package cnv.cloudprime.loadbalancer;

import java.math.BigInteger;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.amazonaws.services.ec2.model.Instance;

public class WebServer {
    private Instance instance;
    private ConcurrentHashMap<Long, BigInteger> pendingRequests = new ConcurrentHashMap<Long, BigInteger>();
    private AtomicLong requestId = new AtomicLong();
    private Date launchTime;
    private Lock serverLock = new ReentrantLock();

    public WebServer(Instance instance) {
        this.instance = instance;
        launchTime = instance.getLaunchTime();
    }

    /*
     * Add a request to the server if the lock is not acquired
     * Returns the request index or -1 if the request couldn't be placed
     */
    public synchronized long addRequestIfUnlocked(BigInteger request) {
        try {
            if (serverLock.tryLock()) {
                long id = requestId.incrementAndGet();
                pendingRequests.put(id, request);
                return id;
            } else
                return -1;
        } finally {
            serverLock.unlock();
        }
    }

    /*
     * Removes the pending request at the specified index 
     */
    public synchronized void removeRequest(long requestIndex) {
        pendingRequests.remove(requestIndex);
    }

    /*
     * Returns the number of pending requests
     */
    public synchronized int numPendingRequests() {
        return pendingRequests.size();
    }

    public Instance getInstance() {
        return instance;
    }

    /*
     * Returns the instance's age in seconds
     */
    public long getAge() {
        return (new Date().getTime() - launchTime.getTime()) / 1000;
    }

    /*
     *  Acquires the lock on this server if there is no current running request 
     *  Returns true if the lock was acquired and no request will be added after the invocation
     *  Returns false if the server isn't idle
     */
    public synchronized boolean lockIfIdle() {
        if (pendingRequests.isEmpty()) {
            serverLock.lock();
            return true;
        } else
            return false;
    }
}
