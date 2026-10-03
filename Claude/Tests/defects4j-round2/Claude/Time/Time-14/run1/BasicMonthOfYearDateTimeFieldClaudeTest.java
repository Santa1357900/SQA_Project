package org.joda.time.chrono;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeZone;
import org.joda.time.DurationFieldType;

public class BasicMonthOfYearDateTimeFieldClaudeTest {

    private DateTimeField field;

    @Before
    public void setUp() throws Throwable {
        field = ISOChronology.getInstanceUTC().monthOfYear();
    }

    private static long ymd(int year, int month, int day) {
        return new DateTime(year, month, day, 0, 0, 0, 0, DateTimeZone.UTC).getMillis();
    }

    private static long ymdhms(int year, int month, int day, int h, int m, int s, int ms) {
        return new DateTime(year, month, day, h, m, s, ms, DateTimeZone.UTC).getMillis();
    }

    // isLenient() must always report false for this field
    @Test
    public void testIsLenient_alwaysFalse() throws Throwable {
        assertFalse(field.isLenient());
    }

    // get(long) extracts the month component of the instant
    @Test
    public void testGet_returnsMonthOfYear() throws Throwable {
        long instant = ymd(2004, 7, 15);
        assertEquals(7, field.get(instant));
    }

    // add(long,int): months==0 is the fast-path, instant returned unchanged
    @Test
    public void testAdd_zeroMonths_returnsSameInstant() throws Throwable {
        long instant = ymd(2003, 5, 15);
        assertEquals(instant, field.add(instant, 0));
    }

    // javadoc example: 07-31 - 1 month = 06-30 (day forced to max of shorter month)
    @Test
    public void testAdd_dayForcing_july31MinusOneMonth_june30() throws Throwable {
        long instant = ymd(2003, 7, 31);
        DateTime dt = new DateTime(field.add(instant, -1), DateTimeZone.UTC);
        assertEquals(2003, dt.getYear());
        assertEquals(6, dt.getMonthOfYear());
        assertEquals(30, dt.getDayOfMonth());
    }

    // javadoc example: 03-31 - 1 month = 02-28 in a non-leap year
    @Test
    public void testAdd_dayForcing_march31MinusOneMonth_nonLeapYear_feb28() throws Throwable {
        long instant = ymd(2003, 3, 31);
        DateTime dt = new DateTime(field.add(instant, -1), DateTimeZone.UTC);
        assertEquals(2, dt.getMonthOfYear());
        assertEquals(28, dt.getDayOfMonth());
    }

    // javadoc example: 03-31 - 1 month = 02-29 in a leap year
    @Test
    public void testAdd_dayForcing_march31MinusOneMonth_leapYear_feb29() throws Throwable {
        long instant = ymd(2004, 3, 31);
        DateTime dt = new DateTime(field.add(instant, -1), DateTimeZone.UTC);
        assertEquals(2, dt.getMonthOfYear());
        assertEquals(29, dt.getDayOfMonth());
    }

    // add(long,int): positive branch crossing into the next year
    @Test
    public void testAdd_positiveCrossYearBoundary_decemberPlusOne_nextJanuary() throws Throwable {
        long instant = ymd(2003, 12, 15);
        DateTime dt = new DateTime(field.add(instant, 1), DateTimeZone.UTC);
        assertEquals(2004, dt.getYear());
        assertEquals(1, dt.getMonthOfYear());
        assertEquals(15, dt.getDayOfMonth());
    }

    // add(long,int): negative branch crossing into the previous year
    @Test
    public void testAdd_negativeCrossYearBoundary_januaryMinusOne_previousDecember() throws Throwable {
        long instant = ymd(2003, 1, 15);
        DateTime dt = new DateTime(field.add(instant, -1), DateTimeZone.UTC);
        assertEquals(2002, dt.getYear());
        assertEquals(12, dt.getMonthOfYear());
        assertEquals(15, dt.getDayOfMonth());
    }

    // add(long,int): positive amount spanning multiple years, not a multiple of 12
    @Test
    public void testAdd_positiveMultiYear_spanningNonMultipleOfTwelve() throws Throwable {
        long instant = ymd(2003, 1, 10);
        DateTime dt = new DateTime(field.add(instant, 25), DateTimeZone.UTC);
        assertEquals(2005, dt.getYear());
        assertEquals(2, dt.getMonthOfYear());
        assertEquals(10, dt.getDayOfMonth());
    }

    // add(long,int): negative amount exact multiple of 12 (remMonthToUse==0, monthToUse==1 branch)
    @Test
    public void testAdd_negativeExactMultipleOfMax_remainderZeroBranch() throws Throwable {
        long instant = ymd(2003, 1, 15);
        DateTime dt = new DateTime(field.add(instant, -24), DateTimeZone.UTC);
        assertEquals(2001, dt.getYear());
        assertEquals(1, dt.getMonthOfYear());
        assertEquals(15, dt.getDayOfMonth());
    }

    // add(long,long): value fits in int, must delegate to add(long,int) with same result
    @Test
    public void testAddLong_smallValueDelegatesToIntOverload() throws Throwable {
        long instant = ymd(2003, 3, 31);
        long viaLong = field.add(instant, -1L);
        long viaInt = field.add(instant, -1);
        assertEquals(viaInt, viaLong);
    }

    // add(long,long): value overflowing int range leads to a year outside valid bounds
    @Test
    public void testAddLong_hugeValue_throwsIllegalArgumentException() throws Throwable {
        long instant = ymd(2003, 1, 15);
        try {
            field.add(instant, 10000000000L);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // addWrapField: result within [MIN,MAX], no wrap needed
    @Test
    public void testAddWrapField_withinRange_noWrap() throws Throwable {
        long instant = ymd(2003, 5, 15);
        DateTime dt = new DateTime(field.addWrapField(instant, 2), DateTimeZone.UTC);
        assertEquals(7, dt.getMonthOfYear());
        assertEquals(2003, dt.getYear());
    }

    // addWrapField: wraps forward past MAX, year must stay unchanged
    @Test
    public void testAddWrapField_wrapsForwardAcrossMax_yearUnchanged() throws Throwable {
        long instant = ymd(2003, 10, 15);
        DateTime dt = new DateTime(field.addWrapField(instant, 5), DateTimeZone.UTC);
        assertEquals(3, dt.getMonthOfYear());
        assertEquals(2003, dt.getYear());
    }

    // addWrapField: wraps backward below MIN, year must stay unchanged
    @Test
    public void testAddWrapField_wrapsBackwardBelowMin_yearUnchanged() throws Throwable {
        long instant = ymd(2003, 2, 15);
        DateTime dt = new DateTime(field.addWrapField(instant, -5), DateTimeZone.UTC);
        assertEquals(9, dt.getMonthOfYear());
        assertEquals(2003, dt.getYear());
    }

    // getDifferenceAsLong: same month, partial elapsed -> zero complete months
    @Test
    public void testGetDifferenceAsLong_sameMonth_zero() throws Throwable {
        long minuend = ymd(2003, 1, 15);
        long subtrahend = ymd(2003, 1, 5);
        assertEquals(0L, field.getDifferenceAsLong(minuend, subtrahend));
    }

    // getDifferenceAsLong: multi-year span, same day-of-month/time -> exact month count
    @Test
    public void testGetDifferenceAsLong_crossYear_seventeen() throws Throwable {
        long minuend = ymd(2003, 6, 15);
        long subtrahend = ymd(2002, 1, 15);
        assertEquals(17L, field.getDifferenceAsLong(minuend, subtrahend));
    }

    // getDifferenceAsLong: last-day-of-month special case, exactly one full month elapsed
    @Test
    public void testGetDifferenceAsLong_lastDayOfMonthSpecialCase_fullMonth() throws Throwable {
        long minuend = ymd(2003, 2, 28);
        long subtrahend = ymd(2003, 1, 31);
        assertEquals(1L, field.getDifferenceAsLong(minuend, subtrahend));
    }

    // getDifferenceAsLong: last-day-of-month special case, not yet a full month elapsed
    @Test
    public void testGetDifferenceAsLong_lastDayOfMonthSpecialCase_notFullMonth() throws Throwable {
        long minuend = ymd(2003, 2, 27);
        long subtrahend = ymd(2003, 1, 31);
        assertEquals(0L, field.getDifferenceAsLong(minuend, subtrahend));
    }

    // getDifferenceAsLong: minuend earlier than subtrahend triggers the negation branch
    @Test
    public void testGetDifferenceAsLong_negativeDirection() throws Throwable {
        long minuend = ymd(2003, 1, 1);
        long subtrahend = ymd(2003, 3, 1);
        assertEquals(-2L, field.getDifferenceAsLong(minuend, subtrahend));
    }

    // set(long,int): valid month, day fits, no coercion applied
    @Test
    public void testSet_withinBounds_noCoercion() throws Throwable {
        long instant = ymd(2003, 1, 15);
        DateTime dt = new DateTime(field.set(instant, 6), DateTimeZone.UTC);
        assertEquals(2003, dt.getYear());
        assertEquals(6, dt.getMonthOfYear());
        assertEquals(15, dt.getDayOfMonth());
    }

    // set(long,int): day-of-month coerced down to the max day of the target month
    @Test
    public void testSet_dayCoercion_march31ToApril30() throws Throwable {
        long instant = ymd(2003, 3, 31);
        DateTime dt = new DateTime(field.set(instant, 4), DateTimeZone.UTC);
        assertEquals(4, dt.getMonthOfYear());
        assertEquals(30, dt.getDayOfMonth());
    }

    // set(long,int): value below MIN throws via FieldUtils.verifyValueBounds
    @Test
    public void testSet_belowMinimum_throwsIllegalArgumentException() throws Throwable {
        long instant = ymd(2003, 6, 15);
        try {
            field.set(instant, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // set(long,int): value above MAX throws via FieldUtils.verifyValueBounds
    @Test
    public void testSet_aboveMaximum_throwsIllegalArgumentException() throws Throwable {
        long instant = ymd(2003, 6, 15);
        try {
            field.set(instant, 13);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getRangeDurationField(): month's range duration field is years
    @Test
    public void testGetRangeDurationField_isYears() throws Throwable {
        assertEquals(DurationFieldType.years(), field.getRangeDurationField().getType());
    }

    // isLeap(long): leap year at the configured leap month (February) is true
    @Test
    public void testIsLeap_leapYearFebruary_true() throws Throwable {
        long instant = ymd(2004, 2, 10);
        assertTrue(field.isLeap(instant));
    }

    // isLeap(long): leap year but a non-leap month is false
    @Test
    public void testIsLeap_leapYearOtherMonth_false() throws Throwable {
        long instant = ymd(2004, 6, 10);
        assertFalse(field.isLeap(instant));
    }

    // isLeap(long): non-leap year at the leap month is still false
    @Test
    public void testIsLeap_nonLeapYearFebruary_false() throws Throwable {
        long instant = ymd(2003, 2, 10);
        assertFalse(field.isLeap(instant));
    }

    // getLeapAmount(long): mirrors isLeap, 1 when leap else 0
    @Test
    public void testGetLeapAmount_matchesIsLeap() throws Throwable {
        assertEquals(1, field.getLeapAmount(ymd(2004, 2, 10)));
        assertEquals(0, field.getLeapAmount(ymd(2004, 6, 10)));
    }

    // getLeapDurationField(): the leap unit for months is days
    @Test
    public void testGetLeapDurationField_isDays() throws Throwable {
        assertEquals(DurationFieldType.days(), field.getLeapDurationField().getType());
    }

    // getMinimumValue(): January
    @Test
    public void testGetMinimumValue_isJanuary() throws Throwable {
        assertEquals(DateTimeConstants.JANUARY, field.getMinimumValue());
    }

    // getMaximumValue(): December for the Gregorian/ISO calendar
    @Test
    public void testGetMaximumValue_isDecember() throws Throwable {
        assertEquals(DateTimeConstants.DECEMBER, field.getMaximumValue());
    }

    // roundFloor(long): truncates to the first millisecond of the month
    @Test
    public void testRoundFloor_truncatesToStartOfMonth() throws Throwable {
        long instant = ymdhms(2003, 6, 15, 10, 30, 45, 123);
        DateTime dt = new DateTime(field.roundFloor(instant), DateTimeZone.UTC);
        assertEquals(2003, dt.getYear());
        assertEquals(6, dt.getMonthOfYear());
        assertEquals(1, dt.getDayOfMonth());
        assertEquals(0, dt.getMillisOfDay());
    }

    // remainder(long): equals instant minus roundFloor(instant)
    @Test
    public void testRemainder_equalsInstantMinusRoundFloor() throws Throwable {
        long instant = ymd(2003, 6, 15);
        long expected = 14L * 24L * 60L * 60L * 1000L;
        assertEquals(expected, field.remainder(instant));
    }
}
