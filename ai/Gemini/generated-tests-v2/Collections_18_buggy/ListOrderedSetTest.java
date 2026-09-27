package org.apache.commons.collections.set;

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
import java.util.Set;

import org.junit.Test;
import org.apache.commons.collections.OrderedIterator;

public class ListOrderedSetTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
        assertNotNull(set.asList());
        assertTrue(set.asList().isEmpty());
    }

    @Test
    public void testFactorySetNull() throws Throwable {
        try {
            ListOrderedSet.listOrderedSet(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testFactorySetValid() throws Throwable {
        Set<String> innerSet = new HashSet<String>();
        ListOrderedSet<String> set = ListOrderedSet.listOrderedSet(innerSet);
        assertNotNull(set);
        assertTrue(set.isEmpty());
    }

    @Test
    public void testFactorySetAndListNull() throws Throwable {
        Set<String> innerSet = new HashSet<String>();
        List<String> innerList = new ArrayList<String>();
        
        try {
            ListOrderedSet.listOrderedSet(null, innerList);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            ListOrderedSet.listOrderedSet(innerSet, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testFactorySetAndListNotEmpty() throws Throwable {
        Set<String> innerSet = new HashSet<String>();
        innerSet.add("A");
        List<String> innerList = new ArrayList<String>();
        
        try {
            ListOrderedSet.listOrderedSet(innerSet, innerList);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        Set<String> emptySet = new HashSet<String>();
        List<String> nonEmptyList = new ArrayList<String>();
        nonEmptyList.add("A");
        try {
            ListOrderedSet.listOrderedSet(emptySet, nonEmptyList);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testFactorySetAndListValid() throws Throwable {
        Set<String> innerSet = new HashSet<String>();
        List<String> innerList = new ArrayList<String>();
        ListOrderedSet<String> set = ListOrderedSet.listOrderedSet(innerSet, innerList);
        assertNotNull(set);
        assertTrue(set.isEmpty());
    }

    @Test
    public void testFactoryListNull() throws Throwable {
        try {
            ListOrderedSet.listOrderedSet((List<String>) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testFactoryListWithDuplicates() throws Throwable {
        List<String> list = new ArrayList<String>();
        list.add("A");
        list.add("B");
        list.add("A");

        ListOrderedSet<String> set = ListOrderedSet.listOrderedSet(list);
        assertEquals(2, set.size());
        assertEquals("A", set.get(0));
        assertEquals("B", set.get(1));
    }

    @Test
    public void testAddAndGet() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        assertTrue(set.add("One"));
        assertFalse(set.add("One")); // duplicate
        assertTrue(set.add("Two"));

        assertEquals(2, set.size());
        assertEquals("One", set.get(0));
        assertEquals("Two", set.get(1));
        assertEquals(0, set.indexOf("One"));
        assertEquals(1, set.indexOf("Two"));
        assertEquals(-1, set.indexOf("Three"));
    }

    @Test
    public void testAddAtIndex() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        set.add("B");
        set.add(0, "A");
        set.add(0, "A"); // duplicate, should not change anything

        assertEquals(2, set.size());
        assertEquals("A", set.get(0));
        assertEquals("B", set.get(1));
    }

    @Test
    public void testAddAllCollection() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        List<String> list = new ArrayList<String>();
        list.add("A");
        list.add("B");
        list.add("A");

        assertTrue(set.addAll(list));
        assertFalse(set.addAll(list)); // already added
        assertEquals(2, set.size());
        assertEquals("A", set.get(0));
        assertEquals("B", set.get(1));
    }

    @Test
    public void testAddAllIndexCollection() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        set.add("C");

        List<String> list = new ArrayList<String>();
        list.add("A");
        list.add("B");
        list.add("C"); // duplicate

        assertTrue(set.addAll(0, list));
        assertFalse(set.addAll(0, list)); // already present

        assertEquals(3, set.size());
        assertEquals("A", set.get(0));
        assertEquals("B", set.get(1));
        assertEquals("C", set.get(2));
    }

    @Test
    public void testRemoveObject() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        set.add("A");
        set.add("B");

        assertTrue(set.remove("A"));
        assertFalse(set.remove("NonExistent"));
        assertEquals(1, set.size());
        assertEquals("B", set.get(0));
    }

    @Test
    public void testRemoveIndex() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        set.add("A");
        set.add("B");

        Object removed = set.remove(0);
        assertEquals("A", removed);
        assertEquals(1, set.size());
        assertEquals("B", set.get(0));
    }

    @Test
    public void testRemoveAll() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        set.add("A");
        set.add("B");
        set.add("C");

        List<String> toRemove = new ArrayList<String>();
        toRemove.add("A");
        toRemove.add("C");

        assertTrue(set.removeAll(toRemove));
        assertFalse(set.removeAll(toRemove));
        assertEquals(1, set.size());
        assertEquals("B", set.get(0));
    }

    @Test
    public void testRetainAll() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        set.add("A");
        set.add("B");
        set.add("C");

        List<String> toRetain = new ArrayList<String>();
        toRetain.add("A");
        toRetain.add("B");

        assertTrue(set.retainAll(toRetain));
        assertEquals(2, set.size());
        assertEquals("A", set.get(0));
        assertEquals("B", set.get(1));

        // Retain none
        List<String> retainNone = new ArrayList<String>();
        assertTrue(set.retainAll(retainNone));
        assertTrue(set.isEmpty());
        assertTrue(set.asList().isEmpty());
    }

    @Test
    public void testRetainAllNoChange() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        set.add("A");
        set.add("B");

        List<String> toRetain = new ArrayList<String>();
        toRetain.add("A");
        toRetain.add("B");
        toRetain.add("C");

        assertFalse(set.retainAll(toRetain));
        assertEquals(2, set.size());
    }

    @Test
    public void testClear() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        set.add("A");
        set.add("B");
        assertFalse(set.isEmpty());

        set.clear();
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
        assertTrue(set.asList().isEmpty());
    }

    @Test
    public void testToArray() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        set.add("A");
        set.add("B");

        Object[] arr = set.toArray();
        assertNotNull(arr);
        assertEquals(2, arr.length);
        assertEquals("A", arr[0]);
        assertEquals("B", arr[1]);

        String[] typedArr = new String[2];
        String[] res = set.toArray(typedArr);
        assertNotNull(res);
        assertEquals(2, res.length);
        assertEquals("A", res[0]);
        assertEquals("B", res[1]);
    }

    @Test
    public void testToString() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        set.add("A");
        set.add("B");

        String str = set.toString();
        assertNotNull(str);
        assertTrue(str.contains("A"));
        assertTrue(str.contains("B"));
    }

    @Test
    public void testIteratorOperations() throws Throwable {
        ListOrderedSet<String> set = new ListOrderedSet<String>();
        set.add("A");
        set.add("B");
        set.add("C");

        OrderedIterator<String> it = set.iterator();
        assertNotNull(it);

        assertTrue(it.hasNext());
        assertEquals("A", it.next());

        assertTrue(it.hasPrevious());
        assertEquals("A", it.previous());
        assertEquals("A", it.next());

        assertEquals("B", it.next());
        it.remove(); // removes "B"
        assertEquals(2, set.size());
        assertNull(it.last);

        assertEquals("C", it.next());
        assertFalse(it.hasNext());
    }
}