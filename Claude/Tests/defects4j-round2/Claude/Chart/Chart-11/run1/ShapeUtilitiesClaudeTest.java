package org.jfree.chart.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public class ShapeUtilitiesClaudeTest {

    // clone(): null shape -> returns null (per Javadoc)
    @Test
    public void testClone_nullShape_returnsNull() throws Throwable {
        assertNull(ShapeUtilities.clone((Shape) null));
    }

    // clone(): Line2D is Cloneable -> returns equal but different instance
    @Test
    public void testClone_line2D_returnsEqualButDifferentInstance() throws Throwable {
        Line2D original = new Line2D.Double(1.0, 2.0, 3.0, 4.0);
        Shape cloned = ShapeUtilities.clone(original);
        assertNotSame(original, cloned);
        assertTrue(ShapeUtilities.equal((Line2D) cloned, original));
    }

    // clone(): Rectangle2D (RectangularShape) -> returns equal but different instance
    @Test
    public void testClone_rectangle2D_returnsEqualButDifferentInstance() throws Throwable {
        Rectangle2D original = new Rectangle2D.Double(1.0, 2.0, 5.0, 6.0);
        Shape cloned = ShapeUtilities.clone(original);
        assertNotSame(original, cloned);
        assertEquals(original, cloned);
    }

    // clone(): GeneralPath -> returns different instance with same bounds
    @Test
    public void testClone_generalPath_returnsDifferentInstanceSameBounds() throws Throwable {
        GeneralPath original = new GeneralPath();
        original.moveTo(0.0f, 0.0f);
        original.lineTo(5.0f, 5.0f);
        Shape cloned = ShapeUtilities.clone(original);
        assertNotSame(original, cloned);
        assertEquals(original.getBounds2D(), cloned.getBounds2D());
    }

    // equal(Shape,Shape): both null -> true (per Javadoc)
    @Test
    public void testEqualShapeShape_bothNull_true() throws Throwable {
        assertTrue(ShapeUtilities.equal((Shape) null, (Shape) null));
    }

    // equal(Shape,Shape): one null -> false
    @Test
    public void testEqualShapeShape_oneNull_false() throws Throwable {
        Rectangle2D r = new Rectangle2D.Double(0, 0, 1, 1);
        assertFalse(ShapeUtilities.equal((Shape) r, (Shape) null));
    }

    // equal(Shape,Shape): dispatch to Line2D branch
    @Test
    public void testEqualShapeShape_line2DDispatch() throws Throwable {
        Line2D l1 = new Line2D.Double(0, 0, 5, 5);
        Line2D l2 = new Line2D.Double(0, 0, 5, 5);
        Line2D l3 = new Line2D.Double(0, 0, 9, 9);
        assertTrue(ShapeUtilities.equal((Shape) l1, (Shape) l2));
        assertFalse(ShapeUtilities.equal((Shape) l1, (Shape) l3));
    }

    // equal(Shape,Shape): dispatch to Ellipse2D branch
    @Test
    public void testEqualShapeShape_ellipse2DDispatch() throws Throwable {
        Ellipse2D e1 = new Ellipse2D.Double(0, 0, 10, 10);
        Ellipse2D e2 = new Ellipse2D.Double(0, 0, 10, 10);
        Ellipse2D e3 = new Ellipse2D.Double(0, 0, 20, 20);
        assertTrue(ShapeUtilities.equal((Shape) e1, (Shape) e2));
        assertFalse(ShapeUtilities.equal((Shape) e1, (Shape) e3));
    }

    // equal(Shape,Shape): dispatch to Arc2D branch
    @Test
    public void testEqualShapeShape_arc2DDispatch() throws Throwable {
        Arc2D a1 = new Arc2D.Double(0, 0, 10, 10, 0, 90, Arc2D.OPEN);
        Arc2D a2 = new Arc2D.Double(0, 0, 10, 10, 0, 90, Arc2D.OPEN);
        Arc2D a3 = new Arc2D.Double(0, 0, 10, 10, 0, 45, Arc2D.OPEN);
        assertTrue(ShapeUtilities.equal((Shape) a1, (Shape) a2));
        assertFalse(ShapeUtilities.equal((Shape) a1, (Shape) a3));
    }

    // equal(Shape,Shape): dispatch to Polygon branch
    @Test
    public void testEqualShapeShape_polygonDispatch() throws Throwable {
        Polygon p1 = new Polygon(new int[] {0, 1, 2}, new int[] {0, 1, 0}, 3);
        Polygon p2 = new Polygon(new int[] {0, 1, 2}, new int[] {0, 1, 0}, 3);
        Polygon p3 = new Polygon(new int[] {0, 1, 3}, new int[] {0, 1, 0}, 3);
        assertTrue(ShapeUtilities.equal((Shape) p1, (Shape) p2));
        assertFalse(ShapeUtilities.equal((Shape) p1, (Shape) p3));
    }

    // equal(Shape,Shape): else branch (Rectangle2D) uses ObjectUtilities.equal
    @Test
    public void testEqualShapeShape_rectangle2DElseBranch() throws Throwable {
        Rectangle2D r1 = new Rectangle2D.Double(1, 1, 5, 5);
        Rectangle2D r2 = new Rectangle2D.Double(1, 1, 5, 5);
        Rectangle2D r3 = new Rectangle2D.Double(2, 2, 5, 5);
        assertTrue(ShapeUtilities.equal((Shape) r1, (Shape) r2));
        assertFalse(ShapeUtilities.equal((Shape) r1, (Shape) r3));
    }

    // equal(Line2D,Line2D): both null -> true
    @Test
    public void testEqualLine2D_bothNull_true() throws Throwable {
        assertTrue(ShapeUtilities.equal((Line2D) null, (Line2D) null));
    }

    // equal(Line2D,Line2D): first null -> false
    @Test
    public void testEqualLine2D_firstNull_false() throws Throwable {
        Line2D l = new Line2D.Double(0, 0, 1, 1);
        assertFalse(ShapeUtilities.equal((Line2D) null, l));
    }

    // equal(Line2D,Line2D): second null -> false
    @Test
    public void testEqualLine2D_secondNull_false() throws Throwable {
        Line2D l = new Line2D.Double(0, 0, 1, 1);
        assertFalse(ShapeUtilities.equal(l, (Line2D) null));
    }

    // equal(Line2D,Line2D): different P1 -> false
    @Test
    public void testEqualLine2D_differentP1_false() throws Throwable {
        Line2D l1 = new Line2D.Double(0, 0, 5, 5);
        Line2D l2 = new Line2D.Double(1, 1, 5, 5);
        assertFalse(ShapeUtilities.equal(l1, l2));
    }

    // equal(Line2D,Line2D): different P2 -> false, equal lines -> true
    @Test
    public void testEqualLine2D_differentP2AndEqualLines() throws Throwable {
        Line2D l1 = new Line2D.Double(0, 0, 5, 5);
        Line2D l2 = new Line2D.Double(0, 0, 9, 9);
        Line2D l3 = new Line2D.Double(0, 0, 5, 5);
        assertFalse(ShapeUtilities.equal(l1, l2));
        assertTrue(ShapeUtilities.equal(l1, l3));
    }

    // equal(Ellipse2D,Ellipse2D): null combos
    @Test
    public void testEqualEllipse2D_nullCombos() throws Throwable {
        Ellipse2D e = new Ellipse2D.Double(0, 0, 1, 1);
        assertTrue(ShapeUtilities.equal((Ellipse2D) null, (Ellipse2D) null));
        assertFalse(ShapeUtilities.equal((Ellipse2D) null, e));
        assertFalse(ShapeUtilities.equal(e, (Ellipse2D) null));
    }

    // equal(Ellipse2D,Ellipse2D): different frame -> false, same frame -> true
    @Test
    public void testEqualEllipse2D_frameComparison() throws Throwable {
        Ellipse2D e1 = new Ellipse2D.Double(0, 0, 10, 10);
        Ellipse2D e2 = new Ellipse2D.Double(0, 0, 20, 20);
        Ellipse2D e3 = new Ellipse2D.Double(0, 0, 10, 10);
        assertFalse(ShapeUtilities.equal(e1, e2));
        assertTrue(ShapeUtilities.equal(e1, e3));
    }

    // equal(Arc2D,Arc2D): null combos
    @Test
    public void testEqualArc2D_nullCombos() throws Throwable {
        Arc2D a = new Arc2D.Double(0, 0, 10, 10, 0, 90, Arc2D.OPEN);
        assertTrue(ShapeUtilities.equal((Arc2D) null, (Arc2D) null));
        assertFalse(ShapeUtilities.equal((Arc2D) null, a));
        assertFalse(ShapeUtilities.equal(a, (Arc2D) null));
    }

    // equal(Arc2D,Arc2D): different angleStart -> false
    @Test
    public void testEqualArc2D_differentAngleStart_false() throws Throwable {
        Arc2D a1 = new Arc2D.Double(0, 0, 10, 10, 0, 90, Arc2D.OPEN);
        Arc2D a2 = new Arc2D.Double(0, 0, 10, 10, 10, 90, Arc2D.OPEN);
        assertFalse(ShapeUtilities.equal(a1, a2));
    }

    // equal(Arc2D,Arc2D): different arcType -> false
    @Test
    public void testEqualArc2D_differentArcType_false() throws Throwable {
        Arc2D a1 = new Arc2D.Double(0, 0, 10, 10, 0, 90, Arc2D.OPEN);
        Arc2D a2 = new Arc2D.Double(0, 0, 10, 10, 0, 90, Arc2D.CHORD);
        assertFalse(ShapeUtilities.equal(a1, a2));
    }

    // equal(Arc2D,Arc2D): fully equal arcs -> true
    @Test
    public void testEqualArc2D_equalArcs_true() throws Throwable {
        Arc2D a1 = new Arc2D.Double(0, 0, 10, 10, 0, 90, Arc2D.OPEN);
        Arc2D a2 = new Arc2D.Double(0, 0, 10, 10, 0, 90, Arc2D.OPEN);
        assertTrue(ShapeUtilities.equal(a1, a2));
    }

    // equal(Polygon,Polygon): null combos
    @Test
    public void testEqualPolygon_nullCombos() throws Throwable {
        Polygon p = new Polygon(new int[] {0, 1}, new int[] {0, 1}, 2);
        assertTrue(ShapeUtilities.equal((Polygon) null, (Polygon) null));
        assertFalse(ShapeUtilities.equal((Polygon) null, p));
        assertFalse(ShapeUtilities.equal(p, (Polygon) null));
    }

    // equal(Polygon,Polygon): different npoints -> false
    @Test
    public void testEqualPolygon_differentNpoints_false() throws Throwable {
        Polygon p1 = new Polygon(new int[] {0, 1, 2}, new int[] {0, 1, 0}, 3);
        Polygon p2 = new Polygon(new int[] {0, 1}, new int[] {0, 1}, 2);
        assertFalse(ShapeUtilities.equal(p1, p2));
    }

    // equal(Polygon,Polygon): different ypoints -> false
    @Test
    public void testEqualPolygon_differentYPoints_false() throws Throwable {
        Polygon p1 = new Polygon(new int[] {0, 1, 2}, new int[] {0, 1, 0}, 3);
        Polygon p2 = new Polygon(new int[] {0, 1, 2}, new int[] {0, 9, 0}, 3);
        assertFalse(ShapeUtilities.equal(p1, p2));
    }

    // equal(GeneralPath,GeneralPath): null combos
    @Test
    public void testEqualGeneralPath_nullCombos() throws Throwable {
        GeneralPath g = new GeneralPath();
        g.moveTo(0.0f, 0.0f);
        assertTrue(ShapeUtilities.equal((GeneralPath) null, (GeneralPath) null));
        assertFalse(ShapeUtilities.equal((GeneralPath) null, g));
        assertFalse(ShapeUtilities.equal(g, (GeneralPath) null));
    }

    // equal(GeneralPath,GeneralPath): different winding rule -> false
    @Test
    public void testEqualGeneralPath_differentWindingRule_false() throws Throwable {
        GeneralPath p1 = new GeneralPath(GeneralPath.WIND_NON_ZERO);
        GeneralPath p2 = new GeneralPath(PathIterator.WIND_EVEN_ODD);
        assertFalse(ShapeUtilities.equal(p1, p2));
    }

    // equal(GeneralPath,GeneralPath): identical paths -> true
    @Test
    public void testEqualGeneralPath_samePaths_true() throws Throwable {
        GeneralPath p1 = new GeneralPath();
        p1.moveTo(0.0f, 0.0f);
        p1.lineTo(10.0f, 10.0f);
        GeneralPath p2 = new GeneralPath();
        p2.moveTo(0.0f, 0.0f);
        p2.lineTo(10.0f, 10.0f);
        assertTrue(ShapeUtilities.equal(p1, p2));
    }

    // BUG: equal(GeneralPath,GeneralPath) compares p1 against itself instead
    // of against p2, so genuinely different paths (same winding rule) must
    // still be reported as unequal per contract.
    @Test
    public void testEqualGeneralPath_differentPaths_returnsFalse() throws Throwable {
        GeneralPath p1 = new GeneralPath();
        p1.moveTo(0.0f, 0.0f);
        p1.lineTo(10.0f, 10.0f);
        GeneralPath p2 = new GeneralPath();
        p2.moveTo(5.0f, 5.0f);
        p2.lineTo(20.0f, 20.0f);
        assertFalse(ShapeUtilities.equal(p1, p2));
    }

    // createTranslatedShape(Shape,double,double): null shape throws
    @Test
    public void testCreateTranslatedShape_nullShape_throws() throws Throwable {
        try {
            ShapeUtilities.createTranslatedShape((Shape) null, 1.0, 1.0);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // createTranslatedShape(Shape,double,double): correct translation
    @Test
    public void testCreateTranslatedShape_translatesCorrectly() throws Throwable {
        Rectangle2D rect = new Rectangle2D.Double(1.0, 1.0, 5.0, 5.0);
        Shape translated = ShapeUtilities.createTranslatedShape(rect, 10.0, 20.0);
        Rectangle2D bounds = translated.getBounds2D();
        assertEquals(11.0, bounds.getX(), 1e-9);
        assertEquals(21.0, bounds.getY(), 1e-9);
        assertEquals(5.0, bounds.getWidth(), 1e-9);
    }

    // createTranslatedShape(Shape,RectangleAnchor,double,double): null shape throws
    @Test
    public void testCreateTranslatedShapeAnchor_nullShape_throws() throws Throwable {
        try {
            ShapeUtilities.createTranslatedShape((Shape) null, (RectangleAnchor) null, 1.0, 1.0);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // createTranslatedShape(Shape,RectangleAnchor,double,double): null anchor throws
    @Test
    public void testCreateTranslatedShapeAnchor_nullAnchor_throws() throws Throwable {
        Rectangle2D rect = new Rectangle2D.Double(0, 0, 5, 5);
        try {
            ShapeUtilities.createTranslatedShape(rect, (RectangleAnchor) null, 1.0, 1.0);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // rotateShape: null base returns null
    @Test
    public void testRotateShape_nullBase_returnsNull() throws Throwable {
        assertNull(ShapeUtilities.rotateShape(null, 0.0, 0.0f, 0.0f));
    }

    // rotateShape: rotates a point 90 degrees about the origin correctly
    @Test
    public void testRotateShape_rotatesPointCorrectly() throws Throwable {
        Rectangle2D base = new Rectangle2D.Double(1.0, 1.0, 0.0, 0.0);
        Shape rotated = ShapeUtilities.rotateShape(base, Math.PI / 2.0, 0.0f, 0.0f);
        Rectangle2D bounds = rotated.getBounds2D();
        assertEquals(-1.0, bounds.getX(), 1e-6);
        assertEquals(1.0, bounds.getY(), 1e-6);
    }

    // drawRotatedShape: transform is restored after drawing
    @Test
    public void testDrawRotatedShape_restoresTransform() throws Throwable {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        AffineTransform before = g2.getTransform();
        Shape shape = new Rectangle2D.Double(0, 0, 5, 5);
        ShapeUtilities.drawRotatedShape(g2, shape, Math.PI / 4.0, 2.0f, 2.0f);
        assertEquals(before, g2.getTransform());
    }

    // createDiagonalCross: verify bounding box
    @Test
    public void testCreateDiagonalCross_boundsCorrect() throws Throwable {
        Shape s = ShapeUtilities.createDiagonalCross(2.0f, 1.0f);
        Rectangle2D b = s.getBounds2D();
        assertEquals(-3.0, b.getX(), 1e-3);
        assertEquals(-3.0, b.getY(), 1e-3);
        assertEquals(6.0, b.getWidth(), 1e-3);
        assertEquals(6.0, b.getHeight(), 1e-3);
    }

    // createRegularCross: verify bounding box
    @Test
    public void testCreateRegularCross_boundsCorrect() throws Throwable {
        Shape s = ShapeUtilities.createRegularCross(2.0f, 1.0f);
        Rectangle2D b = s.getBounds2D();
        assertEquals(-2.0, b.getX(), 1e-3);
        assertEquals(-2.0, b.getY(), 1e-3);
        assertEquals(4.0, b.getWidth(), 1e-3);
        assertEquals(4.0, b.getHeight(), 1e-3);
    }

    // createDiamond: verify bounding box
    @Test
    public void testCreateDiamond_boundsCorrect() throws Throwable {
        Shape s = ShapeUtilities.createDiamond(2.0f);
        Rectangle2D b = s.getBounds2D();
        assertEquals(-2.0, b.getX(), 1e-3);
        assertEquals(-2.0, b.getY(), 1e-3);
        assertEquals(4.0, b.getWidth(), 1e-3);
        assertEquals(4.0, b.getHeight(), 1e-3);
    }

    // createUpTriangle: verify bounding box
    @Test
    public void testCreateUpTriangle_boundsCorrect() throws Throwable {
        Shape s = ShapeUtilities.createUpTriangle(2.0f);
        Rectangle2D b = s.getBounds2D();
        assertEquals(-2.0, b.getX(), 1e-3);
        assertEquals(-2.0, b.getY(), 1e-3);
        assertEquals(4.0, b.getWidth(), 1e-3);
        assertEquals(4.0, b.getHeight(), 1e-3);
    }

    // createDownTriangle: verify bounding box
    @Test
    public void testCreateDownTriangle_boundsCorrect() throws Throwable {
        Shape s = ShapeUtilities.createDownTriangle(2.0f);
        Rectangle2D b = s.getBounds2D();
        assertEquals(-2.0, b.getX(), 1e-3);
        assertEquals(-2.0, b.getY(), 1e-3);
        assertEquals(4.0, b.getWidth(), 1e-3);
        assertEquals(4.0, b.getHeight(), 1e-3);
    }

    // createLineRegion: non-vertical line branch
    @Test
    public void testCreateLineRegion_horizontalLine_boundsCorrect() throws Throwable {
        Line2D line = new Line2D.Double(0.0, 0.0, 10.0, 0.0);
        Shape region = ShapeUtilities.createLineRegion(line, 4.0f);
        Rectangle2D b = region.getBounds2D();
        assertEquals(0.0, b.getX(), 1e-3);
        assertEquals(-4.0, b.getY(), 1e-3);
        assertEquals(10.0, b.getWidth(), 1e-3);
        assertEquals(8.0, b.getHeight(), 1e-3);
    }

    // createLineRegion: vertical line special-case branch
    @Test
    public void testCreateLineRegion_verticalLine_boundsCorrect() throws Throwable {
        Line2D line = new Line2D.Double(0.0, 0.0, 0.0, 10.0);
        Shape region = ShapeUtilities.createLineRegion(line, 4.0f);
        Rectangle2D b = region.getBounds2D();
        assertEquals(-2.0, b.getX(), 1e-3);
        assertEquals(0.0, b.getY(), 1e-3);
        assertEquals(4.0, b.getWidth(), 1e-3);
        assertEquals(10.0, b.getHeight(), 1e-3);
    }

    // getPointInRectangle: point already inside area is unchanged
    @Test
    public void testGetPointInRectangle_withinBounds_unchanged() throws Throwable {
        Rectangle2D area = new Rectangle2D.Double(0, 0, 10, 10);
        Point2D p = ShapeUtilities.getPointInRectangle(5.0, 5.0, area);
        assertEquals(5.0, p.getX(), 1e-9);
        assertEquals(5.0, p.getY(), 1e-9);
    }

    // getPointInRectangle: coordinates below minimum are clamped
    @Test
    public void testGetPointInRectangle_belowMin_clamped() throws Throwable {
        Rectangle2D area = new Rectangle2D.Double(0, 0, 10, 10);
        Point2D p = ShapeUtilities.getPointInRectangle(-5.0, -5.0, area);
        assertEquals(0.0, p.getX(), 1e-9);
        assertEquals(0.0, p.getY(), 1e-9);
    }

    // getPointInRectangle: coordinates above maximum are clamped
    @Test
    public void testGetPointInRectangle_aboveMax_clamped() throws Throwable {
        Rectangle2D area = new Rectangle2D.Double(0, 0, 10, 10);
        Point2D p = ShapeUtilities.getPointInRectangle(15.0, 15.0, area);
        assertEquals(10.0, p.getX(), 1e-9);
        assertEquals(10.0, p.getY(), 1e-9);
    }

    // contains: rect2 fully inside rect1 -> true
    @Test
    public void testContains_fullyContained_true() throws Throwable {
        Rectangle2D r1 = new Rectangle2D.Double(0, 0, 10, 10);
        Rectangle2D r2 = new Rectangle2D.Double(2, 2, 3, 3);
        assertTrue(ShapeUtilities.contains(r1, r2));
    }

    // contains: rect2 sticks outside rect1 -> false
    @Test
    public void testContains_notContained_false() throws Throwable {
        Rectangle2D r1 = new Rectangle2D.Double(0, 0, 10, 10);
        Rectangle2D r2 = new Rectangle2D.Double(8, 8, 5, 5);
        assertFalse(ShapeUtilities.contains(r1, r2));
    }

    // contains: zero-size rect2 exactly on the boundary -> true
    @Test
    public void testContains_zeroSizeRectOnBoundary_true() throws Throwable {
        Rectangle2D r1 = new Rectangle2D.Double(0, 0, 10, 10);
        Rectangle2D r2 = new Rectangle2D.Double(10, 10, 0, 0);
        assertTrue(ShapeUtilities.contains(r1, r2));
    }

    // intersects: overlapping rectangles -> true
    @Test
    public void testIntersects_overlapping_true() throws Throwable {
        Rectangle2D r1 = new Rectangle2D.Double(0, 0, 10, 10);
        Rectangle2D r2 = new Rectangle2D.Double(5, 5, 10, 10);
        assertTrue(ShapeUtilities.intersects(r1, r2));
    }

    // intersects: disjoint rectangles -> false
    @Test
    public void testIntersects_nonOverlapping_false() throws Throwable {
        Rectangle2D r1 = new Rectangle2D.Double(0, 0, 10, 10);
        Rectangle2D r2 = new Rectangle2D.Double(20, 20, 5, 5);
        assertFalse(ShapeUtilities.intersects(r1, r2));
    }

    // intersects: rectangles touching exactly at the boundary -> true
    @Test
    public void testIntersects_touchingEdge_true() throws Throwable {
        Rectangle2D r1 = new Rectangle2D.Double(0, 0, 10, 10);
        Rectangle2D r2 = new Rectangle2D.Double(10, 0, 5, 5);
        assertTrue(ShapeUtilities.intersects(r1, r2));
    }
}
