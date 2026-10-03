package org.apache.commons.compress.archivers.cpio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CpioArchiveInputStreamClaudeTest {

    private byte[] singleEntryArchive;

    @Before
    public void setUp() throws Throwable {
        byte[] entry = buildOldAsciiEntry(1L, "hello.txt", bytes("ABCDE"));
        byte[] trailer = buildOldAsciiEntry(0L, CpioConstants.CPIO_TRAILER, new byte[0]);
        singleEntryArchive = concat(entry, trailer);
    }

    private byte[] bytes(String s) throws IOException {
        return s.getBytes("US-ASCII");
    }

    private byte[] concat(byte[] a, byte[] b) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(a);
        bos.write(b);
        return bos.toByteArray();
    }

    private byte[] concat3(byte[] a, byte[] b, byte[] c) throws IOException {
        return concat(concat(a, b), c);
    }

    private byte[] buildOldAsciiEntry(long mode, String name, byte[] data) throws IOException {
        long namesize = name.length() + 1;
        long filesize = data.length;
        StringBuilder sb = new StringBuilder();
        sb.append("070707");
        sb.append(String.format(Locale.ROOT, "%06o", 0L));
        sb.append(String.format(Locale.ROOT, "%06o", 1L));
        sb.append(String.format(Locale.ROOT, "%06o", mode));
        sb.append(String.format(Locale.ROOT, "%06o", 0L));
        sb.append(String.format(Locale.ROOT, "%06o", 0L));
        sb.append(String.format(Locale.ROOT, "%06o", 1L));
        sb.append(String.format(Locale.ROOT, "%06o", 0L));
        sb.append(String.format(Locale.ROOT, "%011o", 0L));
        sb.append(String.format(Locale.ROOT, "%06o", namesize));
        sb.append(String.format(Locale.ROOT, "%011o", filesize));
        sb.append(name);
        sb.append('\0');
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(bytes(sb.toString()));
        bos.write(data);
        return bos.toByteArray();
    }

    private byte[] buildNewEntry(boolean hasCrc, long mode, String name, byte[] data, long chksum)
            throws IOException {
        int namesize = name.length() + 1;
        int filesize = data.length;
        StringBuilder sb = new StringBuilder();
        sb.append(hasCrc ? "070702" : "070701");
        sb.append(String.format(Locale.ROOT, "%08x", 1L));
        sb.append(String.format(Locale.ROOT, "%08x", mode));
        sb.append(String.format(Locale.ROOT, "%08x", 0L));
        sb.append(String.format(Locale.ROOT, "%08x", 0L));
        sb.append(String.format(Locale.ROOT, "%08x", 1L));
        sb.append(String.format(Locale.ROOT, "%08x", 0L));
        sb.append(String.format(Locale.ROOT, "%08x", (long) filesize));
        sb.append(String.format(Locale.ROOT, "%08x", 0L));
        sb.append(String.format(Locale.ROOT, "%08x", 0L));
        sb.append(String.format(Locale.ROOT, "%08x", 0L));
        sb.append(String.format(Locale.ROOT, "%08x", 0L));
        sb.append(String.format(Locale.ROOT, "%08x", (long) namesize));
        sb.append(String.format(Locale.ROOT, "%08x", chksum));
        sb.append(name);
        sb.append('\0');
        int headerLen = 6 + 13 * 8 + namesize;
        int headerPad = (4 - (headerLen % 4)) % 4;
        int dataPad = (4 - (filesize % 4)) % 4;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(bytes(sb.toString()));
        for (int i = 0; i < headerPad; i++) {
            bos.write(0);
        }
        bos.write(data);
        for (int i = 0; i < dataPad; i++) {
            bos.write(0);
        }
        return bos.toByteArray();
    }

    private CpioArchiveInputStream newStream(byte[] data) {
        return new CpioArchiveInputStream(new ByteArrayInputStream(data));
    }

    // available(): before any entry read, must always return 1 per javadoc
    @Test
    public void testAvailable_beforeAnyEntryRead_returnsOne() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            assertEquals(1, in.available());
        } finally {
            in.close();
        }
    }

    // available(): after entry fully consumed, entryEOF true -> 0
    @Test
    public void testAvailable_afterEntryFullyRead_returnsZero() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            in.getNextCPIOEntry();
            byte[] buf = new byte[5];
            in.read(buf, 0, 5);
            in.read(buf, 0, 5);
            assertEquals(0, in.available());
        } finally {
            in.close();
        }
    }

    // available(): ensureOpen() throws after close()
    @Test
    public void testAvailable_afterClose_throwsIOException() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        in.close();
        try {
            in.available();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // constructor with explicit blockSize branch still parses entries correctly
    @Test
    public void testConstructor_withCustomBlockSize_readsEntrySuccessfully() throws Throwable {
        CpioArchiveInputStream in = new CpioArchiveInputStream(
                new ByteArrayInputStream(singleEntryArchive), 1024);
        try {
            CpioArchiveEntry e = in.getNextCPIOEntry();
            assertEquals("hello.txt", e.getName());
        } finally {
            in.close();
        }
    }

    // close(): calling twice must not throw (idempotent)
    @Test
    public void testClose_calledTwice_noExceptionThrown() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        in.close();
        in.close();
        assertTrue(true);
    }

    // read() after close(): ensureOpen() throws IOException
    @Test
    public void testClose_thenRead_throwsIOException() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        in.close();
        try {
            in.read(new byte[1], 0, 1);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // getNextCPIOEntry() after close(): ensureOpen() throws IOException
    @Test
    public void testClose_thenGetNextCPIOEntry_throwsIOException() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        in.close();
        try {
            in.getNextCPIOEntry();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // skip(long) after close(): ensureOpen() throws IOException for positive n
    @Test
    public void testClose_thenSkip_throwsIOException() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        in.close();
        try {
            in.skip(1L);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // getNextCPIOEntry(): old ascii format parsed, name/size correct
    @Test
    public void testGetNextCPIOEntry_oldAsciiEntry_returnsEntryWithCorrectNameAndSize() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            CpioArchiveEntry e = in.getNextCPIOEntry();
            assertEquals("hello.txt", e.getName());
            assertEquals(5L, e.getSize());
        } finally {
            in.close();
        }
    }

    // getNextCPIOEntry(): archive with only trailer returns null and sets EOF
    @Test
    public void testGetNextCPIOEntry_oldAsciiTrailerOnly_returnsNull() throws Throwable {
        byte[] trailerOnly = buildOldAsciiEntry(0L, CpioConstants.CPIO_TRAILER, new byte[0]);
        CpioArchiveInputStream in = newStream(trailerOnly);
        try {
            assertNull(in.getNextCPIOEntry());
        } finally {
            in.close();
        }
    }

    // getNextCPIOEntry(): two real entries then trailer -> order preserved, then null
    @Test
    public void testGetNextCPIOEntry_multipleEntriesThenTrailer_returnsInOrderThenNull() throws Throwable {
        byte[] e1 = buildOldAsciiEntry(1L, "a.txt", bytes("AA"));
        byte[] e2 = buildOldAsciiEntry(1L, "b.txt", bytes("BB"));
        byte[] tr = buildOldAsciiEntry(0L, CpioConstants.CPIO_TRAILER, new byte[0]);
        CpioArchiveInputStream in = newStream(concat3(e1, e2, tr));
        try {
            assertEquals("a.txt", in.getNextCPIOEntry().getName());
            assertEquals("b.txt", in.getNextCPIOEntry().getName());
            assertNull(in.getNextCPIOEntry());
        } finally {
            in.close();
        }
    }

    // getNextCPIOEntry(): unrecognized magic bytes -> IOException "Unknown magic"
    @Test
    public void testGetNextCPIOEntry_unknownMagic_throwsIOException() throws Throwable {
        CpioArchiveInputStream in = newStream(bytes("ABCDEF"));
        try {
            in.getNextCPIOEntry();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Unknown magic"));
        } finally {
            in.close();
        }
    }

    // getNextCPIOEntry(): old ascii mode==0 with non-trailer name -> IOException
    @Test
    public void testGetNextCPIOEntry_oldAsciiModeZeroNonTrailer_throwsIOException() throws Throwable {
        byte[] bad = buildOldAsciiEntry(0L, "nottrailer", new byte[0]);
        CpioArchiveInputStream in = newStream(bad);
        try {
            in.getNextCPIOEntry();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Mode 0"));
        } finally {
            in.close();
        }
    }

    // getNextCPIOEntry(): new format mode==0 with non-trailer name -> IOException
    @Test
    public void testGetNextCPIOEntry_newFormatModeZeroNonTrailer_throwsIOException() throws Throwable {
        byte[] bad = buildNewEntry(false, 0L, "x", new byte[0], 0L);
        CpioArchiveInputStream in = newStream(bad);
        try {
            in.getNextCPIOEntry();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Mode 0"));
        } finally {
            in.close();
        }
    }

    // getNextCPIOEntry(): new (non-CRC) format parsed with correct name/size/format
    @Test
    public void testGetNextCPIOEntry_newFormatEntry_returnsEntryWithCorrectAttributes() throws Throwable {
        byte[] data = bytes("DATA");
        byte[] arch = concat(buildNewEntry(false, 1L, "a", data, 0L),
                buildNewEntry(false, 0L, CpioConstants.CPIO_TRAILER, new byte[0], 0L));
        CpioArchiveInputStream in = newStream(arch);
        try {
            CpioArchiveEntry e = in.getNextCPIOEntry();
            assertEquals("a", e.getName());
            assertEquals(4L, e.getSize());
            assertEquals(CpioConstants.FORMAT_NEW, e.getFormat());
        } finally {
            in.close();
        }
    }

    // read()/closeEntry(): new CRC format with correct checksum does not throw
    @Test
    public void testGetNextCPIOEntry_newCrcFormatCorrectChecksum_noExceptionOnClose() throws Throwable {
        byte[] data = bytes("DATA");
        byte[] arch = concat(buildNewEntry(true, 1L, "a", data, 282L),
                buildNewEntry(true, 0L, CpioConstants.CPIO_TRAILER, new byte[0], 0L));
        CpioArchiveInputStream in = newStream(arch);
        try {
            CpioArchiveEntry e = in.getNextCPIOEntry();
            assertEquals(CpioConstants.FORMAT_NEW_CRC, e.getFormat());
            assertNull(in.getNextCPIOEntry());
        } finally {
            in.close();
        }
    }

    // read()/closeEntry(): new CRC format with wrong checksum throws "CRC Error"
    @Test
    public void testGetNextCPIOEntry_newCrcFormatWrongChecksum_throwsIOExceptionOnClose() throws Throwable {
        byte[] data = bytes("DATA");
        byte[] arch = concat(buildNewEntry(true, 1L, "a", data, 281L),
                buildNewEntry(true, 0L, CpioConstants.CPIO_TRAILER, new byte[0], 0L));
        CpioArchiveInputStream in = newStream(arch);
        try {
            in.getNextCPIOEntry();
            in.getNextCPIOEntry();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("CRC Error"));
        } finally {
            in.close();
        }
    }

    // read(): normal full-entry read returns correct content then -1 at EOF
    @Test
    public void testRead_entryData_returnsCorrectContentThenMinusOne() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            in.getNextCPIOEntry();
            byte[] buf = new byte[5];
            int n = in.read(buf, 0, 5);
            assertEquals(5, n);
            assertEquals("ABCDE", new String(buf, 0, n, "US-ASCII"));
            assertEquals(-1, in.read(buf, 0, 5));
        } finally {
            in.close();
        }
    }

    // read(): multiple sequential reads return correct partial chunks
    @Test
    public void testRead_multipleReadsAcrossEntry_returnsPartialChunksCorrectly() throws Throwable {
        byte[] arch = buildOldAsciiEntry(1L, "data.bin", bytes("HELLOWORLD"));
        CpioArchiveInputStream in = newStream(arch);
        try {
            in.getNextCPIOEntry();
            byte[] buf = new byte[10];
            int n1 = in.read(buf, 0, 4);
            assertEquals("HELL", new String(buf, 0, n1, "US-ASCII"));
            int n2 = in.read(buf, 0, 10);
            assertEquals(6, n2);
            assertEquals("OWORLD", new String(buf, 0, n2, "US-ASCII"));
            assertEquals(-1, in.read(buf, 0, 1));
        } finally {
            in.close();
        }
    }

    // read(): len==0 must return 0 regardless of entry state
    @Test
    public void testRead_zeroLength_returnsZero() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            assertEquals(0, in.read(new byte[5], 0, 0));
        } finally {
            in.close();
        }
    }

    // read(): negative offset -> IndexOutOfBoundsException
    @Test
    public void testRead_negativeOffset_throwsIndexOutOfBoundsException() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            in.read(new byte[5], -1, 1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        } finally {
            in.close();
        }
    }

    // read(): negative length -> IndexOutOfBoundsException
    @Test
    public void testRead_negativeLength_throwsIndexOutOfBoundsException() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            in.read(new byte[5], 0, -1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        } finally {
            in.close();
        }
    }

    // read(): off+len exceeding buffer length -> IndexOutOfBoundsException
    @Test
    public void testRead_offsetPlusLengthExceedsBufferLength_throwsIndexOutOfBoundsException() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            in.read(new byte[3], 1, 3);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        } finally {
            in.close();
        }
    }

    // read(): off+len exactly equal to buffer length is valid (boundary, no entry yet -> -1)
    @Test
    public void testRead_offsetPlusLengthEqualsBufferLength_returnsMinusOneWhenNoEntry() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            assertEquals(-1, in.read(new byte[5], 2, 3));
        } finally {
            in.close();
        }
    }

    // read(): before any getNextCPIOEntry() call, entry==null -> -1
    @Test
    public void testRead_beforeAnyEntryRead_returnsMinusOne() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            assertEquals(-1, in.read(new byte[4], 0, 4));
        } finally {
            in.close();
        }
    }

    // skip(long): negative value -> IllegalArgumentException
    @Test
    public void testSkip_negativeValue_throwsIllegalArgumentException() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            in.skip(-1L);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        } finally {
            in.close();
        }
    }

    // skip(long): valid positive skip advances entry read position correctly
    @Test
    public void testSkip_validValue_skipsAndAdvancesReadPosition() throws Throwable {
        byte[] arch = buildOldAsciiEntry(1L, "data.bin", bytes("HELLOWORLD"));
        CpioArchiveInputStream in = newStream(arch);
        try {
            in.getNextCPIOEntry();
            assertEquals(4L, in.skip(4L));
            byte[] buf = new byte[6];
            int n = in.read(buf, 0, 6);
            assertEquals("OWORLD", new String(buf, 0, n, "US-ASCII"));
        } finally {
            in.close();
        }
    }

    // getNextEntry(): delegates to getNextCPIOEntry() and returns same entry info
    @Test
    public void testGetNextEntry_delegatesToGetNextCPIOEntry() throws Throwable {
        CpioArchiveInputStream in = newStream(singleEntryArchive);
        try {
            CpioArchiveEntry e = in.getNextEntry();
            assertEquals("hello.txt", e.getName());
        } finally {
            in.close();
        }
    }

    // matches(): length < 6 always returns false
    @Test
    public void testMatches_lengthLessThanSix_returnsFalse() throws Throwable {
        byte[] sig = new byte[6];
        assertFalse(CpioArchiveInputStream.matches(sig, 5));
    }

    // matches(): old binary magic in normal byte order -> true
    @Test
    public void testMatches_oldBinaryMagicNormalOrder_returnsTrue() throws Throwable {
        byte[] sig = new byte[]{0x71, (byte) 0xC7, 0, 0, 0, 0};
        assertTrue(CpioArchiveInputStream.matches(sig, 6));
    }

    // matches(): old binary magic in swapped byte order -> true
    @Test
    public void testMatches_oldBinaryMagicSwappedOrder_returnsTrue() throws Throwable {
        byte[] sig = new byte[]{(byte) 0xC7, 0x71, 0, 0, 0, 0};
        assertTrue(CpioArchiveInputStream.matches(sig, 6));
    }

    // matches(): new ascii magic "070701" -> true
    @Test
    public void testMatches_newAsciiMagic_returnsTrue() throws Throwable {
        byte[] sig = bytes("070701");
        assertTrue(CpioArchiveInputStream.matches(sig, 6));
    }

    // matches(): new crc ascii magic "070702" -> true
    @Test
    public void testMatches_newCrcAsciiMagic_returnsTrue() throws Throwable {
        byte[] sig = bytes("070702");
        assertTrue(CpioArchiveInputStream.matches(sig, 6));
    }

    // matches(): old ascii magic "070707" -> true
    @Test
    public void testMatches_oldAsciiMagic_returnsTrue() throws Throwable {
        byte[] sig = bytes("070707");
        assertTrue(CpioArchiveInputStream.matches(sig, 6));
    }

    // matches(): non matching signature bytes -> false
    @Test
    public void testMatches_invalidSignature_returnsFalse() throws Throwable {
        byte[] sig = new byte[]{0, 0, 0, 0, 0, 0};
        assertFalse(CpioArchiveInputStream.matches(sig, 6));
    }
}
