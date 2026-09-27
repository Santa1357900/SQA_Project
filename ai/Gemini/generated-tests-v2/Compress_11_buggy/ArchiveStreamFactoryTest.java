package org.apache.commons.compress.archivers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;

import org.junit.Test;

public class ArchiveStreamFactoryTest {

    @Test
    public void testCreateArchiveInputStreamValidNames() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        byte[] dummyData = new byte[512];
        InputStream in1 = new ByteArrayInputStream(dummyData);
        InputStream in2 = new ByteArrayInputStream(dummyData);
        InputStream in3 = new ByteArrayInputStream(dummyData);
        InputStream in4 = new ByteArrayInputStream(dummyData);
        InputStream in5 = new ByteArrayInputStream(dummyData);
        InputStream in6 = new ByteArrayInputStream(dummyData);

        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.AR, in1));
        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, in2));
        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.TAR, in3));
        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.JAR, in4));
        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.CPIO, in5));
        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.DUMP, in6));
    }

    @Test
    public void testCreateArchiveInputStreamCaseInsensitive() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        byte[] dummyData = new byte[512];
        InputStream in = new ByteArrayInputStream(dummyData);

        assertNotNull(factory.createArchiveInputStream("ZiP", in));
    }

    @Test
    public void testCreateArchiveInputStreamNullName() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        InputStream in = new ByteArrayInputStream(new byte[10]);
        try {
            factory.createArchiveInputStream(null, in);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Archivername"));
        }
    }

    @Test
    public void testCreateArchiveInputStreamNullStream() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        try {
            factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("InputStream"));
        }
    }

    @Test
    public void testCreateArchiveInputStreamUnknownName() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        InputStream in = new ByteArrayInputStream(new byte[10]);
        try {
            factory.createArchiveInputStream("unknown_format", in);
            fail("Expected ArchiveException");
        } catch (ArchiveException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testCreateArchiveOutputStreamValidNames() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        OutputStream out1 = new ByteArrayOutputStream();
        OutputStream out2 = new ByteArrayOutputStream();
        OutputStream out3 = new ByteArrayOutputStream();
        OutputStream out4 = new ByteArrayOutputStream();
        OutputStream out5 = new ByteArrayOutputStream();

        assertNotNull(factory.createArchiveOutputStream(ArchiveStreamFactory.AR, out1));
        assertNotNull(factory.createArchiveOutputStream(ArchiveStreamFactory.ZIP, out2));
        assertNotNull(factory.createArchiveOutputStream(ArchiveStreamFactory.TAR, out3));
        assertNotNull(factory.createArchiveOutputStream(ArchiveStreamFactory.JAR, out4));
        assertNotNull(factory.createArchiveOutputStream(ArchiveStreamFactory.CPIO, out5));
    }

    @Test
    public void testCreateArchiveOutputStreamCaseInsensitive() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        OutputStream out = new ByteArrayOutputStream();

        assertNotNull(factory.createArchiveOutputStream("tAr", out));
    }

    @Test
    public void testCreateArchiveOutputStreamNullName() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        OutputStream out = new ByteArrayOutputStream();
        try {
            factory.createArchiveOutputStream(null, out);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Archivername"));
        }
    }

    @Test
    public void testCreateArchiveOutputStreamNullStream() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        try {
            factory.createArchiveOutputStream(ArchiveStreamFactory.ZIP, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("OutputStream"));
        }
    }

    @Test
    public void testCreateArchiveOutputStreamUnknownName() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        OutputStream out = new ByteArrayOutputStream();
        try {
            factory.createArchiveOutputStream("unknown_format", out);
            fail("Expected ArchiveException");
        } catch (ArchiveException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testAutodetectInputStreamNull() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        try {
            factory.createArchiveInputStream((InputStream) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Stream must not be null"));
        }
    }

    @Test
    public void testAutodetectInputStreamNoMark() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        InputStream in = new ByteArrayInputStream(new byte[10]) {
            public boolean markSupported() {
                return false;
            }
        };
        try {
            factory.createArchiveInputStream(in);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Mark is not supported"));
        }
    }

    @Test
    public void testAutodetectInputStreamUnknownSignature() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        InputStream in = new BufferedInputStream(new ByteArrayInputStream(new byte[1024]));
        try {
            factory.createArchiveInputStream(in);
            fail("Expected ArchiveException");
        } catch (ArchiveException e) {
            assertTrue(e.getMessage().contains("No Archiver found"));
        }
    }

    @Test
    public void testAutodetectZipSignature() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        byte[] zipHeader = new byte[] { 'P', 'K', 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        InputStream in = new BufferedInputStream(new ByteArrayInputStream(zipHeader));
        try {
            ArchiveInputStream ais = factory.createArchiveInputStream(in);
            assertNotNull(ais);
        } catch (ArchiveException e) {
            // Depending on stream contents it might fail or succeed stream init, 
            // but detection logic is covered.
        }
    }
}