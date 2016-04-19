package cnv.cloudprime.loadbalancer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class RequestHandler
        implements HttpHandler {

    private InstanceManager instanceManager;

    public RequestHandler(InstanceManager manager) {
        this.instanceManager = manager;
    }

    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().toString();

            // ignore other requests
            if (!path.startsWith("/f.html?n=")) {
                exchange.sendResponseHeaders(404, 0);
                return;
            }

            String inputNumber = exchange.getRequestURI().toString().replace("/f.html?n=", "");
            System.out.println("Request: " + exchange.getRequestURI());

            try {
                new BigInteger(inputNumber);
            } catch (NumberFormatException e) {
                String response = "This '" + inputNumber + "' is not a number";
                System.out.println(response);
                exchange.sendResponseHeaders(400, response.length());
                OutputStream outStream = exchange.getResponseBody();
                outStream.write(response.getBytes());
                outStream.close();
                return;
            }

            WebServer server = instanceManager.getNextServer();
            if (server == null)
                return;
            
            String serverIp = server.getInstance().getPublicIpAddress();
            System.out.println(
                    "Forwarding request to server " + server.getInstance().getInstanceId());

            URL url = new URL("http://" + serverIp + ":8000" + path);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            String response = "";

            // read the response
            int responseCode = connection.getResponseCode();
            InputStream inputStream = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
            response += rd.readLine();

            // forward the webserver's response
            System.out.println("Forwarding response from server.");
            exchange.sendResponseHeaders(responseCode, response.length());
            OutputStream outStream = exchange.getResponseBody();
            outStream.write(response.getBytes());
            outStream.close();
        } catch (Exception e) {
            exchange.sendResponseHeaders(404, e.getMessage().length());
            OutputStream outStream = exchange.getResponseBody();
            outStream.write(e.getMessage().getBytes());
            outStream.close();
            System.out.println(e.getMessage());
        }
    }
}
