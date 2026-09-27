package org.apache.commons.compress.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.junit.Test;

public class ChecksumCalculatingInputStreamTest {

    @Test
    public void testReadSingleByte() throws Throwable {
        byte[] data = new byte[] { 1, 2, 3 };
        InputStream bais = new ByteArrayInputStream(data);
        Checksum checksum = new CRC32();
        ChecksumCalculatingInputStream cis = new ChecksumCalculatingInputStream(checksum, bais);

        assertEquals(1, cis.read());
        assertEquals(2, cis.read());
        assertEquals(3, cis.read());
        assertEquals(-1, cis.read());
        
        assertTrue(cis.getValue() != 0);
    }

    @Test
    public void testReadByteArray() throws Throwable {
        byte[] data = new byte[] { 10, 20, 30, 40 };
        InputStream bais = new ByteArrayInputStream(data);
        Checksum checksum = new CRC32();
        ChecksumCalculatingInputStream cis = new ChecksumCalculatingInputStream(checksum, bais);

        byte[] buf = new byte[2];
        int read1 = cis.read(buf);
        assertEquals(2, read1);
        assertEquals(10, buf[0]);
        assertEquals(20, buf[1]);

        int read2 = cis.read(buf);
        assertEquals(2, read2);
        assertEquals(30, buf[0]);
        assertEquals(40, buf[1]);

        int read3 = cis.read(buf);
        assertEquals(-1, read3);
    }

    @Test
    public void testReadByteArrayWithOffsetAndLength() throws Throwable {
        byte[] data = new byte[] { 5, 6, 7, 8 };
        InputStream bais = new ByteArrayInputStream(data);
        Checksum checksum = new CRC32();
        ChecksumCalculatingInputStream cis = new ChecksumCalculatingInputStream(checksum, bais);

        byte[] buf = new byte[5];
        int read = cis.read(buf, 1, 3);
        assertEquals(3, read);
        assertEquals(0, buf[0]);
        assertEquals(5, buf[1]);
        assertEquals(6, buf[2]);
        assertEquals(7, buf[3]);
        assertEquals(0, buf[4]);
    }

    @Test
    public void testSkip() throws Throwable {
        byte[] data = new byte[] { 99 };
        InputStream bais = new ByteArrayInputStream(data);
        Checksum checksum = new CRC32();
        ChecksumCalculatingInputStream cis = new ChecksumCalculatingInputStream(checksum, bais);

        long skipped1 = cis.skip(10L);
        assertEquals(1, skipped1);

        long skipped2 = cis.skip(10L);
        assertEquals(0, skipped2);
    }

    @Test
    public void testGetValue() throws Throwable {
        byte[] data = new byte[] { 'a', 'b', 'c' };
        InputStream bais = new ByteArrayInputStream(data);
        Checksum checksum = new CRC32();
        ChecksumCalculatingInputStream cis = new ChecksumCalculatingInputStream(checksum, bais);

        long initialValue = cis.getValue();
        assertEquals(0L, initialValue);

        while (cis.read() != -1) {
            // consume stream
        }

        long finalValue = cis.getValue();
        assertTrue(finalValue != 0L);
    }
}