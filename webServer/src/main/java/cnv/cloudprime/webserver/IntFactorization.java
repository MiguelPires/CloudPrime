package cnv.cloudprime.webserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;

public class IntFactorization {

    private BigInteger zero = new BigInteger("0");
    private BigInteger one = new BigInteger("1");
    private BigInteger divisor = new BigInteger("2");
    private ArrayList<BigInteger> factors = new ArrayList<BigInteger>();
    private boolean firstIter = true;

    ArrayList<BigInteger> calcPrimeFactors(BigInteger num) throws FileNotFoundException {
        if (firstIter) {
            PrintWriter writer = new PrintWriter(new FileOutputStream(new File("fact.log"), true));
            long threadId = Thread.currentThread().getId();
            long millis = System.currentTimeMillis();

            writer.write("Thread: " + threadId + " - Time: " + millis + "\n");
            writer.close();
            firstIter = false;
        }

        if (num.compareTo(one) == 0) {
            return factors;
        }

        while (num.remainder(divisor).compareTo(zero) != 0) {
            divisor = divisor.add(one);
        }

        factors.add(divisor);
        return calcPrimeFactors(num.divide(divisor));
    }
}
