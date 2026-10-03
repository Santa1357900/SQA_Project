package org.apache.commons.math3.optim.nonlinear.vector.jacobian;

import org.apache.commons.math3.analysis.MultivariateMatrixFunction;
import org.apache.commons.math3.analysis.MultivariateVectorFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointVectorValuePair;
import org.apache.commons.math3.optim.nonlinear.vector.ModelFunction;
import org.apache.commons.math3.optim.nonlinear.vector.ModelFunctionJacobian;
import org.apache.commons.math3.optim.nonlinear.vector.Target;
import org.apache.commons.math3.optim.nonlinear.vector.Weight;
import org.junit.Test;
import static org.junit.Assert.*;

public class AbstractLeastSquaresOptimizerClaudeTest {

    // helper: สร้างและรัน optimizer ด้วยปัญหาเชิงเส้นคงที่ (model = J * point)
    private DummyOptimizer setupOptimizer(double[] target, double[] weightDiag,
                                           final double[][] jac, double[] start) throws Throwable {
        DummyOptimizer opt = new DummyOptimizer();
        MultivariateVectorFunction model = new MultivariateVectorFunction() {
            public double[] value(double[] point) {
                double[] v = new double[jac.length];
                for (int i = 0; i < jac.length; i++) {
                    double s = 0.0;
                    for (int k = 0; k < jac[i].length; k++) {
                        s += jac[i][k] * point[k];
                    }
                    v[i] = s;
                }
                return v;
            }
        };
        MultivariateMatrixFunction jacFunc = new MultivariateMatrixFunction() {
            public double[][] value(double[] point) {
                return jac;
            }
        };
        opt.optimize(new MaxEval(100), new InitialGuess(start), new Target(target),
                new Weight(weightDiag), new ModelFunction(model),
                new ModelFunctionJacobian(jacFunc));
        return opt;
    }

    // computeWeightedJacobian: weight=1 -> ผลคือ J เดิม
    @Test
    public void testComputeWeightedJacobian_weightOne_returnsUnscaledJacobian() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        RealMatrix j = opt.computeWeightedJacobian(new double[]{0.0});
        assertEquals(1.0, j.getEntry(0, 0), 1e-9);
    }

    // computeWeightedJacobian: weight=4 -> sqrt(4)=2 คูณ J
    @Test
    public void testComputeWeightedJacobian_weightFour_returnsScaledJacobian() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{4.0},
                new double[][]{{1.0}}, new double[]{0.0});
        RealMatrix j = opt.computeWeightedJacobian(new double[]{0.0});
        assertEquals(2.0, j.getEntry(0, 0), 1e-9);
    }

    // computeCost: residual=0 -> cost=0
    @Test
    public void testComputeCost_zeroResiduals_returnsZero() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        double cost = opt.computeCost(new double[]{0.0});
        assertEquals(0.0, cost, 1e-9);
    }

    // computeCost: weight=1, residual=3 -> sqrt(1*3*3)=3
    @Test
    public void testComputeCost_positiveResiduals_weightOne_returnsResidualMagnitude() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        double cost = opt.computeCost(new double[]{3.0});
        assertEquals(3.0, cost, 1e-9);
    }

    // computeCost: weight=4, residual=3 -> sqrt(4*3*3)=6
    @Test
    public void testComputeCost_positiveResiduals_weightFour_scalesResult() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{4.0},
                new double[][]{{1.0}}, new double[]{0.0});
        double cost = opt.computeCost(new double[]{3.0});
        assertEquals(6.0, cost, 1e-9);
    }

    // getRMS: 1 measurement, chiSquare=100 -> rms=sqrt(100/1)=10
    @Test
    public void testGetRMS_singleMeasurement_returnsSqrtChiSquareOverTargetSize() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        assertEquals(10.0, opt.getRMS(), 1e-9);
    }

    // getRMS: 2 measurements -> rms=sqrt(17/2)
    @Test
    public void testGetRMS_twoMeasurements_returnsSqrtChiSquareOverTargetSize() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{1.0, 4.0}, new double[]{1.0, 1.0},
                new double[][]{{1.0}, {2.0}}, new double[]{0.0});
        assertEquals(Math.sqrt(17.0 / 2.0), opt.getRMS(), 1e-9);
    }

    // getChiSquare: cost=10 หลัง optimize -> chiSquare=100
    @Test
    public void testGetChiSquare_afterOptimize_returnsSquareOfCost() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        assertEquals(100.0, opt.getChiSquare(), 1e-9);
    }

    // getChiSquare: instance ใหม่ยังไม่เคย setCost -> ค่าเริ่มต้น 0
    @Test
    public void testGetChiSquare_freshInstance_returnsZero() throws Throwable {
        DummyOptimizer opt = new DummyOptimizer();
        assertEquals(0.0, opt.getChiSquare(), 1e-9);
    }

    // getWeightSquareRoot: weight=1 -> sqrt=1
    @Test
    public void testGetWeightSquareRoot_returnsSquareRootOfWeightMatrix() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        assertEquals(1.0, opt.getWeightSquareRoot().getEntry(0, 0), 1e-9);
    }

    // getWeightSquareRoot: ต้อง return copy ไม่ใช่ reference ภายใน
    @Test
    public void testGetWeightSquareRoot_returnsDefensiveCopy() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        RealMatrix copy = opt.getWeightSquareRoot();
        copy.setEntry(0, 0, 999.0);
        assertEquals(1.0, opt.getWeightSquareRoot().getEntry(0, 0), 1e-9);
    }

    // setCost: อัปเดต cost แล้วส่งผลต่อ getChiSquare/getRMS
    @Test
    public void testSetCost_updatesChiSquareAndRMS() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        opt.setCost(5.0);
        assertEquals(25.0, opt.getChiSquare(), 1e-9);
        assertEquals(5.0, opt.getRMS(), 1e-9);
    }

    // computeCovariances: weight=1, J=1 -> jTj=1 -> inverse=1
    @Test
    public void testComputeCovariances_weightOne_returnsExpectedMatrix() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        double[][] cov = opt.computeCovariances(new double[]{0.0}, 1e-11);
        assertEquals(1.0, cov[0][0], 1e-9);
    }

    // computeCovariances: weight=4 -> weightedJ=2 -> jTj=4 -> inverse=0.25
    @Test
    public void testComputeCovariances_weightFour_returnsScaledMatrix() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{4.0},
                new double[][]{{1.0}}, new double[]{0.0});
        double[][] cov = opt.computeCovariances(new double[]{0.0}, 1e-11);
        assertEquals(0.25, cov[0][0], 1e-9);
    }

    // computeCovariances: jacobian=0 -> jTj เป็น matrix เอกฐาน -> SingularMatrixException
    @Test
    public void testComputeCovariances_singularJacobian_throwsSingularMatrixException() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{0.0}}, new double[]{0.0});
        try {
            opt.computeCovariances(new double[]{0.0}, 1e-11);
            fail("expected SingularMatrixException");
        } catch (SingularMatrixException expected) {
            // ok
        }
    }

    // computeSigma: weight=1 -> sqrt(cov[0][0])=1
    @Test
    public void testComputeSigma_weightOne_returnsSqrtOfCovarianceDiagonal() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        double[] sigma = opt.computeSigma(new double[]{0.0}, 1e-11);
        assertEquals(1.0, sigma[0], 1e-9);
    }

    // computeSigma: weight=4 -> sqrt(0.25)=0.5
    @Test
    public void testComputeSigma_weightFour_returnsPointFive() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{4.0},
                new double[][]{{1.0}}, new double[]{0.0});
        double[] sigma = opt.computeSigma(new double[]{0.0}, 1e-11);
        assertEquals(0.5, sigma[0], 1e-9);
    }

    // computeSigma: 2 พารามิเตอร์ -> loop วนสองรอบ, แต่ละตัว sigma=1
    @Test
    public void testComputeSigma_twoParameters_returnsArrayLengthTwoAllOnes() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{2.0, 3.0}, new double[]{1.0, 1.0},
                new double[][]{{1.0, 0.0}, {0.0, 1.0}}, new double[]{0.0, 0.0});
        double[] sigma = opt.computeSigma(new double[]{0.0, 0.0}, 1e-11);
        assertEquals(2, sigma.length);
        assertEquals(1.0, sigma[0], 1e-9);
        assertEquals(1.0, sigma[1], 1e-9);
    }

    // optimize: คืนค่า point/value ตรงกับที่ doOptimize คำนวณ
    @Test
    public void testOptimize_returnsPointVectorValuePair_withStartPointAndObjectiveValue() throws Throwable {
        DummyOptimizer opt = new DummyOptimizer();
        PointVectorValuePair result = opt.optimize(new MaxEval(100),
                new InitialGuess(new double[]{0.0}), new Target(new double[]{10.0}),
                new Weight(new double[]{1.0}),
                new ModelFunction(new MultivariateVectorFunction() {
                    public double[] value(double[] point) {
                        return new double[]{point[0]};
                    }
                }),
                new ModelFunctionJacobian(new MultivariateMatrixFunction() {
                    public double[][] value(double[] point) {
                        return new double[][]{{1.0}};
                    }
                }));
        assertEquals(0.0, result.getPoint()[0], 1e-9);
        assertEquals(0.0, result.getValue()[0], 1e-9);
    }



    // computeResiduals: target - objective, ความยาวตรงกัน
    @Test
    public void testComputeResiduals_matchingLength_returnsTargetMinusObjective() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        double[] residuals = opt.computeResiduals(new double[]{3.0});
        assertEquals(7.0, residuals[0], 1e-9);
    }

    // computeResiduals: ความยาวไม่ตรงกับ target -> DimensionMismatchException
    @Test
    public void testComputeResiduals_mismatchedLength_throwsDimensionMismatchException() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{10.0}, new double[]{1.0},
                new double[][]{{1.0}}, new double[]{0.0});
        try {
            opt.computeResiduals(new double[]{1.0, 2.0});
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
            // ok
        }
    }

    // computeResiduals: 2 measurement, ลูปวนสองรอบ
    @Test
    public void testComputeResiduals_twoMeasurements_returnsElementwiseDifference() throws Throwable {
        DummyOptimizer opt = setupOptimizer(new double[]{1.0, 4.0}, new double[]{1.0, 1.0},
                new double[][]{{1.0}, {2.0}}, new double[]{0.0});
        double[] residuals = opt.computeResiduals(new double[]{0.5, 1.5});
        assertEquals(0.5, residuals[0], 1e-9);
        assertEquals(2.5, residuals[1], 1e-9);
    }

    private static class DummyOptimizer extends AbstractLeastSquaresOptimizer {
        DummyOptimizer() {
            super(null);
        }

        @Override
        protected PointVectorValuePair doOptimize() {
            double[] params = getStartPoint();
            double[] objective = computeObjectiveValue(params);
            double[] residuals = computeResiduals(objective);
            setCost(computeCost(residuals));
            return new PointVectorValuePair(params, objective);
        }
    }
}
