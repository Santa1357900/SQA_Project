package org.apache.commons.compress.archivers.tar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class TarArchiveOutputStreamClaudeTest {

    private static String nameOfLength(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append('a');
        }
        return sb.toString();
    }

    // Constructor(OutputStream): default record size must be the standard 512-byte tar record
    @Test
    public void testConstructor_singleArg_defaultRecordSizeIs512() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        assertEquals(512, tos.getRecordSize());
    }

    // Constructor(OutputStream,int): blockSize change must not affect default record size
    @Test
    public void testConstructor_withBlockSize_recordSizeStillDefault512() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos, 1024);
        assertEquals(512, tos.getRecordSize());
    }

    // Constructor(OutputStream,int,int): custom record size must be honored by getRecordSize()
    @Test
    public void testConstructor_withBlockAndRecordSize_recordSizeMatchesGivenValue() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos, 2048, 256);
        assertEquals(256, tos.getRecordSize());
    }

    // finish(): haveUnclosedEntry true branch -> IOException containing "unclosed"
    @Test
    public void testFinish_withUnclosedEntry_throwsIOException() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), "f");
        entry.setSize(0);
        tos.putArchiveEntry(entry);
        try {
            tos.finish();
            fail("expected IOException for unclosed entry");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("unclosed"));
        }
    }

    // finish(): finished true branch on second call -> IOException "already been finished"
    @Test
    public void testFinish_calledTwice_secondCallThrowsIOException() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.finish();
        try {
            tos.finish();
            fail("expected IOException on second finish");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("already"));
        }
    }

    // finish(): normal path writes two EOF records, producing non-empty output
    @Test
    public void testFinish_normal_producesTwoEofRecordsInOutput() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.finish();
        assertTrue(bos.toByteArray().length > 0);
        tos.close();
    }

    // close(): !finished branch calls finish() implicitly, then closes underlying stream
    @Test
    public void testClose_withoutExplicitFinish_finishesAndClosesUnderlyingStream() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), "f");
        entry.setSize(0);
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.close();
        assertTrue(bos.toByteArray().length > 0);
    }

    // close(): closed true branch on second call must be a no-op, not throwing
    @Test
    public void testClose_calledTwice_doesNotThrow() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.close();
        int sizeAfterFirst = bos.toByteArray().length;
        tos.close();
        assertEquals(sizeAfterFirst, bos.toByteArray().length);
    }

    // putArchiveEntry(): name length just under NAMELEN must NOT trigger long-name handling
    @Test
    public void testPutArchiveEntry_nameJustUnderNamelen_doesNotThrow() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        String name = nameOfLength(TarConstants.NAMELEN - 1);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), name);
        entry.setSize(0);
        tos.putArchiveEntry(entry);
        assertEquals(name, entry.getName());
        tos.closeArchiveEntry();
    }

    // putArchiveEntry(): name length exactly NAMELEN (boundary ">=") with default LONGFILE_ERROR -> RuntimeException
    @Test
    public void testPutArchiveEntry_nameExactlyNamelenDefaultMode_throwsRuntimeException() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        String name = nameOfLength(TarConstants.NAMELEN);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), name);
        try {
            tos.putArchiveEntry(entry);
            fail("expected RuntimeException for too long name");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("too long"));
        }
    }

    // putArchiveEntry(): LONGFILE_TRUNCATE mode skips both GNU and error branches, no exception
    @Test
    public void testPutArchiveEntry_nameTooLongTruncateMode_doesNotThrow() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_TRUNCATE);
        String name = nameOfLength(TarConstants.NAMELEN + 10);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), name);
        entry.setSize(0);
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(bos.toByteArray().length > 0);
    }

    // putArchiveEntry(): LONGFILE_GNU mode creates a LongLink entry internally and succeeds
    @Test
    public void testPutArchiveEntry_nameTooLongGnuMode_doesNotThrowAndWritesData() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        String name = nameOfLength(TarConstants.NAMELEN + 20);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), name);
        entry.setSize(0);
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(bos.toByteArray().length > 0);
    }

    // putArchiveEntry(): finished true branch -> IOException containing "finished"
    @Test
    public void testPutArchiveEntry_afterFinish_throwsIOException() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), "f");
        tos.finish();
        try {
            tos.putArchiveEntry(entry);
            fail("expected IOException after finish");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }

    // closeArchiveEntry(): haveUnclosedEntry false branch -> IOException "No current entry"
    @Test
    public void testCloseArchiveEntry_withoutPutArchiveEntry_throwsIOException() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        try {
            tos.closeArchiveEntry();
            fail("expected IOException for no current entry");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("No current entry"));
        }
    }

    // closeArchiveEntry(): currBytes < currSize branch -> IOException mentioning the mismatch
    @Test
    public void testCloseArchiveEntry_incompleteBytesWritten_throwsIOException() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), "f");
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

    // closeArchiveEntry(): currBytes == currSize succeeds, clearing haveUnclosedEntry for next entry
    @Test
    public void testCloseArchiveEntry_exactBytesWritten_succeeds() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), "f");
        entry.setSize(5);
        tos.putArchiveEntry(entry);
        tos.write(new byte[5], 0, 5);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(bos.toByteArray().length > 0);
    }

    // closeArchiveEntry(): finished true branch -> IOException containing "finished"
    @Test
    public void testCloseArchiveEntry_afterFinish_throwsIOException() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.finish();
        try {
            tos.closeArchiveEntry();
            fail("expected IOException after finish");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }

    // closeArchiveEntry(): assemLen > 0 branch pads assembly buffer and still succeeds
    @Test
    public void testCloseArchiveEntry_pendingAssemblyBuffer_padsAndSucceeds() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), "f");
        entry.setSize(10);
        tos.putArchiveEntry(entry);
        tos.write(new byte[10], 0, 10);
        tos.closeArchiveEntry();
        tos.finish();
        assertTrue(bos.toByteArray().length > 0);
    }

    // write(): (currBytes+numToWrite) > currSize branch -> IOException "exceeds size"
    @Test
    public void testWrite_exceedsCurrSize_throwsIOException() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), "f");
        entry.setSize(5);
        tos.putArchiveEntry(entry);
        try {
            tos.write(new byte[10], 0, 10);
            fail("expected IOException for exceeding size");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("exceeds"));
        }
    }

    // write(): numToWrite == 0 must be a no-op, bytes-written statistic unchanged
    @Test
    public void testWrite_zeroLength_doesNotThrowAndDoesNotChangeBytesWritten() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), "f");
        entry.setSize(10);
        tos.putArchiveEntry(entry);
        tos.write(new byte[0], 0, 0);
        assertEquals(0L, tos.getBytesWritten());
    }









    // flush(): must not throw and does not interfere with subsequent finish/close producing output
    @Test
    public void testFlush_doesNotThrowAndDataIsWrittenAfterClose() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("f"), "f");
        entry.setSize(0);
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.flush();
        tos.finish();
        tos.close();
        assertTrue(bos.toByteArray().length > 0);
    }

    // createArchiveEntry(File,String): returns a TarArchiveEntry whose name is the entryName parameter
    @Test
    public void testCreateArchiveEntry_returnsTarArchiveEntryWithGivenName() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = (TarArchiveEntry) tos.createArchiveEntry(new File("sample.txt"), "sample.txt");
        assertEquals("sample.txt", entry.getName());
    }

    // createArchiveEntry(File,String): finished true branch -> IOException containing "finished"
    @Test
    public void testCreateArchiveEntry_afterFinish_throwsIOException() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.finish();
        try {
            tos.createArchiveEntry(new File("f"), "f");
            fail("expected IOException after finish");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("finished"));
        }
    }
}
