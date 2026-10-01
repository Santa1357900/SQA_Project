package org.apache.commons.compress.archivers.zip;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Deflater;

import org.junit.Test;

import org.apache.commons.compress.archivers.ArchiveEntry;

public class ZipArchiveInputStreamClaudeTest {

    private static class LenCapturingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        int maxLen = 0;
        LenCapturingInputStream(byte[] data) {
            delegate = new ByteArrayInputStream(data);
        }
        public int read() throws IOException {
            return delegate.read();
        }
        public int read(byte[] b, int off, int len) throws IOException {
            if (len > maxLen) {
                maxLen = len;
            }
            return delegate.read(b, off, len);
        }
    }

    private static void putShort(byte[] b, int off, int value) {
        b[off] = (byte) (value & 0xFF);
        b[off + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void putLong(byte[] b, int off, long value) {
        b[off] = (byte) (value & 0xFF);
        b[off + 1] = (byte) ((value >>> 8) & 0xFF);
        b[off + 2] = (byte) ((value >>> 16) & 0xFF);
        b[off + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private static byte[] buildLocalEntry(int method, int gpFlag, long crcVal,
            long csize, long size, byte[] nameBytes, byte[] extraBytes, byte[] content) {
        int headerLen = 30 + nameBytes.length + extraBytes.length;
        byte[] header = new byte[headerLen];
        header[0] = 0x50; header[1] = 0x4B; header[2] = 0x03; header[3] = 0x04;
        putShort(header, 4, 20);
        putShort(header, 6, gpFlag);
        putShort(header, 8, method);
        putLong(header, 10, 0L);
        putLong(header, 14, crcVal);
        putLong(header, 18, csize);
        putLong(header, 22, size);
        putShort(header, 26, nameBytes.length);
        putShort(header, 28, extraBytes.length);
        System.arraycopy(nameBytes, 0, header, 30, nameBytes.length);
        System.arraycopy(extraBytes, 0, header, 30 + nameBytes.length, extraBytes.length);
        byte[] result = new byte[header.length + content.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(content, 0, result, header.length, content.length);
        return result;
    }

    private static byte[] buildDataDescriptor() {
        byte[] dd = new byte[16];
        dd[0] = 0x50; dd[1] = 0x4B; dd[2] = 0x07; dd[3] = 0x08;
        return dd;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static byte[] deflate(byte[] data) {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        def.setInput(data);
        def.finish();
        byte[] outBuf = new byte[data.length + 100];
        int n = def.deflate(outBuf);
        def.end();
        byte[] result = new byte[n];
        System.arraycopy(outBuf, 0, result, 0, n);
        return result;
    }

    private static byte[] readAll(ZipArchiveInputStream zis) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] chunk = new byte[64];
        int n;
        while ((n = zis.read(chunk, 0, chunk.length)) != -1) {
            bos.write(chunk, 0, n);
        }
        return bos.toByteArray();
    }

    // constructor เริ่มต้น (UTF8) อ่าน entry STORED และคืน name/method/size/content ถูกต้อง
    @Test
    public void testConstructor_defaultEncoding_readsStoredEntryNameAndContent() throws Throwable {
        byte[] content = "Hello World!".getBytes("UTF-8");
        byte[] nameBytes = "test.txt".getBytes("UTF-8");
        byte[] zipBytes = buildLocalEntry(ZipArchiveOutputStream.STORED, 0, 0L,
                content.length, content.length, nameBytes, new byte[0], content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes));
        try {
            ZipArchiveEntry entry = zis.getNextZipEntry();
            assertEquals("test.txt", entry.getName());
            assertEquals(ZipArchiveOutputStream.STORED, entry.getMethod());
            assertEquals((long) content.length, entry.getSize());
            assertArrayEquals(content, readAll(zis));
        } finally {
            zis.close();
        }
    }

    // constructor กับ encoding ที่กำหนดเอง (ไม่มี EFS flag) ต้องใช้ zipEncoding ที่ส่งเข้ามาถอดชื่อไฟล์
    @Test
    public void testConstructor_customEncodingNoEfs_decodesNameWithGivenEncoding() throws Throwable {
        byte[] nameBytes = "caf\u00E9".getBytes("ISO-8859-1");
        byte[] zipBytes = buildLocalEntry(ZipArchiveOutputStream.STORED, 0, 0L, 0, 0,
                nameBytes, new byte[0], new byte[0]);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(
                new ByteArrayInputStream(zipBytes), "ISO-8859-1", false);
        try {
            ZipArchiveEntry entry = zis.getNextZipEntry();
            assertEquals("caf\u00E9", entry.getName());
        } finally {
            zis.close();
        }
    }

    // สตรีมว่างเปล่า ต้องคืน null ทันที (EOFException ภายใน readFully ถูกจับ)
    @Test
    public void testGetNextZipEntry_emptyStream_returnsNull() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertNull(zis.getNextZipEntry());
        } finally {
            zis.close();
        }
    }

    // header ถูกตัดสั้น (น้อยกว่า 30 ไบต์) ต้องคืน null
    @Test
    public void testGetNextZipEntry_truncatedHeader_returnsNull() throws Throwable {
        byte[] partial = new byte[] {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0};
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(partial));
        try {
            assertNull(zis.getNextZipEntry());
        } finally {
            zis.close();
        }
    }

    // signature ตรงกับ Central File Header -> hitCentralDirectory=true คืน null และคงอยู่ในการเรียกถัดไป
    @Test
    public void testGetNextZipEntry_centralDirectorySignature_returnsNullAndStaysNull() throws Throwable {
        byte[] cfh = new byte[30];
        cfh[0] = 0x50; cfh[1] = 0x4B; cfh[2] = 0x01; cfh[3] = 0x02;
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(cfh));
        try {
            assertNull(zis.getNextZipEntry());
            assertNull(zis.getNextZipEntry());
        } finally {
            zis.close();
        }
    }

    // signature ไม่ตรงทั้ง LFH และ CFH -> คืน null
    @Test
    public void testGetNextZipEntry_unknownSignature_returnsNull() throws Throwable {
        byte[] unknown = new byte[30];
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(unknown));
        try {
            assertNull(zis.getNextZipEntry());
        } finally {
            zis.close();
        }
    }

    // สอง entry ติดกัน อ่าน entry แรกไม่ครบแล้วเรียก getNextZipEntry (closeEntry ต้อง skip ส่วนที่เหลือให้ถูกต้อง)
    @Test
    public void testGetNextZipEntry_multipleEntriesWithPartialRead_readsBothEntriesCorrectly() throws Throwable {
        byte[] c1 = "ABCDE".getBytes("UTF-8");
        byte[] c2 = "FGHIJ".getBytes("UTF-8");
        byte[] e1 = buildLocalEntry(ZipArchiveOutputStream.STORED, 0, 0L, c1.length, c1.length,
                "e1".getBytes("UTF-8"), new byte[0], c1);
        byte[] e2 = buildLocalEntry(ZipArchiveOutputStream.STORED, 0, 0L, c2.length, c2.length,
                "e2".getBytes("UTF-8"), new byte[0], c2);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(concat(e1, e2)));
        try {
            assertEquals("e1", zis.getNextZipEntry().getName());
            byte[] partial = new byte[2];
            assertEquals(2, zis.read(partial, 0, 2));
            assertEquals("e2", zis.getNextZipEntry().getName());
            assertArrayEquals(c2, readAll(zis));
        } finally {
            zis.close();
        }
    }

    // หลัง close() แล้ว getNextZipEntry ต้องคืน null ทันที
    @Test
    public void testGetNextZipEntry_afterClose_returnsNull() throws Throwable {
        byte[] zipBytes = buildLocalEntry(ZipArchiveOutputStream.STORED, 0, 0L, 0, 0,
                "x".getBytes("UTF-8"), new byte[0], new byte[0]);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes));
        zis.close();
        assertNull(zis.getNextZipEntry());
    }

    // getNextEntry() ต้องเป็นการ delegate ไปยัง getNextZipEntry และคืน ZipArchiveEntry ที่ถูกต้อง
    @Test
    public void testGetNextEntry_returnsSameEntryAsGetNextZipEntry() throws Throwable {
        byte[] content = "Data".getBytes("UTF-8");
        byte[] zipBytes = buildLocalEntry(ZipArchiveOutputStream.STORED, 0, 0L,
                content.length, content.length, "n".getBytes("UTF-8"), new byte[0], content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes));
        try {
            ArchiveEntry entry = zis.getNextEntry();
            assertTrue(entry instanceof ZipArchiveEntry);
            assertEquals("n", entry.getName());
        } finally {
            zis.close();
        }
    }

    // ยังไม่มี entry ปัจจุบัน (current==null) -> read ต้องคืน -1
    @Test
    public void testRead_noCurrentEntry_returnsMinusOne() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertEquals(-1, zis.read(new byte[10], 0, 10));
        } finally {
            zis.close();
        }
    }

    // สตรีมถูกปิดแล้ว read ต้องโยน IOException ที่มีคำว่า closed
    @Test
    public void testRead_closedStreamWithoutEntry_throwsIOException() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        zis.close();
        try {
            zis.read(new byte[10], 0, 10);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // start เป็นค่าลบ ทำให้เงื่อนไขตรวจสอบ parameter เป็นเท็จ -> ArrayIndexOutOfBoundsException
    @Test
    public void testRead_negativeStart_throwsArrayIndexOutOfBoundsException() throws Throwable {
        byte[] content = "ABCDE".getBytes("UTF-8");
        byte[] zipBytes = buildLocalEntry(ZipArchiveOutputStream.STORED, 0, 0L,
                content.length, content.length, "n".getBytes("UTF-8"), new byte[0], content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes));
        try {
            zis.getNextZipEntry();
            try {
                zis.read(new byte[10], -1, 1);
                fail("expected ArrayIndexOutOfBoundsException");
            } catch (ArrayIndexOutOfBoundsException expected) { }
        } finally {
            zis.close();
        }
    }

    // length เกิน buffer.length - start -> ArrayIndexOutOfBoundsException
    @Test
    public void testRead_lengthExceedsBuffer_throwsArrayIndexOutOfBoundsException() throws Throwable {
        byte[] content = "ABCDE".getBytes("UTF-8");
        byte[] zipBytes = buildLocalEntry(ZipArchiveOutputStream.STORED, 0, 0L,
                content.length, content.length, "n".getBytes("UTF-8"), new byte[0], content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes));
        try {
            zis.getNextZipEntry();
            try {
                zis.read(new byte[5], 0, 6);
                fail("expected ArrayIndexOutOfBoundsException");
            } catch (ArrayIndexOutOfBoundsException expected) { }
        } finally {
            zis.close();
        }
    }

    // STORED entry เล็ก อ่านครบจำนวนไบต์แล้วครั้งถัดไปต้องคืน -1 (readBytesOfEntry>=csize)
    @Test
    public void testRead_storedEntrySmallContent_returnsExactBytesThenMinusOne() throws Throwable {
        byte[] content = "ABCDE".getBytes("UTF-8");
        byte[] zipBytes = buildLocalEntry(ZipArchiveOutputStream.STORED, 0, 0L,
                content.length, content.length, "n".getBytes("UTF-8"), new byte[0], content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes));
        try {
            zis.getNextZipEntry();
            byte[] out = new byte[5];
            assertEquals(5, zis.read(out, 0, 5));
            assertArrayEquals(content, out);
            assertEquals(-1, zis.read(out, 0, 5));
        } finally {
            zis.close();
        }
    }



    // DEFLATED entry ต้องคืนข้อมูลที่ถอดรหัสแล้วตรงกับต้นฉบับทุกไบต์
    @Test
    public void testRead_deflatedEntry_returnsDecompressedBytes() throws Throwable {
        byte[] original = "The quick brown fox jumps over the lazy dog. Repeat Repeat Repeat.".getBytes("UTF-8");
        byte[] compressed = deflate(original);
        byte[] zipBytes = buildLocalEntry(ZipArchiveOutputStream.DEFLATED, 0, 0L,
                compressed.length, original.length, "d".getBytes("UTF-8"), new byte[0], compressed);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes));
        try {
            ZipArchiveEntry entry = zis.getNextZipEntry();
            assertEquals(ZipArchiveOutputStream.DEFLATED, entry.getMethod());
            assertArrayEquals(original, readAll(zis));
        } finally {
            zis.close();
        }
    }

    // general purpose bit 3 (data descriptor) ตั้งค่า -> ต้องข้ามขนาดใน header และอ่าน descriptor 16 ไบต์ตอน close
    @Test
    public void testGetNextZipEntry_dataDescriptorFlag_readsDeflatedEntryAndSkipsDescriptor() throws Throwable {
        byte[] original = "Streaming data with descriptor".getBytes("UTF-8");
        byte[] compressed = deflate(original);
        byte[] entryBytes = buildLocalEntry(ZipArchiveOutputStream.DEFLATED, 8, 0L, 0, 0,
                "s".getBytes("UTF-8"), new byte[0], compressed);
        byte[] zipBytes = concat(entryBytes, buildDataDescriptor());
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes));
        try {
            assertNotNull(zis.getNextZipEntry());
            assertArrayEquals(original, readAll(zis));
            assertNull(zis.getNextZipEntry());
        } finally {
            zis.close();
        }
    }

    // ตั้ง EFS flag -> ต้องถอดชื่อไฟล์ด้วย UTF-8 เสมอ แม้ constructor จะระบุ encoding อื่นไว้
    @Test
    public void testGetNextZipEntry_efsFlagSet_usesUtf8RegardlessOfConstructorEncoding() throws Throwable {
        byte[] nameBytes = "caf\u00E9".getBytes("UTF-8");
        byte[] zipBytes = buildLocalEntry(ZipArchiveOutputStream.STORED,
                ZipArchiveOutputStream.EFS_FLAG, 0L, 0, 0, nameBytes, new byte[0], new byte[0]);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(
                new ByteArrayInputStream(zipBytes), "ISO-8859-1", false);
        try {
            ZipArchiveEntry entry = zis.getNextZipEntry();
            assertEquals("caf\u00E9", entry.getName());
        } finally {
            zis.close();
        }
    }

    // skip() ค่าบวก ต้องข้ามไปจำนวนที่ขอและ read ต่อจากตำแหน่งนั้นถูกต้อง
    @Test
    public void testSkip_positiveValue_skipsRequestedBytes() throws Throwable {
        byte[] content = "0123456789".getBytes("UTF-8");
        byte[] zipBytes = buildLocalEntry(ZipArchiveOutputStream.STORED, 0, 0L,
                content.length, content.length, "n".getBytes("UTF-8"), new byte[0], content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes));
        try {
            zis.getNextZipEntry();
            assertEquals(3, zis.skip(3));
            assertArrayEquals("3456789".getBytes("UTF-8"), readAll(zis));
        } finally {
            zis.close();
        }
    }

    // skip(0) ลูป while ไม่ทำงานเลย ต้องคืน 0 ทันทีโดยไม่เรียก read()
    @Test
    public void testSkip_zeroValue_returnsZeroImmediately() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertEquals(0, zis.skip(0));
        } finally {
            zis.close();
        }
    }

    // skip ค่าลบ ต้องโยน IllegalArgumentException
    @Test
    public void testSkip_negativeValue_throwsIllegalArgumentException() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            try {
                zis.skip(-1);
                fail("expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) { }
        } finally {
            zis.close();
        }
    }

    // close() เรียกซ้ำสองครั้งต้องไม่โยน exception (idempotent ผ่าน flag closed)
    @Test
    public void testClose_calledTwice_doesNotThrow() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        zis.close();
        zis.close();
        assertNull(zis.getNextZipEntry());
    }

    // เปิด entry อ่านไปบางส่วนแล้วปิดสตรีม จากนั้น read อีกครั้งต้องโยน IOException
    @Test
    public void testRead_afterCloseWithOpenEntry_throwsIOException() throws Throwable {
        byte[] content = "AB".getBytes("UTF-8");
        byte[] zipBytes = buildLocalEntry(ZipArchiveOutputStream.STORED, 0, 0L,
                content.length, content.length, "n".getBytes("UTF-8"), new byte[0], content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes));
        zis.getNextZipEntry();
        zis.read(new byte[1], 0, 1);
        zis.close();
        try {
            zis.read(new byte[1], 0, 1);
            fail("expected IOException");
        } catch (IOException expected) { }
    }

    // matches() กับ signature ของ Local File Header ต้องคืน true
    @Test
    public void testMatches_localFileHeaderSignature_returnsTrue() throws Throwable {
        byte[] sig = ZipArchiveOutputStream.LFH_SIG;
        assertTrue(ZipArchiveInputStream.matches(sig, sig.length));
    }

    // matches() กับ signature ของ End Of Central Directory ต้องคืน true
    @Test
    public void testMatches_endOfCentralDirectorySignature_returnsTrue() throws Throwable {
        byte[] sig = ZipArchiveOutputStream.EOCD_SIG;
        assertTrue(ZipArchiveInputStream.matches(sig, sig.length));
    }

    // length สั้นกว่าความยาวของ signature ต้องคืน false ทันที
    @Test
    public void testMatches_lengthShorterThanSignature_returnsFalse() throws Throwable {
        byte[] sig = ZipArchiveOutputStream.LFH_SIG;
        assertFalse(ZipArchiveInputStream.matches(sig, sig.length - 1));
    }

    // bytes ที่ไม่ตรงกับ signature ใด ๆ ต้องคืน false
    @Test
    public void testMatches_nonMatchingBytes_returnsFalse() throws Throwable {
        byte[] sig = ZipArchiveOutputStream.LFH_SIG;
        byte[] other = new byte[sig.length];
        assertFalse(ZipArchiveInputStream.matches(other, other.length));
    }
}
