package org.jfree.chart.plot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.util.Layer;
import org.jfree.chart.util.PlotOrientation;
import org.jfree.chart.util.RectangleInsets;
import org.jfree.data.Range;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYDataset;

public class XYPlotTest {

    @Test
    public void testConstructorsAndDefaults() throws Throwable {
        XYPlot plot1 = new XYPlot();
        assertNotNull(plot1.getPlotType());
        assertEquals(PlotOrientation.VERTICAL, plot1.getOrientation());
        assertEquals(1, plot1.getWeight());
        assertNotNull(plot1.getAxisOffset());
        assertNull(plot1.getDataset());
        assertNull(plot1.getDomainAxis());
        assertNull(plot1.getRangeAxis());
        assertNull(plot1.getRenderer());
        assertTrue(plot1.isDomainGridlinesVisible());
        assertTrue(plot1.isRangeGridlinesVisible());
        assertFalse(plot1.isDomainMinorGridlinesVisible());
        assertFalse(plot1.isRangeMinorGridlinesVisible());
        assertFalse(plot1.isDomainZeroBaselineVisible());
        assertFalse(plot1.isRangeZeroBaselineVisible());
        assertFalse(plot1.isDomainCrosshairVisible());
        assertFalse(plot1.isRangeCrosshairVisible());
        assertTrue(plot1.isDomainZoomable());
        assertTrue(plot1.isRangeZoomable());
        assertFalse(plot1.canSelectByPoint());
        assertTrue(plot1.canSelectByRegion());

        DefaultXYDataset dataset = new DefaultXYDataset();
        NumberAxis domainAxis = new NumberAxis("X");
        NumberAxis rangeAxis = new NumberAxis("Y");
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();

        XYPlot plot2 = new XYPlot(dataset, domainAxis, rangeAxis, renderer);
        assertEquals(dataset, plot2.getDataset());
        assertEquals(domainAxis, plot2.getDomainAxis());
        assertEquals(rangeAxis, plot2.getRangeAxis());
        assertEquals(renderer, plot2.getRenderer());
    }

    @Test
    public void testOrientation() throws Throwable {
        XYPlot plot = new XYPlot();
        plot.setOrientation(PlotOrientation.HORIZONTAL);
        assertEquals(PlotOrientation.HORIZONTAL, plot.getOrientation());

        try {
            plot.setOrientation(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'orientation'"));
        }
    }

    @Test
    public void testAxisOffset() throws Throwable {
        XYPlot plot = new XYPlot();
        RectangleInsets insets = new RectangleInsets(1.0, 2.0, 3.0, 4.0);
        plot.setAxisOffset(insets);
        assertEquals(insets, plot.getAxisOffset());

        try {
            plot.setAxisOffset(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'offset'"));
        }
    }

    @Test
    public void testDomainAxesManagement() throws Throwable {
        XYPlot plot = new XYPlot();
        NumberAxis axis1 = new NumberAxis("X1");
        NumberAxis axis2 = new NumberAxis("X2");

        plot.setDomainAxis(axis1);
        assertEquals(axis1, plot.getDomainAxis(0));
        assertEquals(axis1, plot.getDomainAxis());

        plot.setDomainAxis(1, axis2);
        assertEquals(axis2, plot.getDomainAxis(1));
        assertEquals(2, plot.getDomainAxisCount());

        plot.setDomainAxisLocation(AxisLocation.TOP_OR_RIGHT);
        assertEquals(AxisLocation.TOP_OR_RIGHT, plot.getDomainAxisLocation());

        plot.setDomainAxisLocation(1, AxisLocation.BOTTOM_OR_RIGHT);
        assertEquals(AxisLocation.BOTTOM_OR_RIGHT, plot.getDomainAxisLocation(1));

        try {
            plot.setDomainAxisLocation(0, null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'location'"));
        }

        plot.clearDomainAxes();
        assertEquals(0, plot.getDomainAxisCount());
    }

    @Test
    public void testRangeAxesManagement() throws Throwable {
        XYPlot plot = new XYPlot();
        NumberAxis axis1 = new NumberAxis("Y1");
        NumberAxis axis2 = new NumberAxis("Y2");

        plot.setRangeAxis(axis1);
        assertEquals(axis1, plot.getRangeAxis(0));
        assertEquals(axis1, plot.getRangeAxis());

        plot.setRangeAxis(1, axis2);
        assertEquals(axis2, plot.getRangeAxis(1));
        assertEquals(2, plot.getRangeAxisCount());

        plot.setRangeAxisLocation(AxisLocation.TOP_OR_RIGHT);
        assertEquals(AxisLocation.TOP_OR_RIGHT, plot.getRangeAxisLocation());

        plot.setRangeAxisLocation(1, AxisLocation.BOTTOM_OR_RIGHT);
        assertEquals(AxisLocation.BOTTOM_OR_RIGHT, plot.getRangeAxisLocation(1));

        try {
            plot.setRangeAxisLocation(0, null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'location'"));
        }

        plot.clearRangeAxes();
        assertEquals(0, plot.getRangeAxisCount());
    }

    @Test
    public void testDatasetsAndRendererManagement() throws Throwable {
        XYPlot plot = new XYPlot();
        DefaultXYDataset ds1 = new DefaultXYDataset();
        DefaultXYDataset ds2 = new DefaultXYDataset();
        XYLineAndShapeRenderer r1 = new XYLineAndShapeRenderer();
        XYLineAndShapeRenderer r2 = new XYLineAndShapeRenderer();

        plot.setDataset(ds1);
        plot.setDataset(1, ds2);
        assertEquals(ds1, plot.getDataset(0));
        assertEquals(ds2, plot.getDataset(1));
        assertEquals(2, plot.getDatasetCount());
        assertEquals(0, plot.indexOf(ds1));
        assertEquals(1, plot.indexOf(ds2));
        assertEquals(-1, plot.indexOf(new DefaultXYDataset()));

        plot.setRenderer(r1);
        plot.setRenderer(1, r2);
        assertEquals(r1, plot.getRenderer(0));
        assertEquals(r2, plot.getRenderer(1));
        assertEquals(2, plot.getRendererCount());
        assertEquals(0, plot.getIndexOf(r1));
        assertEquals(r1, plot.getRendererForDataset(ds1));
        assertEquals(r2, plot.getRendererForDataset(ds2));

        List axisIndices = new ArrayList<Integer>();
        axisIndices.add(new Integer(0));
        plot.mapDatasetToDomainAxes(0, axisIndices);
        plot.mapDatasetToRangeAxes(0, axisIndices);

        try {
            plot.mapDatasetToDomainAxes(-1, axisIndices);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Requires 'index' >= 0"));
        }

        try {
            plot.mapDatasetToDomainAxes(0, null);
        } catch (Exception e) {
            fail("Should accept null list");
        }

        try {
            List emptyList = new ArrayList<Integer>();
            plot.mapDatasetToDomainAxes(0, emptyList);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Empty list not permitted"));
        }

        try {
            List invalidList = new ArrayList<String>();
            invalidList.add("notAnInteger");
            plot.mapDatasetToDomainAxes(0, invalidList);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Indices must be Integer instances"));
        }

        try {
            List duplicateList = new ArrayList<Integer>();
            duplicateList.add(new Integer(0));
            duplicateList.add(new Integer(0));
            plot.mapDatasetToDomainAxes(0, duplicateList);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Indices must be unique"));
        }
    }

    @Test
    public void testGridlinesAndBasesAndCrosshairs() throws Throwable {
        XYPlot plot = new XYPlot();

        plot.setDomainGridlinesVisible(false);
        assertFalse(plot.isDomainGridlinesVisible());
        plot.setDomainGridlinesVisible(true);
        assertTrue(plot.isDomainGridlinesVisible());

        plot.setRangeGridlinesVisible(false);
        assertFalse(plot.isRangeGridlinesVisible());
        plot.setRangeGridlinesVisible(true);
        assertTrue(plot.isRangeGridlinesVisible());

        plot.setDomainMinorGridlinesVisible(true);
        assertTrue(plot.isDomainMinorGridlinesVisible());
        plot.setRangeMinorGridlinesVisible(true);
        assertTrue(plot.isRangeMinorGridlinesVisible());

        Stroke stroke = new BasicStroke(1.0f);
        plot.setDomainGridlineStroke(stroke);
        assertEquals(stroke, plot.getDomainGridlineStroke());
        plot.setDomainMinorGridlineStroke(stroke);
        assertEquals(stroke, plot.getDomainMinorGridlineStroke());
        plot.setRangeGridlineStroke(stroke);
        assertEquals(stroke, plot.getRangeGridlineStroke());
        plot.setRangeMinorGridlineStroke(stroke);
        assertEquals(stroke, plot.getRangeMinorGridlineStroke());

        Paint paint = Color.RED;
        plot.setDomainGridlinePaint(paint);
        assertEquals(paint, plot.getDomainGridlinePaint());
        plot.setDomainMinorGridlinePaint(paint);
        assertEquals(paint, plot.getDomainMinorGridlinePaint());
        plot.setRangeGridlinePaint(paint);
        assertEquals(paint, plot.getRangeGridlinePaint());
        plot.setRangeMinorGridlinePaint(paint);
        assertEquals(paint, plot.getRangeMinorGridlinePaint());

        plot.setDomainZeroBaselineVisible(true);
        assertTrue(plot.isDomainZeroBaselineVisible());
        plot.setDomainZeroBaselineStroke(stroke);
        assertEquals(stroke, plot.getDomainZeroBaselineStroke());
        plot.setDomainZeroBaselinePaint(paint);
        assertEquals(paint, plot.getDomainZeroBaselinePaint());

        plot.setRangeZeroBaselineVisible(true);
        assertTrue(plot.isRangeZeroBaselineVisible());
        plot.setRangeZeroBaselineStroke(stroke);
        assertEquals(stroke, plot.getRangeZeroBaselineStroke());
        plot.setRangeZeroBaselinePaint(paint);
        assertEquals(paint, plot.getRangeZeroBaselinePaint());

        plot.setDomainCrosshairVisible(true);
        assertTrue(plot.isDomainCrosshairVisible());
        plot.setDomainCrosshairLockedOnData(false);
        assertFalse(plot.isDomainCrosshairLockedOnData());
        plot.setDomainCrosshairValue(10.5);
        assertEquals(10.5, plot.getDomainCrosshairValue(), 0.001);
        plot.setDomainCrosshairStroke(stroke);
        assertEquals(stroke, plot.getDomainCrosshairStroke());
        plot.setDomainCrosshairPaint(paint);
        assertEquals(paint, plot.getDomainCrosshairPaint());

        plot.setRangeCrosshairVisible(true);
        assertTrue(plot.isRangeCrosshairVisible());
        plot.setRangeCrosshairLockedOnData(false);
        assertFalse(plot.isRangeCrosshairLockedOnData());
        plot.setRangeCrosshairValue(20.5);
        assertEquals(20.5, plot.getRangeCrosshairValue(), 0.001);
        plot.setRangeCrosshairStroke(stroke);
        assertEquals(stroke, plot.getRangeCrosshairStroke());
        plot.setRangeCrosshairPaint(paint);
        assertEquals(paint, plot.getRangeCrosshairPaint());

        try {
            plot.setDomainGridlineStroke(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setDomainMinorGridlineStroke(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setDomainGridlinePaint(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setDomainMinorGridlinePaint(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setRangeGridlineStroke(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setRangeMinorGridlinePaint(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setDomainZeroBaselineStroke(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setDomainZeroBaselinePaint(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setRangeZeroBaselineStroke(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setRangeZeroBaselinePaint(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setDomainCrosshairStroke(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setDomainCrosshairPaint(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setRangeCrosshairStroke(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setRangeCrosshairPaint(null);
            fail();
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testQuadrantAndTickBands() throws Throwable {
        XYPlot plot = new XYPlot();
        Point2D origin = new Point2D.Double(1.0, 2.0);
        plot.setQuadrantOrigin(origin);
        assertEquals(origin, plot.getQuadrantOrigin());

        try {
            plot.setQuadrantOrigin(null);
            fail();
        } catch (IllegalArgumentException e) {}

        Paint paint = Color.BLUE;
        plot.setQuadrantPaint(0, paint);
        assertEquals(paint, plot.getQuadrantPaint(0));

        try {
            plot.getQuadrantPaint(-1);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.getQuadrantPaint(4);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.setQuadrantPaint(5, paint);
            fail();
        } catch (IllegalArgumentException e) {}

        plot.setDomainTickBandPaint(paint);
        assertEquals(paint, plot.getDomainTickBandPaint());
        plot.setRangeTickBandPaint(paint);
        assertEquals(paint, plot.getRangeTickBandPaint());
    }

    @Test
    public void testMarkersAndAnnotations() throws Throwable {
        XYPlot plot = new XYPlot();
        ValueMarker marker = new ValueMarker(10.0);
        plot.addDomainMarker(marker);
        plot.addRangeMarker(marker);
        assertNotNull(plot.getDomainMarkers(Layer.FOREGROUND));
        assertNotNull(plot.getRangeMarkers(Layer.FOREGROUND));

        assertTrue(plot.removeDomainMarker(marker));
        assertTrue(plot.removeRangeMarker(marker));
        assertFalse(plot.removeDomainMarker(marker));
        assertFalse(plot.removeRangeMarker(marker));

        try {
            plot.addDomainMarker(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.removeRangeMarker(null);
            fail();
        } catch (IllegalArgumentException e) {}

        XYTextAnnotation annotation = new XYTextAnnotation("Test", 1.0, 2.0);
        plot.addAnnotation(annotation);
        assertEquals(1, plot.getAnnotations().size());
        assertTrue(plot.removeAnnotation(annotation));
        assertEquals(0, plot.getAnnotations().size());
        assertFalse(plot.removeAnnotation(annotation));

        try {
            plot.addAnnotation(null);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            plot.removeAnnotation(null);
            fail();
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testZoomAndPanAndWeight() throws Throwable {
        XYPlot plot = new XYPlot();
        plot.setWeight(3);
        assertEquals(3, plot.getWeight());

        plot.setDomainPannable(true);
        assertTrue(plot.isDomainPannable());
        plot.setRangePannable(true);
        assertTrue(plot.isRangePannable());

        plot.zoomDomainAxes(0.5, null, null);
        plot.zoomDomainAxes(0.1, 0.9, null, null);
        plot.zoomRangeAxes(0.5, null, null);
        plot.zoomRangeAxes(0.1, 0.9, null, null);

        plot.panDomainAxes(0.1, null, null);
        plot.panRangeAxes(0.1, null, null);
    }

    @Test
    public void testEqualsAndClone() throws Throwable {
        XYPlot plot1 = new XYPlot();
        XYPlot plot2 = new XYPlot();
        assertEquals(plot1, plot2);
        assertEquals(plot1, plot1);
        assertFalse(plot1.equals(null));
        assertFalse(plot1.equals("NotAXYPlot"));

        plot1.setWeight(5);
        assertFalse(plot1.equals(plot2));
        plot2.setWeight(5);
        assertEquals(plot1, plot2);

        XYPlot clone = (XYPlot) plot1.clone();
        assertEquals(plot1, clone);
        assertFalse(plot1 == clone);
    }

    @Test
    public void testDrawAndRenderingEdgeCases() throws Throwable {
        XYPlot plot = new XYPlot();
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 5, 5);

        // Test area too small to draw
        plot.draw(g2, area, null, null, null);

        Rectangle2D largeArea = new Rectangle2D.Double(0, 0, 200, 200);
        plot.setDomainAxis(new NumberAxis("X"));
        plot.setRangeAxis(new NumberAxis("Y"));
        plot.setRenderer(new XYLineAndShapeRenderer());
        
        plot.draw(g2, largeArea, new Point2D.Double(10, 10), null, null);
        plot.handleClick(10, 10, new PlotRenderingInfo(null));
    }
}