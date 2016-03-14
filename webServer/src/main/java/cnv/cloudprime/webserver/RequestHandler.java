package cnv.cloudprime.webserver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.apache.commons.math3.primes.Primes;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;;

@SuppressWarnings("restriction")
public class RequestHandler
        implements HttpHandler {

    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().toString();

        // TODO: uncomment this. For testing purposes only
        if (/*exchange.getRequestMethod().equals("POST") && */path.contains("/factor/")) {
            String number = exchange.getRequestURI().toString().replace("/factor/", "");

            int semiPrime;
            try {
                semiPrime = Integer.parseInt(number);
            } catch (NumberFormatException e) {
                String response = "This '" + number + "' is not a number";
                System.out.println(response);

                exchange.sendResponseHeaders(400, 0);
                OutputStream outStream = exchange.getResponseBody();
                outStream.write(response.getBytes());
                outStream.close();
                return;
            }

            if (semiPrime < 2) {
                String response = "Must be a number bigger or equal to 2";
                System.out.println(response);

                exchange.sendResponseHeaders(400, response.length());
                OutputStream outStream = exchange.getResponseBody();
                outStream.write(response.getBytes());
                outStream.close();
                return;
            }

            List<Integer> primeFactors = Primes.primeFactors(semiPrime);

            // validate the results
            for (Integer factor : primeFactors) {
                if ((!Primes.isPrime(factor) && factor > 2) || primeFactors.size() != 2) {

                    String response = semiPrime + " is not a semiprime number";
                    System.out.println(response);

                    exchange.sendResponseHeaders(400, response.length());
                    OutputStream outStream = exchange.getResponseBody();
                    outStream.write(response.getBytes());
                    outStream.close();
                    return;
                }
            }

            // respond to request
            String response = primeFactors.toString().replaceAll("\\[|\\]|,", "");
            exchange.sendResponseHeaders(200, response.length());
            OutputStream outStream = exchange.getResponseBody();
            outStream.write(response.getBytes());
            outStream.close();

            System.out.println("Responding to "+number+" with " + response);
        }

    }

}
