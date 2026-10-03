package org.apache.commons.math.util;

import org.junit.Test;
import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

public class MathUtilsClaudeTest {

    // Independent exact binomial coefficient via multiplicative Pascal formula (BigInteger, always exact).
    private static long exactBinomial(int n, int k) {
        BigInteger result = BigInteger.ONE;
        for (int i = 0; i < k; i++) {
            result = result.multiply(BigInteger.valueOf(n - i));
            result = result.divide(BigInteger.valueOf(i + 1));
        }
        return result.longValue();
    }

    // Branch: normal addition within int range.
    @Test
    public void testAddAndCheckInt_normal_returnsSum() throws Throwable {
        assertEquals(300, MathUtils.addAndCheck(100, 200));
    }

    // Branch: int overflow throws.
    @Test
    public void testAddAndCheckInt_overflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.addAndCheck(Integer.MAX_VALUE, 1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // Branch: normal long addition.
    @Test
    public void testAddAndCheckLong_normal_returnsSum() throws Throwable {
        assertEquals(300L, MathUtils.addAndCheck(100L, 200L));
    }

    // Branch: positive long overflow throws.
    @Test
    public void testAddAndCheckLong_positiveOverflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.addAndCheck(Long.MAX_VALUE, 1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // Branch: negative long overflow throws.
    @Test
    public void testAddAndCheckLong_negativeOverflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.addAndCheck(Long.MIN_VALUE, -1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // Branch: opposite-sign addition is always safe.
    @Test
    public void testAddAndCheckLong_oppositeSigns_noOverflow() throws Throwable {
        assertEquals(-1L, MathUtils.addAndCheck(Long.MAX_VALUE, Long.MIN_VALUE));
    }

    // Branches: n<k and n<0 both throw IllegalArgumentException.
    @Test
    public void testBinomialCoefficient_invalidArgs_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficient(3, 5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        try {
            MathUtils.binomialCoefficient(-1, -2);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Branches: k==n, k==0, k==1, k==n-1 short-circuit returns.
    @Test
    public void testBinomialCoefficient_boundaryKValues_shortCircuitReturns() throws Throwable {
        assertEquals(1L, MathUtils.binomialCoefficient(5, 5));
        assertEquals(1L, MathUtils.binomialCoefficient(5, 0));
        assertEquals(5L, MathUtils.binomialCoefficient(5, 1));
        assertEquals(5L, MathUtils.binomialCoefficient(5, 4));
    }

    // General branch, small n: result must equal exact combinatorial value.
    @Test
    public void testBinomialCoefficient_smallGeneral_matchesExactValue() throws Throwable {
        assertEquals(exactBinomial(10, 4), MathUtils.binomialCoefficient(10, 4));
    }

    // General branch, n near documented limit (66): per contract the result must be the EXACT
    // long value (javadoc guarantees exactness up to n=66); this is where double-precision
    // approximation (used internally) can lose precision for large coefficients.
    @Test
    public void testBinomialCoefficient_largeN_matchesExactValue_catchesPrecisionBug() throws Throwable {
        long expected = exactBinomial(66, 33);
        assertEquals(expected, MathUtils.binomialCoefficient(66, 33));
    }

    // Branch: n<k throws (delegated through binomialCoefficientLog).
    @Test
    public void testBinomialCoefficientDouble_nLessThanK_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficientDouble(3, 5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // General branch: double result matches exact integer value.
    @Test
    public void testBinomialCoefficientDouble_general_matchesExpected() throws Throwable {
        assertEquals(210.0, MathUtils.binomialCoefficientDouble(10, 4), 1e-6);
    }

    // Branches: n<k and n<0 throw IllegalArgumentException.
    @Test
    public void testBinomialCoefficientLog_invalidArgs_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficientLog(3, 5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        try {
            MathUtils.binomialCoefficientLog(-1, -2);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Branch: k==1 or k==n-1 returns log(n).
    @Test
    public void testBinomialCoefficientLog_kEqualsOneOrNMinusOne_returnsLogN() throws Throwable {
        assertEquals(Math.log(7.0), MathUtils.binomialCoefficientLog(7, 1), 1e-9);
        assertEquals(Math.log(7.0), MathUtils.binomialCoefficientLog(7, 6), 1e-9);
    }

    // General loop branch: matches ln(exact binomial coefficient).
    @Test
    public void testBinomialCoefficientLog_general_matchesExpected() throws Throwable {
        double expected = Math.log((double) exactBinomial(10, 4));
        assertEquals(expected, MathUtils.binomialCoefficientLog(10, 4), 1e-9);
    }

    // cosh(0)=1, sinh(0)=0 per hyperbolic function definitions.
    @Test
    public void testHyperbolicFunctions_zero_returnsExpected() throws Throwable {
        assertEquals(1.0, MathUtils.cosh(0.0), 1e-9);
        assertEquals(0.0, MathUtils.sinh(0.0), 1e-9);
    }

    // Branches: both NaN -> true, equal -> true, different -> false, one NaN -> false.
    @Test
    public void testEqualsDouble_nanAndEqualValues() throws Throwable {
        assertTrue(MathUtils.equals(Double.NaN, Double.NaN));
        assertTrue(MathUtils.equals(1.0, 1.0));
        assertFalse(MathUtils.equals(1.0, 2.0));
        assertFalse(MathUtils.equals(Double.NaN, 1.0));
    }

    // Branches: both null, one null, different lengths, equal content with NaN.
    @Test
    public void testEqualsDoubleArray_nullLengthAndContent() throws Throwable {
        assertTrue(MathUtils.equals((double[]) null, (double[]) null));
        assertFalse(MathUtils.equals(new double[] {1.0}, (double[]) null));
        assertFalse(MathUtils.equals(new double[] {1.0, 2.0}, new double[] {1.0}));
        assertTrue(MathUtils.equals(new double[] {1.0, Double.NaN}, new double[] {1.0, Double.NaN}));
    }

    // hash(double) delegates to Double.hashCode; hash(double[]) delegates to Arrays.hashCode (including null).
    @Test
    public void testHash_matchesJdkHashCodes() throws Throwable {
        assertEquals(new Double(3.14).hashCode(), MathUtils.hash(3.14));
        double[] arr = {1.0, 2.0};
        assertEquals(Arrays.hashCode(arr), MathUtils.hash(arr));
        assertEquals(Arrays.hashCode((double[]) null), MathUtils.hash((double[]) null));
    }

    // Basic values and documented boundary n=20 (largest n with n! < Long.MAX_VALUE).
    @Test
    public void testFactorial_basicAndBoundary() throws Throwable {
        assertEquals(1L, MathUtils.factorial(0));
        assertEquals(1L, MathUtils.factorial(1));
        assertEquals(120L, MathUtils.factorial(5));
        assertEquals(2432902008176640000L, MathUtils.factorial(20));
    }

    // Branch: n<0 throws IllegalArgumentException.
    @Test
    public void testFactorial_negative_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.factorial(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Branch: n>20 throws ArithmeticException (too large for long).
    @Test
    public void testFactorial_tooLarge_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.factorial(21);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // Branch: n<0 throws; n<21 uses exact factorial.
    @Test
    public void testFactorialDouble_negativeThrowsAndSmallNExact() throws Throwable {
        try {
            MathUtils.factorialDouble(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        assertEquals(120.0, MathUtils.factorialDouble(5), 1e-9);
    }

    // Branch: n<0 throws; n<21 uses log(factorial); n>=21 uses summation loop (n! = 20! * 21).
    @Test
    public void testFactorialLog_negativeThrowsAndMatchesLogFactorial() throws Throwable {
        try {
            MathUtils.factorialLog(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        assertEquals(Math.log(120.0), MathUtils.factorialLog(5), 1e-9);
        double expected21 = Math.log((double) MathUtils.factorial(20)) + Math.log(21.0);
        assertEquals(expected21, MathUtils.factorialLog(21), 1e-6);
    }

    // Branch: either zero returns sum of abs; normal gcd; gcd of negative values uses absolute value.
    @Test
    public void testGcd_zeroAndNormalCases() throws Throwable {
        assertEquals(0, MathUtils.gcd(0, 0));
        assertEquals(7, MathUtils.gcd(0, 7));
        assertEquals(6, MathUtils.gcd(12, 18));
        assertEquals(6, MathUtils.gcd(-12, 18));
    }



    // indicator(double/float): NaN -> NaN, x>=0 -> +1, x<0 -> -1.
    @Test
    public void testIndicatorFloatingTypes_nanAndSigns() throws Throwable {
        assertTrue(Double.isNaN(MathUtils.indicator(Double.NaN)));
        assertEquals(1.0, MathUtils.indicator(0.0), 1e-9);
        assertEquals(-1.0, MathUtils.indicator(-1.0), 1e-9);
        assertTrue(Float.isNaN(MathUtils.indicator(Float.NaN)));
        assertEquals(1.0f, MathUtils.indicator(0.0f), 1e-6f);
        assertEquals(-1.0f, MathUtils.indicator(-1.0f), 1e-6f);
    }

    // Normal lcm and lcm with zero.
    @Test
    public void testLcm_basicAndZero() throws Throwable {
        assertEquals(12, MathUtils.lcm(4, 6));
        assertEquals(0, MathUtils.lcm(0, 5));
    }

    // Covers: normal case, base=0/x>0 -> 0, base>0/x=0 -> -Infinity, both 0 -> NaN, negative arg -> NaN.
    @Test
    public void testLog_variousCasesPerJavadoc() throws Throwable {
        assertEquals(3.0, MathUtils.log(2.0, 8.0), 1e-9);
        assertEquals(0.0, MathUtils.log(0.0, 5.0), 1e-9);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), MathUtils.log(2.0, 0.0), 1e-9);
        assertTrue(Double.isNaN(MathUtils.log(0.0, 0.0)));
        assertTrue(Double.isNaN(MathUtils.log(-1.0, 5.0)));
    }

    // Normal int multiply and overflow.
    @Test
    public void testMulAndCheckInt_normalAndOverflow() throws Throwable {
        assertEquals(42, MathUtils.mulAndCheck(6, 7));
        try {
            MathUtils.mulAndCheck(Integer.MAX_VALUE, 2);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // Covers positive*positive, negative*negative, negative*positive, zero, and overflow branches.
    @Test
    public void testMulAndCheckLong_variousSignsAndOverflow() throws Throwable {
        assertEquals(42L, MathUtils.mulAndCheck(6L, 7L));
        assertEquals(42L, MathUtils.mulAndCheck(-6L, -7L));
        assertEquals(-42L, MathUtils.mulAndCheck(-6L, 7L));
        assertEquals(0L, MathUtils.mulAndCheck(0L, 100L));
        try {
            MathUtils.mulAndCheck(Long.MAX_VALUE, 2L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // Special cases: NaN unchanged, Infinite unchanged, zero moves to +/-MIN_VALUE.
    @Test
    public void testNextAfter_specialAndNormalValues() throws Throwable {
        assertTrue(Double.isNaN(MathUtils.nextAfter(Double.NaN, 1.0)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.nextAfter(Double.POSITIVE_INFINITY, 0.0), 0.0);
        assertEquals(Double.MIN_VALUE, MathUtils.nextAfter(0.0, 1.0), 0.0);
        assertEquals(-Double.MIN_VALUE, MathUtils.nextAfter(0.0, -1.0), 0.0);
        assertTrue(MathUtils.nextAfter(1.0, 2.0) > 1.0);
        assertTrue(MathUtils.nextAfter(1.0, 0.0) < 1.0);
    }

    // Special cases (0, NaN, Infinite unchanged) and normal scaling.
    @Test
    public void testScalb_specialAndNormal() throws Throwable {
        assertEquals(0.0, MathUtils.scalb(0.0, 5), 1e-9);
        assertTrue(Double.isNaN(MathUtils.scalb(Double.NaN, 5)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.scalb(Double.POSITIVE_INFINITY, 5), 0.0);
        assertEquals(8.0, MathUtils.scalb(1.0, 3), 1e-9);
        assertEquals(1.0, MathUtils.scalb(8.0, -3), 1e-9);
    }

    // Verifies documented formula result on several known cases.
    @Test
    public void testNormalizeAngle_knownCases() throws Throwable {
        assertEquals(0.0, MathUtils.normalizeAngle(0.0, 0.0), 1e-9);
        assertEquals(0.0, MathUtils.normalizeAngle(2 * Math.PI, 0.0), 1e-9);
        assertEquals(-Math.PI, MathUtils.normalizeAngle(3 * Math.PI, 0.0), 1e-9);
        double expected = -0.5 + 2 * Math.PI;
        assertEquals(expected, MathUtils.normalizeAngle(-0.5, Math.PI), 1e-9);
    }

    // Default ROUND_HALF_UP: ties away from zero, ordinary scale rounding.
    @Test
    public void testRoundDouble_defaultHalfUp() throws Throwable {
        assertEquals(3.0, MathUtils.round(2.5, 0), 1e-9);
        assertEquals(-3.0, MathUtils.round(-2.5, 0), 1e-9);
        assertEquals(1.23, MathUtils.round(1.234, 2), 1e-9);
    }

    // NumberFormatException catch branch: NaN -> NaN, +/-Infinity -> unchanged.
    @Test
    public void testRoundDoubleWithMethod_nanAndInfiniteCatch() throws Throwable {
        assertTrue(Double.isNaN(MathUtils.round(Double.NaN, 2, BigDecimal.ROUND_HALF_UP)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY),
            MathUtils.round(Double.POSITIVE_INFINITY, 2, BigDecimal.ROUND_HALF_UP), 0.0);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY),
            MathUtils.round(Double.NEGATIVE_INFINITY, 2, BigDecimal.ROUND_HALF_UP), 0.0);
    }

    // Default ROUND_HALF_UP for float: ties away from zero.
    @Test
    public void testRoundFloat_defaultHalfUp() throws Throwable {
        assertEquals(3.0f, MathUtils.round(2.5f, 0), 1e-4f);
        assertEquals(-3.0f, MathUtils.round(-2.5f, 0), 1e-4f);
    }

    // ROUND_CEILING and ROUND_FLOOR for both positive and negative values.
    @Test
    public void testRoundFloat_ceilingAndFloor_bothSigns() throws Throwable {
        assertEquals(3.0f, MathUtils.round(2.1f, 0, BigDecimal.ROUND_CEILING), 1e-4f);
        assertEquals(-2.0f, MathUtils.round(-2.1f, 0, BigDecimal.ROUND_CEILING), 1e-4f);
        assertEquals(2.0f, MathUtils.round(2.9f, 0, BigDecimal.ROUND_FLOOR), 1e-4f);
        assertEquals(-3.0f, MathUtils.round(-2.1f, 0, BigDecimal.ROUND_FLOOR), 1e-4f);
    }

    // ROUND_DOWN truncates towards zero; ROUND_UP rounds away from zero.
    @Test
    public void testRoundFloat_downAndUp_truncateAndAwayFromZero() throws Throwable {
        assertEquals(2.0f, MathUtils.round(2.9f, 0, BigDecimal.ROUND_DOWN), 1e-4f);
        assertEquals(-2.0f, MathUtils.round(-2.9f, 0, BigDecimal.ROUND_DOWN), 1e-4f);
        assertEquals(3.0f, MathUtils.round(2.1f, 0, BigDecimal.ROUND_UP), 1e-4f);
        assertEquals(-3.0f, MathUtils.round(-2.1f, 0, BigDecimal.ROUND_UP), 1e-4f);
    }

    // ROUND_HALF_DOWN: tie rounds toward zero; ROUND_HALF_EVEN: tie rounds to nearest even.
    @Test
    public void testRoundFloat_halfModes_tieBehavior() throws Throwable {
        assertEquals(2.0f, MathUtils.round(2.5f, 0, BigDecimal.ROUND_HALF_DOWN), 1e-4f);
        assertEquals(2.0f, MathUtils.round(2.5f, 0, BigDecimal.ROUND_HALF_EVEN), 1e-4f);
        assertEquals(4.0f, MathUtils.round(3.5f, 0, BigDecimal.ROUND_HALF_EVEN), 1e-4f);
    }

    // ROUND_UNNECESSARY: exact value passes, inexact throws; invalid method constant throws.
    @Test
    public void testRoundFloat_unnecessaryAndInvalidMethod() throws Throwable {
        assertEquals(3.0f, MathUtils.round(3.0f, 0, BigDecimal.ROUND_UNNECESSARY), 1e-4f);
        try {
            MathUtils.round(3.5f, 0, BigDecimal.ROUND_UNNECESSARY);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
        try {
            MathUtils.round(1.0f, 0, 999);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // sign(byte/short/int/long): positive->+1, zero->0, negative->-1.
    @Test
    public void testSignIntegralTypes_values() throws Throwable {
        assertEquals((byte) 1, MathUtils.sign((byte) 5));
        assertEquals((byte) 0, MathUtils.sign((byte) 0));
        assertEquals((byte) -1, MathUtils.sign((byte) -5));
        assertEquals((short) 1, MathUtils.sign((short) 5));
        assertEquals((short) 0, MathUtils.sign((short) 0));
        assertEquals((short) -1, MathUtils.sign((short) -5));
        assertEquals(1, MathUtils.sign(5));
        assertEquals(0, MathUtils.sign(0));
        assertEquals(-1, MathUtils.sign(-5));
        assertEquals(1L, MathUtils.sign(5L));
        assertEquals(0L, MathUtils.sign(0L));
        assertEquals(-1L, MathUtils.sign(-5L));
    }

    // sign(double/float): NaN -> NaN, positive->+1, zero->0, negative->-1.
    @Test
    public void testSignFloatingTypes_nanZeroPositiveNegative() throws Throwable {
        assertTrue(Double.isNaN(MathUtils.sign(Double.NaN)));
        assertEquals(0.0, MathUtils.sign(0.0), 1e-9);
        assertEquals(1.0, MathUtils.sign(5.0), 1e-9);
        assertEquals(-1.0, MathUtils.sign(-5.0), 1e-9);
        assertTrue(Float.isNaN(MathUtils.sign(Float.NaN)));
        assertEquals(0.0f, MathUtils.sign(0.0f), 1e-6f);
        assertEquals(1.0f, MathUtils.sign(5.0f), 1e-6f);
        assertEquals(-1.0f, MathUtils.sign(-5.0f), 1e-6f);
    }

    // Normal int subtraction and underflow.
    @Test
    public void testSubAndCheckInt_normalAndUnderflow() throws Throwable {
        assertEquals(2, MathUtils.subAndCheck(5, 3));
        try {
            MathUtils.subAndCheck(Integer.MIN_VALUE, 1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // Covers normal case, b==Long.MIN_VALUE safe/unsafe branches, and additive-inverse overflow.
    @Test
    public void testSubAndCheckLong_variousCases() throws Throwable {
        assertEquals(2L, MathUtils.subAndCheck(5L, 3L));
        assertEquals(Long.MAX_VALUE, MathUtils.subAndCheck(-1L, Long.MIN_VALUE));
        try {
            MathUtils.subAndCheck(0L, Long.MIN_VALUE);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
        try {
            MathUtils.subAndCheck(Long.MAX_VALUE, -1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }
}
