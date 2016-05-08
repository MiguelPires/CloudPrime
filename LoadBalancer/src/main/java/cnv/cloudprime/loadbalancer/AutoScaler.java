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

    // *** System Parameters ***
    private static final int MIN_INSTANCES = 1;
    private static final int MAX_INSTANCES = 5;
    // these are measured in percentage
    private static final float MAX_CLUSTER_LOAD = 80.0f;
    private static final float MIN_CLUSTER_LOAD = 30.0f;
    //  this is measured in milliseconds
    private static final int CHECK_PERIOD = 4000;
    // this is measured in seconds
    private static final int COOLDOWN_PERIOD = 180;
    // how many minutes are going into the CPU load average
    private static final int ANALYSIS_TIME_WINDOW = 1;
    // how long ago did we scale - measured in millis
    private Long lastScale = null;

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

            System.out.println("###\tSTATUS   ###");
            instanceManager.updateInstances();
            instanceManager.printInstanceIds();

            System.out.println("#\tAvailable replicas: " + instanceManager.runningInstances()
                    + "\n#\tPending replicas: " + instanceManager.pendingInstances());
            System.out.println();
            int clusterSize =
                instanceManager.runningInstances() + instanceManager.pendingInstances();

            if (clusterSize < MIN_INSTANCES) {
                instanceManager.increaseGroup(MIN_INSTANCES - clusterSize);
            } else if (clusterSize > MAX_INSTANCES) {
                instanceManager.decreaseGroup(clusterSize - MAX_INSTANCES);
            }

            float serversAvgLoad = 0f;

            for (WebServer server : instanceManager.getInstances()) {
                Dimension instanceDimension = new Dimension();
                instanceDimension.setName("InstanceId");
                instanceDimension.setValue(server.getInstance().getInstanceId());

                metricsRequest = new GetMetricStatisticsRequest().withMetricName("CPUUtilization")
                        .withStatistics("Average")
                        .withStartTime(new Date(new Date().getTime() - 1000 * 60 * 8)) //8 min ago
                        .withEndTime(new Date()).withPeriod(60).withNamespace("AWS/EC2");
                metricsRequest.setDimensions(Arrays.asList(instanceDimension));

                GetMetricStatisticsResult result =
                    cloudWatchClient.getMetricStatistics(metricsRequest);
                List<Datapoint> dataPoints = result.getDatapoints();

                if (dataPoints != null && !dataPoints.isEmpty()) {
                    Collections.sort(dataPoints,
                            (a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

                    // for debug purposes
                    System.out.print("#\tPoints: ");
                    for (Datapoint point : dataPoints) {
                        System.out.print(" " + point.getAverage() + "%");
                    }
                    System.out.print("\n");

                    float cummulativeLoad = 0f;
                    int bottomMinute = dataPoints.size() > ANALYSIS_TIME_WINDOW
                            ? dataPoints.size() - ANALYSIS_TIME_WINDOW : 0;

                    for (int i = dataPoints.size() - 1; i >= bottomMinute; --i) {
                        cummulativeLoad += dataPoints.get(i).getAverage();
                    }

                    float averageLoad;
                    if (dataPoints.size() > ANALYSIS_TIME_WINDOW) {
                        averageLoad = cummulativeLoad / (float) ANALYSIS_TIME_WINDOW;
                    } else {
                        averageLoad = cummulativeLoad / (float) dataPoints.size();
                    }

                    serversAvgLoad += averageLoad;
                    System.out.println(
                            "#\tAverage CPU load (last " + (dataPoints.size() - bottomMinute)
                                    + " minutes): " + averageLoad + "%");
                }
            }

            // if some server has no datapoints it's still included in the average
            // as if it has 0% load (probably accurate since the server is in it's grace period)
            if (instanceManager.getInstances().size() != 0) {
                serversAvgLoad /= instanceManager.getInstances().size();

                System.out.println("#\tCluster load: " + serversAvgLoad + "%");

            }
            // if the last scaling activity was too soon ago, we wait  
            if (lastScale != null && new Date().getTime() - lastScale <= 1000 * COOLDOWN_PERIOD) {
                System.out.println("#\tCooldown period");
                System.out.println("########");
                continue;
            }

            if (serversAvgLoad > MAX_CLUSTER_LOAD && clusterSize < MAX_INSTANCES) {
                instanceManager.increaseGroup(1);
                lastScale = new Date().getTime();
            } else if (serversAvgLoad < MIN_CLUSTER_LOAD && clusterSize > MIN_INSTANCES) {
                instanceManager.decreaseGroup(1);
                lastScale = new Date().getTime();
            }

            System.out.println("########");
        }
    }
}
