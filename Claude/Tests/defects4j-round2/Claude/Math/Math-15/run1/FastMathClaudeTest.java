package org.apache.commons.math3.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class FastMathClaudeTest {

    // sqrt: delegates to Math.sqrt, perfect square case
    @Test
    public void testSqrt_perfectSquare_matchesMathSqrt() throws Throwable {
        assertEquals(Math.sqrt(4.0), FastMath.sqrt(4.0), 1e-12);
    }

    // sqrt: negative input must produce NaN
    @Test
    public void testSqrt_negative_returnsNaN() throws Throwable {
        assertTrue(Double.isNaN(FastMath.sqrt(-1.0)));
    }

    // cosh: x!=x branch and x==0 trivial branch
    @Test
    public void testCosh_specialValues() throws Throwable {
        assertEquals(1.0, FastMath.cosh(0.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.cosh(Double.NaN)));
    }

    // cosh: x>20 branch, and x>=LOG_MAX_VALUE overflow-avoidance branch
    @Test
    public void testCosh_largeValue_matchesMathCoshAndOverflow() throws Throwable {
        double expected = Math.cosh(25.0);
        assertEquals(expected, FastMath.cosh(25.0), Math.abs(expected) * 1e-6);
        assertTrue(Double.isInfinite(FastMath.cosh(800.0)));
    }

    // sinh: x==0 branch and small negative value (expm1 path, x<0.25)
    @Test
    public void testSinh_zero_and_negativeSmall() throws Throwable {
        assertEquals(0.0, FastMath.sinh(0.0), 0.0);
        assertEquals(Math.sinh(-0.1), FastMath.sinh(-0.1), 1e-9);
    }

    // sinh: x<-20 branch (exp based, not overflow case)
    @Test
    public void testSinh_largeNegative_matchesMathSinh() throws Throwable {
        double expected = Math.sinh(-25.0);
        assertEquals(expected, FastMath.sinh(-25.0), Math.abs(expected) * 1e-6);
    }

    // tanh: x==0, x>20.0, x<-20 boundary branches
    @Test
    public void testTanh_zero_and_largeBoundaries() throws Throwable {
        assertEquals(0.0, FastMath.tanh(0.0), 0.0);
        assertEquals(1.0, FastMath.tanh(25.0), 0.0);
        assertEquals(-1.0, FastMath.tanh(-25.0), 0.0);
    }

    // tanh: x<0.5 expm1-based branch
    @Test
    public void testTanh_smallValue_matchesMathTanh() throws Throwable {
        assertEquals(Math.tanh(0.3), FastMath.tanh(0.3), 1e-9);
    }

    // acosh: a==1 gives log(1+sqrt(0))=0
    @Test
    public void testAcosh_one_returnsZero() throws Throwable {
        assertEquals(0.0, FastMath.acosh(1.0), 1e-9);
    }

    // asinh: negative branch flips sign (odd function property)
    @Test
    public void testAsinh_isOddFunction() throws Throwable {
        assertEquals(-FastMath.asinh(2.0), FastMath.asinh(-2.0), 1e-9);
    }

    // atanh: a>0.15 branch, matches closed form 0.5*ln((1+a)/(1-a))
    @Test
    public void testAtanh_half_matchesFormula() throws Throwable {
        double expected = 0.5 * Math.log(3.0);
        assertEquals(expected, FastMath.atanh(0.5), 1e-9);
    }

    // signum(double)/signum(float): negative, positive, NaN branches
    @Test
    public void testSignum_doubleAndFloat_values() throws Throwable {
        assertEquals(-1.0, FastMath.signum(-5.0), 0.0);
        assertEquals(1.0, FastMath.signum(5.0), 0.0);
        assertTrue(Double.isNaN(FastMath.signum(Double.NaN)));
        assertEquals(-1.0f, FastMath.signum(-5.0f), 0.0f);
        assertEquals(1.0f, FastMath.signum(5.0f), 0.0f);
    }

    // nextUp(double): delegates to nextAfter(a, +Infinity)
    @Test
    public void testNextUp_double_matchesBitIncrement() throws Throwable {
        long bits = Double.doubleToLongBits(1.0);
        double expected = Double.longBitsToDouble(bits + 1);
        assertEquals(expected, FastMath.nextUp(1.0), 0.0);
    }

    // exp: x==0 and x==1 branches
    @Test
    public void testExp_zeroAndOne_matchesMathExp() throws Throwable {
        assertEquals(1.0, FastMath.exp(0.0), 0.0);
        assertEquals(Math.exp(1.0), FastMath.exp(1.0), 1e-9);
    }

    // expm1: x==0 trivial branch and |x|<1 branch
    @Test
    public void testExpm1_zeroAndSmallValue() throws Throwable {
        assertEquals(0.0, FastMath.expm1(0.0), 0.0);
        assertEquals(Math.expm1(0.5), FastMath.expm1(0.5), 1e-9);
    }

    // log: x==1 quick-path, negative input NaN, x==0 negative infinity
    @Test
    public void testLog_specialValues() throws Throwable {
        assertEquals(0.0, FastMath.log(1.0), 1e-12);
        assertTrue(Double.isNaN(FastMath.log(-1.0)));
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.log(0.0), 0.0);
    }

    // log1p: x==-1 branch and x==0 branch
    @Test
    public void testLog1p_specialValues() throws Throwable {
        assertEquals(0.0, FastMath.log1p(0.0), 0.0);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.log1p(-1.0), 0.0);
    }

    // log10: standard positive value
    @Test
    public void testLog10_hundred_matchesMathLog10() throws Throwable {
        assertEquals(Math.log10(100.0), FastMath.log10(100.0), 1e-9);
    }

    // log(base,x): log(x)/log(base) formula
    @Test
    public void testLogBase_log2of8_returnsThree() throws Throwable {
        assertEquals(3.0, FastMath.log(2.0, 8.0), 1e-6);
    }

    // pow(double,double): y==0 branch and x!=x (NaN) branch
    @Test
    public void testPow_yZeroAndXNaN() throws Throwable {
        assertEquals(1.0, FastMath.pow(5.0, 0.0), 0.0);
        assertTrue(Double.isNaN(FastMath.pow(Double.NaN, 2.0)));
    }

    // pow: x==0 branch, positive and negative-zero signed infinite results
    @Test
    public void testPow_zeroBaseSignedInfiniteResults() throws Throwable {
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.pow(0.0, -2.0), 0.0);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), FastMath.pow(-0.0, -3.0), 0.0);
    }

    // pow: x<0 branch, integer exponent vs non-integer exponent (NaN)
    @Test
    public void testPow_negativeBase_integerVsNonInteger() throws Throwable {
        assertEquals(Math.pow(-2.0, 3.0), FastMath.pow(-2.0, 3.0), 1e-6);
        assertTrue(Double.isNaN(FastMath.pow(-2.0, 0.5)));
    }

    // pow(double,int): e==0 branch and e<0 branch
    @Test
    public void testPowInt_zeroAndNegativeExponent() throws Throwable {
        assertEquals(1.0, FastMath.pow(5.0, 0), 0.0);
        assertEquals(Math.pow(2.0, -2), FastMath.pow(2.0, -2), 1e-9);
    }

    // sin: xa==0.0 branch, preserving sign of zero
    @Test
    public void testSin_zeroSignPreservation() throws Throwable {
        assertEquals(0.0, FastMath.sin(0.0), 0.0);
        assertTrue(Double.doubleToRawLongBits(FastMath.sin(-0.0)) == Double.doubleToRawLongBits(-0.0));
    }

    // sin: CodyWaite reduction branch (xa between pi/2 and 3294198)
    @Test
    public void testSin_moderateAngle_matchesMathSin() throws Throwable {
        assertEquals(Math.sin(10.0), FastMath.sin(10.0), 1e-9);
    }

    // cos: quadrant 0 direct path
    @Test
    public void testCos_zero_returnsOne() throws Throwable {
        assertEquals(1.0, FastMath.cos(0.0), 1e-12);
    }

    // tan: CodyWaite reduction, quadrant 0 tanQ path
    @Test
    public void testTan_moderateAngle_matchesMathTan() throws Throwable {
        assertEquals(Math.tan(1.0), FastMath.tan(1.0), 1e-9);
    }

    // atan: xa<1 table index branch
    @Test
    public void testAtan_one_matchesMathAtan() throws Throwable {
        assertEquals(Math.atan(1.0), FastMath.atan(1.0), 1e-9);
    }

    // atan2: y==0 branch, x<0 case returns PI
    @Test
    public void testAtan2_zeroOverNegative_returnsPi() throws Throwable {
        assertEquals(Math.PI, FastMath.atan2(0.0, -1.0), 1e-12);
    }

    // asin/acos: exact boundary branches at +/-1
    @Test
    public void testAsinAcos_boundaryValues() throws Throwable {
        assertEquals(Math.PI / 2.0, FastMath.asin(1.0), 1e-12);
        assertEquals(-Math.PI / 2.0, FastMath.asin(-1.0), 1e-12);
        assertEquals(Math.PI, FastMath.acos(-1.0), 1e-12);
        assertEquals(0.0, FastMath.acos(1.0), 1e-12);
    }

    // cbrt: positive and negative perfect cubes
    @Test
    public void testCbrt_positiveAndNegative() throws Throwable {
        assertEquals(3.0, FastMath.cbrt(27.0), 1e-9);
        assertEquals(-2.0, FastMath.cbrt(-8.0), 1e-9);
    }

    // toRadians/toDegrees: standard conversion, non-zero non-infinite branch
    @Test
    public void testToRadiansAndToDegrees_roundTrip() throws Throwable {
        assertEquals(Math.PI, FastMath.toRadians(180.0), 1e-9);
        assertEquals(180.0, FastMath.toDegrees(Math.PI), 1e-9);
    }

    // abs(double)/abs(int): negative-zero normalization and negative int
    @Test
    public void testAbs_negativeZeroHandling() throws Throwable {
        assertTrue(Double.doubleToRawLongBits(FastMath.abs(-0.0)) == 0L);
        assertEquals(5, FastMath.abs(-5));
    }

    // ulp(double): standard IEEE754 unit in last place
    @Test
    public void testUlp_matchesMathUlp() throws Throwable {
        assertEquals(Math.ulp(1.0), FastMath.ulp(1.0), 0.0);
    }

    // scalb: fast-path branch, n within normal exponent range
    @Test
    public void testScalb_matchesMathScalb() throws Throwable {
        assertEquals(Math.scalb(1.5, 3), FastMath.scalb(1.5, 3), 0.0);
    }

    // nextAfter: direction greater than d, sign bit handling branch
    @Test
    public void testNextAfter_matchesBitIncrement() throws Throwable {
        long bits = Double.doubleToLongBits(1.0);
        double expected = Double.longBitsToDouble(bits + 1);
        assertEquals(expected, FastMath.nextAfter(1.0, 2.0), 0.0);
    }

    // floor/ceil: negative non-integer values
    @Test
    public void testFloorCeil_negativeValues() throws Throwable {
        assertEquals(-2.0, FastMath.floor(-1.5), 0.0);
        assertEquals(-1.0, FastMath.ceil(-1.5), 0.0);
    }

    // rint: tie cases rounding to nearest even integer
    @Test
    public void testRint_tiesRoundToEven() throws Throwable {
        assertEquals(2.0, FastMath.rint(2.5), 0.0);
        assertEquals(4.0, FastMath.rint(3.5), 0.0);
    }

    // round(double)/round(float): half rounds up via floor(x+0.5)
    @Test
    public void testRound_doubleAndFloat() throws Throwable {
        assertEquals(3L, FastMath.round(2.5));
        assertEquals(3, FastMath.round(2.5f));
    }

    // min/max(double): NaN propagation branch
    @Test
    public void testMinMax_nanPropagation() throws Throwable {
        assertTrue(Double.isNaN(FastMath.min(Double.NaN, 1.0)));
        assertTrue(Double.isNaN(FastMath.max(Double.NaN, 1.0)));
    }

    // min/max(double): signed-zero tie-breaking branch per javadoc comment
    @Test
    public void testMinMax_signedZeroHandling() throws Throwable {
        assertTrue(Double.doubleToRawLongBits(FastMath.min(0.0, -0.0)) == Double.doubleToRawLongBits(-0.0));
        assertTrue(Double.doubleToRawLongBits(FastMath.max(0.0, -0.0)) == Double.doubleToRawLongBits(0.0));
    }

    // hypot: normal scaled branch, and infinite-over-NaN precedence per javadoc
    @Test
    public void testHypot_basicAndInfinitePrecedence() throws Throwable {
        assertEquals(Math.hypot(3.0, 4.0), FastMath.hypot(3.0, 4.0), 1e-9);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), FastMath.hypot(Double.POSITIVE_INFINITY, Double.NaN), 0.0);
    }

    // IEEEremainder: delegates to StrictMath.IEEEremainder per javadoc
    @Test
    public void testIEEEremainder_matchesStrictMath() throws Throwable {
        assertEquals(StrictMath.IEEEremainder(5.0, 3.0), FastMath.IEEEremainder(5.0, 3.0), 0.0);
    }

    // copySign(double)/(float): sign differs, flip magnitude's sign
    @Test
    public void testCopySign_flipsSign() throws Throwable {
        assertEquals(-3.0, FastMath.copySign(3.0, -1.0), 0.0);
        assertEquals(-3.0f, FastMath.copySign(3.0f, -1.0f), 0.0f);
    }

    // getExponent(double)/(float): power-of-two exponent extraction
    @Test
    public void testGetExponent_powerOfTwo() throws Throwable {
        assertEquals(3, FastMath.getExponent(8.0));
        assertEquals(3, FastMath.getExponent(8.0f));
    }
}
