package org.apache.commons.compress.archivers.tar;

import org.junit.Test;
import static org.junit.Assert.*;

public class TarUtilsClaudeTest {

    private byte[] makeHeader() {
        int size = TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN + 20;
        if (size < 512) {
            size = 512;
        }
        byte[] header = new byte[size];
        for (int i = 0; i < header.length; i++) {
            header[i] = (byte) 1;
        }
        return header;
    }

    // parseOctal: length < 2 must throw per javadoc
    @Test
    public void testParseOctal_lengthLessThanTwo_throwsIllegalArgumentException() throws Throwable {
        byte[] buf = new byte[]{'0', '0'};
        try {
            TarUtils.parseOctal(buf, 0, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // parseOctal: leading NUL (or all-zero buffer) returns 0L per javadoc work-around
    @Test
    public void testParseOctal_leadingNulOrAllZero_returnsZero() throws Throwable {
        byte[] buf = new byte[8];
        assertEquals(0L, TarUtils.parseOctal(buf, 0, 8));
    }

    // parseOctal: leading spaces are skipped, trailing space trimmed, then digits parsed
    @Test
    public void testParseOctal_leadingSpacesSkipped_parsesValue() throws Throwable {
        byte[] buf = new byte[]{' ', '1', '7', ' '};
        assertEquals(15L, TarUtils.parseOctal(buf, 0, 4));
    }

    // parseOctal: trailing NUL and space both trimmed before parsing digits
    @Test
    public void testParseOctal_trailingNulAndSpaceTrimmed_parsesValue() throws Throwable {
        byte[] buf = new byte[]{'1', '2', 0, ' '};
        assertEquals(10L, TarUtils.parseOctal(buf, 0, 4));
    }

    // parseOctal: byte outside '0'-'7' range throws IllegalArgumentException
    @Test
    public void testParseOctal_invalidOctalDigit_throwsIllegalArgumentException() throws Throwable {
        byte[] buf = new byte[]{'8', 0};
        try {
            TarUtils.parseOctal(buf, 0, 2);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // parseOctalOrBinary: high bit not set delegates to parseOctal
    @Test
    public void testParseOctalOrBinary_highBitNotSet_delegatesToParseOctal() throws Throwable {
        byte[] buf = new byte[]{' ', '1', '7', ' '};
        assertEquals(15L, TarUtils.parseOctalOrBinary(buf, 0, 4));
    }

    // parseOctalOrBinary: negative small value, length < 9 uses parseBinaryLong path, round trips
    @Test
    public void testParseOctalOrBinary_negativeSmallValueLengthLessThanNine_roundTrips() throws Throwable {
        byte[] buf = new byte[8];
        TarUtils.formatLongOctalOrBinaryBytes(-5L, buf, 0, 8);
        assertEquals(-5L, TarUtils.parseOctalOrBinary(buf, 0, 8));
    }

    // parseOctalOrBinary: positive value, length < 9 uses parseBinaryLong path, round trips
    @Test
    public void testParseOctalOrBinary_positiveValueLengthLessThanNine_roundTrips() throws Throwable {
        byte[] buf = new byte[8];
        long value = 12345678901L;
        TarUtils.formatLongOctalOrBinaryBytes(value, buf, 0, 8);
        assertEquals(value, TarUtils.parseOctalOrBinary(buf, 0, 8));
    }

    // parseOctalOrBinary: positive value, length >= 9 uses BigInteger path, round trips
    @Test
    public void testParseOctalOrBinary_positiveValueLengthAtLeastNine_roundTrips() throws Throwable {
        byte[] buf = new byte[12];
        long value = 9999999999999L;
        TarUtils.formatLongOctalOrBinaryBytes(value, buf, 0, 12);
        assertEquals(value, TarUtils.parseOctalOrBinary(buf, 0, 12));
    }

    // parseOctalOrBinary: negative value, length >= 9 uses BigInteger path, round trips
    @Test
    public void testParseOctalOrBinary_negativeValueLengthAtLeastNine_roundTrips() throws Throwable {
        byte[] buf = new byte[12];
        long value = -123456789012L;
        TarUtils.formatLongOctalOrBinaryBytes(value, buf, 0, 12);
        assertEquals(value, TarUtils.parseOctalOrBinary(buf, 0, 12));
    }

    // parseOctalOrBinary: BigInteger magnitude exceeding 63 bits must throw per javadoc
    @Test
    public void testParseOctalOrBinary_bitLengthExceeds63_throwsIllegalArgumentException() throws Throwable {
        byte[] buf = new byte[]{(byte) 0x80, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        try {
            TarUtils.parseOctalOrBinary(buf, 0, 10);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // parseBoolean: byte value 1 means true
    @Test
    public void testParseBoolean_byteOne_returnsTrue() throws Throwable {
        byte[] buf = new byte[]{1};
        assertTrue(TarUtils.parseBoolean(buf, 0));
    }

    // parseBoolean: byte value 0 means false
    @Test
    public void testParseBoolean_byteZero_returnsFalse() throws Throwable {
        byte[] buf = new byte[]{0};
        assertFalse(TarUtils.parseBoolean(buf, 0));
    }

    // parseBoolean: any other byte value also means false (only 1 is true)
    @Test
    public void testParseBoolean_otherByteValue_returnsFalse() throws Throwable {
        byte[] buf = new byte[]{5};
        assertFalse(TarUtils.parseBoolean(buf, 0));
    }



    // parseName: no NUL present, uses full given length
    @Test
    public void testParseName_noTrailingNul_usesFullLength() throws Throwable {
        byte[] buf = new byte[]{'a', 'b', 'c'};
        assertEquals("abc", TarUtils.parseName(buf, 0, 3));
    }

    // parseName: all-NUL buffer returns empty string
    @Test
    public void testParseName_allNulBuffer_returnsEmptyString() throws Throwable {
        byte[] buf = new byte[]{0, 0, 0};
        assertEquals("", TarUtils.parseName(buf, 0, 3));
    }

    // parseName: explicit encoding overload decodes correctly and stops at NUL
    @Test
    public void testParseName_withExplicitEncoding_decodesCorrectly() throws Throwable {
        byte[] buf = new byte[]{'x', 'y', 0};
        assertEquals("xy", TarUtils.parseName(buf, 0, 3, TarUtils.DEFAULT_ENCODING));
    }

    // formatNameBytes: name shorter than buffer is padded with trailing NULs
    @Test
    public void testFormatNameBytes_nameShorterThanLength_padsWithNul() throws Throwable {
        byte[] buf = new byte[5];
        int off = TarUtils.formatNameBytes("ab", buf, 0, 5);
        assertEquals(5, off);
        assertEquals('a', (char) buf[0]);
        assertEquals('b', (char) buf[1]);
        assertEquals(0, buf[2]);
        assertEquals(0, buf[4]);
    }

    // formatNameBytes: name longer than buffer is truncated to fit
    @Test
    public void testFormatNameBytes_nameLongerThanLength_truncatesOutput() throws Throwable {
        byte[] buf = new byte[3];
        int off = TarUtils.formatNameBytes("abcdef", buf, 0, 3);
        assertEquals(3, off);
        assertEquals('a', (char) buf[0]);
        assertEquals('c', (char) buf[2]);
    }

    // formatNameBytes: explicit encoding overload, name fits exactly with padding
    @Test
    public void testFormatNameBytes_withExplicitEncoding_noPaddingNeeded() throws Throwable {
        byte[] buf = new byte[4];
        int off = TarUtils.formatNameBytes("ab", buf, 0, 4, TarUtils.FALLBACK_ENCODING);
        assertEquals(4, off);
        assertEquals('a', (char) buf[0]);
        assertEquals(0, buf[3]);
    }

    // formatUnsignedOctalString: value 0 produces all-zero octal digits
    @Test
    public void testFormatUnsignedOctalString_zeroValue_producesAllZeroDigits() throws Throwable {
        byte[] buf = new byte[4];
        TarUtils.formatUnsignedOctalString(0L, buf, 0, 4);
        assertEquals("0000", new String(buf, 0, 4));
    }

    // formatUnsignedOctalString: normal value produces correct leading-zero-padded octal digits
    @Test
    public void testFormatUnsignedOctalString_normalValue_producesCorrectOctalDigits() throws Throwable {
        byte[] buf = new byte[4];
        TarUtils.formatUnsignedOctalString(8L, buf, 0, 4);
        assertEquals("0010", new String(buf, 0, 4));
    }

    // formatUnsignedOctalString: value too large for buffer length throws IllegalArgumentException
    @Test
    public void testFormatUnsignedOctalString_valueTooLargeForBuffer_throwsIllegalArgumentException() throws Throwable {
        byte[] buf = new byte[1];
        try {
            TarUtils.formatUnsignedOctalString(8L, buf, 0, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // formatOctalBytes: appends trailing space then NUL after the octal digits
    @Test
    public void testFormatOctalBytes_appendsTrailingSpaceAndNul() throws Throwable {
        byte[] buf = new byte[6];
        int off = TarUtils.formatOctalBytes(8L, buf, 0, 6);
        assertEquals(6, off);
        assertEquals((byte) ' ', buf[4]);
        assertEquals(0, buf[5]);
    }

    // formatLongOctalBytes: appends trailing space only (no NUL) after digits
    @Test
    public void testFormatLongOctalBytes_appendsTrailingSpaceOnly() throws Throwable {
        byte[] buf = new byte[6];
        int off = TarUtils.formatLongOctalBytes(8L, buf, 0, 6);
        assertEquals(6, off);
        assertEquals((byte) ' ', buf[5]);
    }

    // formatLongOctalOrBinaryBytes: value within octal-representable range uses plain octal encoding
    @Test
    public void testFormatLongOctalOrBinaryBytes_valueFitsAsOctal_usesOctalEncoding() throws Throwable {
        byte[] buf = new byte[12];
        int off = TarUtils.formatLongOctalOrBinaryBytes(100L, buf, 0, 12);
        assertEquals(12, off);
        assertEquals((byte) '0', buf[0]);
        assertEquals(100L, TarUtils.parseOctalOrBinary(buf, 0, 12));
    }

    // formatLongOctalOrBinaryBytes: value exactly fitting the binary field (length<9) must not throw
    // and must round-trip; catches the missing-return bug that over-restricts via BigInteger path
    @Test
    public void testFormatLongOctalOrBinaryBytes_largeBinaryValueFitsExactly_doesNotThrowAndRoundTrips() throws Throwable {
        byte[] buf = new byte[8];
        long value = (1L << 56) - 1L;
        int off = TarUtils.formatLongOctalOrBinaryBytes(value, buf, 0, 8);
        assertEquals(8, off);
        assertEquals(value, TarUtils.parseOctalOrBinary(buf, 0, 8));
    }

    // formatCheckSumOctalBytes: appends NUL then trailing space after digits
    @Test
    public void testFormatCheckSumOctalBytes_appendsNulThenSpace() throws Throwable {
        byte[] buf = new byte[8];
        int off = TarUtils.formatCheckSumOctalBytes(8L, buf, 0, 8);
        assertEquals(8, off);
        assertEquals(0, buf[6]);
        assertEquals((byte) ' ', buf[7]);
    }

    // computeCheckSum: empty buffer sums to zero (0 iterations of the loop)
    @Test
    public void testComputeCheckSum_emptyBuffer_returnsZero() throws Throwable {
        assertEquals(0L, TarUtils.computeCheckSum(new byte[0]));
    }

    // computeCheckSum: negative byte values are masked and treated as unsigned
    @Test
    public void testComputeCheckSum_masksNegativeByteAsUnsigned() throws Throwable {
        byte[] buf = new byte[]{(byte) 0xFF};
        assertEquals(255L, TarUtils.computeCheckSum(buf));
    }

    // computeCheckSum: sums several bytes correctly
    @Test
    public void testComputeCheckSum_sumsMultipleBytes() throws Throwable {
        byte[] buf = new byte[]{1, 2, 3};
        assertEquals(6L, TarUtils.computeCheckSum(buf));
    }

    // verifyCheckSum: stored checksum equals the computed unsigned sum -> true
    @Test
    public void testVerifyCheckSum_unsignedSumMatches_returnsTrue() throws Throwable {
        byte[] header = makeHeader();
        long sum = 0;
        for (int i = 0; i < header.length; i++) {
            boolean inChk = i >= TarConstants.CHKSUM_OFFSET && i < TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN;
            sum += inChk ? 32 : (0xff & header[i]);
        }
        TarUtils.formatCheckSumOctalBytes(sum, header, TarConstants.CHKSUM_OFFSET, TarConstants.CHKSUMLEN);
        assertTrue(TarUtils.verifyCheckSum(header));
    }

    // verifyCheckSum: stored checksum matches neither unsigned nor signed sum -> false
    @Test
    public void testVerifyCheckSum_mismatchedChecksum_returnsFalse() throws Throwable {
        byte[] header = makeHeader();
        long sum = 0;
        for (int i = 0; i < header.length; i++) {
            boolean inChk = i >= TarConstants.CHKSUM_OFFSET && i < TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN;
            sum += inChk ? 32 : (0xff & header[i]);
        }
        TarUtils.formatCheckSumOctalBytes(sum + 1, header, TarConstants.CHKSUM_OFFSET, TarConstants.CHKSUMLEN);
        assertFalse(TarUtils.verifyCheckSum(header));
    }

    // verifyCheckSum: signed sum differs from unsigned sum but matches stored value -> true via OR branch
    @Test
    public void testVerifyCheckSum_signedSumMatchesButUnsignedDoesNot_returnsTrue() throws Throwable {
        byte[] header = makeHeader();
        int specialIndex = TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN + 10;
        header[specialIndex] = (byte) 0xFF;
        long unsignedSum = 0;
        long signedSum = 0;
        for (int i = 0; i < header.length; i++) {
            boolean inChk = i >= TarConstants.CHKSUM_OFFSET && i < TarConstants.CHKSUM_OFFSET + TarConstants.CHKSUMLEN;
            byte b = inChk ? (byte) ' ' : header[i];
            unsignedSum += 0xff & b;
            signedSum += b;
        }
        assertTrue(unsignedSum != signedSum);
        TarUtils.formatCheckSumOctalBytes(signedSum, header, TarConstants.CHKSUM_OFFSET, TarConstants.CHKSUMLEN);
        assertTrue(TarUtils.verifyCheckSum(header));
    }
}
