package org.apache.commons.codec.binary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;

import org.junit.Test;

public class Base64Test {

    @Test
    public void testDefaultConstructor() throws Throwable {
        Base64 base64 = new Base64();
        assertFalse(base64.isUrlSafe());
        assertFalse(base64.hasData());
        assertEquals(0, base64.avail());
    }

    @Test
    public void testBooleanConstructor() throws Throwable {
        Base64 urlSafeCodec = new Base64(true);
        assertTrue(urlSafeCodec.isUrlSafe());

        Base64 standardCodec = new Base64(false);
        assertFalse(standardCodec.isUrlSafe());
    }

    @Test
    public void testLineLengthConstructor() throws Throwable {
        Base64 base64 = new Base64(76);
        assertFalse(base64.isUrlSafe());
    }

    @Test
    public void testLineLengthAndSeparatorConstructor() throws Throwable {
        byte[] separator = new byte[] { '\n' };
        Base64 base64 = new Base64(76, separator);
        assertFalse(base64.isUrlSafe());
    }

    @Test
    public void testFullConstructor() throws Throwable {
        byte[] separator = new byte[] { '\r', '\n' };
        Base64 base64 = new Base64(76, separator, true);
        assertTrue(base64.isUrlSafe());
    }

    @Test
    public void testConstructorWithNullSeparator() throws Throwable {
        Base64 base64 = new Base64(76, null, false);
        assertFalse(base64.isUrlSafe());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidSeparator() throws Throwable {
        byte[] separator = new byte[] { 'A' }; // 'A' is a base64 char
        new Base64(76, separator, false);
    }

    @Test
    public void testIsBase64() throws Throwable {
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

        byte[] withWhiteSpace = StringUtils.getBytesUtf8("SGVs\nbG8=\r\n");
        assertTrue(Base64.isArrayByteBase64(withWhiteSpace));

        byte[] invalid = new byte[] { (byte) '%' };
        assertFalse(Base64.isArrayByteBase64(invalid));
    }

    @Test
    public void testEncodeBase64Binary() throws Throwable {
        byte[] input = StringUtils.getBytesUtf8("Hello World");
        byte[] encoded = Base64.encodeBase64(input);
        assertNotNull(encoded);
        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(input, decoded);
    }

    @Test
    public void testEncodeBase64NullAndEmpty() throws Throwable {
        assertNull(Base64.encodeBase64(null));
        assertArrayEquals(new byte[0], Base64.encodeBase64(new byte[0]));
    }

    @Test
    public void testEncodeBase64String() throws Throwable {
        byte[] input = StringUtils.getBytesUtf8("Hello World");
        String encodedStr = Base64.encodeBase64String(input);
        assertEquals("SGVsbG8gV29ybGQ=", encodedStr);
    }

    @Test
    public void testEncodeBase64URLSafe() throws Throwable {
        byte[] input = new byte[] { (byte) 251, (byte) 255, (byte) 255 }; // characters that produce + and / in standard
        byte[] encoded = Base64.encodeBase64URLSafe(input);
        assertNotNull(encoded);
        for (int i = 0; i < encoded.length; i++) {
            assertFalse(encoded[i] == '+');
            assertFalse(encoded[i] == '/');
        }
    }

    @Test
    public void testEncodeBase64URLSafeString() throws Throwable {
        byte[] input = new byte[] { (byte) 251, (byte) 255, (byte) 255 };
        String encodedStr = Base64.encodeBase64URLSafeString(input);
        assertNotNull(encodedStr);
        assertFalse(encodedStr.contains("+"));
        assertFalse(encodedStr.contains("/"));
    }

    @Test
    public void testEncodeBase64Chunked() throws Throwable {
        byte[] input = new byte[200];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }
        byte[] encoded = Base64.encodeBase64Chunked(input);
        assertNotNull(encoded);
        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(input, decoded);
    }

    @Test
    public void testEncodeBase64WithMaxResultSize() throws Throwable {
        byte[] input = StringUtils.getBytesUtf8("Hello World");
        byte[] encoded = Base64.encodeBase64(input, false, false, 100);
        assertNotNull(encoded);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEncodeBase64ExceedsMaxResultSize() throws Throwable {
        byte[] input = StringUtils.getBytesUtf8("Hello World");
        Base64.encodeBase64(input, false, false, 2);
    }

    @Test
    public void testDecodeByteAndStringArray() throws Throwable {
        String original = "Hello World";
        byte[] inputBytes = StringUtils.getBytesUtf8(original);
        byte[] encoded = Base64.encodeBase64(inputBytes);

        byte[] decodedBytes = Base64.decodeBase64(encoded);
        assertArrayEquals(inputBytes, decodedBytes);

        String encodedStr = StringUtils.newStringUtf8(encoded);
        byte[] decodedStrBytes = Base64.decodeBase64(encodedStr);
        assertArrayEquals(inputBytes, decodedStrBytes);
    }

    @Test
    public void testDecodeNullAndEmpty() throws Throwable {
        assertNull(Base64.decodeBase64((byte[]) null));
        assertArrayEquals(new byte[0], Base64.decodeBase64(new byte[0]));
        assertNull(Base64.decodeBase64((String) null));
    }

    @Test
    public void testObjectEncode() throws Throwable {
        Base64 base64 = new Base64();
        byte[] input = StringUtils.getBytesUtf8("Test");
        Object encodedObj = base64.encode((Object) input);
        assertTrue(encodedObj instanceof byte[]);

        try {
            base64.encode(new Object());
            fail("Expected EncoderException");
        } catch (EncoderException e) {
            assertTrue(e.getMessage().contains("not a byte[]"));
        }
    }

    @Test
    public void testObjectDecode() throws Throwable {
        Base64 base64 = new Base64();
        byte[] input = StringUtils.getBytesUtf8("Test");
        byte[] encoded = Base64.encodeBase64(input);

        Object decodedObj1 = base64.decode((Object) encoded);
        assertTrue(decodedObj1 instanceof byte[]);

        String encodedStr = StringUtils.newStringUtf8(encoded);
        Object decodedObj2 = base64.decode((Object) encodedStr);
        assertTrue(decodedObj2 instanceof byte[]);

        try {
            base64.decode(new Object());
            fail("Expected DecoderException");
        } catch (DecoderException e) {
            assertTrue(e.getMessage().contains("not a byte[] or a String"));
        }
    }

    @Test
    public void testEncodeToString() throws Throwable {
        Base64 base64 = new Base64();
        byte[] input = StringUtils.getBytesUtf8("Test");
        String result = base64.encodeToString(input);
        assertEquals("VGVzdA==", result);
    }

    @Test
    public void testDiscardWhitespace() throws Throwable {
        byte[] data = StringUtils.getBytesUtf8("  SGVs\nbG8= \r\t");
        byte[] groomed = Base64.discardWhitespace(data);
        assertNotNull(groomed);
        assertEquals("SGVsbG8=", StringUtils.newStringUtf8(groomed));
    }

    @Test
    public void testIntegerEncodingDecoding() throws Throwable {
        BigInteger original = new BigInteger("12345678901234567890");
        byte[] encoded = Base64.encodeInteger(original);
        BigInteger decoded = Base64.decodeInteger(encoded);
        assertEquals(original, decoded);
    }

    @Test(expected = NullPointerException.class)
    public void testEncodeIntegerNull() throws Throwable {
        Base64.encodeInteger(null);
    }

    @Test
    public void testToIntegerBytes() throws Throwable {
        BigInteger bigInt = BigInteger.valueOf(255);
        byte[] bytes = Base64.toIntegerBytes(bigInt);
        assertNotNull(bytes);

        BigInteger bigIntEven = BigInteger.valueOf(256);
        byte[] bytesEven = Base64.toIntegerBytes(bigIntEven);
        assertNotNull(bytesEven);
    }

    @Test
    public void testDecodeWithPaddingVariants() throws Throwable {
        // Test different modulos during decoding (EOF handling)
        byte[] dec1 = Base64.decodeBase64("V"); // mod 1
        assertNotNull(dec1);
        byte[] dec2 = Base64.decodeBase64("VE"); // mod 2
        assertNotNull(dec2);
        byte[] dec3 = Base64.decodeBase64("VGV"); // mod 3
        assertNotNull(dec3);
    }

    @Test
    public void testEncodeModulusCases() throws Throwable {
        // Input length 1 (mod 1)
        byte[] in1 = new byte[] { 0x01 };
        byte[] out1 = Base64.encodeBase64(in1);
        assertNotNull(out1);

        // Input length 2 (mod 2)
        byte[] in2 = new byte[] { 0x01, 0x02 };
        byte[] out2 = Base64.encodeBase64(in2);
        assertNotNull(out2);
    }

    @Test
    public void testCodecWithNegativeBytes() throws Throwable {
        byte[] input = new byte[] { (byte) -128, (byte) -1, (byte) 127 };
        byte[] encoded = Base64.encodeBase64(input);
        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(input, decoded);
    }
}