package org.apache.commons.math3.fraction;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.NullArgumentException;

public class FractionTest {

    @Test
    public void testConstants() throws Throwable {
        assertEquals(2, Fraction.TWO.getNumerator());
        assertEquals(1, Fraction.TWO.getDenominator());
        assertEquals(1, Fraction.ONE.getNumerator());
        assertEquals(1, Fraction.ONE.getDenominator());
        assertEquals(0, Fraction.ZERO.getNumerator());
        assertEquals(1, Fraction.ZERO.getDenominator());
        assertEquals(4, Fraction.FOUR_FIFTHS.getNumerator());
        assertEquals(5, Fraction.FOUR_FIFTHS.getDenominator());
        assertEquals(1, Fraction.ONE_FIFTH.getNumerator());
        assertEquals(5, Fraction.ONE_FIFTH.getDenominator());
        assertEquals(1, Fraction.ONE_HALF.getNumerator());
        assertEquals(2, Fraction.ONE_HALF.getDenominator());
        assertEquals(1, Fraction.ONE_QUARTER.getNumerator());
        assertEquals(4, Fraction.ONE_QUARTER.getDenominator());
        assertEquals(1, Fraction.ONE_THIRD.getNumerator());
        assertEquals(3, Fraction.ONE_THIRD.getDenominator());
        assertEquals(3, Fraction.THREE_FIFTHS.getNumerator());
        assertEquals(5, Fraction.THREE_FIFTHS.getDenominator());
        assertEquals(3, Fraction.THREE_QUARTERS.getNumerator());
        assertEquals(4, Fraction.THREE_QUARTERS.getDenominator());
        assertEquals(2, Fraction.TWO_FIFTHS.getNumerator());
        assertEquals(5, Fraction.TWO_FIFTHS.getDenominator());
        assertEquals(1, Fraction.TWO_QUARTERS.getNumerator());
        assertEquals(2, Fraction.TWO_QUARTERS.getDenominator());
        assertEquals(2, Fraction.TWO_THIRDS.getNumerator());
        assertEquals(3, Fraction.TWO_THIRDS.getDenominator());
        assertEquals(-1, Fraction.MINUS_ONE.getNumerator());
        assertEquals(1, Fraction.MINUS_ONE.getDenominator());
    }

    @Test
    public void testConstructorsInt() throws Throwable {
        Fraction f = new Fraction(5);
        assertEquals(5, f.getNumerator());
        assertEquals(1, f.getDenominator());
    }

    @Test
    public void testConstructorsIntInt() throws Throwable {
        Fraction f = new Fraction(2, 4);
        assertEquals(1, f.getNumerator());
        assertEquals(2, f.getDenominator());

        Fraction fNeg = new Fraction(2, -4);
        assertEquals(-1, fNeg.getNumerator());
        assertEquals(2, fNeg.getDenominator());

        Fraction fNeg2 = new Fraction(-2, -4);
        assertEquals(1, fNeg2.getNumerator());
        assertEquals(2, fNeg2.getDenominator());
    }

    @Test
    public void testZeroDenominator() throws Throwable {
        try {
            new Fraction(1, 0);
            fail("Expecting MathArithmeticException");
        } catch (MathArithmeticException e) {
            // expected
        }
    }

    @Test
    public void testOverflowInFraction() throws Throwable {
        try {
            new Fraction(Integer.MIN_VALUE, -1);
            fail("Expecting MathArithmeticException");
        } catch (MathArithmeticException e) {
            // expected
        }
    }

    @Test
    public void testConstructorsDouble() throws Throwable {
        Fraction f = new Fraction(0.5);
        assertEquals(1, f.getNumerator());
        assertEquals(2, f.getDenominator());

        Fraction f2 = new Fraction(0.3333333333, 10000);
        assertNotNull(f2);

        Fraction f3 = new Fraction(0.5, 1.0e-5, 100);
        assertEquals(1, f3.getNumerator());
        assertEquals(2, f3.getDenominator());

        Fraction f4 = new Fraction(0.3333333333, 1e-12, Integer.MAX_VALUE, 100);
        assertNotNull(f4);
    }

    @Test
    public void testConversionException() throws Throwable {
        try {
            new Fraction(Double.NaN, 1.0e-5, 10);
            fail("Expecting FractionConversionException");
        } catch (FractionConversionException e) {
            // expected
        } catch (IllegalArgumentException e) {
            // expected depending on implementation
        }
    }

    @Test
    public void testAbs() throws Throwable {
        Fraction f1 = new Fraction(-1, 2);
        Fraction f2 = f1.abs();
        assertEquals(1, f2.getNumerator());
        assertEquals(2, f2.getDenominator());

        Fraction f3 = new Fraction(1, 2);
        Fraction f4 = f3.abs();
        assertEquals(1, f4.getNumerator());
        assertEquals(2, f4.getDenominator());
    }

    @Test
    public void testCompareTo() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(1, 3);
        Fraction f3 = new Fraction(2, 4);

        assertTrue(f1.compareTo(f2) > 0);
        assertTrue(f2.compareTo(f1) < 0);
        assertEquals(0, f1.compareTo(f3));
    }

    @Test
    public void testDoubleValue() throws Throwable {
        Fraction f = new Fraction(1, 2);
        assertEquals(0.5, f.doubleValue(), 1.0e-15);
    }

    @Test
    public void testEquals() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(1, 2);
        Fraction f3 = new Fraction(1, 3);

        assertTrue(f1.equals(f1));
        assertTrue(f1.equals(f2));
        assertFalse(f1.equals(f3));
        assertFalse(f1.equals(null));
        assertFalse(f1.equals("Not a fraction"));
    }

    @Test
    public void testFloatValue() throws Throwable {
        Fraction f = new Fraction(1, 2);
        assertEquals(0.5f, f.floatValue(), 1.0e-15f);
    }

    @Test
    public void testHashCode() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(1, 2);
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    public void testIntValue() throws Throwable {
        Fraction f = new Fraction(3, 2);
        assertEquals(1, f.intValue());
    }

    @Test
    public void testLongValue() throws Throwable {
        Fraction f = new Fraction(3, 2);
        assertEquals(1L, f.longValue());
    }

    @Test
    public void testNegate() throws Throwable {
        Fraction f = new Fraction(1, 2);
        Fraction neg = f.negate();
        assertEquals(-1, neg.getNumerator());
        assertEquals(2, neg.getDenominator());

        try {
            new Fraction(Integer.MIN_VALUE, 1).negate();
            fail("Expecting MathArithmeticException");
        } catch (MathArithmeticException e) {
            // expected
        }
    }

    @Test
    public void testReciprocal() throws Throwable {
        Fraction f = new Fraction(2, 3);
        Fraction rec = f.reciprocal();
        assertEquals(3, rec.getNumerator());
        assertEquals(2, rec.getDenominator());
    }

    @Test
    public void testAddFraction() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(1, 3);
        Fraction sum = f1.add(f2);
        assertEquals(5, sum.getNumerator());
        assertEquals(6, sum.getDenominator());

        Fraction zeroFrac = new Fraction(0, 1);
        assertEquals(f2, zeroFrac.add(f2));
        assertEquals(f1, f1.add(zeroFrac));

        Fraction f3 = new Fraction(1, 6);
        Fraction f4 = new Fraction(1, 3);
        assertEquals(0, f3.add(new Fraction(-1, 6)).getNumerator());
        
        try {
            f1.add(null);
            fail("Expecting NullArgumentException");
        } catch (NullArgumentException e) {
            // expected
        }
    }

    @Test
    public void testAddInt() throws Throwable {
        Fraction f = new Fraction(1, 2);
        Fraction res = f.add(1);
        assertEquals(3, res.getNumerator());
        assertEquals(2, res.getDenominator());
    }

    @Test
    public void testSubtractFraction() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(1, 3);
        Fraction sub = f1.subtract(f2);
        assertEquals(1, sub.getNumerator());
        assertEquals(6, sub.getDenominator());

        assertEquals(f2, new Fraction(0, 1).subtract(f2.negate()));
        assertEquals(f1, f1.subtract(new Fraction(0, 1)));

        try {
            f1.subtract(null);
            fail("Expecting NullArgumentException");
        } catch (NullArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSubtractInt() throws Throwable {
        Fraction f = new Fraction(3, 2);
        Fraction res = f.subtract(1);
        assertEquals(1, res.getNumerator());
        assertEquals(2, res.getDenominator());
    }

    @Test
    public void testMultiplyFraction() throws Throwable {
        Fraction f1 = new Fraction(2, 3);
        Fraction f2 = new Fraction(3, 4);
        Fraction mul = f1.multiply(f2);
        assertEquals(1, mul.getNumerator());
        assertEquals(2, mul.getDenominator());

        assertEquals(Fraction.ZERO, f1.multiply(new Fraction(0, 1)));
        assertEquals(Fraction.ZERO, new Fraction(0, 1).multiply(f1));

        try {
            f1.multiply(null);
            fail("Expecting NullArgumentException");
        } catch (NullArgumentException e) {
            // expected
        }
    }

    @Test
    public void testMultiplyInt() throws Throwable {
        Fraction f = new Fraction(1, 3);
        Fraction res = f.multiply(3);
        assertEquals(1, res.getNumerator());
        assertEquals(1, res.getDenominator());
    }

    @Test
    public void testDivideFraction() throws Throwable {
        Fraction f1 = new Fraction(2, 3);
        Fraction f2 = new Fraction(4, 3);
        Fraction div = f1.divide(f2);
        assertEquals(1, div.getNumerator());
        assertEquals(2, div.getDenominator());

        try {
            f1.divide(null);
            fail("Expecting NullArgumentException");
        } catch (NullArgumentException e) {
            // expected
        }

        try {
            f1.divide(new Fraction(0, 1));
            fail("Expecting MathArithmeticException");
        } catch (MathArithmeticException e) {
            // expected
        }
    }

    @Test
    public void testDivideInt() throws Throwable {
        Fraction f = new Fraction(2, 3);
        Fraction res = f.divide(2);
        assertEquals(1, res.getNumerator());
        assertEquals(3, res.getDenominator());
    }

    @Test
    public void testPercentageValue() throws Throwable {
        Fraction f = new Fraction(1, 2);
        assertEquals(50.0, f.percentageValue(), 1.0e-15);
    }

    @Test
    public void testGetReducedFraction() throws Throwable {
        Fraction f = Fraction.getReducedFraction(2, 4);
        assertEquals(1, f.getNumerator());
        assertEquals(2, f.getDenominator());

        Fraction zero = Fraction.getReducedFraction(0, 5);
        assertEquals(0, zero.getNumerator());

        try {
            Fraction.getReducedFraction(1, 0);
            fail("Expecting MathArithmeticException");
        } catch (MathArithmeticException e) {
            // expected
        }

        Fraction minVal = Fraction.getReducedFraction(Integer.MIN_VALUE, 2);
        assertNotNull(minVal);

        try {
            Fraction.getReducedFraction(Integer.MIN_VALUE, -1);
            fail("Expecting MathArithmeticException");
        } catch (MathArithmeticException e) {
            // expected
        }
    }

    @Test
    public void testToString() throws Throwable {
        Fraction f1 = new Fraction(5, 1);
        assertEquals("5", f1.toString());

        Fraction f2 = new Fraction(0, 3);
        assertEquals("0", f2.toString());

        Fraction f3 = new Fraction(2, 3);
        assertEquals("2 / 3", f3.toString());
    }

    @Test
    public void testGetField() throws Throwable {
        Fraction f = new Fraction(1, 2);
        assertNotNull(f.getField());
    }
}