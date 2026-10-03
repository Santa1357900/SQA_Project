package org.jfree.chart.axis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.EventListener;
import java.util.List;

import org.jfree.chart.event.AxisChangeEvent;
import org.jfree.chart.event.AxisChangeListener;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.util.RectangleEdge;
import org.jfree.chart.util.RectangleInsets;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AxisClaudeTest {

    private static class TestAxis extends Axis {
        int configureCount = 0;

        public TestAxis(String label) {
            super(label);
        }

        public void configure() {
            this.configureCount++;
        }

        public AxisSpace reserveSpace(Graphics2D g2, Plot plot,
                Rectangle2D plotArea, RectangleEdge edge, AxisSpace space) {
            return space;
        }

        public AxisState draw(Graphics2D g2, double cursor,
                Rectangle2D plotArea, Rectangle2D dataArea,
                RectangleEdge edge, PlotRenderingInfo plotState) {
            return null;
        }

        public List refreshTicks(Graphics2D g2, AxisState state,
                Rectangle2D dataArea, RectangleEdge edge) {
            return null;
        }
    }

    private static class RecordingListener implements AxisChangeListener {
        int count = 0;
        AxisChangeEvent lastEvent = null;

        public void axisChanged(AxisChangeEvent event) {
            this.count++;
            this.lastEvent = event;
        }
    }

    private TestAxis axis;
    private RecordingListener listener;

    @Before
    public void setUp() throws Throwable {
        this.axis = new TestAxis("Test");
        this.listener = new RecordingListener();
        this.axis.addChangeListener(this.listener);
    }

    // covers constructor defaults for label/visibility/axis-line fields
    @Test
    public void testConstructor_labelAndAxisLineDefaults() throws Throwable {
        TestAxis a = new TestAxis("MyLabel");
        assertEquals("MyLabel", a.getLabel());
        assertTrue(a.isVisible());
        assertEquals(Axis.DEFAULT_AXIS_LABEL_FONT, a.getLabelFont());
        assertEquals(Axis.DEFAULT_AXIS_LABEL_PAINT, a.getLabelPaint());
        assertEquals(Axis.DEFAULT_AXIS_LABEL_INSETS, a.getLabelInsets());
        assertEquals(0.0, a.getLabelAngle(), 0.0000001);
        assertNull(a.getLabelToolTip());
        assertNull(a.getLabelURL());
        assertTrue(a.isAxisLineVisible());
        assertEquals(Axis.DEFAULT_AXIS_LINE_PAINT, a.getAxisLinePaint());
        assertEquals(Axis.DEFAULT_AXIS_LINE_STROKE, a.getAxisLineStroke());
        assertNull(a.getPlot());
    }

    // covers constructor defaults for tick label/tick mark fields
    @Test
    public void testConstructor_tickLabelAndTickMarkDefaults() throws Throwable {
        TestAxis a = new TestAxis(null);
        assertTrue(a.isTickLabelsVisible());
        assertEquals(Axis.DEFAULT_TICK_LABEL_FONT, a.getTickLabelFont());
        assertEquals(Axis.DEFAULT_TICK_LABEL_PAINT, a.getTickLabelPaint());
        assertEquals(Axis.DEFAULT_TICK_LABEL_INSETS, a.getTickLabelInsets());
        assertTrue(a.isTickMarksVisible());
        assertEquals(Axis.DEFAULT_TICK_MARK_INSIDE_LENGTH,
                a.getTickMarkInsideLength(), 0.0000001);
        assertEquals(Axis.DEFAULT_TICK_MARK_OUTSIDE_LENGTH,
                a.getTickMarkOutsideLength(), 0.0000001);
        assertEquals(Axis.DEFAULT_TICK_MARK_PAINT, a.getTickMarkPaint());
        assertEquals(Axis.DEFAULT_TICK_MARK_STROKE, a.getTickMarkStroke());
        assertNull(a.getLabel());
    }

    // covers null-argument validation branches across multiple setters
    @Test
    public void testSetters_nullArgument_throwsIllegalArgumentException()
            throws Throwable {
        try {
            this.axis.setLabelFont(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            this.axis.setLabelPaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            this.axis.setLabelInsets(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            this.axis.setAxisLinePaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            this.axis.setAxisLineStroke(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            this.axis.setTickLabelFont(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            this.axis.setTickLabelPaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            this.axis.setTickLabelInsets(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            this.axis.setTickMarkStroke(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            this.axis.setTickMarkPaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers setVisible() change branch: notifies listener and updates state
    @Test
    public void testSetVisible_change_notifiesListener() throws Throwable {
        this.axis.setVisible(false);
        assertFalse(this.axis.isVisible());
        assertEquals(1, this.listener.count);
    }

    // covers setVisible() no-change branch: does not notify
    @Test
    public void testSetVisible_noChange_doesNotNotify() throws Throwable {
        this.axis.setVisible(true);
        assertTrue(this.axis.isVisible());
        assertEquals(0, this.listener.count);
    }

    // covers setLabel() null-to-value branch
    @Test
    public void testSetLabel_nullToValue_notifies() throws Throwable {
        TestAxis a = new TestAxis(null);
        a.addChangeListener(this.listener);
        a.setLabel("New");
        assertEquals("New", a.getLabel());
        assertEquals(1, this.listener.count);
    }

    // covers setLabel() same-value branch: no notification
    @Test
    public void testSetLabel_sameValue_doesNotNotify() throws Throwable {
        this.axis.setLabel("Test");
        assertEquals("Test", this.axis.getLabel());
        assertEquals(0, this.listener.count);
    }

    // covers setLabel() value-to-null branch
    @Test
    public void testSetLabel_valueToNull_notifies() throws Throwable {
        this.axis.setLabel(null);
        assertNull(this.axis.getLabel());
        assertEquals(1, this.listener.count);
    }

    // covers setLabelFont() different-font branch
    @Test
    public void testSetLabelFont_differentFont_notifies() throws Throwable {
        Font f = new Font("Serif", Font.BOLD, 14);
        this.axis.setLabelFont(f);
        assertEquals(f, this.axis.getLabelFont());
        assertEquals(1, this.listener.count);
    }

    // covers setLabelFont() equal-font branch: no notification
    @Test
    public void testSetLabelFont_sameFont_doesNotNotify() throws Throwable {
        this.axis.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        assertEquals(0, this.listener.count);
    }

    // covers setLabelPaint(): always updates and notifies
    @Test
    public void testSetLabelPaint_valid_notifies() throws Throwable {
        this.axis.setLabelPaint(Color.red);
        assertEquals(Color.red, this.axis.getLabelPaint());
        assertEquals(1, this.listener.count);
    }

    // covers setLabelInsets() different-insets branch
    @Test
    public void testSetLabelInsets_differentInsets_notifies() throws Throwable {
        RectangleInsets ri = new RectangleInsets(1.0, 1.0, 1.0, 1.0);
        this.axis.setLabelInsets(ri);
        assertEquals(ri, this.axis.getLabelInsets());
        assertEquals(1, this.listener.count);
    }

    // covers setLabelInsets() equal-insets branch: no notification
    @Test
    public void testSetLabelInsets_sameInsets_doesNotNotify() throws Throwable {
        this.axis.setLabelInsets(new RectangleInsets(3.0, 3.0, 3.0, 3.0));
        assertEquals(0, this.listener.count);
    }

    // covers setLabelAngle(): always updates and notifies
    @Test
    public void testSetLabelAngle_updatesAndNotifies() throws Throwable {
        this.axis.setLabelAngle(Math.PI / 4.0);
        assertEquals(Math.PI / 4.0, this.axis.getLabelAngle(), 0.0000001);
        assertEquals(1, this.listener.count);
    }

    // covers getLabelToolTip()/setLabelToolTip(): default null, then updates
    @Test
    public void testLabelToolTip_defaultNullAndSetNotifies() throws Throwable {
        assertNull(this.axis.getLabelToolTip());
        this.axis.setLabelToolTip("tip");
        assertEquals("tip", this.axis.getLabelToolTip());
        assertEquals(1, this.listener.count);
    }

    // covers getLabelURL()/setLabelURL(): default null, then updates
    @Test
    public void testLabelURL_defaultNullAndSetNotifies() throws Throwable {
        assertNull(this.axis.getLabelURL());
        this.axis.setLabelURL("http://example.com");
        assertEquals("http://example.com", this.axis.getLabelURL());
        assertEquals(1, this.listener.count);
    }

    // covers setAxisLineVisible(): unconditionally updates and notifies
    @Test
    public void testSetAxisLineVisible_updatesAndNotifies() throws Throwable {
        this.axis.setAxisLineVisible(false);
        assertFalse(this.axis.isAxisLineVisible());
        assertEquals(1, this.listener.count);
    }

    // covers setAxisLinePaint(): updates and notifies
    @Test
    public void testSetAxisLinePaint_valid_notifies() throws Throwable {
        this.axis.setAxisLinePaint(Color.blue);
        assertEquals(Color.blue, this.axis.getAxisLinePaint());
        assertEquals(1, this.listener.count);
    }

    // covers setAxisLineStroke(): updates and notifies
    @Test
    public void testSetAxisLineStroke_valid_notifies() throws Throwable {
        BasicStroke s = new BasicStroke(2.5f);
        this.axis.setAxisLineStroke(s);
        assertEquals(s, this.axis.getAxisLineStroke());
        assertEquals(1, this.listener.count);
    }

    // covers setTickLabelsVisible() change branch
    @Test
    public void testSetTickLabelsVisible_change_notifies() throws Throwable {
        this.axis.setTickLabelsVisible(false);
        assertFalse(this.axis.isTickLabelsVisible());
        assertEquals(1, this.listener.count);
    }

    // covers setTickLabelsVisible() no-change branch: no notification
    @Test
    public void testSetTickLabelsVisible_noChange_doesNotNotify() throws Throwable {
        this.axis.setTickLabelsVisible(true);
        assertEquals(0, this.listener.count);
    }

    // covers setTickLabelFont() different-font branch
    @Test
    public void testSetTickLabelFont_differentFont_notifies() throws Throwable {
        Font f = new Font("Monospaced", Font.ITALIC, 9);
        this.axis.setTickLabelFont(f);
        assertEquals(f, this.axis.getTickLabelFont());
        assertEquals(1, this.listener.count);
    }

    // covers setTickLabelPaint(): updates and notifies
    @Test
    public void testSetTickLabelPaint_valid_notifies() throws Throwable {
        this.axis.setTickLabelPaint(Color.green);
        assertEquals(Color.green, this.axis.getTickLabelPaint());
        assertEquals(1, this.listener.count);
    }

    // covers setTickLabelInsets() different-insets branch
    @Test
    public void testSetTickLabelInsets_differentInsets_notifies() throws Throwable {
        RectangleInsets ri = new RectangleInsets(5.0, 5.0, 5.0, 5.0);
        this.axis.setTickLabelInsets(ri);
        assertEquals(ri, this.axis.getTickLabelInsets());
        assertEquals(1, this.listener.count);
    }

    // covers setTickMarksVisible() change branch
    @Test
    public void testSetTickMarksVisible_change_notifies() throws Throwable {
        this.axis.setTickMarksVisible(false);
        assertFalse(this.axis.isTickMarksVisible());
        assertEquals(1, this.listener.count);
    }

    // covers setTickMarkInsideLength() and setTickMarkOutsideLength()
    @Test
    public void testSetTickMarkInsideAndOutsideLength_updatesAndNotifies()
            throws Throwable {
        this.axis.setTickMarkInsideLength(4.5f);
        assertEquals(4.5f, this.axis.getTickMarkInsideLength(), 0.0000001);
        this.axis.setTickMarkOutsideLength(6.5f);
        assertEquals(6.5f, this.axis.getTickMarkOutsideLength(), 0.0000001);
        assertEquals(2, this.listener.count);
    }

    // covers setTickMarkStroke() equal-stroke branch: no notification
    @Test
    public void testSetTickMarkStroke_equalStroke_doesNotNotify() throws Throwable {
        this.axis.setTickMarkStroke(new BasicStroke(1));
        assertEquals(0, this.listener.count);
    }

    // covers setTickMarkStroke() different-stroke branch
    @Test
    public void testSetTickMarkStroke_differentStroke_notifies() throws Throwable {
        BasicStroke s = new BasicStroke(3.0f);
        this.axis.setTickMarkStroke(s);
        assertEquals(s, this.axis.getTickMarkStroke());
        assertEquals(1, this.listener.count);
    }

    // covers setTickMarkPaint(): updates and notifies
    @Test
    public void testSetTickMarkPaint_valid_notifies() throws Throwable {
        this.axis.setTickMarkPaint(Color.orange);
        assertEquals(Color.orange, this.axis.getTickMarkPaint());
        assertEquals(1, this.listener.count);
    }

    // covers setPlot(): assigns reference and always invokes configure()
    @Test
    public void testSetPlot_callsConfigureAndStoresReference() throws Throwable {
        assertEquals(0, this.axis.configureCount);
        this.axis.setPlot(null);
        assertEquals(1, this.axis.configureCount);
        assertNull(this.axis.getPlot());
    }

    // covers setFixedDimension(): does not notify listeners
    @Test
    public void testSetFixedDimension_doesNotNotifyListeners() throws Throwable {
        this.axis.setFixedDimension(12.5);
        assertEquals(12.5, this.axis.getFixedDimension(), 0.0000001);
        assertEquals(0, this.listener.count);
    }

    // covers addChangeListener/removeChangeListener/hasListener contract
    @Test
    public void testAddRemoveChangeListener_hasListenerReflectsState()
            throws Throwable {
        RecordingListener other = new RecordingListener();
        assertFalse(this.axis.hasListener(other));
        this.axis.addChangeListener(other);
        assertTrue(this.axis.hasListener(other));
        this.axis.removeChangeListener(other);
        assertFalse(this.axis.hasListener(other));
    }

    // covers notifyListeners() firing all registered listeners with the event
    @Test
    public void testNotifyListeners_directCall_firesRegisteredListener()
            throws Throwable {
        AxisChangeEvent event = new AxisChangeEvent(this.axis);
        this.axis.notifyListeners(event);
        assertEquals(1, this.listener.count);
        assertSame(event, this.listener.lastEvent);
    }

    // covers getLabelEnclosure() null-label branch: returns empty rectangle
    @Test
    public void testGetLabelEnclosure_nullLabel_returnsEmptyRectangle()
            throws Throwable {
        TestAxis a = new TestAxis(null);
        BufferedImage img = new BufferedImage(10, 10,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D rect = a.getLabelEnclosure(g2, RectangleEdge.BOTTOM);
        assertEquals(0.0, rect.getWidth(), 0.0000001);
        assertEquals(0.0, rect.getHeight(), 0.0000001);
    }

    // covers getLabelEnclosure() non-null label branch on BOTTOM edge
    @Test
    public void testGetLabelEnclosure_nonNullLabelBottomEdge_returnsPositiveBounds()
            throws Throwable {
        BufferedImage img = new BufferedImage(10, 10,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D rect = this.axis.getLabelEnclosure(g2, RectangleEdge.BOTTOM);
        assertTrue(rect.getWidth() > 0.0);
        assertTrue(rect.getHeight() > 0.0);
    }

    // covers getLabelEnclosure() non-null label branch on LEFT edge (angle path)
    @Test
    public void testGetLabelEnclosure_nonNullLabelLeftEdge_returnsPositiveBounds()
            throws Throwable {
        BufferedImage img = new BufferedImage(10, 10,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D rect = this.axis.getLabelEnclosure(g2, RectangleEdge.LEFT);
        assertTrue(rect.getWidth() > 0.0);
        assertTrue(rect.getHeight() > 0.0);
    }

    // covers drawAxisLine() TOP and LEFT branches setting paint/stroke
    @Test
    public void testDrawAxisLine_topAndLeftEdges_setsPaintAndStroke()
            throws Throwable {
        BufferedImage img = new BufferedImage(10, 10,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 100, 50);
        this.axis.drawAxisLine(g2, 5.0, dataArea, RectangleEdge.TOP);
        assertEquals(this.axis.getAxisLinePaint(), g2.getPaint());
        this.axis.drawAxisLine(g2, 5.0, dataArea, RectangleEdge.LEFT);
        assertEquals(this.axis.getAxisLineStroke(), g2.getStroke());
    }

    // covers drawAxisLine() BOTTOM and RIGHT branches setting paint/stroke
    @Test
    public void testDrawAxisLine_bottomAndRightEdges_setsPaintAndStroke()
            throws Throwable {
        BufferedImage img = new BufferedImage(10, 10,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 100, 50);
        this.axis.drawAxisLine(g2, 5.0, dataArea, RectangleEdge.BOTTOM);
        assertEquals(this.axis.getAxisLinePaint(), g2.getPaint());
        this.axis.drawAxisLine(g2, 5.0, dataArea, RectangleEdge.RIGHT);
        assertEquals(this.axis.getAxisLineStroke(), g2.getStroke());
    }

    // covers drawLabel() null-state branch: throws IllegalArgumentException
    @Test
    public void testDrawLabel_nullState_throwsIllegalArgumentException()
            throws Throwable {
        BufferedImage img = new BufferedImage(10, 10,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D plotArea = new Rectangle2D.Double(0, 0, 100, 100);
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 80, 80);
        try {
            this.axis.drawLabel("Label", g2, plotArea, dataArea,
                    RectangleEdge.BOTTOM, null, null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // covers equals() reflexive branch and type-mismatch branch
    @Test
    public void testEquals_reflexiveAndTypeMismatch() throws Throwable {
        assertTrue(this.axis.equals(this.axis));
        assertFalse(this.axis.equals("not an axis"));
    }

    // covers equals() label-field difference branch
    @Test
    public void testEquals_labelDifference_returnsFalse() throws Throwable {
        TestAxis other = new TestAxis("Different");
        assertFalse(this.axis.equals(other));
    }

    // covers equals() visible-field difference branch, plus baseline equality
    @Test
    public void testEquals_visibleFieldDifference_returnsFalse() throws Throwable {
        TestAxis other = new TestAxis("Test");
        assertTrue(this.axis.equals(other));
        other.setVisible(false);
        assertFalse(this.axis.equals(other));
    }

    // covers clone(): resets plot/listener list but keeps field equality
    @Test
    public void testClone_resetsPlotAndListenersButKeepsEquality()
            throws Throwable {
        Axis clone = (Axis) this.axis.clone();
        assertTrue(this.axis.equals(clone));
        assertFalse(clone.hasListener(this.listener));
        assertNull(clone.getPlot());
    }

}
