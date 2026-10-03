package org.apache.commons.lang.math;

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
    public void testStringToInt() throws Throwable {
        assertEquals(0, NumberUtils.stringToInt(null));
        assertEquals(0, NumberUtils.stringToInt(""));
        assertEquals(1, NumberUtils.stringToInt("1"));
        assertEquals(0, NumberUtils.stringToInt("invalid"));

        assertEquals(1, NumberUtils.stringToInt(null, 1));
        assertEquals(1, NumberUtils.stringToInt("", 1));
        assertEquals(1, NumberUtils.stringToInt("1", 0));
        assertEquals(5, NumberUtils.stringToInt("invalid", 5));
    }

    @Test
    public void testToInt() throws Throwable {
        assertEquals(0, NumberUtils.toInt(null));
        assertEquals(0, NumberUtils.toInt(""));
        assertEquals(123, NumberUtils.toInt("123"));
        assertEquals(0, NumberUtils.toInt("abc"));

        assertEquals(10, NumberUtils.toInt(null, 10));
        assertEquals(10, NumberUtils.toInt("", 10));
        assertEquals(456, NumberUtils.toInt("456", 10));
        assertEquals(10, NumberUtils.toInt("abc", 10));
    }

    @Test
    public void testToLong() throws Throwable {
        assertEquals(0L, NumberUtils.toLong((String) null));
        assertEquals(0L, NumberUtils.toLong(""));
        assertEquals(123L, NumberUtils.toLong("123"));
        assertEquals(0L, NumberUtils.toLong("abc"));

        assertEquals(10L, NumberUtils.toLong(null, 10L));
        assertEquals(10L, NumberUtils.toLong("", 10L));
        assertEquals(456L, NumberUtils.toLong("456", 10L));
        assertEquals(10L, NumberUtils.toLong("abc", 10L));
    }

    @Test
    public void testToFloat() throws Throwable {
        assertEquals(0.0f, NumberUtils.toFloat((String) null), 0.001f);
        assertEquals(0.0f, NumberUtils.toFloat(""), 0.001f);
        assertEquals(1.5f, NumberUtils.toFloat("1.5"), 0.001f);
        assertEquals(0.0f, NumberUtils.toFloat("abc"), 0.001f);

        assertEquals(1.1f, NumberUtils.toFloat(null, 1.1f), 0.001f);
        assertEquals(1.1f, NumberUtils.toFloat("", 1.1f), 0.001f);
        assertEquals(1.5f, NumberUtils.toFloat("1.5", 0.0f), 0.001f);
        assertEquals(1.1f, NumberUtils.toFloat("abc", 1.1f), 0.001f);
    }

    @Test
    public void testToDouble() throws Throwable {
        assertEquals(0.0d, NumberUtils.toDouble((String) null), 0.001d);
        assertEquals(0.0d, NumberUtils.toDouble(""), 0.001d);
        assertEquals(1.5d, NumberUtils.toDouble("1.5"), 0.001d);
        assertEquals(0.0d, NumberUtils.toDouble("abc"), 0.001d);

        assertEquals(1.1d, NumberUtils.toDouble(null, 1.1d), 0.001d);
        assertEquals(1.1d, NumberUtils.toDouble("", 1.1d), 0.001d);
        assertEquals(1.5d, NumberUtils.toDouble("1.5", 0.0d), 0.001d);
        assertEquals(1.1d, NumberUtils.toDouble("abc", 1.1d), 0.001d);
    }

    @Test
    public void testCreateNumber() throws Throwable {
        assertNull(NumberUtils.createNumber(null));
        
        try {
            NumberUtils.createNumber("");
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

        assertNull(NumberUtils.createNumber("--123"));
        
        assertEquals(Integer.valueOf(123), NumberUtils.createNumber("123"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123L"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123l"));
        
        // Test hex
        assertEquals(Integer.valueOf(255), NumberUtils.createNumber("0xff"));
        assertEquals(Integer.valueOf(255), NumberUtils.createNumber("-0xff"));

        // Test Float / Double / BigDecimal suffixes
        assertTrue(NumberUtils.createNumber("123.45f") instanceof Float);
        assertTrue(NumberUtils.createNumber("123.45F") instanceof Float);
        assertTrue(NumberUtils.createNumber("123.45d") instanceof Double);
        assertTrue(NumberUtils.createNumber("123.45D") instanceof Double);
        assertTrue(NumberUtils.createNumber("123.45") instanceof Double);

        // Scientific notation and decimals
        assertTrue(NumberUtils.createNumber("1e3") instanceof Float || NumberUtils.createNumber("1e3") instanceof Double);
        assertTrue(NumberUtils.createNumber("1.23e10") instanceof Double);

        // BigInteger and BigDecimal creation paths
        assertTrue(NumberUtils.createNumber("12345678901234567890") instanceof BigInteger);
        assertTrue(NumberUtils.createNumber("12345678901234567890.1234567890") instanceof BigDecimal);

        // Edge cases with exponents and decimals in createNumber
        assertEquals(Float.valueOf("1.2e1f"), NumberUtils.createNumber("1.2e1f"));
        assertEquals(Double.valueOf("1.2e1d"), NumberUtils.createNumber("1.2e1d"));
        
        // Invalid numbers for createNumber
        try {
            NumberUtils.createNumber("123.45.6");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }

        try {
            NumberUtils.createNumber("1e2e3");
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
            NumberUtils.createNumber("abc");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }
        
        try {
            NumberUtils.createNumber("123L.45");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }
        
        try {
            NumberUtils.createNumber("1.2e3L");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }
    }

    @Test
    public void testCreateConverters() throws Throwable {
        assertNull(NumberUtils.createFloat(null));
        assertEquals(Float.valueOf(1.5f), NumberUtils.createFloat("1.5"));

        assertNull(NumberUtils.createDouble(null));
        assertEquals(Double.valueOf(1.5d), NumberUtils.createDouble("1.5"));

        assertNull(NumberUtils.createInteger(null));
        assertEquals(Integer.valueOf(10), NumberUtils.createInteger("10"));
        assertEquals(Integer.valueOf(10), NumberUtils.createInteger("0xA"));

        assertNull(NumberUtils.createLong(null));
        assertEquals(Long.valueOf(10L), NumberUtils.createLong("10"));

        assertNull(NumberUtils.createBigInteger(null));
        assertEquals(new BigInteger("10"), NumberUtils.createBigInteger("10"));

        assertNull(NumberUtils.createBigDecimal(null));
        assertEquals(new BigDecimal("10.5"), NumberUtils.createBigDecimal("10.5"));
        
        try {
            NumberUtils.createBigDecimal("");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // expected
        }
    }

    @Test
    public void testEqualsArrays() throws Throwable {
        byte[] b1 = new byte[]{1, 2};
        byte[] b2 = new byte[]{1, 2};
        byte[] b3 = new byte[]{1, 3};
        byte[] b4 = new byte[]{1};
        assertTrue(NumberUtils.equals(b1, b1));
        assertTrue(NumberUtils.equals(b1, b2));
        assertFalse(NumberUtils.equals(b1, b3));
        assertFalse(NumberUtils.equals(b1, b4));
        assertFalse(NumberUtils.equals(b1, null));
        assertFalse(NumberUtils.equals(null, b1));
        assertTrue(NumberUtils.equals((byte[]) null, (byte[]) null));

        short[] s1 = new short[]{1, 2};
        short[] s2 = new short[]{1, 2};
        short[] s3 = new short[]{1, 3};
        short[] s4 = new short[]{1};
        assertTrue(NumberUtils.equals(s1, s1));
        assertTrue(NumberUtils.equals(s1, s2));
        assertFalse(NumberUtils.equals(s1, s3));
        assertFalse(NumberUtils.equals(s1, s4));
        assertFalse(NumberUtils.equals(s1, null));
        assertFalse(NumberUtils.equals(null, s1));
        assertTrue(NumberUtils.equals((short[]) null, (short[]) null));

        int[] i1 = new int[]{1, 2};
        int[] i2 = new int[]{1, 2};
        int[] i3 = new int[]{1, 3};
        int[] i4 = new int[]{1};
        assertTrue(NumberUtils.equals(i1, i1));
        assertTrue(NumberUtils.equals(i1, i2));
        assertFalse(NumberUtils.equals(i1, i3));
        assertFalse(NumberUtils.equals(i1, i4));
        assertFalse(NumberUtils.equals(i1, null));
        assertFalse(NumberUtils.equals(null, i1));
        assertTrue(NumberUtils.equals((int[]) null, (int[]) null));

        long[] l1 = new long[]{1L, 2L};
        long[] l2 = new long[]{1L, 2L};
        long[] l3 = new long[]{1L, 3L};
        long[] l4 = new long[]{1L};
        assertTrue(NumberUtils.equals(l1, l1));
        assertTrue(NumberUtils.equals(l1, l2));
        assertFalse(NumberUtils.equals(l1, l3));
        assertFalse(NumberUtils.equals(l1, l4));
        assertFalse(NumberUtils.equals(l1, null));
        assertFalse(NumberUtils.equals(null, l1));
        assertTrue(NumberUtils.equals((long[]) null, (long[]) null));

        float[] f1 = new float[]{1.0f, 2.0f};
        float[] f2 = new float[]{1.0f, 2.0f};
        float[] f3 = new float[]{1.0f, 3.0f};
        float[] f4 = new float[]{1.0f};
        assertTrue(NumberUtils.equals(f1, f1));
        assertTrue(NumberUtils.equals(f1, f2));
        assertFalse(NumberUtils.equals(f1, f3));
        assertFalse(NumberUtils.equals(f1, f4));
        assertFalse(NumberUtils.equals(f1, null));
        assertFalse(NumberUtils.equals(null, f1));
        assertTrue(NumberUtils.equals((float[]) null, (float[]) null));

        double[] d1 = new double[]{1.0d, 2.0d};
        double[] d2 = new double[]{1.0d, 2.0d};
        double[] d3 = new double[]{1.0d, 3.0d};
        double[] d4 = new double[]{1.0d};
        assertTrue(NumberUtils.equals(d1, d1));
        assertTrue(NumberUtils.equals(d1, d2));
        assertFalse(NumberUtils.equals(d1, d3));
        assertFalse(NumberUtils.equals(d1, d4));
        assertFalse(NumberUtils.equals(d1, null));
        assertFalse(NumberUtils.equals(null, d1));
        assertTrue(NumberUtils.equals((double[]) null, (double[]) null));
    }

    @Test
    public void testMinArray() throws Throwable {
        assertEquals(1L, NumberUtils.min(new long[]{2L, 1L, 3L}));
        assertEquals(1, NumberUtils.min(new int[]{2, 1, 3}));
        assertEquals((short)1, NumberUtils.min(new short[]{2, 1, 3}));
        assertEquals((byte)1, NumberUtils.min(new byte[]{2, 1, 3}));
        assertEquals(1.0d, NumberUtils.min(new double[]{2.0d, 1.0d, 3.0d}), 0.001d);
        assertEquals(1.0f, NumberUtils.min(new float[]{2.0f, 1.0f, 3.0f}), 0.001f);

        // Exceptions for null or empty arrays
        testMinArrayExceptions();
    }

    private void testMinArrayExceptions() throws Throwable {
        try {
            NumberUtils.min((long[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.min(new long[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try {
            NumberUtils.min((int[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.min(new int[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try {
            NumberUtils.min((short[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.min(new short[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try {
            NumberUtils.min((byte[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.min(new byte[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try {
            NumberUtils.min((double[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.min(new double[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try {
            NumberUtils.min((float[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.min(new float[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testMaxArray() throws Throwable {
        assertEquals(3L, NumberUtils.max(new long[]{2L, 1L, 3L}));
        assertEquals(3, NumberUtils.max(new int[]{2, 1, 3}));
        assertEquals((short)3, NumberUtils.max(new short[]{2, 1, 3}));
        assertEquals((byte)3, NumberUtils.max(new byte[]{2, 1, 3}));
        assertEquals(3.0d, NumberUtils.max(new double[]{2.0d, 1.0d, 3.0d}), 0.001d);
        assertEquals(3.0f, NumberUtils.max(new float[]{2.0f, 1.0f, 3.0f}), 0.001f);

        testMaxArrayExceptions();
    }

    private void testMaxArrayExceptions() throws Throwable {
        try {
            NumberUtils.max((long[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.max(new long[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try {
            NumberUtils.max((int[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.max(new int[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try {
            NumberUtils.max((short[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.max(new short[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try {
            NumberUtils.max((byte[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.max(new byte[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try {
            NumberUtils.max((double[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.max(new double[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        try {
            NumberUtils.max((float[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        try {
            NumberUtils.max(new float[0]);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testMinThreeParams() throws Throwable {
        assertEquals(1L, NumberUtils.min(2L, 1L, 3L));
        assertEquals(1L, NumberUtils.min(3L, 2L, 1L));
        assertEquals(1L, NumberUtils.min(1L, 3L, 2L));

        assertEquals(1, NumberUtils.min(2, 1, 3));
        assertEquals(1, NumberUtils.min(3, 2, 1));
        assertEquals(1, NumberUtils.min(1, 3, 2));

        assertEquals((short)1, NumberUtils.min((short)2, (short)1, (short)3));
        assertEquals((short)1, NumberUtils.min((short)3, (short)2, (short)1));
        assertEquals((short)1, NumberUtils.min((short)1, (short)3, (short)2));

        assertEquals((byte)1, NumberUtils.min((byte)2, (byte)1, (byte)3));
        assertEquals((byte)1, NumberUtils.min((byte)3, (byte)2, (byte)1));
        assertEquals((byte)1, NumberUtils.min((byte)1, (byte)3, (byte)2));

        assertEquals(1.0d, NumberUtils.min(2.0d, 1.0d, 3.0d), 0.001d);
        assertEquals(1.0f, NumberUtils.min(2.0f, 1.0f, 3.0f), 0.001f);
    }

    @Test
    public void testMaxThreeParams() throws Throwable {
        assertEquals(3L, NumberUtils.max(2L, 1L, 3L));
        assertEquals(3L, NumberUtils.max(1L, 3L, 2L));
        assertEquals(3L, NumberUtils.max(3L, 1L, 2L));

        assertEquals(3, NumberUtils.max(2, 1, 3));
        assertEquals(3, NumberUtils.max(1, 3, 2));
        assertEquals(3, NumberUtils.max(3, 1, 2));

        assertEquals((short)3, NumberUtils.max((short)2, (short)1, (short)3));
        assertEquals((short)3, NumberUtils.max((short)1, (short)3, (short)2));
        assertEquals((short)3, NumberUtils.max((short)3, (short)1, (short)2));

        assertEquals((byte)3, NumberUtils.max((byte)2, (byte)1, (byte)3));
        assertEquals((byte)3, NumberUtils.max((byte)1, (byte)3, (byte)2));
        assertEquals((byte)3, NumberUtils.max((byte)3, (byte)1, (byte)2));

        assertEquals(3.0d, NumberUtils.max(2.0d, 1.0d, 3.0d), 0.001d);
        assertEquals(3.0f, NumberUtils.max(2.0f, 1.0f, 3.0f), 0.001f);
    }

    @Test
    public void testCompareDouble() throws Throwable {
        assertEquals(-1, NumberUtils.compare(1.0d, 2.0d));
        assertEquals(1, NumberUtils.compare(2.0d, 1.0d));
        assertEquals(0, NumberUtils.compare(1.0d, 1.0d));

        // Zero vs negative zero
        assertEquals(-1, NumberUtils.compare(-0.0d, 0.0d));
        assertEquals(1, NumberUtils.compare(0.0d, -0.0d));
        assertEquals(0, NumberUtils.compare(0.0d, 0.0d));
        assertEquals(0, NumberUtils.compare(-0.0d, -0.0d));

        // NaNs
        assertEquals(0, NumberUtils.compare(Double.NaN, Double.NaN));
        assertEquals(1, NumberUtils.compare(Double.NaN, 1.0d));
        assertEquals(-1, NumberUtils.compare(1.0d, Double.NaN));
    }

    @Test
    public void testCompareFloat() throws Throwable {
        assertEquals(-1, NumberUtils.compare(1.0f, 2.0f));
        assertEquals(1, NumberUtils.compare(2.0f, 1.0f));
        assertEquals(0, NumberUtils.compare(1.0f, 1.0f));

        // Zero vs negative zero
        assertEquals(-1, NumberUtils.compare(-0.0f, 0.0f));
        assertEquals(1, NumberUtils.compare(0.0f, -0.0f));
        assertEquals(0, NumberUtils.compare(0.0f, 0.0f));
        assertEquals(0, NumberUtils.compare(-0.0f, -0.0f));

        // NaNs
        assertEquals(0, NumberUtils.compare(Float.NaN, Float.NaN));
        assertEquals(1, NumberUtils.compare(Float.NaN, 1.0f));
        assertEquals(-1, NumberUtils.compare(1.0f, Float.NaN));
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
        assertTrue(NumberUtils.isNumber("-123.45"));
        assertTrue(NumberUtils.isNumber("1e3"));
        assertTrue(NumberUtils.isNumber("1E3"));
        assertTrue(NumberUtils.isNumber("1e+3"));
        assertTrue(NumberUtils.isNumber("1e-3"));
        assertTrue(NumberUtils.isNumber("0xff"));
        assertTrue(NumberUtils.isNumber("-0xff"));
        assertTrue(NumberUtils.isNumber("123l"));
        assertTrue(NumberUtils.isNumber("123L"));
        assertTrue(NumberUtils.isNumber("123f"));
        assertTrue(NumberUtils.isNumber("123F"));
        assertTrue(NumberUtils.isNumber("123d"));
        assertTrue(NumberUtils.isNumber("123D"));

        // Invalid numbers
        assertFalse(NumberUtils.isNumber("0x"));
        assertFalse(NumberUtils.isNumber("0xg"));
        assertFalse(NumberUtils.isNumber("123..45"));
        assertFalse(NumberUtils.isNumber("1e2e3"));
        assertFalse(NumberUtils.isNumber("1e"));
        assertFalse(NumberUtils.isNumber("abc"));
        assertFalse(NumberUtils.isNumber("123e4L"));
        assertFalse(NumberUtils.isNumber("."));
        assertFalse(NumberUtils.isNumber("1e-"));
        assertFalse(NumberUtils.isNumber("+123"));
        assertFalse(NumberUtils.isNumber("123.45.6"));
        assertFalse(NumberUtils.isNumber("123_45"));
    }
}