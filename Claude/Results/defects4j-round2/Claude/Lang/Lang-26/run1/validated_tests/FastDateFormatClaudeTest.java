package org.apache.commons.lang3.time;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;
import static org.junit.Assert.*;

public class FastDateFormatClaudeTest {

    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    private static final TimeZone GMT_PLUS_2 = TimeZone.getTimeZone("GMT+02:00");
    private static final Locale US = Locale.US;

    private Date buildDate(int year, int month, int day, int hour, int minute, int second, TimeZone tz) {
        Calendar cal = new GregorianCalendar(tz);
        cal.clear();
        cal.set(year, month, day, hour, minute, second);
        return cal.getTime();
    }

    private Calendar buildCalendar(int year, int month, int day, int hour, int minute, int second, TimeZone tz) {
        Calendar cal = new GregorianCalendar(tz);
        cal.clear();
        cal.set(year, month, day, hour, minute, second);
        return cal;
    }

    // Covers getInstance(): default pattern matches SimpleDateFormat's default pattern
    @Test
    public void testGetInstance_defaultPattern_matchesSimpleDateFormatDefault() throws Throwable {
        String expected = new SimpleDateFormat().toPattern();
        assertEquals(expected, FastDateFormat.getInstance().getPattern());
    }

    // Covers getInstance(String): pattern is stored and returned unchanged
    @Test
    public void testGetInstance_withPattern_returnsSamePattern() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss");
        assertEquals("yyyy-MM-dd'T'HH:mm:ss", fmt.getPattern());
    }

    // Covers constructor branch: pattern == null throws IllegalArgumentException
    @Test
    public void testGetInstance_nullPattern_throwsIllegalArgumentException() throws Throwable {
        try {
            FastDateFormat.getInstance((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // Covers parsePattern() default switch branch: unknown letter throws IllegalArgumentException
    @Test
    public void testGetInstance_invalidPatternChar_throwsIllegalArgumentException() throws Throwable {
        try {
            FastDateFormat.getInstance("Q").format(new Date(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // Covers getDateInstance/getTimeInstance: pattern matches java.text.DateFormat's own pattern
    @Test
    public void testGetDateInstance_and_getTimeInstance_patternMatchesStandardDateFormat() throws Throwable {
        SimpleDateFormat sdfDate = (SimpleDateFormat) DateFormat.getDateInstance(DateFormat.SHORT, US);
        assertEquals(sdfDate.toPattern(), FastDateFormat.getDateInstance(FastDateFormat.SHORT, US).getPattern());
        SimpleDateFormat sdfTime = (SimpleDateFormat) DateFormat.getTimeInstance(DateFormat.SHORT, US);
        assertEquals(sdfTime.toPattern(), FastDateFormat.getTimeInstance(FastDateFormat.SHORT, US).getPattern());
    }

    // Covers getDateTimeInstance: pattern matches java.text.DateFormat's own pattern
    @Test
    public void testGetDateTimeInstance_patternMatchesStandardDateFormat() throws Throwable {
        SimpleDateFormat sdfDT = (SimpleDateFormat) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, US);
        String actual = FastDateFormat.getDateTimeInstance(FastDateFormat.SHORT, FastDateFormat.SHORT, US).getPattern();
        assertEquals(sdfDT.toPattern(), actual);
    }

    // Covers 'y'/'M'/'d' basic numeric+text parsing via format(Date)
    @Test
    public void testFormatDate_yyyyMMdd_basic() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy-MM-dd", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("2023-01-15", fmt.format(date));
    }

    // Covers 'H','m','s' single-digit values zero-padded to two digits
    @Test
    public void testFormatDate_HHmmss_paddedSingleDigits() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("HH:mm:ss", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 5, 3, 9, GMT);
        assertEquals("05:03:09", fmt.format(date));
    }

    // Covers 'H' (hour in day 0..23): midnight stays raw 0, zero-padded
    @Test
    public void testFormatDate_HH_midnight_rawZeroPadded() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("HH", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("00", fmt.format(date));
    }

    // Covers 'h' (hour in am/pm 1..12): midnight (HOUR=0) converts to 12
    @Test
    public void testFormatDate_h_midnight_convertsToTwelve() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("h", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("12", fmt.format(date));
    }

    // Covers 'h' with non-zero HOUR: raw value used, unpadded
    @Test
    public void testFormatDate_h_afternoon_unpaddedHour() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("h", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 13, 0, 0, GMT);
        assertEquals("1", fmt.format(date));
    }

    // Covers 'K' (hour in am/pm 0..11): midnight stays raw 0, no conversion
    @Test
    public void testFormatDate_K_midnight_rawZero() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("K", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("0", fmt.format(date));
    }

    // Covers 'k' (hour in day 1..24): midnight (HOUR_OF_DAY=0) converts to 24
    @Test
    public void testFormatDate_k_midnight_convertsToTwentyFour() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("k", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("24", fmt.format(date));
    }

    // Covers 'k' with non-zero HOUR_OF_DAY: raw value used
    @Test
    public void testFormatDate_k_afternoon_rawValue() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("k", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 13, 0, 0, GMT);
        assertEquals("13", fmt.format(date));
    }

    // Covers 'y' tokenLen<4: two digit year (TwoDigitYearField)
    @Test
    public void testFormatDate_yy_twoDigitYear() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yy", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("23", fmt.format(date));
    }

    // Covers 'y' tokenLen>=4: full year, plus overflow beyond the padded size
    @Test
    public void testFormatDate_yyyy_fullYearAndOverflow() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy", GMT, US);
        assertEquals("2023", fmt.format(buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT)));
        assertEquals("12345", fmt.format(buildDate(12345, Calendar.JANUARY, 15, 0, 0, 0, GMT)));
    }

    // Boundary: 'y' with exactly 3 letters is still two-digit per contract (<4 letters)
    @Test
    public void testFormatDate_yyy_threeYLetters_stillTwoDigit() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyy", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("23", fmt.format(date));
    }

    // Covers 'M' tokenLen>=4: full month name (TextField with months)
    @Test
    public void testFormatDate_MMMM_fullMonthName() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("MMMM", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("January", fmt.format(date));
    }

    // Covers 'M' tokenLen==3: short month name (TextField with shortMonths)
    @Test
    public void testFormatDate_MMM_shortMonthName() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("MMM", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("Jan", fmt.format(date));
    }

    // Covers 'M' tokenLen==2: two digit month (TwoDigitMonthField), January and December
    @Test
    public void testFormatDate_MM_twoDigitMonth() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("MM", GMT, US);
        assertEquals("01", fmt.format(buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT)));
        assertEquals("12", fmt.format(buildDate(2023, Calendar.DECEMBER, 15, 0, 0, 0, GMT)));
    }

    // Covers 'M' tokenLen==1: unpadded month number (UnpaddedMonthField)
    @Test
    public void testFormatDate_M_unpaddedMonth() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("M", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("1", fmt.format(date));
    }

    // Covers 'E' full vs short weekday text field (2023-01-15 is a Sunday)
    @Test
    public void testFormatDate_EEEE_and_EEE_weekdayNames() throws Throwable {
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("Sunday", FastDateFormat.getInstance("EEEE", GMT, US).format(date));
        assertEquals("Sun", FastDateFormat.getInstance("EEE", GMT, US).format(date));
    }

    // Covers 'a' am/pm marker text field, both branches
    @Test
    public void testFormatDate_a_amAndPmMarkers() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("a", GMT, US);
        assertEquals("AM", fmt.format(buildDate(2023, Calendar.JANUARY, 15, 10, 0, 0, GMT)));
        assertEquals("PM", fmt.format(buildDate(2023, Calendar.JANUARY, 15, 14, 0, 0, GMT)));
    }

    // Covers 'G' era designator text field
    @Test
    public void testFormatDate_G_eraDesignator() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("G", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("AD", fmt.format(date));
    }

    // Boundary: selectNumberRule default branch with size exactly 3 (PaddedNumberField)
    @Test
    public void testFormatDate_sss_threeDigitPaddedSeconds() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("sss", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 5, GMT);
        assertEquals("005", fmt.format(date));
    }

    // Covers 'Z' tokenLen==1: RFC822 style, no colon
    @Test
    public void testFormatDate_Z_singleLetter_noColon() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("Z", GMT_PLUS_2, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT_PLUS_2);
        assertEquals("+0200", fmt.format(date));
    }

    // Covers 'Z' tokenLen>=2: ISO8601 style, with colon
    @Test
    public void testFormatDate_ZZ_doubleLetter_withColon() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("ZZ", GMT_PLUS_2, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT_PLUS_2);
        assertEquals("+02:00", fmt.format(date));
    }

    // Covers literal text token (quoted text containing non-letter trailing char) via StringLiteral
    @Test
    public void testFormatDate_literalQuotedText() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("'Date:' yyyy", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("Date: 2023", fmt.format(date));
    }

    // Covers escaped '' inside literal token, producing a single quote char (CharacterLiteral)
    @Test
    public void testFormatDate_escapedQuote_producesSingleQuoteChar() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("''", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        assertEquals("'", fmt.format(date));
    }

    // Covers format(Calendar): when timeZone not forced, calendar's own time zone is used
    @Test
    public void testFormatCalendar_notForced_usesCalendarOwnTimeZone() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("HH", null, US);
        Calendar cal = buildCalendar(2023, Calendar.JANUARY, 15, 5, 0, 0, GMT);
        assertEquals("05", fmt.format(cal));
    }

    // Covers format(Calendar): when timeZone forced, formatter's time zone overrides calendar's
    @Test
    public void testFormatCalendar_forced_overridesCalendarTimeZone() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("HH", GMT_PLUS_2, US);
        Calendar cal = buildCalendar(2023, Calendar.JANUARY, 15, 5, 0, 0, GMT);
        assertEquals("07", fmt.format(cal));
    }

    // Covers format(long): equivalent to format(new Date(millis))
    @Test
    public void testFormatLong_matchesFormatDate() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 5, 3, 9, GMT);
        assertEquals(fmt.format(date), fmt.format(date.getTime()));
    }

    // Covers format(Date,StringBuffer) and format(long,StringBuffer): same buffer instance returned
    @Test
    public void testFormatDateAndLongIntoStringBuffer_returnsSameBufferInstance() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        StringBuffer buf1 = new StringBuffer();
        assertSame(buf1, fmt.format(date, buf1));
        StringBuffer buf2 = new StringBuffer();
        assertSame(buf2, fmt.format(date.getTime(), buf2));
        assertEquals("2023", buf1.toString());
    }

    // Covers format(Calendar,StringBuffer): same buffer instance returned
    @Test
    public void testFormatCalendarIntoStringBuffer_returnsSameBufferInstance() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy", GMT, US);
        Calendar cal = buildCalendar(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        StringBuffer buf = new StringBuffer();
        assertSame(buf, fmt.format(cal, buf));
        assertEquals("2023", buf.toString());
    }

    // Covers format(Object,StringBuffer,FieldPosition) delegation for Date, Calendar and Long
    @Test
    public void testFormatObject_delegatesForDateCalendarLong() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy-MM-dd", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        Calendar cal = buildCalendar(2023, Calendar.JANUARY, 15, 0, 0, 0, GMT);
        StringBuffer b1 = new StringBuffer();
        fmt.format((Object) date, b1, new FieldPosition(0));
        assertEquals(fmt.format(date), b1.toString());
        StringBuffer b2 = new StringBuffer();
        fmt.format((Object) cal, b2, new FieldPosition(0));
        assertEquals(fmt.format(cal), b2.toString());
        StringBuffer b3 = new StringBuffer();
        fmt.format((Object) Long.valueOf(date.getTime()), b3, new FieldPosition(0));
        assertEquals(fmt.format(date.getTime()), b3.toString());
    }

    // Covers format(Object,...) default branch: unsupported type throws IllegalArgumentException
    @Test
    public void testFormatObject_unsupportedType_throwsIllegalArgumentException() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy");
        try {
            fmt.format((Object) "not a date", new StringBuffer(), new FieldPosition(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unknown class"));
        }
    }

    // Covers format(Object,...) null branch: throws IllegalArgumentException mentioning null
    @Test
    public void testFormatObject_null_throwsIllegalArgumentException() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy");
        try {
            fmt.format((Object) null, new StringBuffer(), new FieldPosition(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("null"));
        }
    }

    // Covers parseObject: parsing unsupported, always returns null and resets position to 0
    @Test
    public void testParseObject_returnsNullAndResetsPosition() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy");
        ParsePosition pos = new ParsePosition(5);
        Object result = fmt.parseObject("2023", pos);
        assertNull(result);
        assertEquals(0, pos.getIndex());
        assertEquals(0, pos.getErrorIndex());
    }

    // Covers getPattern(): returns exactly the pattern supplied at construction
    @Test
    public void testGetPattern_returnsConstructedPattern() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("dd/MM/yyyy", GMT, US);
        assertEquals("dd/MM/yyyy", fmt.getPattern());
    }

    // Covers getTimeZone/getTimeZoneOverridesCalendar/getLocale for explicit vs null (default) args
    @Test
    public void testAccessors_timeZoneAndLocale_explicitVsDefault() throws Throwable {
        FastDateFormat forced = FastDateFormat.getInstance("yyyy", GMT_PLUS_2, US);
        assertEquals(GMT_PLUS_2.getID(), forced.getTimeZone().getID());
        assertTrue(forced.getTimeZoneOverridesCalendar());
        assertEquals(US, forced.getLocale());
        FastDateFormat notForced = FastDateFormat.getInstance("yyyyMM", null, null);
        assertEquals(TimeZone.getDefault().getID(), notForced.getTimeZone().getID());
        assertFalse(notForced.getTimeZoneOverridesCalendar());
        assertEquals(Locale.getDefault(), notForced.getLocale());
    }

    // Covers getMaxLengthEstimate(): actual formatted length never exceeds the estimate
    @Test
    public void testGetMaxLengthEstimate_actualLengthWithinEstimate() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss", GMT, US);
        Date date = buildDate(2023, Calendar.JANUARY, 15, 5, 3, 9, GMT);
        String formatted = fmt.format(date);
        assertTrue(formatted.length() <= fmt.getMaxLengthEstimate());
    }

    // Covers equals(): true when pattern, timeZone and locale all match
    @Test
    public void testEquals_trueForEqualConstructionParams() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getInstance("yyyy-DD", GMT, US);
        FastDateFormat f2 = FastDateFormat.getInstance("yyyy-DD", GMT, US);
        assertTrue(f1.equals(f2));
    }

    // Covers equals(): false for different pattern and for non-FastDateFormat argument
    @Test
    public void testEquals_falseForDifferentPatternOrType() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getInstance("yyyy-DD", GMT, US);
        FastDateFormat f3 = FastDateFormat.getInstance("yyyy/DD", GMT, US);
        assertFalse(f1.equals(f3));
        assertFalse(f1.equals("not a FastDateFormat"));
    }

    // Covers hashCode(): equal objects (per equals) produce equal hash codes
    @Test
    public void testHashCode_equalForEqualObjects() throws Throwable {
        FastDateFormat f1 = FastDateFormat.getInstance("yyyy-DD", GMT, US);
        FastDateFormat f2 = FastDateFormat.getInstance("yyyy-DD", GMT, US);
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    // Covers toString(): debugging string includes the pattern
    @Test
    public void testToString_containsPattern() throws Throwable {
        FastDateFormat fmt = FastDateFormat.getInstance("yyyy-MM-dd");
        assertTrue(fmt.toString().contains("yyyy-MM-dd"));
    }
}
