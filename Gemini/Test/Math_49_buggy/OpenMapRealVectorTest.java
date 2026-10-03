package org.apache.commons.math.linear;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.exception.MathArithmeticException;

public class OpenMapRealVectorTest {

    @Test
    public void testConstructors() throws Throwable {
        OpenMapRealVector v0 = new OpenMapRealVector();
        assertEquals(0, v0.getDimension());

        OpenMapRealVector v1 = new OpenMapRealVector(10);
        assertEquals(10, v1.getDimension());

        OpenMapRealVector v2 = new OpenMapRealVector(10, 1e-6);
        assertEquals(10, v2.getDimension());

        OpenMapRealVector v3 = new OpenMapRealVector(10, 5);
        assertEquals(10, v3.getDimension());

        OpenMapRealVector v4 = new OpenMapRealVector(10, 5, 1e-6);
        assertEquals(10, v4.getDimension());

        double[] arr = new double[] {0.0, 1.0, 2.0, 0.0};
        OpenMapRealVector v5 = new OpenMapRealVector(arr);
        assertEquals(4, v5.getDimension());
        assertEquals(1.0, v5.getEntry(1), 1e-12);

        OpenMapRealVector v6 = new OpenMapRealVector(arr, 1e-6);
        assertEquals(4, v6.getDimension());

        Double[] boxedArr = new Double[] {Double.valueOf(0.0), Double.valueOf(3.0), Double.valueOf(0.0)};
        OpenMapRealVector v7 = new OpenMapRealVector(boxedArr);
        assertEquals(3, v7.getDimension());
        assertEquals(3.0, v7.getEntry(1), 1e-12);

        OpenMapRealVector v8 = new OpenMapRealVector(boxedArr, 1e-6);
        assertEquals(3, v8.getDimension());

        OpenMapRealVector v9 = new OpenMapRealVector(v5);
        assertEquals(v5.getDimension(), v9.getDimension());
        assertEquals(v5.getEntry(1), v9.getEntry(1), 1e-12);

        ArrayRealVector normalVec = new ArrayRealVector(new double[] {0.0, 5.0, 0.0});
        OpenMapRealVector v10 = new OpenMapRealVector(normalVec);
        assertEquals(3, v10.getDimension());
        assertEquals(5.0, v10.getEntry(1), 1e-12);
    }

    @Test
    public void testAdd() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[] {1.0, 0.0, 3.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[] {0.0, 2.0, 3.0});

        OpenMapRealVector sum1 = v1.add(v2);
        assertEquals(1.0, sum1.getEntry(0), 1e-12);
        assertEquals(2.0, sum1.getEntry(1), 1e-12);
        assertEquals(6.0, sum1.getEntry(2), 1e-12);

        ArrayRealVector v3 = new ArrayRealVector(new double[] {1.0, 1.0, 1.0});
        RealVector sum2 = v1.add(v3);
        assertEquals(2.0, sum2.getEntry(0), 1e-12);
        assertEquals(1.0, sum2.getEntry(1), 1e-12);
        assertEquals(4.0, sum2.getEntry(2), 1e-12);

        // Test addition where other vector is larger in size of entries
        OpenMapRealVector v4 = new OpenMapRealVector(new double[] {1.0, 0.0, 0.0});
        OpenMapRealVector v5 = new OpenMapRealVector(new double[] {0.0, 2.0, 4.0, 0.0});
        // Dimensions must match for add, let's create same dimension
        OpenMapRealVector v6 = new OpenMapRealVector(new double[] {1.0, 0.0, 0.0, 0.0});
        OpenMapRealVector v7 = new OpenMapRealVector(new double[] {0.0, 2.0, 4.0, 0.0});
        OpenMapRealVector sum3 = v6.add(v7);
        assertEquals(1.0, sum3.getEntry(0), 1e-12);
        assertEquals(2.0, sum3.getEntry(1), 1e-12);
        assertEquals(4.0, sum3.getEntry(2), 1e-12);
    }

    @Test
    public void testAppend() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[] {1.0, 2.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[] {3.0, 4.0});

        OpenMapRealVector app1 = v1.append(v2);
        assertEquals(4, app1.getDimension());
        assertEquals(3.0, app1.getEntry(2), 1e-12);

        ArrayRealVector v3 = new ArrayRealVector(new double[] {5.0});
        OpenMapRealVector app2 = v1.append(v3);
        assertEquals(3, app2.getDimension());
        assertEquals(5.0, app2.getEntry(2), 1e-12);

        OpenMapRealVector app3 = v1.append(6.0);
        assertEquals(3, app3.getDimension());
        assertEquals(6.0, app3.getEntry(2), 1e-12);

        OpenMapRealVector app4 = v1.append(new double[] {7.0, 8.0});
        assertEquals(4, app4.getDimension());
        assertEquals(7.0, app4.getEntry(2), 1e-12);
        assertEquals(8.0, app4.getEntry(3), 1e-12);
    }

    @Test
    public void testDotProduct() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[] {1.0, 0.0, 2.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[] {0.0, 3.0, 4.0});

        double dp1 = v1.dotProduct(v2);
        assertEquals(8.0, dp1, 1e-12);

        // Test thisIsSmaller branch in dotProduct
        OpenMapRealVector v3 = new OpenMapRealVector(new double[] {1.0, 0.0, 0.0, 0.0});
        OpenMapRealVector v4 = new OpenMapRealVector(new double[] {2.0, 3.0, 4.0, 5.0});
        double dp2 = v3.dotProduct(v4);
        assertEquals(2.0, dp2, 1e-12);

        ArrayRealVector v5 = new ArrayRealVector(new double[] {0.0, 3.0, 4.0});
        double dp3 = v1.dotProduct(v5);
        assertEquals(8.0, dp3, 1e-12);
    }

    @Test
    public void testEbeOperations() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[] {2.0, 4.0, 6.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[] {1.0, 2.0, 3.0});

        RealVector div1 = v1.ebeDivide(v2);
        assertEquals(2.0, div1.getEntry(0), 1e-12);
        assertEquals(2.0, div1.getEntry(1), 1e-12);
        assertEquals(2.0, div1.getEntry(2), 1e-12);

        RealVector div2 = v1.ebeDivide(new double[] {1.0, 2.0, 3.0});
        assertEquals(2.0, div2.getEntry(0), 1e-12);

        RealVector mul1 = v1.ebeMultiply(v2);
        assertEquals(2.0, mul1.getEntry(0), 1e-12);
        assertEquals(8.0, mul1.getEntry(1), 1e-12);
        assertEquals(18.0, mul1.getEntry(2), 1e-12);

        RealVector mul2 = v1.ebeMultiply(new double[] {1.0, 2.0, 3.0});
        assertEquals(2.0, mul2.getEntry(0), 1e-12);
    }

    @Test
    public void testGetSubVectorAndData() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(new double[] {0.0, 1.0, 2.0, 0.0, 4.0});
        OpenMapRealVector sub = v.getSubVector(1, 3);
        assertEquals(3, sub.getDimension());
        assertEquals(1.0, sub.getEntry(0), 1e-12);
        assertEquals(2.0, sub.getEntry(1), 1e-12);
        assertEquals(0.0, sub.getEntry(2), 1e-12);

        double[] data = v.getData();
        assertEquals(5, data.length);
        assertEquals(1.0, data[1], 1e-12);

        double[] array = v.toArray();
        assertEquals(5, array.length);
    }

    @Test
    public void testDistances() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[] {1.0, 2.0, 0.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[] {4.0, 6.0, 0.0});
        OpenMapRealVector v3 = new OpenMapRealVector(new double[] {0.0, 0.0, 5.0}); // triggers not contains key in distance

        double dist1 = v1.getDistance(v2);
        assertEquals(5.0, dist1, 1e-12);

        double distOpen = v1.getDistance(v3);
        assertTrue(distOpen > 0.0);

        ArrayRealVector ar1 = new ArrayRealVector(new double[] {4.0, 6.0, 0.0});
        double dist2 = v1.getDistance(ar1);
        assertEquals(5.0, dist2, 1e-12);

        double dist3 = v1.getDistance(new double[] {4.0, 6.0, 0.0});
        assertEquals(5.0, dist3, 1e-12);

        double l1_1 = v1.getL1Distance(v2);
        assertEquals(7.0, l1_1, 1e-12);

        double l1_open = v1.getL1Distance(v3);
        assertTrue(l1_open > 0.0);

        double l1_2 = v1.getL1Distance(ar1);
        assertEquals(7.0, l1_2, 1e-12);

        double l1_3 = v1.getL1Distance(new double[] {4.0, 6.0, 0.0});
        assertEquals(7.0, l1_3, 1e-12);

        double linf_1 = v1.getLInfDistance(ar1);
        assertEquals(4.0, linf_1, 1e-12);

        double linf_2 = v1.getLInfDistance(new double[] {4.0, 6.0, 0.0});
        assertEquals(4.0, linf_2, 1e-12);
        
        // Exercise private getLInfDistance(OpenMapRealVector) via RealVector dispatch
        double linf_3 = v1.getLInfDistance(v2);
        assertEquals(4.0, linf_3, 1e-12);

        OpenMapRealVector v4 = new OpenMapRealVector(new double[] {0.0, 0.0, 10.0});
        double linf_4 = v1.getLInfDistance(v4);
        assertEquals(10.0, linf_4, 1e-12);
    }

    @Test
    public void testInfiniteAndNaN() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[] {1.0, Double.NaN, 3.0});
        assertTrue(v1.isNaN());
        assertFalse(v1.isInfinite());

        OpenMapRealVector v2 = new OpenMapRealVector(new double[] {1.0, Double.POSITIVE_INFINITY, 3.0});
        assertFalse(v2.isNaN());
        assertTrue(v2.isInfinite());

        OpenMapRealVector v3 = new OpenMapRealVector(new double[] {1.0, 2.0, 3.0});
        assertFalse(v3.isNaN());
        assertFalse(v3.isInfinite());

        // Test case where NaN is encountered first in isInfinite
        OpenMapRealVector v4 = new OpenMapRealVector(new double[] {Double.NaN, Double.POSITIVE_INFINITY});
        assertFalse(v4.isInfinite());
    }

    @Test
    public void testMapOperations() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(new double[] {1.0, 0.0, 2.0});
        RealVector mappedAdd = v.mapAdd(5.0);
        assertEquals(6.0, mappedAdd.getEntry(0), 1e-12);
        assertEquals(5.0, mappedAdd.getEntry(1), 1e-12);

        v.mapAddToSelf(5.0);
        assertEquals(6.0, v.getEntry(0), 1e-12);
        assertEquals(5.0, v.getEntry(1), 1e-12);
    }

    @Test
    public void testOuterProductAndProjection() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(new double[] {1.0, 0.0, 2.0});
        RealMatrix outer = v.outerProduct(new double[] {1.0, 2.0});
        assertEquals(3, outer.getRowDimension());
        assertEquals(2, outer.getColumnDimension());
        assertEquals(2.0, outer.getEntry(0, 1), 1e-12);

        OpenMapRealVector projTarget = new OpenMapRealVector(new double[] {1.0, 1.0, 1.0});
        RealVector proj = v.projection(projTarget);
        assertEquals(3, proj.getDimension());

        OpenMapRealVector projArr = v.projection(new double[] {1.0, 1.0, 1.0});
        assertEquals(3, projArr.getDimension());
    }

    @Test
    public void testSettersAndSubVectors() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(5);
        v.setEntry(2, 3.0);
        assertEquals(3.0, v.getEntry(2), 1e-12);

        // Test setting a default value removes the entry if present
        v.setEntry(2, 0.0);
        assertEquals(0.0, v.getEntry(2), 1e-12);

        v.setSubVector(1, new double[] {4.0, 5.0});
        assertEquals(4.0, v.getEntry(1), 1e-12);
        assertEquals(5.0, v.getEntry(2), 1e-12);

        v.setSubVector(1, new ArrayRealVector(new double[] {6.0, 7.0}));
        assertEquals(6.0, v.getEntry(1), 1e-12);

        v.set(9.0);
        assertEquals(9.0, v.getEntry(0), 1e-12);
    }

    @Test
    public void testSubtract() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[] {5.0, 0.0, 3.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[] {2.0, 1.0, 0.0});

        OpenMapRealVector sub1 = v1.subtract(v2);
        assertEquals(3.0, sub1.getEntry(0), 1e-12);
        assertEquals(-1.0, sub1.getEntry(1), 1e-12);
        assertEquals(3.0, sub1.getEntry(2), 1e-12);

        ArrayRealVector v3 = new ArrayRealVector(new double[] {2.0, 1.0, 0.0});
        OpenMapRealVector sub2 = v1.subtract(v3);
        assertEquals(3.0, sub2.getEntry(0), 1e-12);

        OpenMapRealVector sub3 = v1.subtract(new double[] {2.0, 1.0, 0.0});
        assertEquals(3.0, sub3.getEntry(0), 1e-12);
    }

    @Test
    public void testUnitVectorAndUnitize() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(new double[] {3.0, 4.0, 0.0});
        OpenMapRealVector unit = v.unitVector();
        assertEquals(1.0, unit.getNorm(), 1e-12);

        OpenMapRealVector zeroV = new OpenMapRealVector(new double[] {0.0, 0.0, 0.0});
        boolean exceptionThrown = false;
        try {
            zeroV.unitize();
        } catch (MathArithmeticException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testHashCodeAndEquals() throws Throwable {
        OpenMapRealVector v1 = new OpenMapRealVector(new double[] {1.0, 0.0, 2.0});
        OpenMapRealVector v2 = new OpenMapRealVector(new double[] {1.0, 0.0, 2.0});
        OpenMapRealVector v3 = new OpenMapRealVector(new double[] {1.0, 1.0, 2.0});
        OpenMapRealVector v4 = new OpenMapRealVector(new double[] {1.0, 0.0, 2.0}, 1.0e-11);
        OpenMapRealVector v5 = new OpenMapRealVector(new double[] {1.0, 0.0, 2.0, 0.0});

        assertEquals(v1, v1);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());

        assertFalse(v1.equals(null));
        assertFalse(v1.equals(new Object()));
        assertFalse(v1.equals(v3));
        assertFalse(v1.equals(v4));
        assertFalse(v1.equals(v5));

        // Test equals where other has entries not in this
        OpenMapRealVector v6 = new OpenMapRealVector(new double[] {1.0, 0.0, 2.0});
        OpenMapRealVector v7 = new OpenMapRealVector(new double[] {1.0, 3.0, 2.0});
        assertFalse(v6.equals(v7));
    }

    @Test
    public void testSparsityAndIterators() throws Throwable {
        OpenMapRealVector v = new OpenMapRealVector(new double[] {1.0, 0.0, 0.0, 4.0});
        assertEquals(0.5, v.getSparsity(), 1e-12);

        java.util.Iterator<Entry> it = v.sparseIterator();
        int count = 0;
        while (it.hasNext()) {
            Entry entry = it.next();
            assertNotNull(entry);
            assertTrue(entry.getValue() != 0.0);
            count++;
        }
        assertEquals(2, count);

        boolean unsupportedThrown = false;
        try {
            it.remove();
        } catch (UnsupportedOperationException e) {
            unsupportedThrown = true;
        }
        assertTrue(unsupportedThrown);

        // Test OpenMapEntry methods directly via sparse iterator
        java.util.Iterator<Entry> it2 = v.sparseIterator();
        if (it2.hasNext()) {
            Entry e = it2.next();
            assertEquals(e.getIndex(), e.getIndex());
            e.setValue(5.0);
            assertEquals(5.0, e.getValue(), 1e-12);
        }
    }
}