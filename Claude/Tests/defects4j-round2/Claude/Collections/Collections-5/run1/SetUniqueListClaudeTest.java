package org.apache.commons.collections.list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.junit.Test;
import static org.junit.Assert.*;

public class SetUniqueListClaudeTest {

    // decorate(null) must throw IllegalArgumentException
    @Test
    public void testDecorate_nullList_throwsIllegalArgumentException() throws Throwable {
        try {
            SetUniqueList.decorate(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // decorate() with empty list branch returns empty list
    @Test
    public void testDecorate_emptyList_returnsEmptyList() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        assertEquals(0, sl.size());
        assertTrue(sl.isEmpty());
    }

    // decorate() with non-empty list keeps first occurrence, removes duplicates
    @Test
    public void testDecorate_listWithDuplicates_keepsFirstOccurrenceOnly() throws Throwable {
        List src = new ArrayList();
        src.add("A");
        src.add("B");
        src.add("A");
        SetUniqueList sl = SetUniqueList.decorate(src);
        assertEquals(2, sl.size());
        assertEquals("A", sl.get(0));
        assertEquals("B", sl.get(1));
    }

    // protected constructor: null set must throw IllegalArgumentException
    @Test
    public void testConstructor_nullSet_throwsIllegalArgumentException() throws Throwable {
        try {
            new SetUniqueList(new ArrayList(), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // asSet() returns an unmodifiable view
    @Test
    public void testAsSet_isUnmodifiable_addThrowsException() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        Set view = sl.asSet();
        try {
            view.add("B");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // asSet() reflects current elements of the list
    @Test
    public void testAsSet_reflectsCurrentElements() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        Set view = sl.asSet();
        assertEquals(2, view.size());
        assertTrue(view.contains("A"));
        assertTrue(view.contains("B"));
    }

    // add(Object): unique element is appended, returns true
    @Test
    public void testAdd_uniqueObject_returnsTrueAndAppends() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        boolean result = sl.add("B");
        assertTrue(result);
        assertEquals(2, sl.size());
        assertEquals("B", sl.get(1));
    }

    // add(Object): duplicate element is rejected, returns false, size unchanged
    @Test
    public void testAdd_duplicateObject_returnsFalseAndSizeUnchanged() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        boolean result = sl.add("A");
        assertFalse(result);
        assertEquals(1, sl.size());
    }

    // add(int, Object): unique element inserted at specified index
    @Test
    public void testAddIndex_uniqueObject_insertsAtSpecifiedIndex() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        sl.add(1, "C");
        assertEquals(3, sl.size());
        assertEquals("C", sl.get(1));
        assertEquals("B", sl.get(2));
    }

    // add(int, Object): duplicate element is not inserted, list unchanged
    @Test
    public void testAddIndex_duplicateObject_noInsertion() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        sl.add(0, "B");
        assertEquals(2, sl.size());
        assertEquals("A", sl.get(0));
        assertEquals("B", sl.get(1));
    }

    // addAll(Collection): only unique elements are appended
    @Test
    public void testAddAllCollection_mixedDuplicates_onlyUniqueAppended() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        boolean changed = sl.addAll(Arrays.asList(new String[] {"B", "C", "A", "D"}));
        assertTrue(changed);
        assertEquals(4, sl.size());
        assertEquals("C", sl.get(2));
        assertEquals("D", sl.get(3));
    }

    // addAll(Collection): all duplicates -> no change, returns false
    @Test
    public void testAddAllCollection_allDuplicates_returnsFalseNoChange() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        boolean changed = sl.addAll(Arrays.asList(new String[] {"A", "B"}));
        assertFalse(changed);
        assertEquals(2, sl.size());
    }

    // addAll(int, Collection): per contract, elements must be inserted at the
    // given index, not appended at the end. This catches the bug.
    @Test
    public void testAddAllIndex_insertsAtGivenIndexNotAtEnd() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        boolean changed = sl.addAll(1, Arrays.asList(new String[] {"X", "Y"}));
        assertTrue(changed);
        assertEquals(4, sl.size());
        assertEquals("A", sl.get(0));
        assertEquals("X", sl.get(1));
        assertEquals("Y", sl.get(2));
        assertEquals("B", sl.get(3));
    }

    // addAll(int, Collection): empty collection -> no change
    @Test
    public void testAddAllIndex_emptyCollection_returnsFalse() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        boolean changed = sl.addAll(1, new ArrayList());
        assertFalse(changed);
        assertEquals(2, sl.size());
    }

    // set(int, Object): replace with a value that is a duplicate elsewhere ->
    // old duplicate position removed, old value at index returned
    @Test
    public void testSet_replaceWithExistingDuplicateElsewhere_removesOldDuplicate() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        sl.add("C");
        Object removed = sl.set(0, "C");
        assertEquals("A", removed);
        assertEquals(2, sl.size());
        assertEquals("C", sl.get(0));
        assertEquals("B", sl.get(1));
        assertFalse(sl.contains("A"));
    }

    // set(int, Object): setting same value at same index (pos == index) -> no structural change
    @Test
    public void testSet_replaceWithSameValueAtSameIndex_noStructuralChange() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        sl.add("C");
        Object removed = sl.set(0, "A");
        assertEquals("A", removed);
        assertEquals(3, sl.size());
        assertEquals("A", sl.get(0));
    }

    // set(int, Object): replace with a brand new unique value
    @Test
    public void testSet_replaceWithNewUniqueValue_returnsOldValue() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        Object removed = sl.set(0, "Z");
        assertEquals("A", removed);
        assertEquals(2, sl.size());
        assertEquals("Z", sl.get(0));
        assertFalse(sl.contains("A"));
    }

    // remove(Object): present element removed from list and backing set
    @Test
    public void testRemoveObject_present_removesFromListAndSet() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        boolean removedFlag = sl.remove("A");
        assertTrue(removedFlag);
        assertEquals(1, sl.size());
        assertFalse(sl.contains("A"));
    }

    // remove(Object): absent element -> returns false, no change
    @Test
    public void testRemoveObject_absent_returnsFalseNoChange() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        boolean removedFlag = sl.remove("Z");
        assertFalse(removedFlag);
        assertEquals(1, sl.size());
    }

    // remove(int): removes by index and purges element from backing set
    @Test
    public void testRemoveIndex_removesElementAndFromSet() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        Object removed = sl.remove(0);
        assertEquals("A", removed);
        assertEquals(1, sl.size());
        assertFalse(sl.contains("A"));
    }

    // removeAll(Collection): removes all matching elements from list and set
    @Test
    public void testRemoveAll_removesSpecifiedElements() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        sl.add("C");
        boolean changed = sl.removeAll(Arrays.asList(new String[] {"A", "C"}));
        assertTrue(changed);
        assertEquals(1, sl.size());
        assertEquals("B", sl.get(0));
        assertFalse(sl.contains("A"));
    }

    // retainAll(Collection): keeps only the specified elements
    @Test
    public void testRetainAll_keepsOnlySpecifiedElements() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        sl.add("C");
        boolean changed = sl.retainAll(Arrays.asList(new String[] {"B"}));
        assertTrue(changed);
        assertEquals(1, sl.size());
        assertEquals("B", sl.get(0));
        assertFalse(sl.contains("A"));
    }

    // clear(): empties both the list and the backing set
    @Test
    public void testClear_emptiesListAndSet() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        sl.clear();
        assertEquals(0, sl.size());
        assertTrue(sl.isEmpty());
        assertFalse(sl.contains("A"));
    }

    // contains(Object): delegates to backing set
    @Test
    public void testContains_delegatesToInternalSet() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        assertTrue(sl.contains("A"));
        assertFalse(sl.contains("B"));
    }

    // containsAll(Collection): delegates to backing set
    @Test
    public void testContainsAll_delegatesToInternalSet() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        assertTrue(sl.containsAll(Arrays.asList(new String[] {"A", "B"})));
        assertFalse(sl.containsAll(Arrays.asList(new String[] {"A", "C"})));
    }

    // iterator().remove(): also removes element from backing set, freeing it for re-add
    @Test
    public void testIterator_removeAlsoRemovesFromSet() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        sl.add("C");
        Iterator it = sl.iterator();
        it.next();
        it.next();
        it.remove();
        assertEquals(2, sl.size());
        assertFalse(sl.contains("B"));
        assertTrue(sl.add("B"));
    }

    // listIterator().add(Object): unique element inserted before the cursor's next element
    @Test
    public void testListIterator_addUniqueElement_insertsBeforeCursor() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        ListIterator it = sl.listIterator();
        it.next();
        it.add("C");
        assertEquals(3, sl.size());
        assertEquals("C", sl.get(1));
        assertEquals("B", sl.get(2));
    }

    // listIterator().add(Object): duplicate element is silently ignored
    @Test
    public void testListIterator_addDuplicateElement_noInsertion() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        ListIterator it = sl.listIterator();
        it.next();
        it.add("B");
        assertEquals(2, sl.size());
        assertEquals("B", sl.get(1));
    }

    // listIterator().set(Object): explicitly unsupported
    @Test
    public void testListIteratorSet_throwsUnsupportedOperationException() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        ListIterator it = sl.listIterator();
        it.next();
        try {
            it.set("C");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // listIterator(int): cursor starts at the given position
    @Test
    public void testListIteratorIndexed_startsAtGivenPosition() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        sl.add("C");
        ListIterator it = sl.listIterator(1);
        assertEquals(1, it.nextIndex());
        assertEquals("B", it.next());
    }

    // listIterator().previous(): returns the last returned element going backwards
    @Test
    public void testListIterator_previous_returnsCorrectElement() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        ListIterator it = sl.listIterator();
        it.next();
        it.next();
        Object previous = it.previous();
        assertEquals("B", previous);
    }

    // subList(): returns the correct portion and shares uniqueness with the parent list
    @Test
    public void testSubList_sharesSetUniqueness() throws Throwable {
        SetUniqueList sl = SetUniqueList.decorate(new ArrayList());
        sl.add("A");
        sl.add("B");
        sl.add("C");
        sl.add("D");
        List sub = sl.subList(1, 3);
        assertEquals(2, sub.size());
        assertEquals("B", sub.get(0));
        assertEquals("C", sub.get(1));
        boolean added = sub.add("A");
        assertFalse(added);
        assertEquals(2, sub.size());
    }
}
