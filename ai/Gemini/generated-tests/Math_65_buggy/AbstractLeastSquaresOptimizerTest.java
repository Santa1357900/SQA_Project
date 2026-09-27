package org.apache.commons.math.optimization.general;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.MaxIterationsExceededException;
import org.apache.commons.math.analysis.DifferentiableMultivariateVectorialFunction;
import org.apache.commons.math.analysis.MultivariateMatrixFunction;
import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.optimization.VectorialConvergenceChecker;
import org.apache.commons.math.optimization.VectorialPointValuePair;
import org.apache.commons.math.optimization.SimpleVectorialValueChecker;
import org.junit.Test;

import static org.junit.Assert.*;

public class AbstractLeastSquaresOptimizerTest {

    private static class DummyOptimizer extends AbstractLeastSquaresOptimizer {
        private boolean doOptimizeCalled = false;

        protected VectorialPointValuePair doOptimize()
            throws FunctionEvaluationException, OptimizationException, IllegalArgumentException {
            doOptimizeCalled = true;
            updateResidualsAndCost();
            return new VectorialPointValuePair(point, objective);
        }
    }

    private static class DummyFunction implements DifferentiableMultivariateVectorialFunction {
        private final double[] values;
        private final double[][] jacobianMatrix;

        public DummyFunction(double[] values, double[][] jacobianMatrix) {
            this.values = values;
            this.jacobianMatrix = jacobianMatrix;
        }

        public double[] value(double[] point) throws IllegalArgumentException, FunctionEvaluationException {
            return values;
        }

        public MultivariateMatrixFunction jacobian() {
            return new MultivariateMatrixFunction() {
                public double[][] value(double[] point) throws IllegalArgumentException, FunctionEvaluationException {
                    return jacobianMatrix;
                }
            };
        }
    }

    @Test
    public void testDefaultConstructorAndGettersSetters() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        assertEquals(AbstractLeastSquaresOptimizer.DEFAULT_MAX_ITERATIONS, optimizer.getMaxIterations());
        assertEquals(Integer.MAX_VALUE, optimizer.getMaxEvaluations());
        assertEquals(0, optimizer.getIterations());
        assertEquals(0, optimizer.getEvaluations());
        assertEquals(0, optimizer.getJacobianEvaluations());
        assertNotNull(optimizer.getConvergenceChecker());

        optimizer.setMaxIterations(50);
        assertEquals(50, optimizer.getMaxIterations());

        optimizer.setMaxEvaluations(100);
        assertEquals(100, optimizer.getMaxEvaluations());

        VectorialConvergenceChecker customChecker = new SimpleVectorialValueChecker();
        optimizer.setConvergenceChecker(customChecker);
        assertEquals(customChecker, optimizer.getConvergenceChecker());
    }

    @Test
    public void testIncrementIterationsCounterExceeded() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        optimizer.setMaxIterations(0);
        try {
            optimizer.incrementIterationsCounter();
            fail("Expected OptimizationException");
        } catch (OptimizationException e) {
            assertTrue(e.getCause() instanceof MaxIterationsExceededException);
        }
    }

    @Test
    public void testOptimizeDimensionMismatch() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        double[] target = new double[] { 1.0, 2.0 };
        double[] weights = new double[] { 1.0 };
        double[] startPoint = new double[] { 0.0 };

        DummyFunction func = new DummyFunction(new double[] { 1.0, 2.0 }, new double[][] {{1.0}, {1.0}});

        try {
            optimizer.optimize(func, target, weights, startPoint);
            fail("Expected OptimizationException due to dimension mismatch");
        } catch (OptimizationException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testOptimizeAndMetrics() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        double[] target = new double[] { 3.0, 5.0 };
        double[] weights = new double[] { 1.0, 4.0 };
        double[] startPoint = new double[] { 1.0, 2.0 };

        DummyFunction func = new DummyFunction(
            new double[] { 2.0, 4.0 },
            new double[][] {
                { 1.0, 0.0 },
                { 0.0, 1.0 }
            }
        );

        VectorialPointValuePair result = optimizer.optimize(func, target, weights, startPoint);
        assertNotNull(result);
        assertEquals(1, optimizer.getEvaluations());
        
        double rms = optimizer.getRMS();
        assertTrue(rms >= 0.0);

        double chiSquare = optimizer.getChiSquare();
        assertTrue(chiSquare >= 0.0);
    }

    @Test
    public void testGetCovariancesAndGuessParametersErrors() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        double[] target = new double[] { 1.0, 2.0, 3.0 };
        double[] weights = new double[] { 1.0, 1.0, 1.0 };
        double[] startPoint = new double[] { 0.0, 0.0 };

        DummyFunction func = new DummyFunction(
            new double[] { 1.0, 2.0, 3.0 },
            new double[][] {
                { 1.0, 0.0 },
                { 0.0, 1.0 },
                { 1.0, 1.0 }
            }
        );

        optimizer.optimize(func, target, weights, startPoint);

        double[][] covar = optimizer.getCovariances();
        assertNotNull(covar);
        assertEquals(2, covar.length);

        double[] errors = optimizer.guessParametersErrors();
        assertNotNull(errors);
        assertEquals(2, errors.length);
    }

    @Test
    public void testGuessParametersErrorsNoDegreesOfFreedom() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        double[] target = new double[] { 1.0, 2.0 };
        double[] weights = new double[] { 1.0, 1.0 };
        double[] startPoint = new double[] { 0.0, 0.0, 0.0 }; // cols = 3, rows = 2 (rows <= cols)

        DummyFunction func = new DummyFunction(
            new double[] { 1.0, 2.0 },
            new double[][] {
                { 1.0, 0.0, 0.0 },
                { 0.0, 1.0, 0.0 }
            }
        );

        optimizer.optimize(func, target, weights, startPoint);

        try {
            optimizer.guessParametersErrors();
            fail("Expected OptimizationException due to no degrees of freedom");
        } catch (OptimizationException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testUpdateJacobianDimensionMismatch() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        double[] target = new double[] { 1.0, 2.0 };
        double[] weights = new double[] { 1.0, 1.0 };
        double[] startPoint = new double[] { 0.0 };

        // Jacobian rows (1) does not match rows (2)
        DummyFunction func = new DummyFunction(
            new double[] { 1.0, 2.0 },
            new double[][] {
                { 1.0 }
            }
        );

        try {
            optimizer.optimize(func, target, weights, startPoint);
            fail("Expected FunctionEvaluationException due to jacobian dimension mismatch");
        } catch (FunctionEvaluationException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testUpdateResidualsAndCostDimensionMismatch() throws Throwable {
        DummyOptimizer optimizer = new DummyOptimizer();
        double[] target = new double[] { 1.0, 2.0 };
        double[] weights = new double[] { 1.0, 1.0 };
        double[] startPoint = new double[] { 0.0 };

        // Objective function returns 3 elements instead of 2
        DummyFunction func = new DummyFunction(
            new double[] { 1.0, 2.0, 3.0 },
            new double[][] {
                { 1.0 }, { 1.0 }, { 1.0 }
            }
        );

        try {
            optimizer.optimize(func, target, weights, startPoint);
            fail("Expected FunctionEvaluationException due to objective dimension mismatch");
        } catch (FunctionEvaluationException e) {
            assertNotNull(e.getMessage());
        }
    }
}