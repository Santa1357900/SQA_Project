package com.fasterxml.jackson.databind.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.SimpleTimeZone;

public class StdDateFormatTest {

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        assertNotNull(sdf);
        assertEquals(Locale.US, sdf._locale);

        TimeZone tz = TimeZone.getTimeZone("GMT+2");
        StdDateFormat sdf2 = new StdDateFormat(tz, Locale.GERMANY);
        assertEquals(tz, sdf2.getTimeZone());
        assertEquals(Locale.GERMANY, sdf2._locale);
    }

    @Test
    public void testDefaultTimeZoneAndLocale() throws Throwable {
        TimeZone defaultTz = StdDateFormat.getDefaultTimeZone();
        assertNotNull(defaultTz);
        assertEquals("UTC", defaultTz.getID());
    }

    @Test
    public void testWithTimeZone() throws Throwable {
        StdDateFormat sdf = StdDateFormat.instance;
        TimeZone tz = TimeZone.getTimeZone("PST");
        StdDateFormat sdf2 = sdf.withTimeZone(tz);
        assertNotSame(sdf, sdf2);
        assertEquals(tz, sdf2.getTimeZone());

        // Same timezone should return same instance
        StdDateFormat sdf3 = sdf2.withTimeZone(tz);
        assertSame(sdf2, sdf3);

        // Null timezone should default to UTC
        StdDateFormat sdf4 = sdf.withTimeZone(null);
        assertEquals(StdDateFormat.getDefaultTimeZone(), sdf4.getTimeZone());
    }

    @Test
    public void testWithLocale() throws Throwable {
        StdDateFormat sdf = StdDateFormat.instance;
        StdDateFormat sdf2 = sdf.withLocale(Locale.FRANCE);
        assertNotSame(sdf, sdf2);
        assertEquals(Locale.FRANCE, sdf2._locale);

        // Same locale should return same instance
        StdDateFormat sdf3 = sdf2.withLocale(Locale.FRANCE);
        assertSame(sdf2, sdf3);
    }

    @Test
    public void testClone() throws Throwable {
        StdDateFormat sdf = new StdDateFormat(TimeZone.getTimeZone("EST"), Locale.CANADA, Boolean.FALSE);
        StdDateFormat cloned = sdf.clone();
        assertNotNull(cloned);
        assertNotSame(sdf, cloned);
        assertEquals(sdf.getTimeZone(), cloned.getTimeZone());
        assertEquals(sdf.isLenient(), cloned.isLenient());
    }

    @Test
    public void testGetISO8601FormatAndRFC1123Format() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat iso = StdDateFormat.getISO8601Format(tz, Locale.US);
        assertNotNull(iso);

        DateFormat isoOld = StdDateFormat.getISO8601Format(tz);
        assertNotNull(isoOld);

        DateFormat rfc = StdDateFormat.getRFC1123Format(tz, Locale.US);
        assertNotNull(rfc);

        DateFormat rfcOld = StdDateFormat.getRFC1123Format(tz);
        assertNotNull(rfcOld);
    }

    @Test
    public void testSetTimeZone() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        TimeZone tz1 = TimeZone.getTimeZone("GMT+1");
        TimeZone tz2 = TimeZone.getTimeZone("GMT+2");

        sdf.setTimeZone(tz1);
        assertEquals(tz1, sdf.getTimeZone());

        // Setting same timezone should not clear formats or change anything unnecessarily
        sdf.setTimeZone(tz1);
        assertEquals(tz1, sdf.getTimeZone());

        sdf.setTimeZone(tz2);
        assertEquals(tz2, sdf.getTimeZone());
    }

    @Test
    public void testLenient() throws Throwable {
        StdDateFormat sdf = new StdDateFormat();
        assertTrue(sdf.isLenient());

        sdf.setLenient(false);
        assertFalse(sdf.isLenient());

        // Setting same leniency should be a no-op
        sdf.setLenient(false);
        assertFalse(sdf.isLenient());

        sdf.setLenient(true);
        assertTrue(sdf.isLenient());
    }

    @Test
    public void testParseISO8601Basic() throws Throwable {
        StdDateFormat sdf = StdDateFormat.instance;
        Date date = sdf.parse("2020-01-01T00:00:00.000Z");
        assertNotNull(date);

        // Test with various timezone offsets and lengths
        Date date2 = sdf.parse("2020-01-01T00:00:00+0000");
        assertNotNull(date2);

        Date date3 = sdf.parse("2020-01-01T00:00+00:00");
        assertNotNull(date3);

        Date date4 = sdf.parse("2020-01-01T00+01");
        assertNotNull(date4);

        Date date5 = sdf.parse("2020-01-01T00:00:00");
        assertNotNull(date5);

        Date date6 = sdf.parse("2020-01-01");
        assertNotNull(date6);
        
        Date date7 = sdf.parse("2020-01-01T00:00:00.123+02");
        assertNotNull(date7);

        Date date8 = sdf.parse("2020-01-01T00:00:00.123-0200");
        assertNotNull(date8);

        Date date9 = sdf.parse("2020-01-01T00:00:00.123-02:00");
        assertNotNull(date9);
    }

    @Test
    public void testParseTimestamp() throws Throwable {
        StdDateFormat sdf = StdDateFormat.instance;
        long currentTime = 1577836800000L;
        Date date = sdf.parse(String.valueOf(currentTime));
        assertNotNull(date);
        assertEquals(currentTime, date.getTime());

        // Negative timestamp
        Date negDate = sdf.parse("-1000");
        assertNotNull(negDate);
        assertEquals(-1000L, negDate.getTime());
    }

    @Test
    public void testParseRFC1123() throws Throwable {
        StdDateFormat sdf = StdDateFormat.instance;
        // Wed, 01 Jan 2020 00:00:00 GMT
        Date date = sdf.parse("Wed, 01 Jan 2020 00:00:00 GMT");
        assertNotNull(date);
    }

    @Test(expected = ParseException.class)
    public void testParseInvalidFormat() throws Throwable {
        StdDateFormat sdf = StdDateFormat.instance;
        sdf.parse("NotADateStringAtAll");
    }

    @Test
    public void testParseWithParsePosition() throws Throwable {
        StdDateFormat sdf = StdDateFormat.instance;
        ParsePosition pos = new ParsePosition(0);
        Date date = sdf.parse("2020-01-01T00:00:00.000Z", pos);
        assertNotNull(date);
        assertEquals(24, pos.getIndex());

        // Parse timestamp with ParsePosition
        ParsePosition pos2 = new ParsePosition(0);
        Date date2 = sdf.parse("1577836800000", pos2);
        assertNotNull(date2);

        // Parse RFC1123 with ParsePosition
        ParsePosition pos3 = new ParsePosition(0);
        Date date3 = sdf.parse("Wed, 01 Jan 2020 00:00:00 GMT", pos3);
        assertNotNull(date3);

        // Parse invalid ISO-like string that falls through or returns null under ParsePosition
        ParsePosition pos4 = new ParsePosition(0);
        Date date4 = sdf.parse("2020-00-00T00:00:00.000Z", pos4);
        // Might be null or handled depending on strictness, but we exercise the code path
    }

    @Test
    public void testFormat() throws Throwable {
        StdDateFormat sdf = StdDateFormat.instance;
        Date date = new Date(1577836800000L);
        StringBuffer sb = new StringBuffer();
        FieldPosition fp = new FieldPosition(0);
        StringBuffer result = sdf.format(date, sb, fp);
        assertNotNull(result);
        assertTrue(result.toString().contains("2020-01-01"));
    }

    @Test
    public void testToStringAndEqualsHashCode() throws Throwable {
        StdDateFormat sdf = StdDateFormat.instance;
        String str = sdf.toString();
        assertNotNull(str);
        assertTrue(str.contains("StdDateFormat"));

        assertTrue(sdf.equals(sdf));
        assertFalse(sdf.equals("some string"));

        assertEquals(System.identityHashCode(sdf), sdf.hashCode());
    }

    @Test
    public void testLooksLikeISO8601EdgeCases() throws Throwable {
        StdDateFormat sdf = new StdDateFormat() {
            public boolean exposeLooksLikeISO8601(String s) {
                return looksLikeISO8601(s);
            }
        };
        StdDateFormat exposed = sdf;
        // Direct checks via parsing behavior or subclassing if needed
        assertTrue(exposed.exposeLooksLikeISO8601("2020-01-01"));
        assertFalse(exposed.exposeLooksLikeISO8601("20"));
        assertFalse(exposed.exposeLooksLikeISO8601("Abc-01-01"));
        assertFalse(exposed.exposeLooksLikeISO8601("2020A01-01"));
        assertFalse(exposed.exposeLooksLikeISO8601("2020/01/01"));
    }

    @Test
    public void testParseAsISO8601VariousLengths() throws Throwable {
        StdDateFormat sdf = StdDateFormat.instance;
        // Testing timeLen switch branches in parseAsISO8601
        assertNotNull(sdf.parse("2020-01-01T00Z"));
        assertNotNull(sdf.parse("2020-01-01T00:00Z"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00Z"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00.1Z"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00.12Z"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00.123Z"));
        
        // Without Z but with timezone offset variants
        assertNotNull(sdf.parse("2020-01-01T00+00"));
        assertNotNull(sdf.parse("2020-01-01T00:00+00"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00+00"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00.1+00"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00.12+00"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00.123+00"));

        // Without timezone at all
        assertNotNull(sdf.parse("2020-01-01T00"));
        assertNotNull(sdf.parse("2020-01-01T00:00"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00.1"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00.12"));
        assertNotNull(sdf.parse("2020-01-01T00:00:00.123"));
    }
}