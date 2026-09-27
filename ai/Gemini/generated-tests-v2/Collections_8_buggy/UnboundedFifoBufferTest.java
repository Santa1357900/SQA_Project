package org.apache.commons.collections.buffer;

import junit.framework.TestCase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.commons.collections.BufferUnderflowException;

public class UnboundedFifoBufferTest extends TestCase {

    public UnboundedFifoBufferTest(String name) {
        super(name);
    }

    public void testConstructor_InvalidSize() throws Throwable {
        try {
            new UnboundedFifoBuffer(0);
            fail("Expected IllegalArgumentException for size 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            new UnboundedFifoBuffer(-5);
            fail("Expected IllegalArgumentException for negative size");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    public void testConstructor_Default() throws Throwable {
        UnboundedFifoBuffer buffer = new UnboundedFifoBuffer();
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
    }

    public void testAddAndGetAndRemove() throws Throwable {
        UnboundedFifoBuffer buffer = new UnboundedFifoBuffer(2);
        assertTrue(buffer.isEmpty());

        try {
            buffer.add(null);
            fail("Expected NullPointerException when adding null");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage() != null);
        }

        assertTrue(buffer.add("One"));
        assertFalse(buffer.isEmpty());
        assertEquals(1, buffer.size());
        assertEquals("One", buffer.get());

        assertTrue(buffer.add("Two"));
        assertEquals(2, buffer.size());
        assertEquals("One", buffer.get());

        // Test buffer expansion
        assertTrue(buffer.add("Three"));
        assertEquals(3, buffer.size());
        assertEquals("One", buffer.get());

        assertEquals("One", buffer.remove());
        assertEquals(2, buffer.size());
        assertEquals("Two", buffer.get());

        assertEquals("Two", buffer.remove());
        assertEquals(1, buffer.size());
        assertEquals("Three", buffer.get());

        assertEquals("Three", buffer.remove());
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
    }

    public void testUnderflowExceptions() throws Throwable {
        UnboundedFifoBuffer buffer = new UnboundedFifoBuffer();

        try {
            buffer.get();
            fail("Expected BufferUnderflowException on empty get()");
        } catch (BufferUnderflowException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            buffer.remove();
            fail("Expected BufferUnderflowException on empty remove()");
        } catch (BufferUnderflowException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    public void testWrapAroundBehavior() throws Throwable {
        // Force wrap around by adding and removing multiple times
        UnboundedFifoBuffer buffer = new UnboundedFifoBuffer(2);
        buffer.add("A");
        buffer.add("B");
        assertEquals("A", buffer.remove());
        buffer.add("C");
        assertEquals("B", buffer.remove());
        assertEquals("C", buffer.remove());
        assertTrue(buffer.isEmpty());

        buffer.add("D");
        buffer.add("E");
        assertEquals("D", buffer.get());
        assertEquals(2, buffer.size());
    }

    public void testIterator_Operations() throws Throwable {
        UnboundedFifoBuffer buffer = new UnboundedFifoBuffer(2);
        buffer.add("A");
        buffer.add("B");
        buffer.add("C");

        Iterator it = buffer.iterator();
        
        try {
            it.remove();
            fail("Expected IllegalStateException before next()");
        } catch (IllegalStateException e) {
            // expected
        }

        assertTrue(it.hasNext());
        assertEquals("A", it.next());

        // Remove head element via iterator
        it.remove();
        assertEquals(2, buffer.size());
        assertEquals("B", buffer.get());

        assertTrue(it.hasNext());
        assertEquals("B", it.next());

        // Remove non-head element via iterator (shifts elements)
        it.remove();
        assertEquals(1, buffer.size());
        assertEquals("C", buffer.get());

        assertTrue(it.hasNext());
        assertEquals("C", it.next());
        it.remove();
        assertTrue(buffer.isEmpty());

        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // expected
        }
    }

    public void testSerialization() throws Throwable {
        UnboundedFifoBuffer buffer = new UnboundedFifoBuffer();
        buffer.add("Test1");
        buffer.add("Test2");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(buffer);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        UnboundedFifoBuffer deserialized = (UnboundedFifoBuffer) ois.readObject();
        ois.close();

        assertEquals(2, deserialized.size());
        assertEquals("Test1", deserialized.remove());
        assertEquals("Test2", deserialized.remove());
        assertTrue(deserialized.isEmpty());
    }
}