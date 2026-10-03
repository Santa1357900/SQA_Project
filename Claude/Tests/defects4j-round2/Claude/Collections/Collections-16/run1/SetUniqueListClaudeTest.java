package org.apache.commons.collections.list;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.junit.Test;
import static org.junit.Assert.*;

public class SetUniqueListClaudeTest {

    // decorate(List): list == null -> IllegalArgumentException
    @Test
    public void testDecorate_nullList_throwsIllegalArgumentException() throws Throwable {
        try {
            SetUniqueList.decorate(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // decorate(List): list.isEmpty() branch
    @Test
    public void testDecorate_emptyList_returnsEmptySetUniqueList() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        assertEquals(0, sl.size());
        assertTrue(sl.isEmpty());
    }

    // decorate(List): non-empty with duplicates, only first occurrence kept
    @Test
    public void testDecorate_listWithDuplicates_keepsFirstOccurrenceOnly() throws Throwable {
        List list = new ArrayList();
        list.add("A"); list.add("B"); list.add("A"); list.add("C");
        SetUniqueList sl = SetUniqueList.decorate(list);
        assertEquals(3, sl.size());
        assertEquals("A", sl.get(0));
        assertEquals("B", sl.get(1));
        assertEquals("C", sl.get(2));
    }

    // constructor: set == null -> IllegalArgumentException
    @Test
    public void testConstructor_nullSet_throwsIllegalArgumentException() throws Throwable {
        try {
            new SetUniqueList(new ArrayList(), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // asSet(): returned set reflects list contents
    @Test
    public void testAsSet_returnsSetWithSameElements() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B");
        Set s = sl.asSet();
        assertEquals(2, s.size());
        assertTrue(s.contains("A"));
        assertTrue(s.contains("B"));
    }

    // asSet(): unmodifiable view, mutation throws UnsupportedOperationException
    @Test
    public void testAsSet_addThrowsUnsupportedOperationException() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        Set s = sl.asSet();
        try {
            s.add("X");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // add(Object): new element -> true, size increases
    @Test
    public void testAdd_newElement_returnsTrueAndIncreasesSize() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        boolean result = sl.add("A");
        assertTrue(result);
        assertEquals(1, sl.size());
    }

    // add(Object): duplicate element -> false, size unchanged
    @Test
    public void testAdd_duplicateElement_returnsFalseAndSizeUnchanged() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A");
        boolean result = sl.add("A");
        assertFalse(result);
        assertEquals(1, sl.size());
    }

    // add(int,Object): new element inserted at given index
    @Test
    public void testAddIndex_newElement_insertedAtIndex() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("C");
        sl.add(1, "B");
        assertEquals("B", sl.get(1));
        assertEquals(3, sl.size());
    }

    // add(int,Object): duplicate -> no insertion, no exception
    @Test
    public void testAddIndex_duplicateElement_notInsertedNoException() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B");
        sl.add(0, "A");
        assertEquals(2, sl.size());
        assertEquals("A", sl.get(0));
    }

    // addAll(Collection): duplicates ignored, new elements appended in order
    @Test
    public void testAddAllCollection_withDuplicatesAndNew_onlyNewAdded() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A");
        List coll = new ArrayList();
        coll.add("A"); coll.add("B"); coll.add("C");
        boolean changed = sl.addAll(coll);
        assertTrue(changed);
        assertEquals(3, sl.size());
        assertEquals("B", sl.get(1));
        assertEquals("C", sl.get(2));
    }

    // addAll(Collection): empty collection -> 0 iterations, returns false
    @Test
    public void testAddAllCollection_emptyCollection_returnsFalse() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A");
        boolean changed = sl.addAll(new ArrayList());
        assertFalse(changed);
        assertEquals(1, sl.size());
    }

    // addAll(int,Collection): index only advances on actual insertion (duplicate skip branch)
    @Test
    public void testAddAllIndex_insertsAtCorrectIndexSkippingDuplicates() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("D");
        List coll = new ArrayList();
        coll.add("A"); coll.add("B"); coll.add("C");
        boolean changed = sl.addAll(1, coll);
        assertTrue(changed);
        assertEquals(4, sl.size());
        assertEquals("A", sl.get(0));
        assertEquals("B", sl.get(1));
        assertEquals("C", sl.get(2));
        assertEquals("D", sl.get(3));
    }

    // set(int,Object): pos == -1 branch, normal replace
    @Test
    public void testSet_newValue_replacesAndReturnsOld() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B");
        Object removed = sl.set(0, "C");
        assertEquals("A", removed);
        assertEquals("C", sl.get(0));
        assertFalse(sl.contains("A"));
        assertTrue(sl.contains("C"));
    }

    // set(int,Object): pos != -1 && pos != index branch, duplicate removed and list shifts
    @Test
    public void testSet_valueExistsElsewhere_removesDuplicateAndShiftsList() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B"); sl.add("C");
        Object removed = sl.set(0, "C");
        assertEquals("A", removed);
        assertEquals(2, sl.size());
        assertEquals("C", sl.get(0));
        assertEquals("B", sl.get(1));
    }

    // set(int,Object): object set at its own current index (pos == index) must keep it in the internal set
    @Test
    public void testSet_sameValueSameIndex_elementStillInSetAfter() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B");
        Object removed = sl.set(0, "A");
        assertEquals("A", removed);
        assertTrue(sl.contains("A"));
    }

    // remove(Object): existing element removed from list and set
    @Test
    public void testRemoveObject_existing_removesFromListAndSet() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B");
        boolean result = sl.remove("A");
        assertTrue(result);
        assertFalse(sl.contains("A"));
        assertEquals(1, sl.size());
    }

    // remove(Object): non-existing element -> false
    @Test
    public void testRemoveObject_nonExisting_returnsFalse() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A");
        boolean result = sl.remove("Z");
        assertFalse(result);
        assertEquals(1, sl.size());
    }

    // remove(int): removes by index and updates set
    @Test
    public void testRemoveIndex_removesAndReturnsElementAndUpdatesSet() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B");
        Object removed = sl.remove(0);
        assertEquals("A", removed);
        assertFalse(sl.contains("A"));
        assertEquals(1, sl.size());
    }

    // removeAll(Collection): removes elements present in given collection
    @Test
    public void testRemoveAll_removesCommonElements() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B"); sl.add("C");
        List toRemove = new ArrayList();
        toRemove.add("A"); toRemove.add("C");
        boolean result = sl.removeAll(toRemove);
        assertTrue(result);
        assertEquals(1, sl.size());
        assertTrue(sl.contains("B"));
    }

    // retainAll(Collection): keeps only elements present in given collection
    @Test
    public void testRetainAll_keepsOnlySpecified() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B"); sl.add("C");
        List toRetain = new ArrayList();
        toRetain.add("B");
        boolean result = sl.retainAll(toRetain);
        assertTrue(result);
        assertEquals(1, sl.size());
        assertTrue(sl.contains("B"));
        assertFalse(sl.contains("A"));
    }

    // clear(): empties both list and internal set
    @Test
    public void testClear_emptiesListAndSet() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B");
        sl.clear();
        assertEquals(0, sl.size());
        assertFalse(sl.contains("A"));
    }

    // contains(Object): delegates to internal set
    @Test
    public void testContains_delegatesToSet() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A");
        assertTrue(sl.contains("A"));
        assertFalse(sl.contains("Z"));
    }

    // containsAll(Collection): delegates to internal set
    @Test
    public void testContainsAll_delegatesToSet() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B");
        List check = new ArrayList();
        check.add("A"); check.add("B");
        assertTrue(sl.containsAll(check));
        check.add("Z");
        assertFalse(sl.containsAll(check));
    }

    // iterator(): remove() updates internal set
    @Test
    public void testIterator_removeUpdatesSetAndContains() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B");
        Iterator it = sl.iterator();
        Object first = it.next();
        it.remove();
        assertFalse(sl.contains(first));
        assertEquals(1, sl.size());
    }

    // listIterator(): next()/previous() traversal
    @Test
    public void testListIterator_nextAndPrevious() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B");
        ListIterator lit = sl.listIterator();
        Object n1 = lit.next();
        Object n2 = lit.next();
        assertEquals("A", n1);
        assertEquals("B", n2);
        Object p = lit.previous();
        assertEquals("B", p);
    }

    // listIterator(): add(Object) with duplicate is a no-op
    @Test
    public void testListIterator_addDuplicate_noOp() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A");
        ListIterator lit = sl.listIterator();
        lit.next();
        lit.add("A");
        assertEquals(1, sl.size());
    }

    // listIterator(): add(Object) with new element inserts and updates set
    @Test
    public void testListIterator_addNew_insertsAndUpdatesSet() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A");
        ListIterator lit = sl.listIterator();
        lit.next();
        lit.add("B");
        assertEquals(2, sl.size());
        assertTrue(sl.contains("B"));
    }

    // listIterator(): remove() updates internal set
    @Test
    public void testListIterator_remove_updatesSet() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B");
        ListIterator lit = sl.listIterator();
        lit.next();
        lit.remove();
        assertFalse(sl.contains("A"));
        assertEquals(1, sl.size());
    }

    // listIterator(): set(Object) always throws UnsupportedOperationException
    @Test
    public void testListIterator_set_throwsUnsupportedOperationException() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A");
        ListIterator lit = sl.listIterator();
        lit.next();
        try {
            lit.set("B");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // listIterator(int): starts traversal at given index
    @Test
    public void testListIteratorIndex_startsAtGivenIndex() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B"); sl.add("C");
        ListIterator lit = sl.listIterator(1);
        Object n = lit.next();
        assertEquals("B", n);
    }

    // subList(from,to): returns SetUniqueList view preserving original order/content
    @Test
    public void testSubList_sharesUniquenessConstraint() throws Throwable {
        List list = new ArrayList();
        SetUniqueList sl = SetUniqueList.decorate(list);
        sl.add("A"); sl.add("B"); sl.add("C");
        List sub = sl.subList(0, 2);
        assertEquals(2, sub.size());
        assertEquals("A", sub.get(0));
        assertEquals("B", sub.get(1));
    }
}
