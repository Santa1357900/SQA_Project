package org.apache.commons.math.util;

import static org.junit.Assert.*;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.commons.math.exception.NonMonotonousSequenceException;

public class MathUtilsClaudeTest {

    // addAndCheck(int,int): normal addition within range
    @Test
    public void testAddAndCheckInt_normal_returnsSum() throws Throwable {
        assertEquals(300000, MathUtils.addAndCheck(100000, 200000));
    }

    // addAndCheck(int,int): overflow beyond Integer.MAX_VALUE throws
    @Test
    public void testAddAndCheckInt_overflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.addAndCheck(Integer.MAX_VALUE, 1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // addAndCheck(long,long): overflow beyond Long.MAX_VALUE throws
    @Test
    public void testAddAndCheckLong_overflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.addAndCheck(Long.MAX_VALUE, 1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // binomialCoefficient: k==n and k==0 both return 1
    @Test
    public void testBinomialCoefficient_edgeCases_returnsOne() throws Throwable {
        assertEquals(1L, MathUtils.binomialCoefficient(5, 5));
        assertEquals(1L, MathUtils.binomialCoefficient(5, 0));
    }

    // binomialCoefficient: k==1 or k==n-1 returns n
    @Test
    public void testBinomialCoefficient_kEqualsOneOrNMinusOne_returnsN() throws Throwable {
        assertEquals(7L, MathUtils.binomialCoefficient(7, 1));
        assertEquals(7L, MathUtils.binomialCoefficient(7, 6));
    }

    // binomialCoefficient: symmetry C(n,k) == C(n,n-k), matches known exact value
    @Test
    public void testBinomialCoefficient_symmetry_matchesDirect() throws Throwable {
        assertEquals(120L, MathUtils.binomialCoefficient(10, 3));
        assertEquals(120L, MathUtils.binomialCoefficient(10, 7));
    }

    // binomialCoefficient: n < k is invalid precondition
    @Test
    public void testBinomialCoefficient_nLessThanK_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficient(3, 5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // binomialCoefficient: negative n is invalid precondition
    @Test
    public void testBinomialCoefficient_negativeN_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.binomialCoefficient(-5, -10);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // binomialCoefficient: result too large for long throws ArithmeticException
    @Test
    public void testBinomialCoefficient_overflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.binomialCoefficient(67, 33);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // binomialCoefficientDouble: for n<67 must equal exact long value
    @Test
    public void testBinomialCoefficientDouble_matchesExactValue() throws Throwable {
        assertEquals(120.0, MathUtils.binomialCoefficientDouble(10, 3), 1e-9);
    }

    // binomialCoefficientLog: for n<67 must equal natural log of exact value
    @Test
    public void testBinomialCoefficientLog_matchesLogOfExact() throws Throwable {
        assertEquals(Math.log(120.0), MathUtils.binomialCoefficientLog(10, 3), 1e-9);
    }

    // compareTo: equal-within-eps returns 0, less returns -1, greater returns 1
    @Test
    public void testCompareTo_equalAndOrdering() throws Throwable {
        assertEquals(0, MathUtils.compareTo(1.0, 1.0, 0.001));
        assertEquals(-1, MathUtils.compareTo(1.0, 2.0, 0.001));
        assertEquals(1, MathUtils.compareTo(2.0, 1.0, 0.001));
    }

    // cosh(0)=1 and sinh(0)=0 per hyperbolic function definitions
    @Test
    public void testCoshSinh_zero_returnsExpected() throws Throwable {
        assertEquals(1.0, MathUtils.cosh(0.0), 1e-9);
        assertEquals(0.0, MathUtils.sinh(0.0), 1e-9);
    }

    // equals(double,double): NaN==NaN true, exact equal true, different false
    @Test
    public void testEqualsDoubleDouble_basicCases() throws Throwable {
        assertTrue(MathUtils.equals(Double.NaN, Double.NaN));
        assertTrue(MathUtils.equals(1.0, 1.0));
        assertFalse(MathUtils.equals(1.0, 2.0));
    }

    // equals(double,double) contract: defined as equals(x,y,1) -> 1 ulp apart must be equal
    @Test
    public void testEquals_oneUlpApart_trueFromContract() throws Throwable {
        double x = 1.0;
        double y = Double.longBitsToDouble(Double.doubleToLongBits(x) + 1L);
        assertTrue(MathUtils.equals(x, y));
    }

    // equalsIncludingNaN(double,double): NaN vs normal number must be false
    @Test
    public void testEqualsIncludingNaN_double_NaNvsNumber_false() throws Throwable {
        assertFalse(MathUtils.equalsIncludingNaN(Double.NaN, 1.0));
    }

    // equals(double,double,double eps): within eps true, outside eps false
    @Test
    public void testEqualsDoubleEps_withinAndOutsideRange() throws Throwable {
        assertTrue(MathUtils.equals(1.0, 1.0005, 0.001));
        assertFalse(MathUtils.equals(1.0, 1.01, 0.001));
    }

    // equals(double,double,int maxUlps): 1 ulp apart true, far apart false
    @Test
    public void testEqualsMaxUlps_adjacentTrueFarFalse() throws Throwable {
        double x = 1.0;
        double y = Double.longBitsToDouble(Double.doubleToLongBits(x) + 1L);
        assertTrue(MathUtils.equals(x, y, 1));
        assertFalse(MathUtils.equals(1.0, 2.0, 1));
    }

    // equalsIncludingNaN(double,double,int): both NaN returns true
    @Test
    public void testEqualsIncludingNaNMaxUlps_bothNaN_true() throws Throwable {
        assertTrue(MathUtils.equalsIncludingNaN(Double.NaN, Double.NaN, 1));
    }

    // equals(double[],double[]): null handling per javadoc (both null true, one null false)
    @Test
    public void testEqualsArray_nullHandling() throws Throwable {
        assertTrue(MathUtils.equals((double[]) null, (double[]) null));
        assertFalse(MathUtils.equals(new double[] {1.0}, (double[]) null));
        assertFalse(MathUtils.equals((double[]) null, new double[] {1.0}));
    }

    // equals(double[],double[]): equal arrays true, different length/content false
    @Test
    public void testEqualsArray_equalAndDifferent() throws Throwable {
        double[] a = {1.0, 2.0, 3.0};
        double[] b = {1.0, 2.0, 3.0};
        double[] c = {1.0, 2.0};
        double[] d = {1.0, 2.0, 4.0};
        assertTrue(MathUtils.equals(a, b));
        assertFalse(MathUtils.equals(a, c));
        assertFalse(MathUtils.equals(a, d));
    }

    // equalsIncludingNaN(double[],double[]): matching NaN positions treated as equal
    @Test
    public void testEqualsIncludingNaNArray_withNaNElements() throws Throwable {
        double[] a = {1.0, Double.NaN, 3.0};
        double[] b = {1.0, Double.NaN, 3.0};
        assertTrue(MathUtils.equalsIncludingNaN(a, b));
    }

    // factorial: boundary values 0! and 20! (largest representable as long)
    @Test
    public void testFactorial_boundaryValues() throws Throwable {
        assertEquals(1L, MathUtils.factorial(0));
        assertEquals(2432902008176640000L, MathUtils.factorial(20));
    }

    // factorial: negative argument throws IllegalArgumentException
    @Test
    public void testFactorial_negative_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.factorial(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // factorial: n>20 overflows long, throws ArithmeticException
    @Test
    public void testFactorial_tooLarge_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.factorial(21);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // factorialLog(0) = log(0!) = log(1) = 0
    @Test
    public void testFactorialLog_zero_returnsZero() throws Throwable {
        assertEquals(0.0, MathUtils.factorialLog(0), 1e-9);
    }

    // gcd(int,int): basic value and zero special cases per javadoc
    @Test
    public void testGcdInt_basicAndZeroCases() throws Throwable {
        assertEquals(6, MathUtils.gcd(12, 18));
        assertEquals(5, MathUtils.gcd(0, 5));
        assertEquals(0, MathUtils.gcd(0, 0));
    }

    // gcd(int,int): MIN_VALUE with 0 would be 2^31, throws ArithmeticException
    @Test
    public void testGcdInt_minValueOverflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.gcd(Integer.MIN_VALUE, 0);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // hash(double): must follow Double.hashCode() contract
    @Test
    public void testHashDouble_matchesJdkContract() throws Throwable {
        assertEquals(Double.valueOf(5.5).hashCode(), MathUtils.hash(5.5));
    }

    // hash(double[]): null array hashes to 0 per Arrays.hashCode contract
    @Test
    public void testHashDoubleArray_nullReturnsZero() throws Throwable {
        assertEquals(0, MathUtils.hash((double[]) null));
    }

    // indicator(double): positive, negative, zero(treated as positive), NaN
    @Test
    public void testIndicatorDouble_variousSigns() throws Throwable {
        assertEquals(1.0, MathUtils.indicator(5.0), 1e-9);
        assertEquals(-1.0, MathUtils.indicator(-5.0), 1e-9);
        assertEquals(1.0, MathUtils.indicator(0.0), 1e-9);
        assertTrue(Double.isNaN(MathUtils.indicator(Double.NaN)));
    }

    // lcm(int,int): basic value and zero special case per javadoc
    @Test
    public void testLcmInt_basicAndZero() throws Throwable {
        assertEquals(12, MathUtils.lcm(4, 6));
        assertEquals(0, MathUtils.lcm(0, 5));
    }

    // lcm(int,int): MIN_VALUE with power-of-two n overflows, throws ArithmeticException
    @Test
    public void testLcmInt_minValueOverflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.lcm(Integer.MIN_VALUE, 1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // log(base,x): log base 2 of 8 equals 3
    @Test
    public void testLog_baseAndX() throws Throwable {
        assertEquals(3.0, MathUtils.log(2.0, 8.0), 1e-9);
    }

    // mulAndCheck(int,int): overflow beyond Integer.MAX_VALUE throws
    @Test
    public void testMulAndCheckInt_overflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.mulAndCheck(Integer.MAX_VALUE, 2);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // scalb: special values pass-through (0, NaN, Infinite) and normal scaling
    @Test
    public void testScalb_specialAndNormalValues() throws Throwable {
        assertEquals(0.0, MathUtils.scalb(0.0, 5), 1e-9);
        assertTrue(Double.isNaN(MathUtils.scalb(Double.NaN, 3)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.scalb(Double.POSITIVE_INFINITY, 2), 1e-9);
        assertEquals(8.0, MathUtils.scalb(1.0, 3), 1e-9);
    }

    // normalizeAngle: reduces an angle outside [0,2pi) back into range
    @Test
    public void testNormalizeAngle_reducesToRange() throws Throwable {
        double result = MathUtils.normalizeAngle(3.0 * Math.PI, Math.PI);
        assertEquals(Math.PI, result, 1e-9);
    }

    // normalizeArray: scales non-NaN elements proportionally to reach target sum
    @Test
    public void testNormalizeArray_basicScaling() throws Throwable {
        double[] values = {1.0, 2.0, 3.0};
        double[] out = MathUtils.normalizeArray(values, 12.0);
        assertEquals(2.0, out[0], 1e-9);
        assertEquals(4.0, out[1], 1e-9);
        assertEquals(6.0, out[2], 1e-9);
    }

    // normalizeArray: infinite target sum throws IllegalArgumentException
    @Test
    public void testNormalizeArray_infiniteSum_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.normalizeArray(new double[] {1.0, 2.0}, Double.POSITIVE_INFINITY);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // normalizeArray: NaN target sum throws IllegalArgumentException
    @Test
    public void testNormalizeArray_nanSum_throwsIllegalArgumentException() throws Throwable {
        try {
            MathUtils.normalizeArray(new double[] {1.0, 2.0}, Double.NaN);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // normalizeArray: infinite element in input array throws ArithmeticException
    @Test
    public void testNormalizeArray_infiniteElement_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.normalizeArray(new double[] {1.0, Double.POSITIVE_INFINITY}, 10.0);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // normalizeArray: elements summing to zero throws ArithmeticException
    @Test
    public void testNormalizeArray_sumZero_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.normalizeArray(new double[] {1.0, -1.0}, 5.0);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // round(double,int): default ROUND_HALF_UP rounds halves away from zero
    @Test
    public void testRoundDouble_halfUpDefault() throws Throwable {
        assertEquals(3.0, MathUtils.round(2.5, 0), 1e-9);
        assertEquals(-3.0, MathUtils.round(-2.5, 0), 1e-9);
    }

    // round(double,int,int): ROUND_DOWN truncates toward zero
    @Test
    public void testRoundDoubleWithMethod_roundDown() throws Throwable {
        assertEquals(2.0, MathUtils.round(2.9, 0, BigDecimal.ROUND_DOWN), 1e-9);
    }

    // round(float,int): default ROUND_HALF_UP rounds halves away from zero
    @Test
    public void testRoundFloat_halfUpDefault() throws Throwable {
        assertEquals(3.0f, MathUtils.round(2.5f, 0), 1e-6f);
        assertEquals(-3.0f, MathUtils.round(-2.5f, 0), 1e-6f);
    }

    // sign(double): positive, negative, zero, and NaN per javadoc
    @Test
    public void testSignDouble_variousValues() throws Throwable {
        assertEquals(1.0, MathUtils.sign(5.0), 1e-9);
        assertEquals(-1.0, MathUtils.sign(-5.0), 1e-9);
        assertEquals(0.0, MathUtils.sign(0.0), 1e-9);
        assertTrue(Double.isNaN(MathUtils.sign(Double.NaN)));
    }

    // subAndCheck(int,int): underflow below Integer.MIN_VALUE throws
    @Test
    public void testSubAndCheckInt_overflow_throwsArithmeticException() throws Throwable {
        try {
            MathUtils.subAndCheck(Integer.MIN_VALUE, 1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // pow(int,int): basic exponentiation and negative exponent throws
    @Test
    public void testPowIntInt_basicAndNegativeExponent() throws Throwable {
        assertEquals(1024, MathUtils.pow(2, 10));
        try {
            MathUtils.pow(2, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // pow(BigInteger,int): basic exponentiation matches BigInteger.pow
    @Test
    public void testPowBigInteger_basic() throws Throwable {
        assertEquals(BigInteger.valueOf(1024), MathUtils.pow(BigInteger.valueOf(2), 10));
    }

    // distance(double[],double[]): Euclidean distance between two points
    @Test
    public void testDistance_doubleArrays() throws Throwable {
        double[] p1 = {0.0, 0.0};
        double[] p2 = {3.0, 4.0};
        assertEquals(5.0, MathUtils.distance(p1, p2), 1e-9);
    }

    // distance1(double[],double[]): L1 (sum of abs) distance between two points
    @Test
    public void testDistance1_doubleArrays() throws Throwable {
        double[] p1 = {1.0, 2.0};
        double[] p2 = {4.0, 6.0};
        assertEquals(7.0, MathUtils.distance1(p1, p2), 1e-9);
    }

    // checkOrder(double[]): strictly increasing sorted array passes without exception
    @Test
    public void testCheckOrder_sortedArray_noException() throws Throwable {
        double[] vals = {1.0, 2.0, 3.0};
        MathUtils.checkOrder(vals);
        assertEquals(3, vals.length);
    }

    // checkOrder(double[]): unsorted array throws NonMonotonousSequenceException
    @Test
    public void testCheckOrder_unsortedArray_throwsNonMonotonousSequenceException() throws Throwable {
        double[] vals = {3.0, 2.0, 1.0};
        try {
            MathUtils.checkOrder(vals);
            fail("expected NonMonotonousSequenceException");
        } catch (NonMonotonousSequenceException expected) { }
    }

    // safeNorm: Euclidean 2-norm of a simple vector, matches sqrt(sum of squares)
    @Test
    public void testSafeNorm_basicVector() throws Throwable {
        double[] v = {3.0, 4.0};
        assertEquals(5.0, MathUtils.safeNorm(v), 1e-9);
    }
}
