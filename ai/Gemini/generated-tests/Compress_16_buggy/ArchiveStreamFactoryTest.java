package org.apache.commons.compress.archivers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.Test;

public class ArchiveStreamFactoryTest {

    @Test
    public void testCreateArchiveInputStreamValidNames() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        byte[] dummyData = new byte[1024];

        InputStream arIn = new ByteArrayInputStream(dummyData);
        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.AR, arIn));

        InputStream zipIn = new ByteArrayInputStream(dummyData);
        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, zipIn));

        InputStream tarIn = new ByteArrayInputStream(dummyData);
        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.TAR, tarIn));

        InputStream jarIn = new ByteArrayInputStream(dummyData);
        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.JAR, jarIn));

        InputStream cpioIn = new ByteArrayInputStream(dummyData);
        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.CPIO, cpioIn));

        InputStream dumpIn = new ByteArrayInputStream(dummyData);
        assertNotNull(factory.createArchiveInputStream(ArchiveStreamFactory.DUMP, dumpIn));
    }

    @Test
    public void testCreateArchiveInputStreamCaseInsensitive() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        byte[] dummyData = new byte[1024];
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
            assertTrue(e.getMessage().contains("Archivername must not be null"));
        }
    }

    @Test
    public void testCreateArchiveInputStreamNullStream() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        try {
            factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, (InputStream) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("InputStream must not be null"));
        }
    }

    @Test
    public void testCreateArchiveInputStreamUnknown() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        InputStream in = new ByteArrayInputStream(new byte[10]);
        try {
            factory.createArchiveInputStream("unknown-format", in);
            fail("Expected ArchiveException");
        } catch (ArchiveException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testCreateArchiveOutputStreamValidNames() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        OutputStream out = new ByteArrayOutputStream();

        assertNotNull(factory.createArchiveOutputStream(ArchiveStreamFactory.AR, out));
        assertNotNull(factory.createArchiveOutputStream(ArchiveStreamFactory.ZIP, out));
        assertNotNull(factory.createArchiveOutputStream(ArchiveStreamFactory.TAR, out));
        assertNotNull(factory.createArchiveOutputStream(ArchiveStreamFactory.JAR, out));
        assertNotNull(factory.createArchiveOutputStream(ArchiveStreamFactory.CPIO, out));
    }

    @Test
    public void testCreateArchiveOutputStreamNullName() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        OutputStream out = new ByteArrayOutputStream();
        try {
            factory.createArchiveOutputStream(null, out);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Archivername must not be null"));
        }
    }

    @Test
    public void testCreateArchiveOutputStreamNullStream() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        try {
            factory.createArchiveOutputStream(ArchiveStreamFactory.ZIP, (OutputStream) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("OutputStream must not be null"));
        }
    }

    @Test
    public void testCreateArchiveOutputStreamUnknown() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        OutputStream out = new ByteArrayOutputStream();
        try {
            factory.createArchiveOutputStream("unknown-format", out);
            fail("Expected ArchiveException");
        } catch (ArchiveException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testAutodetectNullStream() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        try {
            factory.createArchiveInputStream((InputStream) null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Stream must not be null"));
        }
    }

    @Test
    public void testAutodetectMarkNotSupported() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        InputStream in = new InputStream() {
            public int read() {
                return -1;
            }
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
    public void testAutodetectUnknownSignature() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        byte[] dummy = new byte[600];
        InputStream in = new BufferedInputStream(new ByteArrayInputStream(dummy));
        try {
            factory.createArchiveInputStream(in);
            fail("Expected ArchiveException");
        } catch (ArchiveException e) {
            assertTrue(e.getMessage().contains("No Archiver found"));
        }
    }
}