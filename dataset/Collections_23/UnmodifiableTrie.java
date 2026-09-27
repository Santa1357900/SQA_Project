ว่าคลาสมี Constructor อะไรบ้าง
   - ❌ ห้ามส่ง `null` ให้ parameter ที่เป็น primitive type (`int`, `double`, `boolean`, `char`, `long`) เด็ดขาด เพราะ javac จะฟ้อง type incompatible
   - ❌ ห้ามเรียกใช้ `private` method / constructor หรือ private field หรือ private inner class (เช่น `Assign` ใน `RemoveUnusedVars`) โดยตรง
   - ❌ ห้ามใช้ `assertEquals(null, val)` ให้ใช้ `assertNull(val)` แทนเสมอ
   - ❌ ห้ามใช้ `assertEquals(Double.NaN, val)` เพราะ ambiguous ใน JUnit 4 ให้ใช้ `assertEquals(Double.valueOf(Double.NaN), val)` หรือ `assertTrue(Double.isNaN(val))`
5. Full Imports Required:
   - ต้อง Import ทุกคลาสที่เรียกใช้อย่างครบถ้วน ไม่ละเลย Utility หรือ Annotation package (เช่น `com.fasterxml.jackson.databind.util.ClassUtil`, `com.fasterxml.jackson.annotation.*`, `org.jsoup.parser.*`, `org.jsoup.nodes.*`)

[TEST DESIGN & FAULT DETECTION DIRECTIVES (เพิ่มโอกาส REVEALING และลด INCONCLUSIVE)]
1. Oracle Precision (ลด Inconclusive จากการ Assert เปราะบาง):
   - ❌ อย่า Assert ข้อความ Exception หรือ toString() แบบ Exact Match (เพราะข้อความอาจเปลี่ยนตามเวอร์ชัน) ให้เน้น Assert ชนิดของ Exception หรือใช้ `assertTrue(e.getMessage().contains(...))` แทน
   - สำหรับกรณีทดสอบที่คาดว่าจะเกิด Exception ให้ใช้โครงสร้าง `try { method(); fail("Should throw exception"); } catch (SpecificException e) { /* Expected */ }` หรือ `@Test(expected = SpecificException.class)`
2. Bug-Hunting Edge Cases (กระตุ้นจุดบกพร่องที่ซ่อนอยู่):
   - ค่าขอบเขตวิกฤต: ค่าศูนย์ (0), ค่าลบ (-1), Maximum/Minimum values, Integer overflow/underflow
   - ปัญหา Null Safety: ส่ง `null` เข้าไปยัง Public method ที่รับ Object เพื่อดูว่ามี NullPointerException โดยไม่ได้จัดการหรือไม่
   - กรณี Collection/Array: ทดสอบ Empty, Single element, Duplicate elements, Null elements, Unmodifiable collection
   - กรณี String: Empty string `""`, Whitespace only `"   "`, String containing delimiters, Escape sequences, Special Unicode characters
   - Boundary & Loop conditions: จุดเปลี่ยนเงื่อนไข `<` vs `<=`, Off-by-one errors, Empty input streams

[TARGET SOURCE CODE]
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.collections4.trie;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.apache.commons.collections4.OrderedMapIterator;
import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.Unmodifiable;
import org.apache.commons.collections4.iterators.UnmodifiableOrderedMapIterator;

/**
 * An unmodifiable {@link Trie}.
 *
 * @since 4.0
 * @version $Id$
 */
public class UnmodifiableTrie<K, V> implements Trie<K, V>, Serializable, Unmodifiable {

    /** Serialization version */
    private static final long serialVersionUID = -7156426030315945159L;

    private final Trie<K, V> delegate;

    /**
     * Factory method to create a unmodifiable trie.
     *
     * @param <K>  the key type
     * @param <V>  the value type
     * @param trie  the trie to decorate, must not be null
     * @return a new unmodifiable trie
     * @throws IllegalArgumentException if trie is null
     */
    public static <K, V> UnmodifiableTrie<K, V> unmodifiableTrie(final Trie<K, ? extends V> trie) {
        return new UnmodifiableTrie<K, V>(trie);
    }

    //-----------------------------------------------------------------------
    /**
     * Constructor that wraps (not copies).
     *
     * @param trie  the trie to decorate, must not be null
     * @throws IllegalArgumentException if trie is null
     */
    public UnmodifiableTrie(final Trie<K, ? extends V> trie) {
        if (trie == null) {
            throw new IllegalArgumentException("Trie must not be null");
        }
        @SuppressWarnings("unchecked") // safe to upcast
        final Trie<K, V> tmpTrie = (Trie<K, V>) trie;
        this.delegate = tmpTrie;
    }

    //-----------------------------------------------------------------------

    public Set<Entry<K, V>> entrySet() {
        return Collections.unmodifiableSet(delegate.entrySet());
    }

    public Set<K> keySet() {
        return Collections.unmodifiableSet(delegate.keySet());
    }

    public Collection<V> values() {
        return Collections.unmodifiableCollection(delegate.values());
    }

    public void clear() {
        throw new UnsupportedOperationException();
    }

    public boolean containsKey(final Object key) {
        return delegate.containsKey(key);
    }

    public boolean containsValue(final Object value) {
        return delegate.containsValue(value);
    }

    public V get(final Object key) {
        return delegate.get(key);
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public V put(final K key, final V value) {
        throw new UnsupportedOperationException();
    }

    public void putAll(final Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    public V remove(final Object key) {
        throw new UnsupportedOperationException();
    }

    public int size() {
        return delegate.size();
    }

    public K firstKey() {
        return delegate.firstKey();
    }

    public SortedMap<K, V> headMap(final K toKey) {
        return Collections.unmodifiableSortedMap(delegate.headMap(toKey));
    }

    public K lastKey() {
        return delegate.lastKey();
    }

    public SortedMap<K, V> subMap(final K fromKey, final K toKey) {
        return Collections.unmodifiableSortedMap(delegate.subMap(fromKey, toKey));
    }

    public SortedMap<K, V> tailMap(final K fromKey) {
        return Collections.unmodifiableSortedMap(delegate.tailMap(fromKey));
    }

    public SortedMap<K, V> prefixMap(final K key) {
        return Collections.unmodifiableSortedMap(delegate.prefixMap(key));
    }

    public Comparator<? super K> comparator() {
        return delegate.comparator();
    }

    //-----------------------------------------------------------------------
    public OrderedMapIterator<K, V> mapIterator() {
        final OrderedMapIterator<K, V> it = delegate.mapIterator();
        return UnmodifiableOrderedMapIterator.unmodifiableOrderedMapIterator(it);
    }

    public K nextKey(K key) {
        return delegate.nextKey(key);
    }

    public K previousKey(K key) {
        return delegate.previousKey(key);
    }

    //-----------------------------------------------------------------------
    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return delegate.equals(obj);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

}