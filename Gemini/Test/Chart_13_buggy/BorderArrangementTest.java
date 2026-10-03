package org.jfree.chart.block;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.jfree.chart.util.RectangleEdge;
import org.jfree.chart.util.Size2D;
import org.jfree.data.Range;
import org.junit.Test;

public class BorderArrangementTest {

    @Test
    public void testConstructorAndAdd() throws Throwable {
        BorderArrangement arrangement = new BorderArrangement();
        Block block = new EmptyBlock(10.0, 10.0);
        
        arrangement.add(block, null);
        arrangement.add(block, RectangleEdge.TOP);
        arrangement.add(block, RectangleEdge.BOTTOM);
        arrangement.add(block, RectangleEdge.LEFT);
        arrangement.add(block, RectangleEdge.RIGHT);
        
        arrangement.clear();
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        BorderArrangement b1 = new BorderArrangement();
        BorderArrangement b2 = new BorderArrangement();
        
        assertTrue(b1.equals(b2));
        assertTrue(b1.equals(b1));
        assertFalse(b1.equals(null));
        assertFalse(b1.equals("Some String"));

        Block block1 = new EmptyBlock(10.0, 10.0);
        Block block2 = new EmptyBlock(10.0, 10.0);

        b1.add(block1, RectangleEdge.TOP);
        assertFalse(b1.equals(b2));

        b2.add(block2, RectangleEdge.TOP);
        assertTrue(b1.equals(b2));

        b1.add(block1, RectangleEdge.BOTTOM);
        b2.add(block2, RectangleEdge.BOTTOM);
        b1.add(block1, RectangleEdge.LEFT);
        b2.add(block2, RectangleEdge.LEFT);
        b1.add(block1, RectangleEdge.RIGHT);
        b2.add(block2, RectangleEdge.RIGHT);
        b1.add(block1, null);
        b2.add(block2, null);
        
        assertTrue(b1.equals(b2));
    }

    @Test
    public void testArrangeNN() throws Throwable {
        BorderArrangement arrangement = new BorderArrangement();
        BlockContainer container = new BlockContainer(arrangement);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.TOP);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.BOTTOM);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.LEFT);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.RIGHT);
        container.add(new EmptyBlock(10.0, 10.0), null);

        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        RectangleConstraint constraint = RectangleConstraint.NONE;
        Size2D size = arrangement.arrange(container, g2, constraint);
        assertNotNull(size);
    }

    @Test
    public void testArrangeFN() throws Throwable {
        BorderArrangement arrangement = new BorderArrangement();
        BlockContainer container = new BlockContainer(arrangement);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.TOP);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.BOTTOM);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.LEFT);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.RIGHT);
        container.add(new EmptyBlock(10.0, 10.0), null);

        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        RectangleConstraint constraint = new RectangleConstraint(100.0, null, LengthConstraintType.FIXED, 0.0, null, LengthConstraintType.NONE);
        Size2D size = arrangement.arrange(container, g2, constraint);
        assertNotNull(size);
    }

    @Test
    public void testArrangeFF() throws Throwable {
        BorderArrangement arrangement = new BorderArrangement();
        BlockContainer container = new BlockContainer(arrangement);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.TOP);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.BOTTOM);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.LEFT);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.RIGHT);
        container.add(new EmptyBlock(10.0, 10.0), null);

        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        RectangleConstraint constraint = new RectangleConstraint(100.0, 100.0);
        Size2D size = arrangement.arrange(container, g2, constraint);
        assertNotNull(size);
    }

    @Test
    public void testArrangeFR() throws Throwable {
        BorderArrangement arrangement = new BorderArrangement();
        BlockContainer container = new BlockContainer(arrangement);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.TOP);
        container.add(new EmptyBlock(10.0, 10.0), null);

        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        RectangleConstraint constraint = new RectangleConstraint(100.0, null, LengthConstraintType.FIXED, 0.0, new Range(0.0, 50.0), LengthConstraintType.RANGE);
        Size2D size = arrangement.arrange(container, g2, constraint);
        assertNotNull(size);
    }

    @Test
    public void testArrangeRR() throws Throwable {
        BorderArrangement arrangement = new BorderArrangement();
        BlockContainer container = new BlockContainer(arrangement);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.TOP);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.BOTTOM);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.LEFT);
        container.add(new EmptyBlock(10.0, 10.0), RectangleEdge.RIGHT);
        container.add(new EmptyBlock(10.0, 10.0), null);

        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        RectangleConstraint constraint = new RectangleConstraint(new Range(0.0, 100.0), new Range(0.0, 100.0));
        Size2D size = arrangement.arrange(container, g2, constraint);
        assertNotNull(size);
    }

    @Test(expected = RuntimeException.class)
    public void testArrangeUnsupportedWidthRangeHeightNone() throws Throwable {
        BorderArrangement arrangement = new BorderArrangement();
        BlockContainer container = new BlockContainer(arrangement);
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        RectangleConstraint constraint = new RectangleConstraint(new Range(0.0, 100.0), null);
        arrangement.arrange(container, g2, constraint);
    }

    @Test(expected = RuntimeException.class)
    public void testArrangeUnsupportedWidthRangeHeightFixed() throws Throwable {
        BorderArrangement arrangement = new BorderArrangement();
        BlockContainer container = new BlockContainer(arrangement);
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        RectangleConstraint constraint = new RectangleConstraint(new Range(0.0, 100.0), 100.0);
        arrangement.arrange(container, g2, constraint);
    }

    @Test(expected = RuntimeException.class)
    public void testArrangeUnsupportedWidthNoneHeightFixed() throws Throwable {
        BorderArrangement arrangement = new BorderArrangement();
        BlockContainer container = new BlockContainer(arrangement);
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        RectangleConstraint constraint = new RectangleConstraint(0.0, null, LengthConstraintType.NONE, 100.0, null, LengthConstraintType.FIXED);
        arrangement.arrange(container, g2, constraint);
    }

    @Test(expected = RuntimeException.class)
    public void testArrangeUnsupportedWidthNoneHeightRange() throws Throwable {
        BorderArrangement arrangement = new BorderArrangement();
        BlockContainer container = new BlockContainer(arrangement);
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        RectangleConstraint constraint = new RectangleConstraint(0.0, null, LengthConstraintType.NONE, 0.0, new Range(0.0, 100.0), LengthConstraintType.RANGE);
        arrangement.arrange(container, g2, constraint);
    }
}