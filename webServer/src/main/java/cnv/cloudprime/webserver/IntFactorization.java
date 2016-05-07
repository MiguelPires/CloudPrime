package cnv.cloudprime.webserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;

public class IntFactorization {
    static final String LOG_FILENAME = "metrics";

    private BigInteger zero = new BigInteger("0");
    private BigInteger one = new BigInteger("1");
    private BigInteger divisor = new BigInteger("2");
    private ArrayList<BigInteger> factors = new ArrayList<BigInteger>();

    private boolean written = false;
    private BigInteger firstNum = null;

    ArrayList<BigInteger> calcPrimeFactors(BigInteger num) throws FileNotFoundException {
        if (firstNum == null) {
            firstNum = num;
        }

        if (num.compareTo(one) == 0) {
            if (!written) {
                write(firstNum);
            }

            return factors;
        }

        while (num.remainder(divisor).compareTo(zero) != 0) {
            divisor = divisor.add(one);
        }

        factors.add(divisor);
        return calcPrimeFactors(num.divide(divisor));
    }

    private static void write(BigInteger num) throws FileNotFoundException {
        PrintWriter writer = new PrintWriter(new FileOutputStream(
                new File(LOG_FILENAME + "-" + Thread.currentThread().getId() + ".log"), true));

        writer.write("Input:" + num.toString() + "\n");
        writer.close();

    }
}
