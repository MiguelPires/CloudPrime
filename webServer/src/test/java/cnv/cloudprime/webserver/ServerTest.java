package cnv.cloudprime.webserver;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.primes.Primes;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
public class ServerTest {
    private static HttpServer server;

    @Before
    public void setUp() throws IOException {
        try {
            if (server == null) {
                server = HttpServer.create(new InetSocketAddress(80), 0);
                server.createContext("/", new RequestHandler());
                server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
                server.start();
            }
        } catch (Exception e) {
            return;
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (server != null)
            server.stop(0);
    }

    @Test
    public void success() throws Exception {
        URL newUserUrl = new URL("http://localhost/f.html?n=9");
        HttpURLConnection connection = (HttpURLConnection) newUserUrl.openConnection();

        int responseCode = connection.getResponseCode();
        assertTrue("Wrong response code '" + responseCode + "'. Should be 200",
                responseCode == 200);

        InputStream inputStream = connection.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
        String line = rd.readLine();
        assertTrue("Wrong prime factors: " + line, line.equals("3, 3."));
        inputStream.close();
    }

    @Test
    public void twoFactorsNonPrime() throws Exception {
        //  setUp();
        URL newUserUrl = new URL("http://localhost/f.html?n=8");
        HttpURLConnection connection = (HttpURLConnection) newUserUrl.openConnection();
        int responseCode = connection.getResponseCode();
        assertTrue("Wrong response code '" + responseCode + "'. Should be 200",
                responseCode == 200);

        InputStream inputStream = connection.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
        String line = rd.readLine();
        assertTrue("Wrong prime factors: " + line, line.equals("2, 2, 2."));
        inputStream.close();
    }

    @Test
    public void threeFactors() throws Exception {
        //     setUp();
        URL newUserUrl = new URL("http://localhost/f.html?n=27");
        HttpURLConnection connection = (HttpURLConnection) newUserUrl.openConnection();
        int responseCode = connection.getResponseCode();
        assertTrue("Wrong response code '" + responseCode + "'. Should be 200",
                responseCode == 200);

        InputStream inputStream = connection.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
        String line = rd.readLine();
        assertTrue("Wrong prime factors: " + line, line.equals("3, 3, 3."));
        inputStream.close();
    }

    @Test
    public void semiPrimes() throws Exception {
        //   setUp();
        final int inferiorLimit = 10;
        final int superiorLimit = 100;
        List<Thread> threads = new ArrayList<Thread>();

        for (int primeOne = inferiorLimit; primeOne < superiorLimit; primeOne =
            Primes.nextPrime(primeOne + 1)) {
            for (int primeTwo = inferiorLimit; primeTwo < superiorLimit; primeTwo =
                Primes.nextPrime(primeTwo + 1)) {
                final int semiPrime = primeOne * primeTwo;

                final URL newUserUrl = new URL("http://localhost/f.html?n=" + semiPrime);
                System.out.println("Testing with '" + newUserUrl + "'");

                try {
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

                                System.out.println("For semiprime " + semiPrime + " got " + line);
                                assertTrue("Wrong response code '" + responseCode
                                        + "'. Should be 200" + "\n" + line, responseCode == 200);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    thread.start();
                    threads.add(thread);
                } catch (Exception e) {
                    // The system probably ran out of memory...
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }

            }
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
