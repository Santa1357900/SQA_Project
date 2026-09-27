package org.apache.commons.codec.binary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;
import org.junit.Test;

public class Base64Test {

    @Test
    public void testDefaultConstructor() throws Throwable {
        Base64 base64 = new Base64();
        assertFalse(base64.isUrlSafe());
    }

    @Test
    public void testBooleanConstructor() throws Throwable {
        Base64 urlSafeCodec = new Base64(true);
        assertTrue(urlSafeCodec.isUrlSafe());

        Base64 unsafeCodec = new Base64(false);
        assertFalse(unsafeCodec.isUrlSafe());
    }

    @Test
    public void testIntConstructor() throws Throwable {
        Base64 codec = new Base64(76);
        assertFalse(codec.isUrlSafe());
    }

    @Test
    public void testIntAndByteArrayConstructor() throws Throwable {
        byte[] separator = new byte[] {'\n'};
        Base64 codec = new Base64(76, separator);
        assertFalse(codec.isUrlSafe());
    }

    @Test
    public void testFullConstructorWithNullSeparator() throws Throwable {
        Base64 codec = new Base64(76, null, false);
        assertFalse(codec.isUrlSafe());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidSeparator() throws Throwable {
        byte[] invalidSeparator = new byte[] {'A'};
        new Base64(76, invalidSeparator, false);
    }

    @Test
    public void testIsBase64Byte() throws Throwable {
        assertTrue(Base64.isBase64((byte) 'A'));
        assertTrue(Base64.isBase64((byte) 'z'));
        assertTrue(Base64.isBase64((byte) '0'));
        assertTrue(Base64.isBase64((byte) '+'));
        assertTrue(Base64.isBase64((byte) '/'));
        assertTrue(Base64.isBase64((byte) '='));
        assertFalse(Base64.isBase64((byte) ' '));
        assertFalse(Base64.isBase64((byte) -1));
        assertFalse(Base64.isBase64((byte) 127));
    }

    @Test
    public void testIsArrayByteBase64() throws Throwable {
        byte[] valid = StringUtils.getBytesUtf8("SGVsbG8gV29ybGQ=");
        assertTrue(Base64.isArrayByteBase64(valid));

        byte[] withWhiteSpace = StringUtils.getBytesUtf8("SGVs\n bG8=\r\n");
        assertTrue(Base64.isArrayByteBase64(withWhiteSpace));

        byte[] invalid = new byte[] {'A', 'B', 'C', (byte) 127};
        assertFalse(Base64.isArrayByteBase64(invalid));
    }

    @Test
    public void testEncodeBase64BinaryNullAndEmpty() throws Throwable {
        assertNull(Base64.encodeBase64(null));
        assertArrayEquals(new byte[0], Base64.encodeBase64(new byte[0]));
    }

    @Test
    public void testDecodeBase64BinaryNullAndEmpty() throws Throwable {
        assertNull(Base64.decodeBase64((byte[]) null));
        assertArrayEquals(new byte[0], Base64.decodeBase64(new byte[0]));
    }

    @Test
    public void testDecodeBase64StringNullAndEmpty() throws Throwable {
        assertNull(Base64.decodeBase64((String) null));
        assertArrayEquals(new byte[0], Base64.decodeBase64(""));
    }

    @Test
    public void testEncodeDecodeStandard() throws Throwable {
        byte[] original = StringUtils.getBytesUtf8("Hello, World!");
        byte[] encoded = Base64.encodeBase64(original);
        assertNotNull(encoded);
        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(original, decoded);
    }

    @Test
    public void testEncodeChunked() throws Throwable {
        byte[] original = StringUtils.getBytesUtf8("This is a very long string that should definitely trigger chunking in the base64 encoder to test line lengths and separators properly across multiple blocks.");
        byte[] encoded = Base64.encodeBase64Chunked(original);
        assertNotNull(encoded);
        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(original, decoded);
    }

    @Test
    public void testEncodeURLSafe() throws Throwable {
        byte[] original = StringUtils.getBytesUtf8(">>>Special>>>Chars>>?@#");
        byte[] encoded = Base64.encodeBase64URLSafe(original);
        String encodedStr = Base64.encodeBase64URLSafeString(original);
        assertNotNull(encoded);
        assertNotNull(encodedStr);
        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(original, decoded);
    }

    @Test
    public void testEncodeMaxResultSizeExceeded() throws Throwable {
        byte[] original = StringUtils.getBytesUtf8("Test max result size");
        try {
            Base64.encodeBase64(original, false, false, 2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Input array too big"));
        }
    }

    @Test
    public void testObjectEncode() throws Throwable {
        Base64 base64 = new Base64();
        byte[] original = StringUtils.getBytesUtf8("Object Test");
        Object encoded = base64.encode(original);
        assertTrue(encoded instanceof byte[]);
        byte[] decoded = (byte[]) base64.decode(encoded);
        assertArrayEquals(original, decoded);

        Object encodedStrObj = base64.encodeToString(original);
        assertTrue(encodedStrObj instanceof String);

        try {
            base64.encode("Not A Byte Array");
            fail("Expected EncoderException");
        } catch (EncoderException e) {
            assertTrue(e.getMessage().contains("not a byte[]"));
        }
    }

    @Test
    public void testObjectDecode() throws Throwable {
        Base64 base64 = new Base64();
        byte[] original = StringUtils.getBytesUtf8("Decode Object Test");
        byte[] encoded = Base64.encodeBase64(original);
        
        Object decodedBytes = base64.decode((Object) encoded);
        assertTrue(decodedBytes instanceof byte[]);
        assertArrayEquals(original, (byte[]) decodedBytes);

        String encodedString = StringUtils.newStringUtf8(encoded);
        Object decodedStringObj = base64.decode((Object) encodedString);
        assertTrue(decodedStringObj instanceof byte[]);
        assertArrayEquals(original, (byte[]) decodedStringObj);

        try {
            base64.decode(new Integer(123));
            fail("Expected DecoderException");
        } catch (DecoderException e) {
            assertTrue(e.getMessage().contains("not a byte[] or a String"));
        }
    }

    @Test
    public void testIntegerEncodingDecoding() throws Throwable {
        BigInteger originalInt = new BigInteger("12345678901234567890");
        byte[] encoded = Base64.encodeInteger(originalInt);
        assertNotNull(encoded);
        BigInteger decodedInt = Base64.decodeInteger(encoded);
        assertEquals(originalInt, decodedInt);

        BigInteger alignedInt = new BigInteger("255");
        byte[] encodedAligned = Base64.encodeInteger(alignedInt);
        assertEquals(alignedInt, Base64.decodeInteger(encodedAligned));
    }

    @Test(expected = NullPointerException.class)
    public void testEncodeIntegerNull() throws Throwable {
        Base64.encodeInteger(null);
    }

    @Test
    public void testDiscardWhitespace() throws Throwable {
        byte[] data = StringUtils.getBytesUtf8("SGVs bG8=\nV29ybGQ=\r\t");
        byte[] groomed = Base64.discardWhitespace(data);
        assertNotNull(groomed);
        byte[] decoded = Base64.decodeBase64(groomed);
        assertArrayEquals(StringUtils.getBytesUtf8("Hello\nWorld"), decoded);
    }

    @Test
    public void testStreamingBufferAndModulusCases() throws Throwable {
        Base64 base64 = new Base64(0);
        // Modulus 1 and 2 flush cases via direct encode/decode calls
        byte[] data = new byte[] {1, 2};
        byte[] encoded = base64.encode(data);
        assertNotNull(encoded);
        
        Base64 base64Decode = new Base64(0);
        byte[] decoded = base64Decode.decode(encoded);
        assertArrayEquals(data, decoded);
    }
}