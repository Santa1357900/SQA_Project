package org.apache.commons.math.util;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.exception.DimensionMismatchException;
import org.apache.commons.math.exception.OutOfRangeException;
import org.apache.commons.math.exception.NotStrictlyPositiveException;

public class MultidimensionalCounterTest {

    @Test
    public void testValidCounterCreation() throws Throwable {
        int[] sizes = new int[] {2, 3, 4};
        MultidimensionalCounter counter = new MultidimensionalCounter(sizes);
        
        assertEquals(3, counter.getDimension());
        assertEquals(24, counter.getSize());
        
        int[] retrievedSizes = counter.getSizes();
        assertNotNull(retrievedSizes);
        assertEquals(3, retrievedSizes.length);
        assertEquals(2, retrievedSizes[0]);
        assertEquals(3, retrievedSizes[1]);
        assertEquals(4, retrievedSizes[2]);
    }

    @Test
    public void testInvalidCounterCreationZeroSize() throws Throwable {
        try {
            new MultidimensionalCounter(new int[] {2, 0, 4});
            fail("Should have thrown NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testInvalidCounterCreationNegativeSize() throws Throwable {
        try {
            new MultidimensionalCounter(new int[] {2, -1, 4});
            fail("Should have thrown NotStrictlyPositiveException");
        } catch (NotStrictlyPositiveException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testGetCountMultiDimensional() throws Throwable {
        MultidimensionalCounter counter = new MultidimensionalCounter(new int[] {2, 3});
        
        assertEquals(0, counter.getCount(new int[] {0, 0}));
        assertEquals(1, counter.getCount(new int[] {0, 1}));
        assertEquals(2, counter.getCount(new int[] {0, 2}));
        assertEquals(3, counter.getCount(new int[] {1, 0}));
        assertEquals(4, counter.getCount(new int[] {1, 1}));
        assertEquals(5, counter.getCount(new int[] {1, 2}));
    }

    @Test
    public void testGetCountDimensionMismatch() throws Throwable {
        MultidimensionalCounter counter = new MultidimensionalCounter(new int[] {2, 3});
        try {
            counter.getCount(new int[] {0});
            fail("Should have thrown DimensionMismatchException");
        } catch (DimensionMismatchException e) {
            assertEquals(1, e.getArgument());
            assertEquals(2, e.getDimension());
        }
    }

    @Test
    public void testGetCountOutOfRange() throws Throwable {
        MultidimensionalCounter counter = new MultidimensionalCounter(new int[] {2, 3});
        try {
            counter.getCount(new int[] {0, 3});
            fail("Should have thrown OutOfRangeException");
        } catch (OutOfRangeException e) {
            assertTrue(true);
        }

        try {
            counter.getCount(new int[] {-1, 0});
            fail("Should have thrown OutOfRangeException");
        } catch (OutOfRangeException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testGetCountsSingleDimensional() throws Throwable {
        MultidimensionalCounter counter = new MultidimensionalCounter(new int[] {2, 3});
        
        int[] c0 = counter.getCounts(0);
        assertNotNull(c0);
        assertEquals(2, c0.length);
        assertEquals(0, c0[0]);
        assertEquals(0, c0[1]);

        int[] c5 = counter.getCounts(5);
        assertNotNull(c5);
        assertEquals(2, c5.length);
        assertEquals(1, c5[0]);
        assertEquals(2, c5[1]);
    }

    @Test
    public void testGetCountsOutOfRange() throws Throwable {
        MultidimensionalCounter counter = new MultidimensionalCounter(new int[] {2, 3});
        
        try {
            counter.getCounts(-1);
            fail("Should have thrown OutOfRangeException");
        } catch (OutOfRangeException e) {
            assertTrue(true);
        }

        try {
            counter.getCounts(6);
            fail("Should have thrown OutOfRangeException");
        } catch (OutOfRangeException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testIterator() throws Throwable {
        MultidimensionalCounter counter = new MultidimensionalCounter(new int[] {2, 2});
        MultidimensionalCounter.Iterator iter = counter.iterator();
        
        assertNotNull(iter);
        
        int expectedCount = 0;
        while (iter.hasNext()) {
            Integer nextVal = iter.next();
            assertNotNull(nextVal);
            assertEquals(expectedCount, nextVal.intValue());
            assertEquals(expectedCount, iter.getCount());
            
            int[] counts = iter.getCounts();
            assertNotNull(counts);
            assertEquals(2, counts.length);
            
            expectedCount++;
        }
        
        assertEquals(4, expectedCount);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testIteratorRemove() throws Throwable {
        MultidimensionalCounter counter = new MultidimensionalCounter(new int[] {2, 2});
        MultidimensionalCounter.Iterator iter = counter.iterator();
        iter.remove();
    }

    @Test
    public void testIteratorGetCountDim() throws Throwable {
        MultidimensionalCounter counter = new MultidimensionalCounter(new int[] {2, 2});
        MultidimensionalCounter.Iterator iter = counter.iterator();
        
        assertTrue(iter.hasNext());
        iter.next(); // [0, 0]
        
        assertEquals(0, iter.getCount(0));
        assertEquals(0, iter.getCount(1));
        
        iter.next(); // [0, 1]
        assertEquals(0, iter.getCount(0));
        assertEquals(1, iter.getCount(1));
    }

    @Test
    public void testToString() throws Throwable {
        MultidimensionalCounter counter = new MultidimensionalCounter(new int[] {2, 3});
        String str = counter.toString();
        assertNotNull(str);
        assertTrue(str.length() > 0);
    }
}