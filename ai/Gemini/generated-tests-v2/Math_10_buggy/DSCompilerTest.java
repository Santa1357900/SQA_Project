package org.apache.commons.math3.analysis.differentiation;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;

public class DSCompilerTest {

    @Test
    public void testGetCompilerZeroParametersZeroOrder() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(0, 0);
        assertNotNull(compiler);
        assertEquals(0, compiler.getFreeParameters());
        assertEquals(0, compiler.getOrder());
        assertEquals(1, compiler.getSize());
    }

    @Test
    public void testGetCompilerCachingAndMultipleParameters() throws Throwable {
        DSCompiler compiler1 = DSCompiler.getCompiler(2, 2);
        DSCompiler compiler2 = DSCompiler.getCompiler(2, 2);
        assertSame(compiler1, compiler2);

        assertEquals(2, compiler1.getFreeParameters());
        assertEquals(2, compiler1.getOrder());
        assertEquals(6, compiler1.getSize());
    }

    @Test
    public void testGetPartialDerivativeIndexAndOrders() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 3);
        assertEquals(0, compiler.getPartialDerivativeIndex(0));
        assertEquals(1, compiler.getPartialDerivativeIndex(1));
        assertEquals(2, compiler.getPartialDerivativeIndex(2));
        assertEquals(3, compiler.getPartialDerivativeIndex(3));

        int[] orders = compiler.getPartialDerivativeOrders(2);
        assertNotNull(orders);
        assertEquals(1, orders.length);
        assertEquals(2, orders[0]);
    }

    @Test
    public void testGetPartialDerivativeIndexDimensionMismatch() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(2, 2);
        try {
            compiler.getPartialDerivativeIndex(1);
            fail("Expected DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testGetPartialDerivativeIndexNumberIsTooLarge() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(2, 1);
        try {
            compiler.getPartialDerivativeIndex(2, 1);
            fail("Expected NumberIsTooLargeException");
        } catch (NumberIsTooLargeException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testAdd() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] lhs = new double[] { 1.0, 2.0 };
        double[] rhs = new double[] { 3.0, 4.0 };
        double[] result = new double[2];

        compiler.add(lhs, 0, rhs, 0, result, 0);
        assertEquals(4.0, result[0], 1.0e-15);
        assertEquals(6.0, result[1], 1.0e-15);
    }

    @Test
    public void testSubtract() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] lhs = new double[] { 5.0, 7.0 };
        double[] rhs = new double[] { 2.0, 3.0 };
        double[] result = new double[2];

        compiler.subtract(lhs, 0, rhs, 0, result, 0);
        assertEquals(3.0, result[0], 1.0e-15);
        assertEquals(4.0, result[1], 1.0e-15);
    }

    @Test
    public void testMultiply() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] lhs = new double[] { 2.0, 3.0 };
        double[] rhs = new double[] { 4.0, 5.0 };
        double[] result = new double[2];

        compiler.multiply(lhs, 0, rhs, 0, result, 0);
        assertEquals(8.0, result[0], 1.0e-15);
        // Product rule: (u*v)' = u'v + uv' -> 3*4 + 2*5 = 12 + 10 = 22
        assertEquals(22.0, result[1], 1.0e-15);
    }

    @Test
    public void testDivide() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] lhs = new double[] { 4.0, 2.0 };
        double[] rhs = new double[] { 2.0, 1.0 };
        double[] result = new double[2];

        compiler.divide(lhs, 0, rhs, 0, result, 0);
        assertEquals(2.0, result[0], 1.0e-15);
    }

    @Test
    public void testRemainder() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] lhs = new double[] { 5.0, 2.0 };
        double[] rhs = new double[] { 3.0, 1.0 };
        double[] result = new double[2];

        compiler.remainder(lhs, 0, rhs, 0, result, 0);
        assertEquals(2.0, result[0], 1.0e-15);
    }

    @Test
    public void testPowDouble() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] operand = new double[] { 2.0, 1.0 };
        double[] result = new double[2];

        compiler.pow(operand, 0, 3.0, result, 0);
        assertEquals(8.0, result[0], 1.0e-15);
    }

    @Test
    public void testPowIntZero() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] operand = new double[] { 2.0, 1.0 };
        double[] result = new double[2];

        compiler.pow(operand, 0, 0, result, 0);
        assertEquals(1.0, result[0], 1.0e-15);
        assertEquals(0.0, result[1], 1.0e-15);
    }

    @Test
    public void testPowIntPositive() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] operand = new double[] { 2.0, 1.0 };
        double[] result = new double[2];

        compiler.pow(operand, 0, 2, result, 0);
        assertEquals(4.0, result[0], 1.0e-15);
    }

    @Test
    public void testPowIntNegative() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] operand = new double[] { 2.0, 1.0 };
        double[] result = new double[2];

        compiler.pow(operand, 0, -1, result, 0);
        assertEquals(0.5, result[0], 1.0e-15);
    }

    @Test
    public void testPowTwoOperands() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] x = new double[] { 2.0, 1.0 };
        double[] y = new double[] { 3.0, 1.0 };
        double[] result = new double[2];

        compiler.pow(x, 0, y, 0, result, 0);
        assertEquals(8.0, result[0], 1.0e-15);
    }

    @Test
    public void testRootN() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] operand = new double[] { 8.0, 1.0 };
        double[] result = new double[2];

        compiler.rootN(operand, 0, 3, result, 0);
        assertEquals(2.0, result[0], 1.0e-15);

        compiler.rootN(operand, 0, 2, result, 0);
        assertEquals(Math.sqrt(8.0), result[0], 1.0e-15);

        compiler.rootN(operand, 0, 4, result, 0);
        assertEquals(Math.pow(8.0, 0.25), result[0], 1.0e-15);
    }

    @Test
    public void testExpAndExpm1() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] operand = new double[] { 0.0, 1.0 };
        double[] result = new double[2];

        compiler.exp(operand, 0, result, 0);
        assertEquals(1.0, result[0], 1.0e-15);

        compiler.expm1(operand, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);
    }

    @Test
    public void testLogLog1pLog10() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] operand = new double[] { 1.0, 1.0 };
        double[] result = new double[2];

        compiler.log(operand, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);

        compiler.log1p(operand, 0, result, 0);
        assertEquals(Math.log1p(1.0), result[0], 1.0e-15);

        compiler.log10(operand, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);
    }

    @Test
    public void testTrigonometricFunctions() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 2);
        double[] operand = new double[] { 0.0, 1.0 };
        double[] result = new double[3];

        compiler.sin(operand, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);

        compiler.cos(operand, 0, result, 0);
        assertEquals(1.0, result[0], 1.0e-15);

        compiler.tan(operand, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);

        compiler.asin(operand, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);

        compiler.acos(new double[] { 1.0, 1.0 }, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);

        compiler.atan(operand, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);
    }

    @Test
    public void testAtan2() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] y = new double[] { 1.0, 1.0 };
        double[] xPositive = new double[] { 1.0, 1.0 };
        double[] xNegative = new double[] { -1.0, 1.0 };
        double[] result = new double[2];

        compiler.atan2(y, 0, xPositive, 0, result, 0);
        assertEquals(Math.atan2(1.0, 1.0), result[0], 1.0e-15);

        compiler.atan2(y, 0, xNegative, 0, result, 0);
        assertEquals(Math.atan2(1.0, -1.0), result[0], 1.0e-15);
    }

    @Test
    public void testHyperbolicFunctions() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 2);
        double[] operand = new double[] { 0.0, 1.0 };
        double[] result = new double[3];

        compiler.sinh(operand, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);

        compiler.cosh(operand, 0, result, 0);
        assertEquals(1.0, result[0], 1.0e-15);

        compiler.tanh(operand, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);

        compiler.asinh(operand, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);

        compiler.acosh(new double[] { 2.0, 1.0 }, 0, result, 0);
        assertEquals(Math.acosh(2.0), result[0], 1.0e-15);

        compiler.atanh(operand, 0, result, 0);
        assertEquals(0.0, result[0], 1.0e-15);
    }

    @Test
    public void testLinearCombination() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] c1 = new double[] { 1.0, 2.0 };
        double[] c2 = new double[] { 3.0, 4.0 };
        double[] c3 = new double[] { 5.0, 6.0 };
        double[] c4 = new double[] { 7.0, 8.0 };
        double[] result = new double[2];

        compiler.linearCombination(1.0, c1, 0, 1.0, c2, 0, result, 0);
        assertEquals(4.0, result[0], 1.0e-15);
        assertEquals(6.0, result[1], 1.0e-15);

        compiler.linearCombination(1.0, c1, 0, 1.0, c2, 0, 1.0, c3, 0, result, 0);
        assertEquals(9.0, result[0], 1.0e-15);
        assertEquals(12.0, result[1], 1.0e-15);

        compiler.linearCombination(1.0, c1, 0, 1.0, c2, 0, 1.0, c3, 0, 1.0, c4, 0, result, 0);
        assertEquals(16.0, result[0], 1.0e-15);
        assertEquals(20.0, result[1], 1.0e-15);
    }

    @Test
    public void testTaylor() throws Throwable {
        DSCompiler compiler = DSCompiler.getCompiler(1, 1);
        double[] ds = new double[] { 2.0, 3.0 };
        double val = compiler.taylor(ds, 0, 1.0);
        assertEquals(5.0, val, 1.0e-15);
    }

    @Test
    public void testCheckCompatibility() throws Throwable {
        DSCompiler compiler1 = DSCompiler.getCompiler(1, 1);
        DSCompiler compiler2 = DSCompiler.getCompiler(1, 1);
        DSCompiler compiler3 = DSCompiler.getCompiler(2, 1);

        compiler1.checkCompatibility(compiler2);

        try {
            compiler1.checkCompatibility(compiler3);
            fail("Expected DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            assertTrue(true);
        }
    }
}