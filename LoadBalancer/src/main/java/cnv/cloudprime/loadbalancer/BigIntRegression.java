package cnv.cloudprime.loadbalancer;

import java.math.BigInteger;

public class BigIntRegression {
    /** sum of x values */
    private BigInteger sumX = new BigInteger("0");

    /** total variation in x (sum of squared deviations from xbar) */
    private BigInteger sumXX = new BigInteger("0");

    /** sum of y values */
    private BigInteger sumY = new BigInteger("0");

    /** total variation in y (sum of squared deviations from ybar) */
    private BigInteger sumYY = new BigInteger("0");

    /** sum of products */
    private BigInteger sumXY = new BigInteger("0");

    /** number of observations */
    private BigInteger n = new BigInteger("0");

    /** mean of accumulated x values, used in updating formulas */
    private BigInteger xbar = new BigInteger("0");

    /** mean of accumulated y values, used in updating formulas */
    private BigInteger ybar = new BigInteger("0");

    /*
     *  Adds a data point to the model
     */
    public void addData(final BigInteger x, final BigInteger y) {
        if (n.equals(0)) {
            xbar = x;
            ybar = y;
        } else {
            final BigInteger fact1 = new BigInteger("1").add(n);
            final BigInteger fact2 = divideWithRound(n, new BigInteger("1").add(n));

            final BigInteger dx = x.subtract(xbar);
            final BigInteger dy = y.subtract(ybar);
            sumXX = sumXX.add(dx.multiply(dx).multiply(fact2));
            sumYY = sumYY.add(dy.multiply(dy).multiply(fact2));
            sumXY = sumXY.add(dx.multiply(dy).multiply(fact2));

            xbar = xbar.add(dx.divide(fact1));
            ybar = ybar.add(dy.divide(fact1));
        }
        sumX = sumX.add(x);
        sumY = sumY.add(y);
        n = n.add(new BigInteger("1"));

        //System.out.println("SumYY is " + sumYY + " and SumXX is " + sumXX);
    }

    /*
     *  Returns the slope (inclination) of the model's line
     */
    public BigInteger getSlope() {
        if (n.compareTo(new BigInteger("2")) == -1) {
            return new BigInteger("-1"); //not enough data
        }

        Integer compValue = 10 * new Integer((int) Double.MIN_VALUE);
        BigInteger comp = new BigInteger(compValue.toString());

        if (sumXX.abs().compareTo(comp) == -1) {
            return new BigInteger("-1"); //not enough variation in x
        }
        return sumXY.divide(sumXX);
    }

    /*
     *  Returns the prediction for a certain value (returns the y for a certain x)
     */
    public BigInteger predict(final BigInteger x) {
        final BigInteger b1 = getSlope();
        return getIntercept(b1).add(b1.multiply(x));
    }

    /*
     *  Returns the number of datapoints
     */
    public BigInteger getNumberOfPoints() {
        return n;
    }

    /*
     *  Returns the intercept of the slope
     */
    private BigInteger getIntercept(final BigInteger slope) {
        if (n.compareTo(new BigInteger("2")) == -1)
            return new BigInteger("-1");
        else
            return sumY.subtract(slope.multiply(sumX)).divide(n);
    }

    /*
     * Computes R-squared where R is Pearson's correlation coefficient
     */
    public double getRSquared() {
        BigInteger ssto = getTotalSumSquares();
        BigInteger[] parts = ssto.subtract(getSumSquaredErrors()).divideAndRemainder(ssto);
        return new Double(parts[0].toString() + "." + parts[1].toString());
    }

    /*
     * Returns the sum of squared deviations of the y values about their mean.
     */
    public BigInteger getTotalSumSquares() {
        if (n.compareTo(new BigInteger("2")) == -1) {
            return new BigInteger("-1");
        }
        return sumYY;
    }

    /*
     * Returns the sum of squared errors of the model
     */
    public BigInteger getSumSquaredErrors() {
        return BigInteger.ZERO.max(sumYY.subtract(divideWithRound(sumXY.multiply(sumXY), sumXX)));
    }

    /*
     *  This divides big integers always round to the nearest big int
     */
    public static BigInteger divideWithRound(BigInteger dividend, BigInteger divisor) {
        final BigInteger[] parts = dividend.divideAndRemainder(divisor);
        BigInteger result = parts[0];

        int compValue = dividend.compareTo(divisor.multiply(new BigInteger("2")));
        if (compValue == -1 || compValue == 0)
            result = result.add(new BigInteger("1"));
        //System.out.println("Dividend: "+dividend+"; Divisor: "+divisor+"; Result: "+result);
        return result;
    }

}
