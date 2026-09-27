package org.apache.commons.math.geometry.euclidean.threed;

import org.junit.Test;
import static org.junit.Assert.*;

public class RotationTest {

    @Test
    public void testIdentityAndGetters() throws Throwable {
        Rotation r = Rotation.IDENTITY;
        assertEquals(1.0, r.getQ0(), 1.0e-15);
        assertEquals(0.0, r.getQ1(), 1.0e-15);
        assertEquals(0.0, r.getQ2(), 1.0e-15);
        assertEquals(0.0, r.getQ3(), 1.0e-15);
        assertEquals(0.0, r.getAngle(), 1.0e-15);
        
        Vector3D axis = r.getAxis();
        assertEquals(1.0, axis.getX(), 1.0e-15);
        assertEquals(0.0, axis.getY(), 1.0e-15);
        assertEquals(0.0, axis.getZ(), 1.0e-15);
    }

    @Test
    public void testQuaternionNormalization() throws Throwable {
        Rotation r = new Rotation(2.0, 0.0, 0.0, 0.0, true);
        assertEquals(1.0, r.getQ0(), 1.0e-15);
        
        Rotation r2 = new Rotation(0.0, 2.0, 0.0, 0.0, false);
        assertEquals(0.0, r2.getQ0(), 1.0e-15);
        assertEquals(2.0, r2.getQ1(), 1.0e-15);
    }

    @Test
    public void testAxisAndAngleConstructor() throws Throwable {
        Vector3D axis = new Vector3D(0.0, 0.0, 1.0);
        Rotation r = new Rotation(axis, Math.PI * 0.5);
        assertEquals(Math.PI * 0.5, r.getAngle(), 1.0e-12);

        Vector3D transformed = r.applyTo(Vector3D.PLUS_I);
        assertEquals(0.0, transformed.getX(), 1.0e-12);
        assertEquals(1.0, transformed.getY(), 1.0e-12);
        assertEquals(0.0, transformed.getZ(), 1.0e-12);
    }

    @Test(expected = RuntimeException.class)
    public void testZeroNormAxisConstructor() throws Throwable {
        Vector3D zeroAxis = new Vector3D(0.0, 0.0, 0.0);
        new Rotation(zeroAxis, 1.0);
    }

    @Test
    public void testMatrixConstructorAndGetMatrix() throws Throwable {
        Rotation r = new Rotation(Vector3D.PLUS_K, Math.PI * 0.5);
        double[][] m = r.getMatrix();
        assertEquals(3, m.length);
        assertEquals(3, m[0].length);

        Rotation rFromMatrix = new Rotation(m, 1.0e-10);
        assertEquals(r.getAngle(), rFromMatrix.getAngle(), 1.0e-10);
    }

    @Test(expected = NotARotationMatrixException.class)
    public void testInvalidMatrixDimensions() throws Throwable {
        double[][] badMatrix = new double[2][3];
        new Rotation(badMatrix, 1.0e-10);
    }

    @Test(expected = NotARotationMatrixException.class)
    public void testNegativeDeterminantMatrix() throws Throwable {
        double[][] negDetMatrix = new double[][] {
            {-1.0, 0.0, 0.0},
            {0.0, 1.0, 0.0},
            {0.0, 0.0, 1.0}
        };
        new Rotation(negDetMatrix, 1.0e-10);
    }

    @Test(expected = NotARotationMatrixException.class)
    public void testNonOrthogonalMatrixNotConverging() throws Throwable {
        double[][] nonOrth = new double[][] {
            {10.0, 0.0, 0.0},
            {0.0, 10.0, 0.0},
            {0.0, 0.0, 10.0}
        };
        new Rotation(nonOrth, 1.0e-15);
    }

    @Test
    public void testTwoVectorsConstructor() throws Throwable {
        Rotation r = new Rotation(Vector3D.PLUS_I, Vector3D.PLUS_J);
        Vector3D img = r.applyTo(Vector3D.PLUS_I);
        assertEquals(Vector3D.PLUS_J.getX(), img.getX(), 1.0e-12);
        assertEquals(Vector3D.PLUS_J.getY(), img.getY(), 1.0e-12);
        assertEquals(Vector3D.PLUS_J.getZ(), img.getZ(), 1.0e-12);
    }

    @Test
    public void testOppositeVectorsConstructor() throws Throwable {
        Rotation r = new Rotation(Vector3D.PLUS_I, new Vector3D(-1.0, 0.0, 0.0));
        assertEquals(Math.PI, r.getAngle(), 1.0e-12);
    }

    @Test(expected = RuntimeException.class)
    public void testZeroNormVectorConstructor() throws Throwable {
        new Rotation(new Vector3D(0, 0, 0), Vector3D.PLUS_J);
    }

    @Test
    public void testFourVectorsConstructor() throws Throwable {
        Rotation r = new Rotation(Vector3D.PLUS_I, Vector3D.PLUS_J, Vector3D.PLUS_J, Vector3D.PLUS_K);
        assertNotNull(r);
        
        // Test parallel/colinear branches for coverage
        Rotation r2 = new Rotation(Vector3D.PLUS_I, Vector3D.PLUS_J, Vector3D.PLUS_I, Vector3D.PLUS_J);
        assertNotNull(r2);
    }

    @Test(expected = RuntimeException.class)
    public void testFourVectorsZeroNorm() throws Throwable {
        new Rotation(new Vector3D(0,0,0), Vector3D.PLUS_J, Vector3D.PLUS_I, Vector3D.PLUS_J);
    }

    @Test
    public void testCardanEulerConstructorsAndGetAngles() throws Throwable {
        for (RotationOrder order : new RotationOrder[] {
            RotationOrder.XYZ, RotationOrder.XZY, RotationOrder.YXZ,
            RotationOrder.YZX, RotationOrder.ZXY, RotationOrder.ZYX,
            RotationOrder.XYX, RotationOrder.XZX, RotationOrder.YXY,
            RotationOrder.YZY, RotationOrder.ZXZ, RotationOrder.ZYZ
        }) {
            Rotation r = new Rotation(order, 0.1, 0.2, 0.3);
            double[] angles = r.getAngles(order);
            assertEquals(3, angles.length);
        }
    }

    @Test(expected = CardanEulerSingularityException.class)
    public void testCardanSingularity() throws Throwable {
        Rotation r = new Rotation(RotationOrder.XYZ, 0.0, Math.PI * 0.5, 0.0);
        r.getAngles(RotationOrder.XYZ);
    }

    @Test(expected = CardanEulerSingularityException.class)
    public void testEulerSingularity() throws Throwable {
        Rotation r = new Rotation(RotationOrder.XYX, 0.0, 0.0, 0.0);
        r.getAngles(RotationOrder.XYX);
    }

    @Test
    public void testRevertAndComposition() throws Throwable {
        Rotation r1 = new Rotation(Vector3D.PLUS_K, 0.5);
        Rotation r2 = r1.revert();
        Rotation comp = r1.applyTo(r2);
        assertEquals(0.0, comp.getAngle(), 1.0e-12);

        Rotation invComp = r1.applyInverseTo(r2);
        assertNotNull(invComp);
    }

    @Test
    public void testApplyInverseToVector() throws Throwable {
        Rotation r = new Rotation(Vector3D.PLUS_K, Math.PI * 0.5);
        Vector3D v = r.applyInverseTo(Vector3D.PLUS_J);
        assertEquals(Vector3D.PLUS_I.getX(), v.getX(), 1.0e-12);
        assertEquals(Vector3D.PLUS_I.getY(), v.getY(), 1.0e-12);
        assertEquals(Vector3D.PLUS_I.getZ(), v.getZ(), 1.0e-12);
    }

    @Test
    public void testDistance() throws Throwable {
        Rotation r1 = new Rotation(Vector3D.PLUS_I, 0.1);
        Rotation r2 = new Rotation(Vector3D.PLUS_I, 0.2);
        double dist = Rotation.distance(r1, r2);
        assertEquals(0.1, dist, 1.0e-12);
        
        double zeroDist = Rotation.distance(r1, r1);
        assertEquals(0.0, zeroDist, 1.0e-12);
    }

    @Test
    public void testAxisSpecialBranches() throws Throwable {
        Rotation r = new Rotation(-0.5, 0.1, 0.2, 0.3, true);
        Vector3D axis = r.getAxis();
        assertNotNull(axis);
        
        Rotation id = Rotation.IDENTITY;
        Vector3D idAxis = id.getAxis();
        assertEquals(1.0, idAxis.getX(), 1.0e-12);
    }

    @Test
    public void testAngleSpecialBranches() throws Throwable {
        Rotation r1 = new Rotation(-0.05, 0.5, 0.5, 0.5, true);
        assertTrue(r1.getAngle() >= 0.0);

        Rotation r2 = new Rotation(-0.9, 0.1, 0.1, 0.1, true);
        assertTrue(r2.getAngle() >= 0.0);
    }
}