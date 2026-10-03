package org.joda.time;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.Assert.*;

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
        assertEquals("UTC", DateTimeZone.UTC.toString());
        assertTrue(DateTimeZone.UTC.isFixed());
        assertEquals(0, DateTimeZone.UTC.getOffset(0L));
        assertEquals(0, DateTimeZone.UTC.getStandardOffset(0L));
        assertEquals("UTC", DateTimeZone.UTC.getNameKey(0L));
        assertEquals(0L, DateTimeZone.UTC.nextTransition(0L));
        assertEquals(0L, DateTimeZone.UTC.previousTransition(0L));
    }

    @Test
    public void testGetDefaultAndSetDefault() throws Throwable {
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
    public void testForIDValidAndInvalid() throws Throwable {
        assertEquals(DateTimeZone.UTC, DateTimeZone.forID(null));
        assertEquals(DateTimeZone.UTC, DateTimeZone.forID("UTC"));
        
        DateTimeZone ny = DateTimeZone.forID("America/New_York");
        assertNotNull(ny);
        assertEquals("America/New_York", ny.getID());

        // Fixed offset formats via forID
        DateTimeZone plusTwo = DateTimeZone.forID("+02:00");
        assertNotNull(plusTwo);
        assertEquals(2 * DateTimeConstants.MILLIS_PER_HOUR, plusTwo.getOffset(0L));

        DateTimeZone minusThree = DateTimeZone.forID("-03:00");
        assertNotNull(minusThree);
        assertEquals(-3 * DateTimeConstants.MILLIS_PER_HOUR, minusThree.getOffset(0L));

        DateTimeZone zeroOffset = DateTimeZone.forID("+00:00");
        assertEquals(DateTimeZone.UTC, zeroOffset);

        try {
            DateTimeZone.forID("Invalid/Zone/ID");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("is not recognised"));
        }
    }

    @Test
    public void testForOffsetHours() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(5);
        assertNotNull(zone);
        assertEquals(5 * DateTimeConstants.MILLIS_PER_HOUR, zone.getOffset(0L));

        DateTimeZone zero = DateTimeZone.forOffsetHours(0);
        assertEquals(DateTimeZone.UTC, zero);
    }

    @Test
    public void testForOffsetHoursMinutes() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(2, 30);
        assertNotNull(zone);
        int expected = 2 * DateTimeConstants.MILLIS_PER_HOUR + 30 * DateTimeConstants.MILLIS_PER_MINUTE;
        assertEquals(expected, zone.getOffset(0L));

        DateTimeZone negative = DateTimeZone.forOffsetHoursMinutes(-2, 30);
        assertNotNull(negative);
        int expectedNeg = -2 * DateTimeConstants.MILLIS_PER_HOUR - 30 * DateTimeConstants.MILLIS_PER_MINUTE;
        assertEquals(expectedNeg, zone.getOffset(0L));

        DateTimeZone zero = DateTimeZone.forOffsetHoursMinutes(0, 0);
        assertEquals(DateTimeZone.UTC, zero);

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

        try {
            DateTimeZone.forOffsetHoursMinutes(999999, 0);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Offset is too large"));
        }
    }

    @Test
    public void testForOffsetMillis() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetMillis(12345);
        assertNotNull(zone);
        assertEquals(12345, zone.getOffset(0L));
    }

    @Test
    public void testForTimeZone() throws Throwable {
        assertEquals(DateTimeZone.UTC, DateTimeZone.forTimeZone(null));
        assertEquals(DateTimeZone.UTC, DateTimeZone.forTimeZone(TimeZone.getTimeZone("UTC")));

        TimeZone tzNY = TimeZone.getTimeZone("America/New_York");
        DateTimeZone dtzNY = DateTimeZone.forTimeZone(tzNY);
        assertEquals("America/New_York", dtzNY.getID());

        TimeZone tzShort = TimeZone.getTimeZone("PST");
        DateTimeZone dtzPST = DateTimeZone.forTimeZone(tzShort);
        assertEquals("America/Los_Angeles", dtzPST.getID());

        TimeZone gmtPlus = TimeZone.getTimeZone("GMT+03:00");
        DateTimeZone dtzGmtPlus = DateTimeZone.forTimeZone(gmtPlus);
        assertEquals(3 * DateTimeConstants.MILLIS_PER_HOUR, dtzGmtPlus.getOffset(0L));

        try {
            TimeZone invalid = TimeZone.getTimeZone("InvalidTimeZoneXYZ");
            DateTimeZone.forTimeZone(invalid);
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
    public void testProviderAndNameProviderGettersSetters() throws Throwable {
        assertNotNull(DateTimeZone.getProvider());
        assertNotNull(DateTimeZone.getNameProvider());

        org.joda.time.tz.Provider originalProvider = DateTimeZone.getProvider();
        try {
            DateTimeZone.setProvider(originalProvider);
            assertNotNull(DateTimeZone.getProvider());
        } finally {
            DateTimeZone.setProvider(originalProvider);
        }

        org.joda.time.tz.NameProvider originalNameProvider = DateTimeZone.getNameProvider();
        try {
            DateTimeZone.setNameProvider(originalNameProvider);
            assertNotNull(DateTimeZone.getNameProvider());
        } finally {
            DateTimeZone.setNameProvider(originalNameProvider);
        }
    }

    @Test
    public void testNamesAndOffsets() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        long now = 0L;
        assertNotNull(zone.getNameKey(now));
        assertNotNull(zone.getName(now));
        assertNotNull(zone.getName(now, Locale.US));
        assertNotNull(zone.getShortName(now));
        assertNotNull(zone.getShortName(now, Locale.US));
        
        assertTrue(zone.getOffset(now) != 0);
        assertTrue(zone.getStandardOffset(now) != 0);
        
        boolean isStd = zone.isStandardOffset(now);
        assertEquals(isStd, zone.getOffset(now) == zone.getStandardOffset(now));

        assertNotNull(zone.getOffset((ReadableInstant) null));
        Instant instant = new Instant(now);
        assertEquals(zone.getOffset(now), zone.getOffset(instant));
    }

    @Test
    public void testConversionsAndTransitions() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        long now = 0L;

        long local = zone.convertUTCToLocal(now);
        long utc = zone.convertLocalToUTC(local, false);
        assertTrue(utc != 0L || now == 0L);

        long utcStrict = zone.convertLocalToUTC(local, true);
        assertEquals(utc, utcStrict);

        long keepLocal = zone.getMillisKeepLocal(DateTimeZone.UTC, now);
        assertTrue(keepLocal != now);

        long next = zone.nextTransition(now);
        long prev = zone.previousTransition(next);
        assertTrue(prev <= next);

        assertFalse(zone.isFixed());
        
        TimeZone jdkTz = zone.toTimeZone();
        assertNotNull(jdkTz);

        assertEquals(zone, zone);
        assertFalse(zone.equals(null));
        assertFalse(zone.equals("NotAZone"));
        assertTrue(zone.hashCode() != 0);
    }

    @Test
    public void testOffsetFromLocalAndAdjustOffset() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        long now = 1318128000000L; // Fall back transition area approx (Nov 2011)
        
        int offsetFromLocal = zone.getOffsetFromLocal(now);
        assertTrue(offsetFromLocal != 0);

        long adjusted = zone.adjustOffset(now, true);
        assertTrue(adjusted != 0L);
        
        long adjustedEarlier = zone.adjustOffset(now, false);
        assertTrue(adjustedEarlier != 0L);
    }

    @Test
    public void testLocalDateTimeGapAndOverlap() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        LocalDateTime ldt = new LocalDateTime(2011, 3, 13, 2, 30, 0, 0); // Spring forward gap
        
        boolean isGap = zone.isLocalDateTimeGap(ldt);
        assertTrue(isGap);

        LocalDateTime ldtNormal = new LocalDateTime(2011, 6, 1, 12, 0, 0, 0);
        assertFalse(zone.isLocalDateTimeGap(ldtNormal));

        FixedDateTimeZone fixed = (FixedDateTimeZone) DateTimeZone.UTC;
        assertFalse(fixed.isLocalDateTimeGap(ldtNormal));
    }

    @Test
    public void testConvertLocalToUTCExceptions() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        LocalDateTime ldtGap = new LocalDateTime(2011, 3, 13, 2, 30, 0, 0);
        long localMillis = ldtGap.toDateTime(DateTimeZone.UTC).getMillis();

        try {
            zone.convertLocalToUTC(localMillis, true);
            fail("Should throw IllegalArgumentException for illegal instant in gap");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Illegal instant due to time zone offset transition"));
        }

        // Test non-strict in gap
        long utcNonStrict = zone.convertLocalToUTC(localMillis, false);
        assertTrue(utcNonStrict != 0L);
    }

    @Test
    public void testSerializationStub() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        Object stub = zone.writeReplace();
        assertNotNull(stub);
    }

    @Test
    public void testConstructorAndSubclassEdgeCases() throws Throwable {
        DateTimeZone customZone = new DateTimeZone("Custom/Zone") {
            public String getNameKey(long instant) { return "CST"; }
            public int getOffset(long instant) { return 3600000; }
            public int getStandardOffset(long instant) { return 3600000; }
            public boolean isFixed() { return true; }
            public long nextTransition(long instant) { return instant; }
            public long previousTransition(long instant) { return instant; }
            public boolean equals(Object object) { return object instanceof DateTimeZone && getID().equals(((DateTimeZone) object).getID()); }
        };

        assertEquals("Custom/Zone", customZone.getID());
        assertEquals("Custom/Zone", customZone.toString());
        assertTrue(customZone.isFixed());

        try {
            new DateTimeZone(null) {
                public String getNameKey(long instant) { return null; }
                public int getOffset(long instant) { return 0; }
                public int getStandardOffset(long instant) { return 0; }
                public boolean isFixed() { return true; }
                public long nextTransition(long instant) { return 0; }
                public long previousTransition(long instant) { return 0; }
                public boolean equals(Object object) { return false; }
            };
            fail("Should throw IllegalArgumentException for null id");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Id must not be null"));
        }
    }
}