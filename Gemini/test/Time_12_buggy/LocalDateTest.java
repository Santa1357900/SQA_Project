package org.joda.time;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.joda.time.chrono.ISOChronology;
import org.joda.time.chrono.GJChronology;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class LocalDate_ESTest {

    private DateTimeZone originalDateTimeZone;

    @Before
    public void setUp() throws Throwable {
        originalDateTimeZone = DateTimeZone.getDefault();
        DateTimeZone.setDefault(DateTimeZone.UTC);
    }

    @After
    public void tearDown() throws Throwable {
        DateTimeZone.setDefault(originalDateTimeZone);
    }

    @Test
    public void testNowFactories() throws Throwable {
        LocalDate dt1 = LocalDate.now();
        assertNotNull(dt1);

        LocalDate dt2 = LocalDate.now(DateTimeZone.UTC);
        assertNotNull(dt2);

        LocalDate dt3 = LocalDate.now(ISOChronology.getInstanceUTC());
        assertNotNull(dt3);

        try {
            LocalDate.now((DateTimeZone) null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("Zone"));
        }

        try {
            LocalDate.now((Chronology) null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("Chronology"));
        }
    }

    @Test
    public void testParseMethods() throws Throwable {
        LocalDate dt = LocalDate.parse("2012-06-05");
        assertEquals(2012, dt.getYear());
        assertEquals(6, dt.getMonthOfYear());
        assertEquals(5, dt.getDayOfMonth());

        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy/MM/dd");
        LocalDate dt2 = LocalDate.parse("2012/06/05", fmt);
        assertEquals(dt, dt2);
    }

    @Test
    public void testFromCalendarAndDateFields() throws Throwable {
        Calendar cal = Calendar.getInstance();
        cal.set(2010, Calendar.JANUARY, 15);
        LocalDate dtCal = LocalDate.fromCalendarFields(cal);
        assertEquals(2010, dtCal.getYear());
        assertEquals(1, dtCal.getMonthOfYear());
        assertEquals(15, dtCal.getDayOfMonth());

        try {
            LocalDate.fromCalendarFields(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("calendar"));
        }

        Date date = new Date(110, 5, 20); // Year 2010 (1900 + 110), June 20, 20
        LocalDate dtDate = LocalDate.fromDateFields(date);
        assertEquals(2010, dtDate.getYear());
        assertEquals(6, dtDate.getMonthOfYear());
        assertEquals(20, dtDate.getDayOfMonth());

        try {
            LocalDate.fromDateFields(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("date"));
        }
    }

    @Test
    public void testConstructorsWithInstantAndChronology() throws Throwable {
        long millis = 0L;
        LocalDate dt1 = new LocalDate(millis);
        assertEquals(1970, dt1.getYear());

        LocalDate dt2 = new LocalDate(millis, DateTimeZone.UTC);
        assertEquals(1970, dt2.getYear());

        LocalDate dt3 = new LocalDate(millis, (Chronology) null);
        assertEquals(1970, dt3.getYear());

        LocalDate dt4 = new LocalDate(DateTimeZone.UTC);
        assertNotNull(dt4);

        LocalDate dt5 = new LocalDate((Chronology) null);
        assertNotNull(dt5);
    }

    @Test
    public void testObjectConstructors() throws Throwable {
        LocalDate dtBase = new LocalDate(2011, 4, 3);
        LocalDate dtObj1 = new LocalDate(dtBase);
        assertEquals(dtBase, dtObj1);

        LocalDate dtObj2 = new LocalDate(dtBase, DateTimeZone.UTC);
        assertEquals(dtBase, dtObj2);

        LocalDate dtObj3 = new LocalDate(dtBase, ISOChronology.getInstanceUTC());
        assertEquals(dtBase, dtObj3);

        LocalDate dtStr = new LocalDate("2011-04-03");
        assertEquals(dtBase, dtStr);
    }

    @Test
    public void testSizeAndFieldGetters() throws Throwable {
        LocalDate dt = new LocalDate(2012, 5, 10);
        assertEquals(3, dt.size());
        assertEquals(2012, dt.getValue(0));
        assertEquals(5, dt.getValue(1));
        assertEquals(10, dt.getValue(2));

        try {
            dt.getValue(-1);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        try {
            dt.getValue(3);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }

    @Test
    public void testGetDateTimeFieldAndSupport() throws Throwable {
        LocalDate dt = new LocalDate(2012, 5, 10);
        assertEquals(2012, dt.get(DateTimeFieldType.year()));
        assertEquals(5, dt.get(DateTimeFieldType.monthOfYear()));
        assertEquals(10, dt.get(DateTimeFieldType.dayOfMonth()));

        try {
            dt.get(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            dt.get(DateTimeFieldType.hourOfDay());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertTrue(dt.isSupported(DateTimeFieldType.year()));
        assertTrue(dt.isSupported(DurationFieldType.days()));
        assertFalse(dt.isSupported((DateTimeFieldType) null));
        assertFalse(dt.isSupported((DurationFieldType) null));
        assertFalse(dt.isSupported(DurationFieldType.hours()));
    }

    @Test
    public void testEqualsAndHashCodeAndCompareTo() throws Throwable {
        LocalDate dt1 = new LocalDate(2012, 5, 10);
        LocalDate dt2 = new LocalDate(2012, 5, 10);
        LocalDate dt3 = new LocalDate(2012, 5, 11);

        assertTrue(dt1.equals(dt1));
        assertTrue(dt1.equals(dt2));
        assertFalse(dt1.equals(dt3));
        assertFalse(dt1.equals("SomeString"));
        assertFalse(dt1.equals(null));

        assertEquals(dt1.hashCode(), dt2.hashCode());

        assertEquals(0, dt1.compareTo(dt2));
        assertTrue(dt1.compareTo(dt3) < 0);
        assertTrue(dt3.compareTo(dt1) > 0);

        try {
            dt1.compareTo(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testConversionsToDateTimeAndOthers() throws Throwable {
        LocalDate dt = new LocalDate(2012, 5, 10);
        assertNotNull(dt.toDateTimeAtStartOfDay());
        assertNotNull(dt.toDateTimeAtStartOfDay(DateTimeZone.UTC));

        assertNotNull(dt.toDateTimeAtMidnight());
        assertNotNull(dt.toDateTimeAtMidnight(DateTimeZone.UTC));

        assertNotNull(dt.toDateTimeAtCurrentTime());
        assertNotNull(dt.toDateTimeAtCurrentTime(DateTimeZone.UTC));

        assertNotNull(dt.toDateMidnight());
        assertNotNull(dt.toDateMidnight(DateTimeZone.UTC));

        LocalTime time = new LocalTime(12, 30, 0);
        LocalDateTime ldt = dt.toLocalDateTime(time);
        assertEquals(2012, ldt.getYear());
        assertEquals(12, ldt.getHourOfDay());

        try {
            dt.toLocalDateTime(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            LocalTime otherChronoTime = new LocalTime(12, 30, 0, GJChronology.getInstanceUTC());
            dt.toLocalDateTime(otherChronoTime);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertNotNull(dt.toDateTime(time));
        assertNotNull(dt.toDateTime(time, DateTimeZone.UTC));

        try {
            LocalTime otherChronoTime = new LocalTime(12, 30, 0, GJChronology.getInstanceUTC());
            dt.toDateTime(otherChronoTime, DateTimeZone.UTC);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertNotNull(dt.toInterval());
        assertNotNull(dt.toInterval(DateTimeZone.UTC));

        Date jdkDate = dt.toDate();
        assertNotNull(jdkDate);
    }

    @Test
    public void testWithMethodsAndFieldMutations() throws Throwable {
        LocalDate dt = new LocalDate(2012, 5, 10);

        assertEquals(dt, dt.withLocalMillis(dt.getLocalMillis()));
        assertEquals(dt, dt.withFields(null));
        assertEquals(dt, dt.withFields(new LocalDate(2012, 5, 10)));

        assertEquals(new LocalDate(2015, 5, 10), dt.withField(DateTimeFieldType.year(), 2015));
        try {
            dt.withField(null, 2015);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            dt.withField(DateTimeFieldType.hourOfDay(), 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertEquals(dt, dt.withFieldAdded(DurationFieldType.years(), 0));
        assertEquals(new LocalDate(2013, 5, 10), dt.withFieldAdded(DurationFieldType.years(), 1));
        try {
            dt.withFieldAdded(null, 1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            dt.withFieldAdded(DurationFieldType.hours(), 1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertEquals(dt, dt.withPeriodAdded(null, 1));
        assertEquals(dt, dt.withPeriodAdded(Period.years(0), 1));
        assertEquals(new LocalDate(2013, 5, 11), dt.withPeriodAdded(Period.days(366), 1));

        assertEquals(dt, dt.plus((ReadablePeriod) null));
        assertEquals(new LocalDate(2013, 5, 10), dt.plus(Period.years(1)));
        assertEquals(new LocalDate(2013, 5, 10), dt.plusYears(1));
        assertEquals(dt, dt.plusYears(0));

        assertEquals(new LocalDate(2012, 6, 10), dt.plusMonths(1));
        assertEquals(dt, dt.plusMonths(0));

        assertEquals(new LocalDate(2012, 5, 17), dt.plusWeeks(1));
        assertEquals(dt, dt.plusWeeks(0));

        assertEquals(new LocalDate(2012, 5, 11), dt.plusDays(1));
        assertEquals(dt, dt.plusDays(0));

        assertEquals(dt, dt.minus((ReadablePeriod) null));
        assertEquals(new LocalDate(2011, 5, 10), dt.minus(Period.years(1)));
        assertEquals(new LocalDate(2011, 5, 10), dt.minusYears(1));
        assertEquals(dt, dt.minusYears(0));

        assertEquals(new LocalDate(2012, 4, 10), dt.minusMonths(1));
        assertEquals(dt, dt.minusMonths(0));

        assertEquals(new LocalDate(2012, 5, 3), dt.minusWeeks(1));
        assertEquals(dt, dt.minusWeeks(0));

        assertEquals(new LocalDate(2012, 5, 9), dt.minusDays(1));
        assertEquals(dt, dt.minusDays(0));
    }

    @Test
    public void testPropertiesAndGetters() throws Throwable {
        LocalDate dt = new LocalDate(2012, 5, 10);

        assertEquals(1, dt.getEra());
        assertEquals(20, dt.getCenturyOfEra());
        assertEquals(2012, dt.getYearOfEra());
        assertEquals(12, dt.getYearOfCentury());
        assertEquals(2012, dt.getYear());
        assertEquals(2012, dt.getWeekyear());
        assertEquals(5, dt.getMonthOfYear());
        assertEquals(19, dt.getWeekOfWeekyear());
        assertEquals(131, dt.getDayOfYear());
        assertEquals(10, dt.getDayOfMonth());
        assertEquals(4, dt.getDayOfWeek());

        assertEquals(dt, dt.withEra(1));
        assertEquals(dt, dt.withCenturyOfEra(20));
        assertEquals(dt, dt.withYearOfEra(2012));
        assertEquals(dt, dt.withYearOfCentury(12));
        assertEquals(dt, dt.withYear(2012));
        assertEquals(dt, dt.withWeekyear(2012));
        assertEquals(dt, dt.withMonthOfYear(5));
        assertEquals(dt, dt.withWeekOfWeekyear(19));
        assertEquals(dt, dt.withDayOfYear(131));
        assertEquals(dt, dt.withDayOfMonth(10));
        assertEquals(dt, dt.withDayOfWeek(4));

        assertNotNull(dt.property(DateTimeFieldType.year()));
        try {
            dt.property(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            dt.property(DateTimeFieldType.hourOfDay());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertNotNull(dt.era());
        assertNotNull(dt.centuryOfEra());
        assertNotNull(dt.yearOfCentury());
        assertNotNull(dt.yearOfEra());
        assertNotNull(dt.year());
        assertNotNull(dt.weekyear());
        assertNotNull(dt.monthOfYear());
        assertNotNull(dt.weekOfWeekyear());
        assertNotNull(dt.dayOfYear());
        assertNotNull(dt.dayOfMonth());
        assertNotNull(dt.dayOfWeek());
    }

    @Test
    public void testToStringMethods() throws Throwable {
        LocalDate dt = new LocalDate(2012, 5, 10);
        assertEquals("2012-05-10", dt.toString());
        assertEquals("2012/05/10", dt.toString("yyyy/MM/dd"));
        assertEquals("2012/05/10", dt.toString(null));
        assertEquals("2012/05/10", dt.toString("yyyy/MM/dd", Locale.US));
        assertEquals("2012/05/10", dt.toString(null, Locale.US));
    }

    @Test
    public void testSerialization() throws Throwable {
        LocalDate dt = new LocalDate(2012, 5, 10);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(dt);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        LocalDate dtDeser = (LocalDate) ois.readObject();
        ois.close();

        assertEquals(dt, dtDeser);
    }

    @Test
    public void testPropertyOperations() throws Throwable {
        LocalDate dt = new LocalDate(2012, 5, 10);
        LocalDate.Property prop = dt.dayOfMonth();

        assertNotNull(prop.getField());
        assertEquals(dt.getLocalMillis(), prop.getMillis());
        assertEquals(dt.getChronology(), prop.getChronology());
        assertEquals(dt, prop.getLocalDate());

        assertEquals(new LocalDate(2012, 5, 15), prop.addToCopy(5));
        assertEquals(new LocalDate(2012, 5, 1), prop.addWrapFieldToCopy(-9));
        assertEquals(new LocalDate(2012, 5, 20), prop.setCopy(20));
        assertEquals(new LocalDate(2012, 5, 10), prop.setCopy("10", Locale.US));
        assertEquals(new LocalDate(2012, 5, 10), prop.setCopy("10"));

        assertEquals(new LocalDate(2012, 5, 31), prop.withMaximumValue());
        assertEquals(new LocalDate(2012, 5, 1), prop.withMinimumValue());

        assertEquals(dt, prop.roundFloorCopy());
        assertEquals(dt, prop.roundCeilingCopy());
        assertEquals(dt, prop.roundHalfFloorCopy());
        assertEquals(dt, prop.roundHalfCeilingCopy());
        assertEquals(dt, prop.roundHalfEvenCopy());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(prop);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        LocalDate.Property propDeser = (LocalDate.Property) ois.readObject();
        ois.close();

        assertEquals(prop.getLocalDate(), propDeser.getLocalDate());
        assertEquals(prop.getField().getType(), propDeser.getField().getType());
    }

    @Test
    public void testReadResolveWithNonNullChronologyZone() throws Throwable {
        LocalDate dt = new LocalDate(2012, 5, 10, ISOChronology.getInstance(DateTimeZone.forID("Europe/Paris")));
        assertEquals(dt, dt);
    }
}