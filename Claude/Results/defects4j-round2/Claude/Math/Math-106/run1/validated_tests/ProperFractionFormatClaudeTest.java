package org.apache.commons.math.fraction;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProperFractionFormatClaudeTest {

    private ProperFractionFormat format;

    @Before
    public void setUp() throws Throwable {
        format = new ProperFractionFormat(NumberFormat.getIntegerInstance(Locale.US));
    }

    // Default constructor must provide a non-null whole number format
    @Test
    public void testDefaultConstructor_wholeFormatIsNotNull() throws Throwable {
        ProperFractionFormat pf = new ProperFractionFormat();
        assertNotNull(pf.getWholeFormat());
    }

    // Single-format constructor: whole format reference kept, numerator/denominator are clones
    @Test
    public void testConstructorWithSingleFormat_cloneBehavior() throws Throwable {
        NumberFormat original = NumberFormat.getIntegerInstance(Locale.US);
        ProperFractionFormat pf = new ProperFractionFormat(original);
        assertSame(original, pf.getWholeFormat());
        assertNotSame(original, pf.getNumeratorFormat());
        assertNotSame(pf.getNumeratorFormat(), pf.getDenominatorFormat());
    }

    // Three-format constructor: each format stored exactly as given
    @Test
    public void testConstructorWithThreeFormats_eachFormatStoredAsGiven() throws Throwable {
        NumberFormat wf = NumberFormat.getIntegerInstance(Locale.US);
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        NumberFormat df = NumberFormat.getIntegerInstance(Locale.US);
        ProperFractionFormat pf = new ProperFractionFormat(wf, nf, df);
        assertSame(wf, pf.getWholeFormat());
        assertSame(nf, pf.getNumeratorFormat());
        assertSame(df, pf.getDenominatorFormat());
    }

    // format(): whole != 0 branch, proper layout "whole num / den"
    @Test
    public void testFormat_wholeNonZeroPositive_properFormatOutput() throws Throwable {
        StringBuffer sb = new StringBuffer();
        format.format(new Fraction(7, 2), sb, new FieldPosition(42));
        assertEquals("3 1 / 2", sb.toString());
    }

    // format(): whole == 0 branch, no whole part printed
    @Test
    public void testFormat_wholeZero_noWholePartInOutput() throws Throwable {
        StringBuffer sb = new StringBuffer();
        format.format(new Fraction(1, 2), sb, new FieldPosition(42));
        assertEquals("1 / 2", sb.toString());
    }

    // format(): negative whole, minus sign appears only on whole part
    @Test
    public void testFormat_negativeWholeNonZero_minusSignOnWholeOnly() throws Throwable {
        StringBuffer sb = new StringBuffer();
        format.format(new Fraction(-7, 2), sb, new FieldPosition(42));
        assertEquals("-3 1 / 2", sb.toString());
    }

    // format(): whole == 0 with negative numerator, sign kept on numerator (num not abs'd)
    @Test
    public void testFormat_negativeNumeratorZeroWhole_numeratorRetainsSign() throws Throwable {
        StringBuffer sb = new StringBuffer();
        format.format(new Fraction(-1, 2), sb, new FieldPosition(42));
        assertEquals("-1 / 2", sb.toString());
    }

    // format(): whole != 0 but remainder is exactly 0, numerator printed as 0
    @Test
    public void testFormat_zeroRemainderWithNonZeroWhole_numeratorZeroOutput() throws Throwable {
        StringBuffer sb = new StringBuffer();
        format.format(new Fraction(3, 1), sb, new FieldPosition(42));
        assertEquals("3 0 / 1", sb.toString());
    }

    // format() must return the same StringBuffer instance passed in
    @Test
    public void testFormat_returnsSameStringBufferInstance() throws Throwable {
        StringBuffer sb = new StringBuffer();
        StringBuffer result = format.format(new Fraction(1, 2), sb, new FieldPosition(42));
        assertSame(sb, result);
    }

    // format() always resets begin/end index of the FieldPosition to 0 at start
    @Test
    public void testFormat_resetsFieldPositionBeginAndEndToZero() throws Throwable {
        FieldPosition pos = new FieldPosition(42);
        pos.setBeginIndex(5);
        pos.setEndIndex(9);
        format.format(new Fraction(1, 2), new StringBuffer(), pos);
        assertEquals(0, pos.getBeginIndex());
        assertEquals(0, pos.getEndIndex());
    }

    // getWholeFormat() returns the format supplied to the constructor
    @Test
    public void testGetWholeFormat_returnsFormatSetInConstructor() throws Throwable {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        ProperFractionFormat pf = new ProperFractionFormat(nf);
        assertSame(nf, pf.getWholeFormat());
    }

    // setWholeFormat(null) must throw IllegalArgumentException per javadoc
    @Test
    public void testSetWholeFormat_null_throwsIllegalArgumentException() throws Throwable {
        try {
            format.setWholeFormat(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // setWholeFormat(valid) is reflected by a subsequent getWholeFormat()
    @Test
    public void testSetWholeFormat_validFormat_reflectedInGetWholeFormat() throws Throwable {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        format.setWholeFormat(nf);
        assertSame(nf, format.getWholeFormat());
    }

    // parse(): simple "num/den" string handled directly by super.parse()
    @Test
    public void testParse_simpleImproperFraction_viaSuperParse() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Fraction f = format.parse("1/2", pos);
        assertNotNull(f);
        assertEquals(1, f.getNumerator());
        assertEquals(2, f.getDenominator());
    }

    // parse(): proper fraction with positive whole number
    @Test
    public void testParse_properFractionPositiveWhole_correctValue() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Fraction f = format.parse("1 1/2", pos);
        assertNotNull(f);
        assertEquals(3, f.getNumerator());
        assertEquals(2, f.getDenominator());
    }

    // parse(): javadoc example "-3 1/2" is legitimate and denotes -7/2
    @Test
    public void testParse_properFractionNegativeWhole_javadocExample() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Fraction f = format.parse("-3 1/2", pos);
        assertNotNull(f);
        assertEquals(-7, f.getNumerator());
        assertEquals(2, f.getDenominator());
    }



    // BUG: javadoc says "-3 -1/2" is invalid (minus only allowed on whole part); parse must fail
    @Test
    public void testParse_negativeNumeratorWithNegativeWhole_invalidReturnsNull() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Fraction f = format.parse("-3 -1/2", pos);
        assertNull(f);
    }





    // parse(): invalid whole number returns null
    @Test
    public void testParse_invalidWholeNumber_returnsNull() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Fraction f = format.parse("abc", pos);
        assertNull(f);
    }



    // parse(): invalid denominator after '/' returns null
    @Test
    public void testParse_invalidDenominator_returnsNull() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Fraction f = format.parse("1 2/x", pos);
        assertNull(f);
    }

    // parse(): larger whole number, correct arithmetic composition
    @Test
    public void testParse_largerWholeNumber_correctComputation() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Fraction f = format.parse("2 3/4", pos);
        assertNotNull(f);
        assertEquals(11, f.getNumerator());
        assertEquals(4, f.getDenominator());
    }

    // parse(): empty source string is invalid, returns null
    @Test
    public void testParse_emptyString_returnsNull() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Fraction f = format.parse("", pos);
        assertNull(f);
    }

    // parse(): extra whitespace between whole and numerator is ignored
    @Test
    public void testParse_wholeNumberWithExtraWhitespace_parsedCorrectly() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Fraction f = format.parse("1   1/2", pos);
        assertNotNull(f);
        assertEquals(3, f.getNumerator());
        assertEquals(2, f.getDenominator());
    }
}
