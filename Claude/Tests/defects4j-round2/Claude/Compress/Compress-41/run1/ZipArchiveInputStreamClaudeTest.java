package org.apache.commons.compress.archivers.zip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import org.apache.commons.compress.archivers.ArchiveEntry;

import org.junit.Test;
import static org.junit.Assert.*;

public class ZipArchiveInputStreamClaudeTest {

    // ---------- byte-building helpers (little endian ZIP structures) ----------

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, long value) {
        out.write((int) (value & 0xFF));
        out.write((int) ((value >> 8) & 0xFF));
        out.write((int) ((value >> 16) & 0xFF));
        out.write((int) ((value >> 24) & 0xFF));
    }

    private static byte[] deflate(byte[] content) {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        def.setInput(content);
        def.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        while (!def.finished()) {
            int n = def.deflate(buf);
            bos.write(buf, 0, n);
        }
        def.end();
        return bos.toByteArray();
    }

    private static byte[] buildStoredEntryNoDD(String name, byte[] content) throws IOException {
        byte[] nameBytes = name.getBytes("UTF-8");
        CRC32 crc = new CRC32();
        crc.update(content);
        long crcValue = crc.getValue();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, 0x04034b50L);
        writeShort(out, 20);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, crcValue);
        writeInt(out, content.length);
        writeInt(out, content.length);
        writeShort(out, nameBytes.length);
        writeShort(out, 0);
        out.write(nameBytes, 0, nameBytes.length);
        out.write(content, 0, content.length);
        int cdStart = out.size();
        writeInt(out, 0x02014b50L);
        writeShort(out, 20);
        writeShort(out, 20);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, crcValue);
        writeInt(out, content.length);
        writeInt(out, content.length);
        writeShort(out, nameBytes.length);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, 0);
        out.write(nameBytes, 0, nameBytes.length);
        int cdSize = out.size() - cdStart;
        writeInt(out, 0x06054b50L);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 1);
        writeShort(out, 1);
        writeInt(out, cdSize);
        writeInt(out, cdStart);
        writeShort(out, 0);
        return out.toByteArray();
    }

    private static byte[] buildDeflatedEntryNoDD(String name, byte[] content) throws IOException {
        byte[] nameBytes = name.getBytes("UTF-8");
        byte[] compressed = deflate(content);
        CRC32 crc = new CRC32();
        crc.update(content);
        long crcValue = crc.getValue();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, 0x04034b50L);
        writeShort(out, 20);
        writeShort(out, 0);
        writeShort(out, 8); // DEFLATED
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, crcValue);
        writeInt(out, compressed.length);
        writeInt(out, content.length);
        writeShort(out, nameBytes.length);
        writeShort(out, 0);
        out.write(nameBytes, 0, nameBytes.length);
        out.write(compressed, 0, compressed.length);
        int cdStart = out.size();
        writeInt(out, 0x02014b50L);
        writeShort(out, 20);
        writeShort(out, 20);
        writeShort(out, 0);
        writeShort(out, 8);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, crcValue);
        writeInt(out, compressed.length);
        writeInt(out, content.length);
        writeShort(out, nameBytes.length);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, 0);
        out.write(nameBytes, 0, nameBytes.length);
        int cdSize = out.size() - cdStart;
        writeInt(out, 0x06054b50L);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 1);
        writeShort(out, 1);
        writeInt(out, cdSize);
        writeInt(out, cdStart);
        writeShort(out, 0);
        return out.toByteArray();
    }

    private static byte[] buildStoredEntryWithDDNoSig(String name, byte[] content) throws IOException {
        byte[] nameBytes = name.getBytes("UTF-8");
        CRC32 crc = new CRC32();
        crc.update(content);
        long crcValue = crc.getValue();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, 0x04034b50L);
        writeShort(out, 20);
        writeShort(out, 0x0008); // data descriptor bit
        writeShort(out, 0); // STORED
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, 0);
        writeInt(out, 0);
        writeShort(out, nameBytes.length);
        writeShort(out, 0);
        out.write(nameBytes, 0, nameBytes.length);
        out.write(content, 0, content.length);
        // data descriptor WITHOUT signature
        writeInt(out, crcValue);
        writeInt(out, content.length);
        writeInt(out, content.length);
        int cdStart = out.size();
        writeInt(out, 0x02014b50L);
        writeShort(out, 20);
        writeShort(out, 20);
        writeShort(out, 0x0008);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, crcValue);
        writeInt(out, content.length);
        writeInt(out, content.length);
        writeShort(out, nameBytes.length);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, 0);
        out.write(nameBytes, 0, nameBytes.length);
        int cdSize = out.size() - cdStart;
        writeInt(out, 0x06054b50L);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 1);
        writeShort(out, 1);
        writeInt(out, cdSize);
        writeInt(out, cdStart);
        writeShort(out, 0);
        return out.toByteArray();
    }

    private byte[] readAll(ZipArchiveInputStream zis) throws IOException {
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = zis.read(buf, 0, buf.length)) != -1) {
            collected.write(buf, 0, n);
        }
        return collected.toByteArray();
    }

    // ---------- tests ----------

    // covers: closed==false, hitCentralDirectory==false, firstEntry EOFException branch -> returns null
    @Test
    public void testGetNextEntry_emptyStream_returnsNull() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        ArchiveEntry e = zis.getNextEntry();
        assertNull(e);
    }

    // covers: closed==true -> getNextZipEntry returns null immediately
    @Test
    public void testGetNextZipEntry_afterClose_returnsNull() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        zis.close();
        assertNull(zis.getNextZipEntry());
    }

    // covers: LFH_SIG matched, non-DD stored entry, header field parsing
    @Test
    public void testGetNextZipEntry_validStoredEntryNoDataDescriptor_returnsCorrectMetadata() throws Throwable {
        byte[] content = "Hello World!".getBytes("UTF-8");
        byte[] archive = buildStoredEntryNoDD("test.txt", content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            assertNotNull(e);
            assertEquals("test.txt", e.getName());
            assertEquals(ZipArchiveOutputStream.STORED, e.getMethod());
            assertEquals(content.length, e.getSize());
        } finally {
            zis.close();
        }
    }

    // covers: readStored() normal branch, exact data round trip and crc field
    @Test
    public void testRead_storedEntryNoDataDescriptor_returnsExactContent() throws Throwable {
        byte[] content = "Hello World!".getBytes("UTF-8");
        byte[] archive = buildStoredEntryNoDD("test.txt", content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            byte[] result = readAll(zis);
            assertArrayEquals(content, result);
            assertEquals(new CRC32ValueOf(content), e.getCrc());
        } finally {
            zis.close();
        }
    }

    // small helper class replaced below by direct CRC32 usage; kept out to avoid API guesswork
    private static long crc32Of(byte[] data) {
        CRC32 c = new CRC32();
        c.update(data);
        return c.getValue();
    }

    // covers: hitCentralDirectory path reached naturally after last entry, skipRemainderOfArchive success
    @Test
    public void testGetNextZipEntry_iterateToEnd_returnsNullAfterLastEntry() throws Throwable {
        byte[] content = "abc".getBytes("UTF-8");
        byte[] archive = buildStoredEntryNoDD("a.txt", content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            ZipArchiveEntry e1 = zis.getNextZipEntry();
            assertNotNull(e1);
            readAll(zis);
            ZipArchiveEntry e2 = zis.getNextZipEntry();
            assertNull(e2);
        } finally {
            zis.close();
        }
    }

    // covers: readDeflated/readFromInflater normal decompression path
    @Test
    public void testGetNextZipEntry_deflatedEntry_readsCorrectContent() throws Throwable {
        byte[] content = "The quick brown fox jumps over the lazy dog".getBytes("UTF-8");
        byte[] archive = buildDeflatedEntryNoDD("fox.txt", content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            assertEquals(ZipArchiveOutputStream.DEFLATED, e.getMethod());
            byte[] result = readAll(zis);
            assertArrayEquals(content, result);
            assertEquals(crc32Of(content), e.getCrc());
        } finally {
            zis.close();
        }
    }

    // covers: readFirstLocalFileHeader DD_SIG branch -> UnsupportedZipFeatureException(SPLITTING)
    @Test
    public void testGetNextZipEntry_startsWithDataDescriptorSignature_throwsUnsupportedZipFeatureException() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, 0x08074b50L); // DD_SIG
        byte[] padding = new byte[26];
        out.write(padding, 0, padding.length);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
        try {
            zis.getNextZipEntry();
            fail("expected UnsupportedZipFeatureException");
        } catch (UnsupportedZipFeatureException expected) {
            // expected
        } finally {
            zis.close();
        }
    }

    // covers: read() closed==true branch
    @Test
    public void testRead_closedStream_throwsIOException() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        zis.close();
        try {
            zis.read(new byte[10], 0, 1);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // covers: read() current==null branch -> returns -1
    @Test
    public void testRead_currentNull_returnsMinusOne() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertEquals(-1, zis.read(new byte[10], 0, 1));
        } finally {
            zis.close();
        }
    }

    // covers: read() offset<0 bound check
    @Test
    public void testRead_negativeOffset_throwsArrayIndexOutOfBoundsException() throws Throwable {
        byte[] archive = buildStoredEntryNoDD("a.txt", "abc".getBytes("UTF-8"));
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            zis.getNextZipEntry();
            try {
                zis.read(new byte[10], -1, 1);
                fail("expected ArrayIndexOutOfBoundsException");
            } catch (ArrayIndexOutOfBoundsException expected) {
                // expected
            }
        } finally {
            zis.close();
        }
    }

    // covers: read() length<0 bound check
    @Test
    public void testRead_negativeLength_throwsArrayIndexOutOfBoundsException() throws Throwable {
        byte[] archive = buildStoredEntryNoDD("a.txt", "abc".getBytes("UTF-8"));
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            zis.getNextZipEntry();
            try {
                zis.read(new byte[10], 0, -1);
                fail("expected ArrayIndexOutOfBoundsException");
            } catch (ArrayIndexOutOfBoundsException expected) {
                // expected
            }
        } finally {
            zis.close();
        }
    }

    // covers: read() offset>buffer.length bound check
    @Test
    public void testRead_offsetGreaterThanBufferLength_throwsArrayIndexOutOfBoundsException() throws Throwable {
        byte[] archive = buildStoredEntryNoDD("a.txt", "abc".getBytes("UTF-8"));
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            zis.getNextZipEntry();
            try {
                zis.read(new byte[10], 11, 0);
                fail("expected ArrayIndexOutOfBoundsException");
            } catch (ArrayIndexOutOfBoundsException expected) {
                // expected
            }
        } finally {
            zis.close();
        }
    }

    // covers: supportsDataDescriptorFor() returns false for STORED+DD when not allowed by default
    @Test
    public void testRead_storedEntryWithDataDescriptorDisallowed_throwsUnsupportedZipFeatureException() throws Throwable {
        byte[] nameBytes = "b".getBytes("UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, 0x04034b50L);
        writeShort(out, 20);
        writeShort(out, 0x0008);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, 0);
        writeInt(out, 0);
        writeShort(out, nameBytes.length);
        writeShort(out, 0);
        out.write(nameBytes, 0, nameBytes.length);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
        try {
            zis.getNextZipEntry();
            try {
                zis.read(new byte[10], 0, 1);
                fail("expected UnsupportedZipFeatureException");
            } catch (UnsupportedZipFeatureException expected) {
                // expected
            }
        } finally {
            zis.close();
        }
    }

    // covers: canReadEntryData() not-instanceof branch -> false
    @Test
    public void testCanReadEntryData_nullEntry_returnsFalse() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertFalse(zis.canReadEntryData((ArchiveEntry) null));
        } finally {
            zis.close();
        }
    }

    // covers: canReadEntryData() positive path for a plain supported entry
    @Test
    public void testCanReadEntryData_normalStoredEntry_returnsTrue() throws Throwable {
        byte[] archive = buildStoredEntryNoDD("a.txt", "abc".getBytes("UTF-8"));
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            assertTrue(zis.canReadEntryData(e));
        } finally {
            zis.close();
        }
    }

    // covers: close() idempotency (closed flag guard)
    @Test
    public void testClose_calledTwice_doesNotThrow() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        zis.close();
        zis.close();
        assertTrue(true);
    }

    // covers: skip() IllegalArgumentException branch
    @Test
    public void testSkip_negativeValue_throwsIllegalArgumentException() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            try {
                zis.skip(-1L);
                fail("expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        } finally {
            zis.close();
        }
    }

    // covers: skip() zero-iteration loop -> returns 0
    @Test
    public void testSkip_zeroValue_returnsZero() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertEquals(0L, zis.skip(0L));
        } finally {
            zis.close();
        }
    }

    // covers: skip() within an open entry correctly advances read position (readStored buffering)
    @Test
    public void testSkip_withinEntryData_advancesPositionCorrectly() throws Throwable {
        byte[] content = "HelloWorld".getBytes("UTF-8");
        byte[] archive = buildStoredEntryNoDD("c.txt", content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            zis.getNextZipEntry();
            long skipped = zis.skip(5L);
            assertEquals(5L, skipped);
            byte[] rest = readAll(zis);
            assertArrayEquals("World".getBytes("UTF-8"), rest);
        } finally {
            zis.close();
        }
    }

    // covers: matches() length too short branch -> false
    @Test
    public void testMatches_lengthTooShort_returnsFalse() throws Throwable {
        byte[] sig = new byte[] {0x50, 0x4B, 0x03, 0x04};
        assertFalse(ZipArchiveInputStream.matches(sig, 3));
    }

    // covers: matches() LFH signature match branch -> true
    @Test
    public void testMatches_localFileHeaderSignature_returnsTrue() throws Throwable {
        byte[] sig = new byte[] {0x50, 0x4B, 0x03, 0x04};
        assertTrue(ZipArchiveInputStream.matches(sig, 4));
    }

    // covers: matches() EOCD signature match branch (empty zip) -> true
    @Test
    public void testMatches_endOfCentralDirectorySignature_returnsTrue() throws Throwable {
        byte[] sig = new byte[] {0x50, 0x4B, 0x05, 0x06};
        assertTrue(ZipArchiveInputStream.matches(sig, 4));
    }

    // covers: matches() no signature matches -> false
    @Test
    public void testMatches_nonMatchingBytes_returnsFalse() throws Throwable {
        byte[] sig = new byte[] {0x00, 0x00, 0x00, 0x00};
        assertFalse(ZipArchiveInputStream.matches(sig, 4));
    }

    // covers: constructor stores explicit encoding directly into package-private field
    @Test
    public void testConstructor_explicitEncoding_fieldStoresGivenEncoding() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]), "UTF-8", true, true);
        try {
            assertEquals("UTF-8", zis.encoding);
        } finally {
            zis.close();
        }
    }

    // covers: constructor stores null encoding (platform default) into field
    @Test
    public void testConstructor_nullEncoding_fieldIsNull() throws Throwable {
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(new byte[0]), null);
        try {
            assertNull(zis.encoding);
        } finally {
            zis.close();
        }
    }

    // covers: read() final else branch -> unsupported compression method
    @Test
    public void testRead_unsupportedCompressionMethod_throwsUnsupportedZipFeatureException() throws Throwable {
        byte[] nameBytes = "d".getBytes("UTF-8");
        byte[] data = new byte[] {1, 2, 3};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, 0x04034b50L);
        writeShort(out, 20);
        writeShort(out, 0);
        writeShort(out, 99); // unsupported method
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, data.length);
        writeInt(out, data.length);
        writeShort(out, nameBytes.length);
        writeShort(out, 0);
        out.write(nameBytes, 0, nameBytes.length);
        out.write(data, 0, data.length);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
        try {
            zis.getNextZipEntry();
            try {
                zis.read(new byte[10], 0, 3);
                fail("expected UnsupportedZipFeatureException");
            } catch (UnsupportedZipFeatureException expected) {
                // expected
            }
        } finally {
            zis.close();
        }
    }

    // bug-hunting: STORED entry with data descriptor (no DD signature) immediately followed by the
    // central directory header must still be fully readable; bufferContainsSignature() must correctly
    // recognize the central file header signature to find the entry boundary.
    @Test
    public void testGetNextZipEntry_storedEntryWithDataDescriptorNoSignatureBeforeCentralDirectory_readsFullDataAndCorrectSize() throws Throwable {
        byte[] content = "Hello".getBytes("UTF-8");
        byte[] archive = buildStoredEntryWithDDNoSig("a.txt", content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive), "UTF-8", true, true);
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            assertNotNull(e);
            byte[] result = readAll(zis);
            assertArrayEquals(content, result);
            assertEquals(content.length, e.getSize());
        } finally {
            zis.close();
        }
    }

    // covers: single-arg constructor default UTF-8 encoding functional behavior
    @Test
    public void testSingleArgConstructor_readsEntryNameCorrectly() throws Throwable {
        byte[] archive = buildStoredEntryNoDD("hello.txt", "x".getBytes("UTF-8"));
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            ZipArchiveEntry e = zis.getNextZipEntry();
            assertEquals("hello.txt", e.getName());
        } finally {
            zis.close();
        }
    }

    // covers: read() with length==0 returns 0 without consuming data
    @Test
    public void testRead_zeroLength_returnsZero() throws Throwable {
        byte[] content = "abc".getBytes("UTF-8");
        byte[] archive = buildStoredEntryNoDD("z.txt", content);
        ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            zis.getNextZipEntry();
            assertEquals(0, zis.read(new byte[10], 0, 0));
        } finally {
            zis.close();
        }
    }

    // tiny holder kept out of API usage: only used to compute a deterministic crc constant inline
    private static final class CRC32ValueOf {
        private final long value;
        CRC32ValueOf(byte[] data) {
            CRC32 c = new CRC32();
            c.update(data);
            value = c.getValue();
        }
    }
}
