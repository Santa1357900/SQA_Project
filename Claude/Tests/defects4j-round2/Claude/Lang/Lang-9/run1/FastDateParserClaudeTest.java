package org.apache.commons.lang3.time;

import static org.junit.Assert.*;
import org.junit.Test;

import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class FastDateParserClaudeTest {

    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    private static final Locale US = Locale.US;

    // init(): formatPattern.lookingAt() fails on empty pattern -> IllegalArgumentException
    @Test
    public void testConstructor_emptyPattern_throwsIllegalArgumentException() throws Throwable {
        try {
            new FastDateParser("", GMT, US);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Invalid pattern"));
        }
    }

    // getPattern() simple accessor
    @Test
    public void testGetPattern_returnsGivenPattern() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", GMT, US);
        assertEquals("yyyy-MM-dd", parser.getPattern());
    }

    // getTimeZone() simple accessor
    @Test
    public void testGetTimeZone_returnsGivenTimeZone() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", GMT, US);
        assertEquals(GMT, parser.getTimeZone());
    }

    // getLocale() simple accessor
    @Test
    public void testGetLocale_returnsGivenLocale() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", GMT, US);
        assertEquals(US, parser.getLocale());
    }

    // equals(): all three fields match -> true branch
    @Test
    public void testEquals_sameFields_true() throws Throwable {
        FastDateParser p1 = new FastDateParser("yyyy-MM-dd", GMT, US);
        FastDateParser p2 = new FastDateParser("yyyy-MM-dd", GMT, US);
        assertTrue(p1.equals(p2));
    }

    // equals(): pattern differs -> false
    @Test
    public void testEquals_differentPattern_false() throws Throwable {
        FastDateParser p1 = new FastDateParser("yyyy-MM-dd", GMT, US);
        FastDateParser p2 = new FastDateParser("yyyy/MM/dd", GMT, US);
        assertFalse(p1.equals(p2));
    }

    // equals(): timeZone differs -> false
    @Test
    public void testEquals_differentTimeZone_false() throws Throwable {
        FastDateParser p1 = new FastDateParser("yyyy", GMT, US);
        FastDateParser p2 = new FastDateParser("yyyy", TimeZone.getTimeZone("GMT+01:00"), US);
        assertFalse(p1.equals(p2));
    }

    // equals(): locale differs -> false
    @Test
    public void testEquals_differentLocale_false() throws Throwable {
        FastDateParser p1 = new FastDateParser("yyyy", GMT, US);
        FastDateParser p2 = new FastDateParser("yyyy", GMT, Locale.UK);
        assertFalse(p1.equals(p2));
    }

    // equals(): obj not instanceof FastDateParser (including null) -> false
    @Test
    public void testEquals_nonFastDateParserObject_false() throws Throwable {
        FastDateParser p1 = new FastDateParser("yyyy", GMT, US);
        assertFalse(p1.equals("yyyy"));
        assertFalse(p1.equals(null));
    }

    // hashCode(): equal objects must produce equal hash codes (Object contract)
    @Test
    public void testHashCode_equalObjects_sameHashCode() throws Throwable {
        FastDateParser p1 = new FastDateParser("yyyy-MM-dd", GMT, US);
        FastDateParser p2 = new FastDateParser("yyyy-MM-dd", GMT, US);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    // toString(): documented format "FastDateParser[pattern,locale,timezoneID]"
    @Test
    public void testToString_matchesExpectedFormat() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", GMT, US);
        String expected = "FastDateParser[" + "yyyy" + "," + US + "," + GMT.getID() + "]";
        assertEquals(expected, parser.toString());
    }

    // parse(String): happy path sets year/month/day via NumberStrategy fields
    @Test
    public void testParse_validDatePattern_setsCalendarFields() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", GMT, US);
        Date date = parser.parse("2023-05-15");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(2023, cal.get(Calendar.YEAR));
        assertEquals(Calendar.MAY, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
    }

    // parse(String): no regex match -> ParseException with explanatory message
    @Test
    public void testParse_mismatchedLiteral_throwsParseException() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", GMT, US);
        try {
            parser.parse("2023/05/15");
            fail("expected ParseException");
        } catch (ParseException expected) {
            assertTrue(expected.getMessage().contains("Unparseable date"));
        }
    }

    // parse(String): Japanese Imperial locale mismatch adds "1868" note to message
    @Test
    public void testParse_japaneseImperialLocaleMismatch_throwsParseExceptionWithLocaleNote() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", GMT, FastDateParser.JAPANESE_IMPERIAL);
        try {
            parser.parse("xx");
            fail("expected ParseException");
        } catch (ParseException expected) {
            assertTrue(expected.getMessage().contains("1868"));
        }
    }

    // parse(String, ParsePosition): on success, pos is advanced by matched length
    @Test
    public void testParseWithPosition_validInput_advancesIndex() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", GMT, US);
        ParsePosition pos = new ParsePosition(0);
        Date date = parser.parse("2023", pos);
        assertNotNull(date);
        assertEquals(4, pos.getIndex());
    }

    // parse(String, ParsePosition): on mismatch, returns null (no exception)
    @Test
    public void testParseWithPosition_mismatchedInput_returnsNull() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", GMT, US);
        ParsePosition pos = new ParsePosition(0);
        Date date = parser.parse("abcd", pos);
        assertNull(date);
    }

    // parseObject(String): delegates to parse(String) and returns a Date
    @Test
    public void testParseObjectString_returnsDateInstance() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", GMT, US);
        Object obj = parser.parseObject("2023");
        assertTrue(obj instanceof Date);
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime((Date) obj);
        assertEquals(2023, cal.get(Calendar.YEAR));
    }

    // parseObject(String, ParsePosition): delegates to parse(String, ParsePosition)
    @Test
    public void testParseObjectStringPosition_returnsDateOrNull() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", GMT, US);
        ParsePosition pos = new ParsePosition(0);
        Object obj = parser.parseObject("2023", pos);
        assertNotNull(obj);
        assertEquals(4, pos.getIndex());
    }

    // BUG: SimpleDateFormat-compatible parsing must be case-insensitive for text month names
    @Test
    public void testParse_lowercaseMonthName_parsesCaseInsensitively() throws Throwable {
        FastDateParser parser = new FastDateParser("MMM", GMT, US);
        Date date = parser.parse("jan");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
    }

    // BUG reinforcement: AM/PM text must also be matched case-insensitively
    @Test
    public void testParse_lowercaseAmPm_parsesCaseInsensitively() throws Throwable {
        FastDateParser parser = new FastDateParser("hh:mm a", GMT, US);
        Date date = parser.parse("02:30 pm");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(2, cal.get(Calendar.HOUR));
        assertEquals(Calendar.PM, cal.get(Calendar.AM_PM));
    }

    // BUG reinforcement: day-of-week text must also be matched case-insensitively
    @Test
    public void testParse_lowercaseDayOfWeek_parsesCaseInsensitively() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd EEEE", GMT, US);
        Date date = parser.parse("2023-05-15 monday");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK));
    }

    // Control case: exact-case day-of-week text should parse on both buggy and fixed versions
    @Test
    public void testParse_exactCaseDayOfWeek_controlCase() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd EEEE", GMT, US);
        Date date = parser.parse("2023-05-15 Monday");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK));
    }

    // ERA strategy ('G'): combined with YEAR+MONTH+DAY_OF_MONTH resolution group
    @Test
    public void testParse_eraStrategy_parsesADWithYearMonthDay() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd G", GMT, US);
        Date date = parser.parse("2023-05-15 AD");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(2023, cal.get(Calendar.YEAR));
        assertEquals(GregorianCalendar.AD, cal.get(Calendar.ERA));
    }

    // DAY_OF_YEAR strategy ('D'): combined with YEAR forms a complete resolution group
    @Test
    public void testParse_dayOfYearStrategy_withYear() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy D", GMT, US);
        Date date = parser.parse("2023 45");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH));
        assertEquals(14, cal.get(Calendar.DAY_OF_MONTH));
    }

    // DAY_OF_WEEK_IN_MONTH strategy ('F'): combined with YEAR+MONTH+DAY_OF_WEEK (complete group)
    @Test
    public void testParse_dayOfWeekInMonthStrategy_withFullContext() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM F E", GMT, US);
        Date date = parser.parse("2023-05 2 Monday");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(8, cal.get(Calendar.DAY_OF_MONTH));
    }

    // MILLISECOND / SECOND strategies: independent time-of-day fields
    @Test
    public void testParse_millisecondStrategy() throws Throwable {
        FastDateParser parser = new FastDateParser("ss.SSS", GMT, US);
        Date date = parser.parse("05.123");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(5, cal.get(Calendar.SECOND));
        assertEquals(123, cal.get(Calendar.MILLISECOND));
    }

    // HOUR strategy ('K'): plain 0-11 hour, no modulo applied
    @Test
    public void testParse_hourStrategy_KPattern_noModulo() throws Throwable {
        FastDateParser parser = new FastDateParser("KK", GMT, US);
        Date date = parser.parse("11");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(11, cal.get(Calendar.HOUR));
    }

    // MODULO_HOUR strategy ('h'): value 12 wraps to 0 via iValue % 12
    @Test
    public void testParse_moduloHourStrategy_wrapsAt12() throws Throwable {
        FastDateParser parser = new FastDateParser("hh", GMT, US);
        Date date = parser.parse("12");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(0, cal.get(Calendar.HOUR));
    }

    // MODULO_HOUR_OF_DAY strategy ('H'): value 24 wraps to 0 via iValue % 24
    @Test
    public void testParse_moduloHourOfDayStrategy_wrapsAt24() throws Throwable {
        FastDateParser parser = new FastDateParser("HH", GMT, US);
        Date date = parser.parse("24");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY));
    }

    // ABBREVIATED_YEAR strategy ('yy'): two-digit year within window resolves to current century
    @Test
    public void testParse_abbreviatedYearStrategy_adjustsTwoDigitYear() throws Throwable {
        int thisYear = Calendar.getInstance(GMT, US).get(Calendar.YEAR);
        int r = thisYear % 100;
        String twoDigit = (r < 10 ? "0" + r : String.valueOf(r));
        FastDateParser parser = new FastDateParser("yy", GMT, US);
        Date date = parser.parse(twoDigit);
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(thisYear, cal.get(Calendar.YEAR));
    }

    // LITERAL_YEAR strategy ('yyyy'): no two-digit adjustment performed
    @Test
    public void testParse_literalYearStrategy_doesNotAdjust() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", GMT, US);
        Date date = parser.parse("0099");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(99, cal.get(Calendar.YEAR));
    }

    // NumberStrategy.addRegex: fixed-width digit groups when next field is also a number
    @Test
    public void testParse_fixedWidthNumberStrategy_consecutiveNumericFields() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyyMMdd", GMT, US);
        Date date = parser.parse("20230515");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(2023, cal.get(Calendar.YEAR));
        assertEquals(Calendar.MAY, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
    }

    // CopyQuotedStrategy: quoted literal text stripped of quotes, plus literal ':' handling
    @Test
    public void testParse_copyQuotedStrategy_literalTextAndColon() throws Throwable {
        FastDateParser parser = new FastDateParser("'T'HH:mm", GMT, US);
        Date date = parser.parse("T05:30");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        assertEquals(5, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, cal.get(Calendar.MINUTE));
    }

    // TimeZoneStrategy: numeric "+HHmm" offset parsed into GMT+offset timezone
    @Test
    public void testParse_timeZoneStrategy_positiveNumericOffset() throws Throwable {
        FastDateParser parser = new FastDateParser("Z", GMT, US);
        Date date = parser.parse("+0130");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        int expected = TimeZone.getTimeZone("GMT+0130").getRawOffset();
        assertEquals(expected, cal.get(Calendar.ZONE_OFFSET));
    }

    // TimeZoneStrategy: numeric "-HHmm" offset parsed into GMT-offset timezone
    @Test
    public void testParse_timeZoneStrategy_negativeNumericOffset() throws Throwable {
        FastDateParser parser = new FastDateParser("Z", GMT, US);
        Date date = parser.parse("-0500");
        Calendar cal = Calendar.getInstance(GMT, US);
        cal.setTime(date);
        int expected = TimeZone.getTimeZone("GMT-0500").getRawOffset();
        assertEquals(expected, cal.get(Calendar.ZONE_OFFSET));
    }

    // adjustYear(): branch trial < thisYear+20 -> returned within current century, unshifted
    @Test
    public void testAdjustYear_withinWindow_noCenturyShift() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", GMT, US);
        int thisYear = Calendar.getInstance(GMT, US).get(Calendar.YEAR);
        int lastTwoDigits = thisYear % 100;
        assertEquals(thisYear, parser.adjustYear(lastTwoDigits));
    }

    // adjustYear(): branch trial >= thisYear+20 -> shifted back by 100 (previous century)
    @Test
    public void testAdjustYear_beyondWindow_shiftsBackCentury() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy", GMT, US);
        int thisYear = Calendar.getInstance(GMT, US).get(Calendar.YEAR);
        int base = thisYear - thisYear % 100;
        int twoDigitYear = (thisYear % 100 + 50) % 100;
        int trial = base + twoDigitYear;
        int expected = trial < thisYear + 20 ? trial : trial - 100;
        assertEquals(expected, parser.adjustYear(twoDigitYear));
    }

    // getParsePattern(): package-private accessor returns a usable, non-empty regex Pattern
    @Test
    public void testGetParsePattern_notNullAndNonEmpty() throws Throwable {
        FastDateParser parser = new FastDateParser("yyyy-MM-dd", GMT, US);
        assertNotNull(parser.getParsePattern());
        assertTrue(parser.getParsePattern().pattern().length() > 0);
    }
}
