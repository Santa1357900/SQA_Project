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
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);
        assertNotNull(sul);
        assertTrue(sul.isEmpty());
    }

    public void testDecorateWithDuplicates() throws Throwable {
        List list = new ArrayList<String>();
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
        assertTrue(sul.contains("A"));
        assertTrue(sul.contains("B"));
        assertTrue(sul.contains("C"));
    }

    public void testConstructorNullSet() throws Throwable {
        List list = new ArrayList<String>();
        try {
            new SetUniqueList(list, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Set must not be null"));
        }
    }

    public void testAsSet() throws Throwable {
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);
        sul.add("One");
        Set set = sul.asSet();
        assertNotNull(set);
        assertTrue(set.contains("One"));
        try {
            set.add("Two");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    public void testAddDuplicateAndUnique() throws Throwable {
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);

        boolean added1 = sul.add("A");
        assertTrue(added1);
        assertEquals(1, sul.size());

        boolean added2 = sul.add("A");
        assertFalse(added2);
        assertEquals(1, sul.size());

        boolean added3 = sul.add(0, "B");
        // add(int, Object) does not return boolean, but let's check size/content
        assertEquals(2, sul.size());
        assertEquals("B", sul.get(0));

        sul.add(0, "A"); // Already exists, should not be added or moved depending on logic (contains check prevents it)
        assertEquals(2, sul.size());
        assertEquals("B", sul.get(0));
    }

    public void testAddAllCollection() throws Throwable {
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);

        List coll = new ArrayList<String>();
        coll.add("X");
        coll.add("Y");
        coll.add("X");

        boolean changed = sul.addAll(coll);
        assertTrue(changed);
        assertEquals(2, sul.size());
        assertEquals("X", sul.get(0));
        assertEquals("Y", sul.get(1));

        boolean changedAgain = sul.addAll(coll);
        assertFalse(changedAgain);
    }

    public void testAddAllAtIndexCollection() throws Throwable {
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);
        sul.add("A");
        sul.add("C");

        List coll = new ArrayList<String>();
        coll.add("B");
        coll.add("A"); // duplicate

        boolean changed = sul.addAll(1, coll);
        assertTrue(changed);
        assertEquals(3, sul.size());
        assertEquals("A", sul.get(0));
        assertEquals("B", sul.get(1));
        assertEquals("C", sul.get(2));
    }

    public void testSetMethod() throws Throwable {
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);
        sul.add("A");
        sul.add("B");

        // Set index 0 to "B" (which is already at index 1)
        Object removed = sul.set(0, "B");
        assertEquals("A", removed);
        assertEquals(1, sul.size());
        assertEquals("B", sul.get(0));

        // Set index 0 to "C" (not in list)
        Object removed2 = sul.set(0, "C");
        assertEquals("B", removed2);
        assertEquals(1, sul.size());
        assertEquals("C", sul.get(0));
    }

    public void testRemoveObjectAndIndex() throws Throwable {
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);
        sul.add("A");
        sul.add("B");

        boolean removedObj = sul.remove("A");
        assertTrue(removedObj);
        assertFalse(sul.contains("A"));
        assertEquals(1, sul.size());

        Object removedIdx = sul.remove(0);
        assertEquals("B", removedIdx);
        assertTrue(sul.isEmpty());
        assertFalse(sul.contains("B"));
    }

    jpublic void testRemoveAllAndRetainAll() throws Throwable {
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);
        sul.add("A");
        sul.add("B");
        sul.add("C");

        List toRemove = new ArrayList<String>();
        toRemove.add("A");
        toRemove.add("B");

        boolean remResult = sul.removeAll(toRemove);
        assertTrue(remResult);
        assertEquals(1, sul.size());
        assertTrue(sul.contains("C"));
        assertFalse(sul.set.contains("A"));
        assertFalse(sul.set.contains("B"));

        sul.add("A");
        List toRetain = new ArrayList<String>();
        toRetain.add("C");

        boolean retResult = sul.retainAll(toRetain);
        assertTrue(retResult);
        assertEquals(1, sul.size());
        assertTrue(sul.contains("C"));
        assertFalse(sul.contains("A"));
    }

    public void testClearAndContainsAll() throws Throwable {
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);
        sul.add("A");
        sul.add("B");

        List coll = new ArrayList<String>();
        coll.add("A");
        coll.add("B");

        assertTrue(sul.containsAll(coll));

        sul.clear();
        assertTrue(sul.isEmpty());
        assertTrue(sul.set.isEmpty());
    }

    public void testIteratorAndRemove() throws Throwable {
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);
        sul.add("A");
        sul.add("B");

        Iterator it = sul.iterator();
        assertTrue(it.hasNext());
        assertEquals("A", it.next());
        it.remove();

        assertEquals(1, sul.size());
        assertFalse(sul.contains("A"));
        assertFalse(sul.set.contains("A"));
    }

    public void testListIteratorOperations() throws Throwable {
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);
        sul.add("A");
        sul.add("B");

        ListIterator lit = sul.listIterator();
        assertTrue(lit.hasNext());
        assertEquals("A", lit.next());
        assertEquals("B", lit.next());
        assertTrue(lit.hasPrevious());
        assertEquals("B", lit.previous());

        lit.remove();
        assertEquals(1, sul.size());
        assertFalse(sul.contains("B"));

        // test listIterator with index
        ListIterator litIndex = sul.listIterator(0);
        assertTrue(litIndex.hasNext());

        // test listIterator add unique and duplicate
        litIndex.next();
        litIndex.add("C");
        assertTrue(sul.contains("C"));

        litIndex.add("A"); // duplicate, should not be added
        assertEquals(2, sul.size());

        try {
            litIndex.set("D");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    public void testSubList() throws Throwable {
        List list = new ArrayList<String>();
        SetUniqueList sul = SetUniqueList.decorate(list);
        sul.add("A");
        sul.add("B");
        sul.add("C");

        List sub = sul.subList(1, 3);
        assertNotNull(sub);
        assertTrue(sub instanceof SetUniqueList);
        assertEquals(2, sub.size());
        assertEquals("B", sub.get(0));
        assertEquals("C", sub.get(1));
    }
}