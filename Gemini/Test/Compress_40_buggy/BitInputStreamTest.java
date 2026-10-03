package org.apache.commons.compress.utils;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

public class BitInputStreamTest {

    @Test
    public void testConstructorAndClose() throws Throwable {
        byte[] data = new byte[] { (byte) 0xFF };
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        BitInputStream bitInputStream = new BitInputStream(in, ByteOrder.BIG_ENDIAN);
        bitInputStream.close();
        
        // Verify stream is closed by trying to read from it or similar if possible,
        // but primarily ensuring close doesn't throw unexpected exceptions.
    }

    @Test
    public void testClearBitCache() throws Throwable {
        byte[] data = new byte[] { (byte) 0xFF, (byte) 0xAA };
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        BitInputStream bitInputStream = new BitInputStream(in, ByteOrder.BIG_ENDIAN);
        
        // Read some bits to populate cache
        long val = bitInputStream.readBits(4);
        assertEquals(0x0F, val);
        
        // Clear cache
        bitInputStream.clearBitCache();
        
        // Next read should start from the next byte or fresh stream depending on implementation,
        // actually clearBitCache clears the internal cache, effectively resetting the buffered bits.
        long val2 = bitInputStream.readBits(4);
        assertEquals(0x0F, val2);
    }

    @Test
    public void testReadBitsNegativeCount() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[10]);
        BitInputStream bitInputStream = new BitInputStream(in, ByteOrder.BIG_ENDIAN);
        try {
            bitInputStream.readBits(-1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("count must not be negative"));
        }
    }

    @Test
    public void testReadBitsTooLargeCount() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[10]);
        BitInputStream bitInputStream = new BitInputStream(in, ByteOrder.BIG_ENDIAN);
        try {
            bitInputStream.readBits(64);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("greater than"));
        }
    }

    @Test
    public void testReadBitsZeroCount() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[] { (byte) 0xFF });
        BitInputStream bitInputStream = new BitInputStream(in, ByteOrder.BIG_ENDIAN);
        long val = bitInputStream.readBits(0);
        assertEquals(0L, val);
    }

    @Test
    public void testReadBitsBigEndian() throws Throwable {
        // Binary: 10101100 11001010
        byte[] data = new byte[] { (byte) 0xAC, (byte) 0xCA };
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        BitInputStream bitInputStream = new BitInputStream(in, ByteOrder.BIG_ENDIAN);

        // Read 4 bits: top 4 bits of 0xAC is 1010 (10)
        assertEquals(10L, bitInputStream.readBits(4));

        // Read 4 bits: bottom 4 bits of 0xAC is 1100 (12)
        assertEquals(12L, bitInputStream.readBits(4));

        // Read 8 bits: 0xCA (202)
        assertEquals(202L, bitInputStream.readBits(8));
    }

    @Test
    public void testReadBitsLittleEndian() throws Throwable {
        // Binary: 10101100 11001010
        byte[] data = new byte[] { (byte) 0xAC, (byte) 0xCA };
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        BitInputStream bitInputStream = new BitInputStream(in, ByteOrder.LITTLE_ENDIAN);

        // Read 4 bits: bottom 4 bits of 0xAC is 1100 (12)
        assertEquals(12L, bitInputStream.readBits(4));

        // Read 4 bits: top 4 bits of 0xAC is 1010 (10)
        assertEquals(10L, bitInputStream.readBits(4));

        // Read 8 bits: 0xCA (202)
        assertEquals(202L, bitInputStream.readBits(8));
    }

    @Test
    public void testReadBitsEOF() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[] { (byte) 0x01 });
        BitInputStream bitInputStream = new BitInputStream(in, ByteOrder.BIG_ENDIAN);

        // Read 8 bits successfully
        assertEquals(1L, bitInputStream.readBits(8));

        // Read more than available -> EOF (-1)
        assertEquals(-1L, bitInputStream.readBits(1));
    }

    @Test
    public void testReadMaximumCacheSizeBits() throws Throwable {
        // MAXIMUM_CACHE_SIZE is 63
        byte[] data = new byte[10];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) 0xFF;
        }
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        BitInputStream bitInputStream = new BitInputStream(in, ByteOrder.BIG_ENDIAN);

        long val = bitInputStream.readBits(63);
        assertTrue(val > 0);
    }
}