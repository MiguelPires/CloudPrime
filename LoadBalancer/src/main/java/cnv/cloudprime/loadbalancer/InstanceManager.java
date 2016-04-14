package cnv.cloudprime.loadbalancer;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
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

public class InstanceManager {

    private AmazonEC2Client ec2Client;
    private int lastServer = 0;
    // running instances
    private ConcurrentHashMap<String, WebServer> instances =
        new ConcurrentHashMap<String, WebServer>();
    // pending instances
    private ConcurrentHashMap<String, WebServer> pendingInstances =
        new ConcurrentHashMap<String, WebServer>();
    // this instance's id
    String localInstanceId = "";

    public static final int RUNNING_CODE = 16;
    public static final int PENDING_CODE = 0;

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

    public WebServer getNextServer() {
        if (instances.size() != 0) {
            lastServer = (++lastServer) % instances.size();
            WebServer server = instances.get(lastServer);
            if (server.getInstance().getState().getCode() != RUNNING_CODE) {
                System.out.println(
                        "Server '" + server.getInstance().getInstanceId() + "' isn't running");
                return null;

            } else
                return server;
        } else {
            System.out.println("There are no servers available");
            return null;
        }
    }

    public void increaseGroup(int instancesNum) {
        System.out.println("Adding " + instancesNum + " servers");

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId("ami-5136b022").withInstanceType("t2.micro").withMinCount(1)
                .withMaxCount(instancesNum).withKeyName("cnv-lab-aws")
                .withSubnetId("subnet-8cc5dcfb").withSecurityGroupIds("sg-5cdad738");

        ec2Client.runInstances(runInstancesRequest);
        updateInstances();
    }

    public void decreaseGroup(int instancesNum) {
        throw new UnsupportedOperationException();
    }

    public int runningInstances() {
        return instances.size();
    }

    public int pendingInstances() {
        return pendingInstances.size();
    }

    public void printInstanceIds() {
        System.out.println("Ids:");
        for (String id : instances.keySet()) {
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

                if (!instances.containsKey(instance.getInstanceId())) {
                    WebServer server = new WebServer(instance);

                    // only add if it's running
                    // NOTE: in the future something should be done with stopped(80) instances

                    int instanceCode = instance.getState().getCode();
                    if (instanceCode == RUNNING_CODE) {
                        // move it from pending to running
                        if (pendingInstances.containsKey(instance.getInstanceId())) {
                            pendingInstances.remove(instance.getInstanceId());
                        }

                        instances.put(instance.getInstanceId(), server);
                        System.out.println("Found new server: " + instance.getInstanceId());
                        printInstanceIds();
                    } else if (instanceCode == PENDING_CODE) {
                        pendingInstances.put(instance.getInstanceId(), server);
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

            if (!foundInstance)
                instances.remove(instanceId);
        }

        // Take the running instances out of the pending list
        for (String instanceId : pendingInstances.keySet()) {
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

                            foundServer = true;
                            break;
                        }
                    }
                }

                if (foundServer)
                    break;
            }

        }
    }
}
