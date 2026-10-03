package org.apache.commons.collections4.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import org.apache.commons.collections4.Factory;
import org.apache.commons.collections4.FunctorException;

public class MultiValueMapTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test
    public void testFactoryConstructorNull() throws Throwable {
        try {
            new MultiValueMap<String, String>(new HashMap<String, Object>(), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("factory must not be null"));
        }
    }

    @Test
    public void testStaticMultiValueMapWithMap() throws Throwable {
        Map<String, Collection<String>> inner = new HashMap<String, Collection<String>>();
        MultiValueMap<String, String> map = MultiValueMap.multiValueMap(inner);
        assertNotNull(map);
    }

    @Test
    public void testStaticMultiValueMapWithClass() throws Throwable {
        Map<String, Collection<String>> inner = new HashMap<String, Collection<String>>();
        MultiValueMap<String, String> map = MultiValueMap.multiValueMap(inner, ArrayList.class);
        assertNotNull(map);
    }

    @Test
    public void testStaticMultiValueMapWithFactory() throws Throwable {
        Map<String, Collection<String>> inner = new HashMap<String, Collection<String>>();
        Factory<ArrayList<String>> factory = new Factory<ArrayList<String>>() {
            public ArrayList<String> create() {
                return new ArrayList<String>();
            }
        };
        MultiValueMap<String, String> map = MultiValueMap.multiValueMap(inner, factory);
        assertNotNull(map);
    }

    @Test
    public void testPutAndGet() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        Object added = map.put("key1", "value1");
        assertEquals("value1", added);

        Object addedAgain = map.put("key1", "value2");
        assertEquals("value2", addedAgain);

        Collection<String> col = map.getCollection("key1");
        assertNotNull(col);
        assertEquals(2, col.size());
        assertTrue(col.contains("value1"));
        assertTrue(col.contains("value2"));
    }

    @Test
    public void testPutDuplicate() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("key1", "value1");
        Object added = map.put("key1", "value1");
        assertEquals("value1", added);
        assertEquals(2, map.totalSize());
    }

    @Test
    public void testContainsValue() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("key1", "val1");
        map.put("key2", "val2");

        assertTrue(map.containsValue("val1"));
        assertTrue(map.containsValue("val2"));
        assertFalse(map.containsValue("notPresent"));
        assertFalse(map.containsValue(null, "val1"));
    }

    @Test
    public void testContainsValueForKey() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("key1", "val1");

        assertTrue(map.containsValue("key1", "val1"));
        assertFalse(map.containsValue("key1", "val2"));
        assertFalse(map.containsValue("nonExistent", "val1"));
    }

    @Test
    public void testRemoveMapping() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("key1", "val1");
        map.put("key1", "val2");

        assertFalse(map.removeMapping("nonExistent", "val1"));
        assertFalse(map.removeMapping("key1", "nonExistent"));

        assertTrue(map.removeMapping("key1", "val1"));
        assertEquals(1, map.size("key1"));

        assertTrue(map.removeMapping("key1", "val2"));
        assertFalse(map.containsKey("key1"));
    }

    @Test
    public void testPutAllCollection() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        List<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");

        assertFalse(map.putAll("key1", null));
        assertFalse(map.putAll("key1", new ArrayList<String>()));

        assertTrue(map.putAll("key1", list));
        assertEquals(2, map.size("key1"));

        // Add more to existing key
        List<String> list2 = new ArrayList<String>();
        list2.add("c");
        assertTrue(map.putAll("key1", list2));
        assertEquals(3, map.size("key1"));
    }

    @Test
    public void testPutAllMapNormal() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        Map<String, String> normalMap = new HashMap<String, String>();
        normalMap.put("k1", "v1");
        normalMap.put("k2", "v2");

        map.putAll(normalMap);
        assertEquals(2, map.totalSize());
        assertEquals("v1", map.getCollection("k1").iterator().next());
    }

    @Test
    public void testPutAllMapMulti() throws Throwable {
        MultiValueMap<String, String> map1 = new MultiValueMap<String, String>();
        map1.put("k1", "v1");

        MultiValueMap<String, String> map2 = new MultiValueMap<String, String>();
        map2.putAll(map1);
        assertEquals(1, map2.totalSize());
        assertTrue(map2.containsValue("k1", "v1"));
    }

    @Test
    public void testClear() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("k1", "v1");
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.totalSize());
    }

    @Test
    public void testSizeForKey() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        assertEquals(0, map.size("missing"));
        map.put("k1", "v1");
        assertEquals(1, map.size("k1"));
    }

    @Test
    public void testTotalSize() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        assertEquals(0, map.totalSize());
        map.put("k1", "v1");
        map.put("k1", "v2");
        map.put("k2", "v3");
        assertEquals(3, map.totalSize());
    }

    @Test
    public void testIteratorForKey() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        Iterator<String> emptyIt = map.iterator("missing");
        assertNotNull(emptyIt);
        assertFalse(emptyIt.hasNext());

        map.put("k1", "v1");
        map.put("k1", "v2");
        Iterator<String> it = map.iterator("k1");
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertEquals("v1", it.next());
        assertTrue(it.hasNext());
        assertEquals("v2", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void testIteratorAllMappings() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("k1", "v1");
        map.put("k2", "v2");

        Iterator<Map.Entry<String, String>> it = map.iterator();
        assertNotNull(it);
        int count = 0;
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            assertNotNull(entry.getKey());
            assertNotNull(entry.getValue());
            try {
                entry.setValue("newValue");
                fail("Expected UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // expected
            }
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    public void testValuesView() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("k1", "v1");
        map.put("k2", "v2");

        Collection<Object> values = map.values();
        assertNotNull(values);
        assertEquals(2, values.size());

        // Test values iterator and clear view
        Iterator<Object> valIt = values.iterator();
        assertTrue(valIt.hasNext());
        assertNotNull(valIt.next());

        values.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    public void testValuesIteratorRemove() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("k1", "v1");

        Iterator<String> it = map.iterator("k1");
        assertTrue(it.hasNext());
        assertEquals("v1", it.next());
        it.remove();
        assertFalse(map.containsKey("k1"));
    }

    @Test
    public void testSerialization() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("k1", "v1");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(map);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        @SuppressWarnings("unchecked")
        MultiValueMap<String, String> deserialized = (MultiValueMap<String, String>) ois.readObject();
        ois.close();

        assertNotNull(deserialized);
        assertEquals(1, deserialized.totalSize());
        assertTrue(deserialized.containsValue("k1", "v1"));
    }

    @Test
    public void testReflectionFactoryException() throws Throwable {
        // Pass a class without a public no-arg constructor to trigger instantiation failure
        Map<String, Collection<String>> inner = new HashMap<String, Collection<String>>();
        MultiValueMap<String, String> map = MultiValueMap.multiValueMap(inner, InvalidCollection.class);
        try {
            map.put("key", "value");
            fail("Expected FunctorException");
        } catch (FunctorException e) {
            assertTrue(e.getMessage().contains("Cannot instantiate class"));
        }
    }

    public static class InvalidCollection<E> extends ArrayList<E> {
        private static final long serialVersionUID = 1L;
        public InvalidCollection(int dummy) {
            super();
        }
    }
}