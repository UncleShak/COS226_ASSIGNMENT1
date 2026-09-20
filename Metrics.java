public class Metrics 
{

    private final long[] winCounts;
    private long totalBids = 0;
    private long totalWaitNanos = 0;
    private long maxWaitNanos = 0;

    public Metrics(int numberOfBidders) 
    {
        winCounts = new long[numberOfBidders];
    }


    public void recordBid(int bidderId, long waitNanos) 
    {
        
        totalBids++;
        winCounts[bidderId]++;
        totalWaitNanos += waitNanos;

        if (waitNanos > maxWaitNanos) 
        {
            maxWaitNanos = waitNanos;
        }
    }

    public long getTotalBids() 
    {
        return totalBids;
    }

    public long[] getWinCounts() 
    {
        return winCounts;
    }

    public double getAverageWaitNanos() 
    {
        return totalBids == 0 ? 0.0 : (double) totalWaitNanos / totalBids;
    }

    public long getMaxWaitNanos() 
    {
        return maxWaitNanos;
    }
}