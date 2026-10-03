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
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;

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
        byte[] separator = new byte[] {'\n'};
        Base64 base64 = new Base64(76, separator);
        assertFalse(base64.isUrlSafe());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidSeparator() throws Throwable {
        byte[] invalidSeparator = new byte[] {'A'};
        new Base64(76, invalidSeparator, false);
    }

    @Test
    public void testConstructorWithNullSeparator() throws Throwable {
        Base64 base64 = new Base64(76, null, false);
        assertFalse(base64.isUrlSafe());
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
        byte[] valid = new byte[] {'A', 'B', 'C', 'D'};
        assertTrue(Base64.isArrayByteBase64(valid));

        byte[] withWhitespace = new byte[] {'A', 'B', '\r', '\n', 'C', 'D'};
        assertTrue(Base64.isArrayByteBase64(withWhitespace));

        byte[] invalid = new byte[] {'A', 'B', '*', 'D'};
        assertFalse(Base64.isArrayByteBase64(invalid));
    }

    @Test
    public void testEncodeDecodeStandard() throws Throwable {
        byte[] original = "Hello, World!".getBytes("UTF-8");
        byte[] encoded = Base64.encodeBase64(original);
        assertNotNull(encoded);
        
        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(original, decoded);
    }

    @Test
    public void testEncodeDecodeEmptyAndNull() throws Throwable {
        assertNull(Base64.encodeBase64(null));
        assertArrayEquals(new byte[0], Base64.encodeBase64(new byte[0]));

        assertNull(Base64.decodeBase64((byte[]) null));
        assertArrayEquals(new byte[0], Base64.decodeBase64(new byte[0]));

        assertNull(Base64.decodeBase64((String) null));
        assertEquals("", Base64.decodeBase64(""));
    }

    @Test
    public void testEncodeString() throws Throwable {
        String original = "Base64 Test";
        String encoded = Base64.encodeBase64String(original.getBytes("UTF-8"));
        assertNotNull(encoded);

        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(original.getBytes("UTF-8"), decoded);
    }

    @Test
    public void testEncodeURLSafe() throws Throwable {
        byte[] original = "Subject: ?<>{}[]".getBytes("UTF-8");
        byte[] encoded = Base64.encodeBase64URLSafe(original);
        assertNotNull(encoded);
        
        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(original, decoded);

        String encodedStr = Base64.encodeBase64URLSafeString(original);
        assertNotNull(encodedStr);
        byte[] decodedStr = Base64.decodeBase64(encodedStr);
        assertArrayEquals(original, decodedStr);
    }

    @Test
    public void testEncodeChunked() throws Throwable {
        byte[] original = new byte[200];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) i;
        }
        byte[] encoded = Base64.encodeBase64Chunked(original);
        assertNotNull(encoded);

        byte[] decoded = Base64.decodeBase64(encoded);
        assertArrayEquals(original, decoded);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEncodeMaxResultSizeExceeded() throws Throwable {
        byte[] original = "Exceed max size".getBytes("UTF-8");
        Base64.encodeBase64(original, false, false, 2);
    }

    @Test
    public void testObjectEncode() throws Throwable {
        Base64 base64 = new Base64();
        byte[] original = "Test Object Encode".getBytes("UTF-8");
        Object encoded = base64.encode((Object) original);
        assertTrue(encoded instanceof byte[]);

        byte[] decoded = (byte[]) base64.decode(encoded);
        assertArrayEquals(original, decoded);

        String originalStr = "Test String Decode";
        byte[] encodedBytes = Base64.encodeBase64(originalStr.getBytes("UTF-8"));
        byte[] decodedObjResult = (byte[]) base64.decode((Object) encodedBytes);
        assertArrayEquals(originalStr.getBytes("UTF-8"), decodedObjResult);

        byte[] decodedStrObjResult = (byte[]) base64.decode((Object) new String(encodedBytes, "UTF-8"));
        assertArrayEquals(originalStr.getBytes("UTF-8"), decodedStrObjResult);
    }

    @Test(expected = EncoderException.class)
    public void testObjectEncodeInvalidType() throws Throwable {
        Base64 base64 = new Base64();
        base64.encode((Object) "Not a byte array");
    }

    @Test(expected = DecoderException.class)
    public void testObjectDecodeInvalidType() throws Throwable {
        Base64 base64 = new Base64();
        base64.decode((Object) new Integer(123));
    }

    @Test
    public void testIntegerEncodeDecode() throws Throwable {
        BigInteger bigInt = new BigInteger("12345678901234567890");
        byte[] encoded = Base64.encodeInteger(bigInt);
        assertNotNull(encoded);

        BigInteger decoded = Base64.decodeInteger(encoded);
        assertEquals(bigInt, decoded);
    }

    @Test(expected = NullPointerException.class)
    public void testEncodeIntegerNull() throws Throwable {
        Base64.encodeInteger(null);
    }

    @Test
    public void testIntegerBytesSmall() throws Throwable {
        BigInteger bigInt = BigInteger.valueOf(10);
        byte[] encoded = Base64.encodeInteger(bigInt);
        BigInteger decoded = Base64.decodeInteger(encoded);
        assertEquals(bigInt, decoded);
    }

    @Test
    public void testDiscardWhitespace() throws Throwable {
        byte[] data = "AB CD\r\nEF\t".getBytes("UTF-8");
        byte[] groomed = Base64.discardWhitespace(data);
        assertNotNull(groomed);
        assertEquals("ABCDEF", new String(groomed, "UTF-8"));
    }

    @Test
    public void testStreamingModulusScenarios() throws Throwable {
        Base64 base64 = new Base64(0);
        byte[] data = new byte[] {1, 2};
        byte[] encoded = base64.encode(data);
        assertNotNull(encoded);

        Base64 base64Decode = new Base64();
        byte[] decoded = base64Decode.decode(encoded);
        assertArrayEquals(data, decoded);

        Base64 base64Mod2 = new Base64(0);
        base64Mod2.encode(new byte[] {1}, 0, 1);
        base64Mod2.encode(new byte[] {1}, 0, -1);
        assertTrue(base64Mod2.hasData());

        Base64 base64Mod1 = new Base64(0);
        base64Mod1.encode(new byte[] {1, 2}, 0, 1);
        base64Mod1.encode(new byte[] {1, 2}, 0, -1);
        assertTrue(base64Mod1.hasData());
    }

    @Test
    public void testDecodePadEdgeCases() throws Throwable {
        byte[] data = "QQ==".getBytes("UTF-8");
        byte[] decoded = Base64.decodeBase64(data);
        assertNotNull(decoded);

        byte[] data2 = "QQ".getBytes("UTF-8");
        byte[] decoded2 = Base64.decodeBase64(data2);
        assertNotNull(decoded2);
        
        byte[] data3 = "Q=".getBytes("UTF-8");
        byte[] decoded3 = Base64.decodeBase64(data3);
        assertNotNull(decoded3);
    }

    @Test
    public void testReadResultsWithSameBuffer() throws Throwable {
        Base64 base64 = new Base64(0);
        byte[] out = new byte[10];
        base64.setInitialBuffer(out, 0, 10);
        base64.encode(new byte[] {1, 2, 3}, 0, 3);
        base64.encode(new byte[] {1, 2, 3}, 0, -1);
        
        int read = base64.readResults(out, 0, 10);
        assertTrue(read >= 0);
    }
}