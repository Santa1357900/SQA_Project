package org.apache.commons.math.analysis.solvers;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.MaxIterationsExceededException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.junit.Test;

import static org.junit.Assert.*;

public class BrentSolverTest {

    private static class ConstantFunction implements UnivariateRealFunction {
        private final double value;
        public ConstantFunction(double value) {
            this.value = value;
        }
        public double value(double x) throws FunctionEvaluationException {
            return value;
        }
    }

    private static class SinFunction implements UnivariateRealFunction {
        public double value(double x) throws FunctionEvaluationException {
            return Math.sin(x);
        }
    }

    private static class QuadraticFunction implements UnivariateRealFunction {
        public double value(double x) throws FunctionEvaluationException {
            return x * x - 4.0;
        }
    }

    private static class IllConditionedFunction implements UnivariateRealFunction {
        public double value(double x) throws FunctionEvaluationException {
            return (x - 1.0) * 1e-10;
        }
    }

    @Test
    public void testConstructors() throws Throwable {
        BrentSolver solver1 = new BrentSolver(new SinFunction());
        assertNotNull(solver1);

        BrentSolver solver2 = new BrentSolver();
        assertNotNull(solver2);
    }

    @Test
    public void testSolveWithMinMaxSin() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new SinFunction();
        double root = solver.solve(f, 3.0, 4.0);
        assertEquals(Math.PI, root, 1E-6);
    }

    @Test
    public void testSolveWithInitialGuess() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new SinFunction();
        double root = solver.solve(f, 3.0, 4.0, 3.1);
        assertEquals(Math.PI, root, 1E-6);
    }

    @Test
    public void testInitialGuessGoodEnough() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new SinFunction();
        double root = solver.solve(f, 3.0, 4.0, Math.PI);
        assertEquals(Math.PI, root, 1E-6);
    }

    @Test
    public void testMinEndpointGoodEnough() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new SinFunction();
        double root = solver.solve(f, Math.PI, 4.0, 3.5);
        assertEquals(Math.PI, root, 1E-6);
    }

    @Test
    public void testMaxEndpointGoodEnough() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new SinFunction();
        double root = solver.solve(f, 3.0, Math.PI, 3.1);
        assertEquals(Math.PI, root, 1E-6);
    }

    @Test
    public void testInitialAndMinBracketRoot() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new SinFunction();
        // min = 3.0 (sin(3) > 0), initial = 3.2 (sin(3.2) < 0), max = 4.0
        double root = solver.solve(f, 3.0, 4.0, 3.14);
        assertEquals(Math.PI, root, 1E-6);
    }

    @Test
    public void testInitialAndMaxBracketRoot() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new SinFunction();
        // min = 3.0, initial = 3.14 (sin > 0), max = 3.5 (sin < 0)
        double root = solver.solve(f, 3.0, 3.5, 3.14);
        assertEquals(Math.PI, root, 1E-6);
    }

    @Test
    public void testSolveQuadratic() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new QuadraticFunction();
        double root = solver.solve(f, 0.0, 3.0);
        assertEquals(2.0, root, 1E-6);
    }

    @Test
    public void testSolveQuadraticWithInitial() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new QuadraticFunction();
        double root = solver.solve(f, 0.0, 3.0, 1.5);
        assertEquals(2.0, root, 1E-6);
    }

    @Test
    public void testRootAtMinOrMaxWithoutBracketing() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new QuadraticFunction();
        // min is root (2.0 is root for x^2-4, let's use f(x) = x - 2)
        UnivariateRealFunction f2 = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x - 2.0;
            }
        };
        double rootMin = solver.solve(f2, 2.0, 4.0);
        assertEquals(2.0, rootMin, 1E-6);

        double rootMax = solver.solve(f2, 0.0, 2.0);
        assertEquals(2.0, rootMax, 1E-6);
    }

    @Test
    public void testNonBracketingThrowsException() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new ConstantFunction(1.0);
        try {
            solver.solve(f, 0.0, 1.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("do not have different signs"));
        }
    }

    @Test
    public void testZeroSignBothEndpointsZero() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new ConstantFunction(0.0);
        double root = solver.solve(f, 0.0, 1.0);
        assertEquals(0.0, root, 1E-6);
    }

    @Test
    public void testDeprecatedMethods() throws Throwable {
        BrentSolver solver = new BrentSolver(new SinFunction());
        double root1 = solver.solve(3.0, 4.0);
        assertEquals(Math.PI, root1, 1E-6);

        double root2 = solver.solve(3.0, 4.0, 3.1);
        assertEquals(Math.PI, root2, 1E-6);
    }

    @Test
    public void testMaxIterationsExceeded() throws Throwable {
        BrentSolver solver = new BrentSolver();
        solver.setMaximalIterationCount(0);
        UnivariateRealFunction f = new SinFunction();
        try {
            solver.solve(f, 3.0, 4.0);
            fail("Expected MaxIterationsExceededException");
        } catch (MaxIterationsExceededException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testFunctionAccuracyReached() throws Throwable {
        BrentSolver solver = new BrentSolver();
        solver.setFunctionValueAccuracy(0.1);
        UnivariateRealFunction f = new IllConditionedFunction();
        double root = solver.solve(f, 0.0, 2.0);
        assertTrue(Math.abs(f.value(root)) <= 0.1 || Math.abs(root - 1.0) < 1E-3);
    }

    @Test
    public void testBisectionFallbackBranch() throws Throwable {
        BrentSolver solver = new BrentSolver();
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) throws FunctionEvaluationException {
                return x * x * x - x - 2.0;
            }
        };
        double root = solver.solve(f, 1.0, 2.0);
        assertEquals(1.521379, root, 1E-3);
    }
}