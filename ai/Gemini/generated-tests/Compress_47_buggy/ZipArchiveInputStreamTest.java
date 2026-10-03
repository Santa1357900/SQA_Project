package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ZipArchiveInputStreamTest {

    @Test
    public void testMatchesWithNullOrEmpty() throws Throwable {
        byte[] empty = new byte[0];
        assertFalse(ZipArchiveInputStream.matches(empty, 0));
        assertFalse(ZipArchiveInputStream.matches(null, 0));
    }

    @Test
    public void testMatchesWithShortArray() throws Throwable {
        byte[] shortArr = new byte[2];
        assertFalse(ZipArchiveInputStream.matches(shortArr, 2));
    }

    @Test
    public void testMatchesWithValidLFH() throws Throwable {
        byte[] lfhSig = ZipArchiveOutputStream.LFH_SIG;
        assertTrue(ZipArchiveInputStream.matches(lfhSig, lfhSig.length));
    }

    @Test
    public void testConstructorAndGetNextEntryNullStream() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        assertNull(zais.getNextZipEntry());
        assertNull(zais.getNextEntry());
        zais.close();
    }

    @Test
    public void testSkipNegativeValue() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        try {
            zais.skip(-1);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        } finally {
            zais.close();
        }
    }

    @Test
    public void testSkipZeroAndPositive() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        long skipped = zais.skip(0);
        assertEquals(0L, skipped);
        
        long skippedPos = zais.skip(5);
        assertEquals(5L, skippedPos);
        zais.close();
    }

    @Test
    public void testCanReadEntryDataWithNonZipEntry() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
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
    public void testReadOnClosedStream() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        zais.close();
        
        byte[] buf = new byte[10];
        try {
            zais.read(buf, 0, 5);
            fail("Should have thrown IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("closed"));
        }
    }

    @Test
    public void testReadWithInvalidIndices() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        byte[] buf = new byte[10];
        
        try {
            zais.read(buf, -1, 5);
            fail("Should have thrown ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Expected
        }
        
        try {
            zais.read(buf, 0, -1);
            fail("Should have thrown ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Expected
        }
        
        try {
            zais.read(buf, 5, 10);
            fail("Should have thrown ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Expected
        }
        
        zais.close();
    }
}