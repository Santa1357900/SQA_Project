package org.jfree.chart.plot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.annotations.CategoryAnnotation;
import org.jfree.chart.annotations.TextAnnotation;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.AxisSpace;
import org.jfree.chart.axis.CategoryAnchor;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.util.Layer;
import org.jfree.chart.util.RectangleInsets;
import org.jfree.chart.util.SortOrder;
import org.jfree.data.Range;
import org.jfree.data.category.DefaultCategoryDataset;

public class CategoryPlotTest {

    @Test
    public void testConstructorsAndDefaults() throws Throwable {
        CategoryPlot plot1 = new CategoryPlot();
        assertNotNull(plot1);
        assertEquals(PlotOrientation.VERTICAL, plot1.getOrientation());
        assertNotNull(plot1.getAxisOffset());
        assertEquals(CategoryPlot.DEFAULT_DOMAIN_GRIDLINES_VISIBLE, plot1.isDomainGridlinesVisible());
        assertEquals(CategoryPlot.DEFAULT_RANGE_GRIDLINES_VISIBLE, plot1.isRangeGridlinesVisible());
        assertEquals(CategoryPlot.DEFAULT_CROSSHAIR_VISIBLE, plot1.isRangeCrosshairVisible());

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "R1", "C1");
        CategoryAxis domainAxis = new CategoryAxis("Category");
        ValueAxis rangeAxis = new NumberAxis("Value");
        BarRenderer renderer = new BarRenderer();

        CategoryPlot plot2 = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        assertNotNull(plot2);
        assertEquals(dataset, plot2.getDataset());
        assertEquals(domainAxis, plot2.getDomainAxis());
        assertEquals(rangeAxis, plot2.getRangeAxis());
        assertEquals(renderer, plot2.getRenderer());
    }

    @Test
    public void testPlotType() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertNotNull(plot.getPlotType());
    }

    @Test
    public void testOrientation() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setOrientation(PlotOrientation.HORIZONTAL);
        assertEquals(PlotOrientation.HORIZONTAL, plot.getOrientation());

        try {
            plot.setOrientation(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'orientation' argument"));
        }
    }

    @Test
    public void testAxisOffset() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        RectangleInsets insets = new RectangleInsets(2.0, 2.0, 2.0, 2.0);
        plot.setAxisOffset(insets);
        assertEquals(insets, plot.getAxisOffset());

        try {
            plot.setAxisOffset(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'offset' argument"));
        }
    }

    @Test
    public void testDomainAxisManagement() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        CategoryAxis axis1 = new CategoryAxis("Axis 1");
        CategoryAxis axis2 = new CategoryAxis("Axis 2");

        plot.setDomainAxis(0, axis1);
        assertEquals(axis1, plot.getDomainAxis(0));

        plot.setDomainAxis(1, axis2, true);
        assertEquals(axis2, plot.getDomainAxis(1));
        assertEquals(2, plot.getDomainAxisCount());
        assertEquals(1, plot.getDomainAxisIndex(axis2));

        try {
            plot.getDomainAxisIndex(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'axis' argument"));
        }

        plot.setDomainAxes(new CategoryAxis[] { axis2, axis1 });
        assertEquals(axis2, plot.getDomainAxis(0));

        plot.clearDomainAxes();
        assertEquals(0, plot.getDomainAxisCount());
    }

    @Test
    public void testDomainAxisLocation() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setDomainAxisLocation(AxisLocation.TOP_OR_RIGHT);
        assertEquals(AxisLocation.TOP_OR_RIGHT, plot.getDomainAxisLocation());
        assertEquals(AxisLocation.TOP_OR_RIGHT, plot.getDomainAxisLocation(0));

        plot.setDomainAxisLocation(0, AxisLocation.BOTTOM_OR_LEFT, true);
        assertEquals(AxisLocation.BOTTOM_OR_LEFT, plot.getDomainAxisLocation(0));

        try {
            plot.setDomainAxisLocation(0, null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'location'"));
        }

        assertNotNull(plot.getDomainAxisEdge());
        assertNotNull(plot.getDomainAxisEdge(0));
    }

    @Test
    public void testRangeAxisManagement() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        ValueAxis axis1 = new NumberAxis("Range 1");
        ValueAxis axis2 = new NumberAxis("Range 2");

        plot.setRangeAxis(0, axis1);
        assertEquals(axis1, plot.getRangeAxis(0));

        plot.setRangeAxis(1, axis2, true);
        assertEquals(axis2, plot.getRangeAxis(1));
        assertEquals(2, plot.getRangeAxisCount());
        assertEquals(1, plot.getRangeAxisIndex(axis2));

        try {
            plot.getRangeAxisIndex(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'axis' argument"));
        }

        plot.setRangeAxes(new ValueAxis[] { axis2, axis1 });
        assertEquals(axis2, plot.getRangeAxis(0));

        plot.clearRangeAxes();
        assertEquals(0, plot.getRangeAxisCount());
    }

    @Test
    public void testRangeAxisLocation() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setRangeAxisLocation(AxisLocation.TOP_OR_RIGHT);
        assertEquals(AxisLocation.TOP_OR_RIGHT, plot.getRangeAxisLocation());
        assertEquals(AxisLocation.TOP_OR_RIGHT, plot.getRangeAxisLocation(0));

        plot.setRangeAxisLocation(0, AxisLocation.TOP_OR_LEFT, true);
        assertEquals(AxisLocation.TOP_OR_LEFT, plot.getRangeAxisLocation(0));

        try {
            plot.setRangeAxisLocation(0, null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'location'"));
        }

        assertNotNull(plot.getRangeAxisEdge());
        assertNotNull(plot.getRangeAxisEdge(0));
    }

    @Test
    public void testDatasetManagement() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        DefaultCategoryDataset ds1 = new DefaultCategoryDataset();
        ds1.addValue(10.0, "R1", "C1");
        DefaultCategoryDataset ds2 = new DefaultCategoryDataset();
        ds2.addValue(20.0, "R2", "C2");

        plot.setDataset(0, ds1);
        assertEquals(ds1, plot.getDataset(0));
        assertEquals(ds1, plot.getDataset());

        plot.setDataset(1, ds2);
        assertEquals(ds2, plot.getDataset(1));
        assertEquals(2, plot.getDatasetCount());

        plot.mapDatasetToDomainAxis(1, 0);
        assertNotNull(plot.getDomainAxisForDataset(1));

        plot.mapDatasetToRangeAxis(1, 0);
        assertNotNull(plot.getRangeAxisForDataset(1));

        assertNotNull(plot.getCategories());
        assertNotNull(plot.getCategoriesForAxis(plot.getDomainAxis()));
    }

    @Test
    public void testRendererManagement() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        BarRenderer r1 = new BarRenderer();
        BarRenderer r2 = new BarRenderer();

        plot.setRenderer(0, r1);
        assertEquals(r1, plot.getRenderer(0));
        assertEquals(r1, plot.getRenderer());

        plot.setRenderer(1, r2, true);
        assertEquals(r2, plot.getRenderer(1));
        assertEquals(0, plot.getIndexOf(r1));

        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        ds.addValue(5.0, "S1", "Cat1");
        plot.setDataset(0, ds);
        assertEquals(r1, plot.getRendererForDataset(ds));
        assertNull(plot.getRendererForDataset(new DefaultCategoryDataset()));

        plot.setRenderers(new CategoryItemRenderer[] { r2, r1 });
        assertEquals(r2, plot.getRenderer(0));
    }

    @Test
    public void testRenderingOrder() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        assertEquals(DatasetRenderingOrder.FORWARD, plot.getDatasetRenderingOrder());

        try {
            plot.setDatasetRenderingOrder(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'order' argument"));
        }

        plot.setColumnRenderingOrder(SortOrder.DESCENDING);
        assertEquals(SortOrder.DESCENDING, plot.getColumnRenderingOrder());

        try {
            plot.setColumnRenderingOrder(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'order' argument"));
        }

        plot.setRowRenderingOrder(SortOrder.DESCENDING);
        assertEquals(SortOrder.DESCENDING, plot.getRowRenderingOrder());

        try {
            plot.setRowRenderingOrder(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'order' argument"));
        }
    }

    @Test
    public void testDomainGridlines() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setDomainGridlinesVisible(true);
        assertTrue(plot.isDomainGridlinesVisible());
        plot.setDomainGridlinesVisible(true); // duplicate to cover branch

        plot.setDomainGridlinePosition(CategoryAnchor.END);
        assertEquals(CategoryAnchor.END, plot.getDomainGridlinePosition());

        try {
            plot.setDomainGridlinePosition(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'position' argument"));
        }

        Stroke stroke = new BasicStroke(1.0f);
        plot.setDomainGridlineStroke(stroke);
        assertEquals(stroke, plot.getDomainGridlineStroke());

        try {
            plot.setDomainGridlineStroke(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'stroke'"));
        }

        Paint paint = Color.BLACK;
        plot.setDomainGridlinePaint(paint);
        assertEquals(paint, plot.getDomainGridlinePaint());

        try {
            plot.setDomainGridlinePaint(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'paint' argument"));
        }
    }

    @Test
    public void testRangeGridlines() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setRangeGridlinesVisible(false);
        assertFalse(plot.isRangeGridlinesVisible());
        plot.setRangeGridlinesVisible(false); // duplicate branch

        Stroke stroke = new BasicStroke(1.0f);
        plot.setRangeGridlineStroke(stroke);
        assertEquals(stroke, plot.getRangeGridlineStroke());

        try {
            plot.setRangeGridlineStroke(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'stroke' argument"));
        }

        Paint paint = Color.BLACK;
        plot.setRangeGridlinePaint(paint);
        assertEquals(paint, plot.getRangeGridlinePaint());

        try {
            plot.setRangeGridlinePaint(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'paint' argument"));
        }
    }

    @Test
    public void testLegendItems() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertNotNull(plot.getLegendItems());

        LegendItemCollection collection = new LegendItemCollection();
        plot.setFixedLegendItems(collection);
        assertEquals(collection, plot.getFixedLegendItems());
    }

    @Test
    public void testZoomingAndPanning() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertFalse(plot.isDomainZoomable());
        assertTrue(plot.isRangeZoomable());

        plot.zoom(0.5);
        plot.zoom(0.0);

        plot.zoomRangeAxes(1.2, null, null);
        plot.zoomRangeAxes(0.8, 1.2, null, null);
        plot.zoomDomainAxes(1.0, null, null);
        plot.zoomDomainAxes(0.0, 1.0, null, null);
        plot.zoomDomainAxes(1.0, null, null, true);
    }

    @Test
    public void testMarkersAndAnnotations() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        CategoryMarker dMarker = new CategoryMarker("C1");
        ValueMarker rMarker = new ValueMarker(10.0);

        plot.addDomainMarker(dMarker);
        plot.addDomainMarker(0, dMarker, Layer.BACKGROUND);
        plot.addDomainMarker(0, dMarker, Layer.FOREGROUND, false);
        assertNotNull(plot.getDomainMarkers(Layer.FOREGROUND));
        assertNotNull(plot.getDomainMarkers(0, Layer.BACKGROUND));
        assertTrue(plot.removeDomainMarker(dMarker));
        assertTrue(plot.removeDomainMarker(0, dMarker, Layer.BACKGROUND));
        plot.clearDomainMarkers();
        plot.clearDomainMarkers(0);

        plot.addRangeMarker(rMarker);
        plot.addRangeMarker(0, rMarker, Layer.BACKGROUND);
        plot.addRangeMarker(0, rMarker, Layer.FOREGROUND, false);
        assertNotNull(plot.getRangeMarkers(Layer.FOREGROUND));
        assertNotNull(plot.getRangeMarkers(0, Layer.BACKGROUND));
        assertTrue(plot.removeRangeMarker(rMarker));
        assertTrue(plot.removeRangeMarker(0, rMarker, Layer.BACKGROUND));
        plot.clearRangeMarkers();
        plot.clearRangeMarkers(0);

        try {
            plot.removeRangeMarker(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'marker' argument"));
        }

        CategoryAnnotation annotation = new TextAnnotation("Test", "C1", 1.0);
        plot.addAnnotation(annotation);
        plot.addAnnotation(annotation, false);
        assertNotNull(plot.getAnnotations());
        assertTrue(plot.removeAnnotation(annotation));
        assertTrue(plot.removeAnnotation(annotation, false));

        try {
            plot.addAnnotation(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'annotation' argument"));
        }

        try {
            plot.removeAnnotation(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'annotation' argument"));
        }

        plot.clearAnnotations();
    }

    @Test
    public void testCrosshairProperties() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setRangeCrosshairVisible(true);
        assertTrue(plot.isRangeCrosshairVisible());
        plot.setRangeCrosshairVisible(true);

        plot.setRangeCrosshairLockedOnData(false);
        assertFalse(plot.isRangeCrosshairLockedOnData());
        plot.setRangeCrosshairLockedOnData(false);

        plot.setRangeCrosshairValue(15.0);
        assertEquals(15.0, plot.getRangeCrosshairValue(), 0.001);
        plot.setRangeCrosshairValue(20.0, false);

        Stroke stroke = new BasicStroke(2.0f);
        plot.setRangeCrosshairStroke(stroke);
        assertEquals(stroke, plot.getRangeCrosshairStroke());

        try {
            plot.setRangeCrosshairStroke(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'stroke' argument"));
        }

        Paint paint = Color.RED;
        plot.setRangeCrosshairPaint(paint);
        assertEquals(paint, plot.getRangeCrosshairPaint());

        try {
            plot.setRangeCrosshairPaint(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'paint' argument"));
        }
    }

    @Test
    public void testWeightAndSpace() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setWeight(5);
        assertEquals(5, plot.getWeight());

        AxisSpace space = new AxisSpace();
        plot.setFixedDomainAxisSpace(space);
        assertEquals(space, plot.getFixedDomainAxisSpace());
        plot.setFixedDomainAxisSpace(null, false);

        plot.setFixedRangeAxisSpace(space);
        assertEquals(space, plot.getFixedRangeAxisSpace());
        plot.setFixedRangeAxisSpace(null, false);

        plot.setDrawSharedDomainAxis(true);
        assertTrue(plot.getDrawSharedDomainAxis());

        plot.setAnchorValue(100.0);
        assertEquals(100.0, plot.getAnchorValue(), 0.001);
        plot.setAnchorValue(50.0, false);
    }

    @Test
    public void testGetDataRange() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        ValueAxis axis = plot.getRangeAxis();
        Range range = plot.getDataRange(axis);
        assertNull(range);
    }

    @Test
    public void testEqualsAndClone() throws Throwable {
        CategoryPlot plot1 = new CategoryPlot();
        CategoryPlot plot2 = new CategoryPlot();

        assertTrue(plot1.equals(plot1));
        assertTrue(plot1.equals(plot2));
        assertFalse(plot1.equals(null));
        assertFalse(plot1.equals("Some String"));

        plot2.setWeight(10);
        assertFalse(plot1.equals(plot2));

        CategoryPlot clone = (CategoryPlot) plot1.clone();
        assertNotNull(clone);
        assertTrue(plot1.equals(clone));
    }
}