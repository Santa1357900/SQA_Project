package org.apache.commons.lang;

import static org.junit.Assert.*;
import org.junit.Test;
import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberUtilsClaudeTest {

    // Constructor is public; just verify instantiation works
    @Test
    public void testConstructor_createsInstance() throws Throwable {
        NumberUtils instance = new NumberUtils();
        assertNotNull(instance);
    }

    // stringToInt(String): valid numeric string parses correctly
    @Test
    public void testStringToInt_validNumber_returnsParsedValue() throws Throwable {
        assertEquals(42, NumberUtils.stringToInt("42"));
    }

    // stringToInt(String): invalid string falls back to default zero
    @Test
    public void testStringToInt_invalidNumber_returnsZero() throws Throwable {
        assertEquals(0, NumberUtils.stringToInt("abc"));
    }

    // stringToInt(String,int): valid numeric string parses correctly, default unused
    @Test
    public void testStringToIntWithDefault_validNumber_returnsParsedValue() throws Throwable {
        assertEquals(42, NumberUtils.stringToInt("42", 99));
    }

    // stringToInt(String,int): invalid string (whitespace) returns supplied default
    @Test
    public void testStringToIntWithDefault_invalidNumber_returnsDefault() throws Throwable {
        assertEquals(99, NumberUtils.stringToInt(" 123 ", 99));
    }

    // createNumber: null input branch returns null
    @Test
    public void testCreateNumber_null_returnsNull() throws Throwable {
        assertNull(NumberUtils.createNumber(null));
    }

    // createNumber: empty string branch throws NumberFormatException
    @Test
    public void testCreateNumber_emptyString_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createNumber("");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createNumber: "--" prefix protection branch returns null
    @Test
    public void testCreateNumber_doubleMinus_returnsNull() throws Throwable {
        assertNull(NumberUtils.createNumber("--1"));
    }

    // createNumber: "0x" prefix hex branch returns positive Integer
    @Test
    public void testCreateNumber_hexPositive_returnsInteger() throws Throwable {
        Number n = NumberUtils.createNumber("0x1F");
        assertTrue(n instanceof Integer);
        assertEquals(31, n.intValue());
    }

    // createNumber: "-0x" prefix hex branch returns negative Integer
    @Test
    public void testCreateNumber_hexNegative_returnsInteger() throws Throwable {
        Number n = NumberUtils.createNumber("-0x1F");
        assertTrue(n instanceof Integer);
        assertEquals(-31, n.intValue());
    }

    // createNumber: exponent marker appears before decimal point -> invalid, throws
    @Test
    public void testCreateNumber_expBeforeDecimal_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createNumber("1e2.3");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createNumber: plain digits, no dec/exp/qualifier -> Integer branch
    @Test
    public void testCreateNumber_plainInt_returnsInteger() throws Throwable {
        Number n = NumberUtils.createNumber("42");
        assertTrue(n instanceof Integer);
        assertEquals(42, n.intValue());
    }

    // createNumber: value too big for Integer, fits in Long
    @Test
    public void testCreateNumber_largeValue_returnsLong() throws Throwable {
        Number n = NumberUtils.createNumber("12345678901");
        assertTrue(n instanceof Long);
        assertEquals(12345678901L, n.longValue());
    }

    // createNumber: value too big for Long -> BigInteger fallback
    @Test
    public void testCreateNumber_veryLargeValue_returnsBigInteger() throws Throwable {
        Number n = NumberUtils.createNumber("123456789012345678901234567890");
        assertTrue(n instanceof BigInteger);
        assertEquals(new BigInteger("123456789012345678901234567890"), n);
    }

    // createNumber: decimal point present, no qualifier -> Float branch
    @Test
    public void testCreateNumber_decimal_returnsFloat() throws Throwable {
        Number n = NumberUtils.createNumber("1.5");
        assertTrue(n instanceof Float);
        assertEquals(1.5f, n.floatValue(), 1e-6f);
    }

    // createNumber: explicit 'F' qualifier -> Float branch
    @Test
    public void testCreateNumber_floatSuffix_returnsFloat() throws Throwable {
        Number n = NumberUtils.createNumber("3F");
        assertTrue(n instanceof Float);
        assertEquals(3.0f, n.floatValue(), 1e-6f);
    }

    // createNumber: explicit 'D' qualifier -> Double branch
    @Test
    public void testCreateNumber_doubleSuffix_returnsDouble() throws Throwable {
        Number n = NumberUtils.createNumber("3D");
        assertTrue(n instanceof Double);
        assertEquals(3.0d, n.doubleValue(), 1e-9);
    }

    // createNumber: explicit 'L' qualifier on plain digits -> Long branch
    @Test
    public void testCreateNumber_longSuffix_returnsLong() throws Throwable {
        Number n = NumberUtils.createNumber("123L");
        assertTrue(n instanceof Long);
        assertEquals(123L, n.longValue());
    }

    // createNumber: 'L' qualifier value too big for Long -> BigInteger fallback
    @Test
    public void testCreateNumber_longSuffixOverflow_returnsBigInteger() throws Throwable {
        Number n = NumberUtils.createNumber("99999999999999999999L");
        assertTrue(n instanceof BigInteger);
        assertEquals(new BigInteger("99999999999999999999"), n);
    }

    // createNumber: 'L' qualifier combined with decimal point is invalid -> throws
    @Test
    public void testCreateNumber_longSuffixWithDecimal_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createNumber("1.5L");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // Bug: createNumber("L") has no digits before the qualifier; per contract this is an
    // unparsable number and must raise NumberFormatException, not StringIndexOutOfBoundsException
    @Test
    public void testCreateNumber_onlyLSuffix_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createNumber("L");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createNumber: unsupported trailing qualifier letter -> default throw branch
    @Test
    public void testCreateNumber_invalidSuffix_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createNumber("123X");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createNumber: exponent too large for Float/Double (Infinity) -> BigDecimal fallback
    @Test
    public void testCreateNumber_hugeExponent_returnsBigDecimal() throws Throwable {
        Number n = NumberUtils.createNumber("1E400");
        assertTrue(n instanceof BigDecimal);
    }

    // createFloat: valid numeric string converts correctly
    @Test
    public void testCreateFloat_validString_returnsFloat() throws Throwable {
        assertEquals(2.5f, NumberUtils.createFloat("2.5").floatValue(), 1e-6f);
    }

    // createFloat: invalid (empty) string throws NumberFormatException
    @Test
    public void testCreateFloat_invalidString_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createFloat("");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createDouble: valid numeric string converts correctly
    @Test
    public void testCreateDouble_validString_returnsDouble() throws Throwable {
        assertEquals(3.5d, NumberUtils.createDouble("3.5").doubleValue(), 1e-9);
    }

    // createDouble: invalid (empty) string throws NumberFormatException
    @Test
    public void testCreateDouble_invalidString_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createDouble("");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createInteger: plain decimal and hex-prefixed strings both decode correctly
    @Test
    public void testCreateInteger_decimalAndHex_returnsInteger() throws Throwable {
        assertEquals(new Integer(10), NumberUtils.createInteger("10"));
        assertEquals(new Integer(31), NumberUtils.createInteger("0x1F"));
    }

    // createInteger: invalid string throws NumberFormatException
    @Test
    public void testCreateInteger_invalidString_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createInteger("abc");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createLong: valid numeric string converts correctly
    @Test
    public void testCreateLong_validString_returnsLong() throws Throwable {
        assertEquals(new Long(100L), NumberUtils.createLong("100"));
    }

    // createLong: invalid string throws NumberFormatException
    @Test
    public void testCreateLong_invalidString_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createLong("abc");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // createBigInteger and createBigDecimal: valid strings convert correctly
    @Test
    public void testCreateBigIntegerAndBigDecimal_validString_returnsCorrectValue() throws Throwable {
        assertEquals(new BigInteger("123"), NumberUtils.createBigInteger("123"));
        assertEquals(new BigDecimal("1.23"), NumberUtils.createBigDecimal("1.23"));
    }

    // createBigInteger and createBigDecimal: invalid strings throw NumberFormatException
    @Test
    public void testCreateBigIntegerAndBigDecimal_invalidString_throwsNumberFormatException() throws Throwable {
        try {
            NumberUtils.createBigInteger("abc");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
        try {
            NumberUtils.createBigDecimal("xyz");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // minimum(int/long): covers branches where a, b, or c holds the smallest value
    @Test
    public void testMinimum_variousOrders_returnsSmallest() throws Throwable {
        assertEquals(1, NumberUtils.minimum(1, 2, 3));
        assertEquals(1, NumberUtils.minimum(3, 1, 2));
        assertEquals(1, NumberUtils.minimum(3, 2, 1));
        assertEquals(1L, NumberUtils.minimum(1L, 2L, 3L));
        assertEquals(1L, NumberUtils.minimum(3L, 1L, 2L));
    }

    // maximum(int/long): covers branches where a, b, or c holds the largest value
    @Test
    public void testMaximum_variousOrders_returnsLargest() throws Throwable {
        assertEquals(3, NumberUtils.maximum(1, 2, 3));
        assertEquals(3, NumberUtils.maximum(3, 1, 2));
        assertEquals(3, NumberUtils.maximum(1, 3, 2));
        assertEquals(3L, NumberUtils.maximum(1L, 2L, 3L));
        assertEquals(3L, NumberUtils.maximum(3L, 2L, 1L));
    }

    // compare(double,double): less-than, greater-than, and equal branches
    @Test
    public void testCompareDouble_lessGreaterEqual_returnsSignedResult() throws Throwable {
        assertEquals(-1, NumberUtils.compare(1.0d, 2.0d));
        assertEquals(1, NumberUtils.compare(2.0d, 1.0d));
        assertEquals(0, NumberUtils.compare(1.0d, 1.0d));
    }

    // compare(double,double): NaN compared with NaN returns zero per javadoc
    @Test
    public void testCompareDouble_nanWithNan_returnsZero() throws Throwable {
        assertEquals(0, NumberUtils.compare(Double.NaN, Double.NaN));
    }

    // compare(double,double): +0.0 ranks above -0.0 per documented ordering
    @Test
    public void testCompareDouble_zeroVsNegativeZero_returnsSignedResult() throws Throwable {
        assertEquals(1, NumberUtils.compare(0.0d, -0.0d));
        assertEquals(-1, NumberUtils.compare(-0.0d, 0.0d));
    }

    // compare(float,float): less-than, greater-than, and equal branches
    @Test
    public void testCompareFloat_lessGreaterEqual_returnsSignedResult() throws Throwable {
        assertEquals(-1, NumberUtils.compare(1.0f, 2.0f));
        assertEquals(1, NumberUtils.compare(2.0f, 1.0f));
        assertEquals(0, NumberUtils.compare(1.0f, 1.0f));
    }

    // compare(float,float): NaN==NaN returns zero; +0.0f ranks above -0.0f
    @Test
    public void testCompareFloat_nanAndZero_edgeCases() throws Throwable {
        assertEquals(0, NumberUtils.compare(Float.NaN, Float.NaN));
        assertEquals(1, NumberUtils.compare(0.0f, -0.0f));
    }

    // isDigits: null, empty, all-digit, mixed, and whitespace inputs
    @Test
    public void testIsDigits_variousInputs_returnsExpected() throws Throwable {
        assertFalse(NumberUtils.isDigits(null));
        assertFalse(NumberUtils.isDigits(""));
        assertTrue(NumberUtils.isDigits("12345"));
        assertFalse(NumberUtils.isDigits("12a45"));
        assertFalse(NumberUtils.isDigits(" "));
    }

    // isNumber: null/empty branch and hexadecimal branch (valid and "0x" alone)
    @Test
    public void testIsNumber_nullEmptyAndHex_returnsExpected() throws Throwable {
        assertFalse(NumberUtils.isNumber(null));
        assertFalse(NumberUtils.isNumber(""));
        assertTrue(NumberUtils.isNumber("0x1F"));
        assertTrue(NumberUtils.isNumber("-0x1A"));
        assertFalse(NumberUtils.isNumber("0x"));
    }

    // isNumber: plain/decimal/negative valid forms and invalid decimal/sign forms
    @Test
    public void testIsNumber_plainDecimalAndInvalid_returnsExpected() throws Throwable {
        assertTrue(NumberUtils.isNumber("123"));
        assertTrue(NumberUtils.isNumber("-123"));
        assertTrue(NumberUtils.isNumber("12.3"));
        assertFalse(NumberUtils.isNumber("12.3.4"));
        assertFalse(NumberUtils.isNumber("+123"));
        assertFalse(NumberUtils.isNumber("abc"));
    }

    // isNumber: exponent notation branches and type-qualifier (f/D/L) branches
    @Test
    public void testIsNumber_exponentAndTypeQualifiers_returnsExpected() throws Throwable {
        assertTrue(NumberUtils.isNumber("1e10"));
        assertFalse(NumberUtils.isNumber("1e"));
        assertFalse(NumberUtils.isNumber("1ee2"));
        assertTrue(NumberUtils.isNumber("123f"));
        assertTrue(NumberUtils.isNumber("123D"));
        assertTrue(NumberUtils.isNumber("123L"));
    }
}
