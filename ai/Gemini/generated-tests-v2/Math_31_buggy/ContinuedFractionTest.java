package org.apache.commons.math3.util;

import org.apache.commons.math3.exception.ConvergenceException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.junit.Test;
import org.junit.Assert;

public class ContinuedFractionTest {

    private static final class SimpleContinuedFraction extends ContinuedFraction {
        private final double aValue;
        private final double bValue;

        private SimpleContinuedFraction(double aValue, double bValue) {
            super();
            this.aValue = aValue;
            this.bValue = bValue;
        }

        protected double getA(int n, double x) {
            return aValue;
        }

        protected double getB(int n, double x) {
            return bValue;
        }
    }

    private static final class AlternatingContinuedFraction extends ContinuedFraction {
        protected double getA(int n, double x) {
            return n;
        }

        protected double getB(int n, double x) {
            return x;
        }
    }

    private static final class InfiniteContinuedFraction extends ContinuedFraction {
        protected double getA(int n, double x) {
            return Double.POSITIVE_INFINITY;
        }

        protected double getB(int n, double x) {
            return 1.0;
        }
    }

    private static final class NaNContinuedFraction extends ContinuedFraction {
        protected double getA(int n, double x) {
            if (n == 1) {
                return Double.NaN;
            }
            return 1.0;
        }

        protected double getB(int n, double x) {
            return 1.0;
        }
    }

    private static final class ZeroScaleContinuedFraction extends ContinuedFraction {
        protected double getA(int n, double x) {
            return 0.0;
        }

        protected double getB(int n, double x) {
            return -1.0;
        }
    }

    private static final class ScalableContinuedFraction extends ContinuedFraction {
        protected double getA(int n, double x) {
            return 1e200;
        }

        protected double getB(int n, double x) {
            return 1e200;
        }
    }

    @Test
    public void testEvaluateStandard() throws Throwable {
        ContinuedFraction cf = new AlternatingContinuedFraction();
        double value = cf.evaluate(0.0, 1e-9, 10);
        Assert.assertTrue(Double.isFinite(value));
    }

    @Test
    public void testEvaluateZeroHPrev() throws Throwable {
        ContinuedFraction cf = new SimpleContinuedFraction(0.0, 1.0);
        double value = cf.evaluate(0.0, 1e-9, 10);
        Assert.assertTrue(Double.isFinite(value));
    }

    @Test
    public void testEvaluateMaxCountExceeded() throws Throwable {
        ContinuedFraction cf = new AlternatingContinuedFraction();
        try {
            cf.evaluate(1.0, 1e-16, 2);
            Assert.fail("Expected MaxCountExceededException");
        } catch (MaxCountExceededException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testInfinityDivergence() throws Throwable {
        ContinuedFraction cf = new InfiniteContinuedFraction();
        try {
            cf.evaluate(1.0, 1e-9, 10);
            Assert.fail("Expected ConvergenceException");
        } catch (ConvergenceException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testNanDivergence() throws Throwable {
        ContinuedFraction cf = new NaNContinuedFraction();
        try {
            cf.evaluate(1.0, 1e-9, 10);
            Assert.fail("Expected ConvergenceException");
        } catch (ConvergenceException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testZeroScaleDivergence() throws Throwable {
        ContinuedFraction cf = new ZeroScaleContinuedFraction();
        try {
            cf.evaluate(1.0, 1e-9, 10);
            Assert.fail("Expected ConvergenceException");
        } catch (ConvergenceException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testScalableBranch() throws Throwable {
        ContinuedFraction cf = new ScalableContinuedFraction();
        try {
            cf.evaluate(1.0, 1e-9, 5);
        } catch (ConvergenceException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testOverloads() throws Throwable {
        ContinuedFraction cf = new SimpleContinuedFraction(1.0, 1.0);
        double val1 = cf.evaluate(0.0);
        double val2 = cf.evaluate(0.0, 1e-6);
        double val3 = cf.evaluate(0.0, 5);
        Assert.assertTrue(Double.isFinite(val1));
        Assert.assertTrue(Double.isFinite(val2));
        Assert.assertTrue(Double.isFinite(val3));
    }
}