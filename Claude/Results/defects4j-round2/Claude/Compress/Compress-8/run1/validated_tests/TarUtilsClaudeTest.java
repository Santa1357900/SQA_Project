package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

public class TarUtilsClaudeTest {

    // all-NUL buffer must parse as 0 per Javadoc ("allowed to contain all NULs")
    @Test
    public void testParseOctal_allNulBuffer_returnsZero() throws Throwable {
        byte[] buf = new byte[8];
        assertEquals(0L, TarUtils.parseOctal(buf, 0, 8));
    }

    // leading spaces are ignored per Javadoc, trailing space terminates
    @Test
    public void testParseOctal_leadingSpacesIgnored_parsesCorrectly() throws Throwable {
        byte[] buf = new byte[] { (byte) ' ', (byte) ' ', (byte) '7', (byte) '5', (byte) '5', (byte) ' ' };
        long expected = Long.parseLong("755", 8);
        assertEquals(expected, TarUtils.parseOctal(buf, 0, 6));
    }

    // leading zero digits must not change the numeric value
    @Test
    public void testParseOctal_leadingZerosDigit_parsesCorrectly() throws Throwable {
        byte[] buf = new byte[] { (byte) '0', (byte) '0', (byte) '0', (byte) '6', (byte) '4', (byte) '4', (byte) ' ' };
        long expected = Long.parseLong("644", 8);
        assertEquals(expected, TarUtils.parseOctal(buf, 0, 7));
    }

    // trailing NUL terminates parsing
    @Test
    public void testParseOctal_trailingNul_stopsParsing() throws Throwable {
        byte[] buf = new byte[] { (byte) '6', (byte) '4', (byte) '4', 0 };
        long expected = Long.parseLong("644", 8);
        assertEquals(expected, TarUtils.parseOctal(buf, 0, 4));
    }

    // invalid octal digit ('9') must throw IllegalArgumentException
    @Test
    public void testParseOctal_invalidDigit_throwsIllegalArgumentException() throws Throwable {
        byte[] buf = new byte[] { (byte) '7', (byte) '9', (byte) '5', (byte) ' ' };
        try {
            TarUtils.parseOctal(buf, 0, 4);
            fail("expected IllegalArgumentException for invalid digit");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Invalid byte"));
        }
    }

    // Javadoc: "The buffer must contain a trailing space or NUL" -> missing one must throw
    @Test
    public void testParseOctal_missingTrailingSpaceOrNul_throwsIllegalArgumentException() throws Throwable {
        byte[] buf = new byte[] { (byte) '7', (byte) '5', (byte) '5' };
        try {
            TarUtils.parseOctal(buf, 0, 3);
            fail("expected IllegalArgumentException for missing trailing NUL/space");
        } catch (IllegalArgumentException expected) {
            // contract violated: trailing NUL/space is required
        }
    }

    // offset must be respected, parsing only the requested sub-range
    @Test
    public void testParseOctal_offsetNonZero_parsesSubrange() throws Throwable {
        byte[] buf = new byte[] { 0, 0, (byte) '7', (byte) '5', (byte) '5', (byte) ' ' };
        long expected = Long.parseLong("755", 8);
        assertEquals(expected, TarUtils.parseOctal(buf, 2, 4));
    }

    // larger value, oracle derived from Long.parseLong with radix 8
    @Test
    public void testParseOctal_largeValue_matchesLongParseLong() throws Throwable {
        byte[] buf = new byte[] { (byte) '1', (byte) '7', (byte) '7', (byte) '7', (byte) '7',
                (byte) '7', (byte) '7', (byte) '7', (byte) ' ' };
        long expected = Long.parseLong("17777777", 8);
        assertEquals(expected, TarUtils.parseOctal(buf, 0, 9));
    }

    // minimum valid length (2 bytes): a single '0' digit then terminator -> 0
    @Test
    public void testParseOctal_minimumLength_parsesZero() throws Throwable {
        byte[] buf = new byte[] { (byte) '0', (byte) ' ' };
        assertEquals(0L, TarUtils.parseOctal(buf, 0, 2));
    }

    // Javadoc: "may contain an additional trailing space or NUL"
    @Test
    public void testParseOctal_extraTrailingNulAfterSpace_parsesCorrectly() throws Throwable {
        byte[] buf = new byte[] { (byte) '7', (byte) '5', (byte) '5', (byte) ' ', 0 };
        long expected = Long.parseLong("755", 8);
        assertEquals(expected, TarUtils.parseOctal(buf, 0, 5));
    }

    // parseName stops at the first NUL
    @Test
    public void testParseName_stopsAtNul() throws Throwable {
        byte[] buf = new byte[] { (byte) 'a', (byte) 'b', (byte) 'c', 0, (byte) 'd', (byte) 'e' };
        assertEquals("abc", TarUtils.parseName(buf, 0, 6));
    }

    // no NUL present -> entire length is used as the name
    @Test
    public void testParseName_noNul_usesFullLength() throws Throwable {
        byte[] buf = new byte[] { (byte) 'a', (byte) 'b', (byte) 'c' };
        assertEquals("abc", TarUtils.parseName(buf, 0, 3));
    }

    // first byte is NUL -> empty name
    @Test
    public void testParseName_firstByteNul_returnsEmptyString() throws Throwable {
        byte[] buf = new byte[] { 0, (byte) 'a', (byte) 'b' };
        assertEquals("", TarUtils.parseName(buf, 0, 3));
    }

    // offset must be respected for parseName
    @Test
    public void testParseName_withOffset_parsesSubrange() throws Throwable {
        byte[] buf = new byte[] { (byte) 'x', (byte) 'x', (byte) 'a', (byte) 'b', 0 };
        assertEquals("ab", TarUtils.parseName(buf, 2, 3));
    }

    // name shorter than buffer length must be NUL padded
    @Test
    public void testFormatNameBytes_shorterName_padsWithNul() throws Throwable {
        byte[] buf = new byte[5];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (byte) 9;
        }
        int result = TarUtils.formatNameBytes("ab", buf, 0, 5);
        assertEquals(5, result);
        assertEquals((byte) 'a', buf[0]);
        assertEquals((byte) 'b', buf[1]);
        assertEquals((byte) 0, buf[2]);
        assertEquals((byte) 0, buf[3]);
        assertEquals((byte) 0, buf[4]);
    }

    // name longer than buffer length must be truncated
    @Test
    public void testFormatNameBytes_longerName_truncates() throws Throwable {
        byte[] buf = new byte[3];
        int result = TarUtils.formatNameBytes("abcdef", buf, 0, 3);
        assertEquals(3, result);
        assertEquals((byte) 'a', buf[0]);
        assertEquals((byte) 'b', buf[1]);
        assertEquals((byte) 'c', buf[2]);
    }

    // name exactly fills the buffer, loop boundary i < length == i < name.length()
    @Test
    public void testFormatNameBytes_exactLength_noPadding() throws Throwable {
        byte[] buf = new byte[3];
        TarUtils.formatNameBytes("abc", buf, 0, 3);
        assertEquals((byte) 'a', buf[0]);
        assertEquals((byte) 'b', buf[1]);
        assertEquals((byte) 'c', buf[2]);
    }

    // offset must be respected, returned value is offset + length
    @Test
    public void testFormatNameBytes_withOffset_returnsOffsetPlusLength() throws Throwable {
        byte[] buf = new byte[10];
        int result = TarUtils.formatNameBytes("hi", buf, 3, 4);
        assertEquals(7, result);
        assertEquals((byte) 'h', buf[3]);
        assertEquals((byte) 'i', buf[4]);
        assertEquals((byte) 0, buf[5]);
        assertEquals((byte) 0, buf[6]);
    }

    // value 0 must be written as all '0' digits, no exception
    @Test
    public void testFormatUnsignedOctalString_zeroValue_fillsWithZeros() throws Throwable {
        byte[] buf = new byte[6];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (byte) 9;
        }
        TarUtils.formatUnsignedOctalString(0L, buf, 0, 6);
        for (int i = 0; i < buf.length; i++) {
            assertEquals((byte) '0', buf[i]);
        }
    }

    // normal value produces correct octal digits with leading-zero padding
    @Test
    public void testFormatUnsignedOctalString_normalValue_correctDigits() throws Throwable {
        byte[] buf = new byte[6];
        TarUtils.formatUnsignedOctalString(8L, buf, 0, 6); // octal "10"
        assertEquals((byte) '0', buf[0]);
        assertEquals((byte) '0', buf[1]);
        assertEquals((byte) '0', buf[2]);
        assertEquals((byte) '0', buf[3]);
        assertEquals((byte) '1', buf[4]);
        assertEquals((byte) '0', buf[5]);
    }

    // value that exactly fills the buffer with no room for extra padding
    @Test
    public void testFormatUnsignedOctalString_exactFit_noPadding() throws Throwable {
        byte[] buf = new byte[1];
        TarUtils.formatUnsignedOctalString(7L, buf, 0, 1);
        assertEquals((byte) '7', buf[0]);
    }

    // value too large for the buffer must throw IllegalArgumentException
    @Test
    public void testFormatUnsignedOctalString_valueTooLarge_throwsIllegalArgumentException() throws Throwable {
        byte[] buf = new byte[2];
        try {
            TarUtils.formatUnsignedOctalString(64L, buf, 0, 2);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("will not fit"));
        }
    }

    // formatOctalBytes appends trailing space then NUL, verified via round trip
    @Test
    public void testFormatOctalBytes_normalValue_hasTrailingSpaceAndNul() throws Throwable {
        byte[] buf = new byte[8];
        int result = TarUtils.formatOctalBytes(8L, buf, 0, 8);
        assertEquals(8, result);
        assertEquals((byte) ' ', buf[6]);
        assertEquals((byte) 0, buf[7]);
        assertEquals(8L, TarUtils.parseOctal(buf, 0, 8));
    }

    // zero value through formatOctalBytes still has correct trailer
    @Test
    public void testFormatOctalBytes_zeroValue_allZerosWithTrailer() throws Throwable {
        byte[] buf = new byte[8];
        TarUtils.formatOctalBytes(0L, buf, 0, 8);
        for (int i = 0; i < 6; i++) {
            assertEquals((byte) '0', buf[i]);
        }
        assertEquals((byte) ' ', buf[6]);
        assertEquals((byte) 0, buf[7]);
    }

    // formatLongOctalBytes appends trailing space only (no NUL)
    @Test
    public void testFormatLongOctalBytes_normalValue_hasTrailingSpaceNoNul() throws Throwable {
        byte[] buf = new byte[8];
        int result = TarUtils.formatLongOctalBytes(8L, buf, 0, 8);
        assertEquals(8, result);
        assertEquals((byte) ' ', buf[7]);
        assertEquals(8L, TarUtils.parseOctal(buf, 0, 8));
    }

    // formatCheckSumOctalBytes appends NUL then trailing space
    @Test
    public void testFormatCheckSumOctalBytes_normalValue_hasNulThenSpace() throws Throwable {
        byte[] buf = new byte[8];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (byte) 9;
        }
        int result = TarUtils.formatCheckSumOctalBytes(8L, buf, 0, 8);
        assertEquals(8, result);
        assertEquals((byte) 0, buf[6]);
        assertEquals((byte) ' ', buf[7]);
    }

    // empty buffer sums to zero (0-iteration loop)
    @Test
    public void testComputeCheckSum_emptyArray_returnsZero() throws Throwable {
        assertEquals(0L, TarUtils.computeCheckSum(new byte[0]));
    }

    // known positive bytes sum directly
    @Test
    public void testComputeCheckSum_knownBytes_returnsSum() throws Throwable {
        byte[] buf = new byte[] { 1, 2, 3 };
        assertEquals(6L, TarUtils.computeCheckSum(buf));
    }

    // negative byte value (0xFF) must be treated as unsigned 255, not -1
    @Test
    public void testComputeCheckSum_negativeByteValue_treatedAsUnsigned() throws Throwable {
        byte[] buf = new byte[] { (byte) 0xFF };
        assertEquals(255L, TarUtils.computeCheckSum(buf));
    }

    // mixed signed/unsigned bytes sum correctly using BYTE_MASK
    @Test
    public void testComputeCheckSum_mixedBytes_sumsAllUnsignedValues() throws Throwable {
        byte[] buf = new byte[] { (byte) 0xFF, 1, (byte) 0x80 };
        assertEquals(384L, TarUtils.computeCheckSum(buf));
    }
}
