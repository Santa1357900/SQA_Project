package org.apache.commons.collections4.trie;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;

public class AbstractPatriciaTrieTest {

    private static class ConcretePatriciaTrie extends AbstractPatriciaTrie<String, String> {
        private static final long serialVersionUID = 1L;

        public ConcretePatriciaTrie() {
            super(new StringKeyAnalyzer());
        }

        public ConcretePatriciaTrie(Map<? extends String, ? extends String> m) {
            super(new StringKeyAnalyzer(), m);
        }

        @Override
        public int lengthInBits(String key) {
            return key.length() * 16;
        }

        @Override
        public boolean isBitSet(String key, int bitIndex, int lengthInBits) {
            if (bitIndex < 0 || bitIndex >= lengthInBits) {
                return false;
            }
            int charIndex = bitIndex / 16;
            int bit = 15 - (bitIndex % 16);
            return (key.charAt(charIndex) & (1 << bit)) != 0;
        }

        @Override
        public int bitIndex(String key1, String key2) {
            int maxLength = Math.max(lengthInBits(key1), lengthInBits(key2));
            for (int i = 0; i < maxLength; i++) {
                if (isBitSet(key1, i, maxLength) != isBitSet(key2, i, maxLength)) {
                    return i;
                }
            }
            return KeyAnalyzer.NULL_BIT;
        }

        @Override
        public boolean compareKeys(String key1, String key2) {
            if (key1 == null || key2 == null) {
                return key1 == key2;
            }
            return key1.equals(key2);
        }

        @Override
        public String castKey(Object key) {
            return (String) key;
        }
    }

    private static class StringKeyAnalyzer implements KeyAnalyzer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public int lengthInBits(String key) {
            return key.length() * 16;
        }

        @Override
        public boolean isBitSet(String key, int bitIndex, int lengthInBits) {
            if (bitIndex < 0 || bitIndex >= lengthInBits) {
                return false;
            }
            int charIndex = bitIndex / 16;
            int bit = 15 - (bitIndex % 16);
            return (key.charAt(charIndex) & (1 << bit)) != 0;
        }

        @Override
        public int bitIndex(String key1, int offsetInBits, int lengthInBits, String otherKey, int otherOffsetInBits, int otherLengthInBits) {
            int max = Math.max(lengthInBits, otherLengthInBits);
            for (int i = 0; i < max; i++) {
                if (isBitSet(key1, offsetInBits + i, offsetInBits + lengthInBits) !=
                    isBitSet(otherKey, otherOffsetInBits + i, otherOffsetInBits + otherLengthInBits)) {
                    return i;
                }
            }
            return KeyAnalyzer.NULL_BIT;
        }

        @Override
        public boolean isPrefix(String prefix, int offsetInBits, int lengthInBits, String key) {
            if (key == null) {
                return false;
            }
            int bitLen = lengthInBits(key);
            if (offsetInBits + lengthInBits > bitLen) {
                return false;
            }
            for (int i = 0; i < lengthInBits; i++) {
                if (isBitSet(prefix, offsetInBits + i, offsetInBits + lengthInBits) !=
                    isBitSet(key, i, bitLen)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int compare(String k1, String k2) {
            if (k1 == null) {
                return k2 == null ? 0 : -1;
            }
            if (k2 == null) {
                return 1;
            }
            return k1.compareTo(k2);
        }
    }

    @Test
    public void testPutAndGet() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        assertNull(trie.put("A", "Apple"));
        assertEquals(1, trie.size());
        assertEquals("Apple", trie.get("A"));
        assertEquals("Apple", trie.put("A", "Ape"));
        assertEquals("Ape", trie.get("A"));
        assertEquals(1, trie.size());
    }

    @Test(expected = NullPointerException.class)
    public void testPutNullKey() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.put(null, "Value");
    }

    @Test
    public void testContainsKey() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        assertFalse(trie.containsKey("A"));
        assertFalse(trie.containsKey(null));
        trie.put("A", "Apple");
        assertTrue(trie.containsKey("A"));
        assertFalse(trie.containsKey("B"));
        assertFalse(trie.containsKey(Integer.valueOf(123)));
    }

    @Test
    public void testRemove() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        assertNull(trie.remove(null));
        assertNull(trie.remove("NonExistent"));

        trie.put("A", "Apple");
        trie.put("B", "Banana");
        trie.put("C", "Cherry");

        assertEquals("Banana", trie.remove("B"));
        assertEquals(2, trie.size());
        assertNull(trie.get("B"));
        assertFalse(trie.containsKey("B"));

        assertEquals("Apple", trie.remove("A"));
        assertEquals("Cherry", trie.remove("C"));
        assertEquals(0, trie.size());
        assertTrue(trie.isEmpty());
    }

    @Test
    public void testClear() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.put("A", "Apple");
        trie.put("B", "Banana");
        trie.clear();
        assertEquals(0, trie.size());
        assertTrue(trie.isEmpty());
        assertNull(trie.get("A"));
    }

    @Test
    public void testFirstAndLastKey() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        boolean noSuchElemCaught = false;
        try {
            trie.firstKey();
        } catch (NoSuchElementException e) {
            noSuchElemCaught = true;
        }
        assertTrue(noSuchElemCaught);

        noSuchElemCaught = false;
        try {
            trie.lastKey();
        } catch (NoSuchElementException e) {
            noSuchElemCaught = true;
        }
        assertTrue(noSuchElemCaught);

        trie.put("Banana", "B");
        trie.put("Apple", "A");
        trie.put("Cherry", "C");

        assertEquals("Apple", trie.firstKey());
        assertEquals("Cherry", trie.lastKey());
    }

    @Test(expected = NullPointerException.class)
    public void testNextKeyNull() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.nextKey(null);
    }

    @Test(expected = NullPointerException.class)
    public void testPreviousKeyNull() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.previousKey(null);
    }

    @Test
    public void testNextAndPreviousKey() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.put("Apple", "A");
        trie.put("Banana", "B");
        trie.put("Cherry", "C");

        assertEquals("Banana", trie.nextKey("Apple"));
        assertEquals("Cherry", trie.nextKey("Banana"));
        assertNull(trie.nextKey("Cherry"));
        assertNull(trie.nextKey("Unknown"));

        assertEquals("Banana", trie.previousKey("Cherry"));
        assertEquals("Apple", trie.previousKey("Banana"));
        assertNull(trie.previousKey("Apple"));
        assertNull(trie.previousKey("Unknown"));
    }

    @Test
    public void testSelect() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.put("A", "Apple");
        trie.put("B", "Banana");

        Map.Entry<String, String> entry = trie.select("A");
        assertNotNull(entry);
        assertEquals("A", entry.getKey());
        assertEquals("Apple", trie.selectValue("A"));
        assertEquals("A", trie.selectKey("A"));
    }

    @Test
    public void testViews() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.put("A", "Apple");
        trie.put("B", "Banana");

        Set<String> keySet = trie.keySet();
        assertNotNull(keySet);
        assertEquals(2, keySet.size());
        assertTrue(keySet.contains("A"));
        keySet.remove("A");
        assertEquals(1, trie.size());

        Collection<String> values = trie.values();
        assertNotNull(values);
        assertEquals(1, values.size());
        assertTrue(values.contains("Banana"));
        values.remove("Banana");
        assertEquals(0, trie.size());

        trie.put("C", "Cherry");
        Set<Map.Entry<String, String>> entrySet = trie.entrySet();
        assertNotNull(entrySet);
        assertEquals(1, entrySet.size());
        Iterator<Map.Entry<String, String>> it = entrySet.iterator();
        assertTrue(it.hasNext());
        Map.Entry<String, String> entry = it.next();
        assertEquals("C", entry.getKey());
        it.remove();
        assertEquals(0, trie.size());
    }

    @Test
    public void testSubMapsAndRangeMaps() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.put("A", "Apple");
        trie.put("B", "Banana");
        trie.put("C", "Cherry");
        trie.put("D", "Date");

        SortedMap<String, String> subMap = trie.subMap("B", "D");
        assertEquals(2, subMap.size());
        assertTrue(subMap.containsKey("B"));
        assertTrue(subMap.containsKey("C"));
        assertFalse(subMap.containsKey("D"));

        SortedMap<String, String> headMap = trie.headMap("C");
        assertEquals(2, headMap.size());
        assertTrue(headMap.containsKey("A"));
        assertTrue(headMap.containsKey("B"));

        SortedMap<String, String> tailMap = trie.tailMap("C");
        assertEquals(2, tailMap.size());
        assertTrue(tailMap.containsKey("C"));
        assertTrue(tailMap.containsKey("D"));
    }

    @Test
    public void testPrefixMap() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.put("Test1", "1");
        trie.put("Test2", "2");
        trie.put("Other", "3");

        SortedMap<String, String> prefixMap = trie.prefixMap("Test");
        assertEquals(2, prefixMap.size());
        assertTrue(prefixMap.containsKey("Test1"));
        assertTrue(prefixMap.containsKey("Test2"));
        assertFalse(prefixMap.containsKey("Other"));
    }

    @Test
    public void testIteratorConcurrentModification() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.put("A", "Apple");
        trie.put("B", "Banana");

        Iterator<String> it = trie.keySet().iterator();
        trie.put("C", "Cherry");

        boolean exceptionThrown = false;
        try {
            it.next();
        } catch (ConcurrentModificationException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testIteratorRemoveWithoutNext() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.put("A", "Apple");

        Iterator<String> it = trie.keySet().iterator();
        boolean exceptionThrown = false;
        try {
            it.remove();
        } catch (IllegalStateException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testMapIterator() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.put("A", "Apple");
        trie.put("B", "Banana");

        OrderedMapIterator<String, String> mapIt = trie.mapIterator();
        assertFalse(mapIt.hasPrevious());
        assertTrue(mapIt.hasNext());
        assertEquals("A", mapIt.next());
        assertTrue(mapIt.hasPrevious());
        assertEquals("A", mapIt.getKey());
        assertEquals("Apple", mapIt.getValue());
        assertEquals("Apple", mapIt.setValue("NewApple"));
        assertEquals("NewApple", mapIt.getValue());

        assertEquals("B", mapIt.next());
        assertEquals("B", mapIt.previous());
    }

    @Test
    public void testCeilingAndFloorAndHigherAndLower() throws Throwable {
        ConcretePatriciaTrie trie = new ConcretePatriciaTrie();
        trie.put("B", "Banana");
        trie.put("D", "Date");

        assertNotNull(trie.ceilingEntry("B"));
        assertNotNull(trie.floorEntry("D"));
        assertNotNull(trie.higherEntry("B"));
        assertNotNull(trie.lowerEntry("D"));
    }
}