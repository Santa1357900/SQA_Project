package org.apache.commons.compress.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChecksumCalculatingInputStreamClaudeTest {

    private static class ThrowingInputStream extends InputStream {
        public int read() throws IOException {
            throw new IOException("boom");
        }
    }

    // Constructor + getValue(): fresh checksum on empty stream is 0
    @Test
    public void testConstructor_emptyStream_initialValueIsZero() throws Throwable {
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(new byte[0]));
        assertEquals(0L, s.getValue());
    }

    // read(): single byte return value and checksum update
    @Test
    public void testRead_singleByte_returnsValueAndUpdatesChecksum() throws Throwable {
        byte[] data = {5};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        CRC32 expected = new CRC32();
        expected.update(5);
        assertEquals(5, s.read());
        assertEquals(expected.getValue(), s.getValue());
    }

    // read(): sequential calls accumulate checksum
    @Test
    public void testRead_multipleCalls_accumulatesChecksum() throws Throwable {
        byte[] data = {1, 2, 3};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        CRC32 expected = new CRC32();
        expected.update(1); expected.update(2); expected.update(3);
        assertEquals(1, s.read());
        assertEquals(2, s.read());
        assertEquals(3, s.read());
        assertEquals(expected.getValue(), s.getValue());
    }

    // read(): empty stream branch (ret < 0) returns -1, checksum unchanged
    @Test
    public void testRead_emptyStream_returnsMinusOneAndChecksumUnchanged() throws Throwable {
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(new byte[0]));
        assertEquals(-1, s.read());
        assertEquals(0L, s.getValue());
    }

    // read(): byte value 0xFF treated as unsigned int 255
    @Test
    public void testRead_byteValue0xFF_returnedAsUnsignedInt() throws Throwable {
        byte[] data = {(byte) 0xFF};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        CRC32 expected = new CRC32();
        expected.update(255);
        assertEquals(255, s.read());
        assertEquals(expected.getValue(), s.getValue());
    }

    // read(byte[]): delegates to read(b,0,b.length), buffer larger than data
    @Test
    public void testReadByteArray_bufferLargerThanData_returnsActualLength() throws Throwable {
        byte[] data = {1, 2, 3, 4, 5};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        byte[] buf = new byte[10];
        CRC32 expected = new CRC32();
        expected.update(data, 0, data.length);
        int ret = s.read(buf);
        assertEquals(5, ret);
        assertEquals(expected.getValue(), s.getValue());
    }

    // read(byte[]): empty stream returns -1
    @Test
    public void testReadByteArray_emptyStream_returnsMinusOne() throws Throwable {
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(new byte[0]));
        byte[] buf = new byte[4];
        assertEquals(-1, s.read(buf));
        assertEquals(0L, s.getValue());
    }

    // read(byte[]): zero-length array returns 0, no checksum change
    @Test
    public void testReadByteArray_zeroLengthArray_returnsZero() throws Throwable {
        byte[] data = {9, 9};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        byte[] buf = new byte[0];
        assertEquals(0, s.read(buf));
        assertEquals(0L, s.getValue());
    }

    // read(byte[]): single byte buffer reads one byte correctly
    @Test
    public void testReadByteArray_singleByteBuffer_readsOneByte() throws Throwable {
        byte[] data = {42};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        byte[] buf = new byte[1];
        int ret = s.read(buf);
        assertEquals(1, ret);
        assertEquals((byte) 42, buf[0]);
    }

    // read(b,off,len): partial read with offset, only read portion updates checksum
    @Test
    public void testReadByteArrayOffLen_partialReadWithOffset_updatesOnlyReadPortion() throws Throwable {
        byte[] data = {1, 2, 3, 4, 5, 6};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        byte[] buf = new byte[10];
        CRC32 expected = new CRC32();
        expected.update(data, 0, 3);
        int ret = s.read(buf, 2, 3);
        assertEquals(3, ret);
        assertEquals((byte) 1, buf[2]);
        assertEquals((byte) 3, buf[4]);
        assertEquals(expected.getValue(), s.getValue());
    }

    // read(b,off,len): len==0 returns 0, checksum unchanged (update no-op)
    @Test
    public void testReadByteArrayOffLen_lenZero_returnsZeroAndChecksumUnchanged() throws Throwable {
        byte[] data = {1, 2, 3};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        byte[] buf = new byte[5];
        int ret = s.read(buf, 0, 0);
        assertEquals(0, ret);
        assertEquals(0L, s.getValue());
    }

    // read(b,off,len): full length equal to data length reads everything
    @Test
    public void testReadByteArrayOffLen_fullLengthEqualsDataLength_readsAll() throws Throwable {
        byte[] data = {7, 8, 9};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        byte[] buf = new byte[3];
        int ret = s.read(buf, 0, 3);
        assertEquals(3, ret);
        assertArrayEquals(data, buf);
    }

    // read(b,off,len): stream exhausted after full consumption returns -1
    @Test
    public void testReadByteArrayOffLen_streamExhausted_returnsMinusOne() throws Throwable {
        byte[] data = {1, 2};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        byte[] buf = new byte[2];
        s.read(buf, 0, 2);
        int ret = s.read(buf, 0, 2);
        assertEquals(-1, ret);
    }







    // skip(): on empty stream returns 0
    @Test
    public void testSkip_emptyStream_returnsZero() throws Throwable {
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(new byte[0]));
        long skipped = s.skip(5);
        assertEquals(0L, skipped);
    }



    // getValue(): works with a different Checksum implementation (Adler32)
    @Test
    public void testGetValue_withAdler32_matchesIndependentComputation() throws Throwable {
        byte[] data = {1, 2, 3, 4};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new Adler32(), new ByteArrayInputStream(data));
        Adler32 expected = new Adler32();
        expected.update(data, 0, data.length);
        byte[] buf = new byte[4];
        s.read(buf);
        assertEquals(expected.getValue(), s.getValue());
    }

    // getValue(): before any read, value matches a fresh checksum (0 for CRC32)
    @Test
    public void testGetValue_beforeAnyRead_returnsZero() throws Throwable {
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(new byte[]{1, 2, 3}));
        assertEquals(0L, s.getValue());
    }

    // mixed read() / read(byte[]) calls accumulate checksum correctly across methods
    @Test
    public void testMixedReadCalls_accumulateChecksumAcrossMethods() throws Throwable {
        byte[] data = {9, 8, 7, 6};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        CRC32 expected = new CRC32();
        expected.update(9); expected.update(8); expected.update(7); expected.update(6);
        assertEquals(9, s.read());
        byte[] buf = new byte[2];
        s.read(buf);
        assertEquals(6, s.read());
        assertEquals(expected.getValue(), s.getValue());
    }

    // read(): IOException from underlying stream propagates
    @Test
    public void testRead_underlyingStreamThrowsIOException_propagates() throws Throwable {
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ThrowingInputStream());
        try {
            s.read();
            fail("expected IOException");
        } catch (IOException expected) {
        }
    }

    // read(byte[]): IOException from underlying stream propagates
    @Test
    public void testReadByteArray_underlyingStreamThrowsIOException_propagates() throws Throwable {
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ThrowingInputStream());
        try {
            s.read(new byte[4]);
            fail("expected IOException");
        } catch (IOException expected) {
        }
    }

    // read(b,off,len): IOException from underlying stream propagates
    @Test
    public void testReadByteArrayOffLen_underlyingStreamThrowsIOException_propagates() throws Throwable {
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ThrowingInputStream());
        try {
            s.read(new byte[4], 0, 4);
            fail("expected IOException");
        } catch (IOException expected) {
        }
    }

    // skip(): IOException from underlying stream (via internal read()) propagates
    @Test
    public void testSkip_underlyingStreamThrowsIOException_propagates() throws Throwable {
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ThrowingInputStream());
        try {
            s.skip(1);
            fail("expected IOException");
        } catch (IOException expected) {
        }
    }

    // read(byte[]): buffer exactly matching single remaining byte count
    @Test
    public void testReadByteArray_bufferSmallerThanData_returnsBufferLength() throws Throwable {
        byte[] data = {10, 20, 30};
        ChecksumCalculatingInputStream s = new ChecksumCalculatingInputStream(new CRC32(), new ByteArrayInputStream(data));
        byte[] buf = new byte[2];
        int ret = s.read(buf);
        assertEquals(2, ret);
        assertEquals((byte) 10, buf[0]);
        assertEquals((byte) 20, buf[1]);
    }
}
