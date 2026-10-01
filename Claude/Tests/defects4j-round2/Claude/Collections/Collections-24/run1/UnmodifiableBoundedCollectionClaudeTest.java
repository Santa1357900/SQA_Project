package org.apache.commons.collections4.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections4.BoundedCollection;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Minimal concrete BoundedCollection backed by a real JDK ArrayList.
 * Extends a concrete JDK class and only adds the two abstract methods
 * declared (and literally used) by UnmodifiableBoundedCollection's source:
 * isFull() and maxSize().
 */
class BoundedArrayList<E> extends ArrayList<E> implements BoundedCollection<E> {

    private static final long serialVersionUID = 1L;

    private final int limit;

    BoundedArrayList(int limit) {
        super();
        this.limit = limit;
    }

    public boolean isFull() {
        return size() >= limit;
    }

    public int maxSize() {
        return limit;
    }
}

public class UnmodifiableBoundedCollectionClaudeTest {

    // Collection-arg factory: null -> IllegalArgumentException per explicit null check
    @Test
    public void testFactoryCollection_null_throwsIllegalArgumentException() throws Throwable {
        try {
            UnmodifiableBoundedCollection.unmodifiableBoundedCollection((Collection<Integer>) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("null"));
        }
    }

    // Collection-arg factory: plain empty ArrayList is not a BoundedCollection -> IllegalArgumentException
    @Test
    public void testFactoryCollection_plainEmptyCollection_throwsIllegalArgumentException() throws Throwable {
        Collection<Integer> plain = new ArrayList<Integer>();
        try {
            UnmodifiableBoundedCollection.unmodifiableBoundedCollection(plain);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("bounded"));
        }
    }

    // Collection-arg factory: plain non-empty ArrayList is still not bounded -> IllegalArgumentException
    @Test
    public void testFactoryCollection_plainNonEmptyCollection_throwsIllegalArgumentException() throws Throwable {
        Collection<String> plain = new ArrayList<String>();
        plain.add("x");
        try {
            UnmodifiableBoundedCollection.unmodifiableBoundedCollection(plain);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("bounded"));
        }
    }

    // Collection-arg factory: already a BoundedCollection -> loop breaks immediately, wraps successfully
    @Test
    public void testFactoryCollection_boundedCollectionInput_wrapsSuccessfully() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(3);
        Collection<Integer> asCollection = bounded;
        BoundedCollection<Integer> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(asCollection);
        assertNotNull(result);
        assertEquals(3, result.maxSize());
    }

    // Collection-arg factory: passing an already-wrapped UnmodifiableBoundedCollection double-wraps it
    @Test
    public void testFactoryCollection_alreadyWrappedInput_doubleWrapsAndDelegatesMaxSize() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        BoundedCollection<Integer> wrapped1 = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        Collection<Integer> asCollection = wrapped1;
        BoundedCollection<Integer> wrapped2 = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(asCollection);
        assertEquals(5, wrapped2.maxSize());
    }

    // Collection-arg factory: double wrap also delegates contains()/size() through both layers
    @Test
    public void testFactoryCollection_doubleWrap_delegatesContainsAndSize() throws Throwable {
        BoundedArrayList<String> bounded = new BoundedArrayList<String>(5);
        bounded.add("a");
        BoundedCollection<String> wrapped1 = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        Collection<String> asCollection = wrapped1;
        BoundedCollection<String> wrapped2 = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(asCollection);
        assertEquals(1, wrapped2.size());
        assertTrue(wrapped2.contains("a"));
    }

    // BoundedCollection-arg factory: direct wrap returns non-null functional wrapper
    @Test
    public void testFactoryBoundedCollection_wrapsCorrectly() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(7);
        BoundedCollection<Integer> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertNotNull(result);
        assertEquals(7, result.maxSize());
        assertFalse(result.isFull());
    }

    // isFull(): below capacity returns false
    @Test
    public void testIsFull_belowCapacity_returnsFalse() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(3);
        bounded.add(Integer.valueOf(1));
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertFalse(wrapped.isFull());
    }

    // isFull(): at capacity returns true
    @Test
    public void testIsFull_atCapacity_returnsTrue() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(2);
        bounded.add(Integer.valueOf(1));
        bounded.add(Integer.valueOf(2));
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertTrue(wrapped.isFull());
    }

    // isFull(): boundary maxSize = 0 with empty collection is already full
    @Test
    public void testIsFull_zeroCapacity_emptyCollection_returnsTrue() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(0);
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertTrue(wrapped.isFull());
    }

    // maxSize(): delegates exact value from decorated collection
    @Test
    public void testMaxSize_delegatesToDecorated() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(42);
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertEquals(42, wrapped.maxSize());
    }

    // maxSize(): boundary value Integer.MAX_VALUE is returned unchanged
    @Test
    public void testMaxSize_maxIntBoundary() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(Integer.MAX_VALUE);
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertEquals(Integer.MAX_VALUE, wrapped.maxSize());
        assertFalse(wrapped.isFull());
    }

    // size(): delegates to decorated collection, zero elements
    @Test
    public void testSize_emptyCollection_returnsZero() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertEquals(0, wrapped.size());
    }

    // size(): delegates correctly with multiple elements
    @Test
    public void testSize_multipleElements_returnsCorrectCount() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        bounded.add(Integer.valueOf(1));
        bounded.add(Integer.valueOf(2));
        bounded.add(Integer.valueOf(3));
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertEquals(3, wrapped.size());
    }

    // isEmpty(): true when underlying collection has no elements
    @Test
    public void testIsEmpty_trueWhenEmpty() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertTrue(wrapped.isEmpty());
    }

    // isEmpty(): false when underlying collection has at least one element
    @Test
    public void testIsEmpty_falseWhenNotEmpty() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        bounded.add(Integer.valueOf(9));
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertFalse(wrapped.isEmpty());
    }

    // contains(): returns true for a present element
    @Test
    public void testContains_elementPresent_returnsTrue() throws Throwable {
        BoundedArrayList<String> bounded = new BoundedArrayList<String>(5);
        bounded.add("hello");
        BoundedCollection<String> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertTrue(wrapped.contains("hello"));
    }

    // contains(): returns false for an absent element
    @Test
    public void testContains_elementAbsent_returnsFalse() throws Throwable {
        BoundedArrayList<String> bounded = new BoundedArrayList<String>(5);
        bounded.add("hello");
        BoundedCollection<String> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertFalse(wrapped.contains("other"));
    }

    // iterator(): yields the exact elements present in the decorated collection
    @Test
    public void testIterator_returnsAllElements() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        bounded.add(Integer.valueOf(10));
        bounded.add(Integer.valueOf(20));
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        Iterator<Integer> it = wrapped.iterator();
        assertEquals(Integer.valueOf(10), it.next());
        assertEquals(Integer.valueOf(20), it.next());
        assertFalse(it.hasNext());
    }

    // iterator(): hasNext() is false immediately for an empty decorated collection (0-iteration loop)
    @Test
    public void testIterator_emptyCollection_hasNextFalse() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        Iterator<Integer> it = wrapped.iterator();
        assertFalse(it.hasNext());
    }

    // iterator().remove(): must be unmodifiable and throw UnsupportedOperationException
    @Test
    public void testIterator_remove_throwsUnsupportedOperationException() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        bounded.add(Integer.valueOf(1));
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        Iterator<Integer> it = wrapped.iterator();
        it.next();
        try {
            it.remove();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected per unmodifiable contract
        }
    }

    // add(): must always throw UnsupportedOperationException
    @Test
    public void testAdd_throwsUnsupportedOperationException() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        try {
            wrapped.add(Integer.valueOf(1));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // addAll(): must always throw UnsupportedOperationException
    @Test
    public void testAddAll_throwsUnsupportedOperationException() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        List<Integer> toAdd = new ArrayList<Integer>();
        toAdd.add(Integer.valueOf(1));
        try {
            wrapped.addAll(toAdd);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // clear(): must always throw UnsupportedOperationException
    @Test
    public void testClear_throwsUnsupportedOperationException() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        bounded.add(Integer.valueOf(1));
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        try {
            wrapped.clear();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // remove(Object): must always throw UnsupportedOperationException
    @Test
    public void testRemove_throwsUnsupportedOperationException() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        bounded.add(Integer.valueOf(1));
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        try {
            wrapped.remove(Integer.valueOf(1));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // removeAll(): must always throw UnsupportedOperationException
    @Test
    public void testRemoveAll_throwsUnsupportedOperationException() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        bounded.add(Integer.valueOf(1));
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        List<Integer> toRemove = new ArrayList<Integer>();
        toRemove.add(Integer.valueOf(1));
        try {
            wrapped.removeAll(toRemove);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // retainAll(): must always throw UnsupportedOperationException
    @Test
    public void testRetainAll_throwsUnsupportedOperationException() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(5);
        bounded.add(Integer.valueOf(1));
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        List<Integer> toRetain = new ArrayList<Integer>();
        toRetain.add(Integer.valueOf(1));
        try {
            wrapped.retainAll(toRetain);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // toArray(): reflects the exact number and content of elements
    @Test
    public void testToArray_returnsAllElements() throws Throwable {
        BoundedArrayList<String> bounded = new BoundedArrayList<String>(5);
        bounded.add("a");
        bounded.add("b");
        BoundedCollection<String> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        Object[] arr = wrapped.toArray();
        assertEquals(2, arr.length);
        assertEquals("a", arr[0]);
        assertEquals("b", arr[1]);
    }

    // containsAll(): true when all queried elements are present
    @Test
    public void testContainsAll_allPresent_returnsTrue() throws Throwable {
        BoundedArrayList<String> bounded = new BoundedArrayList<String>(5);
        bounded.add("x");
        bounded.add("y");
        BoundedCollection<String> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        List<String> check = new ArrayList<String>();
        check.add("x");
        assertTrue(wrapped.containsAll(check));
    }

    // containsAll(): false when at least one queried element is missing
    @Test
    public void testContainsAll_missingElement_returnsFalse() throws Throwable {
        BoundedArrayList<String> bounded = new BoundedArrayList<String>(5);
        bounded.add("x");
        BoundedCollection<String> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        List<String> check = new ArrayList<String>();
        check.add("x");
        check.add("z");
        assertFalse(wrapped.containsAll(check));
    }

    // Constructor wraps (not copies): mutating the backing collection directly is reflected by the wrapper
    @Test
    public void testLiveView_backingListMutatedDirectly_reflectedInWrapper() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(10);
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertEquals(0, wrapped.size());
        bounded.add(Integer.valueOf(42));
        assertEquals(1, wrapped.size());
        assertTrue(wrapped.contains(Integer.valueOf(42)));
    }

    // Live view also affects isFull() after direct mutation of the backing collection
    @Test
    public void testLiveView_backingListFilledDirectly_isFullReflectsChange() throws Throwable {
        BoundedArrayList<Integer> bounded = new BoundedArrayList<Integer>(2);
        BoundedCollection<Integer> wrapped = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(bounded);
        assertFalse(wrapped.isFull());
        bounded.add(Integer.valueOf(1));
        bounded.add(Integer.valueOf(2));
        assertTrue(wrapped.isFull());
    }
}
