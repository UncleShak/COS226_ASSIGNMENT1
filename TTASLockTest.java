/*
 * Standalone correctness test for TTASLock, independent of the auction.
 *
 * Several threads increment a shared PLAIN int (deliberately not an AtomicInteger)
 * inside the lock. counter++ is a read-modify-write, so if mutual exclusion is broken
 * updates are lost and the final count comes out below the expected total.
 */
public class TTASLockTest 
{

    private static final int NUMBER_OF_THREADS = 8;
    private static final int INCREMENTS_PER_THREAD = 100000;
    private static final int TRIALS = 5;

    /* Shared unprotected state - the thing the lock has to protect. */
    private static int counter = 0;

    public static void main(String[] args) throws InterruptedException 
    {
        int expected = NUMBER_OF_THREADS * INCREMENTS_PER_THREAD;

        System.out.println("==================================================");
        System.out.println(" TTASLock correctness test");
        System.out.println("==================================================");
        System.out.println("Threads              : " + NUMBER_OF_THREADS);
        System.out.println("Increments per thread: " + INCREMENTS_PER_THREAD);
        System.out.println("Expected total       : " + expected);
        System.out.println();

        boolean allPassed = true;

        for(int trial = 1; trial <= TRIALS; trial++) 
        {
            long actual = runTrial(new TTASLock());
            boolean passed = actual == expected;
            allPassed = allPassed && passed;

            System.out.println("TTASLock trial " + trial + " : expected " + expected
                + ", actual " + actual + "  ->  "
                + (passed ? "PASS - mutual exclusion holds"
                          : "FAIL - lost " + (expected - actual) + " updates"));
        }

        System.out.println();
        System.out.println("TTASLock overall     : " + (allPassed ? "PASS (all " + TRIALS + " trials)" : "FAIL"));

        /*
         * Control run: the same code with a lock that does nothing at all. This is NOT
         * a test of the lock - it exists to show that the workload really is racy, so a
         * passing TTAS run means something.
         */
        System.out.println();
        System.out.println("--------------------------------------------------");
        System.out.println(" CONTROL: same test with a NO-OP lock (no mutual exclusion)");
        System.out.println(" This is expected to FAIL - lost updates prove the race is real.");
        System.out.println("--------------------------------------------------");

        Lock noOpLock = new Lock() 
        {
            public void lock() 
            {
                // deliberately does nothing
            }

            public void unlock() 
            {
                // deliberately does nothing
            }
        };

        long control = runTrial(noOpLock);

        System.out.println("no lock              : expected " + expected + ", actual " + control);

        if(control == expected) 
        {
            System.out.println("no lock              : happened to produce the right total on this run.");
            System.out.println("                       The race is timing dependent - increase");
            System.out.println("                       INCREMENTS_PER_THREAD or NUMBER_OF_THREADS and re-run.");
        } 
        else 
        {
            System.out.println("no lock              : LOST " + (expected - control)
                + " updates, as expected without mutual exclusion.");
        }

        System.out.println("==================================================");
    }

    /* Runs one full trial with the given lock and returns the resulting counter value. */
    private static long runTrial(Lock lock) throws InterruptedException 
    {
        counter = 0;

        Thread[] threads = new Thread[NUMBER_OF_THREADS];

        for(int i = 0; i < NUMBER_OF_THREADS; i++) 
        {
            threads[i] = new Thread(() -> 
            {
                for(int j = 0; j < INCREMENTS_PER_THREAD; j++) 
                {
                    lock.lock();

                    try 
                    {
                        counter++;
                    } 
                    finally 
                    {
                        lock.unlock();
                    }
                }
            });
        }

        for(Thread thread : threads) 
        {
            thread.start();
        }

        for(Thread thread : threads) 
        {
            thread.join();
        }

        return counter;
    }
}
