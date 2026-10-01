package org.jfree.data;

import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.util.SortOrder;

import org.junit.Test;
import static org.junit.Assert.*;

public class DefaultKeyedValuesClaudeTest {

    // Constructor: new instance should be empty
    @Test
    public void testConstructor_initialState_emptyCollection() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        assertEquals(0, dkv.getItemCount());
    }

    // getValue(int): out of bounds index throws IndexOutOfBoundsException
    @Test
    public void testGetValueInt_outOfBounds_throwsException() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        try {
            dkv.getValue(0);
            fail("expected IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException expected) {
        }
    }

    // getKey(int): out of bounds index throws IndexOutOfBoundsException
    @Test
    public void testGetKeyInt_outOfBounds_throwsException() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        try {
            dkv.getKey(0);
            fail("expected IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException expected) {
        }
    }

    // getIndex(Comparable): null key throws IllegalArgumentException
    @Test
    public void testGetIndex_nullKey_throwsIllegalArgumentException() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        try {
            dkv.getIndex(null);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // getIndex(Comparable): unknown key returns -1
    @Test
    public void testGetIndex_unknownKey_returnsNegativeOne() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        assertEquals(-1, dkv.getIndex("Z"));
    }

    // getIndex(Comparable): known key returns correct index
    @Test
    public void testGetIndex_knownKey_returnsCorrectIndex() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        dkv.addValue("B", 2.0);
        assertEquals(1, dkv.getIndex("B"));
    }

    // getKeys(): returned list is a copy, modifying it doesn't affect internal state
    @Test
    public void testGetKeys_returnsCopy_modificationDoesNotAffectInternal() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        List keys = dkv.getKeys();
        keys.add("EXTRA");
        assertEquals(1, dkv.getItemCount());
    }

    // getValue(Comparable): unknown key throws UnknownKeyException
    @Test
    public void testGetValueComparable_unknownKey_throwsUnknownKeyException() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        try {
            dkv.getValue("Z");
            fail("expected UnknownKeyException");
        }
        catch (UnknownKeyException expected) {
        }
    }

    // getValue(Comparable): known key returns correct value
    @Test
    public void testGetValueComparable_knownKey_returnsValue() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 5.5);
        assertEquals(5.5, dkv.getValue("A").doubleValue(), 1e-9);
    }

    // addValue(Comparable, double): adds a new key/value pair
    @Test
    public void testAddValueDouble_newKey_addsValue() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 3.0);
        assertEquals(1, dkv.getItemCount());
        assertEquals(3.0, dkv.getValue(0).doubleValue(), 1e-9);
    }

    // addValue(Comparable, Number): null value is permitted
    @Test
    public void testAddValueNumber_nullValue_allowed() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", (Number) null);
        assertNull(dkv.getValue(0));
    }

    // setValue(Comparable, Number): existing key updates value, does not add new item
    @Test
    public void testSetValueNumber_existingKey_updatesValueNotAddNew() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        dkv.setValue("A", new Double(9.0));
        assertEquals(1, dkv.getItemCount());
        assertEquals(9.0, dkv.getValue("A").doubleValue(), 1e-9);
    }

    // setValue(Comparable, Number): null key throws IllegalArgumentException
    @Test
    public void testSetValueNumber_nullKey_throwsIllegalArgumentException() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        try {
            dkv.setValue(null, new Double(1.0));
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // insertValue(int, Comparable, Number): position negative throws IllegalArgumentException
    @Test
    public void testInsertValueNumber_negativePosition_throwsIllegalArgumentException() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        try {
            dkv.insertValue(-1, "A", new Double(1.0));
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // insertValue(int, Comparable, Number): position greater than item count throws IllegalArgumentException
    @Test
    public void testInsertValueNumber_positionTooLarge_throwsIllegalArgumentException() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        try {
            dkv.insertValue(1, "A", new Double(1.0));
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // insertValue(int, Comparable, Number): null key throws IllegalArgumentException
    @Test
    public void testInsertValueNumber_nullKey_throwsIllegalArgumentException() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        try {
            dkv.insertValue(0, null, new Double(1.0));
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // insertValue: inserting a new key at a given position inserts correctly
    @Test
    public void testInsertValueNumber_newKeyAtPosition_insertsCorrectly() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        dkv.addValue("C", 3.0);
        dkv.insertValue(1, "B", new Double(2.0));
        assertEquals(3, dkv.getItemCount());
        assertEquals("B", dkv.getKey(1));
        assertEquals(2.0, dkv.getValue(1).doubleValue(), 1e-9);
    }

    // insertValue: existing key at the same position just updates the value
    @Test
    public void testInsertValueNumber_existingKeySamePosition_updatesValue() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        dkv.addValue("B", 2.0);
        dkv.insertValue(1, "B", new Double(99.0));
        assertEquals(2, dkv.getItemCount());
        assertEquals(99.0, dkv.getValue("B").doubleValue(), 1e-9);
    }

    // insertValue: existing key moved to a different position rebuilds order
    @Test
    public void testInsertValueNumber_existingKeyDifferentPosition_movesKey() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        dkv.addValue("B", 2.0);
        dkv.addValue("C", 3.0);
        dkv.insertValue(0, "C", new Double(3.0));
        assertEquals(3, dkv.getItemCount());
        assertEquals("C", dkv.getKey(0));
        assertEquals("A", dkv.getKey(1));
        assertEquals("B", dkv.getKey(2));
        assertEquals(0, dkv.getIndex("C"));
    }

    // removeValue(int): out of bounds index throws IndexOutOfBoundsException
    @Test
    public void testRemoveValueInt_outOfBounds_throwsIndexOutOfBoundsException() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        try {
            dkv.removeValue(0);
            fail("expected IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException expected) {
        }
    }

    // removeValue(int): removing the last item must correctly decrement item count (bug regression)
    @Test
    public void testRemoveValueInt_removeLastItem_itemCountDecreases() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        dkv.addValue("B", 2.0);
        dkv.addValue("C", 3.0);
        dkv.removeValue(2);
        assertEquals(2, dkv.getItemCount());
    }

    // removeValue(int): removing the single remaining item results in empty collection
    @Test
    public void testRemoveValueInt_removeOnlyItem_resultsInEmptyCollection() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        dkv.removeValue(0);
        assertEquals(0, dkv.getItemCount());
    }

    // removeValue(int): removing a middle item rebuilds index mapping correctly
    @Test
    public void testRemoveValueInt_removeMiddleItem_rebuildsIndexCorrectly() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        dkv.addValue("B", 2.0);
        dkv.addValue("C", 3.0);
        dkv.removeValue(0);
        assertEquals(2, dkv.getItemCount());
        assertEquals(0, dkv.getIndex("B"));
        assertEquals(1, dkv.getIndex("C"));
    }

    // removeValue(Comparable): null key throws IllegalArgumentException (delegated from getIndex)
    @Test
    public void testRemoveValueComparable_nullKey_throwsIllegalArgumentException() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        try {
            dkv.removeValue((Comparable) null);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // removeValue(Comparable): unknown key does nothing (no exception, no change)
    @Test
    public void testRemoveValueComparable_unknownKey_doesNothing() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        dkv.removeValue("Z");
        assertEquals(1, dkv.getItemCount());
    }

    // removeValue(Comparable): known key removes the value
    @Test
    public void testRemoveValueComparable_knownKey_removesValue() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        dkv.addValue("B", 2.0);
        dkv.removeValue("A");
        assertEquals(1, dkv.getItemCount());
        assertEquals(-1, dkv.getIndex("A"));
    }

    // clear(): removes all values, item count becomes zero
    @Test
    public void testClear_removesAllValues() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        dkv.addValue("B", 2.0);
        dkv.clear();
        assertEquals(0, dkv.getItemCount());
    }

    // sortByKeys: ascending order sorts keys alphabetically
    @Test
    public void testSortByKeys_ascendingOrder_sortsCorrectly() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("banana", 1.0);
        dkv.addValue("apple", 2.0);
        dkv.addValue("cherry", 3.0);
        dkv.sortByKeys(SortOrder.ASCENDING);
        assertEquals("apple", dkv.getKey(0));
        assertEquals("banana", dkv.getKey(1));
        assertEquals("cherry", dkv.getKey(2));
    }

    // sortByKeys: descending order sorts keys in reverse alphabetical order
    @Test
    public void testSortByKeys_descendingOrder_sortsCorrectly() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("banana", 1.0);
        dkv.addValue("apple", 2.0);
        dkv.addValue("cherry", 3.0);
        dkv.sortByKeys(SortOrder.DESCENDING);
        assertEquals("cherry", dkv.getKey(0));
        assertEquals("banana", dkv.getKey(1));
        assertEquals("apple", dkv.getKey(2));
    }

    // sortByValues: ascending order sorts by value
    @Test
    public void testSortByValues_ascendingOrder_sortsCorrectly() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 3.0);
        dkv.addValue("B", 1.0);
        dkv.addValue("C", 2.0);
        dkv.sortByValues(SortOrder.ASCENDING);
        assertEquals("B", dkv.getKey(0));
        assertEquals("C", dkv.getKey(1));
        assertEquals("A", dkv.getKey(2));
    }

    // sortByValues: null values sort to the end regardless of order (ascending case)
    @Test
    public void testSortByValues_nullValuesSortToEnd_ascending() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", new Double(1.0));
        dkv.addValue("B", (Number) null);
        dkv.addValue("C", new Double(2.0));
        dkv.sortByValues(SortOrder.ASCENDING);
        assertEquals("B", dkv.getKey(2));
        assertNull(dkv.getValue(2));
    }

    // sortByValues: null values sort to the end regardless of order (descending case)
    @Test
    public void testSortByValues_nullValuesSortToEnd_descending() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", new Double(1.0));
        dkv.addValue("B", (Number) null);
        dkv.addValue("C", new Double(2.0));
        dkv.sortByValues(SortOrder.DESCENDING);
        assertEquals("B", dkv.getKey(2));
        assertNull(dkv.getValue(2));
    }

    // equals: same instance reference returns true
    @Test
    public void testEquals_sameInstance_returnsTrue() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        assertTrue(dkv.equals(dkv));
    }

    // equals: comparing to a non-KeyedValues object returns false
    @Test
    public void testEquals_notKeyedValuesInstance_returnsFalse() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        assertFalse(dkv.equals("not a keyed values"));
    }

    // equals: different item count returns false
    @Test
    public void testEquals_differentItemCount_returnsFalse() throws Throwable {
        DefaultKeyedValues dkv1 = new DefaultKeyedValues();
        dkv1.addValue("A", 1.0);
        DefaultKeyedValues dkv2 = new DefaultKeyedValues();
        dkv2.addValue("A", 1.0);
        dkv2.addValue("B", 2.0);
        assertFalse(dkv1.equals(dkv2));
    }

    // equals: different keys at same index returns false
    @Test
    public void testEquals_differentKeys_returnsFalse() throws Throwable {
        DefaultKeyedValues dkv1 = new DefaultKeyedValues();
        dkv1.addValue("A", 1.0);
        DefaultKeyedValues dkv2 = new DefaultKeyedValues();
        dkv2.addValue("Z", 1.0);
        assertFalse(dkv1.equals(dkv2));
    }

    // equals: different values at same key returns false
    @Test
    public void testEquals_differentValues_returnsFalse() throws Throwable {
        DefaultKeyedValues dkv1 = new DefaultKeyedValues();
        dkv1.addValue("A", 1.0);
        DefaultKeyedValues dkv2 = new DefaultKeyedValues();
        dkv2.addValue("A", 2.0);
        assertFalse(dkv1.equals(dkv2));
    }

    // equals: both instances with null values at the same key are equal
    @Test
    public void testEquals_bothNullValues_returnsTrue() throws Throwable {
        DefaultKeyedValues dkv1 = new DefaultKeyedValues();
        dkv1.addValue("A", (Number) null);
        DefaultKeyedValues dkv2 = new DefaultKeyedValues();
        dkv2.addValue("A", (Number) null);
        assertTrue(dkv1.equals(dkv2));
    }

    // equals: identical key/value content returns true
    @Test
    public void testEquals_equalContent_returnsTrue() throws Throwable {
        DefaultKeyedValues dkv1 = new DefaultKeyedValues();
        dkv1.addValue("A", 1.0);
        dkv1.addValue("B", 2.0);
        DefaultKeyedValues dkv2 = new DefaultKeyedValues();
        dkv2.addValue("A", 1.0);
        dkv2.addValue("B", 2.0);
        assertTrue(dkv1.equals(dkv2));
    }

    // hashCode: equal instances (by keys) produce equal hash codes
    @Test
    public void testHashCode_equalInstances_consistentHashCode() throws Throwable {
        DefaultKeyedValues dkv1 = new DefaultKeyedValues();
        dkv1.addValue("A", 1.0);
        dkv1.addValue("B", 2.0);
        DefaultKeyedValues dkv2 = new DefaultKeyedValues();
        dkv2.addValue("A", 1.0);
        dkv2.addValue("B", 2.0);
        assertEquals(dkv1.hashCode(), dkv2.hashCode());
    }

    // clone: produces an independent copy whose modifications don't affect the original
    @Test
    public void testClone_producesIndependentCopy() throws Throwable {
        DefaultKeyedValues dkv = new DefaultKeyedValues();
        dkv.addValue("A", 1.0);
        DefaultKeyedValues clone = (DefaultKeyedValues) dkv.clone();
        clone.addValue("B", 2.0);
        assertEquals(1, dkv.getItemCount());
        assertEquals(2, clone.getItemCount());
        assertTrue(dkv.equals(clone) == false);
    }
}
