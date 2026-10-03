package org.apache.commons.lang;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;

public class EntitiesTest {

    @Test
    public void testConstantsAndFill() throws Throwable {
        assertNotNull(Entities.XML);
        assertNotNull(Entities.HTML32);
        assertNotNull(Entities.HTML40);

        Entities entities = new Entities();
        Entities.fillWithHtml40Entities(entities);
        assertEquals(34, entities.entityValue("quot"));
        assertEquals(39, entities.entityValue("apos"));
        assertEquals(160, entities.entityValue("nbsp"));
        assertEquals(402, entities.entityValue("fnof"));
    }

    @Test
    public void testPrimitiveEntityMap() throws Throwable {
        Entities.PrimitiveEntityMap map = new Entities.PrimitiveEntityMap();
        map.add("test", 123);
        assertEquals(123, map.value("test"));
        assertEquals("test", map.name(123));
        assertEquals(-1, map.value("unknown"));
        assertNull(map.name(999));
    }

    @Test
    public void testHashEntityMap() throws Throwable {
        Entities.HashEntityMap map = new Entities.HashEntityMap();
        map.add("hash", 456);
        assertEquals(456, map.value("hash"));
        assertEquals("hash", map.name(456));
        assertEquals(-1, map.value("unknown"));
        assertNull(map.name(999));
    }

    @Test
    public void testTreeEntityMap() throws Throwable {
        Entities.TreeEntityMap map = new Entities.TreeEntityMap();
        map.add("tree", 789);
        assertEquals(789, map.value("tree"));
        assertEquals("tree", map.name(789));
        assertEquals(-1, map.value("unknown"));
        assertNull(map.name(999));
    }

    @Test
    public void testLookupEntityMap() throws Throwable {
        Entities.LookupEntityMap map = new Entities.LookupEntityMap();
        map.add("a", 97);
        map.add("high", 300);
        assertEquals(97, map.value("a"));
        assertEquals(300, map.value("high"));
        assertEquals("a", map.name(97));
        assertEquals("high", map.name(300));
        assertNull(map.name(50));
    }

    @Test
    public void testArrayEntityMap() throws Throwable {
        Entities.ArrayEntityMap map = new Entities.ArrayEntityMap(2);
        map.add("one", 1);
        map.add("two", 2);
        map.add("three", 3); // triggers ensureCapacity
        assertEquals(1, map.value("one"));
        assertEquals(2, map.value("two"));
        assertEquals(3, map.value("three"));
        assertEquals(-1, map.value("four"));
        assertEquals("one", map.name(1));
        assertNull(map.name(99));
    }

    @Test
    public void testBinaryEntityMap() throws Throwable {
        Entities.BinaryEntityMap map = new Entities.BinaryEntityMap(2);
        map.add("b", 2);
        map.add("a", 1);
        map.add("c", 3);
        map.add("b", 2); // duplicate test
        assertEquals(1, map.value("a"));
        assertEquals(2, map.value("b"));
        assertEquals(3, map.value("c"));
        assertEquals("b", map.name(2));
        assertNull(map.name(99));
    }

    @Test
    public void testAddEntities() throws Throwable {
        Entities entities = new Entities();
        String[][] custom = {{"foo", "111"}, {"bar", "222"}};
        entities.addEntities(custom);
        assertEquals(111, entities.entityValue("foo"));
        assertEquals("foo", entities.entityName(111));
    }

    @Test
    public void testEscapeAndUnescapeString() throws Throwable {
        Entities entities = new Entities();
        entities.addEntity("quot", 34);

        String escaped = entities.escape("\"Hello\"");
        assertEquals("&quot;Hello&quot;", escaped);

        String highCharEscaped = entities.escape("\u00C0");
        assertEquals("&#192;", highCharEscaped);

        String normalChar = entities.escape("abc");
        assertEquals("abc", normalChar);

        String unescaped = entities.unescape("&quot;Hello&quot; &#192; normal &unknown; &; &#; &#xZ;");
        assertEquals("\"Hello\u00C0 normal &unknown; &; &#; &#xZ;", unescaped);

        String noAmp = entities.unescape("no amp here");
        assertEquals("no amp here", noAmp);

        String hexUnescape = entities.unescape("&#x41;&#X42;");
        assertEquals("AB", unescapeHelper("&#x41;&#X42;")); // verify via helper or direct
    }
    
    private String unescapeHelper(String str) {
        Entities entities = new Entities();
        return entities.unescape(str);
    }

    @Test
    public void testUnescapeEdgeCases() throws Throwable {
        Entities entities = new Entities();
        // Unclosed semi
        assertEquals("&abc", entities.unescape("&abc"));
        // Ampersand before semi
        assertEquals("&a&b;", entities.unescape("&a&b;"));
        // Empty entity name
        assertEquals("&#;", entities.unescape("&#;"));
        // Just hash
        assertEquals("&#", entities.unescape("&#"));
        // Number format exception in unescape
        assertEquals("&#abc;", entities.unescape("&#abc;"));
        // Hex with bad format falling through switch default
        assertEquals("&#xZZ;", entities.unescape("&#xZZ;"));
    }

    @Test
    public void testEscapeWriter() throws Throwable {
        Entities entities = new Entities();
        entities.addEntity("amp", 38);

        StringWriter writer = new StringWriter();
        entities.escape(writer, "A & B \u0080");
        assertEquals("A &amp; B &#128;", writer.toString());
    }

    @Test
    public void testUnescapeWriter() throws Throwable {
        Entities entities = new Entities();
        entities.addEntity("amp", 38);

        // No amp case
        StringWriter writer1 = new StringWriter();
        entities.unescape(writer1, "plain text");
        assertEquals("plain text", writer1.toString());

        // Normal unescape with writer
        StringWriter writer2 = new StringWriter();
        entities.unescape(writer2, "&amp; &#65; &#x42; &unknown; &a&b; &; &#; &#xZ;");
        assertEquals("& A B &unknown; &a&b; &; &#; &#xZ;", writer2.toString());
    }
}