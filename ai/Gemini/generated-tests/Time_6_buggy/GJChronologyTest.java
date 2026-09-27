package org.joda.time.chrono;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Locale;
import org.joda.time.Chronology;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeZone;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.Instant;
import org.joda.time.LocalDate;
import org.joda.time.MonthDay;
import org.joda.time.YearMonth;

public class GJChronology_Test {

    @Test
    public void testGetInstance_Defaults() throws Throwable {
        GJChronology chrono = GJChronology.getInstance();
        assertNotNull(chrono);
        assertEquals(DateTimeZone.getDefault(), chrono.getZone());
        assertEquals(4, chrono.getMinimumDaysInFirstWeek());
        assertNotNull(chrono.getGregorianCutover());
    }

    @Test
    public void testGetInstance_Zone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("Europe/Paris");
        GJChronology chrono = GJChronology.getInstance(zone);
        assertNotNull(chrono);
        assertEquals(zone, chrono.getZone());
    }

    @Test
    public void testGetInstance_ZoneAndInstant() throws Throwable {
        DateTimeZone zone = DateTimeZone.UTC;
        Instant cutover = new Instant(0L);
        GJChronology chrono = GJChronology.getInstance(zone, cutover);
        assertNotNull(chrono);
        assertEquals(zone, chrono.getZone());
        assertEquals(cutover, chrono.getGregorianCutover());
    }

    @Test
    public void testGetInstance_NullArguments() throws Throwable {
        GJChronology chrono = GJChronology.getInstance(null, null, 4);
        assertNotNull(chrono);
        assertEquals(DateTimeZone.getDefault(), chrono.getZone());
        assertEquals(GJChronology.DEFAULT_CUTOVER, chrono.getGregorianCutover());
    }

    @Test
    public void testGetInstanceUTC() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        assertNotNull(chrono);
        assertEquals(DateTimeZone.UTC, chrono.getZone());
    }

    @Test
    public void testGetInstance_MillisAndMinDays() throws Throwable {
        long cutoverMillis = GJChronology.DEFAULT_CUTOVER.getMillis();
        GJChronology chrono1 = GJChronology.getInstance(DateTimeZone.UTC, cutoverMillis, 4);
        GJChronology chrono2 = GJChronology.getInstance(DateTimeZone.UTC, cutoverMillis + 1000L, 4);
        assertNotNull(chrono1);
        assertNotNull(chrono2);
    }

    @Test
    public void testWithUTCAndZone() throws Throwable {
        GJChronology chrono = GJChronology.getInstance();
        Chronology utcChrono = chrono.withUTC();
        assertNotNull(utcChrono);
        assertEquals(DateTimeZone.UTC, utcChrono.getZone());

        Chronology sameZone = chrono.withZone(DateTimeZone.getDefault());
        assertSame(chrono, sameZone);

        Chronology parisChrono = chrono.withZone(DateTimeZone.forID("Europe/Paris"));
        assertNotNull(parisChrono);
        assertEquals(DateTimeZone.forID("Europe/Paris"), parisChrono.getZone());

        Chronology defaultZoneChrono = chrono.withZone(null);
        assertNotNull(defaultZoneChrono);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        GJChronology chrono1 = GJChronology.getInstance();
        GJChronology chrono2 = GJChronology.getInstance();
        GJChronology chronoUTC = GJChronology.getInstanceUTC();

        assertTrue(chrono1.equals(chrono1));
        assertTrue(chrono1.equals(chrono2));
        assertFalse(chrono1.equals(chronoUTC));
        assertFalse(chrono1.equals(new Object()));

        assertEquals(chrono1.hashCode(), chrono2.hashCode());
    }

    @Test
    public void testToString() throws Throwable {
        GJChronology chrono1 = GJChronology.getInstanceUTC();
        assertNotNull(chrono1.toString());

        GJChronology chrono2 = GJChronology.getInstance(DateTimeZone.UTC, new Instant(1000L), 5);
        assertNotNull(chrono2.toString());

        GJChronology chrono3 = GJChronology.getInstance(DateTimeZone.UTC, new Instant(0L), 4);
        assertNotNull(chrono3.toString());
    }

    @Test
    public void testGetDateTimeMillis() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        
        // Gregorian date
        long millisGregorian = chrono.getDateTimeMillis(2007, 6, 15, 12, 0, 0, 0);
        assertTrue(millisGregorian > chrono.getGregorianCutover().getMillis());

        // Julian date
        long millisJulian = chrono.getDateTimeMillis(1500, 1, 1, 12, 0, 0, 0);
        assertTrue(millisJulian < chrono.getGregorianCutover().getMillis());

        // Simple getDateTimeMillis(int, int, int, int)
        long simpleMillis = chrono.getDateTimeMillis(2007, 6, 15, 3600000);
        assertTrue(simpleMillis > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDateTimeMillis_InGap() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        // October 5, 1582 to October 14, 1582 do not exist in GJ cutover
        chrono.getDateTimeMillis(1582, 10, 5, 0, 0, 0, 0);
    }

    @Test(expected = IllegalFieldValueException.class)
    public void testGetDateTimeMillis_InvalidFebruary29() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        // 1583 is not a leap year in Gregorian
        chrono.getDateTimeMillis(1583, 2, 29, 0, 0, 0, 0);
    }

    @Test
    public void testJulianGregorianConversions() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long now = DateTimeUtils.currentTimeMillis();
        long byYear = chrono.julianToGregorianByYear(now);
        long backByYear = chrono.gregorianToJulianByYear(byYear);
        
        long byWeek = chrono.julianToGregorianByWeekyear(now);
        long backByWeek = chrono.gregorianToJulianByWeekyear(byWeek);
        
        assertTrue(byYear != 0);
        assertTrue(backByYear != 0);
        assertTrue(byWeek != 0);
        assertTrue(backByWeek != 0);
    }

    @Test
    public void testCutoverFields_GetAndSet() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long cutover = chrono.getGregorianCutover().getMillis();
        
        long beforeCutover = cutover - 86400000L * 10;
        long afterCutover = cutover + 86400000L * 10;

        DateTimeField dayOfWeek = chrono.dayOfWeek();
        assertNotNull(dayOfWeek);
        assertTrue(dayOfWeek.get(beforeCutover) > 0);
        assertTrue(dayOfWeek.get(afterCutover) > 0);

        assertNotNull(dayOfWeek.getAsText(beforeCutover, Locale.ENGLISH));
        assertNotNull(dayOfWeek.getAsText(afterCutover, Locale.ENGLISH));
        assertNotNull(dayOfWeek.getAsText(1, Locale.ENGLISH));

        assertNotNull(dayOfWeek.getAsShortText(beforeCutover, Locale.ENGLISH));
        assertNotNull(dayOfWeek.getAsShortText(afterCutover, Locale.ENGLISH));
        assertNotNull(dayOfWeek.getAsShortText(1, Locale.ENGLISH));

        assertFalse(dayOfWeek.isLenient());
        assertNotNull(dayOfWeek.getDurationField());
        assertNotNull(dayOfWeek.getRangeDurationField());

        assertTrue(dayOfWeek.isLeap(beforeCutover) == true || dayOfWeek.isLeap(beforeCutover) == false);
        assertTrue(dayOfWeek.getLeapAmount(beforeCutover) >= 0);
        assertNotNull(dayOfWeek.getLeapDurationField());

        assertTrue(dayOfWeek.getMinimumValue() > 0);
        assertTrue(dayOfWeek.getMinimumValue(beforeCutover) > 0);
        assertTrue(dayOfWeek.getMinimumValue(new LocalDate(2007, 1, 1, chrono)) > 0);
        assertTrue(dayOfWeek.getMinimumValue(new LocalDate(2007, 1, 1, chrono), new int[]{2007, 1, 1}) > 0);

        assertTrue(dayOfWeek.getMaximumValue() > 0);
        assertTrue(dayOfWeek.getMaximumValue(beforeCutover) > 0);
        assertTrue(dayOfWeek.getMaximumValue(afterCutover) > 0);
        assertTrue(dayOfWeek.getMaximumValue(new LocalDate(2007, 1, 1, chrono)) > 0);
        assertTrue(dayOfWeek.getMaximumValue(new LocalDate(2007, 1, 1, chrono), new int[]{2007, 1, 1}) > 0);

        assertTrue(dayOfWeek.getMaximumTextLength(Locale.ENGLISH) > 0);
        assertTrue(dayOfWeek.getMaximumShortTextLength(Locale.ENGLISH) > 0);

        long roundedFloor = dayOfWeek.roundFloor(beforeCutover);
        long roundedCeil = dayOfWeek.roundCeiling(beforeCutover);
        long roundedFloorAfter = dayOfWeek.roundFloor(afterCutover);
        long roundedCeilAfter = dayOfWeek.roundCeiling(afterCutover);
        assertTrue(roundedFloor <= beforeCutover);
        assertTrue(roundedCeil >= beforeCutover);
        assertTrue(roundedFloorAfter <= afterCutover);
        assertTrue(roundedCeilAfter >= afterCutover);

        long setBefore = dayOfWeek.set(beforeCutover, 3);
        long setAfter = dayOfWeek.set(afterCutover, 3);
        assertTrue(setBefore != 0);
        assertTrue(setAfter != 0);

        long setTextBefore = dayOfWeek.set(beforeCutover, "3", Locale.ENGLISH);
        long setTextAfter = dayOfWeek.set(afterCutover, "3", Locale.ENGLISH);
        assertTrue(setTextBefore != 0);
        assertTrue(setTextAfter != 0);

        long addInstantInt = dayOfWeek.add(beforeCutover, 2);
        long addInstantLong = dayOfWeek.add(beforeCutover, 2L);
        assertTrue(addInstantInt != 0);
        assertTrue(addInstantLong != 0);

        int diff = dayOfWeek.getDifference(afterCutover, beforeCutover);
        long diffLong = dayOfWeek.getDifferenceAsLong(afterCutover, beforeCutover);
        assertTrue(diff != 0 || diff == 0);
        assertTrue(diffLong != 0 || diffLong == 0);
    }

    @Test
    public void testImpreciseCutoverField_Operations() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long cutover = chrono.getGregorianCutover().getMillis();
        long beforeCutover = cutover - 86400000L * 400;
        long afterCutover = cutover + 86400000L * 400;

        DateTimeField yearField = chrono.year();
        assertNotNull(yearField);

        long addedInt = yearField.add(beforeCutover, 1);
        long addedLong = yearField.add(beforeCutover, 1L);
        long addedAfterInt = yearField.add(afterCutover, 1);
        long addedAfterLong = yearField.add(afterCutover, 1L);
        assertTrue(addedInt != 0);
        assertTrue(addedLong != 0);
        assertTrue(addedAfterInt != 0);
        assertTrue(addedAfterLong != 0);

        int diff1 = yearField.getDifference(afterCutover, beforeCutover);
        int diff2 = yearField.getDifference(beforeCutover, afterCutover);
        int diff3 = yearField.getDifference(beforeCutover, beforeCutover - 86400000L);
        int diff4 = yearField.getDifference(afterCutover, afterCutover + 86400000L);
        assertTrue(diff1 != 0);
        assertTrue(diff2 != 0);

        long diffAsLong1 = yearField.getDifferenceAsLong(afterCutover, beforeCutover);
        long diffAsLong2 = yearField.getDifferenceAsLong(beforeCutover, afterCutover);
        long diffAsLong3 = yearField.getDifferenceAsLong(beforeCutover, beforeCutover - 86400000L);
        long diffAsLong4 = yearField.getDifferenceAsLong(afterCutover, afterCutover + 86400000L);
        assertTrue(diffAsLong1 != 0);
        assertTrue(diffAsLong2 != 0);

        assertTrue(yearField.getMinimumValue(beforeCutover) != 0);
        assertTrue(yearField.getMinimumValue(afterCutover) != 0);
        assertTrue(yearField.getMaximumValue(beforeCutover) != 0);
        assertTrue(yearField.getMaximumValue(afterCutover) != 0);
    }

    @Test
    public void testLinkedDurationField_Operations() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long cutover = chrono.getGregorianCutover().getMillis();
        long beforeCutover = cutover - 86400000L * 400;

        org.joda.time.DurationField yearsDuration = chrono.years();
        assertNotNull(yearsDuration);

        long addedInt = yearsDuration.add(beforeCutover, 1);
        long addedLong = yearsDuration.add(beforeCutover, 1L);
        assertTrue(addedInt != 0);
        assertTrue(addedLong != 0);

        int diff = yearsDuration.getDifference(cutover, beforeCutover);
        long diffLong = yearsDuration.getDifferenceAsLong(cutover, beforeCutover);
        assertTrue(diff != 0);
        assertTrue(diffLong != 0);
    }

    @Test
    public void testPartialAdd() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        LocalDate date = new LocalDate(2004, 2, 29, chrono);
        int[] values = new int[] {2004, 2, 29};
        int[] result = chrono.monthOfYear().add(date, 0, values, 0);
        assertNotNull(result);

        int[] resultAdd = chrono.monthOfYear().add(date, 1, values, 12);
        assertNotNull(resultAdd);
    }
}