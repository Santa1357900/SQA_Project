package org.joda.time;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;
import java.util.TimeZone;

import org.joda.time.tz.NameProvider;
import org.joda.time.tz.Provider;

import org.junit.Test;
import static org.junit.Assert.*;

public class DateTimeZoneClaudeTest {

    // getID returns exactly the id used to construct the zone
    @Test
    public void testGetID_utc_returnsUtc() throws Throwable {
        assertEquals("UTC", DateTimeZone.UTC.getID());
    }

    // isFixed() for the UTC constant must be true (no transitions)
    @Test
    public void testIsFixed_utc_true() throws Throwable {
        assertTrue(DateTimeZone.UTC.isFixed());
    }

    // getOffset(long) for UTC is always zero
    @Test
    public void testGetOffset_long_utc_isZero() throws Throwable {
        assertEquals(0, DateTimeZone.UTC.getOffset(123456789L));
    }

    // getStandardOffset(long) for UTC is always zero
    @Test
    public void testGetStandardOffset_utc_isZero() throws Throwable {
        assertEquals(0, DateTimeZone.UTC.getStandardOffset(123456789L));
    }

    // isStandardOffset: getOffset == getStandardOffset branch
    @Test
    public void testIsStandardOffset_utc_true() throws Throwable {
        assertTrue(DateTimeZone.UTC.isStandardOffset(0L));
    }

    // hashCode() concrete formula: 57 + getID().hashCode()
    @Test
    public void testHashCode_matchesDocumentedFormula() throws Throwable {
        assertEquals(57 + "UTC".hashCode(), DateTimeZone.UTC.hashCode());
    }

    // toString() returns the id
    @Test
    public void testToString_returnsId() throws Throwable {
        assertEquals("UTC", DateTimeZone.UTC.toString());
    }

    // equals contract: same instance true, null-safe false
    @Test
    public void testEquals_sameInstanceTrue_nullFalse() throws Throwable {
        assertTrue(DateTimeZone.UTC.equals(DateTimeZone.UTC));
        assertFalse(DateTimeZone.UTC.equals(null));
    }

    // getShortName(instant) / getName(instant) must delegate to the two-arg overload with null locale
    @Test
    public void testGetShortNameAndGetName_delegateToTwoArgOverload() throws Throwable {
        assertEquals(DateTimeZone.UTC.getShortName(0L), DateTimeZone.UTC.getShortName(0L, null));
        assertEquals(DateTimeZone.UTC.getName(0L), DateTimeZone.UTC.getName(0L, null));
    }

    // getOffset(ReadableInstant): null branch and non-null branch, both zero for UTC
    @Test
    public void testGetOffset_readableInstant_nullAndNonNull() throws Throwable {
        assertEquals(0, DateTimeZone.UTC.getOffset((ReadableInstant) null));
        assertEquals(0, DateTimeZone.UTC.getOffset(new Instant(500000L)));
    }

    // convertUTCToLocal for UTC: offset 0, no change
    @Test
    public void testConvertUTCToLocal_utc_noChange() throws Throwable {
        assertEquals(5000L, DateTimeZone.UTC.convertUTCToLocal(5000L));
    }

    // convertUTCToLocal overflow must throw ArithmeticException
    @Test
    public void testConvertUTCToLocal_overflow_throwsArithmeticException() throws Throwable {
        DateTimeZone plusOne = DateTimeZone.forOffsetHours(1);
        try {
            plusOne.convertUTCToLocal(Long.MAX_VALUE);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // convertLocalToUTC for UTC: offset 0, no change
    @Test
    public void testConvertLocalToUTC_utc_noChange() throws Throwable {
        assertEquals(5000L, DateTimeZone.UTC.convertLocalToUTC(5000L, true));
    }

    // convertLocalToUTC strict=true inside a real DST gap must throw IllegalArgumentException
    @Test
    public void testConvertLocalToUTC_londonGapStrict_throwsIllegalArgumentException() throws Throwable {
        DateTimeZone london = DateTimeZone.forID("Europe/London");
        long instantLocal = new DateTime(2011, 3, 27, 1, 30, 0, 0, DateTimeZone.UTC).getMillis();
        try {
            london.convertLocalToUTC(instantLocal, true);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("transition"));
        }
    }

    // BUG CATCHER: Javadoc mandates getOffsetFromLocal always favours daylight (summer) time during
    // an overlap, regardless of hemisphere. Europe/Paris 2011-10-30 02:00-02:59 is ambiguous
    // (CEST +02:00 is earlier/daylight, CET +01:00 is later/standard) and must resolve to +02:00.
    @Test
    public void testGetOffsetFromLocal_easternHemisphereOverlap_mustFavourDaylightTime() throws Throwable {
        DateTimeZone paris = DateTimeZone.forID("Europe/Paris");
        long instantLocal = new DateTime(2011, 10, 30, 2, 30, 0, 0, DateTimeZone.UTC).getMillis();
        int offset = paris.getOffsetFromLocal(instantLocal);
        assertEquals(7200000, offset);
    }

    // Contrast case: America/New_York 2011-11-06 01:00-01:59 overlap (western hemisphere) already
    // resolves to daylight time (EDT -04:00) via the natural computation, per the same Javadoc rule.
    @Test
    public void testGetOffsetFromLocal_westernHemisphereOverlap_favoursDaylightTime() throws Throwable {
        DateTimeZone ny = DateTimeZone.forID("America/New_York");
        long instantLocal = new DateTime(2011, 11, 6, 1, 30, 0, 0, DateTimeZone.UTC).getMillis();
        int offset = ny.getOffsetFromLocal(instantLocal);
        assertEquals(-14400000, offset);
    }

    // Javadoc gap table: a local time inside the spring-forward gap must resolve to the wall time
    // that occurs after the gap (Europe/London 2011-03-27 01:00->02:00 cutover).
    @Test
    public void testGetOffsetFromLocal_londonGap_resolvesAfterGap() throws Throwable {
        DateTimeZone london = DateTimeZone.forID("Europe/London");
        long instantLocal = new DateTime(2011, 3, 27, 1, 30, 0, 0, DateTimeZone.UTC).getMillis();
        int offset = london.getOffsetFromLocal(instantLocal);
        DateTime result = new DateTime(instantLocal - offset, london);
        assertEquals(2, result.getHourOfDay());
        assertEquals(30, result.getMinuteOfHour());
    }

    // America/New_York 2011-03-13 02:00->03:00 gap exercises the early-return branch of
    // getOffsetFromLocal (nextLocal != nextAdjusted), resolving to the pre-gap standard offset.
    @Test
    public void testGetOffsetFromLocal_nyGap_earlyReturnBranch() throws Throwable {
        DateTimeZone ny = DateTimeZone.forID("America/New_York");
        long instantLocal = new DateTime(2011, 3, 13, 2, 30, 0, 0, DateTimeZone.UTC).getMillis();
        int offset = ny.getOffsetFromLocal(instantLocal);
        assertEquals(-18000000, offset);
    }

    // getMillisKeepLocal: newZone == this branch returns the same instant unchanged
    @Test
    public void testGetMillisKeepLocal_sameZone_returnsSameInstant() throws Throwable {
        assertEquals(12345L, DateTimeZone.UTC.getMillisKeepLocal(DateTimeZone.UTC, 12345L));
    }

    // getMillisKeepLocal across different fixed zones preserves the local wall clock reading
    @Test
    public void testGetMillisKeepLocal_differentZones_keepsLocalTime() throws Throwable {
        DateTimeZone plusOne = DateTimeZone.forOffsetHours(1);
        long result = DateTimeZone.UTC.getMillisKeepLocal(plusOne, 0L);
        assertEquals(-3600000L, result);
    }

    // isLocalDateTimeGap: a fixed zone has isFixed()==true and always returns false
    @Test
    public void testIsLocalDateTimeGap_fixedZone_alwaysFalse() throws Throwable {
        LocalDateTime ldt = new LocalDateTime(2011, 3, 27, 1, 30, 0, 0);
        assertFalse(DateTimeZone.UTC.isLocalDateTimeGap(ldt));
    }

    // isLocalDateTimeGap: a genuinely non-existent local time in a real gap returns true
    @Test
    public void testIsLocalDateTimeGap_realGap_returnsTrue() throws Throwable {
        DateTimeZone london = DateTimeZone.forID("Europe/London");
        LocalDateTime ldt = new LocalDateTime(2011, 3, 27, 1, 30, 0, 0);
        assertTrue(london.isLocalDateTimeGap(ldt));
    }

    // isLocalDateTimeGap: a normal, unambiguous local time returns false
    @Test
    public void testIsLocalDateTimeGap_normalTime_returnsFalse() throws Throwable {
        DateTimeZone london = DateTimeZone.forID("Europe/London");
        LocalDateTime ldt = new LocalDateTime(2011, 6, 15, 10, 0, 0, 0);
        assertFalse(london.isLocalDateTimeGap(ldt));
    }

    // adjustOffset on a fixed zone: before == after, instant returned unchanged (both directions)
    @Test
    public void testAdjustOffset_fixedZone_returnsSameInstant() throws Throwable {
        assertEquals(123456789L, DateTimeZone.UTC.adjustOffset(123456789L, true));
        assertEquals(123456789L, DateTimeZone.UTC.adjustOffset(123456789L, false));
    }

    // nextTransition/previousTransition on a fixed zone must return the same instant (no transitions)
    @Test
    public void testNextAndPreviousTransition_fixedZone_returnSameInstant() throws Throwable {
        assertEquals(1000L, DateTimeZone.UTC.nextTransition(1000L));
        assertEquals(1000L, DateTimeZone.UTC.previousTransition(1000L));
    }

    // toTimeZone() for UTC must map to a java.util.TimeZone with the same id
    @Test
    public void testToTimeZone_utc_matchesId() throws Throwable {
        assertEquals("UTC", DateTimeZone.UTC.toTimeZone().getID());
    }

    // forID("UTC") and forID("+00:00") both return the UTC singleton instance
    @Test
    public void testForID_utcAndZeroOffset_returnUtcInstance() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forID("UTC"));
        assertSame(DateTimeZone.UTC, DateTimeZone.forID("+00:00"));
    }

    // forID with a positive fixed offset builds a zone with matching id and offset
    @Test
    public void testForID_positiveOffset_buildsFixedZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("+01:00");
        assertEquals("+01:00", zone.getID());
        assertEquals(3600000, zone.getOffset(0L));
    }

    // forID with a negative fixed offset builds a zone with matching id and offset
    @Test
    public void testForID_negativeOffset_buildsFixedZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("-03:30");
        assertEquals("-03:30", zone.getID());
        assertEquals(-12600000, zone.getOffset(0L));
    }

    // forID with an unrecognised id throws IllegalArgumentException
    @Test
    public void testForID_unknownId_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forID("Not/AZone123");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not recognised"));
        }
    }

    // forID with a real long-form zone id returns a non-fixed zone with matching id
    @Test
    public void testForID_realZoneId_returnsMatchingZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("Europe/Paris");
        assertEquals("Europe/Paris", zone.getID());
        assertFalse(zone.isFixed());
    }

    // forOffsetHours: zero returns UTC, positive and negative hours build correct ids/offsets
    @Test
    public void testForOffsetHours_zeroPositiveNegative() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forOffsetHours(0));
        DateTimeZone plus5 = DateTimeZone.forOffsetHours(5);
        assertEquals("+05:00", plus5.getID());
        assertEquals(18000000, plus5.getOffset(0L));
        DateTimeZone minus5 = DateTimeZone.forOffsetHours(-5);
        assertEquals("-05:00", minus5.getID());
    }

    // forOffsetHours overflow must be reported as IllegalArgumentException, not ArithmeticException
    @Test
    public void testForOffsetHours_overflow_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetHours(Integer.MAX_VALUE);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // forOffsetHoursMinutes: documented example (-2, 30) must produce "-02:30"
    @Test
    public void testForOffsetHoursMinutes_negativeHoursPositiveMinutes_documentedExample() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(-2, 30);
        assertEquals("-02:30", zone.getID());
        assertEquals(-9000000, zone.getOffset(0L));
    }

    // forOffsetHoursMinutes: minutes outside [0,59] must throw IllegalArgumentException
    @Test
    public void testForOffsetHoursMinutes_minutesOutOfRange_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(2, 70);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        try {
            DateTimeZone.forOffsetHoursMinutes(2, -5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // forOffsetMillis caches equal-offset fixed zones so repeated calls return the same instance
    @Test
    public void testForOffsetMillis_cachesFixedZoneInstances() throws Throwable {
        DateTimeZone a = DateTimeZone.forOffsetMillis(1800000);
        DateTimeZone b = DateTimeZone.forOffsetMillis(1800000);
        assertSame(a, b);
        assertEquals("+00:30", a.getID());
    }

    // forTimeZone("UTC") returns the UTC singleton
    @Test
    public void testForTimeZone_utc_returnsUtcInstance() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forTimeZone(TimeZone.getTimeZone("UTC")));
    }

    // forTimeZone converts a short JDK id ("EST") to the long form id via the alias table
    @Test
    public void testForTimeZone_shortId_convertedToLongId() throws Throwable {
        DateTimeZone zone = DateTimeZone.forTimeZone(TimeZone.getTimeZone("EST"));
        assertEquals("America/New_York", zone.getID());
    }

    // getAvailableIDs must contain "UTC"
    @Test
    public void testGetAvailableIDs_containsUtc() throws Throwable {
        Set<String> ids = DateTimeZone.getAvailableIDs();
        assertTrue(ids.contains("UTC"));
    }

    // setProvider(null) and setNameProvider(null) reset to valid, non-null defaults
    @Test
    public void testSetProviderAndNameProvider_null_resetsToValidDefaults() throws Throwable {
        DateTimeZone.setProvider(null);
        DateTimeZone.setNameProvider(null);
        Provider provider = DateTimeZone.getProvider();
        NameProvider nameProvider = DateTimeZone.getNameProvider();
        assertNotNull(provider);
        assertNotNull(nameProvider);
        assertTrue(DateTimeZone.getAvailableIDs().contains("UTC"));
    }

    // getDefault() must never return null
    @Test
    public void testGetDefault_returnsNonNullZone() throws Throwable {
        assertNotNull(DateTimeZone.getDefault());
    }

    // setDefault updates the default zone; setDefault(null) throws IllegalArgumentException
    @Test
    public void testSetDefault_updatesDefaultAndRejectsNull() throws Throwable {
        DateTimeZone original = DateTimeZone.getDefault();
        try {
            DateTimeZone.setDefault(DateTimeZone.UTC);
            assertEquals(DateTimeZone.UTC, DateTimeZone.getDefault());
            try {
                DateTimeZone.setDefault(null);
                fail("expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
            }
        } finally {
            DateTimeZone.setDefault(original);
        }
    }

    // writeReplace/Stub serialization round-trip must reconstruct an equal zone via forID
    @Test
    public void testSerialization_roundTrip_returnsEqualZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("Europe/Paris");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(zone);
        oos.close();
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Object result = ois.readObject();
        ois.close();
        assertEquals(zone, result);
    }
}
