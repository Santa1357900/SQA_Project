package org.joda.time.chrono;

import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeZone;
import org.joda.time.DurationField;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import static org.junit.Assert.*;

public class ZonedChronologyTest {

    private static final DateTimeZone PARIS_ZONE = DateTimeZone.forID("Europe/Paris");
    private static final DateTimeZone NEW_YORK_ZONE = DateTimeZone.forID("America/New_York");
    private static final DateTimeZone FIXED_ZONE = DateTimeZone.forOffsetHours(2);

    private Chronology baseChronology;
    private ZonedChronology zonedChronology;

    @Before
    public void setUp() throws Throwable {
        baseChronology = ISOChronology.getInstanceUTC();
        zonedChronology = ZonedChronology.getInstance(baseChronology, PARIS_ZONE);
    }

    @After
    public void tearDown() throws Throwable {
        baseChronology = null;
        zonedChronology = null;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetInstanceNullBase() throws Throwable {
        ZonedChronology.getInstance(null, PARIS_ZONE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetInstanceNullZone() throws Throwable {
        ZonedChronology.getInstance(baseChronology, null);
    }

    @Test
    public void testGetInstanceValid() throws Throwable {
        ZonedChronology zc = ZonedChronology.getInstance(baseChronology, PARIS_ZONE);
        assertNotNull(zc);
        assertEquals(PARIS_ZONE, zc.getZone());
        assertEquals(baseChronology, zc.withUTC());
    }

    @Test
    public void testWithZone() throws Throwable {
        Chronology zc1 = zonedChronology.withZone(PARIS_ZONE);
        assertSame(zonedChronology, zc1);

        Chronology zc2 = zonedChronology.withZone(null);
        assertEquals(DateTimeZone.getDefault(), ((ZonedChronology) zc2).getZone());

        Chronology zc3 = zonedChronology.withZone(DateTimeZone.UTC);
        assertEquals(baseChronology, zc3);

        Chronology zc4 = zonedChronology.withZone(NEW_YORK_ZONE);
        assertNotNull(zc4);
        assertEquals(NEW_YORK_ZONE, ((ZonedChronology) zc4).getZone());
    }

    @Test
    public void testGetDateTimeMillis4Args() throws Throwable {
        long millis = zonedChronology.getDateTimeMillis(2020, 6, 15, 12, 30, 0, 0);
        assertTrue(millis > 0);

        DateTime dt = new DateTime(millis, zonedChronology);
        assertEquals(2020, dt.getYear());
        assertEquals(6, dt.getMonthOfYear());
        assertEquals(15, dt.getDayOfMonth());
        assertEquals(12, dt.getHourOfDay());
    }

    @Test
    public void testGetDateTimeMillis4SimpleArgs() throws Throwable {
        long millis = zonedChronology.getDateTimeMillis(2020, 6, 15, 50000);
        assertTrue(millis > 0);
    }

    @Test
    public void testGetDateTimeMillisInstantArgs() throws Throwable {
        long baseMillis = baseChronology.getDateTimeMillis(2020, 6, 15, 0, 0, 0, 0);
        long millis = zonedChronology.getDateTimeMillis(baseMillis, 12, 30, 0, 0);
        assertTrue(millis > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLocalToUTCInvalidInstant() throws Throwable {
        // Transition in Paris: Spring forward (e.g., 2007-03-25T02:00:00 -> 03:00:00)
        // Trying to construct a local time that doesn't exist.
        zonedChronology.getDateTimeMillis(2007, 3, 25, 2, 30, 0, 0);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        ZonedChronology zc1 = ZonedChronology.getInstance(baseChronology, PARIS_ZONE);
        ZonedChronology zc2 = ZonedChronology.getInstance(baseChronology, PARIS_ZONE);
        ZonedChronology zc3 = ZonedChronology.getInstance(baseChronology, NEW_YORK_ZONE);

        assertTrue(zc1.equals(zc1));
        assertTrue(zc1.equals(zc2));
        assertEquals(zc1.hashCode(), zc2.hashCode());

        assertFalse(zc1.equals(null));
        assertFalse(zc1.equals("SomeString"));
        assertFalse(zc1.equals(zc3));
    }

    @Test
    public void testToString() throws Throwable {
        String str = zonedChronology.toString();
        assertNotNull(str);
        assertTrue(str.startsWith("ZonedChronology["));
        assertTrue(str.contains("Europe/Paris"));
    }

    @Test
    public void testFieldsBehavior() throws Throwable {
        DateTimeField hourField = zonedChronology.hourOfDay();
        assertNotNull(hourField);
        assertTrue(hourField.isSupported());
        
        long instant = baseChronology.getDateTimeMillis(2020, 6, 15, 10, 0, 0, 0);
        int hour = hourField.get(instant);
        assertEquals(12, hour); // UTC 10:00 + 2 hours (Paris in June is UTC+2)

        String text = hourField.getAsText(instant, Locale.ENGLISH);
        assertEquals("12", text);

        String shortText = hourField.getAsShortText(instant, Locale.ENGLISH);
        assertEquals("12", shortText);

        assertEquals("12", hourField.getAsText(12, Locale.ENGLISH));
        assertEquals("12", hourField.getAsShortText(12, Locale.ENGLISH));

        long addedInstant = hourField.add(instant, 3);
        DateTime dtAdded = new DateTime(addedInstant, zonedChronology);
        assertEquals(15, dtAdded.getHourOfDay());

        long addedLongInstant = hourField.add(instant, 3L);
        DateTime dtAddedLong = new DateTime(addedLongInstant, zonedChronology);
        assertEquals(15, dtAddedLong.getHourOfDay());

        long wrappedInstant = hourField.addWrapField(instant, 15);
        assertTrue(wrappedInstant > 0);

        long setInstant = hourField.set(instant, 15);
        DateTime dtSet = new DateTime(setInstant, zonedChronology);
        assertEquals(15, dtSet.getHourOfDay());

        long setStringInstant = hourField.set(instant, "14", Locale.ENGLISH);
        DateTime dtSetString = new DateTime(setStringInstant, zonedChronology);
        assertEquals(14, dtSetString.getHourOfDay());

        int diff = hourField.getDifference(instant + 3600000L, instant);
        assertEquals(1, diff);

        long diffLong = hourField.getDifferenceAsLong(instant + 3600000L, instant);
        assertEquals(1L, diffLong);

        assertNotNull(hourField.getDurationField());
        assertNotNull(hourField.getRangeDurationField());
        assertNotNull(hourField.getLeapDurationField());

        assertFalse(hourField.isLeap(instant));
        assertEquals(0, hourField.getLeapAmount(instant));

        long floorInstant = hourField.roundFloor(instant);
        assertTrue(floorInstant <= instant);

        long ceilInstant = hourField.roundCeiling(instant);
        assertTrue(ceilInstant >= instant);

        long rem = hourField.remainder(instant);
        assertTrue(rem >= 0);

        assertEquals(0, hourField.getMinimumValue());
        assertEquals(0, hourField.getMinimumValue(instant));
        assertEquals(0, hourField.getMinimumValue(new LocalDate(2020, 6, 15)));
        assertEquals(0, hourField.getMinimumValue(new LocalDate(2020, 6, 15), new int[]{15}));

        assertEquals(23, hourField.getMaximumValue());
        assertEquals(23, hourField.getMaximumValue(instant));
        assertEquals(23, hourField.getMaximumValue(new LocalDate(2020, 6, 15)));
        assertEquals(23, hourField.getMaximumValue(new LocalDate(2020, 6, 15), new int[]{15}));

        assertTrue(hourField.getMaximumTextLength(Locale.ENGLISH) > 0);
        assertTrue(hourField.getMaximumShortTextLength(Locale.ENGLISH) > 0);
    }

    @Test(expected = IllegalFieldValueException.class)
    public void testFieldInvalidSet() throws Throwable {
        DateTimeField hourField = zonedChronology.hourOfDay();
        long instant = baseChronology.getDateTimeMillis(2020, 6, 15, 10, 0, 0, 0);
        // Setting an invalid value that gets modified or causes transition mismatch
        hourField.set(instant, 99);
    }

    @Test
    public void testYearFieldNonTimeArithmetic() throws Throwable {
        DateTimeField yearField = zonedChronology.year();
        assertNotNull(yearField);
        long instant = baseChronology.getDateTimeMillis(2020, 6, 15, 10, 0, 0, 0);
        
        long added = yearField.add(instant, 2);
        DateTime dt = new DateTime(added, zonedChronology);
        assertEquals(2022, dt.getYear());

        long addedLong = yearField.add(instant, 2L);
        DateTime dtLong = new DateTime(addedLong, zonedChronology);
        assertEquals(2022, dtLong.getYear());

        long wrapped = yearField.addWrapField(instant, 1);
        assertTrue(wrapped > 0);

        long floored = yearField.roundFloor(instant);
        assertTrue(floored <= instant);

        long ceiled = yearField.roundCeiling(instant);
        assertTrue(ceiled >= instant);
    }

    @Test
    public void testDurationFields() throws Throwable {
        DurationField daysField = zonedChronology.days();
        assertNotNull(daysField);
        assertTrue(daysField.isSupported());
        
        long instant = baseChronology.getDateTimeMillis(2020, 6, 15, 10, 0, 0, 0);
        
        int val = daysField.getValue(86400000L, instant);
        assertEquals(1, val);

        long valLong = daysField.getValueAsLong(86400000L, instant);
        assertEquals(1L, valLong);

        long millisFromInt = daysField.getMillis(1, instant);
        assertEquals(86400000L, millisFromInt);

        long millisFromLong = daysField.getMillis(1L, instant);
        assertEquals(86400000L, millisFromLong);

        long addedDuration = daysField.add(instant, 5);
        assertTrue(addedDuration > instant);

        long addedDurationLong = daysField.add(instant, 5L);
        assertTrue(addedDurationLong > instant);

        int diff = daysField.getDifference(instant + 86400000L, instant);
        assertEquals(1, diff);

        long diffLong = daysField.getDifferenceAsLong(instant + 86400000L, instant);
        assertEquals(1L, diffLong);

        DurationField millisField = zonedChronology.millisRs(); // or millis()
        assertNotNull(zonedChronology.millis());
    }

    @Test
    public void testFixedZoneArithmetic() throws Throwable {
        ZonedChronology fixedZoned = ZonedChronology.getInstance(baseChronology, FIXED_ZONE);
        DateTimeField hourField = fixedZoned.hourOfDay();
        long instant = baseChronology.getDateTimeMillis(2020, 6, 15, 10, 0, 0, 0);
        long added = hourField.add(instant, 2);
        assertTrue(added > 0);
    }

    @Test(expected = ArithmeticException.class)
    public void testZonedDurationFieldOverflowAdd() throws Throwable {
        DurationField daysField = zonedChronology.days();
        // Force overflow condition in addOffset or similar inside ZonedDurationField via extreme values
        daysField.add(Long.MAX_VALUE, 1000000);
    }

    @Test(expected = ArithmeticException.class)
    public void testZonedDurationFieldOverflowGetDifference() throws Throwable {
        DurationField daysField = zonedChronology.days();
        daysField.getDifference(Long.MAX_VALUE, Long.MIN_VALUE);
    }
}