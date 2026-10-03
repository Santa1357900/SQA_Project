package org.apache.commons.compress.archivers.tar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;

import org.junit.Test;
import static org.junit.Assert.*;

public class TarArchiveInputStreamClaudeTest {

    // builds one 512-byte USTAR header block with correct POSIX checksum
    private static byte[] buildHeader(String name, long size, char typeFlag) throws Exception {
        byte[] h = new byte[512];
        byte[] nameBytes = name.getBytes("US-ASCII");
        System.arraycopy(nameBytes, 0, h, 0, nameBytes.length);
        writeOctalField(h, 100, 8, 420L);
        writeOctalField(h, 108, 8, 0L);
        writeOctalField(h, 116, 8, 0L);
        writeOctalField(h, 124, 12, size);
        writeOctalField(h, 136, 12, 0L);
        for (int i = 148; i < 156; i++) {
            h[i] = (byte) ' ';
        }
        h[156] = (byte) typeFlag;
        byte[] magic = new byte[] { 'u', 's', 't', 'a', 'r', 0, '0', '0' };
        System.arraycopy(magic, 0, h, 257, magic.length);
        long sum = 0;
        for (int i = 0; i < 512; i++) {
            sum += (h[i] & 0xFF);
        }
        String chkOct = Long.toOctalString(sum);
        StringBuilder sb = new StringBuilder();
        for (int i = chkOct.length(); i < 6; i++) {
            sb.append('0');
        }
        sb.append(chkOct);
        byte[] chkBytes = sb.toString().getBytes("US-ASCII");
        System.arraycopy(chkBytes, 0, h, 148, chkBytes.length);
        h[154] = 0;
        h[155] = (byte) ' ';
        return h;
    }

    private static void writeOctalField(byte[] buf, int offset, int length, long value) throws Exception {
        String oct = Long.toOctalString(value);
        int digitSpace = length - 1;
        StringBuilder sb = new StringBuilder();
        for (int i = oct.length(); i < digitSpace; i++) {
            sb.append('0');
        }
        sb.append(oct);
        byte[] bytes = sb.toString().getBytes("US-ASCII");
        System.arraycopy(bytes, 0, buf, offset, bytes.length);
        buf[offset + length - 1] = 0;
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

    // constructor(is) must use TarConstants.DEFAULT_RCDSIZE as record size
    @Test
    public void testConstructor_default_usesDefaultRecordSize() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertEquals(TarConstants.DEFAULT_RCDSIZE, tais.getRecordSize());
    }

    // constructor(is, blockSize) must still use the default record size
    @Test
    public void testConstructor_withBlockSize_usesDefaultRecordSize() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 2048);
        assertEquals(TarConstants.DEFAULT_RCDSIZE, tais.getRecordSize());
    }

    // constructor(is, blockSize, encoding) must store the encoding field
    @Test
    public void testConstructor_withBlockSizeAndEncoding_setsEncodingField() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 2048, "UTF-8");
        assertEquals("UTF-8", tais.encoding);
    }

    // constructor(is, blockSize, recordSize) must use given record size
    @Test
    public void testConstructor_withBlockAndRecordSize_usesGivenRecordSize() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 2048, 128);
        assertEquals(128, tais.getRecordSize());
    }

    // full constructor(is, blockSize, recordSize, encoding) sets both fields
    @Test
    public void testConstructor_fullArgsWithEncoding_setsFieldsCorrectly() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(newbyte[0]), 2048, 128, "ASCII");
        assertEquals(128, tais.getRecordSize());
        assertEquals("ASCII", tais.encoding);
    }

    // encoding not specified -> encoding field must be null
    @Test
    public void testConstructor_noEncodingSpecified_encodingFieldIsNull() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertNull(tais.encoding);
    }

    // close() must delegate to the underlying InputStream's close()
    @Test
    public void testClose_delegatesToUnderlyingStream() throws Throwable {
        final boolean[] closedFlag = new boolean[1];
        InputStream inner = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() throws IOException {
                closedFlag[0] = true;
                super.close();
            }
        };
        TarArchiveInputStream tais = new TarArchiveInputStream(inner);
        tais.close();
        assertTrue(closedFlag[0]);
    }

    // markSupported() must always return false
    @Test
    public void testMarkSupported_alwaysReturnsFalse() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertFalse(tais.markSupported());
    }

    // mark() is a no-op and must not change observable state
    @Test
    public void testMark_isNoOpDoesNotAffectState() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        tais.mark(100);
        assertEquals(0, tais.available());
    }

    // reset() is a no-op and must not change observable state
    @Test
    public void testReset_isNoOpDoesNotAffectState() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        tais.reset();
        assertEquals(0, tais.available());
    }

    // available() before any entry has been read must be zero
    @Test
    public void testAvailable_beforeAnyEntryRead_returnsZero() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertEquals(0, tais.available());
    }

    // skip(n) with negative n must return 0 and not throw
    @Test
    public void testSkip_negativeN_returnsZeroAndDoesNotThrow() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertEquals(0L, tais.skip(-5L));
    }

    // skip(n) with n == 0 must return 0
    @Test
    public void testSkip_zeroN_returnsZero() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertEquals(0L, tais.skip(0L));
    }

    // empty archive -> getNextTarEntry returns null and marks EOF
    @Test
    public void testGetNextTarEntry_emptyStream_returnsNullAndSetsEOF() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertNull(tais.getNextTarEntry());
        assertTrue(tais.isAtEOF());
    }

    // once hasHitEOF is true, subsequent calls short-circuit to null
    @Test
    public void testGetNextTarEntry_calledAfterEOF_returnsNullImmediately() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        tais.getNextTarEntry();
        assertNull(tais.getNextTarEntry());
        assertNull(tais.getCurrentEntry());
    }

    // header shorter than one record -> readRecord returns null -> entry is null
    @Test
    public void testGetNextTarEntry_incompleteHeaderRecord_returnsNull() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[100]));
        assertNull(tais.getNextTarEntry());
        assertTrue(tais.isAtEOF());
    }

    // a full record of all-zero bytes is the EOF marker record
    @Test
    public void testGetNextTarEntry_allZeroRecord_returnsNullAndSetsEOF() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[512]));
        assertNull(tais.getNextTarEntry());
        assertTrue(tais.isAtEOF());
    }

    // a valid regular-file header must produce an entry with the declared size
    @Test
    public void testGetNextTarEntry_validRegularFile_returnsEntryWithCorrectSize() throws Throwable {
        byte[] header = buildHeader("hello.txt", 5, '0');
        byte[] content = "Hello".getBytes("US-ASCII");
        byte[] data = concat(header, content, new byte[507]);
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(data));
        TarArchiveEntry entry = tais.getNextTarEntry();
        assertNotNull(entry);
        assertEquals(5L, entry.getSize());
    }

    // getNextEntry() must delegate to getNextTarEntry() and update getCurrentEntry()
    @Test
    public void testGetNextEntry_returnsSameEntryAsGetNextTarEntry() throws Throwable {
        byte[] header = buildHeader("a.txt", 0, '0');
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(header));
        ArchiveEntry entry = tais.getNextEntry();
        assertNotNull(entry);
        assertSame(entry, tais.getCurrentEntry());
    }

    // read() with no current entry set must throw IllegalStateException per its own message
    @Test
    public void testRead_noCurrentEntry_throwsIllegalStateException() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        byte[] buf = new byte[10];
        try {
            tais.read(buf, 0, 10);
            fail("expected IllegalStateException when no current entry is set");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("No current tar entry"));
        }
    }

    // read() of a regular entry returns its content then -1 at entry end
    @Test
    public void testRead_regularFile_returnsContentThenMinusOne() throws Throwable {
        byte[] header = buildHeader("f.txt", 5, '0');
        byte[] content = "Hello".getBytes("US-ASCII");
        byte[] data = concat(header, content, new byte[507]);
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(data));
        tais.getNextTarEntry();
        byte[] buf = new byte[10];
        int read = tais.read(buf, 0, 10);
        assertEquals(5, read);
        assertEquals("Hello", new String(buf, 0, 5, "US-ASCII"));
        assertEquals(-1, tais.read(buf, 0, 10));
    }

    // entry declares more bytes than are actually present -> Truncated TAR archive
    @Test
    public void testRead_truncatedArchive_throwsIOException() throws Throwable {
        byte[] header = buildHeader("f.txt", 5, '0');
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(header));
        tais.getNextTarEntry();
        byte[] buf = new byte[5];
        try {
            tais.read(buf, 0, 5);
            fail("expected IOException for truncated archive");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Truncated"));
        }
    }

    // read() on a directory entry must always return -1
    @Test
    public void testRead_directoryEntry_returnsMinusOne() throws Throwable {
        byte[] header = buildHeader("dir/", 0, '5');
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(header));
        TarArchiveEntry entry = tais.getNextTarEntry();
        assertTrue(entry.isDirectory());
        byte[] buf = new byte[10];
        assertEquals(-1, tais.read(buf, 0, 10));
    }

    // available() on a regular entry reflects remaining unread bytes
    @Test
    public void testAvailable_regularEntry_reflectsRemainingBytes() throws Throwable {
        byte[] header = buildHeader("f.txt", 5, '0');
        byte[] content = "Hello".getBytes("US-ASCII");
        byte[] data = concat(header, content, new byte[507]);
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(data));
        tais.getNextTarEntry();
        assertEquals(5, tais.available());
        byte[] buf = new byte[3];
        tais.read(buf, 0, 3);
        assertEquals(2, tais.available());
    }

    // available() on a directory entry is always zero
    @Test
    public void testAvailable_directoryEntry_returnsZero() throws Throwable {
        byte[] header = buildHeader("dir/", 0, '5');
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(header));
        tais.getNextTarEntry();
        assertEquals(0, tais.available());
    }

    // skip() on a regular entry is bounded by the remaining entry bytes
    @Test
    public void testSkip_regularEntry_skipsWithinAvailableBytes() throws Throwable {
        byte[] header = buildHeader("f.txt", 5, '0');
        byte[] content = "Hello".getBytes("US-ASCII");
        byte[] data = concat(header, content, new byte[507]);
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(data));
        tais.getNextTarEntry();
        assertEquals(3L, tais.skip(3L));
        assertEquals(2, tais.available());
        assertEquals(2L, tais.skip(10L));
        assertEquals(0, tais.available());
    }

    // skip() on a directory entry always returns 0
    @Test
    public void testSkip_directoryEntry_returnsZero() throws Throwable {
        byte[] header = buildHeader("dir/", 0, '5');
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(header));
        tais.getNextTarEntry();
        assertEquals(0L, tais.skip(100L));
    }

    // canReadEntryData() must return true for a non-sparse TarArchiveEntry
    @Test
    public void testCanReadEntryData_regularEntry_returnsTrue() throws Throwable {
        byte[] header = buildHeader("f.txt", 0, '0');
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(header));
        TarArchiveEntry entry = tais.getNextTarEntry();
        assertFalse(entry.isSparse());
        assertTrue(tais.canReadEntryData(entry));
    }

    // canReadEntryData() must return false when the entry is not a TarArchiveEntry
    @Test
    public void testCanReadEntryData_nonTarArchiveEntry_returnsFalse() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertFalse(tais.canReadEntryData(null));
    }

    // getCurrentEntry() reflects null before reading and the parsed entry after
    @Test
    public void testGetCurrentEntry_reflectsCurrentState() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertNull(tais.getCurrentEntry());
        byte[] header = buildHeader("f.txt", 0, '0');
        TarArchiveInputStream tais2 = new TarArchiveInputStream(new ByteArrayInputStream(header));
        TarArchiveEntry entry = tais2.getNextTarEntry();
        assertSame(entry, tais2.getCurrentEntry());
    }

    // getNextTarEntry() must skip any unread data and padding of the previous entry
    @Test
    public void testGetNextTarEntry_skipsRemainingDataOfPreviousEntry() throws Throwable {
        byte[] header1 = buildHeader("one.txt", 5, '0');
        byte[] content1 = "Hello".getBytes("US-ASCII");
        byte[] header2 = buildHeader("two.txt", 4, '0');
        byte[] content2 = "Bye!".getBytes("US-ASCII");
        byte[] data = concat(header1, content1, new byte[507], header2, content2, new byte[508]);
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(data));
        TarArchiveEntry e1 = tais.getNextTarEntry();
        assertEquals(5L, e1.getSize());
        TarArchiveEntry e2 = tais.getNextTarEntry();
        assertNotNull(e2);
        assertEquals(4L, e2.getSize());
        byte[] buf = new byte[4];
        assertEquals(4, tais.read(buf, 0, 4));
        assertEquals("Bye!", new String(buf, 0, 4, "US-ASCII"));
    }

    // a GNU long-name entry must apply to the following real entry transparently
    @Test
    public void testGetNextTarEntry_gnuLongNameEntry_appliesToRealEntry() throws Throwable {
        StringBuilder longNameBuilder = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            longNameBuilder.append('x');
        }
        byte[] longNameBytes = (longNameBuilder.toString() + "\0").getBytes("US-ASCII");
        byte[] longHeader = buildHeader("././@LongLink", longNameBytes.length, 'L');
        int pad1 = 512 - (longNameBytes.length % 512);
        byte[] realHeader = buildHeader("short.txt", 4, '0');
        byte[] realContent = "DATA".getBytes("US-ASCII");
        byte[] data = concat(longHeader, longNameBytes, new byte[pad1], realHeader, realContent, new byte[508]);
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(data));
        TarArchiveEntry entry = tais.getNextTarEntry();
        assertNotNull(entry);
        assertEquals(4L, entry.getSize());
        byte[] buf = new byte[4];
        assertEquals(4, tais.read(buf, 0, 4));
        assertEquals("DATA", new String(buf, 0, 4, "US-ASCII"));
    }

    // a PAX extended header "size" entry must override the real entry's declared size
    @Test
    public void testGetNextTarEntry_paxHeaderOverridesSize() throws Throwable {
        byte[] paxContent = "9 size=3\n".getBytes("US-ASCII");
        byte[] paxHeader = buildHeader("PaxHeader", paxContent.length, 'x');
        byte[] realHeader = buildHeader("real.txt", 100, '0');
        byte[] realContent = "XYZ".getBytes("US-ASCII");
        byte[] data = concat(paxHeader, paxContent, new byte[512 - paxContent.length],
                realHeader, realContent, new byte[509]);
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(data));
        TarArchiveEntry entry = tais.getNextTarEntry();
        assertNotNull(entry);
        assertEquals(3L, entry.getSize());
        byte[] buf = new byte[3];
        assertEquals(3, tais.read(buf, 0, 3));
        assertEquals("XYZ", new String(buf, 0, 3, "US-ASCII"));
    }

    // a global PAX header "size" entry must override subsequent entries' declared size
    @Test
    public void testGetNextTarEntry_globalPaxHeaderOverridesSize() throws Throwable {
        byte[] paxContent = "9 size=3\n".getBytes("US-ASCII");
        byte[] globalHeader = buildHeader("GlobalHeader", paxContent.length, 'g');
        byte[] realHeader = buildHeader("real2.txt", 100, '0');
        byte[] realContent = "ABC".getBytes("US-ASCII");
        byte[] data = concat(globalHeader, paxContent, new byte[512 - paxContent.length],
                realHeader, realContent, new byte[509]);
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(data));
        TarArchiveEntry entry = tais.getNextTarEntry();
        assertNotNull(entry);
        assertEquals(3L, entry.getSize());
        byte[] buf = new byte[3];
        assertEquals(3, tais.read(buf, 0, 3));
        assertEquals("ABC", new String(buf, 0, 3, "US-ASCII"));
    }

    // matches() must return false when the supplied length is too short
    @Test
    public void testMatches_lengthTooShort_returnsFalse() throws Throwable {
        assertFalse(TarArchiveInputStream.matches(new byte[0], 0));
    }

    // matches() must return false for a signature that matches none of the known magics
    @Test
    public void testMatches_nonMatchingSignature_returnsFalse() throws Throwable {
        assertFalse(TarArchiveInputStream.matches(new byte[512], 512));
    }

    // getRecordSize() must reflect the value configured via the constructor
    @Test
    public void testGetRecordSize_returnsConfiguredValue() throws Throwable {
        TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(new byte[0]), 2048, 256);
        assertEquals(256, tais.getRecordSize());
    }
}
