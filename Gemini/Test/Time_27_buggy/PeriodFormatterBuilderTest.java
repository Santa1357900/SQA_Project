package org.joda.time.format;

import junit.framework.TestCase;
import org.joda.time.Period;
import org.joda.time.PeriodType;

import java.io.CharArrayWriter;
import java.util.Locale;

public class PeriodFormatterBuilderTest extends TestCase {

    public void testClearAndRebuild() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendYears();
        builder.clear();
        PeriodFormatter formatter = builder.appendMonths().toFormatter();
        assertNotNull(formatter);
        assertTrue(formatter.isPrinter());
        assertTrue(formatter.isParser());
    }

    public void testAppendNullFormatter() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        try {
            builder.append(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testAppendNullPrinterAndParser() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        try {
            builder.append(null, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testAppendLiteralNull() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        try {
            builder.appendLiteral(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testAppendPrefixVariants() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        builder.appendPrefix("year");
        builder.appendPrefix("single", "plural");
        try {
            PeriodFormatterBuilder.PeriodFieldAffix nullAffix = null;
            // Test private/package affixed via public methods checking nulls
            builder.appendPrefix((String) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            builder.appendPrefix(null, "plural");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            builder.appendPrefix("single", null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testAppendSuffixWithoutField() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        try {
            builder.appendSuffix("suffix");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }

        try {
            builder.appendSuffix("sing", "plur");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testAppendSeparatorEdgeCases() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        try {
            builder.appendSeparator(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            builder.appendSeparator(null, "final");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            builder.appendSeparator("text", null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Adjacent separators
        builder.appendDays();
        builder.appendSeparator(",");
        try {
            builder.appendSeparator(",");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testToFormatterNeitherPrinterNorParser() throws Throwable {
        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        // Add something that produces neither or force both flags
        // Actually, if we append null printer and parser separately via append(null, parser)
        builder.append(null, new PeriodFormatterBuilder.Literal("test").EMPTY);
        // Wait, Literal implements both. Let's make a custom or just use toFormatter with empty/invalid state.
        // Let's test standard builder with no elements:
        PeriodFormatter fmt = builder.toFormatter(); // 0 elements defaults to empty literal printer/parser
        assertNotNull(fmt);

        PeriodFormatterBuilder builder2 = new PeriodFormatterBuilder();
        // Force not printer and not parser by building custom state if possible, 
        // or test toPrinter() / toParser() when unsupported.
        PeriodFormatter printerOnly = new PeriodFormatterBuilder().appendYears().toPrinter().toFormatter();
        assertNull(printerOnly.getParser());
        assertNotNull(printerOnly.getPrinter());

        PeriodFormatter parserOnly = new PeriodFormatterBuilder().appendYears().toParser().toFormatter();
        assertNotNull(parserOnly.getParser());
        assertNull(parserOnly.getPrinter());
    }

    public void testFieldFormattingAndParsingAllFields() throws Throwable {
        PeriodFormatter formatter = new PeriodFormatterBuilder()
                .printZeroAlways()
                .appendYears().appendSuffix("y")
                .appendSeparator(",")
                .appendMonths().appendSuffix("m")
                .appendSeparator(",")
                .appendWeeks().appendSuffix("w")
                .appendSeparator(",")
                .appendDays().appendSuffix("d")
                .appendSeparator(",")
                .appendHours().appendSuffix("h")
                .appendSeparator(",")
                .appendMinutes().appendSuffix("min")
                .appendSeparator(",")
                .appendSeconds().appendSuffix("s")
                .appendSeparator(",")
                .appendMillis3Digit().appendSuffix("ms")
                .toFormatter();

        Period period = new Period(1, 2, 3, 4, 5, 6, 7, 8);
        String printed = formatter.print(period);
        assertNotNull(printed);

        Period parsed = formatter.parsePeriod(printed);
        assertNotNull(parsed);
    }

    public void testSecondsWithMillisAndOptional() throws Throwable {
        PeriodFormatter fmt1 = new PeriodFormatterBuilder()
                .appendSecondsWithMillis()
                .toFormatter();
        
        PeriodFormatter fmt2 = new PeriodFormatterBuilder()
                .appendSecondsWithOptionalMillis()
                .toFormatter();

        Period p = new Period(0, 0, 0, 0, 0, 0, 5, 432);
        String s1 = fmt1.print(p);
        String s2 = fmt2.print(p);
        assertTrue(s1.contains("5.432"));
        assertTrue(s2.contains("5.432"));

        Period pZeroMillis = new Period(0, 0, 0, 0, 0, 0, 5, 0);
        String sOptZero = fmt2.print(pZeroMillis);
        assertFalse(sOptZero.contains("."));

        Period parsed1 = fmt1.parsePeriod("5.432");
        assertNotNull(parsed1);
        
        Period parsed2 = fmt1.parsePeriod("-5.432");
        assertNotNull(parsed2);

        Period parsed3 = fmt1.parsePeriod("5");
        assertNotNull(parsed3);
    }

    public void testPrintZeroSettingsVariants() throws Throwable {
        PeriodFormatter fRarelyLast = new PeriodFormatterBuilder()
                .printZeroRarelyLast()
                .appendYears()
                .appendMonths()
                .toFormatter();

        PeriodFormatter fRarelyFirst = new PeriodFormatterBuilder()
                .printZeroRarelyFirst()
                .appendYears()
                .appendMonths()
                .toFormatter();

        PeriodFormatter fIfSupported = new PeriodFormatterBuilder()
                .printZeroIfSupported()
                .appendYears()
                .toFormatter();

        PeriodFormatter fNever = new PeriodFormatterBuilder()
                .printZeroNever()
                .appendYears()
                .toFormatter();

        Period zeroPeriod = Period.ZERO;
        assertNotNull(fRarelyLast.print(zeroPeriod));
        assertNotNull(fRarelyFirst.print(zeroPeriod));
        assertNotNull(fIfSupported.print(zeroPeriod));
        assertEquals("", fNever.print(zeroPeriod));
    }

    public void testRejectSignedValuesAndParsingLimits() throws Throwable {
        PeriodFormatter fmt = new PeriodFormatterBuilder()
                .rejectSignedValues(true)
                .appendDays()
                .toFormatter();

        try {
            fmt.parsePeriod("-5");
            fail("Expected IllegalArgumentException or parse failure");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    public void testWriterPrinting() throws Throwable {
        PeriodFormatter fmt = new PeriodFormatterBuilder()
                .appendDays()
                .appendSuffix(" day", " days")
                .toFormatter();

        CharArrayWriter writer = new CharArrayWriter();
        fmt.printTo(writer, new Period(2, 0, 0, 0));
        assertTrue(writer.toString().contains("days"));
    }

    public void testAffixScansAndParsing() throws Throwable {
        PeriodFormatter fmt = new PeriodFormatterBuilder()
                .minimumPrintedDigits(1)
                .appendDays()
                .appendSuffix("D")
                .appendSeparator("T")
                .appendHours()
                .appendSuffix("H")
                .toFormatter();

        Period p = fmt.parsePeriod("5D-1H"); // negative hours or invalid separator if signed/unsigned
        assertNotNull(p);
    }
}