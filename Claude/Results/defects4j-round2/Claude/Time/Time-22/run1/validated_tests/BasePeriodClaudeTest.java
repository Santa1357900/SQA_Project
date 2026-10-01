package org.joda.time.base;

import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.DurationFieldType;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.PeriodType;
import org.joda.time.ReadableDuration;
import org.joda.time.ReadableInstant;
import org.joda.time.ReadablePartial;
import org.joda.time.ReadablePeriod;
import org.joda.time.chrono.ISOChronology;

import org.junit.Test;
import static org.junit.Assert.*;

public class BasePeriodClaudeTest {

    /** Minimal concrete subclass exposing protected members of BasePeriod for testing. */
    static class TestPeriod extends BasePeriod {
        TestPeriod(int years, int months, int weeks, int days,
                   int hours, int minutes, int seconds, int millis, PeriodType type) {
            super(years, months, weeks, days, hours, minutes, seconds, millis, type);
        }
        TestPeriod(long startInstant, long endInstant, PeriodType type, Chronology chrono) {
            super(startInstant, endInstant, type, chrono);
        }
        TestPeriod(ReadableInstant start, ReadableInstant end, PeriodType type) {
            super(start, end, type);
        }
        TestPeriod(ReadablePartial start, ReadablePartial end, PeriodType type) {
            super(start, end, type);
        }
        TestPeriod(ReadableInstant start, ReadableDuration duration, PeriodType type) {
            super(start, duration, type);
        }
        TestPeriod(ReadableDuration duration, ReadableInstant end, PeriodType type) {
            super(duration, end, type);
        }
        TestPeriod(long duration) {
            super(duration);
        }
        TestPeriod(long duration, PeriodType type, Chronology chrono) {
            super(duration, type, chrono);
        }
        TestPeriod(int[] values, PeriodType type) {
            super(values, type);
        }
        PeriodType doCheckPeriodType(PeriodType type) { return checkPeriodType(type); }
        void doSetPeriod(ReadablePeriod period) { setPeriod(period); }
        void doSetPeriod(int y, int mo, int w, int d, int h, int mi, int s, int ms) {
            setPeriod(y, mo, w, d, h, mi, s, ms);
        }
        void doSetField(DurationFieldType field, int value) { setField(field, value); }
        void doSetFieldInto(int[] values, DurationFieldType field, int value) {
            setFieldInto(values, field, value);
        }
        void doAddField(DurationFieldType field, int value) { addField(field, value); }
        void doAddFieldInto(int[] values, DurationFieldType field, int value) {
            addFieldInto(values, field, value);
        }
        void doMergePeriod(ReadablePeriod period) { mergePeriod(period); }
        int[] doMergePeriodInto(int[] values, ReadablePeriod period) {
            return mergePeriodInto(values, period);
        }
        void doAddPeriod(ReadablePeriod period) { addPeriod(period); }
        int[] doAddPeriodInto(int[] values, ReadablePeriod period) {
            return addPeriodInto(values, period);
        }
        void doSetValue(int index, int value) { setValue(index, value); }
        void doSetValues(int[] values) { setValues(values); }
    }

    // constructor(8 ints,type): valid standard type sets all eight fields correctly
    @Test
    public void testConstructorInts_standardType_setsAllFields() throws Throwable {
        TestPeriod p = new TestPeriod(1, 2, 3, 4, 5, 6, 7, 8, PeriodType.standard());
        assertEquals(1, p.getValue(0));
        assertEquals(8, p.getValue(7));
        assertEquals(8, p.size());
    }

    // constructor(8 ints,type): null type defaults to the standard period type
    @Test
    public void testConstructorInts_nullType_defaultsStandard() throws Throwable {
        TestPeriod p = new TestPeriod(0, 0, 0, 0, 0, 0, 0, 0, null);
        assertEquals(PeriodType.standard(), p.getPeriodType());
    }

    // constructor(8 ints,type): unsupported field with nonzero value throws
    @Test
    public void testConstructorInts_unsupportedFieldNonZero_throws() throws Throwable {
        try {
            new TestPeriod(0, 5, 0, 0, 0, 0, 0, 0, PeriodType.years());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // constructor(8 ints,type): unsupported field with zero value does not throw
    @Test
    public void testConstructorInts_unsupportedFieldZero_noThrow() throws Throwable {
        TestPeriod p = new TestPeriod(5, 0, 0, 0, 0, 0, 0, 0, PeriodType.years());
        assertEquals(5, p.getValue(0));
        assertEquals(1, p.size());
    }

    // constructor(long,long,type,chrono): computes exact day difference with explicit UTC chronology
    @Test
    public void testConstructorLongLongTypeChrono_daysDifference() throws Throwable {
        TestPeriod p = new TestPeriod(0L, 2L * 86400000L, PeriodType.days(), ISOChronology.getInstanceUTC());
        assertEquals(2, p.getValue(0));
    }

    // constructor(long,long,type,chrono): null type/chrono default safely for a zero-length interval
    @Test
    public void testConstructorLongLongTypeChrono_nullsDefault_zeroInterval() throws Throwable {
        TestPeriod p = new TestPeriod(5L, 5L, null, null);
        assertEquals(PeriodType.standard(), p.getPeriodType());
        for (int i = 0; i < p.size(); i++) {
            assertEquals(0, p.getValue(i));
        }
    }

    // constructor(instant,instant,type): both null instants produce an all-zero period
    @Test
    public void testConstructorInstantInstantType_bothNull_zeroValues() throws Throwable {
        TestPeriod p = new TestPeriod((ReadableInstant) null, (ReadableInstant) null, PeriodType.standard());
        assertEquals(8, p.size());
        for (int i = 0; i < 8; i++) {
            assertEquals(0, p.getValue(i));
        }
    }

    // constructor(instant,instant,type): non-null instants compute the day difference
    @Test
    public void testConstructorInstantInstantType_nonNull_computesDays() throws Throwable {
        DateTime start = new DateTime(0L, DateTimeZone.UTC);
        DateTime end = new DateTime(2L * 86400000L, DateTimeZone.UTC);
        TestPeriod p = new TestPeriod(start, end, PeriodType.days());
        assertEquals(2, p.getValue(0));
    }

    // constructor(partial,partial,type): null start throws per javadoc contract
    @Test
    public void testConstructorPartialPartialType_nullStart_throws() throws Throwable {
        LocalDate end = new LocalDate(2000, 1, 2, ISOChronology.getInstanceUTC());
        try {
            new TestPeriod((ReadablePartial) null, end, PeriodType.standard());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not be null"));
        }
    }

    // constructor(partial,partial,type): null end throws per javadoc contract
    @Test
    public void testConstructorPartialPartialType_nullEnd_throws() throws Throwable {
        LocalDate start = new LocalDate(2000, 1, 1, ISOChronology.getInstanceUTC());
        try {
            new TestPeriod(start, (ReadablePartial) null, PeriodType.standard());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not be null"));
        }
    }

    // constructor(partial,partial,type): fast BaseLocal path computes correct day difference
    @Test
    public void testConstructorPartialPartialType_fastPath_computesDays() throws Throwable {
        LocalDate start = new LocalDate(2000, 1, 1, ISOChronology.getInstanceUTC());
        LocalDate end = new LocalDate(2000, 1, 3, ISOChronology.getInstanceUTC());
        TestPeriod p = new TestPeriod(start, end, PeriodType.days());
        assertEquals(2, p.getValue(0));
    }

    // constructor(partial,partial,type): mismatched field counts throws
    @Test
    public void testConstructorPartialPartialType_mismatchedFieldCount_throws() throws Throwable {
        LocalDate start = new LocalDate(2000, 1, 1, ISOChronology.getInstanceUTC());
        LocalTime end = new LocalTime(0, 0, 0, 0, ISOChronology.getInstanceUTC());
        try {
            new TestPeriod(start, end, PeriodType.standard());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("same set of fields"));
        }
    }

    // constructor(instant,duration,type): computes correct day value from a millisecond duration
    @Test
    public void testConstructorInstantDurationType_computesDays() throws Throwable {
        DateTime start = new DateTime(0L, DateTimeZone.UTC);
        Duration duration = new Duration(2L * 86400000L);
        TestPeriod p = new TestPeriod(start, duration, PeriodType.days());
        assertEquals(2, p.getValue(0));
    }

    // constructor(duration,instant,type): computes correct day value working backward from end instant
    @Test
    public void testConstructorDurationInstantType_computesDays() throws Throwable {
        Duration duration = new Duration(2L * 86400000L);
        DateTime end = new DateTime(2L * 86400000L, DateTimeZone.UTC);
        TestPeriod p = new TestPeriod(duration, end, PeriodType.days());
        assertEquals(2, p.getValue(0));
    }



    // constructor(long,type,chrono): explicit time-only type correctly decomposes h/m/s/ms
    @Test
    public void testConstructorLongTypeChrono_timeType_computesFields() throws Throwable {
        TestPeriod p = new TestPeriod(3723004L, PeriodType.time(), ISOChronology.getInstanceUTC());
        assertEquals(1, p.getValue(0));
        assertEquals(2, p.getValue(1));
        assertEquals(3, p.getValue(2));
        assertEquals(4, p.getValue(3));
    }

    // constructor(long,type,chrono): null type defaults to standard period type
    @Test
    public void testConstructorLongTypeChrono_nullType_defaultsStandard() throws Throwable {
        TestPeriod p = new TestPeriod(0L, null, ISOChronology.getInstanceUTC());
        assertEquals(PeriodType.standard(), p.getPeriodType());
        assertEquals(8, p.size());
    }

    // constructor(long,type,chrono): null chrono does not throw for a zero-length duration
    @Test
    public void testConstructorLongTypeChrono_nullChrono_noException() throws Throwable {
        TestPeriod p = new TestPeriod(0L, PeriodType.millis(), null);
        assertEquals(PeriodType.millis(), p.getPeriodType());
        assertEquals(0, p.getValue(0));
    }

    // constructor(int[],type): trusted constructor stores values and type directly without validation
    @Test
    public void testConstructorIntArrayType_storesValuesDirectly() throws Throwable {
        int[] values = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
        TestPeriod p = new TestPeriod(values, PeriodType.standard());
        assertEquals(PeriodType.standard(), p.getPeriodType());
        for (int i = 0; i < 8; i++) {
            assertEquals(i + 1, p.getValue(i));
        }
    }

    // checkPeriodType: null input converted to the default standard type
    @Test
    public void testCheckPeriodType_nullDefaultsToStandard() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        assertEquals(PeriodType.standard(), p.doCheckPeriodType(null));
    }

    // checkPeriodType: a non-null type is returned unchanged
    @Test
    public void testCheckPeriodType_nonNullReturnedAsIs() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        assertEquals(PeriodType.hours(), p.doCheckPeriodType(PeriodType.hours()));
    }

    // size(): delegates to the period type's field count
    @Test
    public void testSize_matchesPeriodType() throws Throwable {
        TestPeriod p1 = new TestPeriod(new int[]{0}, PeriodType.days());
        TestPeriod p8 = new TestPeriod(new int[]{0, 0, 0, 0, 0, 0, 0, 0}, PeriodType.standard());
        assertEquals(1, p1.size());
        assertEquals(8, p8.size());
    }

    // getFieldType(index): valid index returns the expected field type
    @Test
    public void testGetFieldType_validIndex_returnsType() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        assertEquals(DurationFieldType.days(), p.getFieldType(0));
    }

    // getFieldType(index): invalid index throws IndexOutOfBoundsException
    @Test
    public void testGetFieldType_invalidIndex_throws() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        try {
            p.getFieldType(5);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // getValue(index): valid index returns the stored value
    @Test
    public void testGetValue_validIndex_returnsValue() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{42}, PeriodType.days());
        assertEquals(42, p.getValue(0));
    }

    // getValue(index): invalid index throws IndexOutOfBoundsException
    @Test
    public void testGetValue_invalidIndex_throws() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{42}, PeriodType.days());
        try {
            p.getValue(5);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // toDurationFrom: adds the period to the start instant to obtain the duration
    @Test
    public void testToDurationFrom_addsPeriodToInstant() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{2}, PeriodType.days());
        DateTime start = new DateTime(0L, DateTimeZone.UTC);
        Duration d = p.toDurationFrom(start);
        assertEquals(2L * 86400000L, d.getMillis());
    }

    // toDurationTo: subtracts the period from the end instant to obtain the duration
    @Test
    public void testToDurationTo_subtractsPeriodFromInstant() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{2}, PeriodType.days());
        DateTime end = new DateTime(2L * 86400000L, DateTimeZone.UTC);
        Duration d = p.toDurationTo(end);
        assertEquals(2L * 86400000L, d.getMillis());
    }

    // setPeriod(ReadablePeriod): null resets all values to zero
    @Test
    public void testSetPeriod_null_resetsToZero() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{9}, PeriodType.days());
        p.doSetPeriod((ReadablePeriod) null);
        assertEquals(0, p.getValue(0));
    }

    // setPeriod(ReadablePeriod): non-null period copies supported field values
    @Test
    public void testSetPeriod_fromAnotherPeriod_copiesSupportedFields() throws Throwable {
        TestPeriod target = new TestPeriod(new int[]{9}, PeriodType.days());
        TestPeriod source = new TestPeriod(new int[]{3}, PeriodType.days());
        target.doSetPeriod(source);
        assertEquals(3, target.getValue(0));
    }

    // setPeriod(ReadablePeriod): unsupported nonzero field from source throws
    @Test
    public void testSetPeriod_unsupportedFieldNonZero_throws() throws Throwable {
        TestPeriod target = new TestPeriod(new int[]{0}, PeriodType.days());
        TestPeriod source = new TestPeriod(new int[]{3}, PeriodType.hours());
        try {
            target.doSetPeriod(source);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // setPeriod(8 ints): sets all supported field values correctly
    @Test
    public void testSetPeriodInts_validFields_setsValues() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0, 0, 0, 0, 0, 0, 0, 0}, PeriodType.standard());
        p.doSetPeriod(1, 2, 3, 4, 5, 6, 7, 8);
        assertEquals(1, p.getValue(0));
        assertEquals(8, p.getValue(7));
    }

    // setPeriod(8 ints): unsupported nonzero field throws
    @Test
    public void testSetPeriodInts_unsupportedFieldNonZero_throws() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.years());
        try {
            p.doSetPeriod(0, 5, 0, 0, 0, 0, 0, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // setField: supported field sets the corresponding value
    @Test
    public void testSetField_supportedField_setsValue() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        p.doSetField(DurationFieldType.days(), 7);
        assertEquals(7, p.getValue(0));
    }

    // setField: unsupported field with nonzero value throws
    @Test
    public void testSetField_unsupportedFieldNonZero_throws() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        try {
            p.doSetField(DurationFieldType.hours(), 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // setField: null field throws even when value is zero (field == null branch)
    @Test
    public void testSetField_nullFieldEvenZeroValue_throws() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        try {
            p.doSetField((DurationFieldType) null, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // setFieldInto: unsupported field with zero value leaves the provided array unchanged
    @Test
    public void testSetFieldInto_unsupportedZeroValue_noChange() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        int[] values = new int[]{5};
        p.doSetFieldInto(values, DurationFieldType.hours(), 0);
        assertEquals(5, values[0]);
    }

    // addField: supported field adds safely to the existing value
    @Test
    public void testAddField_supportedField_addsValue() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{3}, PeriodType.days());
        p.doAddField(DurationFieldType.days(), 4);
        assertEquals(7, p.getValue(0));
    }

    // addField: unsupported field with nonzero value throws
    @Test
    public void testAddField_unsupportedFieldNonZero_throws() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        try {
            p.doAddField(DurationFieldType.hours(), 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addFieldInto: adds the given value into the supplied array at the correct index
    @Test
    public void testAddFieldInto_addsToProvidedArray() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        int[] values = new int[]{10};
        p.doAddFieldInto(values, DurationFieldType.days(), 5);
        assertEquals(15, values[0]);
    }

    // mergePeriod: null period leaves existing values unchanged
    @Test
    public void testMergePeriod_null_noChange() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{9}, PeriodType.days());
        p.doMergePeriod((ReadablePeriod) null);
        assertEquals(9, p.getValue(0));
    }

    // mergePeriod: non-null period overwrites the supported field's value
    @Test
    public void testMergePeriod_overwritesSupportedField() throws Throwable {
        TestPeriod target = new TestPeriod(new int[]{9}, PeriodType.days());
        TestPeriod source = new TestPeriod(new int[]{3}, PeriodType.days());
        target.doMergePeriod(source);
        assertEquals(3, target.getValue(0));
    }

    // mergePeriodInto: returns the same array reference it was given, updated in place
    @Test
    public void testMergePeriodInto_returnsSameArrayUpdated() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        TestPeriod source = new TestPeriod(new int[]{6}, PeriodType.days());
        int[] values = new int[]{0};
        int[] result = p.doMergePeriodInto(values, source);
        assertSame(values, result);
        assertEquals(6, result[0]);
    }

    // addPeriod: null period leaves existing values unchanged
    @Test
    public void testAddPeriod_null_noChange() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{9}, PeriodType.days());
        p.doAddPeriod((ReadablePeriod) null);
        assertEquals(9, p.getValue(0));
    }

    // addPeriod: non-null period safely adds to the existing supported field value
    @Test
    public void testAddPeriod_addsSupportedField() throws Throwable {
        TestPeriod target = new TestPeriod(new int[]{9}, PeriodType.days());
        TestPeriod source = new TestPeriod(new int[]{3}, PeriodType.days());
        target.doAddPeriod(source);
        assertEquals(12, target.getValue(0));
    }

    // addPeriod: unsupported nonzero field from source throws
    @Test
    public void testAddPeriod_unsupportedFieldNonZero_throws() throws Throwable {
        TestPeriod target = new TestPeriod(new int[]{0}, PeriodType.days());
        TestPeriod source = new TestPeriod(new int[]{3}, PeriodType.hours());
        try {
            target.doAddPeriod(source);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }



    // addPeriodInto: unsupported nonzero field throws with a descriptive message
    @Test
    public void testAddPeriodInto_unsupportedFieldNonZero_throws() throws Throwable {
        TestPeriod base = new TestPeriod(new int[]{0}, PeriodType.days());
        TestPeriod delta = new TestPeriod(new int[]{3}, PeriodType.hours());
        int[] values = new int[]{0};
        try {
            base.doAddPeriodInto(values, delta);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("does not support field"));
        }
    }

    // setValue: valid index updates the stored value
    @Test
    public void testSetValue_validIndex_updatesValue() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        p.doSetValue(0, 11);
        assertEquals(11, p.getValue(0));
    }

    // setValue: invalid index throws IndexOutOfBoundsException
    @Test
    public void testSetValue_invalidIndex_throws() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0}, PeriodType.days());
        try {
            p.doSetValue(5, 1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // setValues: replaces the whole internal array while size() still reflects the period type
    @Test
    public void testSetValues_replacesArray_sizeUnaffected() throws Throwable {
        TestPeriod p = new TestPeriod(new int[]{0, 0, 0, 0, 0, 0, 0, 0}, PeriodType.standard());
        p.doSetValues(new int[]{1, 2, 3, 4, 5, 6, 7, 8});
        assertEquals(8, p.size());
        assertEquals(5, p.getValue(4));
    }
}
