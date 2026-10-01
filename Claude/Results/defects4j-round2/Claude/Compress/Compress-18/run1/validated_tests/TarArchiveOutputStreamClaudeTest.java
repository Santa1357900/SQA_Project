package org.apache.commons.compress.archivers.tar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class TarArchiveOutputStreamClaudeTest {

    private ByteArrayOutputStream baos;
    private TarArchiveOutputStream tos;

    @Before
    public void setUp() throws Throwable {
        baos = new ByteArrayOutputStream();
        tos = new TarArchiveOutputStream(baos);
    }

    private String makeLongName() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            sb.append('a');
        }
        return sb.toString();
    }

    // Covers: TarArchiveOutputStream(OutputStream) -> getRecordSize() returns TarBuffer default record size
    @Test
    public void testConstructorDefault_getRecordSize_returnsDefaultRecordSize() throws Throwable {
        ByteArrayOutputStream localBaos = new ByteArrayOutputStream();
        TarArchiveOutputStream localTos = new TarArchiveOutputStream(localBaos);
        assertEquals(TarBuffer.DEFAULT_RCDSIZE, localTos.getRecordSize());
    }

    // Covers: TarArchiveOutputStream(OutputStream, String) -> can write and close a short-named entry
    @Test
    public void testConstructorWithEncoding_shortAsciiName_putAndCloseSucceeds() throws Throwable {
        ByteArrayOutputStream localBaos = new ByteArrayOutputStream();
        TarArchiveOutputStream localTos = new TarArchiveOutputStream(localBaos, "UTF-8");
        localTos.putArchiveEntry(new TarArchiveEntry("ascii.txt"));
        localTos.closeArchiveEntry();
        localTos.finish();
        assertTrue(localTos.getBytesWritten() > 0);
    }

    // Covers: TarArchiveOutputStream(OutputStream, int blockSize) -> record size stays default
    @Test
    public void testConstructorWithBlockSize_getRecordSize_returnsDefaultRecordSize() throws Throwable {
        ByteArrayOutputStream localBaos = new ByteArrayOutputStream();
        TarArchiveOutputStream localTos = new TarArchiveOutputStream(localBaos, TarBuffer.DEFAULT_BLKSIZE);
        assertEquals(TarBuffer.DEFAULT_RCDSIZE, localTos.getRecordSize());
    }

    // Covers: TarArchiveOutputStream(OutputStream, int blockSize, String encoding) -> writes successfully
    @Test
    public void testConstructorWithBlockSizeAndEncoding_shortAsciiName_putAndCloseSucceeds() throws Throwable {
        ByteArrayOutputStream localBaos = new ByteArrayOutputStream();
        TarArchiveOutputStream localTos = new TarArchiveOutputStream(localBaos, TarBuffer.DEFAULT_BLKSIZE, "UTF-8");
        localTos.putArchiveEntry(new TarArchiveEntry("ascii2.txt"));
        localTos.closeArchiveEntry();
        localTos.finish();
        assertTrue(localTos.getBytesWritten() > 0);
    }

    // Covers: TarArchiveOutputStream(OutputStream, int blockSize, int recordSize) -> getRecordSize matches given value
    @Test
    public void testConstructorWithBlockSizeAndRecordSize_getRecordSize_returnsGivenRecordSize() throws Throwable {
        ByteArrayOutputStream localBaos = new ByteArrayOutputStream();
        TarArchiveOutputStream localTos = new TarArchiveOutputStream(localBaos, TarBuffer.DEFAULT_BLKSIZE, 1024);
        assertEquals(1024, localTos.getRecordSize());
    }

    // Covers: TarArchiveOutputStream(OutputStream, int blockSize, int recordSize, String encoding) -> writes successfully
    @Test
    public void testConstructorFull_shortAsciiName_putAndCloseSucceeds() throws Throwable {
        ByteArrayOutputStream localBaos = new ByteArrayOutputStream();
        TarArchiveOutputStream localTos = new TarArchiveOutputStream(localBaos, TarBuffer.DEFAULT_BLKSIZE,
                TarBuffer.DEFAULT_RCDSIZE, "UTF-8");
        localTos.putArchiveEntry(new TarArchiveEntry("ascii3.txt"));
        localTos.closeArchiveEntry();
        localTos.finish();
        assertTrue(localTos.getBytesWritten() > 0);
    }

    // Covers: setLongFileMode default (LONGFILE_ERROR) + putArchiveEntry: name >= NAMELEN throws RuntimeException
    @Test
    public void testSetLongFileMode_errorModeDefault_longNameThrowsRuntimeException() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry(makeLongName());
        try {
            tos.putArchiveEntry(entry);
            fail("expected RuntimeException for too long file name");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("too long"));
        }
    }

    // Covers: setLongFileMode(LONGFILE_TRUNCATE) branch: long name does not throw
    @Test
    public void testSetLongFileMode_truncate_longNameDoesNotThrow() throws Throwable {
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_TRUNCATE);
        TarArchiveEntry entry = new TarArchiveEntry(makeLongName());
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: setLongFileMode(LONGFILE_GNU) branch: writes long-link sub-entry then main entry without exception
    @Test
    public void testSetLongFileMode_gnu_longNameWritesLongLinkEntryWithoutException() throws Throwable {
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        TarArchiveEntry entry = new TarArchiveEntry(makeLongName());
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: setLongFileMode(LONGFILE_POSIX) branch: writes PAX "path" header then main entry without exception
    @Test
    public void testSetLongFileMode_posix_longNameWritesPaxHeaderWithoutException() throws Throwable {
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        TarArchiveEntry entry = new TarArchiveEntry(makeLongName());
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: nameLen < NAMELEN -> no long-name handling triggered, regardless of longFileMode
    @Test
    public void testPutArchiveEntry_shortName_defaultMode_noException() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("short.txt");
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: setBigNumberMode default (BIGNUMBER_ERROR) -> failForBigNumbers throws for oversized entry size
    @Test
    public void testSetBigNumberMode_errorModeDefault_hugeSizeThrowsRuntimeException() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("huge.bin");
        entry.setSize(100000000000L);
        try {
            tos.putArchiveEntry(entry);
            fail("expected RuntimeException for too big entry size");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("too big"));
        }
    }





    // Covers: setAddPaxHeadersForNonAsciiNames(true) with non-ASCII name -> pax "path" header added, no exception
    @Test
    public void testSetAddPaxHeadersForNonAsciiNames_true_nonAsciiNameNoException() throws Throwable {
        tos.setAddPaxHeadersForNonAsciiNames(true);
        TarArchiveEntry entry = new TarArchiveEntry("t\u00e8st-\u00fcn\u00efc\u00f6d\u00e9.txt");
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: setAddPaxHeadersForNonAsciiNames default false -> non-ascii branch skipped, still works normally
    @Test
    public void testSetAddPaxHeadersForNonAsciiNames_falseDefault_nonAsciiNameNoException() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("t\u00e8st-\u00fcn\u00efc\u00f6d\u00e9-2.txt");
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: getCount() delegates to (int) getBytesWritten()
    @Test
    public void testGetCount_afterFinish_equalsBytesWrittenAsInt() throws Throwable {
        tos.finish();
        assertEquals((int) tos.getBytesWritten(), tos.getCount());
    }

    // Covers: getBytesWritten() before any output is zero
    @Test
    public void testGetBytesWritten_beforeAnyWrite_isZero() throws Throwable {
        assertEquals(0L, tos.getBytesWritten());
    }

    // Covers: getBytesWritten() after finish() of an empty archive is positive (EOF records flushed)
    @Test
    public void testGetBytesWritten_afterFinish_isPositive() throws Throwable {
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: finish() on an archive with no entries does not throw
    @Test
    public void testFinish_emptyArchive_doesNotThrow() throws Throwable {
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: finish() called twice throws IOException "already been finished"
    @Test
    public void testFinish_calledTwice_throwsIOException() throws Throwable {
        tos.finish();
        try {
            tos.finish();
            fail("expected IOException on second finish()");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }

    // Covers: finish() with an unclosed entry throws IOException "unclosed entries"
    @Test
    public void testFinish_withUnclosedEntry_throwsIOException() throws Throwable {
        tos.putArchiveEntry(new TarArchiveEntry("unclosed.txt"));
        try {
            tos.finish();
            fail("expected IOException for unclosed entry");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("unclosed"));
        }
    }

    // Covers: close() without explicit finish() internally finishes the archive
    @Test
    public void testClose_withoutExplicitFinish_finishesArchive() throws Throwable {
        tos.putArchiveEntry(new TarArchiveEntry("closeme.txt"));
        tos.closeArchiveEntry();
        tos.close();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: close() called twice does not throw (closed/finished guards)
    @Test
    public void testClose_calledTwice_doesNotThrow() throws Throwable {
        tos.putArchiveEntry(new TarArchiveEntry("closetwice.txt"));
        tos.closeArchiveEntry();
        tos.close();
        tos.close();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: putArchiveEntry() after finish() throws IOException "already been finished"
    @Test
    public void testPutArchiveEntry_afterFinish_throwsIOException() throws Throwable {
        tos.finish();
        try {
            tos.putArchiveEntry(new TarArchiveEntry("late.txt"));
            fail("expected IOException after finish()");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }

    // Covers: closeArchiveEntry() with no open entry throws IOException "No current entry"
    @Test
    public void testCloseArchiveEntry_withoutOpenEntry_throwsIOException() throws Throwable {
        try {
            tos.closeArchiveEntry();
            fail("expected IOException: no current entry");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("No current entry"));
        }
    }

    // Covers: closeArchiveEntry() after finish() throws IOException "already been finished"
    @Test
    public void testCloseArchiveEntry_afterFinish_throwsIOException() throws Throwable {
        tos.finish();
        try {
            tos.closeArchiveEntry();
            fail("expected IOException after finish()");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }

    // Covers: closeArchiveEntry() with fewer bytes written than declared size throws IOException
    @Test
    public void testCloseArchiveEntry_tooFewBytesWritten_throwsIOException() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("short-write.txt");
        entry.setSize(10);
        tos.putArchiveEntry(entry);
        tos.write(new byte[5], 0, 5);
        try {
            tos.closeArchiveEntry();
            fail("expected IOException for incomplete entry");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("before"));
        }
    }

    // Covers: closeArchiveEntry() with exactly the declared bytes written succeeds (assembly buffer flush path)
    @Test
    public void testCloseArchiveEntry_assemblyBufferExactMatch_doesNotThrow() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("exact-write.txt");
        entry.setSize(5);
        tos.putArchiveEntry(entry);
        tos.write(new byte[5], 0, 5);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: write() exceeding the declared entry size throws IOException "exceeds size"
    @Test
    public void testWrite_exceedsCurrentEntrySize_throwsIOException() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("too-much.txt");
        entry.setSize(5);
        tos.putArchiveEntry(entry);
        try {
            tos.write(new byte[10], 0, 10);
            fail("expected IOException for exceeding entry size");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("exceeds size"));
        }
    }

    // Covers: write() of exactly one full record is written directly (no assembly buffering needed)
    @Test
    public void testWrite_exactlyOneFullRecord_doesNotThrow() throws Throwable {
        int recSize = tos.getRecordSize();
        TarArchiveEntry entry = new TarArchiveEntry("one-record.bin");
        entry.setSize(recSize);
        tos.putArchiveEntry(entry);
        tos.write(new byte[recSize], 0, recSize);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: multiple small write() calls get assembled into one record without exception
    @Test
    public void testWrite_multipleSmallChunksAssembled_doesNotThrow() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("assembled.bin");
        entry.setSize(10);
        tos.putArchiveEntry(entry);
        tos.write(new byte[3], 0, 3);
        tos.write(new byte[7], 0, 7);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: write() larger than one record (direct write of full records + remainder assembled)
    @Test
    public void testWrite_largerThanOneRecord_doesNotThrow() throws Throwable {
        int recSize = tos.getRecordSize();
        int size = recSize + 88;
        TarArchiveEntry entry = new TarArchiveEntry("big-chunk.bin");
        entry.setSize(size);
        tos.putArchiveEntry(entry);
        tos.write(new byte[size], 0, size);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: write() of zero length is a no-op and does not throw for an empty entry
    @Test
    public void testWrite_zeroLength_doesNotThrow() throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry("empty.txt");
        tos.putArchiveEntry(entry);
        tos.write(new byte[0], 0, 0);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: writePaxHeaders() called directly writes and closes its own sub-entry, leaving stream consistent
    @Test
    public void testWritePaxHeaders_directCall_doesNotThrowAndAllowsFinish() throws Throwable {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("comment", "hello-world");
        tos.writePaxHeaders("pax-target.txt", headers);
        tos.finish();
        assertTrue(tos.getBytesWritten() > 0);
    }

    // Covers: flush() does not alter the number of bytes already written
    @Test
    public void testFlush_doesNotChangeBytesWritten() throws Throwable {
        long before = tos.getBytesWritten();
        tos.flush();
        assertEquals(before, tos.getBytesWritten());
    }

    // Covers: createArchiveEntry(File, String) returns a TarArchiveEntry with the requested name
    @Test
    public void testCreateArchiveEntry_returnsEntryWithRequestedName() throws Throwable {
        File f = new File("nonexistent_claude_test_file_xyz.txt");
        ArchiveEntry created = tos.createArchiveEntry(f, "requested-name.txt");
        TarArchiveEntry entry = (TarArchiveEntry) created;
        assertEquals("requested-name.txt", entry.getName());
    }

    // Covers: createArchiveEntry() after finish() throws IOException "already been finished"
    @Test
    public void testCreateArchiveEntry_afterFinish_throwsIOException() throws Throwable {
        tos.finish();
        File f = new File("nonexistent_claude_test_file_xyz2.txt");
        try {
            tos.createArchiveEntry(f, "late-entry.txt");
            fail("expected IOException after finish()");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }
}
