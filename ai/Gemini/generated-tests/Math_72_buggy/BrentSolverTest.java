package org.apache.commons.math.analysis.solvers;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.MaxIterationsExceededException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.junit.Test;

import static org.junit.Assert.*;

public class BrentSolverTest {

    @Test
    public void testConstructors() throws Throwable {
        BrentSolver solver1 = new BrentSolver();
        assertNotNull(solver1);

        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x - 4.0;
            }
        };
        BrentSolver solver2 = new BrentSolver(f);
        assertNotNull(solver2);
        
        double result = solver2.solve(0.0, 3.0);
        assertEquals(2.0, result, 1E-6);
    }

    @Test
    public void testSolveWithInitialGoodEnough() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x - 2.0;
            }
        };

        double result = solver.solve(f, 0.0, 5.0, 2.0);
        assertEquals(2.0, result, 1E-6);
    }

    @Test
    public void testSolveWithMinGoodEnough() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x - 1.0;
            }
        };

        double result = solver.solve(f, 1.0, 5.0, 3.0);
        assertEquals(1.0, result, 1E-6);
    }

    @Test
    public void testSolveWithMaxGoodEnough() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x - 4.0;
            }
        };

        double result = solver.solve(f, 1.0, 4.0, 2.0);
        assertEquals(4.0, result, 1E-6);
    }

    @Test
    public void testSolveInitialMinBracket() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x - 1.0; // root at 1
            }
        };

        // min = 0 (y=-1), initial = 0.5 (y=-0.75), max = 2 (y=3)
        // yInitial * yMin > 0, but min and initial bracket? Wait: y(0)=-1, y(0.5)=-0.75 -> both negative.
        // Let's make y(min) and y(initial) have opposite signs.
        // f(x) = x - 0.5. min=0 (y=-0.5), initial=0.2 (y=-0.3), max=1.0 (y=0.5)
        // Actually, let's design f such that yInitial * yMin < 0:
        // min = 0.0, y=-1.0; initial = 0.5, y=0.5. yMin * yInitial < 0.
        UnivariateRealFunction f2 = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x - 0.25;
            }
        };
        double result = solver.solve(f2, 0.0, 1.0, 0.1);
        assertEquals(0.25, result, 1E-6);
    }

    @Test
    public void testSolveInitialMaxBracket() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x - 0.75;
            }
        };
        double result = solver.solve(f, 0.0, 1.0, 0.9);
        assertEquals(0.75, result, 1E-6);
    }

    @Test
    public void testSolveNonBracketingThrowsException() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x + 1.0; // Always positive
            }
        };

        boolean caught = false;
        try {
            solver.solve(f, 0.0, 2.0, 1.0);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            solver.solve(f, 0.0, 2.0);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testSolveIntervalSignZero() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x;
            }
        };

        double resultMin = solver.solve(f, 0.0, 2.0);
        assertEquals(0.0, resultMin, 1E-6);

        UnivariateRealFunction f2 = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x - 2.0;
            }
        };
        double resultMax = solver.solve(f2, 0.0, 2.0);
        assertEquals(2.0, resultMax, 1E-6);
    }

    @Test
    public void testSolveSignNegativeWithEndpoints() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x - 1.5;
            }
        };
        double result = solver.solve(f, 0.0, 3.0);
        assertEquals(1.5, result, 1E-6);
    }

    @Test
    public void testLinearInterpolationAndInverseQuadraticPaths() throws Throwable {
        BrentSolver solver = new BrentSolver();
        // A cubic function to trigger various branches of Brent's method (linear vs inverse quadratic)
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return (x - 1.0) * (x - 2.0) * (x - 3.0);
            }
        };

        double res1 = solver.solve(f, 0.5, 1.5);
        assertEquals(1.0, res1, 1E-6);

        double res2 = solver.solve(f, 1.5, 2.5);
        assertEquals(2.0, res2, 1E-6);

        double res3 = solver.solve(f, 2.5, 3.5);
        assertEquals(3.0, res3, 1E-6);
    }

    @Test
    public void testMaxIterationsExceeded() throws Throwable {
        BrentSolver solver = new BrentSolver();
        solver.setMaximalIterationCount(2);
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return Math.sin(x);
            }
        };

        boolean caught = false;
        try {
            solver.solve(f, 1.0, 4.0, 2.0);
        } catch (MaxIterationsExceededException e) {
            caught = true;
        }
        assertTrue(caught);
    }
}