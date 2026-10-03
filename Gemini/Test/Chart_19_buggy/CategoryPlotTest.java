package org.jfree.chart.plot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.List;

import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.annotations.CategoryAnnotation;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.AxisSpace;
import org.jfree.chart.axis.CategoryAnchor;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.event.PlotChangeEvent;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.util.Layer;
import org.jfree.chart.util.RectangleInsets;
import org.jfree.chart.util.SortOrder;
import org.jfree.data.Range;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.junit.Test;

public class CategoryPlotTest {

    @Test
    public void testConstructorsAndDefaults() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertNotNull(plot.getPlotType());
        assertEquals(PlotOrientation.VERTICAL, plot.getOrientation());
        assertNotNull(plot.getAxisOffset());
        assertNull(plot.getDataset());
        assertNull(plot.getDomainAxis());
        assertNull(plot.getRangeAxis());
        assertNull(plot.getRenderer());
        assertFalse(plot.isDomainGridlinesVisible());
        assertTrue(plot.isRangeGridlinesVisible());
        assertEquals(CategoryAnchor.MIDDLE, plot.getDomainGridlinePosition());
        assertFalse(plot.isRangeCrosshairVisible());
        assertTrue(plot.isRangeCrosshairLockedOnData());
        assertEquals(0.0, plot.getRangeCrosshairValue(), 0.00001);
        assertFalse(plot.isDomainZoomable());
        assertTrue(plot.isRangeZoomable());
        assertEquals(0, plot.getWeight());
        assertNull(plot.getFixedDomainAxisSpace());
        assertNull(plot.getFixedRangeAxisSpace());
        assertNull(plot.getFixedLegendItems());
    }

    @Test
    public void testParameterizedConstructor() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Value");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();

        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        assertSame(dataset, plot.getDataset());
        assertSame(domainAxis, plot.getDomainAxis());
        assertSame(rangeAxis, plot.getRangeAxis());
        assertSame(renderer, plot.getRenderer());
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
            assertTrue(e.getMessage().contains("Null 'orientation'"));
        }
    }

    @Test
    public void testAxisOffset() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
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
    public void testDomainAxis() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        CategoryAxis axis = new CategoryAxis("Test Axis");
        plot.setDomainAxis(axis);
        assertSame(axis, plot.getDomainAxis());
        assertSame(axis, plot.getDomainAxis(0));
        assertNull(plot.getDomainAxis(1));
        assertEquals(0, plot.getDomainAxisIndex(axis));
        assertEquals(-1, plot.getDomainAxisIndex(new CategoryAxis("Other")));

        plot.setDomainAxes(new CategoryAxis[] { new CategoryAxis("A"), new CategoryAxis("B") });
        assertEquals(2, plot.getDomainAxisCount());

        plot.clearDomainAxes();
        assertEquals(0, plot.getDomainAxisCount());
    }

    @Test
    public void testDomainAxisLocation() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setDomainAxisLocation(AxisLocation.TOP_OR_LEFT);
        assertEquals(AxisLocation.TOP_OR_LEFT, plot.getDomainAxisLocation());
        assertEquals(AxisLocation.TOP_OR_LEFT, plot.getDomainAxisLocation(0));

        plot.setDomainAxisLocation(0, AxisLocation.BOTTOM_OR_RIGHT, false);
        assertEquals(AxisLocation.BOTTOM_OR_RIGHT, plot.getDomainAxisLocation(0));

        try {
            plot.setDomainAxisLocation(0, null, true);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'location'"));
        }
    }

    @Test
    public void testRangeAxis() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        ValueAxis axis = new NumberAxis("Range");
        plot.setRangeAxis(axis);
        assertSame(axis, plot.getRangeAxis());
        assertSame(axis, plot.getRangeAxis(0));
        assertNull(plot.getRangeAxis(1));
        assertEquals(0, plot.getRangeAxisIndex(axis));
        assertEquals(-1, plot.getRangeAxisIndex(new NumberAxis("Other")));

        plot.setRangeAxes(new ValueAxis[] { new NumberAxis("R1"), new NumberAxis("R2") });
        assertEquals(2, plot.getRangeAxisCount());

        plot.clearRangeAxes();
        assertEquals(0, plot.getRangeAxisCount());
    }

    @Test
    public void testRangeAxisLocation() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setRangeAxisLocation(AxisLocation.TOP_OR_LEFT);
        assertEquals(AxisLocation.TOP_OR_LEFT, plot.getRangeAxisLocation());
        assertEquals(AxisLocation.TOP_OR_LEFT, plot.getRangeAxisLocation(0));

        plot.setRangeAxisLocation(0, AxisLocation.TOP_OR_RIGHT, false);
        assertEquals(AxisLocation.TOP_OR_RIGHT, plot.getRangeAxisLocation(0));

        try {
            plot.setRangeAxisLocation(0, null, true);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'location'"));
        }
    }

    @Test
    public void testDatasetMapping() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        DefaultCategoryDataset ds0 = new DefaultCategoryDataset();
        DefaultCategoryDataset ds1 = new DefaultCategoryDataset();
        plot.setDataset(0, ds0);
        plot.setDataset(1, ds1);
        assertEquals(2, plot.getDatasetCount());

        plot.mapDatasetToDomainAxis(1, 0);
        plot.mapDatasetToRangeAxis(1, 0);
        assertNotNull(plot.getDomainAxisForDataset(1));
        assertNotNull(plot.getRangeAxisForDataset(1));
    }

    @Test
    public void testRendererManagement() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        CategoryItemRenderer r0 = new LineAndShapeRenderer();
        CategoryItemRenderer r1 = new LineAndShapeRenderer();
        plot.setRenderer(r0);
        assertSame(r0, plot.getRenderer());
        assertSame(r0, plot.getRenderer(0));
        assertNull(plot.getRenderer(5));

        plot.setRenderer(1, r1);
        assertSame(r1, plot.getRenderer(1));
        assertEquals(1, plot.getIndexOf(r1));
        assertSame(r0, plot.getRendererForDataset(plot.getDataset(0)));
        assertNull(plot.getRendererForDataset(null));

        plot.setRenderers(new CategoryItemRenderer[] { r1, r0 });
        assertSame(r1, plot.getRenderer(0));
    }

    @Test
    public void testRenderingOrderAndSortOrders() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        assertEquals(DatasetRenderingOrder.FORWARD, plot.getDatasetRenderingOrder());

        try {
            plot.setDatasetRenderingOrder(null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'order'"));
        }

        plot.setColumnRenderingOrder(SortOrder.DESCENDING);
        assertEquals(SortOrder.DESCENDING, plot.getColumnRenderingOrder());
        try {
            plot.setColumnRenderingOrder(null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'order'"));
        }

        plot.setRowRenderingOrder(SortOrder.DESCENDING);
        assertEquals(SortOrder.DESCENDING, plot.getRowRenderingOrder());
        try {
            plot.setRowRenderingOrder(null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'order'"));
        }
    }

    @Test
    public void testGridlinesAndStrokesAndPaints() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setDomainGridlinesVisible(true);
        assertTrue(plot.isDomainGridlinesVisible());

        plot.setDomainGridlinePosition(CategoryAnchor.END);
        assertEquals(CategoryAnchor.END, plot.getDomainGridlinePosition());
        try {
            plot.setDomainGridlinePosition(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'position'"));
        }

        Stroke stroke = new BasicStroke(1.5f);
        plot.setDomainGridlineStroke(stroke);
        assertSame(stroke, plot.getDomainGridlineStroke());
        try {
            plot.setDomainGridlineStroke(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'stroke'"));
        }

        Paint paint = Color.RED;
        plot.setDomainGridlinePaint(paint);
        assertSame(paint, plot.getDomainGridlinePaint());
        try {
            plot.setDomainGridlinePaint(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'paint'"));
        }

        plot.setRangeGridlinesVisible(false);
        assertFalse(plot.isRangeGridlinesVisible());

        plot.setRangeGridlineStroke(stroke);
        assertSame(stroke, plot.getRangeGridlineStroke());
        try {
            plot.setRangeGridlineStroke(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'stroke'"));
        }

        plot.setRangeGridlinePaint(paint);
        assertSame(paint, plot.getRangeGridlinePaint());
        try {
            plot.setRangeGridlinePaint(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'paint'"));
        }
    }

    @Test
    public void testCrosshairProperties() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setRangeCrosshairVisible(true);
        assertTrue(plot.isRangeCrosshairVisible());

        plot.setRangeCrosshairLockedOnData(false);
        assertFalse(plot.isRangeCrosshairLockedOnData());

        plot.setRangeCrosshairValue(10.5);
        assertEquals(10.5, plot.getRangeCrosshairValue(), 0.00001);

        Stroke stroke = new BasicStroke(2.0f);
        plot.setRangeCrosshairStroke(stroke);
        assertSame(stroke, plot.getRangeCrosshairStroke());
        try {
            plot.setRangeCrosshairStroke(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'stroke'"));
        }

        Paint paint = Color.GREEN;
        plot.setRangeCrosshairPaint(paint);
        assertSame(paint, plot.getRangeCrosshairPaint());
        try {
            plot.setRangeCrosshairPaint(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'paint'"));
        }
    }

    @Test
    public void testMarkersAndAnnotations() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        CategoryMarker dMarker = new CategoryMarker("Cat1");
        plot.addDomainMarker(dMarker);
        plot.addDomainMarker(0, dMarker, Layer.BACKGROUND);
        Collection dMarkers = plot.getDomainMarkers(Layer.FOREGROUND);
        assertNotNull(dMarkers);

        Marker rMarker = new ValueMarker(5.0);
        plot.addRangeMarker(rMarker);
        plot.addRangeMarker(0, rMarker, Layer.BACKGROUND);
        Collection rMarkers = plot.getRangeMarkers(Layer.FOREGROUND);
        assertNotNull(rMarkers);

        plot.clearDomainMarkers();
        plot.clearRangeMarkers();
        plot.clearDomainMarkers(0);
        plot.clearRangeMarkers(0);

        CategoryAnnotation annotation = new org.jfree.chart.annotations.CategoryTextAnnotation("Note", "Cat1", 1.0);
        plot.addAnnotation(annotation);
        List anns = plot.getAnnotations();
        assertTrue(anns.contains(annotation));

        plot.removeAnnotation(annotation);
        assertFalse(plot.getAnnotations().contains(annotation));

        plot.addAnnotation(annotation);
        plot.clearAnnotations();
        assertTrue(plot.getAnnotations().isEmpty());

        try {
            plot.addDomainMarker(null);
            fail();
        } catch (IllegalArgumentException e) {}

        try {
            plot.addRangeMarker(null);
            fail();
        } catch (IllegalArgumentException e) {}

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
    public void testLegendItemsAndCategories() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        LegendItemCollection lic = new LegendItemCollection();
        plot.setFixedLegendItems(lic);
        assertSame(lic, plot.getFixedLegendItems());
        assertNotNull(plot.getLegendItems());

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "R1", "C1");
        plot.setDataset(dataset);
        plot.setRenderer(new LineAndShapeRenderer());
        assertNotNull(plot.getLegendItems());

        List categories = plot.getCategories();
        assertNotNull(categories);
        assertTrue(categories.contains("C1"));

        List catForAxis = plot.getCategoriesForAxis(plot.getDomainAxis());
        assertNotNull(catForAxis);
    }

    @Test
    public void testZoomAndSpaceAndMisc() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setRangeAxis(new NumberAxis());
        plot.zoom(1.1);
        plot.zoom(0.0);

        plot.zoomRangeAxes(1.2, null, null);
        plot.zoomRangeAxes(1.2, null, null, true);
        plot.zoomRangeAxes(0.8, 1.2, null, null);

        plot.zoomDomainAxes(1.0, null, null);
        plot.zoomDomainAxes(0.0, 1.0, null, null);
        plot.zoomDomainAxes(1.0, null, null, false);

        plot.setWeight(5);
        assertEquals(5, plot.getWeight());

        AxisSpace space = new AxisSpace();
        plot.setFixedDomainAxisSpace(space);
        assertSame(space, plot.getFixedDomainAxisSpace());

        plot.setFixedRangeAxisSpace(space);
        assertSame(space, plot.getFixedRangeAxisSpace());

        plot.setAnchorValue(12.3);
        assertEquals(12.3, plot.getAnchorValue(), 0.00001);

        plot.setDrawSharedDomainAxis(true);
        assertTrue(plot.getDrawSharedDomainAxis());

        assertNull(plot.getDataRange(new NumberAxis()));
    }

    @Test
    public void testEqualsAndClone() throws Throwable {
        CategoryPlot plot1 = new CategoryPlot();
        CategoryPlot plot2 = new CategoryPlot();
        assertTrue(plot1.equals(plot2));
        assertTrue(plot1.equals(plot1));
        assertFalse(plot1.equals(null));
        assertFalse(plot1.equals("Some String"));

        plot1.setOrientation(PlotOrientation.HORIZONTAL);
        assertFalse(plot1.equals(plot2));

        CategoryPlot clone = (CategoryPlot) plot1.clone();
        assertTrue(plot1.equals(clone));
    }
}