package org.apache.commons.lang3.time;

import org.junit.Test;
import static org.junit.Assert.*;

import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class FastDateFormatTest {

    @Test
    public void testGetInstanceDefaults() throws Throwable {
        FastDateFormat format1 = FastDateFormat.getInstance();
        FastDateFormat format2 = FastDateFormat.getInstance();
        assertNotNull(format1);
        assertEquals(format1, format2);
    }

    @Test
    public void testGetInstanceWithPattern() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd");
        assertNotNull(format);
        assertEquals("yyyy-MM-dd", format.getPattern());
    }

    @Test
    public void testGetInstanceWithPatternAndTimeZone() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("GMT");
        FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd", tz);
        assertNotNull(format);
        assertEquals(tz, format.getTimeZone());
    }

    @Test
    public void testGetInstanceWithPatternAndLocale() throws Throwable {
        Locale loc = Locale.US;
        FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd", loc);
        assertNotNull(format);
        assertEquals(loc, format.getLocale());
    }

    @Test
    public void testGetInstanceCompleteArgs() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("EST");
        Locale loc = Locale.US;
        FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss", tz, loc);
        assertNotNull(format);
        assertEquals("yyyy-MM-dd HH:mm:ss", format.getPattern());
        assertEquals(tz, format.getTimeZone());
        assertEquals(loc, format.getLocale());
        assertTrue(format.getTimeZoneOverridesCalendar());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetInstanceNullPattern() throws Throwable {
        FastDateFormat.getInstance(null);
    }

    @Test
    public void testGetDateInstanceStyles() throws Throwable {
        assertNotNull(FastDateFormat.getDateInstance(FastDateFormat.FULL));
        assertNotNull(FastDateFormat.getDateInstance(FastDateFormat.LONG, Locale.US));
        assertNotNull(FastDateFormat.getDateInstance(FastDateFormat.MEDIUM, TimeZone.getDefault()));
        assertNotNull(FastDateFormat.getDateInstance(FastDateFormat.SHORT, TimeZone.getDefault(), Locale.US));
    }

    @Test
    public void testGetTimeInstanceStyles() throws Throwable {
        assertNotNull(FastDateFormat.getTimeInstance(FastDateFormat.FULL));
        assertNotNull(FastDateFormat.getTimeInstance(FastDateFormat.LONG, Locale.US));
        assertNotNull(FastDateFormat.getTimeInstance(FastDateFormat.MEDIUM, TimeZone.getDefault()));
        assertNotNull(FastDateFormat.getTimeInstance(FastDateFormat.SHORT, TimeZone.getDefault(), Locale.US));
    }

    @Test
    public void testGetDateTimeInstanceStyles() throws Throwable {
        assertNotNull(FastDateFormat.getDateTimeInstance(FastDateFormat.SHORT, FastDateFormat.SHORT));
        assertNotNull(FastDateFormat.getDateTimeInstance(FastDateFormat.SHORT, FastDateFormat.SHORT, Locale.US));
        assertNotNull(FastDateFormat.getDateTimeInstance(FastDateFormat.SHORT, FastDateFormat.SHORT, TimeZone.getDefault()));
        assertNotNull(FastDateFormat.getDateTimeInstance(FastDateFormat.SHORT, FastDateFormat.SHORT, TimeZone.getDefault(), Locale.US));
    }

    @Test
    public void testFormatDateAndCalendarAndLong() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd");
        Date date = new Date(0L);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.setTime(date);

        assertNotNull(format.format(date));
        assertNotNull(format.format(cal));
        assertNotNull(format.format(0L));

        StringBuffer buf1 = new StringBuffer();
        assertNotNull(format.format(date, buf1));

        StringBuffer buf2 = new StringBuffer();
        assertNotNull(format.format(cal, buf2));

        StringBuffer buf3 = new StringBuffer();
        assertNotNull(format.format(0L, buf3));
    }

    @Test
    public void testFormatObject() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("yyyy");
        Date date = new Date(0L);
        Calendar cal = Calendar.getInstance();
        Long val = Long.valueOf(0L);

        assertEquals("1970", format.format(date, new StringBuffer(), null).toString());
        
        StringBuffer sb = new StringBuffer();
        format.format(val, sb, null);
        assertNotNull(sb.toString());

        StringBuffer sbCal = new StringBuffer();
        format.format(cal, sbCal, null);
        assertNotNull(sbCal.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatUnknownObject() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("yyyy");
        format.format("Not a date/calendar/long", new StringBuffer(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatNullObject() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("yyyy");
        format.format(null, new StringBuffer(), null);
    }

    @Test
    public void testParseObject() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("yyyy");
        ParsePosition pos = new ParsePosition(0);
        assertNull(format.parseObject("1970", pos));
        assertEquals(0, pos.getIndex());
        assertEquals(0, pos.getErrorIndex());
    }

    @Test
    public void testAccessors() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        Locale loc = Locale.UK;
        FastDateFormat format = FastDateFormat.getInstance("G y M d h H m s S E D F w W a k K z Z", tz, loc);
        
        assertNotNull(format.getPattern());
        assertEquals(tz, format.getTimeZone());
        assertTrue(format.getTimeZoneOverridesCalendar());
        assertEquals(loc, format.getLocale());
        assertTrue(format.getMaxLengthEstimate() > 0);
        assertNotNull(format.toString());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getInstance("yyyy", TimeZone.getDefault(), Locale.US);
        FastDateFormat f2 = FastDateFormat.getInstance("yyyy", TimeZone.getDefault(), Locale.US);
        FastDateFormat f3 = FastDateFormat.getInstance("MM", TimeZone.getDefault(), Locale.US);
        FastDateFormat f4 = FastDateFormat.getInstance("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDateFormat f5 = FastDateFormat.getInstance("yyyy", TimeZone.getDefault(), Locale.UK);

        assertEquals(f1, f1);
        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());
        assertFalse(f1.equals(null));
        assertFalse(f1.equals("SomeString"));
        assertFalse(f1.equals(f3));
        assertFalse(f1.equals(f4));
        assertFalse(f1.equals(f5));
    }

    @Test
    public void testAllPatternComponents() throws Throwable {
        String complexPattern = "GyMdHhmsSEDFwWaKkZ'literal''s'ZZ";
        FastDateFormat format = FastDateFormat.getInstance(complexPattern, TimeZone.getDefault(), Locale.US);
        assertNotNull(format.format(new Date()));
        assertNotNull(format.format(Calendar.getInstance()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPatternComponent() throws Throwable {
        FastDateFormat.getInstance("y x");
    }

    @Test
    public void testPaddedNumberFieldEdgeCases() throws Throwable {
        // pattern with > 3 padding like yyyy, SSS, etc.
        FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 5); // Single digit year
        assertEquals("0005", format.format(cal).substring(0, 4));

        cal.set(Calendar.YEAR, 45); // Two digit year
        assertEquals("0045", format.format(cal).substring(0, 4));

        cal.set(Calendar.YEAR, 999); // Three digit year
        assertEquals("0999", format.format(cal).substring(0, 4));

        cal.set(Calendar.YEAR, 12345); // Large year
        assertEquals("12345", format.format(cal).substring(0, 5));
    }

    @Test
    public void testUnpaddedNumberFieldAndMonth() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("M d H m s");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, 0); // January (1 unpadded)
        cal.set(Calendar.DAY_OF_MONTH, 5);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 8);
        cal.set(Calendar.SECOND, 7);
        String res = format.format(cal);
        assertNotNull(res);
    }

    @Test
    public void testTwelveAndTwentyFourHourFields() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("h H k K");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); // Midnight edge case for 12/24 hour
        String res = format.format(cal);
        assertNotNull(res);

        cal.set(Calendar.HOUR_OF_DAY, 12);
        res = format.format(cal);
        assertNotNull(res);
    }

    @Test
    public void testTimeZoneNameRuleNotForced() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("z zz zzzz", TimeZone.getDefault());
        assertNotNull(format.format(new Date()));
    }

    @Test
    public void testTimeZoneNumberRuleNoColon() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("Z");
        assertNotNull(format.format(new Date()));
    }
}