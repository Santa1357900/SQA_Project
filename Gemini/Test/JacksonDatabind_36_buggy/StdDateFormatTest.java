package com.fasterxml.jackson.databind.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class StdDateFormatTest {

    @Test
    public void testConstantsAndGetters() throws Throwable {
        assertNotNull(StdDateFormat.getDefaultTimeZone());
        assertNotNull(StdDateFormat.instance);

        StdDateFormat sdf = new StdDateFormat();
        assertEquals(Locale.US, sdf._locale);
        assertTrue(sdf.isLenient());
        assertNull(sdf.getTimeZone());
        assertNotNull(sdf.toString());
    }

    @Test
    public void testWithTimeZone() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        TimeZone tz = TimeZone.getTimeZone("GMT+2");
        StdDateFormat sdf2 = sdf.withTimeZone(tz);
        assertNotSame(sdf, sdf2);
        assertEquals(tz, sdf2.getTimeZone());

        // Same timezone should return same instance
        StdDateFormat sdf3 = sdf2.withTimeZone(tz);
        assertSame(sdf2, sdf3);

        // Null timezone defaults to UTC
        StdDateFormat sdf4 = sdf.withTimeZone(null);
        assertEquals(StdDateFormat.getDefaultTimeZone(), sdf4.getTimeZone());
    }

    @Test
    public void testWithLocale() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        StdDateFormat sdf2 = sdf.withLocale(Locale.GERMANY);
        assertNotSame(sdf, sdf2);

        // Same locale should return same instance
        StdDateFormat sdf3 = sdf2.withLocale(Locale.GERMANY);
        assertSame(sdf2, sdf3);
    }

    @Test
    public void testClone() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        StdDateFormat clone = sdf.clone();
        assertNotNull(clone);
        assertNotSame(sdf, clone);
    }

    @Test
    public void testSetTimeZoneClearsFormats() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        // Initialize internal formats
        sdf.format(new Date());
        
        TimeZone tz = TimeZone.getTimeZone("EST");
        sdf.setTimeZone(tz);
        assertEquals(tz, sdf.getTimeZone());
    }

    @Test
    public void testGetISO8601FormatAndRFC1123Static() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df1 = StdDateFormat.getISO8601Format(tz);
        assertNotNull(df1);

        DateFormat df2 = StdDateFormat.getISO8601Format(tz, Locale.US);
        assertNotNull(df2);

        DateFormat df3 = StdDateFormat.getRFC1123Format(tz);
        assertNotNull(df3);

        DateFormat df4 = StdDateFormat.getRFC1123Format(tz, Locale.US);
        assertNotNull(df4);
    }

    @Test
    public void testParseISO8601Variants() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();

        // Plain date (len <= 10)
        Date d1 = sdf.parse("2020-01-01");
        assertNotNull(d1);

        // Zulu format with colon in millis or standard Z
        Date d2 = sdf.parse("2020-01-01T12:00:00.000Z");
        assertNotNull(d2);

        Date d3 = sdf.parse("2020-01-01T12:00:00Z");
        assertNotNull(d3);

        // With timezone offset +02:00
        Date d4 = sdf.parse("2020-01-01T12:00:00.000+02:00");
        assertNotNull(d4);

        // With timezone offset +0200
        Date d5 = sdf.parse("2020-01-01T12:00:00.000+0200");
        assertNotNull(d5);

        // With timezone offset +02 (missing minutes)
        Date d6 = sdf.parse("2020-01-01T12:00:00.000+02");
        assertNotNull(d6);

        // Missing seconds / partial timeLengths
        Date d7 = sdf.parse("2020-01-01T12");
        assertNotNull(d7);

        Date d8 = sdf.parse("2020-01-01T12:30");
        assertNotNull(d8);

        Date d9 = sdf.parse("2020-01-01T12:30:45");
        assertNotNull(d9);

        // No timezone, but looks like ISO
        Date d10 = sdf.parse("2020-01-01T12:30:45");
        assertNotNull(d10);
    }

    @Test
    public void testParseTimestamp() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        // Positive timestamp
        Date d1 = sdf.parse("1577836800000");
        assertNotNull(d1);

        // Negative timestamp
        Date d2 = sdf.parse("-1577836800000");
        assertNotNull(d2);
    }

    @Test
    public void testParseRFC1123Fallback() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        // RFC 1123 string
        Date d = sdf.parse("Wed, 01 Jan 2020 00:00:00 GMT");
        assertNotNull(d);
    }

    @Test(expected = ParseException.class)
    public void testParseInvalidThrowsParseException() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        sdf.parse("NotAValidDateString123456789");
    }

    @Test
    public void testParseWithParsePosition() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        ParsePosition pos = new ParsePosition(0);
        Date d = sdf.parse("2020-01-01T12:00:00.000Z", pos);
        assertNotNull(d);

        // Invalid format returning null via ParsePosition parse overload
        ParsePosition pos2 = new ParsePosition(0);
        Date d2 = sdf.parse("InvalidDateXYZ", pos2);
        assertNull(d2);
    }

    @Test
    public void testFormat() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        StringBuffer sb = new StringBuffer();
        StringBuffer result = sdf.format(new Date(0L), sb, new java.text.FieldPosition(0));
        assertNotNull(result);
    }

    @Test
    public void testDeprecatedConstructorsAndMethods() throws Throwable {
        StdDateFormat sdf = new StdDateFormat(TimeZone.getDefault(), Locale.US);
        assertNotNull(sdf);

        DateFormat isoLegacy = StdDateFormat.getISO8601Format(TimeZone.getDefault());
        assertNotNull(isoLegacy);

        DateFormat rfcLegacy = StdDateFormat.getRFC1123Format(TimeZone.getDefault());
        assertNotNull(rfcLegacy);
    }
}