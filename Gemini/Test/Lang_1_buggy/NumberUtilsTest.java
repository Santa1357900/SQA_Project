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
    public void testToIntStringWithDefault() throws Throwable {
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
    public void testToLongStringWithDefault() throws Throwable {
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
    public void testToFloatStringWithDefault() throws Throwable {
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
    public void testToDoubleStringWithDefault() throws Throwable {
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
    public void testToByteStringWithDefault() throws Throwable {
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
    public void testToShortStringWithDefault() throws Throwable {
        assertEquals((short) 1, NumberUtils.toShort(null, (short) 1));
        assertEquals((short) 1, NumberUtils.toShort("", (short) 1));
        assertEquals((short) 1, NumberUtils.toShort("1", (short) 0));
        assertEquals((short) 1, NumberUtils.toShort("invalid", (short) 1));
    }

    @Test
    public void testCreateNumber() throws Throwable {
        assertNull(NumberUtils.createNumber(null));

        assertEquals(Integer.valueOf(123), NumberUtils.createNumber("123"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123L"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123l"));
        assertEquals(Float.valueOf(123.4f), NumberUtils.createNumber("123.4f"));
        assertEquals(Float.valueOf(123.4f), NumberUtils.createNumber("123.4F"));
        assertEquals(Double.valueOf(123.4d), NumberUtils.createNumber("123.4d"));
        assertEquals(Double.valueOf(123.4d), NumberUtils.createNumber("123.4D"));

        // Hex
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("0xA"));
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("0XA"));
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("-0xA"));
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("#A"));
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("-#A"));
        assertEquals(Long.valueOf(1234567890123L), NumberUtils.createNumber("0x1234567890123"));
        assertTrue(NumberUtils.createNumber("0x123456789012345678") instanceof BigInteger);

        // Float / Double fallback & exponent cases
        assertEquals(Float.valueOf(1e2f), NumberUtils.createNumber("1e2f"));
        assertEquals(Double.valueOf(1e2d), NumberUtils.createNumber("1e2d"));
        assertEquals(BigDecimal.class, NumberUtils.createNumber("1.234567890123456789").getClass());

        // Zero / Decimal with all zeros
        assertEquals(Float.valueOf(0.0f), NumberUtils.createNumber("0.0"));
        assertEquals(Double.valueOf(0.0d), NumberUtils.createNumber("0.00000000000000000"));

        // Explicit types with invalid fallbacks or triggers
        try {
            NumberUtils.createNumber("123.45.6");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }

        try {
            NumberUtils.createNumber("   ");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }

        try {
            NumberUtils.createNumber("1.2e3e4");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }

        try {
            NumberUtils.createNumber("1e");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }
        
        try {
            NumberUtils.createNumber("123L.0");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }
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
        assertEquals(Integer.valueOf(123), NumberUtils.createInteger("123"));
        assertEquals(Integer.valueOf(10), NumberUtils.createInteger("0xA"));
    }

    @Test
    public void testCreateLong() throws Throwable {
        assertNull(NumberUtils.createLong(null));
        assertEquals(Long.valueOf(123L), NumberUtils.createLong("123"));
        assertEquals(Long.valueOf(10L), NumberUtils.createLong("0xA"));
    }

    @Test
    public void testCreateBigInteger() throws Throwable {
        assertNull(NumberUtils.createBigInteger(null));
        assertEquals(BigInteger.valueOf(123), NumberUtils.createBigInteger("123"));
        assertEquals(BigInteger.valueOf(-123), NumberUtils.createBigInteger("-123"));
        assertEquals(BigInteger.valueOf(10), NumberUtils.createBigInteger("0xA"));
        assertEquals(BigInteger.valueOf(10), NumberUtils.createBigInteger("#A"));
        assertEquals(BigInteger.valueOf(8), NumberUtils.createBigInteger("010"));
    }

    @Test
    public void testCreateBigDecimal() throws Throwable {
        assertNull(NumberUtils.createBigDecimal(null));
        assertEquals(new BigDecimal("123.45"), NumberUtils.createBigDecimal("123.45"));

        try {
            NumberUtils.createBigDecimal("   ");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }

        try {
            NumberUtils.createBigDecimal("--123");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }
    }

    @Test
    public void testMinLongArray() throws Throwable {
        long[] arr = {5L, 2L, 9L, 1L, 7L};
        assertEquals(1L, NumberUtils.min(arr));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinLongArrayNull() throws Throwable {
        NumberUtils.min((long[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinLongArrayEmpty() throws Throwable {
        NumberUtils.min(new long[0]);
    }

    @Test
    public void testMinIntArray() throws Throwable {
        int[] arr = {5, 2, 9, 1, 7};
        assertEquals(1, NumberUtils.min(arr));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinIntArrayNull() throws Throwable {
        NumberUtils.min((int[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinIntArrayEmpty() throws Throwable {
        NumberUtils.min(new int[0]);
    }

    @Test
    public void testMinShortArray() throws Throwable {
        short[] arr = {5, 2, 9, 1, 7};
        assertEquals((short) 1, NumberUtils.min(arr));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinShortArrayNull() throws Throwable {
        NumberUtils.min((short[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinShortArrayEmpty() throws Throwable {
        NumberUtils.min(new short[0]);
    }

    @Test
    public void testMinByteArray() throws Throwable {
        byte[] arr = {5, 2, 9, 1, 7};
        assertEquals((byte) 1, NumberUtils.min(arr));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinByteArrayNull() throws Throwable {
        NumberUtils.min((byte[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinByteArrayEmpty() throws Throwable {
        NumberUtils.min(new byte[0]);
    }

    @Test
    public void testMinDoubleArray() throws Throwable {
        double[] arr = {5.0, 2.0, 9.0, 1.0};
        assertEquals(1.0, NumberUtils.min(arr), 0.0001);

        double[] nanArr = {5.0, Double.NaN, 1.0};
        assertTrue(Double.isNaN(NumberUtils.min(nanArr)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinDoubleArrayNull() throws Throwable {
        NumberUtils.min((double[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinDoubleArrayEmpty() throws Throwable {
        NumberUtils.min(new double[0]);
    }

    @Test
    public void testMinFloatArray() throws Throwable {
        float[] arr = {5.0f, 2.0f, 9.0f, 1.0f};
        assertEquals(1.0f, NumberUtils.min(arr), 0.0001f);

        float[] nanArr = {5.0f, Float.NaN, 1.0f};
        assertTrue(Float.isNaN(NumberUtils.min(nanArr)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinFloatArrayNull() throws Throwable {
        NumberUtils.min((float[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMinFloatArrayEmpty() throws Throwable {
        NumberUtils.min(new float[0]);
    }

    @Test
    public void testMaxLongArray() throws Throwable {
        long[] arr = {5L, 2L, 9L, 1L, 7L};
        assertEquals(9L, NumberUtils.max(arr));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxLongArrayNull() throws Throwable {
        NumberUtils.max((long[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxLongArrayEmpty() throws Throwable {
        NumberUtils.max(new long[0]);
    }

    @Test
    public void testMaxIntArray() throws Throwable {
        int[] arr = {5, 2, 9, 1, 7};
        assertEquals(9, NumberUtils.max(arr));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxIntArrayNull() throws Throwable {
        NumberUtils.max((int[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxIntArrayEmpty() throws Throwable {
        NumberUtils.max(new int[0]);
    }

    @Test
    public void testMaxShortArray() throws Throwable {
        short[] arr = {5, 2, 9, 1, 7};
        assertEquals((short) 9, NumberUtils.max(arr));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxShortArrayNull() throws Throwable {
        NumberUtils.max((short[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxShortArrayEmpty() throws Throwable {
        NumberUtils.max(new short[0]);
    }

    @Test
    public void testMaxByteArray() throws Throwable {
        byte[] arr = {5, 2, 9, 1, 7};
        assertEquals((byte) 9, NumberUtils.max(arr));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxByteArrayNull() throws Throwable {
        NumberUtils.max((byte[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxByteArrayEmpty() throws Throwable {
        NumberUtils.max(new byte[0]);
    }

    @Test
    public void testMaxDoubleArray() throws Throwable {
        double[] arr = {5.0, 2.0, 9.0, 1.0};
        assertEquals(9.0, NumberUtils.max(arr), 0.0001);

        double[] nanArr = {5.0, Double.NaN, 1.0};
        assertTrue(Double.isNaN(NumberUtils.max(nanArr)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxDoubleArrayNull() throws Throwable {
        NumberUtils.max((double[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxDoubleArrayEmpty() throws Throwable {
        NumberUtils.max(new double[0]);
    }

    @Test
    public void testMaxFloatArray() throws Throwable {
        float[] arr = {5.0f, 2.0f, 9.0f, 1.0f};
        assertEquals(9.0f, NumberUtils.max(arr), 0.0001f);

        float[] nanArr = {5.0f, Float.NaN, 1.0f};
        assertTrue(Float.isNaN(NumberUtils.max(nanArr)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxFloatArrayNull() throws Throwable {
        NumberUtils.max((float[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxFloatArrayEmpty() throws Throwable {
        NumberUtils.max(new float[0]);
    }

    @Test
    public void testMin3Params() throws Throwable {
        assertEquals(1L, NumberUtils.min(3L, 2L, 1L));
        assertEquals(1L, NumberUtils.min(2L, 1L, 3L));
        assertEquals(1L, NumberUtils.min(1L, 3L, 2L));

        assertEquals(1, NumberUtils.min(3, 2, 1));
        assertEquals(1, NumberUtils.min(2, 1, 3));
        assertEquals(1, NumberUtils.min(1, 3, 2));

        assertEquals((short) 1, NumberUtils.min((short) 3, (short) 2, (short) 1));
        assertEquals((short) 1, NumberUtils.min((short) 2, (short) 1, (short) 3));
        assertEquals((short) 1, NumberUtils.min((short) 1, (short) 3, (short) 2));

        assertEquals((byte) 1, NumberUtils.min((byte) 3, (byte) 2, (byte) 1));
        assertEquals((byte) 1, NumberUtils.min((byte) 2, (byte) 1, (byte) 3));
        assertEquals((byte) 1, NumberUtils.min((byte) 1, (byte) 3, (byte) 2));

        assertEquals(1.0, NumberUtils.min(3.0, 2.0, 1.0), 0.0001);
        assertEquals(1.0f, NumberUtils.min(3.0f, 2.0f, 1.0f), 0.0001f);
    }

    @Test
    public void testMax3Params() throws Throwable {
        assertEquals(3L, NumberUtils.max(1L, 2L, 3L));
        assertEquals(3L, NumberUtils.max(2L, 3L, 1L));
        assertEquals(3L, NumberUtils.max(3L, 1L, 2L));

        assertEquals(3, NumberUtils.max(1, 2, 3));
        assertEquals(3, NumberUtils.max(2, 3, 1));
        assertEquals(3, NumberUtils.max(3, 1, 2));

        assertEquals((short) 3, NumberUtils.max((short) 1, (short) 2, (short) 3));
        assertEquals((short) 3, NumberUtils.max((short) 2, (short) 3, (short) 1));
        assertEquals((short) 3, NumberUtils.max((short) 3, (short) 1, (short) 2));

        assertEquals((byte) 3, NumberUtils.max((byte) 1, (byte) 2, (byte) 3));
        assertEquals((byte) 3, NumberUtils.max((byte) 2, (byte) 3, (byte) 1));
        assertEquals((byte) 3, NumberUtils.max((byte) 3, (byte) 1, (byte) 2));

        assertEquals(3.0, NumberUtils.max(1.0, 2.0, 3.0), 0.0001);
        assertEquals(3.0f, NumberUtils.max(1.0f, 2.0f, 3.0f), 0.0001f);
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
        assertTrue(NumberUtils.isNumber("123"));
        assertTrue(NumberUtils.isNumber("-123"));
        assertTrue(NumberUtils.isNumber("123.45"));
        assertTrue(NumberUtils.isNumber("1e10"));
        assertTrue(NumberUtils.isNumber("1E10"));
        assertTrue(NumberUtils.isNumber("0xA"));
        assertTrue(NumberUtils.isNumber("-0xA"));
        assertTrue(NumberUtils.isNumber("123l"));
        assertTrue(NumberUtils.isNumber("123L"));
        assertTrue(NumberUtils.isNumber("123f"));
        assertTrue(NumberUtils.isNumber("123F"));
        assertTrue(NumberUtils.isNumber("123d"));
        assertTrue(NumberUtils.isNumber("123D"));

        // Invalid numbers
        assertFalse(NumberUtils.isNumber("0x"));
        assertFalse(NumberUtils.isNumber("0xG"));
        assertFalse(NumberUtils.isNumber("123..45"));
        assertFalse(NumberUtils.isNumber("1e1e1"));
        assertFalse(NumberUtils.isNumber("1e"));
        assertFalse(NumberUtils.isNumber("1e+"));
        assertFalse(NumberUtils.isNumber("."));
        assertFalse(NumberUtils.isNumber("-."));
        assertFalse(NumberUtils.isNumber("123a"));
        assertFalse(NumberUtils.isNumber("123l.0"));
        assertFalse(NumberUtils.isNumber("+123"));
        assertFalse(NumberUtils.isNumber("123e4l"));
    }
}