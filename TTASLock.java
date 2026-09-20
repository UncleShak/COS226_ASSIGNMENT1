import java.util.concurrent.atomic.AtomicBoolean;

/*
 * Test-and-test-and-set (TTAS) spin lock.
 * Herlihy & Shavit, "The Art of Multiprocessor Programming", Chapter 7.
 *
 * Uses no Java built-in locking facilities at all - only a single AtomicBoolean.
 */
public class TTASLock implements Lock 
{

    private final AtomicBoolean state = new AtomicBoolean(false);

    public void lock() 
    {
        while (true) 
        {
            /*
             * Inner "test" spin: state.get() is an ordinary read, so on a cache-coherent
             * machine the spinning thread pulls the cache line into its own cache in
             * SHARED state once and then re-reads it locally. Those reads generate NO
             * bus/interconnect traffic at all while the lock stays held.
             *
             * Plain TAS instead calls getAndSet(true) in a tight loop. getAndSet is a
             * read-modify-write, so every single attempt by every spinning thread must
             * obtain the line in EXCLUSIVE/MODIFIED state, invalidating it in all the
             * other caches. Under contention that floods the bus with invalidation
             * traffic and slows down even the thread that is holding the lock (and the
             * one trying to release it), because its own memory accesses are competing
             * for the same saturated bus.
             */
            while (state.get()) 
            {
                // spin on a locally cached copy - read only, no coherence traffic
            }

            /*
             * The lock looked free, so now (and only now) pay for the expensive
             * read-modify-write. If getAndSet returns false we observed false and
             * atomically set true, so we won the lock. If it returns true another
             * thread beat us to it between our read and our RMW, so go back to the
             * cheap local spin instead of hammering getAndSet.
             */
            if (!state.getAndSet(true)) 
            {
                return;
            }
        }
    }

    public void unlock() 
    {
        /*
         * A plain atomic write. It invalidates the line in the spinners' caches, which
         * is exactly what releases them from the inner read-only loop.
         */
        state.set(false);
    }
}
