package org.joda.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.junit.Test;
import org.joda.time.tz.FixedDateTimeZone;

public class DateTimeZoneTest {

    @Test
    public void testGetDefaultAndSetDefault() throws Throwable {
        DateTimeZone original = DateTimeZone.getDefault();
        assertNotNull(original);
        try {
            DateTimeZone.setDefault(DateTimeZone.UTC);
            assertSame(DateTimeZone.UTC, DateTimeZone.getDefault());
        } finally {
            DateTimeZone.setDefault(original);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetDefaultNull() throws Throwable {
        DateTimeZone.setDefault(null);
    }

    @Test
    public void testForIDBasic() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forID("UTC"));
        assertSame(DateTimeZone.UTC, DateTimeZone.forID(null));
        
        DateTimeZone zone = DateTimeZone.forID("+01:00");
        assertNotNull(zone);
        assertEquals("+01:00", zone.getID());
        assertEquals(3600000, zone.getOffset(0L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testForIDInvalid() throws Throwable {
        DateTimeZone.forID("Invalid/TimeZone/ID/XYZ");
    }

    @Test
    public void testForOffsetHours() throws Throwable {
        DateTimeZone zone1 = DateTimeZone.forOffsetHours(2);
        assertNotNull(zone1);
        assertEquals("+02:00", zone1.getID());
        assertEquals(7200000, zone1.getOffset(0L));

        DateTimeZone zoneZero = DateTimeZone.forOffsetHours(0);
        assertSame(DateTimeZone.UTC, zoneZero);
    }

    @Test
    public void testForOffsetHoursMinutes() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(1, 30);
        assertNotNull(zone);
        assertEquals("+01:30", zone.getID());
        assertEquals(5400000, zone.getOffset(0L));

        DateTimeZone zoneNeg = DateTimeZone.forOffsetHoursMinutes(-1, 30);
        assertNotNull(zoneNeg);
        assertEquals("-01:30", zoneNeg.getID());
        assertEquals(-5400000, zoneNeg.getOffset(0L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testForOffsetHoursMinutesInvalidMinutesLow() throws Throwable {
        DateTimeZone.forOffsetHoursMinutes(1, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testForOffsetHoursMinutesInvalidMinutesHigh() throws Throwable {
        DateTimeZone.forOffsetHoursMinutes(1, 60);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testForOffsetHoursMinutesTooLarge() throws Throwable {
        DateTimeZone.forOffsetHoursMinutes(99999, 50);
    }

    @Test
    public void testForOffsetMillis() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetMillis(12345);
        assertNotNull(zone);
        assertEquals("+00:00:12.345", zone.getID());
        assertEquals(12345, zone.getOffset(0L));

        assertSame(DateTimeZone.UTC, DateTimeZone.forOffsetMillis(0));
    }

    @Test
    public void testForTimeZone() throws Throwable {
        assertSame(DateTimeZone.UTC, DateTimeZone.forTimeZone(null));
        assertSame(DateTimeZone.UTC, DateTimeZone.forTimeZone(TimeZone.getTimeZone("UTC")));

        DateTimeZone dtz = DateTimeZone.forTimeZone(TimeZone.getTimeZone("America/New_York"));
        assertNotNull(dtz);
        assertEquals("America/New_York", dtz.getID());

        DateTimeZone dtzAlias = DateTimeZone.forTimeZone(TimeZone.getTimeZone("EST"));
        assertNotNull(dtzAlias);
        assertEquals("America/New_York", dtzAlias.getID());

        DateTimeZone gmtPlus = DateTimeZone.forTimeZone(TimeZone.getTimeZone("GMT+02:00"));
        assertNotNull(gmtPlus);
        assertEquals("+02:00", gmtPlus.getID());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testForTimeZoneUnrecognized() throws Throwable {
        TimeZone tz = new TimeZone() {
            public int getOffset(int era, int year, int month, int day, int dayOfWeek, int milliseconds) { return 0; }
            public void setRawOffset(int offsetMillis) {}
            public int getRawOffset() { return 0; }
            public boolean useDaylightTime() { return false; }
            public boolean inDaylightTime(java.util.Date date) { return false; }
            public String getID() { return "UnknownTimeZoneCustom"; }
        };
        DateTimeZone.forTimeZone(tz);
    }

    @Test
    public void testGetAvailableIDs() throws Throwable {
        Set<String> ids = DateTimeZone.getAvailableIDs();
        assertNotNull(ids);
        assertTrue(ids.contains("UTC"));
    }

    @Test
    public void testProviderAndNameProviderGetters() throws Throwable {
        assertNotNull(DateTimeZone.getProvider());
        assertNotNull(DateTimeZone.getNameProvider());
    }

    @Test
    public void testInstanceMethodsOnFixed() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        assertEquals("+03:00", zone.getID());
        assertTrue(zone.isFixed());
        assertEquals(" +03:00", zone.getNameKey(0L) != null ? zone.getNameKey(0L) : " +03:00");
        assertEquals(10800000, zone.getStandardOffset(0L));
        assertEquals(10800000, zone.getOffset(0L));
        assertTrue(zone.isStandardOffset(0L));
        assertEquals(0L, zone.nextTransition(0L));
        assertEquals(0L, zone.previousTransition(0L));
        assertEquals(zone, zone);
        assertFalse(zone.equals(null));
        assertFalse(zone.equals("string"));
        assertTrue(zone.hashCode() != 0);
        assertEquals("+03:00", zone.toString());
        assertNotNull(zone.toTimeZone());
    }

    @Test
    public void testGetShortAndLongName() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(1);
        String shortName = zone.getShortName(0L, Locale.ENGLISH);
        assertNotNull(shortName);
        String longName = zone.getName(0L, Locale.ENGLISH);
        assertNotNull(longName);

        // Test with null locale fallback
        assertNotNull(zone.getShortName(0L, null));
        assertNotNull(zone.getName(0L, null));
    }

    @Test
    public void testGetOffsetReadableInstant() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        assertEquals(7200000, zone.getOffset((ReadableInstant) null));
        assertEquals(7200000, zone.getOffset(new Instant(0L)));
    }

    @Test
    public void testGetOffsetFromLocal() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        assertEquals(7200000, zone.getOffsetFromLocal(0L));
    }

    @Test
    public void testConversions() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        long utc = 1000000L;
        long local = zone.convertUTCToLocal(utc);
        assertEquals(utc + 7200000L, local);

        long backToUtc = zone.convertLocalToUTC(local, false);
        assertEquals(utc, backToUtc);

        long strictUtc = zone.convertLocalToUTC(local, true);
        assertEquals(utc, strictUtc);

        long originalBasedUtc = zone.convertLocalToUTC(local, true, utc);
        assertEquals(utc, originalBasedUtc);
    }

    @Test(expected = ArithmeticException.class)
    public void testConvertUTCToLocalOverflow() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        zone.convertUTCToLocal(Long.MAX_VALUE);
    }

    @Test(expected = ArithmeticException.class)
    public void testConvertLocalToUTCOverflow() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(2);
        zone.convertLocalToUTC(Long.MIN_VALUE, false);
    }

    @Test
    public void testGetMillisKeepLocal() throws Throwable {
        DateTimeZone zone1 = DateTimeZone.forOffsetHours(1);
        DateTimeZone zone2 = DateTimeZone.forOffsetHours(3);
        
        long instant = 1000000L;
        long converted = zone1.getMillisKeepLocal(zone2, instant);
        assertTrue(converted != instant);
        
        assertSame(zone1, zone1.getMillisKeepLocal(zone1, instant));
        assertSame(zone1, zone1.getMillisKeepLocal(null, instant));
    }

    @Test
    public void testIsLocalDateTimeGap() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(1);
        LocalDateTime ldt = new LocalDateTime(2020, 1, 1, 12, 0, 0, 0);
        assertFalse(zone.isLocalDateTimeGap(ldt));

        FixedDateTimeZone fixed = (FixedDateTimeZone) DateTimeZone.forOffsetHours(0);
        assertFalse(fixed.isLocalDateTimeGap(ldt));
    }

    @Test
    public void testSerializationStub() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(4);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
        oos.writeObject(zone);
        oos.close();

        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
        java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);
        Object readObj = ois.readObject();
        ois.close();

        assertEquals(zone, readObj);
    }
}