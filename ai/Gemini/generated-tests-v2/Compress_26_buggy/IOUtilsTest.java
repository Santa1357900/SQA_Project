package org.apache.commons.compress.utils;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class IOUtilsTest {

    @Test
    public void testCopyStream() throws Throwable {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long count = IOUtils.copy(in, out);
        assertEquals(5L, count);
        
        byte[] result = out.toByteArray();
        assertEquals(5, result.length);
        assertEquals(1, result[0]);
        assertEquals(5, result[4]);
    }

    @Test
    public void testCopyStreamWithBufferSize() throws Throwable {
        byte[] data = new byte[]{10, 20, 30, 40, 50, 60, 70};
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long count = IOUtils.copy(in, out, 2);
        assertEquals(7L, count);
        
        byte[] result = out.toByteArray();
        assertEquals(7, result.length);
        assertEquals(10, result[0]);
        assertEquals(70, result[6]);
    }

    @Test
    public void testSkipZeroAndPositive() throws Throwable {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        ByteArrayInputStream in = new ByteArrayInputStream(data);

        long skipped = IOUtils.skip(in, 2L);
        assertEquals(2L, skipped);

        byte[] remaining = new byte[3];
        int read = in.read(remaining);
        assertEquals(3, read);
        assertEquals(3, remaining[0]);
        assertEquals(5, remaining[2]);
    }

    @Test
    public void testSkipBeyondLength() throws Throwable {
        byte[] data = new byte[]{1, 2, 3};
        ByteArrayInputStream in = new ByteArrayInputStream(data);

        long skipped = IOUtils.skip(in, 10L);
        assertEquals(3L, skipped);
    }

    @Test
    public void testSkipWithZeroReturningStream() throws Throwable {
        InputStream dummyIn = new InputStream() {
            public int read() throws IOException {
                return 0;
            }
            public long skip(long n) throws IOException {
                return 0L; // Simulate skip returning 0
            }
        };

        long skipped = IOUtils.skip(dummyIn, 5L);
        assertEquals(0L, skipped);
    }

    @Test
    public void testReadFullyArray() throws Throwable {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        byte[] b = new byte[3];

        int read = IOUtils.readFully(in, b);
        assertEquals(3, read);
        assertEquals(1, b[0]);
        assertEquals(3, b[2]);
    }

    @Test
    public void testReadFullyWithOffsetAndLen() throws Throwable {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        byte[] b = new byte[5];

        int read = IOUtils.readFully(in, b, 1, 3);
        assertEquals(3, read);
        assertEquals(0, b[0]);
        assertEquals(1, b[1]);
        assertEquals(3, b[3]);
        assertEquals(0, b[4]);
    }

    @Test
    public void testReadFullyIndexOutOfBounds() throws Throwable {
        byte[] data = new byte[]{1, 2, 3};
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        byte[] b = new byte[3];

        try {
            IOUtils.readFully(in, b, 0, 4);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        try {
            IOUtils.readFully(in, b, -1, 2);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        try {
            IOUtils.readFully(in, b, 2, -1);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }

    @Test
    public void testToByteArray() throws Throwable {
        byte[] data = new byte[]{9, 8, 7, 6};
        ByteArrayInputStream in = new ByteArrayInputStream(data);

        byte[] result = IOUtils.toByteArray(in);
        assertNotNull(result);
        assertEquals(4, result.length);
        assertEquals(9, result[0]);
        assertEquals(6, result[3]);
    }

    @Test(expected = NullPointerException.class)
    public void testToByteArrayNull() throws Throwable {
        IOUtils.toByteArray(null);
    }

    @Test
    public void testCloseQuietly() throws Throwable {
        Closeable normalCloseable = new Closeable() {
            public void close() throws IOException {
                // do nothing
            }
        };
        IOUtils.closeQuietly(normalCloseable);

        Closeable throwingCloseable = new Closeable() {
            public void close() throws IOException {
                throw new IOException("Failed to close");
            }
        };
        IOUtils.closeQuietly(throwingCloseable);

        IOUtils.closeQuietly(null);
    }
}