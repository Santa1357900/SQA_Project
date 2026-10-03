package org.apache.commons.math3.fraction;

import static org.junit.Assert.*;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.exception.ZeroException;

public class BigFractionClaudeTest {

    // Branch: BigFraction(BigInteger) delegates to (num,1) -> denominator stays ONE
    @Test
    public void testConstructorBigInteger_singleArg_denominatorOne() throws Throwable {
        BigFraction f = new BigFraction(BigInteger.valueOf(7));
        assertEquals(BigInteger.valueOf(7), f.getNumerator());
        assertEquals(BigInteger.ONE, f.getDenominator());
    }

    // Branch: denominator == 0 -> ZeroException
    @Test
    public void testConstructorBigIntegerBigInteger_zeroDenominator_throwsZeroException() throws Throwable {
        try {
            new BigFraction(BigInteger.ONE, BigInteger.ZERO);
            fail("expected ZeroException");
        } catch (ZeroException expected) {
        }
    }

    // Branch: num == null -> NullArgumentException
    @Test
    public void testConstructorBigIntegerBigInteger_nullNumerator_throwsNullArgumentException() throws Throwable {
        try {
            new BigFraction((BigInteger) null, BigInteger.ONE);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // Branch: den == null -> NullArgumentException
    @Test
    public void testConstructorBigIntegerBigInteger_nullDenominator_throwsNullArgumentException() throws Throwable {
        try {
            new BigFraction(BigInteger.ONE, (BigInteger) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // Branch: gcd > 1 -> reduce to lowest terms
    @Test
    public void testConstructorBigIntegerBigInteger_reducesToLowestTerms() throws Throwable {
        BigFraction f = new BigFraction(4, 8);
        assertEquals(BigInteger.ONE, f.getNumerator());
        assertEquals(BigInteger.valueOf(2), f.getDenominator());
    }

    // Branch: num == 0 -> forced denominator ONE
    @Test
    public void testConstructorBigIntegerBigInteger_zeroNumerator_denominatorBecomesOne() throws Throwable {
        BigFraction f = new BigFraction(0, 5);
        assertEquals(BigInteger.ZERO, f.getNumerator());
        assertEquals(BigInteger.ONE, f.getDenominator());
    }

    // Branch: denominator negative -> sign moved to numerator
    @Test
    public void testConstructorBigIntegerBigInteger_negativeDenominator_signMovedToNumerator() throws Throwable {
        BigFraction f = new BigFraction(1, -2);
        assertEquals(BigInteger.valueOf(-1), f.getNumerator());
        assertEquals(BigInteger.valueOf(2), f.getDenominator());
    }

    // Branch: Double.isNaN(value) -> MathIllegalArgumentException
    @Test
    public void testConstructorDouble_NaN_throwsMathIllegalArgumentException() throws Throwable {
        try {
            new BigFraction(Double.NaN);
            fail("expected MathIllegalArgumentException");
        } catch (MathIllegalArgumentException expected) {
        }
    }

    // Branch: Double.isInfinite(value) -> MathIllegalArgumentException
    @Test
    public void testConstructorDouble_infinite_throwsMathIllegalArgumentException() throws Throwable {
        try {
            new BigFraction(Double.POSITIVE_INFINITY);
            fail("expected MathIllegalArgumentException");
        } catch (MathIllegalArgumentException expected) {
        }
    }

    // Branch: exact bit conversion of 0.5 (power of two, k<0 path)
    @Test
    public void testConstructorDouble_exactHalf_producesOneHalf() throws Throwable {
        BigFraction f = new BigFraction(0.5);
        assertTrue(f.equals(BigFraction.ONE_HALF));
    }

    // Branch: exact bit conversion of 0.25 (power of two, k<0 path)
    @Test
    public void testConstructorDouble_exactQuarter_producesOneQuarter() throws Throwable {
        BigFraction f = new BigFraction(0.25);
        assertTrue(f.equals(BigFraction.ONE_QUARTER));
    }

    // Branch: continued fraction converges within epsilon on first iteration
    @Test
    public void testConstructorDoubleEpsilonMaxIterations_oneThird_convergesExactly() throws Throwable {
        BigFraction f = new BigFraction(1.0 / 3.0, 1.0e-12, 100);
        assertEquals(BigInteger.ONE, f.getNumerator());
        assertEquals(BigInteger.valueOf(3), f.getDenominator());
    }

    // Branch: n >= maxIterations -> FractionConversionException
    @Test
    public void testConstructorDoubleEpsilonMaxIterations_tooFewIterations_throwsFractionConversionException() throws Throwable {
        try {
            new BigFraction(Math.PI, 1.0e-20, 2);
            fail("expected FractionConversionException");
        } catch (FractionConversionException expected) {
        }
    }

    // Branch: maxDenominator constructor converges to 1/10
    @Test
    public void testConstructorDoubleMaxDenominator_oneTenth_convergesExactly() throws Throwable {
        BigFraction f = new BigFraction(0.1, 100);
        assertEquals(BigInteger.ONE, f.getNumerator());
        assertEquals(BigInteger.valueOf(10), f.getDenominator());
    }



    // Branch: large positive integer part also exceeds overflow (shared path, both versions)
    @Test
    public void testConstructorDoubleEpsilon_largePositiveIntegerPart_throwsFractionConversionException() throws Throwable {
        try {
            new BigFraction(3.0e9, 1.0e-10, 100);
            fail("expected FractionConversionException for out-of-range integer part");
        } catch (FractionConversionException expected) {
        }
    }

    // Branch: int,int constructor with zero denominator -> ZeroException
    @Test
    public void testConstructorIntInt_zeroDenominator_throwsZeroException() throws Throwable {
        try {
            new BigFraction(1, 0);
            fail("expected ZeroException");
        } catch (ZeroException expected) {
        }
    }

    // Branch: getReducedFraction normalizes zero numerator
    @Test
    public void testGetReducedFraction_zeroNumerator_returnsZero() throws Throwable {
        BigFraction f = BigFraction.getReducedFraction(0, 5);
        assertTrue(f.equals(BigFraction.ZERO));
    }

    // Branch: getReducedFraction with negative denominator normalizes sign
    @Test
    public void testGetReducedFraction_negativeDenominator_normalizesSign() throws Throwable {
        BigFraction f = BigFraction.getReducedFraction(3, -6);
        assertEquals(BigInteger.valueOf(-1), f.getNumerator());
        assertEquals(BigInteger.valueOf(2), f.getDenominator());
    }

    // Branch: abs() on negative fraction negates
    @Test
    public void testAbs_negativeFraction_returnsPositive() throws Throwable {
        BigFraction f = new BigFraction(-3, 4);
        BigFraction a = f.abs();
        assertEquals(BigInteger.valueOf(3), a.getNumerator());
        assertEquals(BigInteger.valueOf(4), a.getDenominator());
    }

    // Branch: abs() on non-negative fraction returns this
    @Test
    public void testAbs_positiveFraction_returnsSameInstance() throws Throwable {
        assertSame(BigFraction.ONE_HALF, BigFraction.ONE_HALF.abs());
    }

    // Branch: add(BigInteger) reduced result
    @Test
    public void testAddBigInteger_normal() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF.add(BigInteger.ONE);
        assertEquals(BigInteger.valueOf(3), f.getNumerator());
        assertEquals(BigInteger.valueOf(2), f.getDenominator());
    }

    // Branch: add(BigFraction) same denominator path
    @Test
    public void testAddBigFraction_sameDenominator() throws Throwable {
        BigFraction f = BigFraction.ONE_THIRD.add(BigFraction.ONE_THIRD);
        assertEquals(BigInteger.valueOf(2), f.getNumerator());
        assertEquals(BigInteger.valueOf(3), f.getDenominator());
    }

    // Branch: add(BigFraction) different denominator path
    @Test
    public void testAddBigFraction_differentDenominator() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF.add(BigFraction.ONE_THIRD);
        assertEquals(BigInteger.valueOf(5), f.getNumerator());
        assertEquals(BigInteger.valueOf(6), f.getDenominator());
    }

    // Branch: add(BigFraction) null -> NullArgumentException
    @Test
    public void testAddBigFraction_null_throwsNullArgumentException() throws Throwable {
        try {
            BigFraction.ONE_HALF.add((BigFraction) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // Branch: add(BigFraction) with ZERO argument returns 'this' shortcut
    @Test
    public void testAddBigFraction_zeroArgument_returnsEquivalent() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF.add(BigFraction.ZERO);
        assertTrue(f.equals(BigFraction.ONE_HALF));
    }

    // Branch: bigDecimalValue() terminating decimal
    @Test
    public void testBigDecimalValue_terminating_exact() throws Throwable {
        BigDecimal d = BigFraction.ONE_HALF.bigDecimalValue();
        assertEquals(0, d.compareTo(new BigDecimal("0.5")));
    }

    // Branch: bigDecimalValue() non-terminating decimal -> ArithmeticException
    @Test
    public void testBigDecimalValue_nonTerminating_throwsArithmeticException() throws Throwable {
        try {
            BigFraction.ONE_THIRD.bigDecimalValue();
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // Branch: bigDecimalValue(roundingMode) uses dividend scale (0) -> rounds down to 0
    @Test
    public void testBigDecimalValueWithRoundingMode() throws Throwable {
        BigDecimal d = BigFraction.ONE_THIRD.bigDecimalValue(BigDecimal.ROUND_DOWN);
        assertEquals(0, d.compareTo(BigDecimal.ZERO));
    }

    // Branch: bigDecimalValue(scale, roundingMode)
    @Test
    public void testBigDecimalValueWithScaleAndRoundingMode() throws Throwable {
        BigDecimal d = BigFraction.ONE_THIRD.bigDecimalValue(2, BigDecimal.ROUND_HALF_UP);
        assertEquals(0, d.compareTo(new BigDecimal("0.33")));
    }

    // Branch: compareTo greater/less/equal
    @Test
    public void testCompareTo_variousRelations() throws Throwable {
        assertTrue(BigFraction.ONE_HALF.compareTo(BigFraction.ONE_THIRD) > 0);
        assertTrue(BigFraction.ONE_THIRD.compareTo(BigFraction.ONE_HALF) < 0);
        assertEquals(0, BigFraction.ONE_HALF.compareTo(BigFraction.TWO_QUARTERS));
    }

    // Branch: divide(BigInteger) null -> NullArgumentException
    @Test
    public void testDivideBigInteger_null_throwsNullArgumentException() throws Throwable {
        try {
            BigFraction.ONE_HALF.divide((BigInteger) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // Branch: divide(BigInteger) zero -> MathArithmeticException
    @Test
    public void testDivideBigInteger_zero_throwsMathArithmeticException() throws Throwable {
        try {
            BigFraction.ONE_HALF.divide(BigInteger.ZERO);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // Branch: divide(BigFraction) with zero numerator argument -> MathArithmeticException
    @Test
    public void testDivideBigFraction_zeroNumerator_throwsMathArithmeticException() throws Throwable {
        try {
            BigFraction.ONE_HALF.divide(BigFraction.ZERO);
            fail("expected MathArithmeticException");
        } catch (MathArithmeticException expected) {
        }
    }

    // Branch: divide(BigFraction) normal path via multiply(reciprocal())
    @Test
    public void testDivideBigFraction_normal() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF.divide(BigFraction.ONE_THIRD);
        assertEquals(BigInteger.valueOf(3), f.getNumerator());
        assertEquals(BigInteger.valueOf(2), f.getDenominator());
    }

    // Branch: doubleValue() normal division, no NaN branch
    @Test
    public void testDoubleValue_normal() throws Throwable {
        assertEquals(0.5, BigFraction.ONE_HALF.doubleValue(), 1e-9);
    }

    // Branch: doubleValue() NaN-shift branch when both terms overflow double range;
    // numerator = 2^2000, denominator = 2^2000-1 are provably coprime (consecutive ints)
    @Test
    public void testDoubleValue_overflowBranch_bothExtremesCancelToOne() throws Throwable {
        BigInteger n = BigInteger.ONE.shiftLeft(2000);
        BigInteger d = n.subtract(BigInteger.ONE);
        BigFraction f = new BigFraction(n, d);
        assertEquals(1.0, f.doubleValue(), 1e-9);
    }

    // Branch: equals() reduces both sides before comparing
    @Test
    public void testEquals_reducedEquivalents_true() throws Throwable {
        assertTrue(new BigFraction(2, 4).equals(BigFraction.ONE_HALF));
    }

    // Branch: equals(null) -> false
    @Test
    public void testEquals_null_false() throws Throwable {
        assertFalse(BigFraction.ONE_HALF.equals(null));
    }

    // Branch: equals(non BigFraction) -> false
    @Test
    public void testEquals_differentType_false() throws Throwable {
        assertFalse(BigFraction.ONE_HALF.equals("1/2"));
    }

    // Branch: hashCode() consistent for equal objects
    @Test
    public void testHashCode_consistentForEqualObjects() throws Throwable {
        assertEquals(BigFraction.ONE_HALF.hashCode(), new BigFraction(2, 4).hashCode());
    }

    // Branch: floatValue() normal division
    @Test
    public void testFloatValue_normal() throws Throwable {
        assertEquals(0.5f, BigFraction.ONE_HALF.floatValue(), 1e-6f);
    }

    // Branch: numerator/denominator accessors (BigInteger, int, long)
    @Test
    public void testGetters_numeratorDenominator() throws Throwable {
        BigFraction f = new BigFraction(3, 7);
        assertEquals(BigInteger.valueOf(3), f.getNumerator());
        assertEquals(BigInteger.valueOf(7), f.getDenominator());
        assertEquals(3, f.getNumeratorAsInt());
        assertEquals(7, f.getDenominatorAsInt());
        assertEquals(3L, f.getNumeratorAsLong());
        assertEquals(7L, f.getDenominatorAsLong());
    }

    // Branch: intValue() truncates toward zero for negative fractions
    @Test
    public void testIntValue_truncatesTowardZero() throws Throwable {
        BigFraction f = new BigFraction(-7, 2);
        assertEquals(-3, f.intValue());
    }

    // Branch: longValue() truncates toward zero for positive fractions
    @Test
    public void testLongValue_truncatesTowardZero() throws Throwable {
        BigFraction f = new BigFraction(7, 2);
        assertEquals(3L, f.longValue());
    }

    // Branch: multiply(BigInteger) null -> NullArgumentException
    @Test
    public void testMultiplyBigInteger_null_throwsNullArgumentException() throws Throwable {
        try {
            BigFraction.ONE_HALF.multiply((BigInteger) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // Branch: multiply(BigFraction) zero-numerator shortcut returns ZERO
    @Test
    public void testMultiplyBigFraction_zeroShortCut_returnsZero() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF.multiply(BigFraction.ZERO);
        assertTrue(f.equals(BigFraction.ZERO));
    }

    // Branch: multiply(BigFraction) normal path with reduction
    @Test
    public void testMultiplyBigFraction_normal() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF.multiply(BigFraction.ONE_THIRD);
        assertEquals(BigInteger.ONE, f.getNumerator());
        assertEquals(BigInteger.valueOf(6), f.getDenominator());
    }

    // Branch: negate() flips numerator sign
    @Test
    public void testNegate() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF.negate();
        assertEquals(BigInteger.valueOf(-1), f.getNumerator());
        assertEquals(BigInteger.valueOf(2), f.getDenominator());
    }

    // Branch: percentageValue() multiplies by 100 then converts to double
    @Test
    public void testPercentageValue() throws Throwable {
        assertEquals(50.0, BigFraction.ONE_HALF.percentageValue(), 1e-9);
    }

    // Branch: pow(int) negative exponent inverts numerator/denominator
    @Test
    public void testPowInt_negativeExponent() throws Throwable {
        BigFraction f = new BigFraction(2, 3).pow(-1);
        assertEquals(BigInteger.valueOf(3), f.getNumerator());
        assertEquals(BigInteger.valueOf(2), f.getDenominator());
    }

    // Branch: pow(int) zero exponent yields ONE (BigInteger.pow(0) == ONE)
    @Test
    public void testPowInt_zeroExponent() throws Throwable {
        BigFraction f = new BigFraction(2, 3).pow(0);
        assertTrue(f.equals(BigFraction.ONE));
    }

    // Branch: pow(int) positive exponent
    @Test
    public void testPowInt_positiveExponent() throws Throwable {
        BigFraction f = new BigFraction(2, 3).pow(2);
        assertEquals(BigInteger.valueOf(4), f.getNumerator());
        assertEquals(BigInteger.valueOf(9), f.getDenominator());
    }

    // Branch: pow(long) positive exponent
    @Test
    public void testPowLong_positiveExponent() throws Throwable {
        BigFraction f = new BigFraction(2, 3).pow(2L);
        assertEquals(BigInteger.valueOf(4), f.getNumerator());
        assertEquals(BigInteger.valueOf(9), f.getDenominator());
    }

    // Branch: pow(BigInteger) positive exponent
    @Test
    public void testPowBigInteger_positiveExponent() throws Throwable {
        BigFraction f = new BigFraction(2, 3).pow(BigInteger.valueOf(2));
        assertEquals(BigInteger.valueOf(4), f.getNumerator());
        assertEquals(BigInteger.valueOf(9), f.getDenominator());
    }

    // Branch: pow(double) uses FastMath.pow on numerator/denominator doubles
    @Test
    public void testPowDouble() throws Throwable {
        BigFraction f = new BigFraction(4, 1);
        assertEquals(2.0, f.pow(0.5), 1e-9);
    }

    // Branch: reciprocal() swaps numerator/denominator, renormalizing sign
    @Test
    public void testReciprocal_negativeFraction() throws Throwable {
        BigFraction f = new BigFraction(-2, 3).reciprocal();
        assertEquals(BigInteger.valueOf(-3), f.getNumerator());
        assertEquals(BigInteger.valueOf(2), f.getDenominator());
    }

    // Branch: reduce() is idempotent for already-reduced fractions
    @Test
    public void testReduce_idempotent() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF.reduce();
        assertEquals(BigInteger.ONE, f.getNumerator());
        assertEquals(BigInteger.valueOf(2), f.getDenominator());
    }

    // Branch: subtract(BigFraction) null -> NullArgumentException
    @Test
    public void testSubtractBigFraction_null_throwsNullArgumentException() throws Throwable {
        try {
            BigFraction.ONE_HALF.subtract((BigFraction) null);
            fail("expected NullArgumentException");
        } catch (NullArgumentException expected) {
        }
    }

    // Branch: subtract(BigFraction) with ZERO argument returns 'this' shortcut
    @Test
    public void testSubtractBigFraction_zeroArgument_returnsEquivalent() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF.subtract(BigFraction.ZERO);
        assertTrue(f.equals(BigFraction.ONE_HALF));
    }

    // Branch: subtract(BigFraction) different denominator path
    @Test
    public void testSubtractBigFraction_differentDenominator() throws Throwable {
        BigFraction f = BigFraction.ONE_HALF.subtract(BigFraction.ONE_THIRD);
        assertEquals(BigInteger.ONE, f.getNumerator());
        assertEquals(BigInteger.valueOf(6), f.getDenominator());
    }

    // Branch: toString() when denominator == 1
    @Test
    public void testToString_denominatorOne() throws Throwable {
        assertEquals("5", new BigFraction(5).toString());
    }

    // Branch: toString() when numerator == 0
    @Test
    public void testToString_zeroNumerator() throws Throwable {
        assertEquals("0", BigFraction.ZERO.toString());
    }

    // Branch: toString() general "num / den" case
    @Test
    public void testToString_generalCase() throws Throwable {
        assertEquals("1 / 2", BigFraction.ONE_HALF.toString());
    }

    // Branch: getField() returns a non-null, consistent field instance
    @Test
    public void testGetField_notNullAndSingleton() throws Throwable {
        BigFractionField field1 = BigFraction.ONE_HALF.getField();
        BigFractionField field2 = BigFraction.TWO.getField();
        assertNotNull(field1);
        assertSame(field1, field2);
    }
}
