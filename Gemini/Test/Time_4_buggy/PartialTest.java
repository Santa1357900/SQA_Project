package org.joda.time;

import static org.junit.Assert.*;

import java.util.Locale;

import org.junit.Test;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.format.DateTimeFormatter;

public class PartialTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        Partial p = new Partial();
        assertEquals(0, p.size());
        assertNotNull(p.getChronology());
        assertNull(p.getFormatter());
        assertEquals("[]", p.toStringList());
        assertEquals("[]", p.toString());
    }

    @Test
    public void testChronologyConstructor() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        Partial p = new Partial(chrono);
        assertEquals(0, p.size());
        assertEquals(chrono, p.getChronology());
    }

    @Test
    public void testTypeAndValueConstructor() throws Throwable {
        DateTimeFieldType type = DateTimeFieldType.year();
        Partial p = new Partial(type, 2020);
        assertEquals(1, p.size());
        assertEquals(type, p.getFieldType(0));
        assertEquals(2020, p.getValue(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTypeAndValueConstructorNullType() throws Throwable {
        new Partial((DateTimeFieldType) null, 2020);
    }

    @Test
    public void testTypeValueChronologyConstructor() throws Throwable {
        DateTimeFieldType type = DateTimeFieldType.monthOfYear();
        Chronology chrono = ISOChronology.getInstanceUTC();
        Partial p = new Partial(type, 5, chrono);
        assertEquals(1, p.size());
        assertEquals(5, p.getValue(0));
        assertEquals(chrono, p.getChronology());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTypeAndValueInvalidValue() throws Throwable {
        new Partial(DateTimeFieldType.monthOfYear(), 13);
    }

    @Test
    public void testArraysConstructor() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[] {
            DateTimeFieldType.year(),
            DateTimeFieldType.monthOfYear()
        };
        int[] values = new int[] { 2020, 6 };
        Partial p = new Partial(types, values);
        assertEquals(2, p.size());
        assertEquals(2020, p.getValue(0));
        assertEquals(6, p.getValue(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArraysConstructorNullTypes() throws Throwable {
        new Partial((DateTimeFieldType[]) null, new int[] { 2020 });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArraysConstructorNullValues() throws Throwable {
        new Partial(new DateTimeFieldType[] { DateTimeFieldType.year() }, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArraysConstructorMismatchedLength() throws Throwable {
        new Partial(new DateTimeFieldType[] { DateTimeFieldType.year() }, new int[] { 2020, 6 });
    }

    @Test
    public void testArraysConstructorEmpty() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[0];
        int[] values = new int[0];
        Partial p = new Partial(types, values);
        assertEquals(0, p.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArraysConstructorNullElement() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[] { null };
        int[] values = new int[] { 2020 };
        new Partial(types, values);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArraysConstructorWrongOrder() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[] {
            DateTimeFieldType.monthOfYear(),
            DateTimeFieldType.year()
        };
        int[] values = new int[] { 6, 2020 };
        new Partial(types, values);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArraysConstructorDuplicate() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[] {
            DateTimeFieldType.year(),
            DateTimeFieldType.year()
        };
        int[] values = new int[] { 2020, 2020 };
        new Partial(types, values);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArraysConstructorSameUnitDifferentRangeWrongOrder() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[] {
            DateTimeFieldType.dayOfMonth(),
            DateTimeFieldType.dayOfWeek()
        };
        int[] values = new int[] { 1, 1 };
        new Partial(types, values);
    }

    @Test
    public void testReadablePartialConstructor() throws Throwable {
        Partial original = new Partial(DateTimeFieldType.year(), 2020);
        Partial copy = new Partial(original);
        assertEquals(1, copy.size());
        assertEquals(2020, copy.getValue(0));
        assertEquals(original.getChronology(), copy.getChronology());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadablePartialConstructorNull() throws Throwable {
        new Partial((ReadablePartial) null);
    }

    @Test
    public void testGetFieldTypesAndValues() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2020);
        DateTimeFieldType[] types = p.getFieldTypes();
        int[] values = p.getValues();
        assertEquals(1, types.length);
        assertEquals(1, values.length);
        assertEquals(2020, values[0]);
    }

    @Test
    public void testWithChronologyRetainFields() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2020);
        Chronology newChronology = ISOChronology.getInstanceUTC();
        Partial updated = p.withChronologyRetainFields(newChronology);
        assertEquals(newChronology, updated.getChronology());
        assertEquals(2020, updated.getValue(0));

        // Same chronology branch
        assertSame(p, p.withChronologyRetainFields(p.getChronology()));
    }

    @Test
    public void testWithFieldAddAndRemove() throws Throwable {
        Partial p = new Partial();
        Partial p2 = p.with(DateTimeFieldType.year(), 2020);
        assertEquals(1, p2.size());
        assertEquals(2020, p2.getValue(0));

        // Existing value unchanged
        assertSame(p2, p2.with(DateTimeFieldType.year(), 2020));

        // Update existing field
        Partial p3 = p2.with(DateTimeFieldType.year(), 2021);
        assertEquals(2021, p3.getValue(0));

        // Add smaller field (monthOfYear) to year
        Partial p4 = p3.with(DateTimeFieldType.monthOfYear(), 6);
        assertEquals(2, p4.size());
        assertEquals(DateTimeFieldType.year(), p4.getFieldType(0));
        assertEquals(DateTimeFieldType.monthOfYear(), p4.getFieldType(1));

        // Without field
        Partial p5 = p4.without(DateTimeFieldType.monthOfYear());
        assertEquals(1, p5.size());
        assertEquals(DateTimeFieldType.year(), p5.getFieldType(0));

        // Without non-existent field
        assertSame(p5, p5.without(DateTimeFieldType.dayOfMonth()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithNullFieldType() throws Throwable {
        Partial p = new Partial();
        p.with(null, 2020);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithFieldUnsupported() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2020);
        p.withField(DateTimeFieldType.monthOfYear(), 5);
    }

    @Test
    public void testWithFieldSupportedSameValue() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2020);
        assertSame(p, p.withField(DateTimeFieldType.year(), 2020));
    }

    @Test
    public void testWithFieldSupportedNewValue() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2020);
        Partial updated = p.withField(DateTimeFieldType.year(), 2021);
        assertEquals(2021, updated.getValue(0));
    }

    @Test
    public void testWithFieldAdded() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2020);
        assertSame(p, p.withFieldAdded(DurationFieldType.years(), 0));

        Partial updated = p.withFieldAdded(DurationFieldType.years(), 5);
        assertEquals(2025, updated.getValue(0));
    }

    @Test
    public void testWithFieldAddWrapped() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.monthOfYear(), 12);
        assertSame(p, p.withFieldAddWrapped(DurationFieldType.months(), 0));

        Partial updated = p.withFieldAddWrapped(DurationFieldType.months(), 1);
        assertEquals(1, updated.getValue(0));
    }

    @Test
    public void testWithPeriodAddedAndPlusMinus() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2020);
        assertSame(p, p.withPeriodAdded(null, 1));
        assertSame(p, p.withPeriodAdded(Period.years(1), 0));

        Period period = Period.years(2);
        Partial updated = p.withPeriodAdded(period, 1);
        assertEquals(2022, updated.getValue(0));

        Partial plusPartial = p.plus(period);
        assertEquals(2022, plusPartial.getValue(0));

        Partial minusPartial = p.minus(period);
        assertEquals(2018, minusPartial.getValue(0));
    }

    @Test
    public void testPropertyMethods() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2020);
        Partial.Property prop = p.property(DateTimeFieldType.year());
        assertNotNull(prop.getField());
        assertEquals(p, prop.getPartial());
        assertEquals(p, prop.getReadablePartial());
        assertEquals(2020, prop.get());

        Partial addedCopy = prop.addToCopy(5);
        assertEquals(2025, addedCopy.getValue(0));

        Partial wrappedCopy = prop.addWrapFieldToCopy(1);
        assertEquals(2021, wrappedCopy.getValue(0));

        Partial setCopy = prop.setCopy(2030);
        assertEquals(2030, setCopy.getValue(0));

        Partial textSetCopy = prop.setCopy("2040");
        assertEquals(2040, textSetCopy.getValue(0));

        Partial maxCopy = prop.withMaximumValue();
        assertTrue(maxCopy.getValue(0) > 0);

        Partial minCopy = prop.withMinimumValue();
        assertTrue(minCopy.getValue(0) <= maxCopy.getValue(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPropertySetTextWithLocale() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2020);
        Partial.Property prop = p.property(DateTimeFieldType.year());
        prop.setCopy("invalid", Locale.ENGLISH);
    }

    @Test
    public void testIsMatchInstant() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2020);
        Instant instant = new Instant(new DateTime(2020, 1, 1, 0, 0, ISOChronology.getInstanceUTC()).getMillis());
        assertTrue(p.isMatch(instant));

        Instant nonMatch = new Instant(new DateTime(2021, 1, 1, 0, 0, ISOChronology.getInstanceUTC()).getMillis());
        assertFalse(p.isMatch(nonMatch));
        
        // Null instant means now, just execute to ensure no crash
        p.isMatch((ReadableInstant) null);
    }

    @Test
    public void testIsMatchPartial() throws Throwable {
        Partial p1 = new Partial(DateTimeFieldType.year(), 2020);
        Partial p2 = new Partial(DateTimeFieldType.year(), 2020);
        assertTrue(p1.isMatch(p2));

        Partial p3 = new Partial(DateTimeFieldType.year(), 2021);
        assertFalse(p1.isMatch(p3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsMatchNullPartial() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2020);
        p.isMatch((ReadablePartial) null);
    }

    @Test
    public void testToStringFormatting() throws Throwable {
        Partial pEmpty = new Partial();
        assertNull(pEmpty.getFormatter());
        assertEquals("[]", pEmpty.toString());

        Partial pYear = new Partial(DateTimeFieldType.year(), 2020);
        assertNotNull(pYear.toString());
        assertNotNull(pYear.toString("yyyy"));
        assertNotNull(pYear.toString("yyyy", Locale.ENGLISH));

        assertNotNull(pYear.toString(null));
        assertNotNull(pYear.toString(null, Locale.ENGLISH));
    }

    @Test
    public void testToStringListFallback() throws Throwable {
        // Create overlapping or non-standard partial that might yield null formatter[1]
        DateTimeFieldType[] types = new DateTimeFieldType[] {
            DateTimeFieldType.dayOfWeek(),
            DateTimeFieldType.dayOfMonth()
        };
        int[] values = new int[] { 1, 1 };
        Partial p = new Partial(types, values);
        assertNotNull(p.toStringList());
    }
}