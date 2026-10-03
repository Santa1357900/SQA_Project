package org.apache.commons.math3.optimization.direct;

import java.util.List;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathUnsupportedOperationException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.SimpleValueChecker;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import org.junit.Test;
import static org.junit.Assert.*;

public class CMAESOptimizerTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer();
        assertNotNull(optimizer);
        List<Double> sigmaHistory = optimizer.getStatisticsSigmaHistory();
        assertNotNull(sigmaHistory);
        assertTrue(sigmaHistory.isEmpty());
        List<RealMatrix> meanHistory = optimizer.getStatisticsMeanHistory();
        assertNotNull(meanHistory);
        assertTrue(meanHistory.isEmpty());
        List<Double> fitnessHistory = optimizer.getStatisticsFitnessHistory();
        assertNotNull(fitnessHistory);
        assertTrue(fitnessHistory.isEmpty());
        List<RealMatrix> dHistory = optimizer.getStatisticsDHistory();
        assertNotNull(dHistory);
        assertTrue(dHistory.isEmpty());
    }

    @Test
    public void testLambdaConstructor() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(10);
        assertNotNull(optimizer);
    }

    @Test
    public void testLambdaAndSigmaConstructor() throws Throwable {
        double[] sigma = new double[] { 0.1, 0.2 };
        CMAESOptimizer optimizer = new CMAESOptimizer(10, sigma);
        assertNotNull(optimizer);
    }

    @Test
    public void testFullConstructorWithChecker() throws Throwable {
        double[] sigma = new double[] { 0.1 };
        RandomGenerator rng = new MersenneTwister(123L);
        SimpleValueChecker checker = new SimpleValueChecker();
        CMAESOptimizer optimizer = new CMAESOptimizer(
            5, sigma, 100, 0.0, true, 0, 0, rng, true, checker
        );
        assertNotNull(optimizer);
    }

    @Test
    public void testOptimizeSphereFunction() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(
            15, new double[] { 0.5, 0.5 }, 100, 1e-3, true, 0, 0,
            new MersenneTwister(42L), true
        );
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                double sum = 0;
                for (int i = 0; i < point.length; i++) {
                    sum += point[i] * point[i];
                }
                return sum;
            }
        };
        PointValuePair result = optimizer.optimize(
            1000, sphere, GoalType.MINIMIZE, new double[] { 1.0, 1.0 }
        );
        assertNotNull(result);
        assertNotNull(result.getPoint());
        assertTrue(result.getValue() < 0.1);
    }

    @Test
    public void testOptimizeMaximize() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(
            10, new double[] { 0.2 }, 50, 0.0, true, 0, 0,
            new MersenneTwister(1L), false
        );
        MultivariateFunction negSphere = new MultivariateFunction() {
            public double value(double[] point) {
                return -point[0] * point[0];
            }
        };
        PointValuePair result = optimizer.optimize(
            500, negSphere, GoalType.MAXIMIZE, new double[] { 2.0 }
        );
        assertNotNull(result);
        assertTrue(result.getValue() > -1.0);
    }

    @Test
    public void testDiagonalOnlyOptimization() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(
            10, new double[] { 0.3, 0.3 }, 50, 0.0, true, 2, 0,
            new MersenneTwister(99L), true
        );
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0] + point[1] * point[1];
            }
        };
        PointValuePair result = optimizer.optimize(
            200, sphere, GoalType.MINIMIZE, new double[] { 1.0, 1.0 }
        );
        assertNotNull(result);
    }

    @Test
    public void testBoundsAndRepairMode() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(
            10, new double[] { 0.1, 0.1 }, 50, 0.0, true, 0, 1,
            new MersenneTwister(5L), false
        );
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0] + point[1] * point[1];
            }
        };
        double[] lower = new double[] { -1.0, -1.0 };
        double[] upper = new double[] { 1.0, 1.0 };
        PointValuePair result = optimizer.optimize(
            200, sphere, GoalType.MINIMIZE, new double[] { 0.5, 0.5 }, lower, upper
        );
        assertNotNull(result);
    }

    @Test
    public void testDimensionMismatchSigmaException() throws Throwable {
        double[] sigma = new double[] { 0.1, 0.2, 0.3 };
        CMAESOptimizer optimizer = new CMAESOptimizer(10, sigma);
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0];
            }
        };
        try {
            optimizer.optimize(100, sphere, GoalType.MINIMIZE, new double[] { 1.0 });
            fail("Expected DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testNotPositiveSigmaException() throws Throwable {
        double[] sigma = new double[] { -0.1 };
        CMAESOptimizer optimizer = new CMAESOptimizer(10, sigma);
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0];
            }
        };
        try {
            optimizer.optimize(100, sphere, GoalType.MINIMIZE, new double[] { 1.0 });
            fail("Expected NotPositiveException");
        } catch (NotPositiveException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testOutOfRangeSigmaException() throws Throwable {
        double[] sigma = new double[] { 5.0 };
        CMAESOptimizer optimizer = new CMAESOptimizer(10, sigma);
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0];
            }
        };
        double[] lower = new double[] { 0.0 };
        double[] upper = new double[] { 1.0 };
        try {
            optimizer.optimize(100, sphere, GoalType.MINIMIZE, new double[] { 0.5 }, lower, upper);
            fail("Expected OutOfRangeException");
        } catch (OutOfRangeException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testMixedBoundsUnsupportedException() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(10, new double[] { 0.1 });
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0];
            }
        };
        double[] lower = new double[] { 0.0 };
        double[] upper = new double[] { Double.POSITIVE_INFINITY };
        try {
            optimizer.optimize(100, sphere, GoalType.MINIMIZE, new double[] { 0.5 }, lower, upper);
            fail("Expected MathUnsupportedOperationException");
        } catch (MathUnsupportedOperationException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testOverflowBoundsException() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(10, new double[] { 0.1 });
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0];
            }
        };
        double[] lower = new double[] { -Double.MAX_VALUE };
        double[] upper = new double[] { Double.MAX_VALUE };
        try {
            optimizer.optimize(100, sphere, GoalType.MINIMIZE, new double[] { 0.0 }, lower, upper);
            fail("Expected NumberIsTooLargeException");
        } catch (NumberIsTooLargeException e) {
            assertTrue(true);
        }
    }
}