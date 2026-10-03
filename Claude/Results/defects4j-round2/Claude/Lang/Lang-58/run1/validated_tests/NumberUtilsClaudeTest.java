package org.apache.commons.lang.math;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;
import static org.junit.Assert.*;

public class NumberUtilsClaudeTest {

    // Covers deprecated stringToInt(String) and stringToInt(String,int) delegating to toInt
    @Test
    public void testStringToInt_delegatesToToInt() throws Throwable {
        assertEquals(0, NumberUtils.stringToInt(null));
        assertEquals(5, NumberUtils.stringToInt("5"));
        assertEquals(9, NumberUtils.stringToInt("abc", 9));
    }

    // Covers toInt(String) null, empty(NFE caught), and valid branches
    @Test
    public void testToInt_nullEmptyAndValid() throws Throwable {
        assertEquals(0, NumberUtils.toInt(null));
        assertEquals(0, NumberUtils.toInt(""));
        assertEquals(42, NumberUtils.toInt("42"));
    }

    // Covers toInt(String,int) null and NumberFormatException branches returning default
    @Test
    public void testToIntWithDefault_invalidReturnsDefault() throws Throwable {
        assertEquals(9, NumberUtils.toInt(null, 9));
        assertEquals(9, NumberUtils.toInt("abc", 9));
        assertEquals(3, NumberUtils.toInt("3", 9));
    }

    // Covers toLong(String) null, empty, and valid branches
    @Test
    public void testToLong_nullEmptyAndValid() throws Throwable {
        assertEquals(0L, NumberUtils.toLong(null));
        assertEquals(0L, NumberUtils.toLong(""));
        assertEquals(100L, NumberUtils.toLong("100"));
    }

    // Covers toLong(String,long) null and NFE branches
    @Test
    public void testToLongWithDefault_invalidReturnsDefault() throws Throwable {
        assertEquals(7L, NumberUtils.toLong(null, 7L));
        assertEquals(7L, NumberUtils.toLong("xyz", 7L));
        assertEquals(55L, NumberUtils.toLong("55", 7L));
    }

    // Covers toFloat(String) null, invalid(NFE) and valid branches, default is 0.0f
    @Test
    public void testToFloat_nullAndValid() throws Throwable {
        assertEquals(0.0f, NumberUtils.toFloat(null), 0.0001f);
        assertEquals(0.0f, NumberUtils.toFloat("bad"), 0.0001f);
        assertEquals(2.25f, NumberUtils.toFloat("2.25"), 0.0001f);
    }

    // Covers toFloat(String,float) null branch; per prose "if null, default value is returned"
    @Test
    public void testToFloatWithDefault_nullReturnsDefaultValue() throws Throwable {
        assertEquals(2.5f, NumberUtils.toFloat(null, 2.5f), 0.0001f);
        assertEquals(2.5f, NumberUtils.toFloat("bad", 2.5f), 0.0001f);
        assertEquals(1.5f, NumberUtils.toFloat("1.5", 2.5f), 0.0001f);
    }

    // Covers toDouble(String) null, invalid and valid branches
    @Test
    public void testToDouble_nullAndValid() throws Throwable {
        assertEquals(0.0d, NumberUtils.toDouble(null), 0.0001d);
        assertEquals(0.0d, NumberUtils.toDouble("bad"), 0.0001d);
        assertEquals(3.5d, NumberUtils.toDouble("3.5"), 0.0001d);
    }

    // Covers toDouble(String,double) null branch, matches javadoc example exactly
    @Test
    public void testToDoubleWithDefault_nullReturnsDefaultValue() throws Throwable {
        assertEquals(1.1d, NumberUtils.toDouble(null, 1.1d), 0.0001d);
        assertEquals(1.1d, NumberUtils.toDouble("bad", 1.1d), 0.0001d);
        assertEquals(9.9d, NumberUtils.toDouble("9.9", 1.1d), 0.0001d);
    }

    // Covers createNumber null-input branch
    @Test
    public void testCreateNumber_null_returnsNull() throws Throwable {
        assertNull(NumberUtils.createNumber(null));
    }

    // Covers createNumber blank-string branch throwing NumberFormatException
    @Test
    public void testCreateNumber_blankString_throwsNFE() throws Throwable {
        try {
            NumberUtils.createNumber("");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    // Covers createNumber whitespace-only string treated as blank
    @Test
    public void testCreateNumber_whitespaceOnly_throwsNFE() throws Throwable {
        try {
            NumberUtils.createNumber("   ");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    // Covers createNumber "--" prefix protection branch
    @Test
    public void testCreateNumber_doubleMinusPrefix_returnsNull() throws Throwable {
        assertNull(NumberUtils.createNumber("--1"));
    }

    // Covers createNumber hex "0x" branch
    @Test
    public void testCreateNumber_hexPositive_returnsIntegerValue() throws Throwable {
        Number n = NumberUtils.createNumber("0x1A");
        assertTrue(n instanceof Integer);
        assertEquals(26, n.intValue());
    }

    // Covers createNumber "-0x" branch
    @Test
    public void testCreateNumber_hexNegative_returnsIntegerValue() throws Throwable {
        Number n = NumberUtils.createNumber("-0x1A");
        assertTrue(n instanceof Integer);
        assertEquals(-26, n.intValue());
    }

    // Covers createNumber plain integer, no dec/exp, no leading zero
    @Test
    public void testCreateNumber_plainInteger_returnsInteger() throws Throwable {
        Number n = NumberUtils.createNumber("123");
        assertTrue(n instanceof Integer);
        assertEquals(123, n.intValue());
    }

    // Covers createNumber falling back from Integer to Long on overflow
    @Test
    public void testCreateNumber_integerOverflow_returnsLong() throws Throwable {
        Number n = NumberUtils.createNumber("2147483648");
        assertTrue(n instanceof Long);
        assertEquals(2147483648L, n.longValue());
    }

    // Covers createNumber falling back from Long to BigInteger on overflow
    @Test
    public void testCreateNumber_longOverflow_returnsBigInteger() throws Throwable {
        Number n = NumberUtils.createNumber("99999999999999999999");
        assertTrue(n instanceof BigInteger);
        assertEquals(new BigInteger("99999999999999999999"), n);
    }



    // Covers createNumber decimal point without exponent/qualifier branch
    @Test
    public void testCreateNumber_decimalNoQualifier_returnsFloat() throws Throwable {
        Number n = NumberUtils.createNumber("1.5");
        assertTrue(n instanceof Float);
        assertEquals(1.5f, n.floatValue(), 0.0001f);
    }

    // Covers createNumber decimal+exponent branch without type qualifier
    @Test
    public void testCreateNumber_decimalWithExponent_returnsFloat() throws Throwable {
        Number n = NumberUtils.createNumber("1.5e10");
        assertTrue(n instanceof Float);
        assertEquals(1.5e10f, n.floatValue(), 2000.0f);
    }

    // Covers createNumber 'f'/'F' qualifier branch
    @Test
    public void testCreateNumber_floatQualifier_returnsFloat() throws Throwable {
        Number n = NumberUtils.createNumber("1.5f");
        assertTrue(n instanceof Float);
        assertEquals(1.5f, n.floatValue(), 0.0001f);
    }

    // Covers createNumber 'd'/'D' qualifier branch
    @Test
    public void testCreateNumber_doubleQualifier_returnsDouble() throws Throwable {
        Number n = NumberUtils.createNumber("1.5d");
        assertTrue(n instanceof Double);
        assertEquals(1.5d, n.doubleValue(), 0.0001d);
    }

    // Covers createNumber 'L' qualifier branch with valid digits
    @Test
    public void testCreateNumber_longQualifier_returnsLong() throws Throwable {
        Number n = NumberUtils.createNumber("23L");
        assertTrue(n instanceof Long);
        assertEquals(23L, n.longValue());
    }

    // Covers createNumber 'L' qualifier falling back to BigInteger on overflow
    @Test
    public void testCreateNumber_longQualifierOverflow_returnsBigInteger() throws Throwable {
        Number n = NumberUtils.createNumber("99999999999999999999L");
        assertTrue(n instanceof BigInteger);
        assertEquals(new BigInteger("99999999999999999999"), n);
    }

    // Covers createNumber 'L' qualifier rejecting decimal numbers
    @Test
    public void testCreateNumber_longQualifierWithDecimal_throwsNFE() throws Throwable {
        try {
            NumberUtils.createNumber("1.5L");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    // Covers createNumber default-case switch branch for unrecognized trailing char
    @Test
    public void testCreateNumber_trailingDotOnly_throwsNFE() throws Throwable {
        try {
            NumberUtils.createNumber("1.");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    // Covers createFloat null branch and NumberFormatException propagation
    @Test
    public void testCreateFloat_nullAndInvalid() throws Throwable {
        assertNull(NumberUtils.createFloat(null));
        try {
            NumberUtils.createFloat("abc");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    // Covers createDouble null branch and NumberFormatException propagation
    @Test
    public void testCreateDouble_nullAndInvalid() throws Throwable {
        assertNull(NumberUtils.createDouble(null));
        try {
            NumberUtils.createDouble("abc");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    // Covers createInteger null branch, hex decode, and invalid-throw branch
    @Test
    public void testCreateInteger_nullHexAndInvalid() throws Throwable {
        assertNull(NumberUtils.createInteger(null));
        assertEquals(31, NumberUtils.createInteger("0x1F").intValue());
        try {
            NumberUtils.createInteger("abc");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    // Covers createLong valid parse and NumberFormatException branch
    @Test
    public void testCreateLong_validAndInvalid() throws Throwable {
        assertEquals(123L, NumberUtils.createLong("123").longValue());
        try {
            NumberUtils.createLong("abc");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
    }

    // Covers createBigInteger null branch and valid parse
    @Test
    public void testCreateBigInteger_nullAndValid() throws Throwable {
        assertNull(NumberUtils.createBigInteger(null));
        assertEquals(new BigInteger("123"), NumberUtils.createBigInteger("123"));
    }

    // Covers createBigDecimal null branch, blank-throws branch, and valid parse
    @Test
    public void testCreateBigDecimal_nullBlankAndValid() throws Throwable {
        assertNull(NumberUtils.createBigDecimal(null));
        try {
            NumberUtils.createBigDecimal("  ");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // ok
        }
        assertEquals(new BigDecimal("1.1"), NumberUtils.createBigDecimal("1.1"));
    }

    // Covers equals(byte[],byte[]) same-ref, null, length-diff, content-diff and equal branches
    @Test
    public void testEqualsByteArray_trueFalseNullCases() throws Throwable {
        byte[] a = new byte[] {1, 2, 3};
        byte[] b = new byte[] {1, 2, 3};
        byte[] c = new byte[] {1, 2};
        assertTrue(NumberUtils.equals(a, a));
        assertTrue(NumberUtils.equals(a, b));
        assertFalse(NumberUtils.equals(a, c));
        assertFalse(NumberUtils.equals(a, null));
    }

    // Covers equals(float[],float[]) using compare() semantics where NaN == NaN
    @Test
    public void testEqualsFloatArray_NaNConsideredEqual() throws Throwable {
        float[] a = new float[] {Float.NaN, 1.0f};
        float[] b = new float[] {Float.NaN, 1.0f};
        assertTrue(NumberUtils.equals(a, b));
    }

    // Covers min(long[]) null-array branch
    @Test
    public void testMinLongArray_nullThrowsIAE() throws Throwable {
        try {
            NumberUtils.min((long[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // Covers min(int[]) empty-array branch
    @Test
    public void testMinIntArray_emptyThrowsIAE() throws Throwable {
        try {
            NumberUtils.min(new int[0]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // Covers max(double[]) loop over multiple elements
    @Test
    public void testMaxDoubleArray_findsMax() throws Throwable {
        double[] arr = new double[] {3.0, 7.5, -2.0, 7.4};
        assertEquals(7.5d, NumberUtils.max(arr), 0.0001d);
    }

    // Covers min(int,int,int) both if-branches
    @Test
    public void testMin3Int_variousOrder() throws Throwable {
        assertEquals(1, NumberUtils.min(3, 1, 2));
        assertEquals(1, NumberUtils.min(1, 2, 3));
        assertEquals(-5, NumberUtils.min(3, 2, -5));
    }

    // Covers max(double,double,double) NaN propagation via Math.max
    @Test
    public void testMax3Double_withNaNPropagates() throws Throwable {
        double result = NumberUtils.max(1.0d, Double.NaN, 2.0d);
        assertTrue(Double.isNaN(result));
    }

    // Covers compare(double,double) bit-comparison branch for -0.0 vs 0.0 and NaN ordering
    @Test
    public void testCompareDouble_specialOrdering() throws Throwable {
        assertEquals(-1, NumberUtils.compare(-0.0d, 0.0d));
        assertEquals(1, NumberUtils.compare(Double.NaN, Double.POSITIVE_INFINITY));
        assertEquals(0, NumberUtils.compare(Double.NaN, Double.NaN));
    }

    // Covers isDigits null/empty(false) and digit/non-digit branches
    @Test
    public void testIsDigits_variousInputs() throws Throwable {
        assertFalse(NumberUtils.isDigits(null));
        assertFalse(NumberUtils.isDigits(""));
        assertTrue(NumberUtils.isDigits("12345"));
        assertFalse(NumberUtils.isDigits("12a45"));
    }

    // Covers isNumber hex branch and standard decimal branch
    @Test
    public void testIsNumber_hexAndDecimalValid() throws Throwable {
        assertTrue(NumberUtils.isNumber("0x1F"));
        assertTrue(NumberUtils.isNumber("123.45"));
        assertTrue(NumberUtils.isNumber("-123"));
    }

    // Covers isNumber false branches: null/empty, hex too short, two decimal points, trailing E
    @Test
    public void testIsNumber_invalidCases() throws Throwable {
        assertFalse(NumberUtils.isNumber(null));
        assertFalse(NumberUtils.isNumber(""));
        assertFalse(NumberUtils.isNumber("0x"));
        assertFalse(NumberUtils.isNumber("1.2.3"));
        assertFalse(NumberUtils.isNumber("1E"));
    }

    // Covers isNumber qualifier branches: L (no exponent) true, D true, exponent+L invalid false
    @Test
    public void testIsNumber_typeQualifierSuffixes() throws Throwable {
        assertTrue(NumberUtils.isNumber("123L"));
        assertTrue(NumberUtils.isNumber("1.5D"));
        assertFalse(NumberUtils.isNumber("1e5L"));
    }
}
