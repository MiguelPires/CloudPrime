package cnv.cloudprime.loadbalancer;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

public class AutoScaler
        implements Runnable {

    AmazonCloudWatchClient cloudWatchClient;
    private InstanceManager instanceManager;
    private GetMetricStatisticsRequest metricsRequest;

    private static final int MIN_INSTANCES = 1;
    private static final int MAX_INSTANCES = 2;
    // these are measured in percentage
    private static final float MAX_AVG_CPU = 50.0f;
    private static final float MIN_AVG_CPU = 5.0f;
    //  this is measured in milliseconds
    private static final int CHECK_PERIOD = 4000;
    // this is measured in seconds
    private static final int COOLDOWN_PERIOD = 180;

    private int lastScale;

    public AutoScaler(InstanceManager manager) {
        instanceManager = manager;

        cloudWatchClient = new AmazonCloudWatchClient(
                new ProfileCredentialsProvider("credentials", "default").getCredentials())
                        //cloudWatchClient = new AmazonCloudWatchClient(new ProfileCredentialsProvider("default").getCredentials())
                        .withEndpoint("http://monitoring.eu-west-1.amazonaws.com");
    }

    public void run() {
        while (true) {
            try {
                Thread.sleep(CHECK_PERIOD);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            instanceManager.updateInstances();
            instanceManager.printInstanceIds();

            System.out.println("Available replicas: " + instanceManager.runningInstances()
                    + "\nPending replicas: " + instanceManager.pendingInstances());
            int clusterSize =
                instanceManager.runningInstances() + instanceManager.pendingInstances();



            if (clusterSize < MIN_INSTANCES) {
                instanceManager.increaseGroup(MIN_INSTANCES - clusterSize);
            } else if (clusterSize > MAX_INSTANCES) {
                instanceManager.decreaseGroup(clusterSize - MAX_INSTANCES);
            }

            for (WebServer server : instanceManager.getInstances()) {
                Dimension instanceDimension = new Dimension();
                instanceDimension.setName("InstanceId");
                instanceDimension.setValue(server.getInstance().getInstanceId());

                metricsRequest = new GetMetricStatisticsRequest().withMetricName("CPUUtilization")
                        .withStatistics("Average")
                        .withStartTime(new Date(new Date().getTime() - 1000 * 60 * 10)) //10 min ago
                        .withEndTime(new Date()).withPeriod(60).withNamespace("AWS/EC2");
                metricsRequest.setDimensions(Arrays.asList(instanceDimension));

                GetMetricStatisticsResult result =
                    cloudWatchClient.getMetricStatistics(metricsRequest);
                List<Datapoint> dataPoints = result.getDatapoints();

                if (dataPoints != null && !dataPoints.isEmpty()) {
                    Collections.sort(dataPoints,
                            (a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

                    // for debug purposes
                    System.out.print("Points: ");
                    for (Datapoint point : dataPoints) {
                        System.out.print(" " + point.getAverage() + "%");
                    }
                    System.out.println("");

                    Datapoint latestPoint = dataPoints.get(dataPoints.size() - 1);

                    System.out.println("Current CPU load: " + latestPoint.getAverage() + "%");

                    // if the last scaling activity was too soon ago, we wait  
                    if (new Date().getTime() - lastScale <= 1000 * COOLDOWN_PERIOD) {
                        System.out.println("Cooldown period");
                        continue;
                    }
                    
                    if (latestPoint.getAverage() > MAX_AVG_CPU && clusterSize < MAX_INSTANCES) {
                        instanceManager.increaseGroup(1);
                    } else if (latestPoint.getAverage() < MIN_AVG_CPU
                            && clusterSize > MIN_INSTANCES) {
                        instanceManager.decreaseGroup(1);
                    }
                }
            }
        }
    }
}
