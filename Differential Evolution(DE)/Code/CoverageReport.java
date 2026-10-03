import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.tools.ExecFileLoader;

/** Read final-suite JaCoCo execution data for the modified classes only. */
public final class CoverageReport {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException(
            "CoverageReport <exec> <class-directory> <comma-separated-classes>");
        File execution = new File(args[0]);
        Path directory = Paths.get(args[1]);
        String[] names = args[2].split(",");
        ExecFileLoader loader = new ExecFileLoader();
        loader.load(execution);
        CoverageBuilder builder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), builder);
        Set<String> internalNames = new HashSet<>();
        for (String name : names) {
            String internal = name.replace('.', '/');
            internalNames.add(internal);
            Path bytecode = directory.resolve(internal + ".class");
            if (!Files.isRegularFile(bytecode)) throw new IllegalArgumentException(
                "Compiled modified class missing: " + bytecode);
            analyzer.analyzeClass(Files.readAllBytes(bytecode), bytecode.toString());
        }
        Set<String> executed = new HashSet<>();
        for (ExecutionData data : loader.getExecutionDataStore().getContents())
            if (internalNames.contains(data.getName())) executed.add(data.getName());
        if (executed.isEmpty()) throw new IllegalStateException(
            "No modified class recorded in JaCoCo execution data");
        int linesTotal = 0, linesCovered = 0, branchesTotal = 0, branchesCovered = 0;
        int methodsTotal = 0, methodsCovered = 0;
        for (IClassCoverage item : builder.getClasses()) {
            linesTotal += item.getLineCounter().getTotalCount();
            linesCovered += item.getLineCounter().getCoveredCount();
            branchesTotal += item.getBranchCounter().getTotalCount();
            branchesCovered += item.getBranchCounter().getCoveredCount();
            methodsTotal += item.getMethodCounter().getTotalCount();
            methodsCovered += item.getMethodCounter().getCoveredCount();
        }
        if (linesTotal == 0) throw new IllegalStateException(
            "No executable lines in modified classes");
        Map<String, Object> result = new HashMap<>();
        result.put("LinesTotal", linesTotal);
        result.put("LinesCovered", linesCovered);
        result.put("BranchesTotal", branchesTotal);
        result.put("BranchesCovered", branchesCovered);
        result.put("MethodsTotal", methodsTotal);
        result.put("MethodsCovered", methodsCovered);
        result.put("MeasuredModifiedClasses", executed.size());
        result.put("CoverageTool", "JaCoCo");
        System.out.println("DE_JSON:" + MiniJson.encode(result));
    }
}
