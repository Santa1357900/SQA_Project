package org.apache.commons.math3.optimization.direct;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathUnsupportedOperationException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.SimpleValueChecker;
import org.apache.commons.math3.random.MersenneTwister;

public class CMAESOptimizerTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer();
        assertNotNull(optimizer);
        assertNotNull(optimizer.getStatisticsSigmaHistory());
        assertNotNull(optimizer.getStatisticsMeanHistory());
        assertNotNull(optimizer.getStatisticsFitnessHistory());
        assertNotNull(optimizer.getStatisticsDHistory());
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
    public void testFullConstructor() throws Throwable {
        double[] sigma = new double[] { 0.1 };
        MersenneTwister random = new MersenneTwister(123L);
        SimpleValueChecker checker = new SimpleValueChecker();
        CMAESOptimizer optimizer = new CMAESOptimizer(
            5, sigma, 100, 0.0, true, 1, 0, random, true, checker
        );
        assertNotNull(optimizer);
    }

    @Test
    public void testOptimizeSphereFunctionMinimize() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(15, new double[] { 0.5, 0.5 }, 150, 1e-6, true, 0, 0, new MersenneTwister(42L), false);
        
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                double sum = 0.0;
                for (int i = 0; i < point.length; i++) {
                    sum += point[i] * point[i];
                }
                return sum;
            }
        };

        double[] startPoint = new double[] { 1.0, 1.0 };
        PointValuePair result = optimizer.optimize(1000, sphere, GoalType.MINIMIZE, startPoint);
        assertNotNull(result);
        assertTrue(result.getValue() < 0.1);
    }

    @Test
    public void testOptimizeSphereFunctionMaximize() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(10, new double[] { 0.1, 0.1 }, 50, 0.0, false, 0, 0, new MersenneTwister(42L), false);
        
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                double sum = 0.0;
                for (int i = 0; i < point.length; i++) {
                    sum += point[i] * point[i];
                }
                return sum;
            }
        };

        double[] startPoint = new double[] { 0.1, 0.1 };
        PointValuePair result = optimizer.optimize(200, sphere, GoalType.MAXIMIZE, startPoint);
        assertNotNull(result);
    }

    @Test
    public void testDiagonalOnlyOptimization() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(10, new double[] { 0.2, 0.2 }, 50, 0.0, true, 2, 0, new MersenneTwister(1L), false);
        
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0] + point[1] * point[1];
            }
        };

        PointValuePair result = optimizer.optimize(200, sphere, GoalType.MINIMIZE, new double[] { 1.0, 1.0 });
        assertNotNull(result);
    }

    @Test(expected = DimensionMismatchException.class)
    public void testCheckParametersDimensionMismatch() throws Throwable {
        double[] sigma = new double[] { 0.1, 0.2, 0.3 };
        CMAESOptimizer optimizer = new CMAESOptimizer(10, sigma);
        
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0];
            }
        };

        optimizer.optimize(10, sphere, GoalType.MINIMIZE, new double[] { 1.0, 1.0 });
    }

    @Test(expected = NotPositiveException.class)
    public void testCheckParametersNegativeSigma() throws Throwable {
        double[] sigma = new double[] { 0.1, -0.2 };
        CMAESOptimizer optimizer = new CMAESOptimizer(10, sigma);
        
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0] + point[1] * point[1];
            }
        };

        optimizer.optimize(10, sphere, GoalType.MINIMIZE, new double[] { 1.0, 1.0 });
    }

    @Test(expected = OutOfRangeException.class)
    public void testCheckParametersSigmaOutOfBounds() throws Throwable {
        double[] sigma = new double[] { 5.0 };
        CMAESOptimizer optimizer = new CMAESOptimizer(10, sigma);
        
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0];
            }
        };

        double[] lB = new double[] { 0.0 };
        double[] uB = new double[] { 1.0 };
        org.apache.commons.math3.optimization.SimpleBounds bounds = new org.apache.commons.math3.optimization.SimpleBounds(lB, uB);

        optimizer.optimize(10, sphere, GoalType.MINIMIZE, new double[] { 0.5 }, bounds);
    }

    @Test(expected = MathUnsupportedOperationException.class)
    public void testMixedBoundsUnsupported() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(10);
        
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0] + point[1] * point[1];
            }
        };

        double[] lB = new double[] { 0.0, Double.NEGATIVE_INFINITY };
        double[] uB = new double[] { 1.0, 1.0 };
        org.apache.commons.math3.optimization.SimpleBounds bounds = new org.apache.commons.math3.optimization.SimpleBounds(lB, uB);

        optimizer.optimize(10, sphere, GoalType.MINIMIZE, new double[] { 0.5, 0.5 }, bounds);
    }

    @Test
    public void testFiniteBoundsOptimization() throws Throwable {
        CMAESOptimizer optimizer = new CMAESOptimizer(10, new double[] { 0.1, 0.1 }, 30, 0.0, true, 0, 1, new MersenneTwister(1L), true);
        
        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0] + point[1] * point[1];
            }
        };

        double[] lB = new double[] { -10.0, -10.0 };
        double[] uB = new double[] { 10.0, 10.0 };
        org.apache.commons.math3.optimization.SimpleBounds bounds = new org.apache.commons.math3.optimization.SimpleBounds(lB, uB);

        PointValuePair result = optimizer.optimize(100, sphere, GoalType.MINIMIZE, new double[] { 1.0, 1.0 }, bounds);
        assertNotNull(result);
        assertFalse(optimizer.getStatisticsSigmaHistory().isEmpty());
        assertFalse(optimizer.getStatisticsFitnessHistory().isEmpty());
        assertFalse(optimizer.getStatisticsMeanHistory().isEmpty());
        assertFalse(optimizer.getStatisticsDHistory().isEmpty());
    }
}