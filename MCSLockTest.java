/**
 * Standalone correctness test for MCSLock -- run this BEFORE plugging the
 * lock into the auction. Several threads hammer a shared counter inside
 * lock()/unlock(); if the lock provides real mutual exclusion, the final
 * count is exact. Any lost updates mean two threads were in the critical
 * section at once.
 */
public class MCSLockTest {

    public static void main(String[] args) throws InterruptedException {
        int[] threadCounts = {2, 4, 8, 16};
        int incrementsPerThread = 200_000;

        for (int numThreads : threadCounts) {
            boolean passed = runTest(numThreads, incrementsPerThread);
            System.out.println((passed ? "PASS" : "FAIL")
                    + " -- " + numThreads + " threads x " + incrementsPerThread + " increments");
        }
    }

    private static boolean runTest(int numThreads, int incrementsPerThread) throws InterruptedException {
        MCSLock lock = new MCSLock();
        int[] counter = {0}; // effectively-final holder so the lambda can mutate it

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    lock.lock();
                    try {
                        counter[0]++;
                    } finally {
                        lock.unlock();
                    }
                }
            });
        }

        long start = System.nanoTime();
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        int expected = numThreads * incrementsPerThread;
        System.out.println("  expected=" + expected + " actual=" + counter[0] + " (" + elapsedMs + " ms)");
        return counter[0] == expected;
    }
}