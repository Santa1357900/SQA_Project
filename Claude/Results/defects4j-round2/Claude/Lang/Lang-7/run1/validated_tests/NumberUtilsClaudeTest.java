package org.apache.commons.lang3.math;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;
import static org.junit.Assert.*;

public class NumberUtilsClaudeTest {

    // toInt(String): null branch -> default 0
    @Test
    public void testToInt_nullString_returnsZero() throws Throwable {
        assertEquals(0, NumberUtils.toInt(null));
    }

    // toInt(String): successful parse branch
    @Test
    public void testToInt_validString_returnsParsedValue() throws Throwable {
        assertEquals(123, NumberUtils.toInt("123"));
    }

    // toInt(String,int): catch(NumberFormatException) branch returns default
    @Test
    public void testToIntDefault_invalidString_returnsDefault() throws Throwable {
        assertEquals(5, NumberUtils.toInt("abc", 5));
    }

    // toLong(String): null branch -> default 0L
    @Test
    public void testToLong_nullString_returnsZero() throws Throwable {
        assertEquals(0L, NumberUtils.toLong(null));
    }

    // toLong(String,long): catch branch returns default
    @Test
    public void testToLongDefault_invalidString_returnsDefault() throws Throwable {
        assertEquals(7L, NumberUtils.toLong("abc", 7L));
    }

    // toFloat(String): null branch -> default 0.0f
    @Test
    public void testToFloat_nullString_returnsZero() throws Throwable {
        assertEquals(0.0f, NumberUtils.toFloat(null), 0.0001f);
    }

    // toFloat(String,float): catch branch returns default
    @Test
    public void testToFloatDefault_invalidString_returnsDefault() throws Throwable {
        assertEquals(2.5f, NumberUtils.toFloat("abc", 2.5f), 0.0001f);
    }

    // toDouble(String): null branch -> default 0.0d
    @Test
    public void testToDouble_nullString_returnsZero() throws Throwable {
        assertEquals(0.0d, NumberUtils.toDouble(null), 0.0000001d);
    }

    // toDouble(String,double): catch branch returns default
    @Test
    public void testToDoubleDefault_invalidString_returnsDefault() throws Throwable {
        assertEquals(2.5d, NumberUtils.toDouble("abc", 2.5d), 0.0000001d);
    }

    // toByte(String): null branch -> default 0
    @Test
    public void testToByte_nullString_returnsZero() throws Throwable {
        assertEquals(0, NumberUtils.toByte(null));
    }

    // toByte(String,byte): catch branch returns default
    @Test
    public void testToByteDefault_invalidString_returnsDefault() throws Throwable {
        assertEquals((byte) 5, NumberUtils.toByte("abc", (byte) 5));
    }

    // toShort(String): null branch -> default 0
    @Test
    public void testToShort_nullString_returnsZero() throws Throwable {
        assertEquals(0, NumberUtils.toShort(null));
    }

    // toShort(String): successful parse branch
    @Test
    public void testToShort_validString_returnsParsedValue() throws Throwable {
        assertEquals((short) 12, NumberUtils.toShort("12"));
    }

    // toShort(String,short): catch branch returns default
    @Test
    public void testToShortDefault_invalidString_returnsDefault() throws Throwable {
        assertEquals((short) 5, NumberUtils.toShort("abc", (short) 5));
    }

    // createNumber(String): str == null branch
    @Test
    public void testCreateNumber_null_returnsNull() throws Throwable {
        assertNull(NumberUtils.createNumber(null));
    }

    // createNumber(String): StringUtils.isBlank branch throws NumberFormatException
    @Test
    public void testCreateNumber_blankString_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createNumber("");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }



    // createNumber(String): "0x" prefix, hexDigits <= 8 -> Integer
    @Test
    public void testCreateNumber_hexLowercasePrefix_returnsInteger() throws Throwable {
        Number result = NumberUtils.createNumber("0x1A");
        assertTrue(result instanceof Integer);
        assertEquals(26, result.intValue());
    }

    // createNumber(String): "-0x" prefix branch, negative hex -> Integer
    @Test
    public void testCreateNumber_negativeHexPrefix_returnsInteger() throws Throwable {
        Number result = NumberUtils.createNumber("-0x1A");
        assertTrue(result instanceof Integer);
        assertEquals(-26, result.intValue());
    }

    // createNumber(String): hexDigits > 8 branch -> Long
    @Test
    public void testCreateNumber_hexMoreThan8Digits_returnsLong() throws Throwable {
        Number result = NumberUtils.createNumber("0x123456789");
        assertTrue(result instanceof Long);
        assertEquals(Long.parseLong("123456789", 16), result.longValue());
    }

    // createNumber(String): 'L' qualifier with plain digits -> Long
    @Test
    public void testCreateNumber_longSuffix_returnsLong() throws Throwable {
        Number result = NumberUtils.createNumber("123L");
        assertTrue(result instanceof Long);
        assertEquals(123L, result.longValue());
    }

    // createNumber(String): 'L' qualifier but dec != null -> throws NumberFormatException
    @Test
    public void testCreateNumber_longSuffixWithDecimal_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createNumber("1.5L");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    // createNumber(String): 'f' qualifier branch -> Float
    @Test
    public void testCreateNumber_floatSuffix_returnsFloat() throws Throwable {
        Number result = NumberUtils.createNumber("1.5f");
        assertTrue(result instanceof Float);
        assertEquals(1.5f, result.floatValue(), 0.0001f);
    }

    // createNumber(String): 'd' qualifier branch -> Double
    @Test
    public void testCreateNumber_doubleSuffix_returnsDouble() throws Throwable {
        Number result = NumberUtils.createNumber("1.5d");
        assertTrue(result instanceof Double);
        assertEquals(1.5d, result.doubleValue(), 0.0000001d);
    }

    // createNumber(String): no qualifier, too big for Integer/Long -> BigInteger
    @Test
    public void testCreateNumber_tooBigForLong_returnsBigInteger() throws Throwable {
        String s = "123456789012345678901234567890";
        Number result = NumberUtils.createNumber(s);
        assertTrue(result instanceof BigInteger);
        assertEquals(new BigInteger(s), result);
    }



    // createNumber(String): no qualifier, has decimal point -> Float branch
    @Test
    public void testCreateNumber_decimalNoQualifier_returnsFloat() throws Throwable {
        Number result = NumberUtils.createNumber("1.5");
        assertTrue(result instanceof Float);
        assertEquals(1.5f, result.floatValue(), 0.0001f);
    }

    // createFloat(String): null branch
    @Test
    public void testCreateFloat_null_returnsNull() throws Throwable {
        assertNull(NumberUtils.createFloat(null));
    }

    // createDouble(String): valid conversion
    @Test
    public void testCreateDouble_validString_returnsCorrectValue() throws Throwable {
        Double d = NumberUtils.createDouble("2.5");
        assertEquals(2.5d, d.doubleValue(), 0.0000001d);
    }

    // createInteger(String): hex handling via Integer.decode (documented behavior of createInteger)
    @Test
    public void testCreateInteger_hexString_returnsInteger() throws Throwable {
        Integer i = NumberUtils.createInteger("0x1F");
        assertEquals(31, i.intValue());
    }

    // createLong(String): null branch
    @Test
    public void testCreateLong_null_returnsNull() throws Throwable {
        assertNull(NumberUtils.createLong(null));
    }

    // createBigInteger(String): valid conversion
    @Test
    public void testCreateBigInteger_validString_returnsCorrectValue() throws Throwable {
        String s = "123456789123456789123";
        assertEquals(new BigInteger(s), NumberUtils.createBigInteger(s));
    }

    // createBigDecimal(String): blank string throws NumberFormatException
    @Test
    public void testCreateBigDecimal_blankString_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createBigDecimal("");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    // min(long[]): null array branch
    @Test
    public void testMinLongArray_null_throwsIllegalArgumentException() throws Throwable {
        try {
            NumberUtils.min((long[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // min(long[]): empty array branch
    @Test
    public void testMinLongArray_empty_throwsIllegalArgumentException() throws Throwable {
        try {
            NumberUtils.min(new long[0]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // min(int[]): loop replaces min multiple times
    @Test
    public void testMinIntArray_multipleValues_returnsMinimum() throws Throwable {
        assertEquals(-1, NumberUtils.min(new int[] {3, -1, 2}));
    }

    // max(int[]): loop replaces max multiple times
    @Test
    public void testMaxIntArray_multipleValues_returnsMaximum() throws Throwable {
        assertEquals(3, NumberUtils.max(new int[] {3, -1, 2}));
    }

    // min(double[]): NaN short-circuit branch
    @Test
    public void testMinDoubleArray_withNaN_returnsNaN() throws Throwable {
        double result = NumberUtils.min(new double[] {1.0d, Double.NaN, 2.0d});
        assertTrue(Double.isNaN(result));
    }

    // max(float[]): NaN short-circuit branch
    @Test
    public void testMaxFloatArray_withNaN_returnsNaN() throws Throwable {
        float result = NumberUtils.max(new float[] {1.0f, Float.NaN, 2.0f});
        assertTrue(Float.isNaN(result));
    }

    // min(long,long,long): both if-branches taken
    @Test
    public void testMinThreeLongs_returnsSmallest() throws Throwable {
        assertEquals(3L, NumberUtils.min(5L, 3L, 9L));
    }

    // max(int,int,int): both if-branches taken
    @Test
    public void testMaxThreeInts_returnsLargest() throws Throwable {
        assertEquals(9, NumberUtils.max(5, 3, 9));
    }

    // isDigits(String): StringUtils.isEmpty branch for null and ""
    @Test
    public void testIsDigits_nullOrEmpty_returnsFalse() throws Throwable {
        assertFalse(NumberUtils.isDigits(null));
        assertFalse(NumberUtils.isDigits(""));
    }

    // isDigits(String): loop finds a non-digit character
    @Test
    public void testIsDigits_mixedChars_returnsFalseForNonDigit() throws Throwable {
        assertTrue(NumberUtils.isDigits("12345"));
        assertFalse(NumberUtils.isDigits("12a45"));
    }

    // isNumber(String): hex branch, trailing 'L' branch, digit-only valid cases
    @Test
    public void testIsNumber_validFormats_returnsTrue() throws Throwable {
        assertTrue(NumberUtils.isNumber("123"));
        assertTrue(NumberUtils.isNumber("12.3"));
        assertTrue(NumberUtils.isNumber("0x1F"));
        assertTrue(NumberUtils.isNumber("123L"));
    }

    // isNumber(String): null/empty, "0x" with no digits, 'L' with decimal, non-numeric chars
    @Test
    public void testIsNumber_invalidFormats_returnsFalse() throws Throwable {
        assertFalse(NumberUtils.isNumber(null));
        assertFalse(NumberUtils.isNumber(""));
        assertFalse(NumberUtils.isNumber("0x"));
        assertFalse(NumberUtils.isNumber("12.3L"));
        assertFalse(NumberUtils.isNumber("abc"));
    }
}
