package org.apache.commons.compress.archivers.cpio;

import junit.framework.TestCase;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class CpioArchiveOutputStreamTest extends TestCase {

    public void testConstructorWithValidFormats() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream osNew = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW);
        assertNotNull(osNew);

        CpioArchiveOutputStream osNewCrc = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW_CRC);
        assertNotNull(osNewCrc);

        CpioArchiveOutputStream osOldAscii = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_OLD_ASCII);
        assertNotNull(osOldAscii);

        CpioArchiveOutputStream osOldBinary = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_OLD_BINARY);
        assertNotNull(osOldBinary);
    }

    public void testConstructorWithInvalidFormat() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new CpioArchiveOutputStream(out, (short) -99);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown format"));
        }
    }

    public void testDefaultConstructor() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream os = new CpioArchiveOutputStream(out);
        assertNotNull(os);
    }

    public void testPutArchiveEntryAndTimeGeneration() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream os = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW);
        
        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("test1.txt");
        entry.setTime(-1);
        entry.setSize(5);
        
        os.putArchiveEntry(entry);
        assertTrue(entry.getTime() != -1);
        
        os.write("12345".getBytes(), 0, 5);
        os.closeArchiveEntry();
        os.finish();
        os.close();
    }

    public void testFormatMismatchException() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream os = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW);
        
        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_OLD_ASCII);
        entry.setName("test2.txt");
        
        try {
            os.putArchiveEntry(entry);
            fail("Expected IOException due to format mismatch");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Header format"));
        }
    }

    public void testDuplicateEntryException() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream os = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW);
        
        CpioArchiveEntry entry1 = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry1.setName("same.txt");
        entry1.setSize(0);
        
        CpioArchiveEntry entry2 = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry2.setName("same.txt");
        entry2.setSize(0);
        
        os.putArchiveEntry(entry1);
        os.closeArchiveEntry();
        
        try {
            os.putArchiveEntry(entry2);
            fail("Expected IOException due to duplicate entry");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("duplicate entry"));
        }
    }

    public void testWriteErrorsAndConstraints() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream os = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW);
        
        byte[] data = "hello".getBytes();
        
        // Write without current entry
        try {
            os.write(data, 0, data.length);
            fail("Expected IOException for no current CPIO entry");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("no current CPIO entry"));
        }
        
        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("file.txt");
        entry.setSize(3);
        os.putArchiveEntry(entry);
        
        // IndexOutOfBoundsException cases
        try {
            os.write(data, -1, 2);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
        
        try {
            os.write(data, 0, -1);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
        
        try {
            os.write(data, 0, data.length + 10);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
        
        // Write zero bytes (should return immediately)
        os.write(data, 0, 0);
        
        // Attempt to write past end of stored entry
        try {
            os.write(data, 0, 5);
            fail("Expected IOException for writing past end");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("attempt to write past end"));
        }
        
        os.close();
    }

    public void testCloseArchiveEntrySizeMismatch() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream os = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW);
        
        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("mismatch.txt");
        entry.setSize(10);
        os.putArchiveEntry(entry);
        
        os.write("abc".getBytes(), 0, 3);
        
        try {
            os.closeArchiveEntry();
            fail("Expected IOException due to invalid entry size");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("invalid entry size"));
        }
    }

    public void testCrcError() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream os = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW_CRC);
        
        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW_CRC);
        entry.setName("crc.txt");
        entry.setSize(3);
        entry.setChksum(99999); // wrong checksum
        os.putArchiveEntry(entry);
        
        os.write("abc".getBytes(), 0, 3);
        
        try {
            os.closeArchiveEntry();
            fail("Expected IOException due to CRC Error");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("CRC Error"));
        }
    }

    public void testFormatsWriteOldAsciiAndBinary() throws Throwable {
        // Test Old Ascii
        ByteArrayOutputStream outAscii = new ByteArrayOutputStream();
        CpioArchiveOutputStream osAscii = new CpioArchiveOutputStream(outAscii, CpioConstants.FORMAT_OLD_ASCII);
        CpioArchiveEntry entryAscii = new CpioArchiveEntry(CpioConstants.FORMAT_OLD_ASCII);
        entryAscii.setName("ascii.txt");
        entryAscii.setSize(2);
        osAscii.putArchiveEntry(entryAscii);
        osAscii.write("hi".getBytes(), 0, 2);
        osAscii.closeArchiveEntry();
        osAscii.finish();
        osAscii.close();
        assertTrue(outAscii.size() > 0);

        // Test Old Binary
        ByteArrayOutputStream outBin = new ByteArrayOutputStream();
        CpioArchiveOutputStream osBin = new CpioArchiveOutputStream(outBin, CpioConstants.FORMAT_OLD_BINARY);
        CpioArchiveEntry entryBin = new CpioArchiveEntry(CpioConstants.FORMAT_OLD_BINARY);
        entryBin.setName("bin.txt");
        entryBin.setSize(2);
        osBin.putArchiveEntry(entryBin);
        osBin.write("hi".getBytes(), 0, 2);
        osBin.closeArchiveEntry();
        osBin.finish();
        osBin.close();
        assertTrue(outBin.size() > 0);
    }

    public void testFinishWithUnclosedEntry() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream os = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW);
        
        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("unclosed.txt");
        entry.setSize(5);
        os.putArchiveEntry(entry);
        
        try {
            os.finish();
            fail("Expected IOException for unclosed entries");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("unclosed entries"));
        }
    }

    public void testMultipleFinishCallsAndClose() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream os = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW);
        
        os.finish();
        os.finish(); // calling finish twice should be safe due to finished flag
        os.close();
        os.close(); // calling close twice should be safe
    }

    public void testEnsureOpenAfterClose() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream os = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW);
        os.close();
        
        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        entry.setName("closed.txt");
        
        try {
            os.putArchiveEntry(entry);
            fail("Expected IOException because stream is closed");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Stream closed"));
        }
    }

    public void testCreateArchiveEntry() throws Throwable {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpioArchiveOutputStream os = new CpioArchiveOutputStream(out, CpioConstants.FORMAT_NEW);
        
        File tempFile = File.createTempFile("cpioTest", ".tmp");
        tempFile.deleteOnExit();
        
        ArchiveEntry archEntry = os.createArchiveEntry(tempFile, "tempFileName");
        assertNotNull(archEntry);
        assertEquals("tempFileName", archEntry.getName());
    }
}