package org.apache.commons.compress.archivers.tar;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.junit.Before;
import org.junit.Test;

public class TarArchiveOutputStreamClaudeTest {

    private ByteArrayOutputStream baos;
    private TarArchiveOutputStream tos;

    @Before
    public void setUp() throws Throwable {
        baos = new ByteArrayOutputStream();
        tos = new TarArchiveOutputStream(baos);
    }

    private TarArchiveEntry fileEntry(String name, long size) throws Throwable {
        TarArchiveEntry entry = new TarArchiveEntry(name, TarConstants.LF_GNUTYPE_LONGNAME);
        entry.setSize(size);
        return entry;
    }

    // single-arg constructor delegates to default record size
    @Test
    public void testConstructor_singleArg_usesDefaultRecordSize() throws Throwable {
        assertEquals(TarBuffer.DEFAULT_RCDSIZE, tos.getRecordSize());
    }

    // two-arg constructor (custom blockSize) still uses default record size
    @Test
    public void testConstructor_blockSizeArg_usesDefaultRecordSize() throws Throwable {
        TarArchiveOutputStream t2 = new TarArchiveOutputStream(baos, 2048);
        assertEquals(TarBuffer.DEFAULT_RCDSIZE, t2.getRecordSize());
    }

    // three-arg constructor honors the explicit record size argument
    @Test
    public void testConstructor_blockAndRecordSizeArgs_usesGivenRecordSize() throws Throwable {
        TarArchiveOutputStream t2 = new TarArchiveOutputStream(baos, 1024, 256);
        assertEquals(256, t2.getRecordSize());
    }

    // getRecordSize reflects a different configured custom size
    @Test
    public void testGetRecordSize_customSize_returnsConfiguredValue() throws Throwable {
        TarArchiveOutputStream t2 = new TarArchiveOutputStream(baos, 512, 512);
        assertEquals(512, t2.getRecordSize());
    }

    // putArchiveEntry with a short name writes a header and allows a matching close
    @Test
    public void testPutArchiveEntry_shortName_noExceptionAndCloseSucceeds() throws Throwable {
        TarArchiveEntry entry = fileEntry("short.txt", 0);
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
    }

    // name length strictly below NAMELEN does not trigger long-name handling
    @Test
    public void testPutArchiveEntry_nameLengthJustBelowLimit_noException() throws Throwable {
        int len = TarConstants.NAMELEN - 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append('x');
        }
        TarArchiveEntry entry = fileEntry(sb.toString(), 0);
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
    }

    // name length exactly equal to NAMELEN triggers long-name handling (>= boundary)
    @Test
    public void testPutArchiveEntry_nameLengthExactlyAtLimit_throwsRuntimeException() throws Throwable {
        int len = TarConstants.NAMELEN;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append('y');
        }
        TarArchiveEntry entry = fileEntry(sb.toString(), 0);
        try {
            tos.putArchiveEntry(entry);
            fail("expected RuntimeException at boundary length");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("too long"));
        }
    }

    // default LONGFILE_ERROR mode rejects long names with a RuntimeException
    @Test
    public void testPutArchiveEntry_longNameDefaultMode_throwsRuntimeException() throws Throwable {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append('a');
        }
        TarArchiveEntry entry = fileEntry(sb.toString(), 0);
        try {
            tos.putArchiveEntry(entry);
            fail("expected RuntimeException for too-long file name");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("too long"));
        }
    }

    // LONGFILE_TRUNCATE mode allows long names without throwing
    @Test
    public void testPutArchiveEntry_longNameTruncateMode_noException() throws Throwable {
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_TRUNCATE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append('b');
        }
        TarArchiveEntry entry = fileEntry(sb.toString(), 0);
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
    }

    // LONGFILE_GNU mode writes an internal long-link entry then the real entry, no exception
    @Test
    public void testPutArchiveEntry_longNameGnuMode_noException() throws Throwable {
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append('c');
        }
        TarArchiveEntry entry = fileEntry(sb.toString(), 0);
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
    }

    // explicit LONGFILE_ERROR constant behaves the same as the default mode
    @Test
    public void testPutArchiveEntry_longNameExplicitErrorMode_throwsRuntimeException() throws Throwable {
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            sb.append('d');
        }
        TarArchiveEntry entry = fileEntry(sb.toString(), 0);
        try {
            tos.putArchiveEntry(entry);
            fail("expected RuntimeException");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("too long"));
        }
    }

    // closeArchiveEntry throws IOException when fewer bytes were written than declared size
    @Test
    public void testCloseArchiveEntry_bytesLessThanSize_throwsIOException() throws Throwable {
        TarArchiveEntry entry = fileEntry("incomplete.txt", 10);
        tos.putArchiveEntry(entry);
        try {
            tos.closeArchiveEntry();
            fail("expected IOException for incomplete entry");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("before"));
        }
    }

    // closeArchiveEntry succeeds when exactly the declared number of bytes were written
    @Test
    public void testCloseArchiveEntry_bytesEqualSize_noException() throws Throwable {
        byte[] data = new byte[20];
        TarArchiveEntry entry = fileEntry("exact.txt", 20);
        tos.putArchiveEntry(entry);
        tos.write(data, 0, 20);
        tos.closeArchiveEntry();
    }

    // closeArchiveEntry pads and flushes a leftover partially assembled record
    @Test
    public void testCloseArchiveEntry_partialAssembledRecord_padsAndWrites() throws Throwable {
        TarArchiveOutputStream t2 = new TarArchiveOutputStream(baos, 100, 100);
        byte[] data = new byte[30];
        TarArchiveEntry entry = fileEntry("partial.txt", 30);
        t2.putArchiveEntry(entry);
        t2.write(data, 0, 30);
        t2.closeArchiveEntry();
        t2.close();
        assertTrue(baos.size() > 0);
    }

    // write throws IOException when the requested bytes would exceed the entry's declared size
    @Test
    public void testWrite_exceedsDeclaredSize_throwsIOException() throws Throwable {
        TarArchiveEntry entry = fileEntry("small.txt", 5);
        tos.putArchiveEntry(entry);
        byte[] data = new byte[10];
        try {
            tos.write(data, 0, 10);
            fail("expected IOException for exceeding declared size");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("exceeds"));
        }
    }

    // a zero-length write is a no-op and does not throw
    @Test
    public void testWrite_zeroLength_noException() throws Throwable {
        TarArchiveEntry entry = fileEntry("empty.txt", 0);
        tos.putArchiveEntry(entry);
        tos.write(new byte[0], 0, 0);
        tos.closeArchiveEntry();
    }

    // small chunks below record size accumulate in the assembly buffer without error
    @Test
    public void testWrite_smallChunksAccumulateInAssemblyBuffer_noException() throws Throwable {
        TarArchiveOutputStream t2 = new TarArchiveOutputStream(baos, 10, 10);
        TarArchiveEntry entry = fileEntry("assemble.txt", 6);
        t2.putArchiveEntry(entry);
        t2.write(new byte[]{1, 2, 3}, 0, 3);
        t2.write(new byte[]{4, 5, 6}, 0, 3);
        t2.closeArchiveEntry();
    }

    // combined assembled data that crosses the record boundary flushes a full record first
    @Test
    public void testWrite_assemblyBufferCrossesRecordBoundary_noException() throws Throwable {
        TarArchiveOutputStream t2 = new TarArchiveOutputStream(baos, 10, 10);
        TarArchiveEntry entry = fileEntry("cross.txt", 12);
        t2.putArchiveEntry(entry);
        t2.write(new byte[]{1, 2, 3, 4, 5, 6}, 0, 6);
        t2.write(new byte[]{7, 8, 9, 10, 11, 12}, 0, 6);
        t2.closeArchiveEntry();
    }

    // a single write exactly matching the record size is written directly (no leftover)
    @Test
    public void testWrite_exactFullRecord_writesDirectly() throws Throwable {
        TarArchiveOutputStream t2 = new TarArchiveOutputStream(baos, 10, 10);
        TarArchiveEntry entry = fileEntry("fullrecord.txt", 10);
        t2.putArchiveEntry(entry);
        t2.write(new byte[10], 0, 10);
        t2.closeArchiveEntry();
    }

    // a single write spanning several full records runs the writing loop more than once
    @Test
    public void testWrite_multipleFullRecordsInOneCall_noException() throws Throwable {
        TarArchiveOutputStream t2 = new TarArchiveOutputStream(baos, 10, 10);
        TarArchiveEntry entry = fileEntry("multi.txt", 20);
        t2.putArchiveEntry(entry);
        t2.write(new byte[20], 0, 20);
        t2.closeArchiveEntry();
    }

    // closing with no entries written still produces exactly two EOF records of output
    @Test
    public void testFinish_viaClose_writesTwoEOFRecords() throws Throwable {
        TarArchiveOutputStream t2 = new TarArchiveOutputStream(baos, 512, 512);
        t2.close();
        assertEquals(1024, baos.size());
    }

    // close() on a freshly created stream flushes the EOF markers to the underlying stream
    @Test
    public void testClose_producesNonEmptyOutput() throws Throwable {
        tos.close();
        assertTrue(baos.size() > 0);
    }

    // close() is idempotent: a second call does not throw or duplicate the finish logic
    @Test
    public void testClose_calledTwice_noException() throws Throwable {
        tos.close();
        int sizeAfterFirst = baos.size();
        tos.close();
        assertEquals(sizeAfterFirst, baos.size());
    }

    // flush() delegates to the underlying stream without altering its buffered content
    @Test
    public void testFlush_delegatesToUnderlyingStream_noException() throws Throwable {
        tos.flush();
        assertEquals(0, baos.size());
    }

    // createArchiveEntry returns a TarArchiveEntry carrying the explicitly supplied entry name
    @Test
    public void testCreateArchiveEntry_returnsEntryWithGivenName() throws Throwable {
        File f = new File("does-not-exist-claude-test.tmp");
        ArchiveEntry entry = tos.createArchiveEntry(f, "custom-entry-name.txt");
        assertTrue(entry instanceof TarArchiveEntry);
        assertEquals("custom-entry-name.txt", entry.getName());
    }

    // the entry returned by createArchiveEntry can be put and closed through this stream
    @Test
    public void testCreateArchiveEntry_resultUsableInPutArchiveEntry() throws Throwable {
        File f = new File("does-not-exist-claude-test-2.tmp");
        ArchiveEntry entry = tos.createArchiveEntry(f, "usable.txt");
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
    }
}
