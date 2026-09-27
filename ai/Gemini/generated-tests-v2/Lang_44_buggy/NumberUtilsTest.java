package org.apache.commons.lang;

import junit.framework.TestCase;
import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberUtilsTest extends TestCase {

    public NumberUtilsTest(String name) {
        super(name);
    }

    public void testConstructor() throws Throwable {
        NumberUtils utils = new NumberUtils();
        assertNotNull(utils);
    }

    public void testStringToInt() throws Throwable {
        assertEquals(123, NumberUtils.stringToInt("123"));
        assertEquals(0, NumberUtils.stringToInt(null));
        assertEquals(0, NumberUtils.stringToInt("abc"));
        assertEquals(456, NumberUtils.stringToInt("123", 456));
        assertEquals(456, NumberUtils.stringToInt(null, 456));
        assertEquals(456, NumberUtils.stringToInt("abc", 456));
    }

    public void testCreateNumber() throws Throwable {
        assertNull(NumberUtils.createNumber(null));
        
        try {
            NumberUtils.createNumber("");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(e.getMessage().contains("not a valid number"));
        }

        assertNull(NumberUtils.createNumber("--5"));
        
        assertEquals(Integer.valueOf(10), NumberUtils.createNumber("0xA"));
        assertEquals(Integer.valueOf(-10), NumberUtils.createNumber("-0xA"));

        // Test with type qualifiers
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123L"));
        assertEquals(Long.valueOf(123L), NumberUtils.createNumber("123l"));
        
        try {
            NumberUtils.createNumber("123.45L");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(e.getMessage().contains("not a valid number"));
        }

        assertEquals(Float.valueOf(12.3f), NumberUtils.createNumber("12.3f"));
        assertEquals(Float.valueOf(12.3f), NumberUtils.createNumber("12.3F"));
        assertEquals(Double.valueOf(12.3d), NumberUtils.createNumber("12.3d"));
        assertEquals(Double.valueOf(12.3d), NumberUtils.createNumber("12.3D"));

        // Scientific notation with exponent and decimal
        assertNotNull(NumberUtils.createNumber("1.23e2"));
        assertNotNull(NumberUtils.createNumber("1.23e+2"));
        assertNotNull(NumberUtils.createNumber("1.23e-2"));
        assertNotNull(NumberUtils.createNumber("1.23E2"));

        // General fallback cases
        assertEquals(Integer.valueOf(123), NumberUtils.createNumber("123"));
        assertEquals(Long.valueOf(2147483648L), NumberUtils.createNumber("2147483648"));
        assertNotNull(NumberUtils.createNumber("123.45"));
        
        // Invalid numbers with exponents before decimal
        try {
            NumberUtils.createNumber("1e2.3");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(true);
        }

        // Invalid last char
        try {
            NumberUtils.createNumber("123X");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            assertTrue(true);
        }

        // Float/Double invalid or infinite checks forcing big decimal/double
        assertNotNull(NumberUtils.createNumber("1.1e300"));
        assertNotNull(NumberUtils.createNumber("1.1e300f"));
        assertNotNull(NumberUtils.createNumber("1.1e300d"));
    }

    public void testCreateFloat() throws Throwable {
        assertEquals(Float.valueOf(12.3f), NumberUtils.createFloat("12.3"));
    }

    public void testCreateDouble() throws Throwable {
        assertEquals(Double.valueOf(12.3d), NumberUtils.createDouble("12.3"));
    }

    public void testCreateInteger() throws Throwable {
        assertEquals(Integer.valueOf(123), NumberUtils.createInteger("123"));
        assertEquals(Integer.valueOf(10), NumberUtils.createInteger("0xA"));
        assertEquals(Integer.valueOf(511), NumberUtils.createInteger("0777"));
    }

    public void testCreateLong() throws Throwable {
        assertEquals(Long.valueOf(123L), NumberUtils.createLong("123"));
    }

    public void testCreateBigInteger() throws Throwable {
        assertEquals(new BigInteger("123"), NumberUtils.createBigInteger("123"));
    }

    public void testCreateBigDecimal() throws Throwable {
        assertEquals(new BigDecimal("123.45"), NumberUtils.createBigDecimal("123.45"));
    }

    public void testMinimumLong() throws Throwable {
        assertEquals(1L, NumberUtils.minimum(3L, 2L, 1L));
        assertEquals(1L, NumberUtils.minimum(1L, 3L, 2L));
        assertEquals(1L, NumberUtils.minimum(2L, 1L, 3L));
        assertEquals(1L, NumberUtils.minimum(1L, 1L, 1L));
    }

    public void testMinimumInt() throws Throwable {
        assertEquals(1, NumberUtils.minimum(3, 2, 1));
        assertEquals(1, NumberUtils.minimum(1, 3, 2));
        assertEquals(1, NumberUtils.minimum(2, 1, 3));
        assertEquals(1, NumberUtils.minimum(1, 1, 1));
    }

    public void testMaximumLong() throws Throwable {
        assertEquals(3L, NumberUtils.maximum(1L, 2L, 3L));
        assertEquals(3L, NumberUtils.maximum(3L, 1L, 2L));
        assertEquals(3L, NumberUtils.maximum(2L, 3L, 1L));
        assertEquals(3L, NumberUtils.maximum(3L, 3L, 3L));
    }

    public void testMaximumInt() throws Throwable {
        assertEquals(3, NumberUtils.maximum(1, 2, 3));
        assertEquals(3, NumberUtils.maximum(3, 1, 2));
        assertEquals(3, NumberUtils.maximum(2, 3, 1));
        assertEquals(3, NumberUtils.maximum(3, 3, 3));
    }

    public void testCompareDouble() throws Throwable {
        assertEquals(-1, NumberUtils.compare(1.0, 2.0));
        assertEquals(1, NumberUtils.compare(2.0, 1.0));
        assertEquals(0, NumberUtils.compare(1.0, 1.0));
        
        // Zero and negative zero
        assertEquals(-1, NumberUtils.compare(-0.0, 0.0));
        assertEquals(1, NumberUtils.compare(0.0, -0.0));
        assertEquals(0, NumberUtils.compare(0.0, 0.0));
        assertEquals(0, NumberUtils.compare(-0.0, -0.0));

        // NaN comparisons
        assertEquals(0, NumberUtils.compare(Double.NaN, Double.NaN));
        assertEquals(1, NumberUtils.compare(Double.NaN, 1.0));
        assertEquals(-1, NumberUtils.compare(1.0, Double.NaN));
        
        // Exotic bits comparison
        double posInf = Double.POSITIVE_INFINITY;
        double maxVal = Double.MAX_VALUE;
        double minVal = -Double.MAX_VALUE;
        double negInf = Double.NEGATIVE_INFINITY;
        
        assertEquals(-1, NumberUtils.compare(minVal, maxVal));
        assertEquals(1, NumberUtils.compare(maxVal, minVal));
        assertEquals(-1, NumberUtils.compare(negInf, minVal));
        assertEquals(1, NumberUtils.compare(posInf, maxVal));
    }

    public void testCompareFloat() throws Throwable {
        assertEquals(-1, NumberUtils.compare(1.0f, 2.0f));
        assertEquals(1, NumberUtils.compare(2.0f, 1.0f));
        assertEquals(0, NumberUtils.compare(1.0f, 1.0f));
        
        // Zero and negative zero
        assertEquals(-1, NumberUtils.compare(-0.0f, 0.0f));
        assertEquals(1, NumberUtils.compare(0.0f, -0.0f));
        assertEquals(0, NumberUtils.compare(0.0f, 0.0f));
        assertEquals(0, NumberUtils.compare(-0.0f, -0.0f));

        // NaN comparisons
        assertEquals(0, NumberUtils.compare(Float.NaN, Float.NaN));
        assertEquals(1, NumberUtils.compare(Float.NaN, 1.0f));
        assertEquals(-1, NumberUtils.compare(1.0f, Float.NaN));

        // Exotic bits comparison
        float posInf = Float.POSITIVE_INFINITY;
        float maxVal = Float.MAX_VALUE;
        float minVal = -Float.MAX_VALUE;
        float negInf = Float.NEGATIVE_INFINITY;
        
        assertEquals(-1, NumberUtils.compare(minVal, maxVal));
        assertEquals(1, NumberUtils.compare(maxVal, minVal));
        assertEquals(-1, NumberUtils.compare(negInf, minVal));
        assertEquals(1, NumberUtils.compare(posInf, maxVal));
    }

    public void testIsDigits() throws Throwable {
        assertFalse(NumberUtils.isDigits(null));
        assertFalse(NumberUtils.isDigits(""));
        assertTrue(NumberUtils.isDigits("12345"));
        assertFalse(NumberUtils.isDigits("123a45"));
        assertFalse(NumberUtils.isDigits("123.45"));
        assertFalse(NumberUtils.isDigits("-123"));
    }

    public void testIsNumber() throws Throwable {
        assertFalse(NumberUtils.isNumber(null));
        assertFalse(NumberUtils.isNumber(""));
        
        assertTrue(NumberUtils.isNumber("123"));
        assertTrue(NumberUtils.isNumber("-123"));
        assertTrue(NumberUtils.isNumber("123.45"));
        assertTrue(NumberUtils.isNumber("-123.45"));
        assertTrue(NumberUtils.isNumber("1e10"));
        assertTrue(NumberUtils.isNumber("1E10"));
        assertTrue(NumberUtils.isNumber("1e+10"));
        assertTrue(NumberUtils.isNumber("1e-10"));
        assertTrue(NumberUtils.isNumber("0xA"));
        assertTrue(NumberUtils.isNumber("-0xA"));
        assertTrue(NumberUtils.isNumber("123L"));
        assertTrue(NumberUtils.isNumber("123f"));
        assertTrue(NumberUtils.isNumber("123d"));

        // Invalid numbers
        assertFalse(NumberUtils.isNumber("0x"));
        assertFalse(NumberUtils.isNumber("0xG"));
        assertFalse(NumberUtils.isNumber("123.45.67"));
        assertFalse(NumberUtils.isNumber("1e2e3"));
        assertFalse(NumberUtils.isNumber("e10"));
        assertFalse(NumberUtils.isNumber("1e"));
        assertFalse(NumberUtils.isNumber("1e+"));
        assertFalse(NumberUtils.isNumber("."));
        assertFalse(NumberUtils.isNumber("123l")); // 'l' with exponent is checked or strict
        assertFalse(NumberUtils.isNumber("1e2l"));
        assertFalse(NumberUtils.isNumber("123xyz"));
        assertFalse(NumberUtils.isNumber("-"));
        assertFalse(NumberUtils.isNumber("123.45E"));
        assertFalse(NumberUtils.isNumber("123.45E-"));
    }
}