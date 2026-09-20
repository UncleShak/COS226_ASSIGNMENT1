import java.util.concurrent.atomic.AtomicReference;

/**
 * MCS Lock (Mellor-Crummey & Scott).
 *
 * Each thread has its own QNode. Threads link themselves onto an explicit
 * queue (tail -> ... -> qnode) via getAndSet, then spin on their OWN
 * node's `locked` flag. The thread ahead of them flips that flag when it
 * unlocks. This is the key difference from CLH: CLH spins on the
 * *predecessor's* field; MCS spins on its *own* field, which is why MCS
 * scales better on NUMA machines (no need to fetch a remote predecessor's
 * cache line while spinning).
 */
public class MCSLock implements Lock {

    private static class QNode {
        volatile boolean locked = false;
        volatile QNode next = null;
    }

    // Points at the last thread in the queue (or null if the lock is free).
    private final AtomicReference<QNode> tail = new AtomicReference<>(null);

    // Each thread reuses its own QNode across lock()/unlock() calls.
    private final ThreadLocal<QNode> myNode = ThreadLocal.withInitial(QNode::new);

    @Override
    public void lock() {
        QNode qnode = myNode.get();
        qnode.next = null; // clear any stale link from a previous acquisition

        QNode pred = tail.getAndSet(qnode); // atomically: I'm the new tail
        if (pred != null) {
            // Someone is ahead of me. Mark myself as waiting, link myself
            // onto their node, then spin on MY OWN flag.
            qnode.locked = true;
            pred.next = qnode;
            while (qnode.locked) {
                // spin
            }
        }
        // pred == null means the queue was empty -> I have the lock immediately.
    }

    @Override
    public void unlock() {
        QNode qnode = myNode.get();

        if (qnode.next == null) {
            // No successor linked yet -- but one might be mid-lock() right
            // now (it already did getAndSet but hasn't set pred.next yet).
            // Try to atomically clear the tail; if that succeeds, the queue
            // really was empty and we're done.
            if (tail.compareAndSet(qnode, null)) {
                return;
            }
            // compareAndSet failed: a successor IS on its way in, just
            // hasn't published qnode.next yet. Wait for it to appear.
            while (qnode.next == null) {
                // spin
            }
        }

        // Hand off the lock to my successor and detach from it.
        qnode.next.locked = false;
        qnode.next = null;
    }
}