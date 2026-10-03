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
            // Expected
        }
    }

    public void testAddObject() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        boolean added1 = sul.add("A");
        assertTrue(added1);
        boolean added2 = sul.add("A");
        assertFalse(added2);
        assertEquals(1, sul.size());
    }

    public void testAddAtIndex() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add(0, "A");
        sul.add(0, "A"); // Duplicate, should not be added
        assertEquals(1, sul.size());
        assertEquals("A", sul.get(0));

        sul.add(0, "B");
        assertEquals(2, sul.size());
        assertEquals("B", sul.get(0));
        assertEquals("A", sul.get(1));
    }

    public void testAddAllCollection() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        List coll = new ArrayList();
        coll.add("A");
        coll.add("B");
        coll.add("A");

        boolean changed = sul.addAll(coll);
        assertTrue(changed);
        assertEquals(2, sul.size());

        boolean changedAgain = sul.addAll(coll);
        assertFalse(changedAgain);
    }

    public void testAddAllAtIndexCollection() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add("B");

        List coll = new ArrayList();
        coll.add("A");
        coll.add("B");

        boolean changed = sul.addAll(0, coll);
        assertTrue(changed);
        assertEquals(2, sul.size());
        assertEquals("A", sul.get(0));
        assertEquals("B", sul.get(1));
    }

    public void testSetMethod() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add("A");
        sul.add("B");

        // Swap with itself or non-existing
        Object removed = sul.set(0, "C");
        assertEquals("A", removed);
        assertEquals("C", sul.get(0));
        assertEquals("B", sul.get(1));

        // Set existing element to another index position
        sul.set(0, "B");
        assertEquals("B", sul.get(0));
        assertEquals(1, sul.size());
    }

    public void testRemoveObject() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add("A");
        boolean removed = sul.remove("A");
        assertTrue(removed);
        assertFalse(sul.contains("A"));
        assertTrue(sul.isEmpty());

        boolean removedNonExistent = sul.remove("B");
        assertFalse(removedNonExistent);
    }

    public void testRemoveIndex() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add("A");
        Object removed = sul.remove(0);
        assertEquals("A", removed);
        assertTrue(sul.isEmpty());
    }

    public void testRemoveAll() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add("A");
        sul.add("B");

        List coll = new ArrayList();
        coll.add("A");

        boolean changed = sul.removeAll(coll);
        assertTrue(changed);
        assertEquals(1, sul.size());
        assertFalse(sul.contains("A"));
        assertTrue(sul.contains("B"));
    }

    public void testRetainAll() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add("A");
        sul.add("B");

        List coll = new ArrayList();
        coll.add("A");

        boolean changed = sul.retainAll(coll);
        assertTrue(changed);
        assertEquals(1, sul.size());
        assertTrue(sul.contains("A"));
        assertFalse(sul.contains("B"));
    }

    public void testClear() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add("A");
        sul.add("B");
        sul.clear();
        assertTrue(sul.isEmpty());
        assertFalse(sul.contains("A"));
    }

    public void testContainsAndContainsAll() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add("A");
        sul.add("B");

        assertTrue(sul.contains("A"));
        assertFalse(sul.contains("C"));

        List coll = new ArrayList();
        coll.add("A");
        coll.add("B");
        assertTrue(sul.containsAll(coll));

        coll.add("C");
        assertFalse(sul.containsAll(coll));
    }

    public void testIteratorOperations() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add("A");
        sul.add("B");

        Iterator it = sul.iterator();
        assertTrue(it.hasNext());
        assertEquals("A", it.next());
        it.remove();
        assertFalse(sul.contains("A"));
        assertEquals(1, sul.size());
    }

    public void testListIteratorOperations() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add("A");
        sul.add("B");

        ListIterator lit = sul.listIterator();
        assertTrue(lit.hasNext());
        assertEquals("A", lit.next());
        assertEquals("B", lit.next());
        assertTrue(lit.hasPrevious());
        assertEquals("B", lit.previous());

        lit.remove();
        assertFalse(sul.contains("B"));

        lit.add("C");
        assertTrue(sul.contains("C"));

        try {
            lit.set("D");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        ListIterator litIndex = sul.listIterator(1);
        assertNotNull(litIndex);
    }

    public void testSubList() throws Throwable {
        SetUniqueList sul = SetUniqueList.decorate(new ArrayList());
        sul.add("A");
        sul.add("B");
        sul.add("C");

        List sub = sul.subList(1, 3);
        assertNotNull(sub);
        assertEquals(2, sub.size());
        assertEquals("B", sub.get(0));
        assertEquals("C", sub.get(1));
    }
}