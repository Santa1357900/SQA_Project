package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ZipArchiveInputStreamTest {

    @Test
    public void testMatchesNull() throws Throwable {
        boolean result = ZipArchiveInputStream.matches(null, 0);
        assertFalse(result);
    }

    @Test
    public void testMatchesShortLength() throws Throwable {
        byte[] sig = new byte[] { 0x50, 0x4b };
        boolean result = ZipArchiveInputStream.matches(sig, 2);
        assertFalse(result);
    }

    @Test
    public void testMatchesValidLFH() throws Throwable {
        byte[] sig = new byte[] { 0x50, 0x4b, 0x03, 0x04, 0x00, 0x00 };
        boolean result = ZipArchiveInputStream.matches(sig, sig.length);
        assertTrue(result);
    }

    @Test
    public void testMatchesValidEOCD() throws Throwable {
        byte[] sig = new byte[] { 0x50, 0x4b, 0x05, 0x06, 0x00, 0x00 };
        boolean result = ZipArchiveInputStream.matches(sig, sig.length);
        assertTrue(result);
    }

    @Test
    public void testConstructorAndGetNextEntryEmpty() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        assertNull(zais.getNextZipEntry());
        zais.close();
    }

    @Test
    public void testReadOnClosedStream() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        zais.close();
        
        boolean thrown = false;
        try {
            byte[] buf = new byte[10];
            zais.read(buf, 0, 10);
        } catch (IOException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testSkipNegative() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        
        boolean thrown = false;
        try {
            zais.skip(-1L);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
        zais.close();
    }

    @Test
    public void testSkipZeroAndPositive() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        long skipped = zais.skip(0);
        assertEquals(0L, skipped);
        
        long skippedPos = zais.skip(5);
        assertEquals(0L, skippedPos); // since current entry is null, read returns -1
        zais.close();
    }

    @Test
    public void testReadInvalidBounds() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[10]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        
        boolean thrown = false;
        try {
            byte[] buf = new byte[5];
            zais.read(buf, -1, 5);
        } catch (ArrayIndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);
        zais.close();
    }

    @Test
    public void testGetNextZipEntryCentralDirectory() throws Throwable {
        byte[] cfhSig = new byte[] { 0x50, 0x4b, 0x01, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        ByteArrayInputStream bais = new ByteArrayInputStream(cfhSig);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        assertNull(zais.getNextZipEntry());
        zais.close();
    }

    @Test
    public void testGetNextZipEntryInvalidSignature() throws Throwable {
        byte[] invalidSig = new byte[] { 0x00, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        ByteArrayInputStream bais = new ByteArrayInputStream(invalidSig);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais);
        assertNull(zais.getNextZipEntry());
        zais.close();
    }

    @Test
    public void testCustomEncodingConstructor() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais, "UTF8", false);
        assertNull(zais.getNextEntry());
        zais.close();
    }
}