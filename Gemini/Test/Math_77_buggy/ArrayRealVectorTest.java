package org.apache.commons.math.linear;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArrayRealVectorTest {

    @Test
    public void testConstructors() throws Throwable {
        ArrayRealVector v1 = new ArrayRealVector();
        assertEquals(0, v1.getDimension());

        ArrayRealVector v2 = new ArrayRealVector(5);
        assertEquals(5, v2.getDimension());
        assertEquals(0.0, v2.getEntry(0), 0.0);

        ArrayRealVector v3 = new ArrayRealVector(3, 2.5);
        assertEquals(3, v3.getDimension());
        assertEquals(2.5, v3.getEntry(0), 0.0);

        double[] d = new double[] { 1.0, 2.0, 3.0 };
        ArrayRealVector v4 = new ArrayRealVector(d);
        assertEquals(3, v4.getDimension());
        assertEquals(1.0, v4.getEntry(0), 0.0);

        ArrayRealVector v5 = new ArrayRealVector(d, true);
        assertEquals(3, v5.getDimension());

        ArrayRealVector v6 = new ArrayRealVector(d, false);
        assertEquals(3, v6.getDimension());

        ArrayRealVector v7 = new ArrayRealVector(d, 1, 2);
        assertEquals(2, v7.getDimension());
        assertEquals(2.0, v7.getEntry(0), 0.0);

        Double[] dObj = new Double[] { Double.valueOf(4.0), Double.valueOf(5.0) };
        ArrayRealVector v8 = new ArrayRealVector(dObj);
        assertEquals(2, v8.getDimension());
        assertEquals(4.0, v8.getEntry(0), 0.0);

        ArrayRealVector v9 = new ArrayRealVector(dObj, 0, 2);
        assertEquals(2, v9.getDimension());
        assertEquals(4.0, v9.getEntry(0), 0.0);

        ArrayRealVector v10 = new ArrayRealVector(v4);
        assertEquals(3, v10.getDimension());

        ArrayRealVector v11 = new ArrayRealVector(v4, true);
        assertEquals(3, v11.getDimension());

        ArrayRealVector v12 = new ArrayRealVector(v4, false);
        assertEquals(3, v12.getDimension());

        ArrayRealVector v13 = new ArrayRealVector(v1, v2);
        assertEquals(5, v13.getDimension());

        ArrayRealVector v14 = new ArrayRealVector(v1, (RealVector) v2);
        assertEquals(5, v14.getDimension());

        ArrayRealVector v15 = new ArrayRealVector((RealVector) v1, v2);
        assertEquals(5, v15.getDimension());

        ArrayRealVector v16 = new ArrayRealVector(v1, d);
        assertEquals(3, v16.getDimension());

        ArrayRealVector v17 = new ArrayRealVector(d, v1);
        assertEquals(3, v17.getDimension());

        ArrayRealVector v18 = new ArrayRealVector(d, d);
        assertEquals(6, v18.getDimension());
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullArray() throws Throwable {
        double[] nullArr = null;
        new ArrayRealVector(nullArr, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorEmptyArray() throws Throwable {
        new ArrayRealVector(new double[0], true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorPosSizeExceed() throws Throwable {
        new ArrayRealVector(new double[] { 1.0, 2.0 }, 1, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorDoubleObjPosSizeExceed() throws Throwable {
        new ArrayRealVector(new Double[] { Double.valueOf(1.0) }, 0, 5);
    }

    @Test
    public void testCopyAndDataMethods() throws Throwable {
        ArrayRealVector v = new ArrayRealVector(new double[] { 1.0, 2.0 });
        AbstractRealVector copy = v.copy();
        assertTrue(copy instanceof ArrayRealVector);
        assertEquals(v, copy);

        double[] data = v.getData();
        assertArrayEquals(new double[] { 1.0, 2.0 }, data, 0.0);

        double[] dataRef = v.getDataRef();
        assertNotNull(dataRef);
    }

    @Test
    public void testAdd() throws Throwable {
        ArrayRealVector v1 = new ArrayRealVector(new double[] { 1.0, 2.0 });
        ArrayRealVector v2 = new ArrayRealVector(new double[] { 3.0, 4.0 });
        ArrayRealVector res1 = v1.add(v2);
        assertArrayEquals(new double[] { 4.0, 6.0 }, res1.getData(), 0.0);

        RealVector res2 = v1.add((RealVector) v2);
        assertArrayEquals(new double[] { 4.0, 6.0 }, res2.toArray(), 0.0);

        RealVector res3 = v1.add(new double[] { 3.0, 4.0 });
        assertArrayEquals(new double[] { 4.0, 6.0 }, res3.toArray(), 0.0);
    }

    @Test
    public void testSubtract() throws Throwable {
        ArrayRealVector v1 = new ArrayRealVector(new double[] { 5.0, 7.0 });
        ArrayRealVector v2 = new ArrayRealVector(new double[] { 2.0, 3.0 });
        ArrayRealVector res1 = v1.subtract(v2);
        assertArrayEquals(new double[] { 3.0, 4.0 }, res1.getData(), 0.0);

        RealVector res2 = v1.subtract((RealVector) v2);
        assertArrayEquals(new double[] { 3.0, 4.0 }, res2.toArray(), 0.0);

        RealVector res3 = v1.subtract(new double[] { 2.0, 3.0 });
        assertArrayEquals(new double[] { 3.0, 4.0 }, res3.toArray(), 0.0);
    }

    @Test
    public void testMapOperations() throws Throwable {
        ArrayRealVector v = new ArrayRealVector(new double[] { 1.0, 4.0 });
        
        v.mapAddToSelf(1.0);
        assertArrayEquals(new double[] { 2.0, 5.0 }, v.getData(), 0.0);

        v.mapSubtractToSelf(1.0);
        assertArrayEquals(new double[] { 1.0, 4.0 }, v.getData(), 0.0);

        v.mapMultiplyToSelf(2.0);
        assertArrayEquals(new double[] { 2.0, 8.0 }, v.getData(), 0.0);

        v.mapDivideToSelf(2.0);
        assertArrayEquals(new double[] { 1.0, 4.0 }, v.getData(), 0.0);

        v.mapPowToSelf(2.0);
        assertArrayEquals(new double[] { 1.0, 16.0 }, v.getData(), 0.0);

        v.mapExpToSelf();
        v.mapExpm1ToSelf();
        v.mapLogToSelf();
        v.mapLog10ToSelf();
        v.mapLog1pToSelf();
        v.mapCoshToSelf();
        v.mapSinhToSelf();
        v.mapTanhToSelf();
        v.mapCosToSelf();
        v.mapSinToSelf();
        v.mapTanToSelf();
        v.mapAcosToSelf();
        v.mapAsinToSelf();
        v.mapAtanToSelf();
        v.mapInvToSelf();
        v.mapAbsToSelf();
        v.mapSqrtToSelf();
        v.mapCbrtToSelf();
        v.mapCeilToSelf();
        v.mapFloorToSelf();
        v.mapRintToSelf();
        v.mapSignumToSelf();
        v.mapUlpToSelf();
    }

    @Test
    public void testEbeOperations() throws Throwable {
        ArrayRealVector v1 = new ArrayRealVector(new double[] { 2.0, 4.0 });
        ArrayRealVector v2 = new ArrayRealVector(new double[] { 3.0, 5.0 });

        ArrayRealVector res1 = v1.ebeMultiply(v2);
        assertArrayEquals(new double[] { 6.0, 20.0 }, res1.getData(), 0.0);

        RealVector res2 = v1.ebeMultiply((RealVector) v2);
        assertArrayEquals(new double[] { 6.0, 20.0 }, res2.toArray(), 0.0);

        RealVector res3 = v1.ebeMultiply(new double[] { 3.0, 5.0 });
        assertArrayEquals(new double[] { 6.0, 20.0 }, res3.toArray(), 0.0);

        ArrayRealVector res4 = v1.ebeDivide(v2);
        assertArrayEquals(new double[] { 2.0 / 3.0, 4.0 / 5.0 }, res4.getData(), 0.0);

        RealVector res5 = v1.ebeDivide((RealVector) v2);
        assertArrayEquals(new double[] { 2.0 / 3.0, 4.0 / 5.0 }, res5.toArray(), 0.0);

        RealVector res6 = v1.ebeDivide(new double[] { 3.0, 5.0 });
        assertArrayEquals(new double[] { 2.0 / 3.0, 4.0 / 5.0 }, res6.toArray(), 0.0);
    }

    @Test
    public void testDotProduct() throws Throwable {
        ArrayRealVector v1 = new ArrayRealVector(new double[] { 1.0, 2.0 });
        ArrayRealVector v2 = new ArrayRealVector(new double[] { 3.0, 4.0 });

        assertEquals(11.0, v1.dotProduct(v2), 0.0);
        assertEquals(11.0, v1.dotProduct((RealVector) v2), 0.0);
        assertEquals(11.0, v1.dotProduct(new double[] { 3.0, 4.0 }), 0.0);
    }

    @Test
    public void testNormsAndDistances() throws Throwable {
        ArrayRealVector v1 = new ArrayRealVector(new double[] { 3.0, 4.0 });
        ArrayRealVector v2 = new ArrayRealVector(new double[] { 0.0, 0.0 });

        assertEquals(5.0, v1.getNorm(), 0.0);
        assertEquals(7.0, v1.getL1Norm(), 0.0);
        assertTrue(v1.getLInfNorm() >= 0.0);

        assertEquals(5.0, v1.getDistance(v2), 0.0);
        assertEquals(5.0, v1.getDistance((RealVector) v2), 0.0);
        assertEquals(5.0, v1.getDistance(new double[] { 0.0, 0.0 }), 0.0);

        assertEquals(7.0, v1.getL1Distance(v2), 0.0);
        assertEquals(7.0, v1.getL1Distance((RealVector) v2), 0.0);
        assertEquals(7.0, v1.getL1Distance(new double[] { 0.0, 0.0 }), 0.0);

        assertEquals(4.0, v1.getLInfDistance(v2), 0.0);
        assertEquals(4.0, v1.getLInfDistance((RealVector) v2), 0.0);
        assertEquals(4.0, v1.getLInfDistance(new double[] { 0.0, 0.0 }), 0.0);
    }

    @Test
    public void testUnitAndProjection() throws Throwable {
        ArrayRealVector v1 = new ArrayRealVector(new double[] { 3.0, 4.0 });
        RealVector unit = v1.unitVector();
        assertEquals(1.0, unit.getNorm(), 1e-12);

        v1.unitize();
        assertEquals(1.0, v1.getNorm(), 1e-12);

        ArrayRealVector v2 = new ArrayRealVector(new double[] { 1.0, 0.0 });
        ArrayRealVector v3 = new ArrayRealVector(new double[] { 2.0, 0.0 });
        RealVector proj1 = v2.projection(v3);
        assertNotNull(proj1);

        RealVector proj2 = v2.projection(new double[] { 2.0, 0.0 });
        assertNotNull(proj2);

        ArrayRealVector proj3 = v2.projection(v3);
        assertNotNull(proj3);
    }

    @Test(expected = ArithmeticException.class)
    public void testUnitVectorZeroNorm() throws Throwable {
        ArrayRealVector v = new ArrayRealVector(new double[] { 0.0, 0.0 });
        v.unitVector();
    }

    @Test(expected = ArithmeticException.class)
    public void testUnitizeZeroNorm() throws Throwable {
        ArrayRealVector v = new ArrayRealVector(new double[] { 0.0, 0.0 });
        v.unitize();
    }

    @Test
    public void testOuterProduct() throws Throwable {
        ArrayRealVector v1 = new ArrayRealVector(new double[] { 1.0, 2.0 });
        ArrayRealVector v2 = new ArrayRealVector(new double[] { 3.0, 4.0 });

        RealMatrix m1 = v1.outerProduct(v2);
        assertEquals(2, m1.getRowDimension());

        RealMatrix m2 = v1.outerProduct((RealVector) v2);
        assertEquals(2, m2.getRowDimension());

        RealMatrix m3 = v1.outerProduct(new double[] { 3.0, 4.0 });
        assertEquals(2, m3.getRowDimension());
    }

    @Test
    public void testGetSetAndAppend() throws Throwable {
        ArrayRealVector v = new ArrayRealVector(new double[] { 1.0, 2.0, 3.0 });
        assertEquals(2.0, v.getEntry(1), 0.0);
        assertEquals(3, v.getDimension());

        v.setEntry(1, 5.0);
        assertEquals(5.0, v.getEntry(1), 0.0);

        RealVector app1 = v.append(new ArrayRealVector(new double[] { 4.0 }));
        assertEquals(4, app1.getDimension());

        RealVector app2 = v.append(4.0);
        assertEquals(4, app2.getDimension());

        RealVector app3 = v.append(new double[] { 4.0 });
        assertEquals(4, app3.getDimension());

        RealVector sub = v.getSubVector(0, 2);
        assertEquals(2, sub.getDimension());

        v.setSubVector(0, new double[] { 9.0, 8.0 });
        assertEquals(9.0, v.getEntry(0), 0.0);

        v.setSubVector(0, new ArrayRealVector(new double[] { 7.0, 6.0 }));
        assertEquals(7.0, v.getEntry(0), 0.0);

        v.set(1.0);
        assertEquals(1.0, v.getEntry(0), 0.0);

        assertArrayEquals(v.getData(), v.toArray(), 0.0);
        assertNotNull(v.toString());
    }

    @Test
    public void testValidationAndState() throws Throwable {
        ArrayRealVector v = new ArrayRealVector(new double[] { 1.0, Double.NaN });
        assertTrue(v.isNaN());
        assertFalse(v.isInfinite());
        assertEquals(9, v.hashCode());

        ArrayRealVector vInf = new ArrayRealVector(new double[] { 1.0, Double.POSITIVE_INFINITY });
        assertFalse(vInf.isNaN());
        assertTrue(vInf.isInfinite());
        assertTrue(vInf.hashCode() != 0);

        ArrayRealVector vNormal = new ArrayRealVector(new double[] { 1.0, 2.0 });
        assertFalse(vNormal.isNaN());
        assertFalse(vNormal.isInfinite());

        assertTrue(vNormal.equals(vNormal));
        assertFalse(vNormal.equals(null));
        assertFalse(vNormal.equals("string"));
        assertFalse(vNormal.equals(new ArrayRealVector(new double[] { 1.0 })));

        ArrayRealVector vNaN1 = new ArrayRealVector(new double[] { Double.NaN, Double.NaN });
        ArrayRealVector vNaN2 = new ArrayRealVector(new double[] { Double.NaN, Double.NaN });
        assertTrue(vNaN1.equals(vNaN2));

        ArrayRealVector vOther = new ArrayRealVector(new double[] { 1.0, 3.0 });
        assertFalse(vNormal.equals(vOther));
    }
}