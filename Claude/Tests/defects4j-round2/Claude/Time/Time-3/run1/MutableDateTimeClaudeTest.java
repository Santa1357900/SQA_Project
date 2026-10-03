package org.joda.time;

import java.util.Locale;
import org.joda.time.chrono.ISOChronology;
import org.junit.Test;
import static org.junit.Assert.*;

public class MutableDateTimeClaudeTest {

    // constructor(fields,zone): ทุกฟิลด์และ zone ถูกตั้งค่าตรงตามที่ส่งเข้าไป
    @Test
    public void testConstructorFields_withUtcZone_setsAllFieldsCorrectly() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 6, 15, 10, 20, 30, 500, DateTimeZone.UTC);
        assertEquals(2000, mdt.getYear());
        assertEquals(6, mdt.getMonthOfYear());
        assertEquals(15, mdt.getDayOfMonth());
        assertEquals(10, mdt.getHourOfDay());
        assertEquals(20, mdt.getMinuteOfHour());
        assertEquals(30, mdt.getSecondOfMinute());
        assertEquals(500, mdt.getMillisOfSecond());
        assertEquals(DateTimeZone.UTC, mdt.getZone());
    }

    // now(zone)/now(chronology): null argument ต้องโยน NullPointerException ทั้งสองสาขา
    @Test
    public void testNow_nullArguments_throwNullPointerException() throws Throwable {
        try {
            MutableDateTime.now((DateTimeZone) null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) { }
        try {
            MutableDateTime.now((Chronology) null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) { }
    }

    // parse(String): ใช้ ISODateTimeFormat.dateTimeParser().withOffsetParsed() ตาม javadoc
    @Test
    public void testParse_isoString_parsesFieldsCorrectly() throws Throwable {
        MutableDateTime parsed = MutableDateTime.parse("2014-02-03T10:15:30.000Z");
        assertEquals(2014, parsed.getYear());
        assertEquals(2, parsed.getMonthOfYear());
        assertEquals(3, parsed.getDayOfMonth());
        assertEquals(10, parsed.getHourOfDay());
        assertEquals(15, parsed.getMinuteOfHour());
        assertEquals(30, parsed.getSecondOfMinute());
    }

    // getRoundingField/getRoundingMode: ค่าเริ่มต้นต้องเป็น null และ ROUND_NONE
    @Test
    public void testRounding_initialState_isDisabled() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        assertNull(mdt.getRoundingField());
        assertEquals(MutableDateTime.ROUND_NONE, mdt.getRoundingMode());
    }

    // setRounding(field): ต้องใช้โหมด ROUND_FLOOR เป็นค่าเริ่มต้นและปัดค่าจริงทันที
    @Test
    public void testSetRoundingSingleArg_defaultsToRoundFloorAndRounds() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        MutableDateTime mdt = new MutableDateTime(2000, 6, 15, 10, 15, 30, 0, DateTimeZone.UTC);
        mdt.setRounding(chrono.hourOfDay());
        assertEquals(MutableDateTime.ROUND_FLOOR, mdt.getRoundingMode());
        assertEquals(10, mdt.getHourOfDay());
        assertEquals(0, mdt.getMinuteOfHour());
        assertEquals(0, mdt.getSecondOfMinute());
    }

    // setRounding(field,ROUND_CEILING): ปัดขึ้นเมื่อมีเศษวินาทีเหลือ
    @Test
    public void testSetRoundingFieldAndMode_roundCeilingRoundsUp() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        MutableDateTime mdt = new MutableDateTime(2000, 6, 15, 10, 15, 30, 0, DateTimeZone.UTC);
        mdt.setRounding(chrono.minuteOfDay(), MutableDateTime.ROUND_CEILING);
        assertEquals(MutableDateTime.ROUND_CEILING, mdt.getRoundingMode());
        assertEquals(16, mdt.getMinuteOfHour());
        assertEquals(0, mdt.getSecondOfMinute());
    }

    // setRounding(null, mode!=NONE): field null ต้องปิด rounding ไม่ว่า mode จะเป็นอะไร
    @Test
    public void testSetRoundingNullField_disablesRoundingRegardlessOfMode() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        mdt.setRounding(chrono.hourOfDay(), MutableDateTime.ROUND_FLOOR);
        mdt.setRounding(null, MutableDateTime.ROUND_HALF_EVEN);
        assertNull(mdt.getRoundingField());
        assertEquals(MutableDateTime.ROUND_NONE, mdt.getRoundingMode());
    }

    // setRounding(field!=null, mode นอกช่วง): ต้องโยน IllegalArgumentException
    @Test
    public void testSetRoundingInvalidModeWithField_throwsIllegalArgumentException() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        try {
            mdt.setRounding(chrono.hourOfDay(), 99);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // setRounding(null, mode นอกช่วง): javadoc ระบุ "no exception if field is null"
    @Test
    public void testSetRoundingNullFieldInvalidMode_doesNotThrowAndDisables() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        mdt.setRounding(null, 99);
        assertNull(mdt.getRoundingField());
        assertEquals(MutableDateTime.ROUND_NONE, mdt.getRoundingMode());
    }

    // setMillis ผ่าน setRounding(ROUND_HALF_EVEN): เลือกค่าที่ทำให้ฟิลด์เป็นเลขคู่เมื่ออยู่กึ่งกลาง
    @Test
    public void testSetMillis_roundHalfEven_roundsToEvenNeighbor() throws Throwable {
        Chronology chrono = ISOChronology.getInstanceUTC();
        MutableDateTime mdt = new MutableDateTime(2000, 6, 15, 11, 30, 0, 0, DateTimeZone.UTC);
        mdt.setRounding(chrono.hourOfDay(), MutableDateTime.ROUND_HALF_EVEN);
        assertEquals(12, mdt.getHourOfDay());
        assertEquals(0, mdt.getMinuteOfHour());
    }

    // setMillis(ReadableInstant): คัดลอกค่ามิลลิวินาทีของ instant เข้ามาตรงๆ
    @Test
    public void testSetMillisReadableInstant_copiesInstantMillis() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        DateTime other = new DateTime(123456789L, DateTimeZone.UTC);
        mdt.setMillis(other);
        assertEquals(123456789L, mdt.getMillis());
    }

    // add(long): บวกมิลลิวินาทีตรงๆ
    @Test
    public void testAddLong_addsToMillis() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(1000L, DateTimeZone.UTC);
        mdt.add(500L);
        assertEquals(1500L, mdt.getMillis());
    }

    // add(long) overflow: ต้องโยน ArithmeticException ผ่าน FieldUtils.safeAdd
    @Test
    public void testAddLongOverflow_throwsArithmeticException() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(Long.MAX_VALUE, DateTimeZone.UTC);
        try {
            mdt.add(1L);
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) { }
    }

    // add(ReadableDuration=null): javadoc "null means add zero" ต้องไม่เปลี่ยนค่า
    @Test
    public void testAddReadableDurationNull_doesNotChangeMillis() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(1000L, DateTimeZone.UTC);
        mdt.add((ReadableDuration) null);
        assertEquals(1000L, mdt.getMillis());
    }

    // add(duration,scalar) scalar ติดลบ: ทิศทางและขนาดต้องคูณตาม scalar
    @Test
    public void testAddReadableDurationWithNegativeScalar_subtractsMillis() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        Duration dur = new Duration(1000L);
        mdt.add(dur, -3);
        assertEquals(-3000L, mdt.getMillis());
    }

    // add(ReadablePeriod=null): ไม่เปลี่ยนค่า
    @Test
    public void testAddReadablePeriodNull_doesNotChangeMillis() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(5000L, DateTimeZone.UTC);
        mdt.add((ReadablePeriod) null);
        assertEquals(5000L, mdt.getMillis());
    }

    // add(period,scalar): เพิ่มหลายครั้งตาม scalar ผ่าน chronology.add
    @Test
    public void testAddReadablePeriodWithScalar_addsMultipleTimes() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        Period period = Period.hours(1);
        mdt.add(period, 3);
        assertEquals(3, mdt.getHourOfDay());
    }

    // setChronology: เปลี่ยน chronology แต่ millis คงเดิม
    @Test
    public void testSetChronology_changesChronologyKeepsMillis() throws Throwable {
        Chronology other = ISOChronology.getInstance(DateTimeZone.forOffsetHours(4));
        MutableDateTime mdt = new MutableDateTime(123456789L, ISOChronology.getInstanceUTC());
        mdt.setChronology(other);
        assertEquals(other, mdt.getChronology());
        assertEquals(123456789L, mdt.getMillis());
    }

    // setZone(ต่าง zone): คง millis ไว้ ปรับฟิลด์ตาม zone ใหม่
    @Test
    public void testSetZoneDifferentZone_keepsMillisChangesFields() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        mdt.setZone(DateTimeZone.forOffsetHours(3));
        assertEquals(0L, mdt.getMillis());
        assertEquals(3, mdt.getHourOfDay());
        assertEquals(DateTimeZone.forOffsetHours(3), mdt.getZone());
    }

    // setZone(zone เดิม): "If the chronology already has this time zone, no change occurs"
    @Test
    public void testSetZoneSameZone_isNoOp() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        Chronology before = mdt.getChronology();
        mdt.setZone(DateTimeZone.UTC);
        assertSame(before, mdt.getChronology());
    }

    // setZoneRetainFields(ต่าง zone): คงค่าฟิลด์ท้องถิ่น ปรับ millis ให้สอดคล้อง
    @Test
    public void testSetZoneRetainFieldsDifferentZone_keepsFieldsChangesMillis() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 10, 0, 0, 0, DateTimeZone.UTC);
        long before = mdt.getMillis();
        mdt.setZoneRetainFields(DateTimeZone.forOffsetHours(5));
        assertEquals(10, mdt.getHourOfDay());
        assertEquals(DateTimeZone.forOffsetHours(5), mdt.getZone());
        assertTrue(mdt.getMillis() != before);
    }

    // setZoneRetainFields(zone เดิม): ต้อง return ทันทีโดยไม่เปลี่ยน chronology
    @Test
    public void testSetZoneRetainFieldsSameZone_isNoOp() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        Chronology before = mdt.getChronology();
        mdt.setZoneRetainFields(DateTimeZone.UTC);
        assertSame(before, mdt.getChronology());
    }

    // set(DateTimeFieldType,value): ตั้งค่าฟิลด์ที่ระบุ
    @Test
    public void testSetFieldType_setsFieldValue() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        mdt.set(DateTimeFieldType.dayOfMonth(), 15);
        assertEquals(15, mdt.getDayOfMonth());
    }

    // set(null,value): ต้องโยน IllegalArgumentException
    @Test
    public void testSetFieldTypeNull_throwsIllegalArgumentException() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        try {
            mdt.set(null, 5);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // add(DurationFieldType,amount): เพิ่มค่าตามหน่วยที่ระบุ
    @Test
    public void testAddDurationFieldType_addsAmount() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        mdt.add(DurationFieldType.months(), 2);
        assertEquals(3, mdt.getMonthOfYear());
    }

    // add(null,amount): ต้องโยน IllegalArgumentException
    @Test
    public void testAddDurationFieldTypeNull_throwsIllegalArgumentException() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        try {
            mdt.add((DurationFieldType) null, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // setYear/addYears: ตั้งค่าและบวกปีถูกต้อง
    @Test
    public void testSetYearAndAddYears_updateYear() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        mdt.setYear(1999);
        assertEquals(1999, mdt.getYear());
        mdt.addYears(5);
        assertEquals(2004, mdt.getYear());
    }

    // setMonthOfYear/addMonths: ครอบคลุมการ carry ข้ามปี
    @Test
    public void testSetMonthOfYearAndAddMonths_updateMonth() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        mdt.setMonthOfYear(6);
        assertEquals(6, mdt.getMonthOfYear());
        mdt.addMonths(8);
        assertEquals(2, mdt.getMonthOfYear());
        assertEquals(2001, mdt.getYear());
    }

    // setDayOfMonth/addDays: ครอบคลุมการ carry ข้ามเดือน
    @Test
    public void testSetDayOfMonthAndAddDays_updateDay() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        mdt.setDayOfMonth(15);
        assertEquals(15, mdt.getDayOfMonth());
        mdt.addDays(20);
        assertEquals(4, mdt.getDayOfMonth());
        assertEquals(2, mdt.getMonthOfYear());
    }

    // setHourOfDay/addHours: ครอบคลุมการ carry ข้ามวัน
    @Test
    public void testSetHourOfDayAndAddHours_updateHour() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        mdt.setHourOfDay(5);
        assertEquals(5, mdt.getHourOfDay());
        mdt.addHours(20);
        assertEquals(1, mdt.getHourOfDay());
        assertEquals(2, mdt.getDayOfMonth());
    }

    // setMinuteOfHour/addMinutes: ครอบคลุมการ carry ข้ามชั่วโมง
    @Test
    public void testSetMinuteOfHourAndAddMinutes_updateMinute() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        mdt.setMinuteOfHour(30);
        assertEquals(30, mdt.getMinuteOfHour());
        mdt.addMinutes(45);
        assertEquals(15, mdt.getMinuteOfHour());
        assertEquals(1, mdt.getHourOfDay());
    }

    // setSecondOfMinute/addSeconds: ครอบคลุมการ carry ข้ามนาที
    @Test
    public void testSetSecondOfMinuteAndAddSeconds_updateSecond() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        mdt.setSecondOfMinute(30);
        assertEquals(30, mdt.getSecondOfMinute());
        mdt.addSeconds(40);
        assertEquals(10, mdt.getSecondOfMinute());
        assertEquals(1, mdt.getMinuteOfHour());
    }

    // setMillisOfSecond/addMillis: ครอบคลุมการ carry ข้ามวินาที
    @Test
    public void testSetMillisOfSecondAndAddMillis_updateMillisOfSecond() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        mdt.setMillisOfSecond(500);
        assertEquals(500, mdt.getMillisOfSecond());
        mdt.addMillis(700);
        assertEquals(200, mdt.getMillisOfSecond());
        assertEquals(1, mdt.getSecondOfMinute());
    }

    // setDate(long): "The time part of this object will be unaffected"
    @Test
    public void testSetDateLong_keepsTimeChangesDate() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 10, 20, 30, 400, DateTimeZone.UTC);
        long newDateMillis = new MutableDateTime(2010, 5, 5, 0, 0, 0, 0, DateTimeZone.UTC).getMillis();
        mdt.setDate(newDateMillis);
        assertEquals(2010, mdt.getYear());
        assertEquals(5, mdt.getMonthOfYear());
        assertEquals(5, mdt.getDayOfMonth());
        assertEquals(10, mdt.getHourOfDay());
        assertEquals(20, mdt.getMinuteOfHour());
    }

    // setDate(year,month,day): เวลาต้องคงเดิม ตามสัญญาเดียวกับ setDate(long)
    @Test
    public void testSetDateFields_keepsTimeChangesDate() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 15, 45, 0, 0, DateTimeZone.UTC);
        mdt.setDate(2022, 3, 10);
        assertEquals(2022, mdt.getYear());
        assertEquals(3, mdt.getMonthOfYear());
        assertEquals(10, mdt.getDayOfMonth());
        assertEquals(15, mdt.getHourOfDay());
        assertEquals(45, mdt.getMinuteOfHour());
    }

    // setTime(long): "The date part of this object will be unaffected"
    @Test
    public void testSetTimeLong_keepsDateChangesTime() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 6, 15, 0, 0, 0, 0, DateTimeZone.UTC);
        long timeMillis = new MutableDateTime(1970, 1, 1, 8, 30, 0, 0, DateTimeZone.UTC).getMillis();
        mdt.setTime(timeMillis);
        assertEquals(2000, mdt.getYear());
        assertEquals(6, mdt.getMonthOfYear());
        assertEquals(15, mdt.getDayOfMonth());
        assertEquals(8, mdt.getHourOfDay());
        assertEquals(30, mdt.getMinuteOfHour());
    }

    // setTime(h,m,s,ms): ตั้งเวลาทั้งหมด วันที่คงเดิม
    @Test
    public void testSetTimeFields_setsTimeKeepsDate() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 6, 15, 0, 0, 0, 0, DateTimeZone.UTC);
        mdt.setTime(14, 22, 33, 444);
        assertEquals(2000, mdt.getYear());
        assertEquals(6, mdt.getMonthOfYear());
        assertEquals(14, mdt.getHourOfDay());
        assertEquals(22, mdt.getMinuteOfHour());
        assertEquals(33, mdt.getSecondOfMinute());
        assertEquals(444, mdt.getMillisOfSecond());
    }

    // setDateTime(ทั้งหมด): ทุกฟิลด์ถูกตั้งค่าตามที่ให้
    @Test
    public void testSetDateTimeFields_setsAllFields() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        mdt.setDateTime(2015, 8, 20, 9, 10, 11, 12);
        assertEquals(2015, mdt.getYear());
        assertEquals(8, mdt.getMonthOfYear());
        assertEquals(20, mdt.getDayOfMonth());
        assertEquals(9, mdt.getHourOfDay());
        assertEquals(10, mdt.getMinuteOfHour());
        assertEquals(11, mdt.getSecondOfMinute());
        assertEquals(12, mdt.getMillisOfSecond());
    }

    // property(null): ต้องโยน IllegalArgumentException
    @Test
    public void testPropertyNullType_throwsIllegalArgumentException() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        try {
            mdt.property(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // property(valid type): ต้องผูกกับ instant เดิมและมี field ไม่เป็น null
    @Test
    public void testPropertyValidType_returnsBoundProperty() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(0L, DateTimeZone.UTC);
        MutableDateTime.Property prop = mdt.property(DateTimeFieldType.dayOfMonth());
        assertSame(mdt, prop.getMutableDateTime());
        assertNotNull(prop.getField());
    }

    // copy()/clone(): ต้องได้ instance อิสระที่มีค่าเริ่มต้นเท่ากัน
    @Test
    public void testCopyAndClone_produceIndependentEqualInstances() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(123456L, DateTimeZone.UTC);
        MutableDateTime copy = mdt.copy();
        MutableDateTime cloned = (MutableDateTime) mdt.clone();
        assertEquals(mdt.getMillis(), copy.getMillis());
        assertEquals(mdt.getMillis(), cloned.getMillis());
        copy.setMillis(0L);
        assertTrue(mdt.getMillis() != copy.getMillis());
    }

    // toString(): รูปแบบ ISO8601 yyyy-MM-ddTHH:mm:ss.SSS ตาม javadoc
    @Test
    public void testToString_startsWithIsoDateTimePrefix() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 2, 3, 4, 5, 6, DateTimeZone.UTC);
        String s = mdt.toString();
        assertTrue(s.startsWith("2000-01-02T03:04:05.006"));
    }

    // Property.set(int)/add(int)/set(String,Locale): ต้องแก้ instant ที่ผูกไว้จริง
    @Test
    public void testPropertyAddAndSet_modifyUnderlyingInstant() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 5, 0, 0, 0, DateTimeZone.UTC);
        mdt.hourOfDay().set(10);
        assertEquals(10, mdt.getHourOfDay());
        mdt.hourOfDay().add(3);
        assertEquals(13, mdt.getHourOfDay());
        mdt.monthOfYear().set("December", Locale.ENGLISH);
        assertEquals(12, mdt.getMonthOfYear());
    }

    // Property.roundFloor(): ปัดลงฟิลด์ที่เลือก ฟิลด์ย่อยต้องเป็นศูนย์
    @Test
    public void testPropertyRoundFloor_roundsFieldDown() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 10, 30, 15, 0, DateTimeZone.UTC);
        mdt.hourOfDay().roundFloor();
        assertEquals(10, mdt.getHourOfDay());
        assertEquals(0, mdt.getMinuteOfHour());
        assertEquals(0, mdt.getSecondOfMinute());
    }

    // Property.addWrapField(int): wrap เฉพาะภายในฟิลด์ ไม่ carry ไปฟิลด์ใหญ่กว่า
    @Test
    public void testPropertyAddWrapField_wrapsWithinLargerField() throws Throwable {
        MutableDateTime mdt = new MutableDateTime(2000, 1, 1, 23, 0, 0, 0, DateTimeZone.UTC);
        mdt.hourOfDay().addWrapField(2);
        assertEquals(1, mdt.getHourOfDay());
        assertEquals(1, mdt.getDayOfMonth());
    }
}
