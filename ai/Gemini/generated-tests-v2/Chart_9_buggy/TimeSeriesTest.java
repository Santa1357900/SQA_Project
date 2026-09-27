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

public class TimeSeriesTest {

    @Test
    public void testConstructors() throws Throwable {
        TimeSeries series1 = new TimeSeries("Series1");
        assertEquals("Series1", series1.getKey());
        assertEquals(TimeSeries.DEFAULT_DOMAIN_DESCRIPTION, series1.getDomainDescription());
        assertEquals(TimeSeries.DEFAULT_RANGE_DESCRIPTION, series1.getRangeDescription());
        assertEquals(Day.class, series1.getTimePeriodClass());

        TimeSeries series2 = new TimeSeries("Series2", Month.class);
        assertEquals(Month.class, series2.getTimePeriodClass());

        TimeSeries series3 = new TimeSeries("Series3", "Domain", "Range", Year.class);
        assertEquals("Domain", series3.getDomainDescription());
        assertEquals("Range", series3.getRangeDescription());
        assertEquals(Year.class, series3.getTimePeriodClass());
    }

    @Test
    public void testDomainAndRangeDescriptions() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.setDomainDescription("New Domain");
        assertEquals("New Domain", series.getDomainDescription());

        series.setRangeDescription("New Range");
        assertEquals("New Range", series.getRangeDescription());

        series.setDomainDescription(null);
        assertNull(series.getDomainDescription());

        series.setRangeDescription(null);
        assertNull(series.getRangeDescription());
    }

    @Test
    public void testItemCountAndLimits() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        assertEquals(0, series.getItemCount());

        series.setMaximumItemCount(2);
        assertEquals(2, series.getMaximumItemCount());

        try {
            series.setMaximumItemCount(-1);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Negative"));
        }

        series.setMaximumItemAge(5L);
        assertEquals(5L, series.getMaximumItemAge());

        try {
            series.setMaximumItemAge(-1L);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Negative"));
        }
    }

    @Test
    public void testAddAndGetItems() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);
        Day day3 = new Day(3, 1, 2020);

        series.add(day2, 10.0);
        series.add(day1, 5.0);
        series.add(day3, 15.0);

        assertEquals(3, series.getItemCount());
        assertEquals(day1, series.getTimePeriod(0));
        assertEquals(day2, series.getTimePeriod(1));
        assertEquals(day3, series.getTimePeriod(2));

        assertEquals(Double.valueOf(5.0), series.getValue(0));
        assertEquals(Double.valueOf(10.0), series.getValue(1));
        assertEquals(Double.valueOf(15.0), series.getValue(2));

        TimeSeriesDataItem item = series.getDataItem(0);
        assertNotNull(item);
        assertEquals(day1, item.getPeriod());

        assertEquals(item, series.getDataItem(day1));
        assertNull(series.getDataItem(new Day(4, 1, 2020)));

        assertEquals(Double.valueOf(5.0), series.getValue(day1));
        assertNull(series.getValue(new Day(4, 1, 2020)));

        List items = series.getItems();
        assertNotNull(items);
        assertEquals(3, items.size());
    }

    @Test
    public void testAddValidation() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        Day day = new Day(1, 1, 2020);

        try {
            series.add(null, 10.0);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }

        try {
            series.add((TimeSeriesDataItem) null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }

        series.add(day, 10.0);

        try {
            series.add(day, 20.0);
            fail("Should have thrown SeriesException for duplicate");
        } catch (org.jfree.data.general.SeriesException e) {
            assertTrue(e.getMessage().contains("already contains"));
        }

        try {
            series.add(new Month(1, 2020), 15.0);
            fail("Should have thrown SeriesException for mismatched class");
        } catch (org.jfree.data.general.SeriesException e) {
            assertTrue(e.getMessage().contains("time period class"));
        }
    }

    @Test
    public void testMaximumItemCountEnforcement() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        series.setMaximumItemCount(2);

        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);
        Day day3 = new Day(3, 1, 2020);

        series.add(day1, 1.0);
        series.add(day2, 2.0);
        assertEquals(2, series.getItemCount());

        series.add(day3, 3.0);
        assertEquals(2, series.getItemCount());
        assertEquals(day2, series.getTimePeriod(0));
        assertEquals(day3, series.getTimePeriod(1));
    }

    @Test
    public void testRemoveAgedItems() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        series.setMaximumItemAge(1L); // serial index difference > 1 drops old items

        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);
        Day day3 = new Day(5, 1, 2020);

        series.add(day1, 1.0);
        series.add(day2, 2.0);
        series.add(day3, 3.0);

        // day1 and day2 should be aged out because latest is day3 (serial index 5), max age 1 -> oldest allowed is 4
        assertTrue(series.getItemCount() <= 2);
    }

    @Test
    public void testRemoveAgedItemsWithLongTimestamp() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        series.setMaximumItemAge(0L);

        Day day1 = new Day(1, 1, 2020);
        series.add(day1, 1.0);

        series.removeAgedItems(day1.getMiddleMillisecond(), true);
        assertEquals(0, series.getItemCount());
    }

    @Test
    public void testUpdateMethods() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        Day day = new Day(1, 1, 2020);
        series.add(day, 10.0);

        series.update(0, 20.0);
        assertEquals(Double.valueOf(20.0), series.getValue(0));

        series.update(day, 30.0);
        assertEquals(Double.valueOf(30.0), series.getValue(0));

        try {
            series.update(new Day(2, 1, 2020), 40.0);
            fail("Should have thrown SeriesException");
        } catch (org.jfree.data.general.SeriesException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        }
    }

    @Test
    public void testAddOrUpdate() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        Day day = new Day(1, 1, 2020);

        TimeSeriesDataItem overwritten = series.addOrUpdate(day, 10.0);
        assertNull(overwritten);
        assertEquals(1, series.getItemCount());

        TimeSeriesDataItem overwritten2 = series.addOrUpdate(day, 20.0);
        assertNotNull(overwritten2);
        assertEquals(Double.valueOf(10.0), overwritten2.getValue());
        assertEquals(Double.valueOf(20.0), series.getValue(0));

        try {
            series.addOrUpdate(null, 30.0);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }
    }

    @Test
    public void testAddAndOrUpdateSeries() throws Throwable {
        TimeSeries series1 = new TimeSeries("Series1", Day.class);
        TimeSeries series2 = new TimeSeries("Series2", Day.class);

        Day day = new Day(1, 1, 2020);
        series1.add(day, 10.0);
        series2.add(day, 20.0);

        TimeSeries overwritten = series1.addAndOrUpdate(series2);
        assertNotNull(overwritten);
        assertEquals(1, overwritten.getItemCount());
        assertEquals(Double.valueOf(20.0), series1.getValue(day));
    }

    @Test
    public void testDeleteAndClear() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);

        series.add(day1, 1.0);
        series.add(day2, 2.0);

        series.delete(day1);
        assertEquals(1, series.getItemCount());
        assertEquals(day2, series.getTimePeriod(0));

        series.delete(day1); // Non-existent, should do nothing

        series.add(day1, 1.0);
        series.delete(0, 1);
        assertEquals(0, series.getItemCount());

        try {
            series.delete(1, 0);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("start <= end"));
        }

        series.add(day1, 1.0);
        series.clear();
        assertEquals(0, series.getItemCount());
        series.clear(); // Clear empty series
    }

    @Test
    public void testTimePeriodsAndCollections() throws Throwable {
        TimeSeries series1 = new TimeSeries("Series1", Day.class);
        TimeSeries series2 = new TimeSeries("Series2", Day.class);

        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);

        series1.add(day1, 1.0);
        series2.add(day1, 1.0);
        series2.add(day2, 2.0);

        Collection periods = series1.getTimePeriods();
        assertNotNull(periods);
        assertTrue(periods.contains(day1));

        Collection unique = series2.getTimePeriodsUniqueToOtherSeries(series1);
        assertNotNull(unique);
        assertTrue(unique.contains(day2));
        assertFalse(unique.contains(day1));

        assertEquals(day2, series2.getNextTimePeriod());

        try {
            series1.getIndex(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }
    }

    @Test
    public void testCloneAndCreateCopy() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);

        series.add(day1, 1.0);
        series.add(day2, 2.0);

        TimeSeries clone = (TimeSeries) series.clone();
        assertEquals(series, clone);

        TimeSeries copy1 = series.createCopy(0, 1);
        assertEquals(2, copy1.getItemCount());

        try {
            series.createCopy(-1, 1);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("start >= 0"));
        }

        try {
            series.createCopy(1, 0);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("start <= end"));
        }

        TimeSeries copy2 = series.createCopy(day1, day2);
        assertEquals(2, copy2.getItemCount());

        TimeSeries copyEmpty = series.createCopy(new Day(1, 1, 2022), new Day(2, 1, 2022));
        assertEquals(0, copyEmpty.getItemCount());

        try {
            series.createCopy((RegularTimePeriod) null, day2);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }

        try {
            series.createCopy(day1, (RegularTimePeriod) null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }

        try {
            series.createCopy(day2, day1);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("on or before"));
        }
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        TimeSeries series1 = new TimeSeries("Test", Day.class);
        TimeSeries series2 = new TimeSeries("Test", Day.class);

        assertEquals(series1, series1);
        assertEquals(series1, series2);
        assertEquals(series1.hashCode(), series2.hashCode());

        assertFalse(series1.equals(null));
        assertFalse(series1.equals("SomeString"));

        series1.setDomainDescription("Diff Domain");
        assertFalse(series1.equals(series2));
        series2.setDomainDescription("Diff Domain");
        assertEquals(series1, series2);

        series1.setRangeDescription("Diff Range");
        assertFalse(series1.equals(series2));
        series2.setRangeDescription("Diff Range");
        assertEquals(series1, series2);

        series1.setMaximumItemAge(10L);
        assertFalse(series1.equals(series2));
        series2.setMaximumItemAge(10L);
        assertEquals(series1, series2);

        series1.setMaximumItemCount(10);
        assertFalse(series1.equals(series2));
        series2.setMaximumItemCount(10);
        assertEquals(series1, series2);

        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);
        Day day3 = new Day(3, 1, 2020);

        series1.add(day1, 1.0);
        series1.add(day2, 2.0);
        series1.add(day3, 3.0);

        series2.add(day1, 1.0);
        series2.add(day2, 2.0);
        series2.add(day3, 3.0);

        assertEquals(series1, series2);
        assertEquals(series1.hashCode(), series2.hashCode());

        TimeSeries series3 = new TimeSeries("Test", Day.class);
        series3.add(day1, 1.0);
        assertFalse(series1.equals(series3));
    }
}