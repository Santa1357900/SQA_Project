package org.jfree.chart.plot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.util.RectangleEdge;
import org.jfree.chart.util.TableOrder;
import org.jfree.data.category.DefaultCategoryDataset;
import org.junit.Test;

public class MultiplePiePlotTest {

    @Test
    public void testConstructorsAndDefaults() throws Throwable {
        MultiplePiePlot plot1 = new MultiplePiePlot();
        assertNull(plot1.getDataset());
        assertNotNull(plot1.getPieChart());
        assertEquals(TableOrder.BY_COLUMN, plot1.getDataExtractOrder());
        assertEquals(0.0, plot1.getLimit(), 0.0001);
        assertEquals("Other", plot1.getAggregatedItemsKey());
        assertEquals(Color.lightGray, plot1.getAggregatedItemsPaint());
        assertEquals("Multiple Pie Plot", plot1.getPlotType());

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        MultiplePiePlot plot2 = new MultiplePiePlot(dataset);
        assertSame(dataset, plot2.getDataset());
    }

    @Test
    public void testDatasetSettersAndListeners() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "Row1", "Col1");

        plot.setDataset(dataset);
        assertSame(dataset, plot.getDataset());

        // Remove dataset
        plot.setDataset(null);
        assertNull(plot.getDataset());
    }

    @Test
    public void testPieChartSetters() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        PiePlot piePlot = new PiePlot();
        JFreeChart newChart = new JFreeChart(piePlot);

        plot.setPieChart(newChart);
        assertSame(newChart, plot.getPieChart());

        try {
            plot.setPieChart(null);
            fail("Should have thrown IllegalArgumentException for null pieChart");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }

        try {
            JFreeChart invalidChart = new JFreeChart(new CategoryPlot());
            plot.setPieChart(invalidChart);
            fail("Should have thrown IllegalArgumentException for non-PiePlot chart");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("PiePlot"));
        }
    }

    @Test
    public void testDataExtractOrderSetters() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        plot.setDataExtractOrder(TableOrder.BY_ROW);
        assertEquals(TableOrder.BY_ROW, plot.getDataExtractOrder());

        try {
            plot.setDataExtractOrder(null);
            fail("Should have thrown IllegalArgumentException for null order");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("order"));
        }
    }

    @Test
    public void testLimitAndAggregatedItems() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        plot.setLimit(0.05);
        assertEquals(0.05, plot.getLimit(), 0.0001);

        plot.setAggregatedItemsKey("Rest");
        assertEquals("Rest", plot.getAggregatedItemsKey());

        try {
            plot.setAggregatedItemsKey(null);
            fail("Should have thrown IllegalArgumentException for null key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("key"));
        }

        plot.setAggregatedItemsPaint(Color.darkGray);
        assertEquals(Color.darkGray, plot.getAggregatedItemsPaint());

        try {
            plot.setAggregatedItemsPaint(null);
            fail("Should have thrown IllegalArgumentException for null paint");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("paint"));
        }
    }

    @Test
    public void testEquals() throws Throwable {
        MultiplePiePlot plot1 = new MultiplePiePlot();
        MultiplePiePlot plot2 = new MultiplePiePlot();

        assertTrue(plot1.equals(plot1));
        assertTrue(plot1.equals(plot2));

        plot1.setDataExtractOrder(TableOrder.BY_ROW);
        assertFalse(plot1.equals(plot2));
        plot2.setDataExtractOrder(TableOrder.BY_ROW);
        assertTrue(plot1.equals(plot2));

        plot1.setLimit(10.0);
        assertFalse(plot1.equals(plot2));
        plot2.setLimit(10.0);
        assertTrue(plot1.equals(plot2));

        plot1.setAggregatedItemsKey("DiffKey");
        assertFalse(plot1.equals(plot2));
        plot2.setAggregatedItemsKey("DiffKey");
        assertTrue(plot1.equals(plot2));

        plot1.setAggregatedItemsPaint(Color.red);
        assertFalse(plot1.equals(plot2));
        plot2.setAggregatedItemsPaint(Color.red);
        assertTrue(plot1.equals(plot2));

        PiePlot pp = new PiePlot();
        pp.setNoDataMessage("Test");
        plot1.setPieChart(new JFreeChart(pp));
        assertFalse(plot1.equals(plot2));

        assertFalse(plot1.equals(null));
        assertFalse(plot1.equals("NotAPlot"));
    }

    @Test
    public void testDrawWithNoData() throws Throwable {
        MultiplePiePlot plot = new MultiplePiePlot();
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 400, 300);

        plot.draw(g2, area, null, null, null);
        // Verify no exception thrown for empty dataset
    }

    @Test
    public void testDrawWithDatasetByColumn() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(10.0, "Series 1", "Category 1");
        dataset.addValue(20.0, "Series 1", "Category 2");
        dataset.addValue(15.0, "Series 2", "Category 1");
        dataset.addValue(25.0, "Series 2", "Category 2");

        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        plot.setDataExtractOrder(TableOrder.BY_COLUMN);
        plot.setLimit(5.0);

        BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 500, 500);

        plot.draw(g2, area, null, null, null);
        
        LegendItemCollection items = plot.getLegendItems();
        assertNotNull(items);
    }

    @Test
    public void testDrawWithDatasetByRow() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(10.0, "Series 1", "Category 1");
        dataset.addValue(20.0, "Series 1", "Category 2");
        dataset.addValue(15.0, "Series 2", "Category 1");
        dataset.addValue(25.0, "Series 2", "Category 2");

        MultiplePiePlot plot = new MultiplePiePlot(dataset);
        plot.setDataExtractOrder(TableOrder.BY_ROW);

        BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 500, 500);

        plot.draw(g2, area, null, null, null);

        LegendItemCollection items = plot.getLegendItems();
        assertNotNull(items);
    }
}