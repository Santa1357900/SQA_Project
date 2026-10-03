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
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{27,-267,-1000,-494,-258,689,1000,533,-1000,446,-1000,-1000,-1000,-5,-594,-234,1000,-560,-533,-1000,1000,-653,-1000,720,114,298,-187,20,226,1000,-369,-539,292,345,-87,185,-955,-746,-303,-451,-312,-117,1000,-1000,531,-361,987,-856,199,145,89,520,-250,108,1000,-399,928,-1000,463,683,-1000,27,1000,-850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-1000,156,-675,-97,-200,-418,898,798,-1000,-487,-1000,-918,-260,-480,125,-663,750,-753,-639,400,-1000,-222,-298,770,344,411,842,-107,165,-538,633,-1000,-46,163,-62,913,-752,-786,245,-1000,527,-166,1000,-526,260,98,949,381,366,-763,-247,270,-141,357,840,-808,163,-457,812,1000,-1000,795,791,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-593,402,824,130,538,-238,-846,182,290,581,132,650,742,548,-563,-892,552,-489,-836,68,556,-528,-965,-566,-359,-130,868,-267,940,218,927,-169,971,-19,-561,-985,-347,696,-890,-112,-652,-241,754,-670,8,-934,333,-56,-668,728,-406,-137,-572,473,309,574,887,-986,-355,502,-590,655,-386,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{1000,-720,725,836,-834,-562,441,-1000,-672,-153,-629,-467,-713,604,-263,-196,1000,-514,510,-755,986,-620,-651,701,-1000,582,-833,114,1000,-538,1000,-121,35,880,421,-1000,-210,-684,96,462,-1000,25,-680,-1000,916,-479,981,540,370,1000,-260,-1000,-1000,-936,1000,1000,952,-381,-941,-208,234,1000,-109,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{-372,-1000,1000,181,-171,-866,1000,911,999,-811,1000,669,-164,-1000,1000,1000,936,1000,553,-311,-91,-1000,343,-1000,-1000,603,-1000,-1000,1,-1000,1000,626,24,754,-63,-623,-793,992,1000,-177,1000,-436,826,-1000,-892,798,186,-344,-352,531,216,-245,813,-1000,-830,150,798,706,1000,-837,-992,-197,-188,885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{-207,170,1000,-466,1000,-750,242,-771,1000,-1000,141,152,-861,-87,-163,-360,692,-20,605,-14,684,-310,-576,-346,427,29,1000,1000,-983,-64,-172,517,-1000,-43,-688,22,20,-1000,727,939,196,-967,222,-999,963,254,353,1000,25,138,560,544,74,-597,285,-967,351,-824,-330,405,1000,-688,237,388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{-1000,-412,666,306,48,186,849,919,887,-76,-562,-621,-1000,476,-662,-326,673,46,1000,-61,1000,504,61,-706,-604,1000,839,-1000,-693,-741,250,248,-919,585,-702,498,-218,1000,320,252,897,-1000,884,-450,897,323,-293,16,566,568,1000,-112,-627,43,42,-555,-1000,468,-540,572,-330,5,1000,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{953,-991,1000,-288,484,-692,528,649,1000,-1000,1000,-13,-1000,-1000,1000,699,850,858,564,-586,-397,-332,73,-415,-1000,0,-1000,-675,77,-726,679,-244,384,-624,567,-936,-1000,-31,1000,765,1000,-1000,1000,-1000,244,871,636,-324,313,-611,500,705,997,-713,-1000,-323,460,-1000,1000,-597,129,-1000,-617,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{180,-316,-44,1000,1000,1000,662,558,89,-181,317,-279,803,430,-745,1000,-233,-358,-834,-681,-1000,-395,247,-289,-298,-304,-1000,153,267,-243,-438,-420,-229,-88,-625,422,-243,-1000,-1000,95,-359,511,-552,-715,-193,-1000,701,1000,-786,475,1000,-663,945,1000,-1000,-128,962,80,-460,348,-1000,-1000,-629,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{196,279,882,966,400,-43,330,106,-1000,234,-693,-162,193,-181,-17,237,-772,400,-803,1000,-840,84,-182,-212,-485,-155,-290,-199,-1,-538,-1,89,359,-82,-465,-221,578,-785,-127,-488,-10,218,373,-940,-20,1000,99,179,27,-470,-131,393,698,1000,463,-255,-120,-30,-627,-252,-841,-400,342,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{537,797,-436,-856,-505,290,-486,-824,498,569,776,-641,-105,310,-761,404,49,-165,-707,436,770,-792,-731,927,-606,-439,291,-881,-7,304,870,111,889,578,515,-906,-637,684,635,54,-737,50,134,-320,-926,769,185,159,928,-287,-80,116,-971,-167,19,-990,-984,332,513,986,-116,138,516,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{793,173,725,-242,-124,-597,532,162,105,-87,-1000,1000,818,-789,635,501,-1000,-666,-1000,-500,55,-1000,1000,932,275,-380,-1000,281,558,-1000,-1000,429,-368,881,-1000,-1000,-1000,-253,-690,962,879,-1000,1000,-458,-1000,1000,1000,683,422,1000,-1000,-1000,428,-251,1000,1000,-1000,557,1000,1000,365,475,745,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,4,1000,-799,-444,397,660,452,759,-39,-1000,-28,-220,231,1000,1000,-818,1000,-420,-1000,-1000,-994,42,190,-152,-154,-1000,1000,-1000,-725,-1000,-266,-1000,173,-488,-250,294,728,-356,-385,816,-163,-376,1000,-915,1000,-999,285,584,-619,-663,-1000,357,-1000,64,924,-1000,816,1000,307,-1000,1000,-588,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-890,333,-1000,236,150,1000,-878,1000,424,-577,-341,463,96,677,-200,-124,-806,-1000,1000,-121,575,184,219,-932,163,200,-487,-1000,-1000,-1000,-546,-1000,-1000,339,103,293,-148,-115,-809,-73,222,304,435,-621,695,845,1000,-479,12,1000,224,494,447,153,811,-175,849,-875,1000,242,-33,-520,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-116,-890,334,1000,372,166,-1000,-276,-653,305,280,1000,-1000,-699,-1000,1000,-884,-139,36,695,1000,1000,126,-23,-28,838,-12,-396,532,1000,292,1000,-144,-406,-475,1000,968,-245,419,-1000,-570,1000,-1000,46,-1000,-1000,662,818,-1000,953,752,-375,573,1000,-1000,1000,-1000,-748,-196,-787,-241,331,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{348,715,-401,675,94,-105,190,744,197,-609,-822,-107,576,144,-714,-318,-691,-212,591,-59,-597,-662,989,-302,-321,249,-512,-979,-890,-724,-672,-976,77,554,-98,263,265,872,-590,343,-882,-840,236,-347,-829,921,141,-134,917,516,558,-937,354,-968,489,179,855,764,816,-168,-401,-698,-806,540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{-73,184,915,-63,-1000,-297,31,-40,-276,295,-92,445,61,-15,-508,-594,1000,341,-280,558,-86,518,-340,-664,-182,-287,923,615,-150,-843,414,-1000,0,1000,105,-473,-586,83,-836,885,343,-942,947,-939,-22,704,389,185,374,974,-133,553,782,-904,503,659,206,472,861,1000,294,-458,-965,257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,1000,1000,-817,-482,-1000,291,-558,944,749,38,-955,-339,-426,-322,-16,584,469,-289,-141,468,1000,-182,-179,-1000,-53,1000,884,-361,-1000,968,-724,-1000,884,666,-706,814,1000,-729,-265,864,-1000,553,451,620,772,121,495,-522,1000,514,-739,153,-1000,2,1000,600,-908,1000,1000,1000,-1000,435,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{143,-983,1000,-785,-146,-1000,230,406,1000,-235,-266,395,-719,419,-480,227,-246,615,169,-93,1000,-373,-72,735,-276,-195,51,1000,-1000,-1000,-301,1000,1000,-434,-558,362,786,-36,-1000,-1000,-129,-1000,318,-394,135,1000,-808,-150,226,106,-914,-1000,507,-1000,644,-380,-132,55,-417,1000,1000,-1000,-121,-711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{550,-139,777,733,-113,344,-1000,815,-910,478,-132,550,-743,90,-483,-878,711,-182,941,437,-80,796,1000,998,1000,489,330,226,-614,-1000,65,-556,1000,-133,-322,-1000,164,1000,-884,-592,577,-1000,27,-914,-739,298,-1000,-388,580,549,-1000,-192,346,-410,-229,457,-870,-229,-938,-123,-370,-1000,-189,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{669,-139,439,733,-932,344,402,-708,601,337,-168,550,679,-595,-713,-404,711,-402,-137,-43,533,942,-861,-640,-833,586,948,966,778,-950,71,-824,-255,981,431,-333,-854,-411,-884,777,618,-514,670,-437,554,65,473,892,-75,727,759,-203,604,295,-162,-17,13,-248,424,849,174,105,626,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{589,145,572,-256,1000,-487,-763,-551,1000,-139,-309,-510,-22,-647,557,-342,1000,-216,24,-28,-736,-226,154,240,-65,182,889,325,-210,883,-745,-115,-1000,346,877,-257,558,-954,-264,-27,-1000,-816,-547,667,-459,1000,36,-84,190,-431,600,-1000,16,1000,-131,-405,143,-909,-166,-698,-889,1000,-622,-987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-306,733,-96,-1000,-162,1000,-212,1000,253,-412,821,445,-596,-948,-244,-2,-306,-153,1000,173,317,759,-1000,741,1000,1,-931,133,-166,1000,-1000,427,-245,-408,79,14,-637,-813,-254,-1000,-1000,516,-132,807,-498,181,925,-91,390,540,231,182,-474,-612,-121,-1000,-333,-1000,957,-719,-340,496,734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{40,-390,808,-421,1000,-498,154,-62,252,-166,-949,-188,515,887,10,318,950,-472,402,-1000,-488,453,225,919,37,-567,716,-1000,-22,1000,-1000,307,362,683,620,-1000,447,-400,782,-276,-1000,-191,-346,1000,-1000,908,232,-1000,-236,158,1000,-291,562,30,1000,-494,288,463,289,-897,717,554,-303,-590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.CombiningEvaluator$And", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{485,-719,-370,262,-1000,1000,-525,544,286,-1000,1000,-904,212,-224,209,-717,607,366,769,359,672,-512,-240,318,-1000,-379,-206,-1000,-101,834,-252,846,-160,-556,-476,-336,71,1000,-1000,-669,634,198,708,175,-1000,576,337,-530,1000,581,1000,-206,-804,628,-1000,-179,-1000,489,-652,-666,55,76,990,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{-610,-1000,213,569,-212,965,468,567,-449,-616,279,-135,-213,-807,577,-714,-865,799,-573,166,-564,239,87,-260,800,437,-1000,-206,1000,-108,-971,263,1000,529,-407,226,-785,-187,-1000,477,-561,-569,131,-457,-200,-1000,709,432,-1000,650,-531,-611,-1000,-630,-946,-678,-58,89,-1000,285,584,-737,1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.CombiningEvaluator$And", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{-98,-853,-508,240,-888,490,1000,1000,-1000,-1000,701,-400,584,-392,-1000,-409,827,-563,-944,-121,-1000,347,-836,-1000,597,-613,612,-295,1000,-636,840,-1000,-1000,1000,1000,698,-881,-1000,-1000,-186,-22,-258,-1000,-528,708,-668,946,-159,-769,704,100,638,-680,635,-506,-951,-1000,421,338,-369,921,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{270,-502,-283,48,-456,-88,-1000,1000,364,507,623,-333,449,674,528,-992,792,-381,1000,388,1000,-1000,367,1000,-264,-300,-717,-1000,937,1000,159,1000,-100,-304,-1000,215,312,1000,364,-1000,204,85,1000,1000,-1000,103,-69,-1000,738,902,1000,410,-411,342,-615,819,460,105,-1000,-837,-1000,1000,-326,585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{27,-267,-1000,-494,-258,689,1000,533,-1000,446,-1000,-1000,-1000,-5,-594,-234,1000,-560,-533,-1000,1000,-653,-1000,720,114,298,-187,20,226,1000,-369,-539,292,345,-87,185,-955,-746,-303,-451,-312,-117,1000,-1000,531,-361,987,-856,199,145,89,520,-250,108,1000,-399,928,-1000,463,683,-1000,27,1000,-850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-1000,156,-675,-97,-200,-418,898,798,-1000,-487,-1000,-918,-260,-480,125,-663,750,-753,-639,400,-1000,-222,-298,770,344,411,842,-107,165,-538,633,-1000,-46,163,-62,913,-752,-786,245,-1000,527,-166,1000,-526,260,98,949,381,366,-763,-247,270,-141,357,840,-808,163,-457,812,1000,-1000,795,791,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-593,402,824,130,538,-238,-846,182,290,581,132,650,742,548,-563,-892,552,-489,-836,68,556,-528,-965,-566,-359,-130,868,-267,940,218,927,-169,971,-19,-561,-985,-347,696,-890,-112,-652,-241,754,-670,8,-934,333,-56,-668,728,-406,-137,-572,473,309,574,887,-986,-355,502,-590,655,-386,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{1000,-720,725,836,-834,-562,441,-1000,-672,-153,-629,-467,-713,604,-263,-196,1000,-514,510,-755,986,-620,-651,701,-1000,582,-833,114,1000,-538,1000,-121,35,880,421,-1000,-210,-684,96,462,-1000,25,-680,-1000,916,-479,981,540,370,1000,-260,-1000,-1000,-936,1000,1000,952,-381,-941,-208,234,1000,-109,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{493,-936,-1000,-1000,-140,52,-280,-443,-399,-550,-1000,65,300,316,-1000,-385,-990,-3,188,-1000,419,-1000,141,-239,-392,28,-1000,-552,-1000,-459,-1000,-179,-684,-914,-876,439,-1000,-1000,567,176,-154,-1000,-444,287,69,-10,-527,1000,462,-122,-630,313,-75,-597,22,-28,-728,-1000,183,445,1000,189,672,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{294,768,-77,108,-687,-573,-593,-128,80,-387,-761,-593,-410,-461,-566,190,-405,-165,923,1000,213,-1000,-430,-19,-211,196,954,48,-636,-206,647,-96,-1000,-124,-139,-118,1000,897,-1000,-42,-18,-20,439,195,-1000,-1000,1000,-303,496,712,-148,-761,-468,345,-407,134,403,-324,32,269,187,-403,-303,601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-476,-663,499,707,-406,1000,298,-443,-354,-550,854,1000,-410,-491,1000,-385,291,-126,754,400,420,538,568,951,-907,53,-1000,-45,-482,-900,-822,1000,1000,-711,1000,-140,456,-1000,-396,762,-42,76,-444,287,1000,-450,113,672,-133,230,-379,38,773,-597,-402,212,-134,1000,-269,-1000,483,-3,-400,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-260,-657,-1000,-61,-882,787,47,-239,-713,-988,-144,77,-145,-408,-448,166,-917,794,802,-942,-622,-240,-605,-237,559,-776,-213,-831,-591,-1000,791,866,-300,1000,942,-322,612,135,31,-601,829,880,681,-920,-8,-1000,-547,433,1000,-375,224,592,447,1000,567,-882,500,316,-771,902,123,336,204,897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "selectFirst(java.lang.String):org.jsoup.nodes.Element",
            new int[]{1000,786,314,-935,684,1000,373,-963,-10,-882,-957,425,-284,-1000,774,-814,576,1000,948,277,817,154,-967,954,-1000,1000,1000,1000,-343,-208,-1000,-919,-1000,-1000,-422,-450,734,-283,-239,-1000,1000,-1000,-587,1000,-559,1000,517,-575,841,-1000,583,372,1000,650,1000,-44,14,-336,-1000,11,-400,-1000,-835,175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "selectFirst(java.lang.String):org.jsoup.nodes.Element",
            new int[]{20,-783,314,352,747,584,78,1000,-1000,-156,-1000,-858,-284,-1000,398,-467,533,589,1000,385,326,47,613,1000,-4,400,832,1000,-495,746,-1000,-993,-972,-214,730,-359,279,251,-1000,-941,1000,-1000,-1000,576,-771,1000,813,636,-220,-497,-574,-370,1000,277,1000,1000,343,1000,-1000,-274,93,-772,-1000,-437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "selectFirst(java.lang.String):org.jsoup.nodes.Element",
            new int[]{1000,-243,585,-565,-164,917,-435,-963,-549,-465,-707,187,-292,-497,568,-293,-182,740,948,110,339,134,-295,704,-1000,1000,1000,-7,199,188,-913,-700,-429,-1000,-1000,769,693,-431,-834,-1000,-26,-847,-1000,603,-704,1000,-771,253,752,-850,182,-941,401,333,560,-92,30,-336,-303,286,-400,-1000,86,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{-372,-1000,1000,181,-171,-866,1000,911,999,-811,1000,669,-164,-1000,1000,1000,936,1000,553,-311,-91,-1000,343,-1000,-1000,603,-1000,-1000,1,-1000,1000,626,24,754,-63,-623,-793,992,1000,-177,1000,-436,826,-1000,-892,798,186,-344,-352,531,216,-245,813,-1000,-830,150,798,706,1000,-837,-992,-197,-188,885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{-207,170,1000,-466,1000,-750,242,-771,1000,-1000,141,152,-861,-87,-163,-360,692,-20,605,-14,684,-310,-576,-346,427,29,1000,1000,-983,-64,-172,517,-1000,-43,-688,22,20,-1000,727,939,196,-967,222,-999,963,254,353,1000,25,138,560,544,74,-597,285,-967,351,-824,-330,405,1000,-688,237,388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{-1000,-412,666,306,48,186,849,919,887,-76,-562,-621,-1000,476,-662,-326,673,46,1000,-61,1000,504,61,-706,-604,1000,839,-1000,-693,-741,250,248,-919,585,-702,498,-218,1000,320,252,897,-1000,884,-450,897,323,-293,16,566,568,1000,-112,-627,43,42,-555,-1000,468,-540,572,-330,5,1000,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{953,-991,1000,-288,484,-692,528,649,1000,-1000,1000,-13,-1000,-1000,1000,699,850,858,564,-586,-397,-332,73,-415,-1000,0,-1000,-675,77,-726,679,-244,384,-624,567,-936,-1000,-31,1000,765,1000,-1000,1000,-1000,244,871,636,-324,313,-611,500,705,997,-713,-1000,-323,460,-1000,1000,-597,129,-1000,-617,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{180,-316,-44,1000,1000,1000,662,558,89,-181,317,-279,803,430,-745,1000,-233,-358,-834,-681,-1000,-395,247,-289,-298,-304,-1000,153,267,-243,-438,-420,-229,-88,-625,422,-243,-1000,-1000,95,-359,511,-552,-715,-193,-1000,701,1000,-786,475,1000,-663,945,1000,-1000,-128,962,80,-460,348,-1000,-1000,-629,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{196,279,882,966,400,-43,330,106,-1000,234,-693,-162,193,-181,-17,237,-772,400,-803,1000,-840,84,-182,-212,-485,-155,-290,-199,-1,-538,-1,89,359,-82,-465,-221,578,-785,-127,-488,-10,218,373,-940,-20,1000,99,179,27,-470,-131,393,698,1000,463,-255,-120,-30,-627,-252,-841,-400,342,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{537,797,-436,-856,-505,290,-486,-824,498,569,776,-641,-105,310,-761,404,49,-165,-707,436,770,-792,-731,927,-606,-439,291,-881,-7,304,870,111,889,578,515,-906,-637,684,635,54,-737,50,134,-320,-926,769,185,159,928,-287,-80,116,-971,-167,19,-990,-984,332,513,986,-116,138,516,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{793,173,725,-242,-124,-597,532,162,105,-87,-1000,1000,818,-789,635,501,-1000,-666,-1000,-500,55,-1000,1000,932,275,-380,-1000,281,558,-1000,-1000,429,-368,881,-1000,-1000,-1000,-253,-690,962,879,-1000,1000,-458,-1000,1000,1000,683,422,1000,-1000,-1000,428,-251,1000,1000,-1000,557,1000,1000,365,475,745,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,4,1000,-799,-444,397,660,452,759,-39,-1000,-28,-220,231,1000,1000,-818,1000,-420,-1000,-1000,-994,42,190,-152,-154,-1000,1000,-1000,-725,-1000,-266,-1000,173,-488,-250,294,728,-356,-385,816,-163,-376,1000,-915,1000,-999,285,584,-619,-663,-1000,357,-1000,64,924,-1000,816,1000,307,-1000,1000,-588,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-890,333,-1000,236,150,1000,-878,1000,424,-577,-341,463,96,677,-200,-124,-806,-1000,1000,-121,575,184,219,-932,163,200,-487,-1000,-1000,-1000,-546,-1000,-1000,339,103,293,-148,-115,-809,-73,222,304,435,-621,695,845,1000,-479,12,1000,224,494,447,153,811,-175,849,-875,1000,242,-33,-520,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-116,-890,334,1000,372,166,-1000,-276,-653,305,280,1000,-1000,-699,-1000,1000,-884,-139,36,695,1000,1000,126,-23,-28,838,-12,-396,532,1000,292,1000,-144,-406,-475,1000,968,-245,419,-1000,-570,1000,-1000,46,-1000,-1000,662,818,-1000,953,752,-375,573,1000,-1000,1000,-1000,-748,-196,-787,-241,331,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{348,715,-401,675,94,-105,190,744,197,-609,-822,-107,576,144,-714,-318,-691,-212,591,-59,-597,-662,989,-302,-321,249,-512,-979,-890,-724,-672,-976,77,554,-98,263,265,872,-590,343,-882,-840,236,-347,-829,921,141,-134,917,516,558,-937,354,-968,489,179,855,764,816,-168,-401,-698,-806,540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-660,1000,421,-1000,-682,-1000,293,-632,101,940,-711,-1000,-918,-1000,-1000,755,-1000,-1000,-622,-1000,1000,1000,-278,1000,-82,-833,1000,-399,1000,1000,-346,-1000,217,-994,-1000,291,-1000,1000,-833,189,-985,1000,216,-1000,-1000,994,-1000,-1000,-505,-453,-33,679,1000,1000,-36,-240,1000,34,-805,-857,1000,-390,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{299,450,653,956,-852,-133,677,357,577,645,41,319,-783,440,-199,-961,-663,562,-879,-977,-72,303,488,-477,-871,497,-804,805,-856,-620,506,-923,-145,-511,291,-885,-471,-396,-576,448,266,542,689,256,112,-2,-445,94,-867,-447,841,-17,-829,495,-54,475,491,133,134,876,-940,942,177,-31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{-252,-23,1000,1000,111,788,385,-291,1000,1000,346,319,-552,-906,-451,-1000,-7,338,-1000,-1000,-972,677,629,-477,645,131,-1000,267,-1000,1000,1000,-32,-940,-193,-69,-717,-411,-1000,532,746,242,589,906,113,-1000,-741,1000,-774,-1000,-1000,755,-1000,258,421,611,999,-797,-847,-1000,861,-1000,1000,89,920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{233,-706,-1000,-990,851,211,-219,1000,-1000,-1000,-379,1000,-321,237,1000,213,856,565,-195,-846,-1000,-872,-542,-608,-485,621,500,-353,774,1000,766,1000,191,-361,-863,-1000,1000,-1000,1000,-1000,212,-294,-777,-445,-22,-325,1000,-776,-877,1000,-161,-840,113,435,-68,134,664,-20,208,738,439,1000,-1000,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{-73,184,915,-63,-1000,-297,31,-40,-276,295,-92,445,61,-15,-508,-594,1000,341,-280,558,-86,518,-340,-664,-182,-287,923,615,-150,-843,414,-1000,0,1000,105,-473,-586,83,-836,885,343,-942,947,-939,-22,704,389,185,374,974,-133,553,782,-904,503,659,206,472,861,1000,294,-458,-965,257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,1000,1000,-817,-482,-1000,291,-558,944,749,38,-955,-339,-426,-322,-16,584,469,-289,-141,468,1000,-182,-179,-1000,-53,1000,884,-361,-1000,968,-724,-1000,884,666,-706,814,1000,-729,-265,864,-1000,553,451,620,772,121,495,-522,1000,514,-739,153,-1000,2,1000,600,-908,1000,1000,1000,-1000,435,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{143,-983,1000,-785,-146,-1000,230,406,1000,-235,-266,395,-719,419,-480,227,-246,615,169,-93,1000,-373,-72,735,-276,-195,51,1000,-1000,-1000,-301,1000,1000,-434,-558,362,786,-36,-1000,-1000,-129,-1000,318,-394,135,1000,-808,-150,226,106,-914,-1000,507,-1000,644,-380,-132,55,-417,1000,1000,-1000,-121,-711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{550,-139,777,733,-113,344,-1000,815,-910,478,-132,550,-743,90,-483,-878,711,-182,941,437,-80,796,1000,998,1000,489,330,226,-614,-1000,65,-556,1000,-133,-322,-1000,164,1000,-884,-592,577,-1000,27,-914,-739,298,-1000,-388,580,549,-1000,-192,346,-410,-229,457,-870,-229,-938,-123,-370,-1000,-189,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{669,-139,439,733,-932,344,402,-708,601,337,-168,550,679,-595,-713,-404,711,-402,-137,-43,533,942,-861,-640,-833,586,948,966,778,-950,71,-824,-255,981,431,-333,-854,-411,-884,777,618,-514,670,-437,554,65,473,892,-75,727,759,-203,604,295,-162,-17,13,-248,424,849,174,105,626,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{589,145,572,-256,1000,-487,-763,-551,1000,-139,-309,-510,-22,-647,557,-342,1000,-216,24,-28,-736,-226,154,240,-65,182,889,325,-210,883,-745,-115,-1000,346,877,-257,558,-954,-264,-27,-1000,-816,-547,667,-459,1000,36,-84,190,-431,600,-1000,16,1000,-131,-405,143,-909,-166,-698,-889,1000,-622,-987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-306,733,-96,-1000,-162,1000,-212,1000,253,-412,821,445,-596,-948,-244,-2,-306,-153,1000,173,317,759,-1000,741,1000,1,-931,133,-166,1000,-1000,427,-245,-408,79,14,-637,-813,-254,-1000,-1000,516,-132,807,-498,181,925,-91,390,540,231,182,-474,-612,-121,-1000,-333,-1000,957,-719,-340,496,734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{40,-390,808,-421,1000,-498,154,-62,252,-166,-949,-188,515,887,10,318,950,-472,402,-1000,-488,453,225,919,37,-567,716,-1000,-22,1000,-1000,307,362,683,620,-1000,447,-400,782,-276,-1000,-191,-346,1000,-1000,908,232,-1000,-236,158,1000,-291,562,30,1000,-494,288,463,289,-897,717,554,-303,-590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{995,967,393,904,-269,1000,189,-412,48,425,1000,-383,125,-875,-588,282,-184,-152,400,783,70,603,-969,559,-447,834,273,-862,-863,-1000,925,230,1000,-1000,360,-272,719,732,187,-585,-1000,301,-985,1000,604,-367,712,455,-61,-232,-171,-33,32,1000,441,291,-57,463,-881,1000,498,482,586,299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-95,-553,-736,678,460,-535,1000,1000,-403,393,-130,-356,-649,-204,-1000,146,989,1000,510,133,-1000,930,-1000,1000,-461,480,-1000,979,-1000,-1000,-1000,1000,-1000,-1000,406,237,-1000,-434,-794,-441,-1000,-461,-1000,1000,1000,1000,1000,-1000,1000,-809,541,-1000,499,196,-931,1000,225,14,1000,75,-410,-1000,-557,-203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-787,-534,709,589,-803,-261,124,-291,-690,1000,-608,44,329,173,-1000,34,-196,-932,-830,-152,70,1000,750,-1000,-757,-60,717,-170,1000,1000,270,-1000,-264,223,-703,-857,1000,-1000,-328,-271,1000,-355,890,934,783,-821,632,-286,-348,-1000,-1000,1000,-805,119,236,-952,690,753,-697,1000,324,1000,-293,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{928,209,1000,108,1000,320,-247,1000,-829,276,851,914,-1000,-1000,-510,343,-477,1000,-125,-762,-679,646,-847,295,-598,-220,-414,434,-412,-640,-1000,579,-397,-129,1000,-482,-559,1000,1000,225,-1000,642,-1000,403,358,612,726,978,-956,-1000,1000,-1000,1000,-681,1000,816,25,696,535,-1000,-244,-800,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{-363,-1000,578,570,-750,177,-1000,324,53,629,1000,-246,-473,1000,-715,393,-158,-377,-947,353,-1000,1000,-318,-478,-259,-970,-155,1000,1000,-1000,-503,-811,-736,-1000,-1000,-649,-1000,-1000,-812,112,-563,-1000,-1000,-1000,-282,630,-86,954,208,397,-1000,-262,-288,816,-369,140,772,-415,1000,-801,739,1000,-956,282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{175,692,-1000,-678,-240,228,1000,-731,-1000,1000,91,-586,-1000,-893,-70,-1000,251,1000,1000,-823,171,-1000,476,1000,350,271,897,181,755,806,609,-615,364,-267,1000,1000,109,226,-1000,995,-47,-1000,210,-776,-452,403,-469,292,70,469,398,227,250,-1000,1000,151,-840,-1000,-260,210,-399,380,208,131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{-646,1000,-714,-1000,476,-890,-399,-185,1000,-236,-1000,601,69,-1000,241,1000,850,390,1000,-665,626,-1000,-895,402,-130,173,1000,-888,-980,792,484,-101,-737,1000,-352,206,566,-152,993,1000,-315,1000,704,699,260,-351,-159,-436,-409,991,-548,-400,1000,-638,797,-801,108,-929,216,58,-13,-1000,1000,-122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{-738,-1000,912,260,-542,-1000,-1000,-151,1000,57,368,809,-1000,216,-491,1000,1000,-357,-292,-249,-435,-31,-583,-1000,-623,-835,1000,-1000,-312,1000,-22,-660,-774,-6,-603,83,348,-451,1000,1000,-634,-507,-1000,1000,-575,-873,164,1000,325,365,-1000,-400,625,-638,-650,-955,1000,-468,-10,-453,858,-1000,-1000,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{125,-1000,1000,932,665,-78,-573,476,949,217,470,558,-1000,795,94,-834,1000,-891,-510,1000,-442,-294,-58,-495,-347,-313,-348,-1000,1000,269,-885,-1000,-389,-1000,996,-477,-682,-564,-201,185,687,-1000,-1000,236,-1000,373,-1000,1000,129,-474,-485,650,-1000,516,-336,418,-708,-584,966,-1000,-307,-999,168,589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,org.jsoup.nodes.Element):org.jsoup.select.Elements",
            new int[]{367,142,-988,21,269,380,-266,492,-206,-1000,279,-202,-1000,-719,141,-875,155,-790,626,-1000,-607,-559,-291,863,-305,547,4,1000,578,-1000,-16,-684,-626,-410,780,-101,242,-182,-254,431,-274,-708,889,1000,729,1000,-372,1000,1000,450,-412,550,-86,81,326,-531,-421,318,-1000,-207,1000,-590,513,545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,org.jsoup.nodes.Element):org.jsoup.select.Elements",
            new int[]{362,534,-142,228,-12,428,-508,169,284,1000,-869,-325,-389,75,30,375,-114,398,-105,-12,382,821,59,766,-213,-388,690,926,1000,-272,-202,-887,-500,-886,693,112,31,-620,-657,544,-1,-163,570,1000,-67,859,-468,257,627,713,1000,-620,263,-23,101,-768,-760,-1000,-479,-351,1000,821,-803,-548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,org.jsoup.nodes.Element):org.jsoup.select.Elements",
            new int[]{277,1000,78,798,-714,-453,-1000,53,116,1000,576,-488,1000,256,-677,1000,-999,-1000,813,-1000,341,-209,-861,593,-932,1000,683,1000,940,-591,713,-453,-415,483,-163,121,-1000,1000,1000,-914,232,-1000,1000,-332,894,1000,-196,1000,-1000,-371,312,1000,-579,-618,1000,211,-89,-354,420,-1000,133,1000,934,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,org.jsoup.nodes.Element):org.jsoup.select.Elements",
            new int[]{-1,96,-895,539,1000,-78,375,213,-560,-1000,-890,-13,-1000,-782,1000,-13,-769,-253,1000,-398,-502,-1000,689,502,-72,-3,-1000,607,-614,-1000,1000,247,-838,-81,-302,-811,-233,-70,-65,691,-108,-1000,-546,1000,422,857,-537,-45,180,1000,-1000,1000,-1000,563,903,-139,-570,1000,193,129,632,-912,-428,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.select.Selector", "", "selectFirst(java.lang.String,org.jsoup.nodes.Element):org.jsoup.nodes.Element",
            new int[]{-875,-532,-246,721,133,-79,440,-420,-114,534,-254,698,-826,155,644,-399,544,-907,239,285,762,-970,-72,46,540,262,336,896,302,-911,51,-703,374,-816,237,582,927,-408,528,-54,-764,641,-742,158,-619,-491,-958,-748,378,-579,-985,-611,917,564,-402,778,383,653,574,112,615,801,484,-677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.select.Selector", "", "selectFirst(java.lang.String,org.jsoup.nodes.Element):org.jsoup.nodes.Element",
            new int[]{-355,432,279,542,-62,-307,-19,-790,-101,208,-341,96,404,-171,286,72,-403,-129,533,1000,1000,14,281,216,34,848,1000,341,593,-1000,195,465,-694,1000,873,611,-917,33,225,1000,1000,164,802,-1000,303,-388,-1000,-1000,-244,-340,-400,-431,797,133,-1000,-375,-992,422,325,-986,13,-1000,-1000,186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Selector", "", "selectFirst(java.lang.String,org.jsoup.nodes.Element):org.jsoup.nodes.Element",
            new int[]{842,-60,902,-907,-963,-3,90,-137,522,-109,649,-1000,-583,129,96,-1000,3,-1000,208,353,-805,-318,687,545,-744,606,670,303,797,-199,-216,146,-240,224,1000,1000,-147,1000,-189,1000,-178,4,244,-484,764,-1000,457,-837,-316,-725,305,178,-1000,733,970,-649,400,619,-1000,1000,958,-51,86,-569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.select.Selector", "", "selectFirst(java.lang.String,org.jsoup.nodes.Element):org.jsoup.nodes.Element",
            new int[]{-210,-955,-1000,-465,-152,-569,312,135,-660,212,-915,-219,389,59,152,538,516,20,-1000,49,508,-699,-74,169,731,235,-65,809,-608,346,-488,-1000,878,-45,181,236,1000,-706,469,-122,-464,512,-1000,384,-1000,663,-185,375,292,704,-883,182,187,-160,-179,1000,523,1000,638,635,601,1000,857,-53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.select.Selector", "", "selectFirst(java.lang.String,org.jsoup.nodes.Element):org.jsoup.nodes.Element",
            new int[]{985,140,854,851,-513,729,-989,-615,-535,-799,141,-222,445,796,668,-281,334,-718,697,142,-387,-332,-926,93,576,-699,-839,-338,-778,-515,172,979,980,758,-33,16,417,-209,497,-665,-697,513,795,284,973,500,326,197,-217,-59,-191,89,658,548,-790,-921,-802,-312,-510,-721,-358,682,-75,-718}));
    }
}
