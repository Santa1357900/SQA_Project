package org.apache.commons.math3.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class FastMathTest {

    @Test
    public void testConstants() throws Throwable {
        assertTrue(FastMath.PI > 3.14 && FastMath.PI < 3.15);
        assertTrue(FastMath.E > 2.71 && FastMath.E < 2.73);
    }

    @Test
    public void testSqrt() throws Throwable {
        assertEquals(2.0, FastMath.sqrt(4.0), 1e-15);
        assertEquals(0.0, FastMath.sqrt(0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.sqrt(-1.0)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.sqrt(Double.POSITIVE_INFINITY), 1e-15);
    }

    @Test
    public void testCosh() throws Throwable {
        assertEquals(1.0, FastMath.cosh(0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.cosh(Double.NaN)));
        assertTrue(FastMath.cosh(25.0) > 0);
        assertTrue(FastMath.cosh(-25.0) > 0);
        assertEquals(FastMath.cosh(1.0), FastMath.cosh(-1.0), 1e-15);
    }

    @Test
    public void testSinh() throws Throwable {
        assertEquals(0.0, FastMath.sinh(0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.sinh(Double.NaN)));
        assertTrue(FastMath.sinh(25.0) > 0);
        assertTrue(FastMath.sinh(-25.0) < 0);
        assertEquals(FastMath.sinh(0.1), -FastMath.sinh(-0.1), 1e-15);
    }

    @Test
    public void testTanh() throws Throwable {
        assertEquals(0.0, FastMath.tanh(0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.tanh(Double.NaN)));
        assertEquals(1.0, FastMath.tanh(25.0), 1e-15);
        assertEquals(-1.0, FastMath.tanh(-25.0), 1e-15);
        assertEquals(FastMath.tanh(0.2), -FastMath.tanh(-0.2), 1e-15);
    }

    @Test
    public void testAcosh() throws Throwable {
        assertEquals(0.0, FastMath.acosh(1.0), 1e-15);
        assertTrue(FastMath.acosh(2.0) > 0);
    }

    @Test
    public void testAsinh() throws Throwable {
        assertEquals(0.0, FastMath.asinh(0.0), 1e-15);
        assertTrue(FastMath.asinh(1.0) > 0);
        assertTrue(FastMath.asinh(-1.0) < 0);
        assertTrue(FastMath.asinh(0.05) > 0);
        assertTrue(FastMath.asinh(0.1) > 0);
        assertTrue(FastMath.asinh(0.2) > 0);
    }

    @Test
    public void testAtanh() throws Throwable {
        assertEquals(0.0, FastMath.atanh(0.0), 1e-15);
        assertTrue(FastMath.atanh(0.5) > 0);
        assertTrue(FastMath.atanh(-0.5) < 0);
        assertTrue(FastMath.atanh(0.01) > 0);
        assertTrue(FastMath.atanh(0.05) > 0);
        assertTrue(FastMath.atanh(0.1) > 0);
    }

    @Test
    public void testSignum() throws Throwable {
        assertEquals(1.0, FastMath.signum(5.0), 1e-15);
        assertEquals(-1.0, FastMath.signum(-5.0), 1e-15);
        assertEquals(0.0, FastMath.signum(0.0), 1e-15);
        assertEquals(-0.0, FastMath.signum(-0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.signum(Double.NaN)));

        assertEquals(1.0f, FastMath.signum(5.0f), 1e-15f);
        assertEquals(-1.0f, FastMath.signum(-5.0f), 1e-15f);
        assertEquals(0.0f, FastMath.signum(0.0f), 1e-15f);
        assertEquals(-0.0f, FastMath.signum(-0.0f), 1e-15f);
        assertTrue(Float.isNaN(FastMath.signum(Float.NaN)));
    }

    @Test
    public void testNextUp() throws Throwable {
        assertTrue(FastMath.nextUp(1.0) > 1.0);
        assertTrue(FastMath.nextUp(1.0f) > 1.0f);
    }

    @Test
    public void testRandom() throws Throwable {
        double r = FastMath.random();
        assertTrue(r >= 0.0 && r <= 1.0);
    }

    @Test
    public void testExp() throws Throwable {
        assertEquals(1.0, FastMath.exp(0.0), 1e-15);
        assertEquals(FastMath.E, FastMath.exp(1.0), 1e-12);
        assertEquals(0.0, FastMath.exp(-1000.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.exp(1000.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.exp(Double.NaN)));
    }

    @Test
    public void testExpm1() throws Throwable {
        assertEquals(0.0, FastMath.expm1(0.0), 1e-15);
        assertTrue(FastMath.expm1(1.0) > 0);
        assertTrue(FastMath.expm1(-0.5) < 0);
        assertTrue(FastMath.expm1(2.0) > 0);
        assertTrue(FastMath.expm1(-2.0) < 0);
        assertTrue(Double.isNaN(FastMath.expm1(Double.NaN)));
    }

    @Test
    public void testLog() throws Throwable {
        assertEquals(0.0, FastMath.log(1.0), 1e-15);
        assertEquals(1.0, FastMath.log(FastMath.E), 1e-12);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.log(0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.log(-1.0)));
        assertTrue(Double.isNaN(FastMath.log(Double.NaN)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.log(Double.POSITIVE_INFINITY), 1e-15);
        assertEquals(FastMath.log(2.0), FastMath.log(1.0 + 1e-7), 1e-5); // near 1 test path
    }

    @Test
    public void testLog1p() throws Throwable {
        assertEquals(0.0, FastMath.log1p(0.0), 1e-15);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.log1p(-1.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.log1p(Double.POSITIVE_INFINITY), 1e-15);
        assertTrue(FastMath.log1p(1e-7) > 0);
        assertTrue(FastMath.log1p(-1e-7) < 0);
    }

    @Test
    public void testLog10() throws Throwable {
        assertEquals(1.0, FastMath.log10(10.0), 1e-15);
        assertEquals(0.0, FastMath.log10(1.0), 1e-15);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.log10(0.0), 1e-15);
    }

    @Test
    public void testLogBase() throws Throwable {
        assertEquals(2.0, FastMath.log(2.0, 4.0), 1e-15);
        assertEquals(3.0, FastMath.log(2.0, 8.0), 1e-15);
    }

    @Test
    public void testPow() throws Throwable {
        assertEquals(8.0, FastMath.pow(2.0, 3.0), 1e-12);
        assertEquals(1.0, FastMath.pow(5.0, 0.0), 1e-15);
        assertEquals(0.0, FastMath.pow(0.0, 2.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(0.0, -2.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(Double.POSITIVE_INFINITY, 2.0), 1e-15);
        assertEquals(0.0, FastMath.pow(Double.POSITIVE_INFINITY, -2.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.pow(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)));
        assertTrue(Double.isNaN(FastMath.pow(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)));
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.pow(-0.0, 3.0), 1e-15);
        assertEquals(-0.0, FastMath.pow(-0.0, 3), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(-0.0, -3), 1e-15);
        assertTrue(Double.isNaN(FastMath.pow(-2.0, 0.5)));
        assertEquals(8.0, FastMath.pow(2.0, 3), 1e-12);
        assertEquals(0.125, FastMath.pow(2.0, -3), 1e-12);
        assertEquals(1.0, FastMath.pow(2.0, 0), 1e-12);
    }

    @Test
    public void testSin() throws Throwable {
        assertEquals(0.0, FastMath.sin(0.0), 1e-15);
        assertEquals(-0.0, FastMath.sin(-0.0), 1e-15);
        assertEquals(1.0, FastMath.sin(FastMath.PI / 2.0), 1e-12);
        assertEquals(-1.0, FastMath.sin(-FastMath.PI / 2.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.sin(Double.NaN)));
        assertTrue(Double.isNaN(FastMath.sin(Double.POSITIVE_INFINITY)));
        assertEquals(FastMath.sin(10000000.0), FastMath.sin(10000000.0 % (2 * FastMath.PI)), 1e-10); // Payne-Hanek
    }

    @Test
    public void testCos() throws Throwable {
        assertEquals(1.0, FastMath.cos(0.0), 1e-12);
        assertEquals(0.0, FastMath.cos(FastMath.PI / 2.0), 1e-12);
        assertEquals(-1.0, FastMath.cos(FastMath.PI), 1e-12);
        assertTrue(Double.isNaN(FastMath.cos(Double.NaN)));
        assertTrue(Double.isNaN(FastMath.cos(Double.POSITIVE_INFINITY)));
        assertEquals(FastMath.cos(10000000.0), FastMath.cos(10000000.0 % (2 * FastMath.PI)), 1e-10);
    }

    @Test
    public void testTan() throws Throwable {
        assertEquals(0.0, FastMath.tan(0.0), 1e-15);
        assertEquals(-0.0, FastMath.tan(-0.0), 1e-15);
        assertEquals(1.0, FastMath.tan(FastMath.PI / 4.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.tan(Double.NaN)));
        assertTrue(Double.isNaN(FastMath.tan(Double.POSITIVE_INFINITY)));
        assertEquals(FastMath.tan(10000000.0), FastMath.tan(10000000.0 % (2 * FastMath.PI)), 1e-10);
    }

    @Test
    public void testAtan() throws Throwable {
        assertEquals(0.0, FastMath.atan(0.0), 1e-15);
        assertEquals(-0.0, FastMath.atan(-0.0), 1e-15);
        assertEquals(FastMath.PI / 4.0, FastMath.atan(1.0), 1e-12);
        assertEquals(FastMath.PI / 2.0, FastMath.atan(1e20), 1e-6);
        assertEquals(-FastMath.PI / 2.0, FastMath.atan(-1e20), 1e-6);
        assertEquals(FastMath.PI / 4.0, FastMath.atan(2.0), 1e-1); // test idx > 0 branch
    }

    @Test
    public void testAtan2() throws Throwable {
        assertEquals(0.0, FastMath.atan2(0.0, 1.0), 1e-15);
        assertEquals(FastMath.PI, FastMath.atan2(0.0, -1.0), 1e-12);
        assertEquals(FastMath.PI / 2.0, FastMath.atan2(1.0, 0.0), 1e-12);
        assertEquals(-FastMath.PI / 2.0, FastMath.atan2(-1.0, 0.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.atan2(Double.NaN, 1.0)));
        assertTrue(Double.isNaN(FastMath.atan2(1.0, Double.NaN)));
        assertEquals(FastMath.PI / 4.0, FastMath.atan2(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY), 1e-12);
        assertEquals(3.0 * FastMath.PI / 4.0, FastMath.atan2(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY), 1e-12);
        assertEquals(-FastMath.PI / 4.0, FastMath.atan2(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), 1e-12);
        assertEquals(-3.0 * FastMath.PI / 4.0, FastMath.atan2(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY), 1e-12);
        assertEquals(0.0, FastMath.atan2(1.0, Double.POSITIVE_INFINITY), 1e-12);
        assertEquals(-0.0, FastMath.atan2(-1.0, Double.POSITIVE_INFINITY), 1e-12);
        assertEquals(FastMath.PI, FastMath.atan2(1.0, Double.NEGATIVE_INFINITY), 1e-12);
        assertEquals(-FastMath.PI, FastMath.atan2(-1.0, Double.NEGATIVE_INFINITY), 1e-12);
        assertEquals(FastMath.PI / 2.0, FastMath.atan2(Double.POSITIVE_INFINITY, 0.0), 1e-12);
        assertEquals(-FastMath.PI / 2.0, FastMath.atan2(Double.NEGATIVE_INFINITY, 0.0), 1e-12);
    }

    @Test
    public void testAsin() throws Throwable {
        assertEquals(0.0, FastMath.asin(0.0), 1e-15);
        assertEquals(FastMath.PI / 2.0, FastMath.asin(1.0), 1e-12);
        assertEquals(-FastMath.PI / 2.0, FastMath.asin(-1.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.asin(2.0)));
        assertTrue(Double.isNaN(FastMath.asin(Double.NaN)));
    }

    @Test
    public void testAcos() throws Throwable {
        assertEquals(FastMath.PI / 2.0, FastMath.acos(0.0), 1e-12);
        assertEquals(0.0, FastMath.acos(1.0), 1e-12);
        assertEquals(FastMath.PI, FastMath.acos(-1.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.acos(2.0)));
        assertTrue(Double.isNaN(FastMath.acos(Double.NaN)));
        assertEquals(FastMath.PI / 2.0, FastMath.acos(1e-20), 1e-6);
    }

    @Test
    public void testCbrt() throws Throwable {
        assertEquals(2.0, FastMath.cbrt(8.0), 1e-12);
        assertEquals(-2.0, FastMath.cbrt(-8.0), 1e-12);
        assertEquals(0.0, FastMath.cbrt(0.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.cbrt(Double.POSITIVE_INFINITY), 1e-15);
        assertEquals(1.8014398509481984E16 * 2.0, FastMath.cbrt(1.8014398509481984E16 * 1.8014398509481984E16 * 1.8014398509481984E16 * 8.0), 1e-5); // subnormal test
    }

    @Test
    public void testToRadiansDegrees() throws Throwable {
        assertEquals(FastMath.PI, FastMath.toRadians(180.0), 1e-12);
        assertEquals(180.0, FastMath.toDegrees(FastMath.PI), 1e-10);
        assertEquals(0.0, FastMath.toRadians(0.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.toRadians(Double.POSITIVE_INFINITY), 1e-15);
        assertEquals(0.0, FastMath.toDegrees(0.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.toDegrees(Double.POSITIVE_INFINITY), 1e-15);
    }

    @Test
    public void testAbs() throws Throwable {
        assertEquals(5, FastMath.abs(-5));
        assertEquals(5, FastMath.abs(5));
        assertEquals(5L, FastMath.abs(-5L));
        assertEquals(5.0f, FastMath.abs(-5.0f), 1e-15f);
        assertEquals(0.0f, FastMath.abs(-0.0f), 1e-15f);
        assertEquals(5.0, FastMath.abs(-5.0), 1e-15);
        assertEquals(0.0, FastMath.abs(-0.0), 1e-15);
    }

    @Test
    public void testUlp() throws Throwable {
        assertTrue(FastMath.ulp(1.0) > 0);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.ulp(Double.POSITIVE_INFINITY), 1e-15);
        assertTrue(FastMath.ulp(1.0f) > 0);
        assertEquals(Float.POSITIVE_INFINITY, FastMath.ulp(Float.POSITIVE_INFINITY), 1e-15f);
    }

    @Test
    public void testScalb() throws Throwable {
        assertEquals(8.0, FastMath.scalb(2.0, 2), 1e-15);
        assertEquals(2.0, FastMath.scalb(8.0, -2), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.scalb(Double.POSITIVE_INFINITY, 2), 1e-15);
        assertEquals(0.0, FastMath.scalb(0.0, 2), 1e-15);
        assertEquals(0.0, FastMath.scalb(1.0, -3000), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.scalb(1.0, 3000), 1e-15);

        assertEquals(8.0f, FastMath.scalb(2.0f, 2), 1e-15f);
        assertEquals(2.0f, FastMath.scalb(8.0f, -2), 1e-15f);
        assertEquals(Float.POSITIVE_INFINITY, FastMath.scalb(Float.POSITIVE_INFINITY, 2), 1e-15f);
        assertEquals(0.0f, FastMath.scalb(0.0f, 2), 1e-15f);
        assertEquals(0.0f, FastMath.scalb(1.0f, -300), 1e-15f);
        assertEquals(Float.POSITIVE_INFINITY, FastMath.scalb(1.0f, 300), 1e-15f);
    }

    @Test
    public void testNextAfter() throws Throwable {
        assertTrue(FastMath.nextAfter(1.0, 2.0) > 1.0);
        assertTrue(FastMath.nextAfter(1.0, 0.0) < 1.0);
        assertEquals(2.0, FastMath.nextAfter(2.0, 2.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.nextAfter(Double.NaN, 1.0)));
        assertEquals(Double.MAX_VALUE, FastMath.nextAfter(Double.POSITIVE_INFINITY, 0.0), 1e-15);
        assertEquals(Double.MIN_VALUE, FastMath.nextAfter(0.0, 1.0), 1e-15);

        assertTrue(FastMath.nextAfter(1.0f, 2.0) > 1.0f);
        assertTrue(FastMath.nextAfter(1.0f, 0.0) < 1.0f);
        assertEquals(2.0f, FastMath.nextAfter(2.0f, 2.0), 1e-15f);
        assertTrue(Float.isNaN(FastMath.nextAfter(Float.NaN, 1.0)));
        assertEquals(Float.MAX_VALUE, FastMath.nextAfter(Float.POSITIVE_INFINITY, 0.0), 1e-15f);
        assertEquals(Float.MIN_VALUE, FastMath.nextAfter(0.0f, 1.0), 1e-15f);
    }

    @Test
    public void testFloorCeilRintRound() throws Throwable {
        assertEquals(2.0, FastMath.floor(2.7), 1e-15);
        assertEquals(-3.0, FastMath.floor(-2.7), 1e-15);
        assertEquals(2.0, FastMath.floor(2.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.floor(Double.NaN)));

        assertEquals(3.0, FastMath.ceil(2.3), 1e-15);
        assertEquals(-2.0, FastMath.ceil(-2.3), 1e-15);
        assertEquals(2.0, FastMath.ceil(2.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.ceil(Double.NaN)));

        assertEquals(2.0, FastMath.rint(2.3), 1e-15);
        assertEquals(3.0, FastMath.rint(2.7), 1e-15);
        assertEquals(2.0, FastMath.rint(2.5), 1e-15); // round to even
        assertEquals(4.0, FastMath.rint(3.5), 1e-15); // round to even
        assertEquals(-0.0, FastMath.rint(-0.5), 1e-15);

        assertEquals(3L, FastMath.round(2.7));
        assertEquals(3, FastMath.round(2.7f));
    }

    @Test
    public void testMinMax() throws Throwable {
        assertEquals(2, FastMath.min(2, 5));
        assertEquals(2, FastMath.min(5, 2));
        assertEquals(2L, FastMath.min(2L, 5L));
        assertEquals(2L, FastMath.min(5L, 2L));

        assertEquals(2.0f, FastMath.min(2.0f, 5.0f), 1e-15f);
        assertEquals(2.0f, FastMath.min(5.0f, 2.0f), 1e-15f);
        assertTrue(Float.isNaN(FastMath.min(Float.NaN, 1.0f)));
        assertTrue(Float.isNaN(FastMath.min(1.0f, Float.NaN)));
        assertEquals(-0.0f, FastMath.min(0.0f, -0.0f), 1e-15f);

        assertEquals(2.0, FastMath.min(2.0, 5.0), 1e-15);
        assertEquals(2.0, FastMath.min(5.0, 2.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.min(Double.NaN, 1.0)));
        assertTrue(Double.isNaN(FastMath.min(1.0, Double.NaN)));
        assertEquals(-0.0, FastMath.min(0.0, -0.0), 1e-15);

        assertEquals(5, FastMath.max(2, 5));
        assertEquals(5, FastMath.max(5, 2));
        assertEquals(5L, FastMath.max(2L, 5L));
        assertEquals(5L, FastMath.max(5L, 2L));

        assertEquals(5.0f, FastMath.max(2.0f, 5.0f), 1e-15f);
        assertEquals(5.0f, FastMath.max(5.0f, 2.0f), 1e-15f);
        assertTrue(Float.isNaN(FastMath.max(Float.NaN, 1.0f)));
        assertTrue(Float.isNaN(FastMath.max(1.0f, Float.NaN)));
        assertEquals(0.0f, FastMath.max(0.0f, -0.0f), 1e-15f);

        assertEquals(5.0, FastMath.max(2.0, 5.0), 1e-15);
        assertEquals(5.0, FastMath.max(5.0, 2.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.max(Double.NaN, 1.0)));
        assertTrue(Double.isNaN(FastMath.max(1.0, Double.NaN)));
        assertEquals(0.0, FastMath.max(0.0, -0.0), 1e-15);
    }

    @Test
    public void testHypot() throws Throwable {
        assertEquals(5.0, FastMath.hypot(3.0, 4.0), 1e-12);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.hypot(Double.POSITIVE_INFINITY, 3.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.hypot(3.0, Double.POSITIVE_INFINITY), 1e-15);
        assertTrue(Double.isNaN(FastMath.hypot(Double.NaN, 3.0)));
        assertTrue(Double.isNaN(FastMath.hypot(3.0, Double.NaN)));
        assertEquals(1e30, FastMath.hypot(1e30, 1e-30), 1e-15);
        assertEquals(1e30, FastMath.hypot(1e-30, 1e30), 1e-15);
    }

    @Test
    public void testIEEEremainder() throws Throwable {
        assertEquals(StrictMath.IEEEremainder(5.0, 2.0), FastMath.IEEEremainder(5.0, 2.0), 1e-15);
    }

    @Test
    public void testCopySign() throws Throwable {
        assertEquals(5.0, FastMath.copySign(5.0, -1.0), 1e-15);
        assertEquals(-5.0, FastMath.copySign(-5.0, 1.0), 1e-15);
        assertEquals(5.0f, FastMath.copySign(5.0f, -1.0f), 1e-15f);
        assertEquals(-5.0f, FastMath.copySign(-5.0f, 1.0f), 1e-15f);
    }

    @Test
    public void testGetExponent() throws Throwable {
        assertEquals(1, FastMath.getExponent(2.0));
        assertEquals(3, FastMath.getExponent(10.0));
        assertEquals(1, FastMath.getExponent(2.0f));
        assertEquals(3, FastMath.getExponent(10.0f));
    }
}