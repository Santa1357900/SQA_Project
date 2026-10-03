package org.apache.commons.math.distribution;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class NormalDistributionImplClaudeTest {

    private NormalDistributionImpl standardNormal;

    @Before
    public void setUp() throws Throwable {
        standardNormal = new NormalDistributionImpl();
    }

    // default constructor: mean=0, sd=1 per javadoc
    @Test
    public void testDefaultConstructor_returnsMeanZeroSdOne() throws Throwable {
        assertEquals(0.0, standardNormal.getMean(), 1e-12);
        assertEquals(1.0, standardNormal.getStandardDeviation(), 1e-12);
    }

    // 2-arg constructor stores mean and sd via setters
    @Test
    public void testTwoArgConstructor_storesMeanAndSd() throws Throwable {
        NormalDistributionImpl d = new NormalDistributionImpl(3.5, 2.0);
        assertEquals(3.5, d.getMean(), 1e-12);
        assertEquals(2.0, d.getStandardDeviation(), 1e-12);
    }

    // constructor delegates to setStandardDeviation, so sd<=0 must throw
    @Test
    public void testConstructor_zeroSd_throwsIllegalArgumentException() throws Throwable {
        try {
            new NormalDistributionImpl(0.0, 0.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // constructor delegates to setStandardDeviation, so negative sd must throw
    @Test
    public void testConstructor_negativeSd_throwsIllegalArgumentException() throws Throwable {
        try {
            new NormalDistributionImpl(0.0, -1.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // setMean updates mean field, getMean returns the new value
    @Test
    public void testSetMean_thenGetMean_returnsUpdatedValue() throws Throwable {
        standardNormal.setMean(-7.25);
        assertEquals(-7.25, standardNormal.getMean(), 1e-12);
    }

    // setStandardDeviation with positive value succeeds
    @Test
    public void testSetStandardDeviation_positive_updatesValue() throws Throwable {
        standardNormal.setStandardDeviation(4.2);
        assertEquals(4.2, standardNormal.getStandardDeviation(), 1e-12);
    }

    // boundary: sd == 0.0 must throw per javadoc "must be positive"
    @Test
    public void testSetStandardDeviation_zero_throwsIllegalArgumentException() throws Throwable {
        try {
            standardNormal.setStandardDeviation(0.0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // negative sd must throw, and original value must remain unchanged
    @Test
    public void testSetStandardDeviation_negative_throwsAndLeavesOldValue() throws Throwable {
        try {
            standardNormal.setStandardDeviation(-0.5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        assertEquals(1.0, standardNormal.getStandardDeviation(), 1e-12);
    }

    // cumulativeProbability(mean) must equal 0.5 since erf(0)=0
    @Test
    public void testCumulativeProbability_atMean_returnsOneHalf() throws Throwable {
        double p = standardNormal.cumulativeProbability(0.0);
        assertEquals(0.5, p, 1e-9);
    }

    // cumulativeProbability above the mean must exceed 0.5
    @Test
    public void testCumulativeProbability_aboveMean_greaterThanHalf() throws Throwable {
        double p = standardNormal.cumulativeProbability(1.0);
        assertTrue(p > 0.5);
        assertTrue(p < 1.0);
    }

    // cumulativeProbability below the mean must be less than 0.5
    @Test
    public void testCumulativeProbability_belowMean_lessThanHalf() throws Throwable {
        double p = standardNormal.cumulativeProbability(-1.0);
        assertTrue(p < 0.5);
        assertTrue(p > 0.0);
    }

    // symmetry of normal CDF: F(-x) + F(x) == 1 for standard normal
    @Test
    public void testCumulativeProbability_symmetricAroundMean_sumsToOne() throws Throwable {
        double lower = standardNormal.cumulativeProbability(-2.0);
        double upper = standardNormal.cumulativeProbability(2.0);
        assertEquals(1.0, lower + upper, 1e-6);
    }

    // bug oracle: javadoc guarantees result 0 or 1 (not an exception) for x far
    // beyond 20 standard deviations from the mean; buggy code lacks the catch
    // for the convergence failure and propagates MaxIterationsExceededException
    @Test
    public void testCumulativeProbability_extremeHighX_returnsNearOneNoException() throws Throwable {
        double p = standardNormal.cumulativeProbability(1.0E9);
        assertEquals(1.0, p, 1e-5);
    }

    // bug oracle, symmetric case: extreme low x must return near 0, not throw
    @Test
    public void testCumulativeProbability_extremeLowX_returnsNearZeroNoException() throws Throwable {
        double p = standardNormal.cumulativeProbability(-1.0E9);
        assertEquals(0.0, p, 1e-5);
    }

    // non-standard distribution: shifting mean/sd still yields 0.5 at the mean
    @Test
    public void testCumulativeProbability_nonStandardDistribution_atMeanIsHalf() throws Throwable {
        NormalDistributionImpl d = new NormalDistributionImpl(10.0, 3.0);
        double p = d.cumulativeProbability(10.0);
        assertEquals(0.5, p, 1e-9);
    }

    // inverseCumulativeProbability(0) must short-circuit to negative infinity
    @Test
    public void testInverseCumulativeProbability_pZero_returnsNegativeInfinity() throws Throwable {
        double x = standardNormal.inverseCumulativeProbability(0.0);
        assertTrue(Double.isInfinite(x));
        assertTrue(x < 0.0);
    }

    // inverseCumulativeProbability(1) must short-circuit to positive infinity
    @Test
    public void testInverseCumulativeProbability_pOne_returnsPositiveInfinity() throws Throwable {
        double x = standardNormal.inverseCumulativeProbability(1.0);
        assertTrue(Double.isInfinite(x));
        assertTrue(x > 0.0);
    }

    // inverseCumulativeProbability(0.5) must return the mean (symmetric point)
    @Test
    public void testInverseCumulativeProbability_pHalf_returnsMean() throws Throwable {
        double x = standardNormal.inverseCumulativeProbability(0.5);
        assertEquals(0.0, x, 1e-4);
    }

    // round trip check: CDF(inverseCDF(p)) should return back approximately p
    @Test
    public void testInverseCumulativeProbability_roundTrip_matchesOriginalP() throws Throwable {
        double x = standardNormal.inverseCumulativeProbability(0.75);
        double p = standardNormal.cumulativeProbability(x);
        assertEquals(0.75, p, 1e-4);
    }

    // inverseCumulativeProbability(0.5) on shifted distribution returns its mean
    @Test
    public void testInverseCumulativeProbability_nonStandardDistribution_pHalfReturnsMean() throws Throwable {
        NormalDistributionImpl d = new NormalDistributionImpl(5.0, 2.0);
        double x = d.inverseCumulativeProbability(0.5);
        assertEquals(5.0, x, 1e-4);
    }

    // getDomainLowerBound: p < 0.5 branch returns -Double.MAX_VALUE
    @Test
    public void testGetDomainLowerBound_pLessThanHalf_returnsNegativeMaxValue() throws Throwable {
        double ret = standardNormal.getDomainLowerBound(0.25);
        assertEquals(-Double.MAX_VALUE, ret, 0.0);
    }

    // getDomainLowerBound: boundary p == 0.5 falls into else branch -> mean
    @Test
    public void testGetDomainLowerBound_pEqualsHalf_returnsMean() throws Throwable {
        standardNormal.setMean(2.0);
        double ret = standardNormal.getDomainLowerBound(0.5);
        assertEquals(2.0, ret, 1e-12);
    }

    // getDomainLowerBound: p > 0.5 branch returns the mean
    @Test
    public void testGetDomainLowerBound_pGreaterThanHalf_returnsMean() throws Throwable {
        standardNormal.setMean(-3.0);
        double ret = standardNormal.getDomainLowerBound(0.9);
        assertEquals(-3.0, ret, 1e-12);
    }

    // getDomainUpperBound: p < 0.5 branch returns the mean
    @Test
    public void testGetDomainUpperBound_pLessThanHalf_returnsMean() throws Throwable {
        standardNormal.setMean(1.5);
        double ret = standardNormal.getDomainUpperBound(0.1);
        assertEquals(1.5, ret, 1e-12);
    }

    // getDomainUpperBound: boundary p == 0.5 falls into else branch -> MAX_VALUE
    @Test
    public void testGetDomainUpperBound_pEqualsHalf_returnsMaxValue() throws Throwable {
        double ret = standardNormal.getDomainUpperBound(0.5);
        assertEquals(Double.MAX_VALUE, ret, 0.0);
    }

    // getDomainUpperBound: p > 0.5 branch returns Double.MAX_VALUE
    @Test
    public void testGetDomainUpperBound_pGreaterThanHalf_returnsMaxValue() throws Throwable {
        double ret = standardNormal.getDomainUpperBound(0.8);
        assertEquals(Double.MAX_VALUE, ret, 0.0);
    }

    // getInitialDomain: p < 0.5 branch returns mean - sd
    @Test
    public void testGetInitialDomain_pLessThanHalf_returnsMeanMinusSd() throws Throwable {
        NormalDistributionImpl d = new NormalDistributionImpl(4.0, 2.0);
        double ret = d.getInitialDomain(0.2);
        assertEquals(2.0, ret, 1e-12);
    }

    // getInitialDomain: p > 0.5 branch returns mean + sd
    @Test
    public void testGetInitialDomain_pGreaterThanHalf_returnsMeanPlusSd() throws Throwable {
        NormalDistributionImpl d = new NormalDistributionImpl(4.0, 2.0);
        double ret = d.getInitialDomain(0.8);
        assertEquals(6.0, ret, 1e-12);
    }

    // getInitialDomain: p == 0.5 exact branch returns the mean itself
    @Test
    public void testGetInitialDomain_pEqualsHalf_returnsMean() throws Throwable {
        NormalDistributionImpl d = new NormalDistributionImpl(4.0, 2.0);
        double ret = d.getInitialDomain(0.5);
        assertEquals(4.0, ret, 1e-12);
    }

    // sanity: after setStandardDeviation increases sd, CDF at fixed offset moves closer to 0.5
    @Test
    public void testSetStandardDeviation_largerSd_reducesTailProbability() throws Throwable {
        standardNormal.setStandardDeviation(10.0);
        double p = standardNormal.cumulativeProbability(1.0);
        assertTrue(p > 0.5);
        assertTrue(p < 0.54);
    }
}
