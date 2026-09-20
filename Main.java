public class Main 
{

    public static void main(String[] args) throws InterruptedException 
    {
        int numberOfThreads = 4;
        int iterations = 200;

        /* Optional command-line overrides: java Main [threads] [iterations] */
        if(args.length >= 1) 
        {
            numberOfThreads = Integer.parseInt(args[0]);
        }

        if(args.length >= 2) 
        {
            iterations = Integer.parseInt(args[1]);
        }

        if(numberOfThreads < 1) 
        {
            System.out.println("Thread count must be at least 1.");
            return;
        }

        if(iterations < 1) 
        {
            System.out.println("Iteration count must be at least 1.");
            return;
        }

        Auction auction =new Auction(AuctionUtils.generateItemName());

        /*
         * Swap the lock implementation here to compare designs:
         *     Lock lock = new TTASLock();   // test-and-test-and-set  (Member 1)
         *     Lock lock = new CLHLock();    // CLH queue lock         (Member 2)
         *     Lock lock = new MCSLock();    // MCS queue lock         (Member 3)
         */
        Lock lock = new TTASLock();

        Runner runner = new Runner(numberOfThreads,iterations,auction,lock);
        runner.run();
    }
}
