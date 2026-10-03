package org.apache.commons.lang.time;

import junit.framework.TestCase;

import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class FastDateFormatTest extends TestCase {

    public void testGetInstance() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getInstance();
        assertNotNull(f1);

        FastDateFormat f2 = FastDateFormat.getInstance("yyyy-MM-dd");
        assertNotNull(f2);
        assertEquals("yyyy-MM-dd", f2.getPattern());

        TimeZone tz = TimeZone.getTimeZone("GMT");
        FastDateFormat f3 = FastDateFormat.getInstance("yyyy-MM-dd", tz);
        assertNotNull(f3);
        assertEquals(tz, f3.getTimeZone());

        Locale loc = Locale.US;
        FastDateFormat f4 = FastDateFormat.getInstance("yyyy-MM-dd", loc);
        assertNotNull(f4);
        assertEquals(loc, f4.getLocale());

        FastDateFormat f5 = FastDateFormat.getInstance("yyyy-MM-dd", tz, loc);
        assertNotNull(f5);
        assertEquals(tz, f5.getTimeZone());
        assertEquals(loc, f5.getLocale());

        FastDateFormat f6 = FastDateFormat.getInstance("yyyy-MM-dd", tz, loc);
        assertSame(f5, f6);
    }

    public void testGetInstanceEdgeCases() throws Throwable {
        try {
            FastDateFormat.getInstance(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("pattern"));
        }

        try {
            FastDateFormat.getInstance("INVALID_PATTERN_XYZ_%");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testGetDateInstance() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getDateInstance(FastDateFormat.SHORT);
        assertNotNull(f1);

        FastDateFormat f2 = FastDateFormat.getDateInstance(FastDateFormat.MEDIUM, Locale.US);
        assertNotNull(f2);

        TimeZone tz = TimeZone.getTimeZone("UTC");
        FastDateFormat f3 = FastDateFormat.getDateInstance(FastDateFormat.LONG, tz);
        assertNotNull(f3);

        FastDateFormat f4 = FastDateFormat.getDateInstance(FastDateFormat.FULL, tz, Locale.US);
        assertNotNull(f4);
    }

    public void testGetTimeInstance() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getTimeInstance(FastDateFormat.SHORT);
        assertNotNull(f1);

        FastDateFormat f2 = FastDateFormat.getTimeInstance(FastDateFormat.MEDIUM, Locale.US);
        assertNotNull(f2);

        TimeZone tz = TimeZone.getTimeZone("UTC");
        FastDateFormat f3 = FastDateFormat.getTimeInstance(FastDateFormat.LONG, tz);
        assertNotNull(f3);

        FastDateFormat f4 = FastDateFormat.getTimeInstance(FastDateFormat.FULL, tz, Locale.US);
        assertNotNull(f4);
    }

    public void testGetDateTimeInstance() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getDateTimeInstance(FastDateFormat.SHORT, FastDateFormat.SHORT);
        assertNotNull(f1);

        FastDateFormat f2 = FastDateFormat.getDateTimeInstance(FastDateFormat.MEDIUM, FastDateFormat.MEDIUM, Locale.US);
        assertNotNull(f2);

        TimeZone tz = TimeZone.getTimeZone("UTC");
        FastDateFormat f3 = FastDateFormat.getDateTimeInstance(FastDateFormat.LONG, FastDateFormat.LONG, tz);
        assertNotNull(f3);

        FastDateFormat f4 = FastDateFormat.getDateTimeInstance(FastDateFormat.FULL, FastDateFormat.FULL, tz, Locale.US);
        assertNotNull(f4);
    }

    public void testFormattingMethods() throws Throwable {
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS Z", TimeZone.getTimeZone("GMT"), Locale.US);
        
        Date date = new Date(1000000000000L);
        String formatted1 = f.format(date);
        assertNotNull(formatted1);

        String formatted2 = f.format(1000000000000L);
        assertEquals(formatted1, formatted2);

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.setTime(date);
        String formatted3 = f.format(cal);
        assertEquals(formatted1, formatted3);

        StringBuffer buf1 = new StringBuffer();
        StringBuffer res1 = f.format(date, buf1);
        assertSame(buf1, res1);
        assertEquals(formatted1, res1.toString());

        StringBuffer buf2 = new StringBuffer();
        StringBuffer res2 = f.format(1000000000000L, buf2);
        assertSame(buf2, res2);
        assertEquals(formatted1, res2.toString());

        StringBuffer buf3 = new StringBuffer();
        StringBuffer res3 = f.format(cal, buf3);
        assertSame(buf3, res3);
        assertEquals(formatted1, res3.toString());
    }

    public void testFormatObjectAndParseObject() throws Throwable {
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd");
        Date date = new Date(0L);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        Long millis = new Long(0L);

        assertNotNull(f.format(date, new StringBuffer(), new FieldPosition(0)).toString());
        assertNotNull(f.format(cal, new StringBuffer(), new FieldPosition(0)).toString());
        assertNotNull(f.format(millis, new StringBuffer(), new FieldPosition(0)).toString());

        try {
            f.format("NotADateOrCalendarOrLong", new StringBuffer(), new FieldPosition(0));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown class"));
        }

        try {
            f.format(null, new StringBuffer(), new FieldPosition(0));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("<null>"));
        }

        ParsePosition pos = new ParsePosition(0);
        assertNull(f.parseObject("2020-01-01", pos));
        assertEquals(0, pos.getIndex());
        assertEquals(0, pos.getErrorIndex());
    }

    public void testPatternsAndRulesVariants() throws Throwable {
        String[] patterns = {
            "G y M d h H m s S E D F w W a k K z Z ZZ",
            "yy MM MMM MMMM d dd ddd h hh H HH m mm s ss S SS SSS E EE EEE EEEE",
            "yyyy/MM/dd'T'HH:mm:ss.SSSZ",
            "''literal''",
            "'single quote '' inside'",
            "H", "HH", "H", "k", "kk", "K", "KK", "h", "hh",
            "d", "dd", "D", "DD", "F", "w", "ww", "W",
            "M", "MM", "MMM", "MMMM",
            "y", "yy", "yyyy",
            "Z", "ZZ", "z", "zzzz"
        };

        for (int i = 0; i < patterns.length; i++) {
            FastDateFormat f = FastDateFormat.getInstance(patterns[i], TimeZone.getDefault(), Locale.US);
            assertNotNull(f);
            assertNotNull(f.format(new Date()));
            assertNotNull(f.toString());
        }
    }

    public void testEqualsAndHashCode() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getInstance("yyyy-MM-dd", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDateFormat f2 = FastDateFormat.getInstance("yyyy-MM-dd", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDateFormat f3 = FastDateFormat.getInstance("yyyy-MM-dd", TimeZone.getTimeZone("PST"), Locale.US);
        FastDateFormat f4 = FastDateFormat.getInstance("yyyy-MM-dd", TimeZone.getTimeZone("GMT"), Locale.UK);
        FastDateFormat f5 = FastDateFormat.getInstance("yyyy-MM", TimeZone.getTimeZone("GMT"), Locale.US);

        assertEquals(f1, f1);
        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());

        assertFalse(f1.equals(null));
        assertFalse(f1.equals("SomeString"));
        assertFalse(f1.equals(f3));
        assertFalse(f1.equals(f4));
        assertFalse(f1.equals(f5));
    }

    public void testAccessors() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("GMT+2");
        Locale loc = Locale.GERMANY;
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd", tz, loc);

        assertEquals("yyyy-MM-dd", f.getPattern());
        assertEquals(tz, f.getTimeZone());
        assertTrue(f.getTimeZoneOverridesCalendar());
        assertEquals(loc, f.getLocale());
        assertTrue(f.getMaxLengthEstimate() > 0);

        FastDateFormat fDefault = FastDateFormat.getInstance("yyyy-MM-dd");
        assertFalse(fDefault.getTimeZoneOverridesCalendar());
    }

    public void testTimeZoneDisplayAndNumberRules() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("America/New_York");
        FastDateFormat f1 = FastDateFormat.getInstance("z Z ZZ", tz, Locale.US);
        assertNotNull(f1.format(new Date()));

        TimeZone tzNeg = TimeZone.getTimeZone("GMT-05:00");
        FastDateFormat fNeg = FastDateFormat.getInstance("Z ZZ", tzNeg, Locale.US);
        String resNeg = fNeg.format(new Date());
        assertNotNull(resNeg);

        TimeZone tzZero = TimeZone.getTimeZone("GMT");
        FastDateFormat fZero = FastDateFormat.getInstance("Z", tzZero, Locale.US);
        assertEquals("+0000", fZero.format(new Date()));
    }

    public void testPaddedAndUnpaddedFieldsEdgeCases() throws Throwable {
        FastDateFormat f = FastDateFormat.getInstance("yyyy DDD F w W H h m s S", TimeZone.getDefault(), Locale.US);
        Calendar cal = new GregorianCalendar(2000, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 5);
        assertEquals("2000 001 6 52 1 00 12 00 00 005", f.format(cal));

        Calendar calMax = new GregorianCalendar(2000, Calendar.DECEMBER, 31, 23, 59, 59);
        calMax.set(Calendar.MILLISECOND, 999);
        assertNotNull(f.format(calMax));
    }

    public void testTwelveAndTwentyFourHourFields() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getInstance("h K H k", TimeZone.getDefault(), Locale.US);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        String formatted = f1.format(cal);
        assertNotNull(formatted);

        cal.set(Calendar.HOUR_OF_DAY, 12);
        assertNotNull(f1.format(cal));
    }
}