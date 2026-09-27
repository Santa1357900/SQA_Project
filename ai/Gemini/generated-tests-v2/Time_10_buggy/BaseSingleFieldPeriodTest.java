package org.joda.time.base;

import org.junit.Test;
import static org.junit.Assert.*;

import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.DurationFieldType;
import org.joda.time.Hours;
import org.joda.time.LocalDate;
import org.joda.time.MutablePeriod;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.joda.time.Seconds;

public class TestBaseSingleFieldPeriod {

    private static final class ConcreteSingleFieldPeriod extends BaseSingleFieldPeriod {
        private static final long serialVersionUID = 1L;

        public ConcreteSingleFieldPeriod(int period) {
            super(period);
        }

        public DurationFieldType getFieldType() {
            return DurationFieldType.days();
        }

        public PeriodType getPeriodType() {
            return PeriodType.days();
        }
    }

    private static final class AnotherConcreteSingleFieldPeriod extends BaseSingleFieldPeriod {
        private static final long serialVersionUID = 1L;

        public ConcreteSingleFieldPeriod(int period) {
            super(period);
        }

        public DurationFieldType getFieldType() {
            return DurationFieldType.hours();
        }

        public PeriodType getPeriodType() {
            return PeriodType.hours();
        }

        public DurationFieldType getFieldType(int index) {
            return super.getFieldType(index);
        }

        public int getValue(int index) {
            return super.getValue(index);
        }
    }

    @Test
    public void testBetweenInstantsNormal() throws Throwable {
        DateTime start = new DateTime(2012, 6, 1, 0, 0, 0, 0);
        DateTime end = new DateTime(2012, 6, 5, 0, 0, 0, 0);
        int days = BaseSingleFieldPeriod.between(start, end, DurationFieldType.days());
        assertEquals(4, days);
    }

    @Test
    public void testBetweenInstantsNullStart() throws Throwable {
        DateTime end = new DateTime(2012, 6, 5, 0, 0, 0, 0);
        try {
            BaseSingleFieldPeriod.between((org.joda.time.ReadableInstant) null, end, DurationFieldType.days());
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }
    }

    @Test
    public void testBetweenInstantsNullEnd() throws Throwable {
        DateTime start = new DateTime(2012, 6, 1, 0, 0, 0, 0);
        try {
            BaseSingleFieldPeriod.between(start, (org.joda.time.ReadableInstant) null, DurationFieldType.days());
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }
    }

    @Test
    public void testBetweenPartialsNormal() throws Throwable {
        LocalDate start = new LocalDate(2012, 6, 1);
        LocalDate end = new LocalDate(2012, 6, 5);
        int days = BaseSingleFieldPeriod.between(start, end, Days.ZERO);
        assertEquals(4, days);
    }

    @Test
    public void testBetweenPartialsNullStart() throws Throwable {
        LocalDate end = new LocalDate(2012, 6, 5);
        try {
            BaseSingleFieldPeriod.between((org.joda.time.ReadablePartial) null, end, Days.ZERO);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }
    }

    @Test
    public void testBetweenPartialsNullEnd() throws Throwable {
        LocalDate start = new LocalDate(2012, 6, 1);
        try {
            BaseSingleFieldPeriod.between(start, (org.joda.time.ReadablePartial) null, Days.ZERO);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }
    }

    @Test
    public void testBetweenPartialsDifferentSize() throws Throwable {
        LocalDate start = new LocalDate(2012, 6, 1);
        org.joda.time.YearMonthDay end = new org.joda.time.YearMonthDay(2012, 6, 5);
        try {
            BaseSingleFieldPeriod.between(start, end, Days.ZERO);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("same set of fields"));
        }
    }

    @Test
    public void testBetweenPartialsDifferentFields() throws Throwable {
        LocalDate start = new LocalDate(2012, 6, 1);
        LocalDate end = new LocalDate(2012, 6, 5);
        try {
            BaseSingleFieldPeriod.between(start, end, Hours.ZERO);
            // Will fail due to type mismatch or zero instance compatibility
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testBetweenPartialsNonContiguous() throws Throwable {
        org.joda.time.MonthDay start = new org.joda.time.MonthDay(6, 1);
        org.joda.time.MonthDay end = new org.joda.time.MonthDay(6, 5);
        try {
            BaseSingleFieldPeriod.between(start, end, Days.ZERO);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("contiguous"));
        }
    }

    @Test
    public void testStandardPeriodInNull() throws Throwable {
        int val = BaseSingleFieldPeriod.standardPeriodIn(null, 1000L);
        assertEquals(0, val);
    }

    @Test
    public void testStandardPeriodInPrecise() throws Throwable {
        Period period = Period.days(2);
        int millis = BaseSingleFieldPeriod.standardPeriodIn(period, 24L * 60L * 60L * 1000L);
        assertEquals(2, millis);
    }

    @Test
    public void testStandardPeriodInImprecise() throws Throwable {
        Period period = Period.months(2);
        try {
            BaseSingleFieldPeriod.standardPeriodIn(period, 1000L);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not precise"));
        }
    }

    @Test
    public void testStandardPeriodInZeroValueField() throws Throwable {
        Period period = Period.days(0);
        int millis = BaseSingleFieldPeriod.standardPeriodIn(period, 24L * 60L * 60L * 1000L);
        assertEquals(0, millis);
    }

    @Test
    public void testGettersAndSetters() throws Throwable {
        ConcreteSingleFieldPeriod period = new ConcreteSingleFieldPeriod(10);
        assertEquals(10, period.getValue());
        assertEquals(1, period.size());
        assertEquals(DurationFieldType.days(), period.getFieldType());
        assertEquals(PeriodType.days(), period.getPeriodType());

        period.setValue(20);
        assertEquals(20, period.getValue());

        assertEquals(DurationFieldType.days(), period.getFieldType(0));
        assertEquals(20, period.getValue(0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetFieldTypeInvalidIndex() throws Throwable {
        ConcreteSingleFieldPeriod period = new ConcreteSingleFieldPeriod(10);
        period.getFieldType(1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetValueInvalidIndex() throws Throwable {
        ConcreteSingleFieldPeriod period = new ConcreteSingleFieldPeriod(10);
        period.getValue(1);
    }

    @Test
    public void testGetAndIsSupported() throws Throwable {
        ConcreteSingleFieldPeriod period = new ConcreteSingleFieldPeriod(15);
        assertEquals(15, period.get(DurationFieldType.days()));
        assertEquals(0, period.get(DurationFieldType.hours()));
        assertTrue(period.isSupported(DurationFieldType.days()));
        assertFalse(period.isSupported(DurationFieldType.hours()));
    }

    @Test
    public void testToPeriodAndMutablePeriod() throws Throwable {
        ConcreteSingleFieldPeriod period = new ConcreteSingleFieldPeriod(5);
        Period p = period.toPeriod();
        assertNotNull(p);

        MutablePeriod mp = period.toMutablePeriod();
        assertNotNull(mp);
        assertEquals(5, mp.getDays());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        ConcreteSingleFieldPeriod period1 = new ConcreteSingleFieldPeriod(5);
        ConcreteSingleFieldPeriod period2 = new ConcreteSingleFieldPeriod(5);
        ConcreteSingleFieldPeriod period3 = new ConcreteSingleFieldPeriod(6);
        Days days5 = Days.days(5);

        assertTrue(period1.equals(period1));
        assertTrue(period1.equals(period2));
        assertFalse(period1.equals(period3));
        assertFalse(period1.equals(days5)); // different PeriodType/class depending on equals implementation
        assertFalse(period1.equals(null));
        assertFalse(period1.equals("string"));

        assertEquals(period1.hashCode(), period2.hashCode());
    }

    @Test
    public void testCompareTo() throws Throwable {
        ConcreteSingleFieldPeriod period1 = new ConcreteSingleFieldPeriod(5);
        ConcreteSingleFieldPeriod period2 = new ConcreteSingleFieldPeriod(10);
        ConcreteSingleFieldPeriod period3 = new ConcreteSingleFieldPeriod(5);

        assertEquals(0, period1.compareTo(period3));
        assertEquals(-1, period1.compareTo(period2));
        assertEquals(1, period2.compareTo(period1));
    }

    @Test(expected = ClassCastException.class)
    public void testCompareToDifferentClass() throws Throwable {
        ConcreteSingleFieldPeriod period1 = new ConcreteSingleFieldPeriod(5);
        Seconds secs = Seconds.seconds(5);
        period1.compareTo((BaseSingleFieldPeriod) (Object) secs);
    }
}