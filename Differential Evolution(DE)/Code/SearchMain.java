import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.jacoco.agent.rt.RT;
import org.jacoco.core.analysis.*;
import org.jacoco.core.data.*;
import org.jacoco.core.tools.ExecFileLoader;

/** One process per method: probe reset, candidate execution, coverage feedback, DE selection. */
public final class SearchMain {
    /** Fresh project classes prevent a previous candidate's static state from changing the next one. */
    static final class IsolatedLoader extends java.net.URLClassLoader {
        IsolatedLoader(Path bins) throws java.net.MalformedURLException {
            super(new java.net.URL[]{bins.toUri().toURL()}, SearchMain.class.getClassLoader());
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && !name.startsWith("java.") && !name.startsWith("javax.")
                        && !name.startsWith("sun.") && !name.startsWith("jdk.") && !name.startsWith("org.jacoco.")) {
                    try { loaded = findClass(name); } catch (ClassNotFoundException ignored) { }
                }
                if (loaded == null) loaded = super.loadClass(name, false);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
    static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String)values[i], values[i + 1]);
        return result;
    }
    static List<String> receivers(Class<?> target, Path bins) throws Exception {
        TreeSet<String> names = new TreeSet<>();
        // Commons CLI's CommandLine is intentionally created by Parser.parse();
        // it has no public constructor, but is a normal public API receiver.
        if (target.getName().equals("org.apache.commons.cli.CommandLine"))
            names.add(target.getName());
        // FastDateParser is package-private in Lang; its supported public
        // entry point is FastDateFormat, which owns and invokes the parser.
        if (target.getName().equals("org.apache.commons.lang3.time.FastDateFormat"))
            names.add(target.getName());
        if (target.getName().equals("com.fasterxml.jackson.databind.ser.BeanPropertyWriter")) {
            // DEReplay constructs the base writer through ObjectMapper's
            // serializer, even though its public constructor has >6 params.
            names.add(target.getName());
            String unwrapping = "com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter";
            try {
                Class<?> implementation = Class.forName(unwrapping, false, target.getClassLoader());
                if (target.isAssignableFrom(implementation)) names.add(unwrapping);
            } catch (Throwable ignored) { }
        }
        if (!DEReplay.constructors(target).isEmpty()) names.add(target.getName());
        if (names.isEmpty()) {
            String pkg = target.getPackage() == null ? "" : target.getPackage().getName();
            Path directory = bins.resolve(pkg.replace('.', '/'));
            if (Files.isDirectory(directory)) try (Stream<Path> files = Files.walk(directory, 2)) {
                files.filter(p -> p.toString().endsWith(".class")).sorted().forEach(p -> {
                    String name = bins.relativize(p).toString().replace(File.separatorChar, '.').replaceAll("\\.class$", "");
                    if (name.contains("$")) return;
                    try {
                        Class<?> c = Class.forName(name, false, target.getClassLoader());
                        if (target.isAssignableFrom(c) && !DEReplay.constructors(c).isEmpty()) names.add(name);
                    } catch (Throwable ignored) { }
                });
            }
        }
        return new ArrayList<>(names).subList(0, Math.min(4, names.size()));
    }
    static void discover(String name, Path bins) throws Exception {
        if (!Files.isDirectory(bins)) throw new FileNotFoundException("Compiled classes directory not found: " + bins);
        Path targetFile = bins.resolve(name.replace('.', '/') + ".class");
        if (!Files.isRegularFile(targetFile)) throw new FileNotFoundException("Target class file not found: " + targetFile);
        Class<?> type = Class.forName(name, false, SearchMain.class.getClassLoader());
        List<String> choices = receivers(type, bins);
        List<Map<String, Object>> result = new ArrayList<>();
        List<Method> methods = new ArrayList<>(Arrays.asList(type.getDeclaredMethods()));
        // A property writer's serialization methods exercise its main behavior.
        // Search those before simple accessors when the job has a time limit.
        methods.sort(Comparator.<Method>comparingInt(m ->
                name.equals("com.fasterxml.jackson.databind.ser.BeanPropertyWriter")
                    && m.getName().startsWith("serializeAs") ? 0 : 1)
            .thenComparing(DEReplay::signature));
        for (Method m : methods) {
            String skip = null;
            if (!Modifier.isPublic(m.getModifiers())) skip = "not_public";
            else if (m.isSynthetic() || m.isBridge()) skip = "compiler_generated";
            else if (Modifier.isNative(m.getModifiers())) skip = "native_method";
            else if (m.getName().equals("hashCode")) skip = "identity_risk";
            else if (m.getParameterCount() > 6) skip = "more_than_6_parameters";
            else if (!Modifier.isStatic(m.getModifiers()) && choices.isEmpty()) skip = "no_concrete_receiver";
            result.add(map("class", name, "method", DEReplay.signature(m),
                "receivers", String.join(",", choices), "skip_reason", skip,
                "constant_call", Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0));
        }
        System.out.println("DE_JSON:" + MiniJson.encode(result));
    }
    static String asmSignature(String methodName, String descriptor) {
        Type[] parameters = Type.getArgumentTypes(descriptor);
        StringJoiner joined = new StringJoiner(",");
        for (Type parameter : parameters) joined.add(parameter.getClassName());
        return methodName + "(" + joined + "):" + Type.getReturnType(descriptor).getClassName();
    }
    static void discoverCallers(String name, Path bins) throws Exception {
        if (!Files.isDirectory(bins)) throw new FileNotFoundException("Compiled classes directory not found: " + bins);
        Class<?> target = Class.forName(name, false, SearchMain.class.getClassLoader());
        String targetOwner = target.getName().replace('.', '/');
        Set<String> targetOwners = new HashSet<>();
        targetOwners.add(targetOwner);
        addInterfaceOwners(target, targetOwners);
        Set<String> hidden = new HashSet<>();
        boolean targetClassHidden = !Modifier.isPublic(target.getModifiers());
        boolean targetHasReceiver = !receivers(target, bins).isEmpty();
        for (Method method : target.getDeclaredMethods())
            if ((targetClassHidden || !Modifier.isPublic(method.getModifiers())
                    || (!Modifier.isStatic(method.getModifiers()) && !targetHasReceiver))
                    && !method.isSynthetic() && !method.isBridge())
                hidden.add(method.getName() + Type.getMethodDescriptor(method));

        // Build a small reverse call graph so a public entry point can reach
        // package-private implementation methods several calls below it.
        final class Node {
            String owner, method, descriptor;
            int access;
            Set<String> calls = new HashSet<>();
            String key() { return owner + "." + method + descriptor; }
        }
        Map<String, Node> nodes = new HashMap<>();
        try (Stream<Path> files = Files.walk(bins)) {
            for (Path file : (Iterable<Path>)files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String relative = bins.relativize(file).toString().replace(File.separatorChar, '/');
                if (relative.equals("module-info.class")) continue;
                byte[] bytes = Files.readAllBytes(file);
                ClassReader reader = new ClassReader(bytes);
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    String className;
                    @Override public void visit(int version, int access, String internalName,
                                                String signature, String superName, String[] interfaces) {
                        className = internalName;
                    }
                    @Override public MethodVisitor visitMethod(int access, String methodName,
                                                              String descriptor, String signature,
                                                              String[] exceptions) {
                        if ((access & Opcodes.ACC_ABSTRACT) != 0 || methodName.startsWith("<")
                                || Type.getArgumentTypes(descriptor).length > 6) return null;
                        Node node = new Node();
                        node.owner = className; node.method = methodName;
                        node.descriptor = descriptor; node.access = access;
                        nodes.put(node.key(), node);
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                                 String calledDescriptor, boolean isInterface) {
                                node.calls.add(owner + "." + called + calledDescriptor);
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        Set<String> frontier = new HashSet<>();
        for (Node node : nodes.values()) {
            for (String call : node.calls) {
                int split = call.lastIndexOf('.');
                if (split > 0 && targetOwners.contains(call.substring(0, split))
                        && hidden.contains(call.substring(split + 1))) frontier.add(node.key());
            }
        }
        Set<String> reachable = new HashSet<>(frontier);
        Map<String, Set<String>> paths = new HashMap<>();
        for (String key : frontier) paths.put(key, new TreeSet<>(Collections.singleton(key)));
        boolean changed;
        do {
            changed = false;
            for (Node node : nodes.values()) {
                if (reachable.contains(node.key())) continue;
                for (String call : node.calls) if (reachable.contains(call)) {
                    reachable.add(node.key());
                    paths.put(node.key(), new TreeSet<>(paths.get(call)));
                    changed = true;
                    break;
                }
            }
        } while (changed);
        Map<String, Map<String, Object>> candidates = new TreeMap<>();
        for (String key : reachable) {
            Node node = nodes.get(key);
            if (node == null || (node.access & Opcodes.ACC_PUBLIC) == 0) continue;
            String className = node.owner.replace('/', '.');
            try {
                Class<?> caller = Class.forName(className, false, SearchMain.class.getClassLoader());
                String expected = asmSignature(node.method, node.descriptor);
                Method callable = null;
                for (Method m : caller.getDeclaredMethods())
                    if (DEReplay.signature(m).equals(expected)) { callable = m; break; }
                if (callable == null || !Modifier.isPublic(callable.getModifiers())
                        || callable.isSynthetic() || callable.isBridge()) continue;
                List<String> choices = Modifier.isStatic(node.access)
                    ? Collections.emptyList() : receivers(caller, bins);
                if (!Modifier.isStatic(node.access) && choices.isEmpty()) continue;
                candidates.putIfAbsent(className + "\n" + expected,
                    map("class", className, "method", expected, "receivers", String.join(",", choices),
                        "skip_reason", null,
                        "constant_call", Modifier.isStatic(node.access) && Type.getArgumentTypes(node.descriptor).length == 0,
                        "indirect_via", new ArrayList<>(paths.getOrDefault(key, Collections.emptySet()))));
            } catch (Throwable ignored) { }
        }
        List<Map<String, Object>> result = new ArrayList<>(candidates.values());
        result.sort(Comparator.<Map<String, Object>>comparingInt(m -> ((List<?>)m.get("indirect_via")).size())
            .reversed().thenComparing(m -> m.get("class") + "#" + m.get("method")));
        // RemoveUnusedVars is installed dynamically by Closure's pass config,
        // so bytecode call-graph roots such as Compiler.optimize() can run
        // without the pass when CompilerOptions are not initialized. Always
        // include the public end-to-end compile API as a candidate: DEReplay
        // supplies source files and enables the relevant optimization options.
        if (name.equals("com.google.javascript.jscomp.RemoveUnusedVars")
                || name.equals("com.google.javascript.jscomp.FlowSensitiveInlineVariables")) {
            try {
                Class<?> compiler = Class.forName("com.google.javascript.jscomp.Compiler", false,
                    SearchMain.class.getClassLoader());
                List<Map<String, Object>> compileEntrypoints = new ArrayList<>();
                for (Method m : compiler.getMethods()) {
                    if (!m.getName().equals("compile") || m.getParameterCount() > 6) continue;
                    boolean sourceInput = false;
                    for (Class<?> p : m.getParameterTypes())
                        if (List.class.isAssignableFrom(p)
                                || (p.isArray() && p.getComponentType().getName().equals(
                                    "com.google.javascript.jscomp.SourceFile"))) sourceInput = true;
                    if (!sourceInput) continue;
                    List<String> choices = receivers(compiler, bins);
                    if (choices.isEmpty()) continue;
                    Map<String, Object> candidate = map("class", compiler.getName(),
                        "method", DEReplay.signature(m), "receivers", String.join(",", choices),
                        "skip_reason", null, "constant_call", false,
                        "indirect_via", Collections.singletonList("forced-Closure-compile-entry"));
                    compileEntrypoints.add(candidate);
                }
                compileEntrypoints.sort(Comparator.comparing(m -> (String)m.get("method")));
                compileEntrypoints.addAll(result);
                result = compileEntrypoints;
            } catch (Throwable ignored) { }
        }
        if (result.size() > 32) result = new ArrayList<>(result.subList(0, 32));
        System.out.println("DE_JSON:" + MiniJson.encode(result));
    }
    static void addInterfaceOwners(Class<?> type, Set<String> owners) {
        for (Class<?> itf : type.getInterfaces()) {
            if (owners.add(itf.getName().replace('.', '/'))) addInterfaceOwners(itf, owners);
        }
        Class<?> parent = type.getSuperclass();
        if (parent != null && parent != Object.class) addInterfaceOwners(parent, owners);
    }
    static final class Coverage {
        final List<byte[]> binaries = new ArrayList<>();
        Coverage(Path bin, String classes) throws IOException {
            Set<Path> seen = new HashSet<>();
            for (String name : classes.split(",")) {
                Path main = bin.resolve(name.replace('.', '/') + ".class");
                if (!Files.isRegularFile(main)) throw new FileNotFoundException(main.toString());
                seen.add(main);
                String prefix = main.getFileName().toString().replace(".class", "$");
                try (Stream<Path> files = Files.list(main.getParent())) {
                    files.filter(p -> p.getFileName().toString().startsWith(prefix)
                        && p.toString().endsWith(".class")).forEach(seen::add);
                }
            }
            for (Path p : seen) binaries.add(Files.readAllBytes(p));
        }
        Map<String, Object> read() throws IOException {
            ExecFileLoader loader = new ExecFileLoader();
            loader.load(new ByteArrayInputStream(RT.getAgent().getExecutionData(true)));
            CoverageBuilder builder = new CoverageBuilder();
            Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), builder);
            for (byte[] bytes : binaries) analyzer.analyzeClass(bytes, "target");
            int lines = 0, hitLines = 0, branches = 0, hitBranches = 0;
            Set<String> features = new TreeSet<>();
            for (IClassCoverage c : builder.getClasses()) {
                lines += c.getLineCounter().getTotalCount(); hitLines += c.getLineCounter().getCoveredCount();
                branches += c.getBranchCounter().getTotalCount(); hitBranches += c.getBranchCounter().getCoveredCount();
            }
            // Actual probe IDs are used for diversity; they are not reported as branch IDs.
            for (ExecutionData data : loader.getExecutionDataStore().getContents()) {
                boolean[] probes = data.getProbes();
                for (int i = 0; i < probes.length; i++) if (probes[i]) features.add(data.getName() + ":p" + i);
            }
            return map("lines_total", lines, "lines_covered", hitLines,
                "branches_total", branches, "branches_covered", hitBranches, "features", features);
        }
    }
    private static Map<String, Object> evaluateOne(String target, String receivers,
            String signature, Path bin, Coverage coverage, int[] genes) throws Exception {
        DEReplay.Observation observation;
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (IsolatedLoader loader = new IsolatedLoader(bin)) {
            Thread.currentThread().setContextClassLoader(loader);
            RT.getAgent().reset();
            observation = DEReplay.execute(target, receivers, signature, genes);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
        Map<String, Object> cov = coverage.read();
        double lr = ((Number)cov.get("lines_total")).intValue() == 0 ? 0
            : ((Number)cov.get("lines_covered")).doubleValue() / ((Number)cov.get("lines_total")).doubleValue();
        double br = ((Number)cov.get("branches_total")).intValue() == 0 ? lr
            : ((Number)cov.get("branches_covered")).doubleValue() / ((Number)cov.get("branches_total")).doubleValue();
        boolean coveredChangedCode = ((Number)cov.get("lines_covered")).intValue() > 0;
        double score = observation.reachedTarget && coveredChangedCode
            && !observation.token.equals("HARNESS_ERROR") ? 0.7 * br + 0.3 * lr : -1;
        return map("genes", genes, "fitness", score, "coverage", cov,
            "reached_target", observation.reachedTarget, "token", observation.token,
            "generated_source", observation.generatedSource,
            "cli_options", observation.cliOptions,
            "setup_calls", observation.setupCalls, "setup_failures", observation.setupFailures,
            "null_fallbacks", observation.nullFallbacks, "error", observation.error);
    }
    private static boolean closureCliEntry(String target, String signature) {
        return (target.equals("com.google.javascript.jscomp.CommandLineRunner")
                && signature.startsWith("main(java.lang.String[]):"))
            || (target.equals("com.google.javascript.jscomp.AbstractCompilerRunner")
                && signature.equals("run():void"));
    }
    private static Map<String, Object> invalidCliCandidate(int[] genes, String error) {
        return map("genes", genes, "fitness", -1.0,
            "coverage", map("lines_total", 0, "lines_covered", 0,
                "branches_total", 0, "branches_covered", 0, "features", Collections.emptyList()),
            "reached_target", false, "token", "HARNESS_ERROR", "error", error);
    }
    private static final class ForkResult {
        final double score; final String json;
        ForkResult(double score, String json) { this.score = score; this.json = json; }
    }
    private static ForkResult evaluateFork(String target, String receivers, String signature,
            Path bin, String classes, int[] genes, Path folder, int number) throws Exception {
        String agent = System.getProperty("de.agent.path");
        if (agent == null) throw new IllegalStateException("Missing de.agent.path");
        String includes = String.join(":", Arrays.stream(classes.split(","))
            .map(name -> name + "*").toArray(String[]::new));
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx" + System.getProperty("de.heap", "512m"));
        command.add("-Djava.awt.headless=true");
        command.add("-Dde.modified.classes=" + classes);
        command.add("-javaagent:" + agent + "=output=none,includes=" + includes);
        command.add("-cp"); command.add(System.getProperty("java.class.path"));
        command.add("SearchMain"); command.add("evaluate");
        command.add(target); command.add(receivers); command.add(signature);
        command.add(bin.toString()); command.add(classes);
        command.add(Arrays.toString(genes).replace("[", "").replace("]", "").replace(" ", ""));
        Path log = folder.resolve(String.format("cli-eval-%04d.log", number));
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
        int seconds = Integer.getInteger("de.candidate.timeout.seconds", 12);
        if (!process.waitFor(seconds, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly(); process.waitFor();
            Map<String, Object> row = invalidCliCandidate(genes,
                "CLI candidate exceeded " + seconds + " seconds; log=" + log);
            return new ForkResult(-1, MiniJson.encode(row));
        }
        String scoreLine = null, rowLine = null;
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            if (line.startsWith("DE_SCORE:")) scoreLine = line.substring(9);
            if (line.startsWith("DE_ROW:")) rowLine = line.substring(7);
        }
        if (process.exitValue() == 0 && scoreLine != null && rowLine != null) {
            Files.deleteIfExists(log);
            return new ForkResult(Double.parseDouble(scoreLine), rowLine);
        }
        Map<String, Object> row = invalidCliCandidate(genes,
            "CLI child exited " + process.exitValue() + "; log=" + log);
        return new ForkResult(-1, MiniJson.encode(row));
    }
    public static void main(String[] args) throws Exception {
        if (args[0].equals("discover")) { discover(args[1], Paths.get(args[2])); return; }
        if (args[0].equals("discover-callers")) { discoverCallers(args[1], Paths.get(args[2])); return; }
        if (args[0].equals("evaluate")) {
            String target = args[1], receivers = args[2], signature = args[3];
            Path bin = Paths.get(args[4]);
            System.setProperty("de.modified.classes", args[5]);
            int[] genes = Arrays.stream(args[6].split(",")).mapToInt(Integer::parseInt).toArray();
            Map<String, Object> row = evaluateOne(target, receivers, signature, bin,
                new Coverage(bin, args[5]), genes);
            System.out.println("DE_SCORE:" + row.get("fitness"));
            System.out.println("DE_ROW:" + MiniJson.encode(row));
            return;
        }
        String target = args[1], receivers = args[2], signature = args[3];
        Path bin = Paths.get(args[4]), candidates = Paths.get(args[6]);
        int population = Integer.parseInt(args[7]), budget = Integer.parseInt(args[8]);
        double f = Double.parseDouble(args[9]), cr = Double.parseDouble(args[10]);
        int bound = Integer.parseInt(args[11]); long seed = Long.parseLong(args[12]);
        // Let the input factory choose a Closure-specific grammar for this bug.
        System.setProperty("de.modified.classes", args[5]);
        // Inspect once here. Candidate executions each receive fresh project classes below.
        Method targetMethod = DEReplay.method(target, signature);
        Coverage coverage = new Coverage(bin, args[5]);
        Files.createDirectories(candidates.getParent());
        final int[] count = {0};
        boolean forkCli = closureCliEntry(target, signature);
        try (BufferedWriter writer = Files.newBufferedWriter(candidates, StandardCharsets.UTF_8)) {
            java.util.function.ToDoubleFunction<int[]> objective = genes -> {
                try {
                    double score; String row;
                    if (forkCli) {
                        ForkResult result = evaluateFork(target, receivers, signature, bin,
                            args[5], genes, candidates.getParent(), count[0] + 1);
                        score = result.score; row = result.json;
                    } else {
                        Map<String, Object> result = evaluateOne(target, receivers, signature,
                            bin, coverage, genes);
                        score = ((Number)result.get("fitness")).doubleValue();
                        row = MiniJson.encode(result);
                    }
                    writer.write(row); writer.newLine(); writer.flush();
                    if (++count[0] % 32 == 0) System.out.println("Evaluated " + count[0] + " candidates; fitness " + score);
                    return score;
                } catch (Exception e) { throw new RuntimeException(e); }
            };
            Map<String, Object> stats;
            if (Modifier.isStatic(targetMethod.getModifiers()) && targetMethod.getParameterCount() == 0) {
                double score = objective.applyAsDouble(new int[DEReplay.DIMENSIONS]);
                stats = map("evaluations", 1, "trial_evaluations", 0, "de_applied", false, "best_fitness", score);
            } else {
                stats = DifferentialEvolution.search(DEReplay.DIMENSIONS, population, budget, f, cr, bound, seed, objective).json();
            }
            Files.writeString(Paths.get(candidates + ".stats.json"), MiniJson.encode(stats), StandardCharsets.UTF_8);
        }
    }
}
