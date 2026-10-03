import java.lang.reflect.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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
        final List<Path> temporaryFiles = new ArrayList<Path>();
        Object jacksonBean, jacksonProvider, jacksonGenerator;
        StringWriter jacksonOutput;
        String cliSource;
        List<String> cliOptions;
        Genes(int[] values) { this.values = values; }
        int next() { return values[(at++) % values.length]; }
        int pick(int n) { return Math.floorMod(next(), n); }
    }
    public static final class Observation {
        public String token, error, generatedSource;
        public List<String> cliOptions;
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
            if (method.getDeclaringClass().getName().equals(
                    "com.google.javascript.jscomp.CommandLineRunner")
                    && method.getName().equals("main") && types[i] == String[].class) {
                args[i] = closureCliArguments(g);
                continue;
            }
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
    private static String[] closureCliArguments(Genes g) throws IOException {
        String[] scripts = {
            "var value = 1; window.answer = value;",
            "function add(a, b) { return a + b; } window.answer = add(1, 2);",
            "window.answer = function(flag) { if (flag) return 1; return 2; };",
            "var obj = {name: 'x'}; window.answer = obj.name;"
        };
        Path source = Files.createTempFile("de-closure-cli-", ".js");
        source.toFile().deleteOnExit();
        g.cliSource = scripts[g.pick(scripts.length)];
        Files.write(source, g.cliSource.getBytes(StandardCharsets.UTF_8));
        g.temporaryFiles.add(source);
        List<String> argv = new ArrayList<String>();
        argv.add("--js");
        argv.add(source.toString());
        if (g.pick(2) == 0) argv.add("--use_only_custom_externs");
        int level = g.pick(3);
        if (level == 1) {
            argv.add("--compilation_level");
            argv.add("SIMPLE_OPTIMIZATIONS");
        } else if (level == 2) {
            argv.add("--compilation_level");
            argv.add("ADVANCED_OPTIMIZATIONS");
        }
        g.cliOptions = new ArrayList<String>(argv.subList(2, argv.size()));
        return argv.toArray(new String[0]);
    }

    private static Object closureCliReceiver(Class<?> type, Genes g) {
        if (!type.getName().equals("com.google.javascript.jscomp.CompilerRunner")) return null;
        Constructor<?>[] candidates = type.getDeclaredConstructors();
        Arrays.sort(candidates, new Comparator<Constructor<?>>() {
            public int compare(Constructor<?> a, Constructor<?> b) {
                return a.getParameterTypes().length - b.getParameterTypes().length;
            }
        });
        for (Constructor<?> ctor : candidates) {
            Class<?>[] types = ctor.getParameterTypes();
            if (types.length == 0 || types[0] != String[].class) continue;
            try {
                Object[] args = new Object[types.length];
                args[0] = closureCliArguments(g);
                for (int i = 1; i < types.length; i++) {
                    if (types[i] == PrintStream.class) args[i] = new PrintStream(new ByteArrayOutputStream());
                    else if (types[i] == boolean.class) args[i] = false;
                    else if (types[i] == int.class) args[i] = 0;
                }
                ctor.setAccessible(true);
                return ctor.newInstance(args);
            } catch (Exception ignored) { }
        }
        return null;
    }
    private static Object value(Class<?> t, Genes g, int depth, Observation report) throws Exception {
        Object cliReceiver = closureCliReceiver(t, g);
        if (cliReceiver != null) return cliReceiver;
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
        Genes g = new Genes(genes);
        try {
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
                if (!receiverType.getName().equals("com.google.javascript.jscomp.Compiler")
                        && !receiverType.getName().equals("com.google.javascript.jscomp.CompilerRunner")) {
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
                boolean closureCli = (m.getDeclaringClass().getName().equals(
                    "com.google.javascript.jscomp.CommandLineRunner") && m.getName().equals("main"))
                    || (m.getDeclaringClass().getName().equals(
                    "com.google.javascript.jscomp.AbstractCompilerRunner") && m.getName().equals("run"));
                boolean jacksonSerialization = m.getDeclaringClass().getName().equals(
                    "com.fasterxml.jackson.databind.ser.BeanPropertyWriter")
                    && m.getName().startsWith("serializeAs") && g.jacksonGenerator != null;
                boolean arrayShape = m.getName().contains("Column")
                    || m.getName().contains("Element") || m.getName().contains("Placeholder");
                if (jacksonSerialization)
                    g.jacksonGenerator.getClass().getMethod(arrayShape
                        ? "writeStartArray" : "writeStartObject").invoke(g.jacksonGenerator);
                PrintStream originalOut = System.out;
                ByteArrayOutputStream cliOutput = closureCli ? new ByteArrayOutputStream() : null;
                Object result;
                try {
                    if (closureCli) System.setOut(new PrintStream(cliOutput, true, "UTF-8"));
                    result = m.invoke(receiver, args);
                } finally {
                    if (closureCli) { System.out.flush(); System.setOut(originalOut); }
                }
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
                } else if (closureCli) {
                    out.token = "CLI_OUTPUT:" + Base64.getEncoder().encodeToString(cliOutput.toByteArray());
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
        } finally {
            if (g.cliSource != null) {
                out.generatedSource = g.cliSource;
                out.cliOptions = g.cliOptions;
            }
            for (Path path : g.temporaryFiles) try { Files.deleteIfExists(path); }
                catch (IOException ignored) { }
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
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.JSTypeExpression", "com.google.javascript.rhino.JSTypeExpression", "evaluate(com.google.javascript.rhino.jstype.StaticScope,com.google.javascript.rhino.jstype.JSTypeRegistry):com.google.javascript.rhino.jstype.JSType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.Node", "com.google.javascript.rhino.Node", "isEquivalentToTyped(com.google.javascript.rhino.Node):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionParamBuilder", "com.google.javascript.rhino.jstype.FunctionParamBuilder", "addOptionalParams(com.google.javascript.rhino.jstype.JSType[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionParamBuilder", "com.google.javascript.rhino.jstype.FunctionParamBuilder", "addVarArgs(com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSType", "", "isEquivalent(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "canPropertyBeDefined(com.google.javascript.rhino.jstype.JSType,java.lang.String):boolean",
            new int[]{-528,-528,-157,-1000,181,561,255,-290,184,359,-1000,1000,-621,1000,-368,-1000,519,749,-479,-587,200,-65,1000,-484,820,-156,-690,-4,805,-408,-162,-171,-876,-720,-865,1000,-596,849,807,-840,1000,-1000,1000,-485,-437,592,-271,-472,901,128,-1000,151,-136,1000,-849,534,1000,719,245,1000,570,-721,-584,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-711,-1000,263,842,688,-661,666,-1000,618,-266,1000,1000,979,-100,-113,-703,1000,996,292,-260,142,-670,-115,-362,613,-324,-1000,223,-1000,-96,-180,-766,1000,174,168,238,1000,-125,656,280,-39,1000,619,-977,919,-1000,-464,-153,1000,173,-593,-1000,-506,1000,-559,-717,-648,-332,890,1000,-980,1000,-1000,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-6,-610,-893,-568,119,-10,17,-599,-284,-581,-428,306,442,140,934,649,963,650,-138,-155,-864,450,-955,-199,734,-554,-846,-364,-208,10,-123,424,572,-607,385,818,-984,-849,825,947,573,837,755,-936,-674,-670,734,-895,450,849,-310,-499,219,-18,-867,417,-988,833,-203,-111,-955,-195,894,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-754,-264,539,299,-733,1000,84,-81,661,1000,-422,-612,-903,-540,-327,496,-1000,1000,927,480,-857,-1000,-488,799,149,491,-366,-1000,-543,-434,327,-17,26,919,89,-315,1000,-1000,-1000,-139,-393,702,35,-253,71,-376,-157,-1000,-237,9,529,-888,361,-361,812,-227,1000,32,109,-792,424,-1000,-439,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createDefaultObjectUnion(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{465,-970,-496,127,-559,56,-985,431,269,520,-36,-481,-249,912,-321,-945,-759,-909,-331,348,-319,-524,652,933,98,-712,553,677,-976,-774,254,-870,379,-767,69,-413,193,678,-78,-674,207,161,54,-176,876,-581,675,-559,-405,371,477,-37,64,189,-863,-264,-383,486,43,-349,-907,-813,348,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope):com.google.javascript.rhino.jstype.JSType",
            new int[]{-176,784,-827,260,892,89,-899,584,18,-667,160,199,181,57,-808,28,-185,160,-228,-800,-28,214,-458,995,677,-854,371,440,562,883,-969,-509,76,856,509,503,-237,37,625,46,-576,579,-828,-970,-670,-936,-543,-414,-919,864,830,151,-804,186,-468,-161,-949,997,51,-690,417,220,56,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope):com.google.javascript.rhino.jstype.JSType",
            new int[]{-961,-895,-986,122,-141,156,-77,461,756,-718,-296,620,551,442,-414,-104,901,685,962,-497,-814,453,-68,-847,-224,-492,-200,998,646,-13,80,747,487,-659,-990,531,703,704,-879,-534,971,649,-58,-269,411,201,609,492,841,488,-170,999,58,-514,-586,-197,-330,913,433,-808,-429,552,-49,660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{1000,92,-126,-885,-1000,-1000,-183,-850,-1000,774,-815,820,-945,145,-162,-1000,-1000,399,219,-327,-1000,-417,-303,-1000,-53,-1000,1000,115,-1000,-837,1000,-1000,109,643,-1000,-1000,1000,-660,779,1000,-989,985,1000,809,1000,427,1000,-433,1000,437,-233,-793,-471,1000,812,1000,302,-387,420,1000,-286,579,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{677,651,936,204,-732,-26,-694,-587,113,-454,-759,776,-702,978,-866,-822,-339,-141,-924,428,-346,-346,888,-568,885,-898,-223,315,-718,-736,-715,-470,-334,112,-902,-978,941,-541,678,706,-936,927,732,-330,-504,632,-936,-172,-62,340,-516,54,-194,-63,163,-774,-879,232,-957,-940,276,365,747,140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{598,332,-672,-578,976,-45,-102,26,-738,-96,591,-783,-258,661,-812,590,-390,184,-249,-382,506,-525,-867,-50,-66,804,874,-208,383,-356,-624,921,711,-889,489,-731,556,-824,149,-829,-114,263,558,-656,606,-33,-477,-547,-837,573,-920,-264,479,-160,240,569,203,44,-152,72,281,707,904,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{1000,-1000,-941,-333,760,-881,-240,1000,-1000,-1000,-368,-1000,870,1000,-403,-996,1000,549,-226,-302,-1000,1000,400,-523,-1000,1000,-606,-228,81,-1000,-312,11,82,1000,-13,312,14,-243,611,-154,582,486,1000,830,-1000,229,808,-400,-275,-1000,-1000,-356,30,-513,80,953,-1000,-567,1000,-400,-109,-682,951,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.ObjectType,com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.JSType",
            new int[]{648,-648,828,43,-207,-87,341,-1000,-93,-388,-358,1000,-320,-1000,1000,-746,57,-889,-1000,196,-431,94,-1000,-1000,-471,1000,1000,-170,1000,-44,772,491,422,-1000,-828,57,-83,-1000,-3,985,-833,-1000,-1000,833,-1000,1000,-441,1000,631,295,764,-1000,1000,-189,670,-927,978,-152,-259,-639,-715,-79,-85,107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{253,1000,-81,-157,-638,903,122,519,-230,-95,-746,-1000,87,-1000,-1000,-964,125,-661,-123,281,1000,623,1000,-1000,1000,890,-208,-648,-1000,-227,-373,1000,210,-787,1000,-739,29,-771,-695,914,46,-299,-412,1000,-87,286,-766,1000,98,-704,354,-1000,-283,-1000,-293,70,-257,-848,-1000,-1000,798,-1000,-475,938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{155,298,-76,-1000,-521,-325,-1000,-568,533,430,-1000,-219,-70,1000,627,1000,1000,880,-819,-1000,-1000,120,-1000,711,1000,-227,13,626,-53,-665,259,-1000,218,-142,380,-843,686,-376,886,-1000,875,-547,1000,1000,243,-1000,1000,-1000,1000,-1000,584,-1000,1000,-155,-159,-984,305,448,1000,306,1000,738,1000,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.ObjectType,com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.JSType",
            new int[]{1000,156,988,1000,598,218,-349,271,-669,661,1000,1000,-22,21,-935,-830,-708,724,-1000,-922,-972,-223,-271,29,-134,-131,1000,218,-771,380,-129,-267,402,1000,1000,-92,-574,-751,-318,1000,1000,364,533,205,-612,240,-701,-1000,-1000,-368,-327,1000,182,-1000,-981,-118,-501,-676,1000,964,342,202,-118,-111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createNullableType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-886,-506,894,874,-167,-916,322,784,345,-241,-348,-373,12,-387,-841,-381,-177,689,-864,309,414,-19,253,-230,564,-291,836,-55,831,154,-771,-516,446,32,965,-505,-225,365,-114,51,-269,124,-484,-14,-646,-467,23,-454,-553,-142,920,814,-369,749,289,448,493,-244,-233,-738,-437,-490,779,-560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createOptionalNullableType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{1000,-66,-742,139,239,-381,1000,-235,371,165,788,-818,1000,-1000,946,-205,1000,-1000,47,1000,58,1000,-279,25,682,235,-325,-793,-1000,-196,63,-403,39,-1000,333,-218,-780,-297,-145,287,-1000,78,-72,-874,-790,133,-81,682,129,313,325,57,-338,266,1000,-561,452,808,258,986,648,463,678,-782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createOptionalParameters(com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.Node",
            new int[]{-63,-1000,-191,-1000,561,1000,633,-310,-50,1000,207,484,81,341,-214,818,1000,-1000,472,-306,556,-284,1000,-1000,1000,-380,-836,-717,717,1000,-1000,-258,-1000,1000,-1000,1000,-460,-1000,-1000,1000,-1000,637,-1000,-673,-193,-187,-1000,1000,1000,1000,-176,-1000,-112,733,-1000,1000,-1000,122,-806,-580,-831,-874,-855,-336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createOptionalType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{244,-84,-307,-1000,-746,-418,510,-597,-1000,1000,288,66,158,48,866,152,-80,407,-1000,-466,-689,745,-1000,328,667,-1000,-44,-1000,-856,406,-766,-702,-38,-175,746,-554,-247,1000,-404,-143,-377,50,-337,47,-153,-1000,-621,-1000,391,-703,946,1,-937,-1000,-1000,84,790,-563,385,-1000,1000,408,-428,-17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParameters(com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.Node",
            new int[]{-140,518,653,-672,142,866,386,-773,-680,597,684,928,162,754,574,-483,-299,900,-595,691,-604,126,-335,645,798,859,-100,250,-589,869,-95,-211,-999,-669,340,-338,-542,-937,-815,338,-466,-859,50,-392,-782,-58,225,694,-622,411,-373,-858,370,819,-749,-394,771,-374,-363,-433,134,179,-29,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParameters(java.util.List):com.google.javascript.rhino.Node",
            new int[]{-1000,20,939,-537,-670,1000,802,-1000,-97,270,-421,-1000,-315,-2,1000,386,-1000,1000,-1000,-726,1000,-1000,-1000,699,-128,400,-751,-1000,1000,128,231,1000,-807,169,-589,529,620,-1000,-184,-1000,-5,-1000,1000,-694,-500,563,276,-1000,52,-1000,-432,10,-141,-772,35,-164,322,-768,-738,1000,-81,1000,580,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParametersWithVarArgs(com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.Node",
            new int[]{484,34,918,-611,-976,-577,708,448,403,-700,966,-407,-673,-851,534,-515,350,154,-731,995,-442,695,442,222,-317,13,463,-512,-532,477,391,-712,-332,-148,47,-899,-292,391,-993,-527,753,-371,-828,-145,666,356,-761,293,216,392,-55,270,-265,511,657,743,-570,-846,632,-271,525,-599,24,-308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParametersWithVarArgs(java.util.List):com.google.javascript.rhino.Node",
            new int[]{145,-800,964,-846,-580,-505,-8,-621,-320,670,944,-253,244,-174,-987,-603,474,-335,-566,473,-880,828,-277,727,964,-87,482,-750,-229,-158,990,-676,-927,107,-300,948,91,-847,-10,910,-759,-374,24,498,86,-417,438,-147,365,859,686,-181,-260,-256,381,721,331,889,-293,902,-259,935,-911,120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{-574,-1000,-547,-384,859,-692,1000,1000,548,-499,1000,-511,-751,159,812,-640,-1000,-244,-1000,334,361,-794,652,-532,670,-102,272,1000,-262,-896,-17,190,978,-388,-521,879,938,678,-869,1000,971,-494,-310,666,237,-785,-791,595,-1000,-1000,960,647,168,669,1000,1000,-360,159,727,-952,921,-10,-723,467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{-247,-617,22,-613,1000,-184,-456,193,-610,106,-175,152,633,535,39,-932,-1000,0,-1000,-359,-451,-48,972,806,753,1000,203,1000,299,286,-719,499,1000,582,-1000,379,184,-980,-68,1000,192,-283,-1000,-1000,105,-1000,-903,255,-1000,-616,-538,1000,-469,46,1000,1000,448,-1000,226,231,-457,-415,208,30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnknownType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{713,-1000,576,-1000,1000,251,-154,-395,744,-1000,-275,-1000,1000,1000,-420,-1000,-967,-40,-824,-1000,-803,-1000,1000,901,1000,229,563,1000,-1000,-96,-1000,115,134,1000,-1000,392,774,1000,-282,1000,-352,-828,-1000,-187,664,-1000,-854,-810,170,-1000,683,349,136,19,1000,424,-878,-1000,209,178,-693,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.AllType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{-1000,1000,-95,1000,-1000,360,1000,546,1000,-1000,-1000,-992,-1000,1000,-795,1000,1000,1000,332,-1000,1000,13,-1000,-1000,729,-800,-1000,-1000,823,-1000,1000,731,-892,394,-729,-1000,-1000,1000,-528,-271,-60,-888,1000,1000,-92,1000,-409,-409,268,1000,-70,367,473,-495,-1000,-400,593,-367,-1000,1000,1000,-1000,811,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{716,-1000,253,-855,1000,335,-457,70,254,-529,736,-977,1000,-56,594,-179,-1000,1000,-324,-1000,-862,45,1000,899,1000,954,88,1000,-1000,101,-1000,965,975,886,316,77,1000,776,-520,1000,520,-1000,-1000,-1000,-332,-1000,-798,-841,860,-1000,-3,578,-180,-189,816,1000,265,-1000,-1000,-614,400,-416,-978,-890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnknownType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{-45,517,1000,1000,-73,987,-456,-1000,58,99,-1000,-1000,1000,592,-1000,216,281,670,-530,-1000,-1000,1000,261,1000,993,237,-621,-260,-1000,1000,-968,297,-400,1000,-1000,-709,46,-1000,845,-2,-468,-595,-743,-1000,-1000,21,-599,-372,400,1000,-1000,328,-448,-922,18,-404,701,-1000,-715,1000,-869,-1000,1000,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{1000,1000,-377,1000,207,-8,752,-400,58,-518,-1000,-771,-349,1000,-401,1000,303,32,143,-229,-741,13,565,-673,18,-800,-1000,-1000,329,-411,107,535,-180,286,570,-897,-1000,-100,255,-271,-1000,-679,-252,1000,-92,21,-443,-409,1000,400,-538,-265,473,-495,-1000,-400,194,-367,117,1000,-1000,-876,-162,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{-1000,454,323,182,-751,1000,1000,-267,1000,-1000,-631,-1000,-1000,-825,-1000,1000,862,887,619,-1000,411,-228,244,-637,742,-1000,-1000,2,275,7,1000,-702,-553,637,-337,-338,685,15,76,208,306,105,703,324,-1000,-403,-297,-476,714,294,-998,-40,225,-695,-374,101,1000,-1000,-360,-372,729,-1000,27,801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{-1000,-1000,-311,-613,1000,-840,61,160,-1000,711,1000,389,828,-544,607,1000,-1000,-538,-851,400,-630,513,692,990,313,1000,203,1000,-28,-210,-1000,37,286,-792,-515,99,1000,-732,424,1000,311,160,-880,-1000,-656,-715,-730,-716,-520,-632,-121,466,527,708,1000,1000,459,-293,226,260,173,-418,-78,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.JSTypeRegistry$1", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{-928,732,-1000,-727,-959,43,1000,908,1000,-1000,-178,-1000,-939,624,297,1000,157,868,903,-681,1000,-305,-702,-1000,990,-1000,-951,-996,1000,-1000,1000,784,-303,-903,787,-650,-494,1000,-1000,-1000,-28,-138,1000,1000,1000,288,91,-901,811,731,1000,-2,1000,-633,-970,-835,513,610,-650,172,1000,-716,-907,874}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.AllType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{377,436,-581,1000,-437,-765,393,-374,1000,-298,-31,-1000,427,33,-295,443,1000,527,-906,-574,127,-358,-141,-1000,-474,506,-421,-340,-482,-1000,533,1000,521,-1000,-261,-540,44,1000,-951,-509,995,408,124,-22,-233,736,-531,-955,-76,497,-674,512,347,618,-575,-607,-150,1000,-513,1000,303,8,-424,-698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.NoType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "", "getTopDefiningInterface(com.google.javascript.rhino.jstype.ObjectType,java.lang.String):com.google.javascript.rhino.jstype.ObjectType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.InstanceObjectType", DEReplay.run(
            "com.google.javascript.jscomp.ClosureCodingConvention$AssertInstanceofSpec", "com.google.javascript.jscomp.ClosureCodingConvention$AssertInstanceofSpec", "getAssertedType(com.google.javascript.rhino.Node,com.google.javascript.rhino.jstype.JSTypeRegistry):com.google.javascript.rhino.jstype.JSType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.JSTypeExpression", "com.google.javascript.rhino.JSTypeExpression", "evaluate(com.google.javascript.rhino.jstype.StaticScope,com.google.javascript.rhino.jstype.JSTypeRegistry):com.google.javascript.rhino.jstype.JSType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.Node", "com.google.javascript.rhino.Node", "isEquivalentToTyped(com.google.javascript.rhino.Node):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionBuilder", "com.google.javascript.rhino.jstype.FunctionBuilder", "copyFromOtherFunction(com.google.javascript.rhino.jstype.FunctionType):com.google.javascript.rhino.jstype.FunctionBuilder",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionParamBuilder", "com.google.javascript.rhino.jstype.FunctionParamBuilder", "addOptionalParams(com.google.javascript.rhino.jstype.JSType[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionParamBuilder", "com.google.javascript.rhino.jstype.FunctionParamBuilder", "addVarArgs(com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSType", "", "isEquivalent(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "canPropertyBeDefined(com.google.javascript.rhino.jstype.JSType,java.lang.String):boolean",
            new int[]{-528,-528,-157,-1000,181,561,255,-290,184,359,-1000,1000,-621,1000,-368,-1000,519,749,-479,-587,200,-65,1000,-484,820,-156,-690,-4,805,-408,-162,-171,-876,-720,-865,1000,-596,849,807,-840,1000,-1000,1000,-485,-437,592,-271,-472,901,128,-1000,151,-136,1000,-849,534,1000,719,245,1000,570,-721,-584,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-711,-1000,263,842,688,-661,666,-1000,618,-266,1000,1000,979,-100,-113,-703,1000,996,292,-260,142,-670,-115,-362,613,-324,-1000,223,-1000,-96,-180,-766,1000,174,168,238,1000,-125,656,280,-39,1000,619,-977,919,-1000,-464,-153,1000,173,-593,-1000,-506,1000,-559,-717,-648,-332,890,1000,-980,1000,-1000,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-6,-610,-893,-568,119,-10,17,-599,-284,-581,-428,306,442,140,934,649,963,650,-138,-155,-864,450,-955,-199,734,-554,-846,-364,-208,10,-123,424,572,-607,385,818,-984,-849,825,947,573,837,755,-936,-674,-670,734,-895,450,849,-310,-499,219,-18,-867,417,-988,833,-203,-111,-955,-195,894,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-754,-264,539,299,-733,1000,84,-81,661,1000,-422,-612,-903,-540,-327,496,-1000,1000,927,480,-857,-1000,-488,799,149,491,-366,-1000,-543,-434,327,-17,26,919,89,-315,1000,-1000,-1000,-139,-393,702,35,-253,71,-376,-157,-1000,-237,9,529,-888,361,-361,812,-227,1000,32,109,-792,424,-1000,-439,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createDefaultObjectUnion(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{465,-970,-496,127,-559,56,-985,431,269,520,-36,-481,-249,912,-321,-945,-759,-909,-331,348,-319,-524,652,933,98,-712,553,677,-976,-774,254,-870,379,-767,69,-413,193,678,-78,-674,207,161,54,-176,876,-581,675,-559,-405,371,477,-37,64,189,-863,-264,-383,486,43,-349,-907,-813,348,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope):com.google.javascript.rhino.jstype.JSType",
            new int[]{-176,784,-827,260,892,89,-899,584,18,-667,160,199,181,57,-808,28,-185,160,-228,-800,-28,214,-458,995,677,-854,371,440,562,883,-969,-509,76,856,509,503,-237,37,625,46,-576,579,-828,-970,-670,-936,-543,-414,-919,864,830,151,-804,186,-468,-161,-949,997,51,-690,417,220,56,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope):com.google.javascript.rhino.jstype.JSType",
            new int[]{-961,-895,-986,122,-141,156,-77,461,756,-718,-296,620,551,442,-414,-104,901,685,962,-497,-814,453,-68,-847,-224,-492,-200,998,646,-13,80,747,487,-659,-990,531,703,704,-879,-534,971,649,-58,-269,411,201,609,492,841,488,-170,999,58,-514,-586,-197,-330,913,433,-808,-429,552,-49,660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{1000,92,-126,-885,-1000,-1000,-183,-850,-1000,774,-815,820,-945,145,-162,-1000,-1000,399,219,-327,-1000,-417,-303,-1000,-53,-1000,1000,115,-1000,-837,1000,-1000,109,643,-1000,-1000,1000,-660,779,1000,-989,985,1000,809,1000,427,1000,-433,1000,437,-233,-793,-471,1000,812,1000,302,-387,420,1000,-286,579,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{677,651,936,204,-732,-26,-694,-587,113,-454,-759,776,-702,978,-866,-822,-339,-141,-924,428,-346,-346,888,-568,885,-898,-223,315,-718,-736,-715,-470,-334,112,-902,-978,941,-541,678,706,-936,927,732,-330,-504,632,-936,-172,-62,340,-516,54,-194,-63,163,-774,-879,232,-957,-940,276,365,747,140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{598,332,-672,-578,976,-45,-102,26,-738,-96,591,-783,-258,661,-812,590,-390,184,-249,-382,506,-525,-867,-50,-66,804,874,-208,383,-356,-624,921,711,-889,489,-731,556,-824,149,-829,-114,263,558,-656,606,-33,-477,-547,-837,573,-920,-264,479,-160,240,569,203,44,-152,72,281,707,904,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{1000,-1000,-941,-333,760,-881,-240,1000,-1000,-1000,-368,-1000,870,1000,-403,-996,1000,549,-226,-302,-1000,1000,400,-523,-1000,1000,-606,-228,81,-1000,-312,11,82,1000,-13,312,14,-243,611,-154,582,486,1000,830,-1000,229,808,-400,-275,-1000,-1000,-356,30,-513,80,953,-1000,-567,1000,-400,-109,-682,951,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.ObjectType,com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.JSType",
            new int[]{648,-648,828,43,-207,-87,341,-1000,-93,-388,-358,1000,-320,-1000,1000,-746,57,-889,-1000,196,-431,94,-1000,-1000,-471,1000,1000,-170,1000,-44,772,491,422,-1000,-828,57,-83,-1000,-3,985,-833,-1000,-1000,833,-1000,1000,-441,1000,631,295,764,-1000,1000,-189,670,-927,978,-152,-259,-639,-715,-79,-85,107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithNewReturnType(com.google.javascript.rhino.jstype.FunctionType,com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{693,958,-832,54,-87,106,618,-139,918,533,-250,-418,3,225,873,705,89,466,-437,-574,-469,921,-862,66,715,-274,-509,-467,-190,958,-538,972,-456,864,489,985,10,292,-594,-844,141,-166,-51,983,711,411,-212,-193,517,-752,693,-193,733,-532,-145,745,347,936,298,-236,33,363,-185,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithNewThisType(com.google.javascript.rhino.jstype.FunctionType,com.google.javascript.rhino.jstype.ObjectType):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{980,-440,969,200,152,-723,426,156,-630,-454,501,-673,-734,-109,-821,-840,529,837,171,276,-236,-220,653,550,663,-799,-808,-925,-106,-34,-620,-330,-970,559,144,457,-768,-767,-371,-25,452,269,135,-739,-564,-205,627,-622,982,947,-948,-986,559,569,864,898,-334,61,987,-941,125,-456,-230,-760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{253,1000,-81,-157,-638,903,122,519,-230,-95,-746,-1000,87,-1000,-1000,-964,125,-661,-123,281,1000,623,1000,-1000,1000,890,-208,-648,-1000,-227,-373,1000,210,-787,1000,-739,29,-771,-695,914,46,-299,-412,1000,-87,286,-766,1000,98,-704,354,-1000,-283,-1000,-293,70,-257,-848,-1000,-1000,798,-1000,-475,938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{155,298,-76,-1000,-521,-325,-1000,-568,533,430,-1000,-219,-70,1000,627,1000,1000,880,-819,-1000,-1000,120,-1000,711,1000,-227,13,626,-53,-665,259,-1000,218,-142,380,-843,686,-376,886,-1000,875,-547,1000,1000,243,-1000,1000,-1000,1000,-1000,584,-1000,1000,-155,-159,-984,305,448,1000,306,1000,738,1000,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.ObjectType,com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.JSType",
            new int[]{1000,156,988,1000,598,218,-349,271,-669,661,1000,1000,-22,21,-935,-830,-708,724,-1000,-922,-972,-223,-271,29,-134,-131,1000,218,-771,380,-129,-267,402,1000,1000,-92,-574,-751,-318,1000,1000,364,533,205,-612,240,-701,-1000,-1000,-368,-327,1000,182,-1000,-981,-118,-501,-676,1000,964,342,202,-118,-111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createInterfaceType(java.lang.String,com.google.javascript.rhino.Node):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-794,-236,-251,439,307,-43,293,-928,80,959,419,180,-783,141,-583,262,632,-740,201,-831,459,955,599,162,-280,-363,-65,704,918,608,519,793,344,598,6,24,-559,801,-104,515,-766,-26,-92,321,522,198,-578,-959,-689,-523,-240,-69,-215,-466,644,-954,12,-133,902,-337,536,412,-827,-428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createNullableType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-886,-506,894,874,-167,-916,322,784,345,-241,-348,-373,12,-387,-841,-381,-177,689,-864,309,414,-19,253,-230,564,-291,836,-55,831,154,-771,-516,446,32,965,-505,-225,365,-114,51,-269,124,-484,-14,-646,-467,23,-454,-553,-142,920,814,-369,749,289,448,493,-244,-233,-738,-437,-490,779,-560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createOptionalNullableType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{1000,-66,-742,139,239,-381,1000,-235,371,165,788,-818,1000,-1000,946,-205,1000,-1000,47,1000,58,1000,-279,25,682,235,-325,-793,-1000,-196,63,-403,39,-1000,333,-218,-780,-297,-145,287,-1000,78,-72,-874,-790,133,-81,682,129,313,325,57,-338,266,1000,-561,452,808,258,986,648,463,678,-782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSType", "", "isEquivalent(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSType", "", "toMaybeFunctionType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSType", "", "toMaybeParameterizedType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.ParameterizedType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSType", "", "toMaybeTemplateType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.TemplateType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.InstanceObjectType", DEReplay.run(
            "com.google.javascript.jscomp.ClosureCodingConvention$AssertInstanceofSpec", "com.google.javascript.jscomp.ClosureCodingConvention$AssertInstanceofSpec", "getAssertedType(com.google.javascript.rhino.Node,com.google.javascript.rhino.jstype.JSTypeRegistry):com.google.javascript.rhino.jstype.JSType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
