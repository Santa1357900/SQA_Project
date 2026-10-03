package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AttributesTest {

    @Test
    public void testPutAndGet() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("testKey", "testValue");
        
        assertEquals("testValue", attributes.get("testKey"));
        assertEquals("", attributes.get("nonExistent"));
        assertTrue(attributes.hasKey("testKey"));
        assertFalse(attributes.hasKey("nonExistent"));
        assertEquals(1, attributes.size());
    }

    @Test
    public void testCaseSensitivity() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("TestKey", "value1");
        
        assertEquals("value1", attributes.get("TestKey"));
        assertEquals("", attributes.get("testkey"));
        assertEquals("value1", attributes.getIgnoreCase("testkey"));
        assertTrue(attributes.hasKeyIgnoreCase("TESTKEY"));
    }

    @Test
    public void testPutIgnoreCase() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.putIgnoreCase("Key", "val1");
        assertEquals("val1", attributes.get("Key"));

        attributes.putIgnoreCase("KEY", "val2");
        assertEquals(1, attributes.size());
        assertEquals("val2", attributes.get("KEY"));
    }

    @Test
    public void testBooleanAttribute() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("hidden", true);
        
        assertEquals("", attributes.get("hidden"));
        assertTrue(attributes.hasKey("hidden"));

        attributes.put("hidden", false);
        assertFalse(attributes.hasKey("hidden"));
    }

    @Test
    public void testPutAttributeObject() throws Throwable {
        Attributes attributes = new Attributes();
        Attribute attr = new Attribute("href", "http://example.com");
        attributes.put(attr);

        assertEquals("http://example.com", attributes.get("href"));
        assertEquals(attributes, attr.parent);
    }

    @Test
    public void testRemove() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("key1", "val1");
        attributes.put("key2", "val2");
        attributes.put("key3", "val3");

        attributes.remove("key2");
        assertEquals(2, attributes.size());
        assertFalse(attributes.hasKey("key2"));
        assertEquals("val1", attributes.get("key1"));
        assertEquals("val3", attributes.get("key3"));

        // Remove non-existent
        attributes.remove("nonexistent");
        assertEquals(2, attributes.size());
    }

    @Test
    public void testRemoveIgnoreCase() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("KeyOne", "Val");
        attributes.removeIgnoreCase("keyone");
        assertEquals(0, attributes.size());
        assertFalse(attributes.hasKey("KeyOne"));
    }

    @Test
    public void testAddAll() throws Throwable {
        Attributes src = new Attributes();
        src.put("a", "1");
        src.put("b", "2");

        Attributes dest = new Attributes();
        dest.put("c", "3");
        dest.addAll(src);

        assertEquals(3, dest.size());
        assertEquals("1", dest.get("a"));
        assertEquals("2", dest.get("b"));
        assertEquals("3", dest.get("c"));

        // Add empty
        Attributes empty = new Attributes();
        dest.addAll(empty);
        assertEquals(3, dest.size());
    }

    @Test
    public void testIteratorAndRemove() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("a", "1");
        attributes.put("b", "2");

        Iterator<Attribute> it = attributes.iterator();
        assertTrue(it.hasNext());
        Attribute attr = it.next();
        assertEquals("a", attr.getKey());
        
        it.remove();
        assertEquals(1, attributes.size());
        assertFalse(attributes.hasKey("a"));
        assertTrue(attributes.hasKey("b"));
    }

    @Test
    public void testAsList() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("a", "1");
        attributes.put("b", null); // Boolean attribute representation

        List<Attribute> list = attributes.asList();
        assertEquals(2, list.size());
        assertEquals("a", list.get(0).getKey());
        assertEquals("1", list.get(0).getValue());
        assertEquals("b", list.get(1).getKey());
    }

    @Test
    public void testDataset() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("data-id", "123");
        attributes.put("class", "box");
        attributes.put("data-name", "foo");

        Map<String, String> dataset = attributes.dataset();
        assertEquals(2, dataset.size());
        assertEquals("123", dataset.get("id"));
        assertEquals("foo", dataset.get("name"));

        dataset.put("id", "456");
        assertEquals("456", attributes.get("data-id"));

        // Test Dataset entrySet and iterator removal
        Set<Map.Entry<String, String>> entrySet = dataset.entrySet();
        assertNotNull(entrySet);
        
        Iterator<Map.Entry<String, String>> it = dataset.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            if ("name".equals(entry.getKey())) {
                it.remove();
            }
        }
        assertFalse(attributes.hasKey("data-name"));
    }

    @Test
    public void testHtmlAndToString() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("id", "myId");
        attributes.put("class", "myClass");

        String html = attributes.html();
        assertTrue(html.contains("id=\"myId\""));
        assertTrue(html.contains("class=\"myClass\""));
        assertEquals(html, attributes.toString());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Attributes a1 = new Attributes();
        a1.put("k", "v");

        Attributes a2 = new Attributes();
        a2.put("k", "v");

        Attributes a3 = new Attributes();
        a3.put("k", "diff");

        assertEquals(a1, a1);
        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertFalse(a1.equals(null));
        assertFalse(a1.equals("string"));
        assertFalse(a1.equals(a3));
    }

    @Test
    public void testClone() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("key", "val");

        Attributes clone = attributes.clone();
        assertEquals(attributes, clone);
        assertNotSame(attributes, clone);

        clone.put("key", "newVal");
        assertEquals("val", attributes.get("key"));
        assertEquals("newVal", clone.get("key"));
    }

    @Test
    public void testNormalize() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("TEST-KEY", "val");
        attributes.normalize();

        assertTrue(attributes.hasKey("test-key"));
        assertFalse(attributes.hasKey("TEST-KEY"));
        assertEquals("val", attributes.get("test-key"));
    }

    @Test
    public void testCapacityGrowth() throws Throwable {
        Attributes attributes = new Attributes();
        // Force capacity growth beyond InitialCapacity (4)
        for (int i = 0; i < 10; i++) {
            attributes.put("key" + i, "val" + i);
        }
        assertEquals(10, attributes.size());
        assertEquals("val9", attributes.get("key9"));
    }
}