package org.apache.commons.codec.net;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.UnsupportedEncodingException;
import java.util.BitSet;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;
import org.junit.Test;

public class QuotedPrintableCodecTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        assertEquals(CharEncoding.UTF_8, codec.getDefaultCharset());
    }

    @Test
    public void testCharsetConstructor() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec(CharEncoding.ISO_8859_1);
        assertEquals(CharEncoding.ISO_8859_1, codec.getDefaultCharset());
    }

    @Test
    public void testEncodeNullBytes() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        assertNull(codec.encode((byte[]) null));
        assertNull(QuotedPrintableCodec.encodeQuotedPrintable(null, null));
    }

    @Test
    public void testDecodeNullBytes() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        assertNull(codec.decode((byte[]) null));
        assertNull(QuotedPrintableCodec.decodeQuotedPrintable(null));
    }

    @Test
    public void testEncodeNullString() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        assertNull(codec.encode((String) null));
        assertNull(codec.encode(null, CharEncoding.UTF_8));
    }

    @Test
    public void testDecodeNullString() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        assertNull(codec.decode((String) null));
        assertNull(codec.decode(null, CharEncoding.UTF_8));
    }

    @Test
    public void testEncodeObjectNull() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        assertNull(codec.encode((Object) null));
    }

    @Test
    public void testDecodeObjectNull() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        assertNull(codec.decode((Object) null));
    }

    @Test
    public void testEncodeByteArrayObject() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        byte[] input = new byte[] { 'a', 'b', 'c' };
        Object result = codec.encode((Object) input);
        assertNotNull(result);
        assertEquals(byte[].class, result.getClass());
        assertArrayEquals(input, (byte[]) result);
    }

    @Test
    public void testEncodeStringObject() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        Object result = codec.encode((Object) "abc");
        assertNotNull(result);
        assertEquals("abc", result);
    }

    @Test
    public void testEncodeInvalidObjectType() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        try {
            codec.encode(Integer.valueOf(123));
            fail("Expected EncoderException");
        } catch (EncoderException e) {
            assertTrue(e.getMessage().contains("Objects of type"));
        }
    }

    @Test
    public void testDecodeByteArrayObject() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        byte[] input = new byte[] { 'a', 'b', 'c' };
        Object result = codec.decode((Object) input);
        assertNotNull(result);
        assertEquals(byte[].class, result.getClass());
        assertArrayEquals(input, (byte[]) result);
    }

    @Test
    public void testDecodeStringObject() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        Object result = codec.decode((Object) "abc");
        assertNotNull(result);
        assertEquals("abc", result);
    }

    @Test
    public void testDecodeInvalidObjectType() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        try {
            codec.decode(Integer.valueOf(123));
            fail("Expected DecoderException");
        } catch (DecoderException e) {
            assertTrue(e.getMessage().contains("Objects of type"));
        }
    }

    @Test
    public void testEncodeDecodeSafeCharacters() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        String plain = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ,.-";
        String encoded = codec.encode(plain);
        assertEquals(plain, encoded);
        String decoded = codec.decode(encoded);
        assertEquals(plain, decoded);
    }

    @Test
    public void testEncodeUnsafeCharacters() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        byte[] input = new byte[] { 0x01, (byte) 0xFF };
        byte[] encoded = codec.encode(input);
        assertNotNull(encoded);
        byte[] decoded = codec.decode(encoded);
        assertArrayEquals(input, decoded);
    }

    @Test
    public void testEncodeNegativeByte() throws Throwable {
        byte[] input = new byte[] { (byte) -128 };
        byte[] encoded = QuotedPrintableCodec.encodeQuotedPrintable(null, input);
        assertNotNull(encoded);
        byte[] decoded = QuotedPrintableCodec.decodeQuotedPrintable(encoded);
        assertArrayEquals(input, decoded);
    }

    @Test
    public void testDecodeInvalidQuotedPrintableShortInput() throws Throwable {
        byte[] input = new byte[] { '=' };
        try {
            QuotedPrintableCodec.decodeQuotedPrintable(input);
            fail("Expected DecoderException");
        } catch (DecoderException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testDecodeInvalidQuotedPrintableInvalidHex() throws Throwable {
        byte[] input = new byte[] { '=', 'X', 'Y' };
        try {
            QuotedPrintableCodec.decodeQuotedPrintable(input);
            fail("Expected DecoderException");
        } catch (DecoderException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testEncodeStringWithCharset() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        String plain = "test";
        String encoded = codec.encode(plain, CharEncoding.UTF_8);
        assertNotNull(encoded);
        String decoded = codec.decode(encoded, CharEncoding.UTF_8);
        assertEquals(plain, decoded);
    }

    @Test
    public void testEncodeUnsupportedCharset() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        try {
            codec.encode("test", "NONEXISTENT_CHARSET");
            fail("Expected UnsupportedEncodingException");
        } catch (UnsupportedEncodingException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testDecodeUnsupportedCharset() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec();
        try {
            codec.decode("test", "NONEXISTENT_CHARSET");
            fail("Expected UnsupportedEncodingException");
        } catch (UnsupportedEncodingException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testEncodeExceptionHandling() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec("NONEXISTENT_CHARSET");
        try {
            codec.encode("test");
            fail("Expected EncoderException");
        } catch (EncoderException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testDecodeExceptionHandling() throws Throwable {
        QuotedPrintableCodec codec = new QuotedPrintableCodec("NONEXISTENT_CHARSET");
        try {
            codec.decode("test");
            fail("Expected DecoderException");
        } catch (DecoderException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testCustomBitSet() throws Throwable {
        BitSet customBitSet = new BitSet(256);
        customBitSet.set('a');
        byte[] input = new byte[] { 'a', 'b' };
        byte[] encoded = QuotedPrintableCodec.encodeQuotedPrintable(customBitSet, input);
        assertNotNull(encoded);
    }
}