package org.apache.commons.math.complex;

import org.junit.Test;
import static org.junit.Assert.*;

public class ComplexClaudeTest {

    // Constructor stores real/imaginary parts, accessible via getters
    @Test
    public void testConstructor_getRealGetImaginary() throws Throwable {
        Complex c = new Complex(3.0, -4.0);
        assertEquals(3.0, c.getReal(), 1e-9);
        assertEquals(-4.0, c.getImaginary(), 1e-9);
    }

    // abs(): isNaN() branch
    @Test
    public void testAbs_NaN_returnsNaN() throws Throwable {
        Complex c = new Complex(Double.NaN, 1.0);
        assertTrue(Double.isNaN(c.abs()));
    }

    // abs(): isInfinite() branch
    @Test
    public void testAbs_infinite_returnsPositiveInfinity() throws Throwable {
        Complex c = new Complex(Double.POSITIVE_INFINITY, 2.0);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), c.abs(), 1e-9);
    }

    // abs(): abs(real) < abs(imaginary) branch, 3-4-5 triangle
    @Test
    public void testAbs_normalPythagorean() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertEquals(5.0, c.abs(), 1e-9);
    }

    // abs(): real == 0.0 branch (both zero)
    @Test
    public void testAbs_zero_returnsZero() throws Throwable {
        assertEquals(0.0, Complex.ZERO.abs(), 1e-9);
    }

    // add(): normal partwise sum
    @Test
    public void testAdd_normalValues_sumsPartwise() throws Throwable {
        Complex a = new Complex(1.0, 2.0);
        Complex b = new Complex(3.0, 4.0);
        Complex r = a.add(b);
        assertEquals(4.0, r.getReal(), 1e-9);
        assertEquals(6.0, r.getImaginary(), 1e-9);
    }





    // conjugate(): isNaN() branch
    @Test
    public void testConjugate_NaN_returnsNaN() throws Throwable {
        Complex c = new Complex(Double.NaN, 2.0);
        Complex r = c.conjugate();
        assertTrue(Double.isNaN(r.getReal()));
        assertTrue(Double.isNaN(r.getImaginary()));
    }

    // conjugate(): normal value negates imaginary only
    @Test
    public void testConjugate_normalValue_negatesImaginary() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        Complex r = c.conjugate();
        assertEquals(3.0, r.getReal(), 1e-9);
        assertEquals(-4.0, r.getImaginary(), 1e-9);
    }

    // conjugate(): infinite imaginary part gets opposite sign infinity
    @Test
    public void testConjugate_infiniteImaginary_oppositeSign() throws Throwable {
        Complex c = new Complex(1.0, Double.POSITIVE_INFINITY);
        Complex r = c.conjugate();
        assertEquals(1.0, r.getReal(), 1e-9);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), r.getImaginary(), 1e-9);
    }

    // divide(): either operand NaN branch
    @Test
    public void testDivide_eitherNaN_returnsNaN() throws Throwable {
        Complex a = new Complex(Double.NaN, 1.0);
        Complex b = new Complex(1.0, 1.0);
        Complex r = a.divide(b);
        assertTrue(Double.isNaN(r.getReal()));
        assertTrue(Double.isNaN(r.getImaginary()));
    }

    // divide(): rhs equals ZERO branch
    @Test
    public void testDivide_rhsZero_returnsNaN() throws Throwable {
        Complex a = new Complex(1.0, 1.0);
        Complex r = a.divide(Complex.ZERO);
        assertTrue(Double.isNaN(r.getReal()));
        assertTrue(Double.isNaN(r.getImaginary()));
    }

    // divide(): rhs infinite, this finite branch -> ZERO
    @Test
    public void testDivide_rhsInfiniteThisFinite_returnsZero() throws Throwable {
        Complex a = new Complex(1.0, 1.0);
        Complex b = new Complex(Double.POSITIVE_INFINITY, 2.0);
        Complex r = a.divide(b);
        assertEquals(0.0, r.getReal(), 1e-9);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }

    // divide(): abs(c) >= abs(d) branch, (1+2i)/(2+1i) = 0.8+0.6i
    @Test
    public void testDivide_absCGreaterEqualD_normalResult() throws Throwable {
        Complex a = new Complex(1.0, 2.0);
        Complex b = new Complex(2.0, 1.0);
        Complex r = a.divide(b);
        assertEquals(0.8, r.getReal(), 1e-9);
        assertEquals(0.6, r.getImaginary(), 1e-9);
    }

    // divide(): abs(c) < abs(d) branch, (1+2i)/(1+3i) = 0.7-0.1i
    @Test
    public void testDivide_absCLessD_normalResult() throws Throwable {
        Complex a = new Complex(1.0, 2.0);
        Complex b = new Complex(1.0, 3.0);
        Complex r = a.divide(b);
        assertEquals(0.7, r.getReal(), 1e-9);
        assertEquals(-0.1, r.getImaginary(), 1e-9);
    }

    // equals(): this == other branch
    @Test
    public void testEquals_sameReference_true() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        assertTrue(c.equals(c));
    }

    // equals(): other == null branch
    @Test
    public void testEquals_null_false() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        assertFalse(c.equals(null));
    }

    // equals(): ClassCastException branch (different type)
    @Test
    public void testEquals_differentType_false() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        assertFalse(c.equals("not a complex"));
    }

    // equals(): rhs.isNaN() branch, all NaN complexes considered equal
    @Test
    public void testEquals_bothNaN_true() throws Throwable {
        Complex a = new Complex(Double.NaN, 1.0);
        Complex b = new Complex(2.0, Double.NaN);
        assertTrue(a.equals(b));
    }

    // equals(): bit comparison branch, differing values -> false
    @Test
    public void testEquals_differentValues_false() throws Throwable {
        Complex a = new Complex(1.0, 2.0);
        Complex b = new Complex(1.0, 3.0);
        assertFalse(a.equals(b));
    }

    // hashCode(): isNaN() branch returns constant 7
    @Test
    public void testHashCode_NaN_returnsSeven() throws Throwable {
        Complex c = new Complex(Double.NaN, 1.0);
        assertEquals(7, c.hashCode());
    }

    // isNaN(): true when either part NaN, false otherwise
    @Test
    public void testIsNaN_variousParts() throws Throwable {
        assertTrue(new Complex(Double.NaN, 1.0).isNaN());
        assertTrue(new Complex(1.0, Double.NaN).isNaN());
        assertFalse(new Complex(1.0, 2.0).isNaN());
    }

    // isInfinite(): true only when infinite and not NaN (NaN takes precedence)
    @Test
    public void testIsInfinite_trueAndNaNPrecedence() throws Throwable {
        assertTrue(new Complex(Double.POSITIVE_INFINITY, 1.0).isInfinite());
        assertFalse(new Complex(Double.NaN, Double.POSITIVE_INFINITY).isInfinite());
        assertFalse(new Complex(1.0, 2.0).isInfinite());
    }

    // multiply(): either NaN branch
    @Test
    public void testMultiply_eitherNaN_returnsNaN() throws Throwable {
        Complex a = new Complex(Double.NaN, 1.0);
        Complex b = new Complex(2.0, 3.0);
        Complex r = a.multiply(b);
        assertTrue(Double.isNaN(r.getReal()));
        assertTrue(Double.isNaN(r.getImaginary()));
    }

    // multiply(): infinite component branch -> INF
    @Test
    public void testMultiply_infiniteComponent_returnsINF() throws Throwable {
        Complex a = new Complex(Double.POSITIVE_INFINITY, 1.0);
        Complex b = new Complex(2.0, 3.0);
        Complex r = a.multiply(b);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), r.getReal(), 1e-9);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), r.getImaginary(), 1e-9);
    }

    // multiply(): normal (a+bi)(c+di) formula
    @Test
    public void testMultiply_normalValues_product() throws Throwable {
        Complex a = new Complex(1.0, 2.0);
        Complex b = new Complex(3.0, 4.0);
        Complex r = a.multiply(b);
        assertEquals(-5.0, r.getReal(), 1e-9);
        assertEquals(10.0, r.getImaginary(), 1e-9);
    }

    // negate(): isNaN() branch
    @Test
    public void testNegate_NaN_returnsNaN() throws Throwable {
        Complex c = new Complex(Double.NaN, 1.0);
        Complex r = c.negate();
        assertTrue(Double.isNaN(r.getReal()));
        assertTrue(Double.isNaN(r.getImaginary()));
    }

    // negate(): normal value
    @Test
    public void testNegate_normalValue() throws Throwable {
        Complex c = new Complex(3.0, -4.0);
        Complex r = c.negate();
        assertEquals(-3.0, r.getReal(), 1e-9);
        assertEquals(4.0, r.getImaginary(), 1e-9);
    }

    // subtract(): either NaN branch
    @Test
    public void testSubtract_eitherNaN_returnsNaN() throws Throwable {
        Complex a = new Complex(Double.NaN, 1.0);
        Complex b = new Complex(2.0, 3.0);
        Complex r = a.subtract(b);
        assertTrue(Double.isNaN(r.getReal()));
        assertTrue(Double.isNaN(r.getImaginary()));
    }

    // subtract(): normal partwise difference
    @Test
    public void testSubtract_normalValues() throws Throwable {
        Complex a = new Complex(5.0, 7.0);
        Complex b = new Complex(2.0, 3.0);
        Complex r = a.subtract(b);
        assertEquals(3.0, r.getReal(), 1e-9);
        assertEquals(4.0, r.getImaginary(), 1e-9);
    }

    // acos/asin/atan/cos/cosh: isNaN() guard branch returns Complex.NaN
    @Test
    public void testNaNPropagation_acos_asin_atan_cos_cosh() throws Throwable {
        Complex c = new Complex(Double.NaN, 2.0);
        assertTrue(c.acos().isNaN());
        assertTrue(c.asin().isNaN());
        assertTrue(c.atan().isNaN());
        assertTrue(c.cos().isNaN());
        assertTrue(c.cosh().isNaN());
    }

    // exp/log/sin/sinh/tan/tanh: isNaN() guard branch returns Complex.NaN
    @Test
    public void testNaNPropagation_exp_log_sin_sinh_tan_tanh() throws Throwable {
        Complex c = new Complex(Double.NaN, 2.0);
        assertTrue(c.exp().isNaN());
        assertTrue(c.log().isNaN());
        assertTrue(c.sin().isNaN());
        assertTrue(c.sinh().isNaN());
        assertTrue(c.tan().isNaN());
        assertTrue(c.tanh().isNaN());
    }

    // cos(): normal value, cos(0+0i) = 1+0i
    @Test
    public void testCos_zero_returnsOne() throws Throwable {
        Complex r = Complex.ZERO.cos();
        assertEquals(1.0, r.getReal(), 1e-9);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }

    // exp(): normal value, exp(0+0i) = 1+0i
    @Test
    public void testExp_zero_returnsOne() throws Throwable {
        Complex r = Complex.ZERO.exp();
        assertEquals(1.0, r.getReal(), 1e-9);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }

    // log(): normal value, log(1+0i) = 0+0i
    @Test
    public void testLog_one_returnsZero() throws Throwable {
        Complex r = Complex.ONE.log();
        assertEquals(0.0, r.getReal(), 1e-9);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }

    // pow(): null argument throws NullPointerException
    @Test
    public void testPow_nullArgument_throwsNPE() throws Throwable {
        try {
            Complex.ONE.pow(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // pow(): normal computation, 1^1 = 1
    @Test
    public void testPow_onePowOne_returnsOne() throws Throwable {
        Complex r = Complex.ONE.pow(Complex.ONE);
        assertEquals(1.0, r.getReal(), 1e-9);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }

    // pow(): x has NaN or infinite part -> NaN per javadoc
    @Test
    public void testPow_xInfiniteOrNaN_returnsNaN() throws Throwable {
        Complex r1 = Complex.ONE.pow(new Complex(Double.NaN, 1.0));
        Complex r2 = Complex.ONE.pow(new Complex(Double.POSITIVE_INFINITY, 1.0));
        assertTrue(r1.isNaN());
        assertTrue(r2.isNaN());
    }

    // sqrt(): isNaN() guard branch
    @Test
    public void testSqrt_NaN_returnsNaN() throws Throwable {
        Complex c = new Complex(Double.NaN, 1.0);
        assertTrue(c.sqrt().isNaN());
    }

    // sqrt(): real==0 && imaginary==0 branch
    @Test
    public void testSqrt_zero_returnsZero() throws Throwable {
        Complex r = Complex.ZERO.sqrt();
        assertEquals(0.0, r.getReal(), 1e-9);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }

    // sqrt(): real >= 0 branch, sqrt(4+0i) = 2+0i
    @Test
    public void testSqrt_positiveReal_four_returnsTwo() throws Throwable {
        Complex c = new Complex(4.0, 0.0);
        Complex r = c.sqrt();
        assertEquals(2.0, r.getReal(), 1e-9);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }

    // sqrt(): real < 0 branch, sqrt(-3+4i) = 1+2i
    @Test
    public void testSqrt_negativeRealWithImaginary_returnsPrincipalRoot() throws Throwable {
        Complex c = new Complex(-3.0, 4.0);
        Complex r = c.sqrt();
        assertEquals(1.0, r.getReal(), 1e-9);
        assertEquals(2.0, r.getImaginary(), 1e-9);
    }

    // sqrt1z(): sqrt(1 - 0^2) = sqrt(1) = 1+0i
    @Test
    public void testSqrt1z_zero_returnsOne() throws Throwable {
        Complex r = Complex.ZERO.sqrt1z();
        assertEquals(1.0, r.getReal(), 1e-9);
        assertEquals(0.0, r.getImaginary(), 1e-9);
    }
}
