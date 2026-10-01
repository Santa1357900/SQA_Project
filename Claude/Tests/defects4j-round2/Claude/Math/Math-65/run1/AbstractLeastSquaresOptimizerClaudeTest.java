package org.apache.commons.math.optimization.general;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.DifferentiableMultivariateVectorialFunction;
import org.apache.commons.math.analysis.MultivariateMatrixFunction;
import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.optimization.VectorialConvergenceChecker;
import org.apache.commons.math.optimization.SimpleVectorialValueChecker;
import org.apache.commons.math.optimization.VectorialPointValuePair;

public class AbstractLeastSquaresOptimizerClaudeTest {

    /** Minimal concrete optimizer: updates jacobian and residuals/cost, no iteration check. */
    private static class SimpleOptimizer extends AbstractLeastSquaresOptimizer {
        protected VectorialPointValuePair doOptimize()
            throws FunctionEvaluationException, OptimizationException, IllegalArgumentException {
            updateJacobian();
            updateResidualsAndCost();
            return new VectorialPointValuePair(point, objective);
        }
    }

    /** Concrete optimizer that also exercises incrementIterationsCounter(). */
    private static class IterationCountingOptimizer extends AbstractLeastSquaresOptimizer {
        protected VectorialPointValuePair doOptimize()
            throws FunctionEvaluationException, OptimizationException, IllegalArgumentException {
            incrementIterationsCounter();
            updateJacobian();
            updateResidualsAndCost();
            return new VectorialPointValuePair(point, objective);
        }
    }

    /** value(point) = coefficients * point ; jacobian(point) = coefficients (fresh copy each call). */
    private static class LinearFunction implements DifferentiableMultivariateVectorialFunction {
        private final double[][] coefficients;

        LinearFunction(double[][] coefficients) {
            this.coefficients = coefficients;
        }

        public double[] value(double[] pt) throws FunctionEvaluationException {
            double[] result = new double[coefficients.length];
            for (int i = 0; i < coefficients.length; i++) {
                double sum = 0;
                for (int j = 0; j < pt.length; j++) {
                    sum += coefficients[i][j] * pt[j];
                }
                result[i] = sum;
            }
            return result;
        }

        public MultivariateMatrixFunction jacobian() {
            return new MultivariateMatrixFunction() {
                public double[][] value(double[] pt) throws FunctionEvaluationException {
                    double[][] copy = new double[coefficients.length][];
                    for (int i = 0; i < coefficients.length; i++) {
                        copy[i] = coefficients[i].clone();
                    }
                    return copy;
                }
            };
        }
    }

    /** Jacobian row count (2) intentionally mismatches the single target row. */
    private static class BadJacobianDimFunction implements DifferentiableMultivariateVectorialFunction {
        public double[] value(double[] pt) throws FunctionEvaluationException {
            return new double[] { pt[0] };
        }

        public MultivariateMatrixFunction jacobian() {
            return new MultivariateMatrixFunction() {
                public double[][] value(double[] pt) throws FunctionEvaluationException {
                    return new double[][] { {1.0}, {1.0} };
                }
            };
        }
    }

    /** value() length (2) intentionally mismatches the single target row, jacobian dims are fine. */
    private static class BadValueDimFunction implements DifferentiableMultivariateVectorialFunction {
        public double[] value(double[] pt) throws FunctionEvaluationException {
            return new double[] { pt[0], pt[0] };
        }

        public MultivariateMatrixFunction jacobian() {
            return new MultivariateMatrixFunction() {
                public double[][] value(double[] pt) throws FunctionEvaluationException {
                    return new double[][] { {1.0} };
                }
            };
        }
    }

    // Default constructor: checker, maxIterations, maxEvaluations, counters all at defaults
    @Test
    public void testDefaultConstructor_initialState_returnsDefaults() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        assertEquals(AbstractLeastSquaresOptimizer.DEFAULT_MAX_ITERATIONS, optimizer.getMaxIterations());
        assertEquals(Integer.MAX_VALUE, optimizer.getMaxEvaluations());
        assertEquals(0, optimizer.getIterations());
        assertEquals(0, optimizer.getEvaluations());
        assertEquals(0, optimizer.getJacobianEvaluations());
        assertTrue(optimizer.getConvergenceChecker() instanceof SimpleVectorialValueChecker);
    }

    // setMaxIterations/getMaxIterations simple pass-through
    @Test
    public void testSetMaxIterations_positiveValue_getReturnsSame() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        optimizer.setMaxIterations(42);
        assertEquals(42, optimizer.getMaxIterations());
    }

    // boundary value 0 for maxIterations
    @Test
    public void testSetMaxIterations_zero_getReturnsZero() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        optimizer.setMaxIterations(0);
        assertEquals(0, optimizer.getMaxIterations());
    }

    // setMaxEvaluations/getMaxEvaluations simple pass-through
    @Test
    public void testSetMaxEvaluations_positiveValue_getReturnsSame() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        optimizer.setMaxEvaluations(7);
        assertEquals(7, optimizer.getMaxEvaluations());
    }

    // setConvergenceChecker/getConvergenceChecker stores same reference
    @Test
    public void testSetConvergenceChecker_customChecker_getReturnsSameInstance() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        VectorialConvergenceChecker customChecker = new SimpleVectorialValueChecker();
        optimizer.setConvergenceChecker(customChecker);
        assertSame(customChecker, optimizer.getConvergenceChecker());
    }

    // optimize(): target.length != weights.length branch -> OptimizationException
    @Test
    public void testOptimize_targetWeightsLengthMismatch_throwsOptimizationException() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f = new LinearFunction(new double[][] { {1.0} });
        try {
            optimizer.optimize(f, new double[] {1.0, 2.0}, new double[] {1.0}, new double[] {0.0});
            fail("expected OptimizationException");
        } catch (OptimizationException expected) {
            // target and weights dimensions differ
        }
    }

    // optimize(): normal path, evaluation/jacobian counters incremented, cost computed
    @Test
    public void testOptimize_validInputs_computesCostAndCounters() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f =
            new LinearFunction(new double[][] { {1.0}, {1.0} });
        VectorialPointValuePair result = optimizer.optimize(f,
            new double[] {3.0, 5.0}, new double[] {1.0, 2.0}, new double[] {0.0});
        assertNotNull(result);
        assertEquals(1, optimizer.getEvaluations());
        assertEquals(1, optimizer.getJacobianEvaluations());
        double expectedCost = Math.sqrt(1.0 * 9.0 + 2.0 * 25.0);
        assertEquals(expectedCost, optimizer.cost, 1e-9);
    }

    // optimize() resets iteration counter on each call
    @Test
    public void testOptimize_calledTwice_resetsIterationCounterEachTime() throws Throwable {
        IterationCountingOptimizer optimizer = new IterationCountingOptimizer();
        DifferentiableMultivariateVectorialFunction f = new LinearFunction(new double[][] { {1.0} });
        optimizer.optimize(f, new double[] {1.0}, new double[] {1.0}, new double[] {0.0});
        assertEquals(1, optimizer.getIterations());
        optimizer.optimize(f, new double[] {1.0}, new double[] {1.0}, new double[] {0.0});
        assertEquals(1, optimizer.getIterations());
    }

    // updateJacobian(): jacobian.length != rows -> FunctionEvaluationException
    @Test
    public void testUpdateJacobian_viaOptimize_dimensionMismatch_throwsFunctionEvaluationException() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f = new BadJacobianDimFunction();
        try {
            optimizer.optimize(f, new double[] {1.0}, new double[] {1.0}, new double[] {0.0});
            fail("expected FunctionEvaluationException");
        } catch (FunctionEvaluationException expected) {
            // jacobian row count does not match number of target rows
        }
    }

    // updateResidualsAndCost(): objective.length != rows -> FunctionEvaluationException
    @Test
    public void testUpdateResidualsAndCost_viaOptimize_dimensionMismatch_throwsFunctionEvaluationException() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f = new BadValueDimFunction();
        try {
            optimizer.optimize(f, new double[] {1.0}, new double[] {1.0}, new double[] {0.0});
            fail("expected FunctionEvaluationException");
        } catch (FunctionEvaluationException expected) {
            // objective value length does not match number of target rows
        }
    }

    // updateResidualsAndCost(): objectiveEvaluations exceeds maxEvaluations -> FunctionEvaluationException
    @Test
    public void testUpdateResidualsAndCost_viaOptimize_maxEvaluationsExceeded_throwsFunctionEvaluationException() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        optimizer.setMaxEvaluations(0);
        DifferentiableMultivariateVectorialFunction f = new LinearFunction(new double[][] { {1.0} });
        try {
            optimizer.optimize(f, new double[] {1.0}, new double[] {1.0}, new double[] {0.0});
            fail("expected FunctionEvaluationException");
        } catch (FunctionEvaluationException expected) {
            // evaluation count exceeds the configured maximum
        }
    }

    // incrementIterationsCounter(): within limit, no exception, counter advances
    @Test
    public void testIncrementIterationsCounter_withinLimit_noExceptionAndCounterIncremented() throws Throwable {
        IterationCountingOptimizer optimizer = new IterationCountingOptimizer();
        optimizer.setMaxIterations(5);
        DifferentiableMultivariateVectorialFunction f = new LinearFunction(new double[][] { {1.0} });
        optimizer.optimize(f, new double[] {1.0}, new double[] {1.0}, new double[] {0.0});
        assertEquals(1, optimizer.getIterations());
    }

    // incrementIterationsCounter(): exceeds limit -> OptimizationException
    @Test
    public void testIncrementIterationsCounter_exceedsLimit_throwsOptimizationException() throws Throwable {
        IterationCountingOptimizer optimizer = new IterationCountingOptimizer();
        optimizer.setMaxIterations(0);
        DifferentiableMultivariateVectorialFunction f = new LinearFunction(new double[][] { {1.0} });
        try {
            optimizer.optimize(f, new double[] {1.0}, new double[] {1.0}, new double[] {0.0});
            fail("expected OptimizationException");
        } catch (OptimizationException expected) {
            // iteration count exceeds the configured maximum
        }
    }

    // getRMS(): sqrt(sum(weight*residual^2)/rows)
    @Test
    public void testGetRMS_computesSqrtOfWeightedMeanSquareResidual() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f =
            new LinearFunction(new double[][] { {1.0}, {1.0} });
        optimizer.optimize(f, new double[] {3.0, 5.0}, new double[] {1.0, 2.0}, new double[] {0.0});
        double criterion = 1.0 * 3.0 * 3.0 + 2.0 * 5.0 * 5.0;
        double expectedRms = Math.sqrt(criterion / 2.0);
        assertEquals(expectedRms, optimizer.getRMS(), 1e-9);
    }

    // getChiSquare(): contract says variance_i = 1/weight_i, so chiSquare = sum(residual^2 * weight)
    @Test
    public void testGetChiSquare_matchesContractFormula_weightedSumOfSquares() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f =
            new LinearFunction(new double[][] { {1.0}, {1.0} });
        optimizer.optimize(f, new double[] {3.0, 5.0}, new double[] {1.0, 2.0}, new double[] {0.0});
        double expectedChiSquare = 1.0 * 3.0 * 3.0 + 2.0 * 5.0 * 5.0;
        assertEquals(expectedChiSquare, optimizer.getChiSquare(), 1e-9);
    }

    // getCovariances(): well-conditioned jTj, inverse of 1x1 matrix [3] is 1/3
    @Test
    public void testGetCovariances_wellConditioned_returnsInverseOfJtJ() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f =
            new LinearFunction(new double[][] { {1.0}, {1.0} });
        optimizer.optimize(f, new double[] {3.0, 5.0}, new double[] {1.0, 2.0}, new double[] {0.0});
        double[][] covar = optimizer.getCovariances();
        assertEquals(1.0 / 3.0, covar[0][0], 1e-9);
    }

    // getCovariances(): linearly dependent jacobian columns -> singular jTj -> OptimizationException
    @Test
    public void testGetCovariances_singularMatrix_throwsOptimizationException() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f =
            new LinearFunction(new double[][] { {1.0, 1.0}, {2.0, 2.0} });
        optimizer.optimize(f, new double[] {1.0, 2.0}, new double[] {1.0, 1.0}, new double[] {0.0, 0.0});
        try {
            optimizer.getCovariances();
            fail("expected OptimizationException");
        } catch (OptimizationException expected) {
            // jTj is singular because jacobian columns are proportional
        }
    }

    // guessParametersErrors(): rows <= cols -> OptimizationException (no degrees of freedom)
    @Test
    public void testGuessParametersErrors_rowsLessOrEqualCols_throwsOptimizationException() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f =
            new LinearFunction(new double[][] { {1.0, 1.0} });
        optimizer.optimize(f, new double[] {1.0}, new double[] {1.0}, new double[] {0.0, 0.0});
        try {
            optimizer.guessParametersErrors();
            fail("expected OptimizationException");
        } catch (OptimizationException expected) {
            // rows (1) is not greater than cols (2)
        }
    }

    // guessParametersErrors(): errors[i] = sqrt(covar[i][i]) * sqrt(chiSquare/(rows-cols)) per contract
    @Test
    public void testGuessParametersErrors_sufficientDegreesOfFreedom_matchesContractFormula() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f =
            new LinearFunction(new double[][] { {1.0}, {1.0} });
        optimizer.optimize(f, new double[] {3.0, 5.0}, new double[] {1.0, 2.0}, new double[] {0.0});
        double[] errors = optimizer.guessParametersErrors();
        double expectedChiSquare = 1.0 * 3.0 * 3.0 + 2.0 * 5.0 * 5.0;
        double expectedC = Math.sqrt(expectedChiSquare / (2 - 1));
        double expectedError0 = Math.sqrt(1.0 / 3.0) * expectedC;
        assertEquals(expectedError0, errors[0], 1e-9);
    }

    // getJacobianEvaluations(): incremented once per updateJacobian() call, including getCovariances()
    @Test
    public void testGetJacobianEvaluations_afterOptimizeAndCovariances_incrementsEachCall() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f =
            new LinearFunction(new double[][] { {1.0}, {1.0} });
        optimizer.optimize(f, new double[] {3.0, 5.0}, new double[] {1.0, 2.0}, new double[] {0.0});
        assertEquals(1, optimizer.getJacobianEvaluations());
        optimizer.getCovariances();
        assertEquals(2, optimizer.getJacobianEvaluations());
    }

    // getEvaluations(): incremented once per updateResidualsAndCost() call
    @Test
    public void testGetEvaluations_afterOptimize_incrementedOnce() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f = new LinearFunction(new double[][] { {1.0} });
        optimizer.optimize(f, new double[] {1.0}, new double[] {1.0}, new double[] {0.0});
        assertEquals(1, optimizer.getEvaluations());
    }

    // getIterations(): zero before any optimize() call
    @Test
    public void testGetIterations_beforeAnyOptimizeCall_isZero() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        assertEquals(0, optimizer.getIterations());
    }

    // getMaxIterations(): default value is the documented constant
    @Test
    public void testGetMaxIterations_defaultValue_equalsHundred() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        assertEquals(100, optimizer.getMaxIterations());
    }

    // single row / single column optimize(): cost = sqrt(weight * residual^2)
    @Test
    public void testOptimize_singleRowSingleCol_computesCostCorrectly() throws Throwable {
        SimpleOptimizer optimizer = new SimpleOptimizer();
        DifferentiableMultivariateVectorialFunction f = new LinearFunction(new double[][] { {2.0} });
        optimizer.optimize(f, new double[] {10.0}, new double[] {3.0}, new double[] {1.0});
        double expectedCost = Math.sqrt(3.0 * 8.0 * 8.0);
        assertEquals(expectedCost, optimizer.cost, 1e-9);
    }
}
