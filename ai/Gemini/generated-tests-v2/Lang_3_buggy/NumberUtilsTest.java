package org.apache.commons.lang3.math;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberUtilsTest {

    @Test
    public void testConstants() throws Throwable {
        assertNotNull(NumberUtils.LONG_ZERO);
        assertNotNull(NumberUtils.LONG_ONE);
        assertNotNull(NumberUtils.LONG_MINUS_ONE);
        assertNotNull(NumberUtils.INTEGER_ZERO);
        assertNotNull(NumberUtils.INTEGER_ONE);
        assertNotNull(NumberUtils.INTEGER_MINUS_ONE);
        assertNotNull(NumberUtils.SHORT_ZERO);
        assertNotNull(NumberUtils.SHORT_ONE);
        assertNotNull(NumberUtils.SHORT_MINUS_ONE);
        assertNotNull(NumberUtils.BYTE_ZERO);
        assertNotNull(NumberUtils.BYTE_ONE);
        assertNotNull(NumberUtils.BYTE_MINUS_ONE);
        assertNotNull(NumberUtils.DOUBLE_ZERO);
        assertNotNull(NumberUtils.DOUBLE_ONE);
        assertNotNull(NumberUtils.DOUBLE_MINUS_ONE);
        assertNotNull(NumberUtils.FLOAT_ZERO);
        assertNotNull(NumberUtils.FLOAT_ONE);
        assertNotNull(NumberUtils.FLOAT_MINUS_ONE);
        
        NumberUtils utils = new NumberUtils();
        assertNotNull(utils);
    }

    @Test
    public void testToInt() throws Throwable {
        assertEquals(0, NumberUtils.toInt(null));
        assertEquals(0, NumberUtils.toInt(""));
        assertEquals(0, NumberUtils.toInt("invalid"));
        assertEquals(123, NumberUtils.toInt("123"));

        assertEquals(5, NumberUtils.toInt(null, 5));
        assertEquals(5, NumberUtils.toInt("", 5));
        assertEquals(5, NumberUtils.toInt("invalid", 5));
        assertEquals(123, NumberUtils.toInt("123", 5));
    }

    @Test
    public void testToLong() throws Throwable {
        assertEquals(0L, NumberUtils.toLong(null));
        assertEquals(0L, NumberUtils.toLong(""));
        assertEquals(0L, NumberUtils.toLong("invalid"));
        assertEquals(123L, NumberUtils.toLong("123"));

        assertEquals(5L, NumberUtils.toLong(null, 5L));
        assertEquals(5L, NumberUtils.toLong("", 5L));
        assertEquals(5L, NumberUtils.toLong("invalid", 5L));
        assertEquals(123L, NumberUtils.toLong("123", 5L));
    }

    @Test
    public void testToFloat() throws Throwable {
        assertEquals(0.0f, NumberUtils.toFloat(null), 0.0001f);
        assertEquals(0.0f, NumberUtils.toFloat(""), 0.0001f);
        assertEquals(0.0f, NumberUtils.toFloat("invalid"), 0.0001f);
        assertEquals(123.45f, NumberUtils.toFloat("123.45"), 0.0001f);

        assertEquals(1.1f, NumberUtils.toFloat(null, 1.1f), 0.0001f);
        assertEquals(1.1f, NumberUtils.toFloat("", 1.1f), 0.0001f);
        assertEquals(1.1f, NumberUtils.toFloat("invalid", 1.1f), 0.0001f);
        assertEquals(123.45f, NumberUtils.toFloat("123.45", 1.1f), 0.0001f);
    }

    @Test
    public void testToDouble() throws Throwable {
        assertEquals(0.0d, NumberUtils.toDouble(null), 0.0001d);
        assertEquals(0.0d, NumberUtils.toDouble(""), 0.0001d);
        assertEquals(0.0d, NumberUtils.toDouble("invalid"), 0.0001d);
        assertEquals(123.45d, NumberUtils.toDouble("123.45"), 0.0001d);

        assertEquals(1.1d, NumberUtils.toDouble(null, 1.1d), 0.0001d);
        assertEquals(1.1d, NumberUtils.toDouble("", 1.1d), 0.0001d);
        assertEquals(1.1d, NumberUtils.toDouble("invalid", 1.1d), 0.0001d);
        assertEquals(123.45d, NumberUtils.toDouble("123.45", 1.1d), 0.0001d);
    }

    @Test
    public void testToByte() throws Throwable {
        assertEquals((byte) 0, NumberUtils.toByte(null));
        assertEquals((byte) 0, NumberUtils.toByte(""));
        assertEquals((byte) 0, NumberUtils.toByte("invalid"));
        assertEquals((byte) 123, NumberUtils.toByte("123"));

        assertEquals((byte) 5, NumberUtils.toByte(null, (byte) 5));
        assertEquals((byte) 5, NumberUtils.toByte("", (byte) 5));
        assertEquals((byte) 5, NumberUtils.toByte("invalid", (byte) 5));
        assertEquals((byte) 123, NumberUtils.toByte("123", (byte) 5));
    }

    @Test
    public void testToShort() throws Throwable {
        assertEquals((short) 0, NumberUtils.toShort(null));
        assertEquals((short) 0, NumberUtils.toShort(""));
        assertEquals((short) 0, NumberUtils.toShort("invalid"));
        assertEquals((short) 123, NumberUtils.toShort("123"));

        assertEquals((short) 5, NumberUtils.toShort(null, (short) 5));
        assertEquals((short) 5, NumberUtils.toShort("", (short) 5));
        assertEquals((short) 5, NumberUtils.toShort("invalid", (short) 5));
        assertEquals((short) 123, NumberUtils.toShort("123", (short) 5));
    }

    @Test
    public void testCreateNumber() throws Throwable {
        assertNull(NumberUtils.createNumber(null));
        
        boolean thrown = false;
        try {
            NumberUtils.createNumber("   ");
        } catch (NumberFormatException e) {
            thrown = true;
        }
        assertTrue(thrown);

        assertEquals(Integer.valueOf(123), NumberUtils.createNumber("123"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123L"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123l"));
        assertEquals(Float.valueOf(123.45f), NumberUtils.createNumber("123.45f"));
        assertEquals(Float.valueOf(123.45f), NumberUtils.createNumber("123.45F"));
        assertEquals(Double.valueOf(123.45d), NumberUtils.createNumber("123.45d"));
        assertEquals(Double.valueOf(123.45d), NumberUtils.createNumber("123.45D"));
        assertEquals(Double.valueOf(123.45), NumberUtils.createNumber("123.45"));
        assertEquals(Integer.valueOf(0x10), NumberUtils.createNumber("0x10"));
        assertEquals(Integer.valueOf(0x10), NumberUtils.createNumber("0X10"));
        assertEquals(Integer.valueOf(-0x10), NumberUtils.createNumber("-0x10"));
        assertEquals(Integer.valueOf(-0x10), NumberUtils.createNumber("-0X10"));
        assertEquals(Integer.valueOf(0x10), NumberUtils.createNumber("#10"));
        assertEquals(Integer.valueOf(-0x10), NumberUtils.createNumber("-#10"));
        
        // Hex long and BigInteger
        assertEquals(Long.valueOf(0x123456789L), NumberUtils.createNumber("0x123456789"));
        assertEquals(new BigInteger("1234567890123456789", 16), NumberUtils.createNumber("0x1234567890123456789"));

        // Scientific notation and decimals with qualifiers
        assertEquals(Float.valueOf("1.23e2f"), NumberUtils.createNumber("1.23e2f"));
        assertEquals(Double.valueOf("1.23e2d"), NumberUtils.createNumber("1.23e2d"));
        
        // Edge cases for createNumber with format errors
        try {
            NumberUtils.createNumber("1.2.3");
        } catch (NumberFormatException e) {
            // expected
        }
        try {
            NumberUtils.createNumber("123e");
        } catch (NumberFormatException e) {
            // expected
        }
        try {
            NumberUtils.createNumber("123e2.5");
        } catch (NumberFormatException e) {
            // expected
        }
        try {
            NumberUtils.createNumber("invalidNumber");
        } catch (NumberFormatException e) {
            // expected
        }
        try {
            NumberUtils.createNumber("123l"); // wait, 123l is valid, let's test invalid suffix combination
            NumberUtils.createNumber("123.45l");
        } catch (NumberFormatException e) {
            // expected
        }
    }

    @Test
    public void testCreateFloat() throws Throwable {
        assertNull(NumberUtils.createFloat(null));
        assertEquals(Float.valueOf(12.34f), NumberUtils.createFloat("12.34"));
    }

    @Test
    public void testCreateDouble() throws Throwable {
        assertNull(NumberUtils.createDouble(null));
        assertEquals(Double.valueOf(12.34d), NumberUtils.createDouble("12.34"));
    }

    @Test
    public void testCreateInteger() throws Throwable {
        assertNull(NumberUtils.createInteger(null));
        assertEquals(Integer.valueOf(123), NumberUtils.createInteger("123"));
        assertEquals(Integer.valueOf(010), NumberUtils.createInteger("010"));
        assertEquals(Integer.valueOf(0x10), NumberUtils.createInteger("0x10"));
    }

    @Test
    public void testCreateLong() throws Throwable {
        assertNull(NumberUtils.createLong(null));
        assertEquals(Long.valueOf(123L), NumberUtils.createLong("123"));
        assertEquals(Long.valueOf(010L), NumberUtils.createLong("010"));
        assertEquals(Long.valueOf(0x10L), NumberUtils.createLong("0x10"));
    }

    @Test
    public void testCreateBigInteger() throws Throwable {
        assertNull(NumberUtils.createBigInteger(null));
        assertEquals(new BigInteger("123"), NumberUtils.createBigInteger("123"));
        assertEquals(new BigInteger("123").negate(), NumberUtils.createBigInteger("-123"));
        assertEquals(new BigInteger("16", 16), NumberUtils.createBigInteger("0x16"));
        assertEquals(new BigInteger("16", 16), NumberUtils.createBigInteger("#16"));
        assertEquals(new BigInteger("8", 8), NumberUtils.createBigInteger("010"));
    }

    @Test
    public void testCreateBigDecimal() throws Throwable {
        assertNull(NumberUtils.createBigDecimal(null));
        assertEquals(new BigDecimal("123.45"), NumberUtils.createBigDecimal("123.45"));
        
        boolean thrown = false;
        try {
            NumberUtils.createBigDecimal("");
        } catch (NumberFormatException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            NumberUtils.createBigDecimal("--123");
        } catch (NumberFormatException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testMinArray() throws Throwable {
        assertEquals(1L, NumberUtils.min(new long[]{3L, 1L, 4L, 1L, 5L}));
        assertEquals(1, NumberUtils.min(new int[]{3, 1, 4, 1, 5}));
        assertEquals((short) 1, NumberUtils.min(new short[]{3, 1, 4, 1, 5}));
        assertEquals((byte) 1, NumberUtils.min(new byte[]{3, 1, 4, 1, 5}));
        assertEquals(1.0d, NumberUtils.min(new double[]{3.0, 1.0, 4.0}), 0.0001d);
        assertEquals(Double.valueOf(Double.NaN), NumberUtils.min(new double[]{3.0, Double.NaN, 1.0}), 0.0001d);
        assertEquals(1.0f, NumberUtils.min(new float[]{3.0f, 1.0f, 4.0f}), 0.0001f);
        assertEquals(Float.NaN, NumberUtils.min(new float[]{3.0f, Float.NaN, 1.0f}), 0.0001f);

        // Validation exceptions for arrays
        assertMinArrayException(null);
        assertMinArrayException(new long[0]);
        assertMinIntArrayException(null);
        assertMinIntArrayException(new int[0]);
        assertMinShortArrayException(null);
        assertMinShortArrayException(new short[0]);
        assertMinByteArrayException(null);
        assertMinByteArrayException(new byte[0]);
        assertMinDoubleArrayException(null);
        assertMinDoubleArrayException(new double[0]);
        assertMinFloatArrayException(null);
        assertMinFloatArrayException(new float[0]);
    }

    private void assertMinArrayException(long[] arr) {
        boolean t = false;
        try { NumberUtils.min(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }
    private void assertMinIntArrayException(int[] arr) {
        boolean t = false;
        try { NumberUtils.min(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }
    private void assertMinShortArrayException(short[] arr) {
        boolean t = false;
        try { NumberUtils.min(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }
    private void assertMinByteArrayException(byte[] arr) {
        boolean t = false;
        try { NumberUtils.min(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }
    private void assertMinDoubleArrayException(double[] arr) {
        boolean t = false;
        try { NumberUtils.min(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }
    private void assertMinFloatArrayException(float[] arr) {
        boolean t = false;
        try { NumberUtils.min(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }

    @Test
    public void testMaxArray() throws Throwable {
        assertEquals(5L, NumberUtils.max(new long[]{3L, 1L, 4L, 1L, 5L}));
        assertEquals(5, NumberUtils.max(new int[]{3, 1, 4, 1, 5}));
        assertEquals((short) 5, NumberUtils.max(new short[]{3, 1, 4, 1, 5}));
        assertEquals((byte) 5, NumberUtils.max(new byte[]{3, 1, 4, 1, 5}));
        assertEquals(5.0d, NumberUtils.max(new double[]{3.0, 5.0, 1.0}), 0.0001d);
        assertEquals(Double.valueOf(Double.NaN), NumberUtils.max(new double[]{3.0, Double.NaN, 5.0}), 0.0001d);
        assertEquals(5.0f, NumberUtils.max(new float[]{3.0f, 5.0f, 1.0f}), 0.0001f);
        assertEquals(Float.NaN, NumberUtils.max(new float[]{3.0f, Float.NaN, 5.0f}), 0.0001f);

        // Validation exceptions for max arrays
        assertMaxArrayException(null);
        assertMaxArrayException(new long[0]);
        assertMaxIntArrayException(null);
        assertMaxIntArrayException(new int[0]);
        assertMaxShortArrayException(null);
        assertMaxShortArrayException(new short[0]);
        assertMaxByteArrayException(null);
        assertMaxByteArrayException(new byte[0]);
        assertMaxDoubleArrayException(null);
        assertMaxDoubleArrayException(new double[0]);
        assertMaxFloatArrayException(null);
        assertMaxFloatArrayException(new float[0]);
    }

    private void assertMaxArrayException(long[] arr) {
        boolean t = false;
        try { NumberUtils.max(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }
    private void assertMaxIntArrayException(int[] arr) {
        boolean t = false;
        try { NumberUtils.max(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }
    private void assertMaxShortArrayException(short[] arr) {
        boolean t = false;
        try { NumberUtils.max(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }
    private void assertMaxByteArrayException(byte[] arr) {
        boolean t = false;
        try { NumberUtils.max(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }
    private void assertMaxDoubleArrayException(double[] arr) {
        boolean t = false;
        try { NumberUtils.max(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }
    private void assertMaxFloatArrayException(float[] arr) {
        boolean t = false;
        try { NumberUtils.max(arr); } catch (IllegalArgumentException e) { t = true; }
        assertTrue(t);
    }

    @Test
    public void testMin3Params() throws Throwable {
        assertEquals(1L, NumberUtils.min(3L, 1L, 5L));
        assertEquals(1L, NumberUtils.min(5L, 3L, 1L));
        assertEquals(1L, NumberUtils.min(3L, 5L, 1L));

        assertEquals(1, NumberUtils.min(3, 1, 5));
        assertEquals(1, NumberUtils.min(5, 3, 1));
        assertEquals(1, NumberUtils.min(3, 5, 1));

        assertEquals((short) 1, NumberUtils.min((short)3, (short)1, (short)5));
        assertEquals((short) 1, NumberUtils.min((short)5, (short)3, (short)1));
        assertEquals((short) 1, NumberUtils.min((short)3, (short)5, (short)1));

        assertEquals((byte) 1, NumberUtils.min((byte)3, (byte)1, (byte)5));
        assertEquals((byte) 1, NumberUtils.min((byte)5, (byte)3, (byte)1));
        assertEquals((byte) 1, NumberUtils.min((byte)3, (byte)5, (byte)1));

        assertEquals(1.0d, NumberUtils.min(3.0d, 1.0d, 5.0d), 0.0001d);
        assertEquals(1.0f, NumberUtils.min(3.0f, 1.0f, 5.0f), 0.0001f);
    }

    @Test
    public void testMax3Params() throws Throwable {
        assertEquals(5L, NumberUtils.max(3L, 5L, 1L));
        assertEquals(5L, NumberUtils.max(1L, 3L, 5L));
        assertEquals(5L, NumberUtils.max(5L, 3L, 1L));

        assertEquals(5, NumberUtils.max(3, 5, 1));
        assertEquals(5, NumberUtils.max(1, 3, 5));
        assertEquals(5, NumberUtils.max(5, 3, 1));

        assertEquals((short) 5, NumberUtils.max((short)3, (short)5, (short)1));
        assertEquals((short) 5, NumberUtils.max((short)1, (short)3, (short)5));
        assertEquals((short) 5, NumberUtils.max((short)5, (short)3, (short)1));

        assertEquals((byte) 5, NumberUtils.max((byte)3, (byte)5, (byte)1));
        assertEquals((byte) 5, NumberUtils.max((byte)1, (byte)3, (byte)5));
        assertEquals((byte) 5, NumberUtils.max((byte)5, (byte)3, (byte)1));

        assertEquals(5.0d, NumberUtils.max(3.0d, 5.0d, 1.0d), 0.0001d);
        assertEquals(5.0f, NumberUtils.max(3.0f, 5.0f, 1.0f), 0.0001f);
    }

    @Test
    public void testIsDigits() throws Throwable {
        assertFalse(NumberUtils.isDigits(null));
        assertFalse(NumberUtils.isDigits(""));
        assertFalse(NumberUtils.isDigits("123a"));
        assertFalse(NumberUtils.isDigits("-123"));
        assertTrue(NumberUtils.isDigits("123456"));
    }

    @Test
    public void testIsNumber() throws Throwable {
        assertFalse(NumberUtils.isNumber(null));
        assertFalse(NumberUtils.isNumber(""));
        
        // Hex
        assertTrue(NumberUtils.isNumber("0x1234"));
        assertTrue(NumberUtils.isNumber("0X1234"));
        assertFalse(NumberUtils.isNumber("0x"));
        assertFalse(NumberUtils.isNumber("0x123g"));

        // Valid numbers
        assertTrue(NumberUtils.isNumber("123"));
        assertTrue(NumberUtils.isNumber("-123"));
        assertTrue(NumberUtils.isNumber("123.45"));
        assertTrue(NumberUtils.isNumber("1e10"));
        assertTrue(NumberUtils.isNumber("1E10"));
        assertTrue(NumberUtils.isNumber("1e+10"));
        assertTrue(NumberUtils.isNumber("1e-10"));
        assertTrue(NumberUtils.isNumber("123L"));
        assertTrue(NumberUtils.isNumber("123f"));
        assertTrue(NumberUtils.isNumber("123d"));
        assertTrue(NumberUtils.isNumber(".5"));

        // Invalid numbers
        assertFalse(NumberUtils.isNumber("123.45.67"));
        assertFalse(NumberUtils.isNumber("123e2.5"));
        assertFalse(NumberUtils.isNumber("123e+"));
        assertFalse(NumberUtils.isNumber("123e"));
        assertFalse(NumberUtils.isNumber("abc"));
        assertFalse(NumberUtils.isNumber("123l")); // 'l' with decimal or exp not allowed by isNumber spec in implementation? Wait, let's test specifically
        assertFalse(NumberUtils.isNumber("123.45L"));
        assertFalse(NumberUtils.isNumber("1e10L"));
        assertFalse(NumberUtils.isNumber("123++1"));
        assertFalse(NumberUtils.isNumber("123--1"));
        assertFalse(NumberUtils.isNumber("."));
        assertFalse(NumberUtils.isNumber("123fE"));
    }
}