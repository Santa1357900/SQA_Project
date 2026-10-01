package org.joda.time.field;

import org.junit.Test;
import static org.junit.Assert.*;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeFieldType;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.chrono.ISOChronology;

public class FieldUtilsClaudeTest {

    // safeNegate: normal positive/negative value branch
    @Test
    public void testSafeNegate_normalValue_returnsNegated() throws Throwable {
        assertEquals(-5, FieldUtils.safeNegate(5));
        assertEquals(5, FieldUtils.safeNegate(-5));
    }

    // safeNegate: Integer.MIN_VALUE branch throws ArithmeticException
    @Test
    public void testSafeNegate_minValue_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.safeNegate(Integer.MIN_VALUE);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // safeAdd(int,int): normal addition, no overflow
    @Test
    public void testSafeAddInt_normal_returnsSum() throws Throwable {
        assertEquals(7, FieldUtils.safeAdd(3, 4));
    }

    // safeAdd(int,int): sign-change overflow branch, both directions
    @Test
    public void testSafeAddInt_overflow_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.safeAdd(Integer.MAX_VALUE, 1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
        try {
            FieldUtils.safeAdd(Integer.MIN_VALUE, -1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // safeAdd(long,long): normal addition, no overflow
    @Test
    public void testSafeAddLong_normal_returnsSum() throws Throwable {
        assertEquals(7L, FieldUtils.safeAdd(3L, 4L));
    }

    // safeAdd(long,long): sign-change overflow branch, both directions
    @Test
    public void testSafeAddLong_overflow_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.safeAdd(Long.MAX_VALUE, 1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
        try {
            FieldUtils.safeAdd(Long.MIN_VALUE, -1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // safeSubtract: normal subtraction, no overflow
    @Test
    public void testSafeSubtract_normal_returnsDifference() throws Throwable {
        assertEquals(1L, FieldUtils.safeSubtract(4L, 3L));
    }

    // safeSubtract: overflow branch (MIN_VALUE - positive)
    @Test
    public void testSafeSubtract_overflow_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.safeSubtract(Long.MIN_VALUE, 1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // safeMultiply(int,int): normal multiplication, fits in int
    @Test
    public void testSafeMultiplyIntInt_normal_returnsProduct() throws Throwable {
        assertEquals(12, FieldUtils.safeMultiply(3, 4));
    }

    // safeMultiply(int,int): total exceeds int range, throws
    @Test
    public void testSafeMultiplyIntInt_overflow_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.safeMultiply(Integer.MAX_VALUE, 2);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // safeMultiply(long,int): switch case -1 branch
    @Test
    public void testSafeMultiplyLongInt_valTwoMinusOne_returnsNegated() throws Throwable {
        assertEquals(-5L, FieldUtils.safeMultiply(5L, -1));
    }

    // safeMultiply(long,int): switch case 0 branch
    @Test
    public void testSafeMultiplyLongInt_valTwoZero_returnsZero() throws Throwable {
        assertEquals(0L, FieldUtils.safeMultiply(123L, 0));
    }

    // safeMultiply(long,int): switch case 1 branch
    @Test
    public void testSafeMultiplyLongInt_valTwoOne_returnsVal1() throws Throwable {
        assertEquals(123L, FieldUtils.safeMultiply(123L, 1));
    }

    // safeMultiply(long,int): default branch, normal multiplication
    @Test
    public void testSafeMultiplyLongInt_defaultBranch_returnsProduct() throws Throwable {
        assertEquals(15L, FieldUtils.safeMultiply(5L, 3));
    }

    // safeMultiply(long,int): default branch, overflow (total/val2 != val1)
    @Test
    public void testSafeMultiplyLongInt_overflow_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.safeMultiply(Long.MAX_VALUE, 2);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // safeMultiply(long,long): val2 == 1 fast-path branch
    @Test
    public void testSafeMultiplyLongLong_valTwoOne_returnsVal1() throws Throwable {
        assertEquals(42L, FieldUtils.safeMultiply(42L, 1L));
    }

    // safeMultiply(long,long): val1 == 1 fast-path branch
    @Test
    public void testSafeMultiplyLongLong_valOneOne_returnsVal2() throws Throwable {
        assertEquals(42L, FieldUtils.safeMultiply(1L, 42L));
    }

    // safeMultiply(long,long): val1 == 0 fast-path branch
    @Test
    public void testSafeMultiplyLongLong_valOneZero_returnsZero() throws Throwable {
        assertEquals(0L, FieldUtils.safeMultiply(0L, 99L));
    }

    // safeMultiply(long,long): val2 == 0 fast-path branch
    @Test
    public void testSafeMultiplyLongLong_valTwoZero_returnsZero() throws Throwable {
        assertEquals(0L, FieldUtils.safeMultiply(99L, 0L));
    }

    // safeMultiply(long,long): normal multiplication, no overflow
    @Test
    public void testSafeMultiplyLongLong_normal_returnsProduct() throws Throwable {
        assertEquals(35L, FieldUtils.safeMultiply(5L, 7L));
    }

    // safeMultiply(long,long): total/val2 != val1 overflow branch
    @Test
    public void testSafeMultiplyLongLong_overflow_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.safeMultiply(Long.MAX_VALUE, 2L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // safeMultiply(long,long): special MIN_VALUE * -1 overflow branch
    @Test
    public void testSafeMultiplyLongLong_minValueTimesMinusOne_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.safeMultiply(Long.MIN_VALUE, -1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // safeToInt: value well within int range
    @Test
    public void testSafeToInt_withinRange_returnsInt() throws Throwable {
        assertEquals(100, FieldUtils.safeToInt(100L));
    }

    // safeToInt: inclusive boundary values MIN_VALUE and MAX_VALUE
    @Test
    public void testSafeToInt_boundaries_returnsInt() throws Throwable {
        assertEquals(Integer.MAX_VALUE, FieldUtils.safeToInt((long) Integer.MAX_VALUE));
        assertEquals(Integer.MIN_VALUE, FieldUtils.safeToInt((long) Integer.MIN_VALUE));
    }

    // safeToInt: value just outside range on both sides throws
    @Test
    public void testSafeToInt_outOfRange_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.safeToInt(((long) Integer.MAX_VALUE) + 1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
        try {
            FieldUtils.safeToInt(((long) Integer.MIN_VALUE) - 1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // safeMultiplyToInt: normal multiplication that fits in int
    @Test
    public void testSafeMultiplyToInt_normal_returnsProduct() throws Throwable {
        assertEquals(20, FieldUtils.safeMultiplyToInt(4L, 5L));
    }

    // safeMultiplyToInt: safeMultiply itself overflows a long, throws before toInt
    @Test
    public void testSafeMultiplyToInt_multiplyOverflow_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.safeMultiplyToInt(Long.MAX_VALUE, 2L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // safeMultiplyToInt: product fits in long but overflows int at safeToInt step
    @Test
    public void testSafeMultiplyToInt_toIntOverflow_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.safeMultiplyToInt(100000L, 100000L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // verifyValueBounds(DateTimeField,...): value inside bounds, no exception
    @Test
    public void testVerifyValueBoundsDateTimeField_withinBounds_noException() throws Throwable {
        DateTimeField field = ISOChronology.getInstanceUTC().dayOfMonth();
        FieldUtils.verifyValueBounds(field, 15, 1, 31);
    }

    // verifyValueBounds(DateTimeField,...): below lower and above upper bound throw
    @Test
    public void testVerifyValueBoundsDateTimeField_outOfBounds_throws() throws Throwable {
        DateTimeField field = ISOChronology.getInstanceUTC().dayOfMonth();
        try {
            FieldUtils.verifyValueBounds(field, 0, 1, 31);
            fail("expected IllegalFieldValueException");
        } catch (IllegalFieldValueException expected) {
        }
        try {
            FieldUtils.verifyValueBounds(field, 32, 1, 31);
            fail("expected IllegalFieldValueException");
        } catch (IllegalFieldValueException expected) {
        }
    }

    // verifyValueBounds(DateTimeFieldType,...): value inside bounds, no exception
    @Test
    public void testVerifyValueBoundsDateTimeFieldType_withinBounds_noException() throws Throwable {
        FieldUtils.verifyValueBounds(DateTimeFieldType.dayOfMonth(), 15, 1, 31);
    }

    // verifyValueBounds(DateTimeFieldType,...): out of bounds throws
    @Test
    public void testVerifyValueBoundsDateTimeFieldType_outOfBounds_throws() throws Throwable {
        try {
            FieldUtils.verifyValueBounds(DateTimeFieldType.dayOfMonth(), 32, 1, 31);
            fail("expected IllegalFieldValueException");
        } catch (IllegalFieldValueException expected) {
        }
    }

    // verifyValueBounds(String,...): value inside bounds, no exception
    @Test
    public void testVerifyValueBoundsString_withinBounds_noException() throws Throwable {
        FieldUtils.verifyValueBounds("myField", 5, 0, 10);
    }

    // verifyValueBounds(String,...): out of bounds throws
    @Test
    public void testVerifyValueBoundsString_outOfBounds_throws() throws Throwable {
        try {
            FieldUtils.verifyValueBounds("myField", -1, 0, 10);
            fail("expected IllegalFieldValueException");
        } catch (IllegalFieldValueException expected) {
        }
    }

    // getWrappedValue(int,int,int,int): normal case with no overflow in addition
    @Test
    public void testGetWrappedValueFourArgs_normal_returnsWrapped() throws Throwable {
        assertEquals(5, FieldUtils.getWrappedValue(3, 2, 0, 10));
    }

    // getWrappedValue(int,int,int,int): currentValue+wrapValue overflows int;
    // per safeAdd's documented contract this must raise ArithmeticException,
    // not silently wrap to a wrong value (bug-catching test for Time-15)
    @Test
    public void testGetWrappedValueFourArgs_additionOverflow_throwsArithmeticException() throws Throwable {
        try {
            FieldUtils.getWrappedValue(Integer.MAX_VALUE, 1, 0, 10);
            fail("expected ArithmeticException due to int overflow in addition");
        } catch (ArithmeticException expected) {
        }
    }

    // getWrappedValue(int,int,int): minValue >= maxValue throws IllegalArgumentException
    @Test
    public void testGetWrappedValueThreeArgs_minGreaterEqualMax_throwsIllegalArgumentException() throws Throwable {
        try {
            FieldUtils.getWrappedValue(5, 10, 10);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getWrappedValue(int,int,int): value - minValue >= 0 branch, modulo result
    @Test
    public void testGetWrappedValueThreeArgs_valueAboveMin_returnsModulo() throws Throwable {
        assertEquals(1, FieldUtils.getWrappedValue(11, 0, 9));
    }

    // getWrappedValue(int,int,int): value below min, remByRange != 0 branch
    @Test
    public void testGetWrappedValueThreeArgs_valueBelowMin_returnsWrappedFromTop() throws Throwable {
        assertEquals(9, FieldUtils.getWrappedValue(-1, 0, 9));
    }

    // getWrappedValue(int,int,int): value below min, remByRange == 0 branch
    @Test
    public void testGetWrappedValueThreeArgs_valueBelowMinExactMultiple_returnsMinValue() throws Throwable {
        assertEquals(0, FieldUtils.getWrappedValue(-10, 0, 9));
    }

    // equals: identical reference short-circuit branch
    @Test
    public void testEquals_sameReference_returnsTrue() throws Throwable {
        Object o = new Object();
        assertTrue(FieldUtils.equals(o, o));
    }

    // equals: both null returns true
    @Test
    public void testEquals_bothNull_returnsTrue() throws Throwable {
        assertTrue(FieldUtils.equals(null, null));
    }

    // equals: exactly one null returns false
    @Test
    public void testEquals_oneNull_returnsFalse() throws Throwable {
        assertFalse(FieldUtils.equals(null, "x"));
        assertFalse(FieldUtils.equals("x", null));
    }

    // equals: distinct but equal non-null objects returns true
    @Test
    public void testEquals_equalObjects_returnsTrue() throws Throwable {
        assertTrue(FieldUtils.equals("abc", new String("abc")));
    }

    // equals: different non-null objects returns false
    @Test
    public void testEquals_differentObjects_returnsFalse() throws Throwable {
        assertFalse(FieldUtils.equals("abc", "def"));
    }
}
