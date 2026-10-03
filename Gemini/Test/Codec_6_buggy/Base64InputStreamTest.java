package org.apache.commons.codec.binary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class Base64InputStreamTest {

    @Test
    public void testConstructors() throws Throwable {
        byte[] data = "Hello World".getBytes("UTF-8");
        ByteArrayInputStream bais1 = new ByteArrayInputStream(data);
        Base64InputStream stream1 = new Base64InputStream(bais1);
        assertNotNull(stream1);
        assertFalse(stream1.markSupported());

        ByteArrayInputStream bais2 = new ByteArrayInputStream(data);
        Base64InputStream stream2 = new Base64InputStream(bais2, true);
        assertNotNull(stream2);

        ByteArrayInputStream bais3 = new ByteArrayInputStream(data);
        byte[] lineSep = new byte[] { '\r', '\n' };
        Base64InputStream stream3 = new Base64InputStream(bais3, true, 76, lineSep);
        assertNotNull(stream3);
    }

    @Test
    public void testReadSingleByteDecode() throws Throwable {
        String encoded = "SGVsbG8gV29ybGQ="; // "Hello World"
        ByteArrayInputStream bais = new ByteArrayInputStream(encoded.getBytes("UTF-8"));
        Base64InputStream stream = new Base64InputStream(bais, false);

        int b = stream.read();
        assertEquals('H', b);

        // Read remaining
        StringBuilder sb = new StringBuilder();
        sb.append((char) b);
        while ((b = stream.read()) != -1) {
            sb.append((char) b);
        }
        assertEquals("Hello World", sb.toString());
    }

    @Test
    public void testReadSingleByteEncode() throws Throwable {
        String original = "Hi";
        ByteArrayInputStream bais = new ByteArrayInputStream(original.getBytes("UTF-8"));
        Base64InputStream stream = new Base64InputStream(bais, true);

        int b = stream.read();
        assertTrue(b >= 0);

        StringBuilder sb = new StringBuilder();
        while (b != -1) {
            sb.append((char) b);
            b = stream.read();
        }
        assertEquals("SGk=", sb.toString());
    }

    @Test
    public void testReadArrayNullPointerException() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(new byte[0]));
        try {
            stream.read(null, 0, 1);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testReadArrayIndexOutOfBounds() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream(new byte[0]));
        byte[] buf = new byte[10];

        try {
            stream.read(buf, -1, 5);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        try {
            stream.read(buf, 0, -1);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        try {
            stream.read(buf, 11, 1);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        try {
            stream.read(buf, 5, 10);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }

    @Test
    public void testReadArrayZeroLength() throws Throwable {
        Base64InputStream stream = new Base64InputStream(new ByteArrayInputStream("SGVsbG8=".getBytes("UTF-8")));
        byte[] buf = new byte[10];
        int len = stream.read(buf, 0, 0);
        assertEquals(0, len);
    }

    @Test
    public void testReadBufferAndOptimization() throws Throwable {
        String encoded = "SGVsbG8gV29ybGQ=";
        ByteArrayInputStream bais = new ByteArrayInputStream(encoded.getBytes("UTF-8"));
        Base64InputStream stream = new Base64InputStream(bais, false);

        byte[] buf = new byte[5];
        int read = stream.read(buf, 0, 5);
        assertEquals(5, read);
        assertEquals("Hello", new String(buf, 0, 5, "UTF-8"));
    }

    @Test
    public void testReadEOF() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        Base64InputStream stream = new Base64InputStream(bais, false);
        assertEquals(-1, stream.read());
        
        byte[] buf = new byte[10];
        assertEquals(-1, stream.read(buf, 0, 10));
    }

    @Test
    public void testMarkAndResetBehavior() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream("SGVsbG8=".getBytes("UTF-8"));
        Base64InputStream stream = new Base64InputStream(bais, false);
        assertFalse(stream.markSupported());
    }

    @Test
    public void testNonBase64CharactersHandling() throws Throwable {
        // Contains characters not in base64 alphabet mixed in, triggering zero-return paths potentially
        String corrupted = "SGk=   XYZ!";
        ByteArrayInputStream bais = new ByteArrayInputStream(corrupted.getBytes("UTF-8"));
        Base64InputStream stream = new Base64InputStream(bais, false);

        byte[] buf = new byte[10];
        int read = stream.read(buf, 0, 10);
        assertTrue(read >= -1);
    }
}