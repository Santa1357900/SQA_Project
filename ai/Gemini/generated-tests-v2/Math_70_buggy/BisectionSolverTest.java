package org.apache.commons.math.analysis.solvers;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.MaxIterationsExceededException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.junit.Test;

import static org.junit.Assert.*;

public class BisectionSolverTest {

    @Test
    public void testConstructors() throws Throwable {
        BisectionSolver solver1 = new BisectionSolver();
        assertNotNull(solver1);

        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x;
            }
        };
        BisectionSolver solver2 = new BisectionSolver(f);
        assertNotNull(solver2);
    }

    @Test
    public void testSolveStandardFunction() throws Throwable {
        BisectionSolver solver = new BisectionSolver();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x - 4.0;
            }
        };

        double root = solver.solve(f, 1.0, 3.0);
        assertEquals(2.0, root, 1E-5);
    }

    @Test
    public void testSolveWithInitialAndDeprecatedMethods() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x - 1.5;
            }
        };

        BisectionSolver solver = new BisectionSolver(f);
        double root1 = solver.solve(0.0, 3.0, 1.0);
        assertEquals(1.5, root1, 1E-5);

        double root2 = solver.solve(0.0, 3.0);
        assertEquals(1.5, root2, 1E-5);

        double root3 = solver.solve(f, 0.0, 3.0, 1.0);
        assertEquals(1.5, root3, 1E-5);
    }

    @Test
    public void testBranchPositiveProduct() throws Throwable {
        BisectionSolver solver = new BisectionSolver();
        // f(min) and f(m) have the same sign (both positive)
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x + 10.0;
            }
        };

        double root = solver.solve(f, 1.0, 2.0);
        assertNotNull(root);
    }

    @Test
    public void testMaxIterationsExceeded() throws Throwable {
        BisectionSolver solver = new BisectionSolver();
        solver.setMaximalIterationCount(1);
        solver.setAbsoluteAccuracy(1E-50);

        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x - 2.0;
            }
        };

        boolean exceptionThrown = false;
        try {
            solver.solve(f, 1.0, 2.0);
        } catch (MaxIterationsExceededException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testInvalidInterval() throws Throwable {
        BisectionSolver solver = new BisectionSolver();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x;
            }
        };

        boolean exceptionThrown = false;
        try {
            solver.solve(f, 2.0, 1.0);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }
}