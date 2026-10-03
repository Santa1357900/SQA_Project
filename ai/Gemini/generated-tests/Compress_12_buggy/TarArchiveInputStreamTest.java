package org.apache.commons.compress.archivers.tar;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TarArchiveInputStreamTest {

    @Test
    public void testConstructorAndGetters() throws Throwable {
        byte[] emptyData = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(emptyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        assertEquals(TarBuffer.DEFAULT_RCDSIZE, tais.getRecordSize());
        assertNull(tais.getCurrentEntry());
        assertFalse(tais.isAtEOF());
        assertEquals(0, tais.available());
        
        tais.reset();
        tais.close();
    }

    @Test
    public void testConstructorsWithSizes() throws Throwable {
        byte[] emptyData = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(emptyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais, 512, 512);

        assertEquals(512, tais.getRecordSize());
        tais.close();

        ByteArrayInputStream bais2 = new ByteArrayInputStream(emptyData);
        TarArchiveInputStream tais2 = new TarArchiveInputStream(bais2, 1024);
        assertEquals(512, tais2.getRecordSize());
        tais2.close();
    }

    @Test
    public void testMatchesNullAndShort() throws Throwable {
        assertFalse(TarArchiveInputStream.matches(null, 0));
        byte[] shortSig = new byte[10];
        assertFalse(TarArchiveInputStream.matches(shortSig, shortSig.length));
    }

    @Test
    public void testMatchesValidSignatures() throws Throwable {
        byte[] sig = new byte[512];
        
        // POSIX
        System.arraycopy(TarConstants.MAGIC_POSIX.getBytes("US-ASCII"), 0, sig, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_POSIX.getBytes("US-ASCII"), 0, sig, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(sig, sig.length));

        // GNU Space
        System.arraycopy(TarConstants.MAGIC_GNU.getBytes("US-ASCII"), 0, sig, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_GNU_SPACE.getBytes("US-ASCII"), 0, sig, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(sig, sig.length));

        // GNU Zero
        System.arraycopy(TarConstants.VERSION_GNU_ZERO.getBytes("US-ASCII"), 0, sig, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(sig, sig.length));

        // Ant
        System.arraycopy(TarConstants.MAGIC_ANT.getBytes("US-ASCII"), 0, sig, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_ANT.getBytes("US-ASCII"), 0, sig, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(sig, sig.length));
    }

    @Test
    public void testCanReadEntryData() throws Throwable {
        byte[] emptyData = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(emptyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        ArchiveEntry nonTarEntry = new ArchiveEntry() {
            public String getName() { return "test"; }
            public long getSize() { return 0; }
            public boolean isDirectory() { return false; }
            public java.util.Date getLastModifiedDate() { return new java.util.Date(); }
        };

        assertFalse(tais.canReadEntryData(nonTarEntry));

        TarArchiveEntry tarEntry = new TarArchiveEntry("testfile");
        assertTrue(tais.canReadEntryData(tarEntry));

        tais.close();
    }

    @Test
    public void testParsePaxHeaders() throws Throwable {
        byte[] emptyData = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(emptyData);
        final TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        String paxData = "11 path=foo\n";
        java.io.StringReader reader = new java.io.StringReader(paxData);
        Map<String, String> headers = tais.parsePaxHeaders(reader);
        assertNotNull(headers);
        assertEquals("foo", headers.get("path"));
        tais.close();
    }

    @Test
    public void testParsePaxHeadersFailure() throws Throwable {
        byte[] emptyData = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(emptyData);
        final TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        // Length indicates more chars than available
        String paxData = "50 path=foo\n";
        java.io.StringReader reader = new java.io.StringReader(paxData);
        try {
            tais.parsePaxHeaders(reader);
            fail("Expected IOException due to short read");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Failed to read Paxheader"));
        }
        tais.close();
    }

    @Test
    public void testSkipAndReadOnEmpty() throws Throwable {
        byte[] emptyData = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(emptyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        assertEquals(0, tais.skip(10));
        byte[] buf = new byte[10];
        assertEquals(-1, tais.read(buf, 0, 10));

        tais.close();
    }
}