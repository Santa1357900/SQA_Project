package org.apache.commons.compress.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

import org.junit.Test;
import static org.junit.Assert.*;

public class BitInputStreamClaudeTest {

    private static class TrackingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private boolean closedFlag = false;

        TrackingInputStream(byte[] data) {
            delegate = new ByteArrayInputStream(data);
        }

        public int read() throws IOException {
            return delegate.read();
        }

        public void close() throws IOException {
            closedFlag = true;
            delegate.close();
        }

        public boolean isClosedFlag() {
            return closedFlag;
        }
    }

    // close() delegates to underlying InputStream.close()
    @Test
    public void testClose_closesUnderlyingStream() throws Throwable {
        TrackingInputStream tracker = new TrackingInputStream(new byte[] { 1, 2, 3 });
        BitInputStream bis = new BitInputStream(tracker, ByteOrder.BIG_ENDIAN);
        bis.close();
        assertTrue(tracker.isClosedFlag());
    }

    // clearBitCache() discards cached leftover bits so next read starts fresh from the stream
    @Test
    public void testClearBitCache_resetsCacheAndDiscardsRemainingBitsOfByte() throws Throwable {
        byte[] data = new byte[] { (byte) 240, (byte) 170 }; // 0xF0, 0xAA
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            long first = bis.readBits(4);
            assertEquals(15L, first);
            bis.clearBitCache();
            long second = bis.readBits(8);
            assertEquals(170L, second);
        } finally {
            bis.close();
        }
    }

    // readBits: count < 0 throws IllegalArgumentException
    @Test
    public void testReadBits_negativeCount_throwsIllegalArgumentException() throws Throwable {
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(new byte[] { 0 }), ByteOrder.BIG_ENDIAN);
        try {
            try {
                bis.readBits(-1);
                fail("expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // ok
            }
        } finally {
            bis.close();
        }
    }

    // readBits: count > 63 (MAXIMUM_CACHE_SIZE) throws IllegalArgumentException
    @Test
    public void testReadBits_countAboveMaximum_throwsIllegalArgumentException() throws Throwable {
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(new byte[] { 0 }), ByteOrder.BIG_ENDIAN);
        try {
            try {
                bis.readBits(64);
                fail("expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // ok
            }
        } finally {
            bis.close();
        }
    }

    // readBits: extreme overflow value Integer.MAX_VALUE throws IllegalArgumentException
    @Test
    public void testReadBits_countIntegerMaxValue_throwsIllegalArgumentException() throws Throwable {
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(new byte[] { 0 }), ByteOrder.BIG_ENDIAN);
        try {
            try {
                bis.readBits(Integer.MAX_VALUE);
                fail("expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // ok
            }
        } finally {
            bis.close();
        }
    }

    // readBits(0): loop condition false immediately (0 iterations), returns 0 without consuming a byte
    @Test
    public void testReadBits_countZero_returnsZeroWithoutConsumingByte() throws Throwable {
        byte[] data = new byte[] { 55 };
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            long zero = bis.readBits(0);
            assertEquals(0L, zero);
            long next = bis.readBits(8);
            assertEquals(55L, next);
        } finally {
            bis.close();
        }
    }

    // readBits on an empty stream: in.read() returns -1 immediately -> returns -1
    @Test
    public void testReadBits_emptyStream_returnsMinusOne() throws Throwable {
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(new byte[0]), ByteOrder.BIG_ENDIAN);
        try {
            assertEquals(-1L, bis.readBits(8));
        } finally {
            bis.close();
        }
    }

    // BIG_ENDIAN: first read within one byte (loop 1 iter), second read uses cached remainder (loop 0 iter)
    @Test
    public void testReadBits_bigEndianTopBitsThenRemainder_returnsExpectedValues() throws Throwable {
        byte[] data = new byte[] { (byte) 178 }; // 10110010
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            assertEquals(5L, bis.readBits(3));  // 101
            assertEquals(18L, bis.readBits(5)); // 10010
        } finally {
            bis.close();
        }
    }

    // BIG_ENDIAN: loop runs multiple iterations reading several full bytes at once
    @Test
    public void testReadBits_bigEndianMultiByteFullRead_returnsCombinedValue() throws Throwable {
        byte[] data = new byte[] { 0x12, 0x34, 0x56 };
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            assertEquals(0x123456L, bis.readBits(24));
        } finally {
            bis.close();
        }
    }

    // BIG_ENDIAN: value split across a byte boundary (non byte-aligned request)
    @Test
    public void testReadBits_bigEndianAcrossByteBoundary_returnsCorrectBits() throws Throwable {
        byte[] data = new byte[] { (byte) 174, (byte) 128 }; // 10101110, 10000000
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            assertEquals(21L, bis.readBits(5));
            assertEquals(26L, bis.readBits(5));
        } finally {
            bis.close();
        }
    }

    // BIG_ENDIAN: reading bit by bit matches MSB-first ordering of the byte
    @Test
    public void testReadBits_bigEndianBitByBit_matchesMsbOrder() throws Throwable {
        byte[] data = new byte[] { (byte) 178 }; // 1 0 1 1 0 0 1 0
        long[] expected = new long[] { 1, 0, 1, 1, 0, 0, 1, 0 };
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            for (int i = 0; i < 8; i++) {
                assertEquals(expected[i], bis.readBits(1));
            }
        } finally {
            bis.close();
        }
    }

    // LITTLE_ENDIAN: first read takes low bits of byte, remainder read uses cached high bits
    @Test
    public void testReadBits_littleEndianLowBitsThenRemainder_returnsExpectedValues() throws Throwable {
        byte[] data = new byte[] { (byte) 178 }; // 10110010
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.LITTLE_ENDIAN);
        try {
            assertEquals(2L, bis.readBits(3));  // low 3 bits: 010
            assertEquals(22L, bis.readBits(5)); // remaining: 10110
        } finally {
            bis.close();
        }
    }

    // LITTLE_ENDIAN: loop runs multiple iterations, first byte is least-significant
    @Test
    public void testReadBits_littleEndianMultiByteFullRead_returnsCombinedValue() throws Throwable {
        byte[] data = new byte[] { 0x56, 0x34, 0x12 };
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.LITTLE_ENDIAN);
        try {
            assertEquals(0x123456L, bis.readBits(24));
        } finally {
            bis.close();
        }
    }

    // LITTLE_ENDIAN: value split across a byte boundary (non byte-aligned request)
    @Test
    public void testReadBits_littleEndianAcrossByteBoundary_returnsCorrectBits() throws Throwable {
        byte[] data = new byte[] { 85, 3 }; // 01010101, 00000011
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.LITTLE_ENDIAN);
        try {
            assertEquals(21L, bis.readBits(5));
            assertEquals(26L, bis.readBits(5));
        } finally {
            bis.close();
        }
    }

    // LITTLE_ENDIAN: reading bit by bit matches LSB-first ordering of the byte
    @Test
    public void testReadBits_littleEndianBitByBit_matchesLsbOrder() throws Throwable {
        byte[] data = new byte[] { (byte) 178 }; // bits lsb->msb: 0 1 0 0 1 1 0 1
        long[] expected = new long[] { 0, 1, 0, 0, 1, 1, 0, 1 };
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.LITTLE_ENDIAN);
        try {
            for (int i = 0; i < 8; i++) {
                assertEquals(expected[i], bis.readBits(1));
            }
        } finally {
            bis.close();
        }
    }

    // BIG_ENDIAN: maximum allowed count (63) read from an aligned fresh cache, all-one bits
    @Test
    public void testReadBits_maxCount63BigEndianAligned_returnsAllOnes() throws Throwable {
        byte[] data = new byte[8];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) 0xFF;
        }
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            long expected = (1L << 63) - 1;
            assertEquals(expected, bis.readBits(63));
        } finally {
            bis.close();
        }
    }

    // LITTLE_ENDIAN: maximum allowed count (63) read from an aligned fresh cache, all-one bits
    @Test
    public void testReadBits_maxCount63LittleEndianAligned_returnsAllOnes() throws Throwable {
        byte[] data = new byte[8];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) 0xFF;
        }
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.LITTLE_ENDIAN);
        try {
            long expected = (1L << 63) - 1;
            assertEquals(expected, bis.readBits(63));
        } finally {
            bis.close();
        }
    }

    // Bug oracle: BIG_ENDIAN large count requested after a non-byte-aligned partial cache must
    // still correctly return the next bits (contract: readBits returns exactly the requested
    // bits read in stream order); a flawed cache-shift implementation loses the oldest bits here.
    @Test
    public void testReadBits_bigEndianLargeCountAfterPartialByte_returnsCorrectBits() throws Throwable {
        byte[] data = new byte[] { 7, 0, 0, 0, 0, 0, 0, 0, 0 }; // 00000111 then 8 zero bytes
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            long first = bis.readBits(5);
            assertEquals(0L, first);
            long second = bis.readBits(60);
            long expected = 7L << 57; // top 3 bits (111) followed by 57 zero bits
            assertEquals(expected, second);
        } finally {
            bis.close();
        }
    }

    // readBits consumes exactly the available bytes with no leftover, no EOF triggered
    @Test
    public void testReadBits_streamExactLengthNoEOF_returnsCorrectValue() throws Throwable {
        byte[] data = new byte[] { 0x12, 0x34 };
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            assertEquals(0x1234L, bis.readBits(16));
        } finally {
            bis.close();
        }
    }

    // readBits returns -1 at EOF but preserves previously cached bits for a later smaller read
    @Test
    public void testReadBits_insufficientStreamAfterPartialCache_returnsMinusOneAndPreservesCache() throws Throwable {
        byte[] data = new byte[] { (byte) 255 }; // 11111111
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            assertEquals(31L, bis.readBits(5));  // top 5 bits: 11111
            assertEquals(-1L, bis.readBits(8));  // needs more bytes than available
            assertEquals(7L, bis.readBits(3));   // cached remaining 3 bits still usable: 111
        } finally {
            bis.close();
        }
    }

    // Two sequential 4-bit reads must reconstruct the original byte value (BIG_ENDIAN)
    @Test
    public void testReadBits_sequentialFourBitReads_reconstructOriginalByte() throws Throwable {
        byte[] data = new byte[] { (byte) 178 };
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            long high = bis.readBits(4);
            long low = bis.readBits(4);
            assertEquals(178L, high * 16 + low);
        } finally {
            bis.close();
        }
    }

    // Repeated 1-bit reads across two bytes reconstruct the original 16-bit BIG_ENDIAN value
    @Test
    public void testReadBits_repeatedSingleBitReads_reconstructTwoBytesBigEndian() throws Throwable {
        byte[] data = new byte[] { 0, (byte) 255 };
        BitInputStream bis = new BitInputStream(new ByteArrayInputStream(data), ByteOrder.BIG_ENDIAN);
        try {
            long reconstructed = 0;
            for (int i = 0; i < 16; i++) {
                reconstructed = (reconstructed << 1) | bis.readBits(1);
            }
            assertEquals(255L, reconstructed);
        } finally {
            bis.close();
        }
    }
}
