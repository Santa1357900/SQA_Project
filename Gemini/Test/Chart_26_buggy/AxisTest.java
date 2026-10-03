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

import org.junit.Test;
import static org.junit.Assert.*;

import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.event.AxisChangeEvent;
import org.jfree.chart.event.AxisChangeListener;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.util.RectangleEdge;
import org.jfree.chart.util.RectangleInsets;

public class AxisTest {

    private static class ConcreteAxis extends Axis {
        private static final long serialVersionUID = 1L;

        public ConcreteAxis(String label) {
            super(label);
        }

        public void configure() {
        }

        public AxisSpace reserveSpace(Graphics2D g2, Plot plot, Rectangle2D plotArea, RectangleEdge edge, AxisSpace space) {
            if (space == null) {
                space = new AxisSpace();
            }
            return space;
        }

        public AxisState draw(Graphics2D g2, double cursor, Rectangle2D plotArea, Rectangle2D dataArea, RectangleEdge edge, PlotRenderingInfo plotState) {
            return new AxisState(cursor);
        }

        public List refreshTicks(Graphics2D g2, AxisState state, Rectangle2D dataArea, RectangleEdge edge) {
            return new java.util.ArrayList<Object>();
        }
    }

    private static class DummyAxisChangeListener implements AxisChangeListener {
        private boolean changed = false;

        public void axisChanged(AxisChangeEvent event) {
            this.changed = true;
        }

        public boolean isChanged() {
            return this.changed;
        }
    }

    @Test
    public void testConstructorAndGetters() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Test Axis");
        assertEquals("Test Axis", axis.getLabel());
        assertTrue(axis.isVisible());
        assertEquals(Axis.DEFAULT_AXIS_LABEL_FONT, axis.getLabelFont());
        assertEquals(Axis.DEFAULT_AXIS_LABEL_PAINT, axis.getLabelPaint());
        assertEquals(Axis.DEFAULT_AXIS_LABEL_INSETS, axis.getLabelInsets());
        assertEquals(0.0, axis.getLabelAngle(), 0.0001);
        assertNull(axis.getLabelToolTip());
        assertNull(axis.getLabelURL());
        assertTrue(axis.isAxisLineVisible());
        assertEquals(Axis.DEFAULT_AXIS_LINE_PAINT, axis.getAxisLinePaint());
        assertEquals(Axis.DEFAULT_AXIS_LINE_STROKE, axis.getAxisLineStroke());
        assertTrue(axis.isTickLabelsVisible());
        assertEquals(Axis.DEFAULT_TICK_LABEL_FONT, axis.getTickLabelFont());
        assertEquals(Axis.DEFAULT_TICK_LABEL_PAINT, axis.getTickLabelPaint());
        assertEquals(Axis.DEFAULT_TICK_LABEL_INSETS, axis.getTickLabelInsets());
        assertTrue(axis.isTickMarksVisible());
        assertEquals(Axis.DEFAULT_TICK_MARK_INSIDE_LENGTH, axis.getTickMarkInsideLength(), 0.0001f);
        assertEquals(Axis.DEFAULT_TICK_MARK_OUTSIDE_LENGTH, axis.getTickMarkOutsideLength(), 0.0001f);
        assertEquals(Axis.DEFAULT_TICK_MARK_STROKE, axis.getTickMarkStroke());
        assertEquals(Axis.DEFAULT_TICK_MARK_PAINT, axis.getTickMarkPaint());
        assertNull(axis.getPlot());
        assertEquals(0.0, axis.getFixedDimension(), 0.0001);
    }

    @Test
    public void testSetVisible() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        DummyAxisChangeListener listener = new DummyAxisChangeListener();
        axis.addChangeListener(listener);

        axis.setVisible(false);
        assertFalse(axis.isVisible());
        assertTrue(listener.isChanged());

        // Setting same value should not trigger event
        listener.changed = false;
        axis.setVisible(false);
        assertFalse(listener.isChanged());
    }

    @Test
    public void testSetLabel() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Old");
        DummyAxisChangeListener listener = new DummyAxisChangeListener();
        axis.addChangeListener(listener);

        axis.setLabel("New");
        assertEquals("New", axis.getLabel());
        assertTrue(listener.isChanged());

        // Set null label when existing is not null
        listener.changed = false;
        axis.setLabel(null);
        assertNull(axis.getLabel());
        assertTrue(listener.isChanged());

        // Set label from null to non-null
        listener.changed = false;
        axis.setLabel("Valid");
        assertEquals("Valid", axis.getLabel());
        assertTrue(listener.isChanged());

        // Set same label
        listener.changed = false;
        axis.setLabel("Valid");
        assertFalse(listener.isChanged());
    }

    @Test
    public void testSetLabelFont() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        DummyAxisChangeListener listener = new DummyAxisChangeListener();
        axis.addChangeListener(listener);

        Font newFont = new Font("Dialog", Font.BOLD, 14);
        axis.setLabelFont(newFont);
        assertEquals(newFont, axis.getLabelFont());
        assertTrue(listener.isChanged());

        // Same font
        listener.changed = false;
        axis.setLabelFont(newFont);
        assertFalse(listener.isChanged());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetLabelFontNull() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        axis.setLabelFont(null);
    }

    @Test
    public void testSetLabelPaint() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        DummyAxisChangeListener listener = new DummyAxisChangeListener();
        axis.addChangeListener(listener);

        axis.setLabelPaint(Color.red);
        assertEquals(Color.red, axis.getLabelPaint());
        assertTrue(listener.isChanged());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetLabelPaintNull() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        axis.setLabelPaint(null);
    }

    @Test
    public void testSetLabelInsets() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        DummyAxisChangeListener listener = new DummyAxisChangeListener();
        axis.addChangeListener(listener);

        RectangleInsets newInsets = new RectangleInsets(5.0, 5.0, 5.0, 5.0);
        axis.setLabelInsets(newInsets);
        assertEquals(newInsets, axis.getLabelInsets());
        assertTrue(listener.isChanged());

        // Same insets
        listener.changed = false;
        axis.setLabelInsets(newInsets);
        assertFalse(listener.isChanged());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetLabelInsetsNull() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        axis.setLabelInsets(null);
    }

    @Test
    public void testLabelAngleAndTooltipsAndURLs() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        DummyAxisChangeListener listener = new DummyAxisChangeListener();
        axis.addChangeListener(listener);

        axis.setLabelAngle(Math.PI);
        assertEquals(Math.PI, axis.getLabelAngle(), 0.0001);
        assertTrue(listener.isChanged());

        listener.changed = false;
        axis.setLabelToolTip("Tooltip");
        assertEquals("Tooltip", axis.getLabelToolTip());
        assertTrue(listener.isChanged());

        listener.changed = false;
        axis.setLabelURL("http://example.com");
        assertEquals("http://example.com", axis.getLabelURL());
        assertTrue(listener.isChanged());
    }

    @Test
    public void testAxisLineProperties() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        DummyAxisChangeListener listener = new DummyAxisChangeListener();
        axis.addChangeListener(listener);

        axis.setAxisLineVisible(false);
        assertFalse(axis.isAxisLineVisible());
        assertTrue(listener.isChanged());

        listener.changed = false;
        axis.setAxisLinePaint(Color.blue);
        assertEquals(Color.blue, axis.getAxisLinePaint());
        assertTrue(listener.isChanged());

        listener.changed = false;
        Stroke stroke = new BasicStroke(2.0f);
        axis.setAxisLineStroke(stroke);
        assertEquals(stroke, axis.getAxisLineStroke());
        assertTrue(listener.isChanged());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetAxisLinePaintNull() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        axis.setAxisLinePaint(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetAxisLineStrokeNull() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        axis.setAxisLineStroke(null);
    }

    @Test
    public void testTickLabelProperties() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        DummyAxisChangeListener listener = new DummyAxisChangeListener();
        axis.addChangeListener(listener);

        axis.setTickLabelsVisible(false);
        assertFalse(axis.isTickLabelsVisible());
        assertTrue(listener.isChanged());

        listener.changed = false;
        axis.setTickLabelsVisible(false);
        assertFalse(listener.isChanged());

        listener.changed = false;
        Font font = new Font("Courier", Font.PLAIN, 12);
        axis.setTickLabelFont(font);
        assertEquals(font, axis.getTickLabelFont());
        assertTrue(listener.isChanged());

        listener.changed = false;
        axis.setTickLabelFont(font);
        assertFalse(listener.isChanged());

        listener.changed = false;
        axis.setTickLabelPaint(Color.green);
        assertEquals(Color.green, axis.getTickLabelPaint());
        assertTrue(listener.isChanged());

        listener.changed = false;
        RectangleInsets insets = new RectangleInsets(1.0, 1.0, 1.0, 1.0);
        axis.setTickLabelInsets(insets);
        assertEquals(insets, axis.getTickLabelInsets());
        assertTrue(listener.isChanged());

        listener.changed = false;
        axis.setTickLabelInsets(insets);
        assertFalse(listener.isChanged());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetTickLabelFontNull() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        axis.setTickLabelFont(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetTickLabelPaintNull() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        axis.setTickLabelPaint(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetTickLabelInsetsNull() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        axis.setTickLabelInsets(null);
    }

    @Test
    public void testTickMarkProperties() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        DummyAxisChangeListener listener = new DummyAxisChangeListener();
        axis.addChangeListener(listener);

        axis.setTickMarksVisible(false);
        assertFalse(axis.isTickMarksVisible());
        assertTrue(listener.isChanged());

        listener.changed = false;
        axis.setTickMarksVisible(false);
        assertFalse(listener.isChanged());

        listener.changed = false;
        axis.setTickMarkInsideLength(3.0f);
        assertEquals(3.0f, axis.getTickMarkInsideLength(), 0.0001f);
        assertTrue(listener.isChanged());

        listener.changed = false;
        axis.setTickMarkOutsideLength(4.0f);
        assertEquals(4.0f, axis.getTickMarkOutsideLength(), 0.0001f);
        assertTrue(listener.isChanged());

        listener.changed = false;
        Stroke stroke = new BasicStroke(1.5f);
        axis.setTickMarkStroke(stroke);
        assertEquals(stroke, axis.getTickMarkStroke());
        assertTrue(listener.isChanged());

        listener.changed = false;
        axis.setTickMarkStroke(stroke);
        assertFalse(listener.isChanged());

        listener.changed = false;
        axis.setTickMarkPaint(Color.orange);
        assertEquals(Color.orange, axis.getTickMarkPaint());
        assertTrue(listener.isChanged());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetTickMarkStrokeNull() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        axis.setTickMarkStroke(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetTickMarkPaintNull() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        axis.setTickMarkPaint(null);
    }

    @Test
    public void testPlotAndFixedDimension() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        Plot plot = new org.jfree.chart.plot.CategoryPlot();
        axis.setPlot(plot);
        assertEquals(plot, axis.getPlot());

        axis.setFixedDimension(100.0);
        assertEquals(100.0, axis.getFixedDimension(), 0.0001);
    }

    @Test
    public void testListenersManagement() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        DummyAxisChangeListener listener = new DummyAxisChangeListener();
        assertFalse(axis.hasListener(listener));

        axis.addChangeListener(listener);
        assertTrue(axis.hasListener(listener));

        axis.removeChangeListener(listener);
        assertFalse(axis.hasListener(listener));
    }

    @Test
    public void testEqualsAndClone() throws Throwable {
        ConcreteAxis axis1 = new ConcreteAxis("Axis");
        ConcreteAxis axis2 = new ConcreteAxis("Axis");

        assertTrue(axis1.equals(axis1));
        assertTrue(axis1.equals(axis2));
        assertFalse(axis1.equals(null));
        assertFalse(axis1.equals("Some String"));

        axis2.setLabel("Different");
        assertFalse(axis1.equals(axis2));

        axis2 = (ConcreteAxis) axis1.clone();
        assertTrue(axis1.equals(axis2));
    }

    @Test
    public void testDrawLabelAndEnclosure() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Label");
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D plotArea = new Rectangle2D.Double(0, 0, 400, 300);
        Rectangle2D dataArea = new Rectangle2D.Double(50, 50, 300, 200);
        AxisState state = new AxisState(250.0);

        AxisState resTop = axis.drawLabel("Label", g2, plotArea, dataArea, RectangleEdge.TOP, state, null);
        assertNotNull(resTop);

        AxisState resBottom = axis.drawLabel("Label", g2, plotArea, dataArea, RectangleEdge.BOTTOM, state, null);
        assertNotNull(resBottom);

        AxisState resLeft = axis.drawLabel("Label", g2, plotArea, dataArea, RectangleEdge.LEFT, state, null);
        assertNotNull(resLeft);

        AxisState resRight = axis.drawLabel("Label", g2, plotArea, dataArea, RectangleEdge.RIGHT, state, null);
        assertNotNull(resRight);

        // Test with null or empty label
        AxisState resNull = axis.drawLabel(null, g2, plotArea, dataArea, RectangleEdge.TOP, state, null);
        assertEquals(state, resNull);

        AxisState resEmpty = axis.drawLabel("", g2, plotArea, dataArea, RectangleEdge.TOP, state, null);
        assertEquals(state, resEmpty);

        // Test with plot state and entities
        ChartRenderingInfo cri = new ChartRenderingInfo();
        PlotRenderingInfo pri = new PlotRenderingInfo(cri);
        axis.setLabelToolTip("Tip");
        axis.setLabelURL("URL");
        AxisState resWithPlot = axis.drawLabel("Label", g2, plotArea, dataArea, RectangleEdge.TOP, state, pri);
        assertNotNull(resWithPlot);

        // Test enclosure methods via reflection or calling directly if accessible
        Rectangle2D enclosure = axis.getLabelEnclosure(g2, RectangleEdge.TOP);
        assertNotNull(enclosure);
        
        Rectangle2D enclosureLeft = axis.getLabelEnclosure(g2, RectangleEdge.LEFT);
        assertNotNull(enclosureLeft);
        
        axis.setLabel("");
        Rectangle2D enclosureEmpty = axis.getLabelEnclosure(g2, RectangleEdge.TOP);
        assertNotNull(enclosureEmpty);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDrawLabelNullState() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Label");
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        axis.drawLabel("Label", g2, new Rectangle2D.Double(), new Rectangle2D.Double(), RectangleEdge.TOP, null, null);
    }

    @Test
    public void testDrawAxisLine() throws Throwable {
        ConcreteAxis axis = new ConcreteAxis("Axis");
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(10, 10, 80, 80);

        axis.drawAxisLine(g2, 10.0, dataArea, RectangleEdge.TOP);
        axis.drawAxisLine(g2, 90.0, dataArea, RectangleEdge.BOTTOM);
        axis.drawAxisLine(g2, 10.0, dataArea, RectangleEdge.LEFT);
        axis.drawAxisLine(g2, 90.0, dataArea, RectangleEdge.RIGHT);
        assertTrue(true); // executed without exception
    }
}