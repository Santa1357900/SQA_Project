package org.apache.commons.codec.binary;

import static org.junit.Assert.*;
import org.junit.Test;
import java.math.BigInteger;
import java.util.Arrays;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;

public class Base64ClaudeTest {

    // Default constructor: not URL-safe
    @Test
    public void testConstructorDefault_isUrlSafeFalse() throws Throwable {
        Base64 b64 = new Base64();
        assertFalse(b64.isUrlSafe());
    }

    // boolean constructor true branch
    @Test
    public void testConstructorUrlSafeTrue_isUrlSafeTrue() throws Throwable {
        Base64 b64 = new Base64(true);
        assertTrue(b64.isUrlSafe());
    }

    // boolean constructor false branch
    @Test
    public void testConstructorUrlSafeFalse_isUrlSafeFalse() throws Throwable {
        Base64 b64 = new Base64(false);
        assertFalse(b64.isUrlSafe());
    }

    // lineLength rounds down to nearest multiple of 4 (10 -> 8), checks separator positions
    @Test
    public void testConstructorLineLength_roundsDownToMultipleOf4() throws Throwable {
        Base64 b64 = new Base64(10);
        byte[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        byte[] out = b64.encode(data);
        assertEquals(16, out.length);
        assertEquals((byte) '\r', out[8]);
        assertEquals((byte) '\n', out[9]);
        assertEquals((byte) '\r', out[14]);
        assertEquals((byte) '\n', out[15]);
    }

    // null lineSeparator disables chunking entirely
    @Test
    public void testConstructorNullLineSeparator_disablesChunking() throws Throwable {
        Base64 b64 = new Base64(76, null);
        byte[] data = new byte[60];
        for (int i = 0; i < 60; i++) data[i] = (byte) i;
        byte[] out = b64.encode(data);
        assertEquals(80, out.length);
        for (int i = 0; i < out.length; i++) {
            assertTrue(out[i] != '\r' && out[i] != '\n');
        }
    }

    // negative lineLength also disables chunking
    @Test
    public void testConstructorNegativeLineLength_disablesChunking() throws Throwable {
        Base64 b64 = new Base64(-10);
        byte[] data = new byte[30];
        for (int i = 0; i < 30; i++) data[i] = (byte) i;
        byte[] out = b64.encode(data);
        for (int i = 0; i < out.length; i++) {
            assertTrue(out[i] != '\r' && out[i] != '\n');
        }
    }

    // lineSeparator containing a base64 character must throw IllegalArgumentException
    @Test
    public void testConstructorInvalidLineSeparator_throwsIllegalArgumentException() throws Throwable {
        try {
            new Base64(76, new byte[]{'A'});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // hasData/avail lifecycle: empty -> filled -> drained
    @Test
    public void testHasDataAvail_streamLifecycle() throws Throwable {
        Base64 b64 = new Base64();
        assertFalse(b64.hasData());
        assertEquals(0, b64.avail());
        byte[] data = {'a', 'b', 'c'};
        b64.encode(data, 0, data.length);
        b64.encode(data, 0, -1);
        assertTrue(b64.hasData());
        assertTrue(b64.avail() > 0);
        byte[] out = new byte[20];
        int n = b64.readResults(out, 0, 20);
        assertFalse(b64.hasData());
        assertEquals(0, b64.avail());
        assertTrue(n > 0);
    }

    // streaming encode(byte[],int,int) known vector "Hello" -> "SGVsbG8="
    @Test
    public void testEncodeStreaming_knownVectorHello() throws Throwable {
        Base64 b64 = new Base64();
        byte[] data = {'H', 'e', 'l', 'l', 'o'};
        b64.encode(data, 0, data.length);
        b64.encode(data, 0, -1);
        byte[] out = new byte[20];
        int n = b64.readResults(out, 0, 20);
        byte[] result = new byte[n];
        System.arraycopy(out, 0, result, 0, n);
        assertEquals("SGVsbG8=", new String(result, "UTF-8"));
    }

    // streaming decode(byte[],int,int) known vector "SGVsbG8=" -> "Hello"
    @Test
    public void testDecodeStreaming_knownVectorHello() throws Throwable {
        Base64 b64 = new Base64();
        byte[] data = {'S', 'G', 'V', 's', 'b', 'G', '8', '='};
        b64.decode(data, 0, data.length);
        b64.decode(data, 0, -1);
        byte[] out = new byte[10];
        int n = b64.readResults(out, 0, 10);
        byte[] result = new byte[n];
        System.arraycopy(out, 0, result, 0, n);
        assertEquals("Hello", new String(result, "UTF-8"));
    }

    // readResults returns -1 when buffer never allocated and eof reached
    @Test
    public void testReadResults_eofWithNoBuffer_returnsNegativeOne() throws Throwable {
        Base64 b64 = new Base64();
        b64.decode(new byte[0], 0, -1);
        int n = b64.readResults(new byte[10], 0, 10);
        assertEquals(-1, n);
    }

    // isBase64(byte): valid chars including PAD, and invalid/negative bytes
    @Test
    public void testIsBase64Byte_validAndInvalid() throws Throwable {
        assertTrue(Base64.isBase64((byte) 'A'));
        assertTrue(Base64.isBase64((byte) '+'));
        assertTrue(Base64.isBase64((byte) '/'));
        assertTrue(Base64.isBase64((byte) '='));
        assertFalse(Base64.isBase64((byte) '!'));
        assertFalse(Base64.isBase64((byte) -1));
    }

    // isBase64(String): empty and whitespace-only treated as valid
    @Test
    public void testIsBase64String_emptyAndWhitespaceValid() throws Throwable {
        assertTrue(Base64.isBase64(""));
        assertTrue(Base64.isBase64(" \n\t"));
        assertTrue(Base64.isBase64("SGVsbG8="));
        assertFalse(Base64.isBase64("abc!"));
    }

    // isBase64(byte[]) invalid character branch
    @Test
    public void testIsBase64ByteArray_invalidCharacter_false() throws Throwable {
        byte[] invalid = {'A', 'B', '!'};
        byte[] valid = {'A', 'B'};
        assertFalse(Base64.isBase64(invalid));
        assertTrue(Base64.isBase64(valid));
    }

    // deprecated isArrayByteBase64 delegates to isBase64(byte[])
    @Test
    public void testIsArrayByteBase64_delegatesCorrectly() throws Throwable {
        byte[] valid = {'A', 'B', 'C', 'D'};
        byte[] invalid = {'!'};
        assertTrue(Base64.isArrayByteBase64(valid));
        assertFalse(Base64.isArrayByteBase64(invalid));
    }

    // encodeBase64 with empty array returns empty (not null)
    @Test
    public void testEncodeBase64_emptyArray_returnsEmpty() throws Throwable {
        byte[] result = Base64.encodeBase64(new byte[0]);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    // encodeBase64 with null returns null
    @Test
    public void testEncodeBase64_nullArray_returnsNull() throws Throwable {
        byte[] result = Base64.encodeBase64((byte[]) null);
        assertNull(result);
    }

    // classic vector "Man" -> "TWFu"
    @Test
    public void testEncodeBase64_knownVectorMan() throws Throwable {
        byte[] data = {'M', 'a', 'n'};
        assertEquals("TWFu", new String(Base64.encodeBase64(data), "UTF-8"));
    }

    // RFC4648 vector "f" -> "Zg==" (modulus==1 padding branch)
    @Test
    public void testEncodeBase64_singleByte_padsWithTwoEquals() throws Throwable {
        byte[] data = {'f'};
        assertEquals("Zg==", new String(Base64.encodeBase64(data), "UTF-8"));
    }

    // RFC4648 vector "fo" -> "Zm8=" (modulus==2 padding branch)
    @Test
    public void testEncodeBase64_twoBytes_padsWithOneEquals() throws Throwable {
        byte[] data = {'f', 'o'};
        assertEquals("Zm8=", new String(Base64.encodeBase64(data), "UTF-8"));
    }

    // RFC4648 vector "foo" -> "Zm9v" (modulus==0, no padding)
    @Test
    public void testEncodeBase64_threeBytes_noPadding() throws Throwable {
        byte[] data = {'f', 'o', 'o'};
        assertEquals("Zm9v", new String(Base64.encodeBase64(data), "UTF-8"));
    }

    // URL-safe encoding swaps +/ for -_ and omits padding
    @Test
    public void testEncodeBase64URLSafe_swapsCharsAndOmitsPadding() throws Throwable {
        byte[] data = {(byte) 0xFF, (byte) 0xEF};
        byte[] out = Base64.encodeBase64URLSafe(data);
        assertEquals("_-8", new String(out, "UTF-8"));
    }

    // encodeBase64Chunked inserts CRLF exactly at 76 chars and at EOF
    @Test
    public void testEncodeBase64Chunked_insertsCRLFAt76AndEof() throws Throwable {
        byte[] data = new byte[58];
        for (int i = 0; i < 58; i++) data[i] = (byte) i;
        byte[] out = Base64.encodeBase64Chunked(data);
        assertEquals(84, out.length);
        assertEquals((byte) '\r', out[76]);
        assertEquals((byte) '\n', out[77]);
        assertEquals((byte) '=', out[80]);
        assertEquals((byte) '=', out[81]);
        assertEquals((byte) '\r', out[82]);
        assertEquals((byte) '\n', out[83]);
    }

    // maxResultSize boundary: exact size must NOT throw (strict > comparison)
    @Test
    public void testEncodeBase64_maxResultSizeExactBoundary_noException() throws Throwable {
        byte[] data = {1, 2, 3};
        byte[] result = Base64.encodeBase64(data, false, false, 6);
        assertNotNull(result);
        assertEquals(4, result.length);
    }



    // decodeBase64(String) known vector "TWFu" -> "Man"
    @Test
    public void testDecodeBase64String_knownVectorMan() throws Throwable {
        byte[] result = Base64.decodeBase64("TWFu");
        assertEquals("Man", new String(result, "UTF-8"));
    }

    // decode ignores embedded CR/LF (non-base64 whitespace bytes)
    @Test
    public void testDecodeBase64_ignoresEmbeddedWhitespace() throws Throwable {
        byte[] result = Base64.decodeBase64("TW\r\nFu");
        assertEquals("Man", new String(result, "UTF-8"));
    }

    // decode stops at the first '=' padding char, ignoring trailing garbage
    @Test
    public void testDecodeBase64_stopsAtFirstPadCharacter() throws Throwable {
        byte[] result = Base64.decodeBase64("Zg==XYZ");
        assertEquals(1, result.length);
        assertEquals((byte) 'f', result[0]);
    }

    // decoding is URL-safe/standard agnostic
    @Test
    public void testDecodeBase64_handlesUrlSafeAlphabetToo() throws Throwable {
        byte[] urlSafeChars = {'_', '-', '8'};
        byte[] decoded = Base64.decodeBase64(urlSafeChars);
        assertEquals(2, decoded.length);
        assertEquals((byte) 0xFF, decoded[0]);
        assertEquals((byte) 0xEF, decoded[1]);
    }

    // decode(Object) with unsupported type throws DecoderException
    @Test
    public void testDecodeObject_wrongType_throwsDecoderException() throws Throwable {
        Base64 b64 = new Base64();
        try {
            b64.decode((Object) new Integer(5));
            fail("expected DecoderException");
        } catch (DecoderException expected) { }
    }

    // decode(Object) with byte[] delegates to decode(byte[])
    @Test
    public void testDecodeObject_byteArrayBranch() throws Throwable {
        Base64 b64 = new Base64();
        byte[] input = {'Z', 'g', '=', '='};
        Object result = b64.decode((Object) input);
        byte[] bytes = (byte[]) result;
        assertEquals(1, bytes.length);
        assertEquals((byte) 'f', bytes[0]);
    }

    // decode(Object) with String delegates to decode(String)
    @Test
    public void testDecodeObject_stringBranch() throws Throwable {
        Base64 b64 = new Base64();
        Object result = b64.decode((Object) "Zg==");
        byte[] bytes = (byte[]) result;
        assertEquals(1, bytes.length);
        assertEquals((byte) 'f', bytes[0]);
    }

    // encode(Object) with unsupported type throws EncoderException
    @Test
    public void testEncodeObject_wrongType_throwsEncoderException() throws Throwable {
        Base64 b64 = new Base64();
        try {
            b64.encode((Object) "not a byte array");
            fail("expected EncoderException");
        } catch (EncoderException expected) { }
    }

    // encode(Object) with byte[] delegates to encode(byte[])
    @Test
    public void testEncodeObject_byteArrayBranch() throws Throwable {
        Base64 b64 = new Base64();
        byte[] input = {'f'};
        Object result = b64.encode((Object) input);
        byte[] bytes = (byte[]) result;
        assertEquals("Zg==", new String(bytes, "UTF-8"));
    }

    // encodeToString known vector "abc" -> "YWJj"
    @Test
    public void testEncodeToString_knownVectorAbc() throws Throwable {
        Base64 b64 = new Base64();
        byte[] data = {'a', 'b', 'c'};
        assertEquals("YWJj", b64.encodeToString(data));
    }

    // encode(byte[]) with null/empty input returns same reference semantics
    @Test
    public void testEncode_nullAndEmptyArray() throws Throwable {
        Base64 b64 = new Base64();
        assertNull(b64.encode((byte[]) null));
        byte[] empty = new byte[0];
        byte[] result = b64.encode(empty);
        assertEquals(0, result.length);
    }

    // encodeInteger/decodeInteger round trip using a hand-verified bit pattern (255 -> "/w==")
    @Test
    public void testEncodeInteger_decodeInteger_roundTrip255() throws Throwable {
        BigInteger value = BigInteger.valueOf(255);
        byte[] encoded = Base64.encodeInteger(value);
        assertEquals("/w==", new String(encoded, "UTF-8"));
        BigInteger decoded = Base64.decodeInteger(encoded);
        assertEquals(value, decoded);
    }

    // encodeInteger(null) must throw NullPointerException per Javadoc
    @Test
    public void testEncodeInteger_null_throwsNullPointerException() throws Throwable {
        try {
            Base64.encodeInteger(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) { }
    }

    // discardWhitespace removes space, \n, \r, \t only
    @Test
    public void testDiscardWhitespace_removesOnlyWhitespaceChars() throws Throwable {
        byte[] input = {' ', 'a', '\n', 'b', '\r', 'c', '\t', 'd'};
        byte[] expected = {'a', 'b', 'c', 'd'};
        byte[] actual = Base64.discardWhitespace(input);
        assertTrue(Arrays.equals(expected, actual));
    }

    // RFC4648 vector "foobar" -> "Zm9vYmFy" (multi-group, no padding, exercises full 3-byte loop repeatedly)
    @Test
    public void testEncodeBase64_sixBytes_knownVectorFoobar() throws Throwable {
        byte[] data = {'f', 'o', 'o', 'b', 'a', 'r'};
        assertEquals("Zm9vYmFy", new String(Base64.encodeBase64(data), "UTF-8"));
    }

    // decode(byte[]) on null/empty input returns the same array unchanged
    @Test
    public void testDecode_nullAndEmptyArray() throws Throwable {
        Base64 b64 = new Base64();
        assertNull(b64.decode((byte[]) null));
        byte[] empty = new byte[0];
        assertEquals(0, b64.decode(empty).length);
    }
}
