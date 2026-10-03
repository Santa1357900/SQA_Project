package org.apache.commons.compress.archivers.tar;

import static org.junit.Assert.*;
import org.junit.Test;

public class TarUtilsClaudeTest {

    // length < 2 ต้อง throw ตาม contract
    @Test
    public void testParseOctal_lengthLessThan2_throwsIllegalArgumentException() throws Throwable {
        byte[] buffer = {0};
        try {
            TarUtils.parseOctal(buffer, 0, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // leading NUL byte แรก ต้อง return 0 ทันทีตาม javadoc workaround
    @Test
    public void testParseOctal_leadingNul_returnsZero() throws Throwable {
        byte[] buffer = {0, 0};
        assertEquals(0L, TarUtils.parseOctal(buffer, 0, 2));
    }

    // ตัวเลขออคตัลปกติ ปิดท้ายด้วย space เดียว
    @Test
    public void testParseOctal_validOctalWithTrailingSpace_returnsValue() throws Throwable {
        byte[] buffer = {'7', '5', '5', ' '};
        assertEquals(493L, TarUtils.parseOctal(buffer, 0, 4));
    }

    // trailing NUL แล้วตามด้วย space เพิ่ม (two trailers)
    @Test
    public void testParseOctal_validOctalWithTrailingNulAndSpace_returnsValue() throws Throwable {
        byte[] buffer = {'1', '0', ' ', 0};
        assertEquals(8L, TarUtils.parseOctal(buffer, 0, 4));
    }

    // leading space ถูกข้ามไปก่อนพาร์สตัวเลข
    @Test
    public void testParseOctal_leadingSpacesIgnored_returnsValue() throws Throwable {
        byte[] buffer = {' ', ' ', '7', ' '};
        assertEquals(7L, TarUtils.parseOctal(buffer, 0, 4));
    }

    // digit นอกช่วง 0-7 ต้อง throw
    @Test
    public void testParseOctal_invalidDigit_throwsIllegalArgumentException() throws Throwable {
        byte[] buffer = {'8', ' '};
        try {
            TarUtils.parseOctal(buffer, 0, 2);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // ไม่มี trailing space/NUL ต้อง throw
    @Test
    public void testParseOctal_missingTrailingSpaceOrNul_throwsIllegalArgumentException() throws Throwable {
        byte[] buffer = {'7', '5'};
        try {
            TarUtils.parseOctal(buffer, 0, 2);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // high bit ไม่ถูกตั้ง -> delegate ไป parseOctal
    @Test
    public void testParseOctalOrBinary_highBitNotSet_delegatesToParseOctal() throws Throwable {
        byte[] buffer = {'7', ' '};
        assertEquals(7L, TarUtils.parseOctalOrBinary(buffer, 0, 2));
    }

    // high bit ตั้งแต่ไม่ใช่ 0xff -> ถือเป็นค่า positive binary
    @Test
    public void testParseOctalOrBinary_highBitSetNonFF_treatedAsPositive() throws Throwable {
        byte[] buffer = new byte[8];
        buffer[0] = (byte) 0x81;
        buffer[7] = 42;
        assertEquals(42L, TarUtils.parseOctalOrBinary(buffer, 0, 8));
    }

    // round trip ค่าลบ length<9 (parseBinaryLong)
    @Test
    public void testParseOctalOrBinary_roundTripNegativeSmallLength() throws Throwable {
        long value = -12345L;
        byte[] buf = new byte[8];
        TarUtils.formatLongOctalOrBinaryBytes(value, buf, 0, 8);
        assertEquals(value, TarUtils.parseOctalOrBinary(buf, 0, 8));
    }

    // round trip ค่า positive ขนาดใหญ่ length<9
    @Test
    public void testParseOctalOrBinary_roundTripPositiveLargeSmallLength() throws Throwable {
        long value = 123456789012345L;
        byte[] buf = new byte[8];
        TarUtils.formatLongOctalOrBinaryBytes(value, buf, 0, 8);
        assertEquals(value, TarUtils.parseOctalOrBinary(buf, 0, 8));
    }

    // round trip ค่าลบ length>=9 (parseBinaryBigInteger)
    @Test
    public void testParseOctalOrBinary_roundTripNegativeLength9() throws Throwable {
        long value = -987654321987L;
        byte[] buf = new byte[9];
        TarUtils.formatLongOctalOrBinaryBytes(value, buf, 0, 9);
        assertEquals(value, TarUtils.parseOctalOrBinary(buf, 0, 9));
    }

    // byte == 1 -> true
    @Test
    public void testParseBoolean_byteIsOne_returnsTrue() throws Throwable {
        byte[] buffer = {1};
        assertTrue(TarUtils.parseBoolean(buffer, 0));
    }

    // byte == 0 -> false
    @Test
    public void testParseBoolean_byteIsZero_returnsFalse() throws Throwable {
        byte[] buffer = {0};
        assertFalse(TarUtils.parseBoolean(buffer, 0));
    }

    // byte อื่นที่ไม่ใช่ 1 -> false
    @Test
    public void testParseBoolean_byteOther_returnsFalse() throws Throwable {
        byte[] buffer = {2};
        assertFalse(TarUtils.parseBoolean(buffer, 0));
    }

    // trailing NUL ถูกตัดออก
    @Test
    public void testParseName_trailingNulsTrimmed_returnsString() throws Throwable {
        byte[] buffer = {'a', 'b', 'c', 0, 0};
        assertEquals("abc", TarUtils.parseName(buffer, 0, 5));
    }

    // ไม่มี trailing NUL เลย -> ใช้ทั้งบัฟเฟอร์
    @Test
    public void testParseName_noTrailingNul_returnsFullString() throws Throwable {
        byte[] buffer = {'h', 'i'};
        assertEquals("hi", TarUtils.parseName(buffer, 0, 2));
    }

    // บัฟเฟอร์เป็น NUL ทั้งหมด -> คืนค่าสตริงว่าง
    @Test
    public void testParseName_allNul_returnsEmptyString() throws Throwable {
        byte[] buffer = {0, 0, 0};
        assertEquals("", TarUtils.parseName(buffer, 0, 3));
    }

    // ใช้ encoding ชัดเจน (FALLBACK_ENCODING) หยุดที่ NUL ฝังตัวระหว่าง decode
    @Test
    public void testParseName_withEncoding_stopsAtEmbeddedNul() throws Throwable {
        byte[] buffer = {'x', 'y', 0, 'z'};
        String result = TarUtils.parseName(buffer, 0, 4, TarUtils.FALLBACK_ENCODING);
        assertEquals("xy", result);
    }

    // ชื่อสั้นกว่าบัฟเฟอร์ -> เติม NUL ท้าย
    @Test
    public void testFormatNameBytes_shorterThanBuffer_padsWithNul() throws Throwable {
        byte[] buf = new byte[5];
        int ret = TarUtils.formatNameBytes("ab", buf, 0, 5);
        assertEquals(5, ret);
        assertEquals((byte) 'a', buf[0]);
        assertEquals((byte) 'b', buf[1]);
        assertEquals(0, buf[2]);
        assertEquals(0, buf[4]);
    }

    // ชื่อยาวกว่าบัฟเฟอร์ -> ถูกตัด (truncate)
    @Test
    public void testFormatNameBytes_longerThanBuffer_truncates() throws Throwable {
        byte[] buf = new byte[3];
        int ret = TarUtils.formatNameBytes("abcdef", buf, 0, 3);
        assertEquals(3, ret);
        assertEquals((byte) 'a', buf[0]);
        assertEquals((byte) 'c', buf[2]);
    }

    // ค่าที่คืนกลับต้องเป็น offset+length เสมอ
    @Test
    public void testFormatNameBytes_returnsOffsetPlusLength() throws Throwable {
        byte[] buf = new byte[4];
        int ret = TarUtils.formatNameBytes("x", buf, 1, 2);
        assertEquals(3, ret);
        assertEquals((byte) 'x', buf[1]);
        assertEquals(0, buf[2]);
    }

    // เวอร์ชันที่รับ encoding ชัดเจน พร้อม offset ไม่เป็น 0
    @Test
    public void testFormatNameBytes_withEncodingAndOffset_copiesAtPosition() throws Throwable {
        byte[] buf = new byte[6];
        int ret = TarUtils.formatNameBytes("hi", buf, 2, 3, TarUtils.FALLBACK_ENCODING);
        assertEquals(5, ret);
        assertEquals((byte) 'h', buf[2]);
        assertEquals((byte) 'i', buf[3]);
        assertEquals(0, buf[4]);
        assertEquals(0, buf[0]);
    }

    // value == 0 -> เติมศูนย์ทั้งหมด
    @Test
    public void testFormatUnsignedOctalString_zero_fillsAllZeroDigits() throws Throwable {
        byte[] buffer = new byte[4];
        TarUtils.formatUnsignedOctalString(0, buffer, 0, 4);
        assertEquals((byte) '0', buffer[0]);
        assertEquals((byte) '0', buffer[3]);
    }

    // ค่า octal ธรรมดา พร้อม leading zero padding
    @Test
    public void testFormatUnsignedOctalString_positiveValue_correctOctalDigits() throws Throwable {
        byte[] buffer = new byte[4];
        TarUtils.formatUnsignedOctalString(8, buffer, 0, 4);
        assertEquals((byte) '0', buffer[0]);
        assertEquals((byte) '0', buffer[1]);
        assertEquals((byte) '1', buffer[2]);
        assertEquals((byte) '0', buffer[3]);
    }

    // ค่าต้องการ digit มากกว่าพื้นที่บัฟเฟอร์ -> throw
    @Test
    public void testFormatUnsignedOctalString_tooLargeForBuffer_throwsIllegalArgumentException() throws Throwable {
        byte[] buffer = new byte[1];
        try {
            TarUtils.formatUnsignedOctalString(8, buffer, 0, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // ค่า -1 ต้องถูกตีความแบบ unsigned 64-bit -> octal "1" + 21*"7"
    @Test
    public void testFormatUnsignedOctalString_negativeValueTreatedAsUnsigned_returnsFullOctal() throws Throwable {
        byte[] buffer = new byte[22];
        TarUtils.formatUnsignedOctalString(-1L, buffer, 0, 22);
        assertEquals((byte) '1', buffer[0]);
        for (int i = 1; i < 22; i++) {
            assertEquals((byte) '7', buffer[i]);
        }
    }

    // ต่อท้ายด้วย space และ NUL พร้อม return offset+length
    @Test
    public void testFormatOctalBytes_appendsSpaceAndNul_returnsOffsetPlusLength() throws Throwable {
        byte[] buf = new byte[6];
        int ret = TarUtils.formatOctalBytes(8, buf, 0, 6);
        assertEquals(6, ret);
        assertEquals((byte) '1', buf[2]);
        assertEquals((byte) ' ', buf[4]);
        assertEquals(0, buf[5]);
    }

    // ค่าใหญ่เกินไปสำหรับความยาวที่กำหนด -> throw
    @Test
    public void testFormatOctalBytes_tooLargeForBuffer_throwsIllegalArgumentException() throws Throwable {
        byte[] buf = new byte[3];
        try {
            TarUtils.formatOctalBytes(8, buf, 0, 3);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // ต่อท้ายด้วย space เท่านั้น (ไม่มี NUL)
    @Test
    public void testFormatLongOctalBytes_appendsSpaceOnly() throws Throwable {
        byte[] buf = new byte[5];
        int ret = TarUtils.formatLongOctalBytes(8, buf, 0, 5);
        assertEquals(5, ret);
        assertEquals((byte) '1', buf[2]);
        assertEquals((byte) ' ', buf[4]);
    }

    // value == MAXID (ขอบเขต <=) ต้องใช้ octal encoding ไม่มี marker bit
    @Test
    public void testFormatLongOctalOrBinaryBytes_atMaxId_usesOctal() throws Throwable {
        long val = TarConstants.MAXID;
        byte[] buf = new byte[TarConstants.UIDLEN];
        int ret = TarUtils.formatLongOctalOrBinaryBytes(val, buf, 0, TarConstants.UIDLEN);
        assertEquals(TarConstants.UIDLEN, ret);
        assertEquals(0, buf[0] & 0x80);
        assertEquals(val, TarUtils.parseOctalOrBinary(buf, 0, TarConstants.UIDLEN));
    }

    // value == MAXID+1 เกินขอบเขต octal -> ต้องใช้ binary พร้อม marker 0x80
    @Test
    public void testFormatLongOctalOrBinaryBytes_exceedsMaxId_usesBinary() throws Throwable {
        long val = TarConstants.MAXID + 1;
        byte[] buf = new byte[TarConstants.UIDLEN];
        int ret = TarUtils.formatLongOctalOrBinaryBytes(val, buf, 0, TarConstants.UIDLEN);
        assertEquals(TarConstants.UIDLEN, ret);
        assertEquals((byte) 0x80, buf[0]);
        assertEquals(val, TarUtils.parseOctalOrBinary(buf, 0, TarConstants.UIDLEN));
    }

    // ค่าลบ ต้องใช้ binary พร้อม marker 0xff เสมอ ไม่ว่า maxAsOctalChar เท่าไหร่
    @Test
    public void testFormatLongOctalOrBinaryBytes_negativeValue_usesBinaryWithFFMarker() throws Throwable {
        long val = -5L;
        byte[] buf = new byte[8];
        int ret = TarUtils.formatLongOctalOrBinaryBytes(val, buf, 0, 8);
        assertEquals(8, ret);
        assertEquals((byte) 0xff, buf[0]);
        assertEquals(val, TarUtils.parseOctalOrBinary(buf, 0, 8));
    }

    // ค่าใหญ่เกินกว่าที่ field เล็ก (length<9) จะเก็บเป็น binary ได้ -> throw
    @Test
    public void testFormatLongOctalOrBinaryBytes_tooLargeForSmallField_throwsIllegalArgumentException() throws Throwable {
        byte[] buf = new byte[8];
        try {
            TarUtils.formatLongOctalOrBinaryBytes(Long.MAX_VALUE, buf, 0, 8);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // length>=9 ใช้เส้นทาง BigInteger ทั้ง format และ parse ต้อง round trip ถูกต้อง
    @Test
    public void testFormatLongOctalOrBinaryBytes_largeLengthBigIntegerPath_roundTrips() throws Throwable {
        long val = -123456789L;
        byte[] buf = new byte[12];
        int ret = TarUtils.formatLongOctalOrBinaryBytes(val, buf, 0, 12);
        assertEquals(12, ret);
        assertEquals((byte) 0xff, buf[0]);
        assertEquals(val, TarUtils.parseOctalOrBinary(buf, 0, 12));
    }

    // ต่อท้ายด้วย NUL แล้ว space (ลำดับกลับกับ formatOctalBytes)
    @Test
    public void testFormatCheckSumOctalBytes_appendsNulThenSpace_returnsOffsetPlusLength() throws Throwable {
        byte[] buf = new byte[6];
        int ret = TarUtils.formatCheckSumOctalBytes(8, buf, 0, 6);
        assertEquals(6, ret);
        assertEquals((byte) '1', buf[2]);
        assertEquals(0, buf[4]);
        assertEquals((byte) ' ', buf[5]);
    }

    // sum ของ unsigned byte value ธรรมดา
    @Test
    public void testComputeCheckSum_sumsUnsignedByteValues() throws Throwable {
        byte[] buf = {(byte) 0xFF, 1, 2};
        assertEquals(258L, TarUtils.computeCheckSum(buf));
    }

    // array ว่าง -> sum เป็น 0
    @Test
    public void testComputeCheckSum_emptyArray_returnsZero() throws Throwable {
        byte[] buf = new byte[0];
        assertEquals(0L, TarUtils.computeCheckSum(buf));
    }

    // storedSum เท่ากับ unsignedSum ที่คำนวณจริง -> true
    @Test
    public void testVerifyCheckSum_storedSumEqualsUnsignedSum_returnsTrue() throws Throwable {
        int len = TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN + 4;
        byte[] header = new byte[len];
        for (int i = 0; i < len; i++) { header[i] = (byte) 'A'; }
        long nonChecksumCount = len - TarConstants.CHKSUMLEN;
        long sum = nonChecksumCount * 0x41L + TarConstants.CHKSUMLEN * 0x20L;
        TarUtils.formatCheckSumOctalBytes(sum, header, TarConstants.CHKSUM_OFFSET, TarConstants.CHKSUMLEN);
        assertTrue(TarUtils.verifyCheckSum(header));
    }

    // storedSum มากกว่า unsignedSum (COMPRESS-177 heuristic) -> ยังถือว่า true
    @Test
    public void testVerifyCheckSum_storedSumGreaterThanUnsignedSum_returnsTrueCompress177() throws Throwable {
        int len = TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN + 4;
        byte[] header = new byte[len];
        for (int i = 0; i < len; i++) { header[i] = (byte) 'A'; }
        long nonChecksumCount = len - TarConstants.CHKSUMLEN;
        long sum = nonChecksumCount * 0x41L + TarConstants.CHKSUMLEN * 0x20L;
        TarUtils.formatCheckSumOctalBytes(sum + 100, header, TarConstants.CHKSUM_OFFSET, TarConstants.CHKSUMLEN);
        assertTrue(TarUtils.verifyCheckSum(header));
    }

    // storedSum น้อยกว่า unsignedSum และไม่เท่า signedSum -> false
    @Test
    public void testVerifyCheckSum_storedSumLessThanUnsignedSum_returnsFalse() throws Throwable {
        int len = TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN + 4;
        byte[] header = new byte[len];
        for (int i = 0; i < len; i++) { header[i] = (byte) 'A'; }
        long nonChecksumCount = len - TarConstants.CHKSUMLEN;
        long sum = nonChecksumCount * 0x41L + TarConstants.CHKSUMLEN * 0x20L;
        TarUtils.formatCheckSumOctalBytes(sum - 50, header, TarConstants.CHKSUM_OFFSET, TarConstants.CHKSUMLEN);
        assertFalse(TarUtils.verifyCheckSum(header));
    }
}
