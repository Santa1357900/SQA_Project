package org.apache.commons.compress.archivers.sevenz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class SevenZFileClaudeTest {

    private final List<File> tempFiles = new ArrayList<File>();

    @After
    public void tearDown() {
        for (int i = 0; i < tempFiles.size(); i++) {
            File f = tempFiles.get(i);
            if (f.exists()) {
                f.delete();
            }
        }
    }

    private static final byte[] EMPTY_HEADER = {0x01, 0x05, 0x00, 0x00, 0x00};

    private File createArchiveFile(byte[] content) throws IOException {
        File f = File.createTempFile("sevenzclaude", ".7z");
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(content);
        } finally {
            fos.close();
        }
        tempFiles.add(f);
        return f;
    }

    private static byte[] toLE8(long v) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) {
            b[i] = (byte) (v & 0xFFL);
            v >>>= 8;
        }
        return b;
    }

    private static byte[] toLE4(int v) {
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            b[i] = (byte) (v & 0xFF);
            v >>>= 8;
        }
        return b;
    }

    private static byte[] buildValidArchiveBytes(byte[] nextHeader) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(SevenZFile.sevenZSignature, 0, SevenZFile.sevenZSignature.length);
        out.write(0);
        out.write(4);
        byte[] startBody = new byte[20];
        System.arraycopy(toLE8(0L), 0, startBody, 0, 8);
        System.arraycopy(toLE8(nextHeader.length), 0, startBody, 8, 8);
        CRC32 nhCrc = new CRC32();
        nhCrc.update(nextHeader);
        System.arraycopy(toLE4((int) nhCrc.getValue()), 0, startBody, 16, 4);
        CRC32 shCrc = new CRC32();
        shCrc.update(startBody);
        out.write(toLE4((int) shCrc.getValue()), 0, 4);
        out.write(startBody, 0, 20);
        out.write(nextHeader, 0, nextHeader.length);
        return out.toByteArray();
    }

    private static byte[] buildOneDirectoryHeader() {
        return new byte[]{0x01, 0x05, 0x01, 0x0E, 0x01, (byte) 0x80, 0x00, 0x00};
    }

    private static byte[] buildEmptyFileHeader() {
        return new byte[]{0x01, 0x05, 0x01, 0x0E, 0x01, (byte) 0x80,
                0x0F, 0x01, (byte) 0x80, 0x00, 0x00};
    }

    private static byte[] buildManyDirectoriesHeader() {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x01);
        b.write(0x05);
        b.write(0x80);
        b.write(0xC8);
        b.write(0x0E);
        b.write(0x19);
        for (int i = 0; i < 25; i++) {
            b.write(0xFF);
        }
        b.write(0x00);
        b.write(0x00);
        return b.toByteArray();
    }

    // matches(): length shorter than signature -> false without touching content
    @Test
    public void testMatches_lengthLessThanSignatureLength_returnsFalse() throws Throwable {
        byte[] sig = {'7', 'z', (byte) 0xBC, (byte) 0xAF, (byte) 0x27, (byte) 0x1C};
        assertFalse(SevenZFile.matches(sig, 5));
    }

    // matches(): first byte mismatch -> false
    @Test
    public void testMatches_firstByteMismatch_returnsFalse() throws Throwable {
        byte[] sig = {'X', 'z', (byte) 0xBC, (byte) 0xAF, (byte) 0x27, (byte) 0x1C};
        assertFalse(SevenZFile.matches(sig, 6));
    }

    // matches(): last byte mismatch -> false
    @Test
    public void testMatches_lastByteMismatch_returnsFalse() throws Throwable {
        byte[] sig = {'7', 'z', (byte) 0xBC, (byte) 0xAF, (byte) 0x27, (byte) 0x00};
        assertFalse(SevenZFile.matches(sig, 6));
    }

    // matches(): exact 6-byte match -> true
    @Test
    public void testMatches_exactMatch_returnsTrue() throws Throwable {
        byte[] sig = {'7', 'z', (byte) 0xBC, (byte) 0xAF, (byte) 0x27, (byte) 0x1C};
        assertTrue(SevenZFile.matches(sig, 6));
    }

    // matches(): extra trailing bytes/length ignored once prefix matches -> true
    @Test
    public void testMatches_extraBytesIgnored_returnsTrue() throws Throwable {
        byte[] sig = {'7', 'z', (byte) 0xBC, (byte) 0xAF, (byte) 0x27, (byte) 0x1C, 0, 0};
        assertTrue(SevenZFile.matches(sig, 8));
    }

    // readHeaders(): signature bytes mismatch -> "Bad 7z signature"
    @Test
    public void testConstructor_badSignature_throwsIOException() throws Throwable {
        File f = createArchiveFile(new byte[6]);
        try {
            new SevenZFile(f);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("signature"));
        }
    }

    // readHeaders(): major version != 0 -> unsupported version
    @Test
    public void testConstructor_unsupportedMajorVersion_throwsIOException() throws Throwable {
        byte[] content = new byte[8];
        System.arraycopy(SevenZFile.sevenZSignature, 0, content, 0, 6);
        content[6] = 1;
        content[7] = 0;
        File f = createArchiveFile(content);
        try {
            new SevenZFile(f);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("version"));
        }
    }

    // readStartHeader(): corrupted startHeaderCrc -> CRC verification failure
    @Test
    public void testConstructor_startHeaderCrcMismatch_throwsIOException() throws Throwable {
        byte[] good = buildValidArchiveBytes(EMPTY_HEADER);
        byte[] corrupted = good.clone();
        corrupted[8] = (byte) (corrupted[8] ^ 0xFF);
        File f = createArchiveFile(corrupted);
        try {
            new SevenZFile(f);
            fail("expected IOException");
        } catch (IOException expected) {
            // CRC32VerifyingInputStream detects the mismatch
        }
    }

    // readHeaders(): nextHeader bytes do not match stored CRC -> "NextHeader CRC mismatch"
    @Test
    public void testConstructor_nextHeaderCrcMismatch_throwsIOException() throws Throwable {
        byte[] good = buildValidArchiveBytes(EMPTY_HEADER);
        byte[] corrupted = good.clone();
        int lastIdx = corrupted.length - 1;
        corrupted[lastIdx] = (byte) (corrupted[lastIdx] ^ 0xFF);
        File f = createArchiveFile(corrupted);
        try {
            new SevenZFile(f);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("CRC"));
        }
    }

    // readHeaders(): first nid neither kHeader nor kEncodedHeader -> "no Header"
    @Test
    public void testConstructor_noHeaderMarker_throwsIOException() throws Throwable {
        byte[] nextHeader = {0x05};
        File f = createArchiveFile(buildValidArchiveBytes(nextHeader));
        try {
            new SevenZFile(f);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no Header"));
        }
    }

    // readHeader(): kAdditionalStreamsInfo present -> unsupported
    @Test
    public void testConstructor_additionalStreamsInfo_throwsIOException() throws Throwable {
        byte[] nextHeader = {0x01, 0x03};
        File f = createArchiveFile(buildValidArchiveBytes(nextHeader));
        try {
            new SevenZFile(f);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Additional streams"));
        }
    }

    // readHeader(): trailing nid after kFilesInfo is not kEnd -> badly terminated
    @Test
    public void testConstructor_badlyTerminatedHeader_throwsIOException() throws Throwable {
        byte[] nextHeader = {0x01, 0x05, 0x00, 0x00, 0x09};
        File f = createArchiveFile(buildValidArchiveBytes(nextHeader));
        try {
            new SevenZFile(f);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Badly terminated"));
        }
    }

    // constructor: RandomAccessFile cannot open a non-existent file
    @Test
    public void testConstructor_fileNotFound_throwsIOException() throws Throwable {
        File missing = new File(System.getProperty("java.io.tmpdir"),
                "sevenzclaude_missing_12345.7z");
        if (missing.exists()) {
            missing.delete();
        }
        try {
            new SevenZFile(missing);
            fail("expected IOException");
        } catch (IOException expected) {
            // FileNotFoundException is a subtype of IOException
        }
    }

    // minimal archive with zero files opens successfully and reports no entries
    @Test
    public void testConstructor_emptyArchive_opensSuccessfully() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(EMPTY_HEADER));
        SevenZFile sevenZFile = new SevenZFile(f);
        assertNull(sevenZFile.getNextEntry());
    }

    // single-arg constructor delegates to (File,null) and opens the same way
    @Test
    public void testConstructor_singleArgConstructor_opensSuccessfully() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(EMPTY_HEADER));
        SevenZFile sevenZFile = new SevenZFile(f);
        int count = 0;
        for (SevenZArchiveEntry e : sevenZFile.getEntries()) {
            count++;
        }
        assertEquals(0, count);
    }

    // readArchiveProperties(): while-loop executes zero iterations (immediate kEnd)
    @Test
    public void testConstructor_archivePropertiesZero_opensSuccessfully() throws Throwable {
        byte[] nextHeader = {0x01, 0x02, 0x00, 0x05, 0x00, 0x00, 0x00};
        File f = createArchiveFile(buildValidArchiveBytes(nextHeader));
        SevenZFile sevenZFile = new SevenZFile(f);
        int count = 0;
        for (SevenZArchiveEntry e : sevenZFile.getEntries()) {
            count++;
        }
        assertEquals(0, count);
    }

    // readArchiveProperties(): while-loop executes one iteration before kEnd
    @Test
    public void testConstructor_archivePropertiesOne_opensSuccessfully() throws Throwable {
        byte[] nextHeader = {0x01, 0x02, 0x01, 0x02, (byte) 0xAB, (byte) 0xCD,
                0x00, 0x05, 0x00, 0x00, 0x00};
        File f = createArchiveFile(buildValidArchiveBytes(nextHeader));
        SevenZFile sevenZFile = new SevenZFile(f);
        int count = 0;
        for (SevenZArchiveEntry e : sevenZFile.getEntries()) {
            count++;
        }
        assertEquals(0, count);
    }

    // readFilesInfo(): kEmptyFile bit true -> empty entry classified as file, not directory
    @Test
    public void testConstructor_emptyFileNotDirectory_opensSuccessfully() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(buildEmptyFileHeader()));
        SevenZFile sevenZFile = new SevenZFile(f);
        SevenZArchiveEntry entry = sevenZFile.getNextEntry();
        assertFalse(entry.hasStream());
        assertEquals(0L, entry.getSize());
    }

    // getNextEntry(): zero files -> currentEntryIndex check returns null immediately
    @Test
    public void testGetNextEntry_emptyArchive_returnsNullImmediately() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(EMPTY_HEADER));
        SevenZFile sevenZFile = new SevenZFile(f);
        assertNull(sevenZFile.getNextEntry());
    }

    // getNextEntry(): one directory entry then null on exhaustion
    @Test
    public void testGetNextEntry_oneDirectoryEntry_returnsEntryThenNull() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(buildOneDirectoryHeader()));
        SevenZFile sevenZFile = new SevenZFile(f);
        SevenZArchiveEntry entry = sevenZFile.getNextEntry();
        assertFalse(entry.hasStream());
        assertEquals(0L, entry.getSize());
        assertNull(sevenZFile.getNextEntry());
    }

    // getEntries(): zero files -> empty iterable
    @Test
    public void testGetEntries_emptyArchive_noElements() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(EMPTY_HEADER));
        SevenZFile sevenZFile = new SevenZFile(f);
        int count = 0;
        for (SevenZArchiveEntry e : sevenZFile.getEntries()) {
            count++;
        }
        assertEquals(0, count);
    }

    // readFilesInfo(): numFiles decoded via 2-byte uint64 encoding (200), many loop iterations
    @Test
    public void testGetEntries_manyDirectories_countMatches200() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(buildManyDirectoriesHeader()));
        SevenZFile sevenZFile = new SevenZFile(f);
        int count = 0;
        boolean allNoStream = true;
        for (SevenZArchiveEntry e : sevenZFile.getEntries()) {
            count++;
            if (e.hasStream()) {
                allNoStream = false;
            }
        }
        assertEquals(200, count);
        assertTrue(allNoStream);
    }

    // read(): called before any getNextEntry() -> deferredBlockStreams empty
    @Test
    public void testRead_beforeGetNextEntry_throwsIllegalStateException() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(EMPTY_HEADER));
        SevenZFile sevenZFile = new SevenZFile(f);
        try {
            sevenZFile.read();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // no current entry
        }
    }

    // read(): directory entry has no stream, deferredBlockStreams stays empty
    @Test
    public void testRead_afterDirectoryEntryNoStream_throwsIllegalStateException() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(buildOneDirectoryHeader()));
        SevenZFile sevenZFile = new SevenZFile(f);
        sevenZFile.getNextEntry();
        try {
            sevenZFile.read();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // entry without stream never queues a decoded stream
        }
    }

    // read(byte[]): delegates to getCurrentStream() -> same guard as read()
    @Test
    public void testReadByteArray_beforeGetNextEntry_throwsIllegalStateException() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(EMPTY_HEADER));
        SevenZFile sevenZFile = new SevenZFile(f);
        try {
            sevenZFile.read(new byte[10]);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // no current entry
        }
    }

    // read(byte[],off,len): delegates to getCurrentStream() -> same guard
    @Test
    public void testReadByteArrayOffsetLen_beforeGetNextEntry_throwsIllegalStateException() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(EMPTY_HEADER));
        SevenZFile sevenZFile = new SevenZFile(f);
        try {
            sevenZFile.read(new byte[10], 0, 5);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // no current entry
        }
    }

    // close(): idempotent, metadata remains accessible after closing
    @Test
    public void testClose_calledTwice_entriesStillAccessible() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(EMPTY_HEADER));
        SevenZFile sevenZFile = new SevenZFile(f);
        sevenZFile.close();
        sevenZFile.close();
        int count = 0;
        for (SevenZArchiveEntry e : sevenZFile.getEntries()) {
            count++;
        }
        assertEquals(0, count);
    }

    // toString(): delegates to archive.toString(), must never be null
    @Test
    public void testToString_returnsNonNull() throws Throwable {
        File f = createArchiveFile(buildValidArchiveBytes(EMPTY_HEADER));
        SevenZFile sevenZFile = new SevenZFile(f);
        assertNotNull(sevenZFile.toString());
    }
}
