import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * Replays the specs found by the search and writes one outcome per line.
 * Run against the fixed revision to record the expected results.
 *
 *   java AcoOracle <specs.tsv> <outcomes.txt> [timeoutMs]
 */
public final class AcoOracle {

    static final String TIMEOUT = "TIMEOUT";

    /** Runs one spec in its own thread so a hanging call cannot stall the whole run. */
    @SuppressWarnings("deprecation")
    static String timed(final String spec, long timeoutMs) {
        final String[] box = new String[1];
        Thread worker = new Thread(new Runnable() {
            public void run() {
                box[0] = AcoReplay.run(spec);
            }
        }, "aco-call");
        worker.setDaemon(true);
        worker.start();
        try {
            worker.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (worker.isAlive()) {
            try {
                worker.stop();
            } catch (Throwable ignored) {
                worker.interrupt();
            }
            return TIMEOUT;
        }
        return box[0] == null ? TIMEOUT : box[0];
    }

    public static void main(String[] args) throws Exception {
        long timeout = args.length > 2 ? Long.parseLong(args[2]) : 5000L;
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8"));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[1]), "UTF-8"));
        String line;
        while ((line = in.readLine()) != null) {
            String[] cols = line.split("\t", -1);
            if (cols.length < 2) {
                continue;
            }
            out.println(timed(cols[1], timeout));
            out.flush();
        }
        in.close();
        out.close();
        System.exit(0);
    }
}
