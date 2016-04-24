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

@SuppressWarnings("restriction")
public class RequestHandler
        implements HttpHandler {

    private InstanceManager instanceManager;

    public RequestHandler(InstanceManager manager) {
        this.instanceManager = manager;
    }

    public void handle(HttpExchange exchange) throws IOException {
        String response = "";
        BigInteger inputBigInt;
        WebServer server = null;
        long requestIndex = -1;

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
                inputBigInt = new BigInteger(inputNumber);
            } catch (NumberFormatException e) {
                response = "This '" + inputNumber + "' is not a number";
                System.out.println(response);
                exchange.sendResponseHeaders(400, response.length());
                return;
            }

            RequestResult result = instanceManager.getNextServer(inputBigInt);

            if (result == null || !result.isResponseValid())
                throw new NoAvailableServerException(
                        "There is no instance available to serve that request");

            requestIndex = result.getRequestIndex();
            server = result.getServer();

            String serverIp = server.getInstance().getPublicIpAddress();
            System.out.println(
                    "Forwarding request to server " + server.getInstance().getInstanceId());

            URL url = new URL("http://" + serverIp + ":8000" + path);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // read the response
            int responseCode = connection.getResponseCode();
            InputStream inputStream = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
            response += rd.readLine();

            // forward the webserver's response
            System.out.println("Forwarding response from server.");
            exchange.sendResponseHeaders(responseCode, response.length());
        } catch (Exception e) {
            e.printStackTrace();
            response = e.getMessage();
            exchange.sendResponseHeaders(404, response.length());
        } finally {
            OutputStream outStream = exchange.getResponseBody();
            outStream.write(response.getBytes());
            outStream.close();

            if (server != null && requestIndex != -1) {
                server.removeRequest(requestIndex);
            }
            exchange.close();
        }
    }
}
