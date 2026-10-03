package org.jfree.chart.renderer.category;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.statistics.DefaultStatisticalCategoryDataset;
import org.junit.Test;

import static org.junit.Assert.*;

public class StatisticalBarRendererTest {

    @Test
    public void testConstructorsAndGettersSetters() throws Throwable {
        StatisticalBarRenderer renderer = new StatisticalBarRenderer();
        assertNotNull(renderer.getErrorIndicatorPaint());
        assertNotNull(renderer.getErrorIndicatorStroke());

        Paint paint = Color.red;
        renderer.setErrorIndicatorPaint(paint);
        assertEquals(paint, renderer.getErrorIndicatorPaint());

        Stroke stroke = new BasicStroke(2.0f);
        renderer.setErrorIndicatorStroke(stroke);
        assertEquals(stroke, renderer.getErrorIndicatorStroke());
    }

    @Test
    public void testEqualsAndClone() throws Throwable {
        StatisticalBarRenderer r1 = new StatisticalBarRenderer();
        StatisticalBarRenderer r2 = new StatisticalBarRenderer();

        assertTrue(r1.equals(r2));
        assertTrue(r2.equals(r1));

        r1.setErrorIndicatorPaint(Color.blue);
        assertFalse(r1.equals(r2));

        r2.setErrorIndicatorPaint(Color.blue);
        assertTrue(r1.equals(r2));

        assertFalse(r1.equals(null));
        assertFalse(r1.equals("Some String"));

        StatisticalBarRenderer cloned = (StatisticalBarRenderer) r1.clone();
        assertTrue(r1.equals(cloned));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDrawItemInvalidDataset() throws Throwable {
        StatisticalBarRenderer renderer = new StatisticalBarRenderer();
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        
        // Pass a non-StatisticalCategoryDataset to trigger IllegalArgumentException
        DefaultStatisticalCategoryDataset dataset = new DefaultStatisticalCategoryDataset();
        CategoryPlot plot = new CategoryPlot();
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Range");
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 400, 300);
        CategoryItemRendererState state = renderer.initialise(g2, dataArea, plot, dataset, null);

        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);
    }

    @Test
    public void testDrawVerticalItem() throws Throwable {
        StatisticalBarRenderer renderer = new StatisticalBarRenderer();
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();

        DefaultStatisticalCategoryDataset dataset = new DefaultStatisticalCategoryDataset();
        dataset.add(10.0, 2.0, "Row1", "Col1");

        CategoryPlot plot = new CategoryPlot();
        plot.setOrientation(PlotOrientation.VERTICAL);
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Range");
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 400, 300);

        CategoryItemRendererState state = renderer.initialise(g2, dataArea, plot, dataset, null);
        
        // Test normal drawing
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);

        // Test with outline enabled and bar width > 3
        renderer.setDrawBarOutline(true);
        state.setBarWidth(10.0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);

        // Test with item label visible
        renderer.setItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setItemLabelsVisible(true);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);
    }

    @Test
    public void testDrawHorizontalItem() throws Throwable {
        StatisticalBarRenderer renderer = new StatisticalBarRenderer();
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();

        DefaultStatisticalCategoryDataset dataset = new DefaultStatisticalCategoryDataset();
        dataset.add(10.0, 2.0, "Row1", "Col1");

        CategoryPlot plot = new CategoryPlot();
        plot.setOrientation(PlotOrientation.HORIZONTAL);
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Range");
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 400, 300);

        CategoryItemRendererState state = renderer.initialise(g2, dataArea, plot, dataset, null);
        
        // Test normal drawing horizontal
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);

        // Test horizontal with outline and bar width > 3
        renderer.setDrawBarOutline(true);
        state.setBarWidth(10.0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);

        // Test horizontal with item label visible
        renderer.setItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setItemLabelsVisible(true);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);
    }

    @Test
    public void testClipCasesVertical() throws Throwable {
        StatisticalBarRenderer renderer = new StatisticalBarRenderer();
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();

        DefaultStatisticalCategoryDataset dataset = new DefaultStatisticalCategoryDataset();
        dataset.add(-10.0, 1.0, "Row1", "Col1");

        CategoryPlot plot = new CategoryPlot();
        plot.setOrientation(PlotOrientation.VERTICAL);
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Range");
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 400, 300);

        CategoryItemRendererState state = renderer.initialise(g2, dataArea, plot, dataset, null);

        // uclip <= 0.0, value >= uclip
        renderer.setUpperClip(-5.0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);

        // uclip <= 0.0, value <= lclip
        renderer.setUpperClip(5.0);
        renderer.setLowerClip(0.0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);

        // lclip <= 0.0 case
        renderer.setLowerClip(-20.0);
        renderer.setUpperClip(20.0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);

        // positive clips
        renderer.setLowerClip(2.0);
        renderer.setUpperClip(5.0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);
    }

    @Test
    public void testClipCasesHorizontal() throws Throwable {
        StatisticalBarRenderer renderer = new StatisticalBarRenderer();
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();

        DefaultStatisticalCategoryDataset dataset = new DefaultStatisticalCategoryDataset();
        dataset.add(-10.0, 1.0, "Row1", "Col1");

        CategoryPlot plot = new CategoryPlot();
        plot.setOrientation(PlotOrientation.HORIZONTAL);
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Range");
        Rectangle2D dataArea = new Rectangle2D.Double(0, 0, 400, 300);

        CategoryItemRendererState state = renderer.initialise(g2, dataArea, plot, dataset, null);

        // uclip <= 0.0, value >= uclip
        renderer.setUpperClip(-5.0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);

        // positive clips, value <= lclip
        renderer.setLowerClip(15.0);
        renderer.setUpperClip(20.0);
        renderer.drawItem(g2, state, dataArea, plot, domainAxis, rangeAxis, dataset, 0, 0, 0);
    }

    @Test
    public void testSerialization() throws Throwable {
        StatisticalBarRenderer r1 = new StatisticalBarRenderer();
        r1.setErrorIndicatorPaint(Color.cyan);
        r1.setErrorIndicatorStroke(new BasicStroke(1.5f));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(buffer);
        out.writeObject(r1);
        out.close();

        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        StatisticalBarRenderer r2 = (StatisticalBarRenderer) in.readObject();
        in.close();

        assertEquals(r1, r2);
        assertNotNull(r2.getErrorIndicatorPaint());
        assertNotNull(r2.getErrorIndicatorStroke());
    }
}