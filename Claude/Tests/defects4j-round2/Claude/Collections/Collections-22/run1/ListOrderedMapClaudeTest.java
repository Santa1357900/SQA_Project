package org.apache.commons.collections4.map;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.collections4.OrderedMapIterator;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ListOrderedMapClaudeTest {

    private ListOrderedMap<String, String> map;

    @Before
    public void setUp() throws Throwable {
        map = new ListOrderedMap<String, String>();
    }

    // static factory wraps a map and preserves its entries
    @Test
    public void testListOrderedMapFactory_createsEquivalentMap() throws Throwable {
        Map<String, String> src = new HashMap<String, String>();
        src.put("X", "9");
        ListOrderedMap<String, String> lom = ListOrderedMap.listOrderedMap(src);
        assertEquals(1, lom.size());
        assertTrue(lom.containsKey("X"));
    }

    // static factory with null map -> IllegalArgumentException per javadoc
    @Test
    public void testListOrderedMapFactory_nullMap_throwsIllegalArgumentException() throws Throwable {
        Map<String, String> nullMap = null;
        try {
            ListOrderedMap.listOrderedMap(nullMap);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // no-arg constructor produces an empty map
    @Test
    public void testDefaultConstructor_emptyMap() throws Throwable {
        ListOrderedMap<String, String> m = new ListOrderedMap<String, String>();
        assertEquals(0, m.size());
        assertTrue(m.isEmpty());
    }

    // protected constructor (same package) wraps the given map and copies its keys
    @Test
    public void testProtectedConstructor_wrapsExistingMapAndPreservesKeys() throws Throwable {
        Map<String, String> src = new HashMap<String, String>();
        src.put("A", "1");
        ListOrderedMap<String, String> lom = new ListOrderedMap<String, String>(src);
        assertEquals(1, lom.size());
        assertEquals("A", lom.firstKey());
        assertEquals("1", lom.get("A"));
    }

    // mapIterator: hasNext/next in insert order, getValue, and remove()
    @Test
    public void testMapIterator_iteratesInInsertOrderAndSupportsRemove() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        OrderedMapIterator<String, String> it = map.mapIterator();
        assertTrue(it.hasNext());
        assertEquals("A", it.next());
        assertEquals("1", it.getValue());
        it.remove();
        assertEquals(1, map.size());
        assertFalse(map.containsKey("A"));
    }

    // mapIterator: setValue after next() updates underlying map value
    @Test
    public void testMapIterator_setValue_updatesValue() throws Throwable {
        map.put("A", "1");
        OrderedMapIterator<String, String> it = map.mapIterator();
        it.next();
        String old = it.setValue("X");
        assertEquals("1", old);
        assertEquals("X", map.get("A"));
    }

    // mapIterator: getKey before any next() -> IllegalStateException (readable == false)
    @Test
    public void testMapIterator_getKeyBeforeNext_throwsIllegalStateException() throws Throwable {
        OrderedMapIterator<String, String> it = map.mapIterator();
        try {
            it.getKey();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // mapIterator: hasPrevious/previous traverse backwards after reaching the end
    @Test
    public void testMapIterator_previousTraversal_returnsKeysInReverseOrder() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        OrderedMapIterator<String, String> it = map.mapIterator();
        it.next();
        it.next();
        assertTrue(it.hasPrevious());
        assertEquals("B", it.previous());
    }

    // firstKey on non-empty map returns first inserted key
    @Test
    public void testFirstKey_nonEmptyMap_returnsFirstInsertedKey() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        assertEquals("A", map.firstKey());
    }

    // firstKey on empty map -> NoSuchElementException
    @Test
    public void testFirstKey_emptyMap_throwsNoSuchElementException() throws Throwable {
        try {
            map.firstKey();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // lastKey on non-empty map returns last inserted key
    @Test
    public void testLastKey_nonEmptyMap_returnsLastInsertedKey() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        assertEquals("B", map.lastKey());
    }

    // lastKey on empty map -> NoSuchElementException
    @Test
    public void testLastKey_emptyMap_throwsNoSuchElementException() throws Throwable {
        try {
            map.lastKey();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // nextKey: key found and not last -> returns following key
    @Test
    public void testNextKey_existingKeyNotLast_returnsNextKey() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        map.put("C", "3");
        assertEquals("B", map.nextKey("A"));
    }

    // nextKey: key is the last one -> index == size()-1 boundary -> null
    @Test
    public void testNextKey_lastKey_returnsNull() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        assertNull(map.nextKey("B"));
    }

    // nextKey: key not present -> indexOf returns -1 -> null
    @Test
    public void testNextKey_nonExistingKey_returnsNull() throws Throwable {
        map.put("A", "1");
        assertNull(map.nextKey("Z"));
    }

    // previousKey: key found and not first -> returns preceding key
    @Test
    public void testPreviousKey_existingKeyNotFirst_returnsPreviousKey() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        map.put("C", "3");
        assertEquals("B", map.previousKey("C"));
    }

    // previousKey: key is first (index == 0) -> null
    @Test
    public void testPreviousKey_firstKey_returnsNull() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        assertNull(map.previousKey("A"));
    }

    // previousKey: key not present (index == -1) -> null
    @Test
    public void testPreviousKey_nonExistingKey_returnsNull() throws Throwable {
        map.put("A", "1");
        assertNull(map.previousKey("Z"));
    }

    // put: brand new key is appended to the end of insertion order, returns null
    @Test
    public void testPut_newKey_addsToEndOfInsertOrder() throws Throwable {
        assertNull(map.put("A", "1"));
        assertNull(map.put("B", "2"));
        assertEquals("A", map.get(0));
        assertEquals("B", map.get(1));
    }

    // put: re-adding an existing key keeps its position but updates the value
    @Test
    public void testPut_existingKey_doesNotChangeOrder() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        String old = map.put("A", "99");
        assertEquals("1", old);
        assertEquals("A", map.get(0));
        assertEquals("99", map.get("A"));
    }

    // putAll(Map): entries are appended in the supplied map's iteration order
    @Test
    public void testPutAll_mapParam_addsAllEntriesInIterationOrder() throws Throwable {
        Map<String, String> supplied = new LinkedHashMap<String, String>();
        supplied.put("A", "1");
        supplied.put("B", "2");
        map.putAll(supplied);
        assertEquals("A", map.get(0));
        assertEquals("B", map.get(1));
    }

    // putAll(int,Map): brand new keys are inserted sequentially starting at index
    @Test
    public void testPutAllIndexMap_newKeys_insertedStartingAtIndex() throws Throwable {
        map.put("X", "0");
        map.put("Y", "9");
        Map<String, String> supplied = new LinkedHashMap<String, String>();
        supplied.put("A", "1");
        supplied.put("B", "2");
        map.putAll(1, supplied);
        assertEquals("X", map.get(0));
        assertEquals("A", map.get(1));
        assertEquals("B", map.get(2));
        assertEquals("Y", map.get(3));
    }

    // BUG: when an existing key's old value was null, put() also returns null, so
    // putAll(int,Map) cannot distinguish "new key" from "existing key with null value".
    // Per the method's own contract, the next entry must be inserted right after the
    // re-inserted key (indexOf(key)+1), not simply at the pre-shift index+1.
    @Test
    public void testPutAllIndexMap_keyExistsWithNullValue_insertsNextEntryAtCorrectPosition() throws Throwable {
        map.put("A", null);
        map.put("B", "1");
        map.put("C", "2");
        map.put("D", "3");
        Map<String, String> supplied = new LinkedHashMap<String, String>();
        supplied.put("A", "99");
        supplied.put("E", "77");
        map.putAll(2, supplied);
        assertEquals(2, map.indexOf("E"));
        assertEquals("E", map.get(2));
    }

    // remove(key): existing key removed from both map and order, returns old value
    @Test
    public void testRemove_existingKey_removesFromMapAndList() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        String val = map.remove("A");
        assertEquals("1", val);
        assertEquals(1, map.size());
        assertFalse(map.containsKey("A"));
        assertEquals("B", map.get(0));
    }

    // remove(key): non-existing key leaves map untouched, returns null
    @Test
    public void testRemove_nonExistingKey_returnsNull() throws Throwable {
        map.put("A", "1");
        assertNull(map.remove("Z"));
        assertEquals(1, map.size());
    }

    // clear empties both the underlying map and the insert order list
    @Test
    public void testClear_emptiesMapAndList() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    // keySet view iterates in insertion order
    @Test
    public void testKeySet_reflectsInsertOrder() throws Throwable {
        map.put("B", "2");
        map.put("A", "1");
        Set<String> keys = map.keySet();
        Iterator<String> it = keys.iterator();
        assertEquals("B", it.next());
        assertEquals("A", it.next());
        assertEquals(2, keys.size());
    }

    // keyList is unmodifiable -> add() throws UnsupportedOperationException
    @Test
    public void testKeyList_isUnmodifiable_throwsUnsupportedOperationException() throws Throwable {
        map.put("A", "1");
        List<String> keyList = map.keyList();
        try {
            keyList.add("B");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // values view iterates in insertion order
    @Test
    public void testValues_reflectsInsertOrder() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        Collection<String> vals = map.values();
        Iterator<String> it = vals.iterator();
        assertEquals("1", it.next());
        assertEquals("2", it.next());
        assertFalse(it.hasNext());
    }

    // valueList.set(index,value) updates the value and returns the previous one
    @Test
    public void testValueList_setValue_updatesValueAndReturnsOld() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        List<String> vl = map.valueList();
        String old = vl.set(0, "X");
        assertEquals("1", old);
        assertEquals("X", map.get("A"));
    }

    // valueList.remove(index) removes the corresponding mapping entirely
    @Test
    public void testValueList_removeByIndex_removesEntry() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        List<String> vl = map.valueList();
        String removed = vl.remove(0);
        assertEquals("1", removed);
        assertEquals(1, map.size());
        assertFalse(map.containsKey("A"));
    }

    // entrySet iterates in insertion order with correct key/value pairs
    @Test
    public void testEntrySet_iterationOrder() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        Iterator<Map.Entry<String, String>> it = map.entrySet().iterator();
        Map.Entry<String, String> e1 = it.next();
        assertEquals("A", e1.getKey());
        assertEquals("1", e1.getValue());
        Map.Entry<String, String> e2 = it.next();
        assertEquals("B", e2.getKey());
    }

    // toString on empty map returns "{}"
    @Test
    public void testToString_emptyMap_returnsBraces() throws Throwable {
        assertEquals("{}", map.toString());
    }

    // toString on non-empty map contains "key=value"
    @Test
    public void testToString_nonEmptyMap_containsKeyValue() throws Throwable {
        map.put("A", "1");
        assertTrue(map.toString().contains("A=1"));
    }

    // get(int): valid indexes return the keys at those positions
    @Test
    public void testGetByIndex_validIndex_returnsKey() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        assertEquals("A", map.get(0));
        assertEquals("B", map.get(1));
    }

    // get(int): invalid index -> IndexOutOfBoundsException
    @Test
    public void testGetByIndex_invalidIndex_throwsIndexOutOfBoundsException() throws Throwable {
        try {
            map.get(0);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // getValue(int): returns the value stored at that index's key
    @Test
    public void testGetValueByIndex_returnsCorrectValue() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        assertEquals("1", map.getValue(0));
        assertEquals("2", map.getValue(1));
    }

    // indexOf: existing key returns its position
    @Test
    public void testIndexOf_existingKey_returnsIndex() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        assertEquals(1, map.indexOf("B"));
    }

    // indexOf: non-existing key returns -1
    @Test
    public void testIndexOf_nonExistingKey_returnsMinusOne() throws Throwable {
        map.put("A", "1");
        assertEquals(-1, map.indexOf("Z"));
    }

    // setValue(int,V): updates value at the index, keeps key order, returns old value
    @Test
    public void testSetValueByIndex_updatesValueKeepsOrder() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        String old = map.setValue(0, "X");
        assertEquals("1", old);
        assertEquals("X", map.get("A"));
        assertEquals("A", map.get(0));
    }

    // put(int,K,V): new key is inserted directly at the requested index
    @Test
    public void testPutIndexKeyValue_newKey_insertsAtIndex() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        map.put("C", "3");
        String old = map.put(1, "X", "newX");
        assertNull(old);
        assertEquals("A", map.get(0));
        assertEquals("X", map.get(1));
        assertEquals("B", map.get(2));
        assertEquals("C", map.get(3));
    }

    // put(int,K,V): existing key whose original position is before the target index (pos < index)
    @Test
    public void testPutIndexKeyValue_existingKeyPosLessThanIndex_adjustsPositionDown() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        map.put("C", "3");
        map.put("D", "4");
        String old = map.put(3, "A", "newA");
        assertEquals("1", old);
        assertEquals("A", map.get(2));
        assertEquals("D", map.get(3));
    }

    // put(int,K,V): existing key whose original position is not before the target index (pos >= index)
    @Test
    public void testPutIndexKeyValue_existingKeyPosGreaterEqualIndex_insertsAtIndex() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        map.put("C", "3");
        map.put("D", "4");
        String old = map.put(0, "C", "newC");
        assertEquals("3", old);
        assertEquals("C", map.get(0));
        assertEquals("A", map.get(1));
        assertEquals("B", map.get(2));
    }

    // remove(int): removes the entry located at the given index
    @Test
    public void testRemoveByIndex_removesCorrectEntry() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        map.put("C", "3");
        String removed = map.remove(1);
        assertEquals("2", removed);
        assertEquals(2, map.size());
        assertEquals("C", map.get(1));
    }

    // asList is equivalent to keyList, reflecting the same insertion order
    @Test
    public void testAsList_returnsSameAsKeyList() throws Throwable {
        map.put("A", "1");
        map.put("B", "2");
        List<String> asList = map.asList();
        List<String> keyList = map.keyList();
        assertEquals(keyList, asList);
        assertEquals("A", asList.get(0));
    }
}
