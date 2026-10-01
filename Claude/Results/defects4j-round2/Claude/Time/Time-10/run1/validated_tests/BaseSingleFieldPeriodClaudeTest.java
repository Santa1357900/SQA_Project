package org.joda.time.base;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.DurationFieldType;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.MutablePeriod;
import org.joda.time.Period;
import org.joda.time.PeriodType;

public class BaseSingleFieldPeriodClaudeTest {

    // concrete subclass implementing the two abstract methods, field type = days
    private static class TestPeriod extends BaseSingleFieldPeriod {
        protected TestPeriod(int period) {
            super(period);
        }
        public DurationFieldType getFieldType() {
            return DurationFieldType.days();
        }
        public PeriodType getPeriodType() {
            return PeriodType.days();
        }
    }

    // a different concrete subclass, field type = hours, used for cross-class checks
    private static class TestPeriod2 extends BaseSingleFieldPeriod {
        protected TestPeriod2(int period) {
            super(period);
        }
        public DurationFieldType getFieldType() {
            return DurationFieldType.hours();
        }
        public PeriodType getPeriodType() {
            return PeriodType.hours();
        }
    }

    //----------------------------------------------------------------
    // between(ReadableInstant, ReadableInstant, DurationFieldType)
    //----------------------------------------------------------------

    // null start must throw IllegalArgumentException
    @Test
    public void testBetweenInstants_nullStart_throwsIAE() throws Throwable {
        DateTime end = new DateTime(2004, 6, 9, 0, 0, 0, 0, DateTimeZone.UTC);
        try {
            BaseSingleFieldPeriod.between(null, end, DurationFieldType.days());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // null end must throw IllegalArgumentException
    @Test
    public void testBetweenInstants_nullEnd_throwsIAE() throws Throwable {
        DateTime start = new DateTime(2004, 6, 9, 0, 0, 0, 0, DateTimeZone.UTC);
        try {
            BaseSingleFieldPeriod.between(start, null, DurationFieldType.days());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // normal path: end is 3 whole days after start
    @Test
    public void testBetweenInstants_normal_returnsWholeUnits() throws Throwable {
        DateTime start = new DateTime(2004, 6, 9, 0, 0, 0, 0, DateTimeZone.UTC);
        DateTime end = new DateTime(2004, 6, 12, 0, 0, 0, 0, DateTimeZone.UTC);
        int result = BaseSingleFieldPeriod.between(start, end, DurationFieldType.days());
        assertEquals(3, result);
    }

    // sign check: swapping start/end must negate the result
    @Test
    public void testBetweenInstants_swapped_returnsNegative() throws Throwable {
        DateTime start = new DateTime(2004, 6, 9, 0, 0, 0, 0, DateTimeZone.UTC);
        DateTime end = new DateTime(2004, 6, 12, 0, 0, 0, 0, DateTimeZone.UTC);
        int result = BaseSingleFieldPeriod.between(end, start, DurationFieldType.days());
        assertEquals(-3, result);
    }

    //----------------------------------------------------------------
    // between(ReadablePartial, ReadablePartial, ReadablePeriod)
    //----------------------------------------------------------------

    // null start must throw IllegalArgumentException
    @Test
    public void testBetweenPartials_nullStart_throwsIAE() throws Throwable {
        LocalDate end = new LocalDate(2004, 6, 9);
        try {
            BaseSingleFieldPeriod.between(null, end, new TestPeriod(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // null end must throw IllegalArgumentException
    @Test
    public void testBetweenPartials_nullEnd_throwsIAE() throws Throwable {
        LocalDate start = new LocalDate(2004, 6, 9);
        try {
            BaseSingleFieldPeriod.between(start, null, new TestPeriod(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // different size of fields between the two partials must throw IllegalArgumentException
    @Test
    public void testBetweenPartials_differentSize_throwsIAE() throws Throwable {
        LocalDate start = new LocalDate(2004, 6, 9);
        LocalTime end = new LocalTime(0, 0, 0, 0);
        try {
            BaseSingleFieldPeriod.between(start, end, new TestPeriod(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // normal path: 3 whole days between the two LocalDate partials
    @Test
    public void testBetweenPartials_normal_returnsWholeUnits() throws Throwable {
        LocalDate start = new LocalDate(2004, 6, 9);
        LocalDate end = new LocalDate(2004, 6, 12);
        int result = BaseSingleFieldPeriod.between(start, end, new TestPeriod(0));
        assertEquals(3, result);
    }

    // sign check: swapping start/end must negate the result
    @Test
    public void testBetweenPartials_swapped_returnsNegative() throws Throwable {
        LocalDate start = new LocalDate(2004, 6, 9);
        LocalDate end = new LocalDate(2004, 6, 12);
        int result = BaseSingleFieldPeriod.between(end, start, new TestPeriod(0));
        assertEquals(-3, result);
    }

    //----------------------------------------------------------------
    // standardPeriodIn(ReadablePeriod, long)
    //----------------------------------------------------------------

    // null period must return zero
    @Test
    public void testStandardPeriodIn_nullPeriod_returnsZero() throws Throwable {
        int result = BaseSingleFieldPeriod.standardPeriodIn(null, 3600000L);
        assertEquals(0, result);
    }

    // precise field (hours) converts correctly to the requested unit
    @Test
    public void testStandardPeriodIn_preciseField_convertsCorrectly() throws Throwable {
        Period period = new Period(0, 0, 0, 0, 2, 0, 0, 0);
        int result = BaseSingleFieldPeriod.standardPeriodIn(period, 3600000L);
        assertEquals(2, result);
    }

    // imprecise field (months) must throw IllegalArgumentException
    @Test
    public void testStandardPeriodIn_impreciseField_throwsIAE() throws Throwable {
        Period period = new Period(0, 1, 0, 0, 0, 0, 0, 0);
        try {
            BaseSingleFieldPeriod.standardPeriodIn(period, 3600000L);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    //----------------------------------------------------------------
    // constructor / getValue / setValue
    //----------------------------------------------------------------

    // constructor stores the given value, retrievable via getValue
    @Test
    public void testConstructorAndGetValue_positive() throws Throwable {
        TestPeriod period = new TestPeriod(5);
        assertEquals(5, period.getValue());
    }

    // negative value is stored as-is
    @Test
    public void testConstructorAndGetValue_negative() throws Throwable {
        TestPeriod period = new TestPeriod(-7);
        assertEquals(-7, period.getValue());
    }

    // setValue overwrites the stored value
    @Test
    public void testSetValue_updatesStoredValue() throws Throwable {
        TestPeriod period = new TestPeriod(0);
        period.setValue(42);
        assertEquals(42, period.getValue());
    }

    // setValue handles boundary value Integer.MIN_VALUE
    @Test
    public void testSetValue_minValue() throws Throwable {
        TestPeriod period = new TestPeriod(0);
        period.setValue(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, period.getValue());
    }

    //----------------------------------------------------------------
    // getFieldType() / getPeriodType() (abstract, via concrete subclass)
    //----------------------------------------------------------------

    // getFieldType returns the type declared by the concrete subclass
    @Test
    public void testGetFieldType_returnsSubclassType() throws Throwable {
        TestPeriod period = new TestPeriod(1);
        assertEquals(DurationFieldType.days(), period.getFieldType());
    }

    // getPeriodType returns the type declared by the concrete subclass
    @Test
    public void testGetPeriodType_returnsSubclassType() throws Throwable {
        TestPeriod period = new TestPeriod(1);
        assertEquals(PeriodType.days(), period.getPeriodType());
    }

    //----------------------------------------------------------------
    // size()
    //----------------------------------------------------------------

    // size always returns 1 for a single field period
    @Test
    public void testSize_alwaysOne() throws Throwable {
        TestPeriod period = new TestPeriod(9);
        assertEquals(1, period.size());
    }

    //----------------------------------------------------------------
    // getFieldType(int)
    //----------------------------------------------------------------

    // index 0 returns the single supported field type
    @Test
    public void testGetFieldTypeIndex_zero_returnsFieldType() throws Throwable {
        TestPeriod period = new TestPeriod(1);
        assertEquals(DurationFieldType.days(), period.getFieldType(0));
    }

    // index 1 (out of range) throws IndexOutOfBoundsException
    @Test
    public void testGetFieldTypeIndex_one_throwsIOOBE() throws Throwable {
        TestPeriod period = new TestPeriod(1);
        try {
            period.getFieldType(1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // index -1 (out of range) throws IndexOutOfBoundsException
    @Test
    public void testGetFieldTypeIndex_negative_throwsIOOBE() throws Throwable {
        TestPeriod period = new TestPeriod(1);
        try {
            period.getFieldType(-1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    //----------------------------------------------------------------
    // getValue(int)
    //----------------------------------------------------------------

    // index 0 returns the stored value
    @Test
    public void testGetValueIndex_zero_returnsValue() throws Throwable {
        TestPeriod period = new TestPeriod(11);
        assertEquals(11, period.getValue(0));
    }

    // index 1 (out of range) throws IndexOutOfBoundsException
    @Test
    public void testGetValueIndex_one_throwsIOOBE() throws Throwable {
        TestPeriod period = new TestPeriod(11);
        try {
            period.getValue(1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    //----------------------------------------------------------------
    // get(DurationFieldType)
    //----------------------------------------------------------------

    // matching field type returns the stored value
    @Test
    public void testGet_matchingType_returnsValue() throws Throwable {
        TestPeriod period = new TestPeriod(6);
        assertEquals(6, period.get(DurationFieldType.days()));
    }

    // non-matching field type returns zero
    @Test
    public void testGet_nonMatchingType_returnsZero() throws Throwable {
        TestPeriod period = new TestPeriod(6);
        assertEquals(0, period.get(DurationFieldType.hours()));
    }

    // null type returns zero per javadoc
    @Test
    public void testGet_nullType_returnsZero() throws Throwable {
        TestPeriod period = new TestPeriod(6);
        assertEquals(0, period.get((DurationFieldType) null));
    }

    //----------------------------------------------------------------
    // isSupported(DurationFieldType)
    //----------------------------------------------------------------

    // matching field type is supported
    @Test
    public void testIsSupported_matchingType_true() throws Throwable {
        TestPeriod period = new TestPeriod(1);
        assertTrue(period.isSupported(DurationFieldType.days()));
    }

    // non-matching field type is not supported
    @Test
    public void testIsSupported_nonMatchingType_false() throws Throwable {
        TestPeriod period = new TestPeriod(1);
        assertFalse(period.isSupported(DurationFieldType.hours()));
    }

    // null type is not supported
    @Test
    public void testIsSupported_nullType_false() throws Throwable {
        TestPeriod period = new TestPeriod(1);
        assertFalse(period.isSupported((DurationFieldType) null));
    }

    //----------------------------------------------------------------
    // toPeriod()
    //----------------------------------------------------------------

    // toPeriod places the value in the matching field, others stay zero
    @Test
    public void testToPeriod_mapsValueToCorrectField() throws Throwable {
        TestPeriod period = new TestPeriod(5);
        Period result = period.toPeriod();
        assertEquals(5, result.getDays());
        assertEquals(0, result.getHours());
    }

    //----------------------------------------------------------------
    // toMutablePeriod()
    //----------------------------------------------------------------

    // toMutablePeriod places the value in the matching field of a new MutablePeriod
    @Test
    public void testToMutablePeriod_mapsValueToCorrectField() throws Throwable {
        TestPeriod period = new TestPeriod(5);
        MutablePeriod result = period.toMutablePeriod();
        assertEquals(5, result.getDays());
    }

    //----------------------------------------------------------------
    // equals(Object)
    //----------------------------------------------------------------

    // same reference is always equal
    @Test
    public void testEquals_sameReference_true() throws Throwable {
        TestPeriod period = new TestPeriod(3);
        assertTrue(period.equals(period));
    }

    // same period type and same value are equal
    @Test
    public void testEquals_sameTypeAndValue_true() throws Throwable {
        TestPeriod a = new TestPeriod(3);
        TestPeriod b = new TestPeriod(3);
        assertTrue(a.equals(b));
    }

    // different value makes them unequal
    @Test
    public void testEquals_differentValue_false() throws Throwable {
        TestPeriod a = new TestPeriod(3);
        TestPeriod b = new TestPeriod(4);
        assertFalse(a.equals(b));
    }

    // different period type makes them unequal even if value matches
    @Test
    public void testEquals_differentPeriodType_false() throws Throwable {
        TestPeriod a = new TestPeriod(3);
        TestPeriod2 b = new TestPeriod2(3);
        assertFalse(a.equals(b));
    }

    // a non-ReadablePeriod object is never equal
    @Test
    public void testEquals_nonReadablePeriod_false() throws Throwable {
        TestPeriod a = new TestPeriod(3);
        assertFalse(a.equals("not a period"));
    }

    // null is never equal
    @Test
    public void testEquals_null_false() throws Throwable {
        TestPeriod a = new TestPeriod(3);
        assertFalse(a.equals(null));
    }

    //----------------------------------------------------------------
    // hashCode()
    //----------------------------------------------------------------

    // equal objects must produce equal hash codes (hashCode/equals contract)
    @Test
    public void testHashCode_equalObjects_sameHashCode() throws Throwable {
        TestPeriod a = new TestPeriod(7);
        TestPeriod b = new TestPeriod(7);
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
    }

    //----------------------------------------------------------------
    // compareTo(BaseSingleFieldPeriod)
    //----------------------------------------------------------------

    // this value greater than other returns positive
    @Test
    public void testCompareTo_greater_returnsPositive() throws Throwable {
        TestPeriod a = new TestPeriod(10);
        TestPeriod b = new TestPeriod(5);
        assertEquals(1, a.compareTo(b));
    }

    // this value less than other returns negative
    @Test
    public void testCompareTo_less_returnsNegative() throws Throwable {
        TestPeriod a = new TestPeriod(5);
        TestPeriod b = new TestPeriod(10);
        assertEquals(-1, a.compareTo(b));
    }

    // equal values return zero
    @Test
    public void testCompareTo_equalValues_returnsZero() throws Throwable {
        TestPeriod a = new TestPeriod(5);
        TestPeriod b = new TestPeriod(5);
        assertEquals(0, a.compareTo(b));
    }

    // boundary values compare correctly without overflow issues
    @Test
    public void testCompareTo_boundaryValues_noOverflow() throws Throwable {
        TestPeriod a = new TestPeriod(Integer.MIN_VALUE);
        TestPeriod b = new TestPeriod(Integer.MAX_VALUE);
        assertEquals(-1, a.compareTo(b));
        assertEquals(1, b.compareTo(a));
    }

    // comparing to a different concrete class throws ClassCastException
    @Test
    public void testCompareTo_differentClass_throwsCCE() throws Throwable {
        TestPeriod a = new TestPeriod(5);
        TestPeriod2 b = new TestPeriod2(5);
        try {
            a.compareTo(b);
            fail("expected ClassCastException");
        } catch (ClassCastException expected) {
        }
    }

    // comparing to null throws NullPointerException
    @Test
    public void testCompareTo_null_throwsNPE() throws Throwable {
        TestPeriod a = new TestPeriod(5);
        try {
            a.compareTo(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }
}
