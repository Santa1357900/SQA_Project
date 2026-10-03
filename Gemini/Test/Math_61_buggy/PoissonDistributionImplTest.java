package org.apache.commons.math.distribution;

import org.junit.Test;
import static org.junit.Assert.*;

public class PoissonDistributionImplTest {

    @Test
    public void testConstructors() throws Throwable {
        PoissonDistributionImpl dist1 = new PoissonDistributionImpl(2.5);
        assertEquals(2.5, dist1.getMean(), 1e-12);
        assertEquals(PoissonDistributionImpl.DEFAULT_EPSILON, 1e-12, 1e-15);

        PoissonDistributionImpl dist2 = new PoissonDistributionImpl(3.0, 1e-10);
        assertEquals(3.0, dist2.getMean(), 1e-12);

        PoissonDistributionImpl dist3 = new PoissonDistributionImpl(4.0, 500000);
        assertEquals(4.0, dist3.getMean(), 1e-12);

        PoissonDistributionImpl dist4 = new PoissonDistributionImpl(5.0, 1e-9, 200000);
        assertEquals(5.0, dist4.getMean(), 1e-12);
    }

    @Test
    public void testInvalidMean() throws Throwable {
        try {
            new PoissonDistributionImpl(0.0);
            fail("Expected IllegalArgumentException for mean = 0");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            new PoissonDistributionImpl(-1.5);
            fail("Expected IllegalArgumentException for negative mean");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testProbability() throws Throwable {
        PoissonDistributionImpl dist = new PoissonDistributionImpl(1.0);
        
        // x < 0
        assertEquals(0.0, dist.probability(-1), 1e-12);
        assertEquals(0.0, dist.probability(-100), 1e-12);

        // x = Integer.MAX_VALUE
        assertEquals(0.0, dist.probability(Integer.MAX_VALUE), 1e-12);

        // x = 0
        assertEquals(Math.exp(-1.0), dist.probability(0), 1e-12);

        // x > 0 (Stirling / deviance branch)
        double p1 = dist.probability(1);
        assertTrue(p1 > 0.0);

        PoissonDistributionImpl distLarge = new PoissonDistributionImpl(10.0);
        double p10 = distLarge.probability(10);
        assertTrue(p10 > 0.0);
    }

    @Test
    public void testCumulativeProbability() throws Throwable {
        PoissonDistributionImpl dist = new PoissonDistributionImpl(1.0);

        // x < 0
        assertEquals(0.0, dist.cumulativeProbability(-1), 1e-12);
        assertEquals(0.0, dist.cumulativeProbability(-5), 1e-12);

        // x = Integer.MAX_VALUE
        assertEquals(1.0, dist.cumulativeProbability(Integer.MAX_VALUE), 1e-12);

        // normal range
        double cp0 = dist.cumulativeProbability(0);
        assertTrue(cp0 >= 0.0 && cp0 <= 1.0);

        double cp5 = dist.cumulativeProbability(5);
        assertTrue(cp5 > cp0);
        assertEquals(1.0, dist.cumulativeProbability(50), 1e-12);
    }

    @Test
    public void testNormalApproximateProbability() throws Throwable {
        PoissonDistributionImpl dist = new PoissonDistributionImpl(10.0);
        double approx = dist.normalApproximateProbability(10);
        assertTrue(approx >= 0.0 && approx <= 1.0);
    }

    @Test
    public void testSample() throws Throwable {
        PoissonDistributionImpl distSmall = new PoissonDistributionImpl(0.5);
        int sampleSmall = distSmall.sample();
        assertTrue(sampleSmall >= 0);

        PoissonDistributionImpl distLarge = new PoissonDistributionImpl(100.0);
        int sampleLarge = distLarge.sample();
        assertTrue(sampleLarge >= 0);
    }

    @Test
    public void testDomainBounds() throws Throwable {
        PoissonDistributionImpl dist = new PoissonDistributionImpl(2.0);
        assertEquals(0, dist.getDomainLowerBound(0.5));
        assertEquals(Integer.MAX_VALUE, dist.getDomainUpperBound(0.5));
    }
}