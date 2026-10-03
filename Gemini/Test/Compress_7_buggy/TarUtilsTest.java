package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

public class TarUtilsTest {

    @Test
    public void testParseOctalValid() throws Throwable {
        byte[] buffer = new byte[] { '0', '1', '2', '3', ' ', 0 };
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(83L, result);
    }

    @Test
    public void testParseOctalWithLeadingSpacesAndZeros() throws Throwable {
        byte[] buffer = new byte[] { ' ', ' ', '0', '7', 0 };
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(7L, result);
    }

    @Test
    public void testParseOctalTrailingSpace() throws Throwable {
        byte[] buffer = new byte[] { '1', '2', ' ', '3', 0 };
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(10L, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseOctalInvalidDigit() throws Throwable {
        byte[] buffer = new byte[] { '1', '9', 0 };
        TarUtils.parseOctal(buffer, 0, buffer.length);
    }

    @Test
    public void testParseNameNormal() throws Throwable {
        byte[] buffer = new byte[] { 't', 'e', 's', 't', 0, 'x' };
        String name = TarUtils.parseName(buffer, 0, 5);
        assertEquals("test", name);
    }

    @Test
    public void testParseNameMaxLength() throws Throwable {
        byte[] buffer = new byte[] { 'a', 'b', 'c' };
        String name = TarUtils.parseName(buffer, 0, 3);
        assertEquals("abc", name);
    }

    @Test
    public void testFormatNameBytesShorterThanBuffer() throws Throwable {
        byte[] buf = new byte[5];
        int nextOffset = TarUtils.formatNameBytes("abc", buf, 0, 5);
        assertEquals(5, nextOffset);
        assertEquals('a', buf[0]);
        assertEquals('b', buf[1]);
        assertEquals('c', buf[2]);
        assertEquals(0, buf[3]);
        assertEquals(0, buf[4]);
    }

    @Test
    public void testFormatNameBytesLongerThanBuffer() throws Throwable {
        byte[] buf = new byte[2];
        int nextOffset = TarUtils.formatNameBytes("abcd", buf, 0, 2);
        assertEquals(2, nextOffset);
        assertEquals('a', buf[0]);
        assertEquals('b', buf[1]);
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
        byte[] buf = new byte[4];
        TarUtils.formatUnsignedOctalString(8L, buf, 0, 4);
        assertEquals('0', buf[0]);
        assertEquals('0', buf[1]);
        assertEquals('1', buf[2]);
        assertEquals('0', buf[3]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFormatUnsignedOctalStringOverflow() throws Throwable {
        byte[] buf = new byte[2];
        TarUtils.formatUnsignedOctalString(64L, buf, 0, 2);
    }

    @Test
    public void testFormatOctalBytes() throws Throwable {
        byte[] buf = new byte[6];
        int nextOffset = TarUtils.formatOctalBytes(7L, buf, 0, 6);
        assertEquals(6, nextOffset);
        assertEquals(' ', buf[4]);
        assertEquals(0, buf[5]);
    }

    @Test
    public void testFormatLongOctalBytes() throws Throwable {
        byte[] buf = new byte[5];
        int nextOffset = TarUtils.formatLongOctalBytes(7L, buf, 0, 5);
        assertEquals(5, nextOffset);
        assertEquals(' ', buf[4]);
    }

    @Test
    public void testFormatCheckSumOctalBytes() throws Throwable {
        byte[] buf = new byte[6];
        int nextOffset = TarUtils.formatCheckSumOctalBytes(7L, buf, 0, 6);
        assertEquals(6, nextOffset);
        assertEquals(0, buf[4]);
        assertEquals(' ', buf[5]);
    }

    @Test
    public void testComputeCheckSum() throws Throwable {
        byte[] buf = new byte[] { 1, 2, (byte) 255 };
        long sum = TarUtils.computeCheckSum(buf);
        assertEquals(1 + 2 + 255, sum);
    }
}