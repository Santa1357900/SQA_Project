package org.apache.commons.math.util;

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
        assertEquals(2.0, FastMath.sqrt(4.0), 1e-12);
        assertEquals(0.0, FastMath.sqrt(0.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.sqrt(-1.0)));
    }

    @Test
    public void testCosh() throws Throwable {
        assertEquals(1.0, FastMath.cosh(0.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.cosh(Double.NaN)));
        assertTrue(FastMath.cosh(25.0) > 0);
        assertTrue(FastMath.cosh(-25.0) > 0);
    }

    @Test
    public void testSinh() throws Throwable {
        assertEquals(0.0, FastMath.sinh(0.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.sinh(Double.NaN)));
        assertTrue(FastMath.sinh(25.0) > 0);
        assertTrue(FastMath.sinh(-25.0) < 0);
        assertTrue(FastMath.sinh(0.1) != 0);
    }

    @Test
    public void testTanh() throws Throwable {
        assertEquals(0.0, FastMath.tanh(0.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.tanh(Double.NaN)));
        assertEquals(1.0, FastMath.tanh(25.0), 1e-12);
        assertEquals(-1.0, FastMath.tanh(-25.0), 1e-12);
        assertTrue(FastMath.tanh(0.2) != 0);
    }

    @Test
    public void testHyperbolicInverse() throws Throwable {
        assertTrue(Double.isNaN(FastMath.acosh(0.5)));
        assertEquals(0.0, FastMath.acosh(1.0), 1e-12);

        assertEquals(0.0, FastMath.asinh(0.0), 1e-12);
        assertTrue(FastMath.asinh(1.0) > 0);
        assertTrue(FastMath.asinh(-1.0) < 0);
        assertTrue(FastMath.asinh(0.05) != 0);
        assertTrue(FastMath.asinh(0.02) != 0);
        assertTrue(FastMath.asinh(0.001) != 0);

        assertEquals(0.0, FastMath.atanh(0.0), 1e-12);
        assertTrue(FastMath.atanh(0.5) > 0);
        assertTrue(FastMath.atanh(-0.5) < 0);
        assertTrue(FastMath.atanh(0.05) != 0);
        assertTrue(FastMath.atanh(0.02) != 0);
        assertTrue(FastMath.atanh(0.001) != 0);
    }

    @Test
    public void testSignum() throws Throwable {
        assertEquals(-1.0, FastMath.signum(-5.5), 1e-12);
        assertEquals(1.0, FastMath.signum(5.5), 1e-12);
        assertEquals(0.0, FastMath.signum(0.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.signum(Double.NaN)));
    }

    @Test
    public void testNextUp() throws Throwable {
        assertTrue(FastMath.nextUp(1.0) > 1.0);
    }

    @Test
    public void testRandom() throws Throwable {
        double r = FastMath.random();
        assertTrue(r >= 0.0 && r <= 1.0);
    }

    @Test
    public void testExp() throws Throwable {
        assertEquals(1.0, FastMath.exp(0.0), 1e-12);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.exp(800.0), 1e-12);
        assertEquals(0.0, FastMath.exp(-800.0), 1e-12);
        assertTrue(FastMath.exp(-720.0) >= 0.0);
        assertTrue(FastMath.exp(-750.0) == 0.0);
    }

    @Test
    public void testExpm1() throws Throwable {
        assertEquals(0.0, FastMath.expm1(0.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.expm1(Double.NaN)));
        assertTrue(FastMath.expm1(2.0) > 0);
        assertTrue(FastMath.expm1(-2.0) < 0);
    }

    @Test
    public void testLog() throws Throwable {
        assertEquals(0.0, FastMath.log(1.0), 1e-12);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.log(0.0), 1e-12);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.log(Double.POSITIVE_INFINITY), 1e-12);
        assertTrue(Double.isNaN(FastMath.log(-1.0)));
        assertTrue(Double.isNaN(FastMath.log(Double.NaN)));
        assertTrue(FastMath.log(1.005) != 0);
        assertTrue(FastMath.log(1e-350) != 0);
    }

    @Test
    public void testLog1p() throws Throwable {
        assertEquals(0.0, FastMath.log1p(0.0), 1e-12);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.log1p(-1.0), 1e-12);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.log1p(Double.POSITIVE_INFINITY), 1e-12);
        assertTrue(FastMath.log1p(0.1) > 0);
        assertTrue(FastMath.log1p(-0.5) < 0);
    }

    @Test
    public void testLog10() throws Throwable {
        assertEquals(1.0, FastMath.log10(10.0), 1e-12);
        assertEquals(0.0, FastMath.log10(1.0), 1e-12);
    }

    @Test
    public void testPow() throws Throwable {
        assertEquals(8.0, FastMath.pow(2.0, 3.0), 1e-12);
        assertEquals(1.0, FastMath.pow(5.0, 0.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.pow(Double.NaN, 2.0)));
        assertEquals(0.0, FastMath.pow(0.0, 2.0), 1e-12);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(0.0, -2.0), 1e-12);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(Double.POSITIVE_INFINITY, 2.0), 1e-12);
        assertEquals(0.0, FastMath.pow(Double.POSITIVE_INFINITY, -2.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.pow(1.0, Double.POSITIVE_INFINITY)));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(2.0, Double.POSITIVE_INFINITY), 1e-12);
        assertEquals(0.0, FastMath.pow(0.5, Double.POSITIVE_INFINITY), 1e-12);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.pow(Double.NEGATIVE_INFINITY, 3.0), 1e-12);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(Double.NEGATIVE_INFINITY, 2.0), 1e-12);
        assertEquals(0.0, FastMath.pow(Double.NEGATIVE_INFINITY, -3.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.pow(1.0, Double.NEGATIVE_INFINITY)));
        assertEquals(0.0, FastMath.pow(2.0, Double.NEGATIVE_INFINITY), 1e-12);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(0.5, Double.NEGATIVE_INFINITY), 1e-12);
        assertTrue(Double.isNaN(FastMath.pow(-2.0, 0.5)));
        assertEquals(-8.0, FastMath.pow(-2.0, 3.0), 1e-12);
        assertEquals(8.0, FastMath.pow(-2.0, 4.0), 1e-12);
        assertEquals(8.0, FastMath.pow(-2.0, 5e10), 1e-12);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.pow(-0.0, -3.0), 1e-12);
        assertEquals(-0.0, FastMath.pow(-0.0, -2.0), 1e-12);
        assertEquals(-0.0, FastMath.pow(-0.0, 3.0), 1e-12);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(-0.0, -1.0), 1e-12);
    }

    @Test
    public void testTrigonometricSinCosTan() throws Throwable {
        assertEquals(0.0, FastMath.sin(0.0), 1e-12);
        assertEquals(1.0, FastMath.cos(0.0), 1e-12);
        assertEquals(0.0, FastMath.tan(0.0), 1e-12);

        assertTrue(FastMath.sin(5e6) != 0);
        assertTrue(FastMath.cos(5e6) != 0);
        assertTrue(FastMath.tan(5e6) != 0);

        assertTrue(Double.isNaN(FastMath.sin(Double.NaN)));
        assertTrue(Double.isNaN(FastMath.cos(Double.NaN)));
        assertTrue(Double.isNaN(FastMath.tan(Double.NaN)));

        assertEquals(0.0, FastMath.sin(-0.0), 1e-12);
        assertTrue(Double.doubleToLongBits(FastMath.sin(-0.0)) < 0);

        assertEquals(0.0, FastMath.tan(-0.0), 1e-12);
        assertTrue(Double.doubleToLongBits(FastMath.tan(-0.0)) < 0);

        assertTrue(FastMath.tan(2.0) != 0);
    }

    @Test
    public void testAtan() throws Throwable {
        assertEquals(0.0, FastMath.atan(0.0), 1e-12);
        assertTrue(FastMath.atan(2e16) > 0);
        assertTrue(FastMath.atan(-2e16) < 0);
        assertTrue(FastMath.atan(2.0) != 0);
    }

    @Test
    public void testAtan2() throws Throwable {
        assertEquals(0.0, FastMath.atan2(0.0, 1.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.atan2(Double.NaN, 1.0)));
        assertEquals(0.0, FastMath.atan2(0.0, Double.POSITIVE_INFINITY), 1e-12);
        assertEquals(Math.PI, FastMath.atan2(0.0, -1.0), 1e-12);
        assertEquals(-Math.PI, FastMath.atan2(-0.0, -1.0), 1e-12);
        assertEquals(Math.PI / 4.0, FastMath.atan2(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY), 1e-12);
        assertEquals(Math.PI * 3.0 / 4.0, FastMath.atan2(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY), 1e-12);
        assertEquals(Math.PI / 2.0, FastMath.atan2(Double.POSITIVE_INFINITY, 1.0), 1e-12);
        assertEquals(-Math.PI / 4.0, FastMath.atan2(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), 1e-12);
        assertEquals(-Math.PI * 3.0 / 4.0, FastMath.atan2(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY), 1e-12);
        assertEquals(-Math.PI / 2.0, FastMath.atan2(Double.NEGATIVE_INFINITY, 1.0), 1e-12);
        assertEquals(0.0, FastMath.atan2(1.0, Double.POSITIVE_INFINITY), 1e-12);
        assertEquals(-0.0, FastMath.atan2(-1.0, Double.POSITIVE_INFINITY), 1e-12);
        assertEquals(Math.PI, FastMath.atan2(1.0, Double.NEGATIVE_INFINITY), 1e-12);
        assertEquals(-Math.PI, FastMath.atan2(-1.0, Double.NEGATIVE_INFINITY), 1e-12);
        assertEquals(Math.PI / 2.0, FastMath.atan2(1.0, 0.0), 1e-12);
        assertEquals(-Math.PI / 2.0, FastMath.atan2(-1.0, 0.0), 1e-12);
        assertTrue(FastMath.atan2(1.0, 1e300) != 0);
    }

    @Test
    public void testAsinAcos() throws Throwable {
        assertEquals(0.0, FastMath.asin(0.0), 1e-12);
        assertEquals(Math.PI / 2.0, FastMath.asin(1.0), 1e-12);
        assertEquals(-Math.PI / 2.0, FastMath.asin(-1.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.asin(2.0)));
        assertTrue(Double.isNaN(FastMath.asin(Double.NaN)));

        assertEquals(Math.PI / 2.0, FastMath.acos(0.0), 1e-12);
        assertEquals(0.0, FastMath.acos(1.0), 1e-12);
        assertEquals(Math.PI, FastMath.acos(-1.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.acos(2.0)));
        assertTrue(Double.isNaN(FastMath.acos(Double.NaN)));
        assertTrue(FastMath.acos(-0.5) != 0);
    }

    @Test
    public void testCbrt() throws Throwable {
        assertEquals(2.0, FastMath.cbrt(8.0), 1e-12);
        assertEquals(0.0, FastMath.cbrt(0.0), 1e-12);
        assertTrue(FastMath.cbrt(Double.NaN) != Double.NaN || Double.isNaN(FastMath.cbrt(Double.NaN)));
        assertTrue(FastMath.cbrt(1e-320) != 0);
    }

    @Test
    public void testConversions() throws Throwable {
        assertTrue(FastMath.toRadians(180.0) > 0);
        assertTrue(FastMath.toDegrees(Math.PI) > 0);
    }

    @Test
    public void testAbs() throws Throwable {
        assertEquals(5, FastMath.abs(-5));
        assertEquals(5, FastMath.abs(5));
        assertEquals(5L, FastMath.abs(-5L));
        assertEquals(5.0f, FastMath.abs(-5.0f), 1e-6f);
        assertEquals(5.0, FastMath.abs(-5.0), 1e-12);
    }

    @Test
    public void testUlp() throws Throwable {
        assertTrue(FastMath.ulp(1.0) > 0.0);
    }

    @Test
    public void testNextAfter() throws Throwable {
        assertTrue(FastMath.nextAfter(1.0, 2.0) > 1.0);
        assertTrue(FastMath.nextAfter(1.0, 0.0) < 1.0);
        assertEquals(0.0, FastMath.nextAfter(0.0, -1.0), 1e-16);
        assertEquals(Double.valueOf(Double.NaN), FastMath.nextAfter(Double.NaN, 1.0), 1e-16);
    }

    @Test
    public void testFloorCeilRintRound() throws Throwable {
        assertEquals(2.0, FastMath.floor(2.7), 1e-12);
        assertEquals(-3.0, FastMath.floor(-2.7), 1e-12);
        assertEquals(2.0, FastMath.floor(2.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.floor(Double.NaN)));
        assertEquals(5e15, FastMath.floor(5e15), 1e-12);

        assertEquals(3.0, FastMath.ceil(2.7), 1e-12);
        assertEquals(-2.0, FastMath.ceil(-2.7), 1e-12);
        assertEquals(2.0, FastMath.ceil(2.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.ceil(Double.NaN)));

        assertEquals(2.0, FastMath.rint(2.2), 1e-12);
        assertEquals(2.0, FastMath.rint(2.5), 1e-12);
        assertEquals(4.0, FastMath.rint(3.5), 1e-12);

        assertEquals(3L, FastMath.round(2.7));
        assertEquals(2, FastMath.round(2.3f));
    }

    @Test
    public void testMinMax() throws Throwable {
        assertEquals(2, FastMath.min(2, 3));
        assertEquals(3, FastMath.max(2, 3));
        assertEquals(2L, FastMath.min(2L, 3L));
        assertEquals(3L, FastMath.max(2L, 3L));
        assertEquals(2.0f, FastMath.min(2.0f, 3.0f), 1e-6f);
        assertEquals(3.0f, FastMath.max(2.0f, 3.0f), 1e-6f);
        assertEquals(2.0, FastMath.min(2.0, 3.0), 1e-12);
        assertEquals(3.0, FastMath.max(2.0, 3.0), 1e-12);

        assertTrue(Float.isNaN(FastMath.min(Float.NaN, 1.0f)));
        assertTrue(Float.isNaN(FastMath.max(Float.NaN, 1.0f)));
        assertTrue(Double.isNaN(FastMath.min(Double.NaN, 1.0)));
        assertTrue(Double.isNaN(FastMath.max(Double.NaN, 1.0)));
    }
}