package org.joda.time.format;

import junit.framework.TestCase;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.Locale;

import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.MutableDateTime;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.chrono.GJChronology;

public class DateTimeFormatterTest extends TestCase {

    public DateTimeFormatterTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testGettersAndPrintersParsers() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertNotNull(f.getPrinter());
        assertNotNull(f.getParser());
        assertTrue(f.isPrinter());
        assertTrue(f.isParser());

        DateTimeFormatter printerOnly = new DateTimeFormatter(f.getPrinter(), null);
        assertTrue(printerOnly.isPrinter());
        assertFalse(printerOnly.isParser());
        assertNotNull(printerOnly.getPrinter());
        assertNull(printerOnly.getParser());

        DateTimeFormatter parserOnly = new DateTimeFormatter(null, f.getParser());
        assertFalse(parserOnly.isPrinter());
        assertTrue(parserOnly.isParser());
        assertNull(parserOnly.getPrinter());
        assertNotNull(parserOnly.getParser());
    }

    public void testWithLocale() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        DateTimeFormatter fFrench = f.withLocale(Locale.FRENCH);
        assertNotNull(fFrench);
        assertEquals(Locale.FRENCH, fFrench.getLocale());
        
        assertSame(fFrench, fFrench.withLocale(Locale.FRENCH));
        
        DateTimeFormatter fNull = f.withLocale(null);
        assertNull(fNull.getLocale());
    }

    public void testWithOffsetParsed() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertFalse(f.isOffsetParsed());
        DateTimeFormatter fOffset = f.withOffsetParsed();
        assertTrue(fOffset.isOffsetParsed());
        assertSame(fOffset, fOffset.withOffsetParsed());
    }

    public void testWithChronology() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertNull(f.getChronology());
        assertNull(f.getChronolgy());

        Chronology chrono = GJChronology.getInstance();
        DateTimeFormatter fChrono = f.withChronology(chrono);
        assertEquals(chrono, fChrono.getChronology());
        assertEquals(chrono, fChrono.getChronolgy());
        assertSame(fChrono, fChrono.withChronology(chrono));
    }

    public void testWithZone() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        assertNull(f.getZone());

        DateTimeZone zone = DateTimeZone.forID("Europe/Paris");
        DateTimeFormatter fZone = f.withZone(zone);
        assertEquals(zone, fZone.getZone());
        assertSame(fZone, fZone.withZone(zone));

        DateTimeFormatter fUtc = f.withZoneUTC();
        assertEquals(DateTimeZone.UTC, fUtc.getZone());
    }

    public void testWithPivotYear() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yy-MM-dd");
        assertNull(f.getPivotYear());

        DateTimeFormatter fPivot1 = f.withPivotYear(2020);
        assertEquals(Integer.valueOf(2020), fPivot1.getPivotYear());
        assertSame(fPivot1, fPivot1.withPivotYear(2020));

        DateTimeFormatter fPivot2 = f.withPivotYear(Integer.valueOf(1950));
        assertEquals(Integer.valueOf(1950), fPivot2.getPivotYear());
    }

    public void testWithDefaultYear() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("MM-dd");
        assertEquals(2000, f.getDefaultYear());

        DateTimeFormatter fDefault = f.withDefaultYear(2012);
        assertEquals(2012, fDefault.getDefaultYear());
    }

    public void testPrintStringBufferAndWriter() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        DateTime dt = new DateTime(2010, 6, 15, 12, 30, 0, 0, DateTimeZone.UTC);

        StringBuffer sb = new StringBuffer();
        f.printTo(sb, dt);
        assertEquals("2010-06-15", sb.toString());

        CharArrayWriter writer = new CharArrayWriter();
        f.printTo(writer, dt);
        assertEquals("2010-06-15", writer.toString());

        StringBuilder appendable = new StringBuilder();
        f.printTo(appendable, dt);
        assertEquals("2010-06-15", appendable.toString());

        StringBuffer sbMillis = new StringBuffer();
        f.printTo(sbMillis, dt.getMillis());
        assertEquals("2010-06-15", sbMillis.toString());

        CharArrayWriter writerMillis = new CharArrayWriter();
        f.printTo(writerMillis, dt.getMillis());
        assertEquals("2010-06-15", writerMillis.toString());

        StringBuilder appendableMillis = new StringBuilder();
        f.printTo(appendableMillis, dt.getMillis());
        assertEquals("2010-06-15", appendableMillis.toString());
    }

    public void testPrintReadablePartial() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");
        LocalDate date = new LocalDate(2010, 6, 15);

        StringBuffer sb = new StringBuffer();
        f.printTo(sb, date);
        assertEquals("2010-06-15", sb.toString());

        CharArrayWriter writer = new CharArrayWriter();
        f.printTo(writer, date);
        assertEquals("2010-06-15", writer.toString());

        StringBuilder appendable = new StringBuilder();
        f.printTo(appendable, date);
        assertEquals("2010-06-15", appendable.toString());

        assertEquals("2010-06-15", f.print(date));

        try {
            f.printTo((StringBuffer) null, (ReadablePartial) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null"));
        }

        try {
            f.printTo((Writer) null, (ReadablePartial) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null"));
        }
    }

    public void testPrintEdgeCases() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withZone(DateTimeZone.forOffsetHours(2));
        String result = f.print(Long.MAX_VALUE);
        assertNotNull(result);

        DateTimeFormatter parserOnly = new DateTimeFormatter(null, DateTimeFormat.forPattern("yyyy-MM-dd").getParser());
        try {
            parserOnly.print(new DateTime());
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing not supported"));
        }
    }

    public void testParseMethods() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();

        long millis = f.parseMillis("2010-06-15");
        assertEquals(new DateTime(2010, 6, 15, 0, 0, 0, 0, DateTimeZone.UTC).getMillis(), millis);

        LocalDate date = f.parseLocalDate("2010-06-15");
        assertEquals(new LocalDate(2010, 6, 15), date);

        LocalTime time = DateTimeFormat.forPattern("HH:mm:ss").withZoneUTC().parseLocalTime("12:30:45");
        assertEquals(new LocalTime(12, 30, 45), time);

        LocalDateTime dateTime = f.parseLocalDateTime("2010-06-15");
        assertEquals(new LocalDateTime(2010, 6, 15, 0, 0), dateTime);

        DateTime parsedDt = f.parseDateTime("2010-06-15");
        assertEquals(new DateTime(2010, 6, 15, 0, 0, 0, 0, DateTimeZone.UTC), parsedDt);

        MutableDateTime mutableDt = f.parseMutableDateTime("2010-06-15");
        assertEquals(new MutableDateTime(2010, 6, 15, 0, 0, 0, 0, DateTimeZone.UTC), mutableDt);
    }

    public void testParseErrors() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd");

        try {
            f.parseMillis("invalid");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }

        try {
            f.parseLocalDateTime("invalid");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }

        try {
            f.parseDateTime("invalid");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }

        try {
            f.parseMutableDateTime("invalid");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }

        DateTimeFormatter printerOnly = new DateTimeFormatter(DateTimeFormat.forPattern("yyyy-MM-dd").getPrinter(), null);
        try {
            printerOnly.parseMillis("2010-06-15");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Parsing not supported"));
        }
    }

    public void testParseInto() throws Throwable {
        DateTimeFormatter f = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC();
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);

        int pos = f.parseInto(mdt, "2010-06-15", 0);
        assertEquals(10, pos);
        assertEquals(new DateTime(2010, 6, 15, 0, 0, 0, 0, DateTimeZone.UTC).getMillis(), mdt.getMillis());

        try {
            f.parseInto(null, "2010-06-15", 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Instant must not be null"));
        }
    }

    public void testParseWithOffsetAndZoneOverrides() throws Throwable {
        DateTimeFormatter fOffset = DateTimeFormat.forPattern("yyyy-MM-dd Z").withOffsetParsed();
        DateTime dt = fOffset.parseDateTime("2010-06-15 -05:00");
        assertNotNull(dt);

        DateTimeFormatter fZone = DateTimeFormat.forPattern("yyyy-MM-dd").withZone(DateTimeZone.forID("Europe/Paris"));
        DateTime dtZone = fZone.parseDateTime("2010-06-15");
        assertEquals(DateTimeZone.forID("Europe/Paris"), dtZone.getZone());
    }
}