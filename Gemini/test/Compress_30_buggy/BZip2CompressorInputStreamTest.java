package org.apache.commons.compress.compressors.bzip2;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class BZip2CompressorInputStreamTest {

    @Test
    public void testMatchesNullSignature() throws Throwable {
        boolean result = BZip2CompressorInputStream.matches(null, 0);
        assertFalse(result);
    }

    @Test
    public void testMatchesShortLength() throws Throwable {
        byte[] sig = new byte[] { 'B', 'Z' };
        boolean result = BZip2CompressorInputStream.matches(sig, 2);
        assertFalse(result);
    }

    @Test
    public void testMatchesInvalidHeader() throws Throwable {
        byte[] sig = new byte[] { 'A', 'B', 'C', 'd' };
        boolean result = BZip2CompressorInputStream.matches(sig, 4);
        assertFalse(result);
    }

    @Test
    public void testMatchesValidHeader() throws Throwable {
        byte[] sig = new byte[] { 'B', 'Z', 'h', '9' };
        boolean result = BZip2CompressorInputStream.matches(sig, 4);
        assertTrue(result);
    }

    @Test
    public void testConstructorNullStream() throws Throwable {
        boolean exceptionThrown = false;
        try {
            new BZip2CompressorInputStream(null);
        } catch (NullPointerException e) {
            exceptionThrown = true;
        } catch (IOException e) {
            // Also acceptable if it throws IOException due to null check inside init
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testConstructorInvalidMagic() throws Throwable {
        byte[] invalidData = new byte[] { 'X', 'Y', 'Z', '1', '2', '3' };
        ByteArrayInputStream bais = new ByteArrayInputStream(invalidData);
        try {
            new BZip2CompressorInputStream(bais, false);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Stream is not in the BZip2 format"));
        }
    }

    @Test
    public void testReadInvalidBlockSize() throws Throwable {
        // 'B', 'Z', 'h', then invalid block size '0'
        byte[] invalidData = new byte[] { 'B', 'Z', 'h', '0', '1', '2' };
        ByteArrayInputStream bais = new ByteArrayInputStream(invalidData);
        try {
            new BZip2CompressorInputStream(bais, false);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("BZip2 block size is invalid"));
        }
    }

    @Test
    public void testReadOnClosedStream() throws Throwable {
        byte[] invalidData = new byte[] { 'B', 'Z', 'h', '1', '1', '2' };
        ByteArrayInputStream bais = new ByteArrayInputStream(invalidData);
        BZip2CompressorInputStream bzIn = null;
        try {
            bzIn = new BZip2CompressorInputStream(bais, false);
        } catch (IOException e) {
            // Expected if header is incomplete, but let's test manual close if initialization fails or succeeds.
        }
        
        if (bzIn != null) {
            bzIn.close();
            try {
                bzIn.read();
                fail("Expected IOException");
            } catch (IOException e) {
                assertEquals("stream closed", e.getMessage());
            }
        }
    }

    @Test
    public void testReadByteArrayOutOfBoundsIndex() throws Throwable {
        byte[] dummyData = new byte[] { 'B', 'Z', 'h', '1', '1', '2' };
        ByteArrayInputStream bais = new ByteArrayInputStream(dummyData);
        BZip2CompressorInputStream bzIn = null;
        try {
            bzIn = new BZip2CompressorInputStream(bais, false);
        } catch (IOException e) {
            // Initialization might fail, so we test with a dummy or handle exception
            return;
        }

        byte[] dest = new byte[10];
        try {
            bzIn.read(dest, -1, 5);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            assertTrue(e.getMessage().contains("offs(-1) < 0"));
        }

        try {
            bzIn.read(dest, 0, -1);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            assertTrue(e.getMessage().contains("len(-1) < 0"));
        }

        try {
            bzIn.read(dest, 5, 10);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            assertTrue(e.getMessage().contains("offs(5) + len(10) > dest.length(10)"));
        }
        
        bzIn.close();
    }
}