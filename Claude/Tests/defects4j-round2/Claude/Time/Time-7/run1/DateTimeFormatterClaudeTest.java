package org.joda.time.format;

import java.io.StringWriter;
import java.util.Locale;

import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.MutableDateTime;
import org.joda.time.ReadablePartial;
import org.joda.time.chrono.ISOChronology;

import org.junit.Test;
import static org.junit.Assert.*;

public class DateTimeFormatterClaudeTest {

    // constructor with both printer and parser null: isPrinter/isParser false, getters null
    @Test
    public void testConstructor_bothNull_isPrinterFalseIsParserFalse() throws Throwable {
        DateTimeFormatter fmt = new DateTimeFormatter(null, null);
        assertFalse(fmt.isPrinter());
        assertFalse(fmt.isParser());
        assertNull(fmt.getPrinter());
        assertNull(fmt.getParser());
    }

    // constructor with both printer and parser set: isPrinter/isParser true, getters return same instances
    @Test
    public void testConstructor_withPrinterAndParser_isPrinterTrueIsParserTrueAndGettersReturnSame() throws Throwable {
        DateTimeFormatter real = DateTimeFormat.forPattern("yyyy");
        DateTimeFormatter fmt = new DateTimeFormatter(real.getPrinter(), real.getParser());
        assertTrue(fmt.isPrinter());
        assertTrue(fmt.isParser());
        assertSame(real.getPrinter(), fmt.getPrinter());
        assertSame(real.getParser(), fmt.getParser());
    }

    // withLocale: same null locale reference triggers `locale == getLocale()` branch -> returns same instance
    @Test
    public void testWithLocale_sameNullLocale_returnsSameInstance() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy");
        assertNull(fmt.getLocale());
        assertSame(fmt, fmt.withLocale(null));
    }

    // withLocale: distinct non-null locale creates a new formatter carrying that locale
    @Test
    public void testWithLocale_newLocale_updatesLocale() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy");
        DateTimeFormatter fmt2 = fmt.withLocale(Locale.FRENCH);
        assertEquals(Locale.FRENCH, fmt2.getLocale());
        assertNotSame(fmt, fmt2);
    }

    // withLocale: equal (but not identical) locale object hits the equals branch and returns same instance
    @Test
    public void testWithLocale_equalLocale_returnsSameInstance() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy").withLocale(Locale.FRENCH);
        Locale equalLocale = new Locale("fr");
        assertSame(fmt, fmt.withLocale(equalLocale));
    }

    // getLocale defaults to null for a freshly built formatter
    @Test
    public void testGetLocale_defaultIsNull() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("HH:mm");
        assertNull(fmt.getLocale());
    }

    // withOffsetParsed sets the flag true and clears any override zone per javadoc
    @Test
    public void testWithOffsetParsed_setsFlagAndClearsZone() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy").withZone(DateTimeZone.UTC);
        DateTimeFormatter fmt2 = fmt.withOffsetParsed();
        assertTrue(fmt2.isOffsetParsed());
        assertNull(fmt2.getZone());
    }

    // calling withOffsetParsed a second time (flag already true) returns the same instance
    @Test
    public void testWithOffsetParsed_calledWhenAlreadyTrue_returnsSameInstance() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy").withOffsetParsed();
        assertSame(fmt, fmt.withOffsetParsed());
    }

    // isOffsetParsed defaults to false for a freshly built formatter
    @Test
    public void testIsOffsetParsed_defaultFalse() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy");
        assertFalse(fmt.isOffsetParsed());
    }

    // withChronology stores the override chronology, retrievable via getChronology
    @Test
    public void testWithChronology_setsOverrideChronology() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy").withChronology(chrono);
        assertSame(chrono, fmt.getChronology());
    }

    // deprecated getChronolgy must return the exact same value as getChronology
    @Test
    public void testGetChronolgy_matchesGetChronology() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy").withChronology(chrono);
        assertSame(fmt.getChronology(), fmt.getChronolgy());
    }

    // withChronology with the same reference already stored (null) returns the same formatter instance
    @Test
    public void testWithChronology_sameReference_returnsSameInstance() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy");
        assertSame(fmt, fmt.withChronology(null));
    }

    // withZoneUTC overrides the zone to DateTimeZone.UTC
    @Test
    public void testWithZoneUTC_setsUTCZone() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy").withZoneUTC();
        assertEquals(DateTimeZone.UTC, fmt.getZone());
    }

    // withZone sets the override zone and always resets offsetParsed to false
    @Test
    public void testWithZone_setsZoneAndResetsOffsetParsed() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy").withOffsetParsed();
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        DateTimeFormatter fmt2 = fmt.withZone(zone);
        assertEquals(zone, fmt2.getZone());
        assertFalse(fmt2.isOffsetParsed());
    }

    // withZone(null) clears any previously set override zone
    @Test
    public void testWithZone_nullClearsOverride() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy").withZoneUTC();
        DateTimeFormatter fmt2 = fmt.withZone(null);
        assertNull(fmt2.getZone());
    }

    // getZone defaults to null for a freshly built formatter
    @Test
    public void testGetZone_defaultIsNull() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy");
        assertNull(fmt.getZone());
    }

    // withPivotYear(Integer) stores the given pivot year
    @Test
    public void testWithPivotYear_IntegerOverload() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yy-MM-dd").withPivotYear(Integer.valueOf(1950));
        assertEquals(Integer.valueOf(1950), fmt.getPivotYear());
    }

    // withPivotYear(int) primitive overload delegates and stores an equivalent Integer
    @Test
    public void testWithPivotYear_intOverload() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yy-MM-dd").withPivotYear(2000);
        assertEquals(2000, fmt.getPivotYear().intValue());
    }

    // withPivotYear called again with an equal value returns the same instance
    @Test
    public void testWithPivotYear_sameValue_returnsSameInstance() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yy-MM-dd").withPivotYear(2000);
        assertSame(fmt, fmt.withPivotYear(Integer.valueOf(2000)));
    }

    // getPivotYear defaults to null when never overridden
    @Test
    public void testGetPivotYear_defaultIsNull() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yy-MM-dd");
        assertNull(fmt.getPivotYear());
    }

    // withDefaultYear overrides the default year used when only month/day are parsed
    @Test
    public void testWithDefaultYear_changesValue() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("MM-dd").withDefaultYear(1996);
        assertEquals(1996, fmt.getDefaultYear());
    }

    // getDefaultYear defaults to 2000 per javadoc when never overridden
    @Test
    public void testGetDefaultYear_defaultIs2000() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("MM-dd");
        assertEquals(2000, fmt.getDefaultYear());
    }

    // printTo(StringBuffer, ReadableInstant) prints using the instant's own chronology/zone
    @Test
    public void testPrintTo_StringBuffer_ReadableInstant() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        DateTime dt = new DateTime(2004, 6, 9, 0, 0, 0, 0, DateTimeZone.UTC);
        StringBuffer buf = new StringBuffer();
        fmt.printTo(buf, dt);
        assertEquals("2004-06-09", buf.toString());
    }

    // printTo(Writer, ReadableInstant) prints the same content to a Writer destination
    @Test
    public void testPrintTo_Writer_ReadableInstant() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        DateTime dt = new DateTime(2004, 6, 9, 0, 0, 0, 0, DateTimeZone.UTC);
        StringWriter writer = new StringWriter();
        fmt.printTo(writer, dt);
        assertEquals("2004-06-09", writer.toString());
    }

    // print(long) formats a millisecond instant using the formatter's override zone (UTC here)
    @Test
    public void testPrint_long() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        long millis = new DateTime(2004, 6, 9, 0, 0, 0, 0, DateTimeZone.UTC).getMillis();
        assertEquals("2004-06-09", fmt.print(millis));
    }

    // print(ReadablePartial) formats a LocalDate ignoring any override zone/chronology
    @Test
    public void testPrint_ReadablePartial() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        LocalDate ld = new LocalDate(2004, 6, 9, ISOChronology.getInstanceUTC());
        assertEquals("2004-06-09", fmt.print(ld));
    }

    // print on a formatter with no printer must throw UnsupportedOperationException
    @Test
    public void testPrint_noPrinter_throwsUnsupportedOperationException() throws Throwable {
        DateTimeFormatter real = DateTimeFormat.forPattern("yyyy");
        DateTimeFormatter parserOnly = new DateTimeFormatter(null, real.getParser());
        try {
            parserOnly.print(0L);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // printTo(StringBuffer, ReadablePartial) with a null partial must throw IllegalArgumentException
    @Test
    public void testPrintTo_partialNull_throwsIllegalArgumentException() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        StringBuffer buf = new StringBuffer();
        try {
            fmt.printTo(buf, (ReadablePartial) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // when both override chronology and override zone are set, the override zone takes precedence when printing
    @Test
    public void testPrintTo_zoneOverridesChronologyZone() throws Throwable {
        Chronology nyChrono = ISOChronology.getInstance(DateTimeZone.forID("America/New_York"));
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd")
                .withChronology(nyChrono).withZone(DateTimeZone.UTC);
        long millis = new DateTime(2004, 1, 1, 2, 0, 0, 0, DateTimeZone.UTC).getMillis();
        assertEquals("2004-01-01", fmt.print(millis));
    }

    // parseInto updates only the fields present in the text and returns the new position
    @Test
    public void testParseInto_updatesFieldsAndReturnsPosition() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        MutableDateTime instant = new MutableDateTime(1999, 12, 31, 0, 0, 0, 0, DateTimeZone.UTC);
        int newPos = fmt.parseInto(instant, "2004-06-09", 0);
        assertEquals(10, newPos);
        assertEquals(2004, instant.getYear());
        assertEquals(6, instant.getMonthOfYear());
        assertEquals(9, instant.getDayOfMonth());
    }

    // parseInto with a null instant must throw IllegalArgumentException
    @Test
    public void testParseInto_nullInstant_throwsIllegalArgumentException() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        try {
            fmt.parseInto(null, "2004-06-09", 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parseInto on a formatter with no parser must throw UnsupportedOperationException
    @Test
    public void testParseInto_noParser_throwsUnsupportedOperationException() throws Throwable {
        DateTimeFormatter real = DateTimeFormat.forPattern("yyyy-MM-dd");
        DateTimeFormatter printerOnly = new DateTimeFormatter(real.getPrinter(), null);
        MutableDateTime instant = new MutableDateTime(2000, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        try {
            printerOnly.parseInto(instant, "2004-06-09", 0);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // parseMillis returns the millisecond instant matching the parsed literal date (UTC override)
    @Test
    public void testParseMillis_validText_matchesExpectedMillis() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        long millis = fmt.parseMillis("2004-06-09");
        long expected = new DateTime(2004, 6, 9, 0, 0, 0, 0, DateTimeZone.UTC).getMillis();
        assertEquals(expected, millis);
    }

    // parseMillis on malformed text must throw IllegalArgumentException
    @Test
    public void testParseMillis_malformedText_throwsIllegalArgumentException() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        try {
            fmt.parseMillis("not-a-date");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parseMillis with unparsed trailing text must throw IllegalArgumentException
    @Test
    public void testParseMillis_trailingExtraText_throwsIllegalArgumentException() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        try {
            fmt.parseMillis("2004-06-09XYZ");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parseLocalDate keeps only the date part, ignoring the time component in the text
    @Test
    public void testParseLocalDate_returnsDateIgnoringTime() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDate date = fmt.parseLocalDate("2004-06-09T23:59:59");
        assertEquals(new LocalDate(2004, 6, 9, ISOChronology.getInstanceUTC()), date);
    }

    // parseLocalTime keeps only the time part of the parsed text
    @Test
    public void testParseLocalTime_returnsTimeOnly() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalTime time = fmt.parseLocalTime("2004-06-09T10:20:30");
        assertEquals(new LocalTime(10, 20, 30, 0, ISOChronology.getInstanceUTC()), time);
    }

    // parseLocalDateTime with no zone/offset in the text simply reflects the literal fields
    @Test
    public void testParseLocalDateTime_withoutOffset_matchesLiteralFields() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime result = fmt.parseLocalDateTime("2004-06-09T10:20:30");
        assertEquals(new LocalDateTime(2004, 6, 9, 10, 20, 30, 0, ISOChronology.getInstanceUTC()), result);
    }

    // javadoc: a parsed time-zone/offset field must be completely ignored by parseLocalDateTime
    @Test
    public void testParseLocalDateTime_withOffsetInText_offsetMustBeIgnored() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ");
        LocalDateTime result = fmt.parseLocalDateTime("2004-06-09T10:20:30+0800");
        assertEquals(new LocalDateTime(2004, 6, 9, 10, 20, 30, 0, ISOChronology.getInstanceUTC()), result);
    }

    // pivot year applies to two-digit year parsing performed through parseLocalDate
    @Test
    public void testParseLocalDateTime_withPivotYear_resolvesTwoDigitYear() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yy-MM-dd").withPivotYear(2050);
        LocalDate date = fmt.parseLocalDate("50-01-01");
        assertEquals(new LocalDate(2050, 1, 1, ISOChronology.getInstanceUTC()), date);
    }

    // default year (2000, a leap year) lets Feb 29 be parsed when only month/day are present
    @Test
    public void testParseLocalDateTime_withDefaultYear_resolvesLeapDay() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("MM-dd");
        LocalDate date = fmt.parseLocalDate("02-29");
        assertEquals(new LocalDate(2000, 2, 29, ISOChronology.getInstanceUTC()), date);
    }

    // overriding default year to a non-leap year makes Feb 29 invalid -> IllegalArgumentException
    @Test
    public void testParseLocalDateTime_invalidDefaultYear_throwsIllegalArgumentException() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("MM-dd").withDefaultYear(2001);
        try {
            fmt.parseLocalDate("02-29");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // parseDateTime applies the formatter's override zone to the resulting DateTime
    @Test
    public void testParseDateTime_appliesOverrideZone() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(DateTimeZone.UTC);
        DateTime dt = fmt.parseDateTime("2004-06-09T10:20:30");
        assertEquals(new DateTime(2004, 6, 9, 10, 20, 30, 0, DateTimeZone.UTC), dt);
        assertEquals(DateTimeZone.UTC, dt.getZone());
    }

    // with withOffsetParsed(), the parsed offset becomes a fixed-offset zone on the result
    @Test
    public void testParseDateTime_offsetParsedTrue_usesFixedOffsetZone() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ")
                .withChronology(ISOChronology.getInstanceUTC()).withOffsetParsed();
        DateTime dt = fmt.parseDateTime("2004-06-09T10:20:30+0800");
        assertEquals(DateTimeZone.forOffsetHours(8), dt.getZone());
        long expectedMillis = new DateTime(2004, 6, 9, 10, 20, 30, 0, ISOChronology.getInstanceUTC()).getMillis();
        assertEquals(expectedMillis, dt.getMillis());
    }

    // parseMutableDateTime returns a MutableDateTime whose millis/zone follow the same rules as parseDateTime
    @Test
    public void testParseMutableDateTime_returnsCorrectMillisAndZone() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        MutableDateTime mdt = fmt.parseMutableDateTime("2004-06-09");
        long expected = new DateTime(2004, 6, 9, 0, 0, 0, 0, DateTimeZone.UTC).getMillis();
        assertEquals(expected, mdt.getMillis());
        assertEquals(DateTimeZone.UTC, mdt.getZone());
    }
}
