package org.apache.commons.math.complex;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.exception.NullArgumentException;
import org.apache.commons.math.exception.NotPositiveException;

public class ComplexClaudeTest {

    private static final double DELTA = 1e-9;
    private static final double LOOSE_DELTA = 1e-6;

    // Constructor(double): imaginary part must default to 0.0
    @Test
    public void testConstructor_realOnly_setsImaginaryZero() throws Throwable {
        Complex c = new Complex(5.0);
        assertEquals(5.0, c.getReal(), DELTA);
        assertEquals(0.0, c.getImaginary(), DELTA);
    }

    // Constructor: isNaN short-circuits isInfinite; infinite-only sets isInfinite true
    @Test
    public void testConstructor_specialValues_setsNaNAndInfiniteFlags() throws Throwable {
        Complex nanC = new Complex(Double.NaN, Double.POSITIVE_INFINITY);
        assertTrue(nanC.isNaN());
        assertFalse(nanC.isInfinite());
        Complex infC = new Complex(Double.POSITIVE_INFINITY, 2.0);
        assertFalse(infC.isNaN());
        assertTrue(infC.isInfinite());
    }

    // abs(): isNaN branch
    @Test
    public void testAbs_nan_returnsNaN() throws Throwable {
        assertTrue(Double.isNaN(Complex.NaN.abs()));
    }

    // abs(): isInfinite() branch
    @Test
    public void testAbs_infinite_returnsPositiveInfinity() throws Throwable {
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), Complex.INF.abs(), DELTA);
    }

    // abs(): tie case real==0 && imaginary==0
    @Test
    public void testAbs_zero_returnsZero() throws Throwable {
        assertEquals(0.0, Complex.ZERO.abs(), DELTA);
    }

    // abs(): |real| < |imaginary| branch
    @Test
    public void testAbs_imaginaryDominant_returnsCorrectHypotenuse() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        assertEquals(5.0, c.abs(), DELTA);
    }

    // abs(): |real| >= |imaginary| branch
    @Test
    public void testAbs_realDominant_returnsCorrectHypotenuse() throws Throwable {
        Complex c = new Complex(4.0, 3.0);
        assertEquals(5.0, c.abs(), DELTA);
    }

    // add(Complex): normal addition of real and imaginary parts
    @Test
    public void testAdd_normal_sumsRealAndImaginary() throws Throwable {
        Complex a = new Complex(1.0, 2.0);
        Complex b = new Complex(3.0, 4.0);
        Complex r = a.add(b);
        assertEquals(4.0, r.getReal(), DELTA);
        assertEquals(6.0, r.getImaginary(), DELTA);
    }

    // add(Complex)/add(double): NaN propagation from either operand
    @Test
    public void testAdd_nanPropagation_returnsNaN() throws Throwable {
        assertTrue(Complex.NaN.add(Complex.ONE).isNaN());
        assertTrue(new Complex(1.0).add(Double.NaN).isNaN());
    }

    // add(Complex): null argument must throw NullArgumentException
    @Test
    public void testAdd_nullAddend_throwsNullArgumentException() throws Throwable {
        try {
            new Complex(1.0, 1.0).add((Complex) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // conjugate(): negates imaginary part; NaN branch returns NaN
    @Test
    public void testConjugate_normalAndNaN_negatesImaginaryOrReturnsNaN() throws Throwable {
        Complex c = new Complex(2.0, 3.0);
        Complex conj = c.conjugate();
        assertEquals(2.0, conj.getReal(), DELTA);
        assertEquals(-3.0, conj.getImaginary(), DELTA);
        assertTrue(Complex.NaN.conjugate().isNaN());
    }

    // divide(Complex): divisor equals ZERO returns NaN
    @Test
    public void testDivide_divisorZero_returnsNaN() throws Throwable {
        assertTrue(new Complex(1.0, 1.0).divide(Complex.ZERO).isNaN());
    }

    // divide(Complex): this and divisor both infinite returns NaN
    @Test
    public void testDivide_bothInfinite_returnsNaN() throws Throwable {
        assertTrue(Complex.INF.divide(Complex.INF).isNaN());
    }

    // divide(Complex): finite dividend, infinite divisor returns ZERO
    @Test
    public void testDivide_finiteDividedByInfinite_returnsZero() throws Throwable {
        Complex r = new Complex(1.0, 1.0).divide(Complex.INF);
        assertEquals(Complex.ZERO, r);
    }

    // divide(Complex): |c| < |d| branch of prescaling algorithm
    @Test
    public void testDivide_cLessThanD_correctQuotient() throws Throwable {
        Complex a = new Complex(3.0, 4.0);
        Complex d = new Complex(1.0, 2.0);
        Complex r = a.divide(d);
        assertEquals(2.2, r.getReal(), DELTA);
        assertEquals(-0.4, r.getImaginary(), DELTA);
    }

    // divide(Complex): |c| >= |d| branch of prescaling algorithm
    @Test
    public void testDivide_cGreaterEqualD_correctQuotient() throws Throwable {
        Complex a = new Complex(3.0, 4.0);
        Complex d = new Complex(2.0, 1.0);
        Complex r = a.divide(d);
        assertEquals(2.0, r.getReal(), DELTA);
        assertEquals(1.0, r.getImaginary(), DELTA);
    }

    // divide(Complex): null divisor must throw NullArgumentException
    @Test
    public void testDivide_nullDivisor_throwsNullArgumentException() throws Throwable {
        try {
            new Complex(1.0, 1.0).divide((Complex) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // divide(double): zero divisor -> NaN; infinite divisor with finite this -> ZERO
    @Test
    public void testDivideDouble_byZeroAndInfiniteDivisor_returnsNaNOrZero() throws Throwable {
        assertTrue(new Complex(1.0, 1.0).divide(0.0).isNaN());
        Complex r = new Complex(1.0, 1.0).divide(Double.POSITIVE_INFINITY);
        assertEquals(Complex.ZERO, r);
    }

    // reciprocal(): real==0 && imaginary==0 returns NaN
    @Test
    public void testReciprocal_zero_returnsNaN() throws Throwable {
        assertTrue(Complex.ZERO.reciprocal().isNaN());
    }

    // reciprocal(): isInfinite branch returns ZERO
    @Test
    public void testReciprocal_infinite_returnsZero() throws Throwable {
        assertEquals(Complex.ZERO, Complex.INF.reciprocal());
    }

    // reciprocal(): |real| < |imaginary| branch
    @Test
    public void testReciprocal_imaginaryDominant_correctValue() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        Complex r = c.reciprocal();
        assertEquals(0.12, r.getReal(), DELTA);
        assertEquals(-0.16, r.getImaginary(), DELTA);
    }

    // reciprocal(): |real| >= |imaginary| branch
    @Test
    public void testReciprocal_realDominant_correctValue() throws Throwable {
        Complex c = new Complex(4.0, 3.0);
        Complex r = c.reciprocal();
        assertEquals(0.16, r.getReal(), DELTA);
        assertEquals(-0.12, r.getImaginary(), DELTA);
    }

    // equals(): reflexive and NaN-equals-NaN contract
    @Test
    public void testEquals_sameInstanceAndNaNVariants_returnsTrue() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        assertTrue(c.equals(c));
        Complex nan1 = new Complex(Double.NaN, 5.0);
        Complex nan2 = new Complex(7.0, Double.NaN);
        assertTrue(nan1.equals(nan2));
    }

    // equals(): differing values, non-Complex type, and null all return false
    @Test
    public void testEquals_differentValuesOrType_returnsFalse() throws Throwable {
        Complex a = new Complex(1.0, 2.0);
        Complex b = new Complex(1.0, 3.0);
        assertFalse(a.equals(b));
        assertFalse(a.equals("not a complex"));
        assertFalse(a.equals(null));
    }

    // hashCode(): any NaN part yields hash code 7
    @Test
    public void testHashCode_nan_returnsSeven() throws Throwable {
        assertEquals(7, new Complex(Double.NaN, 1.0).hashCode());
        assertEquals(7, Complex.NaN.hashCode());
    }

    // multiply(Complex): normal product formula and infinite-factor branch
    @Test
    public void testMultiply_normalAndInfiniteFactor_correctResults() throws Throwable {
        Complex a = new Complex(1.0, 2.0);
        Complex b = new Complex(3.0, 4.0);
        Complex r = a.multiply(b);
        assertEquals(-5.0, r.getReal(), DELTA);
        assertEquals(10.0, r.getImaginary(), DELTA);
        assertEquals(Complex.INF, a.multiply(Complex.INF));
    }

    // multiply(Complex): null factor must throw NullArgumentException
    @Test
    public void testMultiply_nullFactor_throwsNullArgumentException() throws Throwable {
        try {
            new Complex(1.0, 1.0).multiply((Complex) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // multiply(int): simple scalar multiplication
    @Test
    public void testMultiplyInt_normal_correctProduct() throws Throwable {
        Complex c = new Complex(2.0, 3.0);
        Complex r = c.multiply(4);
        assertEquals(8.0, r.getReal(), DELTA);
        assertEquals(12.0, r.getImaginary(), DELTA);
    }

    // negate(): negates both real and imaginary parts
    @Test
    public void testNegate_normal_negatesBothParts() throws Throwable {
        Complex c = new Complex(2.0, -3.0);
        Complex r = c.negate();
        assertEquals(-2.0, r.getReal(), DELTA);
        assertEquals(3.0, r.getImaginary(), DELTA);
    }

    // subtract(Complex): normal difference of real and imaginary parts
    @Test
    public void testSubtract_normal_correctDifference() throws Throwable {
        Complex a = new Complex(5.0, 7.0);
        Complex b = new Complex(2.0, 3.0);
        Complex r = a.subtract(b);
        assertEquals(3.0, r.getReal(), DELTA);
        assertEquals(4.0, r.getImaginary(), DELTA);
    }

    // subtract(Complex): null subtrahend must throw NullArgumentException
    @Test
    public void testSubtract_nullSubtrahend_throwsNullArgumentException() throws Throwable {
        try {
            new Complex(1.0, 1.0).subtract((Complex) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // log(): documented example log(0 + 0i) = -INFINITY + 0i
    @Test
    public void testLog_zero_returnsNegativeInfinityRealZeroImaginary() throws Throwable {
        Complex r = Complex.ZERO.log();
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), r.getReal(), DELTA);
        assertEquals(0.0, r.getImaginary(), DELTA);
    }

    // exp(): documented example exp(INFINITY + i) = INFINITY + INFINITY i
    @Test
    public void testExp_positiveInfiniteReal_returnsInfiniteBoth() throws Throwable {
        Complex r = new Complex(Double.POSITIVE_INFINITY, 1.0).exp();
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), r.getReal(), DELTA);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), r.getImaginary(), DELTA);
    }

    // exp(): documented example exp(-INFINITY + i) = 0 + 0i
    @Test
    public void testExp_negativeInfiniteReal_returnsZero() throws Throwable {
        Complex r = new Complex(Double.NEGATIVE_INFINITY, 1.0).exp();
        assertEquals(0.0, r.getReal(), DELTA);
        assertEquals(0.0, r.getImaginary(), DELTA);
    }

    // sqrt(): negative real axis produces pure imaginary root; zero special case
    @Test
    public void testSqrt_negativeRealAndZero_correctValues() throws Throwable {
        Complex r1 = new Complex(-4.0, 0.0).sqrt();
        assertEquals(0.0, r1.getReal(), DELTA);
        assertEquals(2.0, r1.getImaginary(), DELTA);
        Complex r2 = Complex.ZERO.sqrt();
        assertEquals(0.0, r2.getReal(), DELTA);
        assertEquals(0.0, r2.getImaginary(), DELTA);
    }

    // sqrt1z(): sqrt(1 - 0^2) = 1 for zero input
    @Test
    public void testSqrt1z_zero_returnsOne() throws Throwable {
        Complex r = Complex.ZERO.sqrt1z();
        assertEquals(1.0, r.getReal(), DELTA);
        assertEquals(0.0, r.getImaginary(), DELTA);
    }

    // atan(): mathematical identity atan(0) = 0
    @Test
    public void testAtan_zero_returnsZero() throws Throwable {
        Complex r = Complex.ZERO.atan();
        assertEquals(0.0, r.getReal(), DELTA);
        assertEquals(0.0, r.getImaginary(), DELTA);
    }

    // asin(): mathematical identity asin(0) = 0
    @Test
    public void testAsin_zero_returnsZero() throws Throwable {
        Complex r = Complex.ZERO.asin();
        assertEquals(0.0, r.getReal(), DELTA);
        assertEquals(0.0, r.getImaginary(), DELTA);
    }

    // acos(): mathematical identity acos(0) = pi/2
    @Test
    public void testAcos_zero_returnsHalfPi() throws Throwable {
        Complex r = Complex.ZERO.acos();
        assertEquals(Math.PI / 2.0, r.getReal(), DELTA);
        assertEquals(0.0, r.getImaginary(), DELTA);
    }

    // tan(): documented example tan(a + INFINITY i) = 0 + i
    @Test
    public void testTan_infiniteImaginary_approximatelyI() throws Throwable {
        Complex r = new Complex(0.0, Double.POSITIVE_INFINITY).tan();
        assertEquals(0.0, r.getReal(), LOOSE_DELTA);
        assertEquals(1.0, r.getImaginary(), LOOSE_DELTA);
    }

    // tanh(): documented example tanh(INFINITY + bi) = 1 + 0i
    @Test
    public void testTanh_infiniteReal_approximatelyOne() throws Throwable {
        Complex r = new Complex(Double.POSITIVE_INFINITY, 0.0).tanh();
        assertEquals(1.0, r.getReal(), LOOSE_DELTA);
        assertEquals(0.0, r.getImaginary(), LOOSE_DELTA);
    }

    // getArgument(): atan2-based angle for a known 45 degree point
    @Test
    public void testGetArgument_normal_correctAngle() throws Throwable {
        Complex c = new Complex(1.0, 1.0);
        assertEquals(Math.PI / 4.0, c.getArgument(), DELTA);
    }

    // nthRoot(int): n <= 0 must throw NotPositiveException
    @Test
    public void testNthRoot_nonPositiveN_throwsNotPositiveException() throws Throwable {
        try {
            new Complex(1.0, 1.0).nthRoot(0);
            fail("expected NotPositiveException");
        } catch (NotPositiveException expected) {
        }
    }

    // nthRoot(int): NaN instance and infinite instance each yield a single special element
    @Test
    public void testNthRoot_nanAndInfiniteInstance_returnsSingleSpecialElement() throws Throwable {
        List<Complex> nanRoots = Complex.NaN.nthRoot(3);
        assertEquals(1, nanRoots.size());
        assertTrue(nanRoots.get(0).isNaN());
        List<Complex> infRoots = Complex.INF.nthRoot(3);
        assertEquals(1, infRoots.size());
        assertTrue(infRoots.get(0).isInfinite());
    }

    // nthRoot(int): square roots of 4 are exactly 2 and -2
    @Test
    public void testNthRoot_normal_returnsCorrectRoots() throws Throwable {
        List<Complex> roots = new Complex(4.0, 0.0).nthRoot(2);
        assertEquals(2, roots.size());
        assertEquals(2.0, roots.get(0).getReal(), DELTA);
        assertEquals(0.0, roots.get(0).getImaginary(), DELTA);
        assertEquals(-2.0, roots.get(1).getReal(), DELTA);
        assertEquals(0.0, roots.get(1).getImaginary(), DELTA);
    }
}
