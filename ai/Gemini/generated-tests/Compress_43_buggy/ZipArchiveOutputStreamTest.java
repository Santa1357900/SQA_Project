package org.apache.commons.compress.archivers.zip;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class ZipArchiveOutputStreamTest {

    @Test
    public void testOutputStreamConstructorAndBasicFlow() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos);
        
        assertTrue(zos.isSeekable() == false);
        
        zos.setEncoding("UTF8");
        assertEquals("UTF8", zos.getEncoding());
        
        zos.setUseLanguageEncodingFlag(true);
        zos.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);
        zos.setFallbackToUTF8(true);
        zos.setUseZip64(Zip64Mode.AsNeeded);
        zos.setComment("test comment");
        zos.setLevel(5);
        zos.setMethod(ZipArchiveOutputStream.DEFLATED);
        
        ZipArchiveEntry entry = new ZipArchiveEntry("test.txt");
        zos.putArchiveEntry(entry);
        zos.write("Hello World".getBytes(), 0, 11);
        zos.closeArchiveEntry();
        
        zos.finish();
        zos.close();
        
        assertTrue(baos.size() > 0);
    }

    @Test
    public void testFileConstructor() throws Throwable {
        File tempFile = File.createTempFile("ziptest", ".zip");
        tempFile.deleteOnExit();
        
        ZipArchiveOutputStream zos = null;
        try {
            zos = new ZipArchiveOutputStream(tempFile);
            assertTrue(zos.isSeekable());
            
            ZipArchiveEntry entry = new ZipArchiveEntry("file.txt");
            zos.putArchiveEntry(entry);
            zos.write("Content".getBytes());
            zos.closeArchiveEntry();
            zos.finish();
        } finally {
            if (zos != null) {
                zos.close();
            }
        }
        assertTrue(tempFile.length() > 0);
    }

    @Test
    public void testInvalidCompressionLevel() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos);
        try {
            zos.setLevel(10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid compression level"));
        } finally {
            zos.close();
        }
    }

    @Test(expected = IOException.class)
    public void testFinishTwiceThrowsException() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos);
        try {
            zos.finish();
            zos.finish();
        } finally {
            zos.close();
        }
    }

    @Test
    public void testCanWriteEntryData() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos);
        
        ZipArchiveEntry validEntry = new ZipArchiveEntry("valid.txt");
        validEntry.setMethod(ZipArchiveOutputStream.DEFLATED);
        assertTrue(zos.canWriteEntryData(validEntry));
        
        ZipArchiveEntry invalidEntry = new ZipArchiveEntry("invalid.txt");
        invalidEntry.setMethod(ZipMethod.IMPLODING.getCode());
        assertFalse(zos.canWriteEntryData(invalidEntry));
        
        assertFalse(zos.canWriteEntryData(new ArchiveEntry() {
            public String getName() { return "x"; }
            public long getSize() { return 0; }
            public boolean isDirectory() { return false; }
            public java.util.Date getLastModifiedDate() { return new java.util.Date(); }
        }));
        
        zos.close();
    }

    @Test
    public void testCreateArchiveEntry() throws Throwable {
        File tempFile = File.createTempFile("temp", "dir");
        tempFile.deleteOnExit();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos);
        
        ArchiveEntry ae = zos.createArchiveEntry(tempFile, "myDir/");
        assertNotNull(ae);
        assertTrue(ae instanceof ZipArchiveEntry);
        
        zos.close();
    }

    @Test
    public void testStoredWithoutSizeNonSeekable() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos);
        ZipArchiveEntry entry = new ZipArchiveEntry("stored.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        // Size and CRC are unknown and stream is non-seekable
        try {
            zos.putArchiveEntry(entry);
            fail("Expected ZipException");
        } catch (ZipException e) {
            assertTrue(e.getMessage().contains("uncompressed size is required"));
        } finally {
            zos.close();
        }
    }

    @Test
    public void testFlushAndClose() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos);
        zos.flush();
        zos.close();
    }

    @Test
    public void testAddRawArchiveEntry() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos);
        
        ZipArchiveEntry entry = new ZipArchiveEntry("raw.txt");
        entry.setMethod(ZipArchiveOutputStream.STORED);
        entry.setSize(4);
        entry.setCrc(12345678L);
        entry.setCompressedSize(4);
        
        byte[] data = new byte[] { 1, 2, 3, 4 };
        final java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
        
        zos.addRawArchiveEntry(entry, bais);
        zos.finish();
        zos.close();
    }

    @Test
    public void testWriteWithoutCurrentEntry() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos);
        try {
            zos.write(new byte[1], 0, 1);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No current entry"));
        } finally {
            zos.close();
        }
    }
}