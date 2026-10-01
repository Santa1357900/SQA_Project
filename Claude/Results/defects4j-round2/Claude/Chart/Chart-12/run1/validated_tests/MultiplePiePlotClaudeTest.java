package org.jfree.chart.plot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.util.TableOrder;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.PieDataset;

import org.junit.Test;
import static org.junit.Assert.*;

public class MultiplePiePlotClaudeTest {

    // Default constructor: dataset field must be null
    @Test
    public void testDefaultConstructor_datasetIsNull() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        assertNull(plot.getDataset());
    }

    // Default constructor: dataExtractOrder defaults to BY_COLUMN
    @Test
    public void testDefaultConstructor_dataExtractOrderIsByColumn() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        assertEquals(TableOrder.BY_COLUMN, plot.getDataExtractOrder());
    }

    // Default constructor: limit defaults to 0.0
    @Test
    public void testDefaultConstructor_limitIsZero() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        assertEquals(0.0, plot.getLimit(), 1e-9);
    }

    // Default constructor: pieChart is never null
    @Test
    public void testDefaultConstructor_pieChartNotNull() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        assertNotNull(plot.getPieChart());
    }

    // Constructor(dataset): getDataset returns exactly the instance passed in
    @Test
    public void testConstructorWithDataset_getDatasetReturnsSameInstance() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        assertSame(dataset, plot.getDataset());
    }

    // Bug hunt: changelog states plot must register itself as a dataset change
    // listener in the constructor (patch 1943021); verify via hasListener().
    @Test
    public void testConstructorWithDataset_registersAsChangeListener() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        assertTrue(dataset.hasListener(plot));
    }

    // Constructor(dataset): null is permitted
    @Test
    public void testConstructorWithNullDataset_datasetIsNull() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot((CategoryDataset) null);
        assertNull(plot.getDataset());
    }

    // setDataset: updates the dataset reference
    @Test
    public void testSetDataset_updatesDatasetReference() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        plot.setDataset(dataset);
        assertSame(dataset, plot.getDataset());
    }

    // setDataset: registers plot as a change listener on the new dataset
    @Test
    public void testSetDataset_registersAsChangeListener() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        plot.setDataset(dataset);
        assertTrue(dataset.hasListener(plot));
    }

    // setDataset: null is permitted and clears the dataset
    @Test
    public void testSetDataset_null_allowsNullDataset() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot(new DefaultCategoryDataset());
        plot.setDataset(null);
        assertNull(plot.getDataset());
    }

    // getPieChart: never null after construction
    @Test
    public void testGetPieChart_notNull() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        assertNotNull(plot.getPieChart());
    }

    // setPieChart: null argument throws IllegalArgumentException
    @Test
    public void testSetPieChart_null_throwsIllegalArgumentException() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        try {
            plot.setPieChart(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // setPieChart: chart whose plot is not a PiePlot throws IllegalArgumentException
    @Test
    public void testSetPieChart_nonPiePlotChart_throwsIllegalArgumentException() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        CategoryPlot categoryPlot = new CategoryPlot();
        JFreeChart chart = new JFreeChart(categoryPlot);
        try {
            plot.setPieChart(chart);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // setPieChart: valid PiePlot-based chart is accepted and stored
    @Test
    public void testSetPieChart_validPiePlotChart_updatesPieChart() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        PiePlot piePlot = new PiePlot((PieDataset) null);
        JFreeChart chart = new JFreeChart(piePlot);
        plot.setPieChart(chart);
        assertSame(chart, plot.getPieChart());
    }

    // setDataExtractOrder: null argument throws IllegalArgumentException
    @Test
    public void testSetDataExtractOrder_null_throwsIllegalArgumentException() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        try {
            plot.setDataExtractOrder(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // setDataExtractOrder: BY_ROW is accepted and stored
    @Test
    public void testSetDataExtractOrder_byRow_updatesOrder() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        plot.setDataExtractOrder(TableOrder.BY_ROW);
        assertEquals(TableOrder.BY_ROW, plot.getDataExtractOrder());
    }

    // setLimit: value is stored and retrievable
    @Test
    public void testSetLimit_updatesLimit() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        plot.setLimit(0.25);
        assertEquals(0.25, plot.getLimit(), 1e-9);
    }

    // getAggregatedItemsKey: default value is "Other"
    @Test
    public void testGetAggregatedItemsKey_default_returnsOther() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        assertEquals("Other", plot.getAggregatedItemsKey());
    }

    // setAggregatedItemsKey: null argument throws IllegalArgumentException
    @Test
    public void testSetAggregatedItemsKey_null_throwsIllegalArgumentException() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        try {
            plot.setAggregatedItemsKey(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // setAggregatedItemsKey: value is stored and retrievable
    @Test
    public void testSetAggregatedItemsKey_updatesKey() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        plot.setAggregatedItemsKey("Misc");
        assertEquals("Misc", plot.getAggregatedItemsKey());
    }

    // getAggregatedItemsPaint: default value is Color.lightGray
    @Test
    public void testGetAggregatedItemsPaint_default_returnsLightGray() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        assertEquals(Color.lightGray, plot.getAggregatedItemsPaint());
    }

    // setAggregatedItemsPaint: null argument throws IllegalArgumentException
    @Test
    public void testSetAggregatedItemsPaint_null_throwsIllegalArgumentException() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        try {
            plot.setAggregatedItemsPaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // setAggregatedItemsPaint: value is stored and retrievable
    @Test
    public void testSetAggregatedItemsPaint_updatesPaint() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        plot.setAggregatedItemsPaint(Color.red);
        assertEquals(Color.red, plot.getAggregatedItemsPaint());
    }

    // getPlotType: returns the fixed plot type string
    @Test
    public void testGetPlotType_returnsMultiplePiePlot() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        assertEquals("Multiple Pie Plot", plot.getPlotType());
    }

    // draw: null dataset takes the "no data" branch without throwing
    @Test
    public void testDraw_nullDataset_drawsNoDataMessageWithoutException() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 100);
        plot.draw(g2, area, null, null, null);
        assertNull(plot.getDataset());
    }

    // draw: empty (non-null) dataset also takes the "no data" branch
    @Test
    public void testDraw_emptyDataset_drawsNoDataMessageWithoutException() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 100);
        plot.draw(g2, area, null, null, null);
        assertEquals(0, dataset.getColumnCount());
    }

    // draw: BY_COLUMN extraction order draws each column as a pie without exception
    @Test
    public void testDraw_byColumnOrder_noException() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "Row1", "Col1");
        dataset.addValue(2.0, "Row1", "Col2");
        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 200, 200);
        plot.draw(g2, area, null, null, null);
        assertEquals(TableOrder.BY_COLUMN, plot.getDataExtractOrder());
    }

    // draw: BY_ROW extraction order draws each row as a pie without exception
    @Test
    public void testDraw_byRowOrder_noException() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "Row1", "Col1");
        dataset.addValue(2.0, "Row2", "Col1");
        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        plot.setDataExtractOrder(TableOrder.BY_ROW);
        BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 200, 200);
        plot.draw(g2, area, null, null, null);
        assertEquals(TableOrder.BY_ROW, plot.getDataExtractOrder());
    }

    // draw: limit > 0.0 triggers dataset consolidation branch without exception
    @Test
    public void testDraw_withLimitGreaterThanZero_noException() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(80.0, "Row1", "Col1");
        dataset.addValue(20.0, "Row1", "Col2");
        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        plot.setLimit(50.0);
        BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 200, 200);
        plot.draw(g2, area, null, null, null);
        assertEquals(50.0, plot.getLimit(), 1e-9);
    }

    // draw: displayCols > displayRows with a taller-than-wide area triggers the swap branch
    @Test
    public void testDraw_columnsGreaterThanRowsAndTallArea_swapsWithoutException() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "Row1", "Col1");
        dataset.addValue(2.0, "Row1", "Col2");
        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        BufferedImage img = new BufferedImage(50, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 50, 100);
        plot.draw(g2, area, null, null, null);
        assertEquals(2, dataset.getColumnCount());
    }

    // getLegendItems: null dataset returns an empty collection
    @Test
    public void testGetLegendItems_nullDataset_returnsEmptyCollection() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        LegendItemCollection items = plot.getLegendItems();
        assertEquals(0, items.getItemCount());
    }

    // getLegendItems: BY_COLUMN order produces one legend item per row key
    @Test
    public void testGetLegendItems_byColumnOrder_returnsItemPerRowKey() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "Row1", "Col1");
        dataset.addValue(2.0, "Row2", "Col1");
        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        LegendItemCollection items = plot.getLegendItems();
        assertEquals(2, items.getItemCount());
    }

    // getLegendItems: BY_ROW order produces one legend item per column key
    @Test
    public void testGetLegendItems_byRowOrder_returnsItemPerColumnKey() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "Row1", "Col1");
        dataset.addValue(2.0, "Row1", "Col2");
        dataset.addValue(3.0, "Row1", "Col3");
        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        plot.setDataExtractOrder(TableOrder.BY_ROW);
        LegendItemCollection items = plot.getLegendItems();
        assertEquals(3, items.getItemCount());
    }

    // getLegendItems: limit > 0.0 adds one extra "aggregated items" legend entry
    @Test
    public void testGetLegendItems_withLimitGreaterThanZero_addsAggregatedItem() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "Row1", "Col1");
        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        plot.setLimit(50.0);
        LegendItemCollection items = plot.getLegendItems();
        assertEquals(2, items.getItemCount());
    }

    // equals: same instance is equal to itself
    @Test
    public void testEquals_sameInstance_returnsTrue() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        assertTrue(plot.equals(plot));
    }

    // equals: null argument returns false
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        assertFalse(plot.equals(null));
    }

    // equals: object of a different class returns false
    @Test
    public void testEquals_differentClass_returnsFalse() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        assertFalse(plot.equals("not a plot"));
    }

    // equals: differing dataExtractOrder makes plots unequal
    @Test
    public void testEquals_differentDataExtractOrder_returnsFalse() throws Throwable {
        MultiplePiePlot p1 = new MultiplePiePlot();
        MultiplePiePlot p2 = new MultiplePiePlot();
        p2.setDataExtractOrder(TableOrder.BY_ROW);
        assertFalse(p1.equals(p2));
    }

    // equals: differing limit makes plots unequal
    @Test
    public void testEquals_differentLimit_returnsFalse() throws Throwable {
        MultiplePiePlot p1 = new MultiplePiePlot();
        MultiplePiePlot p2 = new MultiplePiePlot();
        p2.setLimit(10.0);
        assertFalse(p1.equals(p2));
    }

    // equals: differing aggregatedItemsKey makes plots unequal
    @Test
    public void testEquals_differentAggregatedItemsKey_returnsFalse() throws Throwable {
        MultiplePiePlot p1 = new MultiplePiePlot();
        MultiplePiePlot p2 = new MultiplePiePlot();
        p2.setAggregatedItemsKey("Misc");
        assertFalse(p1.equals(p2));
    }

    // equals: differing aggregatedItemsPaint makes plots unequal
    @Test
    public void testEquals_differentAggregatedItemsPaint_returnsFalse() throws Throwable {
        MultiplePiePlot p1 = new MultiplePiePlot();
        MultiplePiePlot p2 = new MultiplePiePlot();
        p2.setAggregatedItemsPaint(Color.red);
        assertFalse(p1.equals(p2));
    }

    // equals: two freshly constructed default plots are equal
    @Test
    public void testEquals_twoDefaultInstances_returnsTrue() throws Throwable {
        MultiplePiePlot p1 = new MultiplePiePlot();
        MultiplePiePlot p2 = new MultiplePiePlot();
        assertTrue(p1.equals(p2));
    }
}
