package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class TarArchiveInputStreamTest {

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        byte[] dummy = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummy);

        TarArchiveInputStream tais1 = new TarArchiveInputStream(bais);
        assertEquals(TarConstants.DEFAULT_RCDSIZE, tais1.getRecordSize());
        assertFalse(tais1.markSupported());
        assertNull(tais1.getCurrentEntry());
        assertFalse(tais1.isAtEOF());

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
        byte[] dummy = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummy);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        assertEquals(0L, tais.skip(0));
        assertEquals(0L, tais.skip(-5L));
    }

    @Test
    public void testMarkAndReset() throws Throwable {
        byte[] dummy = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummy);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        tais.mark(100);
        tais.reset();
        assertFalse(tais.markSupported());
    }

    @Test
    public void testAvailableWithZeroEntrySize() throws Throwable {
        byte[] dummy = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummy);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        assertEquals(0, tais.available());
    }

    @Test
    public void testCanReadEntryData() throws Throwable {
        byte[] dummy = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummy);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        assertFalse(tais.canReadEntryData(null));
        assertFalse(tais.canReadEntryData(new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("test")));
    }

    @Test
    public void testMatches() throws Throwable {
        byte[] header = new byte[512];
        assertFalse(TarArchiveInputStream.matches(header, 10));

        // Test POSIX magic
        System.arraycopy(TarConstants.MAGIC_POSIX.getBytes("US-ASCII"), 0, header, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_POSIX.getBytes("US-ASCII"), 0, header, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(header, header.length));

        // Test GNU magic with space
        System.arraycopy(TarConstants.MAGIC_GNU.getBytes("US-ASCII"), 0, header, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_GNU_SPACE.getBytes("US-ASCII"), 0, header, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(header, header.length));

        // Test GNU magic with zero
        System.arraycopy(TarConstants.VERSION_GNU_ZERO.getBytes("US-ASCII"), 0, header, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(header, header.length));

        // Test Ant magic
        System.arraycopy(TarConstants.MAGIC_ANT.getBytes("US-ASCII"), 0, header, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_ANT.getBytes("US-ASCII"), 0, header, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(header, header.length));

        // Test invalid
        byte[] invalidHeader = new byte[512];
        assertFalse(TarArchiveInputStream.matches(invalidHeader, invalidHeader.length));
    }

    @Test
    public void testReadWithoutCurrentEntryThrowsException() throws Throwable {
        byte[] dummy = new byte[512];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummy);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        // force hasHitEOF = false and entryOffset < entrySize condition
        // actually if currEntry is null and we call read, it throws IllegalStateException
        try {
            tais.read(new byte[10], 0, 10);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No current tar entry"));
        }
    }

    @Test
    public void testParsePaxHeaders() throws Throwable {
        String paxData = "25 path=a/b/c\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(paxData.getBytes("UTF-8"));
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        Map<String, String> headers = tais.parsePaxHeaders(bais);
        assertNotNull(headers);
        assertEquals("a/b/c", headers.get("path"));
    }

    @Test
    public void testClose() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);
        tais.close();
    }
}