package com.fasterxml.jackson.databind.util;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;
import static org.junit.Assert.*;

public class StdDateFormatClaudeTest
{
    private long utcMillis(int year, int month, int day, int hour, int min, int sec, int ms) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        cal.clear();
        cal.set(year, month, day, hour, min, sec);
        cal.set(Calendar.MILLISECOND, ms);
        return cal.getTimeInMillis();
    }

    // getDefaultTimeZone() must return UTC per class javadoc ("we use UTC for everything")
    @Test
    public void testGetDefaultTimeZone_returnsUTC() throws Throwable {
        assertEquals("UTC", StdDateFormat.getDefaultTimeZone().getID());
    }

    // withTimeZone(null) branch: tz==null -> defaults to DEFAULT_TIMEZONE
    @Test
    public void testWithTimeZone_null_defaultsToUTC() throws Throwable {
        StdDateFormat fmt = new StdDateFormat();
        StdDateFormat fmt2 = fmt.withTimeZone(null);
        assertEquals(StdDateFormat.getDefaultTimeZone(), fmt2.getTimeZone());
    }

    // withTimeZone same tz branch: tz.equals(_timezone) -> returns same instance
    @Test
    public void testWithTimeZone_sameTimezone_returnsSameInstance() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("America/New_York");
        StdDateFormat fmt = new StdDateFormat().withTimeZone(tz);
        StdDateFormat fmt2 = fmt.withTimeZone(tz);
        assertSame(fmt, fmt2);
    }

    // withTimeZone different tz branch: creates new instance with given tz
    @Test
    public void testWithTimeZone_differentTimezone_returnsNewInstance() throws Throwable {
        StdDateFormat fmt = new StdDateFormat();
        TimeZone tz = TimeZone.getTimeZone("America/New_York");
        StdDateFormat fmt2 = fmt.withTimeZone(tz);
        assertNotSame(fmt, fmt2);
        assertEquals(tz, fmt2.getTimeZone());
    }

    // withLocale same locale branch -> returns same instance
    @Test
    public void testWithLocale_sameLocale_returnsSameInstance() throws Throwable {
        StdDateFormat fmt = new StdDateFormat();
        StdDateFormat fmt2 = fmt.withLocale(Locale.US);
        assertSame(fmt, fmt2);
    }

    // withLocale different locale branch -> returns new instance
    @Test
    public void testWithLocale_differentLocale_returnsNewInstance() throws Throwable {
        StdDateFormat fmt = new StdDateFormat();
        StdDateFormat fmt2 = fmt.withLocale(Locale.FRANCE);
        assertNotSame(fmt, fmt2);
    }

    // clone() must return a distinct instance
    @Test
    public void testClone_returnsDifferentInstance() throws Throwable {
        StdDateFormat fmt = new StdDateFormat();
        StdDateFormat cloned = fmt.clone();
        assertNotSame(fmt, cloned);
    }

    // static getISO8601Format(tz, loc) formats per DATE_FORMAT_STR_ISO8601 pattern
    @Test
    public void testGetISO8601Format_staticFactory_formatsCorrectly() throws Throwable {
        Date date = new Date(utcMillis(2015, 0, 23, 15, 4, 22, 123));
        DateFormat iso = StdDateFormat.getISO8601Format(TimeZone.getTimeZone("UTC"), Locale.US);
        assertEquals("2015-01-23T15:04:22.123+0000", iso.format(date));
    }

    // deprecated getISO8601Format(tz) must behave same as explicit-locale variant with US locale
    @Test
    public void testGetISO8601Format_deprecatedOverload_usesDefaultLocale() throws Throwable {
        Date date = new Date(utcMillis(2015, 0, 23, 15, 4, 22, 123));
        DateFormat iso1 = StdDateFormat.getISO8601Format(TimeZone.getTimeZone("UTC"));
        DateFormat iso2 = StdDateFormat.getISO8601Format(TimeZone.getTimeZone("UTC"), Locale.US);
        assertEquals(iso2.format(date), iso1.format(date));
    }

    // static getRFC1123Format(tz, loc) formats per DATE_FORMAT_STR_RFC1123 pattern
    @Test
    public void testGetRFC1123Format_staticFactory_formatsCorrectly() throws Throwable {
        Date date = new Date(utcMillis(2015, 0, 23, 15, 4, 22, 0));
        DateFormat rfc = StdDateFormat.getRFC1123Format(TimeZone.getTimeZone("UTC"), Locale.US);
        assertTrue(rfc.format(date).startsWith("Fri, 23 Jan 2015 15:04:22"));
    }

    // deprecated getRFC1123Format(tz) must behave same as explicit-locale variant with US locale
    @Test
    public void testGetRFC1123Format_deprecatedOverload_usesDefaultLocale() throws Throwable {
        Date date = new Date(utcMillis(2015, 0, 23, 15, 4, 22, 0));
        DateFormat rfc1 = StdDateFormat.getRFC1123Format(TimeZone.getTimeZone("UTC"));
        DateFormat rfc2 = StdDateFormat.getRFC1123Format(TimeZone.getTimeZone("UTC"), Locale.US);
        assertEquals(rfc2.format(date), rfc1.format(date));
    }

    // setTimeZone updates the timezone returned by getTimeZone; default is null
    @Test
    public void testGetTimeZone_setTimeZone_updatesValue() throws Throwable {
        StdDateFormat fmt = new StdDateFormat();
        assertNull(fmt.getTimeZone());
        TimeZone tz = TimeZone.getTimeZone("America/Los_Angeles");
        fmt.setTimeZone(tz);
        assertEquals(tz, fmt.getTimeZone());
    }

    // isLenient defaults to true (per javadoc "default is, I believe, true"); setLenient toggles it
    @Test
    public void testSetLenient_isLenient_defaultAndToggle() throws Throwable {
        StdDateFormat fmt = new StdDateFormat();
        assertTrue(fmt.isLenient());
        fmt.setLenient(false);
        assertFalse(fmt.isLenient());
        fmt.setLenient(true);
        assertTrue(fmt.isLenient());
    }

    // parse: plain date branch (DATE_FORMAT_STR_PLAIN) -> midnight UTC
    @Test
    public void testParse_plainDate_parsesAtMidnightUTC() throws Throwable {
        Date parsed = new StdDateFormat().parse("2015-01-23");
        assertEquals(utcMillis(2015, 0, 23, 0, 0, 0, 0), parsed.getTime());
    }

    // parse: ISO8601 full offset without colon, already normalized (no insertion needed)
    @Test
    public void testParse_isoWithFullOffsetNoColon_parsesCorrectly() throws Throwable {
        Date parsed = new StdDateFormat().parse("2015-01-23T15:04:22.000+0000");
        assertEquals(utcMillis(2015, 0, 23, 15, 4, 22, 0), parsed.getTime());
    }

    // parse: ISO8601 offset with colon -> colon removed branch
    @Test
    public void testParse_isoWithColonInOffset_parsesCorrectly() throws Throwable {
        Date parsed = new StdDateFormat().parse("2015-01-23T15:04:22.000+00:00");
        assertEquals(utcMillis(2015, 0, 23, 15, 4, 22, 0), parsed.getTime());
    }

    // parse: ISO8601 offset missing minutes ("+00") -> "00" appended branch
    @Test
    public void testParse_isoWithShortOffsetMissingMinutes_parsesCorrectly() throws Throwable {
        Date parsed = new StdDateFormat().parse("2015-01-23T15:04:22.000+00");
        assertEquals(utcMillis(2015, 0, 23, 15, 4, 22, 0), parsed.getTime());
    }

    // parse: ISO8601 omitting seconds entirely (timeLen==5, "is legal to omit seconds")
    @Test
    public void testParse_isoOmittingSeconds_parsesCorrectly() throws Throwable {
        Date parsed = new StdDateFormat().parse("2015-01-23T15:04+0000");
        assertEquals(utcMillis(2015, 0, 23, 15, 4, 0, 0), parsed.getTime());
    }

    // parse: 'Z' suffix missing millis -> ".000" inserted branch
    @Test
    public void testParse_isoZuluMissingMillis_addsZeroMillis() throws Throwable {
        Date parsed = new StdDateFormat().parse("2015-01-23T15:04:22Z");
        assertEquals(utcMillis(2015, 0, 23, 15, 4, 22, 0), parsed.getTime());
    }

    // parse: 'Z' suffix with millis already present -> no insertion
    @Test
    public void testParse_isoZuluWithMillis_parsesCorrectly() throws Throwable {
        Date parsed = new StdDateFormat().parse("2015-01-23T15:04:22.123Z");
        assertEquals(utcMillis(2015, 0, 23, 15, 4, 22, 123), parsed.getTime());
    }

    // parse: no timezone at all -> plain-no-tz branch appends 'Z' and parses as UTC
    @Test
    public void testParse_isoNoTimezone_defaultsToUTC() throws Throwable {
        Date parsed = new StdDateFormat().parse("2015-01-23T15:04:22.000");
        assertEquals(utcMillis(2015, 0, 23, 15, 4, 22, 0), parsed.getTime());
    }

    // parse: 'Z' suffix must use UTC regardless of configured instance timezone (databind#1651)
    @Test
    public void testParse_zuluIgnoresConfiguredTimezone() throws Throwable {
        StdDateFormat fmt = new StdDateFormat().withTimeZone(TimeZone.getTimeZone("America/New_York"));
        Date parsed = fmt.parse("2015-01-23T15:04:22.000Z");
        assertEquals(utcMillis(2015, 0, 23, 15, 4, 22, 0), parsed.getTime());
    }

    // parse: all-digits string "0" -> numeric epoch millis branch
    @Test
    public void testParse_numericTimestampZero_returnsEpoch() throws Throwable {
        Date parsed = new StdDateFormat().parse("0");
        assertEquals(0L, parsed.getTime());
    }

    // parse: negative numeric timestamp branch (databind#267)
    @Test
    public void testParse_numericTimestampNegative_returnsCorrectDate() throws Throwable {
        Date parsed = new StdDateFormat().parse("-1000");
        assertEquals(-1000L, parsed.getTime());
    }

    // parse: positive numeric timestamp within long range branch
    @Test
    public void testParse_numericTimestampPositive_returnsCorrectDate() throws Throwable {
        Date parsed = new StdDateFormat().parse("1420383862000");
        assertEquals(1420383862000L, parsed.getTime());
    }

    // parse: all-digit string that overflows long range -> falls back to RFC1123 -> fails -> ParseException
    @Test
    public void testParse_numericTimestampOverflow_fallsBackAndThrows() throws Throwable {
        try {
            new StdDateFormat().parse("99999999999999999999");
            fail("expected ParseException");
        } catch (ParseException expected) {
        }
    }

    // parse: RFC-1123 branch for non-ISO, non-numeric input
    @Test
    public void testParse_rfc1123_parsesCorrectly() throws Throwable {
        Date parsed = new StdDateFormat().parse("Fri, 23 Jan 2015 15:04:22 GMT");
        assertEquals(utcMillis(2015, 0, 23, 15, 4, 22, 0), parsed.getTime());
    }

    // parse: input matching none of the supported formats -> ParseException thrown
    @Test
    public void testParse_invalidString_throwsParseException() throws Throwable {
        try {
            new StdDateFormat().parse("totally-not-a-date-value");
            fail("expected ParseException");
        } catch (ParseException expected) {
        }
    }

    // parse(String): leading/trailing whitespace is trimmed before parsing
    @Test
    public void testParse_trimsWhitespace() throws Throwable {
        Date parsed = new StdDateFormat().parse("  2015-01-23T15:04:22.000+0000  ");
        assertEquals(utcMillis(2015, 0, 23, 15, 4, 22, 0), parsed.getTime());
    }

    // parse(String,ParsePosition): invalid input returns null instead of throwing
    @Test
    public void testParseWithParsePosition_invalidString_returnsNull() throws Throwable {
        Date parsed = new StdDateFormat().parse("not-a-date-xyz", new ParsePosition(0));
        assertNull(parsed);
    }

    // parse(String,ParsePosition): valid ISO plain date returns proper Date
    @Test
    public void testParseWithParsePosition_validISO_returnsDate() throws Throwable {
        Date parsed = new StdDateFormat().parse("2015-01-23", new ParsePosition(0));
        assertNotNull(parsed);
        assertEquals(utcMillis(2015, 0, 23, 0, 0, 0, 0), parsed.getTime());
    }

    // format(Date,StringBuffer,FieldPosition) produces DATE_FORMAT_STR_ISO8601 pattern output
    @Test
    public void testFormat_producesISO8601Pattern() throws Throwable {
        Date date = new Date(utcMillis(2015, 0, 23, 15, 4, 22, 123));
        StringBuffer sb = new StdDateFormat().format(date, new StringBuffer(), new FieldPosition(0));
        assertEquals("2015-01-23T15:04:22.123+0000", sb.toString());
    }

    // round trip: format then parse must preserve the exact instant
    @Test
    public void testFormatThenParse_roundTrip_preservesInstant() throws Throwable {
        Date date = new Date(utcMillis(2015, 5, 15, 10, 30, 45, 678));
        StdDateFormat fmt = new StdDateFormat();
        String s = fmt.format(date, new StringBuffer(), new FieldPosition(0)).toString();
        Date parsed = fmt.parse(s);
        assertEquals(date.getTime(), parsed.getTime());
    }

    // toString includes class name for diagnostic purposes
    @Test
    public void testToString_containsClassName() throws Throwable {
        String s = new StdDateFormat().toString();
        assertTrue(s.contains("StdDateFormat"));
    }

    // equals is identity-based only (databind#1130)
    @Test
    public void testEquals_identityOnly() throws Throwable {
        StdDateFormat a = new StdDateFormat();
        StdDateFormat b = new StdDateFormat();
        assertTrue(a.equals(a));
        assertFalse(a.equals(b));
    }

    // hashCode must match System.identityHashCode (databind#1130)
    @Test
    public void testHashCode_matchesIdentityHashCode() throws Throwable {
        StdDateFormat a = new StdDateFormat();
        assertEquals(System.identityHashCode(a), a.hashCode());
    }

    // looksLikeISO8601: true/false branches based on digit/dash pattern at fixed positions
    @Test
    public void testLooksLikeISO8601_variousInputs() throws Throwable {
        StdDateFormat fmt = new StdDateFormat();
        assertTrue(fmt.looksLikeISO8601("2015-01-23"));
        assertFalse(fmt.looksLikeISO8601("12345"));
        assertFalse(fmt.looksLikeISO8601("123"));
        assertFalse(fmt.looksLikeISO8601("abcd-"));
    }

    // parseAsRFC1123 protected method called directly with valid RFC-1123 string
    @Test
    public void testParseAsRFC1123_direct_parsesCorrectly() throws Throwable {
        StdDateFormat fmt = new StdDateFormat();
        ParsePosition pos = new ParsePosition(0);
        Date parsed = fmt.parseAsRFC1123("Fri, 23 Jan 2015 15:04:22 GMT", pos);
        assertNotNull(parsed);
        assertEquals(utcMillis(2015, 0, 23, 15, 4, 22, 0), parsed.getTime());
    }

    // singleton "instance" must be non-null and usable with default lenient behavior
    @Test
    public void testInstance_singletonNotNull() throws Throwable {
        assertNotNull(StdDateFormat.instance);
        assertTrue(StdDateFormat.instance.isLenient());
    }

    // deprecated constructor(tz, loc) stores the given timezone, retrievable via getTimeZone()
    @Test
    public void testDeprecatedConstructor_withTimeZoneAndLocale_getTimeZoneReturnsSet() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("America/Chicago");
        StdDateFormat fmt = new StdDateFormat(tz, Locale.US);
        assertEquals(tz, fmt.getTimeZone());
    }
}
