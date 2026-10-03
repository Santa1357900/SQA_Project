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
    public void testConstructorsAndDefaults() throws Throwable {
        TimeSeries series1 = new TimeSeries("Series1");
        assertEquals("Series1", series1.getKey());
        assertEquals("Time", series1.getDomainDescription());
        assertEquals("Value", series1.getRangeDescription());
        assertEquals(Day.class, series1.getTimePeriodClass());
        assertEquals(Integer.MAX_VALUE, series1.getMaximumItemCount());
        assertEquals(Long.MAX_VALUE, series1.getMaximumItemAge());
        assertEquals(0, series1.getItemCount());

        TimeSeries series2 = new TimeSeries("Series2", Month.class);
        assertEquals(Month.class, series2.getTimePeriodClass());

        TimeSeries series3 = new TimeSeries("Series3", "DomainDesc", "RangeDesc", Year.class);
        assertEquals("DomainDesc", series3.getDomainDescription());
        assertEquals("RangeDesc", series3.getRangeDescription());
        assertEquals(Year.class, series3.getTimePeriodClass());
    }

    @Test
    public void testDomainAndRangeDescriptions() throws Throwable {
        TimeSeries series = new TimeSeries("Test");
        series.setDomainDescription("NewDomain");
        assertEquals("NewDomain", series.getDomainDescription());

        series.setRangeDescription("NewRange");
        assertEquals("NewRange", series.getRangeDescription());

        series.setDomainDescription(null);
        assertNull(series.getDomainDescription());

        series.setRangeDescription(null);
        assertNull(series.getRangeDescription());
    }

    @Test
    public void testMaximumItemCount() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        series.setMaximumItemCount(2);
        assertEquals(2, series.getMaximumItemCount());

        series.add(new Day(1, 1, 2020), 100.0);
        series.add(new Day(2, 1, 2020), 200.0);
        assertEquals(2, series.getItemCount());

        // Adding a third item should drop the first item
        series.add(new Day(3, 1, 2020), 300.0);
        assertEquals(2, series.getItemCount());
        assertEquals(new Day(2, 1, 2020), series.getTimePeriod(0));
        assertEquals(new Day(3, 1, 2020), series.getTimePeriod(1));

        try {
            series.setMaximumItemCount(-1);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Negative"));
        }
    }

    @Test
    public void testMaximumItemAge() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        series.setMaximumItemAge(2L);
        assertEquals(2L, series.getMaximumItemAge());

        series.add(new Day(1, 1, 2020), 10.0);
        series.add(new Day(2, 1, 2020), 20.0);
        // Latest is Day 2 (serial index). Age rule: latest - item_period <= maxAge (2)
        // Day 3 added: latest becomes Day 3. Day 1 serial index is 2 days older than Day 3? Let's check serial index.
        // Day 1 to Day 3 diff is 2. Let's test setMaximumItemAge with explicit values.
        
        series.setMaximumItemAge(1L);
        assertEquals(1L, series.getMaximumItemAge());

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
        Day day1 = new Day(15, 6, 2020);
        Day day2 = new Day(16, 6, 2020);
        Day day0 = new Day(14, 6, 2020);

        series.add(day2, 20.0);
        series.add(day0, 0.0); // Out of order insertion via binary search
        series.add(day1, 10.0);

        assertEquals(3, series.getItemCount());
        assertEquals(day0, series.getTimePeriod(0));
        assertEquals(day1, series.getTimePeriod(1));
        assertEquals(day2, series.getTimePeriod(2));

        assertEquals(Double.valueOf(0.0), series.getValue(0));
        assertEquals(Double.valueOf(10.0), series.getValue(1));
        assertEquals(Double.valueOf(20.0), series.getValue(2));

        assertNotNull(series.getDataItem(day1));
        assertEquals(1, series.getIndex(day1));

        // Test non-existent period lookups
        Day missingDay = new Day(1, 1, 1900);
        assertNull(series.getDataItem(missingDay));
        assertNull(series.getValue(missingDay));
        assertEquals(-1, series.getIndex(missingDay));

        List items = series.getItems();
        assertNotNull(items);
        assertEquals(3, items.size());

        Collection periods = series.getTimePeriods();
        assertEquals(3, periods.size());
    }

    @Test
    public void testAddEdgeCasesAndExceptions() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);

        try {
            series.add((TimeSeriesDataItem) null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }

        try {
            // Adding wrong period class (Month instead of Day)
            series.add(new Month(1, 2020), 50.0);
            fail("Should have thrown SeriesException");
        } catch (org.jfree.data.general.SeriesException e) {
            assertTrue(e.getMessage().contains("time period class"));
        }

        // Add valid item, then try to add duplicate period
        Day day = new Day(1, 1, 2020);
        series.add(day, 10.0);
        try {
            series.add(day, 20.0);
            fail("Should have thrown SeriesException for duplicate");
        } catch (org.jfree.data.general.SeriesException e) {
            assertTrue(e.getMessage().contains("already contains"));
        }
    }

    @Test
    public void testAddOverloads() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        Day day = new Day(10, 5, 2020);

        series.add(day, 100.0, true);
        assertEquals(1, series.getItemCount());

        series.add(new Day(11, 5, 2020), Double.valueOf(200.0), false);
        assertEquals(2, series.getItemCount());

        series.add(new Day(12, 5, 2020), (Number) null, true);
        assertNull(series.getValue(2));
    }

    @Test
    public void testUpdateMethods() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        Day day = new Day(1, 1, 2020);
        series.add(day, 5.0);

        series.update(0, Double.valueOf(10.0));
        assertEquals(Double.valueOf(10.0), series.getValue(0));

        series.update(day, Double.valueOf(15.0));
        assertEquals(Double.valueOf(15.0), series.getValue(day));

        try {
            series.update(new Day(2, 1, 2020), Double.valueOf(20.0));
            fail("Should have thrown SeriesException");
        } catch (org.jfree.data.general.SeriesException e) {
            assertTrue(e.getMessage().contains("period does not exist"));
        }
    }

    @Test
    public void testAddAndOrUpdate() throws Throwable {
        TimeSeries series1 = new TimeSeries("S1", Day.class);
        Day day1 = new Day(1, 1, 2020);
        Day day2 = new Day(2, 1, 2020);
        series1.add(day1, 10.0);
        series1.add(day2, 20.0);

        TimeSeries series2 = new TimeSeries("S2", Day.class);
        Day day2_overlap = new Day(2, 1, 2020);
        Day day3 = new Day(3, 1, 2020);
        series2.add(day2_overlap, 99.0);
        series2.add(day3, 30.0);

        TimeSeries overwritten = series1.addAndOrUpdate(series2);
        assertEquals(Double.valueOf(99.0), series1.getValue(day2));
        assertEquals(Double.valueOf(30.0), series1.getValue(day3));
        assertEquals(1, overwritten.getItemCount());
        assertEquals(Double.valueOf(20.0), overwritten.getValue(0));
    }

    @Test
    public void testAddOrUpdate() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        Day day = new Day(1, 1, 2020);

        TimeSeriesDataItem old1 = series.addOrUpdate(day, 10.0);
        assertNull(old1);
        assertEquals(1, series.getItemCount());

        TimeSeriesDataItem old2 = series.addOrUpdate(day, 20.0);
        assertNotNull(old2);
        assertEquals(Double.valueOf(10.0), old2.getValue());
        assertEquals(Double.valueOf(20.0), series.getValue(day));

        try {
            series.addOrUpdate(null, 30.0);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }
    }

    @Test
    public void testRemoveAgedItems() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        series.setMaximumItemAge(1L);
        series.add(new Day(1, 1, 2020), 1.0);
        series.add(new Day(5, 1, 2020), 5.0);
        // Should age out Day 1 because latest is Day 5, age 5 - 1 = 4 > 1
        assertEquals(1, series.getItemCount());
        assertEquals(new Day(5, 1, 2020), series.getTimePeriod(0));
    }

    @Test
    public void testClearAndDelete() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        Day d1 = new Day(1, 1, 2020);
        Day d2 = new Day(2, 1, 2020);
        Day d3 = new Day(3, 1, 2020);

        series.add(d1, 1.0);
        series.add(d2, 2.0);
        series.add(d3, 3.0);

        series.delete(d1);
        assertEquals(2, series.getItemCount());
        assertEquals(d2, series.getTimePeriod(0));

        series.delete(0, 0);
        assertEquals(1, series.getItemCount());
        assertEquals(d3, series.getTimePeriod(0));

        try {
            series.delete(1, 0);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("start <= end"));
        }

        series.clear();
        assertEquals(0, series.getItemCount());

        // Clearing an already empty series should do nothing safely
        series.clear();
        assertEquals(0, series.getItemCount());
    }

    @Test
    public void testCloneAndCreateCopy() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        Day d1 = new Day(1, 1, 2020);
        Day d2 = new Day(2, 1, 2020);
        Day d3 = new Day(3, 1, 2020);
        series.add(d1, 10.0);
        series.add(d2, 20.0);
        series.add(d3, 30.0);

        TimeSeries clone = (TimeSeries) series.clone();
        assertEquals(series, clone);

        TimeSeries copyIndices = series.createCopy(0, 1);
        assertEquals(2, copyIndices.getItemCount());
        assertEquals(d1, copyIndices.getTimePeriod(0));
        assertEquals(d2, copyIndices.getTimePeriod(1));

        try {
            series.createCopy(-1, 1);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("start >= 0"));
        }

        try {
            series.createCopy(2, 1);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("start <= end"));
        }

        TimeSeries copyPeriods = series.createCopy(d1, d3);
        assertEquals(3, copyPeriods.getItemCount());

        // Test copy with periods out of range / empty range
        Day dBefore = new Day(1, 1, 2019);
        Day dAfter = new Day(1, 1, 2021);
        TimeSeries emptyCopy = series.createCopy(dBefore, dBefore);
        assertEquals(0, emptyCopy.getItemCount());

        try {
            series.createCopy((RegularTimePeriod) null, d3);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }

        try {
            series.createCopy(d1, (RegularTimePeriod) null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Null"));
        }

        try {
            series.createCopy(d3, d1);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("on or before end"));
        }
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        TimeSeries s1 = new TimeSeries("Test", Day.class);
        TimeSeries s2 = new TimeSeries("Test", Day.class);

        assertTrue(s1.equals(s1));
        assertTrue(s1.equals(s2));
        assertEquals(s1.hashCode(), s2.hashCode());

        assertFalse(s1.equals(null));
        assertFalse(s1.equals("NotATimeSeries"));

        s1.setDomainDescription("Domain1");
        assertFalse(s1.equals(s2));
        s2.setDomainDescription("Domain1");
        assertTrue(s1.equals(s2));

        s1.setRangeDescription("Range1");
        assertFalse(s1.equals(s2));
        s2.setRangeDescription("Range1");
        assertTrue(s1.equals(s2));

        s1.setMaximumItemCount(10);
        assertFalse(s1.equals(s2));
        s2.setMaximumItemCount(10);
        assertTrue(s1.equals(s2));

        s1.setMaximumItemAge(5L);
        assertFalse(s1.equals(s2));
        s2.setMaximumItemAge(5L);
        assertTrue(s1.equals(s2));

        s1.add(new Day(1, 1, 2020), 100.0);
        assertFalse(s1.equals(s2));
        s2.add(new Day(1, 1, 2020), 100.0);
        assertTrue(s1.equals(s2));

        // Add more items to test hashcode branches (>1 and >2 items)
        s1.add(new Day(2, 1, 2020), 200.0);
        s1.add(new Day(3, 1, 2020), 300.0);
        s2.add(new Day(2, 1, 2020), 200.0);
        s2.add(new Day(3, 1, 2020), 300.0);
        assertTrue(s1.hashCode() != 0);
    }

    @Test
    public void testGetNextTimePeriod() throws Throwable {
        TimeSeries series = new TimeSeries("Test", Day.class);
        series.add(new Day(1, 1, 2020), 10.0);
        series.add(new Day(2, 1, 2020), 20.0);

        RegularTimePeriod next = series.getNextTimePeriod();
        assertEquals(new Day(3, 1, 2020), next);
    }

    @Test
    public void testGetTimePeriodsUniqueToOtherSeries() throws Throwable {
        TimeSeries s1 = new TimeSeries("S1", Day.class);
        s1.add(new Day(1, 1, 2020), 1.0);
        s1.add(new Day(2, 1, 2020), 2.0);

        TimeSeries s2 = new TimeSeries("S2", Day.class);
        s2.add(new Day(2, 1, 2020), 2.0);
        s2.add(new Day(3, 1, 2020), 3.0);

        Collection unique = s1.getTimePeriodsUniqueToOtherSeries(s2);
        assertEquals(1, unique.size());
        assertTrue(unique.contains(new Day(3, 1, 2020)));
    }
}