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
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "days(int):org.joda.time.Period",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "fieldDifference(org.joda.time.ReadablePartial,org.joda.time.ReadablePartial):org.joda.time.Period",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{-1000,156,-1000,1000,1000,743,-360,-114,-1000,-628,-360,-1000,-148,-1000,48,-143,795,1000,722,-1000,1000,-95,762,1000,28,406,-1000,-1000,-315,307,410,1000,742,-135,1000,-349,-830,282,348,-722,-1000,188,-1000,-950,-156,-878,-1000,-1000,1000,1000,-311,939,-306,98,-886,-681,-683,1000,-145,-1000,-501,380,799,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{83,592,400,548,-144,607,-743,131,765,-231,-274,505,-807,442,-804,-508,453,-148,533,-538,-360,-423,-57,724,463,637,211,-973,912,254,-957,883,-22,-100,-878,789,585,-68,-39,-142,401,-220,-302,-172,245,468,-465,4,-540,18,710,-745,779,242,-406,918,-5,-666,803,-869,29,719,-951,985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{-783,-100,-718,316,563,-955,-563,-140,-185,142,-217,510,93,231,290,130,-441,646,-427,583,-76,735,-119,770,910,313,7,-22,-355,34,-58,370,-670,436,43,-191,470,64,907,-341,451,799,212,659,-680,243,-624,-459,-756,895,20,-348,315,-302,-21,536,129,-475,858,159,863,175,-21,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{-315,-864,-468,-608,454,-893,-421,82,-455,388,131,-673,-902,-737,683,1,650,527,976,194,105,669,186,902,-606,-114,-327,933,395,450,-760,-328,-962,-457,728,-216,-519,-709,-750,300,-252,-529,-566,56,-747,-897,-420,-22,-691,-21,864,342,-139,71,-634,-221,-526,-271,71,571,893,570,-258,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTI=", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{-612,71,54,-223,-469,-12,-697,-854,35,-35,546,-41,-103,930,133,654,149,231,-768,933,273,-162,205,707,543,410,-580,-459,-608,-475,486,557,286,-30,773,-888,-503,-381,-444,22,697,125,160,-454,364,-552,-925,-66,-429,-971,178,919,986,357,-103,239,609,-406,-544,-20,105,965,-816,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{942,-480,-558,224,-330,492,911,-595,-178,-912,-863,558,-587,290,-10,-739,-925,575,877,423,265,949,-857,-728,-610,-584,191,948,8,732,-654,-805,641,912,391,-925,670,-427,-806,-15,-718,912,847,-204,525,-114,-640,456,-885,-10,94,14,869,923,849,-297,371,376,-193,-559,773,216,-650,931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{565,-602,237,384,-486,87,518,808,-949,-309,172,828,874,-256,-500,989,-265,936,981,-316,1,-29,528,937,889,377,837,-411,38,-77,903,-330,-492,-114,-338,-860,193,21,180,583,158,-171,-115,948,-524,-530,-120,-49,758,481,572,389,767,-603,-441,275,248,266,1000,442,438,-871,-465,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTI=", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{172,717,580,-436,-834,144,-167,-203,-357,716,-558,992,-194,968,-666,901,-683,-575,707,371,-493,-109,662,268,-769,-754,-800,899,-878,-9,709,279,-629,319,-32,985,106,-857,-832,-250,922,249,-527,-553,-780,450,255,-359,-410,-490,-713,-471,704,442,-107,-272,-79,-204,355,-866,-893,514,697,-138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{416,106,-140,-647,-23,385,-707,-363,-226,949,-306,1000,-408,403,-178,646,-1000,-148,1000,90,-610,272,668,-683,-1000,-228,-322,189,-835,581,882,392,-59,389,-585,92,-528,-541,-569,-383,953,755,-611,-352,-661,771,485,400,248,-208,-593,-251,677,-35,-567,-758,526,-119,773,-1000,-219,754,485,529}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTM=", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{-783,351,-265,85,-1000,-836,-1000,1000,316,-1000,-440,-304,946,1000,-524,598,1000,-26,-821,695,920,-456,372,709,-606,-922,-673,-10,-231,450,-690,-535,-962,995,728,1000,1000,566,260,324,-1000,-323,-566,4,587,-796,-578,-22,-1000,185,864,46,-186,29,-634,1000,-506,216,-52,1000,-857,570,36,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{-504,914,672,1000,-688,54,-872,605,900,-1000,-145,1000,-250,435,-969,-976,1000,276,-680,-431,333,-963,-558,1000,1000,496,198,-665,1000,-740,1000,412,-363,328,-790,1000,1000,252,346,124,-573,-1000,368,40,823,-527,-1000,108,-1000,28,1000,972,121,611,482,1000,-956,-607,-263,-27,-224,-203,-1000,-11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{290,985,-194,-637,-839,1,6,-954,941,1000,-736,64,-329,382,-74,936,-1000,-1000,1000,362,-1000,-860,1000,-1000,-1000,-1000,-1000,1000,-1000,-1000,-710,-1000,-958,-968,734,80,-1000,-1000,-669,796,245,1000,-649,-1000,544,605,1000,799,-427,-1000,-1000,220,791,-236,118,-1000,1000,-199,-1000,1000,391,-1000,127,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{-602,272,-82,805,86,-437,-524,1000,1000,-381,351,965,617,526,-504,854,51,-1000,1000,-1000,-1000,-556,898,370,-605,1000,448,78,1000,1000,-232,-524,-1000,-905,622,620,1000,-120,1000,967,570,1000,-96,-328,-913,-188,-138,1000,-198,1000,640,-1000,1000,-68,-888,1000,1000,-305,1000,186,91,1000,-314,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getDays():int",
            new int[]{217,-637,-684,1000,393,374,-697,1000,-649,881,192,47,1000,-376,-612,58,1000,983,-481,-1000,1000,116,-399,871,712,977,1000,-1000,1000,593,-136,1000,-493,108,912,-468,1000,1000,1000,706,-1000,889,-971,591,668,-783,-1000,416,-86,1000,1000,-11,367,-1000,-173,1000,-298,-305,452,-628,1000,133,-1000,788}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{828,-284,-79,982,-391,-420,1000,403,-423,22,-1000,1000,472,856,-636,-406,-1000,-132,775,703,754,-200,625,-155,-904,310,-443,-1000,412,1000,-249,1000,-197,-48,845,299,-533,364,311,-1000,-21,-82,-540,1000,673,-447,578,456,-1000,109,122,-818,-1000,551,-89,-313,303,-45,-712,939,1000,699,750,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{-837,-266,-1000,247,-707,567,957,257,285,926,728,350,388,372,-5,-897,-271,188,-1000,365,769,310,-246,812,1000,-126,-1000,469,-1000,-96,51,339,675,700,207,-998,1000,603,426,1000,1000,1000,-721,-1000,-1000,-582,283,-1000,1000,-988,-250,-448,-847,-23,-62,250,-1000,-559,1000,-1000,-421,721,-24,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{131,-995,-258,885,676,-869,82,-965,435,-932,458,394,-229,-772,837,580,38,-367,668,-898,-437,-638,-997,168,-208,-1000,130,174,-554,83,-75,-477,502,-231,845,774,-196,-134,-699,972,231,711,-6,-533,-554,386,-257,-470,496,292,287,613,759,-821,-367,277,115,-585,600,449,-439,-519,-607,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIw", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{-34,-551,294,63,-799,-410,819,217,409,115,-905,937,878,555,-400,540,-363,314,912,-724,704,-485,-341,-864,-293,-564,479,-198,522,518,449,916,-486,-740,217,214,-26,-877,835,-115,459,977,-830,14,-165,-373,-305,-64,-920,438,428,-115,-874,219,-125,493,-610,-830,-102,323,999,-114,634,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{-269,-532,-683,369,-960,-17,379,874,158,736,674,246,-109,-580,495,-433,127,576,-422,467,446,-5,-350,769,778,-579,-568,-333,-930,-353,430,208,909,388,553,-808,27,156,434,490,748,784,148,-812,-489,-816,-6,-998,876,-492,-59,-61,-358,-74,438,-652,-954,-81,352,-859,-573,216,23,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{-659,276,-381,748,161,507,733,-55,432,-315,-150,-472,639,-224,-343,-940,-35,899,-741,959,814,556,35,421,315,801,-577,277,-661,-488,-484,-350,-464,199,94,-419,889,-341,-493,677,377,150,-315,-732,496,778,-517,-442,554,-708,-453,-160,-35,572,-54,940,-934,-213,910,775,-612,392,-799,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{379,620,354,811,-681,-980,-226,664,101,-538,-348,419,290,-972,-263,392,533,945,139,360,86,159,-15,-299,-798,123,948,-694,38,-897,-341,278,-296,554,-259,688,-907,-995,9,-422,-810,246,242,823,748,640,-145,457,-634,874,-213,-336,315,572,-36,-288,-226,-150,-188,861,-475,133,-522,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{-628,-386,-79,982,624,711,-257,513,-423,22,637,49,833,522,542,-207,238,-835,830,-780,-44,55,478,-155,559,-942,-929,667,139,231,487,864,-591,-268,845,-366,769,719,-889,-68,405,-844,603,-660,-397,-583,578,-744,713,109,-962,-818,415,622,991,16,303,-402,461,939,183,-302,-252,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{1000,-952,294,-1000,-1000,-531,-362,1000,-630,-43,1000,136,-697,295,132,268,1000,147,912,700,-15,310,-644,-729,448,-508,-538,-198,1000,-96,-401,845,1000,1000,-1000,693,54,-658,-876,-479,459,977,443,-417,-1000,-582,-117,1000,-1000,1000,428,-448,430,-1000,449,-123,889,-830,-913,-977,1000,-114,498,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{666,629,-827,-281,-1000,900,470,-538,1000,418,49,312,592,490,-1000,829,869,108,499,-427,805,529,-1000,-157,498,-585,-1000,-290,728,589,-1000,410,1000,739,-897,988,1000,456,-563,735,-243,9,-310,-91,-1000,339,-873,869,-984,1000,-1000,630,-133,-869,-1000,201,-1000,356,149,-566,492,1000,106,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{898,162,-923,-137,-1000,427,-699,-308,263,1000,312,693,643,975,230,828,-920,133,218,-1000,431,550,-963,-681,124,5,-513,-434,590,51,-1000,962,92,-310,-1000,1000,-916,1000,-763,371,270,-25,-925,1000,-427,-665,-570,172,-422,1000,-712,945,518,-1000,-993,1000,-461,559,683,-605,55,1000,140,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{421,-228,908,-737,-714,286,189,1000,-5,425,-2,-1000,-572,-402,-915,801,584,557,235,1000,-556,650,-954,182,-40,-276,-1000,1000,505,444,384,34,461,1000,-291,-1000,1000,-387,-367,-1000,856,108,1000,1000,-325,-326,588,381,312,-1000,-703,-671,731,-434,597,-1000,446,-961,-243,685,-592,120,125,333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getHours():int",
            new int[]{863,-909,599,506,369,376,-737,-280,550,-1000,1000,523,83,1000,1000,1000,1000,-412,1000,-1000,-69,-101,-762,-1000,1000,-1000,1000,-503,8,-1000,-1000,-1000,1000,294,8,-134,-526,1000,-1000,1000,1000,928,216,-1000,-1000,88,-1000,-14,-1000,997,899,441,1000,-1000,-712,657,295,-142,793,-956,-22,-1000,-197,-883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{-780,684,-752,278,-342,201,-324,-374,648,-392,913,-238,238,-806,-530,-180,792,192,855,840,464,213,717,220,577,-625,564,408,233,240,721,188,-986,661,726,158,729,-340,505,-645,-185,629,358,-648,-766,-756,36,-370,475,-204,996,-490,631,871,-683,-434,411,-268,-331,-985,438,132,-722,-660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{326,196,-406,-987,-539,186,121,866,91,90,510,200,997,136,672,-612,-636,518,264,-654,704,528,-246,-186,761,-779,810,-753,846,557,291,23,526,969,617,911,732,-667,-529,-907,-9,460,-561,409,708,-644,-531,977,-800,784,842,-183,553,926,707,726,-741,-690,193,275,-935,-389,-390,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTgwNw==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{-644,431,643,608,222,636,86,88,733,31,-832,-716,474,-642,897,260,839,917,-681,-546,-532,-870,963,-951,901,-803,292,469,729,-338,-745,553,-926,-294,-358,-768,335,42,-962,-111,-242,-632,864,-62,-343,547,279,-358,849,-127,477,-315,249,-859,341,618,-537,-472,321,-79,-319,786,922,-866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{-790,808,824,150,674,-563,917,-906,574,-551,-87,-427,387,768,-367,-84,13,291,-162,-268,-835,-296,199,-800,874,-889,-62,-765,444,855,316,648,-238,921,-279,683,-59,-666,-797,-318,938,103,45,962,-974,-595,154,-496,-254,-24,919,-479,-643,-66,520,-414,-339,-335,-354,629,-402,780,-945,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEz", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{4,674,-512,119,-464,757,-925,113,-79,20,-450,-361,-577,302,365,-332,669,-662,-921,-92,865,477,-701,713,804,-182,-118,978,66,440,-902,-63,-678,-629,-959,36,764,25,680,-611,336,900,638,-285,663,553,-945,-900,-648,-940,-328,465,-302,-492,693,184,-279,-611,-128,949,940,-778,-174,675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{505,-608,291,-341,186,-582,-704,-835,37,785,-651,-125,-527,785,148,-350,699,133,518,-711,-772,-957,-290,948,80,-575,644,296,-867,-532,-148,-104,594,808,459,533,-324,-437,-335,564,221,617,896,751,219,-304,999,-513,-24,37,733,851,9,-261,125,-496,670,245,-753,84,961,-172,-561,456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Integer:NjQ3", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{42,446,86,-57,-471,-120,-326,-491,-172,-338,-951,725,-509,-992,-456,348,836,-329,964,-697,-43,455,286,-985,603,976,-625,90,-559,188,-969,635,696,180,-786,-285,-711,-219,-539,-205,-764,-340,-831,423,308,724,680,-167,708,917,929,-307,906,-221,-236,792,-366,-516,-979,92,-438,-460,-78,303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{169,428,-634,-308,-555,246,57,444,507,602,-578,903,140,-360,-173,137,-96,-261,515,638,-67,460,-665,941,221,-927,-259,-492,957,28,-24,449,-988,702,890,944,-194,-161,-15,-422,389,-837,-39,154,846,-135,302,-976,871,-131,236,-484,737,-904,795,569,776,296,636,374,964,463,785,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTU4", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{-610,-359,-587,-2,-420,-148,310,-546,-170,765,-115,-362,-733,-948,-955,-939,-651,895,317,-61,654,-535,623,516,49,706,14,396,-144,-656,308,-974,203,-983,-821,774,-182,-490,314,-666,-810,129,-981,-372,-745,-514,828,788,524,156,548,-642,-73,-7,514,-610,-431,232,-223,-190,-958,-43,449,-927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{-543,-307,204,-296,-266,-202,400,-653,104,990,-1000,1000,-370,-863,-255,246,1000,-364,916,233,-371,975,1000,-907,1000,104,-795,979,532,-407,291,-191,1000,504,-836,294,-1000,-814,-533,86,-759,-106,-1000,266,66,1000,684,-18,614,-143,118,607,971,-269,-485,966,-525,-1000,-564,275,-935,-251,-390,-916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{699,963,-663,570,-297,455,-1000,-181,732,-998,-678,357,-1000,-1000,293,1000,159,-1,169,379,-593,-109,-899,507,-514,-287,433,214,-655,67,368,832,-1000,-34,1000,-524,-393,945,-312,68,316,-1000,1000,-830,362,-402,1000,-932,1000,0,-4,-565,1000,-797,-181,549,1000,1000,494,-854,1000,39,686,-118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{-122,-1000,234,10,-420,-54,-645,-556,-91,835,-901,-1000,-913,337,414,-446,-651,527,-543,-1000,-25,-1000,623,518,108,760,739,798,-1000,-1000,308,-974,203,167,313,-827,486,-59,314,1000,-133,1000,1000,242,-268,490,530,-369,66,641,953,-642,-73,-328,-340,-449,507,564,-626,-877,-155,-43,-410,326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{257,-59,610,-396,-900,-448,-708,-402,-905,339,-605,-1000,33,1000,-760,-168,248,-1000,857,-347,-737,-859,942,-363,-585,628,630,-403,-994,-34,197,180,-47,-425,310,-97,127,93,-466,-1000,-310,-167,-19,863,-546,127,1000,-226,790,267,953,-1000,-758,-908,-881,-1000,250,-37,-1000,-288,-155,1000,-760,703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{-1000,-290,-204,522,126,588,-355,-402,506,152,-309,441,1000,990,-422,-544,-144,437,-638,-662,54,280,610,-1000,171,-1000,-1000,800,1000,853,-1000,180,-829,281,-1000,190,-793,-681,168,590,935,383,-644,718,451,707,-10,-1000,-310,-1000,-1000,1000,635,121,555,481,-1000,-37,1000,966,221,610,-27,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMillis():int",
            new int[]{-1000,487,-843,1000,500,1000,-166,-1000,732,-421,100,80,1000,178,1000,-578,1000,-480,790,307,579,-108,-1000,-1000,1000,1000,-299,232,1000,1000,115,731,-102,-1000,769,-1000,152,1000,-664,-120,-1000,-1000,-179,-1000,-527,1000,751,-1000,1000,223,288,-747,654,-971,-1000,-165,-1000,1000,1000,-622,-441,62,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTMx", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{139,-42,154,496,-444,-484,-783,553,-575,144,707,-1000,325,1000,180,1000,-771,-1000,233,977,1000,-46,-1000,-279,773,-835,1000,284,869,1000,-307,-1000,-218,111,307,-441,774,1000,-1000,-750,-1000,-1000,731,-1000,1000,20,-499,196,-50,-1000,123,-1000,1000,20,-580,640,-954,26,-369,1000,-764,-287,830,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTI0", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{850,-508,-685,312,-246,-782,322,936,-695,-36,510,-53,883,-74,601,754,-825,658,-383,-646,44,-479,-988,-370,296,660,-606,584,-180,-93,953,793,-113,397,302,277,-258,610,72,-584,-950,-921,-704,613,247,50,426,995,399,-2,597,-431,768,119,696,17,-29,654,-272,566,-642,-786,-26,-777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTMx", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{166,-448,-556,723,472,-502,-255,487,-825,-405,-387,-896,823,541,110,268,-889,-51,341,623,-350,-655,524,-97,33,-262,947,-796,-19,-43,-741,486,-315,-627,105,-689,767,-462,747,266,-247,-242,52,579,414,-261,-40,28,885,-2,398,382,78,-21,484,-631,-397,547,833,407,100,-176,-217,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{740,-747,453,-187,-527,172,-335,384,-466,222,-5,31,724,-573,463,519,6,-23,-288,789,-920,-636,-74,219,775,-168,163,-580,-675,-120,343,-328,-87,-59,-335,36,199,324,-103,-367,-1000,959,680,-372,759,-396,760,-960,996,-237,-126,665,-452,-239,940,115,-934,187,-879,267,-14,215,-620,-751}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{981,575,-366,811,-882,-666,-760,838,173,299,450,153,-17,-415,-731,474,-765,-994,-503,448,715,421,-877,852,-102,-631,727,-406,525,807,642,-318,-895,151,244,-731,-385,726,-634,-685,147,281,544,-531,679,255,-444,368,-260,107,571,674,-661,-758,-912,-48,176,783,828,-291,931,768,-271,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{197,-763,-685,-131,-508,94,800,194,709,650,551,385,-646,-703,744,-193,-33,913,432,787,218,274,-411,619,-616,-11,185,256,-908,-673,-165,883,-429,-366,-821,513,973,-987,-248,172,762,-574,848,-672,668,-550,-383,754,237,-468,486,222,617,-207,-122,128,-96,-822,160,416,118,-944,767,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{-981,-852,557,71,-297,-535,-875,307,-150,773,-393,211,-68,316,238,-646,325,-730,396,-5,89,-818,-830,851,150,-323,-943,-807,-337,358,681,580,609,230,-993,-555,-954,-111,195,217,-145,-756,905,-312,-684,584,659,965,393,-559,681,-556,-789,-114,-461,29,-99,651,868,60,698,-228,-556,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{-993,343,-983,468,993,396,384,-204,165,-74,-560,523,-552,-340,189,986,250,-734,-963,-501,152,-581,67,482,30,371,477,-182,-343,-500,840,747,111,-324,481,568,-301,-758,-159,-153,-477,883,-533,-107,-705,751,993,835,-438,805,-685,-180,-887,561,-923,537,17,-856,509,-408,-530,900,-308,75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{-570,-334,418,82,-119,253,917,-63,-493,703,972,353,499,-580,-817,923,326,-558,478,-62,360,-233,365,779,-543,934,864,910,-197,856,-402,838,653,-728,71,311,950,-842,-172,-492,215,886,-114,8,624,44,-399,-105,-314,403,-54,-445,600,-255,-896,-590,0,4,-812,121,558,473,-35,-334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{712,-260,498,59,-372,413,810,-510,457,742,814,-815,912,764,-897,361,-749,-230,-559,-655,-142,536,257,114,-213,56,-154,-675,-122,293,-458,-787,-657,-129,672,690,-915,540,777,410,443,779,247,-391,533,944,341,-863,904,4,-665,247,852,-905,-518,417,396,-122,-610,-209,649,-8,455,639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{-846,-212,74,-326,558,-53,482,-994,974,598,290,77,-663,-877,47,-19,-882,-41,-509,-728,873,-629,539,546,273,-517,530,-114,-501,587,740,606,190,-795,-211,634,-515,-732,172,-663,-869,-714,878,629,-893,-525,-182,824,-226,-601,617,-204,160,887,-797,793,680,754,606,-507,303,-856,-628,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{993,193,104,-101,126,185,-250,392,464,307,498,-773,-689,758,-606,-485,-530,19,101,359,776,-183,-334,-626,129,585,559,943,183,-21,-195,-307,172,449,601,-453,692,761,-574,206,-319,895,-588,923,-649,-226,447,48,-661,959,367,-32,-12,573,-549,795,-679,524,400,431,11,367,0,-511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{-134,780,448,-100,-998,750,-314,535,-419,205,751,166,856,413,-431,-130,-565,674,804,-643,819,584,958,-983,978,770,312,981,693,-250,-914,722,674,-61,760,-364,-138,791,-834,495,723,-449,-369,-281,-183,138,244,997,-481,347,-858,-55,-679,722,-393,-786,870,-809,710,-250,-402,-92,-732,731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{0,489,-125,1000,-1000,-452,-455,0,-854,372,927,346,796,-208,-1000,1000,0,-1000,188,914,356,698,-999,1000,-674,385,961,311,0,996,-157,0,-571,198,441,0,641,648,0,-565,906,1000,-150,-966,0,653,-596,0,-321,809,102,506,-353,-1000,-981,-1000,-301,258,-165,149,1000,1000,0,49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{-228,-1000,537,377,-438,-234,134,76,-199,1000,510,-861,616,-74,-838,-586,-683,-366,-334,-393,-603,180,-238,1000,-503,375,-788,-1000,-311,280,-230,662,-174,562,-176,-201,-1000,-270,1000,20,701,87,1000,31,-576,1000,378,515,399,-2,308,-745,-641,119,-654,-356,633,917,835,70,931,-522,-185,21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMinutes():int",
            new int[]{776,-314,753,-707,-750,575,804,127,1000,1000,1000,471,-1000,-551,243,-720,218,962,264,602,1000,604,-1000,249,-549,582,-87,1000,-767,-657,217,328,-88,387,-980,678,921,-131,940,130,712,222,400,-431,-754,-526,-42,768,-844,204,464,-68,554,209,-845,1000,-293,-838,-143,433,56,-564,918,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{-682,508,-621,311,-1,627,-736,930,-918,580,-912,146,-342,511,-356,-175,571,-99,-14,-750,-919,342,168,592,474,-935,-537,-59,-325,879,114,720,588,339,-5,-586,-565,503,-242,-22,642,-488,999,-947,9,-700,-228,-856,-214,295,379,-687,104,-333,-196,593,722,821,-660,358,469,215,-937,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{-111,790,-628,-531,-279,-332,-832,456,1000,176,-110,250,-189,720,-581,-972,421,866,226,48,-1000,-308,-1000,249,108,-495,317,345,467,-1000,1000,894,1000,102,189,368,-123,-1000,741,59,-690,-323,399,-993,785,514,-876,-376,230,181,715,-268,488,1000,-75,184,440,-260,-328,370,431,-174,-511,-927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{-14,-392,909,-993,-670,-910,-832,947,-582,-597,-535,713,571,720,-35,-118,755,349,-615,-508,280,-213,197,392,108,-794,430,667,-505,466,350,-66,464,965,616,415,50,153,-301,126,625,394,312,255,-274,-24,-585,887,-248,580,-67,560,680,249,764,-135,-513,7,866,-744,383,-317,-731,569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{137,201,-300,363,792,-389,171,-885,-110,500,382,-439,-26,-953,-952,19,538,966,-609,420,928,845,-56,-82,-310,990,-467,-515,810,-240,-141,670,-51,-370,457,852,302,-304,945,-287,30,219,504,-571,-654,-71,272,127,-711,274,973,516,-31,595,-496,-710,-348,260,422,-105,-572,234,-656,-446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{82,-127,424,-466,527,181,673,-203,-618,-444,348,412,-428,-43,12,22,543,98,462,-51,-70,622,51,86,-703,-33,-75,456,352,-342,41,-159,-293,961,-764,-979,-209,334,-292,-410,820,-1000,341,41,-114,75,0,903,160,-849,-392,44,-33,-958,183,-30,64,443,-741,927,-167,-308,148,906}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{810,541,470,-989,262,264,964,36,-701,415,-565,-384,922,-113,36,157,347,-562,-771,-367,683,-854,-299,174,60,-118,-924,785,-146,94,74,658,393,-671,141,628,-983,505,731,776,-323,-246,-585,-94,-573,-184,-243,-295,-933,414,525,740,999,849,-786,-861,883,88,-931,-190,-881,79,-125,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{-682,696,583,-791,-3,-253,571,-519,496,854,-261,112,798,-396,16,184,-650,386,-125,942,-740,579,-557,-649,-700,-660,460,186,51,-841,923,515,-792,-452,594,530,-430,-238,696,143,585,-728,-856,-523,-576,905,-611,-536,528,-40,624,113,-679,-549,-546,707,361,673,409,175,579,-288,688,-822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{391,800,-927,-945,366,-653,-79,952,41,-13,-426,497,183,-361,-666,123,-99,351,-862,340,595,670,-77,677,-166,323,64,-651,-538,-919,-801,-289,911,902,77,698,-769,952,-778,864,-345,-23,236,-622,663,-386,-707,409,-559,-294,-989,-318,-593,481,321,-321,-959,491,135,946,637,-319,-302,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{795,-411,345,193,-507,-217,44,966,-627,904,-400,862,-460,406,-328,-372,608,983,874,-730,-29,-2,-878,279,386,778,-851,978,794,-422,622,-535,54,-811,-324,-267,-285,-273,-184,-980,-664,949,141,129,948,617,-374,566,225,-129,-198,-321,689,-634,583,-319,-166,-993,-767,431,458,-486,493,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{-172,1000,616,-1000,-188,-195,775,-351,438,1000,-900,-445,1000,-445,33,279,-787,-76,-988,722,-213,-455,-802,-587,-166,-719,-134,416,-297,-536,946,1000,-51,-1000,1000,1000,-972,-119,1000,974,-215,-200,-1000,-617,-897,724,272,-1000,-236,845,1000,600,-31,716,-1000,126,934,425,276,-607,78,-17,496,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{1000,17,869,592,767,-1000,462,-55,772,758,-265,-860,1000,992,-309,1000,271,552,775,-1000,1000,-53,-59,699,-180,108,-142,-64,-652,-573,970,46,-1000,-773,871,-449,-1000,727,-37,-1000,-516,662,800,-880,194,474,323,338,-721,1000,-850,-219,809,121,400,-242,-397,350,-740,110,612,-167,-176,-504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{-115,683,189,39,112,232,1000,-506,465,1000,-166,217,76,-616,-189,-567,-753,830,-489,787,-957,726,794,-765,-506,-359,-437,404,961,-1000,1000,186,-1000,-320,-64,52,-665,-537,778,-631,-318,-339,-976,-611,280,1000,-463,-761,860,-537,533,-505,-673,-1000,-672,578,603,-26,-734,998,631,-407,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{311,-117,92,-57,-288,1000,95,191,630,1000,-823,-337,519,-384,664,-587,1000,277,-1000,-864,-716,1000,373,-566,-751,-671,332,1000,376,1000,1000,940,1000,-357,-905,484,-326,254,1000,1000,601,-574,-627,-775,-1000,-656,-116,-1000,688,7,811,1000,1000,473,-1000,-1000,-706,71,-1000,-1000,-1000,-49,-568,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{-584,273,869,-417,-328,288,-772,1000,-896,4,-265,-860,1000,859,-309,-79,1000,-1000,153,-1000,-768,-747,-1000,1000,338,-1000,-228,-179,-822,1000,61,1000,1000,750,220,-582,-438,727,-156,-1000,504,-918,1000,-880,-453,-1000,-332,-699,-1000,1000,443,27,913,1000,206,27,999,929,-578,110,-627,747,-1000,995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{704,-202,-298,-324,745,-1000,764,668,748,603,-1000,785,1000,-157,384,1000,-994,1000,-250,348,-740,-923,-1000,-642,173,1000,531,-153,388,-898,300,-391,-320,268,258,771,-764,170,1000,787,-1000,1000,-1000,-385,217,-235,-1000,-487,285,1000,886,237,-42,150,-675,591,343,-831,1000,-353,1000,-226,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getMonths():int",
            new int[]{30,346,1000,-694,177,232,283,-232,582,1000,-670,-523,1000,-263,-344,478,-839,-351,-1000,-128,-169,-1000,-1000,-257,73,-625,-978,432,-298,-422,154,1000,126,-1000,1000,1000,-652,-109,1000,701,-1000,264,-1000,-963,-1000,36,-260,-1000,-851,1000,682,-505,34,914,273,-737,255,-175,82,-791,-133,107,-359,536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-1000,222,81,-158,-184,628,1000,1000,-40,-675,-228,-362,-375,583,-16,-118,-446,-609,159,-183,-397,-658,722,-495,664,978,1000,-101,1000,947,-1000,355,110,-676,-825,57,281,-201,-121,-154,-726,1000,-571,644,-400,106,-110,837,-362,-279,-951,-64,-397,1000,577,1000,276,-223,-538,-831,13,-1000,854,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Integer:NzA=", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-992,-420,127,626,280,-471,702,-98,-369,365,-837,62,99,-849,816,815,-987,-394,102,-115,541,-482,151,-692,489,806,-211,-490,-484,-812,-151,-539,704,100,-597,-852,-349,216,825,617,155,-237,494,-59,-89,-367,-621,584,660,-740,727,857,-261,210,689,419,-930,447,-215,-888,-696,-600,857,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-370,-485,-461,769,995,814,-34,392,253,2,-115,-961,896,756,-330,916,848,494,908,622,461,30,-383,-211,-455,-646,-217,-92,478,-899,-625,-272,-860,-74,825,554,717,-247,-785,-193,-743,-342,321,277,268,-983,-890,-348,627,-554,-752,94,-411,-909,-203,923,795,-168,349,783,-173,101,589,-934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-904,-697,-426,895,-714,264,588,583,204,572,-733,-713,707,-322,-53,875,-145,-807,370,-731,-448,596,722,-850,817,978,329,794,763,947,-723,970,110,-531,-39,812,338,184,909,578,-584,513,64,290,-524,742,-807,401,737,-807,-951,-175,-434,-412,513,-50,603,-274,44,580,-421,759,471,-878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{399,436,654,-289,303,802,655,896,-976,-667,28,-616,331,-681,77,37,890,-941,683,-949,241,117,143,-768,130,-665,750,-780,491,416,570,-721,68,-453,-393,-739,985,-718,-115,-671,384,-313,-167,-861,671,-365,-194,291,902,23,-427,104,-662,730,186,-6,-691,110,-268,-906,-790,-6,-791,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-204,126,-104,-246,582,33,-925,-328,-34,-786,-701,-826,-701,-105,-946,-916,-550,554,-340,-672,146,846,-208,-102,-169,112,462,-353,-543,391,-956,658,331,-454,668,908,-565,-165,615,951,-517,65,251,-564,-630,-673,-566,-597,-331,-754,370,522,-674,-377,-561,-301,-157,-531,634,-203,-145,-541,964,-370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-474,516,926,-542,787,300,1000,1000,-1000,-1000,174,-884,126,350,-212,25,164,-926,908,-525,118,22,173,-1000,217,-943,656,-1000,289,342,-625,-397,-1000,-845,44,364,1000,-845,-332,-1000,-385,-239,321,-414,1000,-871,-145,284,501,979,-352,1000,-757,1000,521,803,-1000,-168,-550,-1000,-83,-217,-210,-320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{103,-357,624,543,-527,-1000,0,628,-892,712,20,-751,420,-57,744,695,-236,161,558,-108,170,-903,267,-1000,0,-732,-4,-770,646,101,478,-363,0,-1000,-1000,93,-973,68,-151,-873,432,1000,-525,-254,-1000,756,-223,-785,-1000,-802,-1000,-1000,-1000,-66,-1000,274,704,-268,126,-651,0,-190,880,-937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-1000,-263,405,668,284,265,1000,744,41,285,-687,632,-249,-101,-103,482,309,-434,177,-35,738,173,-110,-106,861,1000,793,-222,267,-1000,297,-924,1000,-578,-390,-1000,389,-413,-597,220,-372,-711,9,-235,-18,-726,-1000,293,793,-3,1000,1000,-437,1000,1000,1000,-163,535,-21,-1000,-1000,501,-67,-299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-1000,-938,782,-356,922,-645,202,535,278,-731,-892,744,-1000,323,132,-542,-907,476,-597,165,404,-474,-1000,-463,584,-512,527,-1000,460,-1000,-591,475,-367,-178,-393,354,290,3,-454,-296,-933,961,-784,1000,-154,-1000,544,1000,-657,439,-8,104,-87,1000,-65,1000,-475,1000,-329,-1000,669,-1000,1000,-349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIz", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-1000,-786,481,1000,-1000,-161,1000,778,-874,469,174,-613,1000,-764,-247,1000,-183,-552,708,-1000,-408,-689,-42,-1000,207,-203,-589,1000,872,873,-16,1000,348,-617,-751,153,426,-23,1000,-465,143,826,127,948,-486,-370,0,846,1000,-555,-1000,407,-1000,-186,816,-846,1000,-1000,-544,813,-596,622,432,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-1000,-1000,-723,1,3,-1000,-799,532,-874,-1000,-367,603,-1000,-1000,1000,380,-112,-213,-1000,53,505,-1000,450,-315,813,1000,-1000,957,-421,591,-1000,339,818,289,1000,-32,-696,425,40,1000,480,178,558,938,1000,-552,-822,849,1000,123,1000,-119,-505,-1000,43,-4,-499,1000,-843,648,-194,-700,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-1000,-327,993,-1000,1000,265,902,796,203,-1000,-784,650,-1000,608,-537,21,-627,-285,-352,-127,367,-617,-1000,-463,736,-659,989,-1000,210,-470,-1000,451,-1000,-69,338,544,1000,-636,-357,-384,-1000,94,-192,888,1000,-1000,599,1000,394,1000,446,1000,83,1000,-697,1000,-1000,1000,-802,-17,1000,-1000,237,83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{499,-799,-104,686,-724,-750,702,-797,-497,1000,421,53,454,946,1,478,-287,721,-868,703,-18,-220,-1000,-143,-86,-995,949,-558,909,-1000,334,-690,68,-961,-1000,-1,-518,-53,-1000,-1000,-1000,-80,-874,19,-1000,712,99,-1000,-1000,-1000,-393,-328,-404,235,241,227,-100,1000,-904,-906,848,-320,360,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjM=", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getSeconds():int",
            new int[]{-819,-668,-150,1000,-1000,922,1000,1000,-513,1000,111,-1000,767,247,307,409,-679,571,938,-293,-1000,-1000,1000,-325,699,1000,809,1000,861,748,-1000,530,484,-1000,-1000,-715,-247,233,589,886,23,1000,-413,881,-1000,340,-670,251,-657,-1000,-1000,-1000,-868,498,1000,339,1000,-1000,-170,-1000,1000,-690,944,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{573,-724,135,202,-174,220,83,536,584,-968,-995,170,-399,223,-540,363,265,-973,-719,-315,-596,193,-80,200,-103,-100,-503,-634,91,106,-136,-446,996,-829,830,-294,390,-14,-217,217,-481,953,-596,417,908,194,-114,171,937,-893,-846,-926,328,29,930,459,-566,177,174,-977,-678,222,435,252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{1000,460,761,868,650,-262,426,161,1000,298,64,507,-674,-362,565,-72,-490,-523,99,703,-504,-509,-435,-945,-449,-476,258,167,1000,225,-316,504,-667,-133,540,675,329,-1000,-393,457,163,-892,965,-1000,389,-189,-733,55,-604,-180,1000,723,561,46,775,-903,-595,531,-290,-137,-548,113,-146,736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{-674,842,-770,-190,-7,977,400,265,10,15,730,520,-782,96,-390,-859,-583,-26,-146,-352,-157,955,-180,332,63,614,-806,297,979,259,-632,220,485,594,-810,997,556,-569,-57,-28,-920,-557,-136,756,568,927,268,776,-975,359,359,659,-245,99,233,-708,601,41,-791,-15,-739,218,-348,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{338,-771,-776,-859,713,605,935,67,134,-704,-992,47,224,97,208,314,283,506,-40,496,-936,-54,828,472,-211,-892,-868,-291,903,-313,-539,-146,938,-712,643,-904,59,-355,-128,-710,-564,477,112,-842,367,256,-377,-965,270,969,-451,565,106,571,-143,505,-422,204,-421,-623,521,-466,340,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{-666,563,445,881,-21,137,398,-210,119,44,-307,-975,309,-936,941,-214,-221,-361,945,927,349,907,-772,879,249,-202,686,433,246,577,526,814,-659,805,474,-800,553,-8,-655,-655,785,433,490,281,659,-343,-593,-783,302,726,831,-309,-420,234,1,805,7,251,-519,-73,673,-303,910,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{-67,685,351,-80,288,185,551,528,-45,-916,-681,402,913,-857,516,527,-88,-324,674,144,-153,-135,150,-540,214,-455,887,-925,933,-974,-638,-764,-283,-955,-890,722,-594,-682,302,-331,169,304,335,976,374,-216,-908,-946,997,-338,-391,647,-8,953,-740,195,742,406,229,-215,851,376,-946,-862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{903,56,543,-737,104,420,818,735,992,745,567,-148,-705,-617,282,755,-34,-155,-87,117,-515,30,-995,-485,-943,258,-78,-226,814,990,-455,-319,-987,-355,647,211,456,-710,-748,-130,853,126,716,-893,727,-299,-969,-108,-421,-459,890,464,495,510,423,-930,-823,632,217,29,-558,-686,-812,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{610,868,0,-1000,459,1000,462,572,610,-476,-351,221,174,-1000,1000,1000,0,-106,295,-438,-583,242,-985,-1000,-506,-823,1000,-1000,0,-128,-1000,-1000,-1000,-1000,0,1000,-805,-501,-79,-1000,1000,1000,1000,11,431,-770,0,-1000,151,-938,-54,828,276,1000,0,-745,417,305,38,-98,188,-619,0,327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{717,-93,-1000,-1000,1000,863,-172,136,40,-1000,-807,-960,159,-43,1000,536,136,839,477,-52,-568,995,1000,-440,193,-133,-569,-487,766,188,-984,39,236,-1000,453,-401,949,51,-1000,-979,-140,470,-948,-874,220,-214,-862,196,1000,-724,44,268,63,-57,-278,-179,98,156,554,-1000,1000,-246,833,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{-1000,-686,-479,793,-1000,762,102,-12,-880,991,738,-1000,-164,-211,-227,314,573,566,-39,496,556,1000,-1000,1000,-211,1000,-587,486,-1000,1000,811,1000,-297,1000,831,-1000,790,1000,-856,-1000,1000,477,-554,180,372,-117,299,-965,-45,544,-118,-1000,-595,-59,-166,787,-488,-236,108,-400,101,-1000,695,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{268,654,513,-743,11,1000,361,98,-398,-891,-1000,150,661,-1000,1000,-963,953,-225,-506,708,186,-830,-1000,-13,-989,-607,562,-818,1000,69,-446,-1000,-716,-461,-261,-596,-767,-329,1000,-733,427,347,880,-785,-610,967,-1000,-211,979,-1000,-45,491,-435,1000,-1000,-504,1000,-598,164,215,948,226,-736,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{-571,269,-30,1000,10,-1000,928,828,-1000,-951,-701,521,1000,-830,-408,-499,-416,-734,1000,1000,249,-727,-1000,75,732,448,204,-232,873,-1000,215,-86,818,-179,-1000,35,-40,-1000,227,904,833,-872,400,1000,708,564,-1000,-310,1000,431,-260,528,-143,794,-1000,1000,247,104,657,-347,1000,1000,-867,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{-1000,-470,-1000,-478,-474,-729,400,265,-1000,-34,-863,-68,1000,96,-1000,245,23,-235,963,590,1000,-222,-180,1000,562,614,-211,-193,-1000,-672,-632,0,1000,266,-925,-1000,556,212,-435,610,187,480,-1000,1000,216,-147,-517,-206,1000,416,-865,477,-676,193,-1000,594,451,-106,1000,-891,1000,553,-485,-56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{-1000,-366,-724,721,-210,-918,-429,-238,-89,1000,635,388,-939,1000,-1000,-1000,-820,-405,-8,1000,602,-71,-102,1000,193,305,-1000,1000,-353,708,329,1000,1000,1000,-52,-532,1000,-374,331,1000,-988,-877,-765,-462,374,1000,-272,1000,-998,1000,668,485,-271,-1000,756,-598,-669,-11,-1000,-552,-896,823,-66,297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getWeeks():int",
            new int[]{-121,-637,-672,630,208,134,-271,791,-676,-1000,629,1000,111,668,1000,599,-20,1000,442,426,-987,-6,1000,-1000,-357,-1000,1000,-1000,1000,870,-1000,-1000,-1000,-150,-78,543,825,-871,-676,-231,-1000,267,575,-185,1000,506,-514,805,-132,226,-62,-104,36,765,624,67,-238,80,-1000,1000,-137,433,249,-644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{-1000,552,1000,-383,113,918,-529,-179,-524,47,-1000,-966,1000,1000,1000,-450,1000,704,-984,-356,358,-876,-204,1000,257,-330,426,-804,-1000,116,257,-1000,-1000,-169,1000,-442,654,377,-670,-613,530,-1000,-680,113,-82,-349,91,1000,-1000,679,-370,-255,-506,-87,-1000,994,1000,1000,73,-350,476,1000,627,256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{-836,658,668,-518,-905,882,266,3,-366,-705,-640,-488,546,393,285,-450,887,599,963,-769,812,-311,-611,705,650,738,-343,-765,-952,-647,-632,-570,-210,-672,783,421,560,496,-695,-683,341,-485,-931,155,-581,-408,250,742,-984,760,473,266,485,205,-741,377,673,344,-256,-449,573,915,138,976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{734,142,315,-47,981,-793,753,-143,11,-284,-378,69,117,-364,-744,981,-66,-758,-554,157,-563,-960,72,50,637,331,-612,-701,434,101,-989,535,900,-722,74,-96,900,-969,-654,-949,-586,-239,-805,-818,-822,-311,-793,994,-666,566,-22,-211,-346,902,-840,-155,627,333,192,281,-818,286,-707,64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{-849,-102,299,548,709,361,-304,-7,-16,-751,-169,-98,782,-764,652,-342,939,642,-379,-30,207,669,-582,923,779,-79,-33,-937,-447,775,930,-136,-922,571,-659,-821,-257,-815,293,-178,-777,-453,747,-683,462,933,-598,11,529,424,-677,-751,-406,-124,552,-874,-65,-102,-712,428,-569,914,378,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{280,183,848,-815,-132,822,-8,614,601,341,142,-108,-682,-39,-350,-239,465,84,-149,243,569,-318,817,-892,791,88,553,-679,-689,686,973,-750,138,-645,-557,-384,-434,148,-988,763,-50,669,-499,665,732,-116,-410,296,717,-326,729,457,-512,361,-733,972,-456,-370,-463,-703,264,-180,-203,990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{495,641,-822,-815,24,-638,838,-530,-816,-766,307,394,-796,-934,6,-741,-51,298,93,249,-275,678,477,-988,253,-397,-399,467,536,86,264,58,124,-374,219,873,949,-91,-410,643,-663,-764,228,-517,63,-156,77,-582,389,884,315,249,-88,707,606,-902,-253,-668,124,-891,64,-512,-797,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{355,471,433,-468,-103,-812,797,-679,865,-749,86,-799,-717,-270,-793,750,-421,279,-751,7,904,750,208,-990,-437,-890,-879,23,-82,-475,-969,72,-63,775,682,610,-777,-136,134,-246,778,596,-414,326,-980,223,-381,189,-341,803,803,-196,-82,-156,-873,-223,256,-985,-890,262,-692,209,569,538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{32,-884,29,801,-780,-409,-451,-718,459,-442,318,170,402,-998,-173,-502,-241,641,-385,-857,-159,314,-336,-603,638,803,-667,195,-540,-150,-576,3,-8,460,-579,-378,201,-767,260,-280,63,436,69,-226,244,-216,249,276,-980,521,-16,-430,565,-751,825,-528,-569,201,-681,-41,-418,160,859,643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{-370,82,-133,-518,-905,-401,246,-757,-580,-921,-377,-394,260,674,-667,-532,122,871,-697,1,822,481,-938,345,-578,-308,936,404,-678,-148,-840,-826,0,-19,-876,255,525,665,243,767,-568,932,72,-240,778,-980,171,-624,-687,-512,-14,539,528,-935,-481,730,105,-184,950,538,-263,748,-494,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{358,-1000,-531,801,-781,-1000,-465,-1000,310,-284,503,236,202,-801,-840,-560,-776,832,-1000,-319,-563,869,-566,-856,-222,331,228,1000,-348,200,-722,535,139,918,-1000,-494,900,-649,917,735,-574,1000,771,-503,1000,-616,194,-681,-773,-369,-357,-239,595,-1000,1000,-280,-966,333,163,651,-1000,286,417,37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{-459,-633,-7,1000,-609,751,-23,-794,1000,-376,81,-839,122,-674,-535,501,-409,46,-880,-1000,421,-923,-646,574,444,-135,-760,-707,-1000,12,-439,633,-229,836,733,-1000,-1000,-318,707,-319,63,910,-5,638,-471,215,-288,617,-132,650,335,-1000,428,-1000,500,67,-543,-85,-825,523,158,1000,957,617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{-1000,-493,364,1000,1000,283,-78,-738,422,-561,-485,-742,1000,-1000,275,222,896,-264,-834,705,-113,-274,-1000,1000,648,1000,239,-946,-1000,806,893,-162,-510,445,-1000,-976,-1000,-1000,-762,-949,-585,-257,676,-1000,-766,1000,-1000,1000,83,714,-1000,-1000,-326,899,-67,259,-109,1000,-306,1000,-620,1000,367,-107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{701,155,-546,-1000,237,-856,-376,-800,-380,-890,761,445,-159,-1000,-828,-593,570,850,-1000,1000,-351,1000,-1000,-1000,823,887,481,1000,1000,-58,1000,44,61,-96,-1000,233,1000,-1000,-101,92,-1000,396,976,-1000,612,104,267,-1000,38,-1000,205,847,1000,906,821,-1000,432,28,83,-271,-1000,-1000,223,-623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "getYears():int",
            new int[]{-600,70,-600,322,-379,-254,803,-1000,-72,-1000,-588,-843,260,-1000,493,-530,122,323,-697,-225,671,481,-1000,345,1000,696,-615,-749,-678,867,1000,-413,-657,1000,-1000,626,-1000,-450,-755,-587,-265,-480,662,-883,-1000,-980,-1000,308,1000,204,144,175,46,612,1000,-1000,-582,-184,-1000,538,-263,824,730,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "hours(int):org.joda.time.Period",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "millis(int):org.joda.time.Period",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{631,640,-378,-157,68,-380,920,922,-403,920,-270,-408,-995,343,831,837,919,-53,-612,-848,-188,584,615,858,365,830,-459,-524,-11,432,-889,753,-584,644,-313,-22,257,192,594,-407,-859,464,781,-211,641,542,893,777,-403,149,-904,714,-402,257,-167,-94,445,-850,390,-186,-810,56,587,669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-694,460,-550,-271,-20,-592,-145,-332,853,-640,354,958,-94,68,-536,-44,-975,982,-713,-232,-23,266,649,109,840,-174,-610,587,-879,-540,488,-66,914,-614,-476,-250,421,863,557,724,896,-583,-67,-60,671,248,748,-97,272,799,748,-792,870,-794,-710,-604,286,-681,666,486,622,-606,-448,893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-806,141,-341,16,-77,808,746,-342,-427,-733,74,-415,308,-383,131,-936,929,764,958,13,-161,-387,807,498,-693,-941,978,-221,906,-207,-286,-185,153,79,-228,-783,319,-863,932,5,-868,-463,98,-811,-327,94,-116,225,154,-854,-384,-351,452,843,540,-125,304,48,-330,14,855,845,232,866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-306,604,-358,623,-951,855,-495,205,498,-526,966,-488,-587,-5,-735,97,284,-989,116,-106,-131,-129,-376,-880,-836,522,520,-945,-965,247,44,-145,-475,177,-414,-412,-261,909,-210,72,39,-616,-730,-237,788,206,273,-65,310,23,718,-278,917,-698,832,169,-136,-167,339,-580,-102,656,563,375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-254,-247,-835,724,281,-213,-710,-857,-593,-509,-164,582,-250,-154,-714,131,141,119,77,679,567,-949,-159,778,782,973,11,-506,-919,-487,-722,807,-106,667,-847,-832,778,498,-479,188,-824,486,-546,107,850,-348,821,726,-687,962,-771,718,756,252,726,-64,-5,921,-673,205,-238,992,-79,362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{337,782,-594,844,917,-625,9,21,420,409,637,624,-535,214,870,118,-821,294,-278,-296,149,-679,-338,706,377,-635,-452,575,399,887,101,-543,873,-790,353,345,874,-385,-909,-803,-75,-1000,-266,75,-676,-865,330,385,748,-90,365,558,279,749,976,-820,54,313,769,473,-500,-972,-14,285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{685,-356,-334,-581,-421,-178,398,46,-989,-839,106,73,970,-190,-220,169,-812,823,-186,-880,722,-229,-56,-988,697,661,687,773,-273,505,229,-192,595,-936,-511,-418,-226,722,-290,319,105,258,-682,725,-480,-671,57,580,-906,-88,391,-615,-100,663,-909,877,151,136,-487,-890,-60,-784,-373,-892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-966,-857,231,16,-241,-409,-940,412,673,772,824,-328,96,-867,880,-461,309,-720,-794,481,-429,-15,124,-573,-649,343,146,-319,-801,170,366,7,-145,-135,-388,933,-874,418,-853,678,803,49,-844,810,511,8,-523,719,-297,516,52,301,274,492,466,-132,-771,-228,569,-757,-312,-153,-312,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{633,-493,-6,586,722,-526,-953,953,-507,-775,298,-18,547,-616,-321,-466,-928,-734,-13,-116,508,-224,-945,42,-391,-458,-363,-16,-363,172,-256,585,-394,-340,-61,133,-594,468,719,627,404,544,523,789,717,-356,-884,-326,-308,-259,181,-895,-628,978,751,748,-305,-310,-171,586,-972,322,-11,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-1000,698,-91,56,276,367,-30,-1000,0,0,147,131,-921,531,-1000,-1000,-208,0,0,600,310,-170,-1000,-309,-1000,-427,799,533,0,360,211,-428,-80,1000,149,-1000,0,977,0,-995,0,-1000,472,1000,1000,-423,450,-485,-583,-253,1000,-991,696,191,1000,357,267,0,1000,-256,-104,828,-35,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{375,-16,529,-179,-1000,1000,575,246,-693,-1000,-133,-529,-363,-864,-449,949,88,-1000,-591,104,-393,1000,575,-453,-121,1000,-351,-369,-241,-1000,1000,-196,-1000,-789,-575,-531,-703,-333,507,836,605,1000,464,-655,-339,1000,1000,110,-927,1000,-1000,-364,-834,-1000,-1000,789,-779,-328,-1000,-326,-884,1000,155,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-1000,-986,-219,-461,392,199,369,-630,369,306,901,-208,707,-357,1000,-1000,1000,568,514,556,164,-752,429,282,-1000,-1000,1000,-130,353,326,-698,-323,672,652,-130,-97,-412,-616,224,-49,-314,-364,-244,65,967,-445,-1000,124,-183,-839,259,-235,868,1000,1000,-449,456,-81,533,-302,1000,445,-297,862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{1000,-763,-685,-838,-885,-263,72,-301,-1000,-81,-163,-918,1000,-119,980,1000,39,953,-500,138,1000,-745,159,-1000,1000,534,1000,-635,-150,1000,-481,840,534,394,-29,-736,-134,629,-614,350,-248,618,-1000,486,-1000,450,-305,-272,-1000,-1000,-238,-254,-673,1000,-1000,1000,1000,676,-1000,93,-441,-1000,-44,537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-238,527,-359,-1000,-924,245,1000,-801,-924,525,463,-1000,35,865,1000,538,912,1000,-59,-349,8,447,1000,-1000,773,1000,1000,-102,571,276,-483,-505,506,331,-112,-1000,946,-102,112,-627,-1000,790,-472,-258,94,199,1000,1000,-828,-540,-395,-890,207,-579,-835,-181,1000,-538,501,-1000,307,-938,126,601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-1000,-809,280,1000,831,1000,-339,770,-42,-550,1000,-503,795,-949,387,-1000,1000,168,366,-443,-243,-500,774,994,-1000,-1000,840,-282,1000,-4,-1000,-154,415,-598,523,1000,-928,-1000,1000,720,-831,-69,291,-1000,1000,-1000,-1000,296,-478,-887,309,-549,79,1000,1000,-528,-440,-216,248,-1000,1000,1000,-387,-351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{1000,-580,280,1000,1000,347,67,-1000,826,160,141,932,-1000,-949,-1000,-1000,-524,-465,1000,1000,-972,-1000,-9,1000,-1000,-139,-365,-1000,-1000,659,195,1000,-1000,-598,523,-498,-217,1000,1000,-593,57,-789,1000,877,1000,-1000,874,-244,-903,1000,-19,-817,1000,1000,1000,-161,-687,582,-799,-1000,-110,1000,-218,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{556,24,-31,-358,1000,558,-649,-1000,-283,153,1000,380,-701,-163,-277,206,709,-772,-1000,-521,-1000,-960,359,960,-275,900,157,1000,34,-376,6,156,-693,198,764,440,-358,-1000,492,-543,776,154,-326,1000,643,-898,-222,183,873,-1000,-652,1000,773,422,914,1000,-1000,1000,-445,724,-665,-543,-58,-6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{400,86,1000,1000,-420,936,1000,-623,-149,-887,-1000,18,894,-86,-902,264,300,205,428,-745,969,-1000,609,-1000,1000,-1000,-809,445,-200,-2,796,1000,259,687,889,68,94,-27,-1000,-806,-1000,-275,907,-935,-200,773,1000,1000,-915,-464,-1000,1000,-77,-78,-1000,-216,1000,-1000,-711,-1000,-692,645,-65,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{203,-146,176,-101,-889,692,-82,-890,-855,169,413,910,536,-957,-57,606,-735,454,803,-906,-572,-248,825,489,-709,-507,-933,14,-977,146,911,874,80,564,-968,966,-746,-533,75,887,525,-187,243,-31,-988,227,450,819,-426,-548,-754,-790,-107,-832,-748,-935,247,-533,528,-248,-416,-228,-25,936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{165,-302,143,367,229,777,-685,52,-739,-988,629,264,-875,803,693,722,169,659,176,-916,-259,702,660,-924,-867,25,-589,-153,-527,-528,857,-674,-882,-991,839,485,124,-53,983,55,-518,988,4,465,-7,-688,134,-444,-114,-265,442,983,-423,346,366,-912,334,-25,420,-447,-964,-661,752,-878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{-371,-189,-718,762,-975,-660,-908,157,409,-975,-393,-192,59,88,-310,-961,40,-504,-319,-606,958,-294,633,844,-169,937,-780,161,218,601,677,319,-319,122,-631,-121,732,470,-53,-31,-476,444,-589,-306,-294,-27,-115,587,-874,553,-605,-925,322,258,-445,819,147,-237,677,277,-726,287,552,860}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{813,413,895,-349,821,419,-708,-173,120,-107,380,-392,470,-821,-77,898,920,-473,-489,-435,-60,667,-375,771,-209,-4,-977,692,-462,551,549,529,69,-489,-229,-215,562,-905,-834,-530,919,-576,970,73,893,150,268,-24,-158,477,402,-712,-624,-419,-614,-4,480,-627,612,-175,-842,-959,-109,198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{122,-116,-406,295,578,-426,-483,-613,-212,-830,975,306,777,-41,-418,230,255,517,-793,783,-361,-738,654,-124,-54,192,-225,660,199,-460,925,540,-974,596,926,-758,155,-878,50,-384,610,424,-76,85,-377,-555,-300,-610,464,-977,-643,692,748,856,608,928,-927,639,-528,856,-672,-644,340,-796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{463,758,-923,967,-940,707,569,-958,671,-777,0,106,-437,862,-903,-58,-779,-667,615,244,-204,158,391,355,-486,620,455,-637,-992,-778,890,488,502,-512,394,834,-147,-981,-171,-805,528,423,-563,848,809,17,860,-959,722,956,-395,241,-487,-116,204,91,-174,88,923,-472,-819,-319,-388,-487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{-500,-45,-955,-82,-272,98,-342,408,505,-3,-294,383,495,887,29,219,-591,554,429,-970,790,723,781,666,-445,328,575,639,177,642,-940,-821,-548,-223,-592,-338,781,-793,-348,353,-948,-339,552,601,357,-32,-15,420,-980,276,982,722,-275,406,114,398,870,-908,565,-106,713,303,-56,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{-383,-118,-171,-282,523,-327,-82,337,-236,185,-562,252,536,836,-57,1000,-945,1000,596,667,-41,-248,-560,-846,-1000,442,1000,1000,36,231,-945,-198,-174,-406,-350,-481,996,-1000,-952,-632,525,-1000,243,1000,755,-941,672,110,-396,450,834,-790,247,-682,381,-1000,1000,-961,35,610,951,-898,-324,-761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{7,-7,-1000,-781,-43,-260,-38,60,136,-816,-136,-885,132,-154,646,524,54,457,-1000,542,-684,-560,923,-387,403,1000,83,680,-38,198,974,128,-1000,864,1000,-1000,-515,-1000,1000,33,551,870,-83,375,-1000,-1000,654,-524,-1000,-1000,-868,53,79,1000,-105,1000,-1000,5,-149,1000,-560,-863,596,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{-89,-537,1000,-401,685,163,-1000,381,-361,131,1000,416,1000,-1000,-425,-436,305,-772,-1000,-578,787,61,301,-508,-535,-759,-825,96,459,838,-286,794,-102,667,-16,-1000,825,981,-46,1000,273,-394,-326,-1000,-1000,-262,-1000,550,873,-1000,204,1000,1000,935,642,748,111,-353,-1000,344,964,-680,1000,-980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{142,-48,1000,-903,1000,-308,-1000,-555,276,301,1000,406,1000,-1000,-357,-699,1000,-1000,-1000,-1000,116,-231,80,784,865,-698,790,96,931,870,1000,763,-354,666,-471,-756,369,981,-80,742,14,55,203,-1000,-828,-303,-1000,999,1000,177,254,1000,526,1000,559,1000,-1000,581,-667,617,-402,-749,1000,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{1000,-285,249,682,-1000,423,-277,-169,1000,239,661,774,-1000,-355,-685,-323,830,-1000,-529,-1000,638,-166,511,654,-1000,481,-490,-325,-1000,1000,1000,1000,289,668,1000,1000,49,835,-1000,-1000,-521,164,-522,-1000,789,1000,-232,-1000,695,676,677,-1000,-474,-195,-946,-1000,-384,-1000,155,-1000,-716,-976,-1000,-984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusDays(int):org.joda.time.Period",
            new int[]{691,-384,354,-853,400,1000,534,-966,-497,-343,770,517,105,-1000,-522,-1000,1000,-400,-1000,780,-607,-443,1000,-279,-101,-726,-1000,-17,894,-809,1000,1000,627,865,235,692,-1000,638,519,-1000,555,1000,-500,-1000,-885,953,-689,536,852,-394,-1000,853,1000,478,1000,-400,-1000,1000,-1000,-850,-862,977,734,-589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{322,-988,-68,-226,-16,1000,1000,0,1000,713,705,-805,0,-325,-602,-1000,1000,81,851,0,-572,256,-1000,-83,0,178,-489,-1000,361,0,1000,1000,1000,-1000,1000,0,-449,-1000,-74,-1000,-1000,269,-787,0,-807,256,-430,917,-878,-948,679,0,-1000,-54,-1000,-137,-1000,574,317,253,-1000,-227,690,115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{-160,20,-183,-196,400,757,-469,262,-856,-42,400,312,-78,-1000,820,-274,-463,-688,-647,568,299,1000,203,-1000,-26,1000,-314,460,-667,-231,-261,-435,-1000,577,-1000,-838,-729,658,292,-444,-104,-312,374,959,127,213,495,-963,-1000,-14,-219,128,-166,-137,419,136,-1000,-166,-818,603,-219,477,-639,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{393,-490,960,234,755,508,64,749,-176,-303,888,515,-562,341,-823,593,-981,-498,598,426,-712,154,-37,730,-799,60,925,789,79,578,161,20,-652,982,-460,407,-623,-609,853,-875,470,-75,297,-448,-773,-667,105,-277,234,-999,461,263,292,4,147,833,262,-366,-737,-785,10,-271,-216,296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{-614,-573,200,590,-742,641,-886,213,-799,-687,-433,780,264,-886,988,-505,129,-707,-707,988,-138,-993,-409,496,-534,292,911,425,-647,280,591,63,318,628,491,-65,658,679,-730,63,-366,356,567,-812,-328,-528,142,-39,-604,-261,-238,-990,-128,573,-58,839,-703,-496,-453,596,763,957,-352,865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{520,-300,-935,-268,538,46,-350,504,-547,484,734,379,-423,413,132,904,426,54,-760,621,-980,-826,841,16,312,-894,-986,553,938,-763,-108,-899,918,492,484,826,13,891,846,-651,23,-239,534,830,-552,-187,-572,497,697,-28,-210,133,272,584,736,-979,-773,397,159,933,597,-378,607,-88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{467,-741,586,-228,-111,-113,-633,526,-65,-855,-539,-717,-152,269,638,-466,476,200,627,-782,-64,-984,407,469,-181,766,435,519,155,-896,45,-113,-175,-420,-217,687,117,-80,-510,638,779,162,-401,-785,-4,151,-740,-738,523,547,700,327,-576,-748,515,-975,753,-39,-525,797,902,-623,859,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{-357,722,911,75,-722,-727,-722,-417,-763,830,-597,246,344,-617,-898,-685,-295,-712,-477,558,797,959,790,-41,354,-821,-1,863,-664,316,390,-972,779,106,-934,-233,-666,194,660,-889,-106,-851,-4,777,-113,290,-19,589,-489,329,-305,-26,491,-430,-1,182,766,226,-187,926,-71,227,-307,-686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{-1000,-699,-927,-1000,326,1000,32,967,-874,-1000,540,1000,207,-1000,1000,152,962,-889,-287,1000,280,-1000,76,604,-806,935,828,169,-1000,-592,-740,-316,-1000,-408,1000,-516,-670,190,-234,536,128,-1000,966,-123,-1000,-362,225,-550,-898,-346,-913,-992,-135,562,832,1000,-529,-153,75,517,-569,1000,-685,321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{-1000,1000,-656,-259,-722,146,1000,336,408,-632,177,948,64,-966,87,1000,-659,-1000,-10,775,1000,739,790,-573,-284,-333,226,1,-655,836,-1000,-491,-1000,137,560,-886,-1000,194,660,-369,-303,-879,827,1000,273,378,1000,-117,250,-111,-359,-619,482,-430,-18,686,-304,1000,-73,-704,-71,1000,-826,-984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{-39,-104,97,147,183,1000,243,1000,-861,-373,-864,1000,955,177,1000,-492,869,-423,-1000,1000,-1000,-1000,432,1000,-271,-121,817,903,892,-917,-3,396,142,734,-51,259,445,1000,226,515,824,-390,-167,332,-806,-1000,-1000,-632,-1000,1000,-744,-1000,-1000,-50,1000,501,-399,-1000,294,1000,1000,173,-257,662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{-1000,-335,-489,-301,-1000,121,-740,581,-547,-583,-1000,944,366,696,529,-145,159,95,21,800,-630,-1000,1000,743,-335,542,743,249,161,183,177,317,979,630,28,903,144,305,-769,907,990,-87,221,-220,-768,-390,-418,29,-821,471,-56,-937,-230,1000,-684,-253,572,-327,-654,-848,518,907,-721,220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{-270,782,507,462,287,-710,-1000,-85,-663,-47,-681,18,-932,77,816,161,-932,-948,681,-280,423,966,-353,187,213,57,-1000,-1000,-209,-1000,506,-1000,26,1000,-1000,-706,1000,1000,537,-35,-90,1000,1000,46,1000,174,426,444,1000,-827,1000,752,734,256,535,-1000,-753,-1000,-907,-498,1000,643,1000,773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{-1000,301,-835,-24,1000,50,1000,-1000,303,-632,1000,506,-1000,-333,-1000,1000,-59,-1000,353,1000,1000,1000,-928,-1000,587,-525,-955,1,681,-130,473,-222,-940,-700,-357,-230,-1000,-791,-264,-1000,-1000,-1000,-84,1000,1000,868,-17,220,-877,-685,-416,-533,481,-1000,-197,-450,-1000,1000,1000,-182,-1000,-370,-337,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{566,207,1000,1000,-201,-893,260,795,1000,-1000,-314,1000,353,-1000,1000,-420,-1000,-55,-914,-1000,-169,438,1000,856,-1000,1000,-470,167,-1000,1000,-685,-1000,801,1000,-614,-770,911,-254,996,-438,1000,1000,1000,1000,486,532,1000,-996,1000,481,784,1000,816,-628,783,-374,1000,-1000,-1000,825,1000,1000,-213,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusHours(int):org.joda.time.Period",
            new int[]{584,-492,-686,908,-1000,83,503,457,733,-650,1000,67,1000,-889,1000,-1000,-361,-160,174,-74,-1000,395,177,-1000,-786,1000,551,-47,-836,595,-283,-322,-886,-636,-1000,-657,-1000,-538,1000,-539,1000,1000,203,774,167,356,337,-1000,1000,593,823,735,40,-734,191,-533,-1000,1000,-934,1000,350,1000,-836,890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{-698,-460,969,4,285,-446,-782,-475,352,200,-764,756,327,490,-74,-103,149,-289,237,-569,-826,-885,641,900,325,66,315,-657,-800,268,-948,-313,-25,-832,777,340,-509,452,-743,392,-730,-338,92,401,349,406,425,556,-170,-642,887,150,808,314,-685,799,963,943,472,565,-500,542,619,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{-310,174,946,591,1000,692,-207,574,424,1000,908,600,-809,-252,-765,-1000,1000,1000,-809,-186,-1000,-13,341,523,856,-898,-895,-955,-1000,34,1000,-1000,-83,-1000,-168,-1000,-1000,1000,562,-1000,29,310,-641,908,1000,1000,-627,-1000,1000,404,-224,-785,-1000,1000,1000,1000,-1000,1000,399,-1000,1000,884,1000,-878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{-16,-83,563,-590,683,-515,-290,-58,409,896,391,-252,319,275,-239,-560,985,-941,-784,-462,-332,-494,40,893,-317,-917,45,-902,-214,-69,564,-706,-699,127,22,-300,-817,-778,-980,375,634,-295,355,270,985,437,-242,475,-405,-144,-771,676,281,-188,814,-658,-618,-730,439,-79,19,765,954,-602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{67,-1000,591,480,-141,-842,-1000,-1000,602,189,-1000,1000,1000,194,-398,798,206,-1000,-89,-481,-572,-509,1000,428,337,363,715,-445,-1000,-628,-1000,-1000,848,-1000,1000,397,-566,750,-792,787,-1000,-309,41,611,-473,1000,774,88,-626,-966,416,14,493,-775,242,574,1000,488,1000,1000,-555,-269,927,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{529,-211,626,319,-459,-400,-529,-1000,995,162,-159,938,1000,-520,-753,166,766,-1000,-438,-75,-515,33,443,543,-981,-621,441,-1000,-656,-1000,-356,-1000,46,-282,-504,100,-1000,325,-1000,603,-654,206,-743,1000,346,1000,886,-688,-204,21,-1000,1000,338,-1000,825,-1000,-31,-1000,275,405,-82,368,800,418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{519,7,-1000,727,436,-379,1000,-157,327,-1000,172,1000,1000,-1000,-545,181,-7,-1000,-1000,772,422,-538,1000,934,-150,473,913,-714,513,-903,153,337,1000,490,-1000,1000,-183,-229,-211,3,816,1000,-1000,-956,110,-892,650,-344,-789,1000,-1000,13,1000,28,-668,-1000,-515,-523,683,963,-148,-133,350,198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{484,-185,634,-682,1000,1000,915,-207,-875,-808,-355,343,835,-469,-626,-947,660,-1000,-544,-302,290,-463,1000,782,400,-264,422,366,800,366,19,-85,980,558,182,752,-131,-425,-675,-242,-816,774,446,-820,962,-443,-13,104,477,988,-320,536,567,572,-787,-1000,13,-1000,66,-300,358,-401,171,-835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{-1000,1000,155,610,-1000,-613,-1000,1000,1000,328,-997,28,892,929,-797,-857,652,-1000,1000,507,-579,-1000,930,1000,1000,-786,1000,-1000,-161,-309,-798,52,441,-511,-85,93,-463,231,-591,-101,-635,381,883,562,-528,1000,-930,-790,218,-556,1000,471,863,177,-1000,1000,687,1000,138,119,214,-578,180,104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{-698,793,972,4,106,735,1000,-475,352,-784,-458,-692,1000,-304,-1000,-1000,77,-778,-310,-37,-548,-885,126,1000,703,-450,-506,-1000,909,370,392,248,1000,931,-1000,629,50,-648,-1000,-734,-1000,-338,211,-874,1000,-713,-520,-610,919,1000,-84,1000,1000,1000,-685,799,-304,-108,-368,-644,156,-604,-186,-191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{973,-514,-837,810,473,366,928,-605,587,728,1000,606,1000,-565,-1000,-864,1000,-1000,-1000,-649,-1000,-1000,1000,-275,-1000,-1000,-231,1000,-1000,-1000,-293,-1000,386,-703,-1000,-209,-1000,-1000,-1000,-12,776,-416,-1000,-481,-415,1000,-418,-513,336,-401,340,613,-101,-1000,1000,-1000,-1000,-1000,76,342,625,322,1000,307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{1000,-884,373,-498,-1000,541,-1000,-1000,228,906,-512,730,1000,235,-923,-868,1000,-1000,463,-1000,-1000,33,1000,-1000,-368,-144,950,90,-1000,-154,-1000,-1000,894,-1000,1000,-797,-634,786,-353,355,-654,-1000,-884,1000,399,1000,1000,1000,571,-1000,671,1000,-196,-641,1000,-1000,587,-292,543,166,681,489,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{383,578,1000,-767,1000,984,-1000,365,-875,-201,-691,-280,90,-95,-764,-755,-20,-74,926,-634,126,1000,-772,782,941,-815,-160,-701,872,203,31,-635,-424,-415,527,-297,-616,192,-562,297,-1000,665,1000,-820,1000,165,-1000,-551,111,372,-512,1000,950,2,-749,708,771,904,732,-416,-531,-1000,466,-24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{-393,96,1000,-1000,-277,234,-1000,89,-114,239,-433,50,-1000,782,744,979,-1000,1000,1000,138,1000,1000,-1000,1000,793,-51,625,-966,1000,975,204,425,-562,319,-969,121,-41,773,494,-239,-305,439,1000,1000,1000,-1000,-204,582,-1000,-246,-1000,885,434,702,-607,-339,1000,1000,806,-57,-650,-408,-281,-707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{507,588,1000,-1000,1000,1000,-142,534,-1000,-224,709,-665,-804,-548,-235,-463,-202,860,-205,-818,1000,1000,40,273,357,-1000,-1000,-360,931,363,564,-153,-1000,321,79,-451,-632,-508,-201,-115,400,775,294,270,1000,-845,-1000,257,89,1000,-1000,345,194,-86,-779,924,71,619,113,-1000,-245,-423,-12,-602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{496,765,-400,-633,135,1000,-1000,511,-875,-543,-538,12,543,-103,-933,-1000,301,-540,653,-759,-484,813,-772,1000,1000,-1000,-160,-1000,1000,-308,-798,-1000,-305,-1000,156,-448,-105,-292,-818,151,-580,749,1000,-719,1000,-104,-643,-1000,-17,35,104,1000,1000,22,-771,708,1000,609,1000,-328,-34,-1000,1000,-307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMillis(int):org.joda.time.Period",
            new int[]{-1000,-485,458,-1000,-389,1000,-1000,-1000,-490,202,-1000,-973,1000,715,-1000,629,-400,1000,1000,-574,1000,1000,-670,-213,1000,-456,-217,165,600,1000,-667,542,-1000,844,100,-594,342,1000,1000,973,-952,1000,1000,-133,1000,-1000,-147,-30,-370,-409,-847,1000,-779,1000,-1000,1000,1000,1000,1000,-704,-1000,190,-57,-291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{-72,948,-500,11,-968,-731,4,840,697,-105,946,193,274,227,-681,954,238,-33,592,872,512,-107,748,-393,-530,616,148,-661,-154,-809,204,952,-803,289,-701,-36,522,583,-623,592,-837,-40,-808,587,-746,-757,336,-196,579,-785,-131,-105,977,-108,589,924,187,260,836,-741,364,953,77,-349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{729,-904,546,-1000,488,0,-368,106,104,-138,-1000,-1000,294,226,-62,-857,-113,164,-781,-931,-800,96,-215,524,-273,136,-1000,-44,-1000,1000,383,-1000,515,593,-1000,4,1000,0,517,-1000,374,-337,999,-1000,726,761,-707,102,0,0,1000,0,-1000,662,503,-945,-839,-1000,0,392,715,-1000,434,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{-725,531,942,-594,-400,-607,48,166,-730,-224,-190,-28,82,50,274,129,455,-434,101,640,681,805,-458,225,-208,-870,-974,-615,18,-246,130,281,218,398,-909,-630,-355,15,40,897,-58,970,-293,-345,341,89,-540,-266,717,-870,238,366,-633,-628,-803,452,-854,280,-317,-154,972,485,539,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{32,712,-510,-96,-230,-892,-682,-97,187,927,648,468,298,-332,-114,-971,-7,-40,347,-98,206,-727,144,471,471,-177,-741,39,-97,864,-409,700,-957,378,-774,-304,-12,-883,-832,963,777,952,-664,-188,-950,-506,753,-954,-283,113,568,-835,-455,598,-247,-172,380,-996,199,192,61,-546,244,377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{266,-589,168,715,158,-406,-619,775,125,413,424,-650,449,-156,-971,-439,-618,349,492,39,712,-478,327,916,886,-838,-269,-690,-134,-626,302,542,354,128,918,474,925,-376,-339,-426,210,825,-740,557,-689,543,-373,808,324,-419,-220,152,46,-375,-330,37,-263,785,265,-332,147,-992,-393,232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{85,-505,708,-411,-819,334,347,-1000,-365,-370,679,-712,4,129,266,58,-772,846,-428,908,123,-347,170,898,-675,443,-671,-807,-28,-695,23,481,828,987,-140,887,-480,851,-588,336,76,495,-992,-443,89,-694,-827,275,432,-724,-425,-490,733,601,619,-616,542,843,609,310,-487,687,-190,73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{-181,741,648,-95,466,199,818,-595,244,-292,-888,870,-383,-679,420,500,31,22,540,769,-174,-22,-819,-753,-761,445,873,162,808,989,356,86,-90,-377,-530,236,-856,312,570,-869,-223,338,-947,591,-404,-971,707,-627,-856,-505,286,187,-445,173,-30,-768,979,-785,171,943,933,66,541,218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{285,1000,576,-13,1000,342,680,998,187,1000,734,898,1000,393,622,-266,1000,-793,1000,771,1000,-727,144,1000,320,1000,-1000,193,-244,40,-1000,965,935,-79,-232,93,-1000,-163,282,574,-800,325,-381,-992,736,1000,753,-1000,-25,1000,682,1000,444,598,-111,-172,240,696,-1000,-1000,789,-48,-1000,377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{-1000,766,638,-1000,-10,-1000,-192,166,-270,-1000,679,-1000,4,192,-181,665,-772,-259,-1000,361,2,1000,-258,-960,-680,-1000,-1000,-615,-188,-141,314,-11,-609,-389,-940,-1000,269,93,-1000,80,708,1000,341,670,433,-1000,-182,275,1000,-1000,391,-111,357,-346,-550,245,-1000,-222,1000,-725,627,674,410,73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{284,857,142,1000,695,-290,-175,1000,-854,1000,766,1000,1000,-1000,1000,-1000,1000,-976,1000,-755,1000,-1000,209,973,1000,1000,-1000,-359,-905,330,-379,-524,306,-627,-111,147,-1000,141,1000,-833,454,1000,-1000,-1000,-367,398,1000,-1000,-62,1000,639,626,374,131,813,-380,1000,-41,-1000,-891,841,-971,-1000,-823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{612,-1000,-696,438,748,-483,-703,927,979,326,1000,146,858,-519,-266,-1000,125,82,830,-382,813,-1000,1000,1000,1000,1000,-536,-1000,178,-380,398,826,626,1000,802,728,31,-1000,-69,858,570,-404,-767,164,-574,1000,-279,1000,-746,1000,-247,-714,382,-232,-786,-291,1000,424,-167,-569,-785,253,-1000,-275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{1000,50,-1000,1000,-683,27,-674,-322,650,1000,1000,-1000,1000,-322,345,-1000,444,-325,1000,-1000,361,-1000,1000,-336,654,300,-389,-1000,376,52,1000,895,749,-33,23,374,13,-467,-400,-137,-390,1000,-820,-368,825,602,1000,38,-1000,1000,-304,-562,1000,-567,-764,441,983,689,-1000,797,-524,1000,1000,300}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{-1000,831,735,383,874,538,1000,1000,-702,346,687,1000,1000,119,896,-650,1000,-1000,1000,-30,1000,516,-202,1000,215,1000,-1000,-1000,-707,-924,-1000,722,1000,1000,-162,258,-1000,124,898,428,-655,113,-402,-1000,828,1000,-169,-221,583,855,217,1000,-411,-148,-332,-314,521,169,-1000,-1000,521,-608,-865,-593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{-1000,-402,-508,-1000,1000,-975,-615,635,1000,1000,-176,344,1000,1000,-1000,1000,-870,1000,-1000,1000,-662,356,932,1000,26,-525,840,1000,1000,942,894,-1000,-1000,-971,1000,866,766,-1000,-1000,-1000,1000,-510,-364,1000,150,-1000,475,188,-342,-461,872,-240,323,1000,-42,-877,-397,-1000,1000,241,-917,372,-788,-68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{381,623,1000,1000,1000,400,106,378,-36,1000,568,118,-300,-5,400,-1000,400,-564,246,-550,900,-905,-854,-297,-217,57,-234,-875,-510,-1000,-422,538,464,159,563,930,9,-313,64,64,-412,797,1000,-274,-1000,657,1000,-359,978,1000,144,253,23,-8,-126,1000,612,112,121,-810,225,-833,48,-918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMinutes(int):org.joda.time.Period",
            new int[]{-1000,-141,1000,1000,-1000,-1000,13,1000,1000,1000,809,-238,-1000,156,-1000,-1000,-1000,-1000,750,680,1000,1000,-1000,371,1000,66,473,-328,1000,-494,738,1000,-470,-43,1000,-158,1000,-1000,1000,-468,-1000,636,1000,1000,278,-479,878,1000,77,1000,249,1000,1000,-1000,-1000,-254,725,-646,1000,-254,1000,88,516,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{-854,-570,1000,1000,159,-560,567,103,714,-387,354,985,-864,366,-117,94,347,-462,-412,306,1000,594,-325,-659,-634,1000,-59,793,640,152,-362,-730,-1000,970,289,-414,-640,-190,-225,752,-239,215,-529,956,235,1000,-885,-42,-641,26,894,-1000,-828,-117,166,24,-112,771,-157,-921,210,-679,502,105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{732,592,-1000,-275,689,856,-271,845,3,682,571,-233,-380,656,-270,860,104,437,877,-711,59,-8,-304,-262,-647,172,1000,334,-93,-804,1,-889,215,357,257,-301,-311,314,-590,-766,355,335,922,-791,400,308,-387,-542,-763,-522,827,-400,89,-628,-830,-258,43,-238,-1000,-840,-228,-912,-1000,732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{182,144,645,875,487,684,949,795,71,-410,-883,524,-373,-167,91,-73,-937,-128,-313,388,-86,23,-98,-655,46,414,-322,768,540,563,-333,-880,192,-488,-134,-721,726,908,448,-605,-482,-510,586,-288,400,-5,-387,36,933,678,-521,-308,441,-628,-858,589,-950,262,254,305,-11,427,-680,4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{-501,597,354,-596,830,541,-22,67,-988,40,-82,781,-632,-506,-121,994,-965,-128,894,-92,-806,-72,-576,110,125,-606,-277,-569,-252,210,270,-912,885,-250,-556,129,-903,488,26,-330,-293,-444,-377,-487,-678,-717,632,325,62,-94,981,779,-464,429,-420,789,616,823,104,-705,30,675,-411,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{242,-71,-632,233,-590,202,-467,-221,988,5,-340,-272,-882,-683,-327,-350,-926,43,-884,988,532,-864,740,842,264,-160,-260,-203,-744,-786,308,-535,829,1000,-504,324,-26,348,-156,970,-172,977,694,-69,980,-80,-353,853,-868,173,275,-983,515,-721,-187,-61,451,177,-643,-910,423,-529,839,-335}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{-871,-301,-61,763,-862,148,21,-662,944,-717,-858,-131,506,977,260,-218,696,363,-136,391,-559,-638,415,345,-172,732,-755,-600,-53,801,840,700,831,996,807,-297,-516,705,236,-620,710,-916,671,385,210,989,443,-491,249,380,718,-49,91,-732,320,649,346,-446,85,-739,-601,961,330,244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{-699,939,-827,940,195,933,-416,132,-387,418,950,647,-258,-88,386,-715,-532,892,560,2,691,-109,724,-579,-273,-364,403,769,703,-449,-226,543,427,-173,177,175,853,-261,583,433,-30,-55,-629,-603,720,64,-731,-555,360,-977,966,894,-404,344,-443,-321,-239,-133,-287,-220,-673,-780,281,-24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{-517,-562,607,-396,572,131,-795,701,507,-573,422,55,206,962,-23,968,-355,205,164,711,-410,-174,747,-431,-987,269,-104,124,-560,-83,-946,689,-946,178,22,-250,-969,241,-74,750,262,350,250,643,423,967,-343,-745,-849,423,938,-320,991,-686,79,699,-267,242,216,516,-221,290,995,988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{-66,-708,375,1000,-442,1000,1000,376,78,-1000,-1000,591,609,-34,-112,15,-212,-52,-1000,755,-888,-917,797,95,-599,1000,-1000,-278,-190,885,-26,247,1000,-472,962,-632,-23,716,44,-1000,-187,-467,1000,-757,387,468,-375,25,583,296,-205,-515,624,-815,-1000,481,-211,-561,-244,-58,-237,448,-547,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{705,-278,163,506,-91,1000,-51,305,393,-3,43,297,-258,754,-1000,263,920,1000,434,-625,904,-54,724,-579,-941,1000,344,897,901,-417,-636,-412,-80,-97,679,-819,521,-94,262,-1000,1000,955,1000,-296,1000,1000,-873,43,-156,-355,502,-825,-404,344,-713,-1000,-239,-156,945,-322,-673,-931,-1000,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{-5,336,463,355,206,-171,1000,-193,-1000,455,45,846,-809,-743,294,-985,-33,-672,1000,894,-463,-291,-531,-267,631,-1000,-374,-106,472,54,756,-1000,1000,290,28,-168,-193,55,89,-445,-1000,149,-669,-1000,-298,-764,-872,684,739,307,816,1000,-564,890,207,263,1000,741,-462,-1000,314,-53,-1000,-622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{-174,307,0,232,1000,32,-470,888,993,321,167,-291,387,648,-174,1000,-518,-175,-1000,-143,1000,83,786,-816,-221,563,1000,813,26,-488,-888,-626,0,461,-202,-614,49,490,-1000,1000,-195,0,688,0,1000,0,161,-700,-1000,-155,700,0,234,0,58,0,-295,827,-732,-207,0,-973,505,784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{-349,-17,752,938,1000,202,-27,1000,245,35,1000,544,-333,648,-1000,1000,-115,186,-523,-278,536,643,-325,-405,-877,846,1000,495,847,-1000,-1000,-1000,-710,279,475,-486,-1000,568,-465,752,-76,401,350,831,9,353,283,-380,-1000,-728,1000,-632,-913,160,-108,120,410,1000,-1000,-440,517,-1000,796,882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{1000,410,197,463,1000,1000,-147,389,-117,647,1000,345,-780,1000,-1000,895,696,1000,768,-1000,1000,1000,-176,-1000,-855,1000,1000,1000,1000,-1000,-1000,-1000,-1000,996,746,-1000,-516,227,329,-1000,1000,1000,1000,-208,981,689,-1000,-147,-515,-1000,999,-968,-1000,1000,-1000,-1000,140,-446,314,-406,-601,-1000,-1000,940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{11,211,-334,1000,-813,-767,821,-1000,514,355,-1000,475,-730,244,1000,-1000,581,-332,484,124,857,-545,1000,-543,954,238,-512,328,-519,842,913,-504,262,952,378,-416,1000,-345,-268,37,121,212,-355,-811,1000,798,-1000,216,-487,782,652,416,624,364,774,-384,-651,144,204,-770,-511,631,-685,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusMonths(int):org.joda.time.Period",
            new int[]{-974,-211,1000,962,1000,173,-285,328,355,-170,1000,985,16,833,-690,1000,-1000,142,-1000,397,24,839,812,-803,-960,846,-243,517,474,-305,-1000,-597,-1000,211,289,-414,-1000,1000,-1000,1000,-773,475,350,1000,-20,435,652,-1000,-1000,-515,1000,139,849,-284,-108,856,594,1000,-649,420,185,-456,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{424,728,464,-741,-58,-911,580,-234,314,342,-725,427,-532,339,-101,-397,208,529,-426,-506,786,-843,179,173,146,481,-67,30,589,-757,520,967,-99,-639,22,-305,822,598,809,-430,160,831,-41,542,801,469,-202,2,-582,-443,-288,580,770,-864,-495,265,143,569,-743,-373,701,-314,922,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{-1000,-420,49,731,-654,1000,-1000,226,-1000,-409,1000,34,-618,-1000,-605,-1000,132,495,-1000,-7,-1000,-558,1000,-956,-1000,-1000,-1000,29,-1000,622,-170,-969,1000,1000,-931,399,-1000,970,-244,1000,-176,-1000,-435,266,-1000,-766,1000,-1000,404,1000,331,-98,170,-1000,1000,1000,1000,-251,104,615,710,1000,875,51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{-59,918,-163,47,764,52,-444,-941,-672,-572,-482,-770,-869,-981,792,-843,753,-562,-609,-897,-868,-106,209,-360,935,301,-147,694,533,498,-929,-923,365,-690,796,-377,-674,952,698,94,-772,153,-429,-797,334,296,902,267,531,853,506,310,760,-542,266,-272,24,51,681,-956,486,-287,-794,744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{959,-955,-856,-337,-554,252,-727,-975,280,-568,-719,828,897,-986,630,-979,-696,773,507,-358,-101,-611,145,-40,461,-867,8,506,839,-53,-935,-106,171,-101,-300,288,297,815,-308,-497,423,745,113,-170,107,831,-972,-81,125,-856,-596,279,-104,118,-183,-675,-199,-717,169,-735,948,-790,-59,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{614,-260,-417,-666,-474,-703,11,32,-185,-946,367,793,583,-989,-238,121,-343,-374,-647,48,390,-729,669,-651,-161,761,-296,162,966,-915,-439,-772,751,152,-182,-22,4,-797,-374,785,-978,-619,-17,-182,237,-110,669,176,535,-865,839,203,-11,-686,-550,-737,468,-970,340,604,726,-347,-930,421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{-555,-492,612,608,29,-696,403,246,721,516,-372,313,854,-473,21,-35,-300,-1000,-30,-182,257,-164,-770,-562,35,879,905,-513,-66,279,-320,-258,-1000,941,-197,-983,-1000,0,-604,112,273,-86,0,645,1000,531,0,-777,-325,-1000,-745,-101,-1000,648,322,354,-424,-513,-710,885,784,1000,1000,480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{250,9,477,-614,-131,-468,1000,231,999,-1000,201,-213,59,-100,-292,737,709,-1000,-1000,525,61,55,-76,98,-999,1000,-257,-741,-222,-1000,449,-737,639,-759,-17,-96,-671,-769,-496,1000,-918,-1000,-602,551,541,-242,1000,147,169,-393,860,-218,-402,-360,-168,-507,840,-131,-274,1000,-325,-321,-304,936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{1000,-202,-135,-976,-446,-735,72,691,510,-55,-1000,335,96,-352,340,-1000,-279,1000,-71,-756,716,-1000,281,145,469,-126,-275,384,1000,-794,-135,892,21,-710,-598,-103,1000,1000,593,-778,457,1000,38,-974,876,1000,-883,-54,-193,460,-705,775,697,-782,-623,-208,4,67,-625,-887,1000,-867,880,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{-1000,296,-79,1000,371,589,-590,-691,957,1000,964,629,921,-477,-1000,-100,-976,-285,-725,950,-58,-930,286,-464,-572,-545,1000,-1000,77,607,333,638,-878,820,-802,182,-1000,-842,-773,-355,416,158,357,336,-80,692,20,597,-175,-1000,-226,155,-1000,-62,1000,541,49,-874,659,1000,815,1000,1000,325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{821,265,612,-407,-209,94,685,804,135,619,-72,313,-526,469,-215,-255,-49,1000,792,-904,674,156,-676,813,1000,-479,-947,1000,1000,-437,751,1000,-23,-1000,-61,396,1000,-508,983,467,1000,307,1000,-1000,34,-757,-1000,-1000,-480,340,-1000,1000,1000,-671,-1000,-557,-693,214,-710,-1000,225,-296,1000,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{-210,-850,1000,175,1000,-344,72,202,364,604,-877,282,848,290,1000,-1000,-279,-1000,-230,-756,-864,-180,453,-141,-1000,-84,1000,-1000,156,-909,456,892,21,-710,-1000,165,-1000,839,-1000,-671,-522,1000,38,1000,1000,241,67,1000,-129,-1000,113,-980,-1000,1000,-56,167,1000,107,-625,1000,-535,366,202,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{241,659,999,-772,630,-1000,-879,-420,-595,-452,-324,-625,-575,-53,436,-290,1000,-1000,-1000,39,36,-1000,724,158,-673,1000,450,-1000,-1000,-584,727,-755,-270,-285,362,-628,-62,1000,255,-1000,-819,966,-1000,1000,1000,940,117,1000,-30,302,551,-284,66,60,517,942,519,821,-348,-169,-208,-597,-134,908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{1000,587,-578,-110,1000,-1000,665,-1000,-1000,-706,-914,-849,-1000,98,-823,-881,-29,211,-963,-1000,-2,-63,-1000,1000,1000,610,-1000,828,-1000,-59,-1000,-833,-1000,-1000,1000,-925,776,-514,348,-280,-1000,746,-495,-1000,512,216,1000,734,-969,1000,-401,1000,1000,-490,-672,-565,-922,1000,918,910,-664,-773,420,480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{-400,94,-1000,686,572,252,-448,-1000,777,377,486,-345,-623,-631,-1000,10,-29,-193,-1000,171,-678,739,-100,294,30,-303,-97,-572,-1000,417,-1000,-755,-1000,81,-860,-557,-244,-585,-528,79,-96,-187,-707,-296,-888,476,713,813,-858,643,-419,439,1000,-1000,728,725,-164,123,1000,1000,183,627,1000,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusSeconds(int):org.joda.time.Period",
            new int[]{803,17,740,-518,1000,18,823,28,141,-382,-1000,298,291,971,1000,-1000,-400,289,-1000,-1000,-211,-148,-975,-26,1000,-464,357,-476,1000,37,496,642,-117,-794,687,-227,400,85,-64,-473,-1000,1000,-166,-267,1000,316,-169,1000,-1000,-572,-61,620,-1000,853,-667,-168,277,1000,-1000,877,-1000,-603,661,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{477,574,-410,1000,-978,921,386,1000,-699,400,1000,1000,-1000,-1000,773,-1000,-1000,-1000,400,-702,1000,-1000,-1000,-752,1000,378,-1000,-1000,-1000,885,535,-1000,-616,400,194,74,831,-1000,336,59,-1000,-1000,1000,-568,1000,-1000,383,-156,101,-127,-175,253,-547,-1000,1000,408,200,105,146,581,-1000,-1000,-898,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{-679,20,14,-336,-352,1000,-1000,1000,-806,-1000,645,735,703,964,557,-939,-710,1000,300,1000,174,434,-3,-596,240,-736,886,424,866,108,-281,-1000,-1000,355,1000,-377,153,-99,-875,-175,-215,-146,585,1000,-527,325,-400,65,-626,-1000,209,1000,-17,733,468,1000,421,-402,1000,-60,318,303,-708,49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{-476,786,720,-827,-494,-85,35,-782,32,-312,136,239,33,203,617,-592,405,-438,-817,-454,585,-105,-818,-521,-905,448,222,-892,-550,-691,905,7,-170,-753,-515,-752,41,-515,148,622,382,270,-476,-628,268,563,21,178,-266,-664,-374,-789,243,-437,-64,-200,649,-745,-660,-524,-407,-351,520,4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{-848,-989,747,-636,116,-567,-223,-412,936,-543,-386,878,62,-600,878,477,-804,860,44,386,771,-983,470,-760,897,97,-225,374,948,607,871,446,988,-158,-241,846,320,542,53,-419,571,543,132,297,943,842,-222,-300,-474,526,-756,724,896,741,783,525,605,42,845,-395,-348,289,393,-822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{877,-383,727,136,132,-172,-980,406,-759,-671,571,-390,-821,846,-766,-642,-798,261,-949,-797,-373,77,-148,880,-429,-653,-10,217,191,-212,391,-318,938,-349,842,-621,-520,-669,-651,777,-967,-337,287,-563,87,-606,-937,582,747,404,66,460,-723,-81,788,-834,-209,167,161,817,-488,-264,-595,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{176,-510,145,73,-566,-30,-466,767,-856,112,937,804,-475,250,861,306,-156,-218,-343,518,-359,328,-582,176,-81,753,-901,511,-42,-91,124,-7,413,799,561,746,437,263,-197,-960,-261,-737,606,264,-358,369,-668,321,537,-829,303,557,394,948,253,-11,440,-784,73,856,-474,484,-739,-645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{-446,400,-696,441,363,-767,-247,-700,770,61,-854,-175,-527,290,-382,330,-729,-112,-433,172,97,718,795,-974,-462,456,-92,286,26,995,-197,744,945,-903,-126,-440,958,-540,-547,-14,-987,-664,-122,-708,-328,-617,632,-69,-298,-502,-20,89,172,-924,690,454,-283,327,572,312,51,-908,-182,-144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{370,-78,419,-992,832,-890,-704,-24,967,829,210,-742,551,-444,-1,898,273,584,239,407,-667,-467,39,-60,-985,-446,-647,114,-782,-859,927,115,971,372,802,-342,6,745,143,-221,633,-191,-883,460,-641,270,483,-366,-55,384,658,255,-56,88,-913,-195,-850,-454,958,-135,844,-429,-492,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{-11,73,349,-290,-464,-927,226,0,395,32,-79,315,1000,368,-529,-636,-90,46,678,0,1000,-591,175,341,-81,-1000,1000,-649,659,0,-520,0,0,360,0,254,-446,982,329,0,874,652,385,-726,1000,-635,453,-518,-1000,149,-50,-508,-743,0,907,831,-1000,789,364,-1000,-290,-190,1000,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{-36,-327,348,-890,169,-484,-990,969,264,1000,-162,-707,636,-733,155,-398,-600,-1000,107,27,-797,-1000,410,-34,-333,665,73,10,-402,8,-686,-622,-170,-307,249,-68,138,-515,789,622,-11,-378,-463,-258,-221,889,86,338,930,-519,339,-678,1000,692,46,659,649,236,-431,-222,236,169,-722,158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{-66,-643,113,-422,-601,-1000,-863,-158,-113,844,-771,-155,1000,-1000,121,242,-1000,-812,563,-75,577,441,708,-1000,856,-97,1000,967,416,344,6,-330,-548,148,-457,50,-341,1000,852,-486,557,577,175,615,423,1000,-629,-151,363,514,-151,127,1000,991,705,1000,-144,-668,391,-89,-600,135,-346,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{-221,864,91,22,-668,360,721,-1000,-73,-1000,319,945,-593,926,158,67,947,985,-524,-282,132,1000,-1000,-429,-39,-566,347,-609,188,-646,1000,802,390,-177,-643,-415,28,155,-961,1000,427,638,370,-104,481,-589,-207,-388,-1000,279,-766,117,-1000,-1000,15,-838,-603,-898,179,-105,-549,-620,1000,-151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{-189,-1000,834,3,560,-129,-625,444,328,-1000,-288,106,-1000,-304,96,459,-1000,865,-674,366,415,-266,391,-629,1000,140,-710,772,920,1000,1000,-11,1000,-975,290,-61,279,-363,-653,-361,-346,329,160,408,404,527,-1000,26,547,1000,-1000,1000,304,530,747,-697,635,-239,1000,600,-299,-336,-558,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{831,301,874,-161,-146,479,329,19,-1000,-1000,806,-476,300,1000,-1000,-1000,-26,-56,-1000,-1000,-649,-648,-501,1000,-1000,-440,433,-1000,-707,-1000,1000,-1000,-831,-1000,-378,-781,-1000,-572,-1000,777,-410,-1000,688,-1000,-601,-1000,611,674,589,-962,726,-252,-723,-897,788,-1000,425,-1000,-1000,817,-467,276,-496,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusWeeks(int):org.joda.time.Period",
            new int[]{-305,-168,-1000,925,-1000,-552,-1000,156,-909,1000,1000,589,-1000,647,1000,1000,-913,-703,346,-354,388,1000,-461,-1000,799,-559,-134,1000,1000,1000,-1000,873,-1000,1000,1000,1000,1000,1000,-478,115,670,198,1000,431,-135,-224,-1000,1000,-1000,-571,61,-60,-616,1000,-645,490,46,342,1000,-20,-334,501,818,-149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{-1000,-658,-484,-85,1000,547,-1000,-911,159,-430,1000,67,-1000,-1000,594,1000,-745,477,-408,1000,-1000,297,-126,-417,880,374,-1000,1000,732,317,-106,1000,-773,1000,-1000,392,1000,237,418,949,-762,-392,-1000,-746,1000,703,1000,-934,467,847,-1000,-14,375,378,-616,15,-134,200,688,-1000,-180,-1000,-980,-85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{-1000,-178,-409,-353,854,-1000,27,-1000,1000,463,1000,992,-413,-698,1000,1000,-478,-108,-287,-483,438,136,-211,-675,-661,1000,-556,-522,-558,199,674,134,1000,486,942,762,666,271,1000,918,730,-832,-630,61,199,929,-1000,-399,111,971,969,1000,-504,266,-387,-427,9,447,204,-568,-996,-597,357,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{363,321,436,316,-250,607,336,-119,207,771,-827,79,566,581,666,-703,-160,-252,805,-766,-626,538,760,-671,472,-431,845,985,-113,534,583,181,995,-504,871,594,-645,88,-595,-257,-603,-543,81,-215,483,-875,932,611,-721,68,-735,-341,-536,258,-806,474,-855,-318,-520,456,-938,412,707,-533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{623,-301,220,-983,148,250,963,585,295,-142,363,-688,-400,386,-214,-221,274,-265,-314,-135,990,-143,584,802,-771,-818,-754,437,970,830,-951,450,-21,928,-596,832,761,22,-671,255,365,-936,784,-853,675,-179,225,-734,523,-349,-33,-654,74,554,446,-996,251,164,190,500,-321,-84,459,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{619,-987,-144,533,-352,11,975,-637,496,850,135,-153,-661,987,615,-40,-854,-94,-553,658,-838,752,-218,-998,569,71,-880,411,-555,729,297,950,-257,952,-998,107,-279,146,821,847,-561,-365,330,917,352,-176,605,-649,756,369,-368,906,912,945,624,760,-931,912,-40,252,223,778,-394,-382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{477,-292,-78,281,-843,-336,-505,-115,-342,31,-941,-484,621,128,-251,-849,427,-956,-440,-830,625,889,0,523,-746,111,-943,-200,-97,824,522,-482,812,-971,-384,469,170,-741,-947,344,841,-276,235,999,207,344,-915,-276,558,179,-815,-354,505,731,344,-977,270,-655,505,578,-609,509,982,279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{-19,923,-1000,-791,1000,-361,-862,-129,1000,-268,1000,565,-745,387,392,995,-1000,1000,668,1000,-1000,-1000,-1000,-394,788,609,202,-957,280,-1000,963,656,-124,577,-280,-191,0,-256,0,-779,467,-1000,-283,-1000,1000,0,0,1000,336,799,999,532,-228,-1000,1000,457,-437,-300,-707,-561,0,-684,0,-765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{577,301,-135,316,116,-914,-619,1000,207,-958,-510,-487,-249,-1000,-437,-1000,-14,1000,-949,-285,-626,538,-431,-244,-745,-1000,-636,383,-1000,534,-651,-552,330,-1000,365,494,-1000,-110,769,-610,669,-860,-1000,1000,-1000,-322,-77,1000,-60,30,-60,1000,-536,1000,-806,-300,236,-321,-520,819,424,136,707,-654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{-763,-294,-587,-1000,537,-778,-1000,-1000,86,474,1000,18,-126,608,535,1000,-653,-601,-685,648,-487,330,34,-849,395,1000,-746,-936,-664,-234,1000,514,811,666,-445,311,1000,-313,448,-104,301,-345,-222,-486,1000,509,-688,-48,-265,684,257,306,-249,-811,527,-383,60,-234,-77,-541,-460,-654,-480,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{310,599,-184,1000,492,544,1000,196,802,785,-593,428,114,-258,956,-1000,-468,912,233,754,-911,77,937,-610,-119,-1000,789,1000,118,601,171,391,720,940,1000,1000,-1000,-79,-147,-464,-620,-1000,481,217,-372,-1000,1000,708,-853,656,-1000,987,673,943,-324,23,839,-60,-1000,873,-567,498,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{-1000,216,1000,116,899,-528,-109,-715,-919,366,777,-76,46,452,16,209,737,-979,-23,-296,1000,-133,1000,-1000,178,-95,830,547,163,385,-29,-912,930,115,1000,283,-825,1000,814,-37,-1000,689,106,154,-329,-294,629,-639,-115,-867,-860,702,-1000,716,-1000,859,-1000,648,-1000,190,-1000,-905,237,596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{-870,-511,-715,-1000,794,-827,-634,-1000,1000,-17,723,614,-415,732,1000,1000,-1000,-988,584,340,105,257,309,-397,905,102,534,-1000,288,370,1000,74,1000,84,-776,619,1000,-734,311,502,812,-398,-17,-896,1000,246,-728,30,-73,1000,1000,-285,-570,-625,677,-662,-416,-664,66,-1000,-217,23,-705,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{-150,-366,307,-178,8,-1000,455,233,-155,-1000,-498,225,-391,-1000,325,-720,-660,666,-641,-1000,388,538,481,-151,-437,-1000,-948,-625,-1000,341,-600,-1000,924,-1000,633,1000,-834,-134,1000,681,785,-380,-1000,526,-1000,-253,-170,1000,132,678,-16,763,365,1000,-520,-148,-337,-374,-715,-391,431,462,1000,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{-135,-430,-231,118,-400,-247,-370,-131,-332,233,-547,-568,159,-268,-1000,-32,774,-505,64,-816,2,-38,-275,573,290,-919,218,982,-470,793,-6,-655,213,-199,-1000,-521,-219,453,-100,-136,535,-283,503,-167,1000,-85,-325,65,184,-193,-153,930,-928,142,-596,899,-1000,296,626,469,-712,-301,282,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{-1000,221,435,316,1000,-1000,-1000,-1000,-1000,797,1000,418,238,-1000,-437,-1000,88,-1000,-949,252,-34,197,614,-1000,994,1000,835,-414,-981,-360,1000,-867,330,-68,1000,-81,-658,765,1000,-288,-1000,1000,-599,411,-102,188,-11,1000,-666,-143,-657,1000,-1000,-239,-943,1000,236,369,-1000,-539,-1000,-1000,-421,-654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minusYears(int):org.joda.time.Period",
            new int[]{-1000,-1000,-999,-1000,631,-1000,-1000,-969,863,-941,1000,-272,-861,600,235,1000,941,-268,808,-564,1000,198,-228,144,-815,330,94,-1000,643,68,966,456,1000,-580,-1000,245,1000,-893,499,1000,1000,-398,-70,-1000,1000,1000,-1000,-678,1000,560,1000,-1000,-1000,-797,1000,-968,-1000,432,548,-1000,-520,-910,-789,351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "minutes(int):org.joda.time.Period",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "months(int):org.joda.time.Period",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{952,904,1000,143,-1000,-400,-958,-513,136,-274,-623,628,-1000,1000,363,649,-1000,-329,862,-71,521,-397,-690,96,184,-1000,1000,1000,34,1000,-644,-931,-359,-507,-623,349,174,-401,715,-1000,1000,-1000,502,-894,-477,-367,-1000,-775,-645,1000,-111,770,-471,-557,118,536,-1000,1000,-1000,-1000,-1000,-322,169,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{371,372,1000,-1000,536,439,-669,403,992,1000,-743,930,-911,-79,-1000,1000,-251,-224,879,467,706,1000,341,322,-370,-361,-1000,-394,182,698,-826,-771,-163,-36,262,822,1000,-366,-821,-1000,1000,-765,633,-301,-769,-455,233,-1000,-726,-287,-487,-1000,380,10,-91,774,1000,-340,-1000,-1000,-939,325,56,416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{-854,-866,248,-508,755,-382,-20,-900,-823,-344,477,20,-145,-44,-408,527,821,-726,471,170,590,482,-873,897,208,432,383,-850,668,92,134,-832,-116,24,129,752,-554,381,-183,494,533,288,173,-392,476,747,904,355,756,-24,921,339,-516,537,567,-79,781,-535,725,402,-88,185,633,-279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{377,612,591,568,604,-239,-680,330,402,457,-304,-202,-743,95,64,677,107,-773,79,800,558,-9,-84,-745,-305,-323,-624,417,825,38,-74,-802,-534,-457,-979,942,-56,-133,361,-951,-927,-376,138,-370,-430,-425,-209,-730,-916,398,277,-652,-977,435,-517,232,732,820,-983,-822,981,847,-665,764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{-184,694,-420,661,745,-152,396,-165,-595,392,74,-460,604,600,-503,-1,411,-179,403,394,-884,-65,-980,-652,-480,-397,337,260,-563,-979,-372,-120,278,-602,139,-393,-331,-460,145,658,-890,-407,-1000,473,-100,-737,262,501,-219,627,454,865,-962,49,665,-700,158,-359,775,245,-399,204,831,996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{-666,-718,-960,269,321,-588,-622,268,-198,363,434,-559,-470,-941,-93,-187,-97,335,262,137,739,-39,-162,-703,-895,160,-639,506,-368,353,10,768,-225,430,-468,-858,498,-13,-842,-979,-25,651,527,-153,101,-139,229,483,805,-708,137,-217,-523,-498,242,-162,190,281,-614,-170,-206,-265,-51,-547}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{747,148,723,-192,159,-200,-705,-1000,775,349,-760,-559,-1000,-941,-747,391,559,335,1000,-1000,-1000,-672,697,608,-473,1000,737,352,-368,1000,-1000,-24,-87,-808,-468,444,-714,254,-1000,446,642,-539,-758,477,968,1000,764,672,-1000,-1000,503,495,-499,-498,-753,-413,1000,980,519,594,489,908,-233,119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{1000,425,1000,451,708,343,-1000,957,410,-574,-417,754,-1000,-1000,737,-188,1000,1,-351,1000,791,430,-71,-554,-455,152,-809,200,357,176,-1000,-855,-515,26,524,1000,-585,-189,805,-820,-1000,-1000,1000,-628,-639,-485,-68,-1000,-40,799,1000,106,-974,-73,-669,360,834,-508,-246,-1000,-126,1000,569,506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{1000,-489,-646,568,731,291,-494,-129,1000,61,604,258,-1000,1000,-259,-261,1000,1000,171,63,-78,224,1000,316,-896,1000,-98,-1000,-201,1000,-855,-1000,-509,582,221,743,953,1000,-1000,-608,824,-454,-98,-761,756,1000,3,99,-150,-1000,787,842,506,-994,-1000,-140,1000,142,1000,-179,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{1000,-31,1000,1000,731,291,-1000,-129,659,-983,-375,823,-318,-1000,516,167,242,-804,-67,385,1000,985,-324,869,-1000,-398,-746,534,354,1000,-1000,-1000,-1000,121,555,1000,368,1000,231,-446,101,-413,1000,-761,444,-235,-514,-1000,-149,-1000,836,122,83,697,-380,33,-465,532,-1000,-1000,-1000,-179,-95,296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{0,1000,764,549,-1000,-450,-346,998,523,560,-495,502,-786,-866,66,656,-209,-822,697,617,719,728,-936,-1000,-162,106,-619,406,-508,353,488,-855,-1000,-739,258,105,-111,349,885,-1000,743,-410,483,-927,-74,1000,-63,-1000,-1000,-860,451,19,-938,712,-723,706,1000,-431,-1000,-219,-1000,251,705,982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{339,587,590,769,-1000,-418,-504,1000,1000,-657,-174,1000,-330,-307,-547,-168,-572,-1000,631,-22,-259,848,-612,-600,-1000,852,-1000,630,-957,1000,216,-1000,-25,-562,841,1000,396,508,1000,-522,899,-580,698,-1000,1000,1000,-789,-358,-483,-1000,407,653,301,359,-1000,1000,1000,1000,-1000,99,-273,689,10,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{373,823,53,1000,1000,342,-222,1000,734,-220,68,1000,-20,-180,1000,-275,1000,858,-423,1000,1000,1000,-62,-842,-859,538,-1000,-574,-123,-383,-864,-1000,-376,719,1000,262,403,-1000,991,-1000,-960,-898,-789,-747,-639,1000,-397,-397,762,-1000,1000,57,-419,-202,-1000,816,914,-1000,-128,-312,-383,666,59,66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{-646,971,825,379,-1000,-1000,677,1000,-761,1000,549,139,694,-222,-1000,481,-156,-86,1000,191,-1000,904,-30,-1000,1000,1000,-828,652,-1000,-1000,279,-205,-54,-1000,26,-1000,-981,-400,1000,113,551,-563,-622,833,152,1000,165,1000,-566,-28,-511,833,-805,764,-1000,871,1000,-672,-668,-51,-491,1000,100,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "multipliedBy(int):org.joda.time.Period",
            new int[]{-169,4,-393,584,462,-516,-147,-77,712,418,207,-79,113,1000,46,798,-622,279,-15,-166,-1000,-6,-448,496,-1000,-400,-258,-533,-322,423,4,487,167,-760,1000,209,333,261,381,-1000,-133,591,-997,-951,525,-22,-114,-883,-678,20,114,-760,386,539,-419,278,452,-63,-459,1000,-744,-733,97,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{-1000,310,-351,1000,-22,-1000,-616,843,1000,-1000,-644,-503,1000,-1000,-268,-778,143,-1000,1000,1000,1000,804,-317,-1000,1000,-426,-67,1000,236,1000,-934,-609,737,574,1000,530,104,-471,-295,147,27,1000,286,-341,-1000,-529,-330,1000,-1000,-1000,799,1000,1000,-384,1000,-1000,-1000,-1000,599,-1000,31,-400,-1000,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{798,438,103,925,850,-158,-166,917,-408,-96,576,384,555,176,-187,988,-728,-624,658,669,999,153,-525,-759,97,-390,864,152,-301,-821,83,-603,-712,-948,431,40,264,98,-853,-738,-318,-44,2,407,-55,-183,-130,-893,-577,320,351,255,-241,923,-271,-693,-127,-868,-540,-786,621,67,751,-861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{441,801,205,-45,-824,241,177,650,-488,637,-41,-702,-496,-948,-551,-111,737,-167,338,282,135,367,226,103,178,989,-535,-512,858,771,462,907,-337,623,676,-348,383,794,546,-858,-416,-327,198,675,-773,587,-67,-90,-891,-813,-768,30,-460,-2,-735,253,992,698,774,188,-670,767,587,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{685,-681,-15,-796,-991,-881,705,-296,-57,30,-574,-906,730,-482,-740,-77,-381,192,994,-869,-561,817,-404,944,844,-213,864,-298,-893,580,985,-883,286,-610,262,850,68,-167,-719,814,-313,379,-602,138,-77,995,-420,-655,370,400,361,970,950,245,940,-414,-91,-413,-683,993,-537,93,-249,197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{663,-459,813,726,-933,409,815,-185,370,-406,-478,-962,261,656,-302,97,-513,-661,-766,609,-680,609,449,-984,481,959,-573,-986,-87,-711,-773,262,761,-652,229,-442,-596,395,929,-837,-548,534,-899,488,264,977,377,-17,694,-582,-953,-268,-433,614,137,1,679,329,686,780,-15,847,-454,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{-451,556,-723,455,-70,323,43,-437,578,-712,-211,-486,744,977,-298,290,-984,234,5,28,-857,455,146,-638,448,-530,-964,-753,435,70,-681,733,119,847,-848,557,-879,710,988,911,-380,184,-537,-691,-85,478,-372,-939,-522,-848,518,550,209,-804,-697,259,848,351,125,262,-580,-445,-860,-227}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{-547,953,770,-971,-561,-630,576,-884,-397,329,-66,-542,-391,-407,-150,-261,-445,-954,-120,-272,868,357,41,48,-884,-259,517,-842,-69,-857,237,-132,-843,-133,-106,-896,-122,-653,-4,108,-1,523,136,853,722,143,-171,21,406,-838,-16,588,108,-590,-633,795,350,-321,242,-930,-410,-516,-196,-646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{-297,699,699,-292,611,-909,336,-697,-341,-184,366,218,345,380,104,-77,-1000,192,994,-1,1000,208,-485,-555,-941,-1000,864,-378,-881,-1000,-28,-883,-1000,-1000,-278,-624,-206,-1000,-983,192,68,721,-1,666,1000,-396,-215,-541,626,-45,768,746,261,57,-308,133,-434,-1000,-678,-1000,493,-1000,-81,-803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{-779,714,-46,1000,329,322,815,-315,845,-1000,811,446,1000,1000,-256,553,-1000,-208,-766,-446,-1000,1000,121,-612,-205,-1000,-303,-463,91,-170,-1000,-1000,451,-838,-171,385,-596,515,453,1000,-93,534,-775,-32,248,-128,377,-1000,-278,-902,221,878,1000,25,137,-129,-624,-815,172,715,417,847,-304,16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{-541,-1000,-744,338,-1000,-622,334,889,-448,634,-1000,-590,233,-7,-1000,570,-521,936,958,729,1000,-165,1000,613,575,1000,1000,888,-678,-559,-738,680,-422,635,-351,698,-392,656,67,-1000,750,988,-797,-1000,-478,193,262,1000,198,-886,70,184,-226,599,-557,1000,21,1000,477,-795,354,15,-282,710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{-1000,-73,630,40,-563,589,444,746,-338,669,-893,487,110,361,-608,666,-909,1000,-874,-199,974,980,1000,131,-82,1000,1000,80,1000,539,-851,-620,-226,-129,0,-583,-940,464,474,-1000,862,1000,-137,-26,150,589,434,273,-563,-680,1000,-21,463,497,-298,978,-42,682,150,213,-179,1000,63,914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{149,769,-599,1000,1000,-11,-232,1000,-580,906,203,-14,951,231,50,442,138,212,798,165,956,949,-896,-661,-541,-89,1000,519,420,161,-278,-354,-938,-1000,-672,401,-804,-491,-213,-757,-309,1000,467,970,302,920,11,-1000,-507,289,348,585,-496,248,0,-539,-198,-597,-323,-1000,894,325,894,365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{-928,-366,913,-483,611,-230,-447,400,-341,1000,-220,530,218,679,-470,-77,690,482,414,94,618,1000,-621,-555,-1000,-398,645,-279,-609,-802,-547,-883,-1000,-653,-26,-232,-703,154,-896,-756,-216,1000,-854,1000,716,-355,-745,-152,1000,1000,1000,313,261,1000,924,803,-296,-876,-967,72,1000,400,783,2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{-420,300,-54,1000,-103,262,213,-145,-407,730,98,456,1000,-18,-348,811,562,725,509,-322,569,-75,264,459,705,385,-266,-703,678,357,-882,-342,-401,-115,-623,1000,-1000,-79,341,-658,593,984,37,-261,-796,875,955,-445,-1000,-744,-377,-16,139,782,-381,-286,-1000,106,667,-996,651,643,293,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{704,-400,1000,1000,785,1000,-1000,-922,993,400,1000,1000,-882,1000,-3,1000,-1000,1000,522,-400,429,-624,784,-744,-666,1000,-1000,1000,-1000,-1000,251,996,-1000,629,-1000,-192,979,1000,1000,200,-1000,-1000,-1000,1000,-262,400,-509,-350,1000,1000,1000,-298,-1000,89,778,1000,-941,1000,81,-176,-927,-1000,-425,391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "negated():org.joda.time.Period",
            new int[]{-687,510,768,-46,19,-519,449,-1000,-701,211,369,658,62,-16,-1000,220,475,1000,656,-1000,698,218,-844,451,-445,681,827,450,-1000,-349,-609,-539,-664,-379,-1000,1000,-608,-109,-61,-683,511,684,-513,521,639,312,368,-305,-62,437,457,-1000,-1000,451,-320,1000,-1000,-66,167,-997,1000,-768,-222,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{533,718,-210,-907,-535,298,-211,-173,-939,709,-899,812,779,-797,-334,100,598,203,231,-958,238,-7,-26,246,31,-715,-537,-501,-456,-171,-941,-293,333,899,-14,584,-336,-296,-523,-888,434,570,609,571,-910,-944,-214,323,-118,-888,152,504,466,-170,-356,-776,552,-619,-86,994,72,-672,851,-305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{-12,882,-86,-71,212,-120,335,965,-386,670,-134,663,246,-76,57,772,-832,626,-805,303,-410,823,434,-317,-20,265,107,653,794,-832,892,-958,789,993,-384,-765,-710,-634,-341,376,-768,15,-844,-800,324,1,500,734,234,355,-14,66,382,780,899,-123,-416,50,798,-515,-400,-627,314,-205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{-1000,130,283,-113,-118,-240,-646,392,-801,557,1000,-1000,339,-539,-176,-1000,943,78,-1000,441,-1000,-1000,882,649,511,-62,-604,1000,-233,-1000,1000,1000,-404,-1000,240,-192,1000,-484,-1000,-162,-1000,121,-794,1000,1000,-1000,1000,192,-89,717,83,797,-728,-895,791,-141,-1000,-241,486,246,-766,318,317,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{518,409,1000,-69,-859,-308,400,-520,-823,42,-1000,1000,-390,1000,82,508,-130,-569,321,1000,975,-406,-552,-227,-312,-118,-223,-914,1000,711,-569,-151,-862,564,-1000,-43,-415,-279,-83,-228,271,-605,1000,41,-1000,198,-1000,315,406,1000,486,-400,-353,-337,-516,-108,197,-1000,-154,1000,778,96,-509,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{1000,879,-173,-1000,192,803,-1000,-603,-546,1000,-1000,1000,1000,-1000,1000,123,1000,-1000,758,445,129,1000,328,-28,132,-1000,-1000,-1000,-707,-1000,-922,812,1000,1000,517,647,-1000,-283,24,-1000,283,854,1000,465,-1000,-1000,-908,810,-916,-1000,843,956,133,-751,-691,-1000,1000,431,1000,1000,552,-949,1000,-428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{-559,756,634,112,57,-804,-552,-389,-540,954,136,-920,-488,402,-170,-362,729,286,-24,787,-656,-631,721,387,-5,-350,-744,836,212,-823,-156,862,195,-874,-670,-987,797,-952,-851,-803,50,-36,-967,416,121,-678,969,242,130,760,282,491,-68,-918,164,87,-334,863,436,147,-374,-255,908,-50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{166,664,-918,-397,-810,-356,-519,-277,878,755,477,35,847,29,715,-921,676,300,519,799,-49,985,522,523,-122,483,-215,-440,-680,-574,826,978,-547,-392,-949,-131,622,711,-733,-391,447,666,201,-537,-276,-118,-777,267,266,447,-122,-171,465,914,194,229,59,-734,273,-764,-646,-685,-798,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{-562,106,400,730,176,-12,-375,-536,-985,-740,851,-901,288,-22,-567,314,240,143,941,927,-472,-680,-682,343,595,-101,396,-591,356,270,708,-681,-70,839,-313,-77,588,-971,475,21,501,-805,830,51,421,-271,907,964,-14,542,-945,-9,2,-436,178,549,-265,970,-264,-442,50,389,-51,-908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{-925,-1000,972,591,28,449,1000,-1000,-337,-483,-213,-729,-283,-23,1000,633,-378,-401,1,1000,0,-280,765,0,-861,-1000,186,-979,0,223,-454,0,-1000,171,-345,209,1000,916,653,-180,128,152,618,885,-488,437,-454,675,190,833,-103,-1000,-500,241,251,1000,-1000,-869,-1000,-223,700,0,-602,488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{1000,1000,174,-1000,-917,-122,321,-672,-952,522,-1000,1000,86,-533,-476,668,-25,418,755,545,81,115,32,394,-281,-700,28,-833,-709,400,-1000,-796,993,756,-83,659,-187,851,46,-858,997,220,493,-89,-1000,-600,-490,-254,-175,-452,-208,1000,660,-678,-784,-479,-615,-176,-41,594,-236,-861,822,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{-295,-380,-664,-180,259,429,-269,138,1000,704,-194,14,491,-249,539,-787,454,-315,-805,-1000,488,355,434,-388,-104,56,-842,746,4,-760,109,979,-611,-445,288,238,-560,1000,-901,-44,-914,913,-465,-800,251,-155,-359,-97,67,-815,1000,-621,-196,814,303,-682,-436,-1000,140,709,273,-83,65,949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{-111,237,1000,594,77,-497,-921,-1000,1000,40,-724,205,-177,931,-476,-252,995,-882,1000,583,147,639,-356,346,-411,-143,-582,-1000,337,-147,-1000,435,-277,762,1000,155,-599,-160,1000,39,1000,-315,292,-797,-255,-241,-767,-509,-283,-1000,1000,664,-184,550,-1000,-187,254,884,264,159,1000,-443,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{-1000,-257,-429,1000,38,-1000,175,1000,-628,-321,39,-839,-442,1000,1000,746,-1000,507,-1000,157,-1000,-76,-222,1000,-224,-56,432,1000,1000,-219,578,1000,256,28,143,-757,-728,-101,137,503,-1000,-577,-1000,-4,672,487,1000,836,235,289,-1000,-322,-821,904,1000,1000,-50,615,-1000,-504,-330,-86,268,-555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{756,-13,1000,-521,-1000,1000,1000,-1000,-781,-107,-1000,480,-160,-1000,265,43,1000,-1000,1000,-605,594,1000,-172,-809,-989,-1000,-981,-1000,-60,708,-741,232,212,1000,-1000,-993,-1000,-182,787,-1000,1000,-16,1000,176,-1000,-486,-1000,792,-26,-896,1000,362,38,-674,-681,-1000,179,-744,804,1000,495,-92,632,-422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard():org.joda.time.Period",
            new int[]{404,6,381,-69,-1000,331,454,200,-1000,634,-36,595,-390,-123,-1000,-799,449,-569,-321,851,-446,-406,1000,821,284,-240,-223,-863,-354,-704,-569,1000,-428,1000,-334,66,-305,-946,-1000,-97,140,-119,753,760,-1000,-178,-957,382,-1000,1000,235,-580,-791,-767,-212,-654,1,-102,934,490,421,205,-104,877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-928,80,125,-943,500,2,-843,293,-988,258,427,-1000,967,193,1000,240,-1000,-211,57,-6,563,117,-1000,1000,1000,-336,-214,219,25,-308,676,-995,-1000,95,-145,-994,-1000,326,-1000,-453,450,-361,411,-889,421,709,547,302,959,-610,-193,-241,-795,-1000,260,1000,139,40,-659,-244,543,844,238,837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-616,-900,936,121,-990,-931,476,334,611,-793,-247,498,-530,474,-113,553,928,-632,622,203,974,419,597,678,783,-105,-771,382,466,490,140,-138,900,-627,-593,34,-39,973,-30,-478,762,-860,483,127,540,894,600,-79,-255,-90,-758,-685,-435,52,-273,504,647,-158,-151,-813,77,-438,-87,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-96,-420,609,1000,17,147,-610,-437,-188,464,-662,613,-759,-1000,-133,182,75,-291,533,1000,-356,1000,-224,796,594,373,804,1000,-382,312,-898,930,813,292,-106,1000,1000,749,1000,-37,-1000,938,531,1000,994,-1000,-1000,536,-642,-878,516,-60,250,1000,1000,-201,302,15,-532,-743,786,-311,1000,-334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-1000,-303,1000,747,1000,1000,-1000,1000,-1000,-6,1000,-1000,1000,216,1000,445,-1000,-720,369,293,158,1000,-1000,1000,1000,-1000,-1000,173,1000,694,-724,-1000,-1000,184,158,-1000,-867,1000,-1000,-307,1000,-1000,999,-1000,837,1000,1000,1000,1000,-622,396,-47,-1000,-1000,31,1000,-154,-1000,-1000,314,378,873,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-1000,175,1000,747,-400,1000,317,686,-892,-134,1000,-582,396,597,1000,199,-841,-467,235,785,-1000,254,-4,983,482,-1000,-1000,-1000,1000,60,-1000,-171,-991,689,991,-875,-276,1000,1000,1000,662,-1000,1000,-186,1000,-1000,1000,1000,517,1000,971,726,-475,-1000,-642,-145,331,-1000,-806,-350,1000,158,713,910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-920,563,837,-1000,1000,1000,-104,443,-28,-392,-221,-1000,389,82,1000,230,-718,-268,328,-1000,966,844,355,1000,1000,-168,-1000,677,-641,-399,382,-1000,-1000,-873,-622,-1000,152,1000,-479,50,1000,-1000,486,-1000,-324,-470,1000,115,86,-514,-482,101,-956,-1000,-481,400,466,492,-1000,-668,-56,-958,-839,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-267,-716,-200,735,823,-348,798,-96,-273,129,-615,-429,460,1000,-536,-55,-549,857,-472,-17,-603,-11,-198,-297,488,446,197,527,583,640,869,655,565,-38,-524,44,-448,883,351,395,880,258,683,-207,-128,171,-433,-922,284,-487,-702,-124,-9,761,649,311,40,-113,-960,-125,459,-797,476,-356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-701,-406,174,-46,206,521,-278,-300,-858,-225,244,-708,719,-530,737,105,-424,-24,228,-350,-86,119,-894,905,360,-266,226,8,21,-28,382,-676,-777,-637,10,-502,-84,635,-891,-53,-81,551,278,435,325,605,-514,860,204,346,-246,219,-507,-720,251,782,-560,-161,-494,2,291,754,390,373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00334() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{863,-585,947,-442,786,951,-448,885,939,-208,695,-20,799,-379,315,-292,-617,920,-650,543,484,-23,-691,-838,-98,851,722,610,-125,-559,-658,-98,-501,-468,297,-550,-578,-401,487,603,327,448,430,739,-200,-639,499,-126,-928,302,-977,539,-196,-31,-910,-207,-391,-10,-960,689,-683,399,644,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00335() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-266,-58,89,730,-246,-159,-484,32,519,562,498,438,-937,-954,708,494,-732,-312,220,797,162,538,-640,812,0,62,-203,855,265,-653,256,309,383,314,487,714,389,465,271,-795,-980,943,133,-27,823,-698,-828,940,-290,-738,946,-411,113,940,725,67,408,592,567,-916,556,712,674,865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00336() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-348,898,220,117,-508,-607,43,-580,-317,905,521,932,-248,570,423,-396,-277,-735,410,-955,679,-666,257,715,473,3,-504,755,-653,-980,-537,-866,879,695,-953,-636,115,488,-479,-98,999,565,-254,542,-700,-680,-193,583,-900,789,619,374,624,-945,-540,-39,696,306,495,-17,410,-982,-614,707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00337() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-489,-15,-112,-602,1000,-865,-14,595,-463,825,-309,-823,806,1000,318,86,-1000,430,-575,592,-990,38,-770,987,1000,429,-220,799,369,162,886,107,-920,185,-653,-531,-1000,12,-328,-122,1000,-886,841,-477,217,-889,968,-1000,1000,-1000,-342,-800,-440,-186,429,1000,791,-46,-1000,-621,871,-618,354,46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00338() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{54,421,-969,175,1000,654,0,-642,-1000,-279,-389,-500,1000,-598,322,-478,420,386,-315,461,-979,1000,-1000,233,-123,823,1000,845,-35,-415,721,0,-671,-76,-937,1000,-947,-564,-577,267,724,602,794,43,190,96,-595,-424,635,-652,-198,-195,335,677,167,307,-758,-375,-762,-223,1000,-300,1000,838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00339() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-431,136,-177,-653,650,1000,-1000,-449,-1000,-1000,104,-311,1000,-712,1000,202,295,-1000,911,-263,974,703,-688,1000,-85,88,1000,333,350,-652,383,-976,-1000,-905,-644,564,-992,-704,-1000,-681,220,672,35,-125,1000,1000,-28,-1000,726,-599,91,-609,-463,93,-571,649,-1000,-651,-254,-736,770,1000,423,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00340() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-785,-764,272,1000,-1000,128,-463,156,709,-727,409,-131,-1000,-1000,815,1000,-6,-207,554,429,641,1000,-620,847,704,-672,-514,185,771,117,979,452,481,763,913,765,15,1000,-118,-1000,-238,882,172,-322,1000,672,-1000,1000,-234,257,-177,-173,-420,167,1000,504,16,692,342,-533,-137,1000,735,469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00341() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "normalizedStandard(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-202,324,-122,865,-782,-691,607,24,323,842,214,305,-1000,-827,90,-55,-1000,417,-981,63,-1000,-167,-78,295,-992,212,1000,678,-1000,-1000,1000,1000,1000,-536,631,774,-1000,-918,675,-133,-620,818,-411,692,-341,243,-1000,-981,-642,-372,1000,509,-27,1000,-314,-1000,-79,761,1000,-894,-270,243,1000,974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00342() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "parse(java.lang.String):org.joda.time.Period",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00343() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "parse(java.lang.String,org.joda.time.format.PeriodFormatter):org.joda.time.Period",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00344() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-916,46,268,193,-186,775,785,-577,700,1000,776,-478,-164,906,418,48,387,-109,-741,-157,140,299,-707,1000,1000,-748,-212,-911,-806,769,-553,-905,675,535,504,-160,315,-1000,521,-1000,-634,765,621,-1000,1000,-321,-1000,20,-792,120,488,1000,-1000,-425,-643,-614,568,-759,-1000,1000,721,-1000,642,243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00345() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-506,-398,424,990,111,-72,156,955,177,-774,152,-267,-518,-141,-300,-603,480,-881,669,-239,416,-840,380,-921,432,781,-396,144,197,-716,-424,426,42,-119,-931,-830,5,-111,826,164,718,-717,-63,268,650,129,-883,754,-132,845,-755,980,160,-707,55,16,-828,-695,-650,-567,-8,389,-201,-315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00346() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-929,840,528,-143,891,320,414,942,704,-384,-327,-360,-923,108,-78,-176,-759,-995,-923,-928,762,49,-908,-514,-253,-900,569,-997,-767,-292,694,196,-179,-40,-840,809,-943,695,-50,-911,591,510,-821,-638,719,910,527,-778,558,-605,231,-772,806,-659,-892,-965,813,-888,-412,557,505,986,-601,904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00347() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{625,-758,168,-392,-337,-28,981,-10,917,-900,-276,-662,169,-562,874,-927,-381,620,-620,26,921,-741,196,-439,-566,802,140,354,-870,-832,809,-614,997,-464,508,-94,-781,-637,-673,969,953,-870,860,523,-921,-844,372,948,958,347,677,460,-62,738,-543,-127,103,299,-697,-209,600,-358,388,563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00349() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{7,-422,268,163,-112,555,579,-141,310,-169,-837,-365,645,494,772,782,-720,208,941,888,891,382,-374,173,-446,258,370,-911,-153,224,-553,878,-562,-117,617,-919,151,160,-680,-243,9,-879,-522,-414,316,51,422,-51,-491,-184,860,-416,-810,-425,-662,483,-457,509,144,-677,-854,366,255,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00350() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{91,368,570,889,-713,303,-562,-806,832,460,265,-204,405,489,-145,-532,809,-783,65,-468,-41,16,338,977,913,-619,733,-490,-164,60,161,-206,225,278,745,-631,135,-598,426,-443,-305,-279,247,-932,756,-70,127,-93,-810,379,-679,728,-974,-811,-713,-164,404,-91,-791,646,146,-430,40,622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00351() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{642,703,-393,454,-308,178,-949,-442,984,-460,-324,846,-56,-21,-851,169,848,-19,897,-172,300,-720,561,-892,-565,826,397,-63,523,-892,622,431,-905,442,-101,-629,86,573,-639,444,96,-698,-97,477,-626,779,817,818,821,-472,-750,-462,998,347,504,164,-116,792,687,316,-23,130,-126,773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00352() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-803,360,-411,282,49,-820,-817,-299,-535,-370,-562,985,-129,551,634,-88,149,-38,217,511,-642,-2,179,-345,377,-862,351,463,923,-862,870,794,-171,251,-693,424,885,-837,-870,-743,-902,-631,513,-666,781,-350,-158,-670,-469,772,704,-267,866,-442,621,-36,-938,-904,350,-680,535,-24,173,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00353() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-952,201,282,759,941,683,97,189,-390,-927,152,-802,-437,-891,44,-395,-516,-397,875,-577,975,754,589,163,901,-460,-340,-892,582,630,710,413,496,447,784,-891,579,-181,-733,-73,-246,993,-268,229,-114,-307,336,-747,-1000,375,842,419,323,876,814,-534,-552,-784,-925,850,-218,527,-849,1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00354() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-797,1000,809,754,628,551,-666,385,644,568,52,-40,-758,843,-791,100,73,943,-444,-1000,88,578,-808,477,782,642,985,-1000,-273,331,241,482,-719,479,-674,433,-301,722,719,-1000,-290,793,-1000,-1000,1000,1000,356,-1000,-680,-583,-718,-585,167,-1000,-1000,-991,118,-1000,-478,1000,798,936,-845,945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00355() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-302,-359,-1000,-434,-666,591,1000,-559,639,523,823,192,472,1000,10,1000,1000,1000,829,351,-73,-713,176,718,-48,1000,-1000,510,342,367,609,-940,247,640,-271,-1000,1000,-485,219,-643,-561,956,1000,-198,-64,175,-869,1000,183,-863,757,1000,-1000,-180,935,-131,-190,1000,-67,-210,986,-1000,923,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00356() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-417,446,-174,1000,100,709,-608,-1000,-712,558,416,-471,-314,-319,233,-362,59,-1000,-357,-283,146,1000,14,1000,1000,-1000,-1000,-904,-400,1000,-204,-61,618,-208,892,166,47,-1000,-137,-490,-822,158,798,-154,-140,-1000,-524,-1000,-1000,1000,-84,813,-1000,-297,-553,-244,775,-1000,-1000,674,238,-850,-67,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00357() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{160,-844,103,754,-1000,299,-781,-830,644,-313,1000,1000,-758,91,-427,-167,73,-780,-444,155,-221,-105,910,332,318,-1000,-164,669,-774,-810,11,-222,817,-133,373,-719,-958,-207,240,-1000,-885,-1000,252,-1000,1000,-260,136,1000,635,-150,331,689,-1000,286,1,-991,980,1000,945,-874,-490,-367,1000,-679}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00358() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-1000,5,872,1000,1000,653,-531,-1000,125,831,-565,98,429,965,-1000,-841,35,252,-845,-1000,-688,702,-431,1000,1000,1000,1000,-1000,-675,-169,211,205,-418,535,-75,779,261,1000,1000,76,-483,238,-489,-1000,281,1000,-1000,-1000,-1000,-256,-1000,-456,328,-608,-1000,-1000,-168,-1000,-111,1000,1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00359() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plus(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{1000,295,142,573,-934,-255,-166,-1000,-820,-148,-966,653,631,67,80,-595,-735,-468,149,1000,-455,-489,533,178,-493,-3,129,-243,51,-1000,-117,-383,-750,-1000,1000,204,859,-1000,-1000,-796,928,29,1000,1000,-626,-1000,-901,580,1000,1000,-1000,364,-1000,-1000,1000,1000,-816,1000,-418,-575,-977,-1000,707,141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00360() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{1000,266,-1000,-825,138,1000,652,-175,1000,-470,-1000,-281,1000,-1000,271,-634,-510,52,-52,-1000,-309,-133,72,-1000,-938,-60,-6,1000,-150,-520,275,-744,525,1000,714,-422,6,1000,-1000,-1000,-737,579,-1000,1000,-43,1000,-120,228,-729,741,414,-474,256,-964,-896,-933,-1000,-1000,-659,-57,-917,164,-362,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00361() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{-944,108,967,423,-655,-132,-149,-367,-712,668,252,60,330,454,-568,169,831,-549,692,261,-554,-897,-850,1000,27,37,300,-203,141,-253,-617,662,-596,-142,-591,201,-222,278,251,788,-238,-659,672,-354,-55,-901,-644,-462,817,-630,68,-902,876,806,-96,443,147,327,285,638,974,-454,-200,-175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00362() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{-115,-611,-99,517,-764,-165,-707,57,-286,-118,-822,-539,-164,125,506,-196,80,-374,663,-770,95,-68,484,768,-834,616,908,-97,837,345,-840,-172,699,637,79,-626,312,953,854,941,-3,-492,819,-501,781,304,200,653,-798,-728,752,130,-164,-869,-937,634,-558,-407,-20,949,-548,876,-920,426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00363() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{131,161,-577,-465,228,551,890,44,150,404,360,553,807,-517,-310,618,123,-734,10,-498,942,-29,-809,-285,-475,383,520,916,-799,405,590,529,837,403,-805,-552,758,-123,-348,589,-511,772,339,743,-714,-173,-480,146,799,-248,-460,-484,-613,762,-679,920,-344,-21,-226,748,571,597,-786,350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00364() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{-844,-668,-850,412,236,-369,936,-969,923,-581,674,653,-359,-271,-510,-518,350,458,870,825,-661,-271,40,928,469,-161,613,298,-950,-384,639,-752,699,175,25,-535,-548,505,-116,-241,-826,-893,-217,-156,-132,-22,-906,991,-101,-332,-303,697,312,795,-878,134,449,491,-94,86,899,-460,396,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00365() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{-212,-410,-820,356,-589,908,754,-975,597,74,226,-618,842,-786,-297,-577,892,-23,-503,391,-855,-486,-262,-408,866,-829,397,150,-972,-619,529,78,-259,-667,-688,66,-816,-498,59,-207,681,867,-188,220,186,753,-668,343,-222,-369,-625,-838,-7,-96,949,-728,831,760,-27,-611,61,496,170,-622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00366() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{-644,978,608,177,17,-591,31,-17,-89,-454,709,734,-12,-107,-566,-862,-580,-883,-937,-448,510,-222,-963,613,-40,2,-914,-1,-752,6,-146,115,-981,-511,-51,566,320,-588,-447,-346,-708,-562,120,783,-506,399,-68,703,-593,-445,310,780,-112,-869,-844,135,-668,-370,-857,403,-866,768,-652,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00367() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{674,531,-558,305,-504,794,630,-358,606,254,-557,-84,908,-22,-758,-231,638,-200,-186,-289,-585,994,-203,-596,-30,179,-173,-303,-6,-745,320,608,-353,325,521,519,-778,990,-862,-780,240,225,-776,114,-218,101,-757,895,295,20,280,-690,-727,-144,213,-752,-755,410,-677,306,303,-67,311,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00368() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{336,-1000,-525,393,-776,697,-729,69,-224,200,529,-1000,-156,200,902,407,486,243,1000,-456,-262,87,1000,339,-806,615,1000,-97,1000,341,-738,-252,1000,995,115,-1000,88,1000,1000,1000,492,249,735,-418,58,24,248,161,-383,-417,-889,-227,-86,-261,-323,540,-90,-148,579,667,-617,339,-464,341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00369() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{-261,53,-454,698,-271,312,852,-265,845,-116,781,-552,842,-155,-588,352,321,598,-439,431,416,627,-360,496,118,-829,693,-387,950,1000,529,-105,151,-667,-490,752,-289,-498,548,494,1000,-231,-174,-626,412,-55,-192,-422,157,-845,-625,440,483,-169,392,1000,831,817,-489,-302,-570,-282,687,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00370() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{89,446,-661,-80,-577,-55,-445,-622,256,517,563,-716,1000,-1000,626,-1000,808,-158,675,-227,-1000,-1000,-512,-222,154,-905,-337,1000,-1000,-1000,-4,-1000,-1000,-327,-211,59,-1000,-87,258,-755,35,-565,-316,1000,-835,905,-280,250,364,491,-23,-1000,-4,-343,784,-1000,-240,-260,-182,299,373,408,-103,-30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00371() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{-655,588,1000,-409,458,-1000,80,-336,-960,404,1000,-2,463,-67,157,-247,-127,251,16,633,66,161,-540,-771,186,-664,-586,-303,-495,-370,-326,754,-457,89,133,-46,-952,-853,-73,400,-25,494,395,488,-330,-1000,-298,-49,1000,652,-889,-252,-3,665,-394,-316,-177,825,-500,-69,1000,-483,381,-312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00372() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{227,-225,-1000,631,-645,850,1000,-1000,1000,-65,-562,728,534,-123,-1000,-373,764,-166,791,130,-1000,554,40,524,585,634,271,591,-808,-944,950,-607,8,130,360,583,-614,1000,-1000,-1000,-902,-595,562,-64,-101,786,-1000,731,85,-564,675,-490,-1000,528,150,-642,-231,59,-43,54,618,-705,569,-936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00373() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{553,-282,690,471,-592,159,-741,203,-962,764,394,-699,-595,186,831,470,583,-82,555,-621,682,-789,-777,872,184,-601,-569,-88,-300,821,-318,-525,-647,301,-58,-181,157,-347,758,-304,307,-819,-67,233,-919,14,-547,455,643,-1000,-321,-54,-4,-678,515,-834,-402,331,-498,1000,199,-247,-260,461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00374() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{878,431,-733,-179,-1000,507,1000,-484,1000,-244,-1000,872,626,-789,-1000,-1000,53,-753,214,267,-1000,-104,-594,969,-136,594,-400,967,-828,-1000,199,-942,-675,-968,255,220,-450,1000,-1000,-1000,-215,-254,-883,-392,199,1000,-1000,342,183,-863,1000,-1000,302,412,-619,-641,-800,-884,-680,25,-29,-431,-219,224}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00375() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusDays(int):org.joda.time.Period",
            new int[]{-948,-400,-1000,1000,-121,39,11,-939,717,-919,1000,47,487,554,-931,248,1000,1000,-403,238,-305,-674,1000,1000,727,-1000,1000,693,126,-1000,590,-1000,-1000,394,1000,-1000,209,1000,1000,1000,44,-1000,1000,711,-89,460,-114,1000,-1000,-1000,-192,1000,-816,-3,-484,1000,1000,-261,1000,1000,1000,-397,615,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00376() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{753,618,824,49,605,-280,833,497,120,-930,-3,375,-994,-866,55,460,548,517,550,-156,471,-382,-381,958,-338,498,-231,530,-838,-236,-667,281,-511,0,315,-468,-894,-385,-699,674,634,841,234,-99,280,-941,-49,-444,-129,-371,-371,978,-135,971,-479,-147,-225,758,712,-315,-465,-206,10,-719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00377() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{-817,218,-64,877,-615,375,1000,65,510,1000,148,-1000,235,-1000,220,1000,-266,-1000,546,897,302,398,1000,1000,645,-1000,1000,1000,-126,-246,427,-1000,-279,-921,1000,-274,-275,-572,493,428,294,14,-881,-209,-569,-1000,889,-452,-284,606,-622,-1000,995,781,-953,1000,579,444,-1000,-389,-1000,806,-600,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00378() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{988,-408,42,-214,-549,512,608,-637,-988,766,-86,-547,353,249,-605,222,-355,-921,331,-531,-575,273,634,990,-278,-814,853,267,-451,262,-144,-883,608,402,638,-850,-248,545,698,789,555,950,-495,-31,-776,-877,-263,639,-27,287,-844,-306,-1,923,277,76,238,-34,-709,-547,-827,-3,113,56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00379() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{-719,907,-820,806,-927,96,-860,-939,-471,-929,40,-870,771,-943,-411,-993,76,-208,-268,639,644,97,856,-53,-667,294,19,-887,-821,212,377,928,223,-743,922,-734,880,-647,-332,599,-98,-586,-501,874,-169,-522,47,55,772,-397,-856,-845,307,-782,-298,-331,-596,726,165,-988,-699,864,-907,-255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00380() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{-478,-629,535,872,181,750,-887,887,499,-252,123,-740,-63,-609,129,-334,849,-938,291,69,-94,-335,367,596,-408,935,891,-100,454,-790,-946,-121,-555,-237,747,-69,-531,-940,237,-264,-15,-766,44,4,456,-534,717,-442,-725,995,-582,681,860,20,-509,750,599,-92,512,821,-513,784,-267,-929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00382() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{118,172,385,-508,746,590,758,-695,-757,383,-381,410,372,-111,-589,-723,-205,1000,-738,-754,-976,-361,335,-889,2,902,368,-95,-235,-187,563,287,-401,-696,1000,972,612,-1000,20,1000,588,-629,805,-187,175,-268,-425,-961,443,-435,-514,-526,1000,1000,-358,-1000,-285,21,101,-727,-446,707,-106,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00383() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{-360,-226,489,311,-863,1000,54,919,412,857,245,-1000,512,-496,13,-378,420,-1000,194,-716,-628,255,387,1000,-414,493,1000,465,-534,5,-1000,-1000,-127,-219,1000,-588,-247,-45,499,185,604,-515,-362,662,379,-1000,1000,-244,-725,606,-1000,4,1000,824,145,597,109,-30,-466,173,-1000,636,68,-684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00384() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{62,160,-243,767,25,1000,812,-387,110,1000,-402,-626,-660,209,-9,-454,1000,-308,330,742,1000,-396,555,842,865,1000,369,-340,1000,144,-368,-686,-173,-879,798,-728,833,-1000,1000,324,837,-374,303,-1000,-29,-720,1000,-1000,-1000,183,512,362,611,809,-1000,741,338,-421,832,-223,413,1000,585,-830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00385() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{1000,183,-1000,-344,-503,1000,-568,672,-469,1000,-655,-336,138,431,380,445,842,-100,326,142,-45,114,661,968,132,-1000,798,1000,-683,430,229,-973,752,9,475,-1000,159,535,1000,1000,777,466,823,-931,-690,-1000,163,-224,217,119,-768,307,957,1000,489,-544,-908,-389,-875,-1000,17,439,865,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00386() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{-412,-348,42,530,-615,118,406,-1000,-803,-449,482,-1000,461,-606,-766,277,-245,-579,674,70,156,-507,556,652,-369,586,638,614,727,-593,286,498,-263,-304,1000,118,-104,-514,404,513,94,-76,-1000,846,-627,51,191,-170,44,900,-388,-953,-108,221,-461,83,222,444,-82,-389,-779,389,-771,-202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00387() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{367,-375,42,434,-1000,649,406,247,-916,-156,807,-1000,502,523,-235,1000,-541,-1000,315,264,96,273,1000,1000,-106,586,1000,614,-1000,-572,341,-1000,1000,-398,1000,-364,-1000,1000,404,1000,1000,869,-499,517,-1000,-1000,-281,687,-239,-1000,-723,-973,798,1000,-461,-977,-1000,504,-1000,-1000,-1000,984,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00388() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{1000,347,1000,-282,985,634,835,333,123,1000,-1000,1000,-803,1000,292,1000,-121,-550,547,-586,-1000,1000,314,-33,799,-100,-411,1000,138,849,-1000,-1000,699,1000,-853,-1000,920,588,1000,-343,260,773,1000,-1000,437,-891,256,447,-336,1000,-856,898,-950,1000,1000,1000,893,-150,-737,597,359,-1000,-341,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00389() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{1000,147,1000,277,-418,279,1000,1000,1000,633,-731,1000,-1000,-1000,1000,1000,-162,-693,1000,-346,-1000,-1000,-1000,400,67,-1000,-1000,1000,-1000,806,324,11,-79,1000,264,-211,1000,1000,-1000,-208,97,1000,-400,106,1000,652,753,1000,537,760,-1000,1000,713,3,1000,844,827,1000,-1000,-1000,-521,-1000,-819,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00390() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{-450,947,681,91,-811,-733,578,206,1000,-1000,-770,678,-506,1000,1000,939,532,-1000,777,-104,-1000,-350,-136,1000,464,-1000,358,-98,-549,-618,-189,319,-1000,1000,1000,1000,290,585,-465,-843,-676,1000,-142,-1000,1000,1000,172,1000,-159,779,-783,20,-963,-717,1000,-148,886,584,-895,-1000,-1000,-755,-438,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00391() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusHours(int):org.joda.time.Period",
            new int[]{-1000,-872,-1000,1000,-266,1000,-1000,-64,-1000,-1000,971,-1000,892,-23,-919,-635,1000,-931,141,1000,-1000,141,636,-513,228,688,562,-596,1000,-660,92,627,300,-400,-38,1000,-1000,-876,1000,1000,1000,-486,-968,128,-1000,-618,-196,140,-862,-685,942,-521,1000,438,-1000,-496,-113,-1000,-927,-51,65,1000,214,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00392() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{701,68,-28,-746,417,827,-722,143,-681,43,-658,125,-320,938,-602,-180,-402,-243,209,-355,-403,557,-67,111,485,-600,-172,75,670,-551,919,677,281,337,-224,598,922,-286,-25,68,-358,334,-566,605,-202,261,848,853,-103,803,754,103,208,660,357,51,-47,-588,192,225,-753,976,934,-917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00393() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{-1000,658,299,849,507,-714,1000,742,957,-370,-613,122,-228,553,871,1000,1000,-1000,-980,-475,-385,-377,617,-541,-73,1000,-320,-11,1000,-1000,1000,-641,1000,1000,779,-398,561,1000,-1000,-350,201,-786,-507,-1000,-454,599,-622,-527,-1000,802,806,1000,96,-307,-353,845,-847,-344,761,603,-473,16,-789,-729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00394() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{402,-983,-785,-82,602,-307,-506,-113,-406,959,588,-900,472,-795,-263,657,-39,864,186,-697,-994,97,60,795,820,-174,-30,-258,695,-514,57,962,40,-320,-776,362,-479,-491,711,93,-977,763,812,-678,-818,417,-455,320,674,866,-602,-205,-863,236,875,-618,-347,750,-478,-958,-986,-405,326,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00395() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{629,471,-98,31,-71,829,-189,-317,171,500,825,-881,841,570,-512,-509,-249,100,-511,714,-334,962,-43,745,551,-802,-60,317,-652,901,-594,816,-638,-580,-862,-97,-216,295,-692,292,671,734,-860,-384,-226,-320,122,-264,-998,559,491,25,63,-351,-648,-824,778,671,734,-96,-18,702,-893,551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00397() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{402,1000,-136,-630,602,-307,366,-103,920,35,966,-19,-23,969,-33,657,230,538,121,1000,-675,97,60,825,1000,-674,260,231,16,1000,-852,322,40,-938,-530,-397,-196,1000,-1000,93,172,183,-619,-413,245,-190,-455,497,-843,57,1000,-205,-437,-1000,-599,-933,-352,861,950,-247,697,129,-1000,241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00398() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{-476,-149,-218,423,30,-998,493,-1000,910,630,1000,-534,685,-1000,-778,-106,-559,950,235,-252,-63,-583,988,403,581,-119,-527,186,29,16,-1000,-50,744,-1000,-489,845,-1000,912,532,-391,260,1000,363,48,-1000,-923,-751,-1000,-127,-636,-850,232,16,-686,296,-89,-422,828,145,-1000,1000,616,-1000,552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00399() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{-554,-146,-298,1000,927,-641,1000,668,124,-81,-252,1000,-1000,925,580,-421,-144,-317,-1000,72,-923,-728,-549,1000,-437,-857,571,1000,-731,1000,1000,1000,-1000,-235,-711,-1000,50,324,-989,-449,-772,-1000,-566,764,-332,1000,-920,484,675,1000,106,813,1000,-483,264,731,-1000,313,892,392,-1000,-385,-313,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00400() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{-375,809,683,1000,376,460,416,413,1000,-821,1000,1000,-130,1000,438,-1000,265,-157,-855,1000,-1000,-1000,62,1000,460,-530,762,923,-445,992,-277,359,-1000,-669,-251,-887,822,284,-760,-1000,481,-1000,715,-8,-199,237,241,694,-1000,-59,1000,777,181,-666,-1000,614,-130,-266,1000,329,183,591,-1000,61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00401() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{-301,-1000,963,-47,-76,-307,-498,-136,519,35,103,419,998,-840,-514,657,228,102,258,-1000,458,633,676,-745,-762,84,-562,-575,16,-756,-60,-501,818,125,-271,-473,-1000,-655,662,909,570,772,-190,-898,35,-265,-307,-760,339,-877,-1000,-206,-607,-1000,69,159,681,861,-650,190,211,-161,478,241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00402() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{499,808,-284,-1000,531,-300,175,-451,-86,-48,-8,-1000,-515,153,360,1000,-16,145,604,385,-140,236,-344,330,1000,65,-917,-756,348,-1000,-1000,-629,1000,-492,-470,-106,-1000,1000,-643,1000,535,1000,-1000,-780,23,-1000,-1000,-180,-843,72,235,-720,-1000,-1000,-599,-848,14,990,866,-1000,864,221,-50,348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00403() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{-1000,1,-471,-872,-144,885,-354,830,300,-877,-584,1000,-307,1000,1000,-4,907,-1000,-1000,538,-545,135,777,-72,611,650,931,171,-285,1000,346,228,-558,1000,839,89,1000,499,-916,-246,404,-1000,344,-589,-412,861,838,238,-1000,1000,-625,1000,665,-255,-1000,1000,773,-1000,751,1000,-299,976,-415,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00404() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{-483,722,746,-529,409,1000,-1000,94,-1000,204,220,-166,-805,-623,1000,1000,-710,-629,-929,-218,253,1000,1000,14,1000,-1000,-794,-1000,1000,-501,-788,-1000,1000,1000,-115,-649,-1000,944,-1000,917,-748,-1000,-1000,-442,-1000,768,-1000,-950,-1000,271,308,-18,793,355,-1000,-893,1000,-196,260,-1000,-234,-175,802,-883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00405() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{-301,-789,486,68,621,-1000,-211,-381,705,36,9,125,-753,34,114,1000,-283,-280,-452,-206,80,135,659,180,972,379,-1000,-148,68,7,221,244,883,302,-257,-934,537,1000,-527,-219,-395,20,-1000,-155,-728,-550,-1000,-1000,938,119,1000,1000,723,-467,-715,165,-413,872,454,185,970,-33,-129,203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00406() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{-949,204,-792,337,708,-1000,959,-1000,1000,610,-687,871,853,-3,-768,-431,-622,-551,-409,-482,-1000,321,1000,-1000,582,934,625,774,-830,-855,903,701,411,-1000,919,-1000,1000,373,-1000,731,-1000,1000,-186,1000,-1000,-1000,90,-913,1000,-1000,1000,1000,-1000,-71,1000,-403,-1000,-1000,70,-1000,-143,-1000,-1000,92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00407() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMillis(int):org.joda.time.Period",
            new int[]{-808,388,812,1000,208,1000,1000,509,1000,-195,1000,445,-484,349,114,-223,1000,-902,-647,-339,-1000,-1000,-1000,1000,-1000,991,1000,1000,-797,959,1000,1000,-729,-73,946,-171,525,445,-707,-1000,527,-91,1000,1000,1000,797,-321,329,-693,342,1000,853,340,-1000,-260,1000,-1000,-459,1000,1000,-918,879,-1000,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00408() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{727,-614,70,-586,944,-333,-571,-818,-633,298,-502,-559,-121,252,-53,573,-902,285,-848,-852,-370,-641,922,-908,-118,-345,-771,-349,600,-377,-91,728,526,-8,56,361,-602,-831,226,453,-105,48,-587,-808,-948,-129,296,39,188,511,228,-347,449,304,84,645,701,384,208,-293,-328,376,440,-711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00409() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{767,-222,476,930,-77,2,263,-1000,86,-529,-834,315,919,-262,452,406,13,485,-792,-244,699,-19,616,-147,-1000,131,-622,-286,-29,-1000,-650,1000,623,-711,-579,-1000,1000,-1000,1000,-281,-1000,382,163,738,77,-1000,-852,-1000,-392,-226,152,-372,204,885,920,12,-852,360,-1000,-1000,-322,-1000,-8,-675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00410() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{889,-28,922,966,-267,430,-90,-144,542,986,-125,467,-327,637,-838,289,-612,567,90,511,-143,928,68,785,421,-10,-999,-63,953,-663,-363,386,417,193,-717,221,-482,-490,-334,244,-259,0,611,-404,-8,-16,709,-961,572,571,150,-564,274,-489,-502,539,10,855,-630,-227,-411,-549,-346,425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00411() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{994,-705,-765,-668,-173,723,2,9,489,-456,346,-25,59,-650,-687,996,883,444,-887,-72,420,-926,793,-100,-972,-960,-484,680,61,70,138,-737,-100,-126,531,925,358,-738,-528,-692,279,319,-207,-545,-928,246,-1,147,447,218,558,-647,-329,-671,678,-806,295,577,-450,397,482,690,745,904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00412() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{832,576,-258,-955,903,181,408,469,510,648,662,-115,330,-244,524,-381,318,-102,-721,932,37,-943,589,-858,104,605,-953,-338,666,-849,859,-729,-952,-930,-821,-845,552,-66,910,-445,-250,572,716,234,556,-801,-516,369,-135,686,458,-4,-384,-110,910,669,-257,-283,341,-413,-293,540,-58,-847}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00414() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{-896,28,-118,-422,198,-694,-993,-109,377,-864,-926,535,609,-642,411,794,678,914,-923,-144,-152,421,661,829,372,454,-211,-381,-132,-137,200,107,850,-71,-16,-714,-496,-355,953,-676,-821,-972,18,-118,-993,990,-86,-572,559,-11,636,801,-471,-515,-421,-211,-977,14,-520,130,278,-384,-959,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00415() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{-667,303,-656,-141,521,245,53,-970,-481,-520,767,-120,133,-957,909,-407,-8,-339,15,-860,898,387,-568,810,-302,-78,44,840,-758,-287,915,-146,235,427,485,-935,-393,-142,-689,479,-596,974,-741,716,150,-624,520,471,110,217,853,-860,973,-652,-936,673,-915,315,861,335,-434,-303,-4,718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00416() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{364,279,831,-889,676,499,347,-911,959,-235,19,-906,279,318,936,-342,681,-432,-909,950,-136,59,-136,-676,562,-414,-639,-279,-283,-648,-483,-113,797,-491,-367,649,400,978,-618,-212,-311,-28,-792,-633,129,-667,-477,-142,-498,467,146,849,234,-544,8,-313,-294,733,323,4,-594,-391,129,-223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00417() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{524,905,-609,-724,-81,932,-275,-924,252,178,169,-840,-470,175,-566,63,291,234,582,2,-751,14,-815,-19,321,196,-330,-39,-337,-42,764,-618,587,369,-221,533,-961,-637,759,-407,725,523,-520,-500,861,545,721,-749,-923,970,289,-294,-967,519,-156,533,947,-788,768,524,-577,354,-2,212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00418() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{-1000,185,639,493,-100,-918,-1000,188,-308,-232,-1000,184,851,-688,1000,359,-274,1000,-762,421,-60,1000,-101,354,710,1000,357,-1000,-133,238,387,371,1000,402,-586,-1000,-217,-235,797,-879,-660,-1000,-468,582,-738,660,-460,-448,-64,-736,-162,870,-602,-144,-925,57,-1000,-111,328,-503,25,-1000,-808,-577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00419() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{-265,650,-765,-421,1000,188,462,357,489,-620,118,-1000,-535,222,1000,-1000,1000,344,-1000,-72,322,-926,1000,-1000,508,-572,143,-1000,-307,-372,239,-924,899,183,-1000,925,1000,-406,-1000,-692,-697,290,-943,861,1000,-483,-466,147,-1000,484,216,1000,-1000,914,-560,-806,-1000,1000,-1000,-400,-1000,-168,625,-144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00420() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{-1000,315,300,-385,-154,-1000,-1000,-261,-89,108,-480,825,685,-290,572,671,-388,387,277,226,-635,-437,-554,1000,1000,1000,-264,-372,-308,144,-213,1000,1000,-297,308,-1000,-1000,657,1000,-94,-700,-1000,187,675,-1000,1000,425,-162,917,-829,-226,1000,445,-636,-447,-346,-676,-731,380,108,-209,-347,-1000,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00421() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{-136,260,12,187,301,-16,-155,458,-94,24,11,349,685,126,148,-44,198,387,121,195,-139,601,565,276,479,-216,561,-795,221,94,-117,-683,71,-136,-47,216,-1000,155,-125,-795,-190,-283,-401,153,311,275,189,-288,917,-829,64,586,-1000,60,-478,-346,-418,619,-52,-227,-214,180,-147,95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00422() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{952,907,-1000,-1000,448,181,509,731,602,1000,1000,-314,83,158,512,-420,1000,-45,-1000,856,-1000,-1000,-313,-588,267,1000,-1000,-454,-150,-603,1000,-958,-175,-253,-502,-675,-552,565,1000,63,371,450,1000,272,257,599,507,931,54,-509,209,-103,45,703,869,1000,-10,-251,1000,689,-1000,1000,-926,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00423() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMinutes(int):org.joda.time.Period",
            new int[]{-68,-87,561,-631,-299,-368,-244,-442,-110,-64,-292,888,884,95,-178,718,-669,-638,1000,842,-150,-1000,290,945,535,149,-505,-1000,394,-156,-1000,263,-442,-1000,690,-149,-809,1000,132,-180,-671,-498,764,-240,-266,-255,-85,168,1000,-667,-165,801,721,-1000,-428,-247,198,-488,-36,-175,332,-304,-246,-428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00424() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{-453,332,-449,71,154,-1000,383,1000,-751,-1000,-978,-802,-399,643,733,-228,-954,-1000,-563,-1000,-1000,-153,-1000,-1000,12,1000,362,1000,-599,910,-816,1000,-642,-1000,-236,-337,-1000,-592,-567,379,432,822,45,1000,-1000,8,445,1000,-760,1000,-1000,-737,-214,1000,1000,1000,1000,892,610,-54,818,211,400,-811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00425() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{-961,724,360,-161,-274,-880,-546,866,-949,407,435,914,600,-582,712,-541,-455,261,-463,707,-404,619,-152,-55,-549,691,134,95,582,-741,-221,-223,136,829,-66,542,488,-451,-586,613,569,-279,-708,-511,-555,-899,964,47,-135,-92,259,407,466,-507,873,773,165,-73,-141,671,903,-531,214,-405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00426() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{-323,74,379,577,286,-918,366,934,-703,-182,110,254,-219,721,778,664,-228,-754,-869,-973,-511,-351,232,-889,-923,-339,363,871,68,883,-993,540,-280,-765,-690,-452,-858,-454,-128,2,-294,456,960,-559,-399,224,-753,-116,-387,717,-944,-189,201,-82,744,653,773,583,352,-387,694,381,916,178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00427() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{929,-158,139,-803,311,-478,417,534,-727,-54,-434,111,906,179,-548,384,110,-43,632,-204,214,560,-947,-917,596,398,386,-646,-902,537,894,-540,-25,-210,-613,-482,910,-159,-820,184,-851,-102,256,-796,-754,-188,-185,127,-939,299,300,664,697,920,-376,94,862,817,801,402,796,-134,-508,338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00428() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{-671,848,75,-487,-87,533,-254,770,-465,-856,-90,-381,307,-408,658,-717,-781,58,-537,-943,64,42,-590,96,-149,827,-750,489,162,475,-639,546,-131,896,293,-408,-635,359,-5,-779,49,849,63,999,194,123,368,869,-497,979,-712,153,-305,-662,-563,-14,-732,20,253,230,-780,779,-897,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00429() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{972,-886,-326,-190,-31,656,319,-534,548,507,-236,-553,-504,-189,-868,-828,-528,-939,-424,-192,924,-841,-9,603,504,-918,-755,545,-918,-827,180,-356,-585,-477,204,-438,309,-309,-263,-54,655,-899,-995,843,-879,847,-311,882,-528,-642,823,-495,-494,398,-21,-510,-579,210,-742,-767,-964,-740,916,821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00430() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{459,-907,751,-265,-848,-772,672,544,-626,-788,116,38,397,223,-648,-767,87,395,843,-13,942,-617,-222,-319,110,-586,22,758,-189,-333,-950,249,-457,-755,994,-198,207,813,-606,-598,846,49,428,-44,-894,-555,-523,-747,684,734,708,388,-914,215,-974,-289,793,-384,248,566,607,-242,606,-321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00431() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{-738,182,-636,-251,332,219,893,-553,291,226,-174,230,-11,179,786,-849,265,-448,-428,540,909,693,327,-659,-904,580,-979,847,-966,-407,-870,779,-484,396,951,-970,815,805,467,-923,953,-398,832,-941,-275,-355,-340,-180,-894,369,103,-155,625,-243,-608,937,162,226,991,-266,215,-485,-194,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00432() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{424,1000,-402,0,488,0,-592,1000,-1000,0,0,-465,383,-249,23,-1000,-88,-649,342,-1000,-385,0,0,-442,564,1000,-112,57,-225,1000,616,0,-154,368,377,-826,-214,705,-916,-726,-430,564,887,1000,0,-130,478,1000,-891,1000,-983,340,11,0,-673,0,290,610,371,460,-316,575,-1000,-623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00433() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{-1000,-981,378,1000,-483,475,1000,-37,1000,1000,-197,965,197,363,426,231,-642,237,-540,1000,867,1000,1000,19,-1000,-6,-909,893,-343,-652,-1000,1000,80,1000,295,-975,860,236,483,-1000,847,619,893,-1000,469,-366,-1000,-797,-1000,-162,500,630,581,-579,18,579,-1000,718,1000,-792,602,289,-485,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00434() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{124,-425,312,-350,-426,136,-1000,1000,824,-163,-367,427,600,1000,-333,-541,-455,-680,-1000,300,419,259,-646,-55,304,-934,-556,450,-596,-913,-221,-55,-199,867,-283,311,-597,968,-586,801,-643,-279,1000,-15,-943,565,-1000,47,-135,1000,111,-580,174,-590,-1000,-38,101,-77,-530,123,903,87,-190,-146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00435() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{129,-147,1000,239,-712,792,-1000,381,458,-1000,-107,432,753,1000,-390,-987,898,642,-461,805,419,-403,-1000,-123,387,-550,-33,-532,13,556,-482,-1000,-547,638,445,-125,434,-412,-90,-172,27,106,1000,888,-605,837,-1000,-65,264,-734,22,1000,-84,-727,-674,-512,820,1000,-385,-7,-84,5,-389,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00436() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{-1000,-295,117,1000,311,373,786,236,946,1000,-201,1000,-234,712,1000,-393,-862,-568,-1000,328,-151,1000,1000,-380,1000,167,-671,972,689,841,127,1000,204,993,-884,-1000,115,-651,817,-580,48,385,1000,-1000,815,179,-1000,-354,-1000,-173,-656,226,1000,-787,1000,-1000,706,1000,1000,-1000,663,725,-268,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00437() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{46,708,-60,-804,-67,-815,672,544,-626,-1000,-192,-828,-382,426,1000,273,87,-482,-1000,-1000,-963,-617,-1000,-319,131,648,37,435,-95,-333,563,95,-678,-869,-412,872,-1000,813,-503,397,846,1000,-283,928,-576,-555,-85,544,-356,1000,-1000,-907,-461,-487,441,-289,969,-565,-298,820,-959,-242,1000,-681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00438() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{-210,-637,93,-271,276,-164,-778,363,-80,-1000,-288,-27,684,400,434,-722,266,-208,-788,-1000,-301,18,-481,385,946,390,-539,-660,403,89,-167,-914,-29,747,-133,-643,71,-668,158,-171,-36,584,400,1000,393,420,-254,638,-626,-162,-400,441,238,-208,-205,-100,130,389,54,85,-353,494,-852,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00439() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusMonths(int):org.joda.time.Period",
            new int[]{430,795,-57,-507,685,1000,-536,-1000,1000,807,-14,-1000,78,-602,-1000,-1000,-954,13,-205,-1000,255,118,-212,150,112,1000,-1000,-807,-988,1000,-453,-390,-620,303,576,-1000,203,-732,-1000,-1000,944,-1000,-1000,23,848,1000,772,1000,-806,-1000,692,1000,202,18,574,-882,-1000,1000,1000,-1000,-1000,-209,-446,999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00440() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{96,68,-603,574,167,640,-865,627,896,-423,-509,745,-113,-160,-123,-821,-991,-711,-223,-18,-73,-236,137,965,-234,-550,957,-496,84,881,995,-545,-797,753,-603,518,-643,584,477,-464,53,-836,-98,-593,-377,737,-925,-418,-5,912,654,424,-596,-612,-170,107,-36,778,-766,-808,-193,41,168,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00441() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{92,-288,834,-56,-666,1000,-788,-1000,277,-1000,-99,350,-612,-1000,513,61,-1000,-128,-43,524,1000,537,-159,-240,934,895,-1000,-1000,1000,-1000,-239,1000,949,-1000,1000,-336,-502,-1000,-492,-1000,-1000,99,589,-1000,1000,1000,-606,-955,-39,-168,1000,-1000,884,794,-502,-260,1000,1000,-144,1000,-620,1000,-363,-714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00442() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{347,-164,-261,-275,-243,-100,-963,699,-98,340,-135,186,850,803,162,184,-906,-217,450,688,-142,-705,221,814,-748,866,-981,-169,-508,-246,-819,926,-680,146,-896,621,-876,912,-640,853,232,-560,924,-988,408,46,-581,-584,658,-480,-649,97,107,-541,31,-867,-53,-767,103,-790,223,648,453,-348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00443() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{576,-321,-400,-309,-23,17,-338,-580,935,-433,440,-418,725,549,277,86,-242,406,934,3,-632,290,-53,665,38,643,-350,465,338,-953,-595,948,723,387,403,889,722,-824,889,183,-964,857,-355,573,-403,847,-889,-473,719,-821,698,880,-851,658,-517,-721,38,-328,-229,893,-362,995,793,398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00444() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{-138,-753,-788,-200,791,13,20,322,629,-934,-62,271,686,619,-516,-414,-460,-928,-515,-426,-305,-654,-963,258,-226,551,628,-301,-437,497,478,-316,72,328,20,239,192,-560,-109,-426,-515,758,782,292,-807,-557,105,910,627,128,-498,141,-213,927,-32,-294,-759,601,580,-36,-880,517,-370,-886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00445() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{-311,719,788,199,-50,-602,-585,-450,760,553,-243,826,-598,709,394,907,707,-130,-655,-735,715,-906,-94,842,677,-464,-409,-851,990,713,489,248,-534,-474,-790,313,-235,454,22,-364,-216,-769,-183,143,333,-659,60,-254,-487,678,-946,-802,-106,853,-505,-499,-224,-201,-421,372,631,-449,722,807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00446() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{-791,-921,-546,1000,-183,826,0,449,-537,-1000,136,1000,-558,0,0,521,0,-509,0,-377,1000,-986,959,-117,-49,-339,1000,-452,-855,1000,346,-422,-1000,513,-759,1000,278,598,-88,850,-238,-65,-1000,-97,-1000,-725,0,-598,-562,0,1000,870,605,-1000,37,176,0,-119,-1000,-928,411,1000,-880,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00447() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{-229,1000,-261,-954,-243,-1000,122,-454,-98,1000,-266,663,850,803,-145,1000,10,-1000,-859,-575,-288,-555,-418,1000,598,-968,-788,-1000,1000,927,94,516,-695,-703,81,-629,-876,101,-640,-722,95,-560,-559,55,408,572,489,-215,658,876,-1000,-823,-750,1000,-43,-900,94,431,-339,432,-59,648,1000,985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00448() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{83,-727,-534,-160,-116,-1000,-940,-689,1000,845,-1000,-418,-566,1000,741,-814,800,25,788,98,725,-1000,-53,665,714,-648,-350,56,1000,1000,1000,-488,646,-462,-87,973,-1000,661,889,-1000,-572,-1000,-355,1000,533,-73,103,-670,701,-705,-1000,-1000,985,-113,676,-1000,757,-113,-290,-40,1000,-1000,1000,-581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00449() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{492,894,-242,680,-49,-297,-1000,259,419,1000,-137,1000,1000,738,-255,-568,129,-1000,-72,476,1000,-1000,-503,908,-1000,132,-1000,-1000,-838,1000,478,352,-1000,-1000,-1000,1000,-1000,1000,-1000,677,728,-1000,714,-542,1000,-447,-1000,-1000,171,574,-1000,-789,1000,-499,-444,-1000,-1000,-666,786,-323,801,-271,1000,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00450() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{367,101,-906,-379,21,-929,-630,-262,-1000,1000,810,923,967,-326,-1000,-1000,500,-990,-102,-211,-87,-374,-1000,-229,-734,194,482,-1000,-775,1000,-807,-424,-64,-72,-889,-384,268,-106,498,673,507,-542,-15,-137,884,-661,29,-556,-345,-1000,-750,-49,1000,836,279,457,-190,-188,1000,-240,-958,1000,307,-836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00451() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{-602,744,287,986,1000,1000,-36,486,1000,278,-803,901,-336,387,121,-721,-788,-1000,-801,-268,-611,-518,-215,912,535,257,1000,669,965,425,981,-1000,407,819,174,409,-1000,-5,283,-603,-119,540,1000,5,-1000,-253,359,730,1000,-242,533,1000,-1000,621,-297,95,-547,1000,-188,-567,-1000,49,-600,197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00452() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{1000,-852,-190,-608,-758,-298,-1000,-581,460,-250,-326,935,474,518,419,-768,-295,1000,1000,389,309,463,721,124,-411,794,-1000,346,110,-1000,-41,1000,-1000,389,-419,826,722,-383,1000,846,-401,-221,-650,83,551,1000,-1000,-1000,348,-883,838,672,-425,-258,-221,-548,960,-1000,-574,914,646,963,1000,789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00453() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{961,379,-917,-1000,-1000,500,942,-842,-1000,1000,1000,-1000,-145,-668,-1000,1000,1000,-396,-874,-1000,176,1000,-53,665,1000,-859,1000,81,-631,-1000,-1000,-438,720,-462,1000,-1000,-134,661,-1000,-306,-572,1000,979,53,-400,-1000,1000,46,701,1000,1000,-1000,-429,1000,873,1000,962,-113,658,452,-1000,1000,-1000,-479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00454() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{-425,-719,663,792,982,163,-1000,633,1000,-1000,-1000,1000,-1000,324,88,-786,-1000,-130,-655,176,715,-599,823,1000,-808,636,907,154,1000,1000,1000,327,285,1000,-1000,585,841,409,1000,-320,1000,-1000,-416,-664,-1000,901,-852,190,-1000,-1000,628,1000,-928,-711,314,124,1000,-863,-1000,-848,-160,-319,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00455() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusSeconds(int):org.joda.time.Period",
            new int[]{1000,-278,-1000,-1000,-1000,-1000,-741,-1000,-940,1000,941,881,1000,63,-342,-917,755,398,1000,118,-253,621,-555,-736,-359,484,-1000,-462,-1000,-1000,-1000,474,-1000,-362,-341,147,320,-744,649,1000,-746,99,-369,452,1000,-93,-383,-1000,806,-883,-127,-63,925,825,-245,-316,127,-527,826,1000,87,1000,515,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00456() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{-1000,-130,-978,985,-946,983,334,400,581,-85,-122,1000,109,1000,1000,1000,-416,-1000,-830,1000,-41,-673,-1000,-631,-1000,-490,-1000,-929,-268,-1000,1000,-806,1000,1000,1000,1000,476,-1000,-1000,13,1000,-1000,418,1000,855,-734,-163,507,-112,-836,-1000,817,-637,-762,1000,331,-1000,-965,-751,-1000,408,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00457() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{-619,372,-1000,-131,655,-1000,403,-1000,1000,-613,679,-173,-912,186,668,270,36,-51,-1000,327,823,1000,-1000,-93,1000,719,-438,-419,-1000,-896,109,-1000,531,1000,1000,1000,-570,-1000,-1000,820,-324,-846,-1000,-956,-1000,1000,1000,1000,-783,925,1000,321,-400,-513,539,-1000,-223,-76,1000,-1000,-574,-909,-784,-642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00458() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{-305,-523,771,811,68,-58,640,-975,205,-760,-172,-315,549,50,577,-681,521,-263,986,-672,864,-716,436,-779,191,297,-152,-296,-421,-767,-152,-656,766,61,382,615,-742,-897,881,723,-506,-963,684,-179,-893,682,-892,-11,-723,-455,-732,622,181,901,-412,-197,636,609,-243,629,-831,834,-136,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00459() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{-140,541,458,-475,-468,-887,490,-253,-640,-756,51,287,532,-435,-341,-290,-788,805,698,713,-217,-533,527,447,-910,-576,-982,-782,-150,672,-587,859,142,-472,-86,-818,617,153,922,-495,-74,-578,497,455,-247,-92,-737,-991,-224,-983,-292,-973,-73,150,-343,425,372,196,-464,570,795,613,-38,-513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00460() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{-661,-168,-155,896,553,603,772,-924,-13,-805,539,-229,-560,299,312,458,-778,-163,-695,167,-292,-599,-774,-830,-851,955,-775,46,298,-875,99,-968,81,458,976,651,296,-370,-714,200,-794,-287,-606,-89,-278,-638,977,-683,-128,58,948,692,-742,-630,534,294,-740,-359,-23,-404,933,-565,-763,-322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00461() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{702,-343,98,516,262,-691,-930,-945,-599,-449,-343,-387,859,904,679,-580,808,-733,605,943,-979,-266,141,968,-580,-901,682,408,298,450,659,258,245,228,-704,230,553,-229,-155,-822,-287,-497,-20,655,525,634,-305,672,-738,550,801,56,-609,-104,903,665,-615,-235,613,-828,-648,394,-347,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00462() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{358,1000,440,-1000,-758,1000,-179,-975,317,49,1000,178,908,50,577,-1000,-924,1000,-906,648,446,933,1000,-630,589,1000,-184,-151,-421,-366,-1000,1000,702,1000,167,-220,559,-737,881,723,-715,-963,389,642,-110,905,2,18,1000,-984,-399,-1000,-228,351,186,1000,-487,-663,-988,103,-1000,834,597,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00463() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{6,-711,-171,515,738,257,-1000,-1000,598,43,221,-413,179,-661,-543,-932,-660,179,-1000,-269,-714,1000,-596,-1000,635,491,1000,-801,-808,-37,-324,1000,-82,-263,538,-998,608,740,14,-406,-891,-31,651,-1000,40,61,173,893,-414,-716,-593,-350,-651,830,-393,-507,349,-14,-624,534,-402,1000,-617,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00464() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{563,-688,735,840,-395,-694,-941,-689,-788,-1000,-963,288,176,-987,311,-375,311,-444,1000,243,-734,-406,556,1000,-865,-645,999,198,524,-864,1000,-303,616,827,-302,206,372,-797,442,-1000,-142,-268,-590,1000,553,957,-309,717,-1000,538,990,721,-1000,-376,767,880,-430,-704,862,-301,-300,834,124,681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00465() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{642,307,-463,-360,281,283,758,1000,35,217,-476,659,-1000,-233,358,1000,-249,-378,-757,-837,642,-196,-369,-176,702,175,-1000,744,382,-839,-60,-821,-834,-552,242,489,-272,69,-288,1000,213,-196,-903,537,-610,-240,-622,758,-157,332,32,-798,668,110,-331,-239,-525,358,247,-300,1000,-916,-387,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00466() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{-136,612,1000,-102,1000,1000,428,457,371,541,-288,27,-512,-158,1000,77,709,190,-1000,-615,411,261,3,-1000,986,-299,-382,-798,-1000,-1000,88,80,139,-396,-217,784,-434,-831,-7,789,342,940,-296,1000,-798,396,-305,-270,33,-1000,412,-536,788,1000,-324,-105,2,424,-277,-415,-1000,-1000,551,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00467() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{-910,-1000,-879,1000,-264,662,-343,-1000,1000,786,339,-1000,348,590,-257,358,-1000,-1000,-830,1000,-1000,1000,-1000,-1000,-1000,243,376,-334,100,-1000,291,-412,637,31,1000,-511,821,1000,-1000,-1000,-1000,-1000,1000,-1000,443,-983,1000,1000,-385,-971,-158,1000,-1000,-246,288,216,320,-1000,-1000,16,1000,460,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00468() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{-170,-653,-290,814,-536,1000,-100,997,-536,-31,-493,-287,1000,425,-354,43,-63,-586,1000,1000,-1000,-17,-680,-761,-1000,-1000,-1000,-348,916,502,-483,721,85,-1000,394,-475,1000,461,-478,-880,196,-993,1000,-134,-15,-817,-522,92,-321,-1000,-741,-887,1000,410,-138,425,142,196,-1000,-120,1000,864,-982,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00469() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{-742,413,-1000,78,-237,-676,1000,-1000,1000,-665,475,-85,-337,946,801,953,454,-586,-352,1000,622,320,-1000,-1000,-144,-324,-1000,-102,-126,-519,-2,-1000,648,1000,1000,1000,-295,1000,-1000,488,437,-1000,663,-350,133,54,978,818,-385,726,1000,-55,756,-807,717,-354,-368,71,869,-1000,408,-1000,-1000,-808}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00471() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusWeeks(int):org.joda.time.Period",
            new int[]{285,466,489,-1000,-331,-1000,455,643,1000,-1000,323,1000,-491,29,-1000,1000,1000,-1000,-1000,-269,-13,-816,-1000,270,1000,-1000,876,1000,-536,216,-752,400,-1000,466,-860,-938,1000,-1000,30,1000,511,-297,897,1000,-1000,-517,-1000,-1000,1000,860,-114,1000,400,-201,-580,-269,880,-52,-902,1000,586,1000,934,61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00472() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{491,310,162,193,1000,-494,-1000,1000,-125,384,-330,-1000,-862,1000,379,34,-1000,-293,-516,-87,-565,669,874,-1000,978,-1000,1000,-1000,1000,-1000,316,407,250,510,-826,1000,-1000,-1000,-706,-720,-920,11,309,-1000,676,-187,-212,-1000,54,-60,1000,392,351,1000,488,658,774,619,-405,563,491,-606,1000,136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00473() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{914,988,-153,-430,-337,1000,504,1000,-601,-366,226,-475,294,-59,-371,-764,-1000,53,-516,-415,-690,811,1000,-500,-163,-1000,864,-949,-466,-1000,1000,-1000,-300,168,1000,400,-67,-639,20,939,1000,-1000,545,-1000,-689,-197,-683,-799,-944,-949,960,-288,-1000,1000,1000,-69,-271,1000,631,-293,-393,-390,358,711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00474() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{-938,-951,-813,-883,-107,-343,397,-769,-177,259,885,967,-475,-899,870,-223,391,-786,-479,-633,355,501,931,605,-20,-556,-772,-650,-902,119,256,-808,-738,686,832,513,19,290,-946,-87,-426,389,297,76,831,-64,-368,-577,-56,-446,234,-442,585,-143,-1000,402,893,-574,-910,200,-240,588,405,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00475() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{-437,-6,-530,-177,892,577,-223,309,-64,-465,999,-670,-240,-91,-545,-116,-419,164,190,-990,-661,53,149,-887,528,677,571,265,-458,-848,-466,-298,-239,-236,112,-859,353,-731,-916,177,-660,71,-689,-942,-142,-573,-13,400,-238,-471,779,-923,-314,352,563,380,-449,474,942,868,105,741,-988,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00476() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{399,879,738,-298,818,-589,149,696,-91,913,-257,-840,-508,-29,-600,-997,-896,-296,983,-424,-573,264,-234,-688,945,-711,903,-588,751,372,457,-547,314,-977,-896,-865,831,-566,51,65,339,-621,944,505,126,-953,552,276,-780,263,902,641,716,-376,195,951,516,375,-712,35,-269,-475,853,-96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00477() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{-671,340,188,-418,-97,-88,594,477,222,-890,-857,119,-787,940,307,-897,-271,-181,234,993,-648,-503,947,-895,427,-106,-907,-550,-513,-558,530,728,-787,-45,-349,-388,790,-163,776,293,-862,539,142,-807,-162,544,-903,-900,803,903,-78,-423,-258,360,-599,450,279,431,-285,391,-761,-533,-454,-794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00478() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{-951,-303,364,274,469,492,-199,794,-70,727,-279,-30,663,968,-825,-929,-540,474,-256,444,-526,476,951,962,-275,81,432,-848,169,272,-717,291,-900,938,-320,152,-581,-332,-788,-286,-107,-910,660,215,901,-20,943,165,-656,40,839,-406,453,-692,-254,-258,983,462,-781,616,996,-42,-680,428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00479() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{363,-1000,816,1000,0,-487,-421,-434,847,228,19,504,-855,-885,0,1000,330,-1000,-88,-464,1000,-843,692,725,99,6,-966,-673,404,-262,15,-876,22,102,135,865,-882,9,0,-378,-472,-567,1000,17,-245,497,-702,-158,-1000,970,583,-707,-177,-660,-1000,0,236,-552,0,830,-93,448,-466,644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00481() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{-1000,427,346,-533,-111,-343,1000,555,-177,259,-902,-417,-20,1000,-577,-967,-653,-786,-479,441,-307,451,784,-733,717,-1000,28,-830,-649,-528,793,-808,-522,-592,-439,428,502,343,305,-415,-575,-89,166,-239,831,-1000,-701,-696,620,198,1000,966,512,-143,-73,1000,893,700,-400,-393,-542,-1000,-63,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00482() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{150,458,-419,-1000,428,1000,1000,-5,-390,326,196,-616,-1000,473,-398,-1000,-978,-626,389,-832,-1000,110,1000,-167,715,-700,263,354,1000,631,378,-1000,-355,-823,60,-1000,697,614,-1000,813,-579,-512,510,-259,830,-1000,-118,-1000,1000,-1000,881,1000,28,318,-461,778,-56,109,-851,-1000,-1000,112,849,-648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00483() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{-1000,-637,510,191,-905,536,953,-5,461,-1000,-655,261,-11,1000,673,377,509,-91,-666,1000,1000,-690,810,-180,403,465,-1000,526,-874,-1000,70,1000,-1000,-87,-123,280,-555,-540,1000,-232,-1000,675,-1000,-1000,214,1000,-1000,-727,1000,1000,843,-925,-1000,510,-538,-61,-287,25,114,657,-443,-755,-1000,-856}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00484() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{-1000,358,-444,-164,-994,319,1000,478,363,-1000,-790,202,-654,1000,1000,-624,460,588,-108,1000,-490,-1000,1000,-957,-300,585,-1000,-261,-1000,-1000,560,1000,-1000,730,164,-95,748,-11,1000,672,-1000,1000,-1000,-1000,-639,1000,-1000,174,1000,1000,560,-151,-904,1000,-706,25,-448,663,109,586,-1000,-824,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00485() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{303,-983,1000,589,443,-58,1,-490,503,693,-1000,728,444,-326,132,-203,-305,-458,-1000,737,1000,184,-413,-778,444,-380,477,320,594,283,729,441,423,-451,-787,-1000,-216,-1000,1000,-690,347,-1000,911,1000,-198,-182,-631,218,-670,1000,-213,-354,354,-817,-655,-336,428,-945,974,281,108,-892,304,-551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00486() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{210,-238,885,82,998,-673,34,-98,56,1000,-1000,253,437,-326,-441,-1000,-1000,-945,-869,345,85,1000,-432,-1000,663,-1000,1000,-628,751,614,1000,-105,757,-805,-1000,-896,1000,-382,215,-818,644,-1000,1000,1000,234,-1000,-421,240,-936,439,-103,970,1000,-1000,-329,406,1000,-473,614,-454,38,-1000,960,108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00487() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "plusYears(int):org.joda.time.Period",
            new int[]{979,81,317,-37,1000,73,815,-281,-251,1000,-403,-874,-308,1000,-986,-1000,-1000,-1000,129,-1000,-114,-66,280,-28,975,-573,1000,1000,1000,1000,489,-1000,988,-401,-381,-1000,254,-414,-1000,319,744,78,335,1000,1000,-1000,513,-53,262,-1000,1000,1000,972,-955,375,366,256,-591,-405,-947,84,108,1000,-381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00488() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "seconds(int):org.joda.time.Period",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00489() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{644,-438,-274,662,229,580,-396,789,1000,-1000,-1000,-1000,-20,-1000,-721,722,1000,1000,-1000,-1000,797,-11,610,1000,-735,913,341,461,-1000,124,614,-228,-915,-1000,1000,-165,635,257,-122,1000,962,-641,-1000,1000,-650,298,383,-518,-504,-869,-529,193,1000,-648,354,-297,626,269,36,928,400,9,323,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00490() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{31,372,977,-430,198,-1000,1000,973,-590,663,-770,-265,872,827,-477,1000,-20,839,-598,684,421,-1000,1000,-167,-691,-663,507,323,-853,-815,-816,121,-286,-780,-337,223,247,968,3,-1000,-866,229,1000,-208,837,-1000,1000,-35,380,854,1000,-570,-1000,-197,-768,-513,512,1000,564,-520,475,-836,-311,-780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00491() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{-14,436,446,-273,-32,-547,-51,-60,-724,-603,-256,-202,616,-459,-282,253,278,-893,768,407,-83,-827,878,506,-803,-406,995,724,44,-720,-333,-311,278,98,894,-690,819,451,-598,-116,288,132,-359,-324,-139,-535,-777,222,-490,913,206,463,406,364,671,-554,-826,131,374,865,-50,297,-97,952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00492() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{-272,-447,728,371,-622,863,-173,651,870,-584,-869,455,754,184,952,498,754,103,-238,290,423,-577,228,-950,286,66,-780,-895,596,-13,-312,895,915,-518,757,284,-914,126,-848,-682,847,-385,171,795,-654,174,-186,-971,-917,358,-607,76,-511,139,-27,339,-796,932,319,948,-557,-113,713,605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00493() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{-713,687,652,576,913,-230,669,477,468,147,-140,-984,663,25,-547,500,683,-366,-296,205,953,-897,735,759,219,350,932,115,-687,-74,-496,-305,-977,-262,-726,-116,-154,299,-469,-526,-886,751,889,-108,-65,-723,467,77,749,734,443,-282,-142,-686,41,-730,281,908,367,574,481,442,-864,284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00494() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{103,-74,328,-677,695,-797,40,-430,-388,-526,453,-318,748,-460,653,-523,586,405,346,712,153,-590,-52,-803,906,367,439,-805,-677,-691,469,-148,-977,420,706,992,605,-538,155,35,99,599,298,-992,876,888,285,702,-778,919,119,-171,354,80,824,731,-387,777,891,-328,810,-488,970,-984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00495() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{347,-197,210,-722,-198,-899,770,152,-681,-2,-8,459,556,261,-444,691,-724,766,-64,-295,-148,-335,-183,-706,-853,-458,-969,839,191,-197,151,417,85,-520,330,325,-34,398,-220,-601,-471,-775,315,-258,607,-694,860,-503,-400,-624,464,199,-800,277,-559,837,-267,596,-443,-699,-256,-204,870,-847}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00496() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{-30,865,-247,-254,710,-264,-61,666,-171,-155,-6,-816,-937,-952,541,-66,153,828,750,-586,-577,223,152,48,-138,-745,672,-464,-488,731,906,-985,18,-726,167,150,-435,188,-442,682,118,316,-547,216,-142,467,-860,-957,32,164,-878,656,-966,471,-434,-702,-506,-501,-248,-674,363,-189,-988,-494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00497() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{-436,-610,-386,432,-366,109,330,-824,975,-295,-1,20,813,-861,-694,-338,780,931,222,-388,153,-438,38,-293,-793,365,825,613,-98,449,-772,-14,-854,162,33,582,-707,344,-322,998,-829,-787,984,967,960,-920,-222,-829,-474,-668,-204,400,-543,76,275,511,964,721,986,177,-958,-653,109,-336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00498() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{889,206,-700,990,384,-53,-387,740,-325,198,-526,542,363,-920,-143,736,613,873,-15,134,-370,-408,-570,933,-631,547,-687,821,-919,90,450,97,-609,-291,-230,252,915,388,87,-151,166,701,415,-216,443,-461,391,-951,-260,-482,-883,-28,-695,152,-284,553,-573,704,284,-951,-732,-769,-480,178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00499() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{-891,823,-685,-519,257,269,845,253,41,473,-704,209,797,-189,-749,579,591,-665,-4,678,-487,222,-500,174,807,-787,-307,-751,879,883,-215,-367,239,570,869,-894,841,539,-220,475,-72,501,-683,-751,522,37,-694,310,-26,649,694,678,633,-841,912,-804,598,31,-937,378,-213,-201,141,-983}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00500() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{-14,325,25,-273,299,-1000,1000,137,-213,324,-665,226,1000,27,509,155,803,608,86,1000,-616,-827,127,-1000,1000,-876,605,-1000,-362,-676,-107,-311,-179,665,1000,479,1000,229,-598,-196,399,132,108,-1000,1000,513,-777,1000,-886,1000,1000,-124,332,-213,1000,-200,217,493,743,-668,834,-689,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00501() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{-1000,-1000,-519,-1000,-422,-57,741,-874,49,21,-1000,955,-1000,1000,1000,-876,-759,707,10,1000,-1000,-1000,-615,-695,-1000,-247,72,-815,-56,-1000,-1000,-405,842,155,-247,179,-946,611,1000,-1000,687,-570,676,400,601,-666,590,810,-1000,952,583,-841,-1000,1000,-617,-307,-344,-71,-140,-320,421,-1000,664,-572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00502() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{-790,1000,-1000,455,347,1000,241,164,167,324,-526,34,452,-486,-1000,673,613,-1000,11,-506,-165,955,-570,1000,486,-351,-996,821,1000,1000,450,-397,292,430,217,-1000,-8,527,-954,1000,-491,61,-1000,-137,-568,-461,-1000,-422,609,-551,-883,1000,851,-1000,753,553,-573,-208,-1000,1000,-953,201,-480,177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00503() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{575,255,-509,-267,257,714,-1000,1000,-1000,59,1000,2,1000,-312,303,-894,591,-944,-1000,648,-1000,-708,421,326,83,1000,-84,381,170,-392,592,-1000,631,-91,-828,7,-89,-1000,-440,475,891,27,-683,657,-351,-374,795,-1000,401,1000,1000,-321,633,-841,-1000,1000,510,1000,-106,-25,1000,-587,1000,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00504() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toPeriod():org.joda.time.Period",
            new int[]{-370,681,391,1000,-362,1000,-406,767,1000,0,-668,-1000,1000,-991,-1000,0,1000,0,494,-1000,1000,368,718,518,1000,-178,-926,314,745,1000,91,0,-58,-257,1000,-1000,-343,608,-1000,1000,-987,-558,-787,624,-453,309,-1000,-1000,126,-1000,-1000,1000,1000,-910,1000,1000,0,904,398,1000,-1000,0,-626,623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00505() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{-727,222,797,-167,606,163,-113,508,-201,473,727,-559,-372,-339,-192,-358,-277,815,-835,-659,-845,-808,917,-100,214,119,-44,-567,-683,-204,354,-777,548,499,-552,976,994,-851,987,-558,734,238,-814,-988,113,730,-894,525,751,-347,-616,-315,647,903,-997,-718,-327,-31,534,-323,-203,225,84,-56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00506() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{281,218,-704,-157,266,376,-369,-588,620,389,117,201,278,-609,347,-16,961,538,-963,-901,18,346,-532,-132,-252,660,471,792,-369,-377,-404,914,558,-87,426,-352,701,-991,460,908,-546,-743,-550,-581,573,-888,634,-293,528,-902,916,484,928,741,723,876,880,-4,3,250,107,588,-380,292}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00508() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{-695,-959,-9,371,840,-301,368,-176,432,-653,-224,-4,-992,-882,398,502,519,-665,985,68,493,-833,-424,753,-78,370,-419,860,-171,-633,712,-220,433,-177,-355,-33,-533,325,-344,736,923,833,44,-364,254,-850,613,-203,902,818,176,-187,-98,578,660,489,-719,802,165,109,973,152,-899,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00509() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{75,146,-590,379,373,256,-207,425,-680,-279,-154,103,870,335,-929,395,-57,-868,818,-726,-356,-874,413,-982,-135,25,943,-22,-629,-799,-526,596,-923,85,846,375,-139,666,-391,-945,-46,757,-858,-743,738,-679,552,-470,-432,298,573,-101,195,-901,-662,772,-753,197,-354,384,-693,-877,-859,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00510() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{-845,-445,-311,102,-70,176,139,867,130,493,-314,619,676,-71,-958,818,-444,141,-214,-386,-7,726,419,398,516,-695,858,-934,-167,261,-538,345,-12,363,-16,-591,-65,-40,303,570,340,-542,271,-172,-772,394,536,766,886,583,-141,-597,-650,-468,-938,-607,750,-407,-480,298,-198,174,902,485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00511() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{245,10,248,-886,-541,276,909,-445,299,-303,-472,89,522,485,683,-739,-587,-733,649,221,-28,569,-673,-204,738,-643,228,213,-399,-850,-482,-490,-434,-863,-478,-391,63,-797,-501,-477,-913,-195,985,-760,-746,222,557,-552,645,-115,-534,-542,-747,-826,-716,431,-252,410,-572,-661,-659,553,225,948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00512() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{652,-942,900,-473,554,175,372,-115,-37,-292,-645,-230,-775,-344,-367,-586,-458,523,-661,164,-982,564,164,-198,-542,609,815,621,-570,-879,846,415,640,-951,-609,-533,368,-504,739,-844,358,-729,-839,-171,380,-20,978,-754,457,-526,439,60,-303,189,-318,-415,-419,-804,-241,-615,-406,-370,-476,115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00513() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{704,-840,487,371,816,-301,227,183,-513,-487,-753,-158,-166,-110,398,-310,-498,-85,-89,-344,-1000,-48,453,-886,-636,626,-419,606,-171,-1000,477,832,-6,-892,-17,-270,270,-37,466,-1000,326,-199,-1000,-691,254,-495,1000,-1000,154,-317,840,-11,-167,-441,660,125,-946,-666,-489,-347,-891,-984,-1000,-13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00514() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{-889,720,-503,-234,-212,-95,68,999,113,745,674,-135,138,-65,664,-69,648,290,278,-186,244,-1000,1000,562,595,-1000,-1000,-1000,-351,819,-385,-1000,62,85,169,1000,-139,-640,684,370,127,1000,-545,-1000,275,-679,-1000,1000,165,-68,-1000,179,436,1000,-646,772,600,227,1000,730,-271,1000,180,16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00515() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{125,497,-404,772,745,890,-409,236,-497,-488,-719,-162,-790,-409,-234,1000,-613,448,-625,440,-1000,327,-132,1000,-998,659,928,-749,-1000,-375,-288,1000,531,-956,-432,-424,1000,1000,-42,-236,-902,110,272,-494,-760,-706,46,-228,1000,521,-265,-536,407,-656,1000,1000,698,1000,555,-67,230,146,-991,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00516() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{878,-1000,-432,-215,1000,891,687,-658,600,-174,-766,659,-356,297,-716,-810,419,-27,471,-351,0,65,-651,-252,-607,-399,580,1000,58,-559,-518,741,-265,-744,877,-823,89,-1000,-4,160,865,-1000,804,-321,735,-1000,-538,-494,182,-146,294,800,-116,477,248,15,-966,-575,576,1000,-135,122,52,533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00517() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{390,293,853,655,-493,426,-1000,350,-589,140,1000,-1000,147,859,-1000,-804,1000,400,-745,-450,-280,-703,-501,-227,1000,1000,157,-522,-655,274,-238,-1000,623,940,726,1000,-826,169,241,-80,933,-125,-1000,1000,-710,181,-23,-919,-536,-279,-418,-539,1000,1000,-950,11,769,-235,-1000,-1000,-874,-840,-208,642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00518() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{-784,-368,-935,-320,291,1000,697,-799,765,-219,496,1000,-59,785,-32,-223,-306,-1000,1000,1000,1000,274,-979,1000,414,-1000,-278,1000,631,1000,-471,-1000,-663,209,172,-245,-537,-41,-527,1000,1000,204,1000,522,450,-173,-889,922,-65,591,-1000,656,-685,1000,-664,-139,802,-120,1000,1000,2,549,185,406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00519() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{393,96,626,190,-24,160,100,-1000,1000,-1000,1000,-24,-265,-483,1000,398,523,-412,1000,-43,-438,445,-956,-343,-1,-1000,603,1000,-276,-1000,435,181,-889,959,1000,969,-269,-400,310,960,1000,-721,-545,-1000,-79,293,-341,-294,-109,0,-1000,-206,420,818,553,288,-1000,-400,-202,1000,1000,-848,556,74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00520() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Days", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDays():org.joda.time.Days",
            new int[]{-747,87,640,373,-425,34,-10,996,64,727,-480,-1000,281,-758,321,-87,-248,-606,800,20,180,-586,-702,237,440,759,-361,-1000,-491,1000,-98,-1000,908,-371,-1000,-703,638,190,-175,115,1000,-144,-1000,1000,874,751,415,-19,-23,499,-74,1000,-198,1000,-216,-1000,303,168,-718,-613,-630,-127,28,422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00521() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{432,354,74,-682,357,179,1000,185,-996,-896,85,-1000,86,-1000,98,420,136,1000,-785,698,131,363,-808,-284,884,-1000,-271,-866,443,-594,-284,-794,1000,-553,226,-515,-201,-1000,-9,151,1000,125,871,833,511,713,463,1000,-389,262,-272,-1000,-341,-793,1000,80,234,-1000,-108,-312,-803,144,-457,841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00522() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{981,-200,-649,-616,339,-404,474,32,433,-1000,-562,210,995,946,371,-795,-366,-1000,-414,-22,-90,730,1000,875,-660,679,178,-294,1000,-539,-469,-822,-1000,-620,-181,1000,1000,-252,-370,1000,443,417,-1000,691,-1000,77,-64,-1000,-1000,259,-865,-66,311,816,-20,871,-1000,316,-588,749,-481,-1000,-1000,-151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00524() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{776,-533,111,133,-492,119,106,267,846,-955,907,-641,-581,-194,-110,913,604,-902,-491,788,799,-783,-992,565,369,-992,-852,386,-584,52,-722,-605,990,-213,7,431,209,-96,945,-982,838,919,-496,810,-685,97,-429,681,-19,74,59,388,288,-549,742,391,-825,406,622,785,-575,-72,113,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00525() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{-126,678,751,-580,-397,662,849,282,-120,-70,542,-821,553,-679,988,853,-166,915,184,540,-25,302,-239,-399,194,-962,-558,535,416,-280,427,-326,858,20,-157,-631,-683,-322,-354,-155,120,12,867,73,153,781,-71,966,94,391,-72,-971,-931,391,217,155,292,-48,426,-277,-61,578,114,329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00526() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{-488,-257,675,577,-758,256,-180,-831,695,254,977,210,698,480,305,142,-74,-819,650,-19,51,374,753,656,-764,518,-720,166,924,-322,832,971,-632,174,-990,623,-834,377,-192,-51,-826,-140,-873,-712,-406,396,-935,265,810,-537,759,361,-449,839,-955,157,387,643,-44,533,192,746,173,-659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00527() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{244,866,-41,107,911,-158,-214,-828,-492,-383,-70,353,827,260,-388,-336,107,-997,-61,965,-262,713,842,466,-103,-519,504,-36,-540,-917,331,-597,-936,-60,-539,893,963,-76,-38,670,476,-147,-540,665,-776,-172,-281,-706,-865,-262,-122,-87,202,156,-491,954,408,651,-574,186,855,-905,-736,775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00528() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{-692,-281,-845,67,224,722,-635,-178,-956,-691,-52,-989,823,809,72,975,-393,-629,-7,875,155,-724,223,-605,906,-765,815,-144,192,46,-528,-55,-823,-138,84,839,-201,-954,477,815,134,74,-271,-360,-989,-721,-785,-1,469,356,-296,994,684,518,-711,390,-614,919,-281,653,442,-437,-230,-910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00529() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{308,-720,-292,431,319,-434,611,-969,-557,-926,325,-438,32,-613,182,-476,357,322,-736,206,273,461,-59,821,223,-110,-310,-529,964,-849,-184,303,-78,-644,-442,790,-145,-939,301,387,636,21,-867,13,-586,299,-172,762,121,-720,474,58,394,-854,448,50,304,-910,-808,483,-992,126,-643,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00530() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{392,-243,-743,815,-876,936,310,-230,123,767,151,-991,-617,-762,-544,551,362,886,149,862,-330,756,645,876,676,267,33,763,-158,686,-268,969,64,-228,-1,602,130,875,-71,591,-492,-743,-918,211,-876,-243,731,-466,-496,-977,854,-818,-234,511,-527,340,240,-818,-287,403,259,-566,-963,878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00531() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{654,824,-997,525,189,732,-689,408,448,-653,-865,632,658,-919,767,984,-558,-768,276,86,787,-606,135,-453,611,-290,-373,197,-478,973,-657,244,877,-540,986,748,70,-921,930,128,-944,934,-528,637,-403,-283,-407,-344,-582,-521,-840,-490,-445,282,985,-946,512,-10,-75,752,-446,25,-705,142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00532() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{-567,317,269,677,182,837,-349,-268,181,-837,-566,-720,700,-693,-112,-810,-711,682,585,391,17,-449,718,-728,374,520,-649,-417,-139,768,-163,-172,-216,515,512,705,-438,15,21,481,-5,-214,129,358,359,-827,-270,881,-65,443,893,199,-347,-427,-722,-364,-118,634,994,-600,97,274,98,-480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00533() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{-476,104,-213,451,143,-656,-507,-238,146,723,177,-1000,-1000,-678,-267,-680,-208,1000,-957,658,305,-295,-460,821,-216,-419,1000,19,277,537,-221,-19,648,-33,1000,-1000,1000,-454,-720,933,334,162,1000,385,373,107,1000,609,-1000,-720,149,161,556,-1000,400,-4,-1000,-914,316,-1000,-996,-606,-643,746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00534() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{812,169,1000,-166,689,-1000,892,-1000,96,460,664,-139,642,379,-1000,-1000,775,-396,-804,1000,-416,1000,280,721,-1000,-342,-66,372,-387,-1000,734,-1000,-675,1000,-1000,42,1000,985,-893,-338,-1000,46,-679,33,114,-652,501,-229,-985,-33,470,24,543,-622,34,1000,-59,615,-172,23,664,-1000,-342,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00535() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{939,-172,702,-1000,-661,134,1000,261,-642,-559,-843,-1000,-559,-1000,-600,567,271,1000,-619,-473,601,1000,-1000,-199,228,-1000,647,886,814,-1000,-1000,-319,1000,1000,-1000,-59,136,-995,-301,471,1000,113,591,1000,-145,527,-659,1000,-855,350,-411,410,-1000,119,909,49,-595,-29,744,-1000,-1000,-516,-1000,-375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00536() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Duration", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardDuration():org.joda.time.Duration",
            new int[]{-620,773,453,-143,706,252,-164,-1000,288,312,202,960,-120,-20,-740,-705,1000,480,331,635,-1000,1000,-1000,-396,1000,-577,1000,716,-1000,580,696,982,1000,-495,-1000,-685,-203,952,19,-394,1000,-1000,1000,-188,999,734,-1000,762,-1000,-737,137,350,1000,677,1000,366,457,747,-1000,-1000,-84,763,-242,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00537() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{45,-64,694,1000,138,-827,-1000,237,24,225,269,1000,-48,1000,858,-303,-629,-911,782,1000,-1000,895,385,-248,-1000,-1000,831,301,1000,-69,550,117,180,851,-1000,-1000,-40,-44,-1000,-1000,-932,-824,-1000,-640,-866,961,1000,-1000,1000,-359,737,-408,272,1000,59,9,1000,1000,1000,-387,-1000,-830,447,-767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00538() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{-297,-618,-506,905,-612,537,-741,94,65,524,-283,-3,-913,-41,-268,170,-630,353,-51,586,-305,-155,-145,1000,-177,473,-684,-936,-288,-845,-264,337,-1000,-409,-234,1000,-154,205,-650,344,389,-198,994,-923,-883,968,645,-75,-1000,-734,199,142,-321,1000,74,-628,1000,672,479,-262,1000,-226,-451,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00539() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{-279,-867,-926,-392,513,267,-778,302,-866,-878,906,332,-781,454,-373,53,-31,445,430,386,733,-2,592,-669,-980,357,-237,588,295,-112,-695,-741,325,-559,482,-993,763,349,-386,-561,160,-153,-630,-10,-326,-577,923,-878,-655,477,-88,-738,-971,555,-193,588,-196,343,-387,491,993,683,668,360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00540() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{-11,37,-866,-584,-775,304,-130,857,-101,467,-629,-399,862,-877,-120,523,255,-185,-590,854,-167,278,438,-61,-282,-567,-229,-15,899,-569,-709,257,-838,575,-849,487,89,-362,-514,-261,251,779,-265,424,791,-986,262,-280,219,-937,-879,266,-559,-738,-462,661,-231,-825,952,56,5,596,322,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00541() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{828,-111,-303,-815,-796,843,263,388,986,508,285,319,-106,-204,-598,-164,121,-967,585,569,-492,-313,-334,601,393,439,199,530,20,-760,-566,212,-202,533,89,809,-355,502,65,211,117,879,300,376,-861,-361,-458,689,-265,715,-18,50,374,789,-599,70,284,-872,-200,501,407,498,-736,-193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00542() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{549,-763,-697,667,708,717,-199,106,-424,645,306,946,-236,895,-565,-684,-559,918,-127,64,-251,159,619,113,622,-650,964,990,452,-898,-10,122,-595,762,-688,-211,157,-832,-757,-903,-710,-383,-390,-207,-319,167,21,-699,828,-827,570,-30,249,901,659,208,842,969,927,877,418,432,-905,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00543() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{471,370,964,77,-269,-14,768,226,-12,457,612,-588,-691,1,825,-505,834,994,-279,996,65,-663,463,631,-187,-253,545,-665,-258,146,-295,-386,899,-87,-969,-569,470,411,-533,-763,590,-105,-25,-720,-968,669,195,-168,-736,221,-391,-380,546,-85,926,-604,-521,816,-293,335,744,203,-928,736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00544() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{654,432,-241,-623,549,-49,-241,-226,100,454,300,612,-848,455,34,-360,813,-130,954,-553,276,616,-232,-306,-997,644,-730,-355,823,-597,-437,-906,786,-746,915,-809,-395,38,502,-940,2,667,302,-394,344,431,-958,457,-423,-522,-792,-417,-347,863,886,-167,-590,-493,-526,336,-830,416,141,-456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00545() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{-92,427,-693,991,234,-719,-687,685,-721,666,-548,-820,720,-233,615,-179,967,698,-632,863,-477,2,82,762,-313,-701,-146,-974,-709,-274,284,458,-452,-594,47,-54,-1,-177,730,-595,-918,-330,-632,-66,871,-470,769,845,-783,-438,836,771,639,-743,837,796,-867,-702,-163,-765,-654,553,381,535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00546() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{-567,336,894,586,-612,-802,-741,-309,600,705,-458,410,-14,-41,-37,551,-842,-81,839,-317,-220,-17,457,735,509,746,622,459,757,369,-26,-137,-141,-428,50,49,-817,-99,581,344,-781,-364,510,81,-242,-536,-172,932,882,-122,478,-670,-321,148,316,164,365,672,367,-320,-204,539,490,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00547() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{-1000,1000,422,-392,-116,-540,-986,-1000,82,-297,-1000,-309,407,358,-170,474,224,-47,799,-577,516,-59,1000,799,789,-506,437,600,1000,-90,787,553,355,-1000,492,-1000,-198,427,581,-561,-1000,-487,-821,513,-39,-82,420,358,881,-283,-540,-1000,116,-170,304,588,487,1000,821,9,-1000,422,1000,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00548() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{331,316,-303,-815,-660,-57,965,-144,8,473,-998,319,977,-492,574,-164,73,349,-752,-608,-532,-49,-259,242,1000,75,776,134,20,-398,739,1000,396,816,350,1000,-535,213,-344,634,117,879,-53,-460,757,-361,-474,1000,988,715,-487,861,-15,-499,221,-704,284,337,-200,-939,-621,-738,123,-193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00549() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{-1000,69,459,192,-335,-1000,-1000,-769,-1000,-1000,435,-712,428,1000,377,-100,-1000,1000,-34,-719,-423,-750,627,423,863,-14,-391,410,-1000,783,130,-192,-964,-896,1000,690,88,0,0,1000,337,-472,460,-38,731,416,334,1000,-164,1000,165,972,65,404,-821,-35,-161,906,-79,451,-976,-1000,372,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00550() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{-849,984,167,-805,999,-1000,-523,-1000,-400,993,-318,-400,134,867,1000,-6,-124,400,367,-1000,-93,715,998,112,1000,-749,-400,685,-47,1000,-112,175,-203,-639,894,863,-921,-708,-263,238,-1000,916,133,-439,1000,-400,423,-436,558,-79,-995,91,21,-400,-60,-814,29,1000,1000,343,-1000,-32,852,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00551() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{-684,699,-198,-867,-132,-29,-1000,-1000,287,-182,-270,937,-1000,-265,-53,-6,-1000,-209,-183,903,1000,-143,998,262,-311,-383,458,1000,-47,-1000,830,175,-180,-1000,808,-250,-978,-42,-415,238,-614,787,-1000,1000,-116,1000,105,-436,272,161,-1000,-257,339,-645,559,-154,206,742,819,-9,-1000,-1000,265,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00552() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Hours", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardHours():org.joda.time.Hours",
            new int[]{1000,-172,876,209,-439,1000,1000,349,1000,-471,-827,-201,-1000,608,-1000,-1000,-977,-1000,1000,229,299,-538,-802,753,-749,513,1000,121,1000,-1000,1000,341,652,1000,705,-1000,-34,1000,-400,189,-402,46,333,524,-1000,1000,-500,1000,1000,224,1000,-1000,1000,1000,600,-367,1000,1000,1000,843,631,-351,-1000,560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00553() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{1000,-680,-300,-62,-168,-1000,1000,-765,631,-315,831,690,1000,-586,-870,-138,-1000,49,570,-1000,-1000,341,-1000,-239,-1000,-238,-345,528,-1000,1,-172,837,1000,-699,-138,117,-172,63,624,-703,372,-377,-921,-123,-440,-1000,-728,-1000,-793,-285,160,817,1000,-495,539,924,-458,-683,941,160,-44,993,-347,670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00554() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{1000,-464,-1000,815,-107,-306,1000,-654,-386,-1000,1000,1000,1000,-663,-654,-110,-1000,-454,418,611,-1000,-679,-1000,-1000,-1000,1000,-1000,-285,-1000,986,-49,878,1000,-1000,-592,-217,-370,222,574,-678,1000,-889,-1000,993,-670,-88,1000,-1000,102,-569,-31,1000,555,439,700,-24,-917,-1000,-791,-220,-442,424,1,551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00555() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{306,-1,-906,389,-108,-505,-803,442,-267,814,-453,500,-520,339,-441,-517,-763,376,-657,344,-643,-324,2,-731,-115,-36,-19,724,292,-604,-733,800,-996,-402,428,-340,301,459,-983,-965,-384,135,-312,742,-994,894,556,937,626,356,741,457,-616,452,874,-2,680,996,-614,119,-923,-856,807,90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00556() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{-815,188,180,-53,328,972,-144,113,592,244,-626,-400,-660,-133,828,-176,194,-267,614,177,986,467,401,482,787,483,505,-826,732,-381,-24,-566,-883,278,-76,862,433,-484,-863,423,351,-158,-113,-588,498,610,948,939,-143,-132,349,-938,22,243,132,108,-774,60,-528,940,-968,228,944,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00557() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{-87,677,-594,645,-474,-909,0,-460,462,-461,-873,-823,463,-780,-128,-653,-325,-7,-646,333,361,-369,-280,-922,687,-212,-896,152,-313,-416,-226,905,-677,-632,-96,535,-329,784,921,-3,681,528,224,-392,748,372,-772,40,329,932,-859,-29,256,-542,-352,93,-946,462,796,392,-426,406,-271,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00558() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{-142,733,222,-95,-436,588,-924,887,290,-744,-415,-38,-35,899,114,-723,847,-171,-471,80,-33,-212,707,169,77,684,-85,-911,655,-401,723,-595,-689,604,315,719,63,-601,-410,278,386,144,301,0,-322,968,599,506,-149,-417,844,407,96,-791,292,-560,608,-419,380,-658,-15,173,-289,346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00559() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{372,-981,-432,57,-977,455,803,-874,874,999,-708,625,-20,164,-221,-693,229,475,937,-951,-410,216,-310,-323,-631,-499,483,125,402,-847,-954,-604,-35,565,449,152,253,-765,-271,-49,-248,986,241,85,542,16,23,-204,-342,-140,-79,249,956,143,-983,28,-325,-485,956,198,824,599,-792,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00560() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{863,-36,-19,-165,-244,-891,553,-65,-592,-665,102,384,731,-49,-211,-767,-837,-258,798,318,-696,-111,-698,-575,-444,579,-51,-146,-913,-546,317,25,315,-276,28,99,175,-697,-267,-212,888,-386,-994,-535,-317,-116,355,-486,-998,-330,996,445,48,-878,836,701,-740,-934,185,357,-732,-625,111,897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00561() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{276,1000,336,158,359,1000,-202,645,-176,-461,214,-432,-1000,155,-459,-645,382,-285,-1000,235,582,319,1000,150,797,-450,497,-807,857,398,-16,-291,92,418,-215,1000,-117,1000,716,1000,681,-171,215,717,-710,998,-17,348,102,932,616,-440,137,-474,-507,-4,1000,965,-766,-1000,97,1000,-516,-887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00562() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{-180,-999,-11,60,-892,-605,1000,-205,1000,667,-1000,396,370,84,569,-143,614,-651,1000,-1000,-194,633,859,-437,284,107,756,-735,529,-939,-1000,-1000,-747,926,1000,-70,-98,-1000,-546,-106,11,740,-652,-918,410,382,1000,641,341,-603,-685,188,1000,-625,-1000,269,919,-465,393,664,462,-96,-746,64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00563() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{1000,48,-912,734,-626,203,-725,-903,457,-606,927,1000,1000,701,-1000,1000,-179,-729,-544,-648,296,-472,-896,-239,-413,-829,-895,528,-635,262,296,-77,1000,-699,506,117,545,955,126,-1000,-543,381,-506,-486,11,-654,-148,-1000,-99,-285,-767,1000,-466,-975,-7,-849,-120,-895,-266,-1000,-296,-453,49,-777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00564() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{1000,-535,-1000,-325,-563,495,1000,-65,-1000,979,1000,482,1000,-49,-150,-243,217,167,-372,747,-1000,-354,153,-1000,-512,692,-887,1000,-1000,963,-584,-103,1000,-276,-230,41,158,411,1000,-456,13,-531,-516,369,-214,-116,-1000,-1000,-998,-330,-408,1000,259,-332,-571,852,344,-865,387,-461,1000,726,-1000,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00565() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{1000,775,-157,887,1000,-613,-109,659,-961,-1000,1000,724,279,-82,53,-706,-1000,-809,1000,234,849,-23,-1000,267,-658,73,-587,776,-1000,37,-1000,800,1000,-1000,417,1000,-1000,-326,134,-1000,486,-211,-803,400,1000,339,1000,-190,240,-59,372,192,-182,-213,1000,885,375,-137,-670,-720,-1000,327,612,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00566() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{1000,-280,-562,1000,-313,-844,1000,-287,631,-1000,619,905,1000,-232,-1000,-138,-1000,-1000,1000,-88,-1000,-519,-1000,-788,-1000,912,-1000,1000,-1000,-183,-225,1000,529,-695,-293,-146,-154,63,-569,-921,758,-1000,-405,-127,-218,-253,740,-596,101,123,112,741,-1000,-495,400,13,-458,-950,-362,294,-442,129,400,505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00567() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{471,-272,4,-61,-857,-273,206,459,978,1000,-909,-414,58,566,-643,-283,465,-612,459,-253,-918,-126,1000,-592,633,-486,302,1000,1000,-707,-836,71,-1000,511,197,-604,467,184,-632,-803,-756,1000,1000,-539,432,-352,-446,-362,451,659,-203,138,513,1000,-1000,904,1000,212,1000,273,1000,304,-429,-552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00568() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Minutes", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardMinutes():org.joda.time.Minutes",
            new int[]{869,-796,319,-1000,-728,1000,1000,-443,-830,1000,-26,-856,161,-346,497,-878,-345,367,-1000,273,-1000,487,1000,-826,224,1000,58,-363,199,595,-373,-1000,815,611,-1000,802,618,1000,1000,944,16,-999,332,301,-214,-634,-1000,-993,-1000,-720,428,76,1000,199,96,1000,635,-579,1000,408,1000,1000,-1000,-705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00569() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{1000,-988,-592,-514,1000,-1000,-1000,651,1000,1000,38,-371,-195,540,288,901,-242,861,223,-825,-59,-1000,-799,1000,1000,-965,-845,468,782,-803,102,329,1000,-744,-996,-673,632,-1000,-965,558,582,876,70,-48,-1000,-638,297,-1000,-1000,1000,-1000,617,-1000,-1000,-92,1000,321,1000,-495,595,-96,-519,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00570() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{251,-706,-703,-539,664,113,1000,-182,-65,638,-932,-1000,-1000,581,-1000,1000,-103,372,-1000,-220,1000,682,-618,1000,1000,-1000,-856,-497,-1000,-516,175,-283,-1000,843,-620,-1000,297,-81,968,-95,-651,1000,-1000,-988,1000,-106,-1000,1000,796,155,1000,-922,-1000,-775,-144,-555,-448,419,1000,561,1000,1000,-767,-864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00571() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{200,-199,101,-1000,105,0,0,240,-802,0,276,-642,61,236,0,839,-1000,672,704,-665,-38,424,42,734,-978,-511,-1000,475,404,-1000,0,-1000,-469,0,-195,-1000,-1000,590,-66,-211,-998,-110,-262,914,69,88,-326,-1000,1000,114,320,-971,-1000,-353,0,404,-564,-715,-392,310,1000,-35,-384,47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00572() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{-557,976,-754,-970,-683,-68,-121,405,-196,628,-174,-574,933,-897,802,280,238,8,-73,-422,491,766,461,-260,197,-190,516,-150,-684,312,348,697,-337,-777,451,72,186,261,-929,-945,-132,557,-975,72,-567,67,-309,734,-807,568,954,173,392,-903,-495,439,-764,-983,406,836,437,538,102,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00573() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{502,-489,-822,-903,335,549,60,300,-287,158,618,-19,-869,-569,-880,901,-795,79,-602,-464,296,114,-799,786,-608,309,-271,669,-626,-263,102,-871,-233,135,-860,-565,-165,626,246,307,424,409,-603,-45,386,-551,-689,-535,574,-131,687,617,-563,-163,107,149,-964,-996,-352,724,977,265,-454,394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00574() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{-995,673,522,246,-879,777,481,-713,-594,399,-700,620,407,-303,916,339,-89,-200,511,777,-824,873,882,-737,-607,727,-215,-25,155,-101,649,589,-353,585,-349,-137,38,566,-444,-91,-883,-621,987,-53,620,377,778,623,867,-979,966,-410,356,792,607,-11,-218,-556,112,-285,564,-40,58,892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00575() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{186,-316,-598,415,542,15,198,-447,166,-549,-274,247,-114,-183,923,-344,-171,-16,-455,945,-825,-911,-738,542,626,-807,-368,-974,693,-898,-351,180,830,548,187,-873,189,-310,40,143,375,995,547,-255,-63,949,759,937,-449,-236,-981,142,-170,281,-535,-735,559,592,-561,-429,749,566,93,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00576() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{-624,421,-423,493,927,-239,110,-761,-71,92,965,-47,551,708,944,-529,-958,-912,263,-233,295,438,941,81,-868,855,767,695,53,770,695,294,141,-260,-850,259,560,481,-895,-142,669,-904,-950,372,-845,-149,-991,-579,788,587,-410,-559,-603,854,-245,150,-481,-784,-547,105,767,800,546,-990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00577() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{444,-849,-406,285,-820,-287,923,616,-575,-148,203,-316,-460,228,-770,-936,917,557,15,438,50,-702,420,-331,-158,-333,567,-959,519,-490,-956,-984,509,54,513,-754,-612,284,-216,237,551,-147,-415,77,695,-246,373,931,348,759,312,-366,715,619,264,-359,939,372,95,-157,492,-251,884,-701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00578() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{979,326,-97,-659,-443,-825,-898,-164,-512,555,184,926,237,-50,978,-238,-315,-163,746,45,-640,-844,172,-776,-636,-5,18,396,741,829,-200,-907,919,-857,384,-528,-358,14,-119,144,390,-651,406,858,-348,834,321,-446,-383,423,431,930,871,-861,-716,598,496,386,172,-861,-393,-453,768,-66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00579() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{810,-348,-624,-477,328,-844,-239,-608,-397,461,-825,-364,-916,711,-186,-303,585,-390,224,-463,136,527,394,20,540,-989,224,12,12,948,-148,-211,208,-878,-66,-307,-287,-369,380,-343,-370,787,-721,-763,307,451,-207,-943,417,-475,-358,-767,-371,-689,-516,246,-64,807,953,989,313,646,424,-739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00580() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{188,604,112,-378,319,912,-707,489,-288,55,288,419,98,-243,16,-125,503,-247,-150,-1000,-28,1000,474,237,-759,1000,813,1000,-492,1000,198,204,-69,-689,-515,984,-845,261,297,-72,42,-51,-505,-301,-613,-1000,-944,-692,580,-189,459,431,556,-241,967,887,-515,-931,82,167,-802,-868,-7,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00581() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{41,697,308,460,329,-638,-742,-905,348,529,-634,713,869,134,1000,-529,848,435,462,794,-907,-780,-79,-464,629,-506,-337,-486,1000,202,54,773,863,-157,669,695,372,-787,-649,-160,-600,163,1000,-107,-825,1000,1000,355,-1000,-238,435,268,352,-414,350,-150,849,1000,-108,-1000,-593,112,-143,-219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00582() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{561,-688,375,-557,1000,-318,316,113,1000,337,1000,-371,-800,104,71,-169,-1000,-1000,-447,-1000,1000,711,-67,1000,-816,1000,682,1000,-305,298,393,-574,74,-748,-619,-603,372,851,-499,587,1000,-563,-1000,466,-906,-1000,-1000,-1000,1000,979,-301,280,-1000,84,-764,408,724,-1000,-1000,1000,836,935,-202,-998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00583() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{745,-626,-87,-1000,1000,-1000,-112,-9,1000,939,38,-371,-1000,581,-722,-942,-887,-1000,-456,-1000,1000,1000,439,1000,-487,1000,753,1000,-1000,1000,1000,-488,-469,-1000,-996,147,573,755,-18,313,297,-389,-1000,9,-1000,-1000,-1000,-1000,1000,1000,-1000,-22,-1000,-1000,-323,988,-448,-1000,188,1000,1000,-519,-310,-864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00584() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Seconds", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardSeconds():org.joda.time.Seconds",
            new int[]{378,-281,99,-682,758,372,-1000,-523,445,1000,-1000,-1000,-835,871,1000,-446,544,494,-838,-917,1000,379,-596,844,1000,376,-509,650,-10,1000,951,184,-651,-267,-840,595,516,-408,-398,-370,-1000,1000,-655,-1000,-971,1000,635,274,253,261,635,-654,-1000,390,489,-144,403,443,1000,-753,139,-373,450,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00585() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{516,596,472,901,-140,274,392,172,308,-807,118,-1000,-48,1000,978,1000,83,-769,1000,-241,711,-715,-378,1000,-597,406,-15,1000,807,21,-154,-472,-317,930,-558,-37,-126,-843,-632,-576,-1000,312,-240,-750,-560,-498,-718,786,-536,-1000,-899,754,829,1000,693,-656,79,-452,-808,-1000,-728,269,205,-94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00586() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{-305,-376,847,469,-95,-8,458,-575,941,-570,-242,-406,-151,137,-249,-320,-916,249,-84,145,260,516,362,-43,781,-767,825,130,746,752,-664,489,66,-28,605,105,156,594,214,-876,723,659,-455,362,198,913,70,959,200,-144,75,-387,9,14,197,-494,529,918,262,863,423,-450,808,307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00588() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{337,-591,-588,-562,467,353,-313,-166,-102,-92,-121,399,399,789,599,800,107,899,947,-904,-509,-320,744,244,-519,-330,476,406,-721,-303,-132,603,-75,-750,-722,-707,87,923,116,729,738,-403,-643,581,-606,-998,876,245,404,660,826,494,-580,869,-458,-314,-361,332,598,-374,632,320,523,-667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00589() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{-317,851,434,-505,429,247,-629,160,293,-320,482,-638,704,800,-632,807,440,-592,876,462,532,-720,423,993,-839,-301,523,-679,689,45,639,277,565,856,660,321,-749,-779,-607,-409,-553,-996,-710,180,-16,-491,-718,-546,507,-541,-891,-502,-249,867,793,-722,638,-694,6,-944,-320,-627,40,687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00590() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{-677,691,86,450,310,770,575,-917,267,-246,883,-954,589,852,943,-764,658,172,-914,-784,-420,714,-766,120,132,-909,349,-978,-269,-51,868,-641,751,-859,-920,988,-26,-895,-563,-202,84,615,643,371,-810,102,45,144,-872,-930,301,-226,-38,-215,626,-55,555,-602,-11,-268,-693,893,-428,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00591() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{-740,-102,518,-538,-348,-759,540,458,-322,389,-681,427,-249,404,-218,-41,862,909,325,-116,-218,-311,-148,499,785,31,-915,77,-650,-323,34,678,-152,338,727,-759,-377,146,358,-722,72,-898,856,-924,-677,-364,537,267,950,696,1,-131,801,470,-304,-554,-570,-217,706,909,931,-502,-778,-324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00592() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{10,827,863,371,27,-623,-390,88,-568,-49,-602,-668,544,-348,641,-804,925,-307,906,-918,-252,-559,748,318,-501,-301,340,-607,-879,17,-79,232,261,881,-657,-95,-297,60,-995,471,214,-911,350,-690,-899,882,741,-826,537,838,-970,-520,404,-590,399,-95,810,117,197,204,-288,-788,359,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00593() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{-840,-166,777,720,-476,365,-511,395,817,-78,-67,673,-307,-560,672,-813,35,654,957,-309,196,-594,332,137,879,207,21,-772,426,-268,319,224,836,7,-604,842,239,-511,982,-652,-269,-103,-760,-425,491,725,821,-455,936,-327,552,-407,-719,-90,520,470,334,16,393,-247,626,-251,-239,88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00594() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{-987,369,988,-732,810,130,-369,-26,189,-206,792,116,106,-479,1,-866,-695,-655,134,-617,132,585,-284,769,803,-997,540,-826,-466,314,-57,891,-461,706,-671,286,-753,-531,-454,207,-681,-485,-404,282,-952,608,-180,-820,-474,-541,165,-853,-367,499,450,694,-678,-912,637,182,940,-790,-962,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00595() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{521,-830,-543,-59,-782,-738,134,59,560,-873,-940,-380,159,-48,819,372,-745,-876,987,549,789,311,709,837,-965,-183,-882,298,-450,-406,-743,-719,-285,-586,-615,-649,-438,-912,611,571,-232,338,-367,84,713,951,40,890,-698,-812,-908,-58,909,56,-779,238,243,-961,552,486,-820,-869,708,58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00596() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{176,-281,1000,-562,-292,-983,-218,128,-102,-432,-1000,-206,-182,-703,-460,800,-729,-86,1000,-904,378,-374,1000,96,338,-341,818,389,319,-303,-1000,1000,-277,1000,790,-653,-33,1000,-88,-405,814,-409,-643,-381,135,1000,557,279,1000,1000,826,-593,-580,-249,38,-522,707,1000,408,1000,707,-1000,1000,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00597() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{98,-37,-253,1,-709,536,-1000,258,162,-826,1000,-1000,554,-528,868,-1000,-1000,-1000,737,-319,390,-49,-265,992,107,-957,1000,-1000,1000,-486,-750,1000,-326,973,-1000,-927,663,-807,-804,515,-332,590,-368,67,-1000,240,-1000,-1000,-1000,-1000,-373,-458,-702,234,947,-758,-784,-383,-736,-757,234,18,801,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00598() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{680,-446,-827,-203,-380,-930,-252,452,-245,-843,378,-708,-648,-543,867,-565,-1000,483,927,343,-104,-125,-870,-466,-600,-9,-171,78,957,-797,-1000,1000,21,85,49,-628,1000,-835,29,359,529,539,895,-543,-1000,-257,-164,-485,-1000,-1000,94,-40,-243,-1000,173,-644,-655,672,-539,625,1000,1000,-344,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00599() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{-1000,510,951,469,-95,447,-33,-17,898,-570,712,1000,-323,-934,-29,-946,-537,112,194,-856,168,642,67,-43,1000,-685,246,-973,-855,779,646,387,202,211,-994,105,-1000,-318,398,-155,-1000,-767,-1000,362,35,1000,1000,-602,638,-194,893,-1000,-952,287,521,-494,51,-973,1000,183,423,-1000,-1000,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00600() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Weeks", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "toStandardWeeks():org.joda.time.Weeks",
            new int[]{868,639,3,254,429,-297,141,448,293,39,482,318,277,453,-699,715,457,-592,745,-92,405,20,70,-653,-630,302,-228,1000,165,-234,-919,1000,-1000,718,-60,-37,-898,-1000,196,1000,-101,-261,-1000,-287,-560,323,-718,1000,1000,637,-891,-260,190,1000,364,1000,494,559,90,552,369,-830,629,-94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00601() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "weeks(int):org.joda.time.Period",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00602() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{1000,618,945,334,-1000,-872,1000,-789,214,-1000,-842,-1000,-274,976,-328,-1000,288,1000,542,458,301,-844,-1000,-463,512,1000,-77,-1000,-119,-833,-1000,248,-780,-528,230,-490,817,-1000,1000,495,581,-432,-1000,-645,-797,334,875,-61,-612,1000,-456,-180,1000,-981,434,-238,1000,-758,730,715,-734,-484,379,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00603() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{1000,526,-708,1000,692,-1000,341,1000,1000,-938,1000,1000,-1000,-15,-305,-208,-1000,1000,-1000,-693,-1000,-654,-1000,-184,-478,-1000,-737,-1000,514,339,-977,-754,137,-521,-253,-268,401,145,-895,1000,1000,-815,-1000,1000,-930,145,1000,-685,-1000,1000,-490,-71,-266,59,1000,-1000,1000,-1000,-335,-584,499,-365,894,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00604() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{-817,-669,131,-697,167,530,-223,727,-977,560,-965,898,841,-676,-130,44,199,-586,655,156,-190,807,407,-189,-521,-544,44,748,614,-488,-528,245,-330,182,-244,140,-822,-965,156,661,-719,-55,381,-823,78,-200,-940,412,-506,-385,136,-282,879,731,-345,609,-910,-563,250,-112,-575,-481,-482,439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00606() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{376,406,-936,297,-231,-488,615,291,488,946,260,411,124,-416,857,-105,72,19,190,-110,-474,-762,-680,437,-801,55,-608,-667,563,98,-727,-982,563,420,845,356,-999,603,-720,-430,98,-381,236,482,-927,-380,504,672,-964,-627,-588,-781,958,-915,987,-724,29,-998,-243,882,-661,367,-56,149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00607() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{888,-989,-45,957,270,658,-737,892,-466,104,978,530,73,133,150,-343,-836,871,826,-252,-624,-992,-433,659,-881,629,877,-645,672,-465,455,859,231,163,667,360,-289,-937,-872,818,393,-782,-105,245,-195,-501,-636,-614,-302,975,-264,-522,450,922,-384,-567,-237,-27,202,-100,-847,39,351,408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00608() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{397,403,830,-941,-167,682,-986,91,-781,235,429,-940,-302,47,633,310,376,-894,172,998,960,857,582,-20,990,444,771,553,294,158,481,-730,463,730,995,804,-600,-63,776,-785,272,-611,-701,848,570,-748,-21,-402,401,-778,812,-722,-371,82,-905,430,-982,-163,449,109,-854,-419,229,827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00609() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{-292,894,540,-333,542,-185,869,-790,-865,-459,711,-119,560,240,-181,626,-670,350,74,-829,-446,-581,259,-37,78,-400,-355,-878,-168,266,664,659,-271,460,388,15,-274,-284,73,-667,956,-847,-422,-243,-821,-298,-863,-455,-741,799,654,805,826,976,462,267,853,-619,-686,647,-525,460,-730,899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00610() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{-281,-1000,141,-1000,-439,652,304,892,-1000,-727,-132,530,678,-645,-690,668,271,871,759,223,427,699,-433,-1000,966,229,1000,1000,-764,-1000,964,954,-1000,54,667,-522,-411,-403,1000,1000,46,682,-372,-592,38,724,-1000,-417,-652,-722,645,269,774,1000,-384,1000,38,892,-291,-1000,823,322,-1000,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00611() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{-907,82,664,-799,-791,774,-1000,1000,-629,1000,-935,1000,275,-423,1,-208,182,-839,1000,212,-1000,-399,1000,654,-478,-1000,1,1000,514,-200,236,95,8,265,-92,-268,-1000,39,-340,44,-9,454,892,-672,-665,-1000,-1000,744,-438,-891,327,-71,659,-91,-319,1000,-686,-1000,-479,-366,-1000,-358,-942,-62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00612() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{51,559,-571,824,1000,-695,1000,-1000,41,-1000,760,628,1000,-430,21,887,-968,655,-729,-974,-456,-796,-679,-590,-341,-219,757,-1000,-710,-179,149,1000,624,161,-538,-255,718,-558,-653,232,541,-657,-134,447,-802,918,-458,-274,-1000,1000,263,1000,719,1000,939,-109,1000,-809,-829,631,-832,1000,-927,417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00613() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{791,-190,22,-1000,-709,1000,-1000,1000,-1000,-131,868,-599,160,-1000,-343,1000,-391,-455,751,399,1000,1000,674,-314,1000,-161,1000,1000,-222,-189,1000,265,-506,353,1000,243,-1000,-844,1000,-184,285,-631,-163,122,-40,-117,-1000,-598,-455,-1000,1000,156,-185,1000,-1000,1000,-1000,844,-677,-1000,926,432,-1000,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00614() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{119,-951,-449,-38,-544,52,468,663,-430,396,-1000,1000,1000,1000,-573,-173,-64,40,535,-543,-862,-844,0,-175,-1000,-854,-496,360,-480,-598,-865,756,-654,-328,-941,-424,-402,-921,-387,1000,-910,372,872,-1000,-321,324,-925,693,-787,160,-432,223,1000,674,288,308,-222,-449,-64,-188,-1000,-188,-642,-140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00615() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{1000,666,-1000,1000,-682,-865,985,927,1000,457,-601,1000,95,-135,56,-570,-657,596,140,-1000,-1000,-872,-1000,1000,-1000,-955,-1000,-1000,1000,-633,-1000,229,-780,-154,265,-907,-1000,365,-1000,-81,-50,-432,1000,-812,-1000,-556,173,1000,-1000,470,-456,425,1000,-981,1000,-325,1000,-260,-740,106,57,885,-898,-191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00616() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{752,-353,400,-1000,-1000,1000,-1000,1000,-1000,1000,1000,-1000,709,-801,252,-26,-17,-1000,1000,628,1000,-107,1000,-441,426,-581,-289,1000,554,-633,906,697,-216,-1000,880,880,-1000,-1000,296,-793,-281,-823,-592,831,-368,-21,-884,1000,-597,-1000,678,-484,-365,776,-1000,145,-679,512,400,-1000,107,-400,84,-897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00617() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withDays(int):org.joda.time.Period",
            new int[]{396,-202,400,295,545,-1000,1000,887,1000,-205,-1000,1000,-296,1000,747,-1000,-428,585,-678,-424,-1000,33,-1000,1000,-784,-979,-1000,-1000,1000,-1000,-1000,-244,-1000,555,1000,-1000,-1000,303,-652,64,-1000,-65,746,-1000,-568,-1000,787,1000,-666,395,-1000,125,359,-1000,704,49,1000,-138,660,1000,-418,690,-695,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00618() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{1000,-482,234,-94,-924,640,850,-52,-129,-933,-604,-1000,192,-1000,1000,-353,-157,-1000,753,1000,-1000,-361,571,739,388,584,235,589,-867,-1000,785,148,-1000,-786,-1000,-543,-174,812,642,-473,-1000,195,-189,680,1000,901,1000,-562,875,-620,-161,476,957,-270,-129,-394,-779,-433,-724,139,288,-256,-451,201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00619() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-817,-376,324,-249,-391,-79,74,-874,-473,250,611,-809,396,-29,-248,247,-217,-937,88,391,-273,-173,840,115,-163,326,-975,1000,-1000,-1000,257,-702,-71,-185,-490,1000,218,474,-747,-312,-681,-290,-562,1000,1000,539,691,263,-1000,-1000,339,391,-610,-1,-885,1000,579,197,-1000,359,-519,-729,-980,426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00620() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-16,-31,-81,-889,-940,161,778,798,-540,-940,261,-882,723,612,572,-300,266,194,-947,-509,-529,852,728,-995,-424,184,-133,-512,-496,-186,100,-735,168,-290,-522,477,-420,-252,28,-753,97,862,798,872,-461,-124,-833,-904,-399,-295,-704,724,-63,-23,986,-124,647,329,911,60,-521,-345,-751,863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00621() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{301,624,592,-765,801,807,109,-28,308,-838,-741,964,-834,-246,804,-565,626,51,746,-687,-7,154,-906,261,-200,419,155,187,727,28,-466,101,487,-605,-155,-829,562,-573,311,-377,-192,611,867,-268,260,654,-813,559,175,967,-891,333,345,-305,-635,-600,11,148,208,540,330,561,579,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00622() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{282,-147,-235,745,147,779,-812,-506,-762,230,-111,592,-560,762,-432,247,300,-521,-367,-287,891,269,-410,-596,490,-885,-660,873,233,-300,-530,672,-679,-156,-557,131,126,446,-215,-182,570,-438,769,-887,-450,-994,944,-305,-164,-585,-129,999,885,131,894,-278,-693,433,-565,330,-660,677,364,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00623() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-360,-313,306,-770,-106,439,-575,70,-618,-541,156,-847,-583,660,-340,231,832,-596,350,-254,131,-549,732,978,-236,167,-960,939,-925,-140,638,-793,928,-551,-971,859,742,796,-558,-539,-263,-20,-558,672,548,-446,772,432,-939,-604,745,-51,-698,-847,-130,716,534,473,-849,664,-671,223,-514,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00624() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{850,-48,-930,-446,-342,80,-509,978,614,-70,-876,-922,-540,872,26,-965,-527,-185,822,906,927,681,941,699,791,13,894,-539,8,-105,-595,-94,180,-308,-982,-964,-785,262,994,-971,521,234,-269,-727,747,-304,744,-178,720,218,323,790,-772,-463,-617,-744,-390,381,732,-940,739,-21,29,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00625() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{68,-612,261,211,-197,366,913,-168,-234,66,-411,-859,179,-654,837,210,-415,-879,411,689,-781,-24,473,870,606,317,-264,-290,-196,-448,-109,-892,-894,68,-528,-326,-189,958,507,60,-320,-655,-295,-517,102,-44,776,-478,651,151,-496,492,713,-22,631,-291,-809,-929,-685,-138,-122,161,-54,-666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00626() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{53,842,-552,815,-924,832,829,-169,337,-563,-264,920,-249,-816,13,99,-789,642,-131,2,-125,368,155,596,-17,584,468,-238,-984,-288,-684,148,450,-786,-574,-262,-925,323,420,-678,-56,195,552,680,356,766,973,712,-30,-812,70,957,-860,72,-153,995,-129,-993,-999,96,-591,-216,397,100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00627() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{44,1000,1000,752,-493,744,-85,1000,288,-1000,317,488,-1000,0,1000,-324,-642,-217,1000,37,0,-1000,-921,699,-687,-38,119,-297,1000,-649,-478,-372,0,-132,-724,442,709,0,0,-574,-513,582,544,357,-970,-177,0,825,759,0,-39,87,-290,-511,-764,-159,-80,-541,0,1000,360,944,-606,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00628() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-215,-228,-336,-1000,-1000,-472,-296,1000,-492,-258,-826,-202,1000,1000,-106,-413,-1000,1000,-1000,-314,454,1000,-153,-1000,769,-566,609,-1000,477,1000,-1000,-891,176,1000,155,-923,-1000,-94,463,-244,636,690,656,-496,-1000,-861,-1000,-1000,660,248,-565,1000,-63,1000,1000,-667,1000,404,1000,-1000,13,-624,-13,953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00629() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-1000,-375,634,-477,241,1000,365,558,-700,-823,-411,-79,370,336,335,517,1000,853,-358,-977,-422,235,487,870,-1000,636,-1000,663,-168,-249,535,-892,659,-317,-522,894,806,-791,507,-395,-320,440,495,1000,-58,270,-766,-705,651,-92,-651,551,527,-388,775,436,1000,601,-489,930,-122,-199,-131,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00630() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{1000,-437,948,239,-1000,-1000,-741,-75,987,-602,-48,-1000,1000,-271,498,-649,-1000,51,52,1000,-301,-1000,596,-1000,995,-401,593,187,-699,-1000,-493,326,-859,-536,-844,-253,-1000,1000,-134,244,279,-458,-1000,758,903,557,-313,-573,429,-1000,625,1000,142,-356,-514,144,907,190,-1000,473,-981,-1000,-634,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00631() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{691,423,-769,53,353,685,974,-512,1000,-435,-454,1000,-17,-406,-465,-16,-57,1000,475,-566,355,754,-615,258,292,816,356,-623,149,-28,-1000,-510,-1000,141,326,-892,244,-1000,886,144,-360,375,1000,531,206,667,765,40,523,80,-963,-266,-234,744,-684,820,-448,-528,776,-293,-198,-222,445,537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00632() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-126,-431,1000,468,-651,296,604,-66,-662,-851,-1000,-1000,-771,-372,1000,-1000,-1000,-229,1000,752,-1000,-1000,1000,211,-148,121,471,-942,-990,-1000,-484,202,304,-1000,-1000,55,1000,1000,-47,-1000,-202,103,176,901,921,337,459,614,517,825,377,780,420,-1000,1000,171,-1000,0,-986,-424,294,921,-1000,-391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00633() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withField(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{736,-666,948,-86,-1000,-1000,-1000,1000,976,-651,-182,-794,1000,471,531,-1000,-1000,221,-254,1000,-194,-907,-110,-1000,116,-661,721,-682,410,-108,-732,416,1000,-490,-1000,-822,-41,1000,70,-381,1000,106,-1000,725,-497,-825,531,-593,774,-514,802,1000,-579,-356,247,-939,1000,-178,-368,746,-769,-711,-979,978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00634() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{1000,794,-494,711,-456,-1000,-772,-625,-173,872,1000,-1000,588,1000,32,-1000,1000,701,-477,-567,618,-125,-995,501,781,-508,-341,481,891,-697,685,-1000,-1000,-719,-569,401,90,5,892,-1000,-1000,375,1000,894,-522,1000,411,-595,1000,-1000,-1000,-1000,-564,-451,512,807,-959,-69,-1000,1000,430,663,-1000,390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00635() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-788,-706,582,216,114,749,-257,521,-309,594,16,-186,-58,-840,735,16,585,804,-175,-832,-658,-397,-845,987,20,-384,942,-789,440,-474,-753,-20,168,843,583,104,505,-754,-864,-168,183,156,-835,-569,-154,159,-206,-220,526,-471,-436,33,67,-240,-103,383,721,-243,-77,965,981,-863,-1,-980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00636() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{953,365,-392,-881,479,-959,266,-647,-913,-32,-347,-194,-350,-284,-302,-737,122,-226,-33,213,-60,393,-862,649,566,112,190,236,136,931,-224,707,-928,-546,-809,48,-977,-246,853,-321,-524,193,269,-339,-960,-9,-914,733,-487,-71,32,-64,582,-737,-720,472,-700,-341,109,528,-701,739,-418,-179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00637() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{143,734,-569,790,-823,-403,-679,-429,740,988,-761,132,761,-468,-128,-691,356,903,-458,848,303,433,-500,-979,-960,-795,209,925,179,866,-379,-897,-556,-229,385,829,-612,-504,431,-950,-673,223,441,785,-548,80,340,-419,313,-898,-864,-447,-462,-834,171,-553,956,999,-797,220,-872,127,-449,696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00638() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-861,949,-309,-407,-981,-948,-13,219,-139,-636,-908,-653,446,-742,243,-429,198,-517,726,115,-794,167,-525,500,941,690,490,816,-287,518,656,-374,-96,454,-485,132,-585,-829,-989,999,-161,395,-679,-76,452,-346,503,-51,282,100,871,335,215,610,-230,-181,-586,97,742,84,-860,-240,939,-677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00639() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{495,-144,84,-33,559,-469,-715,812,-49,-809,576,68,-90,971,161,182,674,-29,74,-323,-853,-917,158,-307,618,-506,-857,-547,663,-777,-68,-546,-751,-567,167,-199,-463,678,649,-158,926,713,-763,6,-557,74,490,-413,788,-777,-492,-897,23,-269,160,814,-800,-273,-774,15,61,606,848,774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00640() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-436,-380,987,-569,307,263,-317,408,-354,881,-878,969,-929,-824,876,421,-722,505,101,575,80,292,-474,-475,-477,496,-660,283,426,360,-282,424,-534,13,-369,506,-606,927,115,760,-989,-287,225,-702,123,-704,-489,932,-144,109,90,-444,422,-368,78,-946,732,-671,514,-342,-931,-160,-245,790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00641() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-533,146,761,-433,407,346,-283,335,949,673,964,882,569,-575,-626,-700,559,-398,969,-136,-863,200,-151,751,-859,-629,305,935,567,940,-631,174,88,-865,987,663,194,811,526,-396,-405,-61,-655,737,141,-320,-151,-305,-323,956,-428,938,950,-859,632,978,-3,192,193,-376,-744,367,457,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00642() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{327,997,-501,852,-840,63,-542,109,869,195,-907,591,951,-554,-174,-272,-317,-366,-655,270,162,-605,208,430,85,-493,465,-865,-390,-204,-869,-340,547,-652,293,551,-407,-866,150,434,360,669,-668,-889,-454,-135,469,-870,-255,-152,197,-64,-878,180,-498,667,470,675,654,-340,986,-210,827,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00643() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-161,-479,-309,515,-252,1000,-1000,1000,262,-691,-1000,928,810,-390,-877,-427,-1000,-34,-129,115,-216,-1000,456,769,-356,362,892,-1000,-1000,-1000,-723,-907,1000,454,812,-145,-467,-284,-989,-355,202,245,-435,296,254,-744,1000,-246,-310,712,-689,-131,-1000,1000,759,-387,-93,654,1000,-618,-860,-240,939,713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00644() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{176,-1000,462,-277,1000,1000,-204,807,-1000,-1000,-87,286,-633,-754,-877,-269,-971,-568,626,-803,-583,-949,57,172,652,1000,553,-1000,-1000,-825,455,454,529,-740,-229,-1000,-437,503,-726,-218,54,282,-239,132,283,-712,-98,1000,-692,1000,-200,181,711,989,484,-138,-1000,-757,1000,-165,-10,-240,294,-471}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00645() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-410,523,93,770,-143,677,-679,53,1000,1000,-619,841,-100,-202,-136,-1000,535,64,1000,777,-755,701,-471,71,-1000,-565,114,1000,320,1000,-485,-174,-595,-1000,745,1000,-532,713,536,-883,-1000,-88,-14,1000,-691,-48,-588,-313,-473,-59,-448,779,1000,-875,961,605,354,1000,217,205,-1000,843,-231,368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00646() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-626,419,435,1000,-291,176,-523,1000,-69,-301,1000,605,144,1000,-2,263,832,403,464,-506,-333,-1000,1000,-631,849,98,-429,-154,205,-1000,301,-1000,-177,-592,147,-226,642,1000,-199,638,861,498,-391,942,-572,-133,625,-1000,-127,-864,-353,-256,-977,242,72,998,-338,-557,-1000,202,499,429,-567,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00647() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-680,-774,544,-123,513,508,-1000,780,-624,-874,1000,-345,-66,1000,-424,1000,1000,208,987,-309,-583,-949,463,172,390,407,-1000,258,623,-465,1000,-1000,-1000,-207,-52,-1000,-11,1000,-135,-218,834,905,894,1000,-71,212,413,328,274,-1000,-725,-1000,588,167,1000,569,-1000,-757,-1000,985,-1000,1000,-673,275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00648() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{1000,996,-1000,1000,-1000,-1000,-246,-1000,934,125,-882,-596,370,-719,-1000,-1000,-362,-451,-579,349,938,540,570,-614,-366,-1000,22,956,-1000,49,-610,-723,-149,-1000,-180,449,-1000,-893,980,-1000,-394,621,1000,1000,-1000,16,421,-955,-1000,591,-520,-1000,-833,-374,559,159,283,1000,25,-66,-817,864,493,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00649() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFieldAdded(org.joda.time.DurationFieldType,int):org.joda.time.Period",
            new int[]{-318,373,15,1000,-436,-1000,-399,-342,858,470,937,-114,105,1000,-327,-758,1000,834,227,-560,627,-595,80,-730,-1000,-556,-365,1000,862,128,449,-1000,-1000,-1000,154,221,908,883,410,-1000,-687,-465,1000,1000,-647,951,852,-1000,1000,-1000,-1000,-400,-1000,647,811,878,-1000,192,-1000,1000,-841,1000,-1000,-554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00650() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{611,-548,-808,1000,-1000,653,676,645,246,-873,-372,332,-342,-1000,-400,-28,-140,-1000,1000,-738,1000,684,996,-644,1000,-1000,1000,-1000,-1000,-842,-1000,1000,991,-317,84,764,-187,282,-16,-464,527,-1000,170,-102,1000,-1000,506,221,443,442,243,-1000,-485,711,476,-88,330,425,-85,-1000,1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00651() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{1000,856,508,628,-536,583,495,408,493,-56,-366,416,62,-1000,-1000,-686,-273,31,1000,-121,266,-624,856,-385,322,-74,1000,-908,-1000,-1000,216,119,-743,-403,141,104,415,-557,181,-364,-191,-1000,-705,486,569,-1000,1000,-170,-616,-660,628,-830,350,792,-694,988,-326,-115,-813,-1000,1000,455,736,302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00652() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{704,387,-549,-128,-129,281,497,466,526,671,-28,-433,-379,-595,-348,-640,-445,-512,798,-404,820,-566,526,-411,876,-431,609,-992,63,-455,-358,474,-626,-832,-697,799,992,50,299,-809,637,-436,-481,-285,967,-868,888,-121,-429,-70,470,-653,-623,809,-497,449,-265,-601,-826,-556,869,181,228,266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00653() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{898,642,765,898,-759,540,981,422,829,-224,-25,342,262,-594,-695,776,-165,-80,445,814,165,-933,981,467,-959,236,990,-579,-901,-314,849,-900,-73,748,450,-544,-738,-448,-275,282,-862,-212,-741,917,-4,-458,-218,-278,-943,-634,371,-142,602,-641,-699,583,-838,300,669,-465,117,-373,145,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00654() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-326,-584,491,786,-172,411,-126,-580,-504,-352,-592,-424,770,-782,844,79,961,344,888,182,969,71,-784,-883,384,-380,974,938,-311,-285,-783,716,-53,-943,614,-11,443,-483,561,848,892,-418,-230,-984,-986,153,-584,856,569,-157,-54,-644,-737,629,-228,-148,314,898,259,-528,-37,-280,625,-872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00655() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-398,-695,-69,860,-973,955,193,813,-652,-388,230,-827,441,-547,-787,-476,-715,443,123,-49,-882,938,-509,719,-172,-351,-360,-267,-498,342,-526,884,-899,-472,-313,278,263,745,-905,761,434,-926,735,186,-320,-3,-630,-603,-169,-141,50,60,937,808,546,146,396,-654,598,132,569,372,174,-210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00656() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-759,-27,-745,-182,-177,109,983,505,876,815,458,-870,-793,579,963,841,-410,-855,-80,409,957,-851,509,430,-169,-275,-442,-698,704,483,29,-392,94,136,-748,449,86,419,-107,-353,320,969,-420,-185,565,307,-895,-208,-676,208,145,109,-789,-618,-419,-188,-750,-395,651,251,-294,-763,-581,-770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00657() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-896,-136,960,-199,949,137,122,-706,-11,88,-129,-758,-809,-635,361,645,205,-547,-325,-332,-626,-630,-875,333,-6,745,892,-27,-355,801,-547,192,458,-352,-929,975,-759,656,-539,50,-442,-310,682,-440,-974,496,296,-288,388,-376,858,175,248,951,-48,688,725,-937,124,602,767,564,322,880}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00658() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{655,-1000,114,-541,728,-230,-1000,857,-631,-88,-736,-394,-322,800,510,-620,-916,665,0,1000,-1000,113,-133,938,-755,1000,-472,1000,409,773,-69,-900,-66,-311,147,-544,-1000,-11,-578,-163,-1000,1000,-222,1000,-248,1000,593,1000,167,-164,-1000,460,-223,-1000,880,-1000,-538,691,-695,262,-1000,-445,-1000,694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00659() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-198,-689,699,1000,-79,392,-370,193,-799,-892,-12,-21,-285,-747,1000,88,157,-1000,1000,-194,198,1000,680,-248,1000,-190,230,-95,-1000,558,-1000,1000,906,-294,-415,-9,222,232,1000,1000,686,-874,387,-611,602,-495,-459,777,1000,552,-721,-514,-704,-327,779,-445,430,-296,380,-82,851,749,760,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00660() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-648,599,-69,-658,-973,955,778,543,164,1000,-607,-962,1000,-1000,-383,-394,241,1000,1000,30,843,938,-198,-983,492,210,74,-280,1000,-7,-526,-654,1000,-1000,56,835,155,-751,197,220,827,1000,-1000,482,5,-799,-630,289,-793,-843,-249,110,20,-786,-211,146,-523,-1000,805,-1000,1000,-848,-861,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00661() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{647,998,110,-531,-1000,-372,770,793,1000,1000,-825,-758,1000,-1000,-1000,1000,-966,-366,947,1000,364,-1000,337,-203,-423,-758,709,-1000,-163,763,990,-1000,368,527,634,-281,-1000,-881,-375,-618,-660,-99,-760,606,-40,-1000,-858,124,-1000,-1000,-795,-192,-49,-1000,-748,17,-385,-472,1000,-479,181,-418,99,-740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00662() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{451,-578,634,863,-170,-665,1000,139,-1000,-1000,-547,455,122,-1000,-21,-1000,578,26,1000,-932,-99,1000,271,-288,1000,67,-897,-1000,-836,-377,-704,1000,323,-1000,-412,47,1000,-752,1000,832,1000,-1000,191,-448,-75,-1000,-1000,1000,1000,1000,-1000,-36,-478,-607,431,-1000,1000,125,171,-1000,1000,876,44,-583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00663() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-1000,-967,-261,3,-404,-207,-178,-760,708,523,1,-1000,316,-747,1000,1000,-396,-1000,805,-738,703,327,138,-218,72,-746,26,-1000,178,-842,-806,819,-157,315,-116,1000,-349,397,512,207,1000,-243,355,-1000,1000,-495,-489,917,343,617,-893,-101,-1000,711,934,-1000,363,-1000,1000,331,278,-419,313,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00664() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-629,-609,1000,1000,-622,1000,33,1000,-1000,-373,-995,427,567,-613,1000,198,544,-118,1000,-267,1000,1000,-285,-797,1000,244,763,-333,-1000,1000,-1000,843,1000,-414,-152,-734,-1000,144,383,1000,-652,-544,-153,90,-73,-740,52,909,1000,343,-596,-809,383,-686,845,-612,388,-712,-64,-1000,1000,1000,345,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00665() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withFields(org.joda.time.ReadablePeriod):org.joda.time.Period",
            new int[]{-1000,-877,-456,-421,-598,929,829,1000,1000,1000,-755,-1000,-435,-223,-547,394,-231,-1000,1000,-801,1000,107,-239,-1000,1000,1000,974,-1000,603,489,-1000,-119,1000,-354,-205,1000,-994,178,-158,-68,1000,1000,-1000,-729,1000,-300,713,412,-667,1000,-234,-1000,-1000,-1000,597,-1000,-586,-1000,-727,-1000,164,-1000,-732,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00666() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{33,-196,-491,-128,152,-35,1000,246,-490,55,883,351,-499,223,593,350,-432,-973,-415,-1000,-875,1000,83,428,-250,1000,-694,203,283,94,1000,1000,1000,400,-421,740,-796,-462,15,-1000,1000,-79,-242,-39,400,-1000,235,-603,1000,-400,28,-373,-1000,-306,426,-948,-8,-306,-400,20,373,-198,-184,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00667() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{-213,-156,923,-180,523,755,160,328,444,-614,-521,969,-266,-81,-87,-852,-188,30,963,-384,-54,446,200,1,641,-533,946,213,-746,-267,-171,-336,-104,923,-269,29,-903,-294,-739,-662,925,-696,-760,-383,323,-695,-110,980,-752,-8,-557,134,869,391,-321,556,591,-528,568,-710,905,-492,620,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00670() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{-507,282,-930,-576,-611,629,369,-90,-847,-610,734,70,-144,363,497,-983,-373,-438,-182,-725,-765,-264,407,-213,429,-750,-444,806,731,-419,-4,-930,-50,-930,-178,470,112,-758,-669,458,-277,2,-22,-119,-658,-671,868,-369,255,-312,205,-492,-234,-652,947,-939,-972,-5,-460,797,703,620,60,-879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00671() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{-227,-489,-645,-350,-202,-619,-159,-874,609,-881,-506,48,-22,-489,-716,369,-837,-659,801,-881,381,774,-859,-339,-739,593,266,-574,-912,-995,-10,443,293,22,-493,770,21,-850,-53,981,862,-897,-169,306,865,564,497,-289,807,629,-423,238,-651,506,-580,250,209,296,-2,909,-253,214,292,-874}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00672() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{704,571,-504,707,-62,-548,289,954,257,586,956,236,-564,-327,446,-318,450,-719,-804,-136,957,375,-100,971,-855,352,-184,-336,882,-884,-629,265,690,76,570,704,-521,-385,-719,766,-771,613,-123,-454,818,867,884,751,70,-90,316,520,751,-372,-815,750,-273,-639,-672,-425,789,672,-889,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00673() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{-268,903,-141,-23,244,-241,923,-866,-186,-300,160,668,-519,703,-656,852,556,-922,480,552,-376,470,-628,279,126,888,875,515,586,714,263,878,183,946,856,-543,-247,670,-343,-235,561,-278,111,-877,198,802,319,-978,837,945,17,-962,-258,-848,487,-810,-629,-722,179,-683,760,764,-720,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00674() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{-282,-237,167,536,681,305,-1000,-392,564,-909,-65,602,345,1000,3,590,-707,-343,-813,78,738,-129,-143,-467,-226,-746,-490,-475,-699,1000,-837,514,414,-729,519,264,-633,-1000,-1000,743,-49,594,323,1000,-398,-666,-1000,29,1000,463,-436,-955,-509,-680,-731,98,-859,871,599,1000,1000,446,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00675() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{865,588,801,-207,435,371,394,1000,631,-165,524,712,-750,-747,726,-1000,-433,98,970,-609,-47,1000,794,259,-570,235,1000,-294,-406,-1000,-480,-510,975,1000,-433,374,-789,-1000,-1000,-572,928,-824,-847,-500,1000,95,1000,-460,-440,-361,210,1000,1000,670,-1000,1000,551,-1000,702,-822,149,-142,-68,-718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00676() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{-175,-547,333,638,681,609,-1000,-925,631,-1000,346,467,1000,39,758,-1000,-1000,-959,-1000,668,-47,-372,-1000,-931,192,-1000,170,-1000,-1000,1000,-1000,-510,975,-800,-463,639,-789,-1000,-1000,1000,69,1000,-847,890,-374,-158,-1000,-597,1000,-200,18,23,-1000,-401,-111,73,-526,1000,558,1000,1000,-142,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00677() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{-637,1000,1000,59,1000,-33,220,-744,592,112,-684,965,805,866,-629,1000,-1000,-1000,1000,1000,-747,-460,-577,-499,-722,1000,831,-153,590,1000,696,1000,-329,1000,1000,-419,99,1000,-316,-929,234,-173,-609,-411,334,1000,519,-289,428,-135,-1000,-1000,274,-872,-721,473,-250,24,366,-1000,437,-622,-682,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00678() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{-719,-258,1000,579,419,1000,-1000,-646,1000,-1000,-1000,462,-22,735,-784,-305,-868,161,1000,-376,-237,169,513,225,1000,-1000,874,-43,-1000,773,-56,-377,-421,-3,794,-487,-1000,-767,-1000,468,613,-747,-173,290,-363,-176,37,833,147,1000,-990,613,201,-452,-133,-75,38,782,535,494,1000,185,1000,-822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00679() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{8,-234,-853,281,446,364,-268,-295,82,-156,-325,74,105,761,31,1000,-249,-710,171,-246,133,682,-18,-785,-386,-65,-359,-400,-282,-316,-816,865,684,-670,237,-112,-379,-489,-798,-48,-434,-423,398,334,-444,-1000,-1000,202,504,380,-442,-861,11,-473,-604,-19,-254,745,564,489,460,475,661,-193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00680() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{-117,928,9,251,8,188,580,65,1000,1000,-1000,-171,568,329,-419,-651,-758,-468,1000,183,-1000,-1000,309,-298,-753,409,1000,-167,392,-869,1000,378,-451,356,1000,835,205,776,614,-882,1000,-444,-1000,519,307,1000,1000,844,-1000,-993,-1000,-119,718,-362,-692,1000,1000,-407,-51,-1000,-278,-1000,-280,551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00681() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withHours(int):org.joda.time.Period",
            new int[]{475,411,-763,-612,-81,-326,1000,1000,88,1000,300,607,804,527,-300,308,-313,-793,231,-927,-124,708,286,361,-1000,1000,328,745,833,-1000,1000,1000,1000,1000,222,635,448,1000,450,-748,498,-775,-774,510,1000,-493,848,-55,-1000,-1000,284,320,-46,-993,-396,-156,417,-1000,-1000,-1000,-52,104,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00682() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{1000,-196,1000,363,-1000,-1000,-1000,-290,-688,820,225,1000,62,1000,1000,1000,-790,343,-1000,1000,-809,-1000,-1000,432,609,1000,-539,-817,-132,383,-1000,1000,112,1000,-651,-531,-787,-1000,1000,-903,-452,303,-1000,-698,1000,-795,1000,-1000,265,594,-494,-1000,1000,365,-1000,57,-1000,-1000,-62,-70,673,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00683() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{136,-68,560,803,-561,493,-475,-408,-892,-513,799,907,-778,430,771,356,-126,436,-643,653,916,631,353,756,-141,431,-521,426,118,571,473,132,-318,-455,-82,708,-413,-217,145,437,66,-667,503,-637,171,64,580,-455,-182,494,531,-328,-305,222,-412,-961,303,-367,11,-794,490,-912,302,-169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00684() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{677,-804,-77,-535,-809,-359,265,606,-144,130,-377,347,-176,-978,-896,-253,-528,112,179,799,413,274,-648,931,49,754,-34,260,-35,-877,625,-319,-611,674,43,293,191,-279,773,-20,456,683,-738,-931,-476,103,913,-106,580,-172,445,833,-634,443,-246,-80,-599,782,-341,481,-797,-187,469,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00685() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{-342,-975,586,850,65,755,130,-603,-5,-615,-808,885,234,-945,-226,-58,-322,-817,423,-745,512,-130,-108,-36,-459,330,712,-383,-220,651,-62,26,332,-993,717,-138,-149,70,930,126,-567,-171,170,719,636,-725,-456,-746,-468,491,422,996,170,-445,115,-673,651,-615,715,-467,125,145,-180,818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00686() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{-552,-846,729,482,4,398,840,-41,213,-342,429,-983,-335,-950,-212,-140,461,344,767,-931,345,-200,-816,385,549,473,-738,-814,838,937,287,154,-780,204,-981,-860,11,-754,-956,509,-507,-7,194,342,-700,901,-526,-194,-189,299,-519,-282,-637,590,-749,878,-135,58,-375,-903,-448,594,798,-986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00687() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{152,-413,247,679,671,-909,38,36,-616,-158,369,261,-63,701,981,-590,544,110,931,926,459,-533,194,-706,-206,253,688,796,435,-133,-381,352,-301,891,415,-98,-334,168,-860,-917,-369,-469,-757,-937,-967,-742,798,-715,-640,416,-58,643,59,-855,150,-881,-742,602,-817,-727,-764,847,68,-850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00688() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{885,-475,1,-463,-784,-910,549,-617,688,71,-881,169,-207,891,-86,435,-829,20,-948,318,197,-454,-894,554,512,-119,-183,-647,-931,-161,-283,456,-849,154,-292,122,-899,107,332,134,-971,432,-624,-27,-341,869,-203,260,638,-139,-521,-738,126,737,267,831,-681,-562,-57,-533,625,-651,-698,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00689() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{-26,-798,534,-566,696,-46,-507,-235,914,-260,545,-422,-869,-289,-128,293,-314,-477,602,-456,341,987,969,-641,972,653,-473,-886,-854,-457,888,50,-738,-946,746,-419,219,699,626,-73,93,-216,747,717,-806,-600,-322,-898,-942,-844,28,-328,413,1000,350,-12,-29,124,599,-86,6,506,77,79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00691() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{831,-207,-87,-722,550,-1000,709,-674,829,651,736,-267,-510,924,-1000,-431,469,-780,-287,-529,-1000,-915,-362,-406,-43,-632,516,-148,-1000,-1000,118,872,-873,-849,7,579,-1000,949,474,-166,150,740,578,1000,-36,653,-916,573,1000,-698,-1000,165,524,631,1000,763,125,86,852,492,1000,349,-145,931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00692() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{704,150,301,1000,1000,-469,833,-388,-516,-249,633,18,60,-558,1000,-413,914,212,805,366,840,-725,648,263,-240,-275,712,615,459,628,-5,576,127,419,386,-303,-467,363,-1000,-903,-688,-947,-240,-285,-634,-815,159,-641,-1000,537,-370,60,503,-1000,322,-825,-323,55,221,-1000,-206,978,-260,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00693() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{-790,688,-720,-34,-48,-319,93,-675,386,837,-571,-506,238,-965,-223,126,539,-946,824,-659,-623,-1000,-804,-1000,-15,203,69,-959,79,248,639,-36,275,944,180,317,-993,-409,571,330,452,258,105,635,382,632,-187,-340,-51,-877,-715,-407,546,-279,-309,324,-252,124,476,1000,85,1000,51,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00694() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{478,-389,111,640,1000,1000,497,-1000,-1000,-1000,216,745,1000,1000,379,-57,17,834,932,185,777,-1000,244,300,-776,82,-1000,1000,740,622,-1000,359,1000,838,-862,-351,-207,-279,-753,-1000,-1000,-1000,-794,-263,938,-487,-369,-484,65,1000,-13,397,111,-918,799,1000,1000,-1000,-409,-1000,180,-863,-1000,350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00695() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{-250,532,303,837,91,-910,144,-279,-594,480,1000,-1000,-906,216,141,-582,1000,437,1000,-1000,-1000,106,601,554,33,1000,-1000,-101,331,1000,743,865,458,393,-1000,308,-120,-215,-1000,241,-971,-820,1000,650,1000,-1000,-988,-100,-370,-813,-521,-738,126,462,56,542,-270,229,-174,-533,24,1000,1000,-456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00696() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{-655,457,-720,-1000,306,197,1000,-510,1000,-145,-718,-506,-432,-531,-189,892,-1000,-1000,-1000,-659,995,1000,690,-13,-67,-1000,69,-1000,-596,248,1000,-1000,-1000,-1000,499,1000,-750,1000,134,135,-718,-100,12,-196,-1000,1000,-1000,1000,511,765,-42,121,-1000,303,1000,79,-545,-218,1000,-6,931,-322,1000,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00697() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMillis(int):org.joda.time.Period",
            new int[]{-819,184,990,758,239,19,880,-379,317,1000,94,-1000,-1000,55,-732,-1000,1000,-447,1000,-868,-945,-1000,1000,-639,-1000,273,-789,818,-140,115,1000,695,-633,-573,61,1000,126,763,-1000,-301,-513,-908,1000,1000,1000,-586,-1000,1000,-463,463,-1000,-511,-1000,599,1000,120,474,1000,37,-524,1000,-47,1000,916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00698() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{482,-790,-110,-357,-1000,949,-746,-1000,1000,-700,-47,-835,96,-817,-1000,1000,477,-1000,-1000,397,-1000,-504,-650,-1000,-328,-49,-626,-1000,523,-33,609,944,-1000,-937,-1000,225,-1000,-1000,-90,-640,321,-271,243,-1000,-874,872,377,-732,-649,-580,589,729,1000,682,408,206,-484,562,-671,-110,-103,366,-215,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00699() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{-640,724,79,530,759,43,-690,572,338,-608,-130,-932,-461,103,161,958,-958,876,59,-176,252,-771,-613,592,223,781,861,643,-42,-705,326,-17,734,-113,993,8,654,639,57,-411,-22,-48,-383,773,936,423,-880,191,-255,749,-300,435,-716,-741,-288,138,540,-5,-455,232,-673,989,343,997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00700() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{55,-705,220,901,586,675,942,-932,868,917,670,195,-500,-62,54,-760,-222,407,770,-373,379,681,-195,-998,-611,913,485,116,359,-5,-176,229,-219,517,-363,-686,938,-295,566,545,-571,442,-321,113,841,-113,-385,-791,994,-21,399,-811,24,131,-29,-606,-887,-175,278,-125,-852,-521,288,353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00701() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{-623,-910,-683,-112,369,886,-994,707,-927,-858,-425,-705,-12,-651,-135,-902,523,-290,954,22,-336,235,763,-549,-990,-324,348,-779,-833,774,753,-255,-782,-189,-817,-609,534,-748,960,398,526,373,-764,197,-860,237,-261,778,819,68,-997,942,-480,190,-568,717,-96,29,-652,-2,290,357,718,556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00702() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{153,984,-340,-24,-977,369,-991,-238,452,269,241,-72,432,467,-116,663,76,-142,-845,372,-292,748,-963,326,-420,-937,260,-582,-633,370,703,142,316,90,-340,799,458,108,-941,-878,666,-919,288,-911,-523,193,-449,-525,-391,-154,858,-107,857,-165,-344,-508,707,-89,-86,-695,-644,-652,-985,-728}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00703() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{-153,225,-115,-312,-933,-516,-699,807,-230,-378,-880,-34,838,142,360,90,-841,-435,-408,-380,-581,355,-434,387,543,-578,661,-568,-753,915,-979,-320,903,506,91,279,430,162,994,-370,-706,-465,-321,665,873,536,-891,762,-588,-626,874,-165,-425,-560,578,-401,225,743,-891,798,-527,-675,-554,-744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00704() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{244,-956,87,16,682,432,-664,-446,-446,979,-274,-933,-534,71,662,-821,207,114,8,64,815,887,-208,932,59,-938,-548,-879,959,-998,136,-393,-321,440,-566,654,687,-544,-369,540,412,-724,82,-191,808,864,-393,-641,205,14,-17,798,-381,-304,507,-13,302,-199,411,-553,721,-903,438,419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00705() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{-5,343,-314,-141,-64,-948,-581,-344,-520,102,908,-911,-498,-658,739,-587,-497,834,-413,94,232,-26,372,-907,-991,-481,-896,740,848,871,903,-272,173,-581,532,-335,503,590,-491,848,789,341,355,415,-705,295,861,912,865,-572,-590,427,-784,-704,300,920,516,167,-189,-839,-112,-936,-694,755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00706() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{757,-227,-941,156,-57,-861,-314,276,-692,-402,-928,304,-604,847,-497,-276,-4,-245,366,133,859,-766,192,453,873,-406,112,-615,-42,137,854,-447,635,216,-717,684,-213,-203,202,381,-537,-324,637,572,773,947,-868,-446,863,45,-249,-287,-220,-237,-603,-563,-371,-12,-165,551,826,613,372,-340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00707() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{727,1000,220,1000,-177,-1000,942,-932,-542,-73,670,-703,-710,-62,54,-771,-1000,817,-53,-693,255,676,1000,-998,-455,-990,-460,686,896,573,-485,-653,1000,255,650,-749,1000,1000,-587,183,-637,594,185,-220,-165,-896,595,1000,1000,-384,399,215,-295,764,486,-61,474,-175,-282,-657,125,-1000,-580,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00708() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{-447,-82,293,343,868,614,-580,-453,1000,-958,-201,-1000,-1000,-287,-190,1000,-950,1000,-345,22,77,-1000,-786,-164,-16,-324,229,436,1000,-1000,1000,641,-782,-1000,715,-690,151,85,-186,-63,255,-276,-168,197,-860,237,-209,-1,819,68,-513,1000,-479,-682,-56,615,407,-81,-484,-48,94,1000,1000,556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00709() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{-556,-158,-53,241,38,1000,-1000,556,-237,472,139,-1000,-472,107,1000,-681,-903,1000,-81,-135,779,-149,879,1000,473,-462,-240,97,-297,-1000,241,-221,1,467,882,228,1000,226,-533,-222,333,-1000,-485,711,1000,650,414,2,103,993,-765,1000,-884,-824,-148,266,636,75,537,-766,328,-897,450,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00710() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{-68,-49,-949,363,252,130,-1000,1000,1000,-1000,-275,792,-856,492,1000,694,-965,-477,-507,278,412,-1000,-250,1000,676,-301,1000,107,-925,-101,587,646,-247,-498,822,73,-923,2,-162,-505,-376,74,237,955,-610,168,-481,198,873,616,-407,-451,508,-306,-1000,601,-725,902,-310,1000,-51,1000,1000,-35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00711() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{617,-937,-968,-333,-636,410,-1000,289,1000,-1000,-301,627,-202,222,-335,-264,-690,-572,-1000,568,-1000,-1000,372,-489,571,-837,-599,-103,-1000,-92,-392,951,159,-19,244,-126,-183,150,-395,-694,-1000,-332,295,108,-962,-1000,-273,1000,-75,616,561,-97,984,-55,-502,105,98,1000,-685,383,91,-345,243,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00712() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{-218,-147,259,757,-1000,302,-840,788,-940,-374,-284,-771,-729,350,-109,-570,-1000,1000,207,360,185,-530,-1000,-1000,318,-151,-731,928,1000,-802,-85,-254,695,569,699,-949,1000,1000,-186,493,-274,-890,-117,229,31,149,837,1000,522,-478,-1000,1000,-1000,523,-653,-31,1000,-834,-1000,1000,608,576,718,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00713() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMinutes(int):org.joda.time.Period",
            new int[]{1000,-197,-180,285,153,-843,888,-350,-821,1000,393,649,136,713,-137,-735,874,-989,292,-1000,945,735,-1000,1000,839,-1000,-207,-853,-1000,116,-530,1000,11,-363,118,1000,-186,462,-930,-122,297,-805,453,43,622,-14,478,192,-763,-752,984,-1000,1000,789,-685,-37,-1000,1000,806,-790,346,-1000,170,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00714() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{-838,332,-796,255,-1000,841,16,-1000,-1000,1000,289,642,280,512,168,607,-1000,-1000,818,-1000,-1000,966,-843,298,-203,197,-653,672,656,874,-135,866,47,-1000,-239,-905,-818,742,152,-1000,173,-212,1000,1000,632,468,32,-465,-690,877,275,-944,-357,-235,149,589,713,-40,1000,-2,-1000,12,493,-426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00715() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{-5,-310,464,853,-312,963,338,32,617,281,922,642,-387,69,13,-1000,-881,579,818,-555,320,249,-101,-116,-147,769,-956,-472,656,-60,-411,-38,-490,-216,-898,775,-849,-424,76,-183,-575,-541,290,-187,-92,158,835,-465,-347,-110,454,-944,274,-915,-184,668,465,726,599,-2,-794,-776,-126,-426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00716() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{308,259,-13,208,-257,983,-699,-961,-926,739,650,167,-629,369,-311,361,-855,-900,198,-25,-817,724,-502,29,-530,789,-403,-60,77,990,-575,-331,959,294,-76,-900,-167,-408,474,-705,990,-395,167,969,794,667,755,968,-136,-419,-212,-128,-485,-759,377,899,876,933,330,232,-702,-668,-32,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00717() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{-382,577,-402,-388,6,350,399,-800,-912,-706,101,-250,-239,-612,765,-38,800,943,787,-18,-788,-160,687,-24,838,221,957,187,-84,108,-875,562,-681,840,-371,-932,-497,374,781,-333,-210,-455,152,-412,568,-815,-982,-977,-780,631,193,631,160,609,-505,-603,214,-267,-594,-765,774,-58,54,-479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00718() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{-557,190,-705,-86,-458,-178,392,-422,-786,955,410,-842,214,-399,125,193,-66,301,670,-825,-268,-71,18,541,-155,-191,166,816,-786,252,-205,640,-427,-987,-352,399,-551,365,507,-928,-669,488,743,549,215,-297,-307,-833,-42,712,814,497,-557,452,501,515,-566,-573,732,211,-862,465,314,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00719() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{-937,720,365,-813,-524,445,923,372,-34,-436,-935,-721,989,395,775,-809,-943,30,944,-611,-748,780,347,867,166,-90,-775,395,981,409,175,-231,473,965,463,-845,964,-511,-414,290,914,539,-79,940,-259,848,666,534,-269,749,-110,148,431,781,507,143,811,-10,-756,-346,-363,-480,-735,-119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00720() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{-564,-365,148,-381,-252,-312,-322,232,796,-353,140,-746,-459,-689,-358,-877,189,-378,149,-226,-245,284,-87,-702,523,552,-771,-570,-607,-63,-42,-785,-932,940,-352,-786,353,-858,-220,464,465,972,730,-906,-833,309,-677,962,803,-295,571,956,-155,454,-923,743,452,13,-72,627,721,-675,-555,-75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00721() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{824,700,-903,197,-359,303,956,-296,-657,-958,102,-789,-844,49,322,-342,801,65,815,-356,-861,176,-442,638,-120,166,329,-141,-844,519,-382,479,723,-486,96,-168,886,38,-40,158,842,-236,568,-918,-919,-839,62,514,725,415,350,937,239,839,784,-842,813,543,140,-495,-82,268,-908,-794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00723() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{1000,494,3,133,631,434,-426,41,-979,-511,1000,1000,-841,-191,-340,636,3,-971,-319,516,258,-539,438,889,-1000,401,363,502,-390,837,-1000,-337,1000,461,-45,883,-150,-632,1000,-1000,156,-353,-1000,1000,1000,-176,1000,758,132,-741,11,-1000,-1000,-627,1000,571,-655,817,-178,531,-202,-16,78,-280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00724() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{455,290,420,-299,-561,51,1000,-516,-999,-728,849,-261,659,1000,419,193,-66,-973,670,241,1000,-364,1000,196,1000,-445,1000,-113,-64,-712,364,-269,1000,-436,-421,-208,-544,-270,1000,-39,-1000,-1000,81,-494,542,-796,-307,-778,-300,1000,829,568,380,-1000,1000,210,169,-18,1000,-412,-491,1000,-358,371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00725() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{-409,690,-1000,-813,730,-680,513,-1000,-1000,-791,-1000,-1000,-1000,381,-399,-127,656,-1000,-786,-763,-1000,395,410,1000,442,-1000,247,544,-470,-974,-887,786,1000,-767,839,-449,1000,-55,-661,-370,1000,818,-567,114,-1000,-391,-1000,365,1000,1000,-620,895,-452,1000,879,-908,-222,75,-554,6,1000,-908,-1000,-780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00726() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{-460,534,485,62,255,239,267,1000,1000,1000,801,296,1000,272,1000,-457,-175,1000,617,-717,1000,-1000,-669,-236,-380,259,-1000,718,1000,1000,1000,-35,-1000,995,-832,-1000,-366,435,626,-496,608,-193,-1000,1000,1000,-787,1000,1000,-762,16,1000,-495,-1000,-44,-678,512,-974,308,-973,-888,-1000,842,-63,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00727() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{-1000,-626,-573,461,-217,975,301,-422,181,1000,218,1000,1000,770,-251,765,-1000,-1000,1000,-1000,262,1000,-397,-531,-303,-261,-1000,700,1000,1000,539,664,305,-1000,-561,-1000,-1000,1000,105,-596,187,-163,382,1000,612,1000,654,-140,-986,1000,330,-1000,-574,-1000,-763,1000,144,-168,-1000,501,-1000,478,1000,398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00728() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{1000,-95,472,487,-768,-217,81,-767,-1000,-1000,571,-340,-1000,1000,-1000,-587,-1000,-1000,-1000,-902,-1000,-864,560,658,297,-877,1000,-133,-1000,-812,-1000,-929,1000,-578,-132,210,784,-1000,186,-1000,12,38,-829,-406,-241,602,-906,1000,1000,-31,-1000,842,-1000,290,1000,285,-466,387,737,228,-702,-1000,396,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00729() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withMonths(int):org.joda.time.Period",
            new int[]{-1000,-1,-1000,-1000,1000,-1000,-430,-1000,-1000,-729,-1000,-566,747,202,-1000,-986,-912,-1000,-1000,-920,-241,-299,1000,827,-303,-1000,944,214,1000,-602,539,-232,1000,158,1000,1000,602,1000,254,-1000,-477,1000,-1000,1000,-274,228,-226,647,111,1000,-973,-1000,-900,-358,955,1000,-1000,45,-631,1000,1000,-143,93,398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00730() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-714,266,-338,915,-224,-927,733,-708,490,-420,-82,660,-646,-125,431,359,289,-513,9,510,-379,120,-236,-492,-912,-897,83,940,-908,576,-115,405,99,438,911,-874,-893,-594,-75,-23,-33,121,736,532,-979,-803,727,-880,976,-663,-734,-498,229,206,-224,-253,-15,-387,-466,-840,-609,740,-693,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00731() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{138,570,739,-5,-909,-346,305,-203,102,141,137,764,-490,860,571,558,-277,-703,-664,-200,-649,-964,-853,645,826,87,586,405,-900,-736,988,-651,206,-175,400,-759,516,838,-71,867,399,-588,-245,846,-373,798,-67,345,365,682,755,970,883,490,844,77,-804,417,477,-985,812,85,634,-609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00732() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{876,-190,-716,164,201,865,656,-313,-472,-621,-749,463,-495,67,-525,-582,379,964,-536,258,861,-581,18,-36,33,134,265,-367,143,-757,54,650,-568,432,-494,533,109,402,295,533,-491,-404,-707,-913,85,45,998,-439,383,-571,813,-629,447,-987,700,521,-805,969,-30,909,219,-188,-934,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00733() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-338,648,867,-622,-292,93,62,-289,127,824,683,134,-411,-69,-328,471,-383,-415,-673,24,575,566,-849,-337,-749,-676,707,232,863,-982,-539,-315,-472,970,-80,741,-713,275,640,574,625,-87,-380,71,-906,-320,466,533,-877,-555,-660,344,-608,848,121,-901,253,-617,510,-51,593,27,856,513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00734() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-740,29,372,615,725,325,107,-693,-7,132,549,163,-50,-293,61,676,-683,30,474,739,-852,-814,131,642,456,-381,407,809,915,113,-672,-424,861,-684,-721,-288,-174,-14,-998,-896,705,691,-921,51,-682,691,932,936,614,-78,7,-276,-958,-412,231,664,-269,-262,-865,-129,-967,354,-939,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00735() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-859,-510,294,-254,-6,61,551,340,1000,-625,-677,87,877,-322,-334,-1000,208,-892,-405,-114,-530,-904,1000,-252,-933,-357,206,692,-129,121,144,-93,-134,-478,375,338,500,1000,-725,-49,72,85,-439,-607,-584,142,-208,938,811,258,727,-425,-307,-785,828,-665,-463,454,-46,-374,-146,-509,-447,-750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00736() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{609,453,-266,-611,-1000,-131,287,528,-494,624,164,1000,-520,-524,661,-564,-220,-62,-159,-184,-1000,671,-887,193,-519,1000,970,181,448,-562,204,-296,-518,924,1000,-67,828,964,-500,883,1000,-323,-1000,-296,-653,-274,-1000,1000,-378,-9,-609,-167,-638,1000,-1000,1000,-249,1000,-860,-184,-1000,-365,633,-903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00737() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{876,-293,-612,347,-319,246,350,11,-654,87,-749,557,-325,129,745,-7,-271,47,40,599,-1000,1000,-856,225,-518,-6,1000,-72,3,-333,328,229,-896,173,951,234,498,23,-332,1000,-79,810,-533,-913,-32,154,-566,758,578,-27,-683,42,-278,966,700,668,-353,364,-406,909,53,211,1000,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00738() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-532,474,396,-905,-649,-932,-343,-561,-801,294,-143,392,-314,941,-876,258,-62,-1000,857,796,896,121,-611,626,-1000,-1000,413,-731,-259,-613,-263,-114,392,1000,923,856,288,450,54,-434,23,-864,386,47,-957,-829,504,-359,126,-160,-504,-184,-1000,385,943,-330,512,90,533,217,-560,873,1000,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00739() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-252,-527,867,-800,-902,-1000,-1000,-659,1000,662,182,754,-629,-69,18,837,-619,-900,-411,75,-918,716,112,581,-1000,-1000,-501,-202,-1000,-385,-539,-315,-45,-897,905,741,-713,-1000,-639,-66,-469,-325,1000,508,-376,-509,-1000,213,-877,212,-142,1000,-1000,-354,-580,-416,1000,-371,-287,220,-241,1000,922,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00740() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{953,-607,293,-549,401,186,-357,-949,-39,72,810,-988,-518,1000,0,670,-267,332,515,746,1000,1000,-1000,932,-307,-320,-8,-41,1000,-1000,634,406,-684,229,-1000,1000,-555,-625,-729,1000,-177,916,537,293,-89,179,229,636,-875,-879,-146,0,-1000,-595,1000,-1000,177,615,1000,1000,1000,788,975,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00741() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{761,-677,243,-266,-1000,-313,-735,-432,-23,485,-870,1000,-65,-225,1000,769,-849,-677,-663,428,-1000,1000,-928,-685,-1000,-1000,809,-307,-1000,-425,-614,91,-1000,1000,1000,201,516,-866,-527,1000,-1000,736,-290,-559,53,222,-326,1000,-6,-38,-643,925,-620,882,676,813,366,-417,-1000,54,-75,1000,1000,-107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00742() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{240,-727,-721,343,-99,325,479,56,279,-383,-495,-535,-1000,1000,-745,-428,-87,547,109,739,533,-93,-393,642,-874,1000,12,-617,117,-816,1000,742,-327,223,-123,1000,-534,-381,-510,642,245,370,980,-1000,-707,-468,932,-967,331,-103,-299,-276,-78,-209,61,-741,674,424,1000,633,679,316,-939,-210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00743() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{141,642,909,-1000,-1000,-1000,-1000,-949,248,1000,1000,973,644,-1000,-492,1000,-267,-1000,381,-457,-943,802,97,-301,488,-320,-1000,830,-378,448,-1000,-1000,-684,-1000,562,-405,-555,-935,-522,-1000,727,-639,571,1000,463,179,-1000,618,-875,1000,-449,1000,-1000,76,-1000,-1000,543,-98,-776,1000,-675,316,-599,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00744() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{567,883,-399,-209,-342,933,1000,241,-1000,-98,-194,-574,-1000,1000,-1000,53,-560,898,41,402,1000,523,-1000,568,-1000,1000,1000,-939,470,-1000,1000,405,406,1000,923,890,-207,450,847,1000,249,-864,-893,-643,-223,-858,466,-94,270,-855,-196,-946,84,1000,1000,-670,-986,740,637,-533,-560,-798,1000,959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00745() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withPeriodType(org.joda.time.PeriodType):org.joda.time.Period",
            new int[]{-349,666,397,-893,-668,-225,-343,-519,715,202,111,533,-1000,-252,-623,-150,-338,-183,1000,-181,-1000,-341,419,-45,149,201,347,1000,-629,0,-400,52,-755,1000,923,-1000,-502,450,-752,44,-175,171,58,-301,-35,221,-1000,859,6,834,449,-999,-927,-354,-1000,-1000,46,216,-544,967,-289,1000,446,-381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00746() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{-194,-284,109,388,-10,542,687,921,-709,-1000,290,138,797,-1000,-266,829,-1000,505,98,-2,-855,468,1000,-240,340,-26,425,1000,-66,250,219,-340,-206,-772,-212,-1000,-69,427,-59,969,947,41,754,-152,-19,-639,-690,838,95,-440,-883,-371,121,-169,477,-474,462,24,-1000,-566,472,-177,-845,149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00747() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{40,-354,-203,-603,-304,648,1000,483,-969,9,126,-320,971,-383,218,-125,-421,-784,1000,-655,-333,-653,231,308,-1000,307,236,261,-917,-376,1000,-699,1000,-31,-238,-831,1000,1000,-482,731,417,497,595,23,53,-408,-1000,566,25,366,-1000,914,-1000,558,-539,-138,562,9,404,802,-979,8,-660,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00748() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{674,360,-360,992,-723,715,927,-836,-89,-977,375,0,-67,652,120,-834,118,-455,812,118,148,-855,-936,-200,-172,437,376,-620,893,-600,137,292,417,-111,131,421,905,-36,-336,-757,-97,66,893,-66,-342,45,-759,-938,761,-961,-136,304,-994,-545,83,-155,-281,-81,-609,-412,757,-65,-783,118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00749() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{179,782,-471,98,779,814,738,75,484,-365,-385,937,-468,-909,562,276,567,326,-785,-722,647,-305,-246,-148,-262,912,-165,-384,-161,-431,63,-233,-906,-168,-254,-469,-746,-826,359,779,665,-683,607,-332,601,247,288,708,209,-932,235,526,593,799,559,-216,-385,-241,-880,208,-70,121,713,-531}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00750() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{-611,388,791,-194,-458,-881,-267,688,214,26,628,-427,327,-260,-660,-131,-955,1000,232,-435,-900,-210,328,-285,-275,-956,938,894,-6,-66,790,117,594,468,593,277,-976,161,-408,-879,74,867,184,-973,56,-491,932,367,-304,432,-577,169,889,-664,352,-87,550,-618,896,-171,702,924,-953,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00751() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{970,-415,552,566,212,-462,-778,-656,-78,-394,382,157,512,189,-431,-37,520,989,-784,672,104,807,-891,103,513,-131,782,379,303,396,-629,415,-719,885,-706,-184,-848,-439,-169,-602,-305,821,-908,822,885,-871,212,348,869,-220,-316,-370,947,-746,659,-379,-238,-582,-219,163,-183,-204,659,-357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00752() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{40,-414,-891,309,-1000,-841,-681,83,307,-931,-672,836,842,736,222,-491,307,-520,540,-325,-785,-558,609,702,-270,-530,-216,261,79,440,-400,-892,503,-841,-341,630,-313,-940,-206,626,-562,-790,-519,989,53,815,-207,566,-808,366,277,-717,-588,844,-941,-138,-469,991,-791,904,904,-439,-65,-49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00753() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{-623,654,-465,-770,-786,-193,195,253,591,293,706,140,-610,-710,-36,643,-997,1,-293,173,563,600,-622,-160,639,-28,-769,503,-202,-406,-195,-871,-376,646,802,-614,-189,-564,10,-675,761,591,-562,243,759,83,-905,-608,227,-838,956,-953,848,-499,575,-359,393,-522,-468,-824,982,-592,440,903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00754() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{-773,-251,-398,401,-835,831,551,-383,-733,-494,717,-840,905,-745,-777,-785,-373,-247,445,-204,331,-325,-646,751,-970,603,931,920,-749,316,936,160,333,416,-790,-447,-111,328,-680,79,-480,990,-128,228,-493,-762,-937,643,646,-154,-807,699,-337,444,-267,150,707,-863,313,432,-689,-450,498,-955}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00755() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{906,-777,-409,543,-520,822,881,-902,-698,-542,704,336,-242,-439,-502,221,308,-780,-374,115,-696,674,-837,-180,-975,725,64,433,-383,243,-816,102,878,-337,-541,441,787,-634,226,-746,-460,206,164,492,-540,80,-427,898,615,-595,885,685,-768,-367,-852,755,-944,540,-3,-511,712,-94,-849,-96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00756() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{798,-562,830,-868,970,-722,650,581,-416,325,-463,899,607,707,990,906,452,221,627,27,-845,339,362,-530,-131,-554,-211,334,63,-592,397,-812,985,247,84,-733,965,741,114,329,978,118,125,529,-527,-365,-896,-854,-18,821,-661,-63,-774,-583,272,-986,-446,664,-89,691,-598,452,-996,-507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00757() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{20,-565,-1000,1000,-1000,240,896,-955,109,-1000,145,442,248,-66,-822,-971,206,-1000,-161,-264,-680,-323,-231,947,-861,365,-24,331,-234,1000,799,-253,429,-1000,-779,1000,253,-1000,-128,-127,-1000,-729,-492,964,44,1000,121,-63,-365,-625,1000,-193,-584,995,25,1000,-817,904,-731,63,1000,-821,38,239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00758() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{625,-233,614,-59,1000,-736,778,-100,-198,427,-1000,1000,-298,-1000,1000,382,975,667,-880,-532,350,204,335,-994,-700,399,-16,317,-180,394,362,-946,212,-75,265,-683,331,258,973,715,1000,-471,1000,-352,857,311,-395,337,27,236,-826,1000,430,678,1000,136,-879,83,-925,683,-1000,-142,234,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00759() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{-23,-764,230,574,-1000,869,-66,320,-1000,300,-1000,673,-1000,1000,886,25,426,-95,-748,-642,-443,217,-1000,-27,11,1000,-929,219,337,-732,-1000,-348,-291,-389,-1000,-1000,300,-212,300,517,-1000,568,-29,1000,713,-577,925,-1000,149,200,237,-322,788,-1000,-233,-1000,-300,-369,24,-664,-24,-458,-165,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00760() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{-137,128,-138,47,-285,715,1000,-412,298,-552,1000,8,1000,1000,-636,-169,-265,-849,1000,192,-563,-960,-34,112,325,-494,552,-1000,-306,-767,317,658,-1000,-446,-1,99,1000,241,-684,117,-508,-305,283,-207,-1000,-356,-717,152,-41,-46,157,-363,-1000,-1000,1000,-809,-451,820,561,429,368,1000,-1000,30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00761() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withSeconds(int):org.joda.time.Period",
            new int[]{-168,5,1000,692,-553,948,-116,700,-1000,86,-1000,527,-774,-47,400,797,-99,-887,423,351,89,1000,661,-1000,673,-1000,-258,-785,275,-1000,-671,-472,152,494,575,-1000,400,-1000,410,-1000,557,1000,-580,400,42,914,-975,-431,1000,-491,370,707,-1000,-1000,1000,-1000,-234,429,-1000,-1000,-1000,-1000,604,360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00762() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-221,-64,1000,-118,664,869,-1000,1000,111,1000,-611,-572,1000,-212,1000,458,-406,-1000,1000,1000,-994,-1000,-985,1000,-958,-1000,1000,501,-92,1000,282,95,-167,1000,-159,-742,-1000,94,173,1000,976,279,540,-1000,-280,-192,-720,546,129,557,172,339,-617,-392,-898,-1000,384,-955,1000,-705,-1000,1000,699,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00763() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-951,746,340,-485,-2,834,-913,-393,-798,237,-612,700,1000,-306,1000,319,-1000,-435,1000,-1000,-657,1000,1000,1000,-940,-1000,1000,877,-1000,1000,-1000,-232,-1000,-62,599,-1000,-1000,1000,107,-222,662,-190,442,-811,-42,-1000,-1000,271,35,311,761,1000,-722,754,-1000,-1000,432,-1000,788,-210,-1000,-184,345,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00764() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-715,150,-683,-305,740,948,-259,77,656,-65,-106,362,-445,507,-455,-607,-530,138,-156,-558,-728,694,929,-295,483,50,-526,394,-995,292,-363,640,-141,-640,955,-333,-989,391,-836,-279,705,193,579,-883,294,-685,-245,404,-462,982,-557,147,-459,813,-471,-897,96,-331,-405,686,-962,261,30,-483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00765() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-588,201,-816,592,-250,77,989,-857,-446,810,653,-393,209,100,-418,-688,965,-964,513,433,397,-864,-19,590,-212,984,-261,53,257,417,-523,-80,125,787,325,900,-965,186,-22,333,608,750,37,-44,77,-856,153,592,-771,-844,-92,399,281,981,32,623,817,-802,-500,900,-165,715,328,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00766() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{90,-964,-504,161,439,71,-706,-164,-300,-568,-364,799,641,-372,381,981,-836,-44,553,-807,-523,-53,508,-226,-878,-768,538,362,72,427,-790,308,-181,-800,782,-485,-648,-901,-518,502,-776,-124,-875,347,555,-907,-430,-155,712,-418,859,686,-314,-2,-460,-270,715,123,-382,610,-410,854,880,740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00767() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-746,889,-102,612,-200,-18,-56,-272,775,361,-758,-65,834,-596,517,48,-809,-13,637,-342,92,57,-703,784,-444,-76,150,-340,-968,541,577,-664,-723,594,-644,-966,-228,620,921,201,281,567,580,85,-200,-786,-882,-679,52,-291,-484,480,-58,-912,-790,-171,-52,-893,232,678,-740,989,878,-446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00768() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{884,445,212,112,-147,231,224,-964,-394,-51,738,-352,871,-251,127,410,-728,366,-791,-244,761,199,284,-710,24,359,-355,186,133,-440,-635,62,376,-687,-738,920,-792,255,-661,702,553,668,710,-71,324,215,-953,517,-47,-218,926,355,956,-87,90,535,-856,-194,-371,200,915,72,-676,908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00769() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-725,-382,-274,-683,-25,251,-151,-885,-2,-793,787,6,496,358,872,-759,-836,-867,359,-699,-383,582,622,-391,98,-485,315,933,592,-84,144,134,111,-591,938,105,-132,916,-842,-196,-569,-134,-359,138,-508,-687,-563,-886,881,-854,941,156,659,-795,-874,780,704,-426,706,916,56,565,294,-911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00770() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{107,343,192,-800,-53,685,102,849,770,715,-23,-406,417,-32,382,738,-481,365,-549,690,-302,669,306,-894,-184,-760,472,771,962,949,544,-498,-440,613,430,-458,-40,-254,911,-23,-800,-400,-30,548,-770,-531,-647,913,-171,-915,-806,-126,691,-477,-865,383,-303,527,-600,252,295,-606,503,-607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00771() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-591,-32,-564,-1000,580,1000,-1000,0,1000,-54,-260,613,-930,-31,-95,704,-1000,-61,0,-804,0,0,1000,102,1000,-1000,-264,0,-190,124,-745,1000,0,-1000,480,0,-691,-653,304,-1000,0,-564,0,-1000,-100,71,0,0,-321,1000,-793,-727,289,0,0,-600,-115,0,424,-944,-622,-541,0,-11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00772() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-715,-961,-357,435,-134,270,-343,349,43,-308,-325,236,140,-43,219,1000,-1000,138,890,-488,-1000,130,788,-554,-472,-546,-88,394,-995,308,-412,951,-224,-505,533,-1000,-517,-1000,-775,1000,-134,-218,-1000,508,522,-685,-785,529,-462,982,672,964,-917,813,-62,112,123,-506,-706,900,-172,1000,732,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00773() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{326,-821,163,-266,-533,321,16,446,-798,-91,1000,-612,-214,1000,-34,60,660,-592,-545,443,-336,-260,-483,47,-101,377,-428,90,713,228,-1000,791,-954,-1000,329,647,180,-974,107,886,389,-190,159,-114,697,656,247,516,-434,425,463,143,169,754,851,-905,92,272,270,-210,23,-184,17,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00774() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-683,214,212,725,107,32,224,-386,565,-37,-1000,494,1000,-857,784,735,-1000,-43,1000,-907,-273,20,284,626,-1000,-614,527,-87,-918,839,24,-449,-849,34,-97,-1000,-682,-10,-661,702,553,480,-32,328,188,-1000,-1000,517,550,-583,117,960,-278,-914,-1000,-360,-856,-807,-35,1000,-1000,1000,1000,73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00775() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-217,118,26,890,480,1000,1000,-387,116,-377,-16,-395,-1000,572,-923,-717,-1000,197,-777,648,-374,0,-826,-918,707,1000,-1000,-1000,-345,-530,-240,253,-28,-215,-616,984,1000,-257,796,777,1000,887,575,492,371,1000,1000,618,977,-1000,-1000,-665,400,37,203,968,-1000,0,424,-185,384,-741,-163,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00776() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-759,-489,-577,356,-1000,235,-539,43,191,231,-155,-156,24,139,32,519,-866,160,636,-92,-744,156,97,-444,-82,-170,-352,216,1000,99,-25,800,-135,-113,150,618,-199,-558,333,-1000,-325,-509,-571,338,250,-535,-416,-308,-811,1000,251,628,-763,814,86,245,686,-566,-519,675,10,992,647,637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00777() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withWeeks(int):org.joda.time.Period",
            new int[]{-417,-566,-650,-317,421,532,-59,-851,-389,-1000,608,427,-1000,1000,-1000,450,-401,958,-1000,71,-961,-119,943,-1000,711,824,290,383,-218,-616,-892,999,-72,-1000,774,1000,460,-222,273,147,964,-218,-1000,-202,946,1000,1000,-89,-94,831,-970,-432,363,1000,-164,499,292,1000,-608,1000,295,-882,-509,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00778() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{-560,552,844,1000,-650,-638,-1000,144,-307,-522,599,525,230,-1000,-303,-982,-759,-1000,1000,1000,702,-1000,-284,689,1000,235,1000,1000,1000,-321,400,1000,331,-446,-907,232,-980,768,1000,1000,1000,1000,-156,302,1000,-590,-1000,-1000,-997,133,-1000,-1000,-1000,816,-1000,-1000,747,-19,353,-1000,-942,285,-52,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00779() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{908,-662,486,-646,684,878,-90,-760,727,-816,-623,464,-696,-337,783,-396,-419,384,-926,-42,-426,999,-253,-337,-165,-43,803,51,213,549,193,-911,910,538,-95,-646,-190,-402,-313,890,-407,-410,-877,498,496,853,290,548,-263,-924,516,227,637,96,-60,311,-789,10,-520,972,-358,-643,-395,948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00780() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{897,419,780,451,5,-114,-595,-551,-194,396,-226,-515,-457,-678,245,100,-202,-993,874,-226,459,-384,-411,373,821,-893,744,-917,982,451,465,-231,632,-559,257,-702,-964,922,-783,271,564,-946,-77,-193,-517,379,-446,568,-120,860,124,-976,-444,-178,-147,-860,564,778,-352,-900,-406,-502,412,480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00781() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{901,773,825,418,776,998,597,306,-778,674,-332,-28,810,-192,-142,-94,-529,-932,-167,430,-214,-424,380,-364,712,837,-253,567,-596,177,-586,316,685,-759,746,280,429,585,840,-246,690,222,-405,212,759,81,-462,-816,-524,-210,-744,459,126,-76,426,-505,27,911,-616,-609,530,-816,912,-464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00782() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{-446,813,536,142,22,875,-31,-709,-411,117,554,788,-73,-934,-423,-794,-517,-281,113,772,103,130,745,-536,-787,-177,900,599,-642,-208,841,-507,-202,48,406,356,602,155,-533,495,219,26,-191,-922,855,-662,591,292,162,389,-80,-490,700,926,-825,268,458,582,670,-692,289,866,-981,705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00783() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{-337,-775,514,-886,-812,-495,-1000,504,-207,302,727,-647,-167,-491,610,650,-140,-36,-411,-512,-712,371,332,-56,343,-89,-811,-534,-618,-907,717,152,618,637,206,-97,984,641,487,322,-554,106,13,446,-607,-6,-281,-66,921,-538,481,154,804,609,-868,777,-277,176,942,943,946,977,222,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00784() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{130,434,484,51,-816,932,-589,-907,-609,-441,706,643,523,-815,111,-870,-905,-755,414,-412,905,-682,-38,638,548,-762,30,986,339,-888,702,634,-32,-142,1,-398,-39,-693,-160,835,536,769,-862,220,820,54,877,-891,-454,-608,267,-353,-658,407,-736,286,481,-602,676,396,615,523,826,602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00785() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{386,162,-674,-403,-938,742,-114,580,-621,619,-689,-839,624,193,800,-376,-343,-575,537,-245,-945,108,-852,121,-153,-160,95,-393,-586,-498,-709,-658,-954,235,320,-306,372,-625,-463,-73,-580,-337,986,-652,-570,112,625,243,690,382,640,816,-350,-758,-230,408,378,-242,536,-181,994,-788,981,268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00786() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{153,454,-851,-355,-114,-650,887,115,-877,336,-252,811,-911,678,-195,-566,-556,-19,-1,846,493,735,253,-527,81,232,-915,-781,-63,82,-341,-77,-929,693,310,963,-360,642,481,490,-841,585,-340,-974,363,555,-535,658,132,-122,-686,-106,-106,690,268,-568,-273,-886,-430,-485,953,501,178,-659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00787() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{-552,832,-847,-916,138,-621,144,-508,150,8,412,700,-11,159,-137,981,739,-572,345,-547,787,-38,132,419,-151,407,956,985,-811,21,-806,-107,829,-234,912,40,-711,660,326,132,912,487,974,62,225,99,359,172,-99,-55,179,381,914,230,-614,-688,235,153,251,12,-966,-953,382,-354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00788() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{559,1000,237,248,901,192,315,-760,-191,350,-813,768,117,923,-614,-471,666,698,875,318,308,-952,226,-60,-165,87,136,-169,503,484,-563,513,17,-984,-432,750,-775,105,-938,291,-199,418,-582,-564,962,853,290,-88,-263,266,-822,302,-656,-660,94,-1000,836,-304,-275,-1000,-1000,-333,-109,504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00789() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{264,-192,1000,-73,90,-1000,-927,-922,-284,-881,797,531,874,-678,-875,17,216,57,393,-458,1000,-604,-258,1000,756,562,82,1000,379,-436,952,1000,325,-559,1000,567,-1000,571,518,-104,1000,1000,-1000,561,695,-101,21,-830,-571,-729,757,-1000,-954,110,-278,-614,-178,-292,-231,81,-406,306,-438,480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00790() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{1000,101,566,-875,-1000,-300,-659,758,-1000,-815,473,82,1000,689,-151,333,264,-1000,-34,-1000,401,-731,-819,1000,292,765,-209,697,1000,-1000,136,950,-853,1000,611,229,-914,493,1000,-927,1000,-387,-649,-897,164,1000,-318,-1000,-1000,-430,1000,1000,-900,-623,1000,-263,589,-425,-1000,-737,-1000,-1000,-483,-490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00792() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{685,581,488,729,21,-36,209,152,-106,-336,-525,-202,-374,1000,225,-152,13,277,330,-560,844,67,-863,266,336,-954,-20,-860,754,391,-123,218,-1000,-231,-348,884,755,256,-620,235,1000,-267,-100,-80,-119,463,-372,-1000,-940,529,521,-896,-1000,12,784,-187,1000,-16,-802,-30,-478,81,939,29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00793() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "withYears(int):org.joda.time.Period",
            new int[]{885,499,-1000,535,-938,-810,-1000,-946,-9,235,416,-296,248,364,-417,-53,824,-846,1000,-1000,717,-1000,-726,1000,594,-722,427,-76,1000,-684,-1000,157,1000,-922,-124,1000,-674,1000,470,-959,1000,-420,-754,1000,542,685,-692,-262,466,1000,-454,169,-1000,-823,-1000,-1000,1000,948,619,-661,-1000,-578,-222,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00794() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Period", DEReplay.run(
            "org.joda.time.Period", "org.joda.time.Period", "years(int):org.joda.time.Period",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
