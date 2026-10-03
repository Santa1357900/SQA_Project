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
        assertEquals(0, NumberUtils.toInt("not-a-number"));
    }

    @Test
    public void testToIntStringWithDefault() throws Throwable {
        assertEquals(5, NumberUtils.toInt(null, 5));
        assertEquals(5, NumberUtils.toInt("", 5));
        assertEquals(123, NumberUtils.toInt("123", 5));
        assertEquals(5, NumberUtils.toInt("not-a-number", 5));
    }

    @Test
    public void testToLongString() throws Throwable {
        assertEquals(0L, NumberUtils.toLong(null));
        assertEquals(0L, NumberUtils.toLong(""));
        assertEquals(123L, NumberUtils.toLong("123"));
        assertEquals(0L, NumberUtils.toLong("not-a-number"));
    }

    @Test
    public void testToLongStringWithDefault() throws Throwable {
        assertEquals(5L, NumberUtils.toLong(null, 5L));
        assertEquals(5L, NumberUtils.toLong("", 5L));
        assertEquals(123L, NumberUtils.toLong("123", 5L));
        assertEquals(5L, NumberUtils.toLong("not-a-number", 5L));
    }

    @Test
    public void testToFloatString() throws Throwable {
        assertEquals(0.0f, NumberUtils.toFloat(null), 0.0001f);
        assertEquals(0.0f, NumberUtils.toFloat(""), 0.0001f);
        assertEquals(1.5f, NumberUtils.toFloat("1.5"), 0.0001f);
        assertEquals(0.0f, NumberUtils.toFloat("not-a-number"), 0.0001f);
    }

    @Test
    public void testToFloatStringWithDefault() throws Throwable {
        assertEquals(1.1f, NumberUtils.toFloat(null, 1.1f), 0.0001f);
        assertEquals(1.1f, NumberUtils.toFloat("", 1.1f), 0.0001f);
        assertEquals(1.5f, NumberUtils.toFloat("1.5", 0.0f), 0.0001f);
        assertEquals(1.1f, NumberUtils.toFloat("not-a-number", 1.1f), 0.0001f);
    }

    @Test
    public void testToDoubleString() throws Throwable {
        assertEquals(0.0d, NumberUtils.toDouble(null), 0.0001d);
        assertEquals(0.0d, NumberUtils.toDouble(""), 0.0001d);
        assertEquals(1.5d, NumberUtils.toDouble("1.5"), 0.0001d);
        assertEquals(0.0d, NumberUtils.toDouble("not-a-number"), 0.0001d);
    }

    @Test
    public void testToDoubleStringWithDefault() throws Throwable {
        assertEquals(1.1d, NumberUtils.toDouble(null, 1.1d), 0.0001d);
        assertEquals(1.1d, NumberUtils.toDouble("", 1.1d), 0.0001d);
        assertEquals(1.5d, NumberUtils.toDouble("1.5", 0.0d), 0.0001d);
        assertEquals(1.1d, NumberUtils.toDouble("not-a-number", 1.1d), 0.0001d);
    }

    @Test
    public void testToByteString() throws Throwable {
        assertEquals((byte) 0, NumberUtils.toByte(null));
        assertEquals((byte) 0, NumberUtils.toByte(""));
        assertEquals((byte) 1, NumberUtils.toByte("1"));
        assertEquals((byte) 0, NumberUtils.toByte("not-a-number"));
    }

    @Test
    public void testToByteStringWithDefault() throws Throwable {
        assertEquals((byte) 5, NumberUtils.toByte(null, (byte) 5));
        assertEquals((byte) 5, NumberUtils.toByte("", (byte) 5));
        assertEquals((byte) 1, NumberUtils.toByte("1", (byte) 0));
        assertEquals((byte) 5, NumberUtils.toByte("not-a-number", (byte) 5));
    }

    @Test
    public void testToShortString() throws Throwable {
        assertEquals((short) 0, NumberUtils.toShort(null));
        assertEquals((short) 0, NumberUtils.toShort(""));
        assertEquals((short) 1, NumberUtils.toShort("1"));
        assertEquals((short) 0, NumberUtils.toShort("not-a-number"));
    }

    @Test
    public void testToShortStringWithDefault() throws Throwable {
        assertEquals((short) 5, NumberUtils.toShort(null, (short) 5));
        assertEquals((short) 5, NumberUtils.toShort("", (short) 5));
        assertEquals((short) 1, NumberUtils.toShort("1", (short) 0));
        assertEquals((short) 5, NumberUtils.toShort("not-a-number", (short) 5));
    }

    @Test
    public void testCreateNumber() throws Throwable {
        assertNull(NumberUtils.createNumber(null));
        
        boolean caughtBlank = false;
        try {
            NumberUtils.createNumber("   ");
        } catch (NumberFormatException e) {
            caughtBlank = true;
        }
        assertTrue(caughtBlank);

        assertNull(NumberUtils.createNumber("--123"));
        
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("0xA"));
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("-0xA"));

        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123L"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123l"));
        
        boolean caughtInvalidLong = false;
        try {
            NumberUtils.createNumber("123.45L");
        } catch (NumberFormatException e) {
            caughtInvalidLong = true;
        }
        assertTrue(caughtInvalidLong);

        assertEquals(Float.valueOf(1.5f), NumberUtils.createNumber("1.5f"));
        assertEquals(Float.valueOf(1.5f), NumberUtils.createNumber("1.5F"));

        assertEquals(Double.valueOf(1.5d), NumberUtils.createNumber("1.5d"));
        assertEquals(Double.valueOf(1.5d), NumberUtils.createNumber("1.5D"));

        assertEquals(Integer.valueOf(123), NumberUtils.createNumber("123"));
        assertEquals(Long.valueOf(2147483648L), NumberUtils.createNumber("2147483648"));
        
        assertTrue(NumberUtils.createNumber("1.5") instanceof Double);
        assertTrue(NumberUtils.createNumber("1e10") instanceof Double);
        assertTrue(NumberUtils.createNumber("999999999999999999999") instanceof BigInteger);
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
        assertEquals(1L, NumberUtils.min(new long[]{3L, 1L, 5L}));
        assertEquals(1L, NumberUtils.min(new long[]{1L}));
        
        boolean caughtNull = false;
        try {
            NumberUtils.min((long[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.min(new long[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMinIntArray() throws Throwable {
        assertEquals(1, NumberUtils.min(new int[]{3, 1, 5}));
        assertEquals(1, NumberUtils.min(new int[]{1}));
        
        boolean caughtNull = false;
        try {
            NumberUtils.min((int[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.min(new int[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMinShortArray() throws Throwable {
        assertEquals((short) 1, NumberUtils.min(new short[]{3, 1, 5}));
        assertEquals((short) 1, NumberUtils.min(new short[]{1}));
        
        boolean caughtNull = false;
        try {
            NumberUtils.min((short[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.min(new short[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMinByteArray() throws Throwable {
        assertEquals((byte) 1, NumberUtils.min(new byte[]{3, 1, 5}));
        assertEquals((byte) 1, NumberUtils.min(new byte[]{1}));
        
        boolean caughtNull = false;
        try {
            NumberUtils.min((byte[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.min(new byte[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMinDoubleArray() throws Throwable {
        assertEquals(1.0d, NumberUtils.min(new double[]{3.0d, 1.0d, 5.0d}), 0.0001d);
        assertTrue(Double.isNaN(NumberUtils.min(new double[]{3.0d, Double.NaN, 1.0d})));
        
        boolean caughtNull = false;
        try {
            NumberUtils.min((double[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.min(new double[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMinFloatArray() throws Throwable {
        assertEquals(1.0f, NumberUtils.min(new float[]{3.0f, 1.0f, 5.0f}), 0.0001f);
        assertTrue(Float.isNaN(NumberUtils.min(new float[]{3.0f, Float.NaN, 1.0f})));
        
        boolean caughtNull = false;
        try {
            NumberUtils.min((float[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.min(new float[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMaxLongArray() throws Throwable {
        assertEquals(5L, NumberUtils.max(new long[]{3L, 1L, 5L}));
        assertEquals(1L, NumberUtils.max(new long[]{1L}));
        
        boolean caughtNull = false;
        try {
            NumberUtils.max((long[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.max(new long[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMaxIntArray() throws Throwable {
        assertEquals(5, NumberUtils.max(new int[]{3, 1, 5}));
        assertEquals(1, NumberUtils.max(new int[]{1}));
        
        boolean caughtNull = false;
        try {
            NumberUtils.max((int[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.max(new int[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMaxShortArray() throws Throwable {
        assertEquals((short) 5, NumberUtils.max(new short[]{3, 1, 5}));
        assertEquals((short) 1, NumberUtils.max(new short[]{1}));
        
        boolean caughtNull = false;
        try {
            NumberUtils.max((short[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.max(new short[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMaxByteArray() throws Throwable {
        assertEquals((byte) 5, NumberUtils.max(new byte[]{3, 1, 5}));
        assertEquals((byte) 1, NumberUtils.max(new byte[]{1}));
        
        boolean caughtNull = false;
        try {
            NumberUtils.max((byte[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.max(new byte[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMaxDoubleArray() throws Throwable {
        assertEquals(5.0d, NumberUtils.max(new double[]{3.0d, 1.0d, 5.0d}), 0.0001d);
        assertTrue(Double.isNaN(NumberUtils.max(new double[]{3.0d, Double.NaN, 5.0d})));
        
        boolean caughtNull = false;
        try {
            NumberUtils.max((double[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.max(new double[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMaxFloatArray() throws Throwable {
        assertEquals(5.0f, NumberUtils.max(new float[]{3.0f, 1.0f, 5.0f}), 0.0001f);
        assertTrue(Float.isNaN(NumberUtils.max(new float[]{3.0f, Float.NaN, 5.0f})));
        
        boolean caughtNull = false;
        try {
            NumberUtils.max((float[]) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull);

        boolean caughtEmpty = false;
        try {
            NumberUtils.max(new float[0]);
        } catch (IllegalArgumentException e) {
            caughtEmpty = true;
        }
        assertTrue(caughtEmpty);
    }

    @Test
    public void testMin3Params() throws Throwable {
        assertEquals(1L, NumberUtils.min(3L, 1L, 2L));
        assertEquals(1L, NumberUtils.min(2L, 3L, 1L));
        assertEquals(1L, NumberUtils.min(1L, 2L, 3L));

        assertEquals(1, NumberUtils.min(3, 1, 2));
        assertEquals(1, NumberUtils.min(2, 3, 1));
        assertEquals(1, NumberUtils.min(1, 2, 3));

        assertEquals((short) 1, NumberUtils.min((short) 3, (short) 1, (short) 2));
        assertEquals((short) 1, NumberUtils.min((short) 2, (short) 3, (short) 1));
        assertEquals((short) 1, NumberUtils.min((short) 1, (short) 2, (short) 3));

        assertEquals((byte) 1, NumberUtils.min((byte) 3, (byte) 1, (byte) 2));
        assertEquals((byte) 1, NumberUtils.min((byte) 2, (byte) 3, (byte) 1));
        assertEquals((byte) 1, NumberUtils.min((byte) 1, (byte) 2, (byte) 3));

        assertEquals(1.0d, NumberUtils.min(3.0d, 1.0d, 2.0d), 0.0001d);
        assertEquals(1.0f, NumberUtils.min(3.0f, 1.0f, 2.0f), 0.0001f);
    }

    @Test
    public void testMax3Params() throws Throwable {
        assertEquals(3L, NumberUtils.max(1L, 3L, 2L));
        assertEquals(3L, NumberUtils.max(2L, 1L, 3L));
        assertEquals(3L, NumberUtils.max(3L, 2L, 1L));

        assertEquals(3, NumberUtils.max(1, 3, 2));
        assertEquals(3, NumberUtils.max(2, 1, 3));
        assertEquals(3, NumberUtils.max(3, 2, 1));

        assertEquals((short) 3, NumberUtils.max((short) 1, (short) 3, (short) 2));
        assertEquals((short) 3, NumberUtils.max((short) 2, (short) 1, (short) 3));
        assertEquals((short) 3, NumberUtils.max((short) 3, (short) 2, (short) 1));

        assertEquals((byte) 3, NumberUtils.max((byte) 1, (byte) 3, (byte) 2));
        assertEquals((byte) 3, NumberUtils.max((byte) 2, (byte) 1, (byte) 3));
        assertEquals((byte) 3, NumberUtils.max((byte) 3, (byte) 2, (byte) 1));

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
        
        assertTrue(NumberUtils.isNumber("123"));
        assertTrue(NumberUtils.isNumber("-123"));
        assertTrue(NumberUtils.isNumber("123.45"));
        assertTrue(NumberUtils.isNumber("1e10"));
        assertTrue(NumberUtils.isNumber("1E10"));
        assertTrue(NumberUtils.isNumber("1e+10"));
        assertTrue(NumberUtils.isNumber("1e-10"));
        assertTrue(NumberUtils.isNumber("0xA"));
        assertTrue(NumberUtils.isNumber("-0xA"));
        assertTrue(NumberUtils.isNumber("123L"));
        assertTrue(NumberUtils.isNumber("123f"));
        assertTrue(NumberUtils.isNumber("123d"));

        assertFalse(NumberUtils.isNumber("0x"));
        assertFalse(NumberUtils.isNumber("0xGG"));
        assertFalse(NumberUtils.isNumber("123..45"));
        assertFalse(NumberUtils.isNumber("1e10e10"));
        assertFalse(NumberUtils.isNumber("1e"));
        assertFalse(NumberUtils.isNumber("e10"));
        assertFalse(NumberUtils.isNumber("1+1"));
        assertFalse(NumberUtils.isNumber("123.45.67"));
        assertFalse(NumberUtils.isNumber("123L5"));
        assertFalse(NumberUtils.isNumber("abc"));
        assertFalse(NumberUtils.isNumber("123E"));
        assertFalse(NumberUtils.isNumber("123."));
        assertFalse(NumberUtils.isNumber("123e+"));
    }
}