package com.fasterxml.jackson.databind.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class StdDateFormatTest {

    @Test
    public void testConstructorsAndSingletons() throws Throwable {
        StdDateFormat df1 = new StdDateFormat();
        assertNotNull(df1);

        TimeZone tz = TimeZone.getTimeZone("PST");
        StdDateFormat df2 = new StdDateFormat(tz);
        assertNotNull(df2);

        StdDateFormat df3 = new StdDateFormat(tz, Locale.GERMAN);
        assertNotNull(df3);

        assertNotNull(StdDateFormat.getDefaultTimeZone());
        assertNotNull(StdDateFormat.getBlueprintISO8601Format());
        assertNotNull(StdDateFormat.getBlueprintRFC1123Format());
        assertNotNull(StdDateFormat.getISO8601Format(tz));
        assertNotNull(StdDateFormat.getISO8601Format(tz, Locale.GERMAN));
        assertNotNull(StdDateFormat.getRFC1123Format(tz));
        assertNotNull(StdDateFormat.getRFC1123Format(tz, Locale.GERMAN));
    }

    @Test
    public void testWithersAndClone() throws Throwable {
        StdDateFormat std = new StdDateFormat();
        TimeZone tz = TimeZone.getTimeZone("EST");
        
        StdDateFormat stdWithTz = std.withTimeZone(tz);
        assertNotNull(stdWithTz);
        // Test caching / identical return
        assertSame(stdWithTz, stdWithTz.withTimeZone(tz));
        assertSame(std, std.withTimeZone(null));

        StdDateFormat stdWithLoc = std.withLocale(Locale.FRANCE);
        assertNotNull(stdWithLoc);
        assertSame(stdWithLoc, stdWithLoc.withLocale(Locale.FRANCE));

        StdDateFormat cloned = std.clone();
        assertNotNull(cloned);
    }

    @Test
    public void testSetTimeZone() throws Throwable {
        StdDateFormat std = new StdDateFormat();
        TimeZone tz1 = TimeZone.getTimeZone("EST");
        std.setTimeZone(tz1);
        // setting same timezone should be no-op branch
        std.setTimeZone(tz1);
        
        TimeZone tz2 = TimeZone.getTimeZone("CST");
        std.setTimeZone(tz2);
        assertNotNull(std.toString());
    }

    @Test
    public void testToString() throws Throwable {
        StdDateFormat std = new StdDateFormat();
        String str = std.toString();
        assertTrue(str.contains("StdDateFormat"));

        StdDateFormat stdWithTz = new StdDateFormat(TimeZone.getTimeZone("GMT"), Locale.US);
        assertTrue(stdWithTz.toString().contains("timezone"));
    }

    @Test
    public void testParseValidFormats() throws Throwable {
        StdDateFormat std = new StdDateFormat();

        // ISO-8601 full
        Date d1 = std.parse("2014-10-02T12:30:45.000+0000");
        assertNotNull(d1);

        // ISO-8601 with Z
        Date d2 = std.parse("2014-10-02T12:30:45.000Z");
        assertNotNull(d2);

        // ISO-8601 with Z missing millis (triggers : check)
        Date d2b = std.parse("2014-10-02T12:30:45Z");
        assertNotNull(d2b);

        // ISO-8601 with timezone colon
        Date d3 = std.parse("2014-10-02T12:30:45.000+00:00");
        assertNotNull(d3);

        // ISO-8601 with timezone missing minutes (+00)
        Date d4 = std.parse("2014-10-02T12:30:45.000+00");
        assertNotNull(d4);

        // Plain date (length <= 10, digit)
        Date d5 = std.parse("2014-10-02");
        assertNotNull(d5);

        // Long timestamp (all digits)
        Date d6 = std.parse("1412253045000");
        assertNotNull(d6);

        // Negative long timestamp
        Date d7 = std.parse("-100000");
        assertNotNull(d7);

        // RFC-1123 fallback
        Date d8 = std.parse("Thu, 02 Oct 2014 12:30:45 GMT");
        assertNotNull(d8);
    }

    @Test
    public void testParseAsISO8601EdgeCases() throws Throwable {
        StdDateFormat std = new StdDateFormat();
        ParsePosition pos = new ParsePosition(0);

        // No timezone, timeLen <= 8
        Date d9 = std.parse("2014-10-02T12:30:45", pos);
        assertNotNull(d9);

        // No timezone, very short time
        Date d10 = std.parse("2014-10-02T12", pos);
        assertNotNull(d10);
    }

    @Test(expected = ParseException.class)
    public void testParseInvalidDateThrowsException() throws Throwable {
        StdDateFormat std = new StdDateFormat();
        std.parse("not-a-date");
    }

    @Test
    public void testFormat() throws Throwable {
        StdDateFormat std = new StdDateFormat();
        Date date = new Date(0L);
        StringBuffer sb = new StringBuffer();
        StringBuffer result = std.format(date, sb, new java.text.FieldPosition(0));
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    public void testLocaleCustomization() throws Throwable {
        // Test parsing with non-default locale
        StdDateFormat std = new StdDateFormat(TimeZone.getTimeZone("GMT"), Locale.GERMAN);
        Date d = std.parse("2014-10-02");
        assertNotNull(d);
    }
}