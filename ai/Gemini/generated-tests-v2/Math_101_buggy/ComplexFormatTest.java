package org.apache.commons.math.complex;

import org.junit.Test;
import static org.junit.Assert.*;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;

public class ComplexFormatTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        assertNotNull(cf);
        assertEquals("i", cf.getImaginaryCharacter());
        assertNotNull(cf.getRealFormat());
        assertNotNull(cf.getImaginaryFormat());
    }

    @Test
    public void testNumberFormatConstructor() throws Throwable {
        NumberFormat nf = NumberFormat.getInstance();
        ComplexFormat cf = new ComplexFormat(nf);
        assertEquals("i", cf.getImaginaryCharacter());
        assertEquals(nf, cf.getRealFormat());
        assertEquals(nf, cf.getImaginaryFormat());
    }

    @Test
    public void testRealAndImaginaryFormatConstructor() throws Throwable {
        NumberFormat rf = NumberFormat.getInstance();
        NumberFormat imf = NumberFormat.getInstance();
        ComplexFormat cf = new ComplexFormat(rf, imf);
        assertEquals("i", cf.getImaginaryCharacter());
        assertEquals(rf, cf.getRealFormat());
        assertEquals(imf, cf.getImaginaryFormat());
    }

    @Test
    public void testStringImaginaryCharacterConstructor() throws Throwable {
        ComplexFormat cf = new ComplexFormat("j");
        assertEquals("j", cf.getImaginaryCharacter());
        assertNotNull(cf.getRealFormat());
        assertNotNull(cf.getImaginaryFormat());
    }

    @Test
    public void testStringAndFormatConstructor() throws Throwable {
        NumberFormat nf = NumberFormat.getInstance();
        ComplexFormat cf = new ComplexFormat("j", nf);
        assertEquals("j", cf.getImaginaryCharacter());
        assertEquals(nf, cf.getRealFormat());
        assertEquals(nf, cf.getImaginaryFormat());
    }

    @Test
    public void testFullArgsConstructor() throws Throwable {
        NumberFormat rf = NumberFormat.getInstance();
        NumberFormat imf = NumberFormat.getInstance();
        ComplexFormat cf = new ComplexFormat("j", rf, imf);
        assertEquals("j", cf.getImaginaryCharacter());
        assertEquals(rf, cf.getRealFormat());
        assertEquals(imf, cf.getImaginaryFormat());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetImaginaryCharacterNull() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        cf.setImaginaryCharacter(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetImaginaryCharacterEmpty() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        cf.setImaginaryCharacter("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetRealFormatNull() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        cf.setRealFormat(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetImaginaryFormatNull() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        cf.setImaginaryFormat(null);
    }

    @Test
    public void testGetInstanceLocale() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance(Locale.US);
        assertNotNull(cf);
        assertEquals("i", cf.getImaginaryCharacter());
    }

    @Test
    public void testGetInstanceDefault() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance();
        assertNotNull(cf);
    }

    @Test
    public void testGetAvailableLocales() throws Throwable {
        Locale[] locales = ComplexFormat.getAvailableLocales();
        assertNotNull(locales);
        assertTrue(locales.length > 0);
    }

    @Test
    public void testFormatComplexStatic() throws Throwable {
        Complex c = new Complex(1.0, 1.0);
        String formatted = ComplexFormat.formatComplex(c);
        assertNotNull(formatted);
        assertTrue(formatted.contains("1"));
    }

    @Test
    public void testFormatPositiveImaginary() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        Complex c = new Complex(1.0, 2.0);
        String res = cf.format(c);
        assertEquals("1 + 2i", res);
    }

    @Test
    public void testFormatNegativeImaginary() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        Complex c = new Complex(1.0, -2.0);
        String res = cf.format(c);
        assertEquals("1 - 2i", res);
    }

    @Test
    public void testFormatZeroImaginary() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        Complex c = new Complex(1.0, 0.0);
        String res = cf.format(c);
        assertEquals("1", res);
    }

    @Test
    public void testFormatNaNImaginary() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        Complex c = new Complex(1.0, Double.NaN);
        String res = cf.format(c);
        assertEquals("1 + (NaN)i", res);
    }

    @Test
    public void testFormatSpecialValuesReal() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        Complex c1 = new Complex(Double.NaN, 1.0);
        assertEquals("(NaN) + 1i", cf.format(c1));

        Complex c2 = new Complex(Double.POSITIVE_INFINITY, 1.0);
        assertEquals("(Infinity) + 1i", cf.format(c2));

        Complex c3 = new Complex(Double.NEGATIVE_INFINITY, 1.0);
        assertEquals("(-Infinity) + 1i", cf.format(c3));
    }

    @Test
    public void testFormatObjectComplex() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        Object obj = new Complex(2.0, 3.0);
        StringBuffer sb = new StringBuffer();
        FieldPosition fp = new FieldPosition(0);
        StringBuffer res = cf.format(obj, sb, fp);
        assertEquals("2 + 3i", res.toString());
    }

    @Test
    public void testFormatObjectNumber() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        Object obj = Double.valueOf(5.0);
        StringBuffer sb = new StringBuffer();
        FieldPosition fp = new FieldPosition(0);
        StringBuffer res = cf.format(obj, sb, fp);
        assertEquals("5", res.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatObjectInvalidType() throws Throwable {
        ComplexFormat cf = new ComplexFormat();
        cf.format("InvalidObject", new StringBuffer(), new FieldPosition(0));
    }

    @Test
    public void testParseSimple() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance(Locale.US);
        Complex c = cf.parse("1 + 1i");
        assertNotNull(c);
        assertEquals(1.0, c.getReal(), 0.01);
        assertEquals(1.0, c.getImaginary(), 0.01);
    }

    @Test
    public void testParseNegativeImaginary() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance(Locale.US);
        Complex c = cf.parse("1 - 2i");
        assertNotNull(c);
        assertEquals(1.0, c.getReal(), 0.01);
        assertEquals(-2.0, c.getImaginary(), 0.01);
    }

    @Test
    public void testParseRealOnly() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance(Locale.US);
        Complex c = cf.parse("5");
        assertNotNull(c);
        assertEquals(5.0, c.getReal(), 0.01);
        assertEquals(0.0, c.getImaginary(), 0.01);
    }

    @Test
    public void testParseSpecialValues() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance(Locale.US);
        Complex c1 = cf.parse("(NaN) + (Infinity)i");
        assertNotNull(c1);
        assertTrue(Double.isNaN(c1.getReal()));
        assertTrue(Double.isInfinite(c1.getImaginary()));
    }

    @Test(expected = ParseException.class)
    public void testParseInvalidString() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance(Locale.US);
        cf.parse("not a complex number");
    }

    @Test
    public void testParseWithParsePositionInvalidSign() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance(Locale.US);
        ParsePosition pos = new ParsePosition(0);
        Complex c = cf.parse("1 * 1i", pos);
        assertNull(c);
        assertEquals(0, pos.getIndex());
        assertTrue(pos.getErrorIndex() >= 0);
    }

    @Test
    public void testParseWithParsePositionInvalidReal() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance(Locale.US);
        ParsePosition pos = new ParsePosition(0);
        Complex c = cf.parse("abc + 1i", pos);
        assertNull(c);
        assertEquals(0, pos.getIndex());
    }

    @Test
    public void testParseWithParsePositionInvalidImaginary() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance(Locale.US);
        ParsePosition pos = new ParsePosition(0);
        Complex c = cf.parse("1 + abc i", pos);
        assertNull(c);
        assertEquals(0, pos.getIndex());
    }

    @Test
    public void testParseWithParsePositionInvalidImaginaryChar() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance(Locale.US);
        ParsePosition pos = new ParsePosition(0);
        Complex c = cf.parse("1 + 1j", pos);
        assertNull(c);
        assertEquals(0, pos.getIndex());
        assertTrue(pos.getErrorIndex() >= 0);
    }

    @Test
    public void testParseObjectWithPosition() throws Throwable {
        ComplexFormat cf = ComplexFormat.getInstance(Locale.US);
        ParsePosition pos = new ParsePosition(0);
        Object obj = cf.parseObject("2 + 2i", pos);
        assertNotNull(obj);
        assertTrue(obj instanceof Complex);
        assertEquals(2.0, ((Complex) obj).getReal(), 0.01);
    }
}