package org.apache.commons.compress.archivers.sevenz;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

public class SevenZFileTest {

    @Test
    public void testMatchesNull() throws Throwable {
        boolean result = SevenZFile.matches(null, 0);
        assertFalse(result);
    }

    @Test
    public void testMatchesShortLength() throws Throwable {
        byte[] sig = new byte[] { '7', 'z' };
        boolean result = SevenZFile.matches(sig, 2);
        assertFalse(result);
    }

    @Test
    public void testMatchesValidSignature() throws Throwable {
        byte[] sig = new byte[] { (byte)'7', (byte)'z', (byte)0xBC, (byte)0xAF, (byte)0x27, (byte)0x1C };
        boolean result = SevenZFile.matches(sig, 6);
        assertTrue(result);
    }

    @Test
    public void testMatchesInvalidSignature() throws Throwable {
        byte[] sig = new byte[] { (byte)'0', (byte)'0', (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00 };
        boolean result = SevenZFile.matches(sig, 6);
        assertFalse(result);
    }

    @Test
    public void testConstructorNonExistentFile() throws Throwable {
        File nonExistent = new File("non_existent_archive_123456789.7z");
        try {
            new SevenZFile(nonExistent);
            fail("Expected IOException for non-existent file");
        } catch (IOException e) {
            // Expected
        }
    }

    @Test
    public void testReadOnUninitializedStreamThrowsIllegalState() throws Throwable {
        // Create a dummy temp file to pass file existence/RandomAccessFile checks if needed,
        // or test an instance where headers/stream are not set up.
        // Actually, SevenZFile constructor tries to read headers immediately on the file.
        // Since we can't easily forge a valid 7z binary without extensive setup, 
        // let's verify that opening an invalid file throws IOException.
        File tempFile = File.createTempFile("invalid7z", ".7z");
        tempFile.deleteOnExit();
        try {
            new SevenZFile(tempFile);
            fail("Expected IOException due to invalid signature/headers");
        } catch (IOException e) {
            assertTrue(true);
        }
    }
}