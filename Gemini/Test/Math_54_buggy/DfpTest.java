package org.apache.commons.math.dfp;

import org.junit.Test;
import static org.junit.Assert.*;

public class DfpTest {

    @Test
    public void testConstructorsAndBasicGetters() throws Throwable {
        DfpField field = new DfpField(20);
        Dfp d0 = field.getZero();
        Dfp d1 = field.getOne();
        Dfp d2 = field.getTwo();

        assertNotNull(d0);
        assertNotNull(d1);
        assertNotNull(d2);

        assertEquals(0, d0.classify());
        assertEquals(20, d0.getRadixDigits());
        assertEquals(field, d0.getField());

        Dfp dByte = new Dfp(field, (byte) 5);
        Dfp dInt = new Dfp(field, 1234);
        Dfp dLong = new Dfp(field, 123456789L);
        Dfp dLongMin = new Dfp(field, Long.MIN_VALUE);
        Dfp dDouble = new Dfp(field, 123.456);
        Dfp dDoubleSub = new Dfp(field, 1e-350);
        Dfp dDoubleInf = new Dfp(field, Double.POSITIVE_INFINITY);
        Dfp dDoubleNegInf = new Dfp(field, Double.NEGATIVE_INFINITY);
        Dfp dDoubleNaN = new Dfp(field, Double.NaN);

        assertNotNull(dByte);
        assertNotNull(dInt);
        assertNotNull(dLong);
        assertNotNull(dLongMin);
        assertNotNull(dDouble);
        assertNotNull(dDoubleSub);
        assertTrue(dDoubleInf.isInfinite());
        assertTrue(dDoubleNegInf.isInfinite());
        assertTrue(dDoubleNaN.isNaN());

        Dfp copy = new Dfp(dInt);
        assertEquals(dInt, copy);

        Dfp strInf = new Dfp(field, "Infinity");
        Dfp strNegInf = new Dfp(field, "-Infinity");
        Dfp strNaN = new Dfp(field, "NaN");
        Dfp strSci = new Dfp(field, "1.234e10");
        Dfp strDec = new Dfp(field, "0.00000");
        Dfp strDec2 = new Dfp(field, "123.45600");

        assertTrue(strInf.isInfinite());
        assertTrue(strNegInf.isInfinite());
        assertTrue(strNaN.isNaN());
        assertNotNull(strSci);
        assertNotNull(strDec);
        assertNotNull(strDec2);

        Dfp nonFinite = new Dfp(field, (byte) 1, Dfp.INFINITE);
        assertTrue(nonFinite.isInfinite());

        Dfp newInsDefault = d1.newInstance();
        Dfp newInsByte = d1.newInstance((byte) 1);
        Dfp newInsInt = d1.newInstance(1);
        Dfp newInsLong = d1.newInstance(1L);
        Dfp newInsDouble = d1.newInstance(1.0);
        Dfp newInsDfp = d1.newInstance(d1);
        Dfp newInsStr = d1.newInstance("1");
        Dfp newInsSigCode = d1.newInstance((byte) 1, Dfp.INFINITE);

        assertNotNull(newInsDefault);
        assertNotNull(newInsByte);
        assertNotNull(newInsInt);
        assertNotNull(newInsLong);
        assertNotNull(newInsDouble);
        assertNotNull(newInsDfp);
        assertNotNull(newInsStr);
        assertNotNull(newInsSigCode);

        DfpField fieldDiff = new DfpField(10);
        Dfp dDiffPrec = new Dfp(fieldDiff, 1.0);
        Dfp invalidNewIns = d1.newInstance(dDiffPrec);
        assertTrue(invalidNewIns.isNaN());
    }

    @Test
    public void testComparisonsAndEqualsAndHashCode() throws Throwable {
        DfpField field = new DfpField(20);
        Dfp a = new Dfp(field, 10);
        Dfp b = new Dfp(field, 20);
        Dfp c = new Dfp(field, 10);
        Dfp nan = new Dfp(field, Double.NaN);
        Dfp inf = new Dfp(field, Double.POSITIVE_INFINITY);
        DfpField fieldDiff = new DfpField(10);
        Dfp diffPrec = new Dfp(fieldDiff, 10);

        assertTrue(a.lessThan(b));
        assertFalse(b.lessThan(a));
        assertFalse(a.lessThan(nan));
        assertFalse(nan.lessThan(a));
        assertFalse(a.lessThan(diffPrec));

        assertTrue(b.greaterThan(a));
        assertFalse(a.greaterThan(b));
        assertFalse(a.greaterThan(nan));
        assertFalse(nan.greaterThan(a));
        assertFalse(a.greaterThan(diffPrec));

        assertTrue(a.equals(c));
        assertFalse(a.equals(b));
        assertFalse(a.equals("notADfp"));
        assertFalse(a.equals(nan));
        assertFalse(nan.equals(a));
        assertFalse(a.equals(diffPrec));

        assertTrue(a.unequal(b));
        assertFalse(a.unequal(c));
        assertFalse(a.unequal(nan));
        assertFalse(a.unequal(diffPrec));

        assertTrue(a.hashCode() != 0);
    }

    @Test
    public void testArithmeticOperations() throws Throwable {
        DfpField field = new DfpField(20);
        Dfp x = new Dfp(field, 10);
        Dfp y = new Dfp(field, 3);
        Dfp zero = field.getZero();
        Dfp one = field.getOne();
        Dfp inf = new Dfp(field, Double.POSITIVE_INFINITY);
        Dfp negInf = new Dfp(field, Double.NEGATIVE_INFINITY);
        Dfp nan = new Dfp(field, Double.NaN);
        DfpField fieldDiff = new DfpField(10);
        Dfp diffPrec = new Dfp(fieldDiff, 5);

        assertNotNull(x.add(y));
        assertNotNull(x.add(diffPrec));
        assertNotNull(nan.add(x));
        assertNotNull(x.add(nan));
        assertNotNull(inf.add(zero));
        assertNotNull(zero.add(inf));
        assertNotNull(inf.add(inf));
        assertNotNull(inf.add(negInf));

        Dfp zeroNeg = new Dfp(field, 0).negate();
        Dfp zeroNeg2 = new Dfp(field, 0).negate();
        assertNotNull(zeroNeg.add(zeroNeg2));

        Dfp large1 = new Dfp(field, 1e100);
        Dfp large2 = new Dfp(field, 1e100);
        assertNotNull(large1.add(large2));

        assertNotNull(x.subtract(y));
        assertNotNull(x.negate());

        assertNotNull(x.multiply(y));
        assertNotNull(x.multiply(2));
        assertNotNull(x.multiply(-1));
        assertNotNull(x.multiply(20000));
        assertNotNull(x.multiply(diffPrec));
        assertNotNull(nan.multiply(x));
        assertNotNull(x.multiply(nan));
        assertNotNull(inf.multiply(y));
        assertNotNull(y.multiply(inf));
        assertNotNull(inf.multiply(inf));
        assertNotNull(inf.multiply(zero));
        assertNotNull(zero.multiply(inf));

        assertNotNull(x.divide(y));
        assertNotNull(x.divide(3));
        assertNotNull(x.divide(0));
        assertNotNull(zero.divide(0));
        assertNotNull(inf.divide(0));
        assertNotNull(x.divide(-1));
        assertNotNull(x.divide(20000));
        assertNotNull(x.divide(diffPrec));
        assertNotNull(nan.divide(x));
        assertNotNull(x.divide(nan));
        assertNotNull(inf.divide(y));
        assertNotNull(y.divide(inf));
        assertNotNull(inf.divide(inf));

        Dfp neg = new Dfp(field, -10);
        assertNotNull(neg.sqrt());
        assertNotNull(inf.sqrt());
        assertNotNull(nan.sqrt());
        assertNotNull(zero.sqrt());
        Dfp s1 = new Dfp(field, 2);
        assertNotNull(s1.sqrt());
        Dfp s2 = new Dfp(field, 1e6);
        assertNotNull(s2.sqrt());
        Dfp s3 = new Dfp(field, 1e-6);
        assertNotNull(s3.sqrt());
    }

    @Test
    public void testRoundingAndConversion() throws Throwable {
        DfpField field = new DfpField(20);
        Dfp d = new Dfp(field, 123.456);
        Dfp negD = new Dfp(field, -123.456);
        Dfp zero = field.getZero();
        Dfp inf = new Dfp(field, Double.POSITIVE_INFINITY);
        Dfp nan = new Dfp(field, Double.NaN);
        Dfp smallExp = new Dfp(field, 1e-100);

        assertNotNull(d.rint());
        assertNotNull(d.floor());
        assertNotNull(d.ceil());
        assertNotNull(negD.floor());
        assertNotNull(negD.ceil());
        assertNotNull(nan.rint());
        assertNotNull(inf.rint());
        assertNotNull(zero.rint());
        assertNotNull(smallExp.rint());
        Dfp largeExp = new Dfp(field, 1e50);
        assertNotNull(largeExp.rint());

        Dfp halfEvenPos = new Dfp(field, "123.5");
        assertNotNull(halfEvenPos.rint());
        Dfp halfEvenOdd = new Dfp(field, "123.5");
        halfEvenOdd.exp = 1; 

        assertEquals(123456, d.intValue());
        Dfp veryLarge = new Dfp(field, 1e20);
        assertEquals(2147483647, veryLarge.intValue());
        Dfp verySmall = new Dfp(field, -1e20);
        assertEquals(-2147483648, verySmall.intValue());

        assertNotNull(d.remainder(new Dfp(field, 10)));
        assertEquals(3, d.log10K());
        assertNotNull(d.power10K(2));
        assertTrue(d.log10() >= 0);
        assertNotNull(d.power10(2));
        assertNotNull(d.power10(-2));
        assertNotNull(d.power10(1));
        assertNotNull(d.power10(2));
        assertNotNull(d.power10(3));

        assertEquals(123.456, d.toDouble(), 0.01);
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), inf.toDouble(), 0.01);
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), new Dfp(field, Double.NEGATIVE_INFINITY).toDouble(), 0.01);
        assertEquals(Double.valueOf(Double.NaN), nan.toDouble(), 0.01);
        Dfp negDouble = new Dfp(field, -123.456);
        assertEquals(-123.456, negDouble.toDouble(), 0.01);
        Dfp tinyDouble = new Dfp(field, 1e-1000);
        assertNotNull(tinyDouble.toDouble());
        Dfp hugeDouble = new Dfp(field, 1e1000);
        assertNotNull(hugeDouble.toDouble());

        assertNotNull(d.toSplitDouble());
    }

    @Test
    public void testUtilityAndStringAndNextAfter() throws Throwable {
        DfpField field = new DfpField(20);
        Dfp d = new Dfp(field, 123.456);
        Dfp zero = field.getZero();
        Dfp inf = new Dfp(field, Double.POSITIVE_INFINITY);
        Dfp negInf = new Dfp(field, Double.NEGATIVE_INFINITY);
        Dfp nan = new Dfp(field, Double.NaN);
        DfpField fieldDiff = new DfpField(10);
        Dfp diffPrec = new Dfp(fieldDiff, 1.0);

        assertNotNull(d.toString());
        assertNotNull(inf.toString());
        assertNotNull(negInf.toString());
        assertNotNull(nan.toString());
        Dfp sciStrDfp = new Dfp(field, 1e25);
        assertNotNull(sciStrDfp.toString());
        Dfp zeroStr = new Dfp(field, 0);
        assertNotNull(zeroStr.toString());

        assertNotNull(Dfp.copysign(d, new Dfp(field, -1)));

        assertNotNull(d.nextAfter(new Dfp(field, 200)));
        assertNotNull(d.nextAfter(new Dfp(field, 50)));
        assertNotNull(d.nextAfter(d));
        assertNotNull(zero.nextAfter(new Dfp(field, 1)));
        assertNotNull(new Dfp(field, -1).nextAfter(new Dfp(field, -2)));
        assertNotNull(d.nextAfter(diffPrec));

        Dfp alignTest = new Dfp(field, 123.456);
        assertNotNull(alignTest.align(-100));
        assertNotNull(alignTest.align(100));
        assertNotNull(alignTest.align(alignTest.exp));

        assertNotNull(d.dotrap(DfpField.FLAG_INVALID, "test", d, d));
        assertNotNull(d.dotrap(DfpField.FLAG_DIV_ZERO, "test", d, d));
        assertNotNull(d.dotrap(DfpField.FLAG_DIV_ZERO, "test", zero, d));
        assertNotNull(inf.dotrap(DfpField.FLAG_DIV_ZERO, "test", d, d));
        assertNotNull(new Dfp(field, (byte)1, Dfp.SNAN).dotrap(DfpField.FLAG_DIV_ZERO, "test", d, d));
        assertNotNull(d.dotrap(DfpField.FLAG_UNDERFLOW, "test", d, d));
        assertNotNull(d.dotrap(DfpField.FLAG_OVERFLOW, "test", d, d));
        assertNotNull(d.dotrap(999, "test", d, d));
    }
}