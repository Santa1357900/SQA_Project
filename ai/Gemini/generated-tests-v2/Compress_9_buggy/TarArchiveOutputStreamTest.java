package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class TarArchiveOutputStreamTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        assertNotNull(tos);
        assertEquals(TarBuffer.DEFAULT_RCDSIZE, tos.getRecordSize());
        tos.close();
    }

    @Test
    public void testConstructorWithBlockSize() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int blockSize = 1024;
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos, blockSize);
        assertNotNull(tos);
        assertEquals(TarBuffer.DEFAULT_RCDSIZE, tos.getRecordSize());
        tos.close();
    }

    @Test
    public void testConstructorWithBlockAndRecordSize() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int blockSize = 1024;
        int recordSize = 512;
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos, blockSize, recordSize);
        assertNotNull(tos);
        assertEquals(recordSize, tos.getRecordSize());
        tos.close();
    }

    @Test
    public void testSetLongFileMode() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_TRUNCATE);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR);
        tos.close();
    }

    @Test
    public void testPutAndCloseArchiveEntryNormal() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        TarArchiveEntry entry = new TarArchiveEntry("test.txt");
        entry.setSize(5);
        
        tos.putArchiveEntry(entry);
        byte[] data = new byte[] {1, 2, 3, 4, 5};
        tos.write(data, 0, data.length);
        tos.closeArchiveEntry();
        tos.finish();
        tos.close();
    }

    @Test
    public void testPutArchiveEntryLongNameError() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR);
        
        StringBuffer longName = new StringBuffer();
        for (int i = 0; i < 150; i++) {
            longName.append("a");
        }
        
        TarArchiveEntry entry = new TarArchiveEntry(longName.toString());
        
        boolean thrown = false;
        try {
            tos.putArchiveEntry(entry);
        } catch (RuntimeException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("is too long"));
        }
        assertTrue(thrown);
        tos.close();
    }

    @Test
    public void testPutArchiveEntryLongNameGnu() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        
        StringBuffer longName = new StringBuffer();
        for (int i = 0; i < 150; i++) {
            longName.append("b");
        }
        
        TarArchiveEntry entry = new TarArchiveEntry(longName.toString());
        entry.setSize(0);
        
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.finish();
        tos.close();
    }

    @Test
    public void testPutArchiveEntryLongNameTruncate() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_TRUNCATE);
        
        StringBuffer longName = new StringBuffer();
        for (int i = 0; i < 150; i++) {
            longName.append("c");
        }
        
        TarArchiveEntry entry = new TarArchiveEntry(longName.toString());
        entry.setSize(0);
        
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.finish();
        tos.close();
    }

    @Test
    public void testFinishTwice() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.finish();
        
        boolean thrown = false;
        try {
            tos.finish();
        } catch (IOException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("already been finished"));
        }
        assertTrue(thrown);
        tos.close();
    }

    @Test
    public void testPutArchiveEntryAfterFinish() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.finish();
        
        TarArchiveEntry entry = new TarArchiveEntry("test.txt");
        boolean thrown = false;
        try {
            tos.putArchiveEntry(entry);
        } catch (IOException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Stream has already been finished"));
        }
        assertTrue(thrown);
        tos.close();
    }

    @Test
    public void testCloseArchiveEntryWithoutOpen() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        boolean thrown = false;
        try {
            tos.closeArchiveEntry();
        } catch (IOException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("No current entry to close"));
        }
        assertTrue(thrown);
        tos.close();
    }

    @Test
    public void testCloseArchiveEntryEarly() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        TarArchiveEntry entry = new TarArchiveEntry("test.txt");
        entry.setSize(10);
        tos.putArchiveEntry(entry);
        
        byte[] data = new byte[] {1, 2, 3};
        tos.write(data, 0, data.length);
        
        boolean thrown = false;
        try {
            tos.closeArchiveEntry();
        } catch (IOException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("closed at"));
        }
        assertTrue(thrown);
        tos.close();
    }

    @Test
    public void testWriteExceedsSize() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        TarArchiveEntry entry = new TarArchiveEntry("test.txt");
        entry.setSize(2);
        tos.putArchiveEntry(entry);
        
        byte[] data = new byte[] {1, 2, 3, 4, 5};
        boolean thrown = false;
        try {
            tos.write(data, 0, data.length);
        } catch (IOException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("exceeds size"));
        }
        assertTrue(thrown);
        tos.close();
    }

    @Test
    public void testWriteWithAssemblyBuffer() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        TarArchiveEntry entry = new TarArchiveEntry("test.txt");
        entry.setSize(600);
        tos.putArchiveEntry(entry);
        
        // Write small chunks to test assembly logic (assemLen > 0)
        byte[] smallData = new byte[30];
        for (int i = 0; i < 30; i++) {
            smallData[i] = (byte) i;
        }
        
        tos.write(smallData, 0, 10);
        tos.write(smallData, 10, 20); // triggers assembly paths
        
        // Write a larger chunk exceeding record size to test while loop in write
        byte[] largeData = new byte[600];
        tos.write(largeData, 0, 570);
        
        tos.closeArchiveEntry();
        tos.finish();
        tos.close();
    }

    @Test
    public void testFlush() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.flush();
        tos.close();
    }

    @Test
    public void testCreateArchiveEntry() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        File tempFile = File.createTempFile("commons-compress", "tar");
        tempFile.deleteOnExit();
        
        ArchiveEntry entry = tos.createArchiveEntry(tempFile, "testEntryName");
        assertNotNull(entry);
        assertEquals("testEntryName", entry.getName());
        
        tos.close();
    }

    @Test
    public void testCreateArchiveEntryAfterFinish() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.finish();
        
        File tempFile = File.createTempFile("commons-compress", "tar");
        tempFile.deleteOnExit();
        
        boolean thrown = false;
        try {
            tos.createArchiveEntry(tempFile, "testEntryName");
        } catch (IOException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Stream has already been finished"));
        }
        assertTrue(thrown);
        tos.close();
    }
}