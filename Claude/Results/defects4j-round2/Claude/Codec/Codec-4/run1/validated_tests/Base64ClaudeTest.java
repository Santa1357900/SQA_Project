package org.apache.commons.codec.binary;

import static org.junit.Assert.*;

import java.math.BigInteger;

import org.junit.Test;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;

public class Base64ClaudeTest {

    // Default ctor javadoc: "line length is 0 (no chunking)" - must NOT insert CRLF for long input
    @Test
    public void testBase64Constructor_noArgLongInput_shouldNotChunkPerJavadoc() throws Throwable {
        byte[] data = new byte[60];
        Base64 b64 = new Base64();
        byte[] encoded = b64.encode(data);
        String s = new String(encoded, "UTF-8");
        assertFalse(s.contains("\r\n"));
    }

    // Base64(boolean) javadoc: line length 76, CRLF separator -> first break at index 76
    @Test
    public void testBase64BooleanConstructor_falseLongInput_chunksAt76() throws Throwable {
        byte[] data = new byte[60];
        Base64 b64 = new Base64(false);
        byte[] encoded = b64.encode(data);
        String s = new String(encoded, "UTF-8");
        assertEquals(76, s.indexOf("\r\n"));
    }

    // Base64(int lineLength) rounds down to nearest multiple of 4: 10 -> 8
    @Test
    public void testBase64IntConstructor_lineLengthRoundsDownToMultipleOf4() throws Throwable {
        byte[] data = new byte[9];
        Base64 b64 = new Base64(10);
        byte[] encoded = b64.encode(data);
        String s = new String(encoded, "UTF-8");
        assertEquals(8, s.indexOf("\r\n"));
    }

    // null lineSeparator forces lineLength=0 (no chunking) per constructor branch
    @Test
    public void testBase64IntByteArrayConstructor_nullSeparator_disablesChunking() throws Throwable {
        byte[] data = new byte[60];
        Base64 b64 = new Base64(76, null);
        byte[] encoded = b64.encode(data);
        String s = new String(encoded, "UTF-8");
        assertFalse(s.contains("\r\n"));
    }

    // lineSeparator containing a base64 alphabet char must throw IllegalArgumentException
    @Test
    public void testBase64IntByteArrayBooleanConstructor_separatorContainsBase64Char_throwsIllegalArgumentException() throws Throwable {
        byte[] sep = new byte[] { 'A' };
        try {
            new Base64(76, sep);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // isUrlSafe() returns false for STANDARD_ENCODE_TABLE (default)
    @Test
    public void testIsUrlSafe_defaultConstructor_false() throws Throwable {
        assertFalse(new Base64().isUrlSafe());
    }

    // isUrlSafe() returns true when constructed with urlSafe=true
    @Test
    public void testIsUrlSafe_urlSafeTrueConstructor_true() throws Throwable {
        assertTrue(new Base64(true).isUrlSafe());
    }

    // hasData() false before any encode/decode call (buffer == null)
    @Test
    public void testHasData_newInstance_false() throws Throwable {
        assertFalse(new Base64().hasData());
    }

    // avail() returns 0 when buffer is null
    @Test
    public void testAvail_newInstance_zero() throws Throwable {
        assertEquals(0, new Base64().avail());
    }

    // isBase64: 'A' valid, '=' (PAD) valid, '!' invalid
    @Test
    public void testIsBase64_variousOctets_correctClassification() throws Throwable {
        assertTrue(Base64.isBase64((byte) 'A'));
        assertTrue(Base64.isBase64((byte) '='));
        assertFalse(Base64.isBase64((byte) '!'));
    }

    // isArrayByteBase64: empty array treated as true per javadoc
    @Test
    public void testIsArrayByteBase64_emptyArray_true() throws Throwable {
        assertTrue(Base64.isArrayByteBase64(new byte[0]));
    }

    // isArrayByteBase64: whitespace mixed with valid chars is still true
    @Test
    public void testIsArrayByteBase64_validWithWhitespace_true() throws Throwable {
        byte[] arr = "TWFu \r\n".getBytes("UTF-8");
        assertTrue(Base64.isArrayByteBase64(arr));
    }

    // isArrayByteBase64: one invalid, non-whitespace char yields false
    @Test
    public void testIsArrayByteBase64_invalidChar_false() throws Throwable {
        byte[] arr = "TWF!".getBytes("UTF-8");
        assertFalse(Base64.isArrayByteBase64(arr));
    }

    // encodeBase64(null) returns null (binaryData null branch)
    @Test
    public void testEncodeBase64_nullInput_returnsNull() throws Throwable {
        assertNull(Base64.encodeBase64(null));
    }

    // encodeBase64(empty) returns the same empty array (length==0 branch)
    @Test
    public void testEncodeBase64_emptyInput_returnsEmptyArray() throws Throwable {
        byte[] r = Base64.encodeBase64(new byte[0]);
        assertEquals(0, r.length);
    }

    // modulus==0 case: classic RFC vector "Man" -> "TWFu"
    @Test
    public void testEncodeBase64_threeByteInput_knownVector() throws Throwable {
        byte[] r = Base64.encodeBase64("Man".getBytes("UTF-8"));
        assertEquals("TWFu", new String(r, "UTF-8"));
    }

    // modulus==1 at EOF (standard table): one byte input pads with "=="
    @Test
    public void testEncodeBase64_oneByteInput_standardPadding() throws Throwable {
        byte[] r = Base64.encodeBase64("M".getBytes("UTF-8"));
        assertEquals("TQ==", new String(r, "UTF-8"));
    }

    // modulus==2 at EOF (standard table): two byte input pads with "="
    @Test
    public void testEncodeBase64_twoByteInput_standardPadding() throws Throwable {
        byte[] r = Base64.encodeBase64("Ma".getBytes("UTF-8"));
        assertEquals("TWE=", new String(r, "UTF-8"));
    }

    // URL-safe table skips padding for modulus==1 case
    @Test
    public void testEncodeBase64URLSafe_oneByteInput_noPadding() throws Throwable {
        byte[] r = Base64.encodeBase64URLSafe("M".getBytes("UTF-8"));
        assertEquals("TQ", new String(r, "UTF-8"));
    }

    // URL-safe table skips padding for modulus==2 case, returned as String
    @Test
    public void testEncodeBase64URLSafeString_twoByteInput_noPadding() throws Throwable {
        String s = Base64.encodeBase64URLSafeString("Ma".getBytes("UTF-8"));
        assertEquals("TWE", s);
    }



    // encodeBase64Chunked forces chunking -> long input contains CRLF
    @Test
    public void testEncodeBase64Chunked_sixtyByteInput_containsCRLF() throws Throwable {
        byte[] data = new byte[60];
        byte[] r = Base64.encodeBase64Chunked(data);
        String s = new String(r, "UTF-8");
        assertTrue(s.contains("\r\n"));
    }

    // maxResultSize smaller than required output must throw IllegalArgumentException
    @Test
    public void testEncodeBase64_maxResultSizeExceeded_throwsIllegalArgumentException() throws Throwable {
        byte[] data = new byte[100];
        try {
            Base64.encodeBase64(data, false, false, 10);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // maxResultSize sufficient: no exception, correct encoded output returned
    @Test
    public void testEncodeBase64_maxResultSizeSufficient_returnsEncoded() throws Throwable {
        byte[] data = "Man".getBytes("UTF-8");
        byte[] r = Base64.encodeBase64(data, false, false, 100);
        assertEquals("TWFu", new String(r, "UTF-8"));
    }

    // decode(Object) with byte[] argument delegates to decode(byte[])
    @Test
    public void testDecodeObject_byteArrayInput_returnsDecodedBytes() throws Throwable {
        Object result = new Base64().decode((Object) "TWFu".getBytes("UTF-8"));
        assertArrayEquals("Man".getBytes("UTF-8"), (byte[]) result);
    }

    // decode(Object) with String argument delegates to decode(String)
    @Test
    public void testDecodeObject_stringInput_returnsDecodedBytes() throws Throwable {
        Object result = new Base64().decode((Object) "TWFu");
        assertArrayEquals("Man".getBytes("UTF-8"), (byte[]) result);
    }

    // decode(Object) with unsupported type must throw DecoderException
    @Test
    public void testDecodeObject_invalidType_throwsDecoderException() throws Throwable {
        try {
            new Base64().decode((Object) Integer.valueOf(5));
            fail("expected DecoderException");
        } catch (DecoderException expected) {
        }
    }

    // decode(String) known vector roundtrip
    @Test
    public void testDecodeString_knownVector_returnsOriginalBytes() throws Throwable {
        byte[] r = new Base64().decode("TWFu");
        assertArrayEquals("Man".getBytes("UTF-8"), r);
    }

    // decode(byte[] null) returns null (pArray null branch)
    @Test
    public void testDecodeByteArray_nullInput_returnsNull() throws Throwable {
        assertNull(new Base64().decode((byte[]) null));
    }

    // decode(byte[] empty) returns the same empty array
    @Test
    public void testDecodeByteArray_emptyInput_returnsEmptyArray() throws Throwable {
        byte[] r = new Base64().decode(new byte[0]);
        assertEquals(0, r.length);
    }

    // decode EOF modulus==2 branch (no padding present, 2 leftover chars)
    @Test
    public void testDecodeBase64_noPaddingModulus2_decodesCorrectly() throws Throwable {
        byte[] r = Base64.decodeBase64("TQ");
        assertArrayEquals("M".getBytes("UTF-8"), r);
    }

    // decode EOF modulus==3 branch (no padding present, 3 leftover chars)
    @Test
    public void testDecodeBase64_noPaddingModulus3_decodesCorrectly() throws Throwable {
        byte[] r = Base64.decodeBase64("TWE");
        assertArrayEquals("Ma".getBytes("UTF-8"), r);
    }

    // decode handles explicit '=' padding correctly
    @Test
    public void testDecodeBase64_withPadding_decodesCorrectly() throws Throwable {
        byte[] r = Base64.decodeBase64("TQ==");
        assertArrayEquals("M".getBytes("UTF-8"), r);
    }

    // first '=' sets eof and stops processing remaining bytes (break branch)
    @Test
    public void testDecodeBase64_paddingStopsFurtherProcessing() throws Throwable {
        byte[] r = Base64.decodeBase64("TQ==AAAA");
        assertArrayEquals("M".getBytes("UTF-8"), r);
    }

    // non-base64 whitespace bytes (CR/LF) are silently ignored while decoding
    @Test
    public void testDecodeBase64_whitespaceIgnored_decodesCorrectly() throws Throwable {
        byte[] r = Base64.decodeBase64("TW\r\nFu");
        assertArrayEquals("Man".getBytes("UTF-8"), r);
    }

    // discardWhitespace removes space/tab/CR/LF, keeps other bytes in order
    @Test
    public void testDiscardWhitespace_mixedWhitespace_removesAll() throws Throwable {
        byte[] input = " A\tB\r\nC ".getBytes("UTF-8");
        byte[] r = Base64.discardWhitespace(input);
        assertArrayEquals(new byte[] { 'A', 'B', 'C' }, r);
    }

    // encode(Object) with byte[] argument delegates to encode(byte[])
    @Test
    public void testEncodeObject_byteArrayInput_returnsEncodedBytes() throws Throwable {
        Object result = new Base64().encode((Object) "Man".getBytes("UTF-8"));
        assertArrayEquals("TWFu".getBytes("UTF-8"), (byte[]) result);
    }

    // encode(Object) with unsupported type must throw EncoderException
    @Test
    public void testEncodeObject_invalidType_throwsEncoderException() throws Throwable {
        try {
            new Base64().encode((Object) Integer.valueOf(5));
            fail("expected EncoderException");
        } catch (EncoderException expected) {
        }
    }

    // encodeToString returns String form of encode(byte[])
    @Test
    public void testEncodeToString_threeByteInput_knownVector() throws Throwable {
        String s = new Base64().encodeToString("Man".getBytes("UTF-8"));
        assertEquals("TWFu", s);
    }

    // encode(byte[] null) returns null (pArray null branch)
    @Test
    public void testEncodeByteArray_nullInput_returnsNull() throws Throwable {
        assertNull(new Base64().encode((byte[]) null));
    }

    // encode(byte[] empty) returns the same empty array
    @Test
    public void testEncodeByteArray_emptyInput_returnsEmptyArray() throws Throwable {
        byte[] r = new Base64().encode(new byte[0]);
        assertEquals(0, r.length);
    }

    // crypto round trip: encodeInteger then decodeInteger returns original value
    @Test
    public void testDecodeInteger_roundTripWithEncodeInteger_returnsOriginalValue() throws Throwable {
        BigInteger original = BigInteger.valueOf(123456789L);
        byte[] encoded = Base64.encodeInteger(original);
        BigInteger decoded = Base64.decodeInteger(encoded);
        assertEquals(original, decoded);
    }

    // encodeInteger(null) must throw NullPointerException per javadoc
    @Test
    public void testEncodeInteger_nullInput_throwsNullPointerException() throws Throwable {
        try {
            Base64.encodeInteger(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // toIntegerBytes: byte-aligned bitLength strips the extra sign byte
    @Test
    public void testToIntegerBytes_byteAlignedValue_stripsSignByte() throws Throwable {
        byte[] r = Base64.toIntegerBytes(BigInteger.valueOf(255));
        assertArrayEquals(new byte[] { (byte) 255 }, r);
    }

    // toIntegerBytes: non-byte-aligned bitLength returns minimal representation as-is
    @Test
    public void testToIntegerBytes_nonByteAlignedValue_returnsMinimalBytes() throws Throwable {
        byte[] r = Base64.toIntegerBytes(BigInteger.valueOf(100));
        assertArrayEquals(new byte[] { 100 }, r);
    }
}
