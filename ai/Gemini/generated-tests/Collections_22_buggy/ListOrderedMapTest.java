package org.apache.commons.collections4.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.collections4.OrderedMapIterator;
import org.junit.Test;

public class ListOrderedMapTest {

    @Test
    public void testConstructorsAndFactory() throws Throwable {
        ListOrderedMap<String, String> map1 = new ListOrderedMap<String, String>();
        assertTrue(map1.isEmpty());

        Map<String, String> normalMap = new HashMap<String, String>();
        normalMap.put("A", "ValueA");
        ListOrderedMap<String, String> map2 = ListOrderedMap.listOrderedMap(normalMap);
        assertEquals(1, map2.size());
        assertEquals("ValueA", map2.get("A"));
    }

    @Test
    public void testFirstAndLastKey() throws Throwable {
        ListOrderedMap<String, String> map = new ListOrderedMap<String, String>();
        try {
            map.firstKey();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // expected
        }

        try {
            map.lastKey();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // expected
        }

        map.put("One", "1");
        map.put("Two", "2");

        assertEquals("One", map.firstKey());
        assertEquals("Two", map.lastKey());
    }

    @Test
    public void testNextAndPreviousKey() throws Throwable {
        ListOrderedMap<String, String> map = new ListOrderedMap<String, String>();
        map.put("A", "1");
        map.put("B", "2");
        map.put("C", "3");

        assertEquals("B", map.nextKey("A"));
        assertEquals("C", map.nextKey("B"));
        assertNull(map.nextKey("C"));
        assertNull(map.nextKey("NonExistent"));

        assertNull(map.previousKey("A"));
        assertEquals("A", map.previousKey("B"));
        assertEquals("B", map.previousKey("C"));
        assertNull(map.previousKey("NonExistent"));
    }

    @Test
    public void testPutAndReAdd() throws Throwable {
        ListOrderedMap<String, String> map = new ListOrderedMap<String, String>();
        assertNull(map.put("Key1", "Val1"));
        assertEquals("Val1", map.put("Key1", "Val1Updated"));
        assertEquals("Val1Updated", map.get("Key1"));
        assertEquals(1, map.size());
        assertEquals("Key1", map.get(0));
    }

    @Test
    public void testPutAllMaps() throws Throwable {
        ListOrderedMap<String, String> map = new ListOrderedMap<String, String>();
        map.put("A", "1");

        Map<String, String> src = new HashMap<String, String>();
        src.put("B", "2");
        src.put("C", "3");

        map.putAll(src);
        assertEquals(3, map.size());
        assertEquals("2", map.get("B"));

        Map<String, String> srcIndex = new HashMap<String, String>();
        srcIndex.put("X", "9");
        map.putAll(1, srcIndex);
        assertEquals("X", map.get(1));
    }

    @Test
    public void testPutAtIndex() throws Throwable {
        ListOrderedMap<String, String> map = new ListOrderedMap<String, String>();
        map.put("A", "1");
        map.put("C", "3");

        map.put(1, "B", "2");
        assertEquals("B", map.get(1));
        assertEquals(3, map.size());

        // Re-adding existing key at index
        map.put(0, "C", "3-new");
        assertEquals("C", map.get(0));
        assertEquals("3-new", map.get("C"));
    }

    @Test
    public void testRemoveByKeyAndIndex() throws Throwable {
        ListOrderedMap<String, String> map = new ListOrderedMap<String, String>();
        map.put("A", "1");
        map.put("B", "2");

        assertNull(map.remove("NonExistent"));
        assertEquals("1", map.remove("A"));
        assertFalse(map.containsKey("A"));

        assertEquals("2", map.remove(0));
        assertTrue(map.isEmpty());
    }

    @Test
    public void testClear() throws Throwable {
        ListOrderedMap<String, String> map = new ListOrderedMap<String, String>();
        map.put("A", "1");
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.keyList().size());
    }

    @Test
    public void testViewsAndLists() throws Throwable {
        ListOrderedMap<String, String> map = new ListOrderedMap<String, String>();
        map.put("A", "1");
        map.put("B", "2");

        Set<String> keySet = map.keySet();
        assertTrue(keySet.contains("A"));
        assertTrue(keySet.size() == 2);
        keySet.clear();
        assertTrue(map.isEmpty());

        map.put("A", "1");
        map.put("B", "2");
        Collection<String> values = map.values();
        assertTrue(values.contains("1"));
        values.remove("1");
        assertFalse(map.containsKey("A"));

        map.put("A", "1");
        List<String> keyList = map.keyList();
        assertEquals(2, keyList.size());
        assertEquals("A", keyList.get(0));

        List<String> valueList = map.valueList();
        assertEquals("2", valueList.get(1));
        valueList.set(1, "2-updated");
        assertEquals("2-updated", map.getValue(1));
        valueList.remove(0);
        assertEquals(1, map.size());

        Set<Map.Entry<String, String>> entrySet = map.entrySet();
        assertFalse(entrySet.isEmpty());
        assertTrue(entrySet.contains(new HashMap.SimpleEntry<String, String>("B", "2-updated")));
        assertFalse(entrySet.remove("NotAnEntry"));
        
        Iterator<Map.Entry<String, String>> entryIter = entrySet.iterator();
        assertTrue(entryIter.hasNext());
        Map.Entry<String, String> entry = entryIter.next();
        entry.setValue("NewVal");
        assertEquals("NewVal", entry.getValue());
        entryIter.remove();
        assertTrue(map.isEmpty());

        assertEquals("{}", map.toString());

        map.put("A", "1");
        map.put("B", "2");
        String str = map.toString();
        assertTrue(str.contains("A=1"));
    }

    @Test
    public void testIndexGettersAndSetters() throws Throwable {
        ListOrderedMap<String, String> map = new ListOrderedMap<String, String>();
        map.put("A", "1");
        map.put("B", "2");

        assertEquals("A", map.get(0));
        assertEquals("1", map.getValue(0));
        assertEquals(0, map.indexOf("A"));
        assertEquals(-1, map.indexOf("NonExistent"));

        assertEquals("1", map.setValue(0, "1-new"));
        assertEquals("1-new", map.getValue(0));

        assertEquals(map.keyList(), map.asList());
    }

    @Test
    public void testMapIterator() throws Throwable {
        ListOrderedMap<String, String> iteratorMap = new ListOrderedMap<String, String>();
        iteratorMap.put("A", "1");
        iteratorMap.put("B", "2");

        OrderedMapIterator<String, String> it = iteratorMap.mapIterator();
        
        try {
            it.getKey();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }

        try {
            it.getValue();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }

        try {
            it.setValue("Val");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }

        try {
            it.remove();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }

        assertTrue(it.hasNext());
        assertFalse(it.hasPrevious());

        assertEquals("A", it.next());
        assertTrue(it.hasPrevious());
        assertEquals("A", it.getKey());
        assertEquals("1", it.getValue());
        assertEquals("Iterator[A=1]", it.toString());

        assertEquals("2", it.setValue("1.5"));
        assertEquals("1.5", it.getValue());

        assertEquals("B", it.next());
        assertEquals("B", it.previous());

        it.remove();
        assertFalse(iteratorMap.containsKey("B"));

        it.reset();
        assertTrue(it.hasNext());
        assertEquals("Iterator[]", it.toString());
    }
}