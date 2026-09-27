package org.apache.commons.lang3.math;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        NumberUtils utils = new NumberUtils();
        assertNotNull(utils);
    }

    @Test
    public void testToIntString() throws Throwable {
        assertEquals(0, NumberUtils.toInt(null));
        assertEquals(0, NumberUtils.toInt(""));
        assertEquals(123, NumberUtils.toInt("123"));
        assertEquals(0, NumberUtils.toInt("invalid"));
    }

    @Test
    public void testToIntStringInt() throws Throwable {
        assertEquals(5, NumberUtils.toInt(null, 5));
        assertEquals(5, NumberUtils.toInt("", 5));
        assertEquals(123, NumberUtils.toInt("123", 5));
        assertEquals(5, NumberUtils.toInt("invalid", 5));
    }

    @Test
    public void testToLongString() throws Throwable {
        assertEquals(0L, NumberUtils.toLong(null));
        assertEquals(0L, NumberUtils.toLong(""));
        assertEquals(123L, NumberUtils.toLong("123"));
        assertEquals(0L, NumberUtils.toLong("invalid"));
    }

    @Test
    public void testToLongStringLong() throws Throwable {
        assertEquals(5L, NumberUtils.toLong(null, 5L));
        assertEquals(5L, NumberUtils.toLong("", 5L));
        assertEquals(123L, NumberUtils.toLong("123", 5L));
        assertEquals(5L, NumberUtils.toLong("invalid", 5L));
    }

    @Test
    public void testToFloatString() throws Throwable {
        assertEquals(0.0f, NumberUtils.toFloat(null), 0.0001f);
        assertEquals(0.0f, NumberUtils.toFloat(""), 0.0001f);
        assertEquals(1.5f, NumberUtils.toFloat("1.5"), 0.0001f);
        assertEquals(0.0f, NumberUtils.toFloat("invalid"), 0.0001f);
    }

    @Test
    public void testToFloatStringFloat() throws Throwable {
        assertEquals(1.1f, NumberUtils.toFloat(null, 1.1f), 0.0001f);
        assertEquals(1.1f, NumberUtils.toFloat("", 1.1f), 0.0001f);
        assertEquals(1.5f, NumberUtils.toFloat("1.5", 0.0f), 0.0001f);
        assertEquals(1.1f, NumberUtils.toFloat("invalid", 1.1f), 0.0001f);
    }

    @Test
    public void testToDoubleString() throws Throwable {
        assertEquals(0.0d, NumberUtils.toDouble(null), 0.0001d);
        assertEquals(0.0d, NumberUtils.toDouble(""), 0.0001d);
        assertEquals(1.5d, NumberUtils.toDouble("1.5"), 0.0001d);
        assertEquals(0.0d, NumberUtils.toDouble("invalid"), 0.0001d);
    }

    @Test
    public void testToDoubleStringDouble() throws Throwable {
        assertEquals(1.1d, NumberUtils.toDouble(null, 1.1d), 0.0001d);
        assertEquals(1.1d, NumberUtils.toDouble("", 1.1d), 0.0001d);
        assertEquals(1.5d, NumberUtils.toDouble("1.5", 0.0d), 0.0001d);
        assertEquals(1.1d, NumberUtils.toDouble("invalid", 1.1d), 0.0001d);
    }

    @Test
    public void testToByteString() throws Throwable {
        assertEquals((byte) 0, NumberUtils.toByte(null));
        assertEquals((byte) 0, NumberUtils.toByte(""));
        assertEquals((byte) 1, NumberUtils.toByte("1"));
        assertEquals((byte) 0, NumberUtils.toByte("invalid"));
    }

    @Test
    public void testToByteStringByte() throws Throwable {
        assertEquals((byte) 1, NumberUtils.toByte(null, (byte) 1));
        assertEquals((byte) 1, NumberUtils.toByte("", (byte) 1));
        assertEquals((byte) 1, NumberUtils.toByte("1", (byte) 0));
        assertEquals((byte) 1, NumberUtils.toByte("invalid", (byte) 1));
    }

    @Test
    public void testToShortString() throws Throwable {
        assertEquals((short) 0, NumberUtils.toShort(null));
        assertEquals((short) 0, NumberUtils.toShort(""));
        assertEquals((short) 1, NumberUtils.toShort("1"));
        assertEquals((short) 0, NumberUtils.toShort("invalid"));
    }

    @Test
    public void testToShortStringShort() throws Throwable {
        assertEquals((short) 1, NumberUtils.toShort(null, (short) 1));
        assertEquals((short) 1, NumberUtils.toShort("", (short) 1));
        assertEquals((short) 1, NumberUtils.toShort("1", (short) 0));
        assertEquals((short) 1, NumberUtils.toShort("invalid", (short) 1));
    }

    @Test
    public void testCreateNumber() throws Throwable {
        assertNull(NumberUtils.createNumber(null));

        boolean caught = false;
        try {
            NumberUtils.createNumber("   ");
        } catch (NumberFormatException e) {
            caught = true;
        }
        assertTrue(caught);

        assertNull(NumberUtils.createNumber("--123"));
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("0xA"));
        assertEquals(Integer.valueOf(-10), NumberUtils.createNumber("-0xA"));

        assertTrue(NumberUtils.createNumber("123L") instanceof Long);
        assertTrue(NumberUtils.createNumber("123l") instanceof Long);
        
        // Too big for long, should return BigInteger
        Number bigNum = NumberUtils.createNumber("9223372036854775808L");
        assertTrue(bigNum instanceof BigInteger);

        boolean invalidLong = false;
        try {
            NumberUtils.createNumber("123.45L");
        } catch (NumberFormatException e) {
            invalidLong = true;
        }
        assertTrue(invalidLong);

        assertTrue(NumberUtils.createNumber("1.5f") instanceof Float);
        assertTrue(NumberUtils.createNumber("1.5F") instanceof Float);
        assertTrue(NumberUtils.createNumber("1.5d") instanceof Double);
        assertTrue(NumberUtils.createNumber("1.5D") instanceof Double);

        // Scientific notation and defaults
        assertEquals(Integer.valueOf(123), NumberUtils.createNumber("123"));
        assertEquals(Long.valueOf(12345678900L), NumberUtils.createNumber("12345678900"));
        assertTrue(NumberUtils.createNumber("1.5E2") instanceof Float);
        assertTrue(NumberUtils.createNumber("1.5E2d") instanceof Double);
        assertTrue(NumberUtils.createNumber("1.5E2f") instanceof Float);
        
        // Exponent edge cases in createNumber
        boolean expErr1 = false;
        try {
            NumberUtils.createNumber("1e");
        } catch (NumberFormatException e) {
            expErr1 = true;
        }
        assertTrue(expErr1);

        boolean expErr2 = false;
        try {
            NumberUtils.createNumber("1.2.3");
        } catch (NumberFormatException e) {
            expErr2 = true;
        }
        assertTrue(expErr2);
    }

    @Test
    public void testCreateFloat() throws Throwable {
        assertNull(NumberUtils.createFloat(null));
        assertEquals(Float.valueOf(1.5f), NumberUtils.createFloat("1.5"));
    }

    @Test
    public void testCreateDouble() throws Throwable {
        assertNull(NumberUtils.createDouble(null));
        assertEquals(Double.valueOf(1.5d), NumberUtils.createDouble("1.5"));
    }

    @Test
    public void testCreateInteger() throws Throwable {
        assertNull(NumberUtils.createInteger(null));
        assertEquals(Integer.valueOf(10), NumberUtils.createInteger("10"));
        assertEquals(Integer.valueOf(10), NumberUtils.createInteger("0xA"));
    }

    @Test
    public void testCreateLong() throws Throwable {
        assertNull(NumberUtils.createLong(null));
        assertEquals(Long.valueOf(10L), NumberUtils.createLong("10"));
    }

    @Test
    public void testCreateBigInteger() throws Throwable {
        assertNull(NumberUtils.createBigInteger(null));
        assertEquals(new BigInteger("10"), NumberUtils.createBigInteger("10"));
    }

    @Test
    public void testCreateBigDecimal() throws Throwable {
        assertNull(NumberUtils.createBigDecimal(null));
        assertEquals(new BigDecimal("10.5"), NumberUtils.createBigDecimal("10.5"));

        boolean caught = false;
        try {
            NumberUtils.createBigDecimal("   ");
        } catch (NumberFormatException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testMinLongArray() throws Throwable {
        assertEquals(1L, NumberUtils.min(new long[]{3L, 1L, 2L}));
        assertEquals(-5L, NumberUtils.min(new long[]{-5L, 1L, 2L}));

        boolean ex1 = false;
        try {
            NumberUtils.min((long[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.min(new long[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMinIntArray() throws Throwable {
        assertEquals(1, NumberUtils.min(new int[]{3, 1, 2}));
        assertEquals(-5, NumberUtils.min(new int[]{-5, 1, 2}));

        boolean ex1 = false;
        try {
            NumberUtils.min((int[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.min(new int[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMinShortArray() throws Throwable {
        assertEquals((short) 1, NumberUtils.min(new short[]{3, 1, 2}));
        assertEquals((short) -5, NumberUtils.min(new short[]{-5, 1, 2}));

        boolean ex1 = false;
        try {
            NumberUtils.min((short[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.min(new short[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMinByteArray() throws Throwable {
        assertEquals((byte) 1, NumberUtils.min(new byte[]{3, 1, 2}));
        assertEquals((byte) -5, NumberUtils.min(new byte[]{-5, 1, 2}));

        boolean ex1 = false;
        try {
            NumberUtils.min((byte[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.min(new byte[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMinDoubleArray() throws Throwable {
        assertEquals(1.0d, NumberUtils.min(new double[]{3.0, 1.0, 2.0}), 0.0001d);
        assertTrue(Double.isNaN(NumberUtils.min(new double[]{3.0, Double.NaN, 2.0})));

        boolean ex1 = false;
        try {
            NumberUtils.min((double[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.min(new double[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMinFloatArray() throws Throwable {
        assertEquals(1.0f, NumberUtils.min(new float[]{3.0f, 1.0f, 2.0f}), 0.0001f);
        assertTrue(Float.isNaN(NumberUtils.min(new float[]{3.0f, Float.NaN, 2.0f})));

        boolean ex1 = false;
        try {
            NumberUtils.min((float[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.min(new float[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMaxLongArray() throws Throwable {
        assertEquals(3L, NumberUtils.max(new long[]{1L, 3L, 2L}));

        boolean ex1 = false;
        try {
            NumberUtils.max((long[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.max(new long[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMaxIntArray() throws Throwable {
        assertEquals(3, NumberUtils.max(new int[]{1, 3, 2}));

        boolean ex1 = false;
        try {
            NumberUtils.max((int[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.max(new int[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMaxShortArray() throws Throwable {
        assertEquals((short) 3, NumberUtils.max(new short[]{1, 3, 2}));

        boolean ex1 = false;
        try {
            NumberUtils.max((short[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.max(new short[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMaxByteArray() throws Throwable {
        assertEquals((byte) 3, NumberUtils.max(new byte[]{1, 3, 2}));

        boolean ex1 = false;
        try {
            NumberUtils.max((byte[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.max(new byte[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMaxDoubleArray() throws Throwable {
        assertEquals(3.0d, NumberUtils.max(new double[]{1.0, 3.0, 2.0}), 0.0001d);
        assertTrue(Double.isNaN(NumberUtils.max(new double[]{1.0, Double.NaN, 2.0})));

        boolean ex1 = false;
        try {
            NumberUtils.max((double[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.max(new double[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMaxFloatArray() throws Throwable {
        assertEquals(3.0f, NumberUtils.max(new float[]{1.0f, 3.0f, 2.0f}), 0.0001f);
        assertTrue(Float.isNaN(NumberUtils.max(new float[]{1.0f, Float.NaN, 2.0f})));

        boolean ex1 = false;
        try {
            NumberUtils.max((float[]) null);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            NumberUtils.max(new float[0]);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);
    }

    @Test
    public void testMin3Params() throws Throwable {
        assertEquals(1L, NumberUtils.min(3L, 1L, 2L));
        assertEquals(1L, NumberUtils.min(2L, 3L, 1L));
        assertEquals(1, NumberUtils.min(3, 1, 2));
        assertEquals(1, NumberUtils.min(2, 3, 1));
        assertEquals((short) 1, NumberUtils.min((short) 3, (short) 1, (short) 2));
        assertEquals((short) 1, NumberUtils.min((short) 2, (short) 3, (short) 1));
        assertEquals((byte) 1, NumberUtils.min((byte) 3, (byte) 1, (byte) 2));
        assertEquals((byte) 1, NumberUtils.min((byte) 2, (byte) 3, (byte) 1));
        assertEquals(1.0d, NumberUtils.min(3.0d, 1.0d, 2.0d), 0.0001d);
        assertEquals(1.0f, NumberUtils.min(3.0f, 1.0f, 2.0f), 0.0001f);
    }

    @Test
    public void testMax3Params() throws Throwable {
        assertEquals(3L, NumberUtils.max(1L, 3L, 2L));
        assertEquals(3L, NumberUtils.max(2L, 1L, 3L));
        assertEquals(3, NumberUtils.max(1, 3, 2));
        assertEquals(3, NumberUtils.max(2, 1, 3));
        assertEquals((short) 3, NumberUtils.max((short) 1, (short) 3, (short) 2));
        assertEquals((short) 3, NumberUtils.max((short) 2, (short) 1, (short) 3));
        assertEquals((byte) 3, NumberUtils.max((byte) 1, (byte) 3, (byte) 2));
        assertEquals((byte) 3, NumberUtils.max((byte) 2, (byte) 1, (byte) 3));
        assertEquals(3.0d, NumberUtils.max(1.0d, 3.0d, 2.0d), 0.0001d);
        assertEquals(3.0f, NumberUtils.max(1.0f, 3.0f, 2.0f), 0.0001f);
    }

    @Test
    public void testIsDigits() throws Throwable {
        assertFalse(NumberUtils.isDigits(null));
        assertFalse(NumberUtils.isDigits(""));
        assertTrue(NumberUtils.isDigits("12345"));
        assertFalse(NumberUtils.isDigits("123a45"));
        assertFalse(NumberUtils.isDigits("123.45"));
    }

    @Test
    public void testIsNumber() throws Throwable {
        assertFalse(NumberUtils.isNumber(null));
        assertFalse(NumberUtils.isNumber(""));
        
        // Hex
        assertTrue(NumberUtils.isNumber("0x0123456789abcdefABCDEF"));
        assertFalse(NumberUtils.isNumber("0x"));
        assertFalse(NumberUtils.isNumber("0xg"));

        // Valid numbers
        assertTrue(NumberUtils.isNumber("123"));
        assertTrue(NumberUtils.isNumber("-123"));
        assertTrue(NumberUtils.isNumber("123.45"));
        assertTrue(NumberUtils.isNumber("-123.45"));
        assertTrue(NumberUtils.isNumber("1e10"));
        assertTrue(NumberUtils.isNumber("1e+10"));
        assertTrue(NumberUtils.isNumber("1e-10"));
        assertTrue(NumberUtils.isNumber("123L"));
        assertTrue(NumberUtils.isNumber("123f"));
        assertTrue(NumberUtils.isNumber("123d"));
        assertTrue(NumberUtils.isNumber(".5"));

        // Invalid numbers
        assertFalse(NumberUtils.isNumber("123.45.67"));
        assertFalse(NumberUtils.isNumber("1e10e10"));
        assertFalse(NumberUtils.isNumber("1e"));
        assertFalse(NumberUtils.isNumber("e10"));
        assertFalse(NumberUtils.isNumber("123e+"));
        assertFalse(NumberUtils.isNumber("123+"));
        assertFalse(NumberUtils.isNumber("123."));
        assertFalse(NumberUtils.isNumber("123l")); // with exponent and l not allowed or similar constraints
        assertFalse(NumberUtils.isNumber("abc"));
        assertFalse(NumberUtils.isNumber("1234.56.78"));
        assertFalse(NumberUtils.isNumber("-"));
        assertFalse(NumberUtils.isNumber("0x123l"));
    }
}