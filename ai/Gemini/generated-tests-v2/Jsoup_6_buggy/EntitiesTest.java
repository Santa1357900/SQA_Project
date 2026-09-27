package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

public class EntitiesTest {

    @Test
    public void testEscapeBaseMode() throws Throwable {
        CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
        String input = "A & B < C > D \" E";
        String escaped = Entities.escape(input, encoder, Entities.EscapeMode.base);
        assertNotNull(escaped);
        assertTrue(escaped.contains("&amp;"));
        assertTrue(escaped.contains("&lt;"));
        assertTrue(escaped.contains("&gt;"));
        assertTrue(escaped.contains("&quot;"));
    }

    @Test
    public void testEscapeExtendedMode() throws Throwable {
        CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
        String input = "\u00A0"; // nbsp
        String escaped = Entities.escape(input, encoder, Entities.EscapeMode.extended);
        assertNotNull(escaped);
        assertEquals("&nbsp;", escaped);
    }

    @Test
    public void testEscapeUnencodableChar() throws Throwable {
        // ASCII-only encoder to force numeric entity fallback
        CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
        String input = "\u00A0"; // non-breaking space, not in US-ASCII
        String escaped = Entities.escape(input, encoder, Entities.EscapeMode.base);
        assertEquals("&#160;", escaped);
    }

    @Test
    public void testEscapeWithDocumentOutputSettings() throws Throwable {
        Document doc = new Document("");
        Document.OutputSettings out = doc.outputSettings();
        out.escapeMode(Entities.EscapeMode.base);
        String result = Entities.escape("Hello & World", out);
        assertEquals("Hello &amp; World", result);
    }

    @Test
    public void testUnescapeNoAmpersand() throws Throwable {
        String input = "No entities here";
        String result = Entities.unescape(input);
        assertEquals(input, result);
    }

    @Test
    public void testUnescapeNamedEntity() throws Throwable {
        String input = "Hello &amp; Welcome &quot;Test&quot;";
        String result = Entities.unescape(input);
        assertEquals("Hello & Welcome \"Test\"", result);
    }

    @Test
    public void testUnescapeDecimalNumericEntity() throws Throwable {
        String input = "&#38;&#60;";
        String result = Entities.unescape(input);
        assertEquals("&<", result);
    }

    @Test
    public void testUnescapeHexNumericEntity() throws Throwable {
        String input = "&#x26;&#x3C;";
        String result = Entities.unescape(input);
        assertEquals("&<", result);
    }

    @Test
    public void testUnescapeUppercaseHexNumericEntity() throws Throwable {
        String input = "&#X26;";
        String result = Entities.unescape(input);
        assertEquals("&", result);
    }

    @Test
    public void testUnescapeInvalidNumberFormat() throws Throwable {
        String input = "&#99999999999999;";
        String result = Entities.unescape(input);
        assertEquals(input, result);
    }

    @Test
    public void testUnescapeInvalidEntityName() throws Throwable {
        String input = "&notarealentitynameatall;";
        String result = Entities.unescape(input);
        assertEquals(input, result);
    }

    @Test
    public void testUnescapeOutOfRangeChar() throws Throwable {
        // Test a character code that exceeds 0xFFFF or is -1
        String input = "&#x10FFFF;";
        String result = Entities.unescape(input);
        // charval > 0xFFFF condition check
        assertNotNull(result);
    }
}