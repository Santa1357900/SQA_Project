package org.apache.commons.collections.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.commons.collections.Factory;
import org.apache.commons.collections.FunctorException;

public class MultiValueMapTest extends TestCase {

    public MultiValueMapTest(String name) {
        super(name);
    }

    public void testNoArgConstructor() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    public void testDecorateMap() throws Throwable {
        Map inner = new HashMap();
        MultiValueMap map = MultiValueMap.decorate(inner);
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    public void testDecorateMapWithClass() throws Throwable {
        Map inner = new HashMap();
        MultiValueMap map = MultiValueMap.decorate(inner, ArrayList.class);
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    public void testDecorateMapWithFactory() throws Throwable {
        Map inner = new HashMap();
        Factory factory = new Factory() {
            public Object create() {
                return new ArrayList<Object>();
            }
        };
        MultiValueMap map = MultiValueMap.decorate(inner, factory);
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    public void testProtectedConstructorNullFactory() throws Throwable {
        Map inner = new HashMap();
        try {
            new MultiValueMap(inner, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("factory"));
        }
    }

    public void testPutAndGet() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        Object added = map.put("key1", "value1");
        assertNull(added);

        Object addedAgain = map.put("key1", "value2");
        assertNull(addedAgain);

        Collection col = map.getCollection("key1");
        assertNotNull(col);
        assertEquals(2, col.size());
        assertTrue(col.contains("value1"));
        assertTrue(col.contains("value2"));
    }

    public void testSizeForKey() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        assertEquals(0, map.size("nonExistent"));

        map.put("key1", "val1");
        map.put("key1", "val2");
        assertEquals(2, map.size("key1"));
    }

    public void testTotalSize() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        assertEquals(0, map.totalSize());

        map.put("key1", "val1");
        map.put("key1", "val2");
        map.put("key2", "val3");
        assertEquals(3, map.totalSize());
    }

    public void testContainsValue() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        assertFalse(map.containsValue("val1"));

        map.put("key1", "val1");
        assertTrue(map.containsValue("val1"));
        assertFalse(map.containsValue("val2"));
    }

    public void testContainsValueWithKey() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        assertFalse(map.containsValue("key1", "val1"));

        map.put("key1", "val1");
        assertTrue(map.containsValue("key1", "val1"));
        assertFalse(map.containsValue("key1", "val2"));
        assertFalse(map.containsValue("key2", "val1"));
    }

    public void testRemoveMapping() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        map.put("key1", "val1");
        map.put("key1", "val2");

        Object removed = map.removeMapping("key2", "val1");
        assertNull(removed);

        Object removedNonExist = map.removeMapping("key1", "val3");
        assertNull(removedNonExist);

        Object removedValid = map.removeMapping("key1", "val1");
        assertEquals("val1", removedValid);
        assertEquals(1, map.size("key1"));

        Object removedLast = map.removeMapping("key1", "val2");
        assertEquals("val2", removedLast);
        assertNull(map.getCollection("key1"));
        assertTrue(map.isEmpty());
    }

    public void testClear() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        map.put("key1", "val1");
        map.put("key2", "val2");
        assertFalse(map.isEmpty());

        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.totalSize());
    }

    public void testPutAllCollection() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        List<String> list = new ArrayList<String>();
        list.add("val1");
        list.add("val2");

        boolean changed = map.putAll("key1", null);
        assertFalse(changed);

        List<String> emptyList = new ArrayList<String>();
        boolean changedEmpty = map.putAll("key1", emptyList);
        assertFalse(changedEmpty);

        boolean changedValid = map.putAll("key1", list);
        assertFalse(changedValid); // because map logic returns false on new collection put
        assertEquals(2, map.size("key1"));

        boolean changedAgain = map.putAll("key1", list);
        assertTrue(changedAgain);
        assertEquals(4, map.size("key1"));
    }

    public void testPutAllMapNormal() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        Map<String, String> normalMap = new HashMap<String, String>();
        normalMap.put("k1", "v1");
        normalMap.put("k2", "v2");

        map.putAll(normalMap);
        assertEquals(2, map.totalSize());
        assertEquals("v1", map.getCollection("k1").iterator().next());
    }

    public void testPutAllMapMulti() throws Throwable {
        MultiValueMap map1 = new MultiValueMap();
        map1.put("k1", "v1");

        MultiValueMap map2 = new MultiValueMap();
        map2.putAll(map1);

        assertEquals(1, map2.totalSize());
        assertTrue(map2.containsValue("k1", "v1"));
    }

    public void testIteratorForKey() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        Iterator itEmpty = map.iterator("nonExistent");
        assertNotNull(itEmpty);
        assertFalse(itEmpty.hasNext());

        map.put("key1", "val1");
        map.put("key1", "val2");

        Iterator it = map.iterator("key1");
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertEquals("val1", it.next());
        assertTrue(it.hasNext());
        assertEquals("val2", it.next());
        assertFalse(it.hasNext());
    }

    public void testIteratorRemove() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        map.put("key1", "val1");
        map.put("key1", "val2");

        Iterator it = map.iterator("key1");
        assertTrue(it.hasNext());
        assertEquals("val1", it.next());
        it.remove();

        assertEquals(1, map.size("key1"));

        assertTrue(it.hasNext());
        assertEquals("val2", it.next());
        it.remove();

        assertNull(map.getCollection("key1"));
        assertTrue(map.isEmpty());
    }

    public void testValuesView() throws Throwable {
        MultiValueMap map = new MultiValueMap();
        map.put("key1", "val1");
        map.put("key1", "val2");
        map.put("key2", "val3");

        Collection values = map.values();
        assertNotNull(values);
        assertEquals(3, values.size());
        assertTrue(values.contains("val1"));
        assertTrue(values.contains("val2"));
        assertTrue(values.contains("val3"));

        // Test iterator from values collection (IteratorChain)
        Iterator chainIt = values.iterator();
        assertNotNull(chainIt);
        int count = 0;
        while (chainIt.hasNext()) {
            assertNotNull(chainIt.next());
            count++;
        }
        assertEquals(3, count);

        // Test values clear
        values.clear();
        assertTrue(map.isEmpty());
    }

    public void testReflectionFactoryException() throws Throwable {
        Map inner = new HashMap();
        // Passing a class without a default no-arg constructor to ReflectionFactory via decorate
        MultiValueMap map = MultiValueMap.decorate(inner, InvalidBean.class);
        try {
            map.put("key", "value");
            fail("Expected FunctorException");
        } catch (FunctorException e) {
            assertTrue(e.getMessage().contains("Cannot instantiate class"));
        }
    }

    public static class InvalidBean {
        public InvalidBean(String arg) {
        }
    }
}