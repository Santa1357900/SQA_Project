package org.apache.commons.math.optimization.direct;

import java.util.Arrays;

import org.apache.commons.math.analysis.MultivariateFunction;
import org.apache.commons.math.exception.NumberIsTooSmallException;
import org.apache.commons.math.exception.OutOfRangeException;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.junit.Test;

import static org.junit.Assert.*;

public class BOBYQAOptimizerTest {

    @Test
    public void testConstructorAndDefaultValues() throws Throwable {
        BOBYQAOptimizer optimizer = new BOBYQAOptimizer(6);
        assertNotNull(optimizer);
    }

    @Test(expected = NumberIsTooSmallException.class)
    public void testDimensionTooSmall() throws Throwable {
        BOBYQAOptimizer optimizer = new BOBYQAOptimizer(5);
        double[] lowerBound = new double[] { -1.0 };
        double[] upperBound = new double[] { 1.0 };
        double[] startPoint = new double[] { 0.0 };

        optimizer.optimize(100, new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0];
            }
        }, GoalType.MINIMIZE, startPoint, lowerBound, upperBound);
    }

    @Test(expected = OutOfRangeException.class)
    public void testInterpolationPointsOutOfRange() throws Throwable {
        // Dimension is 2, so valid npt interval is [4, 6]. Let's pass 3.
        BOBYQAOptimizer optimizer = new BOBYQAOptimizer(3);
        double[] lowerBound = new double[] { -1.0, -1.0 };
        double[] upperBound = new double[] { 1.0, 1.0 };
        double[] startPoint = new double[] { 0.0, 0.0 };

        optimizer.optimize(100, new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] * point[0] + point[1] * point[1];
            }
        }, GoalType.MINIMIZE, startPoint, lowerBound, upperBound);
    }

    @Test
    public void testMinimizeSphereFunction() throws Throwable {
        BOBYQAOptimizer optimizer = new BOBYQAOptimizer(6);
        double[] lowerBound = new double[] { -5.0, -5.0 };
        double[] upperBound = new double[] { 5.0, 5.0 };
        double[] startPoint = new double[] { 3.0, 2.0 };

        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                double sum = 0.0;
                for (int i = 0; i < point.length; i++) {
                    sum += point[i] * point[i];
                }
                return sum;
            }
        };

        RealPointValuePair result = optimizer.optimize(200, sphere, GoalType.MINIMIZE, startPoint, lowerBound, upperBound);
        assertNotNull(result);
        assertTrue(result.getValue() < 1e-5);
        assertEquals(0.0, result.getPoint()[0], 1e-3);
        assertEquals(0.0, result.getPoint()[1], 1e-3);
    }

    @Test
    public void testMaximizeSphereFunction() throws Throwable {
        BOBYQAOptimizer optimizer = new BOBYQAOptimizer(6);
        double[] lowerBound = new double[] { -1.0, -1.0 };
        double[] upperBound = new double[] { 1.0, 1.0 };
        double[] startPoint = new double[] { 0.1, 0.1 };

        MultivariateFunction sphere = new MultivariateFunction() {
            public double value(double[] point) {
                double sum = 0.0;
                for (int i = 0; i < point.length; i++) {
                    sum += point[i] * point[i];
                }
                return sum;
            }
        };

        RealPointValuePair result = optimizer.optimize(200, sphere, GoalType.MAXIMIZE, startPoint, lowerBound, upperBound);
        assertNotNull(result);
        assertTrue(result.getValue() > 1.5);
    }

    @Test
    public void testInitialRadiusAdjustment() throws Throwable {
        // Test when minDiff is smaller than 2 * initialTrustRegionRadius
        BOBYQAOptimizer optimizer = new BOBYQAOptimizer(6, 10.0, 1e-8);
        double[] lowerBound = new double[] { 0.0, 0.0 };
        double[] upperBound = new double[] { 1.0, 1.0 }; // diff is 1.0, minDiff < 20.0
        double[] startPoint = new double[] { 0.5, 0.5 };

        MultivariateFunction func = new MultivariateFunction() {
            public double value(double[] point) {
                return point[0] + point[1];
            }
        };

        RealPointValuePair result = optimizer.optimize(100, func, GoalType.MINIMIZE, startPoint, lowerBound, upperBound);
        assertNotNull(result);
    }
}