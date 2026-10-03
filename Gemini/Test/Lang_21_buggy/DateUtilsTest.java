package org.apache.commons.lang3.time;

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
    public void testConstructor() throws Throwable {
        DateUtils utils = new DateUtils();
        assertNotNull(utils);
    }

    @Test
    public void testIsSameDayDate() throws Throwable {
        Date date1 = new Date(1000000L);
        Date date2 = new Date(1000000L + 5000L);
        assertTrue(DateUtils.isSameDay(date1, date2));

        Date date3 = new Date(0L);
        Date date4 = new Date(100000000000L);
        assertFalse(DateUtils.isSameDay(date3, date4));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSameDayDateNull1() throws Throwable {
        DateUtils.isSameDay((Date) null, new Date());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSameDayDateNull2() throws Throwable {
        DateUtils.isSameDay(new Date(), (Date) null);
    }

    @Test
    public void testIsSameDayCalendar() throws Throwable {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = (Calendar) cal1.clone();
        assertTrue(DateUtils.isSameDay(cal1, cal2));

        Calendar cal3 = Calendar.getInstance();
        cal3.add(Calendar.DAY_OF_YEAR, 1);
        assertFalse(DateUtils.isSameDay(cal1, cal3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSameDayCalendarNull1() throws Throwable {
        DateUtils.isSameDay((Calendar) null, Calendar.getInstance());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSameDayCalendarNull2() throws Throwable {
        DateUtils.isSameDay(Calendar.getInstance(), (Calendar) null);
    }

    @Test
    public void testIsSameInstantDate() throws Throwable {
        Date d1 = new Date(123456L);
        Date d2 = new Date(123456L);
        Date d3 = new Date(654321L);

        assertTrue(DateUtils.isSameInstant(d1, d2));
        assertFalse(DateUtils.isSameInstant(d1, d3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSameInstantDateNull1() throws Throwable {
        DateUtils.isSameInstant((Date) null, new Date());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSameInstantDateNull2() throws Throwable {
        DateUtils.isSameInstant(new Date(), (Date) null);
    }

    @Test
    public void testIsSameInstantCalendar() throws Throwable {
        Calendar c1 = Calendar.getInstance();
        c1.setTimeInMillis(123456L);
        Calendar c2 = Calendar.getInstance();
        c2.setTimeInMillis(123456L);
        Calendar c3 = Calendar.getInstance();
        c3.setTimeInMillis(654321L);

        assertTrue(DateUtils.isSameInstant(c1, c2));
        assertFalse(DateUtils.isSameInstant(c1, c3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSameInstantCalendarNull1() throws Throwable {
        DateUtils.isSameInstant((Calendar) null, Calendar.getInstance());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSameInstantCalendarNull2() throws Throwable {
        DateUtils.isSameInstant(Calendar.getInstance(), (Calendar) null);
    }

    @Test
    public void testIsSameLocalTime() throws Throwable {
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = (Calendar) c1.clone();
        assertTrue(DateUtils.isSameLocalTime(c1, c2));

        Calendar c3 = Calendar.getInstance();
        c3.add(Calendar.MILLISECOND, 1);
        assertFalse(DateUtils.isSameLocalTime(c1, c3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSameLocalTimeNull1() throws Throwable {
        DateUtils.isSameLocalTime(null, Calendar.getInstance());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSameLocalTimeNull2() throws Throwable {
        DateUtils.isSameLocalTime(Calendar.getInstance(), null);
    }

    @Test
    public void testParseDate() throws Throwable {
        String[] patterns = new String[]{"yyyy-MM-dd", "yyyy/MM/dd"};
        Date parsed = DateUtils.parseDate("2020-05-01", patterns);
        assertNotNull(parsed);

        String[] patternsZZ = new String[]{"yyyy-MM-dd'T'HH:mm:ssZZ"};
        Date parsedZZ = DateUtils.parseDate("2020-05-01T12:00:00+01:00", patternsZZ);
        assertNotNull(parsedZZ);
    }

    @Test(expected = ParseException.class)
    public void testParseDateFail() throws Throwable {
        String[] patterns = new String[]{"yyyy-MM-dd"};
        DateUtils.parseDate("invalid-date", patterns);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseDateNull() throws Throwable {
        DateUtils.parseDate(null, "yyyy-MM-dd");
    }

    @Test
    public void testParseDateStrictly() throws Throwable {
        String[] patterns = new String[]{"yyyy-MM-dd"};
        Date parsed = DateUtils.parseDateStrictly("2020-02-28", patterns);
        assertNotNull(parsed);
    }

    @Test(expected = ParseException.class)
    public void testParseDateStrictlyFail() throws Throwable {
        String[] patterns = new String[]{"yyyy-MM-dd"};
        DateUtils.parseDateStrictly("2020-02-30", patterns);
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
        assertNotNull(DateUtils.addYears(base, -1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullDate() throws Throwable {
        DateUtils.addDays(null, 1);
    }

    @Test
    public void testSetMethods() throws Throwable {
        Date base = new Date(1000000000000L);
        assertNotNull(DateUtils.setYears(base, 2025));
        assertNotNull(DateUtils.setMonths(base, 5));
        assertNotNull(DateUtils.setDays(base, 15));
        assertNotNull(DateUtils.setHours(base, 10));
        assertNotNull(DateUtils.setMinutes(base, 30));
        assertNotNull(DateUtils.setSeconds(base, 45));
        assertNotNull(DateUtils.setMilliseconds(base, 500));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetNullDate() throws Throwable {
        DateUtils.setDays(null, 1);
    }

    @Test
    public void testToCalendar() throws Throwable {
        Date date = new Date();
        Calendar cal = DateUtils.toCalendar(date);
        assertNotNull(cal);
        assertEquals(date.getTime(), cal.getTime().getTime());
    }

    @Test
    public void testRoundAndTruncateAndCeiling() throws Throwable {
        Date date = new Date(1588334401231L); // 2020-05-01 13:40:01.231
        
        assertNotNull(DateUtils.round(date, Calendar.HOUR));
        assertNotNull(DateUtils.round(date, Calendar.MONTH));
        assertNotNull(DateUtils.round(Calendar.getInstance(), Calendar.HOUR));
        assertNotNull(DateUtils.round((Object) date, Calendar.HOUR));
        assertNotNull(DateUtils.round((Object) Calendar.getInstance(), Calendar.HOUR));

        assertNotNull(DateUtils.truncate(date, Calendar.HOUR));
        assertNotNull(DateUtils.truncate(Calendar.getInstance(), Calendar.HOUR));
        assertNotNull(DateUtils.truncate((Object) date, Calendar.HOUR));
        assertNotNull(DateUtils.truncate((Object) Calendar.getInstance(), Calendar.HOUR));

        assertNotNull(DateUtils.ceiling(date, Calendar.HOUR));
        assertNotNull(DateUtils.ceiling(Calendar.getInstance(), Calendar.HOUR));
        assertNotNull(DateUtils.ceiling((Object) date, Calendar.HOUR));
        assertNotNull(DateUtils.ceiling((Object) Calendar.getInstance(), Calendar.HOUR));

        // Test specific fields for rounding/truncating branches
        assertNotNull(DateUtils.round(date, Calendar.MILLISECOND));
        assertNotNull(DateUtils.round(date, Calendar.SECOND));
        assertNotNull(DateUtils.round(date, Calendar.MINUTE));
        assertNotNull(DateUtils.round(date, Calendar.DATE));
        assertNotNull(DateUtils.round(date, DateUtils.SEMI_MONTH));
        assertNotNull(DateUtils.round(date, Calendar.AM_PM));
        assertNotNull(DateUtils.round(date, Calendar.YEAR));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRoundNullDate() throws Throwable {
        DateUtils.round((Date) null, Calendar.HOUR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRoundNullCalendar() throws Throwable {
        DateUtils.round((Calendar) null, Calendar.HOUR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRoundNullObject() throws Throwable {
        DateUtils.round((Object) null, Calendar.HOUR);
    }

    @Test(expected = ClassCastException.class)
    public void testRoundInvalidObject() throws Throwable {
        DateUtils.round(new Object(), Calendar.HOUR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTruncateNullDate() throws Throwable {
        DateUtils.truncate((Date) null, Calendar.HOUR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTruncateNullCalendar() throws Throwable {
        DateUtils.truncate((Calendar) null, Calendar.HOUR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTruncateNullObject() throws Throwable {
        DateUtils.truncate((Object) null, Calendar.HOUR);
    }

    @Test(expected = ClassCastException.class)
    public void testTruncateInvalidObject() throws Throwable {
        DateUtils.truncate(new Object(), Calendar.HOUR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCeilingNullDate() throws Throwable {
        DateUtils.ceiling((Date) null, Calendar.HOUR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCeilingNullCalendar() throws Throwable {
        DateUtils.ceiling((Calendar) null, Calendar.HOUR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCeilingNullObject() throws Throwable {
        DateUtils.ceiling((Object) null, Calendar.HOUR);
    }

    @Test(expected = ClassCastException.class)
    public void testCeilingInvalidObject() throws Throwable {
        DateUtils.ceiling(new Object(), Calendar.HOUR);
    }

    @Test(expected = ArithmeticException.class)
    public void testModifyTooLargeYear() throws Throwable {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 300000000);
        DateUtils.truncate(cal, Calendar.MONTH);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testModifyUnsupportedField() throws Throwable {
        Calendar cal = Calendar.getInstance();
        DateUtils.round(cal, 99999);
    }

    @Test
    public void testIterator() throws Throwable {
        Date date = new Date();
        Iterator<Calendar> it1 = DateUtils.iterator(date, DateUtils.RANGE_MONTH_SUNDAY);
        assertNotNull(it1);

        Iterator<Calendar> it2 = DateUtils.iterator(date, DateUtils.RANGE_MONTH_MONDAY);
        assertNotNull(it2);

        Iterator<Calendar> it3 = DateUtils.iterator(date, DateUtils.RANGE_WEEK_SUNDAY);
        assertNotNull(it3);

        Iterator<Calendar> it4 = DateUtils.iterator(date, DateUtils.RANGE_WEEK_MONDAY);
        assertNotNull(it4);

        Iterator<Calendar> it5 = DateUtils.iterator(date, DateUtils.RANGE_WEEK_RELATIVE);
        assertNotNull(it5);

        Iterator<Calendar> it6 = DateUtils.iterator(date, DateUtils.RANGE_WEEK_CENTER);
        assertNotNull(it6);

        Calendar cal = Calendar.getInstance();
        Iterator<?> itObj = DateUtils.iterator((Object) cal, DateUtils.RANGE_WEEK_SUNDAY);
        assertNotNull(itObj);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIteratorNullDate() throws Throwable {
        DateUtils.iterator((Date) null, DateUtils.RANGE_MONTH_SUNDAY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIteratorNullCalendar() throws Throwable {
        DateUtils.iterator((Calendar) null, DateUtils.RANGE_MONTH_SUNDAY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIteratorNullObject() throws Throwable {
        DateUtils.iterator((Object) null, DateUtils.RANGE_MONTH_SUNDAY);
    }

    @Test(expected = ClassCastException.class)
    public void testIteratorInvalidObject() throws Throwable {
        DateUtils.iterator(new Object(), DateUtils.RANGE_MONTH_SUNDAY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIteratorInvalidStyle() throws Throwable {
        DateUtils.iterator(new Date(), -999);
    }

    @Test
    public void testGetFragmentMethods() throws Throwable {
        Date date = new Date();
        Calendar cal = Calendar.getInstance();

        assertTrue(DateUtils.getFragmentInMilliseconds(date, Calendar.SECOND) >= 0);
        assertTrue(DateUtils.getFragmentInSeconds(date, Calendar.MINUTE) >= 0);
        assertTrue(DateUtils.getFragmentInMinutes(date, Calendar.HOUR_OF_DAY) >= 0);
        assertTrue(DateUtils.getFragmentInHours(date, Calendar.DAY_OF_YEAR) >= 0);
        assertTrue(DateUtils.getFragmentInDays(date, Calendar.MONTH) >= 0);

        assertTrue(DateUtils.getFragmentInMilliseconds(cal, Calendar.SECOND) >= 0);
        assertTrue(DateUtils.getFragmentInSeconds(cal, Calendar.MINUTE) >= 0);
        assertTrue(DateUtils.getFragmentInMinutes(cal, Calendar.HOUR_OF_DAY) >= 0);
        assertTrue(DateUtils.getFragmentInHours(cal, Calendar.DAY_OF_YEAR) >= 0);
        assertTrue(DateUtils.getFragmentInDays(cal, Calendar.MONTH) >= 0);
        
        // Testing YEAR and DATE fragments
        assertTrue(DateUtils.getFragmentInDays(cal, Calendar.YEAR) >= 0);
        assertTrue(DateUtils.getFragmentInDays(cal, Calendar.DATE) >= 0);
        assertTrue(DateUtils.getFragmentInHours(cal, Calendar.YEAR) >= 0);
        assertTrue(DateUtils.getFragmentInMinutes(cal, Calendar.YEAR) >= 0);
        assertTrue(DateUtils.getFragmentInSeconds(cal, Calendar.YEAR) >= 0);
        assertTrue(DateUtils.getFragmentInMilliseconds(cal, Calendar.YEAR) >= 0);
        assertTrue(DateUtils.getFragmentInMilliseconds(cal, Calendar.MILLISECOND) == 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetFragmentNullDate() throws Throwable {
        DateUtils.getFragmentInDays((Date) null, Calendar.YEAR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetFragmentNullCalendar() throws Throwable {
        DateUtils.getFragmentInDays((Calendar) null, Calendar.YEAR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetFragmentUnsupported() throws Throwable {
        DateUtils.getFragmentInDays(Calendar.getInstance(), -999);
    }

    @Test
    public void testTruncatedEqualsAndCompareTo() throws Throwable {
        Date d1 = new Date();
        Date d2 = new Date(d1.getTime() + 10L);
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = (Calendar) c1.clone();

        assertTrue(DateUtils.truncatedEquals(c1, c2, Calendar.DAY_OF_YEAR));
        assertTrue(DateUtils.truncatedEquals(d1, d2, Calendar.DAY_OF_YEAR));

        assertEquals(0, DateUtils.truncatedCompareTo(c1, c2, Calendar.DAY_OF_YEAR));
        assertEquals(0, DateUtils.truncatedCompareTo(d1, d2, Calendar.DAY_OF_YEAR));
    }

    @Test
    public void testDateIteratorMethods() throws Throwable {
        Calendar start = Calendar.getInstance();
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DATE, 3);

        Iterator<Calendar> iterator = DateUtils.iterator(start, DateUtils.RANGE_WEEK_SUNDAY);
        // Using DateIterator directly via public iterator method or testing behavior
        assertNotNull(iterator);
        
        Calendar s = Calendar.getInstance();
        Calendar e = (Calendar) s.clone();
        e.add(Calendar.DATE, 2);
        DateUtils.DateIterator dateIter = new DateUtils.DateIterator(s, e);
        
        assertTrue(dateIter.hasNext());
        assertNotNull(dateIter.next());
        assertTrue(dateIter.hasNext());
        assertNotNull(dateIter.next());

        try {
            dateIter.next();
            // Might throw NoSuchElementException if fully consumed
        } catch (NoSuchElementException ex) {
            // Expected
        }

        try {
            dateIter.remove();
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {
            // Expected
        }
    }
}