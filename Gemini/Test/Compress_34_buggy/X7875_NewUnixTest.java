package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.zip.ZipException;

public class X7875_NewUnixTest {

    @Test
    public void testConstructorAndGetters() throws Throwable {
        X7875_NewUnix extra = new X7875_NewUnix();
        assertEquals(0x7875, extra.getHeaderId().getValue());
        assertEquals(1000L, extra.getUID());
        assertEquals(1000L, extra.getGID());
    }

    @Test
    public void testSettersAndGetters() throws Throwable {
        X7875_NewUnix extra = new X7875_NewUnix();
        extra.setUID(12345L);
        extra.setGID(67890L);
        assertEquals(12345L, extra.getUID());
        assertEquals(67890L, extra.getGID());

        extra.setUID(0L);
        extra.setGID(0L);
        assertEquals(0L, extra.getUID());
        assertEquals(0L, extra.getGID());
    }

    @Test
    public void testLengths() throws Throwable {
        X7875_NewUnix extra = new X7875_NewUnix();
        extra.setUID(1000L);
        extra.setGID(1000L);
        
        ZipShort localLen = extra.getLocalFileDataLength();
        ZipShort cdLen = extra.getCentralDirectoryLength();
        assertEquals(localLen.getValue(), cdLen.getValue());
        assertTrue(localLen.getValue() > 0);

        assertEquals(0, extra.getCentralDirectoryData().length);
    }

    @Test
    public void testLocalFileDataDataAndParse() throws Throwable {
        X7875_NewUnix extra = new X7875_NewUnix();
        extra.setUID(1000L);
        extra.setGID(1000L);

        byte[] data = extra.getLocalFileDataData();
        assertNotNull(data);
        assertTrue(data.length > 0);

        X7875_NewUnix parsed = new X7875_NewUnix();
        parsed.parseFromLocalFileData(data, 0, data.length);
        assertEquals(extra.getUID(), parsed.getUID());
        assertEquals(extra.getGID(), parsed.getGID());
    }

    @Test
    public void testParseFromCentralDirectoryData() throws Throwable {
        X7875_NewUnix extra = new X7875_NewUnix();
        byte[] dummy = new byte[10];
        extra.parseFromCentralDirectoryData(dummy, 0, dummy.length);
        assertEquals(1000L, extra.getUID());
    }

    @Test
    public void testClone() throws Throwable {
        X7875_NewUnix extra = new X7875_NewUnix();
        extra.setUID(500L);
        extra.setGID(600L);

        Object clonedObj = extra.clone();
        assertTrue(clonedObj instanceof X7875_NewUnix);
        X7875_NewUnix cloned = (X7875_NewUnix) clonedObj;
        assertEquals(extra.getUID(), cloned.getUID());
        assertEquals(extra.getGID(), cloned.getGID());
        assertEquals(extra, cloned);
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        X7875_NewUnix e1 = new X7875_NewUnix();
        e1.setUID(100L);
        e1.setGID(200L);

        X7875_NewUnix e2 = new X7875_NewUnix();
        e2.setUID(100L);
        e2.setGID(200L);

        X7875_NewUnix e3 = new X7875_NewUnix();
        e3.setUID(300L);
        e3.setGID(200L);

        assertTrue(e1.equals(e1));
        assertTrue(e1.equals(e2));
        assertFalse(e1.equals(e3));
        assertFalse(e1.equals(new Object()));
        assertFalse(e1.equals(null));

        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    public void testToString() throws Throwable {
        X7875_NewUnix extra = new X7875_NewUnix();
        extra.setUID(1000L);
        extra.setGID(1000L);
        String str = extra.toString();
        assertNotNull(str);
        assertTrue(str.contains("0x7875"));
        assertTrue(str.contains("UID=1000"));
        assertTrue(str.contains("GID=1000"));
    }

    @Test
    public void testTrimLeadingZeroesForceMinLength() throws Throwable {
        assertNull(X7875_NewUnix.trimLeadingZeroesForceMinLength(null));

        byte[] input = new byte[] {0, 0, 5, 10};
        byte[] trimmed = X7875_NewUnix.trimLeadingZeroesForceMinLength(input);
        assertNotNull(trimmed);
        assertEquals(2, trimmed.length);
        assertEquals(5, trimmed[0]);
        assertEquals(10, trimmed[1]);

        byte[] allZeros = new byte[] {0, 0, 0};
        byte[] trimmedZeros = X7875_NewUnix.trimLeadingZeroesForceMinLength(allZeros);
        assertNotNull(trimmedZeros);
        assertEquals(1, trimmedZeros.length);
        assertEquals(0, trimmedZeros[0]);
    }
}