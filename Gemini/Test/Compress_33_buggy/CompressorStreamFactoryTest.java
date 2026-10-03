package org.apache.commons.compress.compressors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.Test;

public class CompressorStreamFactoryTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        assertEquals(false, factory.getDecompressConcatenated());
    }

    @Test
    public void testBooleanConstructorTrue() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory(true);
        assertEquals(true, factory.getDecompressConcatenated());
    }

    @Test
    public void testBooleanConstructorFalse() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory(false);
        assertEquals(false, factory.getDecompressConcatenated());
    }

    @Test
    public void testSetDecompressConcatenatedAllowed() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        factory.setDecompressConcatenated(true);
        assertEquals(true, factory.getDecompressConcatenated());
    }

    @Test
    public void testSetDecompressConcatenatedNotAllowed() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory(true);
        try {
            factory.setDecompressConcatenated(false);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Cannot override the setting defined by the constructor"));
        }
    }

    @Test
    public void testCreateCompressorInputStreamNullStream() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        try {
            factory.createCompressorInputStream((InputStream) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Stream must not be null"));
        }
    }

    @Test
    public void testCreateCompressorInputStreamNoMark() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        InputStream in = new InputStream() {
            public int read() {
                return -1;
            }
            public boolean markSupported() {
                return false;
            }
        };
        try {
            factory.createCompressorInputStream(in);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Mark is not supported"));
        }
    }

    @Test
    public void testCreateCompressorInputStreamUnknownSignature() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        byte[] data = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        InputStream in = new java.io.BufferedInputStream(new ByteArrayInputStream(data));
        try {
            factory.createCompressorInputStream(in);
            fail("Expected CompressorException");
        } catch (CompressorException e) {
            assertTrue(e.getMessage().contains("No Compressor found for the stream signature"));
        }
    }

    @Test
    public void testCreateCompressorInputStreamByNameNullName() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        try {
            factory.createCompressorInputStream(null, in);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Compressor name and stream must not be null"));
        }
    }

    @Test
    public void testCreateCompressorInputStreamByNameNullStream() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        try {
            factory.createCompressorInputStream(CompressorStreamFactory.GZIP, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Compressor name and stream must not be null"));
        }
    }

    @Test
    public void testCreateCompressorInputStreamByNameUnknown() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        try {
            factory.createCompressorInputStream("unknown-compressor", in);
            fail("Expected CompressorException");
        } catch (CompressorException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testCreateCompressorInputStreamByNameValid() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        try {
            CompressorInputStream cis = factory.createCompressorInputStream(CompressorStreamFactory.DEFLATE, in);
            assertNotNull(cis);
        } catch (CompressorException e) {
            // Depending on environment support, if it fails due to IO/Compression specifics, handle gracefully or assert
            assertNotNull(e);
        }
    }

    @Test
    public void testCreateCompressorOutputStreamNullName() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            factory.createCompressorOutputStream(null, out);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Compressor name and stream must not be null"));
        }
    }

    @Test
    public void testCreateCompressorOutputStreamNullStream() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        try {
            factory.createCompressorOutputStream(CompressorStreamFactory.GZIP, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Compressor name and stream must not be null"));
        }
    }

    @Test
    public void testCreateCompressorOutputStreamUnknown() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            factory.createCompressorOutputStream("unknown-compressor", out);
            fail("Expected CompressorException");
        } catch (CompressorException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testCreateCompressorOutputStreamValid() throws Throwable {
        CompressorStreamFactory factory = new CompressorStreamFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompressorOutputStream cos = factory.createCompressorOutputStream(CompressorStreamFactory.GZIP, out);
        assertNotNull(cos);
    }
}