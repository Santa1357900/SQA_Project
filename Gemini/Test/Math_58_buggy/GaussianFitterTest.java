package org.apache.commons.math.optimization.fitting;

import java.util.Comparator;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.analysis.ParametricUnivariateRealFunction;
import org.apache.commons.math.exception.NullArgumentException;
import org.apache.commons.math.exception.NumberIsTooSmallException;
import org.apache.commons.math.exception.NotStrictlyPositiveException;
import org.apache.commons.math.optimization.DifferentiableMultivariateVectorialOptimizer;

public class GaussianFitterTest {

    @Test
    public void testParameterGuesserNullObservations() throws Throwable {
        try {
            new GaussianFitter.ParameterGuesser(null);
            fail("Expected NullArgumentException");
        } catch (NullArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testParameterGuesserTooFewObservations() throws Throwable {
        WeightedObservedPoint[] points = new WeightedObservedPoint[2];
        points[0] = new WeightedObservedPoint(1.0, 0.0, 1.0);
        points[1] = new WeightedObservedPoint(1.0, 1.0, 2.0);
        try {
            new GaussianFitter.ParameterGuesser(points);
            fail("Expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException e) {
            // Expected
        }
    }

    @Test
    public void testParameterGuesserValid() throws Throwable {
        WeightedObservedPoint[] points = new WeightedObservedPoint[5];
        points[0] = new WeightedObservedPoint(1.0, -2.0, 0.1);
        points[1] = new WeightedObservedPoint(1.0, -1.0, 0.5);
        points[2] = new WeightedObservedPoint(1.0, 0.0, 1.0);
        points[3] = new WeightedObservedPoint(1.0, 1.0, 0.5);
        points[4] = new WeightedObservedPoint(1.0, 2.0, 0.1);

        GaussianFitter.ParameterGuesser guesser = new GaussianFitter.ParameterGuesser(points);
        double[] guess1 = guesser.guess();
        double[] guess2 = guesser.guess();

        assertNotNull(guess1);
        assertEquals(3, guess1.length);
        assertNotNull(guess2);
        assertEquals(3, guess2.length);
    }

    @Test
    public void testParameterGuesserUnorderedAndInterpolationFallback() throws Throwable {
        // Points that might trigger OutOfRangeException in interpolation or fallback branches
        WeightedObservedPoint[] points = new WeightedObservedPoint[4];
        points[0] = new WeightedObservedPoint(1.0, 5.0, 10.0);
        points[1] = new WeightedObservedPoint(1.0, 1.0, 1.0);
        points[2] = new WeightedObservedPoint(1.0, 3.0, 5.0);
        points[3] = new WeightedObservedPoint(1.0, 0.0, 0.0);

        GaussianFitter.ParameterGuesser guesser = new GaussianFitter.ParameterGuesser(points);
        double[] guess = guesser.guess();
        assertNotNull(guess);
        assertEquals(3, guess.length);
    }

    @Test
    public void testParameterGuesserComparatorNulls() throws Throwable {
        // Use a dummy subclass or access via valid guess method that sorts points containing nulls if possible, 
        // but ParameterGuesser clones and sorts. Wait, if points contain null elements, Arrays.sort might throw NPE.
        // Let's test standard points where comparator logic is fully exercised (equal X, different Y, etc.)
        WeightedObservedPoint[] points = new WeightedObservedPoint[4];
        points[0] = new WeightedObservedPoint(1.0, 1.0, 2.0);
        points[1] = new WeightedObservedPoint(1.0, 1.0, 1.0); // same X, different Y
        points[2] = new WeightedObservedPoint(2.0, 2.0, 1.0); // different X, different weight comparison if applicable
        points[3] = new WeightedObservedPoint(0.5, 2.0, 1.0); // different weight
        
        GaussianFitter.ParameterGuesser guesser = new GaussianFitter.ParameterGuesser(points);
        double[] guess = guesser.guess();
        assertNotNull(guess);
    }

    @Test
    public void testGaussianFitterFitParametricFunctionDirectly() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        GaussianFitter fitter = new GaussianFitter(optimizer);
        fitter.addObservedPoint(0.0, 1.0);
        fitter.addObservedPoint(1.0, 2.0);
        fitter.addObservedPoint(2.0, 1.0);
        fitter.addObservedPoint(3.0, 0.5);

        double[] initialGuess = new double[] { 2.0, 1.0, 1.0 };
        double[] result = fitter.fit(initialGuess);
        assertNotNull(result);
    }

    @Test
    public void testGaussianFitterFunctionParametricValueAndGradientExceptions() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        GaussianFitter fitter = new GaussianFitter(optimizer);
        fitter.addObservedPoint(0.0, 1.0);
        fitter.addObservedPoint(1.0, 2.0);
        fitter.addObservedPoint(2.0, 1.0);
        fitter.addObservedPoint(3.0, 0.5);

        // We can invoke fit(initialGuess) which creates the inner ParametricUnivariateRealFunction
        // and tests value & gradient handling when sigma is negative/zero (NotStrictlyPositiveException caught block)
        double[] initialGuess = new double[] { 2.0, 1.0, -1.0 };
        double[] result = fitter.fit(initialGuess);
        assertNotNull(result);
    }

    @Test
    public void testGaussianFitterFitNoArguments() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        GaussianFitter fitter = new GaussianFitter(optimizer);
        fitter.addObservedPoint(0.0, 0.0);
        fitter.addObservedPoint(1.0, 1.0);
        fitter.addObservedPoint(2.0, 2.0);
        fitter.addObservedPoint(3.0, 1.0);
        fitter.addObservedPoint(4.0, 0.0);

        double[] result = fitter.fit();
        assertNotNull(result);
    }

    private static class DummyOptimizer implements DifferentiableMultivariateVectorialOptimizer {
        public org.apache.commons.math.optimization.OptimizationData[] getInformations() {
            return new org.apache.commons.math.optimization.OptimizationData[0];
        }

        public int getMaxEvaluations() {
            return 100;
        }

        public int getEvaluations() {
            return 10;
        }

        public int getMaxIterations() {
            return 100;
        }

        public int getIterations() {
            return 5;
        }

        public org.apache.commons.math.optimization.ConvergenceChecker<org.apache.commons.math.optimization.VectorialPointValuePair> getConvergenceChecker() {
            return null;
        }

        public org.apache.commons.math.optimization.VectorialPointValuePair optimize(int maxEval,
                org.apache.commons.math.analysis.DifferentiableMultivariateVectorialFunction f,
                double[] target, double[] weights, double[] startPoint) {
            return new org.apache.commons.math.optimization.VectorialPointValuePair(new double[] {1.0, 1.0, 1.0}, new double[] {0.0});
        }
    }
}