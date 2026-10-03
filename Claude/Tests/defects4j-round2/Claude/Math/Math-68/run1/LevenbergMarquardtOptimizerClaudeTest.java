package org.apache.commons.math.optimization.general;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.DifferentiableMultivariateVectorialFunction;
import org.apache.commons.math.analysis.MultivariateMatrixFunction;
import org.apache.commons.math.optimization.OptimizationException;
import org.apache.commons.math.optimization.VectorialPointValuePair;

public class LevenbergMarquardtOptimizerClaudeTest {

    private LevenbergMarquardtOptimizer optimizer;

    @Before
    public void setUp() throws Throwable {
        optimizer = new LevenbergMarquardtOptimizer();
    }

    /** builds a linear model y = factors * variables, jacobian is constant = factors */
    private DifferentiableMultivariateVectorialFunction buildLinearModel(final double[][] factors) {
        return new DifferentiableMultivariateVectorialFunction() {
            public double[] value(double[] variables) {
                double[] y = new double[factors.length];
                for (int i = 0; i < y.length; ++i) {
                    double yi = 0;
                    for (int j = 0; j < variables.length; ++j) {
                        yi += factors[i][j] * variables[j];
                    }
                    y[i] = yi;
                }
                return y;
            }
            public MultivariateMatrixFunction jacobian() {
                return new MultivariateMatrixFunction() {
                    public double[][] value(double[] point) {
                        return factors;
                    }
                };
            }
        };
    }

    // covers: constructor sets up default tuning parameters without error
    @Test
    public void testConstructor_defaultInstance_notNull() throws Throwable {
        LevenbergMarquardtOptimizer fresh = new LevenbergMarquardtOptimizer();
        assertNotNull(fresh);
    }

    // covers: cost==0 branch skips maxCosine loop -> immediate return, point unchanged
    @Test
    public void testOptimize_zeroResidualAtStart_returnsStartPointUnchanged() throws Throwable {
        double[][] factors = { {1, 0}, {0, 1} };
        double[] target = {3, -2};
        double[] start = {3, -2};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1}, start);
        assertEquals(3.0, result.getPoint()[0], 1e-12);
        assertEquals(-2.0, result.getPoint()[1], 1e-12);
    }

    // covers: cost!=0 but maxCosine<=orthoTolerance (large tolerance) -> immediate return, point unchanged
    @Test
    public void testSetOrthoTolerance_largeValue_convergesImmediatelyAtStartPoint() throws Throwable {
        optimizer.setOrthoTolerance(2.0);
        double[][] factors = { {1} };
        double[] target = {100};
        double[] start = {0};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1}, start);
        assertEquals(0.0, result.getPoint()[0], 1e-12);
        assertEquals(0.0, result.getValue()[0], 1e-12);
    }

    // covers: trivial 1D exact solve, basic convergence path
    @Test
    public void testOptimize_trivialOneDimensional_convergesToExactSolution() throws Throwable {
        double[][] factors = { {2} };
        double[] target = {3};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1}, new double[] {0});
        assertEquals(1.5, result.getPoint()[0], 1e-6);
    }

    // covers: negative jacobian coefficient, akk<0 path possible in Householder alpha choice
    @Test
    public void testOptimize_negativeCoefficientOneDimensional_convergesToExactSolution() throws Throwable {
        double[][] factors = { {-3} };
        double[] target = {9};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1}, new double[] {0});
        assertEquals(-3.0, result.getPoint()[0], 1e-6);
    }

    // covers: exact square identity system, 2 unknowns
    @Test
    public void testOptimize_exactIdentitySystemTwoDimensions_convergesToExactTarget() throws Throwable {
        double[][] factors = { {1, 0}, {0, 1} };
        double[] target = {2, 3};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1}, new double[] {0, 0});
        assertEquals(2.0, result.getPoint()[0], 1e-6);
        assertEquals(3.0, result.getPoint()[1], 1e-6);
    }

    // covers: exact square identity system, 3 unknowns (multiple solvedCols loop iterations)
    @Test
    public void testOptimize_threeDimensionalExactSystem_convergesToExactSolution() throws Throwable {
        double[][] factors = { {1, 0, 0}, {0, 1, 0}, {0, 0, 1} };
        double[] target = {1, -2, 3};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1, 1}, new double[] {0, 0, 0});
        assertEquals(1.0, result.getPoint()[0], 1e-6);
        assertEquals(-2.0, result.getPoint()[1], 1e-6);
        assertEquals(3.0, result.getPoint()[2], 1e-6);
    }

    // covers: diagonal system with distinct non-unit column norms, exercises jacNorm scaling per column
    @Test
    public void testOptimize_diagonalSystemDistinctCoefficients_convergesToExactSolution() throws Throwable {
        double[][] factors = { {2, 0, 0}, {0, 3, 0}, {0, 0, 4} };
        double[] target = {6, 12, 20};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1, 1}, new double[] {0, 0, 0});
        assertEquals(3.0, result.getPoint()[0], 1e-6);
        assertEquals(4.0, result.getPoint()[1], 1e-6);
        assertEquals(5.0, result.getPoint()[2], 1e-6);
    }

    // covers: coupled square system (non-diagonal), exact unique solution via linear algebra
    @Test
    public void testOptimize_coupledSquareSystem_convergesToExactSolution() throws Throwable {
        double[][] factors = { {1, 1}, {1, -1} };
        double[] target = {4, 2};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1}, new double[] {0, 0});
        assertEquals(3.0, result.getPoint()[0], 1e-6);
        assertEquals(1.0, result.getPoint()[1], 1e-6);
    }

    // covers: over-determined (3 eq, 1 unknown) least squares solution matches normal equations a = sum(xy)/sum(x^2)
    @Test
    public void testOptimize_overDeterminedThreePointsOneUnknown_matchesNormalEquationSolution() throws Throwable {
        double[][] factors = { {1}, {2}, {3} };
        double[] target = {2, 4, 7};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1, 1}, new double[] {0});
        double expected = 31.0 / 14.0;
        assertEquals(expected, result.getPoint()[0], 1e-6);
    }

    // covers: over-determined linear regression (4 eq, 2 unknowns) matches least squares normal equation solution
    @Test
    public void testOptimize_overDeterminedLinearRegressionTwoParameters_matchesNormalEquationSolution() throws Throwable {
        double[][] factors = { {1, 1}, {1, 2}, {1, 3}, {1, 4} };
        double[] target = {2.0, 2.9, 4.2, 5.0};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1, 1, 1}, new double[] {0, 0});
        assertEquals(0.95, result.getPoint()[0], 1e-6);
        assertEquals(1.03, result.getPoint()[1], 1e-6);
    }

    // covers: QR column pivoting (column with larger norm must be permuted first), consistent full rank over-determined system
    @Test
    public void testOptimize_qrColumnsPermutation_returnsExactUniqueSolution() throws Throwable {
        double[][] factors = { {1, -1}, {0, 2}, {1, -2} };
        double[] target = {4, 6, 1};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1, 1}, new double[] {0, 0});
        assertEquals(7.0, result.getPoint()[0], 1e-6);
        assertEquals(3.0, result.getPoint()[1], 1e-6);
    }

    // covers: rank-deficient consistent system (identical columns) -> ak2==0 branch in QR, parl stays 0 in LM parameter
    @Test
    public void testOptimize_rankDeficientConsistentSystem_convergesToZeroResidual() throws Throwable {
        double[][] factors = { {1, 1}, {1, 1} };
        double[] target = {2, 2};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1}, new double[] {0, 0});
        assertEquals(2.0, result.getValue()[0], 1e-6);
        assertEquals(2.0, result.getValue()[1], 1e-6);
    }

    // covers: NaN propagating through jacobian column norm triggers OptimizationException in qrDecomposition
    @Test
    public void testOptimize_jacobianContainsNaN_throwsOptimizationException() throws Throwable {
        double[][] factors = { {1, 1}, {Double.NaN, 1} };
        double[] target = {1, 1};
        try {
            optimizer.optimize(buildLinearModel(factors), target, new double[] {1, 1}, new double[] {0, 0});
            fail("expected OptimizationException");
        } catch (OptimizationException expected) {
            // expected: unable to perform Q.R decomposition
        }
    }

    // covers: setInitialStepBoundFactor with a small factor, ternary branch xNorm!=0 still converges
    @Test
    public void testSetInitialStepBoundFactor_smallValue_stillConvergesToExactSolution() throws Throwable {
        optimizer.setInitialStepBoundFactor(0.1);
        double[][] factors = { {1} };
        double[] target = {5};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1}, new double[] {1});
        assertEquals(5.0, result.getPoint()[0], 1e-6);
    }

    // covers: setInitialStepBoundFactor with a large factor, still converges without exception
    @Test
    public void testSetInitialStepBoundFactor_largeValue_stillConvergesToExactSolution() throws Throwable {
        optimizer.setInitialStepBoundFactor(1000.0);
        double[][] factors = { {1} };
        double[] target = {5};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1}, new double[] {1});
        assertEquals(5.0, result.getPoint()[0], 1e-6);
    }

    // covers: tight cost relative tolerance still produces a precise converged result
    @Test
    public void testSetCostRelativeTolerance_tightValue_convergesPrecisely() throws Throwable {
        optimizer.setCostRelativeTolerance(1e-12);
        double[][] factors = { {1, 0}, {0, 1} };
        double[] target = {7, -5};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1}, new double[] {0, 0});
        assertEquals(7.0, result.getPoint()[0], 1e-6);
        assertEquals(-5.0, result.getPoint()[1], 1e-6);
    }

    // covers: tight parameters relative tolerance still produces a precise converged result
    @Test
    public void testSetParRelativeTolerance_tightValue_convergesPrecisely() throws Throwable {
        optimizer.setParRelativeTolerance(1e-12);
        double[][] factors = { {1, 0}, {0, 1} };
        double[] target = {7, -5};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1}, new double[] {0, 0});
        assertEquals(7.0, result.getPoint()[0], 1e-6);
        assertEquals(-5.0, result.getPoint()[1], 1e-6);
    }

    // covers: starting point very far from the solution still converges, multiple inner/outer iterations
    @Test
    public void testOptimize_startFarFromSolution_stillConverges() throws Throwable {
        double[][] factors = { {1} };
        double[] target = {1000};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1}, new double[] {0});
        assertEquals(1000.0, result.getPoint()[0], 1e-4);
    }

    // covers: negative starting point far from positive solution
    @Test
    public void testOptimize_negativeStartPoint_convergesToExactSolution() throws Throwable {
        double[][] factors = { {1} };
        double[] target = {5};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1}, new double[] {-100});
        assertEquals(5.0, result.getPoint()[0], 1e-6);
    }

    // covers: converging to a zero target/point
    @Test
    public void testOptimize_singleEquationZeroTarget_convergesToZero() throws Throwable {
        double[][] factors = { {1} };
        double[] target = {0};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1}, new double[] {5});
        assertEquals(0.0, result.getPoint()[0], 1e-6);
        assertEquals(0.0, result.getValue()[0], 1e-6);
    }

    // covers: negative target values on an identity system
    @Test
    public void testOptimize_identityWithNegativeTarget_convergesToExactSolution() throws Throwable {
        double[][] factors = { {1, 0}, {0, 1} };
        double[] target = {-4, -7};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1}, new double[] {0, 0});
        assertEquals(-4.0, result.getPoint()[0], 1e-6);
        assertEquals(-7.0, result.getPoint()[1], 1e-6);
    }

    // covers: same optimizer instance reused across two different problems with different dimensions
    @Test
    public void testOptimize_reuseOptimizerForSecondProblem_convergesIndependently() throws Throwable {
        VectorialPointValuePair first = optimizer.optimize(buildLinearModel(new double[][] {{1}}),
                new double[] {2}, new double[] {1}, new double[] {0});
        assertEquals(2.0, first.getPoint()[0], 1e-6);

        VectorialPointValuePair second = optimizer.optimize(
                buildLinearModel(new double[][] {{1, 0}, {0, 1}}),
                new double[] {5, 6}, new double[] {1, 1}, new double[] {0, 0});
        assertEquals(5.0, second.getPoint()[0], 1e-6);
        assertEquals(6.0, second.getPoint()[1], 1e-6);
    }

    // covers: another over-determined regression dataset to broaden QR/LM-parameter coverage
    @Test
    public void testOptimize_fourPointsTwoUnknownsDifferentData_matchesNormalEquationSolution() throws Throwable {
        double[][] factors = { {1, 0}, {1, 1}, {1, 2}, {1, 3} };
        double[] target = {1.0, 3.0, 5.0, 7.0};
        VectorialPointValuePair result = optimizer.optimize(buildLinearModel(factors), target,
                new double[] {1, 1, 1, 1}, new double[] {0, 0});
        assertEquals(1.0, result.getPoint()[0], 1e-6);
        assertEquals(2.0, result.getPoint()[1], 1e-6);
    }
}
