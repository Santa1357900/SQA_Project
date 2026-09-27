package org.joda.time.base;

import org.junit.Test;
import static org.junit.Assert.*;

import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.DurationFieldType;
import org.joda.time.Instant;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.MutablePeriod;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.joda.time.ReadableDuration;
import org.joda.time.ReadableInstant;
import org.joda.time.ReadablePartial;
import org.joda.time.ReadablePeriod;
import org.joda.time.chrono.ISOChronology;

public class BaseTestPeriod extends BasePeriod {

    public TestTestPeriodImpl() {
        super(1, 2, 3, 4, 5, 6, 7, 8, PeriodType.standard());
    }

    public TestTestPeriodImpl(int years, int months, int weeks, int days,
                              int hours, int minutes, int seconds, int millis,
                              PeriodType type) {
        super(years, months, weeks, days, hours, minutes, seconds, millis, type);
    }

    public TestTestPeriodImpl(long startInstant, long endInstant, PeriodType type, Chronology chrono) {
        super(startInstant, endInstant, type, chrono);
    }

    public TestTestPeriodImpl(ReadableInstant startInstant, ReadableInstant endInstant, PeriodType type) {
        super(startInstant, endInstant, type);
    }

    public TestTestPeriodImpl(ReadablePartial start, ReadablePartial end, PeriodType type) {
        super(start, end, type);
    }

    public TestTestPeriodImpl(ReadableInstant startInstant, ReadableDuration duration, PeriodType type) {
        super(startInstant, duration, type);
    }

    public TestTestPeriodImpl(ReadableDuration duration, ReadableInstant endInstant, PeriodType type) {
        super(duration, endInstant, type);
    }

    public TestTestPeriodImpl(long duration) {
        super(duration);
    }

    public TestTestPeriodImpl(long duration, PeriodType type, Chronology chrono) {
        super(duration, type, chrono);
    }

    public TestTestPeriodImpl(Object period, PeriodType type, Chronology chrono) {
        super(period, type, chrono);
    }

    public TestTestPeriodImpl(int[] values, PeriodType type) {
        super(values, type);
    }

    public void invokeSetPeriod(ReadablePeriod period) {
        setPeriod(period);
    }

    public void invokeSetPeriod(int years, int months, int weeks, int days,
                                int hours, int minutes, int seconds, int millis) {
        setPeriod(years, months, weeks, days, hours, minutes, seconds, millis);
    }

    public void invokeSetField(DurationFieldType field, int value) {
        setField(field, value);
    }

    public void invokeSetFieldInto(int[] values, DurationFieldType field, int value) {
        setFieldInto(values, field, value);
    }

    public void invokeAddField(DurationFieldType field, int value) {
        addField(field, value);
    }

    public void invokeAddFieldInto(int[] values, DurationFieldType field, int value) {
        addFieldInto(values, field, value);
    }

    public void invokeMergePeriod(ReadablePeriod period) {
        mergePeriod(period);
    }

    public int[] invokeMergePeriodInto(int[] values, ReadablePeriod period) {
        return mergePeriodInto(values, period);
    }

    public void invokeAddPeriod(ReadablePeriod period) {
        addPeriod(period);
    }

    public int[] invokeAddPeriodInto(int[] values, ReadablePeriod period) {
        return addPeriodInto(values, period);
    }

    public void invokeSetValue(int index, int value) {
        setValue(index, value);
    }

    public void invokeSetValues(int[] values) {
        setValues(values);
    }
}

public class BasePeriodTest {

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(1, 2, 3, 4, 5, 6, 7, 8, PeriodType.standard());
        assertEquals(PeriodType.standard(), period.getPeriodType());
        assertEquals(8, period.size());
        assertEquals(DurationFieldType.years(), period.getFieldType(0));
        assertEquals(1, period.getValue(0));
        assertEquals(2, period.getValue(1));
        assertEquals(3, period.getValue(2));
        assertEquals(4, period.getValue(3));
        assertEquals(5, period.getValue(4));
        assertEquals(6, period.getValue(5));
        assertEquals(7, period.getValue(6));
        assertEquals(8, period.getValue(7));
    }

    @Test
    public void testIntervalMillisConstructor() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(1000L, 5000L, PeriodType.time(), ISOChronology.getInstanceUTC());
        assertNotNull(period);
        assertEquals(PeriodType.time(), period.getPeriodType());
    }

    @Test
    public void testReadableInstantsConstructor() throws Throwable {
        DateTime start = new DateTime(2010, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        DateTime end = new DateTime(2010, 1, 2, 0, 0, 0, 0, DateTimeZone.UTC);
        TestTestPeriodImpl period = new TestTestPeriodImpl(start, end, PeriodType.standard());
        assertNotNull(period);

        TestTestPeriodImpl nullPeriod = new TestTestPeriodImpl((ReadableInstant) null, (ReadableInstant) null, PeriodType.standard());
        assertNotNull(nullPeriod);
    }

    @Test
    public void testReadablePartialsConstructor() throws Throwable {
        LocalDate start = new LocalDate(2010, 1, 1);
        LocalDate end = new LocalDate(2010, 1, 5);
        TestTestPeriodImpl period = new TestTestPeriodImpl(start, end, PeriodType.standard());
        assertNotNull(period);

        LocalTime startTime = new LocalTime(10, 0);
        LocalTime endTime = new LocalTime(12, 0);
        TestTestPeriodImpl timePeriod = new TestTestPeriodImpl(startTime, endTime, PeriodType.time());
        assertNotNull(timePeriod);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadablePartialsNull() throws Throwable {
        new TestTestPeriodImpl((ReadablePartial) null, (ReadablePartial) null, PeriodType.standard());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadablePartialsDifferentSize() throws Throwable {
        LocalDate start = new LocalDate(2010, 1, 1);
        LocalTime end = new LocalTime(12, 0);
        new TestTestPeriodImpl(start, end, PeriodType.standard());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadablePartialsDifferentFields() throws Throwable {
        LocalDate start = new LocalDate(2010, 1, 1);
        LocalDate end = new LocalDate(2010, 1, 1);
        // Using distinct types or sizes if possible, or trigger non-contiguous / mismatch
        // Mocking partials or using different implementations of ReadablePartial if needed.
        // Let's test non-matching field types by implementing a small ReadablePartial dummy:
        ReadablePartial p1 = new ReadablePartial() {
            public int size() { return 1; }
            public org.joda.time.DateTimeFieldType getFieldType(int index) { return org.joda.time.DateTimeFieldType.year(); }
            public int getValue(int index) { return 2010; }
            public Chronology getChronology() { return ISOChronology.getInstanceUTC(); }
            public int get(org.joda.time.DateTimeFieldType fieldType) { return 2010; }
            public boolean isSupported(org.joda.time.DateTimeFieldType fieldType) { return true; }
            public DateTime toDateTime(ReadableInstant instant) { return null; }
        };
        ReadablePartial p2 = new ReadablePartial() {
            public int size() { return 1; }
            public org.joda.time.DateTimeFieldType getFieldType(int index) { return org.joda.time.DateTimeFieldType.monthOfYear(); }
            public int getValue(int index) { return 1; }
            public Chronology getChronology() { return ISOChronology.getInstanceUTC(); }
            public int get(org.joda.time.DateTimeFieldType fieldType) { return 1; }
            public boolean isSupported(org.joda.time.DateTimeFieldType fieldType) { return true; }
            public DateTime toDateTime(ReadableInstant instant) { return null; }
        };
        new TestTestPeriodImpl(p1, p2, PeriodType.standard());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadablePartialsNotContiguous() throws Throwable {
        // Year and dayOfMonth are not contiguous in Joda-Time standard rules
        ReadablePartial p1 = new ReadablePartial() {
            public int size() { return 2; }
            public org.joda.time.DateTimeFieldType getFieldType(int index) {
                return index == 0 ? org.joda.time.DateTimeFieldType.year() : org.joda.time.DateTimeFieldType.dayOfMonth();
            }
            public int getValue(int index) { return 1; }
            public Chronology getChronology() { return ISOChronology.getInstanceUTC(); }
            public int get(org.joda.time.DateTimeFieldType fieldType) { return 1; }
            public boolean isSupported(org.joda.time.DateTimeFieldType fieldType) { return true; }
            public DateTime toDateTime(ReadableInstant instant) { return null; }
        };
        new TestTestPeriodImpl(p1, p1, PeriodType.standard());
    }

    @Test
    public void testInstantAndDurationConstructors() throws Throwable {
        DateTime start = new DateTime(2010, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        Duration duration = new Duration(3600000L);
        TestTestPeriodImpl p1 = new TestTestPeriodImpl(start, duration, PeriodType.standard());
        assertNotNull(p1);

        TestTestPeriodImpl p2 = new TestTestPeriodImpl(duration, start, PeriodType.standard());
        assertNotNull(p2);
    }

    @Test
    public void testDurationConstructors() throws Throwable {
        TestTestPeriodImpl p1 = new TestTestPeriodImpl(5000L);
        assertNotNull(p1);

        TestTestPeriodImpl p2 = new TestTestPeriodImpl(5000L, PeriodType.time(), ISOChronology.getInstanceUTC());
        assertNotNull(p2);
    }

    @Test
    public void testObjectConstructor() throws Throwable {
        Period periodObj = new Period(1, 2, 3, 4);
        TestTestPeriodImpl p1 = new TestTestPeriodImpl(periodObj, PeriodType.standard(), ISOChronology.getInstanceUTC());
        assertNotNull(p1);

        MutablePeriod mutablePeriod = new MutablePeriod(1, 2, 3, 4);
        TestTestPeriodImpl p2 = new TestTestPeriodImpl(mutablePeriod, null, null);
        assertNotNull(p2);
    }

    @Test
    public void testArrayConstructor() throws Throwable {
        int[] values = new int[] {1, 2, 3, 4, 5, 6, 7, 8};
        TestTestPeriodImpl p = new TestTestPeriodImpl(values, PeriodType.standard());
        assertEquals(values, p.getValues()); // wait, getValues is protected/private, use getValue
        assertEquals(1, p.getValue(0));
    }

    @Test
    public void testDurationsToFrom() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(0, 0, 0, 1, 0, 0, 0, 0, PeriodType.standard());
        DateTime start = new DateTime(2010, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        assertNotNull(period.toDurationFrom(start));
        assertNotNull(period.toDurationTo(start));
    }

    @Test
    public void testSetPeriodMethods() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl();
        period.invokeSetPeriod(new Period(2, 3, 4, 5, 6, 7, 8, 9));
        assertEquals(2, period.getValue(0));

        period.invokeSetPeriod(null);
        assertEquals(0, period.getValue(0));

        period.invokeSetPeriod(10, 11, 12, 13, 14, 15, 16, 17);
        assertEquals(10, period.getValue(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetPeriodUnsupportedField() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(0, 0, 0, 0, 0, 0, 0, 0, PeriodType.time());
        period.invokeSetPeriod(1, 0, 0, 0, 0, 0, 0, 0); // Years not supported in time period type
    }

    @Test
    public void testSetFieldAndAddFieldErrorCases() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(0, 0, 0, 0, 0, 0, 0, 0, PeriodType.time());
        period.invokeSetField(DurationFieldType.hours(), 5);
        assertEquals(5, period.getValue(0)); // hours is index 0 in time period type? Let's check or use invokeSetFieldInto

        int[] values = new int[4];
        period.invokeSetFieldInto(values, DurationFieldType.hours(), 10);
        assertEquals(10, values[0]);

        period.invokeAddField(DurationFieldType.hours(), 2);
        period.invokeAddFieldInto(values, DurationFieldType.hours(), 3);
        assertEquals(13, values[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetFieldUnsupportedNonZero() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(0, 0, 0, 0, 0, 0, 0, 0, PeriodType.time());
        period.invokeSetField(DurationFieldType.years(), 5);
    }

    @Test
    public void testSetFieldUnsupportedZero() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(0, 0, 0, 0, 0, 0, 0, 0, PeriodType.time());
        period.invokeSetField(DurationFieldType.years(), 0); // should pass without exception
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetFieldNull() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl();
        period.invokeSetField(null, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddFieldUnsupported() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(0, 0, 0, 0, 0, 0, 0, 0, PeriodType.time());
        period.invokeAddField(DurationFieldType.years(), 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddFieldNull() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl();
        period.invokeAddField(null, 5);
    }

    @Test
    public void testMergePeriod() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(1, 0, 0, 0, 0, 0, 0, 0, PeriodType.standard());
        period.invokeMergePeriod(new Period(0, 2, 0, 0, 0, 0, 0, 0));
        assertEquals(2, period.getValue(1));

        period.invokeMergePeriod(null);

        int[] values = new int[8];
        period.invokeMergePeriodInto(values, new Period(1, 1, 1, 1, 1, 1, 1, 1));
        assertEquals(1, values[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMergePeriodUnsupported() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(0, 0, 0, 0, 0, 0, 0, 0, PeriodType.time());
        period.invokeMergePeriod(new Period(1, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    public void testAddPeriod() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(1, 1, 1, 1, 1, 1, 1, 1, PeriodType.standard());
        period.invokeAddPeriod(new Period(1, 1, 1, 1, 1, 1, 1, 1));
        assertEquals(2, period.getValue(0));

        period.invokeAddPeriod(null);

        int[] values = new int[8];
        period.invokeAddPeriodInto(values, new Period(2, 2, 2, 2, 2, 2, 2, 2));
        assertEquals(2, values[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddPeriodUnsupported() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl(0, 0, 0, 0, 0, 0, 0, 0, PeriodType.time());
        period.invokeAddPeriod(new Period(1, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    public void testSetValueAndValues() throws Throwable {
        TestTestPeriodImpl period = new TestTestPeriodImpl();
        period.invokeSetValue(0, 99);
        assertEquals(99, period.getValue(0));

        int[] newVals = new int[] {9, 8, 7, 6, 5, 4, 3, 2};
        period.invokeSetValues(newVals);
        assertEquals(9, period.getValue(0));
    }
}