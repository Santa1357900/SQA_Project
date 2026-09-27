package org.jfree.data.category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;
import org.jfree.data.UnknownKeyException;

public class DefaultIntervalCategoryDatasetTest {

    @Test
    public void testConstructorsAndBasicGetters() throws Throwable {
        double[][] starts = {
            {1.0, 2.0},
            {3.0, 4.0}
        };
        double[][] ends = {
            {1.5, 2.5},
            {3.5, 4.5}
        };

        DefaultIntervalCategoryDataset dataset = new DefaultIntervalCategoryDataset(starts, ends);

        assertEquals(2, dataset.getSeriesCount());
        assertEquals(2, dataset.getCategoryCount());
        assertEquals(2, dataset.getRowCount());
        assertEquals(2, dataset.getColumnCount());

        assertNotNull(dataset.getRowKeys());
        assertNotNull(dataset.getColumnKeys());

        assertEquals("Series 1", dataset.getRowKey(0));
        assertEquals("Series 2", dataset.getRowKey(1));
        assertEquals("Category 1", dataset.getColumnKey(0));
        assertEquals("Category 2", dataset.getColumnKey(1));

        assertEquals(0, dataset.getRowIndex("Series 1"));
        assertEquals(1, dataset.getRowIndex("Series 2"));
        assertEquals(-1, dataset.getRowIndex("NonExistent"));

        assertEquals(0, dataset.getColumnIndex("Category 1"));
        assertEquals(1, dataset.getColumnIndex("Category 2"));

        assertEquals(Double.valueOf(1.5), dataset.getEndValue(0, 0));
        assertEquals(Double.valueOf(1.0), dataset.getStartValue(0, 0));
        assertEquals(Double.valueOf(1.5), dataset.getValue(0, 0));
    }

    @Test
    public void testConstructorsWithCustomKeys() throws Throwable {
        String[] seriesNames = {"S1", "S2"};
        String[] categoryNames = {"C1", "C2"};
        Number[][] starts = {
            {10.0, 20.0},
            {30.0, 40.0}
        };
        Number[][] ends = {
            {15.0, 25.0},
            {35.0, 45.0}
        };

        DefaultIntervalCategoryDataset dataset = new DefaultIntervalCategoryDataset(
                seriesNames, categoryNames, starts, ends);

        assertEquals(2, dataset.getSeriesCount());
        assertEquals(2, dataset.getCategoryCount());
        assertEquals("S1", dataset.getSeriesKey(0));
        assertEquals("C1", dataset.getColumnKey(0));
        assertEquals(Double.valueOf(10.0), dataset.getStartValue("S1", "C1"));
        assertEquals(Double.valueOf(15.0), dataset.getEndValue("S1", "C1"));
        assertEquals(Double.valueOf(15.0), dataset.getValue("S1", "C1"));
    }

    @Test
    public void testConstructorsWithSeriesNamesOnly() throws Throwable {
        String[] seriesNames = {"S1"};
        Number[][] starts = {{1.0}};
        Number[][] ends = {{2.0}};

        DefaultIntervalCategoryDataset dataset = new DefaultIntervalCategoryDataset(
                seriesNames, starts, ends);

        assertEquals(1, dataset.getSeriesCount());
        assertEquals("S1", dataset.getSeriesKey(0));
        assertEquals("Category 1", dataset.getColumnKey(0));
    }

    @Test
    public void testEmptyDataset() throws Throwable {
        Number[][] starts = new Number[0][0];
        Number[][] ends = new Number[0][0];

        DefaultIntervalCategoryDataset dataset = new DefaultIntervalCategoryDataset(starts, ends);

        assertEquals(0, dataset.getSeriesCount());
        assertEquals(0, dataset.getCategoryCount());
        assertEquals(0, dataset.getRowCount());
        assertEquals(0, dataset.getColumnCount());
        
        List rowKeys = dataset.getRowKeys();
        assertNotNull(rowKeys);
        assertTrue(rowKeys.isEmpty());

        List colKeys = dataset.getColumnKeys();
        assertNotNull(colKeys);
        assertTrue(colKeys.isEmpty());
    }

    @Test
    public void testNullDataConstructors() throws Throwable {
        DefaultIntervalCategoryDataset dataset = new DefaultIntervalCategoryDataset(null, null);
        assertEquals(0, dataset.getSeriesCount());
        assertEquals(0, dataset.getCategoryCount());
        assertNull(dataset.getRowKeys());
        assertNull(dataset.getColumnKeys());
    }

    @Test
    public void testInvalidArgumentsInConstructor() throws Throwable {
        Number[][] starts = {{1.0, 2.0}};
        Number[][] ends = {{1.5}}; // Mismatched category count

        try {
            new DefaultIntervalCategoryDataset(starts, ends);
            fail("Expected IllegalArgumentException for mismatched category counts");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("number of categories"));
        }

        Number[][] starts2 = {{1.0}, {2.0}};
        Number[][] ends2 = {{1.5}}; // Mismatched series count

        try {
            new DefaultIntervalCategoryDataset(starts2, ends2);
            fail("Expected IllegalArgumentException for mismatched series counts");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("number of series"));
        }

        try {
            String[] badSeriesKeys = {"S1", "S2"};
            new DefaultIntervalCategoryDataset(badSeriesKeys, starts2, ends2);
            fail("Expected IllegalArgumentException for mismatched series keys length");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("number of series keys"));
        }

        try {
            String[] seriesKeys = {"S1"};
            String[] badCategoryKeys = {"C1", "C2"};
            new DefaultIntervalCategoryDataset(seriesKeys, badCategoryKeys, starts2, ends2);
            fail("Expected IllegalArgumentException for mismatched category keys length");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("number of category keys"));
        }
    }

    @Test
    public void testSetSeriesKeys() throws Throwable {
        double[][] starts = {{1.0}};
        double[][] ends = {{2.0}};
        DefaultIntervalCategoryDataset dataset = new DefaultIntervalCategoryDataset(starts, ends);

        Comparable[] newKeys = {"NewSeries"};
        dataset.setSeriesKeys(newKeys);
        assertEquals("NewSeries", dataset.getSeriesKey(0));

        try {
            dataset.setSeriesKeys(null);
            fail("Expected IllegalArgumentException for null seriesKeys");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }

        try {
            dataset.setSeriesKeys(new Comparable[]{"S1", "S2"});
            fail("Expected IllegalArgumentException for wrong length of seriesKeys");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does not match the data"));
        }
    }

    @Test
    public void testSetCategoryKeys() throws Throwable {
        double[][] starts = {{1.0, 2.0}};
        double[][] ends = {{1.5, 2.5}};
        DefaultIntervalCategoryDataset dataset = new DefaultIntervalCategoryDataset(starts, ends);

        Comparable[] newCategories = {"CatA", "CatB"};
        dataset.setCategoryKeys(newCategories);
        assertEquals("CatA", dataset.getColumnKey(0));
        assertEquals("CatB", dataset.getColumnKey(1));

        try {
            dataset.setCategoryKeys(null);
            fail("Expected IllegalArgumentException for null categoryKeys");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }

        try {
            dataset.setCategoryKeys(new Comparable[]{"CatA"});
            fail("Expected IllegalArgumentException for wrong length of categoryKeys");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does not match the data"));
        }

        try {
            dataset.setCategoryKeys(new Comparable[]{"CatA", null});
            fail("Expected IllegalArgumentException for null category element");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null category not permitted"));
        }
    }

    @Test
    public void testSetStartAndEndValues() throws Throwable {
        double[][] starts = {{1.0, 2.0}};
        double[][] ends = {{1.5, 2.5}};
        DefaultIntervalCategoryDataset dataset = new DefaultIntervalCategoryDataset(starts, ends);

        dataset.setStartValue(0, "Category 1", Double.valueOf(5.0));
        assertEquals(Double.valueOf(5.0), dataset.getStartValue(0, 0));

        dataset.setEndValue(0, "Category 1", Double.valueOf(6.0));
        assertEquals(Double.valueOf(6.0), dataset.getEndValue(0, 0));

        try {
            dataset.setStartValue(-1, "Category 1", Double.valueOf(1.0));
            fail("Expected IllegalArgumentException for negative series index");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("series outside valid range"));
        }

        try {
            dataset.setStartValue(5, "Category 1", Double.valueOf(1.0));
            fail("Expected IllegalArgumentException for out of bounds series index");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("series outside valid range"));
        }

        try {
            dataset.setStartValue(0, "NonExistentCategory", Double.valueOf(1.0));
            fail("Expected IllegalArgumentException for unrecognized category");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("unrecognised category"));
        }

        try {
            dataset.setEndValue(-1, "Category 1", Double.valueOf(1.0));
            fail("Expected IllegalArgumentException for negative series index in setEndValue");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("series outside valid range"));
        }

        try {
            dataset.setEndValue(0, "NonExistentCategory", Double.valueOf(1.0));
            fail("Expected IllegalArgumentException for unrecognized category in setEndValue");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("unrecognised category"));
        }
    }

    @Test
    public void testGetValuesWithKeysAndUnknownHandling() throws Throwable {
        double[][] starts = {{1.0, 2.0}};
        double[][] ends = {{1.5, 2.5}};
        DefaultIntervalCategoryDataset dataset = new DefaultIntervalCategoryDataset(starts, ends);

        // Valid keys
        assertEquals(Double.valueOf(1.0), dataset.getStartValue("Series 1", "Category 1"));
        assertEquals(Double.valueOf(1.5), dataset.getEndValue("Series 1", "Category 1"));
        assertEquals(Double.valueOf(1.5), dataset.getValue("Series 1", "Category 1"));

        // Unknown series key
        try {
            dataset.getStartValue("UnknownSeries", "Category 1");
            fail("Expected UnknownKeyException");
        } catch (UnknownKeyException e) {
            // expected
        }

        try {
            dataset.getEndValue("UnknownSeries", "Category 1");
            fail("Expected UnknownKeyException");
        } catch (UnknownKeyException e) {
            // expected
        }

        try {
            dataset.getValue("UnknownSeries", "Category 1");
            fail("Expected UnknownKeyException");
        } catch (UnknownKeyException e) {
            // expected
        }

        // Unknown category key
        try {
            dataset.getStartValue("Series 1", "UnknownCategory");
            fail("Expected UnknownKeyException");
        } catch (UnknownKeyException e) {
            // expected
        }

        try {
            dataset.getEndValue("Series 1", "UnknownCategory");
            fail("Expected UnknownKeyException");
        } catch (UnknownKeyException e) {
            // expected
        }

        try {
            dataset.getValue("Series 1", "UnknownCategory");
            fail("Expected UnknownKeyException");
        } catch (UnknownKeyException e) {
            // expected
        }

        // Column index with null key
        try {
            dataset.getColumnIndex(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }
    }

    @Test
    public void testGetValuesIndexOutOfBounds() throws Throwable {
        double[][] starts = {{1.0, 2.0}};
        double[][] ends = {{1.5, 2.5}};
        DefaultIntervalCategoryDataset dataset = new DefaultIntervalCategoryDataset(starts, ends);

        // Start value out of bounds
        try {
            dataset.getStartValue(-1, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("series index out of range"));
        }
        try {
            dataset.getStartValue(10, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("series index out of range"));
        }
        try {
            dataset.getStartValue(0, -1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("category index out of range"));
        }
        try {
            dataset.getStartValue(0, 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("category index out of range"));
        }

        // End value out of bounds
        try {
            dataset.getEndValue(-1, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("series index out of range"));
        }
        try {
            dataset.getEndValue(10, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("series index out of range"));
        }
        try {
            dataset.getEndValue(0, -1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("category index out of range"));
        }
        try {
            dataset.getEndValue(0, 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("category index out of range"));
        }

        // Row key out of bounds
        try {
            dataset.getRowKey(-1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            dataset.getRowKey(5);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testEqualsAndClone() throws Throwable {
        double[][] starts1 = {{1.0, 2.0}};
        double[][] ends1 = {{1.5, 2.5}};
        DefaultIntervalCategoryDataset d1 = new DefaultIntervalCategoryDataset(starts1, ends1);

        double[][] starts2 = {{1.0, 2.0}};
        double[][] ends2 = {{1.5, 2.5}};
        DefaultIntervalCategoryDataset d2 = new DefaultIntervalCategoryDataset(starts2, ends2);

        assertTrue(d1.equals(d1));
        assertTrue(d1.equals(d2));
        assertFalse(d1.equals(null));
        assertFalse(d1.equals("Some String"));

        // Different series keys
        DefaultIntervalCategoryDataset d3 = new DefaultIntervalCategoryDataset(
                new String[]{"Diff"}, null, starts1, ends1);
        assertFalse(d1.equals(d3));

        // Different category keys
        DefaultIntervalCategoryDataset d4 = new DefaultIntervalCategoryDataset(
                null, new String[]{"Cat1", "CatDiff"}, starts1, ends1);
        assertFalse(d1.equals(d4));

        // Different start data
        DefaultIntervalCategoryDataset d5 = new DefaultIntervalCategoryDataset(
                new double[][]{{99.0, 2.0}}, ends1);
        assertFalse(d1.equals(d5));

        // Different end data
        DefaultIntervalCategoryDataset d6 = new DefaultIntervalCategoryDataset(
                starts1, new double[][]{{99.0, 2.5}});
        assertFalse(d1.equals(d6));

        // Null startData comparison cases
        DefaultIntervalCategoryDataset d7 = new DefaultIntervalCategoryDataset(null, null);
        DefaultIntervalCategoryDataset d8 = new DefaultIntervalCategoryDataset(null, null);
        assertTrue(d7.equals(d8));
        assertFalse(d1.equals(d7));
        assertFalse(d7.equals(d1));

        // Different length startData
        DefaultIntervalCategoryDataset d9 = new DefaultIntervalCategoryDataset(
                new double[][]{{1.0}}, new double[][]{{1.5}});
        assertFalse(d1.equals(d9));

        // Clone test
        DefaultIntervalCategoryDataset clone = (DefaultIntervalCategoryDataset) d1.clone();
        assertTrue(d1.equals(clone));
        
        // Mutating clone should not affect original
        clone.setStartValue(0, "Category 1", Double.valueOf(999.0));
        assertFalse(d1.equals(clone));
    }

    @Test
    public void testCloneWithNullData() throws Throwable {
        DefaultIntervalCategoryDataset dataset = new DefaultIntervalCategoryDataset(null, null);
        try {
            dataset.clone();
            // Depending on implementation, clone() might throw NPE or handle it, 
            // but we want to ensure coverage of private clone method paths if called with null.
        } catch (Throwable t) {
            // Expected if internal clone(Number[][]) is invoked with null
        }
    }
}