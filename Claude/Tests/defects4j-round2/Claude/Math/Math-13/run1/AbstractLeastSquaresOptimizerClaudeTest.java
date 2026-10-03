package org.apache.commons.math3.optimization.general;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.commons.math3.analysis.differentiation.MultivariateDifferentiableVectorFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.optimization.PointVectorValuePair;

public class AbstractLeastSquaresOptimizerClaudeTest {

    /** Minimal concrete optimizer used only to exercise the protected/public API. */
    private static class DummyOptimizer extends AbstractLeastSquaresOptimizer {
        DummyOptimizer() {
            super(null);
        }
        protected PointVectorValuePair doOptimize() {
            updateJacobian();
            updateResidualsAndCost();
            return null;
        }
    }

    /** Subclass exercising the deprecated no-arg constructor. */
    private static class DummyOptimizerDefault extends AbstractLeastSquaresOptimizer {
        DummyOptimizerDefault() {
            super();
        }
        protected PointVectorValuePair doOptimize() {
            return null;
        }
    }

    /** Model: 1 parameter -> 2 outputs. output[0]=identity(param0), output[1]=constant 2.0. */
    private static class TwoOutputsOneParamModel implements MultivariateDifferentiableVectorFunction {
        public DerivativeStructure[] value(DerivativeStructure[] point) {
            DerivativeStructure[] result = new DerivativeStructure[2];
            result[0] = point[0];
            result[1] = new DerivativeStructure(1, 1, 0, 2.0);
            return result;
        }
    }

    /** Model: 2 parameters -> 2 outputs, both identity (square system, rows==cols). */
    private static class SquareIdentityModel implements MultivariateDifferentiableVectorFunction {
        public DerivativeStructure[] value(DerivativeStructure[] point) {
            DerivativeStructure[] result = new DerivativeStructure[2];
            result[0] = point[0];
            result[1] = point[1];
            return result;
        }
    }

    /** Model that always returns length-2 output, regardless of the target's declared length. */
    private static class FixedLengthTwoModel implements MultivariateDifferentiableVectorFunction {
        public DerivativeStructure[] value(DerivativeStructure[] point) {
            DerivativeStructure[] result = new DerivativeStructure[2];
            result[0] = point[0];
            result[1] = new DerivativeStructure(point.length, 1, 0, 3.0);
            return result;
        }
    }

    private DummyOptimizer optimizer;

    @Before
    public void setUp() throws Throwable {
        optimizer = new DummyOptimizer();
    }

    // deprecated no-arg constructor: subclass is constructible, counter starts at zero
    @Test
    public void testDefaultConstructor_freshInstance_hasZeroJacobianEvaluations() throws Throwable {
        DummyOptimizerDefault d = new DummyOptimizerDefault();
        assertEquals(0, d.getJacobianEvaluations());
    }

    // getJacobianEvaluations(): fresh optimizer has never evaluated the jacobian
    @Test
    public void testGetJacobianEvaluations_beforeAnyCall_returnsZero() throws Throwable {
        assertEquals(0, optimizer.getJacobianEvaluations());
    }

    // optimize(...) runs setUp() (reset to 0) then doOptimize() once -> exactly one evaluation
    @Test
    public void testOptimize_runsSetUpAndDoOptimizeOnce_jacobianEvaluationsIsOne() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        assertEquals(1, optimizer.getJacobianEvaluations());
    }

    // updateJacobian(): populates the deprecated field with the NEGATED weighted jacobian
    @Test
    public void testUpdateJacobian_populatesWeightedResidualJacobianField() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        assertNotNull(optimizer.weightedResidualJacobian);
        assertEquals(2, optimizer.weightedResidualJacobian.length);
        assertEquals(1, optimizer.weightedResidualJacobian[0].length);
        assertEquals(-1.0, optimizer.weightedResidualJacobian[0][0], 1e-9);
        assertEquals(-1.0, optimizer.weightedResidualJacobian[1][0], 1e-9);
    }

    // computeWeightedJacobian(): every direct call increments the evaluation counter
    @Test
    public void testComputeWeightedJacobian_calledTwice_evaluationsCounterIncrementsEachTime() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        int before = optimizer.getJacobianEvaluations();
        optimizer.computeWeightedJacobian(new double[]{1.0});
        assertEquals(before + 1, optimizer.getJacobianEvaluations());
    }

    // computeWeightedJacobian(): model output length mismatching target length throws
    @Test
    public void testComputeWeightedJacobian_outputLengthMismatchesTarget_throwsDimensionMismatch() throws Throwable {
        double[] target = {1.0, 2.0, 3.0};
        double[] weights = {1.0, 1.0, 1.0};
        double[] start = {1.0};
        try {
            optimizer.optimize(100, new FixedLengthTwoModel(), target, weights, start);
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
            // ok
        }
    }

    // computeWeightedJacobian(): returns W^(1/2) * J with correct numeric values
    @Test
    public void testComputeWeightedJacobian_returnsExpectedMatrixValues() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {4.0, 4.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        RealMatrix j = optimizer.computeWeightedJacobian(new double[]{1.0});
        assertEquals(2.0, j.getData()[0][0], 1e-9);
        assertEquals(2.0, j.getData()[1][0], 1e-9);
    }

    // updateResidualsAndCost(): objective/cost/weightedResiduals reflect model evaluated at point
    @Test
    public void testUpdateResidualsAndCost_computesObjectiveResidualsAndCost() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        assertEquals(1.0, optimizer.objective[0], 1e-9);
        assertEquals(2.0, optimizer.objective[1], 1e-9);
        assertEquals(0.0, optimizer.cost, 1e-9);
        assertEquals(0.0, optimizer.weightedResiduals[0], 1e-9);
        assertEquals(0.0, optimizer.weightedResiduals[1], 1e-9);
    }

    // computeCost(): sqrt of weighted sum of squares, with unit weights
    @Test
    public void testComputeCost_withResiduals_returnsSqrtOfWeightedSumOfSquares() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        double cost = optimizer.computeCost(new double[]{3.0, 4.0});
        assertEquals(5.0, cost, 1e-9);
    }

    // getRMS(): zero cost gives zero RMS
    @Test
    public void testGetRMS_zeroCost_returnsZero() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        assertEquals(0.0, optimizer.getRMS(), 1e-9);
    }

    // getRMS(): sqrt(chiSquare/rows) for a non-zero cost
    @Test
    public void testGetRMS_nonZeroCost_matchesSqrtChiSquareOverRows() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        optimizer.setCost(4.0);
        assertEquals(Math.sqrt(8.0), optimizer.getRMS(), 1e-9);
    }

    // getChiSquare(): cost squared
    @Test
    public void testGetChiSquare_afterSetCost_returnsCostSquared() throws Throwable {
        optimizer.setCost(3.0);
        assertEquals(9.0, optimizer.getChiSquare(), 1e-9);
    }

    // getWeightSquareRoot(): unit weights produce an identity-like square root matrix
    @Test
    public void testGetWeightSquareRoot_unitWeights_returnsIdentityValues() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        RealMatrix sqrtW = optimizer.getWeightSquareRoot();
        assertEquals(1.0, sqrtW.getData()[0][0], 1e-9);
        assertEquals(1.0, sqrtW.getData()[1][1], 1e-9);
        assertEquals(0.0, sqrtW.getData()[0][1], 1e-9);
    }

    // setCost(): directly mutates the protected cost field
    @Test
    public void testSetCost_directValue_fieldReflectsNewCost() throws Throwable {
        optimizer.setCost(2.5);
        assertEquals(2.5, optimizer.cost, 1e-9);
    }

    // getCovariances(): default threshold, J=[[1],[1]] => J^T J=[2] => covariance=[[0.5]]
    @Test
    public void testGetCovariances_defaultThreshold_returnsInverseOfJtJ() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        double[][] cov = optimizer.getCovariances();
        assertEquals(0.5, cov[0][0], 1e-9);
    }

    // getCovariances(threshold): explicit small threshold gives the same numeric result
    @Test
    public void testGetCovariances_explicitThreshold_matchesComputeCovariances() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        double[][] cov = optimizer.getCovariances(1e-10);
        assertEquals(0.5, cov[0][0], 1e-9);
    }

    // computeCovariances(params, threshold): direct call returns the expected inverse matrix
    @Test
    public void testComputeCovariances_directCall_returnsExpectedInverse() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        double[][] cov = optimizer.computeCovariances(new double[]{1.0}, 1e-14);
        assertEquals(0.5, cov[0][0], 1e-9);
    }

    // guessParametersErrors(): rows>cols and zero cost -> all errors are zero
    @Test
    public void testGuessParametersErrors_rowsGreaterThanCols_returnsZeroErrorWhenCostIsZero() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        double[] errors = optimizer.guessParametersErrors();
        assertEquals(1, errors.length);
        assertEquals(0.0, errors[0], 1e-9);
    }

    // guessParametersErrors(): rows<=cols (square system) must throw NumberIsTooSmallException
    @Test
    public void testGuessParametersErrors_rowsLessOrEqualCols_throwsNumberIsTooSmall() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0, 2.0};
        optimizer.optimize(100, new SquareIdentityModel(), target, weights, start);
        try {
            optimizer.guessParametersErrors();
            fail("expected NumberIsTooSmallException");
        } catch (NumberIsTooSmallException expected) {
            // ok
        }
    }

    // computeSigma(): sqrt of covariance diagonal, independent of cost
    @Test
    public void testComputeSigma_directCall_returnsSqrtOfCovarianceDiagonal() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        double[] sigma = optimizer.computeSigma(new double[]{1.0}, 1e-14);
        assertEquals(Math.sqrt(0.5), sigma[0], 1e-9);
    }

    // optimize(...): the base class must forward exactly the value produced by doOptimize()
    @Test
    public void testOptimize_returnsValueProducedByDoOptimize() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        PointVectorValuePair result = optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        assertNull(result);
    }

    // computeResiduals(): residual = target - objectiveValue, elementwise
    @Test
    public void testComputeResiduals_matchingLength_returnsTargetMinusObjective() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        double[] res = optimizer.computeResiduals(new double[]{0.4, 0.5});
        assertEquals(0.6, res[0], 1e-9);
        assertEquals(1.5, res[1], 1e-9);
    }

    // computeResiduals(): mismatched length throws DimensionMismatchException whose getDimension()
    // must report the WRONG (actual) length of the objectiveValue argument (here: 1), not the
    // target's length (2). This pins down the correct constructor-argument order of the exception.
    @Test
    public void testComputeResiduals_mismatchedLength_exceptionReportsWrongDimension() throws Throwable {
        double[] target = {1.0, 2.0};
        double[] weights = {1.0, 1.0};
        double[] start = {1.0};
        optimizer.optimize(100, new TwoOutputsOneParamModel(), target, weights, start);
        try {
            optimizer.computeResiduals(new double[]{5.0});
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
            assertEquals(1, expected.getDimension());
        }
    }
}
