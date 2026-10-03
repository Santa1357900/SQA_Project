package org.apache.commons.math.analysis.solvers;

import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.exception.MathInternalError;
import org.junit.Assert;
import org.junit.Test;

public class BaseSecantSolverTest {

    private static class DummySecantSolver extends BaseSecantSolver {
        protected DummySecantSolver(final double absoluteAccuracy, final Method method) {
            super(absoluteAccuracy, method);
        }

        protected DummySecantSolver(final double relativeAccuracy,
                                    final double absoluteAccuracy,
                                    final Method method) {
            super(relativeAccuracy, absoluteAccuracy, method);
        }

        protected DummySecantSolver(final double relativeAccuracy,
                                    final double absoluteAccuracy,
                                    final double functionValueAccuracy,
                                    final Method method) {
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

    private static class SquareFunction implements UnivariateRealFunction {
        public double value(double x) {
            return x * x - 4.0;
        }
    }

    @Test
    public void testConstructorsAndSolves() throws Throwable {
        BaseSecantSolver solver1 = new DummySecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        BaseSecantSolver solver2 = new DummySecantSolver(1e-12, 1e-6, BaseSecantSolver.Method.ILLINOIS);
        BaseSecantSolver solver3 = new DummySecantSolver(1e-12, 1e-6, 1e-9, BaseSecantSolver.Method.PEGASUS);

        UnivariateRealFunction f = new LinearFunction(2.0, -4.0); // Root at x = 2.0

        double r1 = solver1.solve(100, f, 0.0, 3.0);
        Assert.assertEquals(2.0, r1, 1e-4);

        double r2 = solver2.solve(100, f, 0.0, 3.0, 1.5);
        Assert.assertEquals(2.0, r2, 1e-4);

        double r3 = solver3.solve(100, f, 0.0, 3.0, AllowedSolution.ANY_SIDE);
        Assert.assertEquals(2.0, r3, 1e-4);

        double r4 = solver3.solve(100, f, 0.0, 3.0, 1.5, AllowedSolution.ANY_SIDE);
        Assert.assertEquals(2.0, r4, 1e-4);
    }

    @Test
    public void testBoundsAreExactRoots() throws Throwable {
        BaseSecantSolver solver = new DummySecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        UnivariateRealFunction f = new LinearFunction(1.0, 0.0); // Root at x = 0.0

        double rootMin = solver.solve(100, f, 0.0, 5.0);
        Assert.assertEquals(0.0, rootMin, 1e-6);

        UnivariateRealFunction f2 = new UnivariateRealFunction() {
            public double value(double x) {
                return x - 3.0;
            }
        };
        double rootMax = solver.solve(100, f2, 0.0, 3.0);
        Assert.assertEquals(3.0, rootMax, 1e-6);
    }

    @Test
    public void testAllowedSolutionsRegulaFalsi() throws Throwable {
        BaseSecantSolver solver = new DummySecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        UnivariateRealFunction f = new SquareFunction(); // Roots at -2 and 2

        double rAny = solver.solve(100, f, 1.0, 3.0, AllowedSolution.ANY_SIDE);
        double rLeft = solver.solve(100, f, 1.0, 3.0, AllowedSolution.LEFT_SIDE);
        double rRight = solver.solve(100, f, 1.0, 3.0, AllowedSolution.RIGHT_SIDE);
        double rBelow = solver.solve(100, f, 1.0, 3.0, AllowedSolution.BELOW_SIDE);
        double rAbove = solver.solve(100, f, 1.0, 3.0, AllowedSolution.ABOVE_SIDE);

        Assert.assertEquals(2.0, rAny, 1e-3);
        Assert.assertEquals(2.0, rLeft, 1e-3);
        Assert.assertEquals(2.0, rRight, 1e-3);
        Assert.assertEquals(2.0, rBelow, 1e-3);
        Assert.assertEquals(2.0, rAbove, 1e-3);
    }

    @Test
    public void testIllinoisMethod() throws Throwable {
        BaseSecantSolver solver = new DummySecantSolver(1e-6, BaseSecantSolver.Method.ILLINOIS);
        UnivariateRealFunction f = new SquareFunction();
        double r = solver.solve(100, f, 1.0, 3.0, AllowedSolution.ANY_SIDE);
        Assert.assertEquals(2.0, r, 1e-3);
    }

    @Test
    public void testPegasusMethod() throws Throwable {
        BaseSecantSolver solver = new DummySecantSolver(1e-6, BaseSecantSolver.Method.PEGASUS);
        UnivariateRealFunction f = new SquareFunction();
        double r = solver.solve(100, f, 1.0, 3.0, AllowedSolution.ANY_SIDE);
        Assert.assertEquals(2.0, r, 1e-3);
    }

    @Test
    public void testExactIntermediateRoot() throws Throwable {
        BaseSecantSolver solver = new DummySecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) {
                if (x == 1.5) {
                    return 0.0;
                }
                return x - 2.0;
            }
        };
        double r = solver.solve(100, f, 0.0, 3.0);
        Assert.assertEquals(1.5, r, 1e-6);
    }
}