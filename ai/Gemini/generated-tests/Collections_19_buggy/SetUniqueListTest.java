package org.apache.commons.collections.list;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.junit.Test;

public class SetUniqueListTest {

    @Test(expected = IllegalArgumentException.class)
    public void testSetUniqueListNullList() throws Throwable {
        SetUniqueList.setUniqueList(null);
    }

    @Test
    public void testSetUniqueListEmptyList() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        assertNotNull(sul);
        assertTrue(sul.isEmpty());
    }

    @Test
    public void testSetUniqueListWithDuplicates() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("A");
        list.add("B");
        list.add("A");
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        assertEquals(2, sul.size());
        assertEquals("A", sul.get(0));
        assertEquals("B", sul.get(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullSet() throws Throwable {
        List<String> list = new ArrayList<String>();
        new SetUniqueList<String>(list, null);
    }

    @Test
    public void testAsSet() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("One");
        Set<String> setView = sul.asSet();
        assertNotNull(setView);
        assertTrue(setView.contains("One"));
        
        try {
            setView.add("Two");
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    public void testAddObject() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        
        boolean added1 = sul.add("A");
        assertTrue(added1);
        
        boolean added2 = sul.add("A");
        assertFalse(added2);
        
        assertEquals(1, sul.size());
    }

    @Test
    public void testAddAtIndex() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        
        sul.add(0, "A");
        sul.add(0, "B");
        sul.add(1, "A"); // Duplicate, should not be added
        
        assertEquals(2, sul.size());
        assertEquals("B", sul.get(0));
        assertEquals("A", sul.get(1));
    }

    @Test
    public void testAddAllCollection() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        
        List<String> toAdd = new ArrayList<String>();
        toAdd.add("X");
        toAdd.add("Y");
        toAdd.add("X");
        
        boolean changed = sul.addAll(toAdd);
        assertTrue(changed);
        assertEquals(2, sul.size());
        
        boolean changedAgain = sul.addAll(toAdd);
        assertFalse(changedAgain);
    }

    @Test
    public void testAddAllAtIndex() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("A");
        
        List<String> toAdd = new ArrayList<String>();
        toAdd.add("B");
        toAdd.add("A");
        
        boolean changed = sul.addAll(0, toAdd);
        assertTrue(changed);
        assertEquals(2, sul.size());
        assertEquals("B", sul.get(0));
        assertEquals("A", sul.get(1));
    }

    @Test
    public void testSetMethod() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("A");
        sul.add("B");
        
        // Replace index 0 with "B" (already exists at index 1)
        String old = sul.set(0, "B");
        assertEquals("A", old);
        assertEquals(1, sul.size());
        assertEquals("B", sul.get(0));
        
        // Normal set where object is not a duplicate
        sul.add("C");
        String old2 = sul.set(0, "D");
        assertEquals("B", old2);
        assertTrue(sul.contains("D"));
        assertFalse(sul.contains("B"));
    }

    @Test
    public void testRemoveObject() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("A");
        
        boolean removed = sul.remove("NonExistent");
        assertFalse(removed);
        
        boolean removed2 = sul.remove("A");
        assertTrue(removed2);
        assertFalse(sul.contains("A"));
        assertTrue(sul.isEmpty());
    }

    @Test
    public void testRemoveIndex() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("A");
        sul.add("B");
        
        String removed = sul.remove(0);
        assertEquals("A", removed);
        assertFalse(sul.contains("A"));
        assertTrue(sul.contains("B"));
    }

    @Test
    public void testRemoveAll() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("A");
        sul.add("B");
        sul.add("C");
        
        List<String> toRemove = new ArrayList<String>();
        toRemove.add("A");
        toRemove.add("C");
        
        boolean changed = sul.removeAll(toRemove);
        assertTrue(changed);
        assertEquals(1, sul.size());
        assertEquals("B", sul.get(0));
    }

    @Test
    public void testRetainAll() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("A");
        sul.add("B");
        sul.add("C");
        
        // Case 1: retain all elements (no change)
        List<String> retainList1 = new ArrayList<String>();
        retainList1.add("A");
        retainList1.add("B");
        retainList1.add("C");
        assertFalse(sul.retainAll(retainList1));
        
        // Case 2: retain none (clear)
        List<String> retainList2 = new ArrayList<String>();
        assertTrue(sul.retainAll(retainList2));
        assertTrue(sul.isEmpty());
        
        // Case 3: partial retain
        sul.add("A");
        sul.add("B");
        sul.add("C");
        List<String> retainList3 = new ArrayList<String>();
        retainList3.add("A");
        retainList3.add("X");
        assertTrue(sul.retainAll(retainList3));
        assertEquals(1, sul.size());
        assertEquals("A", sul.get(0));
    }

    @Test
    public void testClear() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("A");
        sul.add("B");
        
        sul.clear();
        assertTrue(sul.isEmpty());
        assertFalse(sul.contains("A"));
    }

    @Test
    public void testContainsAndContainsAll() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("A");
        sul.add("B");
        
        assertTrue(sul.contains("A"));
        assertFalse(sul.contains("C"));
        
        List<String> sub = new ArrayList<String>();
        sub.add("A");
        sub.add("B");
        assertTrue(sul.containsAll(sub));
        
        sub.add("C");
        assertFalse(sul.containsAll(sub));
    }

    @Test
    public void testIterator() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("A");
        sul.add("B");
        
        Iterator<String> it = sul.iterator();
        assertTrue(it.hasNext());
        assertEquals("A", it.next());
        
        it.remove();
        assertFalse(sul.contains("A"));
        assertEquals(1, sul.size());
    }

    @Test
    public void testListIterator() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("A");
        sul.add("B");
        
        ListIterator<String> lit = sul.listIterator();
        assertTrue(lit.hasNext());
        assertEquals("A", lit.next());
        assertEquals("B", lit.next());
        assertTrue(lit.hasPrevious());
        assertEquals("B", lit.previous());
        
        lit.remove();
        assertFalse(sul.contains("B"));
        
        // Test SetListListIterator add
        ListIterator<String> lit2 = sul.listIterator();
        lit2.next();
        lit2.add("C");
        assertTrue(sul.contains("C"));
        
        // Duplicate add via list iterator
        lit2.add("A"); // Should ignore because "A" is already present
        assertEquals(2, sul.size());
        
        try {
            lit2.set("D");
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
        
        ListIterator<String> litIndex = sul.listIterator(1);
        assertNotNull(litIndex);
    }

    @Test
    public void testSubList() throws Throwable {
        List<String> list = new ArrayList<String>();
        SetUniqueList<String> sul = SetUniqueList.setUniqueList(list);
        sul.add("A");
        sul.add("B");
        sul.add("C");
        
        List<String> sub = sul.subList(1, 3);
        assertNotNull(sub);
        assertEquals(2, sub.size());
        assertEquals("B", sub.get(0));
        assertEquals("C", sub.get(1));
    }

    @Test
    public void testCreateSetBasedOnListNonHashSet() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("A");
        
        // Use a custom Set implementation to test the reflection / instantiation branch
        SetUniqueList<String> sul = new SetUniqueList<String>(new ArrayList<String>(), new HashSet<String>()) {
            @Override
            protected Set<String> createSetBasedOnList(Set<String> set, List<String> list) {
                return super.createSetBasedOnList(set, list);
            }
        };
        
        Set<String> customSet = new java.util.TreeSet<String>();
        Set<String> result = sul.createSetBasedOnList(customSet, list);
        assertNotNull(result);
        assertTrue(result.contains("A"));
    }

}