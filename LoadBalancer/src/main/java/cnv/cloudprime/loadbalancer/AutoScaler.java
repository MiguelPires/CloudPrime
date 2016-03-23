package cnv.cloudprime.loadbalancer;

import com.amazonaws.services.ec2.AmazonEC2;

public class AutoScaler
        implements Runnable {

    private InstanceManager instanceManager;

    public AutoScaler(InstanceManager manager) {
        instanceManager = manager;
    }

    public void run() {
        /*while (true) {

        }*/
    }
}
