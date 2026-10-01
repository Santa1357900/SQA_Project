package org.apache.commons.math.complex;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ComplexFormatClaudeTest {

    private NumberFormat nf;
    private ComplexFormat complexFormat;

    @Before
    public void setUp() throws Throwable {
        nf = NumberFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(2);
        complexFormat = new ComplexFormat(nf);
    }

    // default constructor: imaginary character defaults to "i", formats not null
    @Test
    public void testDefaultConstructor_usesDefaultImaginaryCharacterI() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        assertEquals("i", cf.getImaginaryCharacter());
        assertNotNull(cf.getRealFormat());
        assertNotNull(cf.getImaginaryFormat());
    }

    // ComplexFormat(NumberFormat): realFormat is same ref, imaginaryFormat is a clone
    @Test
    public void testConstructorNumberFormat_setsSameRealFormatAndClonedImaginaryFormat() throws Throwable {
        NumberFormat fmt = NumberFormat.getInstance(Locale.US);
        ComplexFormat cf = new ComplexFormat(fmt);
        assertSame(fmt, cf.getRealFormat());
        assertNotSame(fmt, cf.getImaginaryFormat());
        assertEquals("i", cf.getImaginaryCharacter());
    }

    // ComplexFormat(realFormat, imaginaryFormat): both stored by reference, independently
    @Test
    public void testConstructorRealImaginaryFormat_setsIndependentFormats() throws Throwable {
        NumberFormat rf = NumberFormat.getInstance(Locale.US);
        NumberFormat imf = NumberFormat.getInstance(Locale.US);
        ComplexFormat cf = new ComplexFormat(rf, imf);
        assertSame(rf, cf.getRealFormat());
        assertSame(imf, cf.getImaginaryFormat());
    }

    // ComplexFormat(String): custom imaginary char with default number formats
    @Test
    public void testConstructorImaginaryCharacterOnly_setsCharacterAndDefaultFormats() throws Throwable {
        ComplexFormat cf = new ComplexFormat("j");
        assertEquals("j", cf.getImaginaryCharacter());
        assertNotNull(cf.getRealFormat());
    }

    // ComplexFormat(String, NumberFormat): realFormat same ref, imaginaryFormat cloned
    @Test
    public void testConstructorImaginaryCharacterAndFormat_clonesFormatForImaginary() throws Throwable {
        NumberFormat fmt2 = NumberFormat.getInstance(Locale.US);
        ComplexFormat cf = new ComplexFormat("k", fmt2);
        assertEquals("k", cf.getImaginaryCharacter());
        assertSame(fmt2, cf.getRealFormat());
        assertNotSame(fmt2, cf.getImaginaryFormat());
    }

    // ComplexFormat(String, realFormat, imaginaryFormat): all three fields set independently
    @Test
    public void testConstructorImaginaryCharacterRealImaginaryFormat_setsAllThreeFields() throws Throwable {
        NumberFormat rf3 = NumberFormat.getInstance(Locale.US);
        NumberFormat imf3 = NumberFormat.getInstance(Locale.US);
        ComplexFormat cf = new ComplexFormat("m", rf3, imf3);
        assertEquals("m", cf.getImaginaryCharacter());
        assertSame(rf3, cf.getRealFormat());
        assertSame(imf3, cf.getImaginaryFormat());
    }

    // setImaginaryCharacter throws on null -> via constructor path
    @Test
    public void testConstructorImaginaryCharacterNull_throwsIllegalArgumentException() throws Throwable {
        try {
            new ComplexFormat((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // setImaginaryCharacter throws on empty string -> via constructor path
    @Test
    public void testConstructorImaginaryCharacterEmpty_throwsIllegalArgumentException() throws Throwable {
        try {
            new ComplexFormat("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // static formatComplex delegates to a default instance and appends sign + suffix
    @Test
    public void testFormatComplexStatic_appendsPlusAndImaginarySuffix() throws Throwable {
        String result = ComplexFormat.formatComplex(new Complex(0.0, 1.0));
        assertTrue(result.indexOf(" + ") >= 0);
        assertTrue(result.endsWith("i"));
    }

    // format(Complex,...): positive imaginary appends " + " and returns same buffer
    @Test
    public void testFormatComplex_positiveImaginary_appendsPlusSignAndReturnsSameBuffer() throws Throwable {
        Complex c = new Complex(1.0, 2.0);
        StringBuffer sb = new StringBuffer();
        StringBuffer result = complexFormat.format(c, sb, new FieldPosition(0));
        String expected = nf.format(1.0) + " + " + nf.format(2.0) + "i";
        assertEquals(expected, result.toString());
        assertSame(sb, result);
    }

    // format(Complex,...): negative imaginary appends " - " and uses absolute value
    @Test
    public void testFormatComplex_negativeImaginary_appendsMinusSign() throws Throwable {
        Complex c = new Complex(1.0, -2.0);
        StringBuffer sb = new StringBuffer();
        complexFormat.format(c, sb, new FieldPosition(0));
        String expected = nf.format(1.0) + " - " + nf.format(2.0) + "i";
        assertEquals(expected, sb.toString());
    }

    // format(Complex,...): zero imaginary omits the imaginary term entirely
    @Test
    public void testFormatComplex_zeroImaginary_omitsImaginaryTerm() throws Throwable {
        Complex c = new Complex(1.0, 0.0);
        StringBuffer sb = new StringBuffer();
        complexFormat.format(c, sb, new FieldPosition(0));
        assertEquals(nf.format(1.0), sb.toString());
    }

    // format(Complex,...): NaN imaginary takes the "im>0 || isNaN" branch and is wrapped
    @Test
    public void testFormatComplex_NaNImaginary_appendsPlusAndNaN() throws Throwable {
        Complex c = new Complex(1.0, Double.NaN);
        StringBuffer sb = new StringBuffer();
        complexFormat.format(c, sb, new FieldPosition(0));
        assertEquals(nf.format(1.0) + " + (NaN)i", sb.toString());
    }

    // formatDouble: infinite real part is wrapped in parentheses per javadoc
    @Test
    public void testFormatComplex_infiniteReal_wrapsInParentheses() throws Throwable {
        Complex c = new Complex(Double.POSITIVE_INFINITY, 1.0);
        StringBuffer sb = new StringBuffer();
        complexFormat.format(c, sb, new FieldPosition(0));
        assertEquals("(Infinity) + " + nf.format(1.0) + "i", sb.toString());
    }

    // formatDouble: negated negative-infinity imaginary becomes positive Infinity, wrapped
    @Test
    public void testFormatComplex_negativeInfiniteImaginary_wrapsInParentheses() throws Throwable {
        Complex c = new Complex(1.0, Double.NEGATIVE_INFINITY);
        StringBuffer sb = new StringBuffer();
        complexFormat.format(c, sb, new FieldPosition(0));
        assertEquals(nf.format(1.0) + " - (Infinity)i", sb.toString());
    }

    // format(Object,...): Complex instance delegates to format(Complex,...)
    @Test
    public void testFormatObject_ComplexInstance_formatsAsComplex() throws Throwable {
        Complex c = new Complex(3.0, 4.0);
        StringBuffer sb = new StringBuffer();
        complexFormat.format((Object) c, sb, new FieldPosition(0));
        String expected = nf.format(3.0) + " + " + nf.format(4.0) + "i";
        assertEquals(expected, sb.toString());
    }

    // format(Object,...): Number instance is wrapped as Complex(value, 0.0)
    @Test
    public void testFormatObject_NumberInstance_formatsRealOnly() throws Throwable {
        Double val = new Double(5.0);
        StringBuffer sb = new StringBuffer();
        complexFormat.format((Object) val, sb, new FieldPosition(0));
        assertEquals(nf.format(5.0), sb.toString());
    }

    // format(Object,...): unsupported type throws IllegalArgumentException
    @Test
    public void testFormatObject_InvalidType_throwsIllegalArgumentException() throws Throwable {
        StringBuffer sb = new StringBuffer();
        try {
            complexFormat.format((Object) "not a number", sb, new FieldPosition(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getAvailableLocales mirrors NumberFormat's available locale set, must be non-empty
    @Test
    public void testGetAvailableLocales_returnsNonEmptyArray() throws Throwable {
        Locale[] locales = ComplexFormat.getAvailableLocales();
        assertTrue(locales.length > 0);
    }

    // getInstance(): default instance uses default imaginary character "i"
    @Test
    public void testGetInstance_returnsFormatWithDefaultImaginaryCharacter() throws Throwable {
        ComplexFormat f = ComplexFormat.getInstance();
        assertEquals("i", f.getImaginaryCharacter());
    }

    // getInstance(Locale): real format has maximum fraction digits set to 2 per javadoc
    @Test
    public void testGetInstanceLocale_realFormatMaxFractionDigitsIsTwo() throws Throwable {
        ComplexFormat f = ComplexFormat.getInstance(Locale.US);
        assertEquals(2, f.getRealFormat().getMaximumFractionDigits());
    }

    // parse(String): valid "re + imi" string returns a Complex with both parts
    @Test
    public void testParseString_validRealImaginary_returnsComplex() throws Throwable {
        Complex c = complexFormat.parse("1 + 2i");
        assertEquals(1.0, c.getReal(), 1e-9);
        assertEquals(2.0, c.getImaginary(), 1e-9);
    }

    // parse(String): unparseable input throws ParseException per contract
    @Test
    public void testParseString_invalidInput_throwsParseException() throws Throwable {
        try {
            complexFormat.parse("xyz");
            fail("expected ParseException");
        } catch (ParseException expected) {
        }
    }

    // bug-sensitive: "(NaN)" occupying the entire source must parse as NaN, not throw
    @Test
    public void testParse_NaNAtEndOfString_parsesSpecialValue() throws Throwable {
        ComplexFormat defaultFmt = new ComplexFormat();
        Complex c = defaultFmt.parse("(NaN)");
        assertTrue(Double.isNaN(c.getReal()));
        assertEquals(0.0, c.getImaginary(), 1e-9);
    }

    // bug-sensitive: "(Infinity)" occupying the entire source must parse as +Infinity
    @Test
    public void testParse_PositiveInfinityAtEndOfString_parsesSpecialValue() throws Throwable {
        ComplexFormat defaultFmt = new ComplexFormat();
        Complex c = defaultFmt.parse("(Infinity)");
        assertTrue(Double.isInfinite(c.getReal()));
        assertTrue(c.getReal() > 0);
        assertEquals(0.0, c.getImaginary(), 1e-9);
    }

    // bug-sensitive: "(-Infinity)" occupying the entire source must parse as -Infinity
    @Test
    public void testParse_NegativeInfinityAtEndOfString_parsesSpecialValue() throws Throwable {
        ComplexFormat defaultFmt = new ComplexFormat();
        Complex c = defaultFmt.parse("(-Infinity)");
        assertTrue(Double.isInfinite(c.getReal()));
        assertTrue(c.getReal() < 0);
    }

    // parse(source,pos): switch case 0 - no sign char found, returns real-only complex
    @Test
    public void testParseStringParsePosition_noSign_returnsRealOnlyComplex() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Complex c = complexFormat.parse("5", pos);
        assertEquals(5.0, c.getReal(), 1e-9);
        assertEquals(0.0, c.getImaginary(), 1e-9);
    }

    // parse(source,pos): switch case '+' - positive imaginary sign
    @Test
    public void testParseStringParsePosition_plusSign_returnsComplexWithPositiveImaginary() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Complex c = complexFormat.parse("1+2i", pos);
        assertEquals(1.0, c.getReal(), 1e-9);
        assertEquals(2.0, c.getImaginary(), 1e-9);
    }

    // parse(source,pos): switch case '-' - negative imaginary sign
    @Test
    public void testParseStringParsePosition_minusSign_returnsComplexWithNegativeImaginary() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Complex c = complexFormat.parse("1-2i", pos);
        assertEquals(1.0, c.getReal(), 1e-9);
        assertEquals(-2.0, c.getImaginary(), 1e-9);
    }

    // parse(source,pos): switch default - invalid sign char resets index and sets error index
    @Test
    public void testParseStringParsePosition_invalidSign_returnsNullAndResetsIndex() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Complex c = complexFormat.parse("1*2i", pos);
        assertNull(c);
        assertEquals(0, pos.getIndex());
        assertEquals(1, pos.getErrorIndex());
    }

    // parse(source,pos): invalid real number returns null and resets index to initial
    @Test
    public void testParseStringParsePosition_invalidReal_returnsNullAndResetsIndex() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Complex c = complexFormat.parse("abc", pos);
        assertNull(c);
        assertEquals(0, pos.getIndex());
    }

    // parse(source,pos): valid real/sign but invalid imaginary returns null, resets index
    @Test
    public void testParseStringParsePosition_invalidImaginary_returnsNullAndResetsIndex() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Complex c = complexFormat.parse("1+abc", pos);
        assertNull(c);
        assertEquals(0, pos.getIndex());
    }

    // parse(source,pos): imaginary character mismatch returns null and sets error index
    @Test
    public void testParseStringParsePosition_wrongImaginaryCharacter_returnsNull() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Complex c = complexFormat.parse("1+2j", pos);
        assertNull(c);
        assertEquals(0, pos.getIndex());
        assertEquals(3, pos.getErrorIndex());
    }

    // parse(source,pos): leading whitespace before the real part is skipped
    @Test
    public void testParseStringParsePosition_leadingWhitespace_isSkipped() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Complex c = complexFormat.parse(" 5", pos);
        assertEquals(5.0, c.getReal(), 1e-9);
        assertEquals(0.0, c.getImaginary(), 1e-9);
    }

    // parseObject delegates to parse(source,pos) and returns a Complex
    @Test
    public void testParseObject_delegatesToParseAndReturnsComplex() throws Throwable {
        ParsePosition pos = new ParsePosition(0);
        Object obj = complexFormat.parseObject("3", pos);
        assertTrue(obj instanceof Complex);
        assertEquals(3.0, ((Complex) obj).getReal(), 1e-9);
    }

    // setImaginaryCharacter rejects null
    @Test
    public void testSetImaginaryCharacter_null_throwsIllegalArgumentException() throws Throwable {
        try {
            complexFormat.setImaginaryCharacter(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // setImaginaryCharacter rejects empty string
    @Test
    public void testSetImaginaryCharacter_empty_throwsIllegalArgumentException() throws Throwable {
        try {
            complexFormat.setImaginaryCharacter("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // setImaginaryCharacter with valid value updates the getter
    @Test
    public void testSetImaginaryCharacter_valid_updatesGetter() throws Throwable {
        complexFormat.setImaginaryCharacter("z");
        assertEquals("z", complexFormat.getImaginaryCharacter());
    }

    // setImaginaryFormat rejects null
    @Test
    public void testSetImaginaryFormat_null_throwsIllegalArgumentException() throws Throwable {
        try {
            complexFormat.setImaginaryFormat(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // setImaginaryFormat with valid value updates the getter reference
    @Test
    public void testSetImaginaryFormat_valid_updatesGetter() throws Throwable {
        NumberFormat newImf = NumberFormat.getInstance(Locale.US);
        complexFormat.setImaginaryFormat(newImf);
        assertSame(newImf, complexFormat.getImaginaryFormat());
    }

    // setRealFormat rejects null
    @Test
    public void testSetRealFormat_null_throwsIllegalArgumentException() throws Throwable {
        try {
            complexFormat.setRealFormat(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // setRealFormat with valid value updates the getter reference
    @Test
    public void testSetRealFormat_valid_updatesGetter() throws Throwable {
        NumberFormat newRf = NumberFormat.getInstance(Locale.US);
        complexFormat.setRealFormat(newRf);
        assertSame(newRf, complexFormat.getRealFormat());
    }
}
