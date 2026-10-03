package org.apache.commons.math3.dfp;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DfpTest {

    private DfpField field;
    private Dfp zero;
    private Dfp one;
    private Dfp two;

    @Before
    public void setUp() {
        field = new DfpField(20);
        zero = field.getZero();
        one = field.getOne();
        two = field.getTwo();
    }

    @Test
    public void testConstructorsAndBasicGetters() throws Throwable {
        Dfp dByte = new Dfp(field, (byte) 5);
        Dfp dInt = new Dfp(field, 10);
        Dfp dLong = new Dfp(field, 20L);
        Dfp dDouble = new Dfp(field, 3.14);
        Dfp dString = new Dfp(field, "123.456");
        Dfp dCopy = new Dfp(dString);

        assertEquals(20, dByte.getRadixDigits());
        assertNotNull(dByte.getField());
        assertFalse(dByte.isZero());
        assertTrue(dCopy.equals(dString));
        assertEquals(dString.hashCode(), dCopy.hashCode());

        Dfp minLongDfp = new Dfp(field, Long.MIN_VALUE);
        assertNotNull(minLongDfp);

        Dfp subnormalDfp = new Dfp(field, Double.longBitsToDouble(0x0001000000000000L));
        assertNotNull(subnormalDfp);

        Dfp posInfDfp = new Dfp(field, Double.POSITIVE_INFINITY);
        assertTrue(posInfDfp.isInfinite());

        Dfp negInfDfp = new Dfp(field, Double.NEGATIVE_INFINITY);
        assertTrue(negInfDfp.isInfinite());

        Dfp nanDfp = new Dfp(field, Double.NaN);
        assertTrue(nanDfp.isNaN());

        Dfp strInfPos = new Dfp(field, "Infinity");
        assertTrue(strInfPos.isInfinite());

        Dfp strInfNeg = new Dfp(field, "-Infinity");
        assertTrue(strInfNeg.isInfinite());

        Dfp strNan = new Dfp(field, "NaN");
        assertTrue(strNan.isNaN());

        Dfp sciString = new Dfp(field, "1.234e2");
        assertEquals(123.4, sciString.toDouble(), 1e-3);
    }

    @Test
    public void testNewInstanceMethods() throws Throwable {
        Dfp d1 = zero.newInstance();
        assertTrue(d1.isZero());

        Dfp d2 = zero.newInstance((byte) 2);
        Dfp d3 = zero.newInstance(3);
        Dfp d4 = zero.newInstance(4L);
        Dfp d5 = zero.newInstance(5.5);
        Dfp d6 = zero.newInstance("6.7");
        Dfp d7 = zero.newInstance(d2);
        Dfp d8 = zero.newInstance((byte) 1, Dfp.QNAN);

        assertNotNull(d3);
        assertNotNull(d4);
        assertNotNull(d5);
        assertNotNull(d6);
        assertNotNull(d7);
        assertTrue(d8.isNaN());

        DfpField otherField = new DfpField(10);
        Dfp otherDfp = new Dfp(otherField, 1);
        Dfp invalidCopy = zero.newInstance(otherDfp);
        assertTrue(invalidCopy.isNaN());
    }

    @Test
    public void testComparisonsAndSigns() throws Throwable {
        Dfp pos = new Dfp(field, 5);
        Dfp neg = new Dfp(field, -5);
        Dfp zeroVal = new Dfp(field, 0);

        assertTrue(neg.lessThan(pos));
        assertTrue(pos.greaterThan(neg));
        assertFalse(pos.lessThan(pos));
        assertFalse(pos.greaterThan(pos));

        assertTrue(neg.negativeOrNull());
        assertTrue(zeroVal.negativeOrNull());
        assertTrue(zeroVal.positiveOrNull());
        assertTrue(pos.positiveOrNull());

        assertTrue(neg.strictlyNegative());
        assertFalse(zeroVal.strictlyNegative());
        assertTrue(pos.strictlyPositive());
        assertFalse(zeroVal.strictlyPositive());

        assertTrue(pos.unequal(neg));
        assertFalse(pos.unequal(pos));

        Dfp nan = new Dfp(field, Double.NaN);
        assertFalse(pos.lessThan(nan));
        assertFalse(nan.lessThan(pos));
        assertFalse(pos.greaterThan(nan));
        assertFalse(nan.greaterThan(pos));
        assertFalse(pos.negativeOrNull());
        assertFalse(pos.strictlyNegative());
        assertFalse(pos.positiveOrNull());
        assertFalse(pos.strictlyPositive());
        assertFalse(pos.isZero());
    }

    @Test
    public void testArithmeticOperations() throws Throwable {
        Dfp a = new Dfp(field, 10);
        Dfp b = new Dfp(field, 3);

        Dfp sum = a.add(b);
        Dfp diff = a.subtract(b);
        Dfp prod = a.multiply(b);
        Dfp quot = a.divide(b);
        Dfp neg = a.negate();
        Dfp abs = neg.abs();

        assertEquals(13.0, sum.toDouble(), 1e-3);
        assertEquals(7.0, diff.toDouble(), 1e-3);
        assertEquals(30.0, prod.toDouble(), 1e-3);
        assertEquals(3.3333, quot.toDouble(), 1e-3);
        assertEquals(-10.0, neg.toDouble(), 1e-3);
        assertEquals(10.0, abs.toDouble(), 1e-3);

        Dfp fastMult = a.multiply(5);
        assertEquals(50.0, fastMult.toDouble(), 1e-3);

        Dfp fastDiv = a.divide(2);
        assertEquals(5.0, fastDiv.toDouble(), 1e-3);

        Dfp recip = a.reciprocal();
        assertEquals(0.1, recip.toDouble(), 1e-3);

        Dfp sqrtVal = new Dfp(field, 9);
        Dfp sqrtRes = sqrtVal.sqrt();
        assertEquals(3.0, sqrtRes.toDouble(), 1e-3);
    }

    @Test
    public void testSpecialArithmeticCases() throws Throwable {
        Dfp inf = new Dfp(field, Double.POSITIVE_INFINITY);
        Dfp negInf = new Dfp(field, Double.NEGATIVE_INFINITY);
        Dfp nan = new Dfp(field, Double.NaN);

        assertEquals(inf, inf.add(one));
        assertEquals(inf, one.add(inf));
        assertEquals(nan, inf.add(negInf));
        assertEquals(nan, nan.add(one));
        assertEquals(nan, one.add(nan));

        assertEquals(inf, inf.multiply(two));
        assertEquals(inf, two.multiply(inf));
        assertEquals(nan, inf.multiply(zero));

        assertEquals(zero, one.divide(inf));
        assertEquals(inf, inf.divide(one));
        assertEquals(nan, inf.divide(inf));

        Dfp divZero = one.divide(zero);
        assertTrue(divZero.isInfinite());

        Dfp zeroDivZero = zero.divide(zero);
        assertTrue(zeroDivZero.isNaN());

        Dfp negSqrt = new Dfp(field, -1).sqrt();
        assertTrue(negSqrt.isNaN());

        Dfp snan = new Dfp(field, (byte) 1, Dfp.SNAN);
        assertTrue(snan.sqrt().isNaN());

        Dfp diffRadix = new Dfp(new DfpField(10), 1);
        assertEquals(nan, one.add(diffRadix));
        assertEquals(nan, one.subtract(diffRadix)); // subtract uses add(negate)
        assertEquals(nan, one.multiply(diffRadix));
        assertEquals(nan, one.divide(diffRadix));
        assertFalse(one.lessThan(diffRadix));
        assertFalse(one.greaterThan(diffRadix));
        assertFalse(one.equals(diffRadix));
        assertFalse(one.unequal(diffRadix));
        assertNotNull(one.nextAfter(diffRadix));
    }

    @Test
    public void testRoundingAndConversion() throws Throwable {
        Dfp d = new Dfp(field, 3.7);
        assertEquals(4.0, d.rint().toDouble(), 1e-3);
        assertEquals(3.0, d.floor().toDouble(), 1e-3);
        assertEquals(4.0, d.ceil().toDouble(), 1e-3);

        Dfp negD = new Dfp(field, -3.7);
        assertEquals(-4.0, negD.floor().toDouble(), 1e-3);
        assertEquals(-3.0, negD.ceil().toDouble(), 1e-3);

        Dfp rem = new Dfp(field, 10).remainder(new Dfp(field, 3));
        assertEquals(1.0, rem.toDouble(), 1e-3);

        assertEquals(4, new Dfp(field, 4.2).intValue());
        assertEquals(Integer.MAX_VALUE, new Dfp(field, 3e9).intValue());
        assertEquals(Integer.MIN_VALUE, new Dfp(field, -3e9).intValue());

        assertEquals(1, new Dfp(field, 10000).log10K());
        assertEquals(10000.0, new Dfp(field, 1).power10K(1).toDouble(), 1e-3);
        assertEquals(3, new Dfp(field, 1000).log10());
        assertEquals(10.0, new Dfp(field, 1).power10(1).toDouble(), 1e-3);
        assertEquals(1.0, new Dfp(field, 1).power10(-1).toDouble(), 1e-3);
        assertEquals(100.0, new Dfp(field, 1).power10(2).toDouble(), 1e-3);
        assertEquals(1000.0, new Dfp(field, 1).power10(3).toDouble(), 1e-3);
    }

    @Test
    public void testNextAfterAndCopysign() throws Throwable {
        Dfp a = new Dfp(field, 5);
        Dfp b = new Dfp(field, 6);
        Dfp c = new Dfp(field, 5);

        Dfp next = a.nextAfter(b);
        assertTrue(next.greaterThan(a));

        Dfp prev = a.nextAfter(new Dfp(field, 4));
        assertTrue(prev.lessThan(a));

        assertEquals(a, a.nextAfter(c));

        Dfp negA = new Dfp(field, -5);
        Dfp nextNeg = negA.nextAfter(new Dfp(field, -6));
        assertNotNull(nextNeg);

        Dfp zeroVal = new Dfp(field, 0);
        assertNotNull(zeroVal.nextAfter(one));
        assertNotNull(one.nextAfter(zeroVal));

        Dfp copiedSign = Dfp.copysign(new Dfp(field, 5), new Dfp(field, -1));
        assertEquals(-5.0, copiedSign.toDouble(), 1e-3);
    }

    @Test
    public void testToStringAndSplitDouble() throws Throwable {
        Dfp d = new Dfp(field, 123456.789);
        assertNotNull(d.toString());

        Dfp largeExp = new Dfp(field, 1e20);
        assertNotNull(largeExp.toString());

        Dfp inf = new Dfp(field, Double.POSITIVE_INFINITY);
        assertEquals("Infinity", inf.toString());
        assertEquals("-Infinity", new Dfp(field, Double.NEGATIVE_INFINITY).toString());
        assertEquals("NaN", new Dfp(field, Double.NaN).toString());

        double[] split = d.toSplitDouble();
        assertEquals(2, split.length);

        double zeroDbl = zero.toDouble();
        assertEquals(0.0, zeroDbl, 1e-3);

        double negZeroDbl = new Dfp(field, -0.0).toDouble();
        assertTrue(Double.doubleToLongBits(negZeroDbl) == Double.doubleToLongBits(-0.0));

        double nanDbl = new Dfp(field, Double.NaN).toDouble();
        assertTrue(Double.isNaN(nanDbl));
    }

    @Test
    public void testClassifyAndTrap() throws Throwable {
        Dfp d = new Dfp(field, 5);
        assertEquals(Dfp.FINITE, d.classify());

        Dfp res = d.dotrap(DfpField.FLAG_INVALID, "test", d, d);
        assertNotNull(res);

        Dfp resDiv = d.dotrap(DfpField.FLAG_DIV_ZERO, "test", d, d);
        assertNotNull(resDiv);

        Dfp resUnder = d.dotrap(DfpField.FLAG_UNDERFLOW, "test", d, d);
        assertNotNull(resUnder);

        Dfp resOver = d.dotrap(DfpField.FLAG_OVERFLOW, "test", d, d);
        assertNotNull(resOver);
    }

    @Test
    public void testEdgeCasesRoundingAndAlign() throws Throwable {
        Dfp d = new Dfp(field, 1.23);
        d.exp = -35000;
        int roundRes = d.round(5000);
        assertEquals(DfpField.FLAG_UNDERFLOW, roundRes);

        d.exp = 35000;
        int roundResOver = d.round(0);
        assertEquals(DfpField.FLAG_OVERFLOW, roundResOver);

        Dfp alignD = new Dfp(field, 10);
        int lost = alignD.align(20);
        assertEquals(0, lost);

        Dfp multErr = new Dfp(field, 5).multiply(-1);
        assertNotNull(multErr);
    }
}