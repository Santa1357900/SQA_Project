package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

public class EntitiesTest {

    @Test
    public void testEscapeBaseMode() throws Throwable {
        Document.OutputSettings out = new Document.OutputSettings();
        out.escapeMode(Entities.EscapeMode.base);
        
        String input = "A & B < C > D \" E ' F";
        String escaped = Entities.escape(input, out);
        
        assertTrue(escaped.contains("&amp;"));
        assertTrue(escaped.contains("&lt;"));
        assertTrue(escaped.contains("&gt;"));
        assertTrue(escaped.contains("&quot;"));
    }

    @Test
    public void testEscapeExtendedMode() throws Throwable {
        CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
        String input = "\u00A9"; // Copyright symbol
        String escaped = Entities.escape(input, encoder, Entities.EscapeMode.extended);
        
        assertEquals("&copy;", escaped);
    }

    @Test
    public void testEscapeUnencodableChar() throws Throwable {
        CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
        String input = "\u0100"; // Character not in US-ASCII
        String escaped = Entities.escape(input, encoder, Entities.EscapeMode.base);
        
        assertEquals("&#256;", escaped);
    }

    @Test
    public void testUnescapeNoAmpersand() throws Throwable {
        String input = "No entities here!";
        String result = Entities.unescape(input);
        assertEquals(input, result);
    }

    @Test
    public void testUnescapeNamedEntity() throws Throwable {
        String input = "&amp; &lt; &gt; &quot;";
        String result = Entities.unescape(input);
        assertEquals("& < > \"", result);
    }

    @Test
    public void testUnescapeDecimalNumericEntity() throws Throwable {
        String input = "&#38; &#60;";
        String result = Entities.unescape(input);
        assertEquals("& <", result);
    }

    @Test
    public void testUnescapeHexNumericEntity() throws Throwable {
        String input = "&#x26; &#x3C; &#X26;";
        String result = Entities.unescape(input);
        assertEquals("& < &", result);
    }

    @Test
    public void testUnescapeInvalidNumberFormat() throws Throwable {
        String input = "&#xyz;";
        String result = Entities.unescape(input);
        assertEquals("&#xyz;", result);
    }

    @Test
    public void testUnescapeUnknownEntity() throws Throwable {
        String input = "&unknownentity;";
        String result = Entities.unescape(input);
        assertEquals("&unknownentity;", result);
    }

    @Test
    public void testUnescapeOutOfRangeChar() throws Throwable {
        // Character value larger than 0xFFFF or invalid mapped values tested via pattern match
        String input = "&#1114111;"; // beyond 0xFFFF
        String result = Entities.unescape(input);
        assertEquals("&#1114111;", result);
    }
}