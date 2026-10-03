package org.apache.commons.math.analysis;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.MaxIterationsExceededException;
import org.junit.Test;
import static org.junit.Assert.*;

public class BrentSolverTest {

    @Test
    public void testConstructor() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x - 4.0;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        assertNotNull(solver);
    }

    @Test
    public void testSolveIntervalRootAtMin() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        double root = solver.solve(0.0, 5.0);
        assertEquals(0.0, root, 1E-6);
    }

    @Test
    public void testSolveIntervalStandard() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x - 4.0;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        double root = solver.solve(1.0, 3.0);
        assertEquals(2.0, root, 1E-6);
    }

    @Test
    public void testSolveIntervalNegativeRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x - 4.0;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        double root = solver.solve(-3.0, -1.0);
        assertEquals(-2.0, root, 1E-6);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSolveIntervalSameSign() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x + 1.0;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        solver.solve(1.0, 3.0);
    }

    @Test
    public void testSolveWithInitialGoodInitial() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x - 2.0;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        double root = solver.solve(0.0, 5.0, 2.0);
        assertEquals(2.0, root, 1E-6);
    }

    @Test
    public void testSolveWithInitialGoodMin() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        double root = solver.solve(0.0, 5.0, 1.0);
        assertEquals(0.0, root, 1E-6);
    }

    @Test
    public void testSolveWithInitialGoodMax() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x - 5.0;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        double root = solver.solve(0.0, 5.0, 1.0);
        assertEquals(5.0, root, 1E-6);
    }

    @Test
    public void testSolveWithInitialBracketMin() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x - 1.0;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        double root = solver.solve(-2.0, 2.0, -0.5);
        assertEquals(-1.0, root, 1E-6);
    }

    @Test
    public void testSolveWithInitialBracketMax() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x - 1.0;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        double root = solver.solve(-2.0, 2.0, 0.5);
        assertEquals(1.0, root, 1E-6);
    }

    @Test
    public void testSolveWithInitialFullBrent() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x - 4.0;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        double root = solver.solve(0.0, 3.0, 1.5);
        assertEquals(2.0, root, 1E-6);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSolveWithInitialInvalid() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        solver.solve(0.0, 5.0, 6.0);
    }

    @Test
    public void testBrentInternalInverseQuadraticAndBisectionBranches() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x * x - x - 2.0;
            }
        };
        BrentSolver solver = new BrentSolver(f);
        double root = solver.solve(1.0, 2.0);
        assertTrue(root > 1.0 && root < 2.0);
    }

    @Test
    public void testBrentInternalDeltaZeroAndEdgeCases() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return Math.sin(x);
            }
        };
        BrentSolver solver = new BrentSolver(f);
        double root = solver.solve(3.0, 4.0, 3.14159);
        assertEquals(Math.PI, root, 1E-4);
    }
}