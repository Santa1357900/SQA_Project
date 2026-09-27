package org.jfree.data.general;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.jfree.data.KeyToGroupMap;
import org.jfree.data.Range;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.function.Function2D;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.DefaultTableXYDataset;

public class DatasetUtilitiesTest {

    @Test(expected = IllegalArgumentException.class)
    public void testCalculatePieDatasetTotalNull() throws Throwable {
        DatasetUtilities.calculatePieDatasetTotal(null);
    }

    @Test
    public void testCalculatePieDatasetTotalValid() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("Key1", 10.0);
        dataset.setValue("Key2", -5.0); // Should be ignored
        dataset.setValue("Key3", null); // Should be ignored
        dataset.setValue(null, 20.0);   // Should be ignored

        double total = DatasetUtilities.calculatePieDatasetTotal(dataset);
        assertEquals(10.0, total, 0.0001);
    }

    @Test
    public void testCreatePieDatasetForRowAndColumn() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "Row1", "Col1");
        dataset.addValue(2.0, "Row1", "Col2");
        dataset.addValue(3.0, "Row2", "Col1");
        dataset.addValue(4.0, "Row2", "Col2");

        PieDataset pieRowKey = DatasetUtilities.createPieDatasetForRow(dataset, "Row1");
        assertNotNull(pieRowKey);
        assertEquals(2, pieRowKey.getItemCount());
        assertEquals(1.0, pieRowKey.getValue("Col1").doubleValue(), 0.0001);

        PieDataset pieRowIndex = DatasetUtilities.createPieDatasetForRow(dataset, 1);
        assertNotNull(pieRowIndex);
        assertEquals(3.0, pieRowIndex.getValue("Col1").doubleValue(), 0.0001);

        PieDataset pieColKey = DatasetUtilities.createPieDatasetForColumn(dataset, "Col2");
        assertNotNull(pieColKey);
        assertEquals(2.0, pieColKey.getValue("Row1").doubleValue(), 0.0001);

        PieDataset pieColIndex = DatasetUtilities.createPieDatasetForColumn(dataset, 0);
        assertNotNull(pieColIndex);
        assertEquals(3.0, pieColIndex.getValue("Row2").doubleValue(), 0.0001);
    }

    @Test
    public void testCreateConsolidatedPieDataset() throws Throwable {
        DefaultPieDataset source = new DefaultPieDataset();
        source.setValue("A", 50.0);
        source.setValue("B", 30.0);
        source.setValue("C", 10.0);
        source.setValue("D", 5.0);
        source.setValue("E", null);

        // threshold 0.15, minItems 2
        PieDataset consolidated = DatasetUtilities.createConsolidatedPieDataset(source, "Other", 0.15, 2);
        assertNotNull(consolidated);
        // Total = 95. C = 10 (10/95 < 0.15), D = 5 (5/95 < 0.15). Both should consolidate.
        assertEquals(50.0, consolidated.getValue("A").doubleValue(), 0.0001);
        assertEquals(30.0, consolidated.getValue("B").doubleValue(), 0.0001);
        assertEquals(15.0, consolidated.getValue("Other").doubleValue(), 0.0001);

        // Test overload with default minItems (2)
        PieDataset consolidatedDefault = DatasetUtilities.createConsolidatedPieDataset(source, "Other", 0.02);
        assertNotNull(consolidatedDefault);
    }

    @Test
    public void testCreateCategoryDatasetArraysAndData() throws Throwable {
        double[][] data = { {1.0, 2.0}, {3.0, 4.0} };
        CategoryDataset ds1 = DatasetUtilities.createCategoryDataset("R", "C", data);
        assertNotNull(ds1);
        assertEquals(2, ds1.getRowCount());
        assertEquals(2, ds1.getColumnCount());

        Number[][] numData = { {Double.valueOf(1.5), null}, {null, Double.valueOf(4.5)} };
        CategoryDataset ds2 = DatasetUtilities.createCategoryDataset("R", "C", numData);
        assertNotNull(ds2);

        Comparable[] rowKeys = { "Row1", "Row2" };
        Comparable[] colKeys = { "Col1", "Col2" };
        CategoryDataset ds3 = DatasetUtilities.createCategoryDataset(rowKeys, colKeys, data);
        assertNotNull(ds3);

        DefaultKeyedValues rowData = new DefaultKeyedValues();
        rowData.addValue("Key1", 10.0);
        rowData.addValue("Key2", 20.0);
        CategoryDataset ds4 = DatasetUtilities.createCategoryDataset("RowKey", rowData);
        assertNotNull(ds4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCategoryDatasetInvalidRowKeys() throws Throwable {
        Comparable[] rowKeys = null;
        Comparable[] colKeys = { "C1" };
        double[][] data = { {1.0} };
        DatasetUtilities.createCategoryDataset(rowKeys, colKeys, data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCategoryDatasetInvalidColKeys() throws Throwable {
        Comparable[] rowKeys = { "R1" };
        Comparable[] colKeys = null;
        double[][] data = { {1.0} };
        DatasetUtilities.createCategoryDataset(rowKeys, colKeys, data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCategoryDatasetDuplicateRowKeys() throws Throwable {
        Comparable[] rowKeys = { "R1", "R1" };
        Comparable[] colKeys = { "C1" };
        double[][] data = { {1.0}, {2.0} };
        DatasetUtilities.createCategoryDataset(rowKeys, colKeys, data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCategoryDatasetDuplicateColKeys() throws Throwable {
        Comparable[] rowKeys = { "R1" };
        Comparable[] colKeys = { "C1", "C1" };
        double[][] data = { {1.0, 2.0} };
        DatasetUtilities.createCategoryDataset(rowKeys, colKeys, data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCategoryDatasetRowLengthMismatch() throws Throwable {
        Comparable[] rowKeys = { "R1" };
        Comparable[] colKeys = { "C1" };
        double[][] data = { {1.0}, {2.0} };
        DatasetUtilities.createCategoryDataset(rowKeys, colKeys, data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCategoryDatasetColLengthMismatch() throws Throwable {
        Comparable[] rowKeys = { "R1" };
        Comparable[] colKeys = { "C1", "C2" };
        double[][] data = { {1.0} };
        DatasetUtilities.createCategoryDataset(rowKeys, colKeys, data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCategoryDatasetNullRowKey() throws Throwable {
        DatasetUtilities.createCategoryDataset(null, new DefaultKeyedValues());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCategoryDatasetNullRowData() throws Throwable {
        DatasetUtilities.createCategoryDataset("RowKey", null);
    }

    @Test
    public void testSampleFunction2D() throws Throwable {
        Function2D f = new Function2D() {
            public double getValue(double x) {
                return x * x;
            }
        };

        XYDataset dataset = DatasetUtilities.sampleFunction2D(f, 0.0, 10.0, 5, "Series1");
        assertNotNull(dataset);
        assertEquals(1, dataset.getSeriesCount());
        assertEquals(5, dataset.getItemCount(0));

        XYSeries series = DatasetUtilities.sampleFunction2DToSeries(f, 0.0, 5.0, 3, "Series2");
        assertNotNull(series);
        assertEquals(3, series.getItemCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSampleFunction2DNullFunction() throws Throwable {
        DatasetUtilities.sampleFunction2D(null, 0.0, 1.0, 10, "S");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSampleFunction2DNullKey() throws Throwable {
        Function2D f = new Function2D() {
            public double getValue(double x) { return x; }
        };
        DatasetUtilities.sampleFunction2D(f, 0.0, 1.0, 10, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSampleFunction2DInvalidRange() throws Throwable {
        Function2D f = new Function2D() {
            public double getValue(double x) { return x; }
        };
        DatasetUtilities.sampleFunction2D(f, 5.0, 1.0, 10, "S");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSampleFunction2DInvalidSamples() throws Throwable {
        Function2D f = new Function2D() {
            public double getValue(double x) { return x; }
        };
        DatasetUtilities.sampleFunction2D(f, 1.0, 5.0, 1, "S");
    }

    @Test
    public void testIsEmptyOrNull() throws Throwable {
        assertTrue(DatasetUtilities.isEmptyOrNull((PieDataset) null));
        DefaultPieDataset pds = new DefaultPieDataset();
        assertTrue(DatasetUtilities.isEmptyOrNull(pds));
        pds.setValue("A", 0.0);
        assertTrue(DatasetUtilities.isEmptyOrNull(pds));
        pds.setValue("A", 10.0);
        assertFalse(DatasetUtilities.isEmptyOrNull(pds));

        assertTrue(DatasetUtilities.isEmptyOrNull((CategoryDataset) null));
        DefaultCategoryDataset cds = new DefaultCategoryDataset();
        assertTrue(DatasetUtilities.isEmptyOrNull(cds));
        cds.addValue(null, "R1", "C1");
        assertTrue(DatasetUtilities.isEmptyOrNull(cds));
        cds.addValue(5.0, "R1", "C1");
        assertFalse(DatasetUtilities.isEmptyOrNull(cds));

        assertTrue(DatasetUtilities.isEmptyOrNull((XYDataset) null));
        XYSeriesCollection xyc = new XYSeriesCollection();
        assertTrue(DatasetUtilities.isEmptyOrNull(xyc));
        XYSeries series = new XYSeries("S1");
        xyc.addSeries(series);
        assertTrue(DatasetUtilities.isEmptyOrNull(xyc));
        series.add(1.0, 1.0);
        assertFalse(DatasetUtilities.isEmptyOrNull(xyc));
    }

    @Test
    public void testFindDomainBoundsAndRangeBoundsAndExtremes() throws Throwable {
        XYSeries series = new XYSeries("Series");
        series.add(1.0, 2.0);
        series.add(3.0, 4.0);
        XYSeriesCollection xysc = new XYSeriesCollection(series);

        Range domainRange = DatasetUtilities.findDomainBounds(xysc);
        assertNotNull(domainRange);
        assertEquals(1.0, domainRange.getLowerBound(), 0.0001);
        assertEquals(3.0, domainRange.getUpperBound(), 0.0001);

        List visibleKeys = new ArrayList();
        visibleKeys.add("Series");
        Range domainRangeVisible = DatasetUtilities.findDomainBounds(xysc, visibleKeys, true);
        assertNotNull(domainRangeVisible);

        Range rangeBounds = DatasetUtilities.findRangeBounds(xysc);
        assertNotNull(rangeBounds);
        assertEquals(2.0, rangeBounds.getLowerBound(), 0.0001);
        assertEquals(4.0, rangeBounds.getUpperBound(), 0.0001);

        Range rangeBoundsVisible = DatasetUtilities.findRangeBounds(xysc, visibleKeys, domainRange, true);
        assertNotNull(rangeBoundsVisible);

        Number minDom = DatasetUtilities.findMinimumDomainValue(xysc);
        assertEquals(1.0, minDom.doubleValue(), 0.0001);

        Number maxDom = DatasetUtilities.findMaximumDomainValue(xysc);
        assertEquals(3.0, maxDom.doubleValue(), 0.0001);

        Number minRange = DatasetUtilities.findMinimumRangeValue(xysc);
        assertEquals(2.0, minRange.doubleValue(), 0.0001);

        Number maxRange = DatasetUtilities.findMaximumRangeValue(xysc);
        assertEquals(4.0, maxRange.doubleValue(), 0.0001);

        DefaultCategoryDataset catDs = new DefaultCategoryDataset();
        catDs.addValue(-10.0, "R1", "C1");
        catDs.addValue(25.0, "R1", "C2");

        Range catRange = DatasetUtilities.findRangeBounds(catDs);
        assertNotNull(catRange);
        assertEquals(-10.0, catRange.getLowerBound(), 0.0001);
        assertEquals(25.0, catRange.getUpperBound(), 0.0001);

        Range catRangeVisible = DatasetUtilities.findRangeBounds(catDs, visibleKeys, true);
        assertNull(catRangeVisible); // row index not found for "Series" in category dataset, returns null or handles gracefully. Let's fix visibleKeys for category dataset:
        List catVisibleKeys = new ArrayList();
        catVisibleKeys.add("R1");
        Range catRangeVisibleFixed = DatasetUtilities.findRangeBounds(catDs, catVisibleKeys, true);
        assertNotNull(catRangeVisibleFixed);

        Number catMinRange = DatasetUtilities.findMinimumRangeValue(catDs);
        assertEquals(-10.0, catMinRange.doubleValue(), 0.0001);

        Number catMaxRange = DatasetUtilities.findMaximumRangeValue(catDs);
        assertEquals(25.0, catMaxRange.doubleValue(), 0.0001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindDomainBoundsNullDataset() throws Throwable {
        DatasetUtilities.findDomainBounds((XYDataset) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindDomainBoundsListNullDataset() throws Throwable {
        DatasetUtilities.findDomainBounds(null, new ArrayList(), true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindRangeBoundsCategoryNullDataset() throws Throwable {
        DatasetUtilities.findRangeBounds((CategoryDataset) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindRangeBoundsCategoryListNullDataset() throws Throwable {
        DatasetUtilities.findRangeBounds(null, new ArrayList(), true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindRangeBoundsXYListNullDataset() throws Throwable {
        DatasetUtilities.findRangeBounds(null, new ArrayList(), new Range(0, 1), true);
    }

    @Test
    public void testIterateDomainBoundsAndRangeBounds() throws Throwable {
        XYSeries series = new XYSeries("S");
        series.add(Double.NaN, Double.NaN);
        series.add(2.0, 5.0);
        XYSeriesCollection collection = new XYSeriesCollection(series);

        Range dr = DatasetUtilities.iterateDomainBounds(collection, false);
        assertNotNull(dr);

        Range rr = DatasetUtilities.iterateRangeBounds(collection, false);
        assertNotNull(rr);

        DefaultCategoryDataset cds = new DefaultCategoryDataset();
        cds.addValue(Double.NaN, "R1", "C1");
        cds.addValue(3.0, "R1", "C2");
        Range cr = DatasetUtilities.iterateRangeBounds(cds, false);
        assertNotNull(cr);

        Range iterToFindDomain = DatasetUtilities.iterateToFindDomainBounds(collection, new ArrayList<Comparable>(), false);
        assertNull(iterToFindDomain); // empty visible keys should yield positive infinity -> null

        List<Comparable> keys = new ArrayList<Comparable>();
        keys.add("S");
        Range iterToFindDomainValid = DatasetUtilities.iterateToFindDomainBounds(collection, keys, false);
        assertNotNull(iterToFindDomainValid);

        Range iterToFindRangeXY = DatasetUtilities.iterateToFindRangeBounds(collection, keys, new Range(0, 10), false);
        assertNotNull(iterToFindRangeXY);
    }

    @Test
    public void testStackedRangesAndCumulative() throws Throwable {
        DefaultCategoryDataset cds = new DefaultCategoryDataset();
        cds.addValue(10.0, "R1", "C1");
        cds.addValue(-5.0, "R2", "C1");
        cds.addValue(20.0, "R1", "C2");

        Range stackedRange = DatasetUtilities.findStackedRangeBounds(cds);
        assertNotNull(stackedRange);

        KeyToGroupMap map = new KeyToGroupMap("G1");
        map.mapKeyToGroup("R1", "G1");
        map.mapKeyToGroup("R2", "G1");
        Range stackedMapRange = DatasetUtilities.findStackedRangeBounds(cds, map);
        assertNotNull(stackedMapRange);

        Number minStack = DatasetUtilities.findMinimumStackedRangeValue(cds);
        assertNotNull(minStack);

        Number maxStack = DatasetUtilities.findMaximumStackedRangeValue(cds);
        assertNotNull(maxStack);

        Range cumRange = DatasetUtilities.findCumulativeRangeBounds(cds);
        assertNotNull(cumRange);

        DefaultTableXYDataset tableXY = new DefaultTableXYDataset();
        XYSeries s1 = new XYSeries("S1", true, false);
        s1.add(1.0, 5.0);
        tableXY.addSeries(s1);

        Range tableStacked = DatasetUtilities.findStackedRangeBounds(tableXY);
        assertNotNull(tableStacked);

        double stackTotal = DatasetUtilities.calculateStackTotal(tableXY, 0);
        assertEquals(5.0, stackTotal, 0.0001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindStackedRangeBoundsTableNull() throws Throwable {
        DatasetUtilities.findStackedRangeBounds((TableXYDataset) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindCumulativeRangeBoundsNull() throws Throwable {
        DatasetUtilities.findCumulativeRangeBounds(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindMinimumDomainValueNull() throws Throwable {
        DatasetUtilities.findMinimumDomainValue(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindMaximumDomainValueNull() throws Throwable {
        DatasetUtilities.findMaximumDomainValue(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindMinimumRangeValueCategoryNull() throws Throwable {
        DatasetUtilities.findMinimumRangeValue((CategoryDataset) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindMinimumRangeValueXYNull() throws Throwable {
        DatasetUtilities.findMinimumRangeValue((XYDataset) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindMaximumRangeValueCategoryNull() throws Throwable {
        DatasetUtilities.findMaximumRangeValue((CategoryDataset) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindMaximumRangeValueXYNull() throws Throwable {
        DatasetUtilities.findMaximumRangeValue((XYDataset) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindStackedRangeBoundsCategoryNull() throws Throwable {
        DatasetUtilities.findStackedRangeBounds((CategoryDataset) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindStackedRangeBoundsCategoryMapNull() throws Throwable {
        DatasetUtilities.findStackedRangeBounds((CategoryDataset) null, (KeyToGroupMap) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindMinimumStackedRangeValueNull() throws Throwable {
        DatasetUtilities.findMinimumStackedRangeValue(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindMaximumStackedRangeValueNull() throws Throwable {
        DatasetUtilities.findMaximumStackedRangeValue(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIterateToFindRangeBoundsCategoryNullKeys() throws Throwable {
        DefaultCategoryDataset cds = new DefaultCategoryDataset();
        DatasetUtilities.iterateToFindRangeBounds(cds, null, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIterateToFindRangeBoundsCategoryNullDataset() throws Throwable {
        DatasetUtilities.iterateToFindRangeBounds(null, new ArrayList(), true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIterateToFindDomainBoundsXYNullKeys() throws Throwable {
        XYSeries series = new XYSeries("S");
        XYSeriesCollection col = new XYSeriesCollection(series);
        DatasetUtilities.iterateToFindDomainBounds(col, null, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIterateToFindDomainBoundsXYNullDataset() throws Throwable {
        DatasetUtilities.iterateToFindDomainBounds(null, new ArrayList(), true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIterateToFindRangeBoundsXYNullRange() throws Throwable {
        XYSeries series = new XYSeries("S");
        XYSeriesCollection col = new XYSeriesCollection(series);
        DatasetUtilities.iterateToFindRangeBounds(col, new ArrayList(), null, true);
    }
}