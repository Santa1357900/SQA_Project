package org.jfree.data.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.jfree.data.Range;

public class DefaultBoxAndWhiskerCategoryDatasetTest {

    @Test
    public void testConstructorAndInitialState() throws Throwable {
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
        assertEquals(0, dataset.getRowCount());
        assertEquals(0, dataset.getColumnCount());
        assertTrue(Double.isNaN(dataset.getRangeLowerBound(false)));
        assertTrue(Double.isNaN(dataset.getRangeUpperBound(false)));
        assertNotNull(dataset.getRangeBounds(false));
    }

    @Test
    public void testAddListAndGetters() throws Throwable {
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
        List<Double> values = new ArrayList<Double>();
        values.add(1.0);
        values.add(2.0);
        values.add(3.0);
        values.add(4.0);
        values.add(5.0);

        dataset.add(values, "Row1", "Col1");

        assertEquals(1, dataset.getRowCount());
        assertEquals(1, dataset.getColumnCount());
        assertEquals("Row1", dataset.getRowKey(0));
        assertEquals("Col1", dataset.getColumnKey(0));
        assertEquals(0, dataset.getRowIndex("Row1"));
        assertEquals(0, dataset.getColumnIndex("Col1"));

        assertNotNull(dataset.getItem(0, 0));
        assertNotNull(dataset.getValue(0, 0));
        assertNotNull(dataset.getValue("Row1", "Col1"));
        assertNotNull(dataset.getMeanValue(0, 0));
        assertNotNull(dataset.getMeanValue("Row1", "Col1"));
        assertNotNull(dataset.getMedianValue(0, 0));
        assertNotNull(dataset.getMedianValue("Row1", "Col1"));
        assertNotNull(dataset.getQ1Value(0, 0));
        assertNotNull(dataset.getQ1Value("Row1", "Col1"));
        assertNotNull(dataset.getQ3Value(0, 0));
        assertNotNull(dataset.getQ3Value("Row1", "Col1"));
        assertNotNull(dataset.getMinRegularValue(0, 0));
        assertNotNull(dataset.getMinRegularValue("Row1", "Col1"));
        assertNotNull(dataset.getMaxRegularValue(0, 0));
        assertNotNull(dataset.getMaxRegularValue("Row1", "Col1"));
        assertNotNull(dataset.getMinOutlier(0, 0));
        assertNotNull(dataset.getMinOutlier("Row1", "Col1"));
        assertNotNull(dataset.getMaxOutlier(0, 0));
        assertNotNull(dataset.getMaxOutlier("Row1", "Col1"));
        assertNotNull(dataset.getOutliers(0, 0));
        assertNotNull(dataset.getOutliers("Row1", "Col1"));

        List rowKeys = dataset.getRowKeys();
        assertEquals(1, rowKeys.size());
        List colKeys = dataset.getColumnKeys();
        assertEquals(1, colKeys.size());
    }

    @Test
    public void testAddBoxAndWhiskerItem() throws Throwable {
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
        List<Double> outliers = new ArrayList<Double>();
        BoxAndWhiskerItem item = new BoxAndWhiskerItem(
            Double.valueOf(2.0), Double.valueOf(2.0),
            Double.valueOf(1.0), Double.valueOf(3.0),
            Double.valueOf(0.5), Double.valueOf(3.5),
            Double.valueOf(0.0), Double.valueOf(4.0),
            outliers
        );

        dataset.add(item, "R1", "C1");
        assertEquals(Double.valueOf(0.0), dataset.getMinOutlier(0, 0));
        assertEquals(Double.valueOf(4.0), dataset.getMaxOutlier(0, 0));
        assertEquals(0.0, dataset.getRangeLowerBound(false), 0.001);
        assertEquals(4.0, dataset.getRangeUpperBound(false), 0.001);
    }

    @Test
    public void testBoundsUpdateWithMultipleItems() throws Throwable {
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
        
        List<Double> v1 = new ArrayList<Double>();
        v1.add(10.0);
        v1.add(20.0);
        dataset.add(v1, "R1", "C1");

        List<Double> v2 = new ArrayList<Double>();
        v2.add(5.0);
        v2.add(25.0);
        dataset.add(v2, "R1", "C2");

        assertEquals(5.0, dataset.getRangeLowerBound(false), 0.001);
        assertEquals(25.0, dataset.getRangeUpperBound(false), 0.001);

        // Update bounds branch coverage: replacing max/min where bounds match row/col
        List<Double> v3 = new ArrayList<Double>();
        v3.add(15.0);
        dataset.add(v3, "R1", "C2"); // Overwrite C2 which might have been max/min
    }

    @Test
    public void testInvalidIndicesReturnNull() throws Throwable {
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
        assertNull(dataset.getItem(99, 99));
        assertNull(dataset.getMeanValue(99, 99));
        assertNull(dataset.getMeanValue("NonExistent", "NonExistent"));
        assertNull(dataset.getMedianValue(99, 99));
        assertNull(dataset.getMedianValue("NonExistent", "NonExistent"));
        assertNull(dataset.getQ1Value(99, 99));
        assertNull(dataset.getQ1Value("NonExistent", "NonExistent"));
        assertNull(dataset.getQ3Value(99, 99));
        assertNull(dataset.getQ3Value("NonExistent", "NonExistent"));
        assertNull(dataset.getMinRegularValue(99, 99));
        assertNull(dataset.getMinRegularValue("NonExistent", "NonExistent"));
        assertNull(dataset.getMaxRegularValue(99, 99));
        assertNull(dataset.getMaxRegularValue("NonExistent", "NonExistent"));
        assertNull(dataset.getMinOutlier(99, 99));
        assertNull(dataset.getMinOutlier("NonExistent", "NonExistent"));
        assertNull(dataset.getMaxOutlier(99, 99));
        assertNull(dataset.getMaxOutlier("NonExistent", "NonExistent"));
        assertNull(dataset.getOutliers(99, 99));
        assertNull(dataset.getOutliers("NonExistent", "NonExistent"));
    }

    @Test
    public void testEqualsAndClone() throws Throwable {
        DefaultBoxAndWhiskerCategoryDataset d1 = new DefaultBoxAndWhiskerCategoryDataset();
        List<Double> values = new ArrayList<Double>();
        values.add(1.0);
        values.add(2.0);
        d1.add(values, "R1", "C1");

        assertTrue(d1.equals(d1));
        assertFalse(d1.equals(null));
        assertFalse(d1.equals("Some String"));

        DefaultBoxAndWhiskerCategoryDataset d2 = (DefaultBoxAndWhiskerCategoryDataset) d1.clone();
        assertTrue(d1.equals(d2));

        DefaultBoxAndWhiskerCategoryDataset d3 = new DefaultBoxAndWhiskerCategoryDataset();
        d3.add(values, "R1", "DifferentCol");
        assertFalse(d1.equals(d3));
    }
}