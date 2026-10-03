package org.joda.time.format;

import java.util.Locale;

import org.joda.time.DurationFieldType;
import org.joda.time.MutablePeriod;
import org.joda.time.PeriodType;
import org.joda.time.ReadWritablePeriod;

import org.junit.Test;
import static org.junit.Assert.*;

public class PeriodFormatterBuilderClaudeTest {

    // clear() must drop previously appended elements so the builder can be reused
    @Test
    public void testClear_resetsAppendedElements() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears();
        b.clear();
        b.appendLiteral("Q");
        PeriodPrinter p = b.toPrinter();
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, new MutablePeriod(), Locale.US);
        assertEquals("Q", buf.toString());
    }

    // empty builder: createComposite(size 0) -> Literal.EMPTY printer, prints ""
    @Test
    public void testToFormatter_emptyBuilder_printsEmptyString() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        PeriodFormatter f = b.toFormatter();
        assertTrue(f.isPrinter());
        StringBuffer buf = new StringBuffer();
        f.getPrinter().printTo(buf, new MutablePeriod(), Locale.US);
        assertEquals("", buf.toString());
    }

    // appendLiteral(null) must throw IllegalArgumentException
    @Test
    public void testAppendLiteral_nullText_throwsIllegalArgumentException() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        try {
            b.appendLiteral(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // literal text is printed verbatim regardless of period contents
    @Test
    public void testAppendLiteral_printsGivenText() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendLiteral("XYZ");
        PeriodPrinter p = b.toPrinter();
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, new MutablePeriod(), Locale.US);
        assertEquals("XYZ", buf.toString());
    }

    // append(null formatter) must throw IllegalArgumentException
    @Test
    public void testAppend_nullFormatter_throwsIllegalArgumentException() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        try {
            b.append((PeriodFormatter) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // append(formatter) merges another formatter's printer into this one
    @Test
    public void testAppend_formatter_combinesPrinters() throws Throwable {
        PeriodFormatterBuilder b1 = new PeriodFormatterBuilder();
        b1.appendLiteral("A");
        PeriodFormatter fa = b1.toFormatter();
        PeriodFormatterBuilder b2 = new PeriodFormatterBuilder();
        b2.append(fa);
        b2.appendLiteral("B");
        StringBuffer buf = new StringBuffer();
        b2.toFormatter().getPrinter().printTo(buf, new MutablePeriod(), Locale.US);
        assertEquals("AB", buf.toString());
    }

    // append(printer,parser) both null must throw IllegalArgumentException
    @Test
    public void testAppendPrinterParser_bothNull_throwsIllegalArgumentException() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        try {
            b.append((PeriodPrinter) null, (PeriodParser) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // null printer -> toPrinter() null, toParser() non-null, isPrinter false / isParser true
    @Test
    public void testAppendPrinterParser_nullPrinter_toPrinterNullToParserNonNull() throws Throwable {
        PeriodParser customParser = new PeriodParser() {
            public int parseInto(ReadWritablePeriod period, String text, int position, Locale locale) {
                return position;
            }
        };
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.append((PeriodPrinter) null, customParser);
        assertNull(b.toPrinter());
        assertNotNull(b.toParser());
        PeriodFormatter f = b.toFormatter();
        assertFalse(f.isPrinter());
        assertTrue(f.isParser());
    }

    // null parser -> toParser() null, toPrinter() non-null, isPrinter true / isParser false
    @Test
    public void testAppendPrinterParser_nullParser_toParserNullToPrinterNonNull() throws Throwable {
        PeriodFormatterBuilder lit = new PeriodFormatterBuilder();
        lit.appendLiteral("Z");
        PeriodPrinter litPrinter = lit.toPrinter();
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.append(litPrinter, (PeriodParser) null);
        assertNull(b.toParser());
        assertNotNull(b.toPrinter());
        PeriodFormatter f = b.toFormatter();
        assertTrue(f.isPrinter());
        assertFalse(f.isParser());
    }

    // appendSuffix with nothing appended yet must throw IllegalStateException
    @Test
    public void testAppendSuffix_noFieldAppended_throwsIllegalStateException() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        try {
            b.appendSuffix("y");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // appendSuffix after a non-field (literal) element must throw IllegalStateException
    @Test
    public void testAppendSuffix_afterLiteral_throwsIllegalStateException() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendLiteral("X");
        try {
            b.appendSuffix("y");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // prefix PluralAffix: singular text for value 1, plural text otherwise
    @Test
    public void testAppendPrefix_singularPluralForms() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendPrefix("year ", "years ").appendYears();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(1);
        StringBuffer buf1 = new StringBuffer();
        p.printTo(buf1, period, Locale.US);
        assertEquals("year 1", buf1.toString());
        period.setYears(2);
        StringBuffer buf2 = new StringBuffer();
        p.printTo(buf2, period, Locale.US);
        assertEquals("years 2", buf2.toString());
    }

    // suffix PluralAffix: singular text for value 1, plural text otherwise
    @Test
    public void testAppendSuffix_singularPluralForms() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears().appendSuffix(" year", " years");
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(1);
        StringBuffer buf1 = new StringBuffer();
        p.printTo(buf1, period, Locale.US);
        assertEquals("1 year", buf1.toString());
        period.setYears(5);
        StringBuffer buf2 = new StringBuffer();
        p.printTo(buf2, period, Locale.US);
        assertEquals("5 years", buf2.toString());
    }

    // appendPrefix(null) must throw IllegalArgumentException
    @Test
    public void testAppendPrefix_nullText_throwsIllegalArgumentException() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        try {
            b.appendPrefix((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // appendSuffix(null) must throw IllegalArgumentException
    @Test
    public void testAppendSuffix_nullText_throwsIllegalArgumentException() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears();
        try {
            b.appendSuffix((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // minimumPrintedDigits pads the printed field with leading zeros
    @Test
    public void testMinimumPrintedDigits_padsWithLeadingZeros() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.minimumPrintedDigits(3).appendYears();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(5);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("005", buf.toString());
    }

    // maximumParsedDigits limits how many digits are consumed while parsing
    @Test
    public void testMaximumParsedDigits_limitsParsedLength() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.maximumParsedDigits(2).appendYears();
        PeriodParser parser = b.toParser();
        MutablePeriod period = new MutablePeriod();
        int pos = parser.parseInto(period, "12345", 0, Locale.US);
        assertEquals(2, pos);
        assertEquals(12, period.get(DurationFieldType.years()));
    }

    // rejectSignedValues(true) makes a leading '-' invalid, parse fails (negative return)
    @Test
    public void testRejectSignedValues_true_rejectsNegativeSign() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.rejectSignedValues(true).appendYears();
        PeriodParser parser = b.toParser();
        MutablePeriod period = new MutablePeriod();
        int pos = parser.parseInto(period, "-5", 0, Locale.US);
        assertTrue(pos < 0);
    }

    // default rejectSignedValues(false) parses a signed negative value correctly
    @Test
    public void testRejectSignedValues_false_parsesNegativeSign() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears();
        PeriodParser parser = b.toParser();
        MutablePeriod period = new MutablePeriod();
        int pos = parser.parseInto(period, "-5", 0, Locale.US);
        assertEquals(2, pos);
        assertEquals(-5, period.get(DurationFieldType.years()));
    }

    // printZeroAlways forces a zero-valued field to print, unconditionally
    @Test
    public void testPrintZeroAlways_forcesZeroFieldPrinted() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.printZeroAlways().appendYears();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(0);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("0", buf.toString());
    }

    // printZeroNever always suppresses a zero valued field, even with another field printed
    @Test
    public void testPrintZeroNever_suppressesZeroField() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears().appendSeparator(",").printZeroNever().appendMonths();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(5);
        period.setMonths(0);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("5", buf.toString());
    }

    // printZeroRarelyLast (default): if all fields zero, the LAST field prints the zero
    @Test
    public void testPrintZeroRarelyLast_allZero_printsLastFieldOnly() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendPrefix("Y").appendYears().appendSeparator(",").appendPrefix("M").appendMonths();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(0);
        period.setMonths(0);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("M0", buf.toString());
    }

    // printZeroRarelyFirst: if all fields zero, the FIRST field prints the zero
    @Test
    public void testPrintZeroRarelyFirst_allZero_printsFirstFieldOnly() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.printZeroRarelyFirst().appendPrefix("Y").appendYears().appendSeparator(",")
                .appendPrefix("M").appendMonths();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(0);
        period.setMonths(0);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("Y0", buf.toString());
    }

    // printZeroIfSupported + unsupported field: parser exits gracefully, position unchanged
    @Test
    public void testPrintZeroIfSupported_unsupportedField_parseSkipsGracefully() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.printZeroIfSupported().appendWeeks();
        PeriodParser parser = b.toParser();
        MutablePeriod period = new MutablePeriod(PeriodType.months());
        int pos = parser.parseInto(period, "5", 0, Locale.US);
        assertEquals(0, pos);
    }

    // separator printed only when fields exist both before and after it
    @Test
    public void testAppendSeparator_bothFieldsPrinted_insertsSeparator() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears().appendSeparator(",").appendMonths();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(1);
        period.setMonths(2);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("1,2", buf.toString());
    }

    // appendSeparatorIfFieldsAfter: separator prints as soon as the after-field prints
    @Test
    public void testAppendSeparatorIfFieldsAfter_printsWhenAfterFieldPresent() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears().appendSeparatorIfFieldsAfter("-").appendMonths();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(0);
        period.setMonths(5);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("-5", buf.toString());
    }

    // appendSeparatorIfFieldsBefore: separator prints as soon as the before-field prints
    @Test
    public void testAppendSeparatorIfFieldsBefore_printsWhenBeforeFieldPresent() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears().appendSeparatorIfFieldsBefore("-").appendMonths();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(5);
        period.setMonths(0);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("5-", buf.toString());
    }

    // two adjacent separators with no field between them must throw IllegalStateException
    @Test
    public void testAppendSeparator_adjacentSeparators_throwsIllegalStateException() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears().appendSeparator(",");
        try {
            b.appendSeparator(";");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // separator with finalText: three fields printed -> "1,2&3" per javadoc example
    @Test
    public void testAppendSeparatorFinalText_threeFieldsPrinted() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears().appendSeparator(",", "&").appendMonths()
                .appendSeparator(",", "&").appendDays();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(1);
        period.setMonths(2);
        period.setDays(3);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("1,2&3", buf.toString());
    }

    // separator with finalText: two fields printed -> uses finalText "&" ("1&2")
    @Test
    public void testAppendSeparatorFinalText_twoFieldsPrinted() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears().appendSeparator(",", "&").appendMonths();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(1);
        period.setMonths(2);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("1&2", buf.toString());
    }

    // separator with finalText: only one field printed -> no separator at all ("1")
    @Test
    public void testAppendSeparatorFinalText_oneFieldPrinted() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears().appendSeparator(",", "&").appendMonths();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setYears(1);
        period.setMonths(0);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("1", buf.toString());
    }

    // separator variant forms must be accepted while parsing
    @Test
    public void testAppendSeparator_withVariants_parsesVariantText() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendYears().appendSeparator(",", ",", new String[] {";"}).appendMonths();
        PeriodParser parser = b.toParser();
        MutablePeriod period = new MutablePeriod();
        int pos = parser.parseInto(period, "1;2", 0, Locale.US);
        assertEquals(3, pos);
        assertEquals(1, period.get(DurationFieldType.years()));
        assertEquals(2, period.get(DurationFieldType.months()));
    }

    // appendMillis3Digit always pads to 3 digits regardless of global minimumPrintedDigits
    @Test
    public void testAppendMillis3Digit_padsThreeDigits() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendMillis3Digit();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setMillis(7);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("007", buf.toString());
    }

    // appendSecondsWithMillis: decimal part is always printed, even when millis is zero
    @Test
    public void testAppendSecondsWithMillis_zeroMillis_stillPrintsDecimal() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendSecondsWithMillis();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setSeconds(5);
        period.setMillis(0);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("5.000", buf.toString());
    }

    // appendSecondsWithMillis: non-zero millis printed as 3-digit decimal
    @Test
    public void testAppendSecondsWithMillis_nonZeroMillis_printsDecimal() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendSecondsWithMillis();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setSeconds(5);
        period.setMillis(250);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("5.250", buf.toString());
    }

    // appendSecondsWithOptionalMillis: decimal part omitted when millis is zero
    @Test
    public void testAppendSecondsWithOptionalMillis_zeroMillis_omitsDecimal() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendSecondsWithOptionalMillis();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setSeconds(5);
        period.setMillis(0);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("5", buf.toString());
    }

    // appendSecondsWithOptionalMillis: decimal part included when millis is non-zero
    @Test
    public void testAppendSecondsWithOptionalMillis_nonZeroMillis_includesDecimal() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendSecondsWithOptionalMillis();
        PeriodPrinter p = b.toPrinter();
        MutablePeriod period = new MutablePeriod();
        period.setSeconds(5);
        period.setMillis(250);
        StringBuffer buf = new StringBuffer();
        p.printTo(buf, period, Locale.US);
        assertEquals("5.250", buf.toString());
    }

    // parsing seconds+optional millis round trip correctly splits whole and fractional parts
    @Test
    public void testParseInto_secondsWithOptionalMillis_parsesFractional() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendSecondsWithOptionalMillis();
        PeriodParser parser = b.toParser();
        MutablePeriod period = new MutablePeriod();
        int pos = parser.parseInto(period, "5.250", 0, Locale.US);
        assertEquals(5, pos);
        assertEquals(5, period.get(DurationFieldType.seconds()));
        assertEquals(250, period.get(DurationFieldType.millis()));
    }

    // a prefix not followed by a field (another non-field append) must throw IllegalStateException
    @Test
    public void testAppendPrefix_thenLiteral_withoutField_throwsIllegalStateException() throws Throwable {
        PeriodFormatterBuilder b = new PeriodFormatterBuilder();
        b.appendPrefix("x");
        try {
            b.appendLiteral("y");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }
}
