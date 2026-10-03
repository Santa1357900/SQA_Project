package org.joda.time;

import java.util.Set;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DateTimeZoneClaudeTest {

    private DateTimeZone originalDefault;

    @Before
    public void setUp() throws Throwable {
        originalDefault = DateTimeZone.getDefault();
    }

    @After
    public void tearDown() throws Throwable {
        DateTimeZone.setDefault(originalDefault);
    }

    // getDefault(): must never return null
    @Test
    public void testGetDefault_returnsNonNullZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.getDefault();
        assertNotNull(zone);
    }

    // setDefault(null) -> IllegalArgumentException branch
    @Test
    public void testSetDefault_null_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.setDefault(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // setDefault(valid) then getDefault() returns same instance
    @Test
    public void testSetDefault_validZone_getDefaultReturnsSameInstance() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        DateTimeZone.setDefault(zone);
        assertSame(zone, DateTimeZone.getDefault());
    }

    // forID(null) -> delegates to getDefault()
    @Test
    public void testForID_null_returnsDefaultZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID((String) null);
        assertNotNull(zone);
    }

    // forID("UTC") -> returns the UTC singleton
    @Test
    public void testForID_UTC_returnsUTCInstance() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forID("UTC"));
    }

    // forID("+00:00") -> parsed offset is zero, returns UTC
    @Test
    public void testForID_plusZeroOffset_returnsUTCInstance() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forID("+00:00"));
    }

    // forID with positive and negative fixed offset strings -> id round-trips, offset matches
    @Test
    public void testForID_offsetStrings_idAndOffsetMatch() throws Throwable {
        DateTimeZone plus = DateTimeZone.forID("+02:00");
        assertEquals("+02:00", plus.getID());
        assertEquals(7200000, plus.getOffset(0L));

        DateTimeZone minus = DateTimeZone.forID("-05:00");
        assertEquals("-05:00", minus.getID());
        assertEquals(-18000000, minus.getOffset(0L));
    }

    // forID with an unrecognised id (not UTC, not +/- offset, not provider id) -> throws
    @Test
    public void testForID_unrecognisedID_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forID("Not/ARealZone123");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not recognised"));
        }
    }

    // forOffsetHours(0) -> UTC
    @Test
    public void testForOffsetHours_zero_returnsUTC() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forOffsetHours(0));
    }

    // forOffsetHours(positive) -> "+hh:00"
    @Test
    public void testForOffsetHours_positive_idMatches() throws Throwable {
        assertEquals("+05:00", DateTimeZone.forOffsetHours(5).getID());
    }

    // forOffsetHours(negative) -> "-hh:00"
    @Test
    public void testForOffsetHours_negative_idMatches() throws Throwable {
        assertEquals("-05:00", DateTimeZone.forOffsetHours(-5).getID());
    }

    // forOffsetHours out of range (-23..23) -> throws
    @Test
    public void testForOffsetHours_aboveRange_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetHours(24);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // forOffsetHoursMinutes(0,0) -> UTC branch
    @Test
    public void testForOffsetHoursMinutes_bothZero_returnsUTC() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forOffsetHoursMinutes(0, 0));
    }

    // table row: +ve/+ve (2,15) -> "+02:15"
    @Test
    public void testForOffsetHoursMinutes_positiveHoursPositiveMinutes_idMatches() throws Throwable {
        assertEquals("+02:15", DateTimeZone.forOffsetHoursMinutes(2, 15).getID());
    }

    // table row: +ve/-ve (2,-15) -> must throw per javadoc (minutes negative, hours positive)
    @Test
    public void testForOffsetHoursMinutes_positiveHoursNegativeMinutes_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(2, -15);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // table row: zero/+ve (0,15) -> "+00:15"
    @Test
    public void testForOffsetHoursMinutes_zeroHoursPositiveMinutes_idMatches() throws Throwable {
        assertEquals("+00:15", DateTimeZone.forOffsetHoursMinutes(0, 15).getID());
    }

    // BUG CATCHER: table row zero/-ve (0,-15) must give "-00:15", not throw
    @Test
    public void testForOffsetHoursMinutes_zeroHoursNegativeMinutes_idMatches_bug() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(0, -15);
        assertEquals("-00:15", zone.getID());
    }

    // table rows: -ve/+ve (-2,15) -> "-02:15" ; -ve/zero (-2,0) -> "-02:00"
    @Test
    public void testForOffsetHoursMinutes_negativeHours_idMatches() throws Throwable {
        assertEquals("-02:15", DateTimeZone.forOffsetHoursMinutes(-2, 15).getID());
        assertEquals("-02:00", DateTimeZone.forOffsetHoursMinutes(-2, 0).getID());
    }

    // minutesOffset above valid range (>59) -> throws regardless of fix
    @Test
    public void testForOffsetHoursMinutes_minutesAboveRange_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(0, 60);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // minutesOffset far below range (<-59) -> throws regardless of fix
    @Test
    public void testForOffsetHoursMinutes_minutesFarBelowRange_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(0, -60);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // hoursOffset above range (+24) -> throws
    @Test
    public void testForOffsetHoursMinutes_hoursAboveRange_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(24, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // hoursOffset below range (-24) -> throws
    @Test
    public void testForOffsetHoursMinutes_hoursBelowRange_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(-24, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // forOffsetMillis(0) -> UTC
    @Test
    public void testForOffsetMillis_zero_returnsUTC() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forOffsetMillis(0));
    }

    // forOffsetMillis at the maximum valid boundary (86399999 ms) -> "+23:59:59.999"
    @Test
    public void testForOffsetMillis_maxBoundary_idMatches() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetMillis(86399999);
        assertEquals("+23:59:59.999", zone.getID());
    }

    // forOffsetMillis one above max -> throws
    @Test
    public void testForOffsetMillis_aboveMax_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetMillis(86400000);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // forOffsetMillis one below min -> throws
    @Test
    public void testForOffsetMillis_belowMin_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetMillis(-86400000);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // forTimeZone(null) -> delegates to getDefault()
    @Test
    public void testForTimeZone_null_returnsDefaultZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forTimeZone(null);
        assertNotNull(zone);
    }

    // forTimeZone(TimeZone "UTC") -> returns UTC singleton directly
    @Test
    public void testForTimeZone_UTC_returnsUTCInstance() throws Throwable {
        DateTimeZone zone = DateTimeZone.forTimeZone(TimeZone.getTimeZone("UTC"));
        assertSame(DateTimeZone.UTC, zone);
    }

    // getAvailableIDs() must contain "UTC" (verified by provider contract)
    @Test
    public void testGetAvailableIDs_containsUTC() throws Throwable {
        Set<String> ids = DateTimeZone.getAvailableIDs();
        assertNotNull(ids);
        assertTrue(ids.contains("UTC"));
    }

    // getProvider() / getNameProvider() must never be null after static init
    @Test
    public void testStaticProviders_notNull() throws Throwable {
        assertNotNull(DateTimeZone.getProvider());
        assertNotNull(DateTimeZone.getNameProvider());
    }

    // UTC constant: id, fixed flag, offset and standard offset are all zero
    @Test
    public void testUTC_idAndFixedAndOffsets() throws Throwable {
        assertEquals("UTC", DateTimeZone.UTC.getID());
        assertTrue(DateTimeZone.UTC.isFixed());
        assertEquals(0, DateTimeZone.UTC.getOffset(0L));
        assertEquals(0, DateTimeZone.UTC.getStandardOffset(0L));
    }



    // equals: reflexive on same instance, false against a different zone
    @Test
    public void testEquals_reflexiveAndDifferentZone() throws Throwable {
        assertTrue(DateTimeZone.UTC.equals(DateTimeZone.UTC));
        assertFalse(DateTimeZone.UTC.equals(DateTimeZone.forOffsetHours(1)));
    }

    // getOffset(ReadableInstant null) uses current time, but UTC offset is always 0
    @Test
    public void testGetOffsetReadableInstant_null_returnsZeroForUTC() throws Throwable {
        assertEquals(0, DateTimeZone.UTC.getOffset((ReadableInstant) null));
    }

    // isStandardOffset: for UTC, offset always equals standard offset
    @Test
    public void testIsStandardOffset_UTC_true() throws Throwable {
        assertTrue(DateTimeZone.UTC.isStandardOffset(0L));
    }

    // convertUTCToLocal: UTC offset 0 -> identity, no overflow
    @Test
    public void testConvertUTCToLocal_identityForUTC() throws Throwable {
        assertEquals(123456789L, DateTimeZone.UTC.convertUTCToLocal(123456789L));
    }

    // convertUTCToLocal: positive offset + Long.MAX_VALUE -> overflow branch throws
    @Test
    public void testConvertUTCToLocal_overflow_throwsArithmeticException() throws Throwable {
        DateTimeZone plusOne = DateTimeZone.forOffsetHours(1);
        try {
            plusOne.convertUTCToLocal(Long.MAX_VALUE);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // convertLocalToUTC: UTC offset 0 -> offsetLocal==offset, identity
    @Test
    public void testConvertLocalToUTC_identityForUTC() throws Throwable {
        assertEquals(123456789L, DateTimeZone.UTC.convertLocalToUTC(123456789L, true));
    }

    // convertLocalToUTC: fixed positive offset + Long.MIN_VALUE -> overflow branch throws
    @Test
    public void testConvertLocalToUTC_overflow_throwsArithmeticException() throws Throwable {
        DateTimeZone plusOne = DateTimeZone.forOffsetHours(1);
        try {
            plusOne.convertLocalToUTC(Long.MIN_VALUE, false);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // convertLocalToUTC(instantLocal, strict, originalInstantUTC): matching offset -> direct subtraction branch
    @Test
    public void testConvertLocalToUTCWithOriginal_sameOffset_returnsDirectly() throws Throwable {
        long result = DateTimeZone.UTC.convertLocalToUTC(555L, true, 999L);
        assertEquals(555L, result);
    }

    // getMillisKeepLocal: newZone == this -> returns oldInstant unchanged (early-return branch)
    @Test
    public void testGetMillisKeepLocal_sameZone_returnsUnchanged() throws Throwable {
        assertEquals(777L, DateTimeZone.UTC.getMillisKeepLocal(DateTimeZone.UTC, 777L));
    }

    // getMillisKeepLocal: different fixed-offset zone -> applies offset difference deterministically
    @Test
    public void testGetMillisKeepLocal_differentZone_appliesOffset() throws Throwable {
        DateTimeZone plusOne = DateTimeZone.forOffsetHours(1);
        long result = DateTimeZone.UTC.getMillisKeepLocal(plusOne, 0L);
        assertEquals(-3600000L, result);
    }

    // adjustOffset: UTC has no overlap (offsetBefore<=offsetAfter) -> returns same instant for both flags
    @Test
    public void testAdjustOffset_noOverlapForUTC_returnsSameInstant() throws Throwable {
        long instant = 1000000L;
        assertEquals(instant, DateTimeZone.UTC.adjustOffset(instant, true));
        assertEquals(instant, DateTimeZone.UTC.adjustOffset(instant, false));
    }

    // getOffsetFromLocal: negative fixed offset skips the transition-lookup branch, returns the fixed offset
    @Test
    public void testGetOffsetFromLocal_negativeFixedOffset_returnsSameOffset() throws Throwable {
        DateTimeZone minusFive = DateTimeZone.forOffsetHours(-5);
        assertEquals(-18000000, minusFive.getOffsetFromLocal(0L));
    }
}
