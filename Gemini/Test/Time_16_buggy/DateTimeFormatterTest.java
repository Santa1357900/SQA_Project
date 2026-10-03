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

    protected void setUp() throws Throwable {
        super.setUp();
    }

    protected void tearDown() throws Throwable {
        super.tearDown();
    }

    public void testGettersAndPrintersParsers() throws Throwable {
        DateTimeFormatter formatter = new DateTimeFormatter(null, null);
        assertFalse(formatter.isPrinter());
        assertNull(formatter.getPrinter());
        assertFalse(formatter.isParser());
        assertNull(formatter.getParser());
        
        DateTimePrinter dummyPrinter = DateTimeFormat.forPattern("yyyy").getPrinter();
        DateTimeParser dummyParser = DateTimeFormat.forPattern("yyyy").getParser();
        
        DateTimeFormatter validFormatter = new DateTimeFormatter(dummyPrinter, dummyParser);
        assertTrue(validFormatter.isPrinter());
        assertNotNull(validFormatter.getPrinter());
        assertTrue(validFormatter.isParser());
        assertNotNull(validFormatter.getParser());
    }

    public void testWithLocale() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy");
        assertSame(fmt, fmt.withLocale(null));
        assertSame(fmt, fmt.withLocale(Locale.getDefault()));
        
        DateTimeFormatter frenchFmt = fmt.withLocale(Locale.FRENCH);
        assertEquals(Locale.FRENCH, frenchFmt.getLocale());
        assertSame(frenchFmt, frenchFmt.withLocale(Locale.FRENCH));
    }

    public void testWithOffsetParsed() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy");
        assertFalse(fmt.isOffsetParsed());
        
        DateTimeFormatter offsetFmt = fmt.withOffsetParsed();
        assertTrue(offsetFmt.isOffsetParsed());
        assertSame(offsetFmt, offsetFmt.withOffsetParsed());
    }

    public void testWithChronology() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy");
        assertNull(fmt.getChronology());
        assertNull(fmt.getChronolgy());
        
        assertSame(fmt, fmt.withChronology(null));
        
        Chronology chrono = GJChronology.getInstance();
        DateTimeFormatter chronoFmt = fmt.withChronology(chrono);
        assertEquals(chrono, chronoFmt.getChronology());
        assertEquals(chrono, chronoFmt.getChronolgy());
        assertSame(chronoFmt, chronoFmt.withChronology(chrono));
    }

    public void testWithZone() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy");
        assertNull(fmt.getZone());
        
        assertSame(fmt, fmt.withZone(null));
        
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        DateTimeFormatter zoneFmt = fmt.withZone(zone);
        assertEquals(zone, zoneFmt.getZone());
        assertSame(zoneFmt, zoneFmt.withZone(zone));
        
        DateTimeFormatter utcFmt = fmt.withZoneUTC();
        assertEquals(DateTimeZone.UTC, utcFmt.getZone());
    }

    public void testWithPivotYear() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yy");
        assertNull(fmt.getPivotYear());
        
        assertSame(fmt, fmt.withPivotYear((Integer) null));
        
        DateTimeFormatter pivotFmt1 = fmt.withPivotYear(2020);
        assertEquals(Integer.valueOf(2020), pivotFmt1.getPivotYear());
        assertSame(pivotFmt1, pivotFmt1.withPivotYear(2020));
        assertSame(pivotFmt1, pivotFmt1.withPivotYear(Integer.valueOf(2020)));
    }

    public void testDefaultYear() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("MM-dd");
        assertEquals(2000, fmt.getDefaultYear());
        
        DateTimeFormatter defFmt = fmt.withDefaultYear(2012);
        assertEquals(2012, defFmt.getDefaultYear());
    }

    public void testRequirePrinterExceptions() throws Throwable {
        DateTimeFormatter fmt = new DateTimeFormatter(null, null);
        try {
            fmt.print(123456789L);
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing not supported"));
        }

        try {
            fmt.print(new DateTime());
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing not supported"));
        }

        try {
            fmt.print(new LocalDate());
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing not supported"));
        }

        try {
            StringBuffer buf = new StringBuffer();
            fmt.printTo(buf, 123456789L);
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing not supported"));
        }

        try {
            CharArrayWriter out = new CharArrayWriter();
            fmt.printTo(out, 123456789L);
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing not supported"));
        }

        try {
            StringBuilder sb = new StringBuilder();
            fmt.printTo(sb, 123456789L);
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing not supported"));
        }

        try {
            StringBuffer buf = new StringBuffer();
            fmt.printTo(buf, (ReadableInstant) null);
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing not supported"));
        }

        try {
            CharArrayWriter out = new CharArrayWriter();
            fmt.printTo(out, (ReadableInstant) null);
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing not supported"));
        }

        try {
            StringBuilder sb = new StringBuilder();
            fmt.printTo(sb, (ReadableInstant) null);
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing not supported"));
        }

        try {
            StringBuffer buf = new StringBuffer();
            fmt.printTo(buf, (LocalDate) null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("partial must not be null"));
        }

        try {
            CharArrayWriter out = new CharArrayWriter();
            fmt.printTo(out, (LocalDate) null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("partial must not be null"));
        }

        try {
            StringBuilder sb = new StringBuilder();
            fmt.printTo(sb, (LocalDate) null);
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing not supported"));
        }
    }

    public void testRequireParserExceptions() throws Throwable {
        DateTimeFormatter fmt = new DateTimeFormatter(null, null);
        try {
            fmt.parseMillis("2007");
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Parsing not supported"));
        }

        try {
            fmt.parseDateTime("2007");
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Parsing not supported"));
        }

        try {
            fmt.parseLocalDateTime("2007");
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Parsing not supported"));
        }

        try {
            fmt.parseLocalDate("2007");
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Parsing not supported"));
        }

        try {
            fmt.parseLocalTime("12:00");
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Parsing not supported"));
        }

        try {
            fmt.parseMutableDateTime("2007");
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Parsing not supported"));
        }

        try {
            MutableDateTime mdt = new MutableDateTime();
            fmt.parseInto(mdt, "2007", 0);
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Parsing not supported"));
        }
    }

    public void testPrintMethodsValid() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        DateTime dt = new DateTime(2007, 6, 9, 10, 20, 30, DateTimeZone.UTC);
        
        assertEquals("2007-06-09", fmt.print(dt));
        assertEquals("2007-06-09", fmt.print(dt.getMillis()));
        assertEquals("2007-06-09", fmt.print(dt.toLocalDate()));
        
        StringBuffer buf1 = new StringBuffer();
        fmt.printTo(buf1, dt);
        assertEquals("2007-06-09", buf1.toString());

        StringBuffer buf2 = new StringBuffer();
        fmt.printTo(buf2, dt.getMillis());
        assertEquals("2007-06-09", buf2.toString());

        StringBuffer buf3 = new StringBuffer();
        fmt.printTo(buf3, dt.toLocalDate());
        assertEquals("2007-06-09", buf3.toString());

        CharArrayWriter out1 = new CharArrayWriter();
        fmt.printTo(out1, dt);
        assertEquals("2007-06-09", out1.toString());

        CharArrayWriter out2 = new CharArrayWriter();
        fmt.printTo(out2, dt.getMillis());
        assertEquals("2007-06-09", out2.toString());

        CharArrayWriter out3 = new CharArrayWriter();
        fmt.printTo(out3, dt.toLocalDate());
        assertEquals("2007-06-09", out3.toString());

        StringBuilder sb1 = new StringBuilder();
        fmt.printTo(sb1, dt);
        assertEquals("2007-06-09", sb1.toString());

        StringBuilder sb2 = new StringBuilder();
        fmt.printTo(sb2, dt.getMillis());
        assertEquals("2007-06-09", sb2.toString());

        StringBuilder sb3 = new StringBuilder();
        fmt.printTo(sb3, dt.toLocalDate());
        assertEquals("2007-06-09", sb3.toString());
    }

    public void testPrintOverflowEdgeCase() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        // Test extreme values that trigger offset overflow calculation branch
        long maxInstant = Long.MAX_VALUE;
        String res1 = fmt.print(maxInstant);
        assertNotNull(res1);

        long minInstant = Long.MIN_VALUE;
        String res2 = fmt.print(minInstant);
        assertNotNull(res2);
    }

    public void testParseMethodsValid() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        
        long millis = fmt.parseMillis("2007-06-09");
        assertTrue(millis > 0);
        
        DateTime dt = fmt.parseDateTime("2007-06-09");
        assertEquals(2007, dt.getYear());
        assertEquals(6, dt.getMonthOfYear());
        assertEquals(9, dt.getDayOfMonth());
        
        LocalDateTime ldt = fmt.parseLocalDateTime("2007-06-09");
        assertEquals(2007, ldt.getYear());
        
        LocalDate ld = fmt.parseLocalDate("2007-06-09");
        assertEquals(2007, ld.getYear());
        
        DateTimeFormatter timeFmt = DateTimeFormat.forPattern("HH:mm:ss");
        LocalTime lt = timeFmt.parseLocalTime("10:20:30");
        assertEquals(10, lt.getHourOfDay());
        
        MutableDateTime mdt = fmt.parseMutableDateTime("2007-06-09");
        assertEquals(2007, mdt.getYear());
        
        MutableDateTime targetMdt = new MutableDateTime(0L);
        int pos = fmt.parseInto(targetMdt, "2007-06-09", 0);
        assertEquals(10, pos);
        
        try {
            fmt.parseInto(null, "2007-06-09", 0);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Instant must not be null"));
        }
    }

    public void testParseInvalidFormatThrows() throws Throwable {
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        
        try {
            fmt.parseMillis("invalid");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }

        try {
            fmt.parseDateTime("invalid");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }

        try {
            fmt.parseLocalDateTime("invalid");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }

        try {
            // Partial match failing complete string consumption
            fmt.parseMillis("2007-06-09 extra");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
        
        try {
            fmt.parseDateTime("2007-06-09 extra");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }

        try {
            fmt.parseLocalDateTime("2007-06-09 extra");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testParseWithOptionsAndZones() throws Throwable {
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        
        DateTime dt1 = fmt.withOffsetParsed().parseDateTime("2004-06-09T10:20:30-08:00");
        assertNotNull(dt1);
        
        DateTime dt2 = fmt.withZone(DateTimeZone.UTC).parseDateTime("2004-06-09T10:20:30");
        assertEquals(DateTimeZone.UTC, dt2.getZone());
        
        LocalDateTime ldt = fmt.parseLocalDateTime("2004-06-09T10:20:30-08:00");
        assertNotNull(ldt);

        MutableDateTime mdt = fmt.withZone(DateTimeZone.UTC).parseMutableDateTime("2004-06-09T10:20:30");
        assertEquals(DateTimeZone.UTC, mdt.getZone());
        
        MutableDateTime targetMdt = new MutableDateTime(0L);
        int pos = fmt.withOffsetParsed().parseInto(targetMdt, "2004-06-09T10:20:30-08:00", 0);
        assertTrue(pos > 0);
    }
}