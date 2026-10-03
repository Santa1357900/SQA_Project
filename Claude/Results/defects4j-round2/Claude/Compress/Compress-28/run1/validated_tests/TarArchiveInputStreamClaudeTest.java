package org.apache.commons.compress.archivers.tar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipEncoding;
import org.apache.commons.compress.archivers.zip.ZipEncodingHelper;

public class TarArchiveInputStreamClaudeTest {

    // Constructor branch: default (is) -> uses DEFAULT_RCDSIZE
    @Test
    public void testConstructor_default_usesDefaultRecordSize() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertEquals(TarConstants.DEFAULT_RCDSIZE, in.getRecordSize());
        } finally {
            in.close();
        }
    }

    // Constructor branch: (is, encoding) -> uses DEFAULT_RCDSIZE
    @Test
    public void testConstructor_withEncoding_usesDefaultRecordSize() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), "UTF-8");
        try {
            assertEquals(TarConstants.DEFAULT_RCDSIZE, in.getRecordSize());
        } finally {
            in.close();
        }
    }

    // Constructor branch: (is, blockSize) -> uses DEFAULT_RCDSIZE
    @Test
    public void testConstructor_withBlockSize_usesDefaultRecordSize() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 1024);
        try {
            assertEquals(TarConstants.DEFAULT_RCDSIZE, in.getRecordSize());
        } finally {
            in.close();
        }
    }

    // Constructor branch: (is, blockSize, encoding) -> uses DEFAULT_RCDSIZE
    @Test
    public void testConstructor_withBlockSizeEncoding_usesDefaultRecordSize() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 1024, "UTF-8");
        try {
            assertEquals(TarConstants.DEFAULT_RCDSIZE, in.getRecordSize());
        } finally {
            in.close();
        }
    }

    // Constructor branch: (is, blockSize, recordSize) -> uses given recordSize
    @Test
    public void testConstructor_withBlockSizeAndRecordSize_usesGivenRecordSize() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 2048, 256);
        try {
            assertEquals(256, in.getRecordSize());
        } finally {
            in.close();
        }
    }

    // Constructor branch: (is, blockSize, recordSize, encoding) -> uses given recordSize
    @Test
    public void testConstructor_withBlockSizeRecordSizeEncoding_usesGivenRecordSize() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 2048, 256, "UTF-8");
        try {
            assertEquals(256, in.getRecordSize());
        } finally {
            in.close();
        }
    }

    // available(): normal branch returns entrySize - entryOffset before any read
    @Test
    public void testAvailable_withinEntry_returnsRemainingBytes() throws Throwable {
        byte[] header = buildHeader("a.txt", 5L, '0');
        byte[] archive = concat(header);
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            in.getNextTarEntry();
            assertEquals(5, in.available());
        } finally {
            in.close();
        }
    }

    // available(): branch where entrySize-entryOffset > Integer.MAX_VALUE returns Integer.MAX_VALUE
    @Test
    public void testAvailable_sizeExceedsIntegerMax_returnsIntegerMaxValue() throws Throwable {
        byte[] header = buildHeader("big.bin", 3000000000L, '0');
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(header));
        try {
            TarArchiveEntry e = in.getNextTarEntry();
            assertEquals(3000000000L, e.getSize());
            assertEquals(Integer.MAX_VALUE, in.available());
        } finally {
            in.close();
        }
    }

    // skip(): within entry, advances offset and returns actually skipped count
    @Test
    public void testSkip_withinEntry_advancesOffsetAndReturnsSkippedCount() throws Throwable {
        byte[] header = buildHeader("s.bin", 10L, '0');
        byte[] content = padTo("HelloWorld".getBytes("UTF-8"), TarConstants.DEFAULT_RCDSIZE);
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(concat(header, content)));
        try {
            in.getNextTarEntry();
            long skipped = in.skip(3);
            assertEquals(3L, skipped);
            assertEquals(7, in.available());
            byte[] buf = new byte[7];
            int n = in.read(buf, 0, 7);
            assertEquals(7, n);
            assertEquals("loWorld", new String(buf, "UTF-8"));
        } finally {
            in.close();
        }
    }

    // skip(): requesting more than available caps the skip at remaining entry bytes
    @Test
    public void testSkip_beyondEntrySize_capsAtAvailableBytes() throws Throwable {
        byte[] header = buildHeader("sk.bin", 4L, '0');
        byte[] content = padTo("abcd".getBytes("UTF-8"), TarConstants.DEFAULT_RCDSIZE);
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(concat(header, content)));
        try {
            in.getNextTarEntry();
            long skipped = in.skip(100L);
            assertEquals(4L, skipped);
            assertEquals(0, in.available());
        } finally {
            in.close();
        }
    }

    // reset(): documented to have no effect on current state
    @Test
    public void testReset_hasNoEffect_currentEntryUnchanged() throws Throwable {
        byte[] header = buildHeader("r.txt", 0L, '0');
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(header));
        try {
            TarArchiveEntry before = in.getNextTarEntry();
            in.reset();
            assertSame(before, in.getCurrentEntry());
        } finally {
            in.close();
        }
    }

    // getNextTarEntry(): empty archive returns null, and hasHitEOF short-circuit keeps returning null
    @Test
    public void testGetNextTarEntry_emptyArchive_returnsNullAndRemainsNullOnSecondCall() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertNull(in.getNextTarEntry());
            assertNull(in.getNextTarEntry());
        } finally {
            in.close();
        }
    }

    // getNextTarEntry(): a short/truncated record (readNow != recordSize) returns null
    @Test
    public void testGetNextTarEntry_truncatedHeader_returnsNull() throws Throwable {
        byte[] archive = new byte[100];
        Arrays.fill(archive, (byte) 'A');
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            assertNull(in.getNextTarEntry());
        } finally {
            in.close();
        }
    }

    // getNextTarEntry(): skips remaining data + padding (mod!=0 branch, mod==0 branch, zero-size branch)
    @Test
    public void testGetNextTarEntry_multipleEntries_skipsRemainingDataAndPaddingCorrectly() throws Throwable {
        byte[] header1 = buildHeader("a.txt", 5L, '0');
        byte[] content1 = padTo("Hello".getBytes("UTF-8"), TarConstants.DEFAULT_RCDSIZE);
        byte[] header2 = buildHeader("b.bin", TarConstants.DEFAULT_RCDSIZE, '0');
        byte[] content2 = new byte[TarConstants.DEFAULT_RCDSIZE];
        Arrays.fill(content2, (byte) 'B');
        byte[] header3 = buildHeader("c.empty", 0L, '0');
        byte[] eof = new byte[TarConstants.DEFAULT_RCDSIZE * 2];
        byte[] archive = concat(header1, content1, header2, content2, header3, eof);
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            TarArchiveEntry e1 = in.getNextTarEntry();
            assertEquals(5L, e1.getSize());
            TarArchiveEntry e2 = in.getNextTarEntry();
            assertEquals((long) TarConstants.DEFAULT_RCDSIZE, e2.getSize());
            TarArchiveEntry e3 = in.getNextTarEntry();
            assertEquals(0L, e3.getSize());
            assertNull(in.getNextTarEntry());
        } finally {
            in.close();
        }
    }

    // getNextTarEntry()/read(): regular entry content is read correctly and stream ends with null
    @Test
    public void testGetNextTarEntry_regularEntry_readsContentAndReturnsNullAtArchiveEnd() throws Throwable {
        byte[] header = buildHeader("test.txt", 5L, '0');
        byte[] content = padTo("Hello".getBytes("UTF-8"), TarConstants.DEFAULT_RCDSIZE);
        byte[] eof = new byte[TarConstants.DEFAULT_RCDSIZE * 2];
        byte[] archive = concat(header, content, eof);
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(archive));
        try {
            TarArchiveEntry e = in.getNextTarEntry();
            assertEquals(5L, e.getSize());
            byte[] buf = new byte[5];
            int n = in.read(buf, 0, 5);
            assertEquals(5, n);
            assertEquals("Hello", new String(buf, "UTF-8"));
            assertEquals(-1, in.read(buf, 0, 5));
            assertNull(in.getNextTarEntry());
        } finally {
            in.close();
        }
    }



    // read(): numToRead is capped by available() when requesting more than remains in entry
    @Test
    public void testRead_requestMoreThanAvailable_capsAtRemainingBytes() throws Throwable {
        byte[] header = buildHeader("cap.bin", 3L, '0');
        byte[] content = padTo("xyz".getBytes("UTF-8"), TarConstants.DEFAULT_RCDSIZE);
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(concat(header, content)));
        try {
            in.getNextTarEntry();
            byte[] buf = new byte[10];
            int n = in.read(buf, 2, 8);
            assertEquals(3, n);
            assertEquals("xyz", new String(buf, 2, 3, "UTF-8"));
        } finally {
            in.close();
        }
    }

    // read(): entryOffset >= entrySize (zero-size entry) returns -1 immediately
    @Test
    public void testRead_zeroSizeEntry_returnsMinusOne() throws Throwable {
        byte[] header = buildHeader("empty.bin", 0L, '0');
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(header));
        try {
            in.getNextTarEntry();
            byte[] buf = new byte[1];
            assertEquals(-1, in.read(buf, 0, 1));
        } finally {
            in.close();
        }
    }

    // getNextEntry(): covariant ArchiveEntry delegate returns a TarArchiveEntry with correct size
    @Test
    public void testGetNextEntry_returnsTarArchiveEntryInstance() throws Throwable {
        byte[] header = buildHeader("n.txt", 0L, '0');
        byte[] eof = new byte[TarConstants.DEFAULT_RCDSIZE * 2];
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(concat(header, eof)));
        try {
            ArchiveEntry ae = in.getNextEntry();
            assertTrue(ae instanceof TarArchiveEntry);
            assertEquals(0L, ((TarArchiveEntry) ae).getSize());
        } finally {
            in.close();
        }
    }

    // canReadEntryData(): a non-sparse TarArchiveEntry is readable -> true
    @Test
    public void testCanReadEntryData_withNonSparseTarEntry_returnsTrue() throws Throwable {
        byte[] header = buildHeader("entry.txt", 1L, '0');
        ZipEncoding enc = ZipEncodingHelper.getZipEncoding(null);
        TarArchiveEntry entry = new TarArchiveEntry(header, enc);
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertTrue(in.canReadEntryData(entry));
        } finally {
            in.close();
        }
    }

    // canReadEntryData(): null is not a TarArchiveEntry instance -> false
    @Test
    public void testCanReadEntryData_withNull_returnsFalse() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertFalse(in.canReadEntryData(null));
        } finally {
            in.close();
        }
    }

    // getCurrentEntry(): initially null before any entry has been read
    @Test
    public void testGetCurrentEntry_initiallyNull() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertNull(in.getCurrentEntry());
        } finally {
            in.close();
        }
    }

    // setCurrentEntry()/getCurrentEntry(): protected setter is reflected by the getter
    @Test
    public void testSetCurrentEntry_updatesGetCurrentEntry() throws Throwable {
        byte[] header = buildHeader("se.txt", 2L, '0');
        ZipEncoding enc = ZipEncodingHelper.getZipEncoding(null);
        TarArchiveEntry entry = new TarArchiveEntry(header, enc);
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            in.setCurrentEntry(entry);
            assertSame(entry, in.getCurrentEntry());
        } finally {
            in.close();
        }
    }

    // isAtEOF()/setAtEOF(): protected flag toggles as expected
    @Test
    public void testIsAtEOF_setAtEOF_toggleState() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            assertFalse(in.isAtEOF());
            in.setAtEOF(true);
            assertTrue(in.isAtEOF());
            in.setAtEOF(false);
            assertFalse(in.isAtEOF());
        } finally {
            in.close();
        }
    }

    // parsePaxHeaders(): "len keyword=value\n" format parses a single header correctly
    @Test
    public void testParsePaxHeaders_singleKeyValue_parsedCorrectly() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            String data = "6 a=b\n";
            Map<String, String> headers = in.parsePaxHeaders(new ByteArrayInputStream(data.getBytes("UTF-8")));
            assertEquals("b", headers.get("a"));
        } finally {
            in.close();
        }
    }

    // parsePaxHeaders(): empty input stream yields an empty header map (0-iteration loop)
    @Test
    public void testParsePaxHeaders_emptyStream_returnsEmptyMap() throws Throwable {
        TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        try {
            Map<String, String> headers = in.parsePaxHeaders(new ByteArrayInputStream(new byte[0]));
            assertTrue(headers.isEmpty());
        } finally {
            in.close();
        }
    }

    // matches(): length shorter than VERSION_OFFSET+VERSIONLEN -> false
    @Test
    public void testMatches_lengthTooShort_returnsFalse() throws Throwable {
        byte[] sig = new byte[10];
        assertFalse(TarArchiveInputStream.matches(sig, 5));
    }

    // matches(): sufficiently long but non-matching bytes -> false
    @Test
    public void testMatches_nonMatchingBytes_returnsFalse() throws Throwable {
        byte[] sig = new byte[TarConstants.VERSION_OFFSET + TarConstants.VERSIONLEN];
        assertFalse(TarArchiveInputStream.matches(sig, sig.length));
    }

    // matches(): POSIX magic + version placed at correct offsets -> true
    @Test
    public void testMatches_posixMagicAndVersion_returnsTrue() throws Throwable {
        byte[] sig = new byte[TarConstants.VERSION_OFFSET + TarConstants.VERSIONLEN];
        byte[] magic = TarConstants.MAGIC_POSIX.getBytes("UTF-8");
        byte[] version = TarConstants.VERSION_POSIX.getBytes("UTF-8");
        System.arraycopy(magic, 0, sig, TarConstants.MAGIC_OFFSET, magic.length);
        System.arraycopy(version, 0, sig, TarConstants.VERSION_OFFSET, version.length);
        assertTrue(TarArchiveInputStream.matches(sig, sig.length));
    }

    private static byte[] buildHeader(String name, long size, char typeflag) throws UnsupportedEncodingException {
        byte[] header = new byte[TarConstants.DEFAULT_RCDSIZE];
        byte[] nameBytes = name.getBytes("UTF-8");
        System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
        writeOctal(header, 100, 8, 0L);
        writeOctal(header, 108, 8, 0L);
        writeOctal(header, 116, 8, 0L);
        writeOctal(header, 124, 12, size);
        writeOctal(header, 136, 12, 0L);
        header[156] = (byte) typeflag;
        return header;
    }

    private static void writeOctal(byte[] buf, int offset, int length, long value) throws UnsupportedEncodingException {
        String oct = Long.toOctalString(value);
        int digits = length - 1;
        StringBuilder sb = new StringBuilder();
        for (int i = oct.length(); i < digits; i++) {
            sb.append('0');
        }
        sb.append(oct);
        byte[] bytes = sb.toString().getBytes("UTF-8");
        System.arraycopy(bytes, 0, buf, offset, bytes.length);
        buf[offset + length - 1] = 0;
    }

    private static byte[] padTo(byte[] content, int size) {
        byte[] result = new byte[size];
        System.arraycopy(content, 0, result, 0, Math.min(content.length, size));
        return result;
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (int i = 0; i < parts.length; i++) {
            total += parts[i].length;
        }
        byte[] result = new byte[total];
        int pos = 0;
        for (int i = 0; i < parts.length; i++) {
            System.arraycopy(parts[i], 0, result, pos, parts[i].length);
            pos += parts[i].length;
        }
        return result;
    }
}
