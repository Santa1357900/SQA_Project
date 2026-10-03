package org.apache.commons.math.analysis.solvers;

import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.exception.MathInternalError;
import org.junit.Test;

import static org.junit.Assert.*;

public class BaseSecantSolverTest {

    private static class DummySecantSolver extends BaseSecantSolver {
        public DummySecantSolver(final double absoluteAccuracy, final Method method) {
            super(absoluteAccuracy, method);
        }

        public DummySecantSolver(final double relativeAccuracy, final double absoluteAccuracy, final Method method) {
            super(relativeAccuracy, absoluteAccuracy, method);
        }

        public DummySecantSolver(final double relativeAccuracy, final double absoluteAccuracy, final double functionValueAccuracy, final Method method) {
            super(relativeAccuracy, absoluteAccuracy, functionValueAccuracy, method);
        }
    }

    private static class LinearFunction implements UnivariateRealFunction {
        private final double slope;
        private final double intercept;

        public LinearFunction(double slope, double intercept) {
            this.slope = slope;
            this.intercept = intercept;
        }

        public double value(double x) {
            return slope * x + intercept;
        }
    }

    private static class SinFunction implements UnivariateRealFunction {
        public double value(double x) {
            return Math.sin(x);
        }
    }

    @Test
    public void testConstructors() throws Throwable {
        DummySecantSolver solver1 = new DummySecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        assertNotNull(solver1);

        DummySecantSolver solver2 = new DummySecantSolver(1e-12, 1e-6, BaseSecantSolver.Method.ILLINOIS);
        assertNotNull(solver2);

        DummySecantSolver solver3 = new DummySecantSolver(1e-12, 1e-6, 1e-8, BaseSecantSolver.Method.PEGASUS);
        assertNotNull(solver3);
    }

    @Test
    public void testSolveWithAllowedSolutions() throws Throwable {
        UnivariateRealFunction f = new LinearFunction(1.0, -2.0); // root at x = 2.0

        DummySecantSolver solverRegula = new DummySecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        double res1 = solverRegula.solve(100, f, 0.0, 3.0, AllowedSolution.ANY_SIDE);
        assertTrue(Math.abs(res1 - 2.0) < 1e-5);

        double res2 = solverRegula.solve(100, f, 0.0, 3.0, 1.5, AllowedSolution.ANY_SIDE);
        assertTrue(Math.abs(res2 - 2.0) < 1e-5);

        double res3 = solverRegula.solve(100, f, 0.0, 3.0, AllowedSolution.LEFT_SIDE);
        assertTrue(Math.abs(res3 - 2.0) < 1e-5);

        double res4 = solverRegula.solve(100, f, 0.0, 3.0, AllowedSolution.RIGHT_SIDE);
        assertTrue(Math.abs(res4 - 2.0) < 1e-5);

        double res5 = solverRegula.solve(100, f, 0.0, 3.0, AllowedSolution.BELOW_SIDE);
        assertTrue(Math.abs(res5 - 2.0) < 1e-5);

        double res6 = solverRegula.solve(100, f, 0.0, 3.0, AllowedSolution.ABOVE_SIDE);
        assertTrue(Math.abs(res6 - 2.0) < 1e-5);
    }

    @Test
    public void testExactRootBounds() throws Throwable {
        UnivariateRealFunction f = new LinearFunction(1.0, 0.0); // root at x = 0.0
        DummySecantSolver solver = new DummySecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);

        double resMin = solver.solve(100, f, 0.0, 5.0);
        assertEquals(0.0, resMin, 1e-12);

        UnivariateRealFunction f2 = new LinearFunction(1.0, -5.0); // root at x = 5.0
        double resMax = solver.solve(100, f2, 0.0, 5.0);
        assertEquals(5.0, resMax, 1e-12);
    }

    @Test
    public void testMethodsCoverage() throws Throwable {
        UnivariateRealFunction f = new SinFunction(); // root at 0 or pi

        DummySecantSolver solverRegula = new DummySecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        double r1 = solverRegula.solve(100, f, 3.0, 4.0, AllowedSolution.ANY_SIDE);
        assertTrue(Math.abs(r1 - Math.PI) < 1e-5);

        DummySecantSolver solverIllinois = new DummySecantSolver(1e-6, BaseSecantSolver.Method.ILLINOIS);
        double r2 = solverIllinois.solve(100, f, 3.0, 4.0, AllowedSolution.ANY_SIDE);
        assertTrue(Math.abs(r2 - Math.PI) < 1e-5);

        DummySecantSolver solverPegasus = new DummySecantSolver(1e-6, BaseSecantSolver.Method.PEGASUS);
        double r3 = solverPegasus.solve(100, f, 3.0, 4.0, AllowedSolution.ANY_SIDE);
        assertTrue(Math.abs(r3 - Math.PI) < 1e-5);
    }

    @Test
    public void testAllowedSolutionBranches() throws Throwable {
        UnivariateRealFunction f = new LinearFunction(1.0, -2.0);

        DummySecantSolver solver = new DummySecantSolver(1e-2, 1e-2, BaseSecantSolver.Method.REGULA_FALSI);
        
        double leftSol = solver.solve(100, f, 0.0, 3.0, AllowedSolution.LEFT_SIDE);
        assertTrue(leftSol <= 2.0 || Math.abs(leftSol - 2.0) < 0.1);

        double rightSol = solver.solve(100, f, 0.0, 3.0, AllowedSolution.RIGHT_SIDE);
        assertTrue(rightSol >= 2.0 || Math.abs(rightSol - 2.0) < 0.1);

        double belowSol = solver.solve(100, f, 0.0, 3.0, AllowedSolution.BELOW_SIDE);
        assertTrue(belowSol <= 2.0 || Math.abs(belowSol - 2.0) < 0.1);

        double aboveSol = solver.solve(100, f, 0.0, 3.0, AllowedSolution.ABOVE_SIDE);
        assertTrue(aboveSol >= 2.0 || Math.abs(aboveSol - 2.0) < 0.1);
    }
}