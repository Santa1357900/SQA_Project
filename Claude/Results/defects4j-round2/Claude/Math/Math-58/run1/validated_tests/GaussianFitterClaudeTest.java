package org.apache.commons.math.optimization.fitting;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.exception.NullArgumentException;
import org.apache.commons.math.exception.NumberIsTooSmallException;
import org.apache.commons.math.optimization.DifferentiableMultivariateVectorialOptimizer;
import org.apache.commons.math.optimization.general.LevenbergMarquardtOptimizer;

public class GaussianFitterClaudeTest {

    // helper: builds synthetic noiseless Gaussian sample value
    private double gaussianValue(double norm, double mean, double sigma, double x) {
        double d = x - mean;
        return norm * Math.exp(-(d * d) / (2.0 * sigma * sigma));
    }

    // constructor: fresh fitter has no observations yet
    @Test
    public void testConstructor_noObservations_getObservationsReturnsEmptyArray() throws Throwable {
        DifferentiableMultivariateVectorialOptimizer opt = new LevenbergMarquardtOptimizer();
        GaussianFitter fitter = new GaussianFitter(opt);
        WeightedObservedPoint[] obs = fitter.getObservations();
        assertEquals(0, obs.length);
    }

    // addObservedPoint(x,y): single point stored correctly
    @Test
    public void testAddObservedPoint_singlePoint_observationsLengthOneAndValuesMatch() throws Throwable {
        DifferentiableMultivariateVectorialOptimizer opt = new LevenbergMarquardtOptimizer();
        GaussianFitter fitter = new GaussianFitter(opt);
        fitter.addObservedPoint(2.0, 7.0);
        WeightedObservedPoint[] obs = fitter.getObservations();
        assertEquals(1, obs.length);
        assertEquals(2.0, obs[0].getX(), 1e-9);
        assertEquals(7.0, obs[0].getY(), 1e-9);
    }

    // addObservedPoint(x,y): multiple points accumulate (loop, several iterations)
    @Test
    public void testAddObservedPoint_multiplePoints_observationsLengthMatchesCount() throws Throwable {
        DifferentiableMultivariateVectorialOptimizer opt = new LevenbergMarquardtOptimizer();
        GaussianFitter fitter = new GaussianFitter(opt);
        fitter.addObservedPoint(0.0, 1.0);
        fitter.addObservedPoint(1.0, 2.0);
        fitter.addObservedPoint(2.0, 3.0);
        assertEquals(3, fitter.getObservations().length);
    }

    // fit(): full javadoc example, mean must lie within observed x-range, norm/sigma positive
    @Test
    public void testFit_withJavadocExampleData_returnsPlausibleGaussianParameters() throws Throwable {
        DifferentiableMultivariateVectorialOptimizer opt = new LevenbergMarquardtOptimizer();
        GaussianFitter fitter = new GaussianFitter(opt);
        fitter.addObservedPoint(4.0254623, 531026.0);
        fitter.addObservedPoint(4.03128248, 984167.0);
        fitter.addObservedPoint(4.03839603, 1887233.0);
        fitter.addObservedPoint(4.04421621, 2687152.0);
        fitter.addObservedPoint(4.05132976, 3461228.0);
        fitter.addObservedPoint(4.05326982, 3580526.0);
        fitter.addObservedPoint(4.05779662, 3439750.0);
        fitter.addObservedPoint(4.0636168, 2877648.0);
        fitter.addObservedPoint(4.06943698, 2175960.0);
        fitter.addObservedPoint(4.07525716, 1447024.0);
        fitter.addObservedPoint(4.08237071, 717104.0);
        fitter.addObservedPoint(4.08366408, 620014.0);
        double[] parameters = fitter.fit();
        assertEquals(3, parameters.length);
        assertTrue(parameters[0] > 0.0);
        assertTrue(parameters[2] > 0.0);
        assertTrue(parameters[1] >= 4.0254623 && parameters[1] <= 4.08366408);
    }

    // fit(): fewer than 3 observations propagates NumberIsTooSmallException from ParameterGuesser
    @Test
    public void testFit_fewerThanThreeObservations_throwsNumberIsTooSmallException() throws Throwable {
        DifferentiableMultivariateVectorialOptimizer opt = new LevenbergMarquardtOptimizer();
        GaussianFitter fitter = new GaussianFitter(opt);
        fitter.addObservedPoint(0.0, 1.0);
        fitter.addObservedPoint(1.0, 2.0);
        try {
            fitter.fit();
            fail("expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException expected) {
        }
    }

    // ParameterGuesser(null): NullArgumentException branch
    @Test
    public void testParameterGuesserConstructor_nullObservations_throwsNullArgumentException() throws Throwable {
        try {
            new GaussianFitter.ParameterGuesser(null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // ParameterGuesser(2 points): below-minimum length branch
    @Test
    public void testParameterGuesserConstructor_twoObservations_throwsNumberIsTooSmallException() throws Throwable {
        WeightedObservedPoint[] pts = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 0.0),
            new WeightedObservedPoint(1.0, 1.0, 1.0)
        };
        try {
            new GaussianFitter.ParameterGuesser(pts);
            fail("expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException expected) {
        }
    }



    // guess(): result is cached (same values) but each call returns a distinct cloned array
    @Test
    public void testGuess_cachesParametersAndReturnsClone() throws Throwable {
        WeightedObservedPoint[] pts = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 1.0),
            new WeightedObservedPoint(1.0, 1.0, 5.0),
            new WeightedObservedPoint(1.0, 2.0, 1.0)
        };
        GaussianFitter.ParameterGuesser guesser = new GaussianFitter.ParameterGuesser(pts);
        double[] g1 = guesser.guess();
        double[] g2 = guesser.guess();
        assertNotSame(g1, g2);
        assertEquals(g1[2], g2[2], 1e-12);
    }

    // findMaxY: peak at last index forces right-side search to fail -> OutOfRange fallback used
    @Test
    public void testGuess_maxAtLastIndex_rightSideFallsBack_fallbackSigma() throws Throwable {
        WeightedObservedPoint[] pts = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 1.0),
            new WeightedObservedPoint(1.0, 1.0, 2.0),
            new WeightedObservedPoint(1.0, 2.0, 3.0),
            new WeightedObservedPoint(1.0, 3.0, 10.0)
        };
        double[] g = new GaussianFitter.ParameterGuesser(pts).guess();
        assertEquals(10.0, g[0], 1e-9);
        assertEquals(3.0, g[1], 1e-9);
        assertEquals(1.273983, g[2], 1e-3);
    }

    // findMaxY tie-break: strict '>' keeps the first occurrence among equal maxima
    @Test
    public void testGuess_findMaxY_tieBreak_firstOccurrenceWins() throws Throwable {
        WeightedObservedPoint[] pts = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 5.0),
            new WeightedObservedPoint(1.0, 1.0, 10.0),
            new WeightedObservedPoint(1.0, 2.0, 10.0),
            new WeightedObservedPoint(1.0, 3.0, 0.0)
        };
        double[] g = new GaussianFitter.ParameterGuesser(pts).guess();
        assertEquals(10.0, g[0], 1e-9);
        assertEquals(1.0, g[1], 1e-9);
    }





    // ascending-only 3 points: right side search has zero candidates -> OutOfRange fallback to full width
    @Test
    public void testGuess_outOfRangeHalfMax_fallsBackToFullRangeWidth() throws Throwable {
        WeightedObservedPoint[] pts = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 0.0),
            new WeightedObservedPoint(1.0, 1.0, 5.0),
            new WeightedObservedPoint(1.0, 2.0, 10.0)
        };
        double[] g = new GaussianFitter.ParameterGuesser(pts).guess();
        assertEquals(10.0, g[0], 1e-9);
        assertEquals(2.0, g[1], 1e-9);
        assertEquals(0.849322, g[2], 1e-3);
    }

    // unsorted input: internal sort uses a clone, caller's array order stays untouched, result still correct
    @Test
    public void testGuess_unsortedInput_sortsInternallyWithoutMutatingCallerArray() throws Throwable {
        WeightedObservedPoint[] pts = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 2.0, 10.0),
            new WeightedObservedPoint(1.0, 0.0, 0.0),
            new WeightedObservedPoint(1.0, 1.0, 5.0)
        };
        double[] g = new GaussianFitter.ParameterGuesser(pts).guess();
        assertEquals(10.0, g[0], 1e-9);
        assertEquals(2.0, g[1], 1e-9);
        assertEquals(2.0, pts[0].getX(), 1e-9);
    }

    // duplicate X values: comparator falls back to Y ordering; right side still fails -> fallback width
    @Test
    public void testGuess_duplicateXValues_comparatorFallbackToY_fallbackSigma() throws Throwable {
        WeightedObservedPoint[] pts = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 1.0, 2.0),
            new WeightedObservedPoint(1.0, 1.0, 8.0),
            new WeightedObservedPoint(1.0, 0.0, 0.0)
        };
        double[] g = new GaussianFitter.ParameterGuesser(pts).guess();
        assertEquals(8.0, g[0], 1e-9);
        assertEquals(1.0, g[1], 1e-9);
        assertEquals(0.424661, g[2], 1e-3);
    }

    // constructor stores a defensive clone: mutating caller's array afterwards must not affect guess()
    @Test
    public void testParameterGuesserConstructor_defensiveClone_externalMutationIgnored() throws Throwable {
        WeightedObservedPoint[] pts = new WeightedObservedPoint[] {
            new WeightedObservedPoint(1.0, 0.0, 1.0),
            new WeightedObservedPoint(1.0, 1.0, 5.0),
            new WeightedObservedPoint(1.0, 2.0, 1.0)
        };
        GaussianFitter.ParameterGuesser guesser = new GaussianFitter.ParameterGuesser(pts);
        pts[0] = new WeightedObservedPoint(1.0, 99.0, 99.0);
        double[] g = guesser.guess();
        assertEquals(5.0, g[0], 1e-9);
        assertEquals(1.0, g[1], 1e-9);
    }

    // fit(initialGuess): synthetic noiseless Gaussian recovered close to the true generating parameters
    @Test
    public void testFitWithInitialGuess_syntheticGaussianData_recoversTrueParameters() throws Throwable {
        DifferentiableMultivariateVectorialOptimizer opt = new LevenbergMarquardtOptimizer();
        GaussianFitter fitter = new GaussianFitter(opt);
        for (int x = 0; x <= 10; x++) {
            fitter.addObservedPoint((double) x, gaussianValue(10.0, 5.0, 2.0, (double) x));
        }
        double[] parameters = fitter.fit(new double[] { 8.0, 4.5, 1.5 });
        assertEquals(10.0, parameters[0], 0.1);
        assertEquals(5.0, parameters[1], 0.1);
        assertEquals(2.0, parameters[2], 0.1);
    }

    // fit(): no-arg overload uses ParameterGuesser internally and still returns a sane finite result
    @Test
    public void testFit_noArgs_syntheticGaussianData_returnsFiniteSaneParameters() throws Throwable {
        DifferentiableMultivariateVectorialOptimizer opt = new LevenbergMarquardtOptimizer();
        GaussianFitter fitter = new GaussianFitter(opt);
        for (int x = 0; x <= 10; x++) {
            fitter.addObservedPoint((double) x, gaussianValue(10.0, 5.0, 2.0, (double) x));
        }
        double[] parameters = fitter.fit();
        assertEquals(3, parameters.length);
        assertTrue(parameters[0] > 0.0);
        assertTrue(parameters[2] > 0.0);
        assertTrue(parameters[1] >= 0.0 && parameters[1] <= 10.0);
    }
}
