package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;

public class EntitiesTest {

    @Test
    public void testIsNamedEntity() throws Throwable {
        assertTrue(Entities.isNamedEntity("amp"));
        assertTrue(Entities.isNamedEntity("lt"));
        assertTrue(Entities.isNamedEntity("gt"));
        assertTrue(Entities.isNamedEntity("quot"));
        assertFalse(Entities.isNamedEntity("notanentityatall"));
        assertFalse(Entities.isNamedEntity(null));
    }

    @Test
    public void testIsBaseNamedEntity() throws Throwable {
        assertTrue(Entities.isBaseNamedEntity("amp"));
        assertTrue(Entities.isBaseNamedEntity("lt"));
        assertFalse(Entities.isBaseNamedEntity("notanentityatall"));
        assertFalse(Entities.isBaseNamedEntity(null));
    }

    @Test
    public void testGetCharacterByName() throws Throwable {
        assertEquals(Character.valueOf('&'), Entities.getCharacterByName("amp"));
        assertEquals(Character.valueOf('<'), Entities.getCharacterByName("lt"));
        assertEquals(Character.valueOf('>'), Entities.getCharacterByName("gt"));
        assertNull(Entities.getCharacterByName("nonexistent"));
        assertNull(Entities.getCharacterByName(null));
    }

    @Test
    public void testEscapeModeGetMap() throws Throwable {
        Map<Character, String> xhtmlMap = Entities.EscapeMode.xhtml.getMap();
        assertNotNull(xhtmlMap);
        assertTrue(xhtmlMap.containsKey('&'));

        Map<Character, String> baseMap = Entities.EscapeMode.base.getMap();
        assertNotNull(baseMap);

        Map<Character, String> extendedMap = Entities.EscapeMode.extended.getMap();
        assertNotNull(extendedMap);
    }

    @Test
    public void testEscapeBasic() throws Throwable {
        Document doc = new Document("");
        doc.outputSettings().escapeMode(Entities.EscapeMode.extended);

        String escaped = Entities.escape("Hello & < > \" '", doc.outputSettings());
        assertNotNull(escaped);
        assertTrue(escaped.contains("&amp;"));
        assertTrue(escaped.contains("&lt;"));
        assertTrue(escaped.contains("&gt;"));
    }

    @Test
    public void testEscapeXhtmlMode() throws Throwable {
        Document doc = new Document("");
        doc.outputSettings().escapeMode(Entities.EscapeMode.xhtml);

        String escaped = Entities.escape("< > & \"", doc.outputSettings());
        assertTrue(escaped.contains("&amp;"));
        assertTrue(escaped.contains("&lt;"));
        assertTrue(escaped.contains("&gt;"));
    }

    @Test
    public void testEscapeNonBreakingSpace() throws Throwable {
        Document doc = new Document("");
        doc.outputSettings().escapeMode(Entities.EscapeMode.extended);

        String input = "\u00A0";
        String escaped = Entities.escape(input, doc.outputSettings());
        assertEquals("&nbsp;", escaped);

        doc.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
        String escapedXhtml = Entities.escape(input, doc.outputSettings());
        assertEquals("\u00A0", escapedXhtml);
    }

    @Test
    public void testEscapeInAttribute() throws Throwable {
        Document doc = new Document("");
        doc.outputSettings().escapeMode(Entities.EscapeMode.extended);

        StringBuilder accum = new StringBuilder();
        Entities.escape(accum, "< > \" &", doc.outputSettings(), true, false, false);
        String result = accum.toString();
        
        assertTrue(result.contains("&quot;"));
        assertTrue(result.contains("&amp;"));
        assertTrue(result.contains("<"));
        assertTrue(result.contains(">"));
    }

    @Test
    public void testEscapeNormaliseWhite() throws Throwable {
        Document doc = new Document("");
        doc.outputSettings().escapeMode(Entities.EscapeMode.extended);

        StringBuilder accum = new StringBuilder();
        Entities.escape(accum, "   hello   world   ", doc.outputSettings(), false, true, true);
        assertEquals("hello world ", accum.toString());

        StringBuilder accum2 = new StringBuilder();
        Entities.escape(accum2, "   hello   world   ", doc.outputSettings(), false, true, false);
        assertEquals(" hello world ", accum2.toString());
    }

    @Test
    public void testEscapeSupplementaryCharacters() throws Throwable {
        Document doc = new Document("");
        doc.outputSettings().escapeMode(Entities.EscapeMode.extended);

        // Supplementary character (e.g., Emoji or high unicode point)
        String supplementary = new String(Character.toChars(0x1F600));
        String escaped = Entities.escape(supplementary, doc.outputSettings());
        assertNotNull(escaped);
    }

    @Test
    public void testUnescape() throws Throwable {
        String unescaped = Entities.unescape("&amp; &lt; &gt;");
        assertEquals("& < >", unescaped);

        String unescapedStrict = Entities.unescape("&amp", true);
        assertEquals("&amp", unescapedStrict);
    }
}