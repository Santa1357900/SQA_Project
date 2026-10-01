package com.google.gson.internal.bind.util;

import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.Test;
import static org.junit.Assert.*;

public class ISO8601UtilsClaudeTest {

    private static Date utcDate(int year, int month, int day, int hour, int minute, int second, int millis) {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(year, month - 1, day, hour, minute, second);
        cal.set(Calendar.MILLISECOND, millis);
        return cal.getTime();
    }

    private static Date tzDate(TimeZone tz, int year, int month, int day, int hour, int minute, int second) {
        Calendar cal = new GregorianCalendar(tz);
        cal.clear();
        cal.set(year, month - 1, day, hour, minute, second);
        return cal.getTime();
    }

    private static Date localDate(int year, int month, int day) {
        return new GregorianCalendar(year, month - 1, day).getTime();
    }

    // format(Date): default UTC, no millis, offset==0 branch appends 'Z'
    @Test
    public void testFormat_date_defaultUTC_noMillis() throws Throwable {
        Date date = utcDate(1970, 1, 1, 0, 0, 0, 0);
        assertEquals("1970-01-01T00:00:00Z", ISO8601Utils.format(date));
    }

    // format(Date,boolean millis): millis included, 3-digit no pad needed
    @Test
    public void testFormat_date_withMillis_includesMillisAndZ() throws Throwable {
        Date date = utcDate(1970, 1, 1, 0, 0, 0, 123);
        assertEquals("1970-01-01T00:00:00.123Z", ISO8601Utils.format(date, true));
    }

    // format(Date,boolean millis): padInt pads milliseconds to 3 digits
    @Test
    public void testFormat_date_millisPadding_singleDigit() throws Throwable {
        Date date = utcDate(1970, 1, 1, 0, 0, 0, 7);
        assertEquals("1970-01-01T00:00:00.007Z", ISO8601Utils.format(date, true));
    }

    // format(Date,boolean,TimeZone): positive offset branch
    @Test
    public void testFormat_date_tz_positiveOffset() throws Throwable {
        Date date = new Date(0L);
        TimeZone tz = TimeZone.getTimeZone("GMT+02:00");
        assertEquals("1970-01-01T02:00:00+02:00", ISO8601Utils.format(date, false, tz));
    }

    // format(Date,boolean,TimeZone): negative offset branch
    @Test
    public void testFormat_date_tz_negativeOffset() throws Throwable {
        Date date = new Date(0L);
        TimeZone tz = TimeZone.getTimeZone("GMT-05:00");
        assertEquals("1969-12-31T19:00:00-05:00", ISO8601Utils.format(date, false, tz));
    }

    // format(Date,boolean,TimeZone): offset==0 with explicit UTC timezone arg appends 'Z'
    @Test
    public void testFormat_date_tz_utcZeroOffset_appendsZ() throws Throwable {
        Date date = new Date(0L);
        TimeZone tz = TimeZone.getTimeZone("UTC");
        assertEquals("1970-01-01T00:00:00Z", ISO8601Utils.format(date, false, tz));
    }

    // format(Date): padInt pads single-digit hour/minute/second fields
    @Test
    public void testFormat_date_paddingSingleDigitFields() throws Throwable {
        Date date = utcDate(1970, 1, 1, 3, 5, 9, 0);
        assertEquals("1970-01-01T03:05:09Z", ISO8601Utils.format(date));
    }

    // parse: date-only with dashes, early-return branch, pos index updated
    @Test
    public void testParse_dateOnly_withDashes_andPosIndex() throws Throwable {
        String date = "2018-06-25";
        ParsePosition pos = new ParsePosition(0);
        Date actual = ISO8601Utils.parse(date, pos);
        assertEquals(localDate(2018, 6, 25), actual);
        assertEquals(10, pos.getIndex());
    }

    // parse: date-only without dashes, checkOffset('-') false branches
    @Test
    public void testParse_dateOnly_noDashes() throws Throwable {
        String date = "20180625";
        ParsePosition pos = new ParsePosition(0);
        Date actual = ISO8601Utils.parse(date, pos);
        assertEquals(localDate(2018, 6, 25), actual);
        assertEquals(8, pos.getIndex());
    }

    // parse: hasT branch, timezoneIndicator=='Z', pos index at end of string
    @Test
    public void testParse_dateTime_withZ_andPosIndex() throws Throwable {
        String date = "2018-06-25T10:15:30Z";
        ParsePosition pos = new ParsePosition(0);
        Date actual = ISO8601Utils.parse(date, pos);
        assertEquals(utcDate(2018, 6, 25, 10, 15, 30, 0), actual);
        assertEquals(date.length(), pos.getIndex());
    }

    // parse: milliseconds with exactly 3 digits (default switch case)
    @Test
    public void testParse_dateTime_withMillis_threeDigits() throws Throwable {
        String date = "2018-06-25T10:15:30.123Z";
        Date actual = ISO8601Utils.parse(date, new ParsePosition(0));
        assertEquals(utcDate(2018, 6, 25, 10, 15, 30, 123), actual);
    }

    // parse: milliseconds with 1 digit fraction, scaled by 100 (switch case 1)
    @Test
    public void testParse_dateTime_millis_oneDigit_scaledTo100() throws Throwable {
        String date = "2018-06-25T10:15:30.5Z";
        Date actual = ISO8601Utils.parse(date, new ParsePosition(0));
        assertEquals(utcDate(2018, 6, 25, 10, 15, 30, 500), actual);
    }

    // parse: milliseconds with 2 digits fraction, scaled by 10 (switch case 2)
    @Test
    public void testParse_dateTime_millis_twoDigits_scaledTo10() throws Throwable {
        String date = "2018-06-25T10:15:30.12Z";
        Date actual = ISO8601Utils.parse(date, new ParsePosition(0));
        assertEquals(utcDate(2018, 6, 25, 10, 15, 30, 120), actual);
    }

    // parse: more than 3 fraction digits truncated to first 3, offset skips remaining digits
    @Test
    public void testParse_dateTime_millis_moreThanThreeDigits_truncated() throws Throwable {
        String date = "2018-06-25T10:15:30.123456Z";
        ParsePosition pos = new ParsePosition(0);
        Date actual = ISO8601Utils.parse(date, pos);
        assertEquals(utcDate(2018, 6, 25, 10, 15, 30, 123), actual);
        assertEquals(date.length(), pos.getIndex());
    }

    // parse: seconds/milliseconds omitted when char after minutes is 'Z'
    @Test
    public void testParse_dateTime_secondsOmitted() throws Throwable {
        String date = "2018-06-25T10:15Z";
        Date actual = ISO8601Utils.parse(date, new ParsePosition(0));
        assertEquals(utcDate(2018, 6, 25, 10, 15, 0, 0), actual);
    }

    // parse: leap second 60 truncated to 59 (seconds > 59 && seconds < 63 branch)
    @Test
    public void testParse_leapSecond_truncatedTo59() throws Throwable {
        String date = "2018-06-25T10:15:60Z";
        Date actual = ISO8601Utils.parse(date, new ParsePosition(0));
        assertEquals(utcDate(2018, 6, 25, 10, 15, 59, 0), actual);
    }

    // parse: seconds value outside truncation window (63) is invalid -> ParseException
    @Test
    public void testParse_secondsOutOfLeapRange_throwsParseException() throws Throwable {
        String date = "2018-06-25T10:15:63Z";
        try {
            ISO8601Utils.parse(date, new ParsePosition(0));
            fail("expected ParseException");
        } catch (ParseException expected) {
            // ok
        }
    }

    // parse: timezone offset with colon
    @Test
    public void testParse_timezone_withColonOffset() throws Throwable {
        String date = "2018-06-25T10:15:30+02:00";
        TimeZone tz = TimeZone.getTimeZone("GMT+02:00");
        Date actual = ISO8601Utils.parse(date, new ParsePosition(0));
        assertEquals(tzDate(tz, 2018, 6, 25, 10, 15, 30), actual);
    }

    // parse: timezone offset without colon must resolve to same instant as with colon
    @Test
    public void testParse_timezone_withoutColonOffset() throws Throwable {
        String date = "2018-06-25T10:15:30+0200";
        TimeZone tz = TimeZone.getTimeZone("GMT+02:00");
        Date actual = ISO8601Utils.parse(date, new ParsePosition(0));
        assertEquals(tzDate(tz, 2018, 6, 25, 10, 15, 30), actual);
    }

    // parse: "+0000" special-cased to UTC
    @Test
    public void testParse_timezone_plus0000_isUTC() throws Throwable {
        String date = "2018-06-25T10:15:30+0000";
        Date actual = ISO8601Utils.parse(date, new ParsePosition(0));
        assertEquals(utcDate(2018, 6, 25, 10, 15, 30, 0), actual);
    }

    // parse: "+00:00" special-cased to UTC
    @Test
    public void testParse_timezone_plusColon0000_isUTC() throws Throwable {
        String date = "2018-06-25T10:15:30+00:00";
        Date actual = ISO8601Utils.parse(date, new ParsePosition(0));
        assertEquals(utcDate(2018, 6, 25, 10, 15, 30, 0), actual);
    }

    // parse: negative timezone offset with non-zero minutes
    @Test
    public void testParse_timezone_negativeOffset() throws Throwable {
        String date = "2018-06-25T10:15:30-05:30";
        TimeZone tz = TimeZone.getTimeZone("GMT-05:30");
        Date actual = ISO8601Utils.parse(date, new ParsePosition(0));
        assertEquals(tzDate(tz, 2018, 6, 25, 10, 15, 30), actual);
    }

    // parse: no timezone indicator after time component -> IllegalArgumentException wrapped
    @Test
    public void testParse_noTimezoneIndicator_throwsParseException() throws Throwable {
        String date = "2018-06-25T10:15:30";
        try {
            ISO8601Utils.parse(date, new ParsePosition(0));
            fail("expected ParseException");
        } catch (ParseException expected) {
            assertTrue(expected.getMessage().contains("No time zone"));
        }
    }

    // parse: invalid timezone indicator character -> IndexOutOfBoundsException wrapped
    @Test
    public void testParse_invalidTimezoneIndicator_throwsParseException() throws Throwable {
        String date = "2018-06-25T10:15:30X";
        try {
            ISO8601Utils.parse(date, new ParsePosition(0));
            fail("expected ParseException");
        } catch (ParseException expected) {
            assertTrue(expected.getMessage().contains("Invalid time zone indicator"));
        }
    }

    // parse: non-digit character inside year field -> NumberFormatException wrapped
    @Test
    public void testParse_invalidYearDigits_throwsParseException() throws Throwable {
        String date = "20XX-06-25";
        try {
            ISO8601Utils.parse(date, new ParsePosition(0));
            fail("expected ParseException");
        } catch (ParseException expected) {
            assertTrue(expected.getMessage().contains("Invalid number"));
        }
    }

    // parse: date-only format with trailing garbage instead of 'T' -> invalid timezone indicator
    @Test
    public void testParse_dateOnlyWithTrailingGarbage_throwsParseException() throws Throwable {
        String date = "2018-06-25X";
        try {
            ISO8601Utils.parse(date, new ParsePosition(0));
            fail("expected ParseException");
        } catch (ParseException expected) {
            assertTrue(expected.getMessage().contains("zone indicator"));
        }
    }
}
