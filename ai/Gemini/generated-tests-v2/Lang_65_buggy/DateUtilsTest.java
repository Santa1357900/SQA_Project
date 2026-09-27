package org.apache.commons.lang.time;

import org.junit.Test;
import static org.junit.Assert.*;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.TimeZone;

public class DateUtilsTest {

    @Test
    public void testConstants() throws Throwable {
        assertNotNull(DateUtils.UTC_TIME_ZONE);
        assertEquals(1000L, DateUtils.MILLIS_PER_SECOND);
        assertEquals(60000L, DateUtils.MILLIS_PER_MINUTE);
        assertEquals(3600000L, DateUtils.MILLIS_PER_HOUR);
        assertEquals(86400000L, DateUtils.MILLIS_PER_DAY);
        assertEquals(1001, DateUtils.SEMI_MONTH);
        assertEquals(1, DateUtils.RANGE_WEEK_SUNDAY);
        assertEquals(2, DateUtils.RANGE_WEEK_MONDAY);
        assertEquals(3, DateUtils.RANGE_WEEK_RELATIVE);
        assertEquals(4, DateUtils.RANGE_WEEK_CENTER);
        assertEquals(5, DateUtils.RANGE_MONTH_SUNDAY);
        assertEquals(6, DateUtils.RANGE_MONTH_MONDAY);
        
        // Deprecated constants
        assertEquals(1000, DateUtils.MILLIS_IN_SECOND);
        assertEquals(60000, DateUtils.MILLIS_IN_MINUTE);
        assertEquals(3600000, DateUtils.MILLIS_IN_HOUR);
        assertEquals(86400000, DateUtils.MILLIS_IN_DAY);

        DateUtils du = new DateUtils();
        assertNotNull(du);
    }

    @Test
    public void testIsSameDayDate() throws Throwable {
        Date d1 = new Date(1000000000L);
        Date d2 = new Date(1000000000L + 500L);
        Date d3 = new Date(1000000000L + 100000000L);

        assertTrue(DateUtils.isSameDay(d1, d2));
        assertFalse(DateUtils.isSameDay(d1, d3));

        boolean thrown = false;
        try {
            DateUtils.isSameDay((Date) null, d2);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.isSameDay(d1, (Date) null);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testIsSameDayCalendar() throws Throwable {
        Calendar c1 = Calendar.getInstance();
        c1.set(2010, Calendar.JANUARY, 1, 10, 0, 0);
        Calendar c2 = Calendar.getInstance();
        c2.set(2010, Calendar.JANUARY, 1, 23, 59, 59);
        Calendar c3 = Calendar.getInstance();
        c3.set(2010, Calendar.JANUARY, 2, 10, 0, 0);

        assertTrue(DateUtils.isSameDay(c1, c2));
        assertFalse(DateUtils.isSameDay(c1, c3));

        boolean thrown = false;
        try {
            DateUtils.isSameDay((Calendar) null, c2);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.isSameDay(c1, (Calendar) null);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testIsSameInstantDate() throws Throwable {
        Date d1 = new Date(5000L);
        Date d2 = new Date(5000L);
        Date d3 = new Date(6000L);

        assertTrue(DateUtils.isSameInstant(d1, d2));
        assertFalse(DateUtils.isSameInstant(d1, d3));

        boolean thrown = false;
        try {
            DateUtils.isSameInstant((Date) null, d2);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.isSameInstant(d1, (Date) null);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testIsSameInstantCalendar() throws Throwable {
        Calendar c1 = Calendar.getInstance();
        c1.setTimeInMillis(5000L);
        Calendar c2 = Calendar.getInstance();
        c2.setTimeInMillis(5000L);
        Calendar c3 = Calendar.getInstance();
        c3.setTimeInMillis(6000L);

        assertTrue(DateUtils.isSameInstant(c1, c2));
        assertFalse(DateUtils.isSameInstant(c1, c3));

        boolean thrown = false;
        try {
            DateUtils.isSameInstant((Calendar) null, c2);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.isSameInstant(c1, (Calendar) null);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testIsSameLocalTime() throws Throwable {
        Calendar c1 = Calendar.getInstance();
        c1.set(2012, Calendar.JUNE, 1, 12, 30, 30);
        c1.set(Calendar.MILLISECOND, 500);

        Calendar c2 = Calendar.getInstance();
        c2.set(2012, Calendar.JUNE, 1, 12, 30, 30);
        c2.set(Calendar.MILLISECOND, 500);

        Calendar c3 = Calendar.getInstance();
        c3.set(2012, Calendar.JUNE, 1, 12, 30, 31);
        c3.set(Calendar.MILLISECOND, 500);

        assertTrue(DateUtils.isSameLocalTime(c1, c2));
        assertFalse(DateUtils.isSameLocalTime(c1, c3));

        boolean thrown = false;
        try {
            DateUtils.isSameLocalTime(null, c2);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.isSameLocalTime(c1, null);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testParseDate() throws Throwable {
        String[] patterns = new String[]{"yyyy-MM-dd", "yyyy/MM/dd"};
        Date parsed = DateUtils.parseDate("2020-05-10", patterns);
        assertNotNull(parsed);

        Date parsed2 = DateUtils.parseDate("2020/05/10", patterns);
        assertNotNull(parsed2);

        boolean parseExceptionThrown = false;
        try {
            DateUtils.parseDate("invalid-date", patterns);
        } catch (ParseException e) {
            parseExceptionThrown = true;
        }
        assertTrue(parseExceptionThrown);

        boolean iaeThrown = false;
        try {
            DateUtils.parseDate(null, patterns);
        } catch (IllegalArgumentException e) {
            iaeThrown = true;
        }
        assertTrue(iaeThrown);

        iaeThrown = false;
        try {
            DateUtils.parseDate("2020-05-10", null);
        } catch (IllegalArgumentException e) {
            iaeThrown = true;
        }
        assertTrue(iaeThrown);
    }

    @Test
    public void testAddMethods() throws Throwable {
        Date base = new Date(1000000000000L);
        assertNotNull(DateUtils.addYears(base, 1));
        assertNotNull(DateUtils.addMonths(base, 1));
        assertNotNull(DateUtils.addWeeks(base, 1));
        assertNotNull(DateUtils.addDays(base, 1));
        assertNotNull(DateUtils.addHours(base, 1));
        assertNotNull(DateUtils.addMinutes(base, 1));
        assertNotNull(DateUtils.addSeconds(base, 1));
        assertNotNull(DateUtils.addMilliseconds(base, 1));
        assertNotNull(DateUtils.add(base, Calendar.YEAR, -1));

        boolean thrown = false;
        try {
            DateUtils.add(null, Calendar.YEAR, 1);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testRoundAndTruncate() throws Throwable {
        Date base = new Date(1000000000000L);
        Calendar cal = Calendar.getInstance();
        cal.setTime(base);

        assertNotNull(DateUtils.round(base, Calendar.HOUR));
        assertNotNull(DateUtils.round(cal, Calendar.HOUR));
        assertNotNull(DateUtils.round((Object) base, Calendar.HOUR));
        assertNotNull(DateUtils.round((Object) cal, Calendar.HOUR));

        assertNotNull(DateUtils.truncate(base, Calendar.HOUR));
        assertNotNull(DateUtils.truncate(cal, Calendar.HOUR));
        assertNotNull(DateUtils.truncate((Object) base, Calendar.HOUR));
        assertNotNull(DateUtils.truncate((Object) cal, Calendar.HOUR));

        // Test SEMI_MONTH rounding and truncating logic specifically
        Date smDate1 = DateUtils.parseDate("2020-01-01", new String[]{"yyyy-MM-dd"});
        assertNotNull(DateUtils.round(smDate1, DateUtils.SEMI_MONTH));
        assertNotNull(DateUtils.truncate(smDate1, DateUtils.SEMI_MONTH));

        Date smDate2 = DateUtils.parseDate("2020-01-10", new String[]{"yyyy-MM-dd"});
        assertNotNull(DateUtils.round(smDate2, DateUtils.SEMI_MONTH));

        Date smDate3 = DateUtils.parseDate("2020-01-20", new String[]{"yyyy-MM-dd"});
        assertNotNull(DateUtils.round(smDate3, DateUtils.SEMI_MONTH));

        // Test AM_PM rounding logic
        Calendar amCal = Calendar.getInstance();
        amCal.set(2020, Calendar.JANUARY, 1, 5, 0, 0);
        assertNotNull(DateUtils.round(amCal, Calendar.AM_PM));
        amCal.set(2020, Calendar.JANUARY, 1, 15, 0, 0);
        assertNotNull(DateUtils.round(amCal, Calendar.AM_PM));

        // Test exceptions for round/truncate
        boolean thrown = false;
        try {
            DateUtils.round((Date) null, Calendar.HOUR);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.round((Calendar) null, Calendar.HOUR);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.round((Object) null, Calendar.HOUR);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.round(new Object(), Calendar.HOUR);
        } catch (ClassCastException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.truncate((Date) null, Calendar.HOUR);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.truncate((Calendar) null, Calendar.HOUR);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.truncate((Object) null, Calendar.HOUR);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.truncate(new Object(), Calendar.HOUR);
        } catch (ClassCastException e) {
            thrown = true;
        }
        assertTrue(thrown);

        // Test ArithmeticException for year > 280,000,000
        Calendar hugeCal = Calendar.getInstance();
        hugeCal.set(Calendar.YEAR, 300000000);
        thrown = false;
        try {
            DateUtils.round(hugeCal, Calendar.YEAR);
        } catch (ArithmeticException e) {
            thrown = true;
        }
        assertTrue(thrown);

        // Test unsupported field in modify
        Calendar normalCal = Calendar.getInstance();
        thrown = false;
        try {
            DateUtils.round(normalCal, 999999);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testIteratorMethods() throws Throwable {
        Date baseDate = new Date();
        Calendar baseCal = Calendar.getInstance();

        assertNotNull(DateUtils.iterator(baseDate, DateUtils.RANGE_MONTH_SUNDAY));
        assertNotNull(DateUtils.iterator(baseDate, DateUtils.RANGE_MONTH_MONDAY));
        assertNotNull(DateUtils.iterator(baseDate, DateUtils.RANGE_WEEK_SUNDAY));
        assertNotNull(DateUtils.iterator(baseDate, DateUtils.RANGE_WEEK_MONDAY));
        assertNotNull(DateUtils.iterator(baseDate, DateUtils.RANGE_WEEK_RELATIVE));
        assertNotNull(DateUtils.iterator(baseDate, DateUtils.RANGE_WEEK_CENTER));

        assertNotNull(DateUtils.iterator(baseCal, DateUtils.RANGE_MONTH_SUNDAY));
        assertNotNull(DateUtils.iterator(baseCal, DateUtils.RANGE_MONTH_MONDAY));
        assertNotNull(DateUtils.iterator(baseCal, DateUtils.RANGE_WEEK_SUNDAY));
        assertNotNull(DateUtils.iterator(baseCal, DateUtils.RANGE_WEEK_MONDAY));
        assertNotNull(DateUtils.iterator(baseCal, DateUtils.RANGE_WEEK_RELATIVE));
        assertNotNull(DateUtils.iterator(baseCal, DateUtils.RANGE_WEEK_CENTER));

        assertNotNull(DateUtils.iterator((Object) baseDate, DateUtils.RANGE_WEEK_SUNDAY));
        assertNotNull(DateUtils.iterator((Object) baseCal, DateUtils.RANGE_WEEK_SUNDAY));

        boolean thrown = false;
        try {
            DateUtils.iterator((Date) null, DateUtils.RANGE_WEEK_SUNDAY);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.iterator((Calendar) null, DateUtils.RANGE_WEEK_SUNDAY);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.iterator((Object) null, DateUtils.RANGE_WEEK_SUNDAY);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.iterator(new Object(), DateUtils.RANGE_WEEK_SUNDAY);
        } catch (ClassCastException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            DateUtils.iterator(baseDate, -999);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testDateIteratorOperations() throws Throwable {
        Calendar start = Calendar.getInstance();
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DATE, 2);

        Iterator it = new DateUtils.DateIterator(start, end);
        assertTrue(it.hasNext());
        assertNotNull(it.next());
        assertTrue(it.hasNext());
        assertNotNull(it.next());

        boolean thrown = false;
        try {
            it.next();
        } catch (NoSuchElementException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            it.remove();
        } catch (UnsupportedOperationException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }
}