package org.apache.commons.math.distribution;

import junit.framework.TestCase;
import org.apache.commons.math.MathException;

public class FDistributionImplTest extends TestCase {

    private static final double DEFAULT_NUMERATOR_DOF = 2.0;
    private static final double DEFAULT_DENOMINATOR_DOF = 2.0;
    private static final double TOLERANCE = 1e-6;

    public void testConstructorAndGettersSetters() throws Throwable {
        FDistributionImpl dist = new FDistributionImpl(5.0, 10.0);
        assertEquals(5.0, dist.getNumeratorDegreesOfFreedom(), TOLERANCE);
        assertEquals(10.0, dist.getDenominatorDegreesOfFreedom(), TOLERANCE);

        dist.setNumeratorDegreesOfFreedom(3.0);
        dist.setDenominatorDegreesOfFreedom(4.0);
        assertEquals(3.0, dist.getNumeratorDegreesOfFreedom(), TOLERANCE);
        assertEquals(4.0, dist.getDenominatorDegreesOfFreedom(), TOLERANCE);
    }

    public void testInvalidNumeratorDegreesOfFreedom() throws Throwable {
        try {
            new FDistributionImpl(0.0, 2.0);
            fail("Expected IllegalArgumentException for zero numerator degrees of freedom");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            new FDistributionImpl(-1.0, 2.0);
            fail("Expected IllegalArgumentException for negative numerator degrees of freedom");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        FDistributionImpl dist = new FDistributionImpl(DEFAULT_NUMERATOR_DOF, DEFAULT_DENOMINATOR_DOF);
        try {
            dist.setNumeratorDegreesOfFreedom(0.0);
            fail("Expected IllegalArgumentException for setting zero numerator degrees of freedom");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            dist.setNumeratorDegreesOfFreedom(-2.5);
            fail("Expected IllegalArgumentException for setting negative numerator degrees of freedom");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    public void testInvalidDenominatorDegreesOfFreedom() throws Throwable {
        try {
            new FDistributionImpl(2.0, 0.0);
            fail("Expected IllegalArgumentException for zero denominator degrees of freedom");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            new FDistributionImpl(2.0, -1.0);
            fail("Expected IllegalArgumentException for negative denominator degrees of freedom");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        FDistributionImpl dist = new FDistributionImpl(DEFAULT_NUMERATOR_DOF, DEFAULT_DENOMINATOR_DOF);
        try {
            dist.setDenominatorDegreesOfFreedom(0.0);
            fail("Expected IllegalArgumentException for setting zero denominator degrees of freedom");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            dist.setDenominatorDegreesOfFreedom(-3.0);
            fail("Expected IllegalArgumentException for setting negative denominator degrees of freedom");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    public void testCumulativeProbability() throws Throwable {
        FDistributionImpl dist = new FDistributionImpl(DEFAULT_NUMERATOR_DOF, DEFAULT_DENOMINATOR_DOF);

        // x <= 0 should return 0.0
        assertEquals(0.0, dist.cumulativeProbability(0.0), TOLERANCE);
        assertEquals(0.0, dist.cumulativeProbability(-1.0), TOLERANCE);
        assertEquals(0.0, dist.cumulativeProbability(-100.5), TOLERANCE);

        // x > 0 should compute regularized beta
        double prob = dist.cumulativeProbability(1.0);
        assertTrue(prob >= 0.0 && prob <= 1.0);
    }

    public void testInverseCumulativeProbability() throws Throwable {
        FDistributionImpl dist = new FDistributionImpl(DEFAULT_NUMERATOR_DOF, DEFAULT_DENOMINATOR_DOF);

        assertEquals(0.0, dist.inverseCumulativeProbability(0.0), TOLERANCE);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), dist.inverseCumulativeProbability(1.0), TOLERANCE);

        double inv = dist.inverseCumulativeProbability(0.5);
        assertTrue(inv > 0.0);
    }

    public void testDomainBoundsAndInitialDomain() throws Throwable {
        // Access protected methods via subclass or direct invocation since test is in the same package
        FDistributionImpl dist = new FDistributionImpl(5.0, 10.0);

        assertEquals(0.0, dist.getDomainLowerBound(0.5), TOLERANCE);
        assertEquals(Double.MAX_VALUE, dist.getDomainUpperBound(0.5), TOLERANCE);
        
        // Initial domain based on denominator d: d / (d - 2.0) -> 10.0 / (10.0 - 2.0) = 1.25
        assertEquals(1.25, dist.getInitialDomain(0.5), TOLERANCE);
    }
}