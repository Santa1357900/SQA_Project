package com.google.gson.internal.bind.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Date;
import java.util.TimeZone;
import java.util.GregorianCalendar;

public class ISO8601UtilsTest {

    @Test
    public void testFormatDateBasic() throws Throwable {
        Date date = new Date(0L); // 1970-01-01 00:00:00 UTC
        String formatted = ISO8601Utils.format(date);
        assertNotNull(formatted);
        assertTrue(formatted.startsWith("1970-01-01T00:00:00"));
    }

    @Test
    public void testFormatDateWithMillis() throws Throwable {
        Date date = new Date(123L);
        String formatted = ISO8601Utils.format(date, true);
        assertNotNull(formatted);
        assertTrue(formatted.contains(".123"));
    }

    @Test
    public void testFormatDateWithCustomTimeZone() throws Throwable {
        Date date = new Date(0L);
        TimeZone tz = TimeZone.getTimeZone("GMT+02:00");
        String formatted = ISO8601Utils.format(date, false, tz);
        assertNotNull(formatted);
        assertTrue(formatted.endsWith("+02:00"));

        TimeZone tzNegative = TimeZone.getTimeZone("GMT-05:00");
        String formattedNeg = ISO8601Utils.format(date, false, tzNegative);
        assertNotNull(formattedNeg);
        assertTrue(formattedNeg.endsWith("-05:00"));
    }

    @Test
    public void testParseDateSimple() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Date date = ISO8601Utils.parse("1970-01-01Z", pos);
        assertNotNull(date);
        assertEquals(10, pos.getIndex());
    }

    @Test
    public void testParseDateWithoutSeparators() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Date date = ISO8601Utils.parse("19700101Z", pos);
        assertNotNull(date);
    }

    @Test
    public void testParseDateWithTimeAndMillis() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Date date = ISO8601Utils.parse("1970-01-01T00:00:00.123Z", pos);
        assertNotNull(date);
    }

    @Test
    public void testParseDateWithTimeAndTwoDigitMillis() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Date date = ISO8601Utils.parse("1970-01-01T00:00:00.12Z", pos);
        assertNotNull(date);
    }

    @Test
    public void testParseDateWithTimeAndOneDigitMillis() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Date date = ISO8601Utils.parse("1970-01-01T00:00:00.1Z", pos);
        assertNotNull(date);
    }

    @Test
    public void testParseDateWithTimezoneOffsetPlus() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Date date = ISO8601Utils.parse("1970-01-01T00:00:00+02:00", pos);
        assertNotNull(date);
    }

    @Test
    public void testParseDateWithTimezoneOffsetPlusZero() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Date date = ISO8601Utils.parse("1970-01-01T00:00:00+00:00", pos);
        assertNotNull(date);
    }

    @Test
    public void testParseDateWithTimezoneOffsetMinus() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Date date = ISO8601Utils.parse("1970-01-01T00:00:00-05:00", pos);
        assertNotNull(date);
    }

    @Test
    public void testParseDateLeapSecond() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Date date = ISO8601Utils.parse("1970-01-01T00:00:60Z", pos);
        assertNotNull(date);
    }

    @Test
    public void testParseInvalidNumberFormatThrowsParseException() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        try {
            ISO8601Utils.parse("1970-XX-01Z", pos);
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testParseMissingTimeZoneThrowsParseException() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        try {
            ISO8601Utils.parse("1970-01-01T00:00:00", pos);
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testParseInvalidTimeZoneIndicatorThrowsParseException() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        try {
            ISO8601Utils.parse("1970-01-01T00:00:00X", pos);
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testParseNullDateThrowsParseException() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        try {
            ISO8601Utils.parse(null, pos);
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testParseIndexOutOfBounds() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        try {
            ISO8601Utils.parse("19", pos);
            fail("Expected ParseException");
        } catch (ParseException e) {
            assertNotNull(e);
        }
    }
}