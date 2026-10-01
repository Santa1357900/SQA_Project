package org.jfree.chart.block;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import org.jfree.chart.util.RectangleEdge;
import org.jfree.chart.util.Size2D;
import org.jfree.data.Range;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class BorderArrangementClaudeTest {

    private static final double DELTA = 0.001;

    private Graphics2D g2;
    private BlockContainer container;
    private BorderArrangement ba;

    @Before
    public void setUp() throws Throwable {
        this.g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        this.container = new BlockContainer();
        this.ba = new BorderArrangement();
    }

    // equals(): obj == this branch
    @Test
    public void testEquals_sameReference_true() throws Throwable {
        assertTrue(this.ba.equals(this.ba));
    }

    // equals(): null argument -> not instanceof -> false
    @Test
    public void testEquals_null_false() throws Throwable {
        assertFalse(this.ba.equals(null));
    }

    // equals(): different type argument -> false
    @Test
    public void testEquals_differentType_false() throws Throwable {
        assertFalse(this.ba.equals("not a BorderArrangement"));
    }

    // equals(): two default instances with all null blocks -> true
    @Test
    public void testEquals_twoDefaultInstances_true() throws Throwable {
        assertTrue(new BorderArrangement().equals(new BorderArrangement()));
    }

    // equals(): same center block reference in both -> true
    @Test
    public void testEquals_sameCenterBlockReference_true() throws Throwable {
        BorderArrangement ba1 = new BorderArrangement();
        BorderArrangement ba2 = new BorderArrangement();
        EmptyBlock center = new EmptyBlock(10.0, 10.0);
        ba1.add(center, null);
        ba2.add(center, null);
        assertTrue(ba1.equals(ba2));
    }

    // equals(): different center blocks (different sizes) -> false
    @Test
    public void testEquals_differentCenterBlock_false() throws Throwable {
        BorderArrangement ba1 = new BorderArrangement();
        BorderArrangement ba2 = new BorderArrangement();
        ba1.add(new EmptyBlock(10.0, 10.0), null);
        ba2.add(new EmptyBlock(20.0, 20.0), null);
        assertFalse(ba1.equals(ba2));
    }

    // equals(): different top blocks -> false
    @Test
    public void testEquals_differentTopBlock_false() throws Throwable {
        BorderArrangement ba1 = new BorderArrangement();
        BorderArrangement ba2 = new BorderArrangement();
        ba1.add(new EmptyBlock(5.0, 5.0), RectangleEdge.TOP);
        ba2.add(new EmptyBlock(7.0, 7.0), RectangleEdge.TOP);
        assertFalse(ba1.equals(ba2));
    }

    // clear(): resets all block fields to null, matches default instance
    @Test
    public void testClear_afterAddingBlocks_resetsToDefaultEquals() throws Throwable {
        this.ba.add(new EmptyBlock(1.0, 1.0), RectangleEdge.TOP);
        this.ba.add(new EmptyBlock(1.0, 1.0), null);
        this.ba.clear();
        assertTrue(this.ba.equals(new BorderArrangement()));
    }

    // add(): null key sets center block; verified via arrangeNN placement
    @Test
    public void testAdd_nullKey_setsCenterBlock_viaArrangeNN() throws Throwable {
        EmptyBlock center = new EmptyBlock(80.0, 15.0);
        this.ba.add(center, null);
        Size2D size = this.ba.arrangeNN(this.container, this.g2);
        assertEquals(80.0, size.getWidth(), DELTA);
        assertEquals(15.0, size.getHeight(), DELTA);
    }

    // add(): TOP edge sets topBlock; verified via arrangeNN placement
    @Test
    public void testAdd_topEdge_setsTopBlock_viaArrangeNN() throws Throwable {
        EmptyBlock top = new EmptyBlock(20.0, 7.0);
        this.ba.add(top, RectangleEdge.TOP);
        Size2D size = this.ba.arrangeNN(this.container, this.g2);
        assertEquals(20.0, size.getWidth(), DELTA);
        assertEquals(7.0, size.getHeight(), DELTA);
        Rectangle2D r = top.getBounds();
        assertEquals(0.0, r.getX(), DELTA);
        assertEquals(0.0, r.getY(), DELTA);
    }

    // add(): BOTTOM edge sets bottomBlock; verified via arrangeNN placement
    @Test
    public void testAdd_bottomEdge_setsBottomBlock_viaArrangeNN() throws Throwable {
        EmptyBlock bottom = new EmptyBlock(15.0, 9.0);
        this.ba.add(bottom, RectangleEdge.BOTTOM);
        Size2D size = this.ba.arrangeNN(this.container, this.g2);
        assertEquals(15.0, size.getWidth(), DELTA);
        assertEquals(9.0, size.getHeight(), DELTA);
        Rectangle2D r = bottom.getBounds();
        assertEquals(0.0, r.getY(), DELTA);
    }

    // add(): LEFT edge sets leftBlock; verified via arrangeNN placement
    @Test
    public void testAdd_leftEdge_setsLeftBlock_viaArrangeNN() throws Throwable {
        EmptyBlock left = new EmptyBlock(11.0, 6.0);
        this.ba.add(left, RectangleEdge.LEFT);
        Size2D size = this.ba.arrangeNN(this.container, this.g2);
        assertEquals(11.0, size.getWidth(), DELTA);
        assertEquals(6.0, size.getHeight(), DELTA);
    }

    // add(): RIGHT edge sets rightBlock; verified via arrangeNN placement
    @Test
    public void testAdd_rightEdge_setsRightBlock_viaArrangeNN() throws Throwable {
        EmptyBlock right = new EmptyBlock(13.0, 4.0);
        this.ba.add(right, RectangleEdge.RIGHT);
        Size2D size = this.ba.arrangeNN(this.container, this.g2);
        assertEquals(13.0, size.getWidth(), DELTA);
        assertEquals(4.0, size.getHeight(), DELTA);
        Rectangle2D r = right.getBounds();
        assertEquals(0.0, r.getX(), DELTA);
    }

    // add(): non-null, non-RectangleEdge key causes ClassCastException on cast
    @Test
    public void testAdd_invalidKeyType_throwsClassCastException() throws Throwable {
        EmptyBlock block = new EmptyBlock(1.0, 1.0);
        try {
            this.ba.add(block, "not an edge");
            fail("expected ClassCastException");
        }
        catch (ClassCastException expected) {
            // expected
        }
    }

    // add(): passing null block at TOP removes previously added top block
    @Test
    public void testAdd_nullBlockRemovesTop_viaArrangeNN() throws Throwable {
        this.ba.add(new EmptyBlock(20.0, 7.0), RectangleEdge.TOP);
        this.ba.add(null, RectangleEdge.TOP);
        Size2D size = this.ba.arrangeNN(this.container, this.g2);
        assertEquals(0.0, size.getWidth(), DELTA);
        assertEquals(0.0, size.getHeight(), DELTA);
    }

    // arrange(): width=NONE, height=NONE dispatches to arrangeNN
    @Test
    public void testArrange_noneNone_delegatesToArrangeNN() throws Throwable {
        Size2D size = this.ba.arrange(this.container, this.g2, RectangleConstraint.NONE);
        assertEquals(0.0, size.getWidth(), DELTA);
        assertEquals(0.0, size.getHeight(), DELTA);
    }

    // arrange(): width=NONE, height=FIXED -> not implemented
    @Test
    public void testArrange_noneFixed_throwsRuntimeException() throws Throwable {
        RectangleConstraint c = new RectangleConstraint(0.0, null,
                LengthConstraintType.NONE, 50.0, null, LengthConstraintType.FIXED);
        try {
            this.ba.arrange(this.container, this.g2, c);
            fail("expected RuntimeException");
        }
        catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("implemented"));
        }
    }

    // arrange(): width=NONE, height=RANGE -> not implemented
    @Test
    public void testArrange_noneRange_throwsRuntimeException() throws Throwable {
        RectangleConstraint c = new RectangleConstraint(0.0, null,
                LengthConstraintType.NONE, 0.0, new Range(0.0, 50.0),
                LengthConstraintType.RANGE);
        try {
            this.ba.arrange(this.container, this.g2, c);
            fail("expected RuntimeException");
        }
        catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("implemented"));
        }
    }

    // arrange(): width=RANGE, height=NONE -> not implemented
    @Test
    public void testArrange_rangeNone_throwsRuntimeException() throws Throwable {
        RectangleConstraint c = new RectangleConstraint(0.0, new Range(0.0, 50.0),
                LengthConstraintType.RANGE, 0.0, null, LengthConstraintType.NONE);
        try {
            this.ba.arrange(this.container, this.g2, c);
            fail("expected RuntimeException");
        }
        catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("implemented"));
        }
    }

    // arrange(): width=RANGE, height=FIXED -> not implemented
    @Test
    public void testArrange_rangeFixed_throwsRuntimeException() throws Throwable {
        RectangleConstraint c = new RectangleConstraint(0.0, new Range(0.0, 50.0),
                LengthConstraintType.RANGE, 50.0, null, LengthConstraintType.FIXED);
        try {
            this.ba.arrange(this.container, this.g2, c);
            fail("expected RuntimeException");
        }
        catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("implemented"));
        }
    }

    // arrange(): width=FIXED, height=NONE dispatches to arrangeFN (no blocks)
    @Test
    public void testArrange_fixedNone_delegatesToArrangeFN() throws Throwable {
        RectangleConstraint c = new RectangleConstraint(50.0, null,
                LengthConstraintType.FIXED, 0.0, null, LengthConstraintType.NONE);
        Size2D size = this.ba.arrange(this.container, this.g2, c);
        assertEquals(50.0, size.getWidth(), DELTA);
        assertEquals(0.0, size.getHeight(), DELTA);
    }

    // arrange(): width=FIXED, height=FIXED dispatches to arrangeFF (no blocks)
    @Test
    public void testArrange_fixedFixed_delegatesToArrangeFF() throws Throwable {
        RectangleConstraint c = new RectangleConstraint(50.0, 30.0);
        Size2D size = this.ba.arrange(this.container, this.g2, c);
        assertEquals(50.0, size.getWidth(), DELTA);
        assertEquals(30.0, size.getHeight(), DELTA);
    }

    // arrange(): width=FIXED, height=RANGE dispatches to arrangeFR (no blocks)
    @Test
    public void testArrange_fixedRange_delegatesToArrangeFR() throws Throwable {
        RectangleConstraint c = new RectangleConstraint(50.0, null,
                LengthConstraintType.FIXED, 0.0, new Range(0.0, 100.0),
                LengthConstraintType.RANGE);
        Size2D size = this.ba.arrange(this.container, this.g2, c);
        assertEquals(50.0, size.getWidth(), DELTA);
        assertEquals(0.0, size.getHeight(), DELTA);
    }

    // arrange(): width=RANGE, height=RANGE dispatches to arrangeRR (no blocks)
    @Test
    public void testArrange_rangeRange_delegatesToArrangeRR() throws Throwable {
        RectangleConstraint c = new RectangleConstraint(new Range(0.0, 80.0),
                new Range(0.0, 80.0));
        Size2D size = this.ba.arrange(this.container, this.g2, c);
        assertEquals(0.0, size.getWidth(), DELTA);
        assertEquals(0.0, size.getHeight(), DELTA);
    }

    // arrangeNN(): all five blocks present, verify computed size and bounds
    @Test
    public void testArrangeNN_allFiveBlocks_computesCorrectLayout() throws Throwable {
        EmptyBlock top = new EmptyBlock(50.0, 10.0);
        EmptyBlock bottom = new EmptyBlock(50.0, 8.0);
        EmptyBlock left = new EmptyBlock(15.0, 30.0);
        EmptyBlock right = new EmptyBlock(20.0, 30.0);
        EmptyBlock center = new EmptyBlock(60.0, 30.0);
        this.ba.add(top, RectangleEdge.TOP);
        this.ba.add(bottom, RectangleEdge.BOTTOM);
        this.ba.add(left, RectangleEdge.LEFT);
        this.ba.add(right, RectangleEdge.RIGHT);
        this.ba.add(center, null);
        Size2D size = this.ba.arrangeNN(this.container, this.g2);
        assertEquals(95.0, size.getWidth(), DELTA);
        assertEquals(48.0, size.getHeight(), DELTA);
        assertEquals(15.0, center.getBounds().getX(), DELTA);
        assertEquals(60.0, center.getBounds().getWidth(), DELTA);
    }

    // arrangeFN(): only center block present, natural height derived from center
    @Test
    public void testArrangeFN_centerBlockOnly_computesWidthAndHeight() throws Throwable {
        EmptyBlock center = new EmptyBlock(80.0, 15.0);
        this.ba.add(center, null);
        Size2D size = this.ba.arrangeFN(this.container, this.g2, 50.0);
        assertEquals(50.0, size.getWidth(), DELTA);
        assertEquals(15.0, size.getHeight(), DELTA);
        assertEquals(50.0, center.getBounds().getWidth(), DELTA);
    }

    // arrangeFR(): natural height already within height range -> no recursion
    @Test
    public void testArrangeFR_heightWithinRange_returnsNaturalSize() throws Throwable {
        EmptyBlock center = new EmptyBlock(80.0, 15.0);
        this.ba.add(center, null);
        RectangleConstraint c = new RectangleConstraint(50.0, null,
                LengthConstraintType.FIXED, 0.0, new Range(0.0, 100.0),
                LengthConstraintType.RANGE);
        Size2D size = this.ba.arrangeFR(this.container, this.g2, c);
        assertEquals(50.0, size.getWidth(), DELTA);
        assertEquals(15.0, size.getHeight(), DELTA);
    }

    // arrangeFR(): natural height outside range -> recurses with clamped fixed height
    @Test
    public void testArrangeFR_heightOutsideRange_recursesWithClampedHeight() throws Throwable {
        EmptyBlock center = new EmptyBlock(80.0, 15.0);
        this.ba.add(center, null);
        RectangleConstraint c = new RectangleConstraint(50.0, null,
                LengthConstraintType.FIXED, 0.0, new Range(0.0, 5.0),
                LengthConstraintType.RANGE);
        Size2D size = this.ba.arrangeFR(this.container, this.g2, c);
        assertEquals(50.0, size.getWidth(), DELTA);
        assertEquals(5.0, size.getHeight(), DELTA);
        assertEquals(5.0, center.getBounds().getHeight(), DELTA);
    }

    // BUG TEST (Chart-13): arrangeFF must not assign a negative width to the
    // center block when left+right block widths exceed the total width.
    @Test
    public void testArrangeFF_widthTooSmallForLeftRight_centerWidthNotNegative() throws Throwable {
        EmptyBlock left = new EmptyBlock(30.0, 10.0);
        EmptyBlock right = new EmptyBlock(30.0, 10.0);
        EmptyBlock center = new EmptyBlock(20.0, 10.0);
        this.ba.add(left, RectangleEdge.LEFT);
        this.ba.add(right, RectangleEdge.RIGHT);
        this.ba.add(center, null);
        RectangleConstraint c = new RectangleConstraint(50.0, 100.0);
        this.ba.arrangeFF(this.container, this.g2, c);
        assertTrue(center.getBounds().getWidth() >= 0.0);
    }

    // arrangeFF(): all five blocks present, verify final bounds placement
    @Test
    public void testArrangeFF_allBlocksPresent_computesBoundsCorrectly() throws Throwable {
        EmptyBlock top = new EmptyBlock(999.0, 12.0);
        EmptyBlock bottom = new EmptyBlock(999.0, 8.0);
        EmptyBlock left = new EmptyBlock(12.0, 50.0);
        EmptyBlock right = new EmptyBlock(18.0, 50.0);
        EmptyBlock center = new EmptyBlock(500.0, 500.0);
        this.ba.add(top, RectangleEdge.TOP);
        this.ba.add(bottom, RectangleEdge.BOTTOM);
        this.ba.add(left, RectangleEdge.LEFT);
        this.ba.add(right, RectangleEdge.RIGHT);
        this.ba.add(center, null);
        Size2D size = this.ba.arrangeFF(this.container, this.g2,
                new RectangleConstraint(100.0, 60.0));
        assertEquals(100.0, size.getWidth(), DELTA);
        assertEquals(60.0, size.getHeight(), DELTA);
        assertEquals(52.0, bottom.getBounds().getY(), DELTA);
        assertEquals(82.0, right.getBounds().getX(), DELTA);
        assertEquals(70.0, center.getBounds().getWidth(), DELTA);
    }

    // arrangeRR(): only center block present, range constraints ignored by EmptyBlock
    @Test
    public void testArrangeRR_centerBlockOnly_computesSize() throws Throwable {
        EmptyBlock center = new EmptyBlock(30.0, 40.0);
        this.ba.add(center, null);
        Range widthRange = new Range(0.0, 100.0);
        Range heightRange = new Range(0.0, 100.0);
        Size2D size = this.ba.arrangeRR(this.container, widthRange, heightRange, this.g2);
        assertEquals(30.0, size.getWidth(), DELTA);
        assertEquals(40.0, size.getHeight(), DELTA);
        assertEquals(0.0, center.getBounds().getX(), DELTA);
    }
}
