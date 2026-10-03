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
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesIso8859_1(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("ARRAY:[B:22:19:java.lang.Byte:OTA=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NDk=:19:java.lang.Byte:MTAx:19:java.lang.Byte:Nzc=:19:java.lang.Byte:NzE=:19:java.lang.Byte:ODk=:19:java.lang.Byte:MTEx:19:java.lang.Byte:ODQ=:19:java.lang.Byte:MTA=:19:java.lang.Byte:MTE3:19:java.lang.Byte:MTAw:19:java.lang.Byte:NzI=:19:java.lang.Byte:MTA3:19:java.lang.Byte:OTg=:19:java.lang.Byte:NzA=:19:java.lang.Byte:NTc=:19:java.lang.Byte:MTA0:19:java.lang.Byte:NTQ=:19:java.lang.Byte:Njg=:19:java.lang.Byte:NTY=:19:java.lang.Byte:ODU=", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesIso8859_1(java.lang.String):byte[]",
            new int[]{-114,-235,-326,-703,-649,-887,-638,-199,190,-29,202,-473,765,780,101,-271,-951,-122,-983,183,577,159,-562,-600,-205,695,708,988,-644,-755,-463,-775,24,542,-535,488,-815,-35,852,-384,659,834,38,-514,492,-118,-284,163,726,-269,285,791,-256,-868,-32,-893,-253,-380,-181,-113,-862,818,394,979}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUnchecked(java.lang.String,java.lang.String):byte[]",
            new int[]{899,654,967,-325,957,-467,775,226,-947,-510,-436,-656,-726,552,-668,425,465,128,626,159,-452,243,541,667,-428,850,-398,-13,272,142,-594,763,91,690,-682,347,725,-377,680,-716,38,-375,-967,-859,913,-517,648,1000,49,161,-680,570,33,932,797,850,85,-41,-484,536,-169,911,-654,-28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUnchecked(java.lang.String,java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NDU=:19:java.lang.Byte:NDg=:19:java.lang.Byte:MTIw:19:java.lang.Byte:NTY=", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUnchecked(java.lang.String,java.lang.String):byte[]",
            new int[]{-1000,-1000,205,-700,-972,874,800,-350,-409,1000,-231,-1000,-1000,590,-1000,-1000,939,1000,-1000,-1000,1000,-1000,-1000,528,-248,-72,-1000,1000,1000,-1000,-922,-872,142,772,1000,-1000,-1000,-917,-1000,-970,-502,1000,1000,1000,1000,-666,22,-468,-1000,-229,18,-1000,-688,1000,-1000,1000,494,1000,-216,-1000,1000,-1000,1000,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUsAscii(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:MzI=", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUsAscii(java.lang.String):byte[]",
            new int[]{386,140,523,-258,-871,-319,-904,-413,-742,298,218,79,285,-64,266,595,658,621,279,620,-511,692,394,899,-322,-788,-399,361,-764,-441,-290,-64,-672,532,-533,201,350,180,-206,211,-754,-4,303,-154,156,-884,435,-471,498,-245,-178,-732,-366,191,-175,-359,-638,351,-419,549,681,-741,-653,-638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUtf16(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUtf16(java.lang.String):byte[]",
            new int[]{686,-545,270,-825,888,244,-513,-674,211,659,600,68,-311,-835,-199,723,671,-134,-542,743,872,536,254,-482,-264,761,-157,33,-82,390,788,-480,-870,-460,-679,-128,-822,68,-721,718,615,558,485,786,120,657,-223,480,-580,-86,-83,422,-578,-878,696,-872,-978,631,-329,-48,-967,93,-787,983}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUtf16Be(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("ARRAY:[B:44:19:java.lang.Byte:MA==:19:java.lang.Byte:NDU=:19:java.lang.Byte:MA==:19:java.lang.Byte:MTA1:19:java.lang.Byte:MA==:19:java.lang.Byte:ODg=:19:java.lang.Byte:MA==:19:java.lang.Byte:MTA=:19:java.lang.Byte:MA==:19:java.lang.Byte:NDM=:19:java.lang.Byte:MA==:19:java.lang.Byte:ODA=:19:java.lang.Byte:MA==:19:java.lang.Byte:OTU=:19:java.lang.Byte:MA==:19:java.lang.Byte:MTEx:19:java.lang.Byte:MA==:19:java.lang.Byte:MTA0:19:java.lang.Byte:MA==:19:java.lang.Byte:NzE=:19:java.lang.Byte:MA==:19:java.lang.Byte:OTU=:19:java.lang.Byte:MA==:19:java.lang.Byte:NDY=:19:java.lang.Byte:MA==:19:java.lang.Byte:OTU=:19:java.lang.Byte:MA==:19:java.lang.Byte:NjU=:19:java.lang.Byte:MA==:19:java.lang.Byte:ODU=:19:java.lang.Byte:MA==:19:java.lang.Byte:NzI=:19:java.lang.Byte:MA==:19:java.lang.Byte:OTU=:19:java.lang.Byte:MA==:19:java.lang.Byte:ODk=:19:java.lang.Byte:MA==:19:java.lang.Byte:MTA1:19:java.lang.Byte:MA==:19:java.lang.Byte:MTA2:19:java.lang.Byte:MA==:19:java.lang.Byte:NzM=:19:java.lang.Byte:MA==:19:java.lang.Byte:ODQ=", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUtf16Be(java.lang.String):byte[]",
            new int[]{349,-581,-163,-153,-789,-266,-296,-285,-932,335,-77,308,159,255,633,64,491,-958,-796,540,-503,486,18,587,-950,-655,170,715,-880,422,-48,-256,620,-975,-346,-892,526,-769,-841,308,229,-305,103,-112,-725,-239,-48,78,-887,-433,-764,-233,-692,498,-580,-192,-224,858,-608,869,-963,-22,-611,966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUtf16Le(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("ARRAY:[B:20:19:java.lang.Byte:NDU=:19:java.lang.Byte:MA==:19:java.lang.Byte:NDU=:19:java.lang.Byte:MA==:19:java.lang.Byte:NTI=:19:java.lang.Byte:MA==:19:java.lang.Byte:NTI=:19:java.lang.Byte:MA==:19:java.lang.Byte:NTU=:19:java.lang.Byte:MA==:19:java.lang.Byte:MTAx:19:java.lang.Byte:MA==:19:java.lang.Byte:NDU=:19:java.lang.Byte:MA==:19:java.lang.Byte:NTc=:19:java.lang.Byte:MA==:19:java.lang.Byte:NDg=:19:java.lang.Byte:MA==:19:java.lang.Byte:NTI=:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUtf16Le(java.lang.String):byte[]",
            new int[]{-250,-447,-841,-904,847,-288,-256,70,-620,-244,513,-485,-668,-951,-951,-192,666,283,-911,26,-321,-980,-174,-430,-720,-744,5,651,-660,687,-538,-645,830,-425,-298,-274,174,854,20,603,-31,420,725,771,-988,-552,67,421,456,996,657,-150,-179,-826,329,-93,578,710,-309,742,94,-117,-65,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUtf8(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("ARRAY:[B:13:19:java.lang.Byte:ODM=:19:java.lang.Byte:NDM=:19:java.lang.Byte:MTIy:19:java.lang.Byte:MTIw:19:java.lang.Byte:NzA=:19:java.lang.Byte:ODk=:19:java.lang.Byte:ODI=:19:java.lang.Byte:Njg=:19:java.lang.Byte:Nzc=:19:java.lang.Byte:MTEy:19:java.lang.Byte:NzY=:19:java.lang.Byte:Njc=:19:java.lang.Byte:MTEw", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "getBytesUtf8(java.lang.String):byte[]",
            new int[]{863,-324,-144,513,-17,-364,390,175,-811,273,763,39,-804,-614,473,-814,-545,91,-70,30,221,-693,860,-314,-200,-47,-518,-839,-14,-454,-515,-351,533,490,-574,841,-717,484,-270,-627,510,-784,210,247,-46,-204,-310,425,-928,202,-821,-385,-914,-547,853,767,-432,-827,-935,-254,489,-255,561,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "newString(byte[],java.lang.String):java.lang.String",
            new int[]{726,1,-888,-774,-73,-467,24,899,525,283,-574,-698,360,666,-218,649,-718,909,-456,-618,-68,-548,50,576,-419,-561,725,-38,720,-888,5,832,-146,-661,-915,714,73,-291,572,52,-658,-280,446,326,-824,-629,-654,93,-594,201,117,190,47,-751,833,42,968,730,14,-588,-6,-697,-316,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "newStringIso8859_1(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "newStringUsAscii(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "newStringUtf16(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "newStringUtf16Be(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "newStringUtf16Le(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.StringUtils", "org.apache.commons.codec.binary.StringUtils", "newStringUtf8(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "", "encodeBase64String(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "", "isBase64(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "", "isBase64(java.lang.String):boolean",
            new int[]{-237,-115,579,198,-864,641,663,-115,-729,-761,671,676,-239,434,-872,-788,-692,-553,-961,140,-873,-693,-863,604,-996,-900,481,-63,798,-147,260,624,934,735,918,-657,-251,261,-406,696,-573,-703,262,-915,-303,773,914,133,-527,-767,691,27,-817,-384,946,-800,648,994,-123,-97,-916,-616,591,-641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTk1:19:java.lang.Byte:LTcy:19:java.lang.Byte:LTM0:19:java.lang.Byte:MTE0:19:java.lang.Byte:LTM1", DEReplay.run(
            "org.apache.commons.codec.binary.BaseNCodec", "org.apache.commons.codec.binary.Base32,org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{671,-369,-439,275,107,-824,724,564,963,-217,303,-444,495,-746,961,96,-955,694,521,507,-445,-657,-492,259,335,29,765,-691,645,25,459,238,590,307,-150,856,-449,-311,-918,-115,898,-876,-34,-477,-888,-417,212,228,582,335,913,880,-649,-770,200,-343,-158,610,943,-595,229,-141,-861,53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.BaseNCodec", "org.apache.commons.codec.binary.Base32,org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:Mzk=:19:java.lang.Byte:LTQ2:19:java.lang.Byte:LTcz", DEReplay.run(
            "org.apache.commons.codec.binary.BaseNCodec", "org.apache.commons.codec.binary.Base32,org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{-832,-572,144,521,945,894,637,755,-710,685,524,-564,270,-769,454,-62,960,112,-863,-770,645,201,623,857,83,-323,-957,-890,75,-984,-661,-107,608,-614,708,-540,697,-491,-491,268,733,-57,164,-512,-402,-964,272,408,121,-691,-438,-119,535,927,-420,288,520,89,92,39,153,656,-942,558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.BaseNCodec", "org.apache.commons.codec.binary.Base32,org.apache.commons.codec.binary.Base64", "encodeAsString(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.BaseNCodec", "org.apache.commons.codec.binary.Base32,org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.BaseNCodec", "org.apache.commons.codec.binary.Base32,org.apache.commons.codec.binary.Base64", "isInAlphabet(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.BaseNCodec", "org.apache.commons.codec.binary.Base32,org.apache.commons.codec.binary.Base64", "isInAlphabet(java.lang.String):boolean",
            new int[]{-865,603,208,626,240,129,605,-175,-951,-160,997,-567,-574,-996,88,422,88,986,10,-795,-375,604,-986,690,527,-703,589,107,971,-125,-957,855,-316,-758,403,-229,818,-350,393,-36,435,826,210,623,-65,611,-185,-947,572,618,-440,612,-950,-124,93,-811,-875,-575,-291,387,118,372,-439,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "md2(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("ARRAY:[B:16:19:java.lang.Byte:LTk3:19:java.lang.Byte:LTM3:19:java.lang.Byte:MzU=:19:java.lang.Byte:LTQ3:19:java.lang.Byte:MTk=:19:java.lang.Byte:LTQ3:19:java.lang.Byte:LTM3:19:java.lang.Byte:Mjk=:19:java.lang.Byte:Nzc=:19:java.lang.Byte:MzY=:23:java.lang.Byte:LTExNA==:19:java.lang.Byte:Mjg=:19:java.lang.Byte:LTM5:19:java.lang.Byte:NDE=:19:java.lang.Byte:MTIw:23:java.lang.Byte:LTEwNA==", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "md2(java.lang.String):byte[]",
            new int[]{932,673,353,-95,944,45,283,-165,-541,-226,78,-252,314,-594,626,-392,111,250,992,218,42,-708,-529,116,-338,432,-245,850,268,704,462,-359,-291,435,713,-810,-274,-927,979,-234,-843,833,987,584,-223,212,-880,-22,-103,946,470,908,387,-950,-581,-90,303,-814,-476,-207,925,-170,315,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "md2Hex(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.String:ZjU3OTc1MjdjODdjMDcyZTVmNzhjNDk5MzM1YWVkNGQ=", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "md2Hex(java.lang.String):java.lang.String",
            new int[]{-530,-292,182,214,-404,553,-207,-16,125,401,713,-408,522,-36,-882,-737,-968,260,-585,-671,797,348,713,507,699,273,-336,-750,-526,-131,334,91,-794,602,333,-299,504,-739,680,318,358,976,588,-891,963,772,-305,-915,-356,-333,-873,252,433,-797,-767,815,797,-817,-181,-520,581,-579,286,-849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "md5(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("ARRAY:[B:16:19:java.lang.Byte:Njk=:19:java.lang.Byte:LTI1:19:java.lang.Byte:MzE=:19:java.lang.Byte:MjI=:19:java.lang.Byte:OTY=:19:java.lang.Byte:ODY=:19:java.lang.Byte:OTk=:19:java.lang.Byte:LTI2:19:java.lang.Byte:ODI=:19:java.lang.Byte:LTE5:19:java.lang.Byte:OTY=:23:java.lang.Byte:LTEwNQ==:19:java.lang.Byte:LTEz:19:java.lang.Byte:ODI=:19:java.lang.Byte:Mjc=:19:java.lang.Byte:NDM=", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "md5(java.lang.String):byte[]",
            new int[]{676,-841,-794,-528,402,-553,347,819,-377,-524,388,-622,-968,351,-244,-710,108,-64,994,316,935,341,844,-552,-901,-987,-537,534,66,-187,-719,-927,-703,867,438,599,-26,548,-924,-385,-326,-15,817,-83,558,-278,714,328,757,-245,677,345,-516,70,-106,28,-884,-970,-826,-439,0,-529,235,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "md5Hex(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.String:ZGE4MzIyODZiZmRjNmQ0Zjg5YmM2ZTNkNTg4N2QwNzg=", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "md5Hex(java.lang.String):java.lang.String",
            new int[]{-786,-370,595,968,-449,-547,-159,838,-584,554,482,531,-639,867,846,855,262,119,-850,524,314,-663,-213,-432,629,-104,791,-209,689,-119,-420,-970,25,-447,249,323,135,833,-675,-538,879,-108,181,903,390,-831,-897,469,841,-565,397,-89,373,-110,-161,-998,-82,698,-693,-441,695,-149,450,553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("ARRAY:[B:20:19:java.lang.Byte:LTIz:19:java.lang.Byte:NTk=:19:java.lang.Byte:Nzg=:19:java.lang.Byte:NjA=:19:java.lang.Byte:NzA=:19:java.lang.Byte:Nzk=:19:java.lang.Byte:LTM=:19:java.lang.Byte:ODE=:19:java.lang.Byte:MTE1:19:java.lang.Byte:NDc=:19:java.lang.Byte:LTY3:19:java.lang.Byte:MTA5:19:java.lang.Byte:LTE5:19:java.lang.Byte:MTEz:19:java.lang.Byte:MTI2:19:java.lang.Byte:LTk4:19:java.lang.Byte:LTM=:19:java.lang.Byte:LTk0:23:java.lang.Byte:LTExOA==:19:java.lang.Byte:LTgz", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha(java.lang.String):byte[]",
            new int[]{668,662,-830,-693,56,536,-544,370,513,-212,-131,-119,302,127,988,830,-635,-399,166,82,735,-6,971,985,337,652,327,-667,129,-552,296,-925,-200,-494,-406,299,871,-987,-529,-122,-100,-978,-654,840,725,462,-260,83,-789,786,-528,-658,-201,612,812,-548,-713,-451,373,914,-230,-18,861,526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha1(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("ARRAY:[B:20:19:java.lang.Byte:Njk=:19:java.lang.Byte:MTM=:19:java.lang.Byte:LTM0:19:java.lang.Byte:LTU2:19:java.lang.Byte:LTM1:19:java.lang.Byte:MzI=:19:java.lang.Byte:MTA4:19:java.lang.Byte:NDY=:19:java.lang.Byte:NDI=:19:java.lang.Byte:LTc5:19:java.lang.Byte:LTgy:19:java.lang.Byte:LTIy:19:java.lang.Byte:LTg3:19:java.lang.Byte:MTQ=:23:java.lang.Byte:LTEyMw==:19:java.lang.Byte:LTI3:19:java.lang.Byte:MjM=:19:java.lang.Byte:ODM=:19:java.lang.Byte:LTcy:19:java.lang.Byte:LTcz", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha1(java.lang.String):byte[]",
            new int[]{-156,-75,120,-459,-339,-200,221,739,856,-63,-224,-295,770,667,-443,-998,90,589,-925,-281,-920,-197,-411,313,-665,-755,4,705,902,244,-973,753,887,740,906,398,-254,-838,761,272,-672,-128,-902,380,447,-703,-584,562,-741,-601,12,-106,-943,961,-958,918,-788,-676,712,660,-41,-327,48,-256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha1Hex(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.String:ZTdlN2I4YzRjZjU4MzlhNjY5NGExYmM1ZWU5MzRmNDE5ODExYzUyMw==", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha1Hex(java.lang.String):java.lang.String",
            new int[]{102,117,-766,-400,596,-707,713,243,378,-165,578,-894,855,-780,42,-799,-870,204,29,362,91,490,470,17,-395,-60,-518,-697,-268,660,-706,824,344,351,587,-93,95,-86,-254,-595,-708,531,394,792,-104,-838,-451,-398,-387,721,-236,-914,-160,89,-325,376,-909,-471,540,433,436,-903,-445,566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha256(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("ARRAY:[B:32:19:java.lang.Byte:LTY5:19:java.lang.Byte:MTA1:19:java.lang.Byte:LTk1:19:java.lang.Byte:LTQ2:19:java.lang.Byte:MTg=:19:java.lang.Byte:LTk4:19:java.lang.Byte:MTI1:19:java.lang.Byte:MTE=:19:java.lang.Byte:NTY=:19:java.lang.Byte:MzA=:23:java.lang.Byte:LTEyOA==:19:java.lang.Byte:LTUw:23:java.lang.Byte:LTExNw==:19:java.lang.Byte:MjM=:19:java.lang.Byte:OTc=:19:java.lang.Byte:LTEy:19:java.lang.Byte:NjQ=:19:java.lang.Byte:OTY=:19:java.lang.Byte:MTg=:19:java.lang.Byte:NTg=:19:java.lang.Byte:LTE0:19:java.lang.Byte:NzA=:19:java.lang.Byte:ODU=:19:java.lang.Byte:LTkz:23:java.lang.Byte:LTEwOA==:19:java.lang.Byte:MTE0:19:java.lang.Byte:NzI=:19:java.lang.Byte:LTgy:19:java.lang.Byte:MTIw:19:java.lang.Byte:LTYx:23:java.lang.Byte:LTEyNw==:19:java.lang.Byte:Njk=", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha256(java.lang.String):byte[]",
            new int[]{-156,-881,-797,695,333,159,498,547,826,456,-708,548,356,733,-923,-874,-712,-767,201,686,-766,-406,-464,243,802,-155,-319,-596,113,-927,97,619,834,560,403,145,823,-528,-583,-286,-626,-450,491,580,528,696,167,-701,-48,-212,-736,362,-520,653,-884,655,574,-280,891,579,-329,-466,653,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha256Hex(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:NGZkYmVhZGNlY2QzNTliOGNlZDFjYWE4NTM4YzNhMGE0ZjQzOTYzYzUyMmJjNTgzMjMwMzRmNzIyYmQ3NGMxNQ==", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha256Hex(java.lang.String):java.lang.String",
            new int[]{415,482,888,309,-313,149,627,781,489,439,987,223,354,972,323,-308,553,469,-238,268,998,598,-127,837,-961,812,832,-554,371,50,-393,844,37,948,-959,493,-932,315,-1,313,-305,4,752,-235,-729,-959,-373,-541,39,885,-365,-571,708,-957,-753,458,444,-94,-204,376,311,-363,-929,154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha384(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("ARRAY:[B:48:23:java.lang.Byte:LTEyMA==:19:java.lang.Byte:LTg1:19:java.lang.Byte:LTQ3:19:java.lang.Byte:Nw==:23:java.lang.Byte:LTEyMA==:23:java.lang.Byte:LTEwNw==:23:java.lang.Byte:LTEwMQ==:19:java.lang.Byte:ODY=:19:java.lang.Byte:LTk5:19:java.lang.Byte:NzM=:19:java.lang.Byte:LTQy:19:java.lang.Byte:LTc3:19:java.lang.Byte:LTc4:19:java.lang.Byte:NjM=:19:java.lang.Byte:LTcx:23:java.lang.Byte:LTEwNw==:19:java.lang.Byte:MTE4:19:java.lang.Byte:NzU=:23:java.lang.Byte:LTEwMg==:19:java.lang.Byte:ODg=:23:java.lang.Byte:LTEyNw==:19:java.lang.Byte:NjA=:19:java.lang.Byte:LTk=:19:java.lang.Byte:LTMz:19:java.lang.Byte:LTM5:23:java.lang.Byte:LTExNg==:19:java.lang.Byte:LTg2:19:java.lang.Byte:NTg=:19:java.lang.Byte:OTA=:19:java.lang.Byte:MTc=:19:java.lang.Byte:MTA5:19:java.lang.Byte:LTE2:19:java.lang.Byte:LTYy:19:java.lang.Byte:NDk=:19:java.lang.Byte:Mjg=:19:java.lang.Byte:NDU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:LTky:19:java.lang.Byte:LTQy:19:java.lang.Byte:MTM=:19:java.lang.Byte:LTIx:19:java.lang.Byte:LTMy:19:java.lang.Byte:NTA=:23:java.lang.Byte:LTEyMQ==:19:java.lang.Byte:LTEw:19:java.lang.Byte:LTE5:19:java.lang.Byte:LTUw:19:java.lang.Byte:LTM3", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha384(java.lang.String):byte[]",
            new int[]{-405,-793,-362,-941,200,-106,-640,-168,-271,-512,16,531,-62,-596,231,-62,-642,-83,490,-765,-712,377,898,769,144,-865,-828,964,750,251,902,-26,781,-497,-499,-818,284,259,-216,-299,-34,381,626,677,-228,461,809,-626,-835,-558,-201,-231,479,-75,-51,707,717,349,429,544,-713,597,26,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha384Hex(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.String:NGMxMzNhMTI1NTIzZTYzYTVlNWE3NjliOThkYjIyYjkxZTlmYzMyYmRmNTNhN2FjZjA3Njc5MDg1ZWE1N2JjYTE2ZDkzZTUyNjRlNDZmYjdkODk2NzA3NWZlNWVkMzA5", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha384Hex(java.lang.String):java.lang.String",
            new int[]{717,600,784,-432,-994,-343,66,-485,-513,-349,106,218,-965,-113,-570,-227,82,-464,-322,-358,-932,-907,-333,-942,-333,-290,-578,-106,492,286,626,468,-599,-611,-450,-672,-140,-323,-36,-897,900,342,833,-275,-880,275,613,-57,945,-843,899,-59,-167,-522,844,88,549,-378,296,-632,727,553,-105,-112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha512(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("ARRAY:[B:64:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA5:23:java.lang.Byte:LTExOA==:23:java.lang.Byte:LTEwOQ==:19:java.lang.Byte:ODk=:19:java.lang.Byte:MjM=:19:java.lang.Byte:MjE=:19:java.lang.Byte:LTMw:23:java.lang.Byte:LTEyOA==:23:java.lang.Byte:LTEyMw==:23:java.lang.Byte:LTEwMQ==:19:java.lang.Byte:MzU=:19:java.lang.Byte:LTgy:19:java.lang.Byte:ODU=:23:java.lang.Byte:LTEwNQ==:19:java.lang.Byte:MTAy:19:java.lang.Byte:LTcz:19:java.lang.Byte:LTI0:19:java.lang.Byte:NDI=:19:java.lang.Byte:NDg=:19:java.lang.Byte:LTQ3:19:java.lang.Byte:MzE=:19:java.lang.Byte:LTQ=:19:java.lang.Byte:LTY3:19:java.lang.Byte:ODM=:19:java.lang.Byte:NTg=:19:java.lang.Byte:Mzc=:19:java.lang.Byte:OTE=:19:java.lang.Byte:MTAx:19:java.lang.Byte:MTE=:19:java.lang.Byte:MTk=:19:java.lang.Byte:LTkx:19:java.lang.Byte:NDc=:19:java.lang.Byte:MzQ=:19:java.lang.Byte:ODY=:19:java.lang.Byte:LTk0:19:java.lang.Byte:MTE2:19:java.lang.Byte:LTYx:19:java.lang.Byte:LTU0:19:java.lang.Byte:LTMx:19:java.lang.Byte:Njk=:19:java.lang.Byte:OA==:19:java.lang.Byte:NjQ=:19:java.lang.Byte:Nzc=:19:java.lang.Byte:MTE2:19:java.lang.Byte:LTEw:19:java.lang.Byte:MTAw:19:java.lang.Byte:MTM=:19:java.lang.Byte:LTQz:23:java.lang.Byte:LTEwMQ==:19:java.lang.Byte:ODk=:19:java.lang.Byte:LTkz:19:java.lang.Byte:LTc1:19:java.lang.Byte:LTQ4:19:java.lang.Byte:LTUz:19:java.lang.Byte:MTEx:19:java.lang.Byte:NjM=:19:java.lang.Byte:LTk4:19:java.lang.Byte:LTc2:19:java.lang.Byte:MTE4:19:java.lang.Byte:LTM2:23:java.lang.Byte:LTEyNw==:19:java.lang.Byte:LTk3:19:java.lang.Byte:LTU2", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha512(java.lang.String):byte[]",
            new int[]{206,-628,-285,-561,-458,416,224,864,-746,-844,63,-523,-599,607,475,-640,-291,-891,889,555,-81,33,944,-969,-629,-54,-133,896,86,864,174,-369,-538,685,-263,116,-368,-126,-482,747,525,68,-481,-963,705,-639,824,734,178,-265,-998,-237,544,367,-391,2,96,334,24,330,758,-379,-552,-770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha512Hex(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.String:ZjgyNTg2YmJjN2Y5ZGIxYTJmMDc2M2U0NjQ0ZjdiNTFlZWIwMmEwNDFmMDI0MmE0MDViOTllZmVkN2YwODM2YzI1MTZiMzk3OGQxZWRhYjg5ZDlhZjVlNDU0MmJmOGUxY2ZmNWY5NDQ4YThlODZlZWM0ZGRiN2UwYWNlOGE0ZjI=", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "sha512Hex(java.lang.String):java.lang.String",
            new int[]{647,959,101,629,704,403,74,3,-710,475,-324,-980,634,-240,-240,-215,-995,641,761,739,-262,779,-643,-8,-764,917,975,-385,823,124,541,-963,-457,-82,-233,-172,-888,-733,6,-329,99,-625,-744,114,92,380,231,832,146,-402,-410,541,-881,813,398,-534,618,229,681,304,258,-804,535,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "shaHex(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.String:MTQwYzM2MzllMmY5NWU4NThiOTI3NzM5ODFhODdlMGMyYmYzNzM1Nw==", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "shaHex(java.lang.String):java.lang.String",
            new int[]{127,-248,9,533,-121,-73,-679,-837,-713,872,837,-926,-411,934,136,-254,231,921,439,155,-924,-263,779,-949,452,145,404,741,202,-279,-540,-11,-908,184,-726,106,728,-421,362,817,-531,33,661,-920,-248,-796,426,659,683,-45,-260,976,819,266,-351,521,-374,-47,541,940,284,373,-384,801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "updateDigest(java.security.MessageDigest,java.lang.String):java.security.MessageDigest",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.digest.DigestUtils", "", "updateDigest(java.security.MessageDigest,java.lang.String):java.security.MessageDigest",
            new int[]{-700,-831,-203,781,8,-360,37,348,-835,-75,561,-724,-502,-595,532,360,474,-880,837,305,-289,-681,-183,-404,-302,-4,-557,-423,743,288,81,460,-7,-163,-924,-461,73,-319,-755,795,-541,57,106,-686,40,-390,-868,415,-514,954,683,281,-973,-753,381,-8,-960,-480,635,339,-569,859,822,583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.String:S1NGSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-171,-1000,400,142,-220,304,-8,1000,1000,343,-1000,-210,511,-241,-1000,701,-354,598,682,-466,-1000,255,-930,-1000,1000,1000,-1000,1000,884,1000,747,276,832,-1000,201,344,-173,418,482,-1000,-93,285,94,1000,-1000,440,-571,879,1000,-281,419,-568,-108,1000,66,935,936,715,-1000,-1000,-1000,1000,-369,-463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.String:TEpOSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-1000,-1000,-1000,990,511,357,-212,-876,663,276,653,68,-1000,-763,471,1000,58,618,-306,322,225,-380,900,741,-809,-205,-227,198,411,295,936,-645,116,-87,907,1000,426,-609,-127,-1000,-391,-18,-390,-285,-1000,279,806,-59,306,-530,580,-750,-695,485,-535,46,367,21,939,1000,-98,-447,1000,-674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.String:U1RKUw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-588,-402,660,111,-953,-293,-977,133,1000,532,-1000,-513,84,298,-194,-210,196,-93,-1000,72,-1000,546,-365,-648,115,736,215,1000,1000,1000,691,-887,1000,-442,-1000,-728,1000,418,-1000,-1000,625,117,1000,-601,-1000,-536,755,297,979,25,-503,-1000,78,1000,-318,1000,-1000,677,-612,225,-518,1000,251,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.String:TUtSSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-806,-790,-1000,-481,-222,234,120,567,829,-238,-1000,595,130,-1000,790,837,-501,-106,-1000,-688,-1000,-1000,-500,-397,-171,1000,546,-712,221,1000,440,-1000,-23,-254,1000,-441,-12,1000,812,-949,8,371,143,-476,-501,793,-212,-1000,1000,1000,624,-390,-870,1000,-593,985,-1000,66,-864,1000,-373,-89,429,44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.String:S1NURg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-351,-365,400,-483,-371,9,773,762,-36,144,-1000,510,15,-695,1000,375,-76,-368,-1000,-1000,-1000,-599,-263,-1000,514,1000,393,-532,50,1000,-127,-354,801,-306,1000,-384,-585,1000,200,-1000,46,303,-239,-415,182,302,-78,713,1000,761,844,236,-815,116,-1000,981,-908,778,-752,106,262,414,-220,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.String:QVBGSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-290,222,-1000,-483,-428,24,365,-248,-543,86,1000,517,-478,-359,645,784,191,366,-1000,-569,1000,-635,666,803,42,-1000,202,-605,-751,-875,484,-387,-468,476,1000,998,-216,612,935,723,-110,-177,-231,-682,-724,799,842,-308,-359,503,-23,-692,-492,-1000,-428,-581,-908,296,253,375,-527,-909,343,-896}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.String:Tkw=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-138,-167,40,-485,-757,-96,-348,-1000,-120,618,400,43,-935,1000,-556,366,-66,-1000,-1000,-40,400,-30,865,840,-538,-533,-678,558,146,1000,760,-548,100,282,-142,494,532,-1000,-516,170,6,-412,738,-665,-522,145,1000,1000,-525,-742,-1000,-1000,184,-846,-203,-305,-936,648,1000,-409,-518,-258,516,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.String:S0tGSg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-871,-637,-180,653,272,613,382,-548,-63,-238,138,370,-674,-549,770,880,704,-340,-997,-962,225,-977,831,344,-479,324,-451,-712,-97,295,147,-578,-23,399,907,868,-463,-609,-642,-147,-282,175,142,-926,-449,683,806,465,306,-640,38,-679,-695,178,-535,591,258,66,745,672,-416,-858,429,-961}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.String:TUtQSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-556,-923,660,557,-840,-276,746,-1000,1000,-23,-1000,361,-556,-1000,1000,831,437,355,-1000,-1000,568,-1000,336,1000,-1000,77,208,92,338,988,400,-990,-1000,495,1000,-895,-82,264,-420,-607,-644,-84,-457,-1000,-282,1000,837,1000,1000,596,240,-1000,275,758,-965,1000,327,386,1000,1000,-518,-385,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.String:QUtT", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-656,-1000,-895,-483,-549,304,-747,-305,1000,272,-1000,181,-1000,1000,-702,782,-363,348,-1000,-124,-1000,-1000,-185,40,42,867,-571,1000,1000,1000,1000,-1000,133,-530,-142,-239,686,-400,-88,-1000,-15,-49,1000,-298,-1000,174,837,1000,423,-555,-886,-1000,275,554,38,597,-1000,264,-44,-409,22,380,1000,-518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.String:Sw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-885,-1000,-1000,-898,-406,1000,-796,1000,819,-329,-1000,102,-126,1000,-1000,590,634,-60,-1000,-555,-1000,-869,-781,-154,777,1000,-1000,927,1000,1000,1000,-1000,-535,-952,459,229,86,51,299,-1000,-398,44,1000,-383,195,19,921,1000,414,-1000,-1000,-991,1000,998,764,583,-1000,-157,-907,-1000,167,222,130,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-388,-848,539,-193,928,277,413,697,39,151,203,-864,722,-272,288,866,-643,844,987,518,-568,177,135,-538,-627,38,959,427,418,-2,-305,614,211,-111,644,85,-133,879,713,-104,-660,976,-138,642,140,597,349,882,-360,635,167,245,-458,-165,650,-69,416,247,896,-194,663,993,302,278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.String:S0tTVA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-692,-1000,-1000,-898,-819,1000,-77,162,911,-951,1000,273,561,1000,-1000,-138,669,-939,-1000,-1000,-1000,18,-885,-635,1000,72,-1000,931,1000,1000,-351,-1000,-703,-369,-399,-195,-249,733,-324,-1000,-267,278,1000,-708,-188,-536,921,1000,414,-1000,-1000,-756,1000,-868,182,718,-1000,-102,-438,-1000,-465,-274,818,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.String:S05OVA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-710,-356,-1000,990,671,-309,291,-1000,-309,860,1000,147,-1000,-1000,1000,1000,-403,1000,-306,-1000,394,-810,1000,1000,-1000,-205,637,-420,-867,-1000,1000,-272,93,-984,1000,1000,537,687,24,206,-446,-251,-463,-285,-669,1000,-255,1000,-238,522,969,-575,-1000,485,-854,-859,432,481,1000,1000,-393,-925,452,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.String:S01LUw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-885,618,-1000,-898,-392,1000,839,1000,-887,186,1000,1000,-126,-1000,1000,590,367,-135,-1000,-704,99,-59,1000,1000,-1000,-1000,-1000,-886,-1000,-1000,-351,-998,-535,-361,1000,871,-249,733,-479,1000,-117,-266,-570,-383,195,905,1000,-964,414,587,-1000,-756,-1000,-1000,-1000,-916,-1000,514,1000,1000,-308,-1000,692,-738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.String:S1NLSg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-880,-1000,-1000,-483,294,672,-276,-956,663,106,653,-9,-907,-751,471,1000,389,741,-306,322,225,-648,-6,868,-821,-48,-606,-325,462,641,967,-725,-183,-254,953,23,257,-609,173,-1000,-689,-8,34,-156,-366,210,836,-1000,306,-530,910,-677,-655,744,-535,46,367,-163,939,1000,-509,-710,1000,-755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.String:S1NT", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{1000,1000,1000,-226,-444,376,545,-1000,-528,839,-1000,-173,1000,-1000,-37,1000,-1000,-606,1000,-125,880,-424,1000,-457,43,1000,-1000,-56,234,147,1000,-1000,-1000,645,275,269,-1000,1000,1000,-824,1000,-791,1000,1000,-940,804,-516,1000,-536,-1000,370,-114,-1000,-984,-1000,-861,-89,1000,1000,-739,-1000,-1000,-817,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.String:TUtLSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{523,1000,-1000,-226,987,1000,-503,-150,-112,365,-378,1000,-390,995,-339,-380,-523,-19,-899,-392,479,66,895,336,-917,-409,-643,1000,-981,818,1000,-1000,-310,-1000,-1000,-79,1000,421,-1000,-232,894,-742,-605,-117,313,763,1000,-120,673,-714,-332,1000,441,1000,-1000,126,-89,-232,-416,-415,156,-20,-337,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.String:S1NQVA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-373,1000,-675,271,400,-1000,-627,464,-504,-64,861,302,-365,1000,1000,-1000,37,-16,-697,-911,279,49,385,744,681,-1000,-20,-312,1000,96,-848,825,221,-938,32,-205,1000,-562,990,361,-1000,181,402,-1000,1000,180,449,488,-1000,464,293,653,707,1000,1000,-32,975,90,-925,-8,1000,1000,-100,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.String:SkZOUw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-865,802,-675,46,11,-563,-555,1000,19,112,1000,-519,-365,1000,-274,-714,-8,724,-525,-694,-329,-834,924,117,-52,-1000,657,-261,136,96,-1000,261,221,-938,32,-488,1000,307,-400,1000,-1000,277,402,-1000,1000,-263,449,-841,412,258,-1000,-18,1000,1000,1000,1000,975,-1000,-1000,218,1000,180,1000,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.String:VFNLTA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-707,802,725,46,-1000,-1000,148,1000,-1000,0,1000,-1000,820,1000,-1000,-714,-136,1000,754,-585,651,-430,944,-903,677,-1000,657,-1000,1000,-41,-1000,-467,1000,324,-50,-1000,662,307,1000,556,-1000,424,424,-1000,-801,1000,-1,-498,737,258,-1000,602,861,424,117,1000,99,-1000,-1000,-1000,695,180,172,407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.String:RlBL", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{722,723,-1000,-226,1000,14,-96,-369,51,148,-455,1000,1000,855,-398,-86,-679,-60,-510,-125,516,-61,543,340,255,-153,-1000,1000,234,417,1000,-264,-136,-1000,-74,-541,708,272,-1000,-61,544,-1000,-124,65,-940,749,350,52,179,-677,-422,995,394,1000,526,995,221,14,-617,-705,930,69,-952,777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:VEtTUw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{1000,943,20,61,-284,432,869,-1000,-529,749,-535,-1000,638,-388,-457,1000,-675,-505,1000,441,878,-518,370,48,-1000,673,-1000,435,-32,225,912,-722,-649,-54,415,553,-1000,1000,1000,-681,1000,-712,757,116,24,1000,-570,572,-493,-602,264,-735,-1000,-689,-5,587,1000,884,1000,695,1000,-739,643,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.String:Tkw=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{348,1000,-1000,283,655,-586,-213,-165,-522,169,-946,986,135,440,690,225,-524,-220,-816,-307,554,-416,540,1000,526,-400,-314,204,601,-41,25,689,-145,-1000,384,617,792,182,1000,255,-337,-406,681,-281,1000,477,337,444,-1000,258,79,1000,195,862,1000,-298,979,247,-618,-623,1000,512,-1000,799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.String:UFRTVA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{567,943,20,-450,165,1000,618,-1000,-607,534,-1000,887,1000,-1000,-36,1000,-1000,-1000,266,-398,1000,-1000,970,-20,-1000,1000,-1000,1000,-863,55,1000,-722,-1000,-54,138,932,-1000,1000,1000,-547,1000,-1000,945,1000,369,1000,-933,1000,-1000,-1000,150,280,-1000,803,108,-319,702,1000,837,-836,710,-991,-1000,942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.String:QQ==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{615,-1000,400,-161,-1000,-279,629,-123,-181,-1000,647,-582,1000,-35,-1000,-503,-109,1000,145,467,1000,128,691,-287,1000,298,-1000,471,197,-1000,849,158,1000,1000,1000,-53,-1000,-417,1000,-1000,697,1000,-24,1000,710,1000,-785,1000,-444,633,-1000,-50,-128,117,-1000,-147,132,505,1000,-951,-1000,-141,-919,611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.String:TUtTSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-159,92,-1000,-819,-1000,-304,-32,-936,-986,488,-213,302,-404,443,1000,-535,-516,1000,-495,-194,420,-12,-16,1000,18,306,-1000,692,231,272,490,646,467,-1000,-341,846,282,173,990,-418,-1000,-674,705,-219,1000,213,2,1000,-1000,-154,1000,-44,-596,-45,914,-321,1000,1000,475,988,1000,356,-148,335}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.String:QUtGSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{108,855,1000,-226,-444,-972,372,821,-528,326,1000,-1000,512,1000,-1000,-499,-295,895,1000,695,634,-424,982,-1000,769,-647,160,-749,1000,-48,-413,385,893,1000,195,-728,319,124,1000,-134,1000,-4,635,-798,181,1000,-3,-49,-536,996,-541,164,747,-412,-240,1000,578,-628,-435,252,990,-25,969,9}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{473,-707,-764,595,620,-697,-447,678,30,-587,-779,764,-364,833,293,111,-370,257,-431,642,712,-30,917,-214,277,879,-596,142,-405,764,211,-386,-918,-929,-25,580,-460,897,-330,923,-80,478,437,-712,589,443,872,-190,-672,-614,208,-259,-596,881,533,887,885,-26,751,594,124,402,-658,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.String:U0ZM", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-57,1000,-360,-243,249,1000,-545,-464,-440,-95,-1000,1000,-382,704,-330,818,-335,679,120,-736,576,-1000,702,493,437,207,-713,683,-1000,733,526,-1000,397,-933,-825,-8,578,1000,-197,390,1000,12,-629,-169,-541,937,1000,252,-1000,-595,-1000,-10,571,507,-436,367,504,346,-53,-1000,95,-1000,310,294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.String:VEtTUA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{695,802,725,-115,-1000,-563,19,1000,159,-39,1000,-1000,694,1000,-1000,336,115,1000,711,-585,676,-430,944,-1000,-52,-1000,657,-1000,400,-26,-1000,-467,-1000,324,948,-948,26,-50,1000,433,-1000,510,325,-1000,-843,953,68,-498,1000,281,-931,260,861,804,117,1000,-44,-1000,-1000,-728,695,180,712,383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.String:S0tTSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{1000,983,1000,78,-585,-867,259,233,-416,230,-109,-1000,344,-173,-1000,-175,-694,792,1000,442,706,1000,1000,-882,1000,-99,-535,1000,949,175,14,-71,568,584,112,-607,-261,679,129,-803,-1000,731,702,24,-1000,619,-51,687,725,916,-640,-96,418,-1000,-1000,-409,-368,-83,287,-718,-585,-474,1000,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.String:QVBKS1Q=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.Object):java.lang.Object",
            new int[]{137,1000,293,711,1000,1000,646,146,1000,1000,448,-373,1000,1000,286,-504,587,-320,-826,3,1000,-1000,546,225,-205,1000,-579,-431,-1000,-484,317,695,1000,-90,282,-85,-1000,-472,0,108,-525,-136,-186,-273,-656,-521,-1000,509,40,487,-1000,-493,544,-416,-577,-992,-942,-1000,425,115,-1000,913,164,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.String:QVBKSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.Object):java.lang.Object",
            new int[]{-102,-943,60,31,487,897,54,759,-294,3,456,-205,783,833,-639,179,709,-93,413,219,-467,-809,808,-273,691,651,-24,648,65,404,-539,577,-166,860,-354,536,-468,-472,860,549,712,-265,-835,78,-426,-571,639,-848,-445,-579,323,161,-568,-27,-817,-272,-227,464,409,788,-255,-994,-852,-915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.Object):java.lang.Object",
            new int[]{212,-318,-263,-668,-350,-804,-148,-38,582,120,345,-199,-227,459,-794,-195,423,-436,72,-48,477,-252,888,931,-993,910,37,-902,381,-836,-469,768,391,435,-35,760,769,248,-148,312,600,-475,677,977,-994,-873,559,-94,-792,-944,-247,49,-470,-217,410,243,298,1000,953,890,648,-763,273,989}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.String:UFRLU1JOSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-95,-1000,423,-779,174,-704,257,898,158,-935,306,419,270,687,967,118,960,-823,856,752,-1000,626,-1000,372,-391,-422,879,-922,896,-235,-212,541,1000,-810,-334,-117,761,-84,-143,-1000,1000,-1000,-720,21,402,-72,-747,-538,1000,129,641,-130,469,-353,958,-918,563,-40,-6,321,183,-235,-663,-74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.String:S0tTSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-412,68,1000,159,261,-115,493,633,-556,-439,496,543,1000,127,1000,-43,882,343,1000,723,-1000,-198,-1000,-3,321,541,645,-228,1000,-839,595,-749,271,83,-1000,-53,-610,-859,744,1000,-595,-1000,76,343,-1000,790,-775,-136,855,886,310,29,770,1000,665,-1000,164,806,-852,-1000,-968,220,-890,451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.String:U1BUSlBGSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{304,-393,423,-412,343,-704,-3,898,-213,-935,488,419,814,784,946,685,906,432,664,739,-407,-368,-699,254,-198,756,876,-487,896,-738,175,-711,259,-810,-84,186,658,-969,379,567,-522,-455,175,-134,-553,522,-680,-245,456,-15,-178,-571,709,853,655,-918,563,729,-845,-438,183,119,-373,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.String:VEpLTg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-186,-25,1000,702,-294,404,614,-103,-742,-150,277,-396,-452,-175,134,135,759,73,-19,-218,-15,342,-742,-234,173,-228,-313,-752,846,216,330,-124,118,46,-139,52,156,164,-909,229,412,-260,-475,519,-39,-59,-541,-129,-20,-614,355,-655,1000,-35,-1000,-92,-94,-91,-120,-869,-301,230,-47,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.String:S0ZLSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-186,-530,1000,-273,1000,177,-312,819,883,-1000,-149,839,1000,642,233,1000,468,-312,869,-218,-883,-740,-823,1000,-1000,-228,932,-746,1000,-1000,945,-124,567,46,-1000,526,1000,-1000,767,-707,5,128,-318,685,378,573,-317,300,-360,1000,-77,-655,722,1000,-243,-92,-132,-623,-832,55,1000,-255,-516,930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.String:Tkw=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-788,-1000,635,-117,897,599,-203,192,883,-320,-382,839,1000,1000,1000,1000,468,-515,1000,1000,-311,-184,-889,140,-1000,-88,1000,-1000,494,-1000,622,1000,1000,-279,-1000,186,1000,-379,-168,-1000,67,-1000,-905,1000,364,573,-317,111,1000,849,1000,482,971,380,-174,456,1000,-797,-116,341,232,-156,-760,824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.String:TVNORg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{77,-309,1000,-402,588,495,362,430,-972,-1000,487,662,725,-115,759,529,712,-211,1000,-281,-1000,-496,-1000,-936,-215,851,838,-307,1000,-1000,854,-299,1000,611,-1000,715,1000,-1000,312,1000,-466,-868,-3,1000,-524,707,-383,387,568,567,64,16,1000,1000,47,-1000,-200,-101,-927,-1000,-296,-221,164,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.String:U0tGSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{843,57,1000,-771,202,-523,972,745,-422,-1000,263,-885,-930,-608,-57,-116,972,-356,419,52,-1000,-531,-1000,1000,698,323,-303,-1000,814,703,-99,-151,720,-1000,661,-664,1000,-928,-285,1000,1000,479,-309,157,778,-1000,-1000,94,-860,-627,-429,-1000,54,-598,784,-891,-275,229,-912,211,-13,580,-80,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.String:TEo=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{479,817,1000,-161,408,-535,457,1000,-689,-1000,824,80,400,-569,-310,-116,85,435,453,356,-1000,-633,-564,1000,524,1000,318,-96,1000,-544,731,-1000,-428,-90,-500,166,-127,-1000,1000,1000,-206,-704,650,-154,-484,253,-842,394,-1000,585,-1000,-280,369,1000,329,-1000,-1000,97,-1000,-1000,244,147,-261,167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.String:QVRSSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{963,850,1000,-771,202,177,370,-382,-955,-1000,263,53,-283,300,-497,-49,832,-849,-251,-170,-1000,-531,-974,1000,1000,995,493,3,814,284,-391,-918,720,-1000,-813,-595,-339,-928,-115,1000,32,13,-347,473,30,-574,-678,94,32,-627,554,-755,-862,1000,784,-1000,-275,1000,-632,-919,-846,-811,121,-30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.String:U0tKSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-500,57,1000,-402,-747,319,996,-393,-1000,-719,1000,-885,-852,-179,568,729,1000,1,361,-281,-1000,362,-1000,-1000,931,17,606,-1000,498,-32,-99,-348,542,-364,-133,165,1000,-928,-523,1000,1000,-199,-303,98,532,-974,-845,387,321,-1000,64,-1000,1000,-496,-370,-789,-275,-210,-243,-509,-338,637,164,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.String:TEZSVA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{564,1000,-1000,-18,607,1000,-180,-760,422,592,-1000,142,-827,-383,-678,-1000,-1000,-967,-884,143,804,-248,1000,711,-1000,-562,1000,-35,-633,729,-381,405,563,1000,403,-100,-173,933,-209,1000,-638,34,-957,-523,272,1000,127,205,-343,894,315,1000,-1000,1000,-487,1000,1000,-326,1000,978,945,-1000,279,413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{312,-104,458,320,26,901,710,770,-245,-749,126,875,-894,236,-66,-621,602,809,-995,-417,-136,768,777,690,673,-554,-88,-976,134,709,-668,265,932,-704,104,-595,1,851,25,670,-116,344,-310,-668,462,-989,-672,995,837,406,-235,-233,-315,-244,480,491,-394,-638,943,167,674,506,135,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.String:RlM=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-59,1000,815,-163,-1000,-548,906,-269,-764,853,515,-988,-753,-73,287,391,678,1000,-198,-1000,1000,1000,-1000,-726,1000,-294,-714,-904,-990,672,117,-1000,-1000,82,972,-816,915,371,-1000,-400,-594,-15,-150,-288,-1000,-303,-932,1000,-714,-1000,1000,-951,1000,430,-1000,458,-1000,964,295,-1000,-1000,885,213,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.String:S1M=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{549,1000,-400,-121,199,407,461,-72,-186,82,225,-520,-708,-1000,-1000,-911,509,-233,-1000,-14,1000,15,368,-520,462,-157,-216,206,-365,-898,-360,-756,-862,695,577,187,-64,65,225,-116,-153,1000,-219,-1000,71,470,-436,1000,-1000,-579,-420,-280,-566,1000,647,946,-1000,387,-153,138,400,-166,390,-26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-517,352,713,-75,760,20,507,-616,829,15,-366,453,-496,585,-427,103,-4,-143,-716,-501,523,-329,-285,314,-744,-871,-820,-350,-61,492,-163,91,230,-677,352,-99,228,-942,167,991,504,-460,771,879,-548,-500,838,-379,807,427,-811,-572,-234,-158,152,-495,305,-425,67,-418,-976,-644,-524,517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "getMaxCodeLen():int",
            new int[]{325,843,478,-682,-505,-707,638,24,958,-630,180,264,493,-787,194,-192,604,-342,375,689,751,-757,572,-646,55,-14,547,808,-483,581,812,-474,415,-988,179,703,-65,-718,226,482,345,931,-727,-781,34,-654,656,-472,-372,-535,93,911,17,-582,767,-914,-350,-114,376,792,-291,-470,-146,-191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-1000,-577,970,-674,-219,297,-352,-765,-715,855,-1000,-493,-211,1000,-1000,-80,442,-142,387,1000,-959,-1000,1000,-382,-998,-213,758,-300,-784,-813,-55,1000,931,1000,-495,352,-1000,-1000,-532,-1000,-398,-391,712,1000,706,594,190,-269,-466,904,-467,935,-497,1000,-73,893,377,383,318,498,-477,1000,-160,-273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-15,733,-625,-979,984,160,-307,-1000,290,1000,394,780,127,-529,-409,-264,-632,211,289,-302,-1000,-157,-298,-559,-195,-258,-730,-285,496,-1000,168,-798,721,-1000,1000,-616,139,1000,322,-1000,-197,-529,198,-763,-1000,222,-57,-164,396,-570,359,-1000,1000,1000,324,534,1000,287,95,-460,1000,216,444,997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-1000,-1000,270,-82,-16,-1000,-430,-1000,-1000,935,-1000,-545,-1000,-52,-1000,-1000,46,381,1000,909,-394,-630,558,491,-995,1000,846,-1000,742,-1000,632,280,1000,837,-13,151,-1000,-1000,-1000,-1000,771,-284,-432,640,60,625,1000,-1000,90,1000,899,615,843,933,-1000,750,324,1000,1000,853,-941,528,-280,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-35,-1000,-430,186,584,-1000,-546,-428,-253,538,-252,411,-1000,367,-757,-647,-69,604,765,165,-1000,-22,-42,887,-923,936,-86,-524,-359,-1000,175,232,264,395,-698,-379,-551,-1000,-170,-416,-6,-844,-432,910,346,-499,856,-843,90,922,-187,476,143,242,-1000,113,633,546,974,-363,-87,586,633,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{750,253,75,46,574,-772,71,-885,1000,527,47,764,127,-467,-712,-589,18,538,1000,-821,-131,-276,-10,-367,-955,471,-439,-230,507,-330,292,-328,-73,-1000,-884,-443,-307,909,236,-698,31,-975,-564,272,-64,-229,822,301,-68,341,-282,476,-497,980,-441,893,1000,763,-1000,351,659,310,853,998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-917,603,-865,-979,1000,-66,914,54,393,1000,343,-783,-370,77,-1000,147,-1000,530,-1000,1000,-1000,990,195,-271,-175,-268,474,706,-399,-1000,1000,-646,441,102,1000,-651,264,127,739,-974,-638,1000,244,286,-1000,1000,-1000,-351,1000,-1000,221,-1000,1000,1000,357,-1000,1000,-174,277,1000,1000,615,-412,828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{324,560,-510,575,-890,-240,-790,-1000,477,98,-181,-1000,-1000,-1000,830,-1000,1000,237,898,-479,-192,-1000,-457,376,499,508,356,-436,1000,1000,-458,195,1000,985,1000,-362,-718,1000,-272,268,1000,-332,400,-1000,1000,-213,1000,810,-348,573,1000,796,-135,-192,-1000,325,743,1000,339,1000,-886,-409,361,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-531,-1000,-1000,44,1000,-1000,-119,-914,-1000,966,644,1000,127,-1000,-1000,-1000,-1000,1000,1000,325,174,1000,-352,-267,-1000,1000,-363,-1000,1000,-724,1000,-1000,1000,-1000,1000,-688,109,-615,-940,-1000,-197,-161,-1000,1000,-847,1000,-158,-1000,-1000,1000,881,65,1000,1000,-1000,18,709,-104,-292,140,-331,213,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-400,-1000,270,-82,541,-502,399,28,-829,81,60,1000,-448,-52,-1000,-262,-281,652,422,312,-915,942,222,71,-1000,435,-367,11,-1000,-1000,-50,280,-260,-547,-722,-598,-72,-1000,-1000,-910,-250,-127,-148,1000,1000,467,227,-982,90,1000,-1000,179,12,299,-351,-836,-613,-305,623,-517,88,-42,-96,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-4,-38,149,139,234,86,-486,-1000,358,619,-823,-249,-1000,-1000,877,-1000,769,228,464,-432,-381,-461,-299,414,625,113,34,-418,1000,994,24,-551,207,-6,153,300,-758,449,-187,-869,1000,-458,-214,-629,-580,-440,1000,249,611,430,1000,13,-877,-381,-613,337,367,978,172,570,-186,-320,1000,631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{1000,361,-625,753,-95,331,29,-1000,-715,-8,-478,251,-662,-1000,-882,-589,801,1000,1000,-837,784,23,-370,229,-1000,-213,3,-1000,507,788,-476,-1000,1000,-1000,321,-395,186,1000,-562,-515,-38,-238,-1000,-56,646,154,190,-485,-1000,1000,1000,1000,-82,806,-1000,113,1000,1000,-1000,922,-251,-623,681,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-287,-1000,1000,-582,16,-113,573,-657,-573,433,529,921,-1000,574,71,-779,-69,270,518,-300,-724,-35,-759,1000,-362,400,-343,-1000,38,-588,-80,201,765,840,21,-405,511,-1000,-676,-317,-202,-416,-362,-193,288,-886,20,-310,-968,830,741,-263,292,-178,-263,73,-213,-120,-193,-157,34,-679,-277,180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-1000,-577,970,-641,-571,331,-107,-985,-715,512,-1000,-413,-211,640,-791,-194,662,-104,387,875,-959,-1000,869,-327,-1000,-213,608,-266,-658,-813,1,1000,931,704,-193,169,-1000,-1000,-505,-1000,-115,-391,434,574,1000,715,190,-57,-1000,942,-418,888,110,1000,-73,113,-211,259,140,858,-477,1000,-566,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-757,590,535,-355,-157,726,945,-663,-265,347,-362,-681,610,205,-450,172,-736,-446,-251,132,-843,-410,688,-303,36,-861,-201,567,-821,-915,867,-280,-533,-865,301,-205,-299,688,671,-590,-613,839,711,-419,-459,797,-654,963,972,-590,-759,-712,-13,885,892,-919,483,-726,-681,503,989,871,69,995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-1000,-1000,1000,-13,-1000,-481,-193,-985,-1000,466,-1000,-1000,-1000,-934,-1000,-709,1000,14,885,63,-534,-1000,1000,408,-1000,667,1000,-767,-485,-813,325,1000,1000,1000,-902,705,-1000,-1000,-1000,-1000,563,-219,-7,1000,1000,996,930,-642,-1000,1000,-40,1000,0,953,-1000,264,-685,851,774,1000,-1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-1000,250,72,-335,418,-752,-908,623,-770,703,776,72,-901,-142,-362,-1000,-537,473,-1000,-734,477,986,371,-312,-1000,-7,1000,-1000,156,189,1000,751,597,-81,892,1000,964,-755,-803,417,824,114,1000,1000,633,-599,889,-671,-1000,-1000,-470,-1000,-686,-206,364,-645,577,-296,328,-434,30,-1000,146,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{1000,-862,-575,-73,90,-191,-385,-948,-433,336,525,1000,539,1000,-791,-99,-312,-603,-1000,-123,-359,-223,-301,-957,198,-1000,752,68,149,1000,-152,187,-572,1000,1000,1000,802,819,-774,27,-538,527,190,1000,-851,1000,30,8,739,1000,1000,-279,265,1000,101,-92,1000,-1000,855,-1000,491,-854,245,558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-329,795,317,329,-637,131,847,642,-973,551,743,236,-371,48,701,397,686,330,548,-760,248,-422,190,39,-145,642,-735,345,365,-963,-106,-266,394,-949,-323,-802,-756,-921,136,85,669,891,-824,-683,607,-20,815,120,-929,-672,-568,-48,-162,-993,-923,-544,-773,863,-967,306,-657,-798,716,-368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-585,717,-478,409,418,-421,-180,623,-830,703,776,385,-289,333,-362,578,239,921,374,-734,148,-595,-167,-272,248,931,-482,295,416,-1000,332,-319,440,-906,-478,-979,-81,-1000,687,-240,824,508,-877,-233,-415,286,661,-447,-515,-1000,-613,307,-111,-1000,-606,-847,-830,831,-287,440,-760,-226,864,-234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-1000,396,-1000,317,276,-873,644,1000,-808,114,-198,1000,-16,170,-98,1000,-321,828,600,-971,931,-426,-1000,-908,-136,990,-885,-209,182,-463,651,448,295,729,-1000,267,1000,-1000,380,192,490,431,-1000,798,-156,883,283,164,130,-590,-728,1000,-596,-749,-60,-705,413,496,263,326,-1000,614,717,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-775,-1000,-1000,-977,539,-863,-365,1000,-825,-1000,-1000,677,1000,1000,-1000,331,-83,428,1000,1000,-281,-1000,-1000,-909,1000,1000,-317,-1000,1000,-828,647,875,-1000,1000,-581,778,-369,-105,951,-1000,-1000,105,-1000,1000,-1000,516,-1000,-1000,1000,536,-1000,440,569,25,159,1000,1000,94,501,-1000,1000,718,784,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-213,-150,1000,347,-830,757,1000,717,-1000,-912,619,617,-123,1000,-232,1000,668,256,1000,-1000,301,-1000,-1000,-688,1000,1000,-469,350,1000,-196,54,-384,-243,132,535,646,950,12,-598,-1000,278,1000,-1000,-132,144,821,-25,-1000,0,506,-823,819,205,-418,-854,-156,1000,49,56,59,-769,-842,1000,374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-346,-128,70,372,453,-873,-513,644,-433,136,416,966,-710,1000,-98,1000,225,19,-739,-971,772,-426,-1000,-908,-443,-354,688,-267,765,129,878,693,735,398,-279,259,585,-202,-88,192,490,181,25,798,679,341,634,728,130,-282,218,-69,-596,-22,-60,-705,109,-495,790,-101,-305,345,100,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-1000,433,-665,461,1000,303,829,390,-907,-102,-759,871,322,-1000,170,521,-963,-2,-583,-83,670,125,-214,111,-1000,212,-297,-627,297,180,585,1000,-179,193,-1000,912,86,-1000,-786,-24,-352,-87,398,-740,605,-517,598,1000,339,160,-826,552,-1000,-1000,-1000,205,314,158,-456,287,-125,-216,69,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-685,-656,-1000,-595,573,235,909,392,-665,-402,-523,858,677,672,-854,315,-30,256,-132,184,195,-342,-836,-574,393,374,-21,-745,404,858,54,810,-449,143,-216,858,321,-1000,-315,-332,-676,493,-167,-288,-843,-19,-25,-465,817,123,-461,151,-381,-641,-854,236,1000,49,-96,-829,250,-69,497,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-1000,-619,-1000,-147,252,-999,-454,1000,-642,796,1000,1000,-1000,970,-911,532,106,895,1000,-1000,1000,-1000,-1000,-1000,280,556,611,-1000,1000,-148,1000,-21,1000,1000,806,819,1000,1000,871,-1000,522,708,-1000,1000,-364,1000,-133,-1000,271,-417,-387,272,992,-142,54,-228,1000,-548,1000,-52,-660,48,529,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{1000,-1000,512,-745,-15,1000,-554,320,-817,-434,961,716,484,1000,701,1000,267,-81,1000,204,-1000,-1000,-981,-889,1000,269,-877,848,476,313,-106,-431,-164,313,684,-802,-877,-921,1000,-618,-388,782,-1000,98,-1000,894,-1000,-1000,952,624,571,855,-162,822,-1000,214,-773,27,585,-1000,555,663,1000,797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{605,195,655,-295,-493,676,1000,-129,-1000,-1000,-752,655,1000,1000,553,1000,-344,597,767,-396,-954,-754,-863,189,1000,917,-1000,1000,718,158,-1000,335,-1000,-815,-1000,84,-1000,-716,75,-268,-642,-387,-1000,-1000,-1000,-92,-322,-789,1000,563,-720,1000,-400,-261,-1000,552,-84,39,-919,-961,-57,-187,947,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-1000,881,1000,139,344,175,-492,-627,-471,866,-393,958,506,103,763,-820,-424,355,-105,-381,641,472,1000,252,-1000,-811,-794,1000,-561,-424,85,191,-409,1000,597,-213,1000,-491,-978,-567,-576,110,1000,-231,229,1000,1000,1000,-677,-283,-224,148,-707,166,-933,-480,1000,-569,176,822,-583,917,990,642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-1000,1000,-575,1000,565,-133,-63,815,-981,192,193,914,-514,-1000,1000,1000,-1000,446,-7,-911,894,-336,-328,-187,-627,507,-504,17,-188,-1000,1000,581,601,162,-1000,367,562,-1000,-100,-491,487,-23,-185,-249,940,196,1000,1000,-43,-1000,-740,755,-846,-1000,-676,-660,-98,119,-119,1000,-1000,951,288,45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-962,-284,-283,271,-536,-995,-365,916,-770,-129,45,863,-516,393,-222,676,-153,960,-580,-655,-183,-862,-791,-796,-9,766,688,-267,664,-453,647,534,178,344,-326,-148,585,-789,665,-996,-404,-230,-987,798,-364,977,-94,-365,824,-459,-896,340,-811,-697,-60,-8,69,-269,847,-1,-282,366,680,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("VOID|getMaxCodeLen=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "setMaxCodeLen(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
