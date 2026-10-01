package org.apache.commons.lang3.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class NumberUtilsClaudeTest {

    // toInt(String): null branch -> defaultValue(0)
    @Test
    public void testToInt_nullString_returnsZero() throws Throwable {
        assertEquals(0, NumberUtils.toInt((String) null));
    }

    // toInt(String,int): catch(NumberFormatException) branch -> returns default
    @Test
    public void testToIntWithDefault_invalidString_returnsDefault() throws Throwable {
        assertEquals(42, NumberUtils.toInt("abc", 42));
    }

    // toLong(String,long): str==null branch -> returns default
    @Test
    public void testToLongWithDefault_nullString_returnsDefault() throws Throwable {
        assertEquals(99L, NumberUtils.toLong((String) null, 99L));
    }

    // toFloat(String,float): catch(NumberFormatException) branch -> returns default
    @Test
    public void testToFloatWithDefault_invalidString_returnsDefault() throws Throwable {
        assertEquals(3.3f, NumberUtils.toFloat("xyz", 3.3f), 0.0001f);
    }

    // toDouble(String): try branch succeeds -> parsed value
    @Test
    public void testToDouble_validString_returnsParsedValue() throws Throwable {
        assertEquals(3.14d, NumberUtils.toDouble("3.14"), 0.0001d);
    }

    // toByte(String,byte): catch(NumberFormatException) branch -> returns default
    @Test
    public void testToByteWithDefault_invalidString_returnsDefault() throws Throwable {
        assertEquals((byte) 5, NumberUtils.toByte("abc", (byte) 5));
    }

    // toShort(String): str==null branch -> returns zero
    @Test
    public void testToShort_nullString_returnsZero() throws Throwable {
        assertEquals((short) 0, NumberUtils.toShort((String) null));
    }

    // toShort(String,short): try branch succeeds -> parsed value
    @Test
    public void testToShortWithDefault_validString_returnsParsedValue() throws Throwable {
        assertEquals((short) 7, NumberUtils.toShort("7", (short) 0));
    }

    // createNumber(String): str==null branch -> returns null
    @Test
    public void testCreateNumber_nullString_returnsNull() throws Throwable {
        assertNull(NumberUtils.createNumber(null));
    }

    // createNumber(String): StringUtils.isBlank branch -> throws NumberFormatException
    @Test
    public void testCreateNumber_blankString_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createNumber("");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createNumber(String): str.startsWith("--") branch -> returns null
    @Test
    public void testCreateNumber_doubleMinusPrefix_returnsNull() throws Throwable {
        assertNull(NumberUtils.createNumber("--1"));
    }

    // createNumber(String): lowercase "0x" hex-prefix branch -> Integer via createInteger
    @Test
    public void testCreateNumber_hexPrefixLowercase_returnsInteger() throws Throwable {
        Number n = NumberUtils.createNumber("0x1A");
        assertTrue(n instanceof Integer);
        assertEquals(26, n.intValue());
    }

    // createNumber(String): per contract, "0X" (uppercase X) must also be treated as hexadecimal
    @Test
    public void testCreateNumber_hexPrefixUppercase_returnsInteger() throws Throwable {
        Number n = NumberUtils.createNumber("0X1A");
        assertTrue(n instanceof Integer);
        assertEquals(26, n.intValue());
    }

    // createNumber(String): "-0x" negative hex-prefix branch -> negative Integer
    @Test
    public void testCreateNumber_negativeHexPrefixLowercase_returnsNegativeInteger() throws Throwable {
        Number n = NumberUtils.createNumber("-0x1A");
        assertTrue(n instanceof Integer);
        assertEquals(-26, n.intValue());
    }

    // createNumber(String): no decimal/exp, createInteger succeeds branch
    @Test
    public void testCreateNumber_plainInteger_returnsInteger() throws Throwable {
        Number n = NumberUtils.createNumber("123");
        assertTrue(n instanceof Integer);
        assertEquals(123, n.intValue());
    }

    // createNumber(String): createInteger fails (overflow), createLong succeeds branch
    @Test
    public void testCreateNumber_longRangeValue_returnsLong() throws Throwable {
        Number n = NumberUtils.createNumber("12345678901");
        assertTrue(n instanceof Long);
        assertEquals(12345678901L, n.longValue());
    }

    // createNumber(String): 'L' qualifier branch with pure digits -> createLong
    @Test
    public void testCreateNumber_longQualifier_returnsLong() throws Throwable {
        Number n = NumberUtils.createNumber("123L");
        assertTrue(n instanceof Long);
        assertEquals(123L, n.longValue());
    }

    // createNumber(String): decimal, no qualifier -> createFloat branch succeeds
    @Test
    public void testCreateNumber_decimalWithoutQualifier_returnsFloat() throws Throwable {
        Number n = NumberUtils.createNumber("1.5");
        assertTrue(n instanceof Float);
        assertEquals(1.5f, n.floatValue(), 0.0001f);
    }

    // createNumber(String): 'D' qualifier branch -> createDouble
    @Test
    public void testCreateNumber_doubleQualifier_returnsDouble() throws Throwable {
        Number n = NumberUtils.createNumber("1.5D");
        assertTrue(n instanceof Double);
        assertEquals(1.5d, n.doubleValue(), 0.0001d);
    }

    // createNumber(String): 'F' qualifier branch -> createFloat
    @Test
    public void testCreateNumber_floatQualifier_returnsFloat() throws Throwable {
        Number n = NumberUtils.createNumber("1.5F");
        assertTrue(n instanceof Float);
        assertEquals(1.5f, n.floatValue(), 0.0001f);
    }

    // createNumber(String): malformed decimal (two dots), all create* fail -> propagates NumberFormatException
    @Test
    public void testCreateNumber_malformedDecimal_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createNumber("1.2.3");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createNumber(String): unrecognized trailing qualifier -> default switch branch throws
    @Test
    public void testCreateNumber_invalidQualifier_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createNumber("123Q");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createFloat(String): str==null branch -> returns null
    @Test
    public void testCreateFloat_nullString_returnsNull() throws Throwable {
        assertNull(NumberUtils.createFloat(null));
    }

    // createFloat(String): invalid numeric string -> throws NumberFormatException
    @Test
    public void testCreateFloat_invalidString_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createFloat("abc");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createDouble(String): valid numeric string -> parsed Double value
    @Test
    public void testCreateDouble_validString_returnsDoubleValue() throws Throwable {
        Double d = NumberUtils.createDouble("2.5");
        assertEquals(2.5d, d.doubleValue(), 0.0001d);
    }

    // createInteger(String): hex string decoded via Integer.decode
    @Test
    public void testCreateInteger_hexString_returnsDecodedValue() throws Throwable {
        Integer i = NumberUtils.createInteger("0x10");
        assertEquals(16, i.intValue());
    }

    // createLong(String): valid numeric string -> parsed Long value
    @Test
    public void testCreateLong_validString_returnsLongValue() throws Throwable {
        Long l = NumberUtils.createLong("100");
        assertEquals(100L, l.longValue());
    }

    // createBigInteger(String): valid large numeric string -> matching BigInteger
    @Test
    public void testCreateBigInteger_validString_returnsBigIntegerValue() throws Throwable {
        BigInteger b = NumberUtils.createBigInteger("123456789012345678901234567890");
        assertEquals(new BigInteger("123456789012345678901234567890"), b);
    }

    // createBigDecimal(String): blank string branch -> throws NumberFormatException
    @Test
    public void testCreateBigDecimal_blankString_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createBigDecimal("");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createBigDecimal(String): valid numeric string -> matching BigDecimal
    @Test
    public void testCreateBigDecimal_validString_returnsBigDecimalValue() throws Throwable {
        BigDecimal b = NumberUtils.createBigDecimal("1.23");
        assertEquals(new BigDecimal("1.23"), b);
    }

    // min(long[]): array==null branch -> throws IllegalArgumentException
    @Test
    public void testMinLongArray_nullArray_throwsIllegalArgumentException() throws Throwable {
        try {
            NumberUtils.min((long[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // min(long[]): array.length==0 branch -> throws IllegalArgumentException
    @Test
    public void testMinLongArray_emptyArray_throwsIllegalArgumentException() throws Throwable {
        try {
            NumberUtils.min(new long[0]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // min(long[]): multiple elements loop branch -> smallest value found
    @Test
    public void testMinLongArray_multipleElements_returnsMinimum() throws Throwable {
        assertEquals(2L, NumberUtils.min(new long[]{5L, 2L, 8L}));
    }

    // min(int[]): multiple elements with negative value -> smallest value found
    @Test
    public void testMinIntArray_multipleElements_returnsMinimum() throws Throwable {
        assertEquals(-3, NumberUtils.min(new int[]{5, -3, 8}));
    }

    // min(double[]): Double.isNaN branch -> returns NaN immediately
    @Test
    public void testMinDoubleArray_containsNaN_returnsNaN() throws Throwable {
        double r = NumberUtils.min(new double[]{1.0d, Double.NaN, 2.0d});
        assertTrue(Double.isNaN(r));
    }

    // max(long[]): multiple elements loop branch -> largest value found
    @Test
    public void testMaxLongArray_multipleElements_returnsMaximum() throws Throwable {
        assertEquals(8L, NumberUtils.max(new long[]{5L, 2L, 8L}));
    }

    // max(int[]): multiple elements with negative value -> largest value found
    @Test
    public void testMaxIntArray_multipleElements_returnsMaximum() throws Throwable {
        assertEquals(8, NumberUtils.max(new int[]{5, -3, 8}));
    }

    // min(long,long,long): b<a and c<a both true -> picks smallest
    @Test
    public void testMin3Long_returnsSmallest() throws Throwable {
        assertEquals(1L, NumberUtils.min(3L, 1L, 2L));
    }

    // max(int,int,int): b>a and c>a both true -> picks largest
    @Test
    public void testMax3Int_returnsLargest() throws Throwable {
        assertEquals(9, NumberUtils.max(3, 9, 2));
    }

    // min(double,double,double): NaN propagation through Math.min
    @Test
    public void testMin3Double_withNaN_returnsNaN() throws Throwable {
        double r = NumberUtils.min(1.0d, Double.NaN, 2.0d);
        assertTrue(Double.isNaN(r));
    }

    // isDigits(String): StringUtils.isEmpty branch for null -> returns false
    @Test
    public void testIsDigits_nullString_returnsFalse() throws Throwable {
        assertFalse(NumberUtils.isDigits(null));
    }

    // isDigits(String): all characters digits -> returns true
    @Test
    public void testIsDigits_validDigits_returnsTrue() throws Throwable {
        assertTrue(NumberUtils.isDigits("12345"));
    }

    // isDigits(String): non-digit character present -> returns false
    @Test
    public void testIsDigits_withLetters_returnsFalse() throws Throwable {
        assertFalse(NumberUtils.isDigits("12a45"));
    }

    // isNumber(String): "0x" prefix hex-detection branch -> returns true
    @Test
    public void testIsNumber_hexString_returnsTrue() throws Throwable {
        assertTrue(NumberUtils.isNumber("0x1A"));
    }

    // isNumber(String): non-numeric characters -> returns false
    @Test
    public void testIsNumber_invalidString_returnsFalse() throws Throwable {
        assertFalse(NumberUtils.isNumber("abc"));
    }
}
