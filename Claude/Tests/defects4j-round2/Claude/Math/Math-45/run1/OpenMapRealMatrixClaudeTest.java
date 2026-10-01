package org.apache.commons.math.linear;

import org.junit.Test;
import static org.junit.Assert.*;

public class OpenMapRealMatrixClaudeTest {

    private static final double DELTA = 1e-9;

    // constructor(int,int): row/column dimensions are stored and reported back
    @Test
    public void testConstructor_setsDimensions() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(3, 4);
        assertEquals(3, a.getRowDimension());
        assertEquals(4, a.getColumnDimension());
    }

    // constructor(int,int): sparse matrix starts with every entry equal to zero
    @Test
    public void testConstructor_defaultEntriesAreZero() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(3, 3);
        assertEquals(0.0, a.getEntry(0, 0), DELTA);
        assertEquals(0.0, a.getEntry(2, 2), DELTA);
    }

    // copy constructor: values present in the source matrix are copied
    @Test
    public void testCopyConstructor_copiesEntries() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(0, 1, 3.5);
        OpenMapRealMatrix b = new OpenMapRealMatrix(a);
        assertEquals(3.5, b.getEntry(0, 1), DELTA);
        assertEquals(2, b.getRowDimension());
        assertEquals(2, b.getColumnDimension());
    }

    // copy constructor: the copy must not share storage with the source
    @Test
    public void testCopyConstructor_isIndependentOfSource() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(0, 1, 3.5);
        OpenMapRealMatrix b = new OpenMapRealMatrix(a);
        a.setEntry(0, 1, 99.0);
        assertEquals(3.5, b.getEntry(0, 1), DELTA);
    }

    // copy(): returned matrix has same entries but can be mutated independently
    @Test
    public void testCopy_returnsIndependentEqualCopy() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(1, 1, 7.0);
        OpenMapRealMatrix c = a.copy();
        c.setEntry(1, 1, 0.0);
        assertEquals(7.0, a.getEntry(1, 1), DELTA);
        assertEquals(0.0, c.getEntry(1, 1), DELTA);
    }

    // createMatrix(int,int): returns a new zero-filled matrix with requested dimensions
    @Test
    public void testCreateMatrix_returnsZeroFilledMatrixWithGivenDimensions() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        OpenMapRealMatrix created = a.createMatrix(3, 5);
        assertEquals(3, created.getRowDimension());
        assertEquals(5, created.getColumnDimension());
        assertEquals(0.0, created.getEntry(0, 0), DELTA);
    }

    // getColumnDimension(): reports the value supplied at construction
    @Test
    public void testGetColumnDimension_returnsConstructedValue() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(5, 6);
        assertEquals(6, a.getColumnDimension());
    }

    // add(OpenMapRealMatrix): element-wise sum, including entries only present in one operand
    @Test
    public void testAddOpenMap_compatibleDimensions_sumsElementWise() throws Throwable {
        OpenMapRealMatrix m1 = new OpenMapRealMatrix(2, 2);
        m1.setEntry(0, 0, 1.0);
        m1.setEntry(1, 1, 2.0);
        OpenMapRealMatrix m2 = new OpenMapRealMatrix(2, 2);
        m2.setEntry(0, 0, 10.0);
        m2.setEntry(0, 1, 5.0);
        OpenMapRealMatrix sum = m1.add(m2);
        assertEquals(11.0, sum.getEntry(0, 0), DELTA);
        assertEquals(5.0, sum.getEntry(0, 1), DELTA);
        assertEquals(2.0, sum.getEntry(1, 1), DELTA);
    }

    // add(OpenMapRealMatrix): dimension mismatch must throw
    @Test
    public void testAddOpenMap_incompatibleDimensions_throwsException() throws Throwable {
        OpenMapRealMatrix m1 = new OpenMapRealMatrix(2, 2);
        OpenMapRealMatrix m3 = new OpenMapRealMatrix(3, 3);
        try {
            m1.add(m3);
            fail("expected exception for incompatible dimensions");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // subtract(RealMatrix): argument actually an OpenMapRealMatrix takes the direct-cast path
    @Test
    public void testSubtractRealMatrix_withOpenMapInstance_usesDirectPath() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(0, 0, 10.0);
        a.setEntry(1, 1, 4.0);
        OpenMapRealMatrix b = new OpenMapRealMatrix(2, 2);
        b.setEntry(0, 0, 3.0);
        b.setEntry(0, 1, 1.0);
        OpenMapRealMatrix diff = a.subtract((RealMatrix) b);
        assertEquals(7.0, diff.getEntry(0, 0), DELTA);
        assertEquals(-1.0, diff.getEntry(0, 1), DELTA);
        assertEquals(4.0, diff.getEntry(1, 1), DELTA);
    }

    // subtract(RealMatrix): non-OpenMapRealMatrix argument triggers the ClassCastException fallback path
    @Test
    public void testSubtractRealMatrix_withNonOpenMapInstance_usesFallbackPath() throws Throwable {
        BlockRealMatrix zero = new BlockRealMatrix(2, 2);
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(0, 0, 10.0);
        a.setEntry(1, 1, -3.0);
        OpenMapRealMatrix diff = a.subtract(zero);
        assertEquals(10.0, diff.getEntry(0, 0), DELTA);
        assertEquals(-3.0, diff.getEntry(1, 1), DELTA);
    }

    // subtract(OpenMapRealMatrix): element-wise difference, including entries only present in m
    @Test
    public void testSubtractOpenMap_compatibleDimensions_differenceElementWise() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(0, 0, 5.0);
        OpenMapRealMatrix b = new OpenMapRealMatrix(2, 2);
        b.setEntry(0, 1, 2.0);
        OpenMapRealMatrix diff = a.subtract(b);
        assertEquals(5.0, diff.getEntry(0, 0), DELTA);
        assertEquals(-2.0, diff.getEntry(0, 1), DELTA);
    }

    // subtract(OpenMapRealMatrix): dimension mismatch must throw
    @Test
    public void testSubtractOpenMap_incompatibleDimensions_throwsException() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        OpenMapRealMatrix b = new OpenMapRealMatrix(3, 2);
        try {
            a.subtract(b);
            fail("expected exception for incompatible dimensions");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // multiply(RealMatrix): argument actually an OpenMapRealMatrix takes the direct-cast path
    @Test
    public void testMultiplyRealMatrix_withOpenMapInstance_usesDirectPath() throws Throwable {
        OpenMapRealMatrix m1 = new OpenMapRealMatrix(2, 2);
        m1.setEntry(0, 0, 1.0); m1.setEntry(0, 1, 2.0);
        m1.setEntry(1, 0, 3.0); m1.setEntry(1, 1, 4.0);
        OpenMapRealMatrix m2 = new OpenMapRealMatrix(2, 2);
        m2.setEntry(0, 0, 5.0); m2.setEntry(0, 1, 6.0);
        m2.setEntry(1, 0, 7.0); m2.setEntry(1, 1, 8.0);
        RealMatrix product = m1.multiply((RealMatrix) m2);
        assertEquals(19.0, product.getEntry(0, 0), DELTA);
        assertEquals(22.0, product.getEntry(0, 1), DELTA);
        assertEquals(43.0, product.getEntry(1, 0), DELTA);
        assertEquals(50.0, product.getEntry(1, 1), DELTA);
    }

    // multiply(RealMatrix): non-OpenMapRealMatrix argument triggers the BlockRealMatrix fallback path
    @Test
    public void testMultiplyRealMatrix_withNonOpenMapInstance_usesBlockFallback() throws Throwable {
        OpenMapRealMatrix m1 = new OpenMapRealMatrix(2, 2);
        m1.setEntry(0, 0, 1.0); m1.setEntry(0, 1, 2.0);
        m1.setEntry(1, 0, 3.0); m1.setEntry(1, 1, 4.0);
        BlockRealMatrix zero = new BlockRealMatrix(2, 2);
        RealMatrix product = m1.multiply(zero);
        assertEquals(2, product.getRowDimension());
        assertEquals(2, product.getColumnDimension());
        assertEquals(0.0, product.getEntry(0, 0), DELTA);
        assertEquals(0.0, product.getEntry(1, 1), DELTA);
    }

    // multiply(OpenMapRealMatrix): classic 2x2 matrix product
    @Test
    public void testMultiplyOpenMap_compatibleDimensions_classicProduct() throws Throwable {
        OpenMapRealMatrix m1 = new OpenMapRealMatrix(2, 2);
        m1.setEntry(0, 0, 1.0); m1.setEntry(0, 1, 2.0);
        m1.setEntry(1, 0, 3.0); m1.setEntry(1, 1, 4.0);
        OpenMapRealMatrix m2 = new OpenMapRealMatrix(2, 2);
        m2.setEntry(0, 0, 5.0); m2.setEntry(0, 1, 6.0);
        m2.setEntry(1, 0, 7.0); m2.setEntry(1, 1, 8.0);
        OpenMapRealMatrix product = m1.multiply(m2);
        assertEquals(19.0, product.getEntry(0, 0), DELTA);
        assertEquals(50.0, product.getEntry(1, 1), DELTA);
    }

    // multiply(OpenMapRealMatrix): this.columns != m.rows must throw
    @Test
    public void testMultiplyOpenMap_incompatibleDimensions_throwsException() throws Throwable {
        OpenMapRealMatrix m1 = new OpenMapRealMatrix(2, 2);
        OpenMapRealMatrix m3 = new OpenMapRealMatrix(3, 3);
        try {
            m1.multiply(m3);
            fail("expected exception for incompatible dimensions");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // multiply(OpenMapRealMatrix): two contributions to the same cell cancel to exactly zero
    @Test
    public void testMultiplyOpenMap_cancellingTerms_resultIsZero() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(0, 0, 1.0);
        a.setEntry(0, 1, 1.0);
        OpenMapRealMatrix b = new OpenMapRealMatrix(2, 2);
        b.setEntry(0, 0, 5.0);
        b.setEntry(1, 0, -5.0);
        OpenMapRealMatrix product = a.multiply(b);
        assertEquals(0.0, product.getEntry(0, 0), DELTA);
    }

    // multiply(OpenMapRealMatrix): entirely empty right-hand matrix yields an all-zero result
    @Test
    public void testMultiplyOpenMap_rightMatrixAllZero_resultIsZero() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(0, 0, 7.0);
        OpenMapRealMatrix zero = new OpenMapRealMatrix(2, 2);
        OpenMapRealMatrix product = a.multiply(zero);
        assertEquals(0.0, product.getEntry(0, 0), DELTA);
        assertEquals(0.0, product.getEntry(1, 1), DELTA);
    }

    // getEntry(int,int): unset position returns the sparse default of zero
    @Test
    public void testGetEntry_unsetPosition_returnsZero() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(3, 3);
        a.setEntry(0, 0, 5.0);
        assertEquals(0.0, a.getEntry(1, 2), DELTA);
    }

    // getEntry(int,int): negative row index must throw
    @Test
    public void testGetEntry_rowNegative_throwsException() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        try {
            a.getEntry(-1, 0);
            fail("expected exception for negative row");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // getEntry(int,int): row index equal to row count (just past the last valid row) must throw
    @Test
    public void testGetEntry_rowTooLarge_throwsException() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        try {
            a.getEntry(2, 0);
            fail("expected exception for out-of-range row");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // getEntry(int,int): negative column index must throw
    @Test
    public void testGetEntry_columnNegative_throwsException() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        try {
            a.getEntry(0, -1);
            fail("expected exception for negative column");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // getEntry(int,int): column index equal to column count must throw
    @Test
    public void testGetEntry_columnTooLarge_throwsException() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        try {
            a.getEntry(0, 2);
            fail("expected exception for out-of-range column");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // getRowDimension(): reports the value supplied at construction
    @Test
    public void testGetRowDimension_returnsConstructedValue() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(4, 6);
        assertEquals(4, a.getRowDimension());
    }

    // setEntry(int,int,double): non-zero value is stored and retrievable
    @Test
    public void testSetEntry_nonZeroValue_isStoredAndRetrievable() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(1, 0, 8.5);
        assertEquals(8.5, a.getEntry(1, 0), DELTA);
    }

    // setEntry(int,int,double): setting to zero clears a previously stored entry
    @Test
    public void testSetEntry_zeroValue_clearsStoredEntry() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(0, 0, 3.0);
        a.setEntry(0, 0, 0.0);
        assertEquals(0.0, a.getEntry(0, 0), DELTA);
    }

    // setEntry(int,int,double): out-of-range index must throw
    @Test
    public void testSetEntry_outOfRange_throwsException() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        try {
            a.setEntry(5, 0, 1.0);
            fail("expected exception for out-of-range row");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // addToEntry(int,int,double): adding to an empty cell sets it to the increment value
    @Test
    public void testAddToEntry_onEmptyEntry_setsIncrementValue() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.addToEntry(0, 0, 5.0);
        assertEquals(5.0, a.getEntry(0, 0), DELTA);
    }

    // addToEntry(int,int,double): result of exactly zero clears the stored entry
    @Test
    public void testAddToEntry_resultingInZero_clearsEntry() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(0, 0, 5.0);
        a.addToEntry(0, 0, -5.0);
        assertEquals(0.0, a.getEntry(0, 0), DELTA);
    }

    // addToEntry(int,int,double): out-of-range index must throw
    @Test
    public void testAddToEntry_outOfRange_throwsException() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        try {
            a.addToEntry(0, 5, 1.0);
            fail("expected exception for out-of-range column");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // multiplyEntry(int,int,double): scales an existing stored value
    @Test
    public void testMultiplyEntry_scalesExistingValue() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(0, 0, 4.0);
        a.multiplyEntry(0, 0, 3.0);
        assertEquals(12.0, a.getEntry(0, 0), DELTA);
    }

    // multiplyEntry(int,int,double): multiplying by zero clears the stored entry
    @Test
    public void testMultiplyEntry_resultingInZero_clearsEntry() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        a.setEntry(0, 0, 4.0);
        a.multiplyEntry(0, 0, 0.0);
        assertEquals(0.0, a.getEntry(0, 0), DELTA);
    }

    // multiplyEntry(int,int,double): out-of-range index must throw
    @Test
    public void testMultiplyEntry_outOfRange_throwsException() throws Throwable {
        OpenMapRealMatrix a = new OpenMapRealMatrix(2, 2);
        try {
            a.multiplyEntry(0, 5, 2.0);
            fail("expected exception for out-of-range column");
        } catch (RuntimeException expected) {
            // expected
        }
    }
}
