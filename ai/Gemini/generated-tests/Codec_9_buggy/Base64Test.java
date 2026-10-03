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
        Base64 base64Safe = new Base64(true);
        assertTrue(base64Safe.isUrlSafe());

        Base64 base64Unsafe = new Base64(false);
        assertFalse(base64Unsafe.isUrlSafe());
    }

    @Test
    public void testIntConstructor() throws Throwable {
        Base64 base64 = new Base64(76);
        assertFalse(base64.isUrlSafe());
    }

    @Test
    public void testIntAndByteArrayConstructor() throws Throwable {
        byte[] sep = new byte[]{'\n'};
        Base64 base64 = new Base64(76, sep);
        assertFalse(base64.isUrlSafe());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidSeparator() throws Throwable {
        // 'A' is a base64 character, should throw IllegalArgumentException
        byte[] invalidSep = new byte[]{'A'};
        new Base64(76, invalidSep, false);
    }

    @Test
    public void testConstructorWithNullSeparator() throws Throwable {
        Base64 base64 = new Base64(76, null, false);
        assertFalse(base64.isUrlSafe());
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
    public void testIsBase64String() throws Throwable {
        assertTrue(Base64.isBase64("SGVsbG8gV29ybGQ="));
        assertTrue(Base64.isBase64(""));
        assertFalse(Base64.isBase64("SGVsbG8gV29ybGQ=!#$"));
    }

    @Test
    public void testIsBase64ByteArray() throws Throwable {
        byte[] valid = StringUtils.getBytesUtf8("SGVsbG8gV29ybGQ=");
        assertTrue(Base64.isBase64(valid));

        byte[] invalid = new byte[]{'S', 'G', 'V', 's', '!', '#'};
        assertFalse(Base64.isBase64(invalid));
    }

    @Test
    public void testEncodeDecodeStatic() throws Throwable {
        byte[] data = StringUtils.getBytesUtf8("Hello World");
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

        assertNull(Base64.decodeBase64((String) null));
        assertEquals("", Base64.decodeBase64(""));
    }

    @Test
    public void testEncodeBase64String() throws Throwable {
        String original = "Test String";
        String encodedStr = Base64.encodeBase64String(StringUtils.getBytesUtf8(original));
        assertNotNull(encodedStr);

        byte[] decoded = Base64.decodeBase64(encodedStr);
        assertArrayEquals(StringUtils.getBytesUtf8(original), decoded);
    }

    @Test
    public void testEncodeBase64URLSafe() throws Throwable {
        byte[] data = StringUtils.getBytesUtf8("Subject: ?<>");
        byte[] encoded = Base64.encodeBase64URLSafe(data);
        assertNotNull(encoded);

        String encodedStr = Base64.encodeBase64URLSafeString(data);
        assertNotNull(encodedStr);

        byte[] decoded = Base64.decodeBase64(encodedStr);
        assertArrayEquals(data, decoded);
    }

    @Test
    public void testEncodeBase64Chunked() throws Throwable {
        byte[] data = StringUtils.getBytesUtf8("This is a very long string that should definitely be chunked when encoded using the chunked base64 encoder variant to test line breaks correctly.");
        byte[] encoded = Base64.encodeBase64Chunked(data);
        assertNotNull(encoded);

        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(data, decoded);
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

        byte[] encoded = Base64.encodeBase64(data, false, false, 100);
        assertNotNull(encoded);
    }

    @Test
    public void testObjectEncoderInterface() throws Throwable {
        Base64 base64 = new Base64();
        byte[] data = StringUtils.getBytesUtf8("Object Test");
        Object encodedObj = base64.encode((Object) data);
        assertTrue(encodedObj instanceof byte[]);

        try {
            base64.encode(new Object());
            fail("Expected EncoderException");
        } catch (EncoderException e) {
            assertTrue(e.getMessage().contains("not a byte[]"));
        }
    }

    @Test
    public void testObjectDecoderInterface() throws Throwable {
        Base64 base64 = new Base64();
        String encodedStr = "T2JqZWN0IFRlc3Q=";
        Object decodedObj = base64.decode((Object) encodedStr);
        assertTrue(decodedObj instanceof byte[]);

        byte[] encodedBytes = StringUtils.getBytesUtf8(encodedStr);
        Object decodedBytesObj = base64.decode((Object) encodedBytes);
        assertTrue(decodedBytesObj instanceof byte[]);

        try {
            base64.decode(new Object());
            fail("Expected DecoderException");
        } catch (DecoderException e) {
            assertTrue(e.getMessage().contains("not a byte[] or a String"));
        }
    }

    @Test
    public void testIntegerEncodingDecoding() throws Throwable {
        BigInteger original = new BigInteger("12345678901234567890");
        byte[] encoded = Base64.encodeInteger(original);
        assertNotNull(encoded);

        BigInteger decoded = Base64.decodeInteger(encoded);
        assertEquals(original, decoded);

        BigInteger smallInt = BigInteger.valueOf(42);
        byte[] encodedSmall = Base64.encodeInteger(smallInt);
        assertEquals(smallInt, Base64.decodeInteger(encodedSmall));
    }

    @Test(expected = NullPointerException.class)
    public void testEncodeIntegerNull() throws Throwable {
        Base64.encodeInteger(null);
    }

    @Test
    public void testDiscardWhitespace() throws Throwable {
        byte[] data = StringUtils.getBytesUtf8("SGVs\n bG8g\r V29y\t bGQ=");
        byte[] cleaned = Base64.discardWhitespace(data);
        assertNotNull(cleaned);
        byte[] decoded = Base64.decodeBase64(cleaned);
        assertArrayEquals(StringUtils.getBytesUtf8("Hello World"), decoded);
    }

    @Test
    public void testStreamingModulusCases() throws Throwable {
        // Test different padding/modulus paths in encode and decode
        Base64 base64Std = new Base64(0, null, false);
        byte[] enc1 = base64Std.encode(StringUtils.getBytesUtf8("A"));
        assertArrayEquals(StringUtils.getBytesUtf8("QQ=="), enc1);

        Base64 base64Url = new Base64(0, null, true);
        byte[] enc2 = base64Url.encode(StringUtils.getBytesUtf8("A"));
        assertArrayEquals(StringUtils.getBytesUtf8("QQ"), enc2);

        Base64 base64Std2 = new Base64(0, null, false);
        byte[] enc3 = base64Std2.encode(StringUtils.getBytesUtf8("AB"));
        assertArrayEquals(StringUtils.getBytesUtf8("QUI="), enc3);

        Base64 base64Url2 = new Base64(0, null, true);
        byte[] enc4 = base64Url2.encode(StringUtils.getBytesUtf8("AB"));
        assertArrayEquals(StringUtils.getBytesUtf8("QUI"), enc4);
    }

    @Test
    public void testDecodeWithOptionalPadding() throws Throwable {
        // Missing padding "="
        byte[] decoded = Base64.decodeBase64("QQ");
        assertArrayEquals(StringUtils.getBytesUtf8("A"), decoded);

        byte[] decoded2 = Base64.decodeBase64("QUI");
        assertArrayEquals(StringUtils.getBytesUtf8("AB"), decoded2);
    }
}