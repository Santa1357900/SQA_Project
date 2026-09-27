package org.apache.commons.math.distribution;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.MathException;
import org.apache.commons.math.exception.NotStrictlyPositiveException;
import org.apache.commons.math.exception.OutOfRangeException;

public class NormalDistributionImplTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    public void testDefaultConstructor() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl();
        assertEquals(0.0, dist.getMean(), TOLERANCE);
        assertEquals(1.0, dist.getStandardDeviation(), TOLERANCE);
        assertEquals(NormalDistributionImpl.DEFAULT_INVERSE_ABSOLUTE_ACCURACY, dist.getSolverAbsoluteAccuracy(), TOLERANCE);
    }

    @Test
    public void testParameterizedConstructor() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(2.5, 1.5, 1e-6);
        assertEquals(2.5, dist.getMean(), TOLERANCE);
        assertEquals(1.5, dist.getStandardDeviation(), TOLERANCE);
        assertEquals(1e-6, dist.getSolverAbsoluteAccuracy(), TOLERANCE);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testConstructorWithZeroStandardDeviation() throws Throwable {
        new NormalDistributionImpl(0.0, 0.0);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testConstructorWithNegativeStandardDeviation() throws Throwable {
        new NormalDistributionImpl(0.0, -1.0);
    }

    @Test
    public void testDensity() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(0.0, 1.0);
        assertEquals(0.3989422804014327, dist.density(0.0), TOLERANCE);
        assertEquals(0.24197072451914337, dist.density(1.0), TOLERANCE);
        assertEquals(0.24197072451914337, dist.density(-1.0), TOLERANCE);
    }

    @Test
    public void testCumulativeProbability() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(0.0, 1.0);
        assertEquals(0.5, dist.cumulativeProbability(0.0), TOLERANCE);
        assertEquals(0.8413447460685429, dist.cumulativeProbability(1.0), TOLERANCE);
        assertEquals(0.15865525393145707, dist.cumulativeProbability(-1.0), TOLERANCE);
    }

    @Test
    public void testCumulativeProbabilityExtremeValues() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(0.0, 1.0);
        // Test values extremely far from mean to trigger boundary behavior if applicable
        assertEquals(0.0, dist.cumulativeProbability(-100.0), TOLERANCE);
        assertEquals(1.0, dist.cumulativeProbability(100.0), TOLERANCE);
    }

    @Test
    public void testInverseCumulativeProbabilityExtremes() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(0.0, 1.0);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), dist.inverseCumulativeProbability(0.0), TOLERANCE);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), dist.inverseCumulativeProbability(1.0), TOLERANCE);
    }

    @Test
    public void testInverseCumulativeProbabilityStandard() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(0.0, 1.0);
        assertEquals(0.0, dist.inverseCumulativeProbability(0.5), TOLERANCE);
        assertEquals(1.0, dist.inverseCumulativeProbability(0.8413447460685429), 1e-5);
    }

    @Test(expected = OutOfRangeException.class)
    public void testInverseCumulativeProbabilityInvalidLow() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(0.0, 1.0);
        dist.inverseCumulativeProbability(-0.1);
    }

    @Test(expected = OutOfRangeException.class)
    public void testInverseCumulativeProbabilityInvalidHigh() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(0.0, 1.0);
        dist.inverseCumulativeProbability(1.1);
    }

    @Test
    public void testDomainBounds() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(5.0, 2.0);
        
        // Access protected methods via subclass or direct invocation since test is in the same package
        assertEquals(-Double.MAX_VALUE, dist.getDomainLowerBound(0.1), TOLERANCE);
        assertEquals(5.0, dist.getDomainLowerBound(0.6), TOLERANCE);

        assertEquals(5.0, dist.getDomainUpperBound(0.1), TOLERANCE);
        assertEquals(Double.MAX_VALUE, dist.getDomainUpperBound(0.6), TOLERANCE);
    }

    @Test
    public void testInitialDomain() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(5.0, 2.0);

        assertEquals(3.0, dist.getInitialDomain(0.1), TOLERANCE); // mean - sd
        assertEquals(7.0, dist.getInitialDomain(0.9), TOLERANCE); // mean + sd
        assertEquals(5.0, dist.getInitialDomain(0.5), TOLERANCE); // mean
    }

    @Test
    public void testSample() throws Throwable {
        NormalDistributionImpl dist = new NormalDistributionImpl(0.0, 1.0);
        double sampleVal = dist.sample();
        // Just verify it doesn't throw and returns a finite value
        assertTrue(Double.isFinite(sampleVal));
    }
}