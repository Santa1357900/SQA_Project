package org.joda.time.chrono;

import junit.framework.TestCase;
import org.joda.time.Chronology;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeUtils;
import org.joda.time.DurationField;
import org.joda.time.MonthDay;
import org.joda.time.ReadablePartial;

public class BasicMonthOfYearDateTimeFieldTest extends TestCase {

    private Chronology chronology;
    private DateTimeField monthField;

    protected void setUp() throws Exception {
        super.setUp();
        chronology = ISOChronology.getInstanceUTC();
        monthField = chronology.monthOfYear();
    }

    public void testLenient() throws Throwable {
        assertFalse(monthField.isLenient());
    }

    public void testGetAndSet() throws Throwable {
        long instant = chronology.getDateTimeMillis(2004, 2, 29, 12, 30, 0, 0);
        assertEquals(2, monthField.get(instant));

        long newInstant = monthField.set(instant, 6);
        assertEquals(6, monthField.get(newInstant));
        // Check time part preserved
        assertEquals(12 * 3600000L + 30 * 60000L, chronology.getMillisOfDay(newInstant));
    }

    public void testSetOutOfBounds() throws Throwable {
        try {
            monthField.set(0L, 13);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }

        try {
            monthField.set(0L, 0);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    public void testSetCoerceDayOfMonth() throws Throwable {
        // March 31 set to month 2 (February 2004 is leap year, 29 days)
        long march31 = chronology.getDateTimeMillis(2004, 3, 31, 0, 0, 0, 0);
        long feb29 = monthField.set(march31, 2);
        assertEquals(2, monthField.get(feb29));
        assertEquals(29, chronology.getDayOfMonth(feb29));

        // March 31 set to month 2 in non-leap year (2003, 28 days)
        long march31_2003 = chronology.getDateTimeMillis(2003, 3, 31, 0, 0, 0, 0);
        long feb28 = monthField.set(march31_2003, 2);
        assertEquals(2, monthField.get(feb28));
        assertEquals(28, chronology.getDayOfMonth(feb28));
    }

    public void testAddIntZero() throws Throwable {
        long instant = chronology.getDateTimeMillis(2004, 5, 10, 0, 0, 0, 0);
        assertEquals(instant, monthField.add(instant, 0));
    }

    public void testAddPositiveMonths() throws Throwable {
        long instant = chronology.getDateTimeMillis(2004, 1, 31, 0, 0, 0, 0);
        // Add 1 month -> Feb 29, 2004 (leap)
        long res1 = monthField.add(instant, 1);
        assertEquals(2004, chronology.getYear(res1));
        assertEquals(2, chronology.getMonthOfYear(res1));
        assertEquals(29, chronology.getDayOfMonth(res1));

        // Add 14 months -> March 31, 2005
        long res2 = monthField.add(instant, 14);
        assertEquals(2005, chronology.getYear(res2));
        assertEquals(3, chronology.getMonthOfYear(res2));
        assertEquals(31, chronology.getDayOfMonth(res2));
    }

    public void testAddNegativeMonths() throws Throwable {
        long instant = chronology.getDateTimeMillis(2004, 3, 31, 0, 0, 0, 0);
        // Subtract 1 month -> Feb 29, 2004
        long res1 = monthField.add(instant, -1);
        assertEquals(2004, chronology.getYear(res1));
        assertEquals(2, chronology.getMonthOfYear(res1));
        assertEquals(29, chronology.getDayOfMonth(res1));

        // Subtract 15 months (monthToUse negative with boundary condition tests)
        long instant2 = chronology.getDateTimeMillis(2004, 1, 15, 0, 0, 0, 0);
        long res2 = monthField.add(instant2, -13);
        assertEquals(2002, chronology.getYear(res2));
        assertEquals(12, chronology.getMonthOfYear(res2));
    }

    public void testAddLongMonths() throws Throwable {
        long instant = chronology.getDateTimeMillis(2004, 1, 15, 0, 0, 0, 0);
        long res = monthField.add(instant, 2L);
        assertEquals(3, chronology.getMonthOfYear(res));

        // Test long that exceeds int max/min
        try {
            monthField.add(instant, Long.MAX_VALUE);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null);
        }
    }

    public void testAddReadablePartial() throws Throwable {
        MonthDay md = new MonthDay(2, 29, chronology);
        int[] values = new int[] {2, 29};
        int[] newValues = monthField.add(md, 0, values, 0);
        assertSame(values, newValues);

        int[] addedValues = monthField.add(md, 0, values, 48);
        assertNotNull(addedValues);
        assertEquals(2, addedValues.length);
        assertEquals(2, addedValues[0]);
        assertEquals(29, addedValues[1]); // 2004 to 2008 leap year check via partial
    }

    public void testAddWrapField() throws Throwable {
        long instant = chronology.getDateTimeMillis(2004, 11, 15, 0, 0, 0, 0);
        long res = monthField.addWrapField(instant, 3);
        assertEquals(2, chronology.getMonthOfYear(res));
        assertEquals(2004, chronology.getYear(res));
    }

    public void testGetDifferenceAsLong() throws Throwable {
        long start = chronology.getDateTimeMillis(2004, 1, 15, 0, 0, 0, 0);
        long end = chronology.getDateTimeMillis(2004, 4, 15, 0, 0, 0, 0);
        assertEquals(3L, monthField.getDifferenceAsLong(end, start));
        assertEquals(-3L, monthField.getDifferenceAsLong(start, end));

        // Test with end-of-month coercion in difference calculation
        long jan31 = chronology.getDateTimeMillis(2004, 1, 31, 0, 0, 0, 0);
        long apr30 = chronology.getDateTimeMillis(2004, 4, 30, 0, 0, 0, 0);
        assertEquals(3L, monthField.getDifferenceAsLong(apr30, jan31));
    }

    public void testLeapProperties() throws Throwable {
        long leapInstant = chronology.getDateTimeMillis(2004, 2, 10, 0, 0, 0, 0);
        long nonLeapInstant = chronology.getDateTimeMillis(2003, 2, 10, 0, 0, 0, 0);
        long janInstant = chronology.getDateTimeMillis(2004, 1, 10, 0, 0, 0, 0);

        assertTrue(monthField.isLeap(leapInstant));
        assertFalse(monthField.isLeap(nonLeapInstant));
        assertFalse(monthField.isLeap(janInstant));

        assertEquals(1, monthField.getLeapAmount(leapInstant));
        assertEquals(0, monthField.getLeapAmount(nonLeapInstant));

        assertNotNull(monthField.getLeapDurationField());
        assertNotNull(monthField.getRangeDurationField());
    }

    public void testMinMaxValues() throws Throwable {
        assertEquals(1, monthField.getMinimumValue());
        assertEquals(12, monthField.getMaximumValue());
    }

    public void testRoundFloorAndRemainder() throws Throwable {
        long instant = chronology.getDateTimeMillis(2004, 5, 15, 12, 30, 15, 500);
        long rounded = monthField.roundFloor(instant);
        assertEquals(chronology.getDateTimeMillis(2004, 5, 1, 0, 0, 0, 0), rounded);

        long rem = monthField.remainder(instant);
        assertEquals(instant - rounded, rem);
    }
}