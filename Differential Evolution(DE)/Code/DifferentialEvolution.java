import java.util.*;
import java.util.function.ToDoubleFunction;

/** Maximizing DE/rand/1/bin. Mutation operates on real vectors, decoding rounds them. */
final class DifferentialEvolution {
    static final class Stats {
        int evaluations, trialEvaluations, generations, accepted;
        double best = -Double.MAX_VALUE;
        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("evaluations", evaluations); m.put("trial_evaluations", trialEvaluations);
            m.put("generations", generations); m.put("accepted_trials", accepted);
            m.put("de_applied", trialEvaluations > 0); m.put("best_fitness", best);
            return m;
        }
    }
    static Stats search(int dimensions, int size, int budget, double f, double cr,
                        int bound, long seed, ToDoubleFunction<int[]> objective) {
        if (dimensions < 1 || size < 4 || budget <= size || bound < 1
                || f <= 0 || f > 2 || cr < 0 || cr > 1)
            throw new IllegalArgumentException("Invalid DE settings");
        Random random = new Random(seed);
        double[][] pop = new double[size][dimensions];
        double[] scores = new double[size];
        Stats stats = new Stats();
        Map<String, Double> cache = new HashMap<>();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < dimensions; j++)
                pop[i][j] = i == 0 ? 0 : (random.nextDouble() * 2 - 1) * bound;
            scores[i] = evaluate(pop[i], objective, cache, stats, false);
        }
        int stagnant = 0;
        while (stats.evaluations < budget && stagnant < 20) {
            int before = stats.evaluations;
            stats.generations++;
            // Use a snapshot so population updates do not bias later donors.
            double[][] parents = Arrays.stream(pop).map(double[]::clone).toArray(double[][]::new);
            for (int i = 0; i < size && stats.evaluations < budget; i++) {
                int a, b, c;
                do { a = random.nextInt(size); } while (a == i);
                do { b = random.nextInt(size); } while (b == i || b == a);
                do { c = random.nextInt(size); } while (c == i || c == a || c == b);
                double[] trial = parents[i].clone();
                int forced = random.nextInt(dimensions);
                for (int j = 0; j < dimensions; j++) {
                    if (j == forced || random.nextDouble() < cr) {
                        double donor = parents[a][j] + f * (parents[b][j] - parents[c][j]);
                        trial[j] = Math.max(-bound, Math.min(bound, donor));
                    }
                }
                double score = evaluate(trial, objective, cache, stats, true);
                if (score >= scores[i]) {
                    pop[i] = trial; scores[i] = score; stats.accepted++;
                }
            }
            stagnant = stats.evaluations == before ? stagnant + 1 : 0;
        }
        return stats;
    }
    private static double evaluate(double[] vector, ToDoubleFunction<int[]> objective,
                                   Map<String, Double> cache, Stats stats, boolean trial) {
        int[] decoded = Arrays.stream(vector).mapToInt(x -> (int)Math.round(x)).toArray();
        String key = Arrays.toString(decoded);
        Double score = cache.get(key);
        if (score == null) {
            score = objective.applyAsDouble(decoded);
            if (!Double.isFinite(score)) throw new IllegalStateException("Non-finite fitness");
            cache.put(key, score); stats.evaluations++;
            if (trial) stats.trialEvaluations++;
            stats.best = Math.max(stats.best, score);
        }
        return score;
    }
}
