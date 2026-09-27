package org.apache.commons.lang;

import junit.framework.TestCase;
import java.io.StringWriter;
import java.io.IOException;
import java.io.Writer;

public class Entities_Test extends TestCase {

    public void testConstantsAndFill() throws Throwable {
        assertNotNull(Entities.XML);
        assertNotNull(Entities.HTML32);
        assertNotNull(Entities.HTML40);
        
        Entities customEntities = new Entities();
        Entities.fillWithHtml40Entities(customEntities);
        assertEquals(38, customEntities.entityValue("amp"));
    }

    public void testEntityMaps() throws Throwable {
        Entities.PrimitiveEntityMap primMap = new Entities.PrimitiveEntityMap();
        primMap.add("test", 123);
        assertEquals(123, primMap.value("test"));
        assertEquals("test", primMap.name(123));
        assertEquals(-1, primMap.value("nonexistent"));
        assertNull(primMap.name(9999));

        Entities.HashEntityMap hashMap = new Entities.HashEntityMap();
        hashMap.add("hash", 456);
        assertEquals(456, hashMap.value("hash"));
        assertEquals("hash", hashMap.name(456));
        assertEquals(-1, hashMap.value("none"));
        assertNull(hashMap.name(9999));

        Entities.TreeEntityMap treeMap = new Entities.TreeEntityMap();
        treeMap.add("tree", 789);
        assertEquals(789, treeMap.value("tree"));
        assertEquals("tree", treeMap.name(789));
        assertEquals(-1, treeMap.value("none"));
        assertNull(treeMap.name(9999));

        Entities.LookupEntityMap lookupMap = new Entities.LookupEntityMap();
        lookupMap.add("lookup", 65);
        assertEquals(65, lookupMap.value("lookup"));
        assertEquals("lookup", lookupMap.name(65));
        assertEquals("nbsp", lookupMap.name(160)); // Testing table lookup < 256
        assertEquals(-1, lookupMap.value("none"));

        Entities.ArrayEntityMap arrayMap = new Entities.ArrayEntityMap(2);
        arrayMap.add("arr1", 1);
        arrayMap.add("arr2", 2);
        assertEquals(1, arrayMap.value("arr1"));
        assertEquals("arr1", arrayMap.name(1));
        assertEquals(-1, arrayMap.value("missing"));
        assertNull(arrayMap.name(9999));

        Entities.BinaryEntityMap binMap = new Entities.BinaryEntityMap(2);
        binMap.add("bin1", 10);
        binMap.add("bin2", 20);
        binMap.add("bin1", 10); // Duplicate test
        assertEquals(10, binMap.value("bin1"));
        assertEquals("bin1", binMap.name(10));
        assertEquals(20, binMap.value("bin2"));
        assertEquals("bin2", binMap.name(20));
        assertNull(binMap.name(9999));
    }

    public void testAddEntitiesAndMethods() throws Throwable {
        Entities entities = new Entities();
        String[][] customArray = {{"foo", "111"}, {"bar", "222"}};
        entities.addEntities(customArray);
        assertEquals(111, entities.entityValue("foo"));
        assertEquals("bar", entities.entityName(222));
    }

    public void testEscape() throws Throwable {
        Entities entities = new Entities();
        entities.addEntity("quot", 34);
        
        String escaped = entities.escape("A \"quote\" and high char \u00C0");
        assertTrue(escaped.contains("&quot;"));
        assertTrue(escaped.contains("&#192;"));

        StringWriter writer = new StringWriter();
        entities.escape(writer, "A \"quote\"");
        assertTrue(writer.toString().contains("&quot;"));
    }

    public void testUnescape() throws Throwable {
        Entities entities = new Entities();
        entities.addEntity("foo", 161);

        assertEquals("NoAmpersand", entities.unescape("NoAmpersand"));
        assertEquals("\u00A1", entities.unescape("&foo;"));
        assertEquals("&#192;", entities.unescape("&#192;"));
        assertEquals("&#x00C0;", entities.unescape("&#x00C0;"));
        assertEquals("&#X00C0;", entities.unescape("&#X00C0;"));
        assertEquals("&invalid;", entities.unescape("&invalid;"));
        assertEquals("&amp&foo;", entities.unescape("&amp&foo;")); // malformed amp order
        assertEquals("&;", entities.unescape("&;"));
        assertEquals("&#;", entities.unescape("&#;"));
        assertEquals("&#Z;", entities.unescape("&#Z;")); // NumberFormatException branch
        assertEquals("&#70000;", entities.unescape("&#70000;")); // > 0xFFFF branch
        assertEquals("&incomplete", entities.unescape("&incomplete"));

        StringWriter writer = new StringWriter();
        entities.unescape(writer, "Plain text");
        assertEquals("Plain text", writer.toString());

        StringWriter writer2 = new StringWriter();
        entities.unescape(writer2, "Test &foo;");
        assertEquals("Test \u00A1", writer2.toString());
    }

    public void testIOExceptionHandling() throws Throwable {
        Entities entities = new Entities();
        Writer faultyWriter = new Writer() {
            public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("Simulated IO Exception");
            }
            public void flush() throws IOException {}
            public void close() throws IOException {}
        };

        try {
            entities.escape(faultyWriter, "test");
            fail("Expected RuntimeException / UnhandledException");
        } catch (RuntimeException e) {
            assertTrue(true);
        }

        try {
            entities.unescape(faultyWriter, "test &amp;");
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }
    }
}