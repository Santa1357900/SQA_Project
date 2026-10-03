package org.jfree.chart.plot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.urls.StandardPieURLGenerator;
import org.jfree.chart.util.RectangleInsets;
import org.jfree.chart.util.Rotation;
import org.jfree.data.general.DefaultPieDataset;
import org.junit.Test;

public class PiePlotTest {

    @Test
    public void testConstructors() throws Throwable {
        PiePlot plot1 = new PiePlot();
        assertNull(plot1.getDataset());
        assertTrue(plot1.isCircular());

        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("A", 10.0);
        PiePlot plot2 = new PiePlot(dataset);
        assertEquals(dataset, plot2.getDataset());
    }

    @Test
    public void testSetDataset() throws Throwable {
        PiePlot plot = new PiePlot();
        DefaultPieDataset dataset1 = new DefaultPieDataset();
        dataset1.setValue("Key1", 50.0);
        
        plot.setDataset(dataset1);
        assertEquals(dataset1, plot.getDataset());

        plot.setDataset(null);
        assertNull(plot.getDataset());
    }

    @Test
    public void testPieIndex() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(0, plot.getPieIndex());
        plot.setPieIndex(5);
        assertEquals(5, plot.getPieIndex());
    }

    @Test
    public void testStartAngle() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(90.0, plot.getStartAngle(), 0.001);
        plot.setStartAngle(45.0);
        assertEquals(45.0, plot.getStartAngle(), 0.001);
    }

    @Test
    public void testDirection() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(Rotation.CLOCKWISE, plot.getDirection());
        
        plot.setDirection(Rotation.ANTICLOCKWISE);
        assertEquals(Rotation.ANTICLOCKWISE, plot.getDirection());

        try {
            plot.setDirection(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'direction'"));
        }
    }

    @Test
    public void testInteriorGap() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(PiePlot.DEFAULT_INTERIOR_GAP, plot.getInteriorGap(), 0.001);

        plot.setInteriorGap(0.15);
        assertEquals(0.15, plot.getInteriorGap(), 0.001);

        try {
            plot.setInteriorGap(-0.1);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid 'percent'"));
        }

        try {
            plot.setInteriorGap(0.5);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid 'percent'"));
        }
    }

    @Test
    public void testCircular() throws Throwable {
        PiePlot plot = new PiePlot();
        assertTrue(plot.isCircular());

        plot.setCircular(false);
        assertFalse(plot.isCircular());

        plot.setCircular(true, false);
        assertTrue(plot.isCircular());
    }

    @Test
    public void testIgnoreNullAndZeroValues() throws Throwable {
        PiePlot plot = new PiePlot();
        assertFalse(plot.getIgnoreNullValues());
        assertFalse(plot.getIgnoreZeroValues());

        plot.setIgnoreNullValues(true);
        assertTrue(plot.getIgnoreNullValues());

        plot.setIgnoreZeroValues(true);
        assertTrue(plot.getIgnoreZeroValues());
    }

    @Test
    public void testSectionPaint() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(Color.gray, plot.getBaseSectionPaint());

        plot.setBaseSectionPaint(Color.red);
        assertEquals(Color.red, plot.getBaseSectionPaint());

        try {
            plot.setBaseSectionPaint(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'paint'"));
        }

        assertNull(plot.getSectionPaint("Key1"));
        plot.setSectionPaint("Key1", Color.blue);
        assertEquals(Color.blue, plot.getSectionPaint("Key1"));
    }

    @Test
    public void testSectionOutlines() throws Throwable {
        PiePlot plot = new PiePlot();
        assertTrue(plot.getSectionOutlinesVisible());

        plot.setSectionOutlinesVisible(false);
        assertFalse(plot.getSectionOutlinesVisible());

        assertEquals(Plot.DEFAULT_OUTLINE_PAINT, plot.getBaseSectionOutlinePaint());
        plot.setBaseSectionOutlinePaint(Color.green);
        assertEquals(Color.green, plot.getBaseSectionOutlinePaint());

        try {
            plot.setBaseSectionOutlinePaint(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'paint'"));
        }

        assertEquals(Plot.DEFAULT_OUTLINE_STROKE, plot.getBaseSectionOutlineStroke());
        BasicStroke stroke = new BasicStroke(2.0f);
        plot.setBaseSectionOutlineStroke(stroke);
        assertEquals(stroke, plot.getBaseSectionOutlineStroke());

        try {
            plot.setBaseSectionOutlineStroke(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'stroke'"));
        }

        assertNull(plot.getSectionOutlinePaint("Key1"));
        plot.setSectionOutlinePaint("Key1", Color.yellow);
        assertEquals(Color.yellow, plot.getSectionOutlinePaint("Key1"));

        assertNull(plot.getSectionOutlineStroke("Key1"));
        plot.setSectionOutlineStroke("Key1", stroke);
        assertEquals(stroke, plot.getSectionOutlineStroke("Key1"));
    }

    @Test
    public void testShadowAttributes() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(Color.gray, plot.getShadowPaint());
        plot.setShadowPaint(Color.darkGray);
        assertEquals(Color.darkGray, plot.getShadowPaint());

        assertEquals(4.0, plot.getShadowXOffset(), 0.001);
        plot.setShadowXOffset(5.0);
        assertEquals(5.0, plot.getShadowXOffset(), 0.001);

        assertEquals(4.0, plot.getShadowYOffset(), 0.001);
        plot.setShadowYOffset(6.0);
        assertEquals(6.0, plot.getShadowYOffset(), 0.001);
    }

    @Test
    public void testExplodePercent() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(0.0, plot.getExplodePercent("Key1"), 0.001);

        plot.setExplodePercent("Key1", 0.2);
        assertEquals(0.2, plot.getExplodePercent("Key1"), 0.001);

        try {
            plot.setExplodePercent(null, 0.1);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'key'"));
        }

        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("Key1", 10.0);
        dataset.setValue("Key2", 20.0);
        plot.setDataset(dataset);
        assertEquals(0.2, plot.getMaximumExplodePercent(), 0.001);
    }

    @Test
    public void testLabelsAndGenerators() throws Throwable {
        PiePlot plot = new PiePlot();
        assertNotNull(plot.getLabelGenerator());
        
        plot.setLabelGenerator(null);
        assertNull(plot.getLabelGenerator());

        StandardPieSectionLabelGenerator gen = new StandardPieSectionLabelGenerator();
        plot.setLabelGenerator(gen);
        assertEquals(gen, plot.getLabelGenerator());

        assertEquals(0.025, plot.getLabelGap(), 0.001);
        plot.setLabelGap(0.05);
        assertEquals(0.05, plot.getLabelGap(), 0.001);

        assertEquals(0.14, plot.getMaximumLabelWidth(), 0.001);
        plot.setMaximumLabelWidth(0.2);
        assertEquals(0.2, plot.getMaximumLabelWidth(), 0.001);

        assertTrue(plot.getLabelLinksVisible());
        plot.setLabelLinksVisible(false);
        assertFalse(plot.getLabelLinksVisible());

        assertEquals(0.025, plot.getLabelLinkMargin(), 0.001);
        plot.setLabelLinkMargin(0.03);
        assertEquals(0.03, plot.getLabelLinkMargin(), 0.001);

        assertEquals(Color.black, plot.getLabelLinkPaint());
        plot.setLabelLinkPaint(Color.red);
        assertEquals(Color.red, plot.getLabelLinkPaint());

        try {
            plot.setLabelLinkPaint(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'paint'"));
        }

        BasicStroke stroke = new BasicStroke(1.0f);
        plot.setLabelLinkStroke(stroke);
        assertEquals(stroke, plot.getLabelLinkStroke());

        try {
            plot.setLabelLinkStroke(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'stroke'"));
        }

        Font font = new Font("Dialog", Font.BOLD, 12);
        plot.setLabelFont(font);
        assertEquals(font, plot.getLabelFont());

        try {
            plot.setLabelFont(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'font'"));
        }

        plot.setLabelPaint(Color.orange);
        assertEquals(Color.orange, plot.getLabelPaint());

        try {
            plot.setLabelPaint(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'paint'"));
        }

        plot.setLabelBackgroundPaint(Color.cyan);
        assertEquals(Color.cyan, plot.getLabelBackgroundPaint());

        plot.setLabelOutlinePaint(Color.magenta);
        assertEquals(Color.magenta, plot.getLabelOutlinePaint());

        plot.setLabelOutlineStroke(stroke);
        assertEquals(stroke, plot.getLabelOutlineStroke());

        plot.setLabelShadowPaint(Color.lightGray);
        assertEquals(Color.lightGray, plot.getLabelShadowPaint());

        RectangleInsets padding = new RectangleInsets(1, 1, 1, 1);
        plot.setLabelPadding(padding);
        assertEquals(padding, plot.getLabelPadding());

        try {
            plot.setLabelPadding(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'padding'"));
        }

        assertFalse(plot.getSimpleLabels());
        plot.setSimpleLabels(true);
        assertTrue(plot.getSimpleLabels());

        RectangleInsets offset = new RectangleInsets(2, 2, 2, 2);
        plot.setSimpleLabelOffset(offset);
        assertEquals(offset, plot.getSimpleLabelOffset());

        try {
            plot.setSimpleLabelOffset(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'offset'"));
        }

        assertNotNull(plot.getLabelDistributor());
        PieLabelDistributor distributor = new PieLabelDistributor(1);
        plot.setLabelDistributor(distributor);
        assertEquals(distributor, plot.getLabelDistributor());

        try {
            plot.setLabelDistributor(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'distributor'"));
        }
    }

    @Test
    public void testToolTipAndURLGenerators() throws Throwable {
        PiePlot plot = new PiePlot();
        assertNull(plot.getToolTipGenerator());
        plot.setToolTipGenerator(null);
        assertNull(plot.getToolTipGenerator());

        assertNull(plot.getURLGenerator());
        StandardPieURLGenerator urlGen = new StandardPieURLGenerator();
        plot.setURLGenerator(urlGen);
        assertEquals(urlGen, plot.getURLGenerator());

        assertNotNull(plot.getLegendLabelGenerator());
        plot.setLegendLabelGenerator(new StandardPieSectionLabelGenerator());
        try {
            plot.setLegendLabelGenerator(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'generator'"));
        }

        assertNull(plot.getLegendLabelToolTipGenerator());
        plot.setLegendLabelToolTipGenerator(new StandardPieSectionLabelGenerator());

        assertNull(plot.getLegendLabelURLGenerator());
        plot.setLegendLabelURLGenerator(urlGen);
        assertEquals(urlGen, plot.getLegendLabelURLGenerator());
    }

    @Test
    public void testMinimumArcAngle() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(PiePlot.DEFAULT_MINIMUM_ARC_ANGLE_TO_DRAW, plot.getMinimumArcAngleToDraw(), 0.000001);
        plot.setMinimumArcAngleToDraw(0.001);
        assertEquals(0.001, plot.getMinimumArcAngleToDraw(), 0.000001);
    }

    @Test
    public void testLegendItemShape() throws Throwable {
        PiePlot plot = new PiePlot();
        assertNotNull(plot.getLegendItemShape());
        java.awt.Shape shape = new Rectangle2D.Double(0, 0, 10, 10);
        plot.setLegendItemShape(shape);
        assertEquals(shape, plot.getLegendItemShape());

        try {
            plot.setLegendItemShape(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null 'shape'"));
        }
    }

    @Test
    public void testGetLegendItems() throws Throwable {
        PiePlot plot = new PiePlot();
        LegendItemCollection items = plot.getLegendItems();
        assertNotNull(items);
        assertEquals(0, items.getItemCount());

        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("A", 10.0);
        dataset.setValue("B", 0.0);
        dataset.setValue("C", null);
        plot.setDataset(dataset);

        items = plot.getLegendItems();
        assertNotNull(items);
        assertEquals(2, items.getItemCount()); // A and B are included by default, C(null) ignored
    }

    @Test
    public void testGetPlotType() throws Throwable {
        PiePlot plot = new PiePlot();
        assertNotNull(plot.getPlotType());
    }

    @Test
    public void testEqualsAndClone() throws Throwable {
        PiePlot plot1 = new PiePlot();
        PiePlot plot2 = new PiePlot();
        assertTrue(plot1.equals(plot2));

        plot1.setStartAngle(45.0);
        assertFalse(plot1.equals(plot2));
        plot2.setStartAngle(45.0);
        assertTrue(plot1.equals(plot2));

        PiePlot clone = (PiePlot) plot1.clone();
        assertTrue(plot1.equals(clone));
    }

    @Test
    public void testDrawMethods() throws Throwable {
        PiePlot plot = new PiePlot();
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("Section 1", 20.0);
        dataset.setValue("Section 2", 80.0);
        plot.setDataset(dataset);

        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        Rectangle2D area = new Rectangle2D.Double(0, 0, 400, 300);

        plot.draw(g2, area, null, null, null);

        // Test with empty dataset to cover drawNoDataMessage
        plot.setDataset(new DefaultPieDataset());
        plot.draw(g2, area, null, null, null);
    }
}