package org.joda.time.chrono;

import org.joda.time.Chronology;
import org.joda.time.DateTimeZone;
import org.joda.time.DurationField;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.Instant;

import org.junit.Test;
import static org.junit.Assert.*;

public class GJChronologyClaudeTest {

    // getInstanceUTC(): zone must be UTC
    @Test
    public void testGetInstanceUTC_zoneIsUTC() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        assertEquals(DateTimeZone.UTC, chrono.getZone());
    }

    // getInstanceUTC(): cache branch - called twice returns same instance
    @Test
    public void testGetInstanceUTC_calledTwice_returnsSameCachedInstance() throws Throwable {
        assertSame(GJChronology.getInstanceUTC(), GJChronology.getInstanceUTC());
    }

    // getInstance(): default cutover/minDays regardless of default zone
    @Test
    public void testGetInstance_noArgs_returnsDefaultCutoverAndMinDays() throws Throwable {
        GJChronology chrono = GJChronology.getInstance();
        assertNotNull(chrono);
        assertEquals(GJChronology.DEFAULT_CUTOVER, chrono.getGregorianCutover());
        assertEquals(4, chrono.getMinimumDaysInFirstWeek());
    }

    // getInstance(zone): explicit zone, default cutover and minDays
    @Test
    public void testGetInstance_withZone_setsGivenZoneAndDefaults() throws Throwable {
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.forID("Europe/Paris"));
        assertEquals(DateTimeZone.forID("Europe/Paris"), chrono.getZone());
        assertEquals(GJChronology.DEFAULT_CUTOVER, chrono.getGregorianCutover());
        assertEquals(4, chrono.getMinimumDaysInFirstWeek());
    }

    // getInstance(zone, cutover): null cutover -> default cutover branch
    @Test
    public void testGetInstance_zoneAndNullCutover_usesDefaultCutover() throws Throwable {
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.UTC, null);
        assertEquals(GJChronology.DEFAULT_CUTOVER, chrono.getGregorianCutover());
    }

    // getInstance(zone, cutover): custom cutover stored correctly
    @Test
    public void testGetInstance_zoneAndCustomCutover_setsGivenCutover() throws Throwable {
        Instant custom = new Instant(0L);
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.UTC, custom);
        assertEquals(custom, chrono.getGregorianCutover());
        assertEquals(4, chrono.getMinimumDaysInFirstWeek());
    }

    // getInstance(zone, cutover, minDays): cache hit branch - same params return same instance
    @Test
    public void testGetInstance_sameParams_cachedSameInstance() throws Throwable {
        GJChronology a = GJChronology.getInstance(DateTimeZone.UTC, GJChronology.DEFAULT_CUTOVER, 4);
        GJChronology b = GJChronology.getInstance(DateTimeZone.UTC, GJChronology.DEFAULT_CUTOVER, 4);
        assertSame(a, b);
    }

    // getInstance(zone, cutover, minDays): different minDays -> distinct, unequal instances
    @Test
    public void testGetInstance_differentMinDays_notEqualAndNotSame() throws Throwable {
        GJChronology a = GJChronology.getInstance(DateTimeZone.UTC, null, 4);
        GJChronology b = GJChronology.getInstance(DateTimeZone.UTC, null, 7);
        assertNotSame(a, b);
        assertFalse(a.equals(b));
    }

    // getInstance(zone, long, minDays): millis equal to DEFAULT_CUTOVER -> null-cutover branch
    @Test
    public void testGetInstanceLong_defaultMillis_usesDefaultCutoverInstant() throws Throwable {
        long defaultMillis = GJChronology.DEFAULT_CUTOVER.getMillis();
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.UTC, defaultMillis, 4);
        assertEquals(GJChronology.DEFAULT_CUTOVER, chrono.getGregorianCutover());
    }

    // getInstance(zone, long, minDays): custom millis stored as Instant
    @Test
    public void testGetInstanceLong_customMillis_setsCutover() throws Throwable {
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.UTC, 0L, 4);
        assertEquals(0L, chrono.getGregorianCutover().getMillis());
    }

    // getZone(): returns configured zone
    @Test
    public void testGetZone_returnsConfiguredZone() throws Throwable {
        assertEquals(DateTimeZone.UTC, GJChronology.getInstanceUTC().getZone());
    }

    // withUTC(): zone becomes UTC
    @Test
    public void testWithUTC_convertsToUTCZone() throws Throwable {
        GJChronology paris = GJChronology.getInstance(DateTimeZone.forID("Europe/Paris"));
        Chronology utc = paris.withUTC();
        assertEquals(DateTimeZone.UTC, utc.getZone());
    }

    // withZone(zone): same zone -> returns this (identity branch)
    @Test
    public void testWithZone_sameZone_returnsThis() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        assertSame(chrono, chrono.withZone(DateTimeZone.UTC));
    }

    // withZone(zone): different zone -> new chronology with that zone
    @Test
    public void testWithZone_differentZone_returnsNewChronologyWithThatZone() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        Chronology paris = chrono.withZone(DateTimeZone.forID("Europe/Paris"));
        assertEquals(DateTimeZone.forID("Europe/Paris"), paris.getZone());
    }

    // getDateTimeMillis(4-arg): normal Gregorian date round-trips through field getters
    @Test
    public void testGetDateTimeMillis4Arg_normalGregorianDate_roundTrips() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long millis = chrono.getDateTimeMillis(2000, 6, 15, 12345);
        assertEquals(2000, chrono.year().get(millis));
        assertEquals(6, chrono.monthOfYear().get(millis));
        assertEquals(15, chrono.dayOfMonth().get(millis));
        assertEquals(12345, chrono.millisOfDay().get(millis));
    }

    // getDateTimeMillis(4-arg): date before cutover falls back to Julian chronology
    @Test
    public void testGetDateTimeMillis4Arg_dateBeforeCutover_matchesJulianChronology() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        JulianChronology julian = JulianChronology.getInstance(DateTimeZone.UTC, 4);
        long millis = chrono.getDateTimeMillis(1500, 3, 10, 0);
        assertEquals(julian.getDateTimeMillis(1500, 3, 10, 0), millis);
    }

    // getDateTimeMillis(4-arg): date inside the illegal cutover gap throws
    @Test
    public void testGetDateTimeMillis4Arg_dateInCutoverGap_throwsIllegalArgumentException() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        try {
            chrono.getDateTimeMillis(1582, 10, 10, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getDateTimeMillis(7-arg): normal date round-trips through field getters
    @Test
    public void testGetDateTimeMillis7Arg_normalDate_roundTrips() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long millis = chrono.getDateTimeMillis(2020, 1, 1, 10, 30, 15, 123);
        assertEquals(2020, chrono.year().get(millis));
        assertEquals(10, chrono.hourOfDay().get(millis));
        assertEquals(30, chrono.minuteOfHour().get(millis));
        assertEquals(15, chrono.secondOfMinute().get(millis));
        assertEquals(123, chrono.millisOfSecond().get(millis));
    }

    // getDateTimeMillis(7-arg): Feb 29 before cutover, invalid in Gregorian but valid Julian leap day
    @Test
    public void testGetDateTimeMillis7Arg_feb29BeforeCutover_fallsBackToJulianLeapDay() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        JulianChronology julian = JulianChronology.getInstance(DateTimeZone.UTC, 4);
        long millis = chrono.getDateTimeMillis(1500, 2, 29, 0, 0, 0, 0);
        assertEquals(1500, chrono.year().get(millis));
        assertEquals(2, chrono.monthOfYear().get(millis));
        assertEquals(29, chrono.dayOfMonth().get(millis));
        assertEquals(julian.getDateTimeMillis(1500, 2, 29, 0, 0, 0, 0), millis);
    }

    // getDateTimeMillis(7-arg): Feb 29 after cutover, non-leap in Gregorian -> rethrows original exception
    @Test
    public void testGetDateTimeMillis7Arg_feb29AfterCutoverNonLeapGregorian_throwsIllegalFieldValueException() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        try {
            chrono.getDateTimeMillis(1700, 2, 29, 0, 0, 0, 0);
            fail("expected IllegalFieldValueException");
        } catch (IllegalFieldValueException expected) {
        }
    }

    // getDateTimeMillis(7-arg): date inside the illegal cutover gap throws
    @Test
    public void testGetDateTimeMillis7Arg_dateInCutoverGap_throwsIllegalArgumentException() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        try {
            chrono.getDateTimeMillis(1582, 10, 10, 0, 0, 0, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getGregorianCutover(): returns the configured cutover instant
    @Test
    public void testGetGregorianCutover_returnsConfiguredInstant() throws Throwable {
        Instant cutover = new Instant(123456789L);
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.UTC, cutover, 4);
        assertEquals(cutover, chrono.getGregorianCutover());
    }

    // getMinimumDaysInFirstWeek(): returns the configured value
    @Test
    public void testGetMinimumDaysInFirstWeek_returnsConfiguredValue() throws Throwable {
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.UTC, null, 6);
        assertEquals(6, chrono.getMinimumDaysInFirstWeek());
    }

    // equals(): same instance is equal to itself
    @Test
    public void testEquals_sameInstance_true() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        assertTrue(chrono.equals(chrono));
    }

    // equals(): different zone -> not equal
    @Test
    public void testEquals_differentZone_false() throws Throwable {
        GJChronology utc = GJChronology.getInstanceUTC();
        GJChronology paris = GJChronology.getInstance(DateTimeZone.forID("Europe/Paris"));
        assertFalse(utc.equals(paris));
    }

    // equals(): different cutover -> not equal
    @Test
    public void testEquals_differentCutover_false() throws Throwable {
        GJChronology a = GJChronology.getInstance(DateTimeZone.UTC, new Instant(0L), 4);
        GJChronology b = GJChronology.getInstance(DateTimeZone.UTC, new Instant(1000L), 4);
        assertFalse(a.equals(b));
    }

    // equals(): different minimumDaysInFirstWeek -> not equal
    @Test
    public void testEquals_differentMinDays_false() throws Throwable {
        GJChronology a = GJChronology.getInstance(DateTimeZone.UTC, null, 4);
        GJChronology b = GJChronology.getInstance(DateTimeZone.UTC, null, 7);
        assertFalse(a.equals(b));
    }

    // equals(): non-GJChronology object -> not equal
    @Test
    public void testEquals_nonGJChronologyObject_false() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        assertFalse(chrono.equals("not a chronology"));
    }

    // hashCode(): equal chronologies produce equal hash codes
    @Test
    public void testHashCode_equalChronologies_sameHashCode() throws Throwable {
        GJChronology a = GJChronology.getInstanceUTC();
        GJChronology b = GJChronology.getInstance(DateTimeZone.UTC, null, 4);
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
    }

    // toString(): default cutover and minDays -> only zone shown
    @Test
    public void testToString_defaultCutoverAndMinDays_formatsAsZoneOnly() throws Throwable {
        assertEquals("GJChronology[UTC]", GJChronology.getInstanceUTC().toString());
    }

    // toString(): custom cutover -> string contains cutover suffix
    @Test
    public void testToString_customCutover_containsCutoverSuffix() throws Throwable {
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.UTC, new Instant(0L), 4);
        String s = chrono.toString();
        assertTrue(s.indexOf("cutover=") >= 0);
    }

    // toString(): custom minDaysInFirstWeek -> string contains mdfw suffix
    @Test
    public void testToString_customMinDaysInFirstWeek_containsMdfwSuffix() throws Throwable {
        GJChronology chrono = GJChronology.getInstance(DateTimeZone.UTC, null, 7);
        String s = chrono.toString();
        assertTrue(s.indexOf("mdfw=7") >= 0);
    }



    // assemble(): control check - yearOfCentury correctly shares year's duration field
    @Test
    public void testYearOfCentury_durationField_matchesYearDurationField() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        assertSame(chrono.year().getDurationField(), chrono.yearOfCentury().getDurationField());
    }

    // assemble(): control check - yearOfEra correctly shares year's duration field
    @Test
    public void testYearOfEra_durationField_matchesYearDurationField() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        assertSame(chrono.year().getDurationField(), chrono.yearOfEra().getDurationField());
    }

    // CutoverField.get(): instant at/after cutover delegates to Gregorian field
    @Test
    public void testWeekyearGet_afterCutover_matchesGregorianChronology() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        GregorianChronology greg = GregorianChronology.getInstance(DateTimeZone.UTC, 4);
        long millis = chrono.getDateTimeMillis(2020, 1, 1, 0);
        assertEquals(greg.weekyear().get(millis), chrono.weekyear().get(millis));
    }

    // CutoverField.get(): instant before cutover delegates to Julian field
    @Test
    public void testWeekyearGet_beforeCutover_matchesJulianChronology() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        JulianChronology jul = JulianChronology.getInstance(DateTimeZone.UTC, 4);
        long millis = chrono.getDateTimeMillis(1500, 6, 15, 0);
        assertEquals(jul.weekyear().get(millis), chrono.weekyear().get(millis));
    }

    // CutoverField.getMaximumValue(instant): October 1582 is truncated by the cutover gap
    @Test
    public void testDayOfMonth_getMaximumValue_inCutoverGapMonth_returnsLastJulianDay() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long oct1_1582 = chrono.getDateTimeMillis(1582, 10, 1, 0);
        assertEquals(4, chrono.dayOfMonth().getMaximumValue(oct1_1582));
    }

    // dayOfYear cutover field: sequence stays unbroken across the gap (Javadoc guarantee)
    @Test
    public void testDayOfYear_acrossCutover_sequenceStaysUnbroken() throws Throwable {
        GJChronology chrono = GJChronology.getInstanceUTC();
        long oct4 = chrono.getDateTimeMillis(1582, 10, 4, 0);
        long oct15 = chrono.getDateTimeMillis(1582, 10, 15, 0);
        int doy4 = chrono.dayOfYear().get(oct4);
        int doy15 = chrono.dayOfYear().get(oct15);
        assertEquals(doy4 + 1, doy15);
    }
}
