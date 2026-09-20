import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Task 3 driver. Runs TTAS, CLH and MCS across 2/4/8/16 threads, 3 repeats
 * each, with an identical iteration count throughout, and dumps a CSV you
 * can pull straight into the write-up tables.
 *
 * Wired against the real Runner/Metrics: Runner.getMetrics() and
 * Metrics.getTotalBids() / getAverageWaitNanos() / getMaxWaitNanos() /
 * getWinCounts() all exist as-is, no adjustment needed.
 */
public class ExperimentRunner {

    private static final int[] THREAD_COUNTS = {2, 4, 8, 16};
    private static final int ITERATIONS = 1000; // MUST stay identical across every run compared
    private static final int REPEATS = 3;

    public static void main(String[] args) throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{
                "lock", "threads", "run", "timeMs",
                "totalBids", "expectedBids",
                "highestBid", "highestBidder",
                "avgWaitNs", "maxWaitNs",
                "winCounts" // pipe-separated, one entry per bidder id, e.g. "12|9|15|11"
        });

        for (String lockName : List.of("TTAS", "CLH", "MCS")) {
            for (int threads : THREAD_COUNTS) {
                for (int run = 1; run <= REPEATS; run++) {
                    rows.add(runOnce(lockName, threads, run));
                }
            }
        }

        writeCsv(rows, "results.csv");
        System.out.println("Done -- results.csv written (" + (rows.size() - 1) + " runs).");
    }

    private static String[] runOnce(String lockName, int threads, int run) throws InterruptedException {
        Lock lock = createLock(lockName);
        Auction auction = new Auction(AuctionUtils.generateItemName());
        Runner runner = new Runner(threads, ITERATIONS, auction, lock);

        long start = System.nanoTime();
        runner.run();
        long timeMs = (System.nanoTime() - start) / 1_000_000;

        Metrics metrics = runner.getMetrics();
        int expectedBids = threads * ITERATIONS;

        System.out.printf(
                "%-5s threads=%-3d run=%d  time=%5dms  bids=%d/%d  highest=%.2f (bidder %d)%n",
                lockName, threads, run, timeMs,
                metrics.getTotalBids(), expectedBids,
                auction.getHighestBid(), auction.getHighestBidder());

        long[] winCounts = metrics.getWinCounts();
        StringBuilder wc = new StringBuilder();
        for (int i = 0; i < winCounts.length; i++) {
            if (i > 0) wc.append('|');
            wc.append(winCounts[i]);
        }

        return new String[]{
                lockName, String.valueOf(threads), String.valueOf(run),
                String.valueOf(timeMs),
                String.valueOf(metrics.getTotalBids()),
                String.valueOf(expectedBids),
                String.valueOf(auction.getHighestBid()),
                String.valueOf(auction.getHighestBidder()),
                String.valueOf(metrics.getAverageWaitNanos()),
                String.valueOf(metrics.getMaxWaitNanos()),
                wc.toString()
        };
    }

    private static Lock createLock(String name) {
        switch (name) {
            case "TTAS": return new TTASLock();
            case "CLH":  return new CLHLock();
            case "MCS":  return new MCSLock();
            default: throw new IllegalArgumentException("Unknown lock: " + name);
        }
    }

    private static void writeCsv(List<String[]> rows, String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            for (String[] row : rows) {
                pw.println(String.join(",", row));
            }
        }
    }
}
