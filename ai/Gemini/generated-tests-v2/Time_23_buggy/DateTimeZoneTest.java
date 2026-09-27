package org.joda.time;

import static org.junit.Assert.*;

import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.joda.time.tz.FixedDateTimeZone;

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
        assertEquals(DateTimeZone.UTC, DateTimeZone.UTC);
        assertFalse(DateTimeZone.UTC.equals(null));
        assertFalse(DateTimeZone.UTC.equals("NotAZone"));
        
        assertEquals(57 + "UTC".hashCode(), DateTimeZone.UTC.hashCode());
        
        Set<String> ids = DateTimeZone.getAvailableIDs();
        assertNotNull(ids);
        assertTrue(ids.contains("UTC"));
    }

    @Test
    public void testGetDefaultAndSetDefault() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("Europe/Paris");
        DateTimeZone.setDefault(zone);
        assertEquals(zone, DateTimeZone.getDefault());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetDefaultNull() throws Throwable {
        DateTimeZone.setDefault(null);
    }

    @Test
    public void testForID() throws Throwable {
        assertEquals(DateTimeZone.UTC, DateTimeZone.forID(null));
        assertEquals(DateTimeZone.UTC, DateTimeZone.forID("UTC"));
        
        DateTimeZone gmtPlus2 = DateTimeZone.forID("+02:00");
        assertNotNull(gmtPlus2);
        assertEquals(2 * DateTimeConstants.MILLIS_PER_HOUR, gmtPlus2.getOffset(0L));

        DateTimeZone gmtMinus3 = DateTimeZone.forID("-03:30");
        assertNotNull(gmtMinus3);
        assertEquals(-(3 * DateTimeConstants.MILLIS_PER_HOUR + 30 * DateTimeConstants.MILLIS_PER_MINUTE), gmtMinus3.getOffset(0L));

        DateTimeZone gmtZero = DateTimeZone.forID("+00:00");
        assertEquals(DateTimeZone.UTC, gmtZero);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testForIDInvalid() throws Throwable {
        DateTimeZone.forID("Invalid/ZoneID/XYZ");
    }

    @Test
    public void testForOffsetHours() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(5);
        assertNotNull(zone);
        assertEquals(5 * DateTimeConstants.MILLIS_PER_HOUR, zone.getOffset(0L));

        DateTimeZone zeroZone = DateTimeZone.forOffsetHours(0);
        assertEquals(DateTimeZone.UTC, zeroZone);
    }

    @Test
    public void testForOffsetHoursMinutes() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(2, 30);
        assertNotNull(zone);
        assertEquals(2 * DateTimeConstants.MILLIS_PER_HOUR + 30 * DateTimeConstants.MILLIS_PER_MINUTE, zone.getOffset(0L));

        DateTimeZone negZone = DateTimeZone.forOffsetHoursMinutes(-1, 15);
        assertEquals(-(1 * DateTimeConstants.MILLIS_PER_HOUR + 15 * DateTimeConstants.MILLIS_PER_MINUTE), negZone.getOffset(0L));

        DateTimeZone zeroZone = DateTimeZone.forOffsetHoursMinutes(0, 0);
        assertEquals(DateTimeZone.UTC, zeroZone);
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
        DateTimeZone.forOffsetHoursMinutes(1000, 0);
    }

    @Test
    public void testForOffsetMillis() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetMillis(12345);
        assertNotNull(zone);
        assertEquals(12345, zone.getOffset(0L));

        assertEquals(DateTimeZone.UTC, DateTimeZone.forOffsetMillis(0));
    }

    @Test
    public void testForTimeZone() throws Throwable {
        assertNotNull(DateTimeZone.forTimeZone(null));
        assertEquals(DateTimeZone.UTC, DateTimeZone.forTimeZone(TimeZone.getTimeZone("UTC")));
        
        DateTimeZone dtz = DateTimeZone.forTimeZone(TimeZone.getTimeZone("America/New_York"));
        assertNotNull(dtz);

        DateTimeZone dtzAlias = DateTimeZone.forTimeZone(TimeZone.getTimeZone("EST"));
        assertNotNull(dtzAlias);

        TimeZone customTz = TimeZone.getTimeZone("GMT+04:00");
        DateTimeZone customDtz = DateTimeZone.forTimeZone(customTz);
        assertNotNull(customDtz);
        assertEquals(4 * DateTimeConstants.MILLIS_PER_HOUR, customDtz.getOffset(0L));

        TimeZone customTzNeg = TimeZone.getTimeZone("GMT-05:30");
        DateTimeZone customDtzNeg = DateTimeZone.forTimeZone(customTzNeg);
        assertNotNull(customDtzNeg);
        assertEquals(-(5 * DateTimeConstants.MILLIS_PER_HOUR + 30 * DateTimeConstants.MILLIS_PER_MINUTE), customDtzNeg.getOffset(0L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testForTimeZoneInvalid() throws Throwable {
        TimeZone tz = new TimeZone() {
            public int getOffset(int era, int year, int month, int day, int dayOfWeek, int milliseconds) { return 0; }
            public void setRawOffset(int offsetMillis) {}
            public int getRawOffset() { return 0; }
            public boolean useDaylightTime() { return false; }
            public boolean inDaylightTime(java.util.Date date) { return false; }
        };
        tz.setID("InvalidCustomTimeZoneXYZ");
        DateTimeZone.forTimeZone(tz);
    }

    @Test
    public void testProvidersAndNameProviders() throws Throwable {
        assertNotNull(DateTimeZone.getProvider());
        assertNotNull(DateTimeZone.getNameProvider());
        
        DateTimeZone.setNameProvider(DateTimeZone.getNameProvider());
        DateTimeZone.setProvider(DateTimeZone.getProvider());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetProviderNullIds() throws Throwable {
        DateTimeZone.setProvider(new org.joda.time.tz.UTCProvider() {
            public Set<String> getAvailableIDs() {
                return null;
            }
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetProviderNoUTC() throws Throwable {
        DateTimeZone.setProvider(new org.joda.time.tz.UTCProvider() {
            public Set<String> getAvailableIDs() {
                java.util.HashSet<String> set = new java.util.HashSet<String>();
                set.add("America/New_York");
                return set;
            }
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetProviderInvalidUTC() throws Throwable {
        DateTimeZone.setProvider(new org.joda.time.tz.UTCProvider() {
            public DateTimeZone getZone(String id) {
                if (id.equals("UTC")) {
                    return DateTimeZone.forOffsetHours(1);
                }
                return super.getZone(id);
            }
        });
    }

    @Test
    public void testInstanceMethodsOnFixedZone() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("+01:00");
        long now = 0L;
        
        assertEquals("+01:00", zone.getID());
        assertEquals(3600000, zone.getOffset(now));
        assertEquals(3600000, zone.getStandardOffset(now));
        assertTrue(zone.isStandardOffset(now));
        assertEquals("+01:00", zone.getNameKey(now));
        
        assertNotNull(zone.getName(now));
        assertNotNull(zone.getName(now, Locale.ENGLISH));
        assertNotNull(zone.getShortName(now));
        assertNotNull(zone.getShortName(now, Locale.ENGLISH));
        
        assertEquals(now, zone.nextTransition(now));
        assertEquals(now, zone.previousTransition(now));
        
        assertNotNull(zone.toTimeZone());
        
        ReadableInstant ri = new Instant(now);
        assertEquals(3600000, zone.getOffset(ri));
        assertEquals(3600000, zone.getOffset((ReadableInstant) null));
    }

    @Test
    public void testConversionsAndOffsets() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("America/New_York");
        long now = 0L;
        
        int offset = zone.getOffset(now);
        long utcToLocal = zone.convertUTCToLocal(now);
        assertEquals(now + offset, utcToLocal);

        long localToUtc = zone.convertLocalToUTC(utcToLocal, false);
        assertEquals(now, localToUtc);

        long localToUtcStrict = zone.convertLocalToUTC(utcToLocal, true);
        assertEquals(now, localToUtcStrict);

        long localToUtcOriginal = zone.convertLocalToUTC(utcToLocal, true, now);
        assertEquals(now, localToUtcOriginal);

        long keepLocal = zone.getMillisKeepLocal(DateTimeZone.UTC, now);
        assertNotNull(keepLocal);

        long keepLocalNullZone = zone.getMillisKeepLocal(null, now);
        assertNotNull(keepLocalNullZone);

        long keepLocalSameZone = zone.getMillisKeepLocal(zone, now);
        assertEquals(now, keepLocalSameZone);

        assertFalse(zone.isLocalDateTimeGap(new LocalDateTime(now, zone)));
        
        long adjusted = zone.adjustOffset(now, true);
        assertNotNull(adjusted);
    }

    @Test(expected = ArithmeticException.class)
    public void testConvertUTCToLocalOverflow() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(12);
        zone.convertUTCToLocal(Long.MAX_VALUE);
    }

    @Test(expected = ArithmeticException.class)
    public void testConvertLocalToUTCOverflow() throws Throwable {
        DateTimeZone zone = DateTimeZone.forOffsetHours(12);
        zone.convertLocalToUTC(Long.MIN_VALUE, false);
    }

    @Test
    public void testSerialization() throws Throwable {
        DateTimeZone zone = DateTimeZone.forID("Europe/London");
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
        oos.writeObject(zone);
        oos.close();

        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
        java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);
        Object readObject = ois.readObject();
        ois.close();

        assertEquals(zone, readObject);
    }

    @Test
    public void testDummyChronologyInParseOffset() throws Throwable {
        DateTimeZone z = DateTimeZone.forID("-05:00");
        assertNotNull(z);
    }
}