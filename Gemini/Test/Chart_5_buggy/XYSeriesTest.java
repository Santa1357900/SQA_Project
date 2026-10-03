package org.jfree.data.xy;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jfree.data.general.SeriesException;

public class XYSeriesTest {

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        XYSeries series1 = new XYSeries("Series1");
        assertEquals("Series1", series1.getKey());
        assertTrue(series1.getAutoSort());
        assertTrue(series1.getAllowDuplicateXValues());
        assertEquals(Integer.MAX_VALUE, series1.getMaximumItemCount());
        assertEquals(0, series1.getItemCount());

        XYSeries series2 = new XYSeries("Series2", false);
        assertEquals("Series2", series2.getKey());
        assertFalse(series2.getAutoSort());
        assertTrue(series2.getAllowDuplicateXValues());

        XYSeries series3 = new XYSeries("Series3", false, false);
        assertEquals("Series3", series3.getKey());
        assertFalse(series3.getAutoSort());
        assertFalse(series3.getAllowDuplicateXValues());
    }

    @Test
    public void testAddPrimitivesAndItems() throws Throwable {
        XYSeries series = new XYSeries("Series");
        series.add(1.0, 2.0);
        series.add(3.0, (Number) null);
        series.add(new Double(2.0), new Double(4.0), false);
        series.add(new XYDataItem(0.0, 1.0));
        series.add(new XYDataItem(0.5, 1.5), true);

        assertEquals(5, series.getItemCount());
        // autoSort is true by default, so order should be 0.0, 0.5, 1.0, 2.0, 3.0
        assertEquals(0.0, series.getX(0).doubleValue(), 0.0001);
        assertEquals(0.5, series.getX(1).doubleValue(), 0.0001);
        assertEquals(1.0, series.getX(2).doubleValue(), 0.0001);
        assertEquals(2.0, series.getX(3).doubleValue(), 0.0001);
        assertEquals(3.0, series.getX(4).doubleValue(), 0.0001);
        assertNull(series.getY(4));
    }

    @Test
    public void testAddNullItemThrowsException() throws Throwable {
        XYSeries series = new XYSeries("Series");
        try {
            series.add((XYDataItem) null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("Null 'item' argument") >= 0);
        }
    }

    @Test
    public void testDuplicateXValuesHandling() throws Throwable {
        // Allow duplicates = true, autoSort = true
        XYSeries series1 = new XYSeries("S1", true, true);
        series1.add(1.0, 10.0);
        series1.add(1.0, 20.0);
        assertEquals(2, series1.getItemCount());

        // Allow duplicates = false, autoSort = true
        XYSeries series2 = new XYSeries("S2", true, false);
        series2.add(1.0, 10.0);
        try {
            series2.add(1.0, 20.0);
            fail("Should have thrown SeriesException");
        } catch (SeriesException e) {
            assertTrue(e.getMessage().indexOf("X-value already exists") >= 0);
        }

        // Allow duplicates = false, autoSort = false
        XYSeries series3 = new XYSeries("S3", false, false);
        series3.add(1.0, 10.0);
        try {
            series3.add(1.0, 20.0);
            fail("Should have thrown SeriesException");
        } catch (SeriesException e) {
            assertTrue(e.getMessage().indexOf("X-value already exists") >= 0);
        }
        
        // Allow duplicates = true, autoSort = false
        XYSeries series4 = new XYSeries("S4", false, true);
        series4.add(1.0, 10.0);
        series4.add(1.0, 20.0);
        assertEquals(2, series4.getItemCount());
    }

    @Test
    public void testMaximumItemCount() throws Throwable {
        XYSeries series = new XYSeries("Series");
        series.setMaximumItemCount(2);
        assertEquals(2, series.getMaximumItemCount());

        series.add(1.0, 1.0);
        series.add(2.0, 2.0);
        series.add(3.0, 3.0); // Should remove the first item (1.0)

        assertEquals(2, series.getItemCount());
        assertEquals(2.0, series.getX(0).doubleValue(), 0.0001);
        assertEquals(3.0, series.getX(1).doubleValue(), 0.0001);
    }

    @Test
    public void testRemoveAndDelete() throws Throwable {
        XYSeries series = new XYSeries("Series", false);
        series.add(1.0, 1.0);
        series.add(2.0, 2.0);
        series.add(3.0, 3.0);
        series.add(4.0, 4.0);

        XYDataItem removed = series.remove(1);
        assertEquals(2.0, removed.getX().doubleValue(), 0.0001);
        assertEquals(3, series.getItemCount());

        XYDataItem removedByX = series.remove(new Double(4.0));
        assertEquals(4.0, removedByX.getX().doubleValue(), 0.0001);
        assertEquals(2, series.getItemCount());

        series.delete(0, 0);
        assertEquals(1, series.getItemCount());
        assertEquals(3.0, series.getX(0).doubleValue(), 0.0001);
    }

    @Test
    public void testClear() throws Throwable {
        XYSeries series = new XYSeries("Series");
        series.add(1.0, 1.0);
        series.add(2.0, 2.0);
        assertEquals(2, series.getItemCount());

        series.clear();
        assertEquals(0, series.getItemCount());

        // Clearing an empty series should be safe
        series.clear();
        assertEquals(0, series.getItemCount());
    }

    @Test
    public void testUpdateAndIndexOf() throws Throwable {
        XYSeries series = new XYSeries("Series", false);
        series.add(1.0, 10.0);
        series.add(2.0, 20.0);

        assertEquals(0, series.indexOf(new Double(1.0)));
        assertEquals(1, series.indexOf(new Double(2.0)));
        assertTrue(series.indexOf(new Double(99.0)) < 0);

        series.updateByIndex(0, new Double(15.0));
        assertEquals(15.0, series.getY(0).doubleValue(), 0.0001);

        series.update(new Double(2.0), new Double(25.0));
        assertEquals(25.0, series.getY(1).doubleValue(), 0.0001);

        try {
            series.update(new Double(99.0), new Double(0.0));
            fail("Should have thrown SeriesException");
        } catch (SeriesException e) {
            assertTrue(e.getMessage().indexOf("No observation for x") >= 0);
        }
    }

    @Test
    public void testAddOrUpdate() throws Throwable {
        XYSeries series = new XYSeries("Series", true, false);
        series.add(1.0, 10.0);
        series.add(2.0, 20.0);

        // Update existing
        XYDataItem overwritten = series.addOrUpdate(1.0, 99.0);
        assertNotNull(overwritten);
        assertEquals(10.0, overwritten.getY().doubleValue(), 0.0001);
        assertEquals(99.0, series.getY(0).doubleValue(), 0.0001);

        // Add new
        XYDataItem overwrittenNew = series.addOrUpdate(3.0, 30.0);
        assertNull(overwrittenNew);
        assertEquals(3, series.getItemCount());

        try {
            series.addOrUpdate(null, 1.0);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().indexOf("Null 'x' argument") >= 0);
        }
    }

    @Test
    public void testToArray() throws Throwable {
        XYSeries series = new XYSeries("Series");
        series.add(1.0, 10.0);
        series.add(2.0, null);

        double[][] arr = series.toArray();
        assertEquals(2, arr.length);
        assertEquals(2, arr[0].length);
        assertEquals(1.0, arr[0][0], 0.0001);
        assertEquals(2.0, arr[0][1], 0.0001);
        assertEquals(10.0, arr[1][0], 0.0001);
        assertTrue(Double.isNaN(arr[1][1]));
    }

    @Test
    public void testCloneAndCreateCopy() throws Throwable {
        XYSeries series = new XYSeries("Series");
        series.add(1.0, 10.0);
        series.add(2.0, 20.0);

        XYSeries clone = (XYSeries) series.clone();
        assertEquals(series, clone);
        assertNotSame(series, clone);

        XYSeries copy = series.createCopy(0, 1);
        assertEquals(2, copy.getItemCount());
        assertEquals(1.0, copy.getX(0).doubleValue(), 0.0001);
        assertEquals(20.0, copy.getY(1).doubleValue(), 0.0001);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        XYSeries s1 = new XYSeries("S1");
        XYSeries s2 = new XYSeries("S1");
        XYSeries s3 = new XYSeries("S3");

        assertTrue(s1.equals(s1));
        assertFalse(s1.equals(null));
        assertFalse(s1.equals("NotAXYSeries"));
        assertTrue(s1.equals(s2));
        assertFalse(s1.equals(s3));

        s1.add(1.0, 1.0);
        assertFalse(s1.equals(s2));
        s2.add(1.0, 1.0);
        assertTrue(s1.equals(s2));

        // Test hashCode with different item counts
        XYSeries s4 = new XYSeries("S1");
        s4.add(1.0, 1.0);
        s4.add(2.0, 2.0);
        s4.add(3.0, 3.0);
        s4.add(4.0, 4.0);
        assertTrue(s4.hashCode() != 0);

        XYSeries s5 = new XYSeries("S1", false, false);
        assertFalse(s1.equals(s5));

        XYSeries s6 = new XYSeries("S1");
        s6.setMaximumItemCount(5);
        assertFalse(s1.equals(s6));
    }

    @Test
    public void testGetItems() throws Throwable {
        XYSeries series = new XYSeries("Series");
        series.add(1.0, 1.0);
        List items = series.getItems();
        assertNotNull(items);
        assertEquals(1, items.size());
        
        try {
            items.clear();
            fail("Should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }
}