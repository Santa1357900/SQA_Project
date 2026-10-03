package org.apache.commons.lang.time;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.TimeZone;

public class DurationFormatUtilsClaudeTest {

    // ตรวจ constructor public สร้าง instance ได้ (JavaBean requirement ตาม Javadoc)
    @Test
    public void testConstructor_createsInstance() throws Throwable {
        DurationFormatUtils instance = new DurationFormatUtils();
        assertNotNull(instance);
    }

    // ตรวจค่าคงที่ ISO_EXTENDED_FORMAT_PATTERN ตรงกับที่ประกาศใน source
    @Test
    public void testIsoExtendedFormatPattern_constantValue() throws Throwable {
        assertEquals("'P'yyyy'Y'M'M'd'DT'H'H'm'M's.S'S'", DurationFormatUtils.ISO_EXTENDED_FORMAT_PATTERN);
    }

    // formatDurationHMS: durationMillis=0 -> ทุก field เป็น 0 พร้อม pad
    @Test
    public void testFormatDurationHMS_zeroMillis_zeroedOutput() throws Throwable {
        assertEquals("0:00:00.000", DurationFormatUtils.formatDurationHMS(0L));
    }

    // formatDurationHMS: pattern H:mm:ss.SSS ไม่มี token 'd' -> hours ไม่ wrap ที่ 24
    @Test
    public void testFormatDurationHMS_noDaysToken_hoursExceed24() throws Throwable {
        assertEquals("25:00:00.000", DurationFormatUtils.formatDurationHMS(90000000L));
    }

    // formatDurationHMS: ทดสอบ H,m,s,S ทั้งหมด non-zero พร้อม carry hack ของ S
    @Test
    public void testFormatDurationHMS_nonZeroFields() throws Throwable {
        assertEquals("1:01:01.001", DurationFormatUtils.formatDurationHMS(3661001L));
    }

    // formatDurationISO: durationMillis=0 -> P0Y0M0DT0H0M0.000S ตามมาตรฐาน ISO8601 period
    @Test
    public void testFormatDurationISO_zeroMillis_fullIsoZero() throws Throwable {
        assertEquals("P0Y0M0DT0H0M0.000S", DurationFormatUtils.formatDurationISO(0L));
    }

    // formatDurationISO: durationMillis=1 day exact -> days ถูกคำนวณจาก token 'd'
    @Test
    public void testFormatDurationISO_exactOneDay() throws Throwable {
        assertEquals("P0Y0M1DT0H0M0.000S", DurationFormatUtils.formatDurationISO(86400000L));
    }

    // formatDuration(2-arg): pad=true default, count=1 ทุก token -> ไม่มีผลต่างจาก unpadded
    @Test
    public void testFormatDuration2Arg_defaultPad_singleDigit() throws Throwable {
        assertEquals("2:3:4:5", DurationFormatUtils.formatDuration(183845000L, "d:H:m:s"));
    }

    // formatDuration(2-arg): pattern มี count=2 -> leftPad ด้วย '0'
    @Test
    public void testFormatDuration2Arg_paddedMultiField() throws Throwable {
        assertEquals("02:03:04:05", DurationFormatUtils.formatDuration(183845000L, "dd:HH:mm:ss"));
    }

    // formatDuration(3-arg): padWithZeros=false -> ไม่สนใจ count ของ token
    @Test
    public void testFormatDuration3Arg_noPad_ignoresCount() throws Throwable {
        assertEquals("2:3:4:5", DurationFormatUtils.formatDuration(183845000L, "dd:HH:mm:ss", false));
    }

    // formatDuration: pattern เป็น literal ล้วน -> durationMillis ไม่มีผลต่อผลลัพธ์
    @Test
    public void testFormatDuration_literalOnlyPattern() throws Throwable {
        assertEquals("no time info", DurationFormatUtils.formatDuration(999999L, "'no time info'"));
    }

    // formatDurationWords: ไม่ suppress อะไร, duration=0 -> คงรูปพหูพจน์ดิบทุก field
    @Test
    public void testFormatDurationWords_noSuppress_rawZeroDuration() throws Throwable {
        assertEquals("0 days 0 hours 0 minutes 0 seconds",
                DurationFormatUtils.formatDurationWords(0L, false, false));
    }

    // formatDurationWords: ไม่ suppress, ผสม singular(day=1)กับ plural field อื่น
    @Test
    public void testFormatDurationWords_noSuppress_mixedPluralSingular() throws Throwable {
        assertEquals("1 day 2 hours 3 minutes 4 seconds",
                DurationFormatUtils.formatDurationWords(93784000L, false, false));
    }

    // formatDurationWords: ทุก field=1 -> ทุก token ถูกแปลงเป็น singular
    @Test
    public void testFormatDurationWords_allFieldsOne_allSingular() throws Throwable {
        assertEquals("1 day 1 hour 1 minute 1 second",
                DurationFormatUtils.formatDurationWords(90061000L, false, false));
    }

    // formatDurationWords: suppressTrailingZeroElements ตัด minutes/seconds=0 แต่หยุดที่ hours!=0
    @Test
    public void testFormatDurationWords_suppressTrailing_stopsAtNonZero() throws Throwable {
        assertEquals("5 days 2 hours",
                DurationFormatUtils.formatDurationWords(439200000L, false, true));
    }

    // formatDurationWords: suppressLeadingZeroElements ตัด days=0 แล้วหยุดเพราะ hours!=0
    @Test
    public void testFormatDurationWords_suppressLeading_stopsAtNonZeroHours() throws Throwable {
        assertEquals("3 hours 15 minutes 10 seconds",
                DurationFormatUtils.formatDurationWords(11710000L, true, false));
    }

    // BUG: duration=0 ทุก field=0, suppressLeading=true ควรลบจนเหลือ "" (สมมาตรกับ suppressTrailing)
    @Test
    public void testFormatDurationWords_suppressLeading_zeroDuration_expectEmpty_BUG() throws Throwable {
        assertEquals("", DurationFormatUtils.formatDurationWords(0L, true, false));
    }

    // BUG: duration=0, suppress ทั้ง leading และ trailing ก็ควรได้ "" เช่นกัน
    @Test
    public void testFormatDurationWords_suppressLeadingAndTrailing_zeroDuration_expectEmpty_BUG() throws Throwable {
        assertEquals("", DurationFormatUtils.formatDurationWords(0L, true, true));
    }

    // formatPeriodISO: ระยะเวลาสั้น(<28วัน) -> delegate ไป formatDuration ด้วย ISO pattern, pad=false
    @Test
    public void testFormatPeriodISO_shortDuration() throws Throwable {
        assertEquals("P0Y0M0DT1H1M1.001S", DurationFormatUtils.formatPeriodISO(0L, 3661001L));
    }

    // formatPeriod(3-arg): ระยะเวลาสั้น, pad=true default, ไม่แตะ timezone
    @Test
    public void testFormatPeriod3Arg_shortDuration_defaultPad() throws Throwable {
        assertEquals("1:01:01", DurationFormatUtils.formatPeriod(1000L, 1000L + 3661001L, "H:mm:ss"));
    }

    // formatPeriod(5-arg): ระยะเวลาสั้น, padWithZeros=false
    @Test
    public void testFormatPeriod5Arg_shortDuration_noPad() throws Throwable {
        assertEquals("1:1:1", DurationFormatUtils.formatPeriod(0L, 3661001L, "H:mm:ss", false,
                TimeZone.getTimeZone("GMT")));
    }

    // formatPeriod(5-arg): ระยะเวลายาว(>=28วัน) พอดี 1 ปีเต็ม -> years=1,months=0,days=0
    @Test
    public void testFormatPeriod5Arg_longDuration_exactOneYear() throws Throwable {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.clear();
        cal.set(2000, Calendar.JANUARY, 1, 0, 0, 0);
        long start = cal.getTimeInMillis();
        cal.clear();
        cal.set(2001, Calendar.JANUARY, 1, 0, 0, 0);
        long end = cal.getTimeInMillis();
        String result = DurationFormatUtils.formatPeriod(start, end, "y' years'", true,
                TimeZone.getTimeZone("GMT"));
        assertEquals("1 years", result);
    }

    // format(): token ที่เป็น literal (StringBuffer) -> append ตรงตัว
    @Test
    public void testFormat_literalToken() throws Throwable {
        DurationFormatUtils.Token[] tokens = new DurationFormatUtils.Token[] {
                new DurationFormatUtils.Token(new StringBuffer("xyz")) };
        assertEquals("xyz", DurationFormatUtils.format(tokens, 0, 0, 0, 0, 0, 0, 0, true));
    }

    // format(): y,M,d,H,m ทุกตัวพร้อม pad=true -> leftPad ตาม count ของแต่ละ token
    @Test
    public void testFormat_dateTimeTokens_paddedWithZeros() throws Throwable {
        DurationFormatUtils.Token[] tokens = new DurationFormatUtils.Token[] {
                new DurationFormatUtils.Token(DurationFormatUtils.y, 4),
                new DurationFormatUtils.Token(DurationFormatUtils.M, 2),
                new DurationFormatUtils.Token(DurationFormatUtils.d, 2),
                new DurationFormatUtils.Token(DurationFormatUtils.H, 2),
                new DurationFormatUtils.Token(DurationFormatUtils.m, 2) };
        assertEquals("000708091011", DurationFormatUtils.format(tokens, 7, 8, 9, 10, 11, 0, 0, true));
    }

    // format(): y,M,d,H,m ทุกตัวพร้อม pad=false -> ไม่สนใจ count เลย
    @Test
    public void testFormat_dateTimeTokens_noPad() throws Throwable {
        DurationFormatUtils.Token[] tokens = new DurationFormatUtils.Token[] {
                new DurationFormatUtils.Token(DurationFormatUtils.y, 4),
                new DurationFormatUtils.Token(DurationFormatUtils.M, 2),
                new DurationFormatUtils.Token(DurationFormatUtils.d, 2),
                new DurationFormatUtils.Token(DurationFormatUtils.H, 2),
                new DurationFormatUtils.Token(DurationFormatUtils.m, 2) };
        assertEquals("7891011", DurationFormatUtils.format(tokens, 7, 8, 9, 10, 11, 0, 0, false));
    }

    // format(): token 's' ตามด้วย 'S' -> เกิด carry-hack +1000 แล้ว substring(1)
    @Test
    public void testFormat_sThenSToken_millisecondCarryHack() throws Throwable {
        DurationFormatUtils.Token[] tokens = new DurationFormatUtils.Token[] {
                new DurationFormatUtils.Token(DurationFormatUtils.s, 1),
                new DurationFormatUtils.Token(DurationFormatUtils.S, 3) };
        assertEquals("2005", DurationFormatUtils.format(tokens, 0, 0, 0, 0, 0, 2, 5, true));
    }

    // format(): token 'S' โดยไม่มี 's' นำหน้า, pad=true -> leftPad ตรงไปตรงมา
    @Test
    public void testFormat_sTokenWithoutPrecedingS_padded() throws Throwable {
        DurationFormatUtils.Token[] tokens = new DurationFormatUtils.Token[] {
                new DurationFormatUtils.Token(DurationFormatUtils.S, 3) };
        assertEquals("007", DurationFormatUtils.format(tokens, 0, 0, 0, 0, 0, 0, 7, true));
    }

    // format(): token 'S' โดยไม่มี 's' นำหน้า, pad=false -> toString ตรงๆ
    @Test
    public void testFormat_sTokenWithoutPrecedingS_noPad() throws Throwable {
        DurationFormatUtils.Token[] tokens = new DurationFormatUtils.Token[] {
                new DurationFormatUtils.Token(DurationFormatUtils.S, 3) };
        assertEquals("7", DurationFormatUtils.format(tokens, 0, 0, 0, 0, 0, 0, 7, false));
    }

    // reduceAndCorrect: endValue<startValue หลัง add ลบ -> ปรับ end กลับและ return diff
    @Test
    public void testReduceAndCorrect_correctionAppliedWhenEndBelowStart() throws Throwable {
        Calendar start = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        start.clear();
        start.set(2023, Calendar.JANUARY, 15, 0, 0, 0);
        Calendar end = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        end.clear();
        end.set(2023, Calendar.MARCH, 10, 0, 0, 0);
        int result = DurationFormatUtils.reduceAndCorrect(start, end, Calendar.DAY_OF_MONTH, 26);
        assertEquals(3, result);
        assertEquals(15, end.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.FEBRUARY, end.get(Calendar.MONTH));
    }

    // reduceAndCorrect: endValue>=startValue หลัง add -> ไม่ต้องแก้ไข, return 0
    @Test
    public void testReduceAndCorrect_noCorrectionWhenEqual() throws Throwable {
        Calendar start = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        start.clear();
        start.set(2023, Calendar.JANUARY, 1, 0, 0, 0);
        Calendar end = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        end.clear();
        end.set(2023, Calendar.JANUARY, 1, 0, 0, 0);
        int result = DurationFormatUtils.reduceAndCorrect(start, end, Calendar.DAY_OF_MONTH, 0);
        assertEquals(0, result);
        assertEquals(1, end.get(Calendar.DAY_OF_MONTH));
    }

    // lexx: ตัวอักษรซ้ำ 'yyyy' -> token เดียวที่มี count=4
    @Test
    public void testLexx_repeatedCharacter_incrementsCount() throws Throwable {
        DurationFormatUtils.Token[] tokens = DurationFormatUtils.lexx("yyyy");
        assertEquals(1, tokens.length);
        assertSame(DurationFormatUtils.y, tokens[0].getValue());
        assertEquals(4, tokens[0].getCount());
    }

    // lexx: pattern ผสม token กับ literal หลายตัว -> แยก token ถูกต้องตามลำดับ
    @Test
    public void testLexx_mixedTokensAndLiterals() throws Throwable {
        DurationFormatUtils.Token[] tokens = DurationFormatUtils.lexx("yyyy-MM-dd");
        assertEquals(5, tokens.length);
        assertSame(DurationFormatUtils.y, tokens[0].getValue());
        assertEquals(4, tokens[0].getCount());
        assertEquals("-", tokens[1].getValue().toString());
        assertSame(DurationFormatUtils.M, tokens[2].getValue());
        assertEquals(2, tokens[2].getCount());
        assertSame(DurationFormatUtils.d, tokens[4].getValue());
    }

    // lexx: literal ใน quote เดี่ยว -> ถูกแยกออกเป็น StringBuffer โดยไม่มีเครื่องหมาย quote
    @Test
    public void testLexx_quotedLiteral_stripsQuotes() throws Throwable {
        DurationFormatUtils.Token[] tokens = DurationFormatUtils.lexx("'hello'");
        assertEquals(1, tokens.length);
        assertTrue(tokens[0].getValue() instanceof StringBuffer);
        assertEquals("hello", tokens[0].getValue().toString());
    }

    // lexx: format ว่าง -> ได้ array ว่าง
    @Test
    public void testLexx_emptyString_returnsEmptyArray() throws Throwable {
        DurationFormatUtils.Token[] tokens = DurationFormatUtils.lexx("");
        assertEquals(0, tokens.length);
    }

    // lexx: ตัวอักษรที่ไม่รู้จัก (เช่น 'z') ถูกจัดเป็น literal ผ่าน default case
    @Test
    public void testLexx_unrecognizedChar_treatedAsLiteral() throws Throwable {
        DurationFormatUtils.Token[] tokens = DurationFormatUtils.lexx("z");
        assertEquals(1, tokens.length);
        assertEquals("z", tokens[0].getValue().toString());
    }

    // Token: constructor 1 arg -> count เริ่มต้นเป็น 1
    @Test
    public void testToken_singleArgConstructor_countIsOne() throws Throwable {
        DurationFormatUtils.Token t = new DurationFormatUtils.Token("x");
        assertEquals(1, t.getCount());
        assertEquals("x", t.getValue());
    }

    // Token: constructor 2 arg กำหนด count เอง และ increment() เพิ่มค่าได้
    @Test
    public void testToken_twoArgConstructor_andIncrement() throws Throwable {
        DurationFormatUtils.Token t = new DurationFormatUtils.Token("x", 2);
        assertEquals(2, t.getCount());
        t.increment();
        assertEquals(3, t.getCount());
    }

    // Token.equals: value และ count เท่ากัน (reference เดียวกัน) -> true
    @Test
    public void testToken_equals_sameValueSameCount_true() throws Throwable {
        DurationFormatUtils.Token a = new DurationFormatUtils.Token(DurationFormatUtils.y, 2);
        DurationFormatUtils.Token b = new DurationFormatUtils.Token(DurationFormatUtils.y, 2);
        assertTrue(a.equals(b));
    }

    // Token.equals: count ต่างกัน -> false
    @Test
    public void testToken_equals_differentCount_false() throws Throwable {
        DurationFormatUtils.Token a = new DurationFormatUtils.Token(DurationFormatUtils.y, 2);
        DurationFormatUtils.Token b = new DurationFormatUtils.Token(DurationFormatUtils.y, 3);
        assertFalse(a.equals(b));
    }

    // Token.equals: value เป็นคนละ class (String กับ Integer) -> false
    @Test
    public void testToken_equals_differentValueClass_false() throws Throwable {
        DurationFormatUtils.Token a = new DurationFormatUtils.Token("x");
        DurationFormatUtils.Token b = new DurationFormatUtils.Token(Integer.valueOf(1));
        assertFalse(a.equals(b));
    }

    // Token.equals: value เป็น StringBuffer เนื้อหาเดียวกัน และ value เป็น Number เท่ากัน -> true ทั้งคู่
    @Test
    public void testToken_equals_stringBufferAndNumberValues_true() throws Throwable {
        DurationFormatUtils.Token a1 = new DurationFormatUtils.Token(new StringBuffer("ab"));
        DurationFormatUtils.Token b1 = new DurationFormatUtils.Token(new StringBuffer("ab"));
        assertTrue(a1.equals(b1));
        DurationFormatUtils.Token a2 = new DurationFormatUtils.Token(Integer.valueOf(5));
        DurationFormatUtils.Token b2 = new DurationFormatUtils.Token(Integer.valueOf(5));
        assertTrue(a2.equals(b2));
    }

    // Token.equals: เทียบกับ object ที่ไม่ใช่ Token -> false
    @Test
    public void testToken_equals_notATokenInstance_false() throws Throwable {
        DurationFormatUtils.Token a = new DurationFormatUtils.Token("x");
        assertFalse(a.equals("x"));
    }

    // Token.hashCode: ต้องเท่ากับ hashCode ของ value ตาม Javadoc
    @Test
    public void testToken_hashCode_matchesValueHashCode() throws Throwable {
        DurationFormatUtils.Token t = new DurationFormatUtils.Token("abc");
        assertEquals("abc".hashCode(), t.hashCode());
    }

    // Token.toString: ทวนค่า value ตามจำนวน count (StringUtils.repeat)
    @Test
    public void testToken_toString_repeatsByCount() throws Throwable {
        DurationFormatUtils.Token t = new DurationFormatUtils.Token("y", 3);
        assertEquals("yyy", t.toString());
    }

    // Token.containsTokenWithValue: หาเจอ -> true, หาไม่เจอ -> false
    @Test
    public void testToken_containsTokenWithValue_trueAndFalse() throws Throwable {
        DurationFormatUtils.Token[] arr = new DurationFormatUtils.Token[] {
                new DurationFormatUtils.Token(DurationFormatUtils.y),
                new DurationFormatUtils.Token(DurationFormatUtils.M) };
        assertTrue(DurationFormatUtils.Token.containsTokenWithValue(arr, DurationFormatUtils.y));
        assertFalse(DurationFormatUtils.Token.containsTokenWithValue(arr, DurationFormatUtils.d));
    }
}
