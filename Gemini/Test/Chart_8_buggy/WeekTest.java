package org.jfree.data.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;

public class WeekTest {

    @Test
    public void testConstructors() throws Throwable {
        Week w1 = new Week();
        assertNotNull(w1);

        Week w2 = new Week(10, 2005);
        assertEquals(10, w2.getWeek());
        assertEquals(2005, w2.getYearValue());

        Year yearObj = new Year(2006);
        Week w3 = new Week(15, yearObj);
        assertEquals(15, w3.getWeek());
        assertEquals(2006, w3.getYearValue());

        Date now = new Date();
        Week w4 = new Week(now);
        assertNotNull(w4);

        TimeZone tz = TimeZone.getDefault();
        Locale locale = Locale.getDefault();
        Week w5 = new Week(now, tz);
        assertNotNull(w5);

        Week w6 = new Week(now, tz, locale);
        assertNotNull(w6);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWeekIntInt() throws Throwable {
        new Week(0, 2005);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWeekIntYear() throws Throwable {
        new Week(54, new Year(2005));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullDateConstructor1() throws Throwable {
        new Week((Date) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullDateConstructor2() throws Throwable {
        new Week(null, TimeZone.getDefault());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullDateConstructor3() throws Throwable {
        new Week(null, TimeZone.getDefault(), Locale.getDefault());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullZoneConstructor() throws Throwable {
        new Week(new Date(), null, Locale.getDefault());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullLocaleConstructor() throws Throwable {
        new Week(new Date(), TimeZone.getDefault(), null);
    }

    @Test
    public void testDecEnumYearEdgeCases() throws Throwable {
        Calendar cal = Calendar.getInstance();
        cal.set(2004, Calendar.DECEMBER, 31, 23, 59, 59);
        Week w = new Week(cal.getTime());
        assertNotNull(w);

        cal.set(2007, Calendar.JANUARY, 1, 0, 0, 0);
        Week wJan = new Week(cal.getTime());
        assertNotNull(wJan);
    }

    @Test
    public void testGetYear() throws Throwable {
        Week w = new Week(5, 2009);
        Year y = w.getYear();
        assertEquals(2009, y.getYear());
    }

    @Test
    public void testMillisecondMethods() throws Throwable {
        Week w = new Week(1, 2009);
        Calendar cal = Calendar.getInstance();
        w.peg(cal);
        assertTrue(w.getFirstMillisecond() <= w.getLastMillisecond());
        assertTrue(w.getFirstMillisecond(cal) <= w.getLastMillisecond(cal));
    }

    @Test(expected = NullPointerException.class)
    public void testGetFirstMillisecondNullCalendar() throws Throwable {
        Week w = new Week(1, 2009);
        w.getFirstMillisecond(null);
    }

    @Test(expected = NullPointerException.class)
    public void testGetLastMillisecondNullCalendar() throws Throwable {
        Week w = new Week(1, 2009);
        w.getLastMillisecond(null);
    }

    @Test
    public void testPreviousAndNext() throws Throwable {
        Week w = new Week(2, 2009);
        RegularTimePeriod prev = w.previous();
        assertEquals(new Week(1, 2009), prev);

        Week wFirst = new Week(1, 2009);
        RegularTimePeriod prevFirst = wFirst.previous();
        assertNotNull(prevFirst);

        Week wMin = new Week(1, 1900);
        assertNull(wMin.previous());

        Week w51 = new Week(51, 2009);
        RegularTimePeriod next51 = w51.next();
        assertNotNull(next51);

        Week wMaxWeek = new Week(53, 9999);
        assertNull(wMaxWeek.next());

        Week wDec = new Week(52, 2009);
        assertNotNull(wDec.next());
    }

    @Test
    public void testGetSerialIndex() throws Throwable {
        Week w = new Week(1, 2000);
        long expectedIndex = 2000 * 53L + 1;
        assertEquals(expectedIndex, w.getSerialIndex());
    }

    @Test
    public void testToString() throws Throwable {
        Week w = new Week(9, 2002);
        String s = w.toString();
        assertTrue(s.contains("9"));
        assertTrue(s.contains("2002"));
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Week w1 = new Week(5, 2005);
        Week w2 = new Week(5, 2005);
        Week w3 = new Week(6, 2005);
        Week w4 = new Week(5, 2006);

        assertTrue(w1.equals(w1));
        assertTrue(w1.equals(w2));
        assertEquals(w1.hashCode(), w2.hashCode());

        assertFalse(w1.equals(w3));
        assertFalse(w1.equals(w4));
        assertFalse(w1.equals(null));
        assertFalse(w1.equals("Some String"));
    }

    @Test
    public void testCompareTo() throws Throwable {
        Week w1 = new Week(5, 2005);
        Week w2 = new Week(6, 2005);
        Week w3 = new Week(5, 2004);
        Week w4 = new Week(5, 2005);

        assertTrue(w1.compareTo(w2) < 0);
        assertTrue(w1.compareTo(w3) > 0);
        assertEquals(0, w1.compareTo(w4));

        RegularTimePeriod otherTP = new Day();
        assertEquals(0, w1.compareTo(otherTP));

        Object nonTP = new Object();
        assertTrue(w1.compareTo(nonTP) > 0);
    }

    @Test
    public void testParseWeek() throws Throwable {
        assertNull(Week.parseWeek(null));

        Week w1 = Week.parseWeek("2005-W05");
        assertNotNull(w1);
        assertEquals(5, w1.getWeek());
        assertEquals(2005, w1.getYearValue());

        Week w2 = Week.parseWeek("W05-2005");
        assertNotNull(w2);
        assertEquals(5, w2.getWeek());
        assertEquals(2005, w2.getYearValue());

        Week w3 = Week.parseWeek("2005.05");
        assertNotNull(w3);

        Week w4 = Week.parseWeek("05 2005");
        assertNotNull(w4);
    }

    @Test(expected = TimePeriodFormatException.class)
    public void testParseWeekNoSeparator() throws Throwable {
        Week.parseWeek("2005W05");
    }

    @Test(expected = TimePeriodFormatException.class)
    public void testParseWeekInvalidWeekString1() throws Throwable {
        Week.parseWeek("2005-W99");
    }

    @Test(expected = TimePeriodFormatException.class)
    public void testParseWeekInvalidWeekString2() throws Throwable {
        Week.parseWeek("W99-2005");
    }

    @Test(expected = TimePeriodFormatException.class)
    public void testParseWeekInvalidYear() throws Throwable {
        Week.parseWeek("BADYEAR-W05");
    }
}