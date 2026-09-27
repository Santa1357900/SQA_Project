package org.jfree.chart.renderer.category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.junit.Test;

public class MinMaxCategoryRendererTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        assertFalse(renderer.isDrawLines());
        assertNotNull(renderer.getGroupPaint());
        assertNotNull(renderer.getGroupStroke());
        assertNotNull(renderer.getObjectIcon());
        assertNotNull(renderer.getMaxIcon());
        assertNotNull(renderer.getMinIcon());
    }

    @Test
    public void testSetDrawLines() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setDrawLines(true);
        assertTrue(renderer.isDrawLines());
        renderer.setDrawLines(false);
        assertFalse(renderer.isDrawLines());
    }

    @Test
    public void testSetGroupPaint() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setGroupPaint(Color.RED);
        assertEquals(Color.RED, renderer.getGroupPaint());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetGroupPaintNull() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setGroupPaint(null);
    }

    @Test
    public void testSetGroupStroke() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        Stroke stroke = new BasicStroke(2.0f);
        renderer.setGroupStroke(stroke);
        assertEquals(stroke, renderer.getGroupStroke());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetGroupStrokeNull() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setGroupStroke(null);
    }

    @Test
    public void testSetObjectIcon() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        Icon icon = new ImageIcon();
        renderer.setObjectIcon(icon);
        assertEquals(icon, renderer.getObjectIcon());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetObjectIconNull() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setObjectIcon(null);
    }

    @Test
    public void testSetMaxIcon() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        Icon icon = new ImageIcon();
        renderer.setMaxIcon(icon);
        assertEquals(icon, renderer.getMaxIcon());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetMaxIconNull() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setMaxIcon(null);
    }

    @Test
    public void testSetMinIcon() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        Icon icon = new ImageIcon();
        renderer.setMinIcon(icon);
        assertEquals(icon, renderer.getMinIcon());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetMinIconNull() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setMinIcon(null);
    }

    @Test
    public void testDrawItemVertical() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setDrawLines(true);

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "S1", "C1");
        dataset.addValue(5.0, "S2", "C1");
        dataset.addValue(3.0, "S1", "C2");
        dataset.addValue(null, "S2", "C2");

        CategoryPlot plot = new CategoryPlot();
        plot.setOrientation(PlotOrientation.VERTICAL);

        CategoryAxis domainAxis = new CategoryAxis("Category");
        ValueAxis rangeAxis = new NumberAxis("Range");

        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 200, 100);

        CategoryItemRendererState state = renderer.initialise(g2, dataArea, plot, dataset, null);

        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 1, 0, 0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 1, 0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 1, 1, 0);

        g2.dispose();
        assertTrue(true);
    }

    @Test
    public void testDrawItemHorizontal() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setDrawLines(true);

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "S1", "C1");
        dataset.addValue(4.0, "S2", "C1");

        CategoryPlot plot = new CategoryPlot();
        plot.setOrientation(PlotOrientation.HORIZONTAL);

        CategoryAxis domainAxis = new CategoryAxis("Category");
        ValueAxis rangeAxis = new NumberAxis("Range");

        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 200, 100);

        CategoryItemRendererState state = renderer.initialise(g2, dataArea, plot, dataset, null);

        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 1, 0, 0);

        g2.dispose();
        assertTrue(true);
    }
}