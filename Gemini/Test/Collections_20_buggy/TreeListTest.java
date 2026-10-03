package org.apache.commons.collections.list;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public class TreeListTest {

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetOutOfBoundsNegative() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.get(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetOutOfBoundsPositive() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.add("A");
        list.get(1);
    }

    @Test
    public void testBasicAddAndGet() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        assertEquals(0, list.size());
        
        list.add("Zero");
        list.add("One");
        list.add("Two");
        
        assertEquals(3, list.size());
        assertEquals("Zero", list.get(0));
        assertEquals("One", list.get(1));
        assertEquals("Two", list.get(2));
    }

    @Test
    public void testCollectionConstructor() throws Throwable {
        Collection<String> coll = new ArrayList<String>();
        coll.add("A");
        coll.add("B");
        coll.add("C");

        TreeList<String> list = new TreeList<String>(coll);
        assertEquals(3, list.size());
        assertEquals("A", list.get(0));
        assertEquals("B", list.get(1));
        assertEquals("C", list.get(2));
    }

    @Test
    public void testSetAndReturnValue() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.add("A");
        list.add("B");

        String old = list.set(1, "UpdatedB");
        assertEquals("B", old);
        assertEquals("UpdatedB", list.get(1));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetOutOfBounds() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.set(0, "A");
    }

    @Test
    public void testRemoveMiddleAndEdges() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.add("A");
        list.add("B");
        list.add("C");
        list.add("D");

        assertEquals("B", list.remove(1));
        assertEquals(3, list.size());
        assertEquals("A", list.get(0));
        assertEquals("C", list.get(1));
        assertEquals("D", list.get(2));

        assertEquals("A", list.remove(0));
        assertEquals("D", list.remove(1));
        assertEquals(1, list.size());
        assertEquals("C", list.get(0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testRemoveOutOfBounds() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.remove(0);
    }

    @Test
    public void testIndexOfAndContains() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        assertEquals(-1, list.indexOf("A"));
        assertFalse(list.contains("A"));

        list.add("A");
        list.add("B");
        list.add("A");

        assertEquals(0, list.indexOf("A"));
        assertEquals(1, list.indexOf("B"));
        assertEquals(-1, list.indexOf("NotExists"));
        
        assertTrue(list.contains("A"));
        assertTrue(list.contains("B"));
        assertFalse(list.contains("NotExists"));

        list.clear();
        assertEquals(-1, list.indexOf("A"));
        assertFalse(list.contains("A"));
    }

    @Test
    public void testToArray() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        Object[] emptyArray = list.toArray();
        assertNotNull(emptyArray);
        assertEquals(0, emptyArray.length);

        list.add("X");
        list.add("Y");
        Object[] arr = list.toArray();
        assertEquals(2, arr.length);
        assertEquals("X", arr[0]);
        assertEquals("Y", arr[1]);
    }

    @Test
    public void testClear() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.add("A");
        list.add("B");
        assertEquals(2, list.size());

        list.clear();
        assertEquals(0, list.size());
        assertTrue(list.isEmpty());
    }

    @Test
    public void testInsertBalancesAVLTree() throws Throwable {
        TreeList<Integer> list = new TreeList<Integer>();
        // Add many items to trigger AVL rotations (left, right, double rotations)
        for (int i = 0; i < 50; i++) {
            list.add(i, Integer.valueOf(i));
        }
        assertEquals(50, list.size());
        for (int i = 0; i < 50; i++) {
            assertEquals(Integer.valueOf(i), list.get(i));
        }

        // Insert at beginning, middle, end
        list.add(0, Integer.valueOf(-1));
        list.add(25, Integer.valueOf(999));
        list.add(52, Integer.valueOf(1000));

        assertEquals(53, list.size());
        assertEquals(Integer.valueOf(-1), list.get(0));
        assertEquals(Integer.valueOf(999), list.get(25));

        // Remove to trigger balance/remove logic extensively
        for (int i = 52; i >= 0; i--) {
            list.remove(i);
        }
        assertEquals(0, list.size());
    }

    @Test
    public void testListIteratorForwardAndBackward() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.add("One");
        list.add("Two");
        list.add("Three");

        ListIterator<String> it = list.listIterator();
        assertEquals(0, it.previousIndex());
        assertEquals(0, it.nextIndex());
        assertTrue(it.hasNext());
        assertFalse(it.hasPrevious());

        assertEquals("One", it.next());
        assertEquals("Two", it.next());
        assertTrue(it.hasPrevious());
        
        assertEquals("Two", it.previous());
        assertEquals("One", it.previous());
        assertFalse(it.hasPrevious());

        // Jump to index via listIterator(int)
        ListIterator<String> it2 = list.listIterator(2);
        assertTrue(it2.hasPrevious());
        assertEquals("Two", it2.previous());
    }

    @Test
    public void testListIteratorAddAndSet() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        ListIterator<String> it = list.listIterator();
        
        it.add("A");
        it.add("C");
        
        assertEquals(2, list.size());
        
        it.previous(); // points to C
        it.set("B"); // change C to B
        
        assertEquals("B", list.get(1));

        Iterator<String> simpleIt = list.iterator();
        assertTrue(simpleIt.hasNext());
        assertEquals("A", simpleIt.next());
    }

    @Test
    public void testListIteratorRemove() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.add("A");
        list.add("B");
        list.add("C");

        ListIterator<String> it = list.listIterator();
        assertEquals("A", it.next());
        it.remove(); // removes "A"
        
        assertEquals(2, list.size());
        assertEquals("B", list.get(0));

        assertEquals("B", it.next());
        assertEquals("C", it.next());
        it.remove(); // removes "C"
        assertEquals(1, list.size());
    }

    @Test(expected = IllegalStateException.class)
    public void testListIteratorRemoveIllegalState() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        ListIterator<String> it = list.listIterator();
        it.remove();
    }

    @Test(expected = IllegalStateException.class)
    public void testListIteratorSetIllegalState() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        ListIterator<String> it = list.listIterator();
        it.set("Fail");
    }

    @Test(expected = NoSuchElementException.class)
    public void testListIteratorNoSuchElementNext() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        ListIterator<String> it = list.listIterator();
        it.next();
    }

    @Test(expected = NoSuchElementException.class)
    public void testListIteratorNoSuchElementPrevious() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        ListIterator<String> it = list.listIterator();
        it.previous();
    }

    @Test(expected = ConcurrentModificationException.class)
    public void testConcurrentModificationExceptionOnNext() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.add("A");
        ListIterator<String> it = list.listIterator();
        list.add("B"); // Modify structure outside iterator
        it.next();
    }

    @Test(expected = ConcurrentModificationException.class)
    public void testConcurrentModificationExceptionOnPrevious() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.add("A");
        list.add("B");
        ListIterator<String> it = list.listIterator(1);
        list.remove(0);
        it.previous();
    }

    @Test(expected = ConcurrentModificationException.class)
    public void testConcurrentModificationExceptionOnRemove() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.add("A");
        ListIterator<String> it = list.listIterator();
        it.next();
        list.set(0, "Modified");
        it.remove();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testListIteratorInvalidStartIndex() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.listIterator(5);
    }

    @Test
    public void testToStringOnAVLNode() throws Throwable {
        TreeList<String> list = new TreeList<String>();
        list.add("TestNode");
        // Indirectly check toString or internal node structure coverage via contains/indexOf/toArray
        assertNotNull(list.toString());
        assertTrue(list.contains("TestNode"));
    }
}