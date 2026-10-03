package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

public class TarUtilsTest {

    @Test
    public void testParseOctalValid() throws Throwable {
        byte[] buffer = "123 \0".getBytes("US-ASCII");
        long val = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(83L, val);
    }

    @Test
    public void testParseOctalLeadingZero() throws Throwable {
        byte[] buffer = "\0123".getBytes("US-ASCII");
        long val = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(0L, val);
    }

    @Test
    public void testParseOctalSpaces() throws Throwable {
        byte[] buffer = "  123  \0".getBytes("US-ASCII");
        long val = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(83L, val);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseOctalInvalidLength() throws Throwable {
        byte[] buffer = "1".getBytes("US-ASCII");
        TarUtils.parseOctal(buffer, 0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseOctalInvalidTrailer() throws Throwable {
        byte[] buffer = "123X".getBytes("US-ASCII");
        TarUtils.parseOctal(buffer, 0, buffer.length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseOctalInvalidDigit() throws Throwable {
        byte[] buffer = "128 \0".getBytes("US-ASCII");
        TarUtils.parseOctal(buffer, 0, buffer.length);
    }

    @Test
    public void testParseOctalOrBinaryOctal() throws Throwable {
        byte[] buffer = "123 \0".getBytes("US-ASCII");
        long val = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(83L, val);
    }

    @Test
    public void testParseOctalOrBinaryBinaryPositive() throws Throwable {
        byte[] buffer = new byte[8];
        buffer[0] = (byte) 0x80;
        buffer[7] = 0x01;
        long val = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(1L, val);
    }

    @Test
    public void testParseOctalOrBinaryBinaryNegative() throws Throwable {
        byte[] buffer = new byte[8];
        buffer[0] = (byte) 0xff;
        buffer[7] = (byte) 0xff;
        long val = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(-1L, val);
    }

    @Test
    public void testParseOctalOrBinaryBigIntegerPositive() throws Throwable {
        byte[] buffer = new byte[12];
        buffer[0] = (byte) 0x80;
        buffer[11] = 0x01;
        long val = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(1L, val);
    }

    @Test
    public void testParseOctalOrBinaryBigIntegerNegative() throws Throwable {
        byte[] buffer = new byte[12];
        buffer[0] = (byte) 0xff;
        for (int i = 1; i < buffer.length; i++) {
            buffer[i] = (byte) 0xff;
        }
        long val = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(-1L, val);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseBinaryLongExceeds() throws Throwable {
        byte[] buffer = new byte[9];
        buffer[0] = (byte) 0x80;
        TarUtils.parseOctalOrBinary(buffer, 0, 9);
    }

    @Test
    public void testParseBooleanTrue() throws Throwable {
        byte[] buffer = new byte[] { 1 };
        assertTrue(TarUtils.parseBoolean(buffer, 0));
    }

    @Test
    public void testParseBooleanFalse() throws Throwable {
        byte[] buffer = new byte[] { 0 };
        assertFalse(TarUtils.parseBoolean(buffer, 0));
    }

    @Test
    public void testParseName() throws Throwable {
        byte[] buffer = "test\0\0".getBytes("US-ASCII");
        String name = TarUtils.parseName(buffer, 0, buffer.length);
        assertEquals("test", name);
    }

    @Test
    public void testParseNameEmpty() throws Throwable {
        byte[] buffer = new byte[] { 0, 0 };
        String name = TarUtils.parseName(buffer, 0, buffer.length);
        assertEquals("", name);
    }

    @Test
    public void testFormatNameBytes() throws Throwable {
        byte[] buf = new byte[10];
        int nextOffset = TarUtils.formatNameBytes("test", buf, 0, 5);
        assertEquals(5, nextOffset);
        assertEquals('t', buf[0]);
        assertEquals(0, buf[4]);
    }

    @Test
    public void testFormatUnsignedOctalStringZero() throws Throwable {
        byte[] buf = new byte[4];
        TarUtils.formatUnsignedOctalString(0L, buf, 0, 4);
        assertEquals("0000", new String(buf, "US-ASCII"));
    }

    @Test
    public void testFormatUnsignedOctalStringNonZero() throws Throwable {
        byte[] buf = new byte[4];
        TarUtils.formatUnsignedOctalString(8L, buf, 0, 4);
        assertEquals("0010", new String(buf, "US-ASCII"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatUnsignedOctalStringOverflow() throws Throwable {
        byte[] buf = new byte[2];
        TarUtils.formatUnsignedOctalString(64L, buf, 0, 2);
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
        byte[] buf = new byte[8];
        int next = TarUtils.formatLongOctalOrBinaryBytes(8L, buf, 0, 8);
        assertEquals(8, next);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesBinary() throws Throwable {
        byte[] buf = new byte[12];
        int next = TarUtils.formatLongOctalOrBinaryBytes(Long.MAX_VALUE, buf, 0, 12);
        assertEquals(12, next);
        assertEquals((byte) 0x80, buf[0]);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesBinaryNegative() throws Throwable {
        byte[] buf = new byte[12];
        int next = TarUtils.formatLongOctalOrBinaryBytes(-1L, buf, 0, 12);
        assertEquals(12, next);
        assertEquals((byte) 0xff, buf[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatLongBinaryTooLarge() throws Throwable {
        byte[] buf = new byte[4];
        TarUtils.formatLongOctalOrBinaryBytes(Long.MAX_VALUE, buf, 0, 4);
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
        byte[] buf = new byte[] { 1, 2, 3, 4 };
        long sum = TarUtils.computeCheckSum(buf);
        assertEquals(10L, sum);
    }

    @Test
    public void testVerifyCheckSum() throws Throwable {
        byte[] header = new byte[500];
        long sum = TarUtils.computeCheckSum(header);
        TarUtils.formatCheckSumOctalBytes(sum, header, 148, 8);
        assertTrue(TarUtils.verifyCheckSum(header));
    }
}