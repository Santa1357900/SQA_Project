package org.apache.commons.collections.buffer;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.commons.collections.BufferUnderflowException;
import org.junit.Test;

public class UnboundedFifoBufferClaudeTest {

    // constructor: default size -> empty buffer with internal capacity 33
    @Test
    public void testConstructorDefault_createsEmptyBuffer() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer();
        assertTrue(buf.isEmpty());
        assertEquals(0, buf.size());
        assertEquals(33, buf.buffer.length);
    }

    // constructor: valid positive size -> empty buffer, capacity = size+1
    @Test
    public void testConstructorWithSize_validSize_createsEmptyBuffer() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(5);
        assertTrue(buf.isEmpty());
        assertEquals(0, buf.size());
        assertEquals(6, buf.buffer.length);
    }

    // constructor: size == 0 -> IllegalArgumentException
    @Test
    public void testConstructorWithSize_zero_throwsIllegalArgumentException() throws Throwable {
        try {
            new UnboundedFifoBuffer(0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // constructor: negative size -> IllegalArgumentException
    @Test
    public void testConstructorWithSize_negative_throwsIllegalArgumentException() throws Throwable {
        try {
            new UnboundedFifoBuffer(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // size(): branch tail >= head on fresh empty buffer
    @Test
    public void testSize_emptyBuffer_returnsZero() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(4);
        assertEquals(0, buf.size());
    }

    // size(): branch tail >= head, several adds, no wraparound
    @Test
    public void testSize_afterAdds_returnsCorrectCount() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(4);
        buf.add("A");
        buf.add("B");
        buf.add("C");
        assertEquals(3, buf.size());
    }

    // isEmpty(): true right after construction
    @Test
    public void testIsEmpty_afterConstruction_true() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        assertTrue(buf.isEmpty());
    }

    // isEmpty(): false after at least one add
    @Test
    public void testIsEmpty_afterAdd_false() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        buf.add("x");
        assertFalse(buf.isEmpty());
    }

    // isEmpty(): true again after removing all elements
    @Test
    public void testIsEmpty_afterAddAndRemoveAll_true() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        buf.add("x");
        buf.remove();
        assertTrue(buf.isEmpty());
    }

    // add(): null argument -> NullPointerException, per javadoc
    @Test
    public void testAdd_nullObject_throwsNullPointerException() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        try {
            buf.add(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // add(): single element increases size by one and element is retrievable via get()
    @Test
    public void testAdd_singleElement_increasesSizeAndReturnsTrue() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        boolean result = buf.add("hello");
        assertTrue(result);
        assertEquals(1, buf.size());
        assertEquals("hello", buf.get());
    }

    // add(): javadoc says "true, always" - verify across multiple calls
    @Test
    public void testAdd_returnsTrueAlways() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(2);
        assertTrue(buf.add("a"));
        assertTrue(buf.add("b"));
        assertTrue(buf.add("c"));
    }

    // add(): triggers internal resize branch (size()+1 >= buffer.length), FIFO order preserved
    @Test
    public void testAdd_triggersResize_maintainsFifoOrder() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer();
        for (int i = 0; i < 41; i++) {
            buf.add(new Integer(i));
        }
        assertEquals(41, buf.size());
        for (int i = 0; i < 41; i++) {
            assertEquals(new Integer(i), buf.remove());
        }
        assertTrue(buf.isEmpty());
    }

    // add(): wraparound of tail index -> size() uses the (tail < head) branch
    @Test
    public void testAdd_wraparound_sizeComputedCorrectly() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        buf.add("A");
        buf.add("B");
        buf.add("C");
        buf.remove();
        buf.remove();
        buf.add("D");
        buf.add("E");
        assertEquals(3, buf.size());
        assertEquals("C", buf.remove());
        assertEquals("D", buf.remove());
        assertEquals("E", buf.remove());
    }

    // get(): empty buffer -> BufferUnderflowException
    @Test
    public void testGet_emptyBuffer_throwsBufferUnderflowException() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        try {
            buf.get();
            fail("expected BufferUnderflowException");
        } catch (BufferUnderflowException expected) {
        }
    }

    // get(): returns first-added element without removing it
    @Test
    public void testGet_nonEmptyBuffer_returnsFirstElementWithoutRemoving() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        buf.add("first");
        buf.add("second");
        assertEquals("first", buf.get());
        assertEquals(2, buf.size());
    }

    // get(): calling repeatedly does not change buffer state
    @Test
    public void testGet_doesNotModifySize() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        buf.add("only");
        buf.get();
        buf.get();
        assertEquals(1, buf.size());
        assertEquals("only", buf.get());
    }

    // remove(): empty buffer -> BufferUnderflowException
    @Test
    public void testRemove_emptyBuffer_throwsBufferUnderflowException() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        try {
            buf.remove();
            fail("expected BufferUnderflowException");
        } catch (BufferUnderflowException expected) {
        }
    }

    // remove(): FIFO removal order across multiple elements, size decreases each time
    @Test
    public void testRemove_fifoOrder_multipleElements() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        buf.add("A");
        buf.add("B");
        buf.add("C");
        assertEquals("A", buf.remove());
        assertEquals(2, buf.size());
        assertEquals("B", buf.remove());
        assertEquals(1, buf.size());
        assertEquals("C", buf.remove());
        assertEquals(0, buf.size());
    }



    // iterator(): empty buffer -> hasNext() is false immediately
    @Test
    public void testIterator_emptyBuffer_hasNextFalse() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        Iterator it = buf.iterator();
        assertFalse(it.hasNext());
    }

    // iterator(): single element, one next() then exhausted
    @Test
    public void testIterator_singleElement_iteratesCorrectly() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        buf.add("only");
        Iterator it = buf.iterator();
        assertTrue(it.hasNext());
        assertEquals("only", it.next());
        assertFalse(it.hasNext());
    }

    // iterator(): multiple elements iterated in FIFO insertion order
    @Test
    public void testIterator_multipleElements_fifoOrder() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(4);
        buf.add("A");
        buf.add("B");
        buf.add("C");
        Iterator it = buf.iterator();
        assertEquals("A", it.next());
        assertEquals("B", it.next());
        assertEquals("C", it.next());
        assertFalse(it.hasNext());
    }

    // iterator().next(): called past the end -> NoSuchElementException
    @Test
    public void testIteratorNext_exhausted_throwsNoSuchElementException() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        Iterator it = buf.iterator();
        try {
            it.next();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // iterator().remove(): called before next() -> IllegalStateException
    @Test
    public void testIteratorRemove_withoutNext_throwsIllegalStateException() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(3);
        buf.add("A");
        Iterator it = buf.iterator();
        try {
            it.remove();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // iterator().remove(): removing the first element uses the fast path (lastReturnedIndex == head)
    @Test
    public void testIteratorRemove_firstElement_fastPath() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(4);
        buf.add("A");
        buf.add("B");
        buf.add("C");
        Iterator it = buf.iterator();
        it.next();
        it.remove();
        assertEquals(2, buf.size());
        assertEquals("B", buf.remove());
        assertEquals("C", buf.remove());
    }

    // iterator().remove(): removing a middle element shifts subsequent elements left
    @Test
    public void testIteratorRemove_middleElement_shiftsElements() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(4);
        buf.add("A");
        buf.add("B");
        buf.add("C");
        Iterator it = buf.iterator();
        it.next();
        it.next();
        it.remove();
        assertEquals(2, buf.size());
        assertEquals("A", buf.remove());
        assertEquals("C", buf.remove());
    }

    // iterator().remove(): removing the last element updates tail correctly
    @Test
    public void testIteratorRemove_lastElement_updatesTailCorrectly() throws Throwable {
        UnboundedFifoBuffer buf = new UnboundedFifoBuffer(4);
        buf.add("A");
        buf.add("B");
        buf.add("C");
        Iterator it = buf.iterator();
        it.next();
        it.next();
        it.next();
        it.remove();
        assertEquals(2, buf.size());
        assertEquals("A", buf.remove());
        assertEquals("B", buf.remove());
    }
}
