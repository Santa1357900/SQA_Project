package org.joda.time;

import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.joda.time.tz.NameProvider;
import org.joda.time.tz.Provider;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DateTimeZoneClaudeTest {

    @Before
    public void setUp() throws Throwable {
        // fix the default zone so tests that rely on getDefault() are deterministic
        DateTimeZone.setDefault(DateTimeZone.UTC);
    }

    // UTC constant: fixed, zero offset everywhere
    @Test
    public void testUTC_isFixedAndZeroOffset() throws Throwable {
        assertTrue(DateTimeZone.UTC.isFixed());
        assertEquals(0, DateTimeZone.UTC.getOffset(0L));
        assertEquals("UTC", DateTimeZone.UTC.getID());
    }

    // getID() reflects the id used to build the fixed-offset zone
    @Test
    public void testGetID_returnsConstructedId() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(5);
        assertEquals("+05:00", zone.getID());
    }

    // getDefault() returns whatever was set via setDefault()
    @Test
    public void testGetDefault_returnsSetDefaultZone() throws Throwable {
        DateTimeZone custom = DateTimeZone.forOffsetHours(3);
        DateTimeZone.setDefault(custom);
        assertEquals(custom, DateTimeZone.getDefault());
    }

    // setDefault(null) must throw per javadoc contract
    @Test
    public void testSetDefault_null_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.setDefault(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // forID(null) == default zone (id param javadoc: "null means default")
    @Test
    public void testForID_null_returnsDefaultZone() throws Throwable {
        assertEquals(DateTimeZone.getDefault(), DateTimeZone.forID(null));
    }

    // forID("UTC") must return the UTC singleton
    @Test
    public void testForID_utcLiteral_returnsUTCInstance() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forID("UTC"));
    }

    // "+hh:mm" form builds a positive fixed offset zone (javadoc: fixed offset form)
    @Test
    public void testForID_positiveOffsetString_parsesToFixedZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("+02:00");
        assertEquals(7200000, zone.getOffset(0L));
        assertEquals("+02:00", zone.getID());
    }

    // "-hh:mm" form builds a negative fixed offset zone
    @Test
    public void testForID_negativeOffsetString_parsesToFixedZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("-05:30");
        assertEquals(-19800000, zone.getOffset(0L));
        assertEquals("-05:30", zone.getID());
    }

    // "+00:00" collapses to the UTC singleton (offset == 0 branch)
    @Test
    public void testForID_zeroOffsetString_returnsUTC() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forID("+00:00"));
    }

    // unknown non +/- id must throw with "not recognised"
    @Test
    public void testForID_unrecognisedId_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forID("Not_A_Real_Zone");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not recognised"));
        }
    }

    // forOffsetHours(0) returns UTC singleton
    @Test
    public void testForOffsetHours_zero_returnsUTC() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forOffsetHours(0));
    }

    // forOffsetHours(positive) builds expected padded id/offset
    @Test
    public void testForOffsetHours_positive_createsExpectedZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(5);
        assertEquals(5 * 3600000, zone.getOffset(0L));
        assertEquals("+05:00", zone.getID());
    }

    // forOffsetHours(negative) builds expected negative id/offset
    @Test
    public void testForOffsetHours_negative_createsExpectedZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(-8);
        assertEquals(-8 * 3600000, zone.getOffset(0L));
        assertEquals("-08:00", zone.getID());
    }

    // overflow during hours*60 must surface as IllegalArgumentException
    @Test
    public void testForOffsetHours_overflow_throwsIllegalArgumentException() throws Throwable {
        try {
            DateTimeZone.forOffsetHours(Integer.MAX_VALUE);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("too large"));
        }
    }

    // (0,0) returns UTC singleton
    @Test
    public void testForOffsetHoursMinutes_zero_returnsUTC() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forOffsetHoursMinutes(0, 0));
    }

    // javadoc explicit example: (-2, 30) must build "-02:30"
    @Test
    public void testForOffsetHoursMinutes_negativeHoursJavadocExample() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(-2, 30);
        assertEquals("-02:30", zone.getID());
        assertEquals(-(2 * 3600000 + 30 * 60000), zone.getOffset(0L));
    }

    // positive hours/minutes combine correctly
    @Test
    public void testForOffsetHoursMinutes_positiveHoursAndMinutes() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(2, 30);
        assertEquals("+02:30", zone.getID());
        assertEquals(2 * 3600000 + 30 * 60000, zone.getOffset(0L));
    }

    // minutesOffset > 59 must throw (range check branch)
    @Test
    public void testForOffsetHoursMinutes_minutesTooLarge_throws() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(1, 60);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("range"));
        }
    }

    // minutesOffset < 0 must throw (range check branch)
    @Test
    public void testForOffsetHoursMinutes_minutesNegative_throws() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(1, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("range"));
        }
    }

    // forOffsetMillis(0) returns UTC singleton
    @Test
    public void testForOffsetMillis_zero_returnsUTC() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forOffsetMillis(0));
    }

    // positive millis offset produces expected id
    @Test
    public void testForOffsetMillis_positive_createsExpectedId() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetMillis(3600000);
        assertEquals("+01:00", zone.getID());
        assertEquals(3600000, zone.getOffset(0L));
    }

    // negative millis offset with sub-hour minutes produces expected id
    @Test
    public void testForOffsetMillis_negative_createsExpectedId() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetMillis(-1800000);
        assertEquals("-00:30", zone.getID());
        assertEquals(-1800000, zone.getOffset(0L));
    }

    // forTimeZone(null) == default zone
    @Test
    public void testForTimeZone_null_returnsDefaultZone() throws Throwable {
        assertEquals(DateTimeZone.getDefault(), DateTimeZone.forTimeZone(null));
    }

    // TimeZone with id "UTC" short-circuits to the UTC singleton
    @Test
    public void testForTimeZone_utcId_returnsUTCInstance() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forTimeZone(TimeZone.getTimeZone("UTC")));
    }

    // legacy "GMT" alias is converted to UTC (conversion map defined in class)
    @Test
    public void testForTimeZone_gmtAlias_mapsToUTC() throws Throwable {
        DateTimeZone zone = DateTimeZone.forTimeZone(TimeZone.getTimeZone("GMT"));
        assertEquals(DateTimeZone.UTC, zone);
    }

    // basic provider accessors must never be null, and UTC must be an available id
    @Test
    public void testGetAvailableIDsAndProviders_notNull() throws Throwable {
        Set<String> ids = DateTimeZone.getAvailableIDs();
        assertNotNull(ids);
        assertTrue(ids.contains("UTC"));
        DateTimeZone.setProvider(null);
        DateTimeZone.setNameProvider(null);
        assertNotNull(DateTimeZone.getProvider());
        assertNotNull(DateTimeZone.getNameProvider());
    }

    // getShortName/getName must return a non-empty name for a fixed locale
    @Test
    public void testGetShortNameAndGetName_returnNonEmptyForUTC() throws Throwable {
        String shortName = DateTimeZone.UTC.getShortName(0L, Locale.US);
        String name = DateTimeZone.UTC.getName(0L, Locale.US);
        assertNotNull(shortName);
        assertTrue(shortName.length() > 0);
        assertNotNull(name);
        assertTrue(name.length() > 0);
    }

    // getOffset(ReadableInstant) with null uses "now", but a fixed-offset zone is constant
    @Test
    public void testGetOffsetReadableInstant_nullUsesCurrentTimeConstantForFixedZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        assertEquals(3 * 3600000, zone.getOffset((ReadableInstant) null));
        assertEquals(3 * 3600000, zone.getOffset(new Instant(0L)));
    }

    // fixed-offset zones built via forOffsetXxx have wall == standard offset always
    @Test
    public void testIsStandardOffset_trueForFixedZones() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(4);
        assertTrue(zone.isStandardOffset(0L));
        assertTrue(DateTimeZone.UTC.isStandardOffset(123456789L));
        assertEquals(zone.getOffset(0L), zone.getStandardOffset(0L));
    }

    // UTC: offsetLocal is 0 so the positive-offset branch is skipped, result stays 0
    @Test
    public void testGetOffsetFromLocal_utcZone_returnsZero() throws Throwable {
        assertEquals(0, DateTimeZone.UTC.getOffsetFromLocal(500000L));
    }

    // fixed offset zone has no transitions, so offsetFromLocal equals the constant offset
    @Test
    public void testGetOffsetFromLocal_positiveFixedOffsetZone_returnsOffset() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        assertEquals(2 * 3600000, zone.getOffsetFromLocal(0L));
    }

    // convertUTCToLocal adds the (constant) offset to the UTC instant
    @Test
    public void testConvertUTCToLocal_basicFixedZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(5);
        assertEquals(5 * 3600000L, zone.convertUTCToLocal(0L));
    }

    // adding the offset to Long.MAX_VALUE overflows and must throw
    @Test
    public void testConvertUTCToLocal_overflow_throwsArithmeticException() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(1);
        try {
            zone.convertUTCToLocal(Long.MAX_VALUE);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // fixed zone: offsetLocal always equals offset, so DST branch is skipped
    @Test
    public void testConvertLocalToUTC_twoArg_basicFixedZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        assertEquals(100000L - 7200000L, zone.convertLocalToUTC(100000L, true));
    }

    // subtracting the offset from Long.MIN_VALUE overflows and must throw
    @Test
    public void testConvertLocalToUTC_twoArg_overflow_throwsArithmeticException() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(1);
        try {
            zone.convertLocalToUTC(Long.MIN_VALUE, false);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
        }
    }

    // when offset derived from original instant matches, the 3-arg shortcut is used directly
    @Test
    public void testConvertLocalToUTC_threeArg_matchingOffset_shortcut() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        long result = zone.convertLocalToUTC(100000L, false, 0L);
        assertEquals(100000L - 7200000L, result);
    }

    // newZone == this (same reference) returns the old instant unchanged
    @Test
    public void testGetMillisKeepLocal_sameReference_returnsOldInstant() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(6);
        assertEquals(500L, zone.getMillisKeepLocal(zone, 500L));
    }

    // converting local wall-clock time between two fixed zones
    @Test
    public void testGetMillisKeepLocal_differentFixedZones_computesConversion() throws Throwable {
        DateTimeZone zone1 = DateTimeZone.forOffsetHours(2);
        DateTimeZone zone2 = DateTimeZone.forOffsetHours(5);
        assertEquals(-10800000L, zone1.getMillisKeepLocal(zone2, 0L));
    }

    // newZone == null uses the (fixed-to-UTC-by-setUp) default zone
    @Test
    public void testGetMillisKeepLocal_nullZone_usesDefault() throws Throwable {
        DateTimeZone zone1 = DateTimeZone.forOffsetHours(2);
        assertEquals(7200000L, zone1.getMillisKeepLocal(null, 0L));
    }

    // fixed zone: before/after wall-clock comparison always falls to the normal path,
    // which for a constant offset resolves back to the original instant
    @Test
    public void testAdjustOffset_fixedZone_returnsOriginalInstant() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        assertEquals(123456789L, zone.adjustOffset(123456789L, true));
        assertEquals(123456789L, zone.adjustOffset(123456789L, false));
    }

    // equal offset zones compare equal; different offset zones do not
    @Test
    public void testEquals_zones_trueAndFalseCases() throws Throwable {
        DateTimeZone a = DateTimeZone.forOffsetHours(3);
        DateTimeZone b = DateTimeZone.forOffsetHours(3);
        DateTimeZone c = DateTimeZone.forOffsetHours(4);
        assertTrue(a.equals(b));
        assertFalse(a.equals(c));
    }

    // hashCode formula is defined directly in this class: 57 + id.hashCode()
    @Test
    public void testHashCode_matchesFormula() throws Throwable {
        assertEquals(57 + "UTC".hashCode(), DateTimeZone.UTC.hashCode());
    }

    // toString() simply returns the id
    @Test
    public void testToString_returnsId() throws Throwable {
        assertEquals("UTC", DateTimeZone.UTC.toString());
    }

    // toTimeZone() delegates to java.util.TimeZone.getTimeZone(id)
    @Test
    public void testToTimeZone_returnsMatchingId() throws Throwable {
        assertEquals("UTC", DateTimeZone.UTC.toTimeZone().getID());
    }
}
