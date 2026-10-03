package org.apache.commons.lang.math;

import junit.framework.TestCase;
import java.math.BigInteger;

public class FractionTest extends TestCase {

    public void test_FractionTest_getFraction_int_int_behavior_attempt_1() throws Throwable {
        Fraction f = Fraction.getFraction(3, 4);
        assertNotNull(f);
        assertEquals(3, f.getNumerator());
        assertEquals(4, f.getDenominator());
    }

    public void test_FractionTest_getFraction_zeroDenominator_attempt_2() throws Throwable {
        try {
            Fraction.getFraction(1, 0);
            fail("Expected ArithmeticException for zero denominator");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("denominator"));
        }
    }

    public void test_FractionTest_getFraction_negativeDenominator_attempt_3() throws Throwable {
        Fraction f = Fraction.getFraction(3, -4);
        assertEquals(-3, f.getNumerator());
        assertEquals(4, f.getDenominator());
    }

    public void test_FractionTest_getFraction_negativeMinInt_attempt_4() throws Throwable {
        try {
            Fraction.getFraction(Integer.MIN_VALUE, -1);
            fail("Expected ArithmeticException for overflow negation");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    public void test_FractionTest_getFraction_threeParams_attempt_5() throws Throwable {
        Fraction f = Fraction.getFraction(1, 1, 2);
        assertEquals(3, f.getNumerator());
        assertEquals(2, f.getDenominator());

        Fraction fNeg = Fraction.getFraction(-1, 1, 2);
        assertEquals(-3, fNeg.getNumerator());
        assertEquals(2, fNeg.getDenominator());
    }

    public void test_FractionTest_getFraction_threeParams_edgeCases_attempt_6() throws Throwable {
        try {
            Fraction.getFraction(1, 1, 0);
            fail();
        } catch (ArithmeticException e) {
            // expected
        }

        try {
            Fraction.getFraction(1, 1, -2);
            fail();
        } catch (ArithmeticException e) {
            // expected
        }

        try {
            Fraction.getFraction(1, -1, 2);
            fail();
        } catch (ArithmeticException e) {
            // expected
        }

        try {
            Fraction.getFraction(Integer.MAX_VALUE, 1, 1);
            fail();
        } catch (ArithmeticException e) {
            // expected
        }
    }

    public void test_FractionTest_getReducedFraction_behavior_attempt_7() throws Throwable {
        Fraction f = Fraction.getReducedFraction(2, 4);
        assertEquals(1, f.getNumerator());
        assertEquals(2, f.getDenominator());

        Fraction fZero = Fraction.getReducedFraction(0, 5);
        assertSame(Fraction.ZERO, fZero);

        Fraction fMinDenom = Fraction.getReducedFraction(2, Integer.MIN_VALUE);
        assertEquals(1, fMinDenom.getNumerator());
        assertEquals(1073741824, fMinDenom.getDenominator());
    }

    public void test_FractionTest_getFraction_double_attempt_8() throws Throwable {
        Fraction f = Fraction.getFraction(0.5);
        assertEquals(1, f.getNumerator());
        assertEquals(2, f.getDenominator());

        try {
            Fraction.getFraction(Double.NaN);
            fail();
        } catch (ArithmeticException e) {
            // expected
        }

        try {
            Fraction.getFraction((double) Integer.MAX_VALUE + 10.0);
            fail();
        } catch (ArithmeticException e) {
            // expected
        }
    }

    public void test_FractionTest_getFraction_string_attempt_9() throws Throwable {
        try {
            Fraction.getFraction((String) null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        Fraction f1 = Fraction.getFraction("0.5");
        assertEquals(1, f1.getNumerator());

        Fraction f2 = Fraction.getFraction("1 1/2");
        assertEquals(3, f2.getNumerator());
        assertEquals(2, f2.getDenominator());

        try {
            Fraction.getFraction("1 invalid");
            fail();
        } catch (NumberFormatException e) {
            // expected
        }

        Fraction f3 = Fraction.getFraction("3/4");
        assertEquals(3, f3.getNumerator());
        assertEquals(4, f3.getDenominator());

        Fraction f4 = Fraction.getFraction("5");
        assertEquals(5, f4.getNumerator());
        assertEquals(1, f4.getDenominator());
    }

    public void test_FractionTest_accessors_attempt_10() throws Throwable {
        Fraction f = Fraction.getFraction(-7, 4);
        assertEquals(-7, f.getNumerator());
        assertEquals(4, f.getDenominator());
        assertEquals(3, f.getProperNumerator());
        assertEquals(-1, f.getProperWhole());

        assertEquals(-1, f.intValue());
        assertEquals(-1L, f.longValue());
        assertEquals(-1.75f, f.floatValue(), 0.0001f);
        assertEquals(-1.75, f.doubleValue(), 0.0001);
    }

    public void test_FractionTest_reduce_attempt_11() throws Throwable {
        Fraction f = Fraction.getFraction(2, 4);
        Fraction reduced = f.reduce();
        assertEquals(1, reduced.getNumerator());
        assertEquals(2, reduced.getDenominator());

        Fraction fPrime = Fraction.getFraction(3, 5);
        assertSame(fPrime, fPrime.reduce());
    }

    public void test_FractionTest_invert_attempt_12() throws Throwable {
        Fraction f = Fraction.getFraction(2, 4);
        Fraction inv = f.invert();
        assertEquals(4, inv.getNumerator());
        assertEquals(2, inv.getDenominator());

        try {
            Fraction.ZERO.invert();
            fail();
        } catch (ArithmeticException e) {
            // expected
        }

        try {
            Fraction.getFraction(Integer.MIN_VALUE, 1).invert();
            fail();
        } catch (ArithmeticException e) {
            // expected
        }

        Fraction fNeg = Fraction.getFraction(-2, 4);
        Fraction invNeg = fNeg.invert();
        assertEquals(4, invNeg.getNumerator());
        assertEquals(-2, invNeg.getDenominator());
    }

    public void test_FractionTest_negate_attempt_13() throws Throwable {
        Fraction f = Fraction.getFraction(1, 2);
        Fraction neg = f.negate();
        assertEquals(-1, neg.getNumerator());
        assertEquals(2, neg.getDenominator());

        try {
            Fraction.getFraction(Integer.MIN_VALUE, 1).negate();
            fail();
        } catch (ArithmeticException e) {
            // expected
        }
    }

    public void test_FractionTest_abs_attempt_14() throws Throwable {
        Fraction f = Fraction.getFraction(-1, 2);
        Fraction absF = f.abs();
        assertEquals(1, absF.getNumerator());
        assertEquals(2, absF.getDenominator());

        Fraction fPos = Fraction.getFraction(1, 2);
        assertSame(fPos, fPos.abs());
    }

    public void test_FractionTest_pow_attempt_15() throws Throwable {
        Fraction f = Fraction.getFraction(2, 3);
        assertEquals(Fraction.ONE, f.pow(0));
        assertSame(f, f.pow(1));
        
        Fraction fSquared = f.pow(2);
        assertEquals(4, fSquared.getNumerator());
        assertEquals(9, fSquared.getDenominator());

        Fraction fCubed = f.pow(3);
        assertEquals(8, fCubed.getNumerator());
        assertEquals(27, fCubed.getDenominator());

        Fraction fNegPow = f.pow(-2);
        assertEquals(9, fNegPow.getNumerator());
        assertEquals(4, fNegPow.getDenominator());

        Fraction fMinNeg = f.pow(Integer.MIN_VALUE);
        assertNotNull(fMinNeg);
    }

    public void test_FractionTest_arithmetic_add_subtract_attempt_16() throws Throwable {
        Fraction f1 = Fraction.getFraction(1, 3);
        Fraction f2 = Fraction.getFraction(1, 6);
        
        Fraction sum = f1.add(f2);
        assertEquals(1, sum.getNumerator());
        assertEquals(2, sum.getDenominator());

        Fraction diff = f1.subtract(f2);
        assertEquals(1, diff.getNumerator());
        assertEquals(6, diff.getDenominator());

        Fraction fZero = Fraction.ZERO;
        assertEquals(f1, fZero.add(f1));
        assertEquals(f1, f1.add(fZero));
        assertEquals(f1, f1.subtract(fZero));

        Fraction fWithCommonGcd = Fraction.getFraction(2, 6);
        Fraction sumGcd = f1.add(fWithCommonGcd);
        assertEquals(2, sumGcd.getNumerator());
        assertEquals(3, sumGcd.getDenominator());

        try {
            f1.add(null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            f1.subtract(null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void test_FractionTest_arithmetic_multiply_divide_attempt_17() throws Throwable {
        Fraction f1 = Fraction.getFraction(2, 3);
        Fraction f2 = Fraction.getFraction(3, 4);

        Fraction prod = f1.multiplyBy(f2);
        assertEquals(1, prod.getNumerator());
        assertEquals(2, prod.getDenominator());

        assertEquals(Fraction.ZERO, f1.multiplyBy(Fraction.ZERO));
        assertEquals(Fraction.ZERO, Fraction.ZERO.multiplyBy(f1));

        Fraction quot = f1.divideBy(f2);
        assertEquals(8, quot.getNumerator());
        assertEquals(9, quot.getDenominator());

        try {
            f1.multiplyBy(null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            f1.divideBy(null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            f1.divideBy(Fraction.ZERO);
            fail();
        } catch (ArithmeticException e) {
            // expected
        }
    }

    public void test_FractionTest_equals_hashCode_compareTo_attempt_18() throws Throwable {
        Fraction f1 = Fraction.getFraction(1, 2);
        Fraction f2 = Fraction.getFraction(1, 2);
        Fraction f3 = Fraction.getFraction(2, 4);
        Fraction f4 = Fraction.getFraction(1, 3);

        assertTrue(f1.equals(f1));
        assertTrue(f1.equals(f2));
        assertFalse(f1.equals(f3));
        assertFalse(f1.equals(null));
        assertFalse(f1.equals("string"));

        assertEquals(f1.hashCode(), f2.hashCode());
        // Force hashCode calculation branch
        f1.hashCode = 0;
        assertTrue(f1.hashCode() != 0);

        assertEquals(0, f1.compareTo(f1));
        assertEquals(0, f1.compareTo(f2));
        assertEquals(0, f1.compareTo(f3)); // compareTo treats equal value fractions as 0
        assertEquals(-1, f4.compareTo(f1));
        assertEquals(1, f1.compareTo(f4));
    }

    public void test_FractionTest_toString_toProperString_attempt_19() throws Throwable {
        Fraction f = Fraction.getFraction(3, 4);
        assertEquals("3/4", f.toString());

        Fraction fZero = Fraction.ZERO;
        assertEquals("0", fZero.toProperString());

        Fraction fOne = Fraction.ONE;
        assertEquals("1", fOne.toProperString());

        Fraction fNegOne = Fraction.getFraction(-1, 1);
        assertEquals("-1", fNegOne.toProperString());

        Fraction fProper = Fraction.getFraction(5, 4);
        assertEquals("1 1/4", fProper.toProperString());

        Fraction fImproperProperNumeratorZero = Fraction.getFraction(4, 4);
        assertEquals("1", fImproperProperNumeratorZero.toProperString());

        Fraction fLargeImproper = Fraction.getFraction(10, 3);
        assertEquals("10/3", fLargeImproper.toProperString());
    }
}