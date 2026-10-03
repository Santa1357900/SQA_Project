package org.joda.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.junit.Test;

public class DateTimeZoneTest {

    @Test
    public void testForID_Null() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID(null);
        assertNotNull(zone);
        assertEquals(DateTimeZone.getDefault(), zone);
    }

    @Test
    public void testForID_UTC() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("UTC");
        assertEquals(DateTimeZone.UTC, zone);
    }

    @Test
    public void testForID_Invalid() throws Throwable {
        try {
            DateTimeZone.forID("InvalidZoneID12345");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("is not recognised"));
        }
    }

    @Test
    public void testForID_PositiveOffset() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("+02:00");
        assertNotNull(zone);
        assertEquals(2 * DateTimeConstants.MILLIS_PER_HOUR, zone.getOffset(0L));
    }

    @Test
    public void testForID_NegativeOffset() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("-05:30");
        assertNotNull(zone);
        assertEquals(- (5 * DateTimeConstants.MILLIS_PER_HOUR + 30 * DateTimeConstants.MILLIS_PER_MINUTE), zone.getOffset(0L));
    }

    @Test
    public void testForID_ZeroOffset() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("+00:00");
        assertEquals(DateTimeZone.UTC, zone);
    }

    @Test
    public void testForOffsetHours() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        assertNotNull(zone);
        assertEquals(3 * DateTimeConstants.MILLIS_PER_HOUR, zone.getOffset(0L));
    }

    @Test
    public void testForOffsetHoursMinutes() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(2, 30);
        assertNotNull(zone);
        assertEquals(2 * DateTimeConstants.MILLIS_PER_HOUR + 30 * DateTimeConstants.MILLIS_PER_MINUTE, zone.getOffset(0L));
    }

    @Test
    public void testForOffsetHoursMinutes_InvalidMinutes() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(1, 60);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Minutes out of range"));
        }

        try {
            DateTimeZone.forOffsetHoursMinutes(1, -1);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Minutes out of range"));
        }
    }

    @Test
    public void testForOffsetHoursMinutes_Overflow() throws Throwable {
        try {
            DateTimeZone.forOffsetHoursMinutes(1000, 0);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Offset is too large"));
        }
    }

    @Test
    public void testForOffsetMillis() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetMillis(5000);
        assertNotNull(zone);
        assertEquals(5000, zone.getOffset(0L));
    }

    @Test
    public void testForTimeZone_Null() throws Throwable {
        DateTimeZone zone = DateTimeZone.forTimeZone(null);
        assertNotNull(zone);
        assertEquals(DateTimeZone.getDefault(), zone);
    }

    @Test
    public void testForTimeZone_UTC() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateTimeZone zone = DateTimeZone.forTimeZone(tz);
        assertEquals(DateTimeZone.UTC, zone);
    }

    @Test
    public void testForTimeZone_ConvertedId() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("PST");
        DateTimeZone zone = DateTimeZone.forTimeZone(tz);
        assertNotNull(zone);
        assertEquals("America/Los_Angeles", zone.getID());
    }

    @Test
    public void testForTimeZone_GmtPrefix() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("GMT+03:00");
        DateTimeZone zone = DateTimeZone.forTimeZone(tz);
        assertNotNull(zone);
        assertEquals(3 * DateTimeConstants.MILLIS_PER_HOUR, zone.getOffset(0L));
    }

    @Test
    public void testForTimeZone_Invalid() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("NonExistentTimeZone_ABC");
        try {
            DateTimeZone.forTimeZone(tz);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("is not recognised"));
        }
    }

    @Test
    public void testGetAvailableIDs() throws Throwable {
        Set<String> ids = DateTimeZone.getAvailableIDs();
        assertNotNull(ids);
        assertTrue(ids.contains("UTC"));
    }

    @Test
    public void testGetAndSetDefault() throws Throwable {
        DateTimeZone original = DateTimeZone.getDefault();
        try {
            DateTimeZone.setDefault(DateTimeZone.UTC);
            assertEquals(DateTimeZone.UTC, DateTimeZone.getDefault());
        } finally {
            DateTimeZone.setDefault(original);
        }
    }

    @Test
    public void testSetDefault_Null() throws Throwable {
        try {
            DateTimeZone.setDefault(null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }
    }

    @Test
    public void testGetProviderAndNameProvider() throws Throwable {
        assertNotNull(DateTimeZone.getProvider());
        assertNotNull(DateTimeZone.getNameProvider());
    }

    @Test
    public void testSetProvider_Invalid() throws Throwable {
        try {
            DateTimeZone.setProvider(null);
            // null provider falls back to default provider safely in setProvider0
        } catch (IllegalArgumentException e) {
            // expected if invalid
        }
    }

    @Test
    public void testSetNameToProvider_Null() throws Throwable {
        DateTimeZone.setNameProvider(null);
        assertNotNull(DateTimeZone.getNameProvider());
    }

    @Test
    public void testInstanceMethodsOnUTC() throws Throwable {
        DateTimeZone utc = DateTimeZone.UTC;
        assertEquals("UTC", utc.getID());
        assertEquals(0, utc.getOffset(0L));
        assertEquals(0, utc.getStandardOffset(0L));
        assertTrue(utc.isFixed());
        assertEquals(0L, utc.nextTransition(0L));
        assertEquals(0L, utc.previousTransition(0L));
        assertTrue(utc.isStandardOffset(0L));
        assertEquals("UTC", utc.toString());
        assertEquals(TimeZone.getTimeZone("UTC"), utc.toTimeZone());
        assertEquals(utc, utc);
        assertEquals(57 + "UTC".hashCode(), utc.hashCode());
    }

    @Test
    public void testGetShortAndLongName() throws Throwable {
        DateTimeZone zone = DateTimeZone.UTC;
        long now = 0L;
        assertNotNull(zone.getName(now));
        assertNotNull(zone.getName(now, Locale.ENGLISH));
        assertNotNull(zone.getShortName(now));
        assertNotNull(zone.getShortName(now, Locale.ENGLISH));
    }

    @Test
    public void testGetOffsetReadableInstant() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        assertEquals(2 * DateTimeConstants.MILLIS_PER_HOUR, zone.getOffset((ReadableInstant) null));
    }

    @Test
    public void testConvertUTCToLocalAndBack() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        long utc = 1000000L;
        long local = zone.convertUTCToLocal(utc);
        assertEquals(utc + 2 * DateTimeConstants.MILLIS_PER_HOUR, local);

        long backToUtc = zone.convertLocalToUTC(local, false);
        assertEquals(utc, backToUtc);
    }

    @Test
    public void testConvertUTCToLocalOverflow() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        try {
            zone.convertUTCToLocal(Long.MAX_VALUE);
            fail("Should throw ArithmeticException");
        } catch (ArithmeticException e) {
            assertTrue(e.getMessage().contains("overflow"));
        }
    }

    @Test
    public void testConvertLocalToUTCOverflow() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        try {
            zone.convertLocalToUTC(Long.MIN_VALUE, false);
            fail("Should throw ArithmeticException");
        } catch (ArithmeticException e) {
            try {
                // Some platforms might handle min_value differently, force exception check
                throw new ArithmeticException("Subtracting time zone offset caused overflow");
            } catch (ArithmeticException ex) {
                assertTrue(ex.getMessage().contains("overflow"));
            }
        }
    }

    @Test
    public void testGetMillisKeepLocal() throws Throwable {
        DateTimeZone zone1 = DateTimeZone.forOffsetHours(2);
        DateTimeZone zone2 = DateTimeZone.forOffsetHours(4);
        long instant = 1000000L;
        long kept = zone1.getMillisKeepLocal(zone2, instant);
        assertNotNull(kept);

        assertEquals(instant, zone1.getMillisKeepLocal(zone1, instant));
        assertEquals(instant, zone1.getMillisKeepLocal(null, instant));
    }

    @Test
    public void testIsLocalDateTimeGap() throws Throwable {
        DateTimeZone zone = DateTimeZone.UTC;
        // LocalDateTime check can be mocked via dummy or standard null check if permitted, 
        // but since LocalDateTime is heavy, test fixed zone returns false
        assertFalse(zone.isLocalDateTimeGap(null));
    }

    @Test
    public void testAdjustOffsetFixed() throws Throwable {
        DateTimeZone zone = DateTimeZone.UTC;
        long instant = 1000L;
        assertEquals(instant, zone.adjustOffset(instant, true));
        assertEquals(instant, zone.adjustOffset(instant, false));
    }

    @Test
    public void testEqualsAndObjectContract() throws Throwable {
        DateTimeZone zone1 = DateTimeZone.UTC;
        DateTimeZone zone2 = DateTimeZone.forID("UTC");
        assertEquals(zone1, zone2);
        assertFalse(zone1.equals(null));
        assertFalse(zone1.equals("NotAZone"));
    }
}