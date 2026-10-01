package org.apache.commons.lang.time;

import static org.junit.Assert.*;
import org.junit.Test;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.TimeZone;

public class DateUtilsClaudeTest {

    private Calendar newCal(int year, int month, int day, int hour, int min, int sec) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, day, hour, min, sec);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    // constructor is public, should be instantiable without error
    @Test
    public void testConstructor_createsInstance() throws Throwable {
        DateUtils du = new DateUtils();
        assertNotNull(du);
    }

    // isSameDay(Date,Date): null first argument throws IllegalArgumentException
    @Test
    public void testIsSameDay_dateNull_throwsIAE() throws Throwable {
        try {
            DateUtils.isSameDay((Date) null, new Date());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // isSameDay(Date,Date): same day different times returns true
    @Test
    public void testIsSameDay_sameDayDifferentTime_true() throws Throwable {
        Date d1 = newCal(2002, Calendar.MARCH, 28, 13, 45, 0).getTime();
        Date d2 = newCal(2002, Calendar.MARCH, 28, 6, 1, 0).getTime();
        assertTrue(DateUtils.isSameDay(d1, d2));
    }

    // isSameDay(Date,Date): different day returns false
    @Test
    public void testIsSameDay_differentDay_false() throws Throwable {
        Date d1 = newCal(2002, Calendar.MARCH, 28, 13, 45, 0).getTime();
        Date d2 = newCal(2002, Calendar.MARCH, 12, 13, 45, 0).getTime();
        assertFalse(DateUtils.isSameDay(d1, d2));
    }

    // isSameDay(Calendar,Calendar): null argument throws IllegalArgumentException
    @Test
    public void testIsSameDay_calendarNull_throwsIAE() throws Throwable {
        try {
            DateUtils.isSameDay((Calendar) null, Calendar.getInstance());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // isSameInstant(Date,Date): null argument throws IllegalArgumentException
    @Test
    public void testIsSameInstant_dateNull_throwsIAE() throws Throwable {
        try {
            DateUtils.isSameInstant((Date) null, new Date());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // isSameInstant(Date,Date): equal millisecond instant returns true
    @Test
    public void testIsSameInstant_equalMillis_true() throws Throwable {
        Date d1 = new Date(123456789L);
        Date d2 = new Date(123456789L);
        assertTrue(DateUtils.isSameInstant(d1, d2));
    }

    // isSameInstant(Date,Date): different instant returns false
    @Test
    public void testIsSameInstant_differentMillis_false() throws Throwable {
        Date d1 = new Date(1000L);
        Date d2 = new Date(2000L);
        assertFalse(DateUtils.isSameInstant(d1, d2));
    }

    // isSameInstant(Calendar,Calendar): null argument throws IllegalArgumentException
    @Test
    public void testIsSameInstant_calendarNull_throwsIAE() throws Throwable {
        try {
            DateUtils.isSameInstant((Calendar) null, Calendar.getInstance());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // isSameLocalTime: null argument throws IllegalArgumentException
    @Test
    public void testIsSameLocalTime_null_throwsIAE() throws Throwable {
        try {
            DateUtils.isSameLocalTime((Calendar) null, Calendar.getInstance());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // isSameLocalTime: same fields and same class returns true
    @Test
    public void testIsSameLocalTime_sameFields_true() throws Throwable {
        Calendar cal1 = newCal(2002, Calendar.MARCH, 28, 13, 45, 30);
        Calendar cal2 = (Calendar) cal1.clone();
        assertTrue(DateUtils.isSameLocalTime(cal1, cal2));
    }

    // isSameLocalTime: differing second field returns false
    @Test
    public void testIsSameLocalTime_differentFields_false() throws Throwable {
        Calendar cal1 = newCal(2002, Calendar.MARCH, 28, 13, 45, 30);
        Calendar cal2 = newCal(2002, Calendar.MARCH, 28, 13, 45, 31);
        assertFalse(DateUtils.isSameLocalTime(cal1, cal2));
    }

    // parseDate: null string throws IllegalArgumentException
    @Test
    public void testParseDate_nullStr_throwsIAE() throws Throwable {
        try {
            DateUtils.parseDate(null, new String[] {"yyyy-MM-dd"});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parseDate: null patterns array throws IllegalArgumentException
    @Test
    public void testParseDate_nullPatterns_throwsIAE() throws Throwable {
        try {
            DateUtils.parseDate("2002-03-28", null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parseDate: first pattern fails to consume whole string, second pattern succeeds
    @Test
    public void testParseDate_matchesSecondPattern_success() throws Throwable {
        String[] patterns = new String[] {"yyyyMMdd", "yyyy-MM-dd"};
        Date d = DateUtils.parseDate("2002-03-28", patterns);
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        assertEquals(2002, cal.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH));
        assertEquals(28, cal.get(Calendar.DAY_OF_MONTH));
    }

    // parseDate: no pattern matches the whole string throws ParseException
    @Test
    public void testParseDate_noPatternMatches_throwsParseException() throws Throwable {
        try {
            DateUtils.parseDate("not-a-date", new String[] {"yyyy-MM-dd"});
            fail("expected ParseException");
        } catch (ParseException expected) {
        }
    }

    // addYears: adds positive amount to YEAR field
    @Test
    public void testAddYears_positiveAmount() throws Throwable {
        Date base = newCal(2000, Calendar.JANUARY, 1, 10, 0, 0).getTime();
        Date result = DateUtils.addYears(base, 5);
        Calendar c = Calendar.getInstance();
        c.setTime(result);
        assertEquals(2005, c.get(Calendar.YEAR));
    }

    // addMonths: adds negative amount, moving to prior month
    @Test
    public void testAddMonths_negativeAmount() throws Throwable {
        Date base = newCal(2000, Calendar.MARCH, 15, 10, 0, 0).getTime();
        Date result = DateUtils.addMonths(base, -2);
        Calendar c = Calendar.getInstance();
        c.setTime(result);
        assertEquals(Calendar.JANUARY, c.get(Calendar.MONTH));
    }

    // addWeeks: adds 1 week moving day-of-month forward by 7
    @Test
    public void testAddWeeks_amount() throws Throwable {
        Date base = newCal(2000, Calendar.JANUARY, 1, 10, 0, 0).getTime();
        Date result = DateUtils.addWeeks(base, 1);
        Calendar c = Calendar.getInstance();
        c.setTime(result);
        assertEquals(8, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.JANUARY, c.get(Calendar.MONTH));
    }

    // addDays: adds 10 days moving day-of-month forward
    @Test
    public void testAddDays_amount() throws Throwable {
        Date base = newCal(2000, Calendar.JANUARY, 1, 10, 0, 0).getTime();
        Date result = DateUtils.addDays(base, 10);
        Calendar c = Calendar.getInstance();
        c.setTime(result);
        assertEquals(11, c.get(Calendar.DAY_OF_MONTH));
    }

    // addHours: adds 5 hours to HOUR_OF_DAY field
    @Test
    public void testAddHours_amount() throws Throwable {
        Date base = newCal(2000, Calendar.JANUARY, 1, 10, 0, 0).getTime();
        Date result = DateUtils.addHours(base, 5);
        Calendar c = Calendar.getInstance();
        c.setTime(result);
        assertEquals(15, c.get(Calendar.HOUR_OF_DAY));
    }

    // addMinutes: adds 90 minutes, rolling over the hour field
    @Test
    public void testAddMinutes_amount() throws Throwable {
        Date base = newCal(2000, Calendar.JANUARY, 1, 10, 0, 0).getTime();
        Date result = DateUtils.addMinutes(base, 90);
        Calendar c = Calendar.getInstance();
        c.setTime(result);
        assertEquals(11, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, c.get(Calendar.MINUTE));
    }

    // addSeconds: adds 90 seconds, rolling over the minute field
    @Test
    public void testAddSeconds_amount() throws Throwable {
        Date base = newCal(2000, Calendar.JANUARY, 1, 10, 0, 0).getTime();
        Date result = DateUtils.addSeconds(base, 90);
        Calendar c = Calendar.getInstance();
        c.setTime(result);
        assertEquals(1, c.get(Calendar.MINUTE));
        assertEquals(30, c.get(Calendar.SECOND));
    }

    // addMilliseconds: adds 500 ms to MILLISECOND field
    @Test
    public void testAddMilliseconds_amount() throws Throwable {
        Date base = newCal(2000, Calendar.JANUARY, 1, 10, 0, 0).getTime();
        Date result = DateUtils.addMilliseconds(base, 500);
        Calendar c = Calendar.getInstance();
        c.setTime(result);
        assertEquals(500, c.get(Calendar.MILLISECOND));
    }

    // add: null date throws IllegalArgumentException
    @Test
    public void testAdd_nullDate_throwsIAE() throws Throwable {
        try {
            DateUtils.add((Date) null, Calendar.DATE, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // round(Date,field): null date throws IllegalArgumentException
    @Test
    public void testRound_dateNull_throwsIAE() throws Throwable {
        try {
            DateUtils.round((Date) null, Calendar.YEAR);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // round(Date,HOUR_OF_DAY): minutes > 30 rounds up, matches class Javadoc example
    @Test
    public void testRound_hourOfDay_roundsUp_matchesJavadocExample() throws Throwable {
        Calendar input = newCal(2002, Calendar.JULY, 10, 13, 45, 1);
        input.set(Calendar.MILLISECOND, 231);
        Date rounded = DateUtils.round(input.getTime(), Calendar.HOUR_OF_DAY);
        Calendar expected = newCal(2002, Calendar.JULY, 10, 14, 0, 0);
        assertEquals(expected.getTime(), rounded);
    }

    // round(Object,field) with a Date instance delegates to round(Date,field)
    @Test
    public void testRound_objectOverload_withDate() throws Throwable {
        Date d = newCal(2002, Calendar.JULY, 10, 13, 45, 1).getTime();
        Date viaObject = DateUtils.round((Object) d, Calendar.HOUR_OF_DAY);
        Date viaDate = DateUtils.round(d, Calendar.HOUR_OF_DAY);
        assertEquals(viaDate, viaObject);
    }

    // round(Object,field): unsupported type throws ClassCastException
    @Test
    public void testRound_objectOverload_invalidType_throwsClassCastException() throws Throwable {
        try {
            DateUtils.round((Object) "not a date", Calendar.YEAR);
            fail("expected ClassCastException");
        } catch (ClassCastException expected) {
        }
    }

    // round(Object,field): null throws IllegalArgumentException
    @Test
    public void testRound_objectOverload_null_throwsIAE() throws Throwable {
        try {
            DateUtils.round((Object) null, Calendar.YEAR);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // round(Date,SEMI_MONTH): day in upper half rounds up to first of next month
    @Test
    public void testRound_semiMonth_roundsUpToNextMonth() throws Throwable {
        Calendar input = newCal(2002, Calendar.JULY, 25, 10, 0, 0);
        Date result = DateUtils.round(input.getTime(), DateUtils.SEMI_MONTH);
        Calendar expected = newCal(2002, Calendar.AUGUST, 1, 0, 0, 0);
        assertEquals(expected.getTime(), result);
    }

    // round(Calendar,DATE) on a DST transition day must round down to local midnight (LANG-59)
    @Test
    public void testRound_calendarDate_dstTransitionDay_startOfDay() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("America/Chicago");
        GregorianCalendar input = new GregorianCalendar(tz);
        input.clear();
        input.set(2006, Calendar.APRIL, 2, 1, 0, 0);
        GregorianCalendar expected = new GregorianCalendar(tz);
        expected.clear();
        expected.set(2006, Calendar.APRIL, 2, 0, 0, 0);
        Calendar result = DateUtils.round(input, Calendar.DATE);
        assertEquals(expected.getTime(), result.getTime());
    }

    // truncate(Date,field): null date throws IllegalArgumentException
    @Test
    public void testTruncate_dateNull_throwsIAE() throws Throwable {
        try {
            DateUtils.truncate((Date) null, Calendar.YEAR);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // truncate(Date,HOUR_OF_DAY): minutes/seconds/millis dropped, matches class Javadoc example
    @Test
    public void testTruncate_hour_matchesJavadocExample() throws Throwable {
        Calendar input = newCal(2002, Calendar.JULY, 10, 13, 45, 1);
        input.set(Calendar.MILLISECOND, 231);
        Date result = DateUtils.truncate(input.getTime(), Calendar.HOUR_OF_DAY);
        Calendar expected = newCal(2002, Calendar.JULY, 10, 13, 0, 0);
        assertEquals(expected.getTime(), result);
    }

    // truncate(Date,MONTH): truncates to first day of month at midnight
    @Test
    public void testTruncate_month_truncatesToFirstDay() throws Throwable {
        Calendar input = newCal(2002, Calendar.JULY, 10, 13, 45, 1);
        Date result = DateUtils.truncate(input.getTime(), Calendar.MONTH);
        Calendar expected = newCal(2002, Calendar.JULY, 1, 0, 0, 0);
        assertEquals(expected.getTime(), result);
    }

    // truncate(Date,SEMI_MONTH): day in upper half (20) truncates to 16th
    @Test
    public void testTruncate_semiMonth_topHalf_day20_to16() throws Throwable {
        Calendar input = newCal(2002, Calendar.JULY, 20, 15, 30, 45);
        Date result = DateUtils.truncate(input.getTime(), DateUtils.SEMI_MONTH);
        Calendar expected = newCal(2002, Calendar.JULY, 16, 0, 0, 0);
        assertEquals(expected.getTime(), result);
    }

    // truncate(Date,SEMI_MONTH): day in lower half (5) truncates to 1st
    @Test
    public void testTruncate_semiMonth_bottomHalf_day5_to1() throws Throwable {
        Calendar input = newCal(2002, Calendar.JULY, 5, 8, 0, 0);
        Date result = DateUtils.truncate(input.getTime(), DateUtils.SEMI_MONTH);
        Calendar expected = newCal(2002, Calendar.JULY, 1, 0, 0, 0);
        assertEquals(expected.getTime(), result);
    }

    // truncate(Object,field): unsupported type throws ClassCastException
    @Test
    public void testTruncate_objectOverload_invalidType_throwsClassCastException() throws Throwable {
        try {
            DateUtils.truncate((Object) "not a date", Calendar.YEAR);
            fail("expected ClassCastException");
        } catch (ClassCastException expected) {
        }
    }

    // truncate(Object,field): null throws IllegalArgumentException
    @Test
    public void testTruncate_objectOverload_null_throwsIAE() throws Throwable {
        try {
            DateUtils.truncate((Object) null, Calendar.YEAR);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // truncate(Calendar,DATE) on a DST transition day must yield local midnight (LANG-59/65 bug)
    @Test
    public void testTruncate_calendarDate_dstTransitionDay_startOfDay() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("America/Chicago");
        GregorianCalendar input = new GregorianCalendar(tz);
        input.clear();
        input.set(2006, Calendar.APRIL, 2, 1, 0, 0);
        GregorianCalendar expected = new GregorianCalendar(tz);
        expected.clear();
        expected.set(2006, Calendar.APRIL, 2, 0, 0, 0);
        Calendar result = DateUtils.truncate(input, Calendar.DATE);
        assertEquals(expected.getTime(), result.getTime());
    }

    // iterator(Date,int): null focus throws IllegalArgumentException
    @Test
    public void testIterator_dateNull_throwsIAE() throws Throwable {
        try {
            DateUtils.iterator((Date) null, DateUtils.RANGE_WEEK_SUNDAY);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // iterator(Date,int): invalid range style throws IllegalArgumentException
    @Test
    public void testIterator_invalidRangeStyle_throwsIAE() throws Throwable {
        Date focus = newCal(2002, Calendar.JULY, 4, 0, 0, 0).getTime();
        try {
            DateUtils.iterator(focus, 999);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // iterator(Date,RANGE_MONTH_SUNDAY): matches the exact Javadoc example boundaries
    @Test
    public void testIterator_rangeMonthSunday_matchesJavadocExample() throws Throwable {
        Date focus = newCal(2002, Calendar.JULY, 4, 10, 0, 0).getTime();
        Iterator it = DateUtils.iterator(focus, DateUtils.RANGE_MONTH_SUNDAY);
        Calendar first = (Calendar) it.next();
        Calendar expectedFirst = newCal(2002, Calendar.JUNE, 30, 0, 0, 0);
        assertTrue(DateUtils.isSameDay(first, expectedFirst));
        Calendar last = first;
        while (it.hasNext()) {
            last = (Calendar) it.next();
        }
        Calendar expectedLast = newCal(2002, Calendar.AUGUST, 3, 0, 0, 0);
        assertTrue(DateUtils.isSameDay(last, expectedLast));
    }

    // DateIterator.next(): calling next() after exhaustion throws NoSuchElementException
    @Test
    public void testIterator_exhausted_nextThrowsNoSuchElementException() throws Throwable {
        Date focus = newCal(2002, Calendar.JULY, 4, 0, 0, 0).getTime();
        Iterator it = DateUtils.iterator(focus, DateUtils.RANGE_WEEK_SUNDAY);
        while (it.hasNext()) {
            it.next();
        }
        try {
            it.next();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // DateIterator.remove(): always throws UnsupportedOperationException
    @Test
    public void testIterator_remove_throwsUnsupportedOperationException() throws Throwable {
        Date focus = newCal(2002, Calendar.JULY, 4, 0, 0, 0).getTime();
        Iterator it = DateUtils.iterator(focus, DateUtils.RANGE_WEEK_SUNDAY);
        try {
            it.remove();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }
}
