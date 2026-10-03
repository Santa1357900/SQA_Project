package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

public class TarUtilsTest {

    @Test
    public void testParseOctalValid() throws Throwable {
        byte[] buffer = new byte[] { '0', '1', '2', '3', ' ', 0 };
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(83, result);
    }

    @Test
    public void testParseOctalAllNull() throws Throwable {
        byte[] buffer = new byte[] { 0, 0, 0, 0, 0, 0 };
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(0L, result);
    }

    @Test
    public void testParseOctalLeadingSpaces() throws Throwable {
        byte[] buffer = new byte[] { ' ', ' ', '7', ' ', 0 };
        long result = TarUtils.parseOctal(buffer, 0, buffer.length);
        assertEquals(7L, result);
    }

    @Test
    public void testParseOctalInvalidLength() throws Throwable {
        byte[] buffer = new byte[] { '1' };
        boolean thrown = false;
        try {
            TarUtils.parseOctal(buffer, 0, 1);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testParseOctalInvalidTrailer() throws Throwable {
        byte[] buffer = new byte[] { '1', '2', '3', 'X' };
        boolean thrown = false;
        try {
            TarUtils.parseOctal(buffer, 0, buffer.length);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testParseOctalInvalidDigit() throws Throwable {
        byte[] buffer = new byte[] { '8', ' ', 0 };
        boolean thrown = false;
        try {
            TarUtils.parseOctal(buffer, 0, buffer.length);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testParseOctalOrBinaryOctal() throws Throwable {
        byte[] buffer = new byte[] { '1', '2', ' ', 0 };
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(10L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinary() throws Throwable {
        byte[] buffer = new byte[] { (byte) 0x80, 0, 0, 0, 0, 0, 0, 5 };
        long result = TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        assertEquals(5L, result);
    }

    @Test
    public void testParseOctalOrBinaryBinaryOverflow() throws Throwable {
        byte[] buffer = new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 
                                     (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        boolean thrown = false;
        try {
            TarUtils.parseOctalOrBinary(buffer, 0, buffer.length);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testParseBoolean() throws Throwable {
        byte[] buffer1 = new byte[] { 1 };
        byte[] buffer0 = new byte[] { 0 };
        assertTrue(TarUtils.parseBoolean(buffer1, 0));
        assertFalse(TarUtils.parseBoolean(buffer0, 0));
    }

    @Test
    public void testParseName() throws Throwable {
        byte[] buffer = new byte[] { 't', 'e', 's', 't', 0, 'x' };
        String name = TarUtils.parseName(buffer, 0, buffer.length);
        assertEquals("test", name);
    }

    @Test
    public void testFormatNameBytes() throws Throwable {
        byte[] buffer = new byte[5];
        int nextOffset = TarUtils.formatNameBytes("abc", buffer, 0, 5);
        assertEquals(5, nextOffset);
        assertEquals('a', buffer[0]);
        assertEquals('b', buffer[1]);
        assertEquals('c', buffer[2]);
        assertEquals(0, buffer[3]);
        assertEquals(0, buffer[4]);
    }

    @Test
    public void testFormatUnsignedOctalStringZero() throws Throwable {
        byte[] buffer = new byte[4];
        TarUtils.formatUnsignedOctalString(0L, buffer, 0, 4);
        assertEquals('0', buffer[3]);
        assertEquals('0', buffer[0]);
    }

    @Test
    public void testFormatUnsignedOctalStringValue() throws Throwable {
        byte[] buffer = new byte[4];
        TarUtils.formatUnsignedOctalString(8L, buffer, 0, 4);
        assertEquals('0', buffer[0]);
        assertEquals('0', buffer[1]);
        assertEquals('1', buffer[2]);
        assertEquals('0', buffer[3]);
    }

    @Test
    public void testFormatUnsignedOctalStringTooLarge() throws Throwable {
        byte[] buffer = new byte[2];
        boolean thrown = false;
        try {
            TarUtils.formatUnsignedOctalString(1000L, buffer, 0, 2);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testFormatOctalBytes() throws Throwable {
        byte[] buffer = new byte[8];
        int nextOffset = TarUtils.formatOctalBytes(7L, buffer, 0, 8);
        assertEquals(8, nextOffset);
        assertEquals(' ', buffer[6]);
        assertEquals(0, buffer[7]);
    }

    @Test
    public void testFormatLongOctalBytes() throws Throwable {
        byte[] buffer = new byte[8];
        int nextOffset = TarUtils.formatLongOctalBytes(7L, buffer, 0, 8);
        assertEquals(8, nextOffset);
        assertEquals(' ', buffer[7]);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesOctal() throws Throwable {
        byte[] buffer = new byte[TarConstants.UIDLEN];
        int nextOffset = TarUtils.formatLongOctalOrBinaryBytes(123L, buffer, 0, buffer.length);
        assertEquals(buffer.length, nextOffset);
    }

    @Test
    public void testFormatLongOctalOrBinaryBytesBinary() throws Throwable {
        byte[] buffer = new byte[8];
        long largeValue = 0x1FFFFFFFFL; 
        int nextOffset = TarUtils.formatLongOctalOrBinaryBytes(largeValue, buffer, 0, buffer.length);
        assertEquals(8, nextOffset);
        assertTrue((buffer[0] & 0x80) != 0);
    }

    @Test
    public void testFormatCheckSumOctalBytes() throws Throwable {
        byte[] buffer = new byte[8];
        int nextOffset = TarUtils.formatCheckSumOctalBytes(10L, buffer, 0, 8);
        assertEquals(8, nextOffset);
        assertEquals(0, buffer[6]);
        assertEquals(' ', buffer[7]);
    }

    @Test
    public void testComputeCheckSum() throws Throwable {
        byte[] buffer = new byte[] { 1, 2, 3, (byte) 255 };
        long sum = TarUtils.computeCheckSum(buffer);
        assertEquals(1 + 2 + 3 + 255, sum);
    }
}