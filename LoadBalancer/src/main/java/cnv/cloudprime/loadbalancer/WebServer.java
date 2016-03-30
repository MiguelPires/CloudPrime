package cnv.cloudprime.loadbalancer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.ec2.model.Instance;

public class WebServer {
    private Instance instance;
    private List<BigInteger> pendingRequests = new ArrayList<BigInteger>();

    public WebServer(Instance instance) {
        this.instance = instance;
    }

    /*
     * Returns the index of the newly added pending request
     */
    public synchronized int addRequest(BigInteger request) {
        pendingRequests.add(request);
        return pendingRequests.size() - 1;
    }

    /*
     * Removes the pending request at the specified index 
     */
    public synchronized void removeRequest(int requestIndex) {
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
}
