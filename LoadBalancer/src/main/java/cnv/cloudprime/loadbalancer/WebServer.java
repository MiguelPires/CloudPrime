package cnv.cloudprime.loadbalancer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;

public class WebServer {
    private Instance instance;
    private ConcurrentHashMap<Long, BigInteger> pendingRequests =
        new ConcurrentHashMap<Long, BigInteger>();
    private AtomicLong requestId = new AtomicLong();
    private Date launchTime;
    private Lock serverLock = new ReentrantLock();
    private Thread healthChecker;
    private int unhealthyChecks = 0;
    private InstanceManager instanceManager;

    public WebServer(Instance instance, InstanceManager manager) {
        this.instance = instance;
        launchTime = instance.getLaunchTime();
        instanceManager = manager;
    }

    /*
     *  Initializes the health checker. Should be invoked when the instance
     *  switches to a running state
     */
    public void initHealthChecker() throws MalformedURLException {
        healthChecker = new Thread(new HealthChecker());
        healthChecker.start();
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

    public class HealthChecker
            implements Runnable {

        private URL checkUrl;

        public HealthChecker() throws MalformedURLException {
            String serverIp = getInstance().getPublicIpAddress();
            checkUrl = new URL("http://" + serverIp + ":8000/f.html?n=9");
        }

        @Override
        public void run() {
            while (true) {
                if (getAge() >= InstanceManager.GRACE_PERIOD) {
                    int responseCode;
                    String factorOutput;

                    try {
                        HttpURLConnection connection =
                            (HttpURLConnection) checkUrl.openConnection();
                        // read the response
                        responseCode = connection.getResponseCode();
                        InputStream inputStream = connection.getInputStream();
                        BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
                        factorOutput = rd.readLine();

                        // the instance is unhealthy
                        if (responseCode != 200 || !factorOutput.equals("3, 3.")) {
                            throw new WrongFactorOutput(
                                    "Health Check - Wrong output: " + factorOutput);
                        } else if (unhealthyChecks != 0) {
                            unhealthyChecks = 0;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        ++unhealthyChecks;
                        System.out.println("Health Check - Unhealthy instance detected. Strike: "
                                + unhealthyChecks);

                        if (unhealthyChecks >= InstanceManager.HEALTHY_TRESHOLD) {
                            System.out.println("Rebooting instance");

                            // reset the server's state
                            unhealthyChecks = 0;
                            launchTime = new Date();

                            List<String> instanceIds = new ArrayList<String>();
                            instanceIds.add(getInstance().getInstanceId());
                            RebootInstancesRequest rebootRequest =
                                new RebootInstancesRequest(instanceIds);
                            instanceManager.ec2Client.rebootInstances(rebootRequest);
                        }
                    }
                }
                try {
                    Thread.sleep(InstanceManager.HEALTH_CHECK_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }
}
