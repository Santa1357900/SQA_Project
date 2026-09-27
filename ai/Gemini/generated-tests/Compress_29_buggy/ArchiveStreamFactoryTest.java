package org.apache.commons.compress.archivers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.junit.Test;

public class ArchiveStreamFactoryTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        assertNull(factory.getEntryEncoding());
    }

    @Test
    public void testEncodingConstructor() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory("UTF-8");
        org.junit.Assert.assertEquals("UTF-8", factory.getEntryEncoding());
    }

    @Test
    public void testSetEntryEncodingValid() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        factory.setEntryEncoding("UTF-8");
        org.junit.Assert.assertEquals("UTF-8", factory.getEntryEncoding());
    }

    @Test(expected = IllegalStateException.class)
    public void testSetEntryEncodingThrowsExceptionWhenEncodingSetInConstructor() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory("UTF-8");
        factory.setEntryEncoding("ISO-8859-1");
    }

    @Test
    public void testCreateArchiveInputStreamNullName() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
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
            factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("InputStream must not be null"));
        }
    }

    @Test
    public void testCreateArchiveInputStreamAr() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.AR, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamArj() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.ARJ, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamArjWithEncoding() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.ARJ, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamZip() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamZipWithEncoding() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamTar() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.TAR, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamTarWithEncoding() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.TAR, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamJar() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.JAR, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamJarWithEncoding() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.JAR, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamCpio() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.CPIO, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamCpioWithEncoding() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.CPIO, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamDump() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.DUMP, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test
    public void testCreateArchiveInputStreamDumpWithEncoding() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.DUMP, in);
        assertNotNull(ais);
        ais.close();
    }

    @Test(expected = StreamingNotSupportedException.class)
    public void testCreateArchiveInputStreamSevenZ() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        factory.createArchiveInputStream(ArchiveStreamFactory.SEVEN_Z, in);
    }

    @Test(expected = ArchiveException.class)
    public void testCreateArchiveInputStreamUnknown() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        factory.createArchiveInputStream("unknown-format", in);
    }

    @Test
    public void testCreateArchiveOutputStreamNullName() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
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
            factory.createArchiveOutputStream(ArchiveStreamFactory.ZIP, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("OutputStream must not be null"));
        }
    }

    @Test
    public void testCreateArchiveOutputStreamAr() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArchiveOutputStream aos = factory.createArchiveOutputStream(ArchiveStreamFactory.AR, out);
        assertNotNull(aos);
        aos.close();
    }

    @Test
    public void testCreateArchiveOutputStreamZip() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArchiveOutputStream aos = factory.createArchiveOutputStream(ArchiveStreamFactory.ZIP, out);
        assertNotNull(aos);
        aos.close();
    }

    @Test
    public void testCreateArchiveOutputStreamZipWithEncoding() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory("UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArchiveOutputStream aos = factory.createArchiveOutputStream(ArchiveStreamFactory.ZIP, out);
        assertNotNull(aos);
        aos.close();
    }

    @Test
    public void testCreateArchiveOutputStreamTar() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArchiveOutputStream aos = factory.createArchiveOutputStream(ArchiveStreamFactory.TAR, out);
        assertNotNull(aos);
        aos.close();
    }

    @Test
    public void testCreateArchiveOutputStreamTarWithEncoding() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory("UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArchiveOutputStream aos = factory.createArchiveOutputStream(ArchiveStreamFactory.TAR, out);
        assertNotNull(aos);
        aos.close();
    }

    @Test
    public void testCreateArchiveOutputStreamJar() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArchiveOutputStream aos = factory.createArchiveOutputStream(ArchiveStreamFactory.JAR, out);
        assertNotNull(aos);
        aos.close();
    }

    @Test
    public void testCreateArchiveOutputStreamCpio() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArchiveOutputStream aos = factory.createArchiveOutputStream(ArchiveStreamFactory.CPIO, out);
        assertNotNull(aos);
        aos.close();
    }

    @Test
    public void testCreateArchiveOutputStreamCpioWithEncoding() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory("UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArchiveOutputStream aos = factory.createArchiveOutputStream(ArchiveStreamFactory.CPIO, out);
        assertNotNull(aos);
        aos.close();
    }

    @Test(expected = StreamingNotSupportedException.class)
    public void testCreateArchiveOutputStreamSevenZ() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        factory.createArchiveOutputStream(ArchiveStreamFactory.SEVEN_Z, out);
    }

    @Test(expected = ArchiveException.class)
    public void testCreateArchiveOutputStreamUnknown() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        factory.createArchiveOutputStream("unknown-format", out);
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

    @Test(expected = ArchiveException.class)
    public void testAutodetectInputStreamUnknownSignature() throws Throwable {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        java.io.BufferedInputStream bin = new java.io.BufferedInputStream(new ByteArrayInputStream(new byte[600]));
        factory.createArchiveInputStream(bin);
    }
}