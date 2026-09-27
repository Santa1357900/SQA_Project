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
        assertEquals("", attributes.get("testKey"));
        assertFalse(attributes.hasKey("testKey"));

        attributes.put("testKey", "testValue");
        assertEquals("testValue", attributes.get("testKey"));
        assertTrue(attributes.hasKey("testKey"));
        assertEquals(1, attributes.size());

        // Update existing key
        attributes.put("testKey", "newValue");
        assertEquals("newValue", attributes.get("testKey"));
        assertEquals(1, attributes.size());
    }

    @Test
    public void testCaseSensitivity() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("TestKey", "value1");

        assertEquals("value1", attributes.get("TestKey"));
        assertEquals("", attributes.get("testkey"));
        assertTrue(attributes.hasKey("TestKey"));
        assertFalse(attributes.hasKey("testkey"));

        // Ignore case methods
        assertEquals("value1", attributes.getIgnoreCase("testkey"));
        assertTrue(attributes.hasKeyIgnoreCase("testkey"));
    }

    @Test
    public void testPutIgnoreCase() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("TestKey", "value1");
        attributes.putIgnoreCase("testkey", "value2");

        assertEquals(1, attributes.size());
        assertEquals("value2", attributes.get("TestKey"));
    }

    @Test
    public void testBooleanAttribute() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("hidden", true);
        assertTrue(attributes.hasKey("hidden"));
        assertEquals("", attributes.get("hidden"));

        attributes.put("hidden", false);
        assertFalse(attributes.hasKey("hidden"));
    }

    @Test
    public void testPutAttributeObject() throws Throwable {
        Attributes attributes = new Attributes();
        Attribute attr = new Attribute("href", "http://example.com");
        attributes.put(attr);

        assertEquals("http://example.com", attributes.get("href"));
        assertEquals(attributes, attr.parent());
    }

    @Test
    public void testRemove() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("key1", "val1");
        attributes.put("key2", "val2");
        attributes.put("key3", "val3");

        assertEquals(3, attributes.size());

        attributes.remove("key2");
        assertEquals(2, attributes.size());
        assertFalse(attributes.hasKey("key2"));
        assertEquals("val1", attributes.get("key1"));
        assertEquals("val3", attributes.get("key3"));

        // Remove non-existent
        attributes.remove("nonExistent");
        assertEquals(2, attributes.size());

        // Remove ignore case
        attributes.removeIgnoreCase("KEY1");
        assertEquals(1, attributes.size());
        assertFalse(attributes.hasKey("key1"));
    }

    @Test
    public void testAddAll() throws Throwable {
        Attributes source = new Attributes();
        source.put("k1", "v1");
        source.put("k2", "v2");

        Attributes target = new Attributes();
        target.put("k3", "v3");

        target.addAll(source);
        assertEquals(3, target.size());
        assertEquals("v1", target.get("k1"));
        assertEquals("v2", target.get("k2"));
        assertEquals("v3", target.get("k3"));

        // Empty incoming
        Attributes empty = new Attributes();
        target.addAll(empty);
        assertEquals(3, target.size());
    }

    @Test
    public void testIteratorAndRemove() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("k1", "v1");
        attributes.put("k2", "v2");

        Iterator<Attribute> it = attributes.iterator();
        assertTrue(it.hasNext());
        Attribute attr = it.next();
        assertEquals("k1", attr.getKey());
        assertEquals("v1", attr.getValue());

        assertTrue(it.hasNext());
        it.next();
        it.remove();

        assertEquals(1, attributes.size());
        assertFalse(attributes.hasKey("k2"));
    }

    @Test
    public void testAsList() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("k1", "v1");
        attributes.put("k2", (String) null); // boolean-like attribute representation in values

        List<Attribute> list = attributes.asList();
        assertEquals(2, list.size());
        assertEquals("k1", list.get(0).getKey());
        assertEquals("k2", list.get(1).getKey());
    }

    @Test
    public void testDataset() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("data-foo", "bar");
        attributes.put("data-", "emptyData");
        attributes.put("not-data", "ignore");

        Map<String, String> dataset = attributes.dataset();
        assertEquals(2, dataset.size());
        assertEquals("bar", dataset.get("foo"));
        assertEquals("emptyData", dataset.get(""));
        assertNull(dataset.get("not-data"));

        dataset.put("foo", "baz");
        assertEquals("baz", attributes.get("data-foo"));

        dataset.put("new", "val");
        assertEquals("val", attributes.get("data-new"));

        // Test dataset entrySet and iterator
        Set<Map.Entry<String, String>> entries = dataset.entrySet();
        assertNotNull(entries);
        
        Iterator<Map.Entry<String, String>> it = entries.iterator();
        assertTrue(it.hasNext());
        Map.Entry<String, String> entry = it.next();
        assertNotNull(entry.getKey());
        
        it.remove();
        assertFalse(attributes.hasKey("data-" + entry.getKey()));
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
        Attributes attr1 = new Attributes();
        attr1.put("a", "1");

        Attributes attr2 = new Attributes();
        attr2.put("a", "1");

        Attributes attr3 = new Attributes();
        attr3.put("a", "2");

        assertTrue(attr1.equals(attr1));
        assertTrue(attr1.equals(attr2));
        assertEquals(attr1.hashCode(), attr2.hashCode());

        assertFalse(attr1.equals(null));
        assertFalse(attr1.equals("stringObject"));
        assertFalse(attr1.equals(attr3));
    }

    @Test
    public void testClone() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("key1", "val1");

        Attributes clone = attributes.clone();
        assertEquals(attributes, clone);
        assertNotSame(attributes, clone);

        clone.put("key2", "val2");
        assertEquals(1, attributes.size());
        assertEquals(2, clone.size());
    }

    @Test
    public void testNormalize() throws Throwable {
        Attributes attributes = new Attributes();
        attributes.put("TEST-KEY", "val");
        attributes.normalize();

        assertEquals("val", attributes.get("test-key"));
        assertFalse(attributes.hasKey("TEST-KEY"));
    }

    @Test
    public void testCapacityGrowth() throws Throwable {
        Attributes attributes = new Attributes();
        // Add more than InitialCapacity (4) to trigger growth
        attributes.put("k1", "v1");
        attributes.put("k2", "v2");
        attributes.put("k3", "v3");
        attributes.put("k4", "v4");
        attributes.put("k5", "v5");
        attributes.put("k6", "v6");

        assertEquals(6, attributes.size());
        assertEquals("v6", attributes.get("k6"));
    }
}