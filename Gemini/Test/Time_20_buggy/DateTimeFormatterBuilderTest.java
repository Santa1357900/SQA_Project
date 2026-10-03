package org.joda.time.format;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.joda.time.Chronology;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeZone;
import org.joda.time.chrono.ISOChronology;

public class DateTimeFormatterBuilderTest {

    @Test
    public void testBuilderCreationAndEmpty() throws Throwable {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        assertFalse(builder.canBuildFormatter());
        assertFalse(builder.canBuildPrinter());
        assertFalse(builder.canBuildParser());

        try {
            builder.toFormatter();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Both printing and parsing not supported"));
        }

        try {
            builder.toPrinter();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Printing is not supported"));
        }

        try {
            builder.toParser();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Parsing is not supported"));
        }
    }

    @Test
    public void testAppendLiterals() throws Throwable {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.appendLiteral('A');
        builder.appendLiteral("");
        builder.appendLiteral("BC");
        
        assertTrue(builder.canBuildFormatter());
        DateTimeFormatter formatter = builder.toFormatter();
        assertNotNull(formatter);
        
        String printed = formatter.print(0L);
        assertEquals("ABC", printed);

        builder.clear();
        assertFalse(builder.canBuildFormatter());
    }

    @Test
    public void testAppendLiteralNull() throws Throwable {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        try {
            builder.appendLiteral(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Literal must not be null"));
        }
    }

    @Test
    public void testAppendDecimalAndSignedDecimal() throws Throwable {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.appendDecimal(DateTimeFieldType.year(), 4, 4);
        builder.appendSignedDecimal(DateTimeFieldType.monthOfYear(), 2, 2);
        builder.appendFixedDecimal(DateTimeFieldType.dayOfMonth(), 2);
        builder.appendFixedSignedDecimal(DateTimeFieldType.hourOfDay(), 2);

        DateTimeFormatter formatter = builder.toFormatter();
        assertNotNull(formatter);
    }

    @Test
    public void testAppendDecimalInvalidArguments() throws Throwable {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        try {
            builder.appendDecimal(null, 2, 4);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Field type must not be null"));
        }

        try {
            builder.appendDecimal(DateTimeFieldType.year(), -1, 4);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.appendDecimal(DateTimeFieldType.year(), 4, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.appendFixedDecimal(null, 2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.appendFixedDecimal(DateTimeFieldType.year(), 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.appendSignedDecimal(null, 2, 4);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.appendFixedSignedDecimal(null, 2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.appendFixedSignedDecimal(DateTimeFieldType.year(), -5);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testAppendTextAndShortText() throws Throwable {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.appendText(DateTimeFieldType.monthOfYear());
        builder.appendShortText(DateTimeFieldType.dayOfWeek());
        builder.appendHalfdayOfDayText();
        builder.appendDayOfWeekText();
        builder.appendDayOfWeekShortText();
        builder.appendMonthOfYearText();
        builder.appendMonthOfYearShortText();
        builder.appendEraText();

        assertTrue(builder.canBuildFormatter());

        try {
            builder.appendText(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.appendShortText(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testAppendFractions() throws Throwable {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.appendFraction(DateTimeFieldType.millisOfSecond(), 1, 3);
        builder.appendFractionOfSecond(1, 3);
        builder.appendFractionOfMinute(1, 3);
        builder.appendFractionOfHour(1, 3);
        builder.appendFractionOfDay(1, 3);

        assertTrue(builder.canBuildFormatter());

        try {
            builder.appendFraction(null, 1, 3);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.appendFraction(DateTimeFieldType.millisOfSecond(), -1, 3);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.appendFraction(DateTimeFieldType.millisOfSecond(), 1, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testAppendStandardFields() throws Throwable {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.appendMillisOfSecond(3);
        builder.appendMillisOfDay(5);
        builder.appendSecondOfMinute(2);
        builder.appendSecondOfDay(4);
        builder.appendMinuteOfHour(2);
        builder.appendMinuteOfDay(3);
        builder.appendHourOfDay(2);
        builder.appendClockhourOfDay(2);
        builder.appendHourOfHalfday(2);
        builder.appendClockhourOfHalfday(2);
        builder.appendDayOfWeek(1);
        builder.appendDayOfMonth(2);
        builder.appendDayOfYear(3);
        builder.appendWeekOfWeekyear(2);
        builder.appendWeekyear(4, 4);
        builder.appendMonthOfYear(2);
        builder.appendYear(4, 4);
        builder.appendTwoDigitYear(2000);
        builder.appendTwoDigitYear(2000, true);
        builder.appendTwoDigitWeekyear(2000);
        builder.appendTwoDigitWeekyear(2000, true);
        builder.appendYearOfEra(4, 4);
        builder.appendYearOfCentury(2, 2);
        builder.appendCenturyOfEra(2, 2);

        assertTrue(builder.canBuildFormatter());
    }

    @Test
    public void testAppendTimeZone() throws Throwable {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.appendTimeZoneName();
        Map<String, DateTimeZone> lookup = new HashMap<String, DateTimeZone>();
        lookup.put("UTC", DateTimeZone.UTC);
        builder.appendTimeZoneName(lookup);
        builder.appendTimeZoneShortName();
        builder.appendTimeZoneShortName(lookup);
        builder.appendTimeZoneId();
        builder.appendTimeZoneOffset("Z", true, 2, 4);
        builder.appendTimeZoneOffset("+00:00", "+00:00", false, 1, 2);

        assertTrue(builder.canBuildFormatter());
    }

    @Test
    public void testAppendPattern() throws Throwable {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.appendPattern("yyyy-MM-dd");
        assertTrue(builder.canBuildFormatter());
    }

    @Test
    public void testAppendFormattersAndParsers() throws Throwable {
        DateTimeFormatter formatter = ISODateTimeFormat.date();
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        
        try {
            builder.append((DateTimeFormatter) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        
        builder.append(formatter);

        try {
            builder.append((DateTimePrinter) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.append((DateTimeParser) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.append(null, (DateTimeParser) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        builder.append(formatter.getPrinter(), formatter.getParser());

        try {
            builder.append(formatter.getPrinter(), (DateTimeParser[]) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.append(formatter.getPrinter(), new DateTimeParser[] { null });
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            DateTimeParser[] parsers = new DateTimeParser[] { null, formatter.getParser() };
            builder.append(formatter.getPrinter(), parsers);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        builder.append(formatter.getPrinter(), new DateTimeParser[] { formatter.getParser() });
        builder.appendOptional(formatter.getParser());
    }

    @Test
    public void testStaticUnknownStringMethods() throws Throwable {
        StringBuffer buf = new StringBuffer();
        DateTimeFormatterBuilder.appendUnknownString(buf, 3);
        assertEquals("\ufffd\ufffd\ufffd", buf.toString());

        CharArrayWriter writer = new CharArrayWriter();
        DateTimeFormatterBuilder.printUnknownString(writer, 2);
        assertEquals("\ufffd\ufffd", writer.toString());
    }

    @Test
    public void testTimeZoneOffsetValidation() throws Throwable {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        try {
            builder.appendTimeZoneOffset("Z", true, 0, 2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            builder.appendTimeZoneOffset("Z", true, 3, 2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
}