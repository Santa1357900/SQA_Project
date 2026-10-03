package org.apache.commons.lang3.text.translate;

import java.io.StringWriter;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class NumericEntityUnescaperClaudeTest {

    private NumericEntityUnescaper unescaper;

    @Before
    public void setUp() throws Throwable {
        unescaper = new NumericEntityUnescaper();
    }

    // instantiation sanity check
    @Test
    public void testConstructor_createsInstance_notNull() throws Throwable {
        assertNotNull(unescaper);
    }

    // branch: first char != '&' -> short circuit, returns 0, nothing written
    @Test
    public void testTranslate_firstCharNotAmpersand_returnsZeroNoWrite() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("xyz", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // branch: first char '&' but second char != '#' -> returns 0
    @Test
    public void testTranslate_secondCharNotHash_returnsZeroNoWrite() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&y", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // branch: decimal entity (firstChar not x/X), normal parse, write codepoint
    @Test
    public void testTranslate_decimalEntityBasic_writesCharAndReturnsLength() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#65;", 0, writer);
        assertEquals(5, consumed);
        assertEquals("A", writer.toString());
    }

    // branch: hex entity using lowercase 'x'
    @Test
    public void testTranslate_hexEntityLowercaseX_writesCharAndReturnsLength() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#x41;", 0, writer);
        assertEquals(6, consumed);
        assertEquals("A", writer.toString());
    }

    // branch: hex entity using uppercase 'X'
    @Test
    public void testTranslate_hexEntityUppercaseX_writesCharAndReturnsLength() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#X41;", 0, writer);
        assertEquals(6, consumed);
        assertEquals("A", writer.toString());
    }

    // hex parsing with uppercase hex digits (A-F)
    @Test
    public void testTranslate_hexEntityUppercaseDigits_writesCorrectChar() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#xFF;", 0, writer);
        assertEquals(6, consumed);
        assertEquals(String.valueOf((char) 255), writer.toString());
    }

    // branch: zero digits between '#' and ';' for decimal -> NumberFormatException -> return 0
    @Test
    public void testTranslate_emptyDecimalDigits_returnsZeroNoWrite() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#;", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // branch: zero digits for hex -> NumberFormatException -> return 0
    @Test
    public void testTranslate_emptyHexDigits_returnsZeroNoWrite() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#x;", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // branch: invalid non-numeric decimal characters -> catch NumberFormatException -> return 0
    @Test
    public void testTranslate_invalidDecimalChars_returnsZeroNoWrite() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#ab;", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // branch: invalid hex characters -> catch NumberFormatException -> return 0
    @Test
    public void testTranslate_invalidHexChars_returnsZeroNoWrite() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#xg;", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // branch: decimal value overflows Integer range -> NumberFormatException -> return 0
    @Test
    public void testTranslate_decimalOverflow_returnsZeroNoWrite() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#2147483648;", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // branch: hex value overflows Integer range -> NumberFormatException -> return 0
    @Test
    public void testTranslate_hexOverflow_returnsZeroNoWrite() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#xFFFFFFFF;", 0, writer);
        assertEquals(0, consumed);
        assertEquals("", writer.toString());
    }

    // edge value: codepoint 0 is valid and must be written as NUL char
    @Test
    public void testTranslate_zeroCodePoint_writesNulChar() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#0;", 0, writer);
        assertEquals(4, consumed);
        assertEquals(1, writer.toString().length());
        assertEquals(0, writer.toString().charAt(0));
    }

    // index parameter offset: entity located not at position 0 of the input
    @Test
    public void testTranslate_indexOffsetWithinLargerString_decodesCorrectly() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("ab&#65;cd", 2, writer);
        assertEquals(5, consumed);
        assertEquals("A", writer.toString());
    }

    // loop runs multiple iterations locating ';' across several digits
    @Test
    public void testTranslate_multipleDigitsLoop_decimal_writesCharAndReturnsLength() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#12345;", 0, writer);
        assertEquals(8, consumed);
        assertEquals(String.valueOf((char) 12345), writer.toString());
    }

    // boundary: highest BMP codepoint 0xFFFF must be written as a single char
    @Test
    public void testTranslate_boundaryBmpCodePoint65535_writesSingleChar() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#65535;", 0, writer);
        assertEquals(8, consumed);
        assertEquals(1, writer.toString().length());
        assertEquals((char) 65535, writer.toString().charAt(0));
    }

    // BUG TARGET: codepoint beyond BMP (0x10000) must be written as a UTF-16 surrogate pair,
    // per Javadoc "to the specific codepoint"; a naive out.write(int) truncates to one wrong char.
    @Test
    public void testTranslate_supplementaryCodePoint65536_writesSurrogatePair() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#65536;", 0, writer);
        assertEquals(8, consumed);
        String expected = new String(Character.toChars(65536));
        assertEquals(expected, writer.toString());
    }

    // BUG TARGET (confirmation with a different supplementary codepoint)
    @Test
    public void testTranslate_supplementaryCodePoint70000_writesSurrogatePair() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#70000;", 0, writer);
        assertEquals(8, consumed);
        String expected = new String(Character.toChars(70000));
        assertEquals(expected, writer.toString());
    }

    // decodes the ampersand entity itself back to '&'
    @Test
    public void testTranslate_ampersandEntityItself_decodesToAmpersandChar() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#38;", 0, writer);
        assertEquals(5, consumed);
        assertEquals("&", writer.toString());
    }

    // loop runs exactly one iteration (single digit before ';')
    @Test
    public void testTranslate_singleDigitDecimal_oneLoopIteration() throws Throwable {
        StringWriter writer = new StringWriter();
        int consumed = unescaper.translate("&#5;", 0, writer);
        assertEquals(4, consumed);
        assertEquals((char) 5, writer.toString().charAt(0));
    }

    // out-of-bounds access: input too short to contain the second marker char ('#')
    @Test
    public void testTranslate_tooShortInputMissingSecondChar_throwsStringIndexOutOfBounds() throws Throwable {
        StringWriter writer = new StringWriter();
        try {
            unescaper.translate("&", 0, writer);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
            // expected per CharSequence.charAt contract
        }
    }
}
