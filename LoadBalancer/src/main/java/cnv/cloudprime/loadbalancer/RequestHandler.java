package cnv.cloudprime.loadbalancer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class RequestHandler
        implements HttpHandler {

    private InstanceManager instanceManager;
    private AmazonS3Client s3Client;

    public RequestHandler(InstanceManager manager) {
        this.instanceManager = manager;
        AWSCredentials cred =
            new ProfileCredentialsProvider("credentials", "default").getCredentials();
        s3Client = new AmazonS3Client(cred);
    }

    public void handle(HttpExchange exchange) throws IOException {
        String response = "";
        BigInteger inputBigInt;
        WebServer server = null;
        long requestIndex = -1;
        File timeLog = new File("time.log");
        File RsquaredLog = new File("R.log");

        try {
            String path = exchange.getRequestURI().toString();

            // ignore other requests
            if (!path.startsWith("/f.html?n=")) {
                exchange.sendResponseHeaders(404, 0);
                return;
            }

            String inputNumber = exchange.getRequestURI().toString().replace("/f.html?n=", "");

            try {
                inputBigInt = new BigInteger(inputNumber);
            } catch (NumberFormatException e) {
                response = "This '" + inputNumber + "' is not a number";
                System.out.println(response);
                exchange.sendResponseHeaders(400, response.length());
                return;
            }

            RequestResult result = instanceManager.getServerCostBased(inputBigInt);

            if (result == null || !result.isResponseValid())
                throw new NoAvailableServerException(
                        "There is no instance available to serve that request");

            requestIndex = result.getRequestIndex();
            server = result.getServer();

            String serverIp = server.getInstance().getPublicIpAddress();

            URL url = new URL("http://" + serverIp + ":8000" + path);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(0);
            connection.setReadTimeout(0);

            // *** evaluation code ***
            long init = new Date().getTime();
            // read the response
            int responseCode = connection.getResponseCode();
            InputStream inputStream = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
            response += rd.readLine();

            long end = new Date().getTime();
            long delta = end - init;
            delta /= 1000; // elapsed time in seconds

            BufferedWriter bf = new BufferedWriter(new FileWriter(timeLog, true));
            PrintWriter writer = new PrintWriter(bf);
            writer.println(inputNumber + " , " + delta);
            writer.close();
            bf.close();

            String filename = "metrics-" + System.nanoTime();
            bf = new BufferedWriter(new FileWriter(filename));
            writer = new PrintWriter(bf);
            writer.println("Input:" + inputNumber);
            writer.println("Time:" + delta);
            writer.close();
            bf.close();

            File timeFile = new File(filename);
            s3Client.putObject(new PutObjectRequest("cloudprime-timing", filename, timeFile));
            timeFile.delete();

            if (instanceManager.costModel.getNumberOfPoints().compareTo(new BigInteger("2")) == 1) {
                bf = new BufferedWriter(new FileWriter(RsquaredLog, true));
                writer = new PrintWriter(bf);
                writer.println(inputNumber + " , " + instanceManager.costModel.getRSquared());
                writer.close();
                bf.close();
            }
            // **** end of evaluation code ****

            // forward the webserver's response
            exchange.sendResponseHeaders(responseCode, response.length());
            if (server != null && requestIndex != -1) {
                System.out.println("Removing request");
                System.out.println("Requests " + server.getRequests().toString());
                server.removeRequest(requestIndex);
                System.out.println("Requests " + server.getRequests().toString());
            } else {
                System.out.println("Couldn't remove request");
            }

            OutputStream outStream = exchange.getResponseBody();
            outStream.write(response.getBytes());
            outStream.close();

            exchange.close();

        } catch (NoAvailableServerException e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(404, e.getMessage().length());
            OutputStream outStream = exchange.getResponseBody();
            outStream.write(e.getMessage().getBytes());
            outStream.close();

            exchange.close();
        }
    }
}
