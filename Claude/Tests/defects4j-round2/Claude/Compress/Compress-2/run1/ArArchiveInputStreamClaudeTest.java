package org.apache.commons.compress.archivers.ar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.ArchiveEntry;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArArchiveInputStreamClaudeTest {

    // ---------- helpers (not test methods) ----------

    private static String padRight(String s, int len) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static byte[] buildEntryFields(String name, String size) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(padRight(name, 16).getBytes());
        baos.write(padRight("0", 12).getBytes());
        baos.write(padRight("0", 6).getBytes());
        baos.write(padRight("0", 6).getBytes());
        baos.write(padRight("644", 8).getBytes());
        baos.write(padRight(size, 10).getBytes());
        return baos.toByteArray();
    }

    private static byte[] buildEntryHeaderBytes(String name, String size) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(buildEntryFields(name, size));
        baos.write(ArArchiveEntry.TRAILER.getBytes());
        return baos.toByteArray();
    }

    private static byte[] buildSingleEntryArchive(String name, String size) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ArArchiveEntry.HEADER.getBytes());
        baos.write(buildEntryHeaderBytes(name, size));
        return baos.toByteArray();
    }

    private static byte[] buildTwoEmptyEntriesArchive() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ArArchiveEntry.HEADER.getBytes());
        baos.write(buildEntryHeaderBytes("a.txt", "0"));
        baos.write(buildEntryHeaderBytes("b.txt", "0"));
        return baos.toByteArray();
    }

    // ---------- constructor ----------

    // covers: constructor stores stream, object created successfully
    @Test
    public void testConstructor_validInputStream_createsNonNullInstance() throws Throwable {
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        assertNotNull(in);
    }

    // ---------- getNextArEntry ----------

    // covers: offset==0 branch, header matches, available!=0, returns entry
    @Test
    public void testGetNextArEntry_validHeaderAndEntry_returnsNonNullEntry() throws Throwable {
        byte[] data = buildSingleEntryArchive("a.txt", "0");
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(data));
        ArArchiveEntry e = in.getNextArEntry();
        assertNotNull(e);
    }

    // covers: header mismatch branch -> IOException "invalid header"
    @Test
    public void testGetNextArEntry_invalidHeaderBytes_throwsIOException() throws Throwable {
        int len = ArArchiveEntry.HEADER.getBytes().length;
        byte[] wrong = new byte[len];
        for (int i = 0; i < len; i++) {
            wrong[i] = (byte) 'X';
        }
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(wrong));
        try {
            in.getNextArEntry();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("invalid header"));
        }
    }

    // covers: header read truncated -> IOException "failed to read header"
    @Test
    public void testGetNextArEntry_truncatedHeader_throwsIOException() throws Throwable {
        byte[] data = new byte[] { '!', '<', 'a', 'r' };
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(data));
        try {
            in.getNextArEntry();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("failed to read header"));
        }
    }

    // covers: input.available()==0 branch -> returns null
    @Test
    public void testGetNextArEntry_noEntriesAfterHeader_returnsNull() throws Throwable {
        byte[] data = ArArchiveEntry.HEADER.getBytes();
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(data));
        ArArchiveEntry e = in.getNextArEntry();
        assertNull(e);
    }

    // covers: trailer mismatch branch -> IOException "invalid entry header"
    @Test
    public void testGetNextArEntry_invalidTrailerBytes_throwsIOException() throws Throwable {
        byte[] realTrailer = ArArchiveEntry.TRAILER.getBytes();
        byte[] wrongTrailer = realTrailer.clone();
        wrongTrailer[0] = (byte) (realTrailer[0] + 1);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ArArchiveEntry.HEADER.getBytes());
        baos.write(buildEntryFields("a.txt", "0"));
        baos.write(wrongTrailer);
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(baos.toByteArray()));
        try {
            in.getNextArEntry();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("invalid entry header"));
        }
    }

    // covers: trailer read truncated -> IOException "failed to read entry header"
    @Test
    public void testGetNextArEntry_truncatedTrailer_throwsIOException() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ArArchiveEntry.HEADER.getBytes());
        baos.write(buildEntryFields("a.txt", "0"));
        baos.write(new byte[] { 0x01 });
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(baos.toByteArray()));
        try {
            in.getNextArEntry();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("failed to read entry header"));
        }
    }

    // covers: offset%2!=0 padding skip with non-zero content byte (control case, both versions pass)
    @Test
    public void testGetNextArEntry_oddLengthNonZeroContent_secondEntryParsedCorrectly() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ArArchiveEntry.HEADER.getBytes());
        baos.write(buildEntryHeaderBytes("a.txt", "1"));
        baos.write(new byte[] { (byte) 'x' });
        baos.write(new byte[] { 0x0A });
        baos.write(buildEntryHeaderBytes("b.txt", "0"));
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(baos.toByteArray()));
        ArArchiveEntry e1 = in.getNextArEntry();
        assertNotNull(e1);
        int c = in.read();
        assertEquals((int) 'x', c);
        ArArchiveEntry e2 = in.getNextArEntry();
        assertNotNull(e2);
    }

    // BUG TEST covers: offset tracking in read() when a zero-value byte is read (ret>0 vs ret>=0)
    @Test
    public void testGetNextArEntry_oddLengthZeroByteContent_offsetTrackedCorrectlyForSecondEntry() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ArArchiveEntry.HEADER.getBytes());
        baos.write(buildEntryHeaderBytes("a.txt", "1"));
        baos.write(new byte[] { 0 });
        baos.write(new byte[] { 0x0A });
        baos.write(buildEntryHeaderBytes("b.txt", "0"));
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(baos.toByteArray()));
        ArArchiveEntry e1 = in.getNextArEntry();
        assertNotNull(e1);
        int c = in.read();
        assertEquals(0, c);
        ArArchiveEntry e2 = in.getNextArEntry();
        assertNotNull(e2);
    }

    // covers: Long.parseLong on non-numeric trimmed length field
    @Test
    public void testGetNextArEntry_nonNumericLengthField_throwsNumberFormatException() throws Throwable {
        byte[] data = buildSingleEntryArchive("a.txt", "abc");
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(data));
        try {
            in.getNextArEntry();
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
            assertNotNull(expected);
        }
    }

    // covers: multiple sequential entries then null at end
    @Test
    public void testGetNextArEntry_calledRepeatedly_returnsNullAfterLastEntry() throws Throwable {
        byte[] data = buildTwoEmptyEntriesArchive();
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(data));
        assertNotNull(in.getNextArEntry());
        assertNotNull(in.getNextArEntry());
        assertNull(in.getNextArEntry());
    }

    // ---------- getNextEntry ----------

    // covers: getNextEntry delegates to getNextArEntry, returns ArArchiveEntry instance
    @Test
    public void testGetNextEntry_validArchive_returnsArArchiveEntryInstance() throws Throwable {
        byte[] data = buildSingleEntryArchive("a.txt", "0");
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(data));
        ArchiveEntry e = in.getNextEntry();
        assertNotNull(e);
        assertTrue(e instanceof ArArchiveEntry);
    }

    // covers: getNextEntry returns null when no entries left
    @Test
    public void testGetNextEntry_emptyArchiveAfterHeader_returnsNull() throws Throwable {
        byte[] data = ArArchiveEntry.HEADER.getBytes();
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(data));
        ArchiveEntry e = in.getNextEntry();
        assertNull(e);
    }

    // ---------- close ----------

    // covers: close() single call; ByteArrayInputStream.close() is documented no-op, stream still readable
    @Test
    public void testClose_singleCall_streamRemainsReadable() throws Throwable {
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(new byte[] { 65, 66 }));
        in.close();
        int b = in.read();
        assertEquals(65, b);
    }

    // covers: close() called twice does not throw (closed flag) and stream remains readable
    @Test
    public void testClose_calledTwice_doesNotThrowAndStreamRemainsReadable() throws Throwable {
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(new byte[] { 65, 66 }));
        in.close();
        in.close();
        int b = in.read();
        assertEquals(65, b);
    }

    // ---------- read() ----------

    // covers: sequential single byte reads and EOF returning -1
    @Test
    public void testRead_sequentialBytes_returnsValuesThenMinusOneAtEof() throws Throwable {
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(new byte[] { 10, 20, 30 }));
        assertEquals(10, in.read());
        assertEquals(20, in.read());
        assertEquals(30, in.read());
        assertEquals(-1, in.read());
    }

    // ---------- read(byte[]) ----------

    // covers: read(byte[]) fully available data
    @Test
    public void testReadByteArray_fullyAvailableData_returnsFullLengthAndCorrectBytes() throws Throwable {
        byte[] source = "HELLO".getBytes();
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(source));
        byte[] buf = new byte[5];
        int n = in.read(buf);
        assertEquals(5, n);
        assertArrayEquals(source, buf);
    }

    // covers: read(byte[]) buffer larger than available data returns partial count
    @Test
    public void testReadByteArray_bufferLargerThanAvailable_returnsPartialCount() throws Throwable {
        byte[] source = new byte[] { 1, 2, 3 };
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(source));
        byte[] buf = new byte[5];
        int n = in.read(buf);
        assertEquals(3, n);
    }

    // covers: read(byte[]) on empty stream returns -1 (EOF)
    @Test
    public void testReadByteArray_emptyStream_returnsMinusOne() throws Throwable {
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(new byte[0]));
        byte[] buf = new byte[5];
        int n = in.read(buf);
        assertEquals(-1, n);
    }

    // ---------- read(byte[], off, len) ----------

    // covers: read(byte[],off,len) reads correct segment at given offset
    @Test
    public void testReadByteArrayOffLen_customOffsetAndLength_readsCorrectSegment() throws Throwable {
        byte[] source = new byte[] { 1, 2, 3, 4, 5 };
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(source));
        byte[] buf = new byte[10];
        int n = in.read(buf, 2, 3);
        assertEquals(3, n);
        assertEquals(1, buf[2]);
        assertEquals(2, buf[3]);
        assertEquals(3, buf[4]);
    }

    // covers: read(byte[],off,len) with len==0 returns 0
    @Test
    public void testReadByteArrayOffLen_zeroLength_returnsZero() throws Throwable {
        byte[] source = new byte[] { 1, 2, 3 };
        ArArchiveInputStream in = new ArArchiveInputStream(new ByteArrayInputStream(source));
        byte[] buf = new byte[3];
        int n = in.read(buf, 0, 0);
        assertEquals(0, n);
    }

    // ---------- matches(static) ----------

    // covers: length < 8 branch returns false
    @Test
    public void testMatches_lengthLessThanEight_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 0x21, 0x3c, 0x61, 0x72, 0x63, 0x68, 0x3e, 0x0a };
        assertFalse(ArArchiveInputStream.matches(sig, 7));
    }

    // covers: correct 8-byte signature returns true
    @Test
    public void testMatches_correctSignature_returnsTrue() throws Throwable {
        byte[] sig = new byte[] { 0x21, 0x3c, 0x61, 0x72, 0x63, 0x68, 0x3e, 0x0a };
        assertTrue(ArArchiveInputStream.matches(sig, 8));
    }

    // covers: signature[0] mismatch branch returns false
    @Test
    public void testMatches_byte0Mismatch_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 0x00, 0x3c, 0x61, 0x72, 0x63, 0x68, 0x3e, 0x0a };
        assertFalse(ArArchiveInputStream.matches(sig, 8));
    }

    // covers: signature[1] mismatch branch returns false
    @Test
    public void testMatches_byte1Mismatch_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 0x21, 0x00, 0x61, 0x72, 0x63, 0x68, 0x3e, 0x0a };
        assertFalse(ArArchiveInputStream.matches(sig, 8));
    }

    // covers: signature[2] mismatch branch returns false
    @Test
    public void testMatches_byte2Mismatch_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 0x21, 0x3c, 0x00, 0x72, 0x63, 0x68, 0x3e, 0x0a };
        assertFalse(ArArchiveInputStream.matches(sig, 8));
    }

    // covers: signature[3] mismatch branch returns false
    @Test
    public void testMatches_byte3Mismatch_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 0x21, 0x3c, 0x61, 0x00, 0x63, 0x68, 0x3e, 0x0a };
        assertFalse(ArArchiveInputStream.matches(sig, 8));
    }

    // covers: signature[4] mismatch branch returns false
    @Test
    public void testMatches_byte4Mismatch_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 0x21, 0x3c, 0x61, 0x72, 0x00, 0x68, 0x3e, 0x0a };
        assertFalse(ArArchiveInputStream.matches(sig, 8));
    }

    // covers: signature[5] mismatch branch returns false
    @Test
    public void testMatches_byte5Mismatch_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 0x21, 0x3c, 0x61, 0x72, 0x63, 0x00, 0x3e, 0x0a };
        assertFalse(ArArchiveInputStream.matches(sig, 8));
    }

    // covers: signature[6] mismatch branch returns false
    @Test
    public void testMatches_byte6Mismatch_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 0x21, 0x3c, 0x61, 0x72, 0x63, 0x68, 0x00, 0x0a };
        assertFalse(ArArchiveInputStream.matches(sig, 8));
    }

    // covers: signature[7] mismatch branch returns false
    @Test
    public void testMatches_byte7Mismatch_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 0x21, 0x3c, 0x61, 0x72, 0x63, 0x68, 0x3e, 0x00 };
        assertFalse(ArArchiveInputStream.matches(sig, 8));
    }
}
