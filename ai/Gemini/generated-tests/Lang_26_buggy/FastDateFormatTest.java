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
        FastDateFormat f1 = FastDateFormat.getInstance();
        assertNotNull(f1);
        FastDateFormat f2 = FastDateFormat.getInstance();
        assertSame(f1, f2);
    }

    @Test
    public void testGetInstanceWithPattern() throws Throwable {
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd");
        assertNotNull(f);
        assertEquals("yyyy-MM-dd", f.getPattern());
    }

    @Test
    public void testGetInstanceWithPatternAndTimeZone() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("GMT");
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd", tz);
        assertNotNull(f);
        assertEquals(tz, f.getTimeZone());
        assertTrue(f.getTimeZoneOverridesCalendar());
    }

    @Test
    public void testGetInstanceWithPatternAndLocale() throws Throwable {
        Locale locale = Locale.US;
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd", locale);
        assertNotNull(f);
        assertEquals(locale, f.getLocale());
    }

    @Test
    public void testGetInstanceFullParameters() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        Locale locale = Locale.US;
        FastDateFormat f1 = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss", tz, locale);
        FastDateFormat f2 = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss", tz, locale);
        assertSame(f1, f2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetInstanceNullPattern() throws Throwable {
        FastDateFormat.getInstance(null);
    }

    @Test
    public void testGetDateInstanceStyles() throws Throwable {
        assertNotNull(FastDateFormat.getDateInstance(FastDateFormat.SHORT));
        assertNotNull(FastDateFormat.getDateInstance(FastDateFormat.MEDIUM, TimeZone.getDefault()));
        assertNotNull(FastDateFormat.getDateInstance(FastDateFormat.LONG, Locale.getDefault()));
        assertNotNull(FastDateFormat.getDateInstance(FastDateFormat.FULL, TimeZone.getDefault(), Locale.getDefault()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDateInstanceInvalid() throws Throwable {
        FastDateFormat.getDateInstance(999);
    }

    @Test
    public void testGetTimeInstanceStyles() throws Throwable {
        assertNotNull(FastDateFormat.getTimeInstance(FastDateFormat.SHORT));
        assertNotNull(FastDateFormat.getTimeInstance(FastDateFormat.MEDIUM, TimeZone.getDefault()));
        assertNotNull(FastDateFormat.getTimeInstance(FastDateFormat.LONG, Locale.getDefault()));
        assertNotNull(FastDateFormat.getTimeInstance(FastDateFormat.FULL, TimeZone.getDefault(), Locale.getDefault()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetTimeInstanceInvalid() throws Throwable {
        FastDateFormat.getTimeInstance(999);
    }

    @Test
    public void testGetDateTimeInstanceStyles() throws Throwable {
        assertNotNull(FastDateFormat.getDateTimeInstance(FastDateFormat.SHORT, FastDateFormat.SHORT));
        assertNotNull(FastDateFormat.getDateTimeInstance(FastDateFormat.MEDIUM, FastDateFormat.MEDIUM, Locale.getDefault()));
        assertNotNull(FastDateFormat.getDateTimeInstance(FastDateFormat.LONG, FastDateFormat.LONG, TimeZone.getDefault()));
        assertNotNull(FastDateFormat.getDateTimeInstance(FastDateFormat.FULL, FastDateFormat.FULL, TimeZone.getDefault(), Locale.getDefault()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDateTimeInstanceInvalid() throws Throwable {
        FastDateFormat.getDateTimeInstance(999, 999);
    }

    @Test
    public void testFormatMethods() throws Throwable {
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd");
        Date date = new Date(0L);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        assertEquals(f.format(0L), f.format(date));
        assertEquals(f.format(date), f.format(cal));

        StringBuffer sb = new StringBuffer();
        assertEquals(sb, f.format(0L, sb));
        
        StringBuffer sb2 = new StringBuffer();
        assertEquals(sb2, f.format(date, sb2));

        StringBuffer sb3 = new StringBuffer();
        assertEquals(sb3, f.format(cal, sb3));
    }

    @Test
    public void testFormatObject() throws Throwable {
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd");
        Date date = new Date(0L);
        Calendar cal = Calendar.getInstance();
        Long millis = Long.valueOf(0L);

        assertNotNull(f.format(date, new StringBuffer(), null));
        assertNotNull(f.format(cal, new StringBuffer(), null));
        assertNotNull(f.format(millis, new StringBuffer(), null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatInvalidObject() throws Throwable {
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd");
        f.format("NotADate", new StringBuffer(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatNullObject() throws Throwable {
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd");
        f.format(null, new StringBuffer(), null);
    }

    @Test
    public void testParseObject() throws Throwable {
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd");
        ParsePosition pos = new ParsePosition(0);
        assertNull(f.parseObject("2020-01-01", pos));
        assertEquals(0, pos.getIndex());
        assertEquals(0, pos.getErrorIndex());
    }

    @Test
    public void testEqualsAndHashCodeAndToString() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getInstance("yyyy-MM-dd");
        FastDateFormat f2 = FastDateFormat.getInstance("yyyy-MM-dd");
        FastDateFormat f3 = FastDateFormat.getInstance("yyyy/MM/dd");

        assertTrue(f1.equals(f1));
        assertTrue(f1.equals(f2));
        assertFalse(f1.equals(f3));
        assertFalse(f1.equals(null));
        assertFalse(f1.equals("SomeString"));

        assertEquals(f1.hashCode(), f2.hashCode());
        assertEquals("FastDateFormat[yyyy-MM-dd]", f1.toString());
    }

    @Test
    public void testAccessors() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("EST");
        Locale locale = Locale.GERMAN;
        FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd", tz, locale);

        assertEquals("yyyy-MM-dd", f.getPattern());
        assertEquals(tz, f.getTimeZone());
        assertTrue(f.getTimeZoneOverridesCalendar());
        assertEquals(locale, f.getLocale());
        assertTrue(f.getMaxLengthEstimate() > 0);
    }

    @Test
    public void testAllPatternTokens() throws Throwable {
        String pattern = "G y M d h H m s S E D F w W a k K z Z 'literal' ''";
        FastDateFormat f = FastDateFormat.getInstance(pattern, TimeZone.getDefault(), Locale.US);
        assertNotNull(f.format(new Date()));
    }

    @Test
    public void testSingleDigitAndPaddingRules() throws Throwable {
        // Testing various token lengths to cover NumberRules (Unpadded, TwoDigit, Padded)
        FastDateFormat f1 = FastDateFormat.getInstance("y M d H m s S D F w W K k");
        assertNotNull(f1.format(new Date()));

        FastDateFormat f2 = FastDateFormat.getInstance("yyyy MM dd HH mm ss SSS DDD FFF www WWW KKK kkk");
        assertNotNull(f2.format(new Date()));
        
        FastDateFormat f3 = FastDateFormat.getInstance("yyyyy MMMMM");
        assertNotNull(f3.format(new Date()));
    }

    @Test
    public void testTimeZoneNumberRulesAndNames() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getInstance("Z ZZ z zzz zzzz");
        assertNotNull(f1.format(new Date()));
    }

    @Test
    public void testTwelveAndTwentyFourHourEdgeCases() throws Throwable {
        FastDateFormat f = FastDateFormat.getInstance("h H k K");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        assertEquals("12 24 24 0", f.format(cal));

        cal.set(Calendar.HOUR_OF_DAY, 12);
        assertEquals("12 12 12 0", f.format(cal));
    }

    @Test
    public void testTimeZoneForcedCalendarFormatting() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("GMT+2");
        FastDateFormat f = FastDateFormat.getInstance("HH:mm", tz);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        assertNotNull(f.format(cal));
    }
}