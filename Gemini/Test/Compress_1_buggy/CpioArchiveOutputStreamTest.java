package org.apache.commons.compress.archivers.cpio;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class CpioArchiveOutputStreamTest {

    @Test
    public void testConstructorAndFormatValidation() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = null;
        try {
            out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);
            assertNotNull(out);
        } finally {
            if (out != null) {
                out.close();
            }
        }

        boolean exceptionThrown = false;
        try {
            CpioArchiveOutputStream invalidOut = new CpioArchiveOutputStream(baos, (short) -999);
            invalidOut.close();
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testDefaultConstructor() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos);
        assertNotNull(out);
        out.close();
    }

    @Test
    public void testPutNextEntryAndWriteNewFormat() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);

        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("testfile.txt");
        entry.setFileSize(5);
        entry.setTime(12345L);

        out.putNextEntry(entry);
        byte[] data = "12345".getBytes();
        out.write(data, 0, data.length);
        out.closeArchiveEntry();
        out.finish();
        out.close();

        assertTrue(baos.size() > 0);
    }

    @Test
    public void testPutNextEntryOldAsciiFormat() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_OLD_ASCII);

        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_OLD_ASCII);
        entry.setName("oldascii.txt");
        entry.setFileSize(3);

        out.putNextEntry(entry);
        out.write("abc".getBytes());
        out.closeArchiveEntry();
        out.finish();
        out.close();

        assertTrue(baos.size() > 0);
    }

    @Test
    public void testPutNextEntryOldBinaryFormat() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_OLD_BINARY);

        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_OLD_BINARY);
        entry.setName("oldbin.txt");
        entry.setFileSize(2);

        out.putNextEntry(entry);
        out.write("xy".getBytes());
        out.closeArchiveEntry();
        out.finish();
        out.close();

        assertTrue(baos.size() > 0);
    }

    @Test
    public void testNewCrcFormat() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW_CRC);

        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW_CRC);
        entry.setName("crc.txt");
        byte[] data = "test".getBytes();
        entry.setFileSize(data.length);

        long calculatedChecksum = 0;
        for (int i = 0; i < data.length; i++) {
            calculatedChecksum += data[i] & 0xFF;
        }
        entry.setChksum(calculatedChecksum);

        out.putNextEntry(entry);
        out.write(data);
        out.closeArchiveEntry();
        out.finish();
        out.close();

        assertTrue(baos.size() > 0);
    }

    @Test
    public void testCrcError() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW_CRC);

        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW_CRC);
        entry.setName("crc_err.txt");
        byte[] data = "test".getBytes();
        entry.setFileSize(data.length);
        entry.setChksum(99999L); // Wrong checksum

        boolean exceptionThrown = false;
        try {
            out.putNextEntry(entry);
            out.write(data);
            out.closeArchiveEntry();
        } catch (IOException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("CRC Error"));
        }
        assertTrue(exceptionThrown);
        out.close();
    }

    @Test
    public void testDuplicateEntryName() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);

        CpioArchiveEntry entry1 = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry1.setName("file.txt");
        entry1.setFileSize(0);

        CpioArchiveEntry entry2 = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry2.setName("file.txt");
        entry2.setFileSize(0);

        boolean exceptionThrown = false;
        try {
            out.putNextEntry(entry1);
            out.closeArchiveEntry();
            out.putNextEntry(entry2);
        } catch (IOException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("duplicate entry"));
        }
        assertTrue(exceptionThrown);
        out.close();
    }

    @Test
    public void testInvalidEntrySizeMismatch() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);

        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("size.txt");
        entry.setFileSize(5); // Expected 5

        boolean exceptionThrown = false;
        try {
            out.putNextEntry(entry);
            out.write("12".getBytes()); // Only wrote 2 bytes
            out.closeArchiveEntry();
        } catch (IOException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("invalid entry size"));
        }
        assertTrue(exceptionThrown);
        out.close();
    }

    @Test
    public void testWritePastEnd() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);

        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("past.txt");
        entry.setFileSize(2);

        boolean exceptionThrown = false;
        try {
            out.putNextEntry(entry);
            out.write("123".getBytes());
        } catch (IOException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("attempt to write past end"));
        }
        assertTrue(exceptionThrown);
        out.close();
    }

    @Test
    public void testWriteWithoutEntry() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);

        boolean exceptionThrown = false;
        try {
            out.write("test".getBytes());
        } catch (IOException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("no current CPIO entry"));
        }
        assertTrue(exceptionThrown);
        out.close();
    }

    @Test
    public void testWriteZeroLength() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);

        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("zero.txt");
        entry.setFileSize(0);

        out.putNextEntry(entry);
        out.write(new byte[0], 0, 0);
        out.write(120); // Test single int write
        out.closeArchiveEntry();
        out.close();
    }

    @Test
    public void testWriteIndexOutOfBounds() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);

        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("bounds.txt");
        entry.setFileSize(5);
        out.putNextEntry(entry);

        boolean exceptionThrown = false;
        try {
            out.write(new byte[5], -1, 2);
        } catch (IndexOutOfBoundsException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        out.close();
    }

    @Test
    public void testClosedStreamOperations() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);
        out.close();

        boolean exceptionThrown = false;
        try {
            CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
            entry.setName("closed.txt");
            out.putNextEntry(entry);
        } catch (IOException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("Stream closed"));
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testPutArchiveEntry() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);

        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("archive.txt");
        entry.setFileSize(0);

        out.putArchiveEntry(entry);
        out.closeArchiveEntry();
        out.close();
        assertTrue(baos.size() > 0);
    }

    @Test
    public void testFinishMultipleTimes() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);
        out.finish();
        out.finish(); // Should return immediately without error
        out.close();
    }

    @Test
    public void testDefaultTimeAssignment() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CpioArchiveOutputStream out = new CpioArchiveOutputStream(baos, CpioConstants.FORMAT_NEW);

        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("time.txt");
        entry.setTime(-1); // Should be overwritten by current time
        entry.setFileSize(0);

        out.putNextEntry(entry);
        out.closeArchiveEntry();
        out.close();
        assertTrue(baos.size() > 0);
    }
}