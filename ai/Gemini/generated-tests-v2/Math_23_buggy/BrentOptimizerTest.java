package org.apache.commons.math3.optimization.univariate;

import org.junit.Test;
import org.junit.Assert;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.ConvergenceChecker;
import org.apache.commons.math3.util.FastMath;

public class BrentOptimizerTest {

    @Test(expected = NumberIsTooSmallException.class)
    public void testConstructorRelTooSmall() throws Throwable {
        double minRel = 2 * FastMath.ulp(1d);
        new BrentOptimizer(minRel - 1e-16, 1e-3);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testConstructorAbsNonPositive() throws Throwable {
        double minRel = 2 * FastMath.ulp(1d);
        new BrentOptimizer(minRel, 0.0);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testConstructorAbsNegative() throws Throwable {
        double minRel = 2 * FastMath.ulp(1d);
        new BrentOptimizer(minRel, -1.0);
    }

    @Test
    public void testValidConstructors() throws Throwable {
        double minRel = 2 * FastMath.ulp(1d);
        BrentOptimizer opt1 = new BrentOptimizer(minRel, 1e-3);
        Assert.assertNotNull(opt1);

        ConvergenceChecker<UnivariatePointValuePair> checker = new ConvergenceChecker<UnivariatePointValuePair>() {
            public boolean converged(int iteration, UnivariatePointValuePair previous, UnivariatePointValuePair current) {
                return false;
            }
        };
        BrentOptimizer opt2 = new BrentOptimizer(minRel, 1e-3, checker);
        Assert.assertNotNull(opt2);
    }

    @Test
    public void testOptimizeMinimizeSimpleFunction() throws Throwable {
        double minRel = 2 * FastMath.ulp(1d);
        BrentOptimizer optimizer = new BrentOptimizer(minRel, 1e-8);

        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return (x - 2.0) * (x - 2.0) + 1.0;
            }
        };

        UnivariatePointValuePair result = optimizer.optimize(100, f, GoalType.MINIMIZE, 0.0, 4.0, 2.1);
        Assert.assertNotNull(result);
        Assert.assertEquals(2.0, result.getPoint(), 1e-4);
        Assert.assertEquals(1.0, result.getValue(), 1e-4);
    }

    @Test
    public void testOptimizeMaximizeSimpleFunction() throws Throwable {
        double minRel = 2 * FastMath.ulp(1d);
        BrentOptimizer optimizer = new BrentOptimizer(minRel, 1e-8);

        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return -((x - 2.0) * (x - 2.0)) + 5.0;
            }
        };

        UnivariatePointValuePair result = optimizer.optimize(100, f, GoalType.MAXIMIZE, 0.0, 4.0, 1.9);
        Assert.assertNotNull(result);
        Assert.assertEquals(2.0, result.getPoint(), 1e-4);
        Assert.assertEquals(5.0, result.getValue(), 1e-4);
    }

    @Test
    public void testOptimizeInvertedInterval() throws Throwable {
        double minRel = 2 * FastMath.ulp(1d);
        BrentOptimizer optimizer = new BrentOptimizer(minRel, 1e-8);

        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x * x;
            }
        };

        UnivariatePointValuePair result = optimizer.optimize(100, f, GoalType.MINIMIZE, 5.0, -5.0, 0.1);
        Assert.assertNotNull(result);
        Assert.assertEquals(0.0, result.getPoint(), 1e-4);
        Assert.assertEquals(0.0, result.getValue(), 1e-4);
    }

    @Test
    public void testOptimizeWithConvergenceChecker() throws Throwable {
        double minRel = 2 * FastMath.ulp(1d);
        ConvergenceChecker<UnivariatePointValuePair> checker = new ConvergenceChecker<UnivariatePointValuePair>() {
            public boolean converged(int iteration, UnivariatePointValuePair previous, UnivariatePointValuePair current) {
                if (previous == null) {
                    return false;
                }
                return FastMath.abs(current.getPoint() - previous.getPoint()) < 0.1;
            }
        };

        BrentOptimizer optimizer = new BrentOptimizer(minRel, 1e-8, checker);

        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x * x;
            }
        };

        UnivariatePointValuePair result = optimizer.optimize(100, f, GoalType.MINIMIZE, -1.0, 1.0, 0.5);
        Assert.assertNotNull(result);
    }

    @Test
    public void testGoldenSectionAndParabolicInterpBranches() throws Throwable {
        double minRel = 2 * FastMath.ulp(1d);
        BrentOptimizer optimizer = new BrentOptimizer(minRel, 1e-10);

        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return FastMath.sin(x);
            }
        };

        UnivariatePointValuePair resultMin = optimizer.optimize(200, f, GoalType.MINIMIZE, 3.0, 5.0, 4.0);
        Assert.assertNotNull(resultMin);
        Assert.assertEquals(3.0 * Math.PI / 2.0, resultMin.getPoint(), 1e-3);

        UnivariatePointValuePair resultMax = optimizer.optimize(200, f, GoalType.MAXIMIZE, 0.0, 3.0, 1.5);
        Assert.assertNotNull(resultMax);
        Assert.assertEquals(Math.PI / 2.0, resultMax.getPoint(), 1e-3);
    }
}