package org.apache.commons.math3.optimization.fitting;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math3.optimization.DifferentiableMultivariateVectorOptimizer;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.exception.ZeroException;

public class HarmonicFitterTest {

    @Test(expected = NumberIsTooSmallException.class)
    public void testParameterGuesserTooFewPoints() throws Throwable {
        WeightedObservedPoint[] points = new WeightedObservedPoint[3];
        points[0] = new WeightedObservedPoint(1.0, 0.0, 1.0);
        points[1] = new WeightedObservedPoint(1.0, 1.0, 2.0);
        points[2] = new WeightedObservedPoint(1.0, 2.0, 1.0);
        new HarmonicFitter.ParameterGuesser(points);
    }

    @Test(expected = ZeroException.class)
    public void testParameterGuesserZeroRange() throws Throwable {
        WeightedObservedPoint[] points = new WeightedObservedPoint[4];
        points[0] = new WeightedObservedPoint(1.0, 0.0, 1.0);
        points[1] = new WeightedObservedPoint(1.0, 0.0, 2.0);
        points[2] = new WeightedObservedPoint(1.0, 0.0, 3.0);
        points[3] = new WeightedObservedPoint(1.0, 0.0, 4.0);
        HarmonicFitter.ParameterGuesser guesser = new HarmonicFitter.ParameterGuesser(points);
        guesser.guess();
    }

    @Test
    public void testParameterGuesserNormal() throws Throwable {
        WeightedObservedPoint[] points = new WeightedObservedPoint[5];
        points[0] = new WeightedObservedPoint(1.0, 0.0, 1.0);
        points[1] = new WeightedObservedPoint(1.0, Math.PI / 2, 0.0);
        points[2] = new WeightedObservedPoint(1.0, Math.PI, -1.0);
        points[3] = new WeightedObservedPoint(1.0, 3 * Math.PI / 2, 0.0);
        points[4] = new WeightedObservedPoint(1.0, 2 * Math.PI, 1.0);

        HarmonicFitter.ParameterGuesser guesser = new HarmonicFitter.ParameterGuesser(points);
        double[] guess = guesser.guess();
        assertNotNull(guess);
        assertEquals(3, guess.length);
    }

    @Test
    public void testParameterGuesserUnsorted() throws Throwable {
        WeightedObservedPoint[] points = new WeightedObservedPoint[5];
        points[0] = new WeightedObservedPoint(1.0, 2 * Math.PI, 1.0);
        points[1] = new WeightedObservedPoint(1.0, 0.0, 1.0);
        points[2] = new WeightedObservedPoint(1.0, Math.PI, -1.0);
        points[3] = new WeightedObservedPoint(1.0, Math.PI / 2, 0.0);
        points[4] = new WeightedObservedPoint(1.0, 3 * Math.PI / 2, 0.0);

        HarmonicFitter.ParameterGuesser guesser = new HarmonicFitter.ParameterGuesser(points);
        double[] guess = guesser.guess();
        assertNotNull(guess);
        assertEquals(3, guess.length);
    }

    @Test
    public void testHarmonicFitterFitInitialGuess() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        HarmonicFitter fitter = new HarmonicFitter(optimizer);
        double[] initialGuess = new double[] { 1.0, 1.0, 1.0 };
        
        fitter.addObservedPoint(1.0, 0.0, 1.0);
        fitter.addObservedPoint(1.0, 1.0, 2.0);
        fitter.addObservedPoint(1.0, 2.0, 1.0);
        fitter.addObservedPoint(1.0, 3.0, 0.0);

        double[] result = fitter.fit(initialGuess);
        assertNotNull(result);
    }

    @Test
    public void testHarmonicFitterFitAutoGuess() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        HarmonicFitter fitter = new HarmonicFitter(optimizer);
        
        fitter.addObservedPoint(1.0, 0.0, 1.0);
        fitter.addObservedPoint(1.0, Math.PI / 2, 0.0);
        fitter.addObservedPoint(1.0, Math.PI, -1.0);
        fitter.addObservedPoint(1.0, 3 * Math.PI / 2, 0.0);
        fitter.addObservedPoint(1.0, 2 * Math.PI, 1.0);

        double[] result = fitter.fit();
        assertNotNull(result);
        assertEquals(3, result.length);
    }

    private static class DummyOptimizer implements DifferentiableMultivariateVectorOptimizer {
        public org.apache.commons.math3.optimization.PointVectorValuePair optimize(int maxEval, org.apache.commons.math3.analysis.MultivariateVectorFunction f, org.apache.commons.math3.analysis.MultivariateMatrixFunction jacobian, double[] target, double[] weight, double[] startPoint) {
            return new org.apache.commons.math3.optimization.PointVectorValuePair(startPoint, f.value(startPoint));
        }

        public org.apache.commons.math3.optimization.PointVectorValuePair optimize(int maxEval, org.apache.commons.math3.analysis.DifferentiableMultivariateVectorFunction f, double[] target, double[] weight, double[] startPoint) {
            return new org.apache.commons.math3.optimization.PointVectorValuePair(startPoint, f.value(startPoint));
        }

        public int getMaxEvaluations() {
            return 100;
        }

        public int getEvaluations() {
            return 10;
        }

        public org.apache.commons.math3.optimization.ConvergenceChecker<org.apache.commons.math3.optimization.PointVectorValuePair> getConvergenceChecker() {
            return null;
        }
    }
}