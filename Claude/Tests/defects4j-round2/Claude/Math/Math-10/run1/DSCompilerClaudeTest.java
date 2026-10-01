package org.apache.commons.math3.analysis.differentiation;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;

public class DSCompilerClaudeTest {

    private DSCompiler compiler1;   // 1 parameter, order 1, size 2
    private DSCompiler compiler1o2; // 1 parameter, order 2, size 3
    private DSCompiler compiler2o1; // 2 parameters, order 1, size 3
    private DSCompiler compiler2o2; // 2 parameters, order 2, size 6

    @Before
    public void setUp() throws Throwable {
        compiler1 = DSCompiler.getCompiler(1, 1);
        compiler1o2 = DSCompiler.getCompiler(1, 2);
        compiler2o1 = DSCompiler.getCompiler(2, 1);
        compiler2o2 = DSCompiler.getCompiler(2, 2);
    }

    // getCompiler caches instances for same (parameters, order)
    @Test
    public void testGetCompiler_caching_returnsSameInstance() throws Throwable {
        DSCompiler a = DSCompiler.getCompiler(2, 3);
        DSCompiler b = DSCompiler.getCompiler(2, 3);
        assertSame(a, b);
    }

    // getFreeParameters and getOrder simple getters
    @Test
    public void testGetters_freeParametersAndOrder() throws Throwable {
        assertEquals(2, compiler2o1.getFreeParameters());
        assertEquals(1, compiler2o1.getOrder());
    }

    // getSize for several (parameters, order) combinations
    @Test
    public void testGetSize_variousCompilers_returnsExpectedCounts() throws Throwable {
        assertEquals(3, compiler2o1.getSize());
        assertEquals(3, compiler1o2.getSize());
        assertEquals(6, compiler2o2.getSize());
    }

    // orders.length != parameters -> DimensionMismatchException branch
    @Test
    public void testGetPartialDerivativeIndex_dimensionMismatch_throws() throws Throwable {
        try {
            compiler1.getPartialDerivativeIndex(0, 0);
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
        }
    }

    // sum of orders greater than instance order -> NumberIsTooLargeException branch
    @Test
    public void testGetPartialDerivativeIndex_orderTooLarge_throws() throws Throwable {
        try {
            compiler1o2.getPartialDerivativeIndex(3);
            fail("expected NumberIsTooLargeException");
        } catch (NumberIsTooLargeException expected) {
        }
    }

    // order=1 guaranteed ordering: f, df/dx1, df/dx2 per javadoc
    @Test
    public void testGetPartialDerivativeIndex_twoParamsOrderOne_matchesJavadocOrdering() throws Throwable {
        assertEquals(0, compiler2o1.getPartialDerivativeIndex(0, 0));
        assertEquals(1, compiler2o1.getPartialDerivativeIndex(1, 0));
        assertEquals(2, compiler2o1.getPartialDerivativeIndex(0, 1));
    }

    // getPartialDerivativeOrders is inverse of getPartialDerivativeIndex
    @Test
    public void testGetPartialDerivativeOrders_inverseOfIndex() throws Throwable {
        assertArrayEquals(new int[] {0, 0}, compiler2o1.getPartialDerivativeOrders(0));
        assertArrayEquals(new int[] {1, 0}, compiler2o1.getPartialDerivativeOrders(1));
        assertArrayEquals(new int[] {0, 1}, compiler2o1.getPartialDerivativeOrders(2));
    }

    // linearCombination(a1,c1,a2,c2) element-wise
    @Test
    public void testLinearCombination_twoTerms() throws Throwable {
        double[] c1 = {1.0, 2.0};
        double[] c2 = {3.0, 4.0};
        double[] result = new double[2];
        compiler1.linearCombination(2.0, c1, 0, 3.0, c2, 0, result, 0);
        assertEquals(11.0, result[0], 1e-9);
        assertEquals(16.0, result[1], 1e-9);
    }

    // linearCombination(a1,c1,a2,c2,a3,c3) element-wise
    @Test
    public void testLinearCombination_threeTerms() throws Throwable {
        double[] c1 = {1.0, 2.0};
        double[] c2 = {3.0, 4.0};
        double[] c3 = {5.0, 6.0};
        double[] result = new double[2];
        compiler1.linearCombination(2.0, c1, 0, 3.0, c2, 0, 1.0, c3, 0, result, 0);
        assertEquals(16.0, result[0], 1e-9);
        assertEquals(22.0, result[1], 1e-9);
    }

    // linearCombination(a1,c1,a2,c2,a3,c3,a4,c4) element-wise
    @Test
    public void testLinearCombination_fourTerms() throws Throwable {
        double[] c1 = {1.0, 2.0};
        double[] c2 = {3.0, 4.0};
        double[] c3 = {5.0, 6.0};
        double[] c4 = {7.0, 8.0};
        double[] result = new double[2];
        compiler1.linearCombination(2.0, c1, 0, 3.0, c2, 0, 1.0, c3, 0, 1.0, c4, 0, result, 0);
        assertEquals(23.0, result[0], 1e-9);
        assertEquals(30.0, result[1], 1e-9);
    }

    // add loops over getSize() elements
    @Test
    public void testAdd_elementWise() throws Throwable {
        double[] lhs = {1.0, 2.0};
        double[] rhs = {3.0, 4.0};
        double[] result = new double[2];
        compiler1.add(lhs, 0, rhs, 0, result, 0);
        assertEquals(4.0, result[0], 1e-9);
        assertEquals(6.0, result[1], 1e-9);
    }

    // subtract loops over getSize() elements
    @Test
    public void testSubtract_elementWise() throws Throwable {
        double[] lhs = {1.0, 2.0};
        double[] rhs = {3.0, 4.0};
        double[] result = new double[2];
        compiler1.subtract(lhs, 0, rhs, 0, result, 0);
        assertEquals(-2.0, result[0], 1e-9);
        assertEquals(-2.0, result[1], 1e-9);
    }

    // multiply follows product (Leibniz) rule
    @Test
    public void testMultiply_productRule() throws Throwable {
        double[] lhs = {2.0, 3.0};
        double[] rhs = {4.0, 5.0};
        double[] result = new double[2];
        compiler1.multiply(lhs, 0, rhs, 0, result, 0);
        assertEquals(8.0, result[0], 1e-9);
        assertEquals(22.0, result[1], 1e-9);
    }

    // divide must read rhs at rhsOffset, not lhsOffset: catches the offset bug
    @Test
    public void testDivide_differentOffsets_catchesOffsetBug() throws Throwable {
        // combined array: lhs=[3.0,1.0] at offset 0, rhs=[2.0,1.0] at offset 2
        double[] combined = {3.0, 1.0, 2.0, 1.0};
        double[] result = new double[2];
        compiler1.divide(combined, 0, combined, 2, result, 0);
        assertEquals(1.5, result[0], 1e-9);
        assertEquals(-0.25, result[1], 1e-9);
    }

    // remainder: lhs % rhs and derivative lhs' - k*rhs'
    @Test
    public void testRemainder_computesRemainderAndDerivative() throws Throwable {
        double[] lhs = {7.5, 1.0};
        double[] rhs = {2.0, 0.0};
        double[] result = new double[2];
        compiler1.remainder(lhs, 0, rhs, 0, result, 0);
        assertEquals(1.5, result[0], 1e-9);
        assertEquals(1.0, result[1], 1e-9);
    }

    // pow with real exponent and pow(x,y) array form (constant exponent reduces to power rule)
    @Test
    public void testPow_realExponentAndXYForm() throws Throwable {
        double[] ds = {2.0, 1.0};
        double[] result = new double[2];
        compiler1.pow(ds, 0, 3.0, result, 0);
        assertEquals(8.0, result[0], 1e-9);
        assertEquals(12.0, result[1], 1e-9);

        double[] x = {2.0, 1.0};
        double[] y = {3.0, 0.0};
        double[] result2 = new double[2];
        compiler1.pow(x, 0, y, 0, result2, 0);
        assertEquals(8.0, result2[0], 1e-6);
        assertEquals(12.0, result2[1], 1e-6);
    }

    // integer pow n=0 special case: x^0 = 1, all derivatives 0
    @Test
    public void testPowInt_zeroExponent_returnsOne() throws Throwable {
        double[] ds = {5.0, 1.0};
        double[] result = new double[2];
        compiler1.pow(ds, 0, 0, result, 0);
        assertEquals(1.0, result[0], 1e-9);
        assertEquals(0.0, result[1], 1e-9);
    }

    // integer pow strictly positive branch
    @Test
    public void testPowInt_positiveExponent() throws Throwable {
        double[] ds = {2.0, 1.0};
        double[] result = new double[2];
        compiler1.pow(ds, 0, 3, result, 0);
        assertEquals(8.0, result[0], 1e-9);
        assertEquals(12.0, result[1], 1e-9);
    }

    // integer pow strictly negative branch
    @Test
    public void testPowInt_negativeExponent() throws Throwable {
        double[] ds = {2.0, 1.0};
        double[] result = new double[2];
        compiler1.pow(ds, 0, -2, result, 0);
        assertEquals(0.25, result[0], 1e-9);
        assertEquals(-0.25, result[1], 1e-9);
    }

    // rootN n==2 special branch (sqrt)
    @Test
    public void testRootN_squareRoot() throws Throwable {
        double[] ds = {4.0, 1.0};
        double[] result = new double[2];
        compiler1.rootN(ds, 0, 2, result, 0);
        assertEquals(2.0, result[0], 1e-9);
        assertEquals(0.25, result[1], 1e-9);
    }

    // rootN n==3 special branch (cbrt) and general-n else branch
    @Test
    public void testRootN_cubeRootAndGeneralN() throws Throwable {
        double[] ds = {8.0, 1.0};
        double[] result = new double[2];
        compiler1.rootN(ds, 0, 3, result, 0);
        assertEquals(2.0, result[0], 1e-9);
        assertEquals(1.0 / 12.0, result[1], 1e-9);

        double[] ds2 = {16.0, 1.0};
        double[] result2 = new double[2];
        compiler1.rootN(ds2, 0, 4, result2, 0);
        assertEquals(2.0, result2[0], 1e-9);
        assertEquals(0.03125, result2[1], 1e-9);
    }

    // exp value and derivative (e^x has derivative e^x)
    @Test
    public void testExp_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.exp(ds, 0, result, 0);
        assertEquals(Math.exp(0.5), result[0], 1e-6);
        assertEquals(Math.exp(0.5), result[1], 1e-6);
    }

    // expm1 value uses expm1, derivative uses exp
    @Test
    public void testExpm1_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.expm1(ds, 0, result, 0);
        assertEquals(Math.expm1(0.5), result[0], 1e-6);
        assertEquals(Math.exp(0.5), result[1], 1e-6);
    }

    // log value and derivative 1/x
    @Test
    public void testLog_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.log(ds, 0, result, 0);
        assertEquals(Math.log(0.5), result[0], 1e-6);
        assertEquals(1.0 / 0.5, result[1], 1e-6);
    }

    // log1p value and derivative 1/(1+x)
    @Test
    public void testLog1p_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.log1p(ds, 0, result, 0);
        assertEquals(Math.log1p(0.5), result[0], 1e-6);
        assertEquals(1.0 / 1.5, result[1], 1e-6);
    }

    // log10 value and derivative 1/(x*ln10)
    @Test
    public void testLog10_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.log10(ds, 0, result, 0);
        assertEquals(Math.log10(0.5), result[0], 1e-6);
        assertEquals(1.0 / (0.5 * Math.log(10.0)), result[1], 1e-6);
    }

    // cos value and derivative -sin(x)
    @Test
    public void testCos_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.cos(ds, 0, result, 0);
        assertEquals(Math.cos(0.5), result[0], 1e-6);
        assertEquals(-Math.sin(0.5), result[1], 1e-6);
    }

    // sin value and derivative cos(x), order=1
    @Test
    public void testSin_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.sin(ds, 0, result, 0);
        assertEquals(Math.sin(0.5), result[0], 1e-6);
        assertEquals(Math.cos(0.5), result[1], 1e-6);
    }

    // sin with order=2: exercises second derivative composition loop
    @Test
    public void testSin_order2_secondDerivative() throws Throwable {
        double[] ds = {0.5, 1.0, 0.0};
        double[] result = new double[3];
        compiler1o2.sin(ds, 0, result, 0);
        assertEquals(Math.sin(0.5), result[0], 1e-6);
        assertEquals(Math.cos(0.5), result[1], 1e-6);
        assertEquals(-Math.sin(0.5), result[2], 1e-6);
    }

    // tan value and derivative sec^2(x)
    @Test
    public void testTan_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.tan(ds, 0, result, 0);
        assertEquals(Math.tan(0.5), result[0], 1e-6);
        assertEquals(1.0 / (Math.cos(0.5) * Math.cos(0.5)), result[1], 1e-6);
    }

    // acos value and derivative -1/sqrt(1-x^2)
    @Test
    public void testAcos_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.acos(ds, 0, result, 0);
        assertEquals(Math.acos(0.5), result[0], 1e-6);
        assertEquals(-1.0 / Math.sqrt(1.0 - 0.25), result[1], 1e-6);
    }

    // asin value and derivative 1/sqrt(1-x^2)
    @Test
    public void testAsin_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.asin(ds, 0, result, 0);
        assertEquals(Math.asin(0.5), result[0], 1e-6);
        assertEquals(1.0 / Math.sqrt(1.0 - 0.25), result[1], 1e-6);
    }

    // atan value and derivative 1/(1+x^2)
    @Test
    public void testAtan_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.atan(ds, 0, result, 0);
        assertEquals(Math.atan(0.5), result[0], 1e-6);
        assertEquals(1.0 / 1.25, result[1], 1e-6);
    }

    // atan2 with x>=0 branch
    @Test
    public void testAtan2_xPositiveBranch() throws Throwable {
        double[] y = {1.0, 0.0};
        double[] x = {1.0, 1.0};
        double[] result = new double[2];
        compiler1.atan2(y, 0, x, 0, result, 0);
        assertEquals(Math.atan2(1.0, 1.0), result[0], 1e-6);
        assertEquals(-0.5, result[1], 1e-6);
    }

    // atan2 with x<0 branch (uses +/-pi - 2*atan formula)
    @Test
    public void testAtan2_xNegativeBranch() throws Throwable {
        double[] y = {1.0, 0.0};
        double[] x = {-1.0, 1.0};
        double[] result = new double[2];
        compiler1.atan2(y, 0, x, 0, result, 0);
        assertEquals(Math.atan2(1.0, -1.0), result[0], 1e-6);
        assertEquals(-0.5, result[1], 1e-6);
    }

    // cosh value and derivative sinh(x)
    @Test
    public void testCosh_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.cosh(ds, 0, result, 0);
        assertEquals(Math.cosh(0.5), result[0], 1e-6);
        assertEquals(Math.sinh(0.5), result[1], 1e-6);
    }

    // sinh value and derivative cosh(x)
    @Test
    public void testSinh_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.sinh(ds, 0, result, 0);
        assertEquals(Math.sinh(0.5), result[0], 1e-6);
        assertEquals(Math.cosh(0.5), result[1], 1e-6);
    }

    // tanh value and derivative 1-tanh^2(x)
    @Test
    public void testTanh_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.tanh(ds, 0, result, 0);
        assertEquals(Math.tanh(0.5), result[0], 1e-6);
        assertEquals(1.0 - Math.tanh(0.5) * Math.tanh(0.5), result[1], 1e-6);
    }

    // acosh value and derivative 1/sqrt(x^2-1), x>=1
    @Test
    public void testAcosh_valueAndDerivative() throws Throwable {
        double[] ds = {2.0, 1.0};
        double[] result = new double[2];
        compiler1.acosh(ds, 0, result, 0);
        double expectedValue = Math.log(2.0 + Math.sqrt(4.0 - 1.0));
        assertEquals(expectedValue, result[0], 1e-6);
        assertEquals(1.0 / Math.sqrt(3.0), result[1], 1e-6);
    }

    // asinh value and derivative 1/sqrt(x^2+1)
    @Test
    public void testAsinh_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.asinh(ds, 0, result, 0);
        double expectedValue = Math.log(0.5 + Math.sqrt(0.25 + 1.0));
        assertEquals(expectedValue, result[0], 1e-6);
        assertEquals(1.0 / Math.sqrt(1.25), result[1], 1e-6);
    }

    // atanh value and derivative 1/(1-x^2)
    @Test
    public void testAtanh_valueAndDerivative() throws Throwable {
        double[] ds = {0.5, 1.0};
        double[] result = new double[2];
        compiler1.atanh(ds, 0, result, 0);
        double expectedValue = 0.5 * Math.log((1.0 + 0.5) / (1.0 - 0.5));
        assertEquals(expectedValue, result[0], 1e-6);
        assertEquals(1.0 / (1.0 - 0.25), result[1], 1e-6);
    }

    // compose with identity function: result must equal operand via chain rule
    @Test
    public void testCompose_identityFunction() throws Throwable {
        double[] operand = {3.0, 1.0};
        double[] f = {3.0, 1.0};
        double[] result = new double[2];
        compiler1.compose(operand, 0, f, result, 0);
        assertEquals(3.0, result[0], 1e-9);
        assertEquals(1.0, result[1], 1e-9);
    }

    // taylor expansion of f(x)=x^2 around x0=1 should match (x0+delta)^2
    @Test
    public void testTaylor_quadraticExpansion() throws Throwable {
        double[] ds = {1.0, 2.0, 2.0};
        double value = compiler1o2.taylor(ds, 0, 0.1);
        assertEquals(1.21, value, 1e-9);
    }

    // checkCompatibility: mismatched number of free parameters
    @Test
    public void testCheckCompatibility_parameterMismatch_throws() throws Throwable {
        try {
            compiler1.checkCompatibility(compiler2o1);
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
        }
    }

    // checkCompatibility: mismatched derivation order
    @Test
    public void testCheckCompatibility_orderMismatch_throws() throws Throwable {
        try {
            compiler1.checkCompatibility(compiler1o2);
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
        }
    }
}
