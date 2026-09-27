package org.apache.commons.collections4.list;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.junit.Test;
import static org.junit.Assert.*;

public class SetUniqueListTest {

    @Test
    public void testSetUniqueListFactoryNull() throws Throwable {
        try {
            SetUniqueList.setUniqueList(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("List must not be null"));
        }
    }

    @Test
    public void testSetUniqueListFactoryEmpty() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> uniqueList = SetUniqueList.setUniqueList(list);
        assertNotNull(uniqueList);
        assertTrue(uniqueList.isEmpty());
    }

    @Test
    public void testSetUniqueListFactoryWithDuplicates() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("A");
        list.add("B");
        list.add("A");
        list.add("C");

        SetUniqueList<String> uniqueList = SetUniqueList.setUniqueList(list);
        assertEquals(3, uniqueList.size());
        assertEquals("A", uniqueList.get(0));
        assertEquals("B", uniqueList.get(1));
        assertEquals("C", uniqueList.get(2));
    }

    @Test
    public void testConstructorNullSet() throws Throwable {
        List<String> list = new ArrayList<String>();
        try {
            new SetUniqueList<String>(list, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Set must not be null"));
        }
    }

    @Test
    public void testAsSet() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> uniqueList = SetUniqueList.setUniqueList(list);
        uniqueList.add("Item1");
        
        Set<String> setView = uniqueList.asSet();
        assertNotNull(setView);
        assertTrue(setView.contains("Item1"));
        
        try {
            setView.add("Item2");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected because it is an unmodifiable set view
        }
    }

    @Test
    public void testAddAndDuplicates() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> uniqueList = SetUniqueList.setUniqueList(list);

        assertTrue(uniqueList.add("One"));
        assertFalse(uniqueList.add("One"));
        assertEquals(1, uniqueList.size());

        uniqueList.add(0, "Zero");
        assertEquals("Zero", uniqueList.get(0));
        
        // Add duplicate via index method (should not insert)
        uniqueList.add(0, "One");
        assertEquals("Zero", uniqueList.get(0));
    }

    @Test
    public void testAddAllCollection() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> uniqueList = SetUniqueList.setUniqueList(list);

        List<String> coll = new ArrayList<String>();
        coll.add("A");
        coll.add("B");
        coll.add("A");

        assertTrue(uniqueList.addAll(coll));
        assertEquals(2, uniqueList.size());
        assertFalse(uniqueList.addAll(coll)); // already present

        List<String> collWithIndex = new ArrayList<String>();
        collWithIndex.add("B");
        collWithIndex.add("C");
        assertTrue(uniqueList.addAll(1, collWithIndex));
        assertEquals("A", uniqueList.get(0));
        assertEquals("C", uniqueList.get(1));
        assertEquals("B", uniqueList.get(2));
    }

    @Test
    public void testSetMethod() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> uniqueList = SetUniqueList.setUniqueList(list);
        uniqueList.add("A");
        uniqueList.add("B");
        uniqueList.add("C");

        // Swap with existing item already in list at different index
        String old = uniqueList.set(0, "B");
        assertEquals("A", old);
        assertEquals(2, uniqueList.size());
        assertEquals("B", uniqueList.get(0));
        assertEquals("C", uniqueList.get(1));

        // Set with new item
        String old2 = uniqueList.set(1, "D");
        assertEquals("C", old2);
        assertEquals("D", uniqueList.get(1));
    }

    @Test
    public void testRemoveOperations() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> uniqueList = SetUniqueList.setUniqueList(list);
        uniqueList.add("A");
        uniqueList.add("B");
        uniqueList.add("C");

        assertTrue(uniqueList.remove("B"));
        assertFalse(uniqueList.remove("NonExistent"));
        assertEquals(2, uniqueList.size());

        String removed = uniqueList.remove(0);
        assertEquals("A", removed);
        assertEquals(1, uniqueList.size());

        List<String> toRemove = new ArrayList<String>();
        toRemove.add("C");
        toRemove.add("X");
        assertTrue(uniqueList.removeAll(toRemove));
        assertTrue(uniqueList.isEmpty());
    }

    @Test
    public void testRetainAll() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> uniqueList = SetUniqueList.setUniqueList(list);
        uniqueList.add("A");
        uniqueList.add("B");
        uniqueList.add("C");

        List<String> retain = new ArrayList<String>();
        retain.add("A");
        retain.add("C");
        retain.add("D");

        assertTrue(uniqueList.retainAll(retain));
        assertEquals(2, uniqueList.size());
        assertTrue(uniqueList.contains("A"));
        assertTrue(uniqueList.contains("C"));
        assertFalse(uniqueList.contains("B"));

        // Retain all identical
        assertFalse(uniqueList.retainAll(retain));

        // Retain none
        List<String> retainNone = new ArrayList<String>();
        assertTrue(uniqueList.retainAll(retainNone));
        assertTrue(uniqueList.isEmpty());
    }

    @Test
    public void testClearAndContains() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> uniqueList = SetUniqueList.setUniqueList(list);
        uniqueList.add("A");
        uniqueList.add("B");

        assertTrue(uniqueList.contains("A"));
        
        List<String> sub = new ArrayList<String>();
        sub.add("A");
        sub.add("B");
        assertTrue(uniqueList.containsAll(sub));

        uniqueList.clear();
        assertTrue(uniqueList.isEmpty());
        assertFalse(uniqueList.contains("A"));
    }

    @Test
    public void testIteratorsAndSubList() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> uniqueList = SetUniqueList.setUniqueList(list);
        uniqueList.add("A");
        uniqueList.add("B");
        uniqueList.add("C");

        Iterator<String> it = uniqueList.iterator();
        assertTrue(it.hasNext());
        assertEquals("A", it.next());
        it.remove();
        assertEquals(2, uniqueList.size());

        ListIterator<String> lit = uniqueList.listIterator();
        assertTrue(lit.hasNext());
        assertEquals("B", lit.next());
        assertEquals("B", lit.previous());
        assertEquals("B", lit.next());

        ListIterator<String> litIndex = uniqueList.listIterator(1);
        assertTrue(litIndex.hasNext());
        assertEquals("C", litIndex.next());

        List<String> subList = uniqueList.subList(0, 1);
        assertNotNull(subList);
        assertTrue(subList instanceof SetUniqueList);

        // Test ListIterator add method
        ListIterator<String> litAdd = uniqueList.listIterator();
        litAdd.next();
        litAdd.add("B"); // duplicate, should not add
        litAdd.add("New"); // unique, should add
        assertTrue(uniqueList.contains("New"));

        try {
            litAdd.set("X");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }
}