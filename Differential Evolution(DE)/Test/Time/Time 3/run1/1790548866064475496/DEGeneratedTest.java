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
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{-866,-806,1000,-31,550,-178,638,84,880,293,-463,1000,33,-268,-1000,-1000,141,-150,232,-100,-484,886,295,44,23,536,-581,498,-683,158,-1000,465,501,-4,636,1000,-98,-459,-1000,-231,462,694,-105,-1000,1000,934,120,-330,-462,579,1000,741,256,-283,470,-389,-773,1000,-421,-1000,-382,258,-316,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{-724,-806,731,-31,498,647,1000,-546,289,-428,136,1000,1000,201,152,-1000,141,-532,-701,-829,376,1000,509,-1000,1000,-921,344,1000,-1000,-497,-1000,1000,255,466,699,911,840,386,-1000,1000,-211,683,417,-1000,1000,934,-469,-236,1000,579,1000,741,-403,288,-676,-389,-773,520,806,-273,1000,934,449,676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{1000,-46,-252,839,-595,894,608,-793,228,128,-272,677,-734,-611,-58,890,-430,255,736,-275,-64,-443,1000,350,202,218,-350,842,225,11,832,156,-795,303,-938,-696,719,46,507,-1000,893,-541,412,-698,-178,-1000,960,1000,908,1000,491,-362,-249,585,-298,-658,-302,-1000,-201,-445,-305,506,-283,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{292,-846,-851,-17,-194,1000,409,-16,-69,-1000,1000,-501,697,1000,-292,933,-316,-610,-498,169,1000,299,-1000,-1000,957,-1000,1000,264,1000,-618,476,-934,-564,-97,732,696,142,274,-121,1000,-851,-1000,1000,-248,1000,714,286,742,1000,266,996,1000,410,632,1000,736,-260,18,1000,654,40,-1000,878,-771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{1000,-1000,-1000,-436,485,-178,-419,765,-47,-347,189,-222,-1000,-156,-363,260,-546,939,-164,-365,1000,577,225,606,262,-1000,-530,1000,1000,897,1000,171,887,-281,793,296,539,215,211,49,697,-384,473,-293,791,442,-889,-1000,-1000,356,391,-111,1000,-773,-1000,-857,1000,18,-90,421,1000,1000,-380,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{-557,-138,297,93,348,180,76,-439,710,-236,-26,-999,-1000,-83,-909,-473,97,-329,540,1000,-989,-454,344,980,-53,1000,772,-1000,-115,-1000,370,-426,48,713,630,938,398,-1000,859,-205,-766,6,-29,-705,-851,-118,-156,270,-996,-660,-31,-124,-1000,364,-468,-264,-77,-1000,-1000,-1000,-316,83,-355,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{-450,353,-536,-785,569,149,-911,487,55,-883,350,-184,-915,-792,-768,-854,-381,888,-161,1000,-300,861,-705,-145,-180,195,-275,276,-139,859,116,-101,1000,-852,1000,484,1000,-180,316,138,1000,702,-496,-911,-42,698,-528,-1000,-406,-442,613,-123,718,-1000,87,-632,355,242,219,198,469,706,-262,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{1000,633,469,-161,-728,-163,-981,-129,-533,-304,-304,-977,-765,577,-1000,905,823,-305,753,905,388,861,-524,610,-299,164,-82,-350,1000,-706,1000,265,686,-520,633,775,127,-686,518,12,-1000,-1000,1000,888,179,-123,-225,822,-1000,-555,-1000,247,-20,431,66,168,-378,-75,-671,-591,-649,-667,235,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{527,-403,239,-287,9,-78,1000,-781,-375,-315,316,-112,356,-781,-136,-844,486,-426,-44,-134,22,-168,-750,1000,1000,135,917,719,-478,-1000,-60,-64,155,1000,721,861,249,-582,-984,415,-409,-147,501,-850,1000,-292,-778,1000,419,358,-11,260,-480,-157,179,-134,-760,-843,59,-215,-219,-890,810,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{331,864,864,799,79,-109,598,-418,-1000,364,-96,840,670,1000,1000,138,-326,-1000,1000,799,-792,886,-718,27,-471,1000,204,-836,-14,-1000,-602,789,-1000,1000,461,1000,-279,-354,-835,-231,-597,-510,766,1000,-835,-25,1000,1000,1000,-113,-232,850,-750,943,189,-42,-23,691,-1000,-1000,453,-495,-738,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{507,-242,474,-476,138,480,194,413,-600,-807,1000,239,-515,-422,-63,187,-1000,-544,-212,722,531,413,-785,-698,982,-459,-193,864,888,-14,-95,-221,-1000,-588,829,620,278,7,-422,1000,-1000,-776,1000,491,1000,731,-548,309,812,-871,-1000,1000,-4,1000,1000,-292,-223,1000,546,-524,-13,-684,-392,698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{303,-144,958,-579,-540,73,-577,-798,-105,-811,136,-318,-794,269,-291,678,-680,-618,230,-311,693,526,-624,-408,-894,-922,-530,-34,-180,-64,-90,485,384,811,532,847,-467,-526,-889,863,-706,111,427,468,683,438,-898,662,160,-278,-224,695,435,436,74,161,-891,924,300,-895,160,-834,841,703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{-555,-1000,400,-438,430,-346,-164,621,146,-600,295,248,-812,-476,-562,723,-326,184,86,-696,1000,376,-32,306,158,-1000,-1000,1000,1000,926,365,735,80,-359,784,681,72,-18,-257,312,-538,-819,473,-1000,805,356,-900,-197,-592,-97,517,779,1000,627,189,-909,407,989,-293,68,219,562,-249,-524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{1000,-1000,-926,115,353,-166,-735,-781,783,-615,1000,-275,-1000,191,-426,945,-1000,661,-369,472,1000,117,72,426,903,-1000,-632,1000,1000,1000,1000,237,203,-834,684,429,363,561,425,415,-204,-1000,631,-412,867,52,-778,-361,-411,511,1000,924,1000,-7,154,-746,1000,302,437,-215,1000,716,-1000,-702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(long):void",
            new int[]{172,191,372,-346,27,-173,-273,-623,-925,-370,-97,-928,-277,83,-753,610,1000,-708,624,644,-17,577,650,606,262,323,101,1000,1000,-844,163,89,-517,734,270,296,-568,-1000,693,49,-955,-603,500,-293,275,-179,140,1000,-624,-108,-429,770,-589,264,496,585,-895,-1000,-618,-551,-1000,-223,266,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{230,295,-670,1000,413,451,251,-27,-888,327,-440,570,-1000,1000,248,-948,7,-132,-769,-727,272,128,985,-445,660,-672,1000,310,-142,65,-1000,1000,1000,-687,-268,-79,-420,-299,-1000,1000,714,666,1000,-1000,478,-49,-482,1000,-750,519,1000,303,425,-896,-234,116,-1000,589,-123,-343,-509,239,-285,131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{352,-773,804,264,-765,-1000,241,579,-1000,1000,-922,-212,-1000,474,159,-186,-1000,-583,244,872,-1000,-535,431,-431,621,785,167,1000,1000,-448,32,1000,1000,-391,-1000,-1000,777,612,407,1000,115,-32,59,1000,357,1000,-1000,-422,197,95,-700,1000,11,550,498,823,138,1000,-272,-157,506,648,-124,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{257,-198,-596,942,-1000,453,1000,420,-480,830,1000,953,718,1000,164,-778,590,-497,859,286,-16,-683,28,1000,-914,584,504,1000,-516,-465,-1000,314,-591,-725,1000,1000,708,73,370,-552,521,-706,57,-967,383,1000,199,176,-798,571,-1000,-573,194,149,-1000,891,654,-1000,486,260,443,423,-401,-242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{-48,485,-660,-428,266,-171,-594,-815,-1000,-781,-142,603,-970,-338,-181,-1000,-730,-1000,-672,1000,-1000,22,-913,-317,703,-1000,676,897,1000,364,971,932,1000,-237,-1000,-686,-728,-318,63,1000,-925,-340,425,952,-1000,1000,-883,-796,1000,-1000,1000,-532,-799,-157,541,-71,-376,415,-1000,-153,-455,412,-719,213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{432,-988,-715,1000,-227,-275,323,594,61,1000,-90,210,-216,400,-144,116,356,155,902,-1000,317,-778,1000,1000,-311,1000,-658,1000,-1000,-957,-1000,302,123,-219,1000,669,613,723,559,302,1000,235,448,-377,1000,80,509,-66,-258,1000,1000,217,619,463,-1000,923,327,235,148,-387,-43,221,32,-593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{1000,66,-966,723,225,-721,-251,-1000,411,138,-751,802,-703,-486,1000,-356,-41,78,-748,706,298,-545,-716,-349,992,-249,-559,1000,257,-1000,-1000,576,-293,-32,-401,-201,221,716,-268,1000,567,416,1000,-1000,630,303,-464,-462,-481,1000,-222,441,491,755,-1000,241,-299,1000,-667,-1000,110,-1000,-684,-780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{73,402,-201,96,-152,-152,1000,-700,-5,304,327,210,562,1000,-179,61,161,-749,1000,427,238,-223,-403,383,-374,255,-552,261,-628,-328,-502,571,-689,-1000,1000,930,342,374,745,-527,264,-621,-420,752,271,-116,665,33,690,826,-1000,-64,-153,423,-579,168,639,-459,110,-529,669,-259,-386,-964}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{1000,1000,192,284,479,-177,539,-1000,249,-468,-901,-729,-20,-984,434,-542,-206,398,-1000,1000,1000,1000,848,-470,435,-12,-208,73,16,-1000,32,202,-1000,550,1000,-44,708,739,-823,-1000,240,174,139,-1000,-830,-608,147,1000,74,271,856,505,765,98,-432,-1000,-914,-165,899,-1000,945,-41,-239,-294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{740,-1000,-164,428,1000,-212,939,-463,513,326,-424,-312,-497,-128,1000,103,542,49,-816,-826,1000,409,1000,-402,237,0,-715,-396,-1000,-460,-698,509,-245,202,1000,282,801,480,-524,-78,630,243,73,-1000,510,-1000,803,945,-634,721,1000,-198,641,-738,-777,-509,-641,500,40,-683,798,-258,645,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{504,598,-542,129,457,718,517,-619,288,-196,-930,64,-764,-441,718,-334,716,618,902,-131,1000,-140,756,-950,479,-805,119,423,14,573,-17,1000,786,69,598,1000,755,723,-765,1000,137,235,660,-894,-505,-110,33,124,81,-259,-576,-1000,-19,-678,-1000,-209,-340,1000,775,-387,765,-754,-341,-428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{434,-170,-21,568,-256,-565,-437,-708,-43,-203,-202,-73,325,860,-599,364,-695,660,301,-431,110,-381,842,62,855,-143,360,52,-531,-480,-862,686,-400,1000,620,437,188,335,-247,69,914,741,358,370,287,-41,442,149,-69,1000,-368,1000,213,301,-1000,109,400,-1000,205,-427,-238,-628,-556,-764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{-627,-214,-399,-327,-403,-389,42,691,-916,-1000,284,177,-151,296,-1000,169,-399,-1000,591,-278,-952,-232,-186,1000,168,86,185,949,298,675,428,922,957,10,346,104,-796,-129,-861,245,-272,-625,-194,1000,-322,191,-98,-791,1000,-490,222,-1000,-835,256,-9,428,212,-443,-111,684,-968,706,-379,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{778,149,-42,844,119,-867,-594,-318,1000,986,-250,-496,-703,-347,36,-1000,-110,447,-1000,-826,-1000,-376,1000,-279,785,765,-1000,322,-1000,-939,-1000,1000,451,-443,1000,488,-1000,1000,308,636,1000,921,829,213,1000,-724,821,476,871,1000,-1000,1000,438,832,-1000,-496,-308,1000,951,-1000,-1000,-1000,156,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{-803,-1000,-202,1000,733,419,151,1000,-713,1000,879,574,-181,671,-1000,328,-273,-1000,1000,-1000,1000,-1000,1000,1000,-82,1000,-1000,1000,-671,319,-1000,990,957,-753,797,269,-433,42,647,-686,1000,122,381,527,1000,425,544,124,746,823,301,234,-277,592,-1000,1000,-66,1000,930,1000,-1000,1000,-659,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{-376,-852,258,383,-565,-1000,-1000,-111,1000,68,419,263,1000,-680,1000,1000,-923,843,870,-1000,-433,-1000,-794,1000,1000,649,-1000,-479,-1000,-604,-984,552,80,1000,1000,1000,285,945,569,-537,717,773,101,346,1000,-37,1000,-685,-325,1000,-884,-665,521,1000,-1000,997,1000,-832,344,-286,-713,-1000,-964,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{92,-401,-697,299,339,-896,-24,-648,-833,291,129,-25,359,-19,-882,895,-441,-644,205,-171,976,97,755,-407,-582,-800,-357,344,142,-543,-614,681,844,914,-951,-404,-302,-742,-769,-220,871,64,538,702,135,-891,-102,-971,309,528,759,475,-859,514,-214,943,925,452,12,-26,-334,-74,564,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-55,752,510,-542,997,288,1000,-1000,1000,-254,5,-166,-554,-1000,-192,-1000,-938,1000,1000,525,1000,822,1000,1000,-1000,-1000,-1000,1000,1000,-1000,910,-1000,-194,-860,356,1000,-300,-909,707,1000,-608,939,1000,-963,-867,892,-807,-1000,-1000,1000,1000,1000,902,1000,-213,1000,-1000,718,-244,1000,1000,868,-971,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-591,-758,799,95,184,-257,441,106,-542,589,-113,-513,580,528,934,-539,679,-508,-441,-328,631,-27,-1000,855,338,1000,210,-541,-1000,-1000,878,289,477,-1000,-82,-926,-13,-1000,1000,-317,1000,928,-1000,-369,386,-1000,365,-70,358,132,1000,43,916,763,74,71,330,614,528,-251,-1000,-380,405,-973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{713,-106,-51,90,333,-842,460,488,580,792,726,-829,-651,-566,-646,-626,-499,-443,-765,-75,15,-477,242,-871,-797,968,874,-840,-285,-200,-807,658,-115,186,-232,-886,468,39,328,-510,-234,-258,-637,-417,-253,-936,6,-372,857,623,168,-222,-64,-429,152,268,-110,697,731,-701,-405,-395,-976,993}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-1000,-63,-881,846,-11,-631,-1000,329,139,135,1000,-1000,267,-618,-269,-997,1000,-78,-611,-721,-28,-1000,303,-1000,-1000,1000,1000,-760,-297,-1000,-504,1000,-142,-227,-1000,-805,295,37,-780,148,318,-108,-1000,666,-440,-1000,972,129,1000,513,-61,612,-207,1000,-151,-552,509,706,1000,-1000,-1000,316,-387,-393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-1000,260,-624,584,40,-761,242,427,-223,-954,1000,-219,344,-1000,754,-737,1000,-1000,-1000,-864,623,-214,-765,821,-1000,1000,1000,-885,533,-1000,-577,1000,-157,49,-1000,-1000,154,-402,334,516,128,863,-1000,1000,-876,-1000,767,-252,928,-320,1000,946,167,1000,-955,-401,1000,1000,149,-1000,-297,-1000,-551,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-390,-462,780,-1000,-686,965,-761,1000,285,-299,1000,-708,-730,904,-1000,541,1000,673,-229,-844,-93,-1000,-191,-1000,-882,-1000,1000,-802,-175,1000,576,-202,132,775,-1000,138,-174,1000,-1000,-580,1000,-1000,-382,-409,1000,525,-1000,982,1000,326,136,-71,-1000,-324,779,1000,-1000,-1000,-452,-1000,-1000,85,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-584,22,-621,259,-14,-107,-141,32,721,-558,602,-583,-703,-700,-1000,-997,1000,-1000,-304,63,131,-1000,205,-1000,-1000,1000,447,-477,-297,-1000,-166,242,-655,-129,-899,358,571,101,-728,200,231,-151,-117,-422,-347,-1000,668,-306,1000,1000,-61,612,-275,1000,-292,-434,-618,508,864,-1000,-1000,-1000,-1000,360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{445,696,244,471,825,114,1000,-505,547,580,-677,-1000,823,-847,-545,-818,-675,737,446,-314,830,280,-6,707,-1000,-873,-150,687,371,-988,291,-356,-320,-693,-717,732,257,-754,-389,681,473,45,256,-233,-524,-656,-945,-47,73,308,-92,689,107,1000,-380,526,-726,788,-367,571,-125,-229,-381,504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-852,334,291,489,981,-64,-763,-576,-529,-664,791,-981,377,-701,852,-538,1000,421,563,-795,1000,468,201,-1000,-378,911,47,596,1000,-651,610,-123,539,-495,-194,-1000,-1000,-681,1000,851,-561,797,-1000,1000,-95,-1000,-408,-356,121,-299,1000,506,1000,550,305,960,1000,508,-532,-1000,-149,-317,377,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-458,639,244,312,376,-74,1,-652,54,80,-304,-686,-703,-700,2,-666,88,602,382,-150,830,-37,-16,300,-728,-59,-176,498,420,-855,-191,-59,-331,-351,-402,-527,-564,-973,-94,675,212,475,-165,89,-857,-402,95,114,-192,93,457,612,179,1000,-674,210,494,767,-450,-293,7,-584,-358,-322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-1000,384,759,-978,-571,1000,-424,885,207,-1000,385,-288,92,-817,-529,783,252,-607,-822,376,702,724,-360,-520,-873,-26,467,-622,-1000,256,1000,-1000,-518,787,-862,1000,-616,130,-1000,365,584,-340,69,251,1000,1000,-929,951,-508,306,719,381,-1000,-570,-494,49,-990,-1000,-218,-640,794,1000,-869,643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-601,79,-1000,1000,-243,168,1000,-1000,616,-849,-378,-583,-708,-699,-1000,-997,43,-355,-503,792,452,-575,1000,-1000,-619,573,-581,-593,-363,-1000,-610,712,-157,-104,-601,227,1000,-1000,-728,799,-930,-151,152,-318,-1000,687,1000,-549,930,993,-239,612,-372,1000,-713,1000,-1000,1000,479,-972,-877,-463,-535,-453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{807,389,423,147,-248,-348,-716,-995,788,-69,5,47,-705,-762,-151,-754,-866,783,233,843,433,-105,-104,-651,-95,-345,-312,817,-935,-516,-533,-544,-416,930,993,576,453,-909,665,746,641,308,502,-963,-524,995,851,-706,-860,858,-41,451,-684,105,-213,115,-687,718,197,-138,-215,379,-971,997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-559,52,568,-246,693,-1000,631,855,436,839,329,-1000,216,481,-1000,-94,306,865,362,-1000,302,-767,518,-400,-1000,-1000,475,-133,902,-440,1000,-602,-617,279,-1000,663,-478,582,-1000,-213,358,-870,-363,-130,543,-985,-1000,642,1000,457,31,324,151,700,787,1000,-1000,-1000,-384,396,-893,-435,-390,-841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-498,219,-227,375,733,1000,-211,1000,262,-933,-428,-519,27,-683,569,432,1000,-988,258,-1000,871,-54,-188,233,-1000,176,856,-1000,-363,-56,1000,-614,-157,-439,-1000,-597,-1000,-187,-70,-791,-310,51,-84,1000,720,687,-1000,454,1000,264,1000,178,135,133,325,-347,-598,-627,-349,-1000,175,-463,-429,-646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration):void",
            new int[]{-55,-782,510,-651,-1000,1000,-371,477,-490,-94,805,-537,-209,449,-1000,1000,-656,-639,-440,140,713,173,-38,-1000,527,417,-1000,-973,-966,741,612,-145,-962,1000,-564,442,-694,421,-1000,819,-155,-1000,-172,136,952,1000,65,1000,56,-401,132,-1000,-1000,-1000,-917,-705,-1000,-1000,-539,-1000,-149,1000,-375,577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{-403,849,569,776,-1000,456,-762,-1000,875,1000,115,84,80,-836,-1000,-1000,981,1000,815,899,407,585,593,-736,-1000,-1000,1000,-647,193,-1000,-1000,1000,495,446,-363,1000,1000,-1000,-355,1000,946,-947,796,673,206,1000,861,-1000,-56,-160,-132,-1000,-525,-467,-285,-1000,359,-603,-88,315,1000,288,-319,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{-761,-796,-600,-401,397,-1000,-116,258,-306,-3,1000,508,-248,1000,-28,641,310,405,-28,-1000,-1000,993,85,1000,1000,1000,-545,-149,1000,913,1000,520,883,131,844,-1000,-1000,-883,-177,-1000,324,-1000,1000,1000,552,-1000,-253,349,-447,-591,-329,-12,586,74,1,-105,-193,-1000,447,-434,98,-400,820,414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{10,367,-1000,-590,-308,225,-793,238,-464,-1000,959,-459,-136,-513,713,183,576,-46,-1000,-1000,-1000,1000,-28,580,195,1000,-1000,-106,742,-745,992,-1000,1000,109,-672,-1000,-37,-319,-297,-1000,-368,-1000,1000,-674,151,-1000,-636,-627,-1000,695,-812,1000,-741,-805,1000,636,-197,906,12,-1000,-1000,-1000,-607,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{-215,-986,458,919,-227,45,777,-39,505,285,370,56,-1000,-1000,-772,-1000,623,-527,-975,496,1000,1000,848,1000,-93,-1000,34,886,-81,-949,-1000,-81,1000,1000,-465,441,1000,-1000,-367,2,-105,-1000,816,-954,-115,774,-1000,56,-515,-442,-965,31,-813,-885,-48,-725,-349,925,1000,-668,166,641,-891,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{327,-1000,-986,-581,1000,109,523,-739,-686,-819,1000,-252,-1000,659,714,627,327,94,-1000,-780,-782,1000,89,1000,420,1000,-1000,401,686,176,1000,-478,406,552,660,-1000,-1000,-478,-122,-1000,-306,-1000,951,-486,-115,-1000,-1000,-771,-515,-27,-772,351,-324,-1000,158,167,-400,370,1000,-1000,-1000,-131,-138,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{-1000,-781,325,1000,322,-925,-410,-493,281,662,617,1000,-161,-1000,-1000,-1000,510,-69,-1000,1000,-782,1000,1000,196,-338,-1000,-296,895,-852,-993,1000,-835,330,1000,-73,1000,1000,-1000,-714,371,265,-540,993,-368,620,1000,-1000,400,-983,-829,-1000,-313,-1000,-920,462,-1000,-400,-437,1000,-367,-453,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{-522,-726,-286,-1000,-1000,-1000,-13,446,-306,432,972,463,174,1000,-120,1000,174,82,-28,-965,-1000,810,2,1000,1000,99,249,-354,137,840,1000,207,883,-289,528,-1000,-1000,-684,322,-1000,110,-1000,82,1000,719,-1000,277,1000,-149,-1000,-354,79,1000,1000,-1000,80,264,-981,557,69,313,-207,1000,-565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{340,-1000,963,-68,1000,-661,1000,-320,-791,-530,667,-626,-220,248,382,578,-327,-1000,-1000,-180,203,894,668,1000,1000,664,-646,1000,803,-559,-969,-1000,-712,739,-1000,-956,-1000,-747,-171,-1000,-902,-1000,-294,-1000,-648,-40,-516,1000,-764,-1000,-1000,1000,280,163,1000,517,-57,1000,1000,-1000,-1000,1000,-52,-839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{1000,-972,-436,-526,424,591,-116,-868,-317,-350,-59,-491,-688,-1000,264,26,488,40,337,-511,-123,-848,-417,-105,-3,228,445,-830,492,-895,-412,240,-913,-229,40,151,-995,-159,38,1000,306,-974,20,-504,-175,-482,591,1000,-15,591,282,-1000,-857,-157,-647,-356,151,94,298,-493,-427,1000,-259,523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{-345,-699,578,-1000,1000,-1000,805,-893,-328,74,1000,667,-274,-1000,-507,761,932,884,-1000,191,1000,-133,-158,95,280,315,490,572,913,1000,1000,847,-249,-208,922,-400,-427,-1000,512,-1000,402,-428,627,1000,-34,-175,-243,-1000,932,-460,-672,-1000,296,-1000,-541,-1000,454,504,1000,-281,-435,878,-699,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{-645,849,273,880,452,-712,849,-1000,1000,355,556,908,1000,-1000,-1000,-1000,1000,-662,-923,728,-487,1000,1000,-151,-1000,-1000,-415,1000,50,-1000,-806,-231,932,1000,392,162,1000,-1000,27,-1000,-353,239,1000,-955,1000,40,-985,-742,-471,-1000,-1000,567,-859,-659,1000,-465,-87,823,1000,-705,1000,939,362,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{-164,-679,465,898,-574,-516,223,989,433,871,625,467,935,372,-921,-990,560,-472,-139,51,-291,991,998,231,885,30,6,540,832,-871,-712,710,833,902,-182,156,168,-699,-245,-983,480,-555,615,-269,579,195,-286,959,114,-856,-261,-348,413,589,525,-413,-176,-300,348,-192,917,171,408,34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{327,-1000,-741,-976,1000,4,904,212,-686,-927,1000,-1000,-21,-1000,-217,993,1000,94,-1000,24,307,1000,-258,-1000,-1000,-1000,106,329,-534,-327,-1000,-323,-596,400,1000,108,702,-410,612,226,129,-241,1000,229,233,-906,-538,-1000,-154,-27,-772,31,-324,-1000,288,-924,-400,1000,723,-956,-1000,356,-788,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{1000,-1000,-724,-409,1000,349,1000,1000,915,-1000,418,-634,-95,285,524,397,387,368,-1000,374,-1000,1000,789,1000,1000,400,-1000,1000,412,-253,-165,-1000,1000,780,120,-1000,-1000,216,151,-1000,-918,-79,-561,-1000,538,-809,-1000,213,-1000,-175,-958,1000,-20,40,1000,925,-494,1000,1000,-1000,-1000,-633,1000,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{1000,-245,-1000,-1000,-32,661,-575,-868,-155,-677,664,-655,-750,-1000,264,181,793,1000,330,-446,219,-365,-1000,-1000,-1000,-403,966,-1000,3,703,154,996,-1000,-229,1000,421,-1000,-440,410,1000,306,-974,1000,-423,-175,-1000,1000,1000,236,781,-70,-1000,-767,-1000,-1000,-1000,430,423,42,-309,17,1000,-986,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadableDuration,int):void",
            new int[]{-170,-62,156,652,159,7,489,607,1000,272,-171,430,540,344,-960,-726,446,249,217,-271,-268,15,336,227,755,79,-606,308,414,-579,-680,-464,1000,136,48,-16,-1000,-224,-615,-278,284,-746,-872,302,502,-390,194,888,24,-505,57,-53,416,673,5,-700,708,-325,-244,158,722,-1000,903,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{1000,187,-1000,-132,-476,998,144,-945,770,791,-6,-594,-137,-1000,-246,-812,-722,134,770,-274,-651,38,-834,-752,410,-889,841,-752,-966,-328,584,-296,-67,834,-179,-8,1000,-611,-576,-979,658,-575,278,-1000,-1000,-79,655,-696,-1000,969,-384,285,-680,312,359,-96,-304,322,-959,1000,398,137,-170,-798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{1000,-423,288,208,-1000,162,-922,-961,711,269,538,554,-1000,982,1000,-1000,244,-464,-315,-676,-1000,1000,-193,83,686,112,-1000,1000,-225,-675,810,1000,-1000,223,-399,-42,1000,-876,-873,-905,-417,-916,958,-1000,226,-488,-776,-882,-763,600,993,1000,674,772,-117,-1000,-1000,1000,-240,-797,-250,-1000,-1000,-688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{315,-793,574,1000,-754,-621,-1000,-1000,-430,-779,505,-526,-1000,1000,1000,1000,1000,-188,-143,-236,-1000,891,-487,155,1000,-20,-878,1000,434,-838,995,1000,847,-683,-1000,-165,29,-1000,-944,-987,47,-1000,1000,74,1000,90,-75,-170,570,-1000,880,1000,765,1000,855,-1000,-1000,-1000,-78,-704,-180,-940,-304,13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{918,704,618,735,58,-510,315,-352,-227,-43,1000,457,-1000,413,-217,1000,166,-660,-203,-221,1000,759,-298,-141,-1000,130,-1000,-52,-236,-1000,890,105,-270,248,324,1000,-834,-49,-1000,-871,400,-759,-95,-713,106,162,542,876,1000,802,607,208,60,563,322,-1000,362,-287,-597,-1000,-1000,-1000,58,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{944,-453,-99,1000,-541,196,297,-361,-91,-359,418,-261,-740,685,641,1000,-91,-371,392,-193,-1000,1000,-688,-83,963,-971,-545,-552,-225,410,-826,127,-400,118,-4,-191,3,-1000,-523,-701,851,104,228,-1000,400,434,908,-593,888,-269,935,526,-382,1000,821,-426,-902,687,-468,202,-264,-919,594,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{121,215,663,-967,-282,559,-17,-301,-96,486,521,43,312,-214,-1000,-762,-546,-450,-658,-325,-1000,16,253,203,-389,774,-541,870,-163,-530,-302,63,-1000,279,765,1000,-95,963,-560,1000,-821,1000,-306,919,-1000,-369,-1000,-139,370,628,593,-909,-656,-978,-1000,-208,861,-981,-137,-823,-672,-449,-42,-353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{1000,187,-1000,760,-476,998,144,-1000,770,724,1000,-489,-137,-1000,-246,1000,-722,134,770,-274,260,38,-834,-752,410,-889,1000,-984,-1000,-328,584,-1000,-67,834,-179,994,-1000,-611,-576,-979,1000,-575,-243,-1000,79,-79,1000,-696,1000,713,-384,285,-1000,312,823,434,-304,322,-959,1000,398,847,-381,-798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{-251,-170,174,204,400,765,145,-352,-400,223,-400,-142,283,-737,1000,-400,-652,-111,-164,-90,-40,849,-1000,94,1000,89,633,-679,-703,-1000,971,336,115,120,433,-96,1000,-15,-1000,85,25,74,538,1000,374,-119,-41,313,248,456,-412,-133,-703,-573,-729,151,-298,-1000,-784,10,-421,-104,-68,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{984,777,609,1000,622,-461,860,-803,-187,-590,-82,-992,594,-374,-1000,1000,-124,-747,675,-457,854,1000,-187,-544,-269,-374,-1000,122,-458,440,36,-236,1000,145,-699,517,-1000,5,241,65,368,421,-854,302,-787,1000,592,-225,559,627,-646,-851,617,-543,745,557,663,-60,481,636,288,651,-1000,-58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{614,-1000,-208,622,-1000,-761,-623,-767,338,598,233,1000,-1000,560,945,164,-280,-215,-1000,-938,326,1000,-453,-86,478,699,-400,908,1000,-414,-1000,-24,-504,89,983,-541,149,-800,-595,928,-1000,-288,-678,-1000,1000,-691,-400,-635,-1000,-1000,1000,1000,-986,-990,-1000,-1000,-376,926,194,-1000,-993,-400,400,976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{329,-1000,1000,-742,-219,189,-388,-1000,-114,48,1000,1000,-256,1000,111,365,-590,193,449,1000,-41,1000,945,1000,-1000,1000,-1000,124,-44,-1000,404,835,162,479,762,1000,-381,576,-1000,1000,-1000,-470,-659,922,1000,-871,-1000,566,-715,-892,-798,1000,-176,-861,-1000,-1000,124,-1000,59,-1000,-1000,-1000,1000,-537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{1000,-589,-792,642,-881,920,231,-579,957,992,-278,485,-695,381,498,1000,-653,548,-276,346,-527,-1000,-640,224,-198,-31,-297,-278,-799,-600,64,-417,-753,577,337,1000,-1000,-742,-1000,-476,245,-584,99,-1000,537,975,-332,-431,125,10,-214,744,-447,450,567,-551,2,-194,-970,-106,1000,-520,710,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{-747,544,915,622,1000,-47,714,-767,-859,-625,-394,-991,487,-250,-1000,-15,-25,-749,492,78,1000,122,122,-520,420,-194,487,-230,-1000,-34,-488,157,1000,-376,-386,102,-610,-138,-101,320,254,-182,-702,853,-566,1000,518,322,275,478,-1000,-1000,-990,114,369,602,1000,-1000,-112,448,31,588,-1000,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{944,-199,289,1000,1000,532,-168,-951,-1000,-136,-1000,-998,-76,-805,-326,-1000,-507,-464,452,771,882,265,-193,-822,70,-594,892,-838,-225,-893,-550,146,226,-571,125,-191,3,-984,-741,-500,843,-916,142,821,410,91,899,717,125,600,-1000,-824,-1000,1000,-169,1000,980,-1000,-468,452,341,914,-1000,22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{258,-1000,-1000,-548,33,-197,99,-271,236,1000,347,101,-969,-822,17,48,-1000,-355,-1000,-1000,-385,714,-644,-1000,374,-355,1000,-492,96,-641,-1000,-1000,91,70,773,-175,149,-652,-1000,191,-225,1000,-1000,-1000,1000,-1000,1000,-597,-958,-654,-174,842,-1000,-940,-356,191,687,304,-1000,-294,-603,1000,-1000,177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod):void",
            new int[]{1000,-794,915,1000,213,1000,272,-767,-576,336,1000,559,487,889,497,764,-402,332,-935,589,240,1000,65,175,-777,868,-647,673,-1000,-1000,-451,834,-789,525,215,102,354,-138,-1000,430,-217,-182,565,169,1000,-342,-695,473,275,-683,-1000,-2,-482,114,-1,11,-278,-1000,-1000,-1000,-139,-739,-1000,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{-1000,-1000,566,-495,115,468,-930,-557,-632,-534,1000,-1000,1000,-186,1000,1000,1000,631,794,-1000,-990,450,-1000,-1000,-388,224,-1000,-720,-1000,1000,807,1000,246,-173,-1000,-1000,-322,-795,1000,-1000,-1000,-547,1000,-25,1000,-596,-685,744,-1000,1000,1000,-742,-388,-1000,930,1000,1000,856,1000,-1000,-1000,-503,1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{1000,141,36,630,24,-655,847,-1000,918,963,-1000,1000,506,-112,-707,-1000,-741,-1000,-64,1000,-276,1000,-27,-946,486,-292,660,460,993,-1000,-1000,-202,1000,546,-162,1000,-127,-1000,-478,1000,1000,423,-940,343,-169,748,-189,-1000,-1000,-1000,-1000,1000,125,753,-1000,-319,-293,580,-503,-1000,-17,-876,-470,660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{587,176,-138,324,-36,-794,1000,691,91,-273,-563,288,-509,284,-574,-101,-790,-593,148,773,-1000,1000,-412,-108,365,-891,105,-664,651,-1000,-728,-890,1000,-604,-761,-232,1000,-1000,-602,-162,400,551,-532,-344,341,713,148,1000,449,-993,-327,7,858,-36,289,-1000,1000,59,-664,1000,1000,270,-920,188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{1000,-78,-591,209,159,-1000,847,-45,9,118,-928,978,-224,357,-947,-441,-1000,-840,271,787,-1000,1000,-157,-286,-148,-1000,186,-1000,645,-996,-1000,-941,1000,-177,-1000,-221,658,-1000,-932,-102,819,1000,-597,-907,741,461,249,26,410,-1000,-1000,17,973,307,-487,-1000,879,274,-167,223,774,221,-390,135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{-606,-808,-1000,528,-51,494,888,1000,-183,-1000,-725,991,-636,443,-680,-554,-1000,-744,783,935,-1000,845,-683,175,231,-1000,431,-1000,95,-1000,5,-890,781,-944,-963,766,1000,-1000,-602,-1000,574,615,-191,249,909,596,350,-128,640,-1000,689,-848,1000,-464,685,-1000,1000,-492,-260,1000,1000,1000,-1000,904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{587,-954,659,634,-90,16,1000,461,181,-294,-954,425,-1000,282,360,-498,-1000,-658,-205,1000,-785,936,-605,-179,890,-419,553,-1000,1000,-1000,-809,-1000,1000,-1000,17,-1000,999,-1000,-887,373,642,370,-836,642,-304,1000,-85,1000,449,-1000,57,760,968,202,566,-1000,508,-34,-1000,781,1000,392,-1000,373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{1000,-20,-716,-115,-132,-433,144,-216,935,588,-715,334,619,-183,-46,-397,-191,415,70,-768,-164,1000,-87,-715,-116,-420,253,-324,368,-799,622,-521,795,155,-593,266,688,-1000,-696,368,823,-138,-530,-500,-99,-19,165,-589,-263,-577,-786,452,-70,236,-784,449,-555,341,190,-527,-367,-958,12,578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{-482,507,-517,-928,398,-924,-77,201,949,337,355,370,237,-570,687,871,-640,30,346,-506,159,-406,27,-943,-792,373,-276,715,-21,-931,-818,415,617,649,-222,-966,388,15,-298,-547,520,990,-728,-887,900,-631,251,427,896,-304,88,-153,407,-162,-836,-648,223,-556,331,-357,689,-60,-717,447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{-834,-653,465,409,195,789,-27,-136,142,-115,603,384,959,808,970,449,33,-212,1000,-439,178,294,729,-508,675,-1000,438,-336,1000,172,436,1000,-578,-205,-1000,448,50,252,-1000,-984,-547,-772,-486,489,1000,392,-228,-1000,485,-233,682,-743,968,-154,996,833,256,824,1000,480,-1000,-500,228,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{-81,-1000,135,-377,423,373,-574,1000,-264,-683,181,-194,714,973,1000,1000,975,-8,-200,558,50,112,-293,-551,231,600,553,-1000,-684,804,-331,1000,-793,-986,-1000,-1000,115,475,658,134,-1000,-415,-218,-463,992,-93,21,110,354,1000,1000,582,-350,-123,1000,968,-127,1000,1000,643,-1000,-402,2,902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{704,406,840,-407,370,611,-387,-557,84,910,1000,-1000,1000,-142,1000,817,1000,631,-704,-1000,1000,450,-473,-933,476,1000,-1000,382,-205,1000,-425,1000,65,641,1,-97,-378,859,1000,1000,-1000,-518,88,-25,-113,-211,-524,-715,-1000,1000,-452,1000,-579,-389,434,1000,-1000,1000,58,-1000,-1000,-1000,1000,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{1000,324,-153,-473,158,98,438,453,843,678,416,-746,1000,234,1000,367,23,-246,-444,-711,665,674,614,-410,251,736,-563,488,299,254,-901,849,-698,635,-89,-387,6,-285,299,936,-671,647,-247,-136,576,-63,60,-109,-780,72,-899,1000,-253,-421,3,222,-401,914,-485,-1000,-890,-930,970,-191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{-448,-1000,-959,606,115,-536,-352,117,-864,328,475,-786,662,417,-1000,-465,747,1000,603,-792,389,935,-243,-97,101,-182,-663,-924,-265,181,1000,-47,-114,1000,-415,-1000,319,-573,1000,-876,-595,436,1000,-1000,-683,-307,-284,917,-6,1000,857,-655,383,-861,1000,533,928,334,146,1000,-267,-385,1000,-350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{378,-803,-154,-302,-288,-775,898,139,162,-221,89,-28,-294,918,859,-70,-907,-601,-469,554,-141,503,-723,752,985,-256,-303,-386,808,-158,-606,-166,336,-95,745,252,757,-977,-21,722,-438,-838,-392,830,489,623,291,806,251,-847,-721,389,-100,-725,743,-609,581,-616,-521,205,448,724,-230,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{868,-622,-1000,861,-203,-488,717,-1000,-1000,72,-204,265,-1000,1000,-1000,-1000,-1000,-759,540,1000,-585,1000,-1000,1000,190,-39,1000,-1000,1000,-1000,-96,-1000,1000,-11,803,820,1000,-1000,-695,535,865,1000,-181,392,-564,696,580,1000,943,-1000,-456,-1000,845,-447,751,-543,884,-1000,-1000,400,1000,1000,-982,-549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "add(org.joda.time.ReadablePeriod,int):void",
            new int[]{-1000,471,-391,-896,94,162,1000,-631,442,290,768,707,-1000,-258,-271,-71,-1000,-1000,-350,-398,-282,-2,-1000,1000,480,-493,-314,-574,1000,-487,-497,-385,1000,722,489,158,1000,-1000,1000,762,1000,-427,-649,-114,-258,451,-655,1000,-250,-820,-1000,-738,-18,-371,-615,-1000,234,-1000,-718,413,34,-555,733,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{848,-556,424,136,-251,169,662,1000,393,-20,-766,-819,-539,686,409,-70,891,-570,1000,-763,1000,-198,1000,243,1000,-194,32,377,-1000,-1000,1000,1000,-373,-950,-429,287,1000,72,-784,1000,-431,841,1000,-795,-460,-933,170,-418,-1000,-1000,-650,880,-222,-362,784,201,-276,-778,1000,1000,925,985,-1000,-794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{-511,-314,-537,-181,229,246,1000,-708,-555,289,233,-1000,-1000,933,213,-204,1000,32,97,-44,961,-1000,-976,-717,-503,1000,29,-1000,1000,555,771,116,-405,-510,-884,759,-601,-1000,-313,573,558,-188,-1000,896,-1000,-642,-969,-284,360,1000,55,798,849,-1000,388,261,508,-127,-477,-1000,409,-658,-1000,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{1000,-397,-434,-1000,60,159,208,262,1000,100,-686,461,955,-420,-169,429,511,1000,511,-581,400,863,-524,-290,711,-462,-115,337,-1000,1000,310,-184,-265,400,-283,-1000,-522,-1000,-973,400,1000,1000,-24,93,-201,397,775,-860,-63,-980,-357,-502,-584,-179,400,41,-651,1000,651,400,-808,778,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{-564,-54,-582,-38,-82,183,1000,-708,-1000,114,745,-179,22,-842,567,-1000,891,32,-670,-267,-349,492,-1000,140,-706,1000,-316,-524,735,644,-359,177,356,-510,-2,759,243,-1000,-313,-554,-287,-815,-526,269,-1000,-1000,-407,-270,1000,765,57,170,144,39,1000,-23,587,-397,-331,-374,338,-57,-388,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{-932,-641,-100,-241,-556,-983,222,-1000,-1000,473,-375,119,-1000,-956,169,-400,278,-400,-800,813,-400,-224,-42,347,-890,-472,813,-337,842,-580,-19,-252,384,129,1000,254,201,257,-846,-400,-400,36,1000,-530,-64,-1000,-519,-520,-128,1000,413,646,795,557,-400,-842,936,-136,-400,-400,397,-86,-1000,95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{-563,-816,-417,814,-549,-986,163,-550,281,-454,47,-278,-415,-1000,1000,-602,-1000,222,-360,-336,-578,654,-1000,1000,-763,-1000,937,1000,278,-1000,-134,1000,239,1000,987,154,992,400,125,-369,-110,882,904,-480,-114,-1000,-113,-209,-679,106,947,1000,-14,1000,624,-153,-691,27,861,-938,353,463,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{537,-516,603,-626,279,-487,1000,-238,538,1000,271,769,-845,747,-1000,-873,56,-633,-1000,945,-82,1000,-440,-446,290,-942,-147,85,-37,-353,-1000,285,691,42,559,103,-682,-1000,-328,-1000,-216,-545,695,-666,-339,-528,-856,-395,472,224,524,320,-806,608,-800,-1000,562,660,-1000,-314,530,-555,134,747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{-320,-734,1000,727,275,-1000,-653,-704,733,1000,-558,417,-1000,-630,-1000,-88,-1000,-1000,-1000,891,-1000,1000,-440,-300,-665,-1000,62,-713,-443,-909,-1000,-1000,842,1000,1000,92,1000,1000,-543,-1000,-588,709,-409,1000,1000,-195,-290,-449,1000,1000,1000,1000,-1000,1000,-1000,-1000,-174,173,-1000,-1000,360,150,-326,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{-719,0,184,909,980,595,1000,-532,-480,721,-297,-129,-1000,-1000,430,-1000,-948,-1000,-632,-127,-1000,-71,-691,351,-1000,-571,169,-28,-160,-646,-407,165,-30,-279,-463,910,1000,-658,-1000,-1000,-639,-148,535,-979,88,-632,-1000,172,1000,477,-462,825,-208,1000,-473,-1000,1000,180,625,148,1000,137,-400,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{-716,302,144,504,-460,-982,-210,-1000,-540,104,598,-179,-109,-1000,-266,-1000,-1000,-1000,-1000,755,-1000,943,-1000,934,-997,-738,199,1000,174,-731,-1000,1000,1000,658,833,793,715,1000,3,-1000,-1000,-1000,463,871,-380,-781,-411,-315,418,1000,1000,1000,-1000,684,-1000,-517,543,-548,-1000,-1000,417,217,-404,-676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{57,127,624,-367,-556,-754,222,-213,1000,95,-375,-679,-1000,78,-283,264,-1000,-817,-422,-1000,-299,863,-1000,-100,836,-545,813,931,842,-486,-392,-38,192,447,274,722,-158,801,-858,-645,-1000,-188,488,1000,163,-760,-336,-860,-844,830,1000,1000,-1000,-401,-923,-145,14,-891,82,-1000,192,39,-1000,516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{24,-1000,-105,-498,871,-281,621,-327,-360,914,-399,57,-669,267,-790,-329,324,214,-224,977,1,-187,-643,-1000,-1000,530,-475,-199,269,1000,350,-713,-180,669,117,-855,-211,-1000,-600,-213,585,1000,-98,590,-100,-3,-915,-620,817,1000,242,-98,439,-144,-1000,-442,261,684,-1000,-1000,-443,-379,-376,213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{1000,366,-336,-962,-591,-364,-489,563,1000,-469,-944,-179,809,-322,-176,1000,-741,1000,704,-253,545,572,-667,667,977,-1000,726,1000,174,-731,211,747,-296,883,-298,-1000,-695,37,3,688,926,270,68,405,-137,-781,-411,-1000,-961,-1000,315,455,-1000,-327,-1000,-517,-938,1000,708,562,-816,1000,-458,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{-684,-78,624,-530,250,-378,610,-225,-323,809,665,-771,-1000,-240,364,-516,-73,-917,-615,263,-700,356,-624,341,-235,-642,813,249,757,-486,-392,-192,-302,71,610,722,781,173,-1000,-274,-607,241,500,-471,163,-1000,-933,-1000,-330,-817,529,1000,-186,376,-1000,-821,200,-1000,-490,-689,815,-49,-1000,20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addDays(int):void",
            new int[]{1000,926,-1000,-1000,-216,22,97,1000,-50,-796,-241,-160,690,32,983,1000,-896,809,367,-900,454,231,-643,115,1000,364,603,-776,-873,193,-163,784,-841,479,-1000,-1000,-777,-389,311,1000,187,-1000,390,1000,-750,-18,757,-730,-1000,-1000,640,157,41,-1000,492,877,225,1000,1000,836,-1000,884,-1000,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{-635,569,-88,-399,-226,521,-845,65,-545,227,-64,235,19,-1000,1000,333,-179,69,378,-544,-435,-945,100,1000,517,-900,-919,402,-1000,1000,-385,625,242,1000,-1000,-341,1000,1000,1000,758,813,616,-1000,351,-35,-1000,708,268,-972,-1000,-1000,650,-70,-36,-27,-1000,643,-1000,1000,-1000,-45,-726,-612,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{255,-466,-667,640,-821,641,561,70,708,-81,895,589,-146,-153,193,107,-782,138,-973,-110,259,-762,-556,181,726,307,477,-810,-226,851,-20,610,-727,646,-596,-461,-556,-859,796,-419,-890,-849,-548,732,-431,308,-562,652,363,-516,492,920,-900,209,-309,960,-152,941,-269,-532,-363,994,-352,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{-352,851,-354,263,-566,-779,1000,-464,1000,131,339,1000,1000,-312,-1000,-490,-314,-84,407,-1000,635,-1000,988,1000,942,789,1000,235,-7,944,-1000,801,-831,233,-189,641,-743,-961,53,-28,-1000,-724,20,1000,165,323,-178,43,743,-166,513,1000,-1000,657,-843,486,-556,1000,-1000,-933,-1000,724,-829,302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{433,1000,504,-546,391,667,686,361,771,78,400,-7,781,428,1000,-811,-35,702,-647,217,-315,-48,-361,-360,224,-419,-1000,339,-544,400,111,517,-783,624,-963,-509,264,746,1000,-711,605,112,-469,145,-521,-694,-1000,72,500,-1000,-1000,609,-156,-174,372,-798,12,-1000,1000,-757,-409,366,98,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{1000,369,-161,-1000,-446,-445,1000,-934,1000,503,-1000,-251,1000,-993,-1000,-1000,705,473,-1000,-1000,24,-1000,1000,-488,1000,565,1000,-206,-119,-299,-1000,1000,-1000,347,527,152,900,-781,-338,1000,-763,752,577,1000,435,478,1000,1000,-172,956,-626,-1000,850,934,-172,-137,1000,1000,-367,-438,-400,729,156,587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{40,-375,233,-826,-452,-337,-866,-216,535,19,12,-653,641,-437,416,-356,-71,-75,-503,-392,-228,-742,-242,393,-886,56,-789,-697,-495,-948,-629,613,-703,718,-391,61,999,424,995,617,-22,952,954,47,-653,692,-189,-269,-760,-366,-378,-790,460,18,553,27,929,415,517,-440,321,883,-224,757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{391,-613,313,-883,949,288,172,447,-850,115,-1000,-1000,480,-55,1000,339,317,1000,1000,-593,-194,866,-615,1000,-1000,-1000,-1000,979,658,-1000,-1000,265,134,-262,-332,1000,1000,208,-111,400,-593,973,577,-565,404,-684,386,268,-681,-419,-1000,-54,435,442,1000,-1000,59,-969,810,-743,1000,-683,-706,602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{772,250,199,-1000,783,322,-883,-60,-315,-506,910,94,76,-868,958,384,1000,-948,-68,-615,1000,179,-758,-1000,-951,668,-329,441,-947,-561,59,878,451,1000,917,543,646,1000,1000,954,-268,-217,1000,-734,-74,-390,-1000,-1000,-1000,994,-568,-612,-184,947,-1,-469,591,86,1000,456,-196,744,-572,579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{-296,15,-447,-64,943,692,1000,-712,890,-42,188,284,1000,120,-1000,974,1000,371,-38,-610,-553,-734,-359,914,1000,-103,1000,1000,-355,714,-180,1000,-848,1000,-1000,378,-717,58,1000,564,589,-832,-612,759,1000,-1000,-826,637,-79,-1000,-1000,1000,625,-707,444,1000,-811,7,1000,-48,-91,-1000,-485,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{470,-437,311,653,-1000,-337,164,797,280,485,-794,-653,-87,1000,307,-462,-881,140,1000,-1000,-223,517,267,1000,-247,329,-599,-897,1000,-1000,-1000,502,-703,-178,-696,1000,-114,424,-148,-81,-1000,941,407,-324,-1000,692,1000,945,877,7,1000,-192,-262,253,-454,1000,397,415,-1000,-222,-307,1000,-549,757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{-142,-670,-312,439,-1000,-229,189,-252,708,-182,128,150,-146,1000,-982,100,-704,521,-33,-621,-128,486,-62,289,422,-16,933,-942,264,-228,-51,716,-1000,88,-297,214,-979,-840,157,218,-1000,182,347,489,-261,737,816,420,-268,-118,1000,321,-630,289,-265,1000,540,-216,-1000,-409,-26,937,-590,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{-503,684,-16,-1000,-22,-147,1000,-416,1000,-873,-478,-595,1000,-698,384,1000,756,1000,1000,-1000,-126,-86,147,167,-565,-1000,418,-141,-225,-806,-325,1000,-1000,520,-647,614,600,523,1000,1000,-1000,-44,577,-146,180,-718,-1000,-990,-1000,-1000,-18,1000,-711,394,504,-969,693,573,-363,-40,651,-124,-204,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{789,-306,543,206,-393,655,878,400,433,306,459,334,977,-213,811,-635,16,190,-84,-329,-312,-830,183,67,-436,-200,-998,-64,-62,318,376,859,797,796,-793,300,542,-599,434,501,672,455,-595,684,-607,-761,-367,-442,355,162,-624,190,605,-234,-620,362,228,-132,561,664,-866,-924,378,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{523,-1000,300,-404,273,363,-845,357,169,-804,171,-758,-26,223,815,1000,507,822,1000,-520,-469,954,100,1000,517,-900,-277,402,881,-211,-2,976,139,676,-1000,827,483,1000,1000,148,-640,95,-139,-1000,340,-1000,-515,268,-725,-345,-480,679,-711,-96,-27,-593,-234,-387,-687,-48,1000,-490,-612,537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{985,764,496,-884,875,-271,1000,580,-152,-23,-1000,-1000,287,915,1000,958,556,251,1000,81,-522,1000,-574,1000,-1000,-35,-279,-287,1000,-1000,-810,-740,1000,-1000,-295,1000,182,153,630,-284,-1000,-275,1000,-822,176,397,590,-841,-573,655,61,-682,1000,313,-1000,579,786,253,-267,-224,1000,744,371,-565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addHours(int):void",
            new int[]{1000,-1000,1000,-483,815,-351,176,1000,-249,930,-112,-1000,-559,1000,-745,377,-693,-992,707,1000,-792,1000,1000,488,-1000,757,818,-689,-193,-1000,-380,1000,537,-1000,204,1000,-881,1000,150,-601,884,-324,1000,-788,1000,598,312,-734,-1000,1000,740,104,-1000,-789,1000,1000,-1000,-431,455,-302,734,1000,1000,-919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{-520,-1000,82,220,-575,28,-11,-290,-636,281,1000,153,-716,-1000,-429,365,38,1000,-506,865,1000,-138,485,-763,415,-354,735,-288,-258,-13,615,-1000,425,642,1000,-1000,121,-232,491,304,409,539,-353,1000,-1000,533,-1000,276,635,-1000,522,-58,-929,320,-109,388,510,-629,1000,-597,843,476,834,-691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{-896,-805,141,-996,914,451,282,985,-803,-855,576,40,-271,65,549,739,196,-1000,1000,397,-190,618,975,-833,484,-587,-1000,446,-600,663,-399,52,-55,560,859,1000,-59,-349,-241,-495,-63,-997,-505,818,-790,-370,210,669,-855,-1000,-20,1000,474,77,-1000,852,1000,740,794,-54,1000,197,-654,208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{-1000,277,-393,-1000,369,-292,-717,-334,622,-718,-402,-123,602,-19,-703,-126,-675,-1000,978,241,-913,680,-324,-1000,-190,1000,-1000,930,541,764,900,-646,905,-414,804,-584,-441,-1000,-449,-1000,-489,-1000,397,1000,791,-998,749,474,-834,-67,-27,-1000,1000,841,-115,426,469,1000,308,-820,-286,-896,-853,209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{871,-456,863,-856,1000,-270,-174,-421,407,-1000,497,-166,561,-662,-1000,-107,1000,970,143,-68,-975,906,1000,1000,1000,417,1000,1000,-1000,-552,-709,741,-35,615,394,975,-336,-164,-225,1000,371,37,-386,-1000,-1000,1000,392,175,-860,-1000,1000,-1000,537,-1000,1000,376,-348,1000,100,454,-842,-274,712,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{-1000,-905,89,-717,126,-486,45,937,-1000,-50,436,1000,-879,-656,-652,538,-199,-400,182,348,170,-541,-275,-1000,707,877,-15,781,-662,1000,1000,-280,451,1000,668,-800,-1000,-1000,-747,-233,1000,-997,-395,276,53,-400,833,501,-335,-597,-622,-99,149,841,-733,928,1000,927,1000,-314,319,104,-343,598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{-692,-198,453,-563,1000,-907,990,1000,392,-1000,-377,-207,-695,351,1000,842,1000,994,1000,311,-394,691,990,-313,1000,331,462,1000,-1000,-1000,1000,1000,-602,1000,-1000,322,157,617,530,1000,-707,-1000,-1000,-229,-1000,1000,846,485,185,-776,1000,-1000,943,-1000,-917,365,1000,1000,1000,644,1000,1000,495,348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{115,-1000,-269,-625,-558,-262,1,-589,-551,91,510,1000,-1000,-1000,-911,-445,-315,-61,-89,-66,-360,-1000,-66,-1000,497,-494,107,56,-156,946,-502,-364,-412,629,1000,-294,-307,264,-871,-90,1000,-368,-116,436,498,-507,1000,323,-930,-1000,566,1000,-740,599,-74,1000,127,1000,659,134,-401,-492,-5,154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{-537,-1000,793,-256,1000,654,126,1000,-1000,-376,830,1000,-324,-279,792,1000,1000,139,1000,392,470,468,1000,-1000,750,489,1000,576,-726,349,388,-89,-276,380,717,887,-767,-1000,-693,180,-352,-925,-739,1000,-1000,1000,519,437,-461,-1000,273,99,-366,125,-1000,861,1000,732,1000,-271,442,1000,1000,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{493,-1000,-10,-178,953,-631,115,700,-108,619,56,890,-1000,6,-499,253,299,463,890,-180,-259,-1000,-424,-806,497,-511,1000,314,-88,-849,-243,-527,-907,908,1000,-1000,-132,-590,-658,331,368,-619,-300,-33,351,93,1000,22,-744,-527,-76,-79,-1000,-23,217,1000,100,1000,-417,456,100,-14,690,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{465,-864,83,-937,13,587,145,-157,-1000,132,717,-166,-1000,-583,-1000,-1000,915,341,1000,113,-1000,700,74,-1000,1000,131,-383,758,318,1000,-853,209,-148,1000,394,-212,-240,1000,-432,249,562,-1000,-455,207,-154,348,1000,443,-1000,-845,-375,-82,40,-29,-41,1000,1000,-365,1000,175,-338,328,366,315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{-956,957,779,-1000,992,801,206,676,-239,439,387,-311,-693,-362,277,117,821,209,1000,306,296,251,605,-1000,872,395,-500,1000,-327,756,-166,-844,44,239,875,331,-542,-1000,-602,747,-274,-1000,-680,1000,-786,1000,675,298,-1000,-1000,212,-32,-7,799,-1000,1000,1000,1000,799,-586,-54,751,1000,206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{-946,-332,351,-516,783,169,607,761,-498,-711,247,-854,-654,442,307,513,141,-483,1000,375,708,767,723,-646,125,-156,-1000,703,-999,-249,388,170,233,467,-180,-191,108,-1000,-693,217,-281,-403,-901,721,-640,-426,-927,126,-376,-702,258,99,579,249,-1000,139,1000,923,309,288,1000,735,371,73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{-273,-538,252,93,-466,-410,775,738,-773,302,447,0,-1000,-294,792,-318,33,1000,-158,377,746,-364,-400,-880,375,1000,1000,689,62,224,1000,-461,131,380,-326,-1000,-538,-840,-376,733,1000,-167,-367,470,6,512,62,158,485,-1000,-251,-880,32,590,-813,192,1000,732,597,-95,394,889,1000,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{-133,185,-936,687,-954,-931,-104,-571,-131,-580,-903,-931,229,-269,-97,739,283,296,-527,-106,249,256,-425,774,664,-587,258,46,-689,387,767,676,-332,-194,-121,829,-630,43,-241,864,-63,-709,-505,341,819,-370,503,364,-364,236,187,542,474,-186,391,424,-971,952,-505,300,-535,-797,-695,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{557,-1000,-95,796,-239,-97,-607,365,1000,-94,-844,161,-271,-697,-108,-49,347,-1000,1000,-473,-296,483,316,474,133,-1000,-456,917,287,198,-343,358,-774,967,161,-330,-59,168,182,-34,-190,-400,-108,-285,-354,205,120,-263,-925,-1000,-971,654,-287,220,-1000,603,1000,994,532,922,-149,772,93,-975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMillis(int):void",
            new int[]{745,932,-426,-123,-178,-507,631,159,764,-698,-366,-1000,-174,1000,-685,-1000,-383,397,215,80,-535,193,264,388,-800,-584,-923,209,1000,-336,-166,114,-708,-573,-1000,-406,1000,1000,473,764,475,543,-41,-168,816,-814,-200,307,102,822,391,234,740,-732,-202,-733,161,-866,-1000,752,801,198,808,878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{637,1000,39,48,652,209,-42,-431,204,1000,-819,939,-109,177,-343,616,483,-616,852,1000,208,605,1000,-239,-231,382,-943,728,-921,-484,-71,-400,757,1000,-158,400,52,-560,-1000,-901,346,-954,644,-308,-313,1000,853,-463,-111,-304,1000,-924,692,558,-163,-141,-263,-774,411,-66,1000,-416,-892,785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{-1000,-396,-331,-429,-605,250,-863,550,-417,1000,1000,-489,768,-594,1000,-139,-263,548,156,564,-266,380,-1000,276,85,-240,1000,-390,503,-1000,483,943,-379,-582,272,-1000,880,1000,48,85,615,-8,-242,736,852,-478,892,-382,256,607,-327,-232,-162,-525,1000,217,767,-401,1000,77,-1000,-504,-748,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{-76,-374,-172,-471,386,-699,-547,839,-286,-503,82,577,1000,-1000,-180,-1000,828,-60,342,446,329,-860,-497,-1000,664,-187,-632,524,794,-1000,-1000,1000,161,-174,-207,-950,160,-537,-296,1000,-527,1000,-1000,1000,402,216,45,181,-66,1000,666,-916,-494,555,73,-483,218,400,503,-265,663,-559,-414,769}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{292,-67,-423,138,-857,712,816,816,-636,752,-23,-1000,25,-545,-937,247,298,-682,-580,912,74,300,-1000,996,1000,-216,803,829,-9,-1000,-729,1000,13,-188,181,78,1000,996,-521,-259,280,261,-1000,1000,611,816,346,-1000,657,495,445,-185,-881,-272,356,-81,431,344,21,-1000,-723,-824,-640,-587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{-585,-169,294,-804,-441,-1000,-705,298,-345,677,535,-109,753,174,753,-157,-309,548,267,95,-670,370,-721,-107,-8,-194,563,-233,269,-376,1000,222,-189,-314,68,-483,444,776,68,-6,-1000,1000,-166,414,667,-81,335,-257,94,739,-155,-787,-328,-360,675,-3,-181,-580,934,401,-616,-305,-560,-201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{-905,-136,-730,-456,-1000,818,977,1000,781,674,720,-1000,-236,1000,-142,-810,-1000,-1000,-66,748,1000,125,-211,954,-51,-766,-559,259,1000,45,91,527,-59,225,593,585,1000,-867,1000,1000,-1000,1000,-482,-143,-291,113,-238,-90,-337,165,487,-2,-1000,703,1000,237,434,29,-1000,-734,1000,-1000,103,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{510,48,-486,-718,-944,-697,-434,-612,-804,-389,-631,1000,428,677,450,421,-1000,1000,-237,-695,-1000,1000,-388,131,-429,-854,-137,444,-1000,219,1000,366,-790,-73,-1000,151,-1000,1000,541,-963,-687,901,1000,-1000,584,-102,-554,-187,-364,638,-1000,1000,-852,-847,776,-1000,-680,-1000,912,1000,-1000,1000,214,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{-96,582,-72,-651,119,-81,-1000,683,-61,157,295,-461,1000,-1000,356,-784,469,534,-548,871,809,431,-123,487,103,556,457,279,1000,-1000,-80,1000,215,-754,1000,-1000,1000,573,-534,1000,1000,-46,-497,1000,1000,-170,280,-1000,1000,919,-107,-550,827,-527,940,473,1000,185,383,-219,903,434,-812,487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{-1000,-722,5,-449,-701,59,-571,1000,-684,1000,1000,-819,768,-594,1000,20,288,548,-84,-695,-1000,380,-1000,675,273,-318,1000,444,381,-972,491,744,-278,-798,149,-708,976,950,118,-92,615,192,-216,736,830,-102,-554,-187,232,498,-327,-90,-250,-525,1000,-267,1000,429,798,-100,-1000,-644,-719,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{1000,-600,74,226,1000,-200,683,-307,-368,642,-1000,327,1000,-681,-968,827,1000,-1000,1000,368,28,-913,1000,48,285,-400,-1000,1000,-812,-573,-1000,189,141,1000,-683,397,571,-1000,-1000,-304,1000,-515,-196,-908,-631,1000,-122,-569,-693,687,1000,-1000,602,1000,-568,-1000,-389,-1000,567,-647,971,-158,-31,520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{-1000,-491,85,-391,-1000,312,-495,337,151,1000,1000,899,12,-682,1000,6,-1000,1000,1000,295,-774,1000,-1000,-16,217,173,122,-1000,1000,-625,1000,1000,-554,-1000,1000,-972,339,1000,747,-169,-14,62,-335,-1000,331,5,992,-68,-1000,1000,658,-86,-1000,-1000,980,-688,919,-167,1000,112,-1000,-520,-1000,-719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{-1000,-1000,294,-55,-1000,424,227,1000,-353,-1000,1000,-849,-1000,-976,1000,-734,165,1000,-239,-1000,-670,1000,-1000,-187,448,-607,-1000,873,1000,-960,1000,222,-1000,-438,1000,-445,650,684,1000,1000,-910,1000,-405,-572,803,-961,-143,1000,-810,739,-1000,-787,-1000,-1000,1000,-982,-181,1000,439,401,-1000,977,-872,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{-319,-907,-454,267,132,565,265,-253,-257,1000,-277,1000,624,583,878,1000,-657,-515,1000,924,-1000,1000,-364,38,-488,-669,-274,-75,-1000,171,925,1000,751,684,-1000,251,-228,620,-1000,-1000,-307,557,148,-1000,-534,1000,1000,832,-1000,-478,1000,-1000,-496,-827,1000,307,-557,-1000,960,372,-450,-1000,126,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{549,516,-757,-440,-335,678,82,-88,-112,410,-509,48,-864,397,-449,335,297,-730,-84,303,32,579,-1000,-572,-128,-386,-755,1000,278,1000,1000,-587,-344,331,277,903,46,-1000,-409,223,-416,-983,-597,100,-532,111,482,191,299,-468,278,-146,410,-33,-562,-453,-90,-171,577,-308,1000,-333,-714,-294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{1000,-927,-727,-578,574,754,-501,1000,-1000,-1000,-1000,-1000,899,-1000,-1000,-876,843,-684,-546,172,825,-1000,94,305,335,-753,-350,1000,-464,75,-1000,1000,-46,-53,-732,-337,818,-1000,-754,773,828,483,-160,1000,56,64,-319,-570,1000,1000,529,-321,775,1000,270,-290,500,325,467,-482,948,-498,556,599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMinutes(int):void",
            new int[]{1000,48,-203,212,553,306,-547,752,-560,909,-1000,951,1000,-1000,-544,-901,-73,-16,-237,1000,1000,-1000,720,-844,25,707,-137,-650,-1000,-1000,-1000,366,677,-844,-1000,-101,1000,1000,-1000,-807,1000,-401,-109,906,654,1000,522,-1000,1000,1000,1000,-1000,895,1000,-186,1000,1000,-1000,1000,-1000,469,-576,616,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{856,-132,-201,-596,1000,-693,-653,-670,745,858,-1000,371,685,-1000,786,-82,-535,521,726,-566,711,163,-118,508,65,254,143,609,-916,46,636,-388,223,-130,470,-158,-85,-172,194,619,56,123,-763,-915,-257,266,85,1000,-381,516,960,228,-994,-561,-37,-1000,116,-128,-264,476,-1000,633,378,36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{139,-1000,-126,-790,158,658,-800,211,-724,-181,-432,871,-1000,281,1000,-827,209,49,315,480,-392,-365,-256,64,34,213,1000,521,207,764,895,-424,692,1000,-97,-409,279,-724,-662,81,-326,1000,1000,799,463,699,268,1000,124,-352,-443,-947,-753,-206,-415,520,369,466,508,655,-47,-954,390,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{1000,-1000,-142,-527,1000,324,-69,-640,-1000,977,-1000,1000,-1000,-416,1000,-995,727,418,849,97,-138,183,-606,819,236,-327,1000,63,155,1000,967,-145,1000,978,167,-240,-1000,54,-567,891,-241,1000,-174,553,-293,-1000,241,342,15,479,-212,1000,-1000,-548,1000,1000,987,717,499,1000,361,-1000,515,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{363,795,369,-290,505,302,-264,-640,-1000,203,-156,330,-493,-928,808,-1000,-151,520,995,183,-912,-230,406,1000,-1000,-743,234,-912,1000,1000,967,623,1000,437,-1000,534,111,-140,416,419,547,156,-174,1000,41,-215,241,71,15,1000,-302,383,-330,-832,1000,1000,-55,-13,-156,1000,575,602,-595,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{-710,832,61,469,879,967,-891,-883,1000,572,-646,-114,1000,-1000,1000,134,202,567,1000,855,1000,376,793,-401,383,378,227,-55,1000,-52,280,-497,-369,-53,-122,-622,1000,-232,724,-39,-366,-138,-112,-750,-375,506,1000,-683,878,860,950,105,-573,-210,689,880,-1000,-430,-394,1000,-957,422,659,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{-351,180,189,-246,-433,637,986,-425,287,193,-911,-527,-15,783,-131,-59,122,226,-333,270,-790,868,-267,-198,219,170,-607,-57,-960,-73,160,165,-680,-657,292,-86,-811,548,423,880,-55,-691,-681,373,-439,645,568,710,-906,887,51,-541,-549,283,440,-878,-161,-304,603,465,-776,-840,847,-982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{266,506,1000,-541,939,-740,-57,184,-743,816,-815,257,-610,-483,1000,-1000,-469,642,-91,-161,-546,-486,-3,-167,-37,-1000,1000,-506,-182,164,649,491,1000,119,878,104,-430,986,226,1000,-677,1000,-448,-745,13,-99,-610,744,-580,555,-1000,1000,-822,-1000,607,1000,-332,377,83,1000,-200,-142,-455,-358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{-281,-652,-813,-608,928,1000,-680,-95,-1000,-176,-1000,1000,-339,514,-194,-665,218,155,26,1000,849,-141,-48,110,-1000,-305,1000,658,162,-480,774,-720,620,1000,-396,-1000,-556,300,-1000,-425,-429,1000,1000,1000,-984,-644,736,610,1000,-967,38,1000,232,-895,812,1000,583,-437,453,217,-300,-1000,885,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{826,-1000,1000,-814,-1000,689,121,1000,573,-235,-1000,103,-488,-217,1000,-1000,-838,-650,144,-722,284,-764,-73,1000,108,-1000,1000,981,168,826,1000,1000,1000,1000,1000,156,-1000,1000,-694,907,-888,687,888,883,1000,-1000,1000,1000,-1000,-1000,-427,1000,-840,912,321,223,1000,1000,-390,-361,-1000,-1000,1000,-468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{-1000,1000,-636,234,831,44,69,-1000,961,95,-931,-108,1000,-140,-1000,451,-1000,-58,67,845,847,557,1000,-1000,193,-139,-141,-55,-308,-12,-98,117,1000,-649,-833,-390,1000,-89,339,-780,-701,242,-1000,-1000,-342,1000,564,-162,797,383,970,-1000,-229,-1000,119,-37,-1000,-1000,-423,917,313,633,-444,984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{1000,-603,494,-1000,366,-486,-810,374,-813,-88,-828,-94,-294,-1000,272,-725,716,319,901,-387,-323,727,-34,1000,400,-708,265,145,914,779,647,99,-720,-160,-572,-502,-369,-425,-60,814,237,1000,-291,332,8,-101,197,932,-127,149,-264,-80,-286,945,644,-446,-34,776,-553,21,6,745,-320,41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{881,40,984,399,-646,768,705,392,0,97,244,-772,-922,-643,-186,-557,-120,310,240,-355,-466,-171,-4,565,-714,865,-41,-897,-529,682,402,921,292,-73,528,815,-234,-591,-4,625,687,-345,306,751,-690,-713,-349,-518,-387,33,-88,300,456,834,834,-937,711,861,104,570,-432,-448,-958,-642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{-493,67,300,-166,472,67,705,-368,1000,641,-809,289,370,-662,1000,-715,-1000,44,-93,944,844,-720,382,-398,949,-987,1000,419,-1000,-420,492,-623,872,455,606,-887,-823,666,-1000,710,-939,923,191,-1000,-1000,-1000,1000,-149,-173,33,864,508,-552,-848,399,1000,1000,280,444,570,-1000,-771,736,-673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addMonths(int):void",
            new int[]{-682,684,-738,-548,-564,763,-718,965,945,-794,615,-908,600,-42,-682,-525,-258,21,342,655,-364,-915,568,-425,-229,697,-976,708,42,446,27,-690,676,148,-223,-993,952,-572,-940,-554,-698,-802,161,-968,-793,-447,794,956,-527,-609,-33,-392,-88,638,23,-715,230,412,-208,-376,-955,-324,-192,626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{707,-609,-616,-6,-1000,694,-1000,997,115,-144,1000,-725,1000,-286,74,-1000,-1000,1000,1000,-1000,-1000,1000,-738,-1000,703,-938,873,-484,-170,253,1000,1000,-782,-296,1000,1000,366,1000,-135,127,1000,-1000,-616,1000,943,-1000,-338,878,1000,1000,-646,543,1000,131,-1000,-1000,1000,-1000,-219,1000,1000,1000,-293,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{366,335,-711,1000,-431,-1000,173,124,590,-842,875,-586,20,-11,-513,-531,-344,317,-613,1000,-1000,575,556,-391,80,403,-165,-1000,-141,-98,882,642,233,-990,332,333,-159,623,-376,-606,375,-439,364,914,-531,590,-61,140,101,76,108,978,421,-1000,55,-65,504,40,-469,-570,184,-1000,-322,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{-616,135,-726,519,258,482,340,-477,467,-498,-92,-345,1000,-582,-529,-833,158,-1000,250,-984,588,650,-377,1000,-1000,-315,111,1000,-21,-690,-1000,227,24,1000,517,942,-1000,-1000,14,-1000,-898,1000,-1000,1000,777,557,-636,-351,-690,-580,-1000,-1000,-239,619,1000,52,-431,711,529,335,358,584,96,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{1000,-1000,1000,1000,-1000,664,-1000,1000,-1000,-233,1000,-424,-311,409,1000,1000,-1000,-104,450,1000,-82,1000,190,727,1000,1000,-1000,-454,98,1000,1000,930,-590,218,1000,-758,-438,-69,1000,1000,-1000,-1000,-1000,1000,1000,1000,-102,529,448,203,-109,43,293,696,-833,363,727,1000,-1000,635,689,-1000,419,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{-536,682,-1000,1000,619,-630,-123,38,-30,-762,344,-505,923,-286,-1000,-155,193,1000,-177,1000,-1000,807,-710,-1000,703,-303,873,-450,-170,456,152,665,-34,-296,846,1000,191,-1,871,565,1000,-515,-94,505,-531,-299,-446,378,900,1000,-397,400,1000,-400,-1000,-1000,653,-73,-219,-489,-97,-808,-913,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{-114,6,-678,964,-282,319,-565,748,-30,-403,1000,-263,924,-176,-143,158,-613,1000,338,-737,-1000,534,174,-867,955,152,-435,-29,-649,437,1000,998,39,528,889,645,306,824,-247,190,1000,-1000,-212,824,254,-421,-320,325,795,1000,-177,49,1000,1000,-769,-1000,1000,-654,-279,643,825,730,-69,-801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{-129,445,-795,129,-450,546,-745,320,-456,420,506,-117,-282,-165,462,7,-185,675,546,-440,481,741,83,591,-585,-110,-20,927,-480,244,563,698,933,333,-77,870,65,-61,266,954,379,147,300,-77,-466,-364,-534,-910,-272,-391,950,188,614,-76,856,232,981,-42,644,867,565,-455,1000,-141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{348,-283,-252,432,-387,-892,-531,379,1000,-995,1000,-956,1000,-624,282,-85,-544,1000,-259,-156,-1000,1000,-63,-1000,1000,-546,-65,-1000,-134,-417,1000,1000,-557,-1000,1000,1000,406,1000,-698,852,1000,-978,-69,412,700,-128,-46,481,1000,1000,-920,528,1000,-640,-1000,-838,889,-1000,-1000,-256,363,-806,-1000,-965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{-616,-695,1000,-261,-106,155,469,-126,495,200,-845,393,420,-318,-1000,511,1000,-721,975,-404,1000,572,131,962,-1000,-146,-1000,1000,-525,428,-1000,241,709,-513,799,328,-1000,60,1000,266,749,302,-688,99,-129,-492,-467,1000,-528,-322,-211,-378,-515,1000,542,590,-1000,-1000,172,-286,-435,164,-850,355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{-114,775,60,672,434,344,-158,481,-286,-280,1000,245,-187,370,-383,4,149,273,338,651,57,-535,2,521,-277,786,184,1000,-649,88,-417,435,39,351,405,494,-577,-589,-247,-164,-359,-304,-819,411,-103,597,-760,325,-309,-98,102,-326,-276,486,428,-1000,450,29,549,164,520,364,433,-801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{1000,515,1000,989,-551,-308,-970,595,-17,-760,972,-678,-1000,-51,-796,-536,389,12,57,1000,-955,849,-121,620,859,390,69,-822,415,490,367,806,-244,81,769,581,-1000,163,569,1000,324,-1000,-1000,1000,1000,1000,-686,987,929,908,-439,1000,279,-1000,-329,-624,680,-806,-589,252,947,570,-227,-49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{-205,178,-887,982,24,-407,-78,497,991,-750,1000,-296,1000,-87,-542,211,-900,1000,50,-216,-1000,720,483,-1000,1000,97,-739,-445,-886,38,1000,1000,-380,-687,833,1000,501,1000,-1000,326,-527,-1000,305,877,478,-677,-34,316,906,1000,26,334,1000,400,-1000,-1000,728,-894,-427,-16,513,-470,-521,473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{329,772,-873,1000,629,638,-745,428,368,473,1000,-707,663,-99,-1000,-1000,620,1000,22,1000,-1000,1000,-942,-1000,1000,-633,951,-487,-917,460,201,988,278,429,1000,1000,-1000,311,791,1000,1000,-1000,-1000,1000,-951,262,-924,1000,1000,-455,-511,957,1000,-198,-1000,-23,1000,-1000,1000,482,639,-125,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{-150,-1000,-1000,505,-733,678,983,879,20,-744,-891,272,622,560,900,-224,-892,289,-473,680,-926,-478,-785,-366,110,-220,-422,-550,-815,392,1000,89,-590,218,494,-798,1000,129,217,1000,-46,-706,300,489,-45,250,59,-1000,-415,-222,-835,43,477,-320,-833,244,-421,1000,-1000,-554,689,5,-214,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{-540,305,-1000,939,-532,617,204,142,236,494,428,-427,1000,-304,430,1000,-835,-23,-55,-1000,448,626,-690,-535,74,-592,-435,838,-843,169,614,527,-80,558,299,555,63,-161,-404,-1000,-941,390,23,844,426,-463,33,-286,-372,21,700,-830,133,799,-175,-179,789,499,-416,633,586,321,755,342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addSeconds(int):void",
            new int[]{-437,-919,775,988,-1000,279,-1000,876,-1000,165,1000,-197,-333,373,1000,751,-1000,-104,179,553,-855,1000,238,792,505,1000,-931,-25,-169,1000,974,774,-144,686,584,-925,-2,-252,1000,627,-1000,-777,-572,914,901,548,-101,-81,85,-296,288,173,293,813,-1000,698,826,1000,-653,635,628,-877,979,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{-497,19,-288,-976,-296,210,199,-45,-42,462,480,199,758,559,-84,435,-452,-37,-538,329,-175,-769,-409,-16,18,47,-113,129,356,-513,-111,-138,753,115,-1000,332,276,-849,366,-129,685,-77,239,107,158,-176,-344,-646,-533,162,-118,-553,497,21,-676,-288,688,59,-351,400,-440,-267,547,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{-1000,1000,-528,-956,479,313,-423,527,1000,703,1000,750,465,307,305,435,1000,-261,664,-1000,440,884,110,1000,-76,-1000,-539,-74,-324,-196,-656,778,678,852,-946,619,-1000,-849,1000,-535,381,-109,-259,-437,-422,-1000,-590,-305,527,1000,-461,158,101,402,-1000,-576,546,-255,565,-1000,-780,469,1000,-825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{-820,958,-108,-542,-332,733,-1000,-629,-524,1000,493,470,-1000,189,-1000,899,506,1000,-1000,-231,-667,-284,-453,202,1000,1000,1000,-452,689,640,-13,-279,1000,1000,-70,531,-43,-1000,-1000,212,-1000,256,620,-54,-919,-1000,-89,-1000,-770,1000,1000,-853,93,-495,-1000,239,1000,390,-632,50,-780,303,-424,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{-282,332,-8,-584,-568,849,-169,-589,-813,-902,-570,-712,-159,-293,-685,542,1000,481,-1000,71,-424,-1000,-723,-950,495,1000,591,-173,373,-684,232,-740,786,292,-119,451,244,-955,-79,12,-412,334,584,-234,164,27,29,-766,-1000,139,300,-592,567,-370,-796,468,1000,529,-300,1000,-372,-514,-367,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{-699,-393,833,-362,1000,-33,-407,-461,987,-679,-238,855,-919,-1000,-1000,1000,-902,-649,110,-910,-285,391,776,65,579,-116,-619,-19,282,-509,-281,-364,-981,-379,72,-108,-380,-641,-554,-608,-758,340,-257,-1000,-321,-515,-703,-681,-202,767,31,-774,-311,515,-807,1000,1000,254,-390,233,364,-435,104,951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{-766,79,183,-194,-141,105,-21,13,-843,864,122,-210,-1000,1000,-624,-463,-645,454,332,-249,-572,-181,1000,285,-240,477,388,-170,581,64,-249,324,481,566,-431,888,-413,512,-860,116,-156,-520,-1000,-771,-1000,-1000,682,-622,-1000,1000,578,-296,-549,-87,-856,-175,777,429,-618,-424,-1000,-126,305,386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{170,515,-279,-536,144,998,-37,-204,1000,1000,1000,75,1000,-830,1000,11,1000,161,-136,346,605,-354,-1000,-73,-627,89,-820,207,-42,423,-122,-295,1000,192,-517,208,135,-547,426,-601,703,-814,167,543,135,-83,-632,-365,-209,291,-501,-18,-165,-144,-459,-782,-613,-863,13,-789,-385,-161,299,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{106,950,102,364,163,1000,-608,223,-362,-193,-296,-536,-157,900,-1000,-482,-340,1000,-65,-1000,970,-919,135,1000,130,-116,1000,-913,-1000,961,-394,-674,1000,1000,431,1000,-382,1000,-1000,-504,783,-399,-239,647,-67,-40,578,-197,458,1000,1000,-529,-975,-37,-1000,-644,1000,1000,-1000,-897,-1000,-656,118,-101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{43,180,992,-800,-5,487,1000,-991,-1000,905,-1000,8,-1000,128,-238,-703,-1000,940,-103,-429,255,-110,-458,150,656,1000,266,-693,-140,1000,427,528,301,469,89,102,80,519,-1000,-112,-1000,418,-61,-425,-767,-807,733,-266,-573,223,1000,-402,-649,738,-247,1000,1000,1000,-1000,698,-116,677,-1000,484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{-1000,359,500,-913,1000,293,143,-45,1000,1000,428,507,-569,-287,70,435,-452,-37,880,-1000,-713,586,-343,518,636,-102,-868,-494,68,-393,-111,-138,79,-9,-107,558,-856,-1000,317,-608,-653,-3,-94,-913,-191,-1000,-802,-599,-397,1000,366,-54,224,545,-889,964,688,232,-1000,-1000,-343,780,280,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{-1000,401,-468,-807,107,998,468,409,676,1000,531,75,283,-830,-130,1000,1000,161,717,-898,-121,146,218,765,443,-594,-17,207,28,-548,-320,313,617,317,208,861,-823,-1000,316,-274,450,203,167,-439,172,-1000,-684,-458,-294,976,509,-18,433,-174,-838,256,797,489,-683,-1000,-857,51,251,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{877,-394,-372,-103,-76,-850,-749,467,-348,590,361,780,86,747,519,845,951,-690,521,334,578,-219,-104,-562,-725,981,-206,331,116,-224,521,-281,555,-682,365,-240,-952,-534,-983,-834,-46,-659,902,704,-597,-44,-966,-481,488,-262,632,698,225,-559,-343,307,-990,-608,690,-193,-27,-671,-894,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{1000,-750,825,-1000,714,673,791,158,1000,-507,-831,661,1000,-1000,1000,-701,-1000,-1000,671,-264,939,1000,-1000,-186,-163,-785,-1000,126,-427,-463,499,-672,-487,-1000,-890,380,953,-68,1000,-365,1000,1000,-11,-234,1000,1000,-394,554,1000,-1000,-1000,-263,1000,1000,1000,732,-1000,1000,354,-789,1000,862,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{1000,-946,1000,-956,-192,-691,-423,110,-151,-14,-282,599,457,-694,709,-221,-646,-759,664,224,-217,884,218,-718,-614,-636,-1000,494,807,-887,494,-162,-512,-567,-946,-1000,967,691,1000,-22,-690,1000,-216,-553,525,561,338,-136,174,-1000,-900,-111,536,161,487,346,-692,-125,565,-111,567,-485,-834,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{-708,-1000,606,-982,27,-691,681,1000,-1000,-583,-997,-1000,817,500,709,138,-531,-460,664,-943,613,-1000,1000,688,-1000,-542,1000,1000,807,-1000,-81,291,-1000,-567,37,1000,1000,-542,136,-805,944,461,-1000,-722,1000,-31,815,-136,-34,-31,-374,4,133,-765,-431,133,-60,948,606,846,-811,-620,-314,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeeks(int):void",
            new int[]{-398,-430,-217,-722,234,383,-831,1000,530,-473,799,-325,728,-267,-253,-62,781,-500,882,-557,361,-355,1000,1000,-1000,-1000,680,958,473,-1000,-779,812,47,483,169,1000,90,-988,862,-628,1000,-814,-993,-480,160,-328,-12,-37,574,1000,-1000,294,-104,-639,-1000,-1000,776,-626,1000,233,-1000,-10,1000,-583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{969,-1000,-330,-1000,-1000,-96,-11,-714,204,-29,-174,-1000,1000,59,1000,1000,-401,-202,-596,786,-186,-314,-822,-1000,-714,752,601,-1000,1000,-1000,-1000,287,1000,-468,-1000,1000,-977,-481,565,1000,-544,-1000,-1000,918,1000,1000,-650,-703,466,-1000,-158,1000,1000,-1000,1000,-896,484,940,1000,-674,-874,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{556,-1000,-461,1000,-885,649,313,884,-122,-417,-623,898,-765,594,-543,-338,-707,544,-1000,732,910,-842,-669,1000,981,1000,-914,-433,326,558,529,-1000,1000,-572,-326,284,-419,-986,-337,275,1000,-1000,-122,323,794,758,-986,-1000,215,-1000,1000,-1000,-1000,-190,226,1000,1000,809,1000,379,-341,1000,-564,757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{318,-351,714,-309,-569,-197,-430,-970,631,502,490,-1000,911,-223,958,1000,-814,574,-646,670,-848,738,-210,-61,-467,701,186,-327,1000,-1000,-981,1000,-628,112,-1000,1000,-1000,-242,868,1000,-327,-398,-1000,856,1000,-229,-422,-751,-54,-1000,-181,635,989,-1000,1000,-724,1000,-91,1000,54,361,-543,1000,-885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{141,-87,459,-529,1000,724,-138,-95,1000,708,20,230,-472,109,-545,-379,129,-875,-953,627,-24,-605,-387,-527,952,-41,1000,-259,815,-239,160,62,-817,-12,-857,241,-202,77,85,-228,-210,1000,-607,764,1000,811,-994,-703,-481,1000,-479,-633,-904,535,220,-1000,-39,-209,395,716,-61,-412,116,-317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{809,-1000,-746,227,-410,959,500,978,-1000,99,104,1000,-631,-988,-1000,-244,-132,981,400,-97,-210,-640,-660,1000,692,862,-636,-20,386,795,838,-1000,832,-333,-354,400,-1000,-633,-426,-759,521,-523,1000,-139,-153,501,-199,-188,113,-318,1000,-1000,-427,-210,-729,1000,156,-400,-142,894,173,942,360,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{1,-650,289,497,-481,-1000,-1000,26,14,368,629,-747,-331,341,674,107,-584,-56,-1000,1000,-310,1000,-213,627,-305,13,-225,605,-492,-758,-140,1000,220,1000,-1000,1000,-1000,-337,647,423,-1000,-752,-698,1000,1000,43,290,-244,-696,318,-264,623,-26,-1000,254,102,-81,-371,1000,-429,501,126,154,-588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{-487,-340,297,-287,-583,700,495,-875,866,-250,30,-903,820,-708,-146,1000,718,-1000,133,-294,-1000,828,497,-493,-593,962,1000,-156,872,-1000,-867,486,-1000,269,1000,143,-197,1000,577,-509,-232,663,-500,-679,-1000,-472,877,-406,-583,1000,1000,223,1000,1000,1000,-80,-585,-747,382,199,925,-1000,1000,303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{375,-294,289,-549,129,-378,-751,338,-299,-220,765,-1000,1000,73,252,1000,499,-1000,414,1000,-1000,770,-1000,-163,300,435,624,-943,556,-1000,-919,749,-778,-308,-105,1000,-1000,-71,-234,1000,400,-347,-1000,542,-318,-162,357,-477,-184,-1000,-99,407,-97,-1000,1000,279,-238,-712,905,1000,857,-543,834,-588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{-632,-725,1000,-471,1000,520,207,675,692,1000,804,398,331,-835,-422,1000,1000,-1000,1000,579,-1000,111,-210,216,150,-538,1000,-327,474,-467,-691,982,-956,112,335,-603,370,1000,618,-1000,-1000,1000,-368,512,-1000,347,130,217,-740,1000,-146,-307,821,1000,405,-1000,-546,-1000,1000,863,1000,-695,700,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{329,-1000,121,1000,-397,299,665,-1000,-674,44,-68,346,-1000,116,-702,98,-1000,1000,-1000,714,294,288,-57,1000,837,1000,-929,142,356,-36,305,-2,1000,400,-744,1000,-1000,-903,835,-396,560,-862,-945,380,1000,1000,-1000,-1000,-1000,272,357,-1000,-1000,77,695,1000,1000,353,527,260,81,1000,200,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{77,-657,969,423,791,324,55,510,529,-135,1000,-241,-665,-853,-1000,815,281,-173,-323,1000,-969,1000,960,1000,459,592,184,285,265,22,-966,712,327,1000,151,1000,-641,776,1000,-809,101,1000,-1000,215,-948,385,-648,-435,236,1000,-402,-791,-43,1000,233,-32,-442,-1000,861,745,1000,663,742,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{1000,54,-901,949,38,989,-882,-525,1000,-296,-652,110,181,93,1000,-546,-230,376,-852,-150,-828,100,-48,1000,-469,897,-923,-700,856,-137,-632,-399,802,-323,747,417,5,-22,-387,-226,199,-377,928,-1000,1000,-158,1000,498,519,-1000,-508,-168,919,-638,214,688,-236,-340,760,1000,998,262,12,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{-393,-805,-354,243,955,1000,485,612,231,739,578,872,55,-990,-763,92,777,-1000,1000,-966,-728,-485,-514,358,542,-513,1000,642,123,222,533,375,-585,-31,820,-1000,-889,-103,38,-1000,-53,1000,457,-294,-1000,287,144,160,-710,1000,190,-953,-86,1000,-118,102,-1000,-1000,-642,970,1000,-289,-80,504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{1000,-1000,566,30,55,969,616,-282,221,-307,-66,871,-766,1000,-543,-1000,-493,-974,-1000,968,730,-1000,-1000,-359,1000,796,-245,-261,816,901,710,-1000,255,-1000,-1000,-179,1000,-635,-1000,834,1000,-317,238,789,647,1000,-1000,-1000,-59,-4,501,-788,-1000,-3,-416,-1000,496,1000,-898,178,-1000,345,-1000,746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{-290,-917,-354,793,321,-389,-1000,-220,-374,739,455,143,-1000,506,272,-494,74,237,-954,878,-48,1000,-8,1000,152,-84,-508,642,-612,222,533,375,735,1000,-1000,-60,-889,-358,514,-808,-739,-752,369,994,817,263,37,88,-625,350,186,-66,-468,-1000,47,102,-81,-82,1000,65,636,637,-429,-15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addWeekyears(int):void",
            new int[]{317,-942,-901,855,-715,167,179,31,-562,254,186,66,-1000,654,-72,815,-803,-603,-1000,1000,485,605,348,1000,244,1000,-696,615,-599,455,673,-38,815,400,-1000,589,-1000,-550,-51,489,-218,-826,-150,1000,1000,484,-274,-571,-637,-87,237,-346,-628,-1000,1000,371,243,265,718,-474,-17,746,714,506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{973,-1000,199,987,135,684,-839,1000,-878,875,-1000,-643,-835,15,1000,788,624,-207,-691,-62,759,1000,-1000,1000,-630,-1000,884,879,239,521,-520,-7,54,-263,-174,-708,82,430,1000,-196,506,-1000,989,-704,-72,-1000,-1000,-482,499,1000,-1000,443,433,-1000,-851,1000,979,-560,-240,376,-475,715,1000,-937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{220,290,803,347,154,-1000,815,790,-712,124,-289,1000,1000,831,-1000,-1,-1000,-849,-1000,309,-50,-406,1000,442,-1000,246,-126,-27,-803,454,1000,-1000,1000,314,340,-968,1000,-8,-633,-402,-843,59,-1000,-164,-54,987,-10,1000,-634,-1000,1000,473,-1000,1000,-922,-1000,-355,-1000,1000,-1000,523,-326,152,878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{1000,-161,867,499,162,159,-590,547,-882,997,-761,126,68,-277,136,-178,-353,-406,726,-1000,687,-209,-136,686,44,417,981,791,225,288,1000,-378,130,-296,685,-774,1000,-49,1000,-536,-804,-507,426,141,1000,-194,-1000,1000,-464,-96,-46,828,-612,-625,-653,-139,962,-113,588,185,360,4,-227,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{717,399,359,-317,-854,457,-756,301,-339,418,-661,-65,-303,181,-878,97,-848,-289,1000,743,299,-70,23,1000,1000,-332,-181,1000,-250,927,732,-104,88,-177,375,249,325,829,146,-287,1000,-867,-691,617,-1000,-150,-50,39,-37,552,-342,-465,745,417,433,-212,71,1000,-721,1000,-649,573,1000,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{-266,356,528,-901,-549,-881,166,409,-605,937,-382,414,-769,-520,-2,304,78,139,-186,776,617,822,-885,-298,-69,-622,265,-124,-58,1000,204,1000,-229,-1000,857,468,583,26,361,-238,1000,-665,-104,-761,-821,1000,-1000,-753,125,-506,-963,1000,596,-379,427,680,-335,544,-1000,684,126,948,-45,61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{824,-711,-546,-558,-279,629,-94,-834,-569,-458,-405,-513,263,746,424,396,146,963,-775,338,297,207,63,796,931,-829,-719,708,-64,-899,-522,188,970,787,-714,655,-316,851,233,463,-106,400,252,-608,-541,-737,-542,-71,310,562,-295,195,662,-867,-850,582,-250,-523,515,-334,643,307,-891,833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{959,265,-946,-998,125,-1000,978,817,-1000,-12,663,1000,-806,194,-787,-492,-848,216,-840,281,151,-295,538,776,-79,-1000,-255,665,226,64,-291,704,784,674,-52,-638,596,290,890,259,-881,-1000,462,-402,1,255,-661,-414,303,-321,1000,24,-843,785,-301,428,-360,-615,95,66,122,174,-27,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{883,-1000,-551,496,1000,629,-844,-834,-905,765,-1000,320,-26,-156,375,1000,91,-299,1000,-410,1000,1000,-311,1000,-570,-939,1000,708,-64,-899,-556,-549,-473,-1000,297,170,-273,86,785,-54,-1000,-565,1000,-994,-468,-38,-1000,210,1000,401,-430,-85,551,-867,-1000,602,809,-523,345,480,39,-456,223,-356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{826,-691,164,1000,574,-538,811,362,-788,939,-1000,17,30,-761,577,766,-352,1000,1000,1000,973,784,-593,666,-411,-237,723,468,930,531,-1000,-834,-1000,-382,707,-398,-1,-173,110,83,-231,-556,879,-612,-273,595,-1000,164,1000,464,-578,693,-313,-1000,-945,-87,1000,1000,194,241,185,-993,775,-827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{235,655,515,866,-62,-330,706,-622,-582,260,-410,798,397,80,-1000,-861,-987,-609,-844,-88,361,-282,783,127,-674,1000,-1000,187,-406,1000,1000,-1000,1000,52,1000,-260,1000,-339,-692,-644,-640,655,-1000,435,-193,1000,-861,447,-1000,-1000,741,-321,-793,421,220,-360,746,-1000,184,-882,854,-738,-668,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{1000,-1000,-588,203,1000,824,-796,409,-1000,1000,-1000,778,564,-23,447,1000,-196,-848,1000,-242,1000,1000,353,1000,-1000,-1000,1000,891,859,801,204,-1000,-479,-969,455,-422,485,26,361,-1000,-1000,-977,1000,-998,-270,-592,-659,1000,1000,684,199,1000,106,-1000,-1000,460,1000,253,1000,67,924,-950,893,-997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{598,-825,273,-72,625,637,-772,92,-818,662,-1000,-280,989,1000,-374,1000,-361,-148,-735,462,425,356,817,1000,-382,-952,256,-218,-509,-329,-553,-447,1000,158,-1000,-1000,-87,1000,-437,790,-866,531,879,-1000,-583,-764,-944,986,33,170,436,6,944,-333,-1000,-139,1000,-1000,753,-913,11,779,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{1000,211,929,-876,753,-1000,-589,-1000,-383,-14,-139,-164,-623,-458,-410,95,-516,256,864,406,525,535,-694,-1000,395,-201,-1000,-867,919,604,-594,1000,271,-260,361,669,185,75,-979,-518,890,318,165,-645,-462,1000,-889,-518,244,-626,-860,-1000,439,-77,592,-94,-349,63,-1000,128,133,253,-607,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{-582,-462,-276,-1000,-103,83,594,-703,-535,-1000,795,367,-39,977,-226,213,-337,1000,-570,1000,194,411,553,-657,975,-1000,-1000,-293,-564,-956,-185,1000,1000,1000,-1000,298,-228,1000,-75,1000,-132,452,-127,60,-36,-990,-221,-403,-591,156,452,-679,142,641,471,736,-946,-99,-470,-409,-140,303,-819,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{346,-635,115,-907,195,650,187,399,-833,1000,-673,804,-1000,-123,671,1000,637,525,801,1000,704,1000,-361,699,384,-1000,1000,301,231,536,-1000,-743,-625,-575,-1000,938,174,232,-409,-915,454,-1000,1000,-1000,-1000,-588,-654,-866,1000,695,-807,541,1000,-437,-192,1000,-266,1000,-1000,1000,-384,896,113,-383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "addYears(int):void",
            new int[]{730,99,903,-319,-650,-89,-203,684,-500,673,-138,-139,-71,443,451,449,69,157,-1000,731,217,250,-293,1000,289,-148,809,265,-686,-201,-947,670,545,523,-713,-339,213,611,536,6,-374,-525,341,60,387,-628,-957,-128,-296,76,-360,-102,-65,-54,-702,171,162,179,-617,557,-300,1000,175,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{848,-594,914,-594,-102,770,581,-317,73,-864,-169,-390,436,957,881,-12,100,-422,-528,-498,330,-941,-234,-398,-68,-665,594,981,-180,813,-280,-326,363,583,12,614,189,416,805,-909,-524,467,-437,-64,463,145,-981,535,885,-937,-633,777,366,93,-205,910,-606,-364,692,950,-532,-999,-835,754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{47,696,-326,173,-159,-668,-968,-663,68,-720,-42,269,-797,-1000,-716,1000,34,-217,264,380,521,1000,356,1000,185,973,98,-1000,-952,-751,559,-63,104,-283,70,-470,519,-376,-618,402,726,167,958,187,-573,230,377,-1000,154,1000,473,-994,-429,-722,-88,-1000,677,87,-99,-607,838,841,1000,377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{568,-496,-59,-196,62,-131,-1000,-286,-535,-857,-503,74,-784,-790,-391,640,513,1000,-406,621,645,1000,115,1000,152,1000,1000,-1000,-1000,-33,441,-1000,-1000,-667,877,-1000,556,-621,-678,723,1000,-1000,1000,-1000,-24,-528,1000,-973,738,1000,-280,-1000,-1000,-1000,634,-130,864,1000,564,-1000,1000,520,704,632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{785,-481,51,-751,-986,1000,248,183,-804,-1000,297,195,-450,-136,504,-1000,1000,181,-341,260,631,299,279,-811,-620,-470,832,546,-998,230,314,-567,-680,625,-152,513,144,1000,-465,-1000,62,1000,542,-902,-68,-385,-109,109,250,-710,-398,-620,-860,462,-41,167,-1000,-1,21,-419,-319,-526,293,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-203,-82,259,931,-929,-911,130,765,-425,-143,-49,1000,1000,585,-156,624,-70,-488,-258,-792,-279,-44,143,-1000,-829,-263,-46,-181,401,89,321,-78,-1000,600,-374,646,-214,645,-861,-676,-580,302,88,326,-21,-726,242,-361,926,1000,705,947,477,1000,-1,-478,670,-116,639,484,1000,-672,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{30,466,-139,-182,-417,-630,-866,-1000,-631,787,-3,-390,-309,-220,378,-360,108,797,54,-200,272,-695,399,-819,-695,587,11,50,-863,673,-1,943,510,-736,454,-239,630,461,-555,-132,736,-1000,160,-101,647,-419,408,-478,-30,739,-126,-168,-1000,141,845,79,38,1000,94,-642,400,-624,420,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{-149,-372,882,-194,-581,-178,-1000,-485,-492,1000,651,-517,76,753,-353,728,110,210,-203,-807,347,-1000,369,-625,-1000,3,339,-1000,-88,1000,-137,-122,-495,-809,51,938,668,359,-300,-1000,1000,-935,1000,-1000,135,-106,-57,1000,432,453,812,762,-569,331,-851,-940,563,37,62,434,-85,-742,35,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,227,244,548,359,-929,-1000,-428,34,-745,-143,311,-714,-196,-400,968,624,152,16,76,264,497,-86,1000,490,1000,624,-1000,-976,-590,611,-534,-1000,-408,161,-857,737,-460,-172,-861,-676,-695,345,-469,-167,245,569,242,37,1000,-221,-1000,-1000,-1000,61,-749,900,465,-74,-918,915,82,492,701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{848,-594,83,712,455,86,221,623,-11,-1000,-173,-236,812,-696,-1000,-12,-91,1000,-487,-380,-670,370,81,1000,297,1000,881,-1000,-984,165,581,-1000,-1000,-1000,1000,-1000,1000,-197,-556,-909,333,-726,1000,-696,24,-1000,1000,-1000,165,1000,-221,-1000,-1000,-1000,547,910,1000,1000,692,-1000,263,1000,106,861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{894,-1000,1000,-143,364,589,468,167,35,-671,-418,27,84,1000,860,206,-29,-311,-670,-353,267,-1000,1000,955,106,-705,628,-156,-290,869,104,-528,679,47,-17,130,-7,508,672,-915,-812,27,-731,-345,758,141,-422,698,803,-899,1000,175,-44,-216,197,937,-91,45,742,1000,-342,-1000,-1000,828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{365,1000,444,-561,-1000,303,432,-1000,-249,206,-70,-1000,-725,102,1000,-1000,-590,650,-98,-93,919,-1000,1000,-1000,-327,668,369,-83,-715,1000,-1000,735,694,260,-557,300,-138,139,-478,-814,857,-637,-1000,-371,761,130,-503,-508,545,704,-115,789,-1000,231,466,393,-1000,223,654,-201,325,-1000,-465,207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{-357,497,-558,-749,-176,-765,10,-54,818,-590,-410,39,47,-288,614,252,662,560,-206,630,-7,-195,-79,714,-539,-569,842,124,206,680,715,971,416,-810,-253,-698,-597,-315,125,-621,-162,-505,-407,36,-755,-234,239,540,840,629,438,471,119,-260,618,-36,-804,673,558,-683,-140,425,-778,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{637,-1000,1000,617,186,749,913,1000,545,-1000,732,541,1000,-14,-283,552,659,-21,-1000,-1000,-864,1000,-1000,762,1000,-341,1000,824,-198,-671,5,-1000,-332,6,-88,-209,703,499,843,642,590,1000,639,-399,-185,32,30,94,634,172,-1000,-1000,-502,-1000,-1000,581,1000,-1000,28,-1000,-145,786,412,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{-606,120,-97,892,885,-1000,-1000,40,1000,-808,110,547,386,147,302,1000,-61,-576,-398,-192,-1000,470,-550,1000,299,-160,125,-825,158,-1000,488,-26,-566,-444,-168,-825,711,-881,356,-321,374,305,718,1000,-654,333,460,-320,-53,1000,775,-1000,754,-497,-5,370,867,-198,-725,-248,893,83,338,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{-850,-1000,-1000,1000,103,749,-608,1000,134,569,91,-305,1000,-1000,-427,-221,1000,732,-1000,-11,-660,1000,-567,1000,-447,-356,508,-779,-1000,-1000,914,-1000,-102,6,753,-1000,476,366,-899,1000,1000,-871,1000,942,-717,-957,1000,-168,27,1000,87,-447,-1000,398,813,240,1000,1000,-1000,-1000,373,1000,668,-183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "centuryOfEra():org.joda.time.MutableDateTime$Property",
            new int[]{444,-792,-381,-94,-668,1000,273,835,-515,328,-370,-1000,660,-776,351,-396,865,289,-1000,67,271,400,-325,409,-319,-400,909,17,-1000,-121,516,-1000,548,-644,391,-780,-91,767,-806,1000,1000,-400,1000,1000,-304,-622,834,254,528,1000,-876,474,-1000,665,359,651,341,800,-256,-127,-528,1000,-56,670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{877,-1000,-801,972,-825,-786,256,-447,1000,-693,-455,269,-1000,-724,527,-1000,1000,883,-361,1000,-1000,-1000,901,645,-222,5,150,370,1000,-916,327,1000,1000,-678,342,391,220,-998,259,1000,1000,165,323,-279,-472,-295,377,1000,1000,127,1000,-1000,96,-718,-1000,-1000,472,-33,-1000,544,1000,-873,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{511,-816,609,-506,-700,-41,-515,-1000,-821,-997,1000,-527,1000,-1000,-519,4,713,318,-603,-604,476,235,-968,587,-223,955,723,559,-412,-1000,-10,1000,526,-1000,540,447,805,-1000,-256,-612,-580,1000,1000,-617,-356,-1000,484,-1000,-774,1000,-258,1000,-1000,-37,339,931,443,643,145,554,-1000,388,1000,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{-342,-594,-71,595,-694,-971,-143,-6,447,709,-884,290,1000,1000,-773,-225,876,-369,-1000,336,626,-726,-32,941,25,-419,687,-663,11,-212,271,655,-841,-161,1000,1000,-723,-742,1000,-143,885,-587,680,-741,808,947,-1000,-491,451,574,-1000,333,-564,-18,1000,-329,-932,931,-459,1000,80,1000,259,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{221,-808,333,1000,-871,-292,788,-129,697,-445,-1000,-18,-405,-67,782,-1000,12,1000,-802,1000,-654,-667,454,617,-2,219,740,704,201,-1000,55,88,735,1000,403,883,90,-92,823,611,947,-632,306,76,-331,-473,-543,365,371,349,546,-1000,-791,155,-34,-490,52,-97,-1000,1000,821,-1000,-784,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{-98,-400,-75,-316,-814,-487,829,-1000,-1000,164,75,-341,984,-518,-104,876,349,-870,-1000,-975,659,-680,1000,864,486,67,1000,422,75,-710,458,531,-214,-912,975,786,294,-952,358,-706,-134,-170,921,-803,-58,-1000,-636,1000,-356,1000,-663,758,518,1000,1000,352,-209,733,661,-116,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{502,607,-330,24,308,-47,-35,1000,886,435,335,842,-492,-101,-455,-1000,522,-972,423,423,-694,-520,749,821,1000,-13,-870,-588,1000,559,17,116,496,897,1000,-715,415,993,-534,401,538,-1000,-1000,-149,417,177,-564,1000,741,-1000,493,-897,972,-1000,-439,-573,-181,-685,-1000,-182,731,-1000,-877,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{-1000,550,-225,219,-873,-346,1000,468,-804,870,868,-975,1000,775,-1000,-737,-675,527,-482,817,1000,777,-1000,-125,1000,-378,742,1000,417,-678,-595,-1000,-1000,-670,1000,-331,-552,-340,140,-1000,485,-1000,-774,3,-1000,587,-1000,-574,-1000,-910,422,1000,-1000,1000,1000,940,-705,-700,570,-58,-735,729,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{433,-60,154,535,-474,-41,125,393,-436,-141,-404,642,309,-666,266,-1000,-34,-269,-497,112,22,-271,222,320,964,363,579,-969,249,-1000,1000,-342,-70,341,594,67,697,-85,345,-162,665,-301,-177,-262,-797,-1000,-1000,530,516,369,13,-495,-600,155,1000,-390,-31,-327,-1000,121,-67,-219,-144,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{1000,-272,154,722,264,-388,-1000,-400,-167,-497,-327,1000,-1000,-1000,1000,-1000,757,-142,-868,553,-535,-990,1000,306,531,16,-311,1000,326,-812,-257,-342,221,1000,433,1000,792,-1000,312,1000,478,1000,547,-789,-94,-343,-817,696,1000,277,1000,-1000,-337,-1000,-554,-1000,210,590,-1000,742,941,-103,-885,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{-318,-536,233,-121,-830,-460,417,-573,14,95,-275,-884,275,-481,48,772,361,381,-637,-47,259,-349,-641,769,-107,201,803,1000,-533,-624,-1000,1000,425,-2,624,740,-344,-906,80,-179,-46,232,517,-135,196,-260,275,-420,-932,596,-5,-169,-1000,521,-55,698,-88,668,699,394,400,778,103,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{58,-248,645,-531,-221,-491,-143,-1000,-1000,262,341,290,984,12,93,-26,565,-870,-1000,-975,842,-167,-819,941,743,-1,604,-978,-381,-422,-196,-554,-586,-705,975,771,464,-1000,105,-214,-273,763,1000,-1000,-161,-396,-1000,-1000,-265,734,-663,133,-1000,400,638,-329,-180,789,145,1000,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{-21,-1000,-229,522,-385,-212,-19,-433,-833,199,175,-52,-677,-332,-769,420,1000,-1000,-1000,-333,396,-870,-718,466,1000,-1000,1000,-324,-74,78,-1000,-792,-636,15,762,510,-102,-927,844,1000,335,-665,1000,-1000,-39,-1000,-857,-260,800,-378,171,243,-1000,1000,73,-1000,220,1000,-473,1000,-125,1000,1000,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{-152,-748,-1000,645,-205,-212,1000,663,-154,199,-561,-617,745,193,-683,-625,755,-1000,-108,-153,-710,-540,276,567,1000,660,1000,224,-312,262,355,281,-147,-1000,313,-626,515,602,-315,257,-826,-665,-230,1000,920,-59,-73,-54,495,-283,220,-293,989,-286,73,1000,903,-113,909,457,52,454,-200,537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{58,-248,25,-531,-221,-344,-353,-962,168,-817,897,290,130,-1000,824,-843,681,-1000,693,-975,-1000,-821,1000,687,456,-2,604,1000,590,-953,107,-554,907,1000,-158,-735,-125,998,-522,1000,-89,1000,-1000,1000,-295,-396,-212,1000,1000,-17,-663,133,697,-1000,-1000,-1000,-49,144,-772,1000,931,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{-386,-1000,644,-77,-1000,-41,368,-1000,-978,-535,760,-1000,1000,-1000,83,1000,253,-701,-1000,-596,1000,235,-1000,244,-238,480,1000,1000,-1000,-1000,881,1000,-180,-1000,493,977,206,-1000,658,-1000,-228,1000,1000,-536,332,-1000,-309,-1000,-774,1000,-1000,1000,-1000,910,1000,931,443,643,1000,902,-1000,1000,1000,747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "clone():java.lang.Object",
            new int[]{1000,648,784,-549,1000,-349,-1000,-571,-736,85,717,1000,508,-1000,824,-204,253,-909,745,-713,-1000,-639,1000,1000,1000,-696,-696,1000,-88,-131,-10,-1000,259,1000,-7,-645,-218,1000,-661,1000,-821,1000,-1000,622,-361,335,-711,906,1000,-382,1000,-1000,123,-1000,-1000,-1000,-181,238,-400,955,205,-913,-469,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{1000,-799,1000,-567,-12,829,-90,-296,-418,876,203,-1000,1000,-553,1000,-787,1000,-1000,396,71,8,-723,1000,558,258,-682,-1000,-44,1000,-671,800,-953,-140,-1000,344,788,-1000,1000,-710,-646,-1000,-1000,958,-442,-1000,38,1000,68,254,192,-1000,-1000,-1000,271,-970,1000,360,-1000,-922,1000,-1000,-1000,1000,-300}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{-1000,-1000,-20,1000,-1000,-76,900,-743,1000,1000,1000,-629,-628,-374,-268,538,-978,251,-1000,1000,-27,1000,-323,-5,642,-402,-671,61,921,-362,-1000,1000,433,-317,1000,-1000,-897,1000,777,-469,-1000,538,1000,1000,-1000,205,-756,348,188,1000,884,-213,712,1000,-399,347,-1000,-864,-627,1000,-429,4,151,615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{-405,634,-1000,-251,-413,336,1000,-1000,-116,604,430,1000,39,547,357,544,-1000,761,358,1000,600,-551,3,-358,222,-252,640,-593,-381,861,-313,-236,565,84,255,155,1000,489,1000,185,1000,283,-1000,-199,775,-1000,-599,178,379,698,1000,1000,-313,149,-140,-690,-886,99,-319,-114,886,736,-1000,615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{24,557,-65,-995,79,-77,-505,-65,-541,-1000,-299,565,657,341,710,648,-139,597,-178,1000,265,-618,242,58,762,-465,498,-484,-1000,398,-90,-979,123,140,-752,-344,74,-131,149,571,1000,198,613,359,-542,-1000,652,239,780,-301,-137,583,355,-532,-1000,-1000,572,95,-56,-30,82,189,-895,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{-885,256,-49,779,-306,196,773,-153,1000,77,-101,1000,-247,-143,90,-16,-591,713,-1000,-235,-233,482,-68,-340,-611,-539,118,778,627,293,206,-803,517,409,344,-72,-426,123,55,204,47,-311,49,609,-713,-276,160,569,149,697,1000,1000,276,149,-829,-26,-622,276,-791,-719,1000,-593,-1000,902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{0,0,-111,599,443,-192,-850,-28,478,765,253,91,1000,514,1000,567,-699,-615,-1000,-376,728,332,547,-1000,-65,-40,171,492,1000,-758,295,819,558,407,482,-679,104,266,-1000,-129,-450,-626,231,1000,-1000,639,-510,567,871,157,277,-79,872,-139,-1000,722,-1000,0,-544,1000,150,39,1000,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{-662,619,866,-676,989,62,441,216,-323,-793,-516,-68,-584,355,-558,676,-377,785,-509,-274,185,-319,14,874,-559,-380,-538,683,-163,783,853,-543,374,-953,519,81,-267,-985,-723,867,923,-335,-819,-639,-293,-185,-775,720,638,867,930,697,566,-606,-375,-452,-294,81,-588,-770,963,224,-717,153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{-1000,1000,-108,134,742,623,42,159,128,-165,447,627,-109,1000,-90,1000,-1000,635,-1000,-245,1000,583,602,-1000,244,394,494,270,693,490,192,-718,1000,221,1000,-1000,355,-1000,-806,683,297,-161,-934,828,-996,-190,-1000,1000,977,64,1000,621,1000,-57,-1000,-295,-1000,757,-256,-239,1000,896,-202,-445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{-1000,749,-183,-713,337,432,1000,-326,578,-431,-737,208,-1000,384,-183,229,-107,949,-928,877,21,-893,336,34,-334,-480,203,743,-72,1000,-65,-642,530,-1000,865,-293,-639,1000,333,104,-823,-180,-1000,-1000,-693,-1000,-710,-174,-118,847,1000,1000,313,743,442,-571,357,1000,602,-1000,1000,604,-1000,-355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{914,171,-172,-920,866,470,229,-834,-14,-290,-73,-469,513,229,118,-294,868,-1000,1000,1000,1000,-603,1000,371,444,-135,-1000,-688,414,-776,114,-1000,-28,124,-274,132,765,1000,303,-110,-1000,-298,-651,-1000,-377,194,-658,-278,647,60,-709,-1000,-817,506,-662,49,-426,-1000,464,243,-640,536,1000,-860}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{-686,-843,369,-76,-829,366,-271,263,-140,-990,52,-620,-718,-699,241,-328,175,38,-640,500,-996,681,-876,-871,759,-875,124,-374,-579,-389,-395,421,-722,-758,-90,-545,-34,397,-684,-171,164,56,947,707,-829,-530,652,-158,283,942,-665,-438,821,578,-941,-700,955,757,-549,824,-952,-147,-585,-789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{-183,-967,-726,113,1000,95,-1000,-55,564,85,-524,-614,1000,-1000,1000,-1000,1000,-585,1000,-235,-485,181,-449,914,-1000,-321,-1000,764,560,-1000,-311,38,-475,1000,-981,1000,627,1000,-763,24,-155,-1000,445,-419,1000,1000,1000,-510,994,674,-1000,265,-275,-1000,-668,1000,-43,-643,-1000,1000,-1000,-457,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{1000,-1000,1000,-562,562,829,-90,420,184,564,-793,-1000,1000,-883,1000,-1000,1000,-1000,1000,-351,-300,-723,464,1000,-601,-965,-1000,632,1000,-1000,800,-1000,-602,-615,-849,1000,-603,1000,-1000,-411,-597,-1000,968,-442,-528,1000,984,-373,791,599,-1000,-1000,-1000,-724,-970,1000,171,-1000,-1000,1000,-1000,-933,1000,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{550,133,118,329,812,241,56,392,571,-990,820,16,388,-333,241,-470,-882,1000,1000,211,216,528,-1000,328,-1000,-1000,-969,144,-349,-273,1000,-175,-123,382,-147,745,535,1000,-377,1000,955,92,330,820,296,-317,-1000,141,1000,1000,797,-21,821,-908,-578,-642,-1000,683,-1000,-335,79,-315,154,916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{-427,-1000,325,359,-203,159,83,-821,379,1000,-482,-887,-391,-995,1000,586,767,-1000,-1000,1000,-4,343,860,-1000,273,-104,-429,0,1000,-1000,5,629,-345,-124,227,-245,-909,20,757,-773,-1000,-411,897,-482,-1000,745,-220,-11,236,342,-1000,-766,180,998,-839,839,-453,-83,339,1000,-939,-597,636,-256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "copy():org.joda.time.MutableDateTime",
            new int[]{360,1000,-585,-312,1000,240,307,-584,165,-277,158,805,963,578,-1000,395,-682,534,757,-12,1000,-856,-113,328,-996,-414,-1000,98,-418,124,1000,-1000,1000,630,-264,895,642,983,680,1000,955,46,-1000,33,736,-658,-1000,556,1000,674,1000,1000,45,-775,-376,-529,-1000,-389,-1000,-1000,1000,33,-386,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{171,281,61,618,-491,-532,483,550,-888,325,150,916,-734,-325,-882,-568,154,318,-15,21,-97,582,-592,8,-190,-765,-450,985,-426,-843,67,-774,-674,-908,260,-312,-203,-194,-437,-40,-973,-988,682,799,-287,530,-628,308,-509,-661,111,-716,-758,-601,-268,-265,-152,-569,-561,-805,-260,-800,-387,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-1000,-36,1000,746,-331,632,635,730,-762,342,-481,64,-915,-256,1000,458,-167,91,-364,71,613,-983,-473,-1000,1000,34,-583,-196,221,44,459,417,-239,185,-865,1000,750,-80,-278,-119,133,983,-1000,-446,215,-810,815,-267,-108,-1000,-29,-383,800,-123,-455,-981,-591,1000,-463,408,-1000,773,-629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{950,-1000,-88,496,-56,-811,229,991,53,-523,-528,-300,-660,-900,-835,358,566,-640,-416,-88,558,1000,28,-243,-1000,660,-443,-148,-596,40,951,-259,204,-270,-160,-959,997,613,122,415,742,-368,140,-812,-586,-194,-320,440,-299,1000,-594,108,88,253,-298,363,-258,-480,507,-34,-186,71,-237,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{-360,-901,528,-984,-1000,-301,-739,-265,-867,-151,555,421,135,376,607,-1000,732,85,307,-1000,1000,-938,1000,903,275,81,1000,1000,404,-274,-374,936,-664,131,-401,593,8,723,1000,141,163,1000,-331,22,874,430,548,-497,209,-43,-1000,1000,-362,-1000,1000,182,-1000,-379,1000,1000,-486,437,-772,517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-984,-813,452,394,-740,-206,390,604,-1000,-62,-481,-561,-956,-846,1000,466,-1000,303,660,241,602,-347,-572,442,1000,-833,-1000,-999,505,979,695,356,352,-788,-1000,866,1000,-494,1000,844,-804,972,-1000,-327,-583,-794,924,-180,739,-1000,200,375,296,421,861,-943,-792,1000,171,68,-684,283,-469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{881,-479,-88,-214,-217,-211,529,1000,-347,-995,76,341,-755,527,-1000,-420,-1000,-1000,226,-88,809,1000,96,578,-1000,930,43,-148,-1000,1000,951,154,410,-575,-411,-1000,1000,871,237,225,511,-47,-153,-812,-265,50,-621,449,-236,-942,-1000,-183,832,-665,-504,855,-1000,-44,631,916,-774,71,-1000,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{611,-583,-749,-291,378,-126,-99,-268,747,-686,-150,-321,-839,-872,-766,-1000,-1000,455,-155,280,921,-82,-449,-521,66,-389,1000,-1000,-324,69,-1000,-955,626,249,-566,-239,138,605,-58,-225,-858,-613,-634,197,-46,706,-463,439,-693,418,547,586,137,675,119,466,784,64,192,-1000,572,518,253,622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{357,-860,-1000,-371,-1000,-957,-461,1000,538,766,925,553,-1000,-1000,-866,-1000,1000,-1000,320,1000,-282,1000,-672,-1000,-1000,-598,-1000,1000,-1000,-428,67,-1000,-22,204,1000,-1000,557,293,631,-550,-190,-1000,1000,629,-1000,-870,-289,1000,-666,115,-296,-617,-547,-1000,-204,896,-1000,352,-418,512,-1000,550,-1000,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{369,-424,643,1000,265,-836,620,676,132,5,-482,-359,-996,-890,-752,-494,-1000,18,-232,-507,420,83,-482,-107,-433,254,-647,-465,-194,-563,893,-1000,349,-205,-213,-478,246,151,-676,62,179,-403,468,327,-682,429,-424,262,-461,632,184,175,-504,533,-74,152,336,-527,-173,-597,332,-214,192,-711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{-661,966,978,-346,574,74,997,-483,793,613,-488,733,370,-100,131,-539,-790,1000,-95,-1000,409,-650,-861,609,664,-196,584,115,960,-840,-739,-780,-358,-377,698,788,-809,-355,-235,-449,-603,-41,20,322,-97,1000,-634,-137,-569,-628,1000,-226,-1000,-171,45,-590,1000,-645,-935,-1000,1000,-1000,1000,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{-743,1000,678,429,349,-174,518,-764,800,-246,-361,632,551,221,-234,1000,-274,672,-1000,-714,609,-411,-122,-549,347,-180,709,-103,641,-772,-562,-733,-849,-489,-641,840,875,-3,-1000,-119,-804,-467,-195,96,584,834,-418,645,-185,-569,617,-278,-604,731,-27,-365,1000,-447,-531,1000,999,-912,-590,-741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{721,-401,-1000,88,-823,-141,-136,350,-400,-472,206,-46,-801,49,-749,340,597,-443,-10,361,340,822,276,981,-1000,-310,-15,400,895,368,111,407,-255,-892,186,-972,1000,466,-990,189,80,-413,-172,-338,754,-68,-428,545,-42,105,-1000,-400,39,666,-400,-90,-1000,-630,455,239,-985,-436,-645,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{-119,810,827,-297,-606,587,310,-698,-1000,250,-772,703,543,1000,18,-1000,60,-621,-270,-983,100,598,565,-187,229,-308,857,211,841,6,-806,-199,-558,-839,-721,815,-255,204,-211,465,-858,-26,-1000,878,759,700,69,-711,-102,-478,623,-461,88,-1000,-197,171,-1000,164,-308,-505,263,-141,258,-240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{1000,392,-822,356,32,650,-236,31,953,-178,-670,-606,42,-58,-186,-90,-146,-181,-68,541,717,38,-277,-389,-98,414,-757,-1000,-873,207,-435,-600,770,336,-51,-352,-155,774,136,-537,17,-804,-807,-343,413,-541,-144,269,-610,695,259,590,332,299,85,524,0,-218,1000,-329,617,313,122,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{509,-77,537,654,489,-541,207,69,1000,-347,-1000,45,215,-132,-555,10,122,83,5,-596,205,781,-119,267,-304,305,20,-361,211,-278,-378,376,-339,-248,-856,79,337,160,-557,-825,-514,-678,-603,-273,332,817,-652,117,-96,385,1000,806,-341,1000,-719,160,1000,-1000,78,-990,567,-887,749,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfMonth():org.joda.time.MutableDateTime$Property",
            new int[]{380,-803,735,-426,-157,154,733,253,593,-612,-965,172,-154,216,-722,-980,-83,119,-495,-443,558,-738,-933,-407,-667,577,393,-290,535,-10,951,750,-871,-663,-336,-663,730,-562,696,-47,-257,902,-440,399,-586,-683,257,-97,-204,-339,-619,-441,398,12,-72,-686,-365,913,-56,297,-891,151,-915,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{83,479,276,-1000,-331,78,-1000,-468,446,946,-877,-732,-977,52,816,729,172,-1000,-862,554,442,774,1000,37,1000,1000,-206,-1000,-872,-1000,-916,1000,-1000,54,-1000,-25,1000,-1000,-1000,184,-856,1000,-893,-346,1000,1000,-1000,-119,1000,1000,-549,-579,954,218,-330,-902,926,-1000,390,1000,1000,-837,1000,646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{1000,75,729,990,471,257,968,421,1000,18,-337,295,63,313,-45,-611,-524,119,-801,-1000,109,-287,-253,436,-805,86,342,887,624,998,1000,-911,43,-211,762,192,-544,1000,177,-151,-1000,-1000,1000,257,-378,-1000,88,37,277,149,544,-400,-846,688,527,631,1000,278,-844,-1000,765,-1000,652,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{358,342,-96,-296,869,584,-476,-797,252,520,-358,-372,-418,919,702,210,91,-707,-1000,88,167,1000,371,326,1000,838,-445,-400,-311,-400,-341,400,-177,-387,-400,242,400,-730,130,429,-500,208,-325,-535,961,400,-691,-1000,1000,439,-685,-105,244,204,-78,-247,-81,-574,476,400,421,-241,449,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-1000,-788,1000,400,119,746,-431,640,-759,381,841,-149,183,434,-1000,671,270,-256,-1000,557,708,-1000,1000,155,-388,-1000,1000,1000,1000,1000,-1000,999,-766,1000,959,-1000,-98,130,1000,-1000,-400,1000,-975,-1000,-1000,-119,1000,344,-870,-1000,1000,-1000,-327,-497,478,1000,203,202,-1000,-644,-356,-738,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{-653,439,256,-1000,824,555,-1000,-673,366,1000,-652,-1000,-759,70,676,-1000,-612,-1000,-248,1000,-293,855,1000,-264,609,1000,524,-673,-1000,-314,-1000,1000,-1000,-346,-969,151,1000,-1000,-216,-270,-1000,1000,-1000,372,1000,-1000,-1000,-509,1000,1000,-395,-1000,1000,235,-1000,-425,1000,-887,361,1000,929,-987,982,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-1000,-537,121,-182,488,403,-746,-1000,-272,1000,544,307,-999,263,0,-1000,502,-801,532,525,1000,-653,1000,7,-537,-201,967,224,-382,-324,-433,-649,-750,1000,34,-732,802,-638,-11,-182,244,985,-725,299,-827,-833,338,-289,-1000,577,391,-1000,-63,-819,-623,832,496,281,-253,-346,-15,-1000,-176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{1000,1000,-354,478,-943,-190,31,-305,-100,-391,-600,-733,-683,180,226,362,1000,231,-667,-1000,173,-1000,246,-1000,-1000,-514,62,16,146,1000,204,-371,1000,1000,-139,979,-678,106,701,-237,-45,-320,-175,-780,-602,1000,-378,-74,170,901,147,1000,911,-409,1000,782,745,-1000,237,275,1000,1000,-274,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{974,-427,-672,-441,-585,-442,-855,-955,534,264,-287,21,-725,794,608,230,24,-692,513,-479,448,609,-222,-471,475,918,391,-369,-606,-335,-531,608,-468,-385,-426,841,297,506,-928,624,-469,-473,624,239,26,402,-569,789,-150,-343,-684,-509,-316,-283,-63,-891,765,-130,464,512,-787,-137,-73,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{-69,805,1000,237,-771,-1000,-383,671,-227,1000,408,635,-924,-839,-1000,-922,-960,-1000,-179,-558,252,227,1000,-1000,971,455,-183,-528,-356,-1000,790,680,-1000,52,-443,-1000,460,-810,-762,-958,464,1000,-623,238,-529,66,-58,-67,1000,-1000,652,120,-401,196,801,-49,413,-265,-1000,-359,452,-891,-905,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{184,-1000,-544,-123,-111,-762,559,-1000,-73,-172,1000,1000,395,-321,-1000,914,-1000,-332,-852,201,-141,906,-104,210,1000,-212,346,813,-537,-46,570,701,-1000,-439,1000,-882,-26,323,-111,-474,-425,400,623,1000,-233,-592,-517,-624,-448,-1000,-611,-406,-737,987,-1000,-513,765,-867,-1000,-551,-1000,-1000,-933,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-1000,-712,851,-1000,-587,746,56,1000,-880,180,1000,-592,-1000,434,-1000,1000,397,312,-1000,1000,-11,-959,1000,-829,-751,-1000,1000,1000,1000,1000,-1000,680,-487,1000,1000,-1000,-98,-1000,1000,-1000,132,1000,-975,-1000,-1000,-184,1000,-204,-870,-1000,1000,-823,-540,-127,137,1000,109,-2,-1000,-522,-1000,-696,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-1000,-846,-25,-1000,-587,-598,-1000,153,-364,87,-179,-879,-1000,605,-786,987,58,454,-808,580,1000,-672,1000,980,-442,-1000,198,159,1000,532,33,680,-273,1000,115,-959,-794,-1000,1000,-682,1000,1000,-975,-1000,-816,-1000,1000,674,-238,-1000,1000,-639,-327,-245,-244,1000,-714,202,-126,-713,-356,-670,386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{727,-1000,-1000,823,1000,503,641,-574,-795,-597,829,730,829,1000,-45,429,-165,1000,1000,584,-221,686,-1000,161,390,-859,462,1000,182,1000,136,-298,1000,-1000,1000,-105,-786,292,993,-461,116,-1000,487,66,-133,-1000,609,-136,-882,-1000,296,-752,-1000,200,-472,309,512,1000,-221,-1000,-860,122,-1000,-131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-1000,-603,-96,-1000,254,-362,-549,-285,-942,-431,364,-1000,1000,1000,-1000,1000,-67,-1000,-758,1000,686,-746,1000,676,-1000,-1000,1000,-154,-1000,-342,359,703,242,116,208,-1000,-1000,-560,1000,71,1000,1000,-1000,-763,337,-1000,960,17,-803,-1000,1000,-116,-696,641,-1000,1000,-790,1000,1000,1000,-69,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{293,-460,-822,-1000,649,593,-1000,-1000,-917,189,-167,589,115,400,930,1000,-21,-569,-1000,1000,-353,588,187,308,-459,677,400,996,-634,-1000,-1000,1000,-1000,-289,-299,-18,824,-976,-1000,-400,-203,208,62,-7,274,920,400,-461,-834,938,-488,-406,140,-193,-688,-682,71,-377,1000,1000,665,-508,511,855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfWeek():org.joda.time.MutableDateTime$Property",
            new int[]{1000,0,-647,-788,-1000,107,-1000,-58,-67,-346,-586,270,-1000,-1000,965,-685,-642,-678,123,-927,1000,-554,-102,1000,-805,-121,-1000,639,716,-826,-572,-872,-393,578,-630,1000,-1000,-345,1000,1000,-816,1000,1000,855,-922,522,-811,117,-247,854,-958,1000,-63,972,542,-947,1000,-1000,839,903,1000,-854,-395,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{423,223,-517,-1000,-266,-902,149,-974,-86,-311,-6,-176,784,-96,-176,-414,617,-262,100,-790,-522,1000,-294,913,1000,-318,731,-790,-585,-586,-181,771,1000,-977,120,1000,-638,-329,309,379,-1000,532,216,-395,-210,-725,140,-355,551,573,324,188,-448,176,-337,-181,462,253,537,658,201,587,319,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{826,-433,-5,-888,-1000,194,-353,-1000,-252,-1000,886,-767,732,-539,-757,-1000,-307,77,41,-575,-368,86,1000,165,689,-4,251,717,1000,-17,-856,536,397,1000,346,1000,-58,-34,1000,1000,-1000,-62,-590,301,1000,-699,1000,-1000,400,473,174,-3,-523,722,-120,771,-1000,603,718,652,658,888,-1000,130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{603,585,-483,254,-569,-1000,-283,114,-106,35,838,43,487,90,480,-764,-26,-658,626,-419,-311,-821,-1000,1000,-877,-1000,219,-1000,-1000,1000,-223,409,222,-1000,760,-126,90,-672,-550,961,1000,-499,-665,43,-1000,287,964,-799,1000,330,-691,-259,241,-780,-272,1000,860,338,187,-117,-41,-101,17,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{271,862,-480,213,138,241,43,182,-441,-589,-377,54,874,-402,534,-549,-86,-534,925,-955,-632,1000,-1000,672,48,-621,1000,-1000,-1000,-169,369,701,1000,-887,807,736,-1000,-824,-322,124,540,-410,-344,741,-1000,-827,784,310,-134,775,420,705,-320,-1000,-114,-303,1000,576,362,599,-159,1000,936,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-580,-139,-390,-287,765,-974,342,432,761,919,-329,21,135,211,318,215,-500,-182,-375,18,815,880,-1000,310,291,-590,520,-398,-1000,-218,323,-23,204,-933,-985,-420,-775,-563,-962,-891,969,131,-165,521,-616,1000,-900,1000,-386,-511,-428,157,622,32,-76,-865,1000,288,437,566,-19,-301,981,-415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-816,-433,-761,-1000,-211,574,-148,678,27,101,-1000,-294,282,280,752,-573,-307,241,350,-109,1000,-85,416,217,-343,229,-618,1000,644,32,-1000,-743,256,-515,408,246,-728,600,1000,-241,-945,368,367,1000,239,-251,772,-1000,-58,875,742,-3,-1000,55,-1000,237,325,747,-44,1000,457,888,48,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{658,436,322,-96,-717,-763,408,-372,-184,-1000,-1000,-52,429,-771,374,-112,-632,622,726,-518,-1000,275,1000,673,358,-602,1000,-384,-912,-475,185,1000,601,-1000,947,419,-1000,-774,136,-396,-187,-616,-252,-906,-487,-948,809,-605,978,22,-167,127,241,-279,703,-142,-381,943,580,-165,-40,833,-729,274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-111,381,659,140,-459,442,205,-661,-639,-945,384,346,682,147,307,405,-248,539,363,182,589,-490,947,-177,-11,876,-246,162,730,-284,-256,438,572,441,570,801,870,508,387,714,-722,754,771,-651,349,-938,743,-925,-338,879,803,232,-493,-56,312,504,-834,-459,-468,-557,310,543,-744,-83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-655,-1000,-490,-840,5,834,774,594,251,-262,-946,-1000,-1000,207,693,771,982,735,51,371,447,-827,1000,-583,-927,472,-902,1000,1000,-475,-1000,-1000,-1000,694,-317,-527,24,1000,1000,-396,1000,1000,-835,1000,1000,1000,893,-387,-1000,22,422,75,-308,370,-576,1000,-319,943,714,738,91,833,422,871}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{677,812,-90,929,-856,-877,-735,610,-89,35,-122,716,-15,-227,-50,-956,-158,-1000,1000,-218,-219,-1000,-658,1000,-1000,-1000,219,-1000,-1000,-6,-455,512,62,-1000,508,-665,133,536,-426,961,1000,-46,-995,-609,856,47,1000,-1000,-171,502,-909,-415,513,-1000,209,482,1000,236,-226,-1000,-845,-1000,316,329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-74,1000,-240,-821,-249,-256,181,1000,-572,-659,-1000,1000,-246,-533,-1000,-11,574,-44,1000,-449,-87,-376,-1000,431,-1000,-279,228,-1000,-665,573,216,361,234,-953,1000,-376,-1000,-1000,-908,566,440,-452,-403,-707,-1000,-1000,1000,-670,-407,572,-255,54,298,-1000,675,547,1000,-767,-691,-799,-977,-111,339,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{51,-1000,-672,-1000,-453,-571,767,-1000,250,-593,488,-1000,776,-200,600,-630,-288,361,-572,-568,-605,1000,711,-34,1000,-318,731,610,-41,-401,-1000,394,237,232,-845,960,-1000,-981,1000,223,-1000,215,-546,1000,-882,675,-77,6,551,502,448,534,-748,1000,-1000,482,729,1000,537,1000,1000,1000,-366,-596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{310,538,-256,1000,-326,-386,-384,1000,760,200,-1000,415,-1000,-199,-151,1000,140,-43,656,306,-429,-1000,-428,622,-1000,-926,192,-1000,-938,327,198,158,-620,-1000,657,-1000,267,-1000,-1000,237,1000,710,-976,-543,-1000,1000,1000,-719,-580,-334,-1000,-1000,1000,-761,1000,-293,441,-405,-911,-1000,-1000,-1000,951,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{110,42,-693,162,-302,-736,106,1000,-474,719,557,451,867,481,1000,296,-806,-106,880,33,279,-1000,-837,404,-928,-777,-630,319,-962,220,-1000,513,-1000,-887,-639,-831,342,-598,-221,379,1000,707,-755,1000,-770,1000,737,-1000,-80,460,142,-795,45,528,-616,-94,1000,1000,715,927,-273,-139,796,403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00334() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{66,-711,-717,-827,-564,-939,-649,35,108,200,47,-1000,-449,-477,-1000,-190,434,-195,43,-217,177,-240,1000,-398,-229,355,-856,-35,1000,-1000,-941,1000,-201,1000,-213,-113,967,-630,591,237,-400,995,-1000,-1000,62,-56,1000,-719,-1000,-266,-67,1000,184,530,-37,430,1000,639,-478,58,-407,322,353,616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00335() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "dayOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-385,925,-209,1000,724,-1000,-482,402,214,36,80,970,245,804,212,1000,-1000,407,286,889,-184,-417,-1000,375,-593,-943,581,-1000,-1000,569,1000,649,653,-1000,-416,-784,-636,-987,-1000,-219,1000,776,618,-1000,-1000,1000,-101,619,-45,912,-333,757,563,412,279,-843,1000,-755,-468,-907,-532,-1000,949,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00336() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{827,-1000,-176,-48,949,994,796,-378,966,-1000,-797,423,-47,-557,-468,-469,90,1000,-578,320,-163,-724,501,-398,-786,-396,-965,1000,400,-298,1000,-1000,581,-505,1000,-66,-758,181,217,332,627,367,-659,-222,-207,259,299,-846,296,921,225,-1000,126,-243,-361,-842,-572,-843,-266,487,702,1000,-1000,-116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00337() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{337,-99,-236,-473,61,752,431,55,-568,-1000,670,429,159,1000,-549,733,794,-39,-144,1000,-1000,229,173,213,-384,590,-309,-456,-1000,-1000,-123,393,366,-668,1000,-160,-70,147,16,0,208,517,104,-365,-315,584,259,-623,1000,181,995,-419,60,-1000,-468,276,-1000,-620,-564,-56,-1000,639,-1000,-463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00338() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{624,124,928,-1000,279,1000,-710,-419,257,-1000,179,-304,778,18,-59,-425,677,150,422,1000,-572,200,-531,7,-314,1000,-1000,-977,-1000,-1000,-105,-11,-889,-94,1000,146,193,802,-662,197,522,1000,441,662,-863,1000,144,-1000,769,110,1000,-441,-71,-744,-562,601,-1000,-1000,-1000,369,-77,967,-986,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00339() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-665,0,709,-75,910,-61,-274,162,-668,-1000,1000,489,-153,1000,-1000,-1000,561,940,121,1000,-492,524,-551,-617,-80,157,-541,547,346,885,-760,-36,-281,632,1000,-1000,1000,44,-1000,-3,1000,-594,1000,278,887,766,-718,-408,550,27,-690,-337,341,-692,1000,-1000,-916,-234,192,965,-477,263,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00340() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{622,906,144,-733,279,1000,237,-196,-713,-1000,1000,560,957,227,-1000,546,1000,-177,-474,1000,-1000,512,568,59,-66,1000,-543,-306,-1000,-1000,213,107,-56,-910,1000,0,59,590,126,60,10,1000,-726,-384,-1000,1000,950,-1000,1000,110,451,-891,-468,1000,-222,307,-1000,-717,-975,-460,-1000,599,-1000,-882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00341() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-851,1000,-436,31,994,-953,-333,385,-259,-1000,550,-571,-839,1000,-1000,-601,466,1000,-235,1000,-1000,24,-407,-771,152,-106,-1000,-1000,-567,893,-537,-944,724,747,1000,-872,1000,-1000,-699,-132,593,1000,1000,271,976,531,-921,-500,549,1000,-1000,-221,187,-577,-468,591,-1000,-324,124,1000,497,930,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00342() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{1000,228,239,-1000,-444,1000,-435,-929,1000,-1000,664,651,755,-108,539,537,329,807,728,1000,-277,-391,-295,-175,-476,759,-1000,-1000,-1000,427,503,-866,149,-220,1000,249,-938,795,-827,542,620,1000,-3,1000,510,-535,843,-1000,556,1000,1000,-1000,247,-777,-573,-901,-1000,-1000,-1000,-126,-408,1000,-569,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00343() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{378,-992,-926,282,693,-562,636,-371,-591,-676,685,815,607,-94,741,170,-21,544,330,-575,550,-856,-817,881,-910,-673,651,768,458,-609,690,-229,971,241,177,555,-624,-427,298,69,954,-193,-743,-624,889,-447,-323,-200,-862,698,-284,-678,745,-401,-870,-711,773,-648,302,778,-134,571,-377,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00344() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{434,1000,-570,-52,96,994,-205,128,703,412,-484,296,122,-601,75,-652,114,626,209,-428,952,-1000,-338,-256,-721,-528,-65,764,400,615,102,-217,-944,746,-14,-7,-700,-336,-250,112,-62,228,295,592,414,-83,-163,-120,-1000,969,353,-1000,708,-90,-391,-513,-40,1000,88,-837,156,543,424,664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00345() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{454,1000,939,-676,-745,1000,-731,-94,1000,929,132,-434,1000,-1000,-446,664,1000,1000,105,807,273,-615,-782,-144,-422,272,-1000,-823,-854,279,-387,-266,-969,954,907,-233,186,-142,-553,740,797,335,959,1000,-169,752,-158,-1000,-521,772,1000,-741,447,-880,-138,-721,-1000,682,-1000,-276,-537,1000,-530,-219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00346() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{-209,86,-663,132,383,-731,1000,640,-259,-1000,1000,529,742,1000,-121,1000,968,-311,-237,1000,-1000,-106,-366,1000,-647,-385,361,1000,-165,-399,-529,880,1000,-985,560,-284,-1000,-687,850,479,920,-12,-1000,-1000,468,-636,-531,7,926,507,-1000,-362,931,-1000,-747,-240,811,-470,125,573,-1000,677,-1000,-686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00347() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{394,-817,-1000,1000,477,352,-333,-675,359,1000,167,1000,154,396,260,-51,239,696,95,-1000,213,-525,1000,-808,-756,-1000,537,878,-1000,542,870,-684,287,-258,-78,360,35,-5,572,-243,-6,136,-251,-238,-389,1,982,-204,-658,1000,-540,-943,272,907,772,-141,868,-1000,-26,-967,-1000,-530,-210,-836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00348() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{336,772,-141,-1000,68,78,-155,53,605,-1000,-1000,-239,381,-18,1000,-891,-619,194,1000,174,583,-1000,-926,217,-1000,-345,-209,-785,288,-483,385,263,248,86,502,262,-1000,428,-831,-552,612,365,721,273,1000,-88,-288,-390,-304,230,1000,-978,811,-843,-1000,-207,50,-769,-182,1000,1000,775,541,985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00349() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{1000,427,1000,-17,143,1000,-1000,-1000,1000,-999,-894,960,-939,-1000,-990,-537,21,1000,-154,-1000,1000,277,930,-1000,-614,-358,-1000,-693,580,1000,1000,-1000,-1000,158,379,308,-471,1000,-231,-94,339,327,705,714,-923,681,272,-472,-1000,978,598,-1000,675,497,677,-560,-725,-284,-528,215,51,145,1000,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00350() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{624,625,-101,228,-271,-66,637,254,-269,802,-8,408,-17,-311,141,514,248,-173,500,895,351,602,400,103,-926,-530,-314,-945,329,804,-617,858,530,127,33,481,532,344,-784,342,514,789,359,895,708,163,-9,-694,-934,778,281,461,579,-88,-96,700,275,525,-911,-98,686,125,8,-131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00351() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "era():org.joda.time.MutableDateTime$Property",
            new int[]{892,-897,-631,386,348,1000,-456,-1000,565,-516,529,1000,-684,218,-467,-574,518,839,-663,-842,-329,84,1000,-1000,-526,-314,-987,-65,328,442,1000,-798,351,-738,667,318,223,745,272,378,153,1000,-575,-467,-1000,696,1000,-980,-337,1000,956,-1000,203,1000,1000,-17,-1000,-603,-810,-720,-1000,407,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00352() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{-591,192,938,913,-926,26,288,187,-954,-950,375,972,-306,-721,387,-733,-935,-882,876,-918,769,-861,282,452,-472,-590,518,1000,-686,346,-386,-846,-104,-217,82,927,573,237,-601,-540,886,190,-184,-602,-833,-391,542,636,970,922,296,-57,566,170,198,556,20,-837,8,962,-346,148,863,761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00353() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{-908,-1000,1000,-247,-175,-326,-169,76,341,483,171,-718,907,-215,-1000,940,598,1000,-69,666,-105,409,-823,238,299,-476,43,-938,400,996,-405,-280,226,-787,356,578,804,-56,349,-301,-91,-505,-1000,-371,92,324,-400,-18,321,100,-868,865,119,-232,-37,1000,60,726,-1000,144,-576,-60,274,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00354() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{184,-756,459,576,1,-537,-731,427,-40,-871,-737,1000,-138,-677,212,454,-885,-1000,249,781,589,-1000,552,1000,-486,-1000,932,-360,-752,-823,699,-248,963,-586,629,892,-512,-27,-1000,-73,-623,567,-25,-1000,-188,-551,660,1000,-399,664,-176,-469,49,-271,644,1000,-1000,-1000,-125,890,674,-1000,812,793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00355() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{-21,-818,354,677,-502,267,16,327,-607,-245,-362,-185,-239,1000,1000,769,841,703,-1000,370,-289,-519,-94,1000,-463,-1000,-495,-9,1000,888,-1000,-961,618,-243,597,1000,-442,437,1000,1000,-299,-1000,-505,728,743,127,-952,-950,1000,458,-720,1000,-78,-63,-768,141,-687,1000,-1000,-1000,-532,1000,179,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00356() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{-908,-934,1000,-632,-922,-419,-36,761,-472,-493,1000,-330,-625,-306,-435,1000,-356,770,-784,-310,340,-592,-84,338,1000,-476,990,-972,-1000,996,-74,-484,1000,-163,375,518,1000,-806,-696,-1000,841,497,1000,-1000,-1000,764,1000,963,1000,20,98,274,22,429,1000,-1000,-1000,-361,-484,972,-873,-1000,-46,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00357() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{490,-347,-558,-131,1000,-690,-141,-492,1000,1000,-626,84,451,1000,-702,390,-317,-54,258,922,-46,1000,-25,280,7,-342,212,-411,614,-593,-594,1000,-420,88,-813,-225,-1000,150,1000,470,-1000,368,-351,1000,-314,-695,-938,-1000,103,-712,-939,-301,-401,-612,-446,401,-213,-240,-488,223,1000,614,196,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00358() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{-630,-920,438,-1000,69,739,-452,627,646,683,1000,-871,415,-199,-553,841,-813,567,179,1000,-278,-937,512,-467,1000,40,454,-1000,163,-126,1000,-146,1000,-102,-217,-800,782,-669,-891,-1000,1000,749,1000,-1000,-1000,190,-61,201,985,-1000,63,-224,-1000,1000,-291,-1000,-761,181,-236,-18,341,-1000,-942,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00359() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{-316,-580,189,-692,328,255,-113,-62,619,722,681,-441,474,-752,-502,-271,-715,-719,-77,930,-29,-734,420,400,-51,-642,160,-628,260,-213,1000,-104,619,-952,-453,-395,-1000,-117,-935,-300,-347,76,1000,-440,-736,-90,-201,-5,186,-553,-144,-75,-744,462,-444,-376,-361,171,-416,814,1000,-784,48,-53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00360() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{667,-521,483,-196,-308,724,372,-449,-453,-595,679,866,351,451,-60,396,-1000,892,-60,82,836,-1000,200,1000,668,-1000,1000,694,-1000,98,-176,-649,855,1000,-181,781,-372,-365,-725,100,-1000,1000,-1000,356,-1000,1000,594,-309,-710,704,-552,408,208,-469,1000,-539,-1000,365,-216,1000,376,107,857,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00361() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{-464,-1000,737,126,-821,173,-562,94,154,-683,896,1000,535,183,-531,1000,-543,1000,-573,477,643,489,-98,958,-56,-764,223,-499,-557,1000,894,-139,-300,-1000,-1000,447,-615,-203,1000,1000,886,186,961,-803,677,-520,-200,-1000,809,-480,46,1000,481,-1000,752,-202,-237,1000,-226,685,160,1000,972,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00362() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{14,-84,729,-88,-652,-52,-505,-546,-397,-1000,465,-36,-697,206,1000,-10,-64,-807,-230,-1000,161,-414,1000,536,1000,-560,805,124,-1000,241,-400,-1000,-118,-123,1000,330,472,-571,-522,-1000,1000,1000,-502,219,-1000,1000,1000,1000,-354,403,97,-355,-672,1000,37,296,-1000,-588,146,6,-579,-1000,-207,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00363() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{-960,-1000,1000,278,-781,-52,233,203,-554,-403,465,-250,-915,-1000,-941,-51,-588,522,1000,-1000,1000,-301,-1000,569,-558,-675,608,542,-1000,880,-1000,-991,53,-1000,-725,781,794,-455,328,-393,-513,400,660,-1000,-459,25,430,852,1000,689,-34,606,1000,-1000,1000,167,-895,-1000,-1000,1000,-1000,-71,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00364() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{-312,-473,187,364,-799,-541,-513,785,-758,-431,208,491,65,1000,514,367,-561,-641,-1000,-401,-425,-769,506,1000,1000,-1000,1000,341,-78,1000,559,-4,110,832,1000,1000,-870,481,-1000,164,733,-587,-1000,1000,-318,387,171,1000,-633,876,-939,165,-269,71,-637,137,-1000,1000,-148,-129,947,-473,-1000,-836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00365() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{-445,98,741,1000,-894,-26,-620,214,-1000,-850,208,1000,-69,1000,340,-759,-645,-864,-511,-1000,140,-371,175,1000,-168,-1000,650,1000,-180,1000,-482,-612,-891,-653,521,1000,-436,930,-243,462,437,-499,-1000,-213,-71,-382,110,-274,-40,1000,-424,310,579,-145,-525,1000,-181,-313,-51,329,121,733,65,833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00366() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{354,-941,315,722,-236,-846,279,-560,-426,-672,-362,838,-340,-246,-248,-1000,-541,-1000,249,712,330,-519,-94,236,-483,-1000,580,-526,-95,-602,5,-185,618,-687,253,1000,-845,437,-1000,530,-1000,-181,809,-368,-369,-945,0,521,420,1000,-782,-55,414,-1000,312,1000,-834,-583,-716,548,826,-707,1000,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00367() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingField():org.joda.time.DateTimeField",
            new int[]{-536,-1000,435,783,-347,-256,69,700,-314,-176,-89,304,279,915,160,-166,155,1000,-1000,-179,134,186,112,1000,267,-1000,1000,32,538,1000,-304,-565,-558,-707,-38,834,-439,199,-1000,905,-255,-117,-256,0,634,-426,-354,-515,940,227,-163,1000,24,-195,-473,31,-947,1000,-840,-203,-306,789,-146,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00368() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{96,17,944,791,894,-726,-636,243,393,-978,355,-41,-391,979,188,-239,-688,-865,-465,909,-127,-504,170,291,235,-305,118,0,986,104,146,-875,997,-296,547,-958,-816,-191,-272,-932,540,522,-680,-77,807,721,195,134,891,310,108,-731,443,-494,486,-366,-98,-9,-671,-998,-251,918,-874,797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00369() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{-814,-978,-306,-575,-792,85,-895,-824,-579,367,-710,649,796,-951,-727,157,779,351,-195,497,509,-281,836,-598,-503,-31,-395,-932,-80,-334,440,-774,-797,-281,-602,764,-394,-44,-542,423,-825,-749,-841,-274,-398,682,-742,942,-712,-848,322,-333,211,928,952,-654,-893,608,-201,-14,859,-797,456,-827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00370() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{534,953,-1000,369,943,-236,-362,-506,-818,-39,-1000,603,1000,9,-97,900,147,177,-261,-480,-303,-1000,437,-38,345,-497,761,100,-741,-490,-1000,-619,406,-1000,-1000,1000,445,1000,-714,517,-253,-379,550,-1000,399,337,1000,-9,-18,-213,408,893,1000,262,355,-558,-566,78,23,-369,-115,-1000,380,-960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00371() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{40,130,219,-101,438,-187,13,470,-550,378,-857,-337,1000,37,599,867,-385,464,-1000,-491,775,-1000,1000,1000,-740,-623,-348,781,35,-808,-186,-1000,1000,-1000,-250,-300,-1000,880,-36,-535,472,1000,-1000,132,-775,92,285,1000,1000,-1000,966,-112,979,-414,441,724,-409,1000,729,799,767,-1000,365,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00372() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{644,398,1000,358,894,-365,-41,824,393,-872,1000,-157,-1000,1000,605,-466,-718,-994,-465,859,22,-504,170,291,215,-41,-516,-972,1000,407,439,-316,1000,515,919,-1000,402,-742,-272,-1000,821,827,608,541,558,841,148,-199,1000,926,-394,-687,-1000,-1000,261,-1000,400,-920,-427,-863,-208,1000,-1000,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00373() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{291,108,398,368,1000,-833,-326,735,995,-557,86,302,-908,-589,310,-144,-88,-1000,244,693,198,512,-1000,1000,1000,-444,89,1000,387,958,641,-735,887,625,1000,-560,-1000,-406,763,-1000,351,-279,145,-13,772,1000,112,-940,562,1000,-934,-445,-35,-833,-157,-233,1000,-1000,-1000,-1000,-519,727,-671,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00374() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{-848,-1000,1000,1000,340,-271,-1000,649,822,96,800,-345,-970,-361,173,-876,-475,-1000,1000,-950,431,-65,289,19,89,87,-354,-300,1000,423,1000,-1000,703,480,-358,-1000,632,-1000,-23,-1000,635,266,-1000,846,291,954,-441,-418,1000,194,36,-1000,-485,-124,640,-556,20,505,-361,-887,-486,1000,-924,975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00375() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{841,-20,-449,594,-183,93,-1000,-1000,854,1000,-181,-1000,312,930,-339,1000,665,-617,-759,640,-131,-424,1000,734,-1000,753,-1000,-821,-367,728,-791,293,304,-616,728,-42,-549,826,425,796,-238,-1000,357,854,-819,619,-1000,1000,1000,-1000,741,125,130,106,8,-1000,-365,-362,-221,752,710,719,318,930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00376() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{963,914,69,279,-788,-358,512,-821,-208,-704,-986,183,312,-400,810,736,529,670,-901,321,90,-152,445,10,-491,-904,6,626,421,125,-434,902,-365,127,-625,-515,-391,703,-621,205,-437,493,605,634,648,-183,-830,-384,-654,-343,-332,214,-411,-707,-451,292,-52,-995,-507,-159,-294,371,753,44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00377() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{380,-336,564,-530,-302,-531,-1000,360,-58,367,301,-1000,1000,671,-1000,350,-4,703,1000,-960,708,-1000,-221,-659,-33,1000,-671,-805,-971,7,-1000,638,-318,-856,-540,-830,719,1000,-759,442,229,287,98,-109,-145,39,-918,-1000,1000,400,884,916,814,156,954,-396,-851,684,741,-1000,971,1000,-692,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00378() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{-449,-1000,845,-969,22,304,-1000,-96,170,458,1000,-941,721,-499,-1000,-389,-46,-865,-465,-1000,657,-1000,1000,-498,-836,1000,-1000,-1000,-174,104,-295,-689,1000,-663,-217,-1000,1000,192,-841,-314,540,839,-680,-183,-1000,435,-183,134,1000,-743,1000,350,443,386,1000,-1000,-937,1000,1000,-282,1000,974,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00379() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{-1000,304,481,325,-706,683,-1000,-1000,-24,1000,929,-225,958,-233,-53,-414,688,69,-400,1000,354,-168,1000,515,-1000,1000,-1000,300,-500,-290,483,-419,767,-60,91,-457,-1,-583,432,-551,138,238,-1000,338,-679,682,-697,1000,544,-1000,-813,-398,-257,79,287,-332,-885,1000,230,711,52,-243,-836,264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00380() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{236,28,-519,-976,-311,1000,-74,-885,157,-649,640,69,839,157,-1000,-346,785,664,-471,-223,1000,-1000,1000,-1000,-668,1000,-806,-1000,-750,-76,-973,814,-375,-1000,-1000,333,-321,1000,-1000,1000,-864,883,-244,-667,-1000,554,-604,588,-185,-210,509,1000,471,1000,1000,-1000,-621,292,1000,-630,1000,508,-984,-282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00381() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{-117,108,-302,504,791,-833,-980,-642,-650,-557,-1000,-874,1000,-589,-504,927,-692,92,96,-57,296,-1000,-972,914,860,-356,89,1000,-430,-289,-741,-1000,189,-825,329,133,-1000,-406,303,-558,430,-279,-1000,-520,232,485,-214,-14,1000,1000,868,-255,1000,-487,-157,64,-673,746,-828,-682,50,-160,458,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00382() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{300,-1000,1000,439,-335,164,-528,141,1,-421,1000,93,-1000,-770,-675,-1000,48,-313,1000,-993,-388,-591,975,-1000,-703,276,-468,-1000,827,316,-268,475,-508,322,-288,-1000,1000,-400,-1000,-506,-90,1000,767,307,3,642,-421,-297,862,400,946,384,-1000,925,178,-1000,132,-1000,961,-375,1000,1000,-1000,-462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00383() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "getRoundingMode():int",
            new int[]{-982,-1000,-278,-947,-1000,904,-817,-238,-3,1000,312,415,-150,-1000,-192,112,1000,816,813,-1000,986,-335,761,-1000,-1000,24,-602,-1000,-347,-415,1000,-345,-1000,506,-767,-408,824,-224,-1000,489,-1000,-74,382,690,-974,265,-797,-503,103,-1000,1000,596,-841,1000,206,-903,-1000,-282,1000,184,1000,-286,-765,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00384() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-885,1000,-306,-961,-259,1000,133,-128,-139,-931,1000,193,914,-955,638,1000,1000,419,-722,-1000,-1000,1000,818,-1000,1000,-682,1000,-1000,-343,1000,-313,237,-1000,852,1000,324,1000,1000,-535,534,-167,99,499,-601,729,-1000,-635,-436,-295,224,1000,824,-1000,671,-1000,-236,-579,-1000,-216,-741,-84,1000,-727,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00385() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-696,305,341,619,-357,659,393,-691,1000,246,330,1000,796,-4,971,-1000,-361,60,-718,-285,-1000,1000,-1000,-1000,1000,-777,-38,-1000,-582,-966,-70,1000,-680,896,140,564,1000,745,569,-368,-417,233,649,-1000,708,558,1000,-463,-635,442,368,-519,325,-122,37,-86,20,-1000,1000,660,240,256,869,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00386() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-961,-25,1000,1000,424,-799,1000,-567,1000,-1000,-611,-1000,747,158,679,716,-411,-488,-64,-471,847,584,635,222,402,-1000,1000,590,-1000,-1000,1000,-320,-1000,1000,-1000,551,1000,-354,-736,1000,-438,-377,-1000,766,1000,973,1000,-606,333,-1000,-1000,137,1000,-424,730,1000,75,-1000,9,-860,81,-1000,1000,124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00387() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{135,-1000,-954,393,-893,-941,604,-460,658,531,-1000,424,1000,-1000,-166,-1000,-316,899,-1000,619,604,-994,-1000,1000,-102,892,-329,803,-175,218,-381,-494,718,311,369,1000,104,-1000,1000,-24,-466,-986,-786,723,-8,746,-233,-915,-1000,-122,-1000,-785,-50,-473,-1000,367,-1000,-382,-370,245,-983,27,746,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00388() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{217,-110,991,251,-456,652,-540,-527,748,-1000,-766,-907,911,-218,277,837,-580,-364,883,-834,-51,1000,927,568,267,-1000,212,589,-682,-79,662,-615,-1000,60,-197,381,716,530,382,476,-839,819,617,736,747,559,-629,-1000,611,498,-50,-107,85,-1000,-258,-680,905,-438,675,-594,402,382,-294,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00389() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-840,-66,-1000,-398,-647,1000,-490,223,-19,-33,591,335,217,420,-192,-427,-127,-615,212,365,495,-167,785,440,-1000,-424,1000,-430,374,-558,713,-967,1000,-75,687,218,-582,935,508,-585,-65,1000,-861,-425,260,452,506,-1000,543,-621,-906,381,407,-228,-499,196,-1000,-1000,744,1000,-163,768,-487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00390() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{1000,305,-691,1000,716,383,116,-691,295,199,-345,-992,313,-4,189,-554,602,890,-642,-681,17,121,289,1000,-493,-109,-485,-92,-259,956,1000,-585,-486,-1000,819,165,707,463,869,662,-1000,618,452,1000,1000,707,144,-212,768,634,448,-1000,325,-1000,297,-778,657,151,1000,-962,1000,944,433,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00391() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-253,158,0,328,-964,-730,924,-846,681,-483,-599,-155,918,160,-409,-841,-860,367,-548,-99,544,965,-516,497,551,-157,101,761,-193,-908,-157,160,354,466,-459,752,59,-478,232,-797,-243,-40,-847,823,-258,91,421,-645,-805,-902,-795,-531,-50,-165,72,312,590,-735,11,282,285,-693,179,-741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00392() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,774,-887,-716,214,594,605,-362,-210,-278,1000,971,477,-731,637,731,1000,336,-1000,-564,-1000,507,66,-858,260,-703,561,-848,-646,529,-790,1000,-838,1000,1000,88,508,629,-251,455,404,-520,56,135,-110,-588,787,964,-316,367,1000,295,-781,1000,-861,141,-1000,-832,-849,-514,-648,846,-208,899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00393() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,885,239,-968,-916,-391,295,-239,187,-1000,834,-179,905,327,638,1000,643,971,-1000,-493,-310,-121,235,-1000,1000,-207,903,-782,-95,38,-1000,-684,-801,372,568,358,1000,63,-1000,561,674,-604,-702,-783,130,-534,-364,-284,-1000,-1000,7,1000,-1000,1000,-1000,814,-1000,-1000,-1000,-874,-1000,10,-927,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00394() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-662,1000,-294,-216,-728,682,-864,-79,86,-569,-46,453,857,-1000,853,622,342,385,-572,-919,-413,1000,-70,-349,731,-275,1000,-786,-684,769,196,979,-884,-204,1000,1000,1000,1000,-38,-319,-752,116,484,476,726,-595,-321,-1000,-601,293,977,-23,-769,-71,-1000,-31,162,-944,422,-84,816,750,20,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00395() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{1000,989,-1000,-749,-285,933,-830,-663,5,-318,-478,875,179,812,-1000,702,833,940,-789,114,-983,-1000,-340,-509,-649,1000,492,-618,380,416,980,-1000,623,470,1000,484,-259,-400,1000,-350,-189,-1000,-443,684,59,-722,-1000,87,-1000,81,1000,-231,-1000,334,-1000,-417,-789,229,423,10,-808,1000,-1000,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00396() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-634,-347,19,766,-843,-996,816,-42,1000,-117,226,51,182,1000,1000,-682,-145,355,-1000,94,394,115,818,-72,1000,-322,-203,516,-424,-1000,-1000,119,-682,-836,-174,800,1000,-1000,175,320,-245,-640,-1000,-813,729,1000,787,125,-1000,-816,1000,824,229,246,-206,1000,-579,-1000,-1000,-213,-1000,-737,658,-997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00397() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-64,431,-530,639,844,383,536,-292,644,233,745,-850,474,-574,1000,-554,864,662,-776,-1000,-226,1000,592,1000,437,-1000,-319,1000,-387,1000,400,281,-1000,-358,959,-65,1000,807,255,979,-1000,1000,906,957,1000,507,399,4,873,1000,476,-928,325,-1000,297,-765,158,-421,1000,-1000,1000,944,568,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00398() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-1000,-229,1000,1000,198,298,-834,907,208,392,-867,-59,-231,1000,105,592,-464,1000,-1000,-633,-597,1000,-303,-190,-447,-822,-229,-928,570,370,-1000,-662,-1000,822,-1000,-73,169,1000,1000,-370,629,14,-453,899,1000,-604,769,1000,1000,-560,-1000,404,-1000,341,-503,879,645,242,-1000,511,655,-128,18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00399() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "hourOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{533,989,-576,-214,-442,833,-891,-663,161,-819,-131,553,981,-679,-25,976,631,591,-822,-544,-909,787,115,-251,719,245,738,-618,-175,777,129,-804,-502,-826,925,890,930,791,281,81,-213,-188,-188,938,412,-714,-729,-714,-446,-298,865,205,-836,-11,-960,-356,366,-850,354,-687,141,443,-405,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00400() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{162,-1000,343,-413,-264,449,460,840,-943,-1000,-462,-930,-1000,-612,498,830,462,-1000,-331,-17,-153,-430,306,249,550,-256,-704,1000,-901,-521,867,-802,-1000,-1000,834,662,149,135,-293,-1000,-299,688,-324,-841,158,-579,-302,-160,-250,950,725,1000,1000,298,931,244,420,-819,992,531,18,585,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00401() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{1000,414,-426,-458,682,-612,-593,618,572,939,325,-561,825,-6,-384,158,346,538,555,-280,-619,-66,405,446,323,207,-313,-667,543,-117,1000,-29,440,-162,90,-1000,-427,-266,599,219,89,141,907,-409,-157,-676,-370,-226,22,-636,-465,-146,-377,-551,466,-275,-1000,571,-135,-151,905,-257,879,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00402() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{707,-654,-332,-506,-290,-589,11,439,-39,-297,-207,-245,65,159,417,467,-326,-77,593,128,512,-62,302,-567,52,540,-518,158,-47,265,1000,-440,-402,-578,-75,-473,-22,441,-405,57,-103,477,-242,250,-51,-544,144,-255,360,928,-604,189,306,275,1000,305,-606,296,762,1000,-15,537,-998,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00403() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-846,-630,-591,763,-509,430,-921,95,339,589,90,-826,138,-1000,-1000,350,1000,-928,222,-210,-958,-1000,1000,-1000,852,-542,524,-718,-784,24,-207,-553,998,-953,1000,-1000,208,729,100,-1000,-135,422,1000,-832,171,336,497,371,-1000,517,-163,-163,-289,-351,54,-1000,-933,-683,-65,807,-157,-738,-1000,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00404() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-639,101,-184,359,204,-136,1000,-60,1000,-234,126,1000,-211,-179,855,368,-1000,168,394,124,-533,185,692,47,-1000,-746,596,723,-443,953,-46,318,910,787,-597,-469,-542,109,939,-105,78,-428,-81,853,198,895,422,-394,190,243,-78,-568,297,216,-557,-180,369,-644,-734,-664,-519,-820,301,-539}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00405() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-460,496,-174,-337,-941,-724,-690,618,579,765,563,-279,1000,-429,-269,-123,126,1000,-23,287,-94,-66,106,613,281,441,143,-667,21,-440,877,-125,456,-87,-303,-1000,-61,-747,693,32,74,357,734,-131,-86,-1000,-1000,-284,86,-786,148,-440,767,-478,130,256,-844,739,57,-194,427,-580,879,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00406() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{880,-155,678,1000,-976,154,460,-900,27,266,-511,539,-128,-328,38,214,-950,146,-389,79,-269,130,494,-793,28,-637,1000,484,146,575,-1000,-550,-360,-175,-199,792,-568,-256,749,-596,-794,-9,-1000,11,-619,152,1000,-372,85,562,577,-285,1000,1000,-1000,-302,-562,-1000,419,-466,-1000,-556,550,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00407() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{880,573,-684,57,974,395,767,1000,732,-351,304,775,-49,-280,-297,283,-631,668,-20,-544,-638,130,-683,60,-697,-381,1000,548,146,456,1000,489,654,-242,-555,-863,-1000,-273,749,4,-794,-1000,606,-83,12,1000,77,-45,97,-131,360,68,-208,-214,-631,-302,-538,-278,-449,-1000,48,-885,884,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00408() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-164,-24,-351,325,-417,-246,-367,56,-1000,355,-498,-579,-202,-671,-1000,472,404,-32,-586,-248,-96,-871,870,231,244,-219,-360,318,-288,-548,926,-652,-1000,-966,346,-72,-304,533,-1000,-900,613,894,-181,-767,-265,-264,-171,-27,-108,965,228,962,749,532,-41,-136,-320,-310,1000,1000,86,-368,-884,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00409() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{810,-166,-354,-752,913,-4,-500,1000,740,-571,571,806,275,92,-596,256,-697,1000,-590,158,649,1000,-529,1000,-611,266,393,-376,-784,-195,522,211,1000,931,-1000,-1000,213,-1000,1000,517,-209,233,293,806,601,-663,-668,-374,-414,-943,-282,-983,-289,580,-329,84,998,1000,-251,-423,-459,-1000,1000,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00410() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-763,161,525,1000,-804,133,296,85,798,993,-409,-1000,685,-767,-942,152,1000,-888,-355,-597,-982,-1000,1000,-1000,346,-1000,497,-90,-1000,517,-558,-716,118,-112,1000,-161,-173,919,687,-1000,-202,955,639,-1000,-919,284,1000,-339,4,1000,-81,47,1000,-1000,147,-1000,-1000,-1000,-487,1000,-669,571,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00411() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{941,760,-928,359,204,218,-11,1000,940,-234,900,1000,941,-329,-1000,-246,-1000,1000,-1000,477,903,845,-1000,1000,-1000,-746,1000,-692,-703,-576,791,391,1000,1000,-1000,-1000,138,-1000,1000,570,-219,-428,-81,1000,472,-663,-1000,-200,-292,-1000,211,-530,-389,955,-1000,423,842,1000,-10,-1000,-519,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00412() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-19,-343,-1000,-1000,26,-392,-531,769,106,-827,-341,-203,-465,639,317,889,-68,-798,783,-402,-126,-35,873,-329,-79,318,-1000,-272,286,191,-269,-218,-366,-311,18,1000,64,1000,-865,602,-13,691,75,504,388,-165,664,-115,-103,-317,-699,614,118,91,-474,-32,-290,451,-509,-394,513,578,-1000,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00413() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-726,-241,1000,-371,949,-526,-60,-11,917,108,-1000,657,-1000,-918,689,1000,-1000,192,-347,-330,-1000,1000,-1000,1000,-968,768,-238,-523,300,159,-674,755,-1000,1000,-692,-210,612,111,-1000,-123,978,25,-1000,-243,1000,1000,630,-1000,325,111,284,962,-143,-40,-1000,-941,-1000,90,800,87,-446,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00414() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-210,-540,-202,-37,-40,-147,798,1000,106,-557,-401,-18,-687,375,363,881,-1000,-697,504,-716,-848,619,90,-163,-399,-592,-70,684,-436,877,-108,175,-238,-71,23,701,-1000,1000,-824,-162,-563,-342,-234,-519,131,1000,664,-417,283,1000,10,314,-62,188,1000,-454,682,-615,-195,32,683,604,-408,336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00415() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-928,-810,-217,-52,296,763,597,635,567,-272,23,866,-731,463,-346,722,-874,-246,-324,-174,-623,1000,-1000,402,-367,-696,591,626,-1000,678,-553,80,733,1000,-347,466,-377,-9,698,295,-379,582,-385,698,385,811,275,-451,-402,-310,39,-736,222,787,-363,-573,1000,-248,-434,-260,-164,-576,571,-295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00416() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{953,740,1000,-630,809,787,204,1000,125,-198,-661,1000,680,-236,172,219,-49,400,953,226,64,1000,947,1000,849,-1000,-850,-1000,-748,244,48,-1000,12,679,-1000,-793,692,224,-981,-267,33,1000,237,683,1000,-318,-338,-604,1000,751,-923,-1000,-1000,-463,320,-299,-251,956,-776,-302,-446,-1000,185,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00417() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{1000,31,455,-472,124,1000,-458,-135,917,960,99,1000,1000,-1000,552,-31,-1000,-95,879,-47,-268,475,-234,1000,-1000,553,-584,-772,-825,-287,-790,-1000,-56,1000,371,-1000,-184,925,-997,426,816,483,526,-904,641,-909,473,813,1000,-1000,400,-72,-1000,389,-1000,-254,-983,-158,-640,-900,-133,361,1000,-24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00418() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-623,282,61,-681,339,-914,-277,664,448,-939,103,-1000,-389,841,-233,431,-1000,1000,1000,-627,-539,553,995,831,-67,78,360,-668,109,-295,-1000,-171,197,-101,506,728,-1000,106,634,-742,272,463,-923,508,-759,-117,631,-540,825,87,-441,102,-389,-46,-309,-1000,-946,167,-242,851,1000,640,-790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00419() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{203,612,484,-101,-909,-474,129,652,-928,-561,-1000,1000,712,-279,-240,424,-674,626,70,595,239,1000,428,-731,1000,243,-445,1000,-825,-1000,312,1000,-222,-247,911,-1000,747,428,-564,391,-423,243,-20,471,455,-61,760,-768,1000,-711,470,-711,334,370,53,701,770,914,1000,-948,233,-599,368,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00420() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{-390,-1000,98,92,-1000,-167,43,1000,-1000,419,-861,-1000,1000,-1000,-427,48,-579,1000,90,-1000,-358,538,50,-1000,400,-428,-631,332,-1000,200,912,957,-487,-674,1000,-823,-1000,1000,-1000,1000,-337,-1000,614,511,-861,-77,-44,-1,-85,-1000,-918,761,-719,-871,-1000,6,542,-82,1000,-580,3,-1000,930,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00421() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{525,-546,-836,542,-243,938,1000,-180,480,-1000,-1000,-1000,-712,-587,-331,1000,-1000,-1000,36,-420,-889,-253,-865,-68,-848,-613,-428,1000,-938,607,1000,1000,295,1000,795,1000,462,373,1000,956,-1000,-529,185,473,-531,1000,-1000,766,-1000,1000,1000,-733,-284,-1000,970,-321,499,54,1000,1000,1000,1000,1000,881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00422() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{505,729,99,-537,-466,-123,20,-364,-681,-540,170,-8,336,-259,302,16,-847,57,349,98,567,538,882,767,1000,555,-895,172,-768,-1000,80,-565,-1000,775,873,-1000,218,127,-1000,130,969,667,661,1000,177,470,1000,-307,935,45,181,52,673,728,-196,-158,-939,361,125,414,459,-719,540,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00423() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{334,583,113,-136,-197,306,-1000,182,-235,178,-900,64,-1000,-432,895,-242,-87,-1000,-15,984,-1000,-1000,-602,388,282,-261,225,261,-950,1000,-370,-1000,-912,149,-1000,1000,1000,-1000,834,205,651,461,29,-1000,824,-312,-1000,-213,-664,858,-253,-611,-1000,-851,-1000,-413,237,-936,-27,-259,1000,1000,830,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00424() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-440,-849,-846,960,1000,263,90,783,-1000,-417,-178,347,-18,174,1000,-917,-92,-23,399,-350,-925,884,106,831,1000,-194,341,-589,554,-438,-1000,325,197,369,-1000,1000,1000,-46,-72,-931,1000,-103,-1000,1000,-369,-1000,-271,1000,731,-971,-1000,-1000,-1000,-1000,20,642,1000,-1000,-324,-35,114,696,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00425() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-833,-23,554,-239,1000,625,-749,1000,-1000,-311,326,648,76,154,1000,-807,669,1000,399,-448,475,729,-440,1,871,-1000,1000,-1000,-599,-253,-277,-153,851,-397,-185,1000,1000,-98,206,-625,-245,549,-220,980,-37,22,638,1000,177,-479,-657,47,-317,-611,-420,214,1000,-772,-1000,325,-252,408,108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00426() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{371,-1000,-444,1000,-744,984,-1000,-749,720,448,-1000,-209,-1000,-478,1000,-446,436,-1000,950,1000,-1000,-596,310,34,-910,-264,-220,-395,-3,1000,-253,-1000,-476,-249,-383,861,192,-468,94,179,1000,-245,-827,-826,707,741,678,859,-930,285,-189,-1000,1000,-875,-611,84,-851,-1000,-385,-765,775,1000,669,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00427() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{25,735,681,183,-274,602,-1000,-749,574,277,379,1000,-332,-669,797,-707,-532,-261,965,1000,-96,-108,439,-375,-131,-211,-621,-1000,-998,-12,-253,-529,-974,851,-397,-753,1000,113,-743,-656,641,180,486,-635,1000,-869,134,288,1000,177,-509,-1000,-10,-317,-1000,27,-360,249,-1000,-1000,682,904,408,-972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00428() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-326,-969,-603,729,1000,256,442,1000,33,-176,326,1000,-1000,543,1000,-553,-398,739,823,65,-867,1000,1000,190,1000,-512,1000,-1000,1000,151,-803,202,-233,-1000,-185,884,-75,117,-488,-102,-245,959,-1000,1000,-484,-521,394,1000,947,-1000,-1000,-1000,-220,-923,-469,-891,597,-939,404,-72,-252,-66,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00429() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-900,-512,288,-1000,971,-222,-559,418,744,-1000,-1000,-573,-1000,596,884,-803,-496,950,-109,-1000,-57,840,1000,-1000,788,-728,1000,-1000,451,312,876,-64,759,380,861,376,-801,746,1000,350,-203,1000,-1000,317,-399,-1000,1000,-544,1000,-298,239,-732,-1000,341,-908,-664,-1000,1000,64,1000,-1000,35,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00430() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{937,-1000,62,1000,-1000,902,317,-1000,655,225,-1000,-1000,1000,-274,671,954,-117,1000,1000,-127,-1000,1000,967,978,-1000,311,-1000,694,-471,-369,100,-45,-472,1000,1000,1000,196,-799,-181,1000,537,-1000,1000,290,-1000,-345,357,1000,-558,610,-1000,791,234,-1000,1000,-1000,-1000,-873,556,1000,66,-1000,722,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00431() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "millisOfSecond():org.joda.time.MutableDateTime$Property",
            new int[]{921,-916,68,468,809,1000,1000,-367,993,-1000,-396,-1000,1000,-470,-156,1000,-907,1000,730,-54,-681,-1000,1000,-651,842,487,-1000,241,114,-841,659,876,-71,206,-1000,-1000,4,1000,-1000,475,-968,-409,596,1000,-646,-399,331,792,1000,-385,-176,9,121,-527,1000,-536,-664,1000,143,-110,-1000,-1000,35,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00432() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-104,-1000,1000,840,1000,509,871,-1000,1000,-1000,436,124,-200,1000,-1000,-1000,588,-769,-1000,318,806,-930,460,-1000,1000,802,-706,271,-902,198,1000,1000,1000,525,744,1000,1000,834,-1000,432,1000,307,700,-1000,1000,-748,-191,1000,-1000,-1000,-6,-833,-925,-721,-1000,-3,-671,-840,-575,899,-758,-1000,656,868}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00433() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{430,-438,328,923,-743,456,-443,-460,-61,-881,187,-53,-357,-717,-741,-561,176,-468,-102,-337,1000,16,-371,-786,219,-638,-337,371,-43,142,-538,726,560,-387,107,-633,-178,-448,897,372,322,-402,218,203,72,-923,437,699,-805,-843,-375,-255,-282,-91,-306,147,-575,-304,-151,-214,-1000,-568,1000,478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00434() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{778,-11,156,501,-597,161,-674,-241,212,-584,1000,-15,275,-824,-683,-5,-524,492,899,-809,1000,-298,-557,-1000,472,759,-317,1000,-675,788,-548,1000,577,-461,-168,168,-53,-453,1000,209,-213,-484,721,964,-40,-714,-517,434,-686,-1000,-679,-88,175,98,48,-376,-825,-693,-87,810,850,-621,56,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00435() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{78,-914,445,-316,-41,589,-153,-472,-916,400,1000,-162,-90,-992,-339,1000,-250,-646,1000,-1000,-1000,1000,-1000,1000,-1000,-1000,-1000,-1000,-457,-487,-1000,816,-1000,-909,231,-943,-96,-458,-1000,-427,-1000,1000,129,792,-1000,671,-670,1000,-238,898,-1000,974,-1000,18,-1000,462,1000,1000,870,-1000,-758,981,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00436() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-667,682,-92,-46,-352,723,-131,-69,-449,278,598,309,-78,-779,-335,718,-524,-642,899,-276,-999,890,-557,561,-268,-788,-369,-229,1000,-103,-1000,1000,3,-461,-308,-1000,-642,-453,-133,401,-765,568,400,401,-376,-147,-517,532,242,31,-1000,-44,-1000,-161,525,-191,379,435,-42,-1000,-630,-232,-997,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00437() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{369,69,-534,453,819,722,-815,-1000,-994,-1000,761,724,-1000,-133,-996,-140,-592,-521,-533,-1000,-843,-878,-1000,-1000,-3,64,-234,-1000,-1000,-467,-827,1000,338,-1000,-519,-128,-814,-274,-1000,-1000,94,1000,-1000,724,-840,-301,829,1000,-1000,499,-579,577,339,-1000,996,351,-548,-61,195,-40,-58,1000,-852,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00438() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{1000,73,-600,643,676,-130,-835,166,588,-471,52,-162,-35,-565,133,197,-261,937,-607,-427,1000,-477,-98,-709,646,-311,693,1000,-770,734,-242,559,-89,-15,-1000,292,-454,-837,1000,-268,93,265,578,608,-406,-602,1000,-699,51,-916,-185,301,1000,194,383,-198,-755,-985,-132,1000,1000,-134,-363,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00439() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-19,497,234,387,424,770,-960,-1000,-311,-380,1000,1000,-431,-1000,-984,204,-1000,554,403,-1000,-1000,107,-1000,-655,-586,-800,-1000,-348,-428,-573,-1000,1000,-436,-590,-1000,-915,-846,-866,-1000,-517,-876,599,-629,877,-857,-530,853,618,-612,515,-956,527,-1000,-478,342,489,-702,999,-660,-391,-74,1000,-890,143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00440() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{217,-194,-623,-21,-623,-1000,786,935,1000,-1000,-1000,-491,627,747,1000,-41,-274,-145,223,1000,1000,496,1000,665,1000,424,1000,641,651,-208,1000,-548,268,-954,-248,331,365,290,-990,-117,533,-1000,1000,-248,680,-647,-1000,-1000,1000,-1000,825,-608,1000,1000,-229,-128,107,-1000,22,488,55,-8,808,-471}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00441() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{570,-968,1000,-322,-682,1000,-60,338,-182,1000,203,1000,627,-1000,-1000,224,356,1000,-226,-360,-1000,260,49,-999,-1000,257,-1000,-1000,466,-1000,-1000,295,-1000,159,-260,-15,579,-566,-1000,988,-445,731,-64,785,214,953,935,595,-14,-341,-513,-468,-1000,-664,-977,545,10,-78,-742,-138,904,-981,244,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00442() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-55,-801,659,1000,-1000,448,48,-896,-182,-1000,380,-664,690,722,-102,-19,-613,-701,228,-517,503,311,-650,-107,-456,-746,-822,-76,145,318,491,809,528,-858,-168,-3,-564,-27,314,-153,115,-64,-1000,-187,-509,-819,-201,925,-1000,-100,-744,-186,-802,541,-1000,-145,-112,145,577,-136,-1000,470,71,35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00443() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{1,1000,-963,-21,-691,806,-497,438,-119,1000,-975,-212,-424,-594,733,1000,-618,-46,865,348,-661,567,-467,1000,-547,-775,533,563,845,-409,-1000,223,-1000,198,-558,-1000,-1000,-539,406,4,-247,237,-63,1000,-1000,399,-415,-782,1000,893,-508,552,547,-147,1000,180,646,194,-77,-1000,-242,460,-1000,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00444() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{579,-118,700,487,238,963,-384,-116,-775,-1000,-277,925,-205,-1000,-483,38,-716,1000,48,-427,-1000,-411,-786,-709,-294,-311,-370,333,-82,-51,-1000,559,-765,28,-484,-504,-577,502,-607,-464,-196,1000,-613,608,-786,-111,448,933,-546,675,-593,467,63,-109,-179,837,-123,393,-536,293,174,1000,-363,359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00445() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-317,-742,117,-356,-1000,-287,466,1000,351,1000,-626,917,527,-1000,652,1000,-558,912,865,424,-661,1000,398,1000,-1000,-828,296,-1000,1000,-670,-1000,-13,-1000,1,-606,-1000,-166,-459,-986,111,-247,-381,436,717,-245,399,-1000,-327,1000,-354,-184,-151,-435,755,112,365,1000,83,202,-1000,425,-201,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00446() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-1000,1000,656,1000,727,-139,-751,73,-153,1000,400,394,-1000,-1000,-537,367,1000,-787,-1000,745,-1000,-371,-1000,-399,-1000,-1000,621,-642,-60,-333,468,30,-590,-214,917,738,83,-341,532,9,921,-259,-10,155,-504,971,1000,-230,-980,-568,-13,-1000,-549,-1000,397,-249,-514,-510,1000,1000,-833,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00447() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{288,-1000,577,1000,1000,317,803,333,-188,-436,1000,850,-813,-688,-680,-28,1000,492,-1000,708,635,-1000,400,-1000,1000,1000,332,733,549,982,736,-1000,-316,678,549,-41,808,348,208,263,502,-625,106,-481,636,69,756,-132,342,-433,286,-385,473,-405,-684,642,269,-541,-923,935,622,-962,1000,732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00448() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{-463,776,183,-47,503,368,-225,-787,207,-195,1000,-804,784,10,313,-811,432,269,-386,-738,260,427,814,198,-732,1000,588,208,-1000,-350,910,401,1000,446,41,-151,-259,229,-338,-955,393,-118,450,746,-571,-306,-322,-673,-942,-28,728,-81,-602,-436,-209,431,-373,-108,1000,-259,-162,-970,1000,328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00449() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{601,574,1000,1000,819,-332,-548,229,1000,-498,270,1000,-716,-165,-726,-215,-830,-149,-1000,-265,-677,-265,-186,-129,1000,-1000,-42,-705,-349,-1000,1000,-627,-207,-558,322,998,741,-133,728,1000,-1000,57,484,66,877,-365,301,1000,462,785,-742,492,-74,132,154,-1000,-346,207,-968,-326,1000,-474,1000,-855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00450() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{286,-807,-537,-1000,149,711,-253,-72,-40,-429,1000,-1000,1000,136,1000,289,1000,-118,260,774,1000,943,696,156,-1000,1000,-820,-1000,-507,841,-367,118,73,1000,916,-605,-653,217,-89,190,1000,-1000,-22,-1000,-918,977,-952,-1000,-920,434,106,-792,-451,869,295,262,737,-326,991,-762,-330,-58,-743,966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00451() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-538,416,-1000,224,841,-159,-289,-320,160,-786,814,-863,-351,122,1000,157,-98,-515,938,738,533,463,-69,-185,-137,-1000,-245,592,-496,-890,-279,-1000,-590,-348,-1000,-1000,-273,-414,1000,-574,1000,346,298,-1000,-1000,409,-1000,-650,-1000,779,-549,174,106,1000,290,961,794,523,-1000,-503,652,443,-104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00452() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-1000,84,-451,1000,-366,-977,-116,1000,-624,1000,-1000,1000,307,1000,-382,667,-98,-1000,1000,1000,-76,1000,1000,-531,904,-827,-1000,-1000,1000,1000,844,1000,1000,1000,-1000,239,914,526,349,1000,-1000,-307,-1000,-937,951,-1000,-1000,-1000,1000,-194,-943,-688,262,-482,-1000,990,-1000,896,-889,874,406,-868,609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00453() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{-576,425,-160,-911,-446,296,-815,600,-951,41,741,180,-372,395,427,-268,-91,-622,712,-651,918,145,-977,-707,-990,-542,-573,-629,756,579,-753,-946,-868,129,-510,-15,489,-70,-527,-250,-797,192,-120,-298,-357,-467,645,470,-829,261,502,356,-767,132,-187,262,297,327,-588,-10,386,-178,-459,830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00454() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{711,-906,169,492,-1000,711,-928,-1000,804,-195,934,-478,592,-77,850,641,-1000,31,-608,1000,-158,1000,842,-152,1000,177,-450,597,-375,154,1000,1000,-804,204,1000,-328,631,1000,-1000,-1000,1000,-480,400,63,-743,282,-1000,-316,835,-52,518,-1000,1000,202,-20,442,255,-1000,420,987,-330,473,-102,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00455() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{-245,1000,174,-936,33,971,-401,-960,187,626,127,219,1000,-553,1000,-354,1000,-346,34,-1000,1000,1000,348,516,-1000,1000,1000,-19,-1000,-469,987,540,927,-581,-616,-840,-259,544,-114,-955,-143,752,-497,4,-430,-509,170,-682,-1000,-1000,1000,998,-1000,-649,-564,1000,-1000,-108,1000,-1000,-405,-658,153,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00456() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{1000,841,-153,-586,-811,1000,-120,-289,-906,1000,539,703,-1000,101,-334,-150,-206,163,516,956,318,1000,230,-1000,-1000,271,-833,-109,1000,-714,-1000,1000,-275,-1000,-20,49,-1000,1000,-359,-200,-70,885,-284,133,-1000,-824,-3,-23,1000,-719,1000,-1000,1000,448,836,828,1000,474,-163,-104,-1000,504,709,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00457() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{-178,943,721,-807,279,784,112,547,-7,-1000,1000,-831,584,702,641,-1000,1000,-61,-237,1000,880,-735,369,1000,-1000,-565,-1000,-577,543,-269,1000,-1000,-1000,888,-135,-526,-1000,-268,-144,365,-321,-33,-916,-1000,702,-710,-48,-930,-970,139,389,255,-1000,1000,-164,146,1000,-318,1000,-1000,-444,-1000,510,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00458() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{-51,-630,-381,302,-306,-83,-280,-628,194,-866,-388,-1000,430,877,1000,1000,675,-50,333,1000,-508,-76,-530,907,-1000,904,-1000,-1000,-37,78,-425,363,476,1000,1000,-1000,-164,185,-1000,406,329,-399,-108,400,-1000,-228,-1000,400,-882,-157,604,-10,-1000,262,114,-494,221,261,1000,-754,-1000,406,205,609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00459() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{-316,717,-891,1000,-201,-1000,-820,-1000,-423,-144,29,-424,-414,-305,565,-502,643,-596,1000,1000,-1000,-1000,-1000,-873,-890,933,-943,-1000,1000,-1000,-351,1000,1000,-9,1000,384,-1000,978,-1000,181,-475,-151,493,-776,-1000,-1000,-1000,1000,-429,-503,478,-1000,-1000,-243,9,-1000,1000,-3,611,127,-1000,-675,1000,407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00460() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{-438,-776,631,-604,654,524,534,899,468,638,-173,-189,521,-791,7,416,-655,299,470,-1000,316,781,529,22,822,928,685,209,163,-352,-853,14,-634,-542,-180,-818,-760,835,491,-628,-599,-106,805,-247,776,-536,746,-735,-891,-397,-182,-131,-605,-666,893,490,843,980,787,-363,944,-186,-2,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00461() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{267,-1000,422,270,-329,139,872,543,-972,-1000,549,-461,-243,1000,-1000,-1000,86,496,-29,668,-241,-812,330,167,-199,-943,-1000,811,625,-1000,-1000,-1000,-896,1000,-62,1000,-1000,-1000,-1000,1000,-235,-243,869,-727,-189,-710,-399,184,1000,1000,-567,231,1000,1000,-493,-663,1000,1000,-79,536,-653,-1000,53,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00462() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{558,-508,258,-246,249,678,749,464,194,-856,1000,-941,406,159,267,-1000,675,335,-68,-271,127,193,394,-55,-264,-424,-336,130,100,78,-386,-1000,-841,981,136,-1000,-103,-760,11,1000,179,-1000,-1000,-1000,545,809,647,-167,1000,1000,-1000,-68,1000,1000,363,-803,941,1000,-699,1000,51,-993,-353,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00463() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "minuteOfHour():org.joda.time.MutableDateTime$Property",
            new int[]{-436,-403,9,-663,-872,-107,-778,476,469,-759,859,1000,563,4,740,296,-360,-824,-667,-790,500,752,34,-1000,216,-691,249,1000,-1000,233,376,-1000,-583,650,13,-623,392,-473,943,-51,171,943,1000,164,834,-286,246,-320,-800,654,-611,-50,-577,106,-42,315,255,205,-254,-103,-866,-95,-162,-712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00464() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-474,514,366,973,204,500,-1000,-150,-766,887,1000,-1000,-1000,-950,-639,-169,1000,-428,245,411,-1000,-839,240,-739,142,1000,977,511,980,236,-218,-339,-576,1000,1000,-1000,281,-1000,-880,-419,1000,1000,930,-267,1000,1000,-1000,-1000,-871,2,908,-336,-1000,1000,533,-276,-1000,411,291,1000,734,670,113,-673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00465() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{19,330,201,66,-81,-905,958,-260,-1000,213,262,239,-671,1000,583,-1000,413,444,-537,722,76,669,62,1000,277,-327,415,-936,-404,301,451,147,438,-753,-380,351,-358,117,392,272,-915,-1000,292,1000,812,-686,440,-964,104,110,650,-753,221,-267,-489,539,-1000,1000,-1000,-520,1000,45,-942,-163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00466() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-309,21,-662,477,-1000,-898,479,410,410,-587,1000,357,-118,460,285,-1000,-1000,1000,-229,-272,-255,1000,-32,111,-181,269,-124,-1000,-977,-276,1000,161,1000,-875,-1000,1000,481,-1000,987,-96,-363,-1000,-737,-320,-778,-328,-442,461,99,440,1000,-998,1000,-1000,63,-1000,77,129,-714,-21,-1000,-451,-780,244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00467() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-1000,-410,-1000,-209,-986,845,-1000,1000,467,494,1000,1000,1000,-461,-17,-1000,1000,472,-1000,484,1000,1000,1000,-666,1000,572,-1000,-759,752,1000,-1000,1000,-768,-1000,1000,1000,-1000,1000,-545,-1000,-806,1000,-1000,-1000,-1000,1000,1000,-53,242,276,-1000,1000,-1000,1000,1000,1000,580,-1000,-1000,-536,388,-510,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00468() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-921,567,-161,-866,-665,-830,-849,-154,-208,-173,-935,400,113,18,-76,-1000,301,43,-132,-406,-659,900,-468,1000,329,-1000,818,413,-1000,646,119,140,162,-1000,-1000,976,103,1000,715,-7,-400,1000,519,-97,-588,252,-1,400,608,781,714,-65,1000,-1000,-46,1000,-533,1000,1000,-327,-459,-546,-142,-955}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00469() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{286,700,-1,-454,-457,503,-508,188,-381,601,-1000,-1000,-1000,54,176,-1000,858,784,108,43,-913,856,-178,-1000,-259,-1000,-74,456,388,-262,429,71,917,605,-367,976,51,795,722,-992,910,-1000,-775,0,948,579,-1000,-633,-61,276,223,-188,-995,1000,-391,181,-1000,248,62,351,-965,-218,637,-836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00470() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{138,-817,311,76,463,221,-138,9,-114,647,911,-789,-29,-250,396,433,-293,905,-442,-514,-374,40,-196,-558,-191,210,544,241,763,308,637,439,-388,104,-942,-293,-989,-42,917,-258,793,-122,73,-761,453,876,-769,-274,289,3,654,-780,257,876,-647,-103,419,36,45,274,-773,65,985,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00471() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-267,292,-239,664,-830,-827,352,499,-1000,280,-36,910,15,-941,579,-929,1000,-33,-404,789,-208,1000,92,468,494,-1000,96,554,-925,167,651,553,-595,-629,-410,357,-62,213,1000,531,-857,-876,-434,-426,498,-401,1000,-645,1000,-120,-278,-285,-865,738,-982,-268,-683,1000,748,-1000,-569,-777,-910,290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00472() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{715,387,-656,872,-731,-365,357,157,38,407,-175,485,-838,873,1000,-747,108,1000,-661,704,-507,453,876,-225,277,-1000,-136,542,-552,924,-51,-91,438,-172,-786,976,-445,-728,392,42,-222,-1000,-1000,-1000,443,-264,-442,-38,577,665,818,-186,-652,-320,-93,105,431,310,-322,757,-1000,-326,119,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00473() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{320,608,-804,108,-1000,-840,-1000,-342,-630,-334,14,-462,-992,496,-310,-122,555,1000,744,-327,-657,793,-280,955,-622,-645,622,172,202,-587,506,211,1000,556,-1000,289,555,604,1000,-748,586,-1000,-848,-1000,42,186,-1000,17,-364,169,1000,-478,-760,24,-48,249,-907,341,62,-602,-1000,-810,1000,-307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00474() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-361,-104,-151,728,-94,-360,495,-878,87,467,1000,1000,590,-503,681,-415,840,-1000,1000,1000,-513,-981,1000,1000,216,1000,960,327,-598,-161,-486,-350,-971,-768,1000,-1000,-1000,-31,-417,1000,-1000,950,1000,-1000,-1000,-35,400,1000,847,-311,1000,1000,1000,-957,336,1000,-525,1000,-625,30,1000,677,-1000,-882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00475() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-361,-333,-412,578,-94,134,485,580,361,-590,1000,-283,749,781,-1000,-435,-1000,740,1000,-1000,-724,1000,508,-707,216,1000,335,616,-1000,662,583,786,-1000,-1000,-971,229,568,-48,-993,360,-55,486,1000,635,-1000,612,348,1000,847,-604,1000,244,1000,-957,28,-973,-525,704,-103,253,-1000,-415,-230,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00476() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{320,1000,-273,354,-1000,32,-398,619,-1000,783,-678,-465,-1000,162,306,-834,1000,351,-147,577,-1000,829,-251,-1000,392,-1000,197,422,-361,-488,199,495,643,183,-367,-19,-322,795,581,-312,688,-1000,-868,140,1000,687,-1000,-966,280,466,585,159,-995,1000,-632,-211,-869,425,-525,530,-1000,-558,334,-779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00477() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-1000,861,218,1000,962,495,1000,667,283,159,791,1000,-10,637,-1000,553,-1000,1000,450,-547,-462,1000,1000,1000,1000,185,-871,-1000,-74,-570,-819,-789,-1000,888,289,-356,-946,-1000,64,-1000,897,1000,-1000,702,-289,1000,-305,744,94,-537,-90,1000,-282,204,309,926,363,-897,140,1000,372,-1000,95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00478() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{-822,-348,444,3,417,647,1000,690,479,196,385,-929,498,-138,1000,-413,-142,862,-170,-17,-651,981,875,-106,735,1000,-375,116,-777,55,594,545,-240,-1000,-756,493,812,-418,-242,529,-81,-871,832,1000,-435,600,509,87,536,1000,725,-174,404,-316,-320,-331,-819,511,-966,-1,-620,68,-822,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00479() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "monthOfYear():org.joda.time.MutableDateTime$Property",
            new int[]{690,1000,-891,659,-1000,-419,937,491,773,984,248,-154,-1000,-216,1000,-1000,1000,-267,-129,1000,-830,762,236,-1000,611,-1000,487,-277,-328,-980,109,610,-168,442,-163,-720,-1000,1000,1000,-546,357,-791,-697,-1000,1000,144,-1000,-1000,197,284,-278,1000,-1000,1000,-895,-216,-1000,548,-1000,431,35,-503,-167,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00480() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "now():org.joda.time.MutableDateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00481() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "now(org.joda.time.Chronology):org.joda.time.MutableDateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00482() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "now(org.joda.time.DateTimeZone):org.joda.time.MutableDateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00483() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "parse(java.lang.String):org.joda.time.MutableDateTime",
            new int[]{323,-620,-719,-584,-933,-258,779,919,835,648,798,-765,-297,33,-679,879,-660,351,-422,-666,681,551,-101,-804,844,-420,-973,-185,714,423,-689,649,-697,-117,-449,-370,-484,-146,-574,286,273,651,426,-881,64,134,-19,-234,973,-187,107,877,-44,903,-712,294,-259,-621,548,-880,-911,-516,-291,-415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00484() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-513,-168,384,-975,-537,-555,-48,-169,-164,88,-170,79,742,291,-744,185,477,152,-746,1000,287,410,-285,-490,-422,-340,-720,581,831,183,780,-6,-464,-65,825,-649,-1000,-257,438,-596,-540,184,-412,463,199,790,7,-140,291,-410,617,974,-696,-287,95,-351,235,-906,687,-361,-577,-26,-318,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00485() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-203,483,-651,-472,-555,-93,-422,-164,389,-481,471,722,260,-445,388,666,-5,-672,1000,-24,685,173,-490,-1000,-768,959,796,646,314,394,-6,-464,-65,825,-786,-352,-257,1000,-676,-540,238,-358,183,68,156,371,-112,-43,-308,629,974,-530,-469,116,-351,556,-500,202,-361,701,-480,-330,-4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00486() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-267,-1000,-9,-180,734,689,-166,680,432,86,337,-796,-1000,426,-568,-171,-1000,570,327,-1000,-1000,-1000,-774,-1000,595,-170,763,-698,-1000,-870,-858,943,867,-5,-1000,1000,-352,-1000,-41,912,678,-285,558,-418,-567,-648,-509,819,-374,825,-1000,1000,-584,-423,-1000,545,-270,1000,-1000,-730,749,-495,89,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00487() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-13,-749,-951,-27,-187,-842,-583,439,-699,-1000,1000,-1000,-397,317,552,-217,62,-983,-282,601,-288,-966,1000,632,-574,-99,-400,1000,-860,783,-1000,310,1000,1000,303,1000,-605,938,-1,261,-400,621,400,-674,456,268,-1000,0,-1000,-298,163,44,856,-132,-1000,-400,-896,425,-702,417,196,-343,-1000,-50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00488() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-306,-21,-749,1000,-1000,1000,1000,-257,-119,1000,-1000,577,372,-1000,247,-1000,735,-1000,-467,16,-904,-1000,-676,-1000,514,-1000,-471,-1000,-721,-387,15,1000,1000,208,1000,-1000,-928,1000,80,-1000,-624,-37,1000,448,286,-1000,237,-182,-1000,2,1000,-840,1,-583,-1000,1000,-565,-454,-277,-508,414,-264,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00489() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-610,-44,558,-731,-18,509,17,198,70,-887,-802,-886,-95,556,-715,-799,-1000,-530,395,-1000,-1000,838,976,-131,1000,-74,318,-541,228,-1000,806,1000,183,1000,943,788,1000,-594,884,1000,-176,528,17,752,658,-765,410,247,1000,-334,389,-1000,-91,-1000,-188,-1000,716,-774,948,1000,-381,-182,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00490() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-530,-203,493,439,-101,0,-237,8,-425,863,577,-166,-632,1000,-391,894,-102,-728,-1000,-1000,300,90,-771,727,-307,42,-1000,-620,-1000,-105,-1000,77,507,391,1000,-147,-396,113,1000,-533,-769,142,-1000,616,203,175,59,438,-550,726,1000,1000,-1000,896,36,132,1000,-307,-290,-602,-548,-555,348,-631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00491() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-198,-520,1000,215,-407,689,-767,-790,812,1000,-1000,1000,-893,39,1000,-839,1000,641,1000,17,-521,1000,1000,-1000,1000,853,1000,-476,1000,546,-198,1000,643,-1000,-1000,1000,788,788,-1000,-1000,1000,1000,-1000,428,-1000,-1000,1000,-1000,933,1000,421,450,-1000,-1000,1000,1000,1000,298,154,-1000,1000,-1000,416,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00492() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{665,-875,-200,182,-936,558,-1000,-569,670,-349,477,-411,-524,149,1000,-197,1000,-1000,467,360,-700,-245,1000,405,-44,-1000,1000,788,132,1000,-115,1000,100,25,-669,67,784,938,-1000,17,1000,715,281,-1000,185,-63,-202,-743,-401,1000,19,382,1000,-650,-1000,1000,-1000,626,-847,148,1000,-587,-709,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00493() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-95,-643,-432,-592,192,390,-161,876,336,-274,-203,-837,-1000,81,-682,-443,-772,-689,-184,-964,-496,-1000,-40,-714,206,-121,655,-1000,295,-1000,1000,901,-600,221,-760,554,-640,-1000,350,1000,1000,-85,54,59,219,554,-630,-69,-441,584,-857,1000,-206,-844,-1000,251,-337,795,-473,-424,-254,58,-223,-892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00494() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-395,-368,879,-798,-910,-128,-565,-880,-52,442,-546,525,742,494,-539,345,588,747,-264,-363,-65,802,-449,-691,134,-370,-507,142,225,718,52,14,-744,-745,657,-532,-938,-257,-111,-989,-540,439,-243,-134,-473,196,585,297,807,-614,322,73,-890,-133,912,174,888,-762,704,-617,92,-16,-126,106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00495() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{375,-820,801,921,1000,-346,224,285,497,933,-429,-158,364,243,607,433,-543,149,879,-172,-482,-316,315,-627,-339,473,-194,-165,-572,-721,-342,850,997,-667,-188,-453,635,793,-700,-766,-382,-273,-478,324,-531,-876,613,-918,-399,618,445,-745,-764,-166,-401,-887,-195,-426,-970,63,957,-575,86,605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00496() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-840,69,-975,-537,-400,501,505,-158,-127,227,-856,104,284,-1000,231,-1000,1000,-80,-506,-464,-847,-1000,-490,-355,-1000,633,-582,-1000,183,-719,239,-211,-30,825,812,-1000,-257,550,421,-400,180,301,606,-1000,-159,-751,948,529,-1000,-1000,1000,-1000,76,-664,-351,1000,-96,-107,-1000,-92,686,567,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00497() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-484,-592,-222,-1000,-536,-1000,541,1000,-1000,-280,593,-725,16,247,-1000,-171,-1000,40,-700,-813,19,-1000,-1000,-282,-1000,408,-1000,-532,51,-1000,259,-128,-172,1000,1000,1000,-1000,-1000,1000,1000,354,-1000,160,807,1000,835,-1000,1000,-463,-1000,205,1000,-278,-283,-1000,-896,1000,-228,-177,206,-1000,835,-535,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00498() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{-995,0,74,-317,-873,-1000,1000,120,-1000,-1000,558,-972,1000,-228,-524,-171,-469,-494,-1000,1000,524,-522,14,-334,-1000,1000,-1000,691,-145,426,-433,150,145,1000,760,1000,-812,1000,1000,74,-1000,-243,-308,1000,1000,1000,-1000,-305,697,-1000,-732,962,-587,333,-413,-1000,-1000,-1000,1000,-170,-669,858,76,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00499() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "property(org.joda.time.DateTimeFieldType):org.joda.time.MutableDateTime$Property",
            new int[]{1000,-774,-818,284,459,247,-1000,34,-248,-763,637,-630,-1000,241,1000,-177,296,-1000,615,-564,-715,-746,1000,907,826,-261,-371,53,-612,247,-576,1000,625,345,-587,850,330,860,-1000,782,-152,1000,213,-1000,-219,-52,-560,-882,-281,1000,-330,-318,1000,-662,-827,545,172,1000,-678,-30,405,-355,-1000,653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00500() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-748,115,-157,237,-77,758,792,-195,1000,-1000,819,999,144,757,-743,90,683,-410,-575,-218,816,-455,-574,-877,649,660,-590,760,-571,1000,998,1000,-17,-1000,-1000,1000,344,-365,-312,45,31,-1000,531,-448,311,936,580,1000,185,141,312,1000,-400,-137,-511,40,466,909,-164,-118,400,-257,-1000,-123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00501() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-760,-1000,18,1000,1000,678,985,536,1000,-1000,592,292,-1000,263,263,815,439,366,-1000,-962,798,-823,-1000,1000,215,-1000,-969,1000,-455,1000,475,1000,-83,-1000,-815,1000,155,-178,-1000,0,-1000,-1000,-620,-384,724,-397,-487,1000,1000,-1000,-763,1000,-841,228,-644,1000,1000,954,-165,-1000,1000,-132,-1000,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00502() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-422,-320,329,-576,-155,-121,597,-128,-172,758,75,-546,-756,983,-1000,119,165,-412,-551,-887,789,167,-785,-596,933,-301,-900,2,518,17,260,-203,-558,-402,29,-412,-522,203,562,-187,-767,-247,786,-754,447,-963,665,20,955,753,141,451,-684,369,-27,-90,-409,923,52,-335,-739,-835,237,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00503() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,1000,-945,-576,266,394,791,-715,-417,574,-1000,-536,319,-1000,510,-488,-1000,-846,400,435,-44,-114,-68,787,-713,808,34,480,397,1000,-476,-72,-368,-198,-1000,524,1000,-987,-1000,-497,167,-368,-552,275,-1000,-615,-305,916,-369,281,-158,-1000,268,790,-669,690,-445,-131,1000,286,38,663,-410,-704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00504() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-802,695,1000,1000,-41,933,971,-544,-366,834,-937,-1000,144,809,-460,283,551,396,-572,-595,1000,-302,91,-758,1000,-1000,-1000,-87,586,-1000,297,-849,-1000,-1000,-1000,-527,-1000,-321,943,-594,-1000,-473,245,-1000,807,-1000,601,-298,183,434,363,1000,-1000,1000,-962,1000,-449,955,-379,-424,47,-1000,-141,-560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00505() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-681,-126,840,1000,1000,464,263,687,-44,-795,-491,-849,133,798,641,197,45,-233,-686,-215,966,-332,-27,-769,-154,-1000,718,-551,28,643,-1000,-982,-350,81,-344,-130,-690,-425,-650,-903,349,556,1000,1000,-750,-989,1000,311,-531,-1000,1000,-1000,184,-788,746,1000,314,-61,-97,1000,-358,-901,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00506() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-838,-720,172,1000,174,841,-1000,526,-1000,-952,-1000,-817,-30,1000,856,47,-645,-491,-1000,369,1000,-1000,837,-767,-1000,-1000,1000,-1000,-176,621,310,-1000,-1000,-623,1000,155,-124,-170,-1000,-1000,-659,261,-205,1000,-1000,-363,1000,1000,296,-463,1000,-1000,1000,-1000,-449,947,811,452,-704,1000,-833,411,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00507() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-29,738,769,18,809,-948,-523,177,312,405,-632,51,-540,332,399,554,423,-776,-957,304,-594,236,538,-721,-585,-954,369,471,-143,189,-222,-241,-590,-940,-603,-401,160,-417,-558,-943,688,-990,357,-762,-563,-654,260,-801,-491,-493,-890,-916,-156,-551,101,393,-380,-748,501,65,705,419,846,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00508() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,1000,-477,-291,242,-203,-147,31,271,721,-722,-188,-855,-1000,-319,208,-1000,-220,379,70,215,-307,-551,-262,690,-1000,-938,757,-286,40,-686,79,-609,91,-1000,467,-98,-563,-884,-792,167,-368,583,152,60,-1000,538,634,559,428,-387,139,-276,976,135,379,978,511,708,-261,577,-120,-710,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00509() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-738,-1000,684,1000,802,558,1000,361,240,-892,641,-276,144,1000,-80,321,1000,621,-1000,-1000,1000,-527,-164,653,432,-876,-661,215,19,272,1000,245,-302,-1000,-751,133,-476,-914,319,27,-1000,-1000,-329,-1000,1000,-146,-446,124,1000,-996,-354,1000,-1000,-110,-1000,1000,1,613,-1000,-512,252,-748,-186,-898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00510() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{338,-30,-423,-222,-51,176,472,-667,-345,791,-575,-564,-169,-175,879,-133,-374,-270,-1000,389,-28,-963,-352,1000,211,-863,-291,-4,530,-179,-40,784,147,-450,-64,914,1000,-116,-905,-275,-766,-965,-1000,-561,-779,-223,-1000,-1000,-710,-103,561,452,55,944,-410,1000,-1000,170,977,364,989,1000,-728,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00511() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{201,76,-1000,-892,986,620,901,576,1000,265,294,825,-482,-831,-268,546,-323,146,-575,-1000,509,-884,-772,297,183,346,-361,918,-764,782,5,806,540,-345,-422,1000,130,429,53,-1000,-1000,-1000,-273,69,1000,136,-375,287,722,-873,623,1000,-1000,1000,-355,1000,985,682,-816,-349,-1000,-236,-999,991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00512() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{279,-200,-1000,408,434,-277,364,135,-424,-444,-489,397,798,75,327,-307,-216,-341,-61,-335,-297,-431,10,-596,-1000,26,612,464,-57,-356,157,-88,823,-114,-595,1000,545,587,-1000,-669,-461,-1000,-758,693,918,31,-316,81,-559,-910,-841,632,-345,-331,269,1000,969,-563,-210,736,299,334,-194,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00513() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{1000,695,475,1000,-41,-290,688,850,-688,704,83,745,174,1000,-1000,-1000,921,301,-530,-573,331,81,1000,-758,122,415,73,-641,578,-689,1000,-1000,65,-607,-551,-63,-872,615,1000,-706,-1000,-473,909,82,1000,413,288,-800,41,37,-392,375,-1000,-720,37,715,71,-892,-1000,669,-1000,-672,827,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00514() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-320,565,-576,-155,200,389,-258,276,891,-151,-1000,-1000,1000,-1000,719,322,-254,-572,-970,1000,-84,-1000,-262,1000,-1000,-938,757,331,17,-113,-52,-1000,-520,224,-547,-1000,-297,599,-481,-767,-368,1000,152,447,-1000,996,340,1000,1000,454,990,-868,1000,-332,158,-409,511,309,-882,-200,-120,-357,-863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00515() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfDay():org.joda.time.MutableDateTime$Property",
            new int[]{-370,944,36,92,-397,-188,363,-657,906,928,-814,-969,-698,983,-754,261,-204,-404,-233,-800,586,-158,-881,-628,933,-591,-804,-34,301,-928,-274,-681,-370,-493,29,93,-791,370,141,-366,-781,-944,581,-518,767,574,591,68,955,554,178,278,-602,608,-80,716,-532,923,178,304,-488,-695,71,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00516() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{-128,990,-107,-11,750,235,-479,566,571,716,820,232,-648,-597,-452,-126,-602,-982,656,-42,-393,-353,-560,-649,393,-485,387,780,-163,-101,558,-121,430,-441,988,-673,864,-713,-385,-9,745,-766,535,-823,-208,519,-991,89,-883,278,-438,438,537,-184,-200,-240,491,793,-934,468,120,484,988,897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00517() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{664,711,-21,628,119,694,-1000,185,645,1000,-1000,-149,-667,343,757,-1000,-1000,1000,-693,-20,922,-1000,351,788,433,-566,-659,1000,48,-849,1000,-603,-1000,261,114,-280,1000,836,-323,676,-582,-6,-262,532,23,182,-677,813,64,434,-1000,857,-249,-632,1000,-1000,-440,-533,-743,-81,-595,157,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00518() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{-344,174,654,2,-782,27,-269,-116,-507,1000,443,422,244,197,-1000,689,-135,-159,1000,-998,211,355,345,399,-277,90,336,195,289,-352,451,898,-265,503,169,-50,260,-366,56,-390,-199,-735,-260,-250,973,-717,149,749,1,-20,-384,753,-338,435,281,958,-205,-363,52,1000,-688,-895,1000,-329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00519() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{-134,98,764,1000,-727,-76,1000,103,-1000,-343,-1000,-695,970,-580,1000,-1000,-85,948,481,1000,-1000,-1000,-1000,-128,654,277,-180,-910,-56,-378,-386,1000,1000,1000,1000,1000,-1000,1000,-1000,-1000,1000,484,-1000,-188,630,-1000,1000,-1000,1000,1000,595,205,-546,-1000,-1000,1000,1000,-742,827,-1000,-1000,942,-870,-690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00520() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{1000,339,-21,632,344,-450,-1000,185,1000,581,-1000,-127,392,-385,757,-1000,-1000,1000,-1000,539,1000,-1000,-89,-327,-1000,1000,724,1000,-416,1000,-943,712,-98,1000,114,-262,1000,1000,-1000,1000,-328,582,-1000,894,-549,-1000,196,1000,275,1000,-645,-929,-778,-632,1000,-1000,-568,-1000,-769,-581,924,209,-829,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00521() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{-171,533,-312,6,399,314,-113,369,169,682,274,-407,-324,-282,-400,-154,-535,-629,469,-168,1000,-193,-94,-242,575,-432,-731,674,191,-370,482,-184,-868,798,615,75,521,-400,-862,-181,192,-511,210,-440,155,-169,189,-56,-411,175,-230,467,580,-73,-173,-344,42,495,-580,158,-216,621,741,328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00522() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{684,358,-672,2,-871,598,-937,-641,26,-1000,-486,80,-595,-818,-648,478,-666,-423,290,-1000,761,-171,438,-205,-1000,1000,1000,741,-4,438,-138,156,-1000,69,158,1000,1000,401,142,993,32,-1000,-1000,364,-492,-272,-876,154,1000,86,-944,688,-958,-812,748,-607,1000,-196,-803,-168,595,142,-80,-106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00523() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-940,-468,48,370,831,799,-472,-557,-330,451,-1000,200,1000,1000,565,540,-543,-912,106,-323,234,557,409,548,-921,-219,-201,-433,45,613,-667,-1,-657,809,-1000,-276,-947,-1000,-416,18,-249,1000,-552,446,57,-223,510,1000,1000,60,431,383,770,284,-1000,-365,615,501,164,-771,982,411,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00524() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-86,-573,-386,-1000,1000,1000,-35,-1000,215,-897,-1000,313,996,-73,621,1000,-150,-322,-1000,-952,-108,317,317,-9,-1000,903,-498,-146,-1000,660,-330,315,-711,1000,-129,-272,265,400,-1000,519,-830,116,-228,957,1000,-114,422,938,889,754,896,675,1000,-1000,6,-881,-52,733,-103,-432,135,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00525() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{958,1000,372,863,47,583,-1000,1000,677,722,-684,455,-23,-1000,-163,-1000,-1000,1000,-941,794,1000,-1000,105,-947,-1000,1000,-709,1000,776,438,826,-1000,-1000,800,218,-585,1000,1000,-1000,1000,-1000,-126,-449,1000,-1000,894,-1000,1000,692,1000,-1000,301,40,-1000,1000,-1000,1000,-96,-1000,-1000,1000,-256,-802,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00526() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{238,-690,-423,747,308,-122,862,630,-227,1000,-1000,775,536,308,457,-1000,59,694,604,945,555,-464,-954,652,593,639,-77,-669,-323,-295,-1000,34,1000,311,1000,1000,-1000,1000,-1000,907,878,474,-1000,91,232,-376,1000,-1000,-197,-546,1000,-96,113,-546,-1000,988,217,-1000,727,-1000,1000,511,-1000,256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00527() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-1000,-120,136,-132,87,518,-352,-312,888,-656,-1000,1000,1000,920,575,1000,-630,1000,208,-1000,834,-1000,1000,1000,-701,-326,-387,-978,-1000,-1000,469,1000,-341,1000,1000,-1000,-1000,1000,-994,545,-764,-161,-1000,874,-1000,1000,-1000,-226,-1000,1000,157,340,138,-1000,1000,-698,-318,1000,-1000,-1000,1000,301,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00528() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{-567,-375,-96,578,-1000,45,1000,-427,-768,-285,-1000,-1000,1000,531,1000,-1,461,399,626,728,-824,-53,-747,810,952,-365,-435,-648,-308,-818,-378,-76,754,679,692,1000,-1000,1000,-82,-120,1000,-131,-550,-221,1000,-1000,1000,-606,1000,106,767,284,197,-538,-1000,25,-43,-838,1000,-1000,-977,948,-281,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00529() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{-394,-171,-86,1000,504,113,958,301,-893,-850,-507,594,179,-292,793,-835,-220,390,439,690,-825,755,-304,-381,758,-106,74,-646,-192,-461,861,-142,1000,-27,1000,-616,-781,320,-400,-1000,275,791,-14,-495,188,142,-223,-653,1000,1000,-223,297,-217,-343,141,1000,1000,499,380,298,-1000,-382,-150,-745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00530() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{7,391,278,-598,-476,73,-503,-136,-61,-1000,368,-1000,448,1000,366,1000,1000,-286,-666,-598,416,336,639,992,-400,-110,370,651,-442,-741,-59,520,-854,-408,610,-96,1000,400,-400,1000,-226,-806,-663,-126,1000,-682,-410,-126,-259,651,-311,334,-86,239,823,-1000,-239,-211,-46,-122,77,1000,586,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00531() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "secondOfMinute():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-114,278,185,-1000,166,1000,-496,-1000,-1000,368,-582,887,1000,366,1000,1000,-272,1000,-430,-1000,1000,-200,992,931,-1000,-428,-1000,-215,-741,27,523,78,388,548,72,-964,-550,926,-1000,1000,-782,1000,-879,1000,-975,1000,-126,-665,-1000,458,319,-143,-36,-1000,1000,-1000,-296,1000,-11,-415,423,362,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00532() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-1000,-497,626,788,-976,961,-200,-587,940,456,502,723,400,-242,-215,-292,-1000,685,-139,531,330,-814,-1000,86,-555,-954,1000,-835,-17,218,160,319,448,709,520,-212,378,-1000,-394,189,-411,-1000,-837,699,-965,-462,-898,-49,71,-981,580,-697,-581,972,-748,65,-5,382,-500,614,-1000,-645,1000,-315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00533() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-858,-1000,1000,1000,-773,313,-542,-976,50,660,1000,-681,1000,63,-982,372,-881,293,315,507,853,208,-1000,-1000,-1000,90,1000,908,337,-503,-594,135,735,450,857,297,-66,-220,-1000,496,-1000,-664,-329,1000,215,-476,-94,-22,-733,-46,1000,-115,13,-62,-1000,209,-628,-117,-807,537,-307,400,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00534() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-1000,-124,823,1000,-916,385,-536,-1000,296,943,1000,-924,1000,1000,-736,-157,-917,339,874,1000,653,-154,83,-1000,-685,132,1000,-390,-460,-896,-1000,-69,1000,615,1000,-531,-279,-454,-1000,-320,-329,-750,-540,1000,-781,-777,-366,-424,348,-442,991,-230,1000,475,-1000,305,-136,-94,17,290,-358,-215,1000,-312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00535() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{525,-560,784,-225,-46,1000,1000,-36,-159,-1000,-288,587,-1000,-1000,-286,-1000,-1000,1000,-1000,-616,222,8,563,692,-486,241,-888,466,1000,1000,1000,-201,-1000,514,-62,1000,868,-1000,320,818,-394,-1000,-715,708,-233,-718,-884,-251,-1000,-114,805,-857,-400,1000,-571,-220,1000,1000,-1000,1000,-1000,573,-154,639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00536() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-782,584,-152,521,214,-137,151,-439,355,493,631,773,-200,-177,517,-316,-1000,653,-211,513,481,-558,308,684,10,76,-120,-957,-560,545,720,-184,-124,80,936,-1000,-392,-770,149,290,585,22,-433,-686,-348,-553,-804,-824,96,254,197,-646,-721,1000,-449,476,504,363,21,777,-1000,207,-35,-750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00537() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-888,894,-572,-109,127,1000,285,-630,760,-275,535,1000,-1000,-562,564,-668,-20,762,-526,-238,-1000,-1000,549,-452,-387,-811,1000,515,-73,547,846,-548,-120,584,618,-341,-183,-932,197,92,1000,912,-1000,-385,431,629,-950,-21,158,-444,429,-374,-746,609,1000,684,1000,694,693,844,-1000,-721,-1000,-821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00538() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-412,-599,665,38,-365,932,442,136,42,122,-81,-608,-141,-555,-977,-598,-1000,1000,-254,-484,368,35,-584,754,-536,-403,-151,626,552,106,624,369,-155,1000,60,1000,496,-473,-1000,561,-1000,6,474,605,-117,62,340,302,-807,-726,109,-531,634,506,-434,108,-250,973,-119,932,-223,303,-231,-787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00539() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-756,1000,-798,243,280,353,262,-921,-82,493,122,1000,-373,-271,1000,-766,400,305,-211,297,-489,-1000,343,1000,331,-315,-199,-1000,-1000,635,738,-571,-124,405,459,-1000,-110,-903,371,-183,902,22,-546,-482,-96,393,-929,-337,1000,-430,-49,-235,-1000,944,512,-119,1000,363,21,213,-546,-1000,-652,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00540() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{176,142,200,503,829,510,1000,-496,-22,822,224,768,468,-390,969,-577,-1000,400,912,691,-727,-449,237,1000,-838,105,1000,-109,-599,1000,1000,99,220,-60,900,-604,-531,-205,420,1000,-77,665,534,-467,294,-275,-969,-556,-139,212,755,-1000,-661,-224,373,48,1000,-506,470,1000,363,-1000,-804,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00541() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-148,288,799,269,376,820,900,278,856,-114,919,1000,-1000,-801,272,-174,-1000,819,121,731,-225,-95,400,-670,-55,424,735,-200,-827,545,-48,1000,-222,1000,983,510,4,-668,400,297,28,400,25,-419,-501,551,-812,-292,-1000,462,503,-898,846,207,-455,1000,1000,400,-362,1000,159,183,851,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00542() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{661,-579,-15,-573,149,986,1000,-707,-956,-1000,-1000,1000,-1000,-1000,231,344,942,966,-1000,-1000,-1000,-370,-146,1000,-247,-349,-1000,831,1000,1000,1000,-612,-1000,864,-844,1000,1000,-525,637,714,-411,-1000,-728,1000,824,1000,-872,726,-588,-813,461,-190,-80,195,803,-423,1000,1000,-1000,1000,27,-721,-810,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00543() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-391,-490,117,702,-1,-775,74,-388,-921,-1000,609,-987,-226,-990,-74,153,-881,-109,-652,629,926,208,-1000,-998,-1000,575,728,777,102,-1000,-62,-689,-203,1000,-3,779,515,-140,-26,451,-1000,-1000,-897,820,215,-693,-263,-395,-461,643,1000,781,322,310,116,-10,892,-367,-1000,-621,-907,1000,442,-262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00544() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-394,-429,844,630,-582,165,162,-1000,1,-275,320,-97,574,-306,-324,-1000,-1000,342,370,430,466,-1000,-1000,101,-411,381,-230,-307,-427,484,370,-1000,-73,1000,191,-26,-255,-1000,-1000,-74,-162,-1000,-1000,476,-851,-699,-1000,287,333,-1000,206,-521,-572,1000,-1000,671,-95,800,-958,436,-930,-1000,1000,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00545() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-901,-106,1000,130,-397,95,348,-194,433,112,502,-668,-640,-845,-1000,-357,-917,505,491,655,984,420,-747,-877,-914,520,1000,719,997,-370,975,391,-256,907,900,193,-151,-765,-1000,-119,-33,94,-410,1000,36,-633,128,-300,-620,-719,1000,-409,253,257,-983,416,-432,1000,-652,676,-1000,825,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00546() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-1000,845,1000,919,453,-112,-114,-787,-22,547,437,-443,224,418,-552,-908,980,-228,247,404,146,-622,-859,-200,335,-34,1000,66,1000,-1000,-16,794,-338,701,-921,1000,686,-327,-693,-51,-506,-403,489,1000,-334,-1000,-444,192,-314,-262,1000,445,-17,87,373,139,230,167,284,-436,-1000,1000,-328,-143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00547() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "set(org.joda.time.DateTimeFieldType,int):void",
            new int[]{-223,84,-343,589,-608,689,449,-793,109,599,1000,299,-1000,-1000,1000,-1000,-570,894,77,494,210,-683,309,-102,-374,999,-524,317,-829,918,388,-1000,-170,703,857,-1000,-593,-1000,400,90,-380,-372,-1000,-39,-263,-451,-775,-478,-29,46,494,-659,-360,971,-534,-26,1000,777,-1000,839,-804,-30,939,-617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00548() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{1000,-1000,-702,598,672,-961,-36,169,692,89,-841,1000,171,-806,660,-599,1000,1000,-1000,250,1000,-1000,598,-1000,253,145,604,-203,-849,6,1000,-1000,453,-567,-1000,734,941,-438,-280,1000,297,1000,-549,-153,611,688,-680,-390,-1000,-920,-91,252,-971,-255,610,889,-1000,40,819,427,-549,108,-557,356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00549() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{926,121,1000,-6,785,-1000,-1000,624,-799,-830,-841,-361,-14,-711,1000,759,468,-362,-685,621,-601,-1000,459,-1000,-77,247,500,-598,-891,-818,-425,-717,695,1000,761,-592,-1000,-461,792,-738,297,1000,-549,-153,669,-372,229,1000,-1000,1000,38,-341,-541,-478,-6,-546,-189,1000,-848,-50,-549,254,-443,25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00550() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{-784,1000,390,-867,-1000,56,-946,922,284,691,1000,741,-981,1000,891,-1000,-1000,-1000,1000,-694,-378,478,796,1000,940,1000,-1000,1000,1000,-1000,-1000,1000,-1000,1000,1000,-316,292,-22,1000,-1000,27,-1000,-1000,1000,-55,970,747,1000,1000,-1000,287,1000,1000,414,-1000,-1000,746,-1000,-628,342,569,-529,-773,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00551() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{505,-668,-42,-628,723,-461,369,678,655,515,685,926,-1000,-547,817,-375,245,-731,-396,112,223,-1000,1000,-7,1000,655,-161,102,-40,-687,-9,1000,280,-330,-376,453,1000,52,-709,1000,-148,579,-679,1000,-365,1000,41,-268,-603,-1000,19,750,-613,169,765,842,-546,425,1000,1000,42,-916,-870,-161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00552() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{1000,-882,-461,422,-30,-607,41,774,415,-481,685,603,101,974,-1000,1000,-942,-731,-871,451,1000,-640,96,1000,-317,-1000,595,-854,14,-344,-396,-1000,962,1000,556,-1000,-1000,-686,-911,1000,-1000,1000,-561,-1000,-418,1000,666,-11,-1000,-580,-689,-660,-972,375,214,508,237,-896,530,96,-255,-829,-1000,-625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00553() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{-455,-112,-105,28,-447,554,-1000,38,-374,-35,1000,-1000,-356,974,-1000,-1000,-1000,-1000,1000,1000,1000,-415,-1000,1000,-1000,627,-294,1000,255,287,-1000,1000,-981,1000,327,-778,-1000,-895,987,-533,-437,-1000,-209,233,-1000,166,1000,-165,-557,863,-806,469,-1000,-569,440,-613,726,-1000,-1000,-1000,-950,-968,-324,519}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00554() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{260,908,-446,643,-1000,363,-759,-114,59,214,1000,-877,684,1000,-540,-1000,-732,-718,352,422,787,25,-656,216,-847,562,-250,705,415,84,-369,1000,-973,1000,80,35,-686,-786,1000,-1000,7,-1000,-497,-300,-240,215,74,-605,1000,1000,-194,598,216,-740,-1000,-1000,26,-1000,-1000,-929,-859,520,-197,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00555() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{1000,-77,-494,-381,909,265,-167,826,-135,-603,49,899,440,-742,-535,511,-1000,1000,-145,806,775,-715,889,1000,406,-1000,1000,-846,-115,-1000,-1000,-1000,379,-1000,671,-1000,-499,-356,-711,710,-1000,649,1000,-726,-685,869,-609,3,-586,276,355,857,-198,1000,124,25,-372,-1000,1000,115,-15,-1000,-185,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00556() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{429,471,782,605,323,1000,-95,-924,-1000,350,-75,-30,1000,400,630,-744,1000,-126,-98,677,-1000,966,-1000,-454,433,-1000,-487,1000,-726,159,649,1000,-707,1000,-332,763,6,-334,-871,-1000,-709,-239,617,-51,-905,-932,705,-647,639,1000,1000,-309,163,-1000,58,-556,1000,-182,-1000,1000,-779,194,43,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00557() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{593,-723,-702,-1000,844,-879,946,-131,751,-1000,398,323,-496,-1000,966,204,-404,513,-77,846,1000,437,-632,599,-332,883,269,506,881,694,-957,-810,838,-829,463,-1000,-219,-20,-252,857,-683,689,-82,-164,-259,-1000,1000,616,-1000,-195,-1000,-770,463,172,761,184,534,40,772,-179,67,-1000,684,787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00558() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{944,-97,-859,823,-33,-150,-238,-642,908,769,10,830,392,574,445,-142,887,1000,-945,234,-279,-455,437,-519,-166,566,-101,868,34,-279,844,109,-793,467,-934,1000,100,-909,-432,64,572,995,-1000,217,349,321,-408,-1000,258,-680,109,512,-886,-775,21,-109,-878,89,-438,-210,-1000,889,-1000,276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00559() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{758,-357,346,200,561,-972,-969,916,-557,-652,779,81,578,-958,68,144,-864,431,-672,213,-79,-775,77,-545,436,-936,647,-750,-775,205,-932,-728,723,617,164,-848,-46,162,256,258,-547,285,980,-755,549,700,-298,507,-253,925,-258,434,375,190,-778,466,-103,263,708,334,301,5,-961,-397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00560() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{1000,499,-1000,339,-1000,733,349,159,1000,804,-276,741,587,565,190,-903,1000,581,-338,405,0,478,85,163,-699,1000,-23,1000,123,-194,1000,1000,-1000,299,-1000,1000,292,-1000,551,1000,1000,830,-1000,1000,-55,177,528,-1000,382,-1000,287,736,-1000,-1000,703,-240,-693,-63,-1000,-567,-1000,1000,-506,712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00561() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{752,-798,-577,-863,1000,-111,1000,574,531,-306,-712,188,-555,-931,-131,325,1000,-64,299,1000,1000,465,-1000,69,-1000,878,512,-370,-1000,233,-66,-645,1000,-542,258,-1000,166,-286,-57,1000,-350,265,299,-758,-1000,-633,971,146,-434,45,-1000,-939,178,493,686,-156,250,473,607,-1000,-123,-883,396,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00562() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{206,580,1000,-97,346,-858,-1000,-544,-764,-608,1000,-1000,753,1000,1000,-854,539,-1000,-907,-125,-1000,-65,-303,-942,-177,1000,278,284,-1000,756,597,500,280,1000,-147,-18,-5,561,-854,-1000,-958,-1000,421,-546,588,-1000,-7,379,-570,1000,-549,-1000,97,-673,-698,-1000,836,545,-338,-1000,98,1000,-519,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00563() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setChronology(org.joda.time.Chronology):void",
            new int[]{156,729,-641,-691,-863,445,473,404,1000,-175,263,-28,-1000,687,-528,-202,-599,221,458,556,672,-761,931,1000,247,902,-511,562,1000,-1000,-873,121,-918,-365,506,-142,461,-628,-294,127,-254,-485,-1000,519,113,1000,758,58,465,377,189,332,-543,40,188,-425,-456,-705,-95,-43,-396,-1000,-646,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00564() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{-854,-754,-885,-765,824,-190,1000,-1000,330,73,-174,-305,662,-175,64,76,-256,1000,-503,-12,1000,281,407,564,-61,904,509,1000,-400,286,838,-447,567,943,782,561,-585,449,-967,-400,815,871,-1000,338,-145,121,-271,554,196,784,-893,-9,123,197,718,-495,711,232,-581,400,398,-400,-996,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00565() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{-291,27,24,-317,-250,465,-1000,624,511,1000,183,113,852,-1000,-801,76,142,956,1000,-244,-1000,368,543,-1000,506,1000,337,-661,1000,296,-792,-733,407,-1000,-1000,-1000,1000,798,1000,319,-145,-415,1000,-698,324,-791,1000,-895,1000,114,-156,-379,-1000,-1000,200,-610,418,538,1000,-427,-783,552,-809,452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00566() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{-307,-885,351,-271,-751,645,-517,-658,329,1000,-1000,-518,764,-695,-2,-261,-1000,-178,98,346,1000,717,1000,1000,461,-359,606,672,-1000,866,-726,1000,-363,1000,-882,1000,-1000,-414,-589,-649,-63,1000,-648,321,774,-1000,-405,1000,-1000,113,-534,322,12,-695,-559,-797,166,-1000,572,66,781,-85,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00567() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{162,246,-136,503,-1000,-332,-1000,601,-386,-757,-109,-1000,635,-432,358,-1000,551,-1000,387,212,746,456,1000,-115,1000,-186,-1000,1000,-625,1000,-87,-66,289,-645,12,684,-393,-830,-1000,-22,-650,138,989,1000,-406,-1000,1,1000,-1000,-625,384,755,-281,-1000,349,-1000,-866,1000,863,317,1000,-561,-830,-207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00568() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{412,971,672,330,-1000,-176,-951,-990,194,752,-37,-451,799,-560,1000,210,668,75,-155,902,528,490,308,-82,-74,314,-629,1000,282,1000,462,190,407,250,-146,-216,436,-1000,860,-1000,-297,-84,-20,453,-515,-186,-22,-208,281,-16,20,268,338,-168,1000,-6,-349,64,898,-815,190,-313,-659,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00569() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{298,705,-47,224,-218,-907,-773,1000,310,-696,30,-963,-35,1000,539,951,1000,311,-429,36,746,-318,-291,-795,-469,-287,-105,-756,873,-366,-819,181,640,106,400,-1000,317,569,448,950,-281,-40,-648,-349,-173,231,-608,-475,652,-361,1000,-233,780,539,831,525,-1000,379,170,-348,-248,-469,470,497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00570() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{1000,48,664,178,-1000,-128,-918,1000,366,837,200,-383,713,494,688,-426,-832,-660,307,588,1000,-56,1000,-618,1000,-409,312,1000,-804,424,400,-555,-627,320,1000,345,187,-621,400,659,-690,-108,1000,730,-98,-847,-396,-313,-909,-253,-1000,-38,325,-76,-543,-437,-377,403,-671,-739,1000,-372,-1000,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00571() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{-27,898,-114,399,-961,-76,-1000,2,651,-236,-251,-782,377,292,-593,1000,-54,61,145,386,-218,249,917,651,-297,-933,-1000,-1000,148,203,-1000,1000,705,-360,-1000,-1000,109,-1000,424,1000,-639,100,400,-1000,-772,-565,-225,-14,-437,-783,87,-902,1000,-917,468,719,-190,-99,489,20,21,-35,-988,639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00572() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{-866,359,-310,-650,-1000,-316,-184,1000,642,717,-662,-453,846,-1000,-27,-817,1000,936,118,1000,1000,897,1000,-111,949,186,-1000,-360,400,1000,491,357,1000,-900,-640,-75,1000,-739,387,-250,-189,297,1000,-682,-617,-251,1000,211,1000,421,61,-406,743,-864,1000,-995,-30,1000,547,173,-7,55,-1000,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00573() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{280,-128,-345,-260,-82,-865,-922,1000,397,127,1000,-753,579,502,576,741,-434,-815,770,-334,7,-166,1000,-1000,947,634,-177,-114,1000,472,-397,-1000,354,-871,1000,-1000,1000,264,12,1000,-596,-569,1000,587,-889,-300,260,-926,400,-45,847,-859,-695,-646,-149,239,-510,1000,770,-394,-309,-238,-983,931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00574() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{306,119,-105,318,252,-155,-1000,-818,432,-590,-986,-477,294,1000,-327,-1000,491,318,-543,-324,-651,-518,1000,378,-1000,648,-13,-1000,-675,-297,-1000,50,-302,1000,-1000,-601,-1000,-64,-581,1000,64,660,-1000,-243,-90,-304,-1000,152,-1000,-688,25,-624,-220,-696,271,210,-102,-939,-193,179,408,-625,-1000,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00575() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{135,-1000,-379,-528,-468,403,-619,-32,774,870,-194,-855,-159,64,-1000,-180,-966,-433,806,-651,1000,-250,492,-280,-444,506,1000,-400,127,-622,73,135,-439,790,-27,914,-1000,416,108,549,-149,1000,66,-243,955,-248,564,-612,-417,443,-244,-419,-1000,23,53,-423,-391,-1000,867,-703,-1000,80,-988,167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00576() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{-898,553,-741,388,619,383,-354,-1000,77,-1000,-432,-1000,374,1000,-5,1000,-474,1000,-1000,-740,1000,-551,1000,1000,-1000,648,-485,-262,-254,601,-1000,644,337,1000,-1000,1000,-1000,189,-1000,1000,789,1000,-1000,462,-212,-187,-1000,1000,-1000,-268,-450,-354,1000,-189,1000,-646,191,-456,-1000,1000,1000,-1000,-883,-394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00577() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{-293,271,24,342,-11,1000,-8,1000,132,625,-1000,679,1000,-1000,-141,-261,273,27,-686,393,1000,318,180,476,-432,-70,-638,1000,-25,866,992,1000,701,1000,195,1000,9,-176,223,-683,788,720,147,-784,195,336,-516,577,-328,685,-1000,618,606,14,170,-797,1000,-763,750,-1000,781,-59,-518,137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00578() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{1000,-1000,1000,-794,189,-313,-472,1000,979,1000,1000,17,485,-186,401,775,-86,-1000,1000,562,-1000,105,-242,-1000,-420,1000,1000,-380,774,-1000,336,-1000,-316,-885,1000,-1000,1000,22,1000,1000,-830,-497,1000,-428,-169,613,1000,-1000,1000,415,764,-1000,-1000,995,-854,1000,-1000,748,1000,-1000,-1000,350,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00579() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(int,int,int):void",
            new int[]{302,-526,327,-806,249,1000,-168,647,547,1000,135,707,-245,446,-671,1000,-1000,1000,916,-1000,-70,-723,-643,-1000,-623,680,700,-1000,1000,-1000,-1000,-936,-704,194,-1000,-1000,1000,1000,879,1000,-180,390,1000,-1000,617,713,439,-661,1000,539,96,-1000,-1000,160,156,802,1000,-638,783,-1000,-1000,595,-1000,583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00580() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{-51,-1000,213,-914,-611,504,-254,323,747,415,315,-11,-659,-498,-778,82,-847,-430,401,-1000,-173,285,721,-1000,-1000,-246,-413,26,-389,-735,11,-209,-645,-500,466,1000,-365,973,747,-619,152,260,306,1000,-1000,-551,478,-429,-641,-839,232,631,-468,243,652,-1000,-1000,-665,-1000,-1000,1000,-926,-728,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00581() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{-35,-460,-271,1000,61,767,674,-90,-1000,-1000,1000,-51,-680,457,-301,5,1000,166,-839,-6,-831,513,434,53,-315,-1000,-968,-273,812,795,38,1000,-1000,478,757,-231,-1000,1000,771,-636,152,-138,1000,-744,-1000,-364,-156,-988,369,-1000,-804,600,-108,-1000,653,-701,179,-997,-1000,487,1000,23,-500,-973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00582() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{-523,-311,681,699,-624,-610,675,97,-676,900,-293,210,150,803,870,248,-399,-192,-847,357,-222,684,-743,982,987,932,163,477,-685,169,244,378,-278,271,474,390,13,-70,-842,277,-370,489,-565,-448,908,665,833,177,-833,73,103,316,-203,-642,706,566,-628,174,408,122,-247,-758,686,789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00583() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{-869,-1000,-511,-238,812,899,-195,105,1000,-319,-250,843,724,-1000,-1000,-590,-942,922,-527,41,-369,-1000,487,-1000,-846,134,640,-103,659,-68,-256,972,-69,-156,-308,-242,1000,614,-434,-191,-1000,-543,146,292,941,503,389,998,82,-695,-234,-709,-1000,235,-439,-379,820,-898,337,-152,-199,816,-881,-877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00584() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{-410,-898,-93,-419,-481,10,339,435,-56,1000,349,65,-392,-246,608,1000,13,369,-408,857,814,827,-678,449,430,627,1000,-213,-1000,128,140,-796,250,392,-158,1000,-248,254,-354,-28,-1000,888,-1000,715,-150,1000,1000,1000,-1000,1000,858,773,-150,-440,387,9,-1000,398,-882,74,34,-712,-523,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00587() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{-35,-399,-807,1000,-57,553,-273,-378,-754,-633,618,125,-600,113,-274,-287,416,-60,-1000,-643,-536,-449,328,-65,-732,-958,58,-486,1000,1000,-588,943,-567,409,1000,-928,-152,1000,521,-110,169,-254,1000,-1000,414,-79,108,-62,671,-1000,-24,626,-447,-710,36,-1000,1000,-311,-351,265,1000,523,-216,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00588() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{1000,-1000,1000,-830,-1000,419,718,1000,-948,667,1000,273,1000,-1000,-1000,-523,-638,-1000,815,1000,406,1000,641,329,-455,-1000,335,-599,-1000,-691,-1000,-904,1000,1000,-678,-994,1000,-115,1000,632,-47,-1000,-663,1000,534,973,-12,749,-1000,1000,1000,1000,-1000,1000,1000,190,-435,24,-506,-1000,-1000,-113,-564,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00589() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{-1000,178,-48,1000,-352,116,841,14,227,1000,-767,535,260,1000,1000,515,-415,-612,-681,103,-7,736,-1000,854,1000,1000,-220,-196,-1000,-158,567,664,-826,-445,599,928,-263,-264,-1000,184,-257,543,-800,-604,949,665,1000,-121,-725,-193,841,-75,-286,-1000,252,1000,-1000,269,217,132,-917,-1000,253,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00591() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{1000,320,201,813,-39,-242,1000,96,1000,126,853,-576,-131,-584,207,-625,192,1000,-1000,-305,-455,-383,1000,1000,17,246,880,1000,1000,1000,695,682,-202,500,943,-386,311,303,348,-48,562,357,414,28,1000,220,625,1000,-279,-568,1000,-147,-1000,-309,855,-569,20,-121,222,-77,497,225,451,-893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00592() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{-453,-325,820,-135,-316,493,1000,492,-474,567,343,433,728,863,-91,298,-899,-758,1,134,112,920,-430,1000,-597,-153,-555,217,-804,-512,924,996,-710,-365,962,-1000,-277,196,437,310,323,-491,692,-385,-702,1000,-176,-439,-673,682,-497,128,-370,-991,290,273,-1000,-85,-329,160,-1000,-807,-118,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00593() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{1000,-1000,731,-771,-318,864,-440,46,-837,-519,195,70,-574,-1000,-1000,-9,68,-409,-252,687,750,77,868,-185,-1000,-1000,966,-65,1000,379,-624,-658,1000,996,-28,-235,1000,315,1000,247,-904,287,-222,550,378,446,239,1000,-1000,719,1000,1000,-531,1000,1000,-865,106,-560,-282,-1000,624,1000,-558,-764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00595() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(long):void",
            new int[]{-213,232,-826,1000,164,-422,-167,-146,1000,-755,-331,1000,114,1000,97,-919,-354,-601,1000,-405,-1000,-282,-171,-184,18,436,-709,-1000,1000,-139,-186,1000,-125,-327,914,-1000,-307,-378,-192,479,661,-259,844,-1000,76,-846,-16,-891,41,-956,-251,-23,-916,-783,-1000,719,374,-1000,924,-329,-1000,-255,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00596() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{1000,150,-15,732,78,-930,-302,-1000,1000,284,641,-251,-1000,-994,170,-531,577,261,569,-872,-1000,-483,-18,411,-1000,451,1000,-1000,992,-1000,-292,-1000,-575,347,836,-607,-204,1000,740,-398,336,-590,607,-683,-1000,501,-416,279,1000,878,796,-492,1000,134,1000,127,99,-1000,-996,235,-216,525,264,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00597() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{914,57,-676,267,-969,-113,679,-343,793,-212,1000,579,-256,-729,-599,-313,-194,174,931,-673,-172,-32,57,-945,-138,128,-988,-380,-257,-800,-319,161,523,1000,793,-775,296,-272,310,894,-84,-939,-623,183,-897,-78,-198,197,-623,-240,-656,563,-704,-1000,211,334,663,-368,-525,1000,-670,-991,82,-183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00598() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{-802,857,125,-1000,473,-41,-1000,-320,170,-1000,-519,-750,461,454,-1000,661,-147,1000,740,1000,754,1000,284,750,951,209,-1000,-1000,1000,975,1000,774,954,-589,-848,295,-1000,-177,-1000,-1000,1000,212,-248,-414,-1000,-1000,678,-429,-1000,824,979,213,-1000,1000,-476,-923,-1000,347,552,-835,639,730,1000,806}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00599() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{667,-36,349,835,172,-473,-794,-473,516,488,57,-775,-873,-401,-529,479,-706,-230,285,-977,-526,-332,-862,989,145,466,123,-895,717,-602,75,-894,747,311,510,-788,-471,616,-134,718,50,-280,-630,482,-763,332,591,440,442,866,517,-886,193,580,896,-448,-616,-943,470,-876,-717,167,-281,-828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00600() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{1000,368,-631,403,-624,-631,752,586,539,-624,729,-830,-1000,175,966,251,695,-524,-224,-1000,-1000,-540,321,992,401,626,1000,-1000,-410,-1000,-1000,-1000,1000,1000,1000,-882,-312,1000,760,1000,-45,-1000,249,287,407,4,-837,-288,410,906,41,-440,1000,-429,708,466,180,-924,261,-23,-1000,-1000,-367,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00601() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{86,-251,456,428,479,-1000,-379,45,-639,210,538,865,29,955,912,1000,511,-671,-87,393,-612,75,-1000,-528,460,573,-626,1000,-1000,648,-1000,530,479,340,574,797,969,555,-238,881,-664,-194,-599,-1000,195,-1000,770,574,-108,920,-549,-238,-95,47,169,-1000,-513,-147,181,-503,-651,365,511,-536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00602() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{188,331,810,1000,-346,-14,-858,-642,401,645,-531,1000,1000,576,-347,62,-750,-299,283,-706,-575,-475,-1000,-222,-895,-245,-703,-353,336,413,732,-441,63,-293,535,18,-1000,352,885,577,280,170,-366,144,-546,-570,1000,-180,580,319,-584,-941,220,-367,847,-1000,-107,-700,-361,-718,-544,1000,-244,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00603() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{739,-536,-295,-473,221,-41,-621,-322,928,383,-136,-45,-206,171,-1000,-164,-147,1000,761,-1000,-268,1000,-331,750,90,491,584,63,1000,-418,-474,-1000,288,-124,-848,96,348,-5,531,926,-453,-158,1000,689,-555,1000,-877,247,124,65,-113,-855,-1000,-401,1000,874,57,347,207,-212,-51,-594,-1000,-600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00604() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{1000,792,554,15,960,-583,-215,-675,767,-404,627,-1000,-595,-172,394,449,427,497,-22,108,-309,249,72,1000,887,642,780,-1000,1000,-930,258,-1000,1000,319,678,-785,-1000,1000,-26,-400,-58,-633,-514,-295,-816,405,219,170,-245,1000,1000,-1000,717,1000,1000,80,-265,-659,1000,-1000,-443,628,405,-403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00605() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{1000,383,-942,637,-803,-833,-659,169,611,57,1000,-338,-513,-1000,-555,461,309,219,655,-681,-127,-1000,13,55,620,1000,-510,-1000,-520,-1000,-1000,-274,1000,1000,654,-1000,71,437,-1000,926,1000,-1000,-1000,-54,-416,36,1000,124,-760,666,-87,267,-1000,102,447,793,179,-650,470,4,-735,-1000,607,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00606() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{419,390,413,-1000,-995,-146,682,-267,762,819,-452,577,-753,278,-302,-239,-194,-364,-67,-614,-978,1000,530,-992,-364,-537,-343,403,-257,-779,-1000,292,-865,1000,1000,-341,176,-628,894,510,-342,-1000,9,-144,-429,186,-327,183,281,94,-146,715,330,-329,146,210,1000,-282,-1000,660,-775,-938,-469,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00607() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{-954,-249,284,-391,-513,1000,-526,21,349,157,-1000,1000,693,882,-168,-1000,-1000,-468,-983,-1000,-803,1000,200,-989,-1000,-1000,-309,647,675,1000,-463,6,-815,365,54,1000,1000,-1000,5,-400,171,1000,1000,839,127,-872,397,-415,311,-1000,919,282,-953,-1000,196,-784,112,-892,-238,392,-573,-236,-860,-935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00608() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{265,542,1000,-208,-982,223,-995,-775,406,1000,-801,619,-61,904,-555,-106,-1000,-330,-426,-916,649,950,-325,-1000,-1000,-911,-67,269,1000,890,-305,252,-983,591,-156,871,-613,-1000,-55,312,632,468,389,329,-331,-173,816,-324,684,166,860,-52,165,400,426,-776,-116,-663,-545,-586,-284,704,-614,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00609() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{431,553,325,403,-205,-146,852,755,504,159,815,-566,-754,952,1000,442,-55,-1000,-726,-794,-1000,-920,321,137,248,1000,1000,-652,-875,-1000,-1000,-1000,344,1000,1000,-1000,-346,1000,1000,1000,-549,-1000,130,-170,725,-10,-837,-202,1000,1000,-460,-911,1000,-295,937,185,805,-863,-692,-455,-1000,-740,-658,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00610() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDate(org.joda.time.ReadableInstant):void",
            new int[]{548,297,-81,372,-356,552,-14,-185,240,-386,-1000,97,-150,52,-1000,-11,-1000,630,915,-308,901,452,-1000,1000,-879,-435,-1000,157,1000,1000,1000,379,1000,-686,-1000,7,176,-818,-1000,-642,1000,1000,-1000,854,371,-397,1000,-400,-1000,-227,275,-339,-1000,48,286,-1000,-1000,-148,1000,-49,126,960,607,-250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00612() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{-521,-1000,-489,1000,-243,-51,456,1000,1000,984,-220,717,290,803,-931,1000,-672,518,537,357,-1000,1000,-1000,-1000,827,1000,1000,-285,-21,272,1000,262,-1000,-709,-804,-109,843,772,-11,743,-268,1000,543,-751,1000,-1000,259,-410,45,687,-235,-676,833,-292,774,882,-1000,-119,-1000,-561,400,-236,850,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00613() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{117,-13,-771,-331,460,152,-206,-395,725,-623,111,-579,704,331,592,-381,165,477,601,1000,-550,121,-606,777,-598,27,-833,240,-1000,118,682,-506,-625,398,-36,119,-533,-570,815,530,-421,-252,-500,-232,910,-355,-191,-923,57,-545,237,-376,-296,1000,-504,-728,54,648,20,342,462,44,-506,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00614() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{-1000,-299,-1000,-1000,569,-1000,-640,-982,736,-1000,-659,187,591,71,651,1000,719,209,-1000,34,1000,-56,1000,1000,554,-1000,-786,-167,377,-879,-129,503,1000,784,186,1000,-1000,-488,74,42,-562,1000,-944,-706,-1000,1000,-167,636,-1000,-1000,1000,-13,-1000,1000,-212,-1000,891,883,1000,1000,79,323,-759,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00615() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{-601,597,79,-27,1000,1000,-473,553,1000,409,446,476,283,-232,-1000,-83,-539,1000,213,1000,-1000,1000,-1000,-62,-1000,-324,-268,1000,-409,-982,-40,5,-811,1000,-833,-623,1000,-508,-1000,290,-234,1000,-374,716,1000,-1000,-101,-1000,133,1000,288,-357,-425,-189,-953,743,818,1000,-1000,-530,393,94,1000,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00616() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{371,954,-156,-635,938,-131,-918,-869,736,-206,836,-1000,778,-844,959,-1000,314,894,16,957,-516,135,-1000,414,-880,63,-1000,1000,-1000,716,217,-1000,-1000,1000,122,250,18,-1000,-78,-135,-650,-132,-892,328,1000,-781,-996,-1000,-333,-18,137,80,-57,1000,-912,-1000,-867,669,97,-556,748,-7,-202,-458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00617() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{-1000,1000,-771,774,1000,1000,-437,-126,499,-623,43,-129,768,-486,-798,-381,-205,1000,-899,1000,-1000,620,-1000,642,-598,-1000,-738,1000,-1000,-123,169,2,-1000,1000,-276,188,643,-747,-1000,-581,183,508,-285,1000,-402,-878,-191,-1000,-484,-248,498,-190,-431,1000,17,817,-319,-647,-737,301,462,581,-203,-769}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00618() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{386,-943,370,-785,-287,-13,-506,-893,-490,315,54,712,757,999,-22,-51,-840,574,336,989,133,-657,794,669,860,364,-894,-580,360,-617,178,-513,530,116,-783,644,-437,308,-185,778,-146,-152,-582,-268,-490,898,-441,-371,-57,-536,489,-489,-338,128,-608,604,340,763,237,756,-481,-24,-496,675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00619() {
        org.junit.Assert.assertEquals("VOID|getDayOfMonth=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{3,637,-772,508,-147,-386,-782,325,934,955,299,349,581,862,-829,445,-883,916,151,558,-660,503,-928,-168,467,-769,432,254,-464,18,286,-457,-1000,390,-933,381,1000,51,-878,715,-232,1000,-59,-1000,622,-503,-23,-950,77,1000,-95,-170,885,402,189,619,-1000,383,-899,-610,-224,-404,522,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00620() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{-979,972,69,394,1000,1000,-277,1000,1000,5,-379,1000,757,632,-792,271,-85,785,1000,673,-1000,794,-181,-1000,-611,659,1000,-126,-27,-1000,920,1000,530,149,-932,-1000,314,-73,-204,232,470,563,-298,-373,1000,-1000,136,-448,919,1000,470,-1000,124,-1000,-1000,1000,710,652,-1000,-795,1000,-576,924,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00621() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{-733,-267,-726,-556,406,-1000,-796,-843,-1000,-247,193,-334,237,-366,374,777,307,763,-1000,282,1000,693,260,866,-573,-1000,-694,87,119,2,210,28,622,692,-361,1000,-679,-689,-111,516,-1000,948,-919,-308,-145,171,-167,-191,-1000,-1000,411,36,-78,1000,212,-1000,1000,832,1000,1000,142,-133,427,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00622() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{617,328,-489,439,323,-131,-548,-289,736,605,807,-1000,591,-178,562,-1000,435,175,-150,902,24,110,-1000,906,-1000,-1000,-181,730,-773,851,175,-717,-650,784,186,250,-143,-914,1000,646,-1000,-295,-883,115,1000,-790,-702,-1000,-785,-662,-389,213,174,1000,-270,-1000,257,669,31,-487,779,-208,877,-518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00623() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{317,273,-662,-744,511,-1000,-768,-844,49,-153,644,-1000,365,-525,1000,-136,1000,-756,236,1000,1000,-1000,-688,1000,-1000,-1000,-937,782,-676,557,-1000,-786,205,1000,663,-338,-1000,-1000,-588,-112,-1000,-1000,-1000,95,-258,537,-724,-489,-1000,-662,-68,48,-900,1000,-1000,-777,504,1000,823,-868,996,61,-7,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00624() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{-937,105,-159,-60,353,-658,296,81,-1000,-997,-706,-56,572,534,947,69,809,-427,-1000,648,-377,-400,-1000,728,1000,-378,-188,-91,-49,-610,175,175,-714,-32,191,250,-675,476,1000,-437,-324,-386,101,-180,170,329,-702,511,-958,-380,734,-663,-949,1000,-270,-1000,-1000,646,1000,1000,1000,468,-1000,-281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00625() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{731,597,79,-377,918,396,-462,826,-616,449,446,245,-976,-590,-924,-300,-68,-463,819,-408,-794,109,-192,-559,-673,-891,413,505,203,-982,-675,-53,75,808,-62,-551,840,-755,-932,312,104,475,-374,146,-732,-458,-159,-964,311,935,-164,-274,-425,-406,-72,805,818,869,-92,132,78,-128,712,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00626() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{-77,-671,621,213,221,526,-786,-590,339,899,769,-1000,1000,-112,-443,259,-709,1000,772,880,-12,-1000,674,-622,-1000,-555,-1000,-8,607,-991,442,146,-71,285,-1000,-272,31,-507,-824,973,-181,525,-542,-966,-12,1000,-519,-980,345,823,946,-328,-502,-602,-1000,-663,-125,425,-746,323,-294,-6,111,809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00627() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfMonth(int):void",
            new int[]{73,129,-491,737,118,-101,240,527,998,338,-56,-706,617,929,-135,-943,493,14,-199,750,-700,-694,-1000,108,-65,-311,1000,178,-454,361,149,-255,-1000,336,47,121,-1000,169,-512,246,-687,733,523,-982,973,-595,-202,-349,-370,261,-153,-355,767,693,756,208,-767,-423,-491,-513,19,83,-97,-641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00628() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{-15,-527,-146,-81,-1000,-520,36,-804,368,105,1000,127,150,879,-803,496,-795,-177,-1000,-814,243,153,-577,275,529,-78,423,1000,-58,807,-622,-580,-619,-289,-510,-103,-245,627,-76,-497,-223,-734,-626,-384,-97,-971,-489,179,-572,-519,986,787,-911,-884,59,223,337,-1000,12,140,-14,1000,-777,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00629() {
        org.junit.Assert.assertEquals("VOID|getDayOfWeek=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{-741,-460,-145,416,106,-101,872,-88,-1000,776,-29,-778,645,377,-974,-254,-1000,-176,-407,400,356,487,654,-246,144,-410,842,-566,-31,580,490,-1000,-657,-1000,849,1000,162,189,82,-387,106,-501,-1000,133,514,785,-223,-89,-728,-693,175,-26,-962,-355,674,797,-1000,1000,390,139,72,104,1000,614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00630() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{-1000,-879,599,125,-100,53,-243,-44,-837,-196,-101,-612,-475,63,-767,-1000,-1000,-1000,-585,-1000,1000,411,1000,-314,5,704,1000,-1000,-69,918,968,-1000,-388,-517,202,806,-58,-303,896,-1000,-580,-1000,-1000,-903,1,1000,-1000,-1000,-932,-1000,-15,-1000,-1000,-666,1000,1000,-1000,939,1000,-847,-519,452,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00632() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{1000,-405,-556,-857,810,-938,968,-1000,161,258,-167,-981,-1000,329,224,353,-702,1000,-1000,60,-1000,-1000,-1000,1000,-286,-825,-659,1000,627,-899,-1000,-580,-839,-772,-79,-169,710,-107,82,932,732,192,-325,448,673,-499,1000,1000,-428,-36,-185,1000,-689,-551,-627,-634,1000,-1000,-1000,979,1000,-400,13,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00633() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{-440,759,-902,199,-359,261,1000,376,670,-288,-896,470,-165,807,-1000,1000,-840,115,432,-302,652,1000,117,1000,509,-290,-843,1000,-30,990,387,65,-264,-187,-1000,1000,643,-152,-82,1000,-106,-326,1000,680,1000,1000,-921,-824,400,-334,1000,1000,305,-1000,1000,-1000,-577,488,311,922,-406,1000,1000,443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00634() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{740,-1000,-120,911,-379,-86,-500,676,-452,166,-213,100,375,566,-1000,448,92,-692,-544,-207,745,711,1000,-321,747,-1000,944,-256,-309,850,444,-345,-875,-1000,476,871,212,785,-243,-400,916,-149,-6,-993,127,396,-1000,562,556,-760,700,-567,-1000,-945,1000,1000,12,820,82,126,592,496,813,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00635() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{-705,1000,-159,-203,-670,689,855,26,-1000,-124,-1000,-1000,495,-1000,-460,-200,-1000,1000,-684,1000,-430,-44,1000,-140,1000,-359,-277,-780,-548,443,500,-1000,701,-976,1000,1000,-897,-1000,-1000,598,-272,317,539,1000,297,-87,222,277,-374,-406,-720,-283,-438,1000,-511,-613,327,1000,504,-1000,-1000,413,851,-392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00636() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{-259,-405,219,-171,-587,1,-153,-682,-418,-61,-781,-672,913,498,-785,-236,-1000,-425,-1000,94,458,258,1000,108,330,290,971,-618,-317,862,-75,-1000,-628,-517,-79,310,-179,28,139,-400,45,-1000,-1000,-296,33,540,-1000,-439,-504,-1000,428,-567,-1000,-551,797,1000,-1000,-189,1000,-244,-431,970,572,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00637() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{-422,-911,-551,119,-347,374,413,356,-513,776,-461,662,-229,377,-974,-735,185,-334,-888,976,300,853,734,-957,984,-199,-702,-566,-877,918,304,327,884,298,-751,984,121,902,-96,291,-939,-382,854,-206,-98,-300,6,-766,624,-762,652,311,-72,-923,674,681,-986,-60,426,-958,-344,346,981,-437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00638() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{-1000,-202,-381,-193,956,-468,-555,1000,-722,78,90,614,1000,865,-280,-390,-1000,-774,-743,-620,1000,1000,2,-1000,144,-125,-1000,-330,52,963,1000,201,-251,-346,-900,1000,-309,-480,-229,1000,-499,49,744,-472,1000,-93,450,-960,-1000,-641,951,758,81,982,-21,-70,713,823,52,-56,-426,164,1000,678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00639() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{-272,-1000,-27,1000,344,-362,154,317,-437,-288,206,670,330,324,-85,-262,-962,-1000,276,-3,241,-147,117,-51,50,-87,1000,18,641,990,610,-580,-264,-772,-1000,1000,565,237,-142,-442,-702,-774,-850,-254,48,-569,-923,313,-754,-772,125,-478,-853,-1000,1000,722,-752,1000,-183,200,-406,-218,-608,443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00640() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{135,494,39,-742,723,-907,-400,-344,-903,-995,-1000,-134,325,-1000,892,-20,-606,1000,98,-672,-655,-659,-410,74,94,110,-746,-973,-11,-1000,44,-357,-567,-246,657,-67,394,-1000,-89,1000,76,739,928,398,400,113,893,-188,-174,-224,-553,303,272,939,-740,-1000,713,97,606,-127,-235,-444,-1000,-739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00641() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{-142,740,523,-216,208,335,-305,-172,-730,246,-535,708,35,-691,575,-952,112,615,-186,289,-327,-583,33,-278,632,-332,-1000,-223,391,-1000,117,-177,169,-269,787,283,299,-98,-857,549,546,480,-1,200,-393,-263,-548,57,84,-277,-397,-181,74,-80,-190,-86,495,-104,-517,-713,-389,-766,-237,-953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00642() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfWeek(int):void",
            new int[]{469,-993,297,73,757,-450,209,-724,145,-773,206,-466,235,53,130,-737,-962,-622,-678,605,57,-889,-117,719,-495,-31,813,1000,954,-538,-170,-580,-206,-566,41,-151,906,-735,996,1000,-763,105,-468,-733,-38,-280,-1000,-56,-599,-707,924,471,-689,187,746,-86,199,51,466,-227,407,-374,-293,-146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00644() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{-655,-574,635,571,459,624,871,1000,1000,-604,-1000,995,1000,238,1000,-1000,-144,680,-839,-759,-641,450,1000,-899,337,-1000,809,-939,877,510,-638,419,417,-1000,-747,-158,1000,1000,1000,-1000,1000,1000,690,780,-497,-474,1000,-1000,1000,-843,-1000,1000,1000,598,565,293,-795,-353,-1000,129,373,212,-1000,172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00645() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{-1000,322,-122,1000,959,481,-992,40,-512,-1000,-338,-1000,-797,-544,441,401,606,882,205,396,-541,1000,-1000,1000,268,-459,1000,-150,-1000,-1000,-437,-1000,1000,-260,-1000,220,-673,-243,1000,732,-1000,-790,1000,577,-142,984,493,1000,95,-1000,-248,-381,-488,-625,-445,-775,-590,-661,-1000,-561,-95,-633,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00646() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{-221,840,-361,-140,364,349,676,-616,-211,398,-699,779,331,-47,-1000,-875,-777,37,782,32,-282,350,85,334,883,-803,-135,-324,-551,-502,-43,-42,675,-417,-1000,-178,-479,643,-871,-269,417,559,968,354,-657,-483,1000,400,-42,-1000,-635,909,-559,1000,286,41,510,-337,1000,-847,332,-1000,-1000,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00647() {
        org.junit.Assert.assertEquals("VOID|getDayOfYear=java.lang.Integer:Mjc=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{-152,469,-681,-291,-713,887,440,-1000,-745,330,75,418,534,-789,220,127,276,286,1000,-374,-1000,-86,-307,660,1000,1000,436,1000,297,-224,291,910,-802,-444,-402,1000,-898,666,-704,738,541,-380,-38,-374,-741,-524,486,839,-1000,-190,-42,117,579,521,762,410,1000,1000,1000,-825,345,-395,387,629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00648() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{-1000,612,-261,338,49,298,-105,-307,8,-869,-650,-26,-64,-1000,1000,-1000,1000,623,1000,523,925,876,-1000,660,-19,6,837,618,385,-600,307,-111,-802,-939,530,723,-816,1000,395,617,186,-448,1000,308,-412,-1000,783,660,10,-1000,-500,1000,161,-47,92,-200,983,181,-256,-723,1000,449,-991,-261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00649() {
        org.junit.Assert.assertEquals("VOID|getDayOfYear=java.lang.Integer:MTgx", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{-1000,224,28,189,27,624,871,-253,33,-431,-602,995,437,181,-829,-702,-707,1000,561,490,-541,1000,-400,334,923,-228,1000,-557,-523,-484,330,180,675,-417,-1000,-158,-400,1000,772,-269,417,1000,815,523,-904,-843,1000,400,100,-1000,-737,980,-35,1000,822,301,605,-353,-529,-1000,1000,-1000,-1000,-440}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00650() {
        org.junit.Assert.assertEquals("VOID|getDayOfYear=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{-981,-219,750,310,-176,-203,-525,379,-407,-1000,-338,-788,-743,-612,1000,-211,236,1000,-839,-29,-730,1000,-1000,1000,-362,-1000,809,-486,-908,-843,36,-65,1000,1000,-940,-980,-544,-139,1000,1000,1000,-22,690,-239,-147,230,-203,1000,-125,-843,-1000,357,562,-1000,302,-560,-381,-287,-1000,-75,1000,-506,-1000,-851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00651() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{-133,940,703,-757,783,-224,-887,595,861,-648,333,-875,-443,-648,-248,-404,132,-325,886,-664,233,-235,-703,288,923,-922,971,835,-909,487,-737,152,918,795,-745,422,-837,-204,303,621,465,-667,921,-207,523,684,709,-262,-921,236,7,857,951,-62,-817,525,14,254,18,835,-10,-508,-472,-215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00652() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{1000,-555,674,-608,-232,780,1000,-94,-367,1000,-397,354,823,-22,-869,1000,-1000,-439,-400,-774,-517,-98,1000,-123,923,-227,-152,131,-570,962,-729,1000,873,767,-385,-580,1000,-973,-763,-90,1000,480,-1000,452,-317,1000,1000,-630,1000,1000,-556,862,122,554,-864,547,-1000,727,679,722,-811,-16,1000,692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00653() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{-1000,-299,-680,887,-402,977,-375,-131,222,-628,-960,-572,-1000,231,-945,-277,449,444,542,54,-106,39,-77,281,1000,1000,593,-5,-583,-355,527,1000,126,300,-770,-103,-578,1000,417,700,-1000,-1000,698,862,-387,50,-1000,151,-635,1000,-433,-1000,343,-824,-58,653,530,1000,1000,228,-294,-1000,152,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00654() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{829,419,177,-806,-378,-235,-233,-966,-776,-1000,8,-995,-817,-1000,-527,714,486,-564,1000,-525,-630,-204,-862,1000,-826,-287,1000,123,912,803,-1000,613,1000,340,-130,-58,-736,1000,-595,872,-533,538,1000,-487,-905,-1000,60,1000,-618,801,-472,524,1000,46,-1000,-757,-1000,-461,974,-723,1000,-674,994,992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00655() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{1000,801,-56,-725,550,-235,-1000,-379,-1000,461,75,-1000,-1000,-993,-756,-450,614,-473,205,104,-630,-128,-48,1000,-248,-1000,-622,123,-1000,56,-1000,-160,1000,1000,-1000,-1000,-736,-1000,783,1000,-1000,-821,1000,-509,-83,1000,566,1000,-291,-715,-577,371,-398,-814,-1000,-1000,-476,-198,-1000,72,46,-882,-1000,235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00657() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{857,351,233,-1000,-239,1000,241,-876,-1000,1000,309,-365,146,-638,-1000,1000,-19,-711,1000,-56,-1000,575,-10,1000,1000,485,487,1000,-1000,340,-1000,793,1000,1000,-283,-774,-478,-973,-871,1000,837,-95,-521,-151,-681,564,898,770,-627,564,-421,544,335,1000,-1000,224,24,762,1000,-64,65,-905,968,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00658() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{-1000,552,-907,913,1000,1000,-1000,-718,-671,-214,-259,-1000,-987,-679,-153,33,1000,367,1000,920,-817,1000,-723,1000,794,-1000,812,-90,-1000,-1000,-577,-1000,1000,-552,-1000,808,-1000,270,1000,732,-1000,-1000,1000,948,-540,246,1000,1000,-501,-1000,-436,774,-262,595,-754,-1000,248,-673,-1000,-1000,436,-562,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00659() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setDayOfYear(int):void",
            new int[]{-223,741,-851,449,-48,343,-149,-694,-452,841,8,-1000,-332,-530,321,957,635,327,1000,909,-1000,96,-1000,1000,996,371,1000,1000,-989,-579,-102,400,741,20,-75,-1000,-1000,837,-310,872,-533,-1000,-349,-91,-608,770,928,1000,-811,-84,84,-629,-301,-123,-938,-361,174,739,262,-679,-95,-181,400,-104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00660() {
        org.junit.Assert.assertEquals("VOID|getHourOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{1000,-806,-1000,-292,1000,-354,421,-598,-1000,134,-678,584,-72,490,540,-173,415,643,-340,-500,194,1000,-1000,-1000,662,-637,778,1000,-634,868,-206,1000,1000,338,42,1000,256,-1000,219,-597,-138,1000,-317,-1000,-1000,-189,-470,114,-517,-288,-363,-63,-508,660,198,652,1000,700,-637,-1000,1000,-866,199,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00661() {
        org.junit.Assert.assertEquals("VOID|getHourOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{-1000,131,348,619,-254,-196,474,1000,-151,-843,-841,-941,691,-925,7,1000,-251,-561,441,1000,1000,-1000,-781,-8,-935,542,-664,-1000,338,-1000,825,-1000,-1000,-339,1000,-315,-1000,-1000,390,-1000,-1000,-365,-1000,770,621,619,826,25,-413,449,-1000,-596,307,-137,1000,930,-575,1000,-930,-287,-595,-28,-840,622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00663() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{257,-726,974,-793,555,134,-622,275,167,-178,-464,-60,-43,331,681,-313,-337,609,306,-180,31,431,585,-510,1000,-785,793,417,225,-225,115,109,342,964,-222,-171,954,436,-427,543,812,1000,-85,-76,647,-792,324,135,-286,-569,-304,1000,-919,975,-242,265,1000,-1000,-184,-563,484,-1000,894,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00664() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{-1000,-808,932,1000,757,729,1000,1000,115,-657,-1000,-228,102,318,-174,-36,799,-993,-593,-1000,-1000,-1000,686,1000,520,-193,-1000,226,1000,277,12,-1000,684,1000,1000,-389,601,473,101,949,92,-1000,-227,-323,-239,-305,-739,-134,1000,1000,-1000,-563,-173,1000,-566,18,-1000,288,1000,1000,468,-113,1000,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00665() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{-152,-1000,972,752,1000,694,-400,510,104,-545,154,371,346,-365,-112,-643,-237,-436,209,233,923,-1000,1000,975,188,-179,-770,65,-497,-611,-327,-775,189,410,-94,-419,36,-490,82,401,-85,851,497,-110,728,500,24,25,16,-114,-99,636,-439,541,221,178,-671,-597,898,255,842,-233,988,-573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00666() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{919,-699,973,983,-911,642,422,-126,736,510,688,592,-578,592,-371,540,401,795,-356,-160,-95,-137,-364,469,-353,-422,243,-769,-180,280,82,126,104,-47,-227,220,453,271,535,-722,-255,586,-728,-917,809,-874,236,-372,749,-588,577,182,54,-734,-583,-383,523,-313,-576,295,689,-653,288,-651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00667() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{-801,-792,-68,-365,363,-226,187,-592,-842,-84,150,1000,500,-7,829,-434,1000,-53,-11,14,1000,862,994,536,772,-540,514,1000,-1000,-1000,463,511,503,-619,-510,-1000,533,-1000,-911,573,-686,803,-53,1000,408,1000,-550,-353,-100,-1000,425,122,-273,-219,924,-137,696,-221,969,728,858,-495,561,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00668() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{-952,-1000,-627,663,327,-806,-848,1000,402,-750,-187,-186,469,361,-899,1000,-403,1000,1000,560,1000,-906,-560,374,-204,-405,-592,-788,1000,-867,448,-1000,-963,-677,1000,-1000,-566,-1000,716,468,-704,278,-748,-881,809,632,823,-75,-479,585,-1000,412,-294,287,725,1000,-1000,1000,-15,-1000,396,-500,125,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00669() {
        org.junit.Assert.assertEquals("VOID|getHourOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{1000,-1000,484,-285,175,63,1000,-959,-322,-676,1000,394,9,-700,236,-384,90,1000,1000,1000,777,378,723,-464,198,-88,1000,1000,-1000,-1000,1000,978,-348,-666,-1000,519,39,-660,118,1000,-1000,450,122,9,1000,336,1000,52,-1000,-1000,1000,1000,-688,-988,643,1000,997,-332,874,457,-166,306,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00670() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{-1000,-608,514,804,820,367,140,266,204,-322,566,712,961,-1000,-41,-1000,2,-1000,-349,-64,1000,-985,797,1000,299,-416,-1000,271,177,-561,-2,-1000,27,780,-368,-12,-31,-1000,85,-252,-290,1000,839,-444,469,702,-465,482,-388,-404,-545,440,-857,-2,86,-872,-872,-576,1000,432,1000,-714,1000,-760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00671() {
        org.junit.Assert.assertEquals("VOID|getHourOfDay=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{-1000,-632,1000,469,681,347,-1000,1000,-392,-323,-850,-60,489,1000,720,297,-637,531,46,-1000,-1000,-1000,410,913,1000,-587,-1000,-712,644,-503,-1000,-1000,-459,1000,1000,-1000,1000,648,-338,197,1000,-442,-830,-1000,236,-934,-670,-64,270,1000,-1000,461,-225,1000,-672,-925,-1000,-709,922,720,1000,-805,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00672() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{1000,-1000,976,-201,1000,-346,-1000,488,-1000,63,-470,-258,-336,1000,382,200,133,-671,724,-141,-169,470,1000,-514,-881,-1000,-1000,978,815,265,-63,476,812,1000,-154,6,-1000,120,-581,932,1000,-298,-193,-1000,703,-1000,338,292,-703,1000,-288,1000,-1000,97,-498,852,-476,-657,-184,-1000,1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00673() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{-1000,-28,576,1000,-418,867,-48,742,682,-671,-639,-1000,574,7,333,574,-1000,140,-140,-861,-1000,-954,1000,901,-65,719,-1000,-273,212,-797,-770,-1000,-359,28,516,-1000,147,1000,419,546,1000,-1000,-641,-418,291,238,1000,957,563,698,-452,397,504,-400,-212,-894,-1000,-1000,1000,703,42,227,-952,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00674() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{-1000,-808,251,605,689,-380,1000,812,-767,-236,-133,916,58,279,985,-431,1000,-993,-270,-451,-608,431,147,1000,1000,-1000,-1000,1000,867,-579,760,-1000,490,1000,762,-389,889,-927,-665,990,-1000,-703,77,-1000,-633,-452,289,-589,-1000,1,-663,-319,-897,-90,283,18,-1000,1000,1000,804,1000,-427,959,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00675() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setHourOfDay(int):void",
            new int[]{-856,-1000,533,1000,18,238,999,1000,960,-1000,-285,-1000,183,-1000,-1000,232,-1000,-1000,283,410,1000,-1000,924,967,-1000,1000,225,-1000,-564,-36,665,-912,231,184,983,114,-1000,-1000,-192,305,-497,-1000,566,83,-190,955,205,-553,1000,143,213,-170,960,-138,697,900,-606,429,1000,565,-170,-494,-60,827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00676() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{-25,-239,-516,-236,325,256,330,519,-465,-222,377,-774,1000,-494,872,86,-347,-436,-558,1000,-600,366,1000,-27,-660,580,121,-843,265,978,-715,-214,-297,-1000,-146,-936,-253,-481,-1000,-286,-548,-588,1000,263,-932,-524,264,-538,-1000,-201,633,662,543,1000,775,362,-968,-319,-1000,179,291,1000,-47,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00677() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{1000,1000,555,24,724,193,-398,-24,341,-340,129,1000,725,-758,325,41,306,1000,307,-138,-1000,-85,-731,-78,-84,622,356,1000,-880,-576,238,915,1000,181,-500,916,-1000,1000,-314,-265,1000,213,-166,476,1000,1000,367,915,105,-799,-307,516,-1000,1000,-936,218,759,-178,1000,564,-1000,-617,-264,-972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00678() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:LTU3MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{-1000,891,332,-295,-394,-275,-571,-253,1000,-1000,-872,-766,1000,-1000,199,-147,-278,-1000,1000,495,1000,349,50,-393,1000,1000,-1000,-1000,-322,858,-352,128,-1000,-869,1000,-642,-1000,522,1000,633,-319,-1000,753,302,-1000,-165,-400,-65,-1000,932,1000,1000,1000,85,612,1000,148,-33,-720,406,-353,1000,397,935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00679() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{-457,-700,867,-668,-736,-353,-813,-109,-1000,-488,-1000,-412,141,-787,-1000,86,411,-436,433,589,-1000,514,376,-27,966,1000,121,682,1000,538,-159,1000,-374,1000,-977,-447,-286,1000,-222,737,-108,-116,1000,1000,-1000,-790,1000,570,-668,-386,1000,447,444,367,313,796,-1000,-319,400,-24,-1000,-724,1000,411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00680() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{844,-346,-750,-246,295,-862,113,-349,938,539,800,495,-4,-1000,-60,123,-598,-530,-1000,603,713,-37,1000,-3,-715,1000,-207,-425,-386,1000,-93,-96,165,287,748,-508,-617,367,-869,-304,-837,-492,289,432,-314,-587,-436,-679,-769,-379,-237,413,-646,760,279,596,178,-370,-323,-455,-5,789,-574,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00681() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{83,165,-531,518,884,713,369,100,-830,763,115,60,-397,710,485,-43,361,-343,-301,665,171,-867,222,840,225,-854,-469,128,-869,680,-108,-477,742,-141,900,-325,912,-817,-216,410,19,463,-731,-765,-571,395,-781,-806,-5,-938,163,-943,-846,90,340,-433,203,658,973,199,961,112,-920,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00682() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:LTE=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{892,755,629,34,-814,-146,-414,-379,340,1000,-88,60,1000,126,516,558,928,-958,-1000,254,-780,-1000,-227,59,1000,-1000,1000,1000,-240,-246,40,-477,247,687,-1000,-272,119,1000,-179,-1000,754,-786,542,-596,-176,1000,1000,728,-1,-1000,-1000,-469,-665,577,-602,553,320,980,1000,-7,-1000,600,556,-679}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00683() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{220,-749,-1000,689,-36,97,459,139,-351,57,236,733,555,-600,658,-488,771,-31,-818,-1000,326,944,-242,-632,-205,-460,-92,409,-104,-725,-847,133,370,-121,-69,1000,1000,-1000,-76,-153,694,512,-507,-921,93,636,-485,-256,199,-164,0,-818,-563,-491,7,138,73,-639,380,-1000,133,-322,-290,-818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00684() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{-594,-579,471,-64,-421,-325,-1000,562,98,-442,-428,-225,648,-422,-1000,1000,758,559,692,-300,-359,381,-300,-656,1000,270,-370,742,1000,-879,514,907,217,519,-860,300,-837,1000,655,506,353,-715,481,1000,139,724,1000,795,-225,-104,300,542,-295,147,-267,619,33,-946,-300,457,-990,506,695,-551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00685() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:LTQ4MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{-1000,1000,-117,139,-431,-1000,0,-279,322,66,-1000,417,362,-893,-480,563,613,991,684,-807,-513,-840,-456,-806,1000,209,-173,11,336,-288,226,-97,-406,44,-524,655,-1000,1000,168,968,-786,-45,836,1000,1000,83,803,1000,-300,-544,1000,1000,-262,-661,-384,574,-453,-152,490,-101,-882,-436,936,-542}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00686() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{914,1000,-117,139,-431,-67,136,232,-4,1000,-90,798,774,-992,-559,6,365,1000,684,-224,-639,-1000,-750,460,219,-1000,682,837,-573,-297,226,758,1000,-38,-524,189,276,1000,-1000,-849,590,-45,281,1000,861,685,779,1000,-300,-544,202,1000,-1000,692,-464,239,-65,-456,490,1000,-1000,-436,478,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00687() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{830,-568,-188,149,-73,454,652,1000,-1000,-182,377,-366,622,-947,696,200,-76,-429,-337,865,-909,1000,714,-163,-660,-266,704,-151,730,479,-173,453,687,-375,-478,265,1000,-999,-1000,-746,702,-346,300,-362,-932,-137,402,-538,-151,172,633,-611,358,762,570,-215,-858,-912,-1000,474,-1000,605,-121,679}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00688() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{-681,396,-588,752,138,-901,241,211,877,1000,-1000,263,831,-794,210,121,263,549,845,495,-547,-1000,-1000,-37,424,-799,910,-809,-703,479,920,-99,-31,-1000,-129,598,-793,229,-635,380,-790,-355,274,924,1000,367,-168,800,484,96,905,908,-1000,-993,-474,15,970,-388,316,584,357,400,1000,-520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00689() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{-1000,121,114,383,134,-603,-358,-304,511,-684,-1000,152,1000,-1000,-336,758,963,-54,1000,-1000,12,278,-1000,-470,939,426,-1000,584,416,-812,1000,758,281,-144,-175,1000,-1000,575,359,1000,-1000,-1000,641,1000,839,1000,1000,1000,475,575,1000,1000,-769,-438,-495,1000,733,-961,-1000,1000,-416,996,673,-288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00690() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{-447,661,-1000,269,702,-168,481,-491,118,340,-300,590,609,168,995,-754,-15,166,-997,583,186,423,-10,680,-898,467,-251,-1000,-850,493,676,-1000,-731,-1000,643,-1000,-518,-482,-581,-55,-535,217,314,-150,-43,272,-452,-639,-426,365,177,796,100,50,698,254,-538,681,-25,-615,1000,-110,33,-176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00691() {
        org.junit.Assert.assertEquals("VOID|getMillis=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillis(long):void",
            new int[]{-580,605,-239,704,278,-1000,371,-966,802,777,-1000,-335,796,-1000,975,280,292,240,563,-519,-494,-871,-1000,-1000,383,907,-900,-703,-115,-96,-1000,-378,-323,-714,327,141,-1000,871,130,705,-937,-694,513,1000,1000,382,-1000,580,1000,759,1000,1000,-959,-836,108,1000,-766,-451,-631,-1000,-1000,258,959,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00692() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{28,-430,401,-1000,-461,157,-517,-1000,22,-636,1000,1000,233,-138,1000,1000,-401,661,877,-1000,816,-203,-914,-1000,226,-918,-140,512,-812,515,98,620,8,-48,580,-59,-469,-862,-458,-238,-334,-41,-733,-777,-259,215,-924,631,334,-282,1000,618,862,203,-669,412,-627,398,7,1000,153,-723,-52,-78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00694() {
        org.junit.Assert.assertEquals("VOID|getMillisOfDay=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{566,-792,509,802,915,-1000,-1000,295,-341,753,-1000,-1000,918,1000,-287,-1000,1000,396,120,1000,37,109,-1000,-810,-1000,171,1000,-552,214,1000,-693,-862,767,249,976,1000,344,1000,-989,562,1000,-329,822,-1000,-1000,374,447,-732,-1000,-1000,1000,-1000,-1000,-625,-484,-317,-1000,-305,567,-1000,1000,-1000,-443,-384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00695() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{348,229,505,48,-131,-499,-946,166,-982,-304,-988,-343,-432,32,488,-656,261,-671,7,394,-944,-442,-754,-492,-851,213,408,-826,-535,657,-165,787,-785,-405,736,-9,123,378,-874,-278,969,-27,419,-961,-806,141,-482,-911,-485,-412,688,-677,-311,-306,-986,-578,921,-217,840,-695,718,850,219,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00696() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{-496,-929,60,458,-272,-659,-648,804,823,525,-1000,493,-549,1000,-249,-1000,26,248,41,825,-413,361,-580,-252,-1000,-484,5,-912,209,27,161,-1000,331,157,-219,1000,658,369,-471,676,-553,-17,-855,-1000,448,901,472,-381,-1000,-1000,-400,-1000,-1000,-420,-581,-554,781,1000,-630,-1000,733,-323,-77,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00697() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{-191,-647,618,919,347,-178,-294,351,-297,779,994,-484,142,-216,554,-597,911,870,-307,432,237,163,-873,-528,-95,399,720,373,-105,777,236,145,-489,621,-228,455,-599,868,-216,211,-58,528,-28,-345,-464,-27,-333,-381,-425,-871,-177,-640,-947,491,228,765,-911,232,-850,-409,21,-355,243,-82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00698() {
        org.junit.Assert.assertEquals("VOID|getMillisOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{1000,288,664,52,-466,-452,-757,499,16,679,463,-490,160,-786,556,453,-123,-1000,374,-925,-112,-124,-633,-380,-585,909,-718,-361,-578,707,176,1000,506,-475,709,-527,1000,641,-869,-343,-487,37,1000,604,184,244,-893,-43,-325,782,1000,377,-80,-51,-1000,-898,-84,-1000,539,-595,273,1000,-327,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00699() {
        org.junit.Assert.assertEquals("VOID|getMillisOfDay=java.lang.Integer:MTAwMA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{-100,75,939,-559,-1000,-524,-264,-1000,157,224,794,1000,56,-73,799,1000,851,-753,1000,-906,-322,712,-853,-1000,1000,-1000,0,1000,-1000,-1000,-189,757,592,-373,1000,-897,213,118,-110,-740,-1000,564,-431,-292,434,1000,-1000,67,1000,666,1000,172,1000,-585,-1000,1000,150,576,669,1000,-1000,571,1000,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00700() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{-506,-895,645,484,266,661,1000,-529,202,227,1000,-1000,396,14,913,363,-232,1000,1000,-898,-489,-279,948,-505,-195,1000,332,99,136,1000,221,1000,-822,292,-1000,323,-655,-91,155,257,-703,113,-1000,-67,-794,-217,241,603,-1000,-446,149,-723,-1000,921,779,762,-1000,-790,-1000,1000,305,2,-436,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00701() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{97,-352,490,-732,-405,-186,-362,-588,-204,-482,400,616,-683,204,450,400,-354,557,507,-400,-58,-51,-491,804,-142,-738,33,201,-229,-1000,75,476,-21,264,1000,259,-510,-321,-462,-131,87,201,-314,-756,-270,451,487,358,-23,-472,703,1000,303,240,-769,-337,228,173,-275,400,-893,-146,4,-355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00702() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{-248,400,548,-681,-954,438,-307,-453,-1000,-896,-501,551,-820,-35,1000,-65,-547,777,-155,-131,54,-1000,-1000,-491,-81,-698,-175,-225,-1000,47,253,932,-768,280,957,-520,-863,-905,88,-537,-157,680,160,-468,-513,447,-714,17,930,1000,1000,236,-1000,172,-1000,273,1000,-1000,508,279,257,538,532,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00703() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{-529,-84,282,878,256,-297,-901,888,-315,676,891,-322,-1000,1000,-144,-1000,845,-1000,-974,1000,-1000,-425,-161,125,-1000,627,274,-1000,294,114,181,-29,-1000,750,944,642,-771,835,-522,591,1000,761,208,-1000,63,81,381,-1000,-1000,-1000,192,-1000,-398,-337,-907,-412,1000,570,-417,-1000,1000,1000,385,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00704() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{-730,-973,571,1000,689,913,747,816,-204,367,-671,283,-1000,-1000,206,-1000,-837,99,-418,-335,297,126,-776,913,-798,-1000,-1000,-839,966,326,1000,207,-867,645,-459,172,-1000,51,606,25,-124,326,149,439,203,-662,652,-147,846,162,930,1000,908,1000,-413,168,-71,533,-1000,-1000,-1000,-858,-618,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00705() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{-939,-430,399,978,284,157,1000,-497,577,529,-816,909,233,-1000,1000,549,-401,-1000,-301,-1000,-1000,845,1000,1000,157,-918,-1000,-74,-553,-394,1000,445,96,-608,-240,-1000,332,-862,1000,-415,-587,-41,36,1000,1000,85,-123,1000,1000,1000,931,1000,1000,913,-445,-437,104,-375,-1000,437,-739,-327,68,-78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00706() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{457,569,801,-72,-316,-372,-564,-772,27,457,-42,-923,567,-382,298,345,1000,-909,-5,-42,-58,703,-891,-851,965,-838,1000,1000,-126,-157,-7,1000,1000,198,151,-550,-354,1000,-895,479,-1000,-4,220,373,376,423,-498,-295,846,266,901,268,533,27,-506,253,228,-473,138,148,-799,68,499,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00707() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfDay(int):void",
            new int[]{462,-755,0,-106,-272,-960,-1000,-985,269,0,1000,1000,1000,382,736,1000,990,647,26,-1000,299,1000,-1000,-1000,275,-744,492,1000,-1000,-114,0,140,720,539,521,-5,810,545,-849,118,-553,821,-241,-780,240,1000,-1000,-417,967,235,0,-1000,1000,0,-471,936,-441,475,669,1000,-739,0,689,279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00708() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{-1000,-870,-146,-1000,-1000,830,937,-19,102,-258,200,1000,-1000,-673,-549,250,603,1000,-1000,-1000,1000,-1000,783,149,130,-175,-1000,-45,-171,-195,257,-1000,641,-280,95,-806,1000,-71,362,-3,166,387,-358,-1000,850,47,655,660,-1000,-7,-1000,-196,-210,574,135,321,109,-369,1000,400,-252,448,-767,931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00709() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{-1000,-603,884,-446,-95,-696,727,-337,40,-562,29,-190,1000,-803,146,-788,316,-761,-232,-464,296,-482,366,-763,-91,687,-344,-1000,341,-1000,1000,-206,1000,971,1000,-61,-255,114,613,-825,1000,555,-363,-681,1000,-745,-311,-47,-463,176,109,-1000,845,124,82,271,-1000,-27,246,-885,-717,628,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00710() {
        org.junit.Assert.assertEquals("VOID|getMillisOfSecond=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{299,-824,111,794,-80,538,-545,1000,253,320,620,1000,-933,79,455,889,310,158,73,-770,299,1000,-1000,120,488,-814,-1000,230,-1000,701,-1000,-1000,-57,-265,1000,485,1000,-1000,-353,208,-1000,959,-203,-649,-36,-920,-489,1000,-985,-99,638,284,-393,-637,1000,-933,550,-1000,-2,153,-121,1000,937,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00711() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{-52,-633,27,-280,-421,-410,-147,-71,-82,8,109,280,859,-1000,347,668,449,-247,262,-1000,-121,-393,-481,-575,738,1000,-880,-849,-979,-637,111,-727,-559,1000,1000,785,393,-735,714,-1000,1000,1000,-343,-911,836,-1000,-1000,279,-1000,-868,1000,-850,965,160,497,-1000,-660,-952,1000,-1000,-523,742,133,967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00712() {
        org.junit.Assert.assertEquals("VOID|getMillisOfSecond=java.lang.Integer:NjE2", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{77,-1000,65,-435,-337,644,41,-1000,51,1000,-389,325,185,-24,-857,650,-797,377,-17,-1000,894,-1000,952,612,303,1000,-237,551,421,-1000,747,-1000,-256,1000,-774,607,626,-356,1000,-92,1000,736,54,-734,1000,-841,495,410,-1000,102,-481,-440,153,632,-312,-1000,-407,-913,1000,-1000,-87,932,-445,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00713() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{-483,-268,1000,-76,-21,679,-894,64,71,547,-662,658,-431,-29,-851,-236,212,574,-383,-1000,1000,-214,-212,49,-1000,410,-704,311,832,103,-286,-1000,1000,1000,1000,-248,-812,-621,171,-69,204,810,-706,-281,1000,-1000,-55,-48,-525,502,-142,-793,716,191,-453,-1000,-882,-932,201,-697,414,1000,-622,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00714() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{-727,-317,168,-1000,188,1000,403,126,-69,944,-730,446,-630,-60,-857,-931,-431,912,-945,-86,1000,-316,952,231,616,-400,-110,383,1000,-230,272,-630,482,578,-418,-1000,-668,286,807,74,126,226,-138,-392,1000,1000,1000,-347,-276,-526,-1000,-818,-417,-230,-1000,247,-249,-15,-206,483,296,831,-1000,-273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00715() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{-487,13,611,294,571,-954,445,949,406,-1000,136,768,598,-610,-238,-181,1000,-1000,-188,-279,97,477,-1000,-361,-883,-924,-1000,95,-1000,-1000,-1000,-1000,83,1000,49,231,535,-430,117,-677,-1000,793,-95,-1000,1000,-1000,-640,1000,-930,838,688,1000,440,965,766,-703,68,-1000,31,-481,-521,1000,1000,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00716() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{1000,-589,-99,928,14,49,377,423,67,-26,89,-620,643,-543,-304,-110,544,579,343,-451,-187,1000,-615,-80,617,-609,-1000,311,-106,-257,-1000,90,305,-572,-197,97,882,-259,374,-448,-892,1000,-23,-562,-446,166,-82,242,-1000,-433,-399,288,0,-385,710,423,254,-570,155,394,-1000,212,903,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00717() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{236,-80,-336,-657,161,-486,3,294,368,-86,131,64,645,-145,-735,62,1000,-478,-239,-82,-192,946,133,-341,-1000,387,137,316,40,-148,400,-816,514,831,117,-225,434,400,-14,-723,-335,-174,257,-124,391,409,-528,534,506,-527,-693,23,1000,-457,256,339,-810,251,-549,169,391,-173,-597,-891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00718() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{-1000,-110,730,-1000,-516,1000,-481,1000,233,264,-414,954,-957,553,-1000,-1000,810,258,-1000,164,1000,-472,-566,-128,75,-578,-301,819,1000,931,-595,95,1000,-202,448,-846,-948,-513,-526,505,-759,-66,485,0,1000,576,1000,139,369,433,-1000,-735,74,-133,-704,-290,-542,-704,-277,242,523,932,-817,542}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00719() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{-392,533,-31,532,-307,762,-159,1000,48,422,-469,179,-431,136,-304,69,330,984,-523,-489,1000,93,-212,294,-665,-254,-727,-24,31,448,-768,-366,635,-65,-13,-854,-945,-204,571,343,-852,788,-384,-450,731,-344,681,-38,-758,68,-463,286,129,-267,-730,-448,-169,-576,-216,-184,-445,633,-94,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00720() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{-1000,-792,563,-676,-796,-404,578,558,762,-893,611,1000,-1000,958,-598,-1000,1000,-114,-1000,26,1000,-105,-954,-24,-1000,-1000,-829,95,802,379,-562,-1000,1000,-906,1000,-432,54,-1000,-1000,1000,-900,-192,-329,-409,1000,1000,569,939,86,350,-1000,-1000,-2,-302,265,533,-592,-1000,-719,420,104,1000,-1000,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00721() {
        org.junit.Assert.assertEquals("VOID|getMillisOfSecond=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{264,746,727,794,559,-897,-213,-134,-156,746,-858,-875,1000,466,-42,-480,-167,-179,26,-255,402,424,366,-33,-437,297,-377,-144,473,-707,312,197,-39,336,-1000,-809,-937,887,817,-384,589,959,163,28,363,-920,983,-538,6,546,-424,284,1000,7,-636,-933,-703,481,-533,-1000,-509,341,-406,187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00722() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{628,-792,892,-676,1000,479,578,558,-50,1000,-1000,-327,588,490,-934,-1000,-1000,39,821,-521,333,131,499,685,-798,1000,586,95,870,-1000,612,-1000,-268,1000,-711,260,-1000,349,974,-93,1000,991,-329,381,1000,-1000,-386,-284,-552,350,567,-1000,541,185,-646,-1000,-1000,-264,191,-1000,335,925,-422,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00723() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMillisOfSecond(int):void",
            new int[]{-671,648,29,-588,-321,8,68,-872,272,-273,228,625,-591,-472,-608,-373,820,965,-600,709,-535,-602,825,579,39,-769,-945,455,680,383,543,-337,755,521,1000,-62,-450,265,-214,335,171,-770,-226,140,50,-411,-436,-276,56,621,-914,899,424,655,340,985,-565,-280,320,573,137,-728,-998,-269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00724() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{887,-19,254,704,781,499,-514,963,617,-1000,-646,1000,372,935,1000,904,-1000,-32,396,-678,-1000,463,1000,892,1000,-1000,-960,-1000,683,801,-11,-982,-1000,95,1000,-1000,627,-826,-769,-948,623,1000,-97,-1000,-973,-168,169,-341,-1000,-67,59,-413,898,-820,517,1000,-1000,483,-1000,-245,106,-227,129,-806}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00725() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{-930,220,-663,363,607,-163,-421,618,-674,762,1000,-748,-829,895,-930,-888,1000,761,1000,-741,-1000,-1000,-234,-171,763,-1000,-558,135,-314,670,-1000,-676,654,103,112,907,-1000,-70,212,107,-70,-1000,-1000,450,-348,1000,86,-690,1000,-1000,-16,592,632,-470,-1000,-1000,1000,416,84,-268,-996,-482,-1000,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00726() {
        org.junit.Assert.assertEquals("VOID|getMinuteOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{444,-85,462,-911,-914,426,743,797,-787,-615,-253,770,127,-307,156,-949,396,-629,-822,-896,326,727,-110,362,-792,-439,696,-309,-780,151,50,200,284,-342,28,986,-870,497,336,586,-619,-808,-108,182,-474,-684,273,-39,362,196,-26,-629,646,260,719,-476,-466,117,836,-510,692,-208,111,-209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00727() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{179,-531,179,403,-207,-6,-432,-455,-720,564,-574,-454,305,185,857,457,-798,353,-295,475,976,1000,-791,265,127,627,-70,39,-883,827,1000,-763,849,-760,18,1000,-897,806,-843,500,-1000,-797,-491,-228,-47,-694,-612,1000,-475,-520,476,491,463,-477,-210,-345,897,-659,965,-73,80,-434,-653,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00728() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{389,175,-663,199,-1000,-720,-931,320,1000,762,-53,-556,663,-594,290,-1000,1000,504,6,-948,522,-375,-108,-1000,543,-92,93,762,-239,-498,1000,-40,204,1000,208,-1000,-92,42,1000,-1000,-712,-291,163,-182,-36,756,-656,-320,-655,1000,-330,1000,-527,662,-1000,-788,1000,1000,637,-176,-687,-366,61,-923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00729() {
        org.junit.Assert.assertEquals("VOID|getMinuteOfDay=java.lang.Integer:MTAwMA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{1000,-627,403,704,-168,-715,-201,-1000,495,720,-269,1000,1000,935,1000,-319,991,211,892,-600,1000,1000,209,892,-1000,-347,1000,747,-1000,660,-11,-89,869,638,900,-1000,627,20,737,-1000,-1000,-1000,1000,1000,-645,-1000,169,-860,-370,516,-1000,1000,325,1000,-1000,-785,-1000,1000,225,-347,53,-692,1000,343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00730() {
        org.junit.Assert.assertEquals("VOID|getMinuteOfDay=java.lang.Integer:MTAwMA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{1000,-893,242,588,274,-459,-733,314,311,-93,-424,157,1000,392,1000,1000,-735,145,18,537,-302,1000,155,1000,-752,-657,-911,-817,-132,119,749,-599,1000,-1000,222,1000,-356,700,-963,-913,-446,400,594,-400,-290,-1000,536,90,-1000,51,970,143,566,-1000,897,683,-956,-77,-170,-1000,-539,477,920,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00731() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{1000,-854,267,578,-504,-435,-885,298,-1000,1000,-84,-995,1000,-512,68,725,-695,557,281,677,-424,1000,209,1000,-751,-531,148,-762,-5,-151,317,-423,766,-1000,49,169,-679,104,-1000,1000,-250,59,148,-861,866,-1000,414,-148,325,-563,1000,331,-614,-1000,839,245,-1000,-390,-363,-1000,1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00732() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{25,122,-915,-36,282,-241,-411,621,99,12,871,-549,269,161,-283,-145,728,98,754,-932,-57,-754,707,68,-331,-922,71,166,-642,380,-288,76,865,252,362,433,-901,765,969,-94,-208,-805,-591,322,-348,772,232,-415,822,-966,775,502,794,9,-775,-855,704,717,50,-144,-707,-237,-415,-399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00734() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{1000,306,-622,-621,-1000,-268,-97,-396,601,-796,525,1000,-379,687,80,-1000,818,-409,547,-38,-1000,-982,-80,-382,1000,-993,42,-469,-152,469,696,-170,-1000,1000,867,-250,420,-263,981,-1000,198,-1000,-646,490,-1000,417,-903,-227,-774,422,-1000,1000,-385,1000,-848,-230,758,897,-554,509,-994,-1000,-455,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00735() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{-112,385,-84,298,-872,-669,-943,993,306,1000,1000,-776,422,-134,85,-1000,898,107,892,-932,306,-754,1000,785,79,-1000,580,560,1000,633,1000,703,865,1000,362,-214,-901,765,1000,-561,648,927,15,-665,-348,14,-869,305,-24,114,412,153,330,-1000,-775,837,1000,1000,-1000,631,-1000,1000,302,254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00736() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{-115,602,-1000,535,261,259,-139,112,167,-155,793,-515,699,393,-77,-1000,1000,570,1000,-1000,-425,-1000,530,-578,-130,-1000,-195,53,-607,1000,-438,-554,-396,1000,1000,180,-670,17,336,-1000,117,-1000,-702,-133,-210,1000,282,-592,1000,-1000,-136,700,527,380,-857,-1000,622,830,-791,211,-1000,-1000,42,-345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00737() {
        org.junit.Assert.assertEquals("VOID|getMinuteOfDay=java.lang.Integer:MTAw", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{-640,33,-45,199,582,191,498,618,336,-998,-216,1000,586,529,8,-1000,142,187,1000,-1000,-801,-1000,1000,79,163,-639,495,-898,-660,506,-445,-66,-1000,1000,980,-1000,1000,-78,336,-1000,709,1000,56,-845,420,-236,250,-283,-424,1000,929,19,-106,984,-446,780,-171,1000,-1000,767,-1000,-957,-400,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00738() {
        org.junit.Assert.assertEquals("VOID|getMinuteOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{742,-888,-16,-572,468,435,-423,-409,90,-612,-317,851,270,566,444,-888,138,-716,-488,449,-75,1000,-1000,280,-767,-307,-116,550,-1000,1000,787,-676,174,-478,319,1000,-1000,393,181,-1000,-1000,-1000,-452,117,-1000,-501,-494,747,-1000,342,2,-63,-106,406,-369,-10,514,484,1000,348,-404,-816,-998,-923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00739() {
        org.junit.Assert.assertEquals("VOID|getMinuteOfDay=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfDay(int):void",
            new int[]{-284,-59,13,307,1000,-34,-360,583,-627,-863,166,937,70,1000,429,264,-104,412,1000,268,-1000,-127,1000,527,-617,-1000,-1000,-964,-227,1000,-1000,-157,-1000,95,1000,-1000,568,335,-489,431,321,1000,-138,-1000,-1000,-168,560,-659,-508,-1000,458,-413,1000,237,-9,715,-789,560,-1000,177,390,-514,-47,-453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00740() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{-122,-1000,-314,42,1000,-346,-722,-212,524,960,493,150,-514,1000,1000,845,23,-905,319,-1000,11,-1000,-777,1000,595,-462,441,729,-683,-642,597,-866,-290,-1000,-854,170,130,127,606,-1,1000,-1000,125,-887,616,1000,559,683,-965,-53,-165,684,414,838,-41,1000,934,415,597,-552,1000,1000,305,-189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00741() {
        org.junit.Assert.assertEquals("VOID|getMinuteOfHour=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{761,151,-151,-389,-162,61,-715,400,429,-199,48,198,-384,-297,-270,-120,-400,-151,-624,-273,-8,-546,-389,-824,-261,347,451,644,-190,-37,-385,-616,185,-645,193,-165,-134,-122,-17,82,-97,-251,714,-38,-554,492,-46,966,1000,291,111,172,-193,-267,-548,527,89,-185,-927,682,1000,-346,-444,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00742() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{823,-783,-146,-1000,438,170,367,208,1000,-798,1000,814,-739,123,1000,771,929,-940,-1000,-537,-158,-473,855,660,-415,450,17,728,189,-779,-662,-444,163,-1000,-219,607,355,104,1000,-1000,-615,-1000,1000,-1000,663,-36,232,1000,-636,-183,159,44,576,-68,-273,978,892,-1000,-47,50,1000,496,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00744() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{31,-725,-624,513,316,1000,337,-458,995,521,-197,546,225,-544,1000,415,1000,-80,-115,-113,-1000,-710,359,1000,-1000,185,-767,917,774,-575,-1000,925,-1000,-958,-709,-858,49,549,118,793,-57,773,-359,-613,-6,-1000,830,-318,-1000,-455,-192,-313,-591,453,-178,322,-1000,-127,260,-383,-705,1000,-83,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00745() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{-876,-288,-661,-550,-1000,791,57,337,326,-180,535,413,25,1000,93,288,418,-598,-672,1000,-539,233,319,333,539,-161,26,-421,1000,1000,-117,77,899,193,1000,1000,1000,764,1000,-1000,685,297,14,178,1000,-603,482,978,-1000,1000,-1000,-548,1000,-1000,-478,329,360,789,37,254,853,-362,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00746() {
        org.junit.Assert.assertEquals("VOID|getMinuteOfHour=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{694,147,-431,-439,-1000,1000,-125,924,894,-612,-927,-359,1000,-1000,-407,568,-288,1000,-448,853,1000,299,-214,11,499,1000,612,808,1000,208,-494,45,1000,439,941,-167,679,387,64,-27,-308,1000,-94,-133,-1000,-971,421,-227,1000,-635,-71,-1000,-332,-792,-1000,286,-442,31,-899,1000,803,-499,-275,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00747() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{1000,568,-336,637,867,26,-613,536,-671,443,-743,-110,489,-444,-705,-676,-149,51,465,-48,320,-767,113,-842,-660,-187,-285,199,-353,-309,-289,159,-607,-812,-959,-731,-1000,-516,-836,957,447,455,-693,-691,-971,-495,421,-622,1000,-1000,65,173,-1000,999,-661,-601,507,-40,-751,-560,-897,156,519,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00748() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{-136,-1000,34,860,1000,-716,-1000,-835,974,123,-639,-283,-894,1000,-119,141,726,-1000,704,-1000,401,-1000,-254,357,-805,-958,941,1000,-21,-742,1000,-1000,-1000,-1000,-1000,257,-1000,549,52,238,1000,510,-500,167,749,1000,431,1000,-1000,438,-276,1000,-608,1000,844,1000,743,-96,496,-1000,789,761,822,362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00749() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{563,-1000,-403,227,195,828,-782,-241,463,-546,5,447,534,-308,38,324,853,1000,-734,-617,189,-956,-300,828,1,1000,26,391,190,-586,524,-760,470,-842,161,-51,-137,1000,624,-977,-311,-803,-490,13,-662,385,1000,204,-748,-848,-207,-1000,-23,415,281,1000,-218,-820,-434,438,1000,-1000,-465,-999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00750() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{638,-571,927,-212,196,-1000,261,-624,1000,256,-79,1000,-542,-230,617,55,1000,-904,-985,359,-591,-308,589,51,-666,421,255,1000,165,-404,-878,205,-666,-1000,-279,190,-212,-702,532,-956,853,328,173,250,-168,-523,-73,673,-815,-348,384,132,543,-640,88,637,-412,-603,-218,-169,127,-315,-898,-731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00751() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{-399,-1000,427,-1000,62,624,-621,-670,1000,-203,423,971,306,-338,-360,-243,-668,559,-1000,-675,-587,-610,-22,1000,-150,498,1000,500,-243,104,500,545,36,-868,-192,-374,539,1000,272,-469,-993,-619,370,349,-452,1000,282,1000,-597,820,-296,56,93,-177,-442,897,938,-560,-885,863,1000,205,-943,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00752() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{1000,-853,-77,-1000,-218,-723,-490,18,196,-63,259,487,-568,-668,1000,-1000,-501,-11,-31,-1000,320,-1000,454,-29,-1000,-1000,-97,469,-1000,-659,179,-244,-668,-1000,-1000,-1000,-1000,332,285,1000,-400,-1000,-577,-466,-471,344,367,33,675,-241,152,918,-1000,1000,-680,702,1000,-1000,-751,-893,1000,1000,-94,-151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00753() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{383,319,-314,234,541,-676,-710,-284,-276,985,-538,165,330,-515,131,845,111,-337,424,-1000,-163,-871,293,-1000,-922,-615,-275,642,-683,74,-503,500,-881,-237,-766,-1000,-891,-1000,-1000,962,1000,1000,-953,571,-1000,1000,12,-566,622,-53,339,567,-1000,586,-722,-902,-819,219,-711,-622,1000,-206,305,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00754() {
        org.junit.Assert.assertEquals("VOID|getMinuteOfHour=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{298,25,-403,227,466,494,-7,-209,884,-29,-88,310,534,-308,724,459,853,-76,-493,-352,-539,-956,-300,651,-969,937,26,668,-48,-758,-739,-81,-352,-842,-364,-648,201,764,511,-447,-723,90,327,-473,-755,-444,763,653,-127,-201,564,-155,-1000,415,-478,687,-805,-316,-236,174,544,939,-324,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00755() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMinuteOfHour(int):void",
            new int[]{660,-10,-244,469,-234,259,-354,375,186,187,-506,-32,709,-697,-155,21,904,-549,230,501,1000,-301,-1000,438,-218,602,189,321,-813,-45,-162,-79,-22,-605,-155,-98,289,-166,117,146,757,798,-129,-320,-741,704,657,541,613,215,-16,-823,-297,49,-714,896,603,462,-664,190,613,969,-366,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00756() {
        org.junit.Assert.assertEquals("VOID|getMonthOfYear=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{182,16,223,-734,-352,419,579,221,-280,-355,-90,-400,-359,805,-67,-52,-959,400,939,-1000,-931,134,1000,381,-380,626,-341,1000,773,-361,-744,-139,353,-21,557,209,-868,-144,-638,-296,145,-1000,75,1000,126,-594,137,1000,-1000,-764,768,23,1000,-1000,-1000,446,-1000,234,111,-215,160,137,-91,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00758() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{-371,-317,-649,-842,473,250,393,215,1000,-1000,13,-1000,-169,418,199,-1000,-742,14,1000,-924,400,245,1000,446,274,667,737,1000,50,-456,-1000,-1000,328,972,-208,-143,-1000,-127,-518,256,-1000,-1000,567,1000,-365,439,-619,147,-253,-278,768,-647,297,-1000,501,-914,-1000,146,-1000,-1000,-175,-1000,151,-381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00759() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{1000,-141,-211,-726,-1000,-860,1000,183,-878,985,-1000,-799,-1000,1000,-1000,696,-117,-703,411,-779,983,-546,517,-1000,636,1000,-263,1000,1000,38,1000,-1000,1000,607,763,919,1000,-1000,871,-409,337,-667,1000,87,-1000,246,968,164,-1000,-1000,-582,-295,1000,-700,1000,-934,-1000,1000,166,-1000,825,-790,961,-583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00760() {
        org.junit.Assert.assertEquals("VOID|getMonthOfYear=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{281,-680,-939,768,-446,537,62,106,-545,219,-980,460,-83,486,576,-48,-727,993,378,392,151,964,725,-69,-106,406,767,-191,-769,-20,-233,-308,489,640,-251,-780,-762,-684,-227,-897,947,-291,-3,-578,954,-458,-253,-412,-149,-494,-145,-141,-742,587,-303,270,870,-561,-449,-632,663,52,-829,227}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00761() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{47,-603,-741,-734,503,1000,-605,762,-1000,-993,-90,1000,311,266,-139,-1000,-383,919,216,126,1000,893,912,841,-201,667,1000,339,-712,-726,-1000,336,-177,1000,-717,-999,-1000,-30,-956,-909,213,-1000,-203,951,571,-77,-683,295,852,-61,1000,-259,-885,-1000,-406,-1000,-483,276,-387,-921,272,451,-864,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00762() {
        org.junit.Assert.assertEquals("VOID|getMonthOfYear=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{1000,206,33,979,-966,208,-280,803,116,989,180,625,225,1000,293,-1000,517,74,-441,878,-136,456,516,121,746,-18,-768,1000,-239,830,933,-1000,305,-305,-603,-1000,374,-201,1000,-761,872,-779,-434,-566,-611,-636,68,-1000,36,-70,-714,43,-412,371,-10,-539,464,550,-179,-95,904,-251,-126,444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00763() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{244,-455,689,-976,-110,168,-510,-101,763,179,-552,28,-976,438,1000,-610,-690,378,191,301,249,1000,591,-216,99,-415,7,-546,28,303,223,-150,-598,272,202,-322,-1000,180,214,106,-23,-253,-1000,-379,-30,-579,-354,-470,-939,-274,-360,-834,-403,782,-14,464,722,-106,-387,-470,307,-623,-798,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00764() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{-496,-482,-386,114,535,405,436,-361,576,-443,-1000,-16,-273,36,1000,-799,-469,707,953,-82,-645,998,-313,-96,-105,-813,-237,527,-705,234,-183,492,-17,-230,220,-1000,-768,634,-817,418,-66,522,-309,-569,1000,337,934,-93,-196,184,-224,20,-478,940,-666,375,1000,-987,-1000,-360,-370,157,-536,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00765() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{-193,294,-621,-1000,-1,1000,-81,935,-280,-1000,547,-68,213,291,-9,-52,-528,430,390,-572,-754,102,106,1000,582,393,-160,715,319,-814,-1000,349,-471,-21,-276,-82,-868,536,-698,-304,-248,-1000,-640,1000,286,-1000,-329,924,-485,-106,1000,542,499,-1000,-946,-547,-1000,1000,287,-159,281,839,-411,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00766() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{-110,-377,-594,-478,340,926,116,327,-1000,-1000,-90,668,656,1000,-61,-1000,-1000,1000,970,-1000,291,1000,1000,638,-1000,626,833,1000,433,-754,-1000,309,383,1000,620,-937,-1000,-144,-1000,-972,596,-1000,521,1000,1000,-1000,-148,1000,-380,-944,1000,-115,395,-1000,-241,-577,-1000,-490,-659,-1000,-54,451,-1000,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00767() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{725,-830,350,-780,-991,-94,93,72,-1000,91,-1000,783,-236,1000,-1000,-1000,-1000,313,876,-618,930,-173,1000,-235,-220,1000,-683,1000,434,-65,-841,-492,1000,1000,447,332,-886,-855,-471,-788,538,-1000,960,1000,-543,1000,59,529,269,-1000,439,-569,117,-1000,-14,-1000,-1000,1000,-382,-1000,482,-709,-122,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00768() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{760,117,-461,167,247,1000,784,1000,-114,744,400,-919,341,541,289,-706,-577,955,384,-1000,144,887,856,780,-49,30,1000,800,-253,-1000,-671,334,-706,669,-515,-1000,-722,179,-152,155,-646,-637,-463,56,1000,-261,-714,478,-691,-41,-184,-38,-298,-1000,1000,-953,-1000,233,-520,-1000,610,849,-67,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00769() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{-1000,139,-1000,543,52,419,579,908,746,-1000,-90,1000,1000,-981,1000,-190,424,1000,-1000,1000,-743,1000,688,1000,-574,-1000,969,-1000,773,-536,-1000,-139,-1000,400,-1000,-1000,-1000,1000,-998,-1000,913,-147,-1000,-395,1000,-1000,137,-827,1000,1000,1000,400,1000,-102,-1000,-42,1000,234,-380,1000,507,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00770() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{865,-717,-886,-715,-807,1000,-982,-360,-285,419,-699,856,311,-375,-358,675,851,-566,-465,1000,655,851,471,183,912,643,719,-917,-712,94,319,-700,114,1000,-1000,-67,575,-900,924,-1000,599,-954,-203,-278,-669,-77,-644,-722,1000,229,167,-941,-1000,-1000,-28,-660,926,369,-284,-577,1000,-743,11,875}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00771() {
        org.junit.Assert.assertEquals("VOID|getMonthOfYear=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setMonthOfYear(int):void",
            new int[]{1000,-303,434,144,-904,-161,477,-71,-1000,727,-727,293,-46,1000,-562,-1000,-803,313,321,-162,59,412,1000,-639,44,502,-325,1000,416,452,-45,-552,1000,998,427,-752,-188,-823,-141,-689,997,-1000,839,378,-543,556,289,-17,-449,-949,-12,-320,166,-1000,352,-137,-1000,550,-157,-976,851,-687,-100,408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00772() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{-733,-1000,198,-1000,1000,4,1000,161,1000,-300,193,-65,1000,1000,380,-1000,-1000,447,400,-1000,425,-540,777,-717,1000,124,-868,-1000,-565,1000,-810,-298,542,1000,3,582,659,-50,-946,-97,493,-53,62,-1000,-202,1000,481,-1000,-1000,-990,-634,-135,-1000,-117,-154,1000,601,533,-266,-1000,1000,-1000,572,83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00773() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{418,602,-706,724,-253,544,-919,400,-1000,339,-242,-1000,-861,-765,-459,-254,1000,-1000,20,639,-247,122,671,-26,-722,-1000,113,-592,-160,-1000,711,-125,-202,893,927,1000,584,-315,773,1000,-1000,-1000,-608,355,-728,-865,551,1000,453,-1000,67,1000,-880,-728,-713,-287,532,-32,-313,77,-440,405,-1000,-578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00774() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{-470,-178,977,-1000,84,-337,-500,-145,-462,-1000,-1000,-246,379,-855,249,-11,389,197,-1000,692,-127,-193,725,33,145,-501,-567,146,31,526,-134,705,1000,461,-1000,171,-1000,830,100,-1000,-369,-110,441,1000,-464,643,477,-939,3,-420,82,-428,-1000,113,-7,533,-539,1000,1000,-947,-598,-593,433,-171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00775() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{-1000,-756,879,687,1000,-492,142,-344,-293,-957,-1000,-559,286,-585,284,-587,-196,-314,412,-169,-1000,-1000,515,-924,846,-308,-575,483,91,893,-145,851,380,1000,-923,338,343,-511,-1000,-678,122,-304,230,1000,-941,1000,36,-1000,-260,-1000,-574,1000,140,-1000,-362,907,1000,-822,80,-967,-261,-1000,301,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00776() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{673,-1000,448,518,1000,-192,300,-218,578,-369,-992,-622,43,-333,33,-231,-1000,573,184,-1000,30,-381,546,-446,391,-233,-381,396,-1000,152,-810,-298,341,527,35,396,1000,-160,-496,340,506,-288,-327,-1000,-287,417,481,-54,-1000,-238,583,458,-115,1000,-154,-646,638,-708,-583,-1000,353,-398,572,-351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00777() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{-238,-1000,-706,-967,405,574,910,569,85,-374,128,-566,986,595,-70,-893,164,-710,400,-560,-247,-387,1000,-807,678,-177,-1000,-592,-565,898,-914,-58,124,893,165,1000,341,166,-173,464,-284,-138,-114,-885,-564,361,632,-347,-1000,172,-1000,212,-880,-437,47,569,372,826,-266,-1000,549,-275,-30,137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00778() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{101,-924,-296,754,280,-368,738,-169,1000,611,-108,489,-696,234,-555,-982,-137,-1000,549,-1000,-1000,-1000,584,246,435,-902,702,900,846,618,-931,603,-381,580,170,1000,869,-1000,-271,559,988,-676,-602,-408,-1000,296,-6,-539,-52,10,-1000,664,140,269,-448,1000,-266,-363,1000,447,979,-1000,-115,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00779() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{436,-353,-695,-28,784,116,997,350,763,384,-450,-679,-526,-460,-157,-764,357,-1000,825,-588,-537,-933,473,-114,274,-1000,-364,588,-393,79,-249,-67,-990,239,444,874,1000,-584,-620,1000,-382,-838,-627,457,-749,443,488,522,-304,353,-281,791,-728,1000,-786,-468,135,-421,-91,-33,63,-708,-138,-919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00780() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{-283,-178,736,-1000,84,188,858,588,-462,-1000,-641,-211,782,-810,140,-11,389,366,-1000,692,714,129,1000,-171,145,-68,-1000,-238,123,1000,-527,783,1000,461,-742,437,-1000,1000,138,-1000,-724,-325,534,4,-81,643,1000,-939,-673,-1000,-165,-461,-1000,-669,470,1000,183,1000,925,-1000,-549,-709,930,588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00781() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{-543,-796,429,118,551,169,316,-240,-913,-912,-272,-820,-739,-772,-235,455,-73,-252,-619,7,-707,-963,587,-526,95,-280,674,352,437,-414,-316,829,358,-644,49,920,-846,-469,-956,-624,-502,395,-493,697,-524,-370,134,-995,813,-969,-411,-436,742,-737,642,276,274,-220,-394,616,921,-390,460,167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00782() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{-271,-433,-235,875,296,644,285,1000,-785,1000,-1000,-1000,-1000,-1000,-465,728,969,722,120,-574,-1000,-1000,1000,132,-296,-153,-1000,1000,5,748,-635,1000,-378,533,539,1000,254,550,-669,544,628,-1000,-1000,1000,-395,-211,-595,-446,588,-1000,-313,1000,949,717,-502,1000,-22,-611,-20,-1000,-774,-644,-675,-887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00783() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{-1000,-1000,443,1000,497,-376,693,-886,877,874,-952,-19,-809,485,479,-677,-671,-679,1000,-1000,-919,-1000,343,772,194,-495,-80,386,-685,-258,-249,980,-693,981,562,76,1000,-1000,-1000,566,678,254,-801,548,32,1000,-333,-1000,-750,-667,801,839,539,754,-1000,1000,316,-788,92,-141,958,-614,-138,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00784() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{1000,-590,-559,606,-299,251,805,-417,-120,-159,-831,-354,-381,-572,-171,-404,834,-132,-570,-442,-97,362,1000,491,-186,-859,46,116,-724,581,-1000,762,-34,646,240,662,-298,350,24,655,215,-290,-233,909,0,-1000,-135,-1000,-505,622,-1000,330,-250,519,353,225,252,-1000,35,-708,167,-516,-1000,289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00785() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{884,-344,-74,424,-958,200,-1000,198,-552,-628,-1000,-1000,-226,-763,-181,506,-243,930,-1000,784,1000,991,777,-294,-609,-505,98,504,-1000,-1000,-173,-613,992,-724,329,54,481,879,904,464,-1000,-533,-52,1000,9,-824,935,958,-509,-594,702,473,199,-38,287,-698,724,-392,-1000,650,-177,798,-640,363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00786() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{1000,-1000,-723,-704,686,813,-265,-417,-365,-909,-1000,-1000,255,-1000,-759,298,-274,1000,-1000,-426,1000,994,1000,-712,1000,8,-1000,641,-1000,429,-1000,92,784,385,481,700,-1000,582,1000,584,-417,137,266,1000,-151,-617,543,-68,-730,-750,-462,899,-738,-689,1000,-907,409,-542,-1000,-1000,75,-618,-904,-163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00787() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField):void",
            new int[]{1000,-6,-98,-216,-697,39,-980,198,-552,-562,264,-1000,-120,-3,169,224,-243,618,-538,-658,836,997,661,31,-468,-604,655,121,-203,-854,467,-1000,611,-724,134,662,709,626,904,311,-417,28,199,-93,221,-732,320,958,99,-23,482,-74,163,419,-61,-161,673,206,-156,-1000,104,743,-281,279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00788() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{673,12,-372,-419,-327,1000,-621,-596,1000,-132,-572,2,439,263,430,-425,-281,-395,-717,1000,-906,-1000,565,-31,-66,352,102,132,-278,-1000,-177,821,-42,779,-1000,578,60,-342,1000,-1000,-118,706,205,-329,143,-450,296,1000,-526,18,320,-418,-1000,646,439,789,-169,-315,-1000,-724,-1000,126,164,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00789() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{-1000,-990,-66,97,990,-421,505,767,342,466,-863,614,83,243,1000,1000,-404,677,-1000,-516,-177,173,-1000,133,-1000,-1000,-1000,1000,1000,1000,-1000,-665,-443,629,622,596,1000,-329,1000,-1000,-1000,-776,795,-76,936,-1000,747,-1000,-1000,-1000,1000,-488,-386,478,1000,681,-877,-828,-1000,1000,-1000,1000,-1000,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00790() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{-1000,-1000,312,617,394,-922,319,549,973,398,335,1000,531,-247,1000,1000,-110,645,-1000,-130,-173,512,-1000,19,-901,19,-1000,1000,844,-449,-1000,-775,-1000,719,-793,342,773,-122,830,-1000,-680,-980,1000,-394,936,-1000,655,-1000,-443,-1000,316,113,-164,437,1000,576,-1000,-1000,-1000,1000,-1000,115,-90,624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00791() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{697,-367,114,-280,-417,-767,-1000,-615,-516,-758,1000,558,298,137,191,-820,-378,-1000,512,282,959,-725,249,221,386,-509,991,171,-946,-801,56,1000,332,550,682,982,-123,46,142,-940,742,-564,602,-1000,-565,-421,-767,-270,614,-1000,-639,314,-1000,-252,-615,-135,-423,-1000,988,-17,-880,1000,885,265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00792() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{-482,-950,-358,-508,309,-22,658,-82,284,187,434,-69,-351,46,772,931,124,161,-1000,592,-477,-158,-744,457,-535,-397,-649,1000,284,435,-1000,-522,-1000,731,-128,-465,1000,88,1000,-914,-1000,398,103,-434,909,-552,170,109,-993,-655,500,-15,-548,764,1000,758,-494,-361,-976,610,-1000,938,-1000,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00793() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{1000,1000,456,438,-909,1000,-1000,-409,165,-74,259,1000,1000,-549,-327,99,-381,-329,-717,1000,914,807,-104,136,211,352,-641,-440,-675,-359,883,161,524,460,-47,441,-309,112,-1000,236,1000,706,-66,-1000,-65,-70,-1000,-206,1000,-899,-1000,1000,-1000,-509,-1000,-901,372,-272,1000,-1000,607,-806,759,273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00794() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{-691,711,-997,101,907,-790,-566,450,407,417,790,-408,97,-368,-492,402,-995,628,-822,-425,911,75,714,-657,505,-111,-696,-1,-704,-907,547,-866,356,-620,590,-750,712,-30,703,990,342,-415,-206,605,-916,421,599,-532,-491,837,971,485,834,140,566,-967,260,350,-494,722,-191,84,-858,713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00795() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{-377,583,-1000,-770,102,-658,-108,-151,226,-100,1000,-477,-1000,986,-781,-954,-1000,-1000,-47,950,1000,-1000,466,-1000,1000,727,1000,323,339,-1000,211,71,204,50,-575,211,-425,582,794,-116,-31,664,837,219,99,523,-55,845,467,-229,625,-526,-1000,992,785,430,480,-1000,-425,-703,-140,709,-80,803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00796() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{-474,-923,112,709,-103,-29,-137,-512,704,473,-836,978,357,-870,-75,804,299,958,648,182,261,474,161,668,-108,855,-950,108,756,183,-433,-935,127,345,-788,752,-194,-459,792,-899,-742,-381,834,946,774,483,480,-470,-915,-347,630,-474,849,-238,43,615,820,220,-681,-735,-896,-423,-722,827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00797() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{16,-376,1000,1000,-136,-1000,-1000,225,-1000,-203,742,920,1000,-456,333,-787,-566,-511,344,-136,1000,-361,742,835,92,-924,48,-599,-1000,-8,675,665,1000,609,1000,1000,-477,-30,-430,-1000,1000,-1000,992,-1000,-1000,-436,-331,1000,1000,-1000,-1000,455,-364,1000,-1000,-543,-780,-422,-532,655,-1000,-262,-315,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00798() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{1000,750,338,336,-735,0,-261,-807,-94,-246,571,654,385,-813,-66,-872,0,-76,1000,152,0,-26,17,-287,-36,519,-298,-440,0,-401,910,633,276,378,-987,1000,-649,0,-666,-217,0,693,129,-1000,356,-10,-1000,909,934,-226,-1000,-26,0,582,-1000,-443,-126,204,666,-1000,1000,-1000,1000,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00799() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{224,-1000,-321,-360,-449,-366,-393,-1000,-167,-986,335,574,274,-154,851,1000,325,-440,132,477,138,-532,-595,874,-221,-995,333,530,-1000,-310,-557,1000,-342,946,384,1000,517,-196,359,-1000,576,-517,87,-1000,26,-1000,-924,-471,619,-1000,-714,528,-853,-32,-246,98,-1000,-1000,171,288,-421,115,974,-517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00800() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{-1000,-1000,-395,-542,-40,-512,1000,-189,977,449,-1000,816,540,-608,25,1000,606,1000,1000,618,-370,491,-937,1000,-740,-737,-1000,1000,-95,1000,-1000,-1000,-527,386,164,-753,1000,-761,1000,-1000,-1000,-1000,1000,1000,1000,-1000,1000,-1000,-418,-1000,1000,156,913,-553,62,313,-144,-470,-1000,1000,-964,-177,-1000,733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00801() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{-475,-1000,-663,-471,771,-402,-426,-14,890,895,-986,1000,-269,-690,426,517,-499,1000,-955,-433,-232,1000,-1000,-1000,-1000,-376,220,1000,1000,1000,-520,-1000,-522,-424,229,-748,1000,-911,142,-1000,125,-1000,1000,1000,1000,-92,1000,-1000,-847,-1000,949,-597,1000,-571,1000,675,706,-4,-882,1000,-1000,-399,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00802() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{697,-1000,-563,-592,15,328,984,-518,1000,320,-1000,906,298,-511,191,730,-197,1000,400,-163,-660,415,-719,-507,-856,-16,991,917,866,905,56,-641,-324,112,-479,104,428,-959,142,-1000,742,-684,1000,1000,1000,-263,670,-1000,-515,-982,473,-660,564,-252,1000,763,470,21,-882,242,-1000,-357,-370,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00803() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setRounding(org.joda.time.DateTimeField,int):void",
            new int[]{-255,-1000,-146,-480,-6,522,776,66,850,251,-366,-767,-690,1000,676,823,540,1000,-42,1000,-996,-1000,-464,637,-512,-766,-577,965,-305,98,-1000,107,-1000,773,169,-913,1000,346,1000,-1000,-769,890,-383,-294,760,-1000,915,351,-785,-1000,695,5,-1000,1000,331,964,-351,-1000,-1000,1000,-1000,1000,-730,-373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00804() {
        org.junit.Assert.assertEquals("VOID|getSecondOfDay=java.lang.Integer:NDk=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{1000,477,689,-1000,654,-586,1000,1000,343,-221,-880,494,-782,130,-348,-1000,88,-190,98,-1000,249,-84,-1000,957,1000,-302,553,170,-183,-462,-1000,997,-250,1000,1000,-225,-366,1000,-1000,-413,-432,-115,257,-18,1000,-1000,113,336,81,1000,-389,341,863,-598,-1000,-686,-1000,-1000,322,-208,742,-880,213,-169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00805() {
        org.junit.Assert.assertEquals("VOID|getSecondOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{564,-397,-446,237,-967,239,-543,489,-80,-620,222,-31,137,-806,437,-804,370,702,121,-545,186,366,-728,701,-98,-1000,-39,288,329,478,-45,759,-587,562,-634,-276,478,-579,200,-998,288,918,-173,-402,479,-411,-617,-154,-976,385,905,131,-625,710,595,913,-362,-933,37,-478,764,-288,411,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00806() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{988,499,-292,-46,173,895,379,270,-15,-751,1000,-459,314,166,-824,-383,-1000,-320,1000,-715,-90,359,631,-491,983,-1000,777,-1000,-677,-164,-974,155,1000,-65,1000,-884,-16,252,-407,263,-428,-565,578,772,685,-1000,295,973,948,-179,-416,299,-481,-6,1000,-975,-505,-494,815,579,-347,-494,796,209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00807() {
        org.junit.Assert.assertEquals("VOID|getSecondOfDay=java.lang.Integer:OTc=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{123,542,689,491,-191,849,240,881,296,-233,-153,-735,-782,975,-122,1000,-428,-1000,98,83,774,271,524,-1000,-615,-80,-578,-526,-41,668,1000,-1000,-115,-1000,-396,112,-950,158,-743,659,273,-308,404,1000,-422,983,-915,372,931,1000,-389,-773,-1000,-1000,-1000,-383,-268,800,-473,1000,-1000,-880,-276,3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00808() {
        org.junit.Assert.assertEquals("VOID|getSecondOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{-1000,-96,327,592,403,915,-365,-904,-441,-148,340,-1000,-260,525,-385,801,-599,-1000,368,265,131,489,960,-1000,-1000,-356,-104,-1000,-681,85,400,-517,-386,-1000,-1000,-491,591,-475,288,256,351,-608,892,1000,-83,388,-152,245,861,826,-1000,-241,-432,-423,734,475,-487,368,-31,1000,-192,-916,-233,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00809() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{-1000,1000,885,-302,311,-561,308,1000,-1000,27,-609,-629,507,-1000,1000,634,94,780,-1000,-345,-214,-956,-928,-1000,-1000,1000,-634,1000,591,823,-362,-1000,455,-17,-1000,-713,-1000,1000,-1000,-1000,361,-490,136,65,611,581,-924,209,-692,1000,-48,-1000,154,-602,595,191,1000,-379,-810,892,-1000,66,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00811() {
        org.junit.Assert.assertEquals("VOID|getSecondOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{371,33,453,-96,197,-666,23,481,-26,189,-702,700,-939,-492,-2,916,546,-326,204,-78,896,-211,496,-132,-58,190,-331,882,219,546,-121,492,526,783,594,-234,314,-754,-906,204,985,62,422,836,-487,-490,-328,-258,956,-667,277,257,-661,-594,-407,-510,-821,945,-551,-529,480,530,610,-755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00812() {
        org.junit.Assert.assertEquals("VOID|getSecondOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{1000,477,689,61,359,1000,751,1000,305,-950,620,1000,-18,1000,-348,-1000,-1000,-124,1000,-833,297,1000,456,1000,1000,-302,1000,-1000,-620,-1000,-1000,1000,-250,415,1000,-1000,-366,1000,-704,-413,-1000,-805,257,78,506,-1000,1000,1000,81,1000,-774,1000,888,-673,-717,-586,-1000,-536,1000,454,218,-959,1000,-169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00813() {
        org.junit.Assert.assertEquals("VOID|getSecondOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{279,297,589,264,804,-558,651,-7,-865,399,-861,745,493,-653,-941,86,270,338,-578,-485,-58,-760,-673,643,251,-221,962,556,-14,-196,-326,273,544,814,819,-981,185,988,-570,-831,-622,-509,-77,132,-32,-330,-957,188,35,882,28,-629,-751,-800,-422,-172,294,-23,-347,790,507,351,-696,-728}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00814() {
        org.junit.Assert.assertEquals("VOID|getSecondOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{-531,-483,359,-168,-91,-354,338,584,359,248,-371,-850,63,-719,546,117,771,94,-1000,-758,1000,-724,-928,-390,-1000,544,-743,-157,-369,854,374,-521,613,232,1000,616,234,40,-584,-532,1000,-45,-169,537,396,-1000,-1000,-337,541,1000,610,-1000,-628,-530,-556,-193,-1000,257,-1000,752,471,-638,-595,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00815() {
        org.junit.Assert.assertEquals("VOID|getSecondOfDay=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{549,90,-16,177,-502,-144,-4,170,368,-769,988,-126,-604,-845,-941,-754,864,930,-267,-449,938,-68,-425,1000,1000,-1000,743,-76,539,144,-359,-526,315,240,-778,104,418,-1000,793,110,277,130,-827,-756,540,-67,-1000,-1000,-253,-872,1000,590,-1000,190,597,1000,158,-458,137,-830,1000,-543,-369,483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00816() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{-401,-391,426,-311,-590,-438,309,-522,220,-934,-646,-524,74,-1000,1000,182,1000,-336,-1000,365,1000,-973,-949,35,-1000,910,-1000,911,-217,1000,285,-1000,622,-293,-1000,516,1000,-421,372,-620,1000,1000,-597,260,-432,336,-1000,-494,267,89,235,-1000,-1000,821,395,11,1000,274,-1000,1000,825,-1000,26,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00817() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{566,-33,49,-701,457,-438,281,19,220,-934,-96,241,390,616,-978,-271,38,-336,472,-292,817,144,-675,1000,745,-754,1000,228,-835,-1000,-1000,924,-514,285,856,-579,430,298,-186,70,90,354,916,369,245,-1000,58,689,610,752,-509,472,384,471,1000,-859,303,-363,-630,931,825,-1000,1000,412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00818() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{1000,578,-590,-1000,674,452,862,91,-532,-540,-502,-154,1000,1000,-1000,-1000,-871,-1000,932,-956,-1000,1000,-1000,414,977,-933,1000,-674,-1000,-1000,-1000,1000,-1000,-62,1000,-1000,1000,469,458,391,-964,-659,1000,708,1000,-1000,1000,549,845,979,-197,1000,1000,-78,629,-820,-1000,-1000,1000,-292,1000,-1000,671,944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00819() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfDay(int):void",
            new int[]{72,499,-105,-46,173,60,103,534,-289,-935,107,-454,444,-363,-472,-666,-238,-171,-330,-245,1000,74,-3,134,-466,-508,608,-496,-697,-109,-1000,-89,613,233,169,-589,108,325,-222,-614,43,-565,535,251,-69,-1000,-584,254,698,1000,-646,-425,-247,-6,689,266,942,-534,-335,899,325,-1000,153,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00820() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{-857,-153,759,284,134,-90,-175,-1000,811,-300,876,-1000,-463,-1000,-1000,704,1000,-508,-175,-756,1000,-475,-769,-309,-1000,1000,-564,-1000,355,479,1000,953,815,498,-604,1000,486,-362,-693,-1000,1000,-305,688,-116,894,-1000,1000,933,-457,-276,-1000,726,-584,1000,-11,-798,1000,96,-150,-980,-1000,-1000,341,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00821() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{132,723,894,187,-867,-592,-1000,577,-62,-40,318,-512,735,-588,-63,358,521,-762,-269,710,-1000,-903,-1000,-1000,161,-849,732,506,495,676,-252,-65,61,174,-229,-601,-533,541,-305,923,-54,-734,-1000,-57,725,237,-1000,-1000,592,-36,-447,-276,793,-1000,311,1000,633,-30,677,1000,989,-154,479,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00822() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{329,-629,528,-261,-34,1000,367,1000,-1000,1000,-920,-521,-1000,993,1000,-568,-798,-1000,-406,1000,-1000,1000,442,344,1000,-68,-795,896,1000,-755,-701,-719,272,-666,-369,267,538,982,990,1000,-1000,-853,33,-100,-585,1000,838,865,1000,-789,307,-1000,-912,5,-926,385,-1000,1000,-508,610,857,-111,-971,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00823() {
        org.junit.Assert.assertEquals("VOID|getSecondOfMinute=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{224,-1000,647,-133,895,-521,135,1000,285,377,-737,-997,104,841,822,-1000,-419,867,582,80,205,1000,621,-112,26,-409,-1000,477,712,-838,-841,72,144,-59,-111,-418,874,-688,172,-687,-1000,24,973,1000,557,744,271,1000,508,-948,364,-658,-873,983,-291,-786,1000,-657,-337,674,-1000,-908,-1000,-873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00824() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{252,-634,-384,994,-449,-106,-785,-284,710,-283,3,-1000,164,-813,-772,666,821,-675,831,-668,-535,496,-797,-1000,-952,132,398,-858,364,865,-702,-54,502,837,344,159,-975,-654,-243,-938,848,-623,19,-479,1000,-58,796,391,469,74,-943,1000,-83,488,-106,819,676,187,422,110,-264,-481,570,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00825() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{-704,-545,861,663,-322,52,-207,-5,-53,-333,376,-802,187,-231,-147,129,1000,-508,-1000,144,316,-216,116,64,-1000,271,-372,-1000,732,169,857,-311,211,1000,8,279,-528,217,82,-355,865,-177,616,580,517,148,708,120,461,-261,-611,371,182,593,35,-503,1000,-244,-333,420,-1000,-162,401,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00826() {
        org.junit.Assert.assertEquals("VOID|getSecondOfMinute=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{-111,-821,-1000,187,-126,-87,139,-106,1000,-113,932,0,319,-715,-203,358,1000,-503,-269,-456,-1000,44,317,-699,-959,-137,6,73,272,700,-857,21,-568,-98,180,426,50,-34,-172,-940,354,-491,-644,646,1000,74,579,198,73,171,833,-367,793,906,-933,1000,66,-335,24,1000,43,-233,190,655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00827() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{821,-384,-891,-331,-1000,-308,-448,524,-211,-300,1,-795,-174,-1000,-427,-530,437,-1000,863,-847,-1000,1000,-769,-1000,-867,958,452,-806,-58,1000,-1000,-686,538,837,1000,-849,-1000,-753,468,136,-108,-475,19,60,1000,-1000,-394,-360,1000,-60,-785,1000,681,-912,-116,1000,201,235,554,774,-264,2,349,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00828() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{541,506,1000,-512,-27,-456,-1000,106,-340,-456,100,149,87,-785,-495,537,848,-1000,0,658,-826,-910,-601,-1000,541,-989,978,1000,-654,-755,-294,319,-249,-546,-583,-528,-996,1000,-1000,1000,903,-491,-1000,-398,-585,-834,-852,-1000,-586,975,-25,-220,555,-935,-26,888,-187,-287,-508,1000,1000,-330,597,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00829() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{-494,-95,1000,507,-517,280,-437,-208,-753,-682,-34,-102,174,-250,-345,492,-424,-789,-1000,421,-180,-821,-328,63,175,272,328,-655,32,-999,1000,99,915,557,-220,225,687,-130,-386,264,764,35,175,-6,-267,-552,8,221,-239,439,-913,657,882,-236,491,159,319,-102,90,-118,-467,317,802,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00830() {
        org.junit.Assert.assertEquals("VOID|getSecondOfMinute=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{-880,-213,182,-824,-299,53,58,-950,295,-605,65,-901,543,421,-1000,-467,787,-414,-312,-1000,492,315,-838,-516,751,1000,-897,1000,279,343,-2,165,660,1000,738,306,-286,-184,-256,-812,372,24,1000,959,991,134,409,353,583,-177,-1000,1000,36,400,-1,-258,-1000,-720,-335,-760,-1000,-759,589,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00831() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{543,420,894,-340,-162,-1000,-1000,339,-62,-308,460,-340,1000,-192,-296,-134,664,-762,1000,-162,-644,-595,-692,-1000,209,-849,1000,306,68,1000,-252,-1000,-1000,532,1000,-1000,-1000,340,-345,-339,-127,825,1000,1000,725,984,-1000,-1000,-55,972,-221,169,465,-1000,263,1000,151,-1000,718,1000,989,-239,162,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00832() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{33,-640,-308,843,-221,-592,-534,-899,1000,-865,-125,-512,165,-739,-764,598,521,227,-269,-729,-1000,-903,-1000,-578,-1000,766,717,-831,-138,461,189,526,469,550,329,360,-334,-871,-870,-1000,-54,-314,664,-857,563,-741,533,388,-372,-410,-1000,1000,-652,1000,287,150,1000,-30,-168,-623,-771,-154,1000,839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00833() {
        org.junit.Assert.assertEquals("VOID|getSecondOfMinute=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{132,-1000,791,1000,-867,-331,-606,-251,666,-304,-218,-1000,735,-806,-527,271,952,-762,386,-869,920,833,1,-1000,-1000,226,-556,-1000,861,308,-910,219,929,1000,307,-40,-606,-1000,-465,-366,16,-734,924,947,1000,849,310,870,418,-1000,-447,429,-1000,1000,311,-612,1000,-1000,-411,-346,-1000,-911,643,508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00834() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{-356,974,995,1000,-551,-221,-1000,291,604,-98,217,-516,1000,-1000,57,889,311,-330,-488,963,230,-1000,-166,-579,-285,-281,673,-34,378,-295,-1000,164,-439,422,-506,-69,1000,161,-832,-82,282,204,-328,251,492,-386,-199,-429,176,162,-353,-184,37,365,-142,-312,-659,-511,53,873,-329,-244,390,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00835() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setSecondOfMinute(int):void",
            new int[]{-1000,490,113,433,1000,-954,-743,-1000,1000,-737,915,-1000,463,-1000,-1000,1000,953,-81,802,-954,-1000,-1000,-355,-84,-1000,488,-236,-239,-980,579,946,1000,-4,177,-229,610,944,-1000,-1000,-1000,1000,697,1000,215,593,-1000,955,994,-1000,691,-716,549,-584,-1000,234,-1000,1000,-683,395,-836,-1000,-1000,-78,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00836() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-704,-1000,110,251,-523,304,-363,-765,-420,-930,-124,-1000,-464,397,-1000,23,578,910,-493,-348,-490,-281,1000,-1000,-170,631,-359,417,807,763,794,594,262,-441,-833,551,670,-1000,1000,1000,-360,-392,-932,-770,-558,229,376,2,1000,687,7,40,247,782,-714,236,573,1000,181,-673,-709,1000,-1000,-916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00837() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{1000,-618,-251,115,-1000,-644,-1000,1000,-395,-220,208,-931,-431,301,81,-489,-1,-1000,-794,-1000,-522,-263,516,-250,771,1000,-593,189,-620,80,155,1000,-352,415,-1000,585,1000,-1000,209,946,-467,-17,740,757,-554,-20,-1000,-264,947,-939,-1000,-1000,1000,1000,51,463,-1000,-148,-849,-65,-142,-133,-1000,-918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00838() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-276,547,-442,-156,959,794,504,554,887,20,968,83,-1000,678,887,1000,1000,528,1000,-257,-443,824,-1000,-113,-696,-1000,1000,1000,-244,365,879,-1000,-106,-716,-257,312,-1000,865,-1000,1000,-313,1000,1000,-1000,-492,-6,473,360,-856,260,223,368,393,-20,862,330,1000,1000,-763,1000,394,214,-1000,449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00839() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-1000,106,209,-33,379,313,43,-326,-41,-1000,-655,1000,-128,935,-442,-170,989,-592,-66,668,409,222,501,-562,-670,-1000,1000,-58,48,-652,1000,-1000,675,-1000,-830,986,-224,-796,-226,817,-1000,408,-1000,-1000,299,-12,1000,1000,236,1000,1000,1000,316,-876,1000,272,1000,700,996,864,-741,498,-305,-717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00840() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-1000,31,20,249,150,696,897,430,468,-506,-500,613,-54,494,-1000,-466,-507,1000,189,12,215,-743,982,776,-663,309,148,-880,734,925,441,-1000,1000,1000,1000,141,13,174,628,-353,-19,-1000,-1000,-1000,289,707,1000,-49,463,1000,1000,321,-173,-1000,504,559,1000,513,128,343,-670,652,-238,-843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00841() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-1000,606,-81,630,1000,-750,1000,1000,-420,771,226,-1000,219,1000,312,284,-1000,888,-714,-437,-538,39,-265,1000,-298,-409,-337,530,1000,1000,794,594,0,1000,1000,551,-714,1000,-239,1000,1000,-1000,509,171,-584,229,1000,-433,-760,164,919,874,-818,732,1000,153,635,-1000,-534,345,568,-347,-596,-916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00842() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{1000,-747,-747,560,-1000,-731,-1000,258,384,-843,1000,-350,-933,-431,-315,687,935,-1000,215,-1000,-152,74,-888,-466,653,-40,974,172,-1000,-1000,804,936,73,801,-1000,634,804,-908,1000,1000,-1000,1000,321,139,-102,-81,-1000,450,163,191,-973,-506,1000,1000,-1000,348,-336,325,-953,-580,-581,256,-1000,-228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00843() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-1000,-896,-1000,-217,689,771,1000,-369,839,-501,-133,-614,-351,551,-905,330,607,1000,1000,14,-391,-519,-108,-468,-789,742,-487,744,1000,1000,440,-1000,-377,-322,42,-5,111,193,447,418,628,-419,726,-35,-857,-1000,1000,-998,214,1000,1000,310,-347,-824,-498,898,-839,1000,-118,-75,47,1000,-637,-999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00844() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-689,20,853,654,-743,-317,-455,765,-614,26,-521,1000,-502,227,-143,-7,620,-784,-853,-318,182,-331,1000,416,-57,-541,434,-461,-665,-343,1000,-13,505,565,510,183,-166,-302,-54,-421,-925,-352,-838,-1000,675,266,643,543,625,-10,-261,-104,438,-256,545,-389,-87,149,174,187,-732,-321,-424,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00845() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-1000,908,-581,1000,1000,666,216,-5,-501,-53,-107,559,-86,111,-71,-905,-270,301,-1000,296,376,-1000,-552,613,-262,-213,562,-1000,-753,168,548,-743,-419,-293,79,1000,257,261,816,-1000,-479,-1000,-571,-141,1000,-745,992,638,454,683,502,1000,466,99,1000,603,279,-824,365,990,167,-1000,-816,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00846() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-1000,153,439,701,334,546,-604,573,-314,-14,122,1000,-225,232,-538,-428,913,-765,-184,364,326,-30,260,291,-91,-722,820,-222,-924,696,973,936,-12,753,543,-161,-725,390,296,-923,31,-422,-570,-1000,445,-683,1000,806,330,1000,237,776,-39,1000,1000,496,608,1000,218,-865,360,-450,-688,-360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00847() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-475,109,-264,-496,-10,1000,308,317,1000,-391,859,-93,-768,-273,330,1000,780,465,664,-586,-295,809,-62,-113,741,-819,717,-22,92,219,1000,-631,411,-904,-151,-305,-441,173,-707,19,-723,755,529,-1000,152,198,809,97,837,650,425,95,64,221,411,375,1000,-172,-707,798,-441,523,-881,408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00848() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{304,-1000,-342,-525,-1000,-777,-934,263,297,-951,-210,-378,-870,244,-573,1000,1000,-1000,639,-605,-356,-261,214,-1000,192,665,544,941,224,-567,1000,553,1000,-887,-1000,817,818,-1000,-497,1000,-306,1000,387,-792,-892,-928,-609,-305,615,-245,94,-1000,577,441,-919,46,-326,1000,-473,-803,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00849() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-26,-1000,-80,-371,-1000,-576,90,459,123,265,-286,457,-1000,419,410,997,753,-473,541,-1000,-670,173,291,-592,-425,-49,-605,1000,1000,249,626,555,989,-1000,-657,-659,285,-817,-964,1000,-491,732,1000,-570,-959,893,-185,-997,44,-1000,-168,-1000,469,475,-1000,-522,197,1000,-575,-1000,-422,1000,-1000,-503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00850() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-546,-168,223,1000,1000,-453,-95,-451,-1000,280,-23,-123,-936,-835,81,-1000,946,1000,-296,186,-785,-189,-297,-1000,262,-936,256,-491,-505,646,128,382,-927,399,-1000,1000,-1000,63,599,585,154,-17,-699,-77,-610,260,-112,1000,1000,-391,-648,504,1000,749,51,-141,-222,-104,212,-619,454,-336,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00851() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(int,int,int,int):void",
            new int[]{-1000,-1000,172,-275,-666,88,536,-777,105,-232,949,409,-1000,486,-347,1000,607,864,752,-1000,-648,161,630,-1000,-1000,-307,-442,399,1000,727,1000,272,1000,-1000,-540,1000,53,-817,-410,1000,-416,469,-171,-1000,-962,1000,779,-811,81,139,536,-272,-57,323,-1000,-681,1000,1000,-1000,-662,-818,1000,-1000,-502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00854() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{-941,27,-141,327,412,-37,-28,-665,37,-318,-868,137,-632,-1000,-1000,1000,-243,-158,462,669,170,110,-28,-64,-961,25,-512,-73,118,-524,-115,580,-982,646,-659,-449,-1000,-1000,588,1000,79,-58,-1000,-421,1000,-99,-85,215,-210,-48,1000,590,-353,909,126,-306,1000,-274,504,-1000,936,-457,-1000,-734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00855() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{-816,-638,108,279,-116,1000,-550,590,-950,-757,1000,311,691,-150,-538,1000,-508,-872,-1000,-1000,-329,573,759,311,-322,-1000,238,-1000,1000,-162,461,1000,-578,-774,-1000,-1000,435,-564,-363,849,1000,907,-462,1000,375,-349,-214,-568,-173,1000,680,585,1000,755,-244,-614,941,-1000,632,-903,-933,-364,-1000,772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00856() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{-1000,-653,-133,489,-437,1000,515,770,-947,635,675,1000,-301,-901,493,1000,10,-1000,-802,-1000,-702,118,30,789,197,-1000,-322,-1000,792,541,249,1000,-978,-346,-1000,-1000,18,579,-287,-290,1000,-645,355,-283,1000,716,-460,-710,1000,1000,1000,140,1000,1000,350,-788,1000,-1000,1000,-1000,-984,-1000,-884,-5}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00858() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{1000,-490,123,579,986,-681,169,-78,-543,823,-571,-692,-517,1000,247,430,-143,627,-488,1000,1000,208,986,-679,-975,-1000,-496,62,915,-1000,108,-939,427,18,-437,336,1000,-508,102,-250,-598,-656,1000,1000,83,-512,-76,-415,-556,733,473,1000,-486,-1000,-476,-665,-1000,-232,786,-42,843,-777,948,76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00859() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{-453,-1000,1000,-517,437,-616,-1000,1000,56,547,728,-272,-836,-1000,1000,413,-873,418,-95,-1000,-333,-804,740,1000,197,413,-1000,233,-335,1000,-1000,-1000,-274,-703,338,1000,-134,-794,-1000,297,-971,633,223,-1000,211,-1000,1000,-1000,1000,-1000,954,-22,-285,-965,532,769,-355,755,-294,-540,1000,683,971,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00860() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{-574,-195,38,338,-252,-612,-173,-134,1000,529,-788,-169,-606,400,-1000,-1000,524,379,-152,-102,213,-60,392,-811,-204,838,-50,-577,369,-659,-296,-142,-248,-14,85,-104,-1000,225,75,1000,-1000,-911,-586,-192,55,469,211,-635,-365,-1000,-631,144,-1000,-20,242,173,737,29,-669,400,-124,-231,-559,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00861() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{259,-144,-56,-893,-969,471,837,-717,247,-869,-440,-318,-731,-69,-645,10,-508,-58,-128,485,921,-161,762,-599,-537,-407,-677,168,-294,-537,138,318,-100,96,415,-1000,-42,-564,31,905,-186,-408,-239,365,-4,167,-69,-1000,-68,542,1000,435,697,549,509,586,-52,-338,632,-737,-144,-83,-1000,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00862() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{-379,420,-318,347,253,877,257,346,1000,-190,-1000,-405,174,179,29,269,1000,-908,-397,-498,-1000,-8,-35,214,-123,1000,-45,-1000,-452,-905,59,953,-853,-938,-1000,1000,-1000,1000,733,1000,175,-965,-1000,738,1000,1000,-63,148,-1000,-347,368,458,-1000,631,-447,-723,1000,-317,-820,1000,55,-1000,195,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00863() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{745,-221,516,228,-117,606,101,220,1000,-874,769,-1000,-11,824,-654,1000,1000,-1000,-1000,-866,656,-170,274,-579,-1000,-600,89,-144,422,598,1000,-61,116,-1000,-1000,98,26,1000,-469,1000,1000,-221,-754,-406,809,91,84,362,-642,887,-291,1000,-958,1000,-602,-1000,1000,-872,154,-1000,-826,-1000,-891,969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00864() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{-489,778,-163,632,-729,53,157,-833,509,-684,654,7,-239,-985,283,-626,-985,-959,263,-819,-752,40,-675,298,-780,605,727,-326,158,926,278,346,-966,-72,650,-833,-282,518,905,875,435,131,-669,-929,-292,-541,457,570,-790,-999,-593,-344,830,828,655,826,648,-145,-339,753,488,825,882,408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00865() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{739,94,547,-517,157,-982,860,-342,1000,-224,-1000,-174,-1000,-139,1000,-582,-873,194,473,514,918,-615,-211,19,-810,413,8,1000,-456,660,-81,-1000,61,416,74,314,-499,-135,-288,1000,-1000,-1000,106,-773,594,-452,795,152,960,-1000,149,163,-1000,140,532,125,913,598,-872,-823,335,-846,971,-216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00866() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{-1000,1000,204,109,622,651,466,-94,-960,905,978,1000,-55,-1000,-88,397,-385,86,1000,838,-1000,415,693,1000,-451,19,-46,-914,570,172,-1000,167,-1000,247,-464,-298,917,-147,-103,-355,-727,793,686,-202,517,318,-74,-1000,-200,612,585,-691,838,-598,695,397,-1000,548,856,-73,949,746,-471,-480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00867() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(long):void",
            new int[]{63,-1000,20,-515,-815,-176,567,-568,-751,-609,431,-251,-1000,-857,-1000,758,-532,327,-297,-243,1000,-92,1000,-553,-509,-1000,-966,-230,545,670,-352,-265,270,-989,577,464,511,-1000,-798,894,-494,-642,-358,-76,-236,-604,254,-346,1000,398,1000,171,1000,551,1000,1000,193,-356,1000,-1000,-367,438,-1000,-107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00868() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{1000,542,737,-34,-871,-409,-301,268,-808,36,-1000,-1000,395,93,-443,-568,539,277,1000,1000,1000,-470,-634,440,-16,1000,427,532,-607,1000,324,153,-797,1000,-323,-843,354,571,1000,-257,-1000,1000,941,741,-676,-12,-134,-840,1000,-1000,676,-287,422,34,305,1000,-816,578,1000,-824,-383,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00869() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{598,-352,-921,79,-871,-441,-463,502,-755,216,936,850,719,-405,-331,-568,-823,-154,-209,918,-70,841,737,2,-16,676,569,-522,-612,872,617,-720,-797,21,-741,39,-384,136,-395,-726,-63,-935,-124,461,609,-12,406,924,-302,-755,-670,-287,819,503,305,-509,-28,648,-136,801,-696,661,-603,-984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00870() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{151,-35,-540,839,-376,-791,-135,763,184,42,1000,887,-779,1000,666,353,-433,-1000,-480,23,312,-1000,-1000,-733,826,36,-308,-949,463,-424,-1000,1000,436,-231,1000,238,551,-1000,751,880,581,-1000,395,-1000,-903,995,-236,1000,-1000,-1000,-1000,472,-727,12,144,-1000,1000,1000,246,1000,1000,989,-1000,708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00871() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{-274,-349,284,404,-381,-758,-259,613,-996,277,-623,-442,65,-471,-483,-876,428,26,831,764,-722,-339,685,-156,100,915,839,-722,-329,655,292,-247,61,-36,23,-216,-386,-508,339,452,153,-104,601,797,961,-380,-789,491,-475,-537,918,728,422,-815,721,25,-686,-272,978,-949,541,-437,651,-507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00872() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{1000,-1000,737,-301,-871,-266,-687,69,224,-514,499,-354,-27,375,159,405,372,-37,-112,50,400,-1000,-1000,1000,-16,-934,-191,-126,-607,253,-259,118,-363,1000,-354,365,660,-61,724,-400,-607,879,941,-1000,-1000,-92,-625,-400,1000,-1000,-885,-122,-29,1000,305,-99,-224,1000,964,-251,20,-194,809,288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00873() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{-472,350,575,839,123,534,346,-466,-1000,496,-1000,457,216,-867,-683,-935,-149,-380,222,419,-670,224,1000,128,540,968,-578,-276,-314,-790,787,-612,500,-1000,428,-370,-515,400,-115,75,657,-637,-121,951,951,-588,-479,213,-797,1000,1000,-391,1000,-1000,757,132,-1000,-634,148,-750,-119,-29,-400,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00874() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{520,-185,58,-186,-1000,-1000,556,-56,-1000,-17,181,536,-431,-1000,-931,-948,928,-607,360,505,-769,653,766,723,-815,1000,1000,-1000,-337,-1000,1000,240,894,-56,96,-166,-484,-20,220,536,-475,-582,521,217,36,-1000,-739,536,-714,-671,-873,-1000,1000,402,1000,-929,-426,653,403,-811,217,893,-1000,-923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00875() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{-305,-602,-63,708,479,100,-134,-1000,740,-277,-937,-524,-765,817,-322,1000,925,1000,329,-192,-111,-696,756,1000,-340,-233,133,1000,1000,-1000,-1000,867,1000,420,905,-418,1000,-873,-412,1000,1000,-649,1000,777,547,306,456,-935,-18,692,895,1000,-1000,212,-1000,636,-880,53,838,-435,-824,653,451,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00876() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{1000,234,359,690,-666,-1000,326,-156,-1000,-626,-886,-141,1000,-565,-1000,-1000,-1000,-1000,449,1000,1000,-1000,248,1000,-1000,1000,502,-15,-1000,1000,1000,-1000,-1000,978,-1000,192,-1000,1000,144,-1000,-1000,422,614,298,-213,-534,-216,-1000,799,-1000,-96,-952,1000,973,372,1000,-1000,86,96,-862,-1000,-1000,-673,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00877() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{-218,624,42,-733,-813,660,-254,869,-379,1000,-499,-352,-578,718,398,-6,1000,1000,610,360,-400,693,-108,-642,964,934,-287,-158,-70,-253,-417,1000,146,-29,1000,-1000,694,-368,1000,1000,-611,5,427,901,165,571,-134,560,-10,-17,561,426,-358,-455,305,99,-643,452,1000,-20,597,194,1000,-288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00878() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{-1000,887,-714,19,-256,1000,571,1000,1000,598,918,-1000,-575,1000,1000,-464,-709,268,1000,234,475,-251,981,139,-397,-1000,747,451,508,-1000,870,361,160,-363,862,-633,282,507,29,1000,-373,108,893,-58,-252,1000,187,7,543,-355,464,-473,23,-1000,-997,192,-966,-388,-710,-349,-1000,-348,-13,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00879() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{-1000,-983,1000,656,944,913,230,-663,353,1000,-173,-25,-43,969,343,738,85,-144,552,581,-411,-1000,831,128,-870,-105,-184,148,778,-105,-15,1000,309,187,730,-383,718,-47,614,269,758,-666,168,802,15,764,362,-66,-8,1000,989,218,-247,-123,-422,567,-598,-384,1000,-346,-1000,-142,175,553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00880() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{-651,-1000,-948,-1000,-1000,328,-797,1000,716,957,1000,419,-1000,-405,1000,-568,1000,1000,-188,-609,-1000,-505,-637,-641,1000,676,-807,-522,461,-943,617,1000,1000,357,1000,579,886,-1000,1000,1000,313,-760,857,461,181,551,-596,1000,-1000,-1000,-1000,1000,-1000,215,194,-509,220,1000,1000,950,1000,1000,1000,591}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00881() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{51,-1000,-286,-1000,-481,-636,150,124,400,639,960,-455,-248,1000,1000,1000,18,1000,67,1000,-1000,-971,-1000,-415,123,-860,-987,-928,1000,1000,-1000,1000,-470,1000,896,450,1000,-1000,1000,777,-327,42,1000,-1000,137,684,-1000,1000,-905,-1000,-1000,1000,-1000,1000,351,-1000,307,1000,1000,1000,1000,509,580,454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00882() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{190,-1000,-866,-264,-1000,302,-715,-325,163,-170,-179,-493,-270,991,524,-358,434,-154,332,427,88,400,949,816,-794,-917,645,441,573,-446,619,-400,-285,805,-105,-633,-165,686,-165,732,-128,95,1000,412,-533,639,-529,-579,-856,-172,-178,1000,-387,606,-310,212,-837,766,-292,-782,-1000,649,308,-251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00883() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setTime(org.joda.time.ReadableInstant):void",
            new int[]{-358,-507,-75,-853,-8,-321,-128,349,184,1000,571,67,-779,1000,666,324,-272,1000,92,705,-1000,-1000,-373,-325,-219,36,-21,-207,-64,-424,-398,1000,603,6,1000,35,551,-651,618,880,1000,-1000,395,-182,-382,82,-314,1000,-159,-816,-474,527,-727,-822,261,-380,-42,882,783,894,704,829,-1000,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00884() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{-239,-1000,-407,1000,-203,-701,505,555,-526,-305,-1000,520,-1000,380,-251,-1000,-1000,1000,378,-1000,-150,-276,-723,-438,487,1000,779,1000,1000,1000,-310,-917,493,436,854,197,-187,-94,-990,233,1000,419,-671,595,-65,302,680,-661,984,173,-442,-64,-407,-263,-257,-39,-598,33,317,-1000,652,-409,-446,-533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00885() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{-665,-931,393,-671,-231,-205,327,462,-611,352,-500,834,895,-401,471,501,-664,443,-908,-148,825,185,-416,-222,843,-217,-624,-924,172,-89,800,-545,-905,843,620,251,195,-43,311,-768,798,983,53,718,-74,960,-543,-342,-534,352,203,264,-794,732,-43,348,362,-961,-786,-406,261,-483,-955,-337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00886() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{188,-153,329,-38,-422,1000,-523,448,337,-130,384,550,-325,928,848,-664,-545,27,-355,-446,504,-582,-91,-421,483,138,-3,252,1000,-64,-213,-1000,670,-997,554,-167,-231,383,-685,196,-204,-137,106,-1000,-530,-1000,1000,-395,896,-268,-939,393,739,552,-304,-1000,-841,1000,-402,595,601,450,-292,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00887() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{406,13,1000,-991,859,-185,206,1000,147,-574,1000,146,1000,-1000,-75,1000,-1000,165,-225,-930,1000,-212,765,-376,1000,-385,-1000,354,1000,-164,807,-1000,-553,-302,-426,1000,-215,583,-425,-1000,1000,1000,-1000,-186,-47,248,206,-1000,495,624,-301,466,641,942,-223,-128,-1000,-1000,-781,-835,1000,256,-162,34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00888() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{-644,-715,-54,-676,-892,653,387,839,32,855,-1000,18,-635,1000,-527,-307,363,-244,-857,-120,450,416,-641,132,362,-841,-369,-508,-604,-1000,733,-57,480,377,533,207,-568,289,922,32,-830,-901,913,1000,296,159,-683,902,-216,891,705,133,-11,288,0,-239,866,682,-617,341,114,-570,-344,-309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00889() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{-588,-861,-201,-387,-168,493,-917,639,-10,452,-557,17,765,-327,952,397,-248,583,-235,139,926,-88,443,62,1000,-914,-743,-1000,172,-610,467,-545,-324,-82,508,349,943,-308,268,-311,688,1000,448,-196,-120,-37,-543,-444,70,19,203,240,142,23,-1000,-72,37,-961,-927,352,1000,337,-651,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00890() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{-81,-547,89,1000,-561,153,726,517,137,446,-714,478,-963,245,877,-155,-759,-4,-365,-386,989,35,-365,-674,121,311,-440,-1000,407,-474,-754,-319,-400,173,758,229,1000,-342,-39,-534,1000,361,82,-400,-475,321,-273,-435,-75,224,170,-575,-169,896,-544,-702,-1000,-679,451,595,987,87,-653,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00891() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{-303,-3,-633,191,-432,6,1000,514,-571,57,-1000,413,-1000,1000,-1000,-961,-209,718,-287,-1000,-535,-60,-1000,51,40,337,789,1000,263,257,73,-763,1000,459,785,-155,-982,517,-800,-315,-281,-528,-343,1000,502,142,337,328,994,685,115,45,-355,-547,40,-21,-645,1000,416,396,31,-426,-98,-50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00892() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{503,-578,1000,-758,-147,178,-474,121,219,834,-923,-97,-27,565,236,-87,-1000,-627,82,-494,799,-1000,-191,561,767,-1000,-1000,-175,-52,-298,559,-1000,171,-928,660,-993,-89,-160,-738,-205,1000,177,-120,-1000,-894,-1000,1000,-1000,-53,896,662,258,363,-524,-381,-1000,-1000,-392,-283,93,1000,122,-683,507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00893() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{462,-209,497,-392,-281,1000,-413,805,710,335,245,291,-1000,605,-526,-998,-504,-984,-57,-100,40,-917,-198,-452,553,-261,-137,-668,574,-160,-1000,89,1000,-1000,718,-197,24,366,140,119,-1000,-1000,452,-1000,-900,-1000,1000,-65,746,-322,-158,315,1000,1000,-441,-1000,-955,-3,-203,1000,102,-397,0,125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00894() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{-553,-1000,269,479,-565,-356,-430,898,96,338,-1000,172,107,-520,927,-363,-766,160,92,23,1000,107,-49,-567,1000,105,-384,-947,449,61,186,-277,-1000,344,632,709,567,-584,501,-517,1000,452,444,130,-514,388,-192,-511,-229,160,-91,-23,-85,692,-423,-265,932,-973,-758,99,1000,-547,-842,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00895() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{694,-1000,1000,-676,-155,653,-492,-200,77,116,1000,-163,213,1000,-571,-467,-811,-652,-710,-4,480,-1000,-580,-524,887,-1000,-666,-1000,-621,-115,733,-710,480,-1000,381,-999,-464,145,-831,-951,494,151,-360,-503,-595,-1000,869,-1000,518,-949,-1000,180,696,371,-297,-467,-1000,-718,-617,-71,-526,-392,-570,-205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00896() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{-1000,352,-510,574,-607,58,-257,872,204,538,-1000,38,221,140,1000,-824,-837,1000,-88,101,-462,421,447,1000,152,410,-72,156,-783,-477,-1000,-199,-1000,-174,815,-43,-521,-295,1000,486,-713,-1000,978,835,609,-469,-393,1000,-353,685,933,-28,81,194,422,412,1000,1000,-558,804,860,-114,-459,-35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00897() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{-786,-861,-707,4,-207,445,-810,446,-10,-329,-1000,20,76,-322,-6,-665,426,464,-524,-357,192,1000,-546,788,31,380,690,757,104,-377,253,-749,-600,-82,378,318,-529,438,466,405,-1000,-780,799,-374,712,-177,106,955,70,19,914,379,327,-228,492,152,632,-43,-927,403,654,78,-752,-686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00898() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{950,-931,696,-456,-281,1000,718,-129,360,80,674,-177,-717,1000,-1000,-1000,323,-1000,-642,-364,-64,-612,-634,-707,537,-784,-205,-438,-31,-103,928,605,1000,-1000,578,-123,-544,404,-507,1000,-579,-658,485,-578,-673,-1000,-192,172,929,-669,-799,650,-794,939,-43,-1000,362,922,-809,916,-979,-261,-186,-337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00899() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekOfWeekyear(int):void",
            new int[]{-1000,548,-1000,351,603,-616,759,900,-703,-686,-1000,997,-14,-1000,629,-795,-699,1000,136,-1000,385,1000,-393,267,-320,-459,690,1000,744,976,-161,-945,-660,1000,961,982,215,-846,67,109,-1000,1000,-716,-1000,96,1000,103,787,-70,1000,662,222,-1000,155,508,469,415,53,-662,1000,1000,-27,-435,286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00900() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{-994,236,-386,672,-127,-585,-151,871,792,31,1000,-579,-1000,550,-681,506,777,802,1000,-899,492,-1000,1000,-845,-1000,1000,1000,-228,-435,330,6,-1000,1000,-558,-1000,-683,1000,-245,-74,-551,1000,1000,671,-740,-172,959,-1000,430,-201,877,1000,-1000,22,-1000,262,-122,-1000,-360,495,745,-223,-74,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00901() {
        org.junit.Assert.assertEquals("VOID|getWeekyear=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{-532,765,444,260,-82,-644,343,253,-754,-751,-789,-921,-884,509,-496,-83,662,458,151,-842,976,-616,914,86,-892,752,-994,-470,93,336,260,-543,662,45,-246,-443,972,-432,55,75,954,462,991,361,-325,783,-747,-76,-485,-19,981,-884,-140,-622,8,23,-444,-693,448,601,-594,-306,892,657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00902() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{-441,-770,1000,-797,-613,-172,-97,-1000,540,750,378,-492,-150,-508,-275,-139,1000,586,-655,-100,1000,684,1000,1000,1000,-805,-1000,1000,-1000,861,-1000,1000,-1000,823,1000,1000,-1000,-468,1000,-1000,-1000,-1000,1000,-1000,-304,1000,1000,-1000,-588,-1000,-1000,1000,-1000,1000,709,822,1000,-14,1000,-667,-314,975,-1000,-823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00903() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{-1000,-118,1000,-720,498,384,-251,159,813,179,-1000,-797,-1000,-1000,-974,319,1000,-1000,633,-1000,1000,-1000,1000,521,837,414,-1000,1000,643,1000,-787,909,528,851,51,-42,-1000,38,1000,1000,64,-196,1000,1000,145,1000,849,-1000,-781,-447,1000,-958,-1000,-891,419,1000,-95,-1000,1000,-86,170,-319,-42,-269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00904() {
        org.junit.Assert.assertEquals("VOID|getWeekyear=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{121,-893,275,413,682,-211,937,128,917,-708,-38,-263,-902,-867,-402,348,-322,959,51,-498,-803,-411,130,-831,627,393,325,172,900,398,324,-556,493,860,600,-320,-371,-605,-70,-929,906,265,-491,87,965,-116,-364,542,229,-768,884,-449,-375,-518,127,-576,746,-149,821,-15,973,-534,551,418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00905() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{-1000,-465,624,939,-172,-474,-494,1000,1000,-105,844,-1000,-1000,-47,-1000,414,1000,450,1000,-1000,1000,-1000,1000,-856,-868,1000,1000,1000,940,1000,-1000,-866,1000,-1000,-1000,-1000,870,-271,482,-68,1000,1000,1000,-201,-421,1000,-736,859,-897,1000,1000,-1000,-276,-1000,-351,947,-1000,-487,1000,1000,359,-825,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00906() {
        org.junit.Assert.assertEquals("VOID|getWeekyear=java.lang.Integer:NTQ=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{-74,-158,-393,413,195,-435,0,-78,-156,125,4,543,-902,187,-419,985,-789,586,-802,-479,-1000,711,-644,-1000,-654,-250,325,-517,-88,119,617,-986,493,507,439,1000,318,-489,-278,-1000,194,538,-491,-893,146,-717,-734,-252,379,-213,-627,500,244,-837,333,-576,746,225,-93,-667,973,586,947,418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00907() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{-1000,256,-1000,-971,550,-110,543,1000,935,-435,-1000,-1000,487,-280,-1000,610,-271,269,1000,-637,986,408,1000,-341,-581,1000,-1000,1000,-1000,1000,-759,-564,1000,-309,-1000,-589,536,-1000,391,-217,802,1000,1000,1000,736,1000,-1000,264,-559,717,1000,319,-518,-1000,586,-191,280,-1000,1000,732,141,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00908() {
        org.junit.Assert.assertEquals("VOID|getWeekyear=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{-527,230,423,928,337,-216,655,1000,501,-839,434,-780,-1000,194,-419,340,1000,710,1000,74,621,-1000,914,-275,-1000,1000,-657,71,489,551,-168,-1000,669,-209,-549,-1000,972,-234,127,-328,1000,786,771,959,255,1000,-747,902,-463,154,1000,-1000,-134,-661,-242,129,-906,-244,1000,1000,-51,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00909() {
        org.junit.Assert.assertEquals("VOID|getWeekyear=java.lang.Integer:ODM=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{1000,-442,111,-1000,24,344,384,-920,-198,557,605,16,811,-264,162,-439,-517,-443,834,298,-1000,-390,495,-98,1000,-654,677,216,-327,-261,-343,1000,-299,162,158,992,-383,965,75,-117,-634,-372,196,-265,1000,442,101,-49,889,-584,-158,1000,-86,542,493,533,184,807,25,-869,290,1000,-1000,-823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00910() {
        org.junit.Assert.assertEquals("VOID|getWeekyear=java.lang.Integer:MjE=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{868,-314,720,72,-776,80,-227,-161,-474,-113,21,479,367,779,324,327,-1000,339,-1000,920,-290,-616,-486,-185,-204,-648,166,-582,486,-449,149,-717,-74,-202,-246,957,831,-104,-360,-385,-479,462,-499,-788,-6,62,-263,-17,417,-629,165,-1000,-156,119,-350,-685,-995,707,-501,-799,-250,856,252,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00911() {
        org.junit.Assert.assertEquals("VOID|getWeekyear=java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{-813,-466,-1000,410,-751,-979,154,1000,177,658,-1000,-42,558,717,-688,945,-271,659,991,-248,-364,160,926,-1000,-273,1000,400,827,-1000,279,-975,-1000,1000,-843,-1000,-132,1000,-521,-343,-1000,1000,1000,119,110,566,1000,-1000,930,44,1000,313,-210,263,-1000,229,-440,4,123,1000,266,92,-104,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00912() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{1000,-111,93,73,-312,621,343,319,821,-89,1000,564,787,579,51,692,844,439,-450,-652,-460,756,-1000,-686,-306,-898,1000,-89,1000,-366,-111,-1000,-52,45,-1000,138,1000,259,-194,-595,22,462,-273,-1000,3,783,-747,398,595,234,-14,-129,831,-622,-576,-519,-427,-693,-708,-162,-33,577,-778,76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00913() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{-388,31,1000,-972,858,749,-165,158,885,-168,-755,-414,-422,-1000,-663,357,1000,-1000,72,-1000,928,-35,204,389,594,-264,-1000,1000,1000,1000,-691,676,189,284,51,-27,-526,-36,1000,1000,-153,-196,1000,1000,302,950,693,-1000,-378,-298,710,-128,-984,-438,74,889,111,-619,380,-26,174,-280,-535,170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00914() {
        org.junit.Assert.assertEquals("VOID|getWeekyear=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{865,335,-158,-1000,199,712,213,152,341,11,549,322,393,324,-23,329,1000,157,-385,-1000,-347,666,-801,61,-184,-568,303,-86,356,134,67,332,-91,32,7,569,156,766,-50,-162,49,-589,406,-239,329,208,-314,181,136,-335,710,-128,416,61,-235,-369,-150,650,-339,-366,-144,-128,-573,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00915() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setWeekyear(int):void",
            new int[]{-1000,746,880,-397,507,-41,-532,1000,576,-174,-1000,-1000,1000,-120,-1000,738,109,-1000,1000,-278,1000,270,325,59,-673,1000,-1000,1000,798,1000,-1000,-336,1000,-1000,-1000,-1000,1000,-535,1000,995,725,373,1000,1000,-346,1000,-335,-699,-533,1000,1000,1000,-694,-977,1000,1000,128,-888,1000,1000,-383,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00916() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{895,-1000,-70,635,-82,114,477,-805,-689,-587,133,-213,587,-1000,-48,-1000,-112,1000,-340,-694,-781,1000,960,466,854,-1000,-1000,353,-31,-1000,-1000,-1000,595,-252,808,1000,-1000,-304,-627,-167,1000,-204,1000,446,204,261,105,-963,182,-403,-399,579,-235,-24,-601,478,208,923,629,-436,-573,214,-345,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00917() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{253,-609,-471,674,122,125,-615,158,-919,-1000,-289,595,53,-1000,152,-333,803,1000,1000,-1000,-508,986,131,276,713,564,20,-1000,455,-1000,112,-1000,185,1000,-109,543,-1000,1000,-1000,-638,-129,-1000,1000,-705,679,1000,1000,-818,-1000,-383,-400,-1000,-798,-1000,944,562,-55,-340,-21,737,-287,-670,94,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00918() {
        org.junit.Assert.assertEquals("VOID|getYear=java.lang.Integer:LTE=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{732,-794,-613,933,894,219,1000,-157,382,-253,387,250,1000,-272,-919,177,-175,365,-468,-1000,-657,1000,-440,-624,559,-1000,21,-1000,-1000,-1000,-1000,400,1000,-1000,-257,1000,-561,-1000,-1000,-1000,1000,-1000,179,1000,1000,1000,-853,-610,-985,-1000,128,-821,655,92,245,728,-87,487,550,-1000,-602,-796,-637,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00919() {
        org.junit.Assert.assertEquals("VOID|getYear=java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{247,-869,-800,-237,19,-414,-607,-1000,983,-83,-394,-404,32,431,-206,-722,745,-786,-859,-1000,1000,-106,-920,279,-89,-293,160,879,1000,-121,-467,380,1000,948,568,139,1000,1000,-486,142,-1000,321,938,108,84,708,-516,-426,1000,1000,1000,-66,476,-664,-389,-96,-430,-336,-1000,-769,874,1000,1000,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00920() {
        org.junit.Assert.assertEquals("VOID|getYear=java.lang.Integer:LTE=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{116,-207,29,-221,1000,808,-318,-340,1000,-239,181,1000,257,1000,-127,977,-433,-681,1000,-1000,1000,795,-1000,-876,519,44,-1000,-1000,-1000,-386,614,420,985,1000,-331,-1000,-19,-207,604,1000,341,-1000,-552,-1000,1000,1000,-1000,136,-789,-1000,-797,-715,1000,1000,1000,108,-1000,-35,-186,-995,-656,-890,647,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00921() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{-151,-404,471,-537,-408,-378,500,-143,-1000,-691,1000,-979,543,-1000,52,-672,-158,852,-731,-142,-1000,683,1000,708,1000,-406,-561,571,1000,412,-338,1000,575,-1000,-116,1000,-548,135,-1000,-425,119,-270,1000,1000,-287,-244,209,-1000,-1000,-193,633,-186,-1000,-1000,-1000,81,-800,301,-512,-148,-1000,-437,-586,-855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00922() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{-921,-625,-606,-317,-772,-1000,798,256,-497,-1000,1000,-251,1000,-247,-438,-1000,1000,259,89,258,-638,461,844,1000,1000,-601,-252,-51,1000,796,-1000,-609,-1000,-595,-235,1000,-1000,-126,-865,-74,418,1000,-738,694,-1000,-1000,392,-856,-848,687,368,476,-874,-1000,175,863,805,418,-968,1000,1000,-674,-214,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00923() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{464,-277,329,1000,584,-205,477,901,284,-1000,24,1000,189,-1000,543,-320,1000,1000,1000,-1000,-781,31,960,163,1000,522,708,-644,584,-1000,52,-786,-245,158,-331,1000,-1000,1000,-1000,-706,-28,372,87,160,614,1000,1000,-1000,-1000,299,-1000,-1000,-297,-1000,-601,1000,150,-344,864,1000,822,-1000,760,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00924() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{549,195,6,1000,-506,940,-599,-718,-585,-356,-1000,-1000,-158,-107,1000,-237,496,320,875,491,577,868,-175,573,-327,1000,-677,825,1000,-11,1000,-1000,-336,1000,1000,-813,-1000,1000,-647,1000,-965,103,1000,-1000,-109,196,702,-395,238,937,-1000,-705,504,720,-305,-354,-1000,126,1000,1000,-12,838,1000,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00925() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{477,-63,-667,938,-701,-382,-880,495,-657,-306,-703,-704,1000,-381,1000,-882,1000,432,-291,-524,-756,-25,131,747,-441,-1000,31,404,131,24,-96,-1000,-667,1000,1000,987,-1000,1000,-1000,20,-287,660,1000,-1000,381,633,1000,-523,-1000,698,-631,27,-603,-989,-224,775,574,-371,-72,797,1000,559,-6,-417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00926() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{217,-1000,-294,-508,374,-156,74,-805,-1000,-662,707,-60,-293,-691,-292,-314,-365,1000,-967,-1000,-1000,1000,708,-440,854,69,-766,-1000,20,-1000,-581,-316,1000,-965,-592,-156,-742,-477,-1000,-1000,68,-1000,1000,780,1000,1000,413,-1000,-985,-1000,-332,-821,-1000,-1000,420,357,481,113,-687,-1000,-923,-796,-731,371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00927() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{-160,-54,-803,-602,537,168,830,400,1000,-514,543,518,-47,-500,-643,696,1000,-848,114,-1000,-42,282,-1000,253,-373,69,1000,-669,-194,844,-453,1000,-168,-1000,-241,-20,-67,508,-137,-1000,-279,-1000,-776,1000,1000,603,-987,-297,-1000,-180,-247,-1000,1000,493,-121,320,-327,-427,-1000,-14,633,-1000,-14,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00928() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{-526,63,-666,36,1000,960,-330,-508,1000,-1000,315,37,-734,223,-549,1000,-430,-605,261,-1000,-23,1000,-1000,-159,-342,-430,246,-1000,-767,-542,777,0,881,-1000,-163,1000,-139,876,306,-1000,43,-1000,-161,1000,1000,1000,-719,373,-1000,917,-484,-1000,812,365,808,-704,-808,-139,-30,-1000,-1000,-1000,64,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00929() {
        org.junit.Assert.assertEquals("THROW:org.joda.time.IllegalFieldValueException", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{1000,-767,-165,266,104,-291,1000,-648,-156,-387,278,683,1000,-1000,77,-585,1000,-482,-291,-725,-232,387,109,929,789,-319,516,1000,357,-25,-893,-1000,-336,-24,1000,936,-709,-810,-213,-565,849,263,-1000,398,-55,332,516,869,-851,1000,-167,661,1000,340,1000,1000,178,17,178,990,1000,-396,-214,-863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00930() {
        org.junit.Assert.assertEquals("VOID|getYear=java.lang.Integer:NDc=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{1000,-1000,4,-29,163,-118,168,-673,-551,-374,477,670,1000,-1000,-221,-411,-498,-485,-408,-708,-581,260,101,1000,366,-565,260,1000,231,75,-912,-1000,-412,-49,1000,674,-828,-1000,17,-793,1000,-85,-713,282,875,967,-25,-330,-892,-718,32,870,840,457,582,725,490,-17,404,768,293,-422,-775,-836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00931() {
        org.junit.Assert.assertEquals("VOID|getYear=java.lang.Integer:NTM=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setYear(int):void",
            new int[]{739,-18,-697,407,-80,-269,628,-138,-632,-639,535,718,861,-1000,-424,-1000,344,1000,-229,3,-707,1000,509,186,815,47,-491,-627,-413,-1000,-1000,-1000,692,-252,66,1000,-1000,-304,-1000,-691,1000,-386,1000,652,174,644,105,-955,-603,-1000,21,-401,-896,-745,114,-182,682,492,-86,-620,-237,-456,-684,267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00932() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{-189,801,188,-11,-39,597,343,-527,-212,-979,-670,420,862,-879,-1000,386,201,1000,-606,-1000,668,-675,-545,-1000,291,1000,595,-139,-1000,-468,-299,655,-262,1000,-93,1000,-453,-655,-605,313,-417,668,143,-178,86,-938,-137,168,-634,-62,-713,-1000,-647,-1000,52,-952,-15,86,1000,1000,-196,194,-561,-3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00933() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{366,115,-550,489,893,792,-348,-20,823,-461,1000,-137,-615,-262,1000,455,-200,-1000,-887,1000,775,-1000,-892,58,1000,67,971,-758,1000,-1000,-982,-424,-715,-1000,-554,212,46,448,398,-446,-429,983,-1000,126,413,398,-673,-387,538,-368,188,-734,468,746,1000,5,1000,-949,-341,-621,-349,-1000,-506,421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00934() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{1000,992,448,81,-401,-1000,264,383,-1,111,508,695,1000,-1000,-240,100,-63,674,339,504,797,609,-144,-1000,-910,580,-121,856,-879,1000,1000,514,543,895,332,-27,-1000,-229,314,907,883,-943,966,1000,18,-144,64,833,-1000,-64,-1000,706,-145,-1000,-1000,-451,-947,-603,437,-278,1000,1000,960,-878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00935() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{324,656,651,-587,785,-821,-250,-605,-266,-986,-655,-98,547,-965,253,-541,893,829,-689,252,298,148,-379,-725,-958,562,116,872,-853,-558,212,37,246,501,-355,-525,260,848,-16,-312,954,-288,772,87,-289,876,-253,506,-877,-846,-798,411,-596,-732,-847,-485,-979,913,730,625,429,917,-79,-943}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00936() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{367,346,1000,-856,766,-209,701,452,503,-552,-366,1000,589,-189,-871,-506,-455,-966,-1000,-859,-1000,-668,79,557,-1000,545,-432,-627,-1000,314,-811,561,-693,758,-103,-1000,-739,-793,-119,520,-230,962,27,-1000,-556,599,554,-351,-719,1000,753,338,224,-713,-676,800,385,200,-597,64,-1000,-375,771,212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00937() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{943,1000,531,-106,42,-1000,-34,225,115,-1000,-555,738,1000,-895,-199,-188,726,1000,559,329,1000,-39,731,-850,-766,112,-242,-395,-1000,556,1000,454,515,1000,-172,-567,-916,704,413,720,1000,-798,903,1000,375,-125,-448,1000,-1000,-1000,597,554,155,-1000,-1000,-328,-702,-428,169,-46,962,-47,1000,-822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00938() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{805,165,-486,137,-245,913,1000,74,-1000,345,-1000,-563,-330,-504,786,-215,349,959,54,-336,-1000,69,-241,-1000,503,33,13,409,476,-786,-849,451,343,810,-83,1000,-752,-663,-272,654,-109,-492,-1000,-481,-717,-424,1000,-988,691,-789,-405,223,-1000,-489,321,-516,-733,802,1000,604,-522,452,26,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00939() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{706,1000,-474,589,650,-1000,385,260,-822,-1000,-701,-854,973,-309,-113,-354,1000,1000,-763,992,1000,-804,-960,-1000,-909,575,-196,1000,-637,748,1000,-355,79,1000,481,-528,-323,1000,854,627,1000,-250,898,-583,-75,-49,-1000,1000,-1000,-1000,-1000,-240,-235,-1000,-1000,-418,-57,-254,431,1000,-294,1000,840,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00940() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{-852,45,64,-349,-73,-348,-823,-312,711,-583,1000,777,670,-774,-1000,-1000,152,300,793,-118,1000,-1000,-488,1000,-465,953,1000,-820,-975,-140,570,140,-969,199,-1000,1000,-229,-454,-918,-140,-687,1000,374,754,1000,900,189,-400,-75,-177,-971,366,-91,-957,414,-1000,-301,-1000,72,-769,903,-759,600,222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00941() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{-454,-480,270,-836,-421,31,-1000,-1000,1000,1000,631,631,-1000,174,792,-1000,-645,-1000,-772,222,-125,1000,1000,1000,-267,461,572,338,1000,-515,-287,-321,-240,-716,-1000,-156,926,-696,-763,-1000,-1000,41,-445,-1000,951,1000,1000,-1000,1000,566,1000,1000,919,1000,407,937,80,492,-1000,-1000,-30,-281,-357,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00942() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{583,7,-507,431,-87,-261,498,-300,212,-28,935,611,691,-793,-33,316,-86,781,408,647,1000,-659,-479,-987,-161,1000,403,88,-1000,1000,171,965,228,742,194,1000,-1000,-646,-170,746,-97,-377,-103,974,-133,879,-243,-252,1000,-697,-930,795,-346,-1000,182,-424,401,-1000,392,-675,932,102,1000,-394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00943() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{1000,844,409,-343,93,357,481,-335,-549,-611,287,-797,873,-624,-157,-30,-181,1000,-63,-243,713,113,130,-793,62,730,398,1000,-1000,52,-13,1000,898,1000,130,-1000,-424,-1000,-368,1000,165,-178,46,59,233,-631,995,283,-400,1000,-546,-149,-1000,-1000,-400,-1000,-876,-78,1000,238,110,853,63,-829}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00944() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{1000,408,-752,282,40,961,742,-526,-1000,290,-558,-587,830,-305,597,-1000,386,24,916,-1000,475,-61,-76,-1000,1000,423,546,590,245,-1000,-33,1000,-153,1000,-308,487,1000,-1000,-57,963,467,210,-161,159,333,-1000,994,-535,250,-1000,-554,-934,-1000,-1000,-183,-1000,-95,298,1000,1000,-407,1000,1000,-900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00945() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{-1000,977,531,-192,1000,504,-100,17,883,125,1000,1000,-1000,-6,409,-253,-641,-1000,-721,1000,610,179,-734,383,343,-329,691,789,1000,-949,-978,424,433,-1000,-736,-393,-595,-1000,-526,-217,-565,-330,-657,-131,400,884,-85,-1000,1000,547,424,1000,225,255,1000,701,642,-672,-97,-1000,335,-818,-211,213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00946() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{366,-943,876,538,-636,-665,654,707,294,1000,305,-724,-207,-262,1000,903,-88,-1000,-488,-10,479,1000,731,916,-273,423,969,-758,-264,890,55,950,1000,-242,21,-644,-1000,-918,398,126,1000,-1000,-161,-67,-856,1000,746,-282,-296,244,597,1000,366,-1000,-687,1000,-504,346,-469,-621,-240,-47,1000,-189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00947() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZone(org.joda.time.DateTimeZone):void",
            new int[]{-45,57,889,-846,108,-341,-327,-1000,1000,198,-530,-619,-637,154,444,-1000,-2,-726,-1000,0,-1000,309,1000,769,78,1000,-17,469,470,-384,256,-248,900,-40,-233,-400,1000,-454,126,-831,567,305,-59,-701,-239,427,995,-674,821,-178,803,642,795,916,-691,-142,-1000,233,377,-769,-858,818,1,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00948() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{157,514,-132,508,172,-334,792,820,-212,415,205,154,729,-509,-291,-352,-286,768,-928,68,851,782,616,883,-507,82,92,843,-495,46,615,-337,495,109,847,-203,603,445,423,594,-363,60,838,179,990,-11,497,-716,644,-190,426,-34,-211,-918,277,644,112,-570,678,-778,-359,18,-266,180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00949() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{601,-316,-322,-109,-179,-61,90,-410,152,460,28,-95,819,511,-794,-465,473,309,-582,-268,295,627,426,-122,-920,402,469,766,48,569,1000,651,286,-714,-854,-1000,791,-760,65,116,-539,536,1000,539,-438,-51,134,-1000,502,-451,858,67,-215,-963,-185,550,-195,-99,965,-899,640,-1000,-619,-101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00950() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{176,-28,-174,-235,303,-361,-755,-494,-156,891,1000,-301,565,67,-469,851,196,-51,-245,1000,246,-273,-186,90,-104,296,662,-184,382,355,268,0,476,226,400,-615,504,-1000,400,-320,237,488,950,-580,400,65,-1000,967,-1000,28,-697,-1000,-756,-159,1,211,-741,-179,16,-577,148,-181,558,442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00951() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{766,-950,-199,-277,998,751,-540,268,-638,-139,37,-236,59,665,136,-45,604,-612,-288,-889,981,-866,763,40,-728,325,260,415,950,264,641,436,612,-283,734,-670,946,-460,855,411,-46,269,6,542,83,330,596,-334,-215,75,890,-166,-754,-821,-437,619,-422,128,102,-855,553,488,-406,115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00952() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{485,-463,903,509,-1000,-487,349,309,1000,-981,-9,236,273,-820,-584,220,-372,688,895,17,-1000,1000,-1000,-371,656,-959,198,662,119,488,-77,162,604,65,638,402,-775,118,-1000,-1000,141,723,768,56,-1000,690,-688,99,-177,387,-12,434,25,-142,67,-369,-493,1000,-335,-922,81,541,899,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00953() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{225,-891,409,484,-751,-530,-182,-327,-156,-114,722,-412,-45,-888,-317,65,-297,672,606,355,-561,1000,-186,87,767,-334,146,1000,131,1000,617,-517,886,324,1000,531,-300,639,400,398,116,891,745,-533,-409,839,-1000,-144,-1000,684,-544,-290,-734,-35,538,-279,-1000,526,43,8,630,233,1000,88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00955() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{186,723,789,604,718,915,1000,886,-382,771,665,-631,1000,-652,-947,129,-1000,1000,-786,638,885,300,211,1000,-1000,74,515,1000,278,-1000,-326,-945,1000,-536,1000,418,1000,-500,1000,867,421,37,-187,-240,-919,63,-121,-572,882,478,-69,104,-38,-1000,458,319,572,-1000,1000,-337,-430,580,-518,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00956() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{1000,-1000,602,-705,1000,189,-136,-231,756,-464,210,-35,-227,1000,-481,564,954,-730,747,-454,-780,-893,-414,193,134,-717,22,690,1000,1000,1000,896,799,-857,-1000,-1000,12,-145,-1000,-318,-220,-440,131,1000,-1000,906,650,-747,-169,-19,1000,532,-541,87,67,-730,-769,779,131,-826,1000,227,319,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00957() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{1000,-694,1000,-20,-514,874,213,297,1000,-645,-1000,-545,59,835,-333,-755,626,-95,-208,-659,-23,-866,-1000,1000,-261,-1000,676,-801,921,-1000,-1000,-379,1000,-233,-1000,-1000,-304,-52,-1000,411,-526,-364,291,-624,-1000,-337,905,-7,714,-492,739,1000,402,-347,160,115,-422,1000,484,1000,657,817,-546,115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00958() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{742,-659,-299,-911,88,-166,79,308,-191,-827,655,-356,-387,425,-81,605,-286,-337,-144,-726,128,-260,430,883,-468,-490,413,377,825,1000,1000,168,542,93,-1000,-612,525,-809,20,30,433,749,33,614,20,185,74,-74,-336,-190,648,-403,-738,-490,-576,152,-554,-175,239,-1000,500,605,-54,164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00959() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{1000,-1000,-237,-902,-306,-312,504,784,490,178,-344,627,-731,1000,578,926,-700,462,-524,-388,897,-357,-510,-15,-130,905,385,-755,627,234,717,-284,-236,-762,-185,-1000,-368,217,806,-47,-716,225,-827,929,-621,318,1000,683,-1000,-364,976,-181,-27,-958,731,145,1000,-231,48,-1000,682,933,355,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00960() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{1000,-1000,632,-355,-1000,-71,-221,-767,1000,-799,798,152,1000,937,-1000,1000,603,193,1000,431,-1000,1000,-922,-1000,-842,-28,-864,581,581,1000,1000,1000,1000,-915,1000,-1000,936,-288,-1000,-1000,354,1000,1000,-276,1000,914,-187,-788,-496,-348,528,372,28,233,1000,-323,-730,1000,986,-47,1000,-71,646,-592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00961() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{233,0,-367,-426,812,-375,775,628,568,-595,-512,449,-580,394,-370,-773,665,467,-971,-769,136,-867,-941,956,-999,17,914,-440,984,-729,-475,-737,427,426,-382,-189,-402,-657,-724,-625,-93,803,559,327,298,-985,226,213,954,-123,399,-54,-287,-852,-844,759,66,-108,430,761,-514,495,-611,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00962() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{273,-183,516,604,217,295,274,876,61,-801,-751,156,-265,-924,730,-976,-517,10,-2,-431,-467,284,-183,939,361,-578,250,448,437,-293,-641,-761,958,-462,-144,950,86,120,686,-901,421,-127,-325,-25,-919,688,-121,697,-365,696,85,816,-647,-423,-982,319,-243,575,-742,-744,-685,408,597,308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00963() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "setZoneRetainFields(org.joda.time.DateTimeZone):void",
            new int[]{1000,-1000,-123,-1000,1000,929,67,-342,702,-1000,-635,-399,-870,1000,54,-152,740,-495,-552,-1000,1000,-1000,691,410,-888,-830,274,-1000,1000,-680,-829,-459,850,726,-1000,-1000,352,-1000,-453,-1000,675,-77,-353,-301,-928,478,1000,697,399,-787,920,55,-56,-67,-1000,982,626,1000,390,488,836,1000,-1000,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00964() {
        org.junit.Assert.assertEquals("java.lang.String:MjAyNi0wOS0yN1QwMDowMDowMC4wMDBa", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "toString():java.lang.String",
            new int[]{-1000,-1000,17,271,1000,679,493,901,437,165,-750,325,-764,-340,394,-195,-105,664,467,736,1000,-1000,-16,241,-1000,-663,-422,-946,-3,-9,-984,395,-927,-731,-488,438,110,-150,-402,250,-523,-1000,-475,-587,806,1000,1000,-76,1000,219,-731,-527,-715,-669,361,1000,17,561,-459,-456,-543,-371,1000,-99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00965() {
        org.junit.Assert.assertEquals("java.lang.String:MjkyMjc4OTk0LTA4LTE3VDAwOjAwOjAxLjgwN1o=", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "toString():java.lang.String",
            new int[]{-225,686,-153,701,-928,-135,504,613,293,-1000,-1000,1000,-1000,-58,-1000,-1000,-498,-1000,549,412,-1000,1000,918,-910,-100,116,-449,-285,25,1000,-1000,-566,-41,1000,961,-1000,437,944,-1000,297,259,440,-1000,72,105,303,-7,262,-1000,860,-337,-1000,861,196,-1000,-913,838,-1000,427,-1000,667,-530,-295,-124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00967() {
        org.junit.Assert.assertEquals("java.lang.String:MTk2OS0xMi0zMVQyMzo1OTowMC45NzRa", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "toString():java.lang.String",
            new int[]{-1000,-1000,-845,698,1000,-836,-141,974,623,-639,-200,-484,-808,-481,1000,1000,-271,630,-182,661,1000,-82,-1000,-1000,-65,-1000,-1000,-1000,-835,620,180,913,-1000,-1000,-700,1000,437,-962,879,404,298,-1000,1000,-126,1000,885,1000,971,1000,1000,-895,-770,-1000,-961,1000,1000,656,1000,-1000,833,-829,-371,1000,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00968() {
        org.junit.Assert.assertEquals("java.lang.String:LTU4Nzc2NDEtMDYtMzBUMDA6MDA6MDEuMDAwWg==", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "toString():java.lang.String",
            new int[]{-645,-1000,-625,-720,1000,598,431,425,277,-72,11,-611,-582,-404,1000,-155,417,1000,-1000,768,1000,-1000,-1000,-522,1000,-931,-917,-1000,-291,1000,479,205,-470,-1000,336,1000,684,716,115,-1000,-815,-719,-109,-211,1000,347,128,842,170,1000,92,451,-1000,-1000,389,1000,321,653,-511,476,-1000,-730,1000,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00969() {
        org.junit.Assert.assertEquals("java.lang.String:MjAyNi0wOS0yN1QwMDowMDowMC4wMDBa", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "toString():java.lang.String",
            new int[]{-205,-392,-477,-346,-814,-1000,461,901,68,201,211,1000,626,-37,875,-151,-259,-1000,1000,-1000,-1000,1000,195,845,-617,-599,1000,1000,-332,-324,-1000,907,813,188,547,-210,-83,260,-448,723,243,-149,685,1000,-1000,348,-577,355,96,636,873,-443,413,1000,361,-1000,1000,259,-284,-390,-543,868,-724,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00970() {
        org.junit.Assert.assertEquals("java.lang.String:NjA1Mi0xMi0yOVQwNTozNTozNi4zNTJa", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "toString():java.lang.String",
            new int[]{78,-712,-455,1000,693,178,208,743,1000,-1000,-1000,-915,1000,-273,18,-71,-890,51,-666,142,1000,-1000,-1000,-1000,400,-434,-1000,388,346,-87,-840,395,-1000,-112,547,-119,379,935,1000,477,138,-1000,354,-1000,1000,44,1000,-538,313,400,-655,-315,-745,206,361,-432,864,-214,-1000,1000,-688,-1000,463,-488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00971() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{-231,-1000,1000,454,20,-186,-125,-1000,-53,241,77,731,140,83,770,-50,264,1000,92,-411,727,-433,-357,256,-20,-1000,743,-229,-311,273,-115,-27,674,1000,185,-121,1000,2,887,-123,-1000,129,624,-1000,-200,794,-945,735,165,995,-188,400,-325,146,70,-608,761,53,645,786,464,342,-110,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00972() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-29,1000,1000,-151,-249,354,505,-233,143,-299,-586,166,-249,1000,785,268,277,-351,-1000,1000,-1000,769,512,-889,716,40,-722,271,-652,-362,917,34,1000,1000,-223,567,318,1000,-540,-1000,891,-1000,-960,1000,1000,-838,-529,335,-1000,-601,-173,509,827,-92,-819,1000,1000,-288,-588,-1000,-154,224,294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00973() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-509,-516,189,-456,622,409,95,-377,-195,735,858,-462,624,223,1000,-29,750,230,-95,615,-778,194,809,818,-510,-783,-903,-497,647,5,1000,597,-211,-763,713,488,1000,1000,-962,1000,-1000,-565,284,68,-387,225,187,412,-455,-376,-589,-3,561,-257,-171,-288,-1000,35,390,368,-875,393,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00974() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-880,-1000,-376,-987,-893,-506,169,1000,-1000,967,-661,-120,-1000,-1000,-446,-1000,1000,-1000,372,-222,1000,-1000,-86,698,664,474,1000,-1000,-12,483,-1000,1000,-944,580,1000,-393,-1000,-1000,1000,-308,1000,-3,-1000,795,1000,-1000,438,-1000,1000,1000,941,-153,410,399,14,422,1000,-536,-1000,1000,-152,-359,-798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00975() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{452,675,369,614,-117,1000,91,1000,310,-38,283,168,185,1000,1000,124,-464,1000,-581,-747,387,-679,931,626,-202,338,-1,403,-333,-157,-151,1000,-424,704,285,45,-1000,-141,703,-353,-679,-785,468,-469,-448,-91,547,54,-27,-640,-825,-563,993,724,653,-634,-619,-400,26,821,-945,-705,-202,454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00976() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{-1000,-787,-885,209,-69,-1000,444,806,-147,-455,506,1000,-88,330,-1000,234,-633,1000,-1000,624,-986,1000,-766,703,1000,1000,1000,976,-945,-1000,19,827,848,-722,-615,1000,-1000,-1000,-1000,1000,-1000,1000,372,284,973,-184,-1000,-139,-1000,1000,1000,1000,292,448,31,-330,439,1000,-1000,-1000,961,111,-783,-674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00977() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{826,-897,-37,823,-126,1000,64,211,-246,-235,228,1000,-116,1000,796,822,404,283,426,-697,730,-1000,177,448,318,-502,-1000,-757,1000,847,-79,1000,323,1000,-11,-843,1000,777,1000,-1000,1000,-1000,-622,859,-475,-184,229,-617,864,-1000,423,-367,-667,569,318,-740,1000,-1000,864,1000,-75,-722,1000,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00978() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{771,325,-84,-17,-791,391,-693,-214,496,-192,513,404,-188,487,34,453,-753,107,-781,44,-518,-530,-143,562,970,302,-851,85,-839,769,151,295,-507,-119,-548,366,642,-110,168,95,830,-173,-832,302,-151,-613,846,-580,932,531,-249,-920,-419,350,884,537,-969,-954,-405,-232,156,-832,665,239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00979() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{1000,681,369,652,94,1000,183,-594,224,-38,283,761,14,1000,1000,-22,439,1000,-218,-551,1000,-1000,1000,69,-812,-469,-597,-183,804,-82,95,1000,-530,1000,1000,-132,-575,330,795,-1000,272,-1000,629,-818,-964,-295,1000,-168,1000,-1000,-1000,-1000,529,772,524,-766,-994,-412,1000,1000,-934,-706,248,782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00980() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{-189,719,751,-71,-565,484,-1000,592,1000,-895,654,-612,-152,180,363,-289,-926,335,-411,-950,447,374,-25,93,282,1000,292,1000,-1000,270,13,-150,-111,1000,580,21,-1000,-559,-400,1000,-611,88,273,153,271,210,173,907,-32,-100,46,-88,55,-41,1000,-297,-720,400,-784,363,-873,-427,-1000,20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00981() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{151,196,-1000,997,384,-136,38,-688,705,-1000,967,-334,-763,312,-955,-66,193,1000,154,752,556,153,-305,-417,-82,510,-1000,-214,665,52,613,-717,859,704,869,144,-393,-720,-946,526,1000,-384,490,1000,577,237,600,-205,1000,-282,67,-161,-91,370,217,-349,-794,-537,403,-651,1000,-346,716,-421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00982() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{1000,-502,-1000,-515,264,57,1000,566,-389,68,1000,-155,-478,212,-768,-246,-4,1000,958,1000,-655,-91,116,-624,-650,-766,-183,-1000,1000,-191,223,-1000,1000,-682,1000,759,-986,764,-46,150,1000,-1000,256,1000,886,-293,600,-422,-1000,-393,-225,-265,-929,-171,-1000,-1000,-397,-156,446,-207,380,-227,794,-481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00983() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{-12,-1000,1000,86,-412,-197,326,-600,36,705,-360,1000,-673,874,1000,-733,602,284,1000,-1000,1000,-1000,-125,400,-51,-1000,-516,297,450,705,-149,1000,614,1000,1000,-1000,1000,-269,1000,-934,-450,-38,-215,-643,103,469,-89,425,-523,388,-1000,-763,-346,64,-425,-1000,1000,-411,211,1000,-1000,211,348,-369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00984() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{-698,-446,-18,-256,-347,-298,-105,612,638,-327,506,-533,-51,-841,-490,171,-633,1000,-639,-140,-387,1000,-254,616,-25,968,1000,833,-945,-383,-80,-816,768,271,-527,1000,-1000,-890,-1000,1000,-1000,554,460,-927,609,1000,-1000,-18,-804,831,805,786,-51,283,307,-366,62,1000,-821,-842,556,-149,-802,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00985() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{987,-1000,-1000,247,-1000,-346,-46,631,204,-976,1000,479,-53,-607,-575,307,-1000,1000,-627,159,-326,-41,866,714,1000,-472,-316,-649,-826,311,477,1000,1000,-433,-66,1000,363,579,63,408,-679,-305,-1000,-1000,294,240,-241,-210,331,790,746,-143,-73,598,8,-518,-338,230,-13,-738,1000,-421,96,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00986() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.MutableDateTime$Property", DEReplay.run(
            "org.joda.time.MutableDateTime", "org.joda.time.MutableDateTime", "weekOfWeekyear():org.joda.time.MutableDateTime$Property",
            new int[]{115,-320,-336,-372,788,-905,-1000,537,-377,-1000,375,-502,324,-1000,-1000,1000,27,933,-553,629,-884,1000,-1000,576,110,1000,551,5,-497,270,5,-1000,1000,-670,-1000,713,-382,-810,-1000,1000,214,822,735,-1000,769,574,-1000,187,-1000,712,1000,662,366,-149,1000,-171,519,715,-562,305,1000,-493,-66,-1000}));
    }
}
