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
        Base64 base64UrlSafe = new Base64(true);
        assertTrue(base64UrlSafe.isUrlSafe());

        Base64 base64Standard = new Base64(false);
        assertFalse(base64Standard.isUrlSafe());
    }

    @Test
    public void testIntConstructor() throws Throwable {
        Base64 base64 = new Base64(76);
        assertFalse(base64.isUrlSafe());
    }

    @Test
    public void testIntAndByteArrayConstructor() throws Throwable {
        byte[] separator = new byte[] { '\n' };
        Base64 base64 = new Base64(76, separator);
        assertFalse(base64.isUrlSafe());
    }

    @Test
    public void testFullConstructorWithNullSeparator() throws Throwable {
        Base64 base64 = new Base64(76, null, true);
        assertTrue(base64.isUrlSafe());
    }

    @Test
    public void testFullConstructorWithInvalidSeparator() throws Throwable {
        try {
            byte[] invalidSeparator = new byte[] { 'A' };
            new Base64(76, invalidSeparator, false);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("lineSeperator must not contain base64 characters"));
        }
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

        byte[] withWhiteSpace = StringUtils.getBytesUtf8("SGVs\nbG8g\rV29y\tdGQ=");
        assertTrue(Base64.isArrayByteBase64(withWhiteSpace));

        byte[] invalid = new byte[] { 'S', 'G', 'V', 's', 'b', 'G', '8', 'g', 1 };
        assertFalse(Base64.isArrayByteBase64(invalid));
    }

    @Test
    public void testEncodeDecodeStatic() throws Throwable {
        byte[] data = StringUtils.getBytesUtf8("Hello, World!");
        byte[] encoded = Base64.encodeBase64(data);
        assertNotNull(encoded);

        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(data, decoded);
    }

    @Test
    public void testEncodeBase64NullAndEmpty() throws Throwable {
        assertNull(Base64.encodeBase64(null));
        assertArrayEquals(new byte[0], Base64.encodeBase64(new byte[0]));

        assertNull(Base64.decodeBase64((byte[]) null));
        assertArrayEquals(new byte[0], Base64.decodeBase64(new byte[0]));

        assertNull(Base64.encodeBase64String(null));
        assertNull(Base64.decodeBase64((String) null));
    }

    @Test
    public void testEncodeBase64String() throws Throwable {
        byte[] data = StringUtils.getBytesUtf8("abc");
        String encodedStr = Base64.encodeBase64String(data);
        assertEquals("YWJj", encodedStr);
    }

    @Test
    public void testEncodeBase64URLSafe() throws Throwable {
        byte[] data = new byte[] { (byte) 255, (byte) 254, (byte) 253 };
        byte[] encoded = Base64.encodeBase64URLSafe(data);
        assertNotNull(encoded);
        
        String encodedStr = Base64.encodeBase64URLSafeString(data);
        assertNotNull(encodedStr);
    }

    @Test
    public void testEncodeBase64Chunked() throws Throwable {
        byte[] data = new byte[200];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        byte[] encoded = Base64.encodeBase64Chunked(data);
        assertNotNull(encoded);
    }

    @Test
    public void testEncodeBase64WithMaxResultSize() throws Throwable {
        byte[] data = StringUtils.getBytesUtf8("Hello World");
        try {
            Base64.encodeBase64(data, false, false, 2);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Input array too big"));
        }
    }

    @Test
    public void testObjectEncode() throws Throwable {
        Base64 base64 = new Base64();
        byte[] data = StringUtils.getBytesUtf8("test");
        Object encodedObj = base64.encode((Object) data);
        assertTrue(encodedObj instanceof byte[]);

        try {
            base64.encode((Object) "NotAByteArray");
            fail("Expected EncoderException");
        } catch (EncoderException e) {
            assertTrue(e.getMessage().contains("Parameter supplied to Base64 encode is not a byte[]"));
        }
    }

    @Test
    public void testObjectDecode() throws Throwable {
        Base64 base64 = new Base64();
        byte[] data = StringUtils.getBytesUtf8("dGVzdA==");
        Object decodedObj = base64.decode((Object) data);
        assertTrue(decodedObj instanceof byte[]);

        Object decodedStrObj = base64.decode((Object) "dGVzdA==");
        assertTrue(decodedStrObj instanceof byte[]);

        try {
            base64.decode((Object) Integer.valueOf(123));
            fail("Expected DecoderException");
        } catch (DecoderException e) {
            assertTrue(e.getMessage().contains("Parameter supplied to Base64 decode is not a byte[] or a String"));
        }
    }

    @Test
    public void testDecodeWithPaddingVariants() throws Throwable {
        // Test optional padding / missing padding scenarios
        byte[] decoded1 = Base64.decodeBase64("YWJj");
        assertArrayEquals(StringUtils.getBytesUtf8("abc"), decoded1);

        byte[] decoded2 = Base64.decodeBase64("YWJjD==");
        assertNotNull(decoded2);
        
        byte[] decoded3 = Base64.decodeBase64("YW");
        assertNotNull(decoded3);

        byte[] decoded4 = Base64.decodeBase64("YWI");
        assertNotNull(decoded4);
    }

    @Test
    public void testIntegerCoding() throws Throwable {
        BigInteger bigInt = new BigInteger("12345678901234567890");
        byte[] encodedInt = Base64.encodeInteger(bigInt);
        assertNotNull(encodedInt);

        BigInteger decodedInt = Base64.decodeInteger(encodedInt);
        assertEquals(bigInt, decodedInt);

        try {
            Base64.encodeInteger(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("encodeInteger called with null parameter"));
        }
    }

    @Test
    public void testDiscardWhitespace() throws Throwable {
        byte[] data = StringUtils.getBytesUtf8("  A B \n C \r\t");
        byte[] groomed = Base64.discardWhitespace(data);
        assertNotNull(groomed);
    }

    @Test
    public void testStreamingEncodeEOFModulusCases() throws Throwable {
        Base64 base64Std = new Base64(0, null, false);
        byte[] data1 = new byte[] { 1 };
        base64Std.encode(data1, 0, data1.length);
        base64Std.encode(data1, 0, -1);
        assertTrue(base64Std.hasData());

        Base64 base64Url = new Base64(0, null, true);
        byte[] data2 = new byte[] { 1, 2 };
        base64Url.encode(data2, 0, data2.length);
        base64Url.encode(data2, 0, -1);
        assertTrue(base64Url.hasData());
    }

    @Test
    public void testBufferResizeAndReadResults() throws Throwable {
        Base64 base64 = new Base64();
        // Force reading when no buffer exists
        byte[] dest = new byte[10];
        int res = base64.readResults(dest, 0, 10);
        assertEquals(0, res);

        // Force set initial buffer
        base64.setInitialBuffer(dest, 0, 10);
        assertEquals(0, base64.avail());
    }
}