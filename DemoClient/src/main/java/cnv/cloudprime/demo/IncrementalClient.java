package cnv.cloudprime.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.math3.primes.Primes;

public class IncrementalClient {
    final static int UPPER_LIMIT = 100000000;
    final static int LOWER_LIMIT = 1000;

    public static void main(String[] args) throws MalformedURLException {

        long startTime = System.nanoTime();

        for (int primeOne = LOWER_LIMIT; primeOne < UPPER_LIMIT; primeOne =
            Primes.nextPrime(primeOne + 1)) {
            for (int primeTwo = LOWER_LIMIT; primeTwo < UPPER_LIMIT; primeTwo =
                Primes.nextPrime((primeTwo + 1))) {
                BigInteger semiPrime =
                    BigInteger.valueOf(primeOne).multiply(BigInteger.valueOf(primeTwo));

                final URL newUserUrl =
                    new URL("localhost/f.html?n="
                            + semiPrime.toString(10));

                /* Thread thread = new Thread() {
                    public void run() {*/
                try {
                    HttpURLConnection connection = (HttpURLConnection) newUserUrl.openConnection();
                    int responseCode = connection.getResponseCode();

                    InputStream inputStream = connection.getInputStream();
                    BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
                    String line = rd.readLine();
                    inputStream.close();

                    if (responseCode != 200) {
                        System.out.println(
                                "Responded with '" + responseCode + "' to semiprime " + semiPrime);
                        System.out.println("Message: " + line);
                    } else {
                        System.out.println("Responded with '" + line + "'");

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("Request failed");
                }
                /*     }
                };
                thread.start();*/
            }
        }
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        System.out.println("Execution time (ms): " + duration);
    }
}
