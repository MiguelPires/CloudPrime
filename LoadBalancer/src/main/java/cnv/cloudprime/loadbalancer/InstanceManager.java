package cnv.cloudprime.loadbalancer;

import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

public class InstanceManager {
    // instance execution codes 
    public static final int RUNNING_CODE = 16;
    public static final int PENDING_CODE = 0;

    // *** System Parameters ***
    // how many failed checks the system must see taking action 
    public static final int HEALTHY_TRESHOLD = 2;
    // how frequently we check for the instances' health
    public static final int HEALTH_CHECK_PERIOD = 5000;
    // this is measured in seconds
    static final int GRACE_PERIOD = 125;

    AmazonEC2Client ec2Client;
    private int lastIndex = 0;
    // list of instance identifiers
    private List<String> instanceIds = new ArrayList<String>();
    // running instances
    private ConcurrentHashMap<String, WebServer> instances =
        new ConcurrentHashMap<String, WebServer>();
    // pending instances
    private ConcurrentHashMap<String, WebServer> pendingInstances =
        new ConcurrentHashMap<String, WebServer>();
    // this instance's id
    String localInstanceId = "";
    // the regression model that models the distribution of a "cost"
    // variable (dependent) and a factor variable (independent)
    BigIntRegression costModel = new BigIntRegression();
    //
    BigIntRegression timeModel = new BigIntRegression();

    public InstanceManager(String inAWS) throws IOException {
        if (inAWS.equals("true")) {
            URL url = new URL("http://169.254.169.254/latest/meta-data/instance-id");
            URLConnection conn = url.openConnection();
            Scanner s = new Scanner(conn.getInputStream());

            if (s.hasNext()) {
                localInstanceId += s.next();
            }
            s.close();
        }

        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (C:\\Users\\Miguel\\.aws\\credentials).
         */
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider("credentials", "default").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. "
                            + "Please make sure that your credentials file is at the correct "
                            + "location (C:\\Users\\Miguel\\.aws\\credentials), and is in valid format.",
                    e);
        }
        ec2Client = new AmazonEC2Client(credentials);
        ec2Client.setEndpoint("https://ec2.eu-west-1.amazonaws.com");

        Thread metricsUpdater = new Thread(new MetricsUpdater(this));
        metricsUpdater.start();

        updateInstances();
    }

    public Collection<WebServer> getInstances() {
        return instances.values();
    }

    public AmazonEC2Client getClient() {
        return ec2Client;
    }

    public void addCostDatapoint(BigInteger x, BigInteger y) {
        costModel.addData(x, y);
    }


    public void addTimeDatapoint(BigInteger x, BigInteger y) {
        timeModel.addData(x, y);

    }

    /*
     *              Estimation functions
     * Returns an estimation of cost/time for the specified server according to the
     * specified regression model
     * 
     */

    // the generic estimation function
    public BigInteger estimateServerLoad(WebServer server, BigIntRegression model) {
        Collection<BigInteger> requests = server.getRequests();
        BigInteger sum = new BigInteger("0");

        for (BigInteger request : requests) {
            sum = model.predict(request);
        }

        if (sum.compareTo(BigInteger.ZERO) == 0) {
            return BigInteger.ZERO;
        }

        if (model.getNumberOfPoints().compareTo(new BigInteger("2")) == 1)
            System.out.println("R-squared: " + model.getRSquared());

        return sum;
    }

    //  time-based estimation
    public BigInteger estimateServerTime(WebServer server) {
        return estimateServerLoad(server, timeModel);
    }

    //  cost-based estimation
    public BigInteger estimateServerCost(WebServer server) {
        return estimateServerLoad(server, costModel);
    }

    /*
     * Returns and estimation for the specified server according to the
     * cost model but discounts requests that are close to completion
     */
    public BigInteger estimateServerDiscounted(WebServer server) {
        BigInteger sum = new BigInteger("0");

        for (Long requestId : server.pendingRequests.keySet()) {
            BigInteger request = server.pendingRequests.get(requestId);
            Long requestTime = server.requestTimes.get(requestId);

            BigInteger costPrediction = costModel.predict(request);
            BigInteger timePrediction = timeModel.predict(request);
            System.out.println("Time prediction: " + timePrediction);

            Long elapsedTime = new Date().getTime() / 1000 - requestTime;
            BigInteger timeLeft =
                timeModel.predict(request).subtract(new BigInteger(elapsedTime.toString()));
            System.out.println("Time left: " + timeLeft);

            if (timeLeft.compareTo(BigInteger.ZERO) != 1)
                timeLeft = BigInteger.ZERO;

            if (timeLeft.compareTo(new BigInteger("10")) != 1) {
                costPrediction = BigInteger.ZERO;
                System.out.println("Request " + request + " will only take " + timeLeft
                        + " seconds to complete. Ignoring");
            }
            sum = sum.add(costPrediction);
        }

        if (sum.compareTo(BigInteger.ZERO) == 0) {
            return BigInteger.ZERO;
        }

        if (costModel.getNumberOfPoints().compareTo(new BigInteger("2")) == 1)
            System.out.println("R-squared: " + costModel.getRSquared());

        return sum;
    }

    /*
     *              Algorithm
     *  Returns the least loaded server based on a certain algorithm
     */

    /*
     *  Obtains the least loaded server according to the estimated time to completion 
     *  of the requests at the server and of this request
     */
    public RequestResult getServerTimeBased(BigInteger inputFactor) {
        return getServerModelBased(inputFactor, (server) -> {
            return estimateServerTime(server);
        });
    }

    /*
     *  Obtains the least loaded server according to the estimated cost of
     *  the running requests 
     */
    public RequestResult getServerCostBased(BigInteger inputFactor) {
        return getServerModelBased(inputFactor, (server) -> {
            return estimateServerCost(server);
        });
    }

    /*
     *  Obtains the least loaded server according to the estimated cost of
     *  the running requests but also uses time predictions to improve edge cases
     */
    public RequestResult getServerTimeDiscounted(BigInteger inputFactor) {
        return getServerModelBased(inputFactor, (server) -> {
            return estimateServerDiscounted(server);
        });
    }

    /*
     *  Obtains the least loaded server based on the predictions of the specified 
     * estimation function
     */
    private RequestResult getServerModelBased(BigInteger inputFactor,
                                              Function<WebServer, BigInteger> estimationFunc) {
        if (instances.size() == 0) {
            System.out.println("There are no servers available");
            return new RequestResult();
        }
        try {
            BigInteger lowestCost = new BigInteger("-1");
            WebServer leastBusyServer = null;

            for (String instanceId : instanceIds) {
                WebServer server = instances.get(instanceId);
                int statusCode = server.getInstance().getState().getCode();

                if (server.getAge() <= GRACE_PERIOD || statusCode != RUNNING_CODE) {
                    System.out.println("Ignoring server: " + instanceId);
                    continue;
                }

                BigInteger estimatedLoad = estimationFunc.apply(server);
                if (estimatedLoad.compareTo(lowestCost) == -1 || lowestCost.intValue() == -1) {
                    lowestCost = estimatedLoad;
                    leastBusyServer = server;
                }
                System.out.println("Server " + server.getInstance().getInstanceId() + " has load "
                        + estimatedLoad.toString());
            }

            if (leastBusyServer != null) {
                System.out.println("Chose server " + leastBusyServer.getInstance().getInstanceId()
                        + " with load " + lowestCost.toString());
                long requestIndex = leastBusyServer.addRequestIfUnlocked(inputFactor);
                return new RequestResult(leastBusyServer, requestIndex);
            } else
                return new RequestResult();
        } catch (Exception e) {
            // we need at least two data points
            return getServerRoundRobin(inputFactor);
        }
    }

    /*
     *  Returns a request index and a server (as an output parameter)
     *  If no server was available, returns -1 
     */
    public RequestResult getServerRoundRobin(BigInteger inputFactor) {
        if (instances.size() == 0) {
            System.out.println("There are no servers available");
            return new RequestResult();
        }

        int firstIndex = lastIndex;

        do {
            String newId = "none";
            try {
                lastIndex = (++lastIndex) % instances.size();
                newId = instanceIds.get(lastIndex);
                WebServer server = instances.get(newId);

                // the server might be already lock and marked for removal or
                // still be in its initialization period or not running 
                long requestIndex = server.addRequestIfUnlocked(inputFactor);
                int statusCode = server.getInstance().getState().getCode();

                if (requestIndex == -1)
                    continue;
                if (server.getAge() <= GRACE_PERIOD) {
                    System.out.println("The server '" + newId + "' is " + server.getAge()
                            + " seconds old (still in grace period)");
                    continue;
                }
                if (statusCode != RUNNING_CODE) {
                    System.out.println("Server " + newId + " isn't running");
                    continue;
                } else {
                    return new RequestResult(server, requestIndex);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } while (firstIndex != lastIndex);

        return new RequestResult();
    }

    /*
     *  Adds a certain number of instances
     */
    public void increaseGroup(int instancesNum) {
        System.out.println("#\tAdding " + instancesNum + " servers");

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId("ami-fd1f948e").withInstanceType("t2.micro").withMinCount(1)
                .withMaxCount(instancesNum).withKeyName("cnv-lab-aws")
                .withSubnetId("subnet-8cc5dcfb").withSecurityGroupIds("sg-5cdad738")
                .withMonitoring(true)
                .withIamInstanceProfile(new IamInstanceProfileSpecification().withName("s3writer"));

        ec2Client.runInstances(runInstancesRequest);
        updateInstances();
    }

    /*
     *  Tries to remove a certain number of instances without making requests fail
     *  Returns the number of instances actually removed (may be fewer than requested)
     */
    public int decreaseGroup(int instancesNum) {
        int removedInstances = 0;

        for (int i = 0; i < instanceIds.size();) {
            String instanceId = instanceIds.get(i);
            WebServer server = instances.get(instanceId);

            try {
                if (server.lockIfIdle()) {
                    decreaseGroup(instanceId);
                    if (++removedInstances == instancesNum)
                        return removedInstances;
                } else {
                    i++;
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Couldn't remove instance " + instanceId);
            }
        }

        return removedInstances;
    }

    /*
     *  Removes the specified instance from the cluster
     */
    public void decreaseGroup(String instanceId) {
        System.out.println("#\tRemoving server '" + instanceId + "'");

        try {
            instances.get(instanceId).shutdown();
            // TODO: remove the pending requests and add them to another server

            TerminateInstancesRequest terminationRequest = new TerminateInstancesRequest();
            terminationRequest.withInstanceIds(instanceId);
            ec2Client.terminateInstances(terminationRequest);
            instanceIds.remove(instanceId);
            instances.remove(instanceId);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Couldn't remove instance " + instanceId);
        }
    }

    public int runningInstances() {
        return instances.size();
    }

    public int pendingInstances() {
        return pendingInstances.size();
    }

    public void printInstanceIds() {
        System.out.println("#\tIds:");
        for (String id : instanceIds) {
            System.out.println("#\t" + id);
        }
    }

    public void updateInstances() {
        DescribeInstancesResult describeInstancesRequest = ec2Client.describeInstances();
        List<Reservation> reservations = describeInstancesRequest.getReservations();

        checkForAvailableInstances(reservations);
        checkForUnavailableInstances(reservations);
    }

    /*
     *  Checks for new instances and adds them to running/pending lists
     *   
     */
    private void checkForAvailableInstances(List<Reservation> reservations) {
        for (Reservation reservation : reservations) {
            for (Instance instance : reservation.getInstances()) {

                // ignore this instance (if it's running in AWS)
                if (instance.getInstanceId().equals(localInstanceId))
                    continue;

                int instanceCode = instance.getState().getCode();
                if (!instances.containsKey(instance.getInstanceId())) {
                    WebServer server = new WebServer(instance, this);

                    // only add if it's running && out of the grace period
                    if (instanceCode == RUNNING_CODE && server.getAge() > GRACE_PERIOD) {
                        // move it from pending to running
                        if (pendingInstances.containsKey(instance.getInstanceId())) {
                            pendingInstances.remove(instance.getInstanceId());
                        }

                        try {
                            server.initHealthChecker();
                            instances.put(instance.getInstanceId(), server);
                            instanceIds.add(instance.getInstanceId());
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                        System.out.println("#\tFound new server: " + instance.getInstanceId());
                        printInstanceIds();
                    } else if (instanceCode == PENDING_CODE) {
                        pendingInstances.put(instance.getInstanceId(), server);
                    }
                } else {
                    if (instanceCode != RUNNING_CODE) {
                        System.out.println(
                                "#\tInstance " + instance.getInstanceId() + " stopped working");
                        instances.get(instance.getInstanceId()).shutdown();
                        instanceIds.remove(instance.getInstanceId());
                        instances.remove(instance.getInstanceId());
                    }
                }
            }
        }
    }

    /*
     *  Checks if any of the instances we know about were removed
     *  Removes unavailable instances from the manager's list of instances
     */
    private void checkForUnavailableInstances(List<Reservation> reservations) {
        // check if any of the instances are no longer running
        for (String instanceId : instances.keySet()) {
            boolean foundInstance = false;

            for (Reservation reservation : reservations) {
                // ignore this instance (if the loadbalancer/autoscaler is running in AWS)
                if (instanceId.equals(localInstanceId))
                    continue;

                for (Instance instance : reservation.getInstances()) {
                    if (instance.getInstanceId().equals(instanceId)) {
                        foundInstance = true;
                        break;
                    }
                }
                if (foundInstance) {
                    break;
                }
            }

            if (!foundInstance) {
                instances.get(instanceId).shutdown();
                instances.remove(instanceId);
                instanceIds.remove(instanceId);
            }
        }
    }
}
