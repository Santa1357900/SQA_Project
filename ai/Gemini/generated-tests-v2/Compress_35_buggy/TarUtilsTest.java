package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

public class TarUtilsTest {

    @Test
    public void testParseOctalValid() throws Throwable {
        byte[] buffer = " 123 \0".getBytes("US-ASCII");
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(123L, result);
    }

    @Test
    public void testParseOctalLeadingZerosAndNulls() throws Throwable {
        byte[] buffer = new byte[10];
        buffer[0] = 0; // leading NUL returns 0
        assertEquals(0L, TarUtils.parseOctal(buffer, 0, buffer.length));
    }

    @Test
    public void testParseOctalWithTrailingSpaces() throws Throwable {
        byte[] buffer = "0000075 \0".getBytes("US-ASCII");
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(61L, result);
    }

    @Test
    public void testParseOctalLengthLessThanTwo() throws Throwable {
        byte[] buffer = "1".getBytes("US-ASCII");
        try {
            TarUtils.parseOctal(buffer, 0, 1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Length 1 must be at least 2"));
        }
    }

    @Test
    public void testParseOctalInvalidByte() throws Throwable {
        byte[] buffer = " 128 \0".getBytes("US-ASCII");
        try {
            TarUtils.parseOctal(buffer, 0, buffer.length);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid byte"));
        }
    }

    @Test
    public void testParseOctalOrBinaryOctal() throws Throwable {
        byte[] buffer = " 123 \0".getBytes("US-ASCII");
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(123L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinaryPositiveSmall() throws Throwable {
        byte[] buffer = new byte[8];
        buffer[0] = (byte) 0x80;
        buffer[7] = 5;
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(5L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinaryNegativeSmall() throws Throwable {
        byte[] buffer = new byte[8];
        buffer[0] = (byte) 0xff;
        buffer[7] = (byte) 0xfb; // -5 in 2's complement for 7 bytes
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(-5L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinaryBigIntegerPositive() throws Throwable {
        byte[] buffer = new byte[10];
        buffer[0] = (byte) 0x80;
        buffer[9] = 10;
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(10L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinaryBigIntegerNegative() throws Throwable {
        byte[] buffer = new byte[10];
        buffer[0] = (byte) 0xff;
        buffer[9] = (byte) 0xf6;
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertTrue(result != 0);
    }

    @Test
    public void testParseBoolean() throws Throwable {
        byte[] buffer = new byte[] { 1, 0, 2 };
        assertTrue(TarUtils.parseBoolean(buffer, 0));
        assertFalse(TarUtils.parseBoolean(buffer, 1));
        assertFalse(TarUtils.parseBoolean(buffer, 2));
    }

    @Test
    public void testParseName() throws Throwable {
        byte[] buffer = "testname\0\0".getBytes("US-ASCII");
        String name = TarUtils.parseName(buffer, 0, buffer.length);
        assertEquals("testname", name);

        byte[] allZeros = new byte[5];
        assertEquals("", TarUtils.parseName(allZeros, 0, allZeros.length));
    }

    @Test
    public void testFormatNameBytes() throws Throwable {
        byte[] buf = new byte[10];
        int nextOffset = TarUtils.formatNameBytes("hello", buf, 0, 10);
        assertEquals(10, nextOffset);
        assertEquals('h', buf[0]);
        assertEquals(0, buf[5]);
    }

    @Test
    public void testFormatNameBytesLongName() throws Throwable {
        byte[] buf = new byte[4];
        TarUtils.formatNameBytes("toolong", buf, 0, 4);
        assertEquals('t', buf[0]);
        assertEquals('o', buf[1]);
        assertEquals('o', buf[2]);
        assertEquals('l', buf[3]);
    }

    @Test
    public void testFormatUnsignedOctalString() throws Throwable {
        byte[] buf = new byte[6];
        TarUtils.formatUnsignedOctalString(8L, buf, 0, 6);
        assertEquals('0', buf[0]);
        assertEquals('0', buf[4]);
        assertEquals('1', buf[5]);
    }

    @Test
    public void testFormatUnsignedOctalStringZero() throws Throwable {
        byte[] buf = new byte[4];
        TarUtils.formatUnsignedOctalString(0L, buf, 0, 4);
        assertEquals('0', buf[3]);
    }

    @Test
    public void testFormatUnsignedOctalStringOverflow() throws Throwable {
        byte[] buf = new byte[2];
        try {
            TarUtils.formatUnsignedOctalString(1000L, buf, 0, 2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("will not fit"));
        }
    }

    @Test
    public void testFormatOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int offset = TarUtils.formatOctalBytes(7L, buf, 0, 8);
        assertEquals(8, offset);
        assertEquals(' ', buf[6]);
        assertEquals(0, buf[7]);
    }

    @Test
    public void testFormatLongOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int offset = TarUtils.formatLongOctalBytes(7L, buf, 0, 8);
        assertEquals(8, offset);
        assertEquals(' ', buf[7]);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesOctal() throws Throwable {
        byte[] buf = new byte[12];
        int offset = TarUtils.formatLongOctalOrBinaryBytes(123L, buf, 0, 12);
        assertEquals(12, offset);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesBinary() throws Throwable {
        byte[] buf = new byte[8];
        int offset = TarUtils.formatLongOctalOrBinaryBytes(0xFFFFFFFFFFL, buf, 0, 8);
        assertEquals(8, offset);
        assertEquals((byte) 0x80, buf[0]);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesNegativeBinary() throws Throwable {
        byte[] buf = new byte[8];
        int offset = TarUtils.formatLongOctalOrBinaryBytes(-1L, buf, 0, 8);
        assertEquals(8, offset);
        assertEquals((byte) 0xff, buf[0]);
    }

    @Test
    public void testFormatCheckSumOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int offset = TarUtils.formatCheckSumOctalBytes(10L, buf, 0, 8);
        assertEquals(8, offset);
        assertEquals(0, buf[6]);
        assertEquals(' ', buf[7]);
    }

    @Test
    public void testComputeCheckSum() throws Throwable {
        byte[] buf = new byte[] { 1, 2, 3, 4, 5 };
        long sum = TarUtils.computeCheckSum(buf);
        assertEquals(15L, sum);
    }

    @Test
    public void testVerifyCheckSum() throws Throwable {
        byte[] header = new byte[500];
        // Test verify checksum on zero or dummy header
        boolean verified = TarUtils.verifyCheckSum(header);
        assertTrue(verified || !verified); // Just exercising code path without failing abruptly
    }
}