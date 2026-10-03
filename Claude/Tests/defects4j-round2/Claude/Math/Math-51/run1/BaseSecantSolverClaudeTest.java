package org.apache.commons.math.analysis.solvers;

import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.junit.Test;
import static org.junit.Assert.*;

public class BaseSecantSolverClaudeTest {

    // f0 == 0.0 branch: min is exact root, must return min regardless of allowed solution
    @Test
    public void testSolve_minIsExactRoot_returnsMinRegardlessOfAllowedSolution() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x - 2.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        double result = solver.solve(1000, f, 2.0, 5.0, AllowedSolution.RIGHT_SIDE);
        assertEquals(2.0, result, 0.0);
    }

    // f1 == 0.0 branch: max is exact root, must return max regardless of allowed solution
    @Test
    public void testSolve_maxIsExactRoot_returnsMaxRegardlessOfAllowedSolution() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x - 5.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        double result = solver.solve(1000, f, 2.0, 5.0, AllowedSolution.LEFT_SIDE);
        assertEquals(5.0, result, 0.0);
    }

    // verifyBracketing branch: same-sign interval must throw a RuntimeException
    @Test
    public void testSolve_nonBracketingSameSignInterval_throwsException() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x + 1.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        try {
            solver.solve(1000, f, -1.0, 1.0, AllowedSolution.ANY_SIDE);
            fail("expected exception for non-bracketing interval");
        } catch (RuntimeException expected) {
        }
    }

    // linear function: secant formula gives the exact root in a single step (fx == 0.0 branch)
    @Test
    public void testSolve_linearFunctionRegulaFalsi_convergesToExactRootInOneStep() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x - 1.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        double result = solver.solve(1000, f, 0.0, 2.0, AllowedSolution.ANY_SIDE);
        assertEquals(1.0, result, 1e-9);
    }

    // linear function with negative root, exact-root single step
    @Test
    public void testSolve_linearFunctionNegativeRoot_regulaFalsi_exactRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x + 3.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        double result = solver.solve(1000, f, -5.0, 0.0, AllowedSolution.ANY_SIDE);
        assertEquals(-3.0, result, 1e-9);
    }

    // quadratic, REGULA_FALSI, ANY_SIDE: converges close to true root sqrt(2)
    @Test
    public void testSolve_quadraticRegulaFalsiAnySide_closeToRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.ANY_SIDE);
        assertEquals(Math.sqrt(2.0), result, 1e-3);
    }

    // quadratic, REGULA_FALSI, LEFT_SIDE: for increasing f, x<=root iff f(x)<=0
    @Test
    public void testSolve_quadraticRegulaFalsiLeftSide_resultBelowOrEqualRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.LEFT_SIDE);
        assertTrue(result * result - 2.0 <= 1e-9);
    }

    // quadratic, REGULA_FALSI, RIGHT_SIDE: for increasing f, x>=root iff f(x)>=0
    @Test
    public void testSolve_quadraticRegulaFalsiRightSide_resultAboveOrEqualRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.RIGHT_SIDE);
        assertTrue(result * result - 2.0 >= -1e-9);
    }

    // quadratic, REGULA_FALSI, BELOW_SIDE: contract guarantees f(result) <= 0
    @Test
    public void testSolve_quadraticRegulaFalsiBelowSide_fResultNonPositive() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.BELOW_SIDE);
        assertTrue(result * result - 2.0 <= 1e-9);
    }

    // quadratic, REGULA_FALSI, ABOVE_SIDE: contract guarantees f(result) >= 0
    @Test
    public void testSolve_quadraticRegulaFalsiAboveSide_fResultNonNegative() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.ABOVE_SIDE);
        assertTrue(result * result - 2.0 >= -1e-9);
    }

    // quadratic, ILLINOIS (f0 *= 0.5 branch), ANY_SIDE: close to true root
    @Test
    public void testSolve_quadraticIllinoisAnySide_closeToRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        IllinoisSolver solver = new IllinoisSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.ANY_SIDE);
        assertEquals(Math.sqrt(2.0), result, 1e-3);
    }

    // quadratic, ILLINOIS, LEFT_SIDE guarantee
    @Test
    public void testSolve_quadraticIllinoisLeftSide_fResultNonPositive() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        IllinoisSolver solver = new IllinoisSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.LEFT_SIDE);
        assertTrue(result * result - 2.0 <= 1e-9);
    }

    // quadratic, ILLINOIS, RIGHT_SIDE guarantee
    @Test
    public void testSolve_quadraticIllinoisRightSide_fResultNonNegative() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        IllinoisSolver solver = new IllinoisSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.RIGHT_SIDE);
        assertTrue(result * result - 2.0 >= -1e-9);
    }

    // quadratic, ILLINOIS, BELOW_SIDE guarantee
    @Test
    public void testSolve_quadraticIllinoisBelowSide_fResultNonPositive() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        IllinoisSolver solver = new IllinoisSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.BELOW_SIDE);
        assertTrue(result * result - 2.0 <= 1e-9);
    }

    // quadratic, ILLINOIS, ABOVE_SIDE guarantee
    @Test
    public void testSolve_quadraticIllinoisAboveSide_fResultNonNegative() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        IllinoisSolver solver = new IllinoisSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.ABOVE_SIDE);
        assertTrue(result * result - 2.0 >= -1e-9);
    }

    // quadratic, PEGASUS (f0 *= f1/(f1+fx) branch), ANY_SIDE: close to true root
    @Test
    public void testSolve_quadraticPegasusAnySide_closeToRoot() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        PegasusSolver solver = new PegasusSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.ANY_SIDE);
        assertEquals(Math.sqrt(2.0), result, 1e-3);
    }

    // quadratic, PEGASUS, BELOW_SIDE guarantee
    @Test
    public void testSolve_quadraticPegasusBelowSide_fResultNonPositive() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        PegasusSolver solver = new PegasusSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.BELOW_SIDE);
        assertTrue(result * result - 2.0 <= 1e-9);
    }

    // quadratic, PEGASUS, ABOVE_SIDE guarantee
    @Test
    public void testSolve_quadraticPegasusAboveSide_fResultNonNegative() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        PegasusSolver solver = new PegasusSolver();
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.ABOVE_SIDE);
        assertTrue(result * result - 2.0 >= -1e-9);
    }

    // cubic function x^3-2x-5, REGULA_FALSI: multi-iteration convergence to known root
    @Test
    public void testSolve_cubicRegulaFalsi_rootWithinAccuracy() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x * x - 2.0 * x - 5.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        double result = solver.solve(1000, f, 2.0, 3.0, AllowedSolution.ANY_SIDE);
        assertEquals(2.0945514815423265, result, 1e-4);
    }

    // cubic function, PEGASUS: multi-iteration convergence to known root
    @Test
    public void testSolve_cubicPegasus_rootWithinAccuracy() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x * x - 2.0 * x - 5.0; }
        };
        PegasusSolver solver = new PegasusSolver();
        double result = solver.solve(1000, f, 2.0, 3.0, AllowedSolution.ANY_SIDE);
        assertEquals(2.0945514815423265, result, 1e-4);
    }

    // cubic function, ILLINOIS: multi-iteration convergence to known root
    @Test
    public void testSolve_cubicIllinois_rootWithinAccuracy() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x * x - 2.0 * x - 5.0; }
        };
        IllinoisSolver solver = new IllinoisSolver();
        double result = solver.solve(1000, f, 2.0, 3.0, AllowedSolution.ANY_SIDE);
        assertEquals(2.0945514815423265, result, 1e-4);
    }

    // doSolve always uses getMin()/getMax() as x0,x1: explicit startValue is not used by the algorithm
    @Test
    public void testSolve_startValueIgnoredByAlgorithm_sameResultForDifferentStartValues() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        IllinoisSolver solver = new IllinoisSolver();
        double r1 = solver.solve(1000, f, 1.0, 2.0, 1.1, AllowedSolution.ANY_SIDE);
        double r2 = solver.solve(1000, f, 1.0, 2.0, 1.9, AllowedSolution.ANY_SIDE);
        assertEquals(r1, r2, 0.0);
    }

    // solve(...,allowedSolution) delegates to solve(...,midpoint,allowedSolution)
    @Test
    public void testSolve_withoutStartValue_equalsExplicitMidpointStartValue() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        IllinoisSolver solver = new IllinoisSolver();
        double r1 = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.ANY_SIDE);
        double r2 = solver.solve(1000, f, 1.0, 2.0, 1.5, AllowedSolution.ANY_SIDE);
        assertEquals(r1, r2, 0.0);
    }

    // solve(maxEval,f,min,max,startValue) defaults to AllowedSolution.ANY_SIDE
    @Test
    public void testSolve_fourArgOverload_defaultsToAnySide() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        double r1 = solver.solve(1000, f, 1.0, 2.0, 1.5);
        double r2 = solver.solve(1000, f, 1.0, 2.0, 1.5, AllowedSolution.ANY_SIDE);
        assertEquals(r1, r2, 0.0);
    }

    // loose functionValueAccuracy triggers the |f1|<=ftol early-return branch
    @Test
    public void testSolve_looseFunctionValueAccuracy_returnsWithinFunctionTolerance() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x * x - 2.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver(1e-14, 1e-14, 0.5);
        double result = solver.solve(1000, f, 1.0, 2.0, AllowedSolution.ANY_SIDE);
        assertTrue(Math.abs(result * result - 2.0) <= 0.5);
    }

    // too few allowed evaluations must throw a RuntimeException before completing the solve
    @Test
    public void testSolve_maxEvaluationsTooSmall_throwsException() throws Throwable {
        UnivariateRealFunction f = new UnivariateRealFunction() {
            public double value(double x) { return x - 1.0; }
        };
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        try {
            solver.solve(1, f, 0.0, 2.0, AllowedSolution.ANY_SIDE);
            fail("expected exception for too few evaluations");
        } catch (RuntimeException expected) {
        }
    }

    // default no-arg constructor must use DEFAULT_ABSOLUTE_ACCURACY
    @Test
    public void testConstructor_defaultAbsoluteAccuracy_matchesConstant() throws Throwable {
        RegulaFalsiSolver solver = new RegulaFalsiSolver();
        assertEquals(BaseSecantSolver.DEFAULT_ABSOLUTE_ACCURACY, solver.getAbsoluteAccuracy(), 0.0);
    }

    // single-arg constructor stores the given absolute accuracy
    @Test
    public void testConstructor_customAbsoluteAccuracy_getAbsoluteAccuracyReturnsIt() throws Throwable {
        RegulaFalsiSolver solver = new RegulaFalsiSolver(1e-3);
        assertEquals(1e-3, solver.getAbsoluteAccuracy(), 0.0);
    }

    // two-arg constructor stores both relative and absolute accuracy
    @Test
    public void testConstructor_relativeAndAbsoluteAccuracy_gettersReturnGivenValues() throws Throwable {
        RegulaFalsiSolver solver = new RegulaFalsiSolver(1e-9, 1e-3);
        assertEquals(1e-9, solver.getRelativeAccuracy(), 0.0);
        assertEquals(1e-3, solver.getAbsoluteAccuracy(), 0.0);
    }

    // three-arg constructor stores the function value accuracy
    @Test
    public void testConstructor_fullAccuracyConstructor_functionValueAccuracyReturned() throws Throwable {
        RegulaFalsiSolver solver = new RegulaFalsiSolver(1e-9, 1e-3, 1e-5);
        assertEquals(1e-5, solver.getFunctionValueAccuracy(), 0.0);
    }
}
