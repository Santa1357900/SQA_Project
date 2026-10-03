package org.apache.commons.lang3.time;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Test;
import static org.junit.Assert.*;

public class DateUtilsClaudeTest {

    private Date buildDate(int year, int month, int day, int hour, int minute, int second, int millis) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, day, hour, minute, second);
        c.set(Calendar.MILLISECOND, millis);
        return c.getTime();
    }

    // DateUtils() is public so JavaBean-style tools can instantiate it
    @Test
    public void testConstructor_publicNoArg_createsInstance() throws Throwable {
        DateUtils du = new DateUtils();
        assertNotNull(du);
    }

    // isSameDay(Date,Date): same calendar day, different time of day -> true
    @Test
    public void testIsSameDayDate_sameDayDifferentTime_returnsTrue() throws Throwable {
        Date d1 = buildDate(2002, Calendar.MARCH, 28, 6, 1, 0, 0);
        Date d2 = buildDate(2002, Calendar.MARCH, 28, 13, 45, 0, 0);
        assertTrue(DateUtils.isSameDay(d1, d2));
    }

    // isSameDay(Date,Date): different calendar day -> false
    @Test
    public void testIsSameDayDate_differentDay_returnsFalse() throws Throwable {
        Date d1 = buildDate(2002, Calendar.MARCH, 12, 13, 45, 0, 0);
        Date d2 = buildDate(2002, Calendar.MARCH, 28, 13, 45, 0, 0);
        assertFalse(DateUtils.isSameDay(d1, d2));
    }

    // isSameDay(Date,Date): null argument -> IllegalArgumentException
    @Test
    public void testIsSameDayDate_null_throwsIllegalArgumentException() throws Throwable {
        try {
            DateUtils.isSameDay((Date) null, buildDate(2002, Calendar.MARCH, 28, 0, 0, 0, 0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // isSameDay(Calendar,Calendar): same day -> true; null -> IllegalArgumentException
    @Test
    public void testIsSameDayCalendar_sameDayAndNull() throws Throwable {
        Calendar c1 = Calendar.getInstance();
        c1.set(2002, Calendar.MARCH, 28);
        Calendar c2 = Calendar.getInstance();
        c2.set(2002, Calendar.MARCH, 28);
        assertTrue(DateUtils.isSameDay(c1, c2));
        try {
            DateUtils.isSameDay((Calendar) null, c2);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // isSameInstant(Date,Date): equal millis true, different millis false, null throws
    @Test
    public void testIsSameInstantDate_equalAndDifferentAndNull() throws Throwable {
        Date d1 = new Date(100000L);
        Date d2 = new Date(100000L);
        Date d3 = new Date(100001L);
        assertTrue(DateUtils.isSameInstant(d1, d2));
        assertFalse(DateUtils.isSameInstant(d1, d3));
        try {
            DateUtils.isSameInstant((Date) null, d2);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // isSameLocalTime(Calendar,Calendar): null throws; identical local fields -> true
    @Test
    public void testIsSameLocalTime_nullAndIdenticalFields() throws Throwable {
        Calendar c1 = Calendar.getInstance();
        c1.set(2008, Calendar.JANUARY, 1, 7, 15, 10);
        c1.set(Calendar.MILLISECOND, 538);
        Calendar c2 = (Calendar) c1.clone();
        assertTrue(DateUtils.isSameLocalTime(c1, c2));
        try {
            DateUtils.isSameLocalTime((Calendar) null, c2);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Contract: local time must distinguish AM from PM (01:30 vs 13:30 on same day differ)
    @Test
    public void testIsSameLocalTime_sameClockHourDifferentAmPm_returnsFalse() throws Throwable {
        Calendar c1 = Calendar.getInstance();
        c1.clear();
        c1.set(2008, Calendar.JANUARY, 1, 1, 30, 0);
        Calendar c2 = Calendar.getInstance();
        c2.clear();
        c2.set(2008, Calendar.JANUARY, 1, 13, 30, 0);
        assertFalse(DateUtils.isSameLocalTime(c1, c2));
    }

    // parseDate: pattern matches whole string -> parsed date returned
    @Test
    public void testParseDate_matchingPattern_returnsParsedDate() throws Throwable {
        Date result = DateUtils.parseDate("2002-03-28", new String[] { "yyyy-MM-dd" });
        Calendar c = Calendar.getInstance();
        c.setTime(result);
        assertEquals(2002, c.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, c.get(Calendar.MONTH));
        assertEquals(28, c.get(Calendar.DATE));
    }

    // parseDate: loop tries each pattern; first fails, second matches whole string
    @Test
    public void testParseDate_firstPatternFailsSecondSucceeds_parsesWithSecond() throws Throwable {
        Date result = DateUtils.parseDate("28/03/2002", new String[] { "yyyy-MM-dd", "dd/MM/yyyy" });
        Calendar c = Calendar.getInstance();
        c.setTime(result);
        assertEquals(2002, c.get(Calendar.YEAR));
        assertEquals(28, c.get(Calendar.DATE));
    }

    // parseDate: no pattern matches the whole string -> ParseException
    @Test
    public void testParseDate_noPatternMatches_throwsParseException() throws Throwable {
        try {
            DateUtils.parseDate("not-a-date", new String[] { "yyyy-MM-dd" });
            fail("expected ParseException");
        } catch (ParseException expected) { }
    }

    // parseDate: null date string -> IllegalArgumentException
    @Test
    public void testParseDate_nullString_throwsIllegalArgumentException() throws Throwable {
        try {
            DateUtils.parseDate((String) null, new String[] { "yyyy-MM-dd" });
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // parseDateStrictly: does not allow invalid day-of-month that lenient parsing would roll over
    @Test
    public void testParseDateStrictly_invalidDayOfMonth_throwsParseException() throws Throwable {
        try {
            DateUtils.parseDateStrictly("2002-02-30", new String[] { "yyyy-MM-dd" });
            fail("expected ParseException");
        } catch (ParseException expected) { }
    }

    // addYears/addMonths: original unchanged, returned date reflects addition
    @Test
    public void testAddYearsAndAddMonths_returnsShiftedDateUnchangedOriginal() throws Throwable {
        Date original = buildDate(2000, Calendar.JANUARY, 15, 0, 0, 0, 0);
        Date plusYear = DateUtils.addYears(original, 1);
        Date plusMonth = DateUtils.addMonths(original, 2);
        Calendar c = Calendar.getInstance();
        c.setTime(plusYear);
        assertEquals(2001, c.get(Calendar.YEAR));
        c.setTime(plusMonth);
        assertEquals(Calendar.MARCH, c.get(Calendar.MONTH));
        c.setTime(original);
        assertEquals(2000, c.get(Calendar.YEAR));
    }

    // addDays/addHours/addMinutes: shifts by the expected amount; null date -> IllegalArgumentException
    @Test
    public void testAddFields_shiftsByExpectedAmountsAndNullThrows() throws Throwable {
        Date original = buildDate(2000, Calendar.JANUARY, 1, 10, 0, 0, 0);
        Calendar c = Calendar.getInstance();
        c.setTime(DateUtils.addDays(original, 10));
        assertEquals(11, c.get(Calendar.DATE));
        c.setTime(DateUtils.addHours(original, 2));
        assertEquals(12, c.get(Calendar.HOUR_OF_DAY));
        c.setTime(DateUtils.addMinutes(original, 90));
        assertEquals(11, c.get(Calendar.HOUR_OF_DAY));
        try {
            DateUtils.addSeconds((Date) null, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // setYears/setMonths/setHours/setMinutes: sets the absolute field value directly
    @Test
    public void testSetFields_setsAbsoluteFieldValues() throws Throwable {
        Date original = buildDate(2000, Calendar.JANUARY, 15, 10, 10, 10, 10);
        Calendar c = Calendar.getInstance();
        c.setTime(DateUtils.setYears(original, 1999));
        assertEquals(1999, c.get(Calendar.YEAR));
        c.setTime(DateUtils.setMonths(original, Calendar.JUNE));
        assertEquals(Calendar.JUNE, c.get(Calendar.MONTH));
        c.setTime(DateUtils.setHours(original, 5));
        assertEquals(5, c.get(Calendar.HOUR_OF_DAY));
        c.setTime(DateUtils.setMinutes(original, 45));
        assertEquals(45, c.get(Calendar.MINUTE));
    }

    // setDays: non-lenient calendar rejects a day-of-month invalid for that month
    @Test
    public void testSetDays_invalidDayForMonth_throwsIllegalArgumentException() throws Throwable {
        Date original = buildDate(2020, Calendar.APRIL, 15, 0, 0, 0, 0);
        try {
            DateUtils.setDays(original, 31);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // toCalendar: resulting Calendar represents the same instant as the source Date
    @Test
    public void testToCalendar_convertsDateToCalendarWithSameInstant() throws Throwable {
        Date date = buildDate(2005, Calendar.MAY, 10, 1, 2, 3, 4);
        Calendar c = DateUtils.toCalendar(date);
        assertEquals(date.getTime(), c.getTime().getTime());
    }

    // round(Date,HOUR_OF_DAY): documented example, >=30 minutes rounds up to next hour
    @Test
    public void testRoundDate_hourOfDayPastHalf_roundsUp() throws Throwable {
        Date date = buildDate(2002, Calendar.MARCH, 28, 13, 45, 1, 231);
        Date rounded = DateUtils.round(date, Calendar.HOUR_OF_DAY);
        Calendar c = Calendar.getInstance();
        c.setTime(rounded);
        assertEquals(14, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, c.get(Calendar.MINUTE));
        assertEquals(0, c.get(Calendar.MILLISECOND));
    }

    // round(Date,MONTH): documented example, day past mid-month rolls to the first of next month
    @Test
    public void testRoundDate_monthPastMidMonth_rollsToNextMonth() throws Throwable {
        Date date = buildDate(2002, Calendar.MARCH, 28, 13, 45, 1, 231);
        Date rounded = DateUtils.round(date, Calendar.MONTH);
        Calendar c = Calendar.getInstance();
        c.setTime(rounded);
        assertEquals(Calendar.APRIL, c.get(Calendar.MONTH));
        assertEquals(1, c.get(Calendar.DATE));
    }

    // round(Date,...): null date -> IllegalArgumentException
    @Test
    public void testRoundDate_null_throwsIllegalArgumentException() throws Throwable {
        try {
            DateUtils.round((Date) null, Calendar.HOUR_OF_DAY);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // round(Object,...): Date and Calendar inputs for the same instant dispatch consistently
    @Test
    public void testRoundObject_dateAndCalendarInputsProduceSameInstant() throws Throwable {
        Date date = buildDate(2002, Calendar.MARCH, 28, 13, 45, 1, 231);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        Date viaDate = DateUtils.round((Object) date, Calendar.HOUR_OF_DAY);
        Date viaCal = DateUtils.round((Object) cal, Calendar.HOUR_OF_DAY);
        assertEquals(viaDate.getTime(), viaCal.getTime());
    }

    // round(Object,...): unsupported type -> ClassCastException; null -> IllegalArgumentException
    @Test
    public void testRoundObject_invalidTypeAndNull_throwsExpectedExceptions() throws Throwable {
        try {
            DateUtils.round((Object) "not a date", Calendar.HOUR_OF_DAY);
            fail("expected ClassCastException");
        } catch (ClassCastException expected) { }
        try {
            DateUtils.round((Object) null, Calendar.HOUR_OF_DAY);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // truncate(Date,HOUR_OF_DAY): documented example, drops minutes/seconds/millis
    @Test
    public void testTruncateDate_hourOfDay_dropsSubHourFields() throws Throwable {
        Date date = buildDate(2002, Calendar.MARCH, 28, 13, 45, 1, 231);
        Date truncated = DateUtils.truncate(date, Calendar.HOUR_OF_DAY);
        Calendar c = Calendar.getInstance();
        c.setTime(truncated);
        assertEquals(13, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, c.get(Calendar.MINUTE));
        assertEquals(0, c.get(Calendar.MILLISECOND));
    }

    // truncate(Date,MONTH): documented example, resets to first day of month at midnight
    @Test
    public void testTruncateDate_month_resetsToFirstOfMonth() throws Throwable {
        Date date = buildDate(2002, Calendar.MARCH, 28, 13, 45, 1, 231);
        Date truncated = DateUtils.truncate(date, Calendar.MONTH);
        Calendar c = Calendar.getInstance();
        c.setTime(truncated);
        assertEquals(1, c.get(Calendar.DATE));
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY));
    }

    // truncate(Date,SEMI_MONTH): day in the second half of the month truncates to the 16th
    @Test
    public void testTruncateDate_semiMonthSecondHalf_truncatesToSixteenth() throws Throwable {
        Date date = buildDate(2002, Calendar.MARCH, 20, 13, 45, 1, 231);
        Date truncated = DateUtils.truncate(date, DateUtils.SEMI_MONTH);
        Calendar c = Calendar.getInstance();
        c.setTime(truncated);
        assertEquals(16, c.get(Calendar.DATE));
    }

    // truncate(Date,...): unsupported calendar field -> IllegalArgumentException
    @Test
    public void testTruncateDate_unsupportedField_throwsIllegalArgumentException() throws Throwable {
        Date date = buildDate(2002, Calendar.MARCH, 28, 13, 45, 1, 231);
        try {
            DateUtils.truncate(date, Calendar.DAY_OF_WEEK);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // truncate(Object,...): unsupported type -> ClassCastException; null -> IllegalArgumentException
    @Test
    public void testTruncateObject_invalidTypeAndNull_throwsExpectedExceptions() throws Throwable {
        try {
            DateUtils.truncate((Object) "not a date", Calendar.HOUR_OF_DAY);
            fail("expected ClassCastException");
        } catch (ClassCastException expected) { }
        try {
            DateUtils.truncate((Object) null, Calendar.HOUR_OF_DAY);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // ceiling(Date,HOUR_OF_DAY): not on a boundary -> rounds up to next hour
    @Test
    public void testCeilingDate_hourOfDay_roundsUpWhenNotOnBoundary() throws Throwable {
        Date date = buildDate(2002, Calendar.MARCH, 28, 13, 10, 1, 1);
        Date ceiled = DateUtils.ceiling(date, Calendar.HOUR_OF_DAY);
        Calendar c = Calendar.getInstance();
        c.setTime(ceiled);
        assertEquals(14, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, c.get(Calendar.MINUTE));
    }



    // ceiling(Object,...): unsupported type -> ClassCastException; null -> IllegalArgumentException
    @Test
    public void testCeilingObject_invalidTypeAndNull_throwsExpectedExceptions() throws Throwable {
        try {
            DateUtils.ceiling((Object) "not a date", Calendar.HOUR_OF_DAY);
            fail("expected ClassCastException");
        } catch (ClassCastException expected) { }
        try {
            DateUtils.ceiling((Object) null, Calendar.HOUR_OF_DAY);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // modify(): year beyond 280 million -> ArithmeticException
    @Test
    public void testRoundCalendar_yearTooLarge_throwsArithmeticException() throws Throwable {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, 280000001);
        try {
            DateUtils.round(cal, Calendar.MONTH);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // iterator(Date,...): null date -> IllegalArgumentException
    @Test
    public void testIteratorDate_null_throwsIllegalArgumentException() throws Throwable {
        try {
            DateUtils.iterator((Date) null, DateUtils.RANGE_WEEK_SUNDAY);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // iterator(Calendar,...): invalid range style -> IllegalArgumentException
    @Test
    public void testIteratorCalendar_invalidRangeStyle_throwsIllegalArgumentException() throws Throwable {
        Calendar focus = Calendar.getInstance();
        try {
            DateUtils.iterator(focus, 999);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // iterator(Date, RANGE_MONTH_SUNDAY): matches the documented Jul 4 2002 example, then exhausts
    @Test
    public void testIteratorDate_rangeMonthSunday_matchesDocumentedExampleAndExhausts() throws Throwable {
        Date focus = buildDate(2002, Calendar.JULY, 4, 0, 0, 0, 0);
        Iterator<Calendar> it = DateUtils.iterator(focus, DateUtils.RANGE_MONTH_SUNDAY);
        Calendar first = it.next();
        assertEquals(Calendar.JUNE, first.get(Calendar.MONTH));
        assertEquals(30, first.get(Calendar.DATE));
        Calendar last = first;
        while (it.hasNext()) {
            last = it.next();
        }
        assertEquals(Calendar.AUGUST, last.get(Calendar.MONTH));
        assertEquals(3, last.get(Calendar.DATE));
        try {
            it.next();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) { }
    }

    // iterator(Object,...): unsupported type -> ClassCastException
    @Test
    public void testIteratorObject_invalidType_throwsClassCastException() throws Throwable {
        try {
            DateUtils.iterator((Object) "not a date", DateUtils.RANGE_WEEK_SUNDAY);
            fail("expected ClassCastException");
        } catch (ClassCastException expected) { }
    }

    // getFragmentInMilliseconds/InSeconds(Date): matches documented SECOND/MINUTE fragment examples
    @Test
    public void testGetFragmentInMillisecondsAndSeconds_matchesDocumentedExamples() throws Throwable {
        Date date = buildDate(2008, Calendar.JANUARY, 1, 7, 15, 10, 538);
        assertEquals(538L, DateUtils.getFragmentInMilliseconds(date, Calendar.SECOND));
        assertEquals(10L, DateUtils.getFragmentInSeconds(date, Calendar.MINUTE));
    }

    // getFragmentInMinutes/InHours(Date): matches documented HOUR_OF_DAY/DAY_OF_YEAR fragment examples
    @Test
    public void testGetFragmentInMinutesAndHours_matchesDocumentedExamples() throws Throwable {
        Date date = buildDate(2008, Calendar.JANUARY, 1, 7, 15, 10, 538);
        assertEquals(15L, DateUtils.getFragmentInMinutes(date, Calendar.HOUR_OF_DAY));
        assertEquals(7L, DateUtils.getFragmentInHours(date, Calendar.DAY_OF_YEAR));
    }

    // getFragmentInDays(Date): matches documented MONTH fragment example
    @Test
    public void testGetFragmentInDays_matchesDocumentedExample() throws Throwable {
        Date date = buildDate(2008, Calendar.JANUARY, 28, 0, 0, 0, 0);
        assertEquals(28L, DateUtils.getFragmentInDays(date, Calendar.MONTH));
    }

    // getFragmentInMilliseconds(Calendar,MILLISECOND): a millisecond cannot be split further -> 0
    @Test
    public void testGetFragmentInMillisecondsCalendar_millisecondFragment_returnsZero() throws Throwable {
        Calendar c = Calendar.getInstance();
        c.set(2008, Calendar.JANUARY, 16, 7, 15, 10);
        c.set(Calendar.MILLISECOND, 538);
        assertEquals(0L, DateUtils.getFragmentInMilliseconds(c, Calendar.MILLISECOND));
    }

    // getFragment: unsupported fragment field -> IllegalArgumentException
    @Test
    public void testGetFragment_unsupportedFragment_throwsIllegalArgumentException() throws Throwable {
        Date date = buildDate(2008, Calendar.JANUARY, 28, 0, 0, 0, 0);
        try {
            DateUtils.getFragmentInDays(date, Calendar.DAY_OF_WEEK);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // getFragment: null date -> IllegalArgumentException
    @Test
    public void testGetFragment_nullDate_throwsIllegalArgumentException() throws Throwable {
        try {
            DateUtils.getFragmentInMilliseconds((Date) null, Calendar.SECOND);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // truncatedEquals/truncatedCompareTo(Date,...): same-day instants equal; different days don't
    @Test
    public void testTruncatedEqualsAndCompareTo_date_sameDayDifferentTime() throws Throwable {
        Date d1 = buildDate(2002, Calendar.MARCH, 28, 6, 0, 0, 0);
        Date d2 = buildDate(2002, Calendar.MARCH, 28, 23, 0, 0, 0);
        assertTrue(DateUtils.truncatedEquals(d1, d2, Calendar.DATE));
        assertEquals(0, DateUtils.truncatedCompareTo(d1, d2, Calendar.DATE));
        Date d3 = buildDate(2002, Calendar.MARCH, 27, 23, 0, 0, 0);
        assertFalse(DateUtils.truncatedEquals(d1, d3, Calendar.DATE));
        assertTrue(DateUtils.truncatedCompareTo(d3, d1, Calendar.DATE) < 0);
    }

    // truncatedEquals/truncatedCompareTo(Calendar,...): different days are unequal and compare negative
    @Test
    public void testTruncatedEqualsAndCompareTo_calendar_differentDay() throws Throwable {
        Calendar c1 = Calendar.getInstance();
        c1.set(2002, Calendar.MARCH, 27, 23, 0, 0);
        Calendar c2 = Calendar.getInstance();
        c2.set(2002, Calendar.MARCH, 28, 1, 0, 0);
        assertFalse(DateUtils.truncatedEquals(c1, c2, Calendar.DATE));
        assertTrue(DateUtils.truncatedCompareTo(c1, c2, Calendar.DATE) < 0);
    }
}
