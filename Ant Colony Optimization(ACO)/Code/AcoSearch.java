import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.IExecutionDataVisitor;
import org.jacoco.core.data.ISessionInfoVisitor;
import org.jacoco.core.data.SessionInfo;

/**
 * Ant Colony Optimization test generator.
 *
 * Each ant builds one test case by walking a decision graph:
 *   which method of the class under test -> how to build the receiver ->
 *   which value for every parameter (recursively for object parameters).
 * Every edge carries a pheromone value. An ant picks an edge with probability
 * proportional to tau^alpha * eta^beta. After all ants of an iteration have run,
 * pheromone evaporates and the ants whose test reached new code (JaCoCo probes of
 * the classes under test) or showed a new behaviour deposit pheromone on their path.
 * Tests that add coverage or a new outcome are kept in the archive.
 *
 *   java -javaagent:jacocoagent.jar=output=none,includes=... AcoSearch <config.properties>
 */
public final class AcoSearch {

    // parameters (see Configuration/aco_config.properties)
    static int ants = 20;
    static int iterations = 30;
    static double alpha = 1.0;
    static double beta = 2.0;
    static double rho = 0.25;
    static double tau0 = 1.0;
    static double tauMin = 0.05;
    static double tauMax = 10.0;
    static long seed = 1L;
    static int timeBudgetSec = 40;
    static int callTimeoutMs = 1500;
    static int maxTests = 150;
    static int maxDepth = 2;
    static int extraPerAction = 5;
    static int evalsPerAction = 12;

    static Random rnd;
    static File binDir;
    static final Mined mined = new Mined();
    static final Map<String, Slot> slots = new HashMap<String, Slot>();
    static final Map<String, List<Class<?>>> packageCache = new HashMap<String, List<Class<?>>>();
    static volatile boolean guarding;

    static final String[] PREFABS = {
        "listS", "list0", "listI", "listN", "linked", "set0", "setS", "tree", "map0", "mapS", "treemap", "props",
        "iter", "sb", "sbuf", "reader", "reader0", "writer", "in", "in0", "out", "bigint", "bigdec", "localeUS",
        "localeDE", "utc", "tzParis", "date", "calendar", "random", "comparator", "charset", "exception", "object"};

    /** One outgoing edge of a decision point. */
    static final class Option {
        String literal;          // complete value spec, or
        Constructor<?> ctor;     // constructor to call, or
        Method method;           // method to call (static factory or the method under test), or
        Class<?> component;      // array of this type with arrayLen elements
        int arrayLen;
        boolean edge;            // boundary-like value, gets a higher heuristic weight
        String label;
    }

    /** A decision point with its pheromone trail. */
    static final class Slot {
        final String key;
        final List<Option> options = new ArrayList<Option>();
        double[] tau;
        double[] eta;

        Slot(String key) {
            this.key = key;
        }

        void seal() {
            tau = new double[options.size()];
            eta = new double[options.size()];
            Arrays.fill(tau, tau0);
            for (int i = 0; i < eta.length; i++) {
                eta[i] = options.get(i).edge ? 1.5 : 1.0;
            }
        }
    }

    static final class Step {
        final Slot slot;
        final int index;

        Step(Slot slot, int index) {
            this.slot = slot;
            this.index = index;
        }
    }

    /** Constants found in the class files of the classes under test. */
    static final class Mined {
        final Set<Integer> ints = new LinkedHashSet<Integer>();
        final Set<Long> longs = new LinkedHashSet<Long>();
        final Set<Float> floats = new LinkedHashSet<Float>();
        final Set<Double> doubles = new LinkedHashSet<Double>();
        final Set<String> strings = new LinkedHashSet<String>();
    }

    public static void main(String[] args) throws Exception {
        Properties cfg = new Properties();
        InputStream in = new FileInputStream(args[0]);
        cfg.load(in);
        in.close();
        ants = Integer.parseInt(cfg.getProperty("number_of_ants", "20"));
        iterations = Integer.parseInt(cfg.getProperty("iterations", "30"));
        alpha = Double.parseDouble(cfg.getProperty("alpha", "1.0"));
        beta = Double.parseDouble(cfg.getProperty("beta", "2.0"));
        rho = Double.parseDouble(cfg.getProperty("evaporation_rate", "0.25"));
        tau0 = Double.parseDouble(cfg.getProperty("initial_pheromone", "1.0"));
        seed = Long.parseLong(cfg.getProperty("random_seed", "1"));
        timeBudgetSec = Integer.parseInt(cfg.getProperty("time_budget_seconds", "40"));
        callTimeoutMs = Integer.parseInt(cfg.getProperty("call_timeout_ms", "1500"));
        maxTests = Integer.parseInt(cfg.getProperty("max_tests", "150"));
        maxDepth = Integer.parseInt(cfg.getProperty("max_object_depth", "2"));
        extraPerAction = Integer.parseInt(cfg.getProperty("extra_tests_per_method", "5"));
        evalsPerAction = Integer.parseInt(cfg.getProperty("evaluations_per_method", "12"));
        String[] classNames = cfg.getProperty("classes").split("[,;\\s]+");
        binDir = new File(cfg.getProperty("bin_dir", "."));
        File outFile = new File(cfg.getProperty("output"));
        File statsFile = new File(cfg.getProperty("stats"));
        rnd = new Random(seed);

        long started = System.currentTimeMillis();
        List<Class<?>> targets = new ArrayList<Class<?>>();
        List<String> skipped = new ArrayList<String>();
        for (String name : classNames) {
            if (name.length() == 0) {
                continue;
            }
            try {
                Class<?> c = Class.forName(name, false, AcoSearch.class.getClassLoader());
                targets.add(c);
                mine(c);
            } catch (Throwable e) {
                skipped.add(name + " (" + e.getClass().getSimpleName() + ")");
            }
        }

        Slot root = new Slot("ROOT");
        for (Class<?> c : targets) {
            addActions(root, c);
        }
        root.seal();
        int actions = root.options.size();

        List<String[]> archive = new ArrayList<String[]>();
        List<String> history = new ArrayList<String>();
        Set<Long> covered = new HashSet<Long>();
        Map<String, Integer> classIds = new HashMap<String, Integer>();
        int evaluations = 0, timeouts = 0, blocked = 0, iterationsDone = 0;

        if (actions > 0) {
            System.setSecurityManager(new Guard());
            int[] tries = new int[actions];
            int[] stale = new int[actions];
            int[] slow = new int[actions];
            int[] kept = new int[actions];
            List<Set<String>> outcomes = new ArrayList<Set<String>>();
            for (int i = 0; i < actions; i++) {
                outcomes.add(new HashSet<String>());
            }
            int rounds = Math.max(iterations, (int) Math.ceil(actions * (double) evalsPerAction / ants));
            long deadline = started + timeBudgetSec * 1000L;
            dumpProbes(classIds);

            for (int it = 1; it <= rounds && System.currentTimeMillis() < deadline; it++) {
                for (int a = 0; a < actions; a++) {
                    root.eta[a] = slow[a] >= 3 ? 0.0 : 1.0 / (1.0 + stale[a] / 8.0) + (tries[a] == 0 ? 1.0 : 0.0);
                }
                List<List<Step>> trails = new ArrayList<List<Step>>();
                List<Double> rewards = new ArrayList<Double>();
                int bestGain = 1;
                List<Integer> gains = new ArrayList<Integer>();
                List<Boolean> novel = new ArrayList<Boolean>();

                for (int ant = 0; ant < ants && System.currentTimeMillis() < deadline; ant++) {
                    List<Step> trail = new ArrayList<Step>();
                    String spec;
                    try {
                        spec = build(root, trail, 0);
                    } catch (Throwable e) {
                        continue;
                    }
                    int action = trail.get(0).index;
                    tries[action]++;
                    evaluations++;

                    guarding = true;
                    String outcome = AcoOracle.timed(spec, callTimeoutMs);
                    guarding = false;
                    Set<Long> hit = dumpProbes(classIds);

                    int gain = 0;
                    boolean isNovel = false;
                    if (outcome.equals(AcoOracle.TIMEOUT)) {
                        timeouts++;
                        slow[action]++;
                    } else if (outcome.contains("AcoSearch$Blocked") || outcome.contains("OutOfMemoryError")) {
                        blocked++;
                        slow[action]++;
                    } else {
                        for (Long probe : hit) {
                            if (covered.add(probe)) {
                                gain++;
                            }
                        }
                        isNovel = outcomes.get(action).add(outcome);
                        boolean keep = gain > 0 && archive.size() < maxTests * 2
                                || isNovel && kept[action] < extraPerAction && archive.size() < maxTests;
                        if (keep) {
                            kept[action]++;
                            archive.add(new String[] {root.options.get(action).label, spec, outcome, String.valueOf(gain)});
                        }
                    }
                    stale[action] = gain > 0 ? 0 : stale[action] + 1;
                    bestGain = Math.max(bestGain, gain);
                    trails.add(trail);
                    gains.add(gain);
                    novel.add(isNovel);
                }

                // evaporation, then deposit along the paths of the successful ants
                for (Slot s : slots.values()) {
                    evaporate(s);
                }
                evaporate(root);
                for (int k = 0; k < trails.size(); k++) {
                    double reward = gains.get(k) > 0 ? 1.0 + 2.0 * gains.get(k) / bestGain : novel.get(k) ? 0.3 : 0.0;
                    if (reward > 0) {
                        for (Step step : trails.get(k)) {
                            step.slot.tau[step.index] = Math.min(tauMax, step.slot.tau[step.index] + reward);
                        }
                    }
                }
                iterationsDone = it;
                history.add(it + "," + covered.size() + "," + archive.size() + "," + evaluations);
            }
        }

        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"));
        for (String[] row : archive) {
            out.println(row[0] + "\t" + row[1] + "\t" + row[2] + "\t" + row[3]);
        }
        out.close();

        PrintWriter st = new PrintWriter(new OutputStreamWriter(new FileOutputStream(statsFile), "UTF-8"));
        st.println("{");
        st.println("  \"target_classes\": " + targets.size() + ",");
        st.println("  \"skipped_classes\": " + quoteList(skipped) + ",");
        st.println("  \"methods\": " + actions + ",");
        st.println("  \"iterations_done\": " + iterationsDone + ",");
        st.println("  \"evaluations\": " + evaluations + ",");
        st.println("  \"probes_covered\": " + covered.size() + ",");
        st.println("  \"tests_archived\": " + archive.size() + ",");
        st.println("  \"timeouts\": " + timeouts + ",");
        st.println("  \"blocked\": " + blocked + ",");
        st.println("  \"decision_points\": " + (slots.size() + 1) + ",");
        st.println("  \"coverage_feedback\": " + (agent() != null) + ",");
        st.println("  \"search_seconds\": " + (System.currentTimeMillis() - started) / 1000.0 + ",");
        st.println("  \"history\": " + quoteList(history));
        st.println("}");
        st.close();
        Runtime.getRuntime().halt(0);
    }

    static void evaporate(Slot s) {
        for (int i = 0; i < s.tau.length; i++) {
            s.tau[i] = Math.max(tauMin, s.tau[i] * (1.0 - rho));
        }
    }

    static String quoteList(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append(i == 0 ? "" : ", ").append('"').append(items.get(i).replace("\\", "\\\\").replace("\"", "'")).append('"');
        }
        return sb.append("]").toString();
    }

    // building the decision graph

    static void addActions(Slot root, Class<?> c) {
        if (c.isInterface() || c.isAnnotation() || c.isAnonymousClass() || c.isSynthetic()) {
            return;
        }
        try {
            if (!Modifier.isAbstract(c.getModifiers()) && !c.isEnum()) {
                for (Constructor<?> k : c.getDeclaredConstructors()) {
                    if (!Modifier.isPrivate(k.getModifiers()) && usable(k.getParameterTypes())) {
                        Option o = new Option();
                        o.ctor = k;
                        o.label = c.getName() + ".<init>(" + names(k.getParameterTypes(), ",") + ")";
                        root.options.add(o);
                    }
                }
            }
            for (Method m : c.getDeclaredMethods()) {
                int mod = m.getModifiers();
                if (Modifier.isPrivate(mod) || m.isSynthetic() || m.isBridge() || !usable(m.getParameterTypes())) {
                    continue;
                }
                String n = m.getName();
                if (n.equals("main") || n.equals("wait") || n.equals("notify") || n.equals("notifyAll")
                        || n.equals("finalize") || n.contains("exit") || n.contains("shutdown")) {
                    continue;
                }
                if (!Modifier.isStatic(mod) && receiverSlot(c).options.isEmpty()) {
                    continue;
                }
                Option o = new Option();
                o.method = m;
                o.label = c.getName() + "." + n + "(" + names(m.getParameterTypes(), ",") + ")";
                root.options.add(o);
            }
        } catch (Throwable ignored) {
            // class could not be inspected (missing dependency); leave it out
        }
    }

    static boolean usable(Class<?>[] params) {
        return params.length <= 8;
    }

    static String names(Class<?>[] types, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            sb.append(i == 0 ? "" : sep).append(types[i].getName());
        }
        return sb.toString();
    }

    /** Ways to obtain an instance to call instance methods on (shared by all methods of the class). */
    static Slot receiverSlot(Class<?> c) {
        String key = "recv:" + c.getName();
        Slot s = slots.get(key);
        if (s == null) {
            s = new Slot(key);
            slots.put(key, s);
            constructive(s, c, 0);
            if (c.isEnum()) {
                Object[] constants = c.getEnumConstants();
                for (int i = 0; constants != null && i < constants.length && i < 8; i++) {
                    s.options.add(literal("e(" + c.getName() + "," + ((Enum<?>) constants[i]).name() + ")", false));
                }
            }
            s.seal();
        }
        return s;
    }

    static Slot valueSlot(String key, Class<?> type, int depth) {
        Slot s = slots.get(key);
        if (s == null) {
            s = new Slot(key);
            slots.put(key, s);
            fill(s, type, depth);
            if (s.options.isEmpty()) {
                s.options.add(literal("_", true));
            }
            s.seal();
        }
        return s;
    }

    static Option literal(String spec, boolean edge) {
        Option o = new Option();
        o.literal = spec;
        o.edge = edge;
        return o;
    }

    static String text(String s) {
        StringBuilder sb = new StringBuilder("t:");
        for (int i = 0; i < s.length(); i++) {
            String h = Integer.toHexString(s.charAt(i));
            sb.append("0000".substring(h.length())).append(h);
        }
        return sb.toString();
    }

    static void fill(Slot s, Class<?> t, int depth) {
        List<Option> o = s.options;
        if (t == boolean.class || t == Boolean.class) {
            o.add(literal("z:0", false));
            o.add(literal("z:1", false));
        } else if (t == int.class || t == Integer.class) {
            int[] base = {0, 1, -1, 2, 3, 5, 10, 16, 100, 255, -100, 1000, Integer.MAX_VALUE, Integer.MIN_VALUE};
            Set<Integer> all = new LinkedHashSet<Integer>();
            for (int v : base) {
                all.add(v);
            }
            for (Integer v : mined.ints) {
                all.add(v);
                all.add(v - 1);
                all.add(v + 1);
            }
            for (int i = 0; i < 4; i++) {
                all.add(rnd.nextInt(2001) - 1000);
            }
            int n = 0;
            for (Integer v : all) {
                if (n++ < 40) {
                    o.add(literal("i:" + v, v == 0 || v == -1 || v == Integer.MAX_VALUE || v == Integer.MIN_VALUE));
                }
            }
        } else if (t == long.class || t == Long.class) {
            long[] base = {0L, 1L, -1L, 2L, 10L, 1000L, Integer.MAX_VALUE + 1L, Long.MAX_VALUE, Long.MIN_VALUE};
            Set<Long> all = new LinkedHashSet<Long>();
            for (long v : base) {
                all.add(v);
            }
            for (Long v : mined.longs) {
                all.add(v);
            }
            for (Integer v : mined.ints) {
                all.add(v.longValue());
            }
            int n = 0;
            for (Long v : all) {
                if (n++ < 30) {
                    o.add(literal("j:" + v, v == 0 || v == Long.MAX_VALUE || v == Long.MIN_VALUE));
                }
            }
        } else if (t == short.class || t == Short.class) {
            for (int v : new int[] {0, 1, -1, 10, 255, Short.MAX_VALUE, Short.MIN_VALUE}) {
                o.add(literal("s:" + v, v == 0 || Math.abs(v) > 1000));
            }
        } else if (t == byte.class || t == Byte.class) {
            for (int v : new int[] {0, 1, -1, 10, 65, Byte.MAX_VALUE, Byte.MIN_VALUE}) {
                o.add(literal("b:" + v, v == 0 || Math.abs(v) > 100));
            }
        } else if (t == char.class || t == Character.class) {
            for (char v : "aAzZ09 ._-,;:/\\\"'\n\t+=<>()[]{}%#\u0000é￿".toCharArray()) {
                o.add(literal("c:" + (int) v, v == 0 || v == '￿' || v == ' '));
            }
        } else if (t == double.class || t == Double.class) {
            double[] base = {0.0, 1.0, -1.0, 0.5, -0.5, 2.0, 10.0, 100.0, 1e-10, 1e10, -0.0, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE, Double.MIN_VALUE};
            Set<Double> all = new LinkedHashSet<Double>();
            for (double v : base) {
                all.add(v);
            }
            all.addAll(mined.doubles);
            for (Integer v : mined.ints) {
                all.add(v.doubleValue());
            }
            int n = 0;
            for (Double v : all) {
                if (n++ < 35) {
                    o.add(literal("d:" + Double.doubleToRawLongBits(v), v == 0 || v.isNaN() || v.isInfinite()));
                }
            }
        } else if (t == float.class || t == Float.class) {
            float[] base = {0f, 1f, -1f, 0.5f, 2f, 100f, Float.NaN, Float.POSITIVE_INFINITY, Float.MAX_VALUE, Float.MIN_VALUE};
            Set<Float> all = new LinkedHashSet<Float>();
            for (float v : base) {
                all.add(v);
            }
            all.addAll(mined.floats);
            int n = 0;
            for (Float v : all) {
                if (n++ < 25) {
                    o.add(literal("f:" + Float.floatToRawIntBits(v), v == 0 || v.isNaN() || v.isInfinite()));
                }
            }
        } else if (t == String.class || t == CharSequence.class || t == Comparable.class) {
            o.add(literal("_", true));
            strings(o);
            if (t == CharSequence.class) {
                o.add(literal("p(sb)", false));
            }
        } else if (t.isEnum()) {
            o.add(literal("_", true));
            Object[] constants = t.getEnumConstants();
            for (int i = 0; constants != null && i < constants.length && i < 12; i++) {
                o.add(literal("e(" + t.getName() + "," + ((Enum<?>) constants[i]).name() + ")", false));
            }
        } else if (t == Class.class) {
            o.add(literal("_", true));
            for (String n : new String[] {"java.lang.String", "java.lang.Integer", "java.lang.Object", "int",
                "java.util.List", "java.lang.Number", "[I", "java.util.Map", "java.lang.Double"}) {
                o.add(literal("k(" + n + ")", false));
            }
        } else if (t.isArray()) {
            Class<?> comp = t.getComponentType();
            o.add(literal("_", true));
            o.add(literal("a(" + comp.getName() + ")", true));
            if (depth <= maxDepth) {
                for (int len = 1; len <= 3; len++) {
                    Option a = new Option();
                    a.component = comp;
                    a.arrayLen = len;
                    o.add(a);
                }
            }
        } else {
            o.add(literal("_", true));
            if (t == Object.class || t == java.io.Serializable.class) {
                o.add(literal(text("abc"), false));
                o.add(literal("i:1", false));
                o.add(literal("d:" + Double.doubleToRawLongBits(1.5), false));
                o.add(literal("z:1", false));
                o.add(literal("j:10", false));
                o.add(literal(text(""), true));
                o.add(literal("a(int,i:1,i:2)", false));
            }
            if (t == Number.class) {
                o.add(literal("i:1", false));
                o.add(literal("j:10", false));
                o.add(literal("d:" + Double.doubleToRawLongBits(1.5), false));
                o.add(literal("i:0", true));
                o.add(literal("i:-1", true));
            }
            for (String key : PREFABS) {
                try {
                    if (t.isInstance(AcoReplay.prefab(key))) {
                        o.add(literal("p(" + key + ")", false));
                    }
                } catch (Throwable ignored) {
                    // not available on this JDK
                }
                if (o.size() >= 12) {
                    break;
                }
            }
            if (depth < maxDepth && !t.getName().startsWith("java.")) {
                constructive(s, t, depth);
            }
        }
    }

    static void strings(List<Option> o) {
        String[] base = {"", " ", "a", "abc", "ABC", "0", "1", "-1", "123", "1.5", "-0.0", "0x1F", "1e5", "true",
            "false", "null", "a,b", "a b c", "\n", "\t", "<a>b</a>", "{\"a\":1}", "[1,2]", "é", "%s", "a=b",
            "/a/b", "2020-01-01", "12:30:00", "foo.bar", "a\"b", "#", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"};
        Set<String> all = new LinkedHashSet<String>(Arrays.asList(base));
        all.addAll(mined.strings);
        int n = 0;
        for (String v : all) {
            if (n++ < 60) {
                o.add(literal(text(v), v.length() == 0 || v.trim().length() == 0));
            }
        }
    }

    /** Constructors, static factories and concrete subclasses that yield a value of the type. */
    static void constructive(Slot s, Class<?> t, int depth) {
        int before = s.options.size();
        try {
            if (!t.isInterface() && !Modifier.isAbstract(t.getModifiers())) {
                addConstructors(s, t);
            }
            for (Method m : t.getDeclaredMethods()) {
                int mod = m.getModifiers();
                if (Modifier.isStatic(mod) && !Modifier.isPrivate(mod) && !m.isSynthetic()
                        && t.isAssignableFrom(m.getReturnType()) && m.getParameterTypes().length <= 3
                        && s.options.size() - before < 10) {
                    boolean selfTyped = false;
                    for (Class<?> p : m.getParameterTypes()) {
                        selfTyped |= p == t;
                    }
                    if (!selfTyped) {
                        Option o = new Option();
                        o.method = m;
                        s.options.add(o);
                    }
                }
            }
            if (s.options.size() == before || t.isInterface() || Modifier.isAbstract(t.getModifiers())) {
                for (Class<?> sub : subclasses(t)) {
                    if (s.options.size() - before >= 12) {
                        break;
                    }
                    addConstructors(s, sub);
                }
            }
            if (s.options.size() == before && t != Object.class) {
                for (Class<?> other : packageClasses(t)) {
                    for (Method m : other.getDeclaredMethods()) {
                        int mod = m.getModifiers();
                        if (Modifier.isStatic(mod) && !Modifier.isPrivate(mod) && !m.isSynthetic()
                                && m.getReturnType() != Object.class && t.isAssignableFrom(m.getReturnType())
                                && m.getParameterTypes().length <= 3 && s.options.size() - before < 6) {
                            Option o = new Option();
                            o.method = m;
                            s.options.add(o);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // type could not be inspected; null and prefab values remain
        }
    }

    static void addConstructors(Slot s, Class<?> c) {
        Constructor<?>[] all = c.getDeclaredConstructors();
        Arrays.sort(all, (x, y) -> x.getParameterTypes().length - y.getParameterTypes().length);
        int added = 0;
        for (Constructor<?> k : all) {
            if (!Modifier.isPrivate(k.getModifiers()) && usable(k.getParameterTypes()) && added < 6) {
                Option o = new Option();
                o.ctor = k;
                s.options.add(o);
                added++;
            }
        }
    }

    /** Concrete classes in the same package that can stand in for an abstract type. */
    static List<Class<?>> subclasses(Class<?> t) {
        List<Class<?>> found = new ArrayList<Class<?>>();
        for (Class<?> c : packageClasses(t)) {
            int mod = c.getModifiers();
            if (c != t && t.isAssignableFrom(c) && !Modifier.isAbstract(mod) && !c.isInterface()
                    && !c.isAnonymousClass() && found.size() < 4) {
                found.add(c);
            }
        }
        return found;
    }

    /** Classes compiled into the same package directory as the given type. */
    static List<Class<?>> packageClasses(Class<?> t) {
        Package pkg = t.getPackage();
        String name = pkg == null ? "" : pkg.getName();
        List<Class<?>> found = packageCache.get(name);
        if (found != null) {
            return found;
        }
        found = new ArrayList<Class<?>>();
        packageCache.put(name, found);
        String[] files = new File(binDir, name.replace('.', '/')).list();
        if (files == null) {
            return found;
        }
        Arrays.sort(files);
        for (String f : files) {
            if (!f.endsWith(".class") || found.size() >= 300) {
                continue;
            }
            try {
                found.add(Class.forName((name.length() == 0 ? "" : name + ".") + f.substring(0, f.length() - 6),
                        false, AcoSearch.class.getClassLoader()));
            } catch (Throwable ignored) {
                // skip classes that do not load
            }
        }
        return found;
    }

    // one ant

    static int pick(Slot s) {
        int n = s.options.size();
        double[] w = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            w[i] = Math.pow(s.tau[i], alpha) * Math.pow(s.eta[i], beta);
            sum += w[i];
        }
        if (sum <= 0) {
            return rnd.nextInt(n);
        }
        double r = rnd.nextDouble() * sum;
        for (int i = 0; i < n; i++) {
            r -= w[i];
            if (r <= 0) {
                return i;
            }
        }
        return n - 1;
    }

    /** Walks from a decision point down to a complete value spec, recording the path. */
    static String build(Slot s, List<Step> trail, int depth) {
        int idx = pick(s);
        trail.add(new Step(s, idx));
        Option o = s.options.get(idx);
        if (o.literal != null) {
            return o.literal;
        }
        String path = s.key + ">" + idx;
        StringBuilder sb = new StringBuilder();
        if (o.component != null) {
            sb.append("a(").append(o.component.getName());
            Slot element = valueSlot(path + "@", o.component, depth + 1);
            for (int i = 0; i < o.arrayLen; i++) {
                sb.append(',').append(build(element, trail, depth + 1));
            }
            return sb.append(')').toString();
        }
        Class<?>[] params;
        if (o.ctor != null) {
            params = o.ctor.getParameterTypes();
            sb.append("n(").append(o.ctor.getDeclaringClass().getName()).append(',').append(names(params, "|"));
        } else {
            params = o.method.getParameterTypes();
            Class<?> owner = o.method.getDeclaringClass();
            sb.append("m(").append(owner.getName()).append(',').append(o.method.getName()).append(',')
                .append(names(params, "|")).append(',');
            if (Modifier.isStatic(o.method.getModifiers())) {
                sb.append('_');
            } else {
                sb.append(build(receiverSlot(owner), trail, depth + 1));
            }
        }
        for (int i = 0; i < params.length; i++) {
            sb.append(',').append(build(valueSlot(path + "#" + i, params[i], depth + 1), trail, depth + 1));
        }
        return sb.append(')').toString();
    }

    // coverage feedback

    static Object agentRef;
    static Method dumpMethod;
    static boolean agentChecked;

    static Object agent() {
        if (!agentChecked) {
            agentChecked = true;
            try {
                agentRef = Class.forName("org.jacoco.agent.rt.RT").getMethod("getAgent").invoke(null);
                dumpMethod = Class.forName("org.jacoco.agent.rt.IAgent").getMethod("getExecutionData", boolean.class);
            } catch (Throwable e) {
                agentRef = null;
            }
        }
        return agentRef;
    }

    /** Probes hit since the previous call, as classIndex * 2^20 + probeIndex. */
    static Set<Long> dumpProbes(final Map<String, Integer> classIds) {
        final Set<Long> hit = new HashSet<Long>();
        if (agent() == null) {
            return hit;
        }
        try {
            byte[] data = (byte[]) dumpMethod.invoke(agentRef, true);
            ExecutionDataReader reader = new ExecutionDataReader(new ByteArrayInputStream(data));
            reader.setSessionInfoVisitor(new ISessionInfoVisitor() {
                public void visitSessionInfo(SessionInfo info) {
                }
            });
            reader.setExecutionDataVisitor(new IExecutionDataVisitor() {
                public void visitClassExecution(ExecutionData d) {
                    Integer id = classIds.get(d.getName());
                    if (id == null) {
                        id = classIds.size();
                        classIds.put(d.getName(), id);
                    }
                    boolean[] probes = d.getProbes();
                    for (int i = 0; i < probes.length; i++) {
                        if (probes[i]) {
                            hit.add(((long) id << 20) | i);
                        }
                    }
                }
            });
            reader.read();
        } catch (Throwable ignored) {
            // no coverage for this call
        }
        return hit;
    }

    // constants from the class file

    static void mine(Class<?> c) {
        InputStream raw = c.getClassLoader() == null ? null
                : c.getClassLoader().getResourceAsStream(c.getName().replace('.', '/') + ".class");
        if (raw == null) {
            return;
        }
        try {
            DataInputStream in = new DataInputStream(raw);
            in.readInt();
            in.readInt();
            int count = in.readUnsignedShort();
            String[] utf = new String[count];
            List<Integer> stringRefs = new ArrayList<Integer>();
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1: utf[i] = in.readUTF(); break;
                    case 3: mined.ints.add(in.readInt()); break;
                    case 4: mined.floats.add(in.readFloat()); break;
                    case 5: mined.longs.add(in.readLong()); i++; break;
                    case 6: mined.doubles.add(in.readDouble()); i++; break;
                    case 8: stringRefs.add(in.readUnsignedShort()); break;
                    case 7: case 16: case 19: case 20: in.readUnsignedShort(); break;
                    case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
                    default: in.readInt(); break;
                }
            }
            for (Integer ref : stringRefs) {
                String v = utf[ref];
                if (v != null && v.length() <= 40 && mined.strings.size() < 25) {
                    mined.strings.add(v);
                }
            }
            in.close();
        } catch (Throwable ignored) {
            // constants are optional
        }
    }

    // keeps a test under search from damaging the machine

    static final class Blocked extends SecurityException {
        Blocked(String what) {
            super(what);
        }
    }

    static final class Guard extends SecurityManager {
        private final String tmp = new File(System.getProperty("java.io.tmpdir")).getAbsolutePath();

        public void checkPermission(Permission p) {
            if (!guarding) {
                return;
            }
            String name = p.getName();
            if (p instanceof RuntimePermission && (name.startsWith("exitVM") || name.equals("setSecurityManager"))) {
                throw new Blocked(name);
            }
            if (p instanceof java.io.FilePermission) {
                String actions = p.getActions();
                boolean changes = actions.contains("write") || actions.contains("delete");
                if (actions.contains("execute") || changes && !new File(name).getAbsolutePath().startsWith(tmp)) {
                    throw new Blocked(actions + " " + name);
                }
            }
            if (p instanceof java.net.SocketPermission) {
                throw new Blocked("network " + name);
            }
        }

        public void checkPermission(Permission p, Object context) {
            checkPermission(p);
        }
    }
}
