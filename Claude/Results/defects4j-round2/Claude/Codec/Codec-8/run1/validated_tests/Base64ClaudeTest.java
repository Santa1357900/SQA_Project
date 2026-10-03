package org.apache.commons.codec.binary;

import java.math.BigInteger;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;

import org.junit.Test;
import static org.junit.Assert.*;

public class Base64ClaudeTest {

    // covers default no-arg constructor: non-url-safe table used
    @Test
    public void testConstructor_default_isUrlSafeFalse() throws Throwable {
        Base64 b64 = new Base64();
        assertFalse(b64.isUrlSafe());
    }

    // covers Base64(boolean) constructor: urlSafe flag reflected by isUrlSafe()
    @Test
    public void testConstructor_urlSafeTrue_isUrlSafeTrue() throws Throwable {
        Base64 b64 = new Base64(true);
        assertTrue(b64.isUrlSafe());
    }

    // covers containsBase64Byte branch: lineSeparator with base64 char throws IllegalArgumentException
    @Test
    public void testConstructor_lineSeparatorContainsBase64Char_throwsIllegalArgumentException() throws Throwable {
        try {
            new Base64(76, new byte[] {'A'});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers null lineSeparator branch: chunking disabled entirely (lineLength forced to 0)
    @Test
    public void testConstructor_nullLineSeparator_disablesChunking() throws Throwable {
        Base64 b64 = new Base64(76, null);
        byte[] data = new byte[9];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i + 1);
        }
        byte[] result = b64.encode(data);
        assertEquals(12, result.length);
    }

    // covers lineLength rounding down to nearest multiple of 4 (10 -> 8) per javadoc
    @Test
    public void testConstructorLineLength_roundedDownToMultipleOf4() throws Throwable {
        Base64 b64 = new Base64(10);
        byte[] data = new byte[9];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i + 1);
        }
        byte[] result = b64.encode(data);
        assertEquals(13, result[8]);
        assertEquals(10, result[9]);
    }

    // covers hasData() returning false before any streaming operation
    @Test
    public void testHasData_falseBeforeAnyOperation() throws Throwable {
        Base64 b64 = new Base64();
        assertFalse(b64.hasData());
    }

    // covers avail() returning zero before any streaming operation
    @Test
    public void testAvail_zeroBeforeAnyOperation() throws Throwable {
        Base64 b64 = new Base64();
        assertEquals(0, b64.avail());
    }

    // covers internal encode(byte[],int,int), avail() and readResults() streaming path
    @Test
    public void testStreamingEncode_avail_and_readResults() throws Throwable {
        Base64 b64 = new Base64();
        byte[] data = {77, 97, 110};
        b64.encode(data, 0, data.length);
        b64.encode(data, 0, -1);
        assertEquals(4, b64.avail());
        byte[] out = new byte[4];
        int n = b64.readResults(out, 0, 4);
        assertEquals(4, n);
        assertEquals("TWFu", new String(out, "UTF-8"));
    }

    // covers internal decode(byte[],int,int) streaming path matching known vector
    @Test
    public void testStreamingDecode_internalMethods() throws Throwable {
        Base64 b64 = new Base64();
        byte[] data = "TWFu".getBytes("UTF-8");
        b64.decode(data, 0, data.length);
        b64.decode(data, 0, -1);
        assertEquals(3, b64.avail());
        byte[] out = new byte[3];
        b64.readResults(out, 0, 3);
        assertEquals("Man", new String(out, "UTF-8"));
    }

    // covers encode/decode round trip with well-known base64 test vector "Man" <-> "TWFu"
    @Test
    public void testEncodeDecode_knownVector_Man() throws Throwable {
        byte[] data = "Man".getBytes("UTF-8");
        byte[] encoded = new Base64().encode(data);
        assertEquals("TWFu", new String(encoded, "UTF-8"));
        byte[] decoded = new Base64().decode(encoded);
        assertEquals("Man", new String(decoded, "UTF-8"));
    }

    // covers encode(byte[]) null-array branch
    @Test
    public void testEncode_nullArray_returnsNull() throws Throwable {
        assertNull(new Base64().encode((byte[]) null));
    }

    // covers encode(byte[]) empty-array branch
    @Test
    public void testEncode_emptyArray_returnsEmptyArray() throws Throwable {
        byte[] result = new Base64().encode(new byte[0]);
        assertEquals(0, result.length);
    }

    // covers decode(byte[]) null-array branch
    @Test
    public void testDecode_nullArray_returnsNull() throws Throwable {
        assertNull(new Base64().decode((byte[]) null));
    }

    // covers decode(byte[]) empty-array branch
    @Test
    public void testDecode_emptyArray_returnsEmptyArray() throws Throwable {
        byte[] result = new Base64().decode(new byte[0]);
        assertEquals(0, result.length);
    }

    // covers encodeToString with padding for single-byte input (standard table)
    @Test
    public void testEncodeToString_singleByte() throws Throwable {
        String s = new Base64().encodeToString(new byte[] {65});
        assertEquals("QQ==", s);
    }

    // covers isBase64: PAD byte considered part of the base64 alphabet
    @Test
    public void testIsBase64_padChar_true() throws Throwable {
        assertTrue(Base64.isBase64((byte) '='));
    }

    // covers isBase64: valid alphabet letter
    @Test
    public void testIsBase64_validLetter_true() throws Throwable {
        assertTrue(Base64.isBase64((byte) 'A'));
    }

    // covers isBase64: invalid character within table bounds
    @Test
    public void testIsBase64_invalidChar_false() throws Throwable {
        assertFalse(Base64.isBase64((byte) '!'));
    }

    // covers isBase64: negative octet branch
    @Test
    public void testIsBase64_negativeOctet_false() throws Throwable {
        assertFalse(Base64.isBase64((byte) -1));
    }

    // covers isArrayByteBase64: empty array returns true per javadoc
    @Test
    public void testIsArrayByteBase64_emptyArray_true() throws Throwable {
        assertTrue(Base64.isArrayByteBase64(new byte[0]));
    }

    // covers isArrayByteBase64: whitespace-only bytes treated as valid
    @Test
    public void testIsArrayByteBase64_whitespaceOnly_true() throws Throwable {
        assertTrue(Base64.isArrayByteBase64(new byte[] {' ', '\t', '\r', '\n'}));
    }

    // covers isArrayByteBase64: invalid byte causes false
    @Test
    public void testIsArrayByteBase64_invalidByte_false() throws Throwable {
        assertFalse(Base64.isArrayByteBase64(new byte[] {'A', '!'}));
    }

    // covers encodeBase64(data,false): no chunking means no CR bytes present
    @Test
    public void testEncodeBase64_noChunk_noCRLF() throws Throwable {
        byte[] data = new byte[60];
        for (int i = 0; i < data.length; i++) {
            data[i] = 2;
        }
        byte[] result = Base64.encodeBase64(data, false);
        for (int i = 0; i < result.length; i++) {
            assertTrue(result[i] != '\r');
        }
    }

    // covers encodeBase64Chunked: chunked output contains CRLF separators
    @Test
    public void testEncodeBase64Chunked_containsCRLF() throws Throwable {
        byte[] data = new byte[60];
        for (int i = 0; i < data.length; i++) {
            data[i] = 2;
        }
        byte[] result = Base64.encodeBase64Chunked(data);
        boolean found = false;
        for (int i = 0; i < result.length - 1; i++) {
            if (result[i] == '\r' && result[i + 1] == '\n') {
                found = true;
            }
        }
        assertTrue(found);
    }

    // covers encodeBase64String with known base64 vector
    @Test
    public void testEncodeBase64String_matchesKnownVector() throws Throwable {
        String s = Base64.encodeBase64String("Man".getBytes("UTF-8"));
        assertEquals("TWFu", s);
    }

    // covers encodeBase64URLSafe: padding skipped for url-safe mode
    @Test
    public void testEncodeBase64URLSafe_noPadding() throws Throwable {
        byte[] result = Base64.encodeBase64URLSafe(new byte[] {65});
        assertEquals(2, result.length);
        assertEquals("QQ", new String(result, "UTF-8"));
    }

    // covers encodeBase64URLSafeString: no '+' or '/' chars ever emitted
    @Test
    public void testEncodeBase64URLSafeString_noPlusSlash() throws Throwable {
        String s = Base64.encodeBase64URLSafeString(new byte[] {(byte) 0xFB, (byte) 0xFF, (byte) 0xFF});
        assertTrue(s.indexOf('+') < 0 && s.indexOf('/') < 0);
    }

    // covers decode(Object) byte[] branch
    @Test
    public void testDecodeObject_byteArray() throws Throwable {
        Base64 b64 = new Base64();
        byte[] data = "TWFu".getBytes("UTF-8");
        Object result = b64.decode((Object) data);
        assertTrue(result instanceof byte[]);
        assertEquals("Man", new String((byte[]) result, "UTF-8"));
    }

    // covers decode(Object) String branch
    @Test
    public void testDecodeObject_string() throws Throwable {
        Base64 b64 = new Base64();
        Object result = b64.decode((Object) "TWFu");
        assertEquals("Man", new String((byte[]) result, "UTF-8"));
    }

    // covers decode(Object) invalid-type branch throwing DecoderException
    @Test
    public void testDecodeObject_invalidType_throwsDecoderException() throws Throwable {
        Base64 b64 = new Base64();
        try {
            b64.decode((Object) Integer.valueOf(5));
            fail("expected DecoderException");
        } catch (DecoderException expected) {
        }
    }

    // covers encode(Object) byte[] branch
    @Test
    public void testEncodeObject_byteArray() throws Throwable {
        Base64 b64 = new Base64();
        Object result = b64.encode((Object) "Man".getBytes("UTF-8"));
        assertEquals("TWFu", new String((byte[]) result, "UTF-8"));
    }

    // covers encode(Object) invalid-type branch throwing EncoderException
    @Test
    public void testEncodeObject_invalidType_throwsEncoderException() throws Throwable {
        Base64 b64 = new Base64();
        try {
            b64.encode((Object) "not a byte array");
            fail("expected EncoderException");
        } catch (EncoderException expected) {
        }
    }

    // covers static decodeBase64(String) helper
    @Test
    public void testDecodeBase64String_staticHelper() throws Throwable {
        byte[] result = Base64.decodeBase64("TWFu");
        assertEquals("Man", new String(result, "UTF-8"));
    }

    // covers static decodeBase64(byte[]) helper
    @Test
    public void testDecodeBase64ByteArray_staticHelper() throws Throwable {
        byte[] result = Base64.decodeBase64("TWFu".getBytes("UTF-8"));
        assertEquals("Man", new String(result, "UTF-8"));
    }

    // covers encodeInteger null check throwing NullPointerException per javadoc
    @Test
    public void testEncodeInteger_null_throwsNullPointerException() throws Throwable {
        try {
            Base64.encodeInteger(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // covers encodeInteger/decodeInteger round trip for a positive BigInteger
    @Test
    public void testEncodeDecodeInteger_roundTrip() throws Throwable {
        BigInteger original = BigInteger.valueOf(123456789L);
        byte[] encoded = Base64.encodeInteger(original);
        BigInteger decoded = Base64.decodeInteger(encoded);
        assertEquals(original, decoded);
    }

    // covers encodeBase64 with maxResultSize: hugely exceeded size throws regardless of chunk mode
    @Test
    public void testEncodeBase64_maxResultSizeHugelyExceeded_throwsIllegalArgumentException() throws Throwable {
        byte[] data = new byte[1000];
        try {
            Base64.encodeBase64(data, false, false, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }



    // covers encodeBase64 with maxResultSize for chunked output: actual chunked size (84) exceeds 80
    @Test
    public void testEncodeBase64_chunked_sameSizeExceedsMaxResultSize_throws() throws Throwable {
        byte[] data = new byte[60];
        for (int i = 0; i < data.length; i++) {
            data[i] = 5;
        }
        try {
            Base64.encodeBase64(data, true, false, 80);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers discardWhitespace removing space/tab/CR/LF while keeping other bytes
    @Test
    public void testDiscardWhitespace_removesWhitespaceOnly() throws Throwable {
        byte[] input = new byte[] {'A', ' ', 'B', '\t', 'C', '\r', 'D', '\n', 'E'};
        byte[] result = Base64.discardWhitespace(input);
        assertEquals("ABCDE", new String(result, "UTF-8"));
    }

    // covers decode ignoring non-base64 characters silently (garbage-in philosophy)
    @Test
    public void testDecode_ignoresNonBase64Characters() throws Throwable {
        byte[] data = "TW Fu".getBytes("UTF-8");
        byte[] result = new Base64().decode(data);
        assertEquals("Man", new String(result, "UTF-8"));
    }

    // covers decode stopping at first PAD character, ignoring any trailing bytes
    @Test
    public void testDecode_stopsAtPaddingCharacter() throws Throwable {
        byte[] data = "QQ==XYZQ".getBytes("UTF-8");
        byte[] result = new Base64().decode(data);
        assertEquals(1, result.length);
        assertEquals(65, result[0] & 0xff);
    }
}
