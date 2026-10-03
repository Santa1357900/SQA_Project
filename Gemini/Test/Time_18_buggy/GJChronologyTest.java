package org.joda.time.chrono;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Locale;
import org.joda.time.Chronology;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.MutableDateTime;
import org.joda.time.DateTime;

public class GJChronology_Test {

    @Test
    public void testGetInstance_Defaults() throws Throwable {
        GJChronology chrono = GJChronology.getInstance();
        assertNotNull(chrono);
        assertNotNull(chrono.getZone());
        assertNotNull(chrono.getGregorianCutover());
    }

    @Test
    public void testGetInstance_WithZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("UTC");
        GJChronology chrono = GJChronology.getInstance(zone);
        assertNotNull(chrono);
        assertEquals(zone, chrono.getZone());
    }

    @Test
    public void testGetInstance_WithNullZone() throws Throwable {
        GJChronology chrono = GJChronology.getInstance(null);
        assertNotNull(chrono);
    }

    @Test
    public void testGetInstanceUTC() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        assertNotNull(chrono);
        assertEquals(DateTimeZone.UTC, chrono.getZone());
    }

    @Test
    public void testGetInstance_WithCutoverInstant() throws Throwable {
        Instant cutover = new Instant(0L);
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.UTC, cutover);
        assertNotNull(chrono);
        assertEquals(cutover, chrono.getGregorianCutover());
    }

    @Test
    public void testGetInstance_WithNullCutover() throws Throwable {
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.UTC, null, 4);
        assertNotNull(chrono);
        assertEquals(GJChronology.DEFAULT_CUTOVER, chrono.getGregorianCutover());
    }

    @Test
    public void testGetInstance_WithMillisAndMinDays() throws Throwable {
        long cutoverMillis = GJChronology.DEFAULT_CUTOVER.getMillis();
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.UTC, cutoverMillis, 4);
        assertNotNull(chrono);
        
        GJChronology chrono2 = GJChronology.getInstance(DateTimeZone.UTC, cutoverMillis + 1000L, 4);
        assertNotNull(chrono2);
    }

    @Test
    public void testWithUTC() throws Throwable {
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.forID("America/New_York"));
        Chronology utcChrono = chrono.withUTC();
        assertNotNull(utcChrono);
        assertEquals(DateTimeZone.UTC, utcChrono.getZone());
    }

    @Test
    public void testWithZone() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        DateTimeZone zone = DateTimeZone.forID("Europe/London");
        Chronology newChrono = chrono.withZone(zone);
        assertNotNull(newChrono);
        assertEquals(zone, newChrono.getZone());
        
        // Same zone
        assertSame(chrono, chrono.withZone(DateTimeZone.UTC));
        // Null zone defaults
        assertNotNull(chrono.withZone(null));
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

        // Simple getDateTimeMillis without time of day
        long millisSimple = chrono.getDateTimeMillis(2007, 6, 15, 0);
        assertTrue(millisSimple > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDateTimeMillis_InGap() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        // 1582 cutover gap: October 5 to October 14, 1582 do not exist in GJ
        chrono.getDateTimeMillis(1582, 10, 10, 12, 0, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDateTimeMillisSimple_InGap() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        chrono.getDateTimeMillis(1582, 10, 10, 0);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        GJChronology chrono1 = GJChronology.getInstanceUTC();
        GJChronology chrono2 = GJChronology.getInstanceUTC();
        GJChronology chrono3 = GJChronology.getInstance(DateTimeZone.forID("Europe/Paris"));

        assertTrue(chrono1.equals(chrono2));
        assertFalse(chrono1.equals(chrono3));
        assertFalse(chrono1.equals(null));
        assertFalse(chrono1.equals("NotAChronology"));

        assertEquals(chrono1.hashCode(), chrono2.hashCode());
    }

    @Test
    public void testToString() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        String str = chrono.toString();
        assertNotNull(str);
        assertTrue(str.startsWith("GJChronology"));

        GJChronology custom = GJChronology.getInstance(DateTimeZone.UTC, new Instant(1000L), 3);
        assertNotNull(custom.toString());
    }

    @Test
    public void testConversationsAndFields() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long instant = chrono.getDateTimeMillis(2007, 6, 15, 12, 0, 0, 0);
        
        long julianConv = chrono.gregorianToJulianByYear(instant);
        long gregConv = chrono.julianToGregorianByYear(julianConv);
        assertTrue(gregConv != 0);

        long julianWeekConv = chrono.gregorianToJulianByWeekyear(instant);
        long gregWeekConv = chrono.julianToGregorianByWeekyear(julianWeekConv);
        assertTrue(gregWeekConv != 0);

        assertNotNull(chrono.getGregorianCutover());
        assertEquals(4, chrono.getMinimumDaysInFirstWeek());
    }

    @Test
    public void testFieldOperations_CutoverAndImprecise() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        
        // Test Year field set/add across cutover
        long julianDate = chrono.getDateTimeMillis(1500, 1, 1, 0, 0, 0, 0);
        long gregDate = chrono.getDateTimeMillis(2000, 1, 1, 0, 0, 0, 0);

        int yearJulian = chrono.year().get(julianDate);
        assertEquals(1500, yearJulian);

        int yearGreg = chrono.year().get(gregDate);
        assertEquals(2000, yearGreg);

        long modifiedJulian = chrono.year().set(julianDate, 1501);
        assertTrue(modifiedJulian > julianDate);

        long modifiedGreg = chrono.year().set(gregDate, 2001);
        assertTrue(modifiedGreg > gregDate);

        // Add operations on ImpreciseCutoverField
        long addedYearJulian = chrono.year().add(julianDate, 5);
        assertTrue(addedYearJulian > julianDate);

        long addedYearGreg = chrono.year().add(gregDate, 5);
        assertTrue(addedYearGreg > gregDate);

        // Difference
        int diffYears = chrono.year().getDifference(gregDate, julianDate);
        assertTrue(diffYears > 0);

        long diffYearsLong = chrono.year().getDifferenceAsLong(gregDate, julianDate);
        assertTrue(diffYearsLong > 0);

        // Test other fields like monthOfYear, dayOfMonth, etc.
        long addedMonthsJulian = chrono.monthOfYear().add(julianDate, 2);
        assertTrue(addedMonthsJulian > julianDate);

        long addedMonthsGreg = chrono.monthOfYear().add(gregDate, 2);
        assertTrue(addedMonthsGreg > gregDate);

        int diffMonths = chrono.monthOfYear().getDifference(gregDate, julianDate);
        assertTrue(diffMonths > 0);

        long diffMonthsLong = chrono.monthOfYear().getDifferenceAsLong(gregDate, julianDate);
        assertTrue(diffMonthsLong > 0);
        
        // Test minimum and maximum values
        assertTrue(chrono.year().getMinimumValue(julianDate) <= chrono.year().get(julianDate));
        assertTrue(chrono.year().getMaximumValue(julianDate) >= chrono.year().get(julianDate));
        assertTrue(chrono.year().getMinimumValue(gregDate) <= chrono.year().get(gregDate));
        assertTrue(chrono.year().getMaximumValue(gregDate) >= chrono.year().get(gregDate));
    }

    @Test
    public void testFieldTextAndProperties() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long julianDate = chrono.getDateTimeMillis(1500, 1, 1, 0, 0, 0, 0);
        long gregDate = chrono.getDateTimeMillis(2000, 1, 1, 0, 0, 0, 0);

        Locale locale = Locale.ENGLISH;

        assertNotNull(chrono.monthOfYear().getAsText(julianDate, locale));
        assertNotNull(chrono.monthOfYear().getAsText(gregDate, locale));
        assertNotNull(chrono.monthOfYear().getAsText(1, locale));

        assertNotNull(chrono.monthOfYear().getAsShortText(julianDate, locale));
        assertNotNull(chrono.monthOfYear().getAsShortText(gregDate, locale));
        assertNotNull(chrono.monthOfYear().getAsShortText(1, locale));

        assertTrue(chrono.monthOfYear().getMaximumTextLength(locale) > 0);
        assertTrue(chrono.monthOfYear().getMaximumShortTextLength(locale) > 0);

        assertFalse(chrono.monthOfYear().isLenient());
        assertNotNull(chrono.monthOfYear().getDurationField());
        assertNotNull(chrono.monthOfYear().getRangeDurationField());

        assertFalse(chrono.monthOfYear().isLeap(julianDate));
        assertEquals(0, chrono.monthOfYear().getLeapAmount(julianDate));
        assertNotNull(chrono.monthOfYear().getLeapDurationField());

        // Rounding
        assertTrue(chrono.monthOfYear().roundFloor(julianDate) <= julianDate);
        assertTrue(chrono.monthOfYear().roundCeiling(julianDate) >= julianDate);
        assertTrue(chrono.monthOfYear().roundFloor(gregDate) <= gregDate);
        assertTrue(chrono.monthOfYear().roundCeiling(gregDate) >= gregDate);
    }

    @Test
    public void testDayOfMonthAndMiscFields() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long julianDate = chrono.getDateTimeMillis(1500, 1, 1, 0, 0, 0, 0);
        long gregDate = chrono.getDateTimeMillis(2000, 1, 1, 0, 0, 0, 0);

        long setDomJulian = chrono.dayOfMonth().set(julianDate, 15);
        assertTrue(setDomJulian != 0);

        long setDomGreg = chrono.dayOfMonth().set(gregDate, 15);
        assertTrue(setDomGreg != 0);

        // Test set with text
        long setTextJulian = chrono.dayOfMonth().set(julianDate, "15", Locale.ENGLISH);
        assertTrue(setTextJulian != 0);

        long setTextGreg = chrono.dayOfMonth().set(gregDate, "15", Locale.ENGLISH);
        assertTrue(setTextGreg != 0);
    }

    @Test(expected = IllegalFieldValueException.class)
    public void testInvalidFieldSetJulian() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long julianDate = chrono.getDateTimeMillis(1500, 1, 1, 0, 0, 0, 0);
        // Force setting out of bounds that crosses cutover awkwardly
        chrono.year().set(julianDate, 1600);
    }

    @Test(expected = IllegalFieldValueException.class)
    public void testInvalidFieldSetGregorian() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long gregDate = chrono.getDateTimeMillis(2000, 1, 1, 0, 0, 0, 0);
        // Force setting year backwards past cutover with verification failure
        chrono.year().set(gregDate, 1400);
    }
}