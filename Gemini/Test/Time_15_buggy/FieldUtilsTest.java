package org.joda.time.field;

import junit.framework.TestCase;
import org.joda.time.DateTimeFieldType;
import org.joda.time.IllegalFieldValueException;

public class TestFieldUtils extends TestCase {

    public TestFieldUtils(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testSafeNegate() throws Throwable {
        assertEquals(0, FieldUtils.safeNegate(0));
        assertEquals(-5, FieldUtils.safeNegate(5));
        assertEquals(5, FieldUtils.safeNegate(-5));
        assertEquals(Integer.MAX_VALUE, FieldUtils.safeNegate(-Integer.MAX_VALUE));

        try {
            FieldUtils.safeNegate(Integer.MIN_VALUE);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("Integer.MIN_VALUE"));
        }
    }

    public void testSafeAddInt() throws Throwable {
        assertEquals(5, FieldUtils.safeAdd(2, 3));
        assertEquals(0, FieldUtils.safeAdd(-5, 5));
        assertEquals(-10, FieldUtils.safeAdd(-5, -5));

        try {
            FieldUtils.safeAdd(Integer.MAX_VALUE, 1);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        try {
            FieldUtils.safeAdd(Integer.MIN_VALUE, -1);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    public void testSafeAddLong() throws Throwable {
        assertEquals(5L, FieldUtils.safeAdd(2L, 3L));
        assertEquals(0L, FieldUtils.safeAdd(-5L, 5L));
        assertEquals(-10L, FieldUtils.safeAdd(-5L, -5L));

        try {
            FieldUtils.safeAdd(Long.MAX_VALUE, 1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        try {
            FieldUtils.safeAdd(Long.MIN_VALUE, -1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    public void testSafeSubtractLong() throws Throwable {
        assertEquals(2L, FieldUtils.safeSubtract(5L, 3L));
        assertEquals(-10L, FieldUtils.safeSubtract(-5L, 5L));
        assertEquals(0L, FieldUtils.safeSubtract(5L, 5L));

        try {
            FieldUtils.safeSubtract(Long.MIN_VALUE, 1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }

        try {
            FieldUtils.safeSubtract(Long.MAX_VALUE, -1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    public void testSafeMultiplyInt() throws Throwable {
        assertEquals(6, FieldUtils.safeMultiply(2, 3));
        assertEquals(-6, FieldUtils.safeMultiply(-2, 3));
        assertEquals(0, FieldUtils.safeMultiply(0, 5));

        try {
            FieldUtils.safeMultiply(Integer.MAX_VALUE, 2);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("Multiplication overflows an int"));
        }

        try {
            FieldUtils.safeMultiply(Integer.MIN_VALUE, 2);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("Multiplication overflows an int"));
        }
    }

    public void testSafeMultiplyLongInt() throws Throwable {
        assertEquals(6L, FieldUtils.safeMultiply(2L, 3));
        assertEquals(-2L, FieldUtils.safeMultiply(2L, -1));
        assertEquals(0L, FieldUtils.safeMultiply(2L, 0));
        assertEquals(2L, FieldUtils.safeMultiply(2L, 1));

        try {
            FieldUtils.safeMultiply(Long.MAX_VALUE, 2);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("Multiplication overflows a long"));
        }
    }

    public void testSafeMultiplyLongLong() throws Throwable {
        assertEquals(6L, FieldUtils.safeMultiply(2L, 3L));
        assertEquals(2L, FieldUtils.safeMultiply(1L, 2L));
        assertEquals(2L, FieldUtils.safeMultiply(2L, 1L));
        assertEquals(0L, FieldUtils.safeMultiply(0L, 5L));
        assertEquals(0L, FieldUtils.safeMultiply(5L, 0L));

        try {
            FieldUtils.safeMultiply(Long.MAX_VALUE, 2L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("Multiplication overflows a long"));
        }

        try {
            FieldUtils.safeMultiply(Long.MIN_VALUE, -1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("Multiplication overflows a long"));
        }

        try {
            FieldUtils.safeMultiply(-1L, Long.MIN_VALUE);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("Multiplication overflows a long"));
        }
    }

    public void testSafeToInt() throws Throwable {
        assertEquals(5, FieldUtils.safeToInt(5L));
        assertEquals(Integer.MAX_VALUE, FieldUtils.safeToInt((long) Integer.MAX_VALUE));
        assertEquals(Integer.MIN_VALUE, FieldUtils.safeToInt((long) Integer.MIN_VALUE));

        try {
            FieldUtils.safeToInt((long) Integer.MAX_VALUE + 1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("Value cannot fit in an int"));
        }

        try {
            FieldUtils.safeToInt((long) Integer.MIN_VALUE - 1L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("Value cannot fit in an int"));
        }
    }

    public void testSafeMultiplyToInt() throws Throwable {
        assertEquals(6, FieldUtils.safeMultiplyToInt(2L, 3L));

        try {
            FieldUtils.safeMultiplyToInt((long) Integer.MAX_VALUE, 2L);
            fail("Expected ArithmeticException");
        } catch (ArithmeticException e) {
            // Expected
        }
    }

    public void testVerifyValueBoundsFieldType() throws Throwable {
        try {
            FieldUtils.verifyValueBounds(DateTimeFieldType.dayOfMonth(), 32, 1, 31);
            fail("Expected IllegalFieldValueException");
        } catch (IllegalFieldValueException e) {
            assertEquals(DateTimeFieldType.dayOfMonth(), e.getDateTimeFieldType());
            assertEquals(new Integer(32), e.getIllegalValue());
        }

        // Within bounds, should not throw
        FieldUtils.verifyValueBounds(DateTimeFieldType.dayOfMonth(), 15, 1, 31);
    }

    public void testVerifyValueBoundsString() throws Throwable {
        try {
            FieldUtils.verifyValueBounds("hourOfDay", 25, 0, 23);
            fail("Expected IllegalFieldValueException");
        } catch (IllegalFieldValueException e) {
            assertEquals("hourOfDay", e.getFieldName());
            assertEquals(new Integer(25), e.getIllegalValue());
        }

        // Within bounds, should not throw
        FieldUtils.verifyValueBounds("hourOfDay", 12, 0, 23);
    }

    public void testGetWrappedValueWithWrapValue() throws Throwable {
        assertEquals(2, FieldUtils.getWrappedValue(1, 1, 1, 3));
        assertEquals(1, FieldUtils.getWrappedValue(2, 2, 1, 3));
        assertEquals(3, FieldUtils.getWrappedValue(1, -1, 1, 3));
    }

    public void testGetWrappedValueRange() throws Throwable {
        assertEquals(2, FieldUtils.getWrappedValue(2, 1, 3));
        assertEquals(1, FieldUtils.getWrappedValue(4, 1, 3));
        assertEquals(3, FieldUtils.getWrappedValue(0, 1, 3));
        assertEquals(3, FieldUtils.getWrappedValue(-1, 1, 3));
        assertEquals(1, FieldUtils.getWrappedValue(-3, 1, 3));
        assertEquals(2, FieldUtils.getWrappedValue(-2, 1, 3));

        try {
            FieldUtils.getWrappedValue(1, 5, 3);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("MIN > MAX"));
        }

        try {
            FieldUtils.getWrappedValue(1, 3, 3);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("MIN > MAX"));
        }
    }

    public void testEqualsObject() throws Throwable {
        String s1 = "test";
        String s2 = "test";
        String s3 = "other";

        assertTrue(FieldUtils.equals(s1, s1));
        assertTrue(FieldUtils.equals(s1, s2));
        assertFalse(FieldUtils.equals(s1, s3));
        assertFalse(FieldUtils.equals(s1, null));
        assertFalse(FieldUtils.equals(null, s1));
        assertTrue(FieldUtils.equals(null, null));
    }
}