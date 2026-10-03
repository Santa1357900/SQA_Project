package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

public class EntitiesTest {

    @Test
    public void testEscapeModeGetMap() throws Throwable {
        assertNotNull(Entities.EscapeMode.xhtml.getMap());
        assertNotNull(Entities.EscapeMode.base.getMap());
        assertNotNull(Entities.EscapeMode.extended.getMap());
    }

    @Test
    public void testEscapeBasic() throws Throwable {
        CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
        String input = "Hello & ' \" < > test";
        String escaped = Entities.escape(input, encoder, Entities.EscapeMode.base);
        assertNotNull(escaped);
        assertTrue(escaped.contains("&amp;"));
        assertTrue(escaped.contains("&quot;"));
        assertTrue(escaped.contains("&lt;"));
        assertTrue(escaped.contains("&gt;"));
    }

    @Test
    public void testEscapeXhtml() throws Throwable {
        CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
        String input = "\" & ' < >";
        String escaped = Entities.escape(input, encoder, Entities.EscapeMode.xhtml);
        assertNotNull(escaped);
        assertTrue(escaped.contains("&quot;"));
        assertTrue(escaped.contains("&amp;"));
        assertTrue(escaped.contains("&apos;"));
        assertTrue(escaped.contains("&lt;"));
        assertTrue(escaped.contains("&gt;"));
    }

    @Test
    public void testEscapeExtended() throws Throwable {
        CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
        String input = "\u00A9 test"; // Copyright symbol which is in extended
        String escaped = Entities.escape(input, encoder, Entities.EscapeMode.extended);
        assertNotNull(escaped);
        assertTrue(escaped.contains("&copy;"));
    }

    @Test
    public void testUnescapeNoAmpersand() throws Throwable {
        String input = "Hello World";
        assertEquals("Hello World", Entities.unescape(input));
    }

    @Test
    public void testUnescapeNamedEntity() throws Throwable {
        String input = "&lt;hello&gt;&amp;";
        assertEquals("<hello>&", Entities.unescape(input));
    }

    @Test
    public void testUnescapeDecimalEntity() throws Throwable {
        String input = "&#65;&#x42;"; // A and B
        assertEquals("AB", Entities.unescape(input));
    }

    @Test
    public void testUnescapeInvalidDecimalEntity() throws Throwable {
        String input = "&#invalid;";
        assertEquals("&#invalid;", Entities.unescape(input));
    }

    @Test
    public void testUnescapeOutOfRangeEntity() throws Throwable {
        // High value that might trigger number format or out of range conditions
        String input = "&#99999999;";
        String result = Entities.unescape(input);
        assertNotNull(result);
    }

    @Test
    public void testUnescapeHexEntity() throws Throwable {
        String input = "&#x3C;"; // <
        assertEquals("<", Entities.unescape(input));
    }

    @Test
    public void testUnescapeUppercaseHexEntity() throws Throwable {
        String input = "&#X3C;"; // <
        assertEquals("<", Entities.unescape(input));
    }
}