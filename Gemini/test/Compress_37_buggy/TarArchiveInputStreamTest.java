package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class TarArchiveInputStreamTest {

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        byte[] dummyData = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummyData);

        TarArchiveInputStream tais1 = new TarArchiveInputStream(bais);
        assertEquals(TarConstants.DEFAULT_RCDSIZE, tais1.getRecordSize());
        assertFalse(tais1.markSupported());

        TarArchiveInputStream tais2 = new TarArchiveInputStream(bais, "UTF-8");
        assertEquals("UTF-8", tais2.encoding);

        TarArchiveInputStream tais3 = new TarArchiveInputStream(bais, 1024);
        assertEquals(TarConstants.DEFAULT_RCDSIZE, tais3.getRecordSize());

        TarArchiveInputStream tais4 = new TarArchiveInputStream(bais, 1024, "UTF-8");
        assertEquals("UTF-8", tais4.encoding);

        TarArchiveInputStream tais5 = new TarArchiveInputStream(bais, 1024, 512);
        assertEquals(512, tais5.getRecordSize());

        TarArchiveInputStream tais6 = new TarArchiveInputStream(bais, 1024, 512, "UTF-8");
        assertEquals(512, tais6.getRecordSize());
        assertEquals("UTF-8", tais6.encoding);
    }

    @Test
    public void testSkipNegativeOrZero() throws Throwable {
        byte[] dummyData = new byte[100];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        assertEquals(0L, tais.skip(0L));
        assertEquals(0L, tais.skip(-5L));
    }

    @Test
    public void testMarkAndReset() throws Throwable {
        byte[] dummyData = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        tais.mark(10);
        tais.reset();
        assertFalse(tais.markSupported());
    }

    @Test
    public void testMatchesNullAndShort() throws Throwable {
        assertFalse(TarArchiveInputStream.matches(null, 0));
        assertFalse(TarArchiveInputStream.matches(new byte[5], 5));
    }

    @Test
    public void testMatchesMagicValid() throws Throwable {
        byte[] signature = new byte[512];
        
        // Test POSIX
        System.arraycopy(TarConstants.MAGIC_POSIX.getBytes("US-ASCII"), 0, signature, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_POSIX.getBytes("US-ASCII"), 0, signature, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(signature, signature.length));

        // Test GNU Space
        System.arraycopy(TarConstants.MAGIC_GNU.getBytes("US-ASCII"), 0, signature, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_GNU_SPACE.getBytes("US-ASCII"), 0, signature, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(signature, signature.length));

        // Test GNU Zero
        System.arraycopy(TarConstants.MAGIC_GNU.getBytes("US-ASCII"), 0, signature, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_GNU_ZERO.getBytes("US-ASCII"), 0, signature, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(signature, signature.length));

        // Test Ant
        System.arraycopy(TarConstants.MAGIC_ANT.getBytes("US-ASCII"), 0, signature, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_ANT.getBytes("US-ASCII"), 0, signature, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(signature, signature.length));
    }

    @Test
    public void testCanReadEntryData() throws Throwable {
        byte[] dummyData = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        assertFalse(tais.canReadEntryData(null));
        assertFalse(tais.canReadEntryData(new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("test")));
    }

    @Test
    public void testGetNextTarEntryAtEOF() throws Throwable {
        byte[] dummyData = new byte[512]; // Zero filled record
        ByteArrayInputStream bais = new ByteArrayInputStream(dummyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        assertNull(tais.getNextTarEntry());
        assertNull(tais.getNextTarEntry()); // already hit EOF
    }

    @Test
    public void testReadWhenNoCurrentEntry() throws Throwable {
        byte[] dummyData = new byte[512];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        byte[] buf = new byte[10];
        try {
            tais.read(buf, 0, 10);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No current tar entry"));
        }
    }

    @Test
    public void testParsePaxHeaders() throws Throwable {
        byte[] dummyData = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        String paxHeaderContent = "11 path=foo\n";
        ByteArrayInputStream paxInput = new ByteArrayInputStream(paxHeaderContent.getBytes("UTF-8"));
        Map<String, String> headers = tais.parsePaxHeaders(paxInput);
        assertEquals("foo", headers.get("path"));
    }

    @Test
    public void testClose() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);
        tais.close();
    }
}