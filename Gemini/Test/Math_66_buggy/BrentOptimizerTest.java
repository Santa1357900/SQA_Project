package org.apache.commons.math.optimization.univariate;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.MaxIterationsExceededException;
import org.apache.commons.math.exception.NotStrictlyPositiveException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.optimization.GoalType;

public class BrentOptimizerTest {

    @Test
    public void testConstructorAndDefaults() throws Throwable {
        BrentOptimizer optimizer = new BrentOptimizer();
        assertNotNull(optimizer);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDoOptimizeThrowsException() throws Throwable {
        BrentOptimizer optimizer = new BrentOptimizer();
        optimizer.doOptimize();
    }

    @Test
    public void testMinimizeParabolicFunction() throws Throwable {
        BrentOptimizer optimizer = new BrentOptimizer();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return (x - 2.0) * (x - 2.0) + 1.0;
            }
        };

        double min = optimizer.optimize(f, GoalType.MINIMIZE, 0.0, 4.0);
        assertEquals(2.0, min, 1e-6);
        assertEquals(1.0, optimizer.getResult(), 1e-6);
    }

    @Test
    public void testMaximizeParabolicFunction() throws Throwable {
        BrentOptimizer optimizer = new BrentOptimizer();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return -((x - 2.0) * (x - 2.0)) + 5.0;
            }
        };

        double max = optimizer.optimize(f, GoalType.MAXIMIZE, 0.0, 4.0);
        assertEquals(2.0, max, 1e-6);
        assertEquals(5.0, optimizer.getResult(), 1e-6);
    }

    @Test
    public void testMinimizeWithStartValueAndInvertedBounds() throws Throwable {
        BrentOptimizer optimizer = new BrentOptimizer();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x;
            }
        };

        // Inverted bounds (hi < lo) to test boundary swapping and startValue
        double min = optimizer.optimize(f, GoalType.MINIMIZE, 5.0, -5.0, 1.0);
        assertEquals(0.0, min, 1e-6);
    }

    @Test
    public void testMaxIterationsExceeded() throws Throwable {
        BrentOptimizer optimizer = new BrentOptimizer();
        optimizer.setMaximalIterationCount(2); // Very low iterations to force exception

        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return Math.sin(x);
            }
        };

        try {
            optimizer.optimize(f, GoalType.MINIMIZE, 0.0, Math.PI);
            fail("Expected MaxIterationsExceededException");
        } catch (MaxIterationsExceededException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testAccuracyExceptions() throws Throwable {
        BrentOptimizer optimizer = new BrentOptimizer();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x;
            }
        };

        // Use reflection or direct package access if possible, but since localMin is private,
        // we test via optimize if accuracies could be invalid, wait, localMin validates eps and t.
        // Let's check if setter allows non-positive accuracy or if we can invoke localMin via subclass or if it's strictly private.
        // Since localMin is private, let's test setting invalid accuracies if setters exist, or verify standard flow.
        // Wait, AbstractUnivariateRealOptimizer has setAbsoluteAccuracy and setRelativeAccuracy.
        optimizer.setAbsoluteAccuracy(0.0);
        try {
            optimizer.optimize(f, GoalType.MINIMIZE, -1.0, 1.0);
            fail("Expected NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testGoldenSectionBranchingAndParabolicInterpolation() throws Throwable {
        BrentOptimizer optimizer = new BrentOptimizer();
        optimizer.setAbsoluteAccuracy(1e-8);
        optimizer.setRelativeAccuracy(1e-8);

        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                // A complex function to trigger various branches (parabolic fit, golden section, updates)
                return Math.abs(x - 0.3) + Math.sin(x);
            }
        };

        double result = optimizer.optimize(f, GoalType.MINIMIZE, -1.0, 1.0, 0.0);
        assertTrue(result >= -1.0 && result <= 1.0);
    }

    @Test
    public void testFunctionEvaluationExceptionPropagation() throws Throwable {
        BrentOptimizer optimizer = new BrentOptimizer();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                if (x > 0.5) {
                    throw new FunctionEvaluationException(x, "Error at x > 0.5");
                }
                return x * x;
            }
        };

        try {
            optimizer.optimize(f, GoalType.MINIMIZE, 0.0, 1.0, 0.2);
            fail("Expected FunctionEvaluationException");
        } catch (FunctionEvaluationException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testXEqualsMTermination() throws Throwable {
        BrentOptimizer optimizer = new BrentOptimizer();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return 0.0; // Flat function to hit termination conditions quickly
            }
        };

        double res = optimizer.optimize(f, GoalType.MINIMIZE, 0.0, 1.0, 0.5);
        assertEquals(0.5, res, 1e-1);
    }
}