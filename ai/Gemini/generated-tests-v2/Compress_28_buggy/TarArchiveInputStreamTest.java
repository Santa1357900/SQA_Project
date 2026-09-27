package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class TarArchiveInputStreamTest {

    @Test
    public void testConstructorsAndGetters() throws Throwable {
        byte[] dummyData = new byte[1024];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummyData);
        
        TarArchiveInputStream tais1 = new TarArchiveInputStream(bais);
        assertEquals(512, tais1.getRecordSize());
        tais1.close();

        ByteArrayInputStream bais2 = new ByteArrayInputStream(dummyData);
        TarArchiveInputStream tais2 = new TarArchiveInputStream(bais2, "UTF-8");
        assertEquals(512, tais2.getRecordSize());
        tais2.close();

        ByteArrayInputStream bais3 = new ByteArrayInputStream(dummyData);
        TarArchiveInputStream tais3 = new TarArchiveInputStream(bais3, 1024);
        assertEquals(512, tais3.getRecordSize());
        tais3.close();

        ByteArrayInputStream bais4 = new ByteArrayInputStream(dummyData);
        TarArchiveInputStream tais4 = new TarArchiveInputStream(bais4, 1024, "UTF-8");
        assertEquals(512, tais4.getRecordSize());
        tais4.close();

        ByteArrayInputStream bais5 = new ByteArrayInputStream(dummyData);
        TarArchiveInputStream tais5 = new TarArchiveInputStream(bais5, 1024, 256);
        assertEquals(256, tais5.getRecordSize());
        tais5.close();
    }

    @Test
    public void testMatches() throws Throwable {
        byte[] signatureShort = new byte[10];
        assertFalse(TarArchiveInputStream.matches(signatureShort, signatureShort.length));

        byte[] posixSig = new byte[512];
        System.arraycopy(TarConstants.MAGIC_POSIX.getBytes(), 0, posixSig, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_POSIX.getBytes(), 0, posixSig, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(posixSig, posixSig.length));

        byte[] gnuSpaceSig = new byte[512];
        System.arraycopy(TarConstants.MAGIC_GNU.getBytes(), 0, gnuSpaceSig, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_GNU_SPACE.getBytes(), 0, gnuSpaceSig, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(gnuSpaceSig, gnuSpaceSig.length));

        byte[] gnuZeroSig = new byte[512];
        System.arraycopy(TarConstants.MAGIC_GNU.getBytes(), 0, gnuZeroSig, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_GNU_ZERO.getBytes(), 0, gnuZeroSig, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(gnuZeroSig, gnuZeroSig.length));

        byte[] antSig = new byte[512];
        System.arraycopy(TarConstants.MAGIC_ANT.getBytes(), 0, antSig, TarConstants.MAGIC_OFFSET, TarConstants.MAGICLEN);
        System.arraycopy(TarConstants.VERSION_ANT.getBytes(), 0, antSig, TarConstants.VERSION_OFFSET, TarConstants.VERSIONLEN);
        assertTrue(TarArchiveInputStream.matches(antSig, antSig.length));

        byte[] invalidSig = new byte[512];
        assertFalse(TarArchiveInputStream.matches(invalidSig, invalidSig.length));
    }

    @Test
    public void testResetAndCanReadEntryData() throws Throwable {
        byte[] dummyData = new byte[10];
        ByteArrayInputStream bais = new ByteArrayInputStream(dummyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        tais.reset(); // Should do nothing without exception

        assertFalse(tais.canReadEntryData(null));
        assertFalse(tais.canReadEntryData(new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("test")));

        TarArchiveEntry normalEntry = new TarArchiveEntry("test.txt");
        assertTrue(tais.canReadEntryData(normalEntry));

        tais.close();
    }

    @Test
    public void testReadWithoutCurrentEntry() throws Throwable {
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
        tais.close();
    }

    @Test
    public void testGetNextTarEntryEmpty() throws Throwable {
        byte[] emptyData = new byte[0];
        ByteArrayInputStream bais = new ByteArrayInputStream(emptyData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        assertNull(tais.getNextTarEntry());
        assertNull(tais.getCurrentEntry());
        tais.close();
    }

    @Test
    public void testParsePaxHeaders() throws Throwable {
        String paxData = "15 path=foo/bar\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(paxData.getBytes("UTF-8"));
        TarArchiveInputStream tais = new TarArchiveInputStream(bais);

        java.util.Map<String, String> headers = tais.parsePaxHeaders(bais);
        assertNotNull(headers);
        assertEquals("foo/bar", headers.get("path"));
        tais.close();
    }
}