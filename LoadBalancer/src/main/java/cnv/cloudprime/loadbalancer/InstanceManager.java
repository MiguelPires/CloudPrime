package cnv.cloudprime.loadbalancer;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

public class InstanceManager {
    // this is measured in seconds
    private static final int GRACE_PERIOD = 120;
    public static final int RUNNING_CODE = 16;
    public static final int PENDING_CODE = 0;

    private AmazonEC2Client ec2Client;
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


    public InstanceManager(String inAWS) throws IOException {
        if (inAWS.equals("true")) {
            URL url = new URL("http://169.254.169.254/latest/meta-data/instance-id");
            URLConnection conn = url.openConnection();
            Scanner s = new Scanner(conn.getInputStream());

            if (s.hasNext()) {
                localInstanceId += s.next();
                //System.out.println(s.next());
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

        updateInstances();
    }

    public Collection<WebServer> getInstances() {
        return instances.values();
    }

    public AmazonEC2Client getClient() {
        return ec2Client;
    }

    /*
     *  Returns a request index and a server (as an output parameter)
     *  If no server was available, returns -1 
     */
    public RequestResult getNextServer(BigInteger inputFactor) {
        int MAX_TRIES = 3;
        if (instances.size() == 0) {
            System.out.println("There are no servers available");
            return new RequestResult();
        }

        for (int i = 0; i < MAX_TRIES; ++i) {
            String newId = "none";
            try {
                lastIndex = (++lastIndex) % instances.size();
                newId = instanceIds.get(lastIndex);
                WebServer server = instances.get(newId);

                // the server might be already lock and marked for removal or
                // still be in its initialization period or not running 
                int requestIndex = server.addRequestIfUnlocked(inputFactor);
                int statusCode = server.getInstance().getState().getCode();
                System.out.println("This server is " + server.getAge() + " seconds old");

                if (requestIndex == -1)
                    continue;
                if (server.getAge() <= GRACE_PERIOD) {
                    System.out.println("Server is " + server.getAge()
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
        }
        return new RequestResult();

    }

    public void increaseGroup(int instancesNum) {
        System.out.println("Adding " + instancesNum + " servers");

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId("ami-e4d75b97").withInstanceType("t2.micro").withMinCount(1)
                .withMaxCount(instancesNum).withKeyName("cnv-lab-aws")
                .withSubnetId("subnet-8cc5dcfb").withSecurityGroupIds("sg-5cdad738")
                .withMonitoring(true);

        ec2Client.runInstances(runInstancesRequest);
        updateInstances();
    }

    public void decreaseGroup(int instancesNum) {
        int removedRequests = 0;

        //TODO: find num servers without requests and remove them atomically 
        /* for (String instanceId : instanceIds) {
            WebServer server = instances.get(instanceId);
            if (server.numPendingRequests() 
            // find server without requests and remove them
        }*/
        
    }

    public void decreaseGroup(String instanceId) {
        System.out.println("Removing a server");

        try {
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
        System.out.println("Ids:");
        for (String id : instanceIds) {
            System.out.println(id);
        }
    }

    public void updateInstances() {
        DescribeInstancesResult describeInstancesRequest = ec2Client.describeInstances();
        List<Reservation> reservations = describeInstancesRequest.getReservations();

        for (Reservation reservation : reservations) {

            // add new servers to the running/pending lists 
            for (Instance instance : reservation.getInstances()) {

                // ignore this instance
                if (instance.getInstanceId().equals(localInstanceId))
                    continue;

                int instanceCode = instance.getState().getCode();
                if (!instances.containsKey(instance.getInstanceId())) {
                    WebServer server = new WebServer(instance);

                    // only add if it's running
                    // NOTE: in the future something should be done with stopped(80) instances

                    if (instanceCode == RUNNING_CODE) {
                        // move it from pending to running
                        if (pendingInstances.containsKey(instance.getInstanceId())) {
                            pendingInstances.remove(instance.getInstanceId());
                        }

                        instances.put(instance.getInstanceId(), server);
                        instanceIds.add(instance.getInstanceId());
                        System.out.println("Found new server: " + instance.getInstanceId());
                        printInstanceIds();
                    } else if (instanceCode == PENDING_CODE) {
                        pendingInstances.put(instance.getInstanceId(), server);
                    }
                } else {
                    if (instanceCode != RUNNING_CODE) {
                        System.out.println("Instance "+instance.getInstanceId()+" stopped working");
                        instanceIds.remove(instance.getInstanceId());
                        instances.remove(instance.getInstanceId());
                    }
                }
            }

        }
        // check if any of the instances are no longer running
        for (String instanceId : instances.keySet()) {
            boolean foundInstance = false;

            for (Reservation reservation : reservations) {
                // ignore this instance
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
                instances.remove(instanceId);
                instanceIds.remove(instanceId);
            }
        }

        // Take the running instances out of the pending list
        /*  for (String instanceId : pendingInstances.keySet()) {
            boolean foundServer = false;
        
            for (Reservation reservation : reservations) {
        
                // ignore this instance
                if (instanceId.equals(localInstanceId))
                    continue;
        
                for (Instance instance : reservation.getInstances()) {
                    if (instance.equals(instanceId)) {
                        InstanceState state = instance.getState();
                        if (state.getCode() == RUNNING_CODE) {
                            System.out.println(
                                    "Switching " + instanceId + " from pending to running");
                            WebServer server = pendingInstances.get(instanceId);
                            pendingInstances.remove(instanceId);
                            instances.put(instanceId, server);
                            instanceIds.add(instanceId);
        
                            foundServer = true;
                            break;
                        }
                    }
                }
        
                if (foundServer)
                    break;
            }
        
        }*/
    }
}
