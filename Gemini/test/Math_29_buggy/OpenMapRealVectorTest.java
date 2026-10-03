package org.apache.commons.math3.linear;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.NotPositiveException;

public class OpenMapRealVectorTest {

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector();
        assertEquals(0, v1.getDimension());
        assertEquals(0.0, v1.getSparsity(), 1.0e-12);

        OpenMapRealVector v2 = new OpenMapRealVector(5);
        assertEquals(5, v2.getDimension());
        assertEquals(OpenMapRealVector.DEFAULT_ZERO_TOLERANCE, 1.0e-12, 1.0e-15);

        OpenMapRealVector v3 = new OpenMapRealVector(5, 1.0e-6);
        assertEquals(5, v3.getDimension());

        OpenMapRealVector v4 = new OpenMapRealVector(5, 2);
        assertEquals(5, v4.getDimension());

        OpenMapRealVector v5 = new OpenMapRealVector(5, 2, 1.0e-6);
        assertEquals(5, v5.getDimension());

        double[] arr = {0.0, 1.5, 0.0, -2.5};
        OpenMapRealVector v6 = new OpenMapRealVector(arr);
        assertEquals(4, v6.getDimension());
        assertEquals(1.5, v6.getEntry(1), 1.0e-12);

        OpenMapRealVector v7 = new OpenMapRealVector(arr, 1.0e-6);
        assertEquals(4, v7.getDimension());

        Double[] objArr = {0.0, 3.5, 0.0};
        OpenMapRealVector v8 = new OpenMapRealVector(objArr);
        assertEquals(3, v8.getDimension());
        assertEquals(3.5, v8.getEntry(1), 1.0e-12);

        OpenMapRealVector v9 = new OpenMapRealVector(objArr, 1.0e-6);
        assertEquals(3, v9.getDimension());

        OpenMapRealVector v10 = new OpenMapRealVector(v6);
        assertEquals(v6, v10);

        RealVector standardVec = new ArrayRealVector(new double[]{0.0, 1.0, 2.0});
        OpenMapRealVector v11 = new OpenMapRealVector(standardVec);
        assertEquals(3, v11.getDimension());
        assertEquals(1.0, v11.getEntry(1), 1.0e-12);
    }

    @Test
    public void testSetAndGetEntry() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(10, 1.0e-5);
        v.setEntry(2, 0.5);
        assertEquals(0.5, v.getEntry(2), 1.0e-12);

        // Test default value handling (removes entry if within epsilon)
        v.setEntry(2, 1.0e-8);
        assertEquals(0.0, v.getEntry(2), 1.0e-12);

        v.setEntry(2, 0.5);
        v.setEntry(2, 0.0);
        assertEquals(0.0, v.getEntry(2), 1.0e-12);

        v.set(1.5);
        assertEquals(1.5, v.getEntry(0), 1.0e-12);
        assertEquals(1.5, v.getEntry(9), 1.0e-12);
    }

    @Test
    public void testAdd() throws Throwable {
        double[] d1 = {0.0, 1.0, 0.0, 3.0};
        double[] d2 = {1.0, 0.0, 0.0, 2.0};
        OpenMapRealVector v1 = new OpenMapRealVector(d1);
        OpenMapRealVector v2 = new OpenMapRealVector(d2);

        OpenMapRealVector sum1 = v1.add(v2);
        assertEquals(1.0, sum1.getEntry(0), 1.0e-12);
        assertEquals(1.0, sum1.getEntry(1), 1.0e-12);
        assertEquals(5.0, sum1.getEntry(3), 1.0e-12);

        // Test adding where v1 has more entries than v2 (copyThis = true)
        OpenMapRealVector v3 = new OpenMapRealVector(new double[]{1.0, 2.0, 3.0, 4.0});
        OpenMapRealVector v4 = new OpenMapRealVector(new double[]{0.0, 1.0, 0.0, 0.0});
        OpenMapRealVector sum2 = v3.add(v4);
        assertEquals(1.0, sum2.getEntry(0), 1.0e-12);
        assertEquals(3.0, sum2.getEntry(1), 1.0e-12);

        // Test add with standard RealVector
        RealVector std = new ArrayRealVector(new double[]{1.0, 1.0, 1.0, 1.0});
        RealVector sumStd = v1.add(std);
        assertEquals(1.0, sumStd.getEntry(0), 1.0e-12);
        assertEquals(2.0, sumStd.getEntry(1), 1.0e-12);

        boolean dimensionMismatchThrown = false;
        try {
            v1.add(new OpenMapRealVector(2));
        } catch (DimensionMismatchException e) {
            dimensionMismatchThrown = true;
        }
        assertTrue(dimensionMismatchThrown);
    }

    @Test
    public void testAppend() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[]{1.0, 0.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[]{0.0, 2.0});

        OpenMapRealVector app1 = v1.append(v2);
        assertEquals(4, app1.getDimension());
        assertEquals(1.0, app1.getEntry(0), 1.0e-12);
        assertEquals(2.0, app1.getEntry(3), 1.0e-12);

        RealVector std = new ArrayRealVector(new double[]{3.0, 4.0});
        OpenMapRealVector app2 = v1.append(std);
        assertEquals(4, app2.getDimension());
        assertEquals(3.0, app2.getEntry(2), 1.0e-12);

        OpenMapRealVector app3 = v1.append(5.0);
        assertEquals(3, app3.getDimension());
        assertEquals(5.0, app3.getEntry(2), 1.0e-12);
    }

    @Test
    public void testDotProduct() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[]{1.0, 0.0, 3.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[]{0.0, 2.0, 4.0});

        assertEquals(12.0, v1.dotProduct(v2), 1.0e-12);

        // Test thisIsSmaller branch
        OpenMapRealVector v3 = new OpenMapRealVector(new double[]{1.0, 0.0, 0.0});
        OpenMapRealVector v4 = new OpenMapRealVector(new double[]{2.0, 3.0, 4.0});
        assertEquals(2.0, v3.dotProduct(v4), 1.0e-12);

        RealVector std = new ArrayRealVector(new double[]{0.0, 2.0, 4.0});
        assertEquals(12.0, v1.dotProduct(std), 1.0e-12);

        boolean ex = false;
        try {
            v1.dotProduct(new OpenMapRealVector(2));
        } catch (DimensionMismatchException e) {
            ex = true;
        }
        assertTrue(ex);
    }

    @Test
    public void testEbeOperations() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[]{1.0, 2.0, 0.0});
        RealVector v2 = new ArrayRealVector(new double[]{2.0, 4.0, 1.0});

        RealVector div = v1.ebeDivide(v2);
        assertEquals(0.5, div.getEntry(0), 1.0e-12);
        assertEquals(0.5, div.getEntry(1), 1.0e-12);
        assertEquals(0.0, div.getEntry(2), 1.0e-12);

        RealVector mul = v1.ebeMultiply(v2);
        assertEquals(2.0, mul.getEntry(0), 1.0e-12);
        assertEquals(8.0, mul.getEntry(1), 1.0e-12);
        assertEquals(0.0, mul.getEntry(2), 1.0e-12);
    }

    @Test
    public void testGetSubVector() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(new double[]{0.0, 1.0, 2.0, 3.0, 0.0});
        OpenMapRealVector sub = v.getSubVector(1, 3);
        assertEquals(3, sub.getDimension());
        assertEquals(1.0, sub.getEntry(0), 1.0e-12);
        assertEquals(2.0, sub.getEntry(1), 1.0e-12);
        assertEquals(3.0, sub.getEntry(2), 1.0e-12);

        boolean posEx = false;
        try {
            v.getSubVector(1, -1);
        } catch (NotPositiveException e) {
            posEx = true;
        }
        assertTrue(posEx);
    }

    @Test
    public void testDistances() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[]{1.0, 0.0, 3.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[]{0.0, 4.0, 3.0});

        assertEquals(Math.sqrt(17.0), v1.getDistance(v2), 1.0e-12);
        RealVector std = new ArrayRealVector(new double[]{0.0, 4.0, 3.0});
        assertEquals(Math.sqrt(17.0), v1.getDistance(std), 1.0e-12);

        assertEquals(5.0, v1.getL1Distance(v2), 1.0e-12);
        assertEquals(5.0, v1.getL1Distance(std), 1.0e-12);

        OpenMapRealVector v3 = new OpenMapRealVector(new double[]{1.0, 0.0, 5.0});
        OpenMapRealVector v4 = new OpenMapRealVector(new double[]{0.0, 4.0, 2.0});
        assertEquals(4.0, v3.getLInfDistance(v4), 1.0e-12);
        assertEquals(4.0, v3.getLInfDistance(std), 1.0e-12);
    }

    @Test
    public void testStateChecks() throws Throwable {
        OpenMapRealVector vNormal = new OpenMapRealVector(new double[]{1.0, 2.0});
        assertFalse(vNormal.isInfinite());
        assertFalse(vNormal.isNaN());

        OpenMapRealVector vInf = new OpenMapRealVector(new double[]{Double.POSITIVE_INFINITY, 0.0});
        assertTrue(vInf.isInfinite());
        assertFalse(vInf.isNaN());

        OpenMapRealVector vNaN = new OpenMapRealVector(new double[]{Double.NaN, 0.0});
        assertFalse(vNaN.isInfinite());
        assertTrue(vNaN.isNaN());
    }

    @Test
    public void testMapOperations() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(new double[]{0.0, 1.0, 2.0});
        OpenMapRealVector mapped = v.mapAdd(1.0);
        assertEquals(1.0, mapped.getEntry(0), 1.0e-12);
        assertEquals(2.0, mapped.getEntry(1), 1.0e-12);

        OpenMapRealVector mappedSelf = v.mapAddToSelf(1.0);
        assertEquals(1.0, mappedSelf.getEntry(0), 1.0e-12);
    }

    @Test
    public void testProjectionAndUnit() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[]{1.0, 0.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[]{1.0, 0.0});

        RealVector proj = v1.projection(v2);
        assertEquals(1.0, proj.getEntry(0), 1.0e-12);

        OpenMapRealVector unit = v1.unitVector();
        assertEquals(1.0, unit.getEntry(0), 1.0e-12);

        boolean zeroNormEx = false;
        try {
            OpenMapRealVector zero = new OpenMapRealVector(2);
            zero.unitize();
        } catch (MathArithmeticException e) {
            zeroNormEx = true;
        }
        assertTrue(zeroNormEx);
    }

    @Test
    public void testToArrayAndHashCodeEquals() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[]{0.0, 1.0, 0.0});
        double[] arr = v1.toArray();
        assertEquals(3, arr.length);
        assertEquals(1.0, arr[1], 1.0e-12);

        OpenMapRealVector v2 = new OpenMapRealVector(new double[]{0.0, 1.0, 0.0});
        OpenMapRealVector v3 = new OpenMapRealVector(new double[]{0.0, 2.0, 0.0});

        assertEquals(v1, v1);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
        assertFalse(v1.equals(v3));
        assertFalse(v1.equals(null));
        assertFalse(v1.equals("NotAVector"));

        OpenMapRealVector v4 = new OpenMapRealVector(3, 1.0e-5);
        OpenMapRealVector v5 = new OpenMapRealVector(3, 1.0e-4);
        assertFalse(v4.equals(v5));
        
        OpenMapRealVector v6 = new OpenMapRealVector(4);
        assertFalse(v1.equals(v6));
    }

    @Test
    public void testSetSubVector() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(5);
        RealVector sub = new ArrayRealVector(new double[]{1.0, 2.0});
        v.setSubVector(1, sub);
        assertEquals(1.0, v.getEntry(1), 1.0e-12);
        assertEquals(2.0, v.getEntry(2), 1.0e-12);
    }

    @Test
    public void testSubtract() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[]{1.0, 2.0, 0.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[]{0.0, 2.0, 3.0});

        OpenMapRealVector sub1 = v1.subtract(v2);
        assertEquals(1.0, sub1.getEntry(0), 1.0e-12);
        assertEquals(0.0, sub1.getEntry(1), 1.0e-12);
        assertEquals(-3.0, sub1.getEntry(2), 1.0e-12);

        RealVector std = new ArrayRealVector(new double[]{0.0, 2.0, 3.0});
        RealVector sub2 = v1.subtract(std);
        assertEquals(1.0, sub2.getEntry(0), 1.0e-12);
    }

    @Test
    public void testSparseIteratorAndEntries() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(new double[]{0.0, 5.0, 0.0});
        java.util.Iterator<RealVector.Entry> it = v.sparseIterator();
        assertTrue(it.hasNext());
        RealVector.Entry entry = it.next();
        assertEquals(5.0, entry.getValue(), 1.0e-12);
        assertEquals(1, entry.getIndex());

        entry.setValue(6.0);
        assertEquals(6.0, v.getEntry(1), 1.0e-12);

        java.util.Iterator<RealVector.Entry> it2 = v.sparseIterator();
        assertTrue(it2.hasNext());
        RealVector.Entry entry2 = it2.next();
        boolean unsupportedThrown = false;
        try {
            it2.remove();
        } catch (UnsupportedOperationException e) {
            unsupportedThrown = true;
        }
        assertTrue(unsupportedThrown);
    }
}