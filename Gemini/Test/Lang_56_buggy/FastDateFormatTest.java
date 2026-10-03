package org.apache.commons.lang.time;

import org.junit.Test;
import static org.junit.Assert.*;

import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class FastDateFormatTest {

    @Test
    public void testGetInstanceDefaults() throws Throwable {
        FastDateFormat format1 = FastDateFormat.getInstance();
        FastDateFormat format2 = FastDateFormat.getInstance();
        assertNotNull(format1);
        assertSame(format1, format2);
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
        assertTrue(format.getTimeZoneOverridesCalendar());
    }

    @Test
    public void testGetInstanceWithPatternAndLocale() throws Throwable {
        Locale locale = Locale.US;
        FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd", locale);
        assertNotNull(format);
        assertEquals(locale, format.getLocale());
    }

    @Test
    public void testGetInstanceFullParameters() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        Locale locale = Locale.UK;
        FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss", tz, locale);
        assertNotNull(format);
        assertEquals("yyyy-MM-dd HH:mm:ss", format.getPattern());
        assertEquals(tz, format.getTimeZone());
        assertEquals(locale, format.getLocale());
        assertTrue(format.getTimeZoneOverridesCalendar());
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
    public void testGetTimeZoneDisplay() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("America/New_York");
        String display = FastDateFormat.getTimeZoneDisplay(tz, false, TimeZone.SHORT, Locale.US);
        assertNotNull(display);
        
        String displayDst = FastDateFormat.getTimeZoneDisplay(tz, true, TimeZone.LONG, Locale.US);
        assertNotNull(displayDst);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullPattern() throws Throwable {
        new FastDateFormat(null, null, null);
    }

    @Test
    public void testFormatMethods() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("GMT"), Locale.US);
        Date date = new Date(0L);
        
        String str1 = format.format(date);
        assertNotNull(str1);
        
        String str2 = format.format(0L);
        assertEquals(str1, str2);

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.setTime(date);
        String str3 = format.format(cal);
        assertEquals(str1, str3);

        StringBuffer buf = new StringBuffer();
        StringBuffer bufRes1 = format.format(date, buf);
        assertSame(buf, bufRes1);

        StringBuffer buf2 = new StringBuffer();
        StringBuffer bufRes2 = format.format(0L, buf2);
        assertSame(buf2, bufRes2);

        StringBuffer buf3 = new StringBuffer();
        StringBuffer bufRes3 = format.format(cal, buf3);
        assertSame(buf3, bufRes3);
    }

    @Test
    public void testFormatGeneralObjectAndParseObject() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("yyyy");
        Date date = new Date(0L);
        StringBuffer buf = new StringBuffer();
        
        StringBuffer res = format.format((Object) date, buf, new FieldPosition(0));
        assertNotNull(res);

        Calendar cal = Calendar.getInstance();
        StringBuffer resCal = format.format((Object) cal, buf, new FieldPosition(0));
        assertNotNull(resCal);

        StringBuffer resLong = format.format((Object) Long.valueOf(0L), buf, new FieldPosition(0));
        assertNotNull(resLong);

        ParsePosition pos = new ParsePosition(0);
        assertNull(format.parseObject("2000", pos));
        assertEquals(0, pos.getIndex());
        assertEquals(0, pos.getErrorIndex());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatUnknownObject() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("yyyy");
        format.format(new Object(), new StringBuffer(), new FieldPosition(0));
    }

    @Test
    public void testAccessorsAndBasics() throws Throwable {
        FastDateFormat format1 = FastDateFormat.getInstance("yyyy-MM-dd", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDateFormat format2 = FastDateFormat.getInstance("yyyy-MM-dd", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDateFormat format3 = FastDateFormat.getInstance("yyyy-MM-dd", TimeZone.getTimeZone("UTC"), Locale.US);

        assertEquals("yyyy-MM-dd", format1.getPattern());
        assertEquals(TimeZone.getTimeZone("GMT"), format1.getTimeZone());
        assertTrue(format1.getTimeZoneOverridesCalendar());
        assertEquals(Locale.US, format1.getLocale());
        assertTrue(format1.getMaxLengthEstimate() > 0);

        assertTrue(format1.equals(format2));
        assertFalse(format1.equals(format3));
        assertFalse(format1.equals(new Object()));

        assertEquals(format1.hashCode(), format2.hashCode());
        assertNotNull(format1.toString());
    }

    @Test
    public void testPatternTokensCoverage() throws Throwable {
        String complexPattern = "G y yy yyy yyyy M MM MMM MMMM d h H m s S E D F w W a k K z Z ZZ '' 'literal'";
        FastDateFormat format = FastDateFormat.getInstance(complexPattern, Locale.US);
        assertNotNull(format);
        assertNotNull(format.format(new Date()));
    }

    @Test
    public void testPaddedNumberFieldBoundaries() throws Throwable {
        FastDateFormat format = FastDateFormat.getInstance("yyyy");
        assertNotNull(format.format(100L));
        assertNotNull(format.format(1000L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPatternComponent() throws Throwable {
        FastDateFormat.getInstance("x");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClassCastExceptionInDateInstance() throws Throwable {
        FastDateFormat.getDateInstance(FastDateFormat.FULL, new Locale("invalid", "LOCALE"));
    }
}