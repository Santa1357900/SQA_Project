package org.apache.commons.codec.net;

import java.util.BitSet;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;

public class QuotedPrintableCodecClaudeTest {

    private QuotedPrintableCodec codec;

    @Before
    public void setUp() throws Throwable {
        codec = new QuotedPrintableCodec();
    }

    // branch: default constructor delegates to charset constructor with CharEncoding.UTF_8
    @Test
    public void testConstructor_default_usesUtf8Charset() throws Throwable {
        QuotedPrintableCodec c = new QuotedPrintableCodec();
        assertEquals(CharEncoding.UTF_8, c.getDefaultCharset());
    }

    // branch: charset constructor stores given charset as-is
    @Test
    public void testConstructor_withCharset_setsGivenCharset() throws Throwable {
        QuotedPrintableCodec c = new QuotedPrintableCodec("ISO-8859-1");
        assertEquals("ISO-8859-1", c.getDefaultCharset());
    }

    // branch: bytes == null returns null before any processing
    @Test
    public void testEncodeQuotedPrintableStatic_nullBytes_returnsNull() throws Throwable {
        byte[] result = QuotedPrintableCodec.encodeQuotedPrintable(null, null);
        assertNull(result);
    }







    // branch: null byte[] short-circuits to null
    @Test
    public void testEncode_byteArray_null_returnsNull() throws Throwable {
        assertNull(codec.encode((byte[]) null));
    }

    // branch: printable.get(b) true for ordinary letters, written unchanged
    @Test
    public void testEncode_byteArray_printableAsciiUnchanged() throws Throwable {
        byte[] input = "Hello".getBytes("UTF-8");
        assertArrayEquals(input, codec.encode(input));
    }















    // branch: null input short-circuits to null
    @Test
    public void testDecodeQuotedPrintableStatic_null_returnsNull() throws Throwable {
        assertNull(QuotedPrintableCodec.decodeQuotedPrintable(null));
    }

    // branch: non-escape bytes are appended as-is
    @Test
    public void testDecode_byteArray_literalCharsPassThrough() throws Throwable {
        byte[] input = "Hello".getBytes("UTF-8");
        assertArrayEquals(input, codec.decode(input));
    }

    // branch: '=' triggers reading two hex digits and reconstructing the original byte
    @Test
    public void testDecode_byteArray_simpleHexEscapeDecodesCorrectly() throws Throwable {
        byte[] result = codec.decode(new byte[] { '=', '3', 'D' });
        assertArrayEquals(new byte[] { '=' }, result);
    }

    // branch: '=' with no following hex digits triggers AIOOBE -> DecoderException
    @Test
    public void testDecode_byteArray_incompleteEscapeAtEnd_throwsDecoderException() throws Throwable {
        try {
            codec.decode(new byte[] { '=' });
            fail("expected DecoderException");
        } catch (DecoderException expected) {
        }
    }

    // branch: '=' followed by only one hex digit still triggers DecoderException
    @Test
    public void testDecode_byteArray_incompleteEscapeOneHexDigit_throwsDecoderException() throws Throwable {
        try {
            codec.decode(new byte[] { '=', '4' });
            fail("expected DecoderException");
        } catch (DecoderException expected) {
        }
    }

    // branch: non-hex digit after '=' is an unsuccessful decode per method contract
    @Test
    public void testDecode_byteArray_invalidHexDigit_throwsDecoderException() throws Throwable {
        try {
            codec.decode(new byte[] { '=', 'G', '0' });
            fail("expected DecoderException");
        } catch (DecoderException expected) {
        }
    }



    // branch: null string short-circuits to null
    @Test
    public void testEncode_String_null_returnsNull() throws Throwable {
        assertNull(codec.encode((String) null));
    }

    // branch: plain ASCII letters require no escaping
    @Test
    public void testEncode_String_asciiUnchanged() throws Throwable {
        assertEquals("Hello", codec.encode("Hello"));
    }

    // branch: '=' inside a string is escaped to =3D
    @Test
    public void testEncode_String_withEqualsSign_escaped() throws Throwable {
        assertEquals("A=3DB", codec.encode("A=B"));
    }



    // branch: UnsupportedEncodingException from getBytes(charset) is wrapped into EncoderException
    @Test
    public void testEncode_String_invalidCharset_throwsEncoderException() throws Throwable {
        QuotedPrintableCodec bad = new QuotedPrintableCodec("NOT-A-REAL-CHARSET");
        try {
            bad.encode("Hello");
            fail("expected EncoderException");
        } catch (EncoderException expected) {
        }
    }

    // branch: null string short-circuits to null
    @Test
    public void testDecode_String_null_returnsNull() throws Throwable {
        assertNull(codec.decode((String) null));
    }

    // branch: literal ASCII decodes unchanged
    @Test
    public void testDecode_String_simple_returnsOriginal() throws Throwable {
        assertEquals("Hello", codec.decode("Hello"));
    }

    // branch: escaped '=3D' decodes back to '='
    @Test
    public void testDecode_String_withEscape_returnsOriginal() throws Throwable {
        assertEquals("A=B", codec.decode("A=3DB"));
    }

    // branch: two consecutive escapes reconstruct a multi-byte UTF-8 char
    @Test
    public void testDecode_String_unicodeEscape_returnsOriginalChar() throws Throwable {
        assertEquals("\u00e9", codec.decode("=C3=A9"));
    }

    // branch: UnsupportedEncodingException from new String(bytes,charset) is wrapped into DecoderException
    @Test
    public void testDecode_String_invalidCharset_throwsDecoderException() throws Throwable {
        QuotedPrintableCodec bad = new QuotedPrintableCodec("NOT-A-REAL-CHARSET");
        try {
            bad.decode("Hello");
            fail("expected DecoderException");
        } catch (DecoderException expected) {
        }
    }

    // branch: null string short-circuits to null regardless of charset argument
    @Test
    public void testDecode_StringCharset_null_returnsNull() throws Throwable {
        assertNull(codec.decode((String) null, "UTF-8"));
    }

    // branch: decoded ASCII bytes are interpreted using the explicitly given charset
    @Test
    public void testDecode_StringCharset_withSpecifiedCharset() throws Throwable {
        assertEquals("\u00e9", codec.decode("=C3=A9", "UTF-8"));
    }



    // branch: pObject == null returns null
    @Test
    public void testEncode_Object_null_returnsNull() throws Throwable {
        assertNull(codec.encode((Object) null));
    }



    // branch: instanceof String delegates to encode(String)
    @Test
    public void testEncode_Object_string_delegatesCorrectly() throws Throwable {
        Object result = codec.encode((Object) "A=B");
        assertEquals("A=3DB", result);
    }

    // branch: unsupported object type falls through to the else-throw
    @Test
    public void testEncode_Object_unsupportedType_throwsEncoderException() throws Throwable {
        try {
            codec.encode((Object) Integer.valueOf(5));
            fail("expected EncoderException");
        } catch (EncoderException expected) {
            assertTrue(expected.getMessage().contains("cannot be"));
        }
    }

    // branch: pObject == null returns null
    @Test
    public void testDecode_Object_null_returnsNull() throws Throwable {
        assertNull(codec.decode((Object) null));
    }

    // branch: instanceof byte[] delegates to decode(byte[])
    @Test
    public void testDecode_Object_byteArray_delegatesCorrectly() throws Throwable {
        Object result = codec.decode((Object) new byte[] { '=', '3', 'D' });
        assertArrayEquals(new byte[] { '=' }, (byte[]) result);
    }

    // branch: instanceof String delegates to decode(String)
    @Test
    public void testDecode_Object_string_delegatesCorrectly() throws Throwable {
        Object result = codec.decode((Object) "A=3DB");
        assertEquals("A=B", result);
    }

    // branch: unsupported object type falls through to the else-throw
    @Test
    public void testDecode_Object_unsupportedType_throwsDecoderException() throws Throwable {
        try {
            codec.decode((Object) Integer.valueOf(5));
            fail("expected DecoderException");
        } catch (DecoderException expected) {
            assertTrue(expected.getMessage().contains("cannot be"));
        }
    }
}
