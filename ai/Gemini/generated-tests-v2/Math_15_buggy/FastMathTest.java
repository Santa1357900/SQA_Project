package org.apache.commons.math3.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class FastMathTest {

    @Test
    public void testConstants() throws Throwable {
        assertTrue(FastMath.PI > 3.14 && FastMath.PI < 3.15);
        assertTrue(FastMath.E > 2.71 && FastMath.E < 2.72);
    }

    @Test
    public void testSqrt() throws Throwable {
        assertEquals(0.0, FastMath.sqrt(0.0), 1e-15);
        assertEquals(2.0, FastMath.sqrt(4.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.sqrt(-1.0)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.sqrt(Double.POSITIVE_INFINITY), 1e-15);
    }

    @Test
    public void testCosh() throws Throwable {
        assertEquals(1.0, FastMath.cosh(0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.cosh(Double.NaN)));
        assertTrue(FastMath.cosh(25.0) > 0);
        assertTrue(FastMath.cosh(-25.0) > 0);
        assertTrue(FastMath.cosh(800.0) == Double.POSITIVE_INFINITY);
        assertTrue(FastMath.cosh(-800.0) == Double.POSITIVE_INFINITY);
    }

    @Test
    public void testSinh() throws Throwable {
        assertEquals(0.0, FastMath.sinh(0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.sinh(Double.NaN)));
        assertTrue(FastMath.sinh(25.0) > 0);
        assertTrue(FastMath.sinh(-25.0) < 0);
        assertTrue(FastMath.sinh(800.0) == Double.POSITIVE_INFINITY);
        assertTrue(FastMath.sinh(-800.0) == Double.NEGATIVE_INFINITY);
        assertTrue(FastMath.sinh(0.1) != 0);
    }

    @Test
    public void testTanh() throws Throwable {
        assertEquals(0.0, FastMath.tanh(0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.tanh(Double.NaN)));
        assertEquals(1.0, FastMath.tanh(25.0), 1e-15);
        assertEquals(-1.0, FastMath.tanh(-25.0), 1e-15);
        assertTrue(FastMath.tanh(0.1) != 0);
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
        assertTrue(FastMath.asinh(0.2) > 0);
        assertTrue(FastMath.asinh(0.1) > 0);
        assertTrue(FastMath.asinh(0.05) > 0);
        assertTrue(FastMath.asinh(0.001) > 0);
    }

    @Test
    public void testAtanh() throws Throwable {
        assertEquals(0.0, FastMath.atanh(0.0), 1e-15);
        assertTrue(FastMath.atanh(0.5) > 0);
        assertTrue(FastMath.atanh(-0.5) < 0);
        assertTrue(FastMath.atanh(0.2) > 0);
        assertTrue(FastMath.atanh(0.1) > 0);
        assertTrue(FastMath.atanh(0.01) > 0);
        assertTrue(FastMath.atanh(0.001) > 0);
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
        assertTrue(FastMath.exp(1000.0) == Double.POSITIVE_INFINITY);
        assertTrue(FastMath.exp(-1000.0) == 0.0);
        assertTrue(FastMath.exp(-720.0) >= 0.0);
        assertTrue(FastMath.exp(-710.0) >= 0.0);
        assertTrue(FastMath.exp(710.0) > 0.0);
    }

    @Test
    public void testExpm1() throws Throwable {
        assertEquals(0.0, FastMath.expm1(0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.expm1(Double.NaN)));
        assertTrue(FastMath.expm1(2.0) > 0);
        assertTrue(FastMath.expm1(-2.0) < 0);
        assertTrue(FastMath.expm1(0.5) > 0);
        assertTrue(FastMath.expm1(-0.5) < 0);
    }

    @Test
    public void testLog() throws Throwable {
        assertEquals(0.0, FastMath.log(1.0), 1e-15);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.log(0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.log(-1.0)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.log(Double.POSITIVE_INFINITY), 1e-15);
        assertTrue(FastMath.log(0.0) == Double.NEGATIVE_INFINITY);
        assertTrue(FastMath.log(1.005) > 0);
        assertTrue(FastMath.log(Double.MIN_VALUE) < 0);
    }

    @Test
    public void testLog1p() throws Throwable {
        assertEquals(0.0, FastMath.log1p(0.0), 1e-15);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.log1p(-1.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.log1p(Double.POSITIVE_INFINITY), 1e-15);
        assertTrue(FastMath.log1p(0.5) > 0);
        assertTrue(FastMath.log1p(1e-7) > 0);
    }

    @Test
    public void testLog10() throws Throwable {
        assertEquals(1.0, FastMath.log10(10.0), 1e-15);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.log10(0.0), 1e-15);
    }

    @Test
    public void testLogBase() throws Throwable {
        assertEquals(2.0, FastMath.log(2.0, 4.0), 1e-15);
    }

    @Test
    public void testPow() throws Throwable {
        assertEquals(8.0, FastMath.pow(2.0, 3.0), 1e-15);
        assertEquals(1.0, FastMath.pow(5.0, 0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.pow(Double.NaN, 2.0)));
        assertEquals(0.0, FastMath.pow(0.0, 2.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(0.0, -2.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(Double.POSITIVE_INFINITY, 2.0), 1e-15);
        assertEquals(0.0, FastMath.pow(Double.POSITIVE_INFINITY, -2.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.pow(1.0, Double.POSITIVE_INFINITY)));
        assertTrue(Double.isNaN(FastMath.pow(-1.0, Double.POSITIVE_INFINITY)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(2.0, Double.POSITIVE_INFINITY), 1e-15);
        assertEquals(0.0, FastMath.pow(0.5, Double.POSITIVE_INFINITY), 1e-15);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.pow(Double.NEGATIVE_INFINITY, 3.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(Double.NEGATIVE_INFINITY, 2.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.pow(1.0, Double.NEGATIVE_INFINITY)));
        assertEquals(0.0, FastMath.pow(2.0, Double.NEGATIVE_INFINITY), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(0.5, Double.NEGATIVE_INFINITY), 1e-15);
        assertTrue(Double.isNaN(FastMath.pow(-2.0, 1.5)));
        assertEquals(-8.0, FastMath.pow(-2.0, 3), 1e-15);
        assertEquals(8.0, FastMath.pow(-2.0, 3.0), 1e-15); // note: fractional/even-odd check logic in pow(double,double)
        assertEquals(0.25, FastMath.pow(2.0, -2), 1e-15);
        assertEquals(1.0, FastMath.pow(2.0, 0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(2.0, 1e300), 1e-15);
    }

    @Test
    public void testSin() throws Throwable {
        assertEquals(0.0, FastMath.sin(0.0), 1e-15);
        assertEquals(-0.0, FastMath.sin(-0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.sin(Double.NaN)));
        assertTrue(Double.isNaN(FastMath.sin(Double.POSITIVE_INFINITY)));
        assertTrue(FastMath.sin(5e6) != 0);
        assertTrue(FastMath.sin(2.0) != 0);
    }

    @Test
    public void testCos() throws Throwable {
        assertEquals(1.0, FastMath.cos(0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.cos(Double.NaN)));
        assertTrue(Double.isNaN(FastMath.cos(Double.POSITIVE_INFINITY)));
        assertTrue(FastMath.cos(5e6) != 0);
        assertTrue(FastMath.cos(2.0) != 0);
    }

    @Test
    public void testTan() throws Throwable {
        assertEquals(0.0, FastMath.tan(0.0), 1e-15);
        assertEquals(-0.0, FastMath.tan(-0.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.tan(Double.NaN)));
        assertTrue(Double.isNaN(FastMath.tan(Double.POSITIVE_INFINITY)));
        assertTrue(FastMath.tan(5e6) != 0);
        assertTrue(FastMath.tan(2.0) != 0);
        assertTrue(FastMath.tan(1.6) != 0);
    }

    @Test
    public void testAtan() throws Throwable {
        assertEquals(0.0, FastMath.atan(0.0), 1e-15);
        assertEquals(-0.0, FastMath.atan(-0.0), 1e-15);
        assertTrue(FastMath.atan(2e16) > 0);
        assertTrue(FastMath.atan(-2e16) < 0);
        assertTrue(FastMath.atan(0.5) > 0);
        assertTrue(FastMath.atan(2.0) > 0);
    }

    @Test
    public void testAtan2() throws Throwable {
        assertTrue(Double.isNaN(FastMath.atan2(Double.NaN, 1.0)));
        assertEquals(0.0, FastMath.atan2(0.0, 1.0), 1e-15);
        assertEquals(Math.PI, FastMath.atan2(0.0, -1.0), 1e-15);
        assertEquals(-Math.PI, FastMath.atan2(-0.0, -1.0), 1e-15);
        assertEquals(Math.PI * 0.5, FastMath.atan2(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY), 1e-15);
        assertEquals(Math.PI * 0.75, FastMath.atan2(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY), 1e-15);
        assertEquals(-Math.PI * 0.25, FastMath.atan2(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), 1e-15);
        assertEquals(-Math.PI * 0.75, FastMath.atan2(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY), 1e-15);
        assertEquals(0.0, FastMath.atan2(1.0, Double.POSITIVE_INFINITY), 1e-15);
        assertEquals(Math.PI, FastMath.atan2(1.0, Double.NEGATIVE_INFINITY), 1e-15);
        assertEquals(Math.PI * 0.5, FastMath.atan2(1.0, 0.0), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.atan2(2.0, 0.0), 1e-15); // ratio infinite
    }

    @Test
    public void testAsin() throws Throwable {
        assertTrue(Double.isNaN(FastMath.asin(Double.NaN)));
        assertTrue(Double.isNaN(FastMath.asin(2.0)));
        assertEquals(Math.PI / 2.0, FastMath.asin(1.0), 1e-15);
        assertEquals(-Math.PI / 2.0, FastMath.asin(-1.0), 1e-15);
        assertEquals(0.0, FastMath.asin(0.0), 1e-15);
        assertTrue(FastMath.asin(0.5) > 0);
    }

    @Test
    public void testAcos() throws Throwable {
        assertTrue(Double.isNaN(FastMath.acos(Double.NaN)));
        assertTrue(Double.isNaN(FastMath.acos(2.0)));
        assertEquals(Math.PI, FastMath.acos(-1.0), 1e-15);
        assertEquals(0.0, FastMath.acos(1.0), 1e-15);
        assertEquals(Math.PI / 2.0, FastMath.acos(0.0), 1e-15);
        assertTrue(FastMath.acos(0.5) > 0);
    }

    @Test
    public void testCbrt() throws Throwable {
        assertEquals(2.0, FastMath.cbrt(8.0), 1e-15);
        assertEquals(0.0, FastMath.cbrt(0.0), 1e-15);
        assertTrue(FastMath.cbrt(Double.POSITIVE_INFINITY) == Double.POSITIVE_INFINITY);
        assertTrue(FastMath.cbrt(1e-310) > 0); // subnormal
    }

    @Test
    public void testToRadiansDegrees() throws Throwable {
        assertEquals(0.0, FastMath.toRadians(0.0), 1e-15);
        assertTrue(Double.isInfinite(FastMath.toRadians(Double.POSITIVE_INFINITY)));
        assertEquals(0.0, FastMath.toDegrees(0.0), 1e-15);
        assertTrue(Double.isInfinite(FastMath.toDegrees(Double.POSITIVE_INFINITY)));
        assertTrue(FastMath.toRadians(180.0) > 0);
        assertTrue(FastMath.toDegrees(Math.PI) > 0);
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
        assertTrue(FastMath.ulp(Double.POSITIVE_INFINITY) == Double.POSITIVE_INFINITY);
        assertTrue(FastMath.ulp(1.0f) > 0);
        assertTrue(FastMath.ulp(Float.POSITIVE_INFINITY) == Float.POSITIVE_INFINITY);
    }

    @Test
    public void testScalb() throws Throwable {
        assertEquals(4.0, FastMath.scalb(1.0, 2), 1e-15);
        assertTrue(Double.isNaN(FastMath.scalb(Double.NaN, 2)));
        assertEquals(0.0, FastMath.scalb(1.0, -3000), 1e-15);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.scalb(1.0, 3000), 1e-15);
        assertTrue(FastMath.scalb(1.0, -1500) == 0.0);
        assertTrue(FastMath.scalb(0.0, 2) == 0.0);
        assertTrue(FastMath.scalb(1.0, 1500) == Double.POSITIVE_INFINITY);
        assertTrue(FastMath.scalb(Float.NaN, 2f) == Float.NaN);
        assertEquals(4.0f, FastMath.scalb(1.0f, 2), 1e-15f);
        assertEquals(0.0f, FastMath.scalb(1.0f, -500), 1e-15f);
        assertEquals(Float.POSITIVE_INFINITY, FastMath.scalb(1.0f, 500), 1e-15f);
        assertTrue(FastMath.scalb(1.0f, -300) == 0.0f);
        assertTrue(FastMath.scalb(0.0f, 2) == 0.0f);
        assertTrue(FastMath.scalb(1.0f, 300) == Float.POSITIVE_INFINITY);
    }

    @Test
    public void testNextAfter() throws Throwable {
        assertTrue(FastMath.nextAfter(1.0, 2.0) > 1.0);
        assertTrue(FastMath.nextAfter(1.0, 0.0) < 1.0);
        assertEquals(2.0, FastMath.nextAfter(2.0, 2.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.nextAfter(Double.NaN, 1.0)));
        assertTrue(Double.isNaN(FastMath.nextAfter(1.0, Double.NaN)));
        assertEquals(Double.MAX_VALUE, FastMath.nextAfter(Double.POSITIVE_INFINITY, 0.0), 1e-15);
        assertEquals(-Double.MAX_VALUE, FastMath.nextAfter(Double.NEGATIVE_INFINITY, 0.0), 1e-15);
        assertTrue(FastMath.nextAfter(0.0, 1.0) > 0);
        assertTrue(FastMath.nextAfter(0.0, -1.0) < 0);

        assertTrue(FastMath.nextAfter(1.0f, 2.0) > 1.0f);
        assertTrue(FastMath.nextAfter(1.0f, 0.0) < 1.0f);
        assertEquals(2.0f, FastMath.nextAfter(2.0f, 2.0), 1e-15f);
        assertTrue(Float.isNaN(FastMath.nextAfter(Float.NaN, 1.0)));
        assertTrue(Float.isNaN(FastMath.nextAfter(1.0f, Double.NaN)));
        assertEquals(Float.MAX_VALUE, FastMath.nextAfter(Float.POSITIVE_INFINITY, 0.0), 1e-15f);
        assertEquals(-Float.MAX_VALUE, FastMath.nextAfter(Float.NEGATIVE_INFINITY, 0.0), 1e-15f);
        assertTrue(FastMath.nextAfter(0.0f, 1.0) > 0);
        assertTrue(FastMath.nextAfter(0.0f, -1.0) < 0);
    }

    @Test
    public void testFloorCeilRintRound() throws Throwable {
        assertEquals(2.0, FastMath.floor(2.7), 1e-15);
        assertEquals(2.0, FastMath.ceil(2.3), 1e-15);
        assertEquals(2.0, FastMath.rint(2.3), 1e-15);
        assertEquals(3L, FastMath.round(2.7));
        assertEquals(3, FastMath.round(2.7f));
        assertTrue(Double.isNaN(FastMath.floor(Double.NaN)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.floor(Double.POSITIVE_INFINITY), 1e-15);
        assertEquals(2.0, FastMath.floor(-2.3), 1e-15); // -3.0
        assertTrue(Double.isNaN(FastMath.ceil(Double.NaN)));
        assertEquals(2.0, FastMath.rint(2.6), 1e-15);
        assertEquals(-0.0, FastMath.rint(-0.7), 1e-15);
        assertEquals(2.0, FastMath.rint(2.5), 1e-15); // round to even
    }

    @Test
    public void testMinMax() throws Throwable {
        assertEquals(1, FastMath.min(1, 2));
        assertEquals(2, FastMath.max(1, 2));
        assertEquals(1L, FastMath.min(1L, 2L));
        assertEquals(2L, FastMath.max(1L, 2L));
        assertEquals(1.0f, FastMath.min(1.0f, 2.0f), 1e-15f);
        assertEquals(2.0f, FastMath.max(1.0f, 2.0f), 1e-15f);
        assertTrue(Float.isNaN(FastMath.min(Float.NaN, 1.0f)));
        assertTrue(Float.isNaN(FastMath.max(Float.NaN, 1.0f)));
        assertEquals(-0.0f, FastMath.min(0.0f, -0.0f), 1e-15f);
        assertEquals(0.0f, FastMath.max(0.0f, -0.0f), 1e-15f);

        assertEquals(1.0, FastMath.min(1.0, 2.0), 1e-15);
        assertEquals(2.0, FastMath.max(1.0, 2.0), 1e-15);
        assertTrue(Double.isNaN(FastMath.min(Double.NaN, 1.0)));
        assertTrue(Double.isNaN(FastMath.max(Double.NaN, 1.0)));
        assertEquals(-0.0, FastMath.min(0.0, -0.0), 1e-15);
        assertEquals(0.0, FastMath.max(0.0, -0.0), 1e-15);
    }

    @Test
    public void testHypot() throws Throwable {
        assertEquals(5.0, FastMath.hypot(3.0, 4.0), 1e-15);
        assertTrue(FastMath.hypot(Double.POSITIVE_INFINITY, 1.0) == Double.POSITIVE_INFINITY);
        assertTrue(FastMath.hypot(Double.NaN, 1.0) == Double.NaN);
        assertEquals(1e30, FastMath.hypot(1e30, 1.0), 1e-15);
        assertEquals(1e30, FastMath.hypot(1.0, 1e30), 1e-15);
    }

    @Test
    public void testIEEEremainder() throws Throwable {
        assertEquals(0.0, FastMath.IEEEremainder(4.0, 2.0), 1e-15);
    }

    @Test
    public void testCopySign() throws Throwable {
        assertEquals(1.0, FastMath.copySign(1.0, -1.0), 1e-15);
        assertEquals(-1.0, FastMath.copySign(-1.0, 1.0), 1e-15);
        assertEquals(1.0f, FastMath.copySign(1.0f, -1.0f), 1e-15f);
        assertEquals(-1.0f, FastMath.copySign(-1.0f, 1.0f), 1e-15f);
    }

    @Test
    public void testGetExponent() throws Throwable {
        assertEquals(1, FastMath.getExponent(2.0));
        assertEquals(1, FastMath.getExponent(2.0f));
    }

    @Test
    public void testMain() throws Throwable {
        FastMath.main(new String[0]);
    }
}