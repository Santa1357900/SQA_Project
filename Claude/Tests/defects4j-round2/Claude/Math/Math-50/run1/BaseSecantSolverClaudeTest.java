package org.apache.commons.math.analysis.solvers;

import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.math.analysis.UnivariateRealFunction;

public class BaseSecantSolverClaudeTest {

    private static class TestSecantSolver extends BaseSecantSolver {
        TestSecantSolver(double absoluteAccuracy, Method method) {
            super(absoluteAccuracy, method);
        }
        TestSecantSolver(double relativeAccuracy, double absoluteAccuracy, Method method) {
            super(relativeAccuracy, absoluteAccuracy, method);
        }
        TestSecantSolver(double relativeAccuracy, double absoluteAccuracy,
                          double functionValueAccuracy, Method method) {
            super(relativeAccuracy, absoluteAccuracy, functionValueAccuracy, method);
        }
    }

    private UnivariateRealFunction linearFunction(final double root) {
        return new UnivariateRealFunction() {
            public double value(double x) {
                return x - root;
            }
        };
    }

    private UnivariateRealFunction squareMinusTwo() {
        return new UnivariateRealFunction() {
            public double value(double x) {
                return x * x - 2.0;
            }
        };
    }

    private UnivariateRealFunction fifthPowerMinusOne() {
        return new UnivariateRealFunction() {
            public double value(double x) {
                return x * x * x * x * x - 1.0;
            }
        };
    }

    private UnivariateRealFunction alwaysPositive() {
        return new UnivariateRealFunction() {
            public double value(double x) {
                return x * x + 1.0;
            }
        };
    }

    private UnivariateRealFunction cubicRootNegative() {
        return new UnivariateRealFunction() {
            public double value(double x) {
                return x * x * x + 8.0;
            }
        };
    }

    // covers: protected BaseSecantSolver(double absoluteAccuracy, Method method)
    @Test
    public void testConstructorTwoArg_assignsAbsoluteAccuracy() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-5, BaseSecantSolver.Method.ILLINOIS);
        assertEquals(1e-5, solver.getAbsoluteAccuracy(), 1e-15);
    }

    // covers: protected BaseSecantSolver(double relativeAccuracy, double absoluteAccuracy, Method method)
    @Test
    public void testConstructorThreeArg_assignsRelativeAndAbsoluteAccuracy() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-10, 1e-7, BaseSecantSolver.Method.PEGASUS);
        assertEquals(1e-10, solver.getRelativeAccuracy(), 1e-18);
        assertEquals(1e-7, solver.getAbsoluteAccuracy(), 1e-15);
    }

    // covers: protected BaseSecantSolver(relativeAccuracy, absoluteAccuracy, functionValueAccuracy, Method)
    @Test
    public void testConstructorFourArg_assignsFunctionValueAccuracy() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-10, 1e-7, 1e-4, BaseSecantSolver.Method.REGULA_FALSI);
        assertEquals(1e-4, solver.getFunctionValueAccuracy(), 1e-12);
    }

    // covers: if (f0 == 0.0) return x0;
    @Test
    public void testDoSolve_minIsExactRoot_returnsMin() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        double result = solver.solve(100, linearFunction(1.0), 1.0, 5.0, AllowedSolution.ANY_SIDE);
        assertEquals(1.0, result, 1e-12);
    }

    // covers: if (f1 == 0.0) return x1;
    @Test
    public void testDoSolve_maxIsExactRoot_returnsMax() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        double result = solver.solve(100, linearFunction(5.0), 1.0, 5.0, AllowedSolution.ANY_SIDE);
        assertEquals(5.0, result, 1e-12);
    }

    // covers: verifyBracketing(x0, x1) throwing when f0 and f1 share the same sign
    @Test
    public void testDoSolve_noBracketing_throwsException() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        try {
            solver.solve(100, alwaysPositive(), -1.0, 1.0, AllowedSolution.ANY_SIDE);
            fail("expected exception because interval does not bracket a root");
        } catch (Exception expected) {
            assertNotNull(expected);
        }
    }

    // covers: if (fx == 0.0) return x; inside main loop (secant step exact for a linear function)
    @Test
    public void testDoSolve_linearFunction_exactRootFirstIteration() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-6, BaseSecantSolver.Method.REGULA_FALSI);
        double result = solver.solve(100, linearFunction(3.0), 0.0, 10.0, AllowedSolution.ANY_SIDE);
        assertEquals(3.0, result, 1e-9);
    }

    // covers: REGULA_FALSI branch of the method switch under normal convergence
    @Test
    public void testDoSolve_regulaFalsi_convergesToRoot() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.REGULA_FALSI);
        double result = solver.solve(1000, squareMinusTwo(), 0.0, 2.0, AllowedSolution.ANY_SIDE);
        assertEquals(Math.sqrt(2.0), result, 1e-6);
    }

    // covers: ILLINOIS branch (f0 *= 0.5)
    @Test
    public void testDoSolve_illinois_convergesToRoot() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.ILLINOIS);
        double result = solver.solve(1000, squareMinusTwo(), 0.0, 2.0, AllowedSolution.ANY_SIDE);
        assertEquals(Math.sqrt(2.0), result, 1e-6);
    }

    // covers: PEGASUS branch (f0 *= f1 / (f1 + fx))
    @Test
    public void testDoSolve_pegasus_convergesToRoot() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.PEGASUS);
        double result = solver.solve(1000, squareMinusTwo(), 0.0, 2.0, AllowedSolution.ANY_SIDE);
        assertEquals(Math.sqrt(2.0), result, 1e-6);
    }

    // covers: solve(maxEval,f,min,max,startValue,allowedSolution) overload
    @Test
    public void testSolve6Arg_customStartValue_convergesToRoot() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.REGULA_FALSI);
        double result = solver.solve(1000, squareMinusTwo(), 0.0, 2.0, 1.9, AllowedSolution.ANY_SIDE);
        assertEquals(Math.sqrt(2.0), result, 1e-6);
    }

    // covers: public double solve(maxEval, f, min, max, startValue) delegating to ANY_SIDE
    @Test
    public void testSolveOverride_defaultAllowedSolution_convergesToRoot() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.PEGASUS);
        double result = solver.solve(1000, squareMinusTwo(), 0.0, 2.0, 1.0);
        assertEquals(Math.sqrt(2.0), result, 1e-6);
    }

    // covers: case LEFT_SIDE in both the ftol-stop and interval-stop switches
    @Test
    public void testAllowedSolution_leftSide_resultNotGreaterThanRoot() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.ILLINOIS);
        double result = solver.solve(1000, squareMinusTwo(), 0.0, 2.0, AllowedSolution.LEFT_SIDE);
        assertTrue(result <= Math.sqrt(2.0) + 1e-6);
    }

    // covers: case RIGHT_SIDE in both the ftol-stop and interval-stop switches
    @Test
    public void testAllowedSolution_rightSide_resultNotLessThanRoot() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.ILLINOIS);
        double result = solver.solve(1000, squareMinusTwo(), 0.0, 2.0, AllowedSolution.RIGHT_SIDE);
        assertTrue(result >= Math.sqrt(2.0) - 1e-6);
    }

    // covers: case BELOW_SIDE (f1 <= 0)
    @Test
    public void testAllowedSolution_belowSide_functionValueNonPositive() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.ILLINOIS);
        UnivariateRealFunction f = squareMinusTwo();
        double result = solver.solve(1000, f, 0.0, 2.0, AllowedSolution.BELOW_SIDE);
        assertTrue(f.value(result) <= 1e-6);
    }

    // covers: case ABOVE_SIDE (f1 >= 0)
    @Test
    public void testAllowedSolution_aboveSide_functionValueNonNegative() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.ILLINOIS);
        UnivariateRealFunction f = squareMinusTwo();
        double result = solver.solve(1000, f, 0.0, 2.0, AllowedSolution.ABOVE_SIDE);
        assertTrue(f.value(result) >= -1e-6);
    }

    // covers: case ANY_SIDE
    @Test
    public void testAllowedSolution_anySide_resultCloseToRoot() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.ILLINOIS);
        double result = solver.solve(1000, squareMinusTwo(), 0.0, 2.0, AllowedSolution.ANY_SIDE);
        assertEquals(Math.sqrt(2.0), result, 1e-6);
    }

    // covers: REGULA_FALSI stagnation-handling branch (if (x == x1) ...) under an asymmetric bracket
    @Test
    public void testAsymmetricFunction_regulaFalsi_convergesWithinTolerance() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.REGULA_FALSI);
        double result = solver.solve(2000, fifthPowerMinusOne(), -1.0, 1.5, AllowedSolution.ANY_SIDE);
        assertEquals(1.0, result, 1e-5);
    }

    // covers: ILLINOIS correction over several same-sign updates (asymmetric bracket)
    @Test
    public void testAsymmetricFunction_illinois_convergesWithinTolerance() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.ILLINOIS);
        double result = solver.solve(2000, fifthPowerMinusOne(), -1.0, 1.5, AllowedSolution.ANY_SIDE);
        assertEquals(1.0, result, 1e-5);
    }

    // covers: PEGASUS correction over several same-sign updates (asymmetric bracket)
    @Test
    public void testAsymmetricFunction_pegasus_convergesWithinTolerance() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.PEGASUS);
        double result = solver.solve(2000, fifthPowerMinusOne(), -1.0, 1.5, AllowedSolution.ANY_SIDE);
        assertEquals(1.0, result, 1e-5);
    }

    // covers: inverted-flag interaction with LEFT_SIDE across multiple bracket updates
    @Test
    public void testAsymmetricFunction_leftSide_boundedByRoot() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.PEGASUS);
        double result = solver.solve(2000, fifthPowerMinusOne(), -1.0, 1.5, AllowedSolution.LEFT_SIDE);
        assertTrue(result <= 1.0 + 1e-5);
    }

    // covers: inverted-flag interaction with RIGHT_SIDE across multiple bracket updates
    @Test
    public void testAsymmetricFunction_rightSide_boundedByRoot() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.PEGASUS);
        double result = solver.solve(2000, fifthPowerMinusOne(), -1.0, 1.5, AllowedSolution.RIGHT_SIDE);
        assertTrue(result >= 1.0 - 1e-5);
    }

    // covers: BELOW_SIDE contract under an asymmetric, multi-correction bracket
    @Test
    public void testAsymmetricFunction_belowSide_functionValueNonPositive() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.ILLINOIS);
        UnivariateRealFunction f = fifthPowerMinusOne();
        double result = solver.solve(2000, f, -1.0, 1.5, AllowedSolution.BELOW_SIDE);
        assertTrue(f.value(result) <= 1e-5);
    }

    // covers: ABOVE_SIDE contract under an asymmetric, multi-correction bracket
    @Test
    public void testAsymmetricFunction_aboveSide_functionValueNonNegative() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.PEGASUS);
        UnivariateRealFunction f = fifthPowerMinusOne();
        double result = solver.solve(2000, f, -1.0, 1.5, AllowedSolution.ABOVE_SIDE);
        assertTrue(f.value(result) >= -1e-5);
    }

    // covers: general convergence with a negative root and a negative bracket
    @Test
    public void testNegativeRootCubic_anySide_convergesToRoot() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.ILLINOIS);
        double result = solver.solve(1000, cubicRootNegative(), -3.0, 0.0, AllowedSolution.ANY_SIDE);
        assertEquals(-2.0, result, 1e-5);
    }

    // covers: solve(maxEval,f,min,max,startValue,allowedSolution) forwarding allowedSolution correctly
    @Test
    public void testSolve6Arg_leftSide_allowedSolutionForwarded() throws Throwable {
        TestSecantSolver solver = new TestSecantSolver(1e-9, 1e-9, BaseSecantSolver.Method.ILLINOIS);
        double result = solver.solve(1000, squareMinusTwo(), 0.0, 2.0, 0.1, AllowedSolution.LEFT_SIDE);
        assertTrue(result <= Math.sqrt(2.0) + 1e-6);
    }
}
