package org.joda.time;

import org.junit.Test;
import static org.junit.Assert.*;

import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.ISOPeriodFormat;

public class PeriodTest {

    @Test
    public void testConstantsAndParsing() throws Throwable {
        assertNotNull(Period.ZERO);
        assertEquals(0, Period.ZERO.getMillis());

        Period p1 = Period.parse("P1Y2M3DT4H5M6S");
        assertNotNull(p1);
        assertEquals(1, p1.getYears());
        assertEquals(2, p1.getMonths());
        assertEquals(3, p1.getDays());
        assertEquals(4, p1.getHours());
        assertEquals(5, p1.getMinutes());
        assertEquals(6, p1.getSeconds());

        PeriodFormatter formatter = ISOPeriodFormat.standard();
        Period p2 = Period.parse("P2H", formatter);
        assertNotNull(p2);
        assertEquals(2, p2.getHours());
    }

    @Test
    public void testFactoryMethods() throws Throwable {
        assertEquals(5, Period.years(5).getYears());
        assertEquals(4, Period.months(4).getMonths());
        assertEquals(3, Period.weeks(3).getWeeks());
        assertEquals(2, Period.days(2).getDays());
        assertEquals(1, Period.hours(1).getHours());
        assertEquals(10, Period.minutes(10).getMinutes());
        assertEquals(20, Period.seconds(20).getSeconds());
        assertEquals(500, Period.millis(500).getMillis());
    }

    @Test
    public void testFieldDifference() throws Throwable {
        LocalDate start = new LocalDate(2005, 6, 9);
        LocalDate end = new LocalDate(2007, 4, 12);
        Period diff = Period.fieldDifference(start, end);
        assertNotNull(diff);
        assertEquals(1, diff.getYears());
        assertEquals(-2, diff.getMonths());
        assertEquals(3, diff.getDays());

        try {
            Period.fieldDifference(null, end);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }

        try {
            Period.fieldDifference(start, null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }

        LocalTime startTime = new LocalTime(10, 0, 0);
        LocalDate startDate = new LocalDate(2005, 6, 9);
        try {
            Period.fieldDifference(startDate, startTime);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("same set of fields"));
        }

        LocalDate startOverlap = new LocalDate(2005, 6, 9);
        // Using same partials twice or partial with different size
        try {
            Period.fieldDifference(startDate, new YearMonthDay(2007, 4));
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("same set of fields"));
        }
    }

    @Test
    public void testConstructors() throws Throwable {
        Period pEmpty = new Period();
        assertEquals(0, pEmpty.size());

        Period pTime = new Period(1, 2, 3, 4);
        assertEquals(1, pTime.getHours());
        assertEquals(2, pTime.getMinutes());
        assertEquals(3, pTime.getSeconds());
        assertEquals(4, pTime.getMillis());

        Period pFull = new Period(1, 2, 3, 4, 5, 6, 7, 8);
        assertEquals(1, pFull.getYears());
        assertEquals(2, pFull.getMonths());
        assertEquals(3, pFull.getWeeks());
        assertEquals(4, pFull.getDays());
        assertEquals(5, pFull.getHours());
        assertEquals(6, pFull.getMinutes());
        assertEquals(7, pFull.getSeconds());
        assertEquals(8, pFull.getMillis());

        Period pFullWithType = new Period(1, 2, 3, 4, 5, 6, 7, 8, PeriodType.standard());
        assertEquals(1, pFullWithType.getYears());

        Period pDuration = new Period(1500L);
        assertEquals(1, pDuration.getSeconds());
        assertEquals(500, pDuration.getMillis());

        Period pDurationType = new Period(1500L, PeriodType.standard());
        assertNotNull(pDurationType);

        Period pDurationChrono = new Period(1500L, ISOChronology.getInstanceUTC());
        assertNotNull(pDurationChrono);

        Period pDurationTypeChrono = new Period(1500L, PeriodType.standard(), ISOChronology.getInstanceUTC());
        assertNotNull(pDurationTypeChrono);

        Period pIntervalMillis = new Period(0L, 5000L);
        assertNotNull(pIntervalMillis);

        Period pIntervalType = new Period(0L, 5000L, PeriodType.standard());
        assertNotNull(pIntervalType);

        Period pIntervalChrono = new Period(0L, 5000L, ISOChronology.getInstanceUTC());
        assertNotNull(pIntervalChrono);

        Period pIntervalFull = new Period(0L, 5000L, PeriodType.standard(), ISOChronology.getInstanceUTC());
        assertNotNull(pIntervalFull);

        Instant startInstant = new Instant(0L);
        Instant endInstant = new Instant(5000L);
        Period pInstants = new Period(startInstant, endInstant);
        assertNotNull(pInstants);

        Period pInstantsType = new Period(startInstant, endInstant, PeriodType.standard());
        assertNotNull(pInstantsType);

        LocalDate startDate = new LocalDate(2010, 1, 1);
        LocalDate endDate = new LocalDate(2010, 1, 5);
        Period pPartials = new Period(startDate, endDate);
        assertNotNull(pPartials);

        Period pPartialsType = new Period(startDate, endDate, PeriodType.standard());
        assertNotNull(pPartialsType);

        Duration duration = new Duration(1000L);
        Period pInstDur = new Period(startInstant, duration);
        assertNotNull(pInstDur);

        Period pInstDurType = new Period(startInstant, duration, PeriodType.standard());
        assertNotNull(pInstDurType);

        Period pDurInst = new Period(duration, endInstant);
        assertNotNull(pDurInst);

        Period pDurInstType = new Period(duration, endInstant, PeriodType.standard());
        assertNotNull(pDurInstType);

        Period pObjString = new Period("P1D");
        assertEquals(1, pObjString.getDays());

        Period pObjStringType = new Period("P1D", PeriodType.standard());
        assertEquals(1, pObjStringType.getDays());

        Period pObjStringChrono = new Period("P1D", ISOChronology.getInstanceUTC());
        assertEquals(1, pObjStringChrono.getDays());

        Period pObjStringFull = new Period("P1D", PeriodType.standard(), ISOChronology.getInstanceUTC());
        assertEquals(1, pObjStringFull.getDays());
    }

    @Test
    public void testGetters() throws Throwable {
        Period p = new Period(1, 2, 3, 4, 5, 6, 7, 8);
        assertEquals(1, p.getYears());
        assertEquals(2, p.getMonths());
        assertEquals(3, p.getWeeks());
        assertEquals(4, p.getDays());
        assertEquals(5, p.getHours());
        assertEquals(6, p.getMinutes());
        assertEquals(7, p.getSeconds());
        assertEquals(8, p.getMillis());
        assertSame(p, p.toPeriod());
    }

    @Test
    public void testWithMethods() throws Throwable {
        Period p = Period.days(1);
        Period p2 = p.withPeriodType(PeriodType.standard());
        assertSame(p, p2);

        Period p3 = p.withPeriodType(PeriodType.days());
        assertNotNull(p3);

        Period p4 = p.withFields(Period.hours(5));
        assertEquals(5, p4.getHours());
        assertEquals(1, p4.getDays());

        assertSame(p, p.withFields(null));

        Period p5 = p.withField(DurationFieldType.DAYS_TYPE, 5);
        assertEquals(5, p5.getDays());

        try {
            p.withField(null, 5);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }

        Period p6 = p.withFieldAdded(DurationFieldType.DAYS_TYPE, 3);
        assertEquals(4, p6.getDays());

        try {
            p.withFieldAdded(null, 3);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }

        assertSame(p, p.withFieldAdded(DurationFieldType.DAYS_TYPE, 0));

        assertEquals(2, p.withYears(2).getYears());
        assertEquals(2, p.withMonths(2).getMonths());
        assertEquals(2, p.withWeeks(2).getWeeks());
        assertEquals(2, p.withDays(2).getDays());
        assertEquals(2, p.withHours(2).getHours());
        assertEquals(2, p.withMinutes(2).getMinutes());
        assertEquals(2, p.withSeconds(2).getSeconds());
        assertEquals(2, p.withMillis(2).getMillis());
    }

    @Test
    public void testPlusAndMinus() throws Throwable {
        Period p = Period.days(2);
        Period added = p.plus(Period.days(3));
        assertEquals(5, added.getDays());
        assertSame(p, p.plus(null));

        assertEquals(3, p.plusYears(1).getYears());
        assertEquals(2, p.plusYears(0).getYears());
        assertEquals(3, p.plusMonths(1).getMonths());
        assertEquals(2, p.plusMonths(0).getMonths());
        assertEquals(3, p.plusWeeks(1).getWeeks());
        assertEquals(2, p.plusWeeks(0).getWeeks());
        assertEquals(3, p.plusDays(1).getDays());
        assertEquals(2, p.plusDays(0).getDays());
        assertEquals(3, p.plusHours(1).getHours());
        assertEquals(2, p.plusHours(0).getHours());
        assertEquals(3, p.plusMinutes(1).getMinutes());
        assertEquals(2, p.plusMinutes(0).getMinutes());
        assertEquals(3, p.plusSeconds(1).getSeconds());
        assertEquals(2, p.plusSeconds(0).getSeconds());
        assertEquals(3, p.plusMillis(1).getMillis());
        assertEquals(2, p.plusMillis(0).getMillis());

        Period sub = p.minus(Period.days(1));
        assertEquals(1, sub.getDays());
        assertSame(p, p.minus(null));

        assertEquals(1, p.minusYears(1).getYears());
        assertEquals(1, p.minusMonths(1).getMonths());
        assertEquals(1, p.minusWeeks(1).getWeeks());
        assertEquals(1, p.minusDays(1).getDays());
        assertEquals(1, p.minusHours(1).getHours());
        assertEquals(1, p.minusMinutes(1).getMinutes());
        assertEquals(1, p.minusSeconds(1).getSeconds());
        assertEquals(1, p.minusMillis(1).getMillis());
    }

    @Test
    public void testMultipliedAndNegated() throws Throwable {
        Period p = Period.days(2);
        assertSame(p, p.multipliedBy(1));
        assertSame(Period.ZERO, Period.ZERO.multipliedBy(5));

        Period mult = p.multipliedBy(3);
        assertEquals(6, mult.getDays());

        Period neg = p.negated();
        assertEquals(-2, neg.getDays());
    }

    @Test
    public void testConversionsAndStandard() throws Throwable {
        Period p = Period.days(2).withHours(3);
        
        Weeks weeks = p.toStandardWeeks();
        assertNotNull(weeks);

        Days days = p.toStandardDays();
        assertNotNull(days);

        Hours hours = p.toStandardHours();
        assertNotNull(hours);

        Minutes minutes = p.toStandardMinutes();
        assertNotNull(minutes);

        Seconds seconds = p.toStandardSeconds();
        assertNotNull(seconds);

        Duration duration = p.toStandardDuration();
        assertNotNull(duration);

        Period withYears = Period.years(1);
        try {
            withYears.toStandardWeeks();
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("contains years"));
        }

        try {
            withYears.toStandardDays();
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("contains years"));
        }

        try {
            withYears.toStandardHours();
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("contains years"));
        }

        try {
            withYears.toStandardMinutes();
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("contains years"));
        }

        try {
            withYears.toStandardSeconds();
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("contains years"));
        }

        try {
            withYears.toStandardDuration();
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("contains years"));
        }

        Period norm = p.normalizedStandard();
        assertNotNull(norm);

        Period normType = p.normalizedStandard(PeriodType.standard());
        assertNotNull(normType);

        Period normYearsMonths = Period.years(1).withMonths(14);
        Period normalizedYM = normYearsMonths.normalizedStandard();
        assertEquals(2, normalizedYM.getYears());
        assertEquals(2, normalizedYM.getMonths());
    }
}