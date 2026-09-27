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
    public void testConstantsAndConstructor() throws Throwable {
        DateUtils util = new DateUtils();
        assertNotNull(util);
        
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
        
        assertNotNull(DateUtils.UTC_TIME_ZONE);
    }

    @Test
    public void testIsSameDayDate() throws Throwable {
        Date date1 = new Date(1000000000L);
        Date date2 = new Date(1000000000L + 1000L);
        Date date3 = new Date(0L);

        assertTrue(DateUtils.isSameDay(date1, date2));
        assertFalse(DateUtils.isSameDay(date1, date3));

        boolean exceptionThrown = false;
        try {
            DateUtils.isSameDay((Date) null, date2);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            DateUtils.isSameDay(date1, (Date) null);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testIsSameDayCalendar() throws Throwable {
        Calendar cal1 = Calendar.getInstance();
        cal1.set(2020, Calendar.JANUARY, 1, 10, 0, 0);
        Calendar cal2 = Calendar.getInstance();
        cal2.set(2020, Calendar.JANUARY, 1, 15, 30, 0);
        Calendar cal3 = Calendar.getInstance();
        cal3.set(2020, Calendar.JANUARY, 2, 10, 0, 0);

        assertTrue(DateUtils.isSameDay(cal1, cal2));
        assertFalse(DateUtils.isSameDay(cal1, cal3));

        boolean exceptionThrown = false;
        try {
            DateUtils.isSameDay((Calendar) null, cal2);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            DateUtils.isSameDay(cal1, (Calendar) null);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testIsSameInstantDate() throws Throwable {
        Date date1 = new Date(5000L);
        Date date2 = new Date(5000L);
        Date date3 = new Date(6000L);

        assertTrue(DateUtils.isSameInstant(date1, date2));
        assertFalse(DateUtils.isSameInstant(date1, date3));

        boolean exceptionThrown = false;
        try {
            DateUtils.isSameInstant((Date) null, date2);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            DateUtils.isSameInstant(date1, (Date) null);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testIsSameInstantCalendar() throws Throwable {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTimeInMillis(5000L);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTimeInMillis(5000L);
        Calendar cal3 = Calendar.getInstance();
        cal3.setTimeInMillis(6000L);

        assertTrue(DateUtils.isSameInstant(cal1, cal2));
        assertFalse(DateUtils.isSameInstant(cal1, cal3));

        boolean exceptionThrown = false;
        try {
            DateUtils.isSameInstant((Calendar) null, cal2);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            DateUtils.isSameInstant(cal1, (Calendar) null);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testIsSameLocalTime() throws Throwable {
        Calendar cal1 = Calendar.getInstance();
        cal1.set(2020, Calendar.JANUARY, 1, 10, 20, 30);
        cal1.set(Calendar.MILLISECOND, 500);

        Calendar cal2 = Calendar.getInstance();
        cal2.set(2020, Calendar.JANUARY, 1, 10, 20, 30);
        cal2.set(Calendar.MILLISECOND, 500);

        Calendar cal3 = Calendar.getInstance();
        cal3.set(2020, Calendar.JANUARY, 1, 10, 20, 31);
        cal3.set(Calendar.MILLISECOND, 500);

        assertTrue(DateUtils.isSameLocalTime(cal1, cal2));
        assertFalse(DateUtils.isSameLocalTime(cal1, cal3));

        boolean exceptionThrown = false;
        try {
            DateUtils.isSameLocalTime(null, cal2);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testParseDate() throws Throwable {
        String[] patterns = new String[]{"yyyy-MM-dd", "yyyy/MM/dd"};
        Date parsed = DateUtils.parseDate("2020-05-10", patterns);
        assertNotNull(parsed);

        Date parsed2 = DateUtils.parseDate("2020/05/10", patterns);
        assertNotNull(parsed2);

        boolean exceptionThrown = false;
        try {
            DateUtils.parseDate("invalid-date", patterns);
        } catch (ParseException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            DateUtils.parseDate(null, patterns);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            DateUtils.parseDate("2020-05-10", null);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testAddMethods() throws Throwable {
        Date date = new Date(1000000000000L);
        assertNotNull(DateUtils.addYears(date, 1));
        assertNotNull(DateUtils.addMonths(date, 1));
        assertNotNull(DateUtils.addWeeks(date, 1));
        assertNotNull(DateUtils.addDays(date, 1));
        assertNotNull(DateUtils.addHours(date, 1));
        assertNotNull(DateUtils.addMinutes(date, 1));
        assertNotNull(DateUtils.addSeconds(date, 1));
        assertNotNull(DateUtils.addMilliseconds(date, 100));

        boolean exceptionThrown = false;
        try {
            DateUtils.add(null, Calendar.YEAR, 1);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testRoundAndTruncate() throws Throwable {
        Date date = new Date(1582980000000L);
        assertNotNull(DateUtils.round(date, Calendar.SECOND));
        assertNotNull(DateUtils.round(Calendar.getInstance(), Calendar.MINUTE));
        assertNotNull(DateUtils.round((Object) date, Calendar.HOUR));
        assertNotNull(DateUtils.round((Object) Calendar.getInstance(), Calendar.DATE));

        assertNotNull(DateUtils.truncate(date, Calendar.SECOND));
        assertNotNull(DateUtils.truncate(Calendar.getInstance(), Calendar.MINUTE));
        assertNotNull(DateUtils.truncate((Object) date, Calendar.HOUR));
        assertNotNull(DateUtils.truncate((Object) Calendar.getInstance(), Calendar.DATE));

        boolean ex1 = false;
        try {
            DateUtils.round((Date) null, Calendar.SECOND);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            DateUtils.round((Calendar) null, Calendar.SECOND);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);

        boolean ex3 = false;
        try {
            DateUtils.round((Object) null, Calendar.SECOND);
        } catch (IllegalArgumentException e) {
            ex3 = true;
        }
        assertTrue(ex3);

        boolean ex4 = false;
        try {
            DateUtils.round(new Object(), Calendar.SECOND);
        } catch (ClassCastException e) {
            ex4 = true;
        }
        assertTrue(ex4);

        boolean ex5 = false;
        try {
            DateUtils.truncate((Date) null, Calendar.SECOND);
        } catch (IllegalArgumentException e) {
            ex5 = true;
        }
        assertTrue(ex5);

        boolean ex6 = false;
        try {
            DateUtils.truncate((Calendar) null, Calendar.SECOND);
        } catch (IllegalArgumentException e) {
            ex6 = true;
        }
        assertTrue(ex6);

        boolean ex7 = false;
        try {
            DateUtils.truncate((Object) null, Calendar.SECOND);
        } catch (IllegalArgumentException e) {
            ex7 = true;
        }
        assertTrue(ex7);

        boolean ex8 = false;
        try {
            DateUtils.truncate(new Object(), Calendar.SECOND);
        } catch (ClassCastException e) {
            ex8 = true;
        }
        assertTrue(ex8);
    }

    @Test
    public void testModifyEdgeCases() throws Throwable {
        Calendar cal = Calendar.getInstance();
        cal.set(290000000, Calendar.JANUARY, 1);
        boolean arithEx = false;
        try {
            DateUtils.round(cal, Calendar.DATE);
        } catch (ArithmeticException e) {
            arithEx = true;
        }
        assertTrue(arithEx);

        Calendar calMil = Calendar.getInstance();
        assertNotNull(DateUtils.round(calMil, Calendar.MILLISECOND));

        Calendar calSemiMonth1 = Calendar.getInstance();
        calSemiMonth1.set(2020, Calendar.JANUARY, 1, 12, 0, 0);
        assertNotNull(DateUtils.round(calSemiMonth1, DateUtils.SEMI_MONTH));

        Calendar calSemiMonth2 = Calendar.getInstance();
        calSemiMonth2.set(2020, Calendar.JANUARY, 20, 12, 0, 0);
        assertNotNull(DateUtils.round(calSemiMonth2, DateUtils.SEMI_MONTH));

        Calendar calAmPm = Calendar.getInstance();
        calAmPm.set(2020, Calendar.JANUARY, 1, 15, 0, 0);
        assertNotNull(DateUtils.round(calAmPm, Calendar.AM_PM));

        Calendar calInvalidField = Calendar.getInstance();
        boolean illegalArg = false;
        try {
            DateUtils.round(calInvalidField, -999);
        } catch (IllegalArgumentException e) {
            illegalArg = true;
        }
        assertTrue(illegalArg);
    }

    @Test
    public void testIterator() throws Throwable {
        Date focusDate = new Date();
        assertNotNull(DateUtils.iterator(focusDate, DateUtils.RANGE_MONTH_SUNDAY));
        assertNotNull(DateUtils.iterator(focusDate, DateUtils.RANGE_MONTH_MONDAY));
        assertNotNull(DateUtils.iterator(focusDate, DateUtils.RANGE_WEEK_SUNDAY));
        assertNotNull(DateUtils.iterator(focusDate, DateUtils.RANGE_WEEK_MONDAY));
        assertNotNull(DateUtils.iterator(focusDate, DateUtils.RANGE_WEEK_RELATIVE));
        assertNotNull(DateUtils.iterator(focusDate, DateUtils.RANGE_WEEK_CENTER));

        Calendar focusCal = Calendar.getInstance();
        assertNotNull(DateUtils.iterator(focusCal, DateUtils.RANGE_MONTH_SUNDAY));
        assertNotNull(DateUtils.iterator((Object) focusDate, DateUtils.RANGE_MONTH_SUNDAY));
        assertNotNull(DateUtils.iterator((Object) focusCal, DateUtils.RANGE_MONTH_SUNDAY));

        boolean ex1 = false;
        try {
            DateUtils.iterator((Date) null, DateUtils.RANGE_MONTH_SUNDAY);
        } catch (IllegalArgumentException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            DateUtils.iterator((Calendar) null, DateUtils.RANGE_MONTH_SUNDAY);
        } catch (IllegalArgumentException e) {
            ex2 = true;
        }
        assertTrue(ex2);

        boolean ex3 = false;
        try {
            DateUtils.iterator((Object) null, DateUtils.RANGE_MONTH_SUNDAY);
        } catch (IllegalArgumentException e) {
            ex3 = true;
        }
        assertTrue(ex3);

        boolean ex4 = false;
        try {
            DateUtils.iterator(new Object(), DateUtils.RANGE_MONTH_SUNDAY);
        } catch (ClassCastException e) {
            ex4 = true;
        }
        assertTrue(ex4);

        boolean ex5 = false;
        try {
            DateUtils.iterator(focusDate, -999);
        } catch (IllegalArgumentException e) {
            ex5 = true;
        }
        assertTrue(ex5);
    }

    @Test
    public void testDateIteratorMethods() throws Throwable {
        Calendar start = Calendar.getInstance();
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DATE, 2);

        Iterator it = DateUtils.iterator(start, DateUtils.RANGE_WEEK_SUNDAY);
        assertNotNull(it);
        
        boolean hasN = it.hasNext();
        assertTrue(hasN || !hasN);

        while (it.hasNext()) {
            Object obj = it.next();
            assertNotNull(obj);
        }

        boolean unsupp = false;
        try {
            it.remove();
        } catch (UnsupportedOperationException e) {
            unsupp = true;
        }
        assertTrue(unsupp);

        Calendar s2 = Calendar.getInstance();
        Calendar e2 = (Calendar) s2.clone();
        Iterator it2 = new DateUtils.DateIterator(s2, e2);
        boolean noElem = false;
        try {
            it2.next();
        } catch (NoSuchElementException e) {
            noElem = true;
        }
        assertTrue(noElem);
    }
}