package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.ByteBuffer;

public class TarUtilsTest {

    @Test
    public void testParseOctalValid() throws Throwable {
        byte[] buffer = " 123 \0".getBytes("US-ASCII");
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(123L, result);
    }

    @Test
    public void testParseOctalLeadingZero() throws Throwable {
        byte[] buffer = new byte[8];
        buffer[0] = 0; // leading NUL
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(0L, result);
    }

    @Test
    public void testParseOctalAllNulls() throws Throwable {
        byte[] buffer = new byte[8];
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(0L, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseOctalShortLength() throws Throwable {
        byte[] buffer = "1".getBytes("US-ASCII");
        TarUtils.parseOctal(buffer, 0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseOctalInvalidByte() throws Throwable {
        byte[] buffer = " 128 \0".getBytes("US-ASCII");
        TarUtils.parseOctal(buffer, 0, buffer.length);
    }

    @Test
    public void testParseOctalOrBinaryOctal() throws Throwable {
        byte[] buffer = " 123 \0".getBytes("US-ASCII");
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(123L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinaryPositive() throws Throwable {
        byte[] buffer = new byte[8];
        buffer[0] = (byte) 0x80;
        buffer[7] = 5;
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(5L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinaryNegative() throws Throwable {
        byte[] buffer = new byte[8];
        buffer[0] = (byte) 0xff;
        for (int i = 1; i < buffer.length; i++) {
            buffer[i] = (byte) 0xff;
        }
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(-1L, result);
    }

    @Test
    public void testParseOctalOrBinaryBigInteger() throws Throwable {
        byte[] buffer = new byte[10];
        buffer[0] = (byte) 0x80;
        buffer[9] = 10;
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(10L, result);
    }

    @Test
    public void testParseBoolean() throws Throwable {
        byte[] buffer = new byte[] { 1, 0 };
        assertTrue(TarUtils.parseBoolean(buffer, 0));
        assertFalse(TarUtils.parseBoolean(buffer, 1));
    }

    @Test
    public void testParseNameDefaultEncoding() throws Throwable {
        byte[] buffer = "test\0\0".getBytes("US-ASCII");
        String name = TarUtils.parseName(buffer, 0, buffer.length);
        assertEquals("test", name);
    }

    @Test
    public void testParseNameEmpty() throws Throwable {
        byte[] buffer = new byte[4];
        String name = TarUtils.parseName(buffer, 0, buffer.length);
        assertEquals("", name);
    }

    @Test
    public void testParseNameCustomEncoding() throws Throwable {
        byte[] buffer = "foo\0".getBytes("US-ASCII");
        String name = TarUtils.parseName(buffer, 0, buffer.length, TarUtils.DEFAULT_ENCODING);
        assertEquals("foo", name);
    }

    @Test
    public void testParseNameFallbackEncoding() throws Throwable {
        byte[] buffer = "bar\0".getBytes("US-ASCII");
        String name = TarUtils.parseName(buffer, 0, buffer.length, TarUtils.FALLBACK_ENCODING);
        assertEquals("bar", name);
    }

    @Test
    public void testFormatNameBytesDefault() throws Throwable {
        byte[] buf = new byte[8];
        int offset = TarUtils.formatNameBytes("hello", buf, 0, buf.length);
        assertEquals(8, offset);
        assertEquals('h', buf[0]);
        assertEquals(0, buf[5]);
    }

    @Test
    public void testFormatNameBytesTruncate() throws Throwable {
        byte[] buf = new byte[3];
        int offset = TarUtils.formatNameBytes("hello", buf, 0, buf.length);
        assertEquals(3, offset);
        assertEquals('e', buf[1]);
    }

    @Test
    public void testFormatNameBytesFallback() throws Throwable {
        byte[] buf = new byte[5];
        int offset = TarUtils.formatNameBytes("abc", buf, 0, buf.length, TarUtils.FALLBACK_ENCODING);
        assertEquals(5, offset);
        assertEquals('a', buf[0]);
        assertEquals(0, buf[3]);
    }

    @Test
    public void testFormatUnsignedOctalStringZero() throws Throwable {
        byte[] buf = new byte[4];
        TarUtils.formatUnsignedOctalString(0L, buf, 0, buf.length);
        assertEquals('0', buf[3]);
        assertEquals('0', buf[0]);
    }

    @Test
    public void testFormatUnsignedOctalStringNonZero() throws Throwable {
        byte[] buf = new byte[4];
        TarUtils.formatUnsignedOctalString(8L, buf, 0, buf.length);
        assertEquals('1', buf[2]);
        assertEquals('0', buf[3]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatUnsignedOctalStringOverflow() throws Throwable {
        byte[] buf = new byte[2];
        TarUtils.formatUnsignedOctalString(1000L, buf, 0, buf.length);
    }

    @Test
    public void testFormatOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int next = TarUtils.formatOctalBytes(8L, buf, 0, buf.length);
        assertEquals(8, next);
        assertEquals(' ', buf[6]);
        assertEquals(0, buf[7]);
    }

    @Test
    public void testFormatLongOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int next = TarUtils.formatLongOctalBytes(8L, buf, 0, buf.length);
        assertEquals(8, next);
        assertEquals(' ', buf[7]);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesOctal() throws Throwable {
        byte[] buf = new byte[12];
        int next = TarUtils.formatLongOctalOrBinaryBytes(100L, buf, 0, buf.length);
        assertEquals(12, next);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesBinarySmall() throws Throwable {
        byte[] buf = new byte[8];
        int next = TarUtils.formatLongOctalOrBinaryBytes(100000000L, buf, 0, buf.length);
        assertEquals(8, next);
        assertEquals((byte) 0x80, buf[0]);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesBinaryNegative() throws Throwable {
        byte[] buf = new byte[8];
        int next = TarUtils.formatLongOctalOrBinaryBytes(-1L, buf, 0, buf.length);
        assertEquals(8, next);
        assertEquals((byte) 0xff, buf[0]);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesBinaryBig() throws Throwable {
        byte[] buf = new byte[12];
        int next = TarUtils.formatLongOctalOrBinaryBytes(Long.MAX_VALUE, buf, 0, buf.length);
        assertEquals(12, next);
        assertEquals((byte) 0x80, buf[0]);
    }

    @Test
    public void testFormatCheckSumOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int next = TarUtils.formatCheckSumOctalBytes(123L, buf, 0, buf.length);
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
    public void testVerifyCheckSumValid() throws Throwable {
        byte[] header = new byte[512];
        TarUtils.formatCheckSumOctalBytes(100L, header, 148, 8);
        boolean valid = TarUtils.verifyCheckSum(header);
        assertTrue(valid);
    }

    @Test
    public void testVerifyCheckSumStoredGreaterThanUnsigned() throws Throwable {
        byte[] header = new byte[512];
        TarUtils.formatCheckSumOctalBytes(999999L, header, 148, 8);
        boolean valid = TarUtils.verifyCheckSum(header);
        assertTrue(valid);
    }
}