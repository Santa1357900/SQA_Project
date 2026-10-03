package org.jfree.chart.plot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.util.RectangleInsets;
import org.jfree.chart.util.Rotation;
import org.jfree.data.general.DefaultPieDataset;

public class PiePlotClaudeTest {

    // covers default constructor field initialisation
    @Test
    public void testDefaultConstructor_defaultValues() throws Throwable {
        PiePlot plot = new PiePlot();
        assertNull(plot.getDataset());
        assertEquals(PiePlot.DEFAULT_INTERIOR_GAP, plot.getInteriorGap(), 1e-9);
        assertTrue(plot.isCircular());
        assertEquals(PiePlot.DEFAULT_START_ANGLE, plot.getStartAngle(), 1e-9);
        assertEquals(Rotation.CLOCKWISE, plot.getDirection());
        assertFalse(plot.getIgnoreNullValues());
        assertFalse(plot.getIgnoreZeroValues());
        assertTrue(plot.getSectionOutlinesVisible());
    }

    // covers constructor(PieDataset) storing the dataset reference
    @Test
    public void testConstructorWithDataset_getDataset_returnsSameDataset() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        PiePlot plot = new PiePlot(dataset);
        assertSame(dataset, plot.getDataset());
    }

    // covers setDataset updating the stored dataset
    @Test
    public void testSetDataset_updatesDataset() throws Throwable {
        PiePlot plot = new PiePlot();
        DefaultPieDataset dataset = new DefaultPieDataset();
        plot.setDataset(dataset);
        assertSame(dataset, plot.getDataset());
    }

    // covers getPieIndex/setPieIndex
    @Test
    public void testGetSetPieIndex() throws Throwable {
        PiePlot plot = new PiePlot();
        plot.setPieIndex(5);
        assertEquals(5, plot.getPieIndex());
    }

    // covers setStartAngle updating value
    @Test
    public void testSetStartAngle_updatesValue() throws Throwable {
        PiePlot plot = new PiePlot();
        plot.setStartAngle(180.0);
        assertEquals(180.0, plot.getStartAngle(), 1e-9);
    }

    // covers setDirection null-check throw branch and valid-assignment branch
    @Test
    public void testSetDirection_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setDirection(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        plot.setDirection(Rotation.ANTICLOCKWISE);
        assertEquals(Rotation.ANTICLOCKWISE, plot.getDirection());
    }

    // covers setInteriorGap below-zero throw branch
    @Test
    public void testSetInteriorGap_belowZero_throwsException() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setInteriorGap(-0.01);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers setInteriorGap above-max throw branch
    @Test
    public void testSetInteriorGap_aboveMax_throwsException() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setInteriorGap(PiePlot.MAX_INTERIOR_GAP + 0.01);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers setInteriorGap valid boundary values (0.0 and MAX_INTERIOR_GAP)
    @Test
    public void testSetInteriorGap_atBoundaries_valid() throws Throwable {
        PiePlot plot = new PiePlot();
        plot.setInteriorGap(0.0);
        assertEquals(0.0, plot.getInteriorGap(), 1e-9);
        plot.setInteriorGap(PiePlot.MAX_INTERIOR_GAP);
        assertEquals(PiePlot.MAX_INTERIOR_GAP, plot.getInteriorGap(), 1e-9);
    }

    // covers setCircular(boolean) and setCircular(boolean, boolean) branches
    @Test
    public void testSetCircular_variants() throws Throwable {
        PiePlot plot = new PiePlot();
        plot.setCircular(false);
        assertFalse(plot.isCircular());
        plot.setCircular(true, false);
        assertTrue(plot.isCircular());
    }

    // covers setIgnoreNullValues
    @Test
    public void testSetIgnoreNullValues() throws Throwable {
        PiePlot plot = new PiePlot();
        plot.setIgnoreNullValues(true);
        assertTrue(plot.getIgnoreNullValues());
    }

    // covers setIgnoreZeroValues
    @Test
    public void testSetIgnoreZeroValues() throws Throwable {
        PiePlot plot = new PiePlot();
        plot.setIgnoreZeroValues(true);
        assertTrue(plot.getIgnoreZeroValues());
    }

    // covers lookupSectionPaint: no paint defined -> baseSectionPaint; paint defined -> returns it
    @Test
    public void testLookupSectionPaint_defaultAndAfterSet() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(plot.getBaseSectionPaint(), plot.lookupSectionPaint("X"));
        plot.setSectionPaint("X", Color.red);
        assertEquals(Color.red, plot.lookupSectionPaint("X"));
    }

    // covers getSectionKey: section index within dataset range returns dataset key
    @Test
    public void testGetSectionKey_withDataset_returnsDatasetKey() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("Alpha", new Double(1.0));
        PiePlot plot = new PiePlot(dataset);
        assertEquals("Alpha", plot.getSectionKey(0));
    }

    // covers getSectionKey: out-of-range index with dataset, and null dataset -> generated Integer key
    @Test
    public void testGetSectionKey_outOfRangeOrNullDataset_returnsIntegerKey() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("Alpha", new Double(1.0));
        PiePlot plotWithDataset = new PiePlot(dataset);
        assertEquals(new Integer(5), plotWithDataset.getSectionKey(5));

        PiePlot plotNoDataset = new PiePlot();
        assertEquals(new Integer(2), plotNoDataset.getSectionKey(2));
    }

    // covers getSectionPaint null-key throw branch (documented in javadoc)
    @Test
    public void testGetSectionPaint_nullKey_throwsException() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.getSectionPaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers setBaseSectionPaint null-check throw and valid-assignment branches
    @Test
    public void testSetBaseSectionPaint_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setBaseSectionPaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        plot.setBaseSectionPaint(Color.blue);
        assertEquals(Color.blue, plot.getBaseSectionPaint());
    }

    // covers lookupSectionOutlinePaint default and after explicit set
    @Test
    public void testLookupSectionOutlinePaint_defaultAndAfterSet() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(plot.getBaseSectionOutlinePaint(), plot.lookupSectionOutlinePaint("X"));
        plot.setSectionOutlinePaint("X", Color.green);
        assertEquals(Color.green, plot.lookupSectionOutlinePaint("X"));
    }

    // covers setBaseSectionOutlinePaint null-check throw and valid-assignment branches
    @Test
    public void testSetBaseSectionOutlinePaint_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setBaseSectionOutlinePaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        plot.setBaseSectionOutlinePaint(Color.orange);
        assertEquals(Color.orange, plot.getBaseSectionOutlinePaint());
    }

    // covers lookupSectionOutlineStroke default and after explicit set
    @Test
    public void testLookupSectionOutlineStroke_defaultAndAfterSet() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(plot.getBaseSectionOutlineStroke(), plot.lookupSectionOutlineStroke("X"));
        BasicStroke custom = new BasicStroke(3.0f);
        plot.setSectionOutlineStroke("X", custom);
        assertEquals(custom, plot.lookupSectionOutlineStroke("X"));
    }

    // covers setBaseSectionOutlineStroke null-check throw and valid-assignment branches
    @Test
    public void testSetBaseSectionOutlineStroke_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setBaseSectionOutlineStroke(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        BasicStroke custom = new BasicStroke(1.5f);
        plot.setBaseSectionOutlineStroke(custom);
        assertEquals(custom, plot.getBaseSectionOutlineStroke());
    }

    // covers setShadowPaint null-permitted branch
    @Test
    public void testSetShadowPaint_nullPermitted() throws Throwable {
        PiePlot plot = new PiePlot();
        plot.setShadowPaint(null);
        assertNull(plot.getShadowPaint());
    }

    // covers getExplodePercent default (no explode set) returns 0.0
    @Test
    public void testGetExplodePercent_defaultZero() throws Throwable {
        PiePlot plot = new PiePlot();
        assertEquals(0.0, plot.getExplodePercent("Unknown"), 1e-9);
    }

    // covers setExplodePercent null-key throw branch and valid-assignment branch
    @Test
    public void testSetExplodePercent_nullKeyThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setExplodePercent(null, 0.1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        plot.setExplodePercent("X", 0.3);
        assertEquals(0.3, plot.getExplodePercent("X"), 1e-9);
    }

    // covers getMaximumExplodePercent computing max across dataset keys
    @Test
    public void testGetMaximumExplodePercent_returnsMax() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("A", new Double(10.0));
        dataset.setValue("B", new Double(20.0));
        PiePlot plot = new PiePlot(dataset);
        plot.setExplodePercent("A", 0.2);
        plot.setExplodePercent("B", 0.5);
        assertEquals(0.5, plot.getMaximumExplodePercent(), 1e-9);
    }

    // covers setLabelGenerator null-permitted branch
    @Test
    public void testSetLabelGenerator_nullPermitted() throws Throwable {
        PiePlot plot = new PiePlot();
        plot.setLabelGenerator(null);
        assertNull(plot.getLabelGenerator());
    }

    // covers setLabelFont null-check throw and valid-assignment branches
    @Test
    public void testSetLabelFont_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setLabelFont(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        Font f = new Font("Serif", Font.BOLD, 12);
        plot.setLabelFont(f);
        assertEquals(f, plot.getLabelFont());
    }

    // covers setLabelPaint null-check throw and valid-assignment branches
    @Test
    public void testSetLabelPaint_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setLabelPaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        plot.setLabelPaint(Color.cyan);
        assertEquals(Color.cyan, plot.getLabelPaint());
    }

    // covers setLabelPadding null-check throw and valid-assignment branches
    @Test
    public void testSetLabelPadding_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setLabelPadding(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        RectangleInsets insets = new RectangleInsets(5, 5, 5, 5);
        plot.setLabelPadding(insets);
        assertEquals(insets, plot.getLabelPadding());
    }

    // covers setSimpleLabelOffset null-check throw and valid-assignment branches
    @Test
    public void testSetSimpleLabelOffset_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setSimpleLabelOffset(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        RectangleInsets insets = new RectangleInsets(1, 1, 1, 1);
        plot.setSimpleLabelOffset(insets);
        assertEquals(insets, plot.getSimpleLabelOffset());
    }

    // covers setLabelDistributor null-check throw and valid-assignment branches
    @Test
    public void testSetLabelDistributor_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setLabelDistributor(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        PieLabelDistributor distributor = new PieLabelDistributor(3);
        plot.setLabelDistributor(distributor);
        assertSame(distributor, plot.getLabelDistributor());
    }

    // covers setLegendItemShape null-check throw and valid-assignment branches
    @Test
    public void testSetLegendItemShape_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setLegendItemShape(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        Rectangle2D.Double shape = new Rectangle2D.Double(0, 0, 5, 5);
        plot.setLegendItemShape(shape);
        assertSame(shape, plot.getLegendItemShape());
    }

    // covers setLegendLabelGenerator null-check throw and valid-assignment branches
    @Test
    public void testSetLegendLabelGenerator_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setLegendLabelGenerator(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        StandardPieSectionLabelGenerator gen = new StandardPieSectionLabelGenerator();
        plot.setLegendLabelGenerator(gen);
        assertSame(gen, plot.getLegendLabelGenerator());
    }

    // covers setLabelLinkPaint null-check throw and valid-assignment branches
    @Test
    public void testSetLabelLinkPaint_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setLabelLinkPaint(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        plot.setLabelLinkPaint(Color.magenta);
        assertEquals(Color.magenta, plot.getLabelLinkPaint());
    }

    // covers setLabelLinkStroke null-check throw and valid-assignment branches
    @Test
    public void testSetLabelLinkStroke_nullThrowsAndValidUpdates() throws Throwable {
        PiePlot plot = new PiePlot();
        try {
            plot.setLabelLinkStroke(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        BasicStroke stroke = new BasicStroke(2.0f);
        plot.setLabelLinkStroke(stroke);
        assertEquals(stroke, plot.getLabelLinkStroke());
    }

    // covers getLegendItems with null dataset returning empty collection
    @Test
    public void testGetLegendItems_nullDataset_returnsEmptyCollection() throws Throwable {
        PiePlot plot = new PiePlot();
        LegendItemCollection items = plot.getLegendItems();
        assertEquals(0, items.getItemCount());
    }

    // covers getLegendItems: zero value excluded when ignoreZeroValues is true
    @Test
    public void testGetLegendItems_ignoreZeroValues_excludesZero() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("A", new Double(0.0));
        dataset.setValue("B", new Double(5.0));
        PiePlot plot = new PiePlot(dataset);
        plot.setIgnoreZeroValues(true);
        LegendItemCollection items = plot.getLegendItems();
        assertEquals(1, items.getItemCount());
    }

    // covers getLegendItems: negative value always excluded regardless of flags
    @Test
    public void testGetLegendItems_negativeValue_alwaysExcluded() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("A", new Double(-5.0));
        dataset.setValue("B", new Double(5.0));
        PiePlot plot = new PiePlot(dataset);
        LegendItemCollection items = plot.getLegendItems();
        assertEquals(1, items.getItemCount());
    }

    // covers getPlotType returning a non-empty localized string
    @Test
    public void testGetPlotType_returnsNonNullNonEmptyString() throws Throwable {
        PiePlot plot = new PiePlot();
        String type = plot.getPlotType();
        assertNotNull(type);
        assertTrue(type.length() > 0);
    }

    // covers getArcBounds: explodePercent == 0.0 returns the unexploded rectangle
    @Test
    public void testGetArcBounds_zeroExplodePercent_returnsUnexploded() throws Throwable {
        PiePlot plot = new PiePlot();
        Rectangle2D unexploded = new Rectangle2D.Double(0, 0, 100, 100);
        Rectangle2D exploded = new Rectangle2D.Double(-10, -10, 120, 120);
        Rectangle2D result = plot.getArcBounds(unexploded, exploded, 0.0, 90.0, 0.0);
        assertEquals(unexploded.getX(), result.getX(), 1e-9);
        assertEquals(unexploded.getY(), result.getY(), 1e-9);
        assertEquals(unexploded.getWidth(), result.getWidth(), 1e-9);
        assertEquals(unexploded.getHeight(), result.getHeight(), 1e-9);
    }

    // covers initialise() computing passes required, latest angle and total
    @Test
    public void testInitialise_returnsStateWithCorrectPassesAndAngleAndTotal() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("A", new Double(50.0));
        dataset.setValue("B", new Double(50.0));
        PiePlot plot = new PiePlot(dataset);
        plot.setStartAngle(123.0);
        Graphics2D g2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        PiePlotState state = plot.initialise(g2, new Rectangle2D.Double(0, 0, 10, 10), plot,
                null, null);
        assertEquals(2, state.getPassesRequired());
        assertEquals(123.0, state.getLatestAngle(), 1e-9);
        assertEquals(100.0, state.getTotal(), 1e-9);
    }

    // covers equals() for two freshly constructed default instances
    @Test
    public void testEquals_sameDefaultInstances_equal() throws Throwable {
        PiePlot p1 = new PiePlot();
        PiePlot p2 = new PiePlot();
        assertTrue(p1.equals(p2));
    }

    // covers equals() instanceof check branch with a non-PiePlot object
    @Test
    public void testEquals_differentType_notEqual() throws Throwable {
        PiePlot p1 = new PiePlot();
        assertFalse(p1.equals("not a plot"));
    }

    // hunts the bug: equals() must detect a different labelDistributor
    @Test
    public void testEquals_differentLabelDistributor_notEqual() throws Throwable {
        PiePlot p1 = new PiePlot();
        PiePlot p2 = new PiePlot();
        p2.setLabelDistributor(new PieLabelDistributor(7));
        assertFalse(p1.equals(p2));
    }

    // covers clone() producing an independent copy with preserved field values
    @Test
    public void testClone_independentCopy() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("A", new Double(10.0));
        PiePlot plot = new PiePlot(dataset);
        plot.setStartAngle(45.0);
        PiePlot clone = (PiePlot) plot.clone();
        assertNotSame(plot, clone);
        assertEquals(45.0, clone.getStartAngle(), 1e-9);
        clone.setStartAngle(99.0);
        assertEquals(45.0, plot.getStartAngle(), 1e-9);
    }
}
