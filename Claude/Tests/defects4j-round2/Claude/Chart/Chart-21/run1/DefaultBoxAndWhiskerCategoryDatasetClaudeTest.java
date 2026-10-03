package org.jfree.data.statistics;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.jfree.data.Range;

public class DefaultBoxAndWhiskerCategoryDatasetClaudeTest {

    private DefaultBoxAndWhiskerCategoryDataset dataset;

    @Before
    public void setUp() throws Throwable {
        dataset = new DefaultBoxAndWhiskerCategoryDataset();
    }

    private List listA() {
        List list = new ArrayList();
        list.add(1.0); list.add(2.0); list.add(3.0); list.add(4.0);
        list.add(5.0); list.add(6.0); list.add(7.0); list.add(8.0);
        list.add(9.0); list.add(10.0);
        return list;
    }

    private List listB() {
        List list = new ArrayList();
        list.add(4.0); list.add(5.0); list.add(5.0); list.add(5.0); list.add(6.0);
        return list;
    }

    private List listC() {
        List list = new ArrayList();
        list.add(2.0); list.add(3.0); list.add(3.0);
        return list;
    }

    private List listD() {
        List list = new ArrayList();
        list.add(-5.0); list.add(-4.0); list.add(-3.0); list.add(-2.0); list.add(-1.0);
        return list;
    }

    private List listE() {
        List list = new ArrayList();
        list.add(-2.0); list.add(-1.0); list.add(0.0);
        return list;
    }

    // constructor: empty dataset has zero rows and columns
    @Test
    public void testConstructor_initialState_rowAndColumnCountZero() throws Throwable {
        assertEquals(0, dataset.getRowCount());
        assertEquals(0, dataset.getColumnCount());
    }

    // constructor: empty dataset has NaN range bounds
    @Test
    public void testConstructor_initialState_rangeBoundsNaN() throws Throwable {
        assertTrue(Double.isNaN(dataset.getRangeLowerBound(true)));
        assertTrue(Double.isNaN(dataset.getRangeUpperBound(true)));
    }

    // add(List,...): first add creates one row and one column
    @Test
    public void testAdd_singleList_updatesRowAndColumnCount() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        assertEquals(1, dataset.getRowCount());
        assertEquals(1, dataset.getColumnCount());
    }

    // add(List,...): mean and median computed correctly for known data
    @Test
    public void testAdd_singleList_computesMeanAndMedian() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        assertEquals(5.5, dataset.getMeanValue(0, 0).doubleValue(), 1e-9);
        assertEquals(5.5, dataset.getMedianValue(0, 0).doubleValue(), 1e-9);
    }

    // add(List,...): Q1 <= median <= Q3 property holds
    @Test
    public void testAdd_singleList_quartileOrdering() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        double q1 = dataset.getQ1Value(0, 0).doubleValue();
        double q3 = dataset.getQ3Value(0, 0).doubleValue();
        double median = dataset.getMedianValue(0, 0).doubleValue();
        assertTrue(q1 <= median);
        assertTrue(median <= q3);
    }

    // add(List,...): min/max outlier equal list min/max for tight (non-outlier) data
    @Test
    public void testAdd_singleList_minMaxOutlierEqualsListMinMax() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        assertEquals(1.0, dataset.getMinOutlier(0, 0).doubleValue(), 1e-9);
        assertEquals(10.0, dataset.getMaxOutlier(0, 0).doubleValue(), 1e-9);
    }

    // add(List,...): adding to a new row increases row count but not column count
    @Test
    public void testAdd_secondList_differentRow_rowCountIncreases() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        dataset.add(listB(), "R1", "C0");
        assertEquals(2, dataset.getRowCount());
        assertEquals(1, dataset.getColumnCount());
    }

    // bug: replacing cell holding cached max causes wrong upper bound (updateBounds does not rescan dataset)
    @Test
    public void testAdd_replaceCellHoldingMax_upperBoundBug() throws Throwable {
        DefaultBoxAndWhiskerCategoryDataset ds = new DefaultBoxAndWhiskerCategoryDataset();
        ds.add(listA(), "R0", "C0");
        ds.add(listB(), "R1", "C0");
        ds.add(listC(), "R0", "C0");
        assertEquals(6.0, ds.getRangeUpperBound(true), 1e-9);
    }

    // bug: replacing cell holding cached min also corrupts the unrelated cached max value
    @Test
    public void testAdd_replaceCellHoldingMin_upperBoundNotAffectedBug() throws Throwable {
        DefaultBoxAndWhiskerCategoryDataset ds = new DefaultBoxAndWhiskerCategoryDataset();
        ds.add(listA(), "R0", "C0");
        ds.add(listD(), "R1", "C0");
        ds.add(listE(), "R1", "C0");
        assertEquals(10.0, ds.getRangeUpperBound(true), 1e-9);
    }

    // add(BoxAndWhiskerItem,...): item obtained from one dataset can be added to another
    @Test
    public void testAdd_boxAndWhiskerItemOverload_addsItemToNewDataset() throws Throwable {
        DefaultBoxAndWhiskerCategoryDataset src = new DefaultBoxAndWhiskerCategoryDataset();
        src.add(listC(), "R0", "C0");
        BoxAndWhiskerItem item = src.getItem(0, 0);
        DefaultBoxAndWhiskerCategoryDataset dest = new DefaultBoxAndWhiskerCategoryDataset();
        dest.add(item, "RowX", "ColX");
        assertSame(item, dest.getItem(0, 0));
        assertEquals(1, dest.getRowCount());
    }

    // getItem: returns non-null BoxAndWhiskerItem for a valid cell
    @Test
    public void testGetItem_validIndices_returnsCorrectItem() throws Throwable {
        dataset.add(listB(), "R0", "C0");
        BoxAndWhiskerItem item = dataset.getItem(0, 0);
        assertNotNull(item);
    }

    // getValue(int,int) delegates to getMedianValue(int,int)
    @Test
    public void testGetValue_byIndices_matchesMedian() throws Throwable {
        dataset.add(listB(), "R0", "C0");
        assertEquals(dataset.getMedianValue(0, 0).doubleValue(),
                dataset.getValue(0, 0).doubleValue(), 1e-9);
    }

    // getValue(Comparable,Comparable) delegates to getMedianValue(Comparable,Comparable)
    @Test
    public void testGetValue_byKeys_matchesMedian() throws Throwable {
        dataset.add(listB(), "R0", "C0");
        assertEquals(dataset.getMedianValue("R0", "C0").doubleValue(),
                dataset.getValue("R0", "C0").doubleValue(), 1e-9);
    }

    // getMeanValue: index and key overloads return the same value
    @Test
    public void testGetMeanValue_indexAndKeyOverloadsConsistent() throws Throwable {
        dataset.add(listC(), "R0", "C0");
        assertEquals(8.0 / 3.0, dataset.getMeanValue(0, 0).doubleValue(), 1e-9);
        assertEquals(dataset.getMeanValue(0, 0).doubleValue(),
                dataset.getMeanValue("R0", "C0").doubleValue(), 1e-9);
    }

    // getMedianValue: index and key overloads return the same value
    @Test
    public void testGetMedianValue_indexAndKeyOverloadsConsistent() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        assertEquals(dataset.getMedianValue(0, 0).doubleValue(),
                dataset.getMedianValue("R0", "C0").doubleValue(), 1e-9);
    }

    // getQ1Value: index and key overloads consistent
    @Test
    public void testGetQ1Value_indexAndKeyOverloadsConsistent() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        assertEquals(dataset.getQ1Value(0, 0).doubleValue(),
                dataset.getQ1Value("R0", "C0").doubleValue(), 1e-9);
    }

    // getQ3Value: index and key overloads consistent
    @Test
    public void testGetQ3Value_indexAndKeyOverloadsConsistent() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        assertEquals(dataset.getQ3Value(0, 0).doubleValue(),
                dataset.getQ3Value("R0", "C0").doubleValue(), 1e-9);
    }

    // getColumnIndex / getColumnKey round trip
    @Test
    public void testGetColumnIndex_and_getColumnKey_roundTrip() throws Throwable {
        dataset.add(listA(), "R0", "Col1");
        assertEquals(0, dataset.getColumnIndex("Col1"));
        assertEquals("Col1", dataset.getColumnKey(0));
    }

    // getColumnKeys contains the added column key
    @Test
    public void testGetColumnKeys_containsAddedColumn() throws Throwable {
        dataset.add(listA(), "R0", "Col1");
        assertTrue(dataset.getColumnKeys().contains("Col1"));
    }

    // getRowIndex / getRowKey round trip
    @Test
    public void testGetRowIndex_and_getRowKey_roundTrip() throws Throwable {
        dataset.add(listA(), "Row1", "C0");
        assertEquals(0, dataset.getRowIndex("Row1"));
        assertEquals("Row1", dataset.getRowKey(0));
    }

    // getRowKeys contains the added row key
    @Test
    public void testGetRowKeys_containsAddedRow() throws Throwable {
        dataset.add(listA(), "Row1", "C0");
        assertTrue(dataset.getRowKeys().contains("Row1"));
    }

    // getRowCount / getColumnCount after multiple distinct rows and columns
    @Test
    public void testGetRowCount_and_getColumnCount_afterMultipleAdds() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        dataset.add(listB(), "R1", "C0");
        dataset.add(listC(), "R0", "C1");
        assertEquals(2, dataset.getRowCount());
        assertEquals(2, dataset.getColumnCount());
    }

    // getRangeLowerBound: correct minimum with no replacement (no bug triggered)
    @Test
    public void testGetRangeLowerBound_afterAdds_correctMinimum() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        assertEquals(1.0, dataset.getRangeLowerBound(true), 1e-9);
    }

    // getRangeUpperBound: correct maximum with no replacement (no bug triggered)
    @Test
    public void testGetRangeUpperBound_afterAdds_correctMaximum() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        assertEquals(10.0, dataset.getRangeUpperBound(true), 1e-9);
    }

    // getRangeBounds: returns a Range instance
    @Test
    public void testGetRangeBounds_returnsRangeInstance() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        Object bounds = dataset.getRangeBounds(true);
        assertTrue(bounds instanceof Range);
    }

    // getMinRegularValue <= getMaxRegularValue property, index and key consistent
    @Test
    public void testGetMinAndMaxRegularValue_orderingAndConsistency() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        double minReg = dataset.getMinRegularValue(0, 0).doubleValue();
        double maxReg = dataset.getMaxRegularValue(0, 0).doubleValue();
        assertTrue(minReg <= maxReg);
        assertEquals(minReg, dataset.getMinRegularValue("R0", "C0").doubleValue(), 1e-9);
        assertEquals(maxReg, dataset.getMaxRegularValue("R0", "C0").doubleValue(), 1e-9);
    }

    // getMinOutlier: index overload returns list minimum for non-outlier data
    @Test
    public void testGetMinOutlier_byIndices_equalsListMin() throws Throwable {
        dataset.add(listB(), "R0", "C0");
        assertEquals(4.0, dataset.getMinOutlier(0, 0).doubleValue(), 1e-9);
    }

    // getMinOutlier: key overload consistent with index overload
    @Test
    public void testGetMinOutlier_byKeys_consistentWithIndex() throws Throwable {
        dataset.add(listB(), "R0", "C0");
        assertEquals(dataset.getMinOutlier(0, 0).doubleValue(),
                dataset.getMinOutlier("R0", "C0").doubleValue(), 1e-9);
    }

    // getMaxOutlier: index overload returns list maximum for non-outlier data
    @Test
    public void testGetMaxOutlier_byIndices_equalsListMax() throws Throwable {
        dataset.add(listB(), "R0", "C0");
        assertEquals(6.0, dataset.getMaxOutlier(0, 0).doubleValue(), 1e-9);
    }

    // getMaxOutlier: key overload consistent with index overload
    @Test
    public void testGetMaxOutlier_byKeys_consistentWithIndex() throws Throwable {
        dataset.add(listB(), "R0", "C0");
        assertEquals(dataset.getMaxOutlier(0, 0).doubleValue(),
                dataset.getMaxOutlier("R0", "C0").doubleValue(), 1e-9);
    }

    // getOutliers: index overload returns empty list for tight (non-outlier) data
    @Test
    public void testGetOutliers_byIndices_emptyForTightData() throws Throwable {
        dataset.add(listB(), "R0", "C0");
        List outliers = dataset.getOutliers(0, 0);
        assertNotNull(outliers);
        assertTrue(outliers.isEmpty());
    }

    // getOutliers: key overload consistent with index overload
    @Test
    public void testGetOutliers_byKeys_consistentWithIndex() throws Throwable {
        dataset.add(listB(), "R0", "C0");
        List byIndex = dataset.getOutliers(0, 0);
        List byKey = dataset.getOutliers("R0", "C0");
        assertEquals(byIndex.size(), byKey.size());
    }

    // equals: same instance is equal to itself
    @Test
    public void testEquals_sameInstance_true() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        assertTrue(dataset.equals(dataset));
    }

    // equals: two datasets with identical content are equal
    @Test
    public void testEquals_sameContent_true() throws Throwable {
        DefaultBoxAndWhiskerCategoryDataset d1 = new DefaultBoxAndWhiskerCategoryDataset();
        DefaultBoxAndWhiskerCategoryDataset d2 = new DefaultBoxAndWhiskerCategoryDataset();
        d1.add(listA(), "R0", "C0");
        d2.add(listA(), "R0", "C0");
        assertTrue(d1.equals(d2));
    }

    // equals: two datasets with different content are not equal
    @Test
    public void testEquals_differentContent_false() throws Throwable {
        DefaultBoxAndWhiskerCategoryDataset d1 = new DefaultBoxAndWhiskerCategoryDataset();
        DefaultBoxAndWhiskerCategoryDataset d2 = new DefaultBoxAndWhiskerCategoryDataset();
        d1.add(listA(), "R0", "C0");
        d2.add(listB(), "R0", "C0");
        assertFalse(d1.equals(d2));
    }

    // equals: comparing against null returns false
    @Test
    public void testEquals_null_false() throws Throwable {
        assertFalse(dataset.equals(null));
    }

    // equals: comparing against an object of a different type returns false
    @Test
    public void testEquals_differentType_false() throws Throwable {
        assertFalse(dataset.equals("not a dataset"));
    }

    // clone: produces an equal but independent copy
    @Test
    public void testClone_equalsOriginalAfterClone() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        Object cloneObj = dataset.clone();
        assertTrue(cloneObj instanceof DefaultBoxAndWhiskerCategoryDataset);
        DefaultBoxAndWhiskerCategoryDataset clone = (DefaultBoxAndWhiskerCategoryDataset) cloneObj;
        assertTrue(dataset.equals(clone));
    }

    // clone: modifying the clone does not affect the original dataset's row count
    @Test
    public void testClone_independentCopy_modifyingCloneDoesNotAffectOriginal() throws Throwable {
        dataset.add(listA(), "R0", "C0");
        Object cloneObj = dataset.clone();
        DefaultBoxAndWhiskerCategoryDataset clone = (DefaultBoxAndWhiskerCategoryDataset) cloneObj;
        clone.add(listB(), "R1", "C0");
        assertEquals(1, dataset.getRowCount());
        assertEquals(2, clone.getRowCount());
    }
}
