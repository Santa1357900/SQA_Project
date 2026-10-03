package org.apache.commons.compress.archivers.cpio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CpioArchiveOutputStreamClaudeTest {

    private ByteArrayOutputStream baos;

    @Before
    public void setUp() throws Throwable {
        baos = new ByteArrayOutputStream();
    }

    private CpioArchiveEntry newEntry(String name, long size) {
        CpioArchiveEntry e = new CpioArchiveEntry();
        e.setName(name);
        e.setSize(size);
        e.setTime(0L);
        return e;
    }

    // covers setFormat() default switch branch -> IllegalArgumentException
    @Test
    public void testConstructor_invalidFormat_throwsIllegalArgumentException() throws Throwable {
        try {
            new CpioArchiveOutputStream(baos, (short) 999);
            fail("expected IllegalArgumentException for unknown format");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // covers setFormat(FORMAT_NEW) + putNextEntry's format-default branch
    @Test
    public void testPutNextEntry_formatNew_defaultApplied() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            CpioArchiveEntry e = newEntry("a", 0L);
            cos.putNextEntry(e);
            assertEquals((long) CpioConstants.FORMAT_NEW, (long) e.getFormat());
        } finally {
            cos.close();
        }
    }

    // covers setFormat(FORMAT_NEW_CRC) + putNextEntry's format-default branch
    @Test
    public void testPutNextEntry_formatNewCrc_defaultApplied() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW_CRC);
        try {
            CpioArchiveEntry e = newEntry("a", 0L);
            cos.putNextEntry(e);
            assertEquals((long) CpioConstants.FORMAT_NEW_CRC, (long) e.getFormat());
        } finally {
            cos.close();
        }
    }

    // covers setFormat(FORMAT_OLD_ASCII) + putNextEntry's format-default branch
    @Test
    public void testPutNextEntry_formatOldAscii_defaultApplied() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_OLD_ASCII);
        try {
            CpioArchiveEntry e = newEntry("a", 0L);
            cos.putNextEntry(e);
            assertEquals((long) CpioConstants.FORMAT_OLD_ASCII, (long) e.getFormat());
        } finally {
            cos.close();
        }
    }

    // covers setFormat(FORMAT_OLD_BINARY) + putNextEntry's format-default branch
    @Test
    public void testPutNextEntry_formatOldBinary_defaultApplied() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_OLD_BINARY);
        try {
            CpioArchiveEntry e = newEntry("a", 0L);
            cos.putNextEntry(e);
            assertEquals((long) CpioConstants.FORMAT_OLD_BINARY, (long) e.getFormat());
        } finally {
            cos.close();
        }
    }

    // covers putNextEntry: if (e.getTime() == -1) setTime(currentTimeMillis())
    @Test
    public void testPutNextEntry_timeUnset_autoSetToNonNegativeOne() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            CpioArchiveEntry e = new CpioArchiveEntry();
            e.setName("t");
            e.setSize(0L);
            cos.putNextEntry(e);
            assertTrue(e.getTime() != -1L);
        } finally {
            cos.close();
        }
    }

    // covers putNextEntry: names.put(...) != null -> duplicate entry branch
    @Test
    public void testPutNextEntry_duplicateName_throwsIOException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.putNextEntry(newEntry("dup", 0L));
            cos.closeArchiveEntry();
            try {
                cos.putNextEntry(newEntry("dup", 0L));
                fail("expected IOException for duplicate entry name");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("duplicate"));
            }
        } finally {
            cos.close();
        }
    }

    // covers putNextEntry: if (this.cpioEntry != null) closeArchiveEntry() auto-close
    @Test
    public void testPutNextEntry_autoClosesPreviousEntry_succeeds() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.putNextEntry(newEntry("a", 0L));
            cos.putNextEntry(newEntry("b", 0L));
            cos.closeArchiveEntry();
            assertTrue(baos.size() > 0);
        } finally {
            cos.close();
        }
    }

    // covers auto-close path detecting size mismatch of previous entry
    @Test
    public void testPutNextEntry_autoCloseDetectsSizeMismatch_throwsIOException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.putNextEntry(newEntry("a", 5L));
            try {
                cos.putNextEntry(newEntry("b", 0L));
                fail("expected IOException due to unwritten declared size");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("invalid entry size"));
            }
        } finally {
            cos.close();
        }
    }

    // covers putNextEntry's ensureOpen() throw after close()
    @Test
    public void testPutNextEntry_afterStreamClosed_throwsIOException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        cos.close();
        try {
            cos.putNextEntry(newEntry("a", 0L));
            fail("expected IOException: Stream closed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // covers closeArchiveEntry's ensureOpen() throw after close()
    @Test
    public void testCloseArchiveEntry_afterStreamClosed_throwsIOException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        cos.close();
        try {
            cos.closeArchiveEntry();
            fail("expected IOException: Stream closed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // covers closeArchiveEntry: cpioEntry.getSize() != written branch
    @Test
    public void testCloseArchiveEntry_sizeMismatch_throwsIOException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.putNextEntry(newEntry("a", 5L));
            cos.write(new byte[] {1, 2, 3}, 0, 3);
            try {
                cos.closeArchiveEntry();
                fail("expected IOException for size mismatch");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("invalid entry size"));
            }
        } finally {
            cos.close();
        }
    }

    // covers closeArchiveEntry: crc != cpioEntry.getChksum() for FORMAT_NEW_CRC
    @Test
    public void testCloseArchiveEntry_crcMismatchNewCrcFormat_throwsIOException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW_CRC);
        try {
            cos.putNextEntry(newEntry("a", 1L));
            cos.write(new byte[] {5}, 0, 1);
            try {
                cos.closeArchiveEntry();
                fail("expected IOException: CRC Error");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("CRC"));
            }
        } finally {
            cos.close();
        }
    }

    // covers closeArchiveEntry: crc == cpioEntry.getChksum() (both 0) success path
    @Test
    public void testCloseArchiveEntry_crcMatchNewCrcFormat_noException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW_CRC);
        try {
            cos.putNextEntry(newEntry("a", 2L));
            cos.write(new byte[] {0, 0}, 0, 2);
            cos.closeArchiveEntry();
            assertTrue(baos.size() > 0);
        } finally {
            cos.close();
        }
    }

    // covers closeArchiveEntry pad(size,4) for FORMAT_NEW per cpio new-format spec
    @Test
    public void testCloseArchiveEntry_paddingNewFormat_pads3BytesForSize5() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);
        try {
            cos.putNextEntry(newEntry("f", 5L));
            cos.write(new byte[] {1, 2, 3, 4, 5}, 0, 5);
            int afterData = baos.size();
            cos.closeArchiveEntry();
            assertEquals(3, baos.size() - afterData);
        } finally {
            cos.close();
        }
    }

    // covers closeArchiveEntry pad(size,4) for FORMAT_NEW_CRC per cpio newc spec
    @Test
    public void testCloseArchiveEntry_paddingNewCrcFormat_pads3BytesForSize5() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW_CRC);
        try {
            cos.putNextEntry(newEntry("f", 5L));
            cos.write(new byte[] {0, 0, 0, 0, 0}, 0, 5);
            int afterData = baos.size();
            cos.closeArchiveEntry();
            assertEquals(3, baos.size() - afterData);
        } finally {
            cos.close();
        }
    }

    // covers closeArchiveEntry pad(size,2) for FORMAT_OLD_BINARY per cpio old-binary spec
    @Test
    public void testCloseArchiveEntry_paddingOldBinaryFormat_pads1ByteForSize5() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_OLD_BINARY);
        try {
            cos.putNextEntry(newEntry("f", 5L));
            cos.write(new byte[] {1, 2, 3, 4, 5}, 0, 5);
            int afterData = baos.size();
            cos.closeArchiveEntry();
            assertEquals(1, baos.size() - afterData);
        } finally {
            cos.close();
        }
    }

    // covers closeArchiveEntry: FORMAT_OLD_ASCII must not match any padding branch
    @Test
    public void testCloseArchiveEntry_oldAsciiFormat_noPaddingAdded() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_OLD_ASCII);
        try {
            cos.putNextEntry(newEntry("f", 5L));
            cos.write(new byte[] {1, 2, 3, 4, 5}, 0, 5);
            int afterData = baos.size();
            cos.closeArchiveEntry();
            assertEquals(0, baos.size() - afterData);
        } finally {
            cos.close();
        }
    }

    // covers write(): off < 0 branch -> IndexOutOfBoundsException
    @Test
    public void testWrite_offsetNegative_throwsIndexOutOfBoundsException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.write(new byte[] {1, 2, 3}, -1, 1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            // ok
        } finally {
            cos.close();
        }
    }

    // covers write(): len < 0 branch -> IndexOutOfBoundsException
    @Test
    public void testWrite_lenNegative_throwsIndexOutOfBoundsException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.write(new byte[] {1, 2, 3}, 0, -1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            // ok
        } finally {
            cos.close();
        }
    }

    // covers write(): off > b.length - len branch -> IndexOutOfBoundsException
    @Test
    public void testWrite_offsetPlusLenExceedsArrayLength_throwsIndexOutOfBoundsException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.write(new byte[] {1, 2, 3}, 2, 3);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            // ok
        } finally {
            cos.close();
        }
    }

    // covers write(): len == 0 early-return, before the "no current entry" check
    @Test
    public void testWrite_lenZeroWithoutCurrentEntry_noExceptionNoBytesWritten() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.write(new byte[0], 0, 0);
            assertEquals(0, baos.size());
        } finally {
            cos.close();
        }
    }

    // covers write(): this.cpioEntry == null branch -> IOException
    @Test
    public void testWrite_noCurrentEntry_throwsIOException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.write(new byte[] {1}, 0, 1);
            fail("expected IOException: no current CPIO entry");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no current"));
        } finally {
            cos.close();
        }
    }

    // covers write(): written + len > cpioEntry.getSize() branch -> IOException
    @Test
    public void testWrite_pastEndOfEntry_throwsIOException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.putNextEntry(newEntry("a", 2L));
            cos.write(new byte[] {1, 2, 3}, 0, 3);
            fail("expected IOException: attempt to write past end");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("past end"));
        } finally {
            cos.close();
        }
    }

    // covers write(): normal success path writes bytes and tracks written
    @Test
    public void testWrite_withinBounds_dataWrittenAndSizeTracked() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.putNextEntry(newEntry("a", 3L));
            int beforeWrite = baos.size();
            cos.write(new byte[] {10, 20, 30}, 0, 3);
            assertEquals(3, baos.size() - beforeWrite);
            cos.closeArchiveEntry();
        } finally {
            cos.close();
        }
    }

    // covers write(): ensureOpen() throw after close()
    @Test
    public void testWrite_afterStreamClosed_throwsIOException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        cos.close();
        try {
            cos.write(new byte[] {1}, 0, 1);
            fail("expected IOException: Stream closed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // covers write(int): direct delegate to underlying stream
    @Test
    public void testWriteInt_writesSingleByteDirectlyToOutput() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.write(65);
            assertArrayEquals(new byte[] {65}, baos.toByteArray());
        } finally {
            cos.close();
        }
    }

    // covers putArchiveEntry(ArchiveEntry) delegating to putNextEntry(CpioArchiveEntry)
    @Test
    public void testPutArchiveEntry_delegatesToPutNextEntry_duplicateThrowsIOException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.putArchiveEntry(newEntry("x", 0L));
            cos.closeArchiveEntry();
            try {
                cos.putArchiveEntry(newEntry("x", 0L));
                fail("expected IOException for duplicate entry name");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("duplicate"));
            }
        } finally {
            cos.close();
        }
    }

    // covers finish(): writes TRAILER!!! header via writeHeader + closeArchiveEntry
    @Test
    public void testFinish_writesTrailerRecord_outputGrows() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            int before = baos.size();
            cos.finish();
            assertTrue(baos.size() > before);
        } finally {
            cos.close();
        }
    }

    // covers finish(): "if (this.finished) return;" guard must stop a 2nd trailer write
    @Test
    public void testFinish_calledTwice_secondCallIsNoOp() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        try {
            cos.finish();
            int afterFirst = baos.size();
            cos.finish();
            int afterSecond = baos.size();
            assertEquals(afterFirst, afterSecond);
        } finally {
            cos.close();
        }
    }

    // covers finish(): ensureOpen() throw after close()
    @Test
    public void testFinish_afterStreamClosed_throwsIOException() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        cos.close();
        try {
            cos.finish();
            fail("expected IOException: Stream closed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // covers close(): if (!this.closed) guard prevents double super.close()
    @Test
    public void testClose_calledTwice_noExceptionAndStaysClosed() throws Throwable {
        CpioArchiveOutputStream cos = new CpioArchiveOutputStream(baos);
        cos.close();
        cos.close();
        try {
            cos.write(new byte[] {1}, 0, 1);
            fail("expected IOException after close");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }
}
