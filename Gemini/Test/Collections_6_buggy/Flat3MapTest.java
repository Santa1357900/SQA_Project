package org.apache.commons.collections.map;

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
import org.apache.commons.collections.ResettableIterator;

import junit.framework.TestCase;

public class Flat3MapTest extends TestCase {

    public Flat3MapTest(String name) {
        super(name);
    }

    public void testConstructorAndBasicOperations() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertNull(map.get("A"));
        assertFalse(map.containsKey("A"));
        assertFalse(map.containsValue("ValueA"));

        // Put element 1
        assertNull(map.put("A", "ValueA"));
        assertFalse(map.isEmpty());
        assertEquals(1, map.size());
        assertEquals("ValueA", map.get("A"));
        assertTrue(map.containsKey("A"));
        assertTrue(map.containsValue("ValueA"));

        // Update existing key
        assertEquals("ValueA", map.put("A", "NewValueA"));
        assertEquals("NewValueA", map.get("A"));
        assertEquals(1, map.size());

        // Put null key and null value
        assertNull(map.put(null, null));
        assertEquals(2, map.size());
        assertNull(map.get(null));
        assertTrue(map.containsKey(null));
        assertTrue(map.containsValue(null));

        // Put elements up to flat limit (3)
        assertNull(map.put("B", "ValueB"));
        assertNull(map.put("C", "ValueC"));
        assertEquals(3, map.size());

        // Exceed flat limit (trigger delegate mode)
        assertNull(map.put("D", "ValueD"));
        assertEquals(4, map.size());
        assertEquals("ValueD", map.get("D"));
        assertTrue(map.containsKey("D"));
        assertTrue(map.containsValue("ValueD"));

        // Delegate mode operations
        assertEquals("NewValueA", map.get("A"));
        assertNull(map.get("NonExistent"));
        assertFalse(map.containsKey("NonExistent"));
        assertFalse(map.containsValue("NonExistentValue"));

        // Clear map
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertFalse(map.containsKey("A"));
    }

    public void testMapWithCollectionConstructor() throws Throwable {
        Map<String, String> source = new HashMap<String, String>();
        source.put("K1", "V1");
        source.put("K2", "V2");

        Flat3Map map = new Flat3Map(source);
        assertEquals(2, map.size());
        assertEquals("V1", map.get("K1"));
        assertEquals("V2", map.get("K2"));

        try {
            new Flat3Map(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    public void testPutAll() throws Throwable {
        Flat3Map map = new Flat3Map();
        Map<String, String> emptySource = new HashMap<String, String>();
        map.putAll(emptySource);
        assertEquals(0, map.size());

        Map<String, String> smallSource = new HashMap<String, String>();
        smallSource.put("1", "A");
        smallSource.put("2", "B");
        map.putAll(smallSource);
        assertEquals(2, map.size());

        // PutAll large map (> 3 elements) to trigger delegate mode
        Map<String, String> largeSource = new HashMap<String, String>();
        largeSource.put("3", "C");
        largeSource.put("4", "D");
        largeSource.put("5", "E");
        map.putAll(largeSource);
        assertEquals(5, map.size());
    }

    public void testRemoveFlatMode() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertNull(map.remove("A")); // Remove from empty

        map.put("A", "ValA");
        map.put("B", "ValB");
        map.put("C", "ValC");
        // size = 3

        // Remove middle key with non-null keys
        assertEquals("ValB", map.remove("B"));
        assertEquals(2, map.size());
        assertNull(map.get("B"));
        assertEquals("ValA", map.get("A"));
        assertEquals("ValC", map.get("C"));

        map.clear();
        map.put(null, "ValNull");
        map.put("A", "ValA");
        assertEquals("ValNull", map.remove(null));
        assertEquals(1, map.size());
        assertNull(map.get(null));

        map.clear();
        map.put("A", "ValA");
        assertEquals("ValA", map.remove("A"));
        assertEquals(0, map.size());

        // Test removing non-existent key in flat mode
        map.put("A", "ValA");
        assertNull(map.remove("Z"));
        assertEquals(1, map.size());
    }

    public void testRemoveDelegateMode() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("1", "A");
        map.put("2", "B");
        map.put("3", "C");
        map.put("4", "D"); // triggers delegate

        assertEquals("D", map.remove("4"));
        assertEquals(3, map.size());
        assertNull(map.remove("NonExistent"));
    }

    public void testContainsValueFlatAndDelegate() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("A", null);
        map.put("B", "ValB");

        assertTrue(map.containsValue(null));
        assertTrue(map.containsValue("ValB"));
        assertFalse(map.containsValue("ValC"));

        map.put("C", "ValC");
        map.put("D", "ValD"); // delegate mode
        assertTrue(map.containsValue(null));
        assertTrue(map.containsValue("ValB"));
        assertTrue(map.containsValue("ValD"));
        assertFalse(map.containsValue("NotPresent"));
    }

    public void testViewsAndIteratorsFlatMode() throws Throwable {
        Flat3Map map = new Flat3Map();
        
        // Empty views
        assertTrue(map.keySet().isEmpty());
        assertTrue(map.values().isEmpty());
        assertTrue(map.entrySet().isEmpty());
        
        assertEquals(0, map.keySet().size());
        assertEquals(0, map.values().size());
        assertEquals(0, map.entrySet().size());

        map.put("K1", "V1");
        map.put("K2", "V2");
        map.put("K3", "V3");

        // KeySet operations
        Set keySet = map.keySet();
        assertEquals(3, keySet.size());
        assertTrue(keySet.contains("K1"));
        assertFalse(keySet.contains("InvalidKey"));
        
        Iterator keyIt = keySet.iterator();
        assertTrue(keyIt.hasNext());
        Object k = keyIt.next();
        assertNotNull(k);
        keyIt.remove();
        assertEquals(2, map.size());

        // Values operations
        Collection values = map.values();
        assertEquals(2, values.size());
        assertTrue(values.contains("V2"));
        assertFalse(values.contains("InvalidVal"));
        
        Iterator valIt = values.iterator();
        assertTrue(valIt.hasNext());
        Object v = valIt.next();
        assertNotNull(v);

        // EntrySet operations
        Set entrySet = map.entrySet();
        assertEquals(2, entrySet.size());
        
        Iterator entryIt = entrySet.iterator();
        assertTrue(entryIt.hasNext());
        Object entryObj = entryIt.next();
        assertTrue(entryObj instanceof Map.Entry);
        Map.Entry entry = (Map.Entry) entryObj;
        assertNotNull(entry.getKey());
        assertNotNull(entry.getValue());
        
        // Test entrySet remove with non-entry
        assertFalse(entrySet.remove("NotAnEntry"));
        assertTrue(entrySet.remove(entry));

        // MapIterator
        MapIterator mapIt = map.mapIterator();
        assertFalse(mapIt.hasNext()); // since map is now size 1 or 0
        
        map.clear();
        map.put("A", "1");
        map.put("B", "2");
        
        MapIterator mi = map.mapIterator();
        assertTrue(mi.hasNext());
        assertEquals("A", mi.next());
        assertEquals("A", mi.getKey());
        assertEquals("1", mi.getValue());
        assertEquals("1", mi.setValue("10"));
        assertEquals("10", mi.getValue());
        
        mi.remove();
        assertEquals(1, map.size());
        
        mi.reset();
        assertTrue(mi.hasNext());
        
        // Test iterator exceptions
        Flat3Map.FlatMapIterator fmi = new Flat3Map.FlatMapIterator(new Flat3Map());
        assertFalse(fmi.hasNext());
        try {
            fmi.next();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // Expected
        }
        try {
            fmi.remove();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        }
        try {
            fmi.getKey();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        }
        try {
            fmi.getValue();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        }
        try {
            fmi.setValue("X");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        }
        assertNotNull(fmi.toString());
    }

    public void testViewsAndIteratorsDelegateMode() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("1", "A");
        map.put("2", "B");
        map.put("3", "C");
        map.put("4", "D"); // Delegate mode

        assertNotNull(map.keySet());
        assertNotNull(map.values());
        assertNotNull(map.entrySet());
        assertNotNull(map.mapIterator());

        assertTrue(map.keySet().contains("1"));
        assertTrue(map.values().contains("A"));
        
        Iterator it = map.entrySet().iterator();
        assertTrue(it.hasNext());
        assertNotNull(it.next());

        map.keySet().clear();
        assertTrue(map.isEmpty());
    }

    public void testCloneAndEqualsAndHashCode() throws Throwable {
        Flat3Map map1 = new Flat3Map();
        map1.put("A", "1");
        map1.put("B", "2");

        Flat3Map cloned = (Flat3Map) map1.clone();
        assertEquals(map1, cloned);
        assertEquals(map1.hashCode(), cloned.hashCode());
        assertNotSame(map1, cloned);

        // Delegate mode clone
        map1.put("C", "3");
        map1.put("D", "4"); // delegate mode
        Flat3Map clonedDelegate = (Flat3Map) map1.clone();
        assertEquals(map1, clonedDelegate);
        assertEquals(map1.hashCode(), clonedDelegate.hashCode());

        // Equals and HashCode variations
        assertEquals(map1, map1);
        assertFalse(map1.equals("NotAMap"));

        Flat3Map map2 = new Flat3Map();
        map2.put("A", "1");
        assertFalse(map1.equals(map2)); // different size

        map2.put("B", "2");
        map2.put("C", "99");
        map2.put("D", "4");
        assertFalse(map1.equals(map2)); // different value

        Flat3Map map3 = new Flat3Map();
        map3.put("A", "1");
        map3.put("B", "2");
        map3.put("Z", "3");
        map3.put("D", "4");
        assertFalse(map1.equals(map3)); // missing key

        // ToString
        assertEquals("{}", new Flat3Map().toString());
        assertNotNull(map1.toString());
        
        map1.clear();
        map1.put("A", "1");
        assertNotNull(map1.toString());
    }

    public void testSerialization() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("A", "1");
        map.put("B", "2");
        map.put("C", "3");
        map.put("D", "4"); // Delegate mode serialization test

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(map);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Flat3Map deserialized = (Flat3Map) ois.readObject();
        ois.close();

        assertEquals(map, deserialized);
        assertEquals(4, deserialized.size());
        assertEquals("A", deserialized.get("A"));
    }
}