package org.joda.time.format;

import java.io.StringWriter;
import java.util.Locale;

import org.junit.Test;
import static org.junit.Assert.*;

import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.MutableDateTime;
import org.joda.time.ReadableInstant;
import org.joda.time.ReadablePartial;
import org.joda.time.chrono.ISOChronology;

public class DateTimeFormatterClaudeTest {

    private DateTimeFormatter isoDateTime = ISODateTimeFormat.dateTime();

    // isPrinter() true when a printer is supplied
    @Test
    public void testIsPrinter_withPrinter_returnsTrue() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertTrue(f.isPrinter());
    }

    // isPrinter() false when printer is null via public constructor
    @Test
    public void testIsPrinter_withoutPrinter_returnsFalse() throws Throwable {
        DateTimeFormatter f = new DateTimeFormatter(null, isoDateTime.getParser());
        assertFalse(f.isPrinter());
    }

    // getPrinter() returns exact reference passed in
    @Test
    public void testGetPrinter_returnsSameReference() throws Throwable {
        DateTimePrinter p = isoDateTime.getPrinter();
        DateTimeFormatter f = new DateTimeFormatter(p, null);
        assertSame(p, f.getPrinter());
    }

    // isParser() true when a parser is supplied
    @Test
    public void testIsParser_withParser_returnsTrue() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertTrue(f.isParser());
    }

    // isParser() false when parser is null via public constructor
    @Test
    public void testIsParser_withoutParser_returnsFalse() throws Throwable {
        DateTimeFormatter f = new DateTimeFormatter(isoDateTime.getPrinter(), null);
        assertFalse(f.isParser());
    }

    // getParser() returns exact reference passed in
    @Test
    public void testGetParser_returnsSameReference() throws Throwable {
        DateTimeParser p = isoDateTime.getParser();
        DateTimeFormatter f = new DateTimeFormatter(null, p);
        assertSame(p, f.getParser());
    }

    // withLocale: same (null) locale short-circuits, returns this
    @Test
    public void testWithLocale_sameNullLocale_returnsSameInstance() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertSame(f, f.withLocale(null));
    }

    // withLocale: different locale creates a new formatter carrying it
    @Test
    public void testWithLocale_differentLocale_returnsNewInstanceWithLocale() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        DateTimeFormatter f2 = f.withLocale(Locale.FRANCE);
        assertNotSame(f, f2);
        assertEquals(Locale.FRANCE, f2.getLocale());
    }

    // getLocale() default is null
    @Test
    public void testGetLocale_defaultIsNull() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertNull(f.getLocale());
    }

    // withOffsetParsed: sets flag true and clears override zone (per javadoc)
    @Test
    public void testWithOffsetParsed_setsFlagAndClearsZone() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        DateTimeFormatter f2 = f.withOffsetParsed();
        assertTrue(f2.isOffsetParsed());
        assertNull(f2.getZone());
    }

    // withOffsetParsed: second call on already-true flag returns same instance
    @Test
    public void testWithOffsetParsed_secondCall_returnsSameInstance() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withOffsetParsed();
        assertSame(f, f.withOffsetParsed());
    }

    // isOffsetParsed() default is false
    @Test
    public void testIsOffsetParsed_defaultFalse() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertFalse(f.isOffsetParsed());
    }

    // withChronology / getChronology / deprecated getChronolgy agree
    @Test
    public void testWithChronology_setsChronologyOverride() throws Throwable {
        Chronology chrono = ISOChronology.getInstance(DateTimeZone.forID("Asia/Tokyo"));
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withChronology(chrono);
        assertSame(chrono, f.getChronology());
        assertSame(chrono, f.getChronolgy());
    }

    // getChronology() default is null
    @Test
    public void testGetChronology_defaultIsNull() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertNull(f.getChronology());
    }

    // withZoneUTC() sets override zone to UTC
    @Test
    public void testWithZoneUTC_setsUTCZone() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        assertEquals(DateTimeZone.UTC, f.getZone());
    }

    // withZone(null) when already null short-circuits, returns this
    @Test
    public void testWithZone_nullSameAsDefault_returnsSameInstance() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertSame(f, f.withZone(null));
    }

    // withZone(): per javadoc of withOffsetParsed, calling withZone turns offsetParsed off
    @Test
    public void testWithZone_resetsOffsetParsedFlag() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withOffsetParsed();
        DateTimeFormatter f2 = f.withZone(DateTimeZone.UTC);
        assertFalse(f2.isOffsetParsed());
    }

    // getZone() default is null
    @Test
    public void testGetZone_defaultIsNull() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertNull(f.getZone());
    }

    // withPivotYear(Integer) sets the value returned by getPivotYear()
    @Test
    public void testWithPivotYear_IntegerOverload_setsValue() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yy-MM-dd").withPivotYear(Integer.valueOf(1950));
        assertEquals(Integer.valueOf(1950), f.getPivotYear());
    }

    // withPivotYear: equal (but distinct) Integer value short-circuits to same instance
    @Test
    public void testWithPivotYear_sameValueDifferentObject_returnsSameInstance() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yy-MM-dd").withPivotYear(Integer.valueOf(1950));
        DateTimeFormatter f2 = f.withPivotYear(new Integer(1950));
        assertSame(f, f2);
    }

    // withPivotYear(int) overload delegates correctly
    @Test
    public void testWithPivotYear_intOverload_setsValue() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yy-MM-dd").withPivotYear(1975);
        assertEquals(Integer.valueOf(1975), f.getPivotYear());
    }

    // getPivotYear() default is null
    @Test
    public void testGetPivotYear_defaultIsNull() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yy-MM-dd");
        assertNull(f.getPivotYear());
    }

    // getDefaultYear() default value is 2000 (public two-arg constructor)
    @Test
    public void testGetDefaultYear_defaultIs2000() throws Throwable {
        DateTimeFormatter f = new DateTimeFormatter(isoDateTime.getPrinter(), isoDateTime.getParser());
        assertEquals(2000, f.getDefaultYear());
    }

    // withDefaultYear: a leap default year allows Feb 29 to parse
    @Test
    public void testWithDefaultYear_leapYearAllowsFeb29() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("MM-dd").withDefaultYear(1996);
        LocalDate ld = f.parseLocalDate("02-29");
        assertEquals(1996, ld.getYear());
        assertEquals(29, ld.getDayOfMonth());
    }

    // withDefaultYear: a non-leap default year rejects Feb 29
    @Test
    public void testWithDefaultYear_nonLeapYearRejectsFeb29() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("MM-dd").withDefaultYear(1997);
        try {
            f.parseLocalDate("02-29");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // printTo(StringBuffer, ReadableInstant) uses the override zone
    @Test
    public void testPrintTo_StringBuffer_ReadableInstant_usesOverrideZone() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        DateTime dt = new DateTime(2004, 6, 9, 10, 20, 30, 0, DateTimeZone.UTC);
        StringBuffer buf = new StringBuffer();
        f.printTo(buf, dt);
        assertEquals("2004-06-09", buf.toString());
    }

    // printTo(Writer, ReadableInstant) matches the StringBuffer variant
    @Test
    public void testPrintTo_Writer_ReadableInstant_matchesPrint() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        DateTime dt = new DateTime(2004, 6, 9, 10, 20, 30, 0, DateTimeZone.UTC);
        StringWriter sw = new StringWriter();
        f.printTo(sw, dt);
        assertEquals("2004-06-09", sw.toString());
    }

    // printTo(Appendable, ReadableInstant) matches the StringBuffer variant
    @Test
    public void testPrintTo_Appendable_ReadableInstant_matchesPrint() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        DateTime dt = new DateTime(2004, 6, 9, 10, 20, 30, 0, DateTimeZone.UTC);
        StringBuilder sb = new StringBuilder();
        f.printTo(sb, dt);
        assertEquals("2004-06-09", sb.toString());
    }

    // printTo(StringBuffer, long) uses override zone, epoch millis
    @Test
    public void testPrintTo_StringBuffer_long_withZoneUTC() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        StringBuffer buf = new StringBuffer();
        f.printTo(buf, 0L);
        assertEquals("1970-01-01", buf.toString());
    }

    // printTo(Writer, long) matches printTo(StringBuffer, long)
    @Test
    public void testPrintTo_Writer_long_matchesPrint() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        StringWriter sw = new StringWriter();
        f.printTo(sw, 0L);
        assertEquals("1970-01-01", sw.toString());
    }

    // printTo(StringBuffer, ReadablePartial) null partial throws IllegalArgumentException
    @Test
    public void testPrintTo_StringBuffer_ReadablePartial_nullThrows() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        try {
            f.printTo(new StringBuffer(), (ReadablePartial) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // printTo(Writer, ReadablePartial) null partial throws IllegalArgumentException
    @Test
    public void testPrintTo_Writer_ReadablePartial_nullThrows() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        try {
            f.printTo(new StringWriter(), (ReadablePartial) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // printTo(StringBuffer, ReadablePartial) with a real LocalDate
    @Test
    public void testPrintTo_StringBuffer_ReadablePartial_valid() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        LocalDate ld = new LocalDate(2004, 6, 9);
        StringBuffer buf = new StringBuffer();
        f.printTo(buf, ld);
        assertEquals("2004-06-09", buf.toString());
    }

    // print(ReadableInstant) without a printer throws UnsupportedOperationException
    @Test
    public void testPrint_ReadableInstant_withoutPrinter_throwsUnsupported() throws Throwable {
        DateTimeFormatter f = new DateTimeFormatter(null, isoDateTime.getParser());
        try {
            f.print(new DateTime(0L, DateTimeZone.UTC));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // print(long) without a printer throws UnsupportedOperationException
    @Test
    public void testPrint_long_withoutPrinter_throwsUnsupported() throws Throwable {
        DateTimeFormatter f = new DateTimeFormatter(null, isoDateTime.getParser());
        try {
            f.print(0L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // print(ReadablePartial) formats a LocalDate correctly
    @Test
    public void testPrint_ReadablePartial_valid() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        LocalDate ld = new LocalDate(2004, 6, 9);
        assertEquals("2004-06-09", f.print(ld));
    }

    // print(): override zone takes precedence over override chronology's zone
    @Test
    public void testPrint_zoneOverrideTakesPrecedenceOverChronology() throws Throwable {
        Chronology tokyo = ISOChronology.getInstance(DateTimeZone.forID("Asia/Tokyo"));
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
                .withChronology(tokyo).withZone(DateTimeZone.UTC);
        DateTime dt = new DateTime(2004, 6, 9, 10, 20, 30, 0, DateTimeZone.UTC);
        assertEquals("2004-06-09 10:20:30", f.print(dt));
    }

    // print(): formatting honours the override zone over the instant's own zone
    @Test
    public void testPrint_usesOverrideZoneForInstant() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(DateTimeZone.forID("America/New_York"));
        DateTime dt = new DateTime(2004, 6, 9, 10, 20, 30, 0, DateTimeZone.UTC);
        assertEquals("2004-06-09 06:20:30", f.print(dt));
    }

    // parseInto(): null instant throws IllegalArgumentException
    @Test
    public void testParseInto_nullInstant_throwsIllegalArgument() throws Throwable {
        try {
            isoDateTime.parseInto(null, "2004-06-09T10:20:30.000+02:00", 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parseInto(): a fully-parsed valid text returns text length and updates fields
    @Test
    public void testParseInto_validText_updatesFields() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, ISOChronology.getInstanceUTC());
        String text = "2004-06-09T10:20:30.000+02:00";
        int newPos = isoDateTime.parseInto(mdt, text, 0);
        assertEquals(text.length(), newPos);
        assertEquals(2004, mdt.getYear());
        assertEquals(9, mdt.getDayOfMonth());
    }

    // parseInto(): invalid text returns a negative position
    @Test
    public void testParseInto_invalidText_returnsNegativePosition() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, ISOChronology.getInstanceUTC());
        int newPos = isoDateTime.parseInto(mdt, "not-a-date-at-all", 0);
        assertTrue(newPos < 0);
    }

    // parseMillis(): computes the correct instant honouring an override zone
    @Test
    public void testParseMillis_validText_computesExpectedInstant() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")
                .withZone(DateTimeZone.forID("America/New_York"));
        long millis = f.parseMillis("2004-06-09T10:20:30");
        long expected = new DateTime(2004, 6, 9, 10, 20, 30, 0,
                DateTimeZone.forID("America/New_York")).getMillis();
        assertEquals(expected, millis);
    }

    // parseMillis(): invalid text throws IllegalArgumentException
    @Test
    public void testParseMillis_invalidText_throwsIllegalArgument() throws Throwable {
        try {
            isoDateTime.parseMillis("garbage");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parseMillis(): without a parser throws UnsupportedOperationException
    @Test
    public void testParseMillis_withoutParser_throwsUnsupported() throws Throwable {
        DateTimeFormatter f = new DateTimeFormatter(isoDateTime.getPrinter(), null);
        try {
            f.parseMillis("2004-06-09T10:20:30.000+02:00");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // parseLocalDate(): per javadoc, any parsed zone/offset is completely ignored
    @Test
    public void testParseLocalDate_ignoresTimeZoneOffset() throws Throwable {
        LocalDate ld = isoDateTime.parseLocalDate("2004-06-09T10:20:30.000+05:00");
        assertEquals(2004, ld.getYear());
        assertEquals(6, ld.getMonthOfYear());
        assertEquals(9, ld.getDayOfMonth());
    }

    // parseLocalTime(): parses hour/minute/second fields
    @Test
    public void testParseLocalTime_parsesFields() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("HH:mm:ss");
        LocalTime lt = f.parseLocalTime("10:20:30");
        assertEquals(10, lt.getHourOfDay());
        assertEquals(20, lt.getMinuteOfHour());
        assertEquals(30, lt.getSecondOfMinute());
    }

    // parseLocalDateTime(): parses full date-time fields
    @Test
    public void testParseLocalDateTime_parsesFields() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime ldt = f.parseLocalDateTime("2004-06-09T10:20:30");
        assertEquals(2004, ldt.getYear());
        assertEquals(9, ldt.getDayOfMonth());
        assertEquals(10, ldt.getHourOfDay());
    }



    // parseDateTime(): withOffsetParsed() uses a fixed-offset zone from the parsed text
    @Test
    public void testParseDateTime_withOffsetParsed_usesFixedOffsetZone() throws Throwable {
        DateTimeFormatter f = isoDateTime.withOffsetParsed();
        DateTime dt = f.parseDateTime("2004-06-09T10:20:30.000+02:00");
        assertEquals(DateTimeZone.forOffsetHoursMinutes(2, 0), dt.getZone());
        assertEquals(10, dt.getHourOfDay());
    }

    // parseDateTime(): incomplete parse (trailing garbage) throws IllegalArgumentException
    @Test
    public void testParseDateTime_incompleteText_throwsIllegalArgument() throws Throwable {
        try {
            isoDateTime.parseDateTime("2004-06-09T10:20:30.000+02:00EXTRA");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parseDateTime(): without a parser throws UnsupportedOperationException
    @Test
    public void testParseDateTime_withoutParser_throwsUnsupported() throws Throwable {
        DateTimeFormatter f = new DateTimeFormatter(isoDateTime.getPrinter(), null);
        try {
            f.parseDateTime("2004-06-09T10:20:30.000+02:00");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // parseMutableDateTime(): override zone is applied to the resulting instant
    @Test
    public void testParseMutableDateTime_withZoneOverride() throws Throwable {
        DateTimeFormatter f = isoDateTime.withZone(DateTimeZone.forID("Europe/Paris"));
        MutableDateTime mdt = f.parseMutableDateTime("2004-06-09T10:20:30.000+02:00");
        assertEquals("Europe/Paris", mdt.getZone().getID());
        assertEquals(10, mdt.getHourOfDay());
    }

    // parseMutableDateTime(): incomplete parse throws IllegalArgumentException
    @Test
    public void testParseMutableDateTime_incompleteText_throwsIllegalArgument() throws Throwable {
        try {
            isoDateTime.parseMutableDateTime("2004-06-09T10:20:30.000+02:00EXTRA");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
