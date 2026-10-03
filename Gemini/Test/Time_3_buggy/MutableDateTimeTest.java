package org.joda.time;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;

import static org.junit.Assert.*;

public class MutableDateTimeTest {

    private DateTimeZone originalDateTimeZone;
    private Locale originalLocale;

    @Before
    public void setUp() throws Throwable {
        originalDateTimeZone = DateTimeZone.getDefault();
        originalLocale = Locale.getDefault();
        DateTimeZone.setDefault(DateTimeZone.UTC);
        Locale.setDefault(Locale.ENGLISH);
    }

    @After
    public void tearDown() throws Throwable {
        DateTimeZone.setDefault(originalDateTimeZone);
        Locale.setDefault(originalLocale);
    }

    @Test
    public void testConstructors() throws Throwable {
        MutableDateTime mdt1 = new MutableDateTime();
        assertNotNull(mdt1);

        MutableDateTime mdt2 = new MutableDateTime(DateTimeZone.UTC);
        assertNotNull(mdt2);

        MutableDateTime mdt3 = new MutableDateTime((DateTimeZone) null);
        assertNotNull(mdt3);

        MutableDateTime mdt4 = new MutableDateTime(ISOChronology.getInstanceUTC());
        assertNotNull(mdt4);

        MutableDateTime mdt5 = new MutableDateTime((Chronology) null);
        assertNotNull(mdt5);

        MutableDateTime mdt6 = new MutableDateTime(1000L);
        assertEquals(1000L, mdt6.getMillis());

        MutableDateTime mdt7 = new MutableDateTime(1000L, DateTimeZone.UTC);
        assertEquals(1000L, mdt7.getMillis());

        MutableDateTime mdt8 = new MutableDateTime(1000L, (DateTimeZone) null);
        assertEquals(1000L, mdt8.getMillis());

        MutableDateTime mdt9 = new MutableDateTime(1000L, ISOChronology.getInstanceUTC());
        assertEquals(1000L, mdt9.getMillis());

        MutableDateTime mdt10 = new MutableDateTime(1000L, (Chronology) null);
        assertEquals(1000L, mdt10.getMillis());

        MutableDateTime mdt11 = new MutableDateTime(new Long(2000L));
        assertEquals(2000L, mdt11.getMillis());

        MutableDateTime mdt12 = new MutableDateTime(new Long(2000L), DateTimeZone.UTC);
        assertEquals(2000L, mdt12.getMillis());

        MutableDateTime mdt13 = new MutableDateTime(new Long(2000L), (DateTimeZone) null);
        assertEquals(2000L, mdt13.getMillis());

        MutableDateTime mdt14 = new MutableDateTime(new Long(2000L), ISOChronology.getInstanceUTC());
        assertEquals(2000L, mdt14.getMillis());

        MutableDateTime mdt15 = new MutableDateTime(new Long(2000L), (Chronology) null);
        assertEquals(2000L, mdt15.getMillis());

        MutableDateTime mdt16 = new MutableDateTime(2012, 6, 9, 12, 30, 40, 500);
        assertEquals(2012, mdt16.getYear());

        MutableDateTime mdt17 = new MutableDateTime(2012, 6, 9, 12, 30, 40, 500, DateTimeZone.UTC);
        assertEquals(2012, mdt17.getYear());

        MutableDateTime mdt18 = new MutableDateTime(2012, 6, 9, 12, 30, 40, 500, (DateTimeZone) null);
        assertEquals(2012, mdt18.getYear());

        MutableDateTime mdt19 = new MutableDateTime(2012, 6, 9, 12, 30, 40, 500, ISOChronology.getInstanceUTC());
        assertEquals(2012, mdt19.getYear());

        MutableDateTime mdt20 = new MutableDateTime(2012, 6, 9, 12, 30, 40, 500, (Chronology) null);
        assertEquals(2012, mdt20.getYear());
    }

    @Test
    public void testNowFactories() throws Throwable {
        MutableDateTime mdt1 = MutableDateTime.now();
        assertNotNull(mdt1);

        MutableDateTime mdt2 = MutableDateTime.now(DateTimeZone.UTC);
        assertNotNull(mdt2);

        boolean caught = false;
        try {
            MutableDateTime.now((DateTimeZone) null);
        } catch (NullPointerException e) {
            caught = true;
        }
        assertTrue(caught);

        MutableDateTime mdt3 = MutableDateTime.now(ISOChronology.getInstanceUTC());
        assertNotNull(mdt3);

        caught = false;
        try {
            MutableDateTime.now((Chronology) null);
        } catch (NullPointerException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testParse() throws Throwable {
        MutableDateTime mdt1 = MutableDateTime.parse("2012-06-09T12:30:40.000Z");
        assertNotNull(mdt1);
        assertEquals(2012, mdt1.getYear());

        MutableDateTime mdt2 = MutableDateTime.parse("2012-06-09T12:30:40.000Z", org.joda.time.format.ISODateTimeFormat.dateTimeParser());
        assertNotNull(mdt2);
        assertEquals(2012, mdt2.getYear());
    }

    @Test
    public void testRounding() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(1339245040123L); // 2012-06-09T12:30:40.123Z
        assertNull(mdt.getRoundingField());
        assertEquals(MutableDateTime.ROUND_NONE, mdt.getRoundingMode());

        mdt.setRounding(null, MutableDateTime.ROUND_NONE);
        assertNull(mdt.getRoundingField());
        assertEquals(MutableDateTime.ROUND_NONE, mdt.getRoundingMode());

        mdt.setRounding(mdt.hourOfDay().getField(), MutableDateTime.ROUND_FLOOR);
        assertNotNull(mdt.getRoundingField());
        assertEquals(MutableDateTime.ROUND_FLOOR, mdt.getRoundingMode());

        mdt.setRounding(mdt.hourOfDay().getField(), MutableDateTime.ROUND_CEILING);
        assertEquals(MutableDateTime.ROUND_CEILING, mdt.getRoundingMode());

        mdt.setRounding(mdt.hourOfDay().getField(), MutableDateTime.ROUND_HALF_FLOOR);
        assertEquals(MutableDateTime.ROUND_HALF_FLOOR, mdt.getRoundingMode());

        mdt.setRounding(mdt.hourOfDay().getField(), MutableDateTime.ROUND_HALF_CEILING);
        assertEquals(MutableDateTime.ROUND_HALF_CEILING, mdt.getRoundingMode());

        mdt.setRounding(mdt.hourOfDay().getField(), MutableDateTime.ROUND_HALF_EVEN);
        assertEquals(MutableDateTime.ROUND_HALF_EVEN, mdt.getRoundingMode());

        mdt.setRounding(null);
        assertNull(mdt.getRoundingField());
        assertEquals(MutableDateTime.ROUND_NONE, mdt.getRoundingMode());

        boolean caught = false;
        try {
            mdt.setRounding(mdt.hourOfDay().getField(), 99);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testSetMillis() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(1000L);
        mdt.setMillis(2000L);
        assertEquals(2000L, mdt.getMillis());

        mdt.setMillis(new DateTime(3000L));
        assertEquals(3000L, mdt.getMillis());

        mdt.setMillis((ReadableInstant) null);
        assertNotNull(mdt);
    }

    @Test
    public void testAddOperations() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(1000L);
        mdt.add(500L);
        assertEquals(1500L, mdt.getMillis());

        mdt.add((ReadableDuration) null);
        assertEquals(1500L, mdt.getMillis());

        mdt.add(new Duration(500L));
        assertEquals(2000L, mdt.getMillis());

        mdt.add(new Duration(500L), 2);
        assertEquals(3000L, mdt.getMillis());

        mdt.add((ReadableDuration) null, 2);
        assertEquals(3000L, mdt.getMillis());

        mdt.add((ReadablePeriod) null);
        assertEquals(3000L, mdt.getMillis());

        mdt.add(Period.seconds(1));
        assertEquals(4000L, mdt.getMillis());

        mdt.add(Period.seconds(1), 2);
        assertEquals(6000L, mdt.getMillis());

        mdt.add((ReadablePeriod) null, 2);
        assertEquals(6000L, mdt.getMillis());
    }

    @Test
    public void testChronologyAndZoneOperations() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(1000L);
        mdt.setChronology(ISOChronology.getInstance(DateTimeZone.UTC));
        assertEquals(ISOChronology.getInstance(DateTimeZone.UTC), mdt.getChronology());

        mdt.setZone(DateTimeZone.forID("+01:00"));
        assertEquals(DateTimeZone.forID("+01:00"), mdt.getZone());

        mdt.setZone(null);
        assertEquals(DateTimeZone.getDefault(), mdt.getZone());

        mdt.setZoneRetainFields(DateTimeZone.UTC);
        assertEquals(DateTimeZone.UTC, mdt.getZone());

        mdt.setZoneRetainFields(DateTimeZone.UTC); // same zone branch
        assertEquals(DateTimeZone.UTC, mdt.getZone());

        mdt.setZoneRetainFields(null);
        assertNotNull(mdt.getZone());
    }

    @Test
    public void testFieldSpecificSetAndAdd() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2012, 6, 9, 12, 30, 40, 500);

        mdt.set(DateTimeFieldType.year(), 2015);
        assertEquals(2015, mdt.getYear());

        boolean caught = false;
        try {
            mdt.set(null, 2015);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);

        mdt.add(DurationFieldType.years(), 1);
        assertEquals(2016, mdt.getYear());

        caught = false;
        try {
            mdt.add((DurationFieldType) null, 1);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);

        mdt.setYear(2010);
        assertEquals(2010, mdt.getYear());
        mdt.addYears(2);
        assertEquals(2012, mdt.getYear());

        mdt.setWeekyear(2012);
        mdt.addWeekyears(1);

        mdt.setMonthOfYear(5);
        assertEquals(5, mdt.getMonthOfYear());
        mdt.addMonths(1);
        assertEquals(6, mdt.getMonthOfYear());

        mdt.setWeekOfWeekyear(10);
        mdt.addWeeks(1);

        mdt.setDayOfYear(100);
        mdt.setDayOfMonth(15);
        assertEquals(15, mdt.getDayOfMonth());
        mdt.addDays(1);
        assertEquals(16, mdt.getDayOfMonth());

        mdt.setDayOfWeek(3);

        mdt.setHourOfDay(10);
        assertEquals(10, mdt.getHourOfDay());
        mdt.addHours(1);
        assertEquals(11, mdt.getHourOfDay());

        mdt.setMinuteOfDay(120);
        mdt.setMinuteOfHour(25);
        assertEquals(25, mdt.getMinuteOfHour());
        mdt.addMinutes(1);
        assertEquals(26, mdt.getMinuteOfHour());

        mdt.setSecondOfDay(5000);
        mdt.setSecondOfMinute(30);
        assertEquals(30, mdt.getSecondOfMinute());
        mdt.addSeconds(1);
        assertEquals(31, mdt.getSecondOfMinute());

        mdt.setMillisOfDay(50000);
        mdt.setMillisOfSecond(250);
        assertEquals(250, mdt.getMillisOfSecond());
        mdt.addMillis(10);
        assertEquals(260, mdt.getMillisOfSecond());
    }

    @Test
    public void testSetDateAndTimeOverloads() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2012, 6, 9, 12, 30, 40, 500);

        mdt.setDate(1000L);
        mdt.setDate(new DateTime(2000L));
        mdt.setDate(new MutableDateTime(3000L));
        mdt.setDate(2011, 5, 8);
        assertEquals(2011, mdt.getYear());

        mdt.setTime(5000L);
        mdt.setTime(new DateTime(6000L));
        mdt.setTime(10, 20, 30, 400);
        assertEquals(10, mdt.getHourOfDay());

        mdt.setDateTime(2013, 1, 2, 3, 4, 5, 6);
        assertEquals(2013, mdt.getYear());
    }

    @Test
    public void testPropertiesMethods() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2012, 6, 9, 12, 30, 40, 500);

        assertNotNull(mdt.property(DateTimeFieldType.year()));
        boolean caught = false;
        try {
            mdt.property(null);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);

        assertNotNull(mdt.era());
        assertNotNull(mdt.centuryOfEra());
        assertNotNull(mdt.yearOfCentury());
        assertNotNull(mdt.yearOfEra());
        assertNotNull(mdt.year());
        assertNotNull(mdt.weekyear());
        assertNotNull(mdt.monthOfYear());
        assertNotNull(mdt.weekOfWeekyear());
        assertNotNull(mdt.dayOfYear());
        assertNotNull(mdt.dayOfMonth());
        assertNotNull(mdt.dayOfWeek());
        assertNotNull(mdt.hourOfDay());
        assertNotNull(mdt.minuteOfDay());
        assertNotNull(mdt.minuteOfHour());
        assertNotNull(mdt.secondOfDay());
        assertNotNull(mdt.secondOfMinute());
        assertNotNull(mdt.millisOfDay());
        assertNotNull(mdt.millisOfSecond());
    }

    @Test
    public void testCopyAndCloneAndToString() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2012, 6, 9, 12, 30, 40, 500);
        MutableDateTime copy = mdt.copy();
        assertEquals(mdt, copy);

        Object clone = mdt.clone();
        assertEquals(mdt, clone);

        String str = mdt.toString();
        assertNotNull(str);
        assertTrue(str.contains("2012"));
    }

    @Test
    public void testPropertyClassOperations() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2012, 6, 9, 12, 30, 40, 500);
        MutableDateTime.Property prop = mdt.year();

        assertNotNull(prop.getField());
        assertEquals(mdt.getMillis(), prop.getMillis());
        assertEquals(mdt.getChronology(), prop.getChronology());
        assertEquals(mdt, prop.getMutableDateTime());

        prop.add(1);
        assertEquals(2013, mdt.getYear());

        prop.add(1L);
        assertEquals(2014, mdt.getYear());

        prop.addWrapField(1);
        assertEquals(2015, mdt.getYear());

        prop.set(2020);
        assertEquals(2020, mdt.getYear());

        prop.set("2021", Locale.ENGLISH);
        assertEquals(2021, mdt.getYear());

        prop.set("2022");
        assertEquals(2022, mdt.getYear());

        prop.roundFloor();
        prop.roundCeiling();
        prop.roundHalfFloor();
        prop.roundHalfCeiling();
        prop.roundHalfEven();

        // Serialization of Property
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(prop);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        MutableDateTime.Property deserializedProp = (MutableDateTime.Property) ois.readObject();
        assertNotNull(deserializedProp);
        assertEquals(prop.getYear(), deserializedProp.getYear());
    }
}