package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ZipArchiveInputStreamTest {

    @Test
    public void testMatchesNull() throws Throwable {
        assertFalse(ZipArchiveInputStream.matches(null, 0));
    }

    @Test
    public void testMatchesShortLength() throws Throwable {
        byte[] sig = new byte[] { 0x50, 0x4b };
        assertFalse(ZipArchiveInputStream.matches(sig, 2));
    }

    @Test
    public void testConstructorAndClose() throws Throwable {
        byte[] data = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        zais.close();
        
        try {
            zais.getNextZipEntry();
            fail("Expected IOException on closed stream");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("closed"));
        }
    }

    @Test
    public void testCanReadEntryDataWithNonZipEntry() throws Throwable {
        byte[] data = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        
        org.apache.commons.compress.archivers.ArchiveEntry nonZipEntry = new org.apache.commons.compress.archivers.ArchiveEntry() {
            public String getName() { return "test"; }
            public long getSize() { return 0; }
            public boolean isDirectory() { return false; }
            public java.util.Date getLastModifiedDate() { return new java.util.Date(); }
        };

        assertFalse(zais.canReadEntryData(nonZipEntry));
        zais.close();
    }

    @Test
    public void testSkipNegative() throws Throwable {
        byte[] data = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        
        try {
            zais.skip(-1L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        } finally {
            zais.close();
        }
    }

    @Test
    public void testReadOnClosedStream() throws Throwable {
        byte[] data = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        zais.close();

        byte[] buf = new byte[5];
        try {
            zais.read(buf, 0, 5);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("closed"));
        }
    }

    @Test
    public void testReadOutOfBounds() throws Throwable {
        byte[] data = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);

        byte[] buf = new byte[5];
        try {
            zais.read(buf, 0, 10);
            fail("Expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // expected
        } finally {
            zais.close();
        }
    }
}