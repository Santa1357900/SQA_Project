package org.apache.commons.math3.geometry.euclidean.threed;

import java.util.List;

import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.geometry.euclidean.oned.IntervalsSet;
import org.junit.Assert;
import org.junit.Test;

public class SubLineTest {

    @Test
    public void testConstructorsAndSegments() throws Throwable {
        Vector3D p1 = new Vector3D(0, 0, 0);
        Vector3D p2 = new Vector3D(1, 0, 0);

        SubLine subLine1 = new SubLine(p1, p2);
        List<Segment> segments1 = subLine1.getSegments();
        Assert.assertEquals(1, segments1.size());
        Assert.assertEquals(p1, segments1.get(0).getStart());
        Assert.assertEquals(p2, segments1.get(0).getEnd());

        Segment segment = segments1.get(0);
        SubLine subLine2 = new SubLine(segment);
        List<Segment> segments2 = subLine2.getSegments();
        Assert.assertEquals(1, segments2.size());
        Assert.assertEquals(p1, segments2.get(0).getStart());
        Assert.assertEquals(p2, segments2.get(0).getEnd());

        Line line = new Line(p1, p2);
        IntervalsSet intervalSet = new IntervalsSet(0.0, 1.0);
        SubLine subLine3 = new SubLine(line, intervalSet);
        List<Segment> segments3 = subLine3.getSegments();
        Assert.assertEquals(1, segments3.size());
    }

    @Test(expected = MathIllegalArgumentException.class)
    public void testEqualPointsConstructor() throws Throwable {
        Vector3D p1 = new Vector3D(1, 2, 3);
        new SubLine(p1, p1);
    }

    @Test
    public void testIntersectionIncludeEndPoints() throws Throwable {
        SubLine subLine1 = new SubLine(new Vector3D(0, 0, 0), new Vector3D(2, 0, 0));
        SubLine subLine2 = new SubLine(new Vector3D(1, -1, 0), new Vector3D(1, 1, 0));

        Vector3D intersection = subLine1.intersection(subLine2, true);
        Assert.assertNotNull(intersection);
        Assert.assertEquals(1.0, intersection.getX(), 1.0e-10);
        Assert.assertEquals(0.0, intersection.getY(), 1.0e-10);
        Assert.assertEquals(0.0, intersection.getZ(), 1.0e-10);
    }

    @Test
    public void testIntersectionExcludeEndPoints() throws Throwable {
        SubLine subLine1 = new SubLine(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0));
        SubLine subLine2 = new SubLine(new Vector3D(1, -1, 0), new Vector3D(1, 1, 0));

        Vector3D intersectionInclude = subLine1.intersection(subLine2, true);
        Assert.assertNotNull(intersectionInclude);

        Vector3D intersectionExclude = subLine1.intersection(subLine2, false);
        Assert.assertNull(intersectionExclude);
    }

    @Test
    public void testNoIntersection() throws Throwable {
        SubLine subLine1 = new SubLine(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0));
        SubLine subLine2 = new SubLine(new Vector3D(0, 1, 0), new Vector3D(1, 1, 0));

        Vector3D intersection = subLine1.intersection(subLine2, true);
        Assert.assertNull(intersection);
    }

    @Test
    public void testIntersectionOutsideRange() throws Throwable {
        SubLine subLine1 = new SubLine(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0));
        SubLine subLine2 = new SubLine(new Vector3D(2, -1, 0), new Vector3D(2, 1, 0));

        Vector3D intersection = subLine1.intersection(subLine2, true);
        Assert.assertNull(intersection);
    }
}