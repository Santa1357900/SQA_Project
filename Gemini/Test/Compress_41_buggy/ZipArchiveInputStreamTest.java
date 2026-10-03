package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ZipArchiveInputStreamTest {

    @Test
    public void testMatchesNullAndEmpty() throws Throwable {
        assertFalse(ZipArchiveInputStream.matches(null, 0));
        assertFalse(ZipArchiveInputStream.matches(new byte[0], 0));
        
        byte[] shortSig = new byte[] { 'P', 'K' };
        assertFalse(ZipArchiveInputStream.matches(shortSig, shortSig.length));
    }

    @Test
    public void testMatchesValidSignatures() throws Throwable {
        byte[] lfhSig = new byte[] { 'P', 'K', 0x03, 0x04, 0, 0, 0, 0 };
        assertTrue(ZipArchiveInputStream.matches(lfhSig, lfhSig.length));

        byte[] eocdSig = new byte[] { 'P', 'K', 0x05, 0x06, 0, 0, 0, 0 };
        assertTrue(ZipArchiveInputStream.matches(eocdSig, eocdSig.length));

        byte[] ddSig = new byte[] { 'P', 'K', 0x07, 0x08, 0, 0, 0, 0 };
        assertTrue(ZipArchiveInputStream.matches(ddSig, ddSig.length));
    }

    @Test
    public void testConstructorsAndEncodings() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        ZipArchiveInputStream zais1 = new ZipArchiveInputStream(bais);
        assertNotNull(zais1);
        zais1.close();

        ByteArrayInputStream bais2 = new ByteArrayInputStream(new byte[0]);
        ZipArchiveInputStream zais2 = new ZipArchiveInputStream(bais2, "UTF8");
        assertNotNull(zais2);
        assertEquals("UTF8", zais2.encoding);
        zais2.close();

        ByteArrayInputStream bais3 = new ByteArrayInputStream(new byte[0]);
        ZipArchiveInputStream zais3 = new ZipArchiveInputStream(bais3, "UTF8", true);
        assertNotNull(zais3);
        zais3.close();

        ByteArrayInputStream bais4 = new ByteArrayInputStream(new byte[0]);
        ZipArchiveInputStream zais4 = new ZipArchiveInputStream(bais4, "UTF8", true, true);
        assertNotNull(zais4);
        zais4.close();
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
    public void testSkipNegativeThrowsException() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        try {
            zais.skip(-1L);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        } finally {
            zais.close();
        }
    }

    @Test
    public void testSkipZeroOrPositive() throws Throwable {
        byte[] data = new byte[] { 1, 2, 3, 4, 5 };
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        
        long skipped = zais.skip(0);
        assertEquals(0L, skipped);

        skipped = zais.skip(2);
        assertEquals(2L, skipped);

        skipped = zais.skip(100); // Beyond end of stream
        assertEquals(3L, skipped);

        zais.close();
    }

    @Test
    public void testReadClosedStreamThrowsException() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        zais.close();

        try {
            zais.read(new byte[1], 0, 1);
            fail("Should have thrown IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("closed"));
        }
    }

    @Test
    public void testReadWithInvalidBufferBounds() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        
        byte[] buf = new byte[10];
        try {
            zais.read(buf, -1, 5);
            fail("Should have thrown ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // expected
        }

        try {
            zais.read(buf, 0, -1);
            fail("Should have thrown ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // expected
        }

        try {
            zais.read(buf, 5, 10);
            fail("Should have thrown ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // expected
        }

        zais.close();
    }

    @Test
    public void testGetNextZipEntryOnEmptyStream() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        assertNull(zais.getNextZipEntry());
        assertNull(zais.getNextEntry());
        zais.close();
    }

    @Test
    public void testReadWithoutCurrentEntryReturnsMinusOne() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        assertEquals(-1, zais.read(new byte[5], 0, 5));
        zais.close();
    }
}