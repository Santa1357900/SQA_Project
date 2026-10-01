package org.apache.commons.collections.map;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.collections.MapIterator;
import org.apache.commons.collections.ResettableIterator;

import org.junit.Test;
import static org.junit.Assert.*;

public class Flat3MapClaudeTest {

    // Constructor: default constructor produces an empty flat map
    @Test
    public void testConstructorDefault_createsEmptyMap() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    // Constructor: copies entries from supplied map
    @Test
    public void testConstructorWithMap_copiesEntries() throws Throwable {
        Map src = new HashMap();
        src.put("x", "1");
        src.put("y", "2");
        Flat3Map map = new Flat3Map(src);
        assertEquals(2, map.size());
        assertEquals("1", map.get("x"));
    }

    // Constructor: null map throws NullPointerException (per javadoc)
    @Test
    public void testConstructorWithMap_null_throwsNPE() throws Throwable {
        try {
            new Flat3Map(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // get: empty map returns null (size==0, no case matches)
    @Test
    public void testGet_emptyMap_returnsNull() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertNull(map.get("a"));
    }

    // get: null key branch, match found
    @Test
    public void testGet_nullKeyMatch_returnsValue() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put(null, "v1");
        assertEquals("v1", map.get(null));
    }

    // get: null key branch, no match falls through to return null
    @Test
    public void testGet_nullKeyNoMatch_returnsNull() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        assertNull(map.get(null));
    }

    // get: non-null key, covers case3/case2/case1 matches and no-match
    @Test
    public void testGet_existingAndNonExistingKeys() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        assertEquals("1", map.get("a"));
        assertEquals("2", map.get("b"));
        assertEquals("3", map.get("c"));
        assertNull(map.get("d"));
    }

    // get: delegate mode (size>3) forwards to delegate map
    @Test
    public void testGet_delegateMode_returnsValue() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        map.put("d", "4");
        assertEquals("4", map.get("d"));
        assertEquals("1", map.get("a"));
    }

    // size: flat mode counter and delegate mode delegation
    @Test
    public void testSize_flatAndDelegateModes() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertEquals(0, map.size());
        map.put("a", "1");
        assertEquals(1, map.size());
        map.put("b", "2");
        map.put("c", "3");
        map.put("d", "4");
        assertEquals(4, map.size());
    }

    // isEmpty: true when size 0, false after adding
    @Test
    public void testIsEmpty_trueThenFalse() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertTrue(map.isEmpty());
        map.put("a", "1");
        assertFalse(map.isEmpty());
    }

    // containsKey: null key branch and non-null key branch
    @Test
    public void testContainsKey_nullKeyAndExisting() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertFalse(map.containsKey(null));
        map.put(null, "v");
        map.put("a", "1");
        assertTrue(map.containsKey(null));
        assertTrue(map.containsKey("a"));
        assertFalse(map.containsKey("z"));
    }

    // containsKey: delegate mode forwards call
    @Test
    public void testContainsKey_delegateMode() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        map.put("d", "4");
        assertTrue(map.containsKey("d"));
        assertFalse(map.containsKey("z"));
    }

    // containsValue: null value branch, non-null branch, and delegate mode
    @Test
    public void testContainsValue_nullAndExistingAndDelegate() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", null);
        assertTrue(map.containsValue(null));
        assertFalse(map.containsValue("missing"));
        map.put("b", "2");
        map.put("c", "3");
        map.put("d", "4");
        assertTrue(map.containsValue("4"));
    }

    // put: new key in empty map returns null, increments size
    @Test
    public void testPut_newKeyReturnsNull() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertNull(map.put("a", "1"));
        assertEquals(1, map.size());
    }

    // put: updating existing key returns old value, size unchanged
    @Test
    public void testPut_existingKeyReturnsOldValue() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        Object old = map.put("a", "2");
        assertEquals("1", old);
        assertEquals("2", map.get("a"));
        assertEquals(1, map.size());
    }

    // put: null key insert then update exercises key==null branch
    @Test
    public void testPut_nullKeyUpdate() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put(null, "1");
        Object old = map.put(null, "2");
        assertEquals("1", old);
        assertEquals(1, map.size());
    }

    // put: fourth distinct key triggers conversion to delegate map
    @Test
    public void testPut_fourthEntryConvertsToDelegateMode() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        assertNull(map.put("d", "4"));
        assertEquals(4, map.size());
        assertEquals("4", map.get("d"));
    }

    // putAll: empty source map is a no-op, then small map stays in flat mode
    @Test
    public void testPutAll_emptyThenSmallMapFlatMode() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.putAll(new HashMap());
        assertEquals(0, map.size());
        Map src = new HashMap();
        src.put("a", "1");
        src.put("b", "2");
        map.putAll(src);
        assertEquals(2, map.size());
        assertEquals("1", map.get("a"));
    }

    // putAll: source with size >=4 triggers conversion to delegate map
    @Test
    public void testPutAll_largeMapTriggersDelegate() throws Throwable {
        Flat3Map map = new Flat3Map();
        Map src = new HashMap();
        src.put("a", "1");
        src.put("b", "2");
        src.put("c", "3");
        src.put("d", "4");
        map.putAll(src);
        assertEquals(4, map.size());
        assertEquals("4", map.get("d"));
    }

    // putAll: null map throws NullPointerException (per javadoc)
    @Test
    public void testPutAll_null_throwsNPE() throws Throwable {
        Flat3Map map = new Flat3Map();
        try {
            map.putAll(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }



    // remove: empty map (size==0) and non-matching key both return null
    @Test
    public void testRemove_nonExistingAndEmptyMapReturnsNull() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertNull(map.remove("a"));
        map.put("a", "1");
        assertNull(map.remove("z"));
        assertEquals(1, map.size());
    }

    // remove: null key branch removes and shrinks to size 0
    @Test
    public void testRemove_nullKey() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put(null, "1");
        assertEquals("1", map.remove(null));
        assertEquals(0, map.size());
    }

    // remove: delegate mode forwards removal to delegate map
    @Test
    public void testRemove_delegateMode() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        map.put("d", "4");
        assertEquals("4", map.remove("d"));
        assertEquals(3, map.size());
    }

    // clear: flat mode resets fields and size to zero
    @Test
    public void testClear_flatModeResetsToEmpty() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    // clear: delegate mode clears and switches back to flat mode
    @Test
    public void testClear_delegateModeSwitchesBackToFlat() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        map.put("d", "4");
        map.clear();
        assertEquals(0, map.size());
        map.put("x", "1");
        assertEquals(1, map.size());
    }

    // mapIterator: empty map yields an iterator with no elements
    @Test
    public void testMapIterator_emptyMapReturnsEmptyIterator() throws Throwable {
        Flat3Map map = new Flat3Map();
        MapIterator it = map.mapIterator();
        assertFalse(it.hasNext());
    }

    // mapIterator: iterates all keys, getKey/getValue reflect current entry
    @Test
    public void testMapIterator_iterateAllKeysAndValues() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        MapIterator it = map.mapIterator();
        int count = 0;
        while (it.hasNext()) {
            Object k = it.next();
            assertEquals(map.get(k), it.getValue());
            count++;
        }
        assertEquals(3, count);
    }

    // mapIterator: getKey before next() throws IllegalStateException
    @Test
    public void testMapIterator_getKeyBeforeNext_throwsIllegalState() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        MapIterator it = map.mapIterator();
        try {
            it.getKey();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // mapIterator: next() beyond available elements throws NoSuchElementException
    @Test
    public void testMapIterator_nextBeyondEnd_throwsNoSuchElement() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        MapIterator it = map.mapIterator();
        it.next();
        try {
            it.next();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // mapIterator: remove() deletes current entry from the backing map
    @Test
    public void testMapIterator_remove() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        MapIterator it = map.mapIterator();
        it.next();
        it.remove();
        assertEquals(1, map.size());
    }

    // mapIterator: reset() allows re-iterating from the start
    @Test
    public void testMapIterator_resetAllowsReiteration() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        MapIterator it = map.mapIterator();
        it.next();
        it.next();
        assertFalse(it.hasNext());
        ((ResettableIterator) it).reset();
        assertTrue(it.hasNext());
    }

    // Bug check: setValue on the last-iterated entry (nextIndex==3) must only
    // change that single entry, not fall through and overwrite entries 1 and 2.
    @Test
    public void testMapIterator_setValueOnLastEntry_onlyAffectsThatEntry() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        MapIterator it = map.mapIterator();
        it.next();
        it.next();
        it.next();
        it.setValue("CHANGED");
        assertEquals("1", map.get("a"));
        assertEquals("2", map.get("b"));
        assertEquals("CHANGED", map.get("c"));
    }

    // Bug check: setValue on the middle entry (nextIndex==2) must only change
    // that entry, not fall through and overwrite entry 1.
    @Test
    public void testMapIterator_setValueOnMiddleEntry_onlyAffectsThatEntry() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        MapIterator it = map.mapIterator();
        it.next();
        it.next();
        Object old = it.setValue("CHANGED");
        assertEquals("2", old);
        assertEquals("1", map.get("a"));
        assertEquals("CHANGED", map.get("b"));
        assertEquals("3", map.get("c"));
    }

    // entrySet: size reflects map size and iterator.remove() removes entry
    @Test
    public void testEntrySet_sizeAndIteratorRemove() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        assertEquals(2, map.entrySet().size());
        Iterator it = map.entrySet().iterator();
        it.next();
        it.remove();
        assertEquals(1, map.size());
    }

    // entrySet.remove: non Map.Entry object returns false without side effects
    @Test
    public void testEntrySet_removeNonEntryObjectReturnsFalse() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        Set entries = map.entrySet();
        boolean result = entries.remove("not an entry");
        assertFalse(result);
        assertEquals(1, map.size());
    }

    // Bug check: Map.Entry.setValue on the last entry must only change that entry
    @Test
    public void testEntrySetIterator_setValueOnLastEntry_onlyAffectsThatEntry() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        Iterator it = map.entrySet().iterator();
        it.next();
        it.next();
        Map.Entry e3 = (Map.Entry) it.next();
        e3.setValue("CHANGED");
        assertEquals("1", map.get("a"));
        assertEquals("2", map.get("b"));
        assertEquals("CHANGED", map.get("c"));
    }

    // keySet: size, contains() for present/absent keys
    @Test
    public void testKeySet_containsAndSize() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        Set keys = map.keySet();
        assertEquals(2, keys.size());
        assertTrue(keys.contains("a"));
        assertFalse(keys.contains("z"));
    }

    // values: contains() finds value, iterator yields correct count
    @Test
    public void testValues_containsAndIteration() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        Collection vals = map.values();
        assertTrue(vals.contains("1"));
        int count = 0;
        Iterator it = vals.iterator();
        while (it.hasNext()) {
            it.next();
            count++;
        }
        assertEquals(2, count);
    }

    // clone: produces an independent shallow copy with same entries
    @Test
    public void testClone_producesIndependentShallowCopy() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        Flat3Map cloned = (Flat3Map) map.clone();
        assertEquals(map.size(), cloned.size());
        assertEquals("1", cloned.get("a"));
        cloned.put("c", "3");
        assertFalse(map.containsKey("c"));
    }

    // equals: equal entries with a HashMap, size mismatch, and non-Map object
    @Test
    public void testEquals_variousCases() throws Throwable {
        Flat3Map map1 = new Flat3Map();
        map1.put("a", "1");
        map1.put("b", "2");
        Map same = new HashMap();
        same.put("a", "1");
        same.put("b", "2");
        assertTrue(map1.equals(same));
        Map bigger = new HashMap();
        bigger.put("a", "1");
        bigger.put("b", "2");
        bigger.put("c", "3");
        assertFalse(map1.equals(bigger));
        assertFalse(map1.equals("not a map"));
    }

    // hashCode: must match the standard Map hashCode contract (sum of entry hashes)
    @Test
    public void testHashCode_matchesStandardMapContract() throws Throwable {
        Flat3Map map1 = new Flat3Map();
        map1.put("a", "1");
        map1.put("b", "2");
        Map map2 = new HashMap();
        map2.put("a", "1");
        map2.put("b", "2");
        assertEquals(map2.hashCode(), map1.hashCode());
    }

    // toString: empty map formats as "{}" and single entry as "{key=value}"
    @Test
    public void testToString_emptyAndSingleEntry() throws Throwable {
        Flat3Map map = new Flat3Map();
        assertEquals("{}", map.toString());
        map.put("a", "1");
        assertEquals("{a=1}", map.toString());
    }

    // toString: multiple entries print key3 first (reverse insertion order)
    @Test
    public void testToString_multipleEntriesInReverseInsertionOrder() throws Throwable {
        Flat3Map map = new Flat3Map();
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        assertEquals("{c=3,b=2,a=1}", map.toString());
    }
}
