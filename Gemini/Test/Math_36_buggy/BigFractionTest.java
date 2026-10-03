package org.apache.commons.math.fraction;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;
import static org.junit.Assert.*;

public class BigFractionTest {

    @Test
    public void testConstants() throws Throwable {
        assertEquals(BigFraction.TWO, new BigFraction(2));
        assertEquals(BigFraction.ONE, new BigFraction(1));
        assertEquals(BigFraction.ZERO, new BigFraction(0));
        assertEquals(BigFraction.MINUS_ONE, new BigFraction(-1));
        assertEquals(BigFraction.FOUR_FIFTHS, new BigFraction(4, 5));
        assertEquals(BigFraction.ONE_FIFTH, new BigFraction(1, 5));
        assertEquals(BigFraction.ONE_HALF, new BigFraction(1, 2));
        assertEquals(BigFraction.ONE_QUARTER, new BigFraction(1, 4));
        assertEquals(BigFraction.ONE_THIRD, new BigFraction(1, 3));
        assertEquals(BigFraction.THREE_FIFTHS, new BigFraction(3, 5));
        assertEquals(BigFraction.THREE_QUARTERS, new BigFraction(3, 4));
        assertEquals(BigFraction.TWO_FIFTHS, new BigFraction(2, 5));
        assertEquals(BigFraction.TWO_QUARTERS, new BigFraction(2, 4));
        assertEquals(BigFraction.TWO_THIRDS, new BigFraction(2, 3));
    }

    @Test
    public void testConstructors() throws Throwable {
        BigFraction f1 = new BigFraction(BigInteger.TEN);
        assertEquals(10, f1.getNumeratorAsInt());
        assertEquals(1, f1.getDenominatorAsInt());

        BigFraction f2 = new BigFraction(BigInteger.TEN, BigInteger.valueOf(2));
        assertEquals(5, f2.getNumeratorAsInt());
        assertEquals(1, f2.getDenominatorAsInt());

        BigFraction f3 = new BigFraction(BigInteger.ZERO, BigInteger.TEN);
        assertEquals(0, f3.getNumeratorAsInt());
        assertEquals(1, f3.getDenominatorAsInt());

        BigFraction f4 = new BigFraction(BigInteger.TEN, BigInteger.valueOf(-2));
        assertEquals(-5, f4.getNumeratorAsInt());
        assertEquals(1, f4.getDenominatorAsInt());

        BigFraction f5 = new BigFraction(10);
        assertEquals(10, f5.getNumeratorAsInt());

        BigFraction f6 = new BigFraction(10, 2);
        assertEquals(5, f6.getNumeratorAsInt());

        BigFraction f7 = new BigFraction(10L);
        assertEquals(10L, f7.getNumeratorAsLong());

        BigFraction f8 = new BigFraction(10L, 2L);
        assertEquals(5L, f8.getNumeratorAsLong());

        BigFraction f9 = new BigFraction(0.5);
        assertEquals(0.5, f9.doubleValue(), 1e-15);

        BigFraction f10 = new BigFraction(0.3, 10);
        assertNotNull(f10);

        BigFraction f11 = new BigFraction(0.3, 1e-5, 10);
        assertNotNull(f11);
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testConstructorNullNum() throws Throwable {
        new BigFraction(null, BigInteger.ONE);
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testConstructorNullDen() throws Throwable {
        new BigFraction(BigInteger.ONE, null);
    }

    @Test(expected = org.apache.commons.math.exception.ZeroException.class)
    public void testConstructorZeroDen() throws Throwable {
        new BigFraction(BigInteger.ONE, BigInteger.ZERO);
    }

    @Test(expected = org.apache.commons.math.exception.MathIllegalArgumentException.class)
    public void testConstructorNaN() throws Throwable {
        new BigFraction(Double.NaN);
    }

    @Test(expected = org.apache.commons.math.exception.MathIllegalArgumentException.class)
    public void testConstructorInfinite() throws Throwable {
        new BigFraction(Double.POSITIVE_INFINITY);
    }

    @Test(expected = FractionConversionException.class)
    public void testConstructorFractionConversionExceptionOverflow() throws Throwable {
        new BigFraction(1e20, 1e-15, 10);
    }

    @Test
    public void testGetReducedFraction() throws Throwable {
        BigFraction f = BigFraction.getReducedFraction(0, 5);
        assertEquals(BigFraction.ZERO, f);
        BigFraction f2 = BigFraction.getReducedFraction(2, 4);
        assertEquals(new BigFraction(1, 2), f2);
    }

    @Test
    public void testAbs() throws Throwable {
        BigFraction f = new BigFraction(-1, 2);
        assertEquals(new BigFraction(1, 2), f.abs());
        BigFraction f2 = new BigFraction(1, 2);
        assertEquals(new BigFraction(1, 2), f2.abs());
    }

    @Test
    public void testAdd() throws Throwable {
        BigFraction f = new BigFraction(1, 2);
        assertEquals(new BigFraction(3, 2), f.add(BigInteger.ONE));
        assertEquals(new BigFraction(3, 2), f.add(1));
        assertEquals(new BigFraction(3, 2), f.add(1L));
        assertEquals(new BigFraction(1), f.add(new BigFraction(1, 2)));
        assertEquals(f, f.add(BigFraction.ZERO));
        assertEquals(new BigFraction(1), f.add(new BigFraction(1, 2)));
        
        BigFraction f3 = new BigFraction(1, 3);
        BigFraction f4 = new BigFraction(1, 6);
        assertEquals(new BigFraction(1, 2), f3.add(f4));
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testAddNullBigInteger() throws Throwable {
        BigFraction.ONE.add((BigInteger) null);
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testAddNullBigFraction() throws Throwable {
        BigFraction.ONE.add((BigFraction) null);
    }

    @Test
    public void testBigDecimalValue() throws Throwable {
        BigFraction f = new BigFraction(1, 2);
        assertEquals(0, new BigDecimal("0.5").compareTo(f.bigDecimalValue()));
        assertEquals(0, new BigDecimal("0.5").compareTo(f.bigDecimalValue(BigDecimal.ROUND_HALF_UP)));
        assertEquals(0, new BigDecimal("0.5").compareTo(f.bigDecimalValue(1, BigDecimal.ROUND_HALF_UP)));
    }

    @Test
    public void testCompareTo() throws Throwable {
        BigFraction f1 = new BigFraction(1, 2);
        BigFraction f2 = new BigFraction(1, 3);
        BigFraction f3 = new BigFraction(2, 4);
        assertTrue(f1.compareTo(f2) > 0);
        assertTrue(f2.compareTo(f1) < 0);
        assertEquals(0, f1.compareTo(f3));
    }

    @Test
    public void testDivide() throws Throwable {
        BigFraction f = new BigFraction(1, 2);
        assertEquals(new BigFraction(1, 4), f.divide(BigInteger.valueOf(2)));
        assertEquals(new BigFraction(1, 4), f.divide(2));
        assertEquals(new BigFraction(1, 4), f.divide(2L));
        assertEquals(new BigFraction(1), f.divide(new BigFraction(1, 2)));
    }

    @Test(expected = org.apache.commons.math.exception.ZeroException.class)
    public void testDivideZeroBigInteger() throws Throwable {
        BigFraction.ONE.divide(BigInteger.ZERO);
    }

    @Test(expected = org.apache.commons.math.exception.ZeroException.class)
    public void testDivideZeroInt() throws Throwable {
        BigFraction.ONE.divide(0);
    }

    @Test(expected = org.apache.commons.math.exception.ZeroException.class)
    public void testDivideZeroLong() throws Throwable {
        BigFraction.ONE.divide(0L);
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testDivideNullFraction() throws Throwable {
        BigFraction.ONE.divide((BigFraction) null);
    }

    @Test(expected = org.apache.commons.math.exception.ZeroException.class)
    public void testDivideZeroFraction() throws Throwable {
        BigFraction.ONE.divide(BigFraction.ZERO);
    }

    @Test
    public void testDoubleValue() throws Throwable {
        assertEquals(0.5, new BigFraction(1, 2).doubleValue(), 1e-15);
    }

    @Test
    public void testEquals() throws Throwable {
        BigFraction f1 = new BigFraction(1, 2);
        BigFraction f2 = new BigFraction(2, 4);
        BigFraction f3 = new BigFraction(1, 3);
        
        assertTrue(f1.equals(f1));
        assertTrue(f1.equals(f2));
        assertFalse(f1.equals(f3));
        assertFalse(f1.equals(null));
        assertFalse(f1.equals("NotAFraction"));
    }

    @Test
    public void testFloatValue() throws Throwable {
        assertEquals(0.5f, new BigFraction(1, 2).floatValue(), 1e-15f);
    }

    @Test
    public void testGetters() throws Throwable {
        BigFraction f = new BigFraction(3, 4);
        assertEquals(BigInteger.valueOf(3), f.getNumerator());
        assertEquals(BigInteger.valueOf(4), f.getDenominator());
        assertEquals(3, f.getNumeratorAsInt());
        assertEquals(4, f.getDenominatorAsInt());
        assertEquals(3L, f.getNumeratorAsLong());
        assertEquals(4L, f.getDenominatorAsLong());
    }

    @Test
    public void testHashCode() throws Throwable {
        BigFraction f1 = new BigFraction(1, 2);
        BigFraction f2 = new BigFraction(1, 2);
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    public void testIntValue() throws Throwable {
        BigFraction f = new BigFraction(5, 2);
        assertEquals(2, f.intValue());
    }

    @Test
    public void testLongValue() throws Throwable {
        BigFraction f = new BigFraction(5, 2);
        assertEquals(2L, f.longValue());
    }

    @Test
    public void testMultiply() throws Throwable {
        BigFraction f = new BigFraction(1, 2);
        assertEquals(new BigFraction(1), f.multiply(BigInteger.valueOf(2)));
        assertEquals(new BigFraction(1), f.multiply(2));
        assertEquals(new BigFraction(1), f.multiply(2L));
        assertEquals(new BigFraction(1, 4), f.multiply(new BigFraction(1, 2)));
        assertEquals(BigFraction.ZERO, f.multiply(BigFraction.ZERO));
        assertEquals(BigFraction.ZERO, BigFraction.ZERO.multiply(f));
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testMultiplyNullBigInteger() throws Throwable {
        BigFraction.ONE.multiply((BigInteger) null);
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testMultiplyNullFraction() throws Throwable {
        BigFraction.ONE.multiply((BigFraction) null);
    }

    @Test
    public void testNegate() throws Throwable {
        BigFraction f = new BigFraction(1, 2);
        assertEquals(new BigFraction(-1, 2), f.negate());
    }

    @Test
    public void testPercentageValue() throws Throwable {
        BigFraction f = new BigFraction(1, 2);
        assertEquals(50.0, f.percentageValue(), 1e-15);
    }

    @Test
    public void testPow() throws Throwable {
        BigFraction f = new BigFraction(1, 2);
        assertEquals(new BigFraction(1, 4), f.pow(2));
        assertEquals(new BigFraction(4, 1), f.pow(-2));
        assertEquals(new BigFraction(1, 4), f.pow(2L));
        assertEquals(new BigFraction(4, 1), f.pow(-2L));
        assertEquals(new BigFraction(1, 4), f.pow(BigInteger.valueOf(2)));
        assertEquals(new BigFraction(4, 1), f.pow(BigInteger.valueOf(-2)));
        assertEquals(0.25, f.pow(2.0), 1e-15);
    }

    @Test
    public void testReciprocal() throws Throwable {
        BigFraction f = new BigFraction(2, 3);
        assertEquals(new BigFraction(3, 2), f.reciprocal());
    }

    @Test
    public void testReduce() throws Throwable {
        BigFraction f = new BigFraction(2, 4);
        assertEquals(new BigFraction(1, 2), f.reduce());
    }

    @Test
    public void testSubtract() throws Throwable {
        BigFraction f = new BigFraction(3, 2);
        assertEquals(new BigFraction(1, 2), f.subtract(BigInteger.ONE));
        assertEquals(new BigFraction(1, 2), f.subtract(1));
        assertEquals(new BigFraction(1, 2), f.subtract(1L));
        assertEquals(new BigFraction(1, 2), f.subtract(new BigFraction(1, 2)));
        assertEquals(f, f.subtract(BigFraction.ZERO));
        
        BigFraction f3 = new BigFraction(1, 2);
        BigFraction f4 = new BigFraction(1, 6);
        assertEquals(new BigFraction(1, 3), f3.subtract(f4));
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testSubtractNullBigInteger() throws Throwable {
        BigFraction.ONE.subtract((BigInteger) null);
    }

    @Test(expected = org.apache.commons.math.exception.NullArgumentException.class)
    public void testSubtractNullFraction() throws Throwable {
        BigFraction.ONE.subtract((BigFraction) null);
    }

    @Test
    public void testToString() throws Throwable {
        assertEquals("1", new BigFraction(1, 1).toString());
        assertEquals("0", new BigFraction(0, 5).toString());
        assertEquals("1 / 2", new BigFraction(1, 2).toString());
    }

    @Test
    public void testGetField() throws Throwable {
        assertNotNull(BigFraction.ONE.getField());
    }
}