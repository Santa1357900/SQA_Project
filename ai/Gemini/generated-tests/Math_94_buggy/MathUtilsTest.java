package org.apache.commons.math.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;

public class MathUtilsTest {

    @Test
    public void testConstants() throws Throwable {
        assertTrue(MathUtils.EPSILON > 0);
        assertTrue(MathUtils.SAFE_MIN > 0);
    }

    @Test
    public void testAddAndCheckInt() throws Throwable {
        assertEquals(5, MathUtils.addAndCheck(2, 3));
        
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
        assertEquals(-5L, MathUtils.addAndCheck(-2L, -3L));
        assertEquals(-1L, MathUtils.addAndCheck(-5L, 4L));
        assertEquals(1L, MathUtils.addAndCheck(5L, -4L));

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
    }

    @Test
    public void testBinomialCoefficient() throws Throwable {
        assertEquals(1L, MathUtils.binomialCoefficient(5, 0));
        assertEquals(1L, MathUtils.binomialCoefficient(5, 5));
        assertEquals(5L, MathUtils.binomialCoefficient(5, 1));
        assertEquals(5L, MathUtils.binomialCoefficient(5, 4));
        assertEquals(10L, MathUtils.binomialCoefficient(5, 2));

        try {
            MathUtils.binomialCoefficient(3, 5);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must have n >= k"));
        }

        try {
            MathUtils.binomialCoefficient(-1, 0);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must have n >= 0"));
        }

        try {
            MathUtils.binomialCoefficient(70, 35);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("result too large"));
        }
    }

    @Test
    public void testBinomialCoefficientDouble() throws Throwable {
        assertEquals(10.0, MathUtils.binomialCoefficientDouble(5, 2), 1e-10);
        assertEquals(1.0, MathUtils.binomialCoefficientDouble(0, 0), 1e-10);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.binomialCoefficientDouble(2000, 1000), 1e-10);
    }

    @Test
    public void testBinomialCoefficientLog() throws Throwable {
        assertEquals(0.0, MathUtils.binomialCoefficientLog(5, 0), 1e-10);
        assertEquals(Math.log(5.0), MathUtils.binomialCoefficientLog(5, 1), 1e-10);
        assertEquals(Math.log(5.0), MathUtils.binomialCoefficientLog(5, 4), 1e-10);
        assertEquals(0.0, MathUtils.binomialCoefficientLog(5, 5), 1e-10);

        try {
            MathUtils.binomialCoefficientLog(2, 5);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must have n >= k"));
        }

        try {
            MathUtils.binomialCoefficientLog(-1, 0);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must have n >= 0"));
        }
    }

    @Test
    public void testCosh() throws Throwable {
        assertEquals(1.0, MathUtils.cosh(0.0), 1e-10);
        assertTrue(Double.isNaN(MathUtils.cosh(Double.NaN)));
    }

    @Test
    public void testEqualsDouble() throws Throwable {
        assertTrue(MathUtils.equals(1.0, 1.0));
        assertFalse(MathUtils.equals(1.0, 2.0));
        assertTrue(MathUtils.equals(Double.NaN, Double.NaN));
        assertFalse(MathUtils.equals(1.0, Double.NaN));
        assertFalse(MathUtils.equals(Double.NaN, 1.0));
    }

    @Test
    public void testEqualsDoubleArray() throws Throwable {
        assertTrue(MathUtils.equals((double[]) null, (double[]) null));
        assertFalse(MathUtils.equals(new double[]{1.0}, (double[]) null));
        assertFalse(MathUtils.equals((double[]) null, new double[]{1.0}));
        
        assertTrue(MathUtils.equals(new double[]{1.0, 2.0}, new double[]{1.0, 2.0}));
        assertFalse(MathUtils.equals(new double[]{1.0, 2.0}, new double[]{1.0}));
        assertFalse(MathUtils.equals(new double[]{1.0, 2.0}, new double[]{1.0, 3.0}));
        assertTrue(MathUtils.equals(new double[]{Double.NaN}, new double[]{Double.NaN}));
    }

    @Test
    public void testFactorial() throws Throwable {
        assertEquals(1L, MathUtils.factorial(0));
        assertEquals(1L, MathUtils.factorial(1));
        assertEquals(24L, MathUtils.factorial(4));
        assertEquals(2432902008176640000L, MathUtils.factorial(20));

        try {
            MathUtils.factorial(-1);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must have n >= 0"));
        }

        try {
            MathUtils.factorial(21);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("result too large"));
        }
    }

    @Test
    public void testFactorialDouble() throws Throwable {
        assertEquals(1.0, MathUtils.factorialDouble(0), 1e-10);
        assertEquals(24.0, MathUtils.factorialDouble(4), 1e-10);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.factorialDouble(200), 1e-10);

        try {
            MathUtils.factorialDouble(-1);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must have n >= 0"));
        }
    }

    @Test
    public void testFactorialLog() throws Throwable {
        assertEquals(0.0, MathUtils.factorialLog(0), 1e-10);
        assertEquals(0.0, MathUtils.factorialLog(1), 1e-10);
        assertEquals(Math.log(24.0), MathUtils.factorialLog(4), 1e-10);

        try {
            MathUtils.factorialLog(-1);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must have n > 0"));
        }
    }

    @Test
    public void testGcd() throws Throwable {
        assertEquals(2, MathUtils.gcd(2, 4));
        assertEquals(2, MathUtils.gcd(-2, 4));
        assertEquals(2, MathUtils.gcd(2, -4));
        assertEquals(2, MathUtils.gcd(-2, -4));
        assertEquals(5, MathUtils.gcd(5, 0));
        assertEquals(5, MathUtils.gcd(0, 5));
        assertEquals(0, MathUtils.gcd(0, 0));
        
        try {
            MathUtils.gcd(Integer.MIN_VALUE, Integer.MIN_VALUE);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testHash() throws Throwable {
        assertEquals(new Double(1.5).hashCode(), MathUtils.hash(1.5));
        assertEquals(java.util.Arrays.hashCode(new double[]{1.0, 2.0}), MathUtils.hash(new double[]{1.0, 2.0}));
    }

    @Test
    public void testIndicatorByte() throws Throwable {
        assertEquals((byte) 1, MathUtils.indicator((byte) 5));
        assertEquals((byte) 1, MathUtils.indicator((byte) 0));
        assertEquals((byte) -1, MathUtils.indicator((byte) -5));
    }

    @Test
    public void testIndicatorDouble() throws Throwable {
        assertEquals(1.0, MathUtils.indicator(5.0), 1e-10);
        assertEquals(1.0, MathUtils.indicator(0.0), 1e-10);
        assertEquals(-1.0, MathUtils.indicator(-5.0), 1e-10);
        assertTrue(Double.isNaN(MathUtils.indicator(Double.NaN)));
    }

    @Test
    public void testIndicatorFloat() throws Throwable {
        assertEquals(1.0f, MathUtils.indicator(5.0f), 1e-10);
        assertEquals(1.0f, MathUtils.indicator(0.0f), 1e-10);
        assertEquals(-1.0f, MathUtils.indicator(-5.0f), 1e-10);
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
        assertEquals(12, MathUtils.lcm(4, 6));
        assertEquals(0, MathUtils.lcm(0, 5));
        assertEquals(0, MathUtils.lcm(5, 0));
        
        try {
            MathUtils.lcm(Integer.MAX_VALUE, 2);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testLog() throws Throwable {
        assertEquals(2.0, MathUtils.log(3.0, 9.0), 1e-10);
        assertEquals(0.0, MathUtils.log(2.0, 1.0), 1e-10);
    }

    @Test
    public void testMulAndCheckInt() throws Throwable {
        assertEquals(6, MathUtils.mulAndCheck(2, 3));
        
        try {
            MathUtils.mulAndCheck(Integer.MAX_VALUE, 2);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        try {
            MathUtils.mulAndCheck(Integer.MIN_VALUE, 2);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testMulAndCheckLong() throws Throwable {
        assertEquals(6L, MathUtils.mulAndCheck(2L, 3L));
        assertEquals(6L, MathUtils.mulAndCheck(-2L, -3L));
        assertEquals(-6L, MathUtils.mulAndCheck(-2L, 3L));
        assertEquals(-6L, MathUtils.mulAndCheck(2L, -3L));
        assertEquals(0L, MathUtils.mulAndCheck(0L, 5L));
        assertEquals(0L, MathUtils.mulAndCheck(5L, 0L));

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

        try {
            MathUtils.mulAndCheck(Long.MIN_VALUE, -1L);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testNextAfter() throws Throwable {
        assertEquals(Double.valueOf(Double.NaN), MathUtils.nextAfter(Double.NaN, 1.0), 1e-10);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.nextAfter(Double.POSITIVE_INFINITY, 1.0), 1e-10);
        assertEquals(Double.MIN_VALUE, MathUtils.nextAfter(0.0, 1.0), 1e-10);
        assertEquals(-Double.MIN_VALUE, MathUtils.nextAfter(0.0, -1.0), 1e-10);

        assertTrue(MathUtils.nextAfter(1.0, 2.0) > 1.0);
        assertTrue(MathUtils.nextAfter(1.0, 0.0) < 1.0);
        
        double maxSub = Double.longBitsToDouble(0x000fffffffffffffL);
        assertTrue(MathUtils.nextAfter(maxSub, 2.0) > maxSub);
        assertTrue(MathUtils.nextAfter(Double.MIN_VALUE, -1.0) == 0.0);
    }

    @Test
    public void testScalb() throws Throwable {
        assertEquals(0.0, MathUtils.scalb(0.0, 2), 1e-10);
        assertEquals(Double.valueOf(Double.NaN), MathUtils.scalb(Double.NaN, 2), 1e-10);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.scalb(Double.POSITIVE_INFINITY, 2), 1e-10);
        assertEquals(4.0, MathUtils.scalb(1.0, 2), 1e-10);
    }

    @Test
    public void testNormalizeAngle() throws Throwable {
        double normalized = MathUtils.normalizeAngle(Math.PI * 3, Math.PI);
        assertEquals(Math.PI, normalized, 1e-10);
    }

    @Test
    public void testRoundDouble() throws Throwable {
        assertEquals(1.23, MathUtils.round(1.234, 2), 1e-10);
        assertEquals(1.24, MathUtils.round(1.236, 2), 1e-10);
        assertEquals(1.234, MathUtils.round(1.234, 2, BigDecimal.ROUND_HALF_UP), 1e-10);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MathUtils.round(Double.POSITIVE_INFINITY, 2), 1e-10);
        assertEquals(Double.valueOf(Double.NaN), MathUtils.round(0.0 / 0.0, 2), 1e-10);
    }

    @Test
    public void testRoundFloat() throws Throwable {
        assertEquals(1.23f, MathUtils.round(1.234f, 2), 1e-5);
        assertEquals(1.24f, MathUtils.round(1.236f, 2), 1e-5);
        assertEquals(1.234f, MathUtils.round(1.234f, 2, BigDecimal.ROUND_HALF_UP), 1e-5);
    }

    @Test
    public void testRoundUnscaledEdgeCases() throws Throwable {
        assertEquals(2.0, MathUtils.round(1.5, 0, BigDecimal.ROUND_CEILING), 1e-10);
        assertEquals(-1.0, MathUtils.round(-1.5, 0, BigDecimal.ROUND_CEILING), 1e-10);
        assertEquals(1.0, MathUtils.round(1.5, 0, BigDecimal.ROUND_DOWN), 1e-10);
        assertEquals(-2.0, MathUtils.round(-1.5, 0, BigDecimal.ROUND_FLOOR), 1e-10);
        assertEquals(1.0, MathUtils.round(1.5, 0, BigDecimal.ROUND_FLOOR), 1e-10);
        assertEquals(1.0, MathUtils.round(1.4, 0, BigDecimal.ROUND_HALF_DOWN), 1e-10);
        assertEquals(2.0, MathUtils.round(1.6, 0, BigDecimal.ROUND_HALF_DOWN), 1e-10);
        assertEquals(2.0, MathUtils.round(2.5, 0, BigDecimal.ROUND_HALF_EVEN), 1e-10);
        assertEquals(2.0, MathUtils.round(2.51, 0, BigDecimal.ROUND_HALF_EVEN), 1e-10);
        assertEquals(2.0, MathUtils.round(1.5, 0, BigDecimal.ROUND_HALF_UP), 1e-10);
        assertEquals(2.0, MathUtils.round(2.0, 0, BigDecimal.ROUND_UNNECESSARY), 1e-10);
        assertEquals(2.0, MathUtils.round(1.6, 0, BigDecimal.ROUND_UP), 1e-10);

        try {
            MathUtils.round(1.5, 0, BigDecimal.ROUND_UNNECESSARY);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("Inexact result"));
        }

        try {
            MathUtils.round(1.5, 0, -999);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid rounding method"));
        }
    }

    @Test
    public void testSignByte() throws Throwable {
        assertEquals((byte) 1, MathUtils.sign((byte) 5));
        assertEquals((byte) 0, MathUtils.sign((byte) 0));
        assertEquals((byte) -1, MathUtils.sign((byte) -5));
    }

    @Test
    public void testSignDouble() throws Throwable {
        assertEquals(1.0, MathUtils.sign(5.0), 1e-10);
        assertEquals(0.0, MathUtils.sign(0.0), 1e-10);
        assertEquals(-1.0, MathUtils.sign(-5.0), 1e-10);
        assertTrue(Double.isNaN(MathUtils.sign(Double.NaN)));
    }

    @Test
    public void testSignFloat() throws Throwable {
        assertEquals(1.0f, MathUtils.sign(5.0f), 1e-10);
        assertEquals(0.0f, MathUtils.sign(0.0f), 1e-10);
        assertEquals(-1.0f, MathUtils.sign(-5.0f), 1e-10);
        assertTrue(Float.isNaN(MathUtils.sign(Float.NaN)));
    }

    @Test
    public void testSignInt() throws Throwable {
        assertEquals(1, MathUtils.sign(5));
        assertEquals(0, MathUtils.sign(0));
        assertEquals(-1, MathUtils.sign(-5));
    }

    @Test
    public void testSignLong() throws Throwable {
        assertEquals(1L, MathUtils.sign(5L));
        assertEquals(0L, MathUtils.sign(0L));
        assertEquals(-1L, MathUtils.sign(-5L));
    }

    @Test
    public void testSignShort() throws Throwable {
        assertEquals((short) 1, MathUtils.sign((short) 5));
        assertEquals((short) 0, MathUtils.sign((short) 0));
        assertEquals((short) -1, MathUtils.sign((short) -5));
    }

    @Test
    public void testSinh() throws Throwable {
        assertEquals(0.0, MathUtils.sinh(0.0), 1e-10);
        assertTrue(Double.isNaN(MathUtils.sinh(Double.NaN)));
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

        try {
            MathUtils.subAndCheck(Integer.MAX_VALUE, -1);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testSubAndCheckLong() throws Throwable {
        assertEquals(2L, MathUtils.subAndCheck(5L, 3L));
        assertEquals(-2L, MathUtils.subAndCheck(3L, 5L));

        try {
            MathUtils.subAndCheck(Long.MIN_VALUE, 1L);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        try {
            MathUtils.subAndCheck(Long.MAX_VALUE, -1L);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        try {
            MathUtils.subAndCheck(1L, Long.MIN_VALUE);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        assertEquals(Long.MAX_VALUE, MathUtils.subAndCheck(Long.MAX_VALUE, Long.MIN_VALUE));
    }
}