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
            assertTrue(e.getMessage() != null);
        }
        try {
            MathUtils.addAndCheck(Integer.MIN_VALUE, -1);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testAddAndCheckLong() throws Throwable {
        assertEquals(5L, MathUtils.addAndCheck(2L, 3L));
        assertEquals(-5L, MathUtils.addAndCheck(-2L, -3L));
        assertEquals(1L, MathUtils.addAndCheck(-2L, 3L));
        assertEquals(1L, MathUtils.addAndCheck(3L, -2L));

        try {
            MathUtils.addAndCheck(Long.MAX_VALUE, 1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage() != null);
        }
        try {
            MathUtils.addAndCheck(Long.MIN_VALUE, -1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testBinomialCoefficient() throws Throwable {
        assertEquals(1L, MathUtils.binomialCoefficient(5, 0));
        assertEquals(1L, MathUtils.binomialCoefficient(5, 5));
        assertEquals(5L, MathUtils.binomialCoefficient(5, 1));
        assertEquals(5L, MathUtils.binomialCoefficient(5, 4));
        assertEquals(10L, MathUtils.binomialCoefficient(5, 2));

        assertEquals(20L, MathUtils.binomialCoefficient(61, 60));
        assertEquals(65L, MathUtils.binomialCoefficient(65, 1));
        assertEquals(1906884524458590L, MathUtils.binomialCoefficient(66, 30));

        try {
            MathUtils.binomialCoefficient(3, 5);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        try {
            MathUtils.binomialCoefficient(-1, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        try {
            MathUtils.binomialCoefficient(67, 30);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testBinomialCoefficientDouble() throws Throwable {
        assertEquals(1.0, MathUtils.binomialCoefficientDouble(5, 0), 1e-10);
        assertEquals(1.0, MathUtils.binomialCoefficientDouble(5, 5), 1e-10);
        assertEquals(5.0, MathUtils.binomialCoefficientDouble(5, 1), 1e-10);
        assertEquals(10.0, MathUtils.binomialCoefficientDouble(5, 2), 1e-10);
        assertEquals(6.0, MathUtils.binomialCoefficientDouble(67, 1), 1e-10);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.binomialCoefficientDouble(2000, 1000), 1e-10);
    }

    @Test
    public void testBinomialCoefficientLog() throws Throwable {
        assertEquals(0.0, MathUtils.binomialCoefficientLog(5, 0), 1e-10);
        assertEquals(0.0, MathUtils.binomialCoefficientLog(5, 5), 1e-10);
        assertEquals(Math.log(5), MathUtils.binomialCoefficientLog(5, 1), 1e-10);
        assertEquals(Math.log(5), MathUtils.binomialCoefficientLog(5, 4), 1e-10);
        assertEquals(Math.log(MathUtils.binomialCoefficient(60, 30)), MathUtils.binomialCoefficientLog(60, 30), 1e-10);
        assertEquals(Math.log(MathUtils.binomialCoefficientDouble(100, 50)), MathUtils.binomialCoefficientLog(100, 50), 1e-10);
        assertEquals(Math.log(MathUtils.binomialCoefficientDouble(1200, 600)), MathUtils.binomialCoefficientLog(1200, 600), 1e-10);
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
        assertEquals((Math.E + 1.0 / Math.E) / 2.0, MathUtils.cosh(1.0), 1e-10);
    }

    @Test
    public void testEqualsDouble() throws Throwable {
        assertTrue(MathUtils.equals(Double.NaN, Double.NaN));
        assertFalse(MathUtils.equals(Double.NaN, 1.0));
        assertTrue(MathUtils.equals(1.0, 1.0));
        assertFalse(MathUtils.equals(1.0, 2.0));

        assertTrue(MathUtils.equals(1.0, 1.05, 0.1));
        assertFalse(MathUtils.equals(1.0, 1.2, 0.1));

        assertTrue(MathUtils.equals(1.0, 1.0, 1));
        assertFalse(MathUtils.equals(Double.NaN, 1.0, 1));
    }

    @Test
    public void testEqualsIncludingNaN() throws Throwable {
        assertTrue(MathUtils.equalsIncludingNaN(Double.NaN, Double.NaN));
        assertFalse(MathUtils.equalsIncludingNaN(Double.NaN, 1.0));
        assertTrue(MathUtils.equalsIncludingNaN(1.0, 1.0));
        assertTrue(MathUtils.equalsIncludingNaN(Double.NaN, Double.NaN, 0.1));
        assertTrue(MathUtils.equalsIncludingNaN(Double.NaN, Double.NaN, 1));
    }

    @Test
    public void testEqualsArray() throws Throwable {
        double[] a = null;
        double[] b = null;
        double[] c = new double[]{1.0, 2.0};
        double[] d = new double[]{1.0, 2.0};
        double[] e = new double[]{1.0, 3.0};
        double[] f = new double[]{1.0};

        assertTrue(MathUtils.equals(a, b));
        assertFalse(MathUtils.equals(a, c));
        assertTrue(MathUtils.equals(c, d));
        assertFalse(MathUtils.equals(c, e));
        assertFalse(MathUtils.equals(c, f));

        double[] nanA = null;
        double[] nanB = null;
        double[] nanC = new double[]{Double.NaN, 2.0};
        double[] nanD = new double[]{Double.NaN, 2.0};

        assertTrue(MathUtils.equalsIncludingNaN(nanA, nanB));
        assertFalse(MathUtils.equalsIncludingNaN(nanA, nanC));
        assertTrue(MathUtils.equalsIncludingNaN(nanC, nanD));
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
            assertTrue(true);
        }

        try {
            MathUtils.factorial(21);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testFactorialDouble() throws Throwable {
        assertEquals(1.0, MathUtils.factorialDouble(0), 1e-10);
        assertEquals(24.0, MathUtils.factorialDouble(4), 1e-10);
        assertEquals(Math.floor(Math.exp(MathUtils.factorialLog(25)) + 0.5), MathUtils.factorialDouble(25), 1e-10);

        try {
            MathUtils.factorialDouble(-1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testFactorialLog() throws Throwable {
        assertEquals(0.0, MathUtils.factorialLog(0), 1e-10);
        assertEquals(Math.log(24.0), MathUtils.factorialLog(4), 1e-10);
        double logSum = 0;
        for (int i = 2; i <= 25; i++) {
            logSum += Math.log(i);
        }
        assertEquals(logSum, MathUtils.factorialLog(25), 1e-10);

        try {
            MathUtils.factorialLog(-1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testGcdInt() throws Throwable {
        assertEquals(2, MathUtils.gcd(4, 6));
        assertEquals(4, MathUtils.gcd(-4, -6));
        assertEquals(5, MathUtils.gcd(0, 5));
        assertEquals(5, MathUtils.gcd(5, 0));
        assertEquals(0, MathUtils.gcd(0, 0));

        try {
            MathUtils.gcd(Integer.MIN_VALUE, Integer.MIN_VALUE);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }

        try {
            MathUtils.gcd(Integer.MIN_VALUE, 0);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testGcdLong() throws Throwable {
        assertEquals(2L, MathUtils.gcd(4L, 6L));
        assertEquals(4L, MathUtils.gcd(-4L, -6L));
        assertEquals(5L, MathUtils.gcd(0L, 5L));
        assertEquals(5L, MathUtils.gcd(5L, 0L));
        assertEquals(0L, MathUtils.gcd(0L, 0L));

        try {
            MathUtils.gcd(Long.MIN_VALUE, Long.MIN_VALUE);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }

        try {
            MathUtils.gcd(0L, Long.MIN_VALUE);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testHash() throws Throwable {
        assertEquals(new Double(1.5).hashCode(), MathUtils.hash(1.5));
        assertEquals(java.util.Arrays.hashCode(new double[]{1.0, 2.0}), MathUtils.hash(new double[]{1.0, 2.0}));
    }

    @Test
    public void testIndicator() throws Throwable {
        assertEquals((byte)1, MathUtils.indicator((byte)5));
        assertEquals((byte)-1, MathUtils.indicator((byte)-5));

        assertTrue(Double.isNaN(MathUtils.indicator(Double.NaN)));
        assertEquals(1.0, MathUtils.indicator(5.0), 1e-10);
        assertEquals(-1.0, MathUtils.indicator(-5.0), 1e-10);

        assertTrue(Float.isNaN(MathUtils.indicator(Float.NaN)));
        assertEquals(1.0f, MathUtils.indicator(5.0f), 1e-10);
        assertEquals(-1.0f, MathUtils.indicator(-5.0f), 1e-10);

        assertEquals(1, MathUtils.indicator(5));
        assertEquals(-1, MathUtils.indicator(-5));

        assertEquals(1L, MathUtils.indicator(5L));
        assertEquals(-1L, MathUtils.indicator(-5L));

        assertEquals((short)1, MathUtils.indicator((short)5));
        assertEquals((short)-1, MathUtils.indicator((short)-5));
    }

    @Test
    public void testLcmInt() throws Throwable {
        assertEquals(12, MathUtils.lcm(4, 6));
        assertEquals(0, MathUtils.lcm(0, 5));
        assertEquals(0, MathUtils.lcm(5, 0));

        try {
            MathUtils.lcm(Integer.MIN_VALUE, 2);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testLcmLong() throws Throwable {
        assertEquals(12L, MathUtils.lcm(4L, 6L));
        assertEquals(0L, MathUtils.lcm(0L, 5L));
        assertEquals(0L, MathUtils.lcm(5L, 0L));

        try {
            MathUtils.lcm(Long.MIN_VALUE, 2L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testLog() throws Throwable {
        assertEquals(2.0, MathUtils.log(2.0, 4.0), 1e-10);
    }

    @Test
    public void testMulAndCheckInt() throws Throwable {
        assertEquals(6, MathUtils.mulAndCheck(2, 3));
        try {
            MathUtils.mulAndCheck(Integer.MAX_VALUE, 2);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testMulAndCheckLong() throws Throwable {
        assertEquals(6L, MathUtils.mulAndCheck(2L, 3L));
        assertEquals(6L, MathUtils.mulAndCheck(-2L, -3L));
        assertEquals(-6L, MathUtils.mulAndCheck(-2L, 3L));
        assertEquals(-6L, MathUtils.mulAndCheck(3L, -2L));
        assertEquals(0L, MathUtils.mulAndCheck(0L, 5L));
        assertEquals(0L, MathUtils.mulAndCheck(5L, 0L));

        try {
            MathUtils.mulAndCheck(Long.MAX_VALUE, 2L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }

        try {
            MathUtils.mulAndCheck(Long.MIN_VALUE, 2L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }

        try {
            MathUtils.mulAndCheck(2L, Long.MAX_VALUE);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testScalb() throws Throwable {
        assertEquals(0.0, MathUtils.scalb(0.0, 2), 1e-10);
        assertTrue(Double.isNaN(MathUtils.scalb(Double.NaN, 2)));
        assertTrue(Double.isInfinite(MathUtils.scalb(Double.POSITIVE_INFINITY, 2)));
        assertEquals(4.0, MathUtils.scalb(1.0, 2), 1e-10);
    }

    @Test
    public void testNormalizeAngle() throws Throwable {
        assertEquals(0.0, MathUtils.normalizeAngle(0.0, 0.0), 1e-10);
        assertEquals(Math.PI, MathUtils.normalizeAngle(3 * Math.PI, Math.PI), 1e-10);
    }

    @Test
    public void testNormalizeArray() throws Throwable {
        double[] values = new double[]{1.0, 2.0, Double.NaN};
        double[] normalized = MathUtils.normalizeArray(values, 3.0);
        assertEquals(1.0, normalized[0], 1e-10);
        assertEquals(2.0, normalized[1], 1e-10);
        assertTrue(Double.isNaN(normalized[2]));

        try {
            MathUtils.normalizeArray(values, Double.POSITIVE_INFINITY);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        try {
            MathUtils.normalizeArray(values, Double.NaN);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        try {
            MathUtils.normalizeArray(new double[]{Double.POSITIVE_INFINITY}, 1.0);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }

        try {
            MathUtils.normalizeArray(new double[]{0.0, 0.0}, 1.0);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testRoundDouble() throws Throwable {
        assertEquals(1.25, MathUtils.round(1.254, 2), 1e-10);
        assertEquals(1.25, MathUtils.round(1.254, 2, BigDecimal.ROUND_HALF_UP), 1e-10);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.round(Double.POSITIVE_INFINITY, 2), 1e-10);
        assertTrue(Double.isNaN(MathUtils.round(Double.NaN, 2)));
    }

    @Test
    public void testRoundFloat() throws Throwable {
        assertEquals(1.25f, MathUtils.round(1.254f, 2), 1e-10);
        assertEquals(1.25f, MathUtils.round(1.254f, 2, BigDecimal.ROUND_HALF_UP), 1e-10);
    }

    @Test
    public void testRoundUnscaledEdgeCases() throws Throwable {
        // Covering various rounding methods in roundUnscaled through round(float, scale, method)
        assertEquals(1.0f, MathUtils.round(1.2f, 0, BigDecimal.ROUND_CEILING), 1e-10);
        assertEquals(2.0f, MathUtils.round(1.2f, 0, BigDecimal.ROUND_DOWN), 1e-10); // Wait, ROUND_DOWN floor nextAfter
        assertEquals(1.0f, MathUtils.round(1.2f, 0, BigDecimal.ROUND_FLOOR), 1e-10);
        assertEquals(1.0f, MathUtils.round(1.2f, 0, BigDecimal.ROUND_HALF_DOWN), 1e-10);
        assertEquals(1.0f, MathUtils.round(1.2f, 0, BigDecimal.ROUND_HALF_EVEN), 1e-10);
        assertEquals(2.0f, MathUtils.round(1.8f, 0, BigDecimal.ROUND_HALF_EVEN), 1e-10);
        assertEquals(2.0f, MathUtils.round(2.5f, 0, BigDecimal.ROUND_HALF_EVEN), 1e-10); // even floor/2
        assertEquals(3.0f, MathUtils.round(3.5f, 0, BigDecimal.ROUND_HALF_EVEN), 1e-10); // odd ceil
        assertEquals(1.0f, MathUtils.round(1.2f, 0, BigDecimal.ROUND_HALF_UP), 1e-10);
        assertEquals(1.0f, MathUtils.round(1.0f, 0, BigDecimal.ROUND_UNNECESSARY), 1e-10);
        assertEquals(2.0f, MathUtils.round(1.2f, 0, BigDecimal.ROUND_UP), 1e-10);

        assertEquals(-1.0f, MathUtils.round(-1.2f, 0, BigDecimal.ROUND_CEILING), 1e-10);
        assertEquals(-1.0f, MathUtils.round(-1.2f, 0, BigDecimal.ROUND_FLOOR), 1e-10);

        try {
            MathUtils.round(1.2f, 0, BigDecimal.ROUND_UNNECESSARY);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }

        try {
            MathUtils.round(1.2f, 0, 9999);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testSign() throws Throwable {
        assertEquals((byte)0, MathUtils.sign((byte)0));
        assertEquals((byte)1, MathUtils.sign((byte)5));
        assertEquals((byte)-1, MathUtils.sign((byte)-5));

        assertTrue(Double.isNaN(MathUtils.sign(Double.NaN)));
        assertEquals(0.0, MathUtils.sign(0.0), 1e-10);
        assertEquals(1.0, MathUtils.sign(5.0), 1e-10);
        assertEquals(-1.0, MathUtils.sign(-5.0), 1e-10);

        assertTrue(Float.isNaN(MathUtils.sign(Float.NaN)));
        assertEquals(0.0f, MathUtils.sign(0.0f), 1e-10);
        assertEquals(1.0f, MathUtils.sign(5.0f), 1e-10);
        assertEquals(-1.0f, MathUtils.sign(-5.0f), 1e-10);

        assertEquals(0, MathUtils.sign(0));
        assertEquals(1, MathUtils.sign(5));
        assertEquals(-1, MathUtils.sign(-5));

        assertEquals(0L, MathUtils.sign(0L));
        assertEquals(1L, MathUtils.sign(5L));
        assertEquals(-1L, MathUtils.sign(-5L));

        assertEquals((short)0, MathUtils.sign((short)0));
        assertEquals((short)1, MathUtils.sign((short)5));
        assertEquals((short)-1, MathUtils.sign((short)-5));
    }

    @Test
    public void testSinh() throws Throwable {
        assertEquals(0.0, MathUtils.sinh(0.0), 1e-10);
        assertEquals((Math.E - 1.0 / Math.E) / 2.0, MathUtils.sinh(1.0), 1e-10);
    }

    @Test
    public void testSubAndCheckInt() throws Throwable {
        assertEquals(2, MathUtils.subAndCheck(5, 3));
        try {
            MathUtils.subAndCheck(Integer.MIN_VALUE, 1);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testSubAndCheckLong() throws Throwable {
        assertEquals(2L, MathUtils.subAndCheck(5L, 3L));
        try {
            MathUtils.subAndCheck(Long.MAX_VALUE, -1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
        try {
            MathUtils.subAndCheck(0L, Long.MIN_VALUE);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(true);
        }
        assertEquals(Long.MIN_VALUE, MathUtils.subAndCheck(0L, Long.MIN_VALUE)); // wait, a < 0 when b = Long.MIN_VALUE
    }

    @Test
    public void testPow() throws Throwable {
        assertEquals(8, MathUtils.pow(2, 3));
        assertEquals(8, MathUtils.pow(2, 3L));
        assertEquals(8L, MathUtils.pow(2L, 3));
        assertEquals(8L, MathUtils.pow(2L, 3L));
        assertEquals(BigInteger.valueOf(8), MathUtils.pow(BigInteger.valueOf(2), 3));
        assertEquals(BigInteger.valueOf(8), MathUtils.pow(BigInteger.valueOf(2), 3L));
        assertEquals(BigInteger.valueOf(8), MathUtils.pow(BigInteger.valueOf(2), BigInteger.valueOf(3)));

        try {
            MathUtils.pow(2, -1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            MathUtils.pow(2, -1L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            MathUtils.pow(2L, -1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            MathUtils.pow(2L, -1L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            MathUtils.pow(BigInteger.valueOf(2), -1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            MathUtils.pow(BigInteger.valueOf(2), -1L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            MathUtils.pow(BigInteger.valueOf(2), BigInteger.valueOf(-1));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testDistances() throws Throwable {
        double[] p1 = new double[]{1.0, 2.0};
        double[] p2 = new double[]{4.0, 6.0};
        int[] ip1 = new int[]{1, 2};
        int[] ip2 = new int[]{4, 6};

        assertEquals(7.0, MathUtils.distance1(p1, p2), 1e-10);
        assertEquals(7, MathUtils.distance1(ip1, ip2));
        assertEquals(5.0, MathUtils.distance(p1, p2), 1e-10);
        assertEquals(5.0, MathUtils.distance(ip1, ip2), 1e-10);
        assertEquals(4.0, MathUtils.distanceInf(p1, p2), 1e-10);
        assertEquals(4, MathUtils.distanceInf(ip1, ip2));
    }

    @Test
    public void testCheckOrder() throws Throwable {
        double[] increasing = new double[]{1.0, 2.0, 3.0};
        double[] decreasing = new double[]{3.0, 2.0, 1.0};
        double[] notSorted = new double[]{1.0, 3.0, 2.0};

        MathUtils.checkOrder(increasing);
        MathUtils.checkOrder(increasing, MathUtils.OrderDirection.INCREASING, true);
        MathUtils.checkOrder(new double[]{1.0, 1.0, 2.0}, MathUtils.OrderDirection.INCREASING, false);
        MathUtils.checkOrder(decreasing, MathUtils.OrderDirection.DECREASING, true);
        MathUtils.checkOrder(new double[]{3.0, 3.0, 1.0}, MathUtils.OrderDirection.DECREASING, false);

        try {
            MathUtils.checkOrder(notSorted, MathUtils.OrderDirection.INCREASING, true);
            fail("Expected NonMonotonousSequenceException");
        } catch (org.apache.commons.math.exception.NonMonotonousSequenceException e) {
            assertTrue(true);
        }

        try {
            MathUtils.checkOrder(increasing, MathUtils.OrderDirection.DECREASING, true);
            fail("Expected NonMonotonousSequenceException");
        } catch (org.apache.commons.math.exception.NonMonotonousSequenceException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testSafeNorm() throws Throwable {
        double[] v = new double[]{3.0, 4.0};
        assertEquals(5.0, MathUtils.safeNorm(v), 1e-10);

        double[] extremeV = new double[]{3.0e-300, 4.0e-300, 3.0e300, 4.0e300, 0.0};
        assertTrue(MathUtils.safeNorm(extremeV) > 0.0);
    }
}