package org.jsoup.nodes;

import org.junit.Test;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class AttributesTest {

    @Test
    public void testEmptyAttributes() throws Throwable {
        Attributes attrs = new Attributes();
        assertEquals(0, attrs.size());
        assertEquals("", attrs.get("test"));
        assertEquals("", attrs.getIgnoreCase("TEST"));
        assertFalse(attrs.hasKey("test"));
        assertFalse(attrs.hasKeyIgnoreCase("TEST"));
        assertEquals("", attrs.html());
        assertEquals("", attrs.toString());
        assertEquals(0, attrs.hashCode());
        
        Iterator<Attribute> it = attrs.iterator();
        assertFalse(it.hasNext());
        
        List<Attribute> list = attrs.asList();
        assertTrue(list.isEmpty());
    }

    @Test
    public void testPutAndGet() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("id", "myId");
        attrs.put("class", "myClass");

        assertEquals(2, attrs.size());
        assertEquals("myId", attrs.get("id"));
        assertEquals("myClass", attrs.get("class"));
        assertEquals("", attrs.get("nonexistent"));
        
        assertTrue(attrs.hasKey("id"));
        assertFalse(attrs.hasKey("ID"));
    }

    @Test
    public void testGetIgnoreCase() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("Id", "Value1");

        assertEquals("Value1", attrs.getIgnoreCase("id"));
        assertEquals("Value1", attrs.getIgnoreCase("ID"));
        assertEquals("Value1", attrs.getIgnoreCase("Id"));
        assertEquals("", attrs.getIgnoreCase("other"));
    }

    @Test
    public void testPutBooleanAttribute() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("checked", true);
        attrs.put("hidden", false);

        assertTrue(attrs.hasKey("checked"));
        assertFalse(attrs.hasKey("hidden"));
        assertEquals(1, attrs.size());
    }

    @Test
    public void testPutAttributeObject() throws Throwable {
        Attributes attrs = new Attributes();
        Attribute attr = new Attribute("data-test", "val");
        attrs.put(attr);

        assertEquals(1, attrs.size());
        assertEquals("val", attrs.get("data-test"));
    }

    @Test
    public void testRemove() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("key1", "val1");
        attrs.put("key2", "val2");

        attrs.remove("key1");
        assertEquals(1, attrs.size());
        assertFalse(attrs.hasKey("key1"));
        assertTrue(attrs.hasKey("key2"));

        // Removing non-existent or on empty
        Attributes emptyAttrs = new Attributes();
        emptyAttrs.remove("key1");
        assertEquals(0, emptyAttrs.size());
    }

    @Test
    public void testRemoveIgnoreCase() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("KeyOne", "val1");
        attrs.put("KeyTwo", "val2");

        attrs.removeIgnoreCase("keyone");
        assertEquals(1, attrs.size());
        assertFalse(attrs.hasKey("KeyOne"));
        assertTrue(attrs.hasKey("KeyTwo"));

        Attributes emptyAttrs = new Attributes();
        emptyAttrs.removeIgnoreCase("key");
        assertEquals(0, emptyAttrs.size());
    }

    @Test
    public void testHasKeyIgnoreCase() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("Foo", "bar");

        assertTrue(attrs.hasKeyIgnoreCase("foo"));
        assertTrue(attrs.hasKeyIgnoreCase("FOO"));
        assertTrue(attrs.hasKeyIgnoreCase("Foo"));
        assertFalse(attrs.hasKeyIgnoreCase("bar"));

        Attributes emptyAttrs = new Attributes();
        assertFalse(emptyAttrs.hasKeyIgnoreCase("foo"));
    }

    @Test
    public void testAddAll() throws Throwable {
        Attributes source = new Attributes();
        source.put("a", "1");
        source.put("b", "2");

        Attributes target = new Attributes();
        target.put("c", "3");
        target.addAll(source);

        assertEquals(3, target.size());
        assertEquals("1", target.get("a"));
        assertEquals("2", target.get("b"));
        assertEquals("3", target.get("c"));

        // Add empty incoming
        Attributes empty = new Attributes();
        int sizeBefore = target.size();
        target.addAll(empty);
        assertEquals(sizeBefore, target.size());
    }

    @Test
    public void testIteratorAndAsList() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("one", "1");
        attrs.put("two", "2");

        Iterator<Attribute> it = attrs.iterator();
        assertTrue(it.hasNext());
        assertEquals("one", it.next().getKey());
        assertTrue(it.hasNext());
        assertEquals("two", it.next().getKey());
        assertFalse(it.hasNext());

        List<Attribute> list = attrs.asList();
        assertEquals(2, list.size());
        assertEquals("one", list.get(0).getKey());
        assertEquals("2", list.get(1).getValue());
    }

    @Test
    public void testHtmlAndToString() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("href", "http://example.com");
        attrs.put("class", "test");

        String html = attrs.html();
        assertTrue(html.contains("href=\"http://example.com\""));
        assertTrue(html.contains("class=\"test\""));
        assertEquals(html, attrs.toString());

        Attributes empty = new Attributes();
        assertEquals("", empty.html());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Attributes attrs1 = new Attributes();
        attrs1.put("a", "1");

        Attributes attrs2 = new Attributes();
        attrs2.put("a", "1");

        Attributes attrs3 = new Attributes();
        attrs3.put("b", "2");

        Attributes attrsEmpty1 = new Attributes();
        Attributes attrsEmpty2 = new Attributes();

        assertTrue(attrs1.equals(attrs1));
        assertTrue(attrs1.equals(attrs2));
        assertFalse(attrs1.equals(attrs3));
        assertFalse(attrs1.equals(null));
        assertFalse(attrs1.equals("not attributes"));
        
        assertTrue(attrsEmpty1.equals(attrsEmpty2));
        assertFalse(attrs1.equals(attrsEmpty1));
        assertFalse(attrsEmpty1.equals(attrs1));

        assertEquals(attrs1.hashCode(), attrs2.hashCode());
        assertEquals(0, attrsEmpty1.hashCode());
    }

    @Test
    public void testClone() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("key", "value");

        Attributes clone = attrs.clone();
        assertNotSame(attrs, clone);
        assertEquals(attrs, clone);
        assertEquals("value", clone.get("key"));

        // Test clone with null attributes
        Attributes empty = new Attributes();
        Attributes emptyClone = empty.clone();
        assertNotNull(emptyClone);
        assertEquals(0, emptyClone.size());
    }

    @Test
    public void testDataset() throws Throwable {
        Attributes attrs = new Attributes();
        attrs.put("data-id", "123");
        attrs.put("data-name", "jsoup");
        attrs.put("class", "content");

        Map<String, String> dataset = attrs.dataset();
        assertEquals(2, dataset.size());
        assertEquals("123", dataset.get("id"));
        assertEquals("jsoup", dataset.get("name"));

        // Test dataset put
        dataset.put("value", "456");
        assertEquals("456", attrs.get("data-value"));
        assertEquals("456", dataset.get("value"));

        // Test dataset iteration and removal via entrySet / iterator
        Set<Map.Entry<String, String>> entrySet = dataset.entrySet();
        assertEquals(3, entrySet.size());

        Iterator<Map.Entry<String, String>> iter = entrySet.iterator();
        boolean foundId = false;
        while (iter.hasNext()) {
            Map.Entry<String, String> entry = iter.next();
            if ("id".equals(entry.getKey())) {
                foundId = true;
                iter.remove();
            }
        }
        assertTrue(foundId);
        assertFalse(attrs.hasKey("data-id"));
    }

    @Test
    public void testDatasetInitializationWithNull() throws Throwable {
        Attributes attrs = new Attributes();
        // Force dataset access when attributes is null
        Map<String, String> dataset = attrs.dataset();
        assertNotNull(dataset);
        assertEquals(0, dataset.size());
    }
}