package org.apache.commons.math3.optimization.univariate;

import org.junit.Test;
import org.junit.Assert;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.ConvergenceChecker;
import org.apache.commons.math3.analysis.UnivariateFunction;

public class BrentOptimizerTest {

    @Test(expected = NumberIsTooSmallException.class)
    public void testConstructorWithInvalidRel() throws Throwable {
        new BrentOptimizer(1e-20, 1e-8);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testConstructorWithInvalidAbs() throws Throwable {
        double minRel = 2 * Math.ulp(1d);
        new BrentOptimizer(minRel, 0.0);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testConstructorWithNegativeAbs() throws Throwable {
        double minRel = 2 * Math.ulp(1d);
        new BrentOptimizer(minRel, -1.0);
    }

    @Test
    public void testMinimizeParabola() throws Throwable {
        double minRel = 2 * Math.ulp(1d);
        BrentOptimizer optimizer = new BrentOptimizer(minRel, 1e-10);
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return (x - 2.0) * (x - 2.0) + 1.0;
            }
        };

        UnivariatePointValuePair result = optimizer.optimize(100, f, GoalType.MINIMIZE, 0.0, 4.0, 2.0);
        Assert.assertNotNull(result);
        Assert.assertEquals(2.0, result.getPoint(), 1e-6);
        Assert.assertEquals(1.0, result.getValue(), 1e-6);
    }

    @Test
    public void testMaximizeParabola() throws Throwable {
        double minRel = 2 * Math.ulp(1d);
        BrentOptimizer optimizer = new BrentOptimizer(minRel, 1e-10);
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return -((x - 2.0) * (x - 2.0)) + 5.0;
            }
        };

        UnivariatePointValuePair result = optimizer.optimize(100, f, GoalType.MAXIMIZE, 0.0, 4.0, 2.0);
        Assert.assertNotNull(result);
        Assert.assertEquals(2.0, result.getPoint(), 1e-6);
        Assert.assertEquals(5.0, result.getValue(), 1e-6);
    }

    @Test
    public void testMinInitialIntervalInverted() throws Throwable {
        double minRel = 2 * Math.ulp(1d);
        BrentOptimizer optimizer = new BrentOptimizer(minRel, 1e-10);
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x * x;
            }
        };

        // lo > hi case (4.0 to 0.0)
        UnivariatePointValuePair result = optimizer.optimize(100, f, GoalType.MINIMIZE, 4.0, 0.0, 2.0);
        Assert.assertNotNull(result);
        Assert.assertEquals(0.0, result.getPoint(), 1e-5);
        Assert.assertEquals(0.0, result.getValue(), 1e-5);
    }

    @Test
    public void testConvergenceCheckerIntegration() throws Throwable {
        double minRel = 2 * Math.ulp(1d);
        ConvergenceChecker<UnivariatePointValuePair> checker = new ConvergenceChecker<UnivariatePointValuePair>() {
            public boolean converged(int iteration, UnivariatePointValuePair previous, UnivariatePointValuePair current) {
                return iteration > 2;
            }
        };

        BrentOptimizer optimizer = new BrentOptimizer(minRel, 1e-10, checker);
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x * x;
            }
        };

        UnivariatePointValuePair result = optimizer.optimize(100, f, GoalType.MINIMIZE, -1.0, 1.0, 0.0);
        Assert.assertNotNull(result);
    }

    @Test
    public void testFunctionWithParabolicFitAndGoldenSectionBranches() throws Throwable {
        double minRel = 2 * Math.ulp(1d);
        BrentOptimizer optimizer = new BrentOptimizer(minRel, 1e-8);
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return Math.sin(x);
            }
        };

        UnivariatePointValuePair result = optimizer.optimize(200, f, GoalType.MINIMIZE, 3.0, 5.0, 4.0);
        Assert.assertNotNull(result);
        Assert.assertEquals(3.5 * Math.PI / 2.0, result.getPoint(), 0.1);
    }
}