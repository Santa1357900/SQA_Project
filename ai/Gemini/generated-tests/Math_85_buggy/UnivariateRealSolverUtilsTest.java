package org.apache.commons.math.analysis.solvers;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.ConvergenceException;
import org.apache.commons.math.analysis.UnivariateRealFunction;

public class UnivariateRealSolverUtilsTest {

    private static class DummyFunction implements UnivariateRealFunction {
        private final double root;

        public DummyFunction(double root) {
            this.root = root;
        }

        public double value(double x) throws FunctionEvaluationException {
            return x - root;
        }
    }

    private static class QuadraticFunction implements UnivariateRealFunction {
        public double value(double x) throws FunctionEvaluationException {
            return x * x - 4.0;
        }
    }

    private static class ConstantFunction implements UnivariateRealFunction {
        private final double val;

        public ConstantFunction(double val) {
            this.val = val;
        }

        public double value(double x) throws FunctionEvaluationException {
            return val;
        }
    }

    @Test
    public void testMidpoint() throws Throwable {
        assertEquals(1.5, UnivariateRealSolverUtils.midpoint(1.0, 2.0), 1.0e-15);
        assertEquals(0.0, UnivariateRealSolverUtils.midpoint(-1.0, 1.0), 1.0e-15);
        assertEquals(-1.5, UnivariateRealSolverUtils.midpoint(-2.0, -1.0), 1.0e-15);
    }

    @Test
    public void testSolveSuccess() throws Throwable {
        UnivariateRealFunction f = new QuadraticFunction();
        double result = UnivariateRealSolverUtils.solve(f, 0.0, 3.0);
        assertEquals(2.0, result, 1.0e-6);
    }

    @Test
    public void testSolveWithAccuracySuccess() throws Throwable {
        UnivariateRealFunction f = new QuadraticFunction();
        double result = UnivariateRealSolverUtils.solve(f, 0.0, 3.0, 1.0e-4);
        assertEquals(2.0, result, 1.0e-3);
    }

    @Test
    public void testSolveNullFunction() throws Throwable {
        boolean caught = false;
        try {
            UnivariateRealSolverUtils.solve(null, 0.0, 3.0);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testSolveWithAccuracyNullFunction() throws Throwable {
        boolean caught = false;
        try {
            UnivariateRealSolverUtils.solve(null, 0.0, 3.0, 1.0e-4);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testBracketSuccess() throws Throwable {
        UnivariateRealFunction f = new QuadraticFunction();
        double[] bracket = UnivariateRealSolverUtils.bracket(f, 1.5, 0.0, 3.0);
        assertNotNull(bracket);
        assertEquals(2, bracket.length);
        assertTrue(f.value(bracket[0]) * f.value(bracket[1]) <= 0.0);
    }

    @Test
    public void testBracketWithMaxIterationsSuccess() throws Throwable {
        UnivariateRealFunction f = new QuadraticFunction();
        double[] bracket = UnivariateRealSolverUtils.bracket(f, 1.5, 0.0, 3.0, 10);
        assertNotNull(bracket);
        assertEquals(2, bracket.length);
        assertTrue(f.value(bracket[0]) * f.value(bracket[1]) <= 0.0);
    }

    @Test
    public void testBracketNullFunction() throws Throwable {
        boolean caught = false;
        try {
            UnivariateRealSolverUtils.bracket(null, 1.5, 0.0, 3.0, 10);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testBracketInvalidMaxIterations() throws Throwable {
        UnivariateRealFunction f = new QuadraticFunction();
        boolean caught = false;
        try {
            UnivariateRealSolverUtils.bracket(f, 1.5, 0.0, 3.0, 0);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            UnivariateRealSolverUtils.bracket(f, 1.5, 0.0, 3.0, -5);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testBracketInvalidParametersInitialOutOfBounds() throws Throwable {
        UnivariateRealFunction f = new QuadraticFunction();
        boolean caught = false;
        try {
            UnivariateRealSolverUtils.bracket(f, -1.0, 0.0, 3.0, 10);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            UnivariateRealSolverUtils.bracket(f, 4.0, 0.0, 3.0, 10);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testBracketInvalidParametersBoundsReversed() throws Throwable {
        UnivariateRealFunction f = new QuadraticFunction();
        boolean caught = false;
        try {
            UnivariateRealSolverUtils.bracket(f, 1.5, 3.0, 0.0, 10);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);

        caught = false;
        try {
            UnivariateRealSolverUtils.bracket(f, 2.0, 2.0, 2.0, 10);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testBracketConvergenceException() throws Throwable {
        UnivariateRealFunction f = new ConstantFunction(5.0);
        boolean caught = false;
        try {
            UnivariateRealSolverUtils.bracket(f, 1.0, 0.0, 2.0, 2);
        } catch (ConvergenceException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testBracketHitsBoundariesWithoutConvergence() throws Throwable {
        UnivariateRealFunction f = new ConstantFunction(5.0);
        boolean caught = false;
        try {
            UnivariateRealSolverUtils.bracket(f, 1.0, 0.0, 2.0, 100);
        } catch (ConvergenceException e) {
            caught = true;
        }
        assertTrue(caught);
    }
}