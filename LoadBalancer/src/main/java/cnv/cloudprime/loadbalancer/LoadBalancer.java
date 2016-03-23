package cnv.cloudprime.loadbalancer;
import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

public class LoadBalancer {

	@SuppressWarnings("restriction")
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

		server.createContext("/factor", new RequestHandler());
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.start();
		System.out.println("Load Balancer running");
		System.out.println("Ctrl-C to terminate load balancer");
		System.in.read(); 
	}

}
