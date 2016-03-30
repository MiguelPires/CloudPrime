package cnv.cloudprime.loadbalancer;

import java.util.Arrays;
import java.util.Date;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

public class AutoScaler
        implements Runnable {

    private AmazonCloudWatchClient cloudWatchClient;
    private InstanceManager instanceManager;
    private GetMetricStatisticsRequest metricsRequest;

    private static final int MIN_INSTANCES = 1;
    private static final int MAX_INSTANCES = 2;
    // this is measured in percentage
    private static final double MAX_AVG_CPU = 50;
    //  this is measured in milliseconds
    private static final int CHECK_PERIOD = 2500;

    public AutoScaler(InstanceManager manager) {
        instanceManager = manager;

        cloudWatchClient = new AmazonCloudWatchClient(
                new ProfileCredentialsProvider("credentials", "default").getCredentials())
                        //cloudWatchClient = new AmazonCloudWatchClient(new ProfileCredentialsProvider("default").getCredentials())
                        .withEndpoint("http://monitoring.eu-west-1.amazonaws.com");
        metricsRequest = new GetMetricStatisticsRequest().withMetricName("CPUUtilization")
                .withStatistics("Average")
                .withStartTime(new Date(new Date().getTime() - 1000 * 60 * 10)) //10 min ago
                .withEndTime(new Date()).withPeriod(60).withNamespace("AWS/EC2");
    }

    public void run() {
        try {
            Thread.sleep(CHECK_PERIOD);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
        while (true) {
            instanceManager.updateInstances();
            instanceManager.printInstanceIds();

            int clusterSize =
                instanceManager.runningInstances() + instanceManager.pendingInstances();
            System.out.println("Cluster size is " + clusterSize);

            if (clusterSize < MIN_INSTANCES) {
                instanceManager.increaseGroup(MIN_INSTANCES - clusterSize);
            } else if (clusterSize > MAX_INSTANCES) {
                instanceManager.decreaseGroup(clusterSize - MAX_INSTANCES);
            }

            for (WebServer server : instanceManager.getInstances()) {
                Dimension instanceDimension = new Dimension();
                instanceDimension.setName("InstanceId");
                instanceDimension.setValue(server.getInstance().getInstanceId());

                metricsRequest.setDimensions(Arrays.asList(instanceDimension));

                GetMetricStatisticsResult result =
                    cloudWatchClient.getMetricStatistics(metricsRequest);
                for (Datapoint point : result.getDatapoints()) {
                    System.out.println("CPU load: " + point.getAverage());

                    if (point.getAverage() > MAX_AVG_CPU && clusterSize < MAX_INSTANCES) {
                        instanceManager.increaseGroup(1);
                    }
                }
            }

            try {
                Thread.sleep(CHECK_PERIOD);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
