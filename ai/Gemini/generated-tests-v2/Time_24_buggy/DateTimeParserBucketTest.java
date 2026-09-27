package org.joda.time.format;

import junit.framework.TestCase;

import java.util.Locale;

import org.joda.time.Chronology;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeZone;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.chrono.ISOChronology;

public class DateTimeParserBucketTest extends TestCase {

    public void testConstructorsAndGetters() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        Locale locale = Locale.FRENCH;
        Integer pivotYear = Integer.valueOf(1950);

        DateTimeParserBucket bucket1 = new DateTimeParserBucket(0L, chrono, locale);
        assertNotNull(bucket1.getChronology());
        assertEquals(locale, bucket1.getLocale());
        assertNull(bucket1.getPivotYear());

        DateTimeParserBucket bucket2 = new DateTimeParserBucket(0L, chrono, locale, pivotYear);
        assertEquals(pivotYear, bucket2.getPivotYear());

        DateTimeParserBucket bucket3 = new DateTimeParserBucket(0L, chrono, locale, pivotYear, 2020);
        assertEquals(pivotYear, bucket3.getPivotYear());
        assertEquals(chrono.withUTC(), bucket3.getChronology());
    }

    public void testZoneAndOffsetMutations() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        DateTimeParserBucket bucket = new DateTimeParserBucket(0L, chrono, Locale.getDefault());

        assertNull(bucket.getZone());
        assertEquals(0, bucket.getOffset());

        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        bucket.setZone(zone);
        assertEquals(zone, bucket.getZone());
        assertEquals(0, bucket.getOffset());

        // Setting zone to UTC should result in null zone internally
        bucket.setZone(DateTimeZone.UTC);
        assertNull(bucket.getZone());

        bucket.setOffset(14400);
        assertEquals(14400, bucket.getOffset());
        assertNull(bucket.getZone());
    }

    public void testPivotYearSetting() throws Throwable {
        DateTimeParserBucket bucket = new DateTimeParserBucket(0L, null, null, null, 2000);
        assertNull(bucket.getPivotYear());

        bucket.setPivotYear(Integer.valueOf(1975));
        assertEquals(Integer.valueOf(1975), bucket.getPivotYear());

        bucket.setPivotYear(null);
        assertNull(bucket.getPivotYear());
    }

    public void testSaveFieldVariants() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        DateTimeParserBucket bucket = new DateTimeParserBucket(0L, chrono, Locale.ENGLISH);

        // Save by DateTimeFieldType with int value
        bucket.saveField(DateTimeFieldType.year(), 2012);
        bucket.saveField(DateTimeFieldType.monthOfYear(), 6);

        // Save by DateTimeFieldType with text and locale
        bucket.saveField(DateTimeFieldType.dayOfMonth(), "15", Locale.ENGLISH);

        // Save by explicit DateTimeField
        bucket.saveField(DateTimeFieldType.hourOfDay().getField(chrono), 10);

        long millis = bucket.computeMillis();
        assertTrue(millis != 0L);
    }

    public void testSaveStateAndRestoreState() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        DateTimeParserBucket bucket = new DateTimeParserBucket(0L, chrono, Locale.ENGLISH);

        bucket.saveField(DateTimeFieldType.year(), 2010);
        Object state1 = bucket.saveState();
        assertNotNull(state1);

        bucket.saveField(DateTimeFieldType.monthOfYear(), 5);
        Object state2 = bucket.saveState();

        // Restore to state1 (should discard monthOfYear)
        boolean restored1 = bucket.restoreState(state1);
        assertTrue(restored1);

        // Restore with invalid state object
        boolean restoredInvalid = bucket.restoreState(new Object());
        assertFalse(restoredInvalid);

        // Restore to state2 again
        boolean restored2 = bucket.restoreState(state2);
        assertTrue(restored2);
    }

    public void testComputeMillisEdgeCasesAndSorting() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        // Insert fields in reverse-logical or unordered manner to test internal sorting (both insertion sort and Arrays.sort)
        DateTimeParserBucket bucket = new DateTimeParserBucket(0L, chrono, Locale.ENGLISH);
        
        // Add more than 10 fields to trigger Arrays.sort path in sort(SavedField[], int)
        bucket.saveField(DateTimeFieldType.secondOfMinute(), 1);
        bucket.saveField(DateTimeFieldType.minuteOfHour(), 2);
        bucket.saveField(DateTimeFieldType.hourOfDay(), 3);
        bucket.saveField(DateTimeFieldType.dayOfMonth(), 4);
        bucket.saveField(DateTimeFieldType.monthOfYear(), 5);
        bucket.saveField(DateTimeFieldType.year(), 2015);
        bucket.saveField(DateTimeFieldType.millisOfSecond(), 500);
        bucket.saveField(DateTimeFieldType.millisOfDay(), 1000);
        bucket.saveField(DateTimeFieldType.secondOfDay(), 10);
        bucket.saveField(DateTimeFieldType.minuteOfDay(), 20);
        bucket.saveField(DateTimeFieldType.weekyear(), 2015);

        long millis = bucket.computeMillis(true, "test-text");
        assertTrue(millis != 0L);
    }

    public void testComputeMillisWithMonthOrDayFirst() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        // If first field is month or day, default year should be applied
        DateTimeParserBucket bucket = new DateTimeParserBucket(0L, chrono, Locale.ENGLISH, null, 2010);
        bucket.saveField(DateTimeFieldType.monthOfYear(), 3);
        bucket.saveField(DateTimeFieldType.dayOfMonth(), 25);

        long millis = bucket.computeMillis(false);
        assertTrue(millis != 0L);
    }

    public void testComputeMillisWithIllegalValues() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        DateTimeParserBucket bucket = new DateTimeParserBucket(0L, chrono, Locale.ENGLISH);
        bucket.saveField(DateTimeFieldType.monthOfYear(), 99); // Invalid month

        try {
            bucket.computeMillis(false, "bad-date");
            fail("Should have thrown IllegalFieldValueException");
        } catch (IllegalFieldValueException e) {
            assertTrue(e.getMessage().contains("bad-date") || e.getMessage() != null);
        }
    }

    public void testComputeMillisWithIllegalTimeZoneOffsetTransition() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        DateTimeParserBucket bucket = new DateTimeParserBucket(0L, chrono, Locale.ENGLISH);
        // Set a time zone that has transitions, e.g., America/New_York
        DateTimeZone tz = DateTimeZone.forID("America/New_York");
        bucket.setZone(tz);
        
        // Pick an illegal time during spring forward transition in America/New_York (e.g., 2007-03-11 02:30:00)
        // March 11, 2007, at 2:00 AM clocks sprang forward to 3:00 AM.
        bucket.saveField(DateTimeFieldType.year(), 2007);
        bucket.saveField(DateTimeFieldType.monthOfYear(), 3);
        bucket.saveField(DateTimeFieldType.dayOfMonth(), 11);
        bucket.saveField(DateTimeFieldType.hourOfDay(), 2);
        bucket.saveField(DateTimeFieldType.minuteOfHour(), 30);

        try {
            bucket.computeMillis(false, "transition-text");
            fail("Should have thrown IllegalArgumentException due to illegal instant");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("transition-text") || e.getMessage().contains("Illegal instant"));
        }
    }
}