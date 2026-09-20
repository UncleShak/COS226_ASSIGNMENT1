public class CLHLockTest 
{

    private static int counter = 0;

    public static void main(String[] args) throws InterruptedException 
    {

        int numberOfThreads = 8;
        int incrementsPerThread = 100000;

        CLHLock lock = new CLHLock();
        Thread[] threads = new Thread[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) 
        {
            threads[i] = new Thread(() -> 
            {
                for (int j = 0; j < incrementsPerThread; j++) 
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

        for (Thread t : threads) 
        {
            t.start();
        }

        for (Thread t : threads) 
        {
            t.join();
        }

        int expected = numberOfThreads * incrementsPerThread;

        System.out.println("Expected: " + expected);
        System.out.println("Actual:   " + counter);
        System.out.println(counter == expected 
            ? "PASS - mutual exclusion holds" 
            : "FAIL - lost updates detected");
    }
}