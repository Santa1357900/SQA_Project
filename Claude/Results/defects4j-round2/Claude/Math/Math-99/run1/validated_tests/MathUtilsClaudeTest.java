package org.apache.commons.math.util;

import java.math.BigDecimal;
import java.util.Arrays;

import org.junit.Test;
import static org.junit.Assert.*;

public class MathUtilsClaudeTest {

    // addAndCheck(int,int): normal addition, no overflow
    @Test
    public void testAddAndCheckInt_normalValues_returnsSum() throws Throwable {
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

    // addAndCheck(long,long): normal addition
    @Test
    public void testAddAndCheckLong_normalValues_returnsSum() throws Throwable {
        assertEquals(30L, MathUtils.addAndCheck(10L, 20L));
    }

    // addAndCheck(long,long): positive overflow branch (a>=0,b>=0 path after swap)
    @Test
    public void testAddAndCheckLong_positiveOverflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.addAndCheck(Long.MAX_VALUE, 1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // addAndCheck(long,long): negative overflow branch (a<0,b<0 path)
    @Test
    public void testAddAndCheckLong_negativeOverflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.addAndCheck(Long.MIN_VALUE, -1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // binomialCoefficient: n==k, k==0 and k==1 simple-return branches
    @Test
    public void testBinomialCoefficient_simpleCases_returnsExpected() throws Throwable {
        assertEquals(1L, MathUtils.binomialCoefficient(7, 7));
        assertEquals(1L, MathUtils.binomialCoefficient(7, 0));
        assertEquals(7L, MathUtils.binomialCoefficient(7, 1));
    }

    // binomialCoefficient: k > n/2 symmetry branch, naive loop (n<=61)
    @Test
    public void testBinomialCoefficient_symmetryBranch_returnsCorrectValue() throws Throwable {
        assertEquals(120L, MathUtils.binomialCoefficient(10, 7));
    }

    // binomialCoefficient: n < k throws IllegalArgumentException
    @Test
    public void testBinomialCoefficient_nLessThanK_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficient(2, 5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // binomialCoefficient: negative n throws IllegalArgumentException
    @Test
    public void testBinomialCoefficient_negativeN_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficient(-5, -5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // binomialCoefficientDouble: n>=67 goes through the double-loop branch
    @Test
    public void testBinomialCoefficientDouble_largeN_returnsCorrectValue() throws Throwable {
        assertEquals(2211.0, MathUtils.binomialCoefficientDouble(67, 2), 1e-6);
    }

    // binomialCoefficientLog: n==k returns 0
    @Test
    public void testBinomialCoefficientLog_nEqualsK_returnsZero() throws Throwable {
        assertEquals(0.0, MathUtils.binomialCoefficientLog(5, 5), 1e-9);
    }

    // binomialCoefficientLog: 67<=n<1030 branch delegates to log(binomialCoefficientDouble)
    @Test
    public void testBinomialCoefficientLog_midRange_matchesLogOfExactValue() throws Throwable {
        double expected = Math.log(2415.0);
        assertEquals(expected, MathUtils.binomialCoefficientLog(70, 2), 1e-9);
    }

    // cosh: standard hyperbolic cosine definition at 0
    @Test
    public void testCosh_zero_returnsOne() throws Throwable {
        assertEquals(1.0, MathUtils.cosh(0.0), 1e-9);
    }

    // equals(double,double): NaN==NaN branch and regular equal/unequal values
    @Test
    public void testEqualsDouble_nanAndRegularValues_returnsExpected() throws Throwable {
        assertTrue(MathUtils.equals(Double.NaN, Double.NaN));
        assertTrue(MathUtils.equals(2.0, 2.0));
        assertFalse(MathUtils.equals(2.0, 3.0));
    }

    // equals(double,double,eps): inside and outside the allowed error range
    @Test
    public void testEqualsDoubleEps_variousRanges_returnsExpected() throws Throwable {
        assertTrue(MathUtils.equals(1.0, 1.3, 0.5));
        assertFalse(MathUtils.equals(1.0, 2.0, 0.5));
    }

    // equals(double[],double[]): null/null, one-null/xor, length-mismatch, equal-arrays branches
    @Test
    public void testEqualsArray_edgeCases_returnsExpected() throws Throwable {
        double[] nullArr = null;
        double[] arr1 = new double[] {1.0, 2.0};
        double[] arr2 = new double[] {1.0, 2.0};
        double[] arr3 = new double[] {1.0};
        assertTrue(MathUtils.equals(nullArr, (double[]) null));
        assertFalse(MathUtils.equals(nullArr, arr1));
        assertFalse(MathUtils.equals(arr1, arr3));
        assertTrue(MathUtils.equals(arr1, arr2));
    }

    // factorial: n==0 returns 1
    @Test
    public void testFactorial_zero_returnsOne() throws Throwable {
        assertEquals(1L, MathUtils.factorial(0));
    }

    // factorial: negative n throws IllegalArgumentException
    @Test
    public void testFactorial_negativeN_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.factorial(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // factorial: n>20 throws ArithmeticException
    @Test
    public void testFactorial_tooLarge_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.factorial(21);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // factorialDouble: n>=21 branch using factorialLog/exp
    @Test
    public void testFactorialDouble_largeN_returnsCorrectValue() throws Throwable {
        assertEquals(51090942171709440000.0, MathUtils.factorialDouble(21), 100000.0);
    }

    // factorialLog: n>=21 branch, sum-of-logs definition of ln(n!)
    @Test
    public void testFactorialLog_largeN_matchesSumOfLogs() throws Throwable {
        double expected = 0;
        for (int i = 2; i <= 25; i++) {
            expected += Math.log((double) i);
        }
        assertEquals(expected, MathUtils.factorialLog(25), 1e-9);
    }

    // gcd: u==0 || v==0 branch, special case gcd(0,0)==0
    @Test
    public void testGcd_bothZero_returnsZero() throws Throwable {
        assertEquals(0, MathUtils.gcd(0, 0));
    }

    // gcd: documented MIN_VALUE/MIN_VALUE overflow case throws ArithmeticException
    @Test
    public void testGcd_bothMinValue_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.gcd(Integer.MIN_VALUE, Integer.MIN_VALUE);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // gcd: normal binary-gcd computation
    @Test
    public void testGcd_normalValues_returnsGcd() throws Throwable {
        assertEquals(6, MathUtils.gcd(48, 18));
    }

    // hash(double)/hash(double[]): contract matches Double.hashCode / Arrays.hashCode
    @Test
    public void testHash_matchesStandardHashCodeContract() throws Throwable {
        assertEquals(new Double(2.5).hashCode(), MathUtils.hash(2.5));
        double[] arr = new double[] {1.0, 2.0, 3.0};
        assertEquals(Arrays.hashCode(arr), MathUtils.hash(arr));
        assertEquals(Arrays.hashCode((double[]) null), MathUtils.hash((double[]) null));
    }

    // indicator: byte/int/long/short branches for negative/zero/positive
    @Test
    public void testIndicatorIntegralTypes_variousSigns_returnsExpected() throws Throwable {
        assertEquals((byte) -1, MathUtils.indicator((byte) -5));
        assertEquals((byte) 1, MathUtils.indicator((byte) 0));
        assertEquals(1, MathUtils.indicator(5));
        assertEquals(-1L, MathUtils.indicator(-5L));
        assertEquals((short) -1, MathUtils.indicator((short) -3));
    }

    // indicator: double/float NaN branch and sign branch
    @Test
    public void testIndicatorFloatingTypes_nanAndSign_returnsExpected() throws Throwable {
        assertTrue(Double.isNaN(MathUtils.indicator(Double.NaN)));
        assertEquals(1.0, MathUtils.indicator(0.0), 1e-9);
        assertTrue(Float.isNaN(MathUtils.indicator(Float.NaN)));
        assertEquals(-1.0f, MathUtils.indicator(-2.0f), 1e-9f);
    }

    // lcm: a==0 special-case branch returns 0
    @Test
    public void testLcm_zeroArgument_returnsZero() throws Throwable {
        assertEquals(0, MathUtils.lcm(0, 5));
    }

    // lcm: normal computation via gcd formula
    @Test
    public void testLcm_normalValues_returnsLcm() throws Throwable {
        assertEquals(12, MathUtils.lcm(4, 6));
    }

    // log(base,x): standard change-of-base formula
    @Test
    public void testLog_baseTwoOfEight_returnsThree() throws Throwable {
        assertEquals(3.0, MathUtils.log(2, 8), 1e-9);
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

    // mulAndCheck(long,long): normal positive*positive path
    @Test
    public void testMulAndCheckLong_normalValues_returnsProduct() throws Throwable {
        assertEquals(42L, MathUtils.mulAndCheck(6L, 7L));
    }

    // nextAfter: direction greater than d increases the value
    @Test
    public void testNextAfter_towardsGreater_returnsLargerValue() throws Throwable {
        double result = MathUtils.nextAfter(1.0, 2.0);
        assertTrue(result > 1.0);
    }

    // nextAfter: direction smaller than d decreases the value
    @Test
    public void testNextAfter_towardsSmaller_returnsSmallerValue() throws Throwable {
        double result = MathUtils.nextAfter(1.0, 0.0);
        assertTrue(result < 1.0);
    }

    // scalb: normal scaling and the NaN/zero/infinite unchanged special cases
    @Test
    public void testScalb_normalAndSpecialValues_returnsExpected() throws Throwable {
        assertEquals(8.0, MathUtils.scalb(1.0, 3), 1e-9);
        assertEquals(0.0, MathUtils.scalb(0.0, 5), 1e-9);
        assertTrue(Double.isNaN(MathUtils.scalb(Double.NaN, 5)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.scalb(Double.POSITIVE_INFINITY, 3), 1e-9);
    }



    // normalizeAngle: a below the center, floor-based wrap-around branch
    @Test
    public void testNormalizeAngle_belowCenter_returnsWithinInterval() throws Throwable {
        double expected = -1.0 + 2 * Math.PI;
        assertEquals(expected, MathUtils.normalizeAngle(-1.0, Math.PI), 1e-9);
    }

    // round(double,scale): default ROUND_HALF_UP rounds half away from zero
    @Test
    public void testRoundDouble_defaultHalfUp_roundsAwayFromZero() throws Throwable {
        assertEquals(3.0, MathUtils.round(2.5, 0), 1e-9);
        assertEquals(-3.0, MathUtils.round(-2.5, 0), 1e-9);
    }

    // round(double,scale,method): ROUND_CEILING rounds toward positive infinity
    @Test
    public void testRoundDoubleWithMethod_ceiling_roundsTowardPositiveInfinity() throws Throwable {
        assertEquals(-2.0, MathUtils.round(-2.5, 0, BigDecimal.ROUND_CEILING), 1e-9);
    }

    // round(float,scale): default ROUND_HALF_UP via roundUnscaled helper
    @Test
    public void testRoundFloat_defaultHalfUp_roundsCorrectly() throws Throwable {
        assertEquals(3.0f, MathUtils.round(2.5f, 0), 1e-9f);
    }

    // sign: byte/int/long/short branches for negative/zero/positive
    @Test
    public void testSignIntegralTypes_variousValues_returnsExpected() throws Throwable {
        assertEquals((byte) 1, MathUtils.sign((byte) 5));
        assertEquals((byte) 0, MathUtils.sign((byte) 0));
        assertEquals(-1, MathUtils.sign(-7));
        assertEquals(0L, MathUtils.sign(0L));
        assertEquals((short) -1, MathUtils.sign((short) -2));
    }

    // sign: double/float zero/positive/negative/NaN branches
    @Test
    public void testSignFloatingTypes_zeroPositiveNegativeNaN_returnsExpected() throws Throwable {
        assertEquals(0.0, MathUtils.sign(0.0), 1e-9);
        assertEquals(1.0, MathUtils.sign(5.0), 1e-9);
        assertEquals(-1.0, MathUtils.sign(-5.0), 1e-9);
        assertTrue(Double.isNaN(MathUtils.sign(Double.NaN)));
        assertEquals(-1.0f, MathUtils.sign(-3.0f), 1e-9f);
    }

    // sinh: standard hyperbolic sine definition at 0
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

    // subAndCheck(long,long): b==Long.MIN_VALUE branch with a<0 computes a-b directly
    @Test
    public void testSubAndCheckLong_minValueBranch_returnsZero() throws Throwable {
        assertEquals(0L, MathUtils.subAndCheck(Long.MIN_VALUE, Long.MIN_VALUE));
    }
}
