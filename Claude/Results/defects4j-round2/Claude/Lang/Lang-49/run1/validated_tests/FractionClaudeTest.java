package org.apache.commons.lang.math;

import org.junit.Test;
import static org.junit.Assert.*;

public class FractionClaudeTest {

    // getFraction(int,int): normal construction and sign resolved to numerator when denominator negative
    @Test
    public void testGetFraction_intInt_positiveAndNegativeDenominatorSign() throws Throwable {
        Fraction a = Fraction.getFraction(3, 4);
        assertEquals(3, a.getNumerator());
        assertEquals(4, a.getDenominator());
        Fraction b = Fraction.getFraction(3, -4);
        assertEquals(-3, b.getNumerator());
        assertEquals(4, b.getDenominator());
    }

    // getFraction(int,int): denominator == 0 throws ArithmeticException
    @Test
    public void testGetFraction_intInt_zeroDenominatorThrows() throws Throwable {
        try {
            Fraction.getFraction(1, 0);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // getFraction(int,int): overflow when negating MIN_VALUE numerator with negative denominator
    @Test
    public void testGetFraction_intInt_overflowCantNegateThrows() throws Throwable {
        try {
            Fraction.getFraction(Integer.MIN_VALUE, -1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // getFraction(whole,num,denom): positive whole and negative whole branches
    @Test
    public void testGetFraction_wholeNumDenom_positiveAndNegativeWhole() throws Throwable {
        Fraction a = Fraction.getFraction(1, 3, 4);
        assertEquals(7, a.getNumerator());
        assertEquals(4, a.getDenominator());
        Fraction b = Fraction.getFraction(-1, 3, 4);
        assertEquals(-7, b.getNumerator());
        assertEquals(4, b.getDenominator());
    }

    // getFraction(whole,num,denom): denominator == 0 throws
    @Test
    public void testGetFraction_wholeNumDenom_zeroDenominatorThrows() throws Throwable {
        try {
            Fraction.getFraction(1, 2, 0);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // getFraction(whole,num,denom): negative denominator throws
    @Test
    public void testGetFraction_wholeNumDenom_negativeDenominatorThrows() throws Throwable {
        try {
            Fraction.getFraction(1, 2, -3);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // getFraction(whole,num,denom): negative numerator throws
    @Test
    public void testGetFraction_wholeNumDenom_negativeNumeratorThrows() throws Throwable {
        try {
            Fraction.getFraction(1, -2, 3);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // getFraction(whole,num,denom): resulting numerator overflow throws
    @Test
    public void testGetFraction_wholeNumDenom_numeratorOverflowThrows() throws Throwable {
        try {
            Fraction.getFraction(Integer.MAX_VALUE, Integer.MAX_VALUE, 2);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // getReducedFraction: numerator == 0 normalizes to the ZERO constant
    @Test
    public void testGetReducedFraction_zeroNumeratorReturnsZero() throws Throwable {
        Fraction r = Fraction.getReducedFraction(0, 5);
        assertSame(Fraction.ZERO, r);
    }

    // getReducedFraction: reduces to lowest terms via gcd
    @Test
    public void testGetReducedFraction_reducesToLowestTerms() throws Throwable {
        Fraction r = Fraction.getReducedFraction(4, 8);
        assertEquals(1, r.getNumerator());
        assertEquals(2, r.getDenominator());
    }

    // getReducedFraction: negative denominator sign resolved to numerator
    @Test
    public void testGetReducedFraction_negativeDenominatorResolvesSign() throws Throwable {
        Fraction r = Fraction.getReducedFraction(1, -2);
        assertEquals(-1, r.getNumerator());
        assertEquals(2, r.getDenominator());
    }

    // getReducedFraction: denominator == 0 throws
    @Test
    public void testGetReducedFraction_zeroDenominatorThrows() throws Throwable {
        try {
            Fraction.getReducedFraction(1, 0);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // getReducedFraction: overflow can't negate MIN_VALUE numerator
    @Test
    public void testGetReducedFraction_overflowCantNegateThrows() throws Throwable {
        try {
            Fraction.getReducedFraction(Integer.MIN_VALUE, -1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // getFraction(double): simple 0.5 and -0.5 via continued fraction algorithm
    @Test
    public void testGetFraction_double_halfAndNegativeHalf() throws Throwable {
        Fraction a = Fraction.getFraction(0.5);
        assertEquals(1, a.getNumerator());
        assertEquals(2, a.getDenominator());
        Fraction b = Fraction.getFraction(-0.5);
        assertEquals(-1, b.getNumerator());
        assertEquals(2, b.getDenominator());
    }

    // getFraction(double): exact whole number value
    @Test
    public void testGetFraction_double_wholeNumber() throws Throwable {
        Fraction c = Fraction.getFraction(3.0);
        assertEquals(3, c.getNumerator());
        assertEquals(1, c.getDenominator());
    }

    // getFraction(double): NaN throws ArithmeticException
    @Test
    public void testGetFraction_double_NaNThrows() throws Throwable {
        try {
            Fraction.getFraction(Double.NaN);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // getFraction(double): value greater than Integer.MAX_VALUE throws
    @Test
    public void testGetFraction_double_tooLargeThrows() throws Throwable {
        double tooLarge = ((double) Integer.MAX_VALUE) + 1.0;
        try {
            Fraction.getFraction(tooLarge);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // getFraction(String): null throws IllegalArgumentException
    @Test
    public void testGetFraction_string_nullThrows() throws Throwable {
        try {
            Fraction.getFraction((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getFraction(String): dot format delegates to double parsing
    @Test
    public void testGetFraction_string_doubleFormat() throws Throwable {
        Fraction r = Fraction.getFraction("0.5");
        assertEquals(1, r.getNumerator());
        assertEquals(2, r.getDenominator());
    }

    // getFraction(String): plain whole number format
    @Test
    public void testGetFraction_string_wholeNumberFormat() throws Throwable {
        Fraction r = Fraction.getFraction("5");
        assertEquals(5, r.getNumerator());
        assertEquals(1, r.getDenominator());
    }

    // getFraction(String): Y/Z format
    @Test
    public void testGetFraction_string_slashFormat() throws Throwable {
        Fraction r = Fraction.getFraction("3/4");
        assertEquals(3, r.getNumerator());
        assertEquals(4, r.getDenominator());
    }

    // getFraction(String): X Y/Z mixed format
    @Test
    public void testGetFraction_string_mixedFormat() throws Throwable {
        Fraction r = Fraction.getFraction("1 3/4");
        assertEquals(7, r.getNumerator());
        assertEquals(4, r.getDenominator());
    }

    // getFraction(String): X Y format missing slash throws NumberFormatException
    @Test
    public void testGetFraction_string_mixedFormatMissingSlashThrows() throws Throwable {
        try {
            Fraction.getFraction("1 3");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // getProperNumerator: always positive for improper fractions, positive and negative numerator
    @Test
    public void testGetProperNumerator_positiveAndNegativeImproper() throws Throwable {
        Fraction a = Fraction.getFraction(7, 4);
        assertEquals(3, a.getProperNumerator());
        Fraction b = Fraction.getFraction(-7, 4);
        assertEquals(3, b.getProperNumerator());
    }

    // getProperWhole: sign carried on the whole part
    @Test
    public void testGetProperWhole_positiveAndNegative() throws Throwable {
        Fraction a = Fraction.getFraction(7, 4);
        assertEquals(1, a.getProperWhole());
        Fraction b = Fraction.getFraction(-7, 4);
        assertEquals(-1, b.getProperWhole());
    }

    // intValue and longValue: whole number part of the fraction
    @Test
    public void testIntValueAndLongValue() throws Throwable {
        Fraction f = Fraction.getFraction(7, 4);
        assertEquals(1, f.intValue());
        assertEquals(1L, f.longValue());
    }

    // floatValue and doubleValue: numerator divided by denominator
    @Test
    public void testFloatValueAndDoubleValue() throws Throwable {
        Fraction f = Fraction.getFraction(1, 4);
        assertEquals(0.25f, f.floatValue(), 1e-6f);
        assertEquals(0.25, f.doubleValue(), 1e-9);
    }

    // reduce(): zero numerator with non-unit denominator must reduce to ZERO (0/1) per javadoc
    @Test
    public void testReduce_zeroNumeratorNonUnitDenominatorReturnsZero() throws Throwable {
        Fraction f = Fraction.getFraction(0, 5);
        Fraction r = f.reduce();
        assertEquals(0, r.getNumerator());
        assertEquals(1, r.getDenominator());
    }

    // reduce(): non-trivial gcd simplifies; already-reduced fraction returns same instance
    @Test
    public void testReduce_nonTrivialGcdAndAlreadyReducedSameInstance() throws Throwable {
        Fraction f = Fraction.getFraction(2, 4);
        Fraction r = f.reduce();
        assertEquals(1, r.getNumerator());
        assertEquals(2, r.getDenominator());
        assertSame(Fraction.ONE_HALF, Fraction.ONE_HALF.reduce());
    }

    // invert(): positive numerator swaps parts, negative numerator keeps sign on numerator
    @Test
    public void testInvert_positiveAndNegativeNumerator() throws Throwable {
        Fraction a = Fraction.getFraction(3, 4).invert();
        assertEquals(4, a.getNumerator());
        assertEquals(3, a.getDenominator());
        Fraction b = Fraction.getFraction(-3, 4).invert();
        assertEquals(-4, b.getNumerator());
        assertEquals(3, b.getDenominator());
    }

    // invert(): zero numerator and MIN_VALUE numerator both throw ArithmeticException
    @Test
    public void testInvert_zeroAndMinValueThrow() throws Throwable {
        try {
            Fraction.ZERO.invert();
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
        try {
            Fraction.getFraction(Integer.MIN_VALUE, 1).invert();
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // negate(): flips numerator sign; MIN_VALUE numerator overflows and throws
    @Test
    public void testNegate_normalAndOverflowThrows() throws Throwable {
        Fraction a = Fraction.getFraction(3, 4).negate();
        assertEquals(-3, a.getNumerator());
        assertEquals(4, a.getDenominator());
        try {
            Fraction.getFraction(Integer.MIN_VALUE, 1).negate();
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // abs(): positive returns same instance, negative returns positive equivalent
    @Test
    public void testAbs_positiveSameNegativeFlips() throws Throwable {
        Fraction pos = Fraction.getFraction(3, 4);
        assertSame(pos, pos.abs());
        Fraction absNeg = Fraction.getFraction(-3, 4).abs();
        assertEquals(3, absNeg.getNumerator());
        assertEquals(4, absNeg.getDenominator());
    }

    // pow(): power==1 returns this, power==0 returns ONE, negative and positive powers compute correctly
    @Test
    public void testPow_oneZeroNegativeAndPositive() throws Throwable {
        Fraction half = Fraction.ONE_HALF;
        assertSame(half, half.pow(1));
        assertSame(Fraction.ONE, half.pow(0));
        Fraction neg = half.pow(-2);
        assertEquals(4, neg.getNumerator());
        assertEquals(1, neg.getDenominator());
        Fraction pos = half.pow(2);
        assertEquals(1, pos.getNumerator());
        assertEquals(4, pos.getDenominator());
    }

    // add(): null argument throws IllegalArgumentException
    @Test
    public void testAdd_nullThrows() throws Throwable {
        try {
            Fraction.getFraction(1, 2).add(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // add()/subtract(): coprime denominators (fast path) and non-coprime denominators (BigInteger path)
    @Test
    public void testAddSubtract_coprimeAndNonCoprimeDenominators() throws Throwable {
        Fraction r1 = Fraction.getFraction(1, 2).add(Fraction.getFraction(1, 3));
        assertEquals(5, r1.getNumerator());
        assertEquals(6, r1.getDenominator());
        Fraction r2 = Fraction.getFraction(1, 4).add(Fraction.getFraction(1, 6));
        assertEquals(5, r2.getNumerator());
        assertEquals(12, r2.getDenominator());
        Fraction r3 = Fraction.getFraction(3, 4).subtract(Fraction.getFraction(1, 4));
        assertEquals(1, r3.getNumerator());
        assertEquals(2, r3.getDenominator());
    }

    // multiplyBy(): null throws, zero numerator short-circuits to ZERO, normal reduced multiplication
    @Test
    public void testMultiplyBy_nullZeroAndBasic() throws Throwable {
        try {
            Fraction.getFraction(1, 2).multiplyBy(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        assertSame(Fraction.ZERO, Fraction.ZERO.multiplyBy(Fraction.ONE_HALF));
        Fraction r = Fraction.getFraction(2, 3).multiplyBy(Fraction.getFraction(3, 4));
        assertEquals(1, r.getNumerator());
        assertEquals(2, r.getDenominator());
    }

    // divideBy(): basic division, dividing by zero fraction throws, null argument throws
    @Test
    public void testDivideBy_basicByZeroAndNull() throws Throwable {
        Fraction r = Fraction.getFraction(1, 2).divideBy(Fraction.getFraction(1, 3));
        assertEquals(3, r.getNumerator());
        assertEquals(2, r.getDenominator());
        try {
            Fraction.getFraction(1, 2).divideBy(Fraction.ZERO);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
        try {
            Fraction.getFraction(1, 2).divideBy(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // equals(): same values equal, different representation (1/2 vs 2/4) not equal, non-Fraction object not equal; hashCode consistent for equal fractions
    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Fraction a = Fraction.getFraction(1, 2);
        Fraction b = Fraction.getFraction(1, 2);
        Fraction c = Fraction.getFraction(2, 4);
        assertTrue(a.equals(b));
        assertFalse(a.equals(c));
        assertFalse(a.equals("1/2"));
        assertEquals(a.hashCode(), b.hashCode());
    }

    // compareTo(): ordering by value, equal value despite different representation, ClassCastException and NullPointerException
    @Test
    public void testCompareTo_orderingAndExceptions() throws Throwable {
        Fraction a = Fraction.getFraction(1, 2);
        Fraction b = Fraction.getFraction(1, 3);
        assertTrue(a.compareTo(b) > 0);
        Fraction c = Fraction.getFraction(2, 4);
        assertEquals(0, a.compareTo(c));
        try {
            a.compareTo("not a fraction");
            fail("expected ClassCastException");
        } catch (ClassCastException expected) {
        }
        try {
            a.compareTo(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // toString(): always numerator/denominator format
    @Test
    public void testToString_format() throws Throwable {
        Fraction f = Fraction.getFraction(3, 4);
        assertEquals("3/4", f.toString());
    }

    // toProperString(): zero numerator, numerator equal denominator, numerator equal -denominator branches
    @Test
    public void testToProperString_zeroAndEqualAndNegEqual() throws Throwable {
        assertEquals("0", Fraction.ZERO.toProperString());
        Fraction eq = Fraction.getFraction(4, 4);
        assertEquals("1", eq.toProperString());
        Fraction negEq = Fraction.getFraction(-4, 4);
        assertEquals("-1", negEq.toProperString());
    }

    // toProperString(): improper fraction with non-zero whole part, and simple proper fraction branch
    @Test
    public void testToProperString_improperAndSimpleProper() throws Throwable {
        Fraction improper = Fraction.getFraction(7, 4);
        assertEquals("1 3/4", improper.toProperString());
        assertEquals("1/2", Fraction.ONE_HALF.toProperString());
    }
}
