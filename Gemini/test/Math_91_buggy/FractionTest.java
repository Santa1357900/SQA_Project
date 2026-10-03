package org.apache.commons.math.fraction;

import junit.framework.TestCase;
import java.math.BigInteger;

public class FractionTest extends TestCase {

    public void testConstructorsAndValues() throws Throwable {
        Fraction f1 = new Fraction(3, 4);
        assertEquals(3, f1.getNumerator());
        assertEquals(4, f1.getDenominator());
        assertEquals(0.75, f1.doubleValue(), 1e-15);
        assertEquals(0.75f, f1.floatValue(), 1e-15f);
        assertEquals(0, f1.intValue());
        assertEquals(0L, f1.longValue());

        Fraction fNeg = new Fraction(3, -4);
        assertEquals(-3, fNeg.getNumerator());
        assertEquals(4, fNeg.getDenominator());

        Fraction fNegNeg = new Fraction(-3, -4);
        assertEquals(3, fNegNeg.getNumerator());
        assertEquals(4, fNegNeg.getDenominator());

        Fraction fSingleInt = new Fraction(5);
        assertEquals(5, fSingleInt.getNumerator());
        assertEquals(1, fSingleInt.getDenominator());
    }

    public void testConstructorZeroDenominator() throws Throwable {
        try {
            new Fraction(1, 0);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }
    }

    public void testConstructorMinIntValue() throws Throwable {
        try {
            new Fraction(Integer.MIN_VALUE, -1);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }
        try {
            new Fraction(1, Integer.MIN_VALUE);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }
    }

    public void testDoubleConstructor() throws Throwable {
        Fraction f1 = new Fraction(0.75);
        assertEquals(3, f1.getNumerator());
        assertEquals(4, f1.getDenominator());

        Fraction f2 = new Fraction(0.3333333333, 1.0e-5, 100);
        assertNotNull(f2);

        Fraction f3 = new Fraction(0.3333333333, 100);
        assertNotNull(f3);

        Fraction fInt = new Fraction(2.0, 1e-5, 100);
        assertEquals(2, fInt.getNumerator());
        assertEquals(1, fInt.getDenominator());

        try {
            new Fraction(1e20, 1e-5, 2);
            fail("Expecting FractionConversionException");
        } catch (FractionConversionException e) {
            // expected
        }

        try {
            new Fraction(0.1, 1e-15, 2);
            fail("Expecting FractionConversionException");
        } catch (FractionConversionException e) {
            // expected
        }
    }

    public void testConstants() throws Throwable {
        assertEquals(2, Fraction.TWO.getNumerator());
        assertEquals(1, Fraction.TWO.getDenominator());
        assertEquals(1, Fraction.ONE.getNumerator());
        assertEquals(1, Fraction.ONE.getDenominator());
        assertEquals(0, Fraction.ZERO.getNumerator());
        assertEquals(1, Fraction.ZERO.getDenominator());
        assertEquals(-1, Fraction.MINUS_ONE.getNumerator());
        assertEquals(1, Fraction.MINUS_ONE.getDenominator());
    }

    public void testAbs() throws Throwable {
        Fraction f1 = new Fraction(-3, 4);
        Fraction f2 = f1.abs();
        assertEquals(3, f2.getNumerator());
        assertEquals(4, f2.getDenominator());

        Fraction f3 = new Fraction(3, 4);
        assertSame(f3, f3.abs());
    }

    public void testCompareTo() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(1, 3);
        Fraction f3 = new Fraction(2, 4);

        assertEquals(1, f1.compareTo(f2));
        assertEquals(-1, f2.compareTo(f1));
        assertEquals(0, f1.compareTo(f3));
    }

    public void testEqualsAndHashCode() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(1, 2);
        Fraction f3 = new Fraction(1, 3);

        assertTrue(f1.equals(f1));
        assertTrue(f1.equals(f2));
        assertFalse(f1.equals(f3));
        assertFalse(f1.equals(null));
        assertFalse(f1.equals("SomeString"));

        assertEquals(f1.hashCode(), f2.hashCode());
    }

    public void testNegate() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = f1.negate();
        assertEquals(-1, f2.getNumerator());
        assertEquals(2, f2.getDenominator());

        try {
            new Fraction(Integer.MIN_VALUE, 1).negate();
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }
    }

    public void testReciprocal() throws Throwable {
        Fraction f1 = new Fraction(2, 3);
        Fraction f2 = f1.reciprocal();
        assertEquals(3, f2.getNumerator());
        assertEquals(2, f2.getDenominator());
    }

    public void testAdd() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(1, 3);
        Fraction sum = f1.add(f2);
        assertEquals(5, sum.getNumerator());
        assertEquals(6, sum.getDenominator());

        assertEquals(f2, Fraction.ZERO.add(f2));
        assertEquals(f1, f1.add(Fraction.ZERO));
        
        Fraction fNegZero = new Fraction(0, 1);
        assertEquals(f2, fNegZero.add(f2));

        // Test gcd > 1 branch and BigInteger path
        Fraction f3 = new Fraction(1, 4);
        Fraction f4 = new Fraction(3, 4);
        assertEquals(Fraction.ONE, f3.add(f4));

        Fraction large1 = new Fraction(1, 2);
        try {
            large1.add(null);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testSubtract() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(1, 3);
        Fraction diff = f1.subtract(f2);
        assertEquals(1, diff.getNumerator());
        assertEquals(6, diff.getDenominator());

        assertEquals(f2.negate(), Fraction.ZERO.subtract(f2));
        assertEquals(f1, f1.subtract(Fraction.ZERO));

        Fraction f3 = new Fraction(3, 4);
        Fraction f4 = new Fraction(1, 4);
        assertEquals(f4, f3.subtract(f4));

        try {
            f1.subtract(null);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testMultiply() throws Throwable {
        Fraction f1 = new Fraction(2, 3);
        Fraction f2 = new Fraction(3, 4);
        Fraction prod = f1.multiply(f2);
        assertEquals(1, prod.getNumerator());
        assertEquals(2, prod.getDenominator());

        assertEquals(Fraction.ZERO, Fraction.ZERO.multiply(f2));
        assertEquals(Fraction.ZERO, f1.multiply(Fraction.ZERO));

        try {
            f1.multiply(null);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testDivide() throws Throwable {
        Fraction f1 = new Fraction(1, 2);
        Fraction f2 = new Fraction(3, 4);
        Fraction div = f1.divide(f2);
        assertEquals(2, div.getNumerator());
        assertEquals(3, div.getDenominator());

        try {
            f1.divide(Fraction.ZERO);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }

        try {
            f1.divide(null);
            fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testGetReducedFraction() throws Throwable {
        Fraction f = Fraction.getReducedFraction(2, 4);
        assertEquals(1, f.getNumerator());
        assertEquals(2, f.getDenominator());

        Fraction zero = Fraction.getReducedFraction(0, 5);
        assertEquals(Fraction.ZERO, zero);

        Fraction minValDen = Fraction.getReducedFraction(2, Integer.MIN_VALUE);
        assertNotNull(minValDen);

        try {
            Fraction.getReducedFraction(1, 0);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }

        try {
            Fraction.getReducedFraction(Integer.MIN_VALUE, -1);
            fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            // expected
        }
    }
}