package org.apache.commons.lang3.math;

import org.junit.Test;
import static org.junit.Assert.*;

public class FractionClaudeTest {

    // getFraction(int,int): positive values are not reduced; zero numerator is not normalized
    @Test
    public void testGetFractionIntInt_PositiveUnreducedAndZeroNumeratorNotNormalized() throws Throwable {
        Fraction f1 = Fraction.getFraction(4, 2);
        assertEquals(4, f1.getNumerator());
        assertEquals(2, f1.getDenominator());
        Fraction f2 = Fraction.getFraction(0, 5);
        assertEquals(0, f2.getNumerator());
        assertEquals(5, f2.getDenominator());
    }

    // getFraction(int,int): negative denominator -> sign moved to numerator
    @Test
    public void testGetFractionIntInt_NegativeDenominatorResolvesSign() throws Throwable {
        Fraction f = Fraction.getFraction(1, -2);
        assertEquals(-1, f.getNumerator());
        assertEquals(2, f.getDenominator());
    }

    // getFraction(int,int): zero denominator and MIN_VALUE numerator with negative denominator both throw
    @Test
    public void testGetFractionIntInt_Exceptions() throws Throwable {
        try {
            Fraction.getFraction(5, 0);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
        try {
            Fraction.getFraction(Integer.MIN_VALUE, -1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // getFraction(whole,numer,denom): positive and negative whole number part
    @Test
    public void testGetFractionWholeNumerDenom_PositiveAndNegativeWhole() throws Throwable {
        Fraction f1 = Fraction.getFraction(1, 2, 3);
        assertEquals(5, f1.getNumerator());
        assertEquals(3, f1.getDenominator());
        Fraction f2 = Fraction.getFraction(-1, 2, 3);
        assertEquals(-5, f2.getNumerator());
        assertEquals(3, f2.getDenominator());
    }

    // getFraction(whole,numer,denom): zero denominator, negative denominator, negative numerator all throw
    @Test
    public void testGetFractionWholeNumerDenom_Exceptions() throws Throwable {
        try {
            Fraction.getFraction(1, 2, 0);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
        try {
            Fraction.getFraction(1, 2, -3);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
        try {
            Fraction.getFraction(1, -2, 3);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // getFraction(whole,numer,denom): resulting numerator overflow throws
    @Test
    public void testGetFractionWholeNumerDenom_Overflow_Throws() throws Throwable {
        try {
            Fraction.getFraction(Integer.MAX_VALUE, 1, 1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // getReducedFraction: reduces to lowest terms and normalizes zero numerator to ZERO
    @Test
    public void testGetReducedFraction_ReducesAndNormalizesZero() throws Throwable {
        Fraction f1 = Fraction.getReducedFraction(4, 2);
        assertEquals(2, f1.getNumerator());
        assertEquals(1, f1.getDenominator());
        Fraction f2 = Fraction.getReducedFraction(0, 5);
        assertEquals(Fraction.ZERO, f2);
    }

    // getReducedFraction: negative denominator resolves sign; zero denominator throws
    @Test
    public void testGetReducedFraction_NegativeDenominatorAndZeroDenominatorException() throws Throwable {
        Fraction f = Fraction.getReducedFraction(1, -2);
        assertEquals(-1, f.getNumerator());
        assertEquals(2, f.getDenominator());
        try {
            Fraction.getReducedFraction(1, 0);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // getReducedFraction: MIN_VALUE denominator with even numerator is handled without overflow
    @Test
    public void testGetReducedFraction_MinValueDenominatorEvenNumerator() throws Throwable {
        Fraction f = Fraction.getReducedFraction(2, Integer.MIN_VALUE);
        assertEquals(-1, f.getNumerator());
        assertEquals(1073741824, f.getDenominator());
    }

    // getFraction(double): simple positive and negative values convert to expected fraction
    @Test
    public void testGetFractionDouble_PositiveAndNegativeValues() throws Throwable {
        Fraction f1 = Fraction.getFraction(0.25d);
        assertEquals(1, f1.getNumerator());
        assertEquals(4, f1.getDenominator());
        Fraction f2 = Fraction.getFraction(-0.75d);
        assertEquals(-3, f2.getNumerator());
        assertEquals(4, f2.getDenominator());
    }

    // getFraction(double): NaN and values greater than Integer.MAX_VALUE both throw
    @Test
    public void testGetFractionDouble_Exceptions() throws Throwable {
        try {
            Fraction.getFraction(Double.NaN);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
        try {
            Fraction.getFraction(3.0e18d);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // getFraction(String): double format containing a dot
    @Test
    public void testGetFractionString_DoubleFormat() throws Throwable {
        Fraction f = Fraction.getFraction("1.5");
        assertEquals(3, f.getNumerator());
        assertEquals(2, f.getDenominator());
    }

    // getFraction(String): 'X Y/Z' format
    @Test
    public void testGetFractionString_WholeNumerDenomFormat() throws Throwable {
        Fraction f = Fraction.getFraction("1 2/3");
        assertEquals(5, f.getNumerator());
        assertEquals(3, f.getDenominator());
    }

    // getFraction(String): 'Y/Z' format and plain whole number format
    @Test
    public void testGetFractionString_NumerDenomAndWholeNumberFormat() throws Throwable {
        Fraction f1 = Fraction.getFraction("2/3");
        assertEquals(2, f1.getNumerator());
        assertEquals(3, f1.getDenominator());
        Fraction f2 = Fraction.getFraction("7");
        assertEquals(7, f2.getNumerator());
        assertEquals(1, f2.getDenominator());
    }

    // getFraction(String): null throws IllegalArgumentException, missing '/' in X Y/Z throws NumberFormatException
    @Test
    public void testGetFractionString_Exceptions() throws Throwable {
        try {
            Fraction.getFraction((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        try {
            Fraction.getFraction("1 2");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) { }
    }

    // getProperNumerator and getProperWhole: positive and negative improper fractions
    @Test
    public void testGetProperNumeratorAndWhole_PositiveAndNegative() throws Throwable {
        Fraction f1 = Fraction.getFraction(7, 4);
        assertEquals(3, f1.getProperNumerator());
        assertEquals(1, f1.getProperWhole());
        Fraction f2 = Fraction.getFraction(-7, 4);
        assertEquals(3, f2.getProperNumerator());
        assertEquals(-1, f2.getProperWhole());
    }

    // Number interface methods: intValue, longValue, floatValue, doubleValue
    @Test
    public void testNumberConversions_IntLongFloatDouble() throws Throwable {
        Fraction f = Fraction.getFraction(7, 4);
        assertEquals(1, f.intValue());
        assertEquals(1L, f.longValue());
        assertEquals(1.75f, f.floatValue(), 0.0001f);
        assertEquals(1.75d, f.doubleValue(), 0.0000001d);
    }

    // reduce(): reduces to lowest terms, returns same instance when already reduced
    @Test
    public void testReduce_ReducesAndKeepsAlreadyReduced() throws Throwable {
        Fraction f1 = Fraction.getFraction(4, 2).reduce();
        assertEquals(2, f1.getNumerator());
        assertEquals(1, f1.getDenominator());
        Fraction f2 = Fraction.getFraction(3, 4);
        assertSame(f2, f2.reduce());
    }

    // reduce(): zero numerator with non-trivial denominator normalizes to ZERO
    @Test
    public void testReduce_ZeroNumeratorNormalizesToZero() throws Throwable {
        Fraction f = Fraction.getFraction(0, 5).reduce();
        assertEquals(Fraction.ZERO, f);
    }

    // invert(): positive and negative fractions
    @Test
    public void testInvert_PositiveAndNegativeFraction() throws Throwable {
        Fraction f1 = Fraction.getFraction(3, 4).invert();
        assertEquals(4, f1.getNumerator());
        assertEquals(3, f1.getDenominator());
        Fraction f2 = Fraction.getFraction(-3, 4).invert();
        assertEquals(-4, f2.getNumerator());
        assertEquals(3, f2.getDenominator());
    }

    // invert(): zero numerator and MIN_VALUE numerator both throw
    @Test
    public void testInvert_Exceptions() throws Throwable {
        try {
            Fraction.ZERO.invert();
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
        try {
            Fraction.getFraction(Integer.MIN_VALUE, 1).invert();
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // negate(): normal case and MIN_VALUE overflow case
    @Test
    public void testNegate_NormalAndOverflow() throws Throwable {
        Fraction f = Fraction.getFraction(3, 4).negate();
        assertEquals(-3, f.getNumerator());
        assertEquals(4, f.getDenominator());
        try {
            Fraction.getFraction(Integer.MIN_VALUE, 1).negate();
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // abs(): positive returns same instance, negative returns positive equivalent
    @Test
    public void testAbs_PositiveAndNegative() throws Throwable {
        Fraction f1 = Fraction.getFraction(3, 4);
        assertSame(f1, f1.abs());
        Fraction f2 = Fraction.getFraction(-3, 4).abs();
        assertEquals(3, f2.getNumerator());
        assertEquals(4, f2.getDenominator());
    }

    // pow(): power 0 always returns ONE, power 1 returns same instance
    @Test
    public void testPow_ZeroAndOnePower() throws Throwable {
        Fraction f = Fraction.getFraction(3, 4);
        Fraction r0 = f.pow(0);
        assertEquals(1, r0.getNumerator());
        assertEquals(1, r0.getDenominator());
        assertSame(f, f.pow(1));
    }

    // pow(): positive power squares and reduces correctly
    @Test
    public void testPow_PositivePower() throws Throwable {
        Fraction f = Fraction.ONE_HALF.pow(2);
        assertEquals(1, f.getNumerator());
        assertEquals(4, f.getDenominator());
    }

    // pow(): negative power inverts the fraction
    @Test
    public void testPow_NegativePower() throws Throwable {
        Fraction f = Fraction.getFraction(3, 4).pow(-1);
        assertEquals(4, f.getNumerator());
        assertEquals(3, f.getDenominator());
    }

    // add(): both the gcd==1 fast path and the BigInteger path compute correct sums
    @Test
    public void testAdd_GcdOneAndBigIntegerPaths() throws Throwable {
        Fraction f1 = Fraction.ONE_HALF.add(Fraction.ONE_THIRD);
        assertEquals(5, f1.getNumerator());
        assertEquals(6, f1.getDenominator());
        Fraction f2 = Fraction.getFraction(1, 6).add(Fraction.getFraction(1, 4));
        assertEquals(5, f2.getNumerator());
        assertEquals(12, f2.getDenominator());
    }

    // add(): zero identity on both the receiver and the parameter side
    @Test
    public void testAdd_ZeroIdentityBothSides() throws Throwable {
        Fraction f1 = Fraction.ZERO.add(Fraction.ONE_THIRD);
        assertEquals(1, f1.getNumerator());
        assertEquals(3, f1.getDenominator());
        Fraction f2 = Fraction.ONE_THIRD.add(Fraction.getFraction(0, 5));
        assertEquals(1, f2.getNumerator());
        assertEquals(3, f2.getDenominator());
    }

    // add(null) throws IllegalArgumentException
    @Test
    public void testAdd_Null_ThrowsIllegalArgumentException() throws Throwable {
        try {
            Fraction.ONE_HALF.add(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // subtract(): BigInteger path result and the receiver-is-zero identity (negates parameter)
    @Test
    public void testSubtract_BigIntegerPathAndIdentityOnThis() throws Throwable {
        Fraction f1 = Fraction.getFraction(3, 4).subtract(Fraction.getFraction(1, 6));
        assertEquals(7, f1.getNumerator());
        assertEquals(12, f1.getDenominator());
        Fraction f2 = Fraction.ZERO.subtract(Fraction.ONE_THIRD);
        assertEquals(-1, f2.getNumerator());
        assertEquals(3, f2.getDenominator());
    }

    // subtract(): parameter-is-zero identity returns receiver unchanged; null throws
    @Test
    public void testSubtract_IdentityOnParameterAndNullThrows() throws Throwable {
        Fraction f = Fraction.ONE_THIRD.subtract(Fraction.getFraction(0, 5));
        assertEquals(1, f.getNumerator());
        assertEquals(3, f.getDenominator());
        try {
            Fraction.ONE_THIRD.subtract(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // multiplyBy(): normal product and zero-numerator short circuit
    @Test
    public void testMultiplyBy_NormalAndZero() throws Throwable {
        Fraction f1 = Fraction.ONE_HALF.multiplyBy(Fraction.TWO_THIRDS);
        assertEquals(1, f1.getNumerator());
        assertEquals(3, f1.getDenominator());
        Fraction f2 = Fraction.ZERO.multiplyBy(Fraction.ONE_HALF);
        assertEquals(Fraction.ZERO, f2);
    }

    // multiplyBy(null) throws IllegalArgumentException
    @Test
    public void testMultiplyBy_Null_Throws() throws Throwable {
        try {
            Fraction.ONE_HALF.multiplyBy(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // divideBy(): normal division and dividing by a zero fraction throws
    @Test
    public void testDivideBy_NormalAndByZeroThrows() throws Throwable {
        Fraction f = Fraction.ONE_HALF.divideBy(Fraction.ONE_THIRD);
        assertEquals(3, f.getNumerator());
        assertEquals(2, f.getDenominator());
        try {
            Fraction.ONE_HALF.divideBy(Fraction.ZERO);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // divideBy(null) throws IllegalArgumentException
    @Test
    public void testDivideBy_Null_Throws() throws Throwable {
        try {
            Fraction.ONE_HALF.divideBy(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // equals(): equal-value instances, unequal denominators (2/4 != 1/2), non-Fraction, and null
    @Test
    public void testEquals_VariousCases() throws Throwable {
        assertTrue(Fraction.getFraction(1, 2).equals(Fraction.getFraction(1, 2)));
        assertFalse(Fraction.getFraction(2, 4).equals(Fraction.getFraction(1, 2)));
        assertFalse(Fraction.getFraction(1, 2).equals("1/2"));
        assertFalse(Fraction.getFraction(1, 2).equals(null));
    }

    // hashCode(): equal fractions must have equal hash codes
    @Test
    public void testHashCode_ConsistentWithEquals() throws Throwable {
        assertEquals(Fraction.getFraction(1, 2).hashCode(), Fraction.getFraction(1, 2).hashCode());
    }

    // compareTo(): less than, greater than, and value-equal despite different representation
    @Test
    public void testCompareTo_LessGreaterAndEqualValue() throws Throwable {
        assertEquals(-1, Fraction.ONE_HALF.compareTo(Fraction.TWO_THIRDS));
        assertEquals(1, Fraction.TWO_THIRDS.compareTo(Fraction.ONE_HALF));
        assertEquals(0, Fraction.getFraction(1, 2).compareTo(Fraction.getFraction(2, 4)));
    }

    // compareTo(null) throws NullPointerException
    @Test
    public void testCompareTo_Null_ThrowsNullPointerException() throws Throwable {
        try {
            Fraction.ONE_HALF.compareTo(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) { }
    }

    // toString(): always 'numerator/denominator', even for integral or negative values
    @Test
    public void testToString_Format() throws Throwable {
        assertEquals("3/4", Fraction.getFraction(3, 4).toString());
        assertEquals("-3/4", Fraction.getFraction(-3, 4).toString());
        assertEquals("5/1", Fraction.getFraction(5, 1).toString());
    }

    // toProperString(): zero, one, and negative-one special cases
    @Test
    public void testToProperString_SpecialCases() throws Throwable {
        assertEquals("0", Fraction.ZERO.toProperString());
        assertEquals("1", Fraction.ONE.toProperString());
        assertEquals("-1", Fraction.getFraction(-1, 1).toProperString());
    }

    // toProperString(): proper fraction omits whole part, improper fractions show mixed numbers
    @Test
    public void testToProperString_ProperAndMixedNumbers() throws Throwable {
        assertEquals("3/4", Fraction.getFraction(3, 4).toProperString());
        assertEquals("1 3/4", Fraction.getFraction(7, 4).toProperString());
        assertEquals("-1 3/4", Fraction.getFraction(-7, 4).toProperString());
        assertEquals("2", Fraction.getFraction(8, 4).toProperString());
    }
}
