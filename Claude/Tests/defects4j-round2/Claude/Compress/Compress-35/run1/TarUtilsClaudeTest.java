package org.apache.commons.compress.archivers.tar;

That was an accidental tool call — ignore it. Here is the requested JUnit 4 test suite.package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

public class TarUtilsClaudeTest {

    // ---------- parseOctal ----------

    // covers: valid octal digits with trailing NUL trimmed
    @Test
    public void testParseOctal_validOctalWithTrailingNUL_returnsCorrectValue() throws Throwable {
        byte[] buffer = new byte[] {'0','0','0','0','6','4','4',0};
        long result = TarUtils.parseOctal(buffer, 0, 8);
        assertEquals(420L, result);
    }

    // covers: leading spaces skipped branch
    @Test
    public void testParseOctal_leadingSpacesSkipped_returnsCorrectValue() throws Throwable {
        byte[] buffer = new byte[] {' ',' ','7','5','5',0,0,0};
        long result = TarUtils.parseOctal(buffer, 0, 8);
        assertEquals(493L, result);
    }

    // covers: leading NUL short-circuit returns 0 even with garbage after it
    @Test
    public void testParseOctal_leadingNUL_returnsZero() throws Throwable {
        byte[] buffer = new byte[] {0, 'X','X','X','X','X','X','X'};
        long result = TarUtils.parseOctal(buffer, 0, 8);
        assertEquals(0L, result);
    }

    // covers: Javadoc "allowed to contain all NULs" returns 0
    @Test
    public void testParseOctal_allNULBuffer_returnsZero() throws Throwable {
        byte[] buffer = new byte[8];
        long result = TarUtils.parseOctal(buffer, 0, 8);
        assertEquals(0L, result);
    }

    // covers: trailing spaces only (no NUL) trimmed correctly
    @Test
    public void testParseOctal_trailingSpacesOnlyNoNUL_trimsCorrectly() throws Throwable {
        byte[] buffer = new byte[] {'1','7',' ',' '};
        long result = TarUtils.parseOctal(buffer, 0, 4);
        assertEquals(15L, result);
    }

    // covers: length < 2 throws IllegalArgumentException
    @Test
    public void testParseOctal_lengthLessThanTwo_throwsIllegalArgumentException() throws Throwable {
        byte[] buffer = new byte[] {'1'};
        try {
            TarUtils.parseOctal(buffer, 0, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Length"));
        }
    }

    // covers: invalid octal digit throws IllegalArgumentException
    @Test
    public void testParseOctal_invalidDigit_throwsIllegalArgumentException() throws Throwable {
        byte[] buffer = new byte[] {'8', ' '};
        try {
            TarUtils.parseOctal(buffer, 0, 2);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Invalid byte"));
        }
    }

    // covers: explicit "0" digit parsed via normal path (not the leading-NUL shortcut)
    @Test
    public void testParseOctal_explicitZeroDigit_returnsZero() throws Throwable {
        byte[] buffer = new byte[] {'0', 0};
        long result = TarUtils.parseOctal(buffer, 0, 2);
        assertEquals(0L, result);
    }

    // ---------- parseOctalOrBinary ----------

    // covers: high bit not set -> delegates to parseOctal
    @Test
    public void testParseOctalOrBinary_octalPath_delegatesCorrectly() throws Throwable {
        byte[] buffer = new byte[] {'0','0','0','0','6','4','4',0};
        long result = TarUtils.parseOctalOrBinary(buffer, 0, 8);
        assertEquals(420L, result);
    }

    // covers: negative binary (long) path, round trip via format then parse
    @Test
    public void testParseOctalOrBinary_negativeBinaryLong_roundTrip() throws Throwable {
        byte[] buffer = new byte[8];
        TarUtils.formatLongOctalOrBinaryBytes(-12345L, buffer, 0, 8);
        long result = TarUtils.parseOctalOrBinary(buffer, 0, 8);
        assertEquals(-12345L, result);
    }

    // covers: positive binary (long) path, marker 0x80 and magnitude bytes
    @Test
    public void testParseOctalOrBinary_positiveBinaryLong_returnsValue() throws Throwable {
        byte[] buffer = new byte[8];
        buffer[0] = (byte) 0x80;
        buffer[7] = 5;
        long result = TarUtils.parseOctalOrBinary(buffer, 0, 8);
        assertEquals(5L, result);
    }

    // covers: length >= 9 -> parseBinaryBigInteger positive path
    @Test
    public void testParseOctalOrBinary_positiveBinaryBigInteger_length9_returnsValue() throws Throwable {
        byte[] buffer = new byte[9];
        buffer[0] = (byte) 0x80;
        buffer[8] = 42;
        long result = TarUtils.parseOctalOrBinary(buffer, 0, 9);
        assertEquals(42L, result);
    }

    // covers: length >= 9 negative BigInteger path, round trip via format then parse
    @Test
    public void testParseOctalOrBinary_negativeBinaryBigInteger_roundTrip() throws Throwable {
        byte[] buffer = new byte[12];
        TarUtils.formatLongOctalOrBinaryBytes(-99999L, buffer, 0, 12);
        long result = TarUtils.parseOctalOrBinary(buffer, 0, 12);
        assertEquals(-99999L, result);
    }

    // covers: BigInteger magnitude exceeding 63 bits throws IllegalArgumentException
    @Test
    public void testParseOctalOrBinary_bigIntegerOverflow_throwsIllegalArgumentException() throws Throwable {
        byte[] buffer = new byte[9];
        buffer[0] = (byte) 0xff;
        buffer[1] = (byte) 0x80;
        try {
            TarUtils.parseOctalOrBinary(buffer, 0, 9);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("exceeds"));
        }
    }

    // ---------- parseBoolean ----------

    // covers: byte value 1 -> true
    @Test
    public void testParseBoolean_byteOne_returnsTrue() throws Throwable {
        byte[] buffer = new byte[] {1};
        assertTrue(TarUtils.parseBoolean(buffer, 0));
    }

    // covers: byte value 0 -> false
    @Test
    public void testParseBoolean_byteZero_returnsFalse() throws Throwable {
        byte[] buffer = new byte[] {0};
        assertFalse(TarUtils.parseBoolean(buffer, 0));
    }

    // covers: any non-1 byte -> false
    @Test
    public void testParseBoolean_byteOther_returnsFalse() throws Throwable {
        byte[] buffer = new byte[] {2};
        assertFalse(TarUtils.parseBoolean(buffer, 0));
    }

    // ---------- parseName (2-arg) ----------

    // covers: parsing stops at trailing NUL
    @Test
    public void testParseName_withTrailingNUL_stopsAtNUL() throws Throwable {
        byte[] buffer = new byte[] {'f','o','o',0,0,0};
        String result = TarUtils.parseName(buffer, 0, 6);
        assertEquals("foo", result);
    }

    // covers: no NUL present -> uses full buffer length
    @Test
    public void testParseName_noNUL_usesFullLength() throws Throwable {
        byte[] buffer = new byte[] {'a','b','c'};
        String result = TarUtils.parseName(buffer, 0, 3);
        assertEquals("abc", result);
    }

    // covers: entirely NUL buffer -> empty string branch (len reaches 0)
    @Test
    public void testParseName_allNULBuffer_returnsEmptyString() throws Throwable {
        byte[] buffer = new byte[4];
        String result = TarUtils.parseName(buffer, 0, 4);
        assertEquals("", result);
    }

    // ---------- parseName (4-arg, encoding) / formatNameBytes round trip ----------

    // covers: parseName with explicit encoding decodes what formatNameBytes wrote
    @Test
    public void testParseNameWithEncoding_roundTripsFormattedName() throws Throwable {
        byte[] buffer = new byte[10];
        TarUtils.formatNameBytes("hi", buffer, 0, 10);
        String result = TarUtils.parseName(buffer, 0, 10, TarUtils.DEFAULT_ENCODING);
        assertEquals("hi", result);
    }

    // ---------- formatNameBytes ----------

    // covers: name shorter than buffer -> trailing NUL padding
    @Test
    public void testFormatNameBytes_shorterThanBuffer_padsWithNUL() throws Throwable {
        byte[] buffer = new byte[5];
        int offset = TarUtils.formatNameBytes("ab", buffer, 0, 5);
        assertEquals(5, offset);
        assertEquals('a', (char) buffer[0]);
        assertEquals('b', (char) buffer[1]);
        assertEquals(0, buffer[2]);
        assertEquals(0, buffer[3]);
        assertEquals(0, buffer[4]);
    }

    // covers: name longer than buffer -> truncated to fit
    @Test
    public void testFormatNameBytes_longerThanBuffer_truncates() throws Throwable {
        byte[] buffer = new byte[3];
        int offset = TarUtils.formatNameBytes("abcdef", buffer, 0, 3);
        assertEquals(3, offset);
        assertEquals('a', (char) buffer[0]);
        assertEquals('b', (char) buffer[1]);
        assertEquals('c', (char) buffer[2]);
    }

    // ---------- formatUnsignedOctalString (via formatOctalBytes contexts and direct use) ----------

    // covers: value == 0 branch fills all zero digits
    @Test
    public void testFormatUnsignedOctalString_zeroValue_fillsZeros() throws Throwable {
        byte[] buffer = new byte[4];
        TarUtils.formatUnsignedOctalString(0L, buffer, 0, 4);
        assertEquals("0000", new String(buffer, 0, 4));
    }

    // covers: positive value branch produces correct octal digits with leading zero padding
    @Test
    public void testFormatUnsignedOctalString_positiveValue_correctOctal() throws Throwable {
        byte[] buffer = new byte[4];
        TarUtils.formatUnsignedOctalString(8L, buffer, 0, 4);
        assertEquals("0010", new String(buffer, 0, 4));
    }

    // covers: value too large for buffer throws IllegalArgumentException
    @Test
    public void testFormatUnsignedOctalString_valueTooLarge_throwsIllegalArgumentException() throws Throwable {
        byte[] buffer = new byte[1];
        try {
            TarUtils.formatUnsignedOctalString(8L, buffer, 0, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("will not fit"));
        }
    }

    // ---------- formatOctalBytes ----------

    // covers: octal digits followed by trailing space then NUL
    @Test
    public void testFormatOctalBytes_appendsSpaceThenNUL() throws Throwable {
        byte[] buffer = new byte[6];
        int offset = TarUtils.formatOctalBytes(8L, buffer, 0, 6);
        assertEquals(6, offset);
        assertEquals("0010", new String(buffer, 0, 4));
        assertEquals(' ', (char) buffer[4]);
        assertEquals(0, buffer[5]);
    }

    // ---------- formatLongOctalBytes ----------

    // covers: octal digits followed by trailing space only, no NUL
    @Test
    public void testFormatLongOctalBytes_appendsSpaceOnly() throws Throwable {
        byte[] buffer = new byte[6];
        int offset = TarUtils.formatLongOctalBytes(8L, buffer, 0, 6);
        assertEquals(6, offset);
        assertEquals("00010", new String(buffer, 0, 5));
        assertEquals(' ', (char) buffer[5]);
    }

    // ---------- formatLongOctalOrBinaryBytes ----------

    // covers: small positive value fits as octal -> delegates to formatLongOctalBytes
    @Test
    public void testFormatLongOctalOrBinaryBytes_smallPositiveValue_usesOctalPath() throws Throwable {
        byte[] actual = new byte[8];
        int offset = TarUtils.formatLongOctalOrBinaryBytes(8L, actual, 0, 8);
        byte[] expected = new byte[8];
        TarUtils.formatLongOctalBytes(8L, expected, 0, 8);
        assertEquals(8, offset);
        assertArrayEquals(expected, actual);
    }

    // covers: negative value forces binary path, marker byte 0xFF, correct round trip
    @Test
    public void testFormatLongOctalOrBinaryBytes_negativeValue_usesBinaryPathWithMarker() throws Throwable {
        byte[] buffer = new byte[8];
        int offset = TarUtils.formatLongOctalOrBinaryBytes(-1L, buffer, 0, 8);
        assertEquals(8, offset);
        assertEquals((byte) 0xFF, buffer[0]);
        assertEquals(-1L, TarUtils.parseOctalOrBinary(buffer, 0, 8));
    }

    // ---------- formatCheckSumOctalBytes ----------

    // covers: octal digits followed by NUL then space
    @Test
    public void testFormatCheckSumOctalBytes_appendsNULThenSpace() throws Throwable {
        byte[] buffer = new byte[6];
        int offset = TarUtils.formatCheckSumOctalBytes(8L, buffer, 0, 6);
        assertEquals(6, offset);
        assertEquals("0010", new String(buffer, 0, 4));
        assertEquals(0, buffer[4]);
        assertEquals(' ', (char) buffer[5]);
    }

    // ---------- computeCheckSum ----------

    // covers: sums unsigned byte values across the buffer
    @Test
    public void testComputeCheckSum_sumsUnsignedBytes() throws Throwable {
        byte[] buffer = new byte[] {1, 2, 3};
        long sum = TarUtils.computeCheckSum(buffer);
        assertEquals(6L, sum);
    }

    // covers: empty buffer -> zero rounds (loop executes 0 times)
    @Test
    public void testComputeCheckSum_emptyBuffer_returnsZero() throws Throwable {
        byte[] buffer = new byte[0];
        long sum = TarUtils.computeCheckSum(buffer);
        assertEquals(0L, sum);
    }

    // ---------- verifyCheckSum ----------

    // covers: a correctly formatted header's checksum verifies as true
    @Test
    public void testVerifyCheckSum_validHeader_returnsTrue() throws Throwable {
        int headerLen = TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN + 20;
        byte[] header = new byte[headerLen];
        for (int i = 0; i < header.length; i++) {
            header[i] = (byte) 'A';
        }
        for (int i = TarConstants.CHKSUM_OFFSET; i < TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN; i++) {
            header[i] = (byte) ' ';
        }
        long sum = TarUtils.computeCheckSum(header);
        TarUtils.formatCheckSumOctalBytes(sum, header, TarConstants.CHKSUM_OFFSET, TarConstants.CHKSUMLEN);
        assertTrue(TarUtils.verifyCheckSum(header));
    }

    // covers: corrupting a non-checksum byte makes verification fail
    @Test
    public void testVerifyCheckSum_corruptedHeader_returnsFalse() throws Throwable {
        int headerLen = TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN + 20;
        byte[] header = new byte[headerLen];
        for (int i = 0; i < header.length; i++) {
            header[i] = (byte) 'A';
        }
        for (int i = TarConstants.CHKSUM_OFFSET; i < TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN; i++) {
            header[i] = (byte) ' ';
        }
        long sum = TarUtils.computeCheckSum(header);
        TarUtils.formatCheckSumOctalBytes(sum, header, TarConstants.CHKSUM_OFFSET, TarConstants.CHKSUMLEN);
        header[headerLen - 1] = (byte) 'Z';
        assertFalse(TarUtils.verifyCheckSum(header));
    }
}
