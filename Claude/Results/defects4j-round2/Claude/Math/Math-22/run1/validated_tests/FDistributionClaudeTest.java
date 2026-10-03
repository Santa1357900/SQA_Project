package org.apache.commons.math3.distribution;

import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Test;
import static org.junit.Assert.*;

public class FDistributionClaudeTest {

    // constructor: valid params create instance, getters return given values
    @Test
    public void testConstructor_validParams_createsInstance() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 3.0);
        assertEquals(2.0, dist.getNumeratorDegreesOfFreedom(), 1e-9);
        assertEquals(3.0, dist.getDenominatorDegreesOfFreedom(), 1e-9);
    }

    // constructor: numeratorDegreesOfFreedom == 0 throws NotStrictlyPositiveException
    @Test
    public void testConstructor_zeroNumeratorDF_throwsException() throws Throwable {
        try {
            new FDistribution(0.0, 3.0);
            fail("expected NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException expected) {
        }
    }

    // constructor: numeratorDegreesOfFreedom < 0 throws NotStrictlyPositiveException
    @Test
    public void testConstructor_negativeNumeratorDF_throwsException() throws Throwable {
        try {
            new FDistribution(-1.0, 3.0);
            fail("expected NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException expected) {
        }
    }

    // constructor: denominatorDegreesOfFreedom == 0 throws NotStrictlyPositiveException
    @Test
    public void testConstructor_zeroDenominatorDF_throwsException() throws Throwable {
        try {
            new FDistribution(2.0, 0.0);
            fail("expected NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException expected) {
        }
    }

    // constructor: denominatorDegreesOfFreedom < 0 throws NotStrictlyPositiveException
    @Test
    public void testConstructor_negativeDenominatorDF_throwsException() throws Throwable {
        try {
            new FDistribution(2.0, -5.0);
            fail("expected NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException expected) {
        }
    }

    // constructor with accuracy: custom accuracy value is stored and returned
    @Test
    public void testConstructorWithAccuracy_validParams_setsAccuracy() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 3.0, 0.001);
        assertEquals(0.001, dist.getSolverAbsoluteAccuracy(), 1e-12);
    }

    // constructor with RandomGenerator: valid params create instance with correct df values
    @Test
    public void testConstructorWithRandomGenerator_validParams_createsInstance() throws Throwable {
        RandomGenerator rng = new Well19937c();
        FDistribution dist = new FDistribution(rng, 2.0, 3.0, 1e-10);
        assertEquals(2.0, dist.getNumeratorDegreesOfFreedom(), 1e-9);
        assertEquals(3.0, dist.getDenominatorDegreesOfFreedom(), 1e-9);
    }

    // constructor with RandomGenerator: invalid numerator df throws NotStrictlyPositiveException
    @Test
    public void testConstructorWithRandomGenerator_invalidNumerator_throwsException() throws Throwable {
        RandomGenerator rng = new Well19937c();
        try {
            new FDistribution(rng, 0.0, 3.0, 1e-9);
            fail("expected NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException expected) {
        }
    }

    // constructor with RandomGenerator: invalid denominator df throws NotStrictlyPositiveException
    @Test
    public void testConstructorWithRandomGenerator_invalidDenominator_throwsException() throws Throwable {
        RandomGenerator rng = new Well19937c();
        try {
            new FDistribution(rng, 2.0, -1.0, 1e-9);
            fail("expected NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException expected) {
        }
    }

    // density: normal positive x, numeratorDF=2, denominatorDF=2, x=1 -> known value 0.25
    @Test
    public void testDensity_positiveX_normalCase_returnsExpectedValue() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 2.0);
        assertEquals(0.25, dist.density(1.0), 1e-9);
    }







    // cumulativeProbability: x < 0 -> branch x<=0 returns 0
    @Test
    public void testCumulativeProbability_negativeX_returnsZero() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 3.0);
        assertEquals(0.0, dist.cumulativeProbability(-1.0), 1e-9);
    }

    // cumulativeProbability: x == 0 -> branch x<=0 returns 0
    @Test
    public void testCumulativeProbability_zeroX_returnsZero() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 3.0);
        assertEquals(0.0, dist.cumulativeProbability(0.0), 1e-9);
    }

    // cumulativeProbability: x>0, d1=d2=2 -> regularizedBeta(0.5,1,1)=0.5 (uniform I_x(1,1)=x)
    @Test
    public void testCumulativeProbability_positiveX_knownValue() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 2.0);
        assertEquals(0.5, dist.cumulativeProbability(1.0), 1e-9);
    }

    // cumulativeProbability: as x grows large, probability approaches 1
    @Test
    public void testCumulativeProbability_largeX_approachesOne() throws Throwable {
        FDistribution dist = new FDistribution(5.0, 5.0);
        double result = dist.cumulativeProbability(1000.0);
        assertEquals(1.0, result, 1e-6);
    }

    // getNumeratorDegreesOfFreedom returns value passed to constructor
    @Test
    public void testGetNumeratorDegreesOfFreedom_returnsConstructorValue() throws Throwable {
        FDistribution dist = new FDistribution(7.5, 3.0);
        assertEquals(7.5, dist.getNumeratorDegreesOfFreedom(), 1e-9);
    }

    // getDenominatorDegreesOfFreedom returns value passed to constructor
    @Test
    public void testGetDenominatorDegreesOfFreedom_returnsConstructorValue() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 9.25);
        assertEquals(9.25, dist.getDenominatorDegreesOfFreedom(), 1e-9);
    }

    // getSolverAbsoluteAccuracy: default two-arg constructor uses DEFAULT_INVERSE_ABSOLUTE_ACCURACY
    @Test
    public void testGetSolverAbsoluteAccuracy_defaultConstructor_returnsDefault() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 3.0);
        assertEquals(FDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY, dist.getSolverAbsoluteAccuracy(), 1e-20);
    }

    // DEFAULT_INVERSE_ABSOLUTE_ACCURACY constant has documented value 1e-9
    @Test
    public void testDefaultInverseAbsoluteAccuracyConstant_hasExpectedValue() throws Throwable {
        assertEquals(1e-9, FDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY, 1e-20);
    }

    // getNumericalMean: denominatorDF > 2 -> b/(b-2)
    @Test
    public void testGetNumericalMean_denominatorGreaterThanTwo_returnsFormulaValue() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 3.0);
        assertEquals(3.0, dist.getNumericalMean(), 1e-9);
    }

    // getNumericalMean: denominatorDF == 2 (boundary, not > 2) -> NaN
    @Test
    public void testGetNumericalMean_denominatorEqualsTwo_returnsNaN() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 2.0);
        assertTrue(Double.isNaN(dist.getNumericalMean()));
    }

    // getNumericalMean: denominatorDF < 2 -> NaN
    @Test
    public void testGetNumericalMean_denominatorLessThanTwo_returnsNaN() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 1.0);
        assertTrue(Double.isNaN(dist.getNumericalMean()));
    }

    // getNumericalVariance / calculateNumericalVariance: denominatorDF > 4 -> formula value
    @Test
    public void testGetNumericalVariance_denominatorGreaterThanFour_returnsFormulaValue() throws Throwable {
        FDistribution dist = new FDistribution(3.0, 5.0);
        assertEquals(100.0 / 9.0, dist.getNumericalVariance(), 1e-9);
    }

    // getNumericalVariance: denominatorDF == 4 (boundary, not > 4) -> NaN
    @Test
    public void testGetNumericalVariance_denominatorEqualsFour_returnsNaN() throws Throwable {
        FDistribution dist = new FDistribution(3.0, 4.0);
        assertTrue(Double.isNaN(dist.getNumericalVariance()));
    }

    // getNumericalVariance: denominatorDF < 4 -> NaN
    @Test
    public void testGetNumericalVariance_denominatorLessThanFour_returnsNaN() throws Throwable {
        FDistribution dist = new FDistribution(3.0, 2.0);
        assertTrue(Double.isNaN(dist.getNumericalVariance()));
    }

    // getNumericalVariance: cached path - second call returns same value as first (covers cache flag branch)
    @Test
    public void testGetNumericalVariance_cachesValue_secondCallSameResult() throws Throwable {
        FDistribution dist = new FDistribution(3.0, 5.0);
        double first = dist.getNumericalVariance();
        double second = dist.getNumericalVariance();
        assertEquals(first, second, 1e-12);
    }

    // getSupportLowerBound always returns 0
    @Test
    public void testGetSupportLowerBound_returnsZero() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 3.0);
        assertEquals(0.0, dist.getSupportLowerBound(), 1e-9);
    }

    // getSupportUpperBound always returns positive infinity
    @Test
    public void testGetSupportUpperBound_returnsPositiveInfinity() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 3.0);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), dist.getSupportUpperBound(), 0.0);
    }



    // isSupportUpperBoundInclusive always returns false
    @Test
    public void testIsSupportUpperBoundInclusive_returnsFalse() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 3.0);
        assertFalse(dist.isSupportUpperBoundInclusive());
    }

    // isSupportConnected always returns true
    @Test
    public void testIsSupportConnected_returnsTrue() throws Throwable {
        FDistribution dist = new FDistribution(2.0, 3.0);
        assertTrue(dist.isSupportConnected());
    }
}
