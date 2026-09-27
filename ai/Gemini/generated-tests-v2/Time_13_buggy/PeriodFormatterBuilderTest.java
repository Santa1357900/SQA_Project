package org.joda.time.format;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

import org.joda.time.Period;
import org.joda.time.PeriodType;

public class PeriodFormatterBuilderTest {

    @Test
    public void testClearAndToFormatter() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendYears();
        builder.clear();
        
        try {
            builder.toFormatter();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAppendNullFormatter() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.append((PeriodFormatter) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAppendNullPrinterParser() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.append((PeriodPrinter) null, (PeriodParser) null);
    }

    @Test
    public void testAppendValidPrinterParser() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        PeriodFormatter fmt = new PeriodFormatterBuilder().appendYears().toFormatter();
        builder.append(fmt.getPrinter(), fmt.getParser());
        assertNotNull(builder.toFormatter());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAppendLiteralNull() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendLiteral(null);
    }

    @Test
    public void testAppendLiteralValid() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendLiteral("Test");
        PeriodFormatter formatter = builder.toFormatter();
        assertNotNull(formatter);
        
        String printed = formatter.print(new Period(0));
        assertEquals("Test", printed);
    }

    @Test
    public void testZeroPrintingSettings() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.printZeroAlways();
        builder.printZeroNever();
        builder.printZeroRarelyFirst();
        builder.printZeroRarelyLast();
        builder.printZeroIfSupported();
        builder.minimumPrintedDigits(2);
        builder.maximumParsedDigits(5);
        builder.rejectSignedValues(true);
        builder.appendYears();
        
        PeriodFormatter fmt = builder.toFormatter();
        assertNotNull(fmt);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAppendPrefixNullString() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendPrefix((String) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAppendPrefixNullPlural() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendPrefix(null, "years");
    }

    @Test(expected = IllegalStateException.class)
    public void testAppendSuffixNoField() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendSuffix("suffix");
    }

    @Test
    public void testAppendFieldVariants() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendYears()
               .appendSuffix(" year", " years")
               .appendMonths()
               .appendWeeks()
               .appendDays()
               .appendHours()
               .appendMinutes()
               .appendSeconds()
               .appendSecondsWithMillis()
               .appendSecondsWithOptionalMillis()
               .appendMillis()
               .appendMillis3Digit();
        
        PeriodFormatter fmt = builder.toFormatter();
        assertNotNull(fmt);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAppendSeparatorNull() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendSeparator(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testAppendAdjacentSeparators() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendDays()
               .appendSeparator(",")
               .appendSeparator(",");
    }

    @Test
    public void testSeparatorsVarious() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendDays()
               .appendSeparatorIfFieldsAfter(",")
               .appendHours()
               .appendSeparatorIfFieldsBefore(",")
               .appendMinutes()
               .appendSeparator(",", "&", new String[] {"and"})
               .appendSeconds();
        
        PeriodFormatter fmt = builder.toFormatter();
        assertNotNull(fmt);
    }

    @Test
    public void testToPrinterAndToParser() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendYears();
        assertNotNull(builder.toPrinter());
        assertNotNull(builder.toParser());
    }

    @Test
    public void testToNotPrinterOrParser() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.append((PeriodPrinter) null, new PeriodFormatterBuilder().appendYears().toParser());
        assertNull(builder.toPrinter());
        assertNotNull(builder.toParser());
    }

    @Test
    public void testSimpleAffixWriterAndScan() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        PeriodFormatter formatter = builder.appendYears().appendPrefix("P").appendSuffix("Y").toFormatter();
        
        Period period = new Period(5, 0, 0, 0, 0, 0, 0, 0);
        StringWriter writer = new StringWriter();
        formatter.printTo(writer, period);
        assertEquals("P5Y", writer.toString());
        
        Period parsed = formatter.parsePeriod("P5Y");
        assertEquals(5, parsed.getYears());
    }

    @Test
    public void testPluralAffixParsingAndScanning() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        PeriodFormatter formatter = builder.appendYears().appendSuffix(" year", " years").toFormatter();
        
        Period parsedSingular = formatter.parsePeriod("1 year");
        assertEquals(1, parsedSingular.getYears());
        
        Period parsedPlural = formatter.parsePeriod("2 years");
        assertEquals(2, parsedPlural.getYears());
    }

    @Test
    public void testCompositeAffixOperations() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendYears()
               .appendPrefix("prefix1")
               .appendPrefix("prefix2")
               .appendSuffix("suffix1")
               .appendSuffix("suffix2");
        
        PeriodFormatter fmt = builder.toFormatter();
        assertNotNull(fmt);
        
        String result = fmt.print(new Period(1, 0, 0, 0, 0, 0, 0, 0));
        assertTrue(result.contains("prefix1prefix2"));
        assertTrue(result.contains("suffix1suffix2"));
    }

    @Test
    public void testFieldFormatterEdgeCases() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        PeriodFormatter fmt = builder.printZeroAlways()
                                     .appendSecondsWithMillis()
                                     .toFormatter();
        
        String printed = fmt.print(new Period(0, 0, 0, 0, 0, 0, 5, 123));
        assertEquals("5.123", printed);
        
        Period parsed = fmt.parsePeriod("5.123");
        assertEquals(5, parsed.getSeconds());
        assertEquals(123, parsed.getMillis());
    }

    @Test
    public void testFieldFormatterOptionalMillis() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        PeriodFormatter fmt = builder.appendSecondsWithOptionalMillis().toFormatter();
        
        String printedWithoutMillis = fmt.print(new Period(0, 0, 0, 0, 0, 0, 5, 0));
        assertEquals("5", printedWithoutMillis);
        
        String printedWithMillis = fmt.print(new Period(0, 0, 0, 0, 0, 0, 5, 456));
        assertEquals("5.456", printedWithMillis);
        
        Period parsed = fmt.parsePeriod("5.456");
        assertEquals(5, parsed.getSeconds());
        assertEquals(456, parsed.getMillis());
    }

    @Test
    public void testNegativeValuesAndSignedParsing() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        PeriodFormatter fmt = builder.appendYears().toFormatter();
        
        Period period = new Period(-3, 0, 0, 0, 0, 0, 0, 0);
        String printed = fmt.print(period);
        assertEquals("-3", printed);
        
        Period parsed = fmt.parsePeriod("-3");
        assertEquals(-3, parsed.getYears());
    }

    @Test
    public void testSeparatorVariantsAndParse() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        PeriodFormatter fmt = builder.appendDays()
                                     .appendSeparator(",", ",", new String[] {"and"})
                                     .appendHours()
                                     .toFormatter();
        
        Period parsed = fmt.parsePeriod("2and3");
        assertEquals(2, parsed.getDays());
        assertEquals(3, parsed.getHours());
    }

    @Test
    public void testCompositeDecompose() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        PeriodFormatter fmt = builder.appendYears()
                                     .appendMonths()
                                     .appendWeeks()
                                     .appendDays()
                                     .appendHours()
                                     .appendMinutes()
                                     .appendSeconds()
                                     .appendMillis()
                                     .toFormatter();
        
        assertNotNull(fmt);
        Period period = new Period(1, 2, 3, 4, 5, 6, 7, 8);
        String printed = fmt.print(period);
        assertNotNull(printed);
    }
}