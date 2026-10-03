package org.apache.commons.collections.map;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.collections.MapIterator;

public class Flat3MapTest extends TestCase {

    public Flat3MapTest(String testName) {
        super(testName);
    }

    public void testConstructorAndBasicGetSize() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertNull(map.get("key1"));

        Map<String, String> source = new HashMap<String, String>();
        source.put("A", "ValA");
        source.put("B", "ValB");
        Flat3Map mapCopy = new Flat3Map(source);
        assertFalse(mapCopy.isEmpty());
        assertEquals(2, mapCopy.size());
        assertEquals("ValA", mapCopy.get("A"));
        assertEquals("ValB", mapCopy.get("B"));
    }

    public void testPutAndGetFlatAndDelegate() throws Throwable {
        Flat3Map map = new Flat3Map();
        
        // Size 1
        assertNull(map.put("k1", "v1"));
        assertEquals(1, map.size());
        assertEquals("v1", map.get("k1"));
        
        // Update existing key
        assertEquals("v1", map.put("k1", "v1_new"));
        assertEquals("v1_new", map.get("k1"));

        // Size 2
        assertNull(map.put("k2", "v2"));
        assertEquals(2, map.size());
        assertEquals("v2", map.get("k2"));

        // Size 3
        assertNull(map.put("k3", "v3"));
        assertEquals(3, map.size());
        assertEquals("v3", map.get("k3"));

        // Size 4 -> triggers delegate mode
        assertNull(map.put("k4", "v4"));
        assertEquals(4, map.size());
        assertEquals("v4", map.get("k4"));
        assertEquals("v1_new", map.get("k1"));
        assertEquals("v2", map.get("k2"));
        assertEquals("v3", map.get("k3"));
        
        // Put in delegate mode updating existing
        assertEquals("v4", map.put("k4", "v4_new"));
        assertEquals("v4_new", map.get("k4"));
    }

    public void testNullKeysAndValues() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertNull(map.put(null, "nullKeyVal"));
        assertEquals(1, map.size());
        assertEquals("nullKeyVal", map.get(null));
        assertTrue(map.containsKey(null));
        assertTrue(map.containsValue("nullKeyVal"));

        assertNull(map.put("valNullKey", null));
        assertEquals(2, map.size());
        assertNull(map.get("valNullKey"));
        assertTrue(map.containsKey("valNullKey"));
        assertTrue(map.containsValue(null));
    }

    public void testPutAll() throws Throwable {
        Flat3Map map = new Flat3Map();
        Map<String, String> emptyMap = new HashMap<String, String>();
        map.putAll(emptyMap);
        assertEquals(0, map.size());

        Map<String, String> m = new HashMap<String, String>();
        m.put("1", "one");
        m.put("2", "two");
        m.put("3", "three");
        m.put("4", "four");
        map.putAll(m);
        assertEquals(4, map.size());
        assertEquals("one", map.get("1"));
        assertEquals("four", map.get("4"));

        // test putAll when already delegate
        Flat3Map map2 = new Flat3Map();
        map2.put("a", "1");
        map2.put("b", "2");
        map2.put("c", "3");
        map2.put("d", "4"); // delegate mode
        
        Map<String, String> m2 = new HashMap<String, String>();
        m2.put("e", "5");
        map2.putAll(m2);
        assertEquals(5, map2.size());
        assertEquals("5", map2.get("e"));
    }

    public void testContainsKeyAndValue() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "alpha");
        map.put("b", "beta");

        assertTrue(map.containsKey("a"));
        assertFalse(map.containsKey("z"));
        assertFalse(map.containsKey(null));

        assertTrue(map.containsValue("alpha"));
        assertFalse(map.containsValue("gamma"));
        assertFalse(map.containsValue(null));

        // Test with delegate mode
        map.put("c", "gamma");
        map.put("d", "delta");
        assertTrue(map.containsKey("d"));
        assertFalse(map.containsKey("missing"));
        assertTrue(map.containsValue("delta"));
        assertFalse(map.containsValue("missingVal"));
    }

    public void testRemoveFlatAndDelegate() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertNull(map.remove("nonexistent"));

        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");

        // Remove key2 (middle in flat)
        assertEquals("v2", map.remove("k2"));
        assertEquals(2, map.size());
        assertNull(map.get("k2"));
        assertEquals("v1", map.get("k1"));
        assertEquals("v3", map.get("k3"));

        // Remove remaining
        assertEquals("v1", map.remove("k1"));
        assertEquals("v3", map.remove("k3"));
        assertEquals(0, map.size());

        // Test remove with null keys in flat mode
        map.put(null, "nullVal");
        map.put("k1", "v1");
        assertEquals("nullVal", map.remove(null));
        assertEquals(1, map.size());
        map.clear();

        // Test delegate mode remove
        map.put("1", "a");
        map.put("2", "b");
        map.put("3", "c");
        map.put("4", "d"); // delegate
        assertEquals("b", map.remove("2"));
        assertEquals(3, map.size());
        assertNull(map.remove("missingDelegate"));
    }

    public void testClear() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("k1", "v1");
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());

        // Clear in delegate mode
        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");
        map.put("k4", "v4");
        assertEquals(4, map.size());
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        // should switch back to flat mode
        map.put("newK", "newV");
        assertEquals(1, map.size());
    }

    public void testMapIterator() throws Throwable {
        Flat3Map map = new Flat3Map();
        MapIterator it = map.mapIterator();
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // expected
        }

        map.put("k1", "v1");
        map.put("k2", "v2");
        it = map.mapIterator();
        assertTrue(it.hasNext());
        
        try {
            it.remove();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }

        Object key = it.next();
        assertEquals("k1", key);
        assertTrue(it.toString().contains("k1=v1"));

        assertEquals("v1", it.getValue());
        assertEquals("v1_new", it.setValue("v1_new"));
        assertEquals("v1_new", it.getValue());

        it.remove();
        assertEquals(1, map.size());

        // Test delegate mode iterator
        map.put("k3", "v3");
        map.put("k4", "v4"); // delegate
        MapIterator delegateIt = map.mapIterator();
        assertTrue(delegateIt.hasNext());
        assertNotNull(delegateIt.next());
    }

    public void testEntrySet() throws Throwable {
        Flat3Map map = new Flat3Map();
        Set entrySet = map.entrySet();
        assertEquals(0, entrySet.size());
        assertTrue(entrySet.iterator().hasNext() == false);

        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");
        map.put("k4", "v4"); // delegate mode for entrySet branch

        assertEquals(4, entrySet.size());
        assertFalse(entrySet.remove("notAnEntry"));

        Iterator it = entrySet.iterator();
        assertTrue(it.hasNext());
        Object entryObj = it.next();
        assertTrue(entryObj instanceof Map.Entry);
        Map.Entry entry = (Map.Entry) entryObj;
        assertNotNull(entry.getKey());
        assertNotNull(entry.getValue());
        
        entry.setValue("newVal");
        assertEquals("newVal", entry.getValue());
        assertNotNull(entry.hashCode());
        assertTrue(entry.equals(entry));
        assertFalse(entry.equals("string"));
        assertNotNull(entry.toString());

        it.remove();
        assertEquals(3, map.size());

        entrySet.clear();
        assertTrue(map.isEmpty());
    }

    public void testKeySet() throws Throwable {
        Flat3Map map = new Flat3Map();
        Set keySet = map.keySet();
        assertEquals(0, keySet.size());

        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");
        map.put("k4", "v4"); // delegate

        assertEquals(4, keySet.size());
        assertTrue(keySet.contains("k1"));
        assertFalse(keySet.contains("missing"));

        assertTrue(keySet.remove("k1"));
        assertEquals(3, map.size());

        Iterator it = keySet.iterator();
        assertTrue(it.hasNext());
        assertNotNull(it.next());

        keySet.clear();
        assertTrue(map.isEmpty());
    }

    public void testValues() throws Throwable {
        Flat3Map map = new Flat3Map();
        Collection values = map.values();
        assertEquals(0, values.size());

        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");
        map.put("k4", "v4"); // delegate

        assertEquals(4, values.size());
        assertTrue(values.contains("v1"));
        assertFalse(values.contains("missing"));

        Iterator it = values.iterator();
        assertTrue(it.hasNext());
        assertNotNull(it.next());

        values.clear();
        assertTrue(map.isEmpty());
    }

    public void testCloneAndEqualsAndHashCode() throws Throwable {
        Flat3Map map1 = new Flat3Map();
        map1.put("k1", "v1");
        map1.put("k2", "v2");

        Flat3Map map2 = (Flat3Map) map1.clone();
        assertEquals(map1, map2);
        assertEquals(map1.hashCode(), map2.hashCode());
        assertTrue(map1.equals(map1));
        assertFalse(map1.equals("someString"));

        Map<String, String> standardMap = new HashMap<String, String>();
        standardMap.put("k1", "v1");
        standardMap.put("k2", "v2");
        assertTrue(map1.equals(standardMap));

        standardMap.put("extra", "val");
        assertFalse(map1.equals(standardMap));

        // Delegate clone
        map1.put("k3", "v3");
        map1.put("k4", "v4"); // delegate mode
        Flat3Map mapDelegateClone = (Flat3Map) map1.clone();
        assertEquals(map1, mapDelegateClone);
    }

    public void testSerialization() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");
        map.put("k4", "v4"); // delegate mode serialization test

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(map);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Flat3Map deserialized = (Flat3Map) ois.readObject();
        ois.close();

        assertEquals(map.size(), deserialized.size());
        assertEquals("v1", deserialized.get("k1"));
        assertEquals("v4", deserialized.get("k4"));
    }

    public void testToString() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertEquals("{}", map.toString());

        map.put("k1", "v1");
        assertTrue(map.toString().contains("k1=v1"));

        // Delegate toString
        map.put("k2", "v2");
        map.put("k3", "v3");
        map.put("k4", "v4");
        assertNotNull(map.toString());
    }
}