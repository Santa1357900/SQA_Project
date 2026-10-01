package org.apache.commons.math.linear;

import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.math.exception.MathArithmeticException;

public class OpenMapRealVectorClaudeTest {

    // Default constructor: zero-length vector
    @Test
    public void testDefaultConstructor_zeroDimension() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector();
        assertEquals(0, v.getDimension());
    }

    // Dimension constructor: all entries default to zero, no entries stored
    @Test
    public void testDimensionConstructor_allZeroEntries() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(4);
        assertEquals(4, v.getDimension());
        assertEquals(0.0, v.getEntry(2), 1e-9);
    }

    // Dimension+epsilon constructor: setEntry below/above custom tolerance
    @Test
    public void testDimensionEpsilonConstructor_toleranceApplied() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(3, 0.5);
        v.setEntry(0, 0.3);
        assertEquals(0.0, v.getEntry(0), 1e-9);
        v.setEntry(1, 0.8);
        assertEquals(0.8, v.getEntry(1), 1e-9);
    }

    // Dimension+expectedSize constructor: dimension set correctly
    @Test
    public void testExpectedSizeConstructor_dimensionSet() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(5, 2);
        assertEquals(5, v.getDimension());
    }

    // Protected resize constructor (same package): dimension grows, old values kept
    @Test
    public void testResizeConstructor_increasesDimensionKeepsValues() throws Throwable {
        OpenMapRealVector base = new OpenMapRealVector(new double[] {1.0, 2.0});
        OpenMapRealVector resized = new OpenMapRealVector(base, 3);
        assertEquals(5, resized.getDimension());
        assertEquals(1.0, resized.getEntry(0), 1e-9);
        assertEquals(2.0, resized.getEntry(1), 1e-9);
    }

    // double[] constructor: zero values not stored, non-zero kept
    @Test
    public void testDoubleArrayConstructor_storesNonZeroOnly() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(new double[] {0.0, 5.0, 0.0});
        assertEquals(3, v.getDimension());
        assertEquals(0.0, v.getEntry(0), 1e-9);
        assertEquals(5.0, v.getEntry(1), 1e-9);
    }

    // double[]+epsilon constructor: value below epsilon treated as zero
    @Test
    public void testDoubleArrayConstructor_withEpsilon() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(new double[] {0.2, 5.0}, 0.5);
        assertEquals(0.0, v.getEntry(0), 1e-9);
        assertEquals(5.0, v.getEntry(1), 1e-9);
    }

    // Double[] constructor: zero boxed value not stored
    @Test
    public void testBoxedDoubleArrayConstructor_storesNonZeroOnly() throws Throwable {
        Double[] data = new Double[] {Double.valueOf(0.0), Double.valueOf(7.0)};
        OpenMapRealVector v = new OpenMapRealVector(data);
        assertEquals(2, v.getDimension());
        assertEquals(7.0, v.getEntry(1), 1e-9);
    }

    // Copy constructor: equal values but independent storage
    @Test
    public void testCopyConstructor_equalsOriginalAndIndependent() throws Throwable {
        OpenMapRealVector orig = new OpenMapRealVector(new double[] {1.0, 2.0});
        OpenMapRealVector copyV = new OpenMapRealVector(orig);
        assertTrue(orig.equals(copyV));
        copyV.setEntry(0, 9.0);
        assertEquals(1.0, orig.getEntry(0), 1e-9);
    }

    // Generic RealVector copy constructor: copies values element by element
    @Test
    public void testGenericRealVectorConstructor_copiesValues() throws Throwable {
        OpenMapRealVector source = new OpenMapRealVector(new double[] {3.0, 0.0, 4.0});
        OpenMapRealVector v = new OpenMapRealVector((RealVector) source);
        assertEquals(3, v.getDimension());
        assertEquals(3.0, v.getEntry(0), 1e-9);
        assertEquals(4.0, v.getEntry(2), 1e-9);
    }

    // isDefaultValue: true below epsilon, false above (protected, same package)
    @Test
    public void testIsDefaultValue_trueForZero_falseForNonZero() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector();
        assertTrue(v.isDefaultValue(0.0));
        assertFalse(v.isDefaultValue(1.0));
    }

    // add(OpenMapRealVector): overlapping key summed, disjoint keys carried over
    @Test
    public void testAdd_OpenMapRealVector_combinesOverlappingAndDisjointKeys() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 2.0, 0.0});
        OpenMapRealVector b = new OpenMapRealVector(new double[] {10.0, 0.0, 5.0});
        OpenMapRealVector sum = a.add(b);
        assertEquals(11.0, sum.getEntry(0), 1e-9);
        assertEquals(2.0, sum.getEntry(1), 1e-9);
        assertEquals(5.0, sum.getEntry(2), 1e-9);
    }

    // add(RealVector): dimension mismatch throws before instanceof check
    @Test
    public void testAdd_RealVector_dimensionMismatchThrows() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(2);
        RealVector b = new OpenMapRealVector(3);
        try {
            a.add(b);
            fail("expected exception for dimension mismatch");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // append(OpenMapRealVector): appended values placed at correct offset
    @Test
    public void testAppend_OpenMapRealVector_appendsAtCorrectOffset() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 2.0});
        OpenMapRealVector b = new OpenMapRealVector(new double[] {5.0});
        OpenMapRealVector res = a.append(b);
        assertEquals(3, res.getDimension());
        assertEquals(5.0, res.getEntry(2), 1e-9);
    }

    // append(double): single value appended at end, dimension increases by 1
    @Test
    public void testAppend_double_addsSingleElementAtEnd() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0});
        OpenMapRealVector res = a.append(9.0);
        assertEquals(2, res.getDimension());
        assertEquals(9.0, res.getEntry(1), 1e-9);
    }

    // append(double[]): all values appended at end in order
    @Test
    public void testAppend_doubleArray_addsAllElementsAtEnd() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0});
        OpenMapRealVector res = a.append(new double[] {2.0, 3.0});
        assertEquals(3, res.getDimension());
        assertEquals(2.0, res.getEntry(1), 1e-9);
        assertEquals(3.0, res.getEntry(2), 1e-9);
    }

    // copy(): equal content but independent of original
    @Test
    public void testCopy_returnsEqualButIndependentInstance() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 2.0});
        OpenMapRealVector c = a.copy();
        assertTrue(a.equals(c));
        c.setEntry(0, 100.0);
        assertEquals(1.0, a.getEntry(0), 1e-9);
    }

    // dotProduct(OpenMapRealVector): only overlapping non-zero keys contribute
    @Test
    public void testDotProduct_OpenMapRealVector_sumsProductsOfOverlappingKeys() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 2.0, 3.0});
        OpenMapRealVector b = new OpenMapRealVector(new double[] {4.0, 0.0, 5.0});
        double dot = a.dotProduct(b);
        assertEquals(19.0, dot, 1e-9);
    }

    // ebeDivide(RealVector): element-wise division
    @Test
    public void testEbeDivide_RealVector_dividesElementWise() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {10.0, 20.0});
        RealVector b = new OpenMapRealVector(new double[] {2.0, 4.0});
        OpenMapRealVector res = a.ebeDivide(b);
        assertEquals(5.0, res.getEntry(0), 1e-9);
        assertEquals(5.0, res.getEntry(1), 1e-9);
    }

    // ebeMultiply(double[]): element-wise multiplication
    @Test
    public void testEbeMultiply_doubleArray_multipliesElementWise() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {2.0, 3.0});
        OpenMapRealVector res = a.ebeMultiply(new double[] {4.0, 5.0});
        assertEquals(8.0, res.getEntry(0), 1e-9);
        assertEquals(15.0, res.getEntry(1), 1e-9);
    }

    // getSubVector: valid range returns correct slice
    @Test
    public void testGetSubVector_returnsCorrectSlice() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 2.0, 3.0, 4.0});
        OpenMapRealVector sub = a.getSubVector(1, 2);
        assertEquals(2, sub.getDimension());
        assertEquals(2.0, sub.getEntry(0), 1e-9);
        assertEquals(3.0, sub.getEntry(1), 1e-9);
    }

    // getSubVector: out-of-range end index throws
    @Test
    public void testGetSubVector_invalidIndexThrows() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(3);
        try {
            a.getSubVector(2, 5);
            fail("expected exception for out-of-range sub-vector");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // getData: dense array includes both zero and non-zero entries
    @Test
    public void testGetData_includesZeroAndNonZero() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {0.0, 7.0});
        double[] data = a.getData();
        assertEquals(2, data.length);
        assertEquals(0.0, data[0], 1e-9);
        assertEquals(7.0, data[1], 1e-9);
    }

    // getDistance(double[]): Euclidean distance computed correctly
    @Test
    public void testGetDistance_doubleArray_euclidean() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {3.0, 0.0});
        double dist = a.getDistance(new double[] {0.0, 4.0});
        assertEquals(5.0, dist, 1e-9);
    }

    // getEntry: out-of-bounds index throws
    @Test
    public void testGetEntry_outOfBoundsThrows() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(2);
        try {
            a.getEntry(5);
            fail("expected exception for out-of-range index");
        } catch (RuntimeException expected) {
            // expected
        }
    }

    // getL1Distance(OpenMapRealVector): sum of absolute differences, both orders of keys
    @Test
    public void testGetL1Distance_OpenMapRealVector_sumOfAbsoluteDifferences() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 0.0, 5.0});
        OpenMapRealVector b = new OpenMapRealVector(new double[] {4.0, 2.0, 0.0});
        double l1 = a.getL1Distance(b);
        assertEquals(10.0, l1, 1e-9);
    }



    // getLInfDistance(double[]): max absolute difference (reference, non-buggy path)
    @Test
    public void testGetLInfDistance_doubleArray_maxAbsoluteDifference() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 5.0});
        double dist = a.getLInfDistance(new double[] {4.0, 0.0});
        assertEquals(5.0, dist, 1e-9);
    }

    // isInfinite: true when an infinite value present and no NaN
    @Test
    public void testIsInfinite_trueWithInfiniteNoNaN() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {Double.POSITIVE_INFINITY, 2.0});
        assertTrue(a.isInfinite());
    }

    // isInfinite: false when NaN present, even together with an infinite value
    @Test
    public void testIsInfinite_falseWhenNaNPresentEvenWithInfinite() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {Double.POSITIVE_INFINITY, Double.NaN});
        assertFalse(a.isInfinite());
    }

    // isNaN: true/false branches depending on presence of NaN element
    @Test
    public void testIsNaN_trueAndFalseBranches() throws Throwable {
        OpenMapRealVector withNaN = new OpenMapRealVector(new double[] {1.0, Double.NaN});
        assertTrue(withNaN.isNaN());
        OpenMapRealVector noNaN = new OpenMapRealVector(new double[] {1.0, 2.0});
        assertFalse(noNaN.isNaN());
    }

    // mapAdd: returns new vector, original left unchanged
    @Test
    public void testMapAdd_doesNotModifyOriginal() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 2.0});
        OpenMapRealVector res = a.mapAdd(10.0);
        assertEquals(11.0, res.getEntry(0), 1e-9);
        assertEquals(1.0, a.getEntry(0), 1e-9);
    }

    // mapAddToSelf: mutates in place and returns this
    @Test
    public void testMapAddToSelf_modifiesInPlaceAndReturnsSelf() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 2.0});
        OpenMapRealVector res = a.mapAddToSelf(5.0);
        assertEquals(6.0, a.getEntry(0), 1e-9);
        assertSame(a, res);
    }

    // projection(RealVector): standard vector projection formula
    @Test
    public void testProjection_RealVector_computesCorrectProjection() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {3.0, 4.0});
        RealVector b = new OpenMapRealVector(new double[] {1.0, 0.0});
        RealVector proj = a.projection(b);
        assertEquals(3.0, proj.getEntry(0), 1e-9);
        assertEquals(0.0, proj.getEntry(1), 1e-9);
    }

    // setEntry: below epsilon removes stored entry, above epsilon stores it
    @Test
    public void testSetEntry_belowEpsilonDoesNotStore_aboveStores() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(3);
        a.setEntry(0, 5.0);
        a.setEntry(0, 0.0);
        assertEquals(0.0, a.getEntry(0), 1e-9);
    }

    // setSubVector(double[]): overwrites the requested range only
    @Test
    public void testSetSubVector_doubleArray_overwritesRange() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 2.0, 3.0, 4.0});
        a.setSubVector(1, new double[] {9.0, 8.0});
        assertEquals(1.0, a.getEntry(0), 1e-9);
        assertEquals(9.0, a.getEntry(1), 1e-9);
        assertEquals(8.0, a.getEntry(2), 1e-9);
    }

    // set(double): fills every entry with the same value
    @Test
    public void testSet_fillsEntireVectorWithValue() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(3);
        a.set(7.0);
        assertEquals(7.0, a.getEntry(0), 1e-9);
        assertEquals(7.0, a.getEntry(1), 1e-9);
        assertEquals(7.0, a.getEntry(2), 1e-9);
    }

    // subtract(OpenMapRealVector): handles key present only in the subtrahend
    @Test
    public void testSubtract_OpenMapRealVector_handlesDisjointKeys() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {5.0, 0.0});
        OpenMapRealVector b = new OpenMapRealVector(new double[] {2.0, 3.0});
        OpenMapRealVector res = a.subtract(b);
        assertEquals(3.0, res.getEntry(0), 1e-9);
        assertEquals(-3.0, res.getEntry(1), 1e-9);
    }

    // subtract(double[]): element-wise subtraction
    @Test
    public void testSubtract_doubleArray_elementWise() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {5.0, 0.0});
        OpenMapRealVector res = a.subtract(new double[] {2.0, 3.0});
        assertEquals(3.0, res.getEntry(0), 1e-9);
        assertEquals(-3.0, res.getEntry(1), 1e-9);
    }

    // unitVector: returns normalized copy, original left unchanged
    @Test
    public void testUnitVector_hasUnitNormAndOriginalUnchanged() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {3.0, 4.0});
        OpenMapRealVector unit = a.unitVector();
        assertEquals(0.6, unit.getEntry(0), 1e-9);
        assertEquals(0.8, unit.getEntry(1), 1e-9);
        assertEquals(3.0, a.getEntry(0), 1e-9);
    }

    // unitize: zero-norm vector throws MathArithmeticException
    @Test
    public void testUnitize_zeroVectorThrowsMathArithmeticException() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(3);
        try {
            a.unitize();
            fail("expected MathArithmeticException for zero norm vector");
        } catch (MathArithmeticException expected) {
            // expected
        }
    }

    // toArray: matches getData output exactly
    @Test
    public void testToArray_matchesGetData() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 0.0, 2.0});
        double[] arr = a.toArray();
        double[] data = a.getData();
        assertEquals(data.length, arr.length);
        assertEquals(data[0], arr[0], 1e-9);
        assertEquals(data[2], arr[2], 1e-9);
    }

    // equals: true for same values/dimension, false when dimension differs
    @Test
    public void testEquals_sameValuesTrue_differentDimensionFalse() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 2.0});
        OpenMapRealVector b = new OpenMapRealVector(new double[] {1.0, 2.0});
        assertTrue(a.equals(b));
        OpenMapRealVector c = new OpenMapRealVector(new double[] {1.0, 2.0, 3.0});
        assertFalse(a.equals(c));
    }

    // getSparsity: ratio of stored non-zero entries to dimension
    @Test
    public void testGetSparsity_ratioOfNonZeroToDimension() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 0.0, 2.0, 0.0});
        assertEquals(0.5, a.getSparsity(), 1e-9);
    }

    // sparseIterator: remove() is unsupported (fail-fast, read-only iterator)
    @Test
    public void testSparseIterator_removeThrowsUnsupportedOperationException() throws Throwable {
        OpenMapRealVector a = new OpenMapRealVector(new double[] {1.0, 2.0});
        java.util.Iterator it = a.sparseIterator();
        assertTrue(it.hasNext());
        it.next();
        try {
            it.remove();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}
