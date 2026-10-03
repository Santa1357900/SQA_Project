package org.apache.commons.lang3.time;

import static org.junit.Assert.*;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.FieldPosition;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;

public class FastDatePrinterClaudeTest {

    // Constructor: pattern == null -> NullPointerException เมื่อ parsePattern อ่าน mPattern.length()
    @Test
    public void testConstructor_nullPattern_throwsNPE() throws Throwable {
        try {
            new FastDatePrinter(null, TimeZone.getTimeZone("GMT"), Locale.US);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // Constructor: locale == null -> NullPointerException จาก DateFormatSymbols(Locale)
    @Test
    public void testConstructor_nullLocale_throwsNPE() throws Throwable {
        try {
            new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // Constructor: timeZone == null กับ pattern "z" -> NPE จาก tz.getDisplayName
    @Test
    public void testConstructor_nullTimeZoneWithZPattern_throwsNPE() throws Throwable {
        try {
            new FastDatePrinter("z", null, Locale.US);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // parsePattern: ตัวอักษร pattern ที่ไม่รู้จัก -> IllegalArgumentException (default case)
    @Test
    public void testConstructor_illegalPatternLetter_throwsIllegalArgumentException() throws Throwable {
        try {
            new FastDatePrinter("X", TimeZone.getTimeZone("GMT"), Locale.US);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getPattern คืนค่าตรงกับที่สร้าง
    @Test
    public void testGetPattern_returnsConstructorValue() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy-MM-dd", TimeZone.getTimeZone("GMT"), Locale.US);
        assertEquals("yyyy-MM-dd", p.getPattern());
    }

    // getTimeZone คืนค่าตรงกับที่สร้าง
    @Test
    public void testGetTimeZone_returnsConstructorValue() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("GMT+02:00");
        FastDatePrinter p = new FastDatePrinter("yyyy", tz, Locale.US);
        assertEquals(tz, p.getTimeZone());
    }

    // getLocale คืนค่าตรงกับที่สร้าง
    @Test
    public void testGetLocale_returnsConstructorValue() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        assertEquals(Locale.US, p.getLocale());
    }

    // ค่าคงที่ style ต้องตรงกับ DateFormat
    @Test
    public void testStaticConstants_matchDateFormatConstants() throws Throwable {
        assertEquals(DateFormat.FULL, FastDatePrinter.FULL);
        assertEquals(DateFormat.LONG, FastDatePrinter.LONG);
        assertEquals(DateFormat.MEDIUM, FastDatePrinter.MEDIUM);
        assertEquals(DateFormat.SHORT, FastDatePrinter.SHORT);
    }

    // parseToken: ลำดับตัวอักษรซ้ำกันทั้งหมดต้องถูกคืนเป็น token เดียว พร้อม index ล่าสุด
    @Test
    public void testParseToken_letterRun_returnsFullRunAndIndex() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        int[] indexRef = new int[] {0};
        String token = p.parseToken("yyyy-MM", indexRef);
        assertEquals("yyyy", token);
        assertEquals(3, indexRef[0]);
    }

    // parsePattern: pattern ตัวอักษรเดียวต้องให้ rule เดียว
    @Test
    public void testParsePattern_singleToken_returnsOneRule() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        List rules = p.parsePattern();
        assertEquals(1, rules.size());
    }

    // parsePattern: pattern หลาย token ต้องนับจำนวน rule ถูกต้อง
    @Test
    public void testParsePattern_multiToken_returnsCorrectRuleCount() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy-MM-dd", TimeZone.getTimeZone("GMT"), Locale.US);
        List rules = p.parsePattern();
        assertEquals(5, rules.size());
    }

    // literal พร้อม '' ซ้อนต้องกลายเป็น apostrophe เดี่ยวในผลลัพธ์ (StringLiteral + escaped quote)
    @Test
    public void testFormat_literalWithEscapedQuote_producesApostrophe() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("'it''s'", TimeZone.getTimeZone("GMT"), Locale.US);
        assertEquals("it's", p.format(new Date(0L)));
    }

    // literal ตัวอักษรเดียว (CharacterLiteral) รวมกับ TwoDigitNumberField
    @Test
    public void testFormat_singleCharLiteralAndTwoDigitHour() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("'T'HH", TimeZone.getTimeZone("GMT"), Locale.US);
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.MARCH, 5, 5, 0, 0);
        assertEquals("T05", p.format(cal.getTime()));
    }

    // y เดี่ยว (tokenLen<4) -> padding 4 หลัก, value<100 branch ของ PaddedNumberField
    @Test
    public void testFormat_yearPattern_singleY_padsToFourDigits() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("y", TimeZone.getTimeZone("GMT"), Locale.US);
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(Calendar.YEAR, 45);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        assertEquals("0045", p.format(cal.getTime()));
    }

    // yyy -> padding ยังคงบังคับเป็น 4 หลัก, value 100..999 branch (digits=3)
    @Test
    public void testFormat_yearPattern_threeY_hitsDigitsThreeBranch() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyy", TimeZone.getTimeZone("GMT"), Locale.US);
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(Calendar.YEAR, 500);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        assertEquals("0500", p.format(cal.getTime()));
    }

    // yyyy -> ปี >= 1000 ไม่ต้องเติมศูนย์เพิ่ม (digits == mSize)
    @Test
    public void testFormat_yearPattern_fourY_normalFourDigitYear() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.JANUARY, 1);
        assertEquals("2014", p.format(cal.getTime()));
    }

    // yy -> ปีถูกตัดเหลือ 2 หลักท้าย (TwoDigitYearField)
    @Test
    public void testFormat_yearPattern_twoDigit_truncatesToLastTwoDigits() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yy", TimeZone.getTimeZone("GMT"), Locale.US);
        GregorianCalendar cal2000 = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal2000.clear();
        cal2000.set(2000, Calendar.JANUARY, 1);
        assertEquals("00", p.format(cal2000.getTime()));

        GregorianCalendar cal2014 = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal2014.clear();
        cal2014.set(2014, Calendar.JANUARY, 1);
        assertEquals("14", p.format(cal2014.getTime()));
    }

    // M/MM ตัวเลขเดือน: ต่ำกว่า 10 (unpadded) และสองหลัก
    @Test
    public void testFormat_monthPattern_numericUnpaddedAndTwoDigit() throws Throwable {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.MARCH, 5);
        FastDatePrinter pM = new FastDatePrinter("M", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDatePrinter pMM = new FastDatePrinter("MM", TimeZone.getTimeZone("GMT"), Locale.US);
        assertEquals("3", pM.format(cal.getTime()));
        assertEquals("03", pMM.format(cal.getTime()));
    }

    // MMM/MMMM ชื่อเดือนแบบย่อ/เต็ม ใช้ DateFormatSymbols เป็น oracle
    @Test
    public void testFormat_monthPattern_textShortAndFull_matchesSymbols() throws Throwable {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.MARCH, 5);
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        String expectedShort = symbols.getShortMonths()[cal.get(Calendar.MONTH)];
        String expectedFull = symbols.getMonths()[cal.get(Calendar.MONTH)];

        FastDatePrinter pShort = new FastDatePrinter("MMM", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDatePrinter pFull = new FastDatePrinter("MMMM", TimeZone.getTimeZone("GMT"), Locale.US);
        assertEquals(expectedShort, pShort.format(cal.getTime()));
        assertEquals(expectedFull, pFull.format(cal.getTime()));
    }

    // d ไม่เติมศูนย์: วันที่ < 10 กับ >= 10
    @Test
    public void testFormat_dayOfMonthPattern_unpadded() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("d", TimeZone.getTimeZone("GMT"), Locale.US);
        GregorianCalendar cal1 = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal1.clear();
        cal1.set(2014, Calendar.MARCH, 5);
        assertEquals("5", p.format(cal1.getTime()));

        GregorianCalendar cal2 = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal2.clear();
        cal2.set(2014, Calendar.MARCH, 25);
        assertEquals("25", p.format(cal2.getTime()));
    }

    // G era ใช้ DateFormatSymbols เป็น oracle
    @Test
    public void testFormat_eraPattern_matchesSymbols() throws Throwable {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.MARCH, 5);
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        String expectedEra = symbols.getEras()[cal.get(Calendar.ERA)];

        FastDatePrinter p = new FastDatePrinter("G", TimeZone.getTimeZone("GMT"), Locale.US);
        assertEquals(expectedEra, p.format(cal.getTime()));
    }

    // E แบบสั้น (<4) และเต็ม (>=4) ใช้ DateFormatSymbols เป็น oracle
    @Test
    public void testFormat_weekdayPattern_shortAndFull_matchesSymbols() throws Throwable {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.JANUARY, 1);
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        String expectedShort = symbols.getShortWeekdays()[dow];
        String expectedFull = symbols.getWeekdays()[dow];

        FastDatePrinter pShort = new FastDatePrinter("EEE", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDatePrinter pFull = new FastDatePrinter("EEEE", TimeZone.getTimeZone("GMT"), Locale.US);
        assertEquals(expectedShort, pShort.format(cal.getTime()));
        assertEquals(expectedFull, pFull.format(cal.getTime()));
    }

    // a (AM/PM) ใช้ DateFormatSymbols เป็น oracle
    @Test
    public void testFormat_amPmPattern_matchesSymbols() throws Throwable {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.JANUARY, 1, 14, 0, 0);
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        String expected = symbols.getAmPmStrings()[cal.get(Calendar.AM_PM)];

        FastDatePrinter p = new FastDatePrinter("a", TimeZone.getTimeZone("GMT"), Locale.US);
        assertEquals(expected, p.format(cal.getTime()));
    }

    // D และ DDD วันที่ 1 มกราคม: unpadded กับ padded 3 หลัก
    @Test
    public void testFormat_dayOfYearPattern_unpaddedAndPadded() throws Throwable {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.JANUARY, 1);

        FastDatePrinter pD = new FastDatePrinter("D", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDatePrinter pDDD = new FastDatePrinter("DDD", TimeZone.getTimeZone("GMT"), Locale.US);
        assertEquals("1", pD.format(cal.getTime()));
        assertEquals("001", pDDD.format(cal.getTime()));
    }

    // h,H,k,K ที่เที่ยงคืน: ตรวจการแปลงค่า 0 ของแต่ละ field ตามสัญญา 12/24 ชั่วโมง
    @Test
    public void testFormat_hourPatterns_atMidnight() throws Throwable {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.JANUARY, 1, 0, 0, 0);
        Date d = cal.getTime();

        assertEquals("0", new FastDatePrinter("H", TimeZone.getTimeZone("GMT"), Locale.US).format(d));
        assertEquals("12", new FastDatePrinter("h", TimeZone.getTimeZone("GMT"), Locale.US).format(d));
        assertEquals("24", new FastDatePrinter("k", TimeZone.getTimeZone("GMT"), Locale.US).format(d));
        assertEquals("0", new FastDatePrinter("K", TimeZone.getTimeZone("GMT"), Locale.US).format(d));
    }

    // mm,ss สองหลักเติมศูนย์
    @Test
    public void testFormat_minuteSecondPattern_paddedTwoDigits() throws Throwable {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.JANUARY, 1, 10, 5, 7);
        FastDatePrinter p = new FastDatePrinter("mm:ss", TimeZone.getTimeZone("GMT"), Locale.US);
        assertEquals("05:07", p.format(cal.getTime()));
    }

    // SS กับ millisecond >= 100: TwoDigitNumberField ไม่ตัดทอน แสดงค่าเต็ม
    @Test
    public void testFormat_millisecondPattern_overNinetyNine_showsFullNumber() throws Throwable {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.JANUARY, 1, 10, 0, 0);
        cal.set(Calendar.MILLISECOND, 500);
        FastDatePrinter p = new FastDatePrinter("SS", TimeZone.getTimeZone("GMT"), Locale.US);
        assertEquals("500", p.format(cal.getTime()));
    }

    // Z กับ ZZ สำหรับ offset บวก: ไม่มีและมีเครื่องหมาย colon
    @Test
    public void testFormat_timeZoneNumberPattern_positiveOffset_colonAndNoColon() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("GMT+05:30");
        FastDatePrinter pNoColon = new FastDatePrinter("Z", tz, Locale.US);
        FastDatePrinter pColon = new FastDatePrinter("ZZ", tz, Locale.US);
        Date d = new Date(0L);
        assertEquals("+0530", pNoColon.format(d));
        assertEquals("+05:30", pColon.format(d));
    }

    // Z สำหรับ offset ลบ
    @Test
    public void testFormat_timeZoneNumberPattern_negativeOffset() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("GMT-08:00");
        FastDatePrinter p = new FastDatePrinter("Z", tz, Locale.US);
        assertEquals("-0800", p.format(new Date(0L)));
    }

    // z (สั้น) และ zzzz (ยาว) ใช้ TimeZone.getDisplayName ของ JDK เป็น oracle โดยตรง
    @Test
    public void testFormat_timeZoneNamePattern_matchesJdkDisplayName() throws Throwable {
        TimeZone tz = TimeZone.getTimeZone("GMT");
        String expectedShort = tz.getDisplayName(false, TimeZone.SHORT, Locale.US);
        String expectedLong = tz.getDisplayName(false, TimeZone.LONG, Locale.US);

        FastDatePrinter pShort = new FastDatePrinter("z", tz, Locale.US);
        FastDatePrinter pLong = new FastDatePrinter("zzzz", tz, Locale.US);
        Date d = new Date(0L);
        assertEquals(expectedShort, pShort.format(d));
        assertEquals(expectedLong, pLong.format(d));
    }

    // format(Object,...) รองรับ Date, Calendar (timezone ตรงกัน), Long
    @Test
    public void testFormatObject_dateCalendarLong_delegateCorrectly() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        Date d = new Date(0L);

        StringBuffer sbDate = new StringBuffer();
        StringBuffer resDate = p.format((Object) d, sbDate, new FieldPosition(0));
        assertEquals("1970", resDate.toString());
        assertSame(sbDate, resDate);

        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.setTime(d);
        StringBuffer sbCal = new StringBuffer();
        assertEquals("1970", p.format((Object) cal, sbCal, new FieldPosition(0)).toString());

        StringBuffer sbLong = new StringBuffer();
        assertEquals("1970", p.format((Object) Long.valueOf(0L), sbLong, new FieldPosition(0)).toString());
    }

    // format(Object,...) กับชนิดที่ไม่รองรับ -> IllegalArgumentException
    @Test
    public void testFormatObject_unsupportedType_throwsIllegalArgumentException() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        try {
            p.format((Object) "not a date", new StringBuffer(), new FieldPosition(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().indexOf("Unknown class") >= 0);
        }
    }

    // format(Object,...) กับ null -> IllegalArgumentException พร้อมข้อความ <null>
    @Test
    public void testFormatObject_nullObject_throwsIllegalArgumentExceptionWithNullMessage() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        try {
            p.format((Object) null, new StringBuffer(), new FieldPosition(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().indexOf("<null>") >= 0);
        }
    }

    // format(long) ต้องให้ผลเหมือน format(new Date(millis))
    @Test
    public void testFormatLong_matchesFormatDate() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy-MM-dd", TimeZone.getTimeZone("GMT"), Locale.US);
        long millis = 1000000000L;
        assertEquals(p.format(new Date(millis)), p.format(millis));
    }

    // format(Date,StringBuffer) ต้องต่อท้าย buffer เดิมและคืน instance เดียวกัน
    @Test
    public void testFormatDateWithBuffer_appendsAndReturnsSameBuffer() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        StringBuffer buf = new StringBuffer("PRE-");
        StringBuffer result = p.format(new Date(0L), buf);
        assertSame(buf, result);
        assertEquals("PRE-1970", result.toString());
    }

    // format(Calendar,StringBuffer) ต้องต่อท้าย buffer เดิมและคืน instance เดียวกัน (timezone ตรงกัน)
    @Test
    public void testFormatCalendarWithBuffer_appendsAndReturnsSameBuffer() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.JANUARY, 1);
        StringBuffer buf = new StringBuffer("PRE-");
        StringBuffer result = p.format(cal, buf);
        assertSame(buf, result);
        assertEquals("PRE-2014", result.toString());
    }

    // BUG: format(Calendar) ต้องใช้ timezone ที่ตั้งค่าไว้ของ formatter ไม่ใช่ timezone ของ calendar ที่ส่งเข้ามา
    // (เช่นเดียวกับ format(Date)/format(long) ที่ hard code GregorianCalendar ด้วย mTimeZone)
    @Test
    public void testFormatCalendar_respectsConfiguredTimeZone_notCalendarsOwnZone() throws Throwable {
        TimeZone printerZone = TimeZone.getTimeZone("GMT+00:00");
        TimeZone otherZone = TimeZone.getTimeZone("GMT+10:00");
        FastDatePrinter printer = new FastDatePrinter("HH:mm", printerZone, Locale.US);

        GregorianCalendar cal = new GregorianCalendar(otherZone, Locale.US);
        cal.clear();
        cal.set(2014, Calendar.JANUARY, 1, 20, 0, 0);

        String result = printer.format(cal);
        assertEquals("10:00", result);
    }

    // getMaxLengthEstimate ต้องเป็นขอบบนของความยาวข้อความจริงตามที่ Javadoc สัญญาไว้
    @Test
    public void testGetMaxLengthEstimate_actualLengthWithinEstimate() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy-MM-dd", TimeZone.getTimeZone("GMT"), Locale.US);
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.US);
        cal.clear();
        cal.set(2014, Calendar.MARCH, 5);
        String formatted = p.format(cal.getTime());
        assertTrue(formatted.length() <= p.getMaxLengthEstimate());
    }

    // equals: เท่ากันเมื่อ pattern/timezone/locale เหมือนกัน, ไม่เท่ากันเมื่อ pattern ต่าง หรือชนิดต่าง
    @Test
    public void testEquals_sameFieldsTrue_differentPatternOrTypeFalse() throws Throwable {
        FastDatePrinter p1 = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDatePrinter p2 = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDatePrinter p3 = new FastDatePrinter("MM", TimeZone.getTimeZone("GMT"), Locale.US);

        assertTrue(p1.equals(p2));
        assertFalse(p1.equals(p3));
        assertFalse(p1.equals("not a printer"));
    }

    // hashCode ต้องสอดคล้องกับ equals (object เท่ากันต้องมี hashCode เท่ากัน)
    @Test
    public void testHashCode_consistentWithEquals() throws Throwable {
        FastDatePrinter p1 = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        FastDatePrinter p2 = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        assertTrue(p1.equals(p2));
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    // toString ต้องมีข้อมูล pattern, locale, timezone id ตามรูปแบบที่เขียนในโค้ด
    @Test
    public void testToString_containsPatternAndTimeZoneId() throws Throwable {
        FastDatePrinter p = new FastDatePrinter("yyyy", TimeZone.getTimeZone("GMT"), Locale.US);
        String ts = p.toString();
        assertTrue(ts.indexOf("yyyy") >= 0);
        assertTrue(ts.indexOf("GMT") >= 0);
    }
}
