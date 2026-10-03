package org.jfree.data.time;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

import org.jfree.data.general.SeriesException;

public class TimeSeriesClaudeTest {

    private TimeSeries series;

    @Before
    public void setUp() throws Throwable {
        series = new TimeSeries("Test Series");
    }

    // default constructor: default domain/range descriptions and Day time period class
    @Test
    public void testConstructorDefault_setsDefaultDomainRangeAndDayClass() throws Throwable {
        assertEquals("Time", series.getDomainDescription());
        assertEquals("Value", series.getRangeDescription());
        assertEquals(Day.class, series.getTimePeriodClass());
    }

    // full constructor: all fields set as given
    @Test
    public void testConstructorFull_setsAllFields() throws Throwable {
        TimeSeries s = new TimeSeries("S3", "D", "R", Year.class);
        assertEquals("D", s.getDomainDescription());
        assertEquals("R", s.getRangeDescription());
        assertEquals(Year.class, s.getTimePeriodClass());
        assertEquals(0, s.getItemCount());
    }

    // setDomainDescription/setRangeDescription round trip
    @Test
    public void testGetSetDomainAndRangeDescription() throws Throwable {
        series.setDomainDescription("MyDomain");
        series.setRangeDescription("MyRange");
        assertEquals("MyDomain", series.getDomainDescription());
        assertEquals("MyRange", series.getRangeDescription());
    }

    // getItemCount on empty series
    @Test
    public void testGetItemCount_emptySeries_returnsZero() throws Throwable {
        assertEquals(0, series.getItemCount());
    }

    // getItems returns an unmodifiable list -> add should throw
    @Test
    public void testGetItems_returnsUnmodifiableList() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        List items = series.getItems();
        try {
            items.add(new TimeSeriesDataItem(new Day(2, 1, 2000), 2.0));
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // default maximum item count and age
    @Test
    public void testDefaultMaximumItemCountAndAge() throws Throwable {
        assertEquals(Integer.MAX_VALUE, series.getMaximumItemCount());
        assertEquals(Long.MAX_VALUE, series.getMaximumItemAge());
    }

    // setMaximumItemCount negative -> IllegalArgumentException
    @Test
    public void testSetMaximumItemCount_negative_throwsException() throws Throwable {
        try {
            series.setMaximumItemCount(-1);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // setMaximumItemCount truncates oldest items, keeping newest
    @Test
    public void testSetMaximumItemCount_truncatesOldestItems() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.add(new Day(2, 1, 2000), 2.0);
        series.add(new Day(3, 1, 2000), 3.0);
        series.setMaximumItemCount(2);
        assertEquals(2, series.getItemCount());
        assertEquals(new Day(2, 1, 2000), series.getTimePeriod(0));
        assertEquals(new Day(3, 1, 2000), series.getTimePeriod(1));
    }

    // setMaximumItemAge negative -> IllegalArgumentException
    @Test
    public void testSetMaximumItemAge_negative_throwsException() throws Throwable {
        try {
            series.setMaximumItemAge(-1L);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // setMaximumItemAge removes items older than allowed span
    @Test
    public void testSetMaximumItemAge_removesOldItems() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.add(new Day(2, 1, 2000), 2.0);
        series.add(new Day(10, 1, 2000), 3.0);
        series.setMaximumItemAge(1L);
        assertEquals(1, series.getItemCount());
        assertEquals(new Day(10, 1, 2000), series.getTimePeriod(0));
    }

    // getTimePeriodClass returns class given at construction
    @Test
    public void testGetTimePeriodClass() throws Throwable {
        assertEquals(Day.class, series.getTimePeriodClass());
    }

    // getDataItem(period) found -> returns matching item
    @Test
    public void testGetDataItem_byPeriod_found() throws Throwable {
        Day d = new Day(1, 1, 2000);
        series.add(d, 5.0);
        TimeSeriesDataItem item = series.getDataItem(d);
        assertNotNull(item);
        assertEquals(5.0, item.getValue().doubleValue(), 1e-9);
    }

    // getDataItem(period) not found -> null
    @Test
    public void testGetDataItem_byPeriod_notFound_returnsNull() throws Throwable {
        series.add(new Day(1, 1, 2000), 5.0);
        TimeSeriesDataItem item = series.getDataItem(new Day(2, 1, 2000));
        assertNull(item);
    }

    // getNextTimePeriod returns period after the last one in the series
    @Test
    public void testGetNextTimePeriod() throws Throwable {
        Day d = new Day(1, 1, 2000);
        series.add(d, 1.0);
        RegularTimePeriod next = series.getNextTimePeriod();
        assertEquals(new Day(2, 1, 2000), next);
    }

    // getTimePeriods returns collection with every period
    @Test
    public void testGetTimePeriods_returnsAllPeriods() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.add(new Day(2, 1, 2000), 2.0);
        Collection periods = series.getTimePeriods();
        assertEquals(2, periods.size());
        assertTrue(periods.contains(new Day(1, 1, 2000)));
        assertTrue(periods.contains(new Day(2, 1, 2000)));
    }

    // getIndex with null period -> IllegalArgumentException
    @Test
    public void testGetIndex_nullPeriod_throwsException() throws Throwable {
        try {
            series.getIndex(null);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // getValue(period) not found -> null
    @Test
    public void testGetValue_byPeriod_notFound_returnsNull() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        assertNull(series.getValue(new Day(5, 5, 2005)));
    }

    // add(item) with wrong time period class -> SeriesException
    @Test
    public void testAdd_item_wrongTimePeriodClass_throwsSeriesException() throws Throwable {
        TimeSeriesDataItem item = new TimeSeriesDataItem(new Year(2000), 1.0);
        try {
            series.add(item);
            fail("expected SeriesException");
        }
        catch (SeriesException expected) {
            // expected
        }
    }

    // add(null item) -> IllegalArgumentException
    @Test
    public void testAdd_item_null_throwsException() throws Throwable {
        try {
            series.add((TimeSeriesDataItem) null);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // add duplicate period -> SeriesException, duplicates not permitted
    @Test
    public void testAdd_duplicatePeriod_throwsSeriesException() throws Throwable {
        Day d = new Day(1, 1, 2000);
        series.add(d, 1.0);
        try {
            series.add(d, 2.0);
            fail("expected SeriesException");
        }
        catch (SeriesException expected) {
            // expected
        }
    }

    // adding an out-of-order item inserts it in the correct sorted position
    @Test
    public void testAdd_itemOutOfOrder_insertsAtCorrectPosition() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.add(new Day(3, 1, 2000), 3.0);
        series.add(new Day(2, 1, 2000), 2.0);
        assertEquals(new Day(2, 1, 2000), series.getTimePeriod(1));
    }

    // add(period, double) stores a Double value
    @Test
    public void testAdd_periodValueDouble() throws Throwable {
        series.add(new Day(1, 1, 2000), 3.5);
        assertEquals(3.5, series.getValue(0).doubleValue(), 1e-9);
    }

    // add(period, Number) permits a null value
    @Test
    public void testAdd_periodValueNumberNull() throws Throwable {
        series.add(new Day(1, 1, 2000), (Number) null);
        assertNull(series.getValue(0));
    }

    // update(period, value) on existing period updates value
    @Test
    public void testUpdate_existingPeriod_updatesValue() throws Throwable {
        Day d = new Day(1, 1, 2000);
        series.add(d, 1.0);
        series.update(d, new Double(9.0));
        assertEquals(9.0, series.getValue(0).doubleValue(), 1e-9);
    }

    // update(period, value) on non-existing period -> SeriesException
    @Test
    public void testUpdate_nonExistingPeriod_throwsSeriesException() throws Throwable {
        try {
            series.update(new Day(1, 1, 2000), new Double(1.0));
            fail("expected SeriesException");
        }
        catch (SeriesException expected) {
            // expected
        }
    }

    // update(index, value) updates the value at that index
    @Test
    public void testUpdate_byIndex() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.update(0, new Double(7.0));
        assertEquals(7.0, series.getValue(0).doubleValue(), 1e-9);
    }

    // addOrUpdate with a new period adds item and returns null
    @Test
    public void testAddOrUpdate_newPeriod_returnsNull() throws Throwable {
        TimeSeriesDataItem overwritten = series.addOrUpdate(new Day(1, 1, 2000), 5.0);
        assertNull(overwritten);
        assertEquals(1, series.getItemCount());
    }

    // addOrUpdate with an existing period updates value and returns old item copy
    @Test
    public void testAddOrUpdate_existingPeriod_returnsOverwrittenAndUpdates() throws Throwable {
        Day d = new Day(1, 1, 2000);
        series.add(d, 1.0);
        TimeSeriesDataItem overwritten = series.addOrUpdate(d, 9.0);
        assertNotNull(overwritten);
        assertEquals(1.0, overwritten.getValue().doubleValue(), 1e-9);
        assertEquals(9.0, series.getValue(0).doubleValue(), 1e-9);
    }

    // addAndOrUpdate merges another series, returning overwritten values
    @Test
    public void testAddAndOrUpdate_mergesSeries() throws Throwable {
        Day d = new Day(1, 1, 2000);
        series.add(d, 1.0);
        TimeSeries other = new TimeSeries("Other");
        other.add(d, 2.0);
        other.add(new Day(2, 1, 2000), 3.0);
        TimeSeries overwritten = series.addAndOrUpdate(other);
        assertEquals(1, overwritten.getItemCount());
        assertEquals(1.0, overwritten.getValue(0).doubleValue(), 1e-9);
        assertEquals(2, series.getItemCount());
    }

    // setMaximumItemAge(0) removes older items via removeAgedItems
    @Test
    public void testRemoveAgedItems_removesOldData() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.add(new Day(10, 1, 2000), 2.0);
        series.setMaximumItemAge(0L);
        assertEquals(1, series.getItemCount());
        assertEquals(new Day(10, 1, 2000), series.getTimePeriod(0));
    }

    // clear on non-empty series removes all data items
    @Test
    public void testClear_removesAllItems() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.clear();
        assertEquals(0, series.getItemCount());
    }

    // delete(period) existing removes that item
    @Test
    public void testDelete_byPeriod_existing() throws Throwable {
        Day d = new Day(1, 1, 2000);
        series.add(d, 1.0);
        series.delete(d);
        assertEquals(0, series.getItemCount());
    }

    // delete(period) non-existing leaves series unchanged
    @Test
    public void testDelete_byPeriod_nonExisting_noChange() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.delete(new Day(2, 1, 2000));
        assertEquals(1, series.getItemCount());
    }

    // delete(start, end) removes items in inclusive index range
    @Test
    public void testDelete_byRange() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.add(new Day(2, 1, 2000), 2.0);
        series.add(new Day(3, 1, 2000), 3.0);
        series.delete(0, 1);
        assertEquals(1, series.getItemCount());
        assertEquals(new Day(3, 1, 2000), series.getTimePeriod(0));
    }

    // delete(start, end) with end < start -> IllegalArgumentException
    @Test
    public void testDelete_endLessThanStart_throwsException() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        try {
            series.delete(1, 0);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // clone produces an equal but independent copy
    @Test
    public void testClone_isEqualButIndependent() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        TimeSeries clone = (TimeSeries) series.clone();
        assertEquals(series, clone);
        clone.add(new Day(2, 1, 2000), 2.0);
        assertEquals(1, series.getItemCount());
        assertEquals(2, clone.getItemCount());
    }

    // createCopy(int,int) copies the specified inclusive index range
    @Test
    public void testCreateCopy_intRange() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.add(new Day(2, 1, 2000), 2.0);
        series.add(new Day(3, 1, 2000), 3.0);
        TimeSeries copy = series.createCopy(1, 2);
        assertEquals(2, copy.getItemCount());
        assertEquals(new Day(2, 1, 2000), copy.getTimePeriod(0));
    }

    // createCopy(int,int) end < start -> IllegalArgumentException
    @Test
    public void testCreateCopy_intRange_endLessThanStart_throws() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        try {
            series.createCopy(1, 0);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // createCopy(period,period) copies data falling within the given range
    @Test
    public void testCreateCopy_periodRange_normal() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.add(new Day(2, 1, 2000), 2.0);
        series.add(new Day(3, 1, 2000), 3.0);
        TimeSeries copy = series.createCopy(new Day(2, 1, 2000), new Day(3, 1, 2000));
        assertEquals(2, copy.getItemCount());
    }

    // createCopy(period,period) with null start or end -> IllegalArgumentException
    @Test
    public void testCreateCopy_periodRange_nullArgs_throwException() throws Throwable {
        try {
            series.createCopy((RegularTimePeriod) null, new Day(1, 1, 2000));
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            series.createCopy(new Day(1, 1, 2000), (RegularTimePeriod) null);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // createCopy(period,period) start after end -> IllegalArgumentException
    @Test
    public void testCreateCopy_periodRange_startAfterEnd_throws() throws Throwable {
        try {
            series.createCopy(new Day(2, 1, 2000), new Day(1, 1, 2000));
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // createCopy(period,period) for a gap containing no data must yield an empty series, not throw
    @Test
    public void testCreateCopy_periodRange_emptyGap_returnsEmptySeries() throws Throwable {
        series.add(new Day(1, 1, 2000), 1.0);
        series.add(new Day(3, 1, 2000), 3.0);
        TimeSeries copy = series.createCopy(new Day(2, 1, 2000), new Day(2, 1, 2000));
        assertEquals(0, copy.getItemCount());
    }

    // equals: two series with identical content are equal
    @Test
    public void testEquals_sameContent() throws Throwable {
        TimeSeries s1 = new TimeSeries("Test Series");
        TimeSeries s2 = new TimeSeries("Test Series");
        s1.add(new Day(1, 1, 2000), 1.0);
        s2.add(new Day(1, 1, 2000), 1.0);
        assertTrue(s1.equals(s2));
    }

    // equals: different domain description makes series unequal
    @Test
    public void testEquals_differentDomainDescription() throws Throwable {
        TimeSeries s1 = new TimeSeries("Test Series");
        TimeSeries s2 = new TimeSeries("Test Series");
        s2.setDomainDescription("Other");
        assertFalse(s1.equals(s2));
    }

    // hashCode is consistent for equal objects
    @Test
    public void testHashCode_consistentWithEquals() throws Throwable {
        TimeSeries s1 = new TimeSeries("Test Series");
        TimeSeries s2 = new TimeSeries("Test Series");
        s1.add(new Day(1, 1, 2000), 1.0);
        s2.add(new Day(1, 1, 2000), 1.0);
        assertEquals(s1.hashCode(), s2.hashCode());
    }
}
