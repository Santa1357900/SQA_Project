package org.jsoup.nodes;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

public class EntitiesClaudeTest {

    private CharsetEncoder asciiEncoder;

    @Before
    public void setUp() throws Throwable {
        asciiEncoder = Charset.forName("US-ASCII").newEncoder();
    }

    // covers map.containsKey(c) branch for base mode entity lookup (&)
    @Test
    public void testEscape_baseMode_ampersandEntity() throws Throwable {
        String result = Entities.escape("&", asciiEncoder, Entities.EscapeMode.base);
        assertEquals("&amp;", result);
    }

    // covers multiple map-hit iterations in a loop (<, >, ")
    @Test
    public void testEscape_baseMode_ltGtQuot() throws Throwable {
        String result = Entities.escape("<>\"", asciiEncoder, Entities.EscapeMode.base);
        assertEquals("&lt;&gt;&quot;", result);
    }

    // covers extended map branch (escapeMode == extended -> fullByVal)
    @Test
    public void testEscape_extendedMode_forallSymbol() throws Throwable {
        String result = Entities.escape("\u2200", asciiEncoder, Entities.EscapeMode.extended);
        assertEquals("&forall;", result);
    }

    // covers base-mode miss (not in baseByVal) + encoder.canEncode false -> numeric fallback
    @Test
    public void testEscape_baseMode_forallNotInBase_numericFallback() throws Throwable {
        String result = Entities.escape("\u2200", asciiEncoder, Entities.EscapeMode.base);
        assertEquals("&#8704;", result);
    }

    // covers encoder.canEncode true branch -> literal char appended unchanged
    @Test
    public void testEscape_encodableAsciiChar_literalOutput() throws Throwable {
        String result = Entities.escape("a", asciiEncoder, Entities.EscapeMode.base);
        assertEquals("a", result);
    }

    // covers char not in map AND not encodable -> numeric escape branch
    @Test
    public void testEscape_nonEncodableCharNotInMap_numericEscape() throws Throwable {
        String result = Entities.escape("\u4e2d", asciiEncoder, Entities.EscapeMode.base);
        assertEquals("&#20013;", result);
    }

    // covers zero-iteration loop (empty input string)
    @Test
    public void testEscape_emptyString_returnsEmpty() throws Throwable {
        String result = Entities.escape("", asciiEncoder, Entities.EscapeMode.base);
        assertEquals("", result);
    }

    // covers mixed iterations: literal char + map hit + literal char in same loop
    @Test
    public void testEscape_multipleCharsMixed() throws Throwable {
        String result = Entities.escape("a&b", asciiEncoder, Entities.EscapeMode.base);
        assertEquals("a&amp;b", result);
    }

    // covers map hit for non-ascii char present in baseByVal (copyright symbol)
    @Test
    public void testEscape_baseMode_copyrightSymbol() throws Throwable {
        String result = Entities.escape("\u00A9", asciiEncoder, Entities.EscapeMode.base);
        assertEquals("&copy;", result);
    }

    // covers early-return branch when input does not contain '&'
    @Test
    public void testUnescape_noAmpersand_returnsSameString() throws Throwable {
        String result = Entities.unescape("hello world");
        assertEquals("hello world", result);
    }

    // covers named entity lookup branch, full match with trailing semicolon
    @Test
    public void testUnescape_namedEntityWithSemicolon() throws Throwable {
        String result = Entities.unescape("&amp;");
        assertEquals("&", result);
    }

    // covers named entity branch without trailing semicolon (';?' optional)
    @Test
    public void testUnescape_namedEntityWithoutSemicolon() throws Throwable {
        String result = Entities.unescape("&amp");
        assertEquals("&", result);
    }

    // covers numeric decimal parsing branch (base 10)
    @Test
    public void testUnescape_decimalNumeric() throws Throwable {
        String result = Entities.unescape("&#65;");
        assertEquals("A", result);
    }

    // covers numeric hex parsing branch with lowercase x indicator (base 16)
    @Test
    public void testUnescape_hexNumericLowercaseX() throws Throwable {
        String result = Entities.unescape("&#x41;");
        assertEquals("A", result);
    }

    // covers numeric hex parsing branch with uppercase X indicator (base 16)
    @Test
    public void testUnescape_hexNumericUppercaseX() throws Throwable {
        String result = Entities.unescape("&#X41;");
        assertEquals("A", result);
    }

    // covers name-not-found-in-full-map branch -> charval stays -1 -> text kept unchanged
    @Test
    public void testUnescape_unknownNamedEntity_unchanged() throws Throwable {
        String result = Entities.unescape("&foobar;");
        assertEquals("&foobar;", result);
    }

    // covers NumberFormatException catch branch for invalid decimal digits (hex letters in decimal context)
    @Test
    public void testUnescape_invalidDecimalWithHexDigits_unchanged() throws Throwable {
        String result = Entities.unescape("&#1a2;");
        assertEquals("&#1a2;", result);
    }

    // covers loop executing multiple times (multiple matches in one string)
    @Test
    public void testUnescape_multipleEntitiesInText() throws Throwable {
        String result = Entities.unescape("Hello &amp; &lt;World&gt;");
        assertEquals("Hello & <World>", result);
    }

    // covers contains('&') true but regex never matches -> appendTail copies all text
    @Test
    public void testUnescape_loneAmpersand_noMatch_unchanged() throws Throwable {
        String result = Entities.unescape("A & B");
        assertEquals("A & B", result);
    }

    // bug hunt: numeric codepoint above 0xFFFF must not be truncated into a bogus char;
    // per the "out of range" comment the original entity text must be preserved
    @Test
    public void testUnescape_outOfBmpRangeNumeric_keepsOriginalEntityText() throws Throwable {
        String result = Entities.unescape("&#65536;");
        assertEquals("&#65536;", result);
    }

    // covers boundary charval == 0 (valid, not -1) -> converted to char value 0
    @Test
    public void testUnescape_zeroValue() throws Throwable {
        String result = Entities.unescape("&#0;");
        assertEquals(1, result.length());
        assertEquals(0, (int) result.charAt(0));
    }

    // covers boundary charval == 0xFFFF (still within valid char range) -> converted to char
    @Test
    public void testUnescape_maxBmpValue() throws Throwable {
        String result = Entities.unescape("&#65535;");
        assertEquals(1, result.length());
        assertEquals(65535, (int) result.charAt(0));
    }

    // covers repeated matches of the same named entity in sequence
    @Test
    public void testUnescape_repeatedSameEntity() throws Throwable {
        String result = Entities.unescape("&amp;&amp;");
        assertEquals("&&", result);
    }
}
