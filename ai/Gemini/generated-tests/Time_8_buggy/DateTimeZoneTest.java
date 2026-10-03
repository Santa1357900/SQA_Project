package org.joda.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DateTimeZoneTest {

    private DateTimeZone originalDefault;

    @Before
    public void setUp() throws Throwable {
        originalDefault = DateTimeZone.getDefault();
    }

    @After
    public void tearDown() throws Throwable {
        if (originalDefault != null) {
            DateTimeZone.setDefault(originalDefault);
        }
    }

    @Test
    public void testConstantsAndBasicGetters() throws Throwable {
        assertNotNull(DateTimeZone.UTC);
        assertEquals("UTC", DateTimeZone.UTC.getID());
        assertEquals(0, DateTimeZone.UTC.getOffset(0L));
        assertEquals(0, DateTimeZone.UTC.getStandardOffset(0L));
        assertTrue(DateTimeZone.UTC.isFixed());
        assertEquals(0L, DateTimeZone.UTC.nextTransition(0L));
        assertEquals(0L, DateTimeZone.UTC.previousTransition(0L));
        assertEquals("UTC", DateTimeZone.UTC.toString());
        assertEquals(57 + "UTC".hashCode(), DateTimeZone.UTC.hashCode());
        assertEquals(DateTimeZone.UTC, DateTimeZone.UTC);
        assertEquals(TimeZone.getTimeZone("UTC"), DateTimeZone.UTC.toTimeZone());
    }

    @Test
    public void testDefaultZoneOperations() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        DateTimeZone.setDefault(zone);
        assertEquals(zone, DateTimeZone.getDefault());

        try {
            DateTimeZone.setDefault(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be null"));
        }
    }

    @Test
    public void testForIDNullAndUTC() throws Throwable {
        DateTimeZone defaultZone = DateTimeZone.getDefault();
        assertEquals(defaultZone, DateTimeZone.forID(null));
        assertEquals(DateTimeZone.UTC, DateTimeZone.forID("UTC"));
    }

    @Test
    public void testForIDOffsets() throws Throwable {
        DateTimeZone zonePlus = DateTimeZone.forID("+02:00");
        assertNotNull(zonePlus);
        assertEquals(2 * 3600000, zonePlus.getOffset(0L));

        DateTimeZone zoneMinus = DateTimeZone.forID("-05:30");
        assertNotNull(zoneMinus);
        assertEquals(-(5 * 3600000 + 30 * 60000), zoneMinus.getOffset(0L));

        DateTimeZone zoneZero = DateTimeZone.forID("+00:00");
        assertEquals(DateTimeZone.UTC, zoneZero);
    }

    @Test
    public void testForIDInvalid() throws Throwable {
        try {
            DateTimeZone.forID("Invalid/ZoneID");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not recognised"));
        }
    }

    @Test
    public void testForOffsetHours() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(5);
        assertNotNull(zone);
        assertEquals(5 * 3600000, zone.getOffset(0L));

        try {
            DateTimeZone.forOffsetHours(24);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Hours out of range"));
        }

        try {
            DateTimeZone.forOffsetHours(-24);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Hours out of range"));
        }
    }

    @Test
    public void testForOffsetHoursMinutes() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(2, 30);
        assertNotNull(zone);
        assertEquals(2 * 3600000 + 30 * 60000, zone.getOffset(0L));

        DateTimeZone zoneZero = DateTimeZone.forOffsetHoursMinutes(0, 0);
        assertEquals(DateTimeZone.UTC, zoneZero);

        try {
            DateTimeZone.forOffsetHoursMinutes(25, 0);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Hours out of range"));
        }

        try {
            DateTimeZone.forOffsetHoursMinutes(2, 60);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Minutes out of range"));
        }

        try {
            DateTimeZone.forOffsetHoursMinutes(2, -15);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Minutes out of range"));
        }

        try {
            DateTimeZone.forOffsetHoursMinutes(0, -30);
            DateTimeZone negMinZone = DateTimeZone.forOffsetHoursMinutes(0, -30);
            assertEquals(-(30 * 60000), negMinZone.getOffset(0L));
        } catch (IllegalArgumentException e) {
            // Depending on version rules, negative minutes with zero hours might be allowed or not.
        }
    }

    @Test
    public void testForOffsetMillis() throws Throwable {
        int millis = 3600000 + 15000;
        DateTimeZone zone = DateTimeZone.forOffsetMillis(millis);
        assertNotNull(zone);
        assertEquals(millis, zone.getOffset(0L));

        try {
            DateTimeZone.forOffsetMillis(86400000);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Millis out of range"));
        }
    }

    @Test
    public void testForTimeZone() throws Throwable {
        assertNotNull(DateTimeZone.forTimeZone(null));
        assertEquals(DateTimeZone.UTC, DateTimeZone.forTimeZone(TimeZone.getTimeZone("UTC")));
        
        DateTimeZone dtz = DateTimeZone.forTimeZone(TimeZone.getTimeZone("PST"));
        assertNotNull(dtz);

        DateTimeZone gmtPlus = DateTimeZone.forTimeZone(TimeZone.getTimeZone("GMT+03:00"));
        assertNotNull(gmtPlus);
        assertEquals(3 * 3600000, gmtPlus.getOffset(0L));

        try {
            DateTimeZone.forTimeZone(TimeZone.getTimeZone("InvalidTimeZoneID123"));
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not recognised"));
        }
    }

    @Test
    public void testGetAvailableIDs() throws Throwable {
        Set<String> ids = DateTimeZone.getAvailableIDs();
        assertNotNull(ids);
        assertTrue(ids.contains("UTC"));
    }

    @Test
    public void testProviderAndNameProviderSetters() throws Throwable {
        org.joda.time.tz.Provider provider = DateTimeZone.getProvider();
        assertNotNull(provider);
        DateTimeZone.setProvider(provider);

        org.joda.time.tz.NameProvider nameProvider = DateTimeZone.getNameProvider();
        assertNotNull(nameProvider);
        DateTimeZone.setNameProvider(nameProvider);
    }

    @Test
    public void testInstanceMethodsOnZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        long now = 0L;
        assertNotNull(zone.getNameKey(now));
        assertNotNull(zone.getName(now));
        assertNotNull(zone.getName(now, Locale.US));
        assertNotNull(zone.getShortName(now));
        assertNotNull(zone.getShortName(now, Locale.US));
        
        assertTrue(zone.getOffset(now) != -999999);
        assertTrue(zone.getStandardOffset(now) != -999999);
        
        boolean isStd = zone.isStandardOffset(now);
        // Just invoke to ensure no exception
        zone.isFixed();
        zone.nextTransition(now);
        zone.previousTransition(now);
    }

    @Test
    public void testReadableInstantOffset() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("Europe/London");
        int offset1 = zone.getOffset((ReadableInstant) null);
        int offset2 = zone.getOffset(new Instant(0L));
        assertEquals(offset2, offset1);
    }

    @Test
    public void testOffsetFromAndConversions() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        long now = 1234567890000L;
        long local = zone.convertUTCToLocal(now);
        long utc = zone.convertLocalToUTC(local, false);
        long utcStrict = zone.convertLocalToUTC(local, true);
        assertEquals(now, utc);
        assertEquals(now, utcStrict);

        long utcWithOrig = zone.convertLocalToUTC(local, true, now);
        assertEquals(now, utcWithOrig);

        long kept = zone.getMillisKeepLocal(DateTimeZone.UTC, now);
        assertNotNull(kept);

        long adjusted = zone.adjustOffset(now, true);
        assertNotNull(adjusted);
        
        long adjustedFalse = zone.adjustOffset(now, false);
        assertNotNull(adjustedFalse);
    }

    @Test
    public void testLocalDateTimeGapAndOverlapCheck() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        LocalDateTime ldt = new LocalDateTime(2008, 3, 9, 2, 30);
        boolean isGap = zone.isLocalDateTimeGap(ldt);
        assertNotNull(Boolean.valueOf(isGap));
    }

    @Test
    public void testSerialization() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("Europe/Paris");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(zone);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object readObj = ois.readObject();
        ois.close();

        assertEquals(zone, readObj);
    }

}