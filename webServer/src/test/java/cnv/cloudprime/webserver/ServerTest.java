package cnv.cloudprime.webserver;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import org.apache.commons.math3.primes.Primes;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;
import org.apache.commons.math3.primes.Primes;

@SuppressWarnings("restriction")
public class ServerTest {
    private HttpServer server;

    public void setUp() throws IOException {
        try {
            if (server == null) {
                server = HttpServer.create(new InetSocketAddress(8000), 0);
                server.createContext("/factor", new RequestHandler());
                server.setExecutor(null); // creates a default executor
                server.start();
            }
        } catch (Exception e) {
            return;
        }

    }

    @Test
    public void success() throws Exception {
        setUp();
        URL newUserUrl = new URL("http://localhost:8000/factor/9");

        HttpURLConnection connection = (HttpURLConnection) newUserUrl.openConnection();

        int responseCode = connection.getResponseCode();
        assertTrue("Wrong response code '" + responseCode + "'. Should be 200",
                responseCode == 200);

        InputStream inputStream = connection.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
        String line = rd.readLine();
        assertTrue("Wrong prime factors: " + line, line.equals("3 3"));
        inputStream.close();
    }

    @Test
    public void failureTwoFactorsNonPrime() throws Exception {
        setUp();
        URL newUserUrl = new URL("http://localhost:8000/factor/8");
        HttpURLConnection connection = (HttpURLConnection) newUserUrl.openConnection();
        int responseCode = connection.getResponseCode();
        assertTrue("Wrong response code '" + responseCode + "'. Should be 400",
                responseCode == 400);
    }

    @Test
    public void failureThreeFactors() throws Exception {
        setUp();
        URL newUserUrl = new URL("http://localhost:8000/factor/27");
        HttpURLConnection connection = (HttpURLConnection) newUserUrl.openConnection();
        int responseCode = connection.getResponseCode();
        assertTrue("Wrong response code '" + responseCode + "'. Should be 400",
                responseCode == 400);
    }

    @Test
    public void loadTest() throws Exception {
        setUp();
        final int limit = 100;

        for (int primeOne = 2; primeOne < limit; primeOne = Primes.nextPrime(primeOne + 1)) {
            for (int primeTwo = 2; primeTwo < limit; primeTwo = Primes.nextPrime(primeTwo + 1)) {
                int semiPrime = primeOne * primeTwo;

                final URL newUserUrl = new URL("http://localhost:8000/factor/" + semiPrime);

                Thread thread = new Thread() {
                    public void run() {
                        try {
                            HttpURLConnection connection =
                                (HttpURLConnection) newUserUrl.openConnection();
                            int responseCode = connection.getResponseCode();

                            InputStream inputStream = connection.getInputStream();
                            BufferedReader rd =
                                new BufferedReader(new InputStreamReader(inputStream));
                            String line = rd.readLine();
                            inputStream.close();

                            assertTrue("Wrong response code '" + responseCode + "'. Should be 200"
                                    + "\n" + line, responseCode == 200);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
                thread.start();

            }
        }

    }
}
