package org.apache.commons.math3.distribution;

import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Assert;
import org.junit.Test;

public class HypergeometricDistributionTest {

    @Test(expected = NotStrictlyPositiveException.class)
    public void testConstructorPopulationSizeZero() throws Throwable {
        new HypergeometricDistribution(0, 5, 2);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testConstructorPopulationSizeNegative() throws Throwable {
        new HypergeometricDistribution(-10, 5, 2);
    }

    @Test(expected = NotPositiveException.class)
    public void testConstructorNumberOfSuccessesNegative() throws Throwable {
        new HypergeometricDistribution(10, -1, 2);
    }

    @Test(expected = NotPositiveException.class)
    public void testConstructorSampleSizeNegative() throws Throwable {
        new HypergeometricDistribution(10, 5, -1);
    }

    @Test(expected = NumberIsTooLargeException.class)
    public void testConstructorSuccessesExceedPopulation() throws Throwable {
        new HypergeometricDistribution(10, 11, 2);
    }

    @Test(expected = NumberIsTooLargeException.class)
    public void testConstructorSampleExceedsPopulation() throws Throwable {
        new HypergeometricDistribution(10, 5, 11);
    }

    @Test
    public void testAccessorsAndBasicProperties() throws Throwable {
        HypergeometricDistribution dist = new HypergeometricDistribution(new Well19937c(), 20, 7, 5);
        Assert.assertEquals(20, dist.getPopulationSize());
        Assert.assertEquals(7, dist.getNumberOfSuccesses());
        Assert.assertEquals(5, dist.getSampleSize());
        Assert.assertTrue(dist.isSupportConnected());
        
        // Test lower and upper bounds of support: max(0, n + m - N) => max(0, 5 + 7 - 20) = 0
        // min(m, n) => min(7, 5) = 5
        Assert.assertEquals(0, dist.getSupportLowerBound());
        Assert.assertEquals(5, dist.getSupportUpperBound());
        
        // Mean = n * m / N = 5 * 7 / 20 = 1.75
        Assert.assertEquals(1.75, dist.getNumericalMean(), 1e-9);
        
        // Variance
        double var1 = dist.getNumericalVariance();
        double var2 = dist.getNumericalVariance(); // Test cache path
        Assert.assertEquals(var1, var2, 1e-9);
    }

    @Test
    public void testProbabilityValues() throws Throwable {
        HypergeometricDistribution dist = new HypergeometricDistribution(10, 5, 5);
        
        // Out of domain probability
        Assert.assertEquals(0.0, dist.probability(-1), 1e-9);
        Assert.assertEquals(0.0, dist.probability(6), 1e-9);
        
        // Within domain probability
        double p = dist.probability(2);
        Assert.assertTrue(p >= 0.0);
        Assert.assertTrue(p <= 1.0);
    }

    @Test
    public void testCumulativeProbabilities() throws Throwable {
        HypergeometricDistribution dist = new HypergeometricDistribution(10, 5, 5);
        
        // x below domain lower bound
        Assert.assertEquals(0.0, dist.cumulativeProbability(-5), 1e-9);
        
        // x above or equal to domain upper bound
        Assert.assertEquals(1.0, dist.cumulativeProbability(10), 1e-9);
        
        // x within domain
        double cdf = dist.cumulativeProbability(2);
        Assert.assertTrue(cdf >= 0.0);
        Assert.assertTrue(cdf <= 1.0);
    }

    @Test
    public void testUpperCumulativeProbabilities() throws Throwable {
        HypergeometricDistribution dist = new HypergeometricDistribution(10, 5, 5);
        
        // x <= domain lower bound
        Assert.assertEquals(1.0, dist.upperCumulativeProbability(-1), 1e-9);
        
        // x > domain upper bound
        Assert.assertEquals(0.0, dist.upperCumulativeProbability(10), 1e-9);
        
        // x within domain
        double upperCdf = dist.upperCumulativeProbability(2);
        Assert.assertTrue(upperCdf >= 0.0);
        Assert.assertTrue(upperCdf <= 1.0);
    }
}