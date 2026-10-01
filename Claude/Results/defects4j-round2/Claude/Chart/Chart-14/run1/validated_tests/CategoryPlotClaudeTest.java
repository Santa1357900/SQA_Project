package org.jfree.chart.plot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collection;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.AxisSpace;
import org.jfree.chart.util.Layer;
import org.jfree.chart.util.RectangleEdge;
import org.jfree.chart.util.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;

public class CategoryPlotClaudeTest {

    // Covers default constructor -> orientation defaults to VERTICAL
    @Test
    public void testDefaultConstructor_orientationIsVertical() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertEquals(PlotOrientation.VERTICAL, plot.getOrientation());
    }

    // Covers setOrientation(null) -> IllegalArgumentException branch
    @Test
    public void testSetOrientation_null_throwsIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.setOrientation(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers setOrientation(valid) -> updates orientation
    @Test
    public void testSetOrientation_horizontal_updatesOrientation() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.setOrientation(PlotOrientation.HORIZONTAL);
        assertEquals(PlotOrientation.HORIZONTAL, plot.getOrientation());
    }

    // Covers setAxisOffset(null) -> IllegalArgumentException branch
    @Test
    public void testSetAxisOffset_null_throwsIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.setAxisOffset(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers setAxisOffset(valid) -> reference stored and returned
    @Test
    public void testSetAxisOffset_valid_updatesOffset() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        RectangleInsets insets = new RectangleInsets(1.0, 2.0, 3.0, 4.0);
        plot.setAxisOffset(insets);
        assertSame(insets, plot.getAxisOffset());
    }

    // Covers getDomainAxis(index) with index out of range and no parent -> null
    @Test
    public void testGetDomainAxis_indexOutOfRange_returnsNull() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertNull(plot.getDomainAxis(3));
    }

    // Covers getDomainAxisIndex(null) -> IllegalArgumentException branch
    @Test
    public void testGetDomainAxisIndex_nullAxis_throwsIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.getDomainAxisIndex(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers getDomainAxisLocation() default value set in constructor
    @Test
    public void testGetDomainAxisLocation_default_bottomOrLeft() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertEquals(AxisLocation.BOTTOM_OR_LEFT, plot.getDomainAxisLocation());
    }

    // Covers setDomainAxisLocation(null) -> index 0 null check throws
    @Test
    public void testSetDomainAxisLocation_nullAtIndexZero_throwsIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.setDomainAxisLocation(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers getDomainAxisEdge() default resolution: BOTTOM_OR_LEFT + VERTICAL -> BOTTOM
    @Test
    public void testGetDomainAxisEdge_default_bottom() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertEquals(RectangleEdge.BOTTOM, plot.getDomainAxisEdge());
    }

    // Covers getRangeAxisIndex(null) -> IllegalArgumentException branch
    @Test
    public void testGetRangeAxisIndex_nullAxis_throwsIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.getRangeAxisIndex(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers getRangeAxisLocation() default value set in constructor
    @Test
    public void testGetRangeAxisLocation_default_topOrLeft() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertEquals(AxisLocation.TOP_OR_LEFT, plot.getRangeAxisLocation());
    }

    // Covers setRangeAxisLocation(null) -> index 0 null check throws
    @Test
    public void testSetRangeAxisLocation_null_throwsIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.setRangeAxisLocation(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers getRangeAxisEdge() default resolution: TOP_OR_LEFT + VERTICAL -> LEFT
    @Test
    public void testGetRangeAxisEdge_default_left() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertEquals(RectangleEdge.LEFT, plot.getRangeAxisEdge());
    }

    // Covers clearDomainAxes() and clearRangeAxes() -> counts become zero
    @Test
    public void testClearAxes_domainAndRange_countsBecomeZero() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertEquals(1, plot.getDomainAxisCount());
        assertEquals(1, plot.getRangeAxisCount());
        plot.clearDomainAxes();
        plot.clearRangeAxes();
        assertEquals(0, plot.getDomainAxisCount());
        assertEquals(0, plot.getRangeAxisCount());
    }

    // Covers getDataset(index) with index out of range -> null
    @Test
    public void testGetDataset_indexOutOfRange_returnsNull() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertNull(plot.getDataset(9));
    }

    // Covers setDataset(dataset) -> dataset stored, dataset count unaffected
    @Test
    public void testSetDataset_updatesDatasetAndCount() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "R1", "C1");
        plot.setDataset(dataset);
        assertSame(dataset, plot.getDataset());
        assertEquals(1, plot.getDatasetCount());
    }

    // Covers mapDatasetToDomainAxis with an axis index beyond current axis list -> null
    @Test
    public void testMapDatasetToDomainAxis_unmappedIndexReturnsNull() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.mapDatasetToDomainAxis(0, 5);
        assertNull(plot.getDomainAxisForDataset(0));
    }

    // Covers mapDatasetToRangeAxis with an axis index beyond current axis list -> null
    @Test
    public void testMapDatasetToRangeAxis_unmappedIndexReturnsNull() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.mapDatasetToRangeAxis(0, 5);
        assertNull(plot.getRangeAxisForDataset(0));
    }

    // Covers getRenderer(index) with index out of range -> null
    @Test
    public void testGetRenderer_indexOutOfRange_returnsNull() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertNull(plot.getRenderer(7));
    }

    // Covers setDatasetRenderingOrder(null) -> IllegalArgumentException branch
    @Test
    public void testSetDatasetRenderingOrder_null_throwsIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.setDatasetRenderingOrder(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers getDatasetRenderingOrder() default value REVERSE
    @Test
    public void testGetDatasetRenderingOrder_defaultReverse() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertEquals(DatasetRenderingOrder.REVERSE, plot.getDatasetRenderingOrder());
    }

    // Covers setColumnRenderingOrder(null) and setRowRenderingOrder(null) -> both throw
    @Test
    public void testSetColumnAndRowRenderingOrder_null_throwsIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.setColumnRenderingOrder(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        try {
            plot.setRowRenderingOrder(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers isDomainGridlinesVisible() default false and toggling to true
    @Test
    public void testDomainGridlines_defaultAndToggle() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertFalse(plot.isDomainGridlinesVisible());
        plot.setDomainGridlinesVisible(true);
        assertTrue(plot.isDomainGridlinesVisible());
    }

    // Covers setDomainGridlinePosition/Stroke/Paint(null) -> each throws IllegalArgumentException
    @Test
    public void testDomainGridlineSetters_null_throwIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.setDomainGridlinePosition(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        try {
            plot.setDomainGridlineStroke(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        try {
            plot.setDomainGridlinePaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers isRangeGridlinesVisible() default true and toggling to false
    @Test
    public void testRangeGridlines_defaultAndToggle() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertTrue(plot.isRangeGridlinesVisible());
        plot.setRangeGridlinesVisible(false);
        assertFalse(plot.isRangeGridlinesVisible());
    }

    // Covers setRangeGridlineStroke/Paint(null) -> each throws IllegalArgumentException
    @Test
    public void testRangeGridlineSetters_null_throwIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.setRangeGridlineStroke(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        try {
            plot.setRangeGridlinePaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers getLegendItems() default path (fixedLegendItems null) -> always non-null
    @Test
    public void testGetLegendItems_defaultNotNull() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertNotNull(plot.getLegendItems());
    }

    // Covers setFixedLegendItems() -> getFixedLegendItems() and getLegendItems() return same instance
    @Test
    public void testSetFixedLegendItems_getReturnsSameInstance() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        LegendItemCollection items = new LegendItemCollection();
        plot.setFixedLegendItems(items);
        assertSame(items, plot.getFixedLegendItems());
        assertSame(items, plot.getLegendItems());
    }

    // Covers addAnnotation(null) -> IllegalArgumentException branch
    @Test
    public void testAddAnnotation_null_throwsIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.addAnnotation(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers clearAnnotations() -> annotations list stays empty
    @Test
    public void testClearAnnotations_emptiesList() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertTrue(plot.getAnnotations().isEmpty());
        plot.clearAnnotations();
        assertTrue(plot.getAnnotations().isEmpty());
    }

    // Covers addRangeMarker(Marker) default FOREGROUND layer -> getRangeMarkers contains it
    @Test
    public void testAddRangeMarker_getRangeMarkers_containsMarker() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        Marker marker = new ValueMarker(3.0, Color.RED, new BasicStroke(1.0f),
                Color.BLUE, new BasicStroke(1.0f), 0.5f);
        plot.addRangeMarker(marker);
        Collection markers = plot.getRangeMarkers(Layer.FOREGROUND);
        assertNotNull(markers);
        assertTrue(markers.contains(marker));
    }

    // Covers removeRangeMarker(marker, layer) -> existing marker removed successfully returns true
    @Test
    public void testRemoveRangeMarker_existingMarker_returnsTrue() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        Marker marker = new ValueMarker(4.0, Color.GREEN, new BasicStroke(1.0f),
                Color.YELLOW, new BasicStroke(1.0f), 0.5f);
        plot.addRangeMarker(marker, Layer.BACKGROUND);
        boolean removed = plot.removeRangeMarker(marker, Layer.BACKGROUND);
        assertTrue(removed);
    }

    // BUG TEST: removeRangeMarker() must return false (not throw NPE) when no
    // markers were ever added to the FOREGROUND layer for the given index.
    @Test
    public void testRemoveRangeMarker_noForegroundMarkersPresent_returnsFalse() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        Marker marker = new ValueMarker(2.0, Color.RED, new BasicStroke(1.0f),
                Color.BLUE, new BasicStroke(1.0f), 0.5f);
        boolean removed = plot.removeRangeMarker(marker);
        assertFalse(removed);
    }

    // Covers getDomainMarkers(layer) default -> no markers registered -> null
    @Test
    public void testGetDomainMarkers_default_returnsNull() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertNull(plot.getDomainMarkers(Layer.FOREGROUND));
    }

    // Covers clearRangeMarkers() -> removes the baseline marker map entry entirely
    @Test
    public void testClearRangeMarkers_removesAll() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        plot.clearRangeMarkers();
        assertNull(plot.getRangeMarkers(Layer.BACKGROUND));
    }

    // Covers isRangeCrosshairVisible()/setRangeCrosshairVisible() and crosshair value getter/setter
    @Test
    public void testRangeCrosshair_setVisibleAndValue() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertFalse(plot.isRangeCrosshairVisible());
        plot.setRangeCrosshairVisible(true);
        assertTrue(plot.isRangeCrosshairVisible());
        plot.setRangeCrosshairValue(7.5);
        assertEquals(7.5, plot.getRangeCrosshairValue(), 0.0000001);
    }

    // Covers setRangeCrosshairStroke/Paint(null) -> each throws IllegalArgumentException
    @Test
    public void testRangeCrosshairSetters_null_throwIllegalArgumentException() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        try {
            plot.setRangeCrosshairStroke(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        try {
            plot.setRangeCrosshairPaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers setFixedDomainAxisSpace/getFixedDomainAxisSpace and range equivalents
    @Test
    public void testFixedAxisSpaces_setAndGet() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        AxisSpace domainSpace = new AxisSpace();
        AxisSpace rangeSpace = new AxisSpace();
        plot.setFixedDomainAxisSpace(domainSpace);
        plot.setFixedRangeAxisSpace(rangeSpace);
        assertSame(domainSpace, plot.getFixedDomainAxisSpace());
        assertSame(rangeSpace, plot.getFixedRangeAxisSpace());
    }

    // Covers isDomainZoomable() always false and isRangeZoomable() always true
    @Test
    public void testZoomableFlags() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertFalse(plot.isDomainZoomable());
        assertTrue(plot.isRangeZoomable());
    }

    // Covers getAnchorValue()/setAnchorValue()
    @Test
    public void testSetAnchorValue_getAnchorValue() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        assertEquals(0.0, plot.getAnchorValue(), 0.0000001);
        plot.setAnchorValue(12.34);
        assertEquals(12.34, plot.getAnchorValue(), 0.0000001);
    }

    // Covers render() with a null dataset -> hasData is false -> returns false
    @Test
    public void testRender_datasetNull_returnsFalse() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 10, 10);
        boolean found = plot.render(g2, dataArea, 0, null);
        assertFalse(found);
    }

    // Covers render() with non-empty dataset but null renderer -> short-circuit returns false
    @Test
    public void testRender_datasetNonEmptyRendererNull_returnsFalse() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(2.0, "Row", "Col");
        plot.setDataset(dataset);
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 10, 10);
        boolean found = plot.render(g2, dataArea, 0, null);
        assertFalse(found);
    }

    // Covers calculateAxisSpace() with no axes set -> returns a zero-sized AxisSpace
    @Test
    public void testCalculateAxisSpace_nullAxes_returnsZeroSpace() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D plotArea = new Rectangle2D.Double(0, 0, 100, 100);
        AxisSpace space = plot.calculateAxisSpace(g2, plotArea);
        assertNotNull(space);
        assertEquals(0.0, space.getLeft(), 0.0000001);
        assertEquals(0.0, space.getRight(), 0.0000001);
        assertEquals(0.0, space.getTop(), 0.0000001);
        assertEquals(0.0, space.getBottom(), 0.0000001);
    }

    // Covers draw() with a normal-sized area, null axes/renderer/dataset -> dataArea populated
    @Test
    public void testDraw_defaultPlot_populatesDataArea() throws Throwable {
        CategoryPlot plot = new CategoryPlot();
        Graphics2D g2 = new BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 60, 60);
        PlotRenderingInfo info = new PlotRenderingInfo(null);
        plot.draw(g2, area, null, null, info);
        Rectangle2D dataArea = info.getDataArea();
        assertNotNull(dataArea);
        assertTrue(dataArea.getWidth() > 0.0);
        assertTrue(dataArea.getWidth() <= 60.0);
    }

}
