package org.apache.commons.collections4.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.collections4.BoundedCollection;

public class UnmodifiableBoundedCollectionTest {

    private static class DummyBoundedCollection<E> implements BoundedCollection<E> {
        private final Collection<E> delegate = new ArrayList<E>();
        private final boolean full;
        private final int maxSize;

        public DummyBoundedCollection(boolean full, int maxSize) {
            this.full = full;
            this.maxSize = maxSize;
        }

        public boolean isFull() {
            return full;
        }

        public int maxSize() {
            return maxSize;
        }

        public int size() {
            return delegate.size();
        }

        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        public Iterator<E> iterator() {
            return delegate.iterator();
        }

        public Object[] toArray() {
            return delegate.toArray();
        }

        public <T> T[] toArray(T[] a) {
            return delegate.toArray(a);
        }

        public boolean add(E e) {
            return delegate.add(e);
        }

        public boolean remove(Object o) {
            return delegate.remove(o);
        }

        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        public boolean addAll(Collection<? extends E> c) {
            return delegate.addAll(c);
        }

        public boolean removeAll(Collection<?> c) {
            return delegate.removeAll(c);
        }

        public boolean retainAll(Collection<?> c) {
            return delegate.retainAll(c);
        }

        public void clear() {
            delegate.clear();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFactoryNullCollection() throws Throwable {
        UnmodifiableBoundedCollection.unmodifiableBoundedCollection((BoundedCollection<Object>) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFactoryCollectionNullGeneral() throws Throwable {
        UnmodifiableBoundedCollection.unmodifiableBoundedCollection((Collection<Object>) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFactoryNotBoundedCollection() throws Throwable {
        Collection<String> normalCol = new ArrayList<String>();
        UnmodifiableBoundedCollection.unmodifiableBoundedCollection(normalCol);
    }

    @Test
    public void testFactoryWithDirectBoundedCollection() throws Throwable {
        BoundedCollection<String> dummy = new DummyBoundedCollection<String>(false, 10);
        BoundedCollection<String> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(dummy);
        assertNotNull(result);
        assertTrue(result instanceof UnmodifiableBoundedCollection);
        assertEquals(10, result.maxSize());
        assertFalse(result.isFull());
    }

    @Test
    public void testFactoryWithAbstractCollectionDecorator() throws Throwable {
        BoundedCollection<String> dummy = new DummyBoundedCollection<String>(true, 5);
        AbstractCollectionDecorator<String> decorator = new AbstractCollectionDecorator<String>(dummy) {
            private static final long serialVersionUID = 1L;
        };
        
        BoundedCollection<String> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(decorator);
        assertNotNull(result);
        assertEquals(5, result.maxSize());
        assertTrue(result.isFull());
    }

    @Test
    public void testFactoryWithSynchronizedCollection() throws Throwable {
        BoundedCollection<String> dummy = new DummyBoundedCollection<String>(false, 20);
        SynchronizedCollection<String> syncCol = SynchronizedCollection.synchronizedCollection(dummy);
        
        BoundedCollection<String> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(syncCol);
        assertNotNull(result);
        assertEquals(20, result.maxSize());
        assertFalse(result.isFull());
    }

    @Test
    public void testIteratorIsUnmodifiable() throws Throwable {
        DummyBoundedCollection<String> dummy = new DummyBoundedCollection<String>(false, 10);
        dummy.add("test");
        BoundedCollection<String> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(dummy);
        
        Iterator<String> it = result.iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertEquals("test", it.next());
        
        try {
            it.remove();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testAddUnsupported() throws Throwable {
        BoundedCollection<String> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(new DummyBoundedCollection<String>(false, 5));
        try {
            result.add("item");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testAddAllUnsupported() throws Throwable {
        BoundedCollection<String> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(new DummyBoundedCollection<String>(false, 5));
        Collection<String> list = new ArrayList<String>();
        list.add("item");
        try {
            result.addAll(list);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testClearUnsupported() throws Throwable {
        BoundedCollection<String> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(new DummyBoundedCollection<String>(false, 5));
        try {
            result.clear();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testRemoveUnsupported() throws Throwable {
        BoundedCollection<String> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(new DummyBoundedCollection<String>(false, 5));
        try {
            result.remove("item");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testRemoveAllUnsupported() throws Throwable {
        BoundedCollection<String> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(new DummyBoundedCollection<String>(false, 5));
        Collection<String> list = new ArrayList<String>();
        try {
            result.removeAll(list);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testRetainAllUnsupported() throws Throwable {
        BoundedCollection<String> result = UnmodifiableBoundedCollection.unmodifiableBoundedCollection(new DummyBoundedCollection<String>(false, 5));
        Collection<String> list = new ArrayList<String>();
        try {
            result.retainAll(list);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}