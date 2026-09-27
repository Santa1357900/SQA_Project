package org.apache.commons.math.util;

import java.math.BigDecimal;
import java.util.Arrays;

import org.junit.Test;
import static org.junit.Assert.*;

public class MathUtilsTest {

    @Test
    public void testAddAndCheckInt() throws Throwable {
        assertEquals(5, MathUtils.addAndCheck(2, 3));
        assertEquals(Integer.MAX_VALUE, MathUtils.addAndCheck(Integer.MAX_VALUE - 1, 1));
        assertEquals(Integer.MIN_VALUE, MathUtils.addAndCheck(Integer.MIN_VALUE + 1, -1));

        try {
            MathUtils.addAndCheck(Integer.MAX_VALUE, 1);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        try {
            MathUtils.addAndCheck(Integer.MIN_VALUE, -1);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testAddAndCheckLong() throws Throwable {
        assertEquals(5L, MathUtils.addAndCheck(2L, 3L));
        assertEquals(Long.MAX_VALUE, MathUtils.addAndCheck(Long.MAX_VALUE - 1L, 1L));
        assertEquals(Long.MIN_VALUE, MathUtils.addAndCheck(Long.MIN_VALUE + 1L, -1L));

        // Symmetric and negative/positive branches
        assertEquals(-5L, MathUtils.addAndCheck(-2L, -3L));
        assertEquals(0L, MathUtils.addAndCheck(-5L, 5L));
        assertEquals(0L, MathUtils.addAndCheck(5L, -5L));

        try {
            MathUtils.addAndCheck(Long.MAX_VALUE, 1L);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        try {
            MathUtils.addAndCheck(Long.MIN_VALUE, -1L);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        try {
            MathUtils.addAndCheck(-1L, Long.MIN_VALUE);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testBinomialCoefficient() throws Throwable {
        assertEquals(1L, MathUtils.binomialCoefficient(5, 0));
        assertEquals(1L, MathUtils.binomialCoefficient(5, 5));
        assertEquals(5L, MathUtils.binomialCoefficient(5, 1));
        assertEquals(5L, MathUtils.binomialCoefficient(5, 4));
        assertEquals(10L, MathUtils.binomialCoefficient(5, 2));

        // Symmetry branch (k > n / 2)
        assertEquals(10L, MathUtils.binomialCoefficient(5, 3));

        // n <= 61 branch
        assertEquals(2598960L, MathUtils.binomialCoefficient(52, 5));

        // 61 < n <= 66 branch
        assertEquals(726100748524L, MathUtils.binomialCoefficient(65, 3));

        // n > 66 branch
        assertEquals(905037651774378907L, MathUtils.binomialCoefficient(67, 3));

        try {
            MathUtils.binomialCoefficient(3, 5);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            MathUtils.binomialCoefficient(-1, 0);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            MathUtils.binomialCoefficient(67, 30);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testBinomialCoefficientDouble() throws Throwable {
        assertEquals(1.0, MathUtils.binomialCoefficientDouble(5, 0), 0.0);
        assertEquals(1.0, MathUtils.binomialCoefficientDouble(5, 5), 0.0);
        assertEquals(5.0, MathUtils.binomialCoefficientDouble(5, 1), 0.0);
        assertEquals(5.0, MathUtils.binomialCoefficientDouble(5, 4), 0.0);
        assertEquals(10.0, MathUtils.binomialCoefficientDouble(5, 3), 0.0);

        // n < 67 returns binomialCoefficient
        assertEquals(2598960.0, MathUtils.binomialCoefficientDouble(52, 5), 0.0);

        // n >= 67 branch
        assertEquals(7032997184L, MathUtils.binomialCoefficientDouble(70, 5), 1.0);

        try {
            MathUtils.binomialCoefficientDouble(3, 5);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            MathUtils.binomialCoefficientDouble(-1, 0);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testBinomialCoefficientLog() throws Throwable {
        assertEquals(0.0, MathUtils.binomialCoefficientLog(5, 0), 1e-12);
        assertEquals(0.0, MathUtils.binomialCoefficientLog(5, 5), 1e-12);
        assertEquals(Math.log(5.0), MathUtils.binomialCoefficientLog(5, 1), 1e-12);
        assertEquals(Math.log(5.0), MathUtils.binomialCoefficientLog(5, 4), 1e-12);

        // n < 67
        assertEquals(Math.log(10.0), MathUtils.binomialCoefficientLog(5, 2), 1e-12);

        // 67 <= n < 1030
        assertTrue(MathUtils.binomialCoefficientLog(70, 5) > 0.0);

        // n >= 1030 and k > n / 2 symmetry branch
        assertTrue(MathUtils.binomialCoefficientLog(1100, 1000) > 0.0);
        assertTrue(MathUtils.binomialCoefficientLog(1100, 50) > 0.0);

        try {
            MathUtils.binomialCoefficientLog(3, 5);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            MathUtils.binomialCoefficientLog(-1, 0);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testCosh() throws Throwable {
        assertEquals(1.0, MathUtils.cosh(0.0), 1e-12);
        assertTrue(MathUtils.cosh(1.0) > 1.0);
    }

    @Test
    public void testEqualsDouble() throws Throwable {
        assertTrue(MathUtils.equals(Double.NaN, Double.NaN));
        assertFalse(MathUtils.equals(Double.NaN, 0.0));
        assertFalse(MathUtils.equals(0.0, Double.NaN));
        assertTrue(MathUtils.equals(1.0, 1.0));
        assertFalse(MathUtils.equals(1.0, 2.0));
    }

    @Test
    public void testEqualsDoubleWithEps() throws Throwable {
        assertTrue(MathUtils.equals(1.0, 1.05, 0.1));
        assertTrue(MathUtils.equals(1.05, 1.0, 0.1));
        assertTrue(MathUtils.equals(1.0, 1.0, 0.0));
        assertFalse(MathUtils.equals(1.0, 1.2, 0.1));
        assertFalse(MathUtils.equals(1.2, 1.0, 0.1));
    }

    @Test
    public void testEqualsDoubleArray() throws Throwable {
        assertTrue(MathUtils.equals((double[]) null, (double[]) null));
        assertFalse(MathUtils.equals(new double[] { 1.0 }, (double[]) null));
        assertFalse(MathUtils.equals((double[]) null, new double[] { 1.0 }));
        assertFalse(MathUtils.equals(new double[] { 1.0 }, new double[] { 1.0, 2.0 }));
        assertTrue(MathUtils.equals(new double[] { 1.0, Double.NaN }, new double[] { 1.0, Double.NaN }));
        assertFalse(MathUtils.equals(new double[] { 1.0, 2.0 }, new double[] { 1.0, 3.0 }));
    }

    @Test
    public void testFactorial() throws Throwable {
        assertEquals(1L, MathUtils.factorial(0));
        assertEquals(1L, MathUtils.factorial(1));
        assertEquals(2432902008176640000L, MathUtils.factorial(20));

        try {
            MathUtils.factorial(-1);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            MathUtils.factorial(21);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testFactorialDouble() throws Throwable {
        assertEquals(1.0, MathUtils.factorialDouble(0), 0.0);
        assertEquals(2432902008176640000.0, MathUtils.factorialDouble(20), 0.0);
        assertTrue(MathUtils.factorialDouble(25) > 0.0);

        try {
            MathUtils.factorialDouble(-1);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testFactorialLog() throws Throwable {
        assertEquals(0.0, MathUtils.factorialLog(0), 1e-12);
        assertEquals(Math.log(24.0), MathUtils.factorialLog(4), 1e-12);
        assertTrue(MathUtils.factorialLog(25) > 0.0);

        try {
            MathUtils.factorialLog(-1);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testGcd() throws Throwable {
        assertEquals(5, MathUtils.gcd(5, 0));
        assertEquals(5, MathUtils.gcd(0, 5));
        assertEquals(5, MathUtils.gcd(-5, 0));
        assertEquals(0, MathUtils.gcd(0, 0));
        assertEquals(2, MathUtils.gcd(4, 6));
        assertEquals(2, MathUtils.gcd(-4, 6));
        assertEquals(2, MathUtils.gcd(4, -6));
        assertEquals(2, MathUtils.gcd(-4, -6));

        try {
            MathUtils.gcd(Integer.MIN_VALUE, Integer.MIN_VALUE);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            MathUtils.gcd(Integer.MIN_VALUE, 0);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            MathUtils.gcd(0, Integer.MIN_VALUE);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testHash() throws Throwable {
        assertEquals(new Double(1.5).hashCode(), MathUtils.hash(1.5));
        assertEquals(Arrays.hashCode(new double[] { 1.0, 2.0 }), MathUtils.hash(new double[] { 1.0, 2.0 }));
    }

    @Test
    public void testIndicatorByte() throws Throwable {
        assertEquals((byte) 1, MathUtils.indicator((byte) 5));
        assertEquals((byte) 1, MathUtils.indicator((byte) 0));
        assertEquals((byte) -1, MathUtils.indicator((byte) -5));
    }

    @Test
    public void testIndicatorDouble() throws Throwable {
        assertEquals(1.0, MathUtils.indicator(5.0), 0.0);
        assertEquals(1.0, MathUtils.indicator(0.0), 0.0);
        assertEquals(-1.0, MathUtils.indicator(-5.0), 0.0);
        assertTrue(Double.isNaN(MathUtils.indicator(Double.NaN)));
    }

    @Test
    public void testIndicatorFloat() throws Throwable {
        assertEquals(1.0f, MathUtils.indicator(5.0f), 0.0f);
        assertEquals(1.0f, MathUtils.indicator(0.0f), 0.0f);
        assertEquals(-1.0f, MathUtils.indicator(-5.0f), 0.0f);
        assertTrue(Float.isNaN(MathUtils.indicator(Float.NaN)));
    }

    @Test
    public void testIndicatorInt() throws Throwable {
        assertEquals(1, MathUtils.indicator(5));
        assertEquals(1, MathUtils.indicator(0));
        assertEquals(-1, MathUtils.indicator(-5));
    }

    @Test
    public void testIndicatorLong() throws Throwable {
        assertEquals(1L, MathUtils.indicator(5L));
        assertEquals(1L, MathUtils.indicator(0L));
        assertEquals(-1L, MathUtils.indicator(-5L));
    }

    @Test
    public void testIndicatorShort() throws Throwable {
        assertEquals((short) 1, MathUtils.indicator((short) 5));
        assertEquals((short) 1, MathUtils.indicator((short) 0));
        assertEquals((short) -1, MathUtils.indicator((short) -5));
    }

    @Test
    public void testLcm() throws Throwable {
        assertEquals(0, MathUtils.lcm(0, 5));
        assertEquals(0, MathUtils.lcm(5, 0));
        assertEquals(12, MathUtils.lcm(4, 6));
        assertEquals(12, MathUtils.lcm(-4, 6));
    }

    @Test
    public void testLog() throws Throwable {
        assertEquals(2.0, MathUtils.log(3.0, 9.0), 1e-12);
    }

    @Test
    public void testMulAndCheckInt() throws Throwable {
        assertEquals(6, MathUtils.mulAndCheck(2, 3));
        assertEquals(Integer.MAX_VALUE, MathUtils.mulAndCheck(Integer.MAX_VALUE, 1));

        try {
            MathUtils.mulAndCheck(Integer.MAX_VALUE, 2);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testMulAndCheckLong() throws Throwable {
        assertEquals(6L, MathUtils.mulAndCheck(2L, 3L));
        assertEquals(0L, MathUtils.mulAndCheck(0L, 5L));
        assertEquals(0L, MathUtils.mulAndCheck(5L, 0L));
        assertEquals(6L, MathUtils.mulAndCheck(-2L, -3L));
        assertEquals(-6L, MathUtils.mulAndCheck(-2L, 3L));
        assertEquals(-6L, MathUtils.mulAndCheck(2L, -3L));

        try {
            MathUtils.mulAndCheck(Long.MAX_VALUE, 2L);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        try {
            MathUtils.mulAndCheck(Long.MIN_VALUE, 2L);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testNextAfter() throws Throwable {
        assertTrue(Double.isNaN(MathUtils.nextAfter(Double.NaN, 0.0)));
        assertTrue(Double.isInfinite(MathUtils.nextAfter(Double.POSITIVE_INFINITY, 0.0)));
        assertEquals(Double.MIN_VALUE, MathUtils.nextAfter(0.0, 1.0), 0.0);
        assertEquals(-Double.MIN_VALUE, MathUtils.nextAfter(0.0, -1.0), 0.0);

        assertTrue(MathUtils.nextAfter(1.0, 2.0) > 1.0);
        assertTrue(MathUtils.nextAfter(1.0, 0.0) < 1.0);

        // mantissa boundary cases
        double maxDouble = Double.MAX_VALUE;
        assertTrue(MathUtils.nextAfter(maxDouble, Double.POSITIVE_INFINITY) > maxDouble);
        assertTrue(MathUtils.nextAfter(0.0, 1.0) == Double.MIN_VALUE);
    }

    @Test
    public void testScalb() throws Throwable {
        assertEquals(0.0, MathUtils.scalb(0.0, 2), 0.0);
        assertTrue(Double.isNaN(MathUtils.scalb(Double.NaN, 2)));
        assertTrue(Double.isInfinite(MathUtils.scalb(Double.POSITIVE_INFINITY, 2)));
        assertEquals(8.0, MathUtils.scalb(2.0, 2), 0.0);
    }

    @Test
    public void testNormalizeAngle() throws Throwable {
        assertEquals(0.0, MathUtils.normalizeAngle(Math.PI * 2, Math.PI), 1e-12);
    }

    @Test
    public void testRoundDouble() throws Throwable {
        assertEquals(1.25, MathUtils.round(1.254, 2), 1e-12);
        assertEquals(1.25, MathUtils.round(1.255, 2, BigDecimal.ROUND_HALF_UP), 1e-12);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.round(Double.POSITIVE_INFINITY, 2), 0.0);
        assertTrue(Double.isNaN(MathUtils.round(Double.NaN, 2)));
    }

    @Test
    public void testRoundFloat() throws Throwable {
        assertEquals(1.25f, MathUtils.round(1.254f, 2), 1e-5f);
        assertEquals(1.25f, MathUtils.round(1.254f, 2, BigDecimal.ROUND_HALF_UP), 1e-5f);

        // Test various rounding methods via roundUnscaled/round float/double
        assertEquals(2.0, MathUtils.roundUnscaled(1.5, 1.0, BigDecimal.ROUND_CEILING), 1e-12);
        assertEquals(-1.0, MathUtils.roundUnscaled(-1.5, -1.0, BigDecimal.ROUND_CEILING), 1e-12);

        assertEquals(1.0, MathUtils.roundUnscaled(1.6, 1.0, BigDecimal.ROUND_DOWN), 1e-12);

        assertEquals(1.0, MathUtils.roundUnscaled(1.5, 1.0, BigDecimal.ROUND_FLOOR), 1e-12);
        assertEquals(-2.0, MathUtils.roundUnscaled(-1.5, -1.0, BigDecimal.ROUND_FLOOR), 1e-12);

        assertEquals(1.0, MathUtils.roundUnscaled(1.5, 1.0, BigDecimal.ROUND_HALF_DOWN), 1e-12);
        assertEquals(2.0, MathUtils.roundUnscaled(1.6, 1.0, BigDecimal.ROUND_HALF_DOWN), 1e-12);

        assertEquals(2.0, MathUtils.roundUnscaled(2.5, 1.0, BigDecimal.ROUND_HALF_EVEN), 1e-12);
        assertEquals(2.0, MathUtils.roundUnscaled(1.5, 1.0, BigDecimal.ROUND_HALF_EVEN), 1e-12);
        assertEquals(2.0, MathUtils.roundUnscaled(2.4, 1.0, BigDecimal.ROUND_HALF_EVEN), 1e-12);

        assertEquals(2.0, MathUtils.roundUnscaled(1.5, 1.0, BigDecimal.ROUND_HALF_UP), 1e-12);

        assertEquals(2.0, MathUtils.roundUnscaled(1.1, 1.0, BigDecimal.ROUND_UP), 1e-12);

        try {
            MathUtils.roundUnscaled(1.5, 1.0, BigDecimal.ROUND_UNNECESSARY);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            MathUtils.roundUnscaled(1.0, 1.0, 999);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testSign() throws Throwable {
        assertEquals((byte) 0, MathUtils.sign((byte) 0));
        assertEquals((byte) 1, MathUtils.sign((byte) 5));
        assertEquals((byte) -1, MathUtils.sign((byte) -5));

        assertEquals(0.0, MathUtils.sign(0.0), 0.0);
        assertEquals(1.0, MathUtils.sign(5.0), 0.0);
        assertEquals(-1.0, MathUtils.sign(-5.0), 0.0);
        assertTrue(Double.isNaN(MathUtils.sign(Double.NaN)));

        assertEquals(0.0f, MathUtils.sign(0.0f), 0.0f);
        assertEquals(1.0f, MathUtils.sign(5.0f), 0.0f);
        assertEquals(-1.0f, MathUtils.sign(-5.0f), 0.0f);
        assertTrue(Float.isNaN(MathUtils.sign(Float.NaN)));

        assertEquals(0, MathUtils.sign(0));
        assertEquals(1, MathUtils.sign(5));
        assertEquals(-1, MathUtils.sign(-5));

        assertEquals(0L, MathUtils.sign(0L));
        assertEquals(1L, MathUtils.sign(5L));
        assertEquals(-1L, MathUtils.sign(-5L));

        assertEquals((short) 0, MathUtils.sign((short) 0));
        assertEquals((short) 1, MathUtils.sign((short) 5));
        assertEquals((short) -1, MathUtils.sign((short) -5));
    }

    @Test
    public void testSinh() throws Throwable {
        assertEquals(0.0, MathUtils.sinh(0.0), 1e-12);
    }

    @Test
    public void testSubAndCheckInt() throws Throwable {
        assertEquals(2, MathUtils.subAndCheck(5, 3));
        try {
            MathUtils.subAndCheck(Integer.MIN_VALUE, 1);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testSubAndCheckLong() throws Throwable {
        assertEquals(2L, MathUtils.subAndCheck(5L, 3L));

        try {
            MathUtils.subAndCheck(5L, Long.MIN_VALUE);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        assertEquals(5L - Long.MIN_VALUE, MathUtils.subAndCheck(-5L, Long.MIN_VALUE));

        try {
            MathUtils.subAndCheck(Long.MIN_VALUE, 1L);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }
}