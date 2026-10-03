package org.apache.commons.math.analysis.solvers;

import org.apache.commons.math.analysis.UnivariateFunction;
import org.apache.commons.math.exception.NumberIsTooSmallException;
import org.apache.commons.math.exception.NoBracketingException;
import org.junit.Test;
import static org.junit.Assert.*;

public class BracketingNthOrderBrentSolverTest {

    @Test(expected = NumberIsTooSmallException.class)
    public void testInvalidMaximalOrder() throws Throwable {
        new BracketingNthOrderBrentSolver(1e-6, 1);
    }

    @Test(expected = NumberIsTooSmallException.class)
    public void testInvalidMaximalOrderWithRelative() throws Throwable {
        new BracketingNthOrderBrentSolver(1e-12, 1e-6, 1);
    }

    @Test(expected = NumberIsTooSmallException.class)
    public void testInvalidMaximalOrderWithFunctionValueAccuracy() throws Throwable {
        new BracketingNthOrderBrentSolver(1e-12, 1e-6, 1e-15, 1);
    }

    @Test
    public void testGetMaximalOrder() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver(1e-6, 5);
        assertEquals(5, solver.getMaximalOrder());
    }

    @Test
    public void testDefaultConstructor() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver();
        assertEquals(5, solver.getMaximalOrder());
    }

    @Test
    public void testSolveExactRootAtStartValue() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver();
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x * x - 4.0;
            }
        };
        double root = solver.solve(100, f, 0.0, 3.0, 2.0, AllowedSolution.ANY_SIDE);
        assertEquals(2.0, root, 1e-6);
    }

    @Test
    public void testSolveExactRootAtMin() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver();
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x;
            }
        };
        double root = solver.solve(100, f, 0.0, 2.0, AllowedSolution.ANY_SIDE);
        assertEquals(0.0, root, 1e-6);
    }

    @Test
    public void testSolveExactRootAtMax() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver();
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x - 2.0;
            }
        };
        double root = solver.solve(100, f, 0.0, 2.0, AllowedSolution.ANY_SIDE);
        assertEquals(2.0, root, 1e-6);
    }

    @Test(expected = NoBracketingException.class)
    public void testNoBracketingException() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver();
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x * x + 1.0;
            }
        };
        solver.solve(100, f, -1.0, 1.0, AllowedSolution.ANY_SIDE);
    }

    @Test
    public void testAllowedSolutionsLeftSide() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver();
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return (x - 1.0) * (x - 1.0);
            }
        };
        // Root is at 1.0, but let's test a function crossing zero or touching
        UnivariateFunction f2 = new UnivariateFunction() {
            public double value(double x) {
                return x - 1.0;
            }
        };
        double root = solver.solve(100, f2, 0.0, 2.0, AllowedSolution.LEFT_SIDE);
        assertEquals(1.0, root, 1e-6);
    }

    @Test
    public void testAllowedSolutionsRightSide() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver();
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x - 1.0;
            }
        };
        double root = solver.solve(100, f, 0.0, 2.0, AllowedSolution.RIGHT_SIDE);
        assertEquals(1.0, root, 1e-6);
    }

    @Test
    public void testAllowedSolutionsBelowSide() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver();
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x - 1.0;
            }
        };
        double root = solver.solve(100, f, 0.0, 2.0, AllowedSolution.BELOW_SIDE);
        assertEquals(1.0, root, 1e-6);
    }

    @Test
    public void testAllowedSolutionsAboveSide() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver();
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x - 1.0;
            }
        };
        double root = solver.solve(100, f, 0.0, 2.0, AllowedSolution.ABOVE_SIDE);
        assertEquals(1.0, root, 1e-6);
    }

    @Test
    public void testAgingMechanism() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver(1e-9, 5);
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return Math.sin(x);
            }
        };
        double root = solver.solve(100, f, -0.1, Math.PI, AllowedSolution.ANY_SIDE);
        assertEquals(0.0, root, 1e-6);
    }

    @Test
    public void testMaximalAgingTriggers() throws Throwable {
        BracketingNthOrderBrentSolver solver = new BracketingNthOrderBrentSolver(1e-12, 6);
        UnivariateFunction f = new UnivariateFunction() {
            public double value(double x) {
                return x * x * x - x - 2.0;
            }
        };
        double root = solver.solve(100, f, 1.0, 2.0, AllowedSolution.ANY_SIDE);
        assertTrue(root > 1.0);
    }
}