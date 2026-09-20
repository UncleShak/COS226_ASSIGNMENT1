import java.util.concurrent.atomic.AtomicReference;

public class CLHLock implements Lock 
{

    private final AtomicReference<QNode> tail = new AtomicReference<QNode>(new QNode());

    private final ThreadLocal<QNode> myNode = new ThreadLocal<QNode>() 
    {
        protected QNode initialValue() 
        {
            return new QNode();
        }
    };

    private final ThreadLocal<QNode> myPred = new ThreadLocal<QNode>();

    public void lock() 
    {
        QNode qnode = myNode.get();
        qnode.locked = true;
        QNode pred = tail.getAndSet(qnode);
        myPred.set(pred);

        while (pred.locked) 
        {
            // spin on the predecessor's node
        }
    }

    public void unlock() 
    {
        QNode qnode = myNode.get();
        qnode.locked = false;
        myNode.set(myPred.get());
    }

    private static class QNode 
    {
        volatile boolean locked = false;
    }
}