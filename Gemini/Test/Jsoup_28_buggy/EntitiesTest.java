package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

public class EntitiesTest {

    @Test
    public void testIsNamedEntity() throws Throwable {
        assertTrue(Entities.isNamedEntity("amp"));
        assertTrue(Entities.isNamedEntity("lt"));
        assertTrue(Entities.isNamedEntity("gt"));
        assertFalse(Entities.isNamedEntity("nonexistententityname12345"));
        assertFalse(Entities.isNamedEntity(null));
        assertFalse(Entities.isNamedEntity(""));
    }

    @Test
    public void testGetCharacterByName() throws Throwable {
        assertEquals(Character.valueOf('&'), Entities.getCharacterByName("amp"));
        assertEquals(Character.valueOf('<'), Entities.getCharacterByName("lt"));
        assertEquals(Character.valueOf('>'), Entities.getCharacterByName("gt"));
        assertNull(Entities.getCharacterByName("nonexistententityname12345"));
        assertNull(Entities.getCharacterByName(null));
    }

    @Test
    public void testEscapeModes() throws Throwable {
        Entities.EscapeMode xhtml = Entities.EscapeMode.xhtml;
        Entities.EscapeMode base = Entities.EscapeMode.base;
        Entities.EscapeMode extended = Entities.EscapeMode.extended;

        assertNotNull(xhtml.getMap());
        assertNotNull(base.getMap());
        assertNotNull(extended.getMap());
    }

    @Test
    public void testEscapeWithEncoderAndMode() throws Throwable {
        CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
        
        String input = "Hello & < > \" ' ©";
        String escapedXhtml = Entities.escape(input, encoder, Entities.EscapeMode.xhtml);
        assertNotNull(escapedXhtml);
        assertTrue(escapedXhtml.contains("&amp;"));
        assertTrue(escapedXhtml.contains("&lt;"));
        assertTrue(escapedXhtml.contains("&gt;"));

        String escapedBase = Entities.escape(input, encoder, Entities.EscapeMode.base);
        assertNotNull(escapedBase);

        String escapedExtended = Entities.escape(input, encoder, Entities.EscapeMode.extended);
        assertNotNull(escapedExtended);
    }

    @Test
    public void testEscapeWithDocumentOutputSettings() throws Throwable {
        Document doc = new Document("");
        Document.OutputSettings out = doc.outputSettings();
        
        String input = "Test & < >";
        String result = Entities.escape(input, out);
        assertNotNull(result);
        assertTrue(result.contains("&amp;"));
        assertTrue(result.contains("&lt;"));
        assertTrue(result.contains("&gt;"));
    }

    @Test
    public void testUnescapeBasic() throws Throwable {
        String input = "Hello &amp; &lt; &gt;";
        String expected = "Hello & < >";
        assertEquals(expected, Entities.unescape(input));
    }

    @Test
    public void testUnescapeWithoutAmpersand() throws Throwable {
        String input = "Hello World";
        assertEquals(input, Entities.unescape(input));
        assertEquals(input, Entities.unescape(input, true));
    }

    @Test
    public void testUnescapeNumericEntities() throws Throwable {
        String decimal = "&#38;"; // &
        assertEquals("&", Entities.unescape(decimal));

        String hex = "&#x26;"; // &
        assertEquals("&", Entities.unescape(hex));

        String hexUppercase = "&#X26;"; // &
        assertEquals("&", Entities.unescape(hexUppercase));
    }

    @Test
    public void testUnescapeStrict() throws Throwable {
        String strictInput = "Hello &amp;";
        assertEquals("Hello &", Entities.unescape(strictInput, true));

        // Missing semicolon in strict mode should not unescape
        String looseInput = "Hello &amp";
        assertEquals("Hello &amp", Entities.unescape(looseInput, true));
        // In non-strict mode, it might unescape depending on pattern
        assertEquals("Hello &", Entities.unescape(looseInput, false));
    }

    @Test
    public void testUnescapeInvalidNumberFormat() throws Throwable {
        String invalidNum = "&#999999999999;";
        String result = Entities.unescape(invalidNum);
        assertEquals(invalidNum, result);

        String invalidHex = "&#xZZZ;";
        assertEquals(invalidHex, Entities.unescape(invalidHex));
    }

    @Test
    public void testUnescapeEdgeCases() throws Throwable {
        assertEquals("", Entities.unescape(""));
        assertEquals("&", Entities.unescape("&"));
        assertEquals("&#;", Entities.unescape("&#;"));
        assertEquals("&#x;", Entities.unescape("&#x;"));
    }
}