package org.apache.commons.codec.binary;

import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;

public class Base64ClaudeTest {

    private byte[] pattern(int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) {
            b[i] = (byte) ((i * 7 + 3) % 256);
        }
        return b;
    }

    // covers Base64() -> Base64(0) path, isUrlSafe should be false
    @Test
    public void testDefaultConstructor_notUrlSafe() throws Throwable {
        Base64 b64 = new Base64();
        assertFalse(b64.isUrlSafe());
    }

    // covers Base64(boolean) with true branch
    @Test
    public void testUrlSafeConstructor_true_setsUrlSafeMode() throws Throwable {
        Base64 b64 = new Base64(true);
        assertTrue(b64.isUrlSafe());
    }

    // covers Base64(boolean) with false branch
    @Test
    public void testUrlSafeConstructor_false_setsStandardMode() throws Throwable {
        Base64 b64 = new Base64(false);
        assertFalse(b64.isUrlSafe());
    }

    // covers containsBase64Byte branch triggering IllegalArgumentException
    @Test
    public void testConstructor_lineSeparatorContainsBase64Char_throwsIllegalArgumentException() throws Throwable {
        try {
            new Base64(10, new byte[]{'A'});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers lineSeparator == null branch: lineLength forced to 0, no separator ever inserted
    @Test
    public void testConstructor_nullLineSeparator_disablesChunking() throws Throwable {
        Base64 b64 = new Base64(10, null, false);
        byte[] data = pattern(100);
        byte[] encoded = b64.encode(data);
        for (int i = 0; i < encoded.length; i++) {
            assertFalse(encoded[i] == '\r' || encoded[i] == '\n');
        }
    }

    // covers standard alphabet char branch
    @Test
    public void testIsBase64_plusChar_true() throws Throwable {
        assertTrue(Base64.isBase64((byte) '+'));
    }

    // covers octet == PAD branch
    @Test
    public void testIsBase64_padChar_true() throws Throwable {
        assertTrue(Base64.isBase64((byte) '='));
    }

    // covers in-range but -1 decode table entry branch
    @Test
    public void testIsBase64_invalidPrintableChar_false() throws Throwable {
        assertFalse(Base64.isBase64((byte) '!'));
    }

    // covers octet < 0 short-circuit branch
    @Test
    public void testIsBase64_negativeByte_false() throws Throwable {
        assertFalse(Base64.isBase64((byte) -128));
    }

    // covers for-loop zero iterations -> true
    @Test
    public void testIsArrayByteBase64_emptyArray_true() throws Throwable {
        assertTrue(Base64.isArrayByteBase64(new byte[0]));
    }

    // covers loop with all valid base64 chars
    @Test
    public void testIsArrayByteBase64_validChars_true() throws Throwable {
        assertTrue(Base64.isArrayByteBase64(new byte[]{'A', 'b', '0', '+', '/', '='}));
    }

    // covers isWhiteSpace branch treated as valid
    @Test
    public void testIsArrayByteBase64_whitespaceOnly_true() throws Throwable {
        assertTrue(Base64.isArrayByteBase64(new byte[]{' ', '\t', '\r', '\n'}));
    }

    // covers early return false branch
    @Test
    public void testIsArrayByteBase64_invalidChar_false() throws Throwable {
        assertFalse(Base64.isArrayByteBase64(new byte[]{'A', '!', 'B'}));
    }

    // covers binaryData == null branch
    @Test
    public void testEncodeBase64_nullInput_returnsNull() throws Throwable {
        assertNull(Base64.encodeBase64(null));
    }

    // covers binaryData.length == 0 branch
    @Test
    public void testEncodeBase64_emptyInput_returnsEmptyArray() throws Throwable {
        byte[] result = Base64.encodeBase64(new byte[0]);
        assertEquals(0, result.length);
    }

    // known canonical vector, exercises full 3-byte group encode plus trailing chunk separator
    @Test
    public void testEncodeBase64_knownVector_Man() throws Throwable {
        byte[] data = "Man".getBytes("UTF-8");
        assertEquals("TWFu", Base64.encodeBase64String(data).trim());
    }

    // exercises modulus==2 leftover branch (one '=' pad)
    @Test
    public void testEncodeBase64_knownVector_Ma_withPadding() throws Throwable {
        byte[] data = "Ma".getBytes("UTF-8");
        assertEquals("TWE=", Base64.encodeBase64String(data).trim());
    }

    // exercises modulus==1 leftover branch (two '=' pad)
    @Test
    public void testEncodeBase64_knownVector_M_withDoublePadding() throws Throwable {
        byte[] data = "M".getBytes("UTF-8");
        assertEquals("TQ==", Base64.encodeBase64String(data).trim());
    }

    // covers encodeBase64(data,false) path producing no CRLF at all
    @Test
    public void testEncodeBase64_nonChunked_noTrailingSeparator() throws Throwable {
        byte[] data = "Man".getBytes("UTF-8");
        byte[] encoded = Base64.encodeBase64(data, false);
        assertEquals("TWFu", new String(encoded, "UTF-8"));
    }

    // covers URL-SAFE skip-padding branch for modulus==2 leftover
    @Test
    public void testEncodeBase64URLSafe_noPaddingCharacters() throws Throwable {
        byte[] data = "Ma".getBytes("UTF-8");
        byte[] encoded = Base64.encodeBase64URLSafe(data);
        for (int i = 0; i < encoded.length; i++) {
            assertFalse(encoded[i] == '=');
        }
    }

    // round trip through url-safe string encoding and generic decoder
    @Test
    public void testEncodeBase64URLSafeString_decodesBackToOriginal() throws Throwable {
        byte[] data = pattern(5);
        String encoded = Base64.encodeBase64URLSafeString(data);
        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(data, decoded);
    }

    // covers lineLength <= currentLinePos branch inserting CRLF mid-stream, plus round trip
    @Test
    public void testEncodeBase64Chunked_longInput_roundTripsAndContainsSeparator() throws Throwable {
        byte[] data = pattern(60);
        byte[] encoded = Base64.encodeBase64Chunked(data);
        boolean foundCr = false;
        for (int i = 0; i < encoded.length; i++) {
            if (encoded[i] == '\r') foundCr = true;
        }
        assertTrue(foundCr);
        assertArrayEquals(data, Base64.decodeBase64(encoded));
    }

    // covers len > maxResultSize branch
    @Test
    public void testEncodeBase64_maxResultSizeExceeded_throwsIllegalArgumentException() throws Throwable {
        byte[] data = pattern(1000);
        try {
            Base64.encodeBase64(data, false, false, 10);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers len <= maxResultSize branch (no throw)
    @Test
    public void testEncodeBase64_maxResultSizeSufficient_noException() throws Throwable {
        byte[] data = pattern(3);
        byte[] encoded = Base64.encodeBase64(data, false, false, 1000);
        assertEquals(4, encoded.length);
    }

    // canonical decode vector
    @Test
    public void testDecodeBase64String_knownVector_Man() throws Throwable {
        byte[] decoded = Base64.decodeBase64("TWFu");
        assertArrayEquals("Man".getBytes("UTF-8"), decoded);
    }

    // canonical decode vector with double padding, exercises modulus==3 EOF branch
    @Test
    public void testDecodeBase64Bytes_knownVector_M() throws Throwable {
        byte[] decoded = Base64.decodeBase64("TQ==".getBytes("UTF-8"));
        assertArrayEquals("M".getBytes("UTF-8"), decoded);
    }

    // covers decode loop skipping non-base64 (whitespace) bytes
    @Test
    public void testDecode_ignoresEmbeddedWhitespace() throws Throwable {
        byte[] decoded = Base64.decodeBase64("TW Fu".getBytes("UTF-8"));
        assertArrayEquals("Man".getBytes("UTF-8"), decoded);
    }

    // covers EOF modulus==2 branch without explicit '=' present in input (padding optional)
    @Test
    public void testDecode_paddingIsOptional() throws Throwable {
        byte[] decoded = Base64.decodeBase64("TWE".getBytes("UTF-8"));
        assertArrayEquals("Ma".getBytes("UTF-8"), decoded);
    }

    // covers pObject instanceof byte[] branch of Object decode
    @Test
    public void testDecodeObject_byteArray_returnsDecodedBytes() throws Throwable {
        Base64 b64 = new Base64();
        Object result = b64.decode((Object) "TQ==".getBytes("UTF-8"));
        assertArrayEquals("M".getBytes("UTF-8"), (byte[]) result);
    }

    // covers pObject instanceof String branch of Object decode
    @Test
    public void testDecodeObject_string_returnsDecodedBytes() throws Throwable {
        Base64 b64 = new Base64();
        Object result = b64.decode((Object) "TQ==");
        assertArrayEquals("M".getBytes("UTF-8"), (byte[]) result);
    }

    // covers else branch throwing DecoderException for unsupported type
    @Test
    public void testDecodeObject_invalidType_throwsDecoderException() throws Throwable {
        Base64 b64 = new Base64();
        try {
            b64.decode((Object) Integer.valueOf(5));
            fail("expected DecoderException");
        } catch (DecoderException expected) {
        }
    }

    // covers pObject instanceof byte[] branch of Encoder interface
    @Test
    public void testEncodeObject_byteArray_returnsEncodedBytes() throws Throwable {
        Base64 b64 = new Base64();
        Object result = b64.encode((Object) "Man".getBytes("UTF-8"));
        assertEquals("TWFu", new String((byte[]) result, "UTF-8"));
    }

    // covers !(pObject instanceof byte[]) branch throwing EncoderException
    @Test
    public void testEncodeObject_invalidType_throwsEncoderException() throws Throwable {
        Base64 b64 = new Base64();
        try {
            b64.encode((Object) "not a byte array");
            fail("expected EncoderException");
        } catch (EncoderException expected) {
        }
    }

    // covers pArray == null branch of instance encode
    @Test
    public void testInstanceEncodeByteArray_nullInput_returnsNull() throws Throwable {
        Base64 b64 = new Base64();
        assertNull(b64.encode((byte[]) null));
    }

    // covers pArray.length == 0 branch of instance encode
    @Test
    public void testInstanceEncodeByteArray_emptyInput_returnsEmptyArray() throws Throwable {
        Base64 b64 = new Base64();
        byte[] result = b64.encode(new byte[0]);
        assertEquals(0, result.length);
    }

    // covers pArray == null branch of instance decode
    @Test
    public void testInstanceDecodeByteArray_nullInput_returnsNull() throws Throwable {
        Base64 b64 = new Base64();
        assertNull(b64.decode((byte[]) null));
    }

    // covers pArray.length == 0 branch of instance decode
    @Test
    public void testInstanceDecodeByteArray_emptyInput_returnsEmptyArray() throws Throwable {
        Base64 b64 = new Base64();
        byte[] result = b64.decode(new byte[0]);
        assertEquals(0, result.length);
    }

    // combines instance encode + decode(String) for round trip
    @Test
    public void testInstanceDecodeString_roundTripsWithEncode() throws Throwable {
        Base64 b64 = new Base64();
        byte[] data = pattern(7);
        String encoded = b64.encodeToString(data);
        byte[] decoded = b64.decode(encoded);
        assertArrayEquals(data, decoded);
    }

    // covers modulus 0,1,2 leftover branches for both encode and decode together, strongest bug-agnostic oracle
    @Test
    public void testRoundTrip_variousLengths_modulusBranches() throws Throwable {
        for (int len = 0; len <= 9; len++) {
            byte[] data = pattern(len);
            byte[] encoded = Base64.encodeBase64(data);
            byte[] decoded = Base64.decodeBase64(encoded);
            assertArrayEquals(data, decoded);
        }
    }

    // covers BigInteger.ZERO edge case through encodeInteger/decodeInteger round trip
    @Test
    public void testEncodeDecodeInteger_zero_roundTrips() throws Throwable {
        BigInteger value = BigInteger.ZERO;
        byte[] encoded = Base64.encodeInteger(value);
        BigInteger decoded = Base64.decodeInteger(encoded);
        assertEquals(0, value.compareTo(decoded));
    }

    // covers byte-aligned bit-length edge cases in toIntegerBytes indirectly via public API
    @Test
    public void testEncodeDecodeInteger_byteBoundaryValues_roundTrip() throws Throwable {
        int[] values = {1, 127, 128, 255, 256, 65535, 65536};
        for (int i = 0; i < values.length; i++) {
            BigInteger value = BigInteger.valueOf(values[i]);
            byte[] encoded = Base64.encodeInteger(value);
            BigInteger decoded = Base64.decodeInteger(encoded);
            assertEquals(0, value.compareTo(decoded));
        }
    }

    // covers multi-byte large BigInteger path
    @Test
    public void testEncodeDecodeInteger_largeValue_roundTrips() throws Throwable {
        BigInteger value = new BigInteger("123456789012345678901234567890");
        byte[] encoded = Base64.encodeInteger(value);
        BigInteger decoded = Base64.decodeInteger(encoded);
        assertEquals(0, value.compareTo(decoded));
    }

    // covers explicit null check throwing NullPointerException per javadoc
    @Test
    public void testEncodeInteger_nullInput_throwsNullPointerException() throws Throwable {
        try {
            Base64.encodeInteger(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // covers buffer == null branches of hasData()/avail() on a fresh instance
    @Test
    public void testHasDataAndAvail_freshInstance_emptyState() throws Throwable {
        Base64 b64 = new Base64();
        assertFalse(b64.hasData());
        assertEquals(0, b64.avail());
    }

    // covers switch-case whitespace branches vs default branch of discardWhitespace
    @Test
    public void testDiscardWhitespace_removesOnlyWhitespaceBytes() throws Throwable {
        byte[] input = new byte[]{' ', 'A', '\n', 'B', '\r', '\t', 'C'};
        byte[] result = Base64.discardWhitespace(input);
        assertArrayEquals(new byte[]{'A', 'B', 'C'}, result);
    }
}
