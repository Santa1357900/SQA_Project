package org.apache.commons.math.optimization.general;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.DifferentiableMultivariateVectorialFunction;
import org.apache.commons.math.analysis.MultivariateMatrixFunction;
import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.optimization.VectorialConvergenceChecker;
import org.apache.commons.math.optimization.VectorialPointValuePair;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class LevenbergMarquardtOptimizerClaudeTest {

    private LevenbergMarquardtOptimizer optimizer;

    @Before
    public void setUp() throws Throwable {
        optimizer = new LevenbergMarquardtOptimizer();
    }

    private DifferentiableMultivariateVectorialFunction buildLinearFunction(final double[] x) {
        return new DifferentiableMultivariateVectorialFunction() {
            public double[] value(double[] point) throws FunctionEvaluationException {
                double[] v = new double[x.length];
                for (int i = 0; i < x.length; i++) {
                    v[i] = point[0] * x[i] + point[1];
                }
                return v;
            }
            public MultivariateMatrixFunction jacobian() {
                return new MultivariateMatrixFunction() {
                    public double[][] value(double[] point) throws FunctionEvaluationException {
                        double[][] jac = new double[x.length][2];
                        for (int i = 0; i < x.length; i++) {
                            jac[i][0] = x[i];
                            jac[i][1] = 1.0;
                        }
                        return jac;
                    }
                };
            }
        };
    }

    private DifferentiableMultivariateVectorialFunction buildSingleParamFunction(final double factor) {
        return new DifferentiableMultivariateVectorialFunction() {
            public double[] value(double[] point) throws FunctionEvaluationException {
                return new double[] { point[0] * factor };
            }
            public MultivariateMatrixFunction jacobian() {
                return new MultivariateMatrixFunction() {
                    public double[][] value(double[] point) throws FunctionEvaluationException {
                        return new double[][] { { factor } };
                    }
                };
            }
        };
    }

    private DifferentiableMultivariateVectorialFunction buildNaNJacobianFunction() {
        return new DifferentiableMultivariateVectorialFunction() {
            public double[] value(double[] point) throws FunctionEvaluationException {
                return new double[] { point[0] };
            }
            public MultivariateMatrixFunction jacobian() {
                return new MultivariateMatrixFunction() {
                    public double[][] value(double[] point) throws FunctionEvaluationException {
                        return new double[][] { { Double.NaN } };
                    }
                };
            }
        };
    }

    // covers exactly determined 2x2 linear system, full convergence to exact solution
    @Test
    public void testOptimize_twoPointExactLinearFit_returnsExactSolution() throws Throwable {
        double[] x = { 0.0, 1.0 };
        double[] target = { 1.0, 3.0 };
        double[] weights = { 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(2.0, result.getPoint()[0], 1e-6);
        assertEquals(1.0, result.getPoint()[1], 1e-6);
    }

    // covers over-determined but exactly fittable system (cost reduces to zero)
    @Test
    public void testOptimize_threeCollinearPoints_zeroCost() throws Throwable {
        double[] x = { 0.0, 1.0, 2.0 };
        double[] target = { 1.0, 3.0, 5.0 };
        double[] weights = { 1.0, 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(2.0, result.getPoint()[0], 1e-6);
        assertEquals(1.0, result.getPoint()[1], 1e-6);
    }

    // covers over-determined non-exact fit, oracle = closed-form ordinary least squares
    @Test
    public void testOptimize_threeNonCollinearPoints_matchesOrdinaryLeastSquares() throws Throwable {
        double[] x = { 0.0, 1.0, 2.0 };
        double[] target = { 0.0, 1.0, 3.0 };
        double[] weights = { 1.0, 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(1.5, result.getPoint()[0], 1e-4);
        assertEquals(-1.0 / 6.0, result.getPoint()[1], 1e-4);
    }

    // covers xNorm == 0 branch on first iteration scaling (start point all zero)
    @Test
    public void testOptimize_startAtZero_xNormZeroBranch_exactFit() throws Throwable {
        double[] x = { 0.0, 2.0 };
        double[] target = { -1.0, 3.0 };
        double[] weights = { 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(2.0, result.getPoint()[0], 1e-6);
        assertEquals(-1.0, result.getPoint()[1], 1e-6);
    }

    // covers cost == 0 skip-branch when the starting point is already the exact solution
    @Test
    public void testOptimize_startAtExactSolution_remainsAtSolution() throws Throwable {
        double[] x = { 0.0, 1.0, 2.0 };
        double[] target = { 1.0, 3.0, 5.0 };
        double[] weights = { 1.0, 1.0, 1.0 };
        double[] start = { 2.0, 1.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(2.0, result.getPoint()[0], 1e-9);
        assertEquals(1.0, result.getPoint()[1], 1e-9);
    }

    // covers setInitialStepBoundFactor with a small factor, still exact convergence
    @Test
    public void testSetInitialStepBoundFactor_smallFactor_stillConverges() throws Throwable {
        optimizer.setInitialStepBoundFactor(1.0);
        double[] x = { 0.0, 1.0 };
        double[] target = { 4.0, 7.0 };
        double[] weights = { 1.0, 1.0 };
        double[] start = { 10.0, -5.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(3.0, result.getPoint()[0], 1e-4);
        assertEquals(4.0, result.getPoint()[1], 1e-4);
    }

    // covers setInitialStepBoundFactor with a large factor, still exact convergence
    @Test
    public void testSetInitialStepBoundFactor_largeFactor_stillConverges() throws Throwable {
        optimizer.setInitialStepBoundFactor(1000.0);
        double[] x = { 0.0, 1.0 };
        double[] target = { 4.0, 7.0 };
        double[] weights = { 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(3.0, result.getPoint()[0], 1e-4);
        assertEquals(4.0, result.getPoint()[1], 1e-4);
    }

    // covers loose cost relative tolerance, still converges near the exact minimum
    @Test
    public void testSetCostRelativeTolerance_looseValue_stillReachesExactFit() throws Throwable {
        optimizer.setCostRelativeTolerance(1.0e-3);
        double[] x = { 0.0, 1.0, 2.0 };
        double[] target = { 1.0, 3.0, 5.0 };
        double[] weights = { 1.0, 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(2.0, result.getPoint()[0], 1e-2);
        assertEquals(1.0, result.getPoint()[1], 1e-2);
    }

    // covers loose parameter relative tolerance, still converges near the exact minimum
    @Test
    public void testSetParRelativeTolerance_looseValue_stillReachesExactFit() throws Throwable {
        optimizer.setParRelativeTolerance(1.0e-3);
        double[] x = { 0.0, 1.0, 2.0 };
        double[] target = { 1.0, 3.0, 5.0 };
        double[] weights = { 1.0, 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(2.0, result.getPoint()[0], 1e-2);
        assertEquals(1.0, result.getPoint()[1], 1e-2);
    }

    // covers non-default orthogonality tolerance, still converges near the exact minimum
    @Test
    public void testSetOrthoTolerance_nonDefaultValue_stillReachesExactFit() throws Throwable {
        optimizer.setOrthoTolerance(1.0e-6);
        double[] x = { 0.0, 1.0, 2.0 };
        double[] target = { 1.0, 3.0, 5.0 };
        double[] weights = { 1.0, 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(2.0, result.getPoint()[0], 1e-4);
        assertEquals(1.0, result.getPoint()[1], 1e-4);
    }

    // covers explicit zero QR ranking threshold (edge value), full rank retained
    @Test
    public void testSetQRRankingThreshold_zero_stillConverges() throws Throwable {
        optimizer.setQRRankingThreshold(0.0);
        double[] x = { 0.0, 1.0 };
        double[] target = { 5.0, 2.0 };
        double[] weights = { 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(-3.0, result.getPoint()[0], 1e-6);
        assertEquals(5.0, result.getPoint()[1], 1e-6);
    }

    // covers rank == 0 branch in qrDecomposition (column norm <= threshold): no progress possible
    @Test
    public void testSetQRRankingThreshold_veryLarge_forcesRankZero_pointUnchanged() throws Throwable {
        optimizer.setQRRankingThreshold(1.0e6);
        double[] x = { 0.0, 1.0, 2.0 };
        double[] target = { 1.0, 3.0, 5.0 };
        double[] weights = { 1.0, 1.0, 1.0 };
        double[] start = { 3.0, -2.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(3.0, result.getPoint()[0], 1e-9);
        assertEquals(-2.0, result.getPoint()[1], 1e-9);
    }

    // covers single parameter, single equation system (solvedCols == 1 == rows)
    @Test
    public void testOptimize_singleParameterSystem_exactSolution() throws Throwable {
        double[] target = { 10.0 };
        double[] weights = { 1.0 };
        double[] start = { 1.0 };
        VectorialPointValuePair result = optimizer.optimize(buildSingleParamFunction(2.0), target, weights, start);
        assertEquals(5.0, result.getPoint()[0], 1e-6);
    }

    // covers checker != null branch, using a convergence checker that always returns true
    @Test
    public void testOptimize_withCustomConvergenceChecker_alwaysConverged_oneStepExact() throws Throwable {
        optimizer.setConvergenceChecker(new VectorialConvergenceChecker() {
            public boolean converged(int iteration, VectorialPointValuePair previous, VectorialPointValuePair current) {
                return true;
            }
        });
        double[] x = { 0.0, 1.0 };
        double[] target = { 1.0, 3.0 };
        double[] weights = { 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(2.0, result.getPoint()[0], 1e-6);
        assertEquals(1.0, result.getPoint()[1], 1e-6);
    }

    // covers weighted residuals: exact fit must hold regardless of weight magnitudes
    @Test
    public void testOptimize_weightedResiduals_exactFitIndependentOfWeights() throws Throwable {
        double[] x = { 0.0, 1.0, 2.0 };
        double[] target = { 1.0, 3.0, 5.0 };
        double[] weights = { 5.0, 1.0, 10.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(2.0, result.getPoint()[0], 1e-5);
        assertEquals(1.0, result.getPoint()[1], 1e-5);
    }

    // covers four-point over-determined dataset, independent OLS verification
    @Test
    public void testOptimize_fourPointsOverdetermined_matchesOLS() throws Throwable {
        double[] x = { 0.0, 1.0, 2.0, 3.0 };
        double[] target = { 1.0, 2.0, 2.0, 4.0 };
        double[] weights = { 1.0, 1.0, 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(0.9, result.getPoint()[0], 1e-4);
        assertEquals(0.9, result.getPoint()[1], 1e-4);
    }

    // covers convergence to a negative slope
    @Test
    public void testOptimize_negativeSlope_exactFit() throws Throwable {
        double[] x = { 0.0, 1.0, 2.0 };
        double[] target = { 5.0, 2.0, -1.0 };
        double[] weights = { 1.0, 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(-3.0, result.getPoint()[0], 1e-6);
        assertEquals(5.0, result.getPoint()[1], 1e-6);
    }

    // covers the explicit NaN/infinite column norm check in qrDecomposition -> OptimizationException
    @Test
    public void testOptimize_jacobianNaN_throwsOptimizationException() throws Throwable {
        double[] target = { 0.0 };
        double[] weights = { 1.0 };
        double[] start = { 1.0 };
        try {
            optimizer.optimize(buildNaNJacobianFunction(), target, weights, start);
            fail("expected OptimizationException");
        } catch (OptimizationException expected) {
        }
    }

    // covers an exact proportional (zero intercept) model starting away from the solution
    @Test
    public void testOptimize_zeroIntercept_exactFit() throws Throwable {
        double[] x = { 1.0, 2.0, 3.0 };
        double[] target = { 4.0, 8.0, 12.0 };
        double[] weights = { 1.0, 1.0, 1.0 };
        double[] start = { 1.0, 1.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(4.0, result.getPoint()[0], 1e-5);
        assertEquals(0.0, result.getPoint()[1], 1e-5);
    }

    // covers convergence when starting far from the solution (large initial residual)
    @Test
    public void testOptimize_largeStartPointFarFromSolution_stillConverges() throws Throwable {
        double[] x = { 0.0, 1.0, 2.0 };
        double[] target = { 1.0, 3.0, 5.0 };
        double[] weights = { 1.0, 1.0, 1.0 };
        double[] start = { 1000.0, -500.0 };
        VectorialPointValuePair result = optimizer.optimize(buildLinearFunction(x), target, weights, start);
        assertEquals(2.0, result.getPoint()[0], 1e-4);
        assertEquals(1.0, result.getPoint()[1], 1e-4);
    }

    // covers single equation, single unknown with a negative target value
    @Test
    public void testOptimize_singleEquationNegativeTarget_exactSolution() throws Throwable {
        double[] target = { -20.0 };
        double[] weights = { 1.0 };
        double[] start = { 0.0 };
        VectorialPointValuePair result = optimizer.optimize(buildSingleParamFunction(4.0), target, weights, start);
        assertEquals(-5.0, result.getPoint()[0], 1e-6);
    }

    // covers the invariant that the returned pair's value equals the model evaluated at its point
    @Test
    public void testOptimize_resultValueMatchesModelAtResultPoint() throws Throwable {
        double[] x = { 0.0, 1.0, 2.0 };
        double[] target = { 1.0, 3.0, 5.0 };
        double[] weights = { 1.0, 1.0, 1.0 };
        double[] start = { 0.0, 0.0 };
        DifferentiableMultivariateVectorialFunction f = buildLinearFunction(x);
        VectorialPointValuePair result = optimizer.optimize(f, target, weights, start);
        double[] modelValue = f.value(result.getPoint());
        assertArrayEquals(modelValue, result.getValue(), 1e-6);
    }

    // covers reusing the same optimizer instance across two independent optimize calls
    @Test
    public void testOptimize_sameOptimizerInstanceReused_secondCallIndependent() throws Throwable {
        double[] x1 = { 0.0, 1.0 };
        optimizer.optimize(buildLinearFunction(x1), new double[] { 1.0, 3.0 }, new double[] { 1.0, 1.0 }, new double[] { 0.0, 0.0 });
        double[] x2 = { 0.0, 1.0 };
        VectorialPointValuePair result2 = optimizer.optimize(buildLinearFunction(x2), new double[] { 5.0, 2.0 }, new double[] { 1.0, 1.0 }, new double[] { 0.0, 0.0 });
        assertEquals(-3.0, result2.getPoint()[0], 1e-6);
        assertEquals(5.0, result2.getPoint()[1], 1e-6);
    }
}
