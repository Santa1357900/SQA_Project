package org.jfree.data.general;

import static org.junit.Assert.*;
import org.junit.Test;

import org.jfree.data.Range;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.function.Function2D;
import org.jfree.data.pie.DefaultPieDataset;
import org.jfree.data.pie.PieDataset;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;

public class DatasetUtilitiesClaudeTest {

    // calculatePieDatasetTotal: null dataset throws IllegalArgumentException
    @Test
    public void testCalculatePieDatasetTotal_nullDataset_throwsException() throws Throwable {
        try {
            DatasetUtilities.calculatePieDatasetTotal(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // calculatePieDatasetTotal: negative values ignored, positive summed
    @Test
    public void testCalculatePieDatasetTotal_mixedValues_ignoresNegativeAndSumsPositive() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("A", 10.0);
        dataset.setValue("B", -5.0);
        dataset.setValue("C", 3.0);
        double total = DatasetUtilities.calculatePieDatasetTotal(dataset);
        assertEquals(13.0, total, 1e-9);
    }

    // createPieDatasetForRow(Comparable): returns values for the given row
    @Test
    public void testCreatePieDatasetForRow_byKey_returnsRowValues() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "R1", "C1");
        dataset.addValue(2.0, "R1", "C2");
        dataset.addValue(3.0, "R2", "C1");
        PieDataset pie = DatasetUtilities.createPieDatasetForRow(dataset, "R1");
        assertEquals(1.0, pie.getValue("C1").doubleValue(), 1e-9);
        assertEquals(2.0, pie.getValue("C2").doubleValue(), 1e-9);
    }

    // createPieDatasetForColumn(Comparable): returns values for the given column
    @Test
    public void testCreatePieDatasetForColumn_byKey_returnsColumnValues() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "R1", "C1");
        dataset.addValue(3.0, "R2", "C1");
        PieDataset pie = DatasetUtilities.createPieDatasetForColumn(dataset, "C1");
        assertEquals(1.0, pie.getValue("R1").doubleValue(), 1e-9);
        assertEquals(3.0, pie.getValue("R2").doubleValue(), 1e-9);
    }

    // createConsolidatedPieDataset: items below threshold aggregated into "Other"
    @Test
    public void testCreateConsolidatedPieDataset_belowThreshold_aggregatesIntoOther() throws Throwable {
        DefaultPieDataset source = new DefaultPieDataset();
        source.setValue("A", 0.5);
        source.setValue("B", 0.5);
        source.setValue("C", 9.0);
        PieDataset result = DatasetUtilities.createConsolidatedPieDataset(source, "Other", 0.10);
        assertEquals(2, result.getItemCount());
        assertEquals(1.0, result.getValue("Other").doubleValue(), 1e-9);
        assertEquals(9.0, result.getValue("C").doubleValue(), 1e-9);
    }

    // createConsolidatedPieDataset: aggregation skipped when otherKeys < minItems
    @Test
    public void testCreateConsolidatedPieDataset_minItemsNotMet_noAggregation() throws Throwable {
        DefaultPieDataset source = new DefaultPieDataset();
        source.setValue("A", 0.5);
        source.setValue("B", 0.5);
        source.setValue("C", 9.0);
        PieDataset result = DatasetUtilities.createConsolidatedPieDataset(source, "Other", 0.10, 3);
        assertEquals(3, result.getItemCount());
        assertEquals(0.5, result.getValue("A").doubleValue(), 1e-9);
    }

    // createCategoryDataset(String,String,double[][]): keys and values built with prefixes
    @Test
    public void testCreateCategoryDataset_doubleArrayWithPrefixes_createsExpectedKeysAndValues() throws Throwable {
        double[][] data = {{1.0, 2.0}, {3.0, 4.0}};
        CategoryDataset dataset = DatasetUtilities.createCategoryDataset("Row", "Col", data);
        assertEquals("Row1", dataset.getRowKey(0));
        assertEquals("Col2", dataset.getColumnKey(1));
        assertEquals(4.0, dataset.getValue(1, 1).doubleValue(), 1e-9);
    }

    // createCategoryDataset(Comparable[],Comparable[],double[][]): null rowKeys throws
    @Test
    public void testCreateCategoryDataset_nullRowKeys_throwsException() throws Throwable {
        Comparable[] columnKeys = {"C1"};
        double[][] data = {{1.0}};
        try {
            DatasetUtilities.createCategoryDataset(null, columnKeys, data);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // createCategoryDataset(Comparable[],Comparable[],double[][]): null columnKeys throws
    @Test
    public void testCreateCategoryDataset_nullColumnKeys_throwsException() throws Throwable {
        Comparable[] rowKeys = {"R1"};
        double[][] data = {{1.0}};
        try {
            DatasetUtilities.createCategoryDataset(rowKeys, null, data);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // createCategoryDataset: duplicate row keys throws
    @Test
    public void testCreateCategoryDataset_duplicateRowKeys_throwsException() throws Throwable {
        Comparable[] rowKeys = {"R1", "R1"};
        Comparable[] columnKeys = {"C1"};
        double[][] data = {{1.0}, {2.0}};
        try {
            DatasetUtilities.createCategoryDataset(rowKeys, columnKeys, data);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("rowKeys"));
        }
    }

    // createCategoryDataset: rowKeys length mismatch throws
    @Test
    public void testCreateCategoryDataset_rowKeysLengthMismatch_throwsException() throws Throwable {
        Comparable[] rowKeys = {"R1"};
        Comparable[] columnKeys = {"C1"};
        double[][] data = {{1.0}, {2.0}};
        try {
            DatasetUtilities.createCategoryDataset(rowKeys, columnKeys, data);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("row"));
        }
    }

    // createCategoryDataset: columnKeys length mismatch throws
    @Test
    public void testCreateCategoryDataset_columnKeysLengthMismatch_throwsException() throws Throwable {
        Comparable[] rowKeys = {"R1"};
        Comparable[] columnKeys = {"C1", "C2"};
        double[][] data = {{1.0}};
        try {
            DatasetUtilities.createCategoryDataset(rowKeys, columnKeys, data);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("column"));
        }
    }

    // sampleFunction2DToSeries: null function throws
    @Test
    public void testSampleFunction2DToSeries_nullFunction_throwsException() throws Throwable {
        try {
            DatasetUtilities.sampleFunction2DToSeries(null, 0.0, 10.0, 5, "S");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // sampleFunction2DToSeries: null seriesKey throws
    @Test
    public void testSampleFunction2DToSeries_nullSeriesKey_throwsException() throws Throwable {
        Function2D f = new Function2D() {
            public double getValue(double x) {
                return x;
            }
        };
        try {
            DatasetUtilities.sampleFunction2DToSeries(f, 0.0, 10.0, 5, null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // sampleFunction2DToSeries: start >= end throws
    @Test
    public void testSampleFunction2DToSeries_startNotLessThanEnd_throwsException() throws Throwable {
        Function2D f = new Function2D() {
            public double getValue(double x) {
                return x;
            }
        };
        try {
            DatasetUtilities.sampleFunction2DToSeries(f, 5.0, 5.0, 5, "S");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // sampleFunction2DToSeries: samples < 2 throws
    @Test
    public void testSampleFunction2DToSeries_samplesLessThanTwo_throwsException() throws Throwable {
        Function2D f = new Function2D() {
            public double getValue(double x) {
                return x;
            }
        };
        try {
            DatasetUtilities.sampleFunction2DToSeries(f, 0.0, 10.0, 1, "S");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // sampleFunction2DToSeries: valid range produces samples at correct x/y
    @Test
    public void testSampleFunction2DToSeries_validRange_producesExpectedSamples() throws Throwable {
        Function2D f = new Function2D() {
            public double getValue(double x) {
                return x * 2.0;
            }
        };
        XYSeries series = DatasetUtilities.sampleFunction2DToSeries(f, 0.0, 10.0, 6, "S");
        assertEquals(6, series.getItemCount());
        assertEquals(0.0, series.getX(0).doubleValue(), 1e-9);
        assertEquals(10.0, series.getX(5).doubleValue(), 1e-9);
        assertEquals(20.0, series.getY(5).doubleValue(), 1e-9);
    }

    // sampleFunction2D: wraps sampled series into an XYDataset with one series
    @Test
    public void testSampleFunction2D_validRange_wrapsSeriesInXYDataset() throws Throwable {
        Function2D f = new Function2D() {
            public double getValue(double x) {
                return x;
            }
        };
        XYDataset dataset = DatasetUtilities.sampleFunction2D(f, 0.0, 4.0, 5, "S");
        assertEquals(1, dataset.getSeriesCount());
        assertEquals(5, dataset.getItemCount(0));
    }

    // isEmptyOrNull(PieDataset): null dataset returns true
    @Test
    public void testIsEmptyOrNull_pieDataset_nullReturnsTrue() throws Throwable {
        assertTrue(DatasetUtilities.isEmptyOrNull((PieDataset) null));
    }

    // isEmptyOrNull(PieDataset): zero items returns true
    @Test
    public void testIsEmptyOrNull_pieDataset_emptyReturnsTrue() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        assertTrue(DatasetUtilities.isEmptyOrNull(dataset));
    }

    // isEmptyOrNull(PieDataset): a positive value makes it non-empty
    @Test
    public void testIsEmptyOrNull_pieDataset_positiveValueReturnsFalse() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("A", 1.0);
        assertFalse(DatasetUtilities.isEmptyOrNull(dataset));
    }

    // isEmptyOrNull(PieDataset): only non-positive values still counts as empty
    @Test
    public void testIsEmptyOrNull_pieDataset_onlyNonPositiveValuesReturnsTrue() throws Throwable {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("A", -1.0);
        dataset.setValue("B", 0.0);
        assertTrue(DatasetUtilities.isEmptyOrNull(dataset));
    }

    // isEmptyOrNull(CategoryDataset): null dataset returns true
    @Test
    public void testIsEmptyOrNull_categoryDataset_nullReturnsTrue() throws Throwable {
        assertTrue(DatasetUtilities.isEmptyOrNull((CategoryDataset) null));
    }

    // isEmptyOrNull(CategoryDataset): zero rows returns true
    @Test
    public void testIsEmptyOrNull_categoryDataset_emptyReturnsTrue() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        assertTrue(DatasetUtilities.isEmptyOrNull(dataset));
    }

    // isEmptyOrNull(CategoryDataset): a non-null value makes it non-empty
    @Test
    public void testIsEmptyOrNull_categoryDataset_withValueReturnsFalse() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "R1", "C1");
        assertFalse(DatasetUtilities.isEmptyOrNull(dataset));
    }

    // isEmptyOrNull(CategoryDataset): rows/columns present but all values null returns true
    @Test
    public void testIsEmptyOrNull_categoryDataset_allNullValuesReturnsTrue() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue((Number) null, "R1", "C1");
        assertTrue(DatasetUtilities.isEmptyOrNull(dataset));
    }

    // isEmptyOrNull(XYDataset): null dataset returns true
    @Test
    public void testIsEmptyOrNull_xyDataset_nullReturnsTrue() throws Throwable {
        assertTrue(DatasetUtilities.isEmptyOrNull((XYDataset) null));
    }

    // isEmptyOrNull(XYDataset): series with items returns false
    @Test
    public void testIsEmptyOrNull_xyDataset_withItemsReturnsFalse() throws Throwable {
        DefaultXYDataset dataset = new DefaultXYDataset();
        double[][] data = {{1.0, 2.0}, {3.0, 4.0}};
        dataset.addSeries("S1", data);
        assertFalse(DatasetUtilities.isEmptyOrNull(dataset));
    }

    // findDomainBounds(XYDataset): null dataset throws
    @Test
    public void testFindDomainBounds_nullDataset_throwsException() throws Throwable {
        try {
            DatasetUtilities.findDomainBounds((XYDataset) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // findDomainBounds(XYDataset): returns min/max of x-values
    @Test
    public void testFindDomainBounds_xyDataset_returnsMinMax() throws Throwable {
        DefaultXYDataset dataset = new DefaultXYDataset();
        double[][] data = {{1.0, 2.0, 3.0}, {10.0, 20.0, 30.0}};
        dataset.addSeries("S1", data);
        Range range = DatasetUtilities.findDomainBounds(dataset);
        assertEquals(1.0, range.getLowerBound(), 1e-9);
        assertEquals(3.0, range.getUpperBound(), 1e-9);
    }

    // findRangeBounds(XYDataset): null dataset throws
    @Test
    public void testFindRangeBounds_xyDataset_nullThrowsException() throws Throwable {
        try {
            DatasetUtilities.findRangeBounds((XYDataset) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // findRangeBounds(XYDataset): returns min/max of y-values
    @Test
    public void testFindRangeBounds_xyDataset_returnsMinMax() throws Throwable {
        DefaultXYDataset dataset = new DefaultXYDataset();
        double[][] data = {{1.0, 2.0, 3.0}, {10.0, 20.0, 30.0}};
        dataset.addSeries("S1", data);
        Range range = DatasetUtilities.findRangeBounds(dataset);
        assertEquals(10.0, range.getLowerBound(), 1e-9);
        assertEquals(30.0, range.getUpperBound(), 1e-9);
    }

    // findRangeBounds(CategoryDataset): null dataset throws
    @Test
    public void testFindRangeBounds_categoryDataset_nullThrowsException() throws Throwable {
        try {
            DatasetUtilities.findRangeBounds((CategoryDataset) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // findRangeBounds(CategoryDataset): returns min/max across all cells
    @Test
    public void testFindRangeBounds_categoryDataset_returnsMinMax() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "R1", "C1");
        dataset.addValue(5.0, "R1", "C2");
        dataset.addValue(-2.0, "R2", "C1");
        Range range = DatasetUtilities.findRangeBounds(dataset);
        assertEquals(-2.0, range.getLowerBound(), 1e-9);
        assertEquals(5.0, range.getUpperBound(), 1e-9);
    }

    // iterateRangeBounds(CategoryDataset,boolean): empty dataset returns null
    @Test
    public void testIterateRangeBounds_categoryDataset_emptyReturnsNull() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        Range range = DatasetUtilities.iterateRangeBounds(dataset, true);
        assertNull(range);
    }

    // findMinimumDomainValue / findMaximumDomainValue(XYDataset)
    @Test
    public void testFindMinimumAndMaximumDomainValue_xyDataset_returnsExpected() throws Throwable {
        DefaultXYDataset dataset = new DefaultXYDataset();
        double[][] data = {{1.0, 2.0, 3.0}, {10.0, 20.0, 30.0}};
        dataset.addSeries("S1", data);
        Number min = DatasetUtilities.findMinimumDomainValue(dataset);
        Number max = DatasetUtilities.findMaximumDomainValue(dataset);
        assertEquals(1.0, min.doubleValue(), 1e-9);
        assertEquals(3.0, max.doubleValue(), 1e-9);
    }

    // findMinimumRangeValue / findMaximumRangeValue(CategoryDataset)
    @Test
    public void testFindMinimumAndMaximumRangeValue_categoryDataset_returnsExpected() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1.0, "R1", "C1");
        dataset.addValue(5.0, "R1", "C2");
        dataset.addValue(-2.0, "R2", "C1");
        Number min = DatasetUtilities.findMinimumRangeValue(dataset);
        Number max = DatasetUtilities.findMaximumRangeValue(dataset);
        assertEquals(-2.0, min.doubleValue(), 1e-9);
        assertEquals(5.0, max.doubleValue(), 1e-9);
    }

    // findMinimumRangeValue / findMaximumRangeValue(XYDataset)
    @Test
    public void testFindMinimumAndMaximumRangeValue_xyDataset_returnsExpected() throws Throwable {
        DefaultXYDataset dataset = new DefaultXYDataset();
        double[][] data = {{1.0, 2.0, 3.0}, {10.0, 20.0, 30.0}};
        dataset.addSeries("S1", data);
        Number min = DatasetUtilities.findMinimumRangeValue(dataset);
        Number max = DatasetUtilities.findMaximumRangeValue(dataset);
        assertEquals(10.0, min.doubleValue(), 1e-9);
        assertEquals(30.0, max.doubleValue(), 1e-9);
    }

    // findStackedRangeBounds(CategoryDataset): default base of 0.0 stacks positives/negatives separately
    @Test
    public void testFindStackedRangeBounds_categoryDataset_defaultBase() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(3.0, "R1", "C1");
        dataset.addValue(-2.0, "R2", "C1");
        Range range = DatasetUtilities.findStackedRangeBounds(dataset);
        assertEquals(-2.0, range.getLowerBound(), 1e-9);
        assertEquals(3.0, range.getUpperBound(), 1e-9);
    }

    // findStackedRangeBounds(CategoryDataset,double): base value is added to stacks
    @Test
    public void testFindStackedRangeBounds_categoryDataset_withBase() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(3.0, "R1", "C1");
        dataset.addValue(-2.0, "R2", "C1");
        Range range = DatasetUtilities.findStackedRangeBounds(dataset, 1.0);
        assertEquals(-1.0, range.getLowerBound(), 1e-9);
        assertEquals(4.0, range.getUpperBound(), 1e-9);
    }

    // findMinimumStackedRangeValue / findMaximumStackedRangeValue(CategoryDataset)
    @Test
    public void testFindMinimumAndMaximumStackedRangeValue_categoryDataset_returnsExpected() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(3.0, "R1", "C1");
        dataset.addValue(-2.0, "R2", "C1");
        dataset.addValue(1.0, "R1", "C2");
        dataset.addValue(-4.0, "R2", "C2");
        Number min = DatasetUtilities.findMinimumStackedRangeValue(dataset);
        Number max = DatasetUtilities.findMaximumStackedRangeValue(dataset);
        assertEquals(-4.0, min.doubleValue(), 1e-9);
        assertEquals(3.0, max.doubleValue(), 1e-9);
    }

    // findCumulativeRangeBounds(CategoryDataset): tracks running total min/max per row
    @Test
    public void testFindCumulativeRangeBounds_categoryDataset_runningTotals() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(3.0, "R1", "C1");
        dataset.addValue(-5.0, "R1", "C2");
        dataset.addValue(4.0, "R1", "C3");
        Range range = DatasetUtilities.findCumulativeRangeBounds(dataset);
        assertEquals(-2.0, range.getLowerBound(), 1e-9);
        assertEquals(3.0, range.getUpperBound(), 1e-9);
    }

    // findCumulativeRangeBounds(CategoryDataset): empty dataset returns null
    @Test
    public void testFindCumulativeRangeBounds_emptyDataset_returnsNull() throws Throwable {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        Range range = DatasetUtilities.findCumulativeRangeBounds(dataset);
        assertNull(range);
    }

    // findCumulativeRangeBounds(CategoryDataset): null dataset throws
    @Test
    public void testFindCumulativeRangeBounds_nullDataset_throwsException() throws Throwable {
        try {
            DatasetUtilities.findCumulativeRangeBounds(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
