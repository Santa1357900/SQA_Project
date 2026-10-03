package org.apache.commons.math.distribution;

import org.junit.Test;
import static org.junit.Assert.*;

public class NormalDistributionImplTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl();
        assertEquals(0.0, dist.getMean(), 1e-9);
        assertEquals(1.0, dist.getStandardDeviation(), 1e-9);
    }

    @Test
    public void testParameterizedConstructor() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(2.5, 1.5);
        assertEquals(2.5, dist.getMean(), 1e-9);
        assertEquals(1.5, dist.getStandardDeviation(), 1e-9);
    }

    @Test
    public void testSetMean() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl();
        dist.setMean(5.0);
        assertEquals(5.0, dist.getMean(), 1e-9);
    }

    @Test
    public void testSetStandardDeviationValid() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl();
        dist.setStandardDeviation(2.0);
        assertEquals(2.0, dist.getStandardDeviation(), 1e-9);
    }

    @Test
    public void testSetStandardDeviationInvalidZero() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl();
        try {
            dist.setStandardDeviation(0.0);
            fail("Expected IllegalArgumentException for zero standard deviation");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Standard deviation must be positive"));
        }
    }

    @Test
    public void testSetStandardDeviationInvalidNegative() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl();
        try {
            dist.setStandardDeviation(-1.5);
            fail("Expected IllegalArgumentException for negative standard deviation");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Standard deviation must be positive"));
        }
    }

    @Test
    public void testCumulativeProbabilityAtMean() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(0.0, 1.0);
        double cdf = dist.cumulativeProbability(0.0);
        assertEquals(0.5, cdf, 1e-9);
    }

    @Test
    public void testCumulativeProbabilityPositive() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(0.0, 1.0);
        double cdf = dist.cumulativeProbability(1.96);
        assertTrue(cdf > 0.95 && cdf < 0.98);
    }

    @Test
    public void testInverseCumulativeProbabilityZero() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl();
        double inv = dist.inverseCumulativeProbability(0.0);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), inv, 1e-9);
    }

    @Test
    public void testInverseCumulativeProbabilityOne() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl();
        double inv = dist.inverseCumulativeProbability(1.0);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), inv, 1e-9);
    }

    @Test
    public void testInverseCumulativeProbabilityMid() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(0.0, 1.0);
        double inv = dist.inverseCumulativeProbability(0.5);
        assertEquals(0.0, inv, 1e-4);
    }

    @Test
    public void testGetDomainLowerBound() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(10.0, 2.0);
        
        // p < 0.5
        double lower1 = dist.getDomainLowerBound(0.3);
        assertEquals(-Double.MAX_VALUE, lower1, 1e-9);

        // p >= 0.5
        double lower2 = dist.getDomainLowerBound(0.6);
        assertEquals(10.0, lower2, 1e-9);
    }

    @Test
    public void testGetDomainUpperBound() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(10.0, 2.0);
        
        // p < 0.5
        double upper1 = dist.getDomainUpperBound(0.3);
        assertEquals(10.0, upper1, 1e-9);

        // p >= 0.5
        double upper2 = dist.getDomainUpperBound(0.6);
        assertEquals(Double.MAX_VALUE, upper2, 1e-9);
    }

    @Test
    public void testGetInitialDomain() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(10.0, 2.0);

        // p < 0.5 -> mean - sd = 10.0 - 2.0 = 8.0
        double init1 = dist.getInitialDomain(0.3);
        assertEquals(8.0, init1, 1e-9);

        // p > 0.5 -> mean + sd = 10.0 + 2.0 = 12.0
        double init2 = dist.getInitialDomain(0.7);
        assertEquals(12.0, init2, 1e-9);

        // p == 0.5 -> mean = 10.0
        double init3 = dist.getInitialDomain(0.5);
        assertEquals(10.0, init3, 1e-9);
    }
}