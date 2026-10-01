package org.apache.commons.compress.compressors.bzip2;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.*;

public class BZip2CompressorInputStreamClaudeTest {

    private static byte[] bytes(int... vals) {
        byte[] b = new byte[vals.length];
        for (int i = 0; i < vals.length; i++) {
            b[i] = (byte) vals[i];
        }
        return b;
    }

    private static final byte[] VALID_EMPTY_STREAM = bytes(
        'B', 'Z', 'h', '9',
        0x17, 0x72, 0x45, 0x38, 0x50, 0x90,
        0, 0, 0, 0);

    // matches(): length < 3 -> false regardless of content
    @Test
    public void testMatches_lengthLessThanThree_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 'B', 'Z', 'h' };
        assertFalse(BZip2CompressorInputStream.matches(sig, 2));
    }

    // matches(): first byte wrong -> false
    @Test
    public void testMatches_wrongFirstByte_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 'X', 'Z', 'h' };
        assertFalse(BZip2CompressorInputStream.matches(sig, 3));
    }

    // matches(): second byte wrong -> false
    @Test
    public void testMatches_wrongSecondByte_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 'B', 'X', 'h' };
        assertFalse(BZip2CompressorInputStream.matches(sig, 3));
    }

    // matches(): third byte wrong -> false
    @Test
    public void testMatches_wrongThirdByte_returnsFalse() throws Throwable {
        byte[] sig = new byte[] { 'B', 'Z', 'X' };
        assertFalse(BZip2CompressorInputStream.matches(sig, 3));
    }

    // matches(): exact "BZh" signature -> true
    @Test
    public void testMatches_validSignature_returnsTrue() throws Throwable {
        byte[] sig = new byte[] { 'B', 'Z', 'h' };
        assertTrue(BZip2CompressorInputStream.matches(sig, 3));
    }

    // constructor: null InputStream -> init() throws IOException("No InputStream")
    @Test
    public void testConstructor_nullInputStream_throwsIOException() throws Throwable {
        try {
            new BZip2CompressorInputStream(null);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("InputStream"));
        }
    }

    // constructor: completely empty stream -> magic0 is -1 but isFirstStream, falls through to format error
    @Test
    public void testConstructor_emptyInputStream_throwsNotBZip2Format() throws Throwable {
        try {
            new BZip2CompressorInputStream(new ByteArrayInputStream(new byte[0]));
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("BZip2 format"));
        }
    }

    // constructor: truncated "BZ" magic -> magic2 == -1 != 'h' -> format error
    @Test
    public void testConstructor_truncatedMagicBZOnly_throwsNotBZip2Format() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes('B', 'Z'));
        try {
            new BZip2CompressorInputStream(bais);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("BZip2 format"));
        }
    }

    // constructor: block size digit below '1' -> invalid block size branch
    @Test
    public void testConstructor_invalidBlockSizeDigit_throwsIOException() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes('B', 'Z', 'h', '0'));
        try {
            new BZip2CompressorInputStream(bais);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("block size"));
        }
    }

    // constructor: header ok but stream ends before 6 block-magic bytes -> unexpected end of stream
    @Test
    public void testConstructor_truncatedAfterHeader_throwsUnexpectedEndOfStream() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes('B', 'Z', 'h', '9'));
        try {
            new BZip2CompressorInputStream(bais);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("end of stream"));
        }
    }

    // constructor: 6 magic bytes match neither end-of-stream nor block-start magic -> bad block header
    @Test
    public void testConstructor_badBlockHeaderMagic_throwsIOException() throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(
            bytes('B', 'Z', 'h', '9', 0, 0, 0, 0, 0, 0));
        try {
            new BZip2CompressorInputStream(bais);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("bad block header"));
        }
    }

    // constructor: end-of-stream magic with mismatching combined CRC -> CRC error
    @Test
    public void testConstructor_crcMismatchOnEmptyBlock_throwsCRCError() throws Throwable {
        byte[] data = bytes('B', 'Z', 'h', '9',
            0x17, 0x72, 0x45, 0x38, 0x50, 0x90,
            0, 0, 0, 1);
        try {
            new BZip2CompressorInputStream(new ByteArrayInputStream(data));
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("CRC error"));
        }
    }

    // constructor: valid empty stream (zero blocks, matching CRC) -> currentState becomes EOF, read() == -1
    @Test
    public void testConstructor_validEmptyStream_readReturnsMinusOne() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM));
        assertEquals(-1, in.read());
    }

    // constructor + read(): decompressConcatenated=true, single empty stream, natural EOF on init(false)
    @Test
    public void testConstructor_decompressConcatenatedTrueSingleStream_readReturnsMinusOne() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM), true);
        assertEquals(-1, in.read());
    }

    // constructor + read(): decompressConcatenated=true, two concatenated empty streams
    @Test
    public void testConstructor_decompressConcatenatedTrueTwoStreams_readReturnsMinusOne() throws Throwable {
        byte[] two = bytes('B', 'Z', 'h', '9',
            0x17, 0x72, 0x45, 0x38, 0x50, 0x90, 0, 0, 0, 0,
            'B', 'Z', 'h', '9',
            0x17, 0x72, 0x45, 0x38, 0x50, 0x90, 0, 0, 0, 0);
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(two), true);
        assertEquals(-1, in.read());
    }

    // constructor: decompressConcatenated=true, garbage bytes after a valid empty stream -> garbage error
    @Test
    public void testConstructor_decompressConcatenatedTrueGarbageAfter_throwsIOException() throws Throwable {
        byte[] data = bytes('B', 'Z', 'h', '9',
            0x17, 0x72, 0x45, 0x38, 0x50, 0x90, 0, 0, 0, 0,
            0xFF, 0xFF, 0xFF);
        try {
            new BZip2CompressorInputStream(new ByteArrayInputStream(data), true);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Garbage"));
        }
    }

    // read(): on EOF state, repeated single-byte reads keep returning -1
    @Test
    public void testRead_afterEOF_returnsMinusOneRepeatedly() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM));
        assertEquals(-1, in.read());
        assertEquals(-1, in.read());
    }

    // read(): after close(), stream == null -> IOException("stream closed")
    @Test
    public void testRead_afterClose_throwsIOExceptionStreamClosed() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM));
        in.close();
        try {
            in.read();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // read(byte[],offs,len): offs < 0 -> IndexOutOfBoundsException
    @Test
    public void testReadByteArray_offsNegative_throwsIndexOutOfBoundsException() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM));
        try {
            in.read(new byte[5], -1, 2);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // read(byte[],offs,len): len < 0 -> IndexOutOfBoundsException
    @Test
    public void testReadByteArray_lenNegative_throwsIndexOutOfBoundsException() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM));
        try {
            in.read(new byte[5], 0, -1);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // read(byte[],offs,len): offs+len > dest.length -> IndexOutOfBoundsException
    @Test
    public void testReadByteArray_offsPlusLenExceedsDestLength_throwsIndexOutOfBoundsException() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM));
        try {
            in.read(new byte[5], 3, 5);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    // read(byte[],offs,len): len == 0 must return 0 per InputStream contract (bug: buggy code returns -1)
    @Test
    public void testReadByteArray_lenZero_returnsZero() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM));
        int result = in.read(new byte[5], 0, 0);
        assertEquals(0, result);
    }

    // read(byte[]) no-offset overload with empty array also hits the len==0 contract via same code path
    @Test
    public void testReadByteArray_lenZeroViaNoOffsetOverload_returnsZero() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM));
        int result = in.read(new byte[0]);
        assertEquals(0, result);
    }

    // read(byte[],offs,len): len > 0 but stream already at EOF -> -1 (destOffs == offs, not the len==0 bug)
    @Test
    public void testReadByteArray_normalCallOnEofStream_returnsMinusOne() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM));
        int result = in.read(new byte[4], 0, 4);
        assertEquals(-1, result);
    }

    // read(byte[],offs,len): after close(), in == null -> IOException("stream closed")
    @Test
    public void testReadByteArray_afterClose_throwsIOExceptionStreamClosed() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM));
        in.close();
        try {
            in.read(new byte[5], 0, 5);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    // close(): calling twice is safe (second call sees in == null and skips closing), but stream stays closed
    @Test
    public void testClose_calledTwice_streamStaysClosed() throws Throwable {
        BZip2CompressorInputStream in = new BZip2CompressorInputStream(
            new ByteArrayInputStream(VALID_EMPTY_STREAM));
        in.close();
        in.close();
        try {
            in.read();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }
}
