package org.apache.commons.compress.archivers.ar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class ArArchiveInputStreamTest {

    @Test
    public void testMatchesValid() throws Throwable {
        byte[] validHeader = ArArchiveEntry.HEADER.getBytes();
        assertTrue(ArArchiveInputStream.matches(validHeader, validHeader.length));
    }

    @Test
    public void testMatchesShortLength() throws Throwable {
        byte[] validHeader = ArArchiveEntry.HEADER.getBytes();
        assertFalse(ArArchiveInputStream.matches(validHeader, 7));
    }

    @Test
    public void testMatchesInvalidSignature() throws Throwable {
        byte[] invalidHeader = "!<arch>\n".getBytes();
        invalidHeader[0] = 0x00;
        assertFalse(ArArchiveInputStream.matches(invalidHeader, invalidHeader.length));
    }

    @Test
    public void testCloseStream() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        ArArchiveInputStream ais = new ArArchiveInputStream(bais);
        ais.close();
        // Closing again should be safe and idempotent
        ais.close();
        assertTrue(true);
    }

    @Test
    public void testReadSingleByte() throws Throwable {
        byte[] data = new byte[] { 1, 2, 3 };
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ArArchiveInputStream ais = new ArArchiveInputStream(bais);
        
        int val = ais.read();
        assertEquals(1, val);
        
        int val2 = ais.read(new byte[2]);
        assertEquals(2, val2);
        
        ais.close();
    }

    @Test
    public void testGetNextArEntryEmptyStream() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        ArArchiveInputStream ais = new ArArchiveInputStream(bais);
        
        try {
            ais.getNextArEntry();
            fail("Expected IOException due to missing header");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("failed to read header"));
        }
        ais.close();
    }

    @Test
    public void testGetNextArEntryInvalidHeader() throws Throwable {
        byte[] badHeader = "INVALID_HEADER_DATA_12345".getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(badHeader);
        ArArchiveInputStream ais = new ArArchiveInputStream(bais);
        
        try {
            ais.getNextArEntry();
            fail("Expected IOException due to invalid header");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("invalid header"));
        }
        ais.close();
    }

    @Test
    public void testGetNextArEntryEOFBeforeMetadata() throws Throwable {
        byte[] headerOnly = ArArchiveEntry.HEADER.getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(headerOnly);
        ArArchiveInputStream ais = new ArArchiveInputStream(bais);
        
        // available() is 0 after header is read
        ArArchiveEntry entry = ais.getNextArEntry();
        assertNull(entry);
        ais.close();
    }
}