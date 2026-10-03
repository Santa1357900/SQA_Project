package org.apache.commons.math.analysis.solvers;

import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.exception.ConvergenceException;
import org.apache.commons.math.exception.MathInternalError;
import org.junit.Test;

import static org.junit.Assert.*;

public class BaseSecantSolverTest {

    private static class DummySecantSolver extends BaseSecantSolver {
        public DummySecantSolver(double absoluteAccuracy, Method method) {
            super(absoluteAccuracy, method);
        }

        public DummySecantSolver(double relativeAccuracy, double absoluteAccuracy, Method method) {
            super(relativeAccuracy, absoluteAccuracy, method);
        }

        public DummySecantSolver(double relativeAccuracy, double absoluteAccuracy, double functionValueAccuracy, Method method) {
            super(relativeAccuracy, absoluteAccuracy, functionValueAccuracy, method);
        }
    }

    private static class LinearFunction implements UnivariateRealFunction {
        public double value(double x) {
            return x - 2.0;
        }
    }

    private static class QuadraticFunction implements UnivariateRealFunction {
        public double value(double x) {
            return x * x - 4.0;
        }
    }

    @Test
    public void testConstructorsAndSolveVariants() throws Throwable {
        DummySecantSolver solver1 = new DummySecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        DummySecantSolver solver2 = new DummySecantSolver(1e-12, 1e-6, BaseSecantSolver.Method.ILLINOIS);
        DummySecantSolver solver3 = new DummySecantSolver(1e-12, 1e-6, 1e-9, BaseSecantSolver.Method.PEGASUS);

        UnivariateRealFunction f = new LinearFunction();

        double root1 = solver1.solve(100, f, 0.0, 5.0);
        assertEquals(2.0, root1, 1e-6);

        double root2 = solver2.solve(100, f, 0.0, 5.0, AllowedSolution.ANY_SIDE);
        assertEquals(2.0, root2, 1e-6);

        double root3 = solver3.solve(100, f, 0.0, 5.0, 2.5, AllowedSolution.ANY_SIDE);
        assertEquals(2.0, root3, 1e-6);
    }

    @Test
    public void testExactRootBounds() throws Throwable {
        DummySecantSolver solver = new DummySecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        UnivariateRealFunction f = new LinearFunction();

        double rootMin = solver.solve(100, f, 2.0, 5.0);
        assertEquals(2.0, rootMin, 1e-15);

        double rootMax = solver.solve(100, f, 0.0, 2.0);
        assertEquals(2.0, rootMax, 1e-15);
    }

    @Test
    public void testAllowedSolutionsAllSides() throws Throwable {
        UnivariateRealFunction f = new QuadraticFunction();

        for (BaseSecantSolver.Method method : BaseSecantSolver.Method.values()) {
            DummySecantSolver solver = new DummySecantSolver(1e-6, method);

            assertEquals(2.0, solver.solve(100, f, 0.0, 3.0, AllowedSolution.ANY_SIDE), 1e-4);
            assertEquals(2.0, solver.solve(100, f, 0.0, 3.0, AllowedSolution.LEFT_SIDE), 1e-4);
            assertEquals(2.0, solver.solve(100, f, 0.0, 3.0, AllowedSolution.RIGHT_SIDE), 1e-4);
            assertEquals(2.0, solver.solve(100, f, 0.0, 3.0, AllowedSolution.BELOW_SIDE), 1e-4);
            assertEquals(2.0, solver.solve(100, f, 0.0, 3.0, AllowedSolution.ABOVE_SIDE), 1e-4);
        }
    }

    @Test
    public void testMethodsBranchCoverage() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) {
                return x * x * x - 2 * x - 5;
            }
        };

        DummySecantSolver solverRegula = new DummySecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        assertEquals(2.09455, solverRegula.solve(100, f, 2.0, 3.0, AllowedSolution.ABOVE_SIDE), 1e-3);

        DummySecantSolver solverIllinois = new DummySecantSolver(1e-6, BaseSecantSolver.Method.ILLINOIS);
        assertEquals(2.09455, solverIllinois.solve(100, f, 2.0, 3.0, AllowedSolution.BELOW_SIDE), 1e-3);

        DummySecantSolver solverPegasus = new DummySecantSolver(1e-6, BaseSecantSolver.Method.PEGASUS);
        assertEquals(2.09455, solverPegasus.solve(100, f, 2.0, 3.0, AllowedSolution.LEFT_SIDE), 1e-3);
    }

    @Test
    public void testInvertedIntervalsAndSideAccuracies() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) {
                return Math.sin(x);
            }
        };

        DummySecantSolver solver = new DummySecantSolver(1e-8, BaseSecantSolver.Method.PEGASUS);
        double rootLeft = solver.solve(100, f, -0.5, 0.5, AllowedSolution.LEFT_SIDE);
        assertEquals(0.0, rootLeft, 1e-6);

        double rootRight = solver.solve(100, f, -0.5, 0.5, AllowedSolution.RIGHT_SIDE);
        assertEquals(0.0, rootRight, 1e-6);
    }
}