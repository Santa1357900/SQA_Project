package org.apache.commons.math3.distribution;

import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Assert;
import org.junit.Test;

public class FDistributionTest {

    @Test
    public void testConstructorsAndAccessors() throws Throwable {
        FDistribution dist1 = new FDistribution(2.0, 3.0);
        Assert.assertEquals(2.0, dist1.getNumeratorDegreesOfFreedom(), 1e-12);
        Assert.assertEquals(3.0, dist1.getDenominatorDegreesOfFreedom(), 1e-12);
        Assert.assertEquals(FDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY, dist1.getSolverAbsoluteAccuracy(), 1e-12);

        FDistribution dist2 = new FDistribution(2.0, 3.0, 1e-6);
        Assert.assertEquals(1e-6, dist2.getSolverAbsoluteAccuracy(), 1e-12);

        FDistribution dist3 = new FDistribution(new Well19937c(), 2.0, 3.0, 1e-5);
        Assert.assertEquals(1e-5, dist3.getSolverAbsoluteAccuracy(), 1e-12);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testNumeratorNotPositive() throws Throwable {
        new FDistribution(0.0, 2.0);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testNumeratorNegative() throws Throwable {
        new FDistribution(-1.0, 2.0);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testDenominatorNotPositive() throws Throwable {
        new FDistribution(2.0, 0.0);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testDenominatorNegative() throws Throwable {
        new FDistribution(2.0, -1.0);
    }

    @Test
    public void testDensity() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 5.0);
        double d = dist.density(1.0);
        Assert.assertTrue(d > 0.0);
    }

    @Test
    public void testCumulativeProbability() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 2.0);
        Assert.assertEquals(0.0, dist.cumulativeProbability(0.0), 1e-12);
        Assert.assertEquals(0.0, dist.cumulativeProbability(-1.0), 1e-12);
        
        double cp = dist.cumulativeProbability(1.0);
        Assert.assertTrue(cp > 0.0 && cp < 1.0);
    }

    @Test
    public void testNumericalMean() throws Throwable {
        // denominatorDF > 2 -> b / (b - 2)
        FDistribution dist1 = new FDistribution(2.0, 3.0);
        Assert.assertEquals(3.0 / (3.0 - 2.0), dist1.getNumericalMean(), 1e-12);

        // denominatorDF <= 2 -> NaN
        FDistribution dist2 = new FDistribution(2.0, 2.0);
        Assert.assertTrue(Double.isNaN(dist2.getNumericalMean()));
    }

    @Test
    public void testNumericalVariance() throws Throwable {
        // denominatorDF > 4 -> [2 * b^2 * (a + b - 2)] / [a * (b - 2)^2 * (b - 4)]
        FDistribution dist1 = new FDistribution(2.0, 5.0);
        double expectedVar = (2.0 * (5.0 * 5.0) * (2.0 + 5.0 - 2.0)) / (2.0 * ((5.0 - 2.0) * (5.0 - 2.0)) * (5.0 - 4.0));
        Assert.assertEquals(expectedVar, dist1.getNumericalVariance(), 1e-12);
        // Test caching branch
        Assert.assertEquals(expectedVar, dist1.getNumericalVariance(), 1e-12);

        // denominatorDF <= 4 -> NaN
        FDistribution dist2 = new FDistribution(2.0, 4.0);
        Assert.assertTrue(Double.isNaN(dist2.getNumericalVariance()));
    }

    @Test
    public void testSupport() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 2.0);
        Assert.assertEquals(0.0, dist.getSupportLowerBound(), 1e-12);
        Assert.assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), dist.getSupportUpperBound(), 1e-12);
        Assert.assertTrue(dist.isSupportLowerBoundInclusive());
        Assert.assertFalse(dist.isSupportUpperBoundInclusive());
        Assert.assertTrue(dist.isSupportConnected());
    }
}