package cnv.cloudprime.webserver;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class RequestHandler
        implements HttpHandler {

    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().toString();
        System.out.println("PATH " + path);

        // ignore other requests
        if (!path.startsWith("/f.html?n=")) {
            exchange.sendResponseHeaders(404, 0);
            return;
        }

        String inputNumber = exchange.getRequestURI().toString().replace("/f.html?n=", "");

        // parse
        BigInteger bigInt;
        try {
            bigInt = new BigInteger(inputNumber);
        } catch (NumberFormatException e) {
            String response = "'" + inputNumber + "' is not a number";
            System.out.println(response);
            exchange.sendResponseHeaders(400, response.length());
            OutputStream outStream = exchange.getResponseBody();
            outStream.write(response.getBytes());
            outStream.close();
            return;
        }

        IntFactorization intFactorer = new IntFactorization();
        List<BigInteger> primeFactors = intFactorer.calcPrimeFactors(bigInt);

        // respond to request
        String response = primeFactors.toString().replaceAll("\\[|\\]|", "");
	System.out.println("Response: "+response);
        response += ".";
        exchange.sendResponseHeaders(200, response.length());
        OutputStream outStream = exchange.getResponseBody();
        outStream.write(response.getBytes());
        outStream.close();

        System.out.println("Responding to " + inputNumber + " with " + response);
    }
}
