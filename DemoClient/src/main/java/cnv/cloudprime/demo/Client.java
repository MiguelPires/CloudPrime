package cnv.cloudprime.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.math3.primes.Primes;

public class Client {
    final static int LIMIT = 100;

    public static void main(String[] args) throws MalformedURLException {

        long startTime = System.nanoTime();

        for (int primeOne = 2; primeOne < LIMIT; primeOne = Primes.nextPrime(primeOne + 1)) {
            for (int primeTwo = 2; primeTwo < LIMIT; primeTwo = Primes.nextPrime(primeTwo + 1)) {
                final int semiPrime = primeOne * primeTwo;

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

                            if (responseCode != 200){
                                System.out.println("Responded with '"+responseCode+"' to semiprime "+semiPrime);
                                System.out.println("Message: "+line);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            System.out.println("Request failed");
                        }
                    }
                };
                thread.start();
            }
        }
        long endTime = System.nanoTime();
        long duration = (endTime - startTime)/1000000 ;
        System.out.println("Execution time (ms): "+duration);
    }
}
