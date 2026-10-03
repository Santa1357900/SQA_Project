package org.apache.commons.lang3.time;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class FastDateParserClaudeTest {

    private TimeZone gmt;
    private Locale locale;

    @Before
    public void setUp() throws Throwable {
        gmt = TimeZone.getTimeZone("GMT");
        locale = Locale.US;
    }

    // init(): empty pattern never matches formatPattern -> IllegalArgumentException
    @Test
    public void testConstructor_invalidPattern_throwsIllegalArgumentException() throws Throwable {
        try {
            new FastDateParser("", gmt, locale);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Invalid"));
        }
    }

    // getPattern() returns exact constructor argument
    @Test
    public void testGetPattern_returnsConstructorPattern() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", gmt, locale);
        assertEquals("yyyy-MM-dd", parser.getPattern());
    }

    // getTimeZone() returns exact constructor argument
    @Test
    public void testGetTimeZone_returnsConstructorTimeZone() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", gmt, locale);
        assertEquals(gmt, parser.getTimeZone());
    }

    // getLocale() returns exact constructor argument
    @Test
    public void testGetLocale_returnsConstructorLocale() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", gmt, locale);
        assertEquals(locale, parser.getLocale());
    }

    // getParsePattern() is built during init() and must not be null
    @Test
    public void testGetParsePattern_notNullAfterConstruction() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", gmt, locale);
        assertNotNull(parser.getParsePattern());
    }

    // equals(): obj not instanceof FastDateParser -> false
    @Test
    public void testEquals_notFastDateParserInstance_returnsFalse() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", gmt, locale);
        assertFalse(parser.equals("yyyy"));
    }

    // equals(): same pattern/timeZone/locale -> true
    @Test
    public void testEquals_samePatternTimeZoneLocale_returnsTrue() throws Throwable {
        FastDateParser a = new FastDateParser("yyyy-MM-dd", gmt, locale);
        FastDateParser b = new FastDateParser("yyyy-MM-dd", gmt, locale);
        assertTrue(a.equals(b));
    }

    // equals(): different pattern -> false
    @Test
    public void testEquals_differentPattern_returnsFalse() throws Throwable {
        FastDateParser a = new FastDateParser("yyyy-MM-dd", gmt, locale);
        FastDateParser b = new FastDateParser("yyyy/MM/dd", gmt, locale);
        assertFalse(a.equals(b));
    }

    // equals(): different timeZone -> false
    @Test
    public void testEquals_differentTimeZone_returnsFalse() throws Throwable {
        FastDateParser a = new FastDateParser("yyyy-MM-dd", gmt, locale);
        FastDateParser b = new FastDateParser("yyyy-MM-dd", TimeZone.getTimeZone("GMT+01:00"), locale);
        assertFalse(a.equals(b));
    }

    // equals(): different locale -> false
    @Test
    public void testEquals_differentLocale_returnsFalse() throws Throwable {
        FastDateParser a = new FastDateParser("yyyy-MM-dd", gmt, Locale.US);
        FastDateParser b = new FastDateParser("yyyy-MM-dd", gmt, Locale.GERMANY);
        assertFalse(a.equals(b));
    }

    // hashCode(): equal objects must have equal hashCodes (general Java contract)
    @Test
    public void testHashCode_equalInstances_sameHashCode() throws Throwable {
        FastDateParser a = new FastDateParser("yyyy-MM-dd", gmt, locale);
        FastDateParser b = new FastDateParser("yyyy-MM-dd", gmt, locale);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // toString(): contains pattern, locale and timeZone id as per implementation format
    @Test
    public void testToString_containsPatternLocaleAndTimeZoneId() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", gmt, locale);
        String s = parser.toString();
        assertTrue(s.contains("yyyy-MM-dd"));
        assertTrue(s.contains(locale.toString()));
        assertTrue(s.contains(gmt.getID()));
    }

    // parseObject(String) delegates to parse(String) and returns a Date
    @Test
    public void testParseObjectString_returnsParsedDate() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", gmt, locale);
        Object result = parser.parseObject("2020-01-15");
        assertTrue(result instanceof Date);
    }

    // parse(String): simple numeric date fields parsed into correct calendar values
    @Test
    public void testParseString_simpleDate_parsesYearMonthDay() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", gmt, locale);
        Date d = parser.parse("2020-01-15");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(2020, cal.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
    }

    // parse(String): input that does not match pattern -> ParseException("Unparseable...")
    @Test
    public void testParseString_mismatchedInput_throwsParseException() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", gmt, locale);
        try {
            parser.parse("abcdefghij");
            fail("expected ParseException");
        } catch (ParseException expected) {
            assertTrue(expected.getMessage().contains("Unparseable"));
        }
    }

    // parse(String,ParsePosition): successful match advances the position index
    @Test
    public void testParseStringParsePosition_validInput_advancesPositionIndex() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", gmt, locale);
        ParsePosition pos = new ParsePosition(0);
        Date d = parser.parse("2020", pos);
        assertNotNull(d);
        assertEquals(4, pos.getIndex());
    }

    // parse(String,ParsePosition): mismatched input returns null, index unchanged
    @Test
    public void testParseStringParsePosition_mismatch_returnsNullAndIndexUnchanged() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", gmt, locale);
        ParsePosition pos = new ParsePosition(0);
        Date d = parser.parse("abcd", pos);
        assertNull(d);
        assertEquals(0, pos.getIndex());
    }

    // parseObject(String,ParsePosition) must delegate to parse(String,ParsePosition)
    @Test
    public void testParseObjectStringParsePosition_delegatesToParse() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", gmt, locale);
        Object viaParseObject = parser.parseObject("2020", new ParsePosition(0));
        Date viaParse = parser.parse("2020", new ParsePosition(0));
        assertEquals(viaParse, viaParseObject);
    }

    // BUG: pattern 'k' (Hour in day 1-24) must map 24 -> HOUR_OF_DAY 0 on the SAME day,
    // not roll over to the next day's date.
    @Test
    public void testParse_hourPatternK24_shouldNotRollOverToNextDay() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd'T'kk:mm:ss", gmt, locale);
        Date d = parser.parse("2003-01-10T24:00:00");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(10, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY));
    }

    // pattern 'H' (Hour in day 0-23): normal value parses without date rollover
    @Test
    public void testParse_hourPatternH_zeroToTwentyThree() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd'T'HH:mm:ss", gmt, locale);
        Date d = parser.parse("2003-01-10T05:00:00");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(10, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(5, cal.get(Calendar.HOUR_OF_DAY));
    }

    // pattern 'h' with modulo 12: "12" AM maps to HOUR_OF_DAY 0 (midnight)
    @Test
    public void testParse_hourPatternLowercaseH_moduloTwelveAM() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd hh:mm:ss a", gmt, locale);
        Date d = parser.parse("2020-01-01 12:00:00 AM");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY));
    }

    // pattern 'h' with modulo 12: "12" PM maps to HOUR_OF_DAY 12 (noon)
    @Test
    public void testParse_hourPatternLowercaseH_moduloTwelvePM() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd hh:mm:ss a", gmt, locale);
        Date d = parser.parse("2020-01-01 12:00:00 PM");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(12, cal.get(Calendar.HOUR_OF_DAY));
    }

    // pattern 'K' (Hour in am/pm 0-11): identity mapping to Calendar.HOUR
    @Test
    public void testParse_hourPatternUppercaseK_rangeZeroToEleven() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd KK:mm a", gmt, locale);
        Date d = parser.parse("2020-01-01 00:00 AM");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY));
    }

    // NUMBER_MONTH_STRATEGY: parsed 1-based month converted to 0-based Calendar.MONTH
    @Test
    public void testParse_numberMonthStrategy_oneBasedInputToZeroBasedField() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", gmt, locale);
        Date d = parser.parse("2020-01-15");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
    }

    // TEXT_MONTH_STRATEGY: 3+ letter month pattern parses textual month name
    @Test
    public void testParse_textMonthStrategy_parsesMonthName() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MMM-dd", gmt, locale);
        Date d = parser.parse("2020-Mar-15");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH));
    }

    // ABBREVIATED_YEAR_STRATEGY: 2-digit year delegates to adjustYear(); cross-check consistency
    @Test
    public void testParse_abbreviatedYearStrategy_twoDigitYear_appliesAdjustYear() throws Throwable {
        FastDateParser parser = new FastDateParser("yy-MM-dd", gmt, locale);
        Date d = parser.parse("05-01-15");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(parser.adjustYear(5), cal.get(Calendar.YEAR));
    }

    // LITERAL_YEAR_STRATEGY: pattern length > 2 interprets year literally (no adjustment)
    @Test
    public void testParse_literalYearStrategy_fourDigitPattern() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", gmt, locale);
        Date d = parser.parse("0099-01-15");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(99, cal.get(Calendar.YEAR));
    }

    // DAY_OF_WEEK_STRATEGY: text weekday name parses to the matching Calendar.DAY_OF_WEEK
    @Test
    public void testParse_dayOfWeekTextStrategy_parsesWeekdayName() throws Throwable {
        Calendar seed = Calendar.getInstance(gmt, locale);
        seed.clear();
        seed.set(2020, Calendar.JANUARY, 1);
        int dow = seed.get(Calendar.DAY_OF_WEEK);
        String dowName = DateFormatSymbols.getInstance(locale).getWeekdays()[dow];

        FastDateParser parser = new FastDateParser("EEEE yyyy-MM-dd", gmt, locale);
        Date d = parser.parse(dowName + " 2020-01-01");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(dow, cal.get(Calendar.DAY_OF_WEEK));
    }

    // DAY_OF_YEAR_STRATEGY: numeric day-of-year combined with year resolves to correct date
    @Test
    public void testParse_dayOfYearStrategy_parsesNumericField() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-DDD", gmt, locale);
        Date d = parser.parse("2020-001");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH));
    }

    // MILLISECOND_STRATEGY: 3-digit millisecond field parsed with identity modify
    @Test
    public void testParse_millisecondStrategy_parsesThreeDigitField() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd HH:mm:ss.SSS", gmt, locale);
        Date d = parser.parse("2020-01-01 00:00:00.123");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(123, cal.get(Calendar.MILLISECOND));
    }

    // TimeZoneStrategy: numeric "+HHmm" offset sets the calendar's time zone accordingly
    @Test
    public void testParse_timeZoneStrategy_numericOffset() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd HH:mm:ss Z", gmt, locale);
        Date actual = parser.parse("2020-01-01 00:00:00 +0130");
        Calendar expectedCal = Calendar.getInstance(TimeZone.getTimeZone("GMT+01:30"), locale);
        expectedCal.clear();
        expectedCal.set(2020, Calendar.JANUARY, 1, 0, 0, 0);
        assertEquals(expectedCal.getTime(), actual);
    }

    // TimeZoneStrategy: value starting with "GMT" resolved via TimeZone.getTimeZone(value)
    @Test
    public void testParse_timeZoneStrategy_gmtNamedOffset() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd HH:mm:ss z", gmt, locale);
        Date actual = parser.parse("2020-01-01 00:00:00 GMT+02");
        Calendar expectedCal = Calendar.getInstance(TimeZone.getTimeZone("GMT+02"), locale);
        expectedCal.clear();
        expectedCal.set(2020, Calendar.JANUARY, 1, 0, 0, 0);
        assertEquals(expectedCal.getTime(), actual);
    }

    // CopyQuotedStrategy: quoted literal text between number fields is matched as-is
    @Test
    public void testParse_copyQuotedStrategy_literalTextBetweenFields() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy'at'MM", gmt, locale);
        Date d = parser.parse("2020at01");
        Calendar cal = Calendar.getInstance(gmt, locale);
        cal.setTime(d);
        assertEquals(2020, cal.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
    }

    // adjustYear(): when trial equals thisYear exactly, the year itself is returned unchanged
    @Test
    public void testAdjustYear_trialEqualsThisYear_returnsSameYear() throws Throwable {
        FastDateParser parser = new FastDateParser("yy", gmt, locale);
        int thisYear = Calendar.getInstance(gmt, locale).get(Calendar.YEAR);
        int twoDigit = thisYear % 100;
        assertEquals(thisYear, parser.adjustYear(twoDigit));
    }

    // adjustYear(): a value 50 years after thisYear (mod 100) wraps to the previous century
    @Test
    public void testAdjustYear_trialFiftyYearsAfter_wrapsToPreviousCentury() throws Throwable {
        FastDateParser parser = new FastDateParser("yy", gmt, locale);
        int thisYear = Calendar.getInstance(gmt, locale).get(Calendar.YEAR);
        int twoDigit = (thisYear % 100 + 50) % 100;
        assertEquals(thisYear - 50, parser.adjustYear(twoDigit));
    }
}
