import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Core Ant Colony Optimization engine for automated test generation.
 *
 * Workflow implemented here (SQA Round 2 Workflow section 12):
 *   Initialize Pheromone
 *      -> Path Construction (per ant, guided by pheromone & heuristic)
 *      -> Input Generation (via InputSynthesizer)
 *      -> Execute Test & Validation (via Oracle)
 *      -> Coverage Bookkeeping
 *      -> Pheromone Update (evaporation + reinforcement deposit)
 *      -> Repeat / Termination
 *      -> Generate JUnit Test Suite
 */
public class AntColonyTestGenerator {

    private final ControlFlowGraph cfg;
    private final int numAnts;
    private final int iterations;
    private final double alpha;
    private final double beta;
    private final double evaporationRate;
    private final Random rng;

    private final Map<String, Double> pheromone = new LinkedHashMap<>();
    private final Set<String> coveredLeaves = new LinkedHashSet<>();
    private final Map<String, String> coveringInputs = new LinkedHashMap<>();
    public final List<String> historyLines = new ArrayList<>();

    public AntColonyTestGenerator(ControlFlowGraph cfg, int numAnts, int iterations,
                                   double alpha, double beta, double evaporationRate,
                                   long randomSeed, double initialPheromone) {
        this.cfg = cfg;
        this.numAnts = numAnts;
        this.iterations = iterations;
        this.alpha = alpha;
        this.beta = beta;
        this.evaporationRate = evaporationRate;
        this.rng = new Random(randomSeed);

        for (Edge e : cfg.edges) {
            pheromone.put(e.id, initialPheromone);
        }
    }

    private double heuristic(Edge edge) {
        List<String> reachable = cfg.reachableLeaves(edge.to);
        long uncovered = reachable.stream().filter(l -> !coveredLeaves.contains(l)).count();
        return uncovered + 0.1; // small epsilon avoids zero heuristic on dead paths
    }

    /** One ant walks from root to a leaf using roulette-wheel selection. */
    private Object[] constructPath() {
        String current = cfg.rootNode;
        List<String> pathEdges = new ArrayList<>();

        while (!cfg.isLeaf(current)) {
            List<Edge> options = cfg.adjacency.get(current);
            if (options == null || options.isEmpty()) {
                break;
            }
            double[] weights = new double[options.size()];
            double total = 0;
            for (int i = 0; i < options.size(); i++) {
                Edge e = options.get(i);
                double tau = Math.pow(pheromone.getOrDefault(e.id, 1.0), alpha);
                double eta = Math.pow(heuristic(e), beta);
                weights[i] = tau * eta;
                total += weights[i];
            }

            double r = rng.nextDouble() * total;
            double cumulative = 0;
            Edge chosen = options.get(options.size() - 1);
            for (int i = 0; i < options.size(); i++) {
                cumulative += weights[i];
                if (r <= cumulative) {
                    chosen = options.get(i);
                    break;
                }
            }
            pathEdges.add(chosen.id);
            current = chosen.to;
        }
        return new Object[]{pathEdges, current};
    }

    private void evaporate() {
        for (String id : pheromone.keySet()) {
            pheromone.put(id, pheromone.get(id) * (1.0 - evaporationRate));
        }
    }

    private void deposit(List<String> pathEdges, boolean newlyCovered) {
        double amount = newlyCovered ? 2.5 : 0.5;
        double perEdge = amount / Math.max(pathEdges.size(), 1);
        for (String id : pathEdges) {
            pheromone.put(id, pheromone.get(id) + perEdge);
        }
    }

    @SuppressWarnings("unchecked")
    public void run() {
        int totalLeaves = cfg.leaves().size();

        for (int it = 1; it <= iterations; it++) {
            int newCoveredThisIteration = 0;

            for (int ant = 0; ant < numAnts; ant++) {
                Object[] result = constructPath();
                List<String> pathEdges = (List<String>) result[0];
                String targetLeaf = (String) result[1];

                String candidate = InputSynthesizer.synthesize(targetLeaf, rng);
                boolean verified = Oracle.verify(targetLeaf, candidate);
                boolean newlyCovered = verified && !coveredLeaves.contains(targetLeaf);

                if (verified) {
                    coveringInputs.putIfAbsent(targetLeaf, candidate);
                    if (newlyCovered) {
                        coveredLeaves.add(targetLeaf);
                        newCoveredThisIteration++;
                    }
                }

                deposit(pathEdges, newlyCovered);
            }

            evaporate();

            historyLines.add(String.format(
                    "iteration=%d covered_so_far=%d/%d new_this_iteration=%d",
                    it, coveredLeaves.size(), totalLeaves, newCoveredThisIteration));

            if (coveredLeaves.size() == totalLeaves) {
                break; // Termination condition: 100% leaf coverage reached early
            }
        }
    }

    public Set<String> getCoveredLeaves() {
        return coveredLeaves;
    }

    public Set<String> getUncoveredLeaves() {
        Set<String> uncovered = new TreeSet<>(cfg.leaves());
        uncovered.removeAll(coveredLeaves);
        return uncovered;
    }

    public Map<String, String> getCoveringInputs() {
        return coveringInputs;
    }

    public Map<String, Double> getFinalPheromone() {
        return pheromone;
    }
}
