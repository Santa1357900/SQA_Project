package org.apache.commons.math3.fraction;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.exception.ZeroException;

import org.junit.Assert;
import org.junit.Test;

public class BigFractionTest {

    @Test
    public void testConstructorsAndConstants() throws Throwable {
        Assert.assertEquals(BigFraction.TWO, new BigFraction(2));
        Assert.assertEquals(BigFraction.ONE, new BigFraction(1));
        Assert.assertEquals(BigFraction.ZERO, new BigFraction(0));
        Assert.assertEquals(BigFraction.MINUS_ONE, new BigFraction(-1));
        Assert.assertEquals(BigFraction.FOUR_FIFTHS, new BigFraction(4, 5));
        Assert.assertEquals(BigFraction.ONE_FIFTH, new BigFraction(1, 5));
        Assert.assertEquals(BigFraction.ONE_HALF, new BigFraction(1, 2));
        Assert.assertEquals(BigFraction.ONE_QUARTER, new BigFraction(1, 4));
        Assert.assertEquals(BigFraction.ONE_THIRD, new BigFraction(1, 3));
        Assert.assertEquals(BigFraction.THREE_FIFTHS, new BigFraction(3, 5));
        Assert.assertEquals(BigFraction.THREE_QUARTERS, new BigFraction(3, 4));
        Assert.assertEquals(BigFraction.TWO_FIFTHS, new BigFraction(2, 5));
        Assert.assertEquals(BigFraction.TWO_QUARTERS, new BigFraction(2, 4));
        Assert.assertEquals(BigFraction.TWO_THIRDS, new BigFraction(2, 3));

        BigFraction bfInt = new BigFraction(10);
        Assert.assertEquals(BigInteger.valueOf(10), bfInt.getNumerator());
        Assert.assertEquals(BigInteger.ONE, bfInt.getDenominator());

        BigFraction bfLong = new BigFraction(15L);
        Assert.assertEquals(BigInteger.valueOf(15), bfLong.getNumerator());
        Assert.assertEquals(BigInteger.ONE, bfLong.getDenominator());

        BigFraction bfLongLong = new BigFraction(10L, 20L);
        Assert.assertEquals(BigInteger.valueOf(1), bfLongLong.getNumerator());
        Assert.assertEquals(BigInteger.valueOf(2), bfLongLong.getDenominator());

        BigFraction bfBigInt = new BigFraction(BigInteger.valueOf(30));
        Assert.assertEquals(BigInteger.valueOf(30), bfBigInt.getNumerator());
        Assert.assertEquals(BigInteger.ONE, bfBigInt.getDenominator());

        BigFraction reducedNeg = new BigFraction(-4, -6);
        Assert.assertEquals(BigInteger.valueOf(2), reducedNeg.getNumerator());
        Assert.assertEquals(BigInteger.valueOf(3), reducedNeg.getDenominator());
        
        BigFraction zeroNum = new BigFraction(0, 5);
        Assert.assertEquals(BigInteger.ZERO, zeroNum.getNumerator());
        Assert.assertEquals(BigInteger.ONE, zeroNum.getDenominator());
    }

    @Test(expected = ZeroException.class)
    public void testZeroDenominatorBigInteger() throws Throwable {
        new BigFraction(BigInteger.ONE, BigInteger.ZERO);
    }

    @Test(expected = ZeroException.class)
    public void testZeroDenominatorInt() throws Throwable {
        new BigFraction(1, 0);
    }

    @Test(expected = NullArgumentException.class)
    public void testNullNumeratorBigInteger() throws Throwable {
        new BigFraction(null, BigInteger.ONE);
    }

    @Test(expected = NullArgumentException.class)
    public void testNullDenominatorBigInteger() throws Throwable {
        new BigFraction(BigInteger.ONE, null);
    }

    @Test
    public void testDoubleConstructor() throws Throwable {
        BigFraction bf = new BigFraction(0.5);
        Assert.assertEquals(BigFraction.ONE_HALF, bf);

        BigFraction bfNeg = new BigFraction(-0.5);
        Assert.assertEquals(new BigFraction(-1, 2), bfNeg);

        BigFraction bfZero = new BigFraction(0.0);
        Assert.assertEquals(BigFraction.ZERO, bfZero);

        BigFraction bfDenomNegExp = new BigFraction(1e-10);
        Assert.assertNotNull(bfDenomNegExp);
    }

    @Test(expected = MathIllegalArgumentException.class)
    public void testDoubleConstructorNaN() throws Throwable {
        new BigFraction(Double.NaN);
    }

    @Test(expected = MathIllegalArgumentException.class)
    public void testDoubleConstructorInfinite() throws Throwable {
        new BigFraction(Double.POSITIVE_INFINITY);
    }

    @Test
    public void testDoubleEpsilonMaxIterationsConstructor() throws Throwable {
        BigFraction bf = new BigFraction(0.333333, 1e-6, 10);
        Assert.assertNotNull(bf);

        BigFraction bfIntClose = new BigFraction(2.000001, 1e-3, 10);
        Assert.assertEquals(BigFraction.TWO, bfIntClose);
    }

    @Test(expected = FractionConversionException.class)
    public void testDoubleEpsilonMaxIterationsOverflow() throws Throwable {
        new BigFraction(1e20, 1e-6, 2);
    }

    @Test(expected = FractionConversionException.class)
    public void testDoubleEpsilonMaxIterationsExceeded() throws Throwable {
        new BigFraction(Math.PI, 1e-15, 2);
    }

    @Test
    public void testDoubleMaxDenominatorConstructor() throws Throwable {
        BigFraction bf = new BigFraction(0.333333, 100);
        Assert.assertNotNull(bf);
    }

    @Test
    public void testGetReducedFraction() throws Throwable {
        Assert.assertEquals(BigFraction.ZERO, BigFraction.getReducedFraction(0, 5));
        Assert.assertEquals(BigFraction.ONE_HALF, BigFraction.getReducedFraction(2, 4));
    }

    @Test
    public void testAbs() throws Throwable {
        BigFraction neg = new BigFraction(-3, 4);
        Assert.assertEquals(BigFraction.THREE_QUARTERS, neg.abs());
        Assert.assertEquals(BigFraction.THREE_QUARTERS, BigFraction.THREE_QUARTERS.abs());
    }

    @Test
    public void testAdd() throws Throwable {
        BigFraction f1 = BigFraction.ONE_HALF;
        BigFraction f2 = BigFraction.ONE_THIRD;
        Assert.assertEquals(new BigFraction(5, 6), f1.add(f2));
        Assert.assertEquals(new BigFraction(3, 2), f1.add(1));
        Assert.assertEquals(new BigFraction(3, 2), f1.add(1L));
        Assert.assertEquals(new BigFraction(3, 2), f1.add(BigInteger.ONE));
        Assert.assertEquals(f1, f1.add(BigFraction.ZERO));
        
        BigFraction f3 = new BigFraction(1, 2);
        BigFraction f4 = new BigFraction(3, 2);
        Assert.assertEquals(BigFraction.TWO, f3.add(f4));
    }

    @Test(expected = NullArgumentException.class)
    public void testAddNullBigFraction() throws Throwable {
        BigFraction.ONE_HALF.add((BigFraction) null);
    }

    @Test(expected = NullArgumentException.class)
    public void testAddNullBigInteger() throws Throwable {
        BigFraction.ONE_HALF.add((BigInteger) null);
    }

    @Test
    public void testBigDecimalValue() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF;
        Assert.assertEquals(0, new BigDecimal("0.5").compareTo(f.bigDecimalValue()));
        Assert.assertEquals(0, new BigDecimal("0.5").compareTo(f.bigDecimalValue(BigDecimal.ROUND_HALF_UP)));
        Assert.assertEquals(0, new BigDecimal("0.50").compareTo(f.bigDecimalValue(2, BigDecimal.ROUND_HALF_UP)));
    }

    @Test
    public void testCompareTo() throws Throwable {
        BigFraction f1 = BigFraction.ONE_HALF;
        BigFraction f2 = BigFraction.ONE_THIRD;
        BigFraction f3 = BigFraction.ONE_HALF;

        Assert.assertTrue(f1.compareTo(f2) > 0);
        Assert.assertTrue(f2.compareTo(f1) < 0);
        Assert.assertEquals(0, f1.compareTo(f3));
    }

    @Test
    public void testDivide() throws Throwable {
        BigFraction f1 = BigFraction.ONE_HALF;
        BigFraction f2 = BigFraction.ONE_THIRD;
        Assert.assertEquals(new BigFraction(3, 2), f1.divide(f2));
        Assert.assertEquals(new BigFraction(1, 4), f1.divide(2));
        Assert.assertEquals(new BigFraction(1, 4), f1.divide(2L));
        Assert.assertEquals(new BigFraction(1, 4), f1.divide(BigInteger.valueOf(2)));
    }

    @Test(expected = NullArgumentException.class)
    public void testDivideNullFraction() throws Throwable {
        BigFraction.ONE_HALF.divide((BigFraction) null);
    }

    @Test(expected = MathArithmeticException.class)
    public void testDivideZeroFraction() throws Throwable {
        BigFraction.ONE_HALF.divide(BigFraction.ZERO);
    }

    @Test(expected = NullArgumentException.class)
    public void testDivideNullBigInteger() throws Throwable {
        BigFraction.ONE_HALF.divide((BigInteger) null);
    }

    @Test(expected = MathArithmeticException.class)
    public void testDivideZeroBigInteger() throws Throwable {
        BigFraction.ONE_HALF.divide(BigInteger.ZERO);
    }

    @Test
    public void testNumericValuesAndConversions() throws Throwable {
        BigFraction f = new BigFraction(5, 2);
        Assert.assertEquals(2.5, f.doubleValue(), 1e-15);
        Assert.assertEquals(2.5f, f.floatValue(), 1e-7f);
        Assert.assertEquals(2, f.intValue());
        Assert.assertEquals(2L, f.longValue());
        Assert.assertEquals(250.0, f.percentageValue(), 1e-15);

        BigFraction large = new BigFraction(BigInteger.ONE.shiftLeft(2000), BigInteger.ONE);
        Assert.assertTrue(Double.isNaN(large.doubleValue()) || Double.isInfinite(large.doubleValue()) || large.doubleValue() > 0);
        Assert.assertTrue(Float.isNaN(large.floatValue()) || Float.isInfinite(large.floatValue()) || large.floatValue() > 0);

        BigFraction small = new BigFraction(1, 2);
        Assert.assertEquals(2, small.getDenominatorAsInt());
        Assert.assertEquals(2L, small.getDenominatorAsLong());
        Assert.assertEquals(1, small.getNumeratorAsInt());
        Assert.assertEquals(1L, small.getNumeratorAsLong());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        BigFraction f1 = new BigFraction(2, 4);
        BigFraction f2 = BigFraction.ONE_HALF;
        BigFraction f3 = BigFraction.ONE_THIRD;

        Assert.assertTrue(f1.equals(f1));
        Assert.assertTrue(f1.equals(f2));
        Assert.assertFalse(f1.equals(f3));
        Assert.assertFalse(f1.equals(null));
        Assert.assertFalse(f1.equals("NotABigFraction"));

        Assert.assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    public void testMultiply() throws Throwable {
        BigFraction f1 = BigFraction.ONE_HALF;
        BigFraction f2 = BigFraction.ONE_THIRD;
        Assert.assertEquals(new BigFraction(1, 6), f1.multiply(f2));
        Assert.assertEquals(BigFraction.ZERO, f1.multiply(BigFraction.ZERO));
        Assert.assertEquals(BigFraction.ZERO, BigFraction.ZERO.multiply(f1));
        Assert.assertEquals(BigFraction.ONE, f1.multiply(2));
        Assert.assertEquals(BigFraction.ONE, f1.multiply(2L));
        Assert.assertEquals(BigFraction.ONE, f1.multiply(BigInteger.valueOf(2)));
    }

    @Test(expected = NullArgumentException.class)
    public void testMultiplyNullFraction() throws Throwable {
        BigFraction.ONE_HALF.multiply((BigFraction) null);
    }

    @Test(expected = NullArgumentException.class)
    public void testMultiplyNullBigInteger() throws Throwable {
        BigFraction.ONE_HALF.multiply((BigInteger) null);
    }

    @Test
    public void testNegate() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF;
        Assert.assertEquals(new BigFraction(-1, 2), f.negate());
    }

    @Test
    public void testPow() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF;
        Assert.assertEquals(new BigFraction(1, 4), f.pow(2));
        Assert.assertEquals(new BigFraction(4, 1), f.pow(-2));
        Assert.assertEquals(new BigFraction(1, 4), f.pow(2L));
        Assert.assertEquals(new BigFraction(4, 1), f.pow(-2L));
        Assert.assertEquals(new BigFraction(1, 4), f.pow(BigInteger.valueOf(2)));
        Assert.assertEquals(new BigFraction(4, 1), f.pow(BigInteger.valueOf(-2)));
        Assert.assertEquals(0.25, f.pow(2.0), 1e-15);
    }

    @Test
    public void testReciprocal() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF;
        Assert.assertEquals(BigFraction.TWO, f.reciprocal());
    }

    @Test
    public void testReduce() throws Throwable {
        BigFraction f = new BigFraction(2, 4);
        Assert.assertEquals(BigFraction.ONE_HALF, f.reduce());
    }

    @Test
    public void testSubtract() throws Throwable {
        BigFraction f1 = BigFraction.ONE_HALF;
        BigFraction f2 = BigFraction.ONE_THIRD;
        Assert.assertEquals(new BigFraction(1, 6), f1.subtract(f2));
        Assert.assertEquals(new BigFraction(-1, 2), f1.subtract(1));
        Assert.assertEquals(new BigFraction(-1, 2), f1.subtract(1L));
        Assert.assertEquals(new BigFraction(-1, 2), f1.subtract(BigInteger.ONE));
        Assert.assertEquals(f1, f1.subtract(BigFraction.ZERO));

        BigFraction f3 = new BigFraction(3, 2);
        BigFraction f4 = new BigFraction(1, 2);
        Assert.assertEquals(BigFraction.ONE, f3.subtract(f4));
    }

    @Test(expected = NullArgumentException.class)
    public void testSubtractNullFraction() throws Throwable {
        BigFraction.ONE_HALF.subtract((BigFraction) null);
    }

    @Test(expected = NullArgumentException.class)
    public void testSubtractNullBigInteger() throws Throwable {
        BigFraction.ONE_HALF.subtract((BigInteger) null);
    }

    @Test
    public void testToString() throws Throwable {
        Assert.assertEquals("2", new BigFraction(2, 1).toString());
        Assert.assertEquals("0", new BigFraction(0, 5).toString());
        Assert.assertEquals("1 / 2", BigFraction.ONE_HALF.toString());
    }

    @Test
    public void testGetField() throws Throwable {
        Assert.assertNotNull(BigFraction.ONE_HALF.getField());
    }
}