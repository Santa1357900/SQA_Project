package org.joda.time;

import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.joda.time.tz.NameProvider;
import org.joda.time.tz.Provider;

import org.junit.Test;
import static org.junit.Assert.*;

public class DateTimeZoneClaudeTest {

    private DateTimeZone laZone() {
        return DateTimeZone.forID("America/Los_Angeles");
    }

    // getDefault(): must never return null
    @Test
    public void testGetDefault_returnsNonNullZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.getDefault();
        assertNotNull(zone);
    }

    // setDefault(null) branch: must throw IllegalArgumentException
    @Test
    public void testSetDefault_null_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.setDefault(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // setDefault(valid) then getDefault() returns it; restore afterwards
    @Test
    public void testSetDefault_validZone_updatesDefault() throws Throwable {
        DateTimeZone original = DateTimeZone.getDefault();
        try {
            DateTimeZone paris = DateTimeZone.forID("Europe/Paris");
            DateTimeZone.setDefault(paris);
            assertEquals(paris, DateTimeZone.getDefault());
        } finally {
            DateTimeZone.setDefault(original);
        }
    }

    // forID(null) branch: delegates to getDefault()
    @Test
    public void testForID_null_returnsDefaultZone() throws Throwable {
        assertEquals(DateTimeZone.getDefault(), DateTimeZone.forID(null));
    }

    // forID("UTC") branch: returns the singleton UTC instance
    @Test
    public void testForID_UTCLiteral_returnsUTCInstance() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forID("UTC"));
    }

    // forID(validLongId) branch: provider lookup succeeds
    @Test
    public void testForID_validLongId_returnsZoneWithSameId() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/Los_Angeles");
        assertEquals("America/Los_Angeles", zone.getID());
    }

    // forID("+00:00") branch: zero offset collapses to UTC
    @Test
    public void testForID_plusZeroOffset_returnsUTC() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forID("+00:00"));
    }

    // forID("+02:00") branch: fixed offset zone with matching id and offset
    @Test
    public void testForID_fixedOffsetId_returnsCorrectOffsetAndId() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("+02:00");
        assertEquals("+02:00", zone.getID());
        assertEquals(2 * DateTimeConstants.MILLIS_PER_HOUR, zone.getOffset(0L));
    }

    // forID(unrecognized) branch: throws IllegalArgumentException
    @Test
    public void testForID_unrecognizedId_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forID("Not/A_Real_Zone");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // forOffsetHours: zero, positive and negative hours
    @Test
    public void testForOffsetHours_zeroPositiveNegative_correctOffsets() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forOffsetHours(0));
        DateTimeZone plus5 = DateTimeZone.forOffsetHours(5);
        assertEquals(5 * DateTimeConstants.MILLIS_PER_HOUR, plus5.getOffset(0L));
        DateTimeZone minus5 = DateTimeZone.forOffsetHours(-5);
        assertEquals(-5 * DateTimeConstants.MILLIS_PER_HOUR, minus5.getOffset(0L));
    }

    // forOffsetHoursMinutes: minutesOffset negative branch throws
    @Test
    public void testForOffsetHoursMinutes_negativeMinutes_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(0, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // forOffsetHoursMinutes: minutesOffset out of range (>59) branch throws
    @Test
    public void testForOffsetHoursMinutes_minutesOutOfRange_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(1, 60);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // forOffsetHoursMinutes: positive hours branch, id and offset per contract
    @Test
    public void testForOffsetHoursMinutes_positiveHours_correctIdAndOffset() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(2, 30);
        assertEquals("+02:30", zone.getID());
        assertEquals(2 * DateTimeConstants.MILLIS_PER_HOUR + 30 * DateTimeConstants.MILLIS_PER_MINUTE,
                zone.getOffset(0L));
    }

    // forOffsetHoursMinutes: negative hours branch, exact javadoc example (-2, 30) -> "-02:30"
    @Test
    public void testForOffsetHoursMinutes_negativeHoursPerJavadocExample_correctIdAndOffset() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(-2, 30);
        assertEquals("-02:30", zone.getID());
        assertEquals(-(2 * DateTimeConstants.MILLIS_PER_HOUR + 30 * DateTimeConstants.MILLIS_PER_MINUTE),
                zone.getOffset(0L));
    }

    // forOffsetMillis: zero and non-zero branches
    @Test
    public void testForOffsetMillis_zeroAndNonZero_correctResults() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forOffsetMillis(0));
        DateTimeZone zone = DateTimeZone.forOffsetMillis(5400000);
        assertEquals("+01:30", zone.getID());
        assertEquals(5400000, zone.getOffset(0L));
    }

    // forTimeZone(null) branch: delegates to getDefault()
    @Test
    public void testForTimeZone_null_returnsDefault() throws Throwable {
        assertEquals(DateTimeZone.getDefault(), DateTimeZone.forTimeZone(null));
    }

    // forTimeZone("UTC") branch
    @Test
    public void testForTimeZone_UTC_returnsUTCInstance() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forTimeZone(TimeZone.getTimeZone("UTC")));
    }

    // forTimeZone: old short id "EST" is converted via internal alias map to America/New_York
    @Test
    public void testForTimeZone_oldAliasId_convertsToLongId() throws Throwable {
        DateTimeZone zone = DateTimeZone.forTimeZone(TimeZone.getTimeZone("EST"));
        assertEquals("America/New_York", zone.getID());
    }

    // forTimeZone: "GMT+02:00" style id branch builds a fixed offset zone
    @Test
    public void testForTimeZone_gmtOffsetFormat_correctFixedZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forTimeZone(TimeZone.getTimeZone("GMT+02:00"));
        assertEquals("+02:00", zone.getID());
        assertEquals(2 * DateTimeConstants.MILLIS_PER_HOUR, zone.getOffset(0L));
    }

    // getAvailableIDs: result must contain UTC
    @Test
    public void testGetAvailableIDs_containsUTC() throws Throwable {
        Set<String> ids = DateTimeZone.getAvailableIDs();
        assertTrue(ids.contains("UTC"));
    }

    // getProvider: must not be null after static init
    @Test
    public void testGetProvider_notNull() throws Throwable {
        Provider provider = DateTimeZone.getProvider();
        assertNotNull(provider);
    }

    // getNameProvider: must not be null after static init
    @Test
    public void testGetNameProvider_notNull() throws Throwable {
        NameProvider provider = DateTimeZone.getNameProvider();
        assertNotNull(provider);
    }

    // getID: returns the id the zone was constructed with
    @Test
    public void testGetID_matchesConstructionId() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        assertEquals("+03:00", zone.getID());
    }

    // getShortName: fixed offset zone has null name key, falls back to id
    @Test
    public void testGetShortName_fixedOffsetZoneNullNameKey_returnsId() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(4);
        assertEquals("+04:00", zone.getShortName(0L));
    }

    // getName: fixed offset zone has null name key, falls back to id
    @Test
    public void testGetName_fixedOffsetZoneNullNameKey_returnsId() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(4);
        assertEquals("+04:00", zone.getName(0L));
    }



    // getOffset(ReadableInstant): null uses current time, non-null matches long overload
    @Test
    public void testGetOffsetReadableInstant_nullAndNonNull_consistentBehavior() throws Throwable {
        assertEquals(0, DateTimeZone.UTC.getOffset((ReadableInstant) null));
        DateTime dt = new DateTime(2012, 6, 1, 0, 0, DateTimeZone.UTC);
        DateTimeZone fixed = DateTimeZone.forOffsetHours(4);
        assertEquals(fixed.getOffset(dt.getMillis()), fixed.getOffset(dt));
    }

    // isStandardOffset: fixed zone has equal offset and standard offset always
    @Test
    public void testIsStandardOffset_fixedZone_alwaysTrue() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(4);
        assertTrue(zone.isStandardOffset(0L));
    }

    // isStandardOffset: DST zone differs between winter (standard) and summer (daylight)
    @Test
    public void testIsStandardOffset_dstZoneWinterAndSummer_correctValues() throws Throwable {
        DateTimeZone la = laZone();
        long winter = new DateTime(2012, 1, 1, 0, 0, DateTimeZone.UTC).getMillis();
        long summer = new DateTime(2012, 7, 1, 0, 0, DateTimeZone.UTC).getMillis();
        assertTrue(la.isStandardOffset(winter));
        assertFalse(la.isStandardOffset(summer));
    }

    // getOffsetFromLocal: fixed zone, positive and negative offsets, no DST ambiguity
    @Test
    public void testGetOffsetFromLocal_fixedPositiveAndNegativeOffset_returnsConstant() throws Throwable {
        DateTimeZone plus = DateTimeZone.forOffsetHours(5);
        assertEquals(5 * DateTimeConstants.MILLIS_PER_HOUR, plus.getOffsetFromLocal(1000000L));
        DateTimeZone minus = DateTimeZone.forOffsetHours(-5);
        assertEquals(-5 * DateTimeConstants.MILLIS_PER_HOUR, minus.getOffsetFromLocal(1000000L));
    }

    // getOffsetFromLocal: local time falling in the America/Los_Angeles spring-forward gap
    // must resolve to the pre-gap (standard) offset so the instant is on/after the gap.
    @Test
    public void testGetOffsetFromLocal_dstGapLA_returnsPreGapOffset() throws Throwable {
        DateTimeZone la = laZone();
        long instantLocal = new DateTime(2012, 3, 11, 2, 30, DateTimeZone.UTC).getMillis();
        assertEquals(-8 * DateTimeConstants.MILLIS_PER_HOUR, la.getOffsetFromLocal(instantLocal));
    }

    // convertUTCToLocal: adds the offset at the given instant (UTC zone offset is always 0)
    @Test
    public void testConvertUTCToLocal_addsOffset() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        long instantUTC = 1000000L;
        assertEquals(instantUTC + 3 * DateTimeConstants.MILLIS_PER_HOUR, zone.convertUTCToLocal(instantUTC));
    }

    // convertLocalToUTC(strict=true): local time in LA spring-forward gap must throw
    @Test
    public void testConvertLocalToUTC_strictDstGapLA_throwsIllegalInstantException() throws Throwable {
        DateTimeZone la = laZone();
        long instantLocal = new DateTime(2012, 3, 11, 2, 30, DateTimeZone.UTC).getMillis();
        try {
            la.convertLocalToUTC(instantLocal, true);
            fail("expected IllegalInstantException");
        } catch (IllegalInstantException expected) {
        }
    }

    // convertLocalToUTC(strict=false): gap in western hemisphere uses offsetLocal per javadoc
    @Test
    public void testConvertLocalToUTC_nonStrictDstGapLA_usesOffsetLocal() throws Throwable {
        DateTimeZone la = laZone();
        long instantLocal = new DateTime(2012, 3, 11, 2, 30, DateTimeZone.UTC).getMillis();
        long result = la.convertLocalToUTC(instantLocal, false);
        assertEquals(instantLocal + 8 * DateTimeConstants.MILLIS_PER_HOUR, result);
    }

    // convertLocalToUTC(3-arg): when offset at original instant matches, direct subtraction is used
    @Test
    public void testConvertLocalToUTC_threeArgSameOffset_directSubtraction() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        long instantLocal = 5000000L;
        long result = zone.convertLocalToUTC(instantLocal, false, 0L);
        assertEquals(instantLocal - 3 * DateTimeConstants.MILLIS_PER_HOUR, result);
    }

    // getMillisKeepLocal: same zone reference short-circuits to the same instant
    @Test
    public void testGetMillisKeepLocal_sameZoneReference_returnsSameInstant() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        assertEquals(12345L, zone.getMillisKeepLocal(zone, 12345L));
    }

    // getMillisKeepLocal: converting between UTC and a fixed offset zone keeping local time
    @Test
    public void testGetMillisKeepLocal_differentFixedZone_correctConversion() throws Throwable {
        DateTimeZone newZone = DateTimeZone.forOffsetHours(2);
        long result = DateTimeZone.UTC.getMillisKeepLocal(newZone, 0L);
        assertEquals(-2 * DateTimeConstants.MILLIS_PER_HOUR, result);
    }

    // isLocalDateTimeGap: fixed (no-transition) zone always returns false
    @Test
    public void testIsLocalDateTimeGap_fixedZone_alwaysFalse() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        LocalDateTime ldt = new LocalDateTime(2012, 3, 11, 2, 30);
        assertFalse(zone.isLocalDateTimeGap(ldt));
    }

    // isLocalDateTimeGap: known LA spring-forward gap datetime is a gap
    @Test
    public void testIsLocalDateTimeGap_dstGapLA_true() throws Throwable {
        DateTimeZone la = laZone();
        LocalDateTime ldt = new LocalDateTime(2012, 3, 11, 2, 30);
        assertTrue(la.isLocalDateTimeGap(ldt));
    }

    // isLocalDateTimeGap: ordinary local datetime, not near any transition, is not a gap
    @Test
    public void testIsLocalDateTimeGap_validLocalTime_false() throws Throwable {
        DateTimeZone la = laZone();
        LocalDateTime ldt = new LocalDateTime(2012, 1, 1, 0, 0);
        assertFalse(la.isLocalDateTimeGap(ldt));
    }

    // adjustOffset: fixed zone never has an overlap, instant is returned unchanged
    @Test
    public void testAdjustOffset_fixedZoneNoOverlap_returnsInstantUnchanged() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        long instant = 123456789L;
        assertEquals(instant, zone.adjustOffset(instant, true));
        assertEquals(instant, zone.adjustOffset(instant, false));
    }

    // isFixed: UTC and fixed-offset zones are fixed, a real rule-based zone is not
    @Test
    public void testIsFixed_utcAndFixedTrueRealZoneFalse() throws Throwable {
        assertTrue(DateTimeZone.UTC.isFixed());
        assertTrue(DateTimeZone.forOffsetHours(2).isFixed());
        assertFalse(laZone().isFixed());
    }

    // nextTransition/previousTransition: a fixed zone has no transitions, instant is unchanged
    @Test
    public void testNextAndPreviousTransition_fixedZone_returnSameInstant() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(4);
        long instant = 999999L;
        assertEquals(instant, zone.nextTransition(instant));
        assertEquals(instant, zone.previousTransition(instant));
    }

    // toTimeZone: converts to the JDK TimeZone with the same id
    @Test
    public void testToTimeZone_returnsMatchingId() throws Throwable {
        assertEquals("UTC", DateTimeZone.UTC.toTimeZone().getID());
    }

    // equals: identity true, null-safe false, different offset false
    @Test
    public void testEquals_identityNullAndDifferentZone_correctResults() throws Throwable {
        assertTrue(DateTimeZone.UTC.equals(DateTimeZone.UTC));
        assertFalse(DateTimeZone.UTC.equals(null));
        assertFalse(DateTimeZone.UTC.equals(DateTimeZone.forOffsetHours(2)));
    }



    // toString: simply returns the id
    @Test
    public void testToString_returnsId() throws Throwable {
        assertEquals("UTC", DateTimeZone.UTC.toString());
    }
}
