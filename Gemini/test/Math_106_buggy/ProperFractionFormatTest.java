package org.apache.commons.math.fraction;

import junit.framework.TestCase;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class ProperFractionFormatTest extends TestCase {

    public void testDefaultConstructor() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        assertNotNull(format.getWholeFormat());
        assertNotNull(format.getNumeratorFormat());
        assertNotNull(format.getDenominatorFormat());
    }

    public void testSingleNumberFormatConstructor() throws Throwable {
        NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
        ProperFractionFormat format = new ProperFractionFormat(nf);
        assertNotNull(format.getWholeFormat());
        assertNotNull(format.getNumeratorFormat());
        assertNotNull(format.getDenominatorFormat());
    }

    public void testTripleNumberFormatConstructor() throws Throwable {
        NumberFormat nfWhole = NumberFormat.getInstance(Locale.ENGLISH);
        NumberFormat nfNum = NumberFormat.getInstance(Locale.ENGLISH);
        NumberFormat nfDen = NumberFormat.getInstance(Locale.ENGLISH);
        ProperFractionFormat format = new ProperFractionFormat(nfWhole, nfNum, nfDen);
        assertEquals(nfWhole, format.getWholeFormat());
        assertEquals(nfNum, format.getNumeratorFormat());
        assertEquals(nfDen, format.getDenominatorFormat());
    }

    public void testSetWholeFormatNull() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        try {
            format.setWholeFormat(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("whole format can not be null"));
        }
    }

    public void testFormatProperFraction() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        Fraction fraction = new Fraction(7, 3); // 2 1/3
        StringBuffer sb = new StringBuffer();
        FieldPosition pos = new FieldPosition(0);

        StringBuffer result = format.format(fraction, sb, pos);
        assertEquals("2 1 / 3", result.toString());
    }

    public void testFormatImproperFractionNoWhole() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        Fraction fraction = new Fraction(1, 3); // 0 1/3 -> whole is 0, should not print whole
        StringBuffer sb = new StringBuffer();
        FieldPosition pos = new FieldPosition(0);

        StringBuffer result = format.format(fraction, sb, pos);
        assertEquals("1 / 3", result.toString());
    }

    public void testFormatNegativeProperFraction() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        Fraction fraction = new Fraction(-7, 3); // -2 1/3
        StringBuffer sb = new StringBuffer();
        FieldPosition pos = new FieldPosition(0);

        StringBuffer result = format.format(fraction, sb, pos);
        assertEquals("-2 1 / 3", result.toString());
    }

    public void testParseProperFraction() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        ParsePosition pos = new ParsePosition(0);
        Fraction fraction = format.parse("2 1 / 3", pos);

        assertNotNull(fraction);
        assertEquals(7, fraction.getNumerator());
        assertEquals(3, fraction.getDenominator());
        assertEquals(7, pos.getIndex());
    }

    public void testParseNegativeWholeProperFraction() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        ParsePosition pos = new ParsePosition(0);
        Fraction fraction = format.parse("-2 1 / 3", pos);

        assertNotNull(fraction);
        assertEquals(-7, fraction.getNumerator());
        assertEquals(3, fraction.getDenominator());
    }

    public void testParseImproperFallback() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        ParsePosition pos = new ParsePosition(0);
        // "1 / 3" can be parsed by super.parse directly
        Fraction fraction = format.parse("1 / 3", pos);

        assertNotNull(fraction);
        assertEquals(1, fraction.getNumerator());
        assertEquals(3, fraction.getDenominator());
    }

    public void testParseInvalidWhole() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        ParsePosition pos = new ParsePosition(0);
        // Not a valid proper or improper fraction format starting with valid whole
        Fraction fraction = format.parse("abc 1 / 3", pos);
        assertNull(fraction);
        assertEquals(0, pos.getIndex());
    }

    public void testParseInvalidNumerator() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        ParsePosition pos = new ParsePosition(0);
        Fraction fraction = format.parse("2 abc / 3", pos);
        assertNull(fraction);
        assertEquals(0, pos.getIndex());
    }

    public void testParseInvalidDenominator() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        ParsePosition pos = new ParsePosition(0);
        Fraction fraction = format.parse("2 1 / abc", pos);
        assertNull(fraction);
        assertEquals(0, pos.getIndex());
    }

    public void testParseInvalidSlash() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        ParsePosition pos = new ParsePosition(0);
        Fraction fraction = format.parse("2 1 x 3", pos);
        assertNull(fraction);
        assertEquals(0, pos.getIndex());
        assertTrue(pos.getErrorIndex() >= 0);
    }

    public void testParseMissingSlashNoDenominator() throws Throwable {
        ProperFractionFormat format = new ProperFractionFormat();
        ParsePosition pos = new ParsePosition(0);
        // When next char is 0 (end of string after whole and num)
        Fraction fraction = format.parse("2 1", pos);
        assertNotNull(fraction);
        assertEquals(2, fraction.getNumerator());
        assertEquals(1, fraction.getDenominator());
    }
}