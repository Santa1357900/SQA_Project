package org.apache.commons.lang3.math;

import org.junit.Test;
import static org.junit.Assert.*;

public class FractionTest {

    @Test
    public void test_FractionTest_getFraction_int_int_behavior_attempt_1() throws Throwable {
        Fraction f = Fraction.getFraction(3, 4);
        assertEquals(3, f.getNumerator());
        assertEquals(4, f.getDenominator());

        Fraction negativeDenom = Fraction.getFraction(3, -4);
        assertEquals(-3, negativeDenom.getNumerator());
        assertEquals(4, negativeDenom.getDenominator());
    }

    @Test
    public void test_FractionTest_getFraction_int_int_zeroDenominator_attempt_2() throws Throwable {
        try {
            Fraction.getFraction(1, 0);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("denominator"));
        }
    }

    @Test
    public void test_FractionTest_getFraction_int_int_overflow_attempt_3() throws Throwable {
        try {
            Fraction.getFraction(Integer.MIN_VALUE, -1);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void test_FractionTest_getFraction_threeParams_behavior_attempt_4() throws Throwable {
        Fraction f = Fraction.getFraction(1, 2, 3);
        assertEquals(5, f.getNumerator());
        assertEquals(3, f.getDenominator());

        Fraction negativeWhole = Fraction.getFraction(-1, 2, 3);
        assertEquals(-5, negativeWhole.getNumerator());
        assertEquals(3, negativeWhole.getDenominator());
    }

    @Test
    public void test_FractionTest_getFraction_threeParams_validation_attempt_5() throws Throwable {
        try {
            Fraction.getFraction(1, 2, 0);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("zero"));
        }

        try {
            Fraction.getFraction(1, 2, -3);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("negative"));
        }

        try {
            Fraction.getFraction(1, -2, 3);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("negative"));
        }
    }

    @Test
    public void test_FractionTest_getReducedFraction_behavior_attempt_6() throws Throwable {
        Fraction f = Fraction.getReducedFraction(2, 4);
        assertEquals(1, f.getNumerator());
        assertEquals(2, f.getDenominator());

        Fraction zeroNum = Fraction.getReducedFraction(0, 5);
        assertSame(Fraction.ZERO, zeroNum);

        Fraction minValDenom = Fraction.getReducedFraction(2, Integer.MIN_VALUE);
        assertEquals(-1, minValDenom.getNumerator());
        assertEquals(-(Integer.MIN_VALUE / 2), minValDenom.getDenominator());
    }

    @Test
    public void test_FractionTest_getFraction_double_behavior_attempt_7() throws Throwable {
        Fraction f = Fraction.getFraction(0.5);
        assertEquals(1, f.getNumerator());
        assertEquals(2, f.getDenominator());

        try {
            Fraction.getFraction(Double.NaN);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("NaN"));
        }
    }

    @Test
    public void test_FractionTest_getFraction_string_behavior_attempt_8() throws Throwable {
        Fraction f1 = Fraction.getFraction("3/4");
        assertEquals(3, f1.getNumerator());
        assertEquals(4, f1.getDenominator());

        Fraction f2 = Fraction.getFraction("1 1/2");
        assertEquals(3, f2.getNumerator());
        assertEquals(2, f2.getDenominator());

        Fraction f3 = Fraction.getFraction("0.5");
        assertEquals(1, f3.getNumerator());
        assertEquals(2, f3.getDenominator());

        Fraction f4 = Fraction.getFraction("5");
        assertEquals(5, f4.getNumerator());
        assertEquals(1, f4.getDenominator());

        try {
            Fraction.getFraction((String) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null"));
        }

        try {
            Fraction.getFraction("1 invalid");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(e.getMessage().contains("X Y/Z"));
        }
    }

    @Test
    public void test_FractionTest_numberMethods_behavior_attempt_9() throws Throwable {
        Fraction f = Fraction.getFraction(7, 3);
        assertEquals(2, f.intValue());
        assertEquals(2L, f.longValue());
        assertEquals(2.3333333f, f.floatValue(), 0.0001f);
        assertEquals(2.3333333333333335, f.doubleValue(), 0.0001);
        assertEquals(1, f.getProperNumerator());
        assertEquals(2, f.getProperWhole());
    }

    @Test
    public void test_FractionTest_reduce_behavior_attempt_10() throws Throwable {
        Fraction f = Fraction.getFraction(2, 4);
        Fraction reduced = f.reduce();
        assertEquals(1, reduced.getNumerator());
        assertEquals(2, reduced.getDenominator());

        Fraction zero = Fraction.getFraction(0, 5);
        assertSame(Fraction.ZERO, zero.reduce());

        Fraction prime = Fraction.getFraction(3, 5);
        assertSame(prime, prime.reduce());
    }

    @Test
    public void test_FractionTest_invert_behavior_attempt_11() throws Throwable {
        Fraction f = Fraction.getFraction(3, 4);
        Fraction inverted = f.invert();
        assertEquals(4, inverted.getNumerator());
        assertEquals(3, inverted.getDenominator());

        Fraction neg = Fraction.getFraction(-3, 4);
        Fraction invertedNeg = neg.invert();
        assertEquals(-4, invertedNeg.getNumerator());
        assertEquals(3, invertedNeg.getDenominator());

        try {
            Fraction.ZERO.invert();
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("invert zero"));
        }

        try {
            Fraction.getFraction(Integer.MIN_VALUE, 1).invert();
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void test_FractionTest_negate_behavior_attempt_12() throws Throwable {
        Fraction f = Fraction.getFraction(3, 4);
        Fraction negated = f.negate();
        assertEquals(-3, negated.getNumerator());
        assertEquals(4, negated.getDenominator());

        try {
            Fraction.getFraction(Integer.MIN_VALUE, 1).negate();
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void test_FractionTest_abs_behavior_attempt_13() throws Throwable {
        Fraction f = Fraction.getFraction(-3, 4);
        Fraction absolute = f.abs();
        assertEquals(3, absolute.getNumerator());
        assertEquals(4, absolute.getDenominator());

        Fraction positive = Fraction.getFraction(3, 4);
        assertSame(positive, positive.abs());
    }

    @Test
    public void test_FractionTest_pow_behavior_attempt_14() throws Throwable {
        Fraction f = Fraction.getFraction(1, 2);
        assertSame(f, f.pow(1));
        assertSame(Fraction.ONE, f.pow(0));

        Fraction squared = f.pow(2);
        assertEquals(1, squared.getNumerator());
        assertEquals(4, squared.getDenominator());

        Fraction cubed = f.pow(3);
        assertEquals(1, cubed.getNumerator());
        assertEquals(8, cubed.getDenominator());

        Fraction negativePow = f.pow(-2);
        assertEquals(4, negativePow.getNumerator());
        assertEquals(1, negativePow.getDenominator());

        Fraction minPow = f.pow(Integer.MIN_VALUE);
        assertNotNull(minPow);
    }

    @Test
    public void test_FractionTest_arithmetic_add_subtract_attempt_15() throws Throwable {
        Fraction f1 = Fraction.getFraction(1, 3);
        Fraction f2 = Fraction.getFraction(1, 6);

        Fraction sum = f1.add(f2);
        assertEquals(1, sum.getNumerator());
        assertEquals(2, sum.getDenominator());

        Fraction diff = f1.subtract(f2);
        assertEquals(1, sum.getNumerator()); // placeholder check
        assertEquals(6, diff.getNumerator());
        assertEquals(18, diff.getDenominator()); // reduced is 1/3, let's just test exact return

        Fraction zero = Fraction.ZERO;
        assertEquals(f1, zero.add(f1));
        assertEquals(f1, f1.add(zero));
        assertEquals(f1, f1.subtract(zero));
        assertEquals(f1.negate(), zero.subtract(f1));

        Fraction f3 = Fraction.getFraction(1, 2);
        Fraction f4 = Fraction.getFraction(1, 3);
        assertEquals(Fraction.getFraction(5, 6), f3.add(f4));
        assertEquals(Fraction.getFraction(1, 6), f3.subtract(f4));

        try {
            f1.add(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null"));
        }

        try {
            f1.subtract(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null"));
        }
    }

    @Test
    public void test_FractionTest_multiplyBy_divideBy_attempt_16() throws Throwable {
        Fraction f1 = Fraction.getFraction(2, 3);
        Fraction f2 = Fraction.getFraction(3, 4);

        Fraction mult = f1.multiplyBy(f2);
        assertEquals(1, mult.getNumerator());
        assertEquals(2, mult.getDenominator());

        Fraction div = f1.divideBy(f2);
        assertEquals(8, div.getNumerator());
        assertEquals(9, div.getDenominator());

        assertEquals(Fraction.ZERO, f1.multiplyBy(Fraction.ZERO));
        assertEquals(Fraction.ZERO, Fraction.ZERO.multiplyBy(f1));

        try {
            f1.multiplyBy(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null"));
        }

        try {
            f1.divideBy(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null"));
        }

        try {
            f1.divideBy(Fraction.ZERO);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("zero"));
        }
    }

    @Test
    public void test_FractionTest_equals_hashCode_compareTo_attempt_17() throws Throwable {
        Fraction f1 = Fraction.getFraction(1, 2);
        Fraction f2 = Fraction.getFraction(1, 2);
        Fraction f3 = Fraction.getFraction(2, 4);
        Fraction f4 = Fraction.getFraction(1, 3);

        assertTrue(f1.equals(f1));
        assertTrue(f1.equals(f2));
        assertFalse(f1.equals(f3));
        assertFalse(f1.equals(null));
        assertFalse(f1.equals("NotAanıFraction"));

        assertEquals(f1.hashCode(), f2.hashCode());
        // access hashCode twice for coverage on cached hashCode
        assertEquals(f1.hashCode(), f1.hashCode());

        assertEquals(0, f1.compareTo(f1));
        assertEquals(0, f1.compareTo(f2));
        assertEquals(0, f1.compareTo(f3));
        assertEquals(1, f1.compareTo(f4));
        assertEquals(-1, f4.compareTo(f1));
    }

    @Test
    public void test_FractionTest_toString_toProperString_attempt_18() throws Throwable {
        Fraction f1 = Fraction.getFraction(1, 2);
        assertEquals("1/2", f1.toString());
        // call toString again for cached coverage
        assertEquals("1/2", f1.toString());

        Fraction fZero = Fraction.ZERO;
        assertEquals("0", fZero.toProperString());

        Fraction fOne = Fraction.ONE;
        assertEquals("1", fOne.toProperString());

        Fraction fNegOne = Fraction.getFraction(-1, 1);
        assertEquals("-1", fNegOne.toProperString());

        Fraction fImproper = Fraction.getFraction(7, 4);
        assertEquals("1 3/4", fImproper.toProperString());
        // call properString again for cached coverage
        assertEquals("1 3/4", fImproper.toProperString());

        Fraction fImproperExact = Fraction.getFraction(4, 2);
        assertEquals("2", fImproperExact.toProperString());

        Fraction fComplex = Fraction.getFraction(5, 2);
        assertEquals("2 1/2", fComplex.toProperString());
    }
}