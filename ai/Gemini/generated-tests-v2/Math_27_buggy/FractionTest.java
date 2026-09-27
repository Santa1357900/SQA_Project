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

        assertEquals(2, Fraction.TWO_QUARTERS.getNumerator()); // reduces to 1/2
        assertEquals(1, Fraction.TWO_QUARTERS.getDenominator());

        assertEquals(2, Fraction.TWO_THIRDS.getNumerator());
        assertEquals(3, Fraction.TWO_THIRDS.getDenominator());

        assertEquals(-1, Fraction.MINUS_ONE.getNumerator());
        assertEquals(1, Fraction.MINUS_ONE.getDenominator());
    }

    @Test
    public void testConstructorsInts() throws Throwable {
        Fraction f1 = new Fraction(5);
        assertEquals(5, f1.getNumerator());
        assertEquals(1, f1.getDenominator());

        Fraction f2 = new Fraction(6, 8);
        assertEquals(3, f2.getNumerator());
        assertEquals(4, f2.getDenominator());

        Fraction f3 = new Fraction(-6, 8);
        assertEquals(-3, f3.getNumerator());
        assertEquals(4, f3.getDenominator());

        Fraction f4 = new Fraction(6, -8);
        assertEquals(-3, f4.getNumerator());
        assertEquals(4, f4.getDenominator());

        Fraction f5 = new Fraction(-6, -8);
        assertEquals(3, f5.getNumerator());
        assertEquals(4, f5.getDenominator());
    }

    @Test(expected = MathArithmeticException.class)
    public void testZeroDenominator() throws Throwable {
        new Fraction(1, 0);
    }

    @Test(expected = MathArithmeticException.class)
    public void testOverflowInConstructor() throws Throwable {
        new Fraction(Integer.MIN_VALUE, -1);
    }

    @Test
    public void testDoubleConstructor() throws Throwable {
        Fraction f1 = new Fraction(0.5);
        assertEquals(1, f1.getNumerator());
        assertEquals(2, f1.getDenominator());

        Fraction f2 = new Fraction(0.3333333, 100);
        assertEquals(1, f2.getNumerator());
        assertEquals(3, f2.getDenominator());

        Fraction f3 = new Fraction(0.5, 10);
        assertEquals(1, f3.getNumerator());
        assertEquals(2, f3.getDenominator());

        Fraction f4 = new Fraction(1.0, 1.0e-5, 10);
        assertEquals(1, f4.getNumerator());
        assertEquals(1, f4.getDenominator());
    }

    @Test(expected = FractionConversionException.class)
    public void testDoubleConstructorOverflow() throws Throwable {
        new Fraction(1e20, 1e-5, 10);
    }

    @Test(expected = FractionConversionException.class)
    public void testDoubleConstructorMaxIterations() throws Throwable {
        new Fraction(Math.PI, 1e-15, 1);
    }

    @Test
    public void testAbs() throws Throwable {
        Fraction f1 = new Fraction(-3, 4);
        Fraction f2 = f1.abs();
        assertEquals(3, f2.getNumerator());
        assertEquals(4, f2.getDenominator());

        Fraction f3 = new Fraction(3, 4);
        Fraction f4 = f3.abs();
        assertEquals(3, f4.getNumerator());
        assertEquals(4, f4.getDenominator());
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
    public void testNumericValues() throws Throwable {
        Fraction f = new Fraction(3, 4);
        assertEquals(0.75, f.doubleValue(), 1.0e-15);
        assertEquals(0.75f, f.floatValue(), 1.0e-15f);
        assertEquals(0, f.intValue());
        assertEquals(0L, f.longValue());
        assertEquals(75.0, f.percentageValue(), 1.0e-15);

        Fraction f2 = new Fraction(5, 2);
        assertEquals(2, f2.intValue());
        assertEquals(2L, f2.longValue());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(1, 2);
        Fraction f3 = new Fraction(1, 3);

        assertEquals(f1, f1);
        assertEquals(f1, f2);
        assertFalse(f1.equals(f3));
        assertFalse(f1.equals(null));
        assertFalse(f1.equals("NotAFraction"));

        assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    public void testNegate() throws Throwable {
        Fraction f1 = new Fraction(3, 4);
        Fraction f2 = f1.negate();
        assertEquals(-3, f2.getNumerator());
        assertEquals(4, f2.getDenominator());
    }

    @Test(expected = MathArithmeticException.class)
    public void testNegateOverflow() throws Throwable {
        Fraction f = new Fraction(Integer.MIN_VALUE, 1);
        f.negate();
    }

    @Test
    public void testReciprocal() throws Throwable {
        Fraction f1 = new Fraction(2, 3);
        Fraction f2 = f1.reciprocal();
        assertEquals(3, f2.getNumerator());
        assertEquals(2, f2.getDenominator());
    }

    @Test
    public void testAdd() throws Throwable {
        Fraction f1 = new Fraction(1, 3);
        Fraction f2 = new Fraction(1, 6);
        Fraction f3 = f1.add(f2);
        assertEquals(1, f3.getNumerator());
        assertEquals(2, f3.getDenominator());

        Fraction f4 = f1.add(1);
        assertEquals(4, f4.getNumerator());
        assertEquals(3, f4.getDenominator());

        Fraction zeroFrac = new Fraction(0, 1);
        assertEquals(f2, zeroFrac.add(f2));
        assertEquals(f1, f1.add(zeroFrac));

        Fraction f5 = new Fraction(1, 2);
        Fraction f6 = new Fraction(1, 2);
        Fraction f7 = f5.add(f6);
        assertEquals(1, f7.getNumerator());
        assertEquals(1, f7.getDenominator());
    }

    @Test(expected = NullArgumentException.class)
    public void testAddNull() throws Throwable {
        Fraction f = new Fraction(1, 2);
        f.add(null);
    }

    @Test(expected = MathArithmeticException.class)
    public void testAddOverflow() throws Throwable {
        Fraction f1 = new Fraction(Integer.MAX_VALUE - 1, 1);
        Fraction f2 = new Fraction(2, 1);
        f1.add(f2);
    }

    @Test
    public void testSubtract() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(1, 6);
        Fraction f3 = f1.subtract(f2);
        assertEquals(1, f3.getNumerator());
        assertEquals(3, f3.getDenominator());

        Fraction f4 = f1.subtract(1);
        assertEquals(-1, f4.getNumerator());
        assertEquals(2, f4.getDenominator());

        Fraction zeroFrac = new Fraction(0, 1);
        Fraction f5 = f1.subtract(zeroFrac);
        assertEquals(f1, f5);

        Fraction f6 = new Fraction(0, 1);
        Fraction f7 = f6.subtract(f1);
        assertEquals(-1, f7.getNumerator());
        assertEquals(2, f7.getDenominator());
    }

    @Test(expected = NullArgumentException.class)
    public void testSubtractNull() throws Throwable {
        Fraction f = new Fraction(1, 2);
        f.subtract(null);
    }

    @Test
    public void testMultiply() throws Throwable {
        Fraction f1 = new Fraction(2, 3);
        Fraction f2 = new Fraction(3, 4);
        Fraction f3 = f1.multiply(f2);
        assertEquals(1, f3.getNumerator());
        assertEquals(2, f3.getDenominator());

        Fraction f4 = f1.multiply(3);
        assertEquals(2, f4.getNumerator());
        assertEquals(1, f4.getDenominator());

        Fraction zero = Fraction.ZERO;
        assertEquals(zero, f1.multiply(zero));
        assertEquals(zero, zero.multiply(f1));
    }

    @Test(expected = NullArgumentException.class)
    public void testMultiplyNull() throws Throwable {
        Fraction f = new Fraction(1, 2);
        f.multiply(null);
    }

    @Test
    public void testDivide() throws Throwable {
        Fraction f1 = new Fraction(2, 3);
        Fraction f2 = new Fraction(3, 4);
        Fraction f3 = f1.divide(f2);
        assertEquals(8, f3.getNumerator());
        assertEquals(9, f3.getDenominator());

        Fraction f4 = f1.divide(2);
        assertEquals(1, f4.getNumerator());
        assertEquals(3, f4.getDenominator());
    }

    @Test(expected = NullArgumentException.class)
    public void testDivideNull() throws Throwable {
        Fraction f = new Fraction(1, 2);
        f.divide(null);
    }

    @Test(expected = MathArithmeticException.class)
    public void testDivideByZeroFraction() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = Fraction.ZERO;
        f1.divide(f2);
    }

    @Test
    public void testGetReducedFraction() throws Throwable {
        Fraction f1 = Fraction.getReducedFraction(2, 4);
        assertEquals(1, f1.getNumerator());
        assertEquals(2, f1.getDenominator());

        Fraction f2 = Fraction.getReducedFraction(0, 5);
        assertEquals(Fraction.ZERO, f2);

        Fraction f3 = Fraction.getReducedFraction(2, Integer.MIN_VALUE);
        assertEquals(-1, f3.getNumerator());
        assertEquals(1073741824, f3.getDenominator());

        Fraction f4 = Fraction.getReducedFraction(-2, -4);
        assertEquals(1, f4.getNumerator());
        assertEquals(2, f4.getDenominator());
    }

    @Test(expected = MathArithmeticException.class)
    public void testGetReducedFractionZeroDenom() throws Throwable {
        Fraction.getReducedFraction(1, 0);
    }

    @Test(expected = MathArithmeticException.class)
    public void testGetReducedFractionOverflow() throws Throwable {
        Fraction.getReducedFraction(Integer.MIN_VALUE, -1);
    }

    @Test
    public void testToString() throws Throwable {
        Fraction f1 = new Fraction(1, 1);
        assertEquals("1", f1.toString());

        Fraction f2 = new Fraction(0, 5);
        assertEquals("0", f2.toString());

        Fraction f3 = new Fraction(3, 4);
        assertEquals("3 / 4", f3.toString());
    }

    @Test
    public void testGetField() throws Throwable {
        Fraction f = new Fraction(1, 2);
        assertNotNull(f.getField());
        assertEquals(FractionField.getInstance(), f.getField());
    }
}