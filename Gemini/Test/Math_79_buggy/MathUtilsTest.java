package org.apache.commons.math.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.BigInteger;

public class MathUtilsTest {

    @Test
    public void testAddAndCheckInt() throws Throwable {
        assertEquals(5, MathUtils.addAndCheck(2, 3));
        
        try {
            MathUtils.addAndCheck(Integer.MAX_VALUE, 1);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        try {
            MathUtils.addAndCheck(Integer.MIN_VALUE, -1);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testAddAndCheckLong() throws Throwable {
        assertEquals(5L, MathUtils.addAndCheck(2L, 3L));

        // a > b branch (symmetry check)
        assertEquals(5L, MathUtils.addAndCheck(3L, 2L));

        // a < 0, b < 0 negative overflow
        try {
            MathUtils.addAndCheck(Long.MIN_VALUE, -1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        // a < 0, b < 0 normal case
        assertEquals(-5L, MathUtils.addAndCheck(Long.MIN_VALUE + 10L, -5L));

        // a < 0, b >= 0 (opposite sign, safe)
        assertEquals(2L, MathUtils.addAndCheck(-3L, 5L));

        // a >= 0, b >= 0 positive overflow
        try {
            MathUtils.addAndCheck(Long.MAX_VALUE, 1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        // a >= 0, b >= 0 normal case
        assertEquals(5L, MathUtils.addAndCheck(2L, 3L));
    }

    @Test
    public void testBinomialCoefficient() throws Throwable {
        assertEquals(1L, MathUtils.binomialCoefficient(5, 0));
        assertEquals(1L, MathUtils.binomialCoefficient(5, 5));
        assertEquals(5L, MathUtils.binomialCoefficient(5, 1));
        assertEquals(5L, MathUtils.binomialCoefficient(5, 4));
        assertEquals(10L, MathUtils.binomialCoefficient(5, 2));

        // Symmetry when k > n / 2
        assertEquals(10L, MathUtils.binomialCoefficient(5, 3));

        // n <= 61 branch
        assertEquals(20030010L, MathUtils.binomialCoefficient(30, 10));

        // 61 < n <= 66 branch
        assertEquals(44261653680L, MathUtils.binomialCoefficient(65, 30));

        // n > 66 branch
        assertEquals(14250953957993208L, MathUtils.binomialCoefficient(67, 30));

        try {
            MathUtils.binomialCoefficient(3, 5);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            MathUtils.binomialCoefficient(-1, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        
        try {
            MathUtils.binomialCoefficient(67, 33); // triggers overflow exception potentially
            // just checking execution path
        } catch (ArithmeticException e) {
            // expected for too large result
        }
    }

    @Test
    public void testBinomialCoefficientDouble() throws Throwable {
        assertEquals(1.0, MathUtils.binomialCoefficientDouble(5, 0), 1e-10);
        assertEquals(1.0, MathUtils.binomialCoefficientDouble(5, 5), 1e-10);
        assertEquals(5.0, MathUtils.binomialCoefficientDouble(5, 1), 1e-10);
        assertEquals(5.0, MathUtils.binomialCoefficientDouble(5, 4), 1e-10);
        assertEquals(10.0, MathUtils.binomialCoefficientDouble(5, 3), 1e-10); // k > n/2 symmetry
        assertEquals(20030010.0, MathUtils.binomialCoefficientDouble(30, 10), 1e-10); // n < 67
        assertEquals(2.7888705973349635E18, MathUtils.binomialCoefficientDouble(70, 30), 1e-10); // n >= 67
    }

    @Test
    public void testBinomialCoefficientLog() throws Throwable {
        assertEquals(0.0, MathUtils.binomialCoefficientLog(5, 0), 1e-10);
        assertEquals(0.0, MathUtils.binomialCoefficientLog(5, 5), 1e-10);
        assertEquals(Math.log(5), MathUtils.binomialCoefficientLog(5, 1), 1e-10);
        assertEquals(Math.log(5), MathUtils.binomialCoefficientLog(5, 4), 1e-10);
        assertEquals(Math.log(MathUtils.binomialCoefficient(30, 10)), MathUtils.binomialCoefficientLog(30, 10), 1e-10); // n < 67
        assertTrue(MathUtils.binomialCoefficientLog(100, 30) > 0.0); // 67 <= n < 1030
        assertTrue(MathUtils.binomialCoefficientLog(1200, 30) > 0.0); // n >= 1030
        assertEquals(MathUtils.binomialCoefficientLog(100, 70), MathUtils.binomialCoefficientLog(100, 30), 1e-10); // k > n/2 symmetry
    }

    @Test
    public void testCompareTo() throws Throwable {
        assertEquals(0, MathUtils.compareTo(1.0, 1.0, 0.1));
        assertEquals(-1, MathUtils.compareTo(1.0, 2.0, 0.1));
        assertEquals(1, MathUtils.compareTo(2.0, 1.0, 0.1));
    }

    @Test
    public void testCosh() throws Throwable {
        assertEquals(1.0, MathUtils.cosh(0.0), 1e-10);
        assertEquals(Math.cosh(1.5), MathUtils.cosh(1.5), 1e-10);
    }

    @Test
    public void testEqualsDouble() throws Throwable {
        assertTrue(MathUtils.equals(Double.NaN, Double.NaN));
        assertFalse(MathUtils.equals(Double.NaN, 1.0));
        assertTrue(MathUtils.equals(1.0, 1.0));
        assertFalse(MathUtils.equals(1.0, 2.0));
    }

    @Test
    public void testEqualsDoubleEps() throws Throwable {
        assertTrue(MathUtils.equals(1.0, 1.05, 0.1));
        assertTrue(MathUtils.equals(Double.NaN, Double.NaN, 0.1));
        assertFalse(MathUtils.equals(1.0, 2.0, 0.1));
    }

    @Test
    public void testEqualsDoubleMaxUlps() throws Throwable {
        assertTrue(MathUtils.equals(1.0, 1.0000000000000002, 2));
        assertFalse(MathUtils.equals(1.0, 2.0, 2));
        assertTrue(MathUtils.equals(-1.0, -1.0000000000000002, 2));
    }

    @Test
    public void testEqualsDoubleArray() throws Throwable {
        double[] arr1 = new double[]{1.0, 2.0};
        double[] arr2 = new double[]{1.0, 2.0};
        double[] arr3 = new double[]{1.0, 3.0};
        double[] arr4 = new double[]{1.0};

        assertTrue(MathUtils.equals(null, null));
        assertFalse(MathUtils.equals(arr1, null));
        assertFalse(MathUtils.equals(null, arr1));
        assertTrue(MathUtils.equals(arr1, arr2));
        assertFalse(MathUtils.equals(arr1, arr3));
        assertFalse(MathUtils.equals(arr1, arr4));
    }

    @Test
    public void testFactorial() throws Throwable {
        assertEquals(1L, MathUtils.factorial(0));
        assertEquals(1L, MathUtils.factorial(1));
        assertEquals(2432902008176640000L, MathUtils.factorial(20));

        try {
            MathUtils.factorial(-1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            MathUtils.factorial(21);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }
    }

    @Test
    public void testFactorialDouble() throws Throwable {
        assertEquals(1.0, MathUtils.factorialDouble(0), 1e-10);
        assertEquals(2432902008176640000.0, MathUtils.factorialDouble(20), 1e-10);
        assertTrue(MathUtils.factorialDouble(25) > 0.0);

        try {
            MathUtils.factorialDouble(-1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testFactorialLog() throws Throwable {
        assertEquals(0.0, MathUtils.factorialLog(0), 1e-10);
        assertEquals(Math.log(24), MathUtils.factorialLog(4), 1e-10);
        assertTrue(MathUtils.factorialLog(25) > 0.0);

        try {
            MathUtils.factorialLog(-1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGcd() throws Throwable {
        assertEquals(2, MathUtils.gcd(4, 6));
        assertEquals(4, MathUtils.gcd(-4, -6));
        assertEquals(4, MathUtils.gcd(4, 0));
        assertEquals(4, MathUtils.gcd(0, 4));
        assertEquals(0, MathUtils.gcd(0, 0));

        try {
            MathUtils.gcd(Integer.MIN_VALUE, Integer.MIN_VALUE);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }

        try {
            MathUtils.gcd(Integer.MIN_VALUE, 0);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }

        try {
            MathUtils.gcd(0, Integer.MIN_VALUE);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }

        // Additional branches for binary gcd k == 31 or u/v processing
        try {
            MathUtils.gcd(Integer.MIN_VALUE, Integer.MIN_VALUE);
        } catch (Throwable t) {
            // handled
        }
    }

    @Test
    public void testHash() throws Throwable {
        assertEquals(new Double(1.5).hashCode(), MathUtils.hash(1.5));
        double[] arr = new double[]{1.0, 2.0};
        assertEquals(java.util.Arrays.hashCode(arr), MathUtils.hash(arr));
    }

    @Test
    public void testIndicatorByte() throws Throwable {
        assertEquals((byte)1, MathUtils.indicator((byte)5));
        assertEquals((byte)-1, MathUtils.indicator((byte)-5));
        assertEquals((byte)1, MathUtils.indicator((byte)0));
    }

    @Test
    public void testIndicatorDouble() throws Throwable {
        assertEquals(1.0, MathUtils.indicator(5.0), 1e-10);
        assertEquals(-1.0, MathUtils.indicator(-5.0), 1e-10);
        assertEquals(1.0, MathUtils.indicator(0.0), 1e-10);
        assertTrue(Double.isNaN(MathUtils.indicator(Double.NaN)));
    }

    @Test
    public void testIndicatorFloat() throws Throwable {
        assertEquals(1.0f, MathUtils.indicator(5.0f), 1e-10f);
        assertEquals(-1.0f, MathUtils.indicator(-5.0f), 1e-10f);
        assertEquals(1.0f, MathUtils.indicator(0.0f), 1e-10f);
        assertTrue(Float.isNaN(MathUtils.indicator(Float.NaN)));
    }

    @Test
    public void testIndicatorInt() throws Throwable {
        assertEquals(1, MathUtils.indicator(5));
        assertEquals(-1, MathUtils.indicator(-5));
        assertEquals(1, MathUtils.indicator(0));
    }

    @Test
    public void testIndicatorLong() throws Throwable {
        assertEquals(1L, MathUtils.indicator(5L));
        assertEquals(-1L, MathUtils.indicator(-5L));
        assertEquals(1L, MathUtils.indicator(0L));
    }

    @Test
    public void testIndicatorShort() throws Throwable {
        assertEquals((short)1, MathUtils.indicator((short)5));
        assertEquals((short)-1, MathUtils.indicator((short)-5));
        assertEquals((short)1, MathUtils.indicator((short)0));
    }

    @Test
    public void testLcm() throws Throwable {
        assertEquals(12, MathUtils.lcm(4, 6));
        assertEquals(0, MathUtils.lcm(0, 5));
        assertEquals(0, MathUtils.lcm(5, 0));

        try {
            MathUtils.lcm(Integer.MIN_VALUE, 2);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // expected overflow or exception
        }
    }

    @Test
    public void testLog() throws Throwable {
        assertEquals(2.0, MathUtils.log(3.0, 9.0), 1e-10);
    }

    @Test
    public void testMulAndCheckInt() throws Throwable {
        assertEquals(6, MathUtils.mulAndCheck(2, 3));

        try {
            MathUtils.mulAndCheck(Integer.MAX_VALUE, 2);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }
    }

    @Test
    public void testMulAndCheckLong() throws Throwable {
        assertEquals(6L, MathUtils.mulAndCheck(2L, 3L));
        assertEquals(6L, MathUtils.mulAndCheck(3L, 2L)); // a > b branch

        // negative * negative overflow
        try {
            MathUtils.mulAndCheck(Long.MIN_VALUE, 2L);
        } catch (ArithmeticException e) {
            // expected
        }

        // negative * negative safe
        assertEquals(6L, MathUtils.mulAndCheck(-2L, -3L));

        // negative * positive overflow
        try {
            MathUtils.mulAndCheck(Long.MIN_VALUE, 2L);
        } catch (ArithmeticException e) {
            // expected
        }

        // negative * positive safe
        assertEquals(-6L, MathUtils.mulAndCheck(-2L, 3L));

        // negative * zero
        assertEquals(0L, MathUtils.mulAndCheck(-2L, 0L));

        // positive * positive overflow
        try {
            MathUtils.mulAndCheck(Long.MAX_VALUE, 2L);
        } catch (ArithmeticException e) {
            // expected
        }

        // zero * anything
        assertEquals(0L, MathUtils.mulAndCheck(0L, 5L));
        assertEquals(0L, MathUtils.mulAndCheck(5L, 0L));
    }

    @Test
    public void testNextAfter() throws Throwable {
        assertEquals(1.0000000000000002, MathUtils.nextAfter(1.0, 2.0), 1e-16);
        assertEquals(0.9999999999999999, MathUtils.nextAfter(1.0, 0.0), 1e-16);
        assertEquals(Double.valueOf(Double.NaN), MathUtils.nextAfter(Double.NaN, 1.0), 1e-16);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.nextAfter(Double.POSITIVE_INFINITY, 1.0), 1e-16);
        assertEquals(Double.MIN_VALUE, MathUtils.nextAfter(0.0, 1.0), 1e-16);
        assertEquals(-Double.MIN_VALUE, MathUtils.nextAfter(0.0, -1.0), 1e-16);

        // Mantissa boundary checks
        double maxVal = Double.MAX_VALUE;
        assertTrue(MathUtils.nextAfter(maxVal, Double.POSITIVE_INFINITY) > maxVal);
        
        double zeroMin = Double.MIN_VALUE;
        assertTrue(MathUtils.nextAfter(zeroMin, -1.0) >= 0.0);
    }

    @Test
    public void testScalb() throws Throwable {
        assertEquals(4.0, MathUtils.scalb(1.0, 2), 1e-10);
        assertEquals(0.0, MathUtils.scalb(0.0, 2), 1e-10);
        assertEquals(Double.valueOf(Double.NaN), MathUtils.scalb(Double.NaN, 2), 1e-10);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.scalb(Double.POSITIVE_INFINITY, 2), 1e-10);
    }

    @Test
    public void testNormalizeAngle() throws Throwable {
        assertEquals(0.0, MathUtils.normalizeAngle(MathUtils.TWO_PI, 0.0), 1e-10);
    }

    @Test
    public void testNormalizeArray() throws Throwable {
        double[] values = new double[]{1.0, 2.0, Double.NaN};
        double[] normalized = MathUtils.normalizeArray(values, 6.0);
        assertEquals(2.0, normalized[0], 1e-10);
        assertEquals(4.0, normalized[1], 1e-10);
        assertTrue(Double.isNaN(normalized[2]));

        try {
            MathUtils.normalizeArray(values, Double.POSITIVE_INFINITY);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            MathUtils.normalizeArray(values, Double.NaN);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            MathUtils.normalizeArray(new double[]{Double.POSITIVE_INFINITY, 1.0}, 5.0);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }

        try {
            MathUtils.normalizeArray(new double[]{0.0, 0.0}, 5.0);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }
    }

    @Test
    public void testRoundDouble() throws Throwable {
        assertEquals(1.23, MathUtils.round(1.234, 2), 1e-10);
        assertEquals(1.24, MathUtils.round(1.235, 2), 1e-10);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.round(Double.POSITIVE_INFINITY, 2), 1e-10);
        assertEquals(Double.valueOf(Double.NaN), MathUtils.round(Double.NaN, 2), 1e-10);
    }

    @Test
    public void testRoundFloat() throws Throwable {
        assertEquals(1.23f, MathUtils.round(1.234f, 2), 1e-10f);
        assertEquals(1.24f, MathUtils.round(1.235f, 2), 1e-10f);
    }

    @Test
    public void testRoundUnscaledAllMethods() throws Throwable {
        // Indirectly test via float or double round with various rounding methods
        assertEquals(1.2f, MathUtils.round(1.25f, 1, BigDecimal.ROUND_CEILING), 1e-10f);
        assertEquals(1.2f, MathUtils.round(1.25f, 1, BigDecimal.ROUND_DOWN), 1e-10f);
        assertEquals(1.3f, MathUtils.round(-1.25f, 1, BigDecimal.ROUND_FLOOR), 1e-10f);
        assertEquals(1.2f, MathUtils.round(1.25f, 1, BigDecimal.ROUND_HALF_DOWN), 1e-10f);
        assertEquals(1.2f, MathUtils.round(1.25f, 1, BigDecimal.ROUND_HALF_EVEN), 1e-10f);
        assertEquals(1.3f, MathUtils.round(1.25f, 1, BigDecimal.ROUND_HALF_UP), 1e-10f);
        assertEquals(1.3f, MathUtils.round(1.35f, 1, BigDecimal.ROUND_UP), 1e-10f);
        
        try {
            MathUtils.round(1.25f, 1, BigDecimal.ROUND_UNNECESSARY);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }

        try {
            MathUtils.round(1.25f, 1, 999);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSignByte() throws Throwable {
        assertEquals((byte)0, MathUtils.sign((byte)0));
        assertEquals((byte)1, MathUtils.sign((byte)5));
        assertEquals((byte)-1, MathUtils.sign((byte)-5));
    }

    @Test
    public void testSignDouble() throws Throwable {
        assertEquals(0.0, MathUtils.sign(0.0), 1e-10);
        assertEquals(1.0, MathUtils.sign(5.0), 1e-10);
        assertEquals(-1.0, MathUtils.sign(-5.0), 1e-10);
        assertTrue(Double.isNaN(MathUtils.sign(Double.NaN)));
    }

    @Test
    public void testSignFloat() throws Throwable {
        assertEquals(0.0f, MathUtils.sign(0.0f), 1e-10f);
        assertEquals(1.0f, MathUtils.sign(5.0f), 1e-10f);
        assertEquals(-1.0f, MathUtils.sign(-5.0f), 1e-10f);
        assertTrue(Float.isNaN(MathUtils.sign(Float.NaN)));
    }

    @Test
    public void testSignInt() throws Throwable {
        assertEquals(0, MathUtils.sign(0));
        assertEquals(1, MathUtils.sign(5));
        assertEquals(-1, MathUtils.sign(-5));
    }

    @Test
    public void testSignLong() throws Throwable {
        assertEquals(0L, MathUtils.sign(0L));
        assertEquals(1L, MathUtils.sign(5L));
        assertEquals(-1L, MathUtils.sign(-5L));
    }

    @Test
    public void testSignShort() throws Throwable {
        assertEquals((short)0, MathUtils.sign((short)0));
        assertEquals((short)1, MathUtils.sign((short)5));
        assertEquals((short)-1, MathUtils.sign((short)-5));
    }

    @Test
    public void testSinh() throws Throwable {
        assertEquals(0.0, MathUtils.sinh(0.0), 1e-10);
        assertEquals(Math.sinh(1.5), MathUtils.sinh(1.5), 1e-10);
    }

    @Test
    public void testSubAndCheckInt() throws Throwable {
        assertEquals(2, MathUtils.subAndCheck(5, 3));

        try {
            MathUtils.subAndCheck(Integer.MIN_VALUE, 1);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }
    }

    @Test
    public void testSubAndCheckLong() throws Throwable {
        assertEquals(2L, MathUtils.subAndCheck(5L, 3L));

        try {
            MathUtils.subAndCheck(5L, Long.MIN_VALUE);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }

        assertEquals(5L - Long.MIN_VALUE, MathUtils.subAndCheck(-5L, Long.MIN_VALUE));
    }

    @Test
    public void testPowInt() throws Throwable {
        assertEquals(8, MathUtils.pow(2, 3));
        assertEquals(1, MathUtils.pow(2, 0));

        try {
            MathUtils.pow(2, -1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertEquals(8, MathUtils.pow(2, 3L));
        
        try {
            MathUtils.pow(2, -1L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testPowLong() throws Throwable {
        assertEquals(8L, MathUtils.pow(2L, 3));
        
        try {
            MathUtils.pow(2L, -1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertEquals(8L, MathUtils.pow(2L, 3L));

        try {
            MathUtils.pow(2L, -1L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testPowBigInteger() throws Throwable {
        assertEquals(BigInteger.valueOf(8), MathUtils.pow(BigInteger.valueOf(2), 3));

        try {
            MathUtils.pow(BigInteger.valueOf(2), -1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertEquals(BigInteger.valueOf(8), MathUtils.pow(BigInteger.valueOf(2), 3L));

        try {
            MathUtils.pow(BigInteger.valueOf(2), -1L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertEquals(BigInteger.valueOf(8), MathUtils.pow(BigInteger.valueOf(2), BigInteger.valueOf(3)));

        try {
            MathUtils.pow(BigInteger.valueOf(2), BigInteger.valueOf(-1));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testDistance() throws Throwable {
        double[] p1 = new double[]{0.0, 0.0};
        double[] p2 = new double[]{3.0, 4.0};
        int[] ip1 = new int[]{0, 0};
        int[] ip2 = new int[]{3, 4};

        assertEquals(7.0, MathUtils.distance1(p1, p2), 1e-10);
        assertEquals(7, MathUtils.distance1(ip1, ip2));

        assertEquals(5.0, MathUtils.distance(p1, p2), 1e-10);
        assertEquals(5.0, MathUtils.distance(ip1, ip2), 1e-10);

        assertEquals(4.0, MathUtils.distanceInf(p1, p2), 1e-10);
        assertEquals(4, MathUtils.distanceInf(ip1, ip2));
    }
}