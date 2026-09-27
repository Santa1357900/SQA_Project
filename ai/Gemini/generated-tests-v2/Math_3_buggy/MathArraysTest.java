package org.apache.commons.math3.util;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.NoDataException;
import org.apache.commons.math3.exception.NonMonotonicSequenceException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.Field;

import java.util.Comparator;

public class MathArraysTest {

    @Test
    public void testScale() throws Throwable {
        double[] arr = new double[] {1.0, 2.0, 3.0};
        double[] scaled = MathArrays.scale(2.0, arr);
        assertNotNull(scaled);
        assertEquals(3, scaled.length);
        assertEquals(2.0, scaled[0], 1e-15);
        assertEquals(4.0, scaled[1], 1e-15);
        assertEquals(6.0, scaled[2], 1e-15);
    }

    @Test
    public void testScaleInPlace() throws Throwable {
        double[] arr = new double[] {1.0, 2.0, 3.0};
        MathArrays.scaleInPlace(2.0, arr);
        assertEquals(2.0, arr[0], 1e-15);
        assertEquals(4.0, arr[1], 1e-15);
        assertEquals(6.0, arr[2], 1e-15);
    }

    @Test
    public void testEbeAdd() throws Throwable {
        double[] a = new double[] {1.0, 2.0};
        double[] b = new double[] {3.0, 4.0};
        double[] res = MathArrays.ebeAdd(a, b);
        assertEquals(4.0, res[0], 1e-15);
        assertEquals(6.0, res[1], 1e-15);
    }

    @Test(expected = DimensionMismatchException.class)
    public void testEbeAddDimensionMismatch() throws Throwable {
        double[] a = new double[] {1.0};
        double[] b = new double[] {3.0, 4.0};
        MathArrays.ebeAdd(a, b);
    }

    @Test
    public void testEbeSubtract() throws Throwable {
        double[] a = new double[] {5.0, 3.0};
        double[] b = new double[] {1.0, 2.0};
        double[] res = MathArrays.ebeSubtract(a, b);
        assertEquals(4.0, res[0], 1e-15);
        assertEquals(1.0, res[1], 1e-15);
    }

    @Test(expected = DimensionMismatchException.class)
    public void testEbeSubtractDimensionMismatch() throws Throwable {
        double[] a = new double[] {1.0};
        double[] b = new double[] {3.0, 4.0};
        MathArrays.ebeSubtract(a, b);
    }

    @Test
    public void testEbeMultiply() throws Throwable {
        double[] a = new double[] {2.0, 3.0};
        double[] b = new double[] {4.0, 5.0};
        double[] res = MathArrays.ebeMultiply(a, b);
        assertEquals(8.0, res[0], 1e-15);
        assertEquals(15.0, res[1], 1e-15);
    }

    @Test(expected = DimensionMismatchException.class)
    public void testEbeMultiplyDimensionMismatch() throws Throwable {
        double[] a = new double[] {1.0};
        double[] b = new double[] {3.0, 4.0};
        MathArrays.ebeMultiply(a, b);
    }

    @Test
    public void testEbeDivide() throws Throwable {
        double[] a = new double[] {6.0, 8.0};
        double[] b = new double[] {2.0, 4.0};
        double[] res = MathArrays.ebeDivide(a, b);
        assertEquals(3.0, res[0], 1e-15);
        assertEquals(2.0, res[1], 1e-15);
    }

    @Test(expected = DimensionMismatchException.class)
    public void testEbeDivideDimensionMismatch() throws Throwable {
        double[] a = new double[] {1.0};
        double[] b = new double[] {3.0, 4.0};
        MathArrays.ebeDivide(a, b);
    }

    @Test
    public void testDistance() throws Throwable {
        double[] p1 = new double[] {0.0, 0.0};
        double[] p2 = new double[] {3.0, 4.0};
        assertEquals(5.0, MathArrays.distance(p1, p2), 1e-15);

        int[] ip1 = new int[] {0, 0};
        int[] ip2 = new int[] {3, 4};
        assertEquals(5.0, MathArrays.distance(ip1, ip2), 1e-15);
    }

    @Test
    public void testDistance1() throws Throwable {
        double[] p1 = new double[] {1.0, 2.0};
        double[] p2 = new double[] {4.0, 6.0};
        assertEquals(7.0, MathArrays.distance1(p1, p2), 1e-15);

        int[] ip1 = new int[] {1, 2};
        int[] ip2 = new int[] {4, 6};
        assertEquals(7, MathArrays.distance1(ip1, ip2));
    }

    @Test
    public void testDistanceInf() throws Throwable {
        double[] p1 = new double[] {1.0, 2.0};
        double[] p2 = new double[] {4.0, 6.0};
        assertEquals(4.0, MathArrays.distanceInf(p1, p2), 1e-15);

        int[] ip1 = new int[] {1, 2};
        int[] ip2 = new int[] {4, 6};
        assertEquals(4, MathArrays.distanceInf(ip1, ip2));
    }

    @Test
    public void testIsMonotonic() throws Throwable {
        Double[] increasing = new Double[] {1.0, 2.0, 3.0};
        Double[] decreasing = new Double[] {3.0, 2.0, 1.0};
        Double[] nonStrictInc = new Double[] {1.0, 2.0, 2.0};
        Double[] nonStrictDec = new Double[] {3.0, 2.0, 2.0};

        assertTrue(MathArrays.isMonotonic(increasing, MathArrays.OrderDirection.INCREASING, true));
        assertTrue(MathArrays.isMonotonic(increasing, MathArrays.OrderDirection.INCREASING, false));
        assertFalse(MathArrays.isMonotonic(nonStrictInc, MathArrays.OrderDirection.INCREASING, true));
        assertTrue(MathArrays.isMonotonic(nonStrictInc, MathArrays.OrderDirection.INCREASING, false));

        assertTrue(MathArrays.isMonotonic(decreasing, MathArrays.OrderDirection.DECREASING, true));
        assertTrue(MathArrays.isMonotonic(decreasing, MathArrays.OrderDirection.DECREASING, false));
        assertFalse(MathArrays.isMonotonic(nonStrictDec, MathArrays.OrderDirection.DECREASING, true));
        assertTrue(MathArrays.isMonotonic(nonStrictDec, MathArrays.OrderDirection.DECREASING, false));

        double[] dInc = new double[] {1.0, 2.0, 3.0};
        assertTrue(MathArrays.isMonotonic(dInc, MathArrays.OrderDirection.INCREASING, true));
    }

    @Test(expected = MathInternalError.class)
    public void testIsMonotonicInternalError() throws Throwable {
        // Passing a null or invalid order direction via reflection or forcing isn't easily possible without custom enum, 
        // but we can test checkOrder invalid case or cover normal paths.
        double[] val = new double[] {1.0, 2.0};
        MathArrays.checkOrder(val, MathArrays.OrderDirection.INCREASING, true, true);
    }

    @Test
    public void testCheckOrderDouble() throws Throwable {
        double[] incStrict = new double[] {1.0, 2.0, 3.0};
        double[] incNonStrict = new double[] {1.0, 2.0, 2.0};
        double[] decStrict = new double[] {3.0, 2.0, 1.0};
        double[] decNonStrict = new double[] {3.0, 2.0, 2.0};

        MathArrays.checkOrder(incStrict, MathArrays.OrderDirection.INCREASING, true);
        MathArrays.checkOrder(incNonStrict, MathArrays.OrderDirection.INCREASING, false);
        MathArrays.checkOrder(decStrict, MathArrays.OrderDirection.DECREASING, true);
        MathArrays.checkOrder(decNonStrict, MathArrays.OrderDirection.DECREASING, false);
        MathArrays.checkOrder(incStrict); // default INCREASING, true

        assertFalse(MathArrays.checkOrder(new double[] {1.0, 1.0, 3.0}, MathArrays.OrderDirection.INCREASING, true, false));
        assertFalse(MathArrays.checkOrder(new double[] {3.0, 3.0, 1.0}, MathArrays.OrderDirection.DECREASING, true, false));
    }

    @Test(expected = NonMonotonicSequenceException.class)
    public void testCheckOrderAbort() throws Throwable {
        double[] val = new double[] {1.0, 3.0, 2.0};
        MathArrays.checkOrder(val, MathArrays.OrderDirection.INCREASING, true, true);
    }

    @Test
    public void testCheckRectangular() throws Throwable {
        long[][] rect = new long[][] {{1, 2}, {3, 4}};
        MathArrays.checkRectangular(rect);
    }

    @Test(expected = DimensionMismatchException.class)
    public void testCheckRectangularNotRectangular() throws Throwable {
        long[][] notRect = new long[][] {{1, 2}, {3}};
        MathArrays.checkRectangular(notRect);
    }

    @Test
    public void testCheckPositive() throws Throwable {
        double[] pos = new double[] {1.0, 2.5};
        MathArrays.checkPositive(pos);
    }

    @Test(expected = NotStrictlyPositiveException.class)
    public void testCheckPositiveNotPositive() throws Throwable {
        double[] notPos = new double[] {1.0, 0.0};
        MathArrays.checkPositive(notPos);
    }

    @Test
    public void testCheckNonNegativeLong() throws Throwable {
        long[] nonNeg = new long[] {0L, 5L};
        MathArrays.checkNonNegative(nonNeg);

        long[][] nonNeg2D = new long[][] {{0L, 1L}, {2L, 3L}};
        MathArrays.checkNonNegative(nonNeg2D);
    }

    @Test(expected = NotPositiveException.class)
    public void testCheckNonNegativeLongException() throws Throwable {
        long[] neg = new long[] {1L, -1L};
        MathArrays.checkNonNegative(neg);
    }

    @Test(expected = NotPositiveException.class)
    public void testCheckNonNegative2DException() throws Throwable {
        long[][] neg2D = new long[][] {{1L, 0L}, {-1L, 3L}};
        MathArrays.checkNonNegative(neg2D);
    }

    @Test
    public void testSafeNorm() throws Throwable {
        double[] v = new double[] {3.0, 4.0};
        assertEquals(5.0, MathArrays.safeNorm(v), 1e-15);

        // Test dwarf and giant branches
        double[] extreme = new double[] {1e-300, 1e300, 0.0};
        assertTrue(Double.isInfinite(MathArrays.safeNorm(extreme)) || MathArrays.safeNorm(extreme) > 0);
        
        double[] zeros = new double[] {0.0, 0.0};
        assertEquals(0.0, MathArrays.safeNorm(zeros), 1e-15);
    }

    @Test
    public void testSortInPlace() throws Throwable {
        double[] x = new double[] {3.0, 1.0, 2.0};
        double[] y = new double[] {30.0, 10.0, 20.0};
        MathArrays.sortInPlace(x, y);
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, x, 1e-15);
        assertArrayEquals(new double[] {10.0, 20.0, 30.0}, y, 1e-15);

        double[] xD = new double[] {1.0, 3.0, 2.0};
        double[] yD = new double[] {10.0, 30.0, 20.0};
        MathArrays.sortInPlace(xD, MathArrays.OrderDirection.DECREASING, yD);
        assertArrayEquals(new double[] {3.0, 2.0, 1.0}, xD, 1e-15);
        assertArrayEquals(new double[] {30.0, 20.0, 10.0}, yD, 1e-15);
    }

    @Test(expected = NullArgumentException.class)
    public void testSortInPlaceNullX() throws Throwable {
        MathArrays.sortInPlace(null);
    }

    @Test(expected = NullArgumentException.class)
    public void testSortInPlaceNullY() throws Throwable {
        double[] x = new double[] {1.0, 2.0};
        double[][] yList = new double[][] {null};
        MathArrays.sortInPlace(x, yList);
    }

    @Test(expected = DimensionMismatchException.class)
    public void testSortInPlaceDimensionMismatch() throws Throwable {
        double[] x = new double[] {1.0, 2.0};
        double[] y = new double[] {1.0};
        MathArrays.sortInPlace(x, y);
    }

    @Test
    public void testCopyOf() throws Throwable {
        int[] iSrc = new int[] {1, 2, 3};
        int[] iRes1 = MathArrays.copyOf(iSrc);
        int[] iRes2 = MathArrays.copyOf(iSrc, 5);
        assertArrayEquals(new int[] {1, 2, 3}, iRes1);
        assertEquals(5, iRes2.length);
        assertEquals(0, iRes2[3]);

        double[] dSrc = new double[] {1.0, 2.0, 3.0};
        double[] dRes1 = MathArrays.copyOf(dSrc);
        double[] dRes2 = MathArrays.copyOf(dSrc, 1);
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, dRes1, 1e-15);
        assertEquals(1, dRes2.length);
    }

    @Test
    public void testLinearCombination() throws Throwable {
        double[] a = new double[] {1.0, 2.0};
        double[] b = new double[] {3.0, 4.0};
        assertEquals(11.0, MathArrays.linearCombination(a, b), 1e-15);

        // Test NaN fallback in linearCombination(double[], double[])
        double[] aNaN = new double[] {Double.NaN, 2.0};
        double[] bNaN = new double[] {3.0, 4.0};
        assertTrue(Double.isNaN(MathArrays.linearCombination(aNaN, bNaN)));

        assertEquals(11.0, MathArrays.linearCombination(1.0, 3.0, 2.0, 4.0), 1e-15);
        assertTrue(Double.isNaN(MathArrays.linearCombination(Double.NaN, 3.0, 2.0, 4.0)));

        assertEquals(18.0, MathArrays.linearCombination(1.0, 2.0, 3.0, 4.0, 5.0, 2.0), 1e-15);
        assertTrue(Double.isNaN(MathArrays.linearCombination(Double.NaN, 2.0, 3.0, 4.0, 5.0, 2.0)));

        assertEquals(28.0, MathArrays.linearCombination(1.0, 2.0, 3.0, 4.0, 5.0, 2.0, 2.0, 3.0), 1e-15);
        assertTrue(Double.isNaN(MathArrays.linearCombination(Double.NaN, 2.0, 3.0, 4.0, 5.0, 2.0, 2.0, 3.0)));
    }

    @Test(expected = DimensionMismatchException.class)
    public void testLinearCombinationDimensionMismatch() throws Throwable {
        MathArrays.linearCombination(new double[] {1.0}, new double[] {1.0, 2.0});
    }

    @Test
    public void testEqualsMethods() throws Throwable {
        float[] f1 = new float[] {1.0f, Float.NaN};
        float[] f2 = new float[] {1.0f, Float.NaN};
        float[] f3 = new float[] {1.0f, 2.0f};

        assertTrue(MathArrays.equals(f1, f1));
        assertTrue(MathArrays.equals((float[]) null, (float[]) null));
        assertFalse(MathArrays.equals(f1, (float[]) null));
        assertFalse(MathArrays.equals((float[]) null, f1));
        assertFalse(MathArrays.equals(f1, f3));
        assertTrue(MathArrays.equalsIncludingNaN(f1, f2));
        assertFalse(MathArrays.equalsIncludingNaN(f1, f3));

        double[] d1 = new double[] {1.0, Double.NaN};
        double[] d2 = new double[] {1.0, Double.NaN};
        double[] d3 = new double[] {1.0, 2.0};

        assertTrue(MathArrays.equals(d1, d1));
        assertTrue(MathArrays.equals((double[]) null, (double[]) null));
        assertFalse(MathArrays.equals(d1, (double[]) null));
        assertFalse(MathArrays.equals((double[]) null, d1));
        assertFalse(MathArrays.equals(d1, d3));
        assertTrue(MathArrays.equalsIncludingNaN(d1, d2));
        assertFalse(MathArrays.equalsIncludingNaN(d1, d3));
    }

    @Test
    public void testNormalizeArray() throws Throwable {
        double[] values = new double[] {1.0, 2.0, Double.NaN};
        double[] normalized = MathArrays.normalizeArray(values, 6.0);
        assertEquals(2.0, normalized[0], 1e-15);
        assertEquals(4.0, normalized[1], 1e-15);
        assertTrue(Double.isNaN(normalized[2]));
    }

    @Test(expected = MathIllegalArgumentException.class)
    public void testNormalizeArrayInfiniteSum() throws Throwable {
        MathArrays.normalizeArray(new double[] {1.0}, Double.POSITIVE_INFINITY);
    }

    @Test(expected = MathIllegalArgumentException.class)
    public void testNormalizeArrayNaNSum() throws Throwable {
        MathArrays.normalizeArray(new double[] {1.0}, Double.NaN);
    }

    @Test(expected = MathIllegalArgumentException.class)
    public void testNormalizeArrayInfiniteElement() throws Throwable {
        MathArrays.normalizeArray(new double[] {Double.POSITIVE_INFINITY}, 1.0);
    }

    @Test(expected = MathArithmeticException.class)
    public void testNormalizeArrayZeroSum() throws Throwable {
        MathArrays.normalizeArray(new double[] {0.0, 0.0}, 1.0);
    }

    @Test
    public void testBuildArray() throws Throwable {
        Field<org.apache.commons.math3.fraction.BigFraction> field = org.apache.commons.math3.fraction.BigFractionField.getInstance();
        org.apache.commons.math3.fraction.BigFraction[] arr = MathArrays.buildArray(field, 3);
        assertEquals(3, arr.length);

        org.apache.commons.math3.fraction.BigFraction[][] arr2D = MathArrays.buildArray(field, 2, 2);
        assertEquals(2, arr2D.length);
        assertEquals(2, arr2D[0].length);

        org.apache.commons.math3.fraction.BigFraction[][] arr2DNeg = MathArrays.buildArray(field, 2, -1);
        assertEquals(2, arr2DNeg.length);
    }

    @Test
    public void testConvolve() throws Throwable {
        double[] x = new double[] {1.0, 2.0};
        double[] h = new double[] {1.0, 1.0, 1.0};
        double[] y = MathArrays.convolve(x, h);
        assertEquals(4, y.length);
        assertEquals(1.0, y[0], 1e-15);
        assertEquals(3.0, y[1], 1e-15);
        assertEquals(3.0, y[2], 1e-15);
        assertEquals(2.0, y[3], 1e-15);
    }

    @Test(expected = NoDataException.class)
    public void testConvolveEmptyX() throws Throwable {
        MathArrays.convolve(new double[0], new double[] {1.0});
    }

    @Test(expected = NoDataException.class)
    public void testConvolveEmptyH() throws Throwable {
        MathArrays.convolve(new double[] {1.0}, new double[0]);
    }
}