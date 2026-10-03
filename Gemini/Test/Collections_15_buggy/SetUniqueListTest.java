package org.apache.commons.collections.list;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public class SetUniqueListTest extends TestCase {

    public SetUniqueListTest(String name) {
        super(name);
    }

    public void testDecorateNullList() throws Throwable {
        try {
            SetUniqueList.decorate(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("List must not be null"));
        }
    }

    public void testDecorateEmptyList() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sul = SetUniqueList.decorate(list);
        assertNotNull(sul);
        assertTrue(sul.isEmpty());
    }

    public void testDecorateListWithDuplicates() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        list.add("A");
        list.add("C");
        list.add("B");

        SetUniqueList sul = SetUniqueList.decorate(list);
        assertEquals(3, sul.size());
        assertEquals("A", sul.get(0));
        assertEquals("B", sul.get(1));
        assertEquals("C", sul.get(2));
    }

    public void testConstructorNullSet() throws Throwable {
        List list = new ArrayList();
        try {
            new SetUniqueList(list, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Set must not be null"));
        }
    }

    public void testAsSet() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        SetUniqueList sul = SetUniqueList.decorate(list);
        Set set = sul.asSet();
        assertNotNull(set);
        assertTrue(set.contains("A"));
        
        try {
            set.add("B");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected because it's an unmodifiable set
        }
    }

    public void testAddObject() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sul = SetUniqueList.decorate(list);

        boolean added1 = sul.add("A");
        assertTrue(added1);
        assertEquals(1, sul.size());

        boolean added2 = sul.add("A");
        assertFalse(added2);
        assertEquals(1, sul.size());
    }

    public void testAddAtIndex() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sul = SetUniqueList.decorate(list);

        sul.add(0, "A");
        sul.add(0, "B");
        assertEquals(2, sul.size());
        assertEquals("B", sul.get(0));
        assertEquals("A", sul.get(1));

        // Add duplicate should do nothing
        sul.add(0, "A");
        assertEquals(2, sul.size());
        assertEquals("B", sul.get(0));
        assertEquals("A", sul.get(1));
    }

    public void testAddAllCollection() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sul = SetUniqueList.decorate(list);

        List toAdd = new ArrayList();
        toAdd.add("A");
        toAdd.add("B");
        toAdd.add("A");

        boolean changed = sul.addAll(toAdd);
        assertTrue(changed);
        assertEquals(2, sul.size());
        assertEquals("A", sul.get(0));
        assertEquals("B", sul.get(1));

        boolean changedAgain = sul.addAll(toAdd);
        assertFalse(changedAgain);
    }

    public void testAddAllIndexCollection() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        SetUniqueList sul = SetUniqueList.decorate(list);

        List toAdd = new ArrayList();
        toAdd.add("B");
        toAdd.add("C");
        toAdd.add("A"); // Duplicate

        boolean changed = sul.addAll(0, toAdd);
        assertTrue(changed);
        assertEquals(3, sul.size());
        assertEquals("B", sul.get(0));
        assertEquals("C", sul.get(1));
        assertEquals("A", sul.get(2));
    }

    public void testSetMethod() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        SetUniqueList sul = SetUniqueList.decorate(list);

        // Swap with existing element not at target index
        Object removed = sul.set(0, "B");
        assertEquals("A", removed);
        assertEquals(1, sul.size());
        assertEquals("B", sul.get(0));

        // Set with same element at same index
        List list2 = new ArrayList();
        list2.add("X");
        list2.add("Y");
        SetUniqueList sul2 = SetUniqueList.decorate(list2);
        Object removed2 = sul2.set(0, "X");
        assertEquals("X", removed2);
        assertEquals(2, sul2.size());
        assertEquals("X", sul2.get(0));
        assertEquals("Y", sul2.get(1));
    }

    public void testRemoveObject() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        SetUniqueList sul = SetUniqueList.decorate(list);

        boolean removed = sul.remove("A");
        assertTrue(removed);
        assertFalse(sul.contains("A"));
        assertTrue(sul.isEmpty());

        boolean removedNonExistent = sul.remove("B");
        assertFalse(removedNonExistent);
    }

    public void testRemoveIndex() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        SetUniqueList sul = SetUniqueList.decorate(list);

        Object removed = sul.remove(0);
        assertEquals("A", removed);
        assertTrue(sul.isEmpty());
        assertFalse(sul.contains("A"));
    }

    public void testRemoveAll() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        SetUniqueList sul = SetUniqueList.decorate(list);

        List toRemove = new ArrayList();
        toRemove.add("A");

        boolean changed = sul.removeAll(toRemove);
        assertTrue(changed);
        assertEquals(1, sul.size());
        assertFalse(sul.contains("A"));
        assertTrue(sul.contains("B"));
    }

    public void testRetainAll() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        SetUniqueList sul = SetUniqueList.decorate(list);

        List toRetain = new ArrayList();
        toRetain.add("A");

        boolean changed = sul.retainAll(toRetain);
        assertTrue(changed);
        assertEquals(1, sul.size());
        assertTrue(sul.contains("A"));
        assertFalse(sul.contains("B"));
    }

    public void testClear() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        SetUniqueList sul = SetUniqueList.decorate(list);

        sul.clear();
        assertTrue(sul.isEmpty());
        assertFalse(sul.contains("A"));
    }

    public void testContainsAndContainsAll() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        SetUniqueList sul = SetUniqueList.decorate(list);

        assertTrue(sul.contains("A"));
        assertFalse(sul.contains("C"));

        List coll = new ArrayList();
        coll.add("A");
        coll.add("B");
        assertTrue(sul.containsAll(coll));

        coll.add("C");
        assertFalse(sul.containsAll(coll));
    }

    public void testSubList() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        list.add("C");
        SetUniqueList sul = SetUniqueList.decorate(list);

        List sub = sul.subList(1, 3);
        assertNotNull(sub);
        assertTrue(sub instanceof SetUniqueList);
        assertEquals(2, sub.size());
        assertEquals("B", sub.get(0));
        assertEquals("C", sub.get(1));
    }

    public void testIterator() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        SetUniqueList sul = SetUniqueList.decorate(list);

        Iterator it = sul.iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertEquals("A", it.next());

        it.remove();
        assertFalse(sul.contains("A"));
        assertEquals(1, sul.size());
    }

    public void testListIterator() throws Throwable {
        List list = new ArrayList();
        list.add("A");
        list.add("B");
        SetUniqueList sul = SetUniqueList.decorate(list);

        ListIterator lit = sul.listIterator();
        assertNotNull(lit);
        assertTrue(lit.hasNext());
        assertEquals("A", lit.next());
        assertEquals("B", lit.next());
        assertTrue(lit.hasPrevious());
        assertEquals("B", lit.previous());

        lit.remove();
        assertFalse(sul.contains("B"));

        // Test listIterator with index
        ListIterator litIndex = sul.listIterator(0);
        assertNotNull(litIndex);
        assertEquals("A", litIndex.next());

        // Test SetListListIterator add and unsupported set
        ListIterator setLit = sul.listIterator();
        setLit.next();
        setLit.add("C");
        assertTrue(sul.contains("C"));

        // Try adding duplicate via listIterator
        int sizeBefore = sul.size();
        setLit.add("C");
        assertEquals(sizeBefore, sul.size());

        try {
            setLit.set("X");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }
}