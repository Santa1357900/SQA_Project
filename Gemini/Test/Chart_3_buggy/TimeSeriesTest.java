package org.jfree.data.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.jfree.data.general.SeriesException;

public class TimeSeriesTest {

    @Test
    public void testConstructorsAndDefaults() throws Throwable {
        TimeSeries series = new TimeSeries("TestSeries");
        assertEquals("TestSeries", series.getKey());
        assertEquals("Time", series.getDomainDescription());
        assertEquals("Value", series.getRangeDescription());
        assertEquals(Integer.MAX_VALUE, series.getMaximumItemCount());
        assertEquals(Long.MAX_VALUE, series.getMaximumItemAge());
        assertEquals(0, series.getItemCount());
        assertTrue(Double.isNaN(series.getMinY()));
        assertTrue(Double.isNaN(series.getMaxY()));
        assertNull(series.getTimePeriodClass());

        TimeSeries series2 = new TimeSeries("TestSeries2", "CustomDomain", "CustomRange");
        assertEquals("CustomDomain", series2.getDomainDescription());
        assertEquals("CustomRange", series2.getRangeDescription());
    }

    @Test
    public void testDomainAndRangeDescriptions() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.setDomainDescription("NewDomain");
        assertEquals("NewDomain", series.getDomainDescription());

        series.setRangeDescription("NewRange");
        assertEquals("NewRange", series.getRangeDescription());
    }

    @Test
    public void testAddAndGetItems() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);
        Day day3 = new Day(3, 1, 2020);

        series.add(day2, 20.0);
        series.add(day1, 10.0);
        series.add(day3, 30.0);

        assertEquals(3, series.getItemCount());
        assertEquals(day1, series.getTimePeriod(0));
        assertEquals(day2, series.getTimePeriod(1));
        assertEquals(day3, series.getTimePeriod(2));

        assertEquals(Double.valueOf(10.0), series.getValue(0));
        assertEquals(Double.valueOf(20.0), series.getValue(1));
        assertEquals(Double.valueOf(30.0), series.getValue(2));

        assertEquals(10.0, series.getMinY(), 0.001);
        assertEquals(30.0, series.getMaxY(), 0.001);

        assertNotNull(series.getDataItem(0));
        assertNotNull(series.getDataItem(day1));
        assertNull(series.getDataItem(new Day(4, 1, 2020)));

        assertNotNull(series.getRawDataItem(0));
        assertNotNull(series.getRawDataItem(day1));
        assertNull(series.getRawDataItem(new Day(4, 1, 2020)));

        assertEquals(day3.next(), series.getNextTimePeriod());

        Collection periods = series.getTimePeriods();
        assertEquals(3, periods.size());

        TimeSeries seriesOther = new TimeSeries("Other");
        seriesOther.add(day1, 10.0);
        seriesOther.add(new Day(4, 1, 2020), 40.0);

        Collection unique = series.getTimePeriodsUniqueToOtherSeries(seriesOther);
        assertEquals(1, unique.size());
        assertTrue(unique.contains(new Day(4, 1, 2020)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullItem() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.add((TimeSeriesDataItem) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetIndexNullPeriod() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.getIndex(null);
    }

    @Test(expected = SeriesException.class)
    public void testAddMismatchedPeriodClass() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.add(new Day(1, 1, 2020), 10.0);
        series.add(new Month(1, 2020), 20.0);
    }

    @Test(expected = SeriesException.class)
    public void testAddDuplicatePeriod() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        Day day = new Day(1, 1, 2020);
        series.add(day, 10.0);
        series.add(day, 20.0);
    }

    @Test
    public void testAddNumberAndNotifyVariants() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);
        Day day3 = new Day(3, 1, 2020);

        series.add(day1, (Number) null, false);
        series.add(day2, 50.0, false);
        series.add(new TimeSeriesDataItem(day3, 30.0), false);

        assertEquals(3, series.getItemCount());
        assertNull(series.getValue(day1));
        assertEquals(Double.valueOf(50.0), series.getValue(day2));
    }

    @Test
    public void testMaximumItemCount() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.setMaximumItemCount(2);
        assertEquals(2, series.getMaximumItemCount());

        series.add(new Day(1, 1, 2020), 10.0);
        series.add(new Day(2, 1, 2020), 20.0);
        series.add(new Day(3, 1, 2020), 30.0);

        assertEquals(2, series.getItemCount());
        assertEquals(new Day(2, 1, 2020), series.getTimePeriod(0));
        assertEquals(new Day(3, 1, 2020), series.getTimePeriod(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetNegativeMaximumItemCount() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.setMaximumItemCount(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetNegativeMaximumItemAge() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.setMaximumItemAge(-1L);
    }

    @Test
    public void testMaximumItemAgeAndAging() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.setMaximumItemAge(2L);
        assertEquals(2L, series.getMaximumItemAge());

        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);
        Day day4 = new Day(4, 1, 2020);

        series.add(day1, 10.0);
        series.add(day2, 20.0);
        series.add(day4, 40.0); // day1 should be aged out (serial index diff: 4 - 1 = 3 > 2)

        assertEquals(2, series.getItemCount());
        assertEquals(day2, series.getTimePeriod(0));
        assertEquals(day4, series.getTimePeriod(1));

        // Test removeAgedItems with long timestamp
        series.removeAgedItems(day4.getLastMillisecond(), true);
    }

    @Test
    public void testRemoveAgedItemsEmpty() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.removeAgedItems(System.currentTimeMillis(), false);
        assertEquals(0, series.getItemCount());
    }

    @Test
    public void testUpdateByIndexAndPeriod() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        Day day = new Day(1, 1, 2020);
        series.add(day, 10.0);

        series.update(0, 15.0);
        assertEquals(Double.valueOf(15.0), series.getValue(0));

        series.update(day, 25.0);
        assertEquals(Double.valueOf(25.0), series.getValue(day));

        // Test update with NaN / Bounds iteration trigger
        series.update(0, Double.NaN);
        series.update(0, 5.0);
    }

    @Test(expected = SeriesException.class)
    public void testUpdateNonExistentPeriod() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.update(new Day(1, 1, 2020), 10.0);
    }

    @Test
    public void testAddOrUpdateAndAddAndOrUpdate() throws Throwable {
        TimeSeries series1 = new TimeSeries("Series1");
        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);

        series1.add(day1, 10.0);
        TimeSeriesDataItem overwritten = series1.addOrUpdate(day1, 20.0);
        assertNotNull(overwritten);
        assertEquals(Double.valueOf(10.0), overwritten.getValue());
        assertEquals(Double.valueOf(20.0), series1.getValue(day1));

        TimeSeriesDataItem addedNew = series1.addOrUpdate(day2, 30.0);
        assertNull(addedNew);

        TimeSeries series2 = new TimeSeries("Series2");
        series2.add(day1, 50.0);
        series2.add(new Day(3, 1, 2020), 60.0);

        TimeSeries overwrittenSeries = series1.addAndOrUpdate(series2);
        assertNotNull(overwrittenSeries);
        assertEquals(1, overwrittenSeries.getItemCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddOrUpdateNull() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.addOrUpdate(null);
    }

    @Test(expected = SeriesException.class)
    public void testAddOrUpdateMismatchedClass() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.add(new Day(1, 1, 2020), 10.0);
        series.addOrUpdate(new Month(1, 2020), 20.0);
    }

    @Test
    public void testClear() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.add(new Day(1, 1, 2020), 10.0);
        assertEquals(1, series.getItemCount());

        series.clear();
        assertEquals(0, series.getItemCount());
        assertNull(series.getTimePeriodClass());
        assertTrue(Double.isNaN(series.getMinY()));
        assertTrue(Double.isNaN(series.getMaxY()));

        // Clearing an already empty series should do nothing safely
        series.clear();
    }

    @Test
    public void testDeletePeriodAndRange() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);
        Day day3 = new Day(3, 1, 2020);

        series.add(day1, 10.0);
        series.add(day2, 20.0);
        series.add(day3, 30.0);

        series.delete(day2);
        assertEquals(2, series.getItemCount());
        assertNull(series.getDataItem(day2));

        // Delete non-existent period
        series.delete(new Day(10, 1, 2020));

        series.delete(0, 1);
        assertEquals(0, series.getItemCount());
        assertNull(series.getTimePeriodClass());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteInvalidRange() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.add(new Day(1, 1, 2020), 10.0);
        series.delete(1, 0);
    }

    @Test
    public void testCloneAndCreateCopy() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);
        series.add(day1, 10.0);
        series.add(day2, 20.0);

        TimeSeries clone = (TimeSeries) series.clone();
        assertEquals(series, clone);

        TimeSeries copy1 = series.createCopy(0, 1);
        assertEquals(2, copy1.getItemCount());

        TimeSeries copy2 = series.createCopy(day1, day2);
        assertEquals(2, copy2.getItemCount());

        // Test createCopy with empty range
        TimeSeries copyEmpty = series.createCopy(new Day(1, 1, 2021), new Day(2, 1, 2021));
        assertEquals(0, copyEmpty.getItemCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCopyInvalidIndexStart() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.createCopy(-1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCopyInvalidIndexRange() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.createCopy(1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCopyNullStartPeriod() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.createCopy((RegularTimePeriod) null, new Day(1, 1, 2020));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCopyNullEndPeriod() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.createCopy(new Day(1, 1, 2020), (RegularTimePeriod) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateCopyStartAfterEndPeriod() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);
        series.createCopy(day2, day1);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        TimeSeries series1 = new TimeSeries("Test");
        TimeSeries series2 = new TimeSeries("Test");
        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);

        assertTrue(series1.equals(series1));
        assertFalse(series1.equals(null));
        assertFalse(series1.equals("NotATimeSeries"));

        assertEquals(series1, series2);
        assertEquals(series1.hashCode(), series2.hashCode());

        series1.add(day1, 10.0);
        assertFalse(series1.equals(series2));

        series2.add(day1, 10.0);
        assertEquals(series1, series2);

        series1.setDomainDescription("DiffDomain");
        assertFalse(series1.equals(series2));
        series2.setDomainDescription("DiffDomain");

        series1.setRangeDescription("DiffRange");
        assertFalse(series1.equals(series2));
        series2.setRangeDescription("DiffRange");

        series1.setMaximumItemAge(5L);
        assertFalse(series1.equals(series2));
        series2.setMaximumItemAge(5L);

        series1.setMaximumItemCount(5);
        assertFalse(series1.equals(series2));
        series2.setMaximumItemCount(5);

        // Test hashCode with > 2 items
        series1.add(day2, 20.0);
        series1.add(new Day(3, 1, 2020), 30.0);
        assertTrue(series1.hashCode() != 0);
    }
}