package org.joda.time.format;

import java.util.Locale;

import org.joda.time.MutablePeriod;
import org.joda.time.Period;
import org.joda.time.PeriodType;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PeriodFormatterBuilderClaudeTest {

    private PeriodFormatterBuilder builder;

    @Before
    public void setUp() throws Throwable {
        builder = new PeriodFormatterBuilder();
    }

    // covers clear() resetting iElementPairs/iNotPrinter/iNotParser
    @Test
    public void testClear_removesPreviouslyAppendedElements() throws Throwable {
        builder.appendLiteral("X");
        builder.clear();
        builder.appendLiteral("Y");
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("Y", buf.toString());
    }

    // covers toFormatter() with zero elements -> Literal.EMPTY composite
    @Test
    public void testToFormatter_emptyBuilder_printsEmptyString() throws Throwable {
        PeriodFormatter f = builder.toFormatter();
        assertTrue(f.isPrinter());
        assertTrue(f.isParser());
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(1, 2, 3, 4, 5, 6, 7, 8), Locale.ENGLISH);
        assertEquals("", buf.toString());
    }

    // covers toFormatter() throwing when both iNotPrinter and iNotParser are true
    @Test
    public void testToFormatter_neitherPrinterNorParser_throwsIllegalStateException() throws Throwable {
        PeriodFormatter lit = new PeriodFormatterBuilder().appendLiteral("A").toFormatter();
        builder.append(null, lit.getParser());
        builder.append(lit.getPrinter(), null);
        try {
            builder.toFormatter();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // covers toPrinter() returning null when iNotPrinter is true
    @Test
    public void testToPrinter_afterAppendingOnlyParser_returnsNull() throws Throwable {
        PeriodFormatter lit = new PeriodFormatterBuilder().appendLiteral("A").toFormatter();
        builder.append(null, lit.getParser());
        assertNull(builder.toPrinter());
    }

    // covers toParser() returning null when iNotParser is true
    @Test
    public void testToParser_afterAppendingOnlyPrinter_returnsNull() throws Throwable {
        PeriodFormatter lit = new PeriodFormatterBuilder().appendLiteral("A").toFormatter();
        builder.append(lit.getPrinter(), null);
        assertNull(builder.toParser());
    }

    // covers append(PeriodFormatter) null check
    @Test
    public void testAppend_nullFormatter_throwsIllegalArgumentException() throws Throwable {
        try {
            builder.append((PeriodFormatter) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers append(printer,parser) both-null check
    @Test
    public void testAppend_printerAndParserBothNull_throwsIllegalArgumentException() throws Throwable {
        try {
            builder.append((PeriodPrinter) null, (PeriodParser) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers append(PeriodFormatter) success path combining an inner formatter
    @Test
    public void testAppend_formatter_combinesIntoNewBuilder() throws Throwable {
        PeriodFormatter inner = new PeriodFormatterBuilder().appendLiteral("ABC").toFormatter();
        builder.append(inner);
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("ABC", buf.toString());
    }

    // covers appendLiteral(String) null check
    @Test
    public void testAppendLiteral_null_throwsIllegalArgumentException() throws Throwable {
        try {
            builder.appendLiteral(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers appendLiteral(String) printing the exact given text
    @Test
    public void testAppendLiteral_printsGivenText() throws Throwable {
        builder.appendLiteral("hello");
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("hello", buf.toString());
    }

    // covers minimumPrintedDigits(int) padding a field value with leading zeros
    @Test
    public void testMinimumPrintedDigits_padsWithZeros() throws Throwable {
        builder.minimumPrintedDigits(3).appendYears();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(5, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("005", buf.toString());
    }

    // covers maximumParsedDigits(int) limiting the digit scan window when parsing
    @Test
    public void testMaximumParsedDigits_limitsParsedLength() throws Throwable {
        builder.maximumParsedDigits(2).appendYears();
        PeriodFormatter f = builder.toFormatter();
        MutablePeriod mp = new MutablePeriod();
        int pos = f.getParser().parseInto(mp, "12345", 0, Locale.ENGLISH);
        assertEquals(2, pos);
        assertEquals(12, mp.getYears());
    }

    // covers rejectSignedValues(true) disabling the leading sign parse branch
    @Test
    public void testRejectSignedValues_true_rejectsNegativeSign() throws Throwable {
        builder.rejectSignedValues(true).appendYears();
        PeriodFormatter f = builder.toFormatter();
        MutablePeriod mp = new MutablePeriod();
        int pos = f.getParser().parseInto(mp, "-5", 0, Locale.ENGLISH);
        assertTrue(pos < 0);
    }

    // covers default rejectSignedValues(false) allowing a negative value to parse
    @Test
    public void testRejectSignedValues_false_allowsNegativeSign() throws Throwable {
        builder.rejectSignedValues(false).appendYears();
        PeriodFormatter f = builder.toFormatter();
        MutablePeriod mp = new MutablePeriod();
        int pos = f.getParser().parseInto(mp, "-5", 0, Locale.ENGLISH);
        assertEquals(2, pos);
        assertEquals(-5, mp.getYears());
    }

    // covers PRINT_ZERO_RARELY_LAST forcing the sole field to print zero
    @Test
    public void testPrintZeroRarelyLast_singleField_zeroPeriod_printsZero() throws Throwable {
        builder.printZeroRarelyLast().appendYears();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("0", buf.toString());
    }

    // covers PRINT_ZERO_RARELY_LAST suppressing all but the last field for a zero period
    @Test
    public void testPrintZeroRarelyLast_multipleFields_zeroPeriod_onlyLastPrinted() throws Throwable {
        builder.printZeroRarelyLast().appendYears().appendLiteral(" ").appendMonths();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals(" 0", buf.toString());
    }

    // covers PRINT_ZERO_RARELY_FIRST suppressing all but the first field for a zero period
    @Test
    public void testPrintZeroRarelyFirst_multipleFields_zeroPeriod_onlyFirstPrinted() throws Throwable {
        builder.printZeroRarelyFirst().appendYears().appendLiteral(" ").appendMonths();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("0 ", buf.toString());
    }

    // covers PRINT_ZERO_IF_SUPPORTED skipping a field unsupported by the period's type
    @Test
    public void testPrintZeroIfSupported_unsupportedField_notPrinted() throws Throwable {
        builder.printZeroIfSupported().appendWeeks();
        PeriodFormatter f = builder.toFormatter();
        Period p = new Period(0, 0, 0, 0, 0, 0, 0, 0, PeriodType.yearMonthDayTime());
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, p, Locale.ENGLISH);
        assertEquals("", buf.toString());
    }

    // covers PRINT_ZERO_IF_SUPPORTED printing zero when the field is supported
    @Test
    public void testPrintZeroIfSupported_supportedZero_printed() throws Throwable {
        builder.printZeroIfSupported().appendYears();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("0", buf.toString());
    }

    // covers PRINT_ZERO_ALWAYS bypassing the isSupported() check entirely
    @Test
    public void testPrintZeroAlways_unsupportedField_stillPrinted() throws Throwable {
        builder.printZeroAlways().appendWeeks();
        PeriodFormatter f = builder.toFormatter();
        Period p = new Period(0, 0, 0, 0, 0, 0, 0, 0, PeriodType.yearMonthDayTime());
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, p, Locale.ENGLISH);
        assertEquals("0", buf.toString());
    }

    // covers parseInto requiring a value because PRINT_ZERO_ALWAYS makes mustParse true
    @Test
    public void testPrintZeroAlways_emptyInput_parserRequiresValue() throws Throwable {
        builder.printZeroAlways().appendYears();
        PeriodFormatter f = builder.toFormatter();
        MutablePeriod mp = new MutablePeriod();
        int pos = f.getParser().parseInto(mp, "", 0, Locale.ENGLISH);
        assertTrue(pos < 0);
    }

    // covers PRINT_ZERO_NEVER always skipping a zero value
    @Test
    public void testPrintZeroNever_zeroValue_neverPrinted() throws Throwable {
        builder.printZeroNever().appendYears();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("", buf.toString());
    }

    // covers appendPrefix(String) null check
    @Test
    public void testAppendPrefix_null_throwsIllegalArgumentException() throws Throwable {
        try {
            builder.appendPrefix((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers appendPrefix(singular,plural) null check
    @Test
    public void testAppendPrefix_pluralNull_throwsIllegalArgumentException() throws Throwable {
        try {
            builder.appendPrefix(null, "s");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers clearPrefix() throwing when a prefix is not followed by a field
    @Test
    public void testAppendPrefix_withoutFollowingField_throwsIllegalStateException() throws Throwable {
        builder.appendPrefix("P");
        try {
            builder.appendLiteral("X");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // covers CompositeAffix combining two prefixes applied before one field
    @Test
    public void testAppendPrefix_composite_printsBothPrefixesInOrder() throws Throwable {
        builder.appendPrefix("A").appendPrefix("B").appendYears();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(5, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("AB5", buf.toString());
    }

    // covers appendYears/Months/Weeks/Days/Hours/Minutes/Seconds/Millis each printing their value
    @Test
    public void testAppendAllFields_printsFormattedPeriod() throws Throwable {
        builder.appendYears().appendLiteral("-").appendMonths().appendLiteral("-")
               .appendWeeks().appendLiteral("-").appendDays().appendLiteral(" ")
               .appendHours().appendLiteral(":").appendMinutes().appendLiteral(":")
               .appendSeconds().appendLiteral(".").appendMillis();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(1, 2, 3, 4, 5, 6, 7, 8), Locale.ENGLISH);
        assertEquals("1-2-3-4 5:6:7.8", buf.toString());
    }

    // covers appendMillis3Digit() always using a fixed 3-digit minimum regardless of builder setting
    @Test
    public void testAppendMillis3Digit_padsToThreeDigits() throws Throwable {
        builder.appendMillis3Digit();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 0, 5), Locale.ENGLISH);
        assertEquals("005", buf.toString());
    }

    // covers appendSecondsWithMillis() always printing the decimal millis part
    @Test
    public void testAppendSecondsWithMillis_alwaysShowsMillis() throws Throwable {
        builder.appendSecondsWithMillis();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 5, 0), Locale.ENGLISH);
        assertEquals("5.000", buf.toString());
    }

    // covers appendSecondsWithOptionalMillis() omitting the decimal part when millis is zero
    @Test
    public void testAppendSecondsWithOptionalMillis_zeroMillis_omitted() throws Throwable {
        builder.appendSecondsWithOptionalMillis();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 5, 0), Locale.ENGLISH);
        assertEquals("5", buf.toString());
    }

    // covers appendSecondsWithOptionalMillis() showing the decimal part when millis is non-zero
    @Test
    public void testAppendSecondsWithOptionalMillis_nonZeroMillis_shown() throws Throwable {
        builder.appendSecondsWithOptionalMillis();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 5, 250), Locale.ENGLISH);
        assertEquals("5.250", buf.toString());
    }

    // covers millis overflowing into seconds as documented by appendSecondsWithMillis()
    @Test
    public void testAppendSecondsWithMillis_overflowIntoSeconds() throws Throwable {
        builder.appendSecondsWithMillis();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 0, 0, 0, 5, 1500), Locale.ENGLISH);
        assertEquals("6.500", buf.toString());
    }

    // covers appendSuffix requiring a preceding field to attach to
    @Test
    public void testAppendSuffix_withoutField_throwsIllegalStateException() throws Throwable {
        try {
            builder.appendSuffix("y");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // covers appendSuffix(String) null check
    @Test
    public void testAppendSuffix_null_throwsIllegalArgumentException() throws Throwable {
        try {
            builder.appendSuffix((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers PluralAffix choosing singular vs plural suffix text based on value
    @Test
    public void testAppendSuffix_singularPlural_printsCorrectForm() throws Throwable {
        builder.appendYears().appendSuffix(" year", " years");
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf1 = new StringBuffer();
        f.getPrinter().printTo(buf1, new Period(1, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("1 year", buf1.toString());
        StringBuffer buf5 = new StringBuffer();
        f.getPrinter().printTo(buf5, new Period(5, 0, 0, 0, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("5 years", buf5.toString());
    }

    // covers appendSeparator(String) null check
    @Test
    public void testAppendSeparator_null_throwsIllegalArgumentException() throws Throwable {
        try {
            builder.appendSeparator((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers Separator printing text only when fields exist on both sides
    @Test
    public void testAppendSeparator_printedOnlyWhenBothSidesPrinted() throws Throwable {
        builder.printZeroNever().appendDays().appendSeparator(",").appendHours();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer bufBoth = new StringBuffer();
        f.getPrinter().printTo(bufBoth, new Period(0, 0, 0, 5, 3, 0, 0, 0), Locale.ENGLISH);
        assertEquals("5,3", bufBoth.toString());
        StringBuffer bufOne = new StringBuffer();
        f.getPrinter().printTo(bufOne, new Period(0, 0, 0, 5, 0, 0, 0, 0), Locale.ENGLISH);
        assertEquals("5", bufOne.toString());
    }

    // covers appendSeparator detecting two separators added back to back
    @Test
    public void testAppendSeparator_adjacentSeparators_throwsIllegalStateException() throws Throwable {
        builder.appendDays().appendSeparator(",");
        try {
            builder.appendSeparator(";");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // covers appendSeparatorIfFieldsAfter needing only the after side to print
    @Test
    public void testAppendSeparatorIfFieldsAfter_behavesPerContract() throws Throwable {
        builder.printZeroNever().appendHours().appendSeparatorIfFieldsAfter(",").appendMinutes();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer bufAfterOnly = new StringBuffer();
        f.getPrinter().printTo(bufAfterOnly, new Period(0, 0, 0, 0, 0, 5, 0, 0), Locale.ENGLISH);
        assertEquals(",5", bufAfterOnly.toString());
        StringBuffer bufBeforeOnly = new StringBuffer();
        f.getPrinter().printTo(bufBeforeOnly, new Period(0, 0, 0, 0, 3, 0, 0, 0), Locale.ENGLISH);
        assertEquals("3", bufBeforeOnly.toString());
    }

    // covers appendSeparatorIfFieldsBefore needing only the before side to print
    @Test
    public void testAppendSeparatorIfFieldsBefore_behavesPerContract() throws Throwable {
        builder.printZeroNever().appendHours().appendSeparatorIfFieldsBefore(",").appendMinutes();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer bufBeforeOnly = new StringBuffer();
        f.getPrinter().printTo(bufBeforeOnly, new Period(0, 0, 0, 0, 3, 0, 0, 0), Locale.ENGLISH);
        assertEquals("3,", bufBeforeOnly.toString());
        StringBuffer bufAfterOnly = new StringBuffer();
        f.getPrinter().printTo(bufAfterOnly, new Period(0, 0, 0, 0, 0, 5, 0, 0), Locale.ENGLISH);
        assertEquals("5", bufAfterOnly.toString());
    }

    // covers Separator choosing finalText only for the last printed separator (javadoc example)
    @Test
    public void testAppendSeparator_finalTextForLastSeparator() throws Throwable {
        builder.appendDays().appendSeparator(",", "&").appendHours().appendSeparator(",", "&").appendMinutes();
        PeriodFormatter f = builder.toFormatter();
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new Period(0, 0, 0, 1, 2, 3, 0, 0), Locale.ENGLISH);
        assertEquals("1,2&3", buf.toString());
    }

    // covers parseInto returning the original position when field is unsupported and not required
    @Test
    public void testFieldFormatter_parseInto_unsupportedField_skipsGracefully() throws Throwable {
        builder.appendWeeks();
        PeriodFormatter f = builder.toFormatter();
        MutablePeriod mp = new MutablePeriod(0, 0, 0, 5, 0, 0, 0, 0, PeriodType.dayTime());
        int pos = f.getParser().parseInto(mp, "9", 0, Locale.ENGLISH);
        assertEquals(0, pos);
    }

    // covers parseInt() delegating to Integer.parseInt for a 10+ digit run
    @Test
    public void testParseInto_longDigitString_usesIntegerParse() throws Throwable {
        builder.maximumParsedDigits(10).appendYears();
        PeriodFormatter f = builder.toFormatter();
        MutablePeriod mp = new MutablePeriod();
        int pos = f.getParser().parseInto(mp, "1234567890", 0, Locale.ENGLISH);
        assertEquals(10, pos);
        assertEquals(1234567890, mp.getYears());
    }
}
