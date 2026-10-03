package org.apache.commons.collections4.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.Factory;
import org.junit.Test;
import static org.junit.Assert.*;

public class MultiValueMapClaudeTest {

    // covers no-arg constructor + put(): new key creates ArrayList-backed collection
    @Test
    public void testDefaultConstructor_putNewKey_createsArrayListCollection() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        assertTrue(map.getCollection("a") instanceof ArrayList);
        assertTrue(map.getCollection("a").contains("1"));
    }

    // covers protected constructor: null factory throws IllegalArgumentException
    @Test
    public void testProtectedConstructor_nullFactory_throwsIllegalArgumentException() throws Throwable {
        try {
            new MultiValueMap<String, String>(new HashMap<String, Object>(), (Factory<Collection<String>>) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers static multiValueMap(Map) factory
    @Test
    public void testMultiValueMapFactory_withMap_createsWorkingMap() throws Throwable {
        Map<String, Collection<String>> backing = new HashMap<String, Collection<String>>();
        MultiValueMap<String, String> map = MultiValueMap.multiValueMap(backing);
        map.put("x", "1");
        assertEquals(1, map.size("x"));
    }

    // covers static multiValueMap(Map, Class) factory
    @Test
    public void testMultiValueMapFactory_withMapAndClass_usesSpecifiedCollectionType() throws Throwable {
        Map<String, Collection<String>> backing = new HashMap<String, Collection<String>>();
        MultiValueMap<String, String> map = MultiValueMap.multiValueMap(backing, ArrayList.class);
        map.put("y", "2");
        assertTrue(map.getCollection("y") instanceof ArrayList);
    }

    // covers static multiValueMap(Map, Factory) factory
    @Test
    public void testMultiValueMapFactory_withMapAndFactory_usesFactory() throws Throwable {
        Map<String, Object> backing = new HashMap<String, Object>();
        Factory<ArrayList<String>> factory = new Factory<ArrayList<String>>() {
            public ArrayList<String> create() {
                return new ArrayList<String>();
            }
        };
        MultiValueMap<String, String> map = MultiValueMap.multiValueMap(backing, factory);
        map.put("a", "1");
        assertEquals(1, map.size("a"));
    }

    // covers clear(): decorated().clear() empties the map
    @Test
    public void testClear_removesAllEntries() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.totalSize());
    }

    // covers removeMapping(): valuesForKey == null branch
    @Test
    public void testRemoveMapping_keyAbsent_returnsFalse() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        assertFalse(map.removeMapping("missing", "v"));
    }

    // covers removeMapping(): removed == false branch
    @Test
    public void testRemoveMapping_valueAbsent_returnsFalse() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        assertFalse(map.removeMapping("a", "2"));
    }

    // covers removeMapping(): value removed but collection not empty, key stays
    @Test
    public void testRemoveMapping_notLastValue_removesOnlyThatValue() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        map.put("a", "2");
        assertTrue(map.removeMapping("a", "1"));
        assertNotNull(map.get("a"));
        assertEquals(1, map.size("a"));
    }

    // covers removeMapping(): valuesForKey.isEmpty() branch -> key removed entirely
    @Test
    public void testRemoveMapping_lastValue_removesKeyEntirely() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        assertTrue(map.removeMapping("a", "1"));
        assertNull(map.get("a"));
    }

    // covers containsValue(Object): empty map, loop runs 0 times
    @Test
    public void testContainsValue_emptyMap_returnsFalse() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        assertFalse(map.containsValue("x"));
    }

    // covers containsValue(Object): value found in some entry's collection
    @Test
    public void testContainsValue_valuePresent_returnsTrue() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "x");
        assertTrue(map.containsValue("x"));
    }

    // covers containsValue(Object): loop runs, no match found
    @Test
    public void testContainsValue_valueAbsent_returnsFalse() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "x");
        assertFalse(map.containsValue("y"));
    }

    // covers put(): coll == null branch, newly created collection becomes non-empty
    @Test
    public void testPut_newKey_addsAndReturnsValue() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        Object result = map.put("a", "1");
        assertEquals("1", result);
        assertEquals(1, map.size("a"));
    }

    // covers put(): coll != null branch, add succeeds
    @Test
    public void testPut_existingKey_appendsAndReturnsValue() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        Object result = map.put("a", "2");
        assertEquals("2", result);
        assertEquals(2, map.size("a"));
    }

    // covers put(): coll != null branch, add fails (duplicate in Set) -> returns null
    @Test
    public void testPut_duplicateInSetBackedMap_addFailsReturnsNull() throws Throwable {
        Factory<Collection<String>> setFactory = new Factory<Collection<String>>() {
            public Collection<String> create() {
                return new HashSet<String>();
            }
        };
        MultiValueMap<String, String> map =
            new MultiValueMap<String, String>(new HashMap<String, Object>(), setFactory);
        map.put("a", "x");
        Object result = map.put("a", "x");
        assertNull(result);
        assertEquals(1, map.size("a"));
    }

    // covers put(): coll == null branch where factory returns a non-empty collection
    @Test
    public void testPut_factoryProducesNonEmptyCollection_stillAddsNewValue() throws Throwable {
        Factory<Collection<String>> presetFactory = new Factory<Collection<String>>() {
            public Collection<String> create() {
                List<String> list = new ArrayList<String>();
                list.add("preset");
                return list;
            }
        };
        MultiValueMap<String, String> map =
            new MultiValueMap<String, String>(new HashMap<String, Object>(), presetFactory);
        Object result = map.put("a", "v");
        assertEquals("v", result);
        assertEquals(2, map.size("a"));
    }

    // covers put(): coll.add() propagates exception for an immutable created collection
    @Test
    public void testPut_immutableCollectionFactory_addThrowsUnsupportedOperationException() throws Throwable {
        Factory<Collection<String>> badFactory = new Factory<Collection<String>>() {
            public Collection<String> create() {
                return Collections.emptyList();
            }
        };
        MultiValueMap<String, String> map =
            new MultiValueMap<String, String>(new HashMap<String, Object>(), badFactory);
        try {
            map.put("a", "v");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // covers putAll(Map): map instanceof MultiMap branch, delegates to putAll(key,Collection)
    @Test
    public void testPutAll_mapInstanceOfMultiMap_copiesCollections() throws Throwable {
        MultiValueMap<String, String> source = new MultiValueMap<String, String>();
        source.put("a", "1");
        source.put("a", "2");
        MultiValueMap<String, String> target = new MultiValueMap<String, String>();
        target.putAll(source);
        assertEquals(2, target.size("a"));
    }

    // covers putAll(Map): plain map branch, each entry added via put(Object,Object)
    @Test
    public void testPutAll_plainMap_putsEachEntry() throws Throwable {
        Map<String, String> plain = new HashMap<String, String>();
        plain.put("k", "v");
        MultiValueMap<String, String> target = new MultiValueMap<String, String>();
        target.putAll(plain);
        assertEquals(1, target.size("k"));
        assertTrue(target.containsValue("k", "v"));
    }

    // covers entrySet(): single entry per key whose value is the backing collection
    @Test
    public void testEntrySet_groupsValuesUnderKey() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        map.put("a", "2");
        Set<Map.Entry<String, Object>> entries = map.entrySet();
        assertEquals(1, entries.size());
        Map.Entry<String, Object> entry = entries.iterator().next();
        assertEquals("a", entry.getKey());
        Collection<?> coll = (Collection<?>) entry.getValue();
        assertEquals(2, coll.size());
    }

    // covers values(): size() delegates to totalSize(), iterator flattens all values
    @Test
    public void testValues_sizeEqualsTotalSizeAndIteratesAll() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        map.put("b", "2");
        map.put("b", "3");
        Collection<Object> values = map.values();
        assertEquals(3, values.size());
        int count = 0;
        for (Object v : values) {
            count++;
        }
        assertEquals(3, count);
    }

    // covers Values.clear(): delegates to MultiValueMap.this.clear()
    @Test
    public void testValues_clearClearsMap() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        map.values().clear();
        assertTrue(map.isEmpty());
    }

    // covers containsValue(key,value): coll == null branch
    @Test
    public void testContainsValueKeyPair_keyAbsent_returnsFalse() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        assertFalse(map.containsValue("missing", "v"));
    }

    // covers containsValue(key,value): coll != null, value not present
    @Test
    public void testContainsValueKeyPair_valueAbsent_returnsFalse() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        assertFalse(map.containsValue("a", "2"));
    }

    // covers containsValue(key,value): coll != null, value present
    @Test
    public void testContainsValueKeyPair_present_returnsTrue() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        assertTrue(map.containsValue("a", "1"));
    }

    // covers getCollection(): key absent returns null
    @Test
    public void testGetCollection_keyAbsent_returnsNull() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        assertNull(map.getCollection("missing"));
    }

    // covers getCollection(): key present returns the stored collection
    @Test
    public void testGetCollection_keyPresent_returnsCollection() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        Collection<String> coll = map.getCollection("a");
        assertNotNull(coll);
        assertEquals(1, coll.size());
    }

    // covers size(key): coll == null branch returns zero
    @Test
    public void testSizeKey_keyAbsent_returnsZero() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        assertEquals(0, map.size("missing"));
    }

    // covers size(key): coll != null branch returns collection size
    @Test
    public void testSizeKey_keyPresent_returnsCount() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        map.put("a", "2");
        assertEquals(2, map.size("a"));
    }

    // covers putAll(key,values): values == null branch returns false
    @Test
    public void testPutAllKeyCollection_nullValues_returnsFalse() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        boolean changed = map.putAll("a", (Collection<String>) null);
        assertFalse(changed);
    }

    // covers putAll(key,values): values.size() == 0 branch returns false
    @Test
    public void testPutAllKeyCollection_emptyValues_returnsFalse() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        boolean changed = map.putAll("a", new ArrayList<String>());
        assertFalse(changed);
    }

    // covers putAll(key,values): coll == null branch, creates and stores collection
    @Test
    public void testPutAllKeyCollection_newKey_createsCollectionReturnsTrue() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        List<String> values = new ArrayList<String>();
        values.add("1");
        values.add("2");
        boolean changed = map.putAll("a", values);
        assertTrue(changed);
        assertEquals(2, map.size("a"));
    }

    // covers putAll(key,values): coll != null branch, addAll on existing collection
    @Test
    public void testPutAllKeyCollection_existingKey_addsAllReturnsTrue() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        List<String> values = new ArrayList<String>();
        values.add("2");
        values.add("3");
        boolean changed = map.putAll("a", values);
        assertTrue(changed);
        assertEquals(3, map.size("a"));
    }

    // covers iterator(Object): !containsKey(key) branch returns an empty iterator
    @Test
    public void testIteratorKey_keyAbsent_returnsEmptyIterator() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        Iterator<String> it = map.iterator("missing");
        assertFalse(it.hasNext());
    }

    // covers ValuesIterator.remove(): values.isEmpty() -> key removed from the map
    @Test
    public void testIteratorKey_removeLastValue_removesKey() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        Iterator<String> it = map.iterator("a");
        assertTrue(it.hasNext());
        assertEquals("1", it.next());
        it.remove();
        assertNull(map.get("a"));
    }

    // covers iterator(): keyIterator exhausted on first call, no elements produced
    @Test
    public void testIteratorNoArg_emptyMap_hasNoNext() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        Iterator<Map.Entry<String, String>> it = map.iterator();
        assertFalse(it.hasNext());
    }

    // covers iterator(): multiple values for a key are flattened into separate entries
    @Test
    public void testIteratorNoArg_flattensMultipleValues() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        map.put("a", "2");
        Iterator<Map.Entry<String, String>> it = map.iterator();
        int count = 0;
        while (it.hasNext()) {
            Map.Entry<String, String> e = it.next();
            assertEquals("a", e.getKey());
            count++;
        }
        assertEquals(2, count);
    }

    // covers iterator()'s Entry.setValue(): must throw per javadoc contract
    @Test
    public void testIteratorNoArg_entrySetValue_throwsUnsupportedOperationException() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        map.put("a", "1");
        Iterator<Map.Entry<String, String>> it = map.iterator();
        Map.Entry<String, String> entry = it.next();
        try {
            entry.setValue("x");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // covers totalSize(): zero iterations on empty map and accumulation over multiple keys
    @Test
    public void testTotalSize_emptyAndWithMultipleKeys() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        assertEquals(0, map.totalSize());
        map.put("a", "1");
        map.put("a", "2");
        map.put("b", "3");
        assertEquals(3, map.totalSize());
    }

    // covers createCollection(): default factory instantiates an empty ArrayList
    @Test
    public void testCreateCollection_returnsEmptyArrayList() throws Throwable {
        MultiValueMap<String, String> map = new MultiValueMap<String, String>();
        Collection<String> coll = map.createCollection(5);
        assertNotNull(coll);
        assertTrue(coll.isEmpty());
        assertTrue(coll instanceof ArrayList);
    }
}
