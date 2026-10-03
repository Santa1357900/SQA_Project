package org.apache.commons.math.geometry;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.math.exception.MathArithmeticException;

public class Vector3DTest {

    private static final double EPS = 1e-12;

    @Test
    public void testConstants() throws Throwable {
        assertEquals(0.0, Vector3D.ZERO.getX(), EPS);
        assertEquals(0.0, Vector3D.ZERO.getY(), EPS);
        assertEquals(0.0, Vector3D.ZERO.getZ(), EPS);

        assertEquals(1.0, Vector3D.PLUS_I.getX(), EPS);
        assertEquals(0.0, Vector3D.PLUS_I.getY(), EPS);
        assertEquals(0.0, Vector3D.PLUS_I.getZ(), EPS);

        assertEquals(-1.0, Vector3D.MINUS_I.getX(), EPS);
        assertEquals(0.0, Vector3D.MINUS_I.getY(), EPS);
        assertEquals(0.0, Vector3D.MINUS_I.getZ(), EPS);

        assertEquals(0.0, Vector3D.PLUS_J.getX(), EPS);
        assertEquals(1.0, Vector3D.PLUS_J.getY(), EPS);
        assertEquals(0.0, Vector3D.PLUS_J.getZ(), EPS);

        assertEquals(0.0, Vector3D.MINUS_J.getX(), EPS);
        assertEquals(-1.0, Vector3D.MINUS_J.getY(), EPS);
        assertEquals(0.0, Vector3D.MINUS_J.getZ(), EPS);

        assertEquals(0.0, Vector3D.PLUS_K.getX(), EPS);
        assertEquals(0.0, Vector3D.PLUS_K.getY(), EPS);
        assertEquals(1.0, Vector3D.PLUS_K.getZ(), EPS);

        assertEquals(0.0, Vector3D.MINUS_K.getX(), EPS);
        assertEquals(0.0, Vector3D.MINUS_K.getY(), EPS);
        assertEquals(-1.0, Vector3D.MINUS_K.getZ(), EPS);

        assertTrue(Vector3D.NaN.isNaN());
        assertTrue(Vector3D.POSITIVE_INFINITY.isInfinite());
        assertTrue(Vector3D.NEGATIVE_INFINITY.isInfinite());
    }

    @Test
    public void testConstructors() throws Throwable {
        Vector3D v1 = new Vector3D(1.0, 2.0, 3.0);
        assertEquals(1.0, v1.getX(), EPS);
        assertEquals(2.0, v1.getY(), EPS);
        assertEquals(3.0, v1.getZ(), EPS);

        Vector3D v2 = new Vector3D(0.0, 0.0);
        assertEquals(1.0, v2.getX(), EPS);
        assertEquals(0.0, v2.getY(), EPS);
        assertEquals(0.0, v2.getZ(), EPS);

        Vector3D v3 = new Vector3D(2.0, v1);
        assertEquals(2.0, v3.getX(), EPS);
        assertEquals(4.0, v3.getY(), EPS);
        assertEquals(6.0, v3.getZ(), EPS);

        Vector3D v4 = new Vector3D(1.0, v1, 2.0, v3);
        assertEquals(5.0, v4.getX(), EPS);
        assertEquals(10.0, v4.getY(), EPS);
        assertEquals(15.0, v4.getZ(), EPS);

        Vector3D v5 = new Vector3D(1.0, v1, 1.0, v2, 1.0, v3);
        assertEquals(4.0, v5.getX(), EPS);
        assertEquals(6.0, v5.getY(), EPS);
        assertEquals(9.0, v5.getZ(), EPS);

        Vector3D v6 = new Vector3D(1.0, v1, 1.0, v2, 1.0, v3, 1.0, v4);
        assertEquals(9.0, v6.getX(), EPS);
        assertEquals(16.0, v6.getY(), EPS);
        assertEquals(24.0, v6.getZ(), EPS);
    }

    @Test
    public void testNorms() throws Throwable {
        Vector3D v = new Vector3D(1.0, -2.0, 2.0);
        assertEquals(5.0, v.getNorm1(), EPS);
        assertEquals(3.0, v.getNorm(), EPS);
        assertEquals(9.0, v.getNormSq(), EPS);
        assertEquals(2.0, v.getNormInf(), EPS);
    }

    @Test
    public void testAnglesAndCoordinates() throws Throwable {
        Vector3D v = new Vector3D(1.0, 1.0, 1.0);
        assertTrue(!Double.isNaN(v.getAlpha()));
        assertTrue(!Double.isNaN(v.getDelta()));

        Vector3D v1 = Vector3D.PLUS_I;
        Vector3D v2 = Vector3D.PLUS_J;
        assertEquals(Math.PI / 2.0, Vector3D.angle(v1, v2), EPS);
        
        try {
            Vector3D.angle(Vector3D.ZERO, v1);
            fail("Expected MathArithmeticException");
        } catch (MathArithmeticException e) {
            // expected
        }

        Vector3D v3 = new Vector3D(1.0, 0.0, 1e-10);
        Vector3D v4 = new Vector3D(1.0, 0.0, -1e-10);
        assertTrue(Vector3D.angle(v3, v4) >= 0.0);
    }

    @Test
    public void testOperations() throws Throwable {
        Vector3D v1 = new Vector3D(1.0, 2.0, 3.0);
        Vector3D v2 = new Vector3D(4.0, 5.0, 6.0);

        Vector3D add = v1.add(v2);
        assertEquals(5.0, add.getX(), EPS);

        Vector3D addScaled = v1.add(2.0, v2);
        assertEquals(9.0, addScaled.getX(), EPS);

        Vector3D sub = v1.subtract(v2);
        assertEquals(-3.0, sub.getX(), EPS);

        Vector3D subScaled = v1.subtract(2.0, v2);
        assertEquals(-7.0, subScaled.getX(), EPS);

        Vector3D neg = v1.negate();
        assertEquals(-1.0, neg.getX(), EPS);

        Vector3D mult = v1.scalarMultiply(3.0);
        assertEquals(3.0, mult.getX(), EPS);

        Vector3D norm = v1.normalize();
        assertEquals(1.0, norm.getNorm(), EPS);

        try {
            Vector3D.ZERO.normalize();
            fail("Expected MathArithmeticException");
        } catch (MathArithmeticException e) {
            // expected
        }
    }

    @Test
    public void testOrthogonal() throws Throwable {
        Vector3D v = new Vector3D(1.0, 2.0, 3.0);
        Vector3D orth = v.orthogonal();
        assertEquals(0.0, Vector3D.dotProduct(v, orth), EPS);

        Vector3D vY = new Vector3D(0.0, 1.0, 0.0);
        Vector3D orthY = vY.orthogonal();
        assertEquals(0.0, Vector3D.dotProduct(vY, orthY), EPS);

        Vector3D vZ = new Vector3D(0.0, 0.0, 1.0);
        Vector3D orthZ = vZ.orthogonal();
        assertEquals(0.0, Vector3D.dotProduct(vZ, orthZ), EPS);

        try {
            Vector3D.ZERO.orthogonal();
            fail("Expected MathArithmeticException");
        } catch (MathArithmeticException e) {
            // expected
        }
    }

    @Test
    public void testProductsAndDistances() throws Throwable {
        Vector3D v1 = new Vector3D(1.0, 2.0, 3.0);
        Vector3D v2 = new Vector3D(4.0, 5.0, 6.0);

        assertEquals(32.0, Vector3D.dotProduct(v1, v2), EPS);

        Vector3D cross = Vector3D.crossProduct(v1, v2);
        assertEquals(-3.0, cross.getX(), EPS);
        assertEquals(6.0, cross.getY(), EPS);
        assertEquals(-3.0, cross.getZ(), EPS);

        assertEquals(Vector3D.distance1(v1, v2), v1.subtract(v2).getNorm1(), EPS);
        assertEquals(Vector3D.distance(v1, v2), v1.subtract(v2).getNorm(), EPS);
        assertEquals(Vector3D.distanceInf(v1, v2), v1.subtract(v2).getNormInf(), EPS);
        assertEquals(Vector3D.distanceSq(v1, v2), v1.subtract(v2).getNormSq(), EPS);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Vector3D v1 = new Vector3D(1.0, 2.0, 3.0);
        Vector3D v2 = new Vector3D(1.0, 2.0, 3.0);
        Vector3D v3 = new Vector3D(3.0, 2.0, 1.0);

        assertEquals(v1, v1);
        assertEquals(v1, v2);
        assertFalse(v1.equals(v3));
        assertFalse(v1.equals(null));
        assertFalse(v1.equals("NotAVector"));

        assertEquals(Vector3D.NaN, Vector3D.NaN);
        assertFalse(v1.equals(Vector3D.NaN));
        assertFalse(Vector3D.NaN.equals(v1));

        assertEquals(v1.hashCode(), v2.hashCode());
        assertEquals(8, Vector3D.NaN.hashCode());
    }

    @Test
    public void testToString() throws Throwable {
        Vector3D v = new Vector3D(1.0, 2.0, 3.0);
        assertNotNull(v.toString());
        assertTrue(v.toString().length() > 0);
    }
}