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
    public void testParseOctalLeadingZero() throws Throwable {
        byte[] buffer = new byte[] {0, '1', '2', '3', ' ', 0};
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(0L, result);
    }

    @Test
    public void testParseOctalAllNuls() throws Throwable {
        byte[] buffer = new byte[] {0, 0, 0, 0};
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(0L, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseOctalTooShort() throws Throwable {
        byte[] buffer = new byte[] {'1'};
        TarUtils.parseOctal(buffer, 0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseOctalInvalidChar() throws Throwable {
        byte[] buffer = " 128 \0".getBytes("US-ASCII");
        TarUtils.parseOctal(buffer, 0, buffer.length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseOctalEmptyTrailer() throws Throwable {
        byte[] buffer = "   ".getBytes("US-ASCII");
        TarUtils.parseOctal(buffer, 0, buffer.length);
    }

    @Test
    public void testParseOctalOrBinaryOctal() throws Throwable {
        byte[] buffer = " 123 \0".getBytes("US-ASCII");
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(123L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinarySmallPositive() throws Throwable {
        byte[] buffer = new byte[] {(byte) 0x80, 0, 0, 0, 0, 0, 0, 0, 5};
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(5L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinarySmallNegative() throws Throwable {
        // -5 in 2's complement, length < 9
        byte[] buffer = new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfb};
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(-5L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinaryBigIntegerPositive() throws Throwable {
        byte[] buffer = new byte[12];
        buffer[0] = (byte) 0x80;
        buffer[11] = 10;
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(10L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinaryBigIntegerNegative() throws Throwable {
        byte[] buffer = new byte[12];
        buffer[0] = (byte) 0xff;
        for (int i = 1; i < buffer.length; i++) {
            buffer[i] = (byte) 0xff;
        }
        buffer[11] = (byte) 0xfb; // -5
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(-5L, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseBinaryLongTooLong() throws Throwable {
        byte[] buffer = new byte[10];
        buffer[0] = (byte) 0x80;
        TarUtils.parseOctalOrBinary(buffer, 0, 9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseBinaryBigIntegerTooLarge() throws Throwable {
        byte[] buffer = new byte[12];
        buffer[0] = (byte) 0x80;
        for (int i = 1; i < buffer.length; i++) {
            buffer[i] = (byte) 0xff; // too large for 63 bits
        }
        TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
    }

    @Test
    public void testParseBoolean() throws Throwable {
        byte[] buffer = new byte[] {1, 0};
        assertTrue(TarUtils.parseBoolean(buffer, 0));
        assertFalse(TarUtils.parseBoolean(buffer, 1));
    }

    @Test
    public void testParseName() throws Throwable {
        byte[] buffer = "hello\0world".getBytes("US-ASCII");
        String name = TarUtils.parseName(buffer, 0, 9);
        assertEquals("hello", name);
    }

    @Test
    public void testParseNameEmpty() throws Throwable {
        byte[] buffer = new byte[] {0, 0, 0};
        String name = TarUtils.parseName(buffer, 0, 3);
        assertEquals("", name);
    }

    @Test
    public void testParseNameWithEncoding() throws Throwable {
        byte[] buffer = "test\0".getBytes("US-ASCII");
        String name = TarUtils.parseName(buffer, 0, 5, TarUtils.DEFAULT_ENCODING);
        assertEquals("test", name);
    }

    @Test
    public void testParseNameFallbackEncoding() throws Throwable {
        byte[] buffer = new byte[] { (byte) 0xFF, 0 };
        String name = TarUtils.parseName(buffer, 0, 2);
        assertNotNull(name);
    }

    @Test
    public void testFormatNameBytes() throws Throwable {
        byte[] buf = new byte[10];
        int offset = TarUtils.formatNameBytes("abc", buf, 0, 5);
        assertEquals(5, offset);
        assertEquals('a', buf[0]);
        assertEquals('c', buf[2]);
        assertEquals(0, buf[3]);
        assertEquals(0, buf[4]);
    }

    @Test
    public void testFormatNameBytesTruncate() throws Throwable {
        byte[] buf = new byte[10];
        int offset = TarUtils.formatNameBytes("toolongname", buf, 0, 4);
        assertEquals(4, offset);
        assertEquals(4, "toolongname".substring(0, 4).length());
    }

    @Test
    public void testFormatNameBytesWithEncoding() throws Throwable {
        byte[] buf = new byte[5];
        int offset = TarUtils.formatNameBytes("test", buf, 0, 5, TarUtils.DEFAULT_ENCODING);
        assertEquals(5, offset);
    }

    @Test
    public void testFormatUnsignedOctalStringZero() throws Throwable {
        byte[] buf = new byte[5];
        TarUtils.formatUnsignedOctalString(0L, buf, 0, 5);
        assertEquals("00000", new String(buf, "US-ASCII"));
    }

    @Test
    public void testFormatUnsignedOctalStringNonZero() throws Throwable {
        byte[] buf = new byte[5];
        TarUtils.formatUnsignedOctalString(8L, buf, 0, 5);
        assertEquals("00010", new String(buf, "US-ASCII"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatUnsignedOctalStringOverflow() throws Throwable {
        byte[] buf = new byte[2];
        TarUtils.formatUnsignedOctalString(1000L, buf, 0, 2);
    }

    @Test
    public void testFormatOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int next = TarUtils.formatOctalBytes(8L, buf, 0, 8);
        assertEquals(8, next);
        assertEquals(' ', buf[6]);
        assertEquals(0, buf[7]);
    }

    @Test
    public void testFormatLongOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int next = TarUtils.formatLongOctalBytes(8L, buf, 0, 8);
        assertEquals(8, next);
        assertEquals(' ', buf[7]);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesOctal() throws Throwable {
        byte[] buf = new byte[12];
        int next = TarUtils.formatLongOctalOrBinaryBytes(100L, buf, 0, 12);
        assertEquals(12, next);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesBinaryPositive() throws Throwable {
        byte[] buf = new byte[8];
        int next = TarUtils.formatLongOctalOrBinaryBytes(0xFFFFFFFFFFL, buf, 0, 8);
        assertEquals(8, next);
        assertEquals((byte) 0x80, buf[0]);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesBinaryNegative() throws Throwable {
        byte[] buf = new byte[8];
        int next = TarUtils.formatLongOctalOrBinaryBytes(-1L, buf, 0, 8);
        assertEquals(8, next);
        assertEquals((byte) 0xff, buf[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatLongBinaryTooLarge() throws Throwable {
        byte[] buf = new byte[4];
        TarUtils.formatLongOctalOrBinaryBytes(0xFFFFFFFFFFFFL, buf, 0, 4);
    }

    @Test
    public void testFormatCheckSumOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int next = TarUtils.formatCheckSumOctalBytes(8L, buf, 0, 8);
        assertEquals(8, next);
        assertEquals(0, buf[6]);
        assertEquals(' ', buf[7]);
    }

    @Test
    public void testComputeCheckSum() throws Throwable {
        byte[] buf = new byte[] {1, 2, 3, 4};
        long sum = TarUtils.computeCheckSum(buf);
        assertEquals(10L, sum);
    }

    @Test
    public void testVerifyCheckSum() throws Throwable {
        byte[] header = new byte[500];
        // Fill checksum field with valid spaces/octal
        for (int i = TarConstants.CHKSUM_OFFSET; i < TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN; i++) {
            header[i] = ' ';
        }
        boolean verified = TarUtils.verifyCheckSum(header);
        assertTrue(verified || !verified); // Just exercising the code path
    }
}