package org.joda.time;

import java.util.Locale;

import org.joda.time.chrono.ISOChronology;

import org.junit.Test;
import static org.junit.Assert.*;

public class PartialClaudeTest {

    // covers Partial() no-arg constructor delegating to Partial(Chronology)
    @Test
    public void testDefaultConstructor_sizeZeroAndIsoUtcChronology() throws Throwable {
        Partial p = new Partial();
        assertEquals(0, p.size());
        assertSame(ISOChronology.getInstanceUTC(), p.getChronology());
    }

    // covers Partial(Chronology) with null chronology branch
    @Test
    public void testConstructorChronology_nullArgument_usesIsoUtc() throws Throwable {
        Partial p = new Partial((Chronology) null);
        assertSame(ISOChronology.getInstanceUTC(), p.getChronology());
    }

    // covers normal successful path of Partial(DateTimeFieldType, int)
    @Test
    public void testConstructorTypeValue_validInput_sizeOneWithCorrectValue() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 1999);
        assertEquals(1, p.size());
        assertEquals(DateTimeFieldType.year(), p.getFieldType(0));
        assertEquals(1999, p.getValue(0));
    }

    // covers null type check in Partial(DateTimeFieldType, int)
    @Test
    public void testConstructorTypeValue_nullFieldType_throwsIllegalArgumentException() throws Throwable {
        try {
            new Partial((DateTimeFieldType) null, 5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers chronology.validate() throwing for out-of-range value
    @Test
    public void testConstructorTypeValue_invalidValue_throwsIllegalArgumentException() throws Throwable {
        try {
            new Partial(DateTimeFieldType.monthOfYear(), 13);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers Partial(DateTimeFieldType, int, Chronology) with non-null chronology
    @Test
    public void testConstructorTypeValueChronology_explicitChronology_retained() throws Throwable {
        Chronology c = ISOChronology.getInstanceUTC();
        Partial p = new Partial(DateTimeFieldType.year(), 2000, c);
        assertSame(c, p.getChronology());
    }

    // covers null types array and null values array checks
    @Test
    public void testConstructorArrays_nullArguments_throwIllegalArgumentException() throws Throwable {
        try {
            new Partial((DateTimeFieldType[]) null, new int[]{1});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new Partial(new DateTimeFieldType[]{DateTimeFieldType.year()}, (int[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers values.length != types.length check
    @Test
    public void testConstructorArrays_mismatchedLengths_throwsIllegalArgumentException() throws Throwable {
        try {
            new Partial(new DateTimeFieldType[]{DateTimeFieldType.year()}, new int[]{1, 2});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers types.length == 0 early-return branch
    @Test
    public void testConstructorArrays_emptyArrays_sizeZero() throws Throwable {
        Partial p = new Partial(new DateTimeFieldType[0], new int[0]);
        assertEquals(0, p.size());
    }

    // covers per-element null check in types array
    @Test
    public void testConstructorArrays_nullElementInTypes_throwsIllegalArgumentException() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[]{DateTimeFieldType.year(), null};
        try {
            new Partial(types, new int[]{2000, 5});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers compare < 0 ordering violation branch (smallest-to-largest supplied)
    @Test
    public void testConstructorArrays_smallestToLargestOrder_throwsIllegalArgumentException() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[]{DateTimeFieldType.dayOfMonth(), DateTimeFieldType.year()};
        try {
            new Partial(types, new int[]{15, 2000});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers compare == 0 duplicate-detection branch (both ranges null)
    @Test
    public void testConstructorArrays_duplicateFieldType_throwsIllegalArgumentException() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[]{DateTimeFieldType.year(), DateTimeFieldType.year()};
        try {
            new Partial(types, new int[]{2000, 2001});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers successful multi-field construction path, largest to smallest
    @Test
    public void testConstructorArrays_validLargestToSmallestOrder_correctFieldsAndValues() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[]{
                DateTimeFieldType.year(), DateTimeFieldType.monthOfYear(), DateTimeFieldType.dayOfMonth()};
        Partial p = new Partial(types, new int[]{2023, 5, 15});
        assertEquals(3, p.size());
        assertEquals(2023, p.getValue(0));
        assertEquals(5, p.getValue(1));
        assertEquals(15, p.getValue(2));
    }

    // covers null partial check in Partial(ReadablePartial)
    @Test
    public void testConstructorReadablePartial_null_throwsIllegalArgumentException() throws Throwable {
        try {
            new Partial((ReadablePartial) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers successful copy-constructor path
    @Test
    public void testConstructorReadablePartial_validSource_copiesFieldsAndValues() throws Throwable {
        Partial source = new Partial(DateTimeFieldType.year(), 2010);
        Partial copy = new Partial((ReadablePartial) source);
        assertEquals(1, copy.size());
        assertEquals(DateTimeFieldType.year(), copy.getFieldType(0));
        assertEquals(2010, copy.getValue(0));
    }

    // covers IndexOutOfBoundsException propagation from getFieldType(int) and getValue(int)
    @Test
    public void testIndexAccessors_outOfBounds_throwIndexOutOfBoundsException() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2000);
        try {
            p.getFieldType(1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
        try {
            p.getValue(-1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // covers clone() defensive copies in getFieldTypes() and getValues()
    @Test
    public void testGetFieldTypesAndGetValues_returnDefensiveCopies() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2000);
        DateTimeFieldType[] types = p.getFieldTypes();
        types[0] = DateTimeFieldType.monthOfYear();
        assertEquals(DateTimeFieldType.year(), p.getFieldType(0));
        int[] values = p.getValues();
        values[0] = 1;
        assertEquals(2000, p.getValue(0));
    }

    // covers newChronology == getChronology() identity-returns-this branch
    @Test
    public void testWithChronologyRetainFields_nullEqualsCurrentChronology_returnsSameInstance() throws Throwable {
        Partial p = new Partial();
        assertSame(p, p.withChronologyRetainFields(null));
    }

    // covers with() inserting a new field at the correct largest-to-smallest position
    @Test
    public void testWith_newField_insertedInCorrectOrder() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[]{DateTimeFieldType.monthOfYear(), DateTimeFieldType.dayOfMonth()};
        Partial p = new Partial(types, new int[]{5, 15});
        Partial p2 = p.with(DateTimeFieldType.year(), 2023);
        assertEquals(3, p2.size());
        assertEquals(DateTimeFieldType.year(), p2.getFieldType(0));
        assertEquals(2023, p2.getValue(0));
        assertEquals(5, p2.getValue(1));
    }

    // covers with() same-value short-circuit returning this
    @Test
    public void testWith_sameValue_returnsSameInstance() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2000);
        assertSame(p, p.with(DateTimeFieldType.year(), 2000));
    }

    // covers with() updating an existing field to a different value
    @Test
    public void testWith_existingFieldDifferentValue_updatesValue() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2000);
        Partial p2 = p.with(DateTimeFieldType.year(), 2005);
        assertEquals(2005, p2.getValue(0));
        assertEquals(2000, p.getValue(0));
    }

    // covers null fieldType check in with()
    @Test
    public void testWith_nullFieldType_throwsIllegalArgumentException() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2000);
        try {
            p.with((DateTimeFieldType) null, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // bug hunt: with() must not throw when combining a field whose range is null (year)
    // with one whose range is non-null (yearOfEra); per contract the field is simply added
    @Test
    public void testWith_yearThenAddYearOfEra_noExceptionAndCorrectValues() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 1999);
        Partial p2 = p.with(DateTimeFieldType.yearOfEra(), 2005);
        assertEquals(2, p2.size());
        assertEquals(1999, p2.get(DateTimeFieldType.year()));
        assertEquals(2005, p2.get(DateTimeFieldType.yearOfEra()));
    }

    // bug hunt: same scenario with arguments reversed, hitting the other null-range access
    @Test
    public void testWith_yearOfEraThenAddYear_noExceptionAndCorrectValues() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.yearOfEra(), 2005);
        Partial p2 = p.with(DateTimeFieldType.year(), 1999);
        assertEquals(2, p2.size());
        assertEquals(2005, p2.get(DateTimeFieldType.yearOfEra()));
        assertEquals(1999, p2.get(DateTimeFieldType.year()));
    }

    // covers without() removing a present field
    @Test
    public void testWithout_existingField_removed() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[]{DateTimeFieldType.year(), DateTimeFieldType.monthOfYear()};
        Partial p = new Partial(types, new int[]{2000, 5});
        Partial p2 = p.without(DateTimeFieldType.monthOfYear());
        assertEquals(1, p2.size());
        assertEquals(DateTimeFieldType.year(), p2.getFieldType(0));
    }

    // covers without() returning this when field is not present
    @Test
    public void testWithout_nonExistingField_returnsSameInstance() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2000);
        assertSame(p, p.without(DateTimeFieldType.monthOfYear()));
    }

    // covers withField() throwing for a field not supported by this partial
    @Test
    public void testWithField_unsupportedField_throwsIllegalArgumentException() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2000);
        try {
            p.withField(DateTimeFieldType.monthOfYear(), 5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers withField() successfully updating a supported field
    @Test
    public void testWithField_validUpdate_valueChanged() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2000);
        Partial p2 = p.withField(DateTimeFieldType.year(), 2010);
        assertEquals(2010, p2.getValue(0));
    }

    // covers withFieldAdded() amount==0 returning this
    @Test
    public void testWithFieldAdded_amountZero_returnsSameInstance() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.monthOfYear(), 5);
        assertSame(p, p.withFieldAdded(DurationFieldType.months(), 0));
    }

    // covers withFieldAdded() adding a non-zero amount to a supported field
    @Test
    public void testWithFieldAdded_validAmount_valueIncreased() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.monthOfYear(), 5);
        Partial p2 = p.withFieldAdded(DurationFieldType.months(), 2);
        assertEquals(7, p2.getValue(0));
    }

    // covers withFieldAdded() throwing for an unsupported duration field type
    @Test
    public void testWithFieldAdded_unsupportedField_throwsIllegalArgumentException() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2000);
        try {
            p.withFieldAdded(DurationFieldType.months(), 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers withFieldAddWrapped() wrapping around the field's own maximum
    @Test
    public void testWithFieldAddWrapped_wrapsAroundMax() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.monthOfYear(), 11);
        Partial p2 = p.withFieldAddWrapped(DurationFieldType.months(), 3);
        assertEquals(2, p2.getValue(0));
    }

    // covers withPeriodAdded() period==null and scalar==0 short-circuit branches
    @Test
    public void testWithPeriodAdded_nullPeriodOrZeroScalar_returnsSameInstance() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.monthOfYear(), 5);
        assertSame(p, p.withPeriodAdded(null, 1));
        Period period = new Period(0, 1, 0, 0, 0, 0, 0, 0);
        assertSame(p, p.withPeriodAdded(period, 0));
    }

    // covers withPeriodAdded() updating matched fields and skipping unsupported ones
    @Test
    public void testWithPeriodAdded_validPeriod_fieldsUpdatedIgnoringUnsupported() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[]{DateTimeFieldType.year(), DateTimeFieldType.monthOfYear()};
        Partial p = new Partial(types, new int[]{2000, 5});
        Period period = new Period(1, 2, 0, 3, 0, 0, 0, 0);
        Partial p2 = p.withPeriodAdded(period, 1);
        assertEquals(2001, p2.getValue(0));
        assertEquals(7, p2.getValue(1));
    }

    // covers plus() scalar=1 and minus() scalar=-1 delegation to withPeriodAdded
    @Test
    public void testPlusAndMinus_delegateToWithPeriodAddedWithCorrectScalar() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.monthOfYear(), 5);
        Period period = new Period(0, 2, 0, 0, 0, 0, 0, 0);
        assertEquals(7, p.plus(period).getValue(0));
        assertEquals(3, p.minus(period).getValue(0));
    }

    // covers property() throwing for a field not supported by this partial
    @Test
    public void testProperty_unsupportedField_throwsIllegalArgumentException() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2000);
        try {
            p.property(DateTimeFieldType.monthOfYear());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers isMatch(ReadableInstant) true and false branches
    @Test
    public void testIsMatchInstant_matchingAndNonMatching() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[]{
                DateTimeFieldType.year(), DateTimeFieldType.monthOfYear(), DateTimeFieldType.dayOfMonth()};
        Partial p = new Partial(types, new int[]{2023, 5, 15});
        DateTime matching = new DateTime(2023, 5, 15, 10, 30, 0, 0, DateTimeZone.UTC);
        DateTime nonMatching = new DateTime(2023, 5, 16, 10, 30, 0, 0, DateTimeZone.UTC);
        assertTrue(p.isMatch(matching));
        assertFalse(p.isMatch(nonMatching));
    }

    // covers null-check branch of isMatch(ReadablePartial)
    @Test
    public void testIsMatchPartial_null_throwsIllegalArgumentException() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2000);
        try {
            p.isMatch((ReadablePartial) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers isMatch(ReadablePartial) true and false comparison branches
    @Test
    public void testIsMatchPartial_matchingAndNonMatching() throws Throwable {
        Partial p1 = new Partial(DateTimeFieldType.year(), 2000);
        Partial p2 = new Partial(DateTimeFieldType.year(), 2000);
        Partial p3 = new Partial(DateTimeFieldType.year(), 2001);
        assertTrue(p1.isMatch(p2));
        assertFalse(p1.isMatch(p3));
    }

    // covers getFormatter() size==0 null branch and non-empty resolved-formatter branch
    @Test
    public void testGetFormatter_emptyPartialReturnsNull_fieldsPartialReturnsNonNull() throws Throwable {
        Partial empty = new Partial();
        assertNull(empty.getFormatter());
        DateTimeFieldType[] types = new DateTimeFieldType[]{
                DateTimeFieldType.year(), DateTimeFieldType.monthOfYear(), DateTimeFieldType.dayOfMonth()};
        Partial dateParts = new Partial(types, new int[]{2023, 5, 15});
        assertNotNull(dateParts.getFormatter());
    }

    // covers toString() using the ISO formatter and toStringList() field listing
    @Test
    public void testToString_isoFormat_andToStringList_bracketFormat() throws Throwable {
        DateTimeFieldType[] types = new DateTimeFieldType[]{
                DateTimeFieldType.year(), DateTimeFieldType.monthOfYear(), DateTimeFieldType.dayOfMonth()};
        Partial dateParts = new Partial(types, new int[]{2023, 5, 15});
        assertEquals("2023-05-15", dateParts.toString());
        Partial single = new Partial(DateTimeFieldType.year(), 2023);
        assertEquals("[year=2023]", single.toStringList());
    }

    // covers toString(String) null-pattern branch delegating to toString(), and pattern branch
    @Test
    public void testToStringPattern_nullUsesToString_nonNullUsesPattern() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2023);
        assertEquals(p.toString(), p.toString((String) null));
        assertEquals("2023", p.toString("yyyy"));
    }

    // covers toString(String, Locale) formatting branch
    @Test
    public void testToStringPatternLocale_appliesLocaleFormatting() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.year(), 2023);
        assertEquals("2023", p.toString("yyyy", Locale.ENGLISH));
    }

    // covers Property.get(), getPartial(), addToCopy() and addWrapFieldToCopy()
    @Test
    public void testPropertyAddToCopyAndAddWrapFieldToCopy() throws Throwable {
        Partial p1 = new Partial(DateTimeFieldType.monthOfYear(), 5);
        Partial.Property prop1 = p1.property(DateTimeFieldType.monthOfYear());
        assertEquals(5, prop1.get());
        assertSame(p1, prop1.getPartial());
        assertEquals(7, prop1.addToCopy(2).getValue(0));
        Partial p2 = new Partial(DateTimeFieldType.monthOfYear(), 11);
        assertEquals(2, p2.property(DateTimeFieldType.monthOfYear()).addWrapFieldToCopy(3).getValue(0));
    }

    // covers Property.setCopy(int), setCopy(String) numeric parsing, withMaximumValue(), withMinimumValue()
    @Test
    public void testPropertySetCopyAndBoundaryValues() throws Throwable {
        Partial p = new Partial(DateTimeFieldType.monthOfYear(), 5);
        Partial.Property prop = p.property(DateTimeFieldType.monthOfYear());
        assertEquals(9, prop.setCopy(9).getValue(0));
        assertEquals(12, prop.setCopy("12").getValue(0));
        assertEquals(12, prop.withMaximumValue().getValue(0));
        assertEquals(1, prop.withMinimumValue().getValue(0));
    }
}
