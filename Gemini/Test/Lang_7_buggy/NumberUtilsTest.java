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
        assertEquals(5, NumberUtils.toInt("5"));
        assertEquals(0, NumberUtils.toInt("invalid"));

        assertEquals(10, NumberUtils.toInt(null, 10));
        assertEquals(10, NumberUtils.toInt("", 10));
        assertEquals(5, NumberUtils.toInt("5", 10));
        assertEquals(10, NumberUtils.toInt("invalid", 10));
    }

    @Test
    public void testToLong() throws Throwable {
        assertEquals(0L, NumberUtils.toLong((String) null));
        assertEquals(0L, NumberUtils.toLong(""));
        assertEquals(5L, NumberUtils.toLong("5"));
        assertEquals(0L, NumberUtils.toLong("invalid"));

        assertEquals(10L, NumberUtils.toLong(null, 10L));
        assertEquals(10L, NumberUtils.toLong("", 10L));
        assertEquals(5L, NumberUtils.toLong("5", 10L));
        assertEquals(10L, NumberUtils.toLong("invalid", 10L));
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
        assertEquals((byte) 5, NumberUtils.toByte("5"));
        assertEquals((byte) 0, NumberUtils.toByte("invalid"));

        assertEquals((byte) 10, NumberUtils.toByte(null, (byte) 10));
        assertEquals((byte) 10, NumberUtils.toByte("", (byte) 10));
        assertEquals((byte) 5, NumberUtils.toByte("5", (byte) 10));
        assertEquals((byte) 10, NumberUtils.toByte("invalid", (byte) 10));
    }

    @Test
    public void testToShort() throws Throwable {
        assertEquals((short) 0, NumberUtils.toShort((String) null));
        assertEquals((short) 0, NumberUtils.toShort(""));
        assertEquals((short) 5, NumberUtils.toShort("5"));
        assertEquals((short) 0, NumberUtils.toShort("invalid"));

        assertEquals((short) 10, NumberUtils.toShort(null, (short) 10));
        assertEquals((short) 10, NumberUtils.toShort("", (short) 10));
        assertEquals((short) 5, NumberUtils.toShort("5", (short) 10));
        assertEquals((short) 10, NumberUtils.toShort("invalid", (short) 10));
    }

    @Test
    public void testCreateNumber() throws Throwable {
        assertNull(NumberUtils.createNumber(null));

        try {
            NumberUtils.createNumber("   ");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(e.getMessage().contains("blank string"));
        }

        assertNull(NumberUtils.createNumber("--5"));

        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("0x0A"));
        assertEquals(Integer.valueOf(-10), NumberUtils.createNumber("-0x0A"));
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("0X0A"));
        assertEquals(Integer.valueOf(-10), NumberUtils.createNumber("-0X0A"));
        assertEquals(Long.valueOf(123456789L), NumberUtils.createNumber("0x075BCD15"));

        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123L"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123l"));
        
        try {
            NumberUtils.createNumber("123.45L");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(true);
        }

        assertEquals(Float.valueOf(1.5f), NumberUtils.createNumber("1.5f"));
        assertEquals(Float.valueOf(1.5f), NumberUtils.createNumber("1.5F"));
        
        assertEquals(Double.valueOf(1.5d), NumberUtils.createNumber("1.5d"));
        assertEquals(Double.valueOf(1.5d), NumberUtils.createNumber("1.5D"));

        assertEquals(Integer.valueOf(123), NumberUtils.createNumber("123"));
        assertEquals(Long.valueOf(1234567890123L), NumberUtils.createNumber("1234567890123"));
        assertEquals(new BigInteger("123456789012345678901234567890"), NumberUtils.createNumber("123456789012345678901234567890"));

        assertEquals(Float.valueOf(1.5f), NumberUtils.createNumber("1.5"));
        assertEquals(Double.valueOf(1.5), NumberUtils.createNumber("1.5e10"));
        
        try {
            NumberUtils.createNumber("1.5e");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(true);
        }

        try {
            NumberUtils.createNumber("1.5.6");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(true);
        }

        try {
            NumberUtils.createNumber("abc");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(true);
        }
        
        // Edge cases for createNumber with type qualifiers and zeros
        assertEquals(Float.valueOf(0.0f), NumberUtils.createNumber("0.0f"));
        assertEquals(Double.valueOf(0.0d), NumberUtils.createNumber("0.0d"));
        
        try {
            NumberUtils.createNumber("invalidL");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(true);
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
        assertEquals(Integer.valueOf(10), NumberUtils.createInteger("10"));
        assertEquals(Integer.valueOf(10), NumberUtils.createInteger("0xA"));
    }

    @Test
    public void testCreateLong() throws Throwable {
        assertNull(NumberUtils.createLong(null));
        assertEquals(Long.valueOf(10L), NumberUtils.createLong("10"));
        assertEquals(Long.valueOf(10L), NumberUtils.createLong("0xA"));
    }

    @Test
    public void testCreateBigInteger() throws Throwable {
        assertNull(NumberUtils.createBigInteger(null));
        assertEquals(new BigInteger("123"), NumberUtils.createBigInteger("123"));
    }

    @Test
    public void testCreateBigDecimal() throws Throwable {
        assertNull(NumberUtils.createBigDecimal(null));
        try {
            NumberUtils.createBigDecimal("");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(e.getMessage().contains("blank string"));
        }
        assertEquals(new BigDecimal("123.45"), NumberUtils.createBigDecimal("123.45"));
    }

    @Test
    public void testMinArray() throws Throwable {
        // long[]
        assertEquals(1L, NumberUtils.min(new long[]{5L, 2L, 1L, 9L}));
        try {
            NumberUtils.min((long[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.min(new long[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        // int[]
        assertEquals(1, NumberUtils.min(new int[]{5, 2, 1, 9}));
        try {
            NumberUtils.min((int[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.min(new int[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        // short[]
        assertEquals((short) 1, NumberUtils.min(new short[]{5, 2, 1, 9}));
        try {
            NumberUtils.min((short[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.min(new short[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        // byte[]
        assertEquals((byte) 1, NumberUtils.min(new byte[]{5, 2, 1, 9}));
        try {
            NumberUtils.min((byte[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.min(new byte[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        // double[]
        assertEquals(1.0, NumberUtils.min(new double[]{5.0, 2.0, 1.0, 9.0}), 0.0001);
        assertTrue(Double.isNaN(NumberUtils.min(new double[]{5.0, Double.NaN, 1.0})));
        try {
            NumberUtils.min((double[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.min(new double[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        // float[]
        assertEquals(1.0f, NumberUtils.min(new float[]{5.0f, 2.0f, 1.0f, 9.0f}), 0.0001f);
        assertTrue(Float.isNaN(NumberUtils.min(new float[]{5.0f, Float.NaN, 1.0f})));
        try {
            NumberUtils.min((float[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.min(new float[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testMaxArray() throws Throwable {
        // long[]
        assertEquals(9L, NumberUtils.max(new long[]{5L, 2L, 1L, 9L}));
        try {
            NumberUtils.max((long[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.max(new long[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        // int[]
        assertEquals(9, NumberUtils.max(new int[]{5, 2, 1, 9}));
        try {
            NumberUtils.max((int[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.max(new int[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        // short[]
        assertEquals((short) 9, NumberUtils.max(new short[]{5, 2, 1, 9}));
        try {
            NumberUtils.max((short[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.max(new short[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        // byte[]
        assertEquals((byte) 9, NumberUtils.max(new byte[]{5, 2, 1, 9}));
        try {
            NumberUtils.max((byte[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.max(new byte[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        // double[]
        assertEquals(9.0, NumberUtils.max(new double[]{5.0, 2.0, 1.0, 9.0}), 0.0001);
        assertTrue(Double.isNaN(NumberUtils.max(new double[]{5.0, Double.NaN, 1.0})));
        try {
            NumberUtils.max((double[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.max(new double[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        // float[]
        assertEquals(9.0f, NumberUtils.max(new float[]{5.0f, 2.0f, 1.0f, 9.0f}), 0.0001f);
        assertTrue(Float.isNaN(NumberUtils.max(new float[]{5.0f, Float.NaN, 1.0f})));
        try {
            NumberUtils.max((float[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            NumberUtils.max(new float[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testMin3Params() throws Throwable {
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

        assertEquals((short) 3, NumberUtils.max((short)1, (short)2, (short)3));
        assertEquals((short) 3, NumberUtils.max((short)2, (short)3, (short)1));
        assertEquals((short) 3, NumberUtils.max((short)3, (short)1, (short)2));

        assertEquals((byte) 3, NumberUtils.max((byte)1, (byte)2, (byte)3));
        assertEquals((byte) 3, NumberUtils.max((byte)2, (byte)3, (byte)1));
        assertEquals((byte) 3, NumberUtils.max((byte)3, (byte)1, (byte)2));

        assertEquals(3.0, NumberUtils.max(1.0, 2.0, 3.0), 0.0001);
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
        
        // Hex
        assertTrue(NumberUtils.isNumber("0x1234"));
        assertTrue(NumberUtils.isNumber("0X1234"));
        assertFalse(NumberUtils.isNumber("0x"));
        assertFalse(NumberUtils.isNumber("0x123G"));

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
        assertTrue(NumberUtils.isNumber("123."));
        assertTrue(NumberUtils.isNumber(".123"));

        // Invalid numbers
        assertFalse(NumberUtils.isNumber("123.45.67"));
        assertFalse(NumberUtils.isNumber("123e4e5"));
        assertFalse(NumberUtils.isNumber("e10"));
        assertFalse(NumberUtils.isNumber("1e+"));
        assertFalse(NumberUtils.isNumber("1e"));
        assertFalse(NumberUtils.isNumber("1+1"));
        assertFalse(NumberUtils.isNumber("abc"));
        assertFalse(NumberUtils.isNumber("123a"));
        assertFalse(NumberUtils.isNumber("123L5"));
        assertFalse(NumberUtils.isNumber("123.45L"));
        assertFalse(NumberUtils.isNumber("123e10L"));
    }
}