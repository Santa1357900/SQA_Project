package org.apache.commons.math.util;

import org.junit.Test;
import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.util.Arrays;

public class MathUtilsClaudeTest {

    // addAndCheck(int,int): normal sum, no overflow branch
    @Test
    public void testAddAndCheckInt_normal_returnsSum() throws Throwable {
        assertEquals(7, MathUtils.addAndCheck(3, 4));
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

    // addAndCheck(long,long): normal sum, no overflow
    @Test
    public void testAddAndCheckLong_normal_returnsSum() throws Throwable {
        assertEquals(7L, MathUtils.addAndCheck(3L, 4L));
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

    // binomialCoefficient: n==k branch and k==0 branch both return 1
    @Test
    public void testBinomialCoefficient_edgeCases_returnOne() throws Throwable {
        assertEquals(1L, MathUtils.binomialCoefficient(5, 5));
        assertEquals(1L, MathUtils.binomialCoefficient(5, 0));
    }

    // binomialCoefficient: k==1 branch and k==n-1 branch both return n
    @Test
    public void testBinomialCoefficient_kOneOrNMinusOne_returnsN() throws Throwable {
        assertEquals(5L, MathUtils.binomialCoefficient(5, 1));
        assertEquals(5L, MathUtils.binomialCoefficient(5, 4));
    }

    // binomialCoefficient: general case using full computation path, C(5,2)=10
    @Test
    public void testBinomialCoefficient_generalCase_returnsExpectedValue() throws Throwable {
        assertEquals(10L, MathUtils.binomialCoefficient(5, 2));
    }

    // binomialCoefficient: n<k precondition violated throws IllegalArgumentException
    @Test
    public void testBinomialCoefficient_nLessThanK_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficient(2, 5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // binomialCoefficient: n<0 precondition (with n>=k) throws IllegalArgumentException
    @Test
    public void testBinomialCoefficient_negativeN_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficient(-2, -3);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // binomialCoefficientDouble: general case matches C(5,2)=10
    @Test
    public void testBinomialCoefficientDouble_generalCase_returnsExpectedValue() throws Throwable {
        assertEquals(10.0, MathUtils.binomialCoefficientDouble(5, 2), 1e-9);
    }

    // binomialCoefficientLog: k==0 returns 0, k==n-1 returns log(n)
    @Test
    public void testBinomialCoefficientLog_edgeCases_returnExpectedValues() throws Throwable {
        assertEquals(0.0, MathUtils.binomialCoefficientLog(5, 0), 1e-9);
        assertEquals(Math.log(5.0), MathUtils.binomialCoefficientLog(5, 4), 1e-9);
    }

    // cosh and sinh at x=0: cosh(0)=1, sinh(0)=0
    @Test
    public void testCoshAndSinh_zero_returnExpectedValues() throws Throwable {
        assertEquals(1.0, MathUtils.cosh(0.0), 1e-9);
        assertEquals(0.0, MathUtils.sinh(0.0), 1e-9);
    }

    // equals(double,double): both NaN true, equal values true, different values false
    @Test
    public void testEquals_nanAndNormalCases() throws Throwable {
        assertTrue(MathUtils.equals(Double.NaN, Double.NaN));
        assertTrue(MathUtils.equals(1.0, 1.0));
        assertFalse(MathUtils.equals(1.0, 2.0));
    }

    // equals(double[],double[]): both null true, one null false, diff length false, NaN elements equal true
    @Test
    public void testEqualsArray_nullAndLengthAndNanCases() throws Throwable {
        assertTrue(MathUtils.equals((double[]) null, (double[]) null));
        assertFalse(MathUtils.equals(null, new double[] {1.0}));
        assertFalse(MathUtils.equals(new double[] {1.0, 2.0}, new double[] {1.0}));
        assertTrue(MathUtils.equals(new double[] {1.0, Double.NaN}, new double[] {1.0, Double.NaN}));
    }

    // factorial: valid values from table lookup, n=0, n=5, n=20
    @Test
    public void testFactorial_validValues_returnExpected() throws Throwable {
        assertEquals(1L, MathUtils.factorial(0));
        assertEquals(120L, MathUtils.factorial(5));
        assertEquals(2432902008176640000L, MathUtils.factorial(20));
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

    // factorial: n too large to fit in long throws ArithmeticException
    @Test
    public void testFactorial_tooLarge_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.factorial(21);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // gcd: normal values, zero argument, both zero
    @Test
    public void testGcd_variousValues_returnExpected() throws Throwable {
        assertEquals(6, MathUtils.gcd(12, 18));
        assertEquals(5, MathUtils.gcd(0, 5));
        assertEquals(0, MathUtils.gcd(0, 0));
        assertEquals(6, MathUtils.gcd(-12, 18));
    }

    // hash(double) and hash(double[]) must follow documented hashCode contract
    @Test
    public void testHash_doubleAndArray_matchContract() throws Throwable {
        assertEquals(new Double(5.0).hashCode(), MathUtils.hash(5.0));
        double[] arr = new double[] {1.0, 2.0};
        assertEquals(Arrays.hashCode(arr), MathUtils.hash(arr));
    }

    // indicator(byte) and indicator(short): positive and negative sign cases
    @Test
    public void testIndicatorByteAndShort_signCases() throws Throwable {
        assertEquals((byte) 1, MathUtils.indicator((byte) 5));
        assertEquals((byte) -1, MathUtils.indicator((byte) -5));
        assertEquals((short) 1, MathUtils.indicator((short) 5));
        assertEquals((short) -1, MathUtils.indicator((short) -5));
    }

    // indicator(double): positive, negative and NaN cases
    @Test
    public void testIndicatorDouble_signAndNan() throws Throwable {
        assertEquals(1.0, MathUtils.indicator(5.0), 1e-9);
        assertEquals(-1.0, MathUtils.indicator(-5.0), 1e-9);
        assertTrue(Double.isNaN(MathUtils.indicator(Double.NaN)));
    }

    // indicator(float): positive and negative sign cases
    @Test
    public void testIndicatorFloat_signCases() throws Throwable {
        assertEquals(1.0f, MathUtils.indicator(5.0f), 1e-9f);
        assertEquals(-1.0f, MathUtils.indicator(-5.0f), 1e-9f);
    }

    // indicator(int): positive, negative and zero (zero goes to positive branch)
    @Test
    public void testIndicatorInt_signCases() throws Throwable {
        assertEquals(1, MathUtils.indicator(5));
        assertEquals(-1, MathUtils.indicator(-5));
        assertEquals(1, MathUtils.indicator(0));
    }

    // indicator(long): positive and negative sign cases
    @Test
    public void testIndicatorLong_signCases() throws Throwable {
        assertEquals(1L, MathUtils.indicator(5L));
        assertEquals(-1L, MathUtils.indicator(-5L));
    }

    // lcm: normal case within int range
    @Test
    public void testLcm_normalValue_returnsExpected() throws Throwable {
        assertEquals(12, MathUtils.lcm(4, 6));
    }



    // log(base,x): verifies change of base formula
    @Test
    public void testLog_baseAndX_returnsExpected() throws Throwable {
        assertEquals(3.0, MathUtils.log(2.0, 8.0), 1e-9);
        assertEquals(2.0, MathUtils.log(10.0, 100.0), 1e-9);
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

    // mulAndCheck(long,long): zero branch returns 0, overflow branch throws
    @Test
    public void testMulAndCheckLong_zeroAndOverflowCases() throws Throwable {
        assertEquals(0L, MathUtils.mulAndCheck(0L, 5L));
        try {
            MathUtils.mulAndCheck(Long.MAX_VALUE, 2L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // nextAfter: direction greater/less than d, NaN and infinite special cases
    @Test
    public void testNextAfter_directionAndSpecialCases() throws Throwable {
        assertTrue(MathUtils.nextAfter(1.0, 2.0) > 1.0);
        assertTrue(MathUtils.nextAfter(1.0, 0.0) < 1.0);
        assertTrue(Double.isNaN(MathUtils.nextAfter(Double.NaN, 1.0)));
        assertEquals(Double.MIN_VALUE, MathUtils.nextAfter(0.0, 1.0), 0.0);
    }

    // normalizeAngle: 3*pi normalized around 0 should fall in [-pi, pi]
    @Test
    public void testNormalizeAngle_threePi_returnsNegativePi() throws Throwable {
        assertEquals(-Math.PI, MathUtils.normalizeAngle(3 * Math.PI, 0.0), 1e-9);
    }

    // scalb: normal scaling, zero unchanged, NaN unchanged
    @Test
    public void testScalb_variousCases() throws Throwable {
        assertEquals(8.0, MathUtils.scalb(1.0, 3), 1e-9);
        assertEquals(0.0, MathUtils.scalb(0.0, 5), 1e-9);
        assertTrue(Double.isNaN(MathUtils.scalb(Double.NaN, 2)));
    }

    // round(double,int): default ROUND_HALF_UP and explicit ROUND_FLOOR method
    @Test
    public void testRoundDouble_defaultAndExplicitMethod() throws Throwable {
        assertEquals(3.0, MathUtils.round(2.5, 0), 1e-9);
        assertEquals(2.0, MathUtils.round(2.5, 0, BigDecimal.ROUND_FLOOR), 1e-9);
    }

    // round(float,int): default ROUND_HALF_UP and explicit ROUND_CEILING method with negative value
    @Test
    public void testRoundFloat_halfUpAndCeilingCases() throws Throwable {
        assertEquals(3.0f, MathUtils.round(2.5f, 0), 1e-6f);
        assertEquals(-2.0f, MathUtils.round(-2.3f, 0, BigDecimal.ROUND_CEILING), 1e-6f);
    }

    // sign(byte) and sign(short): positive, negative and zero cases
    @Test
    public void testSignByteAndShort_signCases() throws Throwable {
        assertEquals((byte) 1, MathUtils.sign((byte) 5));
        assertEquals((byte) -1, MathUtils.sign((byte) -5));
        assertEquals((byte) 0, MathUtils.sign((byte) 0));
        assertEquals((short) 1, MathUtils.sign((short) 5));
        assertEquals((short) -1, MathUtils.sign((short) -5));
    }

    // sign(double): positive, negative, zero and NaN cases
    @Test
    public void testSignDouble_signAndNan() throws Throwable {
        assertEquals(1.0, MathUtils.sign(5.0), 1e-9);
        assertEquals(-1.0, MathUtils.sign(-5.0), 1e-9);
        assertEquals(0.0, MathUtils.sign(0.0), 1e-9);
        assertTrue(Double.isNaN(MathUtils.sign(Double.NaN)));
    }

    // sign(float): positive, negative and zero cases
    @Test
    public void testSignFloat_signCases() throws Throwable {
        assertEquals(1.0f, MathUtils.sign(5.0f), 1e-9f);
        assertEquals(-1.0f, MathUtils.sign(-5.0f), 1e-9f);
        assertEquals(0.0f, MathUtils.sign(0.0f), 1e-9f);
    }

    // sign(int): positive, negative and zero cases
    @Test
    public void testSignInt_signCases() throws Throwable {
        assertEquals(1, MathUtils.sign(5));
        assertEquals(-1, MathUtils.sign(-5));
        assertEquals(0, MathUtils.sign(0));
    }

    // sign(long): positive, negative and zero cases
    @Test
    public void testSignLong_signCases() throws Throwable {
        assertEquals(1L, MathUtils.sign(5L));
        assertEquals(-1L, MathUtils.sign(-5L));
        assertEquals(0L, MathUtils.sign(0L));
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

    // subAndCheck(long,long): b==Long.MIN_VALUE branch, negative minuend safe, non-negative minuend throws
    @Test
    public void testSubAndCheckLong_minValueSubtrahendCases() throws Throwable {
        long expected = -5L - Long.MIN_VALUE;
        assertEquals(expected, MathUtils.subAndCheck(-5L, Long.MIN_VALUE));
        try {
            MathUtils.subAndCheck(5L, Long.MIN_VALUE);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected2) {
        }
    }
}
