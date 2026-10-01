package org.apache.commons.math.util;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class MathUtilsClaudeTest {

    // addAndCheck(int,int): normal addition within range
    @Test
    public void testAddAndCheckInt_normal_returnsSum() throws Throwable {
        assertEquals(5, MathUtils.addAndCheck(2, 3));
    }

    // addAndCheck(int,int): overflow branch throws ArithmeticException
    @Test
    public void testAddAndCheckInt_overflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.addAndCheck(Integer.MAX_VALUE, 1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // addAndCheck(long,long): normal addition within range
    @Test
    public void testAddAndCheckLong_normal_returnsSum() throws Throwable {
        assertEquals(300L, MathUtils.addAndCheck(100L, 200L));
    }

    // addAndCheck(long,long): overflow branch throws ArithmeticException
    @Test
    public void testAddAndCheckLong_overflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.addAndCheck(Long.MAX_VALUE, 1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // binomialCoefficient: n==k branch returns 1
    @Test
    public void testBinomialCoefficient_nEqualsK_returnsOne() throws Throwable {
        assertEquals(1L, MathUtils.binomialCoefficient(5, 5));
    }

    // binomialCoefficient: k==0 branch returns 1
    @Test
    public void testBinomialCoefficient_kEqualsZero_returnsOne() throws Throwable {
        assertEquals(1L, MathUtils.binomialCoefficient(5, 0));
    }

    // binomialCoefficient: k==1 branch returns n
    @Test
    public void testBinomialCoefficient_kEqualsOne_returnsN() throws Throwable {
        assertEquals(5L, MathUtils.binomialCoefficient(5, 1));
    }

    // binomialCoefficient: k==n-1 branch returns n
    @Test
    public void testBinomialCoefficient_kEqualsNMinusOne_returnsN() throws Throwable {
        assertEquals(5L, MathUtils.binomialCoefficient(5, 4));
    }

    // binomialCoefficient: general case computes exact value C(10,3)=120
    @Test
    public void testBinomialCoefficient_generalCase_returnsExactValue() throws Throwable {
        assertEquals(120L, MathUtils.binomialCoefficient(10, 3));
    }

    // binomialCoefficient: n<k precondition violation throws IllegalArgumentException
    @Test
    public void testBinomialCoefficient_nLessThanK_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficient(3, 5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // binomialCoefficient: n<0 precondition violation throws IllegalArgumentException
    @Test
    public void testBinomialCoefficient_negativeN_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficient(-5, -5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // binomialCoefficientDouble: general case matches exact integer value as double
    @Test
    public void testBinomialCoefficientDouble_generalCase_returnsExactValue() throws Throwable {
        assertEquals(120.0, MathUtils.binomialCoefficientDouble(10, 3), 1e-9);
    }

    // binomialCoefficientLog: general case equals natural log of exact value
    @Test
    public void testBinomialCoefficientLog_generalCase_matchesLogOfExactValue() throws Throwable {
        double expected = Math.log(120.0);
        assertEquals(expected, MathUtils.binomialCoefficientLog(10, 3), 1e-9);
    }

    // binomialCoefficientLog: n<k precondition violation throws IllegalArgumentException
    @Test
    public void testBinomialCoefficientLog_nLessThanK_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficientLog(2, 5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // cosh: hyperbolic cosine of 0 is 1
    @Test
    public void testCosh_zero_returnsOne() throws Throwable {
        assertEquals(1.0, MathUtils.cosh(0.0), 1e-9);
    }

    // equals(double,double): both NaN are considered equal
    @Test
    public void testEqualsDouble_bothNaN_returnsTrue() throws Throwable {
        assertTrue(MathUtils.equals(Double.NaN, Double.NaN));
    }

    // equals(double,double): different finite values are not equal
    @Test
    public void testEqualsDouble_differentValues_returnsFalse() throws Throwable {
        assertFalse(MathUtils.equals(1.0, 2.0));
    }

    // equals(double[],double[]): both null arrays are equal
    @Test
    public void testEqualsDoubleArray_bothNull_returnsTrue() throws Throwable {
        assertTrue(MathUtils.equals((double[]) null, (double[]) null));
    }

    // equals(double[],double[]): one null one non-null are not equal
    @Test
    public void testEqualsDoubleArray_oneNull_returnsFalse() throws Throwable {
        double[] y = new double[] {1.0};
        assertFalse(MathUtils.equals((double[]) null, y));
    }

    // equals(double[],double[]): different lengths are not equal
    @Test
    public void testEqualsDoubleArray_differentLength_returnsFalse() throws Throwable {
        double[] x = new double[] {1.0, 2.0};
        double[] y = new double[] {1.0};
        assertFalse(MathUtils.equals(x, y));
    }

    // equals(double[],double[]): equal elements including NaN via element-wise equals
    @Test
    public void testEqualsDoubleArray_equalWithNaN_returnsTrue() throws Throwable {
        double[] x = new double[] {1.0, Double.NaN, 3.0};
        double[] y = new double[] {1.0, Double.NaN, 3.0};
        assertTrue(MathUtils.equals(x, y));
    }

    // factorial: 5! = 120
    @Test
    public void testFactorial_five_returns120() throws Throwable {
        assertEquals(120L, MathUtils.factorial(5));
    }

    // factorial: negative n throws IllegalArgumentException
    @Test
    public void testFactorial_negative_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.factorial(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // factorialLog: n=0 means loop runs zero times, sum stays 0
    @Test
    public void testFactorialLog_zero_returnsZero() throws Throwable {
        assertEquals(0.0, MathUtils.factorialLog(0), 1e-9);
    }

    // gcd: normal case, both positive
    @Test
    public void testGcd_normalCase_returnsGcd() throws Throwable {
        assertEquals(6, MathUtils.gcd(12, 18));
    }

    // gcd: one argument zero returns absolute value of the other
    @Test
    public void testGcd_zeroAndValue_returnsAbsOfValue() throws Throwable {
        assertEquals(7, MathUtils.gcd(0, 7));
        assertEquals(7, MathUtils.gcd(7, 0));
    }

    // gcd: negative arguments use absolute value per javadoc
    @Test
    public void testGcd_negativeNumbers_returnsPositiveGcd() throws Throwable {
        assertEquals(6, MathUtils.gcd(-12, 18));
        assertEquals(6, MathUtils.gcd(12, -18));
    }

    // gcd: u*v int-overflow shortcut must not corrupt result for equal large powers of two
    @Test
    public void testGcd_intOverflowInProductCheck_returnsExactGcd() throws Throwable {
        assertEquals(65536, MathUtils.gcd(65536, 65536));
    }

    // hash(double): must match contract delegating to Double.hashCode()
    @Test
    public void testHashDouble_matchesDoubleHashCode() throws Throwable {
        assertEquals(new Double(5.0).hashCode(), MathUtils.hash(5.0));
    }

    // hash(double[]): must match contract delegating to Arrays.hashCode()
    @Test
    public void testHashDoubleArray_matchesArraysHashCode() throws Throwable {
        double[] arr = new double[] {1.0, 2.0, 3.0};
        assertEquals(Arrays.hashCode(arr), MathUtils.hash(arr));
    }

    // indicator(double): x>=0 branch includes zero, returns +1.0
    @Test
    public void testIndicatorDouble_zero_returnsPositiveOne() throws Throwable {
        assertEquals(1.0, MathUtils.indicator(0.0), 1e-9);
    }

    // indicator(double): NaN input returns NaN
    @Test
    public void testIndicatorDouble_nan_returnsNaN() throws Throwable {
        assertTrue(Double.isNaN(MathUtils.indicator(Double.NaN)));
    }

    // lcm: standard least common multiple computation
    @Test
    public void testLcm_normalCase_returnsLcm() throws Throwable {
        assertEquals(12, MathUtils.lcm(4, 6));
    }

    // log(base,x): matches Math.log(x)/Math.log(base)
    @Test
    public void testLogBaseX_returnsCorrectLog() throws Throwable {
        assertEquals(3.0, MathUtils.log(2.0, 8.0), 1e-9);
    }

    // mulAndCheck(int,int): overflow branch throws ArithmeticException
    @Test
    public void testMulAndCheckInt_overflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.mulAndCheck(Integer.MAX_VALUE, 2);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // mulAndCheck(long,long): normal positive*positive branch
    @Test
    public void testMulAndCheckLong_normal_returnsProduct() throws Throwable {
        assertEquals(30L, MathUtils.mulAndCheck(5L, 6L));
    }

    // nextAfter: d==0 special case moving toward positive returns Double.MIN_VALUE
    @Test
    public void testNextAfter_zeroTowardsPositive_returnsMinValue() throws Throwable {
        assertEquals(Double.MIN_VALUE, MathUtils.nextAfter(0.0, 1.0), 0.0);
    }

    // nextAfter: NaN input is returned unchanged
    @Test
    public void testNextAfter_nan_returnsNaN() throws Throwable {
        assertTrue(Double.isNaN(MathUtils.nextAfter(Double.NaN, 1.0)));
    }

    // scalb: normal case multiplies by 2^scaleFactor
    @Test
    public void testScalb_normalCase_returnsScaledValue() throws Throwable {
        assertEquals(8.0, MathUtils.scalb(1.0, 3), 1e-9);
    }

    // scalb: zero input special case returned unchanged
    @Test
    public void testScalb_zero_returnsZero() throws Throwable {
        assertEquals(0.0, MathUtils.scalb(0.0, 5), 0.0);
    }

    // normalizeAngle: 3*pi normalized around center 0 maps to -pi (closed interval)
    @Test
    public void testNormalizeAngle_threePi_centerZero_returnsNegativePi() throws Throwable {
        assertEquals(-Math.PI, MathUtils.normalizeAngle(3 * Math.PI, 0.0), 1e-9);
    }

    // round(double,int): HALF_UP rounds halfway cases away from zero
    @Test
    public void testRoundDouble_halfUp_roundsAwayFromZero() throws Throwable {
        assertEquals(3.0, MathUtils.round(2.5, 0), 1e-9);
        assertEquals(-3.0, MathUtils.round(-2.5, 0), 1e-9);
    }

    // round(float,int): scale 0 rounds to nearest integer, preserving sign
    @Test
    public void testRoundFloat_scaleZero_roundsToNearestInt() throws Throwable {
        assertEquals(2.0f, MathUtils.round(1.6f, 0), 1e-5f);
        assertEquals(-2.0f, MathUtils.round(-1.6f, 0), 1e-5f);
    }

    // sinh: hyperbolic sine of 0 is 0
    @Test
    public void testSinh_zero_returnsZero() throws Throwable {
        assertEquals(0.0, MathUtils.sinh(0.0), 1e-9);
    }

    // subAndCheck(int,int): overflow branch throws ArithmeticException
    @Test
    public void testSubAndCheckInt_overflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.subAndCheck(Integer.MIN_VALUE, 1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }
}
