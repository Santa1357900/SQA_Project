package org.jfree.chart.renderer.category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import org.junit.Test;

import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.annotations.CategoryTextAnnotation;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.StandardEntityCollection;
import org.jfree.chart.labels.CategoryItemLabelGenerator;
import org.jfree.chart.labels.CategorySeriesLabelGenerator;
import org.jfree.chart.labels.CategoryToolTipGenerator;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardCategorySeriesLabelGenerator;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.plot.CategoryMarker;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.urls.CategoryURLGenerator;
import org.jfree.chart.urls.StandardCategoryURLGenerator;
import org.jfree.chart.util.Layer;
import org.jfree.chart.util.LengthAdjustmentType;
import org.jfree.chart.util.RectangleAnchor;
import org.jfree.chart.util.SortOrder;
import org.jfree.data.Range;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

public class AbstractCategoryItemRendererTest {

    private static class ConcreteCategoryItemRenderer extends AbstractCategoryItemRenderer {
        private static final long serialVersionUID = 1L;
    }

    @Test
    public void testConstructorsAndDefaults() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        assertEquals(1, renderer.getPassCount());
        assertNull(renderer.getPlot());
        assertNull(renderer.getBaseItemLabelGenerator());
        assertNull(renderer.getBaseToolTipGenerator());
        assertNull(renderer.getBaseURLGenerator());
        assertNotNull(renderer.getLegendItemLabelGenerator());
        assertNull(renderer.getLegendItemToolTipGenerator());
        assertNull(renderer.getLegendItemURLGenerator());
        assertEquals(0, renderer.getRowCount());
        assertEquals(0, renderer.getColumnCount());
        assertNull(renderer.getDrawingSupplier());
    }

    @Test
    public void testPlotAssignment() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        CategoryPlot plot = new CategoryPlot();
        renderer.setPlot(plot);
        assertEquals(plot, renderer.getPlot());

        try {
            renderer.setPlot(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'plot' argument"));
        }
    }

    @Test
    public void testItemLabelGenerator() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        CategoryItemLabelGenerator gen = new StandardCategoryItemLabelGenerator();
        
        assertNull(renderer.getItemLabelGenerator(0, 0, false));
        assertNull(renderer.getSeriesItemLabelGenerator(0));

        renderer.setSeriesItemLabelGenerator(0, gen);
        assertEquals(gen, renderer.getSeriesItemLabelGenerator(0));
        assertEquals(gen, renderer.getItemLabelGenerator(0, 0, false));

        renderer.setBaseItemLabelGenerator(gen);
        assertEquals(gen, renderer.getBaseItemLabelGenerator());
        
        ConcreteCategoryItemRenderer renderer2 = new ConcreteCategoryItemRenderer();
        renderer2.setSeriesItemLabelGenerator(0, gen, false);
        assertEquals(gen, renderer2.getSeriesItemLabelGenerator(0));

        renderer2.setBaseItemLabelGenerator(gen, false);
        assertEquals(gen, renderer2.getBaseItemLabelGenerator());
    }

    @Test
    public void testToolTipGenerator() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        CategoryToolTipGenerator gen = new StandardCategoryToolTipGenerator();

        assertNull(renderer.getToolTipGenerator(0, 0, false));
        assertNull(renderer.getSeriesToolTipGenerator(0));

        renderer.setSeriesToolTipGenerator(0, gen);
        assertEquals(gen, renderer.getSeriesToolTipGenerator(0));
        assertEquals(gen, renderer.getToolTipGenerator(0, 0, false));

        renderer.setBaseToolTipGenerator(gen);
        assertEquals(gen, renderer.getBaseToolTipGenerator());

        ConcreteCategoryItemRenderer renderer2 = new ConcreteCategoryItemRenderer();
        renderer2.setSeriesToolTipGenerator(0, gen, false);
        assertEquals(gen, renderer2.getSeriesToolTipGenerator(0));

        renderer2.setBaseToolTipGenerator(gen, false);
        assertEquals(gen, renderer2.getBaseToolTipGenerator());
    }

    @Test
    public void testURLGenerator() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        CategoryURLGenerator gen = new StandardCategoryURLGenerator();

        assertNull(renderer.getURLGenerator(0, 0, false));
        assertNull(renderer.getSeriesURLGenerator(0));

        renderer.setSeriesURLGenerator(0, gen);
        assertEquals(gen, renderer.getSeriesURLGenerator(0));
        assertEquals(gen, renderer.getURLGenerator(0, 0, false));

        renderer.setBaseURLGenerator(gen);
        assertEquals(gen, renderer.getBaseURLGenerator());

        ConcreteCategoryItemRenderer renderer2 = new ConcreteCategoryItemRenderer();
        renderer2.setSeriesURLGenerator(0, gen, false);
        assertEquals(gen, renderer2.getSeriesURLGenerator(0));

        renderer2.setBaseURLGenerator(gen, false);
        assertEquals(gen, renderer2.getBaseURLGenerator());
    }

    @Test
    public void testAnnotations() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        CategoryTextAnnotation annotation = new CategoryTextAnnotation("Test", "Category 1", 1.0);

        renderer.addAnnotation(annotation);
        renderer.addAnnotation(annotation, Layer.BACKGROUND);
        renderer.addAnnotation(annotation, Layer.FOREGROUND);

        try {
            renderer.addAnnotation(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'annotation' argument"));
        }

        try {
            renderer.addAnnotation(annotation, null);
            fail("Should have thrown NullPointerException or IllegalArgumentException");
        } catch (Exception e) {
            // expected
        }

        assertTrue(renderer.removeAnnotation(annotation));
        assertFalse(renderer.removeAnnotation(annotation));

        renderer.removeAnnotations();
    }

    @Test
    public void testLegendItemGenerators() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        CategorySeriesLabelGenerator gen = new StandardCategorySeriesLabelGenerator();

        renderer.setLegendItemLabelGenerator(gen);
        assertEquals(gen, renderer.getLegendItemLabelGenerator());

        try {
            renderer.setLegendItemLabelGenerator(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'generator' argument"));
        }

        renderer.setLegendItemToolTipGenerator(gen);
        assertEquals(gen, renderer.getLegendItemToolTipGenerator());

        renderer.setLegendItemURLGenerator(gen);
        assertEquals(gen, renderer.getLegendItemURLGenerator());
    }

    @Test
    public void testInitialiseAndBounds() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "S1", "C1");
        dataset.addValue(2.0, "S2", "C2");

        CategoryPlot plot = new CategoryPlot(dataset, new CategoryAxis(), new NumberAxis(), renderer);
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 100);
        PlotRenderingInfo info = new PlotRenderingInfo(new ChartRenderingInfo());

        CategoryItemRendererState state = renderer.initialise(g2, area, plot, dataset, info);
        assertNotNull(state);
        assertEquals(2, renderer.getRowCount());
        assertEquals(2, renderer.getColumnCount());

        CategoryItemRendererState stateNullDataset = renderer.initialise(g2, area, plot, null, info);
        assertNotNull(stateNullDataset);
        assertEquals(0, renderer.getRowCount());
        assertEquals(0, renderer.getColumnCount());

        assertNull(renderer.findRangeBounds(null));
        Range range = renderer.findRangeBounds(dataset);
        assertNotNull(range);
        assertEquals(1.0, range.getLowerBound(), 0.001);
        assertEquals(2.0, range.getUpperBound(), 0.001);
    }

    @Test
    public void testDrawDomainLine() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        CategoryPlot plot = new CategoryPlot();
        plot.setOrientation(PlotOrientation.VERTICAL);
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 100);

        renderer.drawDomainLine(g2, plot, area, 50.0, Color.BLACK, new BasicStroke(1.0f));

        plot.setOrientation(PlotOrientation.HORIZONTAL);
        renderer.drawDomainLine(g2, plot, area, 50.0, Color.BLACK, new BasicStroke(1.0f));

        try {
            renderer.drawDomainLine(g2, plot, area, 50.0, null, new BasicStroke(1.0f));
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'paint' argument"));
        }

        try {
            renderer.drawDomainLine(g2, plot, area, 50.0, Color.BLACK, null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'stroke' argument"));
        }
    }

    @Test
    public void testDrawRangeLine() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        CategoryPlot plot = new CategoryPlot();
        plot.setRangeAxis(new NumberAxis());
        plot.setOrientation(PlotOrientation.VERTICAL);
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 100);

        // Within range [0.0, 1.0] by default in NumberAxis
        renderer.drawRangeLine(g2, plot, plot.getRangeAxis(), area, 0.5, Color.BLACK, new BasicStroke(1.0f));
        // Outside range
        renderer.drawRangeLine(g2, plot, plot.getRangeAxis(), area, 5.0, Color.BLACK, new BasicStroke(1.0f));

        plot.setOrientation(PlotOrientation.HORIZONTAL);
        renderer.drawRangeLine(g2, plot, plot.getRangeAxis(), area, 0.5, Color.BLACK, new BasicStroke(1.0f));
    }

    @Test
    public void testDrawDomainMarker() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "S1", "C1");
        CategoryPlot plot = new CategoryPlot(dataset, new CategoryAxis(), new NumberAxis(), renderer);
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 100);

        CategoryMarker markerLine = new CategoryMarker("C1", Color.RED, new BasicStroke(1.0f));
        markerLine.setDrawAsLine(true);
        markerLine.setLabel("Marker Label");
        renderer.drawDomainMarker(g2, plot, plot.getDomainAxis(), markerLine, area);

        CategoryMarker markerRect = new CategoryMarker("C1", Color.RED, new BasicStroke(1.0f));
        markerRect.setDrawAsLine(false);
        renderer.drawDomainMarker(g2, plot, plot.getDomainAxis(), markerRect, area);

        CategoryMarker markerMissing = new CategoryMarker("Unknown", Color.RED, new BasicStroke(1.0f));
        renderer.drawDomainMarker(g2, plot, plot.getDomainAxis(), markerMissing, area);
    }

    @Test
    public void testDrawRangeMarker() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        CategoryPlot plot = new CategoryPlot();
        NumberAxis rangeAxis = new NumberAxis();
        rangeAxis.setRange(0.0, 10.0);
        plot.setRangeAxis(rangeAxis);
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 100);

        // ValueMarker inside range
        ValueMarker vm = new ValueMarker(5.0);
        vm.setLabel("VM");
        renderer.drawRangeMarker(g2, plot, rangeAxis, vm, area);

        // ValueMarker outside range
        ValueMarker vmOut = new ValueMarker(15.0);
        renderer.drawRangeMarker(g2, plot, rangeAxis, vmOut, area);

        // IntervalMarker inside range
        IntervalMarker im = new IntervalMarker(2.0, 8.0);
        im.setPaint(Color.BLUE);
        im.setOutlinePaint(Color.BLACK);
        im.setOutlineStroke(new BasicStroke(1.0f));
        im.setLabel("IM");
        renderer.drawRangeMarker(g2, plot, rangeAxis, im, area);

        // IntervalMarker with GradientPaint
        IntervalMarker imGP = new IntervalMarker(2.0, 8.0);
        imGP.setPaint(new GradientPaint(0, 0, Color.RED, 10, 10, Color.BLUE));
        renderer.drawRangeMarker(g2, plot, rangeAxis, imGP, area);

        // IntervalMarker outside range
        IntervalMarker imOut = new IntervalMarker(12.0, 15.0);
        renderer.drawRangeMarker(g2, plot, rangeAxis, imOut, area);

        plot.setOrientation(PlotOrientation.HORIZONTAL);
        renderer.drawRangeMarker(g2, plot, rangeAxis, im, area);
    }

    @Test
    public void testGetLegendItem() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        assertNull(renderer.getLegendItem(0, 0));

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "S1", "C1");
        CategoryPlot plot = new CategoryPlot(dataset, new CategoryAxis(), new NumberAxis(), renderer);
        renderer.setPlot(plot);

        renderer.setLegendItemToolTipGenerator(new StandardCategorySeriesLabelGenerator());
        renderer.setLegendItemURLGenerator(new StandardCategorySeriesLabelGenerator());

        LegendItem item = renderer.getLegendItem(0, 0);
        assertNotNull(item);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        ConcreteCategoryItemRenderer r1 = new ConcreteCategoryItemRenderer();
        ConcreteCategoryItemRenderer r2 = new ConcreteCategoryItemRenderer();

        assertTrue(r1.equals(r1));
        assertTrue(r1.equals(r2));
        assertFalse(r1.equals(null));
        assertFalse(r1.equals("Some String"));

        r1.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        assertFalse(r1.equals(r2));
        r2.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        assertTrue(r1.equals(r2));

        r1.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator());
        assertFalse(r1.equals(r2));
        r2.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator());
        assertTrue(r1.equals(r2));

        r1.setBaseURLGenerator(new StandardCategoryURLGenerator());
        assertFalse(r1.equals(r2));
        r2.setBaseURLGenerator(new StandardCategoryURLGenerator());
        assertTrue(r1.equals(r2));

        assertTrue(r1.hashCode() == r2.hashCode());
    }

    @Test
    public void testUpdateCrosshairValues() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        CategoryPlot plot = new CategoryPlot();
        renderer.setPlot(plot);

        try {
            renderer.updateCrosshairValues(null, "R1", "C1", 1.0, 0, 10.0, 10.0, null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'orientation' argument"));
        }
    }

    @Test
    public void testDrawAnnotations() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        CategoryPlot plot = new CategoryPlot();
        renderer.setPlot(plot);
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 100);

        CategoryTextAnnotation annotation = new CategoryTextAnnotation("Test", "C1", 1.0);
        renderer.addAnnotation(annotation, Layer.FOREGROUND);
        renderer.addAnnotation(annotation, Layer.BACKGROUND);

        renderer.drawAnnotations(g2, area, new CategoryAxis(), new NumberAxis(), Layer.FOREGROUND, new PlotRenderingInfo(new ChartRenderingInfo()));
        renderer.drawAnnotations(g2, area, new CategoryAxis(), new NumberAxis(), Layer.BACKGROUND, new PlotRenderingInfo(new ChartRenderingInfo()));

        try {
            renderer.drawAnnotations(g2, area, new CategoryAxis(), new NumberAxis(), null, new PlotRenderingInfo(new ChartRenderingInfo()));
            fail("Should have thrown RuntimeException or NullPointerException");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testClone() throws Throwable {
        ConcreteCategoryItemRenderer r1 = new ConcreteCategoryItemRenderer();
        r1.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        r1.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator());
        r1.setBaseURLGenerator(new StandardCategoryURLGenerator());

        ConcreteCategoryItemRenderer r2 = (ConcreteCategoryItemRenderer) r1.clone();
        assertTrue(r1.equals(r2));
    }

    @Test
    public void testAddEntityAndHitTest() throws Throwable {
        ConcreteCategoryItemRenderer renderer = new ConcreteCategoryItemRenderer();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "S1", "C1");
        CategoryPlot plot = new CategoryPlot(dataset, new CategoryAxis(), new NumberAxis(), renderer);
        renderer.setPlot(plot);

        StandardEntityCollection entities = new StandardEntityCollection();
        renderer.addEntity(entities, null, dataset, 0, 0, false, 50.0, 50.0);
        assertEquals(1, entities.getEntityCount());

        try {
            renderer.addEntity(entities, null, dataset, 0, 0, false);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'hotspot' argument"));
        }

        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 100);

        boolean hit = renderer.hitTest(50.0, 50.0, g2, area, plot, plot.getDomainAxis(), plot.getRangeAxis(), dataset, 0, 0, false, null);
        assertFalse(hit);

        try {
            renderer.createHotSpotShape(g2, area, plot, plot.getDomainAxis(), plot.getRangeAxis(), dataset, 0, 0, false, null);
            fail("Should have thrown RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Not implemented"));
        }
    }
}