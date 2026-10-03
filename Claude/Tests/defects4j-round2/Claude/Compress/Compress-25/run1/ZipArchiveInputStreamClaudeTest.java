package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;

public class ZipArchiveInputStreamClaudeTest {

    // คุม constructor 1 อาร์กิวเมนต์ (encoding เริ่มต้น UTF8) + getNextZipEntry อ่าน entry ได้ถูกต้อง
    @Test
    public void testConstructor_singleArg_readsStoredEntryWithUtf8Default() throws Throwable {
        byte[] archive = buildStoredZip("hello.txt", new byte[] {1, 2, 3});
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            assertEquals("hello.txt", e.getName());
        } finally {
            zis.close();
        }
    }

    // คุม branch EOFException ถูกจับใน getNextZipEntry เมื่อ stream ว่างเปล่า -> คืน null
    @Test
    public void testGetNextZipEntry_emptyStream_returnsNull() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertNull(zis.getNextZipEntry());
        } finally {
            zis.close();
        }
    }

    // คุม metadata ของ entry แบบ STORED: ชื่อ, method, size
    @Test
    public void testGetNextZipEntry_storedEntry_returnsCorrectMetadata() throws Throwable {
        byte[] content = new byte[] {9, 9, 9};
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("meta.txt", content)));
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            assertEquals("meta.txt", e.getName());
            assertEquals(ZipArchiveOutputStream.STORED, e.getMethod());
            assertEquals(content.length, e.getSize());
        } finally {
            zis.close();
        }
    }

    // คุม Javadoc: DEFLATE อาจคืน getSize() == -1 ก่อนอ่านข้อมูลจริง เพราะไม่มีข้อมูลขนาดในส่วนหัว
    @Test
    public void testGetNextZipEntry_deflatedEntry_sizeUnknownBeforeRead() throws Throwable {
        byte[] content = "some deflated content here".getBytes("UTF-8");
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildDeflatedZip("d.txt", content)));
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            assertEquals(ZipArchiveOutputStream.DEFLATED, e.getMethod());
            assertEquals(-1, e.getSize());
        } finally {
            zis.close();
        }
    }

    // คุม การอ่านหลาย entry ตามลำดับ และคืน null เมื่อพบ central directory
    @Test
    public void testGetNextZipEntry_multipleEntries_readsSequentiallyThenNull() throws Throwable {
        byte[] archive = buildTwoStoredEntriesZip("a.txt", new byte[] {1, 2}, "b.txt", new byte[] {3, 4, 5});
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            ZipArchiveEntry e1 = zis.getNextZipEntry();
            assertEquals("a.txt", e1.getName());
            ZipArchiveEntry e2 = zis.getNextZipEntry();
            assertEquals("b.txt", e2.getName());
            assertNull(zis.getNextZipEntry());
        } finally {
            zis.close();
        }
    }

    // คุม branch "closed" ใน getNextZipEntry -> คืน null โดยไม่ throw
    @Test
    public void testGetNextZipEntry_afterClosed_returnsNullWithoutThrow() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("x.txt", new byte[] {1})));
        zis.close();
        assertNull(zis.getNextZipEntry());
    }

    // คุม getNextEntry() (override ของ ArchiveInputStream) ว่า delegate ไปยัง getNextZipEntry อย่างถูกต้อง
    @Test
    public void testGetNextEntry_delegatesAndReturnsZipArchiveEntry() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("e.txt", new byte[] {7})));
        try {
            ArchiveEntry ae = zis.getNextEntry();
            assertTrue(ae instanceof ZipArchiveEntry);
            assertEquals("e.txt", ((ZipArchiveEntry) ae).getName());
        } finally {
            zis.close();
        }
    }

    // คุม canReadEntryData กับ entry STORED ปกติ -> true
    @Test
    public void testCanReadEntryData_storedEntry_returnsTrue() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("ok.txt", new byte[] {1})));
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            assertTrue(zis.canReadEntryData(e));
        } finally {
            zis.close();
        }
    }

    // คุม branch "ae instanceof ZipArchiveEntry" เป็น false เมื่อส่ง null -> คืน false โดยไม่ throw NPE
    @Test
    public void testCanReadEntryData_nullArchiveEntry_returnsFalse() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertFalse(zis.canReadEntryData(null));
        } finally {
            zis.close();
        }
    }

    // คุม canReadEntryData กับ compression method ที่ยังไม่ถูก implement -> false ตาม Javadoc
    @Test
    public void testCanReadEntryData_unsupportedMethod_returnsFalse() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("u.txt", new byte[] {1})));
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            e.setMethod(99);
            assertFalse(zis.canReadEntryData(e));
        } finally {
            zis.close();
        }
    }

    // คุม read() เมื่อ stream ถูก close แล้ว -> ต้อง throw IOException
    @Test
    public void testRead_afterClose_throwsIOException() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("c.txt", new byte[] {1, 2, 3})));
        zis.close();
        try {
            zis.read(new byte[4], 0, 4);
            fail("expected IOException");
        } catch (IOException expected) {
            // ok
        }
    }

    // คุม read() เมื่อ current == null (ยังไม่เปิด entry ใดเลย) -> คืน -1
    @Test
    public void testRead_noCurrentEntry_returnsMinusOne() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertEquals(-1, zis.read(new byte[4], 0, 4));
        } finally {
            zis.close();
        }
    }

    // คุม bound check: offset > buffer.length -> ArrayIndexOutOfBoundsException
    @Test
    public void testRead_offsetGreaterThanBufferLength_throwsAIOOBE() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("b.txt", new byte[] {1, 2, 3, 4})));
        try {
            zis.getNextZipEntry();
            try {
                zis.read(new byte[4], 5, 1);
                fail("expected ArrayIndexOutOfBoundsException");
            } catch (ArrayIndexOutOfBoundsException expected) {
                // ok
            }
        } finally {
            zis.close();
        }
    }

    // คุม bound check: length < 0 -> ArrayIndexOutOfBoundsException
    @Test
    public void testRead_negativeLength_throwsAIOOBE() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("b2.txt", new byte[] {1, 2, 3, 4})));
        try {
            zis.getNextZipEntry();
            try {
                zis.read(new byte[4], 0, -1);
                fail("expected ArrayIndexOutOfBoundsException");
            } catch (ArrayIndexOutOfBoundsException expected) {
                // ok
            }
        } finally {
            zis.close();
        }
    }

    // คุม bound check: offset < 0 -> ArrayIndexOutOfBoundsException
    @Test
    public void testRead_negativeOffset_throwsAIOOBE() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("b3.txt", new byte[] {1, 2, 3, 4})));
        try {
            zis.getNextZipEntry();
            try {
                zis.read(new byte[4], -1, 1);
                fail("expected ArrayIndexOutOfBoundsException");
            } catch (ArrayIndexOutOfBoundsException expected) {
                // ok
            }
        } finally {
            zis.close();
        }
    }

    // คุม bound check: buffer.length - offset < length -> ArrayIndexOutOfBoundsException
    @Test
    public void testRead_lengthExceedsAvailableBuffer_throwsAIOOBE() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("b4.txt", new byte[] {1, 2, 3, 4})));
        try {
            zis.getNextZipEntry();
            try {
                zis.read(new byte[4], 2, 3);
                fail("expected ArrayIndexOutOfBoundsException");
            } catch (ArrayIndexOutOfBoundsException expected) {
                // ok
            }
        } finally {
            zis.close();
        }
    }

    // คุม readStored (ไม่มี data descriptor): เนื้อหาที่อ่านได้ต้องตรงกับต้นฉบับทุกไบต์
    @Test
    public void testRead_storedEntry_contentMatchesOriginalBytes() throws Throwable {
        byte[] content = new byte[] {10, 20, 30, 40, 50};
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("f.bin", content)));
        try {
            assertNotNull(zis.getNextZipEntry());
            byte[] result = readAll(zis);
            assertArrayEquals(content, result);
        } finally {
            zis.close();
        }
    }

    // คุม readDeflated: เนื้อหาที่ decompress ได้ต้องตรงกับต้นฉบับทุกไบต์
    @Test
    public void testRead_deflatedEntry_contentMatchesOriginalBytes() throws Throwable {
        byte[] content = "The quick brown fox jumps over the lazy dog".getBytes("UTF-8");
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildDeflatedZip("g.txt", content)));
        try {
            assertNotNull(zis.getNextZipEntry());
            byte[] result = readAll(zis);
            assertArrayEquals(content, result);
        } finally {
            zis.close();
        }
    }

    // บั๊ก: readStoredEntry/bufferContainsSignature ต้องหาขอบเขต data descriptor (ไม่มี signature)
    // ที่ตามด้วย Central File Header ของ entry สุดท้ายได้ถูกต้อง ไม่ throw "Truncated ZIP file"
    @Test
    public void testRead_storedEntryWithDataDescriptor_cfhBoundaryDetected() throws Throwable {
        byte[] archive = buildStoredEntryWithDdAndCfhBoundary();
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive), "UTF8", true, true);
        try {
            ZipArchiveEntry entry = zis.getNextZipEntry();
            assertEquals("a.txt", entry.getName());
            byte[] buffer = new byte[5];
            int read = zis.read(buffer, 0, 5);
            assertEquals(5, read);
            assertArrayEquals(new byte[] {'H', 'E', 'L', 'L', 'O'}, buffer);
        } finally {
            zis.close();
        }
    }

    // คุม close() เรียกซ้ำสองครั้งไม่ throw และสถานะ closed ยังคงสอดคล้องกัน (read ยัง throw ตามปกติ)
    @Test
    public void testClose_calledTwice_remainsClosedConsistently() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        zis.close();
        zis.close();
        try {
            zis.read(new byte[1], 0, 1);
            fail("expected IOException");
        } catch (IOException expected) {
            // ok
        }
    }

    // คุม close() ก่อนอ่าน entry ใดๆ ไม่ throw และ getNextZipEntry หลัง close คืน null
    @Test
    public void testClose_beforeReadingAnyEntry_doesNotThrow() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("z.txt", new byte[] {1})));
        zis.close();
        assertNull(zis.getNextZipEntry());
    }

    // คุม skip(value) เมื่อ value เป็นลบ -> IllegalArgumentException
    @Test
    public void testSkip_negativeValue_throwsIllegalArgumentException() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            try {
                zis.skip(-1L);
                fail("expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // ok
            }
        } finally {
            zis.close();
        }
    }

    // คุม skip() ภายในขนาดของ entry -> ข้ามได้ตามจำนวนที่ขอพอดี และข้อมูลที่เหลือถูกต้อง
    @Test
    public void testSkip_withinEntry_skipsExactRequestedBytes() throws Throwable {
        byte[] content = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("sk.bin", content)));
        try {
            zis.getNextZipEntry();
            long skipped = zis.skip(3);
            assertEquals(3, skipped);
            byte[] rest = readAll(zis);
            assertArrayEquals(new byte[] {4, 5, 6, 7, 8}, rest);
        } finally {
            zis.close();
        }
    }

    // คุม skip() เมื่อขอข้ามเกินขนาดจริงของ entry -> คืนจำนวนที่ข้ามได้จริงเท่านั้น (น้อยกว่าที่ขอ)
    @Test
    public void testSkip_beyondEntryEnd_returnsBytesActuallyAvailable() throws Throwable {
        byte[] content = new byte[] {1, 2, 3};
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildStoredZip("sk2.bin", content)));
        try {
            zis.getNextZipEntry();
            long skipped = zis.skip(100L);
            assertEquals(3, skipped);
        } finally {
            zis.close();
        }
    }

    // คุม matches(): length สั้นกว่า signature ที่ต้องการ -> false
    @Test
    public void testMatches_lengthShorterThanSignature_returnsFalse() throws Throwable {
        byte[] sig = new byte[] {0x50, 0x4B, 0x03, 0x04};
        assertFalse(ZipArchiveInputStream.matches(sig, ZipArchiveOutputStream.LFH_SIG.length - 1));
    }

    // คุม matches(): signature ของ Local File Header -> true
    @Test
    public void testMatches_localFileHeaderSignature_returnsTrue() throws Throwable {
        byte[] sig = ZipArchiveOutputStream.LFH_SIG;
        assertTrue(ZipArchiveInputStream.matches(sig, sig.length));
    }

    // คุม matches(): signature ของ End Of Central Directory (zip ว่าง) -> true
    @Test
    public void testMatches_emptyZipEocdSignature_returnsTrue() throws Throwable {
        byte[] sig = ZipArchiveOutputStream.EOCD_SIG;
        assertTrue(ZipArchiveInputStream.matches(sig, sig.length));
    }

    // คุม matches(): signature ของ split zip data descriptor marker -> true
    @Test
    public void testMatches_splitZipMarkerSignature_returnsTrue() throws Throwable {
        byte[] sig = ZipArchiveOutputStream.DD_SIG;
        assertTrue(ZipArchiveInputStream.matches(sig, sig.length));
    }

    // คุม matches(): byte ที่ไม่ตรงกับ signature ใดเลย -> false
    @Test
    public void testMatches_nonMatchingBytes_returnsFalse() throws Throwable {
        byte[] sig = new byte[] {0, 0, 0, 0};
        assertFalse(ZipArchiveInputStream.matches(sig, 4));
    }

    // คุม closeEntry + readDataDescriptor สำหรับ DEFLATED ที่ใช้ data descriptor: size ถูกตั้งถูกต้องหลัง close entry
    @Test
    public void testGetNextZipEntry_deflatedWithDataDescriptor_sizeKnownAfterCloseEntry() throws Throwable {
        byte[] content = "Hello World Hello World Hello".getBytes("UTF-8");
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(buildDeflatedZip("x.txt", content)));
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            byte[] result = readAll(zis);
            assertArrayEquals(content, result);
            assertNull(zis.getNextZipEntry());
            assertEquals(content.length, e.getSize());
        } finally {
            zis.close();
        }
    }

    private byte[] readAll(ZipArchiveInputStream zis) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        int n;
        while ((n = zis.read(buf, 0, buf.length)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private void addStoredEntry(ZipOutputStream zos, String name, byte[] content) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        e.setSize(content.length);
        CRC32 crc = new CRC32();
        crc.update(content);
        e.setCrc(crc.getValue());
        zos.putNextEntry(e);
        zos.write(content);
        zos.closeEntry();
    }

    private byte[] buildStoredZip(String name, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        addStoredEntry(zos, name, content);
        zos.close();
        return baos.toByteArray();
    }

    private byte[] buildTwoStoredEntriesZip(String n1, byte[] c1, String n2, byte[] c2) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        addStoredEntry(zos, n1, c1);
        addStoredEntry(zos, n2, c2);
        zos.close();
        return baos.toByteArray();
    }

    private byte[] buildDeflatedZip(String name, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        ZipEntry e = new ZipEntry(name);
        zos.putNextEntry(e);
        zos.write(content);
        zos.closeEntry();
        zos.close();
        return baos.toByteArray();
    }

    private void writeShortLE(ByteArrayOutputStream bos, int value) {
        bos.write(value & 0xFF);
        bos.write((value >> 8) & 0xFF);
    }

    private void writeIntLE(ByteArrayOutputStream bos, int value) {
        bos.write(value & 0xFF);
        bos.write((value >> 8) & 0xFF);
        bos.write((value >> 16) & 0xFF);
        bos.write((value >> 24) & 0xFF);
    }

    private byte[] buildStoredEntryWithDdAndCfhBoundary() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        // Local File Header (30 bytes): STORED method, GPB bit3 (data descriptor) set
        writeIntLE(bos, 0x04034b50);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0x0008);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0);
        writeIntLE(bos, 0);
        writeIntLE(bos, 0);
        writeIntLE(bos, 0);
        writeShortLE(bos, 5);
        writeShortLE(bos, 0);
        bos.write(new byte[] {'a', '.', 't', 'x', 't'});
        bos.write(new byte[] {'H', 'E', 'L', 'L', 'O'});
        // Data descriptor WITHOUT its optional signature marker (3 WORDs)
        writeIntLE(bos, 0);
        writeIntLE(bos, 5);
        writeIntLE(bos, 5);
        // Central File Header (46 bytes) - this is the only/last entry in the archive
        writeIntLE(bos, 0x02014b50);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0);
        writeIntLE(bos, 0);
        writeIntLE(bos, 5);
        writeIntLE(bos, 5);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0);
        writeShortLE(bos, 0);
        writeIntLE(bos, 0);
        writeIntLE(bos, 0);
        return bos.toByteArray();
    }
}
