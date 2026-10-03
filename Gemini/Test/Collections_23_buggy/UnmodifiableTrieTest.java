package org.apache.commons.collections4.trie;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.apache.commons.collections4.OrderedMapIterator;
import org.apache.commons.collections4.Trie;
import org.junit.Test;

public class UnmodifiableTrieTest {

    private static class DummyTrie<K, V> implements Trie<K, V> {
        private final Map<K, V> map = new HashMap<K, V>();

        public Comparator<? super K> comparator() {
            return null;
        }

        public SortedMap<K, V> prefixMap(K key) {
            return null;
        }

        public K nextKey(K key) {
            return null;
        }

        public K previousKey(K key) {
            return null;
        }

        public OrderedMapIterator<K, V> mapIterator() {
            return null;
        }

        public int size() {
            return map.size();
        }

        public boolean isEmpty() {
            return map.isEmpty();
        }

        public boolean containsKey(Object key) {
            return map.containsKey(key);
        }

        public boolean containsValue(Object value) {
            return map.containsValue(value);
        }

        public V get(Object key) {
            return map.get(key);
        }

        public V put(K key, V value) {
            return map.put(key, value);
        }

        public V remove(Object key) {
            return map.remove(key);
        }

        public void putAll(Map<? extends K, ? extends V> m) {
            map.putAll(m);
        }

        public void clear() {
            map.clear();
        }

        public Set<K> keySet() {
            return map.keySet();
        }

        public Collection<V> values() {
            return map.values();
        }

        public Set<java.util.Map.Entry<K, V>> entrySet() {
            return map.entrySet();
        }

        public K firstKey() {
            if (map.isEmpty()) {
                return null;
            }
            return map.keySet().iterator().next();
        }

        public K lastKey() {
            if (map.isEmpty()) {
                return null;
            }
            return map.keySet().iterator().next();
        }

        public SortedMap<K, V> headMap(K toKey) {
            return null;
        }

        public SortedMap<K, V> subMap(K fromKey, K toKey) {
            return null;
        }

        public SortedMap<K, V> tailMap(K fromKey) {
            return null;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNull() throws Throwable {
        new UnmodifiableTrie<String, String>(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFactoryNull() throws Throwable {
        UnmodifiableTrie.unmodifiableTrie(null);
    }

    @Test
    public void testOperationsAndDelegation() throws Throwable {
        DummyTrie<String, String> dummy = new DummyTrie<String, String>();
        dummy.put("key1", "value1");

        UnmodifiableTrie<String, String> trie = UnmodifiableTrie.unmodifiableTrie(dummy);

        assertNotNull(trie);
        assertFalse(trie.isEmpty());
        assertEquals(1, trie.size());
        assertTrue(trie.containsKey("key1"));
        assertFalse(trie.containsKey("key2"));
        assertTrue(trie.containsValue("value1"));
        assertFalse(trie.containsValue("value2"));
        assertEquals("value1", trie.get("key1"));
        assertNull(trie.get("key2"));

        assertEquals("key1", trie.firstKey());
        assertEquals("key1", trie.lastKey());

        assertNull(trie.comparator());
        assertNull(trie.nextKey("key1"));
        assertNull(trie.previousKey("key1"));
        assertNull(trie.prefixMap("key"));
        assertNull(trie.headMap("key1"));
        assertNull(trie.subMap("key1", "key2"));
        assertNull(trie.tailMap("key1"));
        assertNull(trie.mapIterator());

        Set<String> keySet = trie.keySet();
        assertNotNull(keySet);
        try {
            keySet.add("key2");
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        Collection<String> values = trie.values();
        assertNotNull(values);
        try {
            values.add("value2");
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        Set<Map.Entry<String, String>> entrySet = trie.entrySet();
        assertNotNull(entrySet);
        try {
            entrySet.clear();
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        assertEquals(dummy.hashCode(), trie.hashCode());
        assertEquals(trie, trie);
        assertFalse(trie.equals(null));
        assertNotNull(trie.toString());
    }

    @Test
    public void testUnmodifiableMutations() throws Throwable {
        DummyTrie<String, String> dummy = new DummyTrie<String, String>();
        UnmodifiableTrie<String, String> trie = new UnmodifiableTrie<String, String>(dummy);

        try {
            trie.clear();
            fail("clear should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            trie.put("k", "v");
            fail("put should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            Map<String, String> map = new HashMap<String, String>();
            trie.putAll(map);
            fail("putAll should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            trie.remove("k");
            fail("remove should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }
}