package cnv.cloudprime.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;

public class MediumLoadClient {
    public static void main(String[] args) throws MalformedURLException, InterruptedException {
        Random rand = new Random(System.nanoTime());
        
        BigInteger firstPrime = BigInteger.probablePrime(25, rand);
        BigInteger secondPrime = BigInteger.probablePrime(20, rand);
        
        while (true) {
            
            BigInteger semiprime = firstPrime.multiply(secondPrime);
            try {
                final URL newUserUrl = new URL("http://localhost/f.html?n=" + semiprime.toString(10));

                System.out.println("Requesting");
                HttpURLConnection connection = (HttpURLConnection) newUserUrl.openConnection();
                int responseCode = connection.getResponseCode();

                InputStream inputStream = connection.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
                String line = rd.readLine();
                inputStream.close();

                if (responseCode != 200) {
                    System.out.println("Responded with '" + responseCode + "' to number " + semiprime.toString(10));
                    System.out.println("Message: " + line);
                    Thread.sleep(4000);
                } else {
                    System.out.println("Responded with '" + line + "'");
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Request failed");
                Thread.sleep(4000);

            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.sleep(4000);
            } finally {
                firstPrime = firstPrime.nextProbablePrime();
                secondPrime = secondPrime.nextProbablePrime();
            }
        }
    }
}
