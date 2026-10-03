package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class TarArchiveOutputStreamTest {

    @Test
    public void testConstructors() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos1 = new TarArchiveOutputStream(bos);
        assertNotNull(tos1);
        tos1.close();

        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos2 = new TarArchiveOutputStream(bos2, "UTF-8");
        assertNotNull(tos2);
        tos2.close();

        ByteArrayOutputStream bos3 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos3 = new TarArchiveOutputStream(bos3, 1024);
        assertNotNull(tos3);
        tos3.close();

        ByteArrayOutputStream bos4 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos4 = new TarArchiveOutputStream(bos4, 1024, "UTF-8");
        assertNotNull(tos4);
        tos4.close();

        ByteArrayOutputStream bos5 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos5 = new TarArchiveOutputStream(bos5, 1024, 512);
        assertNotNull(tos5);
        tos5.close();
    }

    @Test
    public void testGettersAndSetters() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
        tos.setAddPaxHeadersForNonAsciiNames(true);
        
        assertEquals(512, tos.getRecordSize());
        
        tos.flush();
        tos.close();
    }

    @Test
    public void testFinishAndCloseBehavior() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.finish();
        
        boolean threw = false;
        try {
            tos.finish();
        } catch (IOException e) {
            threw = true;
        }
        assertTrue(threw);
        
        tos.close();
        tos.close(); // idempotent
    }

    @Test
    public void testPutArchiveEntryValidation() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        tos.finish();
        
        boolean threw = false;
        try {
            TarArchiveEntry entry = new TarArchiveEntry("test.txt");
            tos.putArchiveEntry(entry);
        } catch (IOException e) {
            threw = true;
        }
        assertTrue(threw);
        tos.close();
    }

    @Test
    public void testWriteAndCloseArchiveEntryNormal() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        TarArchiveEntry entry = new TarArchiveEntry("test.txt");
        byte[] data = "Hello, World!".getBytes("UTF-8");
        entry.setSize(data.length);
        
        tos.putArchiveEntry(entry);
        tos.write(data, 0, data.length);
        tos.closeArchiveEntry();
        tos.finish();
        tos.close();
        
        assertTrue(bos.size() > 0);
    }

    @Test
    public void testWriteExceedsSize() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        TarArchiveEntry entry = new TarArchiveEntry("test.txt");
        entry.setSize(5);
        
        tos.putArchiveEntry(entry);
        boolean threw = false;
        try {
            byte[] data = "Hello, World!".getBytes("UTF-8");
            tos.write(data, 0, data.length);
        } catch (IOException e) {
            threw = true;
        }
        assertTrue(threw);
        
        try {
            tos.closeArchiveEntry();
        } catch (IOException e) {
            // expected or stream state
        }
        tos.close();
    }

    @Test
    public void testCloseArchiveEntryWithoutOpen() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        boolean threw = false;
        try {
            tos.closeArchiveEntry();
        } catch (IOException e) {
            threw = true;
        }
        assertTrue(threw);
        tos.close();
    }

    @Test
    public void testCloseArchiveEntryIncomplete() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        TarArchiveEntry entry = new TarArchiveEntry("test.txt");
        entry.setSize(100);
        tos.putArchiveEntry(entry);
        
        byte[] data = "Short".getBytes("UTF-8");
        tos.write(data, 0, data.length);
        
        boolean threw = false;
        try {
            tos.closeArchiveEntry();
        } catch (IOException e) {
            threw = true;
        }
        assertTrue(threw);
        tos.close();
    }

    @Test
    public void testLongFileNameModes() throws Throwable {
        String longName = "this/is/a/very/long/file/name/that/exceeds/the/standard/tar/header/limit/of/one/hundred/characters/which/will/trigger/various/long/file/handling/strategies/in/tar/archive/output/stream/implementation/details.txt";
        
        // Error mode
        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos1 = new TarArchiveOutputStream(bos1);
        tos1.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR);
        TarArchiveEntry entry1 = new TarArchiveEntry(longName);
        boolean threw = false;
        try {
            tos1.putArchiveEntry(entry1);
        } catch (RuntimeException e) {
            threw = true;
        }
        assertTrue(threw);
        tos1.close();

        // Truncate mode
        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos2 = new TarArchiveOutputStream(bos2);
        tos2.setLongFileMode(TarArchiveOutputStream.LONGFILE_TRUNCATE);
        TarArchiveEntry entry2 = new TarArchiveEntry(longName);
        tos2.putArchiveEntry(entry2);
        tos2.closeArchiveEntry();
        tos2.close();

        // GNU mode
        ByteArrayOutputStream bos3 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos3 = new TarArchiveOutputStream(bos3);
        tos3.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        TarArchiveEntry entry3 = new TarArchiveEntry(longName);
        entry3.setSize(0);
        tos3.putArchiveEntry(entry3);
        tos3.closeArchiveEntry();
        tos3.close();

        // POSIX mode
        ByteArrayOutputStream bos4 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos4 = new TarArchiveOutputStream(bos4);
        tos4.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        TarArchiveEntry entry4 = new TarArchiveEntry(longName);
        entry4.setSize(0);
        tos4.putArchiveEntry(entry4);
        tos4.closeArchiveEntry();
        tos4.close();
    }

    @Test
    public void testBigNumberModes() throws Throwable {
        // Error mode
        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos1 = new TarArchiveOutputStream(bos1);
        tos1.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_ERROR);
        TarArchiveEntry entry1 = new TarArchiveEntry("big.txt");
        entry1.setSize(TarConstants.MAXSIZE + 1L);
        boolean threw = false;
        try {
            tos1.putArchiveEntry(entry1);
        } catch (RuntimeException e) {
            threw = true;
        }
        assertTrue(threw);
        tos1.close();

        // POSIX mode
        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos2 = new TarArchiveOutputStream(bos2);
        tos2.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
        TarArchiveEntry entry2 = new TarArchiveEntry("big.txt");
        entry2.setSize(TarConstants.MAXSIZE + 1L);
        entry2.setGroupId(TarConstants.MAXID + 1L);
        entry2.setUserId(TarConstants.MAXID + 1L);
        tos2.putArchiveEntry(entry2);
        tos2.closeArchiveEntry();
        tos2.close();

        // Star mode
        ByteArrayOutputStream bos3 = new ByteArrayOutputStream();
        TarArchiveOutputStream tos3 = new TarArchiveOutputStream(bos3);
        tos3.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
        TarArchiveEntry entry3 = new TarArchiveEntry("big.txt");
        entry3.setSize(TarConstants.MAXSIZE + 1L);
        tos3.putArchiveEntry(entry3);
        tos3.closeArchiveEntry();
        tos3.close();
    }

    @Test
    public void testWriteAssemblyBufferHandling() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        TarArchiveEntry entry = new TarArchiveEntry("assembly.txt");
        byte[] data = new byte[1000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 128);
        }
        entry.setSize(data.length);
        
        tos.putArchiveEntry(entry);
        
        // Write in small chunks to trigger assembly buffer logic
        tos.write(data, 0, 30);
        tos.write(data, 30, 500);
        tos.write(data, 530, 470);
        
        tos.closeArchiveEntry();
        tos.finish();
        tos.close();
    }

    @Test
    public void testCreateArchiveEntry() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        File tempFile = File.createTempFile("commons-compress", ".tmp");
        tempFile.deleteOnExit();
        
        ArchiveEntry archiveEntry = tos.createArchiveEntry(tempFile, "temp.tmp");
        assertNotNull(archiveEntry);
        assertTrue(archiveEntry instanceof TarArchiveEntry);
        
        tos.close();
    }

    @Test
    public void testWritePaxHeadersDirectly() throws Throwable {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(bos);
        
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("path", "non-ascii-name-\u00e4\u00f6\u00fc.txt");
        headers.put("comment", "test-comment");
        
        tos.writePaxHeaders("non-ascii-name-\u00e4\u00f6\u00fc.txt", headers);
        tos.finish();
        tos.close();
    }
}