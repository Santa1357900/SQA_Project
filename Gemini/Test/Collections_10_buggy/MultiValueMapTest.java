package org.apache.commons.collections.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.commons.collections.Factory;
import org.apache.commons.collections.FunctorException;

public class MultiValueMapTest extends TestCase {

    public void testDecorateMap() throws Throwable {
        HashMap<String, String> baseMap = new HashMap<String, String>();
        MultiValueMap map = MultiValueMap.decorate(baseMap);
        assertNotNull(map);

        MultiValueMap mapWithClass = MultiValueMap.decorate(baseMap, ArrayList.class);
        assertNotNull(mapWithClass);

        MultiValueMap mapWithFactory = MultiValueMap.decorate(baseMap, new Factory() {
            public Object create() {
                return new ArrayList<Object>();
            }
        });
        assertNotNull(mapWithFactory);
    }

    public void testConstructorExceptions() throws Throwable {
        try {
            new MultiValueMap(new HashMap<Object, Object>(), null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    public void testPutAndGet() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        assertNull(map.put("key1", "value1"));
        assertEquals("value1", map.put("key1", "value2"));

        Collection col = map.getCollection("key1");
        assertNotNull(col);
        assertEquals(2, col.size());
        assertTrue(col.contains("value1"));
        assertTrue(col.contains("value2"));

        assertNull(map.getCollection("nonexistent"));
        assertEquals(0, map.size("nonexistent"));
        assertEquals(2, map.size("key1"));
    }

    public void testPutAllMap() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        Map<String, String> normalMap = new HashMap<String, String>();
        normalMap.put("a", "1");
        normalMap.put("b", "2");

        map.putAll(normalMap);
        assertEquals(1, map.size("a"));
        assertEquals(1, map.size("b"));

        MultiValueMap multiMap = new MultiValueMap();
        multiMap.put("a", "3");

        MultiValueMap map2 = new MultiValueMap();
        map2.putAll(multiMap);
        assertEquals(1, map2.size("a"));
        assertTrue(map2.getCollection("a").contains("3"));
    }

    public void testPutAllCollection() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        ArrayList<String> values = new ArrayList<String>();
        values.add("v1");
        values.add("v2");

        boolean changed = map.putAll("key1", values);
        assertTrue(changed);
        assertEquals(2, map.size("key1"));

        boolean changedAgain = map.putAll("key1", null);
        assertFalse(changedAgain);

        boolean changedEmpty = map.putAll("key1", new ArrayList<String>());
        assertFalse(changedEmpty);
    }

    public void testRemoveMapping() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        map.put("key1", "val1");
        map.put("key1", "val2");

        assertNull(map.removeMapping("nonexistent", "val1"));
        assertNull(map.removeMapping("key1", "nonexistentVal"));

        assertEquals("val1", map.removeMapping("key1", "val1"));
        assertEquals(1, map.size("key1"));

        assertEquals("val2", map.removeMapping("key1", "val2"));
        assertNull(map.getCollection("key1"));
    }

    public void testContainsValue() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        map.put("key1", "val1");

        assertTrue(map.containsValue("val1"));
        assertFalse(map.containsValue("val2"));

        assertTrue(map.containsValue("key1", "val1"));
        assertFalse(map.containsValue("key1", "val2"));
        assertFalse(map.containsValue("nonexistent", "val1"));

        MultiValueMap emptyMap = new MultiValueMap();
        assertFalse(emptyMap.containsValue("val1"));
    }

    public void testValuesView() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        map.put("key1", "val1");
        map.put("key1", "val2");
        map.put("key2", "val3");

        Collection values = map.values();
        assertNotNull(values);
        assertEquals(3, values.size());

        values.clear();
        assertEquals(0, map.size());
    }

    public void testIterator() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        map.put("key1", "val1");
        map.put("key1", "val2");

        Iterator it = map.iterator("key1");
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertEquals("val1", it.next());
        assertTrue(it.hasNext());
        assertEquals("val2", it.next());
        assertFalse(it.hasNext());

        Iterator emptyIt = map.iterator("nonexistent");
        assertNotNull(emptyIt);
        assertFalse(emptyIt.hasNext());
    }

    public void testIteratorRemove() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        map.put("key1", "val1");

        Iterator it = map.iterator("key1");
        assertTrue(it.hasNext());
        assertEquals("val1", it.next());
        it.remove();
        assertNull(map.getCollection("key1"));
    }

    public void testTotalSize() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        assertEquals(0, map.totalSize());

        map.put("k1", "v1");
        map.put("k1", "v2");
        map.put("k2", "v3");
        assertEquals(3, map.totalSize());
    }

    public void testClear() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        map.put("k1", "v1");
        assertEquals(1, map.totalSize());

        map.clear();
        assertEquals(0, map.totalSize());
    }

    public void testSerialization() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        map.put("k1", "v1");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(map);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        MultiValueMap deserialized = (MultiValueMap) ois.readObject();
        ois.close();

        assertNotNull(deserialized);
        assertEquals(1, deserialized.totalSize());
        assertTrue(deserialized.containsValue("v1"));
    }

    public void testReflectionFactoryException() throws Throwable {
        MultiValueMap map = MultiValueMap.decorate(new HashMap<Object, Object>(), UninstantiableCollection.class);
        try {
            map.put("key", "value");
            fail("Expected FunctorException");
        } catch (FunctorException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    public static class UninstantiableCollection extends ArrayList<Object> {
        public UninstantiableCollection() {
            throw new RuntimeException("Cannot instantiate");
        }
    }
}