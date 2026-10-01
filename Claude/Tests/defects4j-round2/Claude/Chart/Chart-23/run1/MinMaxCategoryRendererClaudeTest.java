package org.jfree.chart.renderer.category;

import static org.junit.Assert.*;
import org.junit.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;

import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

public class MinMaxCategoryRendererClaudeTest {

    // isDrawLines(): default flag value is false per field initializer
    @Test
    public void testIsDrawLines_defaultValue_false() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        assertFalse(renderer.isDrawLines());
    }

    // setDrawLines(boolean): toggling true/false both reflected by getter
    @Test
    public void testSetDrawLines_trueThenFalse_reflectedInGetter() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setDrawLines(true);
        assertTrue(renderer.isDrawLines());
        renderer.setDrawLines(false);
        assertFalse(renderer.isDrawLines());
    }

    // getGroupPaint(): default value is Color.black per field initializer
    @Test
    public void testGetGroupPaint_defaultValue_isBlack() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        assertEquals(Color.black, renderer.getGroupPaint());
    }

    // setGroupPaint(Paint): valid paint updates value
    @Test
    public void testSetGroupPaint_validPaint_updatesValue() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setGroupPaint(Color.red);
        assertEquals(Color.red, renderer.getGroupPaint());
    }

    // setGroupPaint(Paint): null argument throws IllegalArgumentException
    @Test
    public void testSetGroupPaint_null_throwsIllegalArgumentException() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        try {
            renderer.setGroupPaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // getGroupStroke(): default value is BasicStroke with width 1.0f
    @Test
    public void testGetGroupStroke_defaultValue_isBasicStrokeWidth1() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        Stroke stroke = renderer.getGroupStroke();
        assertTrue(stroke instanceof BasicStroke);
        assertEquals(1.0f, ((BasicStroke) stroke).getLineWidth(), 1e-9);
    }

    // setGroupStroke(Stroke): valid stroke updates value
    @Test
    public void testSetGroupStroke_validStroke_updatesValue() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        BasicStroke newStroke = new BasicStroke(3.5f);
        renderer.setGroupStroke(newStroke);
        assertEquals(newStroke, renderer.getGroupStroke());
    }

    // setGroupStroke(Stroke): null argument throws IllegalArgumentException
    @Test
    public void testSetGroupStroke_null_throwsIllegalArgumentException() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        try {
            renderer.setGroupStroke(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // getObjectIcon(): default icon is never null
    @Test
    public void testGetObjectIcon_defaultValue_notNull() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        assertNotNull(renderer.getObjectIcon());
    }

    // setObjectIcon(Icon): valid icon updates value (identity check)
    @Test
    public void testSetObjectIcon_validIcon_updatesValue() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        MinMaxCategoryRenderer other = new MinMaxCategoryRenderer();
        Icon newIcon = other.getMaxIcon();
        renderer.setObjectIcon(newIcon);
        assertSame(newIcon, renderer.getObjectIcon());
    }

    // setObjectIcon(Icon): null argument throws IllegalArgumentException
    @Test
    public void testSetObjectIcon_null_throwsIllegalArgumentException() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        try {
            renderer.setObjectIcon(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // getMaxIcon(): default icon is never null
    @Test
    public void testGetMaxIcon_defaultValue_notNull() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        assertNotNull(renderer.getMaxIcon());
    }

    // setMaxIcon(Icon): valid icon updates value (identity check)
    @Test
    public void testSetMaxIcon_validIcon_updatesValue() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        MinMaxCategoryRenderer other = new MinMaxCategoryRenderer();
        Icon newIcon = other.getMinIcon();
        renderer.setMaxIcon(newIcon);
        assertSame(newIcon, renderer.getMaxIcon());
    }

    // setMaxIcon(Icon): null argument throws IllegalArgumentException
    @Test
    public void testSetMaxIcon_null_throwsIllegalArgumentException() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        try {
            renderer.setMaxIcon(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // getMinIcon(): default icon is never null
    @Test
    public void testGetMinIcon_defaultValue_notNull() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        assertNotNull(renderer.getMinIcon());
    }

    // setMinIcon(Icon): valid icon updates value (identity check)
    @Test
    public void testSetMinIcon_validIcon_updatesValue() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        MinMaxCategoryRenderer other = new MinMaxCategoryRenderer();
        Icon newIcon = other.getObjectIcon();
        renderer.setMinIcon(newIcon);
        assertSame(newIcon, renderer.getMinIcon());
    }

    // setMinIcon(Icon): null argument throws IllegalArgumentException
    @Test
    public void testSetMinIcon_null_throwsIllegalArgumentException() throws Throwable {
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        try {
            renderer.setMinIcon(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // equals(): per Javadoc contract two default-state instances must be equal;
    // buggy version lacks the equals() override and falls back to reference equality.
    @Test
    public void testEquals_defaultInstances_equal() throws Throwable {
        MinMaxCategoryRenderer r1 = new MinMaxCategoryRenderer();
        MinMaxCategoryRenderer r2 = new MinMaxCategoryRenderer();
        assertTrue(r1.equals(r2));
    }

    // equals(): reflexive property, object equals itself
    @Test
    public void testEquals_sameInstance_reflexive() throws Throwable {
        MinMaxCategoryRenderer r1 = new MinMaxCategoryRenderer();
        assertTrue(r1.equals(r1));
    }

    // equals(): comparing with null must return false
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        MinMaxCategoryRenderer r1 = new MinMaxCategoryRenderer();
        assertFalse(r1.equals(null));
    }

    // equals(): instances with different plotLines flag must not be equal
    @Test
    public void testEquals_differentDrawLines_notEqual() throws Throwable {
        MinMaxCategoryRenderer r1 = new MinMaxCategoryRenderer();
        MinMaxCategoryRenderer r2 = new MinMaxCategoryRenderer();
        r2.setDrawLines(true);
        assertFalse(r1.equals(r2));
    }

    // equals(): instances with different groupPaint must not be equal
    @Test
    public void testEquals_differentGroupPaint_notEqual() throws Throwable {
        MinMaxCategoryRenderer r1 = new MinMaxCategoryRenderer();
        MinMaxCategoryRenderer r2 = new MinMaxCategoryRenderer();
        r2.setGroupPaint(Color.red);
        assertFalse(r1.equals(r2));
    }

    // equals(): instances with different groupStroke must not be equal
    @Test
    public void testEquals_differentGroupStroke_notEqual() throws Throwable {
        MinMaxCategoryRenderer r1 = new MinMaxCategoryRenderer();
        MinMaxCategoryRenderer r2 = new MinMaxCategoryRenderer();
        r2.setGroupStroke(new BasicStroke(4.0f));
        assertFalse(r1.equals(r2));
    }

    // drawItem(): single non-null value produces exactly one item entity
    @Test
    public void testDrawItem_singleValue_addsOneEntity() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(10.0, "Series1", "Cat1");
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Value");
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        JFreeChart chart = new JFreeChart(plot);
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        ChartRenderingInfo info = new ChartRenderingInfo();
        chart.draw(g2, new Rectangle2D.Double(0, 0, 200, 200), info);
        EntityCollection entities = info.getEntityCollection();
        assertEquals(1, entities.getEntityCount());
    }

    // drawItem(): null value in dataset must not produce an item entity
    @Test
    public void testDrawItem_nullValue_noEntityAdded() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue((Number) null, "Series1", "Cat1");
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Value");
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        JFreeChart chart = new JFreeChart(plot);
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        ChartRenderingInfo info = new ChartRenderingInfo();
        chart.draw(g2, new Rectangle2D.Double(0, 0, 200, 200), info);
        EntityCollection entities = info.getEntityCollection();
        assertEquals(0, entities.getEntityCount());
    }

    // drawItem(): multiple series sharing the same category, each non-null value adds an entity (min/max line branch)
    @Test
    public void testDrawItem_multipleSeriesSameCategory_addsEntityPerRow() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(5.0, "Series1", "Cat1");
        dataset.addValue(15.0, "Series2", "Cat1");
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Value");
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        JFreeChart chart = new JFreeChart(plot);
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        ChartRenderingInfo info = new ChartRenderingInfo();
        chart.draw(g2, new Rectangle2D.Double(0, 0, 200, 200), info);
        EntityCollection entities = info.getEntityCollection();
        assertEquals(2, entities.getEntityCount());
    }

    // drawItem(): horizontal orientation branch still produces entity for non-null value
    @Test
    public void testDrawItem_horizontalOrientation_addsEntity() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(7.0, "Series1", "Cat1");
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Value");
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        plot.setOrientation(PlotOrientation.HORIZONTAL);
        JFreeChart chart = new JFreeChart(plot);
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        ChartRenderingInfo info = new ChartRenderingInfo();
        chart.draw(g2, new Rectangle2D.Double(0, 0, 200, 200), info);
        EntityCollection entities = info.getEntityCollection();
        assertEquals(1, entities.getEntityCount());
    }

    // drawItem(): plotLines=true with multiple categories exercises the connecting-line branch, entity count unaffected
    @Test
    public void testDrawItem_drawLinesTrueMultipleCategories_addsEntityPerDataPoint() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(3.0, "Series1", "Cat1");
        dataset.addValue(9.0, "Series1", "Cat2");
        MinMaxCategoryRenderer renderer = new MinMaxCategoryRenderer();
        renderer.setDrawLines(true);
        CategoryAxis domainAxis = new CategoryAxis("Category");
        NumberAxis rangeAxis = new NumberAxis("Value");
        CategoryPlot plot = new CategoryPlot(dataset, domainAxis, rangeAxis, renderer);
        JFreeChart chart = new JFreeChart(plot);
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        ChartRenderingInfo info = new ChartRenderingInfo();
        chart.draw(g2, new Rectangle2D.Double(0, 0, 200, 200), info);
        EntityCollection entities = info.getEntityCollection();
        assertEquals(2, entities.getEntityCount());
    }
}
