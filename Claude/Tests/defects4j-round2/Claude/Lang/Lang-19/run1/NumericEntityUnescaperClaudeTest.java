package org.apache.commons.lang3.text.translate;

import java.io.StringWriter;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class NumericEntityUnescaperClaudeTest {

    private NumericEntityUnescaper unescaper;
    private StringWriter out;

    @Before
    public void setUp() throws Throwable {
        unescaper = new NumericEntityUnescaper();
        out = new StringWriter();
    }

    // outer if: charAt(index) != '&' -> return 0, nothing written
    @Test
    public void testTranslate_charAtIndexNotAmpersand_returnsZero() throws Throwable {
        int result = unescaper.translate("abc", 0, out);
        assertEquals(0, result);
        assertEquals("", out.toString());
    }

    // outer if boundary: '&' is the very last char -> index < seqEnd-1 is false -> return 0
    @Test
    public void testTranslate_ampersandAtLastPosition_returnsZero() throws Throwable {
        int result = unescaper.translate("&", 0, out);
        assertEquals(0, result);
        assertEquals("", out.toString());
    }

    // outer if: '&' followed by char that is not '#' -> return 0
    @Test
    public void testTranslate_ampersandSecondToLastButNotHash_returnsZero() throws Throwable {
        int result = unescaper.translate("&x", 0, out);
        assertEquals(0, result);
        assertEquals("", out.toString());
    }

    // decimal entity with semicolon, isHex=false branch, happy path
    @Test
    public void testTranslate_decimalEntityWithSemicolon_decodesCorrectly() throws Throwable {
        int result = unescaper.translate("&#65;", 0, out);
        assertEquals(5, result);
        assertEquals("A", out.toString());
    }

    // hex entity with lowercase 'x', isHex=true branch
    @Test
    public void testTranslate_hexEntityLowercaseX_decodesCorrectly() throws Throwable {
        int result = unescaper.translate("&#x41;", 0, out);
        assertEquals(6, result);
        assertEquals("A", out.toString());
    }

    // hex entity with uppercase 'X', isHex=true branch
    @Test
    public void testTranslate_hexEntityUppercaseX_decodesCorrectly() throws Throwable {
        int result = unescaper.translate("&#X41;", 0, out);
        assertEquals(6, result);
        assertEquals("A", out.toString());
    }

    // entityValue > 0xFFFF (decimal) -> surrogate pair branch
    @Test
    public void testTranslate_entityAbove0xFFFFDecimal_writesSurrogatePair() throws Throwable {
        int result = unescaper.translate("&#65536;", 0, out);
        assertEquals(8, result);
        String written = out.toString();
        assertEquals(2, written.length());
        assertEquals(65536, Character.toCodePoint(written.charAt(0), written.charAt(1)));
    }

    // entityValue exactly 0xFFFF -> NOT greater than 0xFFFF -> single char branch
    @Test
    public void testTranslate_entityExactly0xFFFF_writesSingleChar() throws Throwable {
        int result = unescaper.translate("&#65535;", 0, out);
        assertEquals(8, result);
        String written = out.toString();
        assertEquals(1, written.length());
        assertEquals(0xFFFF, (int) written.charAt(0));
    }

    // invalid decimal digits -> NumberFormatException caught -> return 0, nothing written
    @Test
    public void testTranslate_invalidDecimalDigits_returnsZeroNoWrite() throws Throwable {
        int result = unescaper.translate("&#abc;", 0, out);
        assertEquals(0, result);
        assertEquals("", out.toString());
    }

    // invalid hex digits -> NumberFormatException caught -> return 0, nothing written
    @Test
    public void testTranslate_invalidHexDigits_returnsZeroNoWrite() throws Throwable {
        int result = unescaper.translate("&#xZZ;", 0, out);
        assertEquals(0, result);
        assertEquals("", out.toString());
    }

    // decimal number empty (';' immediately) -> while loop 0 iterations -> parseInt("") -> NFE -> return 0
    @Test
    public void testTranslate_emptyDecimalNumber_zeroIterations_returnsZero() throws Throwable {
        int result = unescaper.translate("&#;", 0, out);
        assertEquals(0, result);
        assertEquals("", out.toString());
    }

    // hex number empty (';' immediately after x) -> while loop 0 iterations -> parseInt("") -> NFE -> return 0
    @Test
    public void testTranslate_emptyHexNumber_zeroIterations_returnsZero() throws Throwable {
        int result = unescaper.translate("&#x;", 0, out);
        assertEquals(0, result);
        assertEquals("", out.toString());
    }

    // multi digit decimal -> while loop runs multiple iterations, verifies return length formula
    @Test
    public void testTranslate_multiDigitDecimal_multipleIterations_correctReturnLength() throws Throwable {
        int result = unescaper.translate("&#12345;", 0, out);
        assertEquals(8, result);
        assertEquals(1, out.toString().length());
        assertEquals(12345, (int) out.toString().charAt(0));
    }

    // index parameter not zero: entity embedded in a larger string
    @Test
    public void testTranslate_indexInMiddleOfLargerString_decodesCorrectly() throws Throwable {
        int result = unescaper.translate("xx&#65;yy", 2, out);
        assertEquals(5, result);
        assertEquals("A", out.toString());
    }

    // trailing content after ';' must not be consumed/written
    @Test
    public void testTranslate_trailingContentAfterSemicolon_notConsumed() throws Throwable {
        int result = unescaper.translate("&#65;extra", 0, out);
        assertEquals(5, result);
        assertEquals("A", out.toString());
    }

    // entityValue == 0 edge case
    @Test
    public void testTranslate_entityValueZero_writesNullChar() throws Throwable {
        int result = unescaper.translate("&#0;", 0, out);
        assertEquals(4, result);
        assertEquals(1, out.toString().length());
        assertEquals(0, (int) out.toString().charAt(0));
    }

    // decimal overflow beyond Integer.MAX_VALUE -> NumberFormatException -> return 0
    @Test
    public void testTranslate_decimalOverflow_numberFormatException_returnsZero() throws Throwable {
        int result = unescaper.translate("&#99999999999;", 0, out);
        assertEquals(0, result);
        assertEquals("", out.toString());
    }

    // BUG HUNT: decimal entity with no trailing ';' reaching end of input.
    // Javadoc states the semicolon is optional / malformed entities elsewhere
    // in this method gracefully return 0 (see NumberFormatException branch).
    // A correct implementation must not throw when scanning for ';' runs off
    // the end of the input; it should fail gracefully with 0, writing nothing.
    @Test
    public void testTranslate_noSemicolonDecimalAtEndOfInput_doesNotThrowReturnsZero() throws Throwable {
        int result = unescaper.translate("&#1234", 0, out);
        assertEquals(0, result);
        assertEquals("", out.toString());
    }

    // BUG HUNT variant exercising the isHex=true path with no trailing ';'
    // reaching end of input: must also fail gracefully, not throw.
    @Test
    public void testTranslate_noSemicolonHexAtEndOfInput_doesNotThrowReturnsZero() throws Throwable {
        int result = unescaper.translate("&#x1A", 0, out);
        assertEquals(0, result);
        assertEquals("", out.toString());
    }

    // hex entity producing codepoint > 0xFFFF -> surrogate pair branch via hex path
    @Test
    public void testTranslate_hexEntityAbove0xFFFF_writesSurrogatePair() throws Throwable {
        int result = unescaper.translate("&#x1F600;", 0, out);
        assertEquals(9, result);
        String written = out.toString();
        assertEquals(2, written.length());
        assertEquals(128512, Character.toCodePoint(written.charAt(0), written.charAt(1)));
    }

    // hex entity with leading zero digits, multiple loop iterations in hex branch
    @Test
    public void testTranslate_hexEntityWithLeadingZero_decodesCorrectly() throws Throwable {
        int result = unescaper.translate("&#x0041;", 0, out);
        assertEquals(8, result);
        assertEquals("A", out.toString());
    }
}
