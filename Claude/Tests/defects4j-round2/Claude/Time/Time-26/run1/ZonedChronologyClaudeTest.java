package org.joda.time.chrono;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.joda.time.Chronology;
import org.joda.time.DateTimeZone;
import org.joda.time.DurationField;
import org.joda.time.IllegalFieldValueException;

public class ZonedChronologyClaudeTest {

    private Chronology isoUTC;
    private DateTimeZone zoneFixed;

    @Before
    public void setUp() throws Throwable {
        isoUTC = ISOChronology.getInstanceUTC();
        zoneFixed = DateTimeZone.forOffsetHours(2);
    }

    // getInstance: base chronology null -> IllegalArgumentException
    @Test
    public void testGetInstance_nullChronology_throwsIllegalArgumentException() throws Throwable {
        try {
            ZonedChronology.getInstance(null, DateTimeZone.UTC);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // getInstance: zone null -> IllegalArgumentException
    @Test
    public void testGetInstance_nullZone_throwsIllegalArgumentException() throws Throwable {
        try {
            ZonedChronology.getInstance(isoUTC, null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // getInstance: valid args returns instance with given zone
    @Test
    public void testGetInstance_validArgs_returnsInstanceWithCorrectZone() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        assertEquals(zoneFixed, zc.getZone());
    }

    // useTimeArithmetic: null field branch
    @Test
    public void testUseTimeArithmetic_nullField_returnsFalse() throws Throwable {
        assertFalse(ZonedChronology.useTimeArithmetic(null));
    }

    // useTimeArithmetic: unit millis < 12h -> true
    @Test
    public void testUseTimeArithmetic_hoursField_returnsTrue() throws Throwable {
        assertTrue(ZonedChronology.useTimeArithmetic(isoUTC.hours()));
    }

    // useTimeArithmetic: boundary unit millis == 12h -> false (strict < check)
    @Test
    public void testUseTimeArithmetic_halfdaysField_boundaryReturnsFalse() throws Throwable {
        assertFalse(ZonedChronology.useTimeArithmetic(isoUTC.halfdays()));
    }

    // useTimeArithmetic: unit millis > 12h -> false
    @Test
    public void testUseTimeArithmetic_daysField_returnsFalse() throws Throwable {
        assertFalse(ZonedChronology.useTimeArithmetic(isoUTC.days()));
    }

    // getZone: returns zone passed to getInstance
    @Test
    public void testGetZone_returnsZonePassedToGetInstance() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/Los_Angeles");
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zone);
        assertEquals(zone, zc.getZone());
    }

    // withUTC: returns base chronology
    @Test
    public void testWithUTC_returnsBaseChronology() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        assertEquals(isoUTC, zc.withUTC());
    }

    // withZone: same zone reference returns this (reference-equal branch)
    @Test
    public void testWithZone_sameZone_returnsSameInstance() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        assertSame(zc, zc.withZone(zoneFixed));
    }

    // withZone: UTC constant returns base chronology
    @Test
    public void testWithZone_utcZone_returnsBaseChronology() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        assertEquals(isoUTC, zc.withZone(DateTimeZone.UTC));
    }

    // withZone: different zone returns new instance with that zone
    @Test
    public void testWithZone_differentZone_returnsNewInstanceWithThatZone() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        DateTimeZone other = DateTimeZone.forID("America/Los_Angeles");
        Chronology result = zc.withZone(other);
        assertEquals(other, result.getZone());
    }

    // withZone: null zone does not throw and returns a chronology
    @Test
    public void testWithZone_nullZone_returnsNonNullChronology() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        Chronology result = zc.withZone(null);
        assertNotNull(result);
    }

    // getDateTimeMillis(y,m,d,millisOfDay): fixed offset zone conversion
    @Test
    public void testGetDateTimeMillis4Arg_fixedOffsetZone_correctConversion() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        long expected = isoUTC.getDateTimeMillis(2000, 1, 1, 0) - 2L * 3600000L;
        assertEquals(expected, zc.getDateTimeMillis(2000, 1, 1, 0));
    }

    // getDateTimeMillis(y,m,d,h,mi,s,ms): fixed offset zone conversion
    @Test
    public void testGetDateTimeMillis7Arg_fixedOffsetZone_correctConversion() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        long expected = isoUTC.getDateTimeMillis(2000, 1, 1, 10, 15, 30, 250) - 2L * 3600000L;
        assertEquals(expected, zc.getDateTimeMillis(2000, 1, 1, 10, 15, 30, 250));
    }

    // getDateTimeMillis(4 arg): local time falls in DST spring-forward gap -> throws
    @Test
    public void testGetDateTimeMillis4Arg_dstGap_throwsIllegalArgumentException() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.forID("America/Los_Angeles"));
        try {
            zc.getDateTimeMillis(2007, 3, 11, 9000000);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // getDateTimeMillis(7 arg): local time falls in DST spring-forward gap -> throws
    @Test
    public void testGetDateTimeMillis7Arg_dstGap_throwsIllegalArgumentException() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.forID("America/Los_Angeles"));
        try {
            zc.getDateTimeMillis(2007, 3, 11, 2, 30, 0, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // getDateTimeMillis(instant,h,mi,s,ms): preserves local date, replaces time fields
    @Test
    public void testGetDateTimeMillisFromInstant_preservesDateReplacesTime() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        long instant = isoUTC.getDateTimeMillis(2000, 6, 15, 10, 0, 0, 0);
        long result = zc.getDateTimeMillis(instant, 5, 30, 0, 0);
        long expected = isoUTC.getDateTimeMillis(2000, 6, 15, 3, 30, 0, 0);
        assertEquals(expected, result);
    }

    // equals: same base and equal zone -> true
    @Test
    public void testEquals_sameBaseAndZone_returnsTrue() throws Throwable {
        ZonedChronology zc1 = ZonedChronology.getInstance(isoUTC, zoneFixed);
        ZonedChronology zc2 = ZonedChronology.getInstance(isoUTC, DateTimeZone.forOffsetHours(2));
        assertTrue(zc1.equals(zc2));
    }

    // equals: reflexive (this == obj) branch
    @Test
    public void testEquals_sameInstance_returnsTrue() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        assertTrue(zc.equals(zc));
    }

    // equals: different zone -> false
    @Test
    public void testEquals_differentZone_returnsFalse() throws Throwable {
        ZonedChronology zc1 = ZonedChronology.getInstance(isoUTC, zoneFixed);
        ZonedChronology zc2 = ZonedChronology.getInstance(isoUTC, DateTimeZone.forID("America/Los_Angeles"));
        assertFalse(zc1.equals(zc2));
    }

    // equals: not a ZonedChronology instance -> false
    @Test
    public void testEquals_notZonedChronology_returnsFalse() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        assertFalse(zc.equals("not a chronology"));
    }

    // hashCode: matches documented formula
    @Test
    public void testHashCode_matchesFormula() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        int expected = 326565 + zoneFixed.hashCode() * 11 + isoUTC.hashCode() * 7;
        assertEquals(expected, zc.hashCode());
    }

    // toString: format "ZonedChronology[base, zoneId]"
    @Test
    public void testToString_containsZoneIdAndBrackets() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        String str = zc.toString();
        assertTrue(str.startsWith("ZonedChronology["));
        assertTrue(str.indexOf(zoneFixed.getID()) >= 0);
        assertTrue(str.endsWith("]"));
    }

    // assemble/convertField caching: same underlying duration field reused
    @Test
    public void testMonthsDurationField_cachedSameInstance() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        DurationField fromMonthOfYear = zc.monthOfYear().getDurationField();
        DurationField direct = zc.months();
        assertSame(direct, fromMonthOfYear);
    }

    // ZonedDurationField.isPrecise: time field (iTimeField true) delegates to base only
    @Test
    public void testHoursDurationField_isPrecise_true() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.forID("America/Los_Angeles"));
        assertTrue(zc.hours().isPrecise());
    }

    // ZonedDurationField.isPrecise: date field, fixed zone -> precise true
    @Test
    public void testDaysDurationField_isPrecise_fixedZone_true() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        assertTrue(zc.days().isPrecise());
    }

    // ZonedDurationField.isPrecise: date field, DST zone -> precise false
    @Test
    public void testDaysDurationField_isPrecise_dstZone_false() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.forID("America/Los_Angeles"));
        assertFalse(zc.days().isPrecise());
    }

    // ZonedDurationField.add: time field across DST adds exact elapsed millis
    @Test
    public void testHoursAdd_acrossDstTransition_addsExactElapsedMillis() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.forID("America/Los_Angeles"));
        long instant = zc.getDateTimeMillis(2007, 3, 11, 1, 30, 0, 0);
        long result = zc.hours().add(instant, 1);
        assertEquals(instant + 3600000L, result);
    }

    // ZonedDurationField.add: date field across DST preserves wall clock time
    @Test
    public void testDaysAdd_acrossDstTransition_preservesWallClockTime() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.forID("America/Los_Angeles"));
        long instant0 = zc.getDateTimeMillis(2007, 3, 10, 10, 0, 0, 0);
        long result = zc.days().add(instant0, 1);
        long expected = zc.getDateTimeMillis(2007, 3, 11, 10, 0, 0, 0);
        assertEquals(expected, result);
        assertEquals(23L * 3600000L, result - instant0);
    }

    // ZonedDurationField.add: overflow near Long.MAX_VALUE throws ArithmeticException
    @Test
    public void testHoursAdd_overflow_throwsArithmeticException() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        try {
            zc.hours().add(Long.MAX_VALUE, 1);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // ZonedDateTimeField.set: resulting local time falls in DST gap -> throws
    @Test
    public void testHourOfDaySet_dstGap_throwsIllegalFieldValueException() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.forID("America/Los_Angeles"));
        long instant = zc.getDateTimeMillis(2007, 3, 11, 1, 30, 0, 0);
        try {
            zc.hourOfDay().set(instant, 2);
            fail("expected IllegalFieldValueException");
        } catch (IllegalFieldValueException expected) { }
    }

    // ZonedDateTimeField.set/get: valid value round trips correctly
    @Test
    public void testHourOfDaySet_validValue_setsCorrectly() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        long instant = zc.getDateTimeMillis(2000, 1, 1, 10, 0, 0, 0);
        long result = zc.hourOfDay().set(instant, 15);
        assertEquals(15, zc.hourOfDay().get(result));
    }

    // getMaximumValue(instant): leap year February has 29 days
    @Test
    public void testDayOfMonthGetMaximumValue_leapYearFebruary() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.UTC);
        long febLeap = zc.getDateTimeMillis(2000, 2, 15, 0);
        assertEquals(29, zc.dayOfMonth().getMaximumValue(febLeap));
    }

    // getMaximumValue(instant): non-leap year February has 28 days
    @Test
    public void testDayOfMonthGetMaximumValue_nonLeapYearFebruary() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.UTC);
        long febNonLeap = zc.getDateTimeMillis(2001, 2, 15, 0);
        assertEquals(28, zc.dayOfMonth().getMaximumValue(febNonLeap));
    }

    // isLeap: February in a leap year is leap
    @Test
    public void testMonthOfYearIsLeap_leapYear_true() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.UTC);
        long febLeap = zc.getDateTimeMillis(2000, 2, 15, 0);
        assertTrue(zc.monthOfYear().isLeap(febLeap));
    }

    // isLeap: February in a non-leap year is not leap
    @Test
    public void testMonthOfYearIsLeap_nonLeapYear_false() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.UTC);
        long febNonLeap = zc.getDateTimeMillis(2001, 2, 15, 0);
        assertFalse(zc.monthOfYear().isLeap(febNonLeap));
    }

    // getAsText(instant,locale): correct English month name
    @Test
    public void testMonthOfYearGetAsText_returnsEnglishMonthName() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.UTC);
        long instant = zc.getDateTimeMillis(2000, 1, 15, 0);
        assertEquals("January", zc.monthOfYear().getAsText(instant, Locale.ENGLISH));
    }

    // roundFloor: time field, fixed zone, rounds down to start of hour
    @Test
    public void testHourOfDayRoundFloor_fixedZone_roundsDownToHour() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        long instant = zc.getDateTimeMillis(2000, 1, 1, 10, 45, 30, 500);
        long expected = zc.getDateTimeMillis(2000, 1, 1, 10, 0, 0, 0);
        assertEquals(expected, zc.hourOfDay().roundFloor(instant));
    }

    // roundFloor: date field on DST transition day rounds to local midnight
    @Test
    public void testDayOfMonthRoundFloor_dstTransitionDay_startOfLocalDay() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, DateTimeZone.forID("America/Los_Angeles"));
        long instant = zc.getDateTimeMillis(2007, 3, 11, 10, 0, 0, 0);
        long expected = zc.getDateTimeMillis(2007, 3, 11, 0, 0, 0, 0);
        assertEquals(expected, zc.dayOfMonth().roundFloor(instant));
    }

    // getMinimumValue/getMaximumValue: hourOfDay range is 0..23
    @Test
    public void testHourOfDayGetMinimumAndMaximumValue() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        assertEquals(0, zc.hourOfDay().getMinimumValue());
        assertEquals(23, zc.hourOfDay().getMaximumValue());
    }

    // isLenient: standard ISO monthOfYear field is strict, not lenient
    @Test
    public void testMonthOfYearIsLenient_false() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(isoUTC, zoneFixed);
        assertFalse(zc.monthOfYear().isLenient());
    }
}
