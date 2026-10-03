package org.apache.commons.math3.optimization.direct;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathUnsupportedOperationException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.SimpleValueChecker;
import org.apache.commons.math3.random.MersenneTwister;

public class CMAESOptimizerTest {

    @Test
    public void testDefaultConstructorAndGetters() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer();
        List<Double> sigmaHistory = optimizer.getStatisticsSigmaHistory();
        List<RealMatrix> meanHistory = optimizer.getStatisticsMeanHistory();
        List<Double> fitnessHistory = optimizer.getStatisticsFitnessHistory();
        List<RealMatrix> dHistory = optimizer.getStatisticsDHistory();

        assertNotNull(sigmaHistory);
        assertNotNull(meanHistory);
        assertNotNull(fitnessHistory);
        assertNotNull(dHistory);
        assertTrue(sigmaHistory.isEmpty());
    }

    @Test
    public void testConstructorWithLambda() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(10);
        assertNotNull(optimizer);
    }

    @Test
    public void testConstructorWithLambdaAndSigma() throws Throwable {
        double[] sigma = new double[] { 0.1, 0.2 };
        CMAESOptimizer optimizer = new CMAESOptimizer(10, sigma);
        assertNotNull(optimizer);
    }

    @Test
    public void testFullConstructorWithStatisticsAndChecker() throws Throwable {
        double[] sigma = new double[] { 0.1 };
        CMAESOptimizer optimizer = new CMAESOptimizer(
            5, sigma, 100, 0.0, true, 0, 0, new MersenneTwister(), true, new SimpleValueChecker()
        );
        assertNotNull(optimizer);
    }

    @Test
    public void testOptimizeSphereFunctionMinimize() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(
            15, new double[] { 0.5, 0.5 }, 100, 1e-6, true, 0, 0, new MersenneTwister(), false, new SimpleValueChecker()
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
    public void testOptimizeSphereFunctionMaximize() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(
            10, new double[] { 0.1, 0.1 }, 50, 0.0, true, 0, 0, new MersenneTwister(), false, new SimpleValueChecker()
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
            200, sphere, GoalType.MAXIMIZE, new double[] { 0.1, 0.1 }, new double[] { -1.0, -1.0 }, new double[] { 1.0, 1.0 }
        );

        assertNotNull(result);
        assertNotNull(result.getPoint());
    }

    @Test
    public void testDiagonalOnlyOptimization() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(
            10, new double[] { 0.2, 0.2 }, 50, 0.0, true, 5, 0, new MersenneTwister(), true, new SimpleValueChecker()
        );

        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0] + point[1] * point[1];
            }
        };

        PointValuePair result = optimizer.optimize(
            200, sphere, GoalType.MINIMIZE, new double[] { 0.5, 0.5 }
        );

        assertNotNull(result);
        assertFalse(optimizer.getStatisticsSigmaHistory().isEmpty());
    }

    @Test
    public void testCheckParametersDimensionMismatch() throws Throwable {
        double[] sigma = new double[] { 0.1, 0.2, 0.3 };
        CMAESOptimizer optimizer = new CMAESOptimizer(10, sigma);

        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0];
            }
        };

        try {
            optimizer.optimize(100, sphere, GoalType.MINIMIZE, new double[] { 1.0, 1.0 });
            fail("Expected DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            // Expected
        }
    }

    @Test
    public void testCheckParametersNotPositiveSigma() throws Throwable {
        double[] sigma = new double[] { -0.1, 0.2 };
        CMAESOptimizer optimizer = new CMAESOptimizer(10, sigma);

        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0] + point[1] * point[1];
            }
        };

        try {
            optimizer.optimize(100, sphere, GoalType.MINIMIZE, new double[] { 1.0, 1.0 });
            fail("Expected NotPositiveException");
        } catch (NotPositiveException e) {
            // Expected
        }
    }

    @Test
    public void testCheckParametersOutOfRangeSigma() throws Throwable {
        double[] sigma = new double[] { 5.0, 0.2 };
        CMAESOptimizer optimizer = new CMAESOptimizer(10, sigma);

        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0] + point[1] * point[1];
            }
        };

        try {
            optimizer.optimize(
                100, sphere, GoalType.MINIMIZE, new double[] { 0.0, 0.0 }, new double[] { -1.0, -1.0 }, new double[] { 1.0, 1.0 }
            );
            fail("Expected OutOfRangeException");
        } catch (OutOfRangeException e) {
            // Expected
        }
    }

    @Test
    public void testCheckParametersMixedBounds() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(10);

        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0];
            }
        };

        try {
            optimizer.optimize(
                100, sphere, GoalType.MINIMIZE, new double[] { 0.0 }, new double[] { 0.0 }, new double[] { Double.POSITIVE_INFINITY }
            );
            fail("Expected MathUnsupportedOperationException");
        } catch (MathUnsupportedOperationException e) {
            // Expected
        }
    }
}