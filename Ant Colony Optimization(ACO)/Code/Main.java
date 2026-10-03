import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

/**
 * Entry point for Ant Colony Optimization test generator.
 * Run directly in Eclipse (Run As -> Java Application) or via command line.
 *
 * Produces all required deliverables for Person 2 (ACO):
 *   - CFG/createNumber_cfg.txt & CFG/NumberUtils_cfg.txt
 *   - Results/aco_run.txt
 *   - Test/ACOTest.java
 */
public class Main {

    // Defaults, used if Configuration/aco_config.properties is not found.
    private static int numAnts = 20;
    private static int iterations = 30;
    private static double alpha = 1.0;
    private static double beta = 2.0;
    private static double evaporationRate = 0.25;
    private static long randomSeed = 42L;
    private static double initialPheromone = 1.0;

    public static void main(String[] args) throws IOException {
        Path baseDir = findProjectRoot();
        System.out.println("Project root: " + baseDir.toAbsolutePath());

        loadConfig(baseDir);

        ControlFlowGraph cfg = new ControlFlowGraph();
        AntColonyTestGenerator generator = new AntColonyTestGenerator(
                cfg, numAnts, iterations, alpha, beta, evaporationRate,
                randomSeed, initialPheromone);

        long startTime = System.currentTimeMillis();
        generator.run();
        long elapsedMs = System.currentTimeMillis() - startTime;

        // --- Write CFG descriptions (deliverable: CFG/) ---
        Path cfgDir = baseDir.resolve("CFG");
        Files.createDirectories(cfgDir);
        String cfgDescription = cfg.describe();
        writeFile(cfgDir.resolve("createNumber_cfg.txt"), cfgDescription);
        writeFile(cfgDir.resolve("NumberUtils_cfg.txt"), cfgDescription);

        // --- Write results log (deliverable: Results/) ---
        Path resultsDir = baseDir.resolve("Results");
        Files.createDirectories(resultsDir);
        StringBuilder resultsSb = new StringBuilder();
        resultsSb.append("ACO Run Results\n");
        resultsSb.append("Target: org.apache.commons.lang3.math.NumberUtils (Defects4J Lang-1)\n");
        resultsSb.append("Config: ants=").append(numAnts)
                .append(" iterations=").append(iterations)
                .append(" alpha=").append(alpha)
                .append(" beta=").append(beta)
                .append(" evaporation=").append(evaporationRate)
                .append(" seed=").append(randomSeed).append("\n");
        resultsSb.append("ACO Search Time: ").append(elapsedMs).append(" ms\n\n");
        resultsSb.append("Search History:\n");
        for (String line : generator.historyLines) {
            resultsSb.append("  ").append(line).append("\n");
        }
        resultsSb.append("\nCovered leaves: ").append(generator.getCoveredLeaves().size())
                .append("/").append(cfg.leaves().size()).append("\n");
        resultsSb.append("Uncovered leaves: ").append(generator.getUncoveredLeaves()).append("\n\n");
        resultsSb.append("Covering inputs & Tokens:\n");
        for (Map.Entry<String, String> e : generator.getCoveringInputs().entrySet()) {
            resultsSb.append("  ").append(e.getKey()).append(" -> ")
                    .append(e.getValue() == null ? "null" : "\"" + e.getValue() + "\"").append("\n");
        }
        resultsSb.append("\nFinal pheromone levels:\n");
        for (Map.Entry<String, Double> e : generator.getFinalPheromone().entrySet()) {
            resultsSb.append(String.format("  %-25s %.4f%n", e.getKey(), e.getValue()));
        }
        writeFile(resultsDir.resolve("aco_run.txt"), resultsSb.toString());

        // --- Write generated JUnit test suite (deliverable: Test/) ---
        Path testDir = baseDir.resolve("Test");
        Files.createDirectories(testDir);
        String junitCode = JUnitGenerator.generate(
                generator.getCoveringInputs(), "ACOTest", "org.apache.commons.lang3.math");
        writeFile(testDir.resolve("ACOTest.java"), junitCode);

        // --- Console summary ---
        int total = cfg.leaves().size();
        int covered = generator.getCoveredLeaves().size();
        System.out.println("==================================================");
        System.out.println("  Ant Colony Optimization (ACO) Generation Done   ");
        System.out.println("==================================================");
        System.out.println("Branch Coverage: " + covered + "/" + total + " leaves (" + String.format("%.2f", (covered * 100.0 / total)) + "%)");
        System.out.println("Uncovered: " + generator.getUncoveredLeaves());
        System.out.println("Search Time: " + elapsedMs + " ms");
        System.out.println("CFG written to: " + cfgDir.resolve("createNumber_cfg.txt"));
        System.out.println("Results log written to: " + resultsDir.resolve("aco_run.txt"));
        System.out.println("JUnit suite written to: " + testDir.resolve("ACOTest.java"));
    }

    private static void writeFile(Path path, String content) throws IOException {
        try (FileWriter fw = new FileWriter(path.toFile())) {
            fw.write(content);
        }
    }

    private static Path findProjectRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4; i++) {
            if (Files.isDirectory(dir.resolve("Code")) && Files.isDirectory(dir.resolve("Configuration"))) {
                return dir;
            }
            if (dir.getParent() == null) {
                break;
            }
            dir = dir.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }

    private static void loadConfig(Path baseDir) {
        Path propsPath = baseDir.resolve("Configuration").resolve("aco_config.properties");
        if (!Files.exists(propsPath)) {
            System.out.println("No config file found at " + propsPath + " - using defaults.");
            return;
        }
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(propsPath.toFile())) {
            props.load(in);
            numAnts = Integer.parseInt(props.getProperty("number_of_ants", String.valueOf(numAnts)));
            iterations = Integer.parseInt(props.getProperty("iterations", String.valueOf(iterations)));
            alpha = Double.parseDouble(props.getProperty("alpha", String.valueOf(alpha)));
            beta = Double.parseDouble(props.getProperty("beta", String.valueOf(beta)));
            evaporationRate = Double.parseDouble(props.getProperty("evaporation_rate", String.valueOf(evaporationRate)));
            randomSeed = Long.parseLong(props.getProperty("random_seed", String.valueOf(randomSeed)));
            initialPheromone = Double.parseDouble(props.getProperty("initial_pheromone", String.valueOf(initialPheromone)));
            System.out.println("Loaded config from " + propsPath);
        } catch (IOException | NumberFormatException e) {
            System.out.println("Failed to read config (" + e.getMessage() + ") - using defaults.");
        }
    }
}
