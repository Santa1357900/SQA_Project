package org.apache.commons.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.junit.Test;
import static org.junit.Assert.*;

public class CollectionUtilsClaudeTest {

    // union: cardinality of each element is max(cardinality in a, cardinality in b)
    @Test
    public void testUnion_unionCardinalityIsMaxOfBoth() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1)); a.add(new Integer(2)); a.add(new Integer(2));
        List b = new ArrayList();
        b.add(new Integer(2)); b.add(new Integer(3));
        Collection result = CollectionUtils.union(a, b);
        assertEquals(4, result.size());
        assertEquals(2, CollectionUtils.cardinality(new Integer(2), result));
    }

    // intersection: cardinality of each element is min(cardinality in a, cardinality in b)
    @Test
    public void testIntersection_cardinalityIsMinOfBoth() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1)); a.add(new Integer(2)); a.add(new Integer(2));
        List b = new ArrayList();
        b.add(new Integer(2)); b.add(new Integer(3));
        Collection result = CollectionUtils.intersection(a, b);
        assertEquals(1, result.size());
        assertEquals(1, CollectionUtils.cardinality(new Integer(2), result));
    }

    // disjunction: cardinality is |max-min| per element (symmetric difference)
    @Test
    public void testDisjunction_cardinalityIsAbsoluteDifference() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1)); a.add(new Integer(2)); a.add(new Integer(2));
        List b = new ArrayList();
        b.add(new Integer(2)); b.add(new Integer(3));
        Collection result = CollectionUtils.disjunction(a, b);
        assertEquals(3, result.size());
        assertEquals(1, CollectionUtils.cardinality(new Integer(1), result));
        assertEquals(1, CollectionUtils.cardinality(new Integer(3), result));
    }

    // subtract: removes one occurrence of each element in b from a
    @Test
    public void testSubtract_removesOneOccurrencePerElementInB() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1)); a.add(new Integer(1)); a.add(new Integer(2));
        List b = new ArrayList();
        b.add(new Integer(1));
        Collection result = CollectionUtils.subtract(a, b);
        assertEquals(2, result.size());
        assertEquals(1, CollectionUtils.cardinality(new Integer(1), result));
    }

    // containsAny: true when the collections share at least one element
    @Test
    public void testContainsAny_trueWhenCommonElementExists() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1)); a.add(new Integer(2));
        List b = new ArrayList();
        b.add(new Integer(2)); b.add(new Integer(3));
        assertTrue(CollectionUtils.containsAny(a, b));
    }

    // containsAny: false when the collections are disjoint
    @Test
    public void testContainsAny_falseWhenNoCommonElement() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1));
        List b = new ArrayList();
        b.add(new Integer(2));
        assertFalse(CollectionUtils.containsAny(a, b));
    }

    // getCardinalityMap: maps each distinct element to its occurrence count
    @Test
    public void testGetCardinalityMap_countsEachElement() throws Throwable {
        List a = new ArrayList();
        a.add("x"); a.add("x"); a.add("y");
        Map map = CollectionUtils.getCardinalityMap(a);
        assertEquals(new Integer(2), map.get("x"));
        assertEquals(new Integer(1), map.get("y"));
    }

    // isSubCollection: true when every element cardinality in a <= in b
    @Test
    public void testIsSubCollection_trueWhenCardinalitiesLessOrEqual() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1)); a.add(new Integer(2));
        List b = new ArrayList();
        b.add(new Integer(1)); b.add(new Integer(2)); b.add(new Integer(2));
        assertTrue(CollectionUtils.isSubCollection(a, b));
    }

    // isSubCollection: false when an element cardinality in a exceeds b
    @Test
    public void testIsSubCollection_falseWhenCardinalityExceeds() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1)); a.add(new Integer(1));
        List b = new ArrayList();
        b.add(new Integer(1));
        assertFalse(CollectionUtils.isSubCollection(a, b));
    }

    // isProperSubCollection: true when a is a sub-collection and strictly smaller
    @Test
    public void testIsProperSubCollection_trueWhenStrictSubsetAndSmallerSize() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1));
        List b = new ArrayList();
        b.add(new Integer(1)); b.add(new Integer(2));
        assertTrue(CollectionUtils.isProperSubCollection(a, b));
    }

    // isProperSubCollection: false when sizes are equal even if sub-collection holds
    @Test
    public void testIsProperSubCollection_falseWhenSameSize() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1)); a.add(new Integer(2));
        List b = new ArrayList();
        b.add(new Integer(2)); b.add(new Integer(1));
        assertFalse(CollectionUtils.isProperSubCollection(a, b));
    }

    // isEqualCollection: true when both collections have the same cardinalities
    @Test
    public void testIsEqualCollection_trueWhenSameCardinalities() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1)); a.add(new Integer(1)); a.add(new Integer(2));
        List b = new ArrayList();
        b.add(new Integer(2)); b.add(new Integer(1)); b.add(new Integer(1));
        assertTrue(CollectionUtils.isEqualCollection(a, b));
    }

    // isEqualCollection: false when sizes differ
    @Test
    public void testIsEqualCollection_falseWhenDifferentSize() throws Throwable {
        List a = new ArrayList();
        a.add(new Integer(1)); a.add(new Integer(2));
        List b = new ArrayList();
        b.add(new Integer(1)); b.add(new Integer(2)); b.add(new Integer(3));
        assertFalse(CollectionUtils.isEqualCollection(a, b));
    }

    // cardinality: for a Set, result is 0 or 1 regardless of duplicates attempted
    @Test
    public void testCardinality_setCountsAtMostOne() throws Throwable {
        Set set = new HashSet();
        set.add("a"); set.add("b");
        assertEquals(1, CollectionUtils.cardinality("a", set));
        assertEquals(0, CollectionUtils.cardinality("c", set));
    }

    // cardinality: for a List, counts occurrences including null elements
    @Test
    public void testCardinality_listCountsOccurrencesIncludingNull() throws Throwable {
        List list = new ArrayList();
        list.add(null); list.add(null); list.add("a");
        assertEquals(2, CollectionUtils.cardinality(null, list));
        assertEquals(1, CollectionUtils.cardinality("a", list));
    }

    // find: returns first element matching the predicate
    @Test
    public void testFind_returnsFirstMatchingElement() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1)); list.add(new Integer(2)); list.add(new Integer(3));
        Predicate isTwo = new Predicate() {
            public boolean evaluate(Object object) {
                return new Integer(2).equals(object);
            }
        };
        assertEquals(new Integer(2), CollectionUtils.find(list, isTwo));
    }

    // find: returns null when collection or predicate is null
    @Test
    public void testFind_returnsNullWhenCollectionOrPredicateNull() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1));
        assertNull(CollectionUtils.find(null, null));
        assertNull(CollectionUtils.find(list, null));
    }

    // forAllDo: closure is executed once per element, side effect observable
    @Test
    public void testForAllDo_executesClosureOnEachElement() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1)); list.add(new Integer(2)); list.add(new Integer(3));
        final int[] sum = new int[1];
        Closure adder = new Closure() {
            public void execute(Object input) {
                sum[0] += ((Integer) input).intValue();
            }
        };
        CollectionUtils.forAllDo(list, adder);
        assertEquals(6, sum[0]);
    }

    // filter: removes elements for which predicate evaluates false, keeps matches
    @Test
    public void testFilter_removesElementsNotMatchingPredicate() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1)); list.add(new Integer(2)); list.add(new Integer(3)); list.add(new Integer(4));
        Predicate isEven = new Predicate() {
            public boolean evaluate(Object object) {
                return ((Integer) object).intValue() % 2 == 0;
            }
        };
        CollectionUtils.filter(list, isEven);
        assertEquals(2, list.size());
        assertTrue(list.contains(new Integer(2)));
        assertTrue(list.contains(new Integer(4)));
    }

    // transform: for a List, elements are replaced in place via listIterator.set
    @Test
    public void testTransform_listTransformedInPlace() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1)); list.add(new Integer(2)); list.add(new Integer(3));
        Transformer doubler = new Transformer() {
            public Object transform(Object input) {
                return new Integer(((Integer) input).intValue() * 2);
            }
        };
        CollectionUtils.transform(list, doubler);
        assertEquals(new Integer(2), list.get(0));
        assertEquals(new Integer(6), list.get(2));
    }

    // countMatches: counts elements satisfying the predicate
    @Test
    public void testCountMatches_countsMatchingElements() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1)); list.add(new Integer(2)); list.add(new Integer(3)); list.add(new Integer(4));
        Predicate isEven = new Predicate() {
            public boolean evaluate(Object object) {
                return ((Integer) object).intValue() % 2 == 0;
            }
        };
        assertEquals(2, CollectionUtils.countMatches(list, isEven));
    }

    // exists: true when at least one element matches the predicate
    @Test
    public void testExists_trueWhenMatchFound() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1)); list.add(new Integer(2));
        Predicate isTwo = new Predicate() {
            public boolean evaluate(Object object) {
                return new Integer(2).equals(object);
            }
        };
        assertTrue(CollectionUtils.exists(list, isTwo));
    }

    // exists: false when no element matches the predicate
    @Test
    public void testExists_falseWhenNoMatch() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1)); list.add(new Integer(2));
        Predicate isFive = new Predicate() {
            public boolean evaluate(Object object) {
                return new Integer(5).equals(object);
            }
        };
        assertFalse(CollectionUtils.exists(list, isFive));
    }

    // select: builds a new collection of matching elements, leaves source unchanged
    @Test
    public void testSelect_collectsMatchingElementsWithoutModifyingSource() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1)); list.add(new Integer(2)); list.add(new Integer(3)); list.add(new Integer(4));
        Predicate isEven = new Predicate() {
            public boolean evaluate(Object object) {
                return ((Integer) object).intValue() % 2 == 0;
            }
        };
        Collection selected = CollectionUtils.select(list, isEven);
        assertEquals(2, selected.size());
        assertEquals(4, list.size());
    }

    // selectRejected: builds a new collection of non-matching elements
    @Test
    public void testSelectRejected_collectsNonMatchingElements() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1)); list.add(new Integer(2)); list.add(new Integer(3)); list.add(new Integer(4));
        Predicate isEven = new Predicate() {
            public boolean evaluate(Object object) {
                return ((Integer) object).intValue() % 2 == 0;
            }
        };
        Collection rejected = CollectionUtils.selectRejected(list, isEven);
        assertEquals(2, rejected.size());
        assertTrue(rejected.contains(new Integer(1)));
    }

    // collect: builds a new collection of transformed elements, source unchanged
    @Test
    public void testCollect_transformsEachElementIntoNewCollection() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1)); list.add(new Integer(2)); list.add(new Integer(3));
        Transformer doubler = new Transformer() {
            public Object transform(Object input) {
                return new Integer(((Integer) input).intValue() * 2);
            }
        };
        Collection result = CollectionUtils.collect(list, doubler);
        assertEquals(3, result.size());
        assertTrue(result.contains(new Integer(6)));
        assertEquals(new Integer(1), list.get(0));
    }

    // addIgnoreNull: null is not added (returns false), non-null is added (returns true)
    @Test
    public void testAddIgnoreNull_nullNotAddedNonNullAdded() throws Throwable {
        List list = new ArrayList();
        assertFalse(CollectionUtils.addIgnoreNull(list, null));
        assertEquals(0, list.size());
        assertTrue(CollectionUtils.addIgnoreNull(list, "a"));
        assertEquals(1, list.size());
    }

    // addAll(Iterator): all elements from the iterator are appended to the collection
    @Test
    public void testAddAllIterator_addsAllElements() throws Throwable {
        List source = new ArrayList();
        source.add(new Integer(1)); source.add(new Integer(2)); source.add(new Integer(3));
        List target = new ArrayList();
        CollectionUtils.addAll(target, source.iterator());
        assertEquals(3, target.size());
        assertTrue(target.contains(new Integer(3)));
    }

    // addAll(Enumeration): all elements from the enumeration are appended to the collection
    @Test
    public void testAddAllEnumeration_addsAllElements() throws Throwable {
        Vector source = new Vector();
        source.add(new Integer(1)); source.add(new Integer(2));
        List target = new ArrayList();
        Enumeration enumeration = source.elements();
        CollectionUtils.addAll(target, enumeration);
        assertEquals(2, target.size());
    }

    // addAll(Object[]): all array elements are appended to the collection
    @Test
    public void testAddAllArray_addsAllElements() throws Throwable {
        Object[] arr = new Object[] { "a", "b", "c" };
        List target = new ArrayList();
        CollectionUtils.addAll(target, arr);
        assertEquals(3, target.size());
        assertEquals("b", target.get(1));
    }

    // index(Object,int) deprecated: for a List, returns the nth value
    @Test
    public void testIndex_listReturnsElementAtIndex() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(10)); list.add(new Integer(20)); list.add(new Integer(30));
        assertEquals(new Integer(20), CollectionUtils.index(list, 1));
    }

    // get: for a List, returns the element at the given index (same as List.get)
    @Test
    public void testGet_listReturnsElementAtIndex() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(10)); list.add(new Integer(20)); list.add(new Integer(30));
        assertEquals(new Integer(20), CollectionUtils.get(list, 1));
    }

    // get: negative index throws IndexOutOfBoundsException per javadoc
    @Test
    public void testGet_negativeIndexThrowsIndexOutOfBounds() throws Throwable {
        List list = new ArrayList();
        list.add(new Integer(1));
        try {
            CollectionUtils.get(list, -1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // get: unsupported object type (not Map/List/array/Iterator/Enumeration) throws IllegalArgumentException
    @Test
    public void testGet_unsupportedTypeThrowsIllegalArgument() throws Throwable {
        Object notACollection = new Integer(5);
        try {
            CollectionUtils.get(notACollection, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // size: for a Collection, returns its size
    @Test
    public void testSize_collectionReturnsSize() throws Throwable {
        List list = new ArrayList();
        list.add("a"); list.add("b"); list.add("c");
        assertEquals(3, CollectionUtils.size(list));
    }

    // size: for an array, returns its length
    @Test
    public void testSize_arrayReturnsLength() throws Throwable {
        Object[] arr = new Object[] { "a", "b" };
        assertEquals(2, CollectionUtils.size(arr));
    }

    // sizeIsEmpty: true for an empty collection, false for a non-empty collection
    @Test
    public void testSizeIsEmpty_trueAndFalseCases() throws Throwable {
        List empty = new ArrayList();
        List nonEmpty = new ArrayList();
        nonEmpty.add("a");
        assertTrue(CollectionUtils.sizeIsEmpty(empty));
        assertFalse(CollectionUtils.sizeIsEmpty(nonEmpty));
    }

    // isEmpty: null collection returns true per javadoc
    @Test
    public void testIsEmpty_nullReturnsTrue() throws Throwable {
        assertTrue(CollectionUtils.isEmpty(null));
    }

    // isNotEmpty: a non-empty, non-null collection returns true
    @Test
    public void testIsNotEmpty_nonEmptyReturnsTrue() throws Throwable {
        List list = new ArrayList();
        list.add("a");
        assertTrue(CollectionUtils.isNotEmpty(list));
        assertFalse(CollectionUtils.isNotEmpty(null));
    }

    // reverseArray: reverses element order for both even and odd length arrays
    @Test
    public void testReverseArray_reversesOddAndEvenLength() throws Throwable {
        Object[] odd = new Object[] { "1", "2", "3" };
        CollectionUtils.reverseArray(odd);
        assertEquals("3", odd[0]);
        assertEquals("2", odd[1]);
        assertEquals("1", odd[2]);
        Object[] even = new Object[] { "1", "2", "3", "4" };
        CollectionUtils.reverseArray(even);
        assertEquals("4", even[0]);
        assertEquals("1", even[3]);
    }

    // isFull: a plain (non-bounded) collection returns false per javadoc
    @Test
    public void testIsFull_nonBoundedCollectionReturnsFalse() throws Throwable {
        List list = new ArrayList();
        list.add("a");
        assertFalse(CollectionUtils.isFull(list));
    }

    // maxSize: a plain (non-bounded) collection returns -1 per javadoc
    @Test
    public void testMaxSize_nonBoundedCollectionReturnsMinusOne() throws Throwable {
        List list = new ArrayList();
        list.add("a");
        assertEquals(-1, CollectionUtils.maxSize(list));
    }

    // retainAll: result keeps only elements of collection that are present in retain
    @Test
    public void testRetainAll_keepsOnlyElementsInRetainCollection() throws Throwable {
        List collection = new ArrayList();
        collection.add(new Integer(1)); collection.add(new Integer(2)); collection.add(new Integer(3));
        List retain = new ArrayList();
        retain.add(new Integer(2)); retain.add(new Integer(3));
        Collection result = CollectionUtils.retainAll(collection, retain);
        assertEquals(2, result.size());
        assertFalse(result.contains(new Integer(1)));
        assertTrue(result.contains(new Integer(2)));
    }

    // removeAll: per javadoc, result must contain elements of collection NOT present in remove
    @Test
    public void testRemoveAll_removesElementsPresentInRemoveCollection() throws Throwable {
        List collection = new ArrayList();
        collection.add(new Integer(1)); collection.add(new Integer(2)); collection.add(new Integer(3));
        List remove = new ArrayList();
        remove.add(new Integer(2));
        Collection result = CollectionUtils.removeAll(collection, remove);
        assertEquals(2, result.size());
        assertTrue(result.contains(new Integer(1)));
        assertTrue(result.contains(new Integer(3)));
        assertFalse(result.contains(new Integer(2)));
    }

    // unmodifiableCollection: add() on the decorated collection throws UnsupportedOperationException
    @Test
    public void testUnmodifiableCollection_addThrowsUnsupportedOperation() throws Throwable {
        List backing = new ArrayList();
        backing.add("a");
        Collection unmodifiable = CollectionUtils.unmodifiableCollection(backing);
        try {
            unmodifiable.add("b");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }
}
