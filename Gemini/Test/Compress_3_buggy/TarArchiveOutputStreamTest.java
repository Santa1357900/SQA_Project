package org.apache.commons.compress.archivers.tar;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TarArchiveOutputStreamTest {

    @Test
    public void testConstructorsAndRecordSize() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        assertEquals(TarBuffer.DEFAULT_RCDSIZE, tos.getRecordSize());
        tos.close();

        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos2 = new TarArchiveOutputStream(bos2, 1024);
        assertEquals(TarBuffer.DEFAULT_RCDSIZE, tos2.getRecordSize());
        tos2.close();

        ByteArrayOutputStream bos3 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos3 = new TarArchiveOutputStream(bos3, 1024, 512);
        assertEquals(512, tos3.getRecordSize());
        tos3.close();
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
        File tempFile = File.createTempFile("commons-compress", ".tmp");
        tempFile.deleteOnExit();
        ArchiveEntry entry = tos.createArchiveEntry(tempFile, "test.txt");
        assertNotNull(entry);
        assertEquals("test.txt", entry.getName());
        tos.close();
    }

    @Test
    public void testPutNormalEntryAndFinish() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = new TarArchiveEntry("test.txt");
        entry.setSize(0);
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.finish();
        tos.close();
        assertTrue(bos.size() > 0);
    }

    @Test
    public void testDirectoryEntry() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = new TarArchiveEntry("testDir/");
        entry.setModTime(System.currentTimeMillis());
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.close();
        assertTrue(bos.size() > 0);
    }

    @Test
    public void testLongFileNameErrorMode() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR);
        
        StringBuffer longName = new StringBuffer();
        for (int i = 0; i < 150; i++) {
            longName.append("a");
        }
        TarArchiveEntry entry = new TarArchiveEntry(longName.toString());
        entry.setSize(0);

        try {
            tos.putArchiveEntry(entry);
            fail("Expected RuntimeException for long file name in ERROR mode");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("is too long"));
        } finally {
            tos.close();
        }
    }

    @Test
    public void testLongFileNameTruncateMode() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_TRUNCATE);
        
        StringBuffer longName = new StringBuffer();
        for (int i = 0; i < 150; i++) {
            longName.append("a");
        }
        TarArchiveEntry entry = new TarArchiveEntry(longName.toString());
        entry.setSize(0);

        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.close();
    }

    @Test
    public void testLongFileNameGnuMode() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        
        StringBuffer longName = new StringBuffer();
        for (int i = 0; i < 150; i++) {
            longName.append("a");
        }
        TarArchiveEntry entry = new TarArchiveEntry(longName.toString());
        entry.setSize(0);

        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        tos.close();
    }

    @Test
    public void testWriteDataWithAssemblyAndExactSize() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos, 512, 512);
        TarArchiveEntry entry = new TarArchiveEntry("data.txt");
        byte[] data = "Hello, Tar World!".getBytes();
        entry.setSize(data.length);

        tos.putArchiveEntry(entry);
        
        // Write in small chunks to test assemBuf and assemble logic
        tos.write(data, 0, 5);
        tos.write(data, 5, data.length - 5);
        
        tos.closeArchiveEntry();
        tos.close();
        assertTrue(bos.size() > 0);
    }

    @Test
    public void testWriteExceedsSpecifiedSize() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = new TarArchiveEntry("data.txt");
        entry.setSize(5);

        tos.putArchiveEntry(entry);
        byte[] data = "Too long data".getBytes();
        try {
            tos.write(data, 0, data.length);
            fail("Expected IOException when writing more bytes than specified in header");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("exceeds size in header"));
        } finally {
            tos.close();
        }
    }

    @Test
    public void testCloseArchiveEntryBeforeWritingEnoughBytes() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        TarArchiveEntry entry = new TarArchiveEntry("data.txt");
        entry.setSize(100);

        tos.putArchiveEntry(entry);
        byte[] data = "Short data".getBytes();
        tos.write(data, 0, data.length);

        try {
            tos.closeArchiveEntry();
            fail("Expected IOException when closing entry before writing all specified bytes");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("before the"));
        } finally {
            tos.close();
        }
    }

    @Test
    public void testMultipleCloseCalls() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.close();
        tos.close(); // Should be safe due to closed flag check
    }

    @Test
    public void testWriteLargeChunkDirectlyToBuffer() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos, 512, 512);
        TarArchiveEntry entry = new TarArchiveEntry("large.txt");
        
        // Size larger than record size (512 bytes)
        int size = 1024;
        entry.setSize(size);
        tos.putArchiveEntry(entry);

        byte[] largeData = new byte[size];
        for (int i = 0; i < size; i++) {
            largeData[i] = (byte) (i % 128);
        }

        tos.write(largeData, 0, largeData.length);
        tos.closeArchiveEntry();
        tos.close();
        assertTrue(bos.size() > 0);
    }

    @Test
    public void testAssemblyBufferFlushOnClose() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos, 512, 512);
        TarArchiveEntry entry = new TarArchiveEntry("small.txt");
        entry.setSize(10);
        tos.putArchiveEntry(entry);

        // Write some bytes leaving assemLen > 0
        byte[] data = "12345".getBytes();
        tos.write(data, 0, data.length);
        
        // Close entry while assemLen > 0 to trigger the assembly padding and write block in closeArchiveEntry()
        tos.closeArchiveEntry();
        tos.close();
        assertTrue(bos.size() > 0);
    }
}