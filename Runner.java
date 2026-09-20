import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
/*Optional Helper Runner Class*/
public class Runner 
{

    public final int numberOfThreads;
    public final int iterations;
    public final Auction auction;
    public final Lock lock;

    private final AtomicLong totalWaitingTime = new AtomicLong(0);
    private final Metrics metrics;

    public Runner(int numberOfThreads,int iterations,Auction auction,Lock lock) 
    {
        this.numberOfThreads = numberOfThreads;
        this.iterations = iterations;
        this.auction = auction;
        this.lock = lock;
        this.metrics = new Metrics(numberOfThreads);
    }

    public Metrics getMetrics() 
    {
        return metrics;
    }

    public void run() throws InterruptedException 
    {
        Thread[] threads = new Thread[numberOfThreads];

        for(int i = 0; i < numberOfThreads; i++) 
        {
            final int bidderId = i;

            threads[i] = new Thread(() -> {
                bidder(bidderId);
            });
        }

        long startTime = System.nanoTime();

        for(Thread thread : threads) 
        {
            thread.start();
        }

        for(Thread thread : threads) 
        {
            thread.join();
        }

        long endTime = System.nanoTime();

        reportResults(endTime - startTime);
    }

    /*Defines the behaviour of an individual bidder. Note you have to decide how to incorporate your lock.*/
    public void bidder(int bidderId) 
    {
        for(int i = 0; i < iterations; i++) 
        {
            /*
             * Random increment is generated OUTSIDE the lock so the critical section
             * stays as short as possible. ThreadLocalRandom gives each thread its own
             * generator, so there is no hidden contention on a shared Random.
             */
            double increment = 1 + ThreadLocalRandom.current().nextInt(10); // 1..10

            long waitStart = System.nanoTime();
            lock.lock();
            long waitNanos = System.nanoTime() - waitStart;

            try 
            {
                /* ---------------- CRITICAL SECTION START ----------------
                 * The three lines below (read, compute, write) MUST be executed
                 * atomically as one unit. If they were not held together under the
                 * lock, two bidders could both read the SAME highest bid, both compute
                 * a new bid from it, and then:
                 *   - one of the two updates is simply lost (the second write
                 *     overwrites the first), or
                 *   - the higher bid is written first and the lower bid is written
                 *     afterwards, and since Auction.placeBid only compares against
                 *     whatever highestBid it sees, the auction can end up recording a
                 *     bidder/amount pair that is not really the highest.
                 * Auction's fields are plain (non-volatile) too, so without the lock
                 * there is not even a guarantee that one thread sees another's write.
                 */
                double current = auction.getHighestBid();                       // 1. read
                double newBid = current + increment;                            // 2. compute strictly higher
                auction.placeBid(bidderId, newBid);                             // 3. write

                /*
                 * Because newBid is computed from the current highest bid while we hold
                 * the lock, newBid > current always holds, so placeBid ALWAYS succeeds
                 * and this bidder is the highest bidder at this moment. That is why a
                 * bid/win is recorded on every single iteration - there are no rejected
                 * bids in this design.
                 *
                 * recordBid is called INSIDE the critical section on purpose. Metrics
                 * uses plain (non-atomic) fields, so the lock we already hold is what
                 * makes its updates atomic and visible; reportResults only reads them
                 * after every thread has been joined.
                 */
                metrics.recordBid(bidderId, waitNanos);
                /* ---------------- CRITICAL SECTION END ---------------- */
            } 
            finally 
            {
                lock.unlock();
            }

            totalWaitingTime.addAndGet(waitNanos);
        }
    }

    /*Optional Helper: Records and reports the results of the experiment.*/
    public void reportResults(long executionTime) 
    {
        long expectedBids = (long) numberOfThreads * iterations;
        long actualBids = metrics.getTotalBids();
        long[] winCounts = metrics.getWinCounts();

        long mostWins = 0;
        long fewestWins = Long.MAX_VALUE;

        for(long wins : winCounts) 
        {
            if(wins > mostWins) 
            {
                mostWins = wins;
            }

            if(wins < fewestWins) 
            {
                fewestWins = wins;
            }
        }

        if(winCounts.length == 0) 
        {
            fewestWins = 0;
        }

        System.out.println();
        System.out.println("==================================================");
        System.out.println(" Auction Results");
        System.out.println("==================================================");
        System.out.println("Lock                 : " + lock.getClass().getSimpleName());
        System.out.println("Item                 : " + auction.getItemName());
        System.out.println("Threads (bidders)    : " + numberOfThreads);
        System.out.println("Iterations per thread: " + iterations);
        System.out.printf (Locale.US, "Execution time       : %.3f ms%n", executionTime / 1_000_000.0);
        System.out.println("--------------------------------------------------");
        System.out.println("Total bids placed    : " + actualBids);
        System.out.println("Expected bids        : " + expectedBids);
        System.out.println("Bid count check      : " + (actualBids == expectedBids ? "OK" : "MISMATCH"));
        System.out.println("--------------------------------------------------");
        System.out.printf (Locale.US, "Final highest bid    : %.2f%n", auction.getHighestBid());
        System.out.println("Final highest bidder : " + auction.getHighestBidder());
        System.out.println("--------------------------------------------------");
        System.out.println("Bids won per bidder:");

        for(int i = 0; i < winCounts.length; i++) 
        {
            System.out.println("  Bidder " + i + " : " + winCounts[i]);
        }

        System.out.println("  Fairness spread (most - fewest) : " + (mostWins - fewestWins));
        System.out.println("--------------------------------------------------");
        System.out.printf (Locale.US, "Average lock wait    : %.3f us (%.0f ns)%n",
            metrics.getAverageWaitNanos() / 1000.0, metrics.getAverageWaitNanos());
        System.out.printf (Locale.US, "Maximum lock wait    : %.3f us (%d ns)%n",
            metrics.getMaxWaitNanos() / 1000.0, metrics.getMaxWaitNanos());
        System.out.printf (Locale.US, "Total waiting time   : %.3f ms%n", totalWaitingTime.get() / 1_000_000.0);
        System.out.println("==================================================");
    }
}
