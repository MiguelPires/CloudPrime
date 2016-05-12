package cnv.cloudprime.loadbalancer;

import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
public class LoadBalancer {

    public static void main(String[] args) throws Exception {
        String inAWS = args[0];

        HttpServer server = HttpServer.create(new InetSocketAddress(80), 0);
        InstanceManager manager = new InstanceManager(inAWS);

        RequestHandler reqHandler = new RequestHandler(manager);
        server.createContext("/", reqHandler);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Load Balancer running");

        AutoScaler autoscaler = new AutoScaler(manager);
        Thread thread = new Thread(autoscaler);
        thread.start();
        System.out.println("Auto scaler running");


        System.out.println("Ctrl-C to terminate load balancer");
        System.in.read();

        thread.interrupt();
    }
}
