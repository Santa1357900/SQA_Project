package org.apache.commons.math3.geometry.euclidean.threed;

import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.geometry.euclidean.oned.IntervalsSet;
import org.apache.commons.math3.geometry.euclidean.oned.Vector1D;
import org.apache.commons.math3.util.FastMath;
import org.junit.Test;

import static org.junit.Assert.*;

public class LineTest {

    @Test
    public void testConstructorAndReset() throws Throwable {
        Vector3D p1 = new Vector3D(0.0, 0.0, 0.0);
        Vector3D p2 = new Vector3D(1.0, 0.0, 0.0);
        Line line = new Line(p1, p2);

        assertEquals(0.0, line.getOrigin().getX(), 1.0e-10);
        assertEquals(0.0, line.getOrigin().getY(), 1.0e-10);
        assertEquals(0.0, line.getOrigin().getZ(), 1.0e-10);
        assertEquals(1.0, line.getDirection().getX(), 1.0e-10);
        assertEquals(0.0, line.getDirection().getY(), 1.0e-10);
        assertEquals(0.0, line.getDirection().getZ(), 1.0e-10);

        // Test reset with valid points
        Vector3D p3 = new Vector3D(0.0, 1.0, 0.0);
        Vector3D p4 = new Vector3D(0.0, 2.0, 0.0);
        line.reset(p3, p4);
        assertEquals(0.0, line.getOrigin().getX(), 1.0e-10);
        assertEquals(0.0, line.getOrigin().getY(), 1.0e-10);
        assertEquals(0.0, line.getOrigin().getZ(), 1.0e-10);
        assertEquals(0.0, line.getDirection().getX(), 1.0e-10);
        assertEquals(1.0, line.getDirection().getY(), 1.0e-10);
        assertEquals(0.0, line.getDirection().getZ(), 1.0e-10);
    }

    @Test
    public void testResetZeroNorm() throws Throwable {
        Vector3D p1 = new Vector3D(1.0, 2.0, 3.0);
        boolean exceptionThrown = false;
        try {
            Line line = new Line(p1, p1);
        } catch (MathIllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        Line line2 = new Line(new Vector3D(0, 0, 0), new Vector3D(1, 1, 1));
        boolean resetException = false;
        try {
            line2.reset(p1, p1);
        } catch (MathIllegalArgumentException e) {
            resetException = true;
        }
        assertTrue(resetException);
    }

    @Test
    public void testCopyConstructor() throws Throwable {
        Vector3D p1 = new Vector3D(1.0, 2, 3);
        Vector3D p2 = new Vector3D(4.0, 5, 6);
        Line line = new Line(p1, p2);
        Line copy = new Line(line);

        assertEquals(line.getOrigin().getX(), copy.getOrigin().getX(), 1.0e-10);
        assertEquals(line.getOrigin().getY(), copy.getOrigin().getY(), 1.0e-10);
        assertEquals(line.getOrigin().getZ(), copy.getOrigin().getZ(), 1.0e-10);
        assertEquals(line.getDirection().getX(), copy.getDirection().getX(), 1.0e-10);
        assertEquals(line.getDirection().getY(), copy.getDirection().getY(), 1.0e-10);
        assertEquals(line.getDirection().getZ(), copy.getDirection().getZ(), 1.0e-10);
    }

    @Test
    public void testRevert() throws Throwable {
        Vector3D p1 = new Vector3D(0.0, 0.0, 0.0);
        Vector3D p2 = new Vector3D(1.0, 0.0, 0.0);
        Line line = new Line(p1, p2);
        Line reverted = line.revert();

        assertEquals(-1.0, reverted.getDirection().getX(), 1.0e-10);
        assertEquals(0.0, reverted.getDirection().getY(), 1.0e-10);
        assertEquals(0.0, reverted.getDirection().getZ(), 1.0e-10);
    }

    @Test
    public void testGetAbscissaAndPointAt() throws Throwable {
        Vector3D p1 = new Vector3D(1.0, 1.0, 1.0);
        Vector3D p2 = new Vector3D(2.0, 1.0, 1.0);
        Line line = new Line(p1, p2);

        Vector3D testPoint = new Vector3D(3.0, 1.0, 1.0);
        double abscissa = line.getAbscissa(testPoint);
        assertEquals(3.0, abscissa, 1.0e-10);

        Vector3D pointAtAbscissa = line.pointAt(3.0);
        assertEquals(3.0, pointAtAbscissa.getX(), 1.0e-10);
        assertEquals(1.0, pointAtAbscissa.getY(), 1.0e-10);
        assertEquals(1.0, pointAtAbscissa.getZ(), 1.0e-10);
    }

    @Test
    public void testToSubSpaceAndToSpace() throws Throwable {
        Vector3D p1 = new Vector3D(0.0, 0.0, 0.0);
        Vector3D p2 = new Vector3D(0.0, 0.0, 1.0);
        Line line = new Line(p1, p2);

        Vector3D spacePoint = new Vector3D(0.0, 0.0, 5.0);
        Vector1D subSpacePoint = line.toSubSpace(spacePoint);
        assertEquals(5.0, subSpacePoint.getX(), 1.0e-10);

        Vector3D backToSpace = line.toSpace(subSpacePoint);
        assertEquals(spacePoint.getX(), backToSpace.getX(), 1.0e-10);
        assertEquals(spacePoint.getY(), backToSpace.getY(), 1.0e-10);
        assertEquals(spacePoint.getZ(), backToSpace.getZ(), 1.0e-10);
    }

    @Test
    public void testIsSimilarTo() throws Throwable {
        Vector3D p1 = new Vector3D(0.0, 0.0, 0.0);
        Vector3D p2 = new Vector3D(1.0, 0.0, 0.0);
        Line line1 = new Line(p1, p2);

        Vector3D p3 = new Vector3D(2.0, 0.0, 0.0);
        Vector3D p4 = new Vector3D(5.0, 0.0, 0.0);
        Line line2 = new Line(p3, p4);

        Vector3D p5 = new Vector3D(0.0, 1.0, 0.0);
        Vector3D p6 = new Vector3D(1.0, 1.0, 0.0);
        Line line3 = new Line(p5, p6);

        assertTrue(line1.isSimilarTo(line2));
        assertFalse(line1.isSimilarTo(line3));

        // Test with reversed direction similar line
        Line line4 = new Line(p2, p1);
        assertTrue(line1.isSimilarTo(line4));
    }

    @Test
    public void testContainsAndDistancePoint() throws Throwable {
        Vector3D p1 = new Vector3D(0.0, 0.0, 0.0);
        Vector3D p2 = new Vector3D(0.0, 1.0, 0.0);
        Line line = new Line(p1, p2);

        Vector3D onLine = new Vector3D(0.0, 5.0, 0.0);
        Vector3D offLine = new Vector3D(1.0, 5.0, 0.0);

        assertTrue(line.contains(onLine));
        assertFalse(line.contains(offLine));

        assertEquals(0.0, line.distance(onLine), 1.0e-10);
        assertEquals(1.0, line.distance(offLine), 1.0e-10);
    }

    @Test
    public void testDistanceLine() throws Throwable {
        // Parallel lines
        Line line1 = new Line(new Vector3D(0, 0, 0), new Vector3D(0, 1, 0));
        Line line2 = new Line(new Vector3D(2, 0, 0), new Vector3D(2, 1, 0));
        assertEquals(2.0, line1.distance(line2), 1.0e-10);

        // Intersecting lines (distance should be 0)
        Line line3 = new Line(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0));
        Line line4 = new Line(new Vector3D(0, 0, 0), new Vector3D(0, 1, 0));
        assertEquals(0.0, line3.distance(line4), 1.0e-10);

        // Skew lines
        Line line5 = new Line(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0));
        Line line6 = new Line(new Vector3D(0, 1, 1), new Vector3D(0, 1, 2));
        assertEquals(1.0, line5.distance(line6), 1.0e-10);
    }

    @Test
    public void testClosestPoint() throws Throwable {
        // Parallel lines
        Line line1 = new Line(new Vector3D(0, 0, 0), new Vector3D(0, 1, 0));
        Line line2 = new Line(new Vector3D(2, 0, 0), new Vector3D(2, 1, 0));
        Vector3D closest = line1.closestPoint(line2);
        assertEquals(0.0, closest.getX(), 1.0e-10);
        assertEquals(0.0, closest.getY(), 1.0e-10);
        assertEquals(0.0, closest.getZ(), 1.0e-10);

        // Intersecting / Skew lines
        Line line3 = new Line(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0));
        Line line4 = new Line(new Vector3D(0, 1, 1), new Vector3D(0, 1, 2));
        Vector3D closestSkew = line3.closestPoint(line4);
        assertEquals(0.0, closestSkew.getX(), 1.0e-10);
        assertEquals(0.0, closestSkew.getY(), 1.0e-10);
        assertEquals(0.0, closestSkew.getZ(), 1.0e-10);
    }

    @Test
    public void testIntersection() throws Throwable {
        Line line1 = new Line(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0));
        Line line2 = new Line(new Vector3D(0, 0, 0), new Vector3D(0, 1, 0));
        Vector3D intersection = line1.intersection(line2);
        assertNotNull(intersection);
        assertEquals(0.0, intersection.getX(), 1.0e-10);
        assertEquals(0.0, intersection.getY(), 1.0e-10);
        assertEquals(0.0, intersection.getZ(), 1.0e-10);

        // Parallel lines should have null intersection
        Line line3 = new Line(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0));
        Line line4 = new Line(new Vector3D(0, 1, 0), new Vector3D(1, 1, 0));
        assertNull(line3.intersection(line4));

        // Skew lines should have null intersection
        Line line5 = new Line(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0));
        Line line6 = new Line(new Vector3D(0, 1, 1), new Vector3D(0, 1, 2));
        assertNull(line5.intersection(line6));
    }

    @Test
    public void testWholeLine() throws Throwable {
        Line line = new Line(new Vector3D(0, 0, 0), new Vector3D(1, 1, 1));
        SubLine subLine = line.wholeLine();
        assertNotNull(subLine);
    }
}