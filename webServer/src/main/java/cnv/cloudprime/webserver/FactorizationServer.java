package cnv.cloudprime.webserver;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
public class FactorizationServer {
    public static void main(String[] args) throws Exception {
        
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        
        server.createContext("/", new RequestHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Server running");
        System.out.println("Ctrl-C to terminate server");
        System.in.read();
      }
}
