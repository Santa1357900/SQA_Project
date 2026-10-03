package org.jfree.chart.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import org.junit.Test;

public class ShapeUtilitiesTest {

    @Test
    public void testCloneShape() throws Throwable {
        assertNull(ShapeUtilities.clone(null));

        Line2D line = new Line2D.Double(1.0, 2.0, 3.0, 4.0);
        Shape clonedLine = ShapeUtilities.clone(line);
        assertNotNull(clonedLine);
        assertTrue(clonedLine instanceof Line2D);

        Rectangle2D rect = new Rectangle2D.Double(0.0, 0.0, 10.0, 10.0);
        Shape clonedRect = ShapeUtilities.clone(rect);
        assertNotNull(clonedRect);

        Ellipse2D ellipse = new Ellipse2D.Double(0.0, 0.0, 5.0, 5.0);
        Shape clonedEllipse = ShapeUtilities.clone(ellipse);
        assertNotNull(clonedEllipse);

        Arc2D arc = new Arc2D.Double(0.0, 0.0, 10.0, 10.0, 0.0, 90.0, Arc2D.OPEN);
        Shape clonedArc = ShapeUtilities.clone(arc);
        assertNotNull(clonedArc);

        GeneralPath path = new GeneralPath();
        path.moveTo(0.0f, 0.0f);
        path.lineTo(1.0f, 1.0f);
        Shape clonedPath = ShapeUtilities.clone(path);
        assertNotNull(clonedPath);
    }

    @Test
    public void testEqualShape() throws Throwable {
        assertTrue(ShapeUtilities.equal((Shape) null, (Shape) null));

        Line2D l1 = new Line2D.Double(1.0, 2.0, 3.0, 4.0);
        Line2D l2 = new Line2D.Double(1.0, 2.0, 3.0, 4.0);
        Line2D l3 = new Line2D.Double(0.0, 0.0, 0.0, 0.0);
        assertTrue(ShapeUtilities.equal(l1, l2));
        assertFalse(ShapeUtilities.equal(l1, l3));

        Ellipse2D e1 = new Ellipse2D.Double(1.0, 2.0, 3.0, 4.0);
        Ellipse2D e2 = new Ellipse2D.Double(1.0, 2.0, 3.0, 4.0);
        Ellipse2D e3 = new Ellipse2D.Double(0.0, 0.0, 0.0, 0.0);
        assertTrue(ShapeUtilities.equal(e1, e2));
        assertFalse(ShapeUtilities.equal(e1, e3));

        Arc2D a1 = new Arc2D.Double(1.0, 2.0, 3.0, 4.0, 0.0, 90.0, Arc2D.OPEN);
        Arc2D a2 = new Arc2D.Double(1.0, 2.0, 3.0, 4.0, 0.0, 90.0, Arc2D.OPEN);
        Arc2D a3 = new Arc2D.Double(1.0, 2.0, 3.0, 4.0, 10.0, 90.0, Arc2D.OPEN);
        assertTrue(ShapeUtilities.equal(a1, a2));
        assertFalse(ShapeUtilities.equal(a1, a3));

        Polygon p1 = new Polygon(new int[] {0, 1, 2}, new int[] {0, 2, 0}, 3);
        Polygon p2 = new Polygon(new int[] {0, 1, 2}, new int[] {0, 2, 0}, 3);
        Polygon p3 = new Polygon(new int[] {0, 1, 3}, new int[] {0, 2, 0}, 3);
        assertTrue(ShapeUtilities.equal(p1, p2));
        assertFalse(ShapeUtilities.equal(p1, p3));

        GeneralPath gp1 = new GeneralPath();
        gp1.moveTo(0.0f, 0.0f);
        gp1.lineTo(1.0f, 1.0f);
        GeneralPath gp2 = new GeneralPath();
        gp2.moveTo(0.0f, 0.0f);
        gp2.lineTo(1.0f, 1.0f);
        GeneralPath gp3 = new GeneralPath();
        gp3.moveTo(0.0f, 0.0f);
        gp3.lineTo(2.0f, 2.0f);
        assertTrue(ShapeUtilities.equal(gp1, gp2));
        assertFalse(ShapeUtilities.equal(gp1, gp3));

        Rectangle2D r1 = new Rectangle2D.Double(1.0, 1.0, 5.0, 5.0);
        Rectangle2D r2 = new Rectangle2D.Double(1.0, 1.0, 5.0, 5.0);
        Rectangle2D r3 = new Rectangle2D.Double(2.0, 2.0, 5.0, 5.0);
        assertTrue(ShapeUtilities.equal(r1, r2));
        assertFalse(ShapeUtilities.equal(r1, r3));
    }

    @Test
    public void testEqualLine2D() throws Throwable {
        Line2D l1 = new Line2D.Double(1.0, 2.0, 3.0, 4.0);
        Line2D l2 = new Line2D.Double(1.0, 2.0, 3.0, 4.0);
        Line2D l3 = new Line2D.Double(0.0, 2.0, 3.0, 4.0);
        Line2D l4 = new Line2D.Double(1.0, 2.0, 0.0, 4.0);

        assertTrue(ShapeUtilities.equal((Line2D) null, (Line2D) null));
        assertFalse(ShapeUtilities.equal(l1, null));
        assertFalse(ShapeUtilities.equal(null, l1));
        assertTrue(ShapeUtilities.equal(l1, l2));
        assertFalse(ShapeUtilities.equal(l1, l3));
        assertFalse(ShapeUtilities.equal(l1, l4));
    }

    @Test
    public void testEqualEllipse2D() throws Throwable {
        Ellipse2D e1 = new Ellipse2D.Double(1.0, 2.0, 3.0, 4.0);
        Ellipse2D e2 = new Ellipse2D.Double(1.0, 2.0, 3.0, 4.0);
        Ellipse2D e3 = new Ellipse2D.Double(0.0, 2.0, 3.0, 4.0);

        assertTrue(ShapeUtilities.equal((Ellipse2D) null, (Ellipse2D) null));
        assertFalse(ShapeUtilities.equal(e1, null));
        assertFalse(ShapeUtilities.equal(null, e1));
        assertTrue(ShapeUtilities.equal(e1, e2));
        assertFalse(ShapeUtilities.equal(e1, e3));
    }

    @Test
    public void testEqualArc2D() throws Throwable {
        Arc2D a1 = new Arc2D.Double(1.0, 2.0, 3.0, 4.0, 0.0, 90.0, Arc2D.OPEN);
        Arc2D a2 = new Arc2D.Double(1.0, 2.0, 3.0, 4.0, 0.0, 90.0, Arc2D.OPEN);
        Arc2D a3 = new Arc2D.Double(0.0, 2.0, 3.0, 4.0, 0.0, 90.0, Arc2D.OPEN);
        Arc2D a4 = new Arc2D.Double(1.0, 2.0, 3.0, 4.0, 10.0, 90.0, Arc2D.OPEN);
        Arc2D a5 = new Arc2D.Double(1.0, 2.0, 3.0, 4.0, 0.0, 45.0, Arc2D.OPEN);
        Arc2D a6 = new Arc2D.Double(1.0, 2.0, 3.0, 4.0, 0.0, 90.0, Arc2D.CHORD);

        assertTrue(ShapeUtilities.equal((Arc2D) null, (Arc2D) null));
        assertFalse(ShapeUtilities.equal(a1, null));
        assertFalse(ShapeUtilities.equal(null, a1));
        assertTrue(ShapeUtilities.equal(a1, a2));
        assertFalse(ShapeUtilities.equal(a1, a3));
        assertFalse(ShapeUtilities.equal(a1, a4));
        assertFalse(ShapeUtilities.equal(a1, a5));
        assertFalse(ShapeUtilities.equal(a1, a6));
    }

    @Test
    public void testEqualPolygon() throws Throwable {
        Polygon p1 = new Polygon(new int[] {0, 1, 2}, new int[] {0, 2, 0}, 3);
        Polygon p2 = new Polygon(new int[] {0, 1, 2}, new int[] {0, 2, 0}, 3);
        Polygon p3 = new Polygon(new int[] {0, 1}, new int[] {0, 2}, 2);
        Polygon p4 = new Polygon(new int[] {0, 1, 5}, new int[] {0, 2, 0}, 3);
        Polygon p5 = new Polygon(new int[] {0, 1, 2}, new int[] {0, 5, 0}, 3);

        assertTrue(ShapeUtilities.equal((Polygon) null, (Polygon) null));
        assertFalse(ShapeUtilities.equal(p1, null));
        assertFalse(ShapeUtilities.equal(null, p1));
        assertTrue(ShapeUtilities.equal(p1, p2));
        assertFalse(ShapeUtilities.equal(p1, p3));
        assertFalse(ShapeUtilities.equal(p1, p4));
        assertFalse(ShapeUtilities.equal(p1, p5));
    }

    @Test
    public void testEqualGeneralPath() throws Throwable {
        GeneralPath gp1 = new GeneralPath();
        gp1.moveTo(0.0f, 0.0f);
        gp1.lineTo(1.0f, 1.0f);

        GeneralPath gp2 = new GeneralPath();
        gp2.moveTo(0.0f, 0.0f);
        gp2.lineTo(1.0f, 1.0f);

        GeneralPath gp3 = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
        gp3.moveTo(0.0f, 0.0f);
        gp3.lineTo(1.0f, 1.0f);

        GeneralPath gp4 = new GeneralPath();
        gp4.moveTo(0.0f, 0.0f);
        gp4.lineTo(2.0f, 2.0f);

        assertTrue(ShapeUtilities.equal((GeneralPath) null, (GeneralPath) null));
        assertFalse(ShapeUtilities.equal(gp1, null));
        assertFalse(ShapeUtilities.equal(null, gp1));
        assertTrue(ShapeUtilities.equal(gp1, gp2));
        assertFalse(ShapeUtilities.equal(gp1, gp3));
        assertFalse(ShapeUtilities.equal(gp1, gp4));
    }

    @Test
    public void testCreateTranslatedShape() throws Throwable {
        Rectangle2D rect = new Rectangle2D.Double(0.0, 0.0, 10.0, 10.0);
        Shape translated = ShapeUtilities.createTranslatedShape(rect, 5.0, 5.0);
        assertNotNull(translated);

        try {
            ShapeUtilities.createTranslatedShape(null, 1.0, 1.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        Shape translatedAnchor = ShapeUtilities.createTranslatedShape(rect, RectangleAnchor.CENTER, 10.0, 10.0);
        assertNotNull(translatedAnchor);

        try {
            ShapeUtilities.createTranslatedShape(null, RectangleAnchor.CENTER, 1.0, 1.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            ShapeUtilities.createTranslatedShape(rect, null, 1.0, 1.0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testRotateShape() throws Throwable {
        assertNull(ShapeUtilities.rotateShape(null, 1.0, 0.0f, 0.0f));

        Rectangle2D rect = new Rectangle2D.Double(0.0, 0.0, 10.0, 10.0);
        Shape rotated = ShapeUtilities.rotateShape(rect, Math.PI / 2.0, 5.0f, 5.0f);
        assertNotNull(rotated);
    }

    @Test
    public void testShapeCreationMethods() throws Throwable {
        assertNotNull(ShapeUtilities.createDiagonalCross(5.0f, 1.0f));
        assertNotNull(ShapeUtilities.createRegularCross(5.0f, 1.0f));
        assertNotNull(ShapeUtilities.createDiamond(5.0f));
        assertNotNull(ShapeUtilities.createUpTriangle(5.0f));
        assertNotNull(ShapeUtilities.createDownTriangle(5.0f));
    }

    @Test
    public void testCreateLineRegion() throws Throwable {
        Line2D line1 = new Line2D.Double(0.0, 0.0, 10.0, 10.0);
        Shape region1 = ShapeUtilities.createLineRegion(line1, 2.0f);
        assertNotNull(region1);

        Line2D line2 = new Line2D.Double(5.0, 0.0, 5.0, 10.0);
        Shape region2 = ShapeUtilities.createLineRegion(line2, 2.0f);
        assertNotNull(region2);
    }

    @Test
    public void testGetPointInRectangle() throws Throwable {
        Rectangle2D area = new Rectangle2D.Double(10.0, 10.0, 20.0, 20.0);
        Point2D pInside = ShapeUtilities.getPointInRectangle(15.0, 15.0, area);
        assertEquals(15.0, pInside.getX(), 0.001);
        assertEquals(15.0, pInside.getY(), 0.001);

        Point2D pOutside = ShapeUtilities.getPointInRectangle(0.0, 40.0, area);
        assertEquals(10.0, pOutside.getX(), 0.001);
        assertEquals(30.0, pOutside.getY(), 0.001);

        try {
            ShapeUtilities.getPointInRectangle(0.0, 0.0, null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testContainsAndIntersects() throws Throwable {
        Rectangle2D r1 = new Rectangle2D.Double(0.0, 0.0, 10.0, 10.0);
        Rectangle2D r2 = new Rectangle2D.Double(2.0, 2.0, 5.0, 5.0);
        Rectangle2D r3 = new Rectangle2D.Double(8.0, 8.0, 5.0, 5.0);
        Rectangle2D r4 = new Rectangle2D.Double(20.0, 20.0, 5.0, 5.0);

        assertTrue(ShapeUtilities.contains(r1, r2));
        assertFalse(ShapeUtilities.contains(r1, r3));
        assertFalse(ShapeUtilities.contains(r1, r4));

        assertTrue(ShapeUtilities.intersects(r1, r2));
        assertTrue(ShapeUtilities.intersects(r1, r3));
        assertFalse(ShapeUtilities.intersects(r1, r4));
    }
}