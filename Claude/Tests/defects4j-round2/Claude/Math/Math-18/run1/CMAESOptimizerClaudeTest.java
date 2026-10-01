package org.apache.commons.math3.optimization.direct;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathUnsupportedOperationException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.SimpleValueChecker;
import org.apache.commons.math3.optimization.ConvergenceChecker;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

public class CMAESOptimizerClaudeTest {

    private MultivariateFunction sphereFunction() {
        return new MultivariateFunction() {
            public double value(double[] point) {
                double sum = 0;
                for (int i = 0; i < point.length; i++) {
                    sum += point[i] * point[i];
                }
                return sum;
            }
        };
    }

    private MultivariateFunction negativeSphereFunction(final double offset) {
        return new MultivariateFunction() {
            public double value(double[] point) {
                double sum = 0;
                for (int i = 0; i < point.length; i++) {
                    sum += point[i] * point[i];
                }
                return offset - sum;
            }
        };
    }

    private MultivariateFunction shiftedSquare(final double target) {
        return new MultivariateFunction() {
            public double value(double[] point) {
                double diff = point[0] - target;
                return diff * diff;
            }
        };
    }

    private MultivariateFunction flatFunction() {
        return new MultivariateFunction() {
            public double value(double[] point) {
                return 0.0;
            }
        };
    }

    // ตรวจค่าคงที่ public static final ตามสัญญา
    @Test
    public void testDefaultConstants_haveExpectedValues() throws Throwable {
        assertEquals(0, CMAESOptimizer.DEFAULT_CHECKFEASABLECOUNT);
        assertEquals(0.0, CMAESOptimizer.DEFAULT_STOPFITNESS, 1e-12);
        assertTrue(CMAESOptimizer.DEFAULT_ISACTIVECMA);
        assertEquals(30000, CMAESOptimizer.DEFAULT_MAXITERATIONS);
        assertEquals(0, CMAESOptimizer.DEFAULT_DIAGONALONLY);
        assertNotNull(CMAESOptimizer.DEFAULT_RANDOMGENERATOR);
    }

    // constructor ไม่มีพารามิเตอร์: ประวัติสถิติต้องว่างก่อนเรียก optimize
    @Test
    public void testConstructorDefault_statisticsHistoriesEmpty() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer();
        assertTrue(optimizer.getStatisticsSigmaHistory().isEmpty());
        assertTrue(optimizer.getStatisticsFitnessHistory().isEmpty());
    }

    // constructor (lambda): ประวัติสถิติต้องว่างก่อนเรียก optimize
    @Test
    public void testConstructorWithLambda_statisticsHistoriesEmpty() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(5);
        assertTrue(optimizer.getStatisticsMeanHistory().isEmpty());
        assertTrue(optimizer.getStatisticsDHistory().isEmpty());
    }

    // constructor (lambda, inputSigma): ประวัติสถิติต้องว่างก่อนเรียก optimize
    @Test
    public void testConstructorWithLambdaAndSigma_statisticsHistoriesEmpty() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(5, new double[] {1.0, 1.0});
        assertTrue(optimizer.getStatisticsSigmaHistory().isEmpty());
    }

    // checkParameters(): inputSigma.length != init.length -> DimensionMismatchException
    @Test
    public void testOptimize_inputSigmaDimensionMismatch_throwsDimensionMismatchException() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(4, new double[] {1.0, 1.0, 1.0},
                100, 0, true, 0, 0, new MersenneTwister(1), false);
        double[] lower = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double[] upper = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        try {
            optimizer.optimize(100, sphereFunction(), GoalType.MINIMIZE,
                    new double[] {0.5, 0.5}, lower, upper);
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
            // ok
        }
    }

    // checkParameters(): inputSigma[i] < 0 -> NotPositiveException
    @Test
    public void testOptimize_inputSigmaNegative_throwsNotPositiveException() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(4, new double[] {-1.0, 1.0},
                100, 0, true, 0, 0, new MersenneTwister(2), false);
        double[] lower = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double[] upper = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        try {
            optimizer.optimize(100, sphereFunction(), GoalType.MINIMIZE,
                    new double[] {0.5, 0.5}, lower, upper);
            fail("expected NotPositiveException");
        } catch (NotPositiveException expected) {
            // ok
        }
    }

    // checkParameters(): inputSigma[i] > range -> OutOfRangeException
    @Test
    public void testOptimize_inputSigmaExceedsBoundaryRange_throwsOutOfRangeException() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(4, new double[] {2.0},
                100, 0, true, 0, 0, new MersenneTwister(3), false);
        try {
            optimizer.optimize(100, shiftedSquare(0.0), GoalType.MINIMIZE,
                    new double[] {0.5}, new double[] {0.0}, new double[] {1.0});
            fail("expected OutOfRangeException");
        } catch (OutOfRangeException expected) {
            // ok
        }
    }

    // checkParameters(): bounds บางมิติ finite บางมิติ infinite -> MathUnsupportedOperationException
    @Test
    public void testOptimize_mixedFiniteInfiniteBounds_throwsMathUnsupportedOperationException() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(4, null,
                100, 0, true, 0, 0, new MersenneTwister(4), false);
        double[] lower = {0.0, Double.NEGATIVE_INFINITY};
        double[] upper = {1.0, Double.POSITIVE_INFINITY};
        try {
            optimizer.optimize(100, sphereFunction(), GoalType.MINIMIZE,
                    new double[] {0.5, 0.5}, lower, upper);
            fail("expected MathUnsupportedOperationException");
        } catch (MathUnsupportedOperationException expected) {
            // ok
        }
    }

    // checkParameters(): upper - lower overflow เป็น Infinity -> NumberIsTooLargeException
    @Test
    public void testOptimize_boundaryDifferenceOverflow_throwsNumberIsTooLargeException() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(4, null,
                100, 0, true, 0, 0, new MersenneTwister(5), false);
        double[] lower = {-Double.MAX_VALUE};
        double[] upper = {Double.MAX_VALUE};
        try {
            optimizer.optimize(100, shiftedSquare(0.0), GoalType.MINIMIZE,
                    new double[] {0.0}, lower, upper);
            fail("expected NumberIsTooLargeException");
        } catch (NumberIsTooLargeException expected) {
            // ok
        }
    }

    // checkParameters(): bounds ทุกมิติ infinite -> boundaries == null, ไม่ throw
    @Test
    public void testOptimize_allInfiniteBounds_noExceptionAndFiniteResult() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(4, null,
                50, 0, true, 0, 0, new MersenneTwister(6), false);
        double[] lower = {Double.NEGATIVE_INFINITY};
        double[] upper = {Double.POSITIVE_INFINITY};
        PointValuePair result = optimizer.optimize(500, shiftedSquare(0.0),
                GoalType.MINIMIZE, new double[] {3.0}, lower, upper);
        assertFalse(Double.isNaN(result.getValue()));
        assertTrue(result.getValue() >= 0.0);
    }

    // doOptimize(): ฟังก์ชัน sphere มิติข่าย 2 ต้องลู่เข้าใกล้ 0
    @Test
    public void testOptimize_sphereFunctionMinimize_convergesNearZero() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {1.0, 1.0},
                2000, 0, true, 0, 0, new MersenneTwister(42), false);
        double[] lower = {-5.0, -5.0};
        double[] upper = {5.0, 5.0};
        PointValuePair result = optimizer.optimize(50000, sphereFunction(),
                GoalType.MINIMIZE, new double[] {3.0, 3.0}, lower, upper);
        assertTrue(result.getValue() < 1e-2);
    }

    // doOptimize(): isMinimize=false, ค่า fitness ภายในถูก negate ให้ถูกกลับคืนเป็นค่าสูงสุด
    @Test
    public void testOptimize_sphereFunctionMaximize_convergesNearMax() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {1.0, 1.0},
                2000, 0, true, 0, 0, new MersenneTwister(7), false);
        double[] lower = {-5.0, -5.0};
        double[] upper = {5.0, 5.0};
        PointValuePair result = optimizer.optimize(50000, negativeSphereFunction(10.0),
                GoalType.MAXIMIZE, new double[] {3.0, 3.0}, lower, upper);
        assertTrue(result.getValue() > 9.5);
    }

    // FitnessFunction.repairAndDecode(): ผลลัพธ์ต้องถูก repair ให้อยู่ใน [lower,upper] เสมอ
    @Test
    public void testOptimize_withFiniteBounds_resultWithinBounds() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {0.5},
                500, 0, true, 0, 0, new MersenneTwister(8), false);
        double[] lower = {-1.0};
        double[] upper = {1.0};
        PointValuePair result = optimizer.optimize(20000, shiftedSquare(5.0),
                GoalType.MINIMIZE, new double[] {0.0}, lower, upper);
        double x = result.getPoint()[0];
        assertTrue(x >= lower[0] - 1e-9 && x <= upper[0] + 1e-9);
    }

    // doOptimize(): checkFeasableCount > 0 -> วนสุ่มค่าใหม่จนอยู่ในขอบเขต, ผลลัพธ์ยังอยู่ใน bounds
    @Test
    public void testOptimize_checkFeasableCountPositive_resultWithinBounds() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {1.5},
                200, 0, true, 0, 5, new MersenneTwister(9), false);
        double[] lower = {-1.0};
        double[] upper = {1.0};
        PointValuePair result = optimizer.optimize(10000, shiftedSquare(0.0),
                GoalType.MINIMIZE, new double[] {0.5}, lower, upper);
        double x = result.getPoint()[0];
        assertTrue(x >= lower[0] - 1e-9 && x <= upper[0] + 1e-9);
    }

    // updateCovarianceDiagonalOnly(): diagonalOnly > 0 ต้องยังคงลดค่าฟังก์ชันได้
    @Test
    public void testOptimize_diagonalOnlyPositive_convergesReasonably() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {1.0, 1.0},
                2000, 0, true, 5, 0, new MersenneTwister(10), false);
        double[] lower = {-5.0, -5.0};
        double[] upper = {5.0, 5.0};
        PointValuePair result = optimizer.optimize(50000, sphereFunction(),
                GoalType.MINIMIZE, new double[] {3.0, 3.0}, lower, upper);
        assertTrue(result.getValue() < 1.0);
    }

    // updateCovariance(): isActiveCMA=false ใช้สูตรอัปเดตแบบไม่ active ต้องยังลู่เข้า
    @Test
    public void testOptimize_isActiveCMAFalse_convergesReasonably() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {1.0, 1.0},
                2000, 0, false, 0, 0, new MersenneTwister(11), false);
        double[] lower = {-5.0, -5.0};
        double[] upper = {5.0, 5.0};
        PointValuePair result = optimizer.optimize(50000, sphereFunction(),
                GoalType.MINIMIZE, new double[] {3.0, 3.0}, lower, upper);
        assertTrue(result.getValue() < 1.0);
    }

    // generateStatistics=true: ทุกประวัติต้องถูกเติมค่าในระหว่างการวนรอบ
    @Test
    public void testOptimize_generateStatisticsTrue_historiesPopulated() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {1.0, 1.0},
                500, 0, true, 0, 0, new MersenneTwister(12), true);
        double[] lower = {-5.0, -5.0};
        double[] upper = {5.0, 5.0};
        optimizer.optimize(20000, sphereFunction(), GoalType.MINIMIZE,
                new double[] {3.0, 3.0}, lower, upper);
        assertTrue(optimizer.getStatisticsSigmaHistory().size() > 0);
        assertEquals(optimizer.getStatisticsSigmaHistory().size(),
                optimizer.getStatisticsFitnessHistory().size());
    }

    // generateStatistics=false (ค่าเริ่มต้น): ประวัติต้องยังว่างหลังเรียก optimize
    @Test
    public void testOptimize_generateStatisticsFalse_historiesRemainEmpty() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {1.0, 1.0},
                500, 0, true, 0, 0, new MersenneTwister(13), false);
        double[] lower = {-5.0, -5.0};
        double[] upper = {5.0, 5.0};
        optimizer.optimize(20000, sphereFunction(), GoalType.MINIMIZE,
                new double[] {3.0, 3.0}, lower, upper);
        assertTrue(optimizer.getStatisticsFitnessHistory().isEmpty());
        assertTrue(optimizer.getStatisticsDHistory().isEmpty());
    }

    // doOptimize(): getConvergenceChecker() != null ต้องทำงานได้โดยไม่ throw และคืนค่าถูกต้อง
    @Test
    public void testOptimize_withConvergenceChecker_completesSuccessfully() throws Throwable {
        ConvergenceChecker<PointValuePair> checker = new SimpleValueChecker();
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {1.0, 1.0},
                2000, 0, true, 0, 0, new MersenneTwister(14), false, checker);
        double[] lower = {-5.0, -5.0};
        double[] upper = {5.0, 5.0};
        PointValuePair result = optimizer.optimize(50000, sphereFunction(),
                GoalType.MINIMIZE, new double[] {3.0, 3.0}, lower, upper);
        assertFalse(Double.isNaN(result.getValue()));
        assertFalse(Double.isInfinite(result.getValue()));
    }

    // doOptimize(): ฟังก์ชันคงที่ (flat) ค่าดีที่สุดต้องเป็น 0 เสมอตามสัญญาฟังก์ชัน
    @Test
    public void testOptimize_flatFunction_returnsExactZeroValue() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {0.5},
                100, 0, true, 0, 0, new MersenneTwister(15), false);
        double[] lower = {-1.0};
        double[] upper = {1.0};
        PointValuePair result = optimizer.optimize(1000, flatFunction(),
                GoalType.MINIMIZE, new double[] {0.5}, lower, upper);
        assertEquals(0.0, result.getValue(), 1e-9);
    }

    // doOptimize(): maxEval ต่ำมาก -> TooManyEvaluationsException ถูกจับภายในและคืนผลลัพธ์ที่ใช้ได้
    @Test
    public void testOptimize_lowMaxEvaluations_returnsFiniteResultWithoutException() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(4, new double[] {1.0, 1.0},
                100, 0, true, 0, 0, new MersenneTwister(16), false);
        double[] lower = {-5.0, -5.0};
        double[] upper = {5.0, 5.0};
        PointValuePair result = optimizer.optimize(3, sphereFunction(),
                GoalType.MINIMIZE, new double[] {3.0, 3.0}, lower, upper);
        assertNotNull(result);
        assertFalse(Double.isNaN(result.getValue()));
    }

    // initializeCMA(): lambda <= 0 -> คำนวณค่าเริ่มต้นเองและยังต้องลู่เข้าได้
    @Test
    public void testOptimize_lambdaZero_defaultsAndConverges() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {2.0},
                3000, 0, true, 0, 0, new MersenneTwister(17), false);
        double[] lower = {-10.0};
        double[] upper = {10.0};
        PointValuePair result = optimizer.optimize(50000, shiftedSquare(0.0),
                GoalType.MINIMIZE, new double[] {4.0}, lower, upper);
        assertTrue(result.getValue() < 1e-2);
    }

    // getStatisticsMeanHistory(): ขนาดของ mean vector ที่เก็บต้องตรงกับมิติของปัญหา
    @Test
    public void testGetStatisticsMeanHistory_entryDimensionMatchesProblem() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(0, new double[] {1.0, 1.0},
                300, 0, true, 0, 0, new MersenneTwister(18), true);
        double[] lower = {-5.0, -5.0};
        double[] upper = {5.0, 5.0};
        optimizer.optimize(20000, sphereFunction(), GoalType.MINIMIZE,
                new double[] {3.0, 3.0}, lower, upper);
        List<RealMatrix> meanHistory = optimizer.getStatisticsMeanHistory();
        assertTrue(meanHistory.size() > 0);
        assertEquals(2, meanHistory.get(0).getColumnDimension());
    }
}
