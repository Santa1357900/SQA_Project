package org.apache.commons.lang3.math;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberUtilsTest {

    @Test
    public void test_NumberUtilsTest_constructor_behavior_attempt_1() throws Throwable {
        NumberUtils utils = new NumberUtils();
        assertNotNull(utils);
    }

    @Test
    public void test_NumberUtilsTest_toInt_conversions_attempt_2() throws Throwable {
        assertEquals(0, NumberUtils.toInt((String) null));
        assertEquals(0, NumberUtils.toInt(""));
        assertEquals(123, NumberUtils.toInt("123"));
        assertEquals(42, NumberUtils.toInt("invalid", 42));
        assertEquals(0, NumberUtils.toInt(null, 5));
        assertEquals(5, NumberUtils.toInt("", 5));
    }

    @Test
    public void test_NumberUtilsTest_toLong_conversions_attempt_3() throws Throwable {
        assertEquals(0L, NumberUtils.toLong((String) null));
        assertEquals(0L, NumberUtils.toLong(""));
        assertEquals(123L, NumberUtils.toLong("123"));
        assertEquals(42L, NumberUtils.toLong("invalid", 42L));
        assertEquals(0L, NumberUtils.toLong(null, 5L));
        assertEquals(5L, NumberUtils.toLong("", 5L));
    }

    @Test
    public void test_NumberUtilsTest_toFloat_conversions_attempt_4() throws Throwable {
        assertEquals(0.0f, NumberUtils.toFloat((String) null), 0.0001f);
        assertEquals(0.0f, NumberUtils.toFloat(""), 0.0001f);
        assertEquals(1.5f, NumberUtils.toFloat("1.5"), 0.0001f);
        assertEquals(4.2f, NumberUtils.toFloat("invalid", 4.2f), 0.0001f);
        assertEquals(1.1f, NumberUtils.toFloat(null, 1.1f), 0.0001f);
        assertEquals(1.1f, NumberUtils.toFloat("", 1.1f), 0.0001f);
    }

    @Test
    public void test_NumberUtilsTest_toDouble_conversions_attempt_5() throws Throwable {
        assertEquals(0.0d, NumberUtils.toDouble((String) null), 0.0001d);
        assertEquals(0.0d, NumberUtils.toDouble(""), 0.0001d);
        assertEquals(1.5d, NumberUtils.toDouble("1.5"), 0.0001d);
        assertEquals(4.2d, NumberUtils.toDouble("invalid", 4.2d), 0.0001d);
        assertEquals(1.1d, NumberUtils.toDouble(null, 1.1d), 0.0001d);
        assertEquals(1.1d, NumberUtils.toDouble("", 1.1d), 0.0001d);
    }

    @Test
    public void test_NumberUtilsTest_toByte_conversions_attempt_6() throws Throwable {
        assertEquals((byte) 0, NumberUtils.toByte((String) null));
        assertEquals((byte) 0, NumberUtils.toByte(""));
        assertEquals((byte) 5, NumberUtils.toByte("5"));
        assertEquals((byte) 7, NumberUtils.toByte("invalid", (byte) 7));
        assertEquals((byte) 3, NumberUtils.toByte(null, (byte) 3));
        assertEquals((byte) 3, NumberUtils.toByte("", (byte) 3));
    }

    @Test
    public void test_NumberUtilsTest_toShort_conversions_attempt_7() throws Throwable {
        assertEquals((short) 0, NumberUtils.toShort((String) null));
        assertEquals((short) 0, NumberUtils.toShort(""));
        assertEquals((short) 5, NumberUtils.toShort("5"));
        assertEquals((short) 7, NumberUtils.toShort("invalid", (short) 7));
        assertEquals((short) 3, NumberUtils.toShort(null, (short) 3));
        assertEquals((short) 3, NumberUtils.toShort("", (short) 3));
    }

    @Test
    public void test_NumberUtilsTest_createNumber_valid_cases_attempt_8() throws Throwable {
        assertNull(NumberUtils.createNumber(null));
        assertNull(NumberUtils.createNumber("--123"));
        
        assertEquals(Integer.valueOf(123), NumberUtils.createNumber("123"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123L"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123l"));
        assertEquals(Float.valueOf(1.5f), NumberUtils.createNumber("1.5f"));
        assertEquals(Float.valueOf(1.5f), NumberUtils.createNumber("1.5F"));
        assertEquals(Double.valueOf(1.5d), NumberUtils.createNumber("1.5d"));
        assertEquals(Double.valueOf(1.5d), NumberUtils.createNumber("1.5D"));
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("0xA"));
        assertEquals(Integer.valueOf(-10), NumberUtils.createNumber("-0xA"));
        
        assertTrue(NumberUtils.createNumber("1.5") instanceof Double);
        assertTrue(NumberUtils.createNumber("123456789012345") instanceof Long);
        assertTrue(NumberUtils.createNumber("123456789012345678901234567890") instanceof BigInteger);
        assertTrue(NumberUtils.createNumber("1.23456789012345678901234567890") instanceof BigDecimal);
    }

    @Test(expected = NumberFormatException.class)
    public void test_NumberUtilsTest_createNumber_blankString_attempt_9() throws Throwable {
        NumberUtils.createNumber("   ");
    }

    @Test(expected = NumberFormatException.class)
    public void test_NumberUtilsTest_createNumber_invalidExpPos_attempt_10() throws Throwable {
        NumberUtils.createNumber("1.5e+2.1");
    }

    @Test(expected = NumberFormatException.class)
    public void test_NumberUtilsTest_createNumber_invalidLongSuffix_attempt_11() throws Throwable {
        NumberUtils.createNumber("1.5L");
    }

    @Test(expected = NumberFormatException.class)
    public void test_NumberUtilsTest_createNumber_invalidDefaultSuffix_attempt_12() throws Throwable {
        NumberUtils.createNumber("1.5X");
    }

    @Test
    public void test_NumberUtilsTest_createNumber_floatDoubleFallback_attempt_13() throws Throwable {
        // Float parse failing or zero fallback
        Number num = NumberUtils.createNumber("0.0f");
        assertEquals(Float.valueOf(0.0f), num);
        
        Number num2 = NumberUtils.createNumber("0.0d");
        assertEquals(Double.valueOf(0.0d), num2);
    }

    @Test
    public void test_NumberUtilsTest_createSpecificTypes_attempt_14() throws Throwable {
        assertNull(NumberUtils.createFloat(null));
        assertEquals(Float.valueOf(2.5f), NumberUtils.createFloat("2.5"));

        assertNull(NumberUtils.createDouble(null));
        assertEquals(Double.valueOf(2.5d), NumberUtils.createDouble("2.5"));

        assertNull(NumberUtils.createInteger(null));
        assertEquals(Integer.valueOf(10), NumberUtils.createInteger("10"));

        assertNull(NumberUtils.createLong(null));
        assertEquals(Long.valueOf(10L), NumberUtils.createLong("10"));

        assertNull(NumberUtils.createBigInteger(null));
        assertEquals(new BigInteger("10"), NumberUtils.createBigInteger("10"));

        assertNull(NumberUtils.createBigDecimal(null));
        assertEquals(new BigDecimal("10.5"), NumberUtils.createBigDecimal("10.5"));
    }

    @Test(expected = NumberFormatException.class)
    public void test_NumberUtilsTest_createBigDecimal_blank_attempt_15() throws Throwable {
        NumberUtils.createBigDecimal("");
    }

    @Test
    public void test_NumberUtilsTest_min_arrays_attempt_16() throws Throwable {
        assertEquals(1L, NumberUtils.min(new long[]{3L, 1L, 2L}));
        assertEquals(1, NumberUtils.min(new int[]{3, 1, 2}));
        assertEquals((short) 1, NumberUtils.min(new short[]{3, 1, 2}));
        assertEquals((byte) 1, NumberUtils.min(new byte[]{3, 1, 2}));
        assertEquals(1.0d, NumberUtils.min(new double[]{3.0d, 1.0d, 2.0d}), 0.0001d);
        assertEquals(1.0f, NumberUtils.min(new float[]{3.0f, 1.0f, 2.0f}), 0.0001f);

        assertTrue(Double.isNaN(NumberUtils.min(new double[]{1.0d, Double.NaN, 2.0d})));
        assertTrue(Float.isNaN(NumberUtils.min(new float[]{1.0f, Float.NaN, 2.0f})));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_longArray_null_attempt_17() throws Throwable {
        NumberUtils.min((long[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_longArray_empty_attempt_18() throws Throwable {
        NumberUtils.min(new long[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_intArray_null_attempt_19() throws Throwable {
        NumberUtils.min((int[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_intArray_empty_attempt_20() throws Throwable {
        NumberUtils.min(new int[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_shortArray_null_attempt_21() throws Throwable {
        NumberUtils.min((short[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_shortArray_empty_attempt_22() throws Throwable {
        NumberUtils.min(new short[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_byteArray_null_attempt_23() throws Throwable {
        NumberUtils.min((byte[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_byteArray_empty_attempt_24() throws Throwable {
        NumberUtils.min(new byte[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_doubleArray_null_attempt_25() throws Throwable {
        NumberUtils.min((double[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_doubleArray_empty_attempt_26() throws Throwable {
        NumberUtils.min(new double[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_floatArray_null_attempt_27() throws Throwable {
        NumberUtils.min((float[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_min_floatArray_empty_attempt_28() throws Throwable {
        NumberUtils.min(new float[0]);
    }

    @Test
    public void test_NumberUtilsTest_max_arrays_attempt_29() throws Throwable {
        assertEquals(3L, NumberUtils.max(new long[]{1L, 3L, 2L}));
        assertEquals(3, NumberUtils.max(new int[]{1, 3, 2}));
        assertEquals((short) 3, NumberUtils.max(new short[]{1, 3, 2}));
        assertEquals((byte) 3, NumberUtils.max(new byte[]{1, 3, 2}));
        assertEquals(3.0d, NumberUtils.max(new double[]{1.0d, 3.0d, 2.0d}), 0.0001d);
        assertEquals(3.0f, NumberUtils.max(new float[]{1.0f, 3.0f, 2.0f}), 0.0001f);

        assertTrue(Double.isNaN(NumberUtils.max(new double[]{1.0d, Double.NaN, 2.0d})));
        assertTrue(Float.isNaN(NumberUtils.max(new float[]{1.0f, Float.NaN, 2.0f})));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_longArray_null_attempt_30() throws Throwable {
        NumberUtils.max((long[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_longArray_empty_attempt_31() throws Throwable {
        NumberUtils.max(new long[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_intArray_null_attempt_32() throws Throwable {
        NumberUtils.max((int[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_intArray_empty_attempt_33() throws Throwable {
        NumberUtils.max(new int[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_shortArray_null_attempt_34() throws Throwable {
        NumberUtils.max((short[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_shortArray_empty_attempt_35() throws Throwable {
        NumberUtils.max(new short[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_byteArray_null_attempt_36() throws Throwable {
        NumberUtils.max((byte[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_byteArray_empty_attempt_37() throws Throwable {
        NumberUtils.max(new byte[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_doubleArray_null_attempt_38() throws Throwable {
        NumberUtils.max((double[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_doubleArray_empty_attempt_39() throws Throwable {
        NumberUtils.max(new double[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_floatArray_null_attempt_40() throws Throwable {
        NumberUtils.max((float[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NumberUtilsTest_max_floatArray_empty_attempt_41() throws Throwable {
        NumberUtils.max(new float[0]);
    }

    @Test
    public void test_NumberUtilsTest_min_three_params_attempt_42() throws Throwable {
        assertEquals(1L, NumberUtils.min(3L, 1L, 2L));
        assertEquals(1L, NumberUtils.min(2L, 3L, 1L));
        assertEquals(1, NumberUtils.min(3, 1, 2));
        assertEquals((short) 1, NumberUtils.min((short) 3, (short) 1, (short) 2));
        assertEquals((byte) 1, NumberUtils.min((byte) 3, (byte) 1, (byte) 2));
        assertEquals(1.0d, NumberUtils.min(3.0d, 1.0d, 2.0d), 0.0001d);
        assertEquals(1.0f, NumberUtils.min(3.0f, 1.0f, 2.0f), 0.0001f);
    }

    @Test
    public void test_NumberUtilsTest_max_three_params_attempt_43() throws Throwable {
        assertEquals(3L, NumberUtils.max(1L, 3L, 2L));
        assertEquals(3L, NumberUtils.max(2L, 1L, 3L));
        assertEquals(3, NumberUtils.max(1, 3, 2));
        assertEquals((short) 3, NumberUtils.max((short) 1, (short) 3, (short) 2));
        assertEquals((byte) 3, NumberUtils.max((byte) 1, (byte) 3, (byte) 2));
        assertEquals(3.0d, NumberUtils.max(1.0d, 3.0d, 2.0d), 0.0001d);
        assertEquals(3.0f, NumberUtils.max(1.0f, 3.0f, 2.0f), 0.0001f);
    }

    @Test
    public void test_NumberUtilsTest_isDigits_attempt_44() throws Throwable {
        assertFalse(NumberUtils.isDigits(null));
        assertFalse(NumberUtils.isDigits(""));
        assertTrue(NumberUtils.isDigits("12345"));
        assertFalse(NumberUtils.isDigits("123a5"));
        assertFalse(NumberUtils.isDigits("123.45"));
    }

    @Test
    public void test_NumberUtilsTest_isNumber_attempt_45() throws Throwable {
        assertFalse(NumberUtils.isNumber(null));
        assertFalse(NumberUtils.isNumber(""));
        assertTrue(NumberUtils.isNumber("12345"));
        assertTrue(NumberUtils.isNumber("-12345"));
        assertTrue(NumberUtils.isNumber("123.45"));
        assertTrue(NumberUtils.isNumber("1e10"));
        assertTrue(NumberUtils.isNumber("1E10"));
        assertTrue(NumberUtils.isNumber("1e+10"));
        assertTrue(NumberUtils.isNumber("1e-10"));
        assertTrue(NumberUtils.isNumber("0x123A"));
        assertTrue(NumberUtils.isNumber("-0x123A"));
        assertFalse(NumberUtils.isNumber("0x"));
        assertFalse(NumberUtils.isNumber("0xG"));
        assertFalse(NumberUtils.isNumber("1.2.3"));
        assertFalse(NumberUtils.isNumber("1e1e1"));
        assertFalse(NumberUtils.isNumber("e10"));
        assertFalse(NumberUtils.isNumber("1+1"));
        assertFalse(NumberUtils.isNumber("1e"));
        assertFalse(NumberUtils.isNumber("199l"));
        assertTrue(NumberUtils.isNumber("199L"));
        assertTrue(NumberUtils.isNumber("199f"));
        assertTrue(NumberUtils.isNumber("199F"));
        assertTrue(NumberUtils.isNumber("199d"));
        assertTrue(NumberUtils.isNumber("199D"));
        assertFalse(NumberUtils.isNumber("199lE2"));
        assertFalse(NumberUtils.isNumber("abc"));
        assertFalse(NumberUtils.isNumber("."));
    }
}