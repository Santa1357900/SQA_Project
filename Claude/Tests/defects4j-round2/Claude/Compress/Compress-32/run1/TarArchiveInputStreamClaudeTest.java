package org.apache.commons.compress.archivers.tar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.compress.archivers.zip.ZipEncodingHelper;

public class TarArchiveInputStreamClaudeTest {

    private static final int RECORD = 512;

    // ---------- constructors ----------

    // ครอบคลุม constructor(InputStream) ใช้ record size เริ่มต้น
    @Test
    public void testConstructor_singleArg_defaultRecordSize() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertEquals(512, in.getRecordSize());
        } finally {
            in.close();
        }
    }

    // ครอบคลุม constructor(InputStream,String) และเก็บ encoding ไว้ใน field
    @Test
    public void testConstructor_withEncoding_storesEncoding() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), "UTF-8");
        try {
            assertEquals(512, in.getRecordSize());
            assertEquals("UTF-8", in.encoding);
        } finally {
            in.close();
        }
    }

    // ครอบคลุม constructor(InputStream,int blockSize) ใช้ record size เริ่มต้น
    @Test
    public void testConstructor_withBlockSize_defaultRecordSize() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 1024);
        try {
            assertEquals(512, in.getRecordSize());
        } finally {
            in.close();
        }
    }

    // ครอบคลุม constructor(InputStream,int,String)
    @Test
    public void testConstructor_withBlockSizeAndEncoding() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 1024, "ASCII");
        try {
            assertEquals(512, in.getRecordSize());
            assertEquals("ASCII", in.encoding);
        } finally {
            in.close();
        }
    }

    // ครอบคลุม constructor(InputStream,int,int) ใช้ record size กำหนดเอง และ encoding เป็น null
    @Test
    public void testConstructor_withBlockSizeAndRecordSize() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 1024, 256);
        try {
            assertEquals(256, in.getRecordSize());
            assertNull(in.encoding);
        } finally {
            in.close();
        }
    }

    // ครอบคลุม constructor(InputStream,int,int,String) เต็มรูปแบบ
    @Test
    public void testConstructor_fullArgs_storesEncodingAndRecordSize() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 1024, 256, "UTF-8");
        try {
            assertEquals(256, in.getRecordSize());
            assertEquals("UTF-8", in.encoding);
        } finally {
            in.close();
        }
    }

    // ---------- markSupported/mark/reset ----------

    // markSupported ต้องคืน false เสมอ
    @Test
    public void testMarkSupported_alwaysFalse() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            assertFalse(in.markSupported());
        } finally {
            in.close();
        }
    }

    // mark เป็น no-op ไม่เปลี่ยนสถานะ markSupported
    @Test
    public void testMark_isNoOp() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            in.mark(100);
            assertFalse(in.markSupported());
        } finally {
            in.close();
        }
    }

    // reset เป็น no-op ไม่เปลี่ยน currentEntry ที่ตั้งไว้
    @Test
    public void testReset_isNoOp_currentEntryUnchanged() throws Throwable {
        byte[] header = buildHeader("m.txt", 0, '0');
        TarArchiveInputStream in = newTarIn(header);
        try {
            TarArchiveEntry e = in.getNextTarEntry();
            in.reset();
            assertSame(e, in.getCurrentEntry());
        } finally {
            in.close();
        }
    }

    // ---------- available ----------

    // available ก่อนอ่าน entry ใดๆ ต้องเป็น 0 (entrySize=0, entryOffset=0)
    @Test
    public void testAvailable_initialState_returnsZero() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            assertEquals(0, in.available());
        } finally {
            in.close();
        }
    }

    // available หลังอ่านบางส่วนของ entry ต้องลดลงตามจำนวนที่อ่านไป
    @Test
    public void testAvailable_afterPartialRead() throws Throwable {
        byte[] header = buildHeader("p.txt", 10, '0');
        byte[] data = "HelloWorld".getBytes("UTF-8");
        byte[] archive = concat(header, data, zeros(RECORD - 10), eofRecord(), eofRecord());
        TarArchiveInputStream in = newTarIn(archive);
        try {
            in.getNextTarEntry();
            in.read(new byte[4], 0, 4);
            assertEquals(6, in.available());
        } finally {
            in.close();
        }
    }

    // available ต้องคืน Integer.MAX_VALUE เมื่อขนาดคงเหลือเกิน Integer.MAX_VALUE
    @Test
    public void testAvailable_sizeExceedsIntMax_returnsIntMaxValue() throws Throwable {
        byte[] header = buildHeader("big.bin", 3000000000L, '0');
        TarArchiveInputStream in = newTarIn(header);
        try {
            TarArchiveEntry e = in.getNextTarEntry();
            assertEquals(3000000000L, e.getSize());
            assertEquals(Integer.MAX_VALUE, in.available());
        } finally {
            in.close();
        }
    }

    // ---------- skip ----------

    // skip กับ n<=0 ต้องคืน 0 เสมอ
    @Test
    public void testSkip_negativeOrZero_returnsZero() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            assertEquals(0L, in.skip(-5));
            assertEquals(0L, in.skip(0));
        } finally {
            in.close();
        }
    }

    // skip ภายในขอบเขตของ entry ต้องเลื่อน entryOffset ตามจำนวนที่ skip ได้จริง
    @Test
    public void testSkip_withinEntry_advancesOffset() throws Throwable {
        byte[] header = buildHeader("sk.txt", 10, '0');
        byte[] data = "HelloWorld".getBytes("UTF-8");
        byte[] archive = concat(header, data, zeros(RECORD - 10), eofRecord(), eofRecord());
        TarArchiveInputStream in = newTarIn(archive);
        try {
            in.getNextTarEntry();
            assertEquals(4L, in.skip(4));
            assertEquals(6, in.available());
        } finally {
            in.close();
        }
    }

    // skip มากกว่าที่เหลืออยู่ ต้องถูกจำกัดไว้ที่ available เท่านั้น
    @Test
    public void testSkip_moreThanAvailable_limitedToAvailable() throws Throwable {
        byte[] header = buildHeader("sk2.txt", 10, '0');
        byte[] data = "HelloWorld".getBytes("UTF-8");
        byte[] archive = concat(header, data, zeros(RECORD - 10), eofRecord(), eofRecord());
        TarArchiveInputStream in = newTarIn(archive);
        try {
            in.getNextTarEntry();
            assertEquals(10L, in.skip(100));
            assertEquals(0, in.available());
        } finally {
            in.close();
        }
    }

    // ---------- getNextTarEntry / getNextEntry ----------

    // สตรีมว่างเปล่าต้องคืน null ทันที (ยังไม่ถึง EOF record จริง เพราะ read ไม่ครบ record)
    @Test
    public void testGetNextTarEntry_emptyStream_returnsNull() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            assertNull(in.getNextTarEntry());
        } finally {
            in.close();
        }
    }

    // entry เดี่ยวต้องถูก parse ขนาดไฟล์ได้ถูกต้องจาก header
    @Test
    public void testGetNextTarEntry_singleEntry_parsesSize() throws Throwable {
        byte[] header = buildHeader("single.txt", 7, '0');
        byte[] data = "1234567".getBytes("UTF-8");
        byte[] archive = concat(header, data, zeros(RECORD - 7), eofRecord(), eofRecord());
        TarArchiveInputStream in = newTarIn(archive);
        try {
            TarArchiveEntry e = in.getNextTarEntry();
            assertEquals(7L, e.getSize());
        } finally {
            in.close();
        }
    }

    // หลาย entry ต่อกัน ต้องข้าม padding ของ record ได้ถูกต้องทั้งกรณีมี/ไม่มี padding และคืน null เมื่อถึง EOF
    @Test
    public void testGetNextTarEntry_multipleEntries_skipsPaddingAndHitsEOF() throws Throwable {
        byte[] header1 = buildHeader("a.txt", 5, '0');
        byte[] data1 = "Hello".getBytes("UTF-8");
        byte[] header2 = buildHeader("b.txt", 512, '0');
        byte[] data2 = new byte[512];
        Arrays.fill(data2, (byte) 'B');
        byte[] archive = concat(header1, data1, zeros(RECORD - 5), header2, data2, eofRecord(), eofRecord());
        TarArchiveInputStream in = newTarIn(archive);
        try {
            TarArchiveEntry e1 = in.getNextTarEntry();
            assertEquals(5L, e1.getSize());
            TarArchiveEntry e2 = in.getNextTarEntry();
            assertEquals(512L, e2.getSize());
            assertNull(in.getNextTarEntry());
            assertNull(in.getNextTarEntry());
        } finally {
            in.close();
        }
    }

    // getNextEntry() ต้อง delegate ไปยัง getNextTarEntry() อย่างถูกต้อง
    @Test
    public void testGetNextEntry_delegatesToGetNextTarEntry() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            assertNull(in.getNextEntry());
        } finally {
            in.close();
        }
    }

    // ---------- read ----------

    // อ่านข้อมูลครบตามขนาด entry แล้วอ่านต่อต้องได้ -1 (ถึงขอบเขต entry)
    @Test
    public void testRead_fullEntryThenMinusOneAtEntryEOF() throws Throwable {
        byte[] header = buildHeader("r.txt", 5, '0');
        byte[] data = "World".getBytes("UTF-8");
        byte[] archive = concat(header, data, zeros(RECORD - 5), eofRecord(), eofRecord());
        TarArchiveInputStream in = newTarIn(archive);
        try {
            in.getNextTarEntry();
            byte[] buf = new byte[5];
            int n = in.read(buf, 0, 5);
            assertEquals(5, n);
            assertEquals("World", new String(buf, "UTF-8"));
            assertEquals(-1, in.read(buf, 0, 5));
        } finally {
            in.close();
        }
    }

    // ถ้าข้อมูลจริงสั้นกว่าที่ header ประกาศไว้ ต้อง throw IOException ("Truncated TAR archive")
    @Test
    public void testRead_truncatedArchive_throwsIOException() throws Throwable {
        byte[] header = buildHeader("t.txt", 10, '0');
        TarArchiveInputStream in = newTarIn(header);
        try {
            in.getNextTarEntry();
            try {
                in.read(new byte[10], 0, 10);
                fail("expected IOException for truncated archive");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("Truncated"));
            }
        } finally {
            in.close();
        }
    }

    // ตามสัญญาเมธอด read ต้อง throw IllegalStateException เมื่อยังไม่มี current entry
    // (ล่าบั๊ก: ลำดับการตรวจ entryOffset>=entrySize ถูกเช็คก่อน currEntry==null ทำให้คืน -1 แทนที่จะ throw)
    @Test
    public void testRead_noCurrentEntry_mustThrowIllegalStateException() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            try {
                in.read(new byte[1], 0, 1);
                fail("expected IllegalStateException when no current tar entry is set");
            } catch (IllegalStateException expected) {
                // ok
            }
        } finally {
            in.close();
        }
    }

    // ---------- canReadEntryData ----------

    // entry ที่ไม่ใช่ TarArchiveEntry (รวมถึง null) ต้องคืน false
    @Test
    public void testCanReadEntryData_nonTarEntry_returnsFalse() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            assertFalse(in.canReadEntryData(null));
        } finally {
            in.close();
        }
    }

    // entry ปกติ (ไม่ใช่ GNU sparse) ต้องคืน true
    @Test
    public void testCanReadEntryData_regularEntry_returnsTrue() throws Throwable {
        byte[] header = buildHeader("c.txt", 0, '0');
        TarArchiveInputStream in = newTarIn(header);
        try {
            TarArchiveEntry e = in.getNextTarEntry();
            assertTrue(in.canReadEntryData(e));
        } finally {
            in.close();
        }
    }

    // ---------- getCurrentEntry / setCurrentEntry / isAtEOF / setAtEOF ----------

    // setCurrentEntry/getCurrentEntry ต้อง round-trip ได้ และค่าเริ่มต้นต้องเป็น null
    @Test
    public void testGetSetCurrentEntry_roundTrip() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            assertNull(in.getCurrentEntry());
            TarArchiveEntry e = new TarArchiveEntry(buildHeader("x.txt", 0, '0'),
                    ZipEncodingHelper.getZipEncoding(null));
            in.setCurrentEntry(e);
            assertSame(e, in.getCurrentEntry());
        } finally {
            in.close();
        }
    }

    // isAtEOF/setAtEOF ต้อง round-trip ได้ และค่าเริ่มต้นต้องเป็น false
    @Test
    public void testIsAtEOF_setAtEOF_roundTrip() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            assertFalse(in.isAtEOF());
            in.setAtEOF(true);
            assertTrue(in.isAtEOF());
        } finally {
            in.close();
        }
    }

    // ---------- close ----------

    // close() ต้อง delegate ไปปิด stream ต้นทางจริง
    @Test
    public void testClose_delegatesToUnderlyingStream() throws Throwable {
        FlagCloseInputStream flagIn = new FlagCloseInputStream(new byte[0]);
        TarArchiveInputStream in = new TarArchiveInputStream(flagIn);
        in.close();
        assertTrue(flagIn.isClosed());
    }

    // ---------- readRecord / isEOFRecord ----------

    // readRecord ต้องคืน null เมื่ออ่านได้ไม่ครบขนาด record
    @Test
    public void testReadRecord_shortStream_returnsNull() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[10]);
        try {
            assertNull(in.readRecord());
        } finally {
            in.close();
        }
    }

    // readRecord ต้องคืน record เต็มขนาดเมื่อข้อมูลพอ
    @Test
    public void testReadRecord_fullRecord_returnsBytes() throws Throwable {
        byte[] data = new byte[RECORD];
        data[0] = (byte) 'A';
        TarArchiveInputStream in = newTarIn(data);
        try {
            byte[] rec = in.readRecord();
            assertEquals(RECORD, rec.length);
            assertEquals((byte) 'A', rec[0]);
        } finally {
            in.close();
        }
    }

    // isEOFRecord(null) ต้องเป็น true
    @Test
    public void testIsEOFRecord_nullRecord_returnsTrue() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            assertTrue(in.isEOFRecord(null));
        } finally {
            in.close();
        }
    }

    // isEOFRecord ของ record ที่เป็นศูนย์ทั้งหมดต้องเป็น true
    @Test
    public void testIsEOFRecord_allZeroRecord_returnsTrue() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            assertTrue(in.isEOFRecord(new byte[RECORD]));
        } finally {
            in.close();
        }
    }

    // isEOFRecord ของ record ที่มี byte ไม่เป็นศูนย์ต้องเป็น false
    @Test
    public void testIsEOFRecord_nonZeroRecord_returnsFalse() throws Throwable {
        byte[] rec = new byte[RECORD];
        rec[0] = 1;
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            assertFalse(in.isEOFRecord(rec));
        } finally {
            in.close();
        }
    }

    // ---------- parsePaxHeaders ----------

    // input ว่างเปล่าต้องคืน map ว่าง (ลูป 0 รอบ)
    @Test
    public void testParsePaxHeaders_emptyInput_returnsEmptyMap() throws Throwable {
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            Map<String, String> headers = in.parsePaxHeaders(new ByteArrayInputStream(new byte[0]));
            assertTrue(headers.isEmpty());
        } finally {
            in.close();
        }
    }

    // header เดียวที่ value ว่างเปล่าต้อง parse ได้ถูกต้อง
    @Test
    public void testParsePaxHeaders_singleEntry_emptyValue() throws Throwable {
        byte[] content = "5 a=\n".getBytes("UTF-8");
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            Map<String, String> headers = in.parsePaxHeaders(new ByteArrayInputStream(content));
            assertEquals("", headers.get("a"));
        } finally {
            in.close();
        }
    }

    // header เดียวที่มี value ต้อง parse keyword/value ได้ถูกต้องและตัด newline ท้ายออก
    @Test
    public void testParsePaxHeaders_singleEntry_withValue() throws Throwable {
        byte[] content = "8 ab=cd\n".getBytes("UTF-8");
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            Map<String, String> headers = in.parsePaxHeaders(new ByteArrayInputStream(content));
            assertEquals("cd", headers.get("ab"));
        } finally {
            in.close();
        }
    }

    // ความยาวที่ระบุเกินกว่าข้อมูลจริงที่มี ต้อง throw IOException
    @Test
    public void testParsePaxHeaders_truncatedValue_throwsIOException() throws Throwable {
        byte[] content = "9 a=\n".getBytes("UTF-8");
        TarArchiveInputStream in = newTarIn(new byte[0]);
        try {
            try {
                in.parsePaxHeaders(new ByteArrayInputStream(content));
                fail("expected IOException for truncated pax header value");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("Paxheader"));
            }
        } finally {
            in.close();
        }
    }

    // ---------- matches (static) ----------

    // length สั้นกว่า threshold ที่ต้องมี version field ต้องคืน false เสมอ
    @Test
    public void testMatches_shortSignature_returnsFalse() throws Throwable {
        assertFalse(TarArchiveInputStream.matches(new byte[0], 0));
    }

    // ================= helpers (not tests) =================

    private static TarArchiveInputStream newTarIn(byte[] data) {
        return new TarArchiveInputStream(new ByteArrayInputStream(data), RECORD, RECORD);
    }

    private static byte[] zeros(int n) {
        return new byte[n];
    }

    private static byte[] eofRecord() {
        return new byte[RECORD];
    }

    private static byte[] concat(byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < parts.length; i++) {
            out.write(parts[i]);
        }
        return out.toByteArray();
    }

    private static void writeOctal(byte[] buf, int offset, int length, long value) {
        String oct = Long.toOctalString(value);
        int numDigits = length - 1;
        StringBuilder sb = new StringBuilder();
        int padCount = numDigits - oct.length();
        for (int i = 0; i < padCount; i++) {
            sb.append('0');
        }
        sb.append(oct);
        String s = sb.toString();
        for (int i = 0; i < numDigits; i++) {
            buf[offset + i] = (byte) s.charAt(i);
        }
        buf[offset + length - 1] = 0;
    }

    private static byte[] buildHeader(String name, long size, char typeFlag) throws IOException {
        byte[] buf = new byte[RECORD];
        byte[] nameBytes = name.getBytes("UTF-8");
        int nameLen = nameBytes.length > 100 ? 100 : nameBytes.length;
        System.arraycopy(nameBytes, 0, buf, 0, nameLen);
        writeOctal(buf, 100, 8, 420L);
        writeOctal(buf, 108, 8, 0L);
        writeOctal(buf, 116, 8, 0L);
        writeOctal(buf, 124, 12, size);
        writeOctal(buf, 136, 12, 0L);
        for (int i = 148; i < 156; i++) {
            buf[i] = (byte) ' ';
        }
        buf[156] = (byte) typeFlag;
        byte[] magic = "ustar".getBytes("UTF-8");
        System.arraycopy(magic, 0, buf, 257, magic.length);
        buf[262] = 0;
        buf[263] = (byte) '0';
        buf[264] = (byte) '0';
        long sum = 0;
        for (int i = 0; i < RECORD; i++) {
            sum += (buf[i] & 0xff);
        }
        writeOctal(buf, 148, 8, sum);
        return buf;
    }

    private static class FlagCloseInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private boolean closed;

        FlagCloseInputStream(byte[] data) {
            delegate = new ByteArrayInputStream(data);
        }

        public int read() throws IOException {
            return delegate.read();
        }

        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        public void close() throws IOException {
            closed = true;
            delegate.close();
        }

        boolean isClosed() {
            return closed;
        }
    }
}
