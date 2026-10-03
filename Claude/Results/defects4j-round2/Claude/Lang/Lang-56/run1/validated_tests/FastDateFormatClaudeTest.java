package org.apache.commons.lang.time;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class FastDateFormatClaudeTest {

    private TimeZone gmt;

    @Before
    public void setUp() throws Throwable {
        gmt = TimeZone.getTimeZone("GMT");
    }

    private Calendar buildCalendar(TimeZone tz, int year, int month, int day) {
        Calendar cal = new GregorianCalendar(tz);
        cal.clear();
        cal.set(year, month, day);
        return cal;
    }

    private Calendar buildCalendarDayOfYear(TimeZone tz, int year, int dayOfYear) {
        Calendar cal = new GregorianCalendar(tz);
        cal.clear();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.DAY_OF_YEAR, dayOfYear);
        return cal;
    }

    private Calendar buildCalendarHour(TimeZone tz, int hourOfDay) {
        Calendar cal = new GregorianCalendar(tz);
        cal.clear();
        cal.set(2005, Calendar.JANUARY, 1, hourOfDay, 0, 0);
        return cal;
    }

    // getInstance() with no args must use the default SimpleDateFormat pattern
    @Test
    public void testGetInstance_default_usesDefaultPattern() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance();
        String expected = new SimpleDateFormat().toPattern();
        assertEquals(expected, fdf.getPattern());
    }

    // getInstance(pattern) stores the pattern verbatim
    @Test
    public void testGetInstance_withPattern_returnsPatternStored() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy-MM-dd");
        assertEquals("yyyy-MM-dd", fdf.getPattern());
    }

    // null pattern must throw IllegalArgumentException
    @Test
    public void testGetInstance_nullPattern_throwsIllegalArgumentException() throws Throwable {
        try {
            FastDateFormat.getInstance((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // caching branch: same args return the same cached instance
    @Test
    public void testGetInstance_sameArguments_returnsCachedInstance() throws Throwable {
        FastDateFormat a = FastDateFormat.getInstance("yyyy", gmt, Locale.US);
        FastDateFormat b = FastDateFormat.getInstance("yyyy", gmt, Locale.US);
        assertSame(a, b);
    }

    // timeZone != null branch sets mTimeZoneForced = true
    @Test
    public void testGetInstance_withTimeZone_setsForcedFlagTrue() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy", gmt);
        assertTrue(fdf.getTimeZoneOverridesCalendar());
        assertEquals(gmt, fdf.getTimeZone());
    }

    // timeZone == null branch leaves mTimeZoneForced = false, uses default zone
    @Test
    public void testGetInstance_withoutTimeZone_notForced() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy");
        assertFalse(fdf.getTimeZoneOverridesCalendar());
        assertEquals(TimeZone.getDefault(), fdf.getTimeZone());
    }

    // locale != null branch stores the given locale
    @Test
    public void testGetInstance_withLocale_setsLocale() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy", Locale.GERMANY);
        assertEquals(Locale.GERMANY, fdf.getLocale());
    }

    // getDateInstance(SHORT,...) must format like java.text.DateFormat SHORT style
    @Test
    public void testGetDateInstance_shortStyleLocaleUS_matchesDateFormatOutput() throws Throwable {
        Date date = buildCalendar(gmt, 2005, Calendar.MARCH, 15).getTime();
        FastDateFormat fdf = FastDateFormat.getDateInstance(FastDateFormat.SHORT, gmt, Locale.US);
        DateFormat jdf = DateFormat.getDateInstance(DateFormat.SHORT, Locale.US);
        jdf.setTimeZone(gmt);
        assertEquals(jdf.format(date), fdf.format(date));
    }

    // getTimeInstance(SHORT,...) must format like java.text.DateFormat SHORT style
    @Test
    public void testGetTimeInstance_shortStyleLocaleUS_matchesDateFormatOutput() throws Throwable {
        Date date = buildCalendarHour(gmt, 14).getTime();
        FastDateFormat fdf = FastDateFormat.getTimeInstance(FastDateFormat.SHORT, gmt, Locale.US);
        DateFormat jdf = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US);
        jdf.setTimeZone(gmt);
        assertEquals(jdf.format(date), fdf.format(date));
    }

    // getDateTimeInstance(SHORT,SHORT,...) must format like java.text.DateFormat SHORT/SHORT
    @Test
    public void testGetDateTimeInstance_shortStyleLocaleUS_matchesDateFormatOutput() throws Throwable {
        Date date = buildCalendarHour(gmt, 9).getTime();
        FastDateFormat fdf = FastDateFormat.getDateTimeInstance(
                FastDateFormat.SHORT, FastDateFormat.SHORT, gmt, Locale.US);
        DateFormat jdf = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.US);
        jdf.setTimeZone(gmt);
        assertEquals(jdf.format(date), fdf.format(date));
    }

    // constructor: null pattern throws IllegalArgumentException
    @Test
    public void testConstructor_nullPattern_throwsIllegalArgumentException() throws Throwable {
        try {
            new FastDateFormat(null, gmt, Locale.US);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // constructor: null timeZone/locale fall back to system defaults, not forced
    @Test
    public void testConstructor_nullTimeZoneLocale_usesSystemDefaults() throws Throwable {
        FastDateFormat fdf = new FastDateFormat("yyyy", null, null);
        assertEquals(TimeZone.getDefault(), fdf.getTimeZone());
        assertEquals(Locale.getDefault(), fdf.getLocale());
        assertFalse(fdf.getTimeZoneOverridesCalendar());
    }

    // init() must populate a positive max length estimate from the parsed rules
    @Test
    public void testInit_populatesMaxLengthEstimatePositive() throws Throwable {
        FastDateFormat fdf = new FastDateFormat("yyyy-MM-dd", gmt, Locale.US);
        fdf.init();
        assertTrue(fdf.getMaxLengthEstimate() > 0);
    }

    // parsePattern must produce one rule per distinct token group
    @Test
    public void testParsePattern_mixedPattern_ruleCountMatchesTokenCount() throws Throwable {
        FastDateFormat fdf = new FastDateFormat("yyyy-MM-dd", gmt, Locale.US);
        List rules = fdf.parsePattern();
        assertEquals(5, rules.size());
    }

    // parseToken: a run of identical letters is returned as one token
    @Test
    public void testParseToken_letterRun_returnsFullRunWithFinalIndex() throws Throwable {
        FastDateFormat fdf = new FastDateFormat("yyyy", gmt, Locale.US);
        int[] idx = new int[]{0};
        String token = fdf.parseToken("yyyy", idx);
        assertEquals("yyyy", token);
        assertEquals(3, idx[0]);
    }

    // parseToken: quoted literal text is parsed, closing quote not included in buffer text
    @Test
    public void testParseToken_quotedLiteral_tokenExcludesClosingQuote() throws Throwable {
        FastDateFormat fdf = new FastDateFormat("yyyy", gmt, Locale.US);
        int[] idx = new int[]{0};
        String token = fdf.parseToken("'ab'", idx);
        assertEquals("'ab", token);
        assertEquals(4, idx[0]);
    }

    // parseToken: '' escape sequence is kept as a literal quote character
    @Test
    public void testParseToken_escapedQuote_literalQuoteCharKept() throws Throwable {
        FastDateFormat fdf = new FastDateFormat("yyyy", gmt, Locale.US);
        int[] idx = new int[]{0};
        String token = fdf.parseToken("''", idx);
        assertEquals("''", token);
        assertEquals(2, idx[0]);
    }





    // control: exactly 'yy' (2 letters) is correctly truncated to the last two digits
    @Test
    public void testFormat_patternYY_twoDigitYear_truncatedCorrectly() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yy", gmt, Locale.US);
        Calendar cal = buildCalendar(gmt, 2005, Calendar.JANUARY, 1);
        assertEquals("05", fdf.format(cal));
    }

    // PaddedNumberField: value 100-999 branch, pads with exactly one leading zero to reach 4 digits
    @Test
    public void testFormat_patternYYYY_midValueYear_paddedWithOneZero() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy", gmt, Locale.US);
        Calendar cal = buildCalendar(gmt, 250, Calendar.JANUARY, 1);
        assertEquals("0250", fdf.format(cal));
    }

    // PaddedNumberField: value >= 1000 branch, no padding needed
    @Test
    public void testFormat_patternYYYY_fullYear_noPadding() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy", gmt, Locale.US);
        Calendar cal = buildCalendar(gmt, 2005, Calendar.JANUARY, 1);
        assertEquals("2005", fdf.format(cal));
    }

    // 'M' single letter: unpadded month number
    @Test
    public void testFormat_patternM_unpaddedMonth() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("M", gmt, Locale.US);
        Calendar cal = buildCalendar(gmt, 2005, Calendar.JANUARY, 1);
        assertEquals("1", fdf.format(cal));
    }

    // 'MM': two digit padded month number
    @Test
    public void testFormat_patternMM_twoDigitMonth() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("MM", gmt, Locale.US);
        Calendar cal = buildCalendar(gmt, 2005, Calendar.JANUARY, 1);
        assertEquals("01", fdf.format(cal));
    }

    // 'MMM': short month name from locale symbols
    @Test
    public void testFormat_patternMMM_shortMonthName() throws Throwable {
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        FastDateFormat fdf = FastDateFormat.getInstance("MMM", gmt, Locale.US);
        Calendar cal = buildCalendar(gmt, 2005, Calendar.JANUARY, 1);
        assertEquals(symbols.getShortMonths()[Calendar.JANUARY], fdf.format(cal));
    }

    // 'MMMM': full month name from locale symbols
    @Test
    public void testFormat_patternMMMM_fullMonthName() throws Throwable {
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        FastDateFormat fdf = FastDateFormat.getInstance("MMMM", gmt, Locale.US);
        Calendar cal = buildCalendar(gmt, 2005, Calendar.JANUARY, 1);
        assertEquals(symbols.getMonths()[Calendar.JANUARY], fdf.format(cal));
    }

    // UnpaddedNumberField: value >= 100 branch via day-of-year
    @Test
    public void testFormat_patternD_dayOfYearOver100_unpadded() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("D", gmt, Locale.US);
        Calendar cal = buildCalendarDayOfYear(gmt, 2005, 200);
        assertEquals("200", fdf.format(cal));
    }

    // TwoDigitNumberField: value >= 100 branch, raw (not truncated) number via day-of-year
    @Test
    public void testFormat_patternDD_dayOfYearOver100_twoDigitFieldRaw() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("DD", gmt, Locale.US);
        Calendar cal = buildCalendarDayOfYear(gmt, 2005, 200);
        assertEquals("200", fdf.format(cal));
    }

    // 'h': twelve-hour clock, midnight (HOUR==0) must display as 12
    @Test
    public void testFormat_patternh_midnightBecomesTwelve() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("h", gmt, Locale.US);
        Calendar cal = buildCalendarHour(gmt, 0);
        assertEquals("12", fdf.format(cal));
    }

    // 'k': twenty-four-hour clock, midnight (HOUR_OF_DAY==0) must display as 24
    @Test
    public void testFormat_patternk_midnightBecomesTwentyFour() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("k", gmt, Locale.US);
        Calendar cal = buildCalendarHour(gmt, 0);
        assertEquals("24", fdf.format(cal));
    }

    // 'Z' single letter: RFC822 offset with no colon
    @Test
    public void testFormat_patternZ_noColonOffset() throws Throwable {
        TimeZone india = TimeZone.getTimeZone("GMT+05:30");
        FastDateFormat fdf = FastDateFormat.getInstance("Z", india, Locale.US);
        Calendar cal = buildCalendar(india, 2005, Calendar.JANUARY, 1);
        assertEquals("+0530", fdf.format(cal));
    }

    // 'ZZ': ISO8601 offset with colon
    @Test
    public void testFormat_patternZZ_colonOffset() throws Throwable {
        TimeZone india = TimeZone.getTimeZone("GMT+05:30");
        FastDateFormat fdf = FastDateFormat.getInstance("ZZ", india, Locale.US);
        Calendar cal = buildCalendar(india, 2005, Calendar.JANUARY, 1);
        assertEquals("+05:30", fdf.format(cal));
    }

    // 'G': era designator text from locale symbols
    @Test
    public void testFormat_patternG_eraDesignator() throws Throwable {
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        FastDateFormat fdf = FastDateFormat.getInstance("G", gmt, Locale.US);
        Calendar cal = buildCalendar(gmt, 2005, Calendar.JANUARY, 1);
        assertEquals(symbols.getEras()[GregorianCalendar.AD], fdf.format(cal));
    }

    // 'E' (<4 letters): short weekday name from locale symbols
    @Test
    public void testFormat_patternE_shortWeekday() throws Throwable {
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        FastDateFormat fdf = FastDateFormat.getInstance("E", gmt, Locale.US);
        Calendar cal = buildCalendar(gmt, 2005, Calendar.JANUARY, 1);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        assertEquals(symbols.getShortWeekdays()[dow], fdf.format(cal));
    }

    // format(Object,StringBuffer,FieldPosition): Date branch delegates to format(Date)
    @Test
    public void testFormatObject_withDate_delegatesToDateFormat() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy-MM-dd", gmt, Locale.US);
        Date date = buildCalendar(gmt, 2005, Calendar.JANUARY, 1).getTime();
        StringBuffer buf = new StringBuffer();
        fdf.format((Object) date, buf, new FieldPosition(0));
        assertEquals(fdf.format(date), buf.toString());
    }

    // format(Object,StringBuffer,FieldPosition): Calendar branch delegates to format(Calendar)
    @Test
    public void testFormatObject_withCalendar_delegatesToCalendarFormat() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy-MM-dd", gmt, Locale.US);
        Calendar cal = buildCalendar(gmt, 2005, Calendar.JANUARY, 1);
        StringBuffer buf = new StringBuffer();
        fdf.format((Object) cal, buf, new FieldPosition(0));
        assertEquals(fdf.format(cal), buf.toString());
    }

    // format(Object,StringBuffer,FieldPosition): Long branch delegates to format(long)
    @Test
    public void testFormatObject_withLong_delegatesToLongFormat() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy-MM-dd", gmt, Locale.US);
        long millis = buildCalendar(gmt, 2005, Calendar.JANUARY, 1).getTimeInMillis();
        StringBuffer buf = new StringBuffer();
        fdf.format((Object) new Long(millis), buf, new FieldPosition(0));
        assertEquals(fdf.format(millis), buf.toString());
    }

    // format(Object,...) with unsupported type must throw IllegalArgumentException
    @Test
    public void testFormatObject_withUnsupportedType_throwsIllegalArgumentException() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy-MM-dd", gmt, Locale.US);
        try {
            fdf.format((Object) "not a date", new StringBuffer(), new FieldPosition(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // format(Object,...) with null must throw IllegalArgumentException mentioning the null case
    @Test
    public void testFormatObject_withNull_throwsIllegalArgumentExceptionMessageContainsNull() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy-MM-dd", gmt, Locale.US);
        try {
            fdf.format((Object) null, new StringBuffer(), new FieldPosition(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("null"));
        }
    }

    // format(Calendar,StringBuffer): forced time zone must override the calendar's own zone
    @Test
    public void testFormatCalendar_timeZoneForced_overridesCalendarZone() throws Throwable {
        TimeZone calendarZone = TimeZone.getTimeZone("GMT+01:00");
        TimeZone forcedZone = TimeZone.getTimeZone("GMT+05:30");
        FastDateFormat fdf = FastDateFormat.getInstance("Z", forcedZone, Locale.US);
        Calendar cal = buildCalendar(calendarZone, 2005, Calendar.JANUARY, 1);
        assertEquals("+0530", fdf.format(cal));
    }

    // parseObject is documented as unsupported: must return null and reset positions to zero
    @Test
    public void testParseObject_alwaysReturnsNullAndSetsPositionsToZero() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy-MM-dd", gmt, Locale.US);
        ParsePosition pos = new ParsePosition(5);
        Object result = fdf.parseObject("2005-01-01", pos);
        assertNull(result);
        assertEquals(0, pos.getIndex());
        assertEquals(0, pos.getErrorIndex());
    }

    // equals: same pattern/timeZone/locale -> true even for distinct instances
    @Test
    public void testEquals_samePatternZoneLocale_true() throws Throwable {
        FastDateFormat a = new FastDateFormat("yyyy", gmt, Locale.US);
        FastDateFormat b = new FastDateFormat("yyyy", gmt, Locale.US);
        assertTrue(a.equals(b));
    }

    // equals: different pattern -> false
    @Test
    public void testEquals_differentPattern_false() throws Throwable {
        FastDateFormat a = new FastDateFormat("yyyy", gmt, Locale.US);
        FastDateFormat b = new FastDateFormat("MM", gmt, Locale.US);
        assertFalse(a.equals(b));
    }

    // toString must contain the pattern text for debugging purposes
    @Test
    public void testToString_containsPatternText() throws Throwable {
        FastDateFormat fdf = FastDateFormat.getInstance("yyyy-MM-dd", gmt, Locale.US);
        assertTrue(fdf.toString().contains("yyyy-MM-dd"));
    }
}
