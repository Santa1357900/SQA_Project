package org.apache.commons.math3.util;

import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.math3.exception.ConvergenceException;
import org.apache.commons.math3.exception.MaxCountExceededException;

public class ContinuedFractionClaudeTest {

    /** Simple periodic continued fraction: a0 at n==0, aRest for n>=1, bRest constant for n>=1. */
    private static class SimpleContinuedFraction extends ContinuedFraction {
        private final double a0;
        private final double aRest;
        private final double bRest;

        SimpleContinuedFraction(double a0, double aRest, double bRest) {
            this.a0 = a0;
            this.aRest = aRest;
            this.bRest = bRest;
        }

        protected double getA(int n, double x) {
            return n == 0 ? a0 : aRest;
        }

        protected double getB(int n, double x) {
            return bRest;
        }
    }

    /** Continued fraction whose coefficients depend directly on the evaluation point x. */
    private static class XConstantFraction extends ContinuedFraction {
        protected double getA(int n, double x) {
            return x;
        }

        protected double getB(int n, double x) {
            return 0.0;
        }
    }

    // Branch: hPrev not near zero, deltaN==1 immediately -> loop runs once then breaks.
    @Test
    public void testEvaluate_constantFraction_returnsConstant() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(5.0, 5.0, 0.0);
        assertEquals(5.0, cf.evaluate(0.0), 1e-9);
    }

    // Golden ratio CF [1;1,1,1,...] = (1+sqrt5)/2 ; exercises multi-iteration convergence loop.
    @Test
    public void testEvaluate_goldenRatio_matchesPhi() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(1.0, 1.0, 1.0);
        double expected = (1.0 + Math.sqrt(5.0)) / 2.0;
        assertEquals(expected, cf.evaluate(0.0), 1e-6);
    }

    // sqrt(2) CF [1;2,2,2,...] ; independent mathematical oracle, multi-iteration branch.
    @Test
    public void testEvaluate_sqrtTwo_matchesSqrt2() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(1.0, 2.0, 1.0);
        assertEquals(Math.sqrt(2.0), cf.evaluate(0.0), 1e-6);
    }

    // Branch: hPrev == 0 -> substituted by internal "small" value, result approximately zero.
    @Test
    public void testEvaluate_hPrevNearZero_substitutesSmallValue() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(0.0, 1.0, 0.0);
        double result = cf.evaluate(0.0);
        assertEquals(0.0, result, 1e-40);
    }

    // evaluate(x, epsilon) overload, loose epsilon still approximates golden ratio.
    @Test
    public void testEvaluateEpsilon_looseEpsilon_approximatesGoldenRatio() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(1.0, 1.0, 1.0);
        double expected = (1.0 + Math.sqrt(5.0)) / 2.0;
        double result = cf.evaluate(0.0, 1e-2);
        assertEquals(expected, result, 0.1);
    }

    // evaluate(x, epsilon) overload, tight epsilon on sqrt(2).
    @Test
    public void testEvaluateEpsilon_tightEpsilon_matchesSqrt2() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(1.0, 2.0, 1.0);
        double result = cf.evaluate(0.0, 1e-10);
        assertEquals(Math.sqrt(2.0), result, 1e-8);
    }

    // evaluate(x, maxIterations) overload, sufficient iterations converges.
    @Test
    public void testEvaluateMaxIterations_sufficient_convergesToGoldenRatio() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(1.0, 1.0, 1.0);
        double expected = (1.0 + Math.sqrt(5.0)) / 2.0;
        double result = cf.evaluate(0.0, 50);
        assertEquals(expected, result, 1e-6);
    }

    // evaluate(x, maxIterations) overload: too few iterations -> MaxCountExceededException.
    @Test
    public void testEvaluateMaxIterations_insufficient_throwsMaxCountExceededException() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(1.0, 1.0, 1.0);
        try {
            cf.evaluate(0.0, 2);
            fail("expected MaxCountExceededException");
        } catch (MaxCountExceededException expected) {
        }
    }

    // Branch: maxIterations==1 -> while loop body never executes, immediate throw.
    @Test
    public void testEvaluate_maxIterationsOne_throwsImmediately() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(1.0, 1.0, 1.0);
        try {
            cf.evaluate(0.0, 1);
            fail("expected MaxCountExceededException");
        } catch (MaxCountExceededException expected) {
        }
    }

    // Full signature evaluate(x, epsilon, maxIterations) with custom params on sqrt(2).
    @Test
    public void testEvaluateFullSignature_customParams_matchesSqrt2() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(1.0, 2.0, 1.0);
        double result = cf.evaluate(0.0, 1e-10, 100);
        assertEquals(Math.sqrt(2.0), result, 1e-8);
    }





    // Negative constant continued fraction converges to its own negative value.
    @Test
    public void testEvaluate_negativeConstant_convergesToNegativeValue() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(-3.0, -3.0, 0.0);
        assertEquals(-3.0, cf.evaluate(0.0), 1e-9);
    }

    // x parameter is actually passed to getA/getB and used in the computation.
    @Test
    public void testEvaluate_xParameterPassedToCoefficients_returnsX() throws Throwable {
        XConstantFraction cf = new XConstantFraction();
        assertEquals(3.5, cf.evaluate(3.5), 1e-9);
    }

    // Different x values produce different results through the same fraction instance.
    @Test
    public void testEvaluate_differentXValues_returnDifferentResults() throws Throwable {
        XConstantFraction cf = new XConstantFraction();
        double r1 = cf.evaluate(2.0);
        double r2 = cf.evaluate(-3.0);
        assertEquals(2.0, r1, 1e-9);
        assertEquals(-3.0, r2, 1e-9);
    }

    // Branch: cN overflows to infinity but rescale loop (a>b path) recovers a finite result.
    @Test
    public void testEvaluate_rescaleRecoversFromInfinity_returnsFiniteValue() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(Double.MAX_VALUE, Double.MAX_VALUE, 0.0);
        double result = cf.evaluate(0.0);
        assertFalse(Double.isInfinite(result));
        assertFalse(Double.isNaN(result));
        assertTrue(result > 0.0);
    }

    // Consistency: evaluate(x) must delegate to evaluate(x, DEFAULT_EPSILON, Integer.MAX_VALUE).
    @Test
    public void testEvaluate_defaultEpsilonDelegation_matchesExplicitEpsilonOverload() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(1.0, 1.0, 1.0);
        double viaDefault = cf.evaluate(0.0);
        double viaExplicit = cf.evaluate(0.0, 10e-9);
        assertEquals(viaExplicit, viaDefault, 0.0);
    }

    // Consistency: evaluate(x, maxIterations) must delegate using DEFAULT_EPSILON.
    @Test
    public void testEvaluate_defaultEpsilonDelegation_matchesExplicitMaxIterationsOverload() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(1.0, 1.0, 1.0);
        double viaMaxIterOverload = cf.evaluate(0.0, 50);
        double viaFullSignature = cf.evaluate(0.0, 10e-9, 50);
        assertEquals(viaFullSignature, viaMaxIterOverload, 0.0);
    }

    // evaluate(x, epsilon) overload on a trivially convergent constant fraction.
    @Test
    public void testEvaluateEpsilon_constantFraction_returnsConstant() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(7.0, 7.0, 0.0);
        assertEquals(7.0, cf.evaluate(0.0, 1e-9), 1e-9);
    }

    // evaluate(x, maxIterations) overload on a trivially convergent constant fraction.
    @Test
    public void testEvaluateMaxIterations_constantFraction_returnsConstant() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(7.0, 7.0, 0.0);
        assertEquals(7.0, cf.evaluate(0.0, 10), 1e-9);
    }

    // Full signature evaluate(x, epsilon, maxIterations) on a trivially convergent constant fraction.
    @Test
    public void testEvaluateFullSignature_constantFraction_returnsConstant() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(7.0, 7.0, 0.0);
        assertEquals(7.0, cf.evaluate(0.0, 1e-9, 10), 1e-9);
    }

    // evaluate(x, maxIterations) with a very large bound still converges for a constant fraction.
    @Test
    public void testEvaluateMaxIterations_largeValue_constantStillConverges() throws Throwable {
        SimpleContinuedFraction cf = new SimpleContinuedFraction(-2.0, -2.0, 0.0);
        assertEquals(-2.0, cf.evaluate(0.0, 1000000), 1e-9);
    }
}
