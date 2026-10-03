package com.fasterxml.jackson.databind.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Calendar;

public class StdDateFormatTest {

    @Test
    public void testConstructorsAndDefaults() throws Throwable {
        StdDateFormat df = new StdDateFormat();
        assertNotNull(df.getTimeZone() == null ? StdDateFormat.getDefaultTimeZone() : df.getTimeZone());
        assertNotNull(df.toString());
        assertNotNull(df.toPattern());
        assertTrue(df.isLenient());
        assertFalse(df.isColonIncludedInTimeZone());
    }

    @Test
    public void testMutantFactories() throws Throwable {
        StdDateFormat df = new StdDateFormat();
        TimeZone tz = TimeZone.getTimeZone("GMT+2");
        Locale loc = Locale.GERMANY;

        StdDateFormat df1 = df.withTimeZone(tz);
        assertNotNull(df1);
        assertEquals(tz, df1.getTimeZone());

        // Same timezone branch
        assertSame(df1, df1.withTimeZone(tz));

        // Null timezone branch
        assertEquals(StdDateFormat.getDefaultTimeZone(), df.withTimeZone(null).getTimeZone());

        StdDateFormat df2 = df.withLocale(loc);
        assertNotNull(df2);
        assertSame(df2, df2.withLocale(loc));

        StdDateFormat df3 = df.withLenient(Boolean.FALSE);
        assertNotNull(df3);
        assertFalse(df3.isLenient());
        assertSame(df3, df3.withLenient(Boolean.FALSE));

        StdDateFormat df4 = df.withColonInTimeZone(true);
        assertNotNull(df4);
        assertTrue(df4.isColonIncludedInTimeZone());
        assertSame(df4, df4.withColonInTimeZone(true));

        StdDateFormat cloned = df.clone();
        assertNotNull(cloned);
    }

    @Test
    public void testSettersAndGetters() throws Throwable {
        StdDateFormat df = new StdDateFormat();
        TimeZone tz = TimeZone.getTimeZone("PST");
        df.setTimeZone(tz);
        assertEquals(tz, df.getTimeZone());

        // Same timezone, should do nothing
        df.setTimeZone(tz);
        assertEquals(tz, df.getTimeZone());

        df.setLenient(false);
        assertFalse(df.isLenient());
        // Same lenience
        df.setLenient(false);
        assertFalse(df.isLenient());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        StdDateFormat df = new StdDateFormat();
        assertTrue(df.equals(df));
        assertFalse(df.equals(null));
        assertFalse(df.equals(new Object()));
        assertEquals(System.identityHashCode(df), df.hashCode());
    }

    @Test
    public void testParseValidPlainDate() throws Throwable {
        Date date = StdDateFormat.instance.parse("2020-05-15");
        assertNotNull(date);
    }

    @Test
    public void testParseValidISO8601() throws Throwable {
        Date date1 = StdDateFormat.instance.parse("2020-05-15T12:30:45.123Z");
        assertNotNull(date1);

        Date date2 = StdDateFormat.instance.parse("2020-05-15T12:30:45.12Z");
        assertNotNull(date2);

        Date date3 = StdDateFormat.instance.parse("2020-05-15T12:30:45.1Z");
        assertNotNull(date3);

        Date date4 = StdDateFormat.instance.parse("2020-05-15T12:30:45");
        assertNotNull(date4);

        Date date5 = StdDateFormat.instance.parse("2020-05-15T12:30:45+02:00");
        assertNotNull(date5);

        Date date6 = StdDateFormat.instance.parse("2020-05-15T12:30:45-0200");
        assertNotNull(date6);
    }

    @Test
    public void testParseLongTimestamp() throws Throwable {
        Date date = StdDateFormat.instance.parse("1589541045000");
        assertNotNull(date);

        Date negativeDate = StdDateFormat.instance.parse("-1000");
        assertNotNull(negativeDate);
    }

    @Test(expected = ParseException.class)
    public void testParseInvalidLongTimestamp() throws Throwable {
        StdDateFormat.instance.parse("99999999999999999999999");
    }

    @Test
    public void testParseRFC1123() throws Throwable {
        Date date = StdDateFormat.instance.parse("Fri, 15 May 2020 12:30:45 GMT");
        assertNotNull(date);
    }

    @Test(expected = ParseException.class)
    public void testParseInvalidDateThrowsException() throws Throwable {
        StdDateFormat.instance.parse("invalid-date-string");
    }

    @Test
    public void testParseWithParsePosition() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Date date = StdDateFormat.instance.parse("2020-05-15", pos);
        assertNotNull(date);

        ParsePosition badPos = new ParsePosition(0);
        Date badDate = StdDateFormat.instance.parse("not-a-date", badPos);
        assertNull(badDate);
    }

    @Test(expected = ParseException.class)
    public void testParseISO8601TooManyFractionDigits() throws Throwable {
        StdDateFormat.instance.parse("2020-05-15T12:30:45.1234567890Z");
    }

    @Test
    public void testFormatDates() throws Throwable {
        Date date = new Date(0L); // 1970-01-01T00:00:00.000+0000

        StdDateFormat dfDefault = new StdDateFormat();
        String formatted1 = dfDefault.format(date);
        assertNotNull(formatted1);

        StdDateFormat dfWithColon = dfDefault.withColonInTimeZone(true);
        String formatted2 = dfWithColon.format(date);
        assertNotNull(formatted2);

        StdDateFormat dfWithTz = dfDefault.withTimeZone(TimeZone.getTimeZone("GMT+02:00"));
        String formatted3 = dfWithTz.format(date);
        assertNotNull(formatted3);
    }

    @Test
    public void testDeprecatedFormats() throws Throwable {
        assertNotNull(StdDateFormat.getISO8601Format(TimeZone.getDefault(), Locale.US));
        assertNotNull(StdDateFormat.getRFC1123Format(TimeZone.getDefault(), Locale.US));
    }
}