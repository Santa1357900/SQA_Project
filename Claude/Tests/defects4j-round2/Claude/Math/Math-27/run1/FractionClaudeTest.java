package org.apache.commons.math3.fraction;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.NullArgumentException;

public class FractionClaudeTest {

    // Fraction(int,int): gcd reduction branch (d>1)
    @Test
    public void testConstructorIntInt_reducesToLowestTerms() throws Throwable {
        Fraction f = new Fraction(4, 8);
        assertEquals(1, f.getNumerator());
        assertEquals(2, f.getDenominator());
    }

    // Fraction(int,int): den<0 branch moves sign to numerator
    @Test
    public void testConstructorIntInt_negativeDenominator_movesSignToNumerator() throws Throwable {
        Fraction f = new Fraction(3, -4);
        assertEquals(-3, f.getNumerator());
        assertEquals(4, f.getDenominator());
    }

    // Fraction(int,int): den==0 throws MathArithmeticException
    @Test
    public void testConstructorIntInt_zeroDenominator_throwsMathArithmeticException() throws Throwable {
        try {
            new Fraction(1, 0);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // Fraction(int,int): den<0 && num==MIN_VALUE overflow branch throws
    @Test
    public void testConstructorIntInt_minValueWithNegativeDenominator_throwsOverflow() throws Throwable {
        try {
            new Fraction(Integer.MIN_VALUE, -1);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // Fraction(int): single-arg constructor delegates to (num,1)
    @Test
    public void testConstructorInt_singleArgument() throws Throwable {
        Fraction f = new Fraction(5);
        assertEquals(5, f.getNumerator());
        assertEquals(1, f.getDenominator());
    }

    // Fraction(double): near-integer branch abs(a0-value)<epsilon
    @Test
    public void testConstructorDouble_integerValue_exactConversion() throws Throwable {
        Fraction f = new Fraction(2.0);
        assertEquals(2, f.getNumerator());
        assertEquals(1, f.getDenominator());
    }

    // Fraction(double): continued fraction converges to known constant
    @Test
    public void testConstructorDouble_fourFifths_matchesConstant() throws Throwable {
        Fraction f = new Fraction(0.8);
        assertEquals(Fraction.FOUR_FIFTHS, f);
    }

    // Fraction(double): positive a0 overflow throws FractionConversionException
    @Test
    public void testConstructorDouble_positiveOverflow_throwsFractionConversionException() throws Throwable {
        try {
            new Fraction(1.0e10);
            fail("expected FractionConversionException");
        } catch (FractionConversionException expected) {
        }
    }

    // BUG: negative a0 overflow is not checked (only a0 > overflow is checked),
    // so a value whose floor is far below Integer.MIN_VALUE should still be
    // reported as a conversion failure, by symmetry with the positive case.
    @Test
    public void testConstructorDouble_negativeOverflow_shouldThrowFractionConversionException() throws Throwable {
        try {
            new Fraction(-1.0e10);
            fail("expected FractionConversionException for numerator overflow");
        } catch (FractionConversionException expected) {
        }
    }

    // Fraction(double,epsilon,maxIterations): n>=maxIterations throws
    @Test
    public void testConstructorDoubleEpsilonMaxIterations_notConverging_throwsException() throws Throwable {
        try {
            new Fraction(Math.PI, 1.0e-20, 2);
            fail("expected FractionConversionException");
        } catch (FractionConversionException expected) {
        }
    }

    // Fraction(double,maxDenominator): a0 overflow throws regardless of maxDenominator
    @Test
    public void testConstructorDoubleMaxDenominator_overflow_throws() throws Throwable {
        try {
            new Fraction(1.0e10, 5);
            fail("expected FractionConversionException");
        } catch (FractionConversionException expected) {
        }
    }

    // Fraction(double,maxDenominator): approximates value within tolerance
    @Test
    public void testConstructorDoubleMaxDenominator_approximatesValue() throws Throwable {
        Fraction f = new Fraction(1.0 / 3.0, 10);
        assertEquals(1.0 / 3.0, f.doubleValue(), 1e-9);
    }

    // abs(): numerator>=0 returns equivalent, numerator<0 negates
    @Test
    public void testAbs_positiveAndNegativeNumerator() throws Throwable {
        Fraction pos = new Fraction(3, 4);
        assertEquals(pos, pos.abs());
        Fraction neg = new Fraction(-3, 4);
        assertEquals(new Fraction(3, 4), neg.abs());
    }

    // compareTo(): less, greater, equal branches
    @Test
    public void testCompareTo_lessGreaterEqual() throws Throwable {
        assertEquals(1, Fraction.ONE_HALF.compareTo(Fraction.ONE_THIRD));
        assertEquals(-1, Fraction.ONE_THIRD.compareTo(Fraction.ONE_HALF));
        assertEquals(0, Fraction.ONE_HALF.compareTo(Fraction.TWO_QUARTERS));
    }

    // doubleValue(): numerator / denominator
    @Test
    public void testDoubleValue() throws Throwable {
        assertEquals(0.75, new Fraction(3, 4).doubleValue(), 1e-9);
    }

    // equals(): same reference, equal reduced value, different type, null
    @Test
    public void testEquals_variousCases() throws Throwable {
        Fraction a = new Fraction(1, 2);
        assertTrue(a.equals(a));
        assertTrue(a.equals(Fraction.TWO_QUARTERS));
        assertFalse(a.equals("1/2"));
        assertFalse(a.equals(null));
    }

    // floatValue(): cast of doubleValue()
    @Test
    public void testFloatValue() throws Throwable {
        assertEquals(0.75f, new Fraction(3, 4).floatValue(), 1e-6f);
    }

    // getNumerator()/getDenominator() accessors
    @Test
    public void testGetDenominatorAndGetNumerator() throws Throwable {
        Fraction f = new Fraction(5, 7);
        assertEquals(5, f.getNumerator());
        assertEquals(7, f.getDenominator());
    }

    // hashCode(): equal fractions must have equal hash codes (Object contract)
    @Test
    public void testHashCode_equalObjectsHaveEqualHashCodes() throws Throwable {
        assertEquals(Fraction.ONE_HALF.hashCode(), Fraction.TWO_QUARTERS.hashCode());
    }

    // intValue()/longValue(): truncate toward zero for negative fractions
    @Test
    public void testIntValueAndLongValue_truncateTowardZero() throws Throwable {
        Fraction f = new Fraction(-7, 2);
        assertEquals(-3, f.intValue());
        assertEquals(-3L, f.longValue());
    }

    // negate(): normal path
    @Test
    public void testNegate_normal() throws Throwable {
        assertEquals(new Fraction(-3, 4), new Fraction(3, 4).negate());
    }

    // negate(): numerator==MIN_VALUE overflow throws
    @Test
    public void testNegate_overflow_throws() throws Throwable {
        try {
            new Fraction(Integer.MIN_VALUE, 1).negate();
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // reciprocal(): normal path
    @Test
    public void testReciprocal_normal() throws Throwable {
        assertEquals(new Fraction(4, 3), new Fraction(3, 4).reciprocal());
    }

    // reciprocal(): numerator==0 leads to zero denominator -> throws
    @Test
    public void testReciprocal_zeroNumerator_throws() throws Throwable {
        try {
            Fraction.ZERO.reciprocal();
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // add(Fraction): null argument throws NullArgumentException
    @Test
    public void testAdd_nullArgument_throwsNullArgumentException() throws Throwable {
        try {
            Fraction.ONE.add((Fraction) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // add(Fraction): zero-identity branches on both sides
    @Test
    public void testAdd_zeroIdentityBothSides() throws Throwable {
        assertEquals(Fraction.ONE_THIRD, Fraction.ZERO.add(Fraction.ONE_THIRD));
        assertEquals(Fraction.ONE_THIRD, Fraction.ONE_THIRD.add(Fraction.ZERO));
    }

    // add(Fraction): d1==1 (coprime denominators) branch
    @Test
    public void testAdd_coprimeDenominators() throws Throwable {
        assertEquals(new Fraction(5, 6), Fraction.ONE_HALF.add(Fraction.ONE_THIRD));
    }

    // add(Fraction): d1>1 BigInteger path
    @Test
    public void testAdd_commonFactorDenominators_bigIntegerPath() throws Throwable {
        assertEquals(new Fraction(5, 12), Fraction.ONE_QUARTER.add(new Fraction(1, 6)));
    }

    // add(int)
    @Test
    public void testAddInt() throws Throwable {
        assertEquals(new Fraction(5, 2), Fraction.ONE_HALF.add(2));
    }

    // subtract(Fraction): zero-identity with negation (isAdd=false)
    @Test
    public void testSubtract_zeroIdentityWithNegation() throws Throwable {
        assertEquals(new Fraction(-1, 3), Fraction.ZERO.subtract(Fraction.ONE_THIRD));
    }

    // subtract(int)
    @Test
    public void testSubtractInt() throws Throwable {
        assertEquals(Fraction.ONE_HALF, new Fraction(5, 2).subtract(2));
    }

    // multiply(Fraction): normal path and zero short-circuit
    @Test
    public void testMultiply_normalAndZeroShortCircuit() throws Throwable {
        assertEquals(Fraction.ONE_THIRD, Fraction.ONE_HALF.multiply(Fraction.TWO_THIRDS));
        assertEquals(Fraction.ZERO, Fraction.ZERO.multiply(Fraction.ONE_HALF));
    }

    // multiply(int)
    @Test
    public void testMultiplyInt() throws Throwable {
        assertEquals(Fraction.ONE, Fraction.ONE_THIRD.multiply(3));
    }

    // divide(Fraction): normal path
    @Test
    public void testDivide_normal() throws Throwable {
        assertEquals(new Fraction(3, 2), Fraction.ONE_HALF.divide(Fraction.ONE_THIRD));
    }

    // divide(Fraction): null argument and zero-fraction throw
    @Test
    public void testDivide_exceptions_nullAndZero() throws Throwable {
        try {
            Fraction.ONE_HALF.divide((Fraction) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
        try {
            Fraction.ONE_HALF.divide(Fraction.ZERO);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // divide(int)
    @Test
    public void testDivideInt() throws Throwable {
        assertEquals(Fraction.TWO_THIRDS, new Fraction(4, 3).divide(2));
    }

    // percentageValue(): multiply(100).doubleValue()
    @Test
    public void testPercentageValue() throws Throwable {
        assertEquals(50.0, Fraction.ONE_HALF.percentageValue(), 1e-9);
    }

    // getReducedFraction(): zero denominator throws; zero numerator returns ZERO
    @Test
    public void testGetReducedFraction_zeroDenominatorAndZeroNumerator() throws Throwable {
        try {
            Fraction.getReducedFraction(1, 0);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
        assertEquals(Fraction.ZERO, Fraction.getReducedFraction(0, 5));
    }

    // getReducedFraction(): normal reduction and negative denominator sign move
    @Test
    public void testGetReducedFraction_normalReductionAndNegativeDenominator() throws Throwable {
        assertEquals(Fraction.THREE_QUARTERS, Fraction.getReducedFraction(6, 8));
        Fraction neg = Fraction.getReducedFraction(3, -4);
        assertEquals(-3, neg.getNumerator());
        assertEquals(4, neg.getDenominator());
    }

    // getReducedFraction(): denominator==MIN_VALUE, odd numerator overflow throws;
    // even numerator takes the documented 2^k/-2^31 special case branch.
    @Test
    public void testGetReducedFraction_minValueDenominatorOddThrowsEvenSpecialCase() throws Throwable {
        try {
            Fraction.getReducedFraction(3, Integer.MIN_VALUE);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
        Fraction r = Fraction.getReducedFraction(2, Integer.MIN_VALUE);
        assertEquals(-1, r.getNumerator());
        assertEquals(1073741824, r.getDenominator());
    }

    // toString(): denominator==1 (whole number, including negative) and fraction form
    @Test
    public void testToString_wholeNumberAndFractionForm() throws Throwable {
        assertEquals("5", new Fraction(5, 1).toString());
        assertEquals("-5", new Fraction(-5, 1).toString());
        assertEquals("3 / 4", new Fraction(3, 4).toString());
    }

    // getField(): returns the FractionField singleton instance
    @Test
    public void testGetField_returnsSingletonInstance() throws Throwable {
        assertSame(FractionField.getInstance(), Fraction.ONE.getField());
    }
}
