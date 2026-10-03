package org.jfree.chart.renderer.category;

import org.junit.Test;
import static org.junit.Assert.*;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import org.jfree.data.Range;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.annotations.CategoryAnnotation;
import org.jfree.chart.labels.CategoryItemLabelGenerator;
import org.jfree.chart.labels.CategoryToolTipGenerator;
import org.jfree.chart.labels.CategorySeriesLabelGenerator;
import org.jfree.chart.labels.StandardCategorySeriesLabelGenerator;
import org.jfree.chart.urls.CategoryURLGenerator;
import org.jfree.chart.util.Layer;

public class AbstractCategoryItemRendererClaudeTest {

    // covers: getPassCount() default implementation returns 1
    @Test
    public void testGetPassCount_returnsOne() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        assertEquals(1, renderer.getPassCount());
    }

    // covers: getPlot() default null when not assigned
    @Test
    public void testGetPlot_initiallyNull() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        assertNull(renderer.getPlot());
    }

    // covers: setPlot(null) throws IllegalArgumentException branch
    @Test
    public void testSetPlot_nullArgument_throwsIllegalArgumentException() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        try {
            renderer.setPlot(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: setPlot(plot)/getPlot() normal path
    @Test
    public void testSetPlotAndGetPlot_returnsSamePlot() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        CategoryAxis domainAxis = new CategoryAxis("x");
        NumberAxis rangeAxis = new NumberAxis("y");
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        renderer.setPlot(plot);
        assertSame(plot, renderer.getPlot());
    }

    // covers: getItemLabelGenerator when both series and base generators are null
    @Test
    public void testGetItemLabelGenerator_noGenerators_returnsNull() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        assertNull(renderer.getItemLabelGenerator(0, 0, false));
    }

    // covers: getItemLabelGenerator returns series generator when set
    @Test
    public void testGetItemLabelGenerator_seriesGeneratorSet_returnsSeriesGenerator() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryItemLabelGenerator gen = new CategoryItemLabelGenerator() {
            public String generateLabel(CategoryDataset dataset, int row, int column) {
                return "L";
            }
        };
        renderer.setSeriesItemLabelGenerator(0, gen);
        assertSame(gen, renderer.getItemLabelGenerator(0, 0, false));
    }

    // covers: getItemLabelGenerator falls back to base when series generator is null
    @Test
    public void testGetItemLabelGenerator_baseGeneratorFallback() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryItemLabelGenerator base = new CategoryItemLabelGenerator() {
            public String generateLabel(CategoryDataset dataset, int row, int column) {
                return "B";
            }
        };
        renderer.setBaseItemLabelGenerator(base);
        assertSame(base, renderer.getItemLabelGenerator(1, 2, false));
    }

    // covers: setSeriesItemLabelGenerator(series, generator, false) notify=false branch
    @Test
    public void testSetSeriesItemLabelGenerator_notifyFalse_getterReflectsChange() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryItemLabelGenerator gen = new CategoryItemLabelGenerator() {
            public String generateLabel(CategoryDataset dataset, int row, int column) {
                return "X";
            }
        };
        renderer.setSeriesItemLabelGenerator(2, gen, false);
        assertSame(gen, renderer.getSeriesItemLabelGenerator(2));
    }

    // covers: setBaseItemLabelGenerator(generator)/getBaseItemLabelGenerator()
    @Test
    public void testSetBaseItemLabelGenerator_getBaseItemLabelGenerator() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryItemLabelGenerator gen = new CategoryItemLabelGenerator() {
            public String generateLabel(CategoryDataset dataset, int row, int column) {
                return "Y";
            }
        };
        renderer.setBaseItemLabelGenerator(gen);
        assertSame(gen, renderer.getBaseItemLabelGenerator());
    }

    // covers: getToolTipGenerator falls back to base tooltip generator
    @Test
    public void testGetToolTipGenerator_baseFallback() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryToolTipGenerator base = new CategoryToolTipGenerator() {
            public String generateToolTip(CategoryDataset dataset, int row, int column) {
                return "tip";
            }
        };
        renderer.setBaseToolTipGenerator(base);
        assertSame(base, renderer.getToolTipGenerator(0, 0, false));
    }

    // covers: setSeriesToolTipGenerator/getSeriesToolTipGenerator
    @Test
    public void testGetSeriesToolTipGenerator_setAndGet() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryToolTipGenerator gen = new CategoryToolTipGenerator() {
            public String generateToolTip(CategoryDataset dataset, int row, int column) {
                return "s";
            }
        };
        renderer.setSeriesToolTipGenerator(1, gen);
        assertSame(gen, renderer.getSeriesToolTipGenerator(1));
    }

    // covers: setBaseToolTipGenerator(generator, false) notify=false branch
    @Test
    public void testSetBaseToolTipGenerator_notifyFalse_getterReflectsChange() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryToolTipGenerator gen = new CategoryToolTipGenerator() {
            public String generateToolTip(CategoryDataset dataset, int row, int column) {
                return "n";
            }
        };
        renderer.setBaseToolTipGenerator(gen, false);
        assertSame(gen, renderer.getBaseToolTipGenerator());
    }

    // covers: getURLGenerator uses series generator over base, and base as fallback
    @Test
    public void testGetURLGenerator_seriesOverridesBase() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryURLGenerator base = new CategoryURLGenerator() {
            public String generateURL(CategoryDataset dataset, int series, int category) {
                return "base";
            }
        };
        CategoryURLGenerator seriesGen = new CategoryURLGenerator() {
            public String generateURL(CategoryDataset dataset, int series, int category) {
                return "series";
            }
        };
        renderer.setBaseURLGenerator(base);
        renderer.setSeriesURLGenerator(0, seriesGen);
        assertSame(seriesGen, renderer.getURLGenerator(0, 0, false));
        assertSame(base, renderer.getURLGenerator(1, 0, false));
    }

    // covers: setBaseURLGenerator(generator, true)/getBaseURLGenerator()
    @Test
    public void testGetBaseURLGenerator_setAndGet() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryURLGenerator gen = new CategoryURLGenerator() {
            public String generateURL(CategoryDataset dataset, int series, int category) {
                return "u";
            }
        };
        renderer.setBaseURLGenerator(gen, true);
        assertSame(gen, renderer.getBaseURLGenerator());
    }

    // covers: addAnnotation(annotation) defaults to FOREGROUND layer
    @Test
    public void testAddAnnotation_defaultLayer_addsToForeground() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        final boolean[] drawn = new boolean[1];
        CategoryAnnotation ann = new CategoryAnnotation() {
            public void draw(Graphics2D g2, CategoryPlot plot, Rectangle2D dataArea,
                    CategoryAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                    PlotRenderingInfo info) {
                drawn[0] = true;
            }
        };
        renderer.addAnnotation(ann);
        CategoryAxis domainAxis = new CategoryAxis("x");
        NumberAxis rangeAxis = new NumberAxis("y");
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 10, 10);
        renderer.drawAnnotations(g2, dataArea, domainAxis, rangeAxis, Layer.FOREGROUND, null);
        assertTrue(drawn[0]);
    }

    // covers: addAnnotation(null) throws IllegalArgumentException
    @Test
    public void testAddAnnotation_nullAnnotation_throwsIllegalArgumentException() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        try {
            renderer.addAnnotation(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: addAnnotation(annotation, Layer.BACKGROUND) branch
    @Test
    public void testAddAnnotation_backgroundLayer_addsToBackground() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        final boolean[] drawn = new boolean[1];
        CategoryAnnotation ann = new CategoryAnnotation() {
            public void draw(Graphics2D g2, CategoryPlot plot, Rectangle2D dataArea,
                    CategoryAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                    PlotRenderingInfo info) {
                drawn[0] = true;
            }
        };
        renderer.addAnnotation(ann, Layer.BACKGROUND);
        CategoryAxis domainAxis = new CategoryAxis("x");
        NumberAxis rangeAxis = new NumberAxis("y");
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 10, 10);
        renderer.drawAnnotations(g2, dataArea, domainAxis, rangeAxis, Layer.BACKGROUND, null);
        assertTrue(drawn[0]);
        drawn[0] = false;
        renderer.drawAnnotations(g2, dataArea, domainAxis, rangeAxis, Layer.FOREGROUND, null);
        assertFalse(drawn[0]);
    }

    // covers: removeAnnotation on annotation absent from both lists returns false
    @Test
    public void testRemoveAnnotation_notPresent_returnsFalse() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryAnnotation ann = new CategoryAnnotation() {
            public void draw(Graphics2D g2, CategoryPlot plot, Rectangle2D dataArea,
                    CategoryAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                    PlotRenderingInfo info) {
            }
        };
        assertFalse(renderer.removeAnnotation(ann));
    }

    // covers: removeAnnotations() clears both foreground and background lists
    @Test
    public void testRemoveAnnotations_clearsBoth() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        final boolean[] drawn = new boolean[1];
        CategoryAnnotation ann = new CategoryAnnotation() {
            public void draw(Graphics2D g2, CategoryPlot plot, Rectangle2D dataArea,
                    CategoryAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                    PlotRenderingInfo info) {
                drawn[0] = true;
            }
        };
        renderer.addAnnotation(ann, Layer.FOREGROUND);
        renderer.addAnnotation(ann, Layer.BACKGROUND);
        renderer.removeAnnotations();
        CategoryAxis domainAxis = new CategoryAxis("x");
        NumberAxis rangeAxis = new NumberAxis("y");
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 10, 10);
        renderer.drawAnnotations(g2, dataArea, domainAxis, rangeAxis, Layer.FOREGROUND, null);
        renderer.drawAnnotations(g2, dataArea, domainAxis, rangeAxis, Layer.BACKGROUND, null);
        assertFalse(drawn[0]);
    }

    // covers: setLegendItemLabelGenerator(null) throws IllegalArgumentException
    @Test
    public void testSetLegendItemLabelGenerator_nullArgument_throwsIllegalArgumentException() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        try {
            renderer.setLegendItemLabelGenerator(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: getLegendItemLabelGenerator() default never null
    @Test
    public void testGetLegendItemLabelGenerator_defaultNotNull() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        assertNotNull(renderer.getLegendItemLabelGenerator());
    }

    // covers: setLegendItemToolTipGenerator/getLegendItemToolTipGenerator
    @Test
    public void testSetLegendItemToolTipGenerator_setAndGet() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategorySeriesLabelGenerator gen = new StandardCategorySeriesLabelGenerator();
        renderer.setLegendItemToolTipGenerator(gen);
        assertSame(gen, renderer.getLegendItemToolTipGenerator());
    }

    // covers: setLegendItemURLGenerator/getLegendItemURLGenerator
    @Test
    public void testSetLegendItemURLGenerator_setAndGet() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategorySeriesLabelGenerator gen = new StandardCategorySeriesLabelGenerator();
        renderer.setLegendItemURLGenerator(gen);
        assertSame(gen, renderer.getLegendItemURLGenerator());
    }

    // covers: getRowCount()/getColumnCount() default zero before initialise
    @Test
    public void testGetRowCountColumnCount_initiallyZero() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        assertEquals(0, renderer.getRowCount());
        assertEquals(0, renderer.getColumnCount());
    }

    // covers: initialise() with non-null dataset updates row/column counts
    @Test
    public void testInitialise_withDataset_updatesRowAndColumnCount() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "R1", "C1");
        dataset.addValue(2.0, "R2", "C1");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryAxis domainAxis = new CategoryAxis("x");
        NumberAxis rangeAxis = new NumberAxis("y");
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 10, 10);
        CategoryItemRendererState state = renderer.initialise(g2, dataArea, plot, dataset, null);
        assertNotNull(state);
        assertEquals(2, renderer.getRowCount());
        assertEquals(1, renderer.getColumnCount());
    }

    // covers: initialise() with null dataset sets counts to zero
    @Test
    public void testInitialise_withNullDataset_zeroCounts() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryAxis domainAxis = new CategoryAxis("x");
        NumberAxis rangeAxis = new NumberAxis("y");
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 10, 10);
        renderer.initialise(g2, dataArea, plot, null, null);
        assertEquals(0, renderer.getRowCount());
        assertEquals(0, renderer.getColumnCount());
    }

    // covers: findRangeBounds(dataset) with null dataset returns null
    @Test
    public void testFindRangeBounds_nullDataset_returnsNull() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        assertNull(renderer.findRangeBounds(null));
    }

    // covers: findRangeBounds(dataset) computes correct min/max range
    @Test
    public void testFindRangeBounds_withDataset_returnsCorrectRange() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "R1", "C1");
        dataset.addValue(5.0, "R1", "C2");
        dataset.addValue(3.0, "R2", "C1");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        Range range = renderer.findRangeBounds(dataset);
        assertNotNull(range);
        assertEquals(1.0, range.getLowerBound(), 1e-9);
        assertEquals(5.0, range.getUpperBound(), 1e-9);
    }

    // covers: protected findRangeBounds(dataset, includeInterval) overload
    @Test
    public void testFindRangeBoundsIncludeInterval_withDataset_returnsRange() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(2.0, "R1", "C1");
        dataset.addValue(4.0, "R1", "C2");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        Range range = renderer.findRangeBounds(dataset, true);
        assertNotNull(range);
        assertEquals(2.0, range.getLowerBound(), 1e-9);
        assertEquals(4.0, range.getUpperBound(), 1e-9);
    }

    // covers: getLegendItems() must return legend items when dataset is not null (bug target)
    @Test
    public void testGetLegendItems_withDataset_shouldReturnLegendItemForVisibleSeries() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "Series1", "Category1");
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Value");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        LegendItemCollection items = renderer.getLegendItems();
        assertEquals(1, items.getItemCount());
    }

    // covers: getLegendItems() when plot is null returns empty collection
    @Test
    public void testGetLegendItems_noPlot_returnsEmptyCollection() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        LegendItemCollection items = renderer.getLegendItems();
        assertEquals(0, items.getItemCount());
    }

    // covers: getLegendItem(datasetIndex, series) builds a correct legend item
    @Test
    public void testGetLegendItem_returnsLegendItemWithCorrectLabelAndSeriesKey() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(2.0, "SeriesA", "Cat1");
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Value");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        LegendItem item = renderer.getLegendItem(0, 0);
        assertNotNull(item);
        assertEquals("SeriesA", item.getLabel());
    }

    // covers: getLegendItem(datasetIndex, series) returns null when plot not set
    @Test
    public void testGetLegendItem_noPlotSet_returnsNull() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        assertNull(renderer.getLegendItem(0, 0));
    }

    // covers: equals() two freshly constructed renderers of same type are equal
    @Test
    public void testEquals_twoDefaultRenderers_areEqual() throws Throwable {
        LineAndShapeRenderer r1 = new LineAndShapeRenderer();
        LineAndShapeRenderer r2 = new LineAndShapeRenderer();
        assertTrue(r1.equals(r2));
    }

    // covers: equals() with null and with different type both return false
    @Test
    public void testEquals_nullAndDifferentType_returnsFalse() throws Throwable {
        LineAndShapeRenderer r1 = new LineAndShapeRenderer();
        assertFalse(r1.equals(null));
        assertFalse(r1.equals("not a renderer"));
    }

    // covers: hashCode() is consistent across repeated calls
    @Test
    public void testHashCode_consistentAcrossCalls() throws Throwable {
        LineAndShapeRenderer r1 = new LineAndShapeRenderer();
        int h1 = r1.hashCode();
        int h2 = r1.hashCode();
        assertEquals(h1, h2);
    }

    // covers: clone() produces a distinct but equal object
    @Test
    public void testClone_producesEqualButDistinctObject() throws Throwable {
        LineAndShapeRenderer r1 = new LineAndShapeRenderer();
        Object clone = r1.clone();
        assertTrue(clone instanceof LineAndShapeRenderer);
        assertNotSame(r1, clone);
        assertTrue(r1.equals(clone));
    }

    // covers: getDrawingSupplier() returns null when no plot assigned
    @Test
    public void testGetDrawingSupplier_noPlot_returnsNull() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        assertNull(renderer.getDrawingSupplier());
    }

    // covers: updateCrosshairValues() with null orientation throws IllegalArgumentException
    @Test
    public void testUpdateCrosshairValues_nullOrientation_throwsIllegalArgumentException() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        try {
            renderer.updateCrosshairValues(null, "R1", "C1", 1.0, 0, 0.0, 0.0, null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: createHotSpotShape() always throws RuntimeException("Not implemented.")
    @Test
    public void testCreateHotSpotShape_alwaysThrowsRuntimeException() throws Throwable {
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        try {
            renderer.createHotSpotShape(null, null, null, null, null, null, 0, 0, false, null);
            fail("expected RuntimeException");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("Not implemented"));
        }
    }

    // covers: createHotSpotBounds() with existing value builds a 4x4 rectangle around point
    @Test
    public void testCreateHotSpotBounds_withValue_returnsRectangleAroundPoint() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(5.0, "R1", "C1");
        CategoryAxis domainAxis = new CategoryAxis("x");
        NumberAxis rangeAxis = new NumberAxis("y");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 100, 100);
        Rectangle2D result = renderer.createHotSpotBounds(g2, dataArea, plot, domainAxis,
                rangeAxis, dataset, 0, 0, false, null, null);
        assertNotNull(result);
        assertEquals(4.0, result.getWidth(), 1e-9);
        assertEquals(4.0, result.getHeight(), 1e-9);
    }

    // covers: hitTest() returns true when point is inside computed hot spot bounds
    @Test
    public void testHitTest_pointInsideBounds_returnsTrue() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(5.0, "R1", "C1");
        CategoryAxis domainAxis = new CategoryAxis("x");
        NumberAxis rangeAxis = new NumberAxis("y");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 100, 100);
        Rectangle2D bounds = renderer.createHotSpotBounds(g2, dataArea, plot, domainAxis,
                rangeAxis, dataset, 0, 0, false, null, null);
        boolean hit = renderer.hitTest(bounds.getCenterX(), bounds.getCenterY(), g2, dataArea,
                plot, domainAxis, rangeAxis, dataset, 0, 0, false, null);
        assertTrue(hit);
    }

    // covers: hitTest() returns false when point is far outside hot spot bounds
    @Test
    public void testHitTest_pointOutsideBounds_returnsFalse() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(5.0, "R1", "C1");
        CategoryAxis domainAxis = new CategoryAxis("x");
        NumberAxis rangeAxis = new NumberAxis("y");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 100, 100);
        boolean hit = renderer.hitTest(-1000.0, -1000.0, g2, dataArea, plot, domainAxis,
                rangeAxis, dataset, 0, 0, false, null);
        assertFalse(hit);
    }

    // covers: protected getDomainAxis(plot, dataset) returns the plot's domain axis
    @Test
    public void testGetDomainAxis_returnsAxisFromPlot() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        CategoryAxis domainAxis = new CategoryAxis("x");
        NumberAxis rangeAxis = new NumberAxis("y");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        CategoryAxis result = renderer.getDomainAxis(plot, dataset);
        assertSame(domainAxis, result);
    }

    // covers: protected getRangeAxis(plot, index) returns the plot's range axis
    @Test
    public void testGetRangeAxis_returnsAxisFromPlot() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        CategoryAxis domainAxis = new CategoryAxis("x");
        NumberAxis rangeAxis = new NumberAxis("y");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        ValueAxis result = renderer.getRangeAxis(plot, 0);
        assertSame(rangeAxis, result);
    }
}
