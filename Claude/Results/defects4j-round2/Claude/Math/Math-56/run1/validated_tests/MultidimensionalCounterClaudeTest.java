package org.apache.commons.math.util;

import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.math.exception.DimensionMismatchException;
import org.apache.commons.math.exception.OutOfRangeException;
import org.apache.commons.math.exception.NotStrictlyPositiveException;

public class MultidimensionalCounterClaudeTest {

    // Constructor: single dimension produces dimension=1 and size equal to given value
    @Test
    public void testConstructor_singleDimension_setsCorrectSize() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(5);
        assertEquals(1, c.getDimension());
        assertEquals(5, c.getSize());
    }

    // Constructor: multi dimension totalSize is product of all sizes
    @Test
    public void testConstructor_multiDimension_setsCorrectTotalSize() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 4, 3);
        assertEquals(3, c.getDimension());
        assertEquals(24, c.getSize());
    }

    // Constructor: zero size must throw NotStrictlyPositiveException per javadoc
    @Test
    public void testConstructor_zeroSize_throwsNotStrictlyPositiveException() throws Throwable {
        try {
            new MultidimensionalCounter(0);
            fail("expected NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException expected) {
        }
    }

    // Constructor: negative size must throw NotStrictlyPositiveException per javadoc
    @Test
    public void testConstructor_negativeSize_throwsNotStrictlyPositiveException() throws Throwable {
        try {
            new MultidimensionalCounter(-1);
            fail("expected NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException expected) {
        }
    }

    // getDimension returns number of dimensions given to constructor
    @Test
    public void testGetDimension_returnsNumberOfDimensions() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(3, 2);
        assertEquals(2, c.getDimension());
    }

    // getSize returns total number of unidimensional slots
    @Test
    public void testGetSize_returnsTotalSizeProduct() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 5, 3);
        assertEquals(30, c.getSize());
    }

    // getSizes returns array matching the sizes given to constructor
    @Test
    public void testGetSizes_returnsCopyMatchingInput() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 5, 3);
        int[] sizes = c.getSizes();
        assertArrayEquals(new int[] {2, 5, 3}, sizes);
    }

    // getSizes must return a defensive copy, not the internal array
    @Test
    public void testGetSizes_modifyingReturnedArray_doesNotAffectInternalState() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 5);
        int[] sizes = c.getSizes();
        sizes[0] = 99;
        int[] sizes2 = c.getSizes();
        assertEquals(2, sizes2[0]);
    }

    // getCounts: index 0 maps to the all-zero tuple
    @Test
    public void testGetCounts_indexZero_returnsAllZeros() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 4, 3);
        assertArrayEquals(new int[] {0, 0, 0}, c.getCounts(0));
    }

    // getCounts: small gap in non-last dimensions is handled correctly (no bug here)
    @Test
    public void testGetCounts_smallIndex_correctMapping() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 4, 3);
        assertArrayEquals(new int[] {0, 1, 0}, c.getCounts(3));
    }

    // BUG: for a single-dimension counter, getCounts(index) must be the identity mapping
    @Test
    public void testGetCounts_singleDimension_identityMapping_catchesBug() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(5);
        assertEquals(3, c.getCounts(3)[0]);
        assertEquals(4, c.getCounts(4)[0]);
    }

    // BUG: last-dimension reconstruction uses a growing step instead of a unit step
    @Test
    public void testGetCounts_multiDimension_lastDimensionLargeGap_catchesBug() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 4);
        assertArrayEquals(new int[] {0, 3}, c.getCounts(3));
    }

    // getCounts: negative index must throw OutOfRangeException
    @Test
    public void testGetCounts_negativeIndex_throwsOutOfRangeException() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        try {
            c.getCounts(-1);
            fail("expected OutOfRangeException");
        } catch (OutOfRangeException expected) {
        }
    }

    // getCounts: index equal to totalSize (excluded bound) must throw OutOfRangeException
    @Test
    public void testGetCounts_indexEqualsTotalSize_throwsOutOfRangeException() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        try {
            c.getCounts(6);
            fail("expected OutOfRangeException");
        } catch (OutOfRangeException expected) {
        }
    }

    // getCount: all-zero tuple maps to unidimensional index 0
    @Test
    public void testGetCount_zeroIndices_returnsZero() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 4, 3);
        assertEquals(0, c.getCount(0, 0, 0));
    }

    // getCount: valid tuple maps to the expected unidimensional index (per class javadoc example)
    @Test
    public void testGetCount_validIndices_returnsExpectedUnidimensionalIndex() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 4, 3);
        assertEquals(23, c.getCount(1, 3, 2));
    }

    // getCount: wrong number of components must throw DimensionMismatchException
    @Test
    public void testGetCount_wrongArrayLength_throwsDimensionMismatchException() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 4, 3);
        try {
            c.getCount(1, 2);
            fail("expected DimensionMismatchException");
        } catch (DimensionMismatchException expected) {
        }
    }

    // getCount: negative component must throw OutOfRangeException
    @Test
    public void testGetCount_negativeComponent_throwsOutOfRangeException() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 4, 3);
        try {
            c.getCount(-1, 0, 0);
            fail("expected OutOfRangeException");
        } catch (OutOfRangeException expected) {
        }
    }

    // getCount: component too large for its dimension must throw OutOfRangeException
    @Test
    public void testGetCount_componentTooLarge_throwsOutOfRangeException() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 4, 3);
        try {
            c.getCount(2, 0, 0);
            fail("expected OutOfRangeException");
        } catch (OutOfRangeException expected) {
        }
    }

    // BUG (corroborating): getCount(getCounts(i)) must be the identity for every valid index
    @Test
    public void testGetCount_roundTrip_withGetCounts_consistentForAllIndices() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 4);
        for (int i = 0; i < c.getSize(); i++) {
            int[] counts = c.getCounts(i);
            assertEquals(i, c.getCount(counts));
        }
    }

    // iterator() must return a non-null Iterator instance
    @Test
    public void testIterator_returnsNonNullIterator() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        MultidimensionalCounter.Iterator it = c.iterator();
        assertNotNull(it);
    }

    // Iterator.hasNext: true before any call, for a counter with more than one slot
    @Test
    public void testIteratorHasNext_initialState_trueForMultiElementCounter() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        MultidimensionalCounter.Iterator it = c.iterator();
        assertTrue(it.hasNext());
    }

    // Iterator.hasNext: false once every slot has been visited
    @Test
    public void testIteratorHasNext_afterFullIteration_false() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        MultidimensionalCounter.Iterator it = c.iterator();
        while (it.hasNext()) {
            it.next();
        }
        assertFalse(it.hasNext());
    }

    // Iterator.next: first call returns unidimensional count 0
    @Test
    public void testIteratorNext_firstCall_returnsZero() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        MultidimensionalCounter.Iterator it = c.iterator();
        int first = it.next();
        assertEquals(0, first);
    }

    // Iterator.next: number of successful calls equals total size of the counter
    @Test
    public void testIteratorNext_fullIteration_countsMatchTotalSize() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        MultidimensionalCounter.Iterator it = c.iterator();
        int visited = 0;
        while (it.hasNext()) {
            it.next();
            visited++;
        }
        assertEquals(c.getSize(), visited);
    }

    // Iterator.getCount: after advancing, returns the current unidimensional slot
    @Test
    public void testIteratorGetCount_afterNext_returnsCurrentPosition() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        MultidimensionalCounter.Iterator it = c.iterator();
        it.next();
        it.next();
        assertEquals(1, it.getCount());
    }

    // Iterator.getCounts: returns a defensive copy of the multidimensional state
    @Test
    public void testIteratorGetCounts_afterNext_returnsDefensiveCopy() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        MultidimensionalCounter.Iterator it = c.iterator();
        it.next();
        int[] counts = it.getCounts();
        counts[0] = 99;
        assertEquals(0, it.getCounts()[0]);
    }

    // Iterator.getCount(dim): returns the slot value for the requested dimension
    @Test
    public void testIteratorGetCountDim_validDimension_returnsCorrectValue() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        MultidimensionalCounter.Iterator it = c.iterator();
        it.next();
        it.next();
        it.next();
        it.next();
        assertEquals(1, it.getCount(0));
        assertEquals(0, it.getCount(1));
    }

    // Iterator.getCount(dim): out-of-range dimension index must throw IndexOutOfBoundsException
    @Test
    public void testIteratorGetCountDim_invalidDimension_throwsIndexOutOfBoundsException() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        MultidimensionalCounter.Iterator it = c.iterator();
        try {
            it.getCount(5);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // Iterator.remove: must throw UnsupportedOperationException per javadoc
    @Test
    public void testIteratorRemove_throwsUnsupportedOperationException() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(2, 3);
        MultidimensionalCounter.Iterator it = c.iterator();
        try {
            it.remove();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // Iterator on a single-slot counter: completes after exactly one next() call
    @Test
    public void testIteratorSingleElementCounter_iterationCompletesAfterOneNext() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(1);
        MultidimensionalCounter.Iterator it = c.iterator();
        assertTrue(it.hasNext());
        int val = it.next();
        assertEquals(0, val);
        assertFalse(it.hasNext());
    }

    // toString: single dimension counter produces a bracketed representation without error
    @Test
    public void testToString_singleDimension_returnsExpectedFormat() throws Throwable {
        MultidimensionalCounter c = new MultidimensionalCounter(5);
        String s = c.toString();
        assertEquals("[0]", s);
    }
}
