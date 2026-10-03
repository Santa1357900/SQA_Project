package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

public class TarUtilsTest {

    @Test
    public void testParseOctalValid() throws Throwable {
        byte[] buffer = "0000755\0 ".getBytes("US-ASCII");
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(493L, result);
    }

    @Test
    public void testParseOctalAllNuls() throws Throwable {
        byte[] buffer = new byte[8];
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(0L, result);
    }

    @Test
    public void testParseOctalLeadingSpacesAndZeros() throws Throwable {
        byte[] buffer = "  0755\0 ".getBytes("US-ASCII");
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(493L, result);
    }

    @Test
    public void testParseOctalNullTerminatedEarly() throws Throwable {
        byte[] buffer = "123\0755".getBytes("US-ASCII");
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(83L, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseOctalInvalidByte() throws Throwable {
        byte[] buffer = "0000855\0 ".getBytes("US-ASCII");
        TarUtils.parseOctal(buffer, 0, buffer.length);
    }

    @Test
    public void testParseNameValid() throws Throwable {
        byte[] buffer = "hello\0world".getBytes("US-ASCII");
        String name = TarUtils.parseName(buffer, 0, buffer.length);
        assertEquals("hello", name);
    }

    @Test
    public void testParseNameNoNull() throws Throwable {
        byte[] buffer = "tarfile".getBytes("US-ASCII");
        String name = TarUtils.parseName(buffer, 0, 4);
        assertEquals("tarf", name);
    }

    @Test
    public void testFormatNameBytesShortName() throws Throwable {
        byte[] buf = new byte[10];
        int offset = 0;
        int length = 10;
        int nextOffset = TarUtils.formatNameBytes("test", buf, offset, length);
        assertEquals(offset + length, nextOffset);
        assertEquals('t', buf[0]);
        assertEquals('e', buf[1]);
        assertEquals('s', buf[2]);
        assertEquals('t', buf[3]);
        assertEquals(0, buf[4]);
    }

    @Test
    public void testFormatNameBytesLongName() throws Throwable {
        byte[] buf = new byte[4];
        int offset = 0;
        int length = 4;
        int nextOffset = TarUtils.formatNameBytes("toolong", buf, offset, length);
        assertEquals(offset + length, nextOffset);
        assertEquals('t', buf[0]);
        assertEquals('o', buf[1]);
        assertEquals('o', buf[2]);
        assertEquals('l', buf[3]);
    }

    @Test
    public void testFormatUnsignedOctalStringZero() throws Throwable {
        byte[] buf = new byte[4];
        TarUtils.formatUnsignedOctalString(0L, buf, 0, 4);
        assertEquals('0', buf[3]);
        assertEquals('0', buf[2]);
        assertEquals('0', buf[1]);
        assertEquals('0', buf[0]);
    }

    @Test
    public void testFormatUnsignedOctalStringNonZero() throws Throwable {
        byte[] buf = new byte[6];
        TarUtils.formatUnsignedOctalString(493L, buf, 0, 6);
        String result = new String(buf, "US-ASCII");
        assertEquals("000755", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatUnsignedOctalStringOverflow() throws Throwable {
        byte[] buf = new byte[2];
        TarUtils.formatUnsignedOctalString(512L, buf, 0, 2);
    }

    @Test
    public void testFormatOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int nextOffset = TarUtils.formatOctalBytes(493L, buf, 0, 8);
        assertEquals(8, nextOffset);
        assertEquals(' ', buf[6]);
        assertEquals(0, buf[7]);
    }

    @Test
    public void testFormatLongOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int nextOffset = TarUtils.formatLongOctalBytes(493L, buf, 0, 8);
        assertEquals(8, nextOffset);
        assertEquals(' ', buf[7]);
    }

    @Test
    public void testFormatCheckSumOctalBytes() throws Throwable {
        byte[] buf = new byte[8];
        int nextOffset = TarUtils.formatCheckSumOctalBytes(493L, buf, 0, 8);
        assertEquals(8, nextOffset);
        assertEquals(0, buf[6]);
        assertEquals(' ', buf[7]);
    }

    @Test
    public void testComputeCheckSum() throws Throwable {
        byte[] buf = new byte[] { (byte) 1, (byte) 2, (byte) 3 };
        long sum = TarUtils.computeCheckSum(buf);
        assertEquals(6L, sum);
    }

    @Test
    public void testComputeCheckSumWithNegativeBytes() throws Throwable {
        byte[] buf = new byte[] { (byte) -1 };
        long sum = TarUtils.computeCheckSum(buf);
        assertEquals(255L, sum);
    }
}