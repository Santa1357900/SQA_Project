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
        assertFalse(Entities.isNamedEntity("nonexistententityname"));
        assertFalse(Entities.isNamedEntity(null));
        assertFalse(Entities.isNamedEntity(""));
    }

    @Test
    public void testIsBaseNamedEntity() throws Throwable {
        assertTrue(Entities.isBaseNamedEntity("amp"));
        assertTrue(Entities.isBaseNamedEntity("lt"));
        assertTrue(Entities.isBaseNamedEntity("gt"));
        assertTrue(Entities.isBaseNamedEntity("quot"));
        assertFalse(Entities.isBaseNamedEntity("nonexistententityname"));
        assertFalse(Entities.isBaseNamedEntity(null));
        assertFalse(Entities.isBaseNamedEntity(""));
    }

    @Test
    public void testGetCharacterByName() throws Throwable {
        assertEquals(Character.valueOf('&'), Entities.getCharacterByName("amp"));
        assertEquals(Character.valueOf('<'), Entities.getCharacterByName("lt"));
        assertEquals(Character.valueOf('>'), Entities.getCharacterByName("gt"));
        assertEquals(Character.valueOf('"'), Entities.getCharacterByName("quot"));
        assertNull(Entities.getCharacterByName("nonexistententityname"));
        assertNull(Entities.getCharacterByName(null));
    }

    @Test
    public void testEscapeModeEnum() throws Throwable {
        for (Entities.EscapeMode mode : Entities.EscapeMode.values()) {
            Map<Character, String> map = mode.getMap();
            assertNotNull(map);
        }
    }

    @Test
    public void testUnescape() throws Throwable {
        String original = "&amp;&lt;&gt;&quot;";
        String unescaped = Entities.unescape(original);
        assertEquals("&<>\"", unescaped);

        String strictUnescaped = Entities.unescape("&amp;", true);
        assertEquals("&", strictUnescaped);

        assertNull(Entities.unescape(null));
        assertEquals("", Entities.unescape(""));
    }

    @Test
    public void testEscapeBasic() throws Throwable {
        Document doc = new Document("");
        doc.outputSettings().escapeMode(Entities.EscapeMode.base);

        String input = "Hello & < > \" ' \u00A0";
        String escaped = Entities.escape(input, doc.outputSettings());
        assertNotNull(escaped);
        assertTrue(escaped.contains("&amp;"));
        assertTrue(escaped.contains("&lt;"));
        assertTrue(escaped.contains("&gt;"));
    }

    @Test
    public void testEscapeXhtml() throws Throwable {
        Document doc = new Document("");
        doc.outputSettings().escapeMode(Entities.EscapeMode.xhtml);

        String input = "< > \" & \u00A0";
        String escaped = Entities.escape(input, doc.outputSettings());
        assertNotNull(escaped);
        assertTrue(escaped.contains("&#xa0;"));
    }

    @Test
    public void testEscapeInAttribute() throws Throwable {
        Document doc = new Document("");
        StringBuilder accum = new StringBuilder();
        Entities.escape(accum, "<>\"&", doc.outputSettings(), true, false, false);
        String result = accum.toString();
        assertTrue(result.contains("&quot;"));
    }

    @Test
    public void testEscapeNormaliseWhite() throws Throwable {
        Document doc = new Document("");
        StringBuilder accum = new StringBuilder();
        Entities.escape(accum, "   hello   world   ", doc.outputSettings(), false, true, true);
        String result = accum.toString();
        assertEquals("hello world ", result);
    }

    @Test
    public void testSupplementaryCharacters() throws Throwable {
        Document doc = new Document("");
        // Supplementary character: Emoji 💩 (U+1F4A9)
        String input = "\uD83D\uDCA9";
        String escaped = Entities.escape(input, doc.outputSettings());
        assertNotNull(escaped);
    }
}