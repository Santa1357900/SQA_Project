package org.apache.commons.codec.binary;

import java.io.ByteArrayInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class BaseNCodecInputStreamClaudeTest {

    private byte[] toBytes(String s) throws Throwable {
        return s.getBytes("UTF-8");
    }

    private String toStr(byte[] b, int len) throws Throwable {
        return new String(b, 0, len, "UTF-8");
    }

    // constructor: สร้าง instance สำเร็จ, markSupported คืน false
    @Test
    public void testConstructor_validArguments_createsStreamNotNull() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(new byte[0]), new Base64(), true);
        assertNotNull(in);
        assertFalse(in.markSupported());
    }

    // read(): decode ของ stream ว่าง ต้องคืน EOF ทันที
    @Test
    public void testReadSingleByte_emptyDecodeStream_returnsEOF() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(new byte[0]), new Base64(), false);
        assertEquals(-1, in.read());
    }

    // read(): encode ของ stream ว่าง ต้องคืน EOF ทันที
    @Test
    public void testReadSingleByte_emptyEncodeStream_returnsEOF() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(new byte[0]), new Base64(), true);
        assertEquals(-1, in.read());
    }

    // read(): decode ทีละไบต์ตามลำดับถูกต้อง แล้ว EOF
    @Test
    public void testReadSingleByte_decodeKnownString_returnsCorrectBytesSequentially() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("SGk=")), new Base64(), false);
        int b1 = in.read();
        int b2 = in.read();
        assertEquals('H', b1);
        assertEquals('i', b2);
        assertEquals(-1, in.read());
    }

    // read(): encode ทีละไบต์ตามลำดับถูกต้อง
    @Test
    public void testReadSingleByte_encodeKnownBytes_returnsCorrectCharsSequentially() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hi")), new Base64(), true);
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            sb.append((char) c);
        }
        assertEquals("SGk=", sb.toString());
    }

    // read(): byte ที่มี high bit (0xFF) ต้องแปลงเป็น unsigned 255 ไม่ใช่ค่าติดลบ
    @Test
    public void testReadSingleByte_decodeHighBitByte_returnsUnsignedValue() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("/w==")), new Base64(), false);
        assertEquals(255, in.read());
        assertEquals(-1, in.read());
    }

    // read(byte[],off,len): b == null ต้องโยน NullPointerException
    @Test
    public void testReadByteArray_nullBuffer_throwsNullPointerException() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hi")), new Base64(), true);
        try {
            in.read(null, 0, 1);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) { }
    }

    // read(byte[],off,len): offset ติดลบ ต้องโยน IndexOutOfBoundsException
    @Test
    public void testReadByteArray_negativeOffset_throwsIndexOutOfBoundsException() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hi")), new Base64(), true);
        byte[] buf = new byte[5];
        try {
            in.read(buf, -1, 1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // read(byte[],off,len): len ติดลบ ต้องโยน IndexOutOfBoundsException
    @Test
    public void testReadByteArray_negativeLen_throwsIndexOutOfBoundsException() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hi")), new Base64(), true);
        byte[] buf = new byte[5];
        try {
            in.read(buf, 0, -1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // read(byte[],off,len): offset > b.length ต้องโยน IndexOutOfBoundsException
    @Test
    public void testReadByteArray_offsetGreaterThanLength_throwsIndexOutOfBoundsException() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hi")), new Base64(), true);
        byte[] buf = new byte[3];
        try {
            in.read(buf, 5, 0);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // read(byte[],off,len): offset+len > b.length ต้องโยน IndexOutOfBoundsException
    @Test
    public void testReadByteArray_offsetPlusLenGreaterThanLength_throwsIndexOutOfBoundsException() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hi")), new Base64(), true);
        byte[] buf = new byte[5];
        try {
            in.read(buf, 3, 3);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) { }
    }

    // read(byte[],off,len): len == 0 ต้องคืน 0 โดยไม่ throw
    @Test
    public void testReadByteArray_lenZero_returnsZeroWithoutException() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hi")), new Base64(), true);
        byte[] buf = new byte[5];
        assertEquals(0, in.read(buf, 0, 0));
    }

    // read(byte[],off,len): ขอบเขต offset == b.length กับ len == 0 ต้องคืน 0 ไม่ throw
    @Test
    public void testReadByteArray_offsetEqualsLengthLenZero_returnsZero() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hi")), new Base64(), true);
        byte[] buf = new byte[4];
        assertEquals(0, in.read(buf, 4, 0));
    }

    // read(byte[],off,len): decode ข้อมูลทั้งหมดในครั้งเดียวได้ถูกต้อง
    @Test
    public void testReadByteArray_decodeFullContent_returnsCorrectBytes() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("SGVsbG8gV29ybGQ=")), new Base64(), false);
        byte[] buf = new byte[20];
        int r = in.read(buf, 0, 20);
        assertEquals(11, r);
        assertEquals("Hello World", toStr(buf, r));
    }

    // read(byte[],off,len): encode ข้อมูลทั้งหมดในครั้งเดียวได้ถูกต้อง
    @Test
    public void testReadByteArray_encodeFullContent_returnsCorrectBytes() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hello World")), new Base64(), true);
        byte[] buf = new byte[20];
        int r = in.read(buf, 0, 20);
        assertEquals(16, r);
        assertEquals("SGVsbG8gV29ybGQ=", toStr(buf, r));
    }

    // read(byte[],off,len): อ่านซ้ำหลังหมดข้อมูลแล้วต้องคืน EOF (-1)
    @Test
    public void testReadByteArray_decodeThenReadAgain_returnsEOF() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("SGVsbG8gV29ybGQ=")), new Base64(), false);
        byte[] buf = new byte[20];
        in.read(buf, 0, 20);
        int r2 = in.read(buf, 0, 20);
        assertEquals(-1, r2);
    }

    // read(byte[],off,len): เขียนผลลัพธ์ที่ offset ที่ไม่ใช่ 0 ถูกตำแหน่ง
    @Test
    public void testReadByteArray_offsetNonZero_writesAtCorrectPosition() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("SGk=")), new Base64(), false);
        byte[] buf = new byte[6];
        int r = in.read(buf, 3, 3);
        assertEquals(2, r);
        assertEquals(0, buf[0]);
        byte[] tmp = new byte[] { buf[3], buf[4] };
        assertEquals("Hi", toStr(tmp, 2));
    }

    // read(byte[],off,len): decode จาก underlying stream ว่าง ต้องคืน -1
    @Test
    public void testReadByteArray_emptyInput_decode_returnsNegativeOne() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(new byte[0]), new Base64(), false);
        byte[] buf = new byte[10];
        assertEquals(-1, in.read(buf, 0, 10));
    }

    // read(byte[],off,len): encode จาก underlying stream ว่าง ต้องคืน -1
    @Test
    public void testReadByteArray_emptyInput_encode_returnsNegativeOne() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(new byte[0]), new Base64(), true);
        byte[] buf = new byte[10];
        assertEquals(-1, in.read(buf, 0, 10));
    }

    // read(byte[],off,len): อ่านหลายครั้งด้วยบัฟเฟอร์เล็กต้องรวมได้ข้อมูลครบถ้วน
    @Test
    public void testReadByteArray_multipleSmallReads_accumulateFullContent() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("SGVsbG8gV29ybGQ=")), new Base64(), false);
        byte[] buf = new byte[4];
        int r1 = in.read(buf, 0, 4);
        String p1 = toStr(buf, r1);
        int r2 = in.read(buf, 0, 4);
        String p2 = toStr(buf, r2);
        int r3 = in.read(buf, 0, 4);
        String p3 = toStr(buf, r3);
        assertEquals(11, r1 + r2 + r3);
        assertEquals("Hello World", p1 + p2 + p3);
    }

    // markSupported(): decode stream ต้องคืน false เสมอ
    @Test
    public void testMarkSupported_decodeStream_returnsFalse() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hi")), new Base64(), false);
        assertFalse(in.markSupported());
    }

    // markSupported(): encode stream ต้องคืน false เสมอ
    @Test
    public void testMarkSupported_encodeStream_returnsFalse() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hi")), new Base64(), true);
        assertFalse(in.markSupported());
    }

    // skip(): ค่าติดลบต้องโยน IllegalArgumentException ตาม javadoc
    @Test
    public void testSkip_negativeLength_throwsIllegalArgumentException() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hello World")), new Base64(), true);
        try {
            in.skip(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // skip(): ขอ skip 0 ต้องคืน 0 โดยไม่ throw
    @Test
    public void testSkip_zeroLength_returnsZero() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hello World")), new Base64(), true);
        assertEquals(0L, in.skip(0));
    }

    // skip(): skip จำนวนที่ถูกต้อง แล้วอ่านข้อมูลที่เหลือได้ตรงกับที่คาดไว้
    @Test
    public void testSkip_positiveLength_skipsAndRemainingReadMatches() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hello World")), new Base64(), true);
        long skipped = in.skip(5);
        assertEquals(5L, skipped);
        byte[] buf = new byte[20];
        int r = in.read(buf, 0, 20);
        assertEquals("G8gV29ybGQ=", toStr(buf, r));
    }

    // skip(): ขอ skip มากกว่าข้อมูลที่มี ต้อง skip ได้เท่าที่มีจริงแล้วถึง EOF
    @Test
    public void testSkip_lengthGreaterThanAvailableData_returnsActualSkippedAmount() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("Hi")), new Base64(), true);
        long skipped = in.skip(100);
        assertEquals(4L, skipped);
        assertEquals(-1, in.read());
    }

    // available(): ก่อนถึง EOF ต้องคืน 1 ตาม javadoc
    @Test
    public void testAvailable_beforeEOF_returnsOne() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("SGVsbG8gV29ybGQ=")), new Base64(), false);
        assertEquals(1, in.available());
    }

    // available(): หลังอ่านข้อมูลจนหมดและถึง EOF แล้วต้องคืน 0 ตาม javadoc
    @Test
    public void testAvailable_afterFullyRead_returnsZero() throws Throwable {
        BaseNCodecInputStream in = new BaseNCodecInputStream(new ByteArrayInputStream(toBytes("SGVsbG8gV29ybGQ=")), new Base64(), false);
        byte[] buf = new byte[20];
        in.read(buf, 0, 20);
        assertEquals(-1, in.read());
        assertEquals(0, in.available());
    }
}
