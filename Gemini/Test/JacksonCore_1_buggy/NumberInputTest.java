package com.fasterxml.jackson.core.io;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;

public class NumberInputTest {

    @Test
    public void testConstants() throws Throwable {
        assertEquals("2.2250738585072012e-308", NumberInput.NASTY_SMALL_DOUBLE);
        assertEquals(1000000000L, NumberInput.L_BILLION);
        assertNotNull(NumberInput.MIN_LONG_STR_NO_SIGN);
        assertNotNull(NumberInput.MAX_LONG_STR);
    }

    @Test
    public void testParseIntCharArray() throws Throwable {
        char[] chars1 = "123456789".toCharArray();
        assertEquals(123456789, NumberInput.parseInt(chars1, 0, 9));

        char[] chars2 = "5".toCharArray();
        assertEquals(5, NumberInput.parseInt(chars2, 0, 1));

        char[] chars3 = "01234".toCharArray();
        assertEquals(1234, NumberInput.parseInt(chars3, 1, 4));
    }

    @Test
    public void testParseIntStringValid() throws Throwable {
        assertEquals(123, NumberInput.parseInt("123"));
        assertEquals(-123, NumberInput.parseInt("-123"));
        assertEquals(0, NumberInput.parseInt("0"));
        assertEquals(7, NumberInput.parseInt("+7")); // Note: parseInt string doesn't explicitly strip '+' in the main method unless through other flows, let's test regular positive
        assertEquals(12345678, NumberInput.parseInt("12345678"));
        assertEquals(1234567890, NumberInput.parseInt("1234567890"));
        assertEquals(-1234567890, NumberInput.parseInt("-1234567890"));
    }

    @Test
    public void testParseIntStringFallback() throws Throwable {
        // Length > 9 or invalid chars falling back to Integer.parseInt
        assertEquals(1234567890, NumberInput.parseInt("1234567890"));
        assertEquals(-1, NumberInput.parseInt("-1"));
        
        // Invalid character cases triggering Integer.parseInt or exception
        assertEquals(123, NumberInput.parseInt("123"));
        
        // Force fallback via length checks and non-digit chars
        try {
            NumberInput.parseInt("123a");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // Expected
        }

        try {
            NumberInput.parseInt("-");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // Expected
        }

        try {
            NumberInput.parseInt("12345678901");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // Expected
        }
    }

    @Test
    public void testParseLongCharArray() throws Throwable {
        char[] chars = "123456789012345678".toCharArray();
        long val = NumberInput.parseLong(chars, 0, 18);
        assertEquals(123456789012345678L, val);
    }

    @Test
    public void testParseLongString() throws Throwable {
        assertEquals(123L, NumberInput.parseLong("123"));
        assertEquals(1234567890123L, NumberInput.parseLong("1234567890123"));
    }

    @Test
    public void testInLongRangeCharArray() throws Throwable {
        char[] maxLong = NumberInput.MAX_LONG_STR.toCharArray();
        assertTrue(NumberInput.inLongRange(maxLong, 0, maxLong.length, false));

        // Smaller length
        assertTrue(NumberInput.inLongRange(maxLong, 0, maxLong.length - 1, false));

        // Larger length
        assertFalse(NumberInput.inLongRange(maxLong, 0, maxLong.length + 1, false));

        // Negative range
        char[] minLongNoSign = NumberInput.MIN_LONG_STR_NO_SIGN.toCharArray();
        assertTrue(NumberInput.inLongRange(minLongNoSign, 0, minLongNoSign.length, true));
        assertFalse(NumberInput.inLongRange(minLongNoSign, 0, minLongNoSign.length + 1, true));
    }

    @Test
    public void testInLongRangeString() throws Throwable {
        assertTrue(NumberInput.inLongRange(NumberInput.MAX_LONG_STR, false));
        assertFalse(NumberInput.inLongRange("99999999999999999999", false));
        assertTrue(NumberInput.inLongRange("1", false));

        assertTrue(NumberInput.inLongRange(NumberInput.MIN_LONG_STR_NO_SIGN, true));
        assertFalse(NumberInput.inLongRange("99999999999999999999", true));
        assertTrue(NumberInput.inLongRange("1", true));
    }

    @Test
    public void testParseAsInt() throws Throwable {
        assertEquals(42, NumberInput.parseAsInt(null, 42));
        assertEquals(42, NumberInput.parseAsInt("", 42));
        assertEquals(42, NumberInput.parseAsInt("   ", 42));
        assertEquals(123, NumberInput.parseAsInt("123", 42));
        assertEquals(123, NumberInput.parseAsInt("+123", 42));
        assertEquals(-123, NumberInput.parseAsInt("-123", 42));
        assertEquals(12, NumberInput.parseAsInt("12.3", 42)); // coerce via double
        assertEquals(42, NumberInput.parseAsInt("invalid", 42));
        assertEquals(42, NumberInput.parseAsInt("9999999999999999", 42)); // overflow int -> NumberFormatException -> defaultValue
    }

    @Test
    public void testParseAsLong() throws Throwable {
        assertEquals(42L, NumberInput.parseAsLong(null, 42L));
        assertEquals(42L, NumberInput.parseAsLong("", 42L));
        assertEquals(42L, NumberInput.parseAsLong("   ", 42L));
        assertEquals(123L, NumberInput.parseAsLong("123", 42L));
        assertEquals(123L, NumberInput.parseAsLong("+123", 42L));
        assertEquals(-123L, NumberInput.parseAsLong("-123", 42L));
        assertEquals(12L, NumberInput.parseAsLong("12.3", 42L)); // coerce via double
        assertEquals(42L, NumberInput.parseAsLong("invalid", 42L));
        assertEquals(42L, NumberInput.parseAsLong("9999999999999999999999999", 42L));
    }

    @Test
    public void testParseAsDouble() throws Throwable {
        assertEquals(42.0, NumberInput.parseAsDouble(null, 42.0), 0.001);
        assertEquals(42.0, NumberInput.parseAsDouble("", 42.0), 0.001);
        assertEquals(42.0, NumberInput.parseAsDouble("   ", 42.0), 0.001);
        assertEquals(12.34, NumberInput.parseAsDouble("12.34", 42.0), 0.001);
        assertEquals(42.0, NumberInput.parseAsDouble("invalid", 42.0), 0.001);
    }

    @Test
    public void testParseDouble() throws Throwable {
        assertEquals(Double.MIN_VALUE, NumberInput.parseDouble(NumberInput.NASTY_SMALL_DOUBLE), 0.0);
        assertEquals(123.45, NumberInput.parseDouble("123.45"), 0.001);

        try {
            NumberInput.parseDouble("not-a-double");
            fail("Expected NumberFormatException");
        } catch (NumberFormatException e) {
            // Expected
        }
    }

    @Test
    public void testParseBigDecimal() throws Throwable {
        BigDecimal bdStr = NumberInput.parseBigDecimal("123.456");
        assertNotNull(bdStr);
        assertEquals(new BigDecimal("123.456"), bdStr);

        char[] buffer = "987.654".toCharArray();
        BigDecimal bdBuf = NumberInput.parseBigDecimal(buffer);
        assertEquals(new BigDecimal("987.654"), bdBuf);

        BigDecimal bdBufOffset = NumberInput.parseBigDecimal("XYZ123.45XYZ".toCharArray(), 3, 6);
        assertEquals(new BigDecimal("123.45"), bdBufOffset);
    }
}