package org.apache.commons.math3.fraction;

import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.NullArgumentException;

public class FractionClaudeTest {

    // covers a0>overflow branch in the private double-based constructor
    @Test
    public void testConstructorDouble_valueExceedsIntRange_throwsFractionConversionException() throws Throwable {
        try {
            new Fraction(1.0e10, 1.0e-5, 100);
            fail("expected FractionConversionException");
        } catch (FractionConversionException expected) {
        }
    }

    // covers normal iterative convergence branch honoring the epsilon contract
    @Test
    public void testConstructorDoubleEpsilonMaxIterations_convergesWithinEpsilon() throws Throwable {
        Fraction f = new Fraction(Math.PI, 1.0e-6, 1000);
        assertEquals(Math.PI, f.doubleValue(), 1.0e-6);
    }

    // covers n>=maxIterations failure branch
    @Test
    public void testConstructorDoubleEpsilonMaxIterations_tooFewIterations_throwsFractionConversionException() throws Throwable {
        try {
            new Fraction(Math.PI, 1.0e-20, 2);
            fail("expected FractionConversionException");
        } catch (FractionConversionException expected) {
        }
    }

    // covers the "almost integer" short-circuit, which must also work when epsilon==0 (maxDenominator overload)
    @Test
    public void testConstructorDoubleMaxDenominator_integerValue_returnsExactFraction() throws Throwable {
        Fraction f = new Fraction(1.0, 1);
        assertEquals(1, f.getNumerator());
        assertEquals(1, f.getDenominator());
    }

    // covers maxDenominator bound on the resulting denominator
    @Test
    public void testConstructorDoubleMaxDenominator_nonIntegerValue_returnsApproximation() throws Throwable {
        Fraction f = new Fraction(0.6, 10);
        assertTrue(f.getDenominator() <= 10);
        assertEquals(0.6, f.doubleValue(), 0.01);
    }

    // covers Fraction(int) delegating to Fraction(int,1)
    @Test
    public void testConstructorSingleInt_createsFractionOverOne() throws Throwable {
        Fraction f = new Fraction(5);
        assertEquals(5, f.getNumerator());
        assertEquals(1, f.getDenominator());
    }

    // covers den==0 branch
    @Test
    public void testConstructorIntInt_zeroDenominator_throwsMathArithmeticException() throws Throwable {
        try {
            new Fraction(1, 0);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // covers den<0 sign-normalization branch
    @Test
    public void testConstructorIntInt_negativeDenominator_signMovedToNumerator() throws Throwable {
        Fraction f = new Fraction(1, -2);
        assertEquals(-1, f.getNumerator());
        assertEquals(2, f.getDenominator());
    }

    // covers gcd reduction branch (d>1)
    @Test
    public void testConstructorIntInt_reducesToLowestTerms() throws Throwable {
        Fraction f = new Fraction(4, 8);
        assertEquals(1, f.getNumerator());
        assertEquals(2, f.getDenominator());
    }

    // covers overflow guard when num or den is Integer.MIN_VALUE with negative denominator
    @Test
    public void testConstructorIntInt_minValueWithNegativeDenominator_throwsMathArithmeticException() throws Throwable {
        try {
            new Fraction(Integer.MIN_VALUE, -1);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // covers numerator<0 branch (calls negate) and numerator>=0 branch (returns this)
    @Test
    public void testAbs_negativeAndPositiveNumerator() throws Throwable {
        Fraction neg = new Fraction(-3, 4);
        Fraction absNeg = neg.abs();
        assertEquals(3, absNeg.getNumerator());
        assertEquals(4, absNeg.getDenominator());
        Fraction pos = new Fraction(3, 4);
        assertSame(pos, pos.abs());
    }

    // covers nOd<dOn, nOd>dOn and nOd==dOn branches
    @Test
    public void testCompareTo_lessEqualGreater() throws Throwable {
        Fraction a = new Fraction(1, 2);
        Fraction b = new Fraction(2, 3);
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertEquals(0, a.compareTo(new Fraction(2, 4)));
    }

    // covers doubleValue() and floatValue() numerator/denominator division
    @Test
    public void testDoubleValueAndFloatValue_computeRatio() throws Throwable {
        Fraction f = new Fraction(1, 4);
        assertEquals(0.25, f.doubleValue(), 1e-9);
        assertEquals(0.25f, f.floatValue(), 1e-6f);
    }

    // covers this==other, instanceof true/equal, instanceof true/different, not-instanceof and null branches
    @Test
    public void testEquals_variousCases() throws Throwable {
        Fraction a = new Fraction(1, 2);
        assertTrue(a.equals(a));
        assertTrue(a.equals(new Fraction(2, 4)));
        assertFalse(a.equals(new Fraction(1, 3)));
        assertFalse(a.equals("1/2"));
        assertFalse(a.equals(null));
    }

    // covers simple accessors
    @Test
    public void testGetNumeratorAndDenominator_returnStoredValues() throws Throwable {
        Fraction f = new Fraction(3, 7);
        assertEquals(3, f.getNumerator());
        assertEquals(7, f.getDenominator());
    }

    // covers hashCode formula consistency for reduced-equal fractions
    @Test
    public void testHashCode_equalFractionsHaveSameHashCode() throws Throwable {
        Fraction a = new Fraction(1, 2);
        Fraction b = new Fraction(2, 4);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // covers intValue() truncation for positive and negative fractions
    @Test
    public void testIntValue_truncatesTowardZero() throws Throwable {
        assertEquals(3, new Fraction(7, 2).intValue());
        assertEquals(-3, new Fraction(-7, 2).intValue());
    }

    // covers longValue() whole-number truncation
    @Test
    public void testLongValue_returnsWholeNumberPart() throws Throwable {
        assertEquals(3L, new Fraction(7, 2).longValue());
    }

    // covers normal negate path
    @Test
    public void testNegate_positiveNumerator_returnsNegated() throws Throwable {
        Fraction f = new Fraction(3, 4);
        Fraction n = f.negate();
        assertEquals(-3, n.getNumerator());
        assertEquals(4, n.getDenominator());
    }

    // covers numerator==Integer.MIN_VALUE overflow guard
    @Test
    public void testNegate_minValueNumerator_throwsMathArithmeticException() throws Throwable {
        Fraction f = new Fraction(Integer.MIN_VALUE, 1);
        try {
            f.negate();
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // covers reciprocal swapping numerator/denominator, including sign normalization
    @Test
    public void testReciprocal_positiveAndNegativeFraction() throws Throwable {
        Fraction f = new Fraction(3, 4);
        Fraction r = f.reciprocal();
        assertEquals(4, r.getNumerator());
        assertEquals(3, r.getDenominator());
        Fraction g = new Fraction(-3, 4);
        Fraction rg = g.reciprocal();
        assertEquals(-4, rg.getNumerator());
        assertEquals(3, rg.getDenominator());
    }

    // covers addSub null-check branch
    @Test
    public void testAddFraction_null_throwsNullArgumentException() throws Throwable {
        Fraction f = new Fraction(1, 2);
        try {
            f.add((Fraction) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // covers numerator==0 identity branch (isAdd true, returns other fraction)
    @Test
    public void testAddFraction_thisIsZero_returnsOtherFraction() throws Throwable {
        Fraction zero = new Fraction(0, 5);
        Fraction other = new Fraction(2, 3);
        Fraction result = zero.add(other);
        assertEquals(2, result.getNumerator());
        assertEquals(3, result.getDenominator());
    }

    // covers fraction.numerator==0 branch returning this unchanged
    @Test
    public void testAddFraction_otherIsZero_returnsThis() throws Throwable {
        Fraction f = new Fraction(2, 3);
        Fraction zero = new Fraction(0, 7);
        Fraction result = f.add(zero);
        assertEquals(2, result.getNumerator());
        assertEquals(3, result.getDenominator());
    }

    // covers d1==1 branch of addSub
    @Test
    public void testAddFraction_coprimeDenominators_returnsReducedSum() throws Throwable {
        Fraction result = new Fraction(1, 2).add(new Fraction(1, 3));
        assertEquals(5, result.getNumerator());
        assertEquals(6, result.getDenominator());
    }

    // covers d1>1 BigInteger branch of addSub
    @Test
    public void testAddFraction_commonDenominatorFactor_returnsReducedSum() throws Throwable {
        Fraction result = new Fraction(1, 4).add(new Fraction(1, 6));
        assertEquals(5, result.getNumerator());
        assertEquals(12, result.getDenominator());
    }

    // covers add(int) formula
    @Test
    public void testAddInt_addsIntegerMultipleOfDenominator() throws Throwable {
        Fraction result = new Fraction(1, 2).add(2);
        assertEquals(5, result.getNumerator());
        assertEquals(2, result.getDenominator());
    }

    // covers subtract delegating to addSub with isAdd=false
    @Test
    public void testSubtractFraction_returnsReducedDifference() throws Throwable {
        Fraction result = new Fraction(1, 2).subtract(new Fraction(1, 3));
        assertEquals(1, result.getNumerator());
        assertEquals(6, result.getDenominator());
    }

    // covers subtract(int) formula
    @Test
    public void testSubtractInt_subtractsIntegerMultipleOfDenominator() throws Throwable {
        Fraction result = new Fraction(5, 2).subtract(1);
        assertEquals(3, result.getNumerator());
        assertEquals(2, result.getDenominator());
    }

    // covers multiply null-check branch
    @Test
    public void testMultiplyFraction_null_throwsNullArgumentException() throws Throwable {
        Fraction f = new Fraction(1, 2);
        try {
            f.multiply((Fraction) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // covers numerator==0 || fraction.numerator==0 branch
    @Test
    public void testMultiplyFraction_zeroOperand_returnsZero() throws Throwable {
        Fraction result = new Fraction(0, 5).multiply(new Fraction(3, 4));
        assertEquals(0, result.getNumerator());
        assertEquals(1, result.getDenominator());
    }

    // covers knuth 4.5.1 multiply reduction
    @Test
    public void testMultiplyFraction_normalCase_returnsReducedProduct() throws Throwable {
        Fraction result = new Fraction(2, 3).multiply(new Fraction(3, 4));
        assertEquals(1, result.getNumerator());
        assertEquals(2, result.getDenominator());
    }

    // covers multiply(int) formula with reduction
    @Test
    public void testMultiplyInt_multipliesNumeratorAndReduces() throws Throwable {
        Fraction result = new Fraction(1, 3).multiply(3);
        assertEquals(1, result.getNumerator());
        assertEquals(1, result.getDenominator());
    }

    // covers divide null-check branch
    @Test
    public void testDivideFraction_null_throwsNullArgumentException() throws Throwable {
        Fraction f = new Fraction(1, 2);
        try {
            f.divide((Fraction) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // covers fraction.numerator==0 zero-divide branch
    @Test
    public void testDivideFraction_zeroDivisor_throwsMathArithmeticException() throws Throwable {
        Fraction f = new Fraction(1, 2);
        try {
            f.divide(new Fraction(0, 3));
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // covers divide delegating to multiply(reciprocal)
    @Test
    public void testDivideFraction_normalCase_returnsReducedQuotient() throws Throwable {
        Fraction result = new Fraction(1, 2).divide(new Fraction(1, 4));
        assertEquals(2, result.getNumerator());
        assertEquals(1, result.getDenominator());
    }

    // covers divide(int) formula
    @Test
    public void testDivideInt_dividesDenominator() throws Throwable {
        Fraction result = new Fraction(1, 2).divide(2);
        assertEquals(1, result.getNumerator());
        assertEquals(4, result.getDenominator());
    }

    // covers percentageValue() formula (100 * doubleValue())
    @Test
    public void testPercentageValue_computesHundredTimesDoubleValue() throws Throwable {
        Fraction f = new Fraction(1, 4);
        assertEquals(25.0, f.percentageValue(), 1e-9);
    }

    // covers denominator==0 branch of getReducedFraction
    @Test
    public void testGetReducedFraction_zeroDenominator_throwsMathArithmeticException() throws Throwable {
        try {
            Fraction.getReducedFraction(1, 0);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // covers numerator==0 normalize-zero branch, returning the ZERO constant
    @Test
    public void testGetReducedFraction_zeroNumerator_returnsZeroConstant() throws Throwable {
        Fraction result = Fraction.getReducedFraction(0, 7);
        assertSame(Fraction.ZERO, result);
    }

    // covers denominator<0 sign-normalization branch (non-overflow case)
    @Test
    public void testGetReducedFraction_negativeDenominator_movesSignToNumerator() throws Throwable {
        Fraction result = Fraction.getReducedFraction(3, -4);
        assertEquals(-3, result.getNumerator());
        assertEquals(4, result.getDenominator());
    }

    // covers denominator==Integer.MIN_VALUE with even numerator special-case branch
    @Test
    public void testGetReducedFraction_minValueDenominatorEvenNumerator_handlesOverflowSpecialCase() throws Throwable {
        Fraction result = Fraction.getReducedFraction(4, Integer.MIN_VALUE);
        double expected = 4.0 / Integer.MIN_VALUE;
        assertEquals(expected, result.doubleValue(), 1e-15);
    }

    // covers numerator==Integer.MIN_VALUE overflow guard within denominator<0 branch
    @Test
    public void testGetReducedFraction_minValueNumeratorNegativeDenominator_throwsMathArithmeticException() throws Throwable {
        try {
            Fraction.getReducedFraction(Integer.MIN_VALUE, -1);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // covers denominator==1 branch and general "num / den" branch of toString
    @Test
    public void testToString_bothBranches() throws Throwable {
        Fraction whole = new Fraction(5, 1);
        assertEquals("5", whole.toString());
        Fraction frac = new Fraction(3, 4);
        assertEquals("3 / 4", frac.toString());
    }

    // covers getField() delegating to the FractionField singleton
    @Test
    public void testGetField_returnsSingletonFractionField() throws Throwable {
        Fraction a = new Fraction(1, 2);
        Fraction b = new Fraction(3, 4);
        FractionField fieldA = a.getField();
        FractionField fieldB = b.getField();
        assertNotNull(fieldA);
        assertSame(fieldA, fieldB);
    }
}
