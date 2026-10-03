package org.apache.commons.lang3.math;

import static org.junit.Assert.*;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberUtilsClaudeTest {

    // constructor is public, instance should be creatable
    @Test
    public void testConstructor_publicNoArg_createsInstance() throws Throwable {
        NumberUtils instance = new NumberUtils();
        assertNotNull(instance);
    }

    // toInt(String): null -> default zero branch
    @Test
    public void testToInt_nullString_returnsZero() throws Throwable {
        assertEquals(0, NumberUtils.toInt(null));
    }

    // toInt(String): invalid/empty string -> catch NumberFormatException branch
    @Test
    public void testToInt_emptyString_returnsZero() throws Throwable {
        assertEquals(0, NumberUtils.toInt(""));
    }

    // toInt(String): valid parse branch
    @Test
    public void testToInt_validString_returnsParsedValue() throws Throwable {
        assertEquals(1, NumberUtils.toInt("1"));
    }

    // toInt(String,int): null -> default value branch
    @Test
    public void testToIntWithDefault_nullString_returnsDefault() throws Throwable {
        assertEquals(1, NumberUtils.toInt(null, 1));
    }

    // toInt(String,int): invalid string -> catch NFE returns default
    @Test
    public void testToIntWithDefault_invalidString_returnsDefault() throws Throwable {
        assertEquals(5, NumberUtils.toInt("abc", 5));
    }

    // toLong(String): null -> zero branch
    @Test
    public void testToLong_nullString_returnsZero() throws Throwable {
        assertEquals(0L, NumberUtils.toLong(null));
    }

    // toLong(String): valid parse branch
    @Test
    public void testToLong_validString_returnsParsedValue() throws Throwable {
        assertEquals(1L, NumberUtils.toLong("1"));
    }

    // toFloat(String): null -> zero branch
    @Test
    public void testToFloat_nullString_returnsZero() throws Throwable {
        assertEquals(0.0f, NumberUtils.toFloat(null), 1e-9f);
    }

    // toFloat(String): valid parse branch
    @Test
    public void testToFloat_validString_returnsParsedValue() throws Throwable {
        assertEquals(1.5f, NumberUtils.toFloat("1.5"), 1e-9f);
    }

    // toDouble(String): null -> zero branch
    @Test
    public void testToDouble_nullString_returnsZero() throws Throwable {
        assertEquals(0.0d, NumberUtils.toDouble(null), 1e-9d);
    }

    // toDouble(String): valid parse branch
    @Test
    public void testToDouble_validString_returnsParsedValue() throws Throwable {
        assertEquals(1.5d, NumberUtils.toDouble("1.5"), 1e-9d);
    }

    // toByte(String): null -> zero branch
    @Test
    public void testToByte_nullString_returnsZero() throws Throwable {
        assertEquals((byte) 0, NumberUtils.toByte(null));
    }

    // toByte(String,byte): invalid -> default branch
    @Test
    public void testToByte_invalidString_returnsDefault() throws Throwable {
        assertEquals((byte) 9, NumberUtils.toByte("xyz", (byte) 9));
    }

    // toShort(String): null -> zero branch
    @Test
    public void testToShort_nullString_returnsZero() throws Throwable {
        assertEquals((short) 0, NumberUtils.toShort(null));
    }

    // toShort(String): valid parse branch
    @Test
    public void testToShort_validString_returnsParsedValue() throws Throwable {
        assertEquals((short) 1, NumberUtils.toShort("1"));
    }

    // createNumber: null input -> returns null
    @Test
    public void testCreateNumber_null_returnsNull() throws Throwable {
        assertNull(NumberUtils.createNumber(null));
    }

    // createNumber: empty string -> blank check throws NumberFormatException
    @Test
    public void testCreateNumber_blankEmpty_throwsNFE() throws Throwable {
        try {
            NumberUtils.createNumber("");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) { }
    }

    // createNumber: whitespace string -> blank check throws NumberFormatException
    @Test
    public void testCreateNumber_blankSpace_throwsNFE() throws Throwable {
        try {
            NumberUtils.createNumber(" ");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) { }
    }

    // createNumber: lowercase hex prefix with <=8 digits -> Integer
    @Test
    public void testCreateNumber_lowerHexPrefix_returnsInteger() throws Throwable {
        Number result = NumberUtils.createNumber("0x1A");
        assertTrue(result instanceof Integer);
        assertEquals(26, result.intValue());
    }

    // createNumber: leading zero without decimal/exponent -> octal Integer
    @Test
    public void testCreateNumber_octalLeadingZero_returnsInteger() throws Throwable {
        Number result = NumberUtils.createNumber("010");
        assertTrue(result instanceof Integer);
        assertEquals(8, result.intValue());
    }

    // createNumber: decimal with <=7 fractional digits -> Float
    @Test
    public void testCreateNumber_decimalSevenOrFewerDigits_returnsFloat() throws Throwable {
        Number result = NumberUtils.createNumber("45.5");
        assertTrue(result instanceof Float);
        assertEquals(45.5f, result.floatValue(), 1e-6f);
    }

    // createNumber: negative decimal value -> Float
    @Test
    public void testCreateNumber_negativeDecimal_returnsFloat() throws Throwable {
        Number result = NumberUtils.createNumber("-45.5");
        assertTrue(result instanceof Float);
        assertEquals(-45.5f, result.floatValue(), 1e-6f);
    }

    // createNumber: 'L' qualifier on plain digits -> Long
    @Test
    public void testCreateNumber_longQualifier_returnsLong() throws Throwable {
        Number result = NumberUtils.createNumber("-123L");
        assertTrue(result instanceof Long);
        assertEquals(-123L, result.longValue());
    }

    // createNumber: 'L' qualifier combined with decimal point -> NumberFormatException
    @Test
    public void testCreateNumber_longQualifierWithDecimal_throwsNFE() throws Throwable {
        try {
            NumberUtils.createNumber("1.1L");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) { }
    }

    // createNumber: 'F' qualifier -> Float
    @Test
    public void testCreateNumber_floatQualifier_returnsFloat() throws Throwable {
        Number result = NumberUtils.createNumber("123F");
        assertTrue(result instanceof Float);
        assertEquals(123.0f, result.floatValue(), 1e-6f);
    }

    // createNumber: 'D' qualifier -> Double
    @Test
    public void testCreateNumber_doubleQualifier_returnsDouble() throws Throwable {
        Number result = NumberUtils.createNumber("123D");
        assertTrue(result instanceof Double);
        assertEquals(123.0d, result.doubleValue(), 1e-9d);
    }

    // createNumber: unsupported trailing qualifier -> default switch throws NFE
    @Test
    public void testCreateNumber_invalidTypeQualifier_throwsNFE() throws Throwable {
        try {
            NumberUtils.createNumber("123Q");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) { }
    }

    // createNumber: value beyond Integer range but within Long -> Long
    @Test
    public void testCreateNumber_beyondIntRange_returnsLong() throws Throwable {
        Number result = NumberUtils.createNumber("2147483648");
        assertTrue(result instanceof Long);
        assertEquals(2147483648L, result.longValue());
    }

    // createNumber: value beyond Long range -> BigInteger
    @Test
    public void testCreateNumber_beyondLongRange_returnsBigInteger() throws Throwable {
        Number result = NumberUtils.createNumber("99999999999999999999");
        assertTrue(result instanceof BigInteger);
        assertEquals(new BigInteger("99999999999999999999"), result);
    }

    // createNumber: fractional digits > 16 -> BigDecimal
    @Test
    public void testCreateNumber_manyDecimalDigits_returnsBigDecimal() throws Throwable {
        String str = "1.11111111111111111"; // 17 fractional digits
        Number result = NumberUtils.createNumber(str);
        assertTrue(result instanceof BigDecimal);
        assertEquals(0, new BigDecimal(str).compareTo((BigDecimal) result));
    }

    // createNumber: lowercase hex prefix with >16 hex digits -> BigInteger via delegate
    @Test
    public void testCreateNumber_largeHexBeyond16Digits_returnsBigInteger() throws Throwable {
        String str = "0xFFFFFFFFFFFFFFFFF"; // 17 hex digits after prefix
        Number result = NumberUtils.createNumber(str);
        assertTrue(result instanceof BigInteger);
        assertEquals(new BigInteger("FFFFFFFFFFFFFFFFF", 16), result);
    }

    // createBigInteger: lowercase "0x" hex prefix parses correctly
    @Test
    public void testCreateBigInteger_lowerHexPrefix_returnsHexValue() throws Throwable {
        BigInteger result = NumberUtils.createBigInteger("0x10");
        assertEquals(BigInteger.valueOf(16), result);
    }

    // createBigInteger: uppercase "0X" hex prefix must also parse as hex per contract (bug target)
    @Test
    public void testCreateBigInteger_upperHexPrefix_returnsHexValue() throws Throwable {
        BigInteger result = NumberUtils.createBigInteger("0X10");
        assertEquals(BigInteger.valueOf(16), result);
    }

    // createBigInteger: leading zero with extra digits -> octal
    @Test
    public void testCreateBigInteger_octalPrefix_returnsOctalValue() throws Throwable {
        BigInteger result = NumberUtils.createBigInteger("010");
        assertEquals(BigInteger.valueOf(8), result);
    }

    // createBigInteger: '#' alternative hex prefix
    @Test
    public void testCreateBigInteger_hashPrefix_returnsHexValue() throws Throwable {
        BigInteger result = NumberUtils.createBigInteger("#1A");
        assertEquals(BigInteger.valueOf(26), result);
    }

    // createBigInteger: negative hex value negated after parse
    @Test
    public void testCreateBigInteger_negativeHex_returnsNegativeValue() throws Throwable {
        BigInteger result = NumberUtils.createBigInteger("-0x1A");
        assertEquals(BigInteger.valueOf(-26), result);
    }

    // createBigDecimal: blank string throws NumberFormatException
    @Test
    public void testCreateBigDecimal_blank_throwsNFE() throws Throwable {
        try {
            NumberUtils.createBigDecimal("  ");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) { }
    }

    // createBigDecimal: leading "--" guarded and throws NumberFormatException
    @Test
    public void testCreateBigDecimal_doubleMinus_throwsNFE() throws Throwable {
        try {
            NumberUtils.createBigDecimal("--5");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) { }
    }

    // createBigDecimal: valid decimal string parses correctly
    @Test
    public void testCreateBigDecimal_valid_returnsBigDecimal() throws Throwable {
        BigDecimal result = NumberUtils.createBigDecimal("3.14");
        assertEquals(0, new BigDecimal("3.14").compareTo(result));
    }

    // createFloat: null returns null
    @Test
    public void testCreateFloat_null_returnsNull() throws Throwable {
        assertNull(NumberUtils.createFloat(null));
    }

    // createDouble: null returns null
    @Test
    public void testCreateDouble_null_returnsNull() throws Throwable {
        assertNull(NumberUtils.createDouble(null));
    }

    // createInteger: hex string decoded via Integer.decode
    @Test
    public void testCreateInteger_hex_returnsDecodedValue() throws Throwable {
        Integer result = NumberUtils.createInteger("0x10");
        assertEquals(Integer.valueOf(16), result);
    }

    // createLong: hex string decoded via Long.decode
    @Test
    public void testCreateLong_hex_returnsDecodedValue() throws Throwable {
        Long result = NumberUtils.createLong("0x10");
        assertEquals(Long.valueOf(16L), result);
    }

    // isDigits: null/empty false, all-digit true, mixed false
    @Test
    public void testIsDigits_variousInputs() throws Throwable {
        assertFalse(NumberUtils.isDigits(null));
        assertFalse(NumberUtils.isDigits(""));
        assertTrue(NumberUtils.isDigits("12345"));
        assertFalse(NumberUtils.isDigits("123a5"));
    }

    // isNumber: covers hex, exponent, decimal-with-L(false), trailing-e(false)
    @Test
    public void testIsNumber_variousInputs() throws Throwable {
        assertFalse(NumberUtils.isNumber(null));
        assertFalse(NumberUtils.isNumber(""));
        assertTrue(NumberUtils.isNumber("123"));
        assertTrue(NumberUtils.isNumber("-12.3"));
        assertTrue(NumberUtils.isNumber("0x1A"));
        assertFalse(NumberUtils.isNumber("0x"));
        assertTrue(NumberUtils.isNumber("123L"));
        assertFalse(NumberUtils.isNumber("1.1L"));
        assertTrue(NumberUtils.isNumber("1e10"));
        assertFalse(NumberUtils.isNumber("1e"));
    }

    // min(long[]): finds minimum across multiple elements
    @Test
    public void testMinLongArray_returnsMinimum() throws Throwable {
        long[] arr = {3L, 1L, 2L};
        assertEquals(1L, NumberUtils.min(arr));
    }

    // min(int[]): null array -> IllegalArgumentException
    @Test
    public void testMinIntArray_nullArray_throwsIAE() throws Throwable {
        try {
            NumberUtils.min((int[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // min(int[]): empty array -> IllegalArgumentException
    @Test
    public void testMinIntArray_emptyArray_throwsIAE() throws Throwable {
        try {
            NumberUtils.min(new int[0]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // max(double[]): NaN in array causes NaN result (special branch)
    @Test
    public void testMaxDoubleArray_withNaN_returnsNaN() throws Throwable {
        double[] arr = {1.0d, Double.NaN, 2.0d};
        assertTrue(Double.isNaN(NumberUtils.max(arr)));
    }

    // min(float[]): finds minimum, single loop iteration branch exercised
    @Test
    public void testMinFloatArray_returnsMinimum() throws Throwable {
        float[] arr = {3.0f, 1.0f, 2.0f};
        assertEquals(1.0f, NumberUtils.min(arr), 1e-6f);
    }

    // max(byte[]): finds maximum across elements
    @Test
    public void testMaxByteArray_returnsMaximum() throws Throwable {
        byte[] arr = {3, 1, 2};
        assertEquals((byte) 3, NumberUtils.max(arr));
    }

    // 3-param min(long): smallest of three selected
    @Test
    public void test3ParamMinLong_returnsSmallest() throws Throwable {
        assertEquals(1L, NumberUtils.min(3L, 1L, 2L));
    }

    // 3-param max(int): largest of three selected
    @Test
    public void test3ParamMaxInt_returnsLargest() throws Throwable {
        assertEquals(3, NumberUtils.max(3, 1, 2));
    }

    // 3-param min(double): NaN propagates via Math.min
    @Test
    public void test3ParamMinDouble_withNaN_returnsNaN() throws Throwable {
        assertTrue(Double.isNaN(NumberUtils.min(1.0d, Double.NaN, 2.0d)));
    }

    // 3-param max(float): largest of three selected
    @Test
    public void test3ParamMaxFloat_returnsLargest() throws Throwable {
        assertEquals(3.0f, NumberUtils.max(1.0f, 3.0f, 2.0f), 1e-6f);
    }
}
