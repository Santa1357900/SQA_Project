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
    public void testToInt() throws Throwable {
        assertEquals(0, NumberUtils.toInt((String) null));
        assertEquals(0, NumberUtils.toInt(""));
        assertEquals(123, NumberUtils.toInt("123"));
        assertEquals(0, NumberUtils.toInt("invalid"));

        assertEquals(5, NumberUtils.toInt(null, 5));
        assertEquals(5, NumberUtils.toInt("", 5));
        assertEquals(123, NumberUtils.toInt("123", 5));
        assertEquals(5, NumberUtils.toInt("invalid", 5));
    }

    @Test
    public void testToLong() throws Throwable {
        assertEquals(0L, NumberUtils.toLong((String) null));
        assertEquals(0L, NumberUtils.toLong(""));
        assertEquals(123L, NumberUtils.toLong("123"));
        assertEquals(0L, NumberUtils.toLong("invalid"));

        assertEquals(5L, NumberUtils.toLong(null, 5L));
        assertEquals(5L, NumberUtils.toLong("", 5L));
        assertEquals(123L, NumberUtils.toLong("123", 5L));
        assertEquals(5L, NumberUtils.toLong("invalid", 5L));
    }

    @Test
    public void testToFloat() throws Throwable {
        assertEquals(0.0f, NumberUtils.toFloat((String) null), 0.0001f);
        assertEquals(0.0f, NumberUtils.toFloat(""), 0.0001f);
        assertEquals(1.5f, NumberUtils.toFloat("1.5"), 0.0001f);
        assertEquals(0.0f, NumberUtils.toFloat("invalid"), 0.0001f);

        assertEquals(1.1f, NumberUtils.toFloat(null, 1.1f), 0.0001f);
        assertEquals(1.1f, NumberUtils.toFloat("", 1.1f), 0.0001f);
        assertEquals(1.5f, NumberUtils.toFloat("1.5", 0.0f), 0.0001f);
        assertEquals(1.1f, NumberUtils.toFloat("invalid", 1.1f), 0.0001f);
    }

    @Test
    public void testToDouble() throws Throwable {
        assertEquals(0.0d, NumberUtils.toDouble((String) null), 0.0001d);
        assertEquals(0.0d, NumberUtils.toDouble(""), 0.0001d);
        assertEquals(1.5d, NumberUtils.toDouble("1.5"), 0.0001d);
        assertEquals(0.0d, NumberUtils.toDouble("invalid"), 0.0001d);

        assertEquals(1.1d, NumberUtils.toDouble(null, 1.1d), 0.0001d);
        assertEquals(1.1d, NumberUtils.toDouble("", 1.1d), 0.0001d);
        assertEquals(1.5d, NumberUtils.toDouble("1.5", 0.0d), 0.0001d);
        assertEquals(1.1d, NumberUtils.toDouble("invalid", 1.1d), 0.0001d);
    }

    @Test
    public void testToByte() throws Throwable {
        assertEquals((byte) 0, NumberUtils.toByte((String) null));
        assertEquals((byte) 0, NumberUtils.toByte(""));
        assertEquals((byte) 1, NumberUtils.toByte("1"));
        assertEquals((byte) 0, NumberUtils.toByte("invalid"));

        assertEquals((byte) 1, NumberUtils.toByte(null, (byte) 1));
        assertEquals((byte) 1, NumberUtils.toByte("", (byte) 1));
        assertEquals((byte) 1, NumberUtils.toByte("1", (byte) 0));
        assertEquals((byte) 1, NumberUtils.toByte("invalid", (byte) 1));
    }

    @Test
    public void testToShort() throws Throwable {
        assertEquals((short) 0, NumberUtils.toShort((String) null));
        assertEquals((short) 0, NumberUtils.toShort(""));
        assertEquals((short) 1, NumberUtils.toShort("1"));
        assertEquals((short) 0, NumberUtils.toShort("invalid"));

        assertEquals((short) 1, NumberUtils.toShort(null, (short) 1));
        assertEquals((short) 1, NumberUtils.toShort("", (short) 1));
        assertEquals((short) 1, NumberUtils.toShort("1", (short) 0));
        assertEquals((short) 1, NumberUtils.toShort("invalid", (short) 1));
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
        assertEquals(Integer.valueOf(10), NumberUtils.createInteger("0xa"));
    }

    @Test
    public void testCreateLong() throws Throwable {
        assertNull(NumberUtils.createLong(null));
        assertEquals(Long.valueOf(123L), NumberUtils.createLong("123"));
    }

    @Test
    public void testCreateBigInteger() throws Throwable {
        assertNull(NumberUtils.createBigInteger(null));
        assertEquals(new BigInteger("123"), NumberUtils.createBigInteger("123"));
    }

    @Test
    public void testCreateBigDecimal() throws Throwable {
        assertNull(NumberUtils.createBigDecimal(null));
        assertEquals(new BigDecimal("123.45"), NumberUtils.createBigDecimal("123.45"));
    }

    @Test(expected = NumberFormatException.class)
    public void testCreateBigDecimalBlank() throws Throwable {
        NumberUtils.createBigDecimal("   ");
    }

    @Test
    public void testCreateNumber() throws Throwable {
        assertNull(NumberUtils.createNumber(null));
        assertNull(NumberUtils.createNumber("--123"));
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("0xA"));
        assertEquals(Integer.valueOf(-10), NumberUtils.createNumber("-0xA"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123L"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123l"));
        assertEquals(Float.valueOf(1.5f), NumberUtils.createNumber("1.5f"));
        assertEquals(Float.valueOf(1.5f), NumberUtils.createNumber("1.5F"));
        assertEquals(Double.valueOf(1.5d), NumberUtils.createNumber("1.5d"));
        assertEquals(Double.valueOf(1.5d), NumberUtils.createNumber("1.5D"));
        assertEquals(Integer.valueOf(123), NumberUtils.createNumber("123"));
        assertEquals(Float.valueOf(1.5f), NumberUtils.createNumber("1.5"));
        assertEquals(Double.valueOf(1.5e10d), NumberUtils.createNumber("1.5e10"));
        assertEquals(Double.valueOf(1.5e10d), NumberUtils.createNumber("1.5E10"));
        assertEquals(BigDecimal.class, NumberUtils.createNumber("1.5e1000").getClass());
    }

    @Test(expected = NumberFormatException.class)
    public void testCreateNumberBlank() throws Throwable {
        NumberUtils.createNumber("");
    }

    @Test(expected = NumberFormatException.class)
    public void testCreateNumberInvalidExp() throws Throwable {
        NumberUtils.createNumber("1.2.3");
    }

    @Test(expected = NumberFormatException.class)
    public void testCreateNumberInvalidLong() throws Throwable {
        NumberUtils.createNumber("1.2L");
    }

    @Test(expected = NumberFormatException.class)
    public void testCreateNumberInvalidDefault() throws Throwable {
        NumberUtils.createNumber("invalid_num");
    }

    @Test
    public void testMinLongArray() throws Throwable {
        assertEquals(1L, NumberUtils.min(new long[]{3L, 1L, 2L}));
        assertEquals(5L, NumberUtils.min(new long[]{5L}));
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
        assertEquals(1, NumberUtils.min(new int[]{3, 1, 2}));
        assertEquals(5, NumberUtils.min(new int[]{5}));
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
        assertEquals((short) 1, NumberUtils.min(new short[]{3, 1, 2}));
        assertEquals((short) 5, NumberUtils.min(new short[]{5}));
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
        assertEquals((byte) 1, NumberUtils.min(new byte[]{3, 1, 2}));
        assertEquals((byte) 5, NumberUtils.min(new byte[]{5}));
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
        assertEquals(1.0d, NumberUtils.min(new double[]{3.0d, 1.0d, 2.0d}), 0.0001d);
        assertTrue(Double.isNaN(NumberUtils.min(new double[]{1.0d, Double.NaN, 2.0d})));
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
        assertEquals(1.0f, NumberUtils.min(new float[]{3.0f, 1.0f, 2.0f}), 0.0001f);
        assertTrue(Float.isNaN(NumberUtils.min(new float[]{1.0f, Float.NaN, 2.0f})));
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
        assertEquals(3L, NumberUtils.max(new long[]{3L, 1L, 2L}));
        assertEquals(5L, NumberUtils.max(new long[]{5L}));
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
        assertEquals(3, NumberUtils.max(new int[]{3, 1, 2}));
        assertEquals(5, NumberUtils.max(new int[]{5}));
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
        assertEquals((short) 3, NumberUtils.max(new short[]{3, 1, 2}));
        assertEquals((short) 5, NumberUtils.max(new short[]{5}));
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
        assertEquals((byte) 3, NumberUtils.max(new byte[]{3, 1, 2}));
        assertEquals((byte) 5, NumberUtils.max(new byte[]{5}));
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
        assertEquals(3.0d, NumberUtils.max(new double[]{3.0d, 1.0d, 2.0d}), 0.0001d);
        assertTrue(Double.isNaN(NumberUtils.max(new double[]{1.0d, Double.NaN, 2.0d})));
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
        assertEquals(3.0f, NumberUtils.max(new float[]{3.0f, 1.0f, 2.0f}), 0.0001f);
        assertTrue(Float.isNaN(NumberUtils.max(new float[]{1.0f, Float.NaN, 2.0f})));
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
    public void testMinThreeValues() throws Throwable {
        assertEquals(1L, NumberUtils.min(3L, 2L, 1L));
        assertEquals(1L, NumberUtils.min(2L, 3L, 1L));
        assertEquals(1L, NumberUtils.min(1L, 2L, 3L));

        assertEquals(1, NumberUtils.min(3, 2, 1));
        assertEquals(1, NumberUtils.min(2, 3, 1));
        assertEquals(1, NumberUtils.min(1, 2, 3));

        assertEquals((short) 1, NumberUtils.min((short)3, (short)2, (short)1));
        assertEquals((short) 1, NumberUtils.min((short)2, (short)3, (short)1));
        assertEquals((short) 1, NumberUtils.min((short)1, (short)2, (short)3));

        assertEquals((byte) 1, NumberUtils.min((byte)3, (byte)2, (byte)1));
        assertEquals((byte) 1, NumberUtils.min((byte)2, (byte)3, (byte)1));
        assertEquals((byte) 1, NumberUtils.min((byte)1, (byte)2, (byte)3));

        assertEquals(1.0d, NumberUtils.min(3.0d, 2.0d, 1.0d), 0.0001d);
        assertEquals(1.0f, NumberUtils.min(3.0f, 2.0f, 1.0f), 0.0001f);
    }

    @Test
    public void testMaxThreeValues() throws Throwable {
        assertEquals(3L, NumberUtils.max(1L, 2L, 3L));
        assertEquals(3L, NumberUtils.max(2L, 1L, 3L));
        assertEquals(3L, NumberUtils.max(3L, 2L, 1L));

        assertEquals(3, NumberUtils.max(1, 2, 3));
        assertEquals(3, NumberUtils.max(2, 1, 3));
        assertEquals(3, NumberUtils.max(3, 2, 1));

        assertEquals((short) 3, NumberUtils.max((short)1, (short)2, (short)3));
        assertEquals((short) 3, NumberUtils.max((short)2, (short)1, (short)3));
        assertEquals((short) 3, NumberUtils.max((short)3, (short)2, (short)1));

        assertEquals((byte) 3, NumberUtils.max((byte)1, (byte)2, (byte)3));
        assertEquals((byte) 3, NumberUtils.max((byte)2, (byte)1, (byte)3));
        assertEquals((byte) 3, NumberUtils.max((byte)3, (byte)2, (byte)1));

        assertEquals(3.0d, NumberUtils.max(1.0d, 2.0d, 3.0d), 0.0001d);
        assertEquals(3.0f, NumberUtils.max(1.0f, 2.0f, 3.0f), 0.0001f);
    }

    @Test
    public void testIsDigits() throws Throwable {
        assertFalse(NumberUtils.isDigits(null));
        assertFalse(NumberUtils.isDigits(""));
        assertTrue(NumberUtils.isDigits("12345"));
        assertFalse(NumberUtils.isDigits("1234a"));
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
        assertTrue(NumberUtils.isNumber("1e+10"));
        assertTrue(NumberUtils.isNumber("1e-10"));
        assertTrue(NumberUtils.isNumber("0xa"));
        assertTrue(NumberUtils.isNumber("0XA"));
        assertFalse(NumberUtils.isNumber("0x"));
        assertFalse(NumberUtils.isNumber("0xg"));
        assertFalse(NumberUtils.isNumber("123..45"));
        assertFalse(NumberUtils.isNumber("1e1e1"));
        assertFalse(NumberUtils.isNumber("e10"));
        assertFalse(NumberUtils.isNumber("1+1"));
        assertFalse(NumberUtils.isNumber("abc"));
        assertFalse(NumberUtils.isNumber("123z"));
        assertTrue(NumberUtils.isNumber("123f"));
        assertTrue(NumberUtils.isNumber("123F"));
        assertTrue(NumberUtils.isNumber("123d"));
        assertTrue(NumberUtils.isNumber("123D"));
        assertTrue(NumberUtils.isNumber("123l"));
        assertTrue(NumberUtils.isNumber("123L"));
        assertFalse(NumberUtils.isNumber("123.4f"));
        assertFalse(NumberUtils.isNumber("123L")); // handled in flow, test specific conditions
        assertFalse(NumberUtils.isNumber("1e1L"));
        assertFalse(NumberUtils.isNumber("."));
        assertTrue(NumberUtils.isNumber("1."));
        assertFalse(NumberUtils.isNumber("1e"));
        assertFalse(NumberUtils.isNumber("1e+"));
    }
}