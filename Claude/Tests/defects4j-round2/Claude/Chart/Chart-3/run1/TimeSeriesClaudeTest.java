package org.jfree.data.time;

import java.util.Collection;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jfree.data.general.SeriesException;

public class TimeSeriesClaudeTest {

    // Constructor: domain/range default to "Time"/"Value"
    @Test
    public void testConstructor_defaultDomainRange() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        assertEquals("Time", series.getDomainDescription());
        assertEquals("Value", series.getRangeDescription());
    }

    // Constructor: custom domain/range are stored as given
    @Test
    public void testConstructor_customDomainRange() throws Throwable {
        TimeSeries series = new TimeSeries("S", "D", "R");
        assertEquals("D", series.getDomainDescription());
        assertEquals("R", series.getRangeDescription());
    }

    // setDomainDescription updates the field
    @Test
    public void testSetDomainDescription_updatesValue() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.setDomainDescription("NewDomain");
        assertEquals("NewDomain", series.getDomainDescription());
    }

    // setRangeDescription updates the field
    @Test
    public void testSetRangeDescription_updatesValue() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.setRangeDescription("NewRange");
        assertEquals("NewRange", series.getRangeDescription());
    }

    // getItemCount on empty series returns 0
    @Test
    public void testGetItemCount_emptySeries() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        assertEquals(0, series.getItemCount());
    }

    // getItems returns an unmodifiable list
    @Test
    public void testGetItems_unmodifiableList() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.add(new Day(1, 1, 2001), 1.0);
        List items = series.getItems();
        try {
            items.add(new TimeSeriesDataItem(new Day(2, 1, 2001), 2.0));
            fail("Expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected) {
        }
    }

    // getMaximumItemCount default value is Integer.MAX_VALUE
    @Test
    public void testGetMaximumItemCount_defaultValue() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        assertEquals(Integer.MAX_VALUE, series.getMaximumItemCount());
    }

    // setMaximumItemCount negative argument throws IllegalArgumentException
    @Test
    public void testSetMaximumItemCount_negative_throwsException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        try {
            series.setMaximumItemCount(-1);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // setMaximumItemCount removes oldest items when exceeded
    @Test
    public void testSetMaximumItemCount_exceedsLimit_removesOldest() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        Day d3 = new Day(3, 1, 2001);
        series.add(d1, 1.0);
        series.add(d2, 2.0);
        series.add(d3, 3.0);
        series.setMaximumItemCount(2);
        assertEquals(2, series.getItemCount());
        assertEquals(d2, series.getTimePeriod(0));
    }

    // getMaximumItemAge default value is Long.MAX_VALUE
    @Test
    public void testGetMaximumItemAge_defaultValue() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        assertEquals(Long.MAX_VALUE, series.getMaximumItemAge());
    }

    // setMaximumItemAge negative argument throws IllegalArgumentException
    @Test
    public void testSetMaximumItemAge_negative_throwsException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        try {
            series.setMaximumItemAge(-1);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // setMaximumItemAge removes items older than the limit
    @Test
    public void testSetMaximumItemAge_removesOldItems() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        Day d3 = new Day(3, 1, 2001);
        series.add(d1, 1.0);
        series.add(d2, 2.0);
        series.add(d3, 3.0);
        series.setMaximumItemAge(1);
        assertEquals(2, series.getItemCount());
        assertEquals(d2, series.getTimePeriod(0));
    }

    // getMinY/getMaxY on empty series returns NaN
    @Test
    public void testGetMinY_getMaxY_emptySeries() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        assertTrue(Double.isNaN(series.getMinY()));
        assertTrue(Double.isNaN(series.getMaxY()));
    }

    // getTimePeriodClass is null initially, set after first add
    @Test
    public void testGetTimePeriodClass_nullInitially_andAfterAdd() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        assertNull(series.getTimePeriodClass());
        series.add(new Day(1, 1, 2001), 1.0);
        assertEquals(Day.class, series.getTimePeriodClass());
    }

    // getDataItem(int) returns a clone - mutating it does not affect the series
    @Test
    public void testGetDataItem_byIndex_returnsClone() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.add(new Day(1, 1, 2001), 1.0);
        TimeSeriesDataItem item = series.getDataItem(0);
        item.setValue(new Double(99.0));
        assertEquals(1.0, series.getValue(0).doubleValue(), 1e-9);
    }

    // getDataItem(RegularTimePeriod) found returns item, not found returns null
    @Test
    public void testGetDataItem_byPeriod_foundAndNotFound() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        series.add(d1, 1.0);
        assertNotNull(series.getDataItem(d1));
        assertNull(series.getDataItem(d2));
    }

    // getTimePeriod returns the correct period at the given index
    @Test
    public void testGetTimePeriod_returnsCorrectPeriod() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        series.add(d1, 1.0);
        assertEquals(d1, series.getTimePeriod(0));
    }

    // getNextTimePeriod returns the successor of the last period
    @Test
    public void testGetNextTimePeriod_returnsNextInSequence() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        series.add(d1, 1.0);
        RegularTimePeriod next = series.getNextTimePeriod();
        assertEquals(d1.next(), next);
    }

    // getTimePeriods returns all periods currently in the series
    @Test
    public void testGetTimePeriods_returnsAllPeriods() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        series.add(d1, 1.0);
        series.add(d2, 2.0);
        Collection periods = series.getTimePeriods();
        assertEquals(2, periods.size());
        assertTrue(periods.contains(d1));
        assertTrue(periods.contains(d2));
    }

    // getTimePeriodsUniqueToOtherSeries finds periods present only in other series
    @Test
    public void testGetTimePeriodsUniqueToOtherSeries() throws Throwable {
        TimeSeries s1 = new TimeSeries("S1");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        s1.add(d1, 1.0);
        TimeSeries s2 = new TimeSeries("S2");
        s2.add(d1, 1.0);
        s2.add(d2, 2.0);
        Collection unique = s1.getTimePeriodsUniqueToOtherSeries(s2);
        assertEquals(1, unique.size());
        assertTrue(unique.contains(d2));
    }

    // getIndex with null period throws IllegalArgumentException
    @Test
    public void testGetIndex_nullPeriod_throwsException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        try {
            series.getIndex(null);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // getValue(int) and getValue(period) found / not found
    @Test
    public void testGetValue_byIndexAndPeriod() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        series.add(d1, 1.0);
        assertEquals(1.0, series.getValue(0).doubleValue(), 1e-9);
        assertEquals(1.0, series.getValue(d1).doubleValue(), 1e-9);
        assertNull(series.getValue(d2));
    }

    // add(null) throws IllegalArgumentException
    @Test
    public void testAdd_nullItem_throwsException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        try {
            series.add((TimeSeriesDataItem) null);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // adding an item with a different time period class throws SeriesException
    @Test
    public void testAdd_differentPeriodClass_throwsSeriesException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.add(new Day(1, 1, 2001), 1.0);
        try {
            series.add(new Year(2001), 2.0);
            fail("Expected SeriesException");
        }
        catch (SeriesException expected) {
        }
    }

    // adding a duplicate period throws SeriesException
    @Test
    public void testAdd_duplicatePeriod_throwsSeriesException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        series.add(d1, 1.0);
        try {
            series.add(d1, 2.0);
            fail("Expected SeriesException");
        }
        catch (SeriesException expected) {
        }
    }

    // add(period, double) correctly stores the item
    @Test
    public void testAdd_periodDouble_addsItem() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        series.add(d1, 5.0);
        assertEquals(1, series.getItemCount());
        assertEquals(5.0, series.getValue(d1).doubleValue(), 1e-9);
    }

    // add(period, Number) permits a null value
    @Test
    public void testAdd_periodNumberNull_allowed() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        series.add(d1, (Number) null);
        assertEquals(1, series.getItemCount());
        assertNull(series.getValue(0));
    }

    // update(period, value) for a non-existent period throws SeriesException
    @Test
    public void testUpdate_periodNotFound_throwsSeriesException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        series.add(d1, 1.0);
        try {
            series.update(d2, new Double(5.0));
            fail("Expected SeriesException");
        }
        catch (SeriesException expected) {
        }
    }

    // update(index, value) updates the value and correctly recalculates maxY
    @Test
    public void testUpdate_byIndex_updatesValueAndBounds() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.add(new Day(1, 1, 2001), 1.0);
        series.add(new Day(2, 1, 2001), 5.0);
        series.add(new Day(3, 1, 2001), 10.0);
        series.update(1, new Double(20.0));
        assertEquals(20.0, series.getValue(1).doubleValue(), 1e-9);
        assertEquals(20.0, series.getMaxY(), 1e-9);
        assertEquals(1.0, series.getMinY(), 1e-9);
    }

    // addAndOrUpdate returns series of overwritten values
    @Test
    public void testAddAndOrUpdate_returnsOverwrittenValues() throws Throwable {
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        TimeSeries series1 = new TimeSeries("A");
        series1.add(d1, 1.0);
        series1.add(d2, 2.0);
        TimeSeries series2 = new TimeSeries("B");
        series2.add(d1, 100.0);
        TimeSeries overwritten = series2.addAndOrUpdate(series1);
        assertEquals(1, overwritten.getItemCount());
        assertEquals(100.0, overwritten.getValue(0).doubleValue(), 1e-9);
    }

    // addOrUpdate with a new period adds the item and returns null
    @Test
    public void testAddOrUpdate_newPeriod_returnsNull() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        series.add(d1, 1.0);
        TimeSeriesDataItem overwritten = series.addOrUpdate(d2, 5.0);
        assertNull(overwritten);
        assertEquals(2, series.getItemCount());
    }

    // addOrUpdate on existing item (no-iterate branch) must correctly update maxY (catches minIgnoreNaN/maxIgnoreNaN bug)
    @Test
    public void testAddOrUpdate_existingPeriod_noIterate_updatesMaxYCorrectly() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        Day d3 = new Day(3, 1, 2001);
        series.add(d1, 1.0);
        series.add(d2, 5.0);
        series.add(d3, 10.0);
        TimeSeriesDataItem overwritten = series.addOrUpdate(d2, 20.0);
        assertNotNull(overwritten);
        assertEquals(5.0, overwritten.getValue().doubleValue(), 1e-9);
        assertEquals(1.0, series.getMinY(), 1e-9);
        assertEquals(20.0, series.getMaxY(), 1e-9);
    }

    // addOrUpdate on existing item where old value was the max (iterate branch) recalculates bounds correctly
    @Test
    public void testAddOrUpdate_existingPeriod_iterateBranch_recalculatesBounds() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        Day d3 = new Day(3, 1, 2001);
        series.add(d1, 1.0);
        series.add(d2, 5.0);
        series.add(d3, 10.0);
        series.addOrUpdate(d3, 2.0);
        assertEquals(1.0, series.getMinY(), 1e-9);
        assertEquals(5.0, series.getMaxY(), 1e-9);
    }

    // removeAgedItems(boolean) with items within limit performs no removal (0 iterations)
    @Test
    public void testRemoveAgedItems_boolean_withinLimit_noRemoval() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.add(new Day(1, 1, 2001), 1.0);
        series.add(new Day(2, 1, 2001), 2.0);
        series.removeAgedItems(true);
        assertEquals(2, series.getItemCount());
    }

    // removeAgedItems(long, boolean) removes items older than the given time
    @Test
    public void testRemoveAgedItems_longNotify_removesOldItems() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.add(new Day(1, 1, 2001), 1.0);
        series.setMaximumItemAge(0);
        long futureMillis = 32503680000000L; // approx year 3000, fixed constant
        series.removeAgedItems(futureMillis, true);
        assertEquals(0, series.getItemCount());
    }

    // clear() removes all items and resets time period class and bounds
    @Test
    public void testClear_removesAllDataAndResetsBounds() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.add(new Day(1, 1, 2001), 1.0);
        series.add(new Day(2, 1, 2001), 2.0);
        series.clear();
        assertEquals(0, series.getItemCount());
        assertNull(series.getTimePeriodClass());
        assertTrue(Double.isNaN(series.getMinY()));
        assertTrue(Double.isNaN(series.getMaxY()));
    }

    // delete(period) removes an existing item and does nothing for a non-existing one
    @Test
    public void testDelete_byPeriod_existingAndNonExisting() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        Day d3 = new Day(3, 1, 2001);
        series.add(d1, 1.0);
        series.add(d2, 2.0);
        series.delete(d1);
        assertEquals(1, series.getItemCount());
        assertNull(series.getValue(d1));
        series.delete(d3);
        assertEquals(1, series.getItemCount());
    }

    // delete(start, end) removes the specified inclusive range
    @Test
    public void testDelete_startEnd_validRange_removesItems() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        Day d3 = new Day(3, 1, 2001);
        series.add(d1, 1.0);
        series.add(d2, 2.0);
        series.add(d3, 3.0);
        series.delete(0, 1);
        assertEquals(1, series.getItemCount());
        assertEquals(d3, series.getTimePeriod(0));
    }

    // delete(start, end) with end < start throws IllegalArgumentException
    @Test
    public void testDelete_startEnd_endLessThanStart_throwsException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.add(new Day(1, 1, 2001), 1.0);
        try {
            series.delete(2, 1);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // clone() produces an independent copy
    @Test
    public void testClone_independentCopy() throws Throwable {
        TimeSeries orig = new TimeSeries("Orig");
        orig.add(new Day(1, 1, 2001), 1.0);
        TimeSeries clone = (TimeSeries) orig.clone();
        orig.add(new Day(2, 1, 2001), 2.0);
        assertEquals(1, clone.getItemCount());
        assertEquals(2, orig.getItemCount());
    }

    // createCopy(int, int) with a valid range returns the correct subset
    @Test
    public void testCreateCopy_intRange_validRange_returnsSubset() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        Day d3 = new Day(3, 1, 2001);
        series.add(d1, 1.0);
        series.add(d2, 2.0);
        series.add(d3, 3.0);
        TimeSeries copy = series.createCopy(1, 2);
        assertEquals(2, copy.getItemCount());
        assertEquals(d2, copy.getTimePeriod(0));
        assertEquals(3.0, copy.getValue(1).doubleValue(), 1e-9);
    }

    // createCopy(int, int) with negative start throws IllegalArgumentException
    @Test
    public void testCreateCopy_intRange_startNegative_throwsException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.add(new Day(1, 1, 2001), 1.0);
        try {
            series.createCopy(-1, 0);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // createCopy(int, int) with end < start throws IllegalArgumentException
    @Test
    public void testCreateCopy_intRange_endLessThanStart_throwsException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        series.add(new Day(1, 1, 2001), 1.0);
        try {
            series.createCopy(1, 0);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // createCopy(period, period) with null start/end throws IllegalArgumentException
    @Test
    public void testCreateCopy_periodRange_nullArgs_throwException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        try {
            series.createCopy((RegularTimePeriod) null, d1);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
        try {
            series.createCopy(d1, (RegularTimePeriod) null);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // createCopy(period, period) with start after end throws IllegalArgumentException
    @Test
    public void testCreateCopy_periodRange_startAfterEnd_throwsException() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d3 = new Day(3, 1, 2001);
        series.add(d1, 1.0);
        try {
            series.createCopy(d3, d1);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    // createCopy(period, period) with a valid range returns the correct subset
    @Test
    public void testCreateCopy_periodRange_validRange_returnsSubset() throws Throwable {
        TimeSeries series = new TimeSeries("S");
        Day d1 = new Day(1, 1, 2001);
        Day d2 = new Day(2, 1, 2001);
        Day d3 = new Day(3, 1, 2001);
        series.add(d1, 1.0);
        series.add(d2, 2.0);
        series.add(d3, 3.0);
        TimeSeries copy = series.createCopy(d1, d2);
        assertEquals(2, copy.getItemCount());
    }

    // equals: identical content returns true, different content returns false
    @Test
    public void testEquals_sameAndDifferentContent() throws Throwable {
        Day d1 = new Day(1, 1, 2001);
        TimeSeries s1 = new TimeSeries("S", "D", "R");
        s1.add(d1, 1.0);
        TimeSeries s2 = new TimeSeries("S", "D", "R");
        s2.add(d1, 1.0);
        assertTrue(s1.equals(s2));
        TimeSeries s3 = new TimeSeries("S", "D", "R");
        s3.add(d1, 2.0);
        assertFalse(s1.equals(s3));
    }

    // hashCode is consistent for equal objects
    @Test
    public void testHashCode_consistentForEqualObjects() throws Throwable {
        Day d1 = new Day(1, 1, 2001);
        TimeSeries s1 = new TimeSeries("S", "D", "R");
        s1.add(d1, 1.0);
        TimeSeries s2 = new TimeSeries("S", "D", "R");
        s2.add(d1, 1.0);
        assertEquals(s1.hashCode(), s2.hashCode());
    }
}
