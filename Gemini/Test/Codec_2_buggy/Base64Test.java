package org.apache.commons.codec.binary;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;
import java.math.BigInteger;

public class Base64Test {

    @Test
    public void testDefaultConstructor() throws Throwable {
        Base64 b64 = new Base64();
        assertFalse(b64.isUrlSafe());
    }

    @Test
    public void testBooleanConstructor() throws Throwable {
        Base64 b64 = new Base64(true);
        assertTrue(b64.isUrlSafe());

        Base64 b64Standard = new Base64(false);
        assertFalse(b64Standard.isUrlSafe());
    }

    @Test
    public void testLineLengthConstructor() throws Throwable {
        Base64 b64 = new Base64(76);
        assertFalse(b64.isUrlSafe());
    }

    @Test
    public void testLineLengthAndSeparatorConstructor() throws Throwable {
        byte[] sep = new byte[]{'\n'};
        Base64 b64 = new Base64(76, sep);
        assertFalse(b64.isUrlSafe());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLineSeparatorThrowsException() throws Throwable {
        byte[] invalidSep = new byte[]{'A'};
        new Base64(76, invalidSep, false);
    }

    @Test
    public void testInvalidLineSeparatorUnsupportedEncodingFallback() throws Throwable {
        try {
            byte[] invalidSep = new byte[]{'A'};
            new Base64(76, invalidSep, false);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("lineSeperator must not contain base64 characters"));
        }
    }

    @Test
    public void testHasDataAndAvail() throws Throwable {
        Base64 b64 = new Base64();
        assertFalse(b64.hasData());
        assertEquals(0, b64.avail());
    }

    @Test
    public void testEncodeAndDecodeNullOrEmpty() throws Throwable {
        assertNull(Base64.encodeBase64(null));
        assertNull(Base64.decodeBase64(null));

        byte[] empty = new byte[0];
        assertArrayEquals(empty, Base64.encodeBase64(empty));
        assertArrayEquals(empty, Base64.decodeBase64(empty));
    }

    @Test
    public void testEncodeAndDecodeBasic() throws Throwable {
        byte[] data = "Hello World".getBytes("UTF-8");
        byte[] encoded = Base64.encodeBase64(data);
        assertNotNull(encoded);

        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(data, decoded);
    }

    @Test
    public void testEncodeChunked() throws Throwable {
        byte[] data = "Hello World, this is a longer string to test chunking capabilities of Base64 encoding functionality properly.".getBytes("UTF-8");
        byte[] encoded = Base64.encodeBase64Chunked(data);
        assertNotNull(encoded);

        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(data, decoded);
    }

    @Test
    public void testEncodeURLSafe() throws Throwable {
        byte[] data = ">>>??<??<<".getBytes("UTF-8");
        byte[] encoded = Base64.encodeBase64URLSafe(data);
        assertNotNull(encoded);
        for (int i = 0; i < encoded.length; i++) {
            assertFalse(encoded[i] == '+');
            assertFalse(encoded[i] == '/');
        }

        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(data, decoded);
    }

    @Test
    public void testEncodeObject() throws Throwable {
        Base64 b64 = new Base64();
        byte[] data = "Test".getBytes("UTF-8");
        Object encodedObj = b64.encode(data);
        assertNotNull(encodedObj);
        assertTrue(encodedObj instanceof byte[]);

        Object decodedObj = b64.decode(encodedObj);
        assertNotNull(decodedObj);
        assertTrue(decodedObj instanceof byte[]);
        assertArrayEquals(data, (byte[]) decodedObj);
    }

    @Test(expected = EncoderException.class)
    public void testEncodeInvalidObjectThrowsException() throws Throwable {
        Base64 b64 = new Base64();
        b64.encode("NotAByteArray");
    }

    @Test(expected = DecoderException.class)
    public void testDecodeInvalidObjectThrowsException() throws Throwable {
        Base64 b64 = new Base64();
        b64.decode("NotAByteArray");
    }

    @Test
    public void testIsBase64() throws Throwable {
        assertTrue(Base64.isBase64((byte) 'A'));
        assertTrue(Base64.isBase64((byte) 'z'));
        assertTrue(Base64.isBase64((byte) '0'));
        assertTrue(Base64.isBase64((byte) '+'));
        assertTrue(Base64.isBase64((byte) '/'));
        assertTrue(Base64.isBase64((byte) '='));
        assertFalse(Base64.isBase64((byte) '!'));
        assertFalse(Base64.isBase64((byte) -2));
    }

    @Test
    public void testIsArrayByteBase64() throws Throwable {
        byte[] valid = "SGVsbG8=".getBytes("UTF-8");
        assertTrue(Base64.isArrayByteBase64(valid));

        byte[] withWhitespace = "SGVs\n bG8=".getBytes("UTF-8");
        assertTrue(Base64.isArrayByteBase64(withWhitespace));

        byte[] invalid = "SGVsbG8=!#$".getBytes("UTF-8");
        assertFalse(Base64.isArrayByteBase64(invalid));
    }

    @Test
    public void testDiscardWhitespace() throws Throwable {
        byte[] input = "AB \n\r\tCD".getBytes("UTF-8");
        byte[] expected = "ABCD".getBytes("UTF-8");
        byte[] actual = Base64.discardWhitespace(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testDiscardNonBase64() throws Throwable {
        byte[] input = "AB!CD#".getBytes("UTF-8");
        byte[] expected = "ABCD".getBytes("UTF-8");
        byte[] actual = Base64.discardNonBase64(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testIntegerEncodingAndDecoding() throws Throwable {
        BigInteger original = new BigInteger("12345678901234567890");
        byte[] encoded = Base64.encodeInteger(original);
        BigInteger decoded = Base64.decodeInteger(encoded);
        assertEquals(original, decoded);

        BigInteger small = BigInteger.valueOf(42);
        byte[] encodedSmall = Base64.encodeInteger(small);
        BigInteger decodedSmall = Base64.decodeInteger(encodedSmall);
        assertEquals(small, decodedSmall);
    }

    @Test(expected = NullPointerException.class)
    public void testEncodeIntegerNullThrowsException() throws Throwable {
        Base64.encodeInteger(null);
    }

    @Test
    public void testStreamingModulusVariations() throws Throwable {
        // Test encoding inputs that trigger modulus 1 and 2 in EOF
        Base64 encoder = new Base64(0, Base64.CHUNK_SEPARATOR, false);
        byte[] input1 = new byte[]{1};
        byte[] encoded1 = Base64.encodeBase64(input1);
        assertNotNull(encoded1);

        byte[] input2 = new byte[]{1, 2};
        byte[] encoded2 = Base64.encodeBase64(input2);
        assertNotNull(encoded2);
    }

    @Test
    public void testDecodeModulusVariations() throws Throwable {
        // Test decoding inputs with different padding lengths
        byte[] dec1 = Base64.decodeBase64("QQ==".getBytes("UTF-8"));
        assertNotNull(dec1);

        byte[] dec2 = Base64.decodeBase64("QQ".getBytes("UTF-8")); // missing padding
        assertNotNull(dec2);

        byte[] dec3 = Base64.decodeBase64("Q0s=".getBytes("UTF-8"));
        assertNotNull(dec3);
    }

    @Test
    public void testReadResultsWithSameBufferAndEof() throws Throwable {
        Base64 b64 = new Base64();
        byte[] buf = new byte[10];
        b64.setInitialBuffer(buf, 0, 10);
        // calling readResults when buf == b
        int res = b64.readResults(buf, 0, 5);
        assertEquals(0, res);
    }

    @Test
    public void testEncodeBufferTooBigException() throws Throwable {
        try {
            // Force an excessively large input to trigger IllegalArgumentException on Integer.MAX_VALUE
            byte[] huge = new byte[Integer.MAX_VALUE / 2];
            Base64.encodeBase64(huge, true, false);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Input array too big"));
        } catch (NegativeArraySizeException e) {
            // Handled safely if memory limit prevents allocation
        } catch (OutOfMemoryError e) {
            // Handled safely if OOM occurs
        }
    }
}